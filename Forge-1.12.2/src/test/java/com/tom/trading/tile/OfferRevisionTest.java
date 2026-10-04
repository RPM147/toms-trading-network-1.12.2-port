package com.tom.trading.tile;

import com.tom.trading.container.MachineView;
import com.tom.trading.item.OreFilters;
import com.tom.trading.trade.TradeExecutor;
import com.tom.trading.trade.TradeResultCode;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class OfferRevisionTest {
    @BeforeClass public static void bootstrap() { Bootstrap.register(); }

    @Test public void economicEditsInvalidateButStockAndDisplayChangesDoNot() {
        TileVendingMachine tile = machine();
        long quote = tile.getOfferRevision();
        assertEquals(quote, MachineView.read(MachineView.capture(tile)).offerRevision);
        tile.getSaleStock().setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
        tile.getEarnings().setStackInSlot(0, new ItemStack(Items.EMERALD, 9));
        tile.setCustomName("Shop");
        tile.setSideMasks(1, 2, 0);
        tile.setPaymentDefinition(0, new ItemStack(Items.EMERALD, 32), 1); // Same count-1 template.
        tile.setMatchingNbt(0, true);
        tile.setCreativeMode(false);
        assertEquals(quote, tile.getOfferRevision());
        tile.setPaymentDefinition(0, new ItemStack(Items.EMERALD), 64);
        assertFalse(tile.matchesOfferRevision(quote));
        quote = tile.getOfferRevision();
        tile.setMatchingNbt(4, false);
        assertTrue(tile.getOfferRevision() > quote);
        quote = tile.getOfferRevision();
        tile.setCreativeMode(true);
        assertTrue(tile.getOfferRevision() > quote);
        quote = tile.getOfferRevision();
        tile.setSaleDefinition(0, ItemStack.EMPTY, 0);
        assertTrue(tile.getOfferRevision() > quote);
    }

    @Test public void staleQuoteIsRejectedBeforeAccessingPlayerOrAnyInventory() {
        TileVendingMachine tile = machine();
        long shown = tile.getOfferRevision();
        tile.setPaymentDefinition(0, new ItemStack(Items.EMERALD), 64);
        NBTTagCompound before = tile.writePortData(new NBTTagCompound());
        // Null player is intentional: the stale-quote guard must run before any player capture.
        TradeExecutor.Outcome outcome = TradeExecutor.execute(null, tile, 1, shown);
        assertEquals(0, outcome.completed);
        assertEquals(TradeResultCode.OFFER_CHANGED, outcome.code);
        assertEquals(before, tile.writePortData(new NBTTagCompound()));
        assertFalse(tile.matchesOfferRevision(0));
        assertFalse(tile.matchesOfferRevision(-1));
        assertTrue(tile.matchesOfferRevision(tile.getOfferRevision()));
    }

    @Test public void nbtFilterAndReloadAlsoInvalidateWithoutChangingTheSaveSchema() {
        TileVendingMachine tile = machine();
        long quote = tile.getOfferRevision();
        ItemStack named = new ItemStack(Items.DIAMOND);
        named.setStackDisplayName("Variant");
        tile.setSaleDefinition(0, named, 1);
        assertTrue(tile.getOfferRevision() > quote);
        OreDictionary.registerOre("ttnAuditPayment", Items.EMERALD);
        quote = tile.getOfferRevision();
        tile.setPaymentDefinition(0, OreFilters.create(new ItemStack(Items.EMERALD), "ttnAuditPayment"), 1);
        assertTrue(tile.getOfferRevision() > quote);
        NBTTagCompound saved = tile.writePortData(new NBTTagCompound());
        assertEquals(TileVendingMachine.DATA_VERSION, saved.getInteger("DataVersion"));
        assertFalse(saved.hasKey("OfferRevision"));
        quote = tile.getOfferRevision();
        tile.readPortData(saved);
        assertTrue(tile.getOfferRevision() > quote);
    }

    private static TileVendingMachine machine() {
        TileVendingMachine tile = new TileVendingMachine();
        tile.setPaymentDefinition(0, new ItemStack(Items.EMERALD), 1);
        tile.setSaleDefinition(0, new ItemStack(Items.DIAMOND), 1);
        return tile;
    }
}
