package com.tom.trading.item;

import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import javax.annotation.Nullable;
import java.util.List;

public final class ItemVendingMachine extends ItemBlock {
    public ItemVendingMachine(Block block) { super(block); }

    @SideOnly(Side.CLIENT)
    @Override public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        if (GuiScreen.isShiftKeyDown()) {
            tooltip.add(I18n.format("tooltip.toms_trading_network.machine_owner"));
            tooltip.add(I18n.format("tooltip.toms_trading_network.machine_automation"));
        } else tooltip.add(I18n.format("tooltip.toms_trading_network.hold_shift"));
    }
}
