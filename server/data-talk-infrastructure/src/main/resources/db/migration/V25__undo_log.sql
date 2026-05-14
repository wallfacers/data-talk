CREATE TABLE undo_log (
    id            TEXT PRIMARY KEY,
    session_id    TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    connection_id TEXT NOT NULL,
    database_name TEXT,
    schema_name   TEXT,
    table_name    TEXT NOT NULL,
    operation     TEXT NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    original_sql  TEXT NOT NULL,
    inverse_sql   TEXT,
    before_state  TEXT,
    affected_rows INTEGER NOT NULL DEFAULT 0,
    undoable      INTEGER NOT NULL DEFAULT 1,
    status        TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'active', 'undone', 'expired')),
    expires_at    INTEGER NOT NULL,
    created_at    INTEGER NOT NULL,
    undone_at     INTEGER
);

CREATE INDEX idx_undo_log_session ON undo_log(session_id, status);
