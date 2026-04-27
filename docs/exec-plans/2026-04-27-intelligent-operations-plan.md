# Intelligent Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a database diagnostics platform with EXPLAIN + index recommendations as the first slice, backed by a `DiagnosticsProvider` Strategy interface covering MySQL / PG / H2 / Oracle (stub), with dual entry points (SQL toolbar + AI tools) and a `DiagnosticTab` workbench surface.

**Architecture:** Domain models in `com.datatalk.domain.diagnostics`; `DiagnosticsProvider` interface + `DiagnosticsService` in `com.datatalk.application.diagnostics`; dialect implementations in `com.datatalk.infra.diagnostics`; two real Actions + three stub Actions in adapter; REST controller mirrors Action output for frontend toolbar calls.

**Tech Stack:** Java 21, Spring Boot 3.5, JdbcTemplate, JUnit 5 + AssertJ + MockMvc, React 19, TypeScript, Zustand, Vitest, shadcn/ui.

**Spec:** [docs/product-specs/2026-04-27-intelligent-operations-design.md](../product-specs/2026-04-27-intelligent-operations-design.md)

**Execution order:** Batch S (Tasks 1–2, sequential) → Batch P (Tasks 3–10, parallel) → Batch F (Tasks 11–18, parallel) → Batch U (Tasks 19–20, sequential).

---

## Status

- 2026-04-27 — Completed. All batches executed. Deviation from plan: `DiagnosticsProvider` interface takes `String decryptedPassword` as separate parameter (original plan passed `ConnectionRecord` with decrypted password, but `passwordEnc` is `byte[]`). Backend 45 new tests green. Frontend 722 tests green. Pre-existing FlywayMigrationIT + EndToEndSmokeIT failures unrelated to this change.

---

## File Structure Map

```
server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/
  DiagnosticCapability.java        NEW — enum
  ScanType.java                    NEW — enum
  Impact.java                      NEW — enum
  ExplainNode.java                 NEW — record
  ExplainPlan.java                 NEW — record
  IndexRecommendation.java         NEW — record
  LockEntry.java                   NEW — record (stub)
  LockReport.java                  NEW — record (stub)
  PoolReport.java                  NEW — record (stub)
  TableSpaceEntry.java             NEW — record (stub)
  SpaceReport.java                 NEW — record (stub)
  DiagnosticResult.java            NEW — sealed interface

server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/
  DiagnosticsProvider.java         NEW — interface
  DiagnosticsProviderRegistry.java NEW — registry bean
  DiagnosticsService.java          NEW — orchestration service

server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/
  MySqlDiagnosticsProvider.java    NEW — EXPLAIN FORMAT=JSON
  PostgreSqlDiagnosticsProvider.java NEW — EXPLAIN (FORMAT JSON)
  H2DiagnosticsProvider.java       NEW — text EXPLAIN
  OracleDiagnosticsProvider.java   NEW — stub

server/data-talk-adapter/src/main/java/com/datatalk/adapter/
  actions/ExplainQueryAction.java  NEW
  actions/IndexHintsAction.java    NEW
  actions/LockInfoAction.java      NEW (stub)
  actions/PoolStatusAction.java    NEW (stub)
  actions/TableSpaceAction.java    NEW (stub)
  controller/DiagnosticsController.java NEW

server/data-talk-adapter/src/main/resources/
  messages.properties              MODIFY
  messages_zh_CN.properties        MODIFY
  agents/AGENTS.md                 MODIFY

client/src/
  services/api/diagnostics.ts                           NEW
  features/stage/types/diagnostics.ts                   NEW
  features/stage/registry/tab-type-registry.ts          MODIFY
  features/stage/components/diagnostics/
    explain-plan-tree.tsx                               NEW
    explain-plan-tree.test.tsx                          NEW
    index-recommendation-list.tsx                       NEW
    index-recommendation-list.test.tsx                  NEW
    diagnostics-tab.tsx                                 NEW
    diagnostics-tab.test.tsx                            NEW
  features/chat/components/tools/renderers/
    diagnostics-card.tsx                                NEW
    diagnostics-card.test.tsx                           NEW
  features/stage/components/activity-rail/
    diagnostics-panel.tsx                               NEW
    diagnostics-panel.test.tsx                          NEW
    stage-activity-rail.tsx                             MODIFY
  features/stage/components/sql-editor-toolbar.tsx      MODIFY
  stores/stage-store.ts                                 MODIFY (RailPanel type)
  i18n/messages.ts                                      MODIFY
```

---

## Batch S — Backend Foundation (sequential)

### Task 1: Domain Models

**Files:**
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/DiagnosticCapability.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/ScanType.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/Impact.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/ExplainNode.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/ExplainPlan.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/IndexRecommendation.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/LockReport.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/PoolReport.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/SpaceReport.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/DiagnosticResult.java`
- Test: `server/data-talk-domain/src/test/java/com/datatalk/domain/diagnostics/DiagnosticResultTest.java`

- [x] **Step 1.1: Write DiagnosticCapability.java**

```java
package com.datatalk.domain.diagnostics;

public enum DiagnosticCapability {
    EXPLAIN, INDEX_HINTS, LOCK_INFO, CONNECTION_POOL, TABLE_SPACE
}
```

- [x] **Step 1.2: Write ScanType.java**

```java
package com.datatalk.domain.diagnostics;

public enum ScanType {
    FULL_SCAN, INDEX_RANGE, INDEX_SCAN, CONST, REF, OTHER
}
```

- [x] **Step 1.3: Write Impact.java**

```java
package com.datatalk.domain.diagnostics;

public enum Impact { HIGH, MEDIUM, LOW }
```

- [x] **Step 1.4: Write ExplainNode.java**

```java
package com.datatalk.domain.diagnostics;

import java.util.List;

public record ExplainNode(
    String operation,
    String table,
    ScanType scanType,
    long rows,
    Double cost,
    String extra,
    List<ExplainNode> children
) {}
```

- [x] **Step 1.5: Write ExplainPlan.java**

```java
package com.datatalk.domain.diagnostics;

import java.util.List;

public record ExplainPlan(
    String dialect,
    String rawText,
    List<ExplainNode> nodes,
    Double totalCostEstimate,
    List<String> warnings
) {}
```

- [x] **Step 1.6: Write IndexRecommendation.java**

```java
package com.datatalk.domain.diagnostics;

import java.util.List;

public record IndexRecommendation(
    String table,
    List<String> columns,
    String indexType,
    Impact impact,
    String rationale
) {}
```

- [x] **Step 1.7: Write stub report records**

```java
// LockReport.java
package com.datatalk.domain.diagnostics;
import java.util.List;
public record LockReport(List<LockEntry> locks) {
    public record LockEntry(String table, String lockType, String holder, String waiter) {}
}

// PoolReport.java
package com.datatalk.domain.diagnostics;
public record PoolReport(int active, int idle, int maxSize, String poolName) {}

// SpaceReport.java
package com.datatalk.domain.diagnostics;
import java.util.List;
public record SpaceReport(List<TableSpaceEntry> tables) {
    public record TableSpaceEntry(String table, long rowCount, long dataSizeBytes, long indexSizeBytes) {}
}
```

- [x] **Step 1.8: Write DiagnosticResult.java**

```java
package com.datatalk.domain.diagnostics;

public sealed interface DiagnosticResult<T>
    permits DiagnosticResult.Ok, DiagnosticResult.Unsupported, DiagnosticResult.DiagnosticError {

    record Ok<T>(T value) implements DiagnosticResult<T> {}
    record Unsupported<T>(String reason) implements DiagnosticResult<T> {}
    record DiagnosticError<T>(String errorType, String message) implements DiagnosticResult<T> {}

    static <T> DiagnosticResult<T> ok(T value) { return new Ok<>(value); }
    static <T> DiagnosticResult<T> unsupported(String reason) { return new Unsupported<>(reason); }
    static <T> DiagnosticResult<T> error(String errorType, String message) { return new DiagnosticError<>(errorType, message); }

    default boolean isOk() { return this instanceof Ok; }
    default boolean isUnsupported() { return this instanceof Unsupported; }
}
```

- [x] **Step 1.9: Write DiagnosticResultTest.java**

```java
package com.datatalk.domain.diagnostics;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticResultTest {

    @Test
    void ok_isOk() {
        var result = DiagnosticResult.ok("value");
        assertThat(result.isOk()).isTrue();
        assertThat(result.isUnsupported()).isFalse();
        assertThat(((DiagnosticResult.Ok<String>) result).value()).isEqualTo("value");
    }

    @Test
    void unsupported_isUnsupported() {
        var result = DiagnosticResult.<String>unsupported("not available");
        assertThat(result.isOk()).isFalse();
        assertThat(result.isUnsupported()).isTrue();
        assertThat(((DiagnosticResult.Unsupported<String>) result).reason()).isEqualTo("not available");
    }

    @Test
    void error_isError() {
        var result = DiagnosticResult.<String>error("SQL_ERROR", "syntax error");
        assertThat(result.isOk()).isFalse();
        assertThat(result.isUnsupported()).isFalse();
        assertThat(result).isInstanceOf(DiagnosticResult.DiagnosticError.class);
    }
}
```

- [x] **Step 1.10: Run domain compile check**

```bash
cd /home/wallfacers/project/data-talk/server && mvn compile -q -pl data-talk-domain
```
Expected: BUILD SUCCESS, zero errors.

---

### Task 2: Application Layer — DiagnosticsProvider Interface + Service

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsProvider.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsProviderRegistry.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsService.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/diagnostics/DiagnosticsServiceTest.java`

- [x] **Step 2.1: Write DiagnosticsProvider.java**

```java
package com.datatalk.application.diagnostics;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import java.util.List;
import java.util.Set;

public interface DiagnosticsProvider {
    /** Returns the driverType values this provider handles, e.g. "mysql", "postgresql". */
    Set<String> supportedDriverTypes();
    Set<DiagnosticCapability> supportedCapabilities();

    DiagnosticResult<ExplainPlan>              explain(String sql, ConnectionRecord conn, String database, String schema);
    DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan, ConnectionRecord conn);
    DiagnosticResult<LockReport>               lockInfo(ConnectionRecord conn, String database);
    DiagnosticResult<PoolReport>               connectionPoolInfo(ConnectionRecord conn);
    DiagnosticResult<SpaceReport>              tableSpaceInfo(ConnectionRecord conn, String database);
}
```

- [x] **Step 2.2: Write DiagnosticsProviderRegistry.java**

```java
package com.datatalk.application.diagnostics;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class DiagnosticsProviderRegistry {

    private final List<DiagnosticsProvider> providers;

    public DiagnosticsProviderRegistry(List<DiagnosticsProvider> providers) {
        this.providers = providers;
    }

    public Optional<DiagnosticsProvider> find(String driverType) {
        if (driverType == null) return Optional.empty();
        String normalized = driverType.toLowerCase();
        return providers.stream()
            .filter(p -> p.supportedDriverTypes().contains(normalized))
            .findFirst();
    }
}
```

- [x] **Step 2.3: Write DiagnosticsService.java**

