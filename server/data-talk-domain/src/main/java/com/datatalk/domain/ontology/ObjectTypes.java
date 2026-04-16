package com.datatalk.domain.ontology;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Built-in object type constants for the Data Talk ontology.
 */
public final class ObjectTypes {

    private ObjectTypes() {}

    public static final ObjectTypeDescriptor CONNECTION = new ObjectTypeDescriptor(
            "datatalk.connection", "Connection",
            Map.of("type", "object", "category", "infrastructure"),
            List.of("id"), Optional.of("name"),
            "com.datatalk.entity.DbConnection");

    public static final ObjectTypeDescriptor SESSION = new ObjectTypeDescriptor(
            "datatalk.session", "Session",
            Map.of("type", "object", "category", "collaboration"),
            List.of("id"), Optional.of("title"),
            "com.datatalk.entity.Session");

    public static final ObjectTypeDescriptor ARTIFACT = new ObjectTypeDescriptor(
            "datatalk.artifact", "Artifact",
            Map.of("type", "object", "category", "data"),
            List.of("id", "version"), Optional.of("title"),
            "com.datatalk.domain.ontology.Artifact");

    public static final ObjectTypeDescriptor ACTION_INVOCATION = new ObjectTypeDescriptor(
            "datatalk.action_invocation", "Action Invocation",
            Map.of("type", "object", "category", "execution"),
            List.of("id"), Optional.empty(),
            "com.datatalk.domain.ontology.ActionInvocation");
}
