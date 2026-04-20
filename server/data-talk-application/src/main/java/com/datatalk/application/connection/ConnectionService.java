package com.datatalk.application.connection;

import com.datatalk.application.i18n.Translator;
import com.datatalk.dto.ConnectionDto;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SecretVault;
import com.datatalk.domain.util.Strings;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
public class ConnectionService {

    private final ConnectionRepository repo;
    private final SecretVault vault;
    private final Clock clock;
    private final Translator translator;

    public ConnectionService(ConnectionRepository repo, SecretVault vault, Clock clock, Translator translator) {
        this.repo = repo;
        this.vault = vault;
        this.clock = clock;
        this.translator = translator;
    }

    private static final int DEFAULT_CONNECT_TIMEOUT = 3000;

    public String create(String name, String kind, String host, int port, String databaseName,
                         String username, String password, Integer connectTimeout) {
        byte[] enc = vault.seal(password);
        String id = java.util.UUID.randomUUID().toString();
        int timeout = connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT;
        String effectiveName = Strings.defaultIfBlank(name, translator.get("connection.default_name", id.substring(0, 8)));
        repo.insert(new ConnectionRecord(id, effectiveName, kind, host, port, databaseName, username, enc, null, clock.millis(), timeout, null, null));
        return id;
    }

    public List<ConnectionDto> list() {
        return repo.findAll().stream()
            .map(c -> new ConnectionDto(c.id(), c.name(), c.kind(), c.host(), c.port(),
                c.databaseName(), c.username(), c.createdAt(), c.connectTimeout(),
                c.lastTestStatus(), c.lastTestAt()))
            .toList();
    }

    public String decryptPassword(String id) {
        return repo.findById(id)
            .map(c -> vault.open(c.passwordEnc()))
            .orElseThrow(() -> new IllegalArgumentException(translator.get("error.connection.unknown", id)));
    }

    public void deleteAll() {
        repo.deleteAll();
    }

    public void update(String id, String name, String kind, String host, int port, String databaseName,
                       String username, String password, Integer connectTimeout) {
        var existing = repo.findById(id)
            .orElseThrow(() -> new java.util.NoSuchElementException(translator.get("error.connection.unknown", id)));
        byte[] enc = password != null ? vault.seal(password) : existing.passwordEnc();
        int timeout = connectTimeout != null ? connectTimeout : existing.connectTimeout();
        String effectiveName = Strings.defaultIfBlank(name, translator.get("connection.default_name", id.substring(0, 8)));
        repo.update(new ConnectionRecord(id, effectiveName, kind, host, port, databaseName, username,
            enc, existing.schemaDigest(), existing.createdAt(), timeout,
            existing.lastTestStatus(), existing.lastTestAt()));
    }

    public boolean deleteById(String id) {
        return repo.deleteById(id);
    }

    public TestResult testConnection(String id) {
        var c = repo.findById(id)
            .orElseThrow(() -> new java.util.NoSuchElementException(translator.get("error.connection.unknown", id)));
        String password = vault.open(c.passwordEnc());
        String url = JdbcUrlBuilder.build(c);
        String kind = c.kind();
        if (kind.equals(ConnectionKind.MYSQL)) {
            int timeoutMs = c.connectTimeout();
            url += (url.contains("?") ? "&" : "?") + "connectTimeout=" + timeoutMs + "&socketTimeout=" + timeoutMs;
        } else if (kind.equals(ConnectionKind.POSTGRESQL)) {
            int timeoutSeconds = c.connectTimeout() / 1000;
            url += (url.contains("?") ? "&" : "?") + "connectTimeout=" + timeoutSeconds + "&socketTimeout=" + timeoutSeconds;
        }
        long started = clock.millis();
        try (var conn = java.sql.DriverManager.getConnection(url, c.username(), password)) {
            boolean ok = conn.isValid(c.connectTimeout() / 1000);
            long ms = clock.millis() - started;
            repo.updateTestStatus(id, ok ? "ok" : "fail", clock.millis());
            return new TestResult(ok, ms, ok ? null : translator.get("connection.test.invalid"));
        } catch (Throwable t) {
            long ms = clock.millis() - started;
            repo.updateTestStatus(id, "fail", clock.millis());
            return new TestResult(false, ms, translator.get("connection.test.failure",
                t.getClass().getSimpleName(), t.getMessage()));
        }
    }

    public record TestResult(boolean ok, long latencyMs, String reason) {}
}
