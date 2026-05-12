package com.datatalk.domain.ingestion;

import java.util.List;

public record IngestionMapping(String mappingId, List<MappingColumn> columns) {}
