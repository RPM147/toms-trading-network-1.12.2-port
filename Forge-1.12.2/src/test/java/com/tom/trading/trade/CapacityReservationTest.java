package com.tom.trading.trade;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class CapacityReservationTest {
    @Test public void orePaymentChoosesTheVariantThatFitsEarningsInEitherOrder() {
        TradeItem a = item("test:iron_a", "", 64, "ingotIron");
        TradeItem b = item("test:iron_b", "", 64, "ingotIron");
        TradeItem sale = item("test:diamond", "", 64);
        TradeOffer offer = offer(TradeDefinition.orePayment(a, "ingotIron", 1), TradeDefinition.direct(sale, 1, true));
        InventorySnapshot earnings = padded(8, slot(b, 63));
        for (int order = 0; order < 2; order++) {
            InventorySnapshot player = padded(36, slot(order == 0 ? a : b, 1), slot(order == 0 ? b : a, 1));
            TradePlan plan = TradePlanner.plan(offer, TradePolicy.NORMAL, 1, player, padded(8, slot(sale, 1)), earnings);
            assertEquals("payment order " + order, TradeResultCode.SUCCESS, plan.getResultCode());
            assertEquals(1, plan.getCompletedCount());
            assertEquals(64, plan.getEarningsAfter().getSlot(0).getCount());
            assertEquals(b, plan.getEarningsAfter().getSlot(0).getItem());
            assertEquals(Collections.singletonMap(b, 1L), plan.getPaidItems());
            assertEquals(Collections.singletonMap(sale, 1L), plan.getDeliveredItems());
        }
    }

    @Test public void looseNbtSaleChoosesTheVariantThatFitsPlayerInEitherOrder() {
        TradeItem a = item("test:gem", "A", 64), b = item("test:gem", "B", 64);
        TradeItem payment = item("test:emerald", "", 64);
        TradeOffer offer = offer(TradeDefinition.direct(payment, 1, true), TradeDefinition.direct(a, 1, false));
        InventorySnapshot player = padded(36, slot(payment, 2), slot(b, 63));
        for (int order = 0; order < 2; order++) {
            InventorySnapshot stock = padded(8, slot(order == 0 ? a : b, 1), slot(order == 0 ? b : a, 1));
            TradePlan plan = TradePlanner.plan(offer, TradePolicy.NORMAL, 1, player, stock, InventorySnapshot.emptySlots(8, 64));
            assertEquals("sale order " + order, TradeResultCode.SUCCESS, plan.getResultCode());
            assertEquals(1, plan.getCompletedCount());
            assertEquals(b, plan.getPlayerAfter().getSlot(1).getItem());
            assertEquals(64, plan.getPlayerAfter().getSlot(1).getCount());
            assertEquals(Collections.singletonMap(payment, 1L), plan.getPaidItems());
            assertEquals(Collections.singletonMap(b, 1L), plan.getDeliveredItems());
        }
    }

    @Test public void bothCounterexamplesWorkAtEveryPairOfRelevantSlotPositions() {
        TradeItem a = item("test:iron_a", "", 64, "ingotIron"), b = item("test:iron_b", "", 64, "ingotIron");
        TradeItem pay = item("test:payment", "", 64), sale = item("test:sale", "", 64);
        TradeOffer paymentOffer = offer(TradeDefinition.orePayment(a, "ingotIron", 1), TradeDefinition.direct(sale, 1, true));
        for (int first = 0; first < 36; first++) for (int second = 0; second < 36; second++) {
            if (first == second) continue;
            List<InventorySlot> slots = new ArrayList<>(padded(36).getSlots());
            slots.set(first, slot(a, 1)); slots.set(second, slot(b, 1));
            TradePlan plan = TradePlanner.plan(paymentOffer, TradePolicy.NORMAL, 1, new InventorySnapshot(slots),
                    padded(8, slot(sale, 1)), padded(8, slot(b, 63)));
            assertEquals(first + "," + second, 1, plan.getCompletedCount());
        }
        a = item("test:gem", "A", 64); b = item("test:gem", "B", 64);
        TradeOffer saleOffer = offer(TradeDefinition.direct(pay, 1, true), TradeDefinition.direct(a, 1, false));
        for (int first = 0; first < 8; first++) for (int second = 0; second < 8; second++) {
            if (first == second) continue;
            List<InventorySlot> slots = new ArrayList<>(padded(8).getSlots());
            slots.set(first, slot(a, 1)); slots.set(second, slot(b, 1));
            TradePlan plan = TradePlanner.plan(saleOffer, TradePolicy.NORMAL, 1, padded(36, slot(pay, 2), slot(b, 63)),
                    new InventorySnapshot(slots), InventorySnapshot.emptySlots(8, 64));
            assertEquals(first + "," + second, 1, plan.getCompletedCount());
        }
    }

    @Test public void paymentCanChooseAnotherIdenticalStackToFreeAResultSlot() {
        TradeItem pay = item("test:payment", "", 64), sale = item("test:sale", "", 64);
        TradeOffer offer = offer(TradeDefinition.direct(pay, 1, true), TradeDefinition.direct(sale, 1, true));
        for (TradePolicy policy : TradePolicy.values()) {
            TradePlan plan = TradePlanner.plan(offer, policy, 1, padded(36, slot(pay, 2), slot(pay, 1)),
                    padded(8, slot(sale, 1)), InventorySnapshot.emptySlots(8, 64));
            assertEquals(policy.toString(), 1, plan.getCompletedCount());
            assertEquals(2, plan.getPlayerAfter().countExact(pay));
            assertEquals(1, plan.getPlayerAfter().countExact(sale));
            assertEquals(Collections.singletonMap(pay, 1L), plan.getPaidItems());
            assertEquals(Collections.singletonMap(sale, 1L), plan.getDeliveredItems());
        }
    }

    @Test public void capacityConstraintsDoNotBreakOverlappingOreDefinitions() {
        TradeItem a = item("test:a", "", 64, "oreAny", "oreOnlyA");
        TradeItem b = item("test:b", "", 64, "oreAny");
        TradeItem c = item("test:c", "", 64, "oreAny");
        TradeItem sale = item("test:sale", "", 64);
        TradeOffer offer = new TradeOffer(Arrays.asList(TradeDefinition.orePayment(c, "oreAny", 2),
                TradeDefinition.orePayment(a, "oreOnlyA", 1)), Collections.singletonList(TradeDefinition.direct(sale, 1, true)));
        TradePlan plan = TradePlanner.plan(offer, TradePolicy.NORMAL, 1,
                padded(36, slot(c, 2), slot(a, 1), slot(b, 2)), padded(8, slot(sale, 1)),
                padded(8, slot(a, 63), slot(b, 62)));
        assertEquals(1, plan.getCompletedCount());
        assertEquals(2, plan.getPlayerAfter().countExact(c));
        assertEquals(64, plan.getEarningsAfter().countExact(a));
        assertEquals(64, plan.getEarningsAfter().countExact(b));
        assertEquals(2, plan.getPaidItems().size());
        assertEquals(Long.valueOf(1), plan.getPaidItems().get(a));
        assertEquals(Long.valueOf(2), plan.getPaidItems().get(b));
    }

    @Test public void duplicateVariantSlotsShareDestinationCapacity() {
        TradeItem a = item("test:a", "", 64, "oreAny"), b = item("test:b", "", 64, "oreAny");
        TradeItem sale = item("test:sale", "", 64);
        TradePlan plan = TradePlanner.plan(offer(TradeDefinition.orePayment(a, "oreAny", 2),
                TradeDefinition.direct(sale, 1, true)), TradePolicy.NORMAL, 1,
                padded(36, slot(a, 1), slot(a, 1), slot(b, 2)), padded(8, slot(sale, 1)),
                padded(8, slot(a, 63), slot(b, 62)));
        assertEquals(1, plan.getCompletedCount());
        assertEquals(64, plan.getEarningsAfter().countExact(a));
        assertEquals(63, plan.getEarningsAfter().countExact(b));
    }

    @Test public void looseSalePreservesTheSelectedCapabilityPayload() {
        NBTTagCompound capsA = new NBTTagCompound(), capsB = new NBTTagCompound();
        capsA.setInteger("energy", 1); capsB.setInteger("energy", 2);
        TradeItem a = new TradeItem("test:battery", 0, null, capsA, Collections.emptySet(), 64);
        TradeItem b = new TradeItem("test:battery", 0, null, capsB, Collections.emptySet(), 64);
        TradeItem pay = item("test:pay", "", 64);
        TradePlan plan = TradePlanner.plan(offer(TradeDefinition.direct(pay, 1, true), TradeDefinition.direct(a, 1, false)),
                TradePolicy.NORMAL, 1, padded(36, slot(pay, 2), slot(b, 63)),
                padded(8, slot(a, 1), slot(b, 1)), InventorySnapshot.emptySlots(8, 64));
        assertEquals(1, plan.getCompletedCount());
        assertEquals(64, plan.getPlayerAfter().countExact(b));
        assertEquals(1, plan.getSaleStockAfter().countExact(a));
        assertEquals(0, plan.getPlayerAfter().countExact(a));
        assertEquals(Collections.singletonMap(b, 1L), plan.getDeliveredItems());
    }

    @Test public void actualCapacityFailureLeavesAllInventoriesUntouched() {
        TradeItem a = item("test:a", "", 64, "oreAny"), b = item("test:b", "", 64, "oreAny");
        TradeItem sale = item("test:sale", "", 64);
        InventorySnapshot player = padded(36, slot(a, 1), slot(b, 1)), stock = padded(8, slot(sale, 1));
        InventorySnapshot earnings = padded(8, slot(a, 64), slot(b, 64));
        TradePlan plan = TradePlanner.plan(offer(TradeDefinition.orePayment(a, "oreAny", 1),
                TradeDefinition.direct(sale, 1, true)), TradePolicy.NORMAL, 1, player, stock, earnings);
        assertEquals(0, plan.getCompletedCount());
        assertEquals(TradeResultCode.MACHINE_NO_SPACE, plan.getResultCode());
        assertTrue(plan.getPaidItems().isEmpty()); assertTrue(plan.getDeliveredItems().isEmpty());
        assertEquals(player, plan.getPlayerAfter());
        assertEquals(stock, plan.getSaleStockAfter());
        assertEquals(earnings, plan.getEarningsAfter());
    }

    @Test(timeout = 2000) public void combinatorialSearchFailsClosedAtTheRequestWideBudget() {
        TradeItem pay = item("test:pay", "", 64), sale = item("test:sale", "", 64);
        InventorySlot[] full = new InventorySlot[36];
        Arrays.fill(full, slot(pay, 64));
        InventorySnapshot player = InventorySnapshot.of(full), stock = padded(8, slot(sale, 1));
        InventorySnapshot earnings = InventorySnapshot.emptySlots(8, 64);
        TradePlan plan = TradePlanner.plan(offer(TradeDefinition.direct(pay, 32, true), TradeDefinition.direct(sale, 1, true)),
                TradePolicy.NORMAL, 1024, player, stock, earnings);
        assertEquals(0, plan.getCompletedCount());
        assertEquals(TradeResultCode.SELECTION_TOO_COMPLEX, plan.getResultCode());
        assertTrue(plan.getPaidItems().isEmpty()); assertTrue(plan.getDeliveredItems().isEmpty());
        assertEquals(player, plan.getPlayerAfter());
        assertEquals(stock, plan.getSaleStockAfter());
        assertEquals(earnings, plan.getEarningsAfter());
    }

    static TradeItem item(String id, String variant, int limit, String... ores) {
        NBTTagCompound tag = new NBTTagCompound();
        if (!variant.isEmpty()) tag.setString("variant", variant);
        return new TradeItem(id, 0, tag.isEmpty() ? null : tag, Arrays.asList(ores), limit);
    }
    static InventorySlot slot(TradeItem item, int count) { return InventorySlot.occupied(item, count, 64); }
    static TradeOffer offer(TradeDefinition pay, TradeDefinition sale) {
        return new TradeOffer(Collections.singletonList(pay), Collections.singletonList(sale));
    }
    static InventorySnapshot padded(int size, InventorySlot... prefix) {
        List<InventorySlot> slots = new ArrayList<>(Arrays.asList(prefix));
        while (slots.size() < size) slots.add(slot(item("test:blocker_" + slots.size(), "", 1), 1));
        return new InventorySnapshot(slots);
    }
}
