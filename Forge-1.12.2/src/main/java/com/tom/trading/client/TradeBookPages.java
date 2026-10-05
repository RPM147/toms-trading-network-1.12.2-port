package com.tom.trading.client;

import java.util.*;
import java.util.function.Function;

/** Read-only vanilla book layout: fit whole recent entries first, then split into at most fifty pages. */
public final class TradeBookPages {
    public static final int MAX_PAGES = 50, LINES = 14;
    private TradeBookPages() {}
    /** Add localized role captions without rewriting the save-owned compact receipt text. */
    public static String displayEntry(String record, String buyerCaption, String sellerCaption) {
        int seller = record.indexOf(" -> ");
        if (seller <= 0) return record;
        int items = record.indexOf(" | ", seller + 4);
        if (items <= seller + 4 || items + 3 >= record.length()) return record;
        return buyerCaption + ": " + record.substring(0, seller) + "\n"
                + sellerCaption + ": " + record.substring(seller + 4, items) + "\n"
                + record.substring(items + 3);
    }
    public static List<String> layout(List<String> records, Function<String, List<String>> wrap) {
        List<String> lines = new ArrayList<>(); int capacity = MAX_PAGES * LINES;
        for (String record : records) {
            List<String> row = wrap.apply(record);
            if (!lines.isEmpty() && lines.size() + row.size() + 1 > capacity) break;
            if (!lines.isEmpty()) lines.add("");
            if (row.size() > capacity) { lines.addAll(row.subList(0, capacity - 1)); lines.add("..."); break; }
            lines.addAll(row);
        }
        if (lines.isEmpty()) return Collections.singletonList("");
        List<String> pages = new ArrayList<>();
        for (int start = 0; start < lines.size(); start += LINES)
            pages.add(String.join("\n", lines.subList(start, Math.min(lines.size(), start + LINES))));
        return Collections.unmodifiableList(pages);
    }
}
