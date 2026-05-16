CREATE TABLE uploaded_file (
    id              TEXT PRIMARY KEY,
    session_id      TEXT NOT NULL,
    filename        TEXT NOT NULL,
    mime_type       TEXT NOT NULL,
    size_bytes      INTEGER NOT NULL,
    physical_path   TEXT NOT NULL,
    analysis_json   TEXT,
    created_at      INTEGER NOT NULL
);

CREATE INDEX idx_uploaded_file_created_at ON uploaded_file(created_at);
