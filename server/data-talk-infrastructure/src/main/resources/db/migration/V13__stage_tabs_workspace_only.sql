-- V13__stage_tabs_workspace_only.sql
-- Drop scope column, change FK ON DELETE CASCADE -> SET NULL, rebuild FTS rowid mapping.
-- Per spec docs/product-specs/2026-04-28-shared-stage-workbench-design.md sec 4.4.

PRAGMA foreign_keys = OFF;

-- 0) day-1 backup table (legal SQLite syntax: CREATE TABLE ... AS SELECT)
CREATE TABLE IF NOT EXISTS stage_tabs_backup_v13_pre AS SELECT * FROM stage_tabs;

-- 1) drop V12 triggers (DROP TABLE clears them too; explicit for readability)
DROP TRIGGER IF EXISTS stage_tabs_ai;
DROP TRIGGER IF EXISTS stage_tabs_au;
DROP TRIGGER IF EXISTS stage_tabs_ad;
DROP TRIGGER IF EXISTS stage_tab_payload_aiu;
DROP TRIGGER IF EXISTS stage_tab_payload_au;
DROP TRIGGER IF EXISTS stage_tab_payload_ad;

-- 2) rebuild stage_tabs without scope; FK SET NULL
CREATE TABLE stage_tabs_new (
  id                 TEXT PRIMARY KEY,
  type               TEXT NOT NULL,
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
  FOREIGN KEY (origin_session_id) REFERENCES sessions(id) ON DELETE SET NULL
);

INSERT INTO stage_tabs_new (
  id, type, title, connection_id, database_name, schema_name,
  origin_session_id, payload_version, pinned, archived, archived_at, created_at, last_touched_at
)
SELECT
  id, type, title, connection_id, database_name, schema_name,
  origin_session_id, payload_version, pinned, archived, archived_at, created_at, last_touched_at
FROM stage_tabs;

DROP TABLE stage_tabs;
ALTER TABLE stage_tabs_new RENAME TO stage_tabs;

-- 3) rebuild indexes
CREATE INDEX idx_stage_tabs_active     ON stage_tabs(archived, last_touched_at DESC) WHERE archived = 0;
CREATE INDEX idx_stage_tabs_type       ON stage_tabs(type, archived);
CREATE INDEX idx_stage_tabs_origin     ON stage_tabs(origin_session_id) WHERE origin_session_id IS NOT NULL;
CREATE INDEX idx_stage_tabs_connection ON stage_tabs(connection_id) WHERE connection_id IS NOT NULL;

-- 4) rebuild FTS row mapping (rowid changed after RENAME)
DELETE FROM stage_tab_index;
INSERT INTO stage_tab_index(rowid, title, content, type, archived)
  SELECT t.rowid,
         t.title,
         COALESCE(p.content_text, ''),
         t.type,
         t.archived
  FROM stage_tabs t
  LEFT JOIN stage_tab_payload p ON p.tab_id = t.id;

-- 5) recreate 6 triggers (no scope column)
CREATE TRIGGER stage_tabs_ai AFTER INSERT ON stage_tabs BEGIN
  INSERT INTO stage_tab_index(rowid, title, content, type, archived)
    VALUES (NEW.rowid, NEW.title, '', NEW.type, NEW.archived);
END;

CREATE TRIGGER stage_tabs_au AFTER UPDATE OF title, archived ON stage_tabs BEGIN
  UPDATE stage_tab_index
    SET title = NEW.title, archived = NEW.archived
    WHERE rowid = NEW.rowid;
END;

CREATE TRIGGER stage_tabs_ad AFTER DELETE ON stage_tabs BEGIN
  DELETE FROM stage_tab_index WHERE rowid = OLD.rowid;
END;

CREATE TRIGGER stage_tab_payload_aiu AFTER INSERT ON stage_tab_payload BEGIN
  UPDATE stage_tab_index
    SET content = NEW.content_text
    WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = NEW.tab_id);
END;

CREATE TRIGGER stage_tab_payload_au AFTER UPDATE OF content_text ON stage_tab_payload BEGIN
  UPDATE stage_tab_index
    SET content = NEW.content_text
    WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = NEW.tab_id);
END;

CREATE TRIGGER stage_tab_payload_ad AFTER DELETE ON stage_tab_payload BEGIN
  UPDATE stage_tab_index
    SET content = ''
    WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = OLD.tab_id);
END;

PRAGMA foreign_keys = ON;
