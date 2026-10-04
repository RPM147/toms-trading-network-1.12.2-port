#!/usr/bin/env python3
"""TTN 1.12.2 offline discovery. Standard library only; source files are opened rb.

Run ONLY on a stopped, complete world backup, after /ttndirectory export.
Writes import.json and report.json to a NEW directory outside that backup.
Never opens RegionFile, loads a world, edits NBT, or copies inventory payloads.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import sys
import uuid
import zlib

MAX_COMPRESSED = 16 * 1024 * 1024
MAX_NBT = 32 * 1024 * 1024
MAX_NODES = 250000
MAX_HINTS = 10000
MAX_REGIONS = 100000
TILE_ID = "toms_trading_network:vending_machine.tile"
REGION_NAME = re.compile(r"r\.(-?\d+)\.(-?\d+)\.mca\Z")


class ScanError(ValueError):
    pass


class NbtReader:
    """Bounded NBT including Java modified UTF-8, with no object deserialization."""
    def __init__(self, raw):
        if len(raw) > MAX_NBT:
            raise ScanError("NBT byte limit exceeded")
        self.data = memoryview(raw)
        self.offset = 0
        self.nodes = 0

    def take(self, size):
        if size < 0 or size > len(self.data) - self.offset:
            raise ScanError("Truncated NBT")
        start = self.offset
        self.offset += size
        return self.data[start:self.offset]

    def number(self, fmt):
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self):
        raw = bytes(self.take(self.number(">H")))
        try:
            value = raw.replace(b"\xc0\x80", b"\x00").decode("utf-8", "surrogatepass")
            return value.encode("utf-16-le", "surrogatepass").decode("utf-16-le")
        except UnicodeError as error:
            raise ScanError("Unsupported/malformed NBT string") from error

    def payload(self, kind, depth):
        self.nodes += 1
        if depth > 64 or self.nodes > MAX_NODES:
            raise ScanError("NBT depth/node limit exceeded")
        formats = {1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}
        if kind in formats:
            return self.number(formats[kind])
        if kind in (7, 11, 12):
            count = self.number(">i")
            if count < 0:
                raise ScanError("Negative NBT array length")
            self.take(count * {7: 1, 11: 4, 12: 8}[kind])
            return None  # Block/item arrays are not catalogue metadata.
        if kind == 8:
            return self.string()
        if kind == 9:
            item_type, count = self.number(">B"), self.number(">i")
            if count < 0 or count > MAX_NODES - self.nodes or (count and not 1 <= item_type <= 12):
                raise ScanError("Invalid/oversized NBT list")
            return [self.payload(item_type, depth + 1) for _ in range(count)]
        if kind == 10:
            result = {}
            while True:
                child = self.number(">B")
                if child == 0:
                    return result
                key = self.string()
                if key in result:
                    raise ScanError("Duplicate NBT key")
                result[key] = self.payload(child, depth + 1)
        raise ScanError("Unsupported NBT tag type: " + str(kind))

    def root(self):
        if self.number(">B") != 10:
            raise ScanError("NBT root is not a compound")
        self.string()
        result = self.payload(10, 0)
        if self.offset != len(self.data):
            raise ScanError("Trailing NBT data")
        return result


def inflate(raw, compression):
    if len(raw) > MAX_COMPRESSED:
        raise ScanError("Compressed chunk limit exceeded")
    if compression not in (1, 2):
        raise ScanError("Unsupported region compression (including external .mcc): " + str(compression))
    try:
        decoder = zlib.decompressobj(31 if compression == 1 else 15)
        result = decoder.decompress(raw, MAX_NBT + 1)
        if len(result) > MAX_NBT or decoder.unconsumed_tail:
            raise ScanError("Decompressed NBT limit exceeded")
        if not decoder.eof or decoder.unused_data:
            raise ScanError("Truncated or concatenated compression stream")
        return result
    except zlib.error as error:
        raise ScanError("Invalid compressed stream") from error


def fingerprint(path):
    value = path.stat()
    return value.st_size, value.st_mtime_ns, value.st_ino


def read_stable(path, limit):
    before = fingerprint(path)
    with path.open("rb") as stream:
        value = stream.read(limit + 1)
    if len(value) > limit or fingerprint(path) != before:
        raise ScanError("Source changed or file exceeds limit: " + path.name)
    return value


def contained(root, path):
    path = path.resolve()
    if not path.is_relative_to(root):
        raise ScanError("Source path escapes backup")
    return path


def nbt_uuid(tag, key, required=False):
    most, least = tag.get(key + "Most"), tag.get(key + "Least")
    if most is None and least is None and not required:
        return None
    if type(most) is not int or type(least) is not int or not -(1 << 63) <= most < (1 << 63) or not -(1 << 63) <= least < (1 << 63):
        raise ScanError("Malformed or missing " + key)
    return str(uuid.UUID(int=((most & ((1 << 64) - 1)) << 64) | (least & ((1 << 64) - 1))))


def label(value):
    if not isinstance(value, str):
        raise ScanError("Invalid machine label")
    return "".join(c for c in value if ord(c) >= 32 and not 127 <= ord(c) <= 159 and c != "\u00a7")[:64]


def machine_hint(tile, dimension, chunk_x, chunk_z):
    coords = [tile.get(axis) for axis in ("x", "y", "z")]
    if any(type(value) is not int for value in coords):
        raise ScanError("Missing machine coordinates")
    x, y, z = coords
    if abs(x) > 30000000 or abs(z) > 30000000 or not 0 <= y <= 255 or x >> 4 != chunk_x or z >> 4 != chunk_z:
        raise ScanError("Machine position does not match its chunk")
    version = tile.get("DataVersion", 1)
    if type(version) is not int or version not in (1, 2, 3):
        raise ScanError("Unsupported machine DataVersion: " + str(version))
    return dict(dimension=dimension, x=x, y=y, z=z,
                machineId=nbt_uuid(tile, "MachineUUID") if version >= 3 else None,
                ownerId=nbt_uuid(tile, "OwnerUUID"), ownerName=label(tile.get("OwnerNameCache", "")),
                name=label(tile.get("CustomName", "")))


def scan_region(path, dimension, emit, issue):
    """Own read-only region reader; Forge 2859's 255-sector extension is supported."""
    match = REGION_NAME.fullmatch(path.name)
    if not match:
        raise ScanError("Unexpected region filename")
    region_x, region_z = map(int, match.groups())
    before = fingerprint(path)
    size = before[0]
    if size < 8192 or size % 4096:
        raise ScanError("Truncated/misaligned region (not repaired)")
    with path.open("rb") as stream:
        header = stream.read(8192)
        offsets = struct.unpack(">1024I", header[:4096])
        occupied, rows = [], []
        for index, offset in enumerate(offsets):
            if offset == 0:
                continue
            sector, sectors = offset >> 8, offset & 255
            if sector < 2 or sectors == 0 or sector * 4096 + 5 > size:
                raise ScanError("Invalid chunk allocation")
            stream.seek(sector * 4096)
            length = struct.unpack(">I", stream.read(4))[0]
            if sectors == 255:
                # Exact Forge formula; +1 padding is intentional, including exact multiples.
                sectors = (length + 4) // 4096 + 1
            if length < 2 or length > MAX_COMPRESSED + 1 or length + 4 > sectors * 4096 or (sector + sectors) * 4096 > size:
                raise ScanError("Invalid/oversized chunk length")
            occupied.append((sector, sector + sectors))
            rows.append((index, sector, length))
        occupied.sort()
        if any(a[1] > b[0] for a, b in zip(occupied, occupied[1:])):
            raise ScanError("Overlapping region allocations")
        for index, sector, length in rows:
            chunk_x, chunk_z = region_x * 32 + index % 32, region_z * 32 + index // 32
            try:
                stream.seek(sector * 4096 + 4)
                compression = stream.read(1)[0]
                raw = stream.read(length - 1)
                if len(raw) != length - 1:
                    raise ScanError("Truncated chunk payload")
                root = NbtReader(inflate(raw, compression)).root()
                level = root.get("Level")
                if not isinstance(level, dict) or level.get("xPos") != chunk_x or level.get("zPos") != chunk_z:
                    raise ScanError("Chunk coordinates mismatch")
                tiles = level.get("TileEntities")
                if not isinstance(tiles, list):
                    raise ScanError("Missing TileEntities list")
                for tile in tiles:
                    if not isinstance(tile, dict):
                        raise ScanError("Invalid tile compound")
                    if tile.get("id") == TILE_ID:
                        try:
                            emit(machine_hint(tile, dimension, chunk_x, chunk_z))
                        except ScanError as error:
                            issue(str(error), path, chunk_x, chunk_z)
            except (ScanError, OSError, struct.error, IndexError) as error:
                issue(str(error), path, chunk_x, chunk_z)
    if fingerprint(path) != before:
        raise ScanError("Region changed during scan; discard this output")
    return len(rows)


