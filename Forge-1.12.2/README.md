# Forge 1.12.2 module

Native port source and tests. Start with the [project README](../README.md).

Build requirements and exact optional API hashes are in
[BUILDING.md](../docs/BUILDING.md). Use the included wrapper, Java 8 and explicit
local API inputs. Do not put third-party JARs, test worlds or build outputs into Git.

Normal release output: `build/libs/toms_trading_network-1.12.2-<version>.jar`.
`releaseJar` alone is not a test/acceptance gate. Keep the `BuildInfo.VERSION`,
Gradle version and published release tag consistent.
