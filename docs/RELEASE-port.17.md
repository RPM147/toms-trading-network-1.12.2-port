# 0.3.4-port.17 — Machine-bound trade books

Tag: `v0.3.4-port.17`. Regular release, **not a pre-release**.
CurseForge upload and installation into a live instance are separate operations.

## Changes

- Hold one blank **Book and Quill** and **Shift-right-click a Vending Machine
  you own** to bind the book. Right-click the bound book in the air to refresh
  and read that machine's recent completed trades through a read-only view.
- Compact lines show buyer, seller, actual delivered items/counts and actual
  paid items/counts. Buyer/seller labels appear on separate lines in the reader's
  game language, including existing records:

  ```text
  Buyer: Furkan15
  Seller: Veras
  64 Iron Ingot <- 3 Copper Coin
  ```

- Anyone holding the book can read its ledger. Only the owner can bind a book;
  handwritten books and existing bindings are not overwritten. The tooltip
  identifies the machine, owner and original binding address.
- Recording starts at the first binding and continues without a held book.
  Another blank book bound by the owner recovers the retained history.
- Server-issued keys, stable world/machine identities, bounded packets and
  correlated replies keep reading scoped to the actual held book. Book access
  does not grant machine management or purchasing rights.
- JSONL logs, chat announcements and bound-book recording remain independent.
  Failed/replayed purchases do not add extra successes.

## Installation and limitations

- Minecraft **1.12.2**, Forge target **14.23.5.2859**, Java **8**.
- Update the host/server **and every client** to port.17, replacing the old TTN
  JAR. Never install two versions together. Back up the stopped world first.
- No manual config change or migration of existing machine/directory data is
  required. Book bindings/history add a separate save-owned format-1 data file.
- Up to 100 recent entries per machine, subject to shared limits of 8192 entries
  and 2 million text characters. Maximum 4096 registered machines. Reading is
  capped at 24 KiB of text and 50 rendered pages, newest entries first.
- No earlier JSONL backfill or recovery of evicted rows. The book is not a
  permanent, crash-atomic financial archive. Keep world backups and archive
  JSONL files separately if longer history is needed. Book keys/history are private.
- [Usage guide](https://github.com/RPM147/toms-trading-network-1.12.2-port/blob/v0.3.4-port.17/docs/USAGE.md)
  / [architecture](https://github.com/RPM147/toms-trading-network-1.12.2-port/blob/v0.3.4-port.17/docs/ARCHITECTURE.md).

## Verification

Java 8 compilation, packaging, artifact verification and **64 focused automated
tests passed** (10 classes; no failures/errors/skips). In-game binding, transfer,
restart and multiplayer acceptance remain manual checks. No live instance was
changed or launched. Publication does not establish in-game acceptance.

Artifact: `toms_trading_network-1.12.2-0.3.4-port.17.jar` (**399435 bytes**).

```text
b738eb676e555443adf9b9fd33e83cd3f5a7dbb4e55a401fa873d6a298686f2b  toms_trading_network-1.12.2-0.3.4-port.17.jar
```

See the [validation scope](https://github.com/RPM147/toms-trading-network-1.12.2-port/blob/v0.3.4-port.17/docs/VALIDATION.md).
Original project by **tom5454**;
unofficial port maintained by **RPM147**. MIT attribution is preserved.
