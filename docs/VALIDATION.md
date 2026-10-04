# Validation scope and source preparation

## Source snapshot — 2026-10-04

The clean repository was prepared from the working `0.3.4-port.12` source.
All **145 files under src/** (production, resources and tests) were compared by
SHA-256 with the working copy and matched. The copied scanner/test scripts and
build wrapper are retained; private instance files and historical evidence archives
are deliberately not published here.

Build-only changes are the generic ignored `deps/remote-api` default and selection
of the platform-appropriate Java compiler executable. No gameplay source, resources,
network format or persistent schema changed. Metadata still points to the original
upstream project. In the next version, replace that metadata URL with
https://github.com/RPM147/toms-trading-network-1.12.2-port, preserve upstream
credit, and use a new version for changed JAR content.

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

The maintainer reports that the current mod appears to work in their play sessions
and plans to address later bugs as they arise. This is user-reported gameplay
feedback, not recorded evidence that every formal checklist case passed.

No game/server was launched and no live instance was edited during extraction.
Linux/macOS builds, empty-cache CI, general modpack compatibility, dedicated-server
and two-client matrix testing, visual GUI-scale checks, and full-world
migration/rollback drills have not been established by this preparation.

For a public beta, invite reproducible bug reports and describe these limits rather
than claiming universal production readiness. Hosting-platform approval and first
publication are separate steps; neither has happened in this local preparation.
