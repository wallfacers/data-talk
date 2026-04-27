# Guarded DDL / DML Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the existing backend SQL risk classification (L1 / L2 / L3) into a user-visible, server-validated confirmation flow that gates execution of mutating and destructive SQL across both the SQL Workbench and chat tool paths.

**Architecture:** Backend `SqlExecuteService` and `ExecuteSqlAction` adopt a single state machine — L1 executes directly, L2 / L3 returns `requires_confirmation` until the client re-submits with `confirmed=true` and a matching `riskAck`. The `/api/sql/execute` REST path uses two HTTP requests; the chat tool path pauses the action via `actionResult` (the channel's existing pause-resume protocol). A shared `<SqlConfirmationCard>` React component renders the visual core in both an `AlertDialog` (Workbench) and an inline tool card (chat).

**Tech Stack:** Spring Boot 3.5, Java 21, Apache Calcite, JdbcTemplate, JUnit 5, AssertJ, WireMock, React 19, TypeScript, Zustand, TanStack Query, shadcn/ui, Vitest, Testing Library.

---

## Design Inputs

Source spec: [Guarded DDL / DML Execution Design](../product-specs/2026-04-25-guarded-ddl-dml-execution-design.md).

Applicable [client/DESIGN.md](../../client/DESIGN.md) constraints:

- Destructive confirmation uses the `Dialog` / `AlertDialog` shadcn primitive; danger surfaces use `status.danger` + `status.dangerSurface`; warnings use `accent.warn`.
- Destructive button is the dialog's primary action; Cancel is `outline` / `secondary`.
- Color cannot carry semantics alone — pair with text and an icon.
- Focus rings visible in both themes; `prefers-reduced-motion` disables non-essential motion.
- Dialog uses `focused` density and `fast 120ms` / `normal 180ms` motion tokens.
- The "type the table name to confirm" anti-pattern is excluded.

## Current Code Map

| File | Current role |
|------|--------------|
| `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java` | AST-based L1 / L2 / L3 classification. `DELETE` is currently always L3. |
| `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlRiskAnalysis.java` | Risk record (`riskLevel`, `reason`, `requiresStrongConfirmation`, `fallbackUsed`). |
| `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java` | Splits, classifies, blocks L3 for `source=user`, and executes. Throws `SqlRiskBlockedException` instead of returning a confirmation status. |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteRequest.java` | REST input record — has no `confirmed` / `riskAck`. |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteResult.java` | REST output record — has no `status` / `risk` / `sqlPreview`. |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SqlExecuteController.java` | Wires HTTP → service. |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java` | Chat tool action — calls `guard.assertSelectOnly` and rejects any non-SELECT today. Registered with `riskLevel = { RiskLevel.L1 }`. |
| `client/src/features/chat/components/tools/renderers/preview-sql.tsx` | Existing renderer; **currently not registered** to any tool name (dead in registry). Uses `client.actionResult(callID, true, { confirmed })`. |
| `client/src/features/chat/components/tools/renderers/execute-sql.tsx` | Renders `datatalk_execute_sql` results. |
| `client/src/services/api/sql.ts` | `executeSql` REST helper. |
| `client/src/features/stage/hooks/use-sql-execute.ts` | Workbench execute hook; current state is `idle / running / success / error / risk_blocked`. |
| `client/src/features/stage/utils/query-editor-actions.ts` | Catches `SqlRiskError` and writes `setRiskBlocked` — no UI follows. |
| `client/src/features/stage/stores/sql-workbench-store.ts` | Per-tab Zustand store. |
| `client/src/features/stage/components/sql-workbench-tab.tsx` | Workbench tab shell — owns Run handler. |
| `client/src/i18n/messages.ts` | zh-CN / en-US copy. |

## Non-Goals

- No server-side confirmation token / nonce cache.
- No live `EXPLAIN` / `SELECT COUNT(*)` impact estimation.
- No transactional rollback / audit log surfaces.
- No per-statement confirmation in multi-statement scripts (the entire batch is gated as one).
- No schema-diff / DDL impact analysis.
- No new endpoints; existing `/api/sql/execute` is reused with extended request / response shape.

## Ordering And Parallelism

- **Backend Group A** (Tasks 1–4): risk model, DTOs, service state machine, REST IT. Tasks 1 and 3 can run in parallel; Tasks 2 and 4 depend on them.
- **Backend Group B** (Task 5): `ExecuteSqlAction` chat tool path. Depends on Group A's service refactor.
- **Frontend Group C** (Tasks 6, 7): shared `<SqlConfirmationCard>` and API service shape. Independent of each other; Task 6 can run in parallel with all backend tasks; Task 7 needs Task 3 (DTO contract) merged.
- **Frontend Group D** (Tasks 8, 9): Workbench wiring; Chat renderer wiring. Both depend on Tasks 6 and 7. Tasks 8 and 9 can run in parallel.
- **Final** (Task 10): Full verification + housekeeping.

---

## Task 1: Reclassify `DELETE WITH WHERE` And Extract Affected Objects

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlRiskAnalysis.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzerTest.java`

- [x] **Step 1.1: Extend `SqlRiskAnalysis` with `affectedObjects`**

Modify the record to carry the parsed table identifiers (used by the UI to surface what gets touched):

```java
package com.datatalk.application.sql;

import com.datatalk.domain.action.RiskLevel;

import java.util.List;

public record SqlRiskAnalysis(
    RiskLevel riskLevel,
    String reason,
    boolean requiresStrongConfirmation,
    boolean fallbackUsed,
    List<String> affectedObjects
) {

    public static SqlRiskAnalysis low(String reason) {
        return new SqlRiskAnalysis(RiskLevel.L1, reason, false, false, List.of());
    }

    public static SqlRiskAnalysis medium(String reason) {
        return new SqlRiskAnalysis(RiskLevel.L2, reason, false, false, List.of());
    }

    public static SqlRiskAnalysis high(String reason) {
        return new SqlRiskAnalysis(RiskLevel.L3, reason, true, false, List.of());
    }

    public static SqlRiskAnalysis fallback(String reason) {
        return new SqlRiskAnalysis(null, reason, false, true, List.of());
    }

    public SqlRiskAnalysis withAffectedObjects(List<String> objects) {
        return new SqlRiskAnalysis(
            riskLevel,
            reason,
            requiresStrongConfirmation,
            fallbackUsed,
            objects == null ? List.of() : List.copyOf(objects)
        );
    }
}
```

- [x] **Step 1.2: Run existing risk analyzer tests to confirm they still compile**

Run: `cd server && mvn -q -pl data-talk-application test -Dtest=CalciteSqlRiskAnalyzerTest`

Expected: existing tests pass; the new field defaults to `List.of()` so call sites that destructure are unaffected.

- [x] **Step 1.3: Add failing tests for new behavior**

Append to `CalciteSqlRiskAnalyzerTest.java`:

```java
import org.junit.jupiter.api.Test;
import com.datatalk.domain.action.RiskLevel;
import static org.assertj.core.api.Assertions.assertThat;

@Test
void deleteWithWhereIsMedium() {
    var result = analyzer.analyze("DELETE FROM users WHERE id = 1", Category.QUERY);
    assertThat(result.riskLevel()).isEqualTo(RiskLevel.L2);
    assertThat(result.reason()).isEqualTo("delete_with_where");
    assertThat(result.affectedObjects()).containsExactly("users");
}

@Test
void deleteWithoutWhereIsHigh() {
    var result = analyzer.analyze("DELETE FROM users", Category.QUERY);
    assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
    assertThat(result.reason()).isEqualTo("delete_without_where");
    assertThat(result.affectedObjects()).containsExactly("users");
}

@Test
void updateWithWhereExposesAffectedObjects() {
    var result = analyzer.analyze("UPDATE orders SET status = 'paid' WHERE id = 9", Category.QUERY);
    assertThat(result.riskLevel()).isEqualTo(RiskLevel.L2);
    assertThat(result.affectedObjects()).containsExactly("orders");
}

@Test
void dropTableExposesAffectedObjects() {
    var result = analyzer.analyze("DROP TABLE temp_log", Category.MUTATION);
    assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
    assertThat(result.affectedObjects()).containsExactly("temp_log");
}

@Test
void multiStatementBatchUsesHighestRiskAndUnionAffectedObjects() {
    var result = analyzer.analyze(
        "UPDATE orders SET note = 'x' WHERE id = 1; DELETE FROM logs;",
        Category.MUTATION
    );
    assertThat(result.riskLevel()).isEqualTo(RiskLevel.L3);
    assertThat(result.affectedObjects()).containsExactlyInAnyOrder("orders", "logs");
}
```

- [x] **Step 1.4: Run new tests to confirm they fail**

Run: `cd server && mvn -q -pl data-talk-application test -Dtest=CalciteSqlRiskAnalyzerTest`

Expected: the four new tests fail — `deleteWithWhereIsMedium` because current code returns L3; `affectedObjects` cases fail because the analyzer never populates the field.

- [x] **Step 1.5: Implement the analyzer changes**

Modify `CalciteSqlRiskAnalyzer.classify(SqlNode)`:

```java
private SqlRiskAnalysis classify(SqlNode node) {
    if (node instanceof SqlWith with) {
        return max(SqlRiskAnalysis.high("with_dml"), classify(with.body))
            .withAffectedObjects(extractObjects(with.body));
    }
    if (node instanceof SqlOrderBy orderBy) {
        return classify(orderBy.query);
    }
    if (node instanceof SqlSelect select) {
        return SqlRiskAnalysis.low("select").withAffectedObjects(extractObjects(select));
    }
    if (node instanceof SqlInsert insert) {
        return SqlRiskAnalysis.medium("insert").withAffectedObjects(extractObjects(insert.getTargetTable()));
    }
    if (node instanceof SqlUpdate update) {
        SqlRiskAnalysis base = update.getCondition() == null
            ? SqlRiskAnalysis.high("update_without_where")
            : SqlRiskAnalysis.medium("update_with_where");
        return base.withAffectedObjects(extractObjects(update.getTargetTable()));
    }
    if (node instanceof SqlDelete delete) {
        SqlRiskAnalysis base = delete.getCondition() == null
            ? SqlRiskAnalysis.high("delete_without_where")
            : SqlRiskAnalysis.medium("delete_with_where");
        return base.withAffectedObjects(extractObjects(delete.getTargetTable()));
    }

    String kind = node.getKind().name();
    if ("EXPLAIN".equals(kind) || "DESCRIBE_TABLE".equals(kind) || "DESCRIBE_SCHEMA".equals(kind)) {
        return SqlRiskAnalysis.low(kind.toLowerCase());
    }
    if ("CREATE_VIEW".equals(kind) || "CREATE_INDEX".equals(kind)) {
        return SqlRiskAnalysis.medium(kind.toLowerCase()).withAffectedObjects(extractObjects(node));
    }
    if (kind.startsWith("DROP")
        || kind.startsWith("ALTER")
        || kind.startsWith("TRUNCATE")
        || kind.startsWith("GRANT")
        || kind.startsWith("REVOKE")) {
        return SqlRiskAnalysis.high(kind.toLowerCase()).withAffectedObjects(extractObjects(node));
    }
    if (kind.contains("QUERY") || kind.contains("SELECT") || kind.contains("SHOW")) {
        return SqlRiskAnalysis.low(kind.toLowerCase());
    }
    return SqlRiskAnalysis.high("unclassified:" + kind.toLowerCase());
}

private List<String> extractObjects(SqlNode node) {
    if (node == null) return List.of();
    var names = new java.util.LinkedHashSet<String>();
    node.accept(new org.apache.calcite.sql.util.SqlBasicVisitor<Void>() {
        @Override
        public Void visit(org.apache.calcite.sql.SqlIdentifier id) {
            if (!id.names.isEmpty()) {
                names.add(id.names.get(id.names.size() - 1));
            }
            return null;
        }
    });
    return List.copyOf(names);
}
```

Modify `analyze(...)` so the aggregate carries the union of affected objects:

```java
@Override
public SqlRiskAnalysis analyze(String sql, Category category) {
    try {
        SqlNodeList statements = SqlParser.create(sql).parseStmtList();
        SqlRiskAnalysis aggregate = null;
        var unionObjects = new java.util.LinkedHashSet<String>();
        for (SqlNode statement : statements) {
            SqlRiskAnalysis current = classify(statement);
            unionObjects.addAll(current.affectedObjects());
            aggregate = aggregate == null ? current : max(aggregate, current);
        }
        if (aggregate == null) {
            return fallbackFor(category, "empty");
        }
        return aggregate.withAffectedObjects(List.copyOf(unionObjects));
    } catch (SqlParseException e) {
        return fallbackFor(category, e.getMessage());
    }
}
```

Note: the `extractObjects(SqlSelect)` walk pulls every identifier the visitor sees, which over-collects (e.g. column references). For L1 SELECT we keep the field but UI only displays it for L2 / L3, so over-collection on SELECT is irrelevant to the user-facing flow.

- [x] **Step 1.6: Run all analyzer tests**

Run: `cd server && mvn -q -pl data-talk-application test -Dtest=CalciteSqlRiskAnalyzerTest`

Expected: all tests pass, including the five new cases.

- [x] **Step 1.7: Compile-check the wider application module**

Run: `cd server && mvn -q -pl data-talk-application compile`

Expected: zero errors. Existing `SqlRiskAnalysis` consumers (e.g. `ActionDispatcher`, `SqlExecuteService`) compile because the new field is additive.

- [x] **Step 1.8: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java \
        server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlRiskAnalysis.java \
        server/data-talk-application/src/test/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzerTest.java
git commit -m "feat(sql-risk): reclassify DELETE WITH WHERE to L2 and expose affectedObjects"
```

---

## Task 2: SQL Execute DTO Surface — Confirmation Inputs And Status Outputs

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteRequest.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteResult.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlConfirmationPayload.java`

- [x] **Step 2.1: Extend `SqlExecuteRequest`**

```java
package com.datatalk.adapter.dto;

public record SqlExecuteRequest(
    String connectionId,
    String sql,
    String source,
    String sessionId,
    String database,
    String schema,
    Boolean confirmed,
    String riskAck
) {
    public boolean confirmedFlag() {
        return Boolean.TRUE.equals(confirmed);
    }
}
```

`riskAck` accepts `"L1" | "L2" | "L3"` or null. Both new fields are nullable to preserve wire compatibility with existing clients that omit them.

- [x] **Step 2.2: Create `SqlConfirmationPayload`**

```java
package com.datatalk.adapter.dto;

import java.util.List;

public record SqlConfirmationPayload(
    String level,
    String reason,
    List<String> affectedObjects,
    String sqlPreview
) {}
```

- [x] **Step 2.3: Extend `SqlExecuteResult` with the union shape**

```java
package com.datatalk.adapter.dto;

import com.datatalk.dto.ResolvedDataContextDto;

import java.util.List;

public record SqlExecuteResult(
    String status,
    ResolvedDataContextDto resolvedContext,
    String contextNotice,
    List<SqlExecuteResultItem> results,
    SqlConfirmationPayload confirmation,
    SqlConfirmationInvalid invalidConfirmation
) {

    public static SqlExecuteResult executed(
        ResolvedDataContextDto resolvedContext,
        String contextNotice,
        List<SqlExecuteResultItem> results
    ) {
        return new SqlExecuteResult("executed", resolvedContext, contextNotice, results, null, null);
    }

    public static SqlExecuteResult requiresConfirmation(
        ResolvedDataContextDto resolvedContext,
        String contextNotice,
        SqlConfirmationPayload confirmation
    ) {
        return new SqlExecuteResult(
            "requires_confirmation",
            resolvedContext,
            contextNotice,
            List.of(),
            confirmation,
            null
        );
    }

    public static SqlExecuteResult confirmationInvalid(
        ResolvedDataContextDto resolvedContext,
        String contextNotice,
        SqlConfirmationInvalid invalidConfirmation
    ) {
        return new SqlExecuteResult(
            "confirmation_invalid",
            resolvedContext,
            contextNotice,
            List.of(),
            null,
            invalidConfirmation
        );
    }

    public record SqlConfirmationInvalid(
        String reason,
        String ackedRisk,
        String currentRisk,
        String message
    ) {}
}
```

- [x] **Step 2.4: Compile-check the adapter module**

Run: `cd server && mvn -q -pl data-talk-adapter compile`

Expected: compile errors in `SqlExecuteController` and any code that constructs `SqlExecuteResult(...)` directly. Fix each call site by routing through the new `SqlExecuteResult.executed(...)` factory:

```java
// SqlExecuteController.java — after the service call
return SqlExecuteResult.executed(result.resolvedContext(), result.contextNotice(), items);
```

(The fuller controller wiring lives in Task 3 — at this step do the minimum required to keep `SqlExecuteController` compiling against the new factory.)

- [x] **Step 2.5: Re-run compile**

Run: `cd server && mvn -q -pl data-talk-adapter compile`

Expected: zero errors.

- [x] **Step 2.6: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteRequest.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteResult.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlConfirmationPayload.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SqlExecuteController.java
git commit -m "feat(sql-execute): extend request/response DTOs for confirmation flow"
```

---

## Task 3: `SqlExecuteService` Confirmation State Machine

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SqlExecuteController.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/sql/SqlExecuteServiceTest.java`

- [x] **Step 3.1: Extend the service signature with `confirmed` and `riskAck`**

Add a new top-level result type to `SqlExecuteService` to express the three statuses:

```java
public sealed interface Outcome permits Executed, RequiresConfirmation, ConfirmationInvalid {
    ResolvedDataContextDto resolvedContext();
    String contextNotice();
}

public record Executed(
    ResolvedDataContextDto resolvedContext,
    String contextNotice,
    List<ResultItem> results
) implements Outcome {}

public record RequiresConfirmation(
    ResolvedDataContextDto resolvedContext,
    String contextNotice,
    String level,
    String reason,
    List<String> affectedObjects,
    String sqlPreview
) implements Outcome {}

public record ConfirmationInvalid(
    ResolvedDataContextDto resolvedContext,
    String contextNotice,
    String reason,
    String ackedRisk,
    String currentRisk,
    String message
) implements Outcome {}
```

Replace the public `execute(...)` method:

```java
public Outcome execute(
    String connectionId,
    String sql,
    String source,
    String sessionId,
    String database,
    String schema,
    boolean confirmed,
    RiskLevel riskAck
) {
    if (sql == null || sql.isBlank())
        throw new IllegalArgumentException(translator.get("error.sql.required"));
    String normalizedSource = validateSource(source);

    ResolvedExecutionContext requestedContext = resolveExecutionContext(sessionId, connectionId, database, schema);
    List<String> statements = sqlStatementSplitters.split(requestedContext.connection().kind(), sql);
    if (statements.isEmpty()) {
        throw new IllegalArgumentException(translator.get("error.sql.required"));
    }

    ResolvedExecutionContext context = tableContextAutoResolver.resolve(requestedContext, sql);
    ResolvedDataContextDto resolvedDto = toDto(context);

    SqlRiskAnalysis risk = riskAnalyzer.analyze(sql, Category.QUERY);
    if (risk.riskLevel() != null
        && (risk.riskLevel() == RiskLevel.L2 || risk.riskLevel() == RiskLevel.L3)) {
        if (!confirmed) {
            return new RequiresConfirmation(
                resolvedDto,
                context.contextNotice(),
                risk.riskLevel().name(),
                risk.reason(),
                risk.affectedObjects(),
                sql
            );
        }
        if (riskAck == null || riskAck.ordinal() < risk.riskLevel().ordinal()) {
            return new ConfirmationInvalid(
                resolvedDto,
                context.contextNotice(),
                "risk_ack_insufficient",
                riskAck == null ? null : riskAck.name(),
                risk.riskLevel().name(),
                translator.get("sql.confirmation.invalid.message")
            );
        }
    }

    List<ResultItem> items = runStatements(context, statements);
    return new Executed(resolvedDto, context.contextNotice(), items);
}

private ResolvedDataContextDto toDto(ResolvedExecutionContext context) {
    return new ResolvedDataContextDto(
        context.connection().id(),
        context.connection().name(),
        context.database(),
        context.schema(),
        context.selectedLevel()
    );
}
```

Move the body of the existing `execute(...)` (everything from `ConnectionRecord cr = context.connection();` down to the final `return new Result(...)`) into a private `runStatements(ResolvedExecutionContext context, List<String> statements)` that returns `List<ResultItem>`. Drop the legacy `if ("user".equals(normalizedSource)) ... throw new SqlRiskBlockedException(...)` branch entirely — the state machine above replaces it.

Add a new translation key in `application/src/main/resources/messages_en.properties` and `messages_zh_CN.properties`:

```properties
# en
sql.confirmation.invalid.message=Acknowledged risk is lower than the current statement risk — please review and confirm again

# zh-CN
sql.confirmation.invalid.message=确认的风险等级低于当前 SQL 风险，请重新查看并确认
```

- [x] **Step 3.2: Update `SqlExecuteController` to consume the new outcome and request fields**

```java
@PostMapping("/api/sql/execute")
public SqlExecuteResult execute(@RequestBody SqlExecuteRequest req) {
    RiskLevel ack = parseRiskAck(req.riskAck());
    var outcome = service.execute(
        req.connectionId(),
        req.sql(),
        req.source(),
        req.sessionId(),
        req.database(),
        req.schema(),
        req.confirmedFlag(),
        ack
    );
    return switch (outcome) {
        case SqlExecuteService.Executed e -> SqlExecuteResult.executed(
            e.resolvedContext(),
            e.contextNotice(),
            mapItems(e.results())
        );
        case SqlExecuteService.RequiresConfirmation r -> SqlExecuteResult.requiresConfirmation(
            r.resolvedContext(),
            r.contextNotice(),
            new SqlConfirmationPayload(r.level(), r.reason(), r.affectedObjects(), r.sqlPreview())
        );
        case SqlExecuteService.ConfirmationInvalid i -> SqlExecuteResult.confirmationInvalid(
            i.resolvedContext(),
            i.contextNotice(),
            new SqlExecuteResult.SqlConfirmationInvalid(
                i.reason(), i.ackedRisk(), i.currentRisk(), i.message()
            )
        );
    };
}

private static RiskLevel parseRiskAck(String value) {
    if (value == null || value.isBlank()) return null;
    try {
        return RiskLevel.valueOf(value);
    } catch (IllegalArgumentException ex) {
        return null;
    }
}
```

Remove any existing `@ExceptionHandler` for `SqlRiskBlockedException` — that exception is no longer thrown. Also delete the `SqlRiskBlockedException` class and the `RiskBlocked` record from `SqlExecuteService` (search the repo first for other consumers; remove those too).

- [x] **Step 3.3: Write failing service tests**

Create or extend `SqlExecuteServiceTest.java`:

```java
@Test
void l1ExecutesWithoutConfirmation() {
    var outcome = service.execute(connectionId, "SELECT 1", "user", null, null, null, false, null);
    assertThat(outcome).isInstanceOf(SqlExecuteService.Executed.class);
}

@Test
void l2WithoutConfirmedReturnsRequiresConfirmation() {
    var outcome = service.execute(connectionId, "UPDATE t SET x = 1 WHERE id = 1", "user", null, null, null, false, null);
    assertThat(outcome).isInstanceOfSatisfying(SqlExecuteService.RequiresConfirmation.class, r -> {
        assertThat(r.level()).isEqualTo("L2");
        assertThat(r.reason()).isEqualTo("update_with_where");
        assertThat(r.affectedObjects()).containsExactly("t");
        assertThat(r.sqlPreview()).isEqualTo("UPDATE t SET x = 1 WHERE id = 1");
    });
}

@Test
void l2WithMatchingAckExecutes() {
    var outcome = service.execute(connectionId, "UPDATE t SET x = 1 WHERE id = 1", "user", null, null, null, true, RiskLevel.L2);
    assertThat(outcome).isInstanceOf(SqlExecuteService.Executed.class);
}

@Test
void l2WithLowerAckIsConfirmationInvalid() {
    var outcome = service.execute(connectionId, "UPDATE t SET x = 1 WHERE id = 1", "user", null, null, null, true, RiskLevel.L1);
    assertThat(outcome).isInstanceOfSatisfying(SqlExecuteService.ConfirmationInvalid.class, i -> {
        assertThat(i.reason()).isEqualTo("risk_ack_insufficient");
        assertThat(i.ackedRisk()).isEqualTo("L1");
        assertThat(i.currentRisk()).isEqualTo("L2");
    });
}

@Test
void l3WithoutConfirmedReturnsRequiresConfirmation() {
    var outcome = service.execute(connectionId, "DROP TABLE temp_log", "user", null, null, null, false, null);
    assertThat(outcome).isInstanceOfSatisfying(SqlExecuteService.RequiresConfirmation.class, r -> {
        assertThat(r.level()).isEqualTo("L3");
        assertThat(r.affectedObjects()).containsExactly("temp_log");
    });
}

@Test
void l3WithMatchingAckExecutes() {
    var outcome = service.execute(connectionId, "DROP TABLE temp_log", "user", null, null, null, true, RiskLevel.L3);
    assertThat(outcome).isInstanceOf(SqlExecuteService.Executed.class);
}

@Test
void multiStatementBatchWithL3UsesHighestRisk() {
    var sql = "UPDATE t SET x = 1 WHERE id = 1; DROP TABLE temp_log;";
    var outcome = service.execute(connectionId, sql, "user", null, null, null, false, null);
    assertThat(outcome).isInstanceOfSatisfying(SqlExecuteService.RequiresConfirmation.class, r -> {
        assertThat(r.level()).isEqualTo("L3");
        assertThat(r.affectedObjects()).containsExactlyInAnyOrder("t", "temp_log");
    });
}
```

If `SqlExecuteServiceTest` does not yet exist in the application module, scaffold it with the same `@SpringBootTest`-style or constructor-injected setup used by `CalciteSqlRiskAnalyzerTest`. The connection / repository dependencies can be mocked since these tests do not need to actually run SQL — the state machine returns before reaching the JDBC path for `requires_confirmation` and `confirmation_invalid` cases. For the `Executed` cases use the H2 fixture used by `SqlExecuteControllerIT`.

- [x] **Step 3.4: Run failing tests**

Run: `cd server && mvn -q -pl data-talk-application test -Dtest=SqlExecuteServiceTest`

Expected: all seven new tests fail.

- [x] **Step 3.5: Make the tests pass**

Implementation already drafted in Step 3.1. Re-verify the state machine behavior matches the assertions exactly.

Run: `cd server && mvn -q -pl data-talk-application test -Dtest=SqlExecuteServiceTest`

Expected: all tests pass.

- [x] **Step 3.6: Compile-check the adapter module**

Run: `cd server && mvn -q -pl data-talk-adapter compile`

Expected: zero errors. Fix any remaining call sites of `service.execute(...)` (e.g. in adapter integration tests) by passing the two new arguments.

- [x] **Step 3.7: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java \
        server/data-talk-application/src/main/resources/messages_en.properties \
        server/data-talk-application/src/main/resources/messages_zh_CN.properties \
        server/data-talk-application/src/test/java/com/datatalk/application/sql/SqlExecuteServiceTest.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SqlExecuteController.java
git commit -m "feat(sql-execute): replace block-on-L3 with confirmation state machine"
```

---

## Task 4: REST Integration Test For Two-Step Confirmation

**Files:**
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SqlExecuteControllerIT.java`

- [x] **Step 4.1: Add HTTP-level cases for the new flow**

Append three test methods:

```java
@Test
void l2RequiresConfirmationThenExecutes() {
    var firstReq = baseRequest("UPDATE app_users SET note = 'x' WHERE id = 1");
    var firstResponse = restTemplate.postForEntity("/api/sql/execute", firstReq, SqlExecuteResult.class);
    assertThat(firstResponse.getStatusCode().is2xxSuccessful()).isTrue();
    var firstBody = firstResponse.getBody();
    assertThat(firstBody.status()).isEqualTo("requires_confirmation");
    assertThat(firstBody.confirmation().level()).isEqualTo("L2");
    assertThat(firstBody.confirmation().affectedObjects()).containsExactly("app_users");

    var confirmReq = baseRequestBuilder("UPDATE app_users SET note = 'x' WHERE id = 1")
        .confirmed(true).riskAck("L2").build();
    var second = restTemplate.postForEntity("/api/sql/execute", confirmReq, SqlExecuteResult.class);
    assertThat(second.getBody().status()).isEqualTo("executed");
    assertThat(second.getBody().results()).hasSize(1);
}

@Test
void l3RequiresConfirmationThenExecutes() {
    var firstReq = baseRequest("DROP TABLE app_logs");
    var firstResponse = restTemplate.postForEntity("/api/sql/execute", firstReq, SqlExecuteResult.class);
    assertThat(firstResponse.getBody().status()).isEqualTo("requires_confirmation");
    assertThat(firstResponse.getBody().confirmation().level()).isEqualTo("L3");

    var confirmReq = baseRequestBuilder("DROP TABLE app_logs")
        .confirmed(true).riskAck("L3").build();
    var second = restTemplate.postForEntity("/api/sql/execute", confirmReq, SqlExecuteResult.class);
    assertThat(second.getBody().status()).isEqualTo("executed");
}

@Test
void riskAckLowerThanCurrentReturnsConfirmationInvalid() {
    var req = baseRequestBuilder("UPDATE app_users SET note = 'x' WHERE id = 1")
        .confirmed(true).riskAck("L1").build();
    var response = restTemplate.postForEntity("/api/sql/execute", req, SqlExecuteResult.class);
    assertThat(response.getBody().status()).isEqualTo("confirmation_invalid");
    assertThat(response.getBody().invalidConfirmation().reason()).isEqualTo("risk_ack_insufficient");
    assertThat(response.getBody().invalidConfirmation().ackedRisk()).isEqualTo("L1");
    assertThat(response.getBody().invalidConfirmation().currentRisk()).isEqualTo("L2");
}
```

If a `baseRequest` helper does not exist, look at how the existing IT constructs `SqlExecuteRequest` and write a helper that returns a fully-populated request with the test connection id and `source = "user"`. A `baseRequestBuilder` builder pattern is suggested for the variants that set `confirmed`/`riskAck`. Set up an `app_users` table with one seed row in the existing H2 fixture before each test and an `app_logs` table that is drop-able.

- [x] **Step 4.2: Run the IT**

Run: `cd server && mvn -q -pl data-talk-adapter -am verify -Dit.test=SqlExecuteControllerIT`

Expected: all existing IT cases plus the three new ones pass.

- [x] **Step 4.3: Commit**

```bash
git add server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SqlExecuteControllerIT.java
git commit -m "test(sql-execute): IT coverage for two-step L2/L3 confirmation flow"
```

---

## Task 5: `ExecuteSqlAction` Adopts The Confirmation State Machine

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
- Test: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ExecuteSqlActionTest.java`

- [x] **Step 5.1: Explore the action-result / pause-resume protocol**

Read these files end-to-end before changing `ExecuteSqlAction`:

- `server/data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionBus.java` (if it exists)
- `client/src/services/channel/use-channel.ts` — find `actionResult` and trace what the server side does with it
- Any `ChannelController` HTTP route that receives the `actionResult` payload

The goal is to find the existing mechanism (a `CompletableFuture<JsonNode>`-shaped pending action, a "pause for client input" outcome, or an in-flight registry keyed by `callId`) that lets a server action emit an intermediate part and then resume execution when the client posts an `actionResult`.

Document what you found in a short comment block in the action file before writing code, e.g.:

```java
// The action dispatcher maintains a pendingActions map keyed by callId. Returning
// a CompletionStage that completes after `dispatcher.awaitClientResult(callId)` is
// the supported pattern (see ActionDispatcher#dispatch around line N).
```

If no such mechanism exists today, the action path falls back to: the action returns a `requires_confirmation` outcome immediately; the client renderer (Task 9) issues a fresh `executeSql` invocation with `confirmed=true` and `riskAck`. Document the decision in the comment block and proceed with the fallback shape.

- [x] **Step 5.2: Update `inputSchema` to expose `confirmed` and `riskAck`, and remove the L1-only restriction**

```java
@DataTalkAction(
    id = "datatalk.execute_sql",
    executor = Executor.SERVER,
    description = "action.execute_sql.description",
    produces = {"datatalk.artifact"},
    requiresConnection = true,
    timeoutMs = 30_000,
    riskLevel = { RiskLevel.L1, RiskLevel.L2, RiskLevel.L3 },
    category = { Category.QUERY, Category.MUTATION }
)
public class ExecuteSqlAction implements ActionHandler<Map, Map> {
    // ...
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("sql"),
            "properties", Map.of(
                "connectionId", Map.of("type", "string"),
                "database",     Map.of("type", "string"),
                "schema",       Map.of("type", "string"),
                "sql",          Map.of("type", "string"),
                "pageSize",     Map.of("type", "integer", "minimum", 1, "maximum", 10_000),
                "confirmed",    Map.of("type", "boolean"),
                "riskAck",      Map.of("type", "string", "enum", List.of("L1", "L2", "L3"))
            ));
    }
```

Remove the `guard.assertSelectOnly(sql)` call. The risk state machine now gates non-SELECT.

- [x] **Step 5.3: Inject `SqlRiskAnalyzer` and gate execution**

Add `SqlRiskAnalyzer` to the constructor. In `execute(...)`, immediately after the `sql` is read:

```java
private Map<String, Object> execute(ActionContext ctx, Map<String, Object> input) {
    String sql = String.valueOf(input.get("sql"));
    boolean confirmedFromInput = Boolean.TRUE.equals(input.get("confirmed"));
    String riskAckRaw = nullableString(input, "riskAck");

    SqlRiskAnalysis risk = riskAnalyzer.analyze(sql, Category.QUERY);
    boolean isHigherRisk = risk.riskLevel() == RiskLevel.L2 || risk.riskLevel() == RiskLevel.L3;

    if (isHigherRisk) {
        // confirmed=true on initial AI tool input is NOT trusted (see design §9).
        // Honor it only when delivered via channel actionResult resume; the dispatcher
        // sets ctx.metadata().confirmedByActionResult() in that path. If the explore
        // step in 5.1 found a different signal, use that instead.
        boolean trustedConfirmation = ctx.metadata() != null
            && ctx.metadata().confirmedByActionResult();
        if (!confirmedFromInput || !trustedConfirmation) {
            return Map.of(
                "status", "requires_confirmation",
                "risk", Map.of(
                    "level", risk.riskLevel().name(),
                    "reason", risk.reason(),
                    "affectedObjects", risk.affectedObjects()
                ),
                "sqlPreview", sql
            );
        }
        RiskLevel ack = parseRiskAck(riskAckRaw);
        if (ack == null || ack.ordinal() < risk.riskLevel().ordinal()) {
            return Map.of(
                "status", "confirmation_invalid",
                "reason", "risk_ack_insufficient",
                "ackedRisk", ack == null ? null : ack.name(),
                "currentRisk", risk.riskLevel().name()
            );
        }
    }
    // ... existing JDBC execution path ...
}

private static RiskLevel parseRiskAck(String value) {
    if (value == null || value.isBlank()) return null;
    try { return RiskLevel.valueOf(value); } catch (IllegalArgumentException e) { return null; }
}
```

If the explore step finds a different "trusted-confirmation" mechanism, swap the `ctx.metadata().confirmedByActionResult()` check for whatever the dispatcher actually exposes. If no such mechanism exists, drop the trust check — `confirmed=true` from the action input is honored, and the client (Task 9) is responsible for issuing it only after rendering the confirmation card. Update the design doc §9 to reflect the implementation reality.

- [x] **Step 5.4: Add unit tests for the action**

Create or extend `ExecuteSqlActionTest.java`:

```java
@Test
void l1SelectExecutesAsBefore() {
    var input = Map.of("sql", "SELECT 1", "connectionId", connectionId);
    var output = action.handle(ctx, input).toCompletableFuture().get();
    assertThat(output.get("status")).isNull(); // existing artifact-shaped output
    assertThat(output).containsKey("artifactId");
}

@Test
void l2WithoutConfirmationReturnsRequiresConfirmationPart() {
    var input = Map.of("sql", "UPDATE t SET x = 1 WHERE id = 1", "connectionId", connectionId);
    var output = action.handle(ctx, input).toCompletableFuture().get();
    assertThat(output.get("status")).isEqualTo("requires_confirmation");
    var risk = (Map<String, Object>) output.get("risk");
    assertThat(risk.get("level")).isEqualTo("L2");
    assertThat(risk.get("affectedObjects")).asList().containsExactly("t");
}

@Test
void l3WithoutConfirmationReturnsRequiresConfirmationPart() {
    var input = Map.of("sql", "DROP TABLE temp_log", "connectionId", connectionId);
    var output = action.handle(ctx, input).toCompletableFuture().get();
    assertThat(output.get("status")).isEqualTo("requires_confirmation");
}

@Test
void l3WithMatchingAckExecutes() {
    var input = Map.of(
        "sql", "DROP TABLE temp_log",
        "connectionId", connectionId,
        "confirmed", true,
        "riskAck", "L3"
    );
    // For this test, simulate the trusted-confirmation channel by setting
    // ctx.metadata().confirmedByActionResult() = true (or by whatever the
    // explore step in 5.1 determined the trust signal to be).
    var output = action.handle(ctxWithTrustedConfirmation, input).toCompletableFuture().get();
    assertThat(output.get("status")).isNull();
    assertThat(output).containsKey("artifactId");
}

@Test
void l2WithLowerAckReturnsConfirmationInvalid() {
    var input = Map.of(
        "sql", "UPDATE t SET x = 1 WHERE id = 1",
        "connectionId", connectionId,
        "confirmed", true,
        "riskAck", "L1"
    );
    var output = action.handle(ctxWithTrustedConfirmation, input).toCompletableFuture().get();
    assertThat(output.get("status")).isEqualTo("confirmation_invalid");
    assertThat(output.get("reason")).isEqualTo("risk_ack_insufficient");
}
```

If the trust-signal field name differs from `confirmedByActionResult()`, adjust the test fixture accordingly.

- [x] **Step 5.5: Run the action tests**

Run: `cd server && mvn -q -pl data-talk-adapter test -Dtest=ExecuteSqlActionTest`

Expected: all five tests pass.

- [x] **Step 5.6: Verify nothing broke at the action-dispatcher level**

Run: `cd server && mvn -q -pl data-talk-adapter test -Dtest=ActionDispatcherTest`

Expected: existing dispatcher tests still pass.

- [x] **Step 5.7: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ExecuteSqlActionTest.java
git commit -m "feat(execute-sql-action): adopt confirmation state machine for L2/L3"
```

---

## Task 6: Shared `<SqlConfirmationCard>` Component

**Files:**
- Create: `client/src/features/sql-confirmation/sql-confirmation-card.tsx`
- Create: `client/src/features/sql-confirmation/sql-confirmation-card.test.tsx`
- Modify: `client/src/i18n/messages.ts`

- [x] **Step 6.1: Add i18n keys**

Add to `client/src/i18n/messages.ts` under both `zh-CN` and `en-US`:

```ts
// en-US
'sqlConfirmation.l2.title': 'Bounded mutation',
'sqlConfirmation.l3.title': 'Destructive operation',
'sqlConfirmation.l2.body': 'This will modify data in {objects}.',
'sqlConfirmation.l3.body': 'This will permanently affect {objects}.',
'sqlConfirmation.l3.irreversible': 'This action cannot be undone.',
'sqlConfirmation.affectedObjects': 'Affected objects',
'sqlConfirmation.cancel': 'Cancel',
'sqlConfirmation.execute': 'Execute',
'sqlConfirmation.executing': 'Executing…',
'sqlConfirmation.invalid.title': 'Confirmation no longer valid',
'sqlConfirmation.invalid.message': 'The current statement risk is {currentRisk}, but you acknowledged {ackedRisk}. Please review and confirm again.',

// zh-CN
'sqlConfirmation.l2.title': '受限变更',
'sqlConfirmation.l3.title': '破坏性操作',
'sqlConfirmation.l2.body': '将修改 {objects} 中的数据。',
'sqlConfirmation.l3.body': '将永久影响 {objects}。',
'sqlConfirmation.l3.irreversible': '此操作无法撤销。',
'sqlConfirmation.affectedObjects': '受影响对象',
'sqlConfirmation.cancel': '取消',
'sqlConfirmation.execute': '执行',
'sqlConfirmation.executing': '执行中…',
'sqlConfirmation.invalid.title': '确认已失效',
'sqlConfirmation.invalid.message': '当前 SQL 风险为 {currentRisk}，但你确认的等级是 {ackedRisk}，请重新查看并确认。',
```

- [x] **Step 6.2: Write failing component tests**

Create `client/src/features/sql-confirmation/sql-confirmation-card.test.tsx`:

```tsx
import { describe, expect, it, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { I18nProvider } from '@/i18n/I18nProvider'
import { SqlConfirmationCard } from './sql-confirmation-card'

const renderCard = (props: Partial<React.ComponentProps<typeof SqlConfirmationCard>> = {}) =>
  render(
    <I18nProvider locale="en-US">
      <SqlConfirmationCard
        risk={{ level: 'L2', reason: 'update_with_where', affectedObjects: ['orders'] }}
        sqlPreview="UPDATE orders SET status = 'paid' WHERE id = 1"
        onCancel={() => {}}
        onExecute={() => {}}
        {...props}
      />
    </I18nProvider>,
  )

describe('SqlConfirmationCard', () => {
  it('renders L2 visuals with amber semantics and bounded-mutation copy', () => {
    renderCard()
    expect(screen.getByText('Bounded mutation')).toBeInTheDocument()
    expect(screen.getByText(/will modify data in orders/i)).toBeInTheDocument()
    expect(screen.queryByText(/cannot be undone/i)).not.toBeInTheDocument()
  })

  it('renders L3 visuals with red semantics and irreversibility warning', () => {
    renderCard({
      risk: { level: 'L3', reason: 'drop_table', affectedObjects: ['temp_log'] },
      sqlPreview: 'DROP TABLE temp_log',
    })
    expect(screen.getByText('Destructive operation')).toBeInTheDocument()
    expect(screen.getByText(/will permanently affect temp_log/i)).toBeInTheDocument()
    expect(screen.getByText('This action cannot be undone.')).toBeInTheDocument()
  })

  it('renders the SQL preview as a read-only code block', () => {
    renderCard()
    expect(screen.getByText(/UPDATE orders SET status/)).toBeInTheDocument()
  })

  it('lists each affected object', () => {
    renderCard({
      risk: { level: 'L2', reason: 'multi', affectedObjects: ['orders', 'order_items'] },
    })
    expect(screen.getByText('orders')).toBeInTheDocument()
    expect(screen.getByText('order_items')).toBeInTheDocument()
  })

  it('initial focus lands on Cancel to prevent accidental destructive activation', () => {
    renderCard({ risk: { level: 'L3', reason: 'drop_table', affectedObjects: ['t'] } })
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus()
  })

  it('calls onCancel and onExecute exactly once each on click', () => {
    const onCancel = vi.fn()
    const onExecute = vi.fn()
    renderCard({ onCancel, onExecute })
    fireEvent.click(screen.getByRole('button', { name: 'Execute' }))
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onExecute).toHaveBeenCalledTimes(1)
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('shows Executing… and disables both buttons while pending', () => {
    renderCard({ pending: true })
    expect(screen.getByRole('button', { name: 'Executing…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
  })
})
```

- [x] **Step 6.3: Run failing tests**

Run: `cd client && npx vitest run src/features/sql-confirmation/sql-confirmation-card.test.tsx`

Expected: all tests fail (file does not exist).

- [x] **Step 6.4: Implement the component**

Create `client/src/features/sql-confirmation/sql-confirmation-card.tsx`:

```tsx
import { useEffect, useRef } from 'react'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { cn } from '@/lib/utils'

export type SqlRisk = {
  level: 'L1' | 'L2' | 'L3'
  reason: string
  affectedObjects: string[]
}

export type SqlConfirmationCardProps = {
  risk: SqlRisk
  sqlPreview: string
  pending?: boolean
  onCancel: () => void
  onExecute: () => void
}

export function SqlConfirmationCard({
  risk,
  sqlPreview,
  pending = false,
  onCancel,
  onExecute,
}: SqlConfirmationCardProps) {
  const { t } = useI18n()
  const cancelRef = useRef<HTMLButtonElement | null>(null)

  useEffect(() => {
    cancelRef.current?.focus()
  }, [])

  const isL3 = risk.level === 'L3'
  const objectsLabel = risk.affectedObjects.length > 0
    ? risk.affectedObjects.join(', ')
    : '—'

  return (
    <div
      className={cn(
        'rounded-md border p-4 space-y-3',
        isL3
          ? 'border-status-danger/40 bg-status-dangerSurface text-status-danger'
          : 'border-accent-warn/40 bg-accent-warnSurface text-accent-warn',
      )}
      role="group"
      aria-label={isL3 ? t('sqlConfirmation.l3.title') : t('sqlConfirmation.l2.title')}
    >
      <div className="flex items-center gap-2 font-medium">
        <span
          aria-hidden
          className={cn(
            'inline-block h-2 w-2 rounded-full',
            isL3 ? 'bg-status-danger' : 'bg-accent-warn',
          )}
        />
        {isL3 ? t('sqlConfirmation.l3.title') : t('sqlConfirmation.l2.title')}
      </div>

      <pre className="rounded bg-bg-canvas p-2 font-mono text-sm overflow-x-auto whitespace-pre">
        {sqlPreview}
      </pre>

      <div className="text-sm">
        <div className="font-medium mb-1">{t('sqlConfirmation.affectedObjects')}</div>
        <ul className="font-mono text-xs space-y-0.5">
          {risk.affectedObjects.map((obj) => (
            <li key={obj}>{obj}</li>
          ))}
        </ul>
      </div>

      <div className="text-sm">
        {isL3
          ? t('sqlConfirmation.l3.body', { objects: objectsLabel })
          : t('sqlConfirmation.l2.body', { objects: objectsLabel })}
      </div>

      {isL3 && (
        <div className="text-sm font-semibold text-status-danger">
          {t('sqlConfirmation.l3.irreversible')}
        </div>
      )}

      <div className="flex justify-end gap-2 pt-1">
        <Button
          ref={cancelRef}
          variant="outline"
          size="sm"
          disabled={pending}
          onClick={onCancel}
        >
          {t('sqlConfirmation.cancel')}
        </Button>
        <Button
          size="sm"
          disabled={pending}
          variant={isL3 ? 'destructive' : 'default'}
          onClick={onExecute}
        >
          {pending ? t('sqlConfirmation.executing') : t('sqlConfirmation.execute')}
        </Button>
      </div>
    </div>
  )
}
```

If `bg-status-dangerSurface` / `bg-accent-warnSurface` Tailwind utilities are not yet defined, extend `tailwind.config.ts` (or the design-system CSS variable bridge file) so the semantic tokens declared in `client/DESIGN.md` resolve. Reuse the existing token mapping helper rather than introducing raw `red-100`/`amber-100` literals.

- [x] **Step 6.5: Run tests to confirm they pass**

Run: `cd client && npx vitest run src/features/sql-confirmation/sql-confirmation-card.test.tsx`

Expected: all seven tests pass.

- [x] **Step 6.6: Type-check**

Run: `cd client && npx tsc --noEmit`

Expected: zero errors.

- [x] **Step 6.7: Commit**

```bash
git add client/src/features/sql-confirmation/sql-confirmation-card.tsx \
        client/src/features/sql-confirmation/sql-confirmation-card.test.tsx \
        client/src/i18n/messages.ts \
        client/tailwind.config.ts
git commit -m "feat(sql-confirmation): add shared SqlConfirmationCard component"
```

---

## Task 7: API Service And Type Surface For Confirmation

**Files:**
- Modify: `client/src/services/api/sql.ts`
- Modify or create: `client/src/services/api/sql.types.ts` (if a separate types file exists in the codebase; otherwise inline)
- Test: `client/src/services/api/sql.test.ts`

- [x] **Step 7.1: Extend the request type and helper**

Locate `executeSql(...)` in `client/src/services/api/sql.ts` and update its request and response surface. The existing request shape gains two optional fields:

```ts
export type SqlExecuteRequest = {
  connectionId?: string
  sql: string
  source: 'user' | 'ai'
  sessionId?: string
  database?: string
  schema?: string
  confirmed?: boolean
  riskAck?: 'L1' | 'L2' | 'L3'
}

export type SqlConfirmationPayload = {
  level: 'L2' | 'L3'
  reason: string
  affectedObjects: string[]
  sqlPreview: string
}

export type SqlConfirmationInvalid = {
  reason: 'risk_ack_insufficient'
  ackedRisk: 'L1' | 'L2' | 'L3' | null
  currentRisk: 'L2' | 'L3'
  message: string
}

export type SqlExecuteResponse =
  | { status: 'executed'; resolvedContext: ResolvedDataContextDto; contextNotice?: string; results: SqlExecuteResultItem[] }
  | { status: 'requires_confirmation'; resolvedContext: ResolvedDataContextDto; contextNotice?: string; confirmation: SqlConfirmationPayload }
  | { status: 'confirmation_invalid'; resolvedContext: ResolvedDataContextDto; contextNotice?: string; invalidConfirmation: SqlConfirmationInvalid }

export async function executeSql(req: SqlExecuteRequest, signal?: AbortSignal): Promise<SqlExecuteResponse> {
  const response = await apiFetch('/api/sql/execute', {
    method: 'POST',
    body: JSON.stringify(req),
    signal,
  })
  if (!response.ok) {
    throw await toApiError(response)
  }
  return (await response.json()) as SqlExecuteResponse
}
```

Drop any `SqlRiskError` parsing branch (the backend no longer throws). If existing call sites import `SqlRiskError`, leave the export but mark it `@deprecated` and have it return nothing — Task 8 removes the consumers.

- [x] **Step 7.2: Write a focused service test**

Add `client/src/services/api/sql.test.ts`:

```ts
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { executeSql } from './sql'

describe('executeSql', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })
  afterEach(() => vi.unstubAllGlobals())

  it('parses an executed response', async () => {
    ;(fetch as any).mockResolvedValue(
      new Response(JSON.stringify({
        status: 'executed',
        resolvedContext: {},
        results: [],
      }), { status: 200, headers: { 'content-type': 'application/json' } }),
    )
    const out = await executeSql({ sql: 'SELECT 1', source: 'user' })
    expect(out.status).toBe('executed')
  })

  it('parses a requires_confirmation response', async () => {
    ;(fetch as any).mockResolvedValue(
      new Response(JSON.stringify({
        status: 'requires_confirmation',
        resolvedContext: {},
        confirmation: { level: 'L2', reason: 'update_with_where', affectedObjects: ['t'], sqlPreview: 'UPDATE t SET x=1 WHERE id=1' },
      }), { status: 200, headers: { 'content-type': 'application/json' } }),
    )
    const out = await executeSql({ sql: 'UPDATE t SET x=1 WHERE id=1', source: 'user' })
    expect(out.status).toBe('requires_confirmation')
    if (out.status === 'requires_confirmation') {
      expect(out.confirmation.level).toBe('L2')
    }
  })

  it('parses a confirmation_invalid response', async () => {
    ;(fetch as any).mockResolvedValue(
      new Response(JSON.stringify({
        status: 'confirmation_invalid',
        resolvedContext: {},
        invalidConfirmation: { reason: 'risk_ack_insufficient', ackedRisk: 'L1', currentRisk: 'L2', message: 'm' },
      }), { status: 200, headers: { 'content-type': 'application/json' } }),
    )
    const out = await executeSql({ sql: 'UPDATE t SET x=1 WHERE id=1', source: 'user', confirmed: true, riskAck: 'L1' })
    expect(out.status).toBe('confirmation_invalid')
  })
})
```

- [x] **Step 7.3: Run the test**

Run: `cd client && npx vitest run src/services/api/sql.test.ts`

Expected: all three pass.

- [x] **Step 7.4: Type-check**

Run: `cd client && npx tsc --noEmit`

Expected: zero errors. Some downstream call sites may now flag missing discriminator handling — leave those for Task 8.

- [x] **Step 7.5: Commit**

```bash
git add client/src/services/api/sql.ts client/src/services/api/sql.test.ts
git commit -m "feat(api): typed confirmation responses for executeSql"
```

---

## Task 8: Workbench State Machine, Store Slice, And `AlertDialog` Wiring

**Files:**
- Modify: `client/src/features/stage/hooks/use-sql-execute.ts`
- Modify: `client/src/features/stage/stores/sql-workbench-store.ts`
- Modify: `client/src/features/stage/utils/query-editor-actions.ts`
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Test: `client/src/features/stage/hooks/use-sql-execute.test.ts`
- Test: `client/src/features/stage/components/sql-workbench-tab.test.tsx` (or the existing equivalent)

- [x] **Step 8.1: Extend `use-sql-execute` state machine**

Replace the existing `SqlExecuteState` (or its equivalent) so the hook exposes:

```ts
export type SqlExecuteState =
  | { kind: 'idle' }
  | { kind: 'running' }
  | { kind: 'success'; results: SqlExecuteResultItem[] }
  | { kind: 'error'; message: string }
  | { kind: 'requires_confirmation'; confirmation: SqlConfirmationPayload; lastRequest: SqlExecuteRequest }
  | { kind: 'confirming' }
  | { kind: 'confirmation_invalid'; invalid: SqlConfirmationInvalid; lastRequest: SqlExecuteRequest }
```

The hook gains two methods:

```ts
function useSqlExecute() {
  // ...
  async function run(req: SqlExecuteRequest) {
    setState({ kind: 'running' })
    const res = await executeSql(req)
    handleResponse(req, res)
  }

  async function confirmAndRun(level: 'L2' | 'L3') {
    if (state.kind !== 'requires_confirmation' && state.kind !== 'confirmation_invalid') return
    const req = { ...state.lastRequest, confirmed: true, riskAck: level }
    setState({ kind: 'confirming' })
    const res = await executeSql(req)
    handleResponse(req, res)
  }

  function cancelConfirmation() {
    setState({ kind: 'idle' })
  }

  function handleResponse(req: SqlExecuteRequest, res: SqlExecuteResponse) {
    if (res.status === 'executed') {
      setState({ kind: 'success', results: res.results })
    } else if (res.status === 'requires_confirmation') {
      setState({ kind: 'requires_confirmation', confirmation: res.confirmation, lastRequest: req })
    } else {
      setState({ kind: 'confirmation_invalid', invalid: res.invalidConfirmation, lastRequest: req })
    }
  }

  return { state, run, confirmAndRun, cancelConfirmation }
}
```

Drop the previous `risk_blocked` state — the new state machine subsumes it.

- [x] **Step 8.2: Add hook tests**

Create or extend `use-sql-execute.test.ts`:

```ts
import { describe, expect, it, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useSqlExecute } from './use-sql-execute'
import * as sqlApi from '@/services/api/sql'

describe('useSqlExecute', () => {
  it('transitions running → success on executed', async () => {
    vi.spyOn(sqlApi, 'executeSql').mockResolvedValue({
      status: 'executed', resolvedContext: {} as any, results: [],
    })
    const { result } = renderHook(() => useSqlExecute())
    await act(async () => { await result.current.run({ sql: 'SELECT 1', source: 'user' }) })
    expect(result.current.state.kind).toBe('success')
  })

  it('transitions running → requires_confirmation on L2', async () => {
    vi.spyOn(sqlApi, 'executeSql').mockResolvedValue({
      status: 'requires_confirmation', resolvedContext: {} as any,
      confirmation: { level: 'L2', reason: 'update_with_where', affectedObjects: ['t'], sqlPreview: 'UPDATE t SET x=1 WHERE id=1' },
    })
    const { result } = renderHook(() => useSqlExecute())
    await act(async () => { await result.current.run({ sql: 'UPDATE t SET x=1 WHERE id=1', source: 'user' }) })
    expect(result.current.state.kind).toBe('requires_confirmation')
  })

  it('confirmAndRun re-executes with confirmed and riskAck', async () => {
    const spy = vi.spyOn(sqlApi, 'executeSql')
      .mockResolvedValueOnce({
        status: 'requires_confirmation', resolvedContext: {} as any,
        confirmation: { level: 'L2', reason: 'r', affectedObjects: [], sqlPreview: 'x' },
      })
      .mockResolvedValueOnce({
        status: 'executed', resolvedContext: {} as any, results: [],
      })
    const { result } = renderHook(() => useSqlExecute())
    await act(async () => { await result.current.run({ sql: 'UPDATE t SET x=1 WHERE id=1', source: 'user' }) })
    await act(async () => { await result.current.confirmAndRun('L2') })
    expect(spy).toHaveBeenLastCalledWith(expect.objectContaining({ confirmed: true, riskAck: 'L2' }), undefined)
    expect(result.current.state.kind).toBe('success')
  })

  it('confirmation_invalid transitions back to a refresh-able state', async () => {
    vi.spyOn(sqlApi, 'executeSql')
      .mockResolvedValueOnce({
        status: 'requires_confirmation', resolvedContext: {} as any,
        confirmation: { level: 'L2', reason: 'r', affectedObjects: [], sqlPreview: 'x' },
      })
      .mockResolvedValueOnce({
        status: 'confirmation_invalid', resolvedContext: {} as any,
        invalidConfirmation: { reason: 'risk_ack_insufficient', ackedRisk: 'L1', currentRisk: 'L2', message: 'm' },
      })
    const { result } = renderHook(() => useSqlExecute())
    await act(async () => { await result.current.run({ sql: 'UPDATE t SET x=1 WHERE id=1', source: 'user' }) })
    await act(async () => { await result.current.confirmAndRun('L1' as any) })
    expect(result.current.state.kind).toBe('confirmation_invalid')
  })

  it('cancelConfirmation returns to idle', async () => {
    vi.spyOn(sqlApi, 'executeSql').mockResolvedValue({
      status: 'requires_confirmation', resolvedContext: {} as any,
      confirmation: { level: 'L3', reason: 'drop_table', affectedObjects: ['t'], sqlPreview: 'DROP TABLE t' },
    })
    const { result } = renderHook(() => useSqlExecute())
    await act(async () => { await result.current.run({ sql: 'DROP TABLE t', source: 'user' }) })
    act(() => result.current.cancelConfirmation())
    expect(result.current.state.kind).toBe('idle')
  })
})
```

Run: `cd client && npx vitest run src/features/stage/hooks/use-sql-execute.test.ts`

Expected: all five tests pass after the hook refactor in Step 8.1.

- [x] **Step 8.3: Update `sql-workbench-store` and `query-editor-actions`**

Remove the `riskBlocked` slice from `sql-workbench-store.ts`. Replace with a `confirmation` slice:

```ts
type ConfirmationSlice =
  | { kind: 'none' }
  | { kind: 'pending'; confirmation: SqlConfirmationPayload; lastRequest: SqlExecuteRequest }
  | { kind: 'invalid'; invalid: SqlConfirmationInvalid; lastRequest: SqlExecuteRequest }

setConfirmation(tabId: string, slice: ConfirmationSlice)
clearConfirmation(tabId: string)
```

In `query-editor-actions.ts`, remove the `catch (SqlRiskError)` block — now a successful `executeSql` may return `requires_confirmation`, which the caller writes to the store via `setConfirmation`. The hook (Step 8.1) handles this; `query-editor-actions.ts` just bridges from the action invocation into the hook's `run` / `confirmAndRun`.

Look at all current callers of `setRiskBlocked` and `riskBlocked` and migrate them. `git grep risk_blocked client/src` to enumerate.

- [x] **Step 8.4: Wire the AlertDialog into `sql-workbench-tab`**

In `sql-workbench-tab.tsx`, add the dialog rendering:

```tsx
import { AlertDialog, AlertDialogContent, AlertDialogHeader, AlertDialogTitle } from '@/components/ui/alert-dialog'
import { SqlConfirmationCard } from '@/features/sql-confirmation/sql-confirmation-card'

// inside the component:
const isPending = state.kind === 'requires_confirmation' || state.kind === 'confirmation_invalid'
const confirmationCard = state.kind === 'requires_confirmation' ? state.confirmation : null
const invalidPayload = state.kind === 'confirmation_invalid' ? state.invalid : null

return (
  <>
    {/* existing tab body */}

    <AlertDialog open={isPending}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>
            {invalidPayload
              ? t('sqlConfirmation.invalid.title')
              : confirmationCard?.level === 'L3'
                ? t('sqlConfirmation.l3.title')
                : t('sqlConfirmation.l2.title')}
          </AlertDialogTitle>
        </AlertDialogHeader>
        {confirmationCard && (
          <SqlConfirmationCard
            risk={{
              level: confirmationCard.level,
              reason: confirmationCard.reason,
              affectedObjects: confirmationCard.affectedObjects,
            }}
            sqlPreview={confirmationCard.sqlPreview}
            pending={state.kind === 'confirming'}
            onCancel={() => cancelConfirmation()}
            onExecute={() => confirmAndRun(confirmationCard.level)}
          />
        )}
        {invalidPayload && (
          <div className="space-y-3">
            <p className="text-sm">
              {t('sqlConfirmation.invalid.message', {
                currentRisk: invalidPayload.currentRisk,
                ackedRisk: invalidPayload.ackedRisk ?? '—',
              })}
            </p>
            <div className="flex justify-end gap-2">
              <Button variant="outline" size="sm" onClick={cancelConfirmation}>
                {t('sqlConfirmation.cancel')}
              </Button>
              <Button size="sm" onClick={() => confirmAndRun(invalidPayload.currentRisk)}>
                {t('sqlConfirmation.execute')}
              </Button>
            </div>
          </div>
        )}
      </AlertDialogContent>
    </AlertDialog>
  </>
)
```

- [x] **Step 8.5: Add a Workbench tab integration test**

Extend the existing test that covers `sql-workbench-tab.tsx`:

```ts
it('opens AlertDialog and executes after Confirm for L2 SQL', async () => {
  vi.spyOn(sqlApi, 'executeSql')
    .mockResolvedValueOnce({
      status: 'requires_confirmation', resolvedContext: {} as any,
      confirmation: { level: 'L2', reason: 'update_with_where', affectedObjects: ['t'], sqlPreview: 'UPDATE t SET x = 1 WHERE id = 1' },
    })
    .mockResolvedValueOnce({
      status: 'executed', resolvedContext: {} as any, results: [],
    })

  renderTab({ initialSql: 'UPDATE t SET x = 1 WHERE id = 1' })

  await user.click(screen.getByRole('button', { name: /run/i }))
  expect(await screen.findByText('Bounded mutation')).toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: 'Execute' }))
  expect(sqlApi.executeSql).toHaveBeenLastCalledWith(
    expect.objectContaining({ confirmed: true, riskAck: 'L2' }),
    expect.anything(),
  )
})

