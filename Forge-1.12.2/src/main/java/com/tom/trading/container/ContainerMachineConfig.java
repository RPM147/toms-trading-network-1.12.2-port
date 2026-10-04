package com.tom.trading.container;

import com.tom.trading.tile.TileVendingMachine;
import com.tom.trading.compat.StackLimits;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

public final class ContainerMachineConfig extends ContainerMachine {
    public ContainerMachineConfig(EntityPlayer player, TileVendingMachine tile) {
        super(player, tile, true);
        for (int i = 0; i < 8; i++) {
            addSlotToContainer(new SlotItemHandler(tile.getSaleStock(), i, 8 + i % 4 * 18, 82 + i / 4 * 18) {
                @Override public boolean isItemValid(ItemStack stack) {
                    return canInteractWith(player) && super.isItemValid(stack);
                }
                @Override public boolean canTakeStack(EntityPlayer viewer) {
                    return canInteractWith(viewer) && super.canTakeStack(viewer);
                }
                @Override public void onSlotChanged() { tile.markDirty(); }
                @Override public int getItemStackLimit(ItemStack stack) {
                    return StackLimits.forItem(stack);
                }
            });
        }
        for (int i = 0; i < 8; i++) {
            addSlotToContainer(new SlotItemHandler(tile.getEarnings(), i, 98 + i % 4 * 18, 82 + i / 4 * 18) {
                @Override public boolean isItemValid(ItemStack stack) { return false; }
                @Override public boolean canTakeStack(EntityPlayer viewer) {
                    return canInteractWith(viewer) && super.canTakeStack(viewer);
                }
                @Override public void onSlotChanged() { tile.markDirty(); }
                @Override public int getItemStackLimit(ItemStack stack) {
                    return StackLimits.forItem(stack);
                }
            });
        }
        addPlayerSlots(129, 187);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (!canInteractWith(player) || index < 0 || index >= inventorySlots.size()) return ItemStack.EMPTY;
        Slot slot = inventorySlots.get(index);
        if (!slot.getHasStack() || !slot.canTakeStack(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if (index < 16) moveStack(stack, 16, 52, true);
        else moveStack(stack, 0, 8, false);
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY);
        else slot.onSlotChanged();
        slot.onTake(player, stack);
        tile.markDirty();
        return original;
    }
}