```java
package com.datatalk.application.diagnostics;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.session.ResolvedExecutionContext;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.domain.diagnostics.*;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiagnosticsService {

    private final DiagnosticsProviderRegistry registry;
    private final ConnectionRepository connRepo;
    private final ConnectionService connSvc;
    private final SessionDataContextService sessionContexts;

    public DiagnosticsService(DiagnosticsProviderRegistry registry,
                               ConnectionRepository connRepo,
                               ConnectionService connSvc,
                               SessionDataContextService sessionContexts) {
        this.registry = registry;
        this.connRepo = connRepo;
        this.connSvc = connSvc;
        this.sessionContexts = sessionContexts;
    }

    public DiagnosticResult<ExplainPlan> explain(String sessionId, String sql) {
        var ctx = resolveContext(sessionId);
        var provider = requireProvider(ctx.connection().kind());
        if (!provider.supportedCapabilities().contains(DiagnosticCapability.EXPLAIN)) {
            return DiagnosticResult.unsupported("EXPLAIN not supported for dialect: " + ctx.connection().kind());
        }
        return provider.explain(sql, withDecryptedPassword(ctx.connection()), ctx.database(), ctx.schema());
    }

    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sessionId, String sql) {
        var ctx = resolveContext(sessionId);
        var provider = requireProvider(ctx.connection().kind());
        if (!provider.supportedCapabilities().contains(DiagnosticCapability.INDEX_HINTS)) {
            return DiagnosticResult.unsupported("Index hints not supported for dialect: " + ctx.connection().kind());
        }
        var explainResult = provider.explain(sql, withDecryptedPassword(ctx.connection()), ctx.database(), ctx.schema());
        if (!explainResult.isOk()) return DiagnosticResult.unsupported("EXPLAIN failed, cannot compute index hints");
        var plan = ((DiagnosticResult.Ok<ExplainPlan>) explainResult).value();
        return provider.indexHints(sql, plan, withDecryptedPassword(ctx.connection()));
    }

    private DiagnosticsProvider requireProvider(String driverType) {
        return registry.find(driverType)
            .orElseThrow(() -> new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING,
                "No diagnostics provider for dialect: " + driverType, false));
    }

    private ResolvedExecutionContext resolveContext(String sessionId) {
        var sessionCtx = sessionContexts.get(sessionId);
        String connectionId = sessionCtx.connectionId();
        if (connectionId == null || connectionId.isBlank()) {
            throw new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING, "No active connection in session", false);
        }
        ConnectionRecord conn = connRepo.findById(connectionId)
            .orElseThrow(() -> new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING,
                "Connection not found: " + connectionId, false));
        return new ResolvedExecutionContext(conn, sessionCtx.databaseName(), sessionCtx.schemaName());
    }

    private ConnectionRecord withDecryptedPassword(ConnectionRecord conn) {
        String pwd = connSvc.decryptPassword(conn.id());
        return new ConnectionRecord(conn.id(), conn.name(), conn.kind(), conn.host(), conn.port(),
            conn.databaseName(), conn.username(), pwd, conn.schemaDigest(), conn.createdAt(),
            conn.connectTimeout(), conn.lastTestStatus(), conn.lastTestAt());
    }
}
```

- [x] **Step 2.4: Write DiagnosticsServiceTest.java**

```java
package com.datatalk.application.diagnostics;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.session.SessionDataContextRecord;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DiagnosticsServiceTest {

    DiagnosticsProviderRegistry registry;
    ConnectionRepository connRepo;
    ConnectionService connSvc;
    SessionDataContextService sessionContexts;
    DiagnosticsService service;
    DiagnosticsProvider mockProvider;

    @BeforeEach
    void setUp() {
        mockProvider = mock(DiagnosticsProvider.class);
        registry = mock(DiagnosticsProviderRegistry.class);
        connRepo = mock(ConnectionRepository.class);
        connSvc = mock(ConnectionService.class);
        sessionContexts = mock(SessionDataContextService.class);
        service = new DiagnosticsService(registry, connRepo, connSvc, sessionContexts);
    }

    @Test
    void explain_delegatesToProvider() {
        var conn = testConn("mysql");
        when(sessionContexts.get("s1")).thenReturn(new SessionDataContextRecord("s1", "c1", null, "db", null, null));
        when(connRepo.findById("c1")).thenReturn(Optional.of(conn));
        when(connSvc.decryptPassword("c1")).thenReturn("pass");
        when(registry.find("mysql")).thenReturn(Optional.of(mockProvider));
        when(mockProvider.supportedCapabilities()).thenReturn(Set.of(DiagnosticCapability.EXPLAIN));
        var plan = new ExplainPlan("mysql", "raw", List.of(), null, List.of());
        when(mockProvider.explain(eq("SELECT 1"), any(), eq("db"), isNull()))
            .thenReturn(DiagnosticResult.ok(plan));

        var result = service.explain("s1", "SELECT 1");

        assertThat(result.isOk()).isTrue();
        assertThat(((DiagnosticResult.Ok<ExplainPlan>) result).value().dialect()).isEqualTo("mysql");
    }

    @Test
    void explain_returnsUnsupported_whenProviderNotFound() {
        var conn = testConn("oracle");
        when(sessionContexts.get("s1")).thenReturn(new SessionDataContextRecord("s1", "c1", null, "db", null, null));
        when(connRepo.findById("c1")).thenReturn(Optional.of(conn));
        when(registry.find("oracle")).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
            () -> service.explain("s1", "SELECT 1"));
    }

    @Test
    void explain_returnsUnsupported_whenCapabilityMissing() {
        var conn = testConn("mysql");
        when(sessionContexts.get("s1")).thenReturn(new SessionDataContextRecord("s1", "c1", null, "db", null, null));
        when(connRepo.findById("c1")).thenReturn(Optional.of(conn));
        when(connSvc.decryptPassword("c1")).thenReturn("pass");
        when(registry.find("mysql")).thenReturn(Optional.of(mockProvider));
        when(mockProvider.supportedCapabilities()).thenReturn(Set.of());

        var result = service.explain("s1", "SELECT 1");

        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void indexHints_chainsExplainFirst() {
        var conn = testConn("mysql");
        when(sessionContexts.get("s1")).thenReturn(new SessionDataContextRecord("s1", "c1", null, "db", null, null));
        when(connRepo.findById("c1")).thenReturn(Optional.of(conn));
        when(connSvc.decryptPassword("c1")).thenReturn("pass");
        when(registry.find("mysql")).thenReturn(Optional.of(mockProvider));
        when(mockProvider.supportedCapabilities()).thenReturn(Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS));
        var plan = new ExplainPlan("mysql", "raw", List.of(), null, List.of());
        when(mockProvider.explain(any(), any(), any(), any())).thenReturn(DiagnosticResult.ok(plan));
        when(mockProvider.indexHints(any(), eq(plan), any())).thenReturn(DiagnosticResult.ok(List.of()));

        var result = service.indexHints("s1", "SELECT 1");

        assertThat(result.isOk()).isTrue();
        verify(mockProvider).explain(any(), any(), any(), any());
        verify(mockProvider).indexHints(any(), eq(plan), any());
    }

    private ConnectionRecord testConn(String kind) {
        return new ConnectionRecord("c1", "test", kind, "localhost", 3306,
            "db", "user", "encpwd", null, 0L, 5000, null, null);
    }
}
```

- [x] **Step 2.5: Run application layer tests**

```bash
cd /home/wallfacers/project/data-talk/server && mvn test -q -pl data-talk-application -Dtest=DiagnosticsServiceTest
```
Expected: Tests run: 4, Failures: 0.

---

## Batch P — Providers + Actions + Controller (parallel)

### Task 3: MySqlDiagnosticsProvider

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/MySqlDiagnosticsProvider.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/MySqlDiagnosticsProviderTest.java`

- [x] **Step 3.1: Write MySqlDiagnosticsProvider.java**

```java
package com.datatalk.infra.diagnostics;

import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.diagnostics.DiagnosticsProvider;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

@Component
public class MySqlDiagnosticsProvider implements DiagnosticsProvider {

    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public Set<String> supportedDriverTypes() { return Set.of("mysql"); }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS);
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String database, String schema) {
        String explainSql = "EXPLAIN FORMAT=JSON " + sql;
        try (Connection c = DriverManager.getConnection(JdbcUrlBuilder.build(conn), conn.username(), conn.passwordEnc());
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(explainSql)) {
            if (!rs.next()) return DiagnosticResult.error("EMPTY_RESULT", "EXPLAIN returned no rows");
            String raw = rs.getString(1);
            List<ExplainNode> nodes = parseMysqlJson(raw);
            List<String> warnings = collectWarnings(nodes);
            return DiagnosticResult.ok(new ExplainPlan("mysql", raw, nodes, null, warnings));
        } catch (SQLException e) {
            return DiagnosticResult.error("SQL_ERROR", e.getMessage());
        }
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan, ConnectionRecord conn) {
        List<IndexRecommendation> recs = new ArrayList<>();
        collectRecommendations(plan.nodes(), recs);
        return DiagnosticResult.ok(recs);
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String database) {
        return DiagnosticResult.unsupported("Lock analysis not yet implemented for MySQL");
    }

    @Override
    public DiagnosticResult<PoolReport> connectionPoolInfo(ConnectionRecord conn) {
        return DiagnosticResult.unsupported("Connection pool info not yet implemented for MySQL");
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String database) {
        return DiagnosticResult.unsupported("Table space info not yet implemented for MySQL");
    }

    private List<ExplainNode> parseMysqlJson(String raw) {
        try {
            JsonNode root = OM.readTree(raw);
            JsonNode qb = root.path("query_block");
            return parseQueryBlock(qb);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<ExplainNode> parseQueryBlock(JsonNode qb) {
        List<ExplainNode> result = new ArrayList<>();
        if (qb.has("table")) {
            result.add(parseTable(qb.path("table")));
        }
        if (qb.has("nested_loop")) {
            for (JsonNode item : qb.path("nested_loop")) {
                if (item.has("table")) result.add(parseTable(item.path("table")));
            }
        }
        return result;
    }

    private ExplainNode parseTable(JsonNode t) {
        String tableName = t.path("table_name").asText(null);
        String accessType = t.path("access_type").asText("ALL");
        long rows = t.path("rows_examined_per_scan").asLong(0);
        String extra = t.path("message").asText(null);
        ScanType scanType = mapAccessType(accessType);
        return new ExplainNode(accessType, tableName, scanType, rows, null, extra, List.of());
    }

    private ScanType mapAccessType(String accessType) {
        return switch (accessType.toLowerCase()) {
            case "all"             -> ScanType.FULL_SCAN;
            case "range"           -> ScanType.INDEX_RANGE;
            case "ref", "eq_ref"   -> ScanType.REF;
            case "index"           -> ScanType.INDEX_SCAN;
            case "const", "system" -> ScanType.CONST;
            default                -> ScanType.OTHER;
        };
    }

    private List<String> collectWarnings(List<ExplainNode> nodes) {
        List<String> warnings = new ArrayList<>();
        for (ExplainNode n : nodes) {
            if (n.scanType() == ScanType.FULL_SCAN && n.table() != null) {
                warnings.add("Full table scan on " + n.table() + " (est. " + n.rows() + " rows)");
            }
        }
        return warnings;
    }

    private void collectRecommendations(List<ExplainNode> nodes, List<IndexRecommendation> out) {
        for (ExplainNode n : nodes) {
            if (n.scanType() == ScanType.FULL_SCAN && n.table() != null && n.rows() > 1000) {
                out.add(new IndexRecommendation(n.table(), List.of("<column_used_in_WHERE>"),
                    "BTREE", Impact.HIGH,
                    "Full table scan on " + n.table() + " (" + n.rows() + " rows). Add index on WHERE/JOIN columns."));
            } else if (n.scanType() == ScanType.FULL_SCAN && n.table() != null) {
                out.add(new IndexRecommendation(n.table(), List.of("<column_used_in_WHERE>"),
                    "BTREE", Impact.MEDIUM,
                    "Full table scan on " + n.table() + ". Consider an index if the table grows."));
            }
            collectRecommendations(n.children(), out);
        }
    }
}
```

- [x] **Step 3.2: Write MySqlDiagnosticsProviderTest.java**

```java
package com.datatalk.infra.diagnostics;

import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;

class MySqlDiagnosticsProviderTest {

    MySqlDiagnosticsProvider provider = new MySqlDiagnosticsProvider();

    @Test
    void supportedDriverTypes_containsMySQL() {
        assertThat(provider.supportedDriverTypes()).contains("mysql");
    }

    @Test
    void supportedCapabilities_hasExplainAndIndexHints() {
        assertThat(provider.supportedCapabilities())
            .contains(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS);
    }

    @Test
    void lockInfo_returnsUnsupported() {
        var result = provider.lockInfo(null, null);
        assertThat(result.isUnsupported()).isTrue();
    }

    @Test
    void mapAccessType_ALL_returnsFULL_SCAN() throws Exception {
        var method = MySqlDiagnosticsProvider.class.getDeclaredMethod("mapAccessType", String.class);
        method.setAccessible(true);
        assertThat(method.invoke(provider, "ALL")).isEqualTo(ScanType.FULL_SCAN);
        assertThat(method.invoke(provider, "const")).isEqualTo(ScanType.CONST);
        assertThat(method.invoke(provider, "ref")).isEqualTo(ScanType.REF);
        assertThat(method.invoke(provider, "range")).isEqualTo(ScanType.INDEX_RANGE);
        assertThat(method.invoke(provider, "index")).isEqualTo(ScanType.INDEX_SCAN);
    }

    @Test
    void indexHints_fullScanAbove1000rows_highImpact() throws Exception {
        var node = new ExplainNode("ALL", "orders", ScanType.FULL_SCAN, 5000L, null, null, List.of());
        var plan = new ExplainPlan("mysql", "raw", List.of(node), null, List.of());
        var result = provider.indexHints("SELECT * FROM orders", plan, null);
        assertThat(result.isOk()).isTrue();
        var recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).impact()).isEqualTo(Impact.HIGH);
        assertThat(recs.get(0).table()).isEqualTo("orders");
    }
}
```

---

### Task 4: PostgreSqlDiagnosticsProvider

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/PostgreSqlDiagnosticsProvider.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/PostgreSqlDiagnosticsProviderTest.java`

