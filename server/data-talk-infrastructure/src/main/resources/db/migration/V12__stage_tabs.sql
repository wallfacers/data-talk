-- V12__stage_tabs.sql: Persistent Stage Tabs + FTS5 trigram content index

CREATE TABLE IF NOT EXISTS stage_tabs (
    id                 TEXT PRIMARY KEY,
    type               TEXT NOT NULL,
    scope              TEXT NOT NULL CHECK (scope IN ('workspace','session')),
    title              TEXT NOT NULL,
    connection_id      TEXT,
    database_name      TEXT,
    schema_name        TEXT,
    origin_session_id  TEXT,
    payload_version    INTEGER NOT NULL DEFAULT 1,
    pinned             INTEGER NOT NULL DEFAULT 0,
    archived           INTEGER NOT NULL DEFAULT 0,
    archived_at        INTEGER,
    created_at         INTEGER NOT NULL,
    last_touched_at    INTEGER NOT NULL,

    FOREIGN KEY (origin_session_id) REFERENCES sessions(id) ON DELETE CASCADE,
    CHECK (scope = 'workspace' OR origin_session_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_stage_tabs_active
    ON stage_tabs(archived, last_touched_at DESC) WHERE archived = 0;
CREATE INDEX IF NOT EXISTS idx_stage_tabs_type
    ON stage_tabs(type, archived);
CREATE INDEX IF NOT EXISTS idx_stage_tabs_session
    ON stage_tabs(origin_session_id) WHERE origin_session_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_stage_tabs_connection
    ON stage_tabs(connection_id) WHERE connection_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS stage_tab_payload (
    tab_id          TEXT PRIMARY KEY,
    payload_json    TEXT NOT NULL,
    content_text    TEXT NOT NULL,
    content_version INTEGER NOT NULL,
    updated_at      INTEGER NOT NULL,
    FOREIGN KEY (tab_id) REFERENCES stage_tabs(id) ON DELETE CASCADE
);

CREATE VIRTUAL TABLE IF NOT EXISTS stage_tab_index USING fts5(
    title,
    content,
    type      UNINDEXED,
    archived  UNINDEXED,
    tokenize  = 'trigram'
);

CREATE TRIGGER IF NOT EXISTS stage_tabs_ai AFTER INSERT ON stage_tabs BEGIN
    INSERT INTO stage_tab_index(rowid, title, content, type, archived)
        VALUES (NEW.rowid, NEW.title, '', NEW.type, NEW.archived);
END;

CREATE TRIGGER IF NOT EXISTS stage_tabs_au AFTER UPDATE OF title, archived ON stage_tabs BEGIN
    UPDATE stage_tab_index
        SET title = NEW.title, archived = NEW.archived
        WHERE rowid = NEW.rowid;
END;

CREATE TRIGGER IF NOT EXISTS stage_tabs_ad AFTER DELETE ON stage_tabs BEGIN
    DELETE FROM stage_tab_index WHERE rowid = OLD.rowid;
END;

CREATE TRIGGER IF NOT EXISTS stage_tab_payload_aiu AFTER INSERT ON stage_tab_payload BEGIN
    UPDATE stage_tab_index
        SET content = NEW.content_text
        WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = NEW.tab_id);
END;

CREATE TRIGGER IF NOT EXISTS stage_tab_payload_au AFTER UPDATE OF content_text ON stage_tab_payload BEGIN
    UPDATE stage_tab_index
        SET content = NEW.content_text
        WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = NEW.tab_id);
END;

CREATE TRIGGER IF NOT EXISTS stage_tab_payload_ad AFTER DELETE ON stage_tab_payload BEGIN
    UPDATE stage_tab_index
        SET content = ''
        WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = OLD.tab_id);
END;
