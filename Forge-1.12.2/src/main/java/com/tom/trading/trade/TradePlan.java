package com.tom.trading.trade;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TradePlan {
    private final TradePolicy policy;
    private final int requestedCount;
    private final int completedCount;
    private final TradeResultCode resultCode;
    private final InventorySnapshot playerBefore;
    private final InventorySnapshot playerAfter;
    private final InventorySnapshot saleStockBefore;
    private final InventorySnapshot saleStockAfter;
    private final InventorySnapshot earningsBefore;
    private final InventorySnapshot earningsAfter;
    private final Map<TradeItem, Long> paidItems;
    private final Map<TradeItem, Long> deliveredItems;

    TradePlan(
            TradePolicy policy,
            int requestedCount,
            int completedCount,
            TradeResultCode resultCode,
            InventorySnapshot playerBefore,
            InventorySnapshot playerAfter,
            InventorySnapshot saleStockBefore,
            InventorySnapshot saleStockAfter,
            InventorySnapshot earningsBefore,
            InventorySnapshot earningsAfter,
            Map<TradeItem, Long> paidItems,
            Map<TradeItem, Long> deliveredItems
    ) {
        this.policy = policy;
        this.requestedCount = requestedCount;
        this.completedCount = completedCount;
        this.resultCode = resultCode;
        this.playerBefore = playerBefore;
        this.playerAfter = playerAfter;
        this.saleStockBefore = saleStockBefore;
        this.saleStockAfter = saleStockAfter;
        this.earningsBefore = earningsBefore;
        this.earningsAfter = earningsAfter;
        this.paidItems = Collections.unmodifiableMap(new LinkedHashMap<>(paidItems));
        this.deliveredItems = Collections.unmodifiableMap(new LinkedHashMap<>(deliveredItems));
    }

    public TradePolicy getPolicy() {
        return policy;
    }

    public int getRequestedCount() {
        return requestedCount;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public TradeResultCode getResultCode() {
        return resultCode;
    }

    public boolean isComplete() {
        return completedCount == requestedCount && resultCode == TradeResultCode.SUCCESS;
    }

    public InventorySnapshot getPlayerBefore() {
        return playerBefore;
    }

    public InventorySnapshot getPlayerAfter() {
        return playerAfter;
    }

    public InventorySnapshot getSaleStockBefore() {
        return saleStockBefore;
    }

    public InventorySnapshot getSaleStockAfter() {
        return saleStockAfter;
    }

    public InventorySnapshot getEarningsBefore() {
        return earningsBefore;
    }

    public InventorySnapshot getEarningsAfter() {
        return earningsAfter;
    }

    /** Actual successful legs, not requested templates or the net inventory difference. */
    public Map<TradeItem, Long> getPaidItems() { return paidItems; }
    public Map<TradeItem, Long> getDeliveredItems() { return deliveredItems; }
}