- [x] **Step 4.1: Write PostgreSqlDiagnosticsProvider.java**

```java
package com.datatalk.infra.diagnostics;

import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.diagnostics.DiagnosticsProvider;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

@Component
public class PostgreSqlDiagnosticsProvider implements DiagnosticsProvider {

    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public Set<String> supportedDriverTypes() { return Set.of("postgresql", "postgres"); }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS);
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String database, String schema) {
        String explainSql = "EXPLAIN (FORMAT JSON, ANALYZE false) " + sql;
        try (Connection c = DriverManager.getConnection(JdbcUrlBuilder.build(conn), conn.username(), conn.passwordEnc());
             Statement st = c.createStatement()) {
            if (schema != null && !schema.isBlank()) c.setSchema(schema);
            try (ResultSet rs = st.executeQuery(explainSql)) {
                if (!rs.next()) return DiagnosticResult.error("EMPTY_RESULT", "EXPLAIN returned no rows");
                String raw = rs.getString(1);
                List<ExplainNode> nodes = parsePgJson(raw);
                List<String> warnings = collectWarnings(nodes);
                return DiagnosticResult.ok(new ExplainPlan("postgresql", raw, nodes, extractTotalCost(raw), warnings));
            }
        } catch (SQLException e) {
            return DiagnosticResult.error("SQL_ERROR", e.getMessage());
        }
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan, ConnectionRecord conn) {
        List<IndexRecommendation> recs = new ArrayList<>();
        collectRecommendations(plan.nodes(), recs);
        return DiagnosticResult.ok(recs);
    }

    @Override public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String database) {
        return DiagnosticResult.unsupported("Lock analysis not yet implemented for PostgreSQL");
    }
    @Override public DiagnosticResult<PoolReport> connectionPoolInfo(ConnectionRecord conn) {
        return DiagnosticResult.unsupported("Connection pool info not yet implemented for PostgreSQL");
    }
    @Override public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String database) {
        return DiagnosticResult.unsupported("Table space info not yet implemented for PostgreSQL");
    }

    private List<ExplainNode> parsePgJson(String raw) {
        try {
            JsonNode root = OM.readTree(raw);
            JsonNode planRoot = root.isArray() ? root.get(0) : root;
            JsonNode plan = planRoot.path("Plan");
            return List.of(parsePgNode(plan));
        } catch (Exception e) {
            return List.of();
        }
    }

    private ExplainNode parsePgNode(JsonNode node) {
        String nodeType = node.path("Node Type").asText("Unknown");
        String relation = node.path("Relation Name").asText(null);
        long rows = node.path("Plan Rows").asLong(0);
        double cost = node.path("Total Cost").asDouble(0);
        ScanType scanType = mapNodeType(nodeType);
        List<ExplainNode> children = new ArrayList<>();
        for (JsonNode child : node.path("Plans")) {
            children.add(parsePgNode(child));
        }
        return new ExplainNode(nodeType, relation, scanType, rows, cost, null, children);
    }

    private ScanType mapNodeType(String nodeType) {
        return switch (nodeType) {
            case "Seq Scan"                                    -> ScanType.FULL_SCAN;
            case "Index Scan", "Index Only Scan"               -> ScanType.INDEX_SCAN;
            case "Bitmap Index Scan", "Bitmap Heap Scan"       -> ScanType.INDEX_RANGE;
            default                                            -> ScanType.OTHER;
        };
    }

    private Double extractTotalCost(String raw) {
        try {
            JsonNode root = OM.readTree(raw);
            JsonNode planRoot = root.isArray() ? root.get(0) : root;
            double cost = planRoot.path("Plan").path("Total Cost").asDouble(-1);
            return cost < 0 ? null : cost;
        } catch (Exception e) { return null; }
    }

    private List<String> collectWarnings(List<ExplainNode> nodes) {
        List<String> warnings = new ArrayList<>();
        for (ExplainNode n : nodes) {
            if (n.scanType() == ScanType.FULL_SCAN && n.table() != null) {
                warnings.add("Sequential scan on " + n.table() + " (est. " + n.rows() + " rows)");
            }
            warnings.addAll(collectWarnings(n.children()));
        }
        return warnings;
    }

    private void collectRecommendations(List<ExplainNode> nodes, List<IndexRecommendation> out) {
        for (ExplainNode n : nodes) {
            if (n.scanType() == ScanType.FULL_SCAN && n.table() != null && n.rows() > 1000) {
                out.add(new IndexRecommendation(n.table(), List.of("<column_used_in_WHERE>"),
                    "BTREE", Impact.HIGH,
                    "Sequential scan on " + n.table() + " (" + n.rows() + " rows). Add index on WHERE/JOIN columns."));
            }
            collectRecommendations(n.children(), out);
        }
    }
}
```

- [x] **Step 4.2: Write PostgreSqlDiagnosticsProviderTest.java**

```java
package com.datatalk.infra.diagnostics;

import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PostgreSqlDiagnosticsProviderTest {

    PostgreSqlDiagnosticsProvider provider = new PostgreSqlDiagnosticsProvider();

    @Test
    void supportedDriverTypes_containsPostgresqlAndPostgres() {
        assertThat(provider.supportedDriverTypes()).containsExactlyInAnyOrder("postgresql", "postgres");
    }

    @Test
    void mapNodeType_SeqScan_returnsFULL_SCAN() throws Exception {
        var method = PostgreSqlDiagnosticsProvider.class.getDeclaredMethod("mapNodeType", String.class);
        method.setAccessible(true);
        assertThat(method.invoke(provider, "Seq Scan")).isEqualTo(ScanType.FULL_SCAN);
        assertThat(method.invoke(provider, "Index Scan")).isEqualTo(ScanType.INDEX_SCAN);
        assertThat(method.invoke(provider, "Bitmap Index Scan")).isEqualTo(ScanType.INDEX_RANGE);
        assertThat(method.invoke(provider, "Hash Join")).isEqualTo(ScanType.OTHER);
    }

    @Test
    void indexHints_seqScanAbove1000_highImpact() {
        var node = new ExplainNode("Seq Scan", "orders", ScanType.FULL_SCAN, 5000L, 100.0, null, List.of());
        var plan = new ExplainPlan("postgresql", "raw", List.of(node), 100.0, List.of());
        var result = provider.indexHints("SELECT * FROM orders", plan, null);
        assertThat(result.isOk()).isTrue();
        var recs = ((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).impact()).isEqualTo(Impact.HIGH);
    }
}
```

---

### Task 5: H2DiagnosticsProvider

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/H2DiagnosticsProvider.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/H2DiagnosticsProviderTest.java`

- [x] **Step 5.1: Write H2DiagnosticsProvider.java**

```java
package com.datatalk.infra.diagnostics;

import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.diagnostics.DiagnosticsProvider;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;
import java.util.regex.*;

@Component
public class H2DiagnosticsProvider implements DiagnosticsProvider {

    private static final Pattern TABLE_SCAN_PATTERN =
        Pattern.compile("FROM\\s+(\\w+\\.\\w+|\\w+)\\s*/\\*\\s*(\\w+)\\.tableScan", Pattern.CASE_INSENSITIVE);
    private static final Pattern INDEX_SCAN_PATTERN =
        Pattern.compile("/\\*\\s*(\\w+\\.\\w+|\\w+):", Pattern.CASE_INSENSITIVE);

