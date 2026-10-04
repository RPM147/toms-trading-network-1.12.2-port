package com.tom.trading.trade;

import java.util.Objects;

public final class TradeDefinition {
    public enum MatchType {
        DIRECT,
        ORE_DICTIONARY
    }

    private final MatchType matchType;
    private final TradeItem prototype;
    private final int quantity;
    private final boolean matchNbt;
    private final String oreName;

    private TradeDefinition(
            MatchType matchType,
            TradeItem prototype,
            int quantity,
            boolean matchNbt,
            String oreName
    ) {
        if (quantity < 1 || quantity > TradeLimits.MAX_QUANTITY) {
            throw new IllegalArgumentException(
                    "quantity must be between 1 and " + TradeLimits.MAX_QUANTITY
            );
        }
        this.matchType = Objects.requireNonNull(matchType, "matchType");
        this.prototype = Objects.requireNonNull(prototype, "prototype");
        this.quantity = quantity;
        this.matchNbt = matchNbt;
        this.oreName = oreName;
    }

    public static TradeDefinition direct(TradeItem prototype, int quantity, boolean matchNbt) {
        return new TradeDefinition(MatchType.DIRECT, prototype, quantity, matchNbt, null);
    }

    public static TradeDefinition orePayment(TradeItem sample, String oreName, int quantity) {
        TradeItem.validateOreName(oreName);
        if (sample == null || !sample.hasOreName(oreName)) {
            throw new IllegalArgumentException("Ore name is not associated with the selected sample");
        }
        return new TradeDefinition(MatchType.ORE_DICTIONARY, sample, quantity, false, oreName);
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public TradeItem getPrototype() {
        return prototype;
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean isMatchNbt() {
        return matchNbt;
    }

    public String getOreName() {
        return oreName;
    }

    public boolean isConcrete() {
        return matchType == MatchType.DIRECT;
    }
}
