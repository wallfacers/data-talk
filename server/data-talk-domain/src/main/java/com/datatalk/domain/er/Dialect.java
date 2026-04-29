package com.datatalk.domain.er;

import java.util.Locale;
import java.util.Optional;

/** Supported ER dialects. Oracle and SQL Server remain explicitly unsupported in day one. */
public enum Dialect {
    MYSQL,
    POSTGRESQL,
    H2,
    SQLITE;

    public static Optional<Dialect> fromConnectionKind(String kind) {
        if (kind == null || kind.isBlank()) return Optional.empty();
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "mysql" -> Optional.of(MYSQL);
            case "postgresql", "postgres" -> Optional.of(POSTGRESQL);
            case "h2" -> Optional.of(H2);
            case "sqlite" -> Optional.of(SQLITE);
            default -> Optional.empty();
        };
    }
}