def load_manifest(world):
    path = contained(world, world / "ttn-directory" / "manifest.json")
    manifest = json.loads(read_stable(path, 1024 * 1024).decode("utf-8"))
    if manifest.get("format") != 1 or not isinstance(manifest.get("dimensions"), list) or len(manifest["dimensions"]) > 4096:
        raise ScanError("Invalid dimension manifest")
    world_id = str(uuid.UUID(manifest["worldId"]))
    lines = ["ttn-directory-manifest-v1\n", world_id + "\n"]
    ids = set()
    for row in manifest["dimensions"]:
        if type(row["id"]) is not int or not -(1 << 31) <= row["id"] < (1 << 31) or row["id"] in ids:
            raise ScanError("Duplicate/invalid dimension ID")
        ids.add(row["id"])
        for key in ("provider", "folder", "problem"):
            if not isinstance(row[key], str) or any(c in row[key] for c in "\r\n\t"):
                raise ScanError("Invalid manifest field")
        folder = row["folder"]
        if folder.startswith(("/", "\\")) or ":" in folder or ".." in folder or "\\" in folder:
            raise ScanError("Unsafe dimension folder")
        contained(world, world / folder)
        lines.append("{}\t{}\t{}\t{}\n".format(row["id"], row["provider"], folder, row["problem"]))
    digest = hashlib.sha256("".join(lines).encode("utf-8")).hexdigest()
    if digest != manifest.get("manifestDigest"):
        raise ScanError("Manifest digest mismatch")
    catalogue = contained(world, world / "data" / "ttn_machine_directory.dat")
    data = NbtReader(inflate(read_stable(catalogue, MAX_COMPRESSED), 1)).root().get("data")
    if not isinstance(data, dict) or data.get("Format") not in (1, 2) or nbt_uuid(data, "WorldUUID", True) != world_id or data.get("ManifestDigest") != digest:
        raise ScanError("Manifest and source-world catalogue do not match; export and back up again")
    return manifest


