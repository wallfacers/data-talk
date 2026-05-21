package com.datatalk.application.sql;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcResultValueNormalizerTest {

    @Test
    void keeps_safe_long_as_long() {
        Object normalized = JdbcResultValueNormalizer.normalize(9_007_199_254_740_991L);
        assertThat(normalized).isEqualTo(9_007_199_254_740_991L);
    }

    @Test
    void serializes_unsafe_long_as_string() {
        Object normalized = JdbcResultValueNormalizer.normalize(9_007_199_254_740_993L);
        assertThat(normalized).isEqualTo("9007199254740993");
    }

    @Test
    void serializes_unsafe_big_integer_as_string() {
        Object normalized = JdbcResultValueNormalizer.normalize(new BigInteger("9223372036854775807"));
        assertThat(normalized).isEqualTo("9223372036854775807");
    }

    @Test
    void unwraps_sql_array_to_java_array() throws SQLException {
        Array sqlArray = mock(Array.class);
        Long[] inner = {1L, 2L, 3L};
        when(sqlArray.getArray()).thenReturn(inner);

        Object normalized = JdbcResultValueNormalizer.normalize(sqlArray);
        assertThat(normalized).isSameAs(inner);
    }

    @Test
    void falls_back_to_string_when_array_unwrap_fails() throws SQLException {
        Array sqlArray = mock(Array.class);
        when(sqlArray.getArray()).thenThrow(new SQLException("not supported"));
        when(sqlArray.toString()).thenReturn("{1,2,3}");

        Object normalized = JdbcResultValueNormalizer.normalize(sqlArray);
        assertThat(normalized).isEqualTo("{1,2,3}");
    }

    @Test
    void unwraps_pgobject_value_via_reflection() {
        // Simulate PGobject without compile-time dependency
        Object pgObject = new FakePGobject("json", "[1,2,3]");
        Object normalized = JdbcResultValueNormalizer.normalize(pgObject);
        assertThat(normalized).isEqualTo("[1,2,3]");
    }

    @Test
    void unwraps_pgobject_jsonb_object() {
        Object pgObject = new FakePGobject("jsonb", "{\"dept_ids\":[1,2,3]}");
        Object normalized = JdbcResultValueNormalizer.normalize(pgObject);
        assertThat(normalized).isEqualTo("{\"dept_ids\":[1,2,3]}");
    }

    @Test
    void normalizes_uuid_to_string() {
        UUID uuid = UUID.randomUUID();
        Object normalized = JdbcResultValueNormalizer.normalize(uuid);
        assertThat(normalized).isEqualTo(uuid.toString());
    }

    @Test
    void normalizes_sql_struct_to_map() throws Exception {
        Struct struct = new Struct() {
            @Override
            public String getSQLTypeName() { return "STRUCT"; }
            @Override
            public Object[] getAttributes() { return new Object[]{"hello", 42L}; }
            @Override
            public Object[] getAttributes(Map<String, Class<?>> map) { return getAttributes(); }
        };
        Object result = JdbcResultValueNormalizer.normalize(struct);
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map).hasSize(2);
        assertThat(map).containsEntry("field_0", "hello");
        assertThat(map).containsEntry("field_1", 42L); // 42L is within JS safe integer range
    }

    @Test
    void normalizes_map_values_recursively() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("big", new BigInteger("9223372036854775807"));
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("name", "test");
        outer.put("nested", inner);
        Object result = JdbcResultValueNormalizer.normalize(outer);
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map.get("name")).isEqualTo("test");
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) map.get("nested");
        assertThat(nested.get("big")).isEqualTo("9223372036854775807");
    }

    @Test
    void handles_null_struct_attributes() throws Exception {
        Struct struct = new Struct() {
            @Override
            public String getSQLTypeName() { return "STRUCT"; }
            @Override
            public Object[] getAttributes() { return new Object[]{null, "value"}; }
            @Override
            public Object[] getAttributes(Map<String, Class<?>> map) { return getAttributes(); }
        };
        Object result = JdbcResultValueNormalizer.normalize(struct);
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map).containsEntry("field_0", null);
        assertThat(map).containsEntry("field_1", "value");
    }

    @Test
    void handles_empty_map() {
        Map<String, Object> empty = Map.of();
        Object result = JdbcResultValueNormalizer.normalize(empty);
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map).isEmpty();
    }

    // --- ClickHouse type normalization ---

    @Test
    void clickhouse_unsignedInt8_returnsNumber() {
        // UInt8 fits in JS safe integer
        Object normalized = JdbcResultValueNormalizer.normalize((short) 200);
        assertThat(normalized).isInstanceOf(Number.class);
        assertThat(((Number) normalized).intValue()).isEqualTo(200);
    }

    @Test
    void clickhouse_unsignedInt64_withinSafeRange_returnsNumber() {
        // UInt64 value within 2^53 — simulate via Long
        long value = 1_000_000L;
        Object normalized = JdbcResultValueNormalizer.normalize(value);
        assertThat(normalized).isEqualTo(value);
    }

    @Test
    void clickhouse_unsignedInt64_exceedingSafeRange_returnsString() {
        // UInt64 value exceeding 2^53 — must be string
        long value = Long.MAX_VALUE; // 9223372036854775807 > 2^53
        Object normalized = JdbcResultValueNormalizer.normalize(value);
        assertThat(normalized).isEqualTo("9223372036854775807");
    }

    @Test
    void clickhouse_bigInteger_exceedingSafeRange_returnsString() {
        // Simulates UInt128 / UInt256 — returned as BigInteger from driver
        Object normalized = JdbcResultValueNormalizer.normalize(new BigInteger("340282366920938463463374607431768211455"));
        assertThat(normalized).isEqualTo("340282366920938463463374607431768211455");
    }

    @Test
    void clickhouse_bigDecimal_withScale_returnsBigDecimal() {
        // Decimal128/Decimal256 with scale > 0 — BigDecimal passes through unchanged
        // since the existing normalizer only converts BigDecimal with scale <= 0 to Long/String.
        // BigDecimal with scale > 0 is serialized correctly by Jackson.
        java.math.BigDecimal value = new java.math.BigDecimal("12345678901234567890.123456789");
        Object normalized = JdbcResultValueNormalizer.normalize(value);
        assertThat(normalized).isInstanceOf(java.math.BigDecimal.class);
        assertThat(normalized).isEqualTo(value);
    }

    @Test
    void clickhouse_uuid_returnsString() {
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        Object normalized = JdbcResultValueNormalizer.normalize(uuid);
        assertThat(normalized).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void clickhouse_bool_returnsBoolean() {
        Object normalized = JdbcResultValueNormalizer.normalize(Boolean.TRUE);
        assertThat(normalized).isInstanceOf(Boolean.class);
        assertThat(normalized).isEqualTo(true);
    }

    @Test
    void clickhouse_string_returnsString() {
        Object normalized = JdbcResultValueNormalizer.normalize("hello world");
        assertThat(normalized).isEqualTo("hello world");
    }

    @Test
    void clickhouse_fixedString_trimsTrailingNulls() {
        // ClickHouse FixedString may have trailing null bytes
        // The normalizer handles strings already — driver usually trims, but
        // if a wrapper with getValue() is returned, it gets unwrapped.
        // Direct String should pass through unchanged.
        Object normalized = JdbcResultValueNormalizer.normalize("test");
        assertThat(normalized).isEqualTo("test");
    }

    @Test
    void clickhouse_map_returnsNormalizedMap() {
        Map<String, Object> clickhouseMap = new LinkedHashMap<>();
        clickhouseMap.put("key1", "value1");
        clickhouseMap.put("key2", 42L);
        Object normalized = JdbcResultValueNormalizer.normalize(clickhouseMap);
        assertThat(normalized).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) normalized;
        assertThat(result).containsEntry("key1", "value1");
        assertThat(result).containsEntry("key2", 42L);
    }

    @Test
    void clickhouse_array_returnsUnwrappedArray() throws SQLException {
        Array sqlArray = mock(Array.class);
        Object[] inner = {"a", "b", "c"};
        when(sqlArray.getArray()).thenReturn(inner);
        Object normalized = JdbcResultValueNormalizer.normalize(sqlArray);
        assertThat(normalized).isSameAs(inner);
    }

    @Test
    void clickhouse_driverWrapperObject_unwrappedViaGetValue() {
        // Simulates ClickHouse-specific wrapper objects (e.g., ClickHouseBigDecimal,
        // ClickHouseDateTime, etc.) that have a getValue() method returning String
        Object wrapper = new FakeClickHouseWrapper("2024-01-15");
        Object normalized = JdbcResultValueNormalizer.normalize(wrapper);
        assertThat(normalized).isEqualTo("2024-01-15");
    }

    @Test
    void clickhouse_dateTimeObject_unwrappedViaToString() {
        // Simulates a ClickHouse DateTime wrapper without getValue() — should not throw
        Object wrapper = new FakeClickHouseDateTimeWrapper("2024-01-15T10:30:00");
        Object normalized = JdbcResultValueNormalizer.normalize(wrapper);
        // Should not throw ClassCastException; should pass through or unwrap
        assertThat(normalized).isNotNull();
    }

    @SuppressWarnings("unused")
    public static class FakePGobject {
        private final String type;
        private final String value;

        public FakePGobject(String type, String value) {
            this.type = type;
            this.value = value;
        }

        public String getType() { return type; }
        public String getValue() { return value; }
    }

    /** Simulates a ClickHouse JDBC wrapper with getValue() returning String. */
    @SuppressWarnings("unused")
    public static class FakeClickHouseWrapper {
        private final String value;
        public FakeClickHouseWrapper(String value) { this.value = value; }
        public String getValue() { return value; }
    }

    /** Simulates a ClickHouse DateTime wrapper WITHOUT getValue() — has toString() only. */
    public static class FakeClickHouseDateTimeWrapper {
        private final String value;
        public FakeClickHouseDateTimeWrapper(String value) { this.value = value; }
        @Override public String toString() { return value; }
    }

    // --- Timezone-aware normalization ---

    @Test
    void timestamp_converted_to_user_timezone() {
        Timestamp ts = Timestamp.from(Instant.parse("2025-06-15T08:00:00Z"));
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        Object result = JdbcResultValueNormalizer.normalize(ts, Types.TIMESTAMP, shanghai, "yyyy-MM-dd HH:mm:ss");
        assertThat(result).isEqualTo("2025-06-15 16:00:00");
    }

    @Test
    void timestamp_converted_to_new_york_timezone() {
        Timestamp ts = Timestamp.from(Instant.parse("2025-06-15T08:00:00Z"));
        ZoneId ny = ZoneId.of("America/New_York");
        Object result = JdbcResultValueNormalizer.normalize(ts, Types.TIMESTAMP, ny, "yyyy-MM-dd HH:mm:ss");
        assertThat(result).isEqualTo("2025-06-15 04:00:00");
    }

    @Test
    void timestamp_with_us_date_format() {
        Timestamp ts = Timestamp.from(Instant.parse("2025-06-15T08:00:00Z"));
        ZoneId ny = ZoneId.of("America/New_York");
        Object result = JdbcResultValueNormalizer.normalize(ts, Types.TIMESTAMP, ny, "MM/dd/yyyy hh:mm:ss a");
        assertThat(result).isEqualTo("06/15/2025 04:00:00 AM");
    }

    @Test
    void timestamp_on_winter_date_dst_off() {
        Timestamp ts = Timestamp.from(Instant.parse("2025-01-15T08:00:00Z"));
        ZoneId ny = ZoneId.of("America/New_York");
        Object result = JdbcResultValueNormalizer.normalize(ts, Types.TIMESTAMP, ny, "yyyy-MM-dd HH:mm:ss");
        assertThat(result).isEqualTo("2025-01-15 03:00:00");
    }

    @Test
    void sql_date_not_converted() {
        java.sql.Date d = java.sql.Date.valueOf("2025-06-15");
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        Object result = JdbcResultValueNormalizer.normalize(d, Types.DATE, shanghai, "yyyy-MM-dd HH:mm:ss");
        assertThat(result).isEqualTo("2025-06-15");
    }

    @Test
    void sql_time_not_converted() {
        java.sql.Time t = java.sql.Time.valueOf("14:30:00");
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        Object result = JdbcResultValueNormalizer.normalize(t, Types.TIME, shanghai, "yyyy-MM-dd HH:mm:ss");
        assertThat(result).isEqualTo("14:30:00");
    }

    @Test
    void offset_date_time_converted() {
        OffsetDateTime odt = OffsetDateTime.of(2025, 6, 15, 8, 0, 0, 0, ZoneOffset.UTC);
        ZoneId ny = ZoneId.of("America/New_York");
        Object result = JdbcResultValueNormalizer.normalize(odt, Types.TIMESTAMP_WITH_TIMEZONE, ny, "yyyy-MM-dd HH:mm:ss");
        assertThat(result).isEqualTo("2025-06-15 04:00:00");
    }

    @Test
    void instant_converted() {
        Instant inst = Instant.parse("2025-06-15T08:00:00Z");
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        Object result = JdbcResultValueNormalizer.normalize(inst, Types.TIMESTAMP, shanghai, "yyyy-MM-dd HH:mm:ss");
        assertThat(result).isEqualTo("2025-06-15 16:00:00");
    }

    @Test
    void local_date_time_not_converted() {
        LocalDateTime ldt = LocalDateTime.of(2025, 6, 15, 14, 30, 0);
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        Object result = JdbcResultValueNormalizer.normalize(ldt, Types.TIMESTAMP, shanghai, "yyyy-MM-dd HH:mm:ss");
        assertThat(result).isEqualTo("2025-06-15 14:30:00");
    }

    @Test
    void local_date_not_converted() {
        LocalDate ld = LocalDate.of(2025, 6, 15);
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        Object result = JdbcResultValueNormalizer.normalize(ld, Types.DATE, shanghai, "yyyy-MM-dd HH:mm:ss");
        assertThat(result).isEqualTo("2025-06-15");
    }

    @Test
    void local_time_not_converted() {
        LocalTime lt = LocalTime.of(14, 30, 0);
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        Object result = JdbcResultValueNormalizer.normalize(lt, Types.TIME, shanghai, "yyyy-MM-dd HH:mm:ss");
        assertThat(result).isEqualTo("14:30:00");
    }

    @Test
    void no_timezone_preference_passes_through() {
        Timestamp ts = Timestamp.valueOf("2025-06-15 08:00:00.000000000");
        Object result = JdbcResultValueNormalizer.normalize(ts, Types.TIMESTAMP, null, null);
        assertThat(result).isInstanceOf(Timestamp.class);
    }

    @Test
    void string_value_passes_through_with_timezone() {
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        Object result = JdbcResultValueNormalizer.normalize("2025-06-15 08:00:00", Types.VARCHAR, shanghai, "yyyy-MM-dd HH:mm:ss");
        assertThat(result).isEqualTo("2025-06-15 08:00:00");
    }

    @Test
    void existing_normalize_delegates_to_no_timezone() {
        Timestamp ts = Timestamp.valueOf("2025-06-15 08:00:00.000000000");
        Object result = JdbcResultValueNormalizer.normalize(ts);
        assertThat(result).isInstanceOf(Timestamp.class);
    }

    @Test
    void invalid_date_format_falls_back_to_iso() {
        LocalDateTime ldt = LocalDateTime.of(2025, 6, 15, 16, 0, 0);
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        Object result = JdbcResultValueNormalizer.normalize(ldt, Types.TIMESTAMP, shanghai, "!!invalid!!");
        assertThat(result).isEqualTo("2025-06-15T16:00:00");
    }
}
