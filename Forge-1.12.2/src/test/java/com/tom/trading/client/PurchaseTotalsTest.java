package com.tom.trading.client;

import org.junit.Test;

import static org.junit.Assert.*;

public class PurchaseTotalsTest {
    @Test public void multipliesEveryDefinitionIndependentlyForMultiplePaymentAndDeliveryItems() {
        PurchaseTotals totals = PurchaseTotals.calculate("7", new int[]{3, 8, 0, 1, 2, 0, 64, 0});
        assertTrue(totals.valid());
        assertEquals(7, totals.batch());
        assertEquals(21L, totals.quantity(0));
        assertEquals(56L, totals.quantity(1));
        assertEquals(0L, totals.quantity(2));
        assertEquals(7L, totals.quantity(3));
        assertEquals(14L, totals.quantity(4));
        assertEquals(448L, totals.quantity(6));
        assertEquals(84L, totals.total(false));
        assertEquals(462L, totals.total(true));
    }

    @Test public void supportsMaximumBatchAndDefinitionsWithoutStackSizeTruncation() {
        PurchaseTotals totals = PurchaseTotals.calculate("1024", new int[]{1024, 1024, 1024, 1024, 1024, 1024, 1024, 1024});
        assertTrue(totals.valid());
        assertEquals(1048576L, totals.quantity(0));
        assertEquals(4194304L, totals.total(false));
        assertEquals(4194304L, totals.total(true));
    }

    @Test public void invalidOrEmptyBatchNeverShowsAQuote() {
        for (String value : new String[]{null, "", "0", "0000", "1025", "9999", "-1", "+1", " 1", "1 ", "1.5", "abc", "99999999999999999999"}) {
            PurchaseTotals totals = PurchaseTotals.calculate(value, new int[]{3, 0, 0, 0, 1, 0, 0, 0});
            assertFalse(String.valueOf(value), totals.valid());
            assertEquals(0L, totals.total(false));
            assertEquals(0L, totals.quantity(4));
        }
        assertEquals(1, PurchaseTotals.parseBatch("0001"));
    }

    @Test public void missingOfferLegOrMalformedQuantityNeverShowsPartialTotals() {
        for (int[] offer : new int[][]{null, new int[0], new int[7], new int[9], new int[8],
                {1, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 1, 0, 0, 0},
                {1, -1, 0, 0, 1, 0, 0, 0}, {1, 0, 0, 0, 1025, 0, 0, 0},
                {Integer.MAX_VALUE, 0, 0, 0, 1, 0, 0, 0}}) {
            PurchaseTotals totals = PurchaseTotals.calculate("2", offer);
            assertFalse(totals.valid());
            assertEquals(0L, totals.total(false));
            assertEquals(0L, totals.total(true));
            assertEquals(0L, totals.quantity(0));
        }
    }

    @Test public void quoteDoesNotChangeWhenSourceOfferArrayIsMutated() {
        int[] offer = {3, 0, 0, 0, 1, 0, 0, 0};
        PurchaseTotals totals = PurchaseTotals.calculate("3", offer);
        offer[0] = 100;
        assertEquals(9L, totals.quantity(0));
        assertEquals(3L, totals.quantity(4));
    }
}
