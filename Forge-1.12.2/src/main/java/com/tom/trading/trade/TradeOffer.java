package com.tom.trading.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TradeOffer {
    private final List<TradeDefinition> payments;
    private final List<TradeDefinition> sales;

    public TradeOffer(List<TradeDefinition> payments, List<TradeDefinition> sales) {
        this.payments = copyDefinitions(payments, "payments");
        this.sales = copyDefinitions(sales, "sales");
    }

    private static List<TradeDefinition> copyDefinitions(
            List<TradeDefinition> definitions,
            String fieldName
    ) {
        if (definitions == null) {
            throw new NullPointerException(fieldName);
        }
        if (definitions.size() > TradeLimits.MAX_DEFINITIONS) {
            throw new IllegalArgumentException(
                    fieldName + " cannot contain more than " + TradeLimits.MAX_DEFINITIONS + " definitions"
            );
        }
        ArrayList<TradeDefinition> copied = new ArrayList<>(definitions.size());
        for (TradeDefinition definition : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException(fieldName + " cannot contain null definitions");
            }
            copied.add(definition);
        }
        return Collections.unmodifiableList(copied);
    }

    public List<TradeDefinition> getPayments() {
        return payments;
    }

    public List<TradeDefinition> getSales() {
        return sales;
    }

    TradeResultCode validate() {
        if (payments.isEmpty() || sales.isEmpty()) {
            return TradeResultCode.INVALID_OFFER;
        }
        for (TradeDefinition sale : sales) {
            if (!sale.isConcrete()) {
                return TradeResultCode.INVALID_OFFER;
            }
        }
        return TradeResultCode.SUCCESS;
    }
}
