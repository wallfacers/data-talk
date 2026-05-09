package com.datatalk.infra.diagnostics;

import com.datatalk.application.diagnostics.DiagnosticsProvider;
import com.datatalk.application.diagnostics.DiagnosticsProviderRegistry;
import com.datatalk.application.i18n.Translator;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class DamengDiagnosticsProviderRegistrationTest {

    @Test
    void damengProviderIsRegistered() {
        var provider = new DamengDiagnosticsProvider(translator());
        var registry = new DiagnosticsProviderRegistry(List.<DiagnosticsProvider>of(provider));

        assertThat(registry.find("dameng")).isPresent();
        assertThat(registry.find("dameng").get()).isSameAs(provider);
    }

    private static Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.dialect_unsupported.dameng.explain_real", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.dameng.index_hints", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.dameng.lock_info", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.dameng.pool_status", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.dameng.table_space", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.dameng.terminate_session", Locale.ENGLISH, "UNSUPPORTED");
        source.addMessage("diagnostics.dialect_unsupported.dameng.optimize_table", Locale.ENGLISH, "UNSUPPORTED");
        return new Translator(source);
    }
}
