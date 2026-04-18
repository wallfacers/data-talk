package com.datatalk.domain.util;

/** Common string utilities for null/blank checks. */
public final class Strings {

    /** Returns true if s is null or whitespace-only. */
    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Returns true if s is non-null and non-blank. */
    public static boolean isNotBlank(String s) {
        return !isBlank(s);
    }

    /** Returns default if s is null or blank; otherwise returns s. */
    public static String defaultIfBlank(String s, String defaultValue) {
        return isBlank(s) ? defaultValue : s;
    }

    private Strings() {} // prevent instantiation
}