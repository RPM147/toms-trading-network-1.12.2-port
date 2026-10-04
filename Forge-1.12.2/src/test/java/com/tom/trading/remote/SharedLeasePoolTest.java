package com.tom.trading.remote;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SharedLeasePoolTest {
    @Test public void pendingFootprintCountsBeforeAnyResourceIsLoadedAndAdmissionIsAtomic() {
        SharedLeasePool<Integer, String> pool = new SharedLeasePool<>(3, value -> true);
        SharedLeasePool<Integer, String>.Reservation first = pool.reserve(Arrays.asList(1, 2));
        assertNotNull(first); assertEquals(2, pool.size());
        assertNull(pool.reserve(Arrays.asList(2, 3, 4))); assertEquals(2, pool.size());
        SharedLeasePool<Integer, String>.Reservation last = pool.reserve(Collections.singleton(3));
        assertNotNull(last); assertEquals(3, pool.size());
        first.close(); assertEquals(1, pool.size()); last.close(); assertEquals(0, pool.size());
    }

    @Test public void overlappingTargetsAndPlayersShareEachPhysicalResourceUntilLastClose() {
        List<String> released = new ArrayList<>();
        SharedLeasePool<Integer, String> pool = new SharedLeasePool<>(3, value -> released.add(value));
        SharedLeasePool<Integer, String>.Reservation a = pool.reserve(Arrays.asList(1, 2));
        SharedLeasePool<Integer, String>.Reservation b = pool.reserve(Arrays.asList(2, 3));
        a.attach(1, "one"); a.attach(2, "shared"); b.attach(3, "three");
        assertSame(a.get(2), b.get(2)); assertEquals(3, pool.size());
        a.close(); assertEquals(Collections.singletonList("one"), released); assertTrue(b.current());
        a.close(); assertEquals(1, released.size());
        b.close(); assertEquals(Arrays.asList("one", "shared", "three"), released); assertEquals(0, pool.size());
    }

    @Test public void cancellationOrPartialLoadFailureReleasesBothLoadedAndUnpreparedSlots() {
        List<String> released = new ArrayList<>();
        SharedLeasePool<Integer, String> pool = new SharedLeasePool<>(4, value -> released.add(value));
        SharedLeasePool<Integer, String>.Reservation handle = pool.reserve(Arrays.asList(1, 2, 3, 4));
        handle.attach(1, "ready"); handle.attach(2, "ticket-before-failed-load");
        handle.close(); assertFalse(handle.current()); assertNull(handle.get(1));
        assertEquals(Arrays.asList("ready", "ticket-before-failed-load"), released); assertEquals(0, pool.size());
    }

    @Test public void failedReleaseStaysQuarantinedAndConsumesCapacity() {
        SharedLeasePool<Integer, String> pool = new SharedLeasePool<>(2, value -> !value.equals("leaked"));
        SharedLeasePool<Integer, String>.Reservation first = pool.reserve(Arrays.asList(1, 2));
        first.attach(1, "leaked"); first.attach(2, "released"); first.close();
        assertEquals(1, pool.size()); assertNull(pool.reserve(Collections.singleton(1)));
        assertNull(pool.reserve(Arrays.asList(3, 4))); assertEquals(1, pool.size());
        assertNotNull(pool.reserve(Collections.singleton(3))); assertEquals(2, pool.size());
    }

    @Test public void throwingReleaseCannotAbortTheRestOfCleanupOrFreeItsQuota() {
        List<String> released = new ArrayList<>();
        SharedLeasePool<Integer, String> pool = new SharedLeasePool<>(2, value -> {
            if (value.equals("broken")) throw new IllegalStateException("simulated Forge failure");
            return released.add(value);
        });
        SharedLeasePool<Integer, String>.Reservation handle = pool.reserve(Arrays.asList(1, 2));
        handle.attach(1, "broken"); handle.attach(2, "good"); handle.close();
        assertEquals(Collections.singletonList("good"), released); assertEquals(1, pool.size());
        assertNull(pool.reserve(Collections.singleton(1)));
    }

    @Test public void worldUnloadInvalidatesOldHandlesWithoutReleasingAReplacementWorldResource() {
        List<String> released = new ArrayList<>();
        SharedLeasePool<Integer, String> pool = new SharedLeasePool<>(2, value -> released.add(value));
        SharedLeasePool<Integer, String>.Reservation old = pool.reserve(Collections.singleton(1)); old.attach(1, "old-world");
        pool.discard(key -> key == 1); assertFalse(old.current()); assertNull(old.get(1));
        SharedLeasePool<Integer, String>.Reservation replacement = pool.reserve(Collections.singleton(1)); replacement.attach(1, "new-world");
        old.close(); assertTrue(replacement.current()); assertTrue(released.isEmpty());
        replacement.close(); assertEquals(Collections.singletonList("new-world"), released);
    }

    @Test public void shutdownReleasesSharedResourcesOnceAndInvalidatesEveryHandle() {
        List<String> released = new ArrayList<>();
        SharedLeasePool<Integer, String> pool = new SharedLeasePool<>(2, value -> released.add(value));
        SharedLeasePool<Integer, String>.Reservation a = pool.reserve(Arrays.asList(1, 2));
        SharedLeasePool<Integer, String>.Reservation b = pool.reserve(Collections.singleton(1));
        a.attach(1, "shared"); pool.shutdown();
        assertFalse(a.current()); assertFalse(b.current()); a.close(); b.close();
        assertEquals(Collections.singletonList("shared"), released); assertEquals(0, pool.size());
    }

    @Test public void duplicateKeysCannotDoubleCountAndResourcesCannotBeReplaced() {
        SharedLeasePool<Integer, String> pool = new SharedLeasePool<>(1, value -> true);
        SharedLeasePool<Integer, String>.Reservation handle = pool.reserve(Arrays.asList(1, 1, 1));
        assertEquals(1, pool.size()); handle.attach(1, "original");
        try { handle.attach(1, "replacement"); fail("Replacement must not orphan the original ticket"); }
        catch (IllegalStateException expected) { }
        assertEquals("original", handle.get(1)); handle.close(); assertEquals(0, pool.size());
    }
}
