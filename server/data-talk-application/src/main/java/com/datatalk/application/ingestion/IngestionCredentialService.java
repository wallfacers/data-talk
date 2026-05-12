package com.datatalk.application.ingestion;

import com.datatalk.application.ingestion.repository.IngestionCredentialRepository;
import com.datatalk.application.persistence.SecretVault;
import com.datatalk.domain.ingestion.AuthScheme;
import com.datatalk.domain.ingestion.IngestionCredential;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class IngestionCredentialService {

    public interface VaultWriter {
        void write(String vaultId, byte[] sealed);
    }

    public interface VaultReader {
        byte[] read(String vaultId);
    }

    private final IngestionCredentialRepository repo;
    private final SecretVault vault;
    private final VaultWriter writer;
    private final VaultReader reader;

    public IngestionCredentialService(IngestionCredentialRepository repo,
                                      SecretVault vault,
                                      VaultWriter writer,
                                      VaultReader reader) {
        this.repo = repo;
        this.vault = vault;
        this.writer = writer;
        this.reader = reader;
    }

    public String create(String name, AuthScheme scheme,
                         Map<String, String> configNonSecret, String rawSecret) {
        if (repo.findByName(name).isPresent()) {
            throw new IllegalArgumentException("credential name already used: " + name);
        }
        String id = "cred_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String vaultId = null;
        if (scheme != AuthScheme.NONE && rawSecret != null && !rawSecret.isEmpty()) {
            vaultId = "vault_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            writer.write(vaultId, vault.seal(rawSecret));
        }
        long now = System.currentTimeMillis();
        repo.save(new IngestionCredential(id, name, scheme, configNonSecret, vaultId, now, now));
        return id;
    }

    public String readSecret(String credentialId) {
        var c = repo.findById(credentialId).orElseThrow(() ->
            new IllegalArgumentException("credential not found: " + credentialId));
        if (c.vaultId() == null) return null;
        return vault.open(reader.read(c.vaultId()));
    }

    public void delete(String credentialId, boolean force) {
        int refs = repo.countReferencingJobs(credentialId);
        if (refs > 0 && !force) {
            throw new IllegalStateException(
                refs + " ingestion job(s) reference this credential; pass force=true to nullify and delete");
        }
        if (refs > 0) repo.nullifyCredentialOnJobs(credentialId);
        repo.deleteById(credentialId);
    }
}
