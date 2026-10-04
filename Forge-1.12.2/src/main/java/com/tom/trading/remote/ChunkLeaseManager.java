package com.tom.trading.remote;

import com.tom.trading.BuildInfo;
import com.tom.trading.TradingNetworkMod;
import com.tom.trading.directory.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import org.apache.logging.log4j.LogManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Per-server, main-thread-only, shared physical chunks with independently releasable session handles. */
public final class ChunkLeaseManager {
    private final MinecraftServer server;
    private final SharedLeasePool<Key, Resource> pool;
    private final Map<Key, Integer> targets = new HashMap<>();
    private final Set<Lease> handles = new HashSet<>();
    private final Set<World> unloadedWorlds = Collections.newSetFromMap(new WeakHashMap<>());
    private DimensionManifest manifest;
    private Map<Integer, DimensionType> providers = Collections.emptyMap();

    public ChunkLeaseManager(MinecraftServer server) {
        this.server = server;
        pool = new SharedLeasePool<>(RemoteSettings.leasedChunks(), Resource::releaseTicket);
    }

    public static void registerCallbacks() {
        ForgeChunkManager.setForcedChunkLoadingCallback(TradingNetworkMod.instance, new ForgeChunkManager.OrderedLoadingCallback() {
            @Override public List<ForgeChunkManager.Ticket> ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world, int max) {
                return Collections.emptyList(); // Session leases must never survive a server restart.
            }
            @Override public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world) { }
        });
    }

    /** Reserves the entire footprint atomically. Does not initialize dimensions, load chunks or request tickets. */
    public Lease reserve(MachineAddress address, MachineDirectoryData data, boolean neighborhood) throws IOException, Failure {
        if (!server.isCallingFromMinecraftThread()) throw new IllegalStateException("Server thread required");
        Key target = new Key(address.dimension, address.x >> 4, address.z >> 4);
        Set<Key> keys = new LinkedHashSet<>();
        keys.add(target); // Target must be checked on disk before a cold dimension is initialized.
        if (neighborhood) {
            ProtectionCoverage.Bounds bounds = ProtectionCoverage.bounds(address.x + 0.5, address.z + 0.5, World.MAX_ENTITY_RADIUS);
            if (bounds == null || bounds.size() > RemoteSettings.MAX_CHUNKS_PER_TARGET) throw new Failure(RemoteOpenResult.REGION_TOO_LARGE);
            for (int x = bounds.minX; x < bounds.maxX; x++) for (int z = bounds.minZ; z < bounds.maxZ; z++)
                keys.add(new Key(address.dimension, x, z));
        }
        if (!targets.containsKey(target) && targets.size() >= RemoteSettings.chunks()) throw new Failure(RemoteOpenResult.CHUNK_BUDGET_EXHAUSTED);
        if (!DimensionManager.isDimensionRegistered(address.dimension)) throw new Failure(RemoteOpenResult.UNAVAILABLE);
        WorldServer overworld = DimensionManager.getWorld(0);
        if (overworld == null || overworld.getMinecraftServer() != server) throw new Failure(RemoteOpenResult.UNAVAILABLE);
        Map<Integer, DimensionType> current = new HashMap<>();
        for (int id : DimensionManager.getStaticDimensionIDs()) current.put(id, DimensionManager.getProviderType(id));
        if (manifest == null || !current.equals(providers)) {
            manifest = DimensionManifest.capture(overworld, data);
            providers = current;
        }
        DimensionManifest.Dimension mapping = null;
        for (DimensionManifest.Dimension row : manifest.dimensions) if (row.id == address.dimension) mapping = row;
        if (mapping == null || !mapping.supported()) throw new Failure(RemoteOpenResult.UNAVAILABLE);
        Path root = overworld.getSaveHandler().getWorldDirectory().getCanonicalFile().toPath();
        Path folder = DimensionManifest.resolveFolder(root, mapping.folder);
        WorldServer world = DimensionManager.getWorld(address.dimension);
        WorldProvider provider = world == null ? DimensionManager.createProviderFor(address.dimension) : world.provider;
        String actualFolder = provider.getSaveFolder();
        if (actualFolder == null && address.dimension != 0) throw new Failure(RemoteOpenResult.UNAVAILABLE);
        if (!provider.getClass().getName().equals(mapping.provider)
                || !DimensionManifest.resolveFolder(root, actualFolder == null ? "" : actualFolder.replace('\\', '/')).equals(folder))
            throw new Failure(RemoteOpenResult.UNAVAILABLE);
        SharedLeasePool<Key, Resource>.Reservation reservation = pool.reserve(keys);
        if (reservation == null) throw new Failure(RemoteOpenResult.CHUNK_BUDGET_EXHAUSTED);
        Lease lease = new Lease(target, keys, folder, mapping.provider, reservation);
        handles.add(lease);
        targets.put(target, targets.getOrDefault(target, 0) + 1);
        return lease;
    }

    /** Prepares at most one physical chunk. False means still waiting, not permission to trade. */
    boolean advance(Lease lease, ChunkLoadBudget budget) throws IOException, Failure {
        if (!handles.contains(lease) || !lease.reservation.current()) throw new Failure(RemoteOpenResult.UNAVAILABLE);
        WorldServer world = DimensionManager.getWorld(lease.target.dimension);
        if (lease.world != null && lease.world != world) throw new Failure(RemoteOpenResult.UNAVAILABLE);
        if (world != null && lease.world == null) bind(lease, world);
        Key next = null;
        for (Key key : lease.keys) {
            Resource resource = lease.reservation.get(key);
            if (resource == null) { if (next == null) next = key; }
            else if (resource.world != world || !resource.live()) throw new Failure(RemoteOpenResult.UNAVAILABLE);
        }
        if (next == null) return lease.live();
        Chunk loaded = world == null ? null : world.getChunkProvider().getLoadedChunk(next.x, next.z);
        if (!budget.take(loaded == null, RemoteSettings.loadInterval())) return false;
        // Read-only header precheck; never initialize a dimension for a nonexistent target.
        if (loaded == null && !ExistingChunkStorage.exists(lease.folder, next.x, next.z)) throw new Failure(RemoteOpenResult.REGION_MISSING);
        if (world == null) {
            if (!next.equals(lease.target)) throw new Failure(RemoteOpenResult.UNAVAILABLE);
            DimensionManager.initDimension(next.dimension);
            world = DimensionManager.getWorld(next.dimension);
            bind(lease, world);
        }
        if (!lease.reservation.current()) throw new Failure(RemoteOpenResult.UNAVAILABLE);
        ForgeChunkManager.Ticket ticket = ForgeChunkManager.requestTicket(TradingNetworkMod.instance, world, ForgeChunkManager.Type.NORMAL);
        if (ticket == null) throw new Failure(RemoteOpenResult.CHUNK_BUDGET_EXHAUSTED);
        Resource resource = new Resource(next, world, ticket, unloadedWorlds);
        lease.reservation.attach(next, resource); // Attach before force/load callbacks: partial failures must release it.
        ticket.setChunkListDepth(1);
        if (ticket.getChunkListDepth() < 1) throw new Failure(RemoteOpenResult.CHUNK_BUDGET_EXHAUSTED);
        ForgeChunkManager.forceChunk(ticket, new ChunkPos(next.x, next.z));
        if (!lease.reservation.current() || unloadedWorlds.contains(world) || DimensionManager.getWorld(next.dimension) != world
                || !ticket.getChunkList().contains(new ChunkPos(next.x, next.z))) throw new Failure(RemoteOpenResult.UNAVAILABLE);
        loaded = world.getChunkProvider().getLoadedChunk(next.x, next.z);
        if (loaded == null && world.getChunkProvider().isChunkGeneratedAt(next.x, next.z))
            loaded = world.getChunkProvider().loadChunk(next.x, next.z);
        // Never fall back to provideChunk: missing terrain must not be generated by remote trading.
        resource.chunk = loaded;
        if (!resource.live() || !lease.reservation.current()) throw new Failure(RemoteOpenResult.UNAVAILABLE);
        return lease.live();
    }

    private void bind(Lease lease, WorldServer world) throws IOException, Failure {
        if (world == null || unloadedWorlds.contains(world) || world.getMinecraftServer() != server
                || !world.getChunkSaveLocation().getCanonicalFile().toPath().equals(lease.folder)
                || !world.provider.getClass().getName().equals(lease.provider)) throw new Failure(RemoteOpenResult.UNAVAILABLE);
        lease.world = world;
    }

    public void release(Lease lease) {
        if (!handles.remove(lease)) return;
        lease.reservation.close();
        targets.computeIfPresent(lease.target, (key, count) -> count > 1 ? count - 1 : null);
    }
    public void worldUnloaded(World world) {
        // Forge's HIGHEST-priority unload handler already discarded its ticket map.
        unloadedWorlds.add(world);
        pool.discard(key -> key.dimension == world.provider.getDimension());
    }
    public void shutdown() {
        for (Lease lease : new ArrayList<>(handles)) release(lease);
        pool.shutdown();
        targets.clear(); manifest = null; providers = Collections.emptyMap();
    }

    public static final class Failure extends Exception {
        public final RemoteOpenResult result;
        Failure(RemoteOpenResult result) { super(result.name()); this.result = result; }
    }

    public static final class Lease {
        private final Key target;
        private final Set<Key> keys;
        private final Path folder;
        private final String provider;
        private final SharedLeasePool<Key, Resource>.Reservation reservation;
        public WorldServer world;
        private Lease(Key target, Set<Key> keys, Path folder, String provider, SharedLeasePool<Key, Resource>.Reservation reservation) {
            this.target = target; this.keys = keys; this.folder = folder; this.provider = provider; this.reservation = reservation;
        }
        public boolean live() {
            if (world == null || !reservation.current()) return false;
            for (Key key : keys) {
                Resource resource = reservation.get(key);
                if (resource == null || resource.world != world || !resource.live()) return false;
            }
            return true;
        }
        public Chunk chunk() {
            Resource resource = reservation.get(target);
            return resource == null ? null : resource.chunk;
        }
    }
    private static final class Resource {
        final Key key;
        final WorldServer world;
        final ForgeChunkManager.Ticket ticket;
        final Set<World> unloadedWorlds;
        Chunk chunk;
        boolean valid = true, released;
        Resource(Key key, WorldServer world, ForgeChunkManager.Ticket ticket, Set<World> unloadedWorlds) {
            this.key = key; this.world = world; this.ticket = ticket; this.unloadedWorlds = unloadedWorlds;
        }
        boolean live() {
            return valid && !unloadedWorlds.contains(world) && DimensionManager.getWorld(key.dimension) == world
                    && chunk != null && chunk.isLoaded() && world.getChunkProvider().getLoadedChunk(key.x, key.z) == chunk
                    && ticket.getChunkList().contains(new ChunkPos(key.x, key.z));
        }
        boolean releaseTicket() {
            if (released) return true;
            valid = false;
            if (!unloadedWorlds.contains(world) && DimensionManager.getWorld(key.dimension) == world) {
                try { ForgeChunkManager.releaseTicket(ticket); }
                catch (RuntimeException | LinkageError ex) {
                    LogManager.getLogger(BuildInfo.MOD_ID).error("Forge rejected remote ticket release; physical chunk quota remains reserved until teardown", ex);
                    return false;
                }
            }
            released = true;
            return true;
        }
    }
    private static final class Key {
        final int dimension, x, z;
        Key(int dimension, int x, int z) { this.dimension = dimension; this.x = x; this.z = z; }
        @Override public int hashCode() { return Objects.hash(dimension, x, z); }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key k = (Key) other; return dimension == k.dimension && x == k.x && z == k.z;
        }
    }
}
