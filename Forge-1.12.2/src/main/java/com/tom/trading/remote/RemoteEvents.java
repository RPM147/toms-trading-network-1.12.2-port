package com.tom.trading.remote;

import com.tom.trading.BuildInfo;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber(modid = BuildInfo.MOD_ID)
public final class RemoteEvents {
    private RemoteEvents() {}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) RemoteTrading.serverTick();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void unload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) RemoteTrading.unloaded(event.getWorld());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) RemoteTrading.playerGone((EntityPlayerMP) event.player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) RemoteTrading.playerGone((EntityPlayerMP) event.player);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayerMP) RemoteTrading.playerGone((EntityPlayerMP) event.getEntityLiving());
    }
}
