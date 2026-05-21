package com.datatalk.domain.report;

import java.time.Instant;
import java.util.Optional;

/**
 * 报告库实体。workspace 维度，与 session 解耦——session 归档后报告仍存在。
 *
 * <p>artifactPathsJson 是 {@code { "json": "...", "html": "...", "pdf"?: "...", "md"?: "..." }}
 * 的 JSON 字符串；具体派生产物路径由 ReportArtifactService 管理。
 *
 * <p>group_id 是同一份报告所有重新生成版本的共享标识。第一次 promote 时由服务端生成；
 * 后续重新生成时 AI 显式带 groupId，version 自动 +1。
 */
public record Report(
        String id,
        String workspaceId,
        String groupId,
        int version,
        String title,
        String subtitle,
        String templateId,
        String templateVersion,
        String accentColor,
        Instant generatedAt,
        String generatedBySessionId,
        String userPrompt,
        String artifactPathsJson,
        ReportDerivativeStatus pdfStatus,
        ReportDerivativeStatus mdStatus,
        String pdfFailReason,
        String mdFailReason
) {

    public Report {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (workspaceId == null || workspaceId.isBlank()) throw new IllegalArgumentException("workspaceId must not be blank");
        if (groupId == null || groupId.isBlank()) throw new IllegalArgumentException("groupId must not be blank");
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        if (templateId == null || templateId.isBlank()) throw new IllegalArgumentException("templateId must not be blank");
        if (templateVersion == null || templateVersion.isBlank()) throw new IllegalArgumentException("templateVersion must not be blank");
        if (accentColor == null || accentColor.isBlank()) throw new IllegalArgumentException("accentColor must not be blank");
        if (generatedAt == null) throw new IllegalArgumentException("generatedAt must not be null");
        if (artifactPathsJson == null || artifactPathsJson.isBlank()) throw new IllegalArgumentException("artifactPathsJson must not be blank");
        if (pdfStatus == null) throw new IllegalArgumentException("pdfStatus must not be null");
        if (mdStatus == null) throw new IllegalArgumentException("mdStatus must not be null");
    }

    public Optional<String> subtitleOpt() {
        return Optional.ofNullable(subtitle);
    }

    public Optional<String> generatedBySessionIdOpt() {
        return Optional.ofNullable(generatedBySessionId);
    }

    public Optional<String> userPromptOpt() {
        return Optional.ofNullable(userPrompt);
    }

    public Optional<String> pdfFailReasonOpt() {
        return Optional.ofNullable(pdfFailReason);
    }

    public Optional<String> mdFailReasonOpt() {
        return Optional.ofNullable(mdFailReason);
    }
}
