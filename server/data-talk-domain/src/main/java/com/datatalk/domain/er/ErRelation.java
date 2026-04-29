package com.datatalk.domain.er;

/**
 * One directed FK or virtual relation in an ER graph.
 *
 * @param sourceTable  table holding the FK column
 * @param sourceColumn FK column name
 * @param targetTable  referenced table
 * @param targetColumn referenced column
 * @param relationType "one_to_many" | "one_to_one" | "many_to_one" | "many_to_many"
 * @param source       "schema_fk" for JDBC FKs or "virtual" for annotations
 */
public record ErRelation(
    String sourceTable,
    String sourceColumn,
    String targetTable,
    String targetColumn,
    String relationType,
    String source
) {}
