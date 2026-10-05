package com.tom.trading.book;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TradeBookDataTest {
    @Test public void issuedKeyIsWorldAndMachineBoundAndNewBooksRecoverTheSameHistory() {
        UUID world = UUID.randomUUID(), machine = UUID.randomUUID(); TradeBookData data = TradeBookData.create(world);
        data.append(machine, "not tracked");
        TradeBookBinding book = data.bind(machine); assertTrue(data.snapshot(book).isEmpty());
        data.append(machine, "Buyer -> Seller | 64 Iron <- 3 Copper");
        assertEquals(book, data.bind(machine));
        assertEquals(Collections.singletonList("Buyer -> Seller | 64 Iron <- 3 Copper"), data.snapshot(data.bind(machine)));
        assertFalse(data.accepts(new TradeBookBinding(world, machine, UUID.randomUUID())));
        assertFalse(data.accepts(new TradeBookBinding(UUID.randomUUID(), machine, book.key)));
        assertFalse(data.accepts(new TradeBookBinding(world, UUID.randomUUID(), book.key)));
        TradeBookBinding other = data.bind(UUID.randomUUID()); assertNotEquals(book.key, other.key);
        assertTrue(data.snapshot(other).isEmpty());
    }
    @Test public void savedHistoryAndBindingRoundTripWithoutCrossMachineLeakage() {
        TradeBookData data = TradeBookData.create(UUID.randomUUID());
        TradeBookBinding a = data.bind(UUID.randomUUID()), b = data.bind(UUID.randomUUID());
        data.append(a.machine, "a1"); data.append(b.machine, "b1"); data.append(a.machine, "a2");
        TradeBookData restored = new TradeBookData(TradeBookData.NAME); restored.readFromNBT(data.writeToNBT(new NBTTagCompound()));
        assertEquals(Arrays.asList("a2", "a1"), restored.snapshot(a)); assertEquals(Collections.singletonList("b1"), restored.snapshot(b));
        assertEquals(a, restored.bind(a.machine)); restored.append(a.machine, "a3");
        assertEquals(Arrays.asList("a3", "a2", "a1"), restored.snapshot(a));
        NBTTagCompound item = new NBTTagCompound(); item.setString("OtherMod", "keep"); a.write(item);
        assertEquals(a, TradeBookBinding.read(item)); assertEquals("keep", item.getString("OtherMod"));
        item.getCompoundTag(TradeBookBinding.TAG).setInteger("Format", 2); assertNull(TradeBookBinding.read(item));
    }
    @Test public void perMachineRetentionKeepsRecentRecordsWhileBookLossDoesNotStopRecording() {
        TradeBookData data = TradeBookData.create(UUID.randomUUID()); TradeBookBinding a = data.bind(UUID.randomUUID());
        for (int i = 0; i < 120; i++) data.append(a.machine, "trade " + i);
        List<String> latest = data.snapshot(data.bind(a.machine)); assertEquals(100, latest.size());
        assertEquals("trade 119", latest.get(0)); assertEquals("trade 20", latest.get(99));
        try { latest.clear(); fail(); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void globalCountAndCharacterBoundsEvictOldRecordsWithoutLosingBookKeys() {
        TradeBookData data = TradeBookData.create(UUID.randomUUID()); List<TradeBookBinding> books = new ArrayList<>();
        for (int i = 0; i < 83; i++) {
            TradeBookBinding book = data.bind(UUID.randomUUID()); books.add(book);
            for (int n = 0; n < 100; n++) data.append(book.machine, "record " + i + " " + n);
        }
        assertEquals(TradeBookData.MAX_RECORDS, data.writeToNBT(new NBTTagCompound()).getTagList("History", 10).tagCount());
        assertTrue(data.snapshot(books.get(0)).isEmpty()); assertTrue(data.accepts(books.get(0)));
        TradeBookData chars = TradeBookData.create(UUID.randomUUID()); String large = String.join("", Collections.nCopies(8192, "x"));
        for (int i = 0; i < 3; i++) { TradeBookBinding book = chars.bind(UUID.randomUUID()); for (int n = 0; n < 100; n++) chars.append(book.machine, large); }
        assertEquals(TradeBookData.MAX_CHARACTERS / large.length(), chars.writeToNBT(new NBTTagCompound()).getTagList("History", 10).tagCount());
    }
    @Test public void utf8SnapshotAndMachineCapacityAreBounded() {
        TradeBookData data = TradeBookData.create(UUID.randomUUID()); TradeBookBinding book = data.bind(UUID.randomUUID());
        String line = String.join("", Collections.nCopies(4000, "ş"));
        for (int i = 0; i < 10; i++) data.append(book.machine, line);
        assertEquals(3, data.snapshot(book).size());
        for (int i = 1; i < TradeBookData.MAX_MACHINES; i++) data.bind(UUID.randomUUID());
        try { data.bind(UUID.randomUUID()); fail("Unbounded machine registrations"); } catch (IllegalStateException expected) { }
        assertEquals(book, data.bind(book.machine));
    }
    @Test public void malformedFutureAndOversizedDataArePreservedRatherThanReset() {
        TradeBookData data = TradeBookData.create(UUID.randomUUID()); TradeBookBinding book = data.bind(UUID.randomUUID()); data.append(book.machine, "valid");
        NBTTagCompound original = data.writeToNBT(new NBTTagCompound());
        for (int mode = 0; mode < 4; mode++) {
            NBTTagCompound invalid = original.copy();
            if (mode == 0) invalid.setInteger("Format", 99);
            if (mode == 1) invalid.removeTag("WorldMost");
            if (mode == 2) invalid.getTagList("History", 10).getCompoundTagAt(0).setString("Text", "forged\nrecord");
            if (mode == 3) invalid.getTagList("Bindings", 10).appendTag(invalid.getTagList("Bindings", 10).getCompoundTagAt(0).copy());
            TradeBookData restored = new TradeBookData(TradeBookData.NAME); restored.readFromNBT(invalid);
            assertFalse(restored.accepts(book)); assertFalse(restored.isDirty()); assertEquals(invalid, restored.writeToNBT(new NBTTagCompound()));
            try { restored.bind(book.machine); fail(); } catch (IllegalStateException expected) { }
        }
    }
    @Test public void blankBindingNeverOverwritesWrittenOrUnsupportedBooks() {
        assertTrue(TradeBooks.blank(null)); NBTTagCompound item = new NBTTagCompound(); item.setString("OtherMod", "keep");
        assertTrue(TradeBooks.blank(item)); net.minecraft.nbt.NBTTagList pages = new net.minecraft.nbt.NBTTagList();
        pages.appendTag(new net.minecraft.nbt.NBTTagString("old manual accounting")); item.setTag("pages", pages); assertFalse(TradeBooks.blank(item));
        pages.set(0, new net.minecraft.nbt.NBTTagString("   ")); assertTrue(TradeBooks.blank(item));
        item.setString(TradeBookBinding.TAG, "unsupported"); assertFalse(TradeBooks.blank(item));
        for (String text : Arrays.asList("\nforged", "\u00a7k", "\u202Ehidden", "\uD800", "")) assertFalse(TradeBookData.validText(text));
    }
}
