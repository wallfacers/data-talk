package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class OracleDiagnosticsProviderTest {

    private OracleDiagnosticsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OracleDiagnosticsProvider(translator());
    }

    @Test
    void supportedDriverTypes_containsOracle() {
        assertThat(provider.supportedDriverTypes()).containsExactly("oracle");
    }

    @Test
    void supportedCapabilities_onlyExplainAndIndexHints() {
        assertThat(provider.supportedCapabilities())
            .containsExactlyInAnyOrder(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS);
    }

    @Test
    void diagnosticsAndMutationsAreExplicitlyUnsupported() {
        assertUnsupported(provider.lockInfo(null, null, null));
        assertUnsupported(provider.poolStatus(null, null));
        assertUnsupported(provider.tableSpaceInfo(null, null, null, List.of()));
        assertUnsupported(provider.terminateSessionPreview(null, null, "1", null));
        assertUnsupported(provider.terminateSession(null, null, "1", null));
        assertUnsupported(provider.optimizeTablePreview(null, null, "users", null, null));
        assertUnsupported(provider.optimizeTable(null, null, "users", null, null));
    }

    private static void assertUnsupported(DiagnosticResult<?> result) {
        assertThat(result).isInstanceOf(DiagnosticResult.Unsupported.class);
        assertThat(((DiagnosticResult.Unsupported<?>) result).reason()).contains("Oracle diagnostics not yet available");
    }

    private Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.coming_soon", Locale.ENGLISH, "Coming in a future release");
        source.addMessage("diagnostics.lock.unsupported.oracle", Locale.ENGLISH, "Oracle diagnostics not yet available");
        source.addMessage("diagnostics.pool.unsupported.oracle", Locale.ENGLISH, "Oracle diagnostics not yet available");
        source.addMessage("diagnostics.space.unsupported.oracle", Locale.ENGLISH, "Oracle diagnostics not yet available");
        source.addMessage("diagnostics.terminate.unsupported.oracle", Locale.ENGLISH, "Oracle diagnostics not yet available");
        source.addMessage("diagnostics.optimize.unsupported.oracle", Locale.ENGLISH, "Oracle diagnostics not yet available");
        return new Translator(source);
    }
}
