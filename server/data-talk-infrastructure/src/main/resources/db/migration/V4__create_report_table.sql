-- ============================================================
-- DataTalk V4 — report table
-- Workspace-scoped report library for the ledger skill flow:
--   * AI emits a frozen report.json via datatalk_promote_report
--   * Server persists metadata + derives HTML (sync) and PDF/MD (async)
-- Schema mirrors `report-library` spec.
-- ============================================================

CREATE TABLE IF NOT EXISTS report (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL,
    group_id TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    title TEXT NOT NULL,
    subtitle TEXT,
    template_id TEXT NOT NULL,
    template_version TEXT NOT NULL,
    accent_color TEXT NOT NULL,
    generated_at INTEGER NOT NULL,
    generated_by_session_id TEXT,
    user_prompt TEXT,
    artifact_paths_json TEXT NOT NULL,
    pdf_status TEXT NOT NULL DEFAULT 'processing'
        CHECK (pdf_status IN ('processing', 'ready', 'failed')),
    md_status TEXT NOT NULL DEFAULT 'processing'
        CHECK (md_status IN ('processing', 'ready', 'failed')),
    pdf_fail_reason TEXT,
    md_fail_reason TEXT
);

CREATE INDEX IF NOT EXISTS idx_report_workspace
    ON report(workspace_id, generated_at DESC);

CREATE INDEX IF NOT EXISTS idx_report_group
    ON report(group_id, version DESC);
