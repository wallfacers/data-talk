package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DamengDiagnosticsDialectUnsupportedTest {

    private Translator translator;
    private DamengDiagnosticsProvider provider;

    @BeforeEach
    void setUp() {
        translator = mock(Translator.class);
        when(translator.getOrDefault(anyString(), anyString()))
            .thenAnswer(inv -> "[" + inv.getArgument(0).toString() + "]");
        when(translator.get(anyString()))
            .thenAnswer(inv -> "[" + inv.getArgument(0).toString() + "]");
        provider = new DamengDiagnosticsProvider(translator);
    }

    @Test
    void supportedDriverTypesIsOnlyDameng() {
        assertThat(provider.supportedDriverTypes()).containsExactly("dameng");
    }

    @Test
    void supportedCapabilitiesIncludesAllSeven() {
        // Day-1: all capabilities are reported as supported so DiagnosticsService
        // routes to our provider methods returning dameng-specific dialect_unsupported.
        assertThat(provider.supportedCapabilities()).hasSize(7);
    }

    @Test
    void explainReturnsUnsupported() {
        var result = provider.explain("SELECT 1", damengRecord(), "pw", "db", null);
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void indexHintsReturnsUnsupported() {
        var result = provider.indexHints("SELECT 1", null, damengRecord(), "pw");
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void lockInfoReturnsUnsupported() {
        var result = provider.lockInfo(damengRecord(), "pw", "db");
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void poolStatusReturnsUnsupported() {
        var result = provider.poolStatus(damengRecord(), "pw");
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void tableSpaceInfoReturnsUnsupported() {
        var result = provider.tableSpaceInfo(damengRecord(), "pw", "db", List.of());
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void terminateSessionReturnsUnsupported() {
        var result = provider.terminateSession(damengRecord(), "pw", "42", "db");
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void terminateSessionPreviewReturnsUnsupported() {
        var result = provider.terminateSessionPreview(damengRecord(), "pw", "42", "db");
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void optimizeTableReturnsUnsupported() {
        var result = provider.optimizeTable(damengRecord(), "pw", "T1", "SCOTT", "db");
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void optimizeTablePreviewReturnsUnsupported() {
        var result = provider.optimizeTablePreview(damengRecord(), "pw", "T1", "SCOTT", "db");
        assertThat(result.isUnsupported()).isTrue();
    }

    private static ConnectionRecord damengRecord() {
        return new ConnectionRecord(
            "id", "n", "dameng", "h", 5236, "SCOTT", "SYSDBA",
            new byte[]{}, "", 0L, 10, null, null, null, 1, true, null, false, null, null, null);
    }
}
