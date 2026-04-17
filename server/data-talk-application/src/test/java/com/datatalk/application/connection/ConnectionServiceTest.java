package com.datatalk.application.connection;

import com.datatalk.application.persistence.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConnectionServiceTest {

    @Test
    void testConnection_succeeds_against_h2_in_memory() {
        var repo = mock(ConnectionRepository.class);
        var vault = mock(SecretVault.class);
        var clk = Clock.systemUTC();
        var svc = new ConnectionService(repo, vault, clk);

        when(repo.findById("c1")).thenReturn(Optional.of(
            new ConnectionRecord("c1", "h2", "localhost", 9999,
                "mem:it;DB_CLOSE_DELAY=-1", "sa", new byte[]{}, null, 0)));
        when(vault.open(any())).thenReturn("");

        var r = svc.testConnection("c1");
        assertThat(r.ok()).isTrue();
        assertThat(r.latencyMs()).isNotNegative();
    }

    @Test
    void testConnection_fails_fast_on_bad_port() {
        var repo = mock(ConnectionRepository.class);
        var vault = mock(SecretVault.class);
        var svc = new ConnectionService(repo, vault, Clock.systemUTC());
        when(repo.findById("c1")).thenReturn(Optional.of(
            new ConnectionRecord("c1", "mysql", "127.0.0.1", 1, "x", "u", new byte[]{}, null, 0)));
        when(vault.open(any())).thenReturn("p");

        var r = svc.testConnection("c1");
        assertThat(r.ok()).isFalse();
        assertThat(r.reason()).isNotBlank();
    }
}
