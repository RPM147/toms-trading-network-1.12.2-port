# Building from source

Run commands from `Forge-1.12.2/`. This repository does not contain a JDK,
Minecraft runtime, optional-mod API JARs or generated build caches.

## Pinned toolchain

| Component | Version |
|---|---|
| Java bytecode / build JDK | Java 8 / major 52 |
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2859 |
| ForgeGradle | 4.0.19 |
| Gradle wrapper | 6.8.2 |
| Mappings | stable 39-1.12 |
| JUnit | 4.13.2 |

The wrapper distribution has a pinned SHA-256 in `gradle-wrapper.properties`.
Use the included wrapper, not a global Gradle installation or a newer JDK.

## Required build inputs for optional integrations

The following APIs are **compile-only** but required to compile this source.
Obtain them legally from their authors' distribution pages; do not rehost or
commit them. Put them in `Forge-1.12.2/deps/remote-api/` (ignored by Git), or pass
`-PremoteApiModsDir=<absolute-folder>` to each build invocation.

| Filename | SHA-256 |
|---|---|
| `minecolonies-1.12.2-0.11.841-BETA-universal.jar` | `bd90043864e31edb6963da62ba46f7aa175c35565a6400599f379594c4a472d0` |
| `ElectroblobsWizardry-4.3.19.jar` | `8a7f94dbccac622febff5111f3b3c2ba439e1d2b2c743f2a5c31fdfcd92d241c` |

Official project pages: [MineColonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies)
and [Electroblob's Wizardry](https://www.curseforge.com/minecraft/mc-mods/electroblobs-wizardry).
Use the exact historical files, not the latest releases. MineColonies reports
`1.12.2-0.11.841-ALPHA` internally despite the BETA filename. Do not bypass the hash
check by renaming a different binary or changing the pin without reviewing its API.

JEI `4.16.5.1030:api` and The One Probe file `2667280` / `1.4.28` resolve through
the repositories in `build.gradle`. These APIs are not bundled either.
Players do not need optional API JARs solely because developers need them to compile.

## Windows PowerShell

Install a Java 8 **JDK**, then set the path for the current shell only:

```powershell
$env:TTN_JAVA8_HOME = 'C:\path\to\java8-jdk'
.\gradlew-jdk8.bat compileJava
.\gradlew-jdk8.bat test
.\gradlew-jdk8.bat build verifyReleaseArtifact
```

When API JARs are elsewhere, add the option, for example:

```powershell
.\gradlew-jdk8.bat '-PremoteApiModsDir=C:\path\to\api-jars' compileJava
```

`gradlew-jdk8.bat` changes JAVA_HOME/PATH only for its own process. Its fallback
JDK location is a convenience, not a downloaded/bundled JDK; setting
`TTN_JAVA8_HOME` explicitly is recommended.

## Other operating systems (not yet verified)

Set `JAVA_HOME` to a Java 8 JDK and add its `bin` to PATH. The build's compiler
check now recognizes `bin/javac` outside Windows. A verified Linux/macOS release
or CI result is not claimed. The `.bat` helper is Windows-only; use:

```sh
./gradlew prepareLegacyForgeRuntime
./gradlew build verifyReleaseArtifact
```

Supply the same optional API inputs. Keep the executable bit on `gradlew`.

## Legacy runtime detail

ForgeGradle's generated mapped runtime must be the corrected `-recomp.jar`.
The Windows helper prepares this before test/check/build/run tasks. On other
systems run `prepareLegacyForgeRuntime` first. Do not remove the Side enum or
test-classpath guards to silence historical `BUKKIT` artifact errors.

The first build can download toolchain dependencies and take considerably longer
than a warm-cache build. Network access to the declared Maven repositories is required.

## Artifacts and tests

- Release JAR: `build/libs/toms_trading_network-1.12.2-<version>.jar`.
- Checksum: `build/reports/verification/release-artifact.sha256`.
- Test report: `build/reports/tests/test/index.html`.
- Do not distribute the intermediate JAR under `build/intermediates/`.
- `verifyReleaseArtifact` checks version agreement, Java-52 bytecode, required
  resources and absence of bundled dependency classes. It is not gameplay testing.
- `releaseJar` alone does not run the complete tests.
- Before `clean build`, preserve any needed existing artifacts; clean removes build output.

Offline scanner tests (Python 3.9+, standard library only):

```sh
python -m unittest scripts/test_machine_directory_scan.py
```

Development `runClient` / `runServer` tasks exist, but require explicit operator
intent. Never aim them at a live world or use them as an unattended publication step.
