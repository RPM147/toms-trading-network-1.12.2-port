# 0.3.4-port.18 — Stabilization, localization, UX & query refinements

Date: `2026-11-19`. Tag: `v0.3.4-port.18`. Regular release, **not a pre-release**.
CurseForge upload and installation into a live instance are separate operations.

## Changes

### Phase 1 — Stabilization, rate-limit & ledger reliability
- **Rate-limit quota isolation & bounded busy replies:** Separated 2/s
  sub-ceilings in `RequestBudget` for directory queries (`admitDirectory`),
  remote opens (`admitRemoteOpen`), and trade-book actions (`admitBook`), while
  retaining the shared 40-request/s total limit. Rejections now return bounded
  `BUSY` replies (`DirectoryPage.Result.BUSY`, `TradeBookNetwork.Result.BUSY`,
  `RemoteOpenResult.BUSY`, and `book.busy` on binding) instead of silently
  dropping packets and locking clients for 15 seconds. Added per-connection
  reopen spacing in `GuiMachineDirectory` (`550 ms`).
- **Remote open/cancel queue fix:** Split `RemoteNetwork` queuing into separate
  `QUEUED_OPEN` and `QUEUED_CANCEL` sets and guarded `addScheduledTask` against
  scheduling failures so an immediate `Cancel` is never dropped by a pending `Open`.
- **Trade-book item NBT slimming:** Stopped writing up to 24 KiB of cached
  `pages` NBT into held `WRITABLE_BOOK` items on bind/open (`TradeBooks`); legacy
  `port.17` books have cached `pages` stripped automatically on first open while
  preserving the `TTNTradeBook` bearer key.
- **Trade-book diagnostics & cleanup:** Logged corrupt/unsupported `ttn_trade_books`
  NBT errors in `TradeBookData` while preserving unknown payloads, and added
  level-2 commands `/ttndirectory books status`, `/ttndirectory books prune`, and
  `/ttndirectory books revoke <machine-uuid>`.
- **Core safety, GUI & build checks:** Guarded `CommonEvents.onBlockBreak` against
  null players on quarantined machines, bounded `GuiMachine` offer quantities to
  `<= 1024` with `KEY_NUMPADENTER` / repeat guards, and updated
  `verifyReleaseArtifact` `requiredEntries` to cover all `port.16`–`port.18`
  classes.

### Phase 2 — Localization, UX & low-risk optimizations
- **Full Turkish localization & translated chat receipts:** Restored the 58
  missing core/GUI/tooltip/The One Probe keys in `tr_tr.lang`, localized
  `"Kitap ve Tüy"` in book messages, enforced full Turkish key/`%s` parity in
  `Phase7ResourcesTest`, and converted `TradeReceiptChat` to `TextComponentTranslation`
  (`chat.toms_trading_network.receipt.*`) across `en_us`, `tr_tr`, and `es_mx`.
- **Favorite page, scroll & selection retention:** Removed the `page == 0`
  restriction on favorite mutations while keeping `expectedWorld`/`expectedRevision`
  validation intact; toggling favorites now preserves your directory page, scroll
  offset, and selection (stepping back one page if the last row on a later
  `Favorites` page is removed).
- **Administrator shared-tab UX:** Enriched `/ttndirectory special list [page]`
  with machine name, owner, dimension/coordinates, and `[missing/stale]` /
  `[identity conflict]` / `[unverified]` / `[unsupported]` labels, and added
  `/ttndirectory special prune`.
- **Low-risk query & UI refinements:** Indexed `ECONOMY` and `FAVORITES` directory
  queries via `MachineDirectoryData` (`orderedSpecialRecords` /
  `orderedRecordsFor`), checked `world instanceof WorldServer` and the 20-tick
  schedule ahead of world/tile lookups in `TileVendingMachine.update()`, and
  cached per-frame totals in `PurchaseTotalsDisplay.Cache`. Asynchronous
  `RotatingTradeLog.append()` queuing remains intentionally deferred until disk
  profiling demonstrates a need.

## Installation and limitations

- Minecraft **1.12.2**, Forge target **14.23.5.2859**, Java **8**.
- Update the host/server **and every client** to `0.3.4-port.18`, replacing the
  old TTN JAR (`TradeBookNetwork.Result.BUSY`, favorite paging, and chat receipt
  keys are not compatible with `port.17` or `port.18-dev`). Never install two
  TTN JARs together. Back up the stopped world first.
- No manual config change or migration of existing machine, directory, or book
  saves is required.
- [Usage guide](https://github.com/RPM147/toms-trading-network-1.12.2-port/blob/v0.3.4-port.18/docs/USAGE.md)
  / [architecture](https://github.com/RPM147/toms-trading-network-1.12.2-port/blob/v0.3.4-port.18/docs/ARCHITECTURE.md).

## Verification

Java 8 compilation, full JUnit suite execution, reobfuscation, packaging, and
`verifyReleaseArtifact` passed on Windows: **300 JUnit tests in 53 classes passed**
with zero failures, errors, or skips (`cleanTest test`). In-game GUI/localization,
multi-page favorites, admin commands, save/restart, and multiplayer acceptance
remain manual checks; no live instance was launched or modified, and no runtime
TPS/CPU/disk profile has been recorded.

Artifact: `toms_trading_network-1.12.2-0.3.4-port.18.jar` (**409342 bytes**).

```text
33fc6889e37f838b5192b41d5cd3434507e62eeb6d29ee058996cb54bfbe4269  toms_trading_network-1.12.2-0.3.4-port.18.jar
```

See the [validation scope](https://github.com/RPM147/toms-trading-network-1.12.2-port/blob/v0.3.4-port.18/docs/VALIDATION.md).
Original project by **tom5454**;
unofficial port maintained by **RPM147**. MIT attribution is preserved.
