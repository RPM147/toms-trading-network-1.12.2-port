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
| Enter | Leave search for list; open selected trade; activate focused button |
| Escape | Close directory; return from trade; close details panel |
| F1 | Open/close full error details, selected offer and help |

Long status messages wrap; F1 gives paginated full text. In the trade screen,
Tab toggles focus on the quantity field and Enter in that field purchases. Holding
Enter does not repeatedly issue purchases. Do not infer a failed purchase from a
missing network reply: check inventory/server state rather than blindly retrying.

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
restart. Receipts are chat messages, not a durable transaction audit database.

Use the server-side `RemoteSettings` configuration for remote-trading controls.
Changing settings is an administrator operation; client settings cannot grant
trade authority. Optional integration support is version-specific; consult
the [README](../README.md) rather than assuming every claim mod is supported.
