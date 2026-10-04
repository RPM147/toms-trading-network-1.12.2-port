package com.tom.trading.network;

import com.tom.trading.directory.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DirectoryNetworkTest {
    @Test public void tabsRoundTripInRequestsAndResponsesAndUnknownValuesFailClosed() {
        for (DirectoryTab tab : DirectoryTab.values()) {
            DirectoryQuery q = new DirectoryQuery(UUID.randomUUID(), 1, 0, 0, null, 0, "", tab);
            ByteBuf bytes = Unpooled.buffer();
            try {
                new DirectoryNetwork.RequestPage(q).toBytes(bytes);
                DirectoryNetwork.RequestPage decoded = new DirectoryNetwork.RequestPage(); decoded.fromBytes(bytes.duplicate());
                assertTrue(decoded.valid); assertEquals(tab, decoded.query.tab);
                bytes.setByte(bytes.writerIndex() - 1, 255);
                DirectoryNetwork.RequestPage invalid = new DirectoryNetwork.RequestPage(); invalid.fromBytes(bytes.duplicate()); assertFalse(invalid.valid);
                bytes.clear();
                new DirectoryNetwork.Page(new DirectoryPage(q, DirectoryPage.Result.OK, UUID.randomUUID(), 1, 0, false, Collections.emptyList())).toBytes(bytes);
                DirectoryNetwork.Page reply = new DirectoryNetwork.Page(); reply.fromBytes(bytes.duplicate());
                assertTrue(reply.valid); assertEquals(tab, reply.page.query.tab);
            } finally { bytes.release(); }
        }
        try { new DirectoryQuery(UUID.randomUUID(), 1, 0, 0, null, 0, "", null); fail(); }
        catch (IllegalArgumentException expected) { }
    }
    private DirectoryQuery query(String search) { return new DirectoryQuery(UUID.randomUUID(), 9, -7, 0, null, 0, search); }
    private DirectoryPage page() {
        List<DirectoryPage.Row> rows = new ArrayList<>();
        String label = String.join("", Collections.nCopies(64, "\uD83D\uDED2"));
        for (int i = 0; i < 50; i++) rows.add(new DirectoryPage.Row(new MachineDirectoryEntry(
                new MachineAddress(-10, i, 255, -1), UUID.randomUUID(), UUID.randomUUID(), label, label,
                MachineDirectoryEntry.Evidence.OBSERVED), DirectoryPage.State.RECORDED));
        return new DirectoryPage(query("İpek"), DirectoryPage.Result.OK, UUID.randomUUID(), 1, 50, false, rows);
    }
    @Test public void maximumUnicodePageRoundTripsBelowTheByteLimitWithOnlyPublicFields() {
        DirectoryPage original = page(); ByteBuf bytes = Unpooled.buffer();
        try {
            new DirectoryNetwork.Page(original).toBytes(bytes);
            assertTrue(bytes.readableBytes() < DirectoryNetwork.MAX_PAGE_BYTES);
            DirectoryNetwork.Page decoded = new DirectoryNetwork.Page(); decoded.fromBytes(bytes);
            assertTrue(decoded.valid); assertEquals(50, decoded.page.rows.size());
            assertEquals(original.worldId, decoded.page.worldId); assertEquals(original.query.screenId, decoded.page.query.screenId);
            assertEquals(original.rows.get(0).entry, decoded.page.rows.get(0).entry);
            assertFalse(decoded.page.backfillComplete);
        } finally { bytes.release(); }
    }
    @Test public void fiftyMaximumSizePreviewsAndMaximumUnicodeLabelsStillFitOnePage() {
        List<OfferPreview.Item> payment = new ArrayList<>(), sale = new ArrayList<>();
        String id = "test:" + String.join("", Collections.nCopies(73, "i"));
        for (int i = 0; i < 4; i++) {
            payment.add(new OfferPreview.Item(id, 32766, 1024, i == 0 ? "123456789" : "", "", true, true));
            sale.add(new OfferPreview.Item(id, 32766, 1024, "", "", true, true));
        }
        OfferPreview preview = new OfferPreview(payment, sale); assertEquals(OfferPreview.MAX_BYTES, preview.encodedSize());
        DirectoryPage base = page(); List<DirectoryPage.Row> rows = new ArrayList<>();
        for (DirectoryPage.Row row : base.rows) {
            MachineDirectoryEntry e = row.entry;
            rows.add(new DirectoryPage.Row(new MachineDirectoryEntry(e.address, e.machineId, e.ownerId, e.ownerName, e.name,
                    e.evidence, preview), row.state));
        }
        String search = String.join("", Collections.nCopies(64, "\uD83D\uDED2"));
        DirectoryQuery q = new DirectoryQuery(UUID.randomUUID(), 1, 0, 0, base.worldId, base.revision, search);
        DirectoryPage maximum = new DirectoryPage(q, base.result, base.worldId, base.revision, base.total, false, rows, search);
        ByteBuf bytes = Unpooled.buffer();
        try {
            new DirectoryNetwork.Page(maximum).toBytes(bytes);
            assertTrue(bytes.readableBytes() <= DirectoryNetwork.MAX_PAGE_BYTES);
            DirectoryNetwork.Page decoded = new DirectoryNetwork.Page(); decoded.fromBytes(bytes.duplicate());
            assertTrue(decoded.valid); assertEquals(preview, decoded.page.rows.get(49).entry.preview);
            assertEquals(search, decoded.page.specialTabName);
            bytes.writeByte(0); DirectoryNetwork.Page extra = new DirectoryNetwork.Page(); extra.fromBytes(bytes); assertFalse(extra.valid);
        } finally { bytes.release(); }
    }
    @Test public void requestsRejectTruncationTrailingBytesControlsAndInvalidCursor() {
        ByteBuf bytes = Unpooled.buffer();
        try {
            DirectoryQuery query = query("Alışveriş"); new DirectoryNetwork.RequestPage(query).toBytes(bytes);
            DirectoryNetwork.RequestPage full = new DirectoryNetwork.RequestPage(); full.fromBytes(bytes.duplicate()); assertTrue(full.valid);
            for (int n = 0; n < bytes.readableBytes(); n++) {
                DirectoryNetwork.RequestPage cut = new DirectoryNetwork.RequestPage(); cut.fromBytes(bytes.slice(0, n)); assertFalse(cut.valid);
            }
            bytes.writeByte(1); DirectoryNetwork.RequestPage extra = new DirectoryNetwork.RequestPage(); extra.fromBytes(bytes); assertFalse(extra.valid);
        } finally { bytes.release(); }
        for (String value : Arrays.asList("a\nb", "\u00a7k", "\u202Ehidden", "\uD800", String.join("", Collections.nCopies(65, "x")))) {
            try { query(value); fail("Invalid query accepted"); } catch (IllegalArgumentException expected) { }
        }
        try { new DirectoryQuery(UUID.randomUUID(), 1, 0, 1, null, 0, ""); fail(); } catch (IllegalArgumentException expected) { }
        try { new DirectoryQuery(UUID.randomUUID(), 1, 0, Integer.MAX_VALUE, UUID.randomUUID(), 1, ""); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void responseRejectsEveryTruncationTrailingDataAndOversizedPayload() {
        ByteBuf bytes = Unpooled.buffer();
        try {
            DirectoryPage empty = new DirectoryPage(query(""), DirectoryPage.Result.OK, UUID.randomUUID(), 1, 0, false, Collections.emptyList());
            new DirectoryNetwork.Page(empty).toBytes(bytes);
            for (int n = 0; n < bytes.readableBytes(); n++) {
                DirectoryNetwork.Page cut = new DirectoryNetwork.Page(); cut.fromBytes(bytes.slice(0, n)); assertFalse(cut.valid);
            }
            bytes.writeByte(0); DirectoryNetwork.Page extra = new DirectoryNetwork.Page(); extra.fromBytes(bytes); assertFalse(extra.valid);
        } finally { bytes.release(); }
        ByteBuf oversized = Unpooled.buffer().writeZero(DirectoryNetwork.MAX_PAGE_BYTES + 1);
        try { DirectoryNetwork.Page decoded = new DirectoryNetwork.Page(); decoded.fromBytes(oversized); assertFalse(decoded.valid); }
        finally { oversized.release(); }
    }
    @Test public void responseRejectsOversizedRowCountBeforeReadingEntriesAndMalformedBoolean() {
        ByteBuf bytes = Unpooled.buffer();
        try {
            new DirectoryNetwork.RequestPage(query("")).toBytes(bytes);
            PacketBuffer b = new PacketBuffer(bytes); b.writeByte(DirectoryPage.Result.OK.ordinal());
            b.writeBoolean(true); b.writeUniqueId(UUID.randomUUID()); b.writeLong(1); b.writeVarInt(51); b.writeBoolean(false);
            b.writeString(""); b.writeVarInt(51);
            DirectoryNetwork.Page decoded = new DirectoryNetwork.Page(); decoded.fromBytes(bytes.duplicate()); assertFalse(decoded.valid);
            bytes.setByte(16 + 8 + 4 + 1, 2); // Optional expected-world flag in the query.
            DirectoryNetwork.Page invalidBool = new DirectoryNetwork.Page(); invalidBool.fromBytes(bytes); assertFalse(invalidBool.valid);
        } finally { bytes.release(); }
    }
    @Test public void directoryAndTradingShareTheTotalBudgetWithoutSharingAnExtraTradeAllowance() {
        long now = 10_000_000_000L; RequestBudget budget = new RequestBudget();
        assertTrue(budget.admitDirectory(now).accepted); assertTrue(budget.admitDirectory(now).accepted);
        for (int i = 0; i < 100; i++) assertFalse(budget.admitDirectory(now).accepted);
        assertTrue(budget.admit(now, 1024).accepted); assertFalse(budget.admit(now, 1).accepted);
        for (int i = 3; i < 40; i++) assertTrue(budget.admit(now, 0).accepted);
        assertFalse(budget.admit(now, 0).accepted);
        assertTrue(budget.admitDirectory(now + 1_000_000_000L).accepted);
        RequestBudget exhausted = new RequestBudget();
        for (int i = 0; i < 40; i++) assertTrue(exhausted.accept(now, 0));
        assertFalse(exhausted.admitDirectory(now).accepted);
    }
    @Test public void replyCannotSilentlyReplaceARequestedSnapshotOrContainDuplicateAddresses() {
        DirectoryPage first = page();
        DirectoryQuery next = new DirectoryQuery(first.query.screenId, 10, -7, 0, first.worldId, first.revision, "");
        try { new DirectoryPage(next, DirectoryPage.Result.OK, UUID.randomUUID(), 1, 0, false, Collections.emptyList()); fail(); }
        catch (IllegalArgumentException expected) { }
        try { new DirectoryPage(query(""), DirectoryPage.Result.OK, first.worldId, 1, 2, false,
                Arrays.asList(first.rows.get(0), first.rows.get(0))); fail(); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void serverTabNameRoundTripsForEveryResultIncludingBlankAndEmptyDirectory() {
        for (DirectoryPage.Result result : DirectoryPage.Result.values()) {
            for (String name : Arrays.asList("", "Server Shops", "Etkinlik Mağazaları — 100%", "工具商店")) {
                DirectoryPage original = new DirectoryPage(query(""), result, UUID.randomUUID(), 1, 0, false,
                        Collections.emptyList()).withSpecialTabName(name);
                ByteBuf bytes = Unpooled.buffer();
                try {
                    new DirectoryNetwork.Page(original).toBytes(bytes);
                    DirectoryNetwork.Page decoded = new DirectoryNetwork.Page(); decoded.fromBytes(bytes);
                    assertTrue(decoded.valid); assertEquals(name, decoded.page.specialTabName);
                    assertEquals(result, decoded.page.result);
                } finally { bytes.release(); }
            }
        }
    }

    @Test public void invalidTabNamesFailClosedInDtoAndWire() {
        for (String name : Arrays.asList("\nShop", "\u00a7kShop", "\u202EShop", "\uD800",
                String.join("", Collections.nCopies(65, "a")), String.join("", Collections.nCopies(129, "a")))) {
            try { page().withSpecialTabName(name); fail("Invalid tab name accepted"); }
            catch (IllegalArgumentException expected) { }
            // An unpaired surrogate cannot be represented as-is in UTF-8; the DTO must reject it.
            if (name.equals("\uD800")) continue;
            ByteBuf bytes = Unpooled.buffer();
            try {
                new DirectoryNetwork.RequestPage(query("")).toBytes(bytes);
                PacketBuffer b = new PacketBuffer(bytes); b.writeByte(DirectoryPage.Result.OK.ordinal());
                b.writeBoolean(true); b.writeUniqueId(UUID.randomUUID()); b.writeLong(1); b.writeVarInt(0);
                b.writeBoolean(false); b.writeString(name); b.writeVarInt(0);
                DirectoryNetwork.Page decoded = new DirectoryNetwork.Page(); decoded.fromBytes(bytes);
                assertFalse(decoded.valid);
            } finally { bytes.release(); }
        }
    }
    @Test public void idempotentFavoriteChangesAndPrivateFlagsRoundTripWithoutPlayerIdentity() {
        UUID worldId = UUID.randomUUID(), machineId = UUID.randomUUID();
        for (boolean value : Arrays.asList(false, true)) {
            DirectoryQuery query = new DirectoryQuery(UUID.randomUUID(), 5, 0, 0, worldId, 9, "shop",
                    DirectoryTab.FAVORITES, machineId, value);
            ByteBuf bytes = Unpooled.buffer();
            try {
                new DirectoryNetwork.RequestPage(query).toBytes(bytes);
                DirectoryNetwork.RequestPage request = new DirectoryNetwork.RequestPage(); request.fromBytes(bytes.duplicate());
                assertTrue(request.valid); assertEquals(machineId, request.query.favoriteMachine); assertEquals(value, request.query.favoriteValue);
                bytes.setByte(bytes.writerIndex() - 2, 2); // Favorite value is a strict boolean, followed by the tab enum.
                DirectoryNetwork.RequestPage malformed = new DirectoryNetwork.RequestPage(); malformed.fromBytes(bytes.duplicate()); assertFalse(malformed.valid);
                bytes.clear();
                DirectoryPage.Row row = new DirectoryPage.Row(new MachineDirectoryEntry(new MachineAddress(0, 1, 64, 1),
                        machineId, null, "Owner", "Shop", MachineDirectoryEntry.Evidence.OBSERVED), DirectoryPage.State.RECORDED, value);
                DirectoryPage page = new DirectoryPage(query, DirectoryPage.Result.OK, worldId, 9, 1, false, Collections.singletonList(row));
                new DirectoryNetwork.Page(page).toBytes(bytes);
                DirectoryNetwork.Page response = new DirectoryNetwork.Page(); response.fromBytes(bytes.duplicate());
                assertTrue(response.valid); assertEquals(value, response.page.rows.get(0).favorite);
                assertEquals(machineId, response.page.query.favoriteMachine); assertEquals(value, response.page.query.favoriteValue);
            } finally { bytes.release(); }
        }
    }
    @Test public void favoriteMutationsRequireBoundSaveAndFirstPage() {
        UUID screen = UUID.randomUUID(), machine = UUID.randomUUID(), world = UUID.randomUUID();
        try { new DirectoryQuery(screen, 1, 0, 0, null, 0, "", DirectoryTab.ALL, machine, true); fail(); }
        catch (IllegalArgumentException expected) { }
        try { new DirectoryQuery(screen, 1, 0, 1, world, 1, "", DirectoryTab.ALL, machine, false); fail(); }
        catch (IllegalArgumentException expected) { }
        try { new DirectoryQuery(screen, 1, 0, 0, world, 1, "", DirectoryTab.ALL, null, true); fail(); }
        catch (IllegalArgumentException expected) { }
    }
}
