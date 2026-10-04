package com.tom.trading.remote;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChunkLoadBudgetTest {
    @Test public void allTargetsShareOneColdLoadPerFiveTicksAndFourPreparationsPerTick() {
        ChunkLoadBudget budget = new ChunkLoadBudget();
        for (int tick = 1; tick <= 16; tick++) {
            budget.beginTick();
            boolean coldAllowed = (tick - 1) % 5 == 0;
            assertEquals(coldAllowed, budget.take(true, 5));
            assertFalse(budget.take(true, 5)); // Second player/neighbor cannot bypass the global interval.
            int warm = coldAllowed ? 3 : 4;
            for (int i = 0; i < warm; i++) assertTrue(budget.take(false, 5));
            assertEquals(4, budget.operations()); assertFalse(budget.take(false, 5));
        }
    }
    @Test public void blockedColdAttemptDoesNotConsumeWarmCapacityAndIntervalHasAHardFloor() {
        ChunkLoadBudget budget = new ChunkLoadBudget(); budget.beginTick();
        assertTrue(budget.take(true, -100));
        for (int tick = 2; tick < 6; tick++) {
            budget.beginTick(); assertFalse(budget.take(true, -100)); assertEquals(0, budget.operations());
            assertTrue(budget.take(false, 5));
        }
        budget.beginTick(); assertTrue(budget.take(true, 5));
    }
    @Test public void maximumFootprintFitsTheDefaultDeadlineAtNormalTickRateWithoutBulkLoading() {
        ChunkLoadBudget budget = new ChunkLoadBudget(); int loaded = 0, ticks = 0;
        while (loaded < RemoteSettings.MAX_CHUNKS_PER_TARGET && ticks < 200) {
            ticks++; budget.beginTick(); if (budget.take(true, 5)) loaded++;
        }
        assertEquals(25, loaded); assertEquals(121, ticks); // About 6.05 s at 20 TPS, not a runtime TPS guarantee.
    }
    @Test public void serverSettingsCannotExpandHardCeilings() {
        int oldChunks = RemoteSettings.maxLeasedChunks, oldTargets = RemoteSettings.maxTargetChunks;
        try {
            RemoteSettings.maxLeasedChunks = Integer.MAX_VALUE; RemoteSettings.maxTargetChunks = Integer.MAX_VALUE;
            assertEquals(64, RemoteSettings.leasedChunks()); assertEquals(8, RemoteSettings.chunks());
            RemoteSettings.maxLeasedChunks = Integer.MIN_VALUE; RemoteSettings.maxTargetChunks = Integer.MIN_VALUE;
            assertEquals(1, RemoteSettings.leasedChunks()); assertEquals(1, RemoteSettings.chunks());
        } finally { RemoteSettings.maxLeasedChunks = oldChunks; RemoteSettings.maxTargetChunks = oldTargets; }
    }
}
