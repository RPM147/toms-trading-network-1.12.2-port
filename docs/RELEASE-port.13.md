# 0.3.4-port.13 — Beta

Tag: `v0.3.4-port.13`. Release channel: **Beta / pre-release**.

Unofficial native port of [Tom's Trading Network by tom5454](https://github.com/tom5454/Toms-Trading-Network),
maintained by RPM147. MIT license; original attribution is retained.
Port development includes substantial generative-AI assistance under human direction.

## Changes

- Updated the mod's displayed name to identify the unofficial 1.12.2 port.
- Linked mod metadata to the port repository and updated maintenance credits.
- No gameplay, inventory, permissions, network schema or saved-data schema changes
  relative to port.12. A new version identifies the changed package.

## Installation

- Requires Minecraft **1.12.2**, target Forge **14.23.5.2859**, and Java **8**.
- Back up the entire stopped world before upgrading. Try a copy first.
- Replace the previous TTN JAR; do not keep both versions in `mods`.
- Install **port.13 on the server/host and every client**. The exact-version
  handshake rejects mixed port.12/port.13 installations.
- Download `toms_trading_network-1.12.2-0.3.4-port.13.jar`, not GitHub's source ZIP.
- Read the [usage guide](https://github.com/RPM147/toms-trading-network-1.12.2-port/blob/v0.3.4-port.13/docs/USAGE.md)
  and [validation scope](https://github.com/RPM147/toms-trading-network-1.12.2-port/blob/v0.3.4-port.13/docs/VALIDATION.md).

## Verification and limits

Compilation, reobfuscation, artifact audit and 3 focused identity/version tests passed.
The maintainer reports 5–6 hours of multiplayer use with friends of the earlier
build; this is not a new port.13 runtime test or a universal compatibility claim.
Report reproducible port bugs in this repository's Issues, not upstream.

Artifact size: **343624 bytes**.

```text
579094a15fba44567133ee87ae490bbbcad2e2811214359929ddebcd0b23452b  toms_trading_network-1.12.2-0.3.4-port.13.jar
```

Release assets: the normal mod JAR and `release-artifact.sha256`.
GitHub's automatically generated source archives are for developers, not installation.
