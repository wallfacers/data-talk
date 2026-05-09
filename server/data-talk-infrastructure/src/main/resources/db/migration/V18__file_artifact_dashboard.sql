-- V18__file_artifact_dashboard.sql
-- Spec: 2026-05-09-dashboard-file-artifact-integration-design §5.1
--
-- SQLite CHECK constraints cannot be ALTERed; rebuild the entire table to add 'dashboard' to kind whitelist
-- and new external column.

CREATE TABLE file_artifact_new (
  id            TEXT PRIMARY KEY,
  scope         TEXT NOT NULL CHECK(scope IN ('session','workspace')),
  status        TEXT NOT NULL CHECK(status IN ('temporary','candidate','archived','discarded')),
  kind          TEXT NOT NULL CHECK(kind IN ('report','er_diagram','sql_script','dataset','dashboard','other')),
  session_id    TEXT,
  connection_id TEXT,
  filename      TEXT NOT NULL,
  physical_path TEXT NOT NULL,
  size_bytes    INTEGER NOT NULL,
  mime_type     TEXT,
  title         TEXT,
  summary       TEXT,
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL,
  archived_at   INTEGER,
  metadata_json TEXT,
  external      INTEGER NOT NULL DEFAULT 0 CHECK(external IN (0, 1))
);

INSERT INTO file_artifact_new
  (id, scope, status, kind, session_id, connection_id, filename, physical_path,
   size_bytes, mime_type, title, summary, created_at, updated_at, archived_at,
   metadata_json, external)
SELECT
   id, scope, status, kind, session_id, connection_id, filename, physical_path,
   size_bytes, mime_type, title, summary, created_at, updated_at, archived_at,
   metadata_json, 0
FROM file_artifact;

DROP TABLE file_artifact;
ALTER TABLE file_artifact_new RENAME TO file_artifact;

CREATE INDEX idx_file_artifact_session    ON file_artifact(session_id)    WHERE scope = 'session';
CREATE INDEX idx_file_artifact_connection ON file_artifact(connection_id) WHERE scope = 'workspace';
CREATE INDEX idx_file_artifact_status     ON file_artifact(status);
CREATE INDEX idx_file_artifact_external   ON file_artifact(external)      WHERE external = 1;
