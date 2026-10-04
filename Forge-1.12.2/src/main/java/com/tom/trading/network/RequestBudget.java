package com.tom.trading.network;

import com.tom.trading.trade.TradeLimits;

/** Per-player ingress budget, applied before work is enqueued on the server thread. */
public final class RequestBudget {
    public static final int MAX_TRADE_UNITS_PER_SECOND = TradeLimits.MAX_BATCH_SIZE;
    private long windowStart;
    private int requests, trades, tradeUnits, rejectionNotices, directoryRequests, remoteOpens;

    /** Zero is a non-trade request; positive values charge the requested batch, not its eventual result. */
    public synchronized boolean accept(long nowNanos, int requestedTradeUnits) {
        return admit(nowNanos, requestedTradeUnits).accepted;
    }

    public synchronized Admission admit(long nowNanos, int requestedTradeUnits) {
        if (requestedTradeUnits < 0 || requestedTradeUnits > TradeLimits.MAX_BATCH_SIZE) return new Admission(false, false, 0);
        resetWindow(nowNanos);
        boolean trade = requestedTradeUnits > 0;
        if (requests >= 40 || (trade && (trades >= 4
                || requestedTradeUnits > MAX_TRADE_UNITS_PER_SECOND - tradeUnits))) {
            boolean notify = trade && rejectionNotices < 4;
            if (notify) rejectionNotices++;
            return new Admission(false, notify, retryDelay(nowNanos));
        }
        requests++;
        if (trade) { trades++; tradeUnits += requestedTradeUnits; }
        int delay = requests >= 40 || (trade && (trades >= 4 || tradeUnits >= MAX_TRADE_UNITS_PER_SECOND))
                ? retryDelay(nowNanos) : 0;
        return new Admission(true, false, delay);
    }

    /** Browsing consumes the SAME total budget, plus its own two-per-second ceiling. */
    public synchronized Admission admitDirectory(long nowNanos) {
        resetWindow(nowNanos);
        if (directoryRequests >= 2) return new Admission(false, false, retryDelay(nowNanos));
        Admission admission = admit(nowNanos, 0);
        if (admission.accepted) directoryRequests++;
        return admission;
    }

    private void resetWindow(long nowNanos) {
        if (nowNanos - windowStart >= 1_000_000_000L || nowNanos < windowStart) {
            windowStart = nowNanos;
            requests = 0;
            trades = 0;
            tradeUnits = 0;
            rejectionNotices = 0;
            directoryRequests = 0;
            remoteOpens = 0;
        }
    }

    /** Cold-load/open admission is not a new total request budget. */
    public synchronized Admission admitRemoteOpen(long nowNanos) {
        resetWindow(nowNanos);
        if (remoteOpens >= 2) return new Admission(false, false, retryDelay(nowNanos));
        Admission admission = admit(nowNanos, 0);
        if (admission.accepted) remoteOpens++;
        return admission;
    }

    private int retryDelay(long now) {
        return (int) Math.max(1, Math.min(1000, (1_000_000_000L - (now - windowStart) + 999_999L) / 1_000_000L));
    }

    public static final class Admission {
        public final boolean accepted, notifyRejection;
        public final int retryAfterMillis;
        private Admission(boolean accepted, boolean notifyRejection, int retryAfterMillis) {
            this.accepted = accepted; this.notifyRejection = notifyRejection; this.retryAfterMillis = retryAfterMillis;
        }
    }
}
