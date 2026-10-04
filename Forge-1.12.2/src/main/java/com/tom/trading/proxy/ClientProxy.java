package com.tom.trading.proxy;

import com.tom.trading.client.GuiMachine;
import com.tom.trading.client.GuiMachineDirectory;
import com.tom.trading.client.DirectoryKeybind;
import com.tom.trading.directory.DirectoryPage;
import net.minecraft.network.INetHandler;
import com.tom.trading.container.ContainerMachine;
import com.tom.trading.container.ContainerRemoteTrading;
import com.tom.trading.network.RemoteNetwork;
import com.tom.trading.remote.RemoteOpenResult;
import com.tom.trading.container.MachineView;
import com.tom.trading.network.MachineNetwork;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {
    @Override
    public Object createMachineGui(ContainerMachine container) {
        return new GuiMachine(container);
    }

    @Override
    public void receiveState(MachineNetwork.State message) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null || !(mc.player.openContainer instanceof ContainerMachine)) return;
            ContainerMachine container = (ContainerMachine) mc.player.openContainer;
            if (!message.context.matches(container, false) || container.configuration != message.configuration
                    || (container.session != 0 && message.context.session != container.session)) return;
            container.session = message.context.session;
            container.view = MachineView.read(message.data);
            container.viewRevision++;
        });
    }

    @Override
    public void receiveFeedback(MachineNetwork.Feedback message) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null || !(mc.player.openContainer instanceof ContainerMachine)) return;
            ContainerMachine container = (ContainerMachine) mc.player.openContainer;
            if (!message.context.matches(container, true)) return;
            if (message.requestId > 0 && !container.tradeRequests.complete(message.requestId,
                    message.retryAfterMillis, System.nanoTime())) return;
            container.completedTrades = message.completed;
            container.feedbackKey = "gui.toms_trading_network.result." + message.code.name().toLowerCase(Locale.ROOT);
        });
    }

    @Override
    public void receiveDirectoryPage(DirectoryPage page, INetHandler connection) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getConnection() != connection || mc.player == null || mc.world == null
                    || mc.player.dimension != page.query.playerDimension || !(mc.currentScreen instanceof GuiMachineDirectory)) return;
            ((GuiMachineDirectory) mc.currentScreen).receive(page);
        });
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        DirectoryKeybind.register();

        Minecraft minecraft = Minecraft.getMinecraft();
        event.getModLog().info(
                "Client proxy active; initial display {}x{}",
                minecraft.displayWidth,
                minecraft.displayHeight
        );
    }

    @Override public void receiveRemoteOpen(RemoteNetwork.Opened message, INetHandler connection) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getConnection() != connection) return;
            boolean eligible = mc.player != null && mc.world != null && mc.player.isEntityAlive() && !mc.player.isSpectator()
                    && mc.player.dimension == message.request.playerDimension && mc.player.openContainer == mc.player.inventoryContainer;
            if (eligible && mc.currentScreen instanceof GuiMachineDirectory
                    && ((GuiMachineDirectory) mc.currentScreen).receiveOpen(message)) return;
            if (message.result == RemoteOpenResult.OPENED) RemoteNetwork.cancel(message.request);
        });
    }
    @Override public void receiveRemoteState(RemoteNetwork.State message, INetHandler connection) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getConnection() != connection || mc.player == null || !(mc.player.openContainer instanceof ContainerRemoteTrading)) return;
            ContainerRemoteTrading container = (ContainerRemoteTrading) mc.player.openContainer;
            if (message.context.matches(container.context) && mc.player.dimension == container.context.playerDimension)
                container.view = MachineView.read(message.data);
        });
    }
    @Override public void receiveRemoteFeedback(RemoteNetwork.Feedback message, INetHandler connection) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getConnection() != connection || mc.player == null || !(mc.player.openContainer instanceof ContainerRemoteTrading)) return;
            ContainerRemoteTrading container = (ContainerRemoteTrading) mc.player.openContainer;
            if (!message.context.matches(container.context) || mc.player.dimension != container.context.playerDimension
                    || !container.tradeRequests.complete(message.requestId, message.retryAfterMillis, System.nanoTime())) return;
            container.completedTrades = message.completed;
            container.feedbackKey = "gui.toms_trading_network.result." + message.code.name().toLowerCase(Locale.ROOT);
        });
    }
}
