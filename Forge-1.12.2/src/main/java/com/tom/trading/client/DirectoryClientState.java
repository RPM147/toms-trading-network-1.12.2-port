package com.tom.trading.client;

import com.tom.trading.directory.DirectoryPage;
import com.tom.trading.directory.DirectoryQuery;
import com.tom.trading.directory.DirectoryText;
import com.tom.trading.directory.DirectoryTab;
import com.tom.trading.directory.MachineAddress;
import java.util.UUID;

/** Pure client presentation state. Correlation is not a server-side authorization boundary. */
public final class DirectoryClientState {
    private static final long SPACING = 550_000_000L, DEBOUNCE = 350_000_000L, TIMEOUT = 15_000_000_000L;
    private final UUID screenId = UUID.randomUUID();
    public final int dimension;
    private UUID worldId;
    private long revision, sequence, due, nextSend, deadline;
    private int pageNumber, staleRetries;
    private boolean dirty = true, closed;
    private String search = "", status = "loading";
    private DirectoryTab tab = DirectoryTab.ALL;
    private DirectoryQuery pending;
    private DirectoryPage page;
    private DirectoryPage.Row selected;
    private Bookmark restoring;
    private boolean restorePageRequested;
    private int restoredScroll;

    /** One return path, not a global cache. No quote, row DTO, world or connection objects. */
    public static final class Bookmark {
        public final int dimension, page, scroll;
        public final UUID worldId, machineId;
        public final MachineAddress address;
        public final DirectoryTab tab;
        public final String search;
        private Bookmark(DirectoryClientState state, int scroll) {
            dimension = state.dimension; worldId = state.worldId; tab = state.tab; search = state.search;
            page = state.page.query.page; this.scroll = Math.max(0, Math.min(100000, scroll));
            machineId = state.selected == null ? null : state.selected.entry.machineId;
            address = state.selected == null ? null : state.selected.entry.address;
        }
    }
    public Bookmark bookmark(int scroll) { return page == null || waiting() || closed ? null : new Bookmark(this, scroll); }
    public void restore(Bookmark bookmark, long now) {
        if (closed || sequence != 0 || bookmark == null || bookmark.dimension != dimension) return;
        restoring = bookmark; tab = bookmark.tab; search = bookmark.search; reset(now);
    }
    public int consumeRestoredScroll() { int value = restoredScroll; restoredScroll = 0; return value; }
    public int selectedIndex() { return page == null ? -1 : page.rows.indexOf(selected); }
    public boolean selectIndex(int index) {
        return page != null && index >= 0 && index < page.rows.size() && select(page, page.rows.get(index));
    }
    public boolean moveSelection(int direction) {
        if (page == null || page.rows.isEmpty() || (direction != -1 && direction != 1)) return false;
        int index = selectedIndex();
        return selectIndex(index < 0 ? (direction > 0 ? 0 : page.rows.size() - 1)
                : Math.max(0, Math.min(page.rows.size() - 1, index + direction)));
    }

    public DirectoryClientState(int dimension, long now) { this.dimension = dimension; due = nextSend = now; }
    public String search() { return search; }
    public DirectoryTab tab() { return tab; }
    public void setTab(DirectoryTab next, long now) {
        if (closed || next == null || next == tab) return;
        restoring = null; tab = next; reset(now); staleRetries = 0;
    }
    public String status() { return status; }
    public DirectoryPage page() { return page; }
    public DirectoryPage.Row selected() { return selected; }
    public boolean waiting() { return dirty || pending != null; }
    public void setSearch(String text, long now) {
        if (closed || !DirectoryText.valid(text) || text.equals(search)) return;
        restoring = null; search = text; reset(now + DEBOUNCE); staleRetries = 0;
    }
    public void refresh(long now) { if (!closed) { restoring = null; reset(now); staleRetries = 0; } }
    private void reset(long when) {
        worldId = null; revision = 0; pageNumber = 0; pending = null; page = null; selected = null;
        dirty = true; due = when; status = "loading";
        restorePageRequested = false; restoredScroll = 0;
    }
    public void turnPage(int direction, long now) {
        if (closed || waiting() || page == null || (direction != -1 && direction != 1)) return;
        int next = page.query.page + direction;
        if (next < 0 || next >= page.pageCount()) return;
        restoring = null; restoredScroll = 0;
        pageNumber = next; page = null; selected = null; due = now; dirty = true; status = "loading"; staleRetries = 0;
    }
    public DirectoryQuery poll(long now) {
        if (closed) return null;
        if (pending != null && now - deadline >= 0) {
            pending = null; status = "timeout"; // No endless polling or auto purchase retries.
        }
        if (!dirty || now - due < 0 || now - nextSend < 0) return null;
        if (sequence == Long.MAX_VALUE) { close(); return null; }
        pending = new DirectoryQuery(screenId, ++sequence, dimension, pageNumber, worldId, revision, search, tab);
        deadline = now + TIMEOUT; nextSend = now + SPACING; dirty = false;
        return pending;
    }
    public boolean accept(DirectoryPage response, long now) {
        if (closed || pending == null || !response.query.screenId.equals(screenId)
                || response.query.requestId != pending.requestId || response.query.playerDimension != dimension
                || response.query.tab != tab || !response.query.search.equals(search) || response.query.page != pageNumber
                || !java.util.Objects.equals(response.query.expectedWorld, pending.expectedWorld)
                || response.query.expectedRevision != pending.expectedRevision) return false;
        pending = null; selected = null;
        if (response.result == DirectoryPage.Result.OK) {
            page = response; worldId = response.worldId; revision = response.revision;
            if (restoring != null) {
                if (!restoring.worldId.equals(worldId)) {
                    restoring = null; tab = DirectoryTab.ALL; search = ""; reset(now + SPACING); return true;
                }
                int target = Math.min(restoring.page, response.pageCount() - 1);
                if (!restorePageRequested && target != response.query.page) {
                    pageNumber = target; page = null; dirty = true; due = now + SPACING; restorePageRequested = true;
                    return true;
                }
                // A deleted/changed row must not turn a reused index into a selected machine.
                for (DirectoryPage.Row row : response.rows)
                    if (row.entry.address.equals(restoring.address) && java.util.Objects.equals(row.entry.machineId, restoring.machineId))
                        selected = row;
                restoredScroll = restoring.scroll; restoring = null;
            }
            status = response.total == 0 ? "empty" : "ready"; staleRetries = 0;
        } else if (response.result == DirectoryPage.Result.STALE && staleRetries++ < 2) {
            reset(now + SPACING); status = "stale_retry";
        } else {
            page = null; status = response.result.name().toLowerCase(java.util.Locale.ROOT);
        }
        return true;
    }
    /** Clicks bind to the page that was actually rendered, not a new row at the same screen index. */
    public boolean select(DirectoryPage rendered, DirectoryPage.Row row) {
        if (closed || waiting() || page == null || page != rendered || !page.rows.contains(row)) return false;
        selected = row; return true;
    }
    public void close() { closed = true; pending = null; page = null; selected = null; restoring = null; dirty = false; }
}
