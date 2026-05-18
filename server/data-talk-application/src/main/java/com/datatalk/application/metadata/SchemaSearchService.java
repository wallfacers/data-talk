package com.datatalk.application.metadata;

import java.util.List;

public interface SchemaSearchService {
    SchemaSearchResult search(SchemaSearchRequest request);

    record SchemaSearchRequest(
        String connectionId,
        String keyword,
        String database,
        String schema,
        int limit
    ) {}

    record SchemaSearchResult(
        List<TableMatch> candidates,
        int totalCandidates,
        boolean truncated,
        String hint
    ) {}

    record TableMatch(
        String table,
        String schema,
        String database,
        int score,
        List<String> matchedOn,
        String commentSnippet
    ) {}
}
