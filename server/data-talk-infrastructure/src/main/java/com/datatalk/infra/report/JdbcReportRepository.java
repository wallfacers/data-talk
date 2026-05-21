package com.datatalk.infra.report;

import com.datatalk.domain.report.Report;
import com.datatalk.domain.report.ReportDerivativeStatus;
import com.datatalk.repository.ReportRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC 仓储 — 与 SQLite report 表对接（V4 migration）。
 *
 * <p>findGroupLatest 实现 group_id 折叠：每个 group 取最新版本（version desc）。
 * findByWorkspaceId(workspaceId, groupId) 在 groupId 非空时按 version desc 展开。
 */
@Repository
public class JdbcReportRepository implements ReportRepository {

    private static final String COLS = """
            id, workspace_id, group_id, version, title, subtitle, template_id, template_version,
            accent_color, generated_at, generated_by_session_id, user_prompt, artifact_paths_json,
            pdf_status, md_status, pdf_fail_reason, md_fail_reason
            """;

    private final JdbcTemplate jdbc;

    public JdbcReportRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Report r) {
        jdbc.update(
                "INSERT INTO report (" + COLS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                r.id(),
                r.workspaceId(),
                r.groupId(),
                r.version(),
                r.title(),
                r.subtitle(),
                r.templateId(),
                r.templateVersion(),
                r.accentColor(),
                r.generatedAt().toEpochMilli(),
                r.generatedBySessionId(),
                r.userPrompt(),
                r.artifactPathsJson(),
                r.pdfStatus().dbValue(),
                r.mdStatus().dbValue(),
                r.pdfFailReason(),
                r.mdFailReason());
    }

    @Override
    public Optional<Report> findById(String id) {
        var rows = jdbc.query("SELECT " + COLS + " FROM report WHERE id = ?", mapper(), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Report> findByWorkspaceId(String workspaceId, String groupId) {
        if (workspaceId != null && !workspaceId.isBlank()) {
            if (groupId != null && !groupId.isBlank()) {
                return jdbc.query(
                        "SELECT " + COLS + " FROM report WHERE workspace_id = ? AND group_id = ? "
                                + "ORDER BY version DESC",
                        mapper(),
                        workspaceId, groupId);
            }
            return findGroupLatest(workspaceId);
        }
        // workspaceId 为空时返回所有报表
        if (groupId != null && !groupId.isBlank()) {
            return jdbc.query(
                    "SELECT " + COLS + " FROM report WHERE group_id = ? "
                            + "ORDER BY version DESC",
                    mapper(),
                    groupId);
        }
        return findAllGroupLatest();
    }

    @Override
    public List<Report> findGroupLatest(String workspaceId) {
        // 每 group 取 version 最大的那行，再按 generated_at desc 排序
        String sql = "SELECT " + COLS + " FROM report r "
                + "WHERE workspace_id = ? "
                + "  AND version = (SELECT MAX(version) FROM report r2 "
                + "                  WHERE r2.workspace_id = r.workspace_id AND r2.group_id = r.group_id) "
                + "ORDER BY generated_at DESC";
        return jdbc.query(sql, mapper(), workspaceId);
    }

    @Override
    public List<Report> findAllGroupLatest() {
        String sql = "SELECT " + COLS + " FROM report r "
                + "WHERE version = (SELECT MAX(version) FROM report r2 "
                + "                  WHERE r2.group_id = r.group_id) "
                + "ORDER BY generated_at DESC";
        return jdbc.query(sql, mapper());
    }

    @Override
    public int currentMaxVersionInGroup(String workspaceId, String groupId) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM report WHERE workspace_id = ? AND group_id = ?",
                Integer.class, workspaceId, groupId);
        return max == null ? 0 : max;
    }

    @Override
    public int countInGroup(String workspaceId, String groupId) {
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report WHERE workspace_id = ? AND group_id = ?",
                Integer.class, workspaceId, groupId);
        return cnt == null ? 0 : cnt;
    }

    @Override
    public boolean existsGroup(String workspaceId, String groupId) {
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report WHERE workspace_id = ? AND group_id = ?",
                Integer.class, workspaceId, groupId);
        return cnt != null && cnt > 0;
    }

    @Override
    public void updatePdfStatus(String reportId, ReportDerivativeStatus status, String failReason) {
        jdbc.update(
                "UPDATE report SET pdf_status = ?, pdf_fail_reason = ? WHERE id = ?",
                status.dbValue(), failReason, reportId);
    }

    @Override
    public void updateMdStatus(String reportId, ReportDerivativeStatus status, String failReason) {
        jdbc.update(
                "UPDATE report SET md_status = ?, md_fail_reason = ? WHERE id = ?",
                status.dbValue(), failReason, reportId);
    }

    @Override
    public void updateArtifactPaths(String reportId, String artifactPathsJson) {
        jdbc.update(
                "UPDATE report SET artifact_paths_json = ? WHERE id = ?",
                artifactPathsJson, reportId);
    }

    @Override
    public void deleteById(String id) {
        jdbc.update("DELETE FROM report WHERE id = ?", id);
    }

    @Override
    public List<String> deleteByGroupId(String workspaceId, String groupId) {
        List<String> ids = jdbc.queryForList(
                "SELECT id FROM report WHERE workspace_id = ? AND group_id = ?",
                String.class, workspaceId, groupId);
        if (!ids.isEmpty()) {
            jdbc.update("DELETE FROM report WHERE workspace_id = ? AND group_id = ?",
                    workspaceId, groupId);
        }
        return ids;
    }

    private RowMapper<Report> mapper() {
        return (ResultSet rs, int idx) -> new Report(
                rs.getString("id"),
                rs.getString("workspace_id"),
                rs.getString("group_id"),
                rs.getInt("version"),
                rs.getString("title"),
                rs.getString("subtitle"),
                rs.getString("template_id"),
                rs.getString("template_version"),
                rs.getString("accent_color"),
                Instant.ofEpochMilli(rs.getLong("generated_at")),
                rs.getString("generated_by_session_id"),
                rs.getString("user_prompt"),
                rs.getString("artifact_paths_json"),
                ReportDerivativeStatus.fromDb(rs.getString("pdf_status")),
                ReportDerivativeStatus.fromDb(rs.getString("md_status")),
                rs.getString("pdf_fail_reason"),
                rs.getString("md_fail_reason"));
    }
}
