package com.datatalk.domain.dashboard;

/**
 * v3 layout: engine is always "free", template selects one of 6 named layouts
 * defined in pattern-catalog.yaml.
 */
public record DashboardLayout(String engine, String template) {
    public DashboardLayout {
        if (!"free".equals(engine)) {
            throw new IllegalArgumentException("layout.engine must be 'free', got '" + engine + "'");
        }
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("layout.template is required");
        }
    }
}
