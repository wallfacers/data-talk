DROP TABLE IF EXISTS pending_calls;
DROP TABLE IF EXISTS action_invocations;
DROP TABLE IF EXISTS query_results;
DROP TABLE IF EXISTS events;
DROP TABLE IF EXISTS undo_log;
DROP TABLE IF EXISTS file_artifact;
DROP TABLE IF EXISTS artifacts;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS sessions;
DROP TABLE IF EXISTS ingestion_job;
DROP TABLE IF EXISTS ingestion_credential;
DROP TABLE IF EXISTS ingestion_vault_store;
DROP TABLE IF EXISTS connections;
DROP TABLE IF EXISTS ai_model_prefs;
DROP TABLE IF EXISTS ai_user_prefs;

CREATE TABLE connections (
  id TEXT PRIMARY KEY, name TEXT NOT NULL DEFAULT '', kind TEXT NOT NULL, host TEXT NOT NULL, port INTEGER NOT NULL,
  database_name TEXT, username TEXT NOT NULL, password_enc BLOB NOT NULL,
  schema_digest TEXT, created_at INTEGER NOT NULL, connect_timeout INTEGER NOT NULL DEFAULT 3000
);
CREATE UNIQUE INDEX idx_connections_name ON connections(name);
CREATE TABLE sessions (
  id TEXT PRIMARY KEY, connection_id TEXT, title TEXT NOT NULL,
  has_ever_sent INTEGER NOT NULL DEFAULT 0, opencode_sid TEXT,
  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
  title_locked INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE messages (
  id TEXT PRIMARY KEY, session_id TEXT NOT NULL,
  role TEXT NOT NULL, parts_json TEXT NOT NULL, created_at INTEGER NOT NULL
);
CREATE TABLE artifacts (
  id TEXT NOT NULL, version INTEGER NOT NULL, session_id TEXT NOT NULL,
  kind TEXT NOT NULL, produced_by TEXT NOT NULL,
  payload_ref TEXT NOT NULL, payload_size INTEGER NOT NULL, supersedes_id TEXT,
  supersedes_ver INTEGER, pinned INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL,
  origin_message_id TEXT,
  origin_part_id TEXT,
  PRIMARY KEY (id, version)
);
CREATE INDEX idx_artifacts_origin ON artifacts(session_id, origin_message_id, origin_part_id);
CREATE TABLE action_invocations (
  call_id TEXT PRIMARY KEY, session_id TEXT NOT NULL, action_id TEXT NOT NULL,
  input_json TEXT, output_json TEXT, error_json TEXT, started_at INTEGER NOT NULL, completed_at INTEGER
);
CREATE TABLE events (
  event_id INTEGER NOT NULL, session_id TEXT NOT NULL,
  event_type TEXT NOT NULL, payload_json TEXT NOT NULL, ts INTEGER NOT NULL,
  PRIMARY KEY (session_id, event_id)
);
CREATE TABLE query_results (
  handle TEXT PRIMARY KEY, session_id TEXT NOT NULL,
  columns_json TEXT NOT NULL, rows_ndjson TEXT NOT NULL, row_count INTEGER NOT NULL,
  created_at INTEGER NOT NULL, ttl_at INTEGER NOT NULL
);
CREATE TABLE pending_calls (
  call_id TEXT PRIMARY KEY, expires_at INTEGER NOT NULL
);

CREATE TABLE ai_user_prefs (
  id            TEXT PRIMARY KEY,
  current_model TEXT,
  updated_at    INTEGER NOT NULL
);

INSERT INTO ai_user_prefs(id, current_model, updated_at)
VALUES ('default', NULL, 0);

CREATE TABLE ai_model_prefs (
  provider_id TEXT    NOT NULL,
  model_id    TEXT    NOT NULL,
  enabled     INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL,
  PRIMARY KEY (provider_id, model_id)
);

CREATE TABLE file_artifact (
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
CREATE INDEX idx_file_artifact_session ON file_artifact(session_id);
CREATE INDEX idx_file_artifact_connection ON file_artifact(connection_id);
CREATE INDEX idx_file_artifact_status ON file_artifact(status);
CREATE INDEX idx_file_artifact_external ON file_artifact(external);

CREATE TABLE ingestion_vault_store (
  vault_id     TEXT PRIMARY KEY,
  sealed_bytes BLOB NOT NULL
);

CREATE TABLE ingestion_credential (
  id           TEXT PRIMARY KEY,
  name         TEXT NOT NULL UNIQUE,
  auth_scheme  TEXT NOT NULL CHECK(auth_scheme IN ('none','bearer','api_key_header','api_key_query','basic')),
  config_json  TEXT NOT NULL,
  vault_id     TEXT,
  created_at   INTEGER NOT NULL,
  updated_at   INTEGER NOT NULL
);

CREATE TABLE ingestion_job (
  id                       TEXT PRIMARY KEY,
  name                     TEXT,
  source_url               TEXT NOT NULL,
  source_method            TEXT NOT NULL CHECK(source_method IN ('GET','POST')),
  source_headers_json      TEXT,
  source_query_params_json TEXT,
  source_body_json         TEXT,
  credential_id            TEXT,
  pagination_json          TEXT,
  payload_format           TEXT NOT NULL CHECK(payload_format IN ('json','jsonl','csv','html')),
  payload_artifact_id      TEXT,
  status                   TEXT NOT NULL CHECK(status IN ('pending','fetching','fetched','mapping','confirmed','writing','completed','failed','cancelled')),
  connection_id            TEXT,
  target_schema            TEXT,
  target_table             TEXT,
  mapping_json             TEXT,
  mapping_hash             TEXT,
  row_count                INTEGER,
  rows_inserted            INTEGER DEFAULT 0,
  bytes_fetched            INTEGER,
  created_at               INTEGER NOT NULL,
  updated_at               INTEGER NOT NULL,
  completed_at             INTEGER,
  error_message            TEXT,
  created_by_kind          TEXT DEFAULT 'ai',
  created_by_session_id    TEXT,
  created_by_label         TEXT,
  heartbeat_at             INTEGER
);
CREATE INDEX idx_ingestion_job_status     ON ingestion_job(status);
CREATE INDEX idx_ingestion_job_connection ON ingestion_job(connection_id);
CREATE INDEX idx_ingestion_job_created    ON ingestion_job(created_at);

CREATE TABLE undo_log (
    id            TEXT PRIMARY KEY,
    session_id    TEXT NOT NULL,
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
