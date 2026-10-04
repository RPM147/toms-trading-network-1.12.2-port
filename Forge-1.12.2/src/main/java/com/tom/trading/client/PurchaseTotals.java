package com.tom.trading.client;

import com.tom.trading.trade.TradeLimits;

/** Immutable, display-only quote for a requested batch. It never predicts available stock or commits a trade. */
public final class PurchaseTotals {
    private final int batch;
    private final long[] quantities;
    private final boolean valid;

    private PurchaseTotals(int batch, long[] quantities, boolean valid) {
        this.batch = batch;
        this.quantities = quantities;
        this.valid = valid;
    }

    /** Zero denotes an empty definition; negative or oversized definitions invalidate the whole preview. */
    public static PurchaseTotals calculate(String text, int[] perTrade) {
        int batch = parseBatch(text);
        long[] totals = new long[8];
        if (batch == 0 || perTrade == null || perTrade.length != totals.length)
            return new PurchaseTotals(batch, totals, false);
        boolean payment = false, delivery = false;
        for (int slot = 0; slot < totals.length; slot++) {
            int quantity = perTrade[slot];
            if (quantity < 0 || quantity > TradeLimits.MAX_QUANTITY)
                return new PurchaseTotals(batch, new long[8], false);
            // Cast before multiplication. Keep the quote safe if the supported limits grow later.
            totals[slot] = (long) quantity * batch;
            if (quantity > 0) {
                if (slot < 4) payment = true;
                else delivery = true;
            }
        }
        return new PurchaseTotals(batch, totals, payment && delivery);
    }

    public static int parseBatch(String text) {
        if (text == null || text.isEmpty() || text.length() > Integer.toString(TradeLimits.MAX_BATCH_SIZE).length()) return 0;
        int value = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') return 0;
            value = value * 10 + c - '0';
        }
        return value >= 1 && value <= TradeLimits.MAX_BATCH_SIZE ? value : 0;
    }

    public int batch() { return batch; }
    public boolean valid() { return valid; }
    public long quantity(int slot) {
        if (slot < 0 || slot >= quantities.length) throw new IndexOutOfBoundsException("Definition slot");
        return valid ? quantities[slot] : 0;
    }
    /** Item counts across one side, not a monetary conversion between unlike currencies. */
    public long total(boolean delivery) {
        if (!valid) return 0;
        long result = 0;
        for (int slot = delivery ? 4 : 0, end = slot + 4; slot < end; slot++) result += quantities[slot];
        return result;
    }
}
