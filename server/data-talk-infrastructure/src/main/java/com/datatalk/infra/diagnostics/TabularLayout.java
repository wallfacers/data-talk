package com.datatalk.infra.diagnostics;

/**
 * Describes how to map a JDBC tabular EXPLAIN result (one row = one ExplainNode)
 * into ExplainNode trees. Each Provider builds a TabularLayout for its dialect.
 *
 * - idCol      : node identifier column (may include ASCII tree prefix like "└─")
 * - parentCol  : optional parent-id column (e.g. SQLite); null = infer tree from idCol prefix
 * - operatorPattern : regex with one capture group extracting the operator name from idCol
 * - rowsCol    : estimated row count column (nullable)
 * - objectCol  : table/object column (nullable)
 * - infoCol    : operator info column (nullable)
 */
public record TabularLayout(
    String idCol,
    String parentCol,
    String operatorPattern,
    String rowsCol,
    String objectCol,
    String infoCol
) {}