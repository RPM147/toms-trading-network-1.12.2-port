package com.tom.trading.trade;

import com.tom.trading.BuildInfo;
import org.apache.logging.log4j.LogManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Server-thread only, owned by one player/container session. Evicted IDs never execute again. */
public final class TradeRequestLedger {
    private static final int MAX_RESULTS = 256;
    private final Map<Long, Entry> results = new LinkedHashMap<>();
    private long highestId;

    public TradeExecutor.Outcome replay(long id, int count, long revision) {
        Entry entry = results.get(id);
        if (entry != null) return entry.count == count && entry.revision == revision
                ? entry.result : rejected();
        return id <= highestId ? rejected() : null;
    }

    public TradeExecutor.Outcome execute(long id, int count, long revision, Supplier<TradeExecutor.Outcome> action) {
        return execute(id, count, revision, action, receipt -> {});
    }

    public TradeExecutor.Outcome execute(long id, int count, long revision, Supplier<TradeExecutor.Outcome> action,
                                         Consumer<TradeReceipt> afterRecord) {
        TradeExecutor.Outcome previous = replay(id, count, revision);
        if (previous != null) return previous;
        if (id <= 0 || revision <= 0 || count < 1 || count > TradeLimits.MAX_BATCH_SIZE) return rejected();
        highestId = id;
        // Reserve before calling code that may re-enter or throw after an inventory mutation.
        Entry entry = new Entry(count, revision);
        results.put(id, entry);
        if (results.size() > MAX_RESULTS) results.remove(results.keySet().iterator().next());
        TradeExecutor.Outcome outcome = action.get();
        // Cache only the small replay result, never item labels/NBT/receipts for 256 requests per viewer.
        entry.result = outcome.withoutReceipt();
        if (outcome.completed > 0 && outcome.receipt != null) {
            try { afterRecord.accept(outcome.receipt); }
            catch (RuntimeException | LinkageError failure) {
                LogManager.getLogger(BuildInfo.MOD_ID).error(
                        "Trade committed and recorded, but its announcement failed; it will not be retried", failure);
            }
        }
        return entry.result;
    }

    private static TradeExecutor.Outcome rejected() {
        return new TradeExecutor.Outcome(0, TradeResultCode.INVALID_REQUEST);
    }
    private static final class Entry {
        final int count;
        final long revision;
        TradeExecutor.Outcome result = new TradeExecutor.Outcome(0, TradeResultCode.INVARIANT_VIOLATION);
        Entry(int count, long revision) { this.count = count; this.revision = revision; }
    }
}
