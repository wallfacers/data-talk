-- tools/rollback/V20-helper.sql
-- Manual rollback before reverting V20. Run in this order:
DELETE FROM file_artifact WHERE kind = 'ingestion_payload';
DROP TABLE IF EXISTS ingestion_job;
DROP TABLE IF EXISTS ingestion_credential;
-- After this, reverting V20 (or running a down-migration that restores V18 CHECK) is safe.
