package com.datatalk.domain.semantic;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SemanticModel(
    String name,
    int version,
    String description,
    List<Entity> entities,
    List<Dimension> dimensions,
    List<Measure> measures,
    List<Metric> metrics,
    Map<String, Map<String, String>> literalMappings,
    List<VerifiedQueryRef> verifiedQueryRefs,
    Instant lastModified,
    String authoredBy
) {
    public SemanticModel {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(authoredBy, "authored_by must not be null");
        if (version < 1) throw new IllegalArgumentException("version must be >= 1, got " + version);
        entities = entities != null ? List.copyOf(entities) : List.of();
        dimensions = dimensions != null ? List.copyOf(dimensions) : List.of();
        measures = measures != null ? List.copyOf(measures) : List.of();
        metrics = metrics != null ? List.copyOf(metrics) : List.of();
        literalMappings = literalMappings != null ? Map.copyOf(literalMappings) : Map.of();
        verifiedQueryRefs = verifiedQueryRefs != null ? List.copyOf(verifiedQueryRefs) : List.of();
    }

    public record VerifiedQueryRef(String id) {
        public VerifiedQueryRef {
            Objects.requireNonNull(id, "id must not be null");
        }
    }
}
