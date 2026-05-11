# Data Source Coverage: GaussDB Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GaussDB 集中式 first-class 数据源支持（Wave C step 6 final），密码连接，PG-fork 复用模式。

**Architecture:** `ConnectionKind.GAUSSDB` + `gaussdbjdbc` 驱动 + PG splitter 路由 + 独立风险分类器 + dialect_unsupported diagnostics。遵循 Dameng/KingbaseES 已验证模式。

**Tech Stack:** Spring Boot 3.5 / Java 21 / JUnit 5 + AssertJ / React 19 + TypeScript (vitest)

**Spec:** [docs/product-specs/2026-05-12-data-source-coverage-gaussdb-design.md](../product-specs/2026-05-12-data-source-coverage-gaussdb-design.md)

---

## File Structure

```
server/data-talk-infrastructure/pom.xml
  [+dependency] com.huaweicloud:gaussdbjdbc:v2.0-8.218.0

server/data-talk-application/src/main/java/com/datatalk/application/connection/
  ConnectionKind.java                                    [MODIFY] +GAUSSDB +normalize +reject
  JdbcUrlBuilder.java                                    [MODIFY] +GAUSSDB branch
  ConnectionService.java                                 [MODIFY] +GAUSSDB testConnection branch

server/data-talk-application/src/main/java/com/datatalk/application/sql/
  CalciteSqlRiskAnalyzer.java                            [MODIFY] +classifyGaussdbSpecific

server/data-talk-application/src/main/java/com/datatalk/application/session/
  ConnectionTargetDiscoveryService.java                  [MODIFY] +GAUSSDB_SYSTEM_SCHEMAS

server/data-talk-infrastructure/src/main/java/com/datatalk/sql/
  DefaultSqlStatementSplitters.java                      [MODIFY] +gaussdb → PG splitter

server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/
  GaussDBDiagnosticsProvider.java                        [CREATE]

server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/
  ConnectionObjectType.java                              [MODIFY] +gaussdb enum value

server/data-talk-adapter/src/main/resources/agents/AGENTS.md
  [MODIFY] +GaussDB section

client/src/features/settings/data-sources/
  gaussdb-connection-fields.tsx                          [CREATE]

client/src/features/settings/data-sources/connection-form.tsx
  [MODIFY] +gaussdb kind routing

client/src/features/settings/data-sources/connection-picker.tsx
  [MODIFY] +gaussdb display name

client/src/features/chat/utils/format-sql.ts
  [MODIFY] +gaussdb formatter routing

# Tests
server/data-talk-infrastructure/src/test/java/com/datatalk/sql/
  DefaultSqlStatementSplittersTest.java                  [MODIFY] +gaussdb routing test

server/data-talk-infrastructure/src/test/java/com/datatalk/infra/coverage/gaussdb/
  GaussDBSplitterEquivalenceTest.java                    [CREATE]
  GaussDBDriverCoexistenceTest.java                      [CREATE]

server/data-talk-application/src/test/java/com/datatalk/application/sql/
  CalciteSqlRiskAnalyzerTest.java                        [MODIFY] +gaussdb risk tests

# i18n
server/data-talk-adapter/src/main/resources/i18n/messages.properties
server/data-talk-adapter/src/main/resources/i18n/messages_zh.properties
client/src/i18n/locales/en.json
client/src/i18n/locales/zh.json
```

---

## Task T1: ConnectionKind + Driver Dependency + JdbcUrlBuilder + Splitter Routing

**Files:**
- Modify: `server/data-talk-infrastructure/pom.xml`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java`

- [ ] **Step 1:** Add GaussDB driver dependency to `server/data-talk-infrastructure/pom.xml`

```xml
<!-- GaussDB JDBC driver -->
<dependency>
    <groupId>com.huaweicloud</groupId>
    <artifactId>gaussdbjdbc</artifactId>
    <version>v2.0-8.218.0</version>
