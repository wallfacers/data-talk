package com.datatalk.infra.diagnostics;

import com.datatalk.application.diagnostics.DiagnosticsProvider;
import com.datatalk.application.diagnostics.DiagnosticsProviderRegistry;
import com.datatalk.application.i18n.Translator;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class KingbaseDiagnosticsProviderRegistrationTest {

    @Test
    void kingbaseProviderIsRegistered() {
        var provider = new KingbaseDiagnosticsProvider(translator());
        var registry = new DiagnosticsProviderRegistry(List.<DiagnosticsProvider>of(provider));

        assertThat(registry.find("kingbase")).isPresent();
        assertThat(registry.find("kingbase").get()).isSameAs(provider);
    }

    private static Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.dialect_unsupported.kingbase.explain_real", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.kingbase.index_hints", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.kingbase.lock_info", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.kingbase.pool_status", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.kingbase.table_space", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.kingbase.terminate_session", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.kingbase.optimize_table", Locale.ENGLISH, "UNSUPPORTED");
        return new Translator(source);
    }
}
