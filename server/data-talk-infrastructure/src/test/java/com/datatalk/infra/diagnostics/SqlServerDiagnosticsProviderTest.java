package com.datatalk.infra.diagnostics;

import com.datatalk.application.diagnostics.DiagnosticsProviderRegistry;
import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class SqlServerDiagnosticsProviderTest {

    private final SqlServerDiagnosticsProvider provider = new SqlServerDiagnosticsProvider(translator());

    @Test
    void supportedDriverTypes_contains_sqlserver() {
        assertThat(provider.supportedDriverTypes()).containsExactly("sqlserver");
    }

    @Test
    void supportedCapabilities_isEmpty_for_day1() {
        assertThat(provider.supportedCapabilities()).isEmpty();
    }

    @Test
    void allCapabilitiesReturn_unsupported() {
        var conn = new com.datatalk.application.persistence.ConnectionRecord(
            "c1", "test", "sqlserver", "localhost", 1433,
            "mydb", "sa", new byte[0], null, 0L, 5000, null, null,
            null, 1, true, null);

        assertThat(provider.explain("SELECT 1", conn, "pw", "mydb", "dbo").isUnsupported()).isTrue();
        assertThat(provider.indexHints("SELECT 1", null, conn, "pw").isUnsupported()).isTrue();
        assertThat(provider.lockInfo(conn, "pw", "mydb").isUnsupported()).isTrue();
        assertThat(provider.poolStatus(conn, "pw").isUnsupported()).isTrue();
        assertThat(provider.tableSpaceInfo(conn, "pw", "mydb", java.util.List.of("t1")).isUnsupported()).isTrue();
        assertThat(provider.terminateSessionPreview(conn, "pw", "53", "mydb").isUnsupported()).isTrue();
        assertThat(provider.terminateSession(conn, "pw", "53", "mydb").isUnsupported()).isTrue();
        assertThat(provider.optimizeTablePreview(conn, "pw", "t1", "dbo", "mydb").isUnsupported()).isTrue();
        assertThat(provider.optimizeTable(conn, "pw", "t1", "dbo", "mydb").isUnsupported()).isTrue();
    }

    @Test
    void registryFindsProvider_for_sqlserver_kind() {
        var registry = new DiagnosticsProviderRegistry(java.util.List.of(provider));
        assertThat(registry.find("sqlserver")).isPresent();
        assertThat(registry.find("sqlserver").get()).isSameAs(provider);
    }

    private Translator translator() {
        var source = new StaticMessageSource();
        source.addMessage("diagnostics.coming_soon", Locale.ENGLISH, "Coming soon");
        return new Translator(source);
    }
}
