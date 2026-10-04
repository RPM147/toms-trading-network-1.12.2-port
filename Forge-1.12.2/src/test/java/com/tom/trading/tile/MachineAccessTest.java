package com.tom.trading.tile;

import com.mojang.authlib.GameProfile;
import com.tom.trading.block.BlockVendingMachine;
import com.tom.trading.container.ContainerMachineConfig;
import com.tom.trading.container.MachineGuiHandler;
import com.tom.trading.event.CommonEvents;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.event.world.BlockEvent;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/** Real containers and inventories in an in-memory world, without starting Minecraft. */
public class MachineAccessTest {
    private static final BlockPos POSITION = new BlockPos(0, 64, 0);
    private TestWorld world;
    private TileVendingMachine tile;
    private TestPlayer owner, guest, operator;

    @BeforeClass public static void bootstrap() { Bootstrap.register(); }

    @Before public void setup() {
        world = new TestWorld();
        tile = new TileVendingMachine();
        tile.setWorld(world);
        tile.setPos(POSITION);
        world.machine = tile;
        owner = player("Owner", 1, false);
        guest = player("Guest", 2, false);
        operator = player("Operator", 3, true);
        tile.setOwner(owner);
        tile.getSaleStock().setStackInSlot(0, new ItemStack(Items.DIAMOND, 12));
        tile.getEarnings().setStackInSlot(0, new ItemStack(Items.EMERALD, 9));
    }

    @Test public void commandPrivilegesAndCreativeNeverOverrideAnExistingOwner() {
        assertTrue(tile.canConfigure(owner));
        assertFalse(tile.canConfigure(guest));
        assertFalse(tile.canConfigure(operator));
        operator.capabilities.isCreativeMode = true;
        assertFalse(tile.canConfigure(operator));
        assertFalse(tile.canEnableCreativeMode(operator));
        assertFalse(tile.canConfigure(null));
    }

    @Test public void ownershipUsesUuidNotNameOrOnlinePresence() {
        assertTrue(tile.canConfigure(player("RenamedOwner", 1, false)));
        assertFalse(tile.canConfigure(player("Owner", 4, true)));
        TileVendingMachine restored = new TileVendingMachine();
        restored.readPortData(tile.writePortData(new NBTTagCompound()));
        assertEquals(owner.getUniqueID(), restored.getOwnerUuid());
        assertFalse(restored.canConfigure(operator));
        assertTrue(restored.canConfigure(owner));
    }

    @Test public void operatorsRecoverOnlyMachinesWithoutARecordedOwner() {
        TileVendingMachine unowned = new TileVendingMachine();
        assertFalse(unowned.canConfigure(guest));
        assertTrue(unowned.canConfigure(operator));
        unowned.setOwner(owner);
        assertFalse(unowned.canConfigure(operator));
        assertTrue(unowned.canConfigure(owner));
    }

    @Test public void rightClickRoutesEveryNonOwnerToTradingAndRejectsForgedConfigOpen() {
        BlockVendingMachine block = new BlockVendingMachine();
        for (TestPlayer viewer : new TestPlayer[] {owner, guest, operator}) {
            block.onBlockActivated(world, POSITION, block.getDefaultState(), viewer,
                    EnumHand.MAIN_HAND, EnumFacing.NORTH, 0.5F, 0.5F, 0.5F);
            assertEquals(viewer == owner ? MachineGuiHandler.CONFIG : MachineGuiHandler.TRADING, viewer.openedGui);
            assertNotNull(viewer.openContainer);
            assertTrue(viewer.openContainer.canInteractWith(viewer));
            assertEquals(viewer == owner ? 52 : 36, viewer.openContainer.inventorySlots.size());
            if (viewer != owner) {
                assertNull(MachineGuiHandler.create(MachineGuiHandler.CONFIG, viewer, world, 0, 64, 0));
                for (Slot slot : viewer.openContainer.inventorySlots) assertSame(viewer.inventory, slot.inventory);
            }
        }
    }

    @Test public void forgedAndRevokedConfigContainersCannotMoveItemsByAnyClickType() {
        assertBlocked(new ContainerMachineConfig(operator, tile), operator);
        ContainerMachineConfig revoked = new ContainerMachineConfig(owner, tile);
        assertTrue(revoked.canInteractWith(owner));
        tile.setOwner(guest);
        assertBlocked(revoked, owner);
    }

    @Test public void revokedContainersStopSyncingMachineInventoryContents() {
        ContainerMachineConfig config = new ContainerMachineConfig(owner, tile);
        RecordingListener listener = new RecordingListener();
        config.addListener(listener);
        assertTrue(listener.updates > 0);
        listener.updates = 0;
        tile.setOwner(guest);
        tile.getSaleStock().setStackInSlot(0, new ItemStack(Items.DIAMOND, 11));
        config.detectAndSendChanges();
        assertEquals(0, listener.updates);
        RecordingListener late = new RecordingListener();
        config.addListener(late);
        assertEquals(0, late.updates);
    }

