package com.tom.trading.remote;

import com.tom.trading.BuildInfo;
import com.tom.trading.block.BlockVendingMachine;
import com.tom.trading.container.ContainerRemoteTrading;
import com.tom.trading.directory.*;
import com.tom.trading.network.RemoteNetwork;
import com.tom.trading.tile.TileVendingMachine;
import com.tom.trading.trade.TradeResultCode;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import org.apache.logging.log4j.LogManager;
import java.util.*;

/** Per-server lifecycle, not a cross-save singleton catalogue. Every method is server-thread-only. */
public final class RemoteTrading {
    private static RemoteTrading running;
    private final MinecraftServer server;
    private final ChunkLeaseManager leases;
    final RemoteProtection access;
    private final Map<EntityPlayerMP, Pending> pending = new LinkedHashMap<>();
    private final Map<EntityPlayerMP, RemoteSession> sessions = new IdentityHashMap<>();
    private final ChunkLoadBudget loadBudget = new ChunkLoadBudget();
    private boolean stopped;

    private RemoteTrading(MinecraftServer server) {
        this.server = server; leases = new ChunkLeaseManager(server); access = new RemoteAccess();
    }
    public static void start(MinecraftServer server) {
        if (running != null) running.stop();
        running = new RemoteTrading(server);
    }
    public static RemoteTrading get(MinecraftServer server) {
        return running != null && running.server == server && !running.stopped ? running : null;
    }
    public static void stopServer() {
        if (running != null) { running.stop(); running = null; }
    }
    public static void serverTick() { if (running != null && !running.stopped) running.tick(); }
    public static void unloaded(World world) {
        if (running != null && world.getMinecraftServer() == running.server) running.worldUnloaded(world);
    }
    public static void playerGone(EntityPlayerMP player) {
        RemoteTrading service = get(player.getServer());
        if (service != null) service.disconnect(player);
    }

    private static boolean ready(EntityPlayerMP player, RemoteOpenRequest request) {
        return !(player instanceof FakePlayer) && !player.hasDisconnected() && player.isEntityAlive() && !player.isSpectator()
                && player.dimension == request.playerDimension && player.openContainer == player.inventoryContainer;
    }
    public void request(EntityPlayerMP player, RemoteOpenRequest request) {
        if (!ready(player, request) || sessions.containsKey(player)) return;
        if (!RemoteSettings.remoteTrading) { reply(player, request, RemoteOpenResult.DISABLED); return; }
        if (!pending.containsKey(player) && pending.size() >= 64) { reply(player, request, RemoteOpenResult.BUSY); return; }
        Pending previous = pending.get(player);
        if (previous != null) drop(previous);
        pending.put(player, new Pending(player, request));
    }
    public void cancel(EntityPlayerMP player, RemoteOpenRequest request) {
        Pending queued = pending.get(player);
        if (queued != null && request.matches(queued.request)) drop(queued);
        RemoteSession session = sessions.get(player);
        if (session != null && request.matches(session.opening)) closeScreen(session);
    }
    private void tick() {
        loadBudget.beginTick();
        for (RemoteSession session : new ArrayList<>(sessions.values()))
            if (!session.current()) closeScreen(session);
        // At most 64 jobs; four new reservations and four physical chunk preparations per tick.
        int admitted = 0, opened = 0;
        for (Pending job : new ArrayList<>(pending.values())) {
            if (pending.get(job.player) != job) continue;
            if (!current(job) || System.nanoTime() - job.created >= 10_000_000_000L) {
                boolean canReply = current(job);
                drop(job);
                if (canReply) reply(job.player, job.request, job.timeoutResult);
                continue;
            }
            if (opened >= 4) continue;
            if (job.lease == null && admitted++ >= 4) continue;
            int before = loadBudget.operations();
            boolean resolved = prepare(job);
            if (resolved) opened++;
            // Only jobs that consumed work move back: a blocked cold job cannot starve the next one.
            if ((resolved || loadBudget.operations() != before) && pending.get(job.player) == job) {
                pending.remove(job.player);
                pending.put(job.player, job);
            }
        }
    }

