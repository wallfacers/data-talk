package com.datatalk.application.ingestion.parser;

import com.datatalk.domain.ingestion.InferredType;

import java.math.BigDecimal;
import java.util.*;

/**
 * Shared type inference logic used by both JSON and JSONL parsers.
 *
 * <p>Fallback chain: BOOLEAN → INTEGER_32 → INTEGER_64 → DECIMAL → DATE → TIMESTAMP
 * → STRING (capacity by longest sample: 64/256/500/LONG) → JSON.
 * Mixed types resolve to the widest type in the chain.</p>
 */
final class TypeInferrer {

    // Ordered from narrowest to widest
    private static final List<InferredType> WIDENESS_ORDER = List.of(
        InferredType.BOOLEAN,
        InferredType.INTEGER_32,
        InferredType.INTEGER_64,
        InferredType.DECIMAL,
        InferredType.DATE,
        InferredType.TIMESTAMP,
        InferredType.STRING_64,
        InferredType.STRING_256,
        InferredType.STRING_500,
        InferredType.STRING_LONG,
        InferredType.JSON
    );

    private TypeInferrer() {}

    /**
     * Infer the widest {@link InferredType} from a list of raw values.
     * Null values are skipped. If all values are null, returns {@code STRING_256}.
     */
    static InferredType infer(List<Object> values) {
        Set<InferredType> votes = EnumSet.noneOf(InferredType.class);

        int maxStringLength = 0;

        for (Object v : values) {
            if (v == null) continue;

            if (v instanceof Boolean) {
                votes.add(InferredType.BOOLEAN);
            } else if (v instanceof Integer) {
                votes.add(InferredType.INTEGER_32);
            } else if (v instanceof Long) {
                votes.add(InferredType.INTEGER_64);
            } else if (v instanceof Double || v instanceof BigDecimal) {
                votes.add(InferredType.DECIMAL);
            } else if (v instanceof String s) {
                if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    votes.add(InferredType.DATE);
                } else if (s.matches("\\d{4}-\\d{2}-\\d{2}T.*")) {
                    votes.add(InferredType.TIMESTAMP);
                } else {
                    maxStringLength = Math.max(maxStringLength, s.length());
                    votes.add(stringCapacity(maxStringLength));
                }
            } else {
                // Lists, maps, or any other complex type
                votes.add(InferredType.JSON);
            }
        }

        if (votes.isEmpty()) {
            // All nulls
            return InferredType.STRING_256;
        }

        // Resolve to the widest type
        InferredType widest = InferredType.BOOLEAN;
        for (InferredType candidate : votes) {
            if (WIDENESS_ORDER.indexOf(candidate) > WIDENESS_ORDER.indexOf(widest)) {
                widest = candidate;
            }
        }

        // If the widest is a STRING type but we had non-string votes (date/timestamp/numeric/boolean),
        // we need STRING_64 as the minimum string representation.
        // But if the widest was already decided by a string value, keep it.

        return widest;
    }

    /**
     * Choose string capacity based on the maximum observed string length.
     */
    static InferredType stringCapacity(int maxLength) {
        if (maxLength <= 64) return InferredType.STRING_64;
        if (maxLength <= 256) return InferredType.STRING_256;
        if (maxLength <= 500) return InferredType.STRING_500;
        return InferredType.STRING_LONG;
    }

    /**
     * Collect up to {@code limit} distinct non-null string representations from values.
     */
    static List<String> collectSampleValues(List<Object> values, int limit) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Object v : values) {
            if (v == null) continue;
            String repr = String.valueOf(v);
            if (seen.add(repr) && seen.size() >= limit) break;
        }
        return List.copyOf(seen);
    }

    /**
     * Check whether all values in the list are null.
     */
    static boolean allNull(List<Object> values) {
        return values.stream().allMatch(Objects::isNull);
    }
}
