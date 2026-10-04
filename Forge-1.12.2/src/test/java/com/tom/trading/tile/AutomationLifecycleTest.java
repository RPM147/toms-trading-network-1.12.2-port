package com.tom.trading.tile;

import com.tom.trading.block.BlockVendingMachine;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Exercises the actual tile update/neighbor guards without a server, chunks or disk access. */
public class AutomationLifecycleTest {
    private static final BlockPos POSITION = new BlockPos(0, 64, 0);
    private static final int TOP = 2;
    private TileVendingMachine tile;
    private TestServerWorld world;
    private Neighbor neighbor;

    @BeforeClass public static void bootstrap() { Bootstrap.register(); }

    @Before public void setup() throws Exception {
        world = TestServerWorld.create();
        tile = new TileVendingMachine();
        tile.setWorld(world);
        tile.setPos(POSITION);
        world.machine = tile;
        neighbor = new Neighbor();
        world.neighbor = neighbor;
        tile.setSaleDefinition(0, new ItemStack(Items.DIAMOND), 1);
        tile.setSideMasks(TOP, 0, TOP);
    }

    @Test public void stableNeighborTransfersAndDoesNotInvalidateDisplayDefinitions() {
        ItemStackHandler source = new ItemStackHandler(1);
        source.setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
        neighbor.handler = source;
        NBTTagCompound display = tile.copyDisplayState();
        long revision = tile.getDisplayRevision();
        tile.update();
        assertTrue(source.getStackInSlot(0).isEmpty());
        assertEquals(7, tile.getSaleStock().getStackInSlot(0).getCount());
        assertEquals(revision, tile.getDisplayRevision());
        assertEquals(display, tile.copyDisplayState());
    }

    @Test public void changedCapabilityAfterSimulationCannotCommitTheOldHandle() {
        AtomicInteger commits = new AtomicInteger();
        ItemStackHandler source = simulatedPull(() -> neighbor.handler = new ItemStackHandler(1), commits);
        tile.update();
        assertEquals(0, commits.get());
        assertEquals(7, source.getStackInSlot(0).getCount());
        assertTrue(tile.getSaleStock().getStackInSlot(0).isEmpty());
        assertFalse(tile.isAutomationQuarantined());
    }

    @Test public void replacedUnloadedInvalidOrUnavailableNeighborCannotCommit() throws Exception {
        for (int scenario = 0; scenario < 4; scenario++) {
            setup();
            int selected = scenario;
            AtomicInteger commits = new AtomicInteger();
            ItemStackHandler source = simulatedPull(() -> {
                if (selected == 0) world.neighbor = new Neighbor();
                if (selected == 1) world.neighborLoaded = false;
                if (selected == 2) neighbor.invalidate();
                if (selected == 3) neighbor.available = false;
            }, commits);
            tile.update();
            assertEquals("scenario " + scenario, 0, commits.get());
            assertEquals(7, source.getStackInSlot(0).getCount());
            assertTrue(tile.getSaleStock().getStackInSlot(0).isEmpty());
        }
    }