def scan_world(world, manifest):
    entries, positions, problems = [], set(), []
    issue_count = 0
    regions = chunks = 0

    def issue(reason, path=None, x=None, z=None):
        nonlocal issue_count
        issue_count += 1
        if len(problems) < 200:
            problems.append(dict(reason=reason, file=str(path.relative_to(world)) if path else None, chunkX=x, chunkZ=z))

    def emit(entry):
        address = tuple(entry[key] for key in ("dimension", "x", "y", "z"))
        if address in positions:
            raise ScanError("Duplicate machine address")
        if len(entries) >= MAX_HINTS:
            raise ScanError("Import hint limit exceeded; coverage is incomplete")
        positions.add(address)
        entries.append(entry)

    mapped = set()
    source_files = {}
    for row in manifest["dimensions"]:
        if row["problem"]:
            issue("Dimension {}: {}".format(row["id"], row["problem"]))
            continue
        region_dir = contained(world, world / row["folder"] / "region")
        if region_dir in mapped:
            issue("Duplicate region folder mapping", region_dir)
            continue
        mapped.add(region_dir)
        if not region_dir.is_dir():
            # Cannot distinguish a never-generated dimension from an incomplete backup.
            issue("Dimension has no saved region directory; not certified empty", region_dir)
            continue
        with os.scandir(region_dir) as files:
            for file in files:
                path = Path(file.path)
                if file.is_symlink():
                    issue("Symlink storage is not supported", path)
                    continue
                if not file.is_file() or path.suffix not in (".mca", ".mcr", ".mcc"):
                    issue("Unrecognized region storage entry", path)
                    continue
                if path.suffix != ".mca":
                    issue("Unsupported region storage format", path)
                    continue
                regions += 1
                if regions > MAX_REGIONS:
                    raise ScanError("Region scan limit exceeded")
                source_files[path] = fingerprint(path)
                try:
                    chunks += scan_region(path, row["id"], emit, issue)
                except (ScanError, OSError, struct.error) as error:
                    issue(str(error), path)
    # Detect old/removed/unmapped dimensions, not just DIMn; do not follow links.
    visited = 0
    for parent, dirs, files in os.walk(world, followlinks=False):
        visited += 1
        if visited > 10000:
            issue("Unmapped-folder search limit exceeded")
            break
        for name in list(dirs):
            path = Path(parent) / name
            if path.is_symlink():
                issue("Symlink subtree not scanned", path)
                dirs.remove(name)
        if Path(parent).name == "region" and Path(parent).resolve() not in mapped:
            issue("Region directory has no dimension/provider mapping", Path(parent))
        if any(name.endswith((".mca", ".mcr", ".mcc")) for name in files) and Path(parent).resolve() not in mapped:
            issue("Unmapped region storage files", Path(parent))
    for path, before in source_files.items():
        if fingerprint(path) != before:
            raise ScanError("Source changed during scan; no output may be imported")
    identities = {}
    for entry in entries:
        if entry["machineId"] is not None:
            identities.setdefault(entry["machineId"], 0)
            identities[entry["machineId"]] += 1
    conflicts = sum(count > 1 for count in identities.values())
    result = dict(format=1, worldId=manifest["worldId"], manifestDigest=manifest["manifestDigest"],
                  complete=issue_count == 0, entries=entries)
    report = dict(complete=result["complete"], regionFiles=regions, chunkRecords=chunks, machines=len(entries),
                  provisionalMachines=sum(entry["machineId"] is None for entry in entries),
                  conflictingIdentities=conflicts, issueCount=issue_count, issues=problems,
                  note="Discovery hints only; no permissions, prices or inventories. Runtime validation is still required.")
    return result, report


