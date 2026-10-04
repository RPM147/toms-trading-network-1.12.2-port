package com.tom.trading.directory;

import java.util.*;

/** Immutable public display DTO. No item NBT, live inventory, paths or permissions. */
public final class DirectoryPage {
    public enum Result { OK, STALE, UNAVAILABLE, BUSY, FAVORITES_FULL }
    public enum State { RECORDED, UNVERIFIED, LEGACY, IDENTITY_CONFLICT, UNSUPPORTED, REMOVED_DIMENSION }
    public final DirectoryQuery query;
    public final Result result;
    public final UUID worldId;
    public final long revision;
    public final int total;
    public final boolean backfillComplete;
    /** Server-supplied plain text. Empty selects the client's translated default, not its config. */
    public final String specialTabName;
    public final List<Row> rows;

    public DirectoryPage(DirectoryQuery query, Result result, UUID worldId, long revision,
                         int total, boolean backfillComplete, List<Row> rows) {
        this(query, result, worldId, revision, total, backfillComplete, rows, "");
    }
    public DirectoryPage(DirectoryQuery query, Result result, UUID worldId, long revision,
                         int total, boolean backfillComplete, List<Row> rows, String specialTabName) {
        Objects.requireNonNull(query); Objects.requireNonNull(result);
        if (!DirectoryText.valid(specialTabName)) throw new IllegalArgumentException("Invalid shared tab name");
        if (total < 0 || total > MachineDirectoryData.MAX_ENTRIES || rows.size() > DirectoryQuery.PAGE_SIZE
                || revision < 0 || (worldId == null && revision != 0)
                || (result == Result.OK && (worldId == null || revision <= 0
                || (query.expectedWorld != null && (!query.expectedWorld.equals(worldId) || query.expectedRevision != revision))
                || query.page >= Math.max(1, (total + DirectoryQuery.PAGE_SIZE - 1) / DirectoryQuery.PAGE_SIZE)
                || rows.size() != Math.min(DirectoryQuery.PAGE_SIZE, total - query.page * DirectoryQuery.PAGE_SIZE)))
                || (result != Result.OK && (!rows.isEmpty() || total != 0)))
            throw new IllegalArgumentException("Invalid directory page");
        Set<MachineAddress> addresses = new HashSet<>();
        for (Row row : rows) if (!addresses.add(row.entry.address)) throw new IllegalArgumentException("Duplicate page address");
        this.query = query; this.result = result; this.worldId = worldId; this.revision = revision;
        this.total = total; this.backfillComplete = backfillComplete;
        this.specialTabName = specialTabName;
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
    }
    public DirectoryPage withSpecialTabName(String name) {
        return specialTabName.equals(name) ? this
                : new DirectoryPage(query, result, worldId, revision, total, backfillComplete, rows, name);
    }
    public int pageCount() { return Math.max(1, (total + DirectoryQuery.PAGE_SIZE - 1) / DirectoryQuery.PAGE_SIZE); }

    public static final class Row {
        public final MachineDirectoryEntry entry;
        public final State state;
        /** Presentation only: belongs to the authenticated recipient, never a permission. */
        public final boolean favorite;
        public Row(MachineDirectoryEntry entry, State state) {
            this(entry, state, false);
        }
        public Row(MachineDirectoryEntry entry, State state, boolean favorite) {
            this.entry = Objects.requireNonNull(entry); this.state = Objects.requireNonNull(state);
            if ((state == State.LEGACY && entry.machineId != null)
                    || (favorite && entry.machineId == null)
                    || ((state == State.RECORDED || state == State.IDENTITY_CONFLICT) && entry.machineId == null))
                throw new IllegalArgumentException("Invalid row identity");
            this.favorite = favorite;
        }
        public boolean sameTarget(Row other) {
            return other != null && entry.address.equals(other.entry.address) && Objects.equals(entry.machineId, other.entry.machineId);
        }
    }
}
