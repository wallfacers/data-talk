package com.datatalk.application.er;

import com.datatalk.domain.er.Dialect;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.datatalk.application.er.DialectTypeRegistry.AbstractType.BIGINT;
import static com.datatalk.application.er.DialectTypeRegistry.AbstractType.BLOB;
import static com.datatalk.application.er.DialectTypeRegistry.AbstractType.BOOLEAN;
import static com.datatalk.application.er.DialectTypeRegistry.AbstractType.DATE;
import static com.datatalk.application.er.DialectTypeRegistry.AbstractType.DECIMAL;
import static com.datatalk.application.er.DialectTypeRegistry.AbstractType.INT;
import static com.datatalk.application.er.DialectTypeRegistry.AbstractType.JSON;
import static com.datatalk.application.er.DialectTypeRegistry.AbstractType.SMALLINT;
import static com.datatalk.application.er.DialectTypeRegistry.AbstractType.TEXT;
import static com.datatalk.application.er.DialectTypeRegistry.AbstractType.TIMESTAMP;
import static com.datatalk.application.er.DialectTypeRegistry.AbstractType.VARCHAR;
import static com.datatalk.application.er.DialectTypeRegistry.autoIncrementPk;
import static com.datatalk.application.er.DialectTypeRegistry.quote;
import static com.datatalk.application.er.DialectTypeRegistry.render;
import static org.assertj.core.api.Assertions.assertThat;

class DialectTypeRegistryTest {

