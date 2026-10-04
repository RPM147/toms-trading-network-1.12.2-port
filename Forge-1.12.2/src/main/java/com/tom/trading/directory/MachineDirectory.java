package com.tom.trading.directory;

import com.tom.trading.BuildInfo;
import com.tom.trading.block.BlockVendingMachine;
import com.tom.trading.tile.TileVendingMachine;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;

/** No world/chunk creation and no static per-save state. All calls run on the server thread. */
@Mod.EventBusSubscriber(modid = BuildInfo.MOD_ID)
public final class MachineDirectory {
    private MachineDirectory() {}

    public static MachineDirectoryData get(World world) {
        if (!(world instanceof WorldServer) || !((WorldServer) world).isCallingFromMinecraftThread()) return null;
        WorldServer overworld = DimensionManager.getWorld(0);
        if (overworld == null || overworld.getMinecraftServer() != world.getMinecraftServer()) return null;
        MapStorage storage = overworld.getMapStorage();
        if (storage == null) return null;
        MachineDirectoryData data = (MachineDirectoryData) storage.getOrLoadData(MachineDirectoryData.class,
                MachineDirectoryData.DATA_NAME);
        if (data == null) {
            // A failed MapStorage read must not overwrite a previously existing catalogue.
            java.io.File existing = overworld.getSaveHandler().getMapFileFromName(MachineDirectoryData.DATA_NAME);
            data = existing != null && existing.exists()
                    ? new MachineDirectoryData(MachineDirectoryData.DATA_NAME) : MachineDirectoryData.create();
            storage.setData(MachineDirectoryData.DATA_NAME, data);
        }
        return data;
    }

    public static boolean observe(TileVendingMachine tile) {
        World world = tile.getWorld();
        if (!(world instanceof WorldServer) || tile.isInvalid()) return true;
        WorldServer serverWorld = (WorldServer) world;
        if (!serverWorld.isCallingFromMinecraftThread()) return false;
        MachineDirectoryData data = get(world);
        if (data == null) return false;
        if (!data.isSupported()) return true;
        Chunk chunk = serverWorld.getChunkProvider().getLoadedChunk(tile.getPos().getX() >> 4, tile.getPos().getZ() >> 4);
        if (chunk == null || chunk.getTileEntity(tile.getPos(), Chunk.EnumCreateEntityType.CHECK) != tile) return false;
        if (!(chunk.getBlockState(tile.getPos()).getBlock() instanceof BlockVendingMachine)) return true;
        tile.ensureMachineIdentity();
        long displayRevision = tile.getDisplayRevision();
        OfferPreview preview = OfferPreviewCapture.capture(tile);
        // Item callbacks must not publish mixed owner/name/offer snapshots or clear a pending edit.
        if (tile.isInvalid() || tile.getDisplayRevision() != displayRevision) return false;
        MachineDirectoryEntry entry = new MachineDirectoryEntry(new MachineAddress(world.provider.getDimension(), tile.getPos()),
                tile.getMachineUuid(), tile.getOwnerUuid(), tile.getOwnerNameCache(),
                tile.hasCustomName() ? tile.getName() : "", tile.isDataVersionSupported()
                ? MachineDirectoryEntry.Evidence.OBSERVED : MachineDirectoryEntry.Evidence.UNSUPPORTED, preview);
        if (data.size() >= MachineDirectoryData.MAX_ENTRIES && data.get(entry.address) == null) {
            LogManager.getLogger(BuildInfo.MOD_ID).error("Machine directory capacity reached; not indexed: {}", entry.address);
            return true; // No per-tick retry/log flood. Explicit reindex can retry after cleanup.
        }
        data.observe(entry);
        return true;
    }

    /** invalidation is just a recheck request; an unloaded machine is never deleted here. */
    public static void changed(TileVendingMachine tile) {
        MachineDirectoryData data = get(tile.getWorld());
        if (data != null && data.isSupported())
            data.enqueue(new MachineAddress(tile.getWorld().provider.getDimension(), tile.getPos()));
    }

    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event) {
        MachineDirectoryData data = get(event.getWorld());
        if (data != null && data.isSupported())
            data.enqueueChunk(event.getWorld().provider.getDimension(), event.getChunk().x, event.getChunk().z);
    }

    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote || event.world.provider.getDimension() != 0) return;
        MachineDirectoryData data = get(event.world);
        if (data == null) return;
        if (!data.isSupported()) {
            data.pager().tick(System.nanoTime(), DimensionManager::isDimensionRegistered);
            return;
        }
        // Removal/replacement reconciliation is bounded, and never visits an unloaded chunk.
        for (int i = 0; i < 32; i++) {
            MachineAddress address = data.pollPending();
            if (address == null) break;
            WorldServer target = DimensionManager.getWorld(address.dimension);
            if (target == null || target.getMinecraftServer() != event.world.getMinecraftServer()) continue;
            Chunk chunk = target.getChunkProvider().getLoadedChunk(address.x >> 4, address.z >> 4);
            if (chunk == null || !chunk.isLoaded()) continue;
            TileEntity tile = chunk.getTileEntity(address.pos(), Chunk.EnumCreateEntityType.CHECK);
            if (tile instanceof TileVendingMachine && !tile.isInvalid()) observe((TileVendingMachine) tile);
            else if (!(chunk.getBlockState(address.pos()).getBlock() instanceof BlockVendingMachine)) data.removeVerified(address);
            // A machine block temporarily missing its tile is not proof of removal.
        }
        data.pager().tick(System.nanoTime(), DimensionManager::isDimensionRegistered);
    }
}
