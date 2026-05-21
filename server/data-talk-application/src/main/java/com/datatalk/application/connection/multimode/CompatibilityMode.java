package com.datatalk.application.connection.multimode;

/** Compatibility mode for multi-mode database kinds (OceanBase, KingbaseES). */
public enum CompatibilityMode {
    MYSQL("mysql"),
    ORACLE("oracle"),
    PG("pg");

    private final String wireValue;

    CompatibilityMode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static CompatibilityMode of(String wire) {
        for (CompatibilityMode m : values()) {
            if (m.wireValue.equals(wire)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown compatibility mode: " + wire);
    }
}
