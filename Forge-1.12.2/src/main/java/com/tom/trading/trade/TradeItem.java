package com.tom.trading.trade;

import net.minecraft.nbt.NBTTagCompound;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable item identity used by the trade planner.
 *
 * <p>The real stack count is deliberately not part of this value. Logical
 * trade quantities and inventory counts remain integer fields elsewhere.</p>
 */
public final class TradeItem {
    private final String itemId;
    private final int metadata;
    private final NBTTagCompound tag;
    private final NBTTagCompound capabilities;
    private final Set<String> oreNames;
    private final int maxStackSize;
    private final int cachedHash;

    public TradeItem(
            String itemId,
            int metadata,
            NBTTagCompound tag,
            Collection<String> oreNames,
            int maxStackSize
    ) {
        this(itemId, metadata, tag, null, oreNames, maxStackSize);
    }

    public TradeItem(String itemId, int metadata, NBTTagCompound tag,
                     NBTTagCompound capabilities, Collection<String> oreNames, int maxStackSize) {
        if (itemId == null || itemId.isEmpty() || !itemId.equals(itemId.trim())) {
            throw new IllegalArgumentException("itemId must be non-empty and trimmed");
        }
        if (metadata < 0 || metadata > Short.MAX_VALUE) {
            throw new IllegalArgumentException("metadata is outside the 1.12.2 range: " + metadata);
        }
        if (maxStackSize < 1) {
            throw new IllegalArgumentException("maxStackSize must be positive");
        }

        LinkedHashSet<String> copiedOreNames = new LinkedHashSet<>();
        if (oreNames != null) {
            for (String oreName : oreNames) {
                validateOreName(oreName);
                copiedOreNames.add(oreName);
            }
        }

        this.itemId = itemId;
        this.metadata = metadata;
        this.tag = tag == null ? null : (NBTTagCompound) tag.copy();
        this.capabilities = capabilities == null || capabilities.isEmpty() ? null : capabilities.copy();
        this.oreNames = Collections.unmodifiableSet(copiedOreNames);
        this.maxStackSize = maxStackSize;
        this.cachedHash = Objects.hash(itemId, metadata, this.tag, this.capabilities, this.oreNames, maxStackSize);
    }

    public String getItemId() {
        return itemId;
    }

    public int getMetadata() {
        return metadata;
    }

    public NBTTagCompound copyTag() {
        return tag == null ? null : (NBTTagCompound) tag.copy();
    }

    public Set<String> getOreNames() {
        return oreNames;
    }

    public int getMaxStackSize() {
        return maxStackSize;
    }

    public boolean hasOreName(String oreName) {
        return oreNames.contains(oreName);
    }

    public boolean sameItemAndMetadata(TradeItem other) {
        return other != null
                && metadata == other.metadata
                && itemId.equals(other.itemId);
    }

    public boolean sameTag(TradeItem other) {
        return other != null && Objects.equals(tag, other.tag)
                && Objects.equals(capabilities, other.capabilities);
    }

    public boolean sameStackIdentity(TradeItem other) {
        return sameItemAndMetadata(other) && sameTag(other);
    }

    static void validateOreName(String oreName) {
        if (oreName == null
                || oreName.isEmpty()
                || !oreName.equals(oreName.trim())
                || oreName.length() > TradeLimits.MAX_ORE_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid Ore Dictionary name");
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof TradeItem)) {
            return false;
        }
        TradeItem other = (TradeItem) object;
        return metadata == other.metadata
                && maxStackSize == other.maxStackSize
                && itemId.equals(other.itemId)
                && Objects.equals(tag, other.tag)
                && Objects.equals(capabilities, other.capabilities)
                && oreNames.equals(other.oreNames);
    }

    @Override
    public int hashCode() {
        return cachedHash;
    }

    @Override
    public String toString() {
        return itemId + "@" + metadata;
    }
}
