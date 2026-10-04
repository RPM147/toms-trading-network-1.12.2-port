package com.tom.trading.trade;

import com.mojang.authlib.GameProfile;
import com.tom.trading.container.ContainerMachine;
import com.tom.trading.container.ContainerRemoteTrading;
import com.tom.trading.directory.MachineAddress;
import com.tom.trading.remote.RemoteContext;
import com.tom.trading.tile.TileVendingMachine;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Real executor and inventories; no game, ticket, world disk or optional policy mod is started. */
public class TradeAccessGuardTest {
    private StubPlayer player;
    private TileVendingMachine tile;
    @BeforeClass public static void bootstrap() { Bootstrap.register(); }
    private static <T> T unconstructed(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }
    @Before public void setup() throws Exception {
        StubWorld world = unconstructed(StubWorld.class);
        player = unconstructed(StubPlayer.class); player.world = world; player.dimension = -1;
        player.profile = new GameProfile(UUID.randomUUID(), "Buyer");
        player.inventory = new InventoryPlayer(player);
        player.openContainer = new Container() {
            public boolean canInteractWith(EntityPlayer viewer) { return true; }
            @Override public void detectAndSendChanges() { throw new AssertionError("Notification happened before ledger recording"); }
        };
        tile = new TileVendingMachine(); tile.setWorld(world); tile.setPos(new BlockPos(5000, 64, -50));
        tile.setPaymentDefinition(0, new ItemStack(Items.EMERALD), 1);
        tile.setSaleDefinition(0, new ItemStack(Items.APPLE), 1);
        tile.getSaleStock().setStackInSlot(0, new ItemStack(Items.APPLE, 5));
        player.inventory.setInventorySlotContents(0, new ItemStack(Items.EMERALD, 5));
    }
    @Test public void permissionRevokedAfterPreparationLeavesAllInventoriesUntouched() {
        AtomicInteger checks = new AtomicInteger();
        TradeExecutor.Outcome outcome = TradeExecutor.execute(player, tile, 3, tile.getOfferRevision(),
                () -> checks.incrementAndGet() == 1 ? null : TradeResultCode.ACCESS_DENIED);
        assertEquals(2, checks.get()); assertEquals(0, outcome.completed); assertEquals(TradeResultCode.ACCESS_DENIED, outcome.code);
        assertEquals(5, player.inventory.getStackInSlot(0).getCount());
        assertEquals(5, tile.getSaleStock().getStackInSlot(0).getCount()); assertTrue(tile.getEarnings().getStackInSlot(0).isEmpty());
        assertNull(outcome.receipt);
    }
    @Test public void committedPartialBatchIsRecordedBeforeNotificationsAndCannotBeRepeated() {
        TradeRequestLedger ledger = new TradeRequestLedger(); long revision = tile.getOfferRevision();
        TradeExecutor.Outcome first = ledger.execute(1, 8, revision, () -> TradeExecutor.execute(player, tile, 8, revision, () -> null));
        assertEquals(5, first.completed);
        assertSame(first, ledger.execute(1, 8, revision, () -> { throw new AssertionError("Duplicate mutation"); }));
        assertTrue(tile.getSaleStock().getStackInSlot(0).isEmpty()); assertEquals(5, tile.getEarnings().getStackInSlot(0).getCount());
    }
    @Test public void detailedProtectionFailuresBeforeCommitNeverConsumePaymentStockOrProduceAReceipt() {
        for (TradeResultCode code : TradeResultCode.values()) if (code.name().startsWith("PROTECTION_")) {
            AtomicInteger checks = new AtomicInteger();
            TradeExecutor.Outcome outcome = TradeExecutor.execute(player, tile, 3, tile.getOfferRevision(),
                    () -> checks.incrementAndGet() == 1 ? null : code);
            assertEquals(2, checks.get()); assertEquals(code, outcome.code); assertEquals(0, outcome.completed);
            assertEquals(5, player.inventory.getStackInSlot(0).getCount()); assertEquals(5, tile.getSaleStock().getStackInSlot(0).getCount());
            assertTrue(tile.getEarnings().getStackInSlot(0).isEmpty()); assertNull(outcome.receipt);
        }
    }
    @Test public void remoteContainerExposesOnlyThe36PlayerSlotsAndNoLocalConfigRoute() {
        RemoteContext context = new RemoteContext(1, -1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new MachineAddress(55, 5000, 64, -50));
        ContainerRemoteTrading container = new ContainerRemoteTrading(player, context, null);
        assertEquals(36, container.inventorySlots.size()); assertFalse(ContainerMachine.class.isInstance(container));
        Set<Integer> indices = new HashSet<>();
        for (Slot slot : container.inventorySlots) { assertSame(player.inventory, slot.inventory); assertTrue(indices.add(slot.getSlotIndex())); }
        for (int i = 0; i < 36; i++) assertTrue(indices.contains(i));
        assertFalse(container.canInteractWith(player)); // No server session is not a bypass.
        assertTrue(container.transferStackInSlot(player, 0).isEmpty());
    }

