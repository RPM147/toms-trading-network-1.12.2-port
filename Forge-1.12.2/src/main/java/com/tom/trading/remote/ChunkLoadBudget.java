package com.tom.trading.remote;

/** One server-wide budget, shared by all pending targets and their protection neighborhoods. */
final class ChunkLoadBudget {
    private long tick, nextColdTick;
    private int remaining;
    void beginTick() { tick++; remaining = 4; }
    int operations() { return 4 - remaining; }
    boolean take(boolean cold, int interval) {
        if (remaining == 0 || (cold && tick < nextColdTick)) return false;
        remaining--;
        if (cold) nextColdTick = tick + Math.max(5, interval);
        return true;
    }
}
