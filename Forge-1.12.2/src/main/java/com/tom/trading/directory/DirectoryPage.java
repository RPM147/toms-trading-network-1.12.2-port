package com.tom.trading.directory;

import java.util.*;

/** Immutable public display DTO. No item NBT, live inventory, paths or permissions. */
public final class DirectoryPage {
    public enum Result { OK, STALE, UNAVAILABLE, BUSY }
    public enum State { RECORDED, UNVERIFIED, LEGACY, IDENTITY_CONFLICT, UNSUPPORTED, REMOVED_DIMENSION }
    public final DirectoryQuery query;
    public final Result result;
    public final UUID worldId;
    public final long revision;
    public final int total;
    public final boolean backfillComplete;
    public final List<Row> rows;

    public DirectoryPage(DirectoryQuery query, Result result, UUID worldId, long revision,
                         int total, boolean backfillComplete, List<Row> rows) {
        Objects.requireNonNull(query); Objects.requireNonNull(result);
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
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
    }
    public int pageCount() { return Math.max(1, (total + DirectoryQuery.PAGE_SIZE - 1) / DirectoryQuery.PAGE_SIZE); }

    public static final class Row {
        public final MachineDirectoryEntry entry;
        public final State state;
        public Row(MachineDirectoryEntry entry, State state) {
            this.entry = Objects.requireNonNull(entry); this.state = Objects.requireNonNull(state);
            if ((state == State.LEGACY && entry.machineId != null)
                    || ((state == State.RECORDED || state == State.IDENTITY_CONFLICT) && entry.machineId == null))
                throw new IllegalArgumentException("Invalid row identity");
        }
        public boolean sameTarget(Row other) {
            return other != null && entry.address.equals(other.entry.address) && Objects.equals(entry.machineId, other.entry.machineId);
        }
    }
}
