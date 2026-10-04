package com.tom.trading.trade;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.HoverEvent;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class TradeReceiptTest {
    @Test public void sameItemInBothDirectionsIsRecordedEvenWhenAllInventoriesHaveZeroNetChange() {
        TradeItem coin = item("test:coin", "", 1024);
        TradeDefinition leg = TradeDefinition.direct(coin, 1024, true);
        TradeOffer offer = new TradeOffer(Arrays.asList(leg, leg, leg, leg), Arrays.asList(leg, leg, leg, leg));
        InventorySnapshot player = InventorySnapshot.of(InventorySlot.occupied(coin, 1024, 1024),
                InventorySlot.occupied(coin, 1024, 1024), InventorySlot.occupied(coin, 1024, 1024),
                InventorySlot.occupied(coin, 1024, 1024));
        InventorySnapshot machine = InventorySnapshot.emptySlots(8, 1024);
        TradePlan plan = TradePlanner.plan(offer, TradePolicy.CREATIVE, 1024, player, machine, machine);
        assertTrue(plan.isComplete()); assertEquals(player, plan.getPlayerAfter());
        assertEquals(Collections.singletonMap(coin, 4_194_304L), plan.getPaidItems());
        assertEquals(Collections.singletonMap(coin, 4_194_304L), plan.getDeliveredItems());
        TradeReceipt receipt = bind(TradeReceipt.prepare(plan, item -> "Copper Coin"));
        assertEquals("Buyer bought 4194304 Copper Coin from Seller for 4194304 Copper Coin",
                receipt.createAnnouncement().getUnformattedText());
        assertEquals(receipt.getPayments().get(0).getVariant(), receipt.getDeliveries().get(0).getVariant());
    }

    @Test public void mixedActualVariantsRemainDistinctAndEveryLegAppearsInTheTooltip() {
        TradeItem payA = item("test:coin_a", "", 64), payB = item("test:coin_b", "", 64);
        TradeItem saleA = item("test:gem", "first-secret-nbt", 64), saleB = item("test:gem", "second-secret-nbt", 64);
        Map<TradeItem, Long> payments = new LinkedHashMap<>(), deliveries = new LinkedHashMap<>();
        payments.put(payA, 3L); payments.put(payB, 7L); deliveries.put(saleA, 16L); deliveries.put(saleB, 2L);
        TradeReceipt receipt = bind(TradeReceipt.prepare(displayPlan(payments, deliveries), item -> "Same name"));
        ITextComponent chat = receipt.createAnnouncement();
        assertTrue(chat.getUnformattedText().contains("+ 1 more item variant(s) [hover]"));
        assertNotEquals(receipt.getDeliveries().get(0).getVariant(), receipt.getDeliveries().get(1).getVariant());
        String details = chat.getStyle().getHoverEvent().getValue().getUnformattedText();
        assertTrue(details.contains("16 Same name [test:gem@0; variant #3]"));
        assertTrue(details.contains("2 Same name [test:gem@0; variant #4]"));
        assertTrue(details.contains("3 Same name [test:coin_a@0; variant #1]"));
        assertTrue(details.contains("7 Same name [test:coin_b@0; variant #2]"));
        assertFalse(ITextComponent.Serializer.componentToJson(chat).contains("secret-nbt"));
        try { receipt.getPayments().clear(); fail(); } catch (UnsupportedOperationException expected) { }
        chat.getStyle().setInsertion("mutated outside receipt");
        assertNull(receipt.createAnnouncement().getStyle().getInsertion());
    }

    @Test public void untrustedLabelsCannotInjectFormattingLinesOrInteractiveChatActions() {
        TradeItem item = item("test:item", "private payload", 64);
        TradePlan plan = displayPlan(Collections.singletonMap(item, 1L), Collections.singletonMap(item, 2L));
        String name = "\u00a7cGem\n\r\t\u202e\u2028\u2029\ud800";
        TradeReceipt receipt = TradeReceipt.prepare(plan, identity -> name).bind(UUID.randomUUID(),
                "\u00a7kBuyer\n", UUID.randomUUID(), UUID.randomUUID(), "\u00a7aSeller\u202e");
        ITextComponent chat = receipt.createAnnouncement();
        assertEquals("Buyer bought 2 Gem from Seller for 1 Gem", chat.getUnformattedText());
        assertNull(chat.getStyle().getClickEvent()); assertNull(chat.getStyle().getInsertion());
        HoverEvent hover = chat.getStyle().getHoverEvent(); assertEquals(HoverEvent.Action.SHOW_TEXT, hover.getAction());
        assertNull(hover.getValue().getStyle().getClickEvent()); assertNull(hover.getValue().getStyle().getHoverEvent());
        assertEquals("fallback", ReceiptText.label(String.join("", Collections.nCopies(10000, "\u00a7k\u202e")), 48, "fallback"));
    }

    @Test public void maximumReceiptStaysBoundedAndSurvivesVanillaChatPacketEncoding() throws Exception {
        Map<TradeItem, Long> payments = new LinkedHashMap<>(), deliveries = new LinkedHashMap<>();
        for (int index = 0; index < 32; index++) {
            payments.put(item("test:payment_" + index + String.join("", Collections.nCopies(100, "x")), "", 1024), 4_194_304L);
            deliveries.put(item("test:sale_" + index + String.join("", Collections.nCopies(100, "x")), "", 1024), 4_194_304L);
        }
        String huge = String.join("", Collections.nCopies(500, "<>&'\"\\\ud83d\ude00"));
        TradeReceipt receipt = TradeReceipt.prepare(displayPlan(payments, deliveries), item -> huge)
                .bind(UUID.randomUUID(), huge, UUID.randomUUID(), UUID.randomUUID(), huge);
        ITextComponent chat = receipt.createAnnouncement();
        assertTrue(chat.getUnformattedText().codePointCount(0, chat.getUnformattedText().length()) <= 384);
        assertTrue(ITextComponent.Serializer.componentToJson(chat).getBytes(StandardCharsets.UTF_8).length <= 32767);
        assertEquals(64, receipt.getPayments().size() + receipt.getDeliveries().size());
        for (TradeReceipt.Line line : receipt.getDeliveries()) assertTrue(line.getLabel().endsWith("\u2026"));
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            new SPacketChat(chat, ChatType.CHAT).writePacketData(buffer);
            SPacketChat decoded = new SPacketChat(); decoded.readPacketData(buffer);
            assertEquals(ChatType.CHAT, decoded.getType());
            assertEquals(chat.getUnformattedText(), decoded.getChatComponent().getUnformattedText());
            assertEquals(chat.getStyle().getHoverEvent().getValue().getUnformattedText(),
                    decoded.getChatComponent().getStyle().getHoverEvent().getValue().getUnformattedText());
        } finally { buffer.release(); }
    }

    @Test public void brokenNameCallbackFallsBackOncePerIdentityAndOwnerlessIsNotCalledTheBuyer() {
        TradeItem item = item("test:item", "", 64);
        AtomicInteger names = new AtomicInteger();
        TradeReceipt receipt = TradeReceipt.prepare(displayPlan(Collections.singletonMap(item, 3L),
                Collections.singletonMap(item, 2L)), identity -> {
            names.incrementAndGet(); throw new IllegalStateException("Broken third-party display name");
        }).bind(UUID.randomUUID(), "Buyer", UUID.randomUUID(), null, "stale owner cache");
        assertEquals(1, names.get()); assertNull(receipt.getOwnerUuid());
        assertEquals("Unowned machine", receipt.getOwnerName());
        assertEquals("test:item", receipt.getDeliveries().get(0).getLabel());
    }

    @Test public void failedPlansHaveNoReceiptAndLegMapsAreImmutable() {
        TradeItem item = item("test:item", "", 64);
        TradeOffer offer = new TradeOffer(Collections.singletonList(TradeDefinition.direct(item, 1, true)),
                Collections.singletonList(TradeDefinition.direct(item, 1, true)));
        InventorySnapshot empty = InventorySnapshot.emptySlots(8, 64);
        TradePlan failed = TradePlanner.plan(offer, TradePolicy.NORMAL, 1, empty, empty, empty);
        try { TradeReceipt.prepare(failed, identity -> "Unused"); fail(); } catch (IllegalArgumentException expected) { }
        try { failed.getPaidItems().put(item, 1L); fail(); } catch (UnsupportedOperationException expected) { }
    }

    static TradeReceipt sample() {
        TradeItem coin = item("test:coin", "", 64), apple = item("test:apple", "", 64);
        return bind(TradeReceipt.prepare(displayPlan(Collections.singletonMap(coin, 3L),
                Collections.singletonMap(apple, 2L)), TradeItem::getItemId));
    }
    private static TradeReceipt bind(TradeReceipt.Prepared receipt) {
        return receipt.bind(UUID.randomUUID(), "Buyer", UUID.randomUUID(), UUID.randomUUID(), "Seller");
    }
    private static TradeItem item(String id, String variant, int limit) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("variant", variant);
        return new TradeItem(id, 0, tag, Collections.emptySet(), limit);
    }
    /** Renderer-only fixture. Planner-derived legs are checked separately, including capacity alternatives. */
    private static TradePlan displayPlan(Map<TradeItem, Long> payments, Map<TradeItem, Long> deliveries) {
        InventorySnapshot empty = InventorySnapshot.emptySlots(0, 64);
        return new TradePlan(TradePolicy.NORMAL, 3, 2, TradeResultCode.MACHINE_MISSING_INPUT,
                empty, empty, empty, empty, empty, empty, payments, deliveries);
    }
}
