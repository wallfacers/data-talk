package com.datatalk.application.connection;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.DeleteOutcome;
import com.datatalk.application.session.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectionDeletionServiceTest {

    @Mock ConnectionRepository connections;
    @Mock SessionRepository sessions;
    @Mock SessionService sessionService;
    @Mock FileArtifactRepository fileArtifacts;
    Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC);

    ConnectionDeletionService svc;

    @BeforeEach
    void setUp() {
        svc = new ConnectionDeletionService(connections, sessions, sessionService, fileArtifacts, clock);
    }

    @Test
    void delete_returns_NotFound_for_missing_connection() {
        when(connections.findById("conn_nope")).thenReturn(Optional.empty());
        var out = svc.delete("conn_nope", false);
        assertThat(out).isInstanceOf(DeleteOutcome.NotFound.class);
    }

    @Test
    void delete_force_false_returns_BlockedByResources_when_counts_nonzero() {
        when(connections.findById("conn_x")).thenReturn(Optional.of(connRec("conn_x", "prod-mysql")));
        when(sessions.listByConnection("conn_x")).thenReturn(List.of(sessionRec("ses_a", "conn_x")));
        when(fileArtifacts.countResourcesByConnection(eq("conn_x"), any()))
                .thenReturn(new FileArtifactRepository.ConnectionResourceCounts(1, 2, 3, 4));

        var out = svc.delete("conn_x", false);

        assertThat(out).isInstanceOf(DeleteOutcome.BlockedByResources.class);
        var blocked = (DeleteOutcome.BlockedByResources) out;
        assertThat(blocked.counts().sessions()).isEqualTo(1);
        verify(connections, never()).deleteById(any());
    }

    @Test
    void delete_force_true_stamps_orphan_metadata_BEFORE_row_delete() {
        when(connections.findById("conn_x")).thenReturn(Optional.of(connRec("conn_x", "prod-mysql")));
        when(sessions.listByConnection("conn_x")).thenReturn(List.of(sessionRec("ses_a", "conn_x")));

        var out = svc.delete("conn_x", true);

        assertThat(out).isInstanceOf(DeleteOutcome.Ok.class);

        var inOrder = inOrder(fileArtifacts, sessionService, connections);
        // Spec §A.4: detach archived (with metadata stamp) FIRST
        inOrder.verify(fileArtifacts).detachArchivedFromConnection("conn_x", "prod-mysql", 1_000L);
        // Then cascade-delete child sessions
        inOrder.verify(sessionService).delete("ses_a", true);
        // Connection row last
        inOrder.verify(connections).deleteById("conn_x");
    }

    @Test
    void delete_force_false_proceeds_when_no_resources() {
        when(connections.findById("conn_x")).thenReturn(Optional.of(connRec("conn_x", "prod-mysql")));
        when(sessions.listByConnection("conn_x")).thenReturn(List.of());
        when(fileArtifacts.countResourcesByConnection(eq("conn_x"), any()))
                .thenReturn(new FileArtifactRepository.ConnectionResourceCounts(0, 0, 0, 0));

        var out = svc.delete("conn_x", false);

        assertThat(out).isInstanceOf(DeleteOutcome.Ok.class);
        verify(connections).deleteById("conn_x");
    }

    private static ConnectionRecord connRec(String id, String name) {
        return new ConnectionRecord(
                id, name, "mysql", "h", 3306, "db", "u", new byte[0],
                null, 0L, 3000, null, null, null, 1, true, null, false);
    }

    private static SessionRecord sessionRec(String id, String connectionId) {
        return new SessionRecord(id, connectionId, "t", true, null, 0L, 0L, false);
    }
}