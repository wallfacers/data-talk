package com.datatalk.repository;

import com.datatalk.domain.report.Report;
import com.datatalk.domain.report.ReportDerivativeStatus;

import java.util.List;
import java.util.Optional;

/**
 * 报告库仓储 port。
 *
 * <p>{@link #findGroupLatest(String)} 是报告库列表 UI 的默认查询，按 group 折叠只返回每组最新版本。
 * {@link #findByWorkspaceId(String, String)} 在 groupId 非空时返回该 group 的全部版本（展开模式）。
 */
public interface ReportRepository {

    void save(Report report);

    Optional<Report> findById(String id);

    /**
     * 列表查询。
     * @param workspaceId 必传
     * @param groupId 可选；非空时返回该 group 内全部版本（version desc）；为空时返回每个 group 的最新版本
     */
    List<Report> findByWorkspaceId(String workspaceId, String groupId);

    /**
     * 等价于 {@code findByWorkspaceId(workspaceId, null)}：每个 group 只返最新版本。
     */
    List<Report> findGroupLatest(String workspaceId);

    /**
     * 给定 workspace + groupId 返回该 group 内当前最大 version；不存在时返回 0。
     */
    int currentMaxVersionInGroup(String workspaceId, String groupId);

    /**
     * 返回该 group 内有几个版本（report list 卡片折叠时显示 groupSize 字段用）。
     */
    int countInGroup(String workspaceId, String groupId);

    /**
     * 校验 workspace 下是否存在该 groupId。AI 重新生成时携带 groupId，服务端必须做存在性校验。
     */
    boolean existsGroup(String workspaceId, String groupId);

    void updatePdfStatus(String reportId, ReportDerivativeStatus status, String failReason);

    void updateMdStatus(String reportId, ReportDerivativeStatus status, String failReason);

    void updateArtifactPaths(String reportId, String artifactPathsJson);

    /**
     * 物理删除。级联清理 file artifact 由调用方负责（report 表不直接依赖 FileArtifactRepository）。
     */
    void deleteById(String id);
}
