package com.tom.trading.trade;

import com.tom.trading.tile.TileVendingMachine;
import com.tom.trading.item.OreFilters;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Plans and prepares every replacement before touching the server inventories. */
public final class TradeExecutor {
    private TradeExecutor() {}

    public static Outcome execute(EntityPlayerMP player, TileVendingMachine tile, int requested, long offerRevision) {
        return execute(player, tile, requested, offerRevision, () -> null);
    }

    /** A null access result permits the operation; callers supply their own local or remote policy. */
    public static Outcome execute(EntityPlayerMP player, TileVendingMachine tile, int requested, long offerRevision,
                                  java.util.function.Supplier<TradeResultCode> access) {
        if (!tile.matchesOfferRevision(offerRevision)) return new Outcome(0, TradeResultCode.OFFER_CHANGED);
        if (!player.getServerWorld().isCallingFromMinecraftThread()
                || !tile.isDataVersionSupported() || tile.isAutomationQuarantined() || tile.isAutomationRunning()
                || requested < 1 || requested > TradeLimits.MAX_BATCH_SIZE) {
            return new Outcome(0, TradeResultCode.INVALID_REQUEST);
        }
        TradeResultCode denied = access.get();
        if (denied != null) return new Outcome(0, denied);
        TradePlan plan;
        ItemStack[] playerAfter;
        ItemStack[] stockAfter;
        ItemStack[] earningsAfter;
        TradeReceipt receipt;
        String ownerName;
        try {
            Map<TradeItem, ItemStack> prototypes = new HashMap<>();
            InventorySnapshot playerBefore = snapshotPlayer(player, prototypes);
            InventorySnapshot stockBefore = snapshot(tile.getSaleStock(), prototypes);
            InventorySnapshot earningsBefore = snapshot(tile.getEarnings(), prototypes);
            List<TradeDefinition> payments = definitions(tile, false, prototypes);
            List<TradeDefinition> sales = definitions(tile, true, prototypes);
            plan = TradePlanner.plan(new TradeOffer(payments, sales),
                    tile.isCreativeMode() ? TradePolicy.CREATIVE : TradePolicy.NORMAL,
                    requested, playerBefore, stockBefore, earningsBefore);
            if (plan.getCompletedCount() == 0) {
                return new Outcome(0, plan.getResultCode());
            }
            playerAfter = materialize(plan.getPlayerAfter(), prototypes);
            stockAfter = materialize(plan.getSaleStockAfter(), prototypes);
            earningsAfter = materialize(plan.getEarningsAfter(), prototypes);
            tile.ensureMachineIdentity();
            TradeReceipt.Prepared prepared = TradeReceipt.prepare(plan, item -> prototypes.get(item).getDisplayName());
            ownerName = tile.getOwnerNameCache();
            receipt = prepared.bind(player.getUniqueID(), player.getGameProfile().getName(), tile.getMachineUuid(),
                    tile.getOwnerUuid(), ownerName);
            // Item capability and display-name callbacks have completed before this preflight.
            if (!playerBefore.equals(snapshotPlayer(player, new HashMap<>()))
                    || !stockBefore.equals(snapshot(tile.getSaleStock(), new HashMap<>()))
                    || !earningsBefore.equals(snapshot(tile.getEarnings(), new HashMap<>()))) {
                return new Outcome(0, TradeResultCode.INVARIANT_VIOLATION);
            }
        } catch (IllegalArgumentException exception) {
            // Invalid/over-limit installed stacks must not be silently normalized or consumed.
            return new Outcome(0, TradeResultCode.INVALID_OFFER);
        }
        denied = access.get();
        if (denied != null) return new Outcome(0, denied);
        if (!tile.isDataVersionSupported() || tile.isAutomationQuarantined() || tile.isAutomationRunning())
            return new Outcome(0, TradeResultCode.INVALID_REQUEST);
        // Item/capability callbacks in capture and materialization may have changed the offer.
        if (!tile.matchesOfferRevision(offerRevision)) return new Outcome(0, TradeResultCode.OFFER_CHANGED);
        if (!receipt.getMachineUuid().equals(tile.getMachineUuid())
                || !java.util.Objects.equals(receipt.getOwnerUuid(), tile.getOwnerUuid())
                || !java.util.Objects.equals(ownerName, tile.getOwnerNameCache()))
            return new Outcome(0, TradeResultCode.OFFER_CHANGED);
        for (int slot = 0; slot < 36; slot++) {
            player.inventory.setInventorySlotContents(slot, playerAfter[slot]);
        }
        if (!tile.isCreativeMode()) {
            replace(tile.getSaleStock(), stockAfter);
            replace(tile.getEarnings(), earningsAfter);
        }
        player.inventory.markDirty();
        tile.markDirty();
        // The caller records this result before any inventory/state/feedback/chat notification.
        return new Outcome(plan.getCompletedCount(), plan.getResultCode(), receipt);
    }

