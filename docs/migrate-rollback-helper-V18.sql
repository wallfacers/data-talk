-- docs/migrate-rollback-helper-V18.sql
-- Rollback V18 helper: list dashboard rows for manual backup before down migration.
-- Usage:
--   sqlite3 ~/.data-talk/datatalk.db < docs/migrate-rollback-helper-V18.sql
--   Then manually: cp -r ~/.data-talk/dashboards/ ~/.data-talk/_rollback_$(date +%s)/
--   Delete file_artifact rows with kind='dashboard' OR external=1
--   Only then can V18 down migration proceed

SELECT id, filename, physical_path, created_at
FROM file_artifact
WHERE kind = 'dashboard' OR external = 1;
