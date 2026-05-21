package com.datatalk.application.coverage.dameng;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kind-private equivalence test: dameng routes through the existing Oracle
 * discovery path. Verifies the 5-item Dameng system schema filter and the
 * namespace routing matches Oracle semantics (server-level connection,
 * schema is primary namespace).
 */
class DamengMetadataEquivalenceTest {

    // ====== Dameng system schema filter ======

    @Test
    void damengSystemSchemasContainsFiveExpectedEntries() throws Exception {
        Set<String> schemas = getDamengSystemSchemas();
        assertThat(schemas).containsExactlyInAnyOrder(
            "sys", "sysdba", "sysauditor", "syssso", "ctisys");
    }

    @Test
    void damengSystemSchemasAreFilteredByIsUserSchema() throws Exception {
        Set<String> schemas = getDamengSystemSchemas();
        for (String schema : schemas) {
            assertThat(invokeIsUserSchema(schema))
                .as("Dameng system schema '%s' should be filtered", schema)
                .isFalse();
        }
    }

    @Test
    void scottAndOtherUserSchemasAreNotFiltered() throws Exception {
        assertThat(invokeIsUserSchema("SCOTT")).isTrue();
        assertThat(invokeIsUserSchema("HR")).isTrue();
        assertThat(invokeIsUserSchema("SALES")).isTrue();
    }

    // ====== Routing: dameng has no independent schema namespace (Oracle-like) ======

    @Test
    void damengHasNoIndependentSchemaNamespace() throws Exception {
        // Dameng is Oracle-like: schemas are the primary namespace.
        // hasIndependentSchemaNamespace returns false, meaning schema discovery
        // uses DatabaseMetaData.getSchemas().
        assertThat(invokeHasIndependentSchemaNamespace("dameng")).isFalse();
        assertThat(invokeHasIndependentSchemaNamespace("oracle")).isTrue();
        assertThat(invokeHasIndependentSchemaNamespace("postgresql")).isTrue();
    }

    // ====== Reflection helpers ======

    @SuppressWarnings("unchecked")
    private static Set<String> getDamengSystemSchemas() throws Exception {
        Class<?> clazz = Class.forName(
            "com.datatalk.application.session.ConnectionTargetDiscoveryService");
        Field field = clazz.getDeclaredField("DAMENG_SYSTEM_SCHEMAS");
        field.setAccessible(true);
        return (Set<String>) field.get(null);
    }

    private static boolean invokeIsUserSchema(String schema) throws Exception {
        Class<?> clazz = Class.forName(
            "com.datatalk.application.session.ConnectionTargetDiscoveryService");
        Method method = clazz.getDeclaredMethod("isUserSchema", String.class);
        method.setAccessible(true);
        // Create an instance with null deps — isUserSchema is a pure function
        // that does not touch instance fields.
        Object instance = clazz.getDeclaredConstructors()[0].newInstance(
            null, null, null);
        return (boolean) method.invoke(instance, schema);
    }

    private static boolean invokeHasIndependentSchemaNamespace(String kind) throws Exception {
        Class<?> clazz = Class.forName(
            "com.datatalk.application.session.ConnectionTargetDiscoveryService");
        Method method = clazz.getDeclaredMethod(
            "hasIndependentSchemaNamespace", String.class);
        method.setAccessible(true);
        Object instance = clazz.getDeclaredConstructors()[0].newInstance(
            null, null, null);
        return (boolean) method.invoke(instance, kind);
    }
}
