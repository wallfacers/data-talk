package com.datatalk.application.sql;

import org.springframework.context.ApplicationEvent;

public class UndoLogCreatedEvent extends ApplicationEvent {

    private final String undoLogId;
    private final String connectionId;
    private final String operation;
    private final String tableName;
    private final int affectedRows;
    private final long createdAt;

    public UndoLogCreatedEvent(Object source, String undoLogId, String connectionId,
                                String operation, String tableName, int affectedRows, long createdAt) {
        super(source);
        this.undoLogId = undoLogId;
        this.connectionId = connectionId;
        this.operation = operation;
        this.tableName = tableName;
        this.affectedRows = affectedRows;
        this.createdAt = createdAt;
    }

    public String getUndoLogId() { return undoLogId; }
    public String getConnectionId() { return connectionId; }
    public String getOperation() { return operation; }
    public String getTableName() { return tableName; }
    public int getAffectedRows() { return affectedRows; }
    public long getCreatedAt() { return createdAt; }
}
