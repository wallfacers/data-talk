package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardDifferTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode dashboard(String template, String theme, String... widgetEntries) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"layout\":{\"engine\":\"free\",\"template\":\"").append(template).append("\"},");
        sb.append("\"theme\":\"").append(theme).append("\",");
        sb.append("\"widgets\":[");
        for (int i = 0; i < widgetEntries.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(widgetEntries[i]);
        }
        sb.append("]}");
        return mapper.readTree(sb.toString());
    }

    private String widget(String id, String slot, String title) {
        return "{\"id\":\"" + id + "\",\"type\":\"chart\",\"slot\":\"" + slot
            + "\",\"title\":\"" + title + "\",\"patternId\":\"generic.echarts-card\",\"options\":{}}";
    }

    @Test
    void templateChange_needsFullRebuild() throws Exception {
        JsonNode old = dashboard("grid-equal", "industry-ecommerce",
            widget("w1", "hero", "Sales"));
        JsonNode updated = dashboard("single-focus", "industry-ecommerce",
            widget("w1", "hero", "Sales"));

        assertThat(DashboardDiffer.needsFullRebuild(old, updated)).isTrue();
    }

    @Test
    void themeChange_needsFullRebuild() throws Exception {
        JsonNode old = dashboard("grid-equal", "industry-ecommerce",
            widget("w1", "hero", "Sales"));
        JsonNode updated = dashboard("grid-equal", "industry-finance",
            widget("w1", "hero", "Sales"));

        assertThat(DashboardDiffer.needsFullRebuild(old, updated)).isTrue();
    }

    @Test
    void widgetCountChange_needsFullRebuild() throws Exception {
        JsonNode old = dashboard("grid-equal", "industry-ecommerce",
            widget("w1", "hero", "Sales"));
        JsonNode updated = dashboard("grid-equal", "industry-ecommerce",
            widget("w1", "hero", "Sales"),
            widget("w2", "sidebar", "Revenue"));

        assertThat(DashboardDiffer.needsFullRebuild(old, updated)).isTrue();
    }

    @Test
    void widgetRemoved_needsFullRebuild() throws Exception {
        JsonNode old = dashboard("grid-equal", "industry-ecommerce",
            widget("w1", "hero", "Sales"));
        JsonNode updated = dashboard("grid-equal", "industry-ecommerce",
            widget("w2", "hero", "Revenue"));

        assertThat(DashboardDiffer.needsFullRebuild(old, updated)).isTrue();
    }

    @Test
    void identicalDashboard_doesNotNeedFullRebuild() throws Exception {
        JsonNode old = dashboard("grid-equal", "industry-ecommerce",
            widget("w1", "hero", "Sales"));
        JsonNode updated = dashboard("grid-equal", "industry-ecommerce",
            widget("w1", "hero", "Sales"));

        assertThat(DashboardDiffer.needsFullRebuild(old, updated)).isFalse();
    }

    @Test
    void onlyWidgetTitleChanged_doesNotNeedFullRebuild() throws Exception {
        JsonNode old = dashboard("grid-equal", "industry-ecommerce",
            widget("w1", "hero", "Sales"));
        JsonNode updated = dashboard("grid-equal", "industry-ecommerce",
            widget("w1", "hero", "Revenue Overview"));

        assertThat(DashboardDiffer.needsFullRebuild(old, updated)).isFalse();
    }

    @Test
    void slotReassignment_needsFullRebuild() throws Exception {
        JsonNode old = dashboard("grid-equal", "industry-ecommerce",
            widget("w1", "hero", "Sales"));
        JsonNode updated = dashboard("grid-equal", "industry-ecommerce",
            widget("w1", "sidebar", "Sales"));

        assertThat(DashboardDiffer.needsFullRebuild(old, updated)).isTrue();
    }

    @Test
    void emptyWidgets_noDifference() throws Exception {
        JsonNode old = dashboard("grid-equal", "industry-ecommerce");
        JsonNode updated = dashboard("grid-equal", "industry-ecommerce");

        assertThat(DashboardDiffer.needsFullRebuild(old, updated)).isFalse();
    }

    @Test
    void multipleWidgetChanges_sameIdsAndSlots_doesNotNeedFullRebuild() throws Exception {
        JsonNode old = dashboard("grid-equal", "industry-ecommerce",
            widget("w1", "hero", "Sales"),
            widget("w2", "sidebar", "Revenue"));
        JsonNode updated = dashboard("grid-equal", "industry-ecommerce",
            widget("w1", "hero", "Sales Updated"),
            widget("w2", "sidebar", "Revenue Updated"));

        assertThat(DashboardDiffer.needsFullRebuild(old, updated)).isFalse();
    }
}
