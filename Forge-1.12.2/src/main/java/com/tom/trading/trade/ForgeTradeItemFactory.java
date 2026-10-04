package com.tom.trading.trade;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

import java.util.LinkedHashSet;
import java.util.Set;

/** Converts a live 1.12.2 stack into the immutable planner identity. */
public final class ForgeTradeItemFactory {
    private ForgeTradeItemFactory() {
    }

    public static TradeItem fromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("A non-empty ItemStack is required");
        }
        ResourceLocation registryName = stack.getItem().getRegistryName();
        if (registryName == null) {
            throw new IllegalArgumentException("ItemStack item has no registry name");
        }

        Set<String> oreNames = new LinkedHashSet<>();
        for (int oreId : OreDictionary.getOreIDs(stack)) {
            String oreName = OreDictionary.getOreName(oreId);
            if (oreName != null && !oreName.isEmpty()) {
                oreNames.add(oreName);
            }
        }

        return new TradeItem(
                registryName.toString(),
                stack.getMetadata(),
                stack.getTagCompound(),
                stack.serializeNBT().getCompoundTag("ForgeCaps"),
                oreNames,
                stack.getMaxStackSize()
        );
    }
}
