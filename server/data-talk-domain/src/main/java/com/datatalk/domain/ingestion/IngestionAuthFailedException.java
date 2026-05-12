package com.datatalk.domain.ingestion;

/**
 * Raised when an upstream HTTP fetch returns 401 Unauthorized. The action
 * handler maps this to {@code errorCode: INGESTION_AUTH_FAILED} so callers
 * can distinguish credential failures from generic fetch errors.
 */
public class IngestionAuthFailedException extends RuntimeException {
    public IngestionAuthFailedException(String message) {
        super(message);
    }
}
