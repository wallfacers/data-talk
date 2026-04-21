package com.datatalk.adapter.actions;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadSchemaActionTest {

    @Test
    void mysql_uses_database_as_catalog_scope() {
        var scope = ReadSchemaAction.metadataScope("mysql", "datatalk_ctx", null);

        assertThat(scope.catalog()).isEqualTo("datatalk_ctx");
        assertThat(scope.schema()).isNull();
    }

    @Test
    void postgres_uses_schema_scope_instead_of_catalog() {
        var scope = ReadSchemaAction.metadataScope("postgresql", "app_db", "public");

        assertThat(scope.catalog()).isNull();
        assertThat(scope.schema()).isEqualTo("public");
    }
}
