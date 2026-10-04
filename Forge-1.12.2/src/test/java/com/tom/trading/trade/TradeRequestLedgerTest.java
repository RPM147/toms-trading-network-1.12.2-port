package com.tom.trading.trade;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class TradeRequestLedgerTest {
    @Test public void identicalReplayReturnsTheCommittedResultButDifferentBodyFails() {
        TradeRequestLedger ledger = new TradeRequestLedger();
        AtomicInteger commits = new AtomicInteger();
        TradeExecutor.Outcome first = ledger.execute(1, 1024, 9, () -> {
            commits.incrementAndGet(); return new TradeExecutor.Outcome(32, TradeResultCode.MACHINE_MISSING_INPUT);
        });
        assertSame(first, ledger.execute(1, 1024, 9, () -> { throw new AssertionError("Duplicate commit"); }));
        assertEquals(TradeResultCode.INVALID_REQUEST, ledger.replay(1, 1, 9).code);
        assertEquals(TradeResultCode.INVALID_REQUEST, ledger.replay(1, 1024, 10).code);
        assertEquals(1, commits.get());
    }
    @Test public void evictedAndOutOfOrderIdsNeverBecomeNewPurchases() {
        TradeRequestLedger ledger = new TradeRequestLedger();
        for (int id = 1; id <= 300; id++) ledger.execute(id, 1, 2, () -> new TradeExecutor.Outcome(1, TradeResultCode.SUCCESS));
        assertEquals(TradeResultCode.INVALID_REQUEST, ledger.execute(1, 1, 2, () -> { throw new AssertionError(); }).code);
        assertNull(ledger.replay(301, 1, 2));
        ledger.execute(500, 1, 2, () -> new TradeExecutor.Outcome(0, TradeResultCode.TRADER_MISSING_INPUT));
        assertEquals(TradeResultCode.INVALID_REQUEST, ledger.replay(499, 1, 2).code);
    }
    @Test public void throwingAndReentrantExecutionCannotRunAgain() {
        TradeRequestLedger ledger = new TradeRequestLedger();
        try {
            ledger.execute(1, 1, 2, () -> {
                assertEquals(TradeResultCode.INVARIANT_VIOLATION,
                        ledger.execute(1, 1, 2, () -> { throw new AssertionError(); }).code);
                throw new IllegalStateException("Unknown commit outcome");
            });
            fail();
        } catch (IllegalStateException expected) { }
        assertEquals(TradeResultCode.INVARIANT_VIOLATION, ledger.execute(1, 1, 2, () -> { throw new AssertionError(); }).code);
    }
    @Test public void sessionLedgersAreIndependentAndRejectInvalidIdsWithoutConsumingValidOnes() {
        TradeRequestLedger first = new TradeRequestLedger(), second = new TradeRequestLedger();
        assertEquals(TradeResultCode.INVALID_REQUEST, first.execute(0, 1, 1, () -> { throw new AssertionError(); }).code);
        assertEquals(TradeResultCode.INVALID_REQUEST, first.execute(1, 1025, 1, () -> { throw new AssertionError(); }).code);
        assertNull(first.replay(1, 1, 1));
        first.execute(1, 1, 1, () -> new TradeExecutor.Outcome(1, TradeResultCode.SUCCESS));
        assertNull(second.replay(1, 1, 1));
    }

    @Test public void positivePartialIsRecordedBeforeExactlyOneAnnouncementAndReceiptIsNotCached() {
        TradeRequestLedger ledger = new TradeRequestLedger();
        AtomicInteger published = new AtomicInteger();
        TradeReceipt receipt = TradeReceiptTest.sample();
        TradeExecutor.Outcome first = ledger.execute(1, 3, 9,
                () -> new TradeExecutor.Outcome(2, TradeResultCode.MACHINE_MISSING_INPUT, receipt), actual -> {
                    published.incrementAndGet(); assertSame(receipt, actual);
                    TradeExecutor.Outcome recorded = ledger.replay(1, 3, 9);
                    assertEquals(2, recorded.completed); assertNull(recorded.receipt);
                    assertSame(recorded, ledger.execute(1, 3, 9, () -> { throw new AssertionError("Reentrant purchase"); },
                            ignored -> { throw new AssertionError("Reentrant announcement"); }));
                });
        assertNull(first.receipt);
        assertSame(first, ledger.execute(1, 3, 9, () -> { throw new AssertionError("Replay purchase"); },
                ignored -> { throw new AssertionError("Replay announcement"); }));
        assertEquals(1, published.get());
    }

    @Test public void notificationFailureCannotChangeTheCommittedResultOrTriggerAnotherAttempt() {
        TradeRequestLedger ledger = new TradeRequestLedger();
        AtomicInteger notifications = new AtomicInteger();
        TradeExecutor.Outcome first = ledger.execute(1, 3, 9,
                () -> new TradeExecutor.Outcome(2, TradeResultCode.MACHINE_MISSING_INPUT, TradeReceiptTest.sample()), receipt -> {
                    notifications.incrementAndGet(); throw new IllegalStateException("Disconnected notification sink");
                });
        assertEquals(2, first.completed); assertEquals(TradeResultCode.MACHINE_MISSING_INPUT, first.code);
        assertSame(first, ledger.execute(1, 3, 9, () -> { throw new AssertionError(); },
                receipt -> { throw new AssertionError("Do not retry failed notifications"); }));
        assertEquals(1, notifications.get());
    }

    @Test public void zeroCompletionAndDeniedRequestsNeverAnnounce() {
        TradeRequestLedger ledger = new TradeRequestLedger();
        long id = 0;
        for (TradeResultCode code : new TradeResultCode[] { TradeResultCode.ACCESS_DENIED,
                TradeResultCode.OFFER_CHANGED, TradeResultCode.TRADER_NO_SPACE }) {
            ledger.execute(++id, 1, 9, () -> new TradeExecutor.Outcome(0, code),
                    receipt -> { throw new AssertionError("No completed trade to announce"); });
        }
    }
}
