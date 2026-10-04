package com.tom.trading.trade;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Regression coverage for the public trade count, independent of per-definition quantities. */
public class LargeBatchTradeTest {
    @Test
    public void batchesAbove64CompleteWithoutTruncationOrLoss() {
        TradeItem payment = item("example:payment", 1024);
        TradeItem sale = item("example:sale", 1024);
        for (int count : new int[] {65, 128, 256, 1024}) {
            TradePlan plan = TradePlanner.plan(offer(payment, sale), TradePolicy.NORMAL, count,
                    inventory(payment, 1024, 36, 1024), inventory(sale, 1024, 8, 1024),
                    InventorySnapshot.emptySlots(8, 1024));
            assertTrue(plan.isComplete());
            assertEquals(count, plan.getCompletedCount());
            assertEquals(1024 - count, plan.getPlayerAfter().countExact(payment));
            assertEquals(count, plan.getPlayerAfter().countExact(sale));
            assertEquals(1024 - count, plan.getSaleStockAfter().countExact(sale));
            assertEquals(count, plan.getEarningsAfter().countExact(payment));
        }
    }

    @Test
    public void vanillaStacksAlsoSupportBatchesLargerThanOneStack() {
        TradeItem payment = item("example:payment", 64);
        TradeItem sale = item("example:sale", 64);
        TradePlan plan = TradePlanner.plan(offer(payment, sale), TradePolicy.NORMAL, 256,
                inventory(payment, 512, 36, 64), inventory(sale, 512, 8, 64),
                InventorySnapshot.emptySlots(8, 64));
        assertTrue(plan.isComplete());
        assertEquals(256, plan.getCompletedCount());
        assertEquals(256, plan.getPlayerAfter().countExact(payment));
        assertEquals(256, plan.getPlayerAfter().countExact(sale));
        assertEquals(256, plan.getSaleStockAfter().countExact(sale));
        assertEquals(256, plan.getEarningsAfter().countExact(payment));
        for (InventorySnapshot after : new InventorySnapshot[] {
                plan.getPlayerAfter(), plan.getSaleStockAfter(), plan.getEarningsAfter()}) {
            for (InventorySlot slot : after.getSlots()) assertTrue(slot.getCount() <= 64);
        }
    }

    @Test
    public void oversizedBatchStopsAtLastWholeUnitForEveryResourceLimit() {
        TradeItem payment = item("example:payment", 1024);
        TradeItem sale = item("example:sale", 1024);
        InventorySnapshot richPlayer = inventory(payment, 1024, 36, 1024);
        InventorySnapshot richStock = inventory(sale, 1024, 8, 1024);
        InventorySnapshot freeEarnings = InventorySnapshot.emptySlots(8, 1024);
        assertPartial(payment, sale, richPlayer, inventory(sale, 129, 8, 1024), freeEarnings,
                TradeResultCode.MACHINE_MISSING_INPUT, 129);
        assertPartial(payment, sale, inventory(payment, 129, 36, 1024), richStock, freeEarnings,
                TradeResultCode.TRADER_MISSING_INPUT, 129);
        assertPartial(payment, sale, richPlayer, richStock,
                InventorySnapshot.of(InventorySlot.occupied(payment, 895, 1024)),
                TradeResultCode.MACHINE_NO_SPACE, 129);
        assertPartial(payment, sale,
                InventorySnapshot.of(InventorySlot.occupied(payment, 1024, 1024),
                        InventorySlot.occupied(sale, 895, 1024)),
                richStock, freeEarnings, TradeResultCode.TRADER_NO_SPACE, 129);
        assertPartial(payment, sale, richPlayer, richStock,
                InventorySnapshot.of(InventorySlot.occupied(payment, 1024, 1024)),
                TradeResultCode.MACHINE_NO_SPACE, 0);
    }

    @Test
    public void creative1024BatchStillConsumesPaymentAndLeavesMachineUntouched() {
        TradeItem payment = item("example:payment", 1024);
        TradeItem sale = item("example:sale", 1024);
        InventorySnapshot stock = InventorySnapshot.emptySlots(8, 1024);
        InventorySnapshot earnings = inventory(payment, 1024, 8, 1024);
        TradePlan plan = TradePlanner.plan(offer(payment, sale), TradePolicy.CREATIVE, 1024,
                inventory(payment, 1024, 36, 1024), stock, earnings);
        assertTrue(plan.isComplete());
        assertEquals(1024, plan.getCompletedCount());
        assertEquals(0, plan.getPlayerAfter().countExact(payment));
        assertEquals(1024, plan.getPlayerAfter().countExact(sale));
        assertEquals(stock, plan.getSaleStockAfter());
        assertEquals(earnings, plan.getEarningsAfter());
    }

    @Test
    public void invalidBatchCountsLeaveEveryInventoryUntouched() {
        TradeItem payment = item("example:payment", 1024);
        TradeItem sale = item("example:sale", 1024);
        InventorySnapshot player = inventory(payment, 1024, 36, 1024);
        InventorySnapshot stock = inventory(sale, 1024, 8, 1024);
        InventorySnapshot earnings = InventorySnapshot.emptySlots(8, 1024);
        for (int count : new int[] {Integer.MIN_VALUE, -1, 0, 1025, Integer.MAX_VALUE}) {
            TradePlan plan = TradePlanner.plan(offer(payment, sale), TradePolicy.NORMAL, count,
                    player, stock, earnings);
            assertEquals(TradeResultCode.INVALID_REQUEST, plan.getResultCode());
            assertEquals(0, plan.getCompletedCount());
            assertEquals(player, plan.getPlayerAfter());
            assertEquals(stock, plan.getSaleStockAfter());
            assertEquals(earnings, plan.getEarningsAfter());
        }
    }

    private static void assertPartial(TradeItem payment, TradeItem sale, InventorySnapshot player,
            InventorySnapshot stock, InventorySnapshot earnings, TradeResultCode result, int completed) {
        TradePlan plan = TradePlanner.plan(offer(payment, sale), TradePolicy.NORMAL, 1024,
                player, stock, earnings);
        assertEquals(result, plan.getResultCode());
        assertEquals(completed, plan.getCompletedCount());
        assertEquals(player.countExact(payment) - completed, plan.getPlayerAfter().countExact(payment));
        assertEquals(player.countExact(sale) + completed, plan.getPlayerAfter().countExact(sale));
        assertEquals(stock.countExact(sale) - completed, plan.getSaleStockAfter().countExact(sale));
        assertEquals(earnings.countExact(payment) + completed, plan.getEarningsAfter().countExact(payment));
    }

    private static TradeOffer offer(TradeItem payment, TradeItem sale) {
        return new TradeOffer(Collections.singletonList(TradeDefinition.direct(payment, 1, true)),
                Collections.singletonList(TradeDefinition.direct(sale, 1, true)));
    }

    private static TradeItem item(String id, int limit) {
        return new TradeItem(id, 0, null, Collections.emptySet(), limit);
    }

    private static InventorySnapshot inventory(TradeItem item, int count, int slots, int limit) {
        List<InventorySlot> contents = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            int amount = Math.min(count, limit);
            contents.add(amount == 0 ? InventorySlot.empty(limit) : InventorySlot.occupied(item, amount, limit));
            count -= amount;
        }
        assertEquals("Test fixture must fit", 0, count);
        return new InventorySnapshot(contents);
    }
}
