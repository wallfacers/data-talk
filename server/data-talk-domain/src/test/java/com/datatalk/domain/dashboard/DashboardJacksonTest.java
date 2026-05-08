package com.datatalk.domain.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardJacksonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripsMinimalDashboard() throws Exception {
        Dashboard original = new Dashboard(
            1, "dash_xxx", "Sales Dashboard", null, "conn_1",
            List.of(),
            List.of(new Widget(
                "chart_w1", WidgetType.CHART,
                new GridPosition(0, 0, 6, 8, null),
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
    }

    @Test
    void roundTripsDashboardWithParameters() throws Exception {
        ParameterDef param = new ParameterDef("global:date_range", "global", null, "dateRange", "date_range", "last_7d");
        WidgetQuery query = new WidgetQuery("conn_1", "SELECT * FROM orders WHERE date = :dateRange", Map.of("dateRange", "global:date_range"));

        Dashboard original = new Dashboard(
            1, "dash_abc12345", "Orders Dashboard", "Monthly overview", "conn_1",
            List.of(param),
            List.of(new Widget(
                "chart_w_abc123", WidgetType.KPI,
                new GridPosition(0, 0, 3, 4, null),
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
    }

    @Test
    void nullCollectionsBecomeEmpty() throws Exception {
        Dashboard dash = new Dashboard(1, "dash_test", "T", null, null, null, null,
            new GridLayout("grid", 12, 32, 8), 1L, 0L, 0L);

        assertThat(dash.parameters()).isEmpty();
        assertThat(dash.widgets()).isEmpty();
    }
}