    @Test public void revokedNeighborOrSideAfterLocalRemovalReturnsEarnings() throws Exception {
        for (int scenario = 0; scenario < 2; scenario++) {
            setup();
            int selected = scenario;
            tile.setSideMasks(0, TOP, TOP);
            tile.getEarnings().setStackInSlot(0, new ItemStack(Items.EMERALD, 32));
            AtomicInteger commits = new AtomicInteger();
            ItemStackHandler destination = new ItemStackHandler(1) {
                @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                    if (simulate) {
                        // The next dirty notification is the actual local earnings removal.
                        world.onDirty = () -> {
                            if (selected == 0) neighbor.available = false;
                            else tile.setSideMasks(0, 0, 0);
                        };
                    } else commits.incrementAndGet();
                    return super.insertItem(slot, stack, simulate);
                }
            };
            neighbor.handler = destination;
            tile.update();
            assertEquals(0, commits.get());
            assertEquals(32, tile.getEarnings().getStackInSlot(0).getCount());
            assertTrue(destination.getStackInSlot(0).isEmpty());
            assertFalse(tile.isAutomationQuarantined());
            assertFalse(tile.writePortData(new NBTTagCompound()).getCompoundTag("AutomationTransfer").hasKey("Item"));
        }
    }

    @Test public void freshWrappersRemainFailClosedWithoutConsumingTheirInventory() {
        neighbor.freshInventory = new InventoryBasic("Synthetic fresh-wrapper provider", false, 1);
        neighbor.freshInventory.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 7));
        tile.update();
        assertEquals(7, neighbor.freshInventory.getStackInSlot(0).getCount());
        assertTrue(tile.getSaleStock().getStackInSlot(0).isEmpty());
        assertEquals(TOP, tile.getAutoSides());
    }

    @Test public void automaticShutdownInvalidatesAnAlreadyCachedSideDisplay() {
        neighbor.handler = new ItemStackHandler(1) {
            @Override public int getSlots() { throw new IllegalStateException("Synthetic neighbor probe failure"); }
        };
        assertEquals(TOP, tile.copyDisplayState().getInteger("Automatic"));
        long display = tile.getDisplayRevision(), quote = tile.getOfferRevision();
        tile.update();
        assertEquals(0, tile.getAutoSides());
        assertTrue(tile.getDisplayRevision() > display);
        assertEquals(quote, tile.getOfferRevision());
        assertEquals(0, tile.copyDisplayState().getInteger("Automatic"));
        assertFalse(tile.isAutomationRunning());
        assertFalse(tile.isAutomationQuarantined());
    }

    @Test public void sideEffectThenThrowQuarantinesAndNeverRetriesOrDropsUncertainItems() {
        tile.setSideMasks(0, TOP, TOP);
        tile.getEarnings().setStackInSlot(0, new ItemStack(Items.EMERALD, 32));
        AtomicInteger commits = new AtomicInteger();
        ItemStackHandler destination = new ItemStackHandler(1) {
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                ItemStack remainder = super.insertItem(slot, stack, simulate);
                if (!simulate) {
                    commits.incrementAndGet();
                    throw new IllegalStateException("Synthetic exception after external side effect");
                }
                return remainder;
            }
        };
        neighbor.handler = destination;
        tile.copyDisplayState();
        tile.update();
        assertTrue(tile.isAutomationQuarantined());
        assertFalse(tile.isAutomationRunning());
        assertEquals(0, tile.copyDisplayState().getInteger("Automatic"));
        assertEquals(32, destination.getStackInSlot(0).getCount());
        assertTrue(tile.getEarnings().getStackInSlot(0).isEmpty());
        NBTTagCompound evidence = tile.writePortData(new NBTTagCompound()).getCompoundTag("AutomationTransfer");
        assertTrue(evidence.getBoolean("Quarantined"));
        assertEquals(32, evidence.getInteger("CountInt"));
        tile.update();
        assertEquals(1, commits.get());
        assertTrue(tile.takeAutomationRemainderForDrop().isEmpty());
    }

    @Test public void unloadedMachineAndWrongThreadCannotStartAnAutomationCycle() {
        ItemStackHandler source = new ItemStackHandler(1);
        source.setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
        neighbor.handler = source;
        world.serverThread = false;
        tile.update();
        world.serverThread = true;
        tile.onChunkUnload();
        tile.update();
        assertEquals(7, source.getStackInSlot(0).getCount());
        tile.onLoad();
        tile.update();
        assertTrue(source.getStackInSlot(0).isEmpty());
        assertEquals(7, tile.getSaleStock().getStackInSlot(0).getCount());
    }

    private ItemStackHandler simulatedPull(Runnable callback, AtomicInteger commits) {
        ItemStackHandler source = new ItemStackHandler(1) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemStack result = super.extractItem(slot, amount, simulate);
                if (simulate) callback.run();
                else commits.incrementAndGet();
                return result;
            }
        };
        source.setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
        neighbor.handler = source;
        return source;
    }

    private static final class Neighbor extends TileEntity {
        IItemHandler handler;
        InventoryBasic freshInventory;
        boolean available = true;
        @Override public boolean hasCapability(Capability<?> cap, EnumFacing side) {
            return available && side == EnumFacing.DOWN;
        }
        @SuppressWarnings("unchecked")
        @Override public <T> T getCapability(Capability<T> cap, EnumFacing side) {
            return !hasCapability(cap, side) ? null
                    : (T) (freshInventory == null ? handler : new InvWrapper(freshInventory));
        }
    }

    /**
     * Test-only constructor-free fixture: WorldServer's real constructor starts world/server
     * infrastructure. Every path used here is overridden; no game, save handler or thread runs.
     * Unsafe and this fixture are confined to src/test and must never enter the release JAR.
     */
    private static final class TestServerWorld extends WorldServer {
        TileVendingMachine machine;
        TileEntity neighbor;
        boolean neighborLoaded, serverThread;
        IBlockState machineState;
        Runnable onDirty;
        private TestServerWorld() { super(null, null, null, 0, null); throw new AssertionError("Never construct"); }
        static TestServerWorld create() throws Exception {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            TestServerWorld world = (TestServerWorld) ((Unsafe) field.get(null)).allocateInstance(TestServerWorld.class);
            world.neighborLoaded = true;
            world.serverThread = true;
            world.machineState = new BlockVendingMachine().getDefaultState();
            return world;
        }
        @Override public boolean isCallingFromMinecraftThread() { return serverThread; }
        @Override public long getTotalWorldTime() { return Math.floorMod(POSITION.hashCode(), 20); }
        @Override public boolean isBlockLoaded(BlockPos position) {
            return POSITION.equals(position) || (neighborLoaded && POSITION.up().equals(position));
        }
        @Override public TileEntity getTileEntity(BlockPos position) {
            return POSITION.equals(position) ? machine : POSITION.up().equals(position) ? neighbor : null;
        }
        @Override public IBlockState getBlockState(BlockPos position) {
            return POSITION.equals(position) ? machineState : Blocks.AIR.getDefaultState();
        }
        @Override public void markChunkDirty(BlockPos position, TileEntity tile) {
            Runnable callback = onDirty;
            onDirty = null;
            if (callback != null) callback.run();
        }
        @Override public void updateComparatorOutputLevel(BlockPos position, Block block) { }
    }
}
