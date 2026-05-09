package com.datatalk.application.connection;

import com.datatalk.application.connection.multimode.CompatibilityMode;
import com.datatalk.application.connection.multimode.MultiModeConnectionShape;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SecretVault;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.stage.StageTabRepository;
import com.datatalk.dto.ConnectionDto;
import com.datatalk.domain.util.Strings;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
public class ConnectionService {

    private final ConnectionRepository repo;
    private final SessionRepository sessionRepo;
    private final StageTabRepository stageTabRepo;
    private final SecretVault vault;
    private final Clock clock;
    private final Translator translator;

    public ConnectionService(ConnectionRepository repo, SessionRepository sessionRepo,
                             StageTabRepository stageTabRepo, SecretVault vault,
                             Clock clock, Translator translator) {
        this.repo = repo;
        this.sessionRepo = sessionRepo;
        this.stageTabRepo = stageTabRepo;
        this.vault = vault;
        this.clock = clock;
        this.translator = translator;
    }

    private static final int DEFAULT_CONNECT_TIMEOUT = 3000;

    public String create(String name, String kind, String host, int port, String databaseName,
                         String username, String password, Integer connectTimeout,
                         String oracleServiceType,
                         Boolean sqlserverEncrypt, Boolean sqlserverTrustServerCertificate, String sqlserverInstanceName,
                         Boolean readOnly,
                         String compatibilityMode, String oceanbaseTenant, String oceanbaseCluster) {
        // Normalize kind via canonical normalizer (accepts aliases, rejects dameng short forms)
        String effectiveKind = ConnectionKind.normalize(kind);
        // Validate multi-mode connection shape for OceanBase
        if (ConnectionKind.OCEANBASE.equals(effectiveKind)) {
            CompatibilityMode mode = compatibilityMode != null ? CompatibilityMode.of(compatibilityMode) : CompatibilityMode.MYSQL;
            MultiModeConnectionShape.validateModeForKind(effectiveKind, mode);
            if (!MultiModeConnectionShape.isDay1FirstClassMode(effectiveKind, mode)) {
                throw new IllegalArgumentException(
                    translator.get("error.connection.dialect_unsupported", effectiveKind, mode.wireValue()));
            }
        }
        byte[] enc = vault.seal(password);
        String id = java.util.UUID.randomUUID().toString();
        int timeout = connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT;
        String effectiveName = Strings.defaultIfBlank(name, translator.get("connection.default_name", id.substring(0, 8)));
        int encrypt = sqlserverEncrypt != null ? (sqlserverEncrypt ? 1 : 0) : 1;
        boolean trustCert = sqlserverTrustServerCertificate == null || sqlserverTrustServerCertificate;
        boolean effectiveReadOnly = readOnly != null && readOnly;
        repo.insert(new ConnectionRecord(id, effectiveName, effectiveKind, host, port, databaseName, username, enc, null, clock.millis(), timeout, null, null, oracleServiceType, encrypt, trustCert, sqlserverInstanceName, effectiveReadOnly, compatibilityMode, oceanbaseTenant, oceanbaseCluster));
        return id;
    }

    public List<ConnectionDto> list() {
        return repo.findAll().stream().map(this::toDto).toList();
    }

