package com.tom.trading.trade;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.event.HoverEvent;

import java.util.List;

/** One bounded public line, with every successful item/count leg in a plain-text tooltip. */
final class TradeReceiptChat {
    private static final String PREFIX = "chat.toms_trading_network.receipt.";

    private TradeReceiptChat() {}

    static ITextComponent format(TradeReceipt receipt) {
        Object owner = receipt.getOwnerUuid() == null
                ? new TextComponentTranslation(PREFIX + "unowned")
                : receipt.getOwnerName();
        ITextComponent component = new TextComponentTranslation(PREFIX + "summary",
                receipt.getBuyerName(), summary(receipt.getDeliveries()), owner, summary(receipt.getPayments()));
        TextComponentString details = new TextComponentString("");
        details.appendSibling(new TextComponentTranslation(PREFIX + "delivered")).appendText("\n");
        details.appendText(formatLines(receipt.getDeliveries()));
        details.appendSibling(new TextComponentTranslation(PREFIX + "paid")).appendText("\n");
        details.appendText(formatLines(receipt.getPayments()));
        details.appendSibling(new TextComponentTranslation(PREFIX + "completed", receipt.getCompleted())).appendText("\n");
        details.appendSibling(new TextComponentTranslation(PREFIX + "buyer_uuid", receipt.getBuyerUuid().toString())).appendText("\n");
        details.appendSibling(new TextComponentTranslation(PREFIX + "owner_uuid",
                receipt.getOwnerUuid() == null
                        ? new TextComponentTranslation(PREFIX + "none")
                        : receipt.getOwnerUuid().toString())).appendText("\n");
        details.appendSibling(new TextComponentTranslation(PREFIX + "machine_uuid", receipt.getMachineUuid().toString()));
        component.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, details));
        return component;
    }

    private static Object summary(List<TradeReceipt.Line> lines) {
        TradeReceipt.Line first = lines.get(0);
        String lead = first.getQuantity() + " " + first.getLabel();
        return lines.size() == 1 ? lead
                : new TextComponentTranslation(PREFIX + "more", lead, lines.size() - 1);
    }

    private static String formatLines(List<TradeReceipt.Line> lines) {
        StringBuilder text = new StringBuilder();
        for (TradeReceipt.Line line : lines) {
            text.append(line.getQuantity()).append(" ").append(line.getLabel())
                    .append(" [").append(ReceiptText.label(line.getItemId(), 96, "item"))
                    .append('@').append(line.getMetadata()).append("; variant #").append(line.getVariant()).append("]\n");
        }
        return text.toString();
    }
}
