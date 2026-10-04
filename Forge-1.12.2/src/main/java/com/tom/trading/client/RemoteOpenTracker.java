package com.tom.trading.client;

import com.tom.trading.directory.DirectoryPage;
import com.tom.trading.remote.RemoteOpenRequest;

/** Presentation-only pending selection. Replies cannot reopen a cancelled or replaced screen. */
public final class RemoteOpenTracker {
    private RemoteOpenRequest pending;
    private long sequence, deadline, nextOpen;
    private boolean closed;
    private boolean throttled;
    public boolean waiting() { return pending != null; }
    public RemoteOpenRequest begin(DirectoryPage page, DirectoryPage.Row row, long now) {
        if (closed || pending != null || (throttled && now - nextOpen < 0) || page == null || page.result != DirectoryPage.Result.OK
                || !page.rows.contains(row) || sequence == Long.MAX_VALUE) return null;
        pending = new RemoteOpenRequest(page.query.screenId, ++sequence, page.worldId, page.revision,
                page.query.playerDimension, row.entry.address, row.entry.machineId);
        deadline = now + 15_000_000_000L; nextOpen = now + 550_000_000L; throttled = true;
        return pending;
    }
    public boolean accept(RemoteOpenRequest response) {
        if (closed || pending == null || !pending.matches(response)) return false;
        pending = null; return true;
    }
    public RemoteOpenRequest expire(long now) { return pending != null && now - deadline >= 0 ? cancel() : null; }
    private RemoteOpenRequest cancel() { RemoteOpenRequest old = pending; pending = null; return old; }
    public RemoteOpenRequest close() { closed = true; return cancel(); }
}
