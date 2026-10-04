package com.tom.trading.automation;

import com.tom.trading.compat.StackLimits;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** One bounded transfer at a time. Only the server thread may use this object. */
public final class AutomationTransfer {
    private final IItemHandler stock, earnings;
    private final Predicate<ItemStack> accepts;
    private final Runnable changed;
    private ItemStack pending = ItemStack.EMPTY;
    private boolean returnToEarnings;
    private boolean quarantined;

    public AutomationTransfer(IItemHandler stock, IItemHandler earnings,
                              Predicate<ItemStack> accepts, Runnable changed) {
        this.stock = stock;
        this.earnings = earnings;
        this.accepts = accepts;
        this.changed = changed;
    }

    public boolean isQuarantined() { return quarantined; }
    public boolean hasPending() { return !pending.isEmpty(); }

    public boolean pull(IItemHandler source, int slot, BooleanSupplier stillValid) {
        if (quarantined || hasPending() || !stillValid.getAsBoolean()) return false;
        int request = StackLimits.slotMaximum();
        ItemStack offered = source.extractItem(slot, request, true).copy();
        if (offered.isEmpty() || offered.getCount() > request || !accepts.test(offered)) return false;
        int fitting = offered.getCount() - insertLocal(stock, offered, true).getCount();
        if (fitting <= 0 || !stillValid.getAsBoolean()) return false;
        ItemStack extracted;
        try {
            extracted = source.extractItem(slot, fitting, false);
            if (extracted == null) throw new IllegalStateException("Null extraction result");
        } catch (RuntimeException error) {
            quarantine(); // An exception does not tell us whether the neighbor already removed items.
            throw error;
        }
        pending = extracted.copy();
        returnToEarnings = false;
        changed.run();
        if (pending.getCount() > fitting) {
            quarantine();
            throw new IllegalStateException("Neighbor extracted more than requested");
        }
        // A changed/refusing source may return fewer items or a different item. Never discard it.
        boolean moved = hasPending();
        drainPending();
        return moved;
    }

    public boolean push(IItemHandler destination, int destinationSlot, int earningsSlot,
                        BooleanSupplier stillValid) {
        if (quarantined || hasPending() || !stillValid.getAsBoolean()) return false;
        ItemStack offered = earnings.extractItem(earningsSlot, StackLimits.slotMaximum(), true).copy();
        if (offered.isEmpty()) return false;
        ItemStack simulated = destination.insertItem(destinationSlot, offered.copy(), true);
        validateRemainder(offered, simulated);
        int fitting = offered.getCount() - simulated.getCount();
        if (fitting <= 0 || !stillValid.getAsBoolean()) return false;
        // Remove from the source before committing externally; reentrant handles are locked by the tile.
        pending = earnings.extractItem(earningsSlot, fitting, false).copy();
        returnToEarnings = true;
        changed.run();
        if (pending.isEmpty()) return false;
        if (!stillValid.getAsBoolean()) {
            drainPending();
            return false;
        }
        int sent = pending.getCount();
        try {
            ItemStack remainder = destination.insertItem(destinationSlot, pending.copy(), false);
            validateRemainder(pending, remainder);
            pending = remainder.copy();
            changed.run();
        } catch (RuntimeException error) {
            // Forge has no rollback/acknowledgement API. Do not retry or drop an uncertain transfer.
            quarantine();
            throw error;
        }
        boolean moved = pending.getCount() < sent;
        drainPending();
        return moved;
    }

    public void drainPending() {
        if (quarantined || pending.isEmpty()) return;
        if (!returnToEarnings && !accepts.test(pending)) return;
        ItemStack remainder = insertLocal(returnToEarnings ? earnings : stock, pending, false);
        if (remainder.getCount() != pending.getCount()) {
            pending = remainder;
            changed.run();
        }
    }

    /** Known-owned leftovers are real items; templates never enter this buffer. */
    public ItemStack takeForDrop() {
        if (quarantined) return ItemStack.EMPTY;
        ItemStack result = pending.copy();
        pending = ItemStack.EMPTY;
        changed.run();
        return result;
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("ReturnToEarnings", returnToEarnings);
        tag.setBoolean("Quarantined", quarantined);
        if (!pending.isEmpty()) {
            ItemStack prototype = pending.copy();
            prototype.setCount(1);
            tag.setTag("Item", prototype.serializeNBT());
            tag.setInteger("CountInt", pending.getCount());
        }
        return tag;
    }

    public void read(NBTTagCompound tag) {
        pending = new ItemStack(tag.getCompoundTag("Item"));
        pending.setCount(Math.max(0, tag.getInteger("CountInt")));
        returnToEarnings = tag.getBoolean("ReturnToEarnings");
        quarantined = tag.getBoolean("Quarantined") || pending.getCount() > StackLimits.PORT_MAXIMUM;
    }

    private void quarantine() {
        quarantined = true;
        changed.run();
    }

    private static ItemStack insertLocal(IItemHandler inventory, ItemStack stack, boolean simulate) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < inventory.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = inventory.insertItem(slot, remainder, simulate);
        }
        return remainder;
    }

    private static void validateRemainder(ItemStack sent, ItemStack remainder) {
        if (remainder == null || (!remainder.isEmpty()
                && (!ItemHandlerHelper.canItemStacksStack(sent, remainder) || remainder.getCount() > sent.getCount()))) {
            throw new IllegalStateException("Neighbor violated the IItemHandler remainder contract");
        }
    }
}
