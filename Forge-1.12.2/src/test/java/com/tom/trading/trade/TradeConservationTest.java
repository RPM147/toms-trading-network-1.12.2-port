package com.tom.trading.trade;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/** Reproducible planner stress; this does not substitute for live packet/container tests. */
public class TradeConservationTest {
    @Test public void randomizedBatchesConserveEveryMetadataNbtAndCapabilityIdentity() {
        Random random = new Random(0x54544e08L);
        int successfulUnits = 0;
        int failedBatches = 0;
        for (int limit : new int[] {64, 1024}) {
            List<TradeItem> items = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("serial", i % 2);
                NBTTagCompound caps = new NBTTagCompound();
                caps.setInteger("charge", i / 4);
                items.add(new TradeItem("test:item", i / 2 % 2, tag, caps,
                        Arrays.asList("groupAny", "group" + i % 2), limit));
            }
            for (int trial = 0; trial < 1000; trial++) {
                InventorySnapshot player = inventory(random, items, 36, limit);
                InventorySnapshot stock = inventory(random, items, 8, limit);
                InventorySnapshot earnings = trial % 2 == 0 ? InventorySnapshot.emptySlots(8, limit)
                        : inventory(random, items, 8, limit);
                List<TradeDefinition> payments = new ArrayList<>();
                List<TradeDefinition> sales = new ArrayList<>();
                for (int i = 0; i <= trial % 4; i++) {
                    TradeItem payment = items.get(random.nextInt(items.size()));
                    int quantity = 1 + random.nextInt(8);
                    payments.add(trial % 3 == 0 ? TradeDefinition.orePayment(payment, "groupAny", quantity)
                            : TradeDefinition.direct(payment, quantity, trial % 3 == 1));
                    sales.add(TradeDefinition.direct(items.get(random.nextInt(items.size())),
                            1 + random.nextInt(8), trial % 2 == 0));
                }
                TradeOffer offer = new TradeOffer(payments, sales);
                int requested = trial % 5 == 0 ? 64 : 1 + random.nextInt(8);
                Map<TradeItem, Long> before = totals(player, stock, earnings);
                TradePlan plan = TradePlanner.plan(offer, TradePolicy.NORMAL, requested, player, stock, earnings);
                assertEquals("Identity loss/duplication at trial " + trial + ", limit " + limit,
                        before, totals(plan.getPlayerAfter(), plan.getSaleStockAfter(), plan.getEarningsAfter()));
                assertEquals(before, totals(player, stock, earnings)); // Inputs are immutable.
                assertTrue(plan.getCompletedCount() >= 0 && plan.getCompletedCount() <= requested);
                if (plan.getCompletedCount() == 0) {
                    failedBatches++;
                    assertEquals(player, plan.getPlayerAfter());
                    assertEquals(stock, plan.getSaleStockAfter());
                    assertEquals(earnings, plan.getEarningsAfter());
                }
                successfulUnits += plan.getCompletedCount();
                // Independently apply one-unit plans to check partial-batch semantics.
                InventorySnapshot unitPlayer = player, unitStock = stock, unitEarnings = earnings;
                int completed = 0;
                for (; completed < requested; completed++) {
                    TradePlan unit = TradePlanner.plan(offer, TradePolicy.NORMAL, 1,
                            unitPlayer, unitStock, unitEarnings);
                    if (unit.getCompletedCount() == 0) break;
                    unitPlayer = unit.getPlayerAfter();
                    unitStock = unit.getSaleStockAfter();
                    unitEarnings = unit.getEarningsAfter();
                }
                assertEquals(completed, plan.getCompletedCount());
                assertEquals(unitPlayer, plan.getPlayerAfter());
                assertEquals(unitStock, plan.getSaleStockAfter());
                assertEquals(unitEarnings, plan.getEarningsAfter());
            }
        }
        assertTrue("Stress must include successful transfers", successfulUnits > 1000);
        assertTrue("Stress must include atomic failures", failedBatches > 100);
        System.out.println("TTN conservation: 2000 batches; successful units=" + successfulUnits
                + "; zero-completion batches=" + failedBatches + "; seed=0x54544e08");
    }

    private static InventorySnapshot inventory(Random random, List<TradeItem> items, int size, int limit) {
        List<InventorySlot> slots = new ArrayList<>();
        for (int i = 0; i < size; i++) slots.add(random.nextInt(4) == 0 ? InventorySlot.empty(limit)
                : InventorySlot.occupied(items.get(random.nextInt(items.size())), 1 + random.nextInt(limit), limit));
        return new InventorySnapshot(slots);
    }

    private static Map<TradeItem, Long> totals(InventorySnapshot... inventories) {
        Map<TradeItem, Long> totals = new HashMap<>();
        for (InventorySnapshot inventory : inventories) for (InventorySlot slot : inventory.getSlots()) {
            if (!slot.isEmpty()) totals.merge(slot.getItem(), (long) slot.getCount(), Long::sum);
        }
        return totals;
    }
}
