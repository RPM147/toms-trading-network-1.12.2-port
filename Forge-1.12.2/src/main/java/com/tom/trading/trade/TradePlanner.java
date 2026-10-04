package com.tom.trading.trade;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Plans bounded trades against immutable snapshots.
 *
 * <p>Each requested unit is simulated on copies and becomes the next working
 * state only after all legs succeed. A failed unit therefore leaves every
 * inventory exactly at the state produced by the previously completed units.</p>
 */
public final class TradePlanner {
    private TradePlanner() {
    }

    public static TradePlan plan(
            TradeOffer offer,
            TradePolicy policy,
            int requestedCount,
            InventorySnapshot player,
            InventorySnapshot saleStock,
            InventorySnapshot earnings
    ) {
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(saleStock, "saleStock");
        Objects.requireNonNull(earnings, "earnings");

        if (requestedCount < 1 || requestedCount > TradeLimits.MAX_BATCH_SIZE) {
            return unchangedPlan(
                    policy,
                    requestedCount,
                    TradeResultCode.INVALID_REQUEST,
                    player,
                    saleStock,
                    earnings
            );
        }

        TradeResultCode offerValidation = offer.validate();
        if (offerValidation != TradeResultCode.SUCCESS) {
            return unchangedPlan(
                    policy,
                    requestedCount,
                    offerValidation,
                    player,
                    saleStock,
                    earnings
            );
        }

        MutableInventory workingPlayer = new MutableInventory(player);
        MutableInventory workingSaleStock = new MutableInventory(saleStock);
        MutableInventory workingEarnings = new MutableInventory(earnings);
        int completed = 0;
        TradeResultCode result = TradeResultCode.SUCCESS;
        SelectionBudget selectionBudget = new SelectionBudget();
        Map<TradeItem, Long> paidItems = new LinkedHashMap<>();
        Map<TradeItem, Long> deliveredTotals = new LinkedHashMap<>();

        while (completed < requestedCount) {
            MutableInventory nextPlayer = new MutableInventory(workingPlayer);
            MutableInventory nextSaleStock = new MutableInventory(workingSaleStock);
            MutableInventory nextEarnings = new MutableInventory(workingEarnings);

            List<StackAmount> removedPayments = nextPlayer.reserve(offer.getPayments());
            if (removedPayments == null) {
                result = TradeResultCode.TRADER_MISSING_INPUT;
                break;
            }

            List<StackAmount> deliveredItems;
            if (policy == TradePolicy.CREATIVE) {
                deliveredItems = materialize(offer.getSales());
            } else {
                deliveredItems = nextSaleStock.reserve(offer.getSales());
                if (deliveredItems == null) {
                    result = TradeResultCode.MACHINE_MISSING_INPUT;
                    break;
                }
            }

            TradeResultCode spaceFailure = !nextPlayer.insert(deliveredItems) ? TradeResultCode.TRADER_NO_SPACE
                    : policy == TradePolicy.NORMAL && !nextEarnings.insert(removedPayments)
                    ? TradeResultCode.MACHINE_NO_SPACE : null;
            if (spaceFailure != null) {
                UnitState alternative = capacityAlternative(offer, policy, workingPlayer,
                        workingSaleStock, workingEarnings, selectionBudget);
                if (alternative == null) {
                    result = selectionBudget.exhausted ? TradeResultCode.SELECTION_TOO_COMPLEX : spaceFailure;
                    break;
                }
                nextPlayer = alternative.player;
                nextSaleStock = alternative.stock;
                nextEarnings = alternative.earnings;
                removedPayments = alternative.payments;
                deliveredItems = alternative.delivered;
            }
            if (policy == TradePolicy.NORMAL
                    && !combinedTotals(workingPlayer, workingSaleStock, workingEarnings).equals(
                            combinedTotals(nextPlayer, nextSaleStock, nextEarnings)
                    )) {
                result = TradeResultCode.INVARIANT_VIOLATION;
                break;
            }

            workingPlayer = nextPlayer;
            workingSaleStock = nextSaleStock;
            workingEarnings = nextEarnings;
            accumulate(paidItems, removedPayments);
            accumulate(deliveredTotals, deliveredItems);
            completed++;
        }

        return new TradePlan(
                policy,
                requestedCount,
                completed,
                result,
                player,
                workingPlayer.toSnapshot(),
                saleStock,
                workingSaleStock.toSnapshot(),
                earnings,
                workingEarnings.toSnapshot(),
                paidItems,
                deliveredTotals
        );
    }

