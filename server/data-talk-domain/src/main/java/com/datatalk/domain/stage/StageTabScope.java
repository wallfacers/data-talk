package com.datatalk.domain.stage;

/**
 * Scope of a stage tab.
 * WORKSPACE tabs persist across sessions; SESSION tabs are bound to a single session.
 */
public enum StageTabScope {
    WORKSPACE("workspace"),
    SESSION("session");

    private final String wire;

    StageTabScope(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static StageTabScope fromWire(String wire) {
        for (StageTabScope scope : values()) {
            if (scope.wire.equals(wire)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Unknown StageTabScope: " + wire);
    }
}
