# 0.3.4-port.14 — Configurable shared tab

Tag: `v0.3.4-port.14`. Regular release, **not a pre-release**.

## What's new

The administrator-selected directory tab is no longer limited to a fixed display
name. Use it for server shops, event vendors, community exchanges, or any other
group of vending machines.

- Set `general.specialTabName` in the server/host's `config/toms_trading_network.cfg`.
- The server sends the configured name to every player; local client config does
  not override it. Empty keeps the translated **Currency & Tax** default.
- Custom names are plain text, trimmed and limited to 64 Unicode characters.
  Control/formatting characters are removed. Long names are shortened on the
  button; hover over it or press F1 to read the full label.
- `/ttndirectory special` command feedback uses the configured name.

## Configuration

If the config does not exist yet, launch the mod once to generate it, then stop
the server/host. Edit the existing `general` category, for example:

```text
S:specialTabName=Server Shops
```

Start the server/host again to apply it. For singleplayer/Open to LAN, change the
hosting instance's config. No client-side config edits are needed.
To restore the translated default, leave the value empty: `S:specialTabName=`.

Existing `/ttndirectory special add`, `list` and `remove` commands are unchanged.
Renaming does not change machine membership, inventories, prices, ownership,
creative mode, or protection checks. Saved-data formats are unchanged.

## Installation and compatibility

- Minecraft **1.12.2**, Forge target **14.23.5.2859**, Java **8**.
- **Update the server/host and every client to port.14.** Directory replies have a
  new field, so exact-version matching rejects mixed port.13/port.14 installations.
- Back up the stopped world before upgrading; replace the old TTN JAR, do not
  keep two versions installed. Use the normal mod JAR, not GitHub's source ZIP.
- [Usage and administrator commands](https://github.com/RPM147/toms-trading-network-1.12.2-port/blob/v0.3.4-port.14/docs/USAGE.md)
- [Report port-specific bugs](https://github.com/RPM147/toms-trading-network-1.12.2-port/issues)

## Verification

Compilation, reobfuscation, artifact verification and **49 focused tests passed**.
No fresh port.14 gameplay session was run; see the
[validation scope](https://github.com/RPM147/toms-trading-network-1.12.2-port/blob/v0.3.4-port.14/docs/VALIDATION.md).
Regular-release status does not guarantee compatibility with every modpack.

Artifact: `toms_trading_network-1.12.2-0.3.4-port.14.jar` (**344824 bytes**).

```text
7013dc01d7537910e4059da47ac1ea00002b1fbe5ad65dd902852162423ae6b2  toms_trading_network-1.12.2-0.3.4-port.14.jar
```

Original project by **tom5454**, unofficial backport maintained by **RPM147**.
MIT license and upstream attribution are preserved. Port development includes
substantial generative-AI assistance under human direction and gameplay testing.
