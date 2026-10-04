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
- At most 100000 directory records and 256 shared-tab memberships.
  Membership is presentation, not a permission or economic authority.
- Port.14 adds a bounded plain-text shared-tab label to server-to-client directory
  replies, including empty/error results. Only the server reads `specialTabName`;
  accepted screen-correlated replies update the client's display label. Blank
  selects a translated default; client config and return bookmarks cannot override
  the server label. No new client-to-server setting or world-data field is added.
  Exact-version peer checks reject pre-port.14 clients/servers. Existing saved
  membership, filter enum and directory/tile/preview schemas are unchanged.
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

## Port.16 additions

- Logging consumes the immutable committed receipt through the request ledger's
  post-record publication path, never a client-provided receipt. Replay/failure
  paths do not append another success. Chat and log sinks are isolated from each
  other and cannot roll back or retry a committed trade. Per-world JSONL files are
  rotated at 10 MiB with five archives; server lifecycle owns the writer. Writes
  flush synchronously, so slow storage can affect ticks. There is no unbounded
  async queue, fsync guarantee or crash-atomic coupling to inventory saves.
- Purchase totals are client presentation only: validated batch counts multiplied
  by public definition quantities using long arithmetic. They neither reserve
  stock nor alter trade execution, payment selection or permission checks.
- Favorites belong to the authenticated player's persisted Forge player NBT,
  scoped by directory WorldUUID, with at most 256 machine UUIDs. The packet never
  selects another player. Mutations are idempotent set operations, require a current
  world/revision cursor, and share existing bounded directory ingress scheduling.
  Favorite membership grants no authority and does not change global directory revision.
- Favorite filtering occurs in the bounded server query scan before search/paging,
  using a snapshot of that player's membership. Existing rows carry a personal
  favorite flag; the appended FAVORITES enum preserves existing tab ordinals.
  Request/response layouts changed, so exact-version peer matching is mandatory.
- Directory/tile/preview schemas remain 2/3/1. Favorites introduce a separate,
  namespaced player-data record; backups should include the whole world/player data.

## Build invariants

Java 8 bytecode, pinned Forge/Gradle/mappings, no bundled optional dependencies,
no arbitrary numeric registry IDs. Hard constants inside Forge `@Config` classes
must be excluded with `@Config.Ignore`; the exact Forge config-sync regression
test remains part of the source. Keep namespace/mod ID stable for saved worlds.
