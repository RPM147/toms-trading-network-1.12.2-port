package com.tom.trading.trade;

/** Plain, visibly abbreviated server labels. Never interpret item NBT as chat components. */
final class ReceiptText {
    private ReceiptText() {}

    static String label(String input, int limit, String fallback) {
        if (input == null) return fallback;
        StringBuilder result = new StringBuilder();
        int count = 0;
        int scanned = 0;
        for (int offset = 0; offset < input.length();) {
            if (++scanned > limit * 4) {
                if (count > 0) {
                    if (count == limit) result.setLength(result.offsetByCodePoints(0, limit - 1));
                    result.append('\u2026');
                }
                break;
            }
            int cp = input.codePointAt(offset);
            offset += Character.charCount(cp);
            if (cp == '\u00a7') {
                if (offset < input.length() && "0123456789abcdefklmnor".indexOf(
                        Character.toLowerCase(input.charAt(offset))) >= 0) offset++;
                continue;
            }
            int type = Character.getType(cp);
            if (Character.isISOControl(cp) || type == Character.FORMAT || type == Character.SURROGATE
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) continue;
            if (count++ == limit) {
                result.setLength(result.offsetByCodePoints(0, limit - 1));
                result.append('\u2026');
                break;
            }
            result.appendCodePoint(cp);
        }
        String text = result.toString().trim();
        return text.isEmpty() ? fallback : text;
    }
}