it('keeps the run button enabled and dialog closed for L1 SQL', async () => {
  vi.spyOn(sqlApi, 'executeSql').mockResolvedValue({
    status: 'executed', resolvedContext: {} as any, results: [],
  })
  renderTab({ initialSql: 'SELECT 1' })
  await user.click(screen.getByRole('button', { name: /run/i }))
  expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
})
```

If a `renderTab` helper does not yet exist, mirror the helper used by the closest existing Workbench component test (likely `sql-workbench-tab.test.tsx` already in the repo).

- [x] **Step 8.6: Run tab and hook tests together**

Run: `cd client && npx vitest run src/features/stage`

Expected: all Stage tests pass, including the new ones.

- [x] **Step 8.7: Type-check**

Run: `cd client && npx tsc --noEmit`

Expected: zero errors.

- [x] **Step 8.8: Commit**

```bash
git add client/src/features/stage/hooks/use-sql-execute.ts \
        client/src/features/stage/hooks/use-sql-execute.test.ts \
        client/src/features/stage/stores/sql-workbench-store.ts \
        client/src/features/stage/utils/query-editor-actions.ts \
        client/src/features/stage/components/sql-workbench-tab.tsx \
        client/src/features/stage/components/sql-workbench-tab.test.tsx
git commit -m "feat(workbench): AlertDialog confirmation flow for L2/L3 SQL"
```

---

## Task 9: Chat Renderer For `requires_confirmation` Tool Output

**Files:**
- Modify: `client/src/features/chat/components/tools/renderers/execute-sql.tsx`
- Modify: `client/src/features/chat/components/tools/renderers/preview-sql.tsx` (re-purposed as the L2/L3 confirmation card host; or delete if `execute-sql.tsx` directly handles the new shape — choose based on Step 9.1)
- Test: `client/src/features/chat/components/tools/renderers/execute-sql.test.tsx`

- [x] **Step 9.1: Decide host: `execute-sql.tsx` discriminates on the tool result shape**

The `datatalk_execute_sql` tool now produces two output shapes:

1. The legacy artifact-shaped success output (`artifactId`, `columns`, `preview`, `rowCount`).
2. The new `{ status: 'requires_confirmation', risk, sqlPreview }` output emitted when L2/L3 is detected without trusted confirmation.
3. The new `{ status: 'confirmation_invalid', reason, ackedRisk, currentRisk }` output.

Since the same tool name produces all three shapes, do the discrimination inside `execute-sql.tsx` rather than registering separate renderers. Delete the unused `preview-sql.tsx` file (it is not registered to any tool — confirmed by `git grep ToolRegistry.register client/src` returning only `datatalk_execute_sql`, `datatalk_read_schema`, `datatalk_render_chart`, `datatalk_layout_erd`).

- [x] **Step 9.2: Modify `execute-sql.tsx` to render `<SqlConfirmationCard>` for the new statuses**

```tsx
import { useChannel } from '@/services/channel/use-channel'
import { SqlConfirmationCard } from '@/features/sql-confirmation/sql-confirmation-card'
import { useState } from 'react'
import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { resolveRisk } from '../../helpers/risk'

