package com.datatalk.application.script;

import java.sql.Types;
import java.util.Map;

/**
 * Maps JDBC column types (from {@link java.sql.ResultSetMetaData}) to DDL types for CREATE TABLE.
 */
public final class ColumnTypeMapper {

    private ColumnTypeMapper() {}

    public static String map(int jdbcType, int precision, int scale) {
        return switch (jdbcType) {
            case Types.VARCHAR, Types.CHAR, Types.CLOB,
                 Types.LONGVARCHAR, Types.LONGNVARCHAR,
                 Types.NVARCHAR, Types.NCHAR, Types.NCLOB -> "VARCHAR(255)";
            case Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT -> "BIGINT";
            case Types.FLOAT, Types.DOUBLE, Types.REAL -> "DOUBLE";
            case Types.BOOLEAN, Types.BIT -> "BOOLEAN";
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "TIMESTAMP";
            case Types.DATE -> "DATE";
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> "TIME";
            case Types.NUMERIC, Types.DECIMAL -> {
                if (precision > 0 && scale >= 0) {
                    yield "DECIMAL(" + precision + "," + scale + ")";
                } else if (precision > 0) {
                    yield "BIGINT";
                } else {
                    yield "TEXT";
                }
            }
            default -> "TEXT";
        };
    }

    public static String mapWithOverride(int jdbcType, int precision, int scale,
                                          String columnName, Map<String, String> overrides) {
        if (overrides != null && overrides.containsKey(columnName)) {
            return overrides.get(columnName);
        }
        return map(jdbcType, precision, scale);
    }
}
