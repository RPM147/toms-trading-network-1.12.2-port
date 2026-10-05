# Player and administrator guide

## Machines and ownership

Place a Vending Machine and configure the desired payment/output in its owner
interface. Offer templates are definitions, not the real stock or earnings.
Other players get the public trade interface, not the owner's inventories.
An existing owner UUID stays authoritative even when the owner is offline;
operator/creative status does not grant access to that owner's private slots.

Back up the complete stopped world before upgrading. Install the exact same
TTN port version on the host/server and clients, with no duplicate TTN JAR.

## Directory and remote trading

Press **G**, or rebind the directory action in Controls. The list contains known
machine metadata; it is not guaranteed to include every historical unloaded machine.
Visit an older machine to let it register, or use the [offline backfill](BACKFILL.md).

Rows show only the machine name and owner on the left, with payment/output icons
and quantities on the right. Receive/Pay text and routine status lines are not
repeated in the row. Hover details and F1 retain full offer/status information;
actual access failures still appear when opening a trade.
Hover over icons for quantities and matching constraints. Some custom-NBT items
use generic icons. Cached offers and stock can differ from the live target.
Click a row, or select it with the keyboard and press Enter, to open the current
remote trade. **Opening is not purchasing.** Confirm the actual offer before buying.

Remote opening checks live machine identity and protection. Bounded temporary
loading can take several seconds or fail because of unavailable regions, capacity,
unsupported protection versions or a timeout. Browsing itself does not load chunks.
Remote sessions can close after inactivity or their absolute lifetime; private
stock/earnings/configuration are never exposed as remote inventory slots.

Search matches recorded sale/payment names, registry IDs, spaced registry paths
and Ore groups, as well as machine/owner labels. Recorded names use the server's
language, not every client-language translation. Machines without previews cannot
match item names. Clear the search to find them by machine/owner instead.

Returning with the Back button or Escape restores tab, search, page, scroll and
the same UUID/address selection after fresh server validation. Replacements are
not silently selected. The history is only for this return path, not persisted
across game restarts or shared across servers.

## Keyboard and errors

| Key | Directory behavior |
|---|---|
| Ctrl+F | Focus search |
| Tab / Shift+Tab | Cycle focus through search, list and enabled buttons |
| Up / Down | Select a row when list is focused |
| Home / End | First / last row on the current page |
| PageUp / PageDown | Previous / next page when list is focused |
| F | Add/remove the selected machine from personal favorites, with the list focused |
| Enter | Leave search for list; open selected trade; activate focused button |
| Escape | Close directory; return from trade; close details panel |
| F1 | Open/close full error details, selected offer and help |

Long status messages wrap; F1 gives paginated full text. In the trade screen,
Tab toggles focus on the quantity field and Enter in that field purchases. Holding
Enter does not repeatedly issue purchases. Do not infer a failed purchase from a
missing network reply: check inventory/server state rather than blindly retrying.

## Purchase totals

Both physical and remote trade screens show the selected batch's total item counts
under the payment/output icons. The icons themselves keep their per-trade amounts.
Hover the totals row or press **F1** for an item-by-item payment/delivery breakdown;
item tooltips also include their selected-batch total. Ore Dictionary payments show
the accepted group, not a promise of which matching inventory variant will be used.

Totals are a preview for the requested count (1–1024), not a reservation. Available
stock, payment and inventory space can cause fewer complete trades to succeed.
An empty/invalid count or unavailable offer has no valid total. Adding counts of
different items is not currency conversion: use the breakdown for actual costs.

## Personal favorites

Click **[+]** next to a machine to add it, or **[*]** to remove it. The marker changes
only favorites and never purchases. Clicking the rest of the row still opens the
trade. Keyboard users can select a row with arrows and press **F** while the list
is focused. Typing F inside the search field continues to type normally.

The **Favorites** tab contains your own selected machines and supports the same
search/paging as other tabs. Favorites are stored by stable machine UUID in your
server-side player data, scoped to this world, and survive normal saves/restarts
and respawns. Up to 256 favorites are kept per player. They do not affect another
player's list, the shared category, ownership, stock or protection. A replacement
machine with a new UUID does not inherit a favorite. No chunk loading is needed
to change favorites. When adding a favorite, saved IDs absent from the directory
are cleaned up so removed machines do not permanently consume the limit. If a
directory change prevents confirming the result, refresh to check the saved state.

## Server transaction log

Enabled by default since port.16. On the server/hosting instance, the existing
`general` config category accepts `B:transactionLog=true` (or `false` to disable);
restart after changing it. This is independent of `publicTradeAnnouncements`.

Each completed local or remote player trade appends one UTF-8 JSON object to
`<world>/toms_trading_network/transactions.jsonl`. It records UTC time, buyer,
machine and owner identifiers, buyer/owner names, actual completed trade count,
and actual payment/delivery item identifiers, labels and quantities. It does not
record raw item NBT, failed trades, hopper transfers or replayed requests again.

The active file is limited to 10 MiB, with five archives named
`transactions.1.jsonl` through `transactions.5.jsonl` (1 is newest). Oldest archives
are automatically replaced. Copy logs elsewhere before rotation if longer history
is needed. Player names/UUIDs are personal data; do not publish these files unredacted.

Records are flushed after each append, without a per-trade disk fsync or atomic
commit with Minecraft's world save. This is an operational audit, not a crash-proof
financial ledger or automatic rollback facility. Sudden crashes may leave a partial
tail record or world/log disagreement. Disk/serialization failures disable logging
for the current server session and report an error in the regular server log;
trades and chat remain enabled. Fix disk/path problems and restart to resume logging.

