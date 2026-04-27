package com.datatalk.application.sql;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Array;
import java.sql.SQLException;

/**
 * Normalizes JDBC values so JSON consumers in JavaScript do not lose precision
 * for integer-like numbers outside the IEEE-754 safe integer range.
 * Also unwraps vendor-specific types (e.g., PGobject for JSON/JSONB) into
 * plain strings so Jackson serializes them correctly.
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
        if (value instanceof Array arrayValue) {
            return unwrapArray(arrayValue);
        }
        // Vendor-specific JDBC wrapper types (e.g., PGobject for JSON/JSONB)
        // expose the actual value via a getValue() method. Skip standard library
        // types to avoid unnecessary reflection overhead.
        String className = value.getClass().getName();
        if (!className.startsWith("java.") && !className.startsWith("javax.") && hasGetValue(value)) {
            return invokeGetValue(value);
        }
        return value;
    }

    private static boolean isSafeInteger(BigInteger value) {
        return value.compareTo(JS_SAFE_INTEGER_MIN) >= 0 && value.compareTo(JS_SAFE_INTEGER_MAX) <= 0;
    }

    private static Object unwrapArray(Array arrayValue) {
        try {
            return arrayValue.getArray();
        } catch (SQLException e) {
            return arrayValue.toString();
        }
    }

    private static boolean hasGetValue(Object value) {
        try {
            var method = value.getClass().getMethod("getValue");
            return method.getReturnType() == String.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static Object invokeGetValue(Object value) {
        try {
            return value.getClass().getMethod("getValue").invoke(value);
        } catch (Exception e) {
            return value.toString();
        }
    }
}
