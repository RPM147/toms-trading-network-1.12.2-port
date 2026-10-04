"""Focused synthetic reader checks. All generated worlds live in temporary directories."""
import gzip
import hashlib
import importlib.util
import json
import random
from pathlib import Path
import struct
import tempfile
import unittest
import uuid
from unittest import mock
import zlib

SPEC = importlib.util.spec_from_file_location("directory_scan", Path(__file__).with_name("scan-machine-directory.py"))
SCAN = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SCAN)


def string(value):
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def tag(kind, name, payload):
    return bytes([kind]) + string(name) + payload


def integer(name, value):
    return tag(3, name, struct.pack(">i", value))


def text(name, value):
    return tag(8, name, string(value))


def uid(name, value):
    number = uuid.UUID(value).int
    return b"".join(tag(4, name + suffix, (part & ((1 << 64) - 1)).to_bytes(8, "big"))
                    for suffix, part in (("Most", number >> 64), ("Least", number)))


def compound(name, contents):
    return tag(10, name, contents + b"\x00")


def chunk_payload(version=2, trailing=b""):
    tile = text("id", SCAN.TILE_ID) + integer("x", 1) + integer("y", 64) + integer("z", 2)
    tile += integer("DataVersion", version) + text("OwnerNameCache", "Owner") + text("CustomName", "Shop")
    tile += compound("SaleStock", text("PrivateItem", "must-not-be-exported"))
    tiles = tag(9, "TileEntities", b"\x0a" + struct.pack(">i", 1) + tile + b"\x00")
    return compound("", compound("Level", integer("xPos", 0) + integer("zPos", 0) + tiles)) + trailing


def write_region(path, compression=2, extended=False, version=2):
    raw = chunk_payload(version)
    if extended:
        padding = random.Random(12).randbytes(1100000)
        raw = raw[:-1] + tag(7, "Padding", struct.pack(">i", len(padding)) + padding) + b"\x00"
    encoded = gzip.compress(raw) if compression == 1 else zlib.compress(raw)
    length = len(encoded) + 1
    sectors = (length + 4) // 4096 + 1
    header = struct.pack(">I", (2 << 8) | (255 if extended else sectors)) + bytes(8192 - 4)
    body = struct.pack(">I", length) + bytes([compression]) + encoded
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(header + body + bytes(sectors * 4096 - len(body)))


