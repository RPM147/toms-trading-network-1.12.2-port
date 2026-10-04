package com.tom.trading.item;

import com.tom.trading.Content;
import com.tom.trading.trade.ForgeTradeItemFactory;
import com.tom.trading.trade.TradeDefinition;
import com.tom.trading.trade.TradeLimits;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Validated group definitions; never creates a concrete sale result from a group. */
public final class OreFilters {
    private OreFilters() {}

    public static boolean isFilter(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof TagFilterItem;
    }

    public static boolean validName(String name) {
        return name != null && !name.isEmpty() && name.length() <= TradeLimits.MAX_ORE_NAME_LENGTH
                && name.equals(name.trim()) && name.getBytes(StandardCharsets.UTF_8).length <= 256
                && name.codePoints().noneMatch(c -> Character.isISOControl(c) || c == 167);
    }

    public static ItemStack sampleOf(ItemStack definition) {
        if (definition == null || definition.isEmpty()) return ItemStack.EMPTY;
        ItemStack sample = isFilter(definition)
                ? new ItemStack(definition.hasTagCompound()
                    ? definition.getTagCompound().getCompoundTag("Sample") : new NBTTagCompound())
                : definition.copy();
        if (sample.isEmpty() || isFilter(sample) || sample.getItem().getRegistryName() == null
                || sample.getMetadata() < 0 || sample.getMetadata() >= OreDictionary.WILDCARD_VALUE) {
            return ItemStack.EMPTY;
        }
        sample.setCount(1);
        return sample;
    }

    public static String oreName(ItemStack definition) {
        return isFilter(definition) && definition.hasTagCompound()
                ? definition.getTagCompound().getString("OreName") : "";
    }

    public static List<String> namesFor(ItemStack definition) {
        ItemStack sample = sampleOf(definition);
        if (sample.isEmpty()) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (int id : OreDictionary.getOreIDs(sample)) {
            String name = OreDictionary.getOreName(id);
            if (validName(name) && !names.contains(name)) names.add(name);
        }
        Collections.sort(names);
        return names;
    }

    public static ItemStack create(ItemStack sample, String name) {
        ItemStack normalized = sampleOf(sample);
        if (!validName(name) || normalized.isEmpty() || !namesFor(normalized).contains(name)) {
            throw new IllegalArgumentException("Ore name is not associated with the server sample");
        }
        ItemStack filter = new ItemStack(Content.TAG_FILTER);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("OreName", name);
        tag.setTag("Sample", normalized.serializeNBT());
        filter.setTagCompound(tag);
        return filter;
    }

    public static boolean isValid(ItemStack filter) {
        return isFilter(filter) && validName(oreName(filter)) && namesFor(filter).contains(oreName(filter));
    }

    public static TradeDefinition payment(ItemStack filter, int quantity) {
        if (!isValid(filter)) throw new IllegalArgumentException("Invalid Ore Dictionary filter");
        return TradeDefinition.orePayment(ForgeTradeItemFactory.fromStack(sampleOf(filter)), oreName(filter), quantity);
    }
}
