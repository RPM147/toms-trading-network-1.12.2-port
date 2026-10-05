package com.tom.trading.proxy;

import com.tom.trading.Content;
import com.tom.trading.compat.StackLimits;
import com.tom.trading.TradingNetworkMod;
import com.tom.trading.container.ContainerMachine;
import com.tom.trading.container.MachineGuiHandler;
import com.tom.trading.network.MachineNetwork;
import com.tom.trading.directory.DirectoryPage;
import net.minecraft.network.INetHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {
    public void preInit(FMLPreInitializationEvent event) {
        Content.registerTileEntity();
        MachineNetwork.register();
        NetworkRegistry.INSTANCE.registerGuiHandler(TradingNetworkMod.instance, new MachineGuiHandler());
    }

    public void init(FMLInitializationEvent event) {
        if (Loader.isModLoaded("theoneprobe")) {
            FMLInterModComms.sendFunctionMessage("theoneprobe", "getTheOneProbe",
                    "com.tom.trading.compat.top.TradingProbeProvider");
        }
    }

    public Object createMachineGui(ContainerMachine container) { return null; }

    public void receiveState(MachineNetwork.State state) {}

    public void receiveFeedback(MachineNetwork.Feedback feedback) {}

    public void receiveDirectoryPage(DirectoryPage page, INetHandler connection) {}
    public void openTradeBook(net.minecraft.util.EnumHand hand) {}
    public void clearTradeBooks() {}
    public void receiveTradeBook(com.tom.trading.network.TradeBookNetwork.Opened message, INetHandler connection) {}

    public void receiveRemoteOpen(com.tom.trading.network.RemoteNetwork.Opened message, INetHandler connection) {}
    public void receiveRemoteState(com.tom.trading.network.RemoteNetwork.State message, INetHandler connection) {}
    public void receiveRemoteFeedback(com.tom.trading.network.RemoteNetwork.Feedback message, INetHandler connection) {}

    public void postInit(FMLPostInitializationEvent event) {
        StackLimits.discover();
    }
}
