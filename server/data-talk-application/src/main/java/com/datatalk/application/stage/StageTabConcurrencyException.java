package com.datatalk.application.stage;

/**
 * Thrown when a stage tab write detects an optimistic concurrency conflict.
 */
public class StageTabConcurrencyException extends RuntimeException {

    private final String tabId;
    private final int expectedVersion;
    private final int actualVersion;

    public StageTabConcurrencyException(String tabId, int expectedVersion, int actualVersion) {
        super("Optimistic concurrency conflict for stage tab " + tabId +
              ": expected version " + expectedVersion + " but found " + actualVersion);
        this.tabId = tabId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public String tabId() {
        return tabId;
    }

    public int expectedVersion() {
        return expectedVersion;
    }

    public int actualVersion() {
        return actualVersion;
    }
}
