package com.datatalk.domain.semantic;

import java.util.List;
import java.util.Objects;

public record UserProfile(
    String defaultTimeRange,
    int defaultLimit,
    List<String> preferredTables,
    List<FrequentFilter> frequentFilters,
    List<QuestionTemplate> questionTemplates
) {
    public UserProfile {
        if (defaultLimit < 1) throw new IllegalArgumentException("defaultLimit must be >= 1, got " + defaultLimit);
        preferredTables = preferredTables != null ? List.copyOf(preferredTables) : List.of();
        frequentFilters = frequentFilters != null ? List.copyOf(frequentFilters) : List.of();
        questionTemplates = questionTemplates != null ? List.copyOf(questionTemplates) : List.of();
    }

    public record FrequentFilter(String dimension, String value, String label) {
        public FrequentFilter {
            Objects.requireNonNull(dimension, "dimension must not be null");
            Objects.requireNonNull(value, "value must not be null");
        }
    }

    public record QuestionTemplate(String template, String category, int hitCount) {
        public QuestionTemplate {
            Objects.requireNonNull(template, "template must not be null");
        }
    }
}
