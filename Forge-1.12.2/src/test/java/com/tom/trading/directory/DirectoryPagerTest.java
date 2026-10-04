package com.tom.trading.directory;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class DirectoryPagerTest {
    private static final long NOW = 10_000_000_000L;
    private MachineDirectoryEntry entry(int x, String owner, String name) {
        return new MachineDirectoryEntry(new MachineAddress(0, x, 64, 0), UUID.randomUUID(), UUID.randomUUID(), owner, name,
                MachineDirectoryEntry.Evidence.OBSERVED);
    }
    private MachineDirectoryData data(int size) {
        MachineDirectoryData data = MachineDirectoryData.create();
        for (int i = size - 1; i >= 0; i--) data.observe(entry(i, "Owner", "Shop"));
        return data;
    }
    private DirectoryQuery query(MachineDirectoryData data, int page, String search) {
        return new DirectoryQuery(UUID.randomUUID(), 1, 0, page, data.getWorldId(), data.getRevision(), search);
    }
    private DirectoryPage execute(MachineDirectoryData data, DirectoryQuery query) {
        List<DirectoryPage> answers = new ArrayList<>();
        data.pager().submit(UUID.randomUUID(), query, NOW, () -> true, answers::add);
        for (int i = 0; i < 100 && answers.isEmpty(); i++) assertTrue(data.pager().tick(NOW, d -> true) <= DirectoryPager.RECORDS_PER_TICK);
        assertEquals(1, answers.size()); return answers.get(0);
    }
    @Test public void stablePagingUsesAddressOrderAndEmptyFirstPageIsCheap() {
        MachineDirectoryData data = data(123);
        List<DirectoryPage> answers = new ArrayList<>();
        data.pager().submit(UUID.randomUUID(), query(data, 0, ""), NOW, () -> true, answers::add);
        assertEquals(50, data.pager().tick(NOW, d -> true));
        DirectoryPage first = answers.get(0), second = execute(data, query(data, 1, "")), last = execute(data, query(data, 2, ""));
        assertEquals(123, first.total); assertEquals(3, first.pageCount()); assertEquals(50, first.rows.size());
        assertEquals(0, first.rows.get(0).entry.address.x); assertEquals(49, first.rows.get(49).entry.address.x);
        assertEquals(50, second.rows.get(0).entry.address.x); assertEquals(23, last.rows.size());
        assertEquals(122, last.rows.get(22).entry.address.x);
        assertEquals(DirectoryPage.Result.STALE, execute(data, query(data, 3, "")).result);
        MachineDirectoryData empty = data(0);
        DirectoryPage zero = execute(empty, query(empty, 0, ""));
        assertEquals(0, zero.total); assertEquals(1, zero.pageCount());
    }
    @Test public void normalizedNameAndOwnerSearchDoesNotMergeDuplicateNicknames() {
        MachineDirectoryData data = data(0);
        data.observe(entry(0, "İPEK", "Demir Dükkânı")); data.observe(entry(1, "İpek", "Altın"));
        DirectoryPage owners = execute(data, query(data, 0, "ipek"));
        assertEquals(2, owners.total);
        assertNotEquals(owners.rows.get(0).entry.ownerId, owners.rows.get(1).entry.ownerId);
        assertEquals(1, execute(data, query(data, 0, "dukkanı")).total);
        assertEquals(1, execute(data, query(data, 0, owners.rows.get(0).entry.ownerId.toString())).total);
        assertEquals(0, execute(data, query(data, 0, "does-not-exist")).total);
    }
    @Test public void workIsBoundedAcrossJobsAndRevisionChangeAbortsPartialPages() {
        MachineDirectoryData data = data(10000);
        List<DirectoryPage> answers = new ArrayList<>();
        data.pager().submit(UUID.randomUUID(), query(data, 0, "Shop"), NOW, () -> true, answers::add);
        data.pager().submit(UUID.randomUUID(), query(data, 0, "Owner"), NOW, () -> true, answers::add);
        assertEquals(4096, data.pager().tick(NOW, d -> true)); assertTrue(answers.isEmpty());
        data.observe(entry(10001, "Changed", "Shop"));
        assertEquals(0, data.pager().tick(NOW, d -> true)); assertEquals(2, answers.size());
        for (DirectoryPage page : answers) { assertEquals(DirectoryPage.Result.STALE, page.result); assertTrue(page.rows.isEmpty()); }
        assertEquals(0, data.pager().pendingCount());
    }
    @Test public void queuesReplacePerPlayerExpireAndRejectOverCapacity() {
        MachineDirectoryData data = data(1); List<DirectoryPage> answers = new ArrayList<>();
        UUID player = UUID.randomUUID();
        data.pager().submit(player, query(data, 0, "first"), NOW, () -> true, p -> fail("Superseded query replied"));
        data.pager().submit(player, query(data, 0, "second"), NOW, () -> true, answers::add);
        assertEquals(1, data.pager().pendingCount()); data.pager().cancel(player);
        for (int i = 0; i < DirectoryPager.MAX_JOBS; i++)
            data.pager().submit(UUID.randomUUID(), query(data, 0, ""), NOW, () -> true, answers::add);
        data.pager().submit(UUID.randomUUID(), query(data, 0, ""), NOW, () -> true, answers::add);
        assertEquals(DirectoryPage.Result.BUSY, answers.get(0).result);
        data.pager().tick(NOW + DirectoryPager.TIMEOUT_NANOS, d -> true);
        assertEquals(65, answers.size()); assertEquals(0, data.pager().pendingCount());
        AtomicBoolean active = new AtomicBoolean(true);
        data.pager().submit(player, query(data, 0, ""), NOW, active::get, p -> fail("Disconnected player replied"));
        active.set(false); data.pager().tick(NOW, d -> true); assertEquals(0, data.pager().pendingCount());
    }
    @Test public void hintsConflictsUnsupportedAndRemovedDimensionsAreNeverLabelledAvailable() {
        MachineDirectoryData data = data(0); UUID duplicate = UUID.randomUUID();
        data.observe(new MachineDirectoryEntry(new MachineAddress(0, 0, 64, 0), duplicate, null, "", "", MachineDirectoryEntry.Evidence.OBSERVED));
        data.observe(new MachineDirectoryEntry(new MachineAddress(0, 1, 64, 0), duplicate, null, "", "", MachineDirectoryEntry.Evidence.OBSERVED));
        data.importHints(Arrays.asList(
                new MachineDirectoryEntry(new MachineAddress(0, 2, 64, 0), null, null, "", "", MachineDirectoryEntry.Evidence.HINT),
                new MachineDirectoryEntry(new MachineAddress(0, 3, 64, 0), UUID.randomUUID(), null, "", "", MachineDirectoryEntry.Evidence.HINT)), false);
        data.observe(new MachineDirectoryEntry(new MachineAddress(0, 4, 64, 0), null, null, "", "", MachineDirectoryEntry.Evidence.UNSUPPORTED));
        List<DirectoryPage.Row> rows = execute(data, query(data, 0, "")).rows;
        assertEquals(DirectoryPage.State.IDENTITY_CONFLICT, rows.get(0).state);
        assertEquals(DirectoryPage.State.IDENTITY_CONFLICT, rows.get(1).state);
        assertEquals(DirectoryPage.State.LEGACY, rows.get(2).state);
        assertEquals(DirectoryPage.State.UNVERIFIED, rows.get(3).state);
        assertEquals(DirectoryPage.State.UNSUPPORTED, rows.get(4).state);
        List<DirectoryPage> removed = new ArrayList<>();
        data.pager().submit(UUID.randomUUID(), query(data, 0, ""), NOW, () -> true, removed::add);
        data.pager().tick(NOW, d -> false);
        assertEquals(DirectoryPage.State.REMOVED_DIMENSION, removed.get(0).rows.get(0).state);
    }
    @Test public void staleSaveAndUnavailableDirectoryDoNotStartSearches() {
        MachineDirectoryData data = data(1);
        DirectoryQuery otherSave = new DirectoryQuery(UUID.randomUUID(), 1, 0, 0, UUID.randomUUID(), data.getRevision(), "");
        assertEquals(DirectoryPage.Result.STALE, execute(data, otherSave).result);
        MachineDirectoryData unreadable = new MachineDirectoryData(MachineDirectoryData.DATA_NAME);
        DirectoryQuery initial = new DirectoryQuery(UUID.randomUUID(), 1, 0, 0, null, 0, "");
        assertEquals(DirectoryPage.Result.UNAVAILABLE, execute(unreadable, initial).result);
        assertEquals(0, unreadable.pager().pendingCount());
    }
}
