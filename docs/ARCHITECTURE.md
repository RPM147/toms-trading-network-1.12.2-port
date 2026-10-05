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

## Port.17 bound trade books

- Binding uses a native Shift-right-click on a supported machine, preserving
  Forge interaction cancellations. The server checks the exact owner, active
  player, same world, eight-block reach, one blank writable book and unique live
  machine identity. Existing text or binding markers cannot be overwritten.
- Save-owned `ttn_trade_books` format 1 records the directory WorldUUID, stable
  MachineUUID and a server-issued random UUID read key. A replacement at the same
  coordinates cannot inherit a binding. Multiple books for the same machine share
  the retained ledger. No chunk/TileEntity references or per-player ticking are kept.
- Possession of a valid bound book grants read access, not ownership or trading
  authority. Requests contain only a nonce, player dimension and hand; the server
  derives the world/machine/key from the real held item instead of accepting an
  arbitrary target from the client. Book keys and world data must remain private.
- Open requests share the existing ingress budget and permit at most one queued
  task per player. Replies are bounded and correlated against connection, nonce,
  dimension, hand, held binding and a 15-second deadline. Stale replies cannot open
  a different book/world; disconnect clears client state and connection references.
- The stored item remains a writable book. The client renders a display-only
  written-book snapshot through vanilla's read-only book view, using plain text
  components with no click actions. This is not a separate authoritative client log.
- Buyer/seller captions are localized at display time on separate lines. Stored
  compact records and receipt quantities are unchanged; no save/protocol migration
  is needed, and existing entries gain captions when opened in the updated view.
- Recording consumes the immutable committed receipt after request-ledger
  publication, never client text. The book, JSONL and chat sinks are isolated;
  replay/failure paths do not append extra successes and book storage errors do
  not roll back/retry the already committed purchase. Amounts are actual paid/
  delivered totals. Recording starts at first binding; no historical backfill.
- Retention is bounded: 4096 machine registrations, 100 entries/machine, 8192
  entries and 2 million UTF-16 text units globally, 8192 text units/entry. An
  insertion-ordered record table plus per-machine deque index avoids scanning all
  records on a purchase. Oldest records are evicted when a bound is exceeded.
- Snapshots contain newest complete entries first, at most 24 KiB of UTF-8 data.
  Client wrapping caps display at 50 pages of 14 lines; any single oversized row
  is visibly shortened. Unsupported/corrupt book NBT is preserved and unavailable.
  Book data uses normal world saves, with no per-trade fsync or crash-atomic link
  to inventory persistence. Back up the entire world, not just the JAR.
- Directory/tile/preview formats remain 2/3/1. New appended book message types
  require port.17 on every peer under the existing exact-version handshake.

## Build invariants

Java 8 bytecode, pinned Forge/Gradle/mappings, no bundled optional dependencies,
no arbitrary numeric registry IDs. Hard constants inside Forge `@Config` classes
must be excluded with `@Config.Ignore`; the exact Forge config-sync regression
test remains part of the source. Keep namespace/mod ID stable for saved worlds.
