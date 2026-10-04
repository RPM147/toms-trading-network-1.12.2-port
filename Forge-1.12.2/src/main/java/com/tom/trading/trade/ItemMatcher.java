package com.tom.trading.trade;

public final class ItemMatcher {
    private ItemMatcher() {
    }

    public static boolean matches(TradeItem candidate, TradeDefinition definition) {
        if (candidate == null || definition == null) {
            return false;
        }
        if (definition.getMatchType() == TradeDefinition.MatchType.ORE_DICTIONARY) {
            return candidate.hasOreName(definition.getOreName());
        }

        TradeItem prototype = definition.getPrototype();
        return candidate.sameItemAndMetadata(prototype)
                && (!definition.isMatchNbt() || candidate.sameTag(prototype));
    }

    public static boolean canMerge(TradeItem first, TradeItem second) {
        return first != null && first.sameStackIdentity(second);
    }
}
