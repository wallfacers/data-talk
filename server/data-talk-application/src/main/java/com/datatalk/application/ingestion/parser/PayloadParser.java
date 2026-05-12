package com.datatalk.application.ingestion.parser;

import com.datatalk.domain.ingestion.IngestionMapping;

import java.nio.file.Path;

/**
 * Infers column schema (names, types, sample values) from a payload file.
 */
public interface PayloadParser {

    /**
     * Read the payload file, sample up to {@code sampleSize} records,
     * and return an inferred column mapping.
     */
    IngestionMapping infer(Path payloadFile, int sampleSize);

    /**
     * Open a streaming row iterator over the payload file.
     * The caller must close the returned {@link RowStream}.
     */
    RowStream openRowStream(Path payloadFile);
}