    private static TradePlan unchangedPlan(
            TradePolicy policy,
            int requestedCount,
            TradeResultCode result,
            InventorySnapshot player,
            InventorySnapshot saleStock,
            InventorySnapshot earnings
    ) {
        return new TradePlan(
                policy,
                requestedCount,
                0,
                result,
                player,
                player,
                saleStock,
                saleStock,
                earnings,
                earnings,
                Collections.emptyMap(),
                Collections.emptyMap()
        );
    }

    private static void accumulate(Map<TradeItem, Long> totals, List<StackAmount> selected) {
        for (StackAmount stack : selected) {
            totals.put(stack.item, Math.addExact(totals.getOrDefault(stack.item, 0L), (long) stack.count));
        }
    }

    private static List<StackAmount> materialize(List<TradeDefinition> definitions) {
        ArrayList<StackAmount> stacks = new ArrayList<>(definitions.size());
        for (TradeDefinition definition : definitions) {
            stacks.add(new StackAmount(definition.getPrototype(), definition.getQuantity()));
        }
        return stacks;
    }

    /** Fast max-flow succeeds normally; only a capacity failure needs alternative allocations. */
    private static UnitState capacityAlternative(TradeOffer offer, TradePolicy policy, MutableInventory player,
                                                 MutableInventory stock, MutableInventory earnings,
                                                 SelectionBudget budget) {
        return reserveToFit(offer.getPayments(), 0, 0, offer.getPayments().get(0).getQuantity(),
                player, policy == TradePolicy.NORMAL ? earnings : null, budget, null, (paidPlayer, paidEarnings, payments) -> {
                    if (policy == TradePolicy.CREATIVE) {
                        MutableInventory delivered = new MutableInventory(paidPlayer);
                        List<StackAmount> sales = materialize(offer.getSales());
                        return delivered.insert(sales)
                                ? new UnitState(delivered, stock, earnings, Selection.toList(payments), sales) : null;
                    }
                    return reserveToFit(offer.getSales(), 0, 0, offer.getSales().get(0).getQuantity(),
                            stock, paidPlayer, budget, null, (remainingStock, deliveredPlayer, sales) ->
                                    new UnitState(deliveredPlayer, remainingStock, paidEarnings,
                                            Selection.toList(payments), Selection.toList(sales)));
                });
    }

    /**
     * Searches quantities, not just slot order. Every branch owns its snapshots;
     * payments can free player slots, and only real variants fitting the destination
     * are inserted. The request-wide budget bounds even adversarial overlapping offers.
     */
    private static UnitState reserveToFit(List<TradeDefinition> definitions, int definitionIndex, int startSlot,
                                           int remaining, MutableInventory source, MutableInventory destination,
                                           SelectionBudget budget, Selection selection, ReservationResult continuation) {
        if (!budget.take()) return null;
        if (remaining == 0) {
            if (++definitionIndex == definitions.size()) return continuation.accept(source, destination, selection);
            remaining = definitions.get(definitionIndex).getQuantity();
            startSlot = 0;
        }
        TradeDefinition definition = definitions.get(definitionIndex);
        int available = 0, first = -1;
        for (int i = startSlot; i < source.slots.size(); i++) {
            MutableSlot slot = source.slots.get(i);
            if (slot.item != null && ItemMatcher.matches(slot.item, definition)) {
                available += slot.count;
                if (first < 0) first = i;
            }
        }
        if (available < remaining) return null;
        MutableSlot slot = source.slots.get(first);
        int maximum = Math.min(remaining, slot.count);
        if (destination != null) maximum = Math.min(maximum, destination.capacityFor(slot.item, maximum));
        // If all later matching slots together cannot satisfy the demand, smaller choices cannot work.
        int minimum = Math.max(0, remaining - (available - slot.count));
        for (int amount = maximum; amount >= minimum && !budget.exhausted; amount--) {
            MutableInventory nextSource = source, nextDestination = destination;
            if (amount > 0) {
                nextSource = new MutableInventory(source);
                MutableSlot consumed = nextSource.slots.get(first);
                consumed.count -= amount;
                if (consumed.count == 0) consumed.item = null;
                if (destination != null) {
                    nextDestination = new MutableInventory(destination);
                    if (!nextDestination.insert(java.util.Collections.singletonList(new StackAmount(slot.item, amount)))) continue;
                }
            }
            UnitState result = reserveToFit(definitions, definitionIndex, first + 1, remaining - amount,
                    nextSource, nextDestination, budget,
                    amount == 0 ? selection : new Selection(selection, new StackAmount(slot.item, amount)), continuation);
            if (result != null) return result;
        }
        return null;
    }

