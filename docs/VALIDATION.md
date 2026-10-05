# Validation scope and source preparation

## Port.17 machine-bound trade books — 2026-10-05

- Java 8 compilation, reobfuscation, release packaging and `verifyReleaseArtifact`
  passed on Windows using existing caches and process-local 512 MiB heaps. Pinned
  optional API JARs were read only; no game/server or live world was modified.
- **64 focused JUnit tests in 10 classes passed**, with no failures, errors or
  skips: TradeBookDataTest (7), TradeBookEntryTest (3), TradeBookClientTest (6),
  TradeBookNetworkTest (3), BuildInfoTest (3), MachineNetworkTest (14),
  Phase7ResourcesTest (5), MachineAccessTest (8), TradeRequestLedgerTest (7),
  TradeTransactionLogTest (8). The full gameplay suite was not rerun.
- New cases cover issued-key/world/machine isolation, save/read round trips,
  retention/global capacity/UTF-8 bounds, corrupt-data preservation, blank-book
  protection, compact actual quantities and variant grouping. Replay/failure and
  a failing book sink leave the committed purchase and other sinks unchanged.
- Protocol/client cases cover truncated/trailing/oversized payloads, bad flags,
  stale nonce/world/dimension/hand/key replies, one-shot consumption, deadlines,
  switching books and bounded newest-first page layout. Resource checks include
  English, Turkish and Mexican Spanish book messages.
- Added buyer/seller role captions on separate lines, localized by the reader's
  language without rewriting saved entries. Tests cover all three label sets,
  multiline layout and preservation of unknown/incomplete records. The earlier
  draft's total of 70 tests was a counting error: that run contained 62 tests;
  the final XML reports contain 64 after these two additional cases.
- Exact target Forge/Minecraft sources were checked for writable-book handling,
  interaction packet forwarding, book text layout and read-only vanilla rendering.
  These are source/automated checks, not proof of actual in-game interactions.
- Artifact: `toms_trading_network-1.12.2-0.3.4-port.17.jar`, **399435 bytes**.
- SHA-256: `b738eb676e555443adf9b9fd33e83cd3f5a7dbb4e55a401fa873d6a298686f2b`.
- Pending manual acceptance: bind with either hand as owner; reject another owner
  and handwritten/rebound books; read local/remote actual trades; transfer/lost-book
  recovery; save/restart; GUI-scale wrapping and compatibility with the live pack.
  No in-game multiplayer session or dedicated-server launch was performed here.
- Port.17 is packaged for the regular GitHub release channel, not pre-release.
  The live instance remains unchanged; CurseForge upload is a separate step.
  [Release notes](RELEASE-port.17.md) describe usage and limits.

## Port.16 logs, totals and favorites — 2026-10-05

- Java 8 compilation, reobfuscation, release packaging and `verifyReleaseArtifact`
  passed on Windows using existing caches and process-local 512 MiB heaps. The live
  instance's pinned optional API JARs were read only; no game/server was launched.
- **122 focused JUnit tests in 20 classes passed**, with no failures, errors or skips.
  Coverage includes the new purchase-total arithmetic (5), favorite client state
  (5), player favorites (7), transaction logging (8), and existing/extended build,
  resource, directory, paging, packet, config-sync, receipt and request-ledger tests.
- Favorite cases cover player/world isolation, stable UUIDs, limits, removed-ID
  cleanup, preservation of unsupported/corrupt data, immutable snapshots, full-list
  paging/filtering and protocol bounds. Navigation cannot silently cancel a pending
  favorite mutation. Stale results are unconfirmed, not falsely reported as saved.
- Log tests use temporary files and cover JSON escaping, actual partial amounts,
  no raw NBT, immediate UTF-8 flush, restart append, interrupted-tail separation,
  rotation limits, replay/failed-trade exclusion and independent failing sinks.
- Total tests cover invalid batches/offers, multiple items, maximum supported
  quantities and safe multiplication. GUI source was reviewed for local/remote
  parity, narrow-width limits, keyboard handling and favorite click coordinates.
  Automated tests do not render Minecraft; visual accessibility remains unverified.
- Initial test compilation exposed an outdated NBT method name in a new test;
  this was corrected. Resource checks also found 11 pre-existing missing Spanish
  preview keys; those translations were added before the final successful run.
