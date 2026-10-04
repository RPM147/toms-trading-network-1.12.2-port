package com.tom.trading.remote;

import java.util.*;
import java.util.function.Predicate;

/** Main-thread resource reservations. Pending and failed-to-release resources also consume the limit. */
final class SharedLeasePool<K, V> {
    private final int limit;
    private final Predicate<V> release;
    private final Map<K, Entry> entries = new HashMap<>();

    SharedLeasePool(int limit, Predicate<V> release) {
        if (limit < 1) throw new IllegalArgumentException("Positive limit required");
        this.limit = limit;
        this.release = Objects.requireNonNull(release);
    }

    Reservation reserve(Collection<K> requested) {
        Set<K> keys = new LinkedHashSet<>(requested);
        if (keys.isEmpty() || keys.contains(null) || keys.size() > limit) return null;
        int added = 0;
        for (K key : keys) {
            Entry entry = entries.get(key);
            if (entry == null) added++;
            else if (entry.quarantined) return null;
        }
        if (entries.size() + added > limit) return null;
        Map<K, Entry> held = new LinkedHashMap<>();
        for (K key : keys) {
            Entry entry = entries.computeIfAbsent(key, unused -> new Entry());
            entry.references++;
            held.put(key, entry);
        }
        return new Reservation(held);
    }

    int size() { return entries.size(); }

    /** Only after the owning world has already discarded its Forge tickets. */
    void discard(Predicate<K> removed) { entries.keySet().removeIf(removed); }

    void shutdown() {
        for (Entry entry : entries.values()) if (entry.value != null) safelyRelease(entry.value);
        entries.clear();
    }

    private boolean safelyRelease(V value) {
        try { return release.test(value); }
        catch (RuntimeException | LinkageError ex) { return false; }
    }

    private final class Entry {
        V value;
        int references;
        boolean quarantined;
    }

    final class Reservation {
        private final Map<K, Entry> held;
        private boolean closed;
        private Reservation(Map<K, Entry> held) { this.held = held; }
        boolean current() {
            if (closed) return false;
            for (Map.Entry<K, Entry> row : held.entrySet())
                if (entries.get(row.getKey()) != row.getValue() || row.getValue().quarantined) return false;
            return true;
        }
        V get(K key) {
            Entry entry = held.get(key);
            return !closed && entry != null && entries.get(key) == entry && !entry.quarantined ? entry.value : null;
        }
        void attach(K key, V value) {
            if (!current() || !held.containsKey(key) || held.get(key).value != null)
                throw new IllegalStateException("Resource reservation changed");
            held.get(key).value = Objects.requireNonNull(value);
        }
        void close() {
            if (closed) return;
            closed = true;
            for (Map.Entry<K, Entry> row : held.entrySet()) {
                Entry entry = row.getValue();
                if (entries.get(row.getKey()) != entry || --entry.references > 0) continue;
                if (entry.value == null || safelyRelease(entry.value)) entries.remove(row.getKey(), entry);
                else entry.quarantined = true;
            }
        }
    }
}
