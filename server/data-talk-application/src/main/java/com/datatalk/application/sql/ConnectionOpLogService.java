package com.datatalk.application.sql;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.UndoLogRepository;
import com.datatalk.domain.undo.BatchUndoResult;
import com.datatalk.domain.undo.UndoLogEntry;
import com.datatalk.domain.undo.UndoResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConnectionOpLogService {

    private final UndoLogRepository undoLogRepo;
    private final UndoExecuteService undoExecuteSvc;
    private final ConnectionRepository connRepo;
    private final ConnectionService connSvc;

    public ConnectionOpLogService(UndoLogRepository undoLogRepo,
                                  UndoExecuteService undoExecuteSvc,
                                  ConnectionRepository connRepo,
                                  ConnectionService connSvc) {
        this.undoLogRepo = undoLogRepo;
        this.undoExecuteSvc = undoExecuteSvc;
        this.connRepo = connRepo;
        this.connSvc = connSvc;
    }

    public List<BatchUndoResult> batchUndo(String connectionId, List<String> undoLogIds) {
        ConnectionRecord cr = connRepo.findById(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));
        String password = connSvc.decryptPassword(connectionId);

        List<BatchUndoResult> results = new ArrayList<>();
        for (String id : undoLogIds) {
            try {
                UndoLogEntry entry = undoLogRepo.findByIdAndConnectionId(id, connectionId).orElse(null);
                if (entry == null) {
                    results.add(new BatchUndoResult(id, "not_found", 0, null));
                    continue;
                }
                UndoResult outcome = undoExecuteSvc.execute(id, true, cr, password);
                results.add(mapResult(id, outcome));
            } catch (Exception e) {
                results.add(new BatchUndoResult(id, "error", 0, e.getMessage()));
            }
        }
        return results;
    }

    private BatchUndoResult mapResult(String id, UndoResult outcome) {
        return switch (outcome) {
            case UndoResult.Undone u -> new BatchUndoResult(id, "undone", u.affectedRows(), null);
            case UndoResult.AlreadyUndone a -> new BatchUndoResult(id, "already_undone", 0, null);
            case UndoResult.Expired e -> new BatchUndoResult(id, "expired", 0, null);
            case UndoResult.NotFound n -> new BatchUndoResult(id, "not_found", 0, null);
            case UndoResult.RequiresConfirmation r -> new BatchUndoResult(id, "undone", r.affectedRows(), null);
        };
    }
}
