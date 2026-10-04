# Validation scope and source preparation

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
