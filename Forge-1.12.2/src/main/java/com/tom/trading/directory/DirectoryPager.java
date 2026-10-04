package com.tom.trading.directory;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/** Save-owned cooperative query queue. Work is metadata-only and bounded across all players. */
public final class DirectoryPager {
    public static final int MAX_JOBS = 64, RECORDS_PER_TICK = 4096, SLICE = 256;
    public static final long TIMEOUT_NANOS = 10_000_000_000L;
    private final MachineDirectoryData data;
    private final LinkedHashMap<UUID, Job> jobs = new LinkedHashMap<>();

    DirectoryPager(MachineDirectoryData data) { this.data = data; }
    public int pendingCount() { return jobs.size(); }
    public void submit(UUID player, DirectoryQuery query, long now, BooleanSupplier active, Consumer<DirectoryPage> answer) {
        jobs.remove(player); // New search supersedes the old search; never retain multiple jobs per player.
        if (!data.isSupported()) { answer.accept(error(query, DirectoryPage.Result.UNAVAILABLE)); return; }
        if (query.expectedWorld != null && (!query.expectedWorld.equals(data.getWorldId())
                || query.expectedRevision != data.getRevision())) {
            answer.accept(error(query, DirectoryPage.Result.STALE)); return;
        }
        if (jobs.size() >= MAX_JOBS) { answer.accept(error(query, DirectoryPage.Result.BUSY)); return; }
        jobs.put(player, new Job(query, now, active, answer));
    }
    public void cancel(UUID player) { jobs.remove(player); }

    /** Returns visited record count for focused bounds tests, not a runtime TPS metric. */
    public int tick(long now, IntPredicate registeredDimension) {
        int visited = 0, turns = 0;
        while (!jobs.isEmpty() && visited < RECORDS_PER_TICK && turns++ < MAX_JOBS) {
            Iterator<Map.Entry<UUID, Job>> iterator = jobs.entrySet().iterator();
            Map.Entry<UUID, Job> first = iterator.next();
            UUID player = first.getKey(); Job job = first.getValue(); iterator.remove();
            if (!job.active.getAsBoolean()) continue;
            if (!data.isSupported()) { job.answer.accept(error(job.query, DirectoryPage.Result.UNAVAILABLE)); continue; }
            if (job.revision != data.getRevision()) { job.answer.accept(error(job.query, DirectoryPage.Result.STALE)); continue; }
            if (now - job.started >= TIMEOUT_NANOS || now < job.started) {
                job.answer.accept(error(job.query, DirectoryPage.Result.BUSY)); continue;
            }
            int allowance = Math.min(SLICE, RECORDS_PER_TICK - visited);
            while (allowance-- > 0 && !job.pageComplete() && job.entries.hasNext()) {
                MachineDirectoryEntry entry = job.entries.next(); visited++;
                if ((job.query.tab == DirectoryTab.ECONOMY && !data.isSpecial(entry.machineId)) || !entry.matchesSearch(job.search)) continue;
                int matchingIndex = job.total++;
                if (matchingIndex >= job.query.page * DirectoryQuery.PAGE_SIZE && job.rows.size() < DirectoryQuery.PAGE_SIZE)
                    job.rows.add(row(entry, registeredDimension));
            }
            if (!job.pageComplete() && job.entries.hasNext()) jobs.put(player, job);
            else if (job.query.page > 0 && job.rows.isEmpty()) job.answer.accept(error(job.query, DirectoryPage.Result.STALE));
            else job.answer.accept(new DirectoryPage(job.query, DirectoryPage.Result.OK, data.getWorldId(), job.revision,
                    job.unfiltered() ? data.size() : job.total, data.isBackfillComplete(), job.rows));
        }
        return visited;
    }
    private DirectoryPage.Row row(MachineDirectoryEntry entry, IntPredicate registeredDimension) {
        DirectoryPage.State state;
        if (!registeredDimension.test(entry.address.dimension)) state = DirectoryPage.State.REMOVED_DIMENSION;
        else if (entry.evidence == MachineDirectoryEntry.Evidence.UNSUPPORTED) state = DirectoryPage.State.UNSUPPORTED;
        else if (entry.machineId == null) state = DirectoryPage.State.LEGACY;
        else if (data.hasIdentityConflict(entry.machineId)) state = DirectoryPage.State.IDENTITY_CONFLICT;
        else if (entry.evidence == MachineDirectoryEntry.Evidence.HINT) state = DirectoryPage.State.UNVERIFIED;
        else state = DirectoryPage.State.RECORDED;
        return new DirectoryPage.Row(entry, state);
    }
    private DirectoryPage error(DirectoryQuery query, DirectoryPage.Result result) {
        return new DirectoryPage(query, result, data.getWorldId(), data.isSupported() ? data.getRevision() : 0,
                0, false, Collections.emptyList());
    }
    private final class Job {
        final DirectoryQuery query;
        final long started, revision;
        final String search;
        final Iterator<MachineDirectoryEntry> entries;
        final BooleanSupplier active;
        final Consumer<DirectoryPage> answer;
        final List<DirectoryPage.Row> rows = new ArrayList<>();
        int total;
        Job(DirectoryQuery query, long now, BooleanSupplier active, Consumer<DirectoryPage> answer) {
            this.query = query; started = now; revision = data.getRevision(); search = DirectoryText.fold(query.search);
            entries = data.orderedRecords(); this.active = active; this.answer = answer;
        }
        boolean pageComplete() {
            // Empty search already knows its total. The common first page visits only 50 records.
            return unfiltered() && total >= Math.min(data.size(), (query.page + 1) * DirectoryQuery.PAGE_SIZE);
        }
        boolean unfiltered() { return search.isEmpty() && query.tab == DirectoryTab.ALL; }
    }
}
