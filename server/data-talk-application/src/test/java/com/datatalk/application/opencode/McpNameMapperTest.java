package com.datatalk.application.opencode;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpNameMapperTest {

    @Test
    void mapsActionIdsToMcpAndOpenCodeToolNames() {
        McpNameMapper mapper = McpNameMapper.forActionIds(List.of(
            "datatalk.execute_sql",
            "datatalk.get_data_context",
            "datatalk.ui.read"
        ));

        assertThat(mapper.toMcpToolName("datatalk.execute_sql")).isEqualTo("execute_sql");
        assertThat(mapper.toMcpToolName("datatalk.get_data_context")).isEqualTo("get_data_context");
        assertThat(mapper.toMcpToolName("datatalk.ui.read")).isEqualTo("ui_read");
        assertThat(mapper.toOpenCodeToolName("datatalk.execute_sql")).isEqualTo("datatalk_execute_sql");
        assertThat(mapper.toOpenCodeToolName("datatalk.ui.read")).isEqualTo("datatalk_ui_read");
    }

    @Test
    void resolvesMcpAndOpenCodeToolNamesBackToActionIds() {
        McpNameMapper mapper = McpNameMapper.forActionIds(List.of(
            "datatalk.execute_sql",
            "datatalk.get_data_context",
            "datatalk.ui.read"
        ));

        assertThat(mapper.toActionIdFromMcpToolName("execute_sql")).isEqualTo("datatalk.execute_sql");
        assertThat(mapper.toActionIdFromMcpToolName("ui_read")).isEqualTo("datatalk.ui.read");
        assertThat(mapper.toActionIdFromOpenCodeToolName("datatalk_execute_sql")).isEqualTo("datatalk.execute_sql");
        assertThat(mapper.toActionIdFromOpenCodeToolName("datatalk_ui_read")).isEqualTo("datatalk.ui.read");
    }

    @Test
    void rejectsUnknownOpenCodeToolNames() {
        McpNameMapper mapper = McpNameMapper.forActionIds(List.of("datatalk.execute_sql"));

        assertThatThrownBy(() -> mapper.toActionIdFromOpenCodeToolName("other_execute_sql"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("other_execute_sql");
    }
}