    @Override
    public Set<String> supportedDriverTypes() { return Set.of("h2"); }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of(DiagnosticCapability.EXPLAIN, DiagnosticCapability.INDEX_HINTS);
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String database, String schema) {
        try (Connection c = DriverManager.getConnection(JdbcUrlBuilder.build(conn), conn.username(), conn.passwordEnc());
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("EXPLAIN " + sql)) {
            StringBuilder sb = new StringBuilder();
            while (rs.next()) sb.append(rs.getString(1)).append("\n");
            String raw = sb.toString();
            List<ExplainNode> nodes = parseH2Text(raw);
            List<String> warnings = collectWarnings(nodes);
            return DiagnosticResult.ok(new ExplainPlan("h2", raw, nodes, null, warnings));
        } catch (SQLException e) {
            return DiagnosticResult.error("SQL_ERROR", e.getMessage());
        }
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan, ConnectionRecord conn) {
        List<IndexRecommendation> recs = new ArrayList<>();
        for (ExplainNode n : plan.nodes()) {
            if (n.scanType() == ScanType.FULL_SCAN && n.table() != null) {
                recs.add(new IndexRecommendation(n.table(), List.of("<column_used_in_WHERE>"),
                    "BTREE", Impact.MEDIUM,
                    "Full scan on " + n.table() + ". Consider adding an index on frequently queried columns."));
            }
        }
        return DiagnosticResult.ok(recs);
    }

    @Override public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String database) {
        return DiagnosticResult.unsupported("Lock analysis not yet implemented for H2");
    }
    @Override public DiagnosticResult<PoolReport> connectionPoolInfo(ConnectionRecord conn) {
        return DiagnosticResult.unsupported("Connection pool info not yet implemented for H2");
    }
    @Override public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String database) {
        return DiagnosticResult.unsupported("Table space info not yet implemented for H2");
    }

    private List<ExplainNode> parseH2Text(String raw) {
        List<ExplainNode> nodes = new ArrayList<>();
        Matcher scanMatcher = TABLE_SCAN_PATTERN.matcher(raw);
        while (scanMatcher.find()) {
            String table = scanMatcher.group(1);
            nodes.add(new ExplainNode("tableScan", table, ScanType.FULL_SCAN, 0L, null, null, List.of()));
        }
        if (nodes.isEmpty()) {
            Matcher indexMatcher = INDEX_SCAN_PATTERN.matcher(raw);
            while (indexMatcher.find()) {
                nodes.add(new ExplainNode("indexScan", indexMatcher.group(1), ScanType.INDEX_SCAN, 0L, null, null, List.of()));
            }
        }
        return nodes;
    }

    private List<String> collectWarnings(List<ExplainNode> nodes) {
        List<String> warnings = new ArrayList<>();
        for (ExplainNode n : nodes) {
            if (n.scanType() == ScanType.FULL_SCAN && n.table() != null) {
                warnings.add("Full table scan on " + n.table());
            }
        }
        return warnings;
    }
}
```

- [x] **Step 5.2: Write H2DiagnosticsProviderTest.java**

```java
package com.datatalk.infra.diagnostics;

import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class H2DiagnosticsProviderTest {

    H2DiagnosticsProvider provider = new H2DiagnosticsProvider();

    @Test
    void supportedDriverTypes_containsH2() {
        assertThat(provider.supportedDriverTypes()).contains("h2");
    }

    @Test
    void parseH2Text_tableScanPattern_detectsFullScan() throws Exception {
        var method = H2DiagnosticsProvider.class.getDeclaredMethod("parseH2Text", String.class);
        method.setAccessible(true);
        String raw = "SELECT\nFROM PUBLIC.ORDERS\n    /* PUBLIC.ORDERS.tableScan */";
        @SuppressWarnings("unchecked")
        var nodes = (List<ExplainNode>) method.invoke(provider, raw);
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).scanType()).isEqualTo(ScanType.FULL_SCAN);
        assertThat(nodes.get(0).table()).containsIgnoringCase("ORDERS");
    }

    @Test
    void parseH2Text_indexPattern_detectsIndexScan() throws Exception {
        var method = H2DiagnosticsProvider.class.getDeclaredMethod("parseH2Text", String.class);
        method.setAccessible(true);
        String raw = "SELECT\nFROM PUBLIC.USERS\n    /* PUBLIC.IDX_USERS_EMAIL: EMAIL = ?1 */";
        @SuppressWarnings("unchecked")
        var nodes = (List<ExplainNode>) method.invoke(provider, raw);
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).scanType()).isEqualTo(ScanType.INDEX_SCAN);
    }

    @Test
    void indexHints_returnsUnsupported_forNonFullScan() {
        var node = new ExplainNode("indexScan", "users", ScanType.INDEX_SCAN, 0L, null, null, List.of());
        var plan = new ExplainPlan("h2", "raw", List.of(node), null, List.of());
        var result = provider.indexHints("SELECT 1", plan, null);
        assertThat(result.isOk()).isTrue();
        assertThat(((DiagnosticResult.Ok<List<IndexRecommendation>>) result).value()).isEmpty();
    }
}
```

---

### Task 6: OracleDiagnosticsProvider (stub)

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/OracleDiagnosticsProvider.java`

- [x] **Step 6.1: Write OracleDiagnosticsProvider.java**

```java
package com.datatalk.infra.diagnostics;

import com.datatalk.application.diagnostics.DiagnosticsProvider;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class OracleDiagnosticsProvider implements DiagnosticsProvider {

    private static final String REASON = "Oracle diagnostics not yet implemented. Coming in a future release.";

    @Override
    public Set<String> supportedDriverTypes() { return Set.of("oracle"); }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() { return Set.of(); }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String database, String schema) {
        return DiagnosticResult.unsupported(REASON);
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan, ConnectionRecord conn) {
        return DiagnosticResult.unsupported(REASON);
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String database) {
        return DiagnosticResult.unsupported(REASON);
    }

    @Override
    public DiagnosticResult<PoolReport> connectionPoolInfo(ConnectionRecord conn) {
        return DiagnosticResult.unsupported(REASON);
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String database) {
        return DiagnosticResult.unsupported(REASON);
    }
}
```

---

### Task 7: ExplainQueryAction + IndexHintsAction

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExplainQueryAction.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/IndexHintsAction.java`
- Test: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ExplainQueryActionTest.java`

- [x] **Step 7.1: Write ExplainQueryAction.java**

```java
package com.datatalk.adapter.actions;

import com.datatalk.application.diagnostics.DiagnosticsService;
import com.datatalk.domain.action.*;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.explain_query",
    executor = Executor.SERVER,
    description = "action.explain_query.description",
    requiresConnection = true,
    exposeToMcp = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.QUERY }
)
public class ExplainQueryAction implements ActionHandler<Map, Map> {

    private final DiagnosticsService diagnosticsService;

    public ExplainQueryAction(DiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("sql"),
            "properties", Map.of("sql", Map.of("type", "string")));
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "dialect",            Map.of("type", "string"),
                "rawText",            Map.of("type", "string"),
                "nodes",              Map.of("type", "array"),
                "totalCostEstimate",  Map.of("type", "number"),
                "warnings",           Map.of("type", "array"),
                "unsupported",        Map.of("type", "boolean")
            ));
    }

    @Override public Class<Map> inputType() { return Map.class; }
    @Override public List<OntologyEffect> sideEffects() { return List.of(); }

    @Override
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = String.valueOf(input.get("sql"));
            var result = diagnosticsService.explain(ctx.sessionId(), sql);
            return switch (result) {
                case DiagnosticResult.Ok<ExplainPlan> ok -> serializePlan(ok.value());
                case DiagnosticResult.Unsupported<ExplainPlan> u ->
                    Map.of("unsupported", true, "reason", u.reason());
                case DiagnosticResult.DiagnosticError<ExplainPlan> e ->
                    Map.of("error", true, "errorType", e.errorType(), "message", e.message());
            };
        });
    }

    static Map<String, Object> serializePlan(ExplainPlan plan) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dialect", plan.dialect());
        out.put("rawText", plan.rawText());
        out.put("nodes", plan.nodes().stream().map(ExplainQueryAction::serializeNode).toList());
        if (plan.totalCostEstimate() != null) out.put("totalCostEstimate", plan.totalCostEstimate());
        out.put("warnings", plan.warnings());
        out.put("unsupported", false);
        return out;
    }

    static Map<String, Object> serializeNode(ExplainNode node) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("operation", node.operation());
        if (node.table() != null) m.put("table", node.table());
        m.put("scanType", node.scanType().name());
        m.put("rows", node.rows());
        if (node.cost() != null) m.put("cost", node.cost());
        if (node.extra() != null) m.put("extra", node.extra());
        if (!node.children().isEmpty()) m.put("children", node.children().stream().map(ExplainQueryAction::serializeNode).toList());
        return m;
    }
}
```

- [x] **Step 7.2: Write IndexHintsAction.java**

```java
package com.datatalk.adapter.actions;

import com.datatalk.application.diagnostics.DiagnosticsService;
import com.datatalk.domain.action.*;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Component
@DataTalkAction(
    id = "datatalk.index_hints",
    executor = Executor.SERVER,
    description = "action.index_hints.description",
    requiresConnection = true,
    exposeToMcp = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.QUERY }
)
public class IndexHintsAction implements ActionHandler<Map, Map> {

    private final DiagnosticsService diagnosticsService;

    public IndexHintsAction(DiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("sql"),
            "properties", Map.of("sql", Map.of("type", "string")));
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "recommendations",  Map.of("type", "array"),
                "explainSummary",   Map.of("type", "string"),
                "unsupported",      Map.of("type", "boolean")
            ));
    }

    @Override public Class<Map> inputType() { return Map.class; }
    @Override public List<OntologyEffect> sideEffects() { return List.of(); }

    @Override
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = String.valueOf(input.get("sql"));
            var result = diagnosticsService.indexHints(ctx.sessionId(), sql);
            return switch (result) {
                case DiagnosticResult.Ok<List<IndexRecommendation>> ok -> {
                    var recs = ok.value();
                    yield Map.of(
                        "recommendations", recs.stream().map(this::serializeRec).toList(),
                        "explainSummary", buildSummary(recs),
                        "unsupported", false
                    );
                }
                case DiagnosticResult.Unsupported<List<IndexRecommendation>> u ->
                    Map.of("unsupported", true, "reason", u.reason());
                case DiagnosticResult.DiagnosticError<List<IndexRecommendation>> e ->
                    Map.of("error", true, "errorType", e.errorType(), "message", e.message());
            };
        });
    }

    private Map<String, Object> serializeRec(IndexRecommendation r) {
        return Map.of(
            "table", r.table(),
            "columns", r.columns(),
            "indexType", r.indexType(),
            "impact", r.impact().name(),
            "rationale", r.rationale()
        );
    }

    private String buildSummary(List<IndexRecommendation> recs) {
        if (recs.isEmpty()) return "No index recommendations found.";
        long high = recs.stream().filter(r -> r.impact() == Impact.HIGH).count();
        long medium = recs.stream().filter(r -> r.impact() == Impact.MEDIUM).count();
        String tables = recs.stream().map(IndexRecommendation::table).distinct()
            .collect(Collectors.joining(", "));
        return String.format("Found %d index recommendation(s) (%d HIGH, %d MEDIUM) on table(s): %s.",
            recs.size(), high, medium, tables);
    }
}
```

- [x] **Step 7.3: Write ExplainQueryActionTest.java**

