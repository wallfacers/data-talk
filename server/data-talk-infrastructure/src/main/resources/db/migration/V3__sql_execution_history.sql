-- ============================================================
-- DataTalk V3 — sql_execution_history table
-- Per-session log of SQL executions used to power:
--   * datatalk_query_history MCP action (read by AI)
--   * AgentPromptBuilder {{RECENT_FAILED_QUERIES_DIGEST}}
--     placeholder rendered into system prompt
-- Writes happen inside ExecuteSqlAction after JDBC commit.
-- Records are bounded per session (top 100) via async trim.
-- ============================================================

CREATE TABLE IF NOT EXISTS sql_execution_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    connection_id TEXT NOT NULL,
    database_name TEXT,
    schema_name TEXT,
    sql_text TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('success', 'failure')),
    error_code TEXT,
    error_message TEXT,
    executed_at INTEGER NOT NULL,
    duration_ms INTEGER,
    row_count INTEGER
);

CREATE INDEX IF NOT EXISTS idx_sql_history_session_executed
    ON sql_execution_history(session_id, executed_at DESC);

CREATE INDEX IF NOT EXISTS idx_sql_history_session_status
    ON sql_execution_history(session_id, status, executed_at DESC);
