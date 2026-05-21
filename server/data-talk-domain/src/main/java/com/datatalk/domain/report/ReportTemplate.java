package com.datatalk.domain.report;

/**
 * ledger skill v0 模板枚举。
 * v0 仅暴露两套：业务月报、问题复盘。新增模板时只追加枚举，不破坏现有 promote 契约。
 */
public enum ReportTemplate {
    MONTHLY_BUSINESS_REVIEW("ledger.monthly-business-review.v1"),
    INCIDENT_POSTMORTEM("ledger.incident-postmortem.v1");

    private final String templateId;

    ReportTemplate(String templateId) {
        this.templateId = templateId;
    }

    public String templateId() {
        return templateId;
    }

    public static ReportTemplate fromTemplateId(String templateId) {
        for (ReportTemplate t : values()) {
            if (t.templateId.equals(templateId)) return t;
        }
        throw new IllegalArgumentException("Unknown report templateId: " + templateId);
    }
}
