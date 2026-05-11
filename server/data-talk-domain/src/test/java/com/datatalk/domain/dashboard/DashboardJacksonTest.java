package com.datatalk.domain.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardJacksonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripsMinimalDashboard() throws Exception {
        Dashboard original = new Dashboard(
            2, "dash_xxx", "Sales Dashboard", null, "conn_1",
            "industry-neutral", "bezel", DashboardRefresh.defaults(),
            List.of(),
            List.of(new Widget(
                "chart_w1", WidgetType.CHART,
                new GridPosition(0, 0, 6, 8, null),
                "generic.echarts-card", null,
                List.of(),
                new WidgetQuery(null, "SELECT 1", Map.of()),
                Map.of("title", "test", "echartsOption", Map.of(), "dataMapping", Map.of("rowsAsDataset", true))
            )),
            new GridLayout("grid", 12, 32, 8),
            1L, 1700000000000L, 1700000000000L
        );

        String json = mapper.writeValueAsString(original);
        Dashboard reread = mapper.readValue(json, Dashboard.class);

        assertThat(reread.id()).isEqualTo("dash_xxx");
        assertThat(reread.widgets()).hasSize(1);
        assertThat(reread.widgets().get(0).type()).isEqualTo(WidgetType.CHART);
        assertThat(reread.widgets().get(0).position().w()).isEqualTo(6);
        assertThat(reread.layout().cols()).isEqualTo(12);
        assertThat(reread.theme()).isEqualTo("industry-neutral");
        assertThat(reread.renderer()).isEqualTo("bezel");
        assertThat(reread.widgets().get(0).patternId()).isEqualTo("generic.echarts-card");
    }

    @Test
    void roundTripsDashboardWithParameters() throws Exception {
        ParameterDef param = new ParameterDef("global:date_range", "global", null, "dateRange", "date_range", "last_7d");
        WidgetQuery query = new WidgetQuery("conn_1", "SELECT * FROM orders WHERE date = :dateRange", Map.of("dateRange", "global:date_range"));

        Dashboard original = new Dashboard(
            2, "dash_abc12345", "Orders Dashboard", "Monthly overview", "conn_1",
            "industry-retail", "bezel", DashboardRefresh.defaults(),
            List.of(param),
            List.of(new Widget(
                "chart_w_abc123", WidgetType.KPI,
                new GridPosition(0, 0, 3, 4, null),
                "generic.kpi-card", null,
                List.of(),
                query,
                Map.of("title", "Total Orders")
            )),
            new GridLayout("grid", 12, 32, 8),
            2L, 1700000000000L, 1700000001000L
        );

        String json = mapper.writeValueAsString(original);
        Dashboard reread = mapper.readValue(json, Dashboard.class);

        assertThat(reread.parameters()).hasSize(1);
        assertThat(reread.parameters().get(0).id()).isEqualTo("global:date_range");
        assertThat(reread.widgets().get(0).query().paramRefs()).containsEntry("dateRange", "global:date_range");
        assertThat(reread.version()).isEqualTo(2L);
        assertThat(reread.theme()).isEqualTo("industry-retail");
    }

    @Test
    void nullCollectionsBecomeEmpty() throws Exception {
        Dashboard dash = new Dashboard(2, "dash_test", "T", null, null,
            "industry-neutral", "bezel", DashboardRefresh.defaults(),
            null, null,
            new GridLayout("grid", 12, 32, 8), 1L, 0L, 0L);

        assertThat(dash.parameters()).isEmpty();
        assertThat(dash.widgets()).isEmpty();
    }

    @Test
    void rejectsSchemaVersion1() {
        assertThatThrownBy(() -> new Dashboard(1, "dash_test", "T", null, null,
            "industry-neutral", "bezel", DashboardRefresh.defaults(),
            List.of(), List.of(),
            new GridLayout("grid", 12, 32, 8), 1L, 0L, 0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("schemaVersion must be 2");
    }

    @Test
    void rejectsNonBezelRenderer() {
        assertThatThrownBy(() -> new Dashboard(2, "dash_test", "T", null, null,
            "industry-neutral", "other", DashboardRefresh.defaults(),
            List.of(), List.of(),
            new GridLayout("grid", 12, 32, 8), 1L, 0L, 0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("renderer must be 'bezel'");
    }

    @Test
    void rejectsNonIndustryTheme() {
        assertThatThrownBy(() -> new Dashboard(2, "dash_test", "T", null, null,
            "dark", "bezel", DashboardRefresh.defaults(),
            List.of(), List.of(),
            new GridLayout("grid", 12, 32, 8), 1L, 0L, 0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("theme must start with 'industry-'");
    }

    @Test
    void rejectsInvalidPatternId() {
        assertThatThrownBy(() -> new Widget(
            "chart_w1", WidgetType.CHART,
            new GridPosition(0, 0, 6, 8, null),
            "INVALID", null,
            List.of(),
            new WidgetQuery(null, "SELECT 1", Map.of()),
            Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("patternId must match");
    }
}
