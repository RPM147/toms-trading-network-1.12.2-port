# 0.3.4-port.16 — Transaction logs, purchase totals and favorites

Tag: `v0.3.4-port.16`. Regular release, **not a pre-release**.

## Changes

- **Server transaction log:** successful local and remote player purchases are
  written to `<world>/toms_trading_network/transactions.jsonl`, including UTC time,
  player/machine/owner IDs and actual paid/delivered items. Keeps a 10 MiB active
  file plus five rotated archives. Enabled by default; the server's
  `general.transactionLog` config setting disables it independently of chat.
- **Purchase totals:** local and remote screens show total item counts for the
  selected number of trades. Hover the totals row or press **F1** for each item's
  payment/output total. These are requested quantities, not a stock guarantee or
  currency conversion. Invalid/empty counts disable the purchase button.
- **Personal favorites:** use **[+]** beside a directory row to favorite it and
  **[*]** to remove it, or press **F** on a selected row with the list focused.
  Browse your selection in **Favorites**. Up to 256 machine UUIDs are saved in
  your world-specific player data, surviving normal saves/restarts and respawns.
- Favorites do not affect the server's shared category, grant access or expose
  private inventories. Filtering/search/paging stay server-side and chunk-free.
- Added English, Turkish and Mexican Spanish labels/help for these features.

## Installation and limitations

- Minecraft **1.12.2**, Forge target **14.23.5.2859**, Java **8**.
- **Update the server/host and all clients to port.16.** Directory packets changed;
  older versions cannot connect. Back up the stopped world and replace the old TTN
  JAR; never keep two versions installed. Use the normal JAR, not the source ZIP.
- No manual config changes or existing machine/directory migration are required
  from port.15. The new transaction log setting defaults to enabled.
- Logs include player names/UUIDs; keep them private. Logging flushes each record,
  but is not a crash-atomic inventory ledger. Disk errors disable logging for that
  server session without disabling trades; inspect the regular server log and
  fix the problem before restarting. Oldest archives are automatically replaced.
- [Usage guide](https://github.com/RPM147/toms-trading-network-1.12.2-port/blob/v0.3.4-port.16/docs/USAGE.md)
- [Report bugs](https://github.com/RPM147/toms-trading-network-1.12.2-port/issues)

## Verification

Java 8 compilation, packaging, artifact verification and **122 focused automated
tests passed** (20 classes, no failures/errors/skips).
In-game visual, restart/respawn and multiplayer acceptance remain manual checks;
automated tests are not a claim of live-modpack compatibility.

Artifact: `toms_trading_network-1.12.2-0.3.4-port.16.jar` (**364510 bytes**).

```text
5f9491ed3219964694f7449baf18a3a148412e39afae2de5ebc6a6a90754c3e2  toms_trading_network-1.12.2-0.3.4-port.16.jar
```

See the [validation scope](https://github.com/RPM147/toms-trading-network-1.12.2-port/blob/v0.3.4-port.16/docs/VALIDATION.md).

Original project by **tom5454**, unofficial backport maintained by **RPM147**.
MIT license and upstream attribution are preserved. Port development includes
substantial generative-AI assistance under human direction and gameplay testing.
