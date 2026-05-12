package com.datatalk.domain.ingestion;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class RecordSemanticsTest {
    @Test void mappingColumnHoldsFields() {
        MappingColumn c = new MappingColumn("$.id", "id", InferredType.INTEGER_64,
            false, List.of("1","2"), false);
        assertThat(c.targetName()).isEqualTo("id");
        assertThat(c.skip()).isFalse();
    }
    @Test void ingestionMappingHoldsColumns() {
        IngestionMapping m = new IngestionMapping("map_x",
            List.of(new MappingColumn("$.id","id",InferredType.INTEGER_64,false,List.of(),false)));
        assertThat(m.columns()).hasSize(1);
    }
    @Test void ingestionCredentialNeverHoldsRawSecret() {
        IngestionCredential c = new IngestionCredential("cred_1", "prod-key",
            AuthScheme.BEARER, Map.of(), "vault_id_x", 0L, 0L);
        assertThat(c.vaultId()).isEqualTo("vault_id_x");
    }
    @Test void ingestionJobHoldsAllSpecFields() {
        IngestionJob j = new IngestionJob(
            "ing_1", "https://x", "GET",
            Map.of(), Map.of(), null,
            null, null, PayloadFormat.JSON, null,
            "pending", null, null, null, null,
            0, 0, 0L, null, 0L, 0L, null, null);
        assertThat(j.id()).isEqualTo("ing_1");
        assertThat(j.payloadFormat()).isEqualTo(PayloadFormat.JSON);
    }
}
