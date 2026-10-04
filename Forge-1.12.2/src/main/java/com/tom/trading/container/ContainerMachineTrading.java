package com.tom.trading.container;

import com.tom.trading.tile.TileVendingMachine;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public final class ContainerMachineTrading extends ContainerMachine {
    public ContainerMachineTrading(EntityPlayer player, TileVendingMachine tile) {
        super(player, tile, false);
        addPlayerSlots(84, 142);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (!canInteractWith(player) || index < 0 || index >= 36) return ItemStack.EMPTY;
        Slot slot = inventorySlots.get(index);
        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack original = stack.copy();
        if (index < 27) moveStack(stack, 27, 36, false);
        else moveStack(stack, 0, 27, false);
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY);
        else slot.onSlotChanged();
        slot.onTake(player, stack);
        return original;
    }
}
