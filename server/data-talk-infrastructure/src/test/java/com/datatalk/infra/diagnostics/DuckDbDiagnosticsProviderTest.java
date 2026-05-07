package com.datatalk.infra.diagnostics;

import com.datatalk.application.diagnostics.DiagnosticsProviderRegistry;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class DuckDbDiagnosticsProviderTest {

    private DuckDbDiagnosticsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DuckDbDiagnosticsProvider(translator());
    }

    @Test
    void supportedDriverTypes_containsDuckdb() {
        assertThat(provider.supportedDriverTypes()).containsExactly("duckdb");
    }

    @Test
    void supportedCapabilities_isEmptyToForceStructuredUnsupported() {
        assertThat(provider.supportedCapabilities()).isEmpty();
    }

    @Test
    void diagnosticsAndMutationsReturnStructuredUnsupported() {
        assertUnsupported(provider.explain("SELECT 1", null, null, null, null), "EXPLAIN not supported for dialect: duckdb");
        assertUnsupported(provider.indexHints("SELECT 1", null, null, null), "Index hints not supported for dialect: duckdb");
        assertUnsupported(provider.lockInfo(null, null, null), "duckdb lock info is not yet supported");
        assertUnsupported(provider.poolStatus(null, null), "duckdb connection pool info is not yet supported");
        assertUnsupported(provider.tableSpaceInfo(null, null, null, List.of()), "duckdb table space info is not yet supported");
        assertUnsupported(provider.terminateSessionPreview(null, null, "1", null), "duckdb session termination is not yet supported");
        assertUnsupported(provider.terminateSession(null, null, "1", null), "duckdb session termination is not yet supported");
        assertUnsupported(provider.optimizeTablePreview(null, null, "users", null, null), "duckdb table optimization is not yet supported");
        assertUnsupported(provider.optimizeTable(null, null, "users", null, null), "duckdb table optimization is not yet supported");
    }

    @Test
    void registryFindsProvider_for_duckdb_kind() {
        var registry = new DiagnosticsProviderRegistry(List.of(provider));
        assertThat(registry.find("duckdb")).isPresent();
        assertThat(registry.find("duckdb").get()).isSameAs(provider);
    }

    private static void assertUnsupported(DiagnosticResult<?> result, String reason) {
        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<?>) result).reason()).isEqualTo(reason);
    }

    private Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.explain_unsupported", Locale.ENGLISH, "EXPLAIN not supported for dialect: {0}");
        source.addMessage("diagnostics.index_hints_unsupported", Locale.ENGLISH, "Index hints not supported for dialect: {0}");
        source.addMessage("diagnostics.lock_not_supported", Locale.ENGLISH, "{0} lock info is not yet supported");
        source.addMessage("diagnostics.pool_not_supported", Locale.ENGLISH, "{0} connection pool info is not yet supported");
        source.addMessage("diagnostics.tablespace_not_supported", Locale.ENGLISH, "{0} table space info is not yet supported");
        source.addMessage("diagnostics.terminate_not_supported", Locale.ENGLISH, "{0} session termination is not yet supported");
        source.addMessage("diagnostics.optimize_not_supported", Locale.ENGLISH, "{0} table optimization is not yet supported");
        return new Translator(source);
    }
}
