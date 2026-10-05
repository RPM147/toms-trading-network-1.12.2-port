package com.tom.trading.book;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Save-owned opt-in ledgers with bounded retention and no TileEntity/chunk references. Server thread only. */
public final class TradeBookData extends WorldSavedData {
    public static final String NAME = "ttn_trade_books";
    public static final int MAX_MACHINES = 4096, PER_MACHINE = 100, MAX_RECORDS = 8192;
    public static final int MAX_CHARACTERS = 2_000_000, MAX_ENTRY = 8192, SNAPSHOT_BYTES = 24 * 1024;
    private UUID world;
    private boolean supported;
    private NBTTagCompound preserved;
    private final Map<UUID, UUID> keys = new LinkedHashMap<>();
    private final Map<UUID, ArrayDeque<Long>> index = new HashMap<>();
    private final LinkedHashMap<Long, Record> records = new LinkedHashMap<>();
    private long sequence;
    private int characters;
    public TradeBookData(String name) { super(name); }
    public static TradeBookData create(UUID world) {
        TradeBookData data = new TradeBookData(NAME); data.world = Objects.requireNonNull(world);
        data.supported = true; data.markDirty(); return data;
    }
    public boolean supports(UUID expectedWorld) { return supported && Objects.equals(world, expectedWorld); }
    public TradeBookBinding bind(UUID machine) {
        requireSupported(); Objects.requireNonNull(machine);
        UUID key = keys.get(machine);
        if (key == null) {
            if (keys.size() >= MAX_MACHINES) throw new IllegalStateException("Book ledger capacity reached");
            key = UUID.randomUUID(); keys.put(machine, key); markDirty();
        }
        return new TradeBookBinding(world, machine, key);
    }
    public boolean accepts(TradeBookBinding binding) {
        return binding != null && supports(binding.world) && binding.key.equals(keys.get(binding.machine));
    }
    public boolean tracks(UUID machine) { return supported && keys.containsKey(machine); }
    public void append(UUID machine, String text) {
        requireSupported();
        if (!keys.containsKey(machine)) return;
        if (!validText(text) || sequence == Long.MAX_VALUE) throw new IllegalArgumentException("Invalid book entry");
        ArrayDeque<Long> entries = index.computeIfAbsent(machine, ignored -> new ArrayDeque<>());
        if (entries.size() >= PER_MACHINE) remove(entries.peekFirst());
        long id = ++sequence; entries.addLast(id); records.put(id, new Record(machine, text)); characters += text.length();
        while (records.size() > MAX_RECORDS || characters > MAX_CHARACTERS) remove(records.keySet().iterator().next());
        markDirty();
    }
    private void remove(long id) {
        Record record = records.remove(id); characters -= record.text.length();
        ArrayDeque<Long> entries = index.get(record.machine); entries.removeFirstOccurrence(id);
        // Keep an empty deque until append completes; otherwise eviction of its last entry could lose the local deque.
    }
    /** Newest complete entries first, bounded by UTF-8 bytes before inventory/network serialization. */
    public List<String> snapshot(TradeBookBinding binding) {
        if (!accepts(binding)) throw new IllegalArgumentException("Invalid book key");
        ArrayDeque<Long> entries = index.get(binding.machine);
        if (entries == null) return Collections.emptyList();
        List<String> result = new ArrayList<>(); int bytes = 0;
        Iterator<Long> newest = entries.descendingIterator();
        while (newest.hasNext()) {
            String text = records.get(newest.next()).text;
            int size = text.getBytes(StandardCharsets.UTF_8).length + 5;
            if (bytes + size > SNAPSHOT_BYTES) break;
            result.add(text); bytes += size;
        }
        return Collections.unmodifiableList(result);
    }
    public static boolean validText(String text) {
        return text != null && !text.isEmpty() && text.length() <= MAX_ENTRY
                && text.getBytes(StandardCharsets.UTF_8).length + 5 <= SNAPSHOT_BYTES
                && text.codePoints().noneMatch(cp -> Character.isISOControl(cp) || cp == 167
                || Character.getType(cp) == Character.FORMAT || Character.getType(cp) == Character.SURROGATE
                || Character.getType(cp) == Character.LINE_SEPARATOR || Character.getType(cp) == Character.PARAGRAPH_SEPARATOR);
    }
    private void requireSupported() { if (!supported) throw new IllegalStateException("Book data unavailable"); }
    @Override public void readFromNBT(NBTTagCompound tag) {
        supported = false; preserved = tag.copy(); keys.clear(); index.clear(); records.clear(); characters = 0; sequence = 0;
        try {
            if (!tag.hasKey("Format", Constants.NBT.TAG_INT) || tag.getInteger("Format") != 1 || !tag.hasUniqueId("World"))
                throw new IllegalArgumentException("Unsupported book data");
            NBTTagList bindings = list(tag, "Bindings"), history = list(tag, "History");
            if (bindings.tagCount() > MAX_MACHINES || history.tagCount() > MAX_RECORDS) throw new IllegalArgumentException("Oversized book data");
            for (int i = 0; i < bindings.tagCount(); i++) {
                NBTTagCompound row = bindings.getCompoundTagAt(i);
                if (!row.hasUniqueId("Machine") || !row.hasUniqueId("Key")
                        || keys.put(row.getUniqueId("Machine"), row.getUniqueId("Key")) != null) throw new IllegalArgumentException("Invalid binding");
            }
            for (int i = 0; i < history.tagCount(); i++) {
                NBTTagCompound row = history.getCompoundTagAt(i); String text = row.getString("Text");
                if (!row.hasUniqueId("Machine") || !validText(text) || !keys.containsKey(row.getUniqueId("Machine")))
                    throw new IllegalArgumentException("Invalid entry");
                UUID machine = row.getUniqueId("Machine");
                ArrayDeque<Long> entries = index.computeIfAbsent(machine, ignored -> new ArrayDeque<>());
                if (entries.size() >= PER_MACHINE || characters + text.length() > MAX_CHARACTERS) throw new IllegalArgumentException("Oversized history");
                entries.addLast(++sequence); records.put(sequence, new Record(machine, text)); characters += text.length();
            }
            world = tag.getUniqueId("World"); supported = true; preserved = null;
        } catch (RuntimeException invalid) { keys.clear(); index.clear(); records.clear(); characters = 0; }
    }
    private static NBTTagList list(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_LIST)) throw new IllegalArgumentException("Missing book list");
        NBTTagList result = tag.getTagList(key, Constants.NBT.TAG_COMPOUND);
        if (((NBTTagList) tag.getTag(key)).tagCount() != result.tagCount()) throw new IllegalArgumentException("Wrong book list type");
        return result;
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        if (!supported) {
            if (preserved != null) for (String key : preserved.getKeySet()) tag.setTag(key, preserved.getTag(key).copy());
            return tag;
        }
        tag.setInteger("Format", 1); tag.setUniqueId("World", world);
        NBTTagList bindings = new NBTTagList(), history = new NBTTagList();
        for (Map.Entry<UUID, UUID> entry : keys.entrySet()) {
            NBTTagCompound row = new NBTTagCompound(); row.setUniqueId("Machine", entry.getKey()); row.setUniqueId("Key", entry.getValue()); bindings.appendTag(row);
        }
        for (Record record : records.values()) {
            NBTTagCompound row = new NBTTagCompound(); row.setUniqueId("Machine", record.machine); row.setString("Text", record.text); history.appendTag(row);
        }
        tag.setTag("Bindings", bindings); tag.setTag("History", history); return tag;
    }
    private static final class Record {
        final UUID machine; final String text;
        Record(UUID machine, String text) { this.machine = machine; this.text = text; }
    }
}