def run(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--world-backup", type=Path, required=True)
    parser.add_argument("--out-dir", type=Path, required=True)
    parser.add_argument("--confirm-stopped-backup", action="store_true")
    args = parser.parse_args(argv)
    if not args.confirm_stopped_backup:
        parser.error("Stop Minecraft/server and use a complete backup, then pass --confirm-stopped-backup")
    world, output = args.world_backup.resolve(strict=True), args.out_dir.resolve()
    if not world.is_dir() or not (world / "level.dat").is_file():
        raise ScanError("Expected a complete world backup with level.dat")
    if output == world or output.is_relative_to(world) or output.exists():
        raise ScanError("Output must be a NEW directory outside the source backup")
    # Immutable snapshot contract. Metadata checks detect common mistakes, not all concurrent writes.
    anchors = [contained(world, world / name) for name in
               ("level.dat", "data/ttn_machine_directory.dat", "ttn-directory/manifest.json")]
    if (world / "session.lock").exists():
        anchors.append(contained(world, world / "session.lock"))
    before = {path: hashlib.sha256(read_stable(path, MAX_COMPRESSED)).digest() for path in anchors}
    manifest = load_manifest(world)
    result, report = scan_world(world, manifest)
    for path, digest in before.items():
        if hashlib.sha256(read_stable(path, MAX_COMPRESSED)).digest() != digest:
            raise ScanError("Source changed during scan; output discarded")
    import_text = json.dumps(result, ensure_ascii=False, separators=(",", ":")) + "\n"
    if len(import_text.encode("utf-8")) > 8 * 1024 * 1024:
        raise ScanError("Import byte limit exceeded; no import written")
    output.mkdir(parents=True, exist_ok=False)
    for name, value in (("report.json", report),):
        with (output / name).open("x", encoding="utf-8", newline="\n") as stream:
            json.dump(value, stream, ensure_ascii=True, indent=2)
            stream.write("\n")
    with (output / "import.json").open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(import_text)
    print("{} machines; {} issues. Coverage {}. Output: {}".format(
        report["machines"], report["issueCount"], "complete" if result["complete"] else "INCOMPLETE", output))
    return 0 if result["complete"] else 2


if __name__ == "__main__":
    try:
        sys.exit(run())
    except (ScanError, OSError, KeyError, TypeError, ValueError) as error:
        print("Scan refused/failed: " + str(error), file=sys.stderr)
        sys.exit(1)
