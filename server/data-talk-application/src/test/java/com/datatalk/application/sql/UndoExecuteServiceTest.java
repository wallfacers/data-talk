package com.datatalk.application.sql;

import com.datatalk.application.persistence.UndoLogRepository;
import com.datatalk.domain.undo.UndoLogEntry;
import com.datatalk.domain.undo.UndoResult;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UndoExecuteServiceTest {

    private final UndoLogRepository repo = mock(UndoLogRepository.class);
    private final UndoExecuteService service = new UndoExecuteService(repo, null);

    private UndoLogEntry entry(String status, boolean undoable, long expiresAt) {
        return new UndoLogEntry(
            "log-1", "sess-1", "conn-1", "db", "public",
            "users", "UPDATE", "UPDATE users SET name='Bob' WHERE id=1",
            "UPDATE users SET name='Alice' WHERE id=1", null,
            1, undoable, status, expiresAt, 1000L, null
        );
    }

    @Test
    void entryNotFound_returnsNotFound() {
        when(repo.findById("missing")).thenReturn(Optional.empty());
        UndoResult result = service.execute("missing", false);
        assertThat(result).isInstanceOf(UndoResult.NotFound.class);
    }

    @Test
    void alreadyUndone_returnsAlreadyUndone() {
        when(repo.findById("log-1")).thenReturn(Optional.of(entry("undone", true, Long.MAX_VALUE)));
        UndoResult result = service.execute("log-1", false);
        assertThat(result).isInstanceOf(UndoResult.AlreadyUndone.class);
    }

    @Test
    void expiredStatus_returnsExpired() {
        when(repo.findById("log-1")).thenReturn(Optional.of(entry("expired", true, Long.MAX_VALUE)));
        UndoResult result = service.execute("log-1", false);
        assertThat(result).isInstanceOf(UndoResult.Expired.class);
    }

    @Test
    void pastExpiresAt_returnsExpired() {
        long past = System.currentTimeMillis() - 1000;
        when(repo.findById("log-1")).thenReturn(Optional.of(entry("active", true, past)));
        UndoResult result = service.execute("log-1", false);
        assertThat(result).isInstanceOf(UndoResult.Expired.class);
    }

    @Test
    void undoableFalse_returnsNotFound() {
        when(repo.findById("log-1")).thenReturn(Optional.of(entry("active", false, Long.MAX_VALUE)));
        UndoResult result = service.execute("log-1", false);
        assertThat(result).isInstanceOf(UndoResult.NotFound.class);
    }

    @Test
    void notConfirmed_returnsRequiresConfirmation() {
        when(repo.findById("log-1")).thenReturn(Optional.of(entry("active", true, Long.MAX_VALUE)));
        UndoResult result = service.execute("log-1", false);
        assertThat(result).isInstanceOf(UndoResult.RequiresConfirmation.class);
        UndoResult.RequiresConfirmation rc = (UndoResult.RequiresConfirmation) result;
        assertThat(rc.inverseSql()).isEqualTo("UPDATE users SET name='Alice' WHERE id=1");
        assertThat(rc.affectedRows()).isEqualTo(1);
        assertThat(rc.tableName()).isEqualTo("users");
    }
}