```java
package com.datatalk.adapter.actions;

import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ExplainQueryActionTest {

    @Test
    void serializePlan_includesAllFields() {
        var node = new ExplainNode("Seq Scan", "orders", ScanType.FULL_SCAN, 1000L, 100.0, "filter", List.of());
        var plan = new ExplainPlan("postgresql", "raw text", List.of(node), 100.0, List.of("Full scan on orders"));
        var out = ExplainQueryAction.serializePlan(plan);
        assertThat(out.get("dialect")).isEqualTo("postgresql");
        assertThat(out.get("rawText")).isEqualTo("raw text");
        assertThat(out.get("unsupported")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        var nodes = (List<Map<String, Object>>) out.get("nodes");
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).get("scanType")).isEqualTo("FULL_SCAN");
        assertThat(nodes.get(0).get("table")).isEqualTo("orders");
    }

    @Test
    void serializeNode_omitsNullTableAndExtra() {
        var node = new ExplainNode("Hash Join", null, ScanType.OTHER, 0L, null, null, List.of());
        var out = ExplainQueryAction.serializeNode(node);
        assertThat(out).doesNotContainKey("table");
        assertThat(out).doesNotContainKey("extra");
        assertThat(out).doesNotContainKey("cost");
    }
}
```

---

### Task 8: Stub Actions (LockInfo, PoolStatus, TableSpace)

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/LockInfoAction.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/PoolStatusAction.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/TableSpaceAction.java`

- [x] **Step 8.1: Write stub actions**

```java
// LockInfoAction.java
package com.datatalk.adapter.actions;

import com.datatalk.domain.action.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;

@Component
@DataTalkAction(
    id = "datatalk.lock_info",
    executor = Executor.SERVER,
    description = "action.lock_info.description",
    requiresConnection = true,
    exposeToMcp = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.QUERY }
)
public class LockInfoAction implements ActionHandler<Map, Map> {
    @Override public Map<String, Object> inputSchema() { return Map.of("type", "object", "properties", Map.of()); }
    @Override public Map<String, Object> outputSchema() { return Map.of("type", "object"); }
    @Override public Class<Map> inputType() { return Map.class; }
    @Override public List<OntologyEffect> sideEffects() { return List.of(); }
    @Override public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        return CompletableFuture.completedFuture(Map.of("unsupported", true,
            "reason", "Lock analysis is not yet available. Coming in a future release."));
    }
}
```

```java
// PoolStatusAction.java
package com.datatalk.adapter.actions;

import com.datatalk.domain.action.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;

@Component
@DataTalkAction(
    id = "datatalk.pool_status",
    executor = Executor.SERVER,
    description = "action.pool_status.description",
    requiresConnection = true,
    exposeToMcp = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.QUERY }
)
public class PoolStatusAction implements ActionHandler<Map, Map> {
    @Override public Map<String, Object> inputSchema() { return Map.of("type", "object", "properties", Map.of()); }
    @Override public Map<String, Object> outputSchema() { return Map.of("type", "object"); }
    @Override public Class<Map> inputType() { return Map.class; }
    @Override public List<OntologyEffect> sideEffects() { return List.of(); }
    @Override public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        return CompletableFuture.completedFuture(Map.of("unsupported", true,
            "reason", "Connection pool status is not yet available. Coming in a future release."));
    }
}
```

```java
// TableSpaceAction.java
package com.datatalk.adapter.actions;

import com.datatalk.domain.action.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;

@Component
@DataTalkAction(
    id = "datatalk.table_space",
    executor = Executor.SERVER,
    description = "action.table_space.description",
    requiresConnection = true,
    exposeToMcp = true,
    riskLevel = { RiskLevel.L1 },
    category = { Category.QUERY }
)
public class TableSpaceAction implements ActionHandler<Map, Map> {
    @Override public Map<String, Object> inputSchema() { return Map.of("type", "object", "properties", Map.of()); }
    @Override public Map<String, Object> outputSchema() { return Map.of("type", "object"); }
    @Override public Class<Map> inputType() { return Map.class; }
    @Override public List<OntologyEffect> sideEffects() { return List.of(); }
    @Override public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        return CompletableFuture.completedFuture(Map.of("unsupported", true,
            "reason", "Table space information is not yet available. Coming in a future release."));
    }
}
```

---

### Task 9: DiagnosticsController

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/DiagnosticsController.java`
- Test: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/DiagnosticsControllerTest.java`

- [x] **Step 9.1: Write DiagnosticsController.java**

```java
package com.datatalk.adapter.controller;

import com.datatalk.adapter.actions.ExplainQueryAction;
import com.datatalk.application.diagnostics.DiagnosticsService;
import com.datatalk.domain.diagnostics.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions/{sessionId}/diagnostics")
public class DiagnosticsController {

    private final DiagnosticsService service;

    public DiagnosticsController(DiagnosticsService service) {
        this.service = service;
    }

    @PostMapping("/explain")
    public ResponseEntity<Map<String, Object>> explain(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        String sql = body.get("sql");
        if (sql == null || sql.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "sql is required"));
        }
        try {
            var result = service.explain(sessionId, sql);
            return switch (result) {
                case DiagnosticResult.Ok<ExplainPlan> ok ->
                    ResponseEntity.ok(ExplainQueryAction.serializePlan(ok.value()));
                case DiagnosticResult.Unsupported<ExplainPlan> u ->
                    ResponseEntity.ok(Map.of("unsupported", true, "reason", u.reason()));
                case DiagnosticResult.DiagnosticError<ExplainPlan> e ->
                    ResponseEntity.internalServerError().body(Map.of("error", e.errorType(), "message", e.message()));
            };
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/index-hints")
    public ResponseEntity<Map<String, Object>> indexHints(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        String sql = body.get("sql");
        if (sql == null || sql.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "sql is required"));
        }
        try {
            var result = service.indexHints(sessionId, sql);
            return switch (result) {
                case DiagnosticResult.Ok<List<IndexRecommendation>> ok -> {
                    var recs = ok.value();
                    yield ResponseEntity.ok(Map.of(
                        "recommendations", recs.stream().map(this::serializeRec).toList(),
                        "explainSummary", buildSummary(recs),
                        "unsupported", false
                    ));
                }
                case DiagnosticResult.Unsupported<List<IndexRecommendation>> u ->
                    ResponseEntity.ok(Map.of("unsupported", true, "reason", u.reason()));
                case DiagnosticResult.DiagnosticError<List<IndexRecommendation>> e ->
                    ResponseEntity.internalServerError().body(Map.of("error", e.errorType(), "message", e.message()));
            };
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private Map<String, Object> serializeRec(IndexRecommendation r) {
        return Map.of("table", r.table(), "columns", r.columns(),
            "indexType", r.indexType(), "impact", r.impact().name(), "rationale", r.rationale());
    }

    private String buildSummary(List<IndexRecommendation> recs) {
        if (recs.isEmpty()) return "No index recommendations found.";
        long high = recs.stream().filter(r -> r.impact() == Impact.HIGH).count();
        return String.format("Found %d recommendation(s) (%d HIGH) for the provided SQL.", recs.size(), high);
    }
}
```

- [x] **Step 9.2: Write DiagnosticsControllerTest.java**

```java
package com.datatalk.adapter.controller;

