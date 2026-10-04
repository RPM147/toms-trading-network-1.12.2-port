package com.tom.trading.directory;

import java.util.UUID;

/** Correlation and snapshot cursor only; none of these client fields authorize a trade. */
public final class DirectoryQuery {
    public static final int PAGE_SIZE = 50;
    public static final int MAX_PAGE = (MachineDirectoryData.MAX_ENTRIES - 1) / PAGE_SIZE;
    public final UUID screenId, expectedWorld;
    public final long requestId, expectedRevision;
    public final int playerDimension, page;
    public final String search;
    public final DirectoryTab tab;

    public DirectoryQuery(UUID screenId, long requestId, int playerDimension, int page,
                          UUID expectedWorld, long expectedRevision, String search) {
        this(screenId, requestId, playerDimension, page, expectedWorld, expectedRevision, search, DirectoryTab.ALL);
    }
    public DirectoryQuery(UUID screenId, long requestId, int playerDimension, int page,
                          UUID expectedWorld, long expectedRevision, String search, DirectoryTab tab) {
        if (tab == null || screenId == null || requestId <= 0 || page < 0 || page > MAX_PAGE || !DirectoryText.valid(search)
                || (expectedWorld == null ? expectedRevision != 0 || page != 0 : expectedRevision <= 0))
            throw new IllegalArgumentException("Invalid directory query");
        this.screenId = screenId; this.requestId = requestId; this.playerDimension = playerDimension;
        this.page = page; this.expectedWorld = expectedWorld; this.expectedRevision = expectedRevision; this.search = search;
        this.tab = tab;
    }
}
