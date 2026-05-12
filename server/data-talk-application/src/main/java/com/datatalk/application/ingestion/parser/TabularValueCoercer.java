package com.datatalk.application.ingestion.parser;

/**
 * Coerces tabular cell strings (from CSV / HTML) into the most specific Java
 * primitive wrapper that fits, so {@link TypeInferrer} can vote a meaningful
 * column type instead of falling back to STRING.
 *
 * <p>The coercion ladder is intentionally conservative — only widely-unambiguous
 * shapes are converted; anything else stays as the original String to preserve
 * downstream parsing freedom.
 *
 * <ol>
 *   <li>{@code null} / empty / whitespace-only → {@code null}</li>
 *   <li>{@code "true"} / {@code "false"} (case-insensitive) → {@link Boolean}</li>
 *   <li>Integer-shaped literal that fits in int range → {@link Integer}</li>
 *   <li>Integer-shaped literal larger than int range → {@link Long} (triggers INTEGER_64)</li>
 *   <li>Decimal-shaped literal → {@link Double}</li>
 *   <li>Anything else → original {@link String} (un-trimmed, caller may trim).</li>
 * </ol>
 *
 * Locale-sensitive numbers (e.g. {@code "1,234"}, {@code "1.234,56"}) intentionally
 * stay as String — locale parsing is out of scope and would silently misinterpret
 * thousands separators.
 */
final class TabularValueCoercer {

    private TabularValueCoercer() {}

    static Object coerce(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;

        if (trimmed.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (trimmed.equalsIgnoreCase("false")) return Boolean.FALSE;

        // Integer-shape first. Parse as long to detect 32-bit overflow and promote.
        if (looksLikeInteger(trimmed)) {
            try {
                long longVal = Long.parseLong(trimmed);
                if (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE) {
                    return (int) longVal;
                }
                return longVal;
            } catch (NumberFormatException ignored) {
                // fall through to decimal / string
            }
        }

        if (looksLikeDecimal(trimmed)) {
            try {
                return Double.parseDouble(trimmed);
            } catch (NumberFormatException ignored) {
                // fall through to string
            }
        }

        return raw;
    }

    private static boolean looksLikeInteger(String s) {
        int start = (s.charAt(0) == '-' || s.charAt(0) == '+') ? 1 : 0;
        if (start == s.length()) return false;
        for (int i = start; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private static boolean looksLikeDecimal(String s) {
        // Rough sieve — strict parsing happens in Double.parseDouble.
        // Reject things that contain locale separators or letters other than e/E/+/-/.
        boolean dotSeen = false;
        boolean digitSeen = false;
        int start = (s.charAt(0) == '-' || s.charAt(0) == '+') ? 1 : 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) { digitSeen = true; continue; }
            if (c == '.' && !dotSeen) { dotSeen = true; continue; }
            if ((c == 'e' || c == 'E') && digitSeen) return true; // delegate
            return false;
        }
        return digitSeen && dotSeen;
    }
}
