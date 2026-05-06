package com.datatalk.application.connection;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.*;
import com.datatalk.application.stage.StageTabRepository;
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
        var svc = new ConnectionService(repo, mock(SessionRepository.class), mock(StageTabRepository.class), vault, clk, translator());

        when(repo.findById("c1")).thenReturn(Optional.of(
            new ConnectionRecord("c1", "测试连接", "h2", "localhost", 9999,
                "mem:it;DB_CLOSE_DELAY=-1", "sa", new byte[]{}, null, 0, 3000, null, null, null, 1, true, null)));
        when(vault.open(any())).thenReturn("");

        var r = svc.testConnection("c1");
        assertThat(r.ok()).isTrue();
        assertThat(r.latencyMs()).isNotNegative();
    }

    @Test
    void testConnection_fails_fast_on_bad_port() {
        var repo = mock(ConnectionRepository.class);
        var vault = mock(SecretVault.class);
        var svc = new ConnectionService(repo, mock(SessionRepository.class), mock(StageTabRepository.class), vault, Clock.systemUTC(), translator());
        when(repo.findById("c1")).thenReturn(Optional.of(
            new ConnectionRecord("c1", "测试连接", "mysql", "127.0.0.1", 1, "x", "u", new byte[]{}, null, 0, 3000, null, null, null, 1, true, null)));
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
        var svc = new ConnectionService(repo, mock(SessionRepository.class), mock(StageTabRepository.class), vault, clock, translator());

        when(repo.findById("c1")).thenReturn(Optional.of(
            new ConnectionRecord("c1", "测试连接", "h2", "localhost", 9999,
                "mem:it;DB_CLOSE_DELAY=-1", "sa", new byte[]{}, null, 0, 3000, null, null, null, 1, true, null)));
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
        var svc = new ConnectionService(repo, mock(SessionRepository.class), mock(StageTabRepository.class), vault, clock, translator());

        when(repo.findById("c1")).thenReturn(Optional.of(
            new ConnectionRecord("c1", "测试连接", "mysql", "127.0.0.1", 1, "x", "u", new byte[]{}, null, 0, 3000, null, null, null, 1, true, null)));
        when(vault.open(any())).thenReturn("p");

        var r = svc.testConnection("c1");

        assertThat(r.ok()).isFalse();
        verify(repo).updateTestStatus(eq("c1"), eq("fail"), anyLong());
    }

    @Test
    void get_returns_current_connection_details() {
        var repo = mock(ConnectionRepository.class);
        var vault = mock(SecretVault.class);
        var svc = new ConnectionService(repo, mock(SessionRepository.class), mock(StageTabRepository.class), vault, Clock.systemUTC(), translator());

        when(repo.findById("c1")).thenReturn(Optional.of(
            new ConnectionRecord("c1", "测试连接", "mysql", "127.0.0.1", 3306,
                "analytics", "u", new byte[]{1}, "digest", 123L, 3000, "ok", 456L, null, 1, true, null)));

        var dto = svc.get("c1");

        assertThat(dto.id()).isEqualTo("c1");
        assertThat(dto.name()).isEqualTo("测试连接");
        assertThat(dto.lastTestStatus()).isEqualTo("ok");
    }

    @Test
    void deleteById_rejects_when_sessions_exist() {
        var repo = mock(ConnectionRepository.class);
        var sessionRepo = mock(SessionRepository.class);
        when(sessionRepo.listByConnection("c1")).thenReturn(java.util.List.of(
            mock(SessionRecord.class)));

        var svc = new ConnectionService(repo, sessionRepo, mock(StageTabRepository.class),
            mock(SecretVault.class), Clock.systemUTC(), translator());

        assertThatThrownBy(() -> svc.deleteById("c1"))
            .isInstanceOf(ConnectionInUseException.class);
    }

    @Test
    void deleteById_rejects_when_stage_tabs_exist() {
        var repo = mock(ConnectionRepository.class);
        var sessionRepo = mock(SessionRepository.class);
        var stageTabRepo = mock(StageTabRepository.class);
        when(sessionRepo.listByConnection("c1")).thenReturn(java.util.List.of());
        when(stageTabRepo.list(any(StageTabRepository.ListFilter.class))).thenReturn(
            java.util.List.of(mock(com.datatalk.domain.stage.StageTab.class)));

        var svc = new ConnectionService(repo, sessionRepo, stageTabRepo,
            mock(SecretVault.class), Clock.systemUTC(), translator());

        assertThatThrownBy(() -> svc.deleteById("c1"))
            .isInstanceOf(ConnectionInUseException.class);
    }

    @Test
    void deleteById_succeeds_when_no_dependencies() {
        var repo = mock(ConnectionRepository.class);
        var sessionRepo = mock(SessionRepository.class);
        var stageTabRepo = mock(StageTabRepository.class);
        when(sessionRepo.listByConnection("c1")).thenReturn(java.util.List.of());
        when(stageTabRepo.list(any(StageTabRepository.ListFilter.class))).thenReturn(java.util.List.of());
        when(repo.deleteById("c1")).thenReturn(true);

        var svc = new ConnectionService(repo, sessionRepo, stageTabRepo,
            mock(SecretVault.class), Clock.systemUTC(), translator());

        assertThat(svc.deleteById("c1")).isTrue();
    }

    @Test
    void create_persists_sqlserver_kind() {
        var repo = mock(ConnectionRepository.class);
        var vault = mock(SecretVault.class);
        when(vault.seal("pw")).thenReturn(new byte[]{1});
        var svc = new ConnectionService(repo, mock(SessionRepository.class), mock(StageTabRepository.class), vault, Clock.systemUTC(), translator());

        svc.create("SQL Server Test", "sqlserver", "db.host", 1433, "mydb", "sa", "pw", 5000, null, null, null, null);

        var captor = org.mockito.ArgumentCaptor.forClass(ConnectionRecord.class);
        verify(repo).insert(captor.capture());
        ConnectionRecord captured = captor.getValue();
        assertThat(captured.kind()).isEqualTo("sqlserver");
        assertThat(captured.sqlserverEncrypt()).isEqualTo(1);
        assertThat(captured.sqlserverTrustServerCertificate()).isTrue();
        assertThat(captured.sqlserverInstanceName()).isNull();
    }

    @Test
    void create_normalizes_mssql_alias_to_sqlserver() {
        var repo = mock(ConnectionRepository.class);
        var vault = mock(SecretVault.class);
        when(vault.seal("pw")).thenReturn(new byte[]{1});
        var svc = new ConnectionService(repo, mock(SessionRepository.class), mock(StageTabRepository.class), vault, Clock.systemUTC(), translator());

        svc.create("MSSQL Alias", "mssql", "db.host", 1433, "mydb", "sa", "pw", 5000, null, null, null, null);

        var captor = org.mockito.ArgumentCaptor.forClass(ConnectionRecord.class);
        verify(repo).insert(captor.capture());
        assertThat(captor.getValue().kind()).isEqualTo("sqlserver");
    }
}
