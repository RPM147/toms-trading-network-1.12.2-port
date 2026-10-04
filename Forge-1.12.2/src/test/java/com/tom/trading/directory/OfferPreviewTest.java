package com.tom.trading.directory;

import com.tom.trading.item.OreFilters;
import com.tom.trading.tile.TileVendingMachine;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.oredict.OreDictionary;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class OfferPreviewTest {
    @BeforeClass public static void bootstrap() { Bootstrap.register(); }
    private TileVendingMachine tile() {
        TileVendingMachine tile = new TileVendingMachine();
        tile.setPaymentDefinition(0, new ItemStack(Items.EMERALD), 3);
        tile.setSaleDefinition(0, new ItemStack(Items.DIAMOND), 1024);
        return tile;
    }
    @Test public void captureExposesDefinitionsAndCountsNotPrivateInventoriesOrItemNbt() {
        TileVendingMachine tile = tile();
        ItemStack named = new ItemStack(Items.DIAMOND);
        named.setStackDisplayName("Special diamond");
        named.getTagCompound().setString("Secret", "not for the catalogue");
        tile.setSaleDefinition(0, named, 1024);
        OfferPreview preview = OfferPreviewCapture.capture(tile);
        assertTrue(preview.known); assertEquals(3, preview.payment.get(0).quantity);
        assertEquals(1024, preview.sale.get(0).quantity); assertEquals("Special diamond", preview.sale.get(0).name);
        assertTrue(preview.sale.get(0).tagged); assertTrue(preview.sale.get(0).matchNbt);
        tile.getSaleStock().setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 12));
        tile.getEarnings().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 7));
        assertEquals(preview, OfferPreviewCapture.capture(tile));
        NBTTagCompound saved = OfferPreviewCodec.save(preview);
        assertEquals(new HashSet<>(Arrays.asList("Format", "PublicOffer")), saved.getKeySet());
        String bytes = new String(saved.getByteArray("PublicOffer"), java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(bytes.contains("Secret")); assertFalse(bytes.contains("not for the catalogue"));
        assertFalse(bytes.contains("gold_ingot")); assertFalse(bytes.contains("iron_ingot"));
        tile.setPaymentDefinition(0, new ItemStack(Items.EMERALD), 65);
        assertNotEquals(preview, OfferPreviewCapture.capture(tile));
        assertEquals(3, preview.payment.get(0).quantity);
        try { preview.payment.clear(); fail(); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void captureHandlesAllEightDefinitionsGroupsAndEmptyOffers() {
        TileVendingMachine tile = tile();
        OreDictionary.registerOre("ttnPreviewEmerald", Items.EMERALD);
        tile.setPaymentDefinition(0, OreFilters.create(new ItemStack(Items.EMERALD), "ttnPreviewEmerald"), 128);
        for (int slot = 1; slot < 4; slot++) {
            tile.setPaymentDefinition(slot, new ItemStack(Items.GOLD_INGOT), 256);
            tile.setSaleDefinition(slot, new ItemStack(Items.IRON_INGOT), 1024);
        }
        OfferPreview preview = OfferPreviewCapture.capture(tile);
        assertTrue(preview.known); assertEquals(4, preview.payment.size()); assertEquals(4, preview.sale.size());
        assertEquals("minecraft:emerald", preview.payment.get(0).itemId);
        assertEquals("ttnPreviewEmerald", preview.payment.get(0).oreName);
        assertEquals(128, preview.payment.get(0).quantity);
        assertTrue(OfferPreviewCapture.capture(new TileVendingMachine()).known);
        assertTrue(OfferPreviewCapture.capture(new TileVendingMachine()).sale.isEmpty());
        tile.readFromNBT(futureTile()); assertSame(OfferPreview.UNKNOWN, OfferPreviewCapture.capture(tile));
    }
    @Test public void largeNamesFallBackToRegistryNamesWithoutDroppingItemsOrAmounts() {
        TileVendingMachine tile = new TileVendingMachine();
        String huge = String.join("", Collections.nCopies(64, "\uD83D\uDED2"));
        for (int i = 0; i < 4; i++) {
            ItemStack named = new ItemStack(Items.DIAMOND); named.setStackDisplayName(huge);
            tile.setPaymentDefinition(i, named, 1024); tile.setSaleDefinition(i, named, 1024);
        }
        OfferPreview preview = OfferPreviewCapture.capture(tile);
        assertTrue(preview.known); assertEquals(4, preview.payment.size()); assertEquals(4, preview.sale.size());
        assertTrue(preview.sale.get(0).name.isEmpty()); assertEquals(1024, preview.sale.get(3).quantity);
        assertTrue(preview.encodedSize() <= OfferPreview.MAX_BYTES);
    }
    @Test public void itemCopyCallbacksCannotPublishMixedOffersOrBreakCapture() {
        TileVendingMachine tile = tile(); AtomicReference<Runnable> callback = new AtomicReference<>();
        ItemStack stack = new ItemStack(Items.EMERALD); stack.setTagCompound(new CallbackTag(callback));
        tile.setPaymentDefinition(0, stack, 3);
        callback.set(() -> tile.setSaleDefinition(0, new ItemStack(Items.IRON_INGOT), 10));
        assertSame(OfferPreview.UNKNOWN, OfferPreviewCapture.capture(tile));
        assertEquals("minecraft:iron_ingot", OfferPreviewCapture.capture(tile).sale.get(0).itemId);
        callback.set(() -> { throw new IllegalStateException("Broken copy callback"); });
        assertSame(OfferPreview.UNKNOWN, OfferPreviewCapture.capture(tile));
        assertTrue(OfferPreviewCapture.capture(tile).known);
    }
    @Test public void optionalCacheRoundTripsAndMissingBadOrFutureCachesLeaveIdentityUsable() {
        MachineDirectoryData data = MachineDirectoryData.create(); MachineAddress address = new MachineAddress(0, 1, 64, 1);
        UUID machine = UUID.randomUUID(); OfferPreview preview = OfferPreviewCapture.capture(tile());
        data.observe(new MachineDirectoryEntry(address, machine, UUID.randomUUID(), "Owner", "Shop",
                MachineDirectoryEntry.Evidence.OBSERVED, preview));
        for (int mode = 0; mode < 5; mode++) {
            NBTTagCompound tag = data.writeToNBT(new NBTTagCompound());
            NBTTagCompound row = tag.getTagList("Entries", 10).getCompoundTagAt(0);
            if (mode == 1) row.removeTag("OfferPreview"); // port.7 catalogue
            if (mode == 2) row.getCompoundTag("OfferPreview").setInteger("Format", 99);
            if (mode == 3) row.getCompoundTag("OfferPreview").setByteArray("PublicOffer", new byte[]{0, 2, 99});
            if (mode == 4) row.getCompoundTag("OfferPreview").setByteArray("PublicOffer", new byte[703]);
            MachineDirectoryData restored = new MachineDirectoryData(MachineDirectoryData.DATA_NAME); restored.readFromNBT(tag);
            assertTrue(restored.isSupported()); assertTrue(restored.isUniqueIdentity(address, machine));
            assertEquals(data.getWorldId(), restored.getWorldId());
            assertEquals(mode == 0 ? preview : OfferPreview.UNKNOWN, restored.get(address).preview);
        }
    }
    @Test public void previewChangesAdvanceRevisionButIdenticalObservationsAndHintsDoNotReplaceIt() {
        MachineDirectoryData data = MachineDirectoryData.create(); MachineAddress address = new MachineAddress(0, 1, 64, 1);
        UUID machine = UUID.randomUUID(), owner = UUID.randomUUID(); TileVendingMachine tile = tile();
        MachineDirectoryEntry entry = new MachineDirectoryEntry(address, machine, owner, "Owner", "Shop",
                MachineDirectoryEntry.Evidence.OBSERVED, OfferPreviewCapture.capture(tile));
        data.observe(entry); long before = data.getRevision(); data.observe(entry); assertEquals(before, data.getRevision());
        tile.setSaleDefinition(0, new ItemStack(Items.DIAMOND), 65);
        MachineDirectoryEntry changed = new MachineDirectoryEntry(address, machine, owner, "Owner", "Shop",
                MachineDirectoryEntry.Evidence.OBSERVED, OfferPreviewCapture.capture(tile));
        data.observe(changed); assertTrue(data.getRevision() > before);
        data.importHints(Collections.singletonList(new MachineDirectoryEntry(address, machine, owner, "Owner", "Shop",
                MachineDirectoryEntry.Evidence.HINT)), false);
        assertEquals(changed, data.get(address));
    }
    @Test public void previewCodecRejectsEveryTruncationAndInvalidFlagsCountsLengthsAndQuantities() {
        ByteBuf bytes = Unpooled.buffer();
        try {
            OfferPreview preview = OfferPreviewCapture.capture(tile());
            OfferPreviewCodec.write(new PacketBuffer(bytes), preview);
            assertEquals(preview.encodedSize() + 2, bytes.readableBytes());
            assertEquals(preview, OfferPreviewCodec.read(new PacketBuffer(bytes.duplicate())));
            for (int i = 0; i < bytes.readableBytes(); i++) {
                try { OfferPreviewCodec.read(new PacketBuffer(bytes.slice(0, i))); fail("Accepted prefix " + i); }
                catch (RuntimeException expected) { }
            }
            int quantityOffset = 4 + 1 + "minecraft:emerald".length() + 2;
            for (int mode = 0; mode < 6; mode++) {
                ByteBuf bad = bytes.copy();
                try {
                    if (mode == 0) bad.setByte(2, 2);
                    if (mode == 1) bad.setByte(3, 5);
                    if (mode == 2) bad.setShort(0, 701);
                    if (mode == 3) bad.setShort(quantityOffset, 0);
                    if (mode == 4) bad.setShort(quantityOffset, 1025);
                    if (mode == 5) bad.setByte(quantityOffset + 2, 4);
                    try { OfferPreviewCodec.read(new PacketBuffer(bad)); fail("Accepted corruption " + mode); }
                    catch (RuntimeException expected) { }
                } finally { bad.release(); }
            }
            bytes.writeByte(1); bytes.setShort(0, bytes.readableBytes() - 2);
            try { OfferPreviewCodec.read(new PacketBuffer(bytes)); fail("Accepted trailing data"); }
            catch (RuntimeException expected) { }
        } finally { bytes.release(); }
    }
    @Test public void modelRejectsInvalidBoundsGroupsFormattingAndUnverifiedPreviews() {
        for (int quantity : new int[]{-1, 0, 1025, 65535}) {
            try { new OfferPreview.Item("minecraft:diamond", 0, quantity, "", "", false, true); fail(); }
            catch (IllegalArgumentException expected) { }
        }
        for (String name : Arrays.asList("a\nb", "\u00a7k", "\u202E", "\uD800")) {
            try { new OfferPreview.Item("minecraft:diamond", 0, 1, name, "", false, true); fail(); }
            catch (IllegalArgumentException expected) { }
        }
        OfferPreview.Item group = new OfferPreview.Item("minecraft:diamond", 0, 1, "", "gemDiamond", false, false);
        try { new OfferPreview(Collections.emptyList(), Collections.singletonList(group)); fail(); }
        catch (IllegalArgumentException expected) { }
        try { new OfferPreview(Collections.nCopies(5, group), Collections.emptyList()); fail(); }
        catch (IllegalArgumentException expected) { }
        try { new MachineDirectoryEntry(new MachineAddress(0, 0, 64, 0), UUID.randomUUID(), null, "", "",
                MachineDirectoryEntry.Evidence.HINT, OfferPreviewCapture.capture(tile())); fail(); }
        catch (IllegalArgumentException expected) { }
    }
    private static NBTTagCompound futureTile() { NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("DataVersion", 99); return tag; }
    private static final class CallbackTag extends NBTTagCompound {
        private final AtomicReference<Runnable> callback;
        CallbackTag(AtomicReference<Runnable> callback) { this.callback = callback; }
        @Override public NBTTagCompound copy() {
            Runnable action = callback.getAndSet(null); if (action != null) action.run();
            return new CallbackTag(callback);
        }
    }
}