    public ConnectionDto get(String id) {
        return repo.findById(id)
            .map(this::toDto)
            .orElseThrow(() -> new IllegalArgumentException(translator.get("error.connection.unknown", id)));
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
                       String username, String password, Integer connectTimeout,
                       String oracleServiceType,
                       Boolean sqlserverEncrypt, Boolean sqlserverTrustServerCertificate, String sqlserverInstanceName,
                       Boolean readOnly,
                       String compatibilityMode, String oceanbaseTenant, String oceanbaseCluster) {
        // Normalize kind via canonical normalizer (accepts aliases, rejects dameng short forms)
        String effectiveKind = ConnectionKind.normalize(kind);
        var existing = repo.findById(id)
            .orElseThrow(() -> new java.util.NoSuchElementException(translator.get("error.connection.unknown", id)));
        byte[] enc = password != null ? vault.seal(password) : existing.passwordEnc();
        int timeout = connectTimeout != null ? connectTimeout : existing.connectTimeout();
        String effectiveOracleType = oracleServiceType != null ? oracleServiceType : existing.oracleServiceType();
        int encrypt = sqlserverEncrypt != null ? (sqlserverEncrypt ? 1 : 0) : existing.sqlserverEncrypt();
        boolean trustCert = sqlserverTrustServerCertificate != null ? sqlserverTrustServerCertificate : existing.sqlserverTrustServerCertificate();
        String effectiveInstanceName = sqlserverInstanceName != null ? sqlserverInstanceName : existing.sqlserverInstanceName();
        boolean effectiveReadOnly = readOnly != null ? readOnly : existing.readOnly();
        String effectiveName = Strings.defaultIfBlank(name, translator.get("connection.default_name", id.substring(0, 8)));
        String effectiveCompatMode = compatibilityMode != null ? compatibilityMode : existing.compatibilityMode();
        String effectiveTenant = oceanbaseTenant != null ? oceanbaseTenant : existing.oceanbaseTenant();
        String effectiveCluster = oceanbaseCluster != null ? oceanbaseCluster : existing.oceanbaseCluster();
        // Validate multi-mode connection shape for OceanBase
        if (ConnectionKind.OCEANBASE.equals(effectiveKind)) {
            CompatibilityMode mode = effectiveCompatMode != null ? CompatibilityMode.of(effectiveCompatMode) : CompatibilityMode.MYSQL;
            MultiModeConnectionShape.validateModeForKind(effectiveKind, mode);
            if (!MultiModeConnectionShape.isDay1FirstClassMode(effectiveKind, mode)) {
                throw new IllegalArgumentException(
                    translator.get("error.connection.dialect_unsupported", effectiveKind, mode.wireValue()));
            }
        }
        repo.update(new ConnectionRecord(id, effectiveName, effectiveKind, host, port, databaseName, username,
            enc, existing.schemaDigest(), existing.createdAt(), timeout,
            existing.lastTestStatus(), existing.lastTestAt(), effectiveOracleType,
            encrypt, trustCert, effectiveInstanceName, effectiveReadOnly,
            effectiveCompatMode, effectiveTenant, effectiveCluster));
    }

    public boolean deleteById(String id) {
        var sessions = sessionRepo.listByConnection(id);
        if (!sessions.isEmpty()) {
            throw new ConnectionInUseException(translator.get("error.connection.delete_in_use_sessions", sessions.size()));
        }
        var tabs = stageTabRepo.list(new StageTabRepository.ListFilter(null, id, null, false, null, null, null, 1));
        if (!tabs.isEmpty()) {
            throw new ConnectionInUseException(translator.get("error.connection.delete_in_use_stage"));
        }
        return repo.deleteById(id);
    }