def backup(root, folder="AoA_Abyss"):
    root.mkdir()
    (root / "level.dat").write_bytes(b"synthetic level marker, never opened by Minecraft")
    (root / "session.lock").write_bytes(b"synthetic stopped-save marker")
    world = "07238993-06cb-477c-be4b-2458bba30ce1"
    signature = "ttn-directory-manifest-v1\n" + world + "\n7\tExampleProvider\t" + folder + "\t\n"
    digest = hashlib.sha256(signature.encode()).hexdigest()
    manifest = dict(format=1, worldId=world, manifestDigest=digest,
                    dimensions=[dict(id=7, provider="ExampleProvider", folder=folder, problem="")])
    (root / "ttn-directory").mkdir()
    (root / "ttn-directory" / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    data = integer("Format", 1) + uid("WorldUUID", world) + text("ManifestDigest", digest)
    (root / "data").mkdir()
    (root / "data" / "ttn_machine_directory.dat").write_bytes(gzip.compress(compound("", compound("data", data))))
    write_region(root / folder / "region" / "r.0.0.mca")
    return manifest


class ReaderTest(unittest.TestCase):
    def test_catalogue_v2_header_is_accepted_read_only_but_future_header_is_refused(self):
        with tempfile.TemporaryDirectory(prefix="ttn-category-header-") as temp:
            root = Path(temp) / "backup"
            manifest = backup(root)
            path = root / "data" / "ttn_machine_directory.dat"
            for version in (2, 3):
                data = integer("Format", version) + uid("WorldUUID", manifest["worldId"]) + text("ManifestDigest", manifest["manifestDigest"])
                path.write_bytes(gzip.compress(compound("", compound("data", data))))
                before = path.read_bytes()
                if version == 2:
                    self.assertEqual(manifest["worldId"], SCAN.load_manifest(root)["worldId"])
                else:
                    with self.assertRaises(SCAN.ScanError): SCAN.load_manifest(root)
                self.assertEqual(before, path.read_bytes())

    def test_gzip_zlib_and_forge_extended_sector_read_without_mutating_source(self):
        with tempfile.TemporaryDirectory(prefix="ttn-rt1-reader-") as temp:
            path = Path(temp) / "r.0.0.mca"
            for compression in (1, 2):
                for extended in (False, True):
                    write_region(path, compression, extended)
                    if extended:
                        self.assertGreater(path.stat().st_size, 255 * 4096)
                    before = hashlib.sha256(path.read_bytes()).digest()
                    entries, issues = [], []
                    self.assertEqual(1, SCAN.scan_region(path, 7, entries.append, lambda *args: issues.append(args)))
                    self.assertEqual([], issues)
                    self.assertEqual(1, len(entries))
                    self.assertEqual(7, entries[0]["dimension"])
                    self.assertIsNone(entries[0]["machineId"])
                    self.assertNotIn("SaleStock", entries[0])
                    self.assertEqual(before, hashlib.sha256(path.read_bytes()).digest())

    def test_complete_backup_scan_output_binding_and_source_hashes(self):
        with tempfile.TemporaryDirectory(prefix="ttn-rt1-world-") as temp:
            root, output = Path(temp) / "backup", Path(temp) / "output"
            manifest = backup(root)
            before = {p: hashlib.sha256(p.read_bytes()).digest() for p in root.rglob("*") if p.is_file()}
            self.assertEqual(0, SCAN.run(["--world-backup", str(root), "--out-dir", str(output), "--confirm-stopped-backup"]))
            imported = json.loads((output / "import.json").read_text(encoding="utf-8"))
            self.assertEqual(manifest["worldId"], imported["worldId"])
            self.assertEqual(manifest["manifestDigest"], imported["manifestDigest"])
            self.assertTrue(imported["complete"])
            self.assertEqual(1, len(imported["entries"]))
            self.assertNotIn("must-not-be-exported", (output / "import.json").read_text())
            self.assertEqual(before, {p: hashlib.sha256(p.read_bytes()).digest() for p in root.rglob("*") if p.is_file()})

    def test_wrong_world_manifest_and_unsafe_folder_are_rejected(self):
        with tempfile.TemporaryDirectory(prefix="ttn-rt1-binding-") as temp:
            root = Path(temp) / "backup"
            manifest = backup(root)
            path = root / "ttn-directory" / "manifest.json"
            for field, value in (("worldId", str(uuid.uuid4())), ("manifestDigest", "0" * 64)):
                changed = dict(manifest); changed[field] = value
                path.write_text(json.dumps(changed), encoding="utf-8")
                with self.assertRaises(SCAN.ScanError): SCAN.load_manifest(root)
            manifest["dimensions"][0]["folder"] = "../outside"
            path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaises(SCAN.ScanError): SCAN.load_manifest(root)

    def test_corrupt_and_unknown_machine_data_are_reported_as_incomplete(self):
        with tempfile.TemporaryDirectory(prefix="ttn-rt1-errors-") as temp:
            root = Path(temp) / "backup"
            manifest = backup(root)
            path = root / "AoA_Abyss" / "region" / "r.0.0.mca"
            for version in (0, 4, 99):
                write_region(path, version=version)
                result, report = SCAN.scan_world(root, manifest)
                self.assertFalse(result["complete"])
                self.assertEqual([], result["entries"])
                self.assertGreater(report["issueCount"], 0)
            path.write_bytes(b"broken")
            result, report = SCAN.scan_world(root, manifest)
            self.assertFalse(result["complete"])
            self.assertEqual(b"broken", path.read_bytes())

    def test_bounds_overlap_and_unsupported_compression(self):
        with tempfile.TemporaryDirectory(prefix="ttn-rt1-bounds-") as temp:
            path = Path(temp) / "r.0.0.mca"
            write_region(path)
            data = bytearray(path.read_bytes()); data[4:8] = data[:4]; path.write_bytes(data)
            with self.assertRaises(SCAN.ScanError): SCAN.scan_region(path, 7, lambda _: None, lambda *args: None)
            write_region(path, compression=3)
            issues = []
            SCAN.scan_region(path, 7, lambda _: None, lambda *args: issues.append(args))
            self.assertTrue(issues)
        with mock.patch.object(SCAN, "MAX_NBT", 16):
            with self.assertRaises(SCAN.ScanError): SCAN.inflate(zlib.compress(b"x" * 1000), 2)
        with self.assertRaises(SCAN.ScanError): SCAN.NbtReader(chunk_payload(trailing=b"junk")).root()
        with self.assertRaises(SCAN.ScanError): SCAN.inflate(zlib.compress(b"x") + b"junk", 2)

    def test_missing_and_unmapped_regions_never_claim_all_machines_found(self):
        with tempfile.TemporaryDirectory(prefix="ttn-rt1-coverage-") as temp:
            root = Path(temp) / "backup"
            manifest = backup(root)
            write_region(root / "Abyssal_Wasteland" / "region" / "r.0.0.mca")
            result, report = SCAN.scan_world(root, manifest)
            self.assertFalse(result["complete"])
            self.assertTrue(any("mapping" in issue["reason"] for issue in report["issues"]))
            manifest["dimensions"].append(dict(id=9, provider="MissingProvider", folder="DIM9", problem=""))
            result, report = SCAN.scan_world(root, manifest)
            self.assertTrue(any("no saved region" in issue["reason"] for issue in report["issues"]))

    def test_output_in_backup_is_refused(self):
        with tempfile.TemporaryDirectory(prefix="ttn-rt1-output-") as temp:
            root = Path(temp) / "backup"; backup(root)
            with self.assertRaises(SCAN.ScanError):
                SCAN.run(["--world-backup", str(root), "--out-dir", str(root / "output"), "--confirm-stopped-backup"])
            self.assertFalse((root / "output").exists())


if __name__ == "__main__":
    unittest.main()