- Artifact: `toms_trading_network-1.12.2-0.3.4-port.16.jar`, **364510 bytes**.
- SHA-256: `5f9491ed3219964694f7449baf18a3a148412e39afae2de5ebc6a6a90754c3e2`.
- Pending manual checks: totals at actual GUI scales; two players with different
  favorites; save/restart and death/respawn persistence; local/remote receipts in
  the host/server log with chat off; slow-disk/real-modpack behavior. Logging is
  not a crash-atomic journal. The broad gameplay suite was not rerun.
- Live instance mods/config/saves were not changed. Release notes: [port.16](RELEASE-port.16.md).

## Port.15 simplified directory rows — 2026-10-04

- Java 8 compilation, reobfuscation, release packaging and `verifyReleaseArtifact`
  passed on Windows with existing dependency caches and process-local 512 MiB heaps.
- **40 focused JUnit tests in seven classes passed**, with no failures, errors or
  skips: BuildInfoTest (3), DirectoryClientStateTest (8), DirectoryPreviewLayoutTest
  (1), DirectoryReturnTest (6), DirectoryItemSearchTest (3), SharedEconomyTabTest
  (9), DirectoryNetworkTest (10).
- Coverage checks existing preview geometry, four-digit quantities, narrow-width
  multi-item layouts, directory state/return navigation, item-name search, shared
  membership, packet bounds and exact-version peer matching. These tests do not
  render the GUI; no in-game visual or multiplayer test was run for port.15.
- Source review confirms that row drawing now emits only machine/owner labels
  on the left. Right-hand previews, hover/F1 details and access checks are retained.
- Per-entry SHA-256 comparison against port.14 found differences only in
  `GuiMachineDirectory.class`, `GuiMachineDirectory$Entries.class`,
  `BuildInfo.class`, `TradingNetworkMod.class` (inlined version), `mcmod.info`
  and `META-INF/MANIFEST.MF`. No entries were added/removed; all other entries,
  including trading, network and persistence classes, matched byte-for-byte.
- Artifact: `toms_trading_network-1.12.2-0.3.4-port.15.jar`, **344317 bytes**.
- SHA-256: `9e573b943f573621321a84e77c1f1b1272d38cb11a7187cd3af8062b9241d64c`.
- Live instance files were not changed. Release notes: [port.15](RELEASE-port.15.md).

## Port.14 configurable shared tab — 2026-10-04

- Java 8 compilation, reobfuscation, release packaging and `verifyReleaseArtifact`
  passed on Windows with existing dependency caches. Build processes were limited
  to 512 MiB heaps and one Gradle worker; no live instance was modified or launched.
- **49 focused JUnit tests in seven classes passed**, with zero failures, errors
  or skips: BuildInfoTest (3), RemoteSettingsConfigTest (7), DirectoryNetworkTest
  (10), DirectoryClientStateTest (8), DirectoryReturnTest (6), SharedEconomyTabTest
  (9), DirectoryPagerTest (6).
- Coverage includes real Forge config save/load/sync, empty/custom/Unicode labels,
  text sanitization, invalid/truncated/oversized packets, maximum-size pages with
  the new label, stale reply rejection, client-config isolation, screen reset,
  unchanged saved membership, paging and exact-version peer rejection.
- Artifact: `toms_trading_network-1.12.2-0.3.4-port.14.jar`, **344824 bytes**.
- SHA-256: `7013dc01d7537910e4059da47ac1ea00002b1fbe5ad65dd902852162423ae6b2`.
- The actual rendered tooltip/button at different GUI scales, a fresh two-client
  config-sync session and the full gameplay regression suite were not rerun for
  this change. Previous multiplayer feedback is not a port.14 runtime test.
- Port.14 uses the regular release channel (not pre-release) at the maintainer's
  request. The channel label is not a compatibility or runtime certification.
  See [release notes](RELEASE-port.14.md).

## Port.13 package checks — 2026-10-04

- Java 8 compilation, reobfuscation, deterministic release packaging and
  `verifyReleaseArtifact` passed on Windows with existing dependency caches.
- `BuildInfoTest`: 3 tests passed, 0 failures/errors/skips. This focused run checks
  identity, target/version agreement and the exact-version peer gate; the full
  gameplay suite was not rerun for the metadata-only change.
- Initial reobfuscation failed because the build JVM could not allocate native
  memory. Retrying with process-local `_JAVA_OPTIONS=-Xms64m -Xmx512m
  -XX:CICompilerCount=2`, a 512 MiB Gradle heap and `--max-workers=1` passed.
  No persistent JVM settings, game processes or live instance files were changed.