    public TestResult testConnection(String id) {
        var c = repo.findById(id)
            .orElseThrow(() -> new java.util.NoSuchElementException(translator.get("error.connection.unknown", id)));
        String password = vault.open(c.passwordEnc());
        String url = JdbcUrlBuilder.build(c);
        String kind = c.kind();
        if (kind.equals(ConnectionKind.DUCKDB)) {
            // DuckDB: no username/password needed, pass empty strings
            long started = clock.millis();
            try (var conn = java.sql.DriverManager.getConnection(url, "", "")) {
                boolean ok = conn.isValid(5);
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
        // OceanBase: compose username for MySQL-mode
        String effectiveUsername = kind.equals(ConnectionKind.OCEANBASE)
            ? composeOceanBaseUsername(c)
            : c.username();
        if (kind.equals(ConnectionKind.MYSQL) || kind.equals(ConnectionKind.TIDB)) {
            int timeoutMs = c.connectTimeout();
            url += (url.contains("?") ? "&" : "?") + "connectTimeout=" + timeoutMs + "&socketTimeout=" + timeoutMs;
        } else if (kind.equals(ConnectionKind.MARIADB)) {
            int timeoutMs = c.connectTimeout();
            url += (url.contains("?") ? "&" : "?") + "connectTimeout=" + timeoutMs;
        } else if (kind.equals(ConnectionKind.ORACLE)) {
            int timeoutSeconds = Math.max(1, c.connectTimeout() / 1000);
            java.sql.DriverManager.setLoginTimeout(timeoutSeconds);
        } else if (kind.equals(ConnectionKind.POSTGRESQL)) {
            int timeoutSeconds = c.connectTimeout() / 1000;
            url += (url.contains("?") ? "&" : "?") + "connectTimeout=" + timeoutSeconds + "&socketTimeout=" + timeoutSeconds;
        } else if (kind.equals(ConnectionKind.SQLSERVER)) {
            int timeoutSeconds = Math.max(1, c.connectTimeout() / 1000);
            url += ";loginTimeout=" + timeoutSeconds;
        } else if (kind.equals(ConnectionKind.CLICKHOUSE)) {
            int timeoutSeconds = Math.max(1, c.connectTimeout() / 1000);
            url += (url.contains("?") ? "&" : "?") + "connect_timeout=" + timeoutSeconds;
        } else if (kind.equals(ConnectionKind.APACHE_DORIS)) {
            int timeoutMs = c.connectTimeout();
            url += (url.contains("?") ? "&" : "?") + "connectTimeout=" + timeoutMs + "&socketTimeout=" + timeoutMs;
        } else if (kind.equals(ConnectionKind.STARROCKS)) {
            int timeoutMs = c.connectTimeout();
            url += (url.contains("?") ? "&" : "?") + "connectTimeout=" + timeoutMs + "&socketTimeout=" + timeoutMs;
        } else if (kind.equals(ConnectionKind.TRINO)) {
            java.sql.DriverManager.setLoginTimeout(Math.max(1, c.connectTimeout() / 1000));
        } else if (kind.equals(ConnectionKind.PRESTO)) {
            java.sql.DriverManager.setLoginTimeout(Math.max(1, c.connectTimeout() / 1000));
        } else if (kind.equals(ConnectionKind.HIVE)) {
            java.sql.DriverManager.setLoginTimeout(Math.max(1, c.connectTimeout() / 1000));
        } else if (kind.equals(ConnectionKind.DAMENG)) {
            int timeoutSeconds = Math.max(1, c.connectTimeout() / 1000);
            java.sql.DriverManager.setLoginTimeout(timeoutSeconds);
        }
        long started = clock.millis();
        try (var conn = java.sql.DriverManager.getConnection(url, effectiveUsername, password)) {
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

    /**
     * Compose OceanBase username: <user>@<tenant>[#<cluster>]
     */
    public static String composeOceanBaseUsername(ConnectionRecord c) {
        StringBuilder sb = new StringBuilder(c.username());
        sb.append('@').append(c.oceanbaseTenant());
        if (c.oceanbaseCluster() != null && !c.oceanbaseCluster().isBlank()) {
            sb.append('#').append(c.oceanbaseCluster());
        }
        return sb.toString();
    }

    public record TestResult(boolean ok, long latencyMs, String reason) {}

    private ConnectionDto toDto(ConnectionRecord c) {
        return new ConnectionDto(c.id(), c.name(), c.kind(), c.host(), c.port(),
            c.databaseName(), c.username(), c.createdAt(), c.connectTimeout(),
            c.lastTestStatus(), c.lastTestAt(), c.oracleServiceType(),
            c.sqlserverEncrypt() != 0, c.sqlserverTrustServerCertificate(), c.sqlserverInstanceName(),
            c.readOnly(), c.compatibilityMode(), c.oceanbaseTenant(), c.oceanbaseCluster());
    }
}
