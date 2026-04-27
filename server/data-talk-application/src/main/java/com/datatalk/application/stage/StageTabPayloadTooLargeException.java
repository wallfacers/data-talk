package com.datatalk.application.stage;

/**
 * Thrown when a stage tab payload exceeds the configured maximum size.
 */
public class StageTabPayloadTooLargeException extends RuntimeException {

    private final long actualBytes;
    private final long maxBytes;

    public StageTabPayloadTooLargeException(long actualBytes, long maxBytes) {
        super("Stage tab payload too large: " + actualBytes + " bytes (max " + maxBytes + ")");
        this.actualBytes = actualBytes;
        this.maxBytes = maxBytes;
    }

    public long actualBytes() { return actualBytes; }
    public long maxBytes() { return maxBytes; }
}
