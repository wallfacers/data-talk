package com.datatalk.infra.stage;

/**
 * Thrown when an upsert payload operation detects an optimistic concurrency conflict:
 * the expected payload version does not match the current version in the database.
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

    public String tabId() { return tabId; }
    public int expectedVersion() { return expectedVersion; }
    public int actualVersion() { return actualVersion; }
}
