package com.datatalk.application.connection;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SecretVault;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
public class ConnectionService {

    private final ConnectionRepository repo;
    private final SecretVault vault;
    private final Clock clock;

    public ConnectionService(ConnectionRepository repo, SecretVault vault, Clock clock) {
        this.repo = repo;
        this.vault = vault;
        this.clock = clock;
    }

    public void create(String id, String kind, String host, int port, String database,
                       String username, String password) {
        byte[] enc = vault.seal(password);
        repo.insert(new ConnectionRecord(id, kind, host, port, database, username, enc, null, clock.millis()));
    }

    public List<ConnectionView> list() {
        return repo.findAll().stream()
            .map(c -> new ConnectionView(c.id(), c.kind(), c.host(), c.port(),
                c.databaseName(), c.username(), c.createdAt()))
            .toList();
    }

    public String decryptPassword(String id) {
        return repo.findById(id)
            .map(c -> vault.open(c.passwordEnc()))
            .orElseThrow(() -> new IllegalArgumentException("unknown connection: " + id));
    }

    public void deleteAll() {
        repo.deleteAll();
    }

    /** Safe-to-serialize view. Omits password_enc. */
    public record ConnectionView(String id, String kind, String host, int port,
                                 String databaseName, String username, long createdAt) {}
}
