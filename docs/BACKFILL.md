# Discovering machines in an existing world

Use this only on a **complete backup made after the game/server has fully stopped**.
Try an isolated copy first. The offline scanner reads source files without modifying
block/player/inventory NBT and writes output to a new folder outside the backup.
It is not safe to scan a live or concurrently modified world.

## Procedure

1. Back up the complete existing world before installing/upgrading the port.
2. In the isolated world with the port loaded, an operator runs `/ttndirectory export`.
   This creates `ttn-directory/manifest.json` and binds it to the saved catalogue.
   An existing manifest is not overwritten; archive it explicitly before a new export.
3. Stop the server normally. Copy the entire world again, including `level.dat`,
   `data/ttn_machine_directory.dat`, the manifest and all dimension folders.
4. From `Forge-1.12.2/`, use Python 3.9+ and replace the placeholders below:

   ```sh
   python scripts/scan-machine-directory.py --world-backup "BACKUP_WORLD" --out-dir "NEW_OUTPUT_DIRECTORY" --confirm-stopped-backup
   ```

5. Read `report.json`. Incomplete coverage, corrupt/unsupported data, missing regions
   and capacity limits must not be reported as complete discovery.
6. Copy the reviewed `import.json` into that same source world's `ttn-directory/`
   folder. Start the isolated server and run `/ttndirectory import import.json`.
7. Use `/ttndirectory status`. Targets are verified when their actual tiles load;
   imported hints alone never grant stock, ownership, remote access or special-tab membership.

The confirmation flag is an operator assertion, not automatic detection of all
Minecraft processes. Hash/fingerprint checks detect some races, not all live-save hazards.
Exit codes: 0 complete scan coverage, 2 incomplete coverage with report, 1 failure/refusal.
Even exit 0 is not a live stock/protection/trade acceptance result.

## Maintenance commands (permission level 2)

| Command | Effect |
|---|---|
| `/ttndirectory status` | Catalogue state and identity conflict count |
| `/ttndirectory export` | Save-bound provider/folder manifest; refuses existing output |
| `/ttndirectory import import.json` | Import bounded hints for matching WorldUUID and current manifest digest |
| `/ttndirectory reindex` | Queue known addresses for loaded-only reconciliation; no disk scan/chunk loads |
| `/ttndirectory renew <dimension> <x> <y> <z>` | Explicitly renew a supported loaded machine's UUID; no owner/inventory changes |

Dimensions may use custom folders, not just `DIMn`. If provider/folder mappings
change, export and scan a new complete backup. The manifest digest is a consistency
check, not an authentication signature against an administrator editing the world.

Import accepts at most 10000 hints / 8 MiB. Directory capacity is 100000 addresses.
Observed records are not overwritten by old hints. Duplicate UUIDs are not merged:
inspect the actual target and explicitly repair the intended copy if appropriate.
After `renew`, special-tab membership does not transfer to the new identity.

## Rollback and corrupt data

Do not delete catalogue files to bypass a format error. Reindex does not repair a
corrupt catalogue. Stop, preserve all data and investigate on a disposable backup.
A newly created catalogue has a different WorldUUID and invalidates old imports.
Do not downgrade a tile-v3 / directory-v2 world by replacing only the JAR; restore
the complete consistent world/player/catalogue backup instead.

Synthetic scanner tests exist. A universal real-world migration/rollback guarantee
is not claimed; test the procedure on your exact pack copy first.
