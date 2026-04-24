package com.datatalk.application.sql;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Normalizes JDBC values so JSON consumers in JavaScript do not lose precision
 * for integer-like numbers outside the IEEE-754 safe integer range.
 */
public final class JdbcResultValueNormalizer {

    private static final BigInteger JS_SAFE_INTEGER_MAX = BigInteger.valueOf(9_007_199_254_740_991L);
    private static final BigInteger JS_SAFE_INTEGER_MIN = JS_SAFE_INTEGER_MAX.negate();

    private JdbcResultValueNormalizer() {}

    public static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long longValue) {
            return isSafeInteger(BigInteger.valueOf(longValue)) ? longValue : Long.toString(longValue);
        }
        if (value instanceof BigInteger bigIntegerValue) {
            return isSafeInteger(bigIntegerValue) ? bigIntegerValue.longValue() : bigIntegerValue.toString();
        }
        if (value instanceof BigDecimal bigDecimalValue && bigDecimalValue.scale() <= 0) {
            BigInteger integerValue = bigDecimalValue.toBigInteger();
            return isSafeInteger(integerValue) ? integerValue.longValue() : integerValue.toString();
        }
        return value;
    }

    private static boolean isSafeInteger(BigInteger value) {
        return value.compareTo(JS_SAFE_INTEGER_MIN) >= 0 && value.compareTo(JS_SAFE_INTEGER_MAX) <= 0;
    }
}