    private boolean current(Pending job) {
        return !stopped && pending.get(job.player) == job && job.player.world == job.playerWorld && ready(job.player, job.request);
    }

    private boolean prepare(Pending job) {
        try {
            RemoteOpenRequest request = job.request;
            if (!RemoteSettings.remoteTrading) { finish(job, RemoteOpenResult.DISABLED); return false; }
            MachineDirectoryData data = MachineDirectory.get(job.player.world);
            if (data == null || !data.isSupported()) { finish(job, RemoteOpenResult.UNAVAILABLE); return false; }
            MachineDirectoryEntry entry = data.get(request.address);
            // Initial selection must match the page. Later unrelated catalogue changes must not cancel a staged load.
            if (!data.getWorldId().equals(request.worldId) || (job.lease == null && data.getRevision() != request.revision)
                    || entry == null || !Objects.equals(entry.machineId, request.machineId)) {
                finish(job, RemoteOpenResult.STALE); return false;
            }
            if (entry.evidence == MachineDirectoryEntry.Evidence.UNSUPPORTED
                    || (request.machineId != null && data.hasIdentityConflict(request.machineId))) {
                finish(job, RemoteOpenResult.UNAVAILABLE); return false;
            }
            if (job.lease == null) job.lease = leases.reserve(request.address, data, access.needsTargetNeighborhood());
            if (!leases.advance(job.lease, loadBudget)) return false;
            if (!current(job)) { drop(job); return false; }
            if (System.nanoTime() - job.created >= 10_000_000_000L) { finish(job, job.timeoutResult); return false; }
            open(job, data);
            return true;
        } catch (ChunkLeaseManager.Failure ex) {
            finish(job, ex.result);
        } catch (Exception | LinkageError ex) {
            LogManager.getLogger(BuildInfo.MOD_ID).warn("Remote region could not be prepared: {}", job.request.address, ex);
            finish(job, RemoteOpenResult.UNAVAILABLE);
        }
        return false;
    }

