package com.tom.trading.item;

import com.tom.trading.BuildInfo;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import javax.annotation.Nullable;
import java.util.List;

/** Display-only filter token, created by the definition UI; no physical crafting recipe. */
public final class TagFilterItem extends Item {
    public TagFilterItem() {
        setTranslationKey(BuildInfo.MOD_ID + ".tag_filter");
        setMaxStackSize(1);
    }

    @SideOnly(Side.CLIENT)
    @Override public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(I18n.format("tooltip.toms_trading_network.ore_filter", OreFilters.oreName(stack)));
        tooltip.add(I18n.format("tooltip.toms_trading_network.payment_only"));
    }
}
