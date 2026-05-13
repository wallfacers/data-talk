package com.datatalk.application.ingestion.ddl;

import com.datatalk.domain.ingestion.InferredType;
import com.datatalk.domain.ingestion.MappingColumn;

import java.util.List;

public interface IngestionDdlAdapter {
    boolean supports(String connectionKind);
    String generateCreateTable(String schema, String table, List<MappingColumn> columns);
    String generateInsert(String schema, String table, List<MappingColumn> columns);
    /** Generates {@code DROP TABLE IF EXISTS [schema.]table} with dialect-specific identifier quoting. */
    String generateDropTable(String schema, String table);
    String sqlTypeFor(InferredType inferred);
}
