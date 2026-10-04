package com.tom.trading.directory;

import java.text.Normalizer;
import java.util.Locale;

/** Bounded plain labels/queries. Folding affects search only, never UUID identity. */
public final class DirectoryText {
    public static final int MAX_CHARACTERS = 64;
    private DirectoryText() {}
    public static boolean allowed(int c) {
        return !Character.isISOControl(c) && Character.getType(c) != Character.FORMAT
                && c != 0xA7 && (c < 0xD800 || c > 0xDFFF);
    }
    public static boolean valid(String value) {
        return value != null && value.length() <= MAX_CHARACTERS * 2
                && value.codePointCount(0, value.length()) <= MAX_CHARACTERS
                && value.codePoints().allMatch(DirectoryText::allowed);
    }
    public static String label(String input) {
        if (input == null) return "";
        StringBuilder result = new StringBuilder();
        input.codePoints().filter(DirectoryText::allowed).limit(MAX_CHARACTERS).forEach(result::appendCodePoint);
        return result.toString();
    }
    public static String fold(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        normalized.codePoints().filter(c -> Character.getType(c) != Character.NON_SPACING_MARK)
                .forEach(c -> result.appendCodePoint(c == 0x131 ? 'i' : c));
        return result.toString().trim();
    }
}
