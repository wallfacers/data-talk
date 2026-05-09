package com.datatalk.infra.diagnostics;

import com.datatalk.application.diagnostics.DiagnosticsProvider;
import com.datatalk.application.diagnostics.DiagnosticsProviderRegistry;
import com.datatalk.application.i18n.Translator;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class OceanBaseDiagnosticsProviderRegistrationTest {

    @Test
    void registryFindsProviderForOceanbaseKind() {
        var provider = new OceanBaseDiagnosticsProvider(translator());
        var registry = new DiagnosticsProviderRegistry(List.<DiagnosticsProvider>of(provider));

        assertThat(registry.find("oceanbase")).isPresent();
        assertThat(registry.find("oceanbase").get()).isSameAs(provider);
    }

    @Test
    void registryUsesCaseInsensitiveMatch() {
        var provider = new OceanBaseDiagnosticsProvider(translator());
        var registry = new DiagnosticsProviderRegistry(List.<DiagnosticsProvider>of(provider));

        assertThat(registry.find("OCEANBASE")).isPresent();
    }

    @Test
    void registryReturnsEmptyForUnknownKind() {
        var provider = new OceanBaseDiagnosticsProvider(translator());
        var registry = new DiagnosticsProviderRegistry(List.<DiagnosticsProvider>of(provider));

        assertThat(registry.find("postgresql")).isEmpty();
    }

    private static Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.dialect_unsupported.oceanbase.explain_real", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.oceanbase.index_hints", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.oceanbase.lock_info", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.oceanbase.pool_status", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.oceanbase.table_space", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.oceanbase.terminate_session", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.oceanbase.optimize_table", Locale.ENGLISH, "UNSUPPORTED");
        return new Translator(source);
    }
}
