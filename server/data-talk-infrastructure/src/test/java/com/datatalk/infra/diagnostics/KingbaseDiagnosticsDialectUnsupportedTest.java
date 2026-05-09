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

class KingbaseDiagnosticsDialectUnsupportedTest {

    private Translator translator;
    private KingbaseDiagnosticsProvider provider;

    @BeforeEach
    void setUp() {
        translator = mock(Translator.class);
        when(translator.getOrDefault(anyString(), anyString()))
            .thenAnswer(inv -> "[" + inv.getArgument(0).toString() + "]");
        when(translator.get(anyString()))
            .thenAnswer(inv -> "[" + inv.getArgument(0).toString() + "]");
        provider = new KingbaseDiagnosticsProvider(translator);
    }

    @Test
    void supportedDriverTypesIsOnlyKingbase() {
        assertThat(provider.supportedDriverTypes()).containsExactly("kingbase");
    }

    @Test
    void supportedCapabilitiesIsEmpty() {
        // Day-1: no capabilities supported — all return dialect_unsupported
        assertThat(provider.supportedCapabilities()).isEmpty();
    }

    @Test
    void explainReturnsUnsupported() {
        var result = provider.explain("SELECT 1", kingbaseRecord(), "pw", "db", null);
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void indexHintsReturnsUnsupported() {
        var result = provider.indexHints("SELECT 1", null, kingbaseRecord(), "pw");
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void lockInfoReturnsUnsupported() {
        var result = provider.lockInfo(kingbaseRecord(), "pw", "db");
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void poolStatusReturnsUnsupported() {
        var result = provider.poolStatus(kingbaseRecord(), "pw");
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void tableSpaceInfoReturnsUnsupported() {
        var result = provider.tableSpaceInfo(kingbaseRecord(), "pw", "db", List.of());
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void terminateSessionReturnsUnsupported() {
        var result = provider.terminateSession(kingbaseRecord(), "pw", "42", "db");
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void terminateSessionPreviewReturnsUnsupported() {
        var result = provider.terminateSessionPreview(kingbaseRecord(), "pw", "42", "db");
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void optimizeTableReturnsUnsupported() {
        var result = provider.optimizeTable(kingbaseRecord(), "pw", "T1", "PUBLIC", "db");
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void optimizeTablePreviewReturnsUnsupported() {
        var result = provider.optimizeTablePreview(kingbaseRecord(), "pw", "T1", "PUBLIC", "db");
        assertThat(result.isUnsupported()).isTrue();
    }

    private static ConnectionRecord kingbaseRecord() {
        return new ConnectionRecord(
            "id", "n", "kingbase", "h", 54321, "testdb", "SYSTEM",
            new byte[]{}, "", 0L, 10, null, null, null, 1, true, null, false,
            "pg", null, null);
    }
}
