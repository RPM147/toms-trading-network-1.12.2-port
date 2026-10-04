package com.tom.trading.tile;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TileVendingMachinePersistenceTest {
    @BeforeClass
    public static void bootstrapVanillaRegistries() {
        Bootstrap.register();
    }

    @Test
    public void versionOneMigratesAndVersionTwoKeepsTransferRemainders() {
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setInteger("DataVersion", 1);
        legacy.setString("CustomName", "Existing machine");
        TileVendingMachine tile = new TileVendingMachine();
        tile.readPortData(legacy);
        assertTrue(tile.isDataVersionSupported());
        NBTTagCompound upgraded = tile.writePortData(new NBTTagCompound());
        assertEquals(3, upgraded.getInteger("DataVersion"));
        upgraded.setInteger("DataVersion", 2); // Exercise a real v2 payload, not a v3 round trip.
        NBTTagCompound transfer = new NBTTagCompound();
        transfer.setTag("Item", new ItemStack(Items.EMERALD).serializeNBT());
        transfer.setInteger("CountInt", 1024);
        upgraded.setTag("AutomationTransfer", transfer);
        TileVendingMachine restored = new TileVendingMachine();
        restored.readPortData(upgraded);
        assertEquals("Existing machine", restored.getName());
        assertEquals(1024, restored.writePortData(new NBTTagCompound())
                .getCompoundTag("AutomationTransfer").getInteger("CountInt"));
        assertEquals(1024, restored.takeAutomationRemainderForDrop().getCount());
        assertTrue(restored.takeAutomationRemainderForDrop().isEmpty());
    }

    @Test
    public void roundTripKeepsDefinitionsOwnershipAndLargeRealStacks() {
        UUID owner = UUID.fromString("69f88e7b-f343-4a0f-8db9-3bfbd0e77401");
        UUID machine = UUID.fromString("90f0cc27-a67f-4e40-8917-e6a8ba1a012a");
        NBTTagCompound initial = new NBTTagCompound();
        initial.setInteger("DataVersion", TileVendingMachine.DATA_VERSION);
        initial.setUniqueId("OwnerUUID", owner);
        initial.setUniqueId("MachineUUID", machine);
        initial.setString("OwnerNameCache", "Owner");

        TileVendingMachine source = new TileVendingMachine();
        source.readPortData(initial);
        source.setCustomName(repeat('x', TileVendingMachine.MAX_NAME_LENGTH + 20));
        source.setCreativeMode(true);
        source.setSideMasks(0x7F, 0x55, 0x2A);
        source.setMatchNbtMask(0xA5);
        source.setPaymentDefinition(0, new ItemStack(Items.DIAMOND, 32), 1024);
        source.setSaleDefinition(0, new ItemStack(Items.EMERALD, 16), 256);

        ItemStack stock = new ItemStack(Items.DIAMOND);
        stock.setCount(1024);
        source.getSaleStock().setStackInSlot(0, stock);
        ItemStack earned = new ItemStack(Items.EMERALD);
        earned.setCount(256);
        source.getEarnings().setStackInSlot(7, earned);

        NBTTagCompound saved = source.writePortData(new NBTTagCompound());
        TileVendingMachine restored = new TileVendingMachine();
        restored.readPortData(saved);

        assertTrue(restored.isDataVersionSupported());
        assertEquals(TileVendingMachine.DATA_VERSION, restored.getLoadedDataVersion());
        assertEquals(owner, restored.getOwnerUuid());
        assertEquals(machine, restored.getMachineUuid());
        assertEquals("Owner", restored.getOwnerNameCache());
        assertEquals(TileVendingMachine.MAX_NAME_LENGTH, restored.getName().length());
        assertTrue(restored.isCreativeMode());
        assertEquals(0x3F, restored.getInputSides());
        assertEquals(0x15, restored.getOutputSides());
        assertEquals(0x2A, restored.getAutoSides());
        assertEquals(0xA5, restored.getMatchNbtMask());
        assertEquals(Items.DIAMOND, restored.getPaymentTemplate(0).getItem());
        assertEquals(1, restored.getPaymentTemplate(0).getCount());
        assertEquals(1024, restored.getPaymentQuantity(0));
        assertEquals(Items.EMERALD, restored.getSaleTemplate(0).getItem());
        assertEquals(1, restored.getSaleTemplate(0).getCount());
        assertEquals(256, restored.getSaleQuantity(0));
        assertEquals(1024, restored.getSaleStock().getStackInSlot(0).getCount());
        assertEquals(256, restored.getEarnings().getStackInSlot(7).getCount());
        assertEquals(TileVendingMachine.REAL_INVENTORY_SLOTS, restored.getSaleStock().getSlots());
        assertEquals(TileVendingMachine.REAL_INVENTORY_SLOTS, restored.getEarnings().getSlots());
    }

    @Test
    public void malformedKnownDataIsClampedWithoutGrowingInventories() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("DataVersion", TileVendingMachine.DATA_VERSION);
        data.setString("CustomName", repeat('n', TileVendingMachine.MAX_NAME_LENGTH + 10));
        data.setInteger("InputSides", -1);
        data.setInteger("OutputSides", 0x4000);
        data.setInteger("AutoSides", 0x43);
        data.setInteger("MatchNBT", -1);

        NBTTagList paymentTemplates = new NBTTagList();
        NBTTagCompound template = new NBTTagCompound();
        template.setInteger("Slot", 0);
        new ItemStack(Items.DIAMOND, 1).writeToNBT(template);
        paymentTemplates.appendTag(template);
        data.setTag("PaymentTemplates", paymentTemplates);
        data.setIntArray("PaymentQuantities", new int[] {-50});

        NBTTagCompound stock = new NBTTagCompound();
        stock.setInteger("Size", 4096);
        NBTTagList stockItems = new NBTTagList();
        NBTTagCompound oversized = new NBTTagCompound();
        oversized.setInteger("Slot", 0);
        new ItemStack(Items.EMERALD, 1).writeToNBT(oversized);
        oversized.setInteger("CountInt", 50000);
        stockItems.appendTag(oversized);
        NBTTagCompound outOfBounds = oversized.copy();
        outOfBounds.setInteger("Slot", 4000);
        stockItems.appendTag(outOfBounds);
        stock.setTag("Items", stockItems);
        data.setTag("SaleStock", stock);

        TileVendingMachine tile = new TileVendingMachine();
        tile.readPortData(data);

        assertEquals(TileVendingMachine.MAX_NAME_LENGTH, tile.getName().length());
        assertEquals(0x3F, tile.getInputSides());
        assertEquals(0, tile.getOutputSides());
        assertEquals(0x03, tile.getAutoSides());
        assertEquals(0xFF, tile.getMatchNbtMask());
        assertEquals(1, tile.getPaymentQuantity(0));
        assertEquals(TileVendingMachine.REAL_INVENTORY_SLOTS, tile.getSaleStock().getSlots());
        assertEquals(TileVendingMachine.MAX_QUANTITY, tile.getSaleStock().getStackInSlot(0).getCount());
    }

    @Test
    public void unknownFutureVersionIsPreservedAndNotInterpreted() {
        NBTTagCompound future = new NBTTagCompound();
        future.setInteger("DataVersion", TileVendingMachine.DATA_VERSION + 4);
        future.setString("FutureOnlyField", "keep-me");
        future.setBoolean("CreativeMode", true);
        future.setUniqueId("MachineUUID", UUID.randomUUID());

        TileVendingMachine tile = new TileVendingMachine();
        tile.readPortData(future);

        assertFalse(tile.isDataVersionSupported());
        assertEquals(TileVendingMachine.DATA_VERSION + 4, tile.getLoadedDataVersion());
        assertFalse(tile.isCreativeMode());
        assertNull(tile.getOwnerUuid());

        tile.setCustomName("must-not-overwrite");
        tile.setCreativeMode(true);
        tile.ensureMachineIdentity();
        tile.renewMachineIdentity();
        NBTTagCompound preserved = tile.writePortData(new NBTTagCompound());
        assertEquals(TileVendingMachine.DATA_VERSION + 4, preserved.getInteger("DataVersion"));
        assertEquals("keep-me", preserved.getString("FutureOnlyField"));
        assertTrue(preserved.getBoolean("CreativeMode"));
        assertFalse(preserved.hasKey("CustomName", Constants.NBT.TAG_STRING));
        assertEquals(future, preserved);
    }

    @Test
    public void deserializationDoesNotMintClientOrOfflineIdentitiesAndV2IgnoresForeignId() {
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setInteger("DataVersion", 2);
        legacy.setUniqueId("MachineUUID", UUID.randomUUID());
        TileVendingMachine tile = new TileVendingMachine();
        tile.readPortData(legacy);
        tile.ensureMachineIdentity();
        assertNull(tile.getMachineUuid());
        assertTrue(tile.isDataVersionSupported());
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
