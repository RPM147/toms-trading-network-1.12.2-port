package com.tom.trading.automation;

import com.tom.trading.tile.TileVendingMachine;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class SidedMachineHandlerTest {
    @BeforeClass public static void bootstrap() { Bootstrap.register(); }

    private static SidedMachineHandler handler(TileVendingMachine tile, boolean[] flags) {
        return new SidedMachineHandler(tile.getSaleStock(), tile.getEarnings(),
                () -> flags[0], () -> flags[1], () -> flags[2], tile::acceptsAutomationItem);
    }

    @Test public void onlyMatchingStockCanEnterAndOnlyEarningsCanLeave() {
        TileVendingMachine tile = new TileVendingMachine();
        tile.setSaleDefinition(0, new ItemStack(Items.DIAMOND), 1);
        SidedMachineHandler handler = handler(tile, new boolean[] {true, true, true});
        assertFalse((Object) handler instanceof IItemHandlerModifiable);
        assertEquals(16, handler.getSlots());
        assertEquals(10, handler.insertItem(0, new ItemStack(Items.EMERALD, 10), false).getCount());
        assertEquals(10, handler.insertItem(8, new ItemStack(Items.DIAMOND, 10), false).getCount());
        assertTrue(handler.insertItem(0, new ItemStack(Items.DIAMOND, 10), true).isEmpty());
        assertTrue(tile.getSaleStock().getStackInSlot(0).isEmpty());
        assertTrue(handler.insertItem(0, new ItemStack(Items.DIAMOND, 10), false).isEmpty());
        assertTrue(handler.extractItem(0, 10, false).isEmpty());
        tile.getEarnings().setStackInSlot(0, new ItemStack(Items.EMERALD, 12));
        assertEquals(5, handler.extractItem(8, 5, true).getCount());
        assertEquals(12, tile.getEarnings().getStackInSlot(0).getCount());
        assertEquals(5, handler.extractItem(8, 5, false).getCount());
        assertTrue(handler.extractItem(8, -1, false).isEmpty());
        assertTrue(handler.extractItem(-1, 1, false).isEmpty());
        assertEquals(7, tile.getEarnings().getStackInSlot(0).getCount());
    }

    @Test public void cachedHandlesRecheckPolicyAndDoNotLeakMutableReferences() {
        TileVendingMachine tile = new TileVendingMachine();
        tile.setSaleDefinition(0, new ItemStack(Items.DIAMOND), 1);
        boolean[] flags = {true, true, true};
        SidedMachineHandler handler = handler(tile, flags);
        ItemStack input = new ItemStack(Items.DIAMOND, 10);
        handler.insertItem(0, input, false);
        input.setCount(60);
        handler.getStackInSlot(0).setCount(2);
        assertEquals(10, tile.getSaleStock().getStackInSlot(0).getCount());
        flags[0] = false;
        assertFalse(handler.isItemValid(0, input));
        assertEquals(60, handler.insertItem(0, input, false).getCount());
        assertTrue(handler.getStackInSlot(0).isEmpty());
        tile.getEarnings().setStackInSlot(0, new ItemStack(Items.EMERALD, 10));
        flags[1] = false;
        assertTrue(handler.extractItem(8, 1, false).isEmpty());
        flags[0] = flags[1] = true;
        flags[2] = false; // Removed/unloaded tile, off-thread caller, or active external callback.
        assertEquals(0, handler.getSlotLimit(0));
        assertTrue(handler.getStackInSlot(8).isEmpty());
        assertTrue(handler.extractItem(8, 1, false).isEmpty());
    }

    @Test public void salesKeepMetadataAndOptionalNbtMatching() {
        TileVendingMachine tile = new TileVendingMachine();
        ItemStack template = new ItemStack(Items.DYE, 1, 4);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("custom", "blue");
        template.setTagCompound(tag);
        tile.setSaleDefinition(0, template, 1024);
        assertTrue(tile.acceptsAutomationItem(template.copy()));
        assertFalse(tile.acceptsAutomationItem(new ItemStack(Items.DYE, 1, 4)));
        tile.setMatchingNbt(4, false);
        assertTrue(tile.acceptsAutomationItem(new ItemStack(Items.DYE, 1, 4)));
        assertFalse(tile.acceptsAutomationItem(new ItemStack(Items.DYE, 1, 5)));
        tile.setSaleDefinition(0, ItemStack.EMPTY, 0);
        assertFalse(tile.acceptsAutomationItem(template));
    }
}
