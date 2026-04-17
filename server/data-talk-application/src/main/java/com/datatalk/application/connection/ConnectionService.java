package com.datatalk.application.connection;

import com.datatalk.dto.ConnectionDto;
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

    public void create(String kind, String host, int port, String databaseName,
                       String username, String password) {
        byte[] enc = vault.seal(password);
        String id = java.util.UUID.randomUUID().toString();
        repo.insert(new ConnectionRecord(id, kind, host, port, databaseName, username, enc, null, clock.millis()));
    }

    public List<ConnectionDto> list() {
        return repo.findAll().stream()
            .map(c -> new ConnectionDto(c.id(), c.kind(), c.host(), c.port(),
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

    public void update(String id, String kind, String host, int port, String databaseName,
                       String username, String password) {
        var existing = repo.findById(id)
            .orElseThrow(() -> new java.util.NoSuchElementException("unknown connection: " + id));
        byte[] enc = password != null ? vault.seal(password) : existing.passwordEnc();
        repo.update(new ConnectionRecord(id, kind, host, port, databaseName, username,
            enc, existing.schemaDigest(), existing.createdAt()));
    }

    public boolean deleteById(String id) {
        return repo.deleteById(id);
    }

    public TestResult testConnection(String id) {
        var c = repo.findById(id)
            .orElseThrow(() -> new java.util.NoSuchElementException("unknown connection: " + id));
        String password = vault.open(c.passwordEnc());
        String url = jdbcUrl(c);
        long started = System.nanoTime();
        try (var conn = java.sql.DriverManager.getConnection(url, c.username(), password)) {
            boolean ok = conn.isValid(3);
            long ms = (System.nanoTime() - started) / 1_000_000L;
            return new TestResult(ok, ms, ok ? null : "connection reported invalid");
        } catch (Throwable t) {
            long ms = (System.nanoTime() - started) / 1_000_000L;
            return new TestResult(false, ms, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static String jdbcUrl(ConnectionRecord c) {
        String db = c.databaseName();
        return switch (c.kind()) {
            case "mysql" -> "jdbc:mysql://" + c.host() + ":" + c.port() + "/"
                + (db != null ? db : "")
                + "?connectTimeout=3000&socketTimeout=3000";
            case "postgres", "postgresql" -> "jdbc:postgresql://" + c.host() + ":" + c.port() + "/"
                + (db != null ? db : "postgres")
                + "?connectTimeout=3&socketTimeout=3";
            case "h2" -> "jdbc:h2:" + (db != null ? db : "mem:test");
            default -> throw new IllegalArgumentException("unsupported kind: " + c.kind());
        };
    }

    public record TestResult(boolean ok, long latencyMs, String reason) {}
}
