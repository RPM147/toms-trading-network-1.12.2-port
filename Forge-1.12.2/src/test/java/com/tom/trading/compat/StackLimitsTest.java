package com.tom.trading.compat;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class StackLimitsTest {
    @BeforeClass public static void bootstrap() { Bootstrap.register(); }

    @Test public void optionalMaximumIsCappedAndInvalidValuesFallBack() {
        assertEquals(64, StackLimits.boundedMaximum(0));
        assertEquals(64, StackLimits.boundedMaximum(-100));
        assertEquals(64, StackLimits.boundedMaximum(64));
        assertEquals(1024, StackLimits.boundedMaximum(1024));
        assertEquals(1024, StackLimits.boundedMaximum(Integer.MAX_VALUE));
    }

    @Test public void vanillaFallbackStillRespectsPerItemLimits() {
        assertEquals(64, StackLimits.slotMaximum());
        assertEquals(64, StackLimits.forItem(new ItemStack(Items.DIAMOND)));
        assertEquals(16, StackLimits.forItem(new ItemStack(Items.ENDER_PEARL)));
        assertEquals(1, StackLimits.forItem(new ItemStack(Items.DIAMOND_SWORD)));
    }
}
