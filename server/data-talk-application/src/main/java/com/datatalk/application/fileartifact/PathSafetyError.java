package com.datatalk.application.fileartifact;

/**
 * Wire-stable reasons a requested artifact path is rejected.
 */
public enum PathSafetyError {
    PATH_OUTSIDE_SESSION_DIR("path_outside_session_dir"),
    PATH_NOT_FOUND("path_not_found"),
    PATH_IS_DIRECTORY("path_is_directory"),
    PATH_IS_SYSTEM("path_is_system"),
    PATH_CONTAINS_SYMLINK("path_contains_symlink"),
    PATH_TOCTOU_RACE("path_toctou_race");

    private final String wire;

    PathSafetyError(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }
}
