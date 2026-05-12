package com.datatalk.domain.ingestion;

import java.util.Map;

public record PaginationSpec(PaginationType type, Map<String, Object> params,
                             int maxPages, TerminationHint terminationHint) {}
