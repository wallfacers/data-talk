-- V20__ingestion.sql
-- Task 10 / Spec 2026-05-12-external-data-ingestion-skills-design §3.1
-- 1) ingestion_credential, 2) ingestion_job, 3) file_artifact CHECK rebuild adding 'ingestion_payload'

CREATE TABLE ingestion_credential (
  id           TEXT PRIMARY KEY,
  name         TEXT NOT NULL UNIQUE,
  auth_scheme  TEXT NOT NULL CHECK(auth_scheme IN
                 ('none','bearer','api_key_header','api_key_query','basic')),
  config_json  TEXT NOT NULL,
  vault_id     TEXT,
  created_at   INTEGER NOT NULL,
  updated_at   INTEGER NOT NULL
);

CREATE TABLE ingestion_job (
  id                       TEXT PRIMARY KEY,
  source_url               TEXT NOT NULL,
  source_method            TEXT NOT NULL CHECK(source_method IN ('GET','POST')),
  source_headers_json      TEXT,
  source_query_params_json TEXT,
  source_body_json         TEXT,
  credential_id            TEXT,
  pagination_json          TEXT,
  payload_format           TEXT NOT NULL CHECK(payload_format IN ('json','jsonl','csv','html')),
  payload_artifact_id      TEXT,
  status                   TEXT NOT NULL CHECK(status IN
                             ('pending','fetching','fetched','mapping','confirmed',
                              'writing','completed','failed','cancelled')),
  connection_id            TEXT,
  target_schema            TEXT,
  target_table             TEXT,
  mapping_json             TEXT,
  row_count                INTEGER,
  rows_inserted            INTEGER DEFAULT 0,
  bytes_fetched            INTEGER,
  created_at               INTEGER NOT NULL,
  updated_at               INTEGER NOT NULL,
  completed_at             INTEGER,
  error_message            TEXT
);
CREATE INDEX idx_ingestion_job_status     ON ingestion_job(status);
CREATE INDEX idx_ingestion_job_connection ON ingestion_job(connection_id) WHERE connection_id IS NOT NULL;
CREATE INDEX idx_ingestion_job_created    ON ingestion_job(created_at);

-- file_artifact CHECK rebuild (SQLite cannot ALTER CHECK)
-- Columns match V18 schema exactly, with expanded CHECK for kind adding 'ingestion_payload'
CREATE TABLE file_artifact_new (
  id            TEXT PRIMARY KEY,
  scope         TEXT NOT NULL CHECK(scope IN ('session','workspace')),
  status        TEXT NOT NULL CHECK(status IN ('temporary','candidate','archived','discarded')),
  kind          TEXT NOT NULL CHECK(kind IN
                  ('report','er_diagram','sql_script','dataset','dashboard','ingestion_payload','other')),
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
  external      INTEGER NOT NULL DEFAULT 0 CHECK(external IN (0,1))
);

INSERT INTO file_artifact_new
  (id, scope, status, kind, session_id, connection_id, filename, physical_path,
   size_bytes, mime_type, title, summary, created_at, updated_at, archived_at,
   metadata_json, external)
SELECT
   id, scope, status, kind, session_id, connection_id, filename, physical_path,
   size_bytes, mime_type, title, summary, created_at, updated_at, archived_at,
   metadata_json, external
FROM file_artifact;

DROP TABLE file_artifact;
ALTER TABLE file_artifact_new RENAME TO file_artifact;

CREATE INDEX idx_file_artifact_session    ON file_artifact(session_id)    WHERE scope = 'session';
CREATE INDEX idx_file_artifact_connection ON file_artifact(connection_id) WHERE scope = 'workspace';
CREATE INDEX idx_file_artifact_status     ON file_artifact(status);
CREATE INDEX idx_file_artifact_external   ON file_artifact(external)      WHERE external = 1;
