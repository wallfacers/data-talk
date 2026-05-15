package com.datatalk.application.sql;

import org.springframework.context.ApplicationEvent;

public class UndoLogStatusChangedEvent extends ApplicationEvent {

    private final String undoLogId;
    private final String connectionId;
    private final String status;
    private final Long undoneAt;

    public UndoLogStatusChangedEvent(Object source, String undoLogId, String connectionId,
                                      String status, Long undoneAt) {
        super(source);
        this.undoLogId = undoLogId;
        this.connectionId = connectionId;
        this.status = status;
        this.undoneAt = undoneAt;
    }

    public String getUndoLogId() { return undoLogId; }
    public String getConnectionId() { return connectionId; }
    public String getStatus() { return status; }
    public Long getUndoneAt() { return undoneAt; }
}