    private interface ReservationResult {
        UnitState accept(MutableInventory source, MutableInventory destination, Selection selection);
    }

    /** Persistent search path: discarded branches never enter the receipt or copy whole leg lists. */
    private static final class Selection {
        final Selection previous;
        final StackAmount stack;
        Selection(Selection previous, StackAmount stack) { this.previous = previous; this.stack = stack; }
        static List<StackAmount> toList(Selection selected) {
            List<StackAmount> result = new ArrayList<>();
            for (; selected != null; selected = selected.previous) result.add(selected.stack);
            Collections.reverse(result);
            return result;
        }
    }

    private static final class UnitState {
        final MutableInventory player, stock, earnings;
        final List<StackAmount> payments, delivered;
        UnitState(MutableInventory player, MutableInventory stock, MutableInventory earnings,
                  List<StackAmount> payments, List<StackAmount> delivered) {
            this.player = player; this.stock = stock; this.earnings = earnings;
            this.payments = payments; this.delivered = delivered;
        }
    }

    private static final class SelectionBudget {
        private int remaining = 2048; // Shared by the entire batch, not reset for each failed unit.
        private boolean exhausted;
        boolean take() {
            if (remaining-- > 0) return true;
            exhausted = true;
            return false;
        }
    }

    private static Map<TradeItem, Long> combinedTotals(MutableInventory... inventories) {
        HashMap<TradeItem, Long> totals = new HashMap<>();
        for (MutableInventory inventory : inventories) {
            for (MutableSlot slot : inventory.slots) {
                if (slot.item != null) {
                    Long previous = totals.get(slot.item);
                    totals.put(slot.item, (previous == null ? 0L : previous) + slot.count);
                }
            }
        }
        return totals;
    }

    private static final class StackAmount {
        private final TradeItem item;
        private final int count;

        private StackAmount(TradeItem item, int count) {
            this.item = item;
            this.count = count;
        }
    }

    private static final class MutableSlot {
        private TradeItem item;
        private int count;
        private final int slotLimit;

        private MutableSlot(InventorySlot slot) {
            this.item = slot.getItem();
            this.count = slot.getCount();
            this.slotLimit = slot.getSlotLimit();
        }

        private MutableSlot(MutableSlot slot) {
            this.item = slot.item;
            this.count = slot.count;
            this.slotLimit = slot.slotLimit;
        }
    }

    private static final class MutableInventory {
        private final List<MutableSlot> slots;

        private MutableInventory(InventorySnapshot snapshot) {
            this.slots = new ArrayList<>(snapshot.size());
            for (InventorySlot slot : snapshot.getSlots()) {
                this.slots.add(new MutableSlot(slot));
            }
        }

        private MutableInventory(MutableInventory inventory) {
            this.slots = new ArrayList<>(inventory.slots.size());
            for (MutableSlot slot : inventory.slots) {
                this.slots.add(new MutableSlot(slot));
            }
        }

        /**
         * Uses a tiny max-flow graph so overlapping direct/Ore definitions
         * cannot reserve the same stack twice or fail because of greedy order.
         */
        private List<StackAmount> reserve(List<TradeDefinition> definitions) {
            int definitionCount = definitions.size();
            int slotCount = slots.size();
            int source = 0;
            int definitionStart = 1;
            int slotStart = definitionStart + definitionCount;
            int sink = slotStart + slotCount;
            int nodeCount = sink + 1;
            int[][] capacity = new int[nodeCount][nodeCount];
            int totalDemand = 0;

            for (int definitionIndex = 0; definitionIndex < definitionCount; definitionIndex++) {
                TradeDefinition definition = definitions.get(definitionIndex);
                totalDemand = Math.addExact(totalDemand, definition.getQuantity());
                int definitionNode = definitionStart + definitionIndex;
                capacity[source][definitionNode] = definition.getQuantity();
                for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                    MutableSlot slot = slots.get(slotIndex);
                    if (slot.item != null && ItemMatcher.matches(slot.item, definition)) {
                        capacity[definitionNode][slotStart + slotIndex] = definition.getQuantity();
                    }
                }
            }
            for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                capacity[slotStart + slotIndex][sink] = slots.get(slotIndex).count;
            }