    private void open(Pending job, MachineDirectoryData data) {
        EntityPlayerMP player = job.player;
        RemoteOpenRequest request = job.request;
        ChunkLeaseManager.Lease lease = job.lease;
        RemoteSession session = null;
        boolean waiting = false;
        try {
            if (!current(job)) return;
            if (!RemoteSettings.remoteTrading) { reply(player, request, RemoteOpenResult.DISABLED); return; }
            Chunk chunk = lease.chunk();
            TileEntity found = chunk.getTileEntity(request.address.pos(), Chunk.EnumCreateEntityType.CHECK);
            if (!(found instanceof TileVendingMachine) || !(chunk.getBlockState(request.address.pos()).getBlock() instanceof BlockVendingMachine)) {
                data.enqueue(request.address);
                reply(player, request, RemoteOpenResult.UNAVAILABLE); return;
            }
            TileVendingMachine tile = (TileVendingMachine) found;
            MachineDirectory.observe(tile);
            // A legacy hint may be resolved here, but it must be selected again with its new identity.
            if (request.machineId == null || !request.machineId.equals(tile.getMachineUuid())) {
                reply(player, request, RemoteOpenResult.STALE); return;
            }
            if (!current(job) || !lease.live() || tile.isInvalid() || !tile.isDataVersionSupported()
                    || tile.isAutomationRunning() || tile.isAutomationQuarantined()
                    || !data.isUniqueIdentity(request.address, request.machineId)) {
                reply(player, request, RemoteOpenResult.UNAVAILABLE); return;
            }
            TradeResultCode denied = access.check(player, tile);
            if (denied != null) {
                if (denied == TradeResultCode.PROTECTION_PLAYER_UNLOADED) {
                    // Following teleport, let ordinary player loading catch up. Never force the player's surroundings.
                    job.timeoutResult = RemoteOpenResult.PROTECTION_PLAYER_UNLOADED;
                    waiting = current(job);
                    return;
                }
                reply(player, request, RemoteOpenResult.denied(denied)); return;
            }
            if (!current(job)) return;
            player.getNextWindowId();
            RemoteContext context = new RemoteContext(player.currentWindowId, player.dimension, UUID.randomUUID(),
                    data.getWorldId(), request.machineId, request.address);
            session = new RemoteSession(this, player, request, context, tile, lease);
            ContainerRemoteTrading container = new ContainerRemoteTrading(player, context, session);
            session.container = container;
            if (!session.live()) { reply(player, request, RemoteOpenResult.UNAVAILABLE); return; }
            player.closeContainer();
            if (!current(job)) return;
            // Custom correlated open response precedes vanilla inventory sync, preserving packet order.
            player.openContainer = container;
            sessions.put(player, session);
            job.lease = null; // Ownership transferred; pending cancellation must not release the active session.
            pending.remove(player, job);
            RemoteNetwork.opened(player, request, context);
            container.addListener(player);
            MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(player, container));
            if (!session.current()) closeScreen(session);
        } catch (Exception | LinkageError ex) {
            LogManager.getLogger(BuildInfo.MOD_ID).warn("Remote target could not be opened: {}", request.address, ex);
            if (session != null && sessions.get(player) == session) closeScreen(session);
            reply(player, request, RemoteOpenResult.UNAVAILABLE);
        } finally {
            if (!waiting) drop(job);
        }
    }
    private void finish(Pending job, RemoteOpenResult result) {
        drop(job);
        reply(job.player, job.request, result);
    }
    private void drop(Pending job) {
        pending.remove(job.player, job);
        ChunkLeaseManager.Lease lease = job.lease;
        job.lease = null;
        if (lease != null) leases.release(lease);
    }
    private void reply(EntityPlayerMP player, RemoteOpenRequest request, RemoteOpenResult result) {
        if (!player.hasDisconnected()) RemoteNetwork.openResult(player, request, result);
    }
    private void closeScreen(RemoteSession session) {
        try {
            if (session.player.openContainer == session.container) session.player.closeScreen();
        } catch (RuntimeException | LinkageError ex) {
            LogManager.getLogger(BuildInfo.MOD_ID).warn("Remote screen cleanup failed; invalidating session", ex);
        } finally { session.close(); }
    }
    void end(RemoteSession session) { closeScreen(session); }
    void closed(RemoteSession session) {
        if (sessions.get(session.player) == session) {
            sessions.remove(session.player);
            leases.release(session.lease);
        }
    }
    private void disconnect(EntityPlayerMP player) {
        Pending job = pending.get(player);
        if (job != null) drop(job);
        RemoteSession session = sessions.get(player);
        if (session != null) closeScreen(session);
    }
    private void worldUnloaded(World world) {
        leases.worldUnloaded(world); // Must happen before close invokes release.
        for (RemoteSession session : new ArrayList<>(sessions.values()))
            if (session.lease.world == world || session.player.world == world) closeScreen(session);
        for (Pending job : new ArrayList<>(pending.values()))
            if (job.playerWorld == world || job.request.address.dimension == world.provider.getDimension()) drop(job);
        if (world.provider.getDimension() == 0) { stop(); running = null; }
    }
    private void stop() {
        stopped = true;
        for (Pending job : new ArrayList<>(pending.values())) drop(job);
        try { for (RemoteSession session : new ArrayList<>(sessions.values())) closeScreen(session); }
        finally { sessions.clear(); leases.shutdown(); }
    }
    private static final class Pending {
        final EntityPlayerMP player;
        final RemoteOpenRequest request;
        final World playerWorld;
        final long created = System.nanoTime();
        ChunkLeaseManager.Lease lease;
        RemoteOpenResult timeoutResult = RemoteOpenResult.LOAD_TIMEOUT;
        Pending(EntityPlayerMP player, RemoteOpenRequest request) {
            this.player = player; this.request = request; this.playerWorld = player.world;
        }
    }
}
