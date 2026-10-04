package com.tom.trading.container;

/** Client request state without client-only linkage; never an authorization boundary. */
public final class TradeRequestTracker {
    private long nextId = 1, pendingId, submittedAt, cooldownAt;
    private int retryAfterMillis;

    public boolean canSubmit(long now) {
        return pendingId == 0 && (retryAfterMillis == 0 || now - cooldownAt >= retryAfterMillis * 1_000_000L);
    }

    public long begin(long now) {
        if (!canSubmit(now)) return 0;
        pendingId = nextId++;
        if (nextId <= 0) nextId = 1;
        submittedAt = now;
        return pendingId;
    }

    public boolean complete(long id, int delayMillis, long now) {
        if (id <= 0 || id != pendingId || delayMillis < 0 || delayMillis > 1000) return false;
        pendingId = 0;
        cooldownAt = now;
        retryAfterMillis = delayMillis;
        return true;
    }

    public boolean isTimedOut(long now) {
        // Do not unlock or automatically repeat an operation with an unknown outcome.
        return pendingId != 0 && now - submittedAt >= 10_000_000_000L;
    }
}
