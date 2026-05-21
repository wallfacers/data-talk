package com.datatalk.application.connection;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.semantic.SemanticModelRepository;
import com.datatalk.application.session.DeleteOutcome;
import com.datatalk.application.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Two-phase connection delete orchestrator.
 *
 * <p>Spec §A.1 (DELETE connection two-phase) + §A.4 (deleteConnectionFileArtifacts
 * ordering: must read connection.name BEFORE setting connection_id=NULL on
 * archived rows).
 */
@Service
public class ConnectionDeletionService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionDeletionService.class);

    private final ConnectionRepository connections;
    private final SessionRepository sessions;
    private final SessionService sessionService;
    private final FileArtifactRepository fileArtifacts;
    private final SemanticModelRepository semanticModelRepository;
    private final Clock clock;

    public ConnectionDeletionService(
            ConnectionRepository connections,
            SessionRepository sessions,
            SessionService sessionService,
            FileArtifactRepository fileArtifacts,
            SemanticModelRepository semanticModelRepository,
            Clock clock) {
        this.connections = connections;
        this.sessions = sessions;
        this.sessionService = sessionService;
        this.fileArtifacts = fileArtifacts;
        this.semanticModelRepository = semanticModelRepository;
        this.clock = clock;
    }

    @Transactional
    public DeleteOutcome delete(String connectionId, boolean force) {
        ConnectionRecord rec = connections.findById(connectionId).orElse(null);
        if (rec == null) {
            return new DeleteOutcome.NotFound(connectionId);
        }
        List<SessionRecord> childSessions = sessions.listByConnection(connectionId);
        List<String> childSessionIds = childSessions.stream().map(SessionRecord::id).toList();

        if (!force) {
            FileArtifactRepository.ConnectionResourceCounts counts =
                    fileArtifacts.countResourcesByConnection(connectionId, childSessionIds);
            int vqCount = semanticModelRepository.countVerifiedQueriesByConnection(connectionId);
            if (counts.sessions() > 0 || counts.candidates() > 0
                    || counts.temporary() > 0 || counts.archived() > 0
                    || vqCount > 0) {
                return new DeleteOutcome.BlockedByResources(connectionId, counts, vqCount);
            }
            // No resources at all — just drop the connection row.
            connections.deleteById(connectionId);
            return new DeleteOutcome.Ok();
        }

        // force = true: cascade
        // 1) move semantic model to trash first
        semanticModelRepository.moveToTrash(connectionId, clock.millis());

        // 2) detach archived rows + stamp orphan metadata (MUST happen BEFORE
        //    connection row deletion so the name is still discoverable, spec §A.4).
        fileArtifacts.detachArchivedFromConnection(connectionId, rec.name(), clock.millis());

        // 3) delete every child session forcefully (its own transient
        //    file_artifacts get cleaned up + archived rows already detached).
        for (String sid : childSessionIds) {
            try {
                sessionService.delete(sid, true);
            } catch (Exception e) {
                log.warn("[connection-delete] child session {} delete failed: {}", sid, e.toString());
            }
        }

        // 3) delete the connection row last.
        connections.deleteById(connectionId);

        return new DeleteOutcome.Ok();
    }
}