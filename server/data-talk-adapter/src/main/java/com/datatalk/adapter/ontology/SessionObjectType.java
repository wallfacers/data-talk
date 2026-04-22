package com.datatalk.adapter.ontology;

import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.ontology.ObjectType;
import com.datatalk.domain.ontology.ObjectTypes;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SessionObjectType implements ObjectType {

    private final Translator translator;

    public SessionObjectType(Translator translator) {
        this.translator = translator;
    }

    public record Session(
        String id, String connectionId, String title, boolean hasEverSent,
        String openCodeSid, long createdAt, long updatedAt
    ) {}

    @Override public String id() { return ObjectTypes.SESSION.id(); }
    @Override public String displayName() { return translator.get("object.session.display_name"); }
    @Override public List<String> primaryKey() { return List.of("id"); }
    @Override public Optional<String> titleField() { return Optional.of("title"); }
    @Override public String javaTypeName() { return Session.class.getName(); }

    @Override
    public Map<String, Object> propertySchema() {
        return Map.of(
            "type", "object",
            "required", List.of("id", "title", "createdAt"),
            "properties", Map.of(
                "id",            Map.of("type", "string"),
                "connectionId",  Map.of("type", "string"),
                "title",         Map.of("type", "string"),
                "hasEverSent",   Map.of("type", "boolean"),
                "openCodeSid",   Map.of("type", "string"),
                "createdAt",     Map.of("type", "integer"),
                "updatedAt",     Map.of("type", "integer")
            )
        );
    }
}
