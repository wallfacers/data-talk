package com.datatalk.adapter.ontology;

import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.ontology.ObjectType;
import com.datatalk.domain.ontology.ObjectTypes;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ArtifactObjectType implements ObjectType {

    private final Translator translator;

    public ArtifactObjectType(Translator translator) {
        this.translator = translator;
    }

    public record Artifact(
        String id, int version, String sessionId, String kind, String producedBy,
        String payloadRef, int payloadSize, String supersedesId, Integer supersedesVersion,
        boolean pinned, long createdAt
    ) {}

    @Override public String id() { return ObjectTypes.ARTIFACT.id(); }
    @Override public String displayName() { return translator.get("object.artifact.display_name"); }
    @Override public List<String> primaryKey() { return List.of("id", "version"); }
    @Override public Optional<String> titleField() { return Optional.of("id"); }
    @Override public String javaTypeName() { return Artifact.class.getName(); }

    @Override
    public Map<String, Object> propertySchema() {
        return Map.of(
            "type", "object",
            "required", List.of("id", "version", "sessionId", "kind", "producedBy", "payloadRef"),
            "properties", Map.ofEntries(
                Map.entry("id",                Map.of("type", "string")),
                Map.entry("version",           Map.of("type", "integer", "minimum", 1)),
                Map.entry("sessionId",         Map.of("type", "string")),
                Map.entry("kind",              Map.of("type", "string", "enum", List.of("table", "chart", "erd"))),
                Map.entry("producedBy",        Map.of("type", "string")),
                Map.entry("payloadRef",        Map.of("type", "string", "pattern", "^(INLINE|HANDLE):")),
                Map.entry("payloadSize",       Map.of("type", "integer", "minimum", 0)),
                Map.entry("supersedesId",      Map.of("type", "string")),
                Map.entry("supersedesVersion", Map.of("type", "integer", "minimum", 1)),
                Map.entry("pinned",            Map.of("type", "boolean")),
                Map.entry("createdAt",         Map.of("type", "integer"))
            )
        );
    }
}
