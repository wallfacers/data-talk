-- V12__stage_tabs.sql: Persistent Stage Tabs + FTS5 trigram content index

-- Core tab metadata
CREATE TABLE IF NOT EXISTS stage_tabs (
    id              TEXT PRIMARY KEY,
    type            TEXT    NOT NULL,
    scope           TEXT    NOT NULL CHECK (scope IN ('workspace','session')),
    title           TEXT    NOT NULL,
    connection_id   TEXT,
    database_name   TEXT,
    schema_name     TEXT,
    origin_session_id TEXT REFERENCES sessions(id) ON DELETE CASCADE,
    payload_version INTEGER NOT NULL DEFAULT 1,
    pinned          INTEGER NOT NULL DEFAULT 0,
    archived        INTEGER NOT NULL DEFAULT 0,
    archived_at     INTEGER,
    created_at      INTEGER NOT NULL,
    last_touched_at INTEGER NOT NULL,
    CHECK (NOT (scope = 'session' AND origin_session_id IS NULL))
);

-- Payload + content (separated for selective loading)
CREATE TABLE IF NOT EXISTS stage_tab_payload (
    tab_id          TEXT PRIMARY KEY REFERENCES stage_tabs(id) ON DELETE CASCADE,
    payload_json    TEXT    NOT NULL DEFAULT '{}',
    content_text    TEXT    NOT NULL DEFAULT '',
    content_version INTEGER NOT NULL DEFAULT 1,
    updated_at      INTEGER NOT NULL
);

-- FTS5 virtual table with trigram tokenizer for substring/coarse matching
CREATE VIRTUAL TABLE IF NOT EXISTS stage_tab_index USING fts5(
    content_text,
    archived,
    tokenize='trigram',
    content=stage_tab_payload,
    content_rowid=rowid
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_stage_tabs_active     ON stage_tabs(scope, archived, last_touched_at DESC);
CREATE INDEX IF NOT EXISTS idx_stage_tabs_type       ON stage_tabs(type, last_touched_at DESC);
CREATE INDEX IF NOT EXISTS idx_stage_tabs_session    ON stage_tabs(origin_session_id);
CREATE INDEX IF NOT EXISTS idx_stage_tabs_connection ON stage_tabs(connection_id);

-- Triggers: sync FTS on payload changes
CREATE TRIGGER stage_tabs_ai AFTER INSERT ON stage_tab_payload BEGIN
    INSERT INTO stage_tab_index(rowid, content_text, archived)
        SELECT p.rowid, p.content_text, COALESCE(t.archived,0)
        FROM stage_tab_payload p JOIN stage_tabs t ON t.id = p.tab_id
        WHERE p.tab_id = NEW.tab_id;
END;

CREATE TRIGGER stage_tabs_au AFTER UPDATE ON stage_tab_payload BEGIN
    INSERT INTO stage_tab_index(stage_tab_index, rowid, content_text, archived)
        VALUES ('delete', OLD.rowid, OLD.content_text, (SELECT COALESCE(archived,0) FROM stage_tabs WHERE id=OLD.tab_id));
    INSERT INTO stage_tab_index(rowid, content_text, archived)
        SELECT p.rowid, p.content_text, COALESCE(t.archived,0)
        FROM stage_tab_payload p JOIN stage_tabs t ON t.id = p.tab_id
        WHERE p.tab_id = NEW.tab_id;
END;

CREATE TRIGGER stage_tabs_ad AFTER DELETE ON stage_tab_payload BEGIN
    INSERT INTO stage_tab_index(stage_tab_index, rowid, content_text, archived)
        VALUES ('delete', OLD.rowid, OLD.content_text, 0);
END;

-- Triggers: keep FTS archived flag in sync
CREATE TRIGGER stage_tab_payload_aiu AFTER INSERT ON stage_tabs BEGIN
    INSERT OR REPLACE INTO stage_tab_index(rowid, content_text, archived)
        SELECT p.rowid, p.content_text, COALESCE(NEW.archived,0)
        FROM stage_tab_payload p WHERE p.tab_id = NEW.id;
END;

CREATE TRIGGER stage_tab_payload_au AFTER UPDATE ON stage_tabs BEGIN
    INSERT OR REPLACE INTO stage_tab_index(rowid, content_text, archived)
        SELECT p.rowid, p.content_text, COALESCE(NEW.archived,0)
        FROM stage_tab_payload p WHERE p.tab_id = NEW.id;
END;
