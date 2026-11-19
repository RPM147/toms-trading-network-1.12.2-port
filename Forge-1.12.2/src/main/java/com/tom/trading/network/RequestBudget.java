package com.tom.trading.network;

import com.tom.trading.trade.TradeLimits;
import java.util.Arrays;

/** Per-player ingress budget, applied before work is enqueued on the server thread. */
public final class RequestBudget {
    public static final int MAX_TRADE_UNITS_PER_SECOND = TradeLimits.MAX_BATCH_SIZE;
    private enum Channel { DIRECTORY, REMOTE_OPEN, BOOK }
    private long windowStart;
    private int requests, trades, tradeUnits, rejectionNotices;
    private final int[] channelRequests = new int[Channel.values().length];
    private final int[] channelRejections = new int[Channel.values().length];

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
        return admitChannel(nowNanos, Channel.DIRECTORY);
    }

    private void resetWindow(long nowNanos) {
        if (nowNanos - windowStart >= 1_000_000_000L || nowNanos < windowStart) {
            windowStart = nowNanos;
            requests = 0;
            trades = 0;
            tradeUnits = 0;
            rejectionNotices = 0;
            Arrays.fill(channelRequests, 0);
            Arrays.fill(channelRejections, 0);
        }
    }

    /** Cold-load/open admission is not a new total request budget. */
    public synchronized Admission admitRemoteOpen(long nowNanos) {
        return admitChannel(nowNanos, Channel.REMOTE_OPEN);
    }

    /** Trade-book binding and reading share the total budget, independent of directory browsing. */
    public synchronized Admission admitBook(long nowNanos) {
        return admitChannel(nowNanos, Channel.BOOK);
    }

    private Admission admitChannel(long nowNanos, Channel channel) {
        resetWindow(nowNanos);
        int slot = channel.ordinal();
        if (channelRequests[slot] >= 2 || requests >= 40) {
            boolean notify = channelRejections[slot] < 4;
            if (notify) channelRejections[slot]++;
            return new Admission(false, notify, retryDelay(nowNanos));
        }
        Admission admission = admit(nowNanos, 0);
        if (admission.accepted) channelRequests[slot]++;
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
