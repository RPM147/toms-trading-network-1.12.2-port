package com.tom.trading.client;

import com.tom.trading.directory.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DirectoryClientStateTest {
    @Test public void serverTabNameSurvivesTabSwitchButNotOldRepliesOrNewScreens() {
        DirectoryClientState state = new DirectoryClientState(0, NOW);
        assertEquals("", state.specialTabName());
        DirectoryPage first = response(state.poll(NOW)).withSpecialTabName("Server Shops");
        assertTrue(state.accept(first, NOW)); assertEquals("Server Shops", state.specialTabName());
        state.setTab(DirectoryTab.ECONOMY, NOW);
        assertEquals("Server Shops", state.specialTabName());
        DirectoryQuery pending = state.poll(NOW + 600_000_000L);
        assertFalse(state.accept(first.withSpecialTabName("Stale server name"), NOW));
        assertEquals("Server Shops", state.specialTabName());
        assertTrue(state.accept(response(pending).withSpecialTabName("Event Shops"), NOW));
        assertEquals("Event Shops", state.specialTabName());
        DirectoryClientState other = new DirectoryClientState(0, NOW);
        assertEquals("", other.specialTabName());
        state.close(); assertEquals("", state.specialTabName());
        assertFalse(state.accept(first, NOW)); assertEquals("", state.specialTabName());
    }

    @Test public void errorRepliesCarryTheLabelAndBlankRestoresTheTranslatedDefault() {
        DirectoryClientState state = new DirectoryClientState(0, NOW);
        DirectoryQuery first = state.poll(NOW);
        assertTrue(state.accept(new DirectoryPage(first, DirectoryPage.Result.UNAVAILABLE, null, 0, 0, false,
                Collections.emptyList(), "Community Shops"), NOW));
        assertEquals("Community Shops", state.specialTabName());
        state.refresh(NOW);
        assertTrue(state.accept(response(state.poll(NOW + 600_000_000L)), NOW));
        assertEquals("", state.specialTabName());
    }

    @Test public void clientConfigDoesNotOverrideTheServerDisplayName() {
        String original = com.tom.trading.remote.RemoteSettings.specialTabName;
        try {
            com.tom.trading.remote.RemoteSettings.specialTabName = "Client Override";
            DirectoryClientState state = new DirectoryClientState(0, NOW);
            assertEquals("", state.specialTabName());
            assertTrue(state.accept(response(state.poll(NOW)).withSpecialTabName("Server Shops"), NOW));
            assertEquals("Server Shops", state.specialTabName());
            state.refresh(NOW);
            assertTrue(state.accept(response(state.poll(NOW + 600_000_000L)), NOW));
            assertEquals("", state.specialTabName());
        } finally { com.tom.trading.remote.RemoteSettings.specialTabName = original; }
    }

    @Test public void switchingTabsRejectsOldAndWrongTabResponsesAndClearsSelection() {
        DirectoryClientState state = new DirectoryClientState(0, NOW);
        DirectoryQuery original = state.poll(NOW); DirectoryPage shown = response(original);
        assertTrue(state.accept(shown, NOW)); assertTrue(state.select(shown, shown.rows.get(0)));
        state.setTab(DirectoryTab.ECONOMY, NOW);
        assertNull(state.selected()); assertNull(state.page()); assertFalse(state.accept(shown, NOW));
        DirectoryQuery current = state.poll(NOW + 600_000_000L);
        assertEquals(DirectoryTab.ECONOMY, current.tab); assertEquals(0, current.page); assertNull(current.expectedWorld);
        DirectoryQuery forged = new DirectoryQuery(current.screenId, current.requestId, current.playerDimension,
                current.page, current.expectedWorld, current.expectedRevision, current.search, DirectoryTab.ALL);
        assertFalse(state.accept(response(forged), NOW)); assertTrue(state.accept(response(current), NOW));
        state.setSearch("Tax", NOW); assertEquals(DirectoryTab.ECONOMY, state.tab());
        state.close(); state.setTab(DirectoryTab.ALL, NOW); assertEquals(DirectoryTab.ECONOMY, state.tab());
    }
    private static final long NOW = 10_000_000_000L;
    private DirectoryPage response(DirectoryQuery query) {
        MachineDirectoryEntry entry = new MachineDirectoryEntry(new MachineAddress(0, 1, 64, 1), UUID.randomUUID(),
                UUID.randomUUID(), "Same name", "Shop", MachineDirectoryEntry.Evidence.OBSERVED);
        return new DirectoryPage(query, DirectoryPage.Result.OK, UUID.randomUUID(), 1, 1, false,
                Collections.singletonList(new DirectoryPage.Row(entry, DirectoryPage.State.RECORDED)));
    }
    @Test public void editingDebouncesCoalescesAndRejectsLateSearchResults() {
        DirectoryClientState state = new DirectoryClientState(0, NOW);
        DirectoryQuery first = state.poll(NOW); assertNotNull(first);
        state.setSearch("iron", NOW + 100_000_000L); state.setSearch("iron shop", NOW + 200_000_000L);
        assertFalse(state.accept(response(first), NOW + 300_000_000L));
        assertNull(state.poll(NOW + 500_000_000L));
        DirectoryQuery latest = state.poll(NOW + 600_000_000L);
        assertEquals("iron shop", latest.search); assertTrue(latest.requestId > first.requestId);
        assertTrue(state.accept(response(latest), NOW + 700_000_000L));
        assertFalse(state.waiting());
    }
    @Test public void closedOrDifferentScreenCannotBeReopenedByAReply() {
        DirectoryClientState first = new DirectoryClientState(0, NOW), second = new DirectoryClientState(0, NOW);
        DirectoryPage reply = response(first.poll(NOW)); second.poll(NOW);
        assertFalse(second.accept(reply, NOW)); first.close(); assertFalse(first.accept(reply, NOW));
        assertNull(first.page()); assertNull(first.poll(NOW + 20_000_000_000L));
    }
    @Test public void selectionIsBoundToTheRenderedSnapshotNotRowNumberOrOwnerName() {
        DirectoryClientState state = new DirectoryClientState(0, NOW);
        DirectoryPage original = response(state.poll(NOW)); assertTrue(state.accept(original, NOW));
        assertTrue(state.select(original, original.rows.get(0))); assertNotNull(state.selected());
        state.refresh(NOW); assertNull(state.selected());
        DirectoryPage replacement = response(state.poll(NOW + 600_000_000L)); state.accept(replacement, NOW + 600_000_000L);
        assertFalse(state.select(original, original.rows.get(0)));
        assertFalse(original.rows.get(0).sameTarget(replacement.rows.get(0)));
        assertTrue(state.select(replacement, replacement.rows.get(0)));
    }
    @Test public void timeoutsStopPollingAndStaleResponsesHaveBoundedAutomaticRetries() {
        DirectoryClientState state = new DirectoryClientState(0, NOW); state.poll(NOW);
        assertNull(state.poll(NOW + 16_000_000_000L)); assertEquals("timeout", state.status()); assertFalse(state.waiting());
        for (int i = 0; i < 3; i++) {
            if (i == 0) state.refresh(NOW + 20_000_000_000L);
            long time = NOW + 20_000_000_000L + i * 1_000_000_000L;
            DirectoryQuery query = state.poll(time); assertNotNull(query);
            state.accept(new DirectoryPage(query, DirectoryPage.Result.STALE, UUID.randomUUID(), 3, 0, false, Collections.emptyList()), time);
        }
        assertFalse(state.waiting()); assertEquals("stale", state.status()); assertNull(state.poll(NOW + 30_000_000_000L));
    }
}
