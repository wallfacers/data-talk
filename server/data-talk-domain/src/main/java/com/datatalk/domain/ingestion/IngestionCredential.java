package com.datatalk.domain.ingestion;

import java.util.Map;

public record IngestionCredential(String id, String name, AuthScheme scheme,
                                  Map<String, String> configNonSecret, String vaultId,
                                  long createdAt, long updatedAt) {}
