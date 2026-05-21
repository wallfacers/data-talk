package com.datatalk.application.stage;

/**
 * Lightweight value object carrying just the fields needed for the
 * AI agent prompt snapshot. Avoids sending full {@link com.datatalk.domain.stage.StageTab}
 * metadata into the prompt to save tokens.
 */
public record StageTabDigest(String tabId, String type, String title, String databaseName, String schemaName) {}
