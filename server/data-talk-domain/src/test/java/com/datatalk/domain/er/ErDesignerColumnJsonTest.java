package com.datatalk.domain.er;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the wire format that the frontend ErDesignerPayload assumes:
 * the column "default" key (a Java reserved word) maps to the
 * `defaultValue` record component via @JsonProperty in both directions.
 */
class ErDesignerColumnJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesDefaultJsonKeyIntoDefaultValueField() throws Exception {
        String json = """
            {
              "id": "c_users_status",
              "name": "status",
              "type": "VARCHAR(32)",
              "nullable": false,
              "isPrimaryKey": false,
              "isAutoIncrement": false,
              "default": "active",
              "comment": null
            }
            """;

        ErDesignerColumn column = mapper.readValue(json, ErDesignerColumn.class);

        assertThat(column.defaultValue()).isEqualTo("active");
        assertThat(column.name()).isEqualTo("status");
    }

    @Test
    void serializesDefaultValueFieldUsingDefaultJsonKey() throws Exception {
        ErDesignerColumn column = new ErDesignerColumn(
            "c_orders_paid", "paid", "BOOLEAN", false, false, false, "false", null);

        JsonNode tree = mapper.valueToTree(column);

        assertThat(tree.has("default")).as("wire key must be 'default'").isTrue();
        assertThat(tree.has("defaultValue")).as("must not leak Java field name").isFalse();
        assertThat(tree.get("default").asText()).isEqualTo("false");
    }

    @Test
    void roundTripsDialectFieldVerbatim() throws Exception {
        ErDesignerPayload payload = new ErDesignerPayload(
            "mysql", "conn-1", "shop", null,
            java.util.List.of(),
            java.util.List.of()
        );

        JsonNode tree = mapper.valueToTree(payload);

        assertThat(tree.has("dialect")).isTrue();
        assertThat(tree.has("dialectName")).isFalse();
        assertThat(tree.get("dialect").asText()).isEqualTo("mysql");

        ErDesignerPayload restored = mapper.treeToValue(tree, ErDesignerPayload.class);
        assertThat(restored.dialect()).isEqualTo("mysql");
        assertThat(restored.resolveDialect()).isEqualTo(Dialect.MYSQL);
    }
}
