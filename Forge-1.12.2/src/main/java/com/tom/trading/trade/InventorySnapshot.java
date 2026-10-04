package com.tom.trading.trade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class InventorySnapshot {
    private final List<InventorySlot> slots;

    public InventorySnapshot(List<InventorySlot> slots) {
        Objects.requireNonNull(slots, "slots");
        ArrayList<InventorySlot> copied = new ArrayList<>(slots.size());
        for (InventorySlot slot : slots) {
            copied.add(Objects.requireNonNull(slot, "slot"));
        }
        this.slots = Collections.unmodifiableList(copied);
    }

    public static InventorySnapshot of(InventorySlot... slots) {
        return new InventorySnapshot(Arrays.asList(slots));
    }

    public static InventorySnapshot emptySlots(int slotCount, int slotLimit) {
        if (slotCount < 0) {
            throw new IllegalArgumentException("slotCount cannot be negative");
        }
        ArrayList<InventorySlot> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(InventorySlot.empty(slotLimit));
        }
        return new InventorySnapshot(slots);
    }

    public int size() {
        return slots.size();
    }

    public InventorySlot getSlot(int index) {
        return slots.get(index);
    }

    public List<InventorySlot> getSlots() {
        return slots;
    }

    public int countExact(TradeItem item) {
        int total = 0;
        for (InventorySlot slot : slots) {
            if (!slot.isEmpty() && slot.getItem().sameStackIdentity(item)) {
                total = Math.addExact(total, slot.getCount());
            }
        }
        return total;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof InventorySnapshot)) {
            return false;
        }
        InventorySnapshot other = (InventorySnapshot) object;
        return slots.equals(other.slots);
    }

    @Override
    public int hashCode() {
        return slots.hashCode();
    }
}