</dependency>
```

- [ ] **Step 2:** Add `GAUSSDB` constant + normalize + reject to `ConnectionKind.java`

```java
public static final String GAUSSDB = "gaussdb";
```

Add to normalize: `if (equalsIgnoreCase(trimmed, GAUSSDB)) return GAUSSDB;`

Add REJECTED_KNOWN set:

```java
private static final Set<String> REJECTED_KNOWN_GAUSSDB = Set.of(
    "gauss_db", "gauss", "gaussdb200"
);
```

Update `REJECTED_KNOWN` union to include `REJECTED_KNOWN_GAUSSDB`.

Add reject message: `"unknown database kind '" + input + "'. Use 'gaussdb' for GaussDB."`

- [ ] **Step 3:** Add GAUSSDB branch in `JdbcUrlBuilder.java`

```java
case ConnectionKind.GAUSSDB -> {
    String db = c.databaseName();
    if (db == null || db.isBlank()) {
        throw new DataTalkException(DataTalkErrorCodes.DATABASE_NAME_REQUIRED,
            "gaussdb requires database", false);
    }
    props.putIfAbsent("loginTimeout", "10");
    yield "jdbc:postgresql://" + c.host() + ":" + c.port() + "/" + db;
}
```

- [ ] **Step 4:** Add `"gaussdb"` to PG splitter routing in `DefaultSqlStatementSplitters.java` line 27

Change: `"kingbase".equalsIgnoreCase(connectionKind)` → also include `"gaussdb".equalsIgnoreCase(connectionKind)`

- [ ] **Step 5:** Compile verify

Run: `cd server && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6:** Commit

```bash
git add server/data-talk-infrastructure/pom.xml \
        server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java \
        server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java \
        server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java
git commit -m "feat(gaussdb): ConnectionKind + driver + JdbcUrlBuilder + PG splitter routing"
```

---

## Task T2: System Schema Filter + ConnectionService + SqlExecuteService Gate

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java` (if kind gate exists)

- [ ] **Step 1:** Add `GAUSSDB_SYSTEM_SCHEMAS` constant to `ConnectionTargetDiscoveryService.java`

```java
private static final Set<String> GAUSSDB_SYSTEM_SCHEMAS = Set.of(
    "pg_catalog", "information_schema", "pg_toast", "pg_temp_1", "pg_toast_temp_1",
    "db_scheduler", "db4ai", "pkg_service", "sqladvisor", "wdr_snapshot", "snapshot"
);
```

Add to `isUserSchema` method: `&& !GAUSSDB_SYSTEM_SCHEMAS.contains(normalized)`

- [ ] **Step 2:** Add GAUSSDB branch in `ConnectionService.testConnection`

```java
} else if (kind.equals(ConnectionKind.GAUSSDB)) {
    int timeoutSeconds = Math.max(1, c.connectTimeout() / 1000);
    java.sql.DriverManager.setLoginTimeout(timeoutSeconds);
}
```

- [ ] **Step 3:** Add GAUSSDB to SqlExecuteService kind gate (if `connectionKind` switch exists)

Check for any switch statement routing by kind and add `gaussdb` alongside `kingbase` / `postgresql`.

- [ ] **Step 4:** Compile verify

Run: `cd server && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5:** Commit

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java \
        server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java
git commit -m "feat(gaussdb): system schema filter + ConnectionService timeout"
```

---

## Task T3: Risk Classifier — classifyGaussdbSpecific

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java`

- [ ] **Step 1:** Add GaussDB-specific L3 patterns

```java
// GaussDB L3 admin command patterns
private static final Pattern GAUSSDB_CREATE_RESOURCE_POOL =
    Pattern.compile("^\\s*CREATE\\s+RESOURCE\\s+POOL\\b", Pattern.CASE_INSENSITIVE);
private static final Pattern GAUSSDB_ALTER_COORDINATOR =
    Pattern.compile("^\\s*ALTER\\s+COORDINATOR\\b", Pattern.CASE_INSENSITIVE);
private static final Pattern GAUSSDB_DROP_NODE =
    Pattern.compile("^\\s*DROP\\s+NODE\\b", Pattern.CASE_INSENSITIVE);
private static final Pattern GAUSSDB_SHUTDOWN =
    Pattern.compile("^\\s*SHUTDOWN\\b", Pattern.CASE_INSENSITIVE);
private static final Pattern GAUSSDB_ALTER_SYSTEM_SET =
    Pattern.compile("^\\s*ALTER\\s+SYSTEM\\s+SET\\b", Pattern.CASE_INSENSITIVE);
```

- [ ] **Step 2:** Add `classifyGaussdbSpecific` method

```java
public SqlRiskAnalysis classifyGaussdbSpecific(String sql) {
    if (sql == null) return null;
    String stripped = stripLeadingComments(sql);
    if (stripped.isEmpty()) return null;
    if (GAUSSDB_CREATE_RESOURCE_POOL.matcher(stripped).find()
        || GAUSSDB_ALTER_COORDINATOR.matcher(stripped).find()
        || GAUSSDB_DROP_NODE.matcher(stripped).find()
        || GAUSSDB_SHUTDOWN.matcher(stripped).find()
        || GAUSSDB_ALTER_SYSTEM_SET.matcher(stripped).find()) {
        return SqlRiskAnalysis.high("gaussdb_admin_command");
    }
    return null;
}
```

