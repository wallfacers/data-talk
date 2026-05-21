package com.datatalk.domain.ontology;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contract for domain object types that can be described as ontology entries.
 */
public interface ObjectType {

    String id();

    String displayName();

    Map<String, Object> propertySchema();

    List<String> primaryKey();

    Optional<String> titleField();

    String javaTypeName();

    default ObjectTypeDescriptor toDescriptor() {
        return new ObjectTypeDescriptor(
                id(), displayName(), propertySchema(),
                primaryKey(), titleField(), javaTypeName());
    }
}
