package com.tom.trading.trade;

import java.util.Objects;

public final class InventorySlot {
    private final TradeItem item;
    private final int count;
    private final int slotLimit;

    private InventorySlot(TradeItem item, int count, int slotLimit) {
        if (slotLimit < 1) {
            throw new IllegalArgumentException("slotLimit must be positive");
        }
        if (item == null) {
            if (count != 0) {
                throw new IllegalArgumentException("An empty slot must have count 0");
            }
        } else {
            int effectiveLimit = Math.min(slotLimit, item.getMaxStackSize());
            if (count < 1 || count > effectiveLimit) {
                throw new IllegalArgumentException(
                        "count must be between 1 and the effective slot limit " + effectiveLimit
                );
            }
        }
        this.item = item;
        this.count = count;
        this.slotLimit = slotLimit;
    }

    public static InventorySlot empty(int slotLimit) {
        return new InventorySlot(null, 0, slotLimit);
    }

    public static InventorySlot occupied(TradeItem item, int count, int slotLimit) {
        return new InventorySlot(Objects.requireNonNull(item, "item"), count, slotLimit);
    }

    public boolean isEmpty() {
        return item == null;
    }

    public TradeItem getItem() {
        return item;
    }

    public int getCount() {
        return count;
    }

    public int getSlotLimit() {
        return slotLimit;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof InventorySlot)) {
            return false;
        }
        InventorySlot other = (InventorySlot) object;
        return count == other.count
                && slotLimit == other.slotLimit
                && Objects.equals(item, other.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, count, slotLimit);
    }
}
