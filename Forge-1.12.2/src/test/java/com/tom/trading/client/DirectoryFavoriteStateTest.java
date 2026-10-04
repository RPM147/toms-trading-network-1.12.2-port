package com.tom.trading.client;

import com.tom.trading.directory.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DirectoryFavoriteStateTest {
    private static final long NOW = 10_000_000_000L, LATER = NOW + 600_000_000L;
    private final UUID world = UUID.randomUUID(), machine = UUID.randomUUID();
    private DirectoryPage reply(DirectoryQuery query, boolean favorite) {
        MachineDirectoryEntry entry = new MachineDirectoryEntry(new MachineAddress(0, 1, 64, 1), machine,
                UUID.randomUUID(), "Owner", "Shop", MachineDirectoryEntry.Evidence.OBSERVED);
        return new DirectoryPage(query, DirectoryPage.Result.OK, world, 1, 1, false,
                Collections.singletonList(new DirectoryPage.Row(entry, DirectoryPage.State.RECORDED, favorite)));
    }
    @Test public void favoriteMutationUsesVerifiedCursorAndWaitsForNormalRateLimit() {
        DirectoryClientState state = new DirectoryClientState(0, NOW);
        DirectoryPage shown = reply(state.poll(NOW), false); assertTrue(state.accept(shown, NOW));
        assertTrue(state.changeFavorite(shown, shown.rows.get(0), NOW));
        assertTrue(state.waiting()); assertNull(state.page()); assertNull(state.poll(NOW));
        assertFalse(state.changeFavorite(shown, shown.rows.get(0), NOW));
        DirectoryQuery query = state.poll(LATER);
        assertEquals(machine, query.favoriteMachine); assertTrue(query.favoriteValue);
        assertEquals(world, query.expectedWorld); assertEquals(1, query.expectedRevision); assertEquals(0, query.page);
        DirectoryQuery wrong = new DirectoryQuery(query.screenId, query.requestId, 0, 0, world, 1, "", query.tab);
        assertFalse(state.accept(reply(wrong, true), LATER));
        assertTrue(state.accept(reply(query, true), LATER));
        DirectoryPage saved = state.page(); assertTrue(saved.rows.get(0).favorite);
        assertTrue(state.changeFavorite(saved, saved.rows.get(0), LATER));
        DirectoryQuery remove = state.poll(LATER + 600_000_000L);
        assertEquals(machine, remove.favoriteMachine); assertFalse(remove.favoriteValue);
    }
    @Test public void tabSearchAndRefreshCannotSilentlyCancelFavoriteBeforeAcknowledgement() {
        DirectoryClientState state = new DirectoryClientState(0, NOW);
        DirectoryPage shown = reply(state.poll(NOW), false); state.accept(shown, NOW);
        state.changeFavorite(shown, shown.rows.get(0), NOW);
        state.setTab(DirectoryTab.FAVORITES, NOW);
        state.setSearch("iron", NOW); state.refresh(NOW);
        assertTrue(state.favoriteChanging()); assertEquals(DirectoryTab.ALL, state.tab()); assertEquals("", state.search());
        DirectoryQuery query = state.poll(LATER);
        assertEquals(DirectoryTab.ALL, query.tab); assertEquals(machine, query.favoriteMachine);
        assertFalse(state.changeFavorite(shown, shown.rows.get(0), LATER));
        state.setTab(DirectoryTab.FAVORITES, LATER); assertEquals(DirectoryTab.ALL, state.tab());
        state.accept(reply(query, true), LATER);
        assertFalse(state.favoriteChanging());
        state.setTab(DirectoryTab.FAVORITES, LATER); assertEquals(DirectoryTab.FAVORITES, state.tab());
        state.setSearch("iron", LATER);
        assertNull(state.poll(LATER + 600_000_000L).favoriteMachine);
    }
    @Test public void staleFavoriteDoesNotSilentlyRetryTheMutationOrClaimSuccess() {
        DirectoryClientState state = new DirectoryClientState(0, NOW);
        DirectoryPage shown = reply(state.poll(NOW), false); state.accept(shown, NOW);
        state.changeFavorite(shown, shown.rows.get(0), NOW);
        DirectoryQuery query = state.poll(LATER);
        state.accept(new DirectoryPage(query, DirectoryPage.Result.STALE, world, 2, 0, false, Collections.emptyList()), LATER);
        assertEquals("favorite_stale", state.status()); assertFalse(state.waiting());
        assertNull(state.poll(LATER + 20_000_000_000L));
        state.refresh(LATER); assertNull(state.poll(LATER + 600_000_000L).favoriteMachine);
    }
    @Test public void bookmarksRetainFavoritesFilterButNotAMutation() {
        DirectoryClientState state = new DirectoryClientState(0, NOW); state.setTab(DirectoryTab.FAVORITES, NOW);
        DirectoryPage shown = reply(state.poll(NOW), true); state.accept(shown, NOW); state.selectIndex(0);
        DirectoryClientState other = new DirectoryClientState(0, NOW);
        other.restore(state.bookmark(30), NOW);
        DirectoryQuery query = other.poll(NOW);
        assertEquals(DirectoryTab.FAVORITES, query.tab); assertNull(query.favoriteMachine);
        other.accept(reply(query, true), NOW); assertEquals(machine, other.selected().entry.machineId);
        assertEquals(30, other.consumeRestoredScroll());
    }
    @Test public void mutationDtoRequiresAnExplicitWorldAndFirstPage() {
        try { new DirectoryQuery(UUID.randomUUID(), 1, 0, 0, null, 0, "", DirectoryTab.ALL, machine, true); fail(); }
        catch (IllegalArgumentException expected) { }
        try { new DirectoryQuery(UUID.randomUUID(), 1, 0, 1, world, 1, "", DirectoryTab.ALL, machine, true); fail(); }
        catch (IllegalArgumentException expected) { }
        try { new DirectoryQuery(UUID.randomUUID(), 1, 0, 0, world, 1, "", DirectoryTab.ALL, null, true); fail(); }
        catch (IllegalArgumentException expected) { }
    }
}
