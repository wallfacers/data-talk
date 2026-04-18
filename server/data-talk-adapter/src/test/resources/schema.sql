DROP TABLE IF EXISTS pending_calls;
DROP TABLE IF EXISTS action_invocations;
DROP TABLE IF EXISTS query_results;
DROP TABLE IF EXISTS events;
DROP TABLE IF EXISTS artifacts;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS sessions;
DROP TABLE IF EXISTS connections;
DROP TABLE IF EXISTS ai_model_prefs;
DROP TABLE IF EXISTS ai_user_prefs;

CREATE TABLE connections (
  id TEXT PRIMARY KEY, name TEXT NOT NULL DEFAULT '', kind TEXT NOT NULL, host TEXT NOT NULL, port INTEGER NOT NULL,
  database_name TEXT, username TEXT NOT NULL, password_enc BLOB NOT NULL,
  schema_digest TEXT, created_at INTEGER NOT NULL, connect_timeout INTEGER NOT NULL DEFAULT 3000
);
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
  PRIMARY KEY (id, version)
);
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
