CREATE TABLE connections (
  id TEXT PRIMARY KEY, kind TEXT NOT NULL, host TEXT NOT NULL, port INTEGER NOT NULL,
  database_name TEXT, username TEXT NOT NULL, password_enc BLOB NOT NULL,
  schema_digest TEXT, created_at INTEGER NOT NULL
);
CREATE TABLE sessions (
  id TEXT PRIMARY KEY, connection_id TEXT REFERENCES connections(id), title TEXT NOT NULL,
  has_ever_sent INTEGER NOT NULL DEFAULT 0, opencode_sid TEXT,
  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
);
CREATE TABLE messages (
  id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id),
  role TEXT NOT NULL, parts_json TEXT NOT NULL, created_at INTEGER NOT NULL
);
CREATE INDEX idx_messages_session ON messages(session_id, created_at);
CREATE TABLE artifacts (
  id TEXT NOT NULL, version INTEGER NOT NULL, session_id TEXT NOT NULL REFERENCES sessions(id),
  kind TEXT NOT NULL CHECK(kind IN ('table','chart','erd')), produced_by TEXT NOT NULL,
  payload_ref TEXT NOT NULL, payload_size INTEGER NOT NULL, supersedes_id TEXT,
  supersedes_ver INTEGER, pinned INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL,
  PRIMARY KEY(id, version)
);
CREATE INDEX idx_artifacts_session ON artifacts(session_id, created_at);
CREATE TABLE action_invocations (
  call_id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id),
  action_id TEXT NOT NULL, status TEXT NOT NULL, input_json TEXT NOT NULL,
  output_json TEXT, error_json TEXT, started_at INTEGER NOT NULL, ended_at INTEGER
);
CREATE TABLE events (
  event_id INTEGER NOT NULL, session_id TEXT NOT NULL REFERENCES sessions(id),
  event_type TEXT NOT NULL, payload_json TEXT NOT NULL, ts INTEGER NOT NULL,
  PRIMARY KEY(session_id, event_id)
);
CREATE INDEX idx_events_ts ON events(ts);
CREATE TABLE query_results (
  handle TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id),
  columns_json TEXT NOT NULL, rows_ndjson TEXT NOT NULL, row_count INTEGER NOT NULL,
  created_at INTEGER NOT NULL, ttl_at INTEGER NOT NULL
);
