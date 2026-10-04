package com.tom.trading.directory;

import com.tom.trading.trade.TradeLimits;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable, informational offer only: no stacks, item NBT, stock or permission state. */
public final class OfferPreview {
    public static final int MAX_BYTES = 700, MAX_ITEM_ID = 128;
    public static final OfferPreview UNKNOWN = new OfferPreview();
    public final boolean known;
    public final List<Item> payment, sale;

    private OfferPreview() { known = false; payment = sale = Collections.emptyList(); }
    public OfferPreview(List<Item> payment, List<Item> sale) {
        this.payment = items(payment); this.sale = items(sale); known = true;
        for (Item item : sale) if (!item.oreName.isEmpty()) throw new IllegalArgumentException("Sale cannot be a group");
        if (encodedSize() > MAX_BYTES) throw new IllegalArgumentException("Offer preview exceeds budget");
    }
    private static List<Item> items(List<Item> source) {
        if (source.size() > TradeLimits.MAX_DEFINITIONS) throw new IllegalArgumentException("Too many preview items");
        List<Item> copy = new ArrayList<>(source);
        for (Item item : copy) Objects.requireNonNull(item);
        return Collections.unmodifiableList(copy);
    }
    public int encodedSize() {
        int size = known ? 3 : 1;
        for (Item item : payment) size += item.encodedSize();
        for (Item item : sale) size += item.encodedSize();
        return size;
    }
    private static int stringSize(String value) {
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        return bytes + (bytes < 128 ? 1 : 2);
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof OfferPreview)) return false;
        OfferPreview p = (OfferPreview) other;
        return known == p.known && payment.equals(p.payment) && sale.equals(p.sale);
    }
    @Override public int hashCode() { return Objects.hash(known, payment, sale); }

    public static final class Item {
        public final String itemId, name, oreName;
        public final int metadata, quantity;
        public final boolean tagged, matchNbt;
        public Item(String itemId, int metadata, int quantity, String name, String oreName, boolean tagged, boolean matchNbt) {
            if (itemId == null || itemId.length() > MAX_ITEM_ID || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || metadata < 0 || metadata >= 32767 || quantity < 1 || quantity > TradeLimits.MAX_QUANTITY
                    || !DirectoryText.valid(name) || !DirectoryText.valid(oreName))
                throw new IllegalArgumentException("Invalid preview item");
            this.itemId = itemId; this.metadata = metadata; this.quantity = quantity;
            this.name = name; this.oreName = oreName; this.tagged = tagged; this.matchNbt = matchNbt;
        }
        int encodedSize() { return 5 + stringSize(itemId) + stringSize(name) + stringSize(oreName); }
        public Item withoutName() { return new Item(itemId, metadata, quantity, "", oreName, tagged, matchNbt); }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Item)) return false;
            Item i = (Item) other;
            return itemId.equals(i.itemId) && metadata == i.metadata && quantity == i.quantity
                    && name.equals(i.name) && oreName.equals(i.oreName) && tagged == i.tagged && matchNbt == i.matchNbt;
        }
        @Override public int hashCode() { return Objects.hash(itemId, metadata, quantity, name, oreName, tagged, matchNbt); }
    }
}
