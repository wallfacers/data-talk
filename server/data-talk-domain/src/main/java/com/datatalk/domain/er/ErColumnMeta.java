package com.datatalk.domain.er;

/**
 * Column metadata for ER rendering.
 *
 * @param name            column name
 * @param type            dialect-specific SQL type fragment, e.g. "BIGINT", "VARCHAR(255)"
 * @param nullable        whether the column allows NULL
 * @param isPrimaryKey    part of primary key
 * @param isForeignKey    references another table
 * @param isAutoIncrement driver-reported auto-increment
 * @param defaultValue    default value as string, or null
 * @param comment         column comment, or null
 */
public record ErColumnMeta(
    String name,
    String type,
    boolean nullable,
    boolean isPrimaryKey,
    boolean isForeignKey,
    boolean isAutoIncrement,
    String defaultValue,
    String comment
) {}
