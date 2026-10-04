package com.tom.trading.client;

import com.tom.trading.directory.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DirectoryReturnTest {
    private static final long NOW = 10_000_000_000L, STEP = 1_000_000_000L;
    private final UUID world = UUID.randomUUID(), target = UUID.randomUUID();
    private DirectoryPage response(DirectoryQuery q, UUID world, long revision, int total, boolean replace) {
        List<DirectoryPage.Row> rows = new ArrayList<>();
        int start = q.page * 50;
        for (int i = start; i < Math.min(total, start + 50); i++) {
            UUID id = i == 55 && !replace ? target : new UUID(0, i + (replace ? 1000 : 0));
            rows.add(new DirectoryPage.Row(new MachineDirectoryEntry(new MachineAddress(0, i, 64, 0), id, null,
                    "Owner", "Shop", MachineDirectoryEntry.Evidence.OBSERVED), DirectoryPage.State.RECORDED));
        }
        return new DirectoryPage(q, DirectoryPage.Result.OK, world, revision, total, false, rows);
    }
    private DirectoryClientState.Bookmark bookmark() {
        DirectoryClientState state = new DirectoryClientState(0, NOW);
        state.setTab(DirectoryTab.ECONOMY, NOW); state.setSearch("Coin", NOW);
        DirectoryQuery first = state.poll(NOW + STEP); state.accept(response(first, world, 1, 120, false), NOW + STEP);
        state.turnPage(1, NOW + STEP);
        DirectoryQuery second = state.poll(NOW + 2 * STEP); state.accept(response(second, world, 1, 120, false), NOW + 2 * STEP);
        assertTrue(state.selectIndex(5)); DirectoryClientState.Bookmark bookmark = state.bookmark(245); state.close(); return bookmark;
    }
    @Test public void returnRefreshesQuoteThenRestoresSearchTabPageScrollAndExactIdentity() {
        DirectoryClientState.Bookmark saved = bookmark(); DirectoryClientState state = new DirectoryClientState(0, NOW);
        state.restore(saved, NOW); DirectoryQuery first = state.poll(NOW);
        assertEquals(0, first.page); assertNull(first.expectedWorld); assertEquals("Coin", first.search); assertEquals(DirectoryTab.ECONOMY, first.tab);
        assertTrue(state.accept(response(first, world, 9, 120, false), NOW)); assertTrue(state.waiting()); assertNull(state.page());
        DirectoryQuery second = state.poll(NOW + STEP); assertEquals(1, second.page); assertEquals(9, second.expectedRevision);
        assertTrue(state.accept(response(second, world, 9, 120, false), NOW + STEP));
        assertEquals(target, state.selected().entry.machineId); assertEquals(5, state.selectedIndex());
        assertEquals(245, state.consumeRestoredScroll()); assertEquals(0, state.consumeRestoredScroll());
        assertFalse(state.waiting());
    }
    @Test public void replacementAtSameAddressIsNotSelectedAndShrunkenPagesAreClamped() {
        DirectoryClientState state = new DirectoryClientState(0, NOW); state.restore(bookmark(), NOW);
        DirectoryQuery first = state.poll(NOW); state.accept(response(first, world, 2, 70, true), NOW);
        DirectoryQuery second = state.poll(NOW + STEP); state.accept(response(second, world, 2, 70, true), NOW + STEP);
        assertNull(state.selected()); assertEquals(1, state.page().query.page);
        state = new DirectoryClientState(0, NOW); state.restore(bookmark(), NOW);
        first = state.poll(NOW); state.accept(response(first, world, 2, 3, false), NOW);
        assertEquals(0, state.page().query.page); assertNull(state.selected()); assertFalse(state.waiting());
    }
    @Test public void differentSaveOrDimensionDiscardsReturnContext() {
        DirectoryClientState state = new DirectoryClientState(0, NOW); state.restore(bookmark(), NOW);
        DirectoryQuery first = state.poll(NOW); state.accept(response(first, UUID.randomUUID(), 1, 120, false), NOW);
        assertNull(state.page()); assertEquals("", state.search()); assertEquals(DirectoryTab.ALL, state.tab());
        DirectoryQuery clean = state.poll(NOW + STEP); assertEquals(0, clean.page); assertNull(clean.expectedWorld);
        state = new DirectoryClientState(7, NOW); state.restore(bookmark(), NOW);
        assertEquals("", state.search()); assertEquals(DirectoryTab.ALL, state.poll(NOW).tab);
    }
    @Test public void changedSearchCancelsRestorationAndLateReplies() {
        DirectoryClientState state = new DirectoryClientState(0, NOW); state.restore(bookmark(), NOW);
        DirectoryQuery old = state.poll(NOW); state.setSearch("Iron", NOW);
        assertFalse(state.accept(response(old, world, 2, 120, false), NOW));
        DirectoryQuery next = state.poll(NOW + STEP); state.accept(response(next, world, 2, 120, false), NOW + STEP);
        assertFalse(state.waiting()); assertEquals(0, state.page().query.page); assertEquals(0, state.consumeRestoredScroll());
    }
    @Test public void keyboardSelectionIsBoundedAndNeverSelectsWhileWaiting() {
        DirectoryClientState state = new DirectoryClientState(0, NOW);
        assertFalse(state.moveSelection(1)); DirectoryQuery query = state.poll(NOW);
        assertFalse(state.selectIndex(0)); state.accept(response(query, world, 1, 3, false), NOW);
        assertTrue(state.moveSelection(1)); assertEquals(0, state.selectedIndex());
        assertTrue(state.moveSelection(-1)); assertEquals(0, state.selectedIndex());
        assertTrue(state.selectIndex(2)); assertTrue(state.moveSelection(1)); assertEquals(2, state.selectedIndex());
        assertFalse(state.selectIndex(3)); state.refresh(NOW); assertFalse(state.selectIndex(0)); assertNull(state.bookmark(0));
    }
    @Test public void restorationStaleRetriesCannotLoopForever() {
        DirectoryClientState state = new DirectoryClientState(0, NOW); state.restore(bookmark(), NOW);
        for (int i = 0; i < 3; i++) {
            long time = NOW + i * 2 * STEP;
            DirectoryQuery first = state.poll(time); state.accept(response(first, world, i + 1, 120, false), time);
            DirectoryQuery second = state.poll(time + STEP);
            state.accept(new DirectoryPage(second, DirectoryPage.Result.STALE, world, i + 2, 0, false, Collections.emptyList()), time + STEP);
        }
        assertFalse(state.waiting()); assertEquals("stale", state.status()); assertNull(state.poll(NOW + 10 * STEP));
    }
}
