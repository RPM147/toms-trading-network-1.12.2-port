package com.tom.trading.trade;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class TradeExecutorAdapterTest {
    @BeforeClass
    public static void bootstrap() { Bootstrap.register(); }

    @Test
    public void capabilityPayloadParticipatesInIdentityAndStrictMatching() {
        NBTTagCompound firstCaps = new NBTTagCompound();
        firstCaps.setInteger("energy", 400);
        NBTTagCompound secondCaps = new NBTTagCompound();
        secondCaps.setInteger("energy", 900);
        TradeItem first = new TradeItem("test:battery", 0, null, firstCaps, Collections.emptySet(), 64);
        TradeItem second = new TradeItem("test:battery", 0, null, secondCaps, Collections.emptySet(), 64);
        assertNotEquals(first, second);
        assertFalse(ItemMatcher.canMerge(first, second));
        assertFalse(ItemMatcher.matches(first, TradeDefinition.direct(second, 1, true)));
        firstCaps.setInteger("energy", 900);
        assertFalse(ItemMatcher.canMerge(first, second)); // Immutable copy.
    }

    @Test
    public void replacementsKeepNestedNbtAndDoNotMutateLivePrototypes() {
        ItemStack prototype = new ItemStack(Items.DIAMOND, 12);
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound nested = new NBTTagCompound();
        nested.setString("owner", "preserve");
        root.setTag("custom", nested);
        prototype.setTagCompound(root);
        TradeItem identity = ForgeTradeItemFactory.fromStack(prototype);
        Map<TradeItem, ItemStack> prototypes = new HashMap<>();
        prototypes.put(identity, prototype);
        ItemStack[] replacements = TradeExecutor.materialize(
                InventorySnapshot.of(InventorySlot.occupied(identity, 3, 64), InventorySlot.empty(64)), prototypes);
        assertEquals(3, replacements[0].getCount());
        assertEquals("preserve", replacements[0].getTagCompound().getCompoundTag("custom").getString("owner"));
        assertEquals(12, prototype.getCount());
        assertTrue(replacements[1].isEmpty());
        replacements[0].getTagCompound().getCompoundTag("custom").setString("owner", "changed");
        assertEquals("preserve", prototype.getTagCompound().getCompoundTag("custom").getString("owner"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingPrototypeFailsBeforeAnyCommit() {
        TradeItem identity = ForgeTradeItemFactory.fromStack(new ItemStack(Items.DIAMOND));
        TradeExecutor.materialize(InventorySnapshot.of(InventorySlot.occupied(identity, 1, 64)), new HashMap<>());
    }
}