import com.datatalk.application.diagnostics.DiagnosticsService;
import com.datatalk.domain.diagnostics.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DiagnosticsController.class)
class DiagnosticsControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @MockBean DiagnosticsService service;

    @Test
    void explain_missingSql_returns400() throws Exception {
        mvc.perform(post("/api/sessions/s1/diagnostics/explain")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void explain_validSql_returns200WithPlan() throws Exception {
        var plan = new ExplainPlan("mysql", "raw", List.of(), null, List.of());
        when(service.explain(eq("s1"), eq("SELECT 1"))).thenReturn(DiagnosticResult.ok(plan));
        mvc.perform(post("/api/sessions/s1/diagnostics/explain")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("sql", "SELECT 1"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dialect").value("mysql"))
            .andExpect(jsonPath("$.unsupported").value(false));
    }

    @Test
    void explain_unsupportedDialect_returns200WithUnsupportedFlag() throws Exception {
        when(service.explain(any(), any())).thenReturn(DiagnosticResult.unsupported("Oracle not supported"));
        mvc.perform(post("/api/sessions/s1/diagnostics/explain")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("sql", "SELECT 1"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unsupported").value(true));
    }

    @Test
    void indexHints_validSql_returns200WithRecs() throws Exception {
        var rec = new IndexRecommendation("orders", List.of("user_id"), "BTREE", Impact.HIGH, "full scan");
        when(service.indexHints(eq("s1"), any())).thenReturn(DiagnosticResult.ok(List.of(rec)));
        mvc.perform(post("/api/sessions/s1/diagnostics/index-hints")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("sql", "SELECT * FROM orders"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recommendations[0].table").value("orders"))
            .andExpect(jsonPath("$.recommendations[0].impact").value("HIGH"));
    }
}
```

---

### Task 10: Backend i18n

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/messages.properties`
- Modify: `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties`

- [x] **Step 10.1: Add keys to messages.properties (English)**

Append to `server/data-talk-adapter/src/main/resources/messages.properties`:

```properties
action.explain_query.description=Get a normalized execution plan tree for a SQL statement. Use when diagnosing query performance.
action.index_hints.description=Get index recommendations for a SQL statement by internally running EXPLAIN. Use when the user asks for index advice or performance optimization.
action.lock_info.description=Get current lock information for the database connection. Not yet available.
action.pool_status.description=Get connection pool status for the database connection. Not yet available.
action.table_space.description=Get table space usage information. Not yet available.
```

- [x] **Step 10.2: Add keys to messages_zh_CN.properties (Chinese)**

Append to `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties`:

```properties
action.explain_query.description=获取 SQL 语句的规范化执行计划树。用于诊断查询性能。
action.index_hints.description=通过内部运行 EXPLAIN 获取 SQL 语句的索引推荐。用于用户询问索引建议或性能优化时。
action.lock_info.description=获取数据库连接的当前锁信息。暂不可用。
action.pool_status.description=获取数据库连接的连接池状态。暂不可用。
action.table_space.description=获取表空间占用信息。暂不可用。
```

---

## Batch P Verification

- [x] **Run backend full compile + targeted tests**

```bash
cd /home/wallfacers/project/data-talk/server && mvn compile -q
```
Expected: BUILD SUCCESS, zero errors.

```bash
cd /home/wallfacers/project/data-talk/server && mvn test -q -pl data-talk-domain,data-talk-application,data-talk-infrastructure -Dtest="DiagnosticResultTest,DiagnosticsServiceTest,MySqlDiagnosticsProviderTest,PostgreSqlDiagnosticsProviderTest,H2DiagnosticsProviderTest,ExplainQueryActionTest"
```
Expected: All tests pass.

```bash
cd /home/wallfacers/project/data-talk/server && mvn test -q -pl data-talk-adapter -Dtest=DiagnosticsControllerTest
```
Expected: Tests run: 4, Failures: 0.

---

## Batch F — Frontend (parallel)

### Task 11: TypeScript Types + API Service

**Files:**
- Create: `client/src/features/stage/types/diagnostics.ts`
- Create: `client/src/services/api/diagnostics.ts`

- [x] **Step 11.1: Write diagnostics.ts types**

```typescript
// client/src/features/stage/types/diagnostics.ts

export type ScanType = 'FULL_SCAN' | 'INDEX_RANGE' | 'INDEX_SCAN' | 'CONST' | 'REF' | 'OTHER'
export type Impact = 'HIGH' | 'MEDIUM' | 'LOW'

export interface ExplainNode {
  operation: string
  table?: string
  scanType: ScanType
  rows: number
  cost?: number
  extra?: string
  children: ExplainNode[]
}

export interface ExplainPlan {
  dialect: string
  rawText: string
  nodes: ExplainNode[]
  totalCostEstimate?: number
  warnings: string[]
  unsupported: false
}

export interface UnsupportedResult {
  unsupported: true
  reason: string
}

export interface IndexRecommendation {
  table: string
  columns: string[]
  indexType: string
  impact: Impact
  rationale: string
}

export interface IndexHintsResult {
  recommendations: IndexRecommendation[]
  explainSummary: string
  unsupported: false
}

export type ExplainResult = ExplainPlan | UnsupportedResult
export type IndexHintsResponse = IndexHintsResult | UnsupportedResult
```

- [x] **Step 11.2: Write diagnostics API service**

```typescript
// client/src/services/api/diagnostics.ts

import { API_BASE } from './base'
import type { ExplainResult, IndexHintsResponse } from '@/features/stage/types/diagnostics'

export async function fetchExplainPlan(sessionId: string, sql: string): Promise<ExplainResult> {
  const res = await fetch(`${API_BASE}/api/sessions/${sessionId}/diagnostics/explain`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sql }),
  })
  if (!res.ok) throw new Error(`Explain failed: ${res.status}`)
  return res.json()
}

export async function fetchIndexHints(sessionId: string, sql: string): Promise<IndexHintsResponse> {
  const res = await fetch(`${API_BASE}/api/sessions/${sessionId}/diagnostics/index-hints`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sql }),
  })
  if (!res.ok) throw new Error(`Index hints failed: ${res.status}`)
  return res.json()
}
```

---

### Task 12: Register diagnostic Tab Type

**Files:**
- Modify: `client/src/features/stage/registry/tab-type-registry.ts`
- Modify: `client/src/stores/stage-store.ts` (add 'diagnostics' to RailPanel)

- [x] **Step 12.1: Add diagnostic to TAB_TYPE_REGISTRY**

In `client/src/features/stage/registry/tab-type-registry.ts`, add after the `workspace` entry:

```typescript
import { SearchCodeIcon } from 'lucide-react'  // add to imports
```

Add to `TAB_TYPE_REGISTRY`:
```typescript
  diagnostic: {
    type: 'diagnostic',
    persistent: true,
    scope: 'workspace',
    icon: SearchCodeIcon,
    labelKey: 'tabType.diagnostic',
    extractContent: (p) => {
      const o = p as { sql?: unknown } | null | undefined
      return typeof o?.sql === 'string' ? o.sql : ''
    },
  },
```

- [x] **Step 12.2: Add 'diagnostics' to RailPanel type in stage-store.ts**

Find the `RailPanel` type definition in `client/src/stores/stage-store.ts` (currently `'schema' | 'history' | 'outline'`) and add `| 'diagnostics'`:

```typescript
export type RailPanel = 'schema' | 'history' | 'outline' | 'diagnostics'
```

- [x] **Step 12.3: Test tab type registration**

In `client/src/features/stage/registry/__tests__/tab-type-registry.test.ts`, add:

```typescript
it('diagnostic type is registered as workbench-scope persistent', () => {
  const desc = getTabTypeDescriptor('diagnostic')
  expect(desc.persistent).toBe(true)
  expect(desc.scope).toBe('workspace')
  expect(desc.type).toBe('diagnostic')
})
```

---

### Task 13: ExplainPlanTree + IndexRecommendationList Components

**Files:**
- Create: `client/src/features/stage/components/diagnostics/explain-plan-tree.tsx`
- Create: `client/src/features/stage/components/diagnostics/explain-plan-tree.test.tsx`
- Create: `client/src/features/stage/components/diagnostics/index-recommendation-list.tsx`
- Create: `client/src/features/stage/components/diagnostics/index-recommendation-list.test.tsx`

- [x] **Step 13.1: Write explain-plan-tree.tsx**

```typescript
// client/src/features/stage/components/diagnostics/explain-plan-tree.tsx
import { useState } from 'react'
import { ChevronDownIcon, ChevronRightIcon, AlertTriangleIcon, CheckIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { ExplainNode, ScanType } from '@/features/stage/types/diagnostics'

function scanTypeClasses(scanType: ScanType): string {
  switch (scanType) {
    case 'FULL_SCAN':   return 'bg-destructive/10 text-destructive'
    case 'INDEX_RANGE':
    case 'REF':         return 'bg-sky-500/10 text-sky-600 dark:text-sky-400'
    case 'INDEX_SCAN':  return 'bg-green-500/10 text-green-700 dark:text-green-400'
    case 'CONST':       return 'text-muted-foreground'
    default:            return 'text-muted-foreground'
  }
}

function ScanTypeIcon({ scanType }: { scanType: ScanType }) {
  if (scanType === 'FULL_SCAN') return <AlertTriangleIcon className="size-3 shrink-0" aria-label="Full scan warning" />
  if (scanType === 'INDEX_SCAN' || scanType === 'CONST') return <CheckIcon className="size-3 shrink-0" aria-label="Index scan" />
  return null
}

function ExplainNodeRow({ node, depth }: { node: ExplainNode; depth: number }) {
  const [expanded, setExpanded] = useState(true)
  const hasChildren = node.children.length > 0
  const indent = depth * 16

  return (
    <div>
      <div
        className={cn('flex items-center gap-1.5 rounded px-2 py-1 text-xs', scanTypeClasses(node.scanType))}
        style={{ paddingLeft: `${8 + indent}px` }}
      >
        {hasChildren ? (
          <button
            type="button"
            onClick={() => setExpanded((e) => !e)}
            aria-label={expanded ? 'Collapse node' : 'Expand node'}
            className="shrink-0"
          >
            {expanded ? <ChevronDownIcon className="size-3" /> : <ChevronRightIcon className="size-3" />}
          </button>
        ) : (
          <span className="size-3 shrink-0" />
        )}
        <ScanTypeIcon scanType={node.scanType} />
        <span className="font-medium">{node.operation}</span>
        {node.table && (
          <span className="font-mono text-[11px] opacity-75">{node.table}</span>
        )}
        {node.rows > 0 && (
          <span className="ml-auto font-mono text-[11px] opacity-60">rows: {node.rows.toLocaleString()}</span>
        )}
        {node.cost != null && (
          <span className="font-mono text-[11px] opacity-60">cost: {node.cost.toFixed(1)}</span>
        )}
      </div>
      {expanded && hasChildren && node.children.map((child, i) => (
        <ExplainNodeRow key={i} node={child} depth={depth + 1} />
      ))}
    </div>
  )
}

export function ExplainPlanTree({ nodes }: { nodes: ExplainNode[] }) {
  if (nodes.length === 0) {
    return <p className="px-3 py-4 text-xs text-muted-foreground">No plan nodes available.</p>
  }
  return (
    <div className="flex flex-col gap-0.5 py-1" role="tree" aria-label="Explain plan tree">
      {nodes.map((node, i) => (
        <ExplainNodeRow key={i} node={node} depth={0} />
      ))}
    </div>
  )
}
```

- [x] **Step 13.2: Write explain-plan-tree.test.tsx**

```typescript
// client/src/features/stage/components/diagnostics/explain-plan-tree.test.tsx
import { render, screen } from '@testing-library/react'
import { ExplainPlanTree } from './explain-plan-tree'
import type { ExplainNode } from '@/features/stage/types/diagnostics'

const fullScanNode: ExplainNode = {
  operation: 'Seq Scan', table: 'orders', scanType: 'FULL_SCAN',
  rows: 5000, children: [],
}
const indexScanNode: ExplainNode = {
  operation: 'Index Scan', table: 'users', scanType: 'INDEX_SCAN',
  rows: 1, children: [],
}

it('renders full scan node with danger styling', () => {
  render(<ExplainPlanTree nodes={[fullScanNode]} />)
  const node = screen.getByText('Seq Scan').closest('div')
  expect(node?.className).toContain('bg-destructive')
})

it('renders index scan node with success styling', () => {
  render(<ExplainPlanTree nodes={[indexScanNode]} />)
  const node = screen.getByText('Index Scan').closest('div')
  expect(node?.className).toContain('bg-green')
})

it('renders empty state when no nodes', () => {
  render(<ExplainPlanTree nodes={[]} />)
  expect(screen.getByText('No plan nodes available.')).toBeInTheDocument()
})

it('renders rows in mono font', () => {
  render(<ExplainPlanTree nodes={[fullScanNode]} />)
  expect(screen.getByText(/rows: 5,000/)).toBeInTheDocument()
})
```

- [x] **Step 13.3: Write index-recommendation-list.tsx**

```typescript
// client/src/features/stage/components/diagnostics/index-recommendation-list.tsx
import { cn } from '@/lib/utils'
import type { IndexRecommendation, Impact } from '@/features/stage/types/diagnostics'

function impactBadgeClasses(impact: Impact): string {
  switch (impact) {
    case 'HIGH':   return 'bg-destructive/10 text-destructive border-destructive/20'
    case 'MEDIUM': return 'bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-500/20'
    case 'LOW':    return 'bg-sky-500/10 text-sky-700 dark:text-sky-400 border-sky-500/20'
  }
}

function ImpactBadge({ impact }: { impact: Impact }) {
  return (
    <span className={cn('inline-flex items-center rounded border px-1.5 py-0.5 text-[10px] font-medium', impactBadgeClasses(impact))}>
      {impact}
    </span>
  )
}

export function IndexRecommendationList({ recommendations }: { recommendations: IndexRecommendation[] }) {
  if (recommendations.length === 0) {
    return <p className="px-3 py-4 text-xs text-muted-foreground">No index recommendations.</p>
  }
  return (
    <div className="flex flex-col divide-y divide-border/40" role="list" aria-label="Index recommendations">
      {recommendations.map((rec, i) => (
        <div key={i} className="flex flex-col gap-1 px-3 py-2.5" role="listitem">
          <div className="flex items-center gap-2">
            <ImpactBadge impact={rec.impact} />
            <span className="font-mono text-[11px] text-foreground">
              {rec.table}({rec.columns.join(', ')})
            </span>
            <span className="text-[10px] text-muted-foreground">{rec.indexType}</span>
          </div>
          <p className="text-xs text-muted-foreground">{rec.rationale}</p>
        </div>
      ))}
    </div>
  )
}
```

- [x] **Step 13.4: Write index-recommendation-list.test.tsx**

```typescript
// client/src/features/stage/components/diagnostics/index-recommendation-list.test.tsx
import { render, screen } from '@testing-library/react'
import { IndexRecommendationList } from './index-recommendation-list'
import type { IndexRecommendation } from '@/features/stage/types/diagnostics'

const highRec: IndexRecommendation = {
  table: 'orders', columns: ['user_id'], indexType: 'BTREE',
  impact: 'HIGH', rationale: 'Full scan on orders',
}

it('renders recommendation with HIGH impact badge', () => {
  render(<IndexRecommendationList recommendations={[highRec]} />)
  expect(screen.getByText('HIGH')).toBeInTheDocument()
  expect(screen.getByText(/orders\(user_id\)/)).toBeInTheDocument()
  expect(screen.getByText('Full scan on orders')).toBeInTheDocument()
})

it('renders empty state when no recommendations', () => {
  render(<IndexRecommendationList recommendations={[]} />)
  expect(screen.getByText('No index recommendations.')).toBeInTheDocument()
})

it('HIGH impact badge has destructive styling', () => {
  render(<IndexRecommendationList recommendations={[highRec]} />)
  const badge = screen.getByText('HIGH')
  expect(badge.className).toContain('bg-destructive')
})
```

---

### Task 14: DiagnosticsTab Component

**Files:**
- Create: `client/src/features/stage/components/diagnostics/diagnostics-tab.tsx`
- Create: `client/src/features/stage/components/diagnostics/diagnostics-tab.test.tsx`

- [x] **Step 14.1: Write diagnostics-tab.tsx**

```typescript
// client/src/features/stage/components/diagnostics/diagnostics-tab.tsx
import { useState } from 'react'
import { ChevronDownIcon, ChevronRightIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { ExplainPlanTree } from './explain-plan-tree'
import { IndexRecommendationList } from './index-recommendation-list'
import type { ExplainPlan, IndexRecommendation } from '@/features/stage/types/diagnostics'

interface DiagnosticsTabPayload {
  sql: string
  connectionName?: string
  dialect?: string
  plan?: ExplainPlan
  recommendations?: IndexRecommendation[]
  explainSummary?: string
  loading?: boolean
  error?: string
}

interface Props {
  payload: DiagnosticsTabPayload
}

export function DiagnosticsTab({ payload }: Props) {
  const [rawExpanded, setRawExpanded] = useState(false)

  if (payload.loading) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
        Running EXPLAIN…
      </div>
    )
  }

  if (payload.error) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-destructive">
        {payload.error}
      </div>
    )
  }

  const plan = payload.plan
  const recs = payload.recommendations ?? []

  return (
    <div className="flex h-full flex-col bg-canvas" data-testid="diagnostics-tab">
      {/* Top chrome */}
      <div className="flex items-center gap-2 border-b border-border/50 bg-subtle px-3 py-2">
        {payload.connectionName && (
          <span className="rounded bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground">
            {payload.connectionName}
          </span>
        )}
        {payload.dialect && (
          <span className="rounded border border-border/50 px-1.5 py-0.5 text-[11px] text-muted-foreground">
            {payload.dialect}
          </span>
        )}
        <span className="font-mono text-[11px] text-muted-foreground truncate max-w-[300px]">
          {payload.sql?.slice(0, 80)}{(payload.sql?.length ?? 0) > 80 ? '…' : ''}
        </span>
      </div>

      {/* Main area */}
      <div className="flex min-h-0 flex-1">
        {/* Left: explain plan */}
        <div className="flex flex-[3] flex-col overflow-auto border-r border-border/50">
          <div className="border-b border-border/30 px-3 py-1.5 text-[11px] font-medium text-muted-foreground uppercase tracking-wide">
            Execution Plan
          </div>
          {plan ? (
            <ExplainPlanTree nodes={plan.nodes} />
          ) : (
            <p className="px-3 py-4 text-xs text-muted-foreground">No plan data.</p>
          )}
        </div>

        {/* Right: index recommendations */}
        <div className="flex flex-[2] flex-col overflow-auto">
          <div className="border-b border-border/30 px-3 py-1.5 text-[11px] font-medium text-muted-foreground uppercase tracking-wide">
            Index Recommendations
          </div>
          <IndexRecommendationList recommendations={recs} />
        </div>
      </div>

      {/* Bottom: raw EXPLAIN */}
      {plan?.rawText && (
        <div className="border-t border-border/50 bg-subtle">
          <button
            type="button"
            onClick={() => setRawExpanded((e) => !e)}
            className="flex w-full items-center gap-1 px-3 py-1.5 text-[11px] text-muted-foreground hover:text-foreground"
            aria-expanded={rawExpanded}
            aria-controls="raw-explain-text"
          >
            {rawExpanded ? <ChevronDownIcon className="size-3" /> : <ChevronRightIcon className="size-3" />}
            Raw EXPLAIN output
          </button>
          {rawExpanded && (
            <pre
              id="raw-explain-text"
              className={cn(
                'max-h-48 overflow-auto bg-subtle px-3 pb-3 font-mono text-[11px] text-muted-foreground'
              )}
            >
              {plan.rawText}
            </pre>
          )}
        </div>
      )}
    </div>
  )
}
```

- [x] **Step 14.2: Write diagnostics-tab.test.tsx**

```typescript
// client/src/features/stage/components/diagnostics/diagnostics-tab.test.tsx
import { render, screen, fireEvent } from '@testing-library/react'
import { DiagnosticsTab } from './diagnostics-tab'
import type { ExplainPlan } from '@/features/stage/types/diagnostics'

const plan: ExplainPlan = {
  dialect: 'mysql', rawText: 'EXPLAIN raw output', nodes: [], warnings: [], unsupported: false,
}

it('shows loading state', () => {
  render(<DiagnosticsTab payload={{ sql: 'SELECT 1', loading: true }} />)
  expect(screen.getByText('Running EXPLAIN…')).toBeInTheDocument()
})

it('shows error state', () => {
  render(<DiagnosticsTab payload={{ sql: 'SELECT 1', error: 'Connection lost' }} />)
  expect(screen.getByText('Connection lost')).toBeInTheDocument()
})

it('renders plan and recommendations sections', () => {
  render(<DiagnosticsTab payload={{ sql: 'SELECT * FROM orders', plan, recommendations: [] }} />)
  expect(screen.getByText('Execution Plan')).toBeInTheDocument()
  expect(screen.getByText('Index Recommendations')).toBeInTheDocument()
})

it('expands raw EXPLAIN on click', () => {
  render(<DiagnosticsTab payload={{ sql: 'SELECT 1', plan, recommendations: [] }} />)
  expect(screen.queryByText('EXPLAIN raw output')).not.toBeInTheDocument()
  fireEvent.click(screen.getByText('Raw EXPLAIN output'))
  expect(screen.getByText('EXPLAIN raw output')).toBeInTheDocument()
})

it('renders connection chip and dialect badge', () => {
  render(<DiagnosticsTab payload={{
    sql: 'SELECT 1', plan, recommendations: [],
    connectionName: 'prod-db', dialect: 'mysql'
  }} />)
  expect(screen.getByText('prod-db')).toBeInTheDocument()
  expect(screen.getByText('mysql')).toBeInTheDocument()
})
```

---

### Task 15: DiagnosticsCard Chat Renderer

**Files:**
- Create: `client/src/features/chat/components/tools/renderers/diagnostics-card.tsx`
- Create: `client/src/features/chat/components/tools/renderers/diagnostics-card.test.tsx`

- [x] **Step 15.1: Write diagnostics-card.tsx**

```typescript
// client/src/features/chat/components/tools/renderers/diagnostics-card.tsx
import { AlertTriangleIcon, SearchCodeIcon } from 'lucide-react'
import { BasicTool } from '../basic-tool'
import { Button } from '@/components/ui/button'
import type { ToolRendererProps } from '../tool-registry'
import { ToolRegistry } from '../tool-registry'
import { useStageStore } from '@/stores/stage-store'
import type { ExplainPlan, IndexRecommendation } from '@/features/stage/types/diagnostics'

type DiagnosticsOutput = {
  dialect?: string
  warnings?: string[]
  recommendations?: IndexRecommendation[]
  explainSummary?: string
  unsupported?: boolean
  reason?: string
  plan?: ExplainPlan
}

export function DiagnosticsCard(props: ToolRendererProps) {
  const { part } = props
  const output = part.state.output as DiagnosticsOutput | undefined
  const input = part.state.input as { sql?: string } | undefined
  const sql = input?.sql ?? ''

  const openDiagnosticsTab = useStageStore((s) => s.openDiagnosticsTab)

  if (output?.unsupported) {
    return (
      <BasicTool part={part} icon={<SearchCodeIcon className="size-3.5" />} title="Query Diagnostics">
        <p className="text-xs text-muted-foreground">{output.reason ?? 'Not available for this database.'}</p>
      </BasicTool>
    )
  }

  const warnings = output?.warnings ?? []
  const recs = output?.recommendations ?? []
  const highCount = recs.filter((r) => r.impact === 'HIGH').length
  const mediumCount = recs.filter((r) => r.impact === 'MEDIUM').length

  function handleOpenInWorkbench() {
    openDiagnosticsTab({
      sessionId: part.sessionID,
      sql,
      plan: output?.plan,
      recommendations: recs,
      explainSummary: output?.explainSummary,
    })
  }

  return (
    <BasicTool part={part} icon={<SearchCodeIcon className="size-3.5" />} title="Query Diagnostics">
      <div className="flex flex-col gap-2 text-xs">
        {warnings.length > 0 && (
          <div className="flex items-center gap-1.5 rounded bg-amber-500/10 px-2 py-1.5 text-amber-700 dark:text-amber-400">
            <AlertTriangleIcon className="size-3.5 shrink-0" />
            <span>{warnings.length} performance warning{warnings.length > 1 ? 's' : ''}</span>
          </div>
        )}
        {recs.length > 0 && (
          <p className="text-muted-foreground">
            Index recommendations: {recs.length} ({highCount} HIGH, {mediumCount} MEDIUM)
          </p>
        )}
        {output?.explainSummary && (
          <p className="text-muted-foreground">{output.explainSummary}</p>
        )}
        <Button variant="ghost" size="sm" className="self-start px-0 text-xs" onClick={handleOpenInWorkbench}>
          Open in Workbench →
        </Button>
      </div>
    </BasicTool>
  )
}

ToolRegistry.register('datatalk.explain_query', DiagnosticsCard)
ToolRegistry.register('datatalk.index_hints', DiagnosticsCard)
```

- [x] **Step 15.2: Write diagnostics-card.test.tsx**

```typescript
// client/src/features/chat/components/tools/renderers/diagnostics-card.test.tsx
import { render, screen } from '@testing-library/react'
import { DiagnosticsCard } from './diagnostics-card'
import { createMockToolPart } from '@/test-utils/mock-tool-part'

const mockDescriptor = { id: 'datatalk.explain_query', name: 'explain_query' } as any

it('shows unsupported message when unsupported flag is set', () => {
  const part = createMockToolPart({ output: { unsupported: true, reason: 'Oracle not supported' } })
  render(<DiagnosticsCard part={part} descriptor={mockDescriptor} />)
  expect(screen.getByText('Oracle not supported')).toBeInTheDocument()
})

it('shows warning count when warnings present', () => {
  const part = createMockToolPart({
    output: { warnings: ['Full scan on orders', 'Full scan on users'], recommendations: [] }
  })
  render(<DiagnosticsCard part={part} descriptor={mockDescriptor} />)
  expect(screen.getByText(/2 performance warnings/)).toBeInTheDocument()
})

it('shows Open in Workbench button', () => {
  const part = createMockToolPart({
    output: { warnings: [], recommendations: [], explainSummary: 'No issues found.' }
  })
  render(<DiagnosticsCard part={part} descriptor={mockDescriptor} />)
  expect(screen.getByText(/Open in Workbench/)).toBeInTheDocument()
})

it('does not render recommendation section when empty', () => {
  const part = createMockToolPart({
    output: { warnings: [], recommendations: [] }
  })
  render(<DiagnosticsCard part={part} descriptor={mockDescriptor} />)
  expect(screen.queryByText(/Index recommendations:/)).not.toBeInTheDocument()
})
```

---

### Task 16: Activity Rail — Diagnostics Panel

**Files:**
- Create: `client/src/features/stage/components/activity-rail/diagnostics-panel.tsx`
- Create: `client/src/features/stage/components/activity-rail/diagnostics-panel.test.tsx`
- Modify: `client/src/features/stage/components/activity-rail/stage-activity-rail.tsx`

- [x] **Step 16.1: Write diagnostics-panel.tsx**

```typescript
// client/src/features/stage/components/activity-rail/diagnostics-panel.tsx
import { AlertTriangleIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { ExplainPlan, IndexRecommendation } from '@/features/stage/types/diagnostics'

export interface DiagnosticsPanelEntry {
  sql: string
  plan?: ExplainPlan
  recommendations?: IndexRecommendation[]
}

interface Props {
  entry: DiagnosticsPanelEntry | null
  onOpenInWorkbench: () => void
}

export function DiagnosticsPanel({ entry, onOpenInWorkbench }: Props) {
  if (!entry) {
    return (
      <p className="px-3 py-4 text-xs text-muted-foreground">
        Run Explain to view the execution plan.
      </p>
    )
  }

  const warnings = entry.plan?.warnings ?? []
  const recs = entry.recommendations ?? []

  return (
    <div className="flex flex-col gap-2 px-3 py-2 text-xs">
      <p className="font-mono text-[11px] text-muted-foreground truncate">{entry.sql.slice(0, 60)}{entry.sql.length > 60 ? '…' : ''}</p>
      {warnings.length > 0 && (
        <div className="flex items-center gap-1.5 text-amber-700 dark:text-amber-400">
          <AlertTriangleIcon className="size-3" />
          <span>{warnings.length} warning{warnings.length > 1 ? 's' : ''}</span>
        </div>
      )}
      <p className="text-muted-foreground">{recs.length} index recommendation{recs.length !== 1 ? 's' : ''}</p>
      <Button variant="ghost" size="sm" className="self-start px-0 text-xs" onClick={onOpenInWorkbench}>
        Open in Workbench →
      </Button>
    </div>
  )
}
```

- [x] **Step 16.2: Write diagnostics-panel.test.tsx**

```typescript
// client/src/features/stage/components/activity-rail/diagnostics-panel.test.tsx
import { render, screen } from '@testing-library/react'
import { DiagnosticsPanel } from './diagnostics-panel'

it('shows empty state when no entry', () => {
  render(<DiagnosticsPanel entry={null} onOpenInWorkbench={() => {}} />)
  expect(screen.getByText(/Run Explain/)).toBeInTheDocument()
})

it('shows sql snippet and warning count when entry present', () => {
  const entry = {
    sql: 'SELECT * FROM orders',
    plan: { dialect: 'mysql', rawText: '', nodes: [], warnings: ['Full scan on orders'], unsupported: false as const },
    recommendations: [],
  }
  render(<DiagnosticsPanel entry={entry} onOpenInWorkbench={() => {}} />)
  expect(screen.getByText(/SELECT \* FROM orders/)).toBeInTheDocument()
  expect(screen.getByText(/1 warning/)).toBeInTheDocument()
  expect(screen.getByText(/0 index recommendations/)).toBeInTheDocument()
})
```

- [x] **Step 16.3: Add Diagnostics panel to stage-activity-rail.tsx**

In `client/src/features/stage/components/activity-rail/stage-activity-rail.tsx`:

1. Add import:
```typescript
import { SearchCodeIcon } from 'lucide-react'
import { DiagnosticsPanel } from './diagnostics-panel'
```

2. In `panelMeta` array, add entry:
```typescript
{ panel: 'diagnostics', label: t('stage.activityRail.diagnostics.title'), Icon: SearchCodeIcon },
```

3. In `panelTitles` record, add:
```typescript
diagnostics: t('stage.activityRail.diagnostics.title'),
```

4. In the render section inside `<RailPanelShell>`, add after the outline panel:
```typescript
{activePanel === 'diagnostics' ? (
  <DiagnosticsPanel entry={null} onOpenInWorkbench={() => {}} />
) : null}
```

---

### Task 17: SQL Editor Toolbar — Explain Button

**Files:**
- Modify: `client/src/features/stage/components/sql-editor-toolbar.tsx`
- Modify: `client/src/features/stage/components/sql-editor-toolbar.test.tsx`

- [x] **Step 17.1: Add Explain button to SqlEditorToolbar**

Modify `client/src/features/stage/components/sql-editor-toolbar.tsx`:

1. Add `SearchCodeIcon` to lucide-react imports.
2. Add `onExplain` and `canExplain` props:
```typescript
type SqlEditorToolbarProps = {
  contextChip: ReactNode
  canRun: boolean
  isRunning: boolean
  onRun: () => void
  onCancel: () => void
  onFormat: () => void
  limit: SqlLimitValue
  onLimitChange: (value: SqlLimitValue) => void
  canExplain?: boolean
  onExplain?: () => void
}
```

3. Add Explain button after the Format button:
```typescript
{onExplain && (
  <Button
    size="sm"
    variant="ghost"
    onClick={onExplain}
    disabled={!canExplain}
    aria-label={t('stage.toolbar.explain')}
  >
    <SearchCodeIcon />
    {t('stage.toolbar.explain')}
  </Button>
)}
```

- [x] **Step 17.2: Update sql-editor-toolbar.test.tsx**

Add test:
```typescript
it('renders Explain button when onExplain prop provided', () => {
  render(
    <SqlEditorToolbar
      contextChip={null}
      canRun={true}
      isRunning={false}
      onRun={() => {}}
      onCancel={() => {}}
      onFormat={() => {}}
      limit={100}
      onLimitChange={() => {}}
      canExplain={true}
      onExplain={() => {}}
    />
  )
  expect(screen.getByRole('button', { name: /explain/i })).toBeEnabled()
})

it('Explain button is disabled when canExplain is false', () => {
  render(
    <SqlEditorToolbar
      contextChip={null}
      canRun={false}
      isRunning={false}
      onRun={() => {}}
      onCancel={() => {}}
      onFormat={() => {}}
      limit={100}
      onLimitChange={() => {}}
      canExplain={false}
      onExplain={() => {}}
    />
  )
  expect(screen.getByRole('button', { name: /explain/i })).toBeDisabled()
})
```

---

### Task 18: Frontend i18n

**Files:**
- Modify: `client/src/i18n/messages.ts`

- [x] **Step 18.1: Add i18n keys to messages.ts**

In the `zh-CN` section, add after `'stage.toolbar.format'`:
```typescript
'stage.toolbar.explain': '执行计划',
'stage.activityRail.diagnostics.title': '诊断',
'tabType.diagnostic': '诊断',
```

In the `en-US` section (around line 813), add after `'stage.toolbar.format'`:
```typescript
'stage.toolbar.explain': 'Explain',
'stage.activityRail.diagnostics.title': 'Diagnostics',
'tabType.diagnostic': 'Diagnostics',
```

---

## Batch F Verification

- [x] **Run frontend type check**

```bash
cd /home/wallfacers/project/data-talk/client && npx tsc --noEmit
```
Expected: Zero errors.

- [x] **Run targeted frontend tests**

```bash
cd /home/wallfacers/project/data-talk/client && npx vitest run src/features/stage/components/diagnostics src/features/chat/components/tools/renderers/diagnostics-card src/features/stage/components/activity-rail/diagnostics-panel src/features/stage/components/sql-editor-toolbar src/features/stage/registry
```
Expected: All tests pass.

---

## Batch U — Integration (sequential)

### Task 19: AGENTS.md Update

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

- [x] **Step 19.1: Add diagnostics tools section to AGENTS.md**

In `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`, append a new section after the existing Registered Actions:

```markdown
### Query Diagnostics

- `datatalk_explain_query`
  Get a normalized execution plan tree for a SQL statement.
  Input: `{ "sql": "<sql>" }`. Connection context is taken from the current session.
  Output: `{ dialect, rawText, nodes, warnings, unsupported }`.
  Call when: user asks "why is this slow", "show execution plan", or any query performance question.
  Do not call when: the user only asks about SQL correctness, not performance.

- `datatalk_index_hints`
  Get index recommendations by internally running EXPLAIN and analyzing the result.
  Input: `{ "sql": "<sql>" }`.
  Output: `{ recommendations: [...], explainSummary, unsupported }`.
  Call when: user asks for index advice, or `datatalk_explain_query` reveals FULL_SCAN nodes.
  Do not call when: the table has fewer than ~1000 rows (full scan is typically acceptable).

- `datatalk_lock_info`, `datatalk_pool_status`, `datatalk_table_space`
  Not yet available. These return `{ "unsupported": true }`. Do not call them.

### Diagnostics Workflow Rules

1. Performance question received → call `datatalk_explain_query` first.
2. Plan contains FULL_SCAN nodes or non-empty `warnings` → call `datatalk_index_hints`.
3. Present `explainSummary` + recommendation `rationale` values as natural language to the user.
4. Do not infer index recommendations from schema alone — always base them on actual EXPLAIN output.
5. Do not run `datatalk_read_schema` before `datatalk_explain_query` to pre-load context.
6. Index recommendations are suggestions only. If the user confirms they want to create an index, generate the `CREATE INDEX` SQL and route it through the standard Guarded DDL flow.
```

---

### Task 20: Consolidated Verification

- [x] **Step 20.1: Full backend verify**

```bash
cd /home/wallfacers/project/data-talk/server && mvn clean verify -q
```
Expected: BUILD SUCCESS. All tests pass including ITs.

- [x] **Step 20.2: Frontend type check**

```bash
cd /home/wallfacers/project/data-talk/client && npx tsc --noEmit
```
Expected: Zero errors.

- [x] **Step 20.3: Full frontend test suite**

```bash
cd /home/wallfacers/project/data-talk/client && npx vitest run
```
Expected: All tests pass. No regressions.

- [x] **Step 20.4: Commit**

```bash
cd /home/wallfacers/project/data-talk
git add server/data-talk-domain/src server/data-talk-application/src server/data-talk-infrastructure/src server/data-talk-adapter/src client/src docs/exec-plans/2026-04-27-intelligent-operations-plan.md docs/product-specs/2026-04-27-intelligent-operations-design.md docs/product-specs/index.md
git commit -m "feat(diagnostics): add EXPLAIN + index hints platform with MySQL/PG/H2 providers

Introduces DiagnosticsProvider Strategy + DiagnosticsService orchestration,
ExplainQueryAction + IndexHintsAction AI tools, DiagnosticsController REST,
DiagnosticsTab (WORKBENCH scope), DiagnosticsCard chat renderer, Activity Rail
Diagnostics panel, and SQL toolbar Explain button. Oracle stub registered.
Lock/Pool/Space stubs registered for future expansion.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## Post-Execution Housekeeping

- [x] Mark all tasks above as completed in this file
- [x] Move this plan from Active to Completed in `docs/exec-plans/index.md`
- [x] Update `docs/exec-plans/2026-04-25-next-implementation-roadmap-plan.md` Task 7 steps as completed
