package com.datatalk.application.session;

import com.datatalk.application.connection.ConnectionKind;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRepository;
import org.springframework.stereotype.Service;

import java.sql.DriverManager;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class ConnectionTargetDiscoveryService {

    private final ConnectionRepository connections;
    private final ConnectionService connectionService;
    private final Translator translator;

    public ConnectionTargetDiscoveryService(
        ConnectionRepository connections,
        ConnectionService connectionService,
        Translator translator
    ) {
        this.connections = connections;
        this.connectionService = connectionService;
        this.translator = translator;
    }

    public DiscoveryResult discover(String connectionId) {
        var connection = connections.findById(connectionId)
            .orElseThrow(() -> new NoSuchElementException(translator.get("error.connection.unknown", connectionId)));

        Set<String> databaseNames = new LinkedHashSet<>();
        String configuredDatabase = effectiveDatabaseName(connection.kind(), connection.databaseName());
        if (configuredDatabase != null && !configuredDatabase.isBlank()) {
            databaseNames.add(configuredDatabase);
        }

        Set<String> schemaNames = new LinkedHashSet<>();
        try (var jdbc = DriverManager.getConnection(
            JdbcUrlBuilder.build(connection),
            connection.username(),
            connectionService.decryptPassword(connectionId)
        )) {
            var meta = jdbc.getMetaData();
            // For PostgreSQL, getCatalogs() only returns the connected database.
            // Execute a direct query to enumerate all visible databases.
            if (ConnectionKind.POSTGRESQL.equals(connection.kind())) {
                try (var stmt = jdbc.createStatement();
                     var rs = stmt.executeQuery(
                         "SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname")) {
                    while (rs.next()) {
                        String name = rs.getString(1);
                        if (name != null && !name.isBlank()) databaseNames.add(name);
                    }
                } catch (Exception ignored) {
                    // Fall through to getCatalogs() below.
                }
            }
            // Oracle uses schemas/owners as the primary namespace (not catalogs/databases).
            // Discover schemas and treat them as the user-visible targets.
            if (ConnectionKind.ORACLE.equals(connection.kind())) {
                // Oracle does not enumerate "databases" via getCatalogs().
                // The configured service name / SID is the database, not a catalog entry.
                // Schema discovery below handles the rest.
                databaseNames.clear();
                if (configuredDatabase != null && !configuredDatabase.isBlank()) {
                    databaseNames.add(configuredDatabase);
                }
            }
            if (databaseNames.isEmpty()) {
                try (var catalogs = meta.getCatalogs()) {
                    while (catalogs.next()) {
                        String name = catalogs.getString(1);
                        if (name != null && !name.isBlank()) databaseNames.add(name);
                    }
                } catch (Exception ignored) {
                    // Some drivers do not expose catalogs. Keep configured databaseName only.
                }
            }
            if (hasIndependentSchemaNamespace(connection.kind())) {
                try (var schemas = meta.getSchemas()) {
                    while (schemas.next()) {
                        String name = schemas.getString("TABLE_SCHEM");
                        if (isUserSchema(name)) schemaNames.add(name);
                    }
                } catch (Exception ignored) {
                    // Some drivers do not expose schemas.
                }
            }
        } catch (Exception e) {
            if (ConnectionKind.SQLITE.equalsIgnoreCase(connection.kind())) {
                throw new IllegalStateException("SQLite target discovery failed for " + configuredDatabase, e);
            }
            // Discovery should degrade gracefully. Configured databaseName is still useful.
        }

        return new DiscoveryResult(connection.id(), connection.name(), databaseNames, schemaNames);
    }

    private boolean hasIndependentSchemaNamespace(String kind) {
        if (kind == null || kind.isBlank()) return true;
        String normalized = kind.toLowerCase(Locale.ROOT);
        return !ConnectionKind.MYSQL.equals(normalized)
            && !ConnectionKind.MARIADB.equals(normalized)
            && !ConnectionKind.SQLITE.equals(normalized);
    }

    private String effectiveDatabaseName(String kind, String databaseName) {
        if (databaseName != null && !databaseName.isBlank()) return databaseName;
        if (ConnectionKind.SQLITE.equalsIgnoreCase(kind)) return ":memory:";
        return databaseName;
    }

    private boolean isUserSchema(String schema) {
        if (schema == null || schema.isBlank()) return false;
        String normalized = schema.toLowerCase(Locale.ROOT);
        return !normalized.equals("information_schema")
            && !normalized.equals("pg_catalog")
            && !normalized.equals("sys")
            && !normalized.equals("system_lobs")
            // Oracle system schemas to exclude
            && !ORACLE_SYSTEM_SCHEMAS.contains(normalized);
    }

    private static final Set<String> ORACLE_SYSTEM_SCHEMAS = Set.of(
        "sys", "system", "dbsnmp", "appqossys", "dbsfwuser",
        "gsmadmin_internal", "lbacsys", "mdsys", "olapsys",
        "orddata", "ordplugins", "outln", "wmsys", "xdb",
        "xs$null", "ctxsys", "ordsys", "sdo", "dvsys",
        "oevmsys", "audsys", "ojsvd_users", "remote_scheduler_agent",
        "dip", "sysbackup", "sysdg", "syskm", "sysrac",
        "spatial_csw_admin_usr", "spatial_wfs_admin_usr"
    );

    public record DiscoveryResult(
        String connectionId,
        String connectionName,
        Set<String> databaseNames,
        Set<String> schemaNames
    ) {}
}
