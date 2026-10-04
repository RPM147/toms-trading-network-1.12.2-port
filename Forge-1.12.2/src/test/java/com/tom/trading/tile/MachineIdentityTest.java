package com.tom.trading.tile;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.UUID;
import static org.junit.Assert.*;

/** Identity generation only; no server launch or source-world disk access. */
public class MachineIdentityTest {
    @BeforeClass public static void bootstrap() { Bootstrap.register(); }
    @Test public void migrationMintsOnceOnServerAndPlacementRenewsWithoutChangingOwnership() throws Exception {
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setInteger("DataVersion", 2);
        UUID owner = UUID.randomUUID(); legacy.setUniqueId("OwnerUUID", owner);
        TileVendingMachine tile = new TileVendingMachine(); tile.readPortData(legacy);
        assertNull(tile.getMachineUuid());
        tile.setWorld(world()); tile.ensureMachineIdentity();
        UUID original = tile.getMachineUuid(); assertNotNull(original);
        tile.ensureMachineIdentity(); assertEquals(original, tile.getMachineUuid());
        NBTTagCompound saved = tile.writePortData(new NBTTagCompound());
        assertEquals(3, saved.getInteger("DataVersion"));
        assertEquals(original, saved.getUniqueId("MachineUUID"));
        tile.readPortData(saved); tile.ensureMachineIdentity(); assertEquals(original, tile.getMachineUuid());
        tile.renewMachineIdentity(); assertNotEquals(original, tile.getMachineUuid());
        assertEquals(owner, tile.getOwnerUuid());
    }
    @Test public void unknownServerPayloadIsNeverAssignedOrRewritten() throws Exception {
        NBTTagCompound future = new NBTTagCompound(); future.setInteger("DataVersion", 99);
        future.setUniqueId("MachineUUID", UUID.randomUUID());
        TileVendingMachine tile = new TileVendingMachine(); tile.readPortData(future); tile.setWorld(world());
        tile.ensureMachineIdentity(); tile.renewMachineIdentity();
        assertNull(tile.getMachineUuid()); assertEquals(future, tile.writePortData(new NBTTagCompound()));
    }
    private static StubWorld world() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return (StubWorld) ((Unsafe) field.get(null)).allocateInstance(StubWorld.class);
    }
    private static final class StubWorld extends WorldServer {
        private StubWorld() { super(null, null, null, 0, null); }
        @Override public boolean isCallingFromMinecraftThread() { return true; }
        @Override public IBlockState getBlockState(BlockPos pos) { return Blocks.AIR.getDefaultState(); }
        @Override public void markChunkDirty(BlockPos pos, TileEntity tile) { }
        @Override public void updateComparatorOutputLevel(BlockPos pos, Block block) { }
    }
}
