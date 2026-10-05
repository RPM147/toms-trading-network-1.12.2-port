package com.tom.trading.trade;

import com.tom.trading.book.*;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TradeBookEntryTest {
    private TradeReceipt receipt(UUID machine) {
        NBTTagCompound secret = new NBTTagCompound(); secret.setString("Secret", "private-data");
        TradeItem copper = new TradeItem("test:copper", 0, null, Collections.emptySet(), 64);
        TradeItem variant = new TradeItem("test:copper", 0, secret, Collections.emptySet(), 64);
        TradeItem iron = new TradeItem("test:iron", 0, null, Collections.emptySet(), 64);
        Map<TradeItem, Long> payment = new LinkedHashMap<>(); payment.put(copper, 1L); payment.put(variant, 2L);
        InventorySnapshot empty = InventorySnapshot.emptySlots(0, 64);
        TradePlan plan = new TradePlan(TradePolicy.NORMAL, 2, 1, TradeResultCode.MACHINE_MISSING_INPUT,
                empty, empty, empty, empty, empty, empty, payment, Collections.singletonMap(iron, 64L));
        return TradeReceipt.prepare(plan, item -> item.getItemId().equals("test:iron") ? "Iron Ingot" : "Copper Coin")
                .bind(UUID.randomUUID(), "Buyer\n", machine, UUID.randomUUID(), "Seller\r");
    }
    @Test public void conciseTextContainsOnlyBuyerSellerActualItemsAndQuantities() {
        String text = TradeBookEntry.format(receipt(UUID.randomUUID()));
        assertEquals("Buyer -> Seller | 64 Iron Ingot <- 3 Copper Coin", text);
        assertFalse(text.contains("private-data")); assertTrue(TradeBookData.validText(text));
    }
    @Test public void failedAndReplayedTradesDoNotCreateAdditionalBookRecords() {
        TradeBookData data = TradeBookData.create(UUID.randomUUID()); TradeBookBinding book = data.bind(UUID.randomUUID());
        TradeRequestLedger requests = new TradeRequestLedger();
        java.util.function.Consumer<TradeReceipt> record = receipt -> data.append(receipt.getMachineUuid(), TradeBookEntry.format(receipt));
        requests.execute(1, 2, 9, () -> new TradeExecutor.Outcome(1, TradeResultCode.MACHINE_MISSING_INPUT, receipt(book.machine)), record);
        requests.execute(1, 2, 9, () -> { throw new AssertionError("Replayed"); }, record);
        requests.execute(2, 1, 9, () -> new TradeExecutor.Outcome(0, TradeResultCode.ACCESS_DENIED), record);
        assertEquals(1, data.snapshot(book).size());
    }
    @Test public void unavailableBookStorageDoesNotSuppressAuditChatOrRepeatAPurchase() {
        java.util.concurrent.atomic.AtomicInteger audits = new java.util.concurrent.atomic.AtomicInteger(), chats = new java.util.concurrent.atomic.AtomicInteger();
        TradeRequestLedger requests = new TradeRequestLedger();
        java.util.function.Consumer<TradeReceipt> publish = receipt -> TradeCompletion.publish(receipt,
                ignored -> { throw new IllegalStateException("Book unavailable"); }, ignored -> audits.incrementAndGet(), ignored -> chats.incrementAndGet());
        TradeExecutor.Outcome committed = requests.execute(1, 2, 9,
                () -> new TradeExecutor.Outcome(1, TradeResultCode.MACHINE_MISSING_INPUT, receipt(UUID.randomUUID())), publish);
        requests.execute(1, 2, 9, () -> { throw new AssertionError("Replayed"); }, publish);
        assertEquals(1, committed.completed); assertEquals(1, audits.get()); assertEquals(1, chats.get());
    }
}
