-- V23__ingestion_name_creator_heartbeat.sql
-- OpenSpec change: ingestion-name-creator-and-stop
-- Adds human-readable name, creator attribution (three columns), and heartbeat timestamp
-- All five columns are nullable to preserve compatibility with rows inserted before this migration
-- The MCP datatalk_http_request input schema enforces name at write time for new rows

ALTER TABLE ingestion_job ADD COLUMN name TEXT;
ALTER TABLE ingestion_job ADD COLUMN created_by_kind TEXT DEFAULT 'ai';
ALTER TABLE ingestion_job ADD COLUMN created_by_session_id TEXT;
ALTER TABLE ingestion_job ADD COLUMN created_by_label TEXT;
ALTER TABLE ingestion_job ADD COLUMN heartbeat_at INTEGER;

CREATE INDEX idx_ingestion_job_heartbeat
  ON ingestion_job(status, heartbeat_at)
  WHERE status IN ('fetching','writing');
