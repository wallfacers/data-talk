package com.datatalk.domain.ingestion;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EnumValuesTest {
    @Test void authSchemeHasFiveValues() {
        assertThat(AuthScheme.values()).containsExactly(
            AuthScheme.NONE, AuthScheme.BEARER, AuthScheme.API_KEY_HEADER,
            AuthScheme.API_KEY_QUERY, AuthScheme.BASIC);
    }
    @Test void payloadFormatHasFourValues() {
        assertThat(PayloadFormat.values()).containsExactly(
            PayloadFormat.JSON, PayloadFormat.JSONL, PayloadFormat.CSV, PayloadFormat.HTML);
    }
    @Test void paginationTypeHasFourValues() {
        assertThat(PaginationType.values()).containsExactly(
            PaginationType.NONE, PaginationType.PAGE, PaginationType.OFFSET, PaginationType.CURSOR);
    }
    @Test void inferredTypeContainsExpectedSet() {
        assertThat(InferredType.values()).contains(
            InferredType.BOOLEAN, InferredType.INTEGER_32, InferredType.INTEGER_64,
            InferredType.DECIMAL, InferredType.DATE, InferredType.TIMESTAMP,
            InferredType.STRING_64, InferredType.STRING_256, InferredType.STRING_500,
            InferredType.STRING_LONG, InferredType.JSON);
    }
    @Test void terminationHintTypeHasThreeValues() {
        assertThat(TerminationHintType.values()).containsExactly(
            TerminationHintType.EMPTY_ARRAY, TerminationHintType.JSON_PATH_COUNT_ZERO,
            TerminationHintType.HTTP_STATUS_404);
    }
}
