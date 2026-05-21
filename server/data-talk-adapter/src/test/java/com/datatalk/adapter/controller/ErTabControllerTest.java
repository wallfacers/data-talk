package com.datatalk.adapter.controller;

import com.datatalk.application.er.ErDdlGeneratorService;
import com.datatalk.application.er.ErDesignerSyncService;
import com.datatalk.application.er.ErRelationDiscoveryService;
import com.datatalk.domain.er.ErDdlKind;
import com.datatalk.domain.er.ErDdlPlan;
import com.datatalk.domain.er.ErDdlStatement;
import com.datatalk.domain.er.ErDesignerPayload;
import com.datatalk.domain.er.ErDesignerTable;
import com.datatalk.domain.er.SkippedOp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ErTabControllerTest {

    private ErRelationDiscoveryService discovery;
    private ErDdlGeneratorService ddlService;
    private ErDesignerSyncService syncService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        discovery = mock(ErRelationDiscoveryService.class);
        ddlService = mock(ErDdlGeneratorService.class);
        syncService = mock(ErDesignerSyncService.class);
        mvc = standaloneSetup(new ErTabController(discovery, ddlService, syncService)).build();
    }

    @Test
    void generateDdlReturnsStatementsAndSkipped() throws Exception {
        when(ddlService.generate(any(ErDesignerPayload.class), eq("conn-1"), eq(false)))
            .thenReturn(new ErDdlPlan(
                List.of(new ErDdlStatement("CREATE TABLE products (id BIGINT)", ErDdlKind.CREATE_TABLE, "products")),
                List.of(new SkippedOp("DROP_TABLE", "old_products", null, "day1_unsupported", "Write manual SQL in query_editor."))
            ));

        mvc.perform(post("/api/er/generate-ddl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "payload": { "dialect": "h2", "targetConnectionId": "conn-1", "tables": [], "relations": [] },
                      "connectionId": "conn-1",
                      "includeDrops": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ddl").value("CREATE TABLE products (id BIGINT);"))
            .andExpect(jsonPath("$.statements", hasSize(1)))
            .andExpect(jsonPath("$.skipped", hasSize(1)));
    }

    @Test
    void diffReturnsDiffList() throws Exception {
        when(ddlService.diff(any(ErDesignerPayload.class), eq("conn-1"))).thenReturn(List.of());

        mvc.perform(post("/api/er/diff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "payload": { "dialect": "h2", "targetConnectionId": "conn-1", "tables": [], "relations": [] },
                      "connectionId": "conn-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.diff").isArray());
    }

    @Test
    void syncFromDbReturnsMergedDesignerPayload() throws Exception {
        when(syncService.sync(any(ErDesignerPayload.class), eq("conn-1"), anyList()))
            .thenReturn(new ErDesignerPayload(
                "h2", "conn-1", null, null,
                List.of(new ErDesignerTable("t_orders", "orders", null, List.of(), List.of(), List.of())),
                List.of()
            ));

        mvc.perform(post("/api/er/sync-from-db")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "payload": { "dialect": "h2", "targetConnectionId": "conn-1", "tables": [], "relations": [] },
                      "connectionId": "conn-1",
                      "tables": ["orders"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload.dialect").value("h2"))
            .andExpect(jsonPath("$.payload.tables", hasSize(1)))
            .andExpect(jsonPath("$.payload.tables[0].name").value("orders"));
    }

    @Test
    void syncFromDbWithoutPayloadReturns400() throws Exception {
        mvc.perform(post("/api/er/sync-from-db")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "connectionId": "conn-1", "tables": ["orders"] }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    @Test
    void generateDdlMissingTargetReturns400() throws Exception {
        mvc.perform(post("/api/er/generate-ddl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "payload": { "dialect": "mysql", "tables": [], "relations": [] },
                      "connectionId": null
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("target_required_for_apply"))
            .andExpect(jsonPath("$.aiHint").isString());
    }
}