    @Test public void ownerCanStillExtractStockAndEarningsAndGuestCannotBreak() {
        ContainerMachineConfig config = new ContainerMachineConfig(owner, tile);
        assertFalse(config.transferStackInSlot(owner, 0).isEmpty());
        assertFalse(config.transferStackInSlot(owner, 8).isEmpty());
        assertTrue(tile.getSaleStock().getStackInSlot(0).isEmpty());
        assertTrue(tile.getEarnings().getStackInSlot(0).isEmpty());
        for (TestPlayer viewer : new TestPlayer[] {owner, guest, operator}) {
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(world, POSITION, Blocks.STONE.getDefaultState(), viewer) {
                // Plain JUnit does not run Forge's @Cancelable event transformer.
                @Override public boolean isCancelable() { return true; }
            };
            CommonEvents.onBlockBreak(event);
            assertEquals(viewer != owner, event.isCanceled());
        }
    }

    private void assertBlocked(ContainerMachineConfig config, TestPlayer viewer) {
        assertFalse(config.canInteractWith(viewer));
        NBTTagCompound before = tile.writePortData(new NBTTagCompound());
        viewer.inventory.setInventorySlotContents(0, new ItemStack(Items.GOLD_INGOT, 3));
        for (int slot : new int[] {0, 8}) {
            assertFalse(config.inventorySlots.get(slot).canTakeStack(viewer));
            assertFalse(config.inventorySlots.get(slot).isItemValid(new ItemStack(Items.DIAMOND)));
            for (ClickType click : ClickType.values()) {
                assertTrue(config.slotClick(slot, 0, click, viewer).isEmpty());
                assertEquals(before, tile.writePortData(new NBTTagCompound()));
                assertTrue(viewer.inventory.getItemStack().isEmpty());
            }
            assertTrue(config.transferStackInSlot(viewer, slot).isEmpty());
        }
        assertEquals(before, tile.writePortData(new NBTTagCompound()));
        assertEquals(3, viewer.inventory.getStackInSlot(0).getCount());
    }

    @Test public void explosionCannotDropOrRemoveTheMachineAndItsContents() {
        BlockVendingMachine block = new BlockVendingMachine();
        assertFalse(block.canDropFromExplosion(null));
        assertTrue(block.getExplosionResistance(null) >= 1_000_000.0F);
        NBTTagCompound before = tile.writePortData(new NBTTagCompound());
        block.onBlockExploded(world, POSITION, null);
        assertSame(tile, world.getTileEntity(POSITION));
        assertEquals(before, tile.writePortData(new NBTTagCompound()));
    }

    private TestPlayer player(String name, int id, boolean commands) {
        return new TestPlayer(world, new GameProfile(new UUID(0, id), name), commands);
    }

    private static final class TestPlayer extends EntityPlayer {
        private final boolean commands;
        private int openedGui = -1;
        TestPlayer(World world, GameProfile profile, boolean commands) {
            super(world, profile);
            this.commands = commands;
            setPosition(0.5, 64.5, 0.5);
        }
        @Override public boolean canUseCommand(int level, String name) { return commands && level <= 2; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return capabilities.isCreativeMode; }
        @Override public void openGui(Object mod, int id, World world, int x, int y, int z) {
            openedGui = id;
            openContainer = MachineGuiHandler.create(id, this, world, x, y, z);
        }
    }

    private static final class TestWorld extends World {
        private TileVendingMachine machine;
        TestWorld() { super(null, new WorldInfo(new NBTTagCompound()), new WorldProviderSurface(), new Profiler(), false); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) { return true; }
        @Override public BlockPos getSpawnPoint() { return POSITION; }
        @Override public TileEntity getTileEntity(BlockPos position) { return POSITION.equals(position) ? machine : null; }
        @Override public IBlockState getBlockState(BlockPos position) { return Blocks.AIR.getDefaultState(); }
        @Override public void markChunkDirty(BlockPos position, TileEntity tile) { }
        @Override public void updateComparatorOutputLevel(BlockPos position, Block block) { }
    }

    private static final class RecordingListener implements IContainerListener {
        private int updates;
        @Override public void sendAllContents(Container c, NonNullList<ItemStack> stacks) { updates++; }
        @Override public void sendSlotContents(Container c, int slot, ItemStack stack) { updates++; }
        @Override public void sendWindowProperty(Container c, int id, int value) { updates++; }
        @Override public void sendAllWindowProperties(Container c, IInventory inventory) { updates++; }
    }
}
