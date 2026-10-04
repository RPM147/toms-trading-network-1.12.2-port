package com.tom.trading.client;

import com.tom.trading.directory.*;
import com.tom.trading.remote.RemoteOpenRequest;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RemoteOpenTrackerTest {
    private DirectoryPage page() {
        DirectoryQuery query = new DirectoryQuery(UUID.randomUUID(), 1, -1, 0, null, 0, "");
        MachineDirectoryEntry entry = new MachineDirectoryEntry(new MachineAddress(13, 16, 70, -4), UUID.randomUUID(),
                UUID.randomUUID(), "Owner", "Shop", MachineDirectoryEntry.Evidence.OBSERVED);
        return new DirectoryPage(query, DirectoryPage.Result.OK, UUID.randomUUID(), 7, 1, false,
                Collections.singletonList(new DirectoryPage.Row(entry, DirectoryPage.State.RECORDED)));
    }
    @Test public void differentSelectionAndClosedScreenCannotAcceptLateOpen() {
        RemoteOpenTracker tracker = new RemoteOpenTracker(); DirectoryPage page = page();
        RemoteOpenRequest request = tracker.begin(page, page.rows.get(0), 1);
        assertNotNull(request); assertTrue(tracker.waiting());
        assertNull(tracker.begin(page, page.rows.get(0), 1_000_000_000L));
        assertFalse(tracker.accept(new RemoteOpenRequest(request.screenId, request.requestId, request.worldId, request.revision,
                0, request.address, request.machineId)));
        assertSame(request, tracker.close()); assertFalse(tracker.accept(request));
        assertNull(tracker.begin(page, page.rows.get(0), 20_000_000_000L));
    }
    @Test public void timeoutCancelsWithoutAutomaticReopenOrPurchase() {
        RemoteOpenTracker tracker = new RemoteOpenTracker(); DirectoryPage page = page();
        RemoteOpenRequest request = tracker.begin(page, page.rows.get(0), -20_000_000_000L);
        assertNotNull(request); assertNull(tracker.expire(-10_000_000_000L));
        assertSame(request, tracker.expire(-4_000_000_000L)); assertFalse(tracker.waiting());
        assertFalse(tracker.accept(request));
        RemoteOpenRequest next = tracker.begin(page, page.rows.get(0), 1);
        assertTrue(next.requestId > request.requestId); assertTrue(tracker.accept(next));
    }
    @Test public void rowMustBelongToRenderedPageAndRapidReopensAreThrottled() {
        DirectoryPage page = page(), unrelated = page(); RemoteOpenTracker tracker = new RemoteOpenTracker();
        assertNull(tracker.begin(page, unrelated.rows.get(0), 1));
        RemoteOpenRequest request = tracker.begin(page, page.rows.get(0), 1); assertTrue(tracker.accept(request));
        assertNull(tracker.begin(page, page.rows.get(0), 100));
        assertNotNull(tracker.begin(page, page.rows.get(0), 1_000_000_000L));
    }
}
