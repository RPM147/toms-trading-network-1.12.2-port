package com.tom.trading.trade;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.HoverEvent;

import java.util.List;

/** One bounded public line, with every successful item/count leg in a plain-text tooltip. */
final class TradeReceiptChat {
    private TradeReceiptChat() {}

    static ITextComponent format(TradeReceipt receipt) {
        String main = receipt.getBuyerName() + " bought " + summary(receipt.getDeliveries())
                + " from " + receipt.getOwnerName() + " for " + summary(receipt.getPayments());
        StringBuilder details = new StringBuilder("Delivered:\n");
        append(details, receipt.getDeliveries());
        details.append("Paid:\n");
        append(details, receipt.getPayments());
        details.append("Completed trades: ").append(receipt.getCompleted())
                .append("\nBuyer UUID: ").append(receipt.getBuyerUuid())
                .append("\nOwner UUID: ").append(receipt.getOwnerUuid() == null ? "none" : receipt.getOwnerUuid())
                .append("\nMachine UUID: ").append(receipt.getMachineUuid());
        TextComponentString component = new TextComponentString(main);
        component.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new TextComponentString(details.toString())));
        return component;
    }

    private static String summary(List<TradeReceipt.Line> lines) {
        TradeReceipt.Line first = lines.get(0);
        return first.getQuantity() + " " + first.getLabel()
                + (lines.size() == 1 ? "" : " + " + (lines.size() - 1) + " more item variant(s) [hover]");
    }

    private static void append(StringBuilder text, List<TradeReceipt.Line> lines) {
        for (TradeReceipt.Line line : lines) {
            text.append(line.getQuantity()).append(" ").append(line.getLabel())
                    .append(" [").append(ReceiptText.label(line.getItemId(), 96, "item"))
                    .append('@').append(line.getMetadata()).append("; variant #").append(line.getVariant()).append("]\n");
        }
    }
}
