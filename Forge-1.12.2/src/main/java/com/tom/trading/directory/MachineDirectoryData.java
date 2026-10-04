package com.tom.trading.directory;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import java.util.*;

/** Owned exclusively by one overworld MapStorage and its server thread. Rebuildable metadata. */
public final class MachineDirectoryData extends WorldSavedData {
    public static final String DATA_NAME = "ttn_machine_directory";
    public static final int FORMAT = 2, MAX_ENTRIES = 100000, MAX_SPECIAL = 256;
    private final Set<UUID> specialMachines = new TreeSet<>();
    private final Map<MachineAddress, MachineDirectoryEntry> entries = new TreeMap<>(MachineAddress.ORDER);
    private final DirectoryPager pager = new DirectoryPager(this);
    private final Map<UUID, Set<MachineAddress>> identities = new HashMap<>();
    private final Map<String, Set<MachineAddress>> chunks = new HashMap<>();
    private final Set<MachineAddress> pending = new LinkedHashSet<>();
    private UUID worldId;
    private String manifestDigest = "", problem = "Directory could not be read";
    private NBTTagCompound preserved;
    private boolean supported, backfillComplete;
    private long revision;

    // MapStorage can return this instance even when decompression throws before readFromNBT.
    // Therefore the reflection constructor MUST default to unavailable, not an empty valid save.
    public MachineDirectoryData(String name) { super(name); }
    public static MachineDirectoryData create() {
        MachineDirectoryData data = new MachineDirectoryData(DATA_NAME);
        data.worldId = UUID.randomUUID(); data.supported = true; data.problem = ""; data.revision = 1; data.markDirty();
        return data;
    }
    public boolean isSupported() { return supported; }
    public String getProblem() { return problem; }
    public UUID getWorldId() { return worldId; }
    public String getManifestDigest() { return manifestDigest; }
    public long getRevision() { return revision; }
    public int size() { return entries.size(); }
    public boolean isBackfillComplete() { return backfillComplete; }
    public Collection<MachineDirectoryEntry> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }
    public MachineDirectoryEntry get(MachineAddress address) { return entries.get(address); }
    public DirectoryPager pager() { return pager; }
    public boolean isSpecial(UUID id) { return id != null && specialMachines.contains(id); }
    public List<UUID> specialMachines() { return Collections.unmodifiableList(new ArrayList<>(specialMachines)); }
    /** Admin tooling supplies the loaded, observed identity. No name/address-based inheritance. */
    public boolean addSpecial(MachineAddress address, UUID id) {
        requireSupported();
        if (!isUniqueIdentity(address, id)) throw new IllegalArgumentException("Machine identity is unverified or conflicting");
        if (specialMachines.contains(id)) return false;
        if (specialMachines.size() >= MAX_SPECIAL) throw new IllegalStateException("Shared tab limit reached (256); remove stale UUIDs first");
        specialMachines.add(id); markDirty(); revision++; return true;
    }
    public boolean removeSpecial(UUID id) {
        requireSupported();
        if (id == null || !specialMachines.remove(id)) return false;
        markDirty(); revision++; return true;
    }
    Iterator<MachineDirectoryEntry> orderedRecords() { return entries.values().iterator(); }
    public boolean hasIdentityConflict(UUID id) {
        Set<MachineAddress> locations = identities.get(id);
        return locations != null && locations.size() > 1;
    }

    /** Metadata-only identity validation; never loads or grants access to a machine. */
    public boolean isUniqueIdentity(UUID id) {
        Set<MachineAddress> locations = identities.get(id);
        return id != null && locations != null && locations.size() == 1
                && isUniqueIdentity(locations.iterator().next(), id);
    }
    /** Includes hints/conflicts, so favorite cleanup removes only identities absent from this directory. */
    public boolean containsIdentity(UUID id) { return id != null && identities.containsKey(id); }

    public boolean isUniqueIdentity(MachineAddress address, UUID id) {
        Set<MachineAddress> locations = identities.get(id);
        MachineDirectoryEntry entry = entries.get(address);
        return supported && id != null && locations != null && locations.size() == 1
                && locations.contains(address) && entry.evidence == MachineDirectoryEntry.Evidence.OBSERVED;
    }
    public int conflictCount() {
        int count = 0;
        for (Set<MachineAddress> locations : identities.values()) if (locations.size() > 1) count++;
        return count;
    }

    /** Only loaded, real tiles call this. An offline hint cannot overwrite a live observation. */
    public void observe(MachineDirectoryEntry entry) {
        requireSupported();
        if (entry.evidence == MachineDirectoryEntry.Evidence.HINT) throw new IllegalArgumentException("Not observed");
        put(entry);
    }
    public void importHints(List<MachineDirectoryEntry> hints, boolean complete) {
        requireSupported();
        Set<MachineAddress> additions = new HashSet<>();
        for (MachineDirectoryEntry hint : hints) {
            if (hint.evidence != MachineDirectoryEntry.Evidence.HINT) throw new IllegalArgumentException("Not a hint");
            if (!entries.containsKey(hint.address)) additions.add(hint.address);
        }
        if ((long) entries.size() + additions.size() > MAX_ENTRIES) throw new IllegalStateException("Directory capacity reached");
        for (MachineDirectoryEntry hint : hints) {
            if (!entries.containsKey(hint.address)) put(hint);
            enqueue(hint.address); // Recheck an already loaded target, without loading an absent chunk.
        }
        // Coverage is a report about the imported backup, never a guarantee of live completeness.
        backfillComplete = complete; markDirty(); revision++;
    }
    public void setManifestDigest(String digest) {
        requireSupported();
        if (!digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid manifest digest");
        if (!manifestDigest.equals(digest)) {
            manifestDigest = digest; backfillComplete = false; markDirty(); revision++;
        }
    }
    public void removeVerified(MachineAddress address) {
        requireSupported();
        MachineDirectoryEntry previous = entries.remove(address);
        if (previous != null) { unindex(previous); markDirty(); revision++; }
    }
    public void enqueue(MachineAddress address) { if (pending.size() < MAX_ENTRIES) pending.add(address); }
    public void enqueueChunk(int dimension, int x, int z) {
        Set<MachineAddress> addresses = chunks.get(chunkKey(dimension, x, z));
        if (addresses != null) for (MachineAddress address : addresses) enqueue(address);
    }
    public MachineAddress pollPending() {
        Iterator<MachineAddress> it = pending.iterator();
        if (!it.hasNext()) return null;
        MachineAddress address = it.next(); it.remove(); return address;
    }
    private void put(MachineDirectoryEntry entry) {
        MachineDirectoryEntry previous = entries.get(entry.address);
        if (entry.equals(previous)) return;
        if (previous == null && entries.size() >= MAX_ENTRIES) throw new IllegalStateException("Directory capacity reached");
        if (previous != null) unindex(previous);
        entries.put(entry.address, entry); index(entry); markDirty(); revision++;
    }
    private void index(MachineDirectoryEntry entry) {
        if (entry.machineId != null) identities.computeIfAbsent(entry.machineId, k -> new HashSet<>()).add(entry.address);
        chunks.computeIfAbsent(chunkKey(entry.address), k -> new HashSet<>()).add(entry.address);
    }
    private void unindex(MachineDirectoryEntry entry) {
        if (entry.machineId != null) removeIndex(identities, entry.machineId, entry.address);
        removeIndex(chunks, chunkKey(entry.address), entry.address);
    }
    private static <T> void removeIndex(Map<T, Set<MachineAddress>> index, T key, MachineAddress address) {
        Set<MachineAddress> values = index.get(key);
        if (values != null && values.remove(address) && values.isEmpty()) index.remove(key);
    }
    private static String chunkKey(MachineAddress a) { return chunkKey(a.dimension, a.x >> 4, a.z >> 4); }
    private static String chunkKey(int dimension, int x, int z) { return dimension + ":" + x + ":" + z; }
    private void requireSupported() { if (!supported) throw new IllegalStateException(problem); }

    @Override public void readFromNBT(NBTTagCompound tag) {
        entries.clear(); identities.clear(); chunks.clear(); pending.clear(); specialMachines.clear(); supported = false;
        worldId = null; manifestDigest = ""; backfillComplete = false; preserved = tag.copy(); revision++;
        try {
            int format = tag.getInteger("Format");
            if (!tag.hasKey("Format", Constants.NBT.TAG_INT) || (format != 1 && format != FORMAT) || !tag.hasUniqueId("WorldUUID"))
                throw new IllegalArgumentException("Unknown or invalid directory format");
            if (format == FORMAT) {
                NBTTagList special = tag.getTagList("SpecialMachines", Constants.NBT.TAG_COMPOUND);
                if (!tag.hasKey("SpecialMachines", Constants.NBT.TAG_LIST)
                        || ((NBTTagList) tag.getTag("SpecialMachines")).tagCount() != special.tagCount()
                        || special.tagCount() > MAX_SPECIAL)
                    throw new IllegalArgumentException("Invalid shared tab membership");
                for (int i = 0; i < special.tagCount(); i++) {
                    NBTTagCompound row = special.getCompoundTagAt(i);
                    if (!row.hasUniqueId("MachineUUID") || !specialMachines.add(row.getUniqueId("MachineUUID")))
                        throw new IllegalArgumentException("Invalid or duplicate shared tab UUID");
                }
            }
            NBTTagList list = tag.getTagList("Entries", Constants.NBT.TAG_COMPOUND);
            if (!tag.hasKey("Entries", Constants.NBT.TAG_LIST)
                    || (((NBTTagList) tag.getTag("Entries")).tagCount() != list.tagCount())
                    || list.tagCount() > MAX_ENTRIES)
                throw new IllegalArgumentException("Invalid directory entries");
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound row = list.getCompoundTagAt(i);
                for (String key : Arrays.asList("Dimension", "X", "Y", "Z"))
                    if (!row.hasKey(key, Constants.NBT.TAG_INT)) throw new IllegalArgumentException("Invalid address");
                MachineAddress address = new MachineAddress(row.getInteger("Dimension"), row.getInteger("X"),
                        row.getInteger("Y"), row.getInteger("Z"));
                MachineDirectoryEntry.Evidence evidence = MachineDirectoryEntry.Evidence.valueOf(row.getString("Evidence"));
                MachineDirectoryEntry entry = new MachineDirectoryEntry(address,
                        row.hasUniqueId("MachineUUID") ? row.getUniqueId("MachineUUID") : null,
                        row.hasUniqueId("OwnerUUID") ? row.getUniqueId("OwnerUUID") : null,
                        row.getString("OwnerName"), row.getString("Name"),
                        evidence, evidence == MachineDirectoryEntry.Evidence.OBSERVED
                        ? OfferPreviewCodec.restore(row.getCompoundTag("OfferPreview")) : OfferPreview.UNKNOWN);
                if (entries.put(address, entry) != null) throw new IllegalArgumentException("Duplicate directory address");
                index(entry);
            }
            worldId = tag.getUniqueId("WorldUUID"); manifestDigest = tag.getString("ManifestDigest");
            if (!manifestDigest.isEmpty() && !manifestDigest.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid saved manifest digest");
            backfillComplete = tag.getBoolean("BackfillComplete"); supported = true; problem = ""; preserved = null;
            // Some spawn chunks may load before overworld registration / our ChunkEvent listener.
            // One bounded restart pass covers those too, and skips every unloaded target.
            pending.addAll(entries.keySet());
        } catch (IllegalArgumentException ex) {
            entries.clear(); identities.clear(); chunks.clear(); specialMachines.clear(); worldId = null; problem = ex.getMessage();
        }
        setDirty(false); // Unknown/corrupt payloads are not automatically rewritten.
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        if (!supported) {
            if (preserved == null) throw new IllegalStateException(problem);
            return preserved.copy();
        }
        tag.setInteger("Format", FORMAT); tag.setUniqueId("WorldUUID", worldId);
        tag.setString("ManifestDigest", manifestDigest); tag.setBoolean("BackfillComplete", backfillComplete);
        NBTTagList special = new NBTTagList();
        for (UUID id : specialMachines) {
            NBTTagCompound row = new NBTTagCompound(); row.setUniqueId("MachineUUID", id); special.appendTag(row);
        }
        tag.setTag("SpecialMachines", special);
        NBTTagList list = new NBTTagList();
        for (MachineDirectoryEntry entry : entries.values()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setInteger("Dimension", entry.address.dimension); row.setInteger("X", entry.address.x);
            row.setInteger("Y", entry.address.y); row.setInteger("Z", entry.address.z);
            if (entry.machineId != null) row.setUniqueId("MachineUUID", entry.machineId);
            if (entry.ownerId != null) row.setUniqueId("OwnerUUID", entry.ownerId);
            row.setString("OwnerName", entry.ownerName); row.setString("Name", entry.name);
            row.setString("Evidence", entry.evidence.name());
            if (entry.preview.known) row.setTag("OfferPreview", OfferPreviewCodec.save(entry.preview));
            list.appendTag(row);
        }
        tag.setTag("Entries", list); return tag;
    }
}
