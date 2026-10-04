package com.tom.trading.client;

import com.tom.trading.BuildInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = BuildInfo.MOD_ID, value = Side.CLIENT)
public final class DirectoryKeybind {
    private static final KeyBinding OPEN = new KeyBinding("key.toms_trading_network.directory",
            KeyConflictContext.IN_GAME, Keyboard.KEY_G, "key.categories.toms_trading_network");
    private static boolean registered;
    private DirectoryKeybind() {}
    public static void register() { if (!registered) { ClientRegistry.registerKeyBinding(OPEN); registered = true; } }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean pressed = false;
        while (OPEN.isPressed()) pressed = true; // Drain repeats even when a GUI blocks opening.
        if (!pressed) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world != null && mc.player != null && mc.getConnection() != null && mc.currentScreen == null
                && mc.player.isEntityAlive() && !mc.player.isSpectator()) mc.displayGuiScreen(new GuiMachineDirectory());
    }
}
