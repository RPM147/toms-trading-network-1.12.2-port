package com.tom.trading.compat;

import com.tom.trading.BuildInfo;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;

/** Optional discovery only: no StackUp linkage, config writes, or item mutations. */
public final class StackLimits {
    public static final int PORT_MAXIMUM = 1024;
    private static int slotMaximum = 64;

    private StackLimits() {}

    public static void discover() {
        slotMaximum = 64;
        if (!Loader.isModLoaded("stackup")) return;
        try {
            Class<?> stackUp = Class.forName("pl.asie.stackup.StackUp");
            Object value = stackUp.getMethod("getConfig").invoke(null);
            if (!(value instanceof Configuration)) throw new IllegalStateException("Missing StackUp config");
            Configuration config = (Configuration) value;
            if (!config.hasKey("general", "maxStackSize")) {
                throw new IllegalStateException("Missing StackUp maximum");
            }
            slotMaximum = boundedMaximum(config.getCategory("general").get("maxStackSize").getInt());
            LogManager.getLogger(BuildInfo.MOD_ID).info("Machine slot maximum: {} (StackUp)", slotMaximum);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            LogManager.getLogger(BuildInfo.MOD_ID).warn("StackUp discovery failed; machine slots use vanilla limits", error);
        }
    }

    static int boundedMaximum(int configured) {
        return configured < 1 ? 64 : Math.min(PORT_MAXIMUM, configured);
    }

    public static int slotMaximum() { return slotMaximum; }

    public static int forItem(ItemStack stack) {
        return stack.isEmpty() ? 0 : Math.max(0, Math.min(slotMaximum, stack.getMaxStackSize()));
    }
}
