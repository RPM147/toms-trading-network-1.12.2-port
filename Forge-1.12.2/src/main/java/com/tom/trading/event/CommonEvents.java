package com.tom.trading.event;

import com.tom.trading.BuildInfo;
import com.tom.trading.tile.TileVendingMachine;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = BuildInfo.MOD_ID)
public final class CommonEvents {
    private CommonEvents() {
    }

    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = false)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getWorld().isRemote) {
            return;
        }

        TileEntity tileEntity = event.getWorld().getTileEntity(event.getPos());
        if (tileEntity instanceof TileVendingMachine) {
            TileVendingMachine machine = (TileVendingMachine) tileEntity;
            if (machine.isAutomationQuarantined()) {
                event.setCanceled(true);
                event.getPlayer().sendStatusMessage(new TextComponentTranslation(
                        "gui.toms_trading_network.automation_quarantined"), false);
            } else if (machine.isAutomationRunning() || !machine.canConfigure(event.getPlayer())) {
                event.setCanceled(true);
            }
        }
    }
}
