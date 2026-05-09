-- V19__multimode_and_oceanbase_fields.sql
-- SQLite cannot ADD CHECK on existing tables. Rebuild required.
-- Renamed from V18 to V19: dashboard branch already owns V18__file_artifact_dashboard.sql.
-- Fixed: schema_digest nullable (V1 was TEXT, not TEXT NOT NULL).
-- Fixed: duckdb_read_only → read_only (V17 added read_only, not duckdb_read_only).

DROP TABLE IF EXISTS connection_new;
CREATE TABLE connection_new (
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

    -- new Wave C step 3 columns
    compatibility_mode TEXT NULL,
    oceanbase_tenant TEXT NULL,
    oceanbase_cluster TEXT NULL,

    CHECK (compatibility_mode IS NULL
        OR compatibility_mode IN ('mysql','oracle','pg')),
    CHECK (kind <> 'oceanbase' OR oceanbase_tenant IS NOT NULL),
    CHECK (kind <> 'oceanbase' OR compatibility_mode IS NOT NULL)
);

INSERT INTO connection_new (
    id, name, kind, host, port, database_name, username, password_enc,
    schema_digest, created_at, connect_timeout, last_test_status,
    last_test_at, oracle_service_type, sqlserver_encrypt,
    sqlserver_trust_server_certificate, sqlserver_instance_name,
    read_only,
    compatibility_mode, oceanbase_tenant, oceanbase_cluster
)
SELECT
    id, name, kind, host, port, database_name, username, password_enc,
    schema_digest, created_at, connect_timeout, last_test_status,
    last_test_at, oracle_service_type, sqlserver_encrypt,
    sqlserver_trust_server_certificate, sqlserver_instance_name,
    read_only,
    NULL, NULL, NULL
FROM connections;

PRAGMA foreign_keys = OFF;
DROP TABLE connections;
ALTER TABLE connection_new RENAME TO connections;
PRAGMA foreign_keys = ON;

-- Recreate indexes
CREATE INDEX IF NOT EXISTS idx_connection_kind ON connections(kind);
CREATE INDEX IF NOT EXISTS idx_connection_created_at ON connections(created_at);
CREATE UNIQUE INDEX IF NOT EXISTS idx_connections_name ON connections(name);