## Machine-bound trade books (port.17)

1. Hold one blank **Book and Quill** in either hand.
2. **Shift-right-click a Vending Machine you own.** A message confirms binding.
3. Right-click the bound book in the air to refresh and read its history.

The book uses a read-only view: no editing or signing controls. Normal unbound
books keep their usual behavior. Books containing handwritten text are rejected
without overwriting it; a bound book cannot be linked to a different machine.
The tooltip shows the machine name, owner and original binding coordinates.
Only the exact machine owner can bind a book; operator or creative status does
not grant permission to bind someone else's machine.

Anyone holding a bound book can read that machine's history, including another
player you give it to. This is bearer access, not an owner-only view. Do not give
the book to someone who should not see those trades. Book access does not expose
stock/earnings, grant configuration rights or authorize remote purchases.

### Entry format and recording

```text
Buyer: Furkan15
Seller: Veras
64 Iron Ingot <- 3 Copper Coin
```

Buyer and seller appear on separate labeled lines using the reader's game language
(Turkish: `Alıcı: Furkan15`, `Satıcı: Veras`; Spanish: `Comprador`, `Vendedor`).
Existing compact entries also display these labels without rewriting saved history.
Each successful local or remote player purchase adds one compact record: buyer,
seller, delivered items/counts, then paid items/counts. Multiple items are joined
with ` + `. The amounts reflect the actual completed trade, including a partial
batch, not the requested batch preview. Matching variants with the same item ID,
metadata and display label are grouped. Item labels come from the server and long
labels are shortened. No date, UUID, raw item NBT or other verbose details are
included in these trade lines. Failed trades, hopper transfers and request replays
do not create additional entries.

Recording starts when the first book is bound to that machine. Earlier JSONL
records are not imported. It continues server-side when the book is in a chest,
not carried, or lost. The owner can bind another blank book to the same machine
to recover the retained history; multiple bound books read the same ledger.
Reading refreshes the book without loading the machine's chunk. A replacement
machine at the same coordinates has a different UUID and does not inherit it.

### Retention and saves

The ledger retains **up to 100 recent entries per machine**, newest first when
read. Shared limits are 8192 entries and 2 million text characters across the
world, with up to 4096 registered machines. Shared limits can evict a machine's
older entries earlier. Each read is limited to 24 KiB of text and 50 rendered
pages, so unusually long/multi-item entries can reduce the visible history.
Only this recent history is retained; a new book cannot restore evicted records.

Book bindings and history use `<world>/data/ttn_trade_books.dat`, saved through
Minecraft's normal world saves. Keep the complete world, including player/book
items and this data, in backups. The file contains book access keys and buyer/
seller information; do not publish it. Unsupported or corrupt data fails closed
rather than silently replacing it. Sudden crashes can lose unsaved entries;
the book is not a crash-atomic financial ledger.

The separate JSONL transaction log and public announcements remain independent:
disabling either does not disable bound-book recording. Archive JSONL files
elsewhere before rotation if you need longer history. Install the same port.17
JAR on the host/server and every client; no manual config change is required.

## Shared tab (Currency & Tax by default)

All players see the same administrator-selected category. Members also appear in
All machines. The category neither enables creative mode nor automatically charges
taxes, changes prices, grants ownership, or bypasses protection.

### Rename the shared category

Since port.14 the display name is set by the server/host in
`config/toms_trading_network.cfg`, inside the `general` category:

```text
S:specialTabName=Server Shops
```

Launch the mod once to generate its config if needed, stop the server/host, edit
the existing setting, and start again. For singleplayer or Open to LAN, edit the
hosting instance's config. Connected players receive the server's name; a remote
client's local config cannot replace it. Use the same exact mod version on every peer.

An empty value keeps the translated Currency & Tax default. Custom names are
sent literally (not translated), trimmed, and limited to 64 Unicode characters.
Control, formatting and invalid surrogate characters are removed. Button text
is shortened to fit; hovering or F1 shows the full label. Before the first server
reply, the UI uses its translated default. Names are not cached across connections.

The category is not restricted to currency or tax machines: event vendors,
community shops and resource exchanges can all use the same existing commands.
Renaming requires no re-adding of machines and does not alter saved membership,
ownership, trade rules or protection checks.

### Manage membership

Level-2 administrator commands:

```text
/ttndirectory special add <dimension> <x> <y> <z>
/ttndirectory special list [page]
/ttndirectory special remove <machine-uuid>
```

Dimension `0` is the Overworld. Add requires the real supported target to be
already loaded, with a unique observed machine identity; the command does not
load chunks. Use the machine UUID, not the owner UUID, when removing a member.
List shows ten IDs per page; at most 256 memberships are stored per world.

Renaming preserves membership. A replacement/new UUID does not inherit it; remove
the old UUID and add the new machine explicitly. Removing membership does not
delete the block or its inventories, and works for unloaded/absent machines too.
Refresh or reopen the directory after administrator changes. Membership is saved
with the world, not immediately committed to an external database.

## Announcements and compatibility

Successful trades can announce their actual paid/delivered items to all players.
The server's `publicTradeAnnouncements` setting controls this behavior and requires
restart. Chat receipts and the separate transaction log can be enabled independently.

Use the server-side `RemoteSettings` configuration for remote-trading controls.
Changing settings is an administrator operation; client settings cannot grant
trade authority. Optional integration support is version-specific; consult
the [README](../README.md) rather than assuming every claim mod is supported.
