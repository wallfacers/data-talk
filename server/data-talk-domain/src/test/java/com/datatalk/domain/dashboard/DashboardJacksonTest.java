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
            3, "dash_xxx", "Sales Dashboard", null, "conn_1",
            "industry-ecommerce", "bezel", DashboardRefresh.defaults(),
            List.of(),
            List.of(new Widget(
                "chart_w_sales01", "chart", "hero", "Sales Trend",
                "generic.echarts-card",
                new ChartSemantics("line", "warm", null, true, true, null, null, null, Map.of()),
                null,
                List.of(),
                new WidgetQuery(null, null, null, "SELECT 1", Map.of()),
                Map.of()
            )),
            new DashboardLayout("free", "single-focus"),
            1L, 1700000000000L, 1700000000000L
        );

        String json = mapper.writeValueAsString(original);
        Dashboard reread = mapper.readValue(json, Dashboard.class);

        assertThat(reread.id()).isEqualTo("dash_xxx");
        assertThat(reread.widgets()).hasSize(1);
        assertThat(reread.widgets().get(0).type()).isEqualTo("chart");
        assertThat(reread.widgets().get(0).slot()).isEqualTo("hero");
        assertThat(reread.layout().template()).isEqualTo("single-focus");
        assertThat(reread.theme()).isEqualTo("industry-ecommerce");
    }

    @Test
    void roundTripsDashboardWithParameters() throws Exception {
        ParameterDef param = new ParameterDef("global:date_range", "global", null, "dateRange", "date_range", "last_7d");
        WidgetQuery query = new WidgetQuery("conn_1", null, null, "SELECT * FROM orders WHERE date = :dateRange", Map.of("dateRange", "global:date_range"));

        Dashboard original = new Dashboard(
            3, "dash_abc12345", "Orders Dashboard", "Monthly overview", "conn_1",
            "industry-ecommerce", "bezel", DashboardRefresh.defaults(),
            List.of(param),
            List.of(new Widget(
                "kpi_w_total01", "kpi", "kpi-bar", "Total Orders",
                "generic.kpi-tile", null,
                null,
                List.of(),
                query,
                Map.of()
            )),
            new DashboardLayout("free", "top-kpi-bottom-charts"),
            2L, 1700000000000L, 1700000001000L
        );

        String json = mapper.writeValueAsString(original);
        Dashboard reread = mapper.readValue(json, Dashboard.class);

        assertThat(reread.parameters()).hasSize(1);
        assertThat(reread.parameters().get(0).id()).isEqualTo("global:date_range");
        assertThat(reread.widgets().get(0).query().paramRefs()).containsEntry("dateRange", "global:date_range");
        assertThat(reread.version()).isEqualTo(2L);
    }

    @Test
    void nullCollectionsBecomeEmpty() {
        Dashboard dash = new Dashboard(3, "dash_test", "T", null, null,
            "industry-ecommerce", "bezel", DashboardRefresh.defaults(),
            null, null,
            new DashboardLayout("free", "grid-equal"), 1L, 0L, 0L);

        assertThat(dash.parameters()).isEmpty();
        assertThat(dash.widgets()).isEmpty();
    }

    @Test
    void rejectsSchemaVersion2() {
        assertThatThrownBy(() -> new Dashboard(2, "dash_test", "T", null, null,
            "industry-ecommerce", "bezel", DashboardRefresh.defaults(),
            List.of(), List.of(),
            new DashboardLayout("free", "grid-equal"), 1L, 0L, 0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("schemaVersion must be 3");
    }

    @Test
    void rejectsNonBezelRenderer() {
        assertThatThrownBy(() -> new Dashboard(3, "dash_test", "T", null, null,
            "industry-ecommerce", "other", DashboardRefresh.defaults(),
            List.of(), List.of(),
            new DashboardLayout("free", "grid-equal"), 1L, 0L, 0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("renderer must be 'bezel'");
    }

    @Test
    void rejectsNonIndustryTheme() {
        assertThatThrownBy(() -> new Dashboard(3, "dash_test", "T", null, null,
            "dark", "bezel", DashboardRefresh.defaults(),
            List.of(), List.of(),
            new DashboardLayout("free", "grid-equal"), 1L, 0L, 0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("theme must start with 'industry-'");
    }

    @Test
    void rejectsInvalidPatternId() {
        assertThatThrownBy(() -> new Widget(
            "chart_w1", "chart", "hero", "Test",
            "INVALID", null,
            null, List.of(),
            null, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("patternId must match");
    }
}
