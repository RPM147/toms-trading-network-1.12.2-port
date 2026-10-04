package com.tom.trading.automation;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Never exposes IItemHandlerModifiable; cached handles re-evaluate permissions. */
public final class SidedMachineHandler implements IItemHandler {
    private final IItemHandler stock, earnings;
    private final BooleanSupplier input, output, available;
    private final Predicate<ItemStack> accepts;

    public SidedMachineHandler(IItemHandler stock, IItemHandler earnings,
                               BooleanSupplier input, BooleanSupplier output,
                               BooleanSupplier available, Predicate<ItemStack> accepts) {
        this.stock = stock;
        this.earnings = earnings;
        this.input = input;
        this.output = output;
        this.available = available;
        this.accepts = accepts;
    }

    @Override public int getSlots() { return stock.getSlots() + earnings.getSlots(); }

    @Override public ItemStack getStackInSlot(int slot) {
        if (!valid(slot) || !available.getAsBoolean()) return ItemStack.EMPTY;
        if (slot < stock.getSlots()) {
            return input.getAsBoolean() ? stock.getStackInSlot(slot).copy() : ItemStack.EMPTY;
        }
        return output.getAsBoolean() ? earnings.getStackInSlot(slot - stock.getSlots()).copy() : ItemStack.EMPTY;
    }

    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!isItemValid(slot, stack)) return stack;
        // Forge's base handler may retain its argument when an empty slot accepts all.
        return stock.insertItem(slot, stack.copy(), simulate).copy();
    }

    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || !valid(slot) || slot < stock.getSlots()
                || !available.getAsBoolean() || !output.getAsBoolean()) return ItemStack.EMPTY;
        return earnings.extractItem(slot - stock.getSlots(), amount, simulate).copy();
    }

    @Override public int getSlotLimit(int slot) {
        if (!valid(slot) || !available.getAsBoolean()) return 0;
        return slot < stock.getSlots() ? (input.getAsBoolean() ? stock.getSlotLimit(slot) : 0)
                : (output.getAsBoolean() ? earnings.getSlotLimit(slot - stock.getSlots()) : 0);
    }

    @Override public boolean isItemValid(int slot, ItemStack stack) {
        return valid(slot) && slot < stock.getSlots() && !stack.isEmpty()
                && available.getAsBoolean() && input.getAsBoolean() && accepts.test(stack);
    }

    private boolean valid(int slot) { return slot >= 0 && slot < getSlots(); }
}
