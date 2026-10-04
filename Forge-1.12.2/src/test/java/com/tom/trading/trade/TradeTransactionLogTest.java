package com.tom.trading.trade;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class TradeTransactionLogTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void jsonRecordsActualPartialAmountsWithUtcAndIdentityButNeverRawNbt() throws Exception {
        TradeReceipt receipt = receipt();
        String text = TradeLogEntry.encode(receipt, Instant.parse("2026-10-04T12:13:14Z"));
        JsonObject entry = new JsonParser().parse(text).getAsJsonObject();
        assertEquals(1, entry.get("schema").getAsInt());
        assertEquals("2026-10-04T12:13:14Z", entry.get("timeUtc").getAsString());
        assertEquals(receipt.getBuyerUuid().toString(), entry.get("buyerUuid").getAsString());
        assertEquals(receipt.getMachineUuid().toString(), entry.get("machineUuid").getAsString());
        assertEquals(receipt.getOwnerUuid().toString(), entry.get("ownerUuid").getAsString());
        assertEquals("Alıcı", entry.get("buyerName").getAsString());
        assertEquals("Satıcı", entry.get("ownerName").getAsString());
        assertEquals(2, entry.get("completedTrades").getAsInt());
        JsonObject paid = entry.getAsJsonArray("payments").get(0).getAsJsonObject();
        assertEquals("test:coin", paid.get("itemId").getAsString());
        assertEquals(7, paid.get("metadata").getAsInt());
        assertEquals(3_000_000_000L, paid.get("quantity").getAsLong());
        assertEquals("Copper \"Coin\" \\ test", paid.get("label").getAsString());
        assertEquals(2, entry.getAsJsonArray("deliveries").get(0).getAsJsonObject().get("quantity").getAsLong());
        assertFalse(text.contains("private-inventory-data"));
        assertFalse(text.contains("private-capability-data"));
        assertFalse(text.contains("\n"));
        assertFalse(text.contains("\r"));
    }

    @Test public void appendFlushesUtf8ImmediatelyAndRestartPreservesExistingRecords() throws Exception {
        Path folder = temp.newFolder().toPath();
        String entry = TradeLogEntry.encode(receipt(), Instant.EPOCH);
        try (RotatingTradeLog log = new RotatingTradeLog(folder, 10000, 2)) {
            log.append(entry);
            assertEquals(entry + "\n", read(folder.resolve("transactions.jsonl")));
        }
        try (RotatingTradeLog log = new RotatingTradeLog(folder, 10000, 2)) {
            log.append(entry);
        }
        assertEquals(entry + "\n" + entry + "\n", read(folder.resolve("transactions.jsonl")));
    }

    @Test public void rotationUsesUtf8ByteCountsAndKeepsOnlyConfiguredArchives() throws Exception {
        Path folder = temp.newFolder().toPath();
        String first = "{\"ş\":0}";
        int limit = (first + "\n").getBytes(StandardCharsets.UTF_8).length;
        try (RotatingTradeLog log = new RotatingTradeLog(folder, limit, 2)) {
            for (int number = 0; number < 5; number++) log.append("{\"ş\":" + number + "}");
        }
        assertEquals("{\"ş\":4}\n", read(folder.resolve("transactions.jsonl")));
        assertEquals("{\"ş\":3}\n", read(folder.resolve("transactions.1.jsonl")));
        assertEquals("{\"ş\":2}\n", read(folder.resolve("transactions.2.jsonl")));
        assertFalse(Files.exists(folder.resolve("transactions.3.jsonl")));
        try (java.util.stream.Stream<Path> paths = Files.list(folder)) { assertEquals(3, paths.count()); }
    }

    @Test public void restartSeparatesAnInterruptedFinalLineWithoutDestroyingItsEvidence() throws Exception {
        Path folder = temp.newFolder().toPath();
        Files.write(folder.resolve("transactions.jsonl"), "{\"interrupted\":".getBytes(StandardCharsets.UTF_8));
        try (RotatingTradeLog log = new RotatingTradeLog(folder, 100, 2)) { log.append("{\"complete\":true}"); }
        assertEquals("{\"interrupted\":\n{\"complete\":true}\n", read(folder.resolve("transactions.jsonl")));
    }

    @Test public void oversizedOrMultilineEntryNeverWritesOrRotatesAnything() throws Exception {
        Path folder = temp.newFolder().toPath();
        try (RotatingTradeLog log = new RotatingTradeLog(folder, 16, 2)) {
            log.append("{}");
            for (String invalid : new String[] {"{}\n{}", "{}\r{}", String.join("", Collections.nCopies(17, "x"))}) {
                try { log.append(invalid); fail("Invalid line must fail before any write"); }
                catch (IOException expected) { }
            }
            assertEquals("{}\n", read(folder.resolve("transactions.jsonl")));
            assertFalse(Files.exists(folder.resolve("transactions.1.jsonl")));
        }
    }

    @Test public void closedLogRejectsFurtherWritesAndInvalidDirectoryFailsCleanly() throws Exception {
        Path folder = temp.newFolder().toPath();
        RotatingTradeLog log = new RotatingTradeLog(folder, 100, 1);
        log.close(); log.close();
        try { log.append("{}"); fail(); } catch (IOException expected) { }
        Path notDirectory = temp.newFile().toPath();
        try { new RotatingTradeLog(notDirectory, 100, 1); fail(); } catch (IOException expected) { }
    }

    @Test public void committedPartialAndReplayWriteExactlyOneLineWhileFailedPurchaseWritesNone() throws Exception {
        Path folder = temp.newFolder().toPath();
        try (RotatingTradeLog log = new RotatingTradeLog(folder, 10000, 2)) {
            TradeRequestLedger ledger = new TradeRequestLedger();
            java.util.function.Consumer<TradeReceipt> publish = receipt -> TradeCompletion.publish(receipt, actual -> {
                try { log.append(TradeLogEntry.encode(actual, Instant.EPOCH)); }
                catch (IOException failure) { throw new IllegalStateException(failure); }
            }, ignored -> {});
            ledger.execute(1, 3, 9, () -> new TradeExecutor.Outcome(2, TradeResultCode.MACHINE_MISSING_INPUT, receipt()), publish);
            ledger.execute(1, 3, 9, () -> { throw new AssertionError("Replayed purchase"); }, publish);
            ledger.execute(2, 1, 9, () -> new TradeExecutor.Outcome(0, TradeResultCode.ACCESS_DENIED), publish);
            assertEquals(1, Files.readAllLines(folder.resolve("transactions.jsonl"), StandardCharsets.UTF_8).size());
        }
    }

    @Test public void brokenAuditDoesNotSuppressChatOrChangeCommittedResultAndBrokenChatDoesNotSuppressAudit() {
        AtomicInteger audits = new AtomicInteger(), chats = new AtomicInteger();
        TradeRequestLedger ledger = new TradeRequestLedger();
        TradeExecutor.Outcome result = ledger.execute(1, 3, 9,
                () -> new TradeExecutor.Outcome(2, TradeResultCode.MACHINE_MISSING_INPUT, receipt()),
                receipt -> TradeCompletion.publish(receipt, ignored -> {
                    audits.incrementAndGet(); throw new IllegalStateException("Disk unavailable");
                }, ignored -> chats.incrementAndGet()));
        assertEquals(2, result.completed);
        assertEquals(1, audits.get()); assertEquals(1, chats.get());
        TradeCompletion.publish(receipt(), ignored -> audits.incrementAndGet(), ignored -> {
            chats.incrementAndGet(); throw new IllegalStateException("Chat unavailable");
        });
        assertEquals(2, audits.get()); assertEquals(2, chats.get());
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static TradeReceipt receipt() {
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("secret", "private-inventory-data");
        NBTTagCompound capabilities = new NBTTagCompound(); capabilities.setString("secret", "private-capability-data");
        TradeItem coin = new TradeItem("test:coin", 7, tag, capabilities, Collections.emptySet(), 64);
        TradeItem apple = new TradeItem("test:apple", 0, null, Collections.emptySet(), 64);
        InventorySnapshot empty = InventorySnapshot.emptySlots(0, 64);
        TradePlan plan = new TradePlan(TradePolicy.NORMAL, 3, 2, TradeResultCode.MACHINE_MISSING_INPUT,
                empty, empty, empty, empty, empty, empty,
                Collections.singletonMap(coin, 3_000_000_000L), Collections.singletonMap(apple, 2L));
        return TradeReceipt.prepare(plan, item -> "Copper \"Coin\" \\ test")
                .bind(UUID.randomUUID(), "Alıcı\n", UUID.randomUUID(), UUID.randomUUID(), "Satıcı\r");
    }
}
