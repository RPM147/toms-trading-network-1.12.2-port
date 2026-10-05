package com.tom.trading.client;

import com.tom.trading.BuildInfo;
import com.tom.trading.TradingNetworkMod;
import com.tom.trading.book.TradeBookBinding;
import com.tom.trading.book.TradeBooks;
import com.tom.trading.network.TradeBookNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.INetHandler;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.util.Collections;
import java.util.List;

/** A read-only vanilla book view; no edit/sign packets and no retained history after closing. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = BuildInfo.MOD_ID, value = Side.CLIENT)
public final class TradeBookClient {
    private final TradeBookClientState opening = new TradeBookClientState();
    private INetHandler connection;
    public void clear() { opening.clear(); connection = null; }
    public void open(EnumHand hand) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.getConnection() == null || mc.currentScreen != null) return;
        if (connection != mc.getConnection()) { clear(); connection = mc.getConnection(); }
        TradeBookBinding binding = TradeBookBinding.read(mc.player.getHeldItem(hand).getTagCompound());
        if (binding == null) { message(mc, "unavailable"); return; }
        TradeBookNetwork.Open request = opening.begin(binding, mc.player.dimension, hand, System.nanoTime());
        if (request != null) { message(mc, "loading"); TradeBookNetwork.request(request); }
    }
    public void receive(TradeBookNetwork.Opened response, INetHandler source) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getConnection() != source || connection != source || mc.player == null || mc.world == null
                    || mc.currentScreen != null || !mc.player.isEntityAlive() || mc.player.isSpectator()) return;
            ItemStack held = mc.player.getHeldItem(response.request.hand);
            if (!TradeBooks.marked(held) || !opening.accept(response, TradeBookBinding.read(held.getTagCompound()),
                    mc.player.dimension, System.nanoTime())) return;
            if (response.result != TradeBookNetwork.Result.OK) { message(mc, "unavailable"); return; }
            List<String> rows = response.rows.isEmpty() ? Collections.singletonList(I18n.format("gui.toms_trading_network.book.empty")) : response.rows;
            String buyerCaption = I18n.format("gui.toms_trading_network.book.buyer");
            String sellerCaption = I18n.format("gui.toms_trading_network.book.seller");
            List<String> pages = TradeBookPages.layout(rows, row -> mc.fontRenderer.listFormattedStringToWidth(
                    TradeBookPages.displayEntry(row, buyerCaption, sellerCaption), 116));
            ItemStack display = new ItemStack(Items.WRITTEN_BOOK); NBTTagCompound tag = new NBTTagCompound(); NBTTagList text = new NBTTagList();
            for (String page : pages) text.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(page))));
            tag.setTag("pages", text); tag.setString("title", I18n.format("gui.toms_trading_network.book.title"));
            tag.setString("author", "Tom's Trading Network"); tag.setBoolean("resolved", true); display.setTagCompound(tag);
            mc.displayGuiScreen(new GuiScreenBook(mc.player, display, false));
        });
    }
    private static void message(Minecraft mc, String key) {
        mc.player.sendStatusMessage(new TextComponentTranslation("gui.toms_trading_network.book." + key), true);
    }
    @SubscribeEvent public static void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(() -> TradingNetworkMod.proxy.clearTradeBooks());
    }
}
