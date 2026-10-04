package com.tom.trading.container;

import org.junit.Test;
import static org.junit.Assert.*;

public class TradeRequestTrackerTest {
    @Test public void pendingAndServerRetryDelayGateBothInputRoutes() {
        TradeRequestTracker state = new TradeRequestTracker();
        long start = 10_000_000_000L;
        long id = state.begin(start);
        assertTrue(id > 0);
        assertFalse(state.canSubmit(start + 300_000_000L));
        assertEquals(0, state.begin(start + 300_000_000L));
        assertTrue(state.complete(id, 700, start + 300_000_000L));
        assertFalse(state.canSubmit(start + 999_999_999L));
        assertTrue(state.canSubmit(start + 1_000_000_000L));
    }

    @Test public void delayedOrDuplicateFeedbackCannotUnlockANewerRequest() {
        TradeRequestTracker state = new TradeRequestTracker();
        long first = state.begin(0);
        assertFalse(state.complete(first + 1, 0, 1));
        assertTrue(state.complete(first, 0, 2));
        long second = state.begin(3);
        assertNotEquals(first, second);
        assertFalse(state.complete(first, 0, 4));
        assertFalse(state.canSubmit(5));
        assertFalse(state.complete(second, -1, 6));
        assertFalse(state.complete(second, 1001, 6));
        assertTrue(state.complete(second, 0, 7));
        assertTrue(state.canSubmit(7));
    }

    @Test public void timeoutDoesNotAutomaticallyRepeatAnUnconfirmedTrade() {
        TradeRequestTracker state = new TradeRequestTracker();
        long id = state.begin(-1_000_000L); // nanoTime may have a negative origin.
        assertFalse(state.isTimedOut(0));
        assertTrue(state.isTimedOut(10_000_000_000L));
        assertFalse(state.canSubmit(10_000_000_000L));
        assertEquals(0, state.begin(10_000_000_000L));
        assertTrue(state.complete(id, 0, 11_000_000_000L));
        assertTrue(state.canSubmit(11_000_000_000L));
    }
}
