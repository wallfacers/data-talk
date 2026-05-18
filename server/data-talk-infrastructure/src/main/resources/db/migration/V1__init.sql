-- ============================================================
-- DataTalk v0.0.1 — Complete Initial Schema
-- SQLite, Flyway-style migration V1 (folded from V1-V28)
-- ============================================================

-- ─── 1. Data Source Connections ─────────────────────────────
-- Final state after V19__multimode_and_oceanbase_fields.sql
-- (V1 create → V5 connect_timeout → V6 test_status → V7 name
--  → V15 oracle → V16 sqlserver → V17 read_only → V19 rebuild)

CREATE TABLE connections (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    kind TEXT NOT NULL,
    host TEXT NOT NULL,
    port INTEGER NOT NULL,
    database_name TEXT,
    username TEXT,
    password_enc BLOB NOT NULL,
    schema_digest TEXT,
    created_at INTEGER NOT NULL,
    connect_timeout INTEGER NOT NULL DEFAULT 3000,
    last_test_status TEXT,
    last_test_at INTEGER,
    oracle_service_type TEXT,
    sqlserver_encrypt INTEGER NOT NULL DEFAULT 1,
    sqlserver_trust_server_certificate INTEGER NOT NULL DEFAULT 1,
    sqlserver_instance_name TEXT,
    read_only INTEGER NOT NULL DEFAULT 0,
    compatibility_mode TEXT,
    oceanbase_tenant TEXT,
    oceanbase_cluster TEXT,
    CHECK (compatibility_mode IS NULL OR compatibility_mode IN ('mysql','oracle','pg')),
    CHECK (kind <> 'oceanbase' OR oceanbase_tenant IS NOT NULL),
    CHECK (kind <> 'oceanbase' OR compatibility_mode IS NOT NULL)
);

CREATE INDEX idx_connection_kind ON connections(kind);
CREATE INDEX idx_connection_created_at ON connections(created_at);
CREATE UNIQUE INDEX idx_connections_name ON connections(name);

-- ─── 2. Sessions ────────────────────────────────────────────
-- V1 create → V4 title_locked → V3 CASCADE

