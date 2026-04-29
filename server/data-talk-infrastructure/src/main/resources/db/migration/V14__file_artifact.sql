-- File Artifact System (spec 2026-04-29-opencode-workdir-and-artifact-system-design)
-- Independent from the existing artifacts payload table.
-- session_id / connection_id are application-managed references, intentionally without FKs.

CREATE TABLE file_artifact (
  id            TEXT PRIMARY KEY,
  scope         TEXT NOT NULL CHECK(scope IN ('session','workspace')),
  status        TEXT NOT NULL CHECK(status IN ('temporary','candidate','archived','discarded')),
  kind          TEXT NOT NULL CHECK(kind IN ('report','er_diagram','sql_script','dataset','other')),
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
  metadata_json TEXT
);

CREATE INDEX idx_file_artifact_session    ON file_artifact(session_id)    WHERE scope = 'session';
CREATE INDEX idx_file_artifact_connection ON file_artifact(connection_id) WHERE scope = 'workspace';
CREATE INDEX idx_file_artifact_status     ON file_artifact(status);
