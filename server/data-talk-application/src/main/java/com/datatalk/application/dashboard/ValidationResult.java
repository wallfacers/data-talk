package com.datatalk.application.dashboard;

import java.util.List;

public record ValidationResult(List<Error> errors) {
    public boolean ok() { return errors.isEmpty(); }

    public record Error(String path, String code, String message) {}
}
