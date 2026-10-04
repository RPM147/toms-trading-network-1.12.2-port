package com.tom.trading.compat.remote;

import com.tom.trading.remote.RemoteProtection;
import com.tom.trading.remote.ProtectionCoverage;
import com.tom.trading.tile.TileVendingMachine;
import com.tom.trading.trade.TradeResultCode;
import electroblob.wizardry.Wizardry;
import electroblob.wizardry.entity.construct.EntityForcefield;
import electroblob.wizardry.registry.WizardryPotions;
import electroblob.wizardry.spell.Possession;
import electroblob.wizardry.util.AllyDesignationSystem;
import electroblob.wizardry.util.EntityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** Public 4.3.19 API/state only; no private-handler reflection or synthetic clicks. */
public final class WizardryAccess implements RemoteProtection {
    @Override public boolean needsTargetNeighborhood() { return true; }
    @Override public TradeResultCode check(EntityPlayerMP player, TileVendingMachine target) {
        if (player.isPotionActive(WizardryPotions.transience) || Possession.getPossessee(player) != null)
            return TradeResultCode.ACCESS_DENIED;
        World world = target.getWorld();
        NBTTagCompound data = target.getTileData();
        boolean bypass = player.capabilities.isCreativeMode && (Wizardry.settings.creativeBypassesArcaneLock
                || EntityUtils.isPlayerOp(player, player.getServer()));
        if (data.hasUniqueId("arcaneLockOwner") && !bypass) {
            EntityPlayer owner = world.getPlayerEntityByUUID(data.getUniqueId("arcaneLockOwner"));
            if (owner == null || (owner != player && !AllyDesignationSystem.isPlayerAlly(owner, player)))
                return TradeResultCode.ACCESS_DENIED;
        }
        Vec3d center = new Vec3d(target.getPos().getX() + 0.5, target.getPos().getY() + 0.5, target.getPos().getZ() + 0.5);
        if (!covered(world, center)) return TradeResultCode.PROTECTION_TARGET_UNLOADED;
        if (!covered(player.world, player.getPositionVector())) return TradeResultCode.PROTECTION_PLAYER_UNLOADED;
        return surrounding(world, center) == surrounding(player.world, player.getPositionVector())
                ? null : TradeResultCode.ACCESS_DENIED;
    }
    private static boolean covered(World world, Vec3d point) {
        WorldServer serverWorld = (WorldServer) world;
        return ProtectionCoverage.loaded(point.x, point.z, World.MAX_ENTITY_RADIUS, (x, z) -> {
            net.minecraft.world.chunk.Chunk chunk = serverWorld.getChunkProvider().getLoadedChunk(x, z);
            return chunk != null && chunk.isLoaded();
        });
    }
    private static EntityForcefield surrounding(World world, Vec3d point) {
        EntityForcefield nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (EntityForcefield field : EntityUtils.getEntitiesWithinRadius(20, point.x, point.y, point.z, world, EntityForcefield.class)) {
            // Keep inherited MC calls owned by vanilla classes for legacy SRG reobfuscation.
            double current = point.squareDistanceTo(((net.minecraft.entity.Entity) field).getPositionVector());
            if (field.contains(point) && current < distance) { nearest = field; distance = current; }
        }
        return nearest;
    }
}
