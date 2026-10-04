package com.tom.trading.container;

import com.tom.trading.tile.TileVendingMachine;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Public/configuration display state. Never contains the real machine inventories. */
public final class MachineView {
    public final ItemStack[] templates = new ItemStack[8];
    public final int[] quantities = new int[8];
    public String name = "";
    public String owner = "";
    public boolean creative;
    public long offerRevision;
    public int inputs, outputs, automatic, matchNbt = 255;

    public MachineView() {
        java.util.Arrays.fill(templates, ItemStack.EMPTY);
    }

    public static NBTTagCompound capture(TileVendingMachine tile) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("OfferRevision", tile.getOfferRevision());
        tag.setString("Name", tile.hasCustomName() ? tile.getName() : "");
        tag.setString("Owner", tile.getOwnerNameCache() == null ? "" : tile.getOwnerNameCache());
        tag.setBoolean("Creative", tile.isCreativeMode());
        tag.setInteger("Inputs", tile.getInputSides());
        tag.setInteger("Outputs", tile.getOutputSides());
        tag.setInteger("Automatic", tile.getAutoSides());
        tag.setInteger("MatchNBT", tile.getMatchNbtMask());
        int[] quantities = new int[8];
        NBTTagList templates = new NBTTagList();
        for (int slot = 0; slot < 8; slot++) {
            ItemStack stack = slot < 4 ? tile.getPaymentTemplate(slot) : tile.getSaleTemplate(slot - 4);
            quantities[slot] = slot < 4 ? tile.getPaymentQuantity(slot) : tile.getSaleQuantity(slot - 4);
            NBTTagCompound entry = new NBTTagCompound();
            if (!stack.isEmpty()) stack.writeToNBT(entry);
            templates.appendTag(entry);
        }
        tag.setTag("Templates", templates);
        tag.setIntArray("Quantities", quantities);
        return tag;
    }

    public static MachineView read(NBTTagCompound tag) {
        MachineView result = new MachineView();
        result.offerRevision = tag.getLong("OfferRevision");
        result.name = tag.getString("Name");
        result.owner = tag.getString("Owner");
        result.creative = tag.getBoolean("Creative");
        result.inputs = tag.getInteger("Inputs") & 63;
        result.outputs = tag.getInteger("Outputs") & 63;
        result.automatic = tag.getInteger("Automatic") & 63;
        result.matchNbt = tag.getInteger("MatchNBT") & 255;
        NBTTagList templates = tag.getTagList("Templates", 10);
        int[] quantities = tag.getIntArray("Quantities");
        for (int slot = 0; slot < 8; slot++) {
            if (slot < templates.tagCount()) result.templates[slot] = new ItemStack(templates.getCompoundTagAt(slot));
            if (!result.templates[slot].isEmpty()) {
                result.templates[slot].setCount(1);
                result.quantities[slot] = slot < quantities.length ? Math.max(1, Math.min(1024, quantities[slot])) : 1;
            }
        }
        return result;
    }
}