    private static InventorySnapshot snapshotPlayer(EntityPlayerMP player, Map<TradeItem, ItemStack> prototypes) {
        List<InventorySlot> slots = new ArrayList<>();
        int limit = Math.min(TradeLimits.MAX_QUANTITY, player.inventory.getInventoryStackLimit());
        for (int slot = 0; slot < 36; slot++) {
            slots.add(capture(player.inventory.getStackInSlot(slot), limit, prototypes));
        }
        return new InventorySnapshot(slots);
    }

    private static InventorySnapshot snapshot(ItemStackHandler inventory, Map<TradeItem, ItemStack> prototypes) {
        List<InventorySlot> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            slots.add(capture(inventory.getStackInSlot(slot),
                    Math.min(TradeLimits.MAX_QUANTITY, inventory.getSlotLimit(slot)), prototypes));
        }
        return new InventorySnapshot(slots);
    }

    private static InventorySlot capture(ItemStack stack, int limit, Map<TradeItem, ItemStack> prototypes) {
        if (stack.isEmpty()) return InventorySlot.empty(limit);
        TradeItem item = remember(stack, prototypes);
        return InventorySlot.occupied(item, stack.getCount(), limit);
    }

    private static TradeItem remember(ItemStack stack, Map<TradeItem, ItemStack> prototypes) {
        TradeItem item = ForgeTradeItemFactory.fromStack(stack);
        ItemStack existing = prototypes.get(item);
        if (existing != null && !net.minecraftforge.items.ItemHandlerHelper.canItemStacksStack(existing, stack)) {
            throw new IllegalArgumentException("Item capabilities reject stacking despite equal serialized data");
        }
        prototypes.putIfAbsent(item, stack.copy());
        return item;
    }

    private static List<TradeDefinition> definitions(TileVendingMachine tile, boolean sale,
                                                     Map<TradeItem, ItemStack> prototypes) {
        List<TradeDefinition> definitions = new ArrayList<>();
        for (int slot = 0; slot < 4; slot++) {
            ItemStack stack = sale ? tile.getSaleTemplate(slot) : tile.getPaymentTemplate(slot);
            if (!stack.isEmpty()) {
                if (OreFilters.isFilter(stack)) {
                    if (sale) throw new IllegalArgumentException("A group cannot be a sale result");
                    definitions.add(OreFilters.payment(stack, tile.getPaymentQuantity(slot)));
                    continue;
                }
                definitions.add(TradeDefinition.direct(remember(stack, prototypes),
                        sale ? tile.getSaleQuantity(slot) : tile.getPaymentQuantity(slot),
                        tile.isMatchingNbt(slot + (sale ? 4 : 0))));
            }
        }
        return definitions;
    }

    static ItemStack[] materialize(InventorySnapshot snapshot, Map<TradeItem, ItemStack> prototypes) {
        ItemStack[] result = new ItemStack[snapshot.size()];
        for (int slot = 0; slot < result.length; slot++) {
            InventorySlot planned = snapshot.getSlot(slot);
            if (planned.isEmpty()) {
                result[slot] = ItemStack.EMPTY;
            } else {
                ItemStack source = prototypes.get(planned.getItem());
                if (source == null) throw new IllegalArgumentException("Missing server item prototype");
                result[slot] = source.copy();
                result[slot].setCount(planned.getCount());
                if (!planned.getItem().equals(ForgeTradeItemFactory.fromStack(result[slot]))
                        || !net.minecraftforge.items.ItemHandlerHelper.canItemStacksStack(source, result[slot])) {
                    throw new IllegalArgumentException("Item identity changed during reconstruction");
                }
            }
        }
        return result;
    }

    private static void replace(ItemStackHandler inventory, ItemStack[] stacks) {
        for (int slot = 0; slot < stacks.length; slot++) inventory.setStackInSlot(slot, stacks[slot]);
    }

    public static final class Outcome {
        public final int completed;
        public final TradeResultCode code;
        public final TradeReceipt receipt;
        public Outcome(int completed, TradeResultCode code) {
            this(completed, code, null);
        }
        public Outcome(int completed, TradeResultCode code, TradeReceipt receipt) {
            this.completed = completed;
            this.code = code;
            this.receipt = receipt;
        }
        Outcome withoutReceipt() { return receipt == null ? this : new Outcome(completed, code); }
    }
}
