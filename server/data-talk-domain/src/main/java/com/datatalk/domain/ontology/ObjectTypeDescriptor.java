package com.datatalk.domain.ontology;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ObjectTypeDescriptor(
        String id,
        String displayName,
        Map<String, Object> propertySchema,
        List<String> primaryKey,
        Optional<String> titleField,
        String javaTypeName
) {}
