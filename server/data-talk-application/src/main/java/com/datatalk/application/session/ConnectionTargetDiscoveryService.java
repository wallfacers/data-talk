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
        if (connection.databaseName() != null && !connection.databaseName().isBlank()) {
            databaseNames.add(connection.databaseName());
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
            try (var schemas = meta.getSchemas()) {
                while (schemas.next()) {
                    String name = schemas.getString("TABLE_SCHEM");
                    if (isUserSchema(name)) schemaNames.add(name);
                }
            } catch (Exception ignored) {
                // Some drivers do not expose schemas.
            }
        } catch (Exception ignored) {
            // Discovery should degrade gracefully. Configured databaseName is still useful.
        }

        return new DiscoveryResult(connection.id(), connection.name(), databaseNames, schemaNames);
    }

    private boolean isUserSchema(String schema) {
        if (schema == null || schema.isBlank()) return false;
        String normalized = schema.toLowerCase(Locale.ROOT);
        return !normalized.equals("information_schema")
            && !normalized.equals("pg_catalog")
            && !normalized.equals("sys")
            && !normalized.equals("system_lobs");
    }

    public record DiscoveryResult(
        String connectionId,
        String connectionName,
        Set<String> databaseNames,
        Set<String> schemaNames
    ) {}
}