export function ExecuteSql(props: ToolRendererProps) {
  const { part, descriptor } = props
  const output = part.state.output as Record<string, unknown> | undefined
  const status = output?.status as string | undefined
  const callID = part.callID ?? part.id
  const { client } = useChannel()
  const [decided, setDecided] = useState<'confirmed' | 'cancelled' | null>(null)

  if (status === 'requires_confirmation') {
    const risk = output!.risk as { level: 'L2' | 'L3'; reason: string; affectedObjects: string[] }
    const sqlPreview = String(output!.sqlPreview ?? '')
    return (
      <BasicTool icon="code" risk={risk.level} status={part.state.status}
        trigger={{ title: risk.level === 'L3' ? 'Confirm destructive SQL' : 'Confirm SQL' }}
        forceOpen
      >
        {decided ? (
          <div className="text-xs text-muted-foreground">
            {decided === 'confirmed' ? 'Sent confirmation' : 'Cancelled'}
          </div>
        ) : (
          <SqlConfirmationCard
            risk={risk}
            sqlPreview={sqlPreview}
            onCancel={() => {
              setDecided('cancelled')
              client?.actionResult(callID, true, { confirmed: false })
            }}
            onExecute={() => {
              setDecided('confirmed')
              client?.actionResult(callID, true, { confirmed: true, riskAck: risk.level })
            }}
          />
        )}
      </BasicTool>
    )
  }

  if (status === 'confirmation_invalid') {
    const ackedRisk = output!.ackedRisk as string | null
    const currentRisk = output!.currentRisk as string
    return (
      <BasicTool icon="warning" risk={currentRisk as 'L2' | 'L3'} status={part.state.status}
        trigger={{ title: 'Confirmation no longer valid' }}
        forceOpen
      >
        <div className="text-sm">
          Acknowledged {ackedRisk ?? '—'}, current is {currentRisk}. Re-run from the workbench or ask the AI to call again.
        </div>
      </BasicTool>
    )
  }

  // Existing success rendering (artifactId, columns, preview, rowCount) unchanged.
  return renderExecutedSqlResult(props)
}
```

`renderExecutedSqlResult` is the existing rendering body — extract it from the current file body so the discriminator stays readable.

- [x] **Step 9.3: Add a renderer test**

Create `execute-sql.test.tsx`:

```tsx
import { describe, expect, it, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { I18nProvider } from '@/i18n/I18nProvider'
import { ExecuteSql } from './execute-sql'
import { ChannelContext } from '@/services/channel/channel-context'

const renderRenderer = (output: any, actionResult = vi.fn()) =>
  render(
    <I18nProvider locale="en-US">
      <ChannelContext.Provider value={{ client: { actionResult } } as any}>
        <ExecuteSql
          part={{
            id: 'p1', callID: 'call-1',
            state: { status: 'completed', input: { sql: 'UPDATE t SET x=1 WHERE id=1' }, output },
          } as any}
          descriptor={{} as any}
        />
      </ChannelContext.Provider>
    </I18nProvider>,
  )

describe('ExecuteSql renderer', () => {
  it('renders confirmation card for requires_confirmation', () => {
    renderRenderer({
      status: 'requires_confirmation',
      risk: { level: 'L2', reason: 'update_with_where', affectedObjects: ['t'] },
      sqlPreview: 'UPDATE t SET x=1 WHERE id=1',
    })
    expect(screen.getByText('Bounded mutation')).toBeInTheDocument()
  })

  it('sends actionResult with confirmed:true and riskAck on Execute', () => {
    const actionResult = vi.fn()
    renderRenderer({
      status: 'requires_confirmation',
      risk: { level: 'L3', reason: 'drop_table', affectedObjects: ['t'] },
      sqlPreview: 'DROP TABLE t',
    }, actionResult)
    fireEvent.click(screen.getByRole('button', { name: 'Execute' }))
    expect(actionResult).toHaveBeenCalledWith('call-1', true, { confirmed: true, riskAck: 'L3' })
  })

  it('sends confirmed:false on Cancel', () => {
    const actionResult = vi.fn()
    renderRenderer({
      status: 'requires_confirmation',
      risk: { level: 'L2', reason: 'r', affectedObjects: [] },
      sqlPreview: 'x',
    }, actionResult)
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(actionResult).toHaveBeenCalledWith('call-1', true, { confirmed: false })
  })

  it('renders the legacy artifact view for executed output', () => {
    renderRenderer({
      artifactId: 'a-1', version: 1, columns: ['id'], preview: [{ id: 1 }], rowCount: 1, durationMs: 5,
    })
    expect(screen.getByText(/id/)).toBeInTheDocument()
  })
})
```

- [x] **Step 9.4: Run renderer tests**

Run: `cd client && npx vitest run src/features/chat/components/tools/renderers`

Expected: all four pass.

- [x] **Step 9.5: Delete `preview-sql.tsx` and confirm no broken imports**

```bash
git grep -n "from.*preview-sql\|PreviewSql" client/src
```

Expected: zero matches. Then:

```bash
git rm client/src/features/chat/components/tools/renderers/preview-sql.tsx
```

- [x] **Step 9.6: Type-check**

Run: `cd client && npx tsc --noEmit`

Expected: zero errors.

- [x] **Step 9.7: Commit**

```bash
git add client/src/features/chat/components/tools/renderers/execute-sql.tsx \
        client/src/features/chat/components/tools/renderers/execute-sql.test.tsx
git commit -m "feat(chat): execute-sql renderer surfaces confirmation card for L2/L3"
```

---

## Task 10: Full Verification And Document Housekeeping

**Files:**
- Modify: `docs/exec-plans/2026-04-25-guarded-ddl-dml-execution-plan.md` (this file)
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/design-docs/index.md`

- [x] **Step 10.1: Backend full verify**

Run: `cd server && mvn clean verify`

Expected: BUILD SUCCESS. Investigate and fix any test that depended on the removed `SqlRiskBlockedException` path.

- [x] **Step 10.2: Frontend type and test gate**

Run:

```bash
cd client && npx tsc --noEmit
cd client && npx vitest run src/features/sql-confirmation src/features/stage src/features/chat src/services/api
```

Expected: zero type errors; all targeted tests pass.

- [x] **Step 10.3: Manual smoke checklist (record only — human runs at review time)**

Record these in the PR description / handoff so a human runs them before sign-off:

1. Workbench runs `SELECT 1` — no dialog, results render normally.
2. Workbench runs `UPDATE t SET x = 1 WHERE id = 1` — amber dialog opens; Cancel returns to idle without execution; Execute completes and results render.
3. Workbench runs `DROP TABLE t` — red dialog opens with "This action cannot be undone."; Cancel returns to idle; Execute completes.
4. Workbench `prefers-reduced-motion: reduce` — dialog entry has no entrance animation.
5. Workbench dark theme — dialog surface tokens visibly carry red / amber semantics.
6. Chat: ask AI to `UPDATE` something — confirmation card appears in the message stream; clicking Execute completes the tool call with the result; clicking Cancel completes the tool call as cancelled (AI sees a cancelled outcome part).
7. Chat: ask AI to `DROP` a table — same as above with red treatment.
8. Switch tabs while a confirmation dialog is open in Workbench — dialog stays bound to its tab; switching back reveals it.

- [x] **Step 10.4: Mark this plan's checkboxes complete and add a "Verification" note**

In this plan file, change `- [ ]` to `- [x]` for every step that was actually completed; for any step that was deferred or skipped, leave the checkbox empty and append a one-line "Status note: deferred — {reason}".

- [x] **Step 10.5: Move plan from Active to Completed in `docs/exec-plans/index.md`**

Edit `docs/exec-plans/index.md`:

- Remove the row from the Active table.
- Insert a row in the Completed table with the completion date and a 1-line summary describing what landed and what the runtime gates were.

- [x] **Step 10.6: Bump design doc status to `shipped` in `docs/design-docs/index.md`**

Change the entry for `guarded-ddl-dml-execution-design` from `approved` to `shipped`.

- [x] **Step 10.7: Final commit**

```bash
git add docs/exec-plans/2026-04-25-guarded-ddl-dml-execution-plan.md \
        docs/exec-plans/index.md \
        docs/design-docs/index.md
git commit -m "docs(roadmap): mark guarded DDL/DML execution shipped"
```

---

## Verification Gates Summary

- `cd server && mvn clean verify` (Task 10.1)
- `cd client && npx tsc --noEmit` (Task 10.2)
- `cd client && npx vitest run src/features/sql-confirmation src/features/stage src/features/chat src/services/api` (Task 10.2)
- Manual smoke checklist in Task 10.3

## Exit Criteria

- `CalciteSqlRiskAnalyzer` reflects the new classification, with `DELETE WITH WHERE` = L2 and `affectedObjects` populated.
- `/api/sql/execute` returns `executed` / `requires_confirmation` / `confirmation_invalid` per the state machine.
- `ExecuteSqlAction` no longer relies on `assertSelectOnly` and routes L2 / L3 through the confirmation flow.
- Workbench renders an `AlertDialog` containing `<SqlConfirmationCard>` for L2 / L3 SQL; Cancel and Execute both behave per the spec.
- Chat `execute-sql` renderer surfaces `<SqlConfirmationCard>` inline for L2 / L3 tool outputs; user input flows back via `client.actionResult`.
- All listed tests pass and `mvn clean verify` is green.
- `docs/exec-plans/index.md` lists this plan as Completed; `docs/design-docs/index.md` lists the design as `shipped`.
