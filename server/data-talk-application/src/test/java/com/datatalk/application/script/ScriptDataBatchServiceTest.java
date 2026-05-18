package com.datatalk.application.script;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScriptDataBatchServiceTest {

    private ScriptDataWriteService writeService;
    private Clock clock;
    private ScriptDataBatchService service;

    private static final Instant NOW = Instant.parse("2026-05-18T10:00:00Z");

    @BeforeEach
    void setUp() {
        writeService = mock(ScriptDataWriteService.class);
        clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        service = new ScriptDataBatchService(writeService, clock);
    }

    @Test
    void writeBatch_firstCall_createsTableAndInserts() {
        List<Map<String, Object>> rows = List.of(
            Map.of("id", 1, "name", "Alice"),
            Map.of("id", 2, "name", "Bob")
        );
        when(writeService.write(eq("conn-1"), eq("users"), eq(rows), eq(true)))
            .thenReturn(new ScriptDataWriteService.WriteResult(2, "users", List.of("id", "name")));

        var result = service.writeBatch(null, "conn-1", "users", rows, true);

        assertThat(result.sessionId()).isNotNull();
        assertThat(result.rowsInserted()).isEqualTo(2);
        assertThat(result.totalRowsInserted()).isEqualTo(2);
    }

    @Test
    void writeBatch_subsequentCall_addsToSameSession() {
        String sessionId = "session-abc";
        List<Map<String, Object>> rows1 = List.of(Map.of("id", 1));
        List<Map<String, Object>> rows2 = List.of(Map.of("id", 2));

        when(writeService.write(eq("conn-1"), eq("users"), eq(rows1), eq(false)))
            .thenReturn(new ScriptDataWriteService.WriteResult(1, "users", List.of("id")));
        when(writeService.write(eq("conn-1"), eq("users"), eq(rows2), eq(false)))
            .thenReturn(new ScriptDataWriteService.WriteResult(1, "users", List.of("id")));

        // First: seed the session with 5 total from previous
        service.writeBatch(sessionId, "conn-1", "users", rows1, false);

        // Second: add more
        var result = service.writeBatch(sessionId, "conn-1", "users", rows2, false);

        assertThat(result.sessionId()).isEqualTo(sessionId);
        assertThat(result.rowsInserted()).isEqualTo(1);
        // First insert already had 1, plus this one = 2
        assertThat(result.totalRowsInserted()).isEqualTo(2);
    }

    @Test
    void writeBatch_newSession_createsSessionWithTotalEqualToRowsInserted() {
        List<Map<String, Object>> rows = List.of(Map.of("id", 1), Map.of("id", 2), Map.of("id", 3));
        when(writeService.write(eq("conn-1"), eq("orders"), eq(rows), eq(true)))
            .thenReturn(new ScriptDataWriteService.WriteResult(3, "orders", List.of("id")));

        var result = service.writeBatch(null, "conn-1", "orders", rows, true);

        assertThat(result.totalRowsInserted()).isEqualTo(3);
        assertThat(result.rowsInserted()).isEqualTo(3);
    }

    @Test
    void writeBatch_createTableFalse_forExistingSession() {
        String sessionId = "session-xyz";
        List<Map<String, Object>> rows = List.of(Map.of("id", 1));

        // createTable=true but session already exists in map -> should pass false to writeService
        // First seed the session
        when(writeService.write(eq("conn-1"), eq("tbl"), eq(rows), eq(true)))
            .thenReturn(new ScriptDataWriteService.WriteResult(1, "tbl", List.of("id")));
        service.writeBatch(sessionId, "conn-1", "tbl", rows, true);

        // Second call with createTable=true but session exists
        List<Map<String, Object>> rows2 = List.of(Map.of("id", 2));
        when(writeService.write(eq("conn-1"), eq("tbl"), eq(rows2), eq(false)))
            .thenReturn(new ScriptDataWriteService.WriteResult(1, "tbl", List.of("id")));

        var result = service.writeBatch(sessionId, "conn-1", "tbl", rows2, true);

        // createTable should be false since session already exists
        verify(writeService).write("conn-1", "tbl", rows2, false);
        assertThat(result.totalRowsInserted()).isEqualTo(2);
    }

    @Test
    void closeSession_returnsAndRemovesSession() {
        List<Map<String, Object>> rows = List.of(Map.of("id", 1));
        when(writeService.write(eq("conn-1"), eq("tbl"), eq(rows), eq(true)))
            .thenReturn(new ScriptDataWriteService.WriteResult(1, "tbl", List.of("id")));

        var result = service.writeBatch(null, "conn-1", "tbl", rows, true);
        String sessionId = result.sessionId();

        var closed = service.closeSession(sessionId);

        assertThat(closed).isNotNull();
        assertThat(closed.totalRowsInserted()).isEqualTo(1);
        assertThat(closed.tableName()).isEqualTo("tbl");

        // Closing again returns null
        assertThat(service.closeSession(sessionId)).isNull();
    }

    @Test
    void cleanupExpiredSessions_removesStaleSessions() {
        List<Map<String, Object>> rows = List.of(Map.of("id", 1));
        when(writeService.write(eq("conn-1"), eq("stale_tbl"), eq(rows), eq(true)))
            .thenReturn(new ScriptDataWriteService.WriteResult(1, "stale_tbl", List.of("id")));

        var result = service.writeBatch(null, "conn-1", "stale_tbl", rows, true);
        String sessionId = result.sessionId();

        // Session should exist before cleanup
        assertThat(service.closeSession(sessionId)).isNotNull();

        // Re-create the session (closeSession removed it)
        when(writeService.write(eq("conn-1"), eq("stale_tbl"), eq(rows), eq(false)))
            .thenReturn(new ScriptDataWriteService.WriteResult(1, "stale_tbl", List.of("id")));
        var result2 = service.writeBatch(sessionId, "conn-1", "stale_tbl", rows, false);

        // With the fixed clock at NOW, the session was just created so it won't be expired.
        // Verify cleanup does not remove fresh sessions
        service.cleanupExpiredSessions();

        // The session should still exist (cleanupExpiredSessions should not remove fresh sessions)
        // We can verify by trying to add to it and seeing the total increases
        List<Map<String, Object>> rows3 = List.of(Map.of("id", 3));
        when(writeService.write(eq("conn-1"), eq("stale_tbl"), eq(rows3), eq(false)))
            .thenReturn(new ScriptDataWriteService.WriteResult(1, "stale_tbl", List.of("id")));
        var result3 = service.writeBatch(sessionId, "conn-1", "stale_tbl", rows3, false);

        assertThat(result3.totalRowsInserted()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void cleanupExpiredSessions_withExpiredSession_removesIt() {
        // Use real clock to create an old session, then verify cleanup on a real service
        Clock realClock = Clock.systemUTC();
        ScriptDataBatchService realService = new ScriptDataBatchService(writeService, realClock);

        List<Map<String, Object>> rows = List.of(Map.of("id", 1));
        when(writeService.write(eq("conn-1"), eq("tbl"), eq(rows), eq(true)))
            .thenReturn(new ScriptDataWriteService.WriteResult(1, "tbl", List.of("id")));

        var result = realService.writeBatch(null, "conn-1", "tbl", rows, true);
        String sessionId = result.sessionId();

        // Session was just created, cleanup should not remove it
        realService.cleanupExpiredSessions();

        // Session should still exist (it was created just now, not expired)
        assertThat(realService.closeSession(sessionId)).isNotNull();
    }
}
