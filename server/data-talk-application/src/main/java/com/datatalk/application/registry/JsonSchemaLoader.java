package com.datatalk.application.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class JsonSchemaLoader {
    private final ObjectMapper om;
    private final JsonSchemaFactory factory =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    public JsonSchemaLoader(ObjectMapper om) { this.om = om; }

    public Result validate(Map<String, Object> schema, Object data) {
        JsonNode schemaNode = om.valueToTree(schema);
        JsonSchema compiled = factory.getSchema(schemaNode);
        JsonNode dataNode = om.valueToTree(data);
        var messages = compiled.validate(dataNode);
        List<String> errors = messages.stream().map(Object::toString).toList();
        return new Result(errors.isEmpty(), errors);
    }

    public record Result(boolean valid, List<String> errors) {}
}
