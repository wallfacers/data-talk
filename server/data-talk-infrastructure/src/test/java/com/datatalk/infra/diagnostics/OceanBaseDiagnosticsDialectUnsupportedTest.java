package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class OceanBaseDiagnosticsDialectUnsupportedTest {

    private OceanBaseDiagnosticsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OceanBaseDiagnosticsProvider(translator());
    }

    @Test
    void supportedDriverTypesIsOnlyOceanbase() {
        assertThat(provider.supportedDriverTypes()).containsExactly("oceanbase");
    }

    @Test
    void supportedCapabilitiesIsEmpty() {
        assertThat(provider.supportedCapabilities()).isEmpty();
    }

    @Test
    void explainReturnsUnsupported() {
        var result = provider.explain("SELECT 1", oceanbaseRecord(), "pw", "db", null);
        assertUnsupported(result, "[diagnostics.dialect_unsupported.oceanbase.explain_real]");
    }

    @Test
    void indexHintsReturnsUnsupported() {
        var result = provider.indexHints("SELECT 1", null, oceanbaseRecord(), "pw");
        assertUnsupported(result, "[diagnostics.dialect_unsupported.oceanbase.index_hints]");
    }

    @Test
    void lockInfoReturnsUnsupported() {
        var result = provider.lockInfo(oceanbaseRecord(), "pw", "db");
        assertUnsupported(result, "[diagnostics.dialect_unsupported.oceanbase.lock_info]");
    }

    @Test
    void poolStatusReturnsUnsupported() {
        var result = provider.poolStatus(oceanbaseRecord(), "pw");
        assertUnsupported(result, "[diagnostics.dialect_unsupported.oceanbase.pool_status]");
    }

    @Test
    void tableSpaceInfoReturnsUnsupported() {
        var result = provider.tableSpaceInfo(oceanbaseRecord(), "pw", "db", List.of());
        assertUnsupported(result, "[diagnostics.dialect_unsupported.oceanbase.table_space]");
    }

    @Test
    void terminateSessionPreviewReturnsUnsupported() {
        var result = provider.terminateSessionPreview(oceanbaseRecord(), "pw", "1", "db");
        assertUnsupported(result, "[diagnostics.dialect_unsupported.oceanbase.terminate_session]");
    }

    @Test
    void terminateSessionReturnsUnsupported() {
        var result = provider.terminateSession(oceanbaseRecord(), "pw", "1", "db");
        assertUnsupported(result, "[diagnostics.dialect_unsupported.oceanbase.terminate_session]");
    }

    @Test
    void optimizeTablePreviewReturnsUnsupported() {
        var result = provider.optimizeTablePreview(oceanbaseRecord(), "pw", "t", null, "db");
        assertUnsupported(result, "[diagnostics.dialect_unsupported.oceanbase.optimize_table]");
    }

    @Test
    void optimizeTableReturnsUnsupported() {
        var result = provider.optimizeTable(oceanbaseRecord(), "pw", "t", null, "db");
        assertUnsupported(result, "[diagnostics.dialect_unsupported.oceanbase.optimize_table]");
    }

    private static void assertUnsupported(DiagnosticResult<?> result, String expectedReason) {
        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<?>) result).reason()).isEqualTo(expectedReason);
    }

    private static ConnectionRecord oceanbaseRecord() {
        return new ConnectionRecord(
            "id", "n", "oceanbase", "h", 2881, "db", "root",
            new byte[]{}, "", 0L, 10, null, null, null, 1, true, null,
            false, "mysql", "sys", null);
    }

    private static Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.dialect_unsupported.oceanbase.explain_real", Locale.ENGLISH,
            "[diagnostics.dialect_unsupported.oceanbase.explain_real]");
        source.addMessage("diagnostics.dialect_unsupported.oceanbase.index_hints", Locale.ENGLISH,
            "[diagnostics.dialect_unsupported.oceanbase.index_hints]");
        source.addMessage("diagnostics.dialect_unsupported.oceanbase.lock_info", Locale.ENGLISH,
            "[diagnostics.dialect_unsupported.oceanbase.lock_info]");
        source.addMessage("diagnostics.dialect_unsupported.oceanbase.pool_status", Locale.ENGLISH,
            "[diagnostics.dialect_unsupported.oceanbase.pool_status]");
        source.addMessage("diagnostics.dialect_unsupported.oceanbase.table_space", Locale.ENGLISH,
            "[diagnostics.dialect_unsupported.oceanbase.table_space]");
        source.addMessage("diagnostics.dialect_unsupported.oceanbase.terminate_session", Locale.ENGLISH,
            "[diagnostics.dialect_unsupported.oceanbase.terminate_session]");
        source.addMessage("diagnostics.dialect_unsupported.oceanbase.optimize_table", Locale.ENGLISH,
            "[diagnostics.dialect_unsupported.oceanbase.optimize_table]");
        return new Translator(source);
    }
}
