package com.tom.trading.container;

import com.tom.trading.TradingNetworkMod;
import com.tom.trading.tile.TileVendingMachine;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public final class MachineGuiHandler implements IGuiHandler {
    public static final int CONFIG = 0;
    public static final int TRADING = 1;

    public static ContainerMachine create(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != CONFIG && id != TRADING) return null;
        BlockPos pos = new BlockPos(x, y, z);
        if (!world.isBlockLoaded(pos)) return null;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileVendingMachine)) return null;
        if (id == CONFIG && !world.isRemote && !((TileVendingMachine) tile).canConfigure(player)) return null;
        ContainerMachine container = id == CONFIG
                ? new ContainerMachineConfig(player, (TileVendingMachine) tile)
                : new ContainerMachineTrading(player, (TileVendingMachine) tile);
        return world.isRemote || container.canInteractWith(player) ? container : null;
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return create(id, player, world, x, y, z);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        ContainerMachine container = create(id, player, world, x, y, z);
        return container == null ? null : TradingNetworkMod.proxy.createMachineGui(container);
    }
}
