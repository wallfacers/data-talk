PRAGMA foreign_keys=OFF;

-- messages
ALTER TABLE messages RENAME TO messages_old;
CREATE TABLE messages (
  id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  role TEXT NOT NULL, parts_json TEXT NOT NULL, created_at INTEGER NOT NULL
);
INSERT INTO messages SELECT * FROM messages_old;
DROP TABLE messages_old;
CREATE INDEX idx_messages_session ON messages(session_id, created_at);

-- artifacts
ALTER TABLE artifacts RENAME TO artifacts_old;
CREATE TABLE artifacts (
  id TEXT NOT NULL, version INTEGER NOT NULL, session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK(kind IN ('table','chart','erd')), produced_by TEXT NOT NULL,
  payload_ref TEXT NOT NULL, payload_size INTEGER NOT NULL, supersedes_id TEXT,
  supersedes_ver INTEGER, pinned INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL,
  PRIMARY KEY(id, version)
);
INSERT INTO artifacts SELECT * FROM artifacts_old;
DROP TABLE artifacts_old;
CREATE INDEX idx_artifacts_session ON artifacts(session_id, created_at);

-- action_invocations
ALTER TABLE action_invocations RENAME TO action_invocations_old;
CREATE TABLE action_invocations (
  call_id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  action_id TEXT NOT NULL, status TEXT NOT NULL, input_json TEXT NOT NULL,
  output_json TEXT, error_json TEXT, started_at INTEGER NOT NULL, ended_at INTEGER
);
INSERT INTO action_invocations SELECT * FROM action_invocations_old;
DROP TABLE action_invocations_old;

-- events
ALTER TABLE events RENAME TO events_old;
CREATE TABLE events (
  event_id INTEGER NOT NULL, session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  event_type TEXT NOT NULL, payload_json TEXT NOT NULL, ts INTEGER NOT NULL,
  PRIMARY KEY(session_id, event_id)
);
INSERT INTO events SELECT * FROM events_old;
DROP TABLE events_old;
CREATE INDEX idx_events_ts ON events(ts);

-- query_results
ALTER TABLE query_results RENAME TO query_results_old;
CREATE TABLE query_results (
  handle TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  columns_json TEXT NOT NULL, rows_ndjson TEXT NOT NULL, row_count INTEGER NOT NULL,
  created_at INTEGER NOT NULL, ttl_at INTEGER NOT NULL
);
INSERT INTO query_results SELECT * FROM query_results_old;
DROP TABLE query_results_old;

PRAGMA foreign_keys=ON;
