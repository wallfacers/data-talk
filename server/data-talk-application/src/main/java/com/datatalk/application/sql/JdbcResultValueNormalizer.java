package com.datatalk.application.sql;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Clob;
import java.sql.Blob;
import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Normalizes JDBC values so JSON consumers in JavaScript do not lose precision
 * for integer-like numbers outside the IEEE-754 safe integer range.
 * Also unwraps vendor-specific types (e.g., PGobject for JSON/JSONB, Oracle CLOB/BLOB)
 * into plain strings or byte arrays so Jackson serializes them correctly.
 */
public final class JdbcResultValueNormalizer {

    private static final BigInteger JS_SAFE_INTEGER_MAX = BigInteger.valueOf(9_007_199_254_740_991L);
    private static final BigInteger JS_SAFE_INTEGER_MIN = JS_SAFE_INTEGER_MAX.negate();

    /** Maximum CLOB/BLOB content to read for normalization (64 KB). */
    private static final int MAX_LOB_LENGTH = 64 * 1024;

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
        // Oracle CLOB normalization: read as String (truncated if large)
        if (value instanceof Clob clobValue) {
            return unwrapClob(clobValue);
        }
        // Oracle BLOB normalization: read as byte[] (truncated if large)
        if (value instanceof Blob blobValue) {
            return unwrapBlob(blobValue);
        }
        if (value instanceof Array arrayValue) {
            return unwrapArray(arrayValue);
        }
        // DuckDB UUID columns are returned as java.util.UUID objects
        if (value instanceof UUID uuidValue) {
            return uuidValue.toString();
        }
        // DuckDB STRUCT columns are returned as java.sql.Struct (DuckDBStruct)
        if (value instanceof Struct structValue) {
            return unwrapStruct(structValue);
        }
        // DuckDB MAP<K,V> columns may be returned as java.util.Map
        if (value instanceof Map<?, ?> mapValue) {
            return unwrapMap(mapValue);
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

    private static String unwrapClob(Clob clob) {
        try {
            long length = clob.length();
            if (length <= 0) return "";
            int readLen = (int) Math.min(length, MAX_LOB_LENGTH);
            String result = clob.getSubString(1, readLen);
            return result != null ? result : "";
        } catch (SQLException e) {
            // Fallback: try reading via Reader
            try (Reader reader = clob.getCharacterStream()) {
                if (reader == null) return "";
                StringBuilder sb = new StringBuilder();
                char[] buffer = new char[4096];
                int total = 0;
                int read;
                while (total < MAX_LOB_LENGTH && (read = reader.read(buffer)) != -1) {
                    int toAppend = Math.min(read, MAX_LOB_LENGTH - total);
                    sb.append(buffer, 0, toAppend);
                    total += toAppend;
                }
                return sb.toString();
            } catch (IOException | SQLException ex) {
                return clob.toString();
            }
        } finally {
            try { clob.free(); } catch (SQLException ignored) {}
        }
    }

    private static byte[] unwrapBlob(Blob blob) {
        try {
            long length = blob.length();
            if (length <= 0) return new byte[0];
            int readLen = (int) Math.min(length, MAX_LOB_LENGTH);
            byte[] result = blob.getBytes(1, readLen);
            return result != null ? result : new byte[0];
        } catch (SQLException e) {
            // Fallback: try reading via InputStream
            try (InputStream is = blob.getBinaryStream()) {
                if (is == null) return new byte[0];
                byte[] buffer = new byte[MAX_LOB_LENGTH];
                int total = is.read(buffer);
                if (total <= 0) return new byte[0];
                if (total < buffer.length) {
                    byte[] trimmed = new byte[total];
                    System.arraycopy(buffer, 0, trimmed, 0, total);
                    return trimmed;
                }
                return buffer;
            } catch (IOException | SQLException ex) {
                return blob.toString().getBytes();
            }
        } finally {
            try { blob.free(); } catch (SQLException ignored) {}
        }
    }

    private static Map<String, Object> unwrapStruct(Struct struct) {
        try {
            Object[] attrs = struct.getAttributes();
            // Try DuckDB-specific getFieldNames() via reflection; fall back to indexed keys
            String[] fieldNames = null;
            try {
                var method = struct.getClass().getMethod("getFieldNames");
                Object result = method.invoke(struct);
                if (result instanceof String[] names) {
                    fieldNames = names;
                }
            } catch (Exception ignored) {}

            var map = new LinkedHashMap<String, Object>();
            for (int i = 0; i < attrs.length; i++) {
                String key = (fieldNames != null && i < fieldNames.length) ? fieldNames[i] : "field_" + i;
                map.put(key, normalize(attrs[i]));
            }
            return map;
        } catch (SQLException e) {
            return Map.of("error", struct.toString());
        }
    }

    private static Map<String, Object> unwrapMap(Map<?, ?> map) {
        var result = new LinkedHashMap<String, Object>();
        for (var entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            result.put(key, normalize(entry.getValue()));
        }
        return result;
    }
}
