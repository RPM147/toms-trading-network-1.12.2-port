# 0.3.4-port.15 — Cleaner vending machine directory

Tag: `v0.3.4-port.15`. Regular release, **not a pre-release**.

## Changes

- Simplified the vending machine directory opened with **G**: the left side of
  each row now shows only the **machine name and owner**.
- Removed the duplicated Receive/Pay text and Recorded/access-status line from
  rows in both **All machines** and the configurable shared tab.
- Kept the right-hand payment/output icons, quantities and trade arrow unchanged.
- Full offer/status information remains available in hover details and **F1**.
  Actual access failures are still shown when opening a trade; no permission or
  server-side validation checks were removed.
- Item-name search, selection, return navigation and the configurable tab name
  are unchanged. Row height is retained for narrow-width multi-item previews.

## Installation

- Minecraft **1.12.2**, Forge target **14.23.5.2859**, Java **8**.
- **Update the server/host and all clients to port.15.** Although this is a UI
  change with no new network/data format, exact-version matching is still enforced.
- Back up the stopped world, then replace the old TTN JAR. Do not keep two
  versions installed or use GitHub's source ZIP as the mod.
- No config edits or data migration are required from port.14.
- [Usage guide](https://github.com/RPM147/toms-trading-network-1.12.2-port/blob/v0.3.4-port.15/docs/USAGE.md)
- [Report bugs](https://github.com/RPM147/toms-trading-network-1.12.2-port/issues)

## Verification

Compilation, artifact verification and **40 focused automated tests passed**.
No in-game visual or multiplayer test was run for this version. See the
[validation scope](https://github.com/RPM147/toms-trading-network-1.12.2-port/blob/v0.3.4-port.15/docs/VALIDATION.md).

Artifact: `toms_trading_network-1.12.2-0.3.4-port.15.jar` (**344317 bytes**).

```text
9e573b943f573621321a84e77c1f1b1272d38cb11a7187cd3af8062b9241d64c  toms_trading_network-1.12.2-0.3.4-port.15.jar
```

Original project by **tom5454**, unofficial backport maintained by **RPM147**.
MIT license and upstream attribution are preserved. Port development includes
substantial generative-AI assistance under human direction and gameplay testing.