            int[][] flow = new int[nodeCount][nodeCount];
            int maxFlow = 0;
            int[] parent = new int[nodeCount];
            while (findAugmentingPath(capacity, flow, source, sink, parent)) {
                int pathCapacity = Integer.MAX_VALUE;
                for (int node = sink; node != source; node = parent[node]) {
                    int previous = parent[node];
                    pathCapacity = Math.min(
                            pathCapacity,
                            capacity[previous][node] - flow[previous][node]
                    );
                }
                for (int node = sink; node != source; node = parent[node]) {
                    int previous = parent[node];
                    flow[previous][node] += pathCapacity;
                    flow[node][previous] -= pathCapacity;
                }
                maxFlow = Math.addExact(maxFlow, pathCapacity);
            }

            if (maxFlow != totalDemand) {
                return null;
            }

            ArrayList<StackAmount> removed = new ArrayList<>();
            for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                int removedFromSlot = 0;
                int slotNode = slotStart + slotIndex;
                for (int definitionIndex = 0; definitionIndex < definitionCount; definitionIndex++) {
                    removedFromSlot += flow[definitionStart + definitionIndex][slotNode];
                }
                if (removedFromSlot > 0) {
                    MutableSlot slot = slots.get(slotIndex);
                    removed.add(new StackAmount(slot.item, removedFromSlot));
                    slot.count -= removedFromSlot;
                    if (slot.count == 0) {
                        slot.item = null;
                    }
                }
            }
            return removed;
        }

        private int capacityFor(TradeItem item, int needed) {
            int capacity = 0;
            for (MutableSlot slot : slots) {
                if (slot.item == null || ItemMatcher.canMerge(slot.item, item)) {
                    capacity += Math.max(0, Math.min(slot.slotLimit, item.getMaxStackSize()) - slot.count);
                    if (capacity >= needed) return needed;
                }
            }
            return capacity;
        }

        private boolean insert(List<StackAmount> stacks) {
            for (StackAmount stack : stacks) {
                int remaining = stack.count;
                for (MutableSlot slot : slots) {
                    if (slot.item != null && ItemMatcher.canMerge(slot.item, stack.item)) {
                        int limit = Math.min(slot.slotLimit, stack.item.getMaxStackSize());
                        int inserted = Math.min(remaining, limit - slot.count);
                        if (inserted > 0) {
                            slot.count += inserted;
                            remaining -= inserted;
                        }
                    }
                    if (remaining == 0) {
                        break;
                    }
                }
                for (MutableSlot slot : slots) {
                    if (remaining == 0) {
                        break;
                    }
                    if (slot.item == null) {
                        int limit = Math.min(slot.slotLimit, stack.item.getMaxStackSize());
                        int inserted = Math.min(remaining, limit);
                        slot.item = stack.item;
                        slot.count = inserted;
                        remaining -= inserted;
                    }
                }
                if (remaining != 0) {
                    return false;
                }
            }
            return true;
        }

        private InventorySnapshot toSnapshot() {
            ArrayList<InventorySlot> snapshotSlots = new ArrayList<>(slots.size());
            for (MutableSlot slot : slots) {
                if (slot.item == null) {
                    snapshotSlots.add(InventorySlot.empty(slot.slotLimit));
                } else {
                    snapshotSlots.add(InventorySlot.occupied(slot.item, slot.count, slot.slotLimit));
                }
            }
            return new InventorySnapshot(snapshotSlots);
        }

        private static boolean findAugmentingPath(
                int[][] capacity,
                int[][] flow,
                int source,
                int sink,
                int[] parent
        ) {
            Arrays.fill(parent, -1);
            parent[source] = source;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(source);

            while (!queue.isEmpty() && parent[sink] == -1) {
                int node = queue.removeFirst();
                for (int next = 0; next < capacity.length; next++) {
                    if (parent[next] == -1 && capacity[node][next] - flow[node][next] > 0) {
                        parent[next] = node;
                        queue.addLast(next);
                    }
                }
            }
            return parent[sink] != -1;
        }
    }
}
