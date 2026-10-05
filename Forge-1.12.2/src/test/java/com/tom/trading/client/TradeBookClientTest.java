package com.tom.trading.client;

import com.tom.trading.book.TradeBookBinding;
import com.tom.trading.network.TradeBookNetwork;
import net.minecraft.util.EnumHand;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TradeBookClientTest {
    private static final long NOW = 10_000_000_000L;
    @Test public void bookEntriesShowLocalizedBuyerAndSellerOnSeparateLines() {
        String record = "Furkan15 -> Veras | 64 Iron Ingot <- 3 Copper Coin";
        assertEquals("Alıcı: Furkan15\nSatıcı: Veras\n64 Iron Ingot <- 3 Copper Coin",
                TradeBookPages.displayEntry(record, "Alıcı", "Satıcı"));
        assertEquals("Buyer: Furkan15\nSeller: Veras\n64 Iron Ingot <- 3 Copper Coin",
                TradeBookPages.displayEntry(record, "Buyer", "Seller"));
        assertEquals("Comprador: Furkan15\nVendedor: Veras\n64 Iron Ingot <- 3 Copper Coin",
                TradeBookPages.displayEntry(record, "Comprador", "Vendedor"));
        List<String> pages = TradeBookPages.layout(Collections.singletonList(record),
                text -> Arrays.asList(TradeBookPages.displayEntry(text, "Alıcı", "Satıcı").split("\n", -1)));
        assertEquals(Collections.singletonList("Alıcı: Furkan15\nSatıcı: Veras\n64 Iron Ingot <- 3 Copper Coin"), pages);
    }
    @Test public void roleCaptionsLeaveUnknownOrIncompleteEntryTextUnchanged() {
        for (String record : Arrays.asList("", "No recorded trades yet.", " -> Seller | 1 item", "Buyer ->  | 1 item",
                "Buyer -> Seller", "Buyer -> Seller | ")) {
            assertEquals(record, TradeBookPages.displayEntry(record, "Alıcı", "Satıcı"));
        }
        assertEquals("Alıcı: Buyer\nSatıcı: Seller\n1 Item -> Special | Name <- 2 Coin",
                TradeBookPages.displayEntry("Buyer -> Seller | 1 Item -> Special | Name <- 2 Coin", "Alıcı", "Satıcı"));
    }
    @Test public void staleChangedHandWorldAndLateRepliesCannotOpenAnotherBook() {
        TradeBookClientState state = new TradeBookClientState(); TradeBookBinding book = new TradeBookBinding(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        TradeBookNetwork.Open query = state.begin(book, 0, EnumHand.OFF_HAND, NOW);
        TradeBookNetwork.Opened response = new TradeBookNetwork.Opened(query, book, TradeBookNetwork.Result.OK, Collections.singletonList("trade"));
        assertNull(state.begin(book, 0, EnumHand.MAIN_HAND, NOW));
        assertFalse(state.accept(response, book, 7, NOW));
        assertFalse(state.accept(response, new TradeBookBinding(book.world, book.machine, UUID.randomUUID()), 0, NOW));
        TradeBookNetwork.Open wrongHand = new TradeBookNetwork.Open(query.requestId, 0, EnumHand.MAIN_HAND);
        assertFalse(state.accept(new TradeBookNetwork.Opened(wrongHand, book, response.result, response.rows), book, 0, NOW));
        assertTrue(state.accept(response, book, 0, NOW)); assertFalse(state.accept(response, book, 0, NOW));
        TradeBookNetwork.Open next = state.begin(book, 0, EnumHand.OFF_HAND, NOW + 600_000_000L);
        assertFalse(state.accept(response, book, 0, NOW + 600_000_000L));
        assertFalse(state.accept(new TradeBookNetwork.Opened(next, book, response.result, response.rows), book, 0, NOW + 16_000_000_000L));
        state.clear(); assertFalse(state.accept(response, book, 0, NOW));
    }
    @Test public void layoutUsesOnlyRecentWholeEntriesWhenPageCapacityIsReached() {
        List<String> records = new ArrayList<>(); for (int i = 0; i < 100; i++) records.add("entry " + i);
        List<String> pages = TradeBookPages.layout(records, text -> Collections.nCopies(20, text));
        assertTrue(pages.size() <= 50); assertTrue(pages.get(0).startsWith("entry 0"));
        String all = String.join("\n", pages); assertTrue(all.contains("entry 32")); assertFalse(all.contains("entry 33"));
        for (String page : pages) assertTrue(page.split("\n", -1).length <= 14);
        assertEquals(Collections.singletonList(""), TradeBookPages.layout(Collections.emptyList(), Collections::singletonList));
    }
    @Test public void switchingBooksSupersedesTheOldRequestAfterNormalRateSpacing() {
        TradeBookClientState state = new TradeBookClientState();
        TradeBookBinding first = new TradeBookBinding(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        TradeBookBinding second = new TradeBookBinding(first.world, UUID.randomUUID(), UUID.randomUUID());
        TradeBookNetwork.Open old = state.begin(first, 0, EnumHand.MAIN_HAND, NOW);
        TradeBookNetwork.Open current = state.begin(second, 0, EnumHand.OFF_HAND, NOW + 600_000_000L);
        assertNotNull(current);
        assertFalse(state.accept(new TradeBookNetwork.Opened(old, first, TradeBookNetwork.Result.OK, Collections.singletonList("first")), second, 0, NOW + 600_000_000L));
        assertTrue(state.accept(new TradeBookNetwork.Opened(current, second, TradeBookNetwork.Result.OK, Collections.singletonList("second")), second, 0, NOW + 600_000_000L));
    }
    @Test public void extremelyLongDisplayNamesHaveAnExplicitBoundedAbbreviation() {
        List<String> pages = TradeBookPages.layout(Collections.singletonList("long"), text -> Collections.nCopies(1000, "line"));
        assertEquals(50, pages.size()); assertTrue(pages.get(49).endsWith("..."));
    }
}
