package com.tom.trading.automation;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class AutomationTransferTest {
    @BeforeClass public static void bootstrap() { Bootstrap.register(); }

    private static AutomationTransfer transfer(ItemStackHandler stock, ItemStackHandler earnings) {
        return new AutomationTransfer(stock, earnings, s -> s.getItem() == Items.DIAMOND, () -> {});
    }

    @Test public void pullSimulatesLocalCapacityAndAcceptsPartialExtraction() {
        ItemStackHandler stock = new ItemStackHandler(1);
        stock.setStackInSlot(0, new ItemStack(Items.DIAMOND, 60));
        ItemStackHandler source = new ItemStackHandler(1) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return super.extractItem(slot, simulate ? amount : Math.min(2, amount), simulate);
            }
        };
        source.setStackInSlot(0, new ItemStack(Items.DIAMOND, 10));
        assertTrue(transfer(stock, new ItemStackHandler(1)).pull(source, 0, () -> true));
        assertEquals(62, stock.getStackInSlot(0).getCount());
        assertEquals(8, source.getStackInSlot(0).getCount());
    }

    @Test public void partialPushReturnsOnlyTheUnacceptedRemainder() {
        ItemStackHandler earnings = new ItemStackHandler(1);
        earnings.setStackInSlot(0, new ItemStack(Items.EMERALD, 64));
        ItemStackHandler destination = new ItemStackHandler(1) {
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (simulate) return super.insertItem(slot, stack, true);
                ItemStack accepted = stack.copy();
                accepted.setCount(7);
                super.insertItem(slot, accepted, false);
                ItemStack remainder = stack.copy();
                remainder.shrink(7);
                return remainder;
            }
        };
        AutomationTransfer engine = transfer(new ItemStackHandler(1), earnings);
        assertTrue(engine.push(destination, 0, 0, () -> true));
        assertEquals(57, earnings.getStackInSlot(0).getCount());
        assertEquals(7, destination.getStackInSlot(0).getCount());
        assertFalse(engine.hasPending());
    }

    @Test public void replacedCapabilityAndRefusedCommitDoNotRemoveEarnings() {
        ItemStackHandler earnings = new ItemStackHandler(1);
        earnings.setStackInSlot(0, new ItemStack(Items.EMERALD, 32));
        AtomicInteger commits = new AtomicInteger();
        ItemStackHandler destination = new ItemStackHandler(1) {
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!simulate) { commits.incrementAndGet(); return stack; }
                return ItemStack.EMPTY;
            }
        };
        AutomationTransfer engine = transfer(new ItemStackHandler(1), earnings);
        AtomicInteger validityCalls = new AtomicInteger();
        assertFalse(engine.push(destination, 0, 0, () -> validityCalls.incrementAndGet() < 3));
        assertEquals(0, commits.get());
        assertEquals(32, earnings.getStackInSlot(0).getCount());
        assertFalse(engine.push(destination, 0, 0, () -> true));
        assertEquals(1, commits.get());
        assertEquals(32, earnings.getStackInSlot(0).getCount());
    }

    @Test public void rejectedOrFullPullDoesNotExtractAnything() {
        ItemStackHandler stock = new ItemStackHandler(1);
        stock.setStackInSlot(0, new ItemStack(Items.DIAMOND, 64));
        ItemStackHandler source = new ItemStackHandler(1);
        source.setStackInSlot(0, new ItemStack(Items.DIAMOND, 10));
        AutomationTransfer engine = transfer(stock, new ItemStackHandler(1));
        assertFalse(engine.pull(source, 0, () -> true));
        stock.setStackInSlot(0, ItemStack.EMPTY);
        source.setStackInSlot(0, new ItemStack(Items.EMERALD, 10));
        assertFalse(engine.pull(source, 0, () -> true));
        assertEquals(10, source.getStackInSlot(0).getCount());
    }

    @Test public void changedSourceItemIsPersistedAndDroppedExactlyOnce() {
        ItemStackHandler stock = new ItemStackHandler(1);
        ItemStackHandler source = new ItemStackHandler(1) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (simulate) return new ItemStack(Items.DIAMOND, Math.min(amount, 5));
                return super.extractItem(slot, amount, false);
            }
        };
        source.setStackInSlot(0, new ItemStack(Items.EMERALD, 5));
        AutomationTransfer engine = transfer(stock, new ItemStackHandler(1));
        assertTrue(engine.pull(source, 0, () -> true));
        assertTrue(source.getStackInSlot(0).isEmpty());
        assertTrue(stock.getStackInSlot(0).isEmpty());
        AutomationTransfer restored = transfer(stock, new ItemStackHandler(1));
        restored.read(engine.write());
        assertTrue(restored.hasPending());
        ItemStack dropped = restored.takeForDrop();
        assertEquals(Items.EMERALD, dropped.getItem());
        assertEquals(5, dropped.getCount());
        assertTrue(restored.takeForDrop().isEmpty());
    }

    @Test public void largeSavedStacksDrainInLegalChunksWithoutByteTruncation() {
        for (int count : new int[] {1, 64, 127, 128, 255, 256, 1024}) {
            ItemStackHandler earnings = new ItemStackHandler(1);
            earnings.setStackInSlot(0, new ItemStack(Items.EMERALD, count));
            ItemStackHandler destination = new ItemStackHandler(16);
            AutomationTransfer engine = transfer(new ItemStackHandler(1), earnings);
            for (int slot = 0; slot < 16; slot++) engine.push(destination, slot, 0, () -> true);
            int total = 0;
            for (int slot = 0; slot < 16; slot++) total += destination.getStackInSlot(slot).getCount();
            assertEquals(count, total);
            assertTrue(earnings.getStackInSlot(0).isEmpty());
            NBTTagCompound persisted = new NBTTagCompound();
            persisted.setTag("Item", new ItemStack(Items.EMERALD).serializeNBT());
            persisted.setInteger("CountInt", count);
            engine.read(persisted);
            assertEquals(count, engine.write().getInteger("CountInt"));
            assertEquals(count, engine.takeForDrop().getCount());
        }
    }

    @Test public void uncertainExternalCommitIsQuarantinedAndNeverRetriedOrDropped() {
        ItemStackHandler earnings = new ItemStackHandler(1);
        earnings.setStackInSlot(0, new ItemStack(Items.EMERALD, 32));
        AtomicInteger commits = new AtomicInteger();
        ItemStackHandler broken = new ItemStackHandler(1) {
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!simulate) { commits.incrementAndGet(); throw new IllegalStateException("External failure"); }
                return ItemStack.EMPTY;
            }
        };
        AutomationTransfer engine = transfer(new ItemStackHandler(1), earnings);
        try {
            engine.push(broken, 0, 0, () -> true);
            fail("Expected external failure");
        } catch (IllegalStateException expected) {
            assertTrue(engine.isQuarantined());
        }
        AutomationTransfer restored = transfer(new ItemStackHandler(1), earnings);
        restored.read(engine.write());
        restored.drainPending();
        assertTrue(restored.isQuarantined());
        assertTrue(restored.takeForDrop().isEmpty());
        assertFalse(restored.push(broken, 0, 0, () -> true));
        assertEquals(1, commits.get());
        assertEquals(32, restored.write().getInteger("CountInt"));
    }
}
