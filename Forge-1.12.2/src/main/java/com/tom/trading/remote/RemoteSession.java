package com.tom.trading.remote;

import com.tom.trading.block.BlockVendingMachine;
import com.tom.trading.container.ContainerRemoteTrading;
import com.tom.trading.directory.MachineDirectory;
import com.tom.trading.directory.MachineDirectoryData;
import com.tom.trading.tile.TileVendingMachine;
import com.tom.trading.trade.TradeResultCode;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/** Bound to actual objects, never owner labels or client world lookups. */
public final class RemoteSession {
    public final EntityPlayerMP player;
    public final RemoteOpenRequest opening;
    public final RemoteContext context;
    public final TileVendingMachine tile;
    public final ChunkLeaseManager.Lease lease;
    private final RemoteTrading service;
    private final World playerWorld;
    private final SessionLifetime lifetime;
    private boolean closed;
    public ContainerRemoteTrading container;

    RemoteSession(RemoteTrading service, EntityPlayerMP player, RemoteOpenRequest opening,
                  RemoteContext context, TileVendingMachine tile, ChunkLeaseManager.Lease lease) {
        this.service = service; this.player = player; this.opening = opening; this.context = context;
        this.tile = tile; this.lease = lease; this.playerWorld = player.world;
        lifetime = new SessionLifetime(System.nanoTime(), RemoteSettings.idleNanos(), RemoteSettings.absoluteNanos());
    }
    public boolean live() {
        long now = System.nanoTime();
        if (closed || !RemoteSettings.remoteTrading || player.hasDisconnected() || !player.isEntityAlive() || player.isSpectator()
                || player.world != playerWorld || player.dimension != context.playerDimension
                || lifetime.expired(now)
                || !lease.live() || tile.isInvalid() || tile.getWorld() != lease.world
                || !context.address.pos().equals(tile.getPos()) || !context.machineId.equals(tile.getMachineUuid())
                || !tile.isDataVersionSupported() || tile.isAutomationQuarantined() || tile.isAutomationRunning()) return false;
        Chunk chunk = lease.chunk();
        if (chunk.getTileEntity(context.address.pos(), Chunk.EnumCreateEntityType.CHECK) != tile
                || !(chunk.getBlockState(context.address.pos()).getBlock() instanceof BlockVendingMachine)) return false;
        MachineDirectoryData data = MachineDirectory.get(lease.world);
        return data != null && data.isSupported() && context.worldId.equals(data.getWorldId())
                && data.isUniqueIdentity(context.address, context.machineId);
    }
    public boolean current() {
        return container != null && player.openContainer == container && container.windowId == context.window && live();
    }
    public TradeResultCode access() {
        if (!current()) return TradeResultCode.REMOTE_UNAVAILABLE;
        TradeResultCode denied = service.access.check(player, tile);
        return denied != null ? denied : current() ? null : TradeResultCode.REMOTE_UNAVAILABLE;
    }
    /** Only a new, authorized purchase attempt is activity. Polls, retries and slot spam are not. */
    public void activity() { lifetime.activity(System.nanoTime()); }
    public void close() {
        if (closed) return;
        closed = true;
        service.closed(this);
    }
    public void end() { service.end(this); }
}
