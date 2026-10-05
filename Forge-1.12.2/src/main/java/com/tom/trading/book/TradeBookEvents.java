package com.tom.trading.book;

import com.tom.trading.BuildInfo;
import com.tom.trading.TradingNetworkMod;
import com.tom.trading.block.BlockVendingMachine;
import com.tom.trading.network.TradeBookNetwork;
import com.tom.trading.tile.TileVendingMachine;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = BuildInfo.MOD_ID)
public final class TradeBookEvents {
    private TradeBookEvents() {}
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void bind(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!event.getEntityPlayer().isSneaking() || held.isEmpty() || held.getItem() != Items.WRITABLE_BOOK
                || event.getUseBlock() == net.minecraftforge.fml.common.eventhandler.Event.Result.DENY
                || event.getUseItem() == net.minecraftforge.fml.common.eventhandler.Event.Result.DENY
                || !(event.getWorld().getBlockState(event.getPos()).getBlock() instanceof BlockVendingMachine)) return;
        // Forge forwards a canceled client block interaction to the server; suppress the ordinary machine/book GUI.
        event.setCanceled(true); event.setCancellationResult(EnumActionResult.SUCCESS);
        if (event.getWorld().isRemote || !(event.getEntityPlayer() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        if (!TradeBookNetwork.accept(player)) return;
        TileEntity tile = event.getWorld().getTileEntity(event.getPos());
        if (tile instanceof TileVendingMachine) TradeBooks.bind(player, (TileVendingMachine) tile, event.getHand());
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void open(PlayerInteractEvent.RightClickItem event) {
        if (!TradeBooks.marked(event.getItemStack())) return;
        event.setCanceled(true); event.setCancellationResult(EnumActionResult.SUCCESS);
        if (event.getWorld().isRemote) TradingNetworkMod.proxy.openTradeBook(event.getHand());
        // Server reading is handled by the correlated packet and checks the actual held stack again.
    }
}
