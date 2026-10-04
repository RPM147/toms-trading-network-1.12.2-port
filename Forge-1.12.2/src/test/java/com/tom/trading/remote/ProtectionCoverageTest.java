package com.tom.trading.remote;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ProtectionCoverageTest {
    @Test public void unloadedNeighborNeverMeansNoForcefield() {
        assertTrue(ProtectionCoverage.loaded(0.5, 0.5, 2, (x, z) -> true));
        assertFalse(ProtectionCoverage.loaded(0.5, 0.5, 2, (x, z) -> x == 0 && z == 0));
        assertFalse(ProtectionCoverage.loaded(0.5, 0.5, 2, (x, z) -> !(x == -1 && z == -1)));
    }
    @Test public void searchUsesVanillaFloorCeilBoundsAndChecksEachChunkOnlyOnce() {
        Set<String> visited = new HashSet<>();
        assertTrue(ProtectionCoverage.loaded(-0.5, -0.5, 2, (x, z) -> { assertTrue(visited.add(x + ":" + z)); return true; }));
        assertEquals(16, visited.size()); assertTrue(visited.contains("-2:-2")); assertTrue(visited.contains("1:1"));
    }
    @Test public void invalidOrUnboundedCoordinatesCannotTurnIntoAnUnlimitedScan() {
        AtomicInteger calls = new AtomicInteger();
        for (double radius : new double[] {-1, 65, Double.NaN, Double.POSITIVE_INFINITY})
            assertFalse(ProtectionCoverage.loaded(0, 0, radius, (x, z) -> { calls.incrementAndGet(); return true; }));
        assertFalse(ProtectionCoverage.loaded(Double.NaN, 0, 2, (x, z) -> { calls.incrementAndGet(); return true; }));
        assertEquals(0, calls.get());
    }
    @Test public void reservationAndAuthorizationUseTheIdenticalBoundedSearchFootprint() {
        for (double x : new double[] {-33.5, -0.5, 0.5, 8.5, 16.5, 5828.5})
            for (double z : new double[] {-17.5, 0.5, 8.5, 6441.5}) {
                ProtectionCoverage.Bounds bounds = ProtectionCoverage.bounds(x, z, 2);
                assertNotNull(bounds); assertTrue(bounds.size() >= 9 && bounds.size() <= 16);
                Set<String> reservation = new HashSet<>();
                for (int cx = bounds.minX; cx < bounds.maxX; cx++) for (int cz = bounds.minZ; cz < bounds.maxZ; cz++)
                    reservation.add(cx + ":" + cz);
                assertTrue(ProtectionCoverage.loaded(x, z, 2, (cx, cz) -> reservation.remove(cx + ":" + cz)));
                assertTrue(reservation.isEmpty());
            }
    }
    @Test public void expandedEntityRadiusCannotSilentlyExceedThePerTargetLoadingCeiling() {
        assertTrue(ProtectionCoverage.bounds(0.5, 0.5, 64).size() > RemoteSettings.MAX_CHUNKS_PER_TARGET);
        assertNull(ProtectionCoverage.bounds(0.5, 0.5, Double.NaN));
    }
}
