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
        String effectiveUsername = ConnectionKind.OCEANBASE.equals(connection.kind())
            ? ConnectionService.composeOceanBaseUsername(connection)
            : connection.username();
        try (var jdbc = DriverManager.getConnection(
            JdbcUrlBuilder.build(connection),
            effectiveUsername,
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
            // KingbaseES follows PostgreSQL database discovery pattern
            if (ConnectionKind.KINGBASE.equals(connection.kind())) {
                try (var stmt = jdbc.createStatement();
                     var rs = stmt.executeQuery(
                         "SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname")) {
                    while (rs.next()) {
                        String name = rs.getString(1);
                        if (name != null && !name.isBlank()) databaseNames.add(name);
                    }
                } catch (Exception ignored) {
                    // Fall through
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
            // Dameng is Oracle-like: server-level connection, schema as primary namespace.
            if (ConnectionKind.DAMENG.equals(connection.kind())) {
                databaseNames.clear();
                if (configuredDatabase != null && !configuredDatabase.isBlank()) {
                    databaseNames.add(configuredDatabase);
                }
            }
            // SQL Server has two-level context (database + schema).
            // Filter system databases and discover schemas.
            if (ConnectionKind.SQLSERVER.equals(connection.kind())) {
                // SQL Server getCatalogs() returns all databases including system ones.
                // We query sys.databases to filter out system databases.
                try (var stmt = jdbc.createStatement();
                     var rs = stmt.executeQuery(
                         "SELECT name FROM sys.databases WHERE name NOT IN ('master','tempdb','model','msdb','resource') ORDER BY name")) {
                    databaseNames.clear();
                    while (rs.next()) {
                        String name = rs.getString(1);
                        if (name != null && !name.isBlank()) databaseNames.add(name);
                    }
                } catch (Exception ignored) {
                    // Fall through to getCatalogs() below.
                }
            }
            // ClickHouse uses SHOW DATABASES; filter system databases.
            if (ConnectionKind.CLICKHOUSE.equals(connection.kind())) {
                try (var stmt = jdbc.createStatement();
                     var rs = stmt.executeQuery("SHOW DATABASES")) {
                    databaseNames.clear();
                    while (rs.next()) {
                        String name = rs.getString(1);
                        if (name != null && !name.isBlank() && !isClickHouseSystemDatabase(name)) {
                            databaseNames.add(name);
                        }
                    }
                } catch (Exception ignored) {
                    // Fall through to getCatalogs() below.
                }
            }
            // Hive uses SHOW DATABASES; filter system databases.
            if (ConnectionKind.HIVE.equals(connection.kind())) {
                try (var stmt = jdbc.createStatement();
                     var rs = stmt.executeQuery("SHOW DATABASES")) {
                    databaseNames.clear();
                    while (rs.next()) {
                        String name = rs.getString(1);
                        if (name != null && !name.isBlank() && !isHiveSystemDatabase(name)) {
                            databaseNames.add(name);
                        }
                    }
                } catch (Exception ignored) {
                    // Fall through to getCatalogs() below.
                }
            }
            // Trino uses SHOW CATALOGS to enumerate catalogs.
            if (ConnectionKind.TRINO.equals(connection.kind())) {
                try (var stmt = jdbc.createStatement();
                     var rs = stmt.executeQuery("SHOW CATALOGS")) {
                    databaseNames.clear();
                    while (rs.next()) {
                        String name = rs.getString(1);
                        if (name != null && !name.isBlank() && !isTrinoSystemCatalog(name)) {
                            databaseNames.add(name);
                        }
                    }
                } catch (Exception ignored) {
                    // Fall through to getCatalogs() below.
                }
            }
            // Presto uses SHOW CATALOGS to enumerate catalogs.
            if (ConnectionKind.PRESTO.equals(connection.kind())) {
                try (var stmt = jdbc.createStatement();
                     var rs = stmt.executeQuery("SHOW CATALOGS")) {
                    databaseNames.clear();
                    while (rs.next()) {
                        String name = rs.getString(1);
                        if (name != null && !name.isBlank() && !isPrestoSystemCatalog(name)) {
                            databaseNames.add(name);
                        }
                    }
                } catch (Exception ignored) {
                    // Fall through to getCatalogs() below.
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

        databaseNames.removeIf(name -> isSystemDatabase(connection.kind(), name));
        return new DiscoveryResult(connection.id(), connection.name(), databaseNames, schemaNames);
    }

    boolean hasIndependentSchemaNamespace(String kind) {
        if (kind == null || kind.isBlank()) return true;
        String normalized = kind.toLowerCase(Locale.ROOT);
        return !ConnectionKind.MYSQL.equals(normalized)
            && !ConnectionKind.MARIADB.equals(normalized)
            && !ConnectionKind.SQLITE.equals(normalized)
            && !ConnectionKind.APACHE_DORIS.equals(normalized)
            && !ConnectionKind.STARROCKS.equals(normalized)
            && !ConnectionKind.HIVE.equals(normalized)
            && !ConnectionKind.TIDB.equals(normalized)
            && !ConnectionKind.DAMENG.equals(normalized);
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
            && !ORACLE_SYSTEM_SCHEMAS.contains(normalized)
            // Dameng system schemas to exclude
            && !DAMENG_SYSTEM_SCHEMAS.contains(normalized)
            // KingbaseES system schemas to exclude
            && !KINGBASE_SYSTEM_SCHEMAS.contains(normalized)
            // GaussDB system schemas to exclude
            && !GAUSSDB_SYSTEM_SCHEMAS.contains(normalized);
    }

    private boolean isClickHouseSystemDatabase(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("system")
            || normalized.equals("information_schema")
            || normalized.equals("_temporary_and_external_tables");
    }

    private boolean isHiveSystemDatabase(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("default")
            || normalized.equals("sys")
            || normalized.equals("information_schema");
    }

    private boolean isTrinoSystemCatalog(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("system")
            || normalized.equals("memory")
            || normalized.equals("jmx");
    }

    private boolean isPrestoSystemCatalog(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("system")
            || normalized.equals("jmx");
    }

    boolean isSystemDatabase(String kind, String name) {
        if (name == null || name.isBlank()) return false;
        String normalized = name.toLowerCase(Locale.ROOT);
        return systemDatabases(kind).stream()
            .anyMatch(sys -> sys.equalsIgnoreCase(normalized));
    }

    Set<String> systemDatabases(String kind) {
        if (kind == null || kind.isBlank()) return Set.of();
        String normalized = kind.toLowerCase(Locale.ROOT);
        if (ConnectionKind.TIDB.equals(normalized)) {
            return Set.of("INFORMATION_SCHEMA", "mysql", "PERFORMANCE_SCHEMA", "METRICS_SCHEMA", "sys");
        }
        if (ConnectionKind.MYSQL.equals(normalized)
            || ConnectionKind.MARIADB.equals(normalized)
            || ConnectionKind.APACHE_DORIS.equals(normalized)
            || ConnectionKind.STARROCKS.equals(normalized)) {
            return Set.of("INFORMATION_SCHEMA", "mysql", "PERFORMANCE_SCHEMA", "sys");
        }
        return Set.of();
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

    private static final Set<String> DAMENG_SYSTEM_SCHEMAS = Set.of(
        "sys",         // system objects
        "sysdba",      // DBA user
        "sysauditor",  // audit
        "syssso",      // security
        "ctisys"       // full-text indexing
    );

    private static final Set<String> KINGBASE_SYSTEM_SCHEMAS = Set.of(
        "pg_catalog", "information_schema", "pg_toast", "pg_temp",
        "sys", "sys_catalog"
    );

    private static final Set<String> GAUSSDB_SYSTEM_SCHEMAS = Set.of(
        "pg_catalog", "information_schema", "pg_toast", "pg_temp_1", "pg_toast_temp_1",
        "db_scheduler", "db4ai", "pkg_service", "sqladvisor", "wdr_snapshot", "snapshot"
    );

    public record DiscoveryResult(
        String connectionId,
        String connectionName,
        Set<String> databaseNames,
        Set<String> schemaNames
    ) {}
}
