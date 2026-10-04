package com.tom.trading.client;

import com.tom.trading.BuildInfo;
import com.tom.trading.Content;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = BuildInfo.MOD_ID, value = Side.CLIENT)
public final class ClientRegistration {
    private ClientRegistration() {
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(
                Content.VENDING_MACHINE_ITEM,
                0,
                new ModelResourceLocation(Content.VENDING_MACHINE_ID, "inventory")
        );
        ModelLoader.setCustomModelResourceLocation(Content.TAG_FILTER, 0,
                new ModelResourceLocation(Content.TAG_FILTER.getRegistryName(), "inventory"));
    }
}
