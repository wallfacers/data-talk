package com.datatalk.application.script;

import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnTypeMapperTest {

    @Test
    void map_varcharTypes_returnVarchar255() {
        assertThat(ColumnTypeMapper.map(Types.VARCHAR, 0, 0)).isEqualTo("VARCHAR(255)");
        assertThat(ColumnTypeMapper.map(Types.CHAR, 0, 0)).isEqualTo("VARCHAR(255)");
        assertThat(ColumnTypeMapper.map(Types.CLOB, 0, 0)).isEqualTo("VARCHAR(255)");
        assertThat(ColumnTypeMapper.map(Types.LONGVARCHAR, 0, 0)).isEqualTo("VARCHAR(255)");
        assertThat(ColumnTypeMapper.map(Types.NVARCHAR, 0, 0)).isEqualTo("VARCHAR(255)");
        assertThat(ColumnTypeMapper.map(Types.NCHAR, 0, 0)).isEqualTo("VARCHAR(255)");
        assertThat(ColumnTypeMapper.map(Types.NCLOB, 0, 0)).isEqualTo("VARCHAR(255)");
    }

    @Test
    void map_integerTypes_returnBigint() {
        assertThat(ColumnTypeMapper.map(Types.INTEGER, 0, 0)).isEqualTo("BIGINT");
        assertThat(ColumnTypeMapper.map(Types.BIGINT, 0, 0)).isEqualTo("BIGINT");
        assertThat(ColumnTypeMapper.map(Types.SMALLINT, 0, 0)).isEqualTo("BIGINT");
        assertThat(ColumnTypeMapper.map(Types.TINYINT, 0, 0)).isEqualTo("BIGINT");
    }

    @Test
    void map_floatDouble_returnDouble() {
        assertThat(ColumnTypeMapper.map(Types.FLOAT, 0, 0)).isEqualTo("DOUBLE");
        assertThat(ColumnTypeMapper.map(Types.DOUBLE, 0, 0)).isEqualTo("DOUBLE");
        assertThat(ColumnTypeMapper.map(Types.REAL, 0, 0)).isEqualTo("DOUBLE");
    }

    @Test
    void map_booleanBit_returnBoolean() {
        assertThat(ColumnTypeMapper.map(Types.BOOLEAN, 0, 0)).isEqualTo("BOOLEAN");
        assertThat(ColumnTypeMapper.map(Types.BIT, 0, 0)).isEqualTo("BOOLEAN");
    }

    @Test
    void map_timestampTypes_returnTimestamp() {
        assertThat(ColumnTypeMapper.map(Types.TIMESTAMP, 0, 0)).isEqualTo("TIMESTAMP");
        assertThat(ColumnTypeMapper.map(Types.TIMESTAMP_WITH_TIMEZONE, 0, 0)).isEqualTo("TIMESTAMP");
    }

    @Test
    void map_date_returnDate() {
        assertThat(ColumnTypeMapper.map(Types.DATE, 0, 0)).isEqualTo("DATE");
    }

    @Test
    void map_time_returnTime() {
        assertThat(ColumnTypeMapper.map(Types.TIME, 0, 0)).isEqualTo("TIME");
        assertThat(ColumnTypeMapper.map(Types.TIME_WITH_TIMEZONE, 0, 0)).isEqualTo("TIME");
    }

    @Test
    void map_numericWithScale_returnDecimal() {
        assertThat(ColumnTypeMapper.map(Types.NUMERIC, 10, 2)).isEqualTo("DECIMAL(10,2)");
        assertThat(ColumnTypeMapper.map(Types.DECIMAL, 20, 5)).isEqualTo("DECIMAL(20,5)");
    }

    @Test
    void map_numericNoScale_returnBigint() {
        assertThat(ColumnTypeMapper.map(Types.NUMERIC, 10, 0)).isEqualTo("DECIMAL(10,0)");
    }

    @Test
    void map_numericNoPrecision_returnText() {
        assertThat(ColumnTypeMapper.map(Types.NUMERIC, 0, 0)).isEqualTo("TEXT");
    }

    @Test
    void map_unknownType_returnText() {
        assertThat(ColumnTypeMapper.map(Types.OTHER, 0, 0)).isEqualTo("TEXT");
        assertThat(ColumnTypeMapper.map(Types.BLOB, 0, 0)).isEqualTo("TEXT");
    }

    @Test
    void mapWithOverride_noOverride_returnsMapped() {
        assertThat(ColumnTypeMapper.mapWithOverride(Types.INTEGER, 0, 0, "id", null))
            .isEqualTo("BIGINT");
    }

    @Test
    void mapWithOverride_withOverride_returnsOverride() {
        Map<String, String> overrides = Map.of("price", "DECIMAL(10,2)");
        assertThat(ColumnTypeMapper.mapWithOverride(Types.DOUBLE, 0, 0, "price", overrides))
            .isEqualTo("DECIMAL(10,2)");
    }

    @Test
    void mapWithOverride_differentColumn_returnsMapped() {
        Map<String, String> overrides = Map.of("price", "DECIMAL(10,2)");
        assertThat(ColumnTypeMapper.mapWithOverride(Types.INTEGER, 0, 0, "qty", overrides))
            .isEqualTo("BIGINT");
    }
}
