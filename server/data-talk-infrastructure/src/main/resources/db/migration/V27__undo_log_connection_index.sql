CREATE INDEX idx_undo_log_conn_status_created ON undo_log(connection_id, status, created_at DESC);