- [ ] **Step 3:** Wire into main classify dispatch — add GAUSSDB branch alongside existing KINGBASE branch

- [ ] **Step 4:** Write test — `CalciteSqlRiskAnalyzerTest` add gaussdb test cases

- [ ] **Step 5:** Compile + test verify

Run: `cd server && mvn -pl data-talk-application -am test -Dtest='*SqlRisk*' -q`
Expected: PASS

- [ ] **Step 6:** Commit

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java
git commit -m "feat(gaussdb): risk classifier classifyGaussdbSpecific L3 patterns"
```

---

## Task T4: GaussDBDiagnosticsProvider + i18n

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/GaussDBDiagnosticsProvider.java`
- Modify: `server/data-talk-adapter/src/main/resources/i18n/messages.properties`
- Modify: `server/data-talk-adapter/src/main/resources/i18n/messages_zh.properties`

- [ ] **Step 1:** Create `GaussDBDiagnosticsProvider.java`

Follow `KingbaseDiagnosticsProvider` pattern: extends `AbstractDiagnosticsProvider`, `supportedDriverTypes()` returns `Set.of("gaussdb")`, all 9 hooks return `dialectUnsupported("capability_name")`.

- [ ] **Step 2:** Add i18n keys for diagnostics unsupported messages

```properties
# messages.properties
diagnostics.dialect_unsupported.gaussdb.explain_real=GaussDB EXPLAIN analysis is not yet supported
diagnostics.dialect_unsupported.gaussdb.index_hints=GaussDB index recommendations are not yet supported
diagnostics.dialect_unsupported.gaussdb.table_size=GaussDB table size analysis is not yet supported
diagnostics.dialect_unsupported.gaussdb.index_usage=GaussDB index usage analysis is not yet supported
diagnostics.dialect_unsupported.gaussdb.long_running=GaussDB long-running query analysis is not yet supported
diagnostics.dialect_unsupported.gaussdb.table_bloat=GaussDB table bloat analysis is not yet supported
diagnostics.dialect_unsupported.gaussdb.connection_stats=GaussDB connection statistics are not yet supported
diagnostics.dialect_unsupported.gaussdb.lock_info=GaussDB lock information is not yet supported
diagnostics.dialect_unsupported.gaussdb.replication_lag=GaussDB replication lag monitoring is not yet supported

# Risk descriptions
risk.gaussdb.create_resource_pool.description=GaussDB resource pool management
risk.gaussdb.alter_coordinator.description=GaussDB coordinator management
risk.gaussdb.drop_node.description=GaussDB node removal
risk.gaussdb.shutdown.description=GaussDB instance shutdown
risk.gaussdb.alter_system_set.description=GaussDB system configuration change
```

```properties
# messages_zh.properties
diagnostics.dialect_unsupported.gaussdb.explain_real=GaussDB EXPLAIN 分析暂不支持
...
risk.gaussdb.create_resource_pool.description=GaussDB 资源池管理
...
```

- [ ] **Step 3:** Compile verify

Run: `cd server && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4:** Commit

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/GaussDBDiagnosticsProvider.java \
        server/data-talk-adapter/src/main/resources/i18n/
git commit -m "feat(gaussdb): GaussDBDiagnosticsProvider + i18n"
```

---

## Task T5: Tests — Splitter Equivalence + Driver Coexistence

**Files:**
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/coverage/gaussdb/GaussDBSplitterEquivalenceTest.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/coverage/gaussdb/GaussDBDriverCoexistenceTest.java`
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/sql/DefaultSqlStatementSplittersTest.java`

- [ ] **Step 1:** Create `GaussDBSplitterEquivalenceTest.java`

Follow `KingbaseSplitterEquivalenceTest` pattern: 6 test cases using `PostgresJdbcSqlStatementSplitter`.

- [ ] **Step 2:** Create `GaussDBDriverCoexistenceTest.java`

```java
// Verify DriverManager does not register duplicate org.postgresql.Driver
// Verify PG URL and GaussDB URL both resolve to a Driver
// Follow KingbaseDriverCoexistenceTest pattern
```

- [ ] **Step 3:** Add gaussdb routing test to `DefaultSqlStatementSplittersTest.java`

- [ ] **Step 4:** Run tests

Run: `cd server && mvn -pl data-talk-infrastructure -am test -Dtest='*GaussDB*,*DefaultSqlStatementSplitters*' -q`
Expected: PASS

- [ ] **Step 5:** Commit

```bash
git add server/data-talk-infrastructure/src/test/java/com/datatalk/infra/coverage/gaussdb/ \
        server/data-talk-infrastructure/src/test/java/com/datatalk/sql/DefaultSqlStatementSplittersTest.java
git commit -m "test(gaussdb): splitter equivalence + driver coexistence"
```

