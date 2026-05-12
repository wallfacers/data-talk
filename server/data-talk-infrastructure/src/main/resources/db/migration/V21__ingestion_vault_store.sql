-- V21__ingestion_vault_store.sql
-- Stores sealed (AES-GCM encrypted) secret blobs keyed by vault_id.
-- Referenced by ingestion_credential.vault_id.

CREATE TABLE ingestion_vault_store (
  vault_id     TEXT PRIMARY KEY,
  sealed_bytes BLOB NOT NULL
);