- Artifact: `toms_trading_network-1.12.2-0.3.4-port.13.jar`, 343624 bytes.
- SHA-256: `579094a15fba44567133ee87ae490bbbcad2e2811214359929ddebcd0b23452b`.
- The final JAR's version/name/URL/credits were read back and verified.
  A per-entry SHA-256 comparison against port.12 found changes only in
  `META-INF/MANIFEST.MF`, `mcmod.info`, `BuildInfo.class`, and
  `TradingNetworkMod.class` (inlined display-name/version constants).
  No entries were added or removed; every other entry matched byte-for-byte.
- No port.13 game/client/server was launched. Host/server and all clients must
  upgrade together despite unchanged gameplay; exact-version matching is retained.
- Package verification and publication are separate operations. See the
  [release notes](RELEASE-port.13.md) and
  [GitHub release page](https://github.com/RPM147/toms-trading-network-1.12.2-port/releases/tag/v0.3.4-port.13)
  for distribution; no new gameplay acceptance is implied by publication.

## Source snapshot — 2026-10-04

The clean repository was prepared from the working `0.3.4-port.12` source.
All **145 files under src/** (production, resources and tests) were compared by
SHA-256 with the working copy and matched. The copied scanner/test scripts and
build wrapper are retained; private instance files and historical evidence archives
are deliberately not published here.

Build-only changes are the generic ignored `deps/remote-api` default and selection
of the platform-appropriate Java compiler executable. No gameplay source, resources,
network format or persistent schema changed during that initial extraction.
Port.13 subsequently updates version/display-name constants and package metadata
to the port repository while preserving upstream credit. Gameplay is unchanged.

## Checks in the clean checkout

- Java 8 wrapper: `compileJava releaseJar verifyReleaseArtifact` passed on Windows.
- Optional API files were supplied through the explicit `remoteApiModsDir` command
  option, read-only; their existing SHA-256 pins passed. They were not copied into Git.
- Gradle reused the local compilation cache (`compileJava FROM-CACHE`); packaging
  and release verification ran. This is not an independent empty-cache rebuild.
- Final JAR matched the previous port.12 artifact byte-for-byte by SHA-256:
  `e50a529d907bbfe3560004a6aab7e671adb82b5b8d432a9efd3d2b7d8664660a`.
- Size: 343502 bytes; previous static inspection counted 171 Java-52 classes.
- Build outputs and caches are excluded from source control. The only intended
  tracked JAR is `gradle/wrapper/gradle-wrapper.jar`.
- Targeted text checks found no original personal username/instance name, known
  local-network address pattern or common secret-token/private-key marker in the
  selected public sources/docs. This is a limited check, not a comprehensive
  security audit or guarantee that arbitrary future commits are safe to publish.
- Original MIT notices were retained; no source history, private docs, saves,
  modpack binaries or unrelated loader modules were copied.

## Earlier port.12 evidence (not rerun here)

Before extraction, 45 focused JUnit tests across nine classes passed:
DirectoryReturnTest, DirectoryItemSearchTest, DirectoryClientStateTest,
DirectoryPreviewLayoutTest, RemoteOpenTrackerTest, DirectoryPagerTest,
SharedEconomyTabTest, DirectoryNetworkTest and RemoteSettingsConfigTest.
Final GUI adjustments subsequently passed compilation and artifact checks.
The full test sources are included; private raw XML/HTML/log archives are not.

These focused tests cover state/search/correlation, bounds, saved membership and
the real Forge config-sync regression. They do not drive the actual rendered GUI
or prove every third-party mod interaction.

## Human and pending acceptance

On 2026-10-04 the maintainer reported 5–6 hours of active multiplayer use with
friends and that most intended checks had been performed. This describes the
pre-port.13 build; its exact installed artifact was not independently verified.
It is user-reported gameplay feedback, not a fresh port.13 runtime test or recorded
evidence that every formal checklist case passed.

No game/server was launched and no live instance was edited during extraction.
Linux/macOS builds, empty-cache CI, general modpack compatibility, dedicated-server
and two-client matrix testing, visual GUI-scale checks, and full-world
migration/rollback drills have not been established by this preparation.

For a public beta, invite reproducible bug reports and describe these limits rather
than claiming universal production readiness. Hosting-platform approval and first
binary publication are separate steps. Source code is on GitHub; preparing a JAR
does not itself publish a GitHub Release or establish hosting-platform approval.
