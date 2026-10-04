package com.tom.trading;

import com.tom.trading.block.BlockVendingMachine;
import com.tom.trading.tile.TileVendingMachine;
import com.tom.trading.item.TagFilterItem;
import com.tom.trading.item.ItemVendingMachine;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod.EventBusSubscriber(modid = BuildInfo.MOD_ID)
public final class Content {
    public static final ResourceLocation VENDING_MACHINE_ID =
            new ResourceLocation(BuildInfo.MOD_ID, "vending_machine");
    public static final ResourceLocation VENDING_MACHINE_TILE_ID =
            new ResourceLocation(BuildInfo.MOD_ID, "vending_machine.tile");

    public static final BlockVendingMachine VENDING_MACHINE = createVendingMachine();
    public static final ItemBlock VENDING_MACHINE_ITEM = createVendingMachineItem();
    public static final Item TAG_FILTER = new TagFilterItem()
            .setRegistryName(new ResourceLocation(BuildInfo.MOD_ID, "tag_filter"));

    private static boolean tileEntityRegistered;

    private Content() {
    }

    private static BlockVendingMachine createVendingMachine() {
        BlockVendingMachine block = new BlockVendingMachine();
        block.setRegistryName(VENDING_MACHINE_ID);
        return block;
    }

    private static ItemBlock createVendingMachineItem() {
        ItemBlock item = new ItemVendingMachine(VENDING_MACHINE);
        item.setRegistryName(VENDING_MACHINE_ID);
        return item;
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(VENDING_MACHINE);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(VENDING_MACHINE_ITEM);
        event.getRegistry().register(TAG_FILTER);
    }

    public static synchronized void registerTileEntity() {
        if (tileEntityRegistered) {
            return;
        }

        GameRegistry.registerTileEntity(TileVendingMachine.class, VENDING_MACHINE_TILE_ID);
        tileEntityRegistered = true;
    }
}
