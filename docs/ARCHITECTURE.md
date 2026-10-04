# Architecture and safety boundaries

This document describes intended code invariants, not a certification of every
third-party mod combination. Gameplay remains server-authoritative.

## Local trading

- Vanilla Forge blocks/TileEntities, containers, capabilities, events and typed
  packets; no coremod/mixin dependency.
- Exact OwnerUUID controls configuration/private stock/earnings access. Level-2
  recovery is limited to genuinely ownerless machines. Offline does not mean ownerless.
- Local container checks retain same-world / eight-block reach constraints.
- Offer quantities use validated integers, not byte-sized stack serialization.
- Trades use synchronous planning/preflight, offer revisions and guarded inventory
  mutation. Successful whole units determine partial-batch receipts.
- Request budgets, per-session request IDs and replay results prevent duplicate
  execution. Replays and chat receipts are not crash-persistent exactly-once storage.

## Directory and remote access

- Save-owned overworld WorldSavedData (`ttn_machine_directory`), WorldUUID,
  address-ordered metadata, stable MachineUUID and explicit conflict handling.
- Current schemas: directory 2 (reads 1), machine tile 3, offer preview 1.
  Unknown/corrupt directory or tile data is not silently overwritten.
- At most 100000 directory records and 256 shared Currency & Tax memberships.
  Membership is presentation, not a permission or economic authority.
- Query scans use a shared 4096-record/tick budget, 64 jobs and 256-record slices.
  Pages hold at most 50 rows / 64 KiB; preview payloads are bounded public data,
  without raw item NBT, earnings or real stock.
- Screen/request, connection, dimension, save UUID and revision correlation reject
  late pages. Selection is bound to the rendered row's full identity and address.
- Return bookmarks are client presentation only. Fresh server pages must be
  validated before restoring selection; no stale quote or session is restored.
- Remote containers contain the player's 36 inventory slots and public offer,
  not remote machine inventory/configuration slots.
- Remote access evaluates actual target/player state. MineColonies and Wizardry
  adapters are version-guarded; unknown policy is unavailable, not a grant.
  Other protection systems need independent review; universal claim support is not claimed.

## Bounded loading and lifecycle

With the reviewed Wizardry adapter, target protection checks use the full search
neighborhood, capped at 25 chunks per footprint, eight target chunks and 64 shared
physical chunks server-wide. Without Wizardry, only the target chunk is leased.
At most one cold chunk preparation per five ticks occurs globally. Existing saved
regions are required; no terrain-generation fallback is used.

Pending opens are bounded to 64, one/player, with a ten-second preparation deadline.
Sessions have 30-second idle and 120-second absolute ceilings. Shared chunks share
leases, not permission decisions. Cancellation/failure/logout/world changes and
server stop release handles; failed releases continue to reserve quota until teardown.

Forge load callbacks and exact-pack interactions still require runtime validation.
Never weaken authorization or quotas to make an unavailable target appear usable.

## Build invariants

Java 8 bytecode, pinned Forge/Gradle/mappings, no bundled optional dependencies,
no arbitrary numeric registry IDs. Hard constants inside Forge `@Config` classes
must be excluded with `@Config.Ignore`; the exact Forge config-sync regression
test remains part of the source. Keep namespace/mod ID stable for saved worlds.
