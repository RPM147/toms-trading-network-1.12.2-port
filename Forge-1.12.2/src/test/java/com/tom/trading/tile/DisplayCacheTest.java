package com.tom.trading.tile;

import com.mojang.authlib.GameProfile;
import com.tom.trading.container.ContainerMachine;
import com.tom.trading.container.ContainerMachineConfig;
import com.tom.trading.container.ContainerMachineTrading;
import com.tom.trading.container.MachineView;
import com.tom.trading.item.OreFilters;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.oredict.OreDictionary;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/** Real tile/container code; no Minecraft process, network connection or save files. */
public class DisplayCacheTest {
    private static final BlockPos POSITION = new BlockPos(0, 64, 0);
    private TestWorld world;
    private TileVendingMachine tile;
    private TestPlayer owner;
    private CopyProbe probe;

    @BeforeClass public static void bootstrap() { Bootstrap.register(); }

    @Before public void setup() {
        world = new TestWorld(false);
        tile = new TileVendingMachine();
        tile.setWorld(world);
        tile.setPos(POSITION);
        world.machine = tile;
        owner = player("Owner", 1);
        tile.setOwner(owner);
        probe = new CopyProbe();
        for (int slot = 0; slot < 4; slot++) {
            ItemStack payment = new ItemStack(Items.EMERALD);
            ItemStack sale = new ItemStack(Items.DIAMOND);
            payment.setTagCompound(new CountingTag(probe));
            sale.setTagCompound(new CountingTag(probe));
            tile.setPaymentDefinition(slot, payment, slot + 1);
            tile.setSaleDefinition(slot, sale, slot + 1);
        }
        probe.copies = 0;
    }

    @Test public void viewersShareOneCaptureAndUnchangedPollingSkipsDisplayCopies() {
        ContainerMachine[] viewers = new ContainerMachine[4];
        for (int i = 0; i < viewers.length; i++) {
            viewers[i] = new ContainerMachineTrading(player("Viewer" + i, i + 2), tile);
            poll(viewers[i], 5);
        }
        // Eight template copies for one capture + eight defensive NBT copies per viewer.
        assertEquals(8 + 8 * viewers.length, probe.copies);
        probe.copies = 0;
        for (ContainerMachine viewer : viewers) poll(viewer, 100);
        assertEquals(0, probe.copies);

        long quote = tile.getOfferRevision();
        tile.setCustomName("Updated display");
        for (ContainerMachine viewer : viewers) poll(viewer, 5);
        assertEquals(8 + 8 * viewers.length, probe.copies);
        assertEquals(quote, tile.getOfferRevision());
    }

    @Test public void realInventorySyncContinuesWithoutInvalidatingDisplay() {
        ContainerMachineConfig config = new ContainerMachineConfig(owner, tile);
        RecordingListener listener = new RecordingListener();
        config.addListener(listener);
        poll(config, 10);
        long display = tile.getDisplayRevision(), quote = tile.getOfferRevision();
        probe.copies = 0;
        listener.slots.clear();
        tile.getSaleStock().setStackInSlot(0, new ItemStack(Items.DIAMOND, 12));
        tile.getEarnings().setStackInSlot(0, new ItemStack(Items.EMERALD, 9));
        poll(config, 5);
        assertEquals(12, listener.slots.get(0).getCount());
        assertEquals(9, listener.slots.get(8).getCount());
        assertEquals(display, tile.getDisplayRevision());
        assertEquals(quote, tile.getOfferRevision());
        assertEquals(0, probe.copies);
    }

    @Test public void displayEditsInvalidateButDoNotChangeTheEconomicQuote() {
        long quote = tile.getOfferRevision();
        assertRefresh(() -> tile.setCustomName("Shop"));
        assertEquals("Shop", tile.copyDisplayState().getString("Name"));
        assertRefresh(() -> tile.setSideMasks(1, 2, 3));
        MachineView view = MachineView.read(tile.copyDisplayState());
        assertEquals(1, view.inputs);
        assertEquals(2, view.outputs);
        assertEquals(3, view.automatic);
        assertRefresh(() -> tile.setOwner(player("NewOwner", 2)));
        assertEquals("NewOwner", tile.copyDisplayState().getString("Owner"));
        assertRefresh(() -> tile.setOwner(player("RenamedOwner", 2)));
        assertEquals("RenamedOwner", tile.copyDisplayState().getString("Owner"));
        assertEquals(quote, tile.getOfferRevision());
    }

    @Test public void normalizedNoOpEditsDoNotDiscardTheCache() {
        tile.setSideMasks(1, 2, 3);
        tile.setCustomName("Shop");
        tile.copyDisplayState();
        long display = tile.getDisplayRevision(), quote = tile.getOfferRevision();
        tile.setOwner(owner);
        tile.setOwner(null);
        tile.setCustomName("Shop");
        tile.setSideMasks(65, 66, 67);
        tile.setMatchNbtMask(0x1FF);
        tile.setCreativeMode(false);
        ItemStack same = tile.getPaymentTemplate(0);
        same.setCount(32);
        tile.setPaymentDefinition(0, same, 1);
        assertEquals(display, tile.getDisplayRevision());
        assertEquals(quote, tile.getOfferRevision());
        probe.copies = 0;
        tile.copyDisplayState();
        assertEquals("Only a defensive copy, not a new capture", 8, probe.copies);
    }

