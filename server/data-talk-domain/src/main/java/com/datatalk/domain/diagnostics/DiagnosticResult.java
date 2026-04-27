package com.datatalk.domain.diagnostics;

public sealed interface DiagnosticResult<T>
    permits DiagnosticResult.Ok, DiagnosticResult.Unsupported, DiagnosticResult.DiagnosticError {

    record Ok<T>(T value) implements DiagnosticResult<T> {}
    record Unsupported<T>(String reason) implements DiagnosticResult<T> {}
    record DiagnosticError<T>(String errorType, String message) implements DiagnosticResult<T> {}

    static <T> DiagnosticResult<T> ok(T value) { return new Ok<>(value); }
    static <T> DiagnosticResult<T> unsupported(String reason) { return new Unsupported<>(reason); }
    static <T> DiagnosticResult<T> error(String errorType, String message) { return new DiagnosticError<>(errorType, message); }

    default boolean isOk() { return this instanceof Ok; }
    default boolean isUnsupported() { return this instanceof Unsupported; }
}
