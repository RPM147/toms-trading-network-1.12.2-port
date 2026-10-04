package com.tom.trading;

import com.tom.trading.proxy.CommonProxy;
import com.tom.trading.directory.DirectoryCommand;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.relauncher.Side;
import java.util.Map;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = BuildInfo.MOD_ID,
        name = BuildInfo.NAME,
        version = BuildInfo.VERSION,
        acceptedMinecraftVersions = "[" + BuildInfo.MINECRAFT_VERSION + "]",
        dependencies = "required-after:forge@[" + BuildInfo.FORGE_VERSION + ",)"
)
public final class TradingNetworkMod {
    @Mod.Instance(BuildInfo.MOD_ID)
    public static TradingNetworkMod instance;

    public static final CreativeTabs CREATIVE_TAB = new CreativeTabs(BuildInfo.MOD_ID) {
        @Override
        public ItemStack createIcon() {
            return Content.VENDING_MACHINE_ITEM == null
                    ? new ItemStack(Blocks.CHEST)
                    : new ItemStack(Content.VENDING_MACHINE_ITEM);
        }
    };

    @SidedProxy(
            clientSide = "com.tom.trading.proxy.ClientProxy",
            serverSide = "com.tom.trading.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    private static Logger logger;

    @NetworkCheckHandler
    public boolean acceptsPeer(Map<String, String> remoteMods, Side remoteSide) {
        return BuildInfo.VERSION.equals(remoteMods.get(BuildInfo.MOD_ID));
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        proxy.preInit(event);
        logger.info(
                "{} {} bootstrap: Minecraft {}, Forge target {}, side {}",
                BuildInfo.NAME,
                BuildInfo.VERSION,
                BuildInfo.MINECRAFT_VERSION,
                BuildInfo.FORGE_VERSION,
                event.getSide()
        );
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        com.tom.trading.remote.ChunkLeaseManager.registerCallbacks();
        proxy.init(event);
        logger.info("{} initialization complete on {}", BuildInfo.MOD_ID, event.getSide());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        com.tom.trading.remote.RemoteTrading.start(event.getServer());
        event.registerServerCommand(new DirectoryCommand());
    }

    @Mod.EventHandler
    public void serverStopping(net.minecraftforge.fml.common.event.FMLServerStoppingEvent event) {
        com.tom.trading.remote.RemoteTrading.stopServer();
    }

    @Mod.EventHandler
    public void serverStopped(net.minecraftforge.fml.common.event.FMLServerStoppedEvent event) {
        com.tom.trading.remote.RemoteTrading.stopServer();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
        logger.info("{} bootstrap complete on {}", BuildInfo.MOD_ID, event.getSide());
    }
}
