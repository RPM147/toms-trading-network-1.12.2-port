# Changelog

## Unreleased — publication preparation

- Created a port-only source repository based on `0.3.4-port.12`.
- Replaced internal project notes with public usage, build, architecture and publishing guides.
- Removed the personal instance-directory default from optional API resolution;
  use `deps/remote-api` or `-PremoteApiModsDir` instead. Hash pins remain unchanged.
- Made the Java compiler path check select `javac.exe` on Windows and `javac`
  elsewhere. Non-Windows execution is not yet verified.
- No gameplay code, mod identity, resource metadata or network/data format change.

## 0.3.4-port.12

- Product/payment-first directory rows and search across recorded offer item labels/IDs.
- Restore directory search, tab, page, scroll and exact machine selection after a trade,
  using a fresh server snapshot rather than stale offers or permissions.
- Keyboard focus/navigation and wrapped errors with paginated F1 details.

## 0.3.4-port.11

- Shared Currency & Tax tab, managed through level-2 administrator commands.
- World-owned MachineUUID membership and directory format 2 with format-1 migration.

## 0.3.4-port.10

- Fixed Forge config initialization attempting to modify a static final chunk-limit
  constant. The hard limit remains immutable and excluded from config syncing.

## Earlier port development

- Native Forge 1.12.2 machines, ownership/inventory authorization, transactional
  trading, integer quantities, sided automation and optional integrations.
- Save-bound directory, temporary remote sessions, bounded chunk preparation,
  public successful-trade receipts and cached public offer previews.

Historical port.9 has a known startup crash and is not a recommended release.
