package com.datatalk.adapter.ontology;

import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.ontology.ObjectType;
import com.datatalk.domain.ontology.ObjectTypes;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ConnectionObjectType implements ObjectType {

    private final Translator translator;

    public ConnectionObjectType(Translator translator) {
        this.translator = translator;
    }

    public record Connection(
        String id, String kind, String host, int port, String databaseName,
        String username, String schemaDigest, long createdAt
    ) {}

    @Override public String id() { return ObjectTypes.CONNECTION.id(); }
    @Override public String displayName() { return translator.get("object.connection.display_name"); }
    @Override public List<String> primaryKey() { return List.of("id"); }
    @Override public Optional<String> titleField() { return Optional.of("id"); }
    @Override public String javaTypeName() { return Connection.class.getName(); }

    @Override
    public Map<String, Object> propertySchema() {
        return Map.of(
            "type", "object",
            "required", List.of("id", "kind", "host", "port", "username"),
            "properties", Map.ofEntries(
                Map.entry("id",           Map.of("type", "string")),
                Map.entry("kind",         Map.of("type", "string", "enum", List.of("mysql", "postgresql", "sqlite", "h2", "mariadb", "tidb", "oceanbase", "oracle", "sqlserver", "duckdb", "clickhouse", "apache_doris", "starrocks", "trino", "presto", "hive", "dameng"))),
                Map.entry("host",         Map.of("type", "string")),
                Map.entry("port",         Map.of("type", "integer", "minimum", 1, "maximum", 65535)),
                Map.entry("databaseName", Map.of("type", "string")),
                Map.entry("username",     Map.of("type", "string")),
                Map.entry("schemaDigest", Map.of("type", "string")),
                Map.entry("createdAt",    Map.of("type", "integer"))
            )
        );
    }
}
