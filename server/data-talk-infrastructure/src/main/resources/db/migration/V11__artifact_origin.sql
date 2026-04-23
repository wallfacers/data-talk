ALTER TABLE artifacts ADD COLUMN origin_message_id TEXT;
ALTER TABLE artifacts ADD COLUMN origin_part_id TEXT;
CREATE INDEX idx_artifacts_origin ON artifacts(session_id, origin_message_id, origin_part_id);
