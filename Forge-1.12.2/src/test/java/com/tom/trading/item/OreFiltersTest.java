package com.tom.trading.item;

import com.tom.trading.Content;
import com.tom.trading.network.MachineNetwork;
import com.tom.trading.tile.TileVendingMachine;
import com.tom.trading.trade.*;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class OreFiltersTest {
    private static Item variant;
    private static final String GROUP = "ttnPhase7Group";

    @BeforeClass public static void bootstrap() {
        Bootstrap.register();
        ForgeRegistries.ITEMS.register(Content.TAG_FILTER);
        variant = new Item().setHasSubtypes(true).setRegistryName(new ResourceLocation("ttn_test", "variant"));
        ForgeRegistries.ITEMS.register(variant);
        OreDictionary.registerOre(GROUP, Items.IRON_INGOT);
        OreDictionary.registerOre(GROUP, new ItemStack(variant, 1, OreDictionary.WILDCARD_VALUE));
        OreDictionary.registerOre("ttnPhase7Second", Items.IRON_INGOT);
    }

    @Test public void sampleMembershipWildcardMetadataAndPrototypeNbtRoundTrip() {
        ItemStack sample = new ItemStack(Items.IRON_INGOT, 32);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("quality", "original"); sample.setTagCompound(tag);
        ItemStack filter = OreFilters.create(sample, GROUP);
        sample.getTagCompound().setString("quality", "changed");
        ItemStack restored = new ItemStack(filter.serializeNBT());
        assertTrue(OreFilters.isValid(restored));
        assertEquals(1, OreFilters.sampleOf(restored).getCount());
        assertEquals("original", OreFilters.sampleOf(restored).getTagCompound().getString("quality"));
        assertTrue(OreFilters.namesFor(sample).contains("ttnPhase7Second"));
        assertTrue(ItemMatcher.matches(ForgeTradeItemFactory.fromStack(new ItemStack(variant, 1, 7)),
                OreFilters.payment(restored, 1024)));
        assertFalse(ItemMatcher.matches(ForgeTradeItemFactory.fromStack(new ItemStack(Items.DIAMOND)),
                OreFilters.payment(restored, 1)));
    }

    @Test public void unknownIncompatibleAndForgedNamesDoNotRegisterOrBecomeSales() {
        int namesBefore = OreDictionary.getOreNames().length;
        for (String name : new String[] {"", "ttnNeverRegistered", " " + GROUP, "bad\nname",
                String.join("", Collections.nCopies(129, "x"))}) {
            try { OreFilters.create(new ItemStack(Items.IRON_INGOT), name); fail(name); }
            catch (IllegalArgumentException expected) { /* Reject before registry mutation. */ }
        }
        try { OreFilters.create(new ItemStack(Items.DIAMOND), GROUP); fail("Incompatible sample"); }
        catch (IllegalArgumentException expected) { /* Expected. */ }
        assertEquals(namesBefore, OreDictionary.getOreNames().length);
        ItemStack filter = OreFilters.create(new ItemStack(Items.IRON_INGOT), GROUP);
        TileVendingMachine tile = new TileVendingMachine();
        tile.setPaymentDefinition(0, filter, 128);
        tile.setSaleDefinition(0, new ItemStack(Items.DIAMOND), 1);
        tile.setSaleDefinition(0, filter, 1);
        assertEquals(Items.DIAMOND, tile.getSaleTemplate(0).getItem());
        assertFalse(tile.acceptsAutomationItem(filter));
        filter.getTagCompound().setString("OreName", "ttnNeverRegistered");
        tile.setPaymentDefinition(0, filter, 256);
        assertEquals(128, tile.getPaymentQuantity(0));
        assertFalse(OreFilters.isValid(filter));
    }

    @Test public void mixedGroupPaymentsKeepActualItemsAndNbtInEarnings() {
        ItemStack special = new ItemStack(variant, 2, 7);
        NBTTagCompound nbt = new NBTTagCompound(); nbt.setString("serial", "keep"); special.setTagCompound(nbt);
        TradeItem iron = ForgeTradeItemFactory.fromStack(new ItemStack(Items.IRON_INGOT));
        TradeItem actual = ForgeTradeItemFactory.fromStack(special);
        TradeItem diamond = ForgeTradeItemFactory.fromStack(new ItemStack(Items.DIAMOND));
        TradeOffer offer = new TradeOffer(Collections.singletonList(OreFilters.payment(
                OreFilters.create(new ItemStack(Items.IRON_INGOT), GROUP), 4)),
                Collections.singletonList(TradeDefinition.direct(diamond, 1, true)));
        TradePlan plan = TradePlanner.plan(offer, TradePolicy.NORMAL, 1,
                InventorySnapshot.of(InventorySlot.occupied(iron, 2, 64), InventorySlot.occupied(actual, 2, 64)),
                InventorySnapshot.of(InventorySlot.occupied(diamond, 1, 64)),
                InventorySnapshot.of(InventorySlot.empty(64), InventorySlot.empty(64)));
        assertEquals(1, plan.getCompletedCount());
        assertEquals(iron, plan.getEarningsAfter().getSlot(0).getItem());
        assertEquals(actual, plan.getEarningsAfter().getSlot(1).getItem());
        assertEquals(2, plan.getEarningsAfter().getSlot(1).getCount());
    }

    @Test public void ghostResolutionUsesRegistryNamesAndCannotSupplyFilterNbt() {
        assertEquals(Items.IRON_INGOT, MachineNetwork.resolveGhost("minecraft:iron_ingot", 0).getItem());
        assertTrue(MachineNetwork.resolveGhost("ttn_test:missing", 0).isEmpty());
        assertTrue(MachineNetwork.resolveGhost("toms_trading_network:tag_filter", 0).isEmpty());
        assertTrue(MachineNetwork.resolveGhost("minecraft:iron_ingot", 32767).isEmpty());
        assertFalse(MachineNetwork.resolveGhost("minecraft:iron_ingot", 0).hasTagCompound());
    }

    @Test public void ghostMetadataCannotPropagateAnOptionalItemsConstructorFailure() {
        Item fragile = new Item() {
            @Override public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(
                    ItemStack stack, NBTTagCompound nbt) {
                if (stack.getMetadata() == 7) throw new IndexOutOfBoundsException("Unsupported subtype");
                return null;
            }
        }.setHasSubtypes(true).setRegistryName("ttn_test", "fragile_subtype");
        ForgeRegistries.ITEMS.register(fragile);
        assertEquals(fragile, MachineNetwork.resolveGhost("ttn_test:fragile_subtype", 0).getItem());
        assertTrue(MachineNetwork.resolveGhost("ttn_test:fragile_subtype", 7).isEmpty());
    }
}
