package com.datatalk.application.ingestion;

import com.datatalk.application.ingestion.repository.IngestionCredentialRepository;
import com.datatalk.application.persistence.SecretVault;
import com.datatalk.domain.ingestion.AuthScheme;
import com.datatalk.domain.ingestion.IngestionCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class IngestionCredentialServiceTest {
    SecretVault vault;
    IngestionCredentialRepository repo;
    IngestionCredentialService service;
    Map<String, byte[]> vaultBackingStore;

    @BeforeEach
    void setup() {
        byte[] masterKey = new byte[32];
        for (int i = 0; i < 32; i++) masterKey[i] = (byte) (i + 1);
        vault = new SecretVault(masterKey);
        vaultBackingStore = new HashMap<>();
        repo = new InMemoryCredentialRepo();
        service = new IngestionCredentialService(repo, vault,
            (id, sealedBytes) -> vaultBackingStore.put(id, sealedBytes),
            (id) -> vaultBackingStore.get(id));
    }

    @Test
    void createBearerStoresSealedToken() {
        var id = service.create("test-bearer", AuthScheme.BEARER, Map.of(), "secret-token-123");
        var c = repo.findById(id).orElseThrow();
        assertThat(c.vaultId()).isNotNull();
        assertThat(service.readSecret(id)).isEqualTo("secret-token-123");
    }

    @Test
    void createNoneDoesNotPersistSecret() {
        var id = service.create("no-auth", AuthScheme.NONE, Map.of(), null);
        var c = repo.findById(id).orElseThrow();
        assertThat(c.vaultId()).isNull();
    }

    @Test
    void deleteBlocksWhenReferenced() {
        var id = service.create("used", AuthScheme.NONE, Map.of(), null);
        ((InMemoryCredentialRepo) repo).referenceCount.put(id, 3);
        assertThatThrownBy(() -> service.delete(id, false))
            .hasMessageContaining("3 ingestion job");
    }

    @Test
    void deleteWithForceNullifiesReferences() {
        var id = service.create("used2", AuthScheme.NONE, Map.of(), null);
        ((InMemoryCredentialRepo) repo).referenceCount.put(id, 2);
        service.delete(id, true);
        assertThat(repo.findById(id)).isEmpty();
        assertThat(((InMemoryCredentialRepo) repo).nullifyCalls).contains(id);
    }

    static class InMemoryCredentialRepo implements IngestionCredentialRepository {
        Map<String, IngestionCredential> data = new HashMap<>();
        Map<String, Integer> referenceCount = new HashMap<>();
        List<String> nullifyCalls = new java.util.ArrayList<>();

        @Override public void save(IngestionCredential c) { data.put(c.id(), c); }
        @Override public Optional<IngestionCredential> findById(String id) { return Optional.ofNullable(data.get(id)); }
        @Override public Optional<IngestionCredential> findByName(String n) {
            return data.values().stream().filter(c -> c.name().equals(n)).findFirst();
        }
        @Override public List<IngestionCredential> findAll() { return List.copyOf(data.values()); }
        @Override public void deleteById(String id) { data.remove(id); }
        @Override public int countReferencingJobs(String id) { return referenceCount.getOrDefault(id, 0); }
        @Override public void nullifyCredentialOnJobs(String id) { nullifyCalls.add(id); }
    }
}
