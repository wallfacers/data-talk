package com.datatalk.application.ingestion;

import com.datatalk.domain.ingestion.InferredType;
import com.datatalk.domain.ingestion.IngestionMapping;
import com.datatalk.domain.ingestion.MappingColumn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MappingHashTest {

    @Test
    void nullMappingReturnsEmptyString() {
        assertThat(MappingHash.compute(null)).isEmpty();
    }

    @Test
    void sameMappingProducesSameHash() {
        var mapping = sampleMapping();
        assertThat(MappingHash.compute(mapping)).isEqualTo(MappingHash.compute(mapping));
    }

    @Test
    void differentTargetNameProducesDifferentHash() {
        var a = new IngestionMapping("m1", List.of(
            new MappingColumn("a", "col_a", InferredType.STRING_64, false, List.of(), true)));
        var b = new IngestionMapping("m1", List.of(
            new MappingColumn("a", "col_b", InferredType.STRING_64, false, List.of(), true)));
        assertThat(MappingHash.compute(a)).isNotEqualTo(MappingHash.compute(b));
    }

    @Test
    void differentTypeProducesDifferentHash() {
        var a = new IngestionMapping("m1", List.of(
            new MappingColumn("a", "col", InferredType.STRING_64, false, List.of(), true)));
        var b = new IngestionMapping("m1", List.of(
            new MappingColumn("a", "col", InferredType.INTEGER_32, false, List.of(), true)));
        assertThat(MappingHash.compute(a)).isNotEqualTo(MappingHash.compute(b));
    }

    @Test
    void differentSkipProducesDifferentHash() {
        var a = new IngestionMapping("m1", List.of(
            new MappingColumn("a", "col", InferredType.STRING_64, false, List.of(), true)));
        var b = new IngestionMapping("m1", List.of(
            new MappingColumn("a", "col", InferredType.STRING_64, true, List.of(), true)));
        assertThat(MappingHash.compute(a)).isNotEqualTo(MappingHash.compute(b));
    }

    @Test
    void sampleValuesDoNotAffectHash() {
        var a = new IngestionMapping("m1", List.of(
            new MappingColumn("a", "col", InferredType.STRING_64, false, List.of("foo", "bar"), true)));
        var b = new IngestionMapping("m1", List.of(
            new MappingColumn("a", "col", InferredType.STRING_64, false, List.of("baz", "qux"), true)));
        assertThat(MappingHash.compute(a)).isEqualTo(MappingHash.compute(b));
    }

    @Test
    void hashIs64CharHex() {
        String hash = MappingHash.compute(sampleMapping());
        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }

    private static IngestionMapping sampleMapping() {
        return new IngestionMapping("m1", List.of(
            new MappingColumn("name", "name", InferredType.STRING_64, false, List.of("Alice"), false),
            new MappingColumn("age", "age", InferredType.INTEGER_32, false, List.of("30"), true)));
    }
}
