package com.datatalk.domain.er;

import java.util.Locale;
import java.util.Optional;

/**
 * Supported ER dialects. MariaDB uses MySQL-compatible ER behavior.
 * Oracle and SQL Server are registered but remain explicitly unsupported in day-1 ER.
 */
public enum Dialect {
    MYSQL,
    POSTGRESQL,
    H2,
    SQLITE,
    MARIADB,
    ORACLE,
    SQLSERVER;

    public static Optional<Dialect> fromConnectionKind(String kind) {
        if (kind == null || kind.isBlank()) return Optional.empty();
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "mysql" -> Optional.of(MYSQL);
            case "mariadb" -> Optional.of(MARIADB);
            case "postgresql", "postgres" -> Optional.of(POSTGRESQL);
            case "h2" -> Optional.of(H2);
            case "sqlite" -> Optional.of(SQLITE);
            case "oracle" -> Optional.of(ORACLE);
            case "sqlserver" -> Optional.of(SQLSERVER);
            default -> Optional.empty();
        };
    }
}
