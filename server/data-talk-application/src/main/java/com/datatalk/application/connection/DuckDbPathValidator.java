package com.datatalk.application.connection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Validates and canonicalizes DuckDB file paths.
 * Ensures paths stay within the configured data root and don't escape to sensitive areas.
 */
@Service
public final class DuckDbPathValidator {

    private final Path dataRoot;

    public DuckDbPathValidator(
        @Value("${datatalk.duckdb.data-root:#{systemProperties['user.home']}/.datatalk/duckdb/}") String dataRootStr
    ) {
        this.dataRoot = Path.of(dataRootStr).normalize().toAbsolutePath();
    }

    /**
     * Validates and canonicalizes a DuckDB file path.
     * Returns normalized absolute path.
     *
     * @param userPath user-supplied path (relative or absolute)
     * @return normalized absolute path within data root
     * @throws IllegalArgumentException if path escapes data root or targets sensitive area
     */
    public Path validateAndCanonicalize(String userPath) {
        Path resolved = Path.of(userPath);
        if (!resolved.isAbsolute()) {
            resolved = dataRoot.resolve(resolved).normalize();
        } else {
            resolved = resolved.normalize();
        }
        // Reject symlinks that escape data root
        if (!resolved.startsWith(dataRoot)) {
            throw new IllegalArgumentException("Path escapes data root: " + userPath);
        }
        // Reject sensitive paths
        String normalized = resolved.toString();
        if (normalized.startsWith("/etc") || normalized.startsWith("/proc") ||
            normalized.startsWith("/sys") || normalized.contains(".ssh") ||
            normalized.contains(".gnupg")) {
            throw new IllegalArgumentException("Path in sensitive area: " + userPath);
        }
        return resolved;
    }

    public Path dataRoot() {
        return dataRoot;
    }
}
