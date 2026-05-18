-- ============================================================
-- DataTalk V2 — user_message_attachments table
-- Extracted from V1__init.sql post-v0.0.1 to fix BUG-0061:
--   V1 was edited in-place to add this table; environments that
--   had already applied the pre-edit V1 ended up with checksum
--   drift AND missing table. Placing the DDL in its own migration
--   restores reachability for both fresh and incremental upgrade.
-- IF NOT EXISTS keeps the migration idempotent for environments
-- that manually created the table via BUG-0061 workaround.
-- ============================================================

CREATE TABLE IF NOT EXISTS user_message_attachments (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    message_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    part_json TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_message_attachments_session_message
    ON user_message_attachments(session_id, message_id, position, id);
