package com.datatalk.adapter.ontology;

import com.datatalk.domain.ontology.ObjectType;
import com.datatalk.domain.ontology.ObjectTypes;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ActionInvocationObjectType implements ObjectType {

    public record Invocation(
        String callId, String sessionId, String actionId, String status,
        String inputJson, String outputJson, String errorJson,
        long startedAt, Long endedAt
    ) {}

    @Override public String id() { return ObjectTypes.ACTION_INVOCATION.id(); }
    @Override public String displayName() { return "ActionInvocation"; }
    @Override public List<String> primaryKey() { return List.of("callId"); }
    @Override public Optional<String> titleField() { return Optional.of("actionId"); }
    @Override public String javaTypeName() { return Invocation.class.getName(); }

    @Override
    public Map<String, Object> propertySchema() {
        return Map.of(
            "type", "object",
            "required", List.of("callId", "sessionId", "actionId", "status"),
            "properties", Map.of(
                "callId",     Map.of("type", "string"),
                "sessionId",  Map.of("type", "string"),
                "actionId",   Map.of("type", "string"),
                "status",     Map.of("type", "string", "enum",
                    List.of("running", "completed", "error", "cancelled")),
                "startedAt",  Map.of("type", "integer"),
                "endedAt",    Map.of("type", "integer")
            )
        );
    }
}
