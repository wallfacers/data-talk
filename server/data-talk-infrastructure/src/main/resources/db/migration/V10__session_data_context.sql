CREATE TABLE session_data_contexts (
  session_id TEXT PRIMARY KEY REFERENCES sessions(id) ON DELETE CASCADE,
  connection_id TEXT REFERENCES connections(id),
  connection_name_snapshot TEXT,
  database_name TEXT,
  schema_name TEXT,
  selected_level TEXT,
  updated_at INTEGER NOT NULL
);
