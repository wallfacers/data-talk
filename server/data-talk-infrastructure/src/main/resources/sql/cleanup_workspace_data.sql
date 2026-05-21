-- Cleanup all workspace-related data
-- Tables: stage_tabs, stage_tab_payload, stage_tab_index (FTS5 auto-cleanup), file_artifacts (workspace scope)

-- 1. Delete all workspace-scoped file artifacts
DELETE FROM file_artifacts WHERE scope = 'WORKSPACE';

-- 2. Delete all tab payloads (FTS5 virtual table rows auto-deleted via trigger)
DELETE FROM stage_tab_payload;

-- 3. Delete all tab metadata (triggers cascade to stage_tab_index)
DELETE FROM stage_tabs;

-- Verify counts (all should be 0)
SELECT 'stage_tabs' AS table_name, COUNT(*) AS remaining_rows FROM stage_tabs
UNION ALL
SELECT 'stage_tab_payload', COUNT(*) FROM stage_tab_payload
UNION ALL
SELECT 'file_artifacts (workspace)', COUNT(*) FROM file_artifacts WHERE scope = 'WORKSPACE';