---

## Task T6: Frontend — Connection Form + Picker + Formatter + i18n

**Files:**
- Create: `client/src/features/settings/data-sources/gaussdb-connection-fields.tsx`
- Modify: `client/src/features/settings/data-sources/connection-form.tsx`
- Modify: `client/src/features/settings/data-sources/connection-picker.tsx`
- Modify: `client/src/features/chat/utils/format-sql.ts`
- Modify: `client/src/i18n/locales/en.json`
- Modify: `client/src/i18n/locales/zh.json`

- [ ] **Step 1:** Create `gaussdb-connection-fields.tsx`

Simple component — no multi-mode, just renders the standard PG-style fields with default port 8000. Follow `dameng-connection-fields.tsx` pattern (simple kind without multi-mode).

- [ ] **Step 2:** Add gaussdb routing in `connection-form.tsx`

- [ ] **Step 3:** Add gaussdb display name in `connection-picker.tsx`

- [ ] **Step 4:** Add gaussdb formatter routing in `format-sql.ts` (route to PG formatter)

- [ ] **Step 5:** Add i18n keys

```json
// en.json
"connection.kind.gaussdb.label": "GaussDB",
"connection.kind.gaussdb.defaultPort": "8000"

// zh.json
"connection.kind.gaussdb.label": "GaussDB",
"connection.kind.gaussdb.defaultPort": "8000"
```

- [ ] **Step 6:** Type check + test

Run: `cd client && npx tsc --noEmit`
Expected: 0 errors

- [ ] **Step 7:** Commit

```bash
git add client/src/features/settings/data-sources/gaussdb-connection-fields.tsx \
        client/src/features/settings/data-sources/connection-form.tsx \
        client/src/features/settings/data-sources/connection-picker.tsx \
        client/src/features/chat/utils/format-sql.ts \
        client/src/i18n/locales/
git commit -m "feat(client): gaussdb connection form + picker + formatter + i18n"
```

---

## Task T7: MCP ConnectionObjectType + AGENTS.md

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java`
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

- [ ] **Step 1:** Add `"gaussdb"` to ConnectionObjectType enum list

- [ ] **Step 2:** Add `## GaussDB` section to AGENTS.md

```markdown
## GaussDB

- **Canonical kind:** `gaussdb`
- **Protocol:** PostgreSQL-compatible (集中式), password authentication
- **JDBC URL:** `jdbc:postgresql://host:8000/database`
- **Default port:** 8000
- **Driver:** `com.huaweicloud:gaussdbjdbc:v2.0-8.218.0`
- **SQL splitter:** PG splitter (dollar-quoted, PL/pgSQL, stored procedures)
- **Risk rules:** PG baseline + 5 GaussDB-specific L3 (CREATE RESOURCE POOL, ALTER COORDINATOR, DROP NODE, SHUTDOWN, ALTER SYSTEM SET)
- **Diagnostics:** All unsupported (Day-1)
- **ER:** Unsupported (Day-1)
- **Day-2:** EXPLAIN diagnostics, ER DDL, SSL/TLS
- **Day-3:** Distributed/DWS mode, Kerberos
```

- [ ] **Step 3:** Commit

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java \
        server/data-talk-adapter/src/main/resources/agents/AGENTS.md
git commit -m "feat(gaussdb): MCP ConnectionObjectType + AGENTS.md"
```

---

## Task T8: Full Verify + Documentation Housekeeping

- [ ] **Step 1:** Full backend verify

Run: `cd server && mvn clean verify -q`
Expected: BUILD SUCCESS

- [ ] **Step 2:** Frontend type check + test

Run: `cd client && npx tsc --noEmit && npx vitest run`
Expected: 0 errors, all pass

- [ ] **Step 3:** Update `docs/exec-plans/index.md` — move this plan to Completed

- [ ] **Step 4:** Update `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` — add gaussdb row

- [ ] **Step 5:** Update `docs/exec-plans/2026-04-25-next-implementation-roadmap-plan.md` — Task 9 Wave C 6/6 complete

- [ ] **Step 6:** Commit

```bash
git add docs/exec-plans/2026-05-12-data-source-coverage-gaussdb-plan.md \
        docs/exec-plans/index.md \
        docs/DATA_SOURCE_TYPE_COMPATIBILITY.md \
        docs/exec-plans/2026-04-25-next-implementation-roadmap-plan.md \
        docs/product-specs/2026-05-12-data-source-coverage-gaussdb-design.md
git commit -m "docs(gaussdb): plan completion + index + compatibility gate + roadmap update"
```
