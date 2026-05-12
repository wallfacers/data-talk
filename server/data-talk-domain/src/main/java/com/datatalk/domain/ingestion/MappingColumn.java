package com.datatalk.domain.ingestion;

import java.util.List;

public record MappingColumn(String sourcePath, String targetName, InferredType type,
                            boolean skip, List<String> sampleValues, boolean nullable) {}
