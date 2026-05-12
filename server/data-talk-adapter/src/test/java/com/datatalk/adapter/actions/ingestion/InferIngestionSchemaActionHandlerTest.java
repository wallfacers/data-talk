package com.datatalk.adapter.actions.ingestion;

import com.datatalk.application.ingestion.IngestionSchemaInferrer;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.ingestion.InferredType;
import com.datatalk.domain.ingestion.IngestionMapping;
import com.datatalk.domain.ingestion.MappingColumn;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InferIngestionSchemaActionHandlerTest {

    @Mock IngestionSchemaInferrer inferrer;
    InferIngestionSchemaActionHandler handler;

    ActionContext ctx = new ActionContext("sess-1", "call-1", "conn-1", "oc-1");

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        handler = new InferIngestionSchemaActionHandler(inferrer);
    }

    // ───────── happy path ─────────

    @Test
    @SuppressWarnings("unchecked")
    void happyPathReturnsMapping() throws Exception {
        MappingColumn col1 = new MappingColumn("$.name", "name", InferredType.STRING_256, false, List.of("Alice"), false);
        MappingColumn col2 = new MappingColumn("$.age", "age", InferredType.INTEGER_32, false, List.of("30"), false);
        IngestionMapping mapping = new IngestionMapping("map_abc123", List.of(col1, col2));

        when(inferrer.infer("ing_1", 100, "sess-1")).thenReturn(mapping);

        Map<String, Object> input = Map.of("jobId", "ing_1");
        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("mappingId")).isEqualTo("map_abc123");
        assertThat(result.get("status")).isEqualTo("inferred");
        assertThat(result.get("rowsAnalyzed")).isEqualTo(100);
        assertThat(result.get("error")).isNull();
        assertThat(result.get("userHint")).isNull();

        List<Map<String, Object>> columns = (List<Map<String, Object>>) result.get("columns");
        assertThat(columns).hasSize(2);
        assertThat(columns.get(0)).containsEntry("sourcePath", "$.name");
        assertThat(columns.get(0)).containsEntry("targetName", "name");
        assertThat(columns.get(0)).containsEntry("type", "string_256");

        String ddl = (String) result.get("suggestedDdl");
        assertThat(ddl).contains("CREATE TABLE").contains("\"name\"").contains("VARCHAR(256)");
    }

    // ───────── custom sampleSize ─────────

    @Test
    @SuppressWarnings("unchecked")
    void customSampleSize() throws Exception {
        IngestionMapping mapping = new IngestionMapping("map_1", List.of());
        when(inferrer.infer("ing_2", 50, "sess-1")).thenReturn(mapping);

        Map<String, Object> input = Map.of("jobId", "ing_2", "sampleSize", 50);
        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("rowsAnalyzed")).isEqualTo(50);
    }

    // ───────── default sampleSize ─────────

    @Test
    @SuppressWarnings("unchecked")
    void defaultSampleSizeIs100() throws Exception {
        IngestionMapping mapping = new IngestionMapping("map_1", List.of());
        when(inferrer.infer("ing_3", 100, "sess-1")).thenReturn(mapping);

        Map<String, Object> input = Map.of("jobId", "ing_3");
        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        verify(inferrer).infer("ing_3", 100, "sess-1");
        assertThat(result.get("rowsAnalyzed")).isEqualTo(100);
    }

    // ───────── job not found ─────────

    @Test
    @SuppressWarnings("unchecked")
    void jobNotFoundReturnsError() throws Exception {
        when(inferrer.infer(eq("missing"), anyInt(), eq("sess-1")))
            .thenThrow(new IllegalArgumentException("job not found: missing"));

        Map<String, Object> input = Map.of("jobId", "missing");
        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("status")).isEqualTo("failed");
        Map<String, String> error = (Map<String, String>) result.get("error");
        assertThat(error.get("code")).isEqualTo("INGESTION_JOB_NOT_FOUND");
        assertThat(result.get("userHint")).asString().isNotBlank();
        assertThat(result.get("mappingId")).isNull();
        assertThat(result.get("columns")).isEqualTo(List.of());
    }

    // ───────── unsupported format ─────────

    @Test
    @SuppressWarnings("unchecked")
    void unsupportedFormatReturnsError() throws Exception {
        when(inferrer.infer(eq("ing_4"), anyInt(), eq("sess-1")))
            .thenThrow(new UnsupportedOperationException("unsupported payload format: XML"));

        Map<String, Object> input = Map.of("jobId", "ing_4");
        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("status")).isEqualTo("failed");
        Map<String, String> error = (Map<String, String>) result.get("error");
        assertThat(error.get("code")).isEqualTo("INGESTION_FORMAT_UNSUPPORTED");
    }

    // ───────── generic failure ─────────

    @Test
    @SuppressWarnings("unchecked")
    void genericFailureReturnsError() throws Exception {
        when(inferrer.infer(eq("ing_5"), anyInt(), eq("sess-1")))
            .thenThrow(new RuntimeException("I/O error reading file"));

        Map<String, Object> input = Map.of("jobId", "ing_5");
        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("status")).isEqualTo("failed");
        Map<String, String> error = (Map<String, String>) result.get("error");
        assertThat(error.get("code")).isEqualTo("INGESTION_INFER_FAILED");
    }

    // ───────── missing jobId ─────────

    @Test
    @SuppressWarnings("unchecked")
    void missingJobIdReturnsError() throws Exception {
        Map<String, Object> input = Map.of("sampleSize", 50);
        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("status")).isEqualTo("failed");
        Map<String, String> error = (Map<String, String>) result.get("error");
        assertThat(error.get("code")).isEqualTo("INGESTION_JOB_NOT_FOUND");
    }

    // ───────── DDL type mapping ─────────

    @Test
    @SuppressWarnings("unchecked")
    void ddlMapsTypesCorrectly() throws Exception {
        MappingColumn boolCol = new MappingColumn("$.active", "active", InferredType.BOOLEAN, false, List.of(), false);
        MappingColumn longCol = new MappingColumn("$.id", "id", InferredType.INTEGER_64, false, List.of(), false);
        MappingColumn decCol = new MappingColumn("$.price", "price", InferredType.DECIMAL, false, List.of(), false);
        MappingColumn dateCol = new MappingColumn("$.created", "created", InferredType.TIMESTAMP, false, List.of(), false);
        MappingColumn textCol = new MappingColumn("$.bio", "bio", InferredType.STRING_LONG, false, List.of(), true);
        IngestionMapping mapping = new IngestionMapping("map_types", List.of(boolCol, longCol, decCol, dateCol, textCol));

        when(inferrer.infer("ing_types", 10, "sess-1")).thenReturn(mapping);

        Map<String, Object> input = Map.of("jobId", "ing_types", "sampleSize", 10);
        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        String ddl = (String) result.get("suggestedDdl");
        assertThat(ddl).contains("BOOLEAN");
        assertThat(ddl).contains("BIGINT");
        assertThat(ddl).contains("DECIMAL(18,4)");
        assertThat(ddl).contains("TIMESTAMP");
        assertThat(ddl).contains("TEXT");
    }

    // ───────── schema definitions ─────────

    @Test
    void inputSchemaIsDefined() {
        var schema = handler.inputSchema();
        assertThat(schema).containsKey("type");
        assertThat(schema).containsKey("properties");
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKey("jobId");
        assertThat(props).containsKey("sampleSize");
    }

    @Test
    void outputSchemaIsDefined() {
        var schema = handler.outputSchema();
        assertThat(schema).containsKey("type");
        assertThat(schema).containsKey("properties");
    }

    @Test
    void sideEffectsPatchesArtifact() {
        assertThat(handler.sideEffects()).containsExactly(
            com.datatalk.domain.action.OntologyEffect.PATCH_ARTIFACT);
    }
}
