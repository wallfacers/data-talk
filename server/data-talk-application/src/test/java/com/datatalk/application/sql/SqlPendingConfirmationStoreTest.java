package com.datatalk.application.sql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqlPendingConfirmationStoreTest {

    private SqlPendingConfirmationStore store;

    @BeforeEach
    void setUp() {
        store = new SqlPendingConfirmationStore();
    }

    private SqlPendingConfirmationStore.PendingConfirmation sampleConfirmation() {
        return new SqlPendingConfirmationStore.PendingConfirmation(
            "DELETE FROM users WHERE id = 1",
            "conn-1",
            "sess-1",
            "mydb",
            "public",
            "ai",
            List.of("users")
        );
    }

    @Test
    void createAndRetrieveConfirmation() {
        SqlPendingConfirmationStore.PendingConfirmation confirmation = sampleConfirmation();
        String id = store.create(confirmation);

        assertThat(id).isNotBlank();

        var retrieved = store.get(id);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().sql()).isEqualTo("DELETE FROM users WHERE id = 1");
        assertThat(retrieved.get().connectionId()).isEqualTo("conn-1");
        assertThat(retrieved.get().sessionId()).isEqualTo("sess-1");
        assertThat(retrieved.get().database()).isEqualTo("mydb");
        assertThat(retrieved.get().schema()).isEqualTo("public");
        assertThat(retrieved.get().source()).isEqualTo("ai");
        assertThat(retrieved.get().affectedObjects()).containsExactly("users");
    }

    @Test
    void removeWorks() {
        String id = store.create(sampleConfirmation());
        assertThat(store.get(id)).isPresent();

        boolean removed = store.remove(id);
        assertThat(removed).isTrue();
        assertThat(store.get(id)).isEmpty();
    }

    @Test
    void removeNonExistentReturnsFalse() {
        assertThat(store.remove("nonexistent")).isFalse();
    }

    @Test
    void nonExistentIdReturnsEmpty() {
        assertThat(store.get("nonexistent")).isEmpty();
    }

    @Test
    void cleanupDoesNotEvictFreshEntries() {
        String id = store.create(sampleConfirmation());
        assertThat(store.get(id)).isPresent();

        // Fresh entries should survive cleanup
        store.cleanup();
        assertThat(store.get(id)).isPresent();
    }

    @Test
    void multipleConfirmationsHaveUniqueIds() {
        String id1 = store.create(sampleConfirmation());
        String id2 = store.create(sampleConfirmation());
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void removeThenGetReturnsEmpty() {
        String id = store.create(sampleConfirmation());
        store.remove(id);
        assertThat(store.get(id)).isEmpty();
    }

    @Test
    void getDoesNotRemoveValidEntry() {
        String id = store.create(sampleConfirmation());
        // First get
        assertThat(store.get(id)).isPresent();
        // Second get should still return the entry
        assertThat(store.get(id)).isPresent();
    }
}