    @Test
    void rendersAbstractTypesForSupportedDialects() {
        assertThat(render(Dialect.MYSQL, BIGINT, Map.of())).isEqualTo("BIGINT");
        assertThat(render(Dialect.POSTGRESQL, BIGINT, Map.of())).isEqualTo("BIGINT");
        assertThat(render(Dialect.H2, BIGINT, Map.of())).isEqualTo("BIGINT");
        assertThat(render(Dialect.SQLITE, BIGINT, Map.of())).isEqualTo("INTEGER");

        assertThat(render(Dialect.MYSQL, INT, Map.of())).isEqualTo("INT");
        assertThat(render(Dialect.POSTGRESQL, INT, Map.of())).isEqualTo("INTEGER");
        assertThat(render(Dialect.H2, INT, Map.of())).isEqualTo("INT");
        assertThat(render(Dialect.SQLITE, INT, Map.of())).isEqualTo("INTEGER");

        assertThat(render(Dialect.MYSQL, SMALLINT, Map.of())).isEqualTo("SMALLINT");
        assertThat(render(Dialect.POSTGRESQL, SMALLINT, Map.of())).isEqualTo("SMALLINT");
        assertThat(render(Dialect.H2, SMALLINT, Map.of())).isEqualTo("SMALLINT");
        assertThat(render(Dialect.SQLITE, SMALLINT, Map.of())).isEqualTo("INTEGER");

        var decimal = Map.<String, Object>of("precision", 10, "scale", 2);
        assertThat(render(Dialect.MYSQL, DECIMAL, decimal)).isEqualTo("DECIMAL(10,2)");
        assertThat(render(Dialect.POSTGRESQL, DECIMAL, decimal)).isEqualTo("NUMERIC(10,2)");
        assertThat(render(Dialect.H2, DECIMAL, decimal)).isEqualTo("DECIMAL(10,2)");
        assertThat(render(Dialect.SQLITE, DECIMAL, decimal)).isEqualTo("NUMERIC(10,2)");

        var varchar = Map.<String, Object>of("len", 255);
        assertThat(render(Dialect.MYSQL, VARCHAR, varchar)).isEqualTo("VARCHAR(255)");
        assertThat(render(Dialect.POSTGRESQL, VARCHAR, varchar)).isEqualTo("VARCHAR(255)");
        assertThat(render(Dialect.H2, VARCHAR, varchar)).isEqualTo("VARCHAR(255)");
        assertThat(render(Dialect.SQLITE, VARCHAR, varchar)).isEqualTo("TEXT");

        assertThat(render(Dialect.MYSQL, TEXT, Map.of())).isEqualTo("TEXT");
        assertThat(render(Dialect.POSTGRESQL, TEXT, Map.of())).isEqualTo("TEXT");
        assertThat(render(Dialect.H2, TEXT, Map.of())).isEqualTo("CLOB");
        assertThat(render(Dialect.SQLITE, TEXT, Map.of())).isEqualTo("TEXT");

        assertThat(render(Dialect.MYSQL, BOOLEAN, Map.of())).isEqualTo("TINYINT(1)");
        assertThat(render(Dialect.POSTGRESQL, BOOLEAN, Map.of())).isEqualTo("BOOLEAN");
        assertThat(render(Dialect.H2, BOOLEAN, Map.of())).isEqualTo("BOOLEAN");
        assertThat(render(Dialect.SQLITE, BOOLEAN, Map.of())).isEqualTo("INTEGER");

        assertThat(render(Dialect.MYSQL, DATE, Map.of())).isEqualTo("DATE");
        assertThat(render(Dialect.POSTGRESQL, DATE, Map.of())).isEqualTo("DATE");
        assertThat(render(Dialect.H2, DATE, Map.of())).isEqualTo("DATE");
        assertThat(render(Dialect.SQLITE, DATE, Map.of())).isEqualTo("TEXT");

        assertThat(render(Dialect.MYSQL, TIMESTAMP, Map.of())).isEqualTo("TIMESTAMP");
        assertThat(render(Dialect.POSTGRESQL, TIMESTAMP, Map.of())).isEqualTo("TIMESTAMP");
        assertThat(render(Dialect.H2, TIMESTAMP, Map.of())).isEqualTo("TIMESTAMP");
        assertThat(render(Dialect.SQLITE, TIMESTAMP, Map.of())).isEqualTo("TEXT");

        assertThat(render(Dialect.MYSQL, JSON, Map.of())).isEqualTo("JSON");
        assertThat(render(Dialect.POSTGRESQL, JSON, Map.of())).isEqualTo("JSONB");
        assertThat(render(Dialect.H2, JSON, Map.of())).isEqualTo("JSON");
        assertThat(render(Dialect.SQLITE, JSON, Map.of())).isEqualTo("TEXT");

        assertThat(render(Dialect.MYSQL, BLOB, Map.of())).isEqualTo("BLOB");
        assertThat(render(Dialect.POSTGRESQL, BLOB, Map.of())).isEqualTo("BYTEA");
        assertThat(render(Dialect.H2, BLOB, Map.of())).isEqualTo("BLOB");
        assertThat(render(Dialect.SQLITE, BLOB, Map.of())).isEqualTo("BLOB");
    }

    @Test
    void quotesIdentifiersAndEscapesEmbeddedQuoteCharacters() {
        assertThat(quote(Dialect.MYSQL, "user`name")).isEqualTo("`user``name`");
        assertThat(quote(Dialect.POSTGRESQL, "user\"name")).isEqualTo("\"user\"\"name\"");
        assertThat(quote(Dialect.H2, "users")).isEqualTo("\"users\"");
        assertThat(quote(Dialect.SQLITE, "users")).isEqualTo("\"users\"");
    }

    @Test
    void autoIncrementPrimaryKeyFragmentsAreDialectSpecific() {
        assertThat(autoIncrementPk(Dialect.MYSQL, "id"))
            .isEqualTo("`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY");
        assertThat(autoIncrementPk(Dialect.POSTGRESQL, "id"))
            .isEqualTo("\"id\" BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY");
        assertThat(autoIncrementPk(Dialect.H2, "id"))
            .isEqualTo("\"id\" BIGINT AUTO_INCREMENT PRIMARY KEY");
        assertThat(autoIncrementPk(Dialect.SQLITE, "id"))
            .isEqualTo("\"id\" INTEGER PRIMARY KEY AUTOINCREMENT");
    }
}
