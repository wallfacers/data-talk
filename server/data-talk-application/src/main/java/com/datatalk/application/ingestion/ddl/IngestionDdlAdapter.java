package com.datatalk.application.ingestion.ddl;

import com.datatalk.domain.ingestion.InferredType;
import com.datatalk.domain.ingestion.MappingColumn;

import java.util.List;

public interface IngestionDdlAdapter {
    boolean supports(String connectionKind);
    String generateCreateTable(String schema, String table, List<MappingColumn> columns);
    String generateInsert(String schema, String table, List<MappingColumn> columns);
    String sqlTypeFor(InferredType inferred);
}
