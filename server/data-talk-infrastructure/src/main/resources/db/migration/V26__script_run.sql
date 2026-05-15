-- Drop old ingestion tables (V20-V23)
DROP TABLE IF EXISTS ingestion_mapping_hash;
DROP TABLE IF EXISTS ingestion_vault_store;
DROP TABLE IF EXISTS ingestion_credential;
DROP TABLE IF EXISTS ingestion_job;

-- Create script_run table
CREATE TABLE script_run (
    id                  VARCHAR(36) PRIMARY KEY,
    script_content      CLOB,
    language            VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    exit_code           INTEGER,
    stdout_text         CLOB,
    connection_id       VARCHAR(36),
    target_table        VARCHAR(256),
    rows_written        INTEGER DEFAULT 0,
    name                VARCHAR(256),
    created_by_kind     VARCHAR(50),
    created_by_session_id VARCHAR(36),
    error_message       CLOB,
    started_at          TIMESTAMP NOT NULL,
    finished_at         TIMESTAMP,
    duration_ms         BIGINT
);

CREATE INDEX idx_script_run_started_at ON script_run(started_at);