CREATE TABLE sessions (
    id TEXT PRIMARY KEY,
    connection_id TEXT REFERENCES connections(id),
    title TEXT NOT NULL,
    title_locked INTEGER NOT NULL DEFAULT 0,
    has_ever_sent INTEGER NOT NULL DEFAULT 0,
    opencode_sid TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- ─── 3. Session Data Context ────────────────────────────────

CREATE TABLE session_data_contexts (
    session_id TEXT PRIMARY KEY REFERENCES sessions(id) ON DELETE CASCADE,
    connection_id TEXT REFERENCES connections(id),
    connection_name_snapshot TEXT,
    database_name TEXT,
    schema_name TEXT,
    selected_level TEXT,
    updated_at INTEGER NOT NULL
);

-- ─── 4. Artifacts (chart/table/ERD) ─────────────────────────
-- V1 create → V3 CASCADE → V11 origin columns

CREATE TABLE artifacts (
    id TEXT NOT NULL,
    version INTEGER NOT NULL,
    session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK(kind IN ('table','chart','erd')),
    produced_by TEXT NOT NULL,
    payload_ref TEXT NOT NULL,
    payload_size INTEGER NOT NULL,
    supersedes_id TEXT,
    supersedes_ver INTEGER,
    pinned INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    origin_message_id TEXT,
    origin_part_id TEXT,
    PRIMARY KEY(id, version)
);

CREATE INDEX idx_artifacts_session ON artifacts(session_id, created_at);
CREATE INDEX idx_artifacts_origin ON artifacts(session_id, origin_message_id, origin_part_id);

-- ─── 5. Action Invocations ──────────────────────────────────
-- V1 create → V3 CASCADE

CREATE TABLE action_invocations (
    call_id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    action_id TEXT NOT NULL,
    status TEXT NOT NULL,
    input_json TEXT NOT NULL,
    output_json TEXT,
    error_json TEXT,
    started_at INTEGER NOT NULL,
    ended_at INTEGER
);

-- ─── 6. Events ──────────────────────────────────────────────
-- V1 create → V3 CASCADE

CREATE TABLE events (
    event_id INTEGER NOT NULL,
    session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    payload_json TEXT NOT NULL,
    ts INTEGER NOT NULL,
    PRIMARY KEY(session_id, event_id)
);

CREATE INDEX idx_events_ts ON events(ts);

-- ─── 7. Query Results Cache ─────────────────────────────────
-- V1 create → V3 CASCADE

CREATE TABLE query_results (
    handle TEXT PRIMARY KEY,
    session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    columns_json TEXT NOT NULL,
    rows_ndjson TEXT NOT NULL,
    row_count INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    ttl_at INTEGER NOT NULL
);

-- ─── 8. AI Preferences ──────────────────────────────────────

CREATE TABLE ai_user_prefs (
    id TEXT PRIMARY KEY,
    current_model TEXT,
    updated_at INTEGER NOT NULL
);

CREATE TABLE ai_model_prefs (
    provider_id TEXT NOT NULL,
    model_id TEXT NOT NULL,
    enabled INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (provider_id, model_id)
);

-- ─── 9. Synthetic Session Messages ──────────────────────────

CREATE TABLE synthetic_session_messages (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    kind TEXT NOT NULL,
    text TEXT NOT NULL,
    metadata_json TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX idx_synthetic_session_messages_session_created
    ON synthetic_session_messages(session_id, created_at, id);

-- ─── 10. Stage Tabs ─────────────────────────────────────────
-- V12 create → V13 rebuild (drop scope, FK SET NULL, rebuild FTS)

CREATE TABLE stage_tabs (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    connection_id TEXT,
    database_name TEXT,
    schema_name TEXT,
    origin_session_id TEXT,
    payload_version INTEGER NOT NULL DEFAULT 1,
    pinned INTEGER NOT NULL DEFAULT 0,
    archived INTEGER NOT NULL DEFAULT 0,
    archived_at INTEGER,
    created_at INTEGER NOT NULL,
    last_touched_at INTEGER NOT NULL,
    FOREIGN KEY (origin_session_id) REFERENCES sessions(id) ON DELETE SET NULL
);

CREATE INDEX idx_stage_tabs_active ON stage_tabs(archived, last_touched_at DESC) WHERE archived = 0;
CREATE INDEX idx_stage_tabs_type ON stage_tabs(type, archived);
CREATE INDEX idx_stage_tabs_origin ON stage_tabs(origin_session_id) WHERE origin_session_id IS NOT NULL;
CREATE INDEX idx_stage_tabs_connection ON stage_tabs(connection_id) WHERE connection_id IS NOT NULL;

CREATE TABLE stage_tab_payload (
    tab_id TEXT PRIMARY KEY,
    payload_json TEXT NOT NULL,
    content_text TEXT NOT NULL,
    content_version INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (tab_id) REFERENCES stage_tabs(id) ON DELETE CASCADE
);

CREATE VIRTUAL TABLE stage_tab_index USING fts5(
    title,
    content,
    type UNINDEXED,
    archived UNINDEXED,
    tokenize = 'trigram'
);

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

-- ─── 11. File Artifacts ─────────────────────────────────────
-- V14 create → V18 rebuild (+dashboard, external)
--          → V20 rebuild (+ingestion_payload)

CREATE TABLE file_artifact (
    id TEXT PRIMARY KEY,
    scope TEXT NOT NULL CHECK(scope IN ('session','workspace')),
    status TEXT NOT NULL CHECK(status IN ('temporary','candidate','archived','discarded')),
    kind TEXT NOT NULL CHECK(kind IN ('report','er_diagram','sql_script','dataset','dashboard','ingestion_payload','other')),
    session_id TEXT,
    connection_id TEXT,
    filename TEXT NOT NULL,
    physical_path TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    mime_type TEXT,
    title TEXT,
    summary TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    archived_at INTEGER,
    metadata_json TEXT,
    external INTEGER NOT NULL DEFAULT 0 CHECK(external IN (0, 1))
);

CREATE INDEX idx_file_artifact_session ON file_artifact(session_id) WHERE scope = 'session';
CREATE INDEX idx_file_artifact_connection ON file_artifact(connection_id) WHERE scope = 'workspace';
CREATE INDEX idx_file_artifact_status ON file_artifact(status);
CREATE INDEX idx_file_artifact_external ON file_artifact(external) WHERE external = 1;

-- ─── 12. Data Ingestion ─────────────────────────────────────
-- V20 create credential + job → V20 vault_store
-- V22 job ADD mapping_hash
-- V23 job ADD name, created_by_*, heartbeat_at

CREATE TABLE ingestion_credential (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    auth_scheme TEXT NOT NULL CHECK(auth_scheme IN ('none','bearer','api_key_header','api_key_query','basic')),
    config_json TEXT NOT NULL,
    vault_id TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE ingestion_vault_store (
    vault_id TEXT PRIMARY KEY,
    sealed_bytes BLOB NOT NULL
);

CREATE TABLE ingestion_job (
    id TEXT PRIMARY KEY,
    source_url TEXT NOT NULL,
    source_method TEXT NOT NULL CHECK(source_method IN ('GET','POST')),
    source_headers_json TEXT,
    source_query_params_json TEXT,
    source_body_json TEXT,
    credential_id TEXT,
    pagination_json TEXT,
    payload_format TEXT NOT NULL CHECK(payload_format IN ('json','jsonl','csv','html')),
    payload_artifact_id TEXT,
    status TEXT NOT NULL CHECK(status IN ('pending','fetching','fetched','mapping','confirmed','writing','completed','failed','cancelled')),
    connection_id TEXT,
    target_schema TEXT,
    target_table TEXT,
    mapping_json TEXT,
    mapping_hash TEXT,
    row_count INTEGER,
    rows_inserted INTEGER DEFAULT 0,
    bytes_fetched INTEGER,
    name TEXT,
    created_by_kind TEXT DEFAULT 'ai',
    created_by_session_id TEXT,
    created_by_label TEXT,
    heartbeat_at INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    completed_at INTEGER,
    error_message TEXT
);

CREATE INDEX idx_ingestion_job_status ON ingestion_job(status);
CREATE INDEX idx_ingestion_job_connection ON ingestion_job(connection_id) WHERE connection_id IS NOT NULL;
CREATE INDEX idx_ingestion_job_created ON ingestion_job(created_at);
CREATE INDEX idx_ingestion_job_heartbeat ON ingestion_job(status, heartbeat_at) WHERE status IN ('fetching','writing');

-- ─── 13. User Preferences ───────────────────────────────────

CREATE TABLE user_preferences (
    id TEXT PRIMARY KEY,
    timezone TEXT NOT NULL DEFAULT 'UTC',
    date_format TEXT NOT NULL DEFAULT 'yyyy-MM-dd HH:mm:ss',
    updated_at INTEGER NOT NULL
);

-- ─── 14. Undo Log ───────────────────────────────────────────
-- V25 create → V27 connection index

CREATE TABLE undo_log (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    connection_id TEXT NOT NULL,
    database_name TEXT,
    schema_name TEXT,
    table_name TEXT NOT NULL,
    operation TEXT NOT NULL CHECK(operation IN ('INSERT', 'UPDATE', 'DELETE')),
    original_sql TEXT NOT NULL,
    inverse_sql TEXT,
    before_state TEXT,
    affected_rows INTEGER NOT NULL DEFAULT 0,
    undoable INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending', 'active', 'undone', 'expired')),
    expires_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    undone_at INTEGER
);

CREATE INDEX idx_undo_log_session ON undo_log(session_id, status);
CREATE INDEX idx_undo_log_conn_status_created ON undo_log(connection_id, status, created_at DESC);

-- ─── 15. Script Run ─────────────────────────────────────────

CREATE TABLE script_run (
    id VARCHAR(36) PRIMARY KEY,
    script_content CLOB,
    language VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    exit_code INTEGER,
    stdout_text CLOB,
    connection_id VARCHAR(36),
    target_table VARCHAR(256),
    rows_written INTEGER DEFAULT 0,
    name VARCHAR(256),
    created_by_kind VARCHAR(50),
    created_by_session_id VARCHAR(36),
    error_message CLOB,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    duration_ms BIGINT
);

CREATE INDEX idx_script_run_started_at ON script_run(started_at);

-- ─── 16. Uploaded Files ─────────────────────────────────────

CREATE TABLE uploaded_file (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    filename TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    physical_path TEXT NOT NULL,
    analysis_json TEXT,
    created_at INTEGER NOT NULL
);

CREATE INDEX idx_uploaded_file_created_at ON uploaded_file(created_at);

-- ─── 17. User Message Attachments ─────────────────────────────
-- Moved to V2__user_message_attachments.sql (BUG-0061 fix) so the
-- table can be applied independently of V1 checksum state.

-- ─── Seed Data ──────────────────────────────────────────────

INSERT INTO ai_user_prefs(id, current_model, updated_at)
VALUES ('default', NULL, strftime('%s','now')*1000);

INSERT OR IGNORE INTO user_preferences(id, timezone, date_format, updated_at)
VALUES ('default', 'UTC', 'yyyy-MM-dd HH:mm:ss', unixepoch());
