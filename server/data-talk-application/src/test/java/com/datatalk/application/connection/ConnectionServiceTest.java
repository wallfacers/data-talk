package com.datatalk.application.connection;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConnectionServiceTest {

    private Translator translator() {
        var translator = mock(Translator.class);
        when(translator.get(eq("connection.test.invalid"))).thenReturn("connection invalid");
        when(translator.get(eq("connection.test.failure"), any(), any())).thenReturn("connection failed");
        when(translator.get(eq("connection.default_name"), any())).thenAnswer(inv -> "Data Source-" + inv.getArgument(1));
        when(translator.get(eq("error.connection.unknown"), any())).thenAnswer(inv -> "unknown connection: " + inv.getArgument(1));
        return translator;
    }

    @Test
    void testConnection_succeeds_against_h2_in_memory() {
        var repo = mock(ConnectionRepository.class);
        var vault = mock(SecretVault.class);
        var clk = Clock.systemUTC();
        var svc = new ConnectionService(repo, vault, clk, translator());

        when(repo.findById("c1")).thenReturn(Optional.of(
            new ConnectionRecord("c1", "测试连接", "h2", "localhost", 9999,
                "mem:it;DB_CLOSE_DELAY=-1", "sa", new byte[]{}, null, 0, 3000, null, null)));
        when(vault.open(any())).thenReturn("");

        var r = svc.testConnection("c1");
        assertThat(r.ok()).isTrue();
        assertThat(r.latencyMs()).isNotNegative();
    }

    @Test
    void testConnection_fails_fast_on_bad_port() {
        var repo = mock(ConnectionRepository.class);
        var vault = mock(SecretVault.class);
        var svc = new ConnectionService(repo, vault, Clock.systemUTC(), translator());
        when(repo.findById("c1")).thenReturn(Optional.of(
            new ConnectionRecord("c1", "测试连接", "mysql", "127.0.0.1", 1, "x", "u", new byte[]{}, null, 0, 3000, null, null)));
        when(vault.open(any())).thenReturn("p");

        var r = svc.testConnection("c1");
        assertThat(r.ok()).isFalse();
        assertThat(r.reason()).isNotBlank();
    }

    @Test
    void testConnection_persists_ok_status_on_success() {
        var repo = mock(ConnectionRepository.class);
        var vault = mock(SecretVault.class);
        var clock = Clock.systemUTC();
        var svc = new ConnectionService(repo, vault, clock, translator());

        when(repo.findById("c1")).thenReturn(Optional.of(
            new ConnectionRecord("c1", "测试连接", "h2", "localhost", 9999,
                "mem:it;DB_CLOSE_DELAY=-1", "sa", new byte[]{}, null, 0, 3000, null, null)));
        when(vault.open(any())).thenReturn("");

        var r = svc.testConnection("c1");

        assertThat(r.ok()).isTrue();
        verify(repo).updateTestStatus(eq("c1"), eq("ok"), anyLong());
    }

    @Test
    void testConnection_persists_fail_status_on_failure() {
        var repo = mock(ConnectionRepository.class);
        var vault = mock(SecretVault.class);
        var clock = Clock.systemUTC();
        var svc = new ConnectionService(repo, vault, clock, translator());

        when(repo.findById("c1")).thenReturn(Optional.of(
            new ConnectionRecord("c1", "测试连接", "mysql", "127.0.0.1", 1, "x", "u", new byte[]{}, null, 0, 3000, null, null)));
        when(vault.open(any())).thenReturn("p");

        var r = svc.testConnection("c1");

        assertThat(r.ok()).isFalse();
        verify(repo).updateTestStatus(eq("c1"), eq("fail"), anyLong());
    }
}
