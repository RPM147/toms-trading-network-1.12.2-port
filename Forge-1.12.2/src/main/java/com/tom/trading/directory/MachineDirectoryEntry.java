package com.tom.trading.directory;

import java.util.Objects;
import java.util.UUID;

/** Public metadata only. OBSERVED means seen by a server, not loaded or purchasable now. */
public final class MachineDirectoryEntry {
    public enum Evidence { HINT, OBSERVED, UNSUPPORTED }
    public final MachineAddress address;
    public final UUID machineId, ownerId;
    public final String ownerName, name;
    public final Evidence evidence;
    public final OfferPreview preview;
    private final String searchText;

    public MachineDirectoryEntry(MachineAddress address, UUID machineId, UUID ownerId,
                                 String ownerName, String name, Evidence evidence) {
        this(address, machineId, ownerId, ownerName, name, evidence, OfferPreview.UNKNOWN);
    }
    public MachineDirectoryEntry(MachineAddress address, UUID machineId, UUID ownerId,
                                 String ownerName, String name, Evidence evidence, OfferPreview preview) {
        this.address = Objects.requireNonNull(address);
        this.machineId = machineId; this.ownerId = ownerId;
        this.ownerName = DirectoryText.label(ownerName); this.name = DirectoryText.label(name);
        this.evidence = Objects.requireNonNull(evidence);
        this.preview = Objects.requireNonNull(preview);
        if (evidence != Evidence.OBSERVED && preview.known) throw new IllegalArgumentException("Unverified preview");
        StringBuilder searchable = new StringBuilder(this.ownerName).append('\n').append(this.name)
                .append('\n').append(ownerId == null ? "" : ownerId.toString());
        for (OfferPreview.Item item : preview.sale) appendItem(searchable, item);
        for (OfferPreview.Item item : preview.payment) appendItem(searchable, item);
        searchText = DirectoryText.fold(searchable.toString());
        if (evidence == Evidence.OBSERVED && machineId == null)
            throw new IllegalArgumentException("Observed machine needs an identity");
    }

    boolean matchesSearch(String normalized) { return searchText.contains(normalized); }
    private static void appendItem(StringBuilder text, OfferPreview.Item item) {
        text.append('\n').append(item.name).append('\n').append(item.itemId)
                .append('\n').append(item.itemId.replace('_', ' ')).append('\n').append(item.oreName);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof MachineDirectoryEntry)) return false;
        MachineDirectoryEntry e = (MachineDirectoryEntry) other;
        return address.equals(e.address) && Objects.equals(machineId, e.machineId)
                && Objects.equals(ownerId, e.ownerId) && ownerName.equals(e.ownerName)
                && name.equals(e.name) && evidence == e.evidence && preview.equals(e.preview);
    }
    @Override public int hashCode() { return Objects.hash(address, machineId, ownerId, ownerName, name, evidence, preview); }
}
