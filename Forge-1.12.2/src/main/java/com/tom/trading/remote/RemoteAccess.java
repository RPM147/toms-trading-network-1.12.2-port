package com.tom.trading.remote;

import com.tom.trading.BuildInfo;
import com.tom.trading.tile.TileVendingMachine;
import com.tom.trading.trade.TradeResultCode;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Does not post fake interaction events or move/impersonate a player. */
public final class RemoteAccess implements RemoteProtection {
    private final List<RemoteProtection> policies = new ArrayList<>();
    private long lastFailureLog;
    private boolean failureLogged;
    public RemoteAccess() {
        // This exact BETA-named JAR identifies itself as ALPHA in both @Mod and mcmod.info.
        optional("minecolonies", "1.12.2-0.11.841-ALPHA", Colonies::create);
        optional("ebwizardry", "4.3.19", Wizardry::create);
    }
    private void optional(String mod, String version, Supplier<RemoteProtection> factory) {
        ModContainer loaded = Loader.instance().getIndexedModList().get(mod);
        if (loaded == null) return;
        try {
            if (version.equals(loaded.getVersion())) { policies.add(factory.get()); return; }
        } catch (RuntimeException | LinkageError ex) {
            LogManager.getLogger(BuildInfo.MOD_ID).error("Remote protection adapter could not initialize: {}", mod, ex);
            policies.add((player, target) -> TradeResultCode.PROTECTION_ADAPTER_ERROR);
            return;
        }
        LogManager.getLogger(BuildInfo.MOD_ID).error("Remote trading unavailable: unsupported protection {} {}", mod, loaded.getVersion());
        policies.add((player, target) -> TradeResultCode.PROTECTION_VERSION_UNSUPPORTED);
    }
    @Override public boolean needsTargetNeighborhood() {
        for (RemoteProtection policy : policies) if (policy.needsTargetNeighborhood()) return true;
        return false;
    }
    @Override public TradeResultCode check(EntityPlayerMP player, TileVendingMachine target) {
        try {
            if (!(target.getWorld() instanceof WorldServer) || player.hasDisconnected()
                    || !player.isEntityAlive() || player.isSpectator()) return TradeResultCode.REMOTE_UNAVAILABLE;
            WorldServer world = (WorldServer) target.getWorld();
            if (world.getMinecraftServer() != player.getServer()
                    || !world.getWorldBorder().contains(target.getPos())) return TradeResultCode.ACCESS_DENIED;
            if (player.getServer().isBlockProtected(world, target.getPos(), player)) return TradeResultCode.ACCESS_DENIED;
            for (RemoteProtection policy : policies) {
                TradeResultCode result = policy.check(player, target);
                if (result != null) return result;
            }
            return null;
        } catch (RuntimeException | LinkageError ex) {
            long now = System.nanoTime();
            if (!failureLogged || now - lastFailureLog >= 10_000_000_000L) {
                failureLogged = true; lastFailureLog = now;
                LogManager.getLogger(BuildInfo.MOD_ID).warn("Remote protection query failed at {}; refusing access (log throttled to 10 seconds)", target.getPos(), ex);
            }
            return TradeResultCode.PROTECTION_ADAPTER_ERROR;
        }
    }
    private static final class Colonies {
        static RemoteProtection create() { return new com.tom.trading.compat.remote.MineColoniesAccess(); }
    }
    private static final class Wizardry {
        static RemoteProtection create() { return new com.tom.trading.compat.remote.WizardryAccess(); }
    }
}
