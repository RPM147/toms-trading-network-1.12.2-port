package com.tom.trading.remote;

/** Monotonic lifetime; passive state synchronization never calls activity. */
public final class SessionLifetime {
    private final long started, idleLimit, absoluteLimit;
    private long activeAt;
    public SessionLifetime(long now, long idleLimit, long absoluteLimit) {
        if (idleLimit <= 0 || absoluteLimit < idleLimit) throw new IllegalArgumentException("Invalid lifetime");
        started = activeAt = now; this.idleLimit = idleLimit; this.absoluteLimit = absoluteLimit;
    }
    public boolean expired(long now) { return now - started >= absoluteLimit || now - activeAt >= idleLimit; }
    public void activity(long now) { if (!expired(now)) activeAt = now; }
}
