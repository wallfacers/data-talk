-- ============================================================
-- DataTalk v0.0.2 — User message attachments
-- Stores file_upload parts that DataTalk echoes locally on top
-- of OpenCode's text-only protocol (see BUG-0056).
-- ============================================================

CREATE TABLE user_message_attachments (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    message_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    part_json TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX idx_user_message_attachments_session_message
    ON user_message_attachments(session_id, message_id, position, id);
