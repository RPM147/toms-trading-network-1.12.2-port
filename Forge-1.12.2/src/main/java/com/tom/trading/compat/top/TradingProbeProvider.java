package com.tom.trading.compat.top;

import com.tom.trading.BuildInfo;
import com.tom.trading.tile.TileVendingMachine;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.function.Function;

/** Instantiated by TOP's supported IMC function route, only when TOP is loaded. */
public final class TradingProbeProvider implements Function<ITheOneProbe, Void>, IProbeInfoProvider {
    @Override public Void apply(ITheOneProbe probe) { probe.registerProvider(this); return null; }
    @Override public String getID() { return BuildInfo.MOD_ID + ":vending_machine"; }

    private static String label(String key) {
        return IProbeInfo.STARTLOC + "probe." + BuildInfo.MOD_ID + "." + key + IProbeInfo.ENDLOC;
    }

    @Override public void addProbeInfo(ProbeMode mode, IProbeInfo info, EntityPlayer player,
                                       World world, IBlockState state, IProbeHitData hit) {
        if (world.isRemote || !world.isBlockLoaded(hit.getPos())) return;
        TileEntity raw = world.getTileEntity(hit.getPos());
        if (!(raw instanceof TileVendingMachine)) return;
        TileVendingMachine tile = (TileVendingMachine) raw;
        if (!tile.isDataVersionSupported() || tile.isAutomationQuarantined()) {
            info.text(label("unavailable"));
            return;
        }
        info.text(label("owner") + ": " + (tile.getOwnerNameCache() == null ? "-" : tile.getOwnerNameCache()));
        if (tile.isCreativeMode()) info.text(label("creative"));
        for (int group = 0; group < 2; group++) {
            info.text(label(group == 0 ? "payment" : "sale"));
            for (int slot = 0; slot < 4; slot++) {
                ItemStack stack = group == 0 ? tile.getPaymentTemplate(slot) : tile.getSaleTemplate(slot);
                if (!stack.isEmpty()) {
                    // TOP's own ItemStack transport never receives a 1024-count stack.
                    stack.setCount(1);
                    info.horizontal().item(stack).text("x " + (group == 0
                            ? tile.getPaymentQuantity(slot) : tile.getSaleQuantity(slot)));
                }
            }
        }
        if (tile.canConfigure(player)) {
            int stock = 0, earnings = 0;
            for (int slot = 0; slot < 8; slot++) {
                stock += tile.getSaleStock().getStackInSlot(slot).getCount();
                earnings += tile.getEarnings().getStackInSlot(slot).getCount();
            }
            info.text(label("stock_items") + ": " + stock);
            info.text(label("earned_items") + ": " + earnings);
        }
    }
}
