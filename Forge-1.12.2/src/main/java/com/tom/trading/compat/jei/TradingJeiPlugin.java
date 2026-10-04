package com.tom.trading.compat.jei;

import com.tom.trading.client.GuiMachine;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Discovered by JEI only; never referenced by unconditional mod bootstrap. */
@JEIPlugin
@SideOnly(Side.CLIENT)
public final class TradingJeiPlugin implements IModPlugin {
    @Override public void register(IModRegistry registry) {
        registry.addGhostIngredientHandler(GuiMachine.class, new GhostHandler());
    }

    private static final class GhostHandler implements IGhostIngredientHandler<GuiMachine> {
        @Override public <I> List<Target<I>> getTargets(GuiMachine gui, I ingredient, boolean doStart) {
            if (!(ingredient instanceof ItemStack) || !gui.canAcceptGhost((ItemStack) ingredient)) {
                return Collections.emptyList();
            }
            List<Target<I>> result = new ArrayList<>();
            for (int slot = 0; slot < 8; slot++) {
                final int targetSlot = slot;
                result.add(new Target<I>() {
                    @Override public Rectangle getArea() { return gui.ghostArea(targetSlot); }
                    @Override public void accept(I value) {
                        if (value instanceof ItemStack) gui.acceptGhost(targetSlot, (ItemStack) value);
                    }
                });
            }
            return result;
        }
        @Override public void onComplete() {}
    }
}
