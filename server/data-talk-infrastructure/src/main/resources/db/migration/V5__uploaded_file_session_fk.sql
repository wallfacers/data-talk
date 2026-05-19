-- Add FOREIGN KEY on uploaded_file.session_id referencing sessions(id) ON DELETE SET NULL.
-- SQLite does not support ALTER TABLE ADD CONSTRAINT, so we rebuild the table.

-- 1. Clean orphan rows whose session_id does not reference an existing session
DELETE FROM uploaded_file
WHERE session_id NOT IN (SELECT id FROM sessions);

-- 2. Create new table with the FK constraint (session_id now nullable for SET NULL)
CREATE TABLE uploaded_file_new (
    id TEXT PRIMARY KEY,
    session_id TEXT,
    filename TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    physical_path TEXT NOT NULL,
    analysis_json TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE SET NULL
);

-- 3. Copy clean data into the new table
INSERT INTO uploaded_file_new
SELECT * FROM uploaded_file;

-- 4. Drop the old table
DROP TABLE uploaded_file;

-- 5. Rename new table to uploaded_file
ALTER TABLE uploaded_file_new RENAME TO uploaded_file;

-- 6. Recreate the index
CREATE INDEX idx_uploaded_file_created_at ON uploaded_file(created_at);
