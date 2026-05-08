package com.datatalk.application.dashboard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed representation of a JSON Patch path with matchKey extension.
 * Supports:
 * - Plain: /title, /layout/cols
 * - Append: /widgets/-
 * - Index: /widgets/0/options
 * - MatchKey: /widgets[id=chart_w_abc12345]/options/title
 */
public sealed interface PatchPath {

    record Plain(String field) implements PatchPath {}
    record Append() implements PatchPath {}
    record Index(int index) implements PatchPath {}
    record MatchKey(String key, String value) implements PatchPath {}

    static PatchPath parse(String segment) {
        if ("-".equals(segment)) {
            return new Append();
        }
        Matcher matcher = MATCH_KEY_PATTERN.matcher(segment);
        if (matcher.matches()) {
            return new MatchKey(matcher.group(1), matcher.group(2));
        }
        try {
            return new Index(Integer.parseInt(segment));
        } catch (NumberFormatException e) {
            return new Plain(segment);
        }
    }

    Pattern MATCH_KEY_PATTERN = Pattern.compile("^\\[([^=]+)=([^\\]]+)\\]$");
}
