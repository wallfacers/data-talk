package com.datatalk.infra.ingestion;

import com.datatalk.application.ingestion.IngestionCredentialService.VaultReader;
import com.datatalk.application.ingestion.IngestionCredentialService.VaultWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * JDBC-backed implementation of VaultWriter / VaultReader for ingestion credentials.
 * Stores sealed (AES-GCM encrypted) blobs in the ingestion_vault_store table.
 */
@Component
public class JdbcIngestionVaultStore implements VaultWriter, VaultReader {

    private final JdbcTemplate jdbc;

    public JdbcIngestionVaultStore(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void write(String vaultId, byte[] sealed) {
        int updated = jdbc.update(
            "UPDATE ingestion_vault_store SET sealed_bytes=? WHERE vault_id=?",
            sealed, vaultId);
        if (updated == 0) {
            jdbc.update(
                "INSERT INTO ingestion_vault_store (vault_id, sealed_bytes) VALUES (?, ?)",
                vaultId, sealed);
        }
    }

    @Override
    public byte[] read(String vaultId) {
        return jdbc.queryForObject(
            "SELECT sealed_bytes FROM ingestion_vault_store WHERE vault_id=?",
            byte[].class, vaultId);
    }
}
