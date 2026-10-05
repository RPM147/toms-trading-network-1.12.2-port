package com.tom.trading.trade;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compact receipt text: buyer -> seller | delivered quantities <- paid quantities. */
public final class TradeBookEntry {
    private TradeBookEntry() {}
    public static String format(TradeReceipt receipt) {
        return ReceiptText.label(receipt.getBuyerName(), 24, "?") + " -> "
                + ReceiptText.label(receipt.getOwnerName(), 24, "?") + " | "
                + items(receipt.getDeliveries()) + " <- " + items(receipt.getPayments());
    }
    private static String items(List<TradeReceipt.Line> lines) {
        Map<String, Long> quantities = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (TradeReceipt.Line line : lines) {
            String identity = line.getItemId() + "\u0000" + line.getMetadata() + "\u0000" + line.getLabel();
            quantities.put(identity, Math.addExact(quantities.getOrDefault(identity, 0L), line.getQuantity()));
            labels.put(identity, ReceiptText.label(line.getLabel(), 24, line.getItemId()));
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Long> entry : quantities.entrySet()) {
            if (text.length() > 0) text.append(" + ");
            text.append(entry.getValue()).append(' ').append(labels.get(entry.getKey()));
        }
        return text.toString();
    }
}
