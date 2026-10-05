package com.tom.trading.book;

import com.tom.trading.directory.MachineDirectory;
import com.tom.trading.directory.MachineDirectoryData;
import com.tom.trading.tile.TileVendingMachine;
import com.tom.trading.trade.TradeBookEntry;
import com.tom.trading.trade.TradeReceipt;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import java.util.List;

/** Server access to bound books. Neither binding nor reading opens or leases a remote machine. */
public final class TradeBooks {
    private TradeBooks() {}
    public static boolean marked(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.WRITABLE_BOOK && stack.hasTagCompound()
                && stack.getTagCompound().hasKey(TradeBookBinding.TAG);
    }
    public static boolean blank(NBTTagCompound tag) {
        if (tag == null) return true;
        if (tag.hasKey(TradeBookBinding.TAG)) return false;
        if (!tag.hasKey("pages")) return true;
        if (!tag.hasKey("pages", 9)) return false;
        NBTTagList pages = tag.getTagList("pages", 8);
        if (pages.tagCount() != ((NBTTagList) tag.getTag("pages")).tagCount()) return false;
        for (int i = 0; i < pages.tagCount(); i++) if (!pages.getStringTagAt(i).trim().isEmpty()) return false;
        return true;
    }
    public static boolean active(EntityPlayerMP player) {
        return !player.hasDisconnected() && player.isEntityAlive() && !player.isSpectator()
                && player.openContainer == player.inventoryContainer;
    }
    public static void bind(EntityPlayerMP player, TileVendingMachine machine, EnumHand hand) {
        if (!active(player) || !player.isSneaking() || machine.getWorld() != player.getServerWorld()
                || player.getDistanceSq(machine.getPos().getX() + 0.5, machine.getPos().getY() + 0.5, machine.getPos().getZ() + 0.5) > 64) return;
        if (!machine.isOwner(player)) { status(player, "owner_only"); return; }
        ItemStack book = player.getHeldItem(hand);
        if (book.isEmpty() || book.getItem() != Items.WRITABLE_BOOK || book.getCount() != 1) { status(player, "single_book"); return; }
        if (marked(book)) {
            TradeBookBinding existing = TradeBookBinding.read(book.getTagCompound());
            status(player, existing != null && existing.machine.equals(machine.getMachineUuid()) ? "already_bound" : "cannot_rebind"); return;
        }
        if (!blank(book.getTagCompound())) { status(player, "not_empty"); return; }
        if (!machine.isDataVersionSupported() || machine.isInvalid() || machine.isAutomationQuarantined()) { status(player, "unavailable"); return; }
        machine.ensureMachineIdentity();
        if (!MachineDirectory.observe(machine)) { status(player, "unavailable"); return; }
        MachineDirectoryData directory = MachineDirectory.get(player.getServerWorld());
        if (directory == null || !directory.isSupported() || !directory.isUniqueIdentity(machine.getMachineUuid())) { status(player, "unavailable"); return; }
        TradeBookData data = get(player.getServerWorld(), true);
        if (data == null || !data.supports(directory.getWorldId())) { status(player, "unavailable"); return; }
        // Offer/name callbacks may run during observation; verify owner and held item again before binding.
        if (!machine.isOwner(player) || player.getHeldItem(hand) != book || machine.isInvalid()) { status(player, "unavailable"); return; }
        try {
            TradeBookBinding binding = data.bind(machine.getMachineUuid());
            NBTTagCompound tag = book.hasTagCompound() ? book.getTagCompound().copy() : new NBTTagCompound();
            tag.setString("TTNBookMachineName", machine.hasCustomName() ? com.tom.trading.directory.DirectoryText.label(machine.getName()) : "");
            tag.setString("TTNBookOwner", com.tom.trading.directory.DirectoryText.label(player.getName()));
            tag.setString("TTNBookAddress", player.dimension + " / " + machine.getPos().getX() + ", " + machine.getPos().getY() + ", " + machine.getPos().getZ());
            binding.write(tag); book.setTagCompound(tag);
            snapshot(book, data.snapshot(binding));
            player.inventory.markDirty(); player.inventoryContainer.detectAndSendChanges();
            status(player, "bound");
        } catch (IllegalStateException full) { status(player, "capacity"); }
    }
    public static List<String> open(EntityPlayerMP player, EnumHand hand, TradeBookBinding binding) {
        if (!active(player)) throw new IllegalArgumentException("Inactive player");
        ItemStack book = player.getHeldItem(hand);
        if (!marked(book) || book.getCount() != 1 || !binding.equals(TradeBookBinding.read(book.getTagCompound())))
            throw new IllegalArgumentException("Book not held");
        TradeBookData data = get(player.getServerWorld(), false);
        MachineDirectoryData directory = MachineDirectory.get(player.getServerWorld());
        if (data == null || directory == null || !directory.isSupported() || !data.supports(directory.getWorldId()) || !data.accepts(binding))
            throw new IllegalArgumentException("Book unavailable");
        List<String> rows = data.snapshot(binding); snapshot(book, rows);
        player.inventory.markDirty(); player.inventoryContainer.detectAndSendChanges(); return rows;
    }
    private static void snapshot(ItemStack book, List<String> rows) {
        NBTTagList pages = new NBTTagList();
        for (String row : rows) pages.appendTag(new NBTTagString(row));
        if (rows.isEmpty()) pages.appendTag(new NBTTagString(""));
        book.setTagInfo("pages", pages);
    }
    public static void record(MinecraftServer server, TradeReceipt receipt) {
        if (server == null || server.getWorld(0) == null) return;
        TradeBookData data = get(server.getWorld(0), false);
        if (data == null || !data.tracks(receipt.getMachineUuid())) return;
        MachineDirectoryData directory = MachineDirectory.get(server.getWorld(0));
        if (directory != null && directory.isSupported() && data.supports(directory.getWorldId()))
            data.append(receipt.getMachineUuid(), TradeBookEntry.format(receipt));
    }
    private static TradeBookData get(WorldServer world, boolean create) {
        if (!world.isCallingFromMinecraftThread()) return null;
        WorldServer overworld = world.getMinecraftServer().getWorld(0);
        if (overworld == null || overworld.getMapStorage() == null) return null;
        MapStorage storage = overworld.getMapStorage();
        TradeBookData data = (TradeBookData) storage.getOrLoadData(TradeBookData.class, TradeBookData.NAME);
        if (data == null && create) {
            java.io.File file = overworld.getSaveHandler().getMapFileFromName(TradeBookData.NAME);
            MachineDirectoryData directory = MachineDirectory.get(world);
            if (directory == null || !directory.isSupported()) return null;
            data = file != null && file.exists() ? new TradeBookData(TradeBookData.NAME) : TradeBookData.create(directory.getWorldId());
            storage.setData(TradeBookData.NAME, data);
        }
        return data;
    }
    public static void status(EntityPlayerMP player, String key) {
        player.sendStatusMessage(new TextComponentTranslation("gui.toms_trading_network.book." + key), false);
    }
}
