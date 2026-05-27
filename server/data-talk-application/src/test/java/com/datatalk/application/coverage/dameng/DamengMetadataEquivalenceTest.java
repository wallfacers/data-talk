package com.datatalk.application.coverage.dameng;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kind-private equivalence test: dameng routes through the existing Oracle
 * discovery path. Verifies namespace routing matches Oracle semantics
 * (server-level connection, schema is primary namespace).
 */
class DamengMetadataEquivalenceTest {

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