    @Test public void everyEconomicEditRefreshesBothOfferAndDisplay() {
        assertEconomicRefresh(() -> tile.setPaymentDefinition(0, new ItemStack(Items.EMERALD), 64));
        assertEquals(64, tile.copyDisplayState().getIntArray("Quantities")[0]);
        assertEconomicRefresh(() -> tile.setSaleDefinition(0, new ItemStack(Items.GOLD_INGOT), 2));
        assertEquals(Items.GOLD_INGOT, MachineView.read(tile.copyDisplayState()).templates[4].getItem());
        ItemStack named = new ItemStack(Items.DIAMOND);
        named.setStackDisplayName("Variant");
        assertEconomicRefresh(() -> tile.setSaleDefinition(0, named, 2));
        assertEquals("Variant", MachineView.read(tile.copyDisplayState()).templates[4].getDisplayName());
        assertEconomicRefresh(() -> tile.setMatchingNbt(4, false));
        assertEconomicRefresh(() -> tile.setCreativeMode(true));
        OreDictionary.registerOre("ttnDisplayCachePayment", Items.EMERALD);
        assertEconomicRefresh(() -> tile.setPaymentDefinition(0,
                OreFilters.create(new ItemStack(Items.EMERALD), "ttnDisplayCachePayment"), 1));
        assertEconomicRefresh(() -> tile.setSaleDefinition(0, ItemStack.EMPTY, 0));
        assertEquals(0, tile.copyDisplayState().getIntArray("Quantities")[4]);
    }

    @Test public void sentCopiesCannotPoisonTheCacheOrExposePrivateInventories() {
        tile.getSaleStock().setStackInSlot(0, new ItemStack(Items.DIAMOND, 12));
        tile.getEarnings().setStackInSlot(0, new ItemStack(Items.EMERALD, 9));
        NBTTagCompound copy = tile.copyDisplayState();
        NBTTagCompound expected = copy.copy();
        assertEquals(new HashSet<>(Arrays.asList("Name", "Owner", "OfferRevision", "Creative",
                "Inputs", "Outputs", "Automatic", "MatchNBT", "Templates", "Quantities")), copy.getKeySet());
        copy.setString("Name", "Forged");
        copy.getIntArray("Quantities")[0] = 1024;
        copy.getTagList("Templates", 10).getCompoundTagAt(0).getCompoundTag("tag")
                .getIntArray("payload")[0] = 999;
        copy.setTag("SaleStock", tile.getSaleStock().serializeNBT());
        assertEquals(expected, tile.copyDisplayState());
        assertEquals(1, tile.getPaymentQuantity(0));
        assertEquals(7, tile.getPaymentTemplate(0).getTagCompound().getIntArray("payload")[0]);
        assertEquals(12, tile.getSaleStock().getStackInSlot(0).getCount());
    }

    @Test public void lifecycleAndReloadNeverReuseAnOldSnapshot() {
        long quote = tile.getOfferRevision();
        assertRefresh(tile::onChunkUnload);
        assertRefresh(tile::onLoad);
        assertRefresh(tile::invalidate);
        tile.validate();
        assertEquals(quote, tile.getOfferRevision());
        NBTTagCompound saved = tile.writePortData(new NBTTagCompound());
        saved.setString("CustomName", "Reloaded");
        assertEconomicRefresh(() -> tile.readPortData(saved));
        assertEquals("Reloaded", tile.copyDisplayState().getString("Name"));
        NBTTagCompound future = new NBTTagCompound();
        future.setInteger("DataVersion", 99);
        future.setString("FuturePayload", "preserved");
        tile.readPortData(future);
        assertFalse(tile.isDataVersionSupported());
        assertEquals("", tile.copyDisplayState().getString("Name"));
        assertEquals(future, tile.writePortData(new NBTTagCompound()));
    }

    @Test public void callbackInvalidatedCaptureIsDiscardedUntilTheNextAttempt() {
        probe.nextCopy = () -> tile.setCustomName("Changed during capture");
        assertNull(tile.copyDisplayState());
        assertEquals(8, probe.copies);
        probe.copies = 0;
        assertEquals("Changed during capture", tile.copyDisplayState().getString("Name"));
        assertEquals(16, probe.copies);
    }

    @Test public void recursiveCaptureReturnsNullWithoutRecursingOrPoisoningTheCache() {
        probe.nextCopy = () -> assertNull(tile.copyDisplayState());
        assertNotNull(tile.copyDisplayState());
        assertEquals(16, probe.copies);
        probe.copies = 0;
        assertNotNull(tile.copyDisplayState());
        assertEquals(8, probe.copies);
    }

