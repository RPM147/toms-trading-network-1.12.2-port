package com.tom.trading.remote;

import com.tom.trading.tile.TileVendingMachine;
import com.tom.trading.trade.TradeResultCode;
import net.minecraft.entity.player.EntityPlayerMP;

/** Null means permitted. A missing/unsupported decision is never a grant. */
public interface RemoteProtection {
    TradeResultCode check(EntityPlayerMP player, TileVendingMachine target);
    default boolean needsTargetNeighborhood() { return false; }
}
