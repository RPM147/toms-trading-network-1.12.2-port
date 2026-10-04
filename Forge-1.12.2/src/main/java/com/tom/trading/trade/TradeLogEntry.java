package com.tom.trading.trade;

import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;

/** One committed receipt, not an inventory snapshot or an attempted purchase. */
final class TradeLogEntry {
    private TradeLogEntry() {}

    static String encode(TradeReceipt receipt, Instant time) throws IOException {
        StringWriter text = new StringWriter();
        JsonWriter json = new JsonWriter(text);
        json.beginObject();
        json.name("schema").value(1);
        json.name("timeUtc").value(time.toString());
        json.name("buyerUuid").value(receipt.getBuyerUuid().toString());
        json.name("buyerName").value(receipt.getBuyerName());
        json.name("machineUuid").value(receipt.getMachineUuid().toString());
        json.name("ownerUuid").value(receipt.getOwnerUuid() == null ? null : receipt.getOwnerUuid().toString());
        json.name("ownerName").value(receipt.getOwnerName());
        json.name("completedTrades").value(receipt.getCompleted());
        lines(json, "payments", receipt.getPayments());
        lines(json, "deliveries", receipt.getDeliveries());
        json.endObject();
        json.close();
        return text.toString();
    }

    private static void lines(JsonWriter json, String name, List<TradeReceipt.Line> lines) throws IOException {
        json.name(name).beginArray();
        for (TradeReceipt.Line line : lines) {
            json.beginObject();
            json.name("itemId").value(line.getItemId());
            json.name("metadata").value(line.getMetadata());
            // Receipt-local identity only: distinguishes actual variants without leaking their NBT.
            json.name("variant").value(line.getVariant());
            json.name("label").value(line.getLabel());
            json.name("quantity").value(line.getQuantity());
            json.endObject();
        }
        json.endArray();
    }
}
