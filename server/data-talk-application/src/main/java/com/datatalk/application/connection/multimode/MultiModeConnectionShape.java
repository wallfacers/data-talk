package com.datatalk.application.connection.multimode;

/**
 * Kind-neutral validation logic for multi-mode connection kinds.
 * v1 — designed for OceanBase, consumed later by KingbaseES.
 */
public final class MultiModeConnectionShape {

    private MultiModeConnectionShape() {}

    /**
     * Validate that (kind, mode) is a legal combination.
     */
    public static void validateModeForKind(String kind, CompatibilityMode mode) {
        switch (kind) {
            case "oceanbase" -> {
                if (mode != CompatibilityMode.MYSQL && mode != CompatibilityMode.ORACLE) {
                    throw new IllegalArgumentException(
                        "oceanbase requires compatibility_mode in {mysql,oracle}");
                }
            }
            case "kingbase" -> {
                if (mode != CompatibilityMode.PG && mode != CompatibilityMode.ORACLE) {
                    throw new IllegalArgumentException(
                        "kingbase requires compatibility_mode in {pg,oracle}");
                }
            }
            default -> {
                if (mode != null) {
                    throw new IllegalArgumentException(
                        "kind '" + kind + "' must not specify compatibility_mode");
                }
            }
        }
    }

    /**
     * Day-1 first-class mode predicate.
     * Returns false => caller must return dialect_unsupported.
     */
    public static boolean isDay1FirstClassMode(String kind, CompatibilityMode mode) {
        return switch (kind) {
            case "oceanbase" -> mode == CompatibilityMode.MYSQL;
            case "kingbase" -> mode == CompatibilityMode.PG;
            default -> mode == null;
        };
    }
}
