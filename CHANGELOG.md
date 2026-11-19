# Changelog

## 0.3.4-port.18 — Stabilization, localization, UX & query refinements (2026-11-19)

### Phase 2 (2026-11-19)

- **Full Turkish localization & translated chat receipts:**
  - Completed the missing 58 core/GUI/tooltip/TOP keys in `tr_tr.lang` and
    localized `"Kitap ve Tüy"` in book messages.
  - Updated `Phase7ResourcesTest` so `tr_tr.lang` is verified against `en_us.lang`
    for complete key parity, non-empty values, and `%s` placeholder counts.
  - Converted public chat announcements in `TradeReceiptChat` from hardcoded
    English strings to `TextComponentTranslation` components (`chat.toms_trading_network.receipt.*`)
    with translations in `en_us.lang`, `tr_tr.lang`, and `es_mx.lang`.
- **Favorite toggle page, scroll & selection retention:**
  - Removed the `page == 0` restriction on favorite mutations (`DirectoryQuery`,
    `PlayerFavorites`) while keeping world UUID and revision validation intact.
  - Updated `DirectoryClientState` and `GuiMachineDirectory` to stay on the
    current page, restore scroll position, and keep the toggled machine selected;
    unfavoriting the sole remaining row on page `> 0` of `Favorites` steps back
    one page cleanly.
- **Administrator shared-tab UX (`special list` & `special prune`):**
  - Enriched `/ttndirectory special list [page]` rows with each machine's name,
    owner, dimension/coordinates, and `[missing/stale]`, `[identity conflict]`,
    `[unverified]`, or `[unsupported]` status.
  - Added `/ttndirectory special prune` (`MachineDirectoryData.pruneSpecial()`) to
    remove missing machine UUIDs from the shared tab without affecting active
    entries.
- **Low-risk source-level performance refinements:**
  - Ordered `TileVendingMachine.update()` to check `world instanceof WorldServer`
    and the 20-tick modulo schedule before `automationWorldReady()` world/tile
    lookups when no pending transfer exists.
  - Added `PurchaseTotalsDisplay.Cache` in `GuiMachine` and `GuiRemoteTrading` to
    avoid recomputing identical totals three times per render frame.
  - Updated `DirectoryPager` to iterate indexed identities (`orderedSpecialRecords`
    / `orderedRecordsFor`) for `ECONOMY` and `FAVORITES` tabs instead of scanning
    the entire directory, with a fallback above `RECORDS_PER_TICK` for safety.
  - Asynchronous `RotatingTradeLog.append()` queuing remains intentionally
    deferred until disk-latency profiling demonstrates a need.
- **Exact-version parity:** Finalized package version at `0.3.4-port.18`
  (rejecting `0.3.4-port.17` and `0.3.4-port.18-dev` peers).

### Phase 1 (2026-11-19)

- **Rate-limit quota isolation & bounded busy replies:**
  - Separated `RequestBudget` sub-limits for directory browsing (`admitDirectory`),
    remote opens (`admitRemoteOpen`), and trade-book actions (`admitBook`) while
    preserving the shared 40-request/second global ceiling.
  - Added bounded rejection feedback (`DirectoryPage.Result.BUSY`,
    `TradeBookNetwork.Result.BUSY`, `RemoteOpenResult.BUSY`, and `book.busy`
    when binding) up to 4 notices per window, preventing 15-second client lockups.
  - Added client-side directory reopen spacing (`DirectoryClientState` /
    `GuiMachineDirectory`) so closing and immediately reopening the directory on
    the same connection respects the 550 ms request interval.
  - Added `gui.toms_trading_network.book.busy` translations in `en_us.lang`,
    `tr_tr.lang`, and `es_mx.lang`.
- **Remote open/cancel queue race & leak fix:**
  - Split `RemoteNetwork` queuing into separate `QUEUED_OPEN` and `QUEUED_CANCEL`
    sets so a queued open no longer drops an immediate cancel packet.
  - Wrapped `addScheduledTask` in a `try/catch` to release queue membership if task
    scheduling fails.
- **Trade book item NBT slimming:**
  - Stopped writing up to 24 KiB of cached `pages` NBT into held `WRITABLE_BOOK`
    items on bind/open (`TradeBooks`), avoiding redundant slot resyncs and
    Shulker Box NBT bloat. Legacy `port.17` books have cached `pages` stripped
    on first read while preserving the server-issued `TTNTradeBook` binding.
- **Trade book diagnostics & orphan cleanup:**
  - Logged corrupt/unsupported `ttn_trade_books` NBT failures in `TradeBookData`
    instead of silently ignoring them, while continuing to preserve unknown payloads.
  - Added `TradeBookData.revoke`, `pruneAbsent`, and capacity counters, exposed via
    level-2 commands `/ttndirectory books status`, `/ttndirectory books prune`, and
    `/ttndirectory books revoke <machine-uuid>`.
- **Core safety, GUI polish & release verification:**
  - Guarded `CommonEvents.onBlockBreak` against null `event.getPlayer()` on
    quarantined machines.
  - Bounded `GuiMachine` offer quantity input to `<= 1024`, added `KEY_NUMPADENTER`
    and repeat-event guards, and replaced fragile `buttonList` index lookups.
  - Updated `verifyReleaseArtifact` `requiredEntries` in `build.gradle` to check
    all `port.16` and `port.17` production classes.
- **Exact-version parity (Phase 1):** Bumped wire protocol for
  `TradeBookNetwork.Result.BUSY`.

## 0.3.4-port.17 — Machine-bound trade books

- Added opt-in machine ledgers: Shift-right-click an owned Vending Machine with
  one blank Book and Quill to bind it. Only the exact owner can bind; existing
  handwritten books and existing bindings are never overwritten.
