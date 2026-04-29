package com.datatalk.domain.er;

import java.util.List;

/** Namespace for ER discovery and generation errors. */
public final class ErErrors {

    private ErErrors() {}

    public static final class DialectUnsupportedException extends RuntimeException {
        private final String kind;

        public DialectUnsupportedException(String kind) {
            super("ER does not support dialect: " + kind);
            this.kind = kind;
        }

        public String kind() {
            return kind;
        }
    }

    public static final class ErPayloadOversizedException extends RuntimeException {
        private final int seenTables;
        private final int limit;

        public ErPayloadOversizedException(int seenTables, int limit) {
            super("ER payload exceeded limit: " + seenTables + " > " + limit);
            this.seenTables = seenTables;
            this.limit = limit;
        }

        public int seenTables() {
            return seenTables;
        }

        public int limit() {
            return limit;
        }
    }

    public static final class TablesNotFoundException extends RuntimeException {
        private final List<String> missing;

        public TablesNotFoundException(List<String> missing) {
            super("Tables not found: " + String.join(", ", missing));
            this.missing = List.copyOf(missing);
        }

        public List<String> missing() {
            return missing;
        }
    }
}