    @Test public void executorReceiptUsesActualNamesAmountsAndCurrentOwnerNotTheBuyer() throws Exception {
        StubPlayer owner = unconstructed(StubPlayer.class);
        owner.profile = new GameProfile(UUID.randomUUID(), "Seller");
        tile.setOwner(owner);
        // Matching is deliberately loose: the stock name differs from the template.
        ItemStack named = new ItemStack(Items.APPLE, 5); named.setStackDisplayName("Real stock apple");
        tile.getSaleStock().setStackInSlot(0, named);
        tile.setMatchingNbt(4, false);
        TradeExecutor.Outcome outcome = TradeExecutor.execute(player, tile, 8, tile.getOfferRevision(), () -> null);
        assertEquals(5, outcome.completed); assertNotEquals(TradeResultCode.SUCCESS, outcome.code);
        TradeReceipt receipt = outcome.receipt;
        assertNotNull(receipt);
        assertEquals(player.getUniqueID(), receipt.getBuyerUuid()); assertEquals("Buyer", receipt.getBuyerName());
        assertEquals(owner.getUniqueID(), receipt.getOwnerUuid()); assertEquals("Seller", receipt.getOwnerName());
        assertEquals(tile.getMachineUuid(), receipt.getMachineUuid());
        assertEquals(5L, receipt.getPayments().get(0).getQuantity());
        assertEquals(5L, receipt.getDeliveries().get(0).getQuantity());
        assertEquals("Real stock apple", receipt.getDeliveries().get(0).getLabel());
        assertTrue(receipt.createAnnouncement().getUnformattedText().contains("bought 5 Real stock apple from Seller"));
    }

    @Test public void ownerReplacementDuringFinalAccessCheckRejectsBeforeCommit() throws Exception {
        StubPlayer owner = unconstructed(StubPlayer.class);
        owner.profile = new GameProfile(UUID.randomUUID(), "NewOwner");
        AtomicInteger checks = new AtomicInteger();
        TradeExecutor.Outcome outcome = TradeExecutor.execute(player, tile, 1, tile.getOfferRevision(), () -> {
            if (checks.incrementAndGet() == 2) tile.setOwner(owner);
            return null;
        });
        assertEquals(TradeResultCode.OFFER_CHANGED, outcome.code); assertNull(outcome.receipt);
        assertEquals(5, tile.getSaleStock().getStackInSlot(0).getCount());
        assertEquals(5, player.inventory.getStackInSlot(0).getCount());
        // Even a name-cache change for the same UUID cannot publish the earlier owner label.
        checks.set(0);
        TradeExecutor.Outcome renamed = TradeExecutor.execute(player, tile, 1, tile.getOfferRevision(), () -> {
            if (checks.incrementAndGet() == 2) {
                owner.profile = new GameProfile(owner.getUniqueID(), "RenamedOwner");
                tile.setOwner(owner);
            }
            return null;
        });
        assertEquals(TradeResultCode.OFFER_CHANGED, renamed.code); assertNull(renamed.receipt);
        assertEquals(5, player.inventory.getStackInSlot(0).getCount());
    }

    private static class StubPlayer extends EntityPlayerMP {
        private GameProfile profile;
        private StubPlayer() { super(null, null, null, null); }
        @Override public GameProfile getGameProfile() { return profile; }
        @Override public UUID getUniqueID() { return profile.getId(); }
        @Override public String getName() { return profile.getName(); }
        @Override public boolean isEntityAlive() { return true; }
        @Override public boolean isSpectator() { return false; }
    }
    private static class StubWorld extends WorldServer {
        private StubWorld() { super(null, null, null, 0, null); }
        @Override public boolean isCallingFromMinecraftThread() { return true; }
        @Override public void markChunkDirty(BlockPos pos, TileEntity tile) { }
        @Override public IBlockState getBlockState(BlockPos pos) { return Blocks.AIR.getDefaultState(); }
        @Override public void updateComparatorOutputLevel(BlockPos pos, Block block) { }
    }
}
