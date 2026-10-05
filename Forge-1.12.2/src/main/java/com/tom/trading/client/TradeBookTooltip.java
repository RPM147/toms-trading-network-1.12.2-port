package com.tom.trading.client;

import com.tom.trading.BuildInfo;
import com.tom.trading.book.TradeBooks;
import com.tom.trading.directory.DirectoryText;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = BuildInfo.MOD_ID, value = Side.CLIENT)
public final class TradeBookTooltip {
    private TradeBookTooltip() {}
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        if (!TradeBooks.marked(event.getItemStack())) return;
        NBTTagCompound tag = event.getItemStack().getTagCompound();
        String name = DirectoryText.label(tag.getString("TTNBookMachineName"));
        if (name.isEmpty()) name = I18n.format("tile.toms_trading_network.vending_machine.name");
        event.getToolTip().add(TextFormatting.GOLD + I18n.format("gui.toms_trading_network.book.tooltip", name));
        event.getToolTip().add(TextFormatting.GRAY + I18n.format("gui.toms_trading_network.directory.owner", DirectoryText.label(tag.getString("TTNBookOwner"))));
        event.getToolTip().add(TextFormatting.GRAY + DirectoryText.label(tag.getString("TTNBookAddress")));
    }
}
