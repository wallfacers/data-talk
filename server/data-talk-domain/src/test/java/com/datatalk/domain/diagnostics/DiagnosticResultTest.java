package com.datatalk.domain.diagnostics;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticResultTest {

    @Test
    void ok_isOk() {
        var result = DiagnosticResult.ok("value");
        assertThat(result.isOk()).isTrue();
        assertThat(result.isUnsupported()).isFalse();
        assertThat(((DiagnosticResult.Ok<String>) result).value()).isEqualTo("value");
    }

    @Test
    void unsupported_isUnsupported() {
        var result = DiagnosticResult.<String>unsupported("not available");
        assertThat(result.isOk()).isFalse();
        assertThat(result.isUnsupported()).isTrue();
        assertThat(((DiagnosticResult.Unsupported<String>) result).reason()).isEqualTo("not available");
    }

    @Test
    void error_isError() {
        var result = DiagnosticResult.<String>error("SQL_ERROR", "syntax error");
        assertThat(result.isOk()).isFalse();
        assertThat(result.isUnsupported()).isFalse();
        assertThat(result).isInstanceOf(DiagnosticResult.DiagnosticError.class);
    }
}
