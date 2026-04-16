package com.datatalk.domain.ontology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectTypeDescriptorTest {

    @Test
    void carriesAllObjectTypeMetadata() {
        ObjectTypeDescriptor d = new ObjectTypeDescriptor(
            "datatalk.artifact", "Artifact", Map.of("type", "object"),
            List.of("id", "version"), Optional.of("title"),
            "com.datatalk.domain.ontology.Artifact");

        assertThat(d.id()).isEqualTo("datatalk.artifact");
        assertThat(d.primaryKey()).containsExactly("id", "version");
        assertThat(d.titleField()).hasValue("title");
    }
}
