package com.tom.trading.trade;

import net.minecraft.util.text.ITextComponent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Ephemeral committed-trade evidence. Contains no mutable stacks, raw NBT or inventory snapshots. */
public final class TradeReceipt {
    // Normal: at most 44 paid variants (36 player + 8 stock), 8 delivered variants.
    // Creative: at most 40 paid variants (36 player + 4 definitions), 4 delivered variants.
    static final int MAX_LINES = 64;
    private final UUID buyerUuid, machineUuid, ownerUuid;
    private final String buyerName, ownerName;
    private final int completed;
    private final List<Line> payments, deliveries;
    private final String announcementJson;

    private TradeReceipt(Prepared prepared, UUID buyerUuid, String buyerName, UUID machineUuid,
                         UUID ownerUuid, String ownerName) {
        this.buyerUuid = Objects.requireNonNull(buyerUuid, "buyerUuid");
        this.machineUuid = Objects.requireNonNull(machineUuid, "machineUuid");
        this.ownerUuid = ownerUuid;
        this.buyerName = ReceiptText.label(buyerName, 64, buyerUuid.toString());
        this.ownerName = ownerUuid == null ? "Unowned machine"
                : ReceiptText.label(ownerName, 64, ownerUuid.toString());
        completed = prepared.completed;
        payments = prepared.payments;
        deliveries = prepared.deliveries;
        announcementJson = ITextComponent.Serializer.componentToJson(TradeReceiptChat.format(this));
        // PacketBuffer.writeString/readTextComponent have a 32767-byte/string ceiling in 1.12.2.
        // Validate BEFORE commit; an unrepresentable receipt must not consume a payment.
        if (announcementJson.getBytes(StandardCharsets.UTF_8).length > 32767)
            throw new IllegalArgumentException("Trade receipt exceeds the vanilla chat packet limit");
    }

    /** Display-name callbacks run during preflight, before final inventory/access/revision checks. */
    static Prepared prepare(TradePlan plan, Function<TradeItem, String> displayNames) {
        if (plan.getCompletedCount() <= 0 || plan.getPaidItems().isEmpty() || plan.getDeliveredItems().isEmpty()
                || plan.getPaidItems().size() + plan.getDeliveredItems().size() > MAX_LINES)
            throw new IllegalArgumentException("Invalid successful receipt legs");
        Map<TradeItem, LineIdentity> identities = new LinkedHashMap<>();
        List<Line> payments = lines(plan.getPaidItems(), identities, displayNames);
        List<Line> deliveries = lines(plan.getDeliveredItems(), identities, displayNames);
        return new Prepared(plan.getCompletedCount(), payments, deliveries);
    }

    private static List<Line> lines(Map<TradeItem, Long> amounts, Map<TradeItem, LineIdentity> identities,
                                    Function<TradeItem, String> displayNames) {
        List<Line> result = new ArrayList<>();
        for (Map.Entry<TradeItem, Long> entry : amounts.entrySet()) {
            TradeItem item = entry.getKey();
            LineIdentity identity = identities.get(item);
            if (identity == null) {
                String fallback = ReceiptText.label(item.getItemId(), 48, "Item");
                String label;
                try { label = ReceiptText.label(displayNames.apply(item), 48, fallback); }
                catch (RuntimeException | LinkageError brokenItemName) { label = fallback; }
                // Receipt-local variant numbers distinguish NBT/capability variants without exposing their NBT.
                identity = new LineIdentity(item.getItemId(), item.getMetadata(), identities.size() + 1, label);
                identities.put(item, identity);
            }
            if (entry.getValue() <= 0) throw new IllegalArgumentException("Nonpositive receipt quantity");
            result.add(new Line(identity, entry.getValue()));
        }
        return Collections.unmodifiableList(result);
    }

    public UUID getBuyerUuid() { return buyerUuid; }
    public UUID getMachineUuid() { return machineUuid; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getBuyerName() { return buyerName; }
    public String getOwnerName() { return ownerName; }
    public int getCompleted() { return completed; }
    public List<Line> getPayments() { return payments; }
    public List<Line> getDeliveries() { return deliveries; }
    public ITextComponent createAnnouncement() { return ITextComponent.Serializer.jsonToComponent(announcementJson); }

    static final class Prepared {
        final int completed;
        final List<Line> payments, deliveries;
        Prepared(int completed, List<Line> payments, List<Line> deliveries) {
            this.completed = completed; this.payments = payments; this.deliveries = deliveries;
        }
        TradeReceipt bind(UUID buyer, String buyerName, UUID machine, UUID owner, String ownerName) {
            return new TradeReceipt(this, buyer, buyerName, machine, owner, ownerName);
        }
    }

    private static final class LineIdentity {
        final String itemId, label;
        final int metadata, variant;
        LineIdentity(String itemId, int metadata, int variant, String label) {
            this.itemId = itemId; this.metadata = metadata; this.variant = variant; this.label = label;
        }
    }

    public static final class Line {
        private final LineIdentity identity;
        private final long quantity;
        private Line(LineIdentity identity, long quantity) { this.identity = identity; this.quantity = quantity; }
        public String getItemId() { return identity.itemId; }
        public int getMetadata() { return identity.metadata; }
        public int getVariant() { return identity.variant; }
        public String getLabel() { return identity.label; }
        public long getQuantity() { return quantity; }
    }
}
