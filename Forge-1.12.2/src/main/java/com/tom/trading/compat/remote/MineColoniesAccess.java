package com.tom.trading.compat.remote;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyTagCapability;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.configuration.Configurations;
import com.minecolonies.coremod.MineColonies;
import com.minecolonies.coremod.colony.Colony;
import com.tom.trading.remote.RemoteProtection;
import com.tom.trading.tile.TileVendingMachine;
import com.tom.trading.trade.TradeResultCode;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/** Isolated exact-0.11.841 policy, queried only after loading the actual target. */
public final class MineColoniesAccess implements RemoteProtection {
    @Override public TradeResultCode check(EntityPlayerMP player, TileVendingMachine target) {
        if (!Configurations.gameplay.enableColonyProtection) return null;
        WorldServer world = (WorldServer) target.getWorld();
        BlockPos pos = target.getPos();
        Chunk chunk = world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !chunk.isLoaded() || MineColonies.CLOSE_COLONY_CAP == null) return TradeResultCode.PROTECTION_CLAIM_UNAVAILABLE;
        IColonyTagCapability claim = chunk.getCapability(MineColonies.CLOSE_COLONY_CAP, null);
        if (claim == null) return TradeResultCode.PROTECTION_CLAIM_UNAVAILABLE;
        int id = claim.getOwningColony();
        if (id == 0) return null;
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(world, pos);
        if (!(colony instanceof Colony) || colony.getID() != id || colony.getDimension() != world.provider.getDimension()
                || !colony.isCoordInColony(world, pos)) return TradeResultCode.PROTECTION_CLAIM_UNAVAILABLE;
        IPermissions permissions = colony.getPermissions();
        if (permissions == null) return TradeResultCode.PROTECTION_CLAIM_UNAVAILABLE;
        Colony concrete = (Colony) colony;
        boolean free = concrete.getFreePositions().contains(pos) || concrete.getFreeBlocks().contains(chunk.getBlockState(pos).getBlock());
        if (free && permissions.hasPermission(player, Action.ACCESS_FREE_BLOCKS)) return null;
        return permissions.hasPermission(player, Action.RIGHTCLICK_BLOCK)
                && permissions.hasPermission(player, Action.OPEN_CONTAINER)
                && permissions.hasPermission(player, Action.RIGHTCLICK_ENTITY) ? null : TradeResultCode.ACCESS_DENIED;
    }
}