    @Test public void throwingCaptureReleasesTheGuardForLaterSync() {
        probe.nextCopy = () -> { throw new IllegalStateException("Synthetic item-copy failure"); };
        try {
            tile.copyDisplayState();
            fail("Expected item copy to fail");
        } catch (IllegalStateException expected) {
            assertEquals("Synthetic item-copy failure", expected.getMessage());
        }
        probe.copies = 0;
        assertNotNull(tile.copyDisplayState());
        assertEquals(16, probe.copies);
    }

    @Test public void revokedOwnerCannotSyncEvenWhenTheSharedCacheIsWarm() {
        ContainerMachineConfig config = new ContainerMachineConfig(owner, tile);
        RecordingListener listener = new RecordingListener();
        config.addListener(listener);
        poll(config, 10);
        tile.setOwner(player("NewOwner", 2));
        tile.copyDisplayState(); // Another viewer already warmed the current display.
        probe.copies = 0;
        listener.slots.clear();
        tile.getSaleStock().setStackInSlot(0, new ItemStack(Items.DIAMOND, 12));
        poll(config, 10);
        assertFalse(config.canInteractWith(owner));
        assertTrue(listener.slots.isEmpty());
        assertEquals(0, probe.copies);
        RecordingListener late = new RecordingListener();
        config.addListener(late);
        assertEquals(0, late.fullSyncs);
        assertTrue(new ContainerMachineTrading(owner, tile).canInteractWith(owner));
    }

    @Test public void clientPollingDoesNotBuildServerDisplayState() {
        TestWorld client = new TestWorld(true);
        client.machine = tile;
        tile.setWorld(client);
        TestPlayer player = new TestPlayer(client, new GameProfile(new UUID(0, 20), "Client"));
        poll(new ContainerMachineTrading(player, tile), 100);
        assertEquals(0, probe.copies);
    }

    private void assertRefresh(Runnable edit) {
        tile.copyDisplayState();
        long before = tile.getDisplayRevision();
        edit.run();
        assertTrue(tile.getDisplayRevision() > before);
        assertEquals(MachineView.capture(tile), tile.copyDisplayState());
    }

    private void assertEconomicRefresh(Runnable edit) {
        long before = tile.getOfferRevision();
        assertRefresh(edit);
        assertFalse(tile.matchesOfferRevision(before));
        assertEquals(tile.getOfferRevision(), tile.copyDisplayState().getLong("OfferRevision"));
    }

    private TestPlayer player(String name, int id) {
        return new TestPlayer(world, new GameProfile(new UUID(0, id), name));
    }

    private static void poll(ContainerMachine container, int ticks) {
        for (int i = 0; i < ticks; i++) container.detectAndSendChanges();
    }

    private static final class CopyProbe {
        int copies;
        Runnable nextCopy;
    }

    private static final class CountingTag extends NBTTagCompound {
        private final CopyProbe probe;
        CountingTag(CopyProbe probe) { this.probe = probe; setIntArray("payload", new int[] {7, 8}); }
        @Override public NBTTagCompound copy() {
            probe.copies++;
            Runnable callback = probe.nextCopy;
            probe.nextCopy = null;
            if (callback != null) callback.run();
            CountingTag copy = new CountingTag(probe);
            for (String key : getKeySet()) copy.setTag(key, getTag(key).copy());
            return copy;
        }
    }

    private static final class TestPlayer extends EntityPlayer {
        TestPlayer(World world, GameProfile profile) { super(world, profile); setPosition(0.5, 64.5, 0.5); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return capabilities.isCreativeMode; }
    }

    private static final class TestWorld extends World {
        private TileVendingMachine machine;
        TestWorld(boolean remote) {
            super(null, new WorldInfo(new NBTTagCompound()), new WorldProviderSurface(), new Profiler(), remote);
        }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) { return true; }
        @Override public BlockPos getSpawnPoint() { return POSITION; }
        @Override public TileEntity getTileEntity(BlockPos position) { return POSITION.equals(position) ? machine : null; }
        @Override public IBlockState getBlockState(BlockPos position) { return Blocks.AIR.getDefaultState(); }
        @Override public void markChunkDirty(BlockPos position, TileEntity tile) { }
        @Override public void updateComparatorOutputLevel(BlockPos position, Block block) { }
    }

    private static final class RecordingListener implements IContainerListener {
        int fullSyncs;
        final Map<Integer, ItemStack> slots = new HashMap<>();
        @Override public void sendAllContents(Container c, NonNullList<ItemStack> stacks) { fullSyncs++; }
        @Override public void sendSlotContents(Container c, int slot, ItemStack stack) { slots.put(slot, stack.copy()); }
        @Override public void sendWindowProperty(Container c, int id, int value) { }
        @Override public void sendAllWindowProperties(Container c, IInventory inventory) { }
    }
}
