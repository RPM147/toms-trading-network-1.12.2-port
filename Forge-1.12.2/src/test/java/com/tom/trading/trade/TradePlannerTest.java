package com.tom.trading.trade;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TradePlannerTest {
    @Test
    public void normalTradeAggregatesDuplicateDefinitions() {
        TradeItem emerald = item("minecraft:emerald", 64);
        TradeItem diamond = item("minecraft:diamond", 64);
        TradeOffer offer = offer(
                Arrays.asList(direct(emerald, 2), direct(emerald, 3)),
                Collections.singletonList(direct(diamond, 4))
        );
        InventorySnapshot player = InventorySnapshot.of(
                InventorySlot.occupied(emerald, 5, 64),
                InventorySlot.empty(64)
        );
        InventorySnapshot stock = InventorySnapshot.of(
                InventorySlot.occupied(diamond, 4, 64)
        );
        InventorySnapshot earnings = InventorySnapshot.emptySlots(1, 64);

        TradePlan plan = TradePlanner.plan(
                offer,
                TradePolicy.NORMAL,
                1,
                player,
                stock,
                earnings
        );

        assertTrue(plan.isComplete());
        assertEquals(0, plan.getPlayerAfter().countExact(emerald));
        assertEquals(4, plan.getPlayerAfter().countExact(diamond));
        assertEquals(0, plan.getSaleStockAfter().countExact(diamond));
        assertEquals(5, plan.getEarningsAfter().countExact(emerald));
        assertEquals(Collections.singletonMap(emerald, 5L), plan.getPaidItems());
        assertEquals(Collections.singletonMap(diamond, 4L), plan.getDeliveredItems());
    }

    @Test
    public void failedUnitLeavesAllFourStoresUnchanged() {
        TradeItem coal = item("minecraft:coal", 64);
        TradeItem diamond = item("minecraft:diamond", 64);
        TradeItem gold = item("minecraft:gold_ingot", 64);
        TradeOffer offer = offer(
                Collections.singletonList(direct(coal, 1)),
                Arrays.asList(direct(diamond, 64), direct(gold, 64))
        );
        InventorySnapshot player = InventorySnapshot.of(
                InventorySlot.occupied(coal, 1, 64)
        );
        InventorySnapshot stock = InventorySnapshot.of(
                InventorySlot.occupied(diamond, 64, 64),
                InventorySlot.occupied(gold, 64, 64)
        );
        InventorySnapshot earnings = InventorySnapshot.emptySlots(1, 64);

        TradePlan plan = TradePlanner.plan(
                offer,
                TradePolicy.NORMAL,
                1,
                player,
                stock,
                earnings
        );

        assertEquals(0, plan.getCompletedCount());
        assertEquals(TradeResultCode.TRADER_NO_SPACE, plan.getResultCode());
        assertEquals(player, plan.getPlayerAfter());
        assertEquals(stock, plan.getSaleStockAfter());
        assertEquals(earnings, plan.getEarningsAfter());
        assertTrue(plan.getPaidItems().isEmpty()); assertTrue(plan.getDeliveredItems().isEmpty());
    }

    @Test
    public void matcherSeparatesMetadataNbtAndOreRules() {
        NBTTagCompound firstTag = new NBTTagCompound();
        NBTTagCompound firstMaterial = new NBTTagCompound();
        firstMaterial.setString("material", "cobalt");
        firstTag.setTag("tool", firstMaterial);

        NBTTagCompound secondTag = new NBTTagCompound();
        NBTTagCompound secondMaterial = new NBTTagCompound();
        secondMaterial.setString("material", "manyullyn");
        secondTag.setTag("tool", secondMaterial);

        TradeItem prototype = item("tconstruct:tool", 7, firstTag, 1, "toolRod");
        TradeItem differentNbt = item("tconstruct:tool", 7, secondTag, 1, "toolRod");
        TradeItem differentMetadata = item("tconstruct:tool", 8, firstTag, 1, "toolRod");

        assertFalse(ItemMatcher.matches(differentNbt, TradeDefinition.direct(prototype, 1, true)));
        assertTrue(ItemMatcher.matches(differentNbt, TradeDefinition.direct(prototype, 1, false)));
        assertFalse(ItemMatcher.matches(differentMetadata, TradeDefinition.direct(prototype, 1, false)));
        assertTrue(ItemMatcher.matches(differentNbt, TradeDefinition.orePayment(prototype, "toolRod", 1)));
    }

    @Test
    public void overlappingOreDefinitionsUseNonGreedyReservation() {
        TradeItem flexible = item("example:flexible", 0, null, 64, "oreAny", "oreOnlyFlexible");
        TradeItem alternative = item("example:alternative", 0, null, 64, "oreAny");
        TradeItem result = item("minecraft:diamond", 64);
        TradeOffer offer = offer(
                Arrays.asList(
                        TradeDefinition.orePayment(flexible, "oreAny", 1),
                        TradeDefinition.orePayment(flexible, "oreOnlyFlexible", 1)
                ),
                Collections.singletonList(direct(result, 1))
        );

        TradePlan plan = TradePlanner.plan(
                offer,
                TradePolicy.NORMAL,
                1,
                InventorySnapshot.of(
                        InventorySlot.occupied(flexible, 1, 64),
                        InventorySlot.occupied(alternative, 1, 64),
                        InventorySlot.empty(64)
                ),
                InventorySnapshot.of(InventorySlot.occupied(result, 1, 64)),
                InventorySnapshot.emptySlots(2, 64)
        );

        assertTrue(plan.isComplete());
        assertEquals(1, plan.getPlayerAfter().countExact(result));
        assertEquals(1, plan.getEarningsAfter().countExact(flexible));
        assertEquals(1, plan.getEarningsAfter().countExact(alternative));
        assertEquals(Long.valueOf(1), plan.getPaidItems().get(flexible));
        assertEquals(Long.valueOf(1), plan.getPaidItems().get(alternative));
    }

    @Test
    public void creativeTradeConsumesPaymentButDoesNotTouchMachineInventories() {
        TradeItem emerald = item("minecraft:emerald", 64);
        TradeItem diamond = item("minecraft:diamond", 64);
        TradeItem stockItem = item("minecraft:iron_ingot", 64);
        TradeItem earnedItem = item("minecraft:coal", 64);
        InventorySnapshot stock = InventorySnapshot.of(
                InventorySlot.occupied(stockItem, 12, 64)
        );
        InventorySnapshot earnings = InventorySnapshot.of(
                InventorySlot.occupied(earnedItem, 8, 64)
        );

        TradePlan plan = TradePlanner.plan(
                offer(
                        Collections.singletonList(direct(emerald, 2)),
                        Collections.singletonList(direct(diamond, 5))
                ),
                TradePolicy.CREATIVE,
                1,
                InventorySnapshot.of(
                        InventorySlot.occupied(emerald, 2, 64),
                        InventorySlot.empty(64)
                ),
                stock,
                earnings
        );

        assertTrue(plan.isComplete());
        assertEquals(0, plan.getPlayerAfter().countExact(emerald));
        assertEquals(5, plan.getPlayerAfter().countExact(diamond));
        assertEquals(stock, plan.getSaleStockAfter());
        assertEquals(earnings, plan.getEarningsAfter());
        assertEquals(Collections.singletonMap(emerald, 2L), plan.getPaidItems());
        assertEquals(Collections.singletonMap(diamond, 5L), plan.getDeliveredItems());
    }

    @Test
    public void batchStopsAfterLastWholeSuccessfulUnit() {
        TradeItem emerald = item("minecraft:emerald", 64);
        TradeItem diamond = item("minecraft:diamond", 64);
        TradePlan plan = TradePlanner.plan(
                offer(
                        Collections.singletonList(direct(emerald, 1)),
                        Collections.singletonList(direct(diamond, 1))
                ),
                TradePolicy.NORMAL,
                3,
                InventorySnapshot.of(
                        InventorySlot.occupied(emerald, 3, 64),
                        InventorySlot.empty(64)
                ),
                InventorySnapshot.of(InventorySlot.occupied(diamond, 2, 64)),
                InventorySnapshot.emptySlots(1, 64)
        );

        assertEquals(2, plan.getCompletedCount());
        assertEquals(TradeResultCode.MACHINE_MISSING_INPUT, plan.getResultCode());
        assertEquals(1, plan.getPlayerAfter().countExact(emerald));
        assertEquals(2, plan.getPlayerAfter().countExact(diamond));
        assertEquals(2, plan.getEarningsAfter().countExact(emerald));
        assertEquals(Collections.singletonMap(emerald, 2L), plan.getPaidItems());
        assertEquals(Collections.singletonMap(diamond, 2L), plan.getDeliveredItems());
    }

    @Test
    public void stackUpSizedQuantitiesRemainIntegers() {
        TradeItem payment = item("example:payment", 1024);
        TradeItem result = item("example:result", 1024);
        TradePlan plan = TradePlanner.plan(
                offer(
                        Collections.singletonList(direct(payment, 1024)),
                        Collections.singletonList(direct(result, 1024))
                ),
                TradePolicy.NORMAL,
                1,
                InventorySnapshot.of(
                        InventorySlot.occupied(payment, 1024, 1024),
                        InventorySlot.empty(1024)
                ),
                InventorySnapshot.of(InventorySlot.occupied(result, 1024, 1024)),
                InventorySnapshot.emptySlots(1, 1024)
        );

        assertTrue(plan.isComplete());
        assertEquals(1024, plan.getPlayerAfter().countExact(result));
        assertEquals(1024, plan.getEarningsAfter().countExact(payment));
    }

    @Test
    public void malformedRequestsAndOneSidedOffersFailClosed() {
        TradeItem emerald = item("minecraft:emerald", 64);
        TradeItem diamond = item("minecraft:diamond", 64);
        InventorySnapshot player = InventorySnapshot.of(
                InventorySlot.occupied(emerald, 1, 64),
                InventorySlot.empty(64)
        );
        InventorySnapshot stock = InventorySnapshot.of(
                InventorySlot.occupied(diamond, 1, 64)
        );
        InventorySnapshot earnings = InventorySnapshot.emptySlots(1, 64);

        TradePlan invalidRequest = TradePlanner.plan(
                offer(
                        Collections.singletonList(direct(emerald, 1)),
                        Collections.singletonList(direct(diamond, 1))
                ),
                TradePolicy.NORMAL,
                0,
                player,
                stock,
                earnings
        );
        TradePlan oneSided = TradePlanner.plan(
                offer(Collections.<TradeDefinition>emptyList(), Collections.singletonList(direct(diamond, 1))),
                TradePolicy.NORMAL,
                1,
                player,
                stock,
                earnings
        );

        assertEquals(TradeResultCode.INVALID_REQUEST, invalidRequest.getResultCode());
        assertEquals(TradeResultCode.INVALID_OFFER, oneSided.getResultCode());
        assertEquals(player, invalidRequest.getPlayerAfter());
        assertEquals(player, oneSided.getPlayerAfter());
    }

    private static TradeOffer offer(
            java.util.List<TradeDefinition> payments,
            java.util.List<TradeDefinition> sales
    ) {
        return new TradeOffer(payments, sales);
    }

    private static TradeDefinition direct(TradeItem item, int quantity) {
        return TradeDefinition.direct(item, quantity, true);
    }

    private static TradeItem item(String id, int maxStackSize) {
        return item(id, 0, null, maxStackSize);
    }

    private static TradeItem item(
            String id,
            int metadata,
            NBTTagCompound tag,
            int maxStackSize,
            String... oreNames
    ) {
        return new TradeItem(
                id,
                metadata,
                tag,
                new LinkedHashSet<>(Arrays.asList(oreNames)),
                maxStackSize
        );
    }
}