- Right-click a bound book to refresh a read-only view of that machine's recent
  completed trades. Anyone holding the book can read it. The item tooltip shows
  the machine, owner and original binding address.
- Compact entries contain only buyer, seller, actual delivered items/counts and
  actual paid items/counts. Matching item variants with the same ID/metadata/label
  are grouped. No timestamps, raw NBT or UUIDs appear in the book's trade lines.
- Book views show buyer/seller role labels on separate lines, localized to the
  reader's language. Existing compact records gain the same labels without migration.
- Recording begins at the first binding and continues without a book being held.
  A replacement blank book bound to the same machine recovers retained history.
  Stable world/machine identities prevent coordinate replacements inheriting it.
- Added bounded save-owned book data: up to 100 entries per machine, 8192 entries
  and 2 million text characters globally, with 4096 tracked machines. Reading uses
  a 24 KiB snapshot and at most 50 rendered pages, newest trades first.
- Kept JSONL transaction logs and public chat independent. Failed/replayed trades
  do not create extra book entries; a book sink failure does not retry a purchase.
- Added bounded, held-item-verified and request-correlated book packets, plus
  English, Turkish and Mexican Spanish messages. Install port.17 on every peer.
  Directory/tile/preview formats remain 2/3/1; book data introduces its own format 1.
- Regular GitHub release, not a pre-release. No live-instance installation or
  CurseForge upload is implied; in-game acceptance remains separately documented.

## 0.3.4-port.16

- Added server transaction logs for committed local/remote purchases, with actual
  item quantities, UTC timestamp and player/machine/owner identities. JSONL files
  rotate at 10 MiB with five archives. New `general.transactionLog` setting defaults
  to true and works independently of public chat announcements.
- Added selected-batch purchase totals to both trade screens, item-by-item hover/F1
  details and per-item total tooltips. Invalid/empty counts no longer enable buying.
- Added personal Favorites tab and [+]/[*] row controls; press F on a keyboard-selected
  row. Favorites persist in world-scoped player data, are limited to 256 UUIDs, and
  are filtered server-side before pagination/search without loading chunks.
- Kept private machine inventories, shared-tab membership and trade permissions
  unchanged. Favorites grant no access. Request correlation/rate limits are retained.
- Extended directory packets; install port.16 on the server/host and every client.
  Existing directory/tile/preview formats remain unchanged. Regular release, not pre-release.
- Completed missing Spanish directory-preview translations found by the resource checks.

## 0.3.4-port.15

- Simplified directory rows (G key) in both All machines and the shared tab:
  only the machine name and owner remain on the left.
- Removed duplicate Receive/Pay and Recorded/access-status lines from the rows.
  Right-hand payment/output icons, quantities and the trade arrow are unchanged.
- Full offer/status information remains in hover details and F1. Actual errors,
  item-name search, navigation and server-side access checks are unchanged.
- Kept row height and preview/hit-test geometry to accommodate multi-item trades
  at narrow GUI widths. Removed the unused per-frame item-summary formatter.
- No config, inventory, saved-data or network-format changes from port.14.
  Exact-version matching still requires port.15 on the server/host and every client.

## 0.3.4-port.14

- Added server-side `general.specialTabName` in `config/toms_trading_network.cfg`
  for the administrator-selected shared directory tab. Empty retains the
  translated Currency & Tax default; custom names support other uses such as
  server shops or event vendors. Restart the server/host after editing.
- Sync the bounded plain-text label to every player; local client settings cannot
  override it. Long button labels have ellipsis, full hover text and F1 details.
- Administration command replies use the configured name. Existing machine
  membership, inventories, ownership, trading rules and saved schemas are unchanged.
- Directory replies now include the label. Upgrade server/host and all clients
  together; port.13 and port.14 cannot connect to each other.
- Published as a regular release, not a pre-release. This channel choice does not
  imply universal compatibility; see the documented validation scope.

## 0.3.4-port.13 — Beta

- Updated the displayed mod name, canonical repository URL and port maintainer credits.
- Preserved tom5454's original attribution and MIT license; disclosed AI-assisted port work.
- Bumped both embedded and Gradle versions; port.12 binaries remain unchanged.
- No gameplay or network/data schema changes. Exact-version handshake still requires
  port.13 on both host/server and every client; port.12 and port.13 cannot be mixed.

### Source-repository preparation

- Created a port-only source repository based on `0.3.4-port.12`.
- Replaced internal project notes with public usage, build, architecture and publishing guides.
- Removed the personal instance-directory default from optional API resolution;
  use `deps/remote-api` or `-PremoteApiModsDir` instead. Hash pins remain unchanged.
- Made the Java compiler path check select `javac.exe` on Windows and `javac`
  elsewhere. Non-Windows execution is not yet verified.
- No gameplay code, mod identity, resource metadata or network/data format change.

## 0.3.4-port.12

- Product/payment-first directory rows and search across recorded offer item labels/IDs.
- Restore directory search, tab, page, scroll and exact machine selection after a trade,
  using a fresh server snapshot rather than stale offers or permissions.
- Keyboard focus/navigation and wrapped errors with paginated F1 details.

## 0.3.4-port.11

- Shared Currency & Tax tab, managed through level-2 administrator commands.
- World-owned MachineUUID membership and directory format 2 with format-1 migration.

## 0.3.4-port.10

- Fixed Forge config initialization attempting to modify a static final chunk-limit
  constant. The hard limit remains immutable and excluded from config syncing.

## Earlier port development

- Native Forge 1.12.2 machines, ownership/inventory authorization, transactional
  trading, integer quantities, sided automation and optional integrations.
- Save-bound directory, temporary remote sessions, bounded chunk preparation,
  public successful-trade receipts and cached public offer previews.

Historical port.9 has a known startup crash and is not a recommended release.
