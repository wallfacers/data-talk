package com.datatalk.domain.semantic;

import java.util.List;
import java.util.Objects;

public record Entity(
    String name,
    String type,
    Physical physical,
    List<String> primaryKey,
    List<ForeignKey> foreignKeys,
    String description
) {
    public Entity {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(physical, "physical must not be null");
        Objects.requireNonNull(primaryKey, "primary_key must not be null");
        if (primaryKey.isEmpty()) throw new IllegalArgumentException("primary_key must not be empty");
        foreignKeys = foreignKeys != null ? List.copyOf(foreignKeys) : List.of();
    }

    public record Physical(String database, String schema, String table) {
        public Physical {
            Objects.requireNonNull(table, "physical.table must not be null");
        }
    }

    public record ForeignKey(String column, String ref) {
        public ForeignKey {
            Objects.requireNonNull(column, "column must not be null");
            Objects.requireNonNull(ref, "ref must not be null");
        }
    }
}
