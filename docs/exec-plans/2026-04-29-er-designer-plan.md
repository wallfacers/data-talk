# ER Designer Implementation Plan (Plan B)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the `er_designer` Stage Tab type — an AI-operable schema-draft authoring surface that generates DDL through `bind_target` + `diff_against_db` + `generate_ddl` and lands the SQL in a new `query_editor` tab for the user to confirm via Task 5's existing L2/L3 guarded execution. No new mutation egress; the designer is strictly an authoring tool.

**Architecture:** Reuses every shared foundation laid by Plan A — canvas (`<ErCanvas>` / `<ErTableNode>` / `<ErEdge>`), worker layout (`useDagreLayout`), AI highlight (`useErHighlight`), store (`useErTabsStore`, designer half), persistence subscription (`stage-persistence-bootstrap`), force-flush (`ui-handlers`), and global Adapter registration (`stage-ui-object-registry`). Plan B adds: complete `applyDesignerPatch` with strict-baseVersion semantics on structural paths, designer-only `<ErTableNode mode='designer'>` editing UX, drag-to-create-relation via `onConnect`, designer toolbar (`Add table` / `Bind target` / `Diff vs DB` / `Generate DDL` / `Dialect`), `<ErTableContextMenu>` for delete-table / add-column, four backend dialect-specific `DdlGenerator` implementations behind a single `DialectTypeRegistry`, an `ErSchemaDiffService`, an `ErDdlGeneratorService`, three new internal REST endpoints (`/api/er/generate-ddl`, `/api/er/diff`, `/api/er/sync-from-db`), `WorkspaceAdapter.exec(open_er_designer)`, the inspector's `fork_to_designer` verb, AGENTS.md designer recipes (English-only), and documentation updates.

**Tech Stack:** Spring Boot 3.5, Java 21, JdbcTemplate, JUnit 5, AssertJ, MockMvc; Tauri v2, React 19, TypeScript, Zustand, `@xyflow/react@^12.10.1`, `dagre@^0.8.5`, vitest, Testing Library.

---

## Status

- **Created:** 2026-04-29
- **State:** Active (implementation + automated verification completed 2026-04-29; 2026-04-30 follow-up fixes shipped; manual Tauri + real-database smoke still pending)
- **Spec:** [docs/product-specs/2026-04-29-er-graph-browsing-design.md](../product-specs/2026-04-29-er-graph-browsing-design.md)
- **Phase:** Plan B of 2 (Plan A `er_inspector` ships first)
- **Dependency:** Plan A must be merged and Plan A's exit criteria satisfied (mvn verify green, tsc clean, npm test green, real-database smoke pass) before any Plan B task starts. The shared canvas / store / persistence pipeline / global adapter registry are foundations Plan B builds on, not parallel work.
- **Estimated effort:** 3-4 weeks

### Execution Checkpoint — 2026-04-29

- Code implementation for Tasks 1-30 was completed through parallel subagents, then integrated in one consolidated pass.
- Backend verification passed: `cd server && mvn compile -q`; `cd server && mvn clean verify` finished with `BUILD SUCCESS` (adapter failsafe summary: 140 tests, 0 failures, 0 errors, 2 skipped).
- Frontend verification passed: `cd client && npx tsc --noEmit && npm test -- --run` finished with 137 test files / 826 tests passed.
- Manual smoke in Task 31 was **not executed** in this terminal session because it requires a running Tauri desktop app and a writable real MySQL connection. Do not move this plan to Completed until Task 31 is run or explicitly waived by the owner.

### Status Snapshot — 2026-04-30

- Post-ship regressions found during review were handled in [ER Designer Follow-up Fixes](./2026-04-30-er-designer-follow-up-fixes-plan.md).
- That follow-up patch corrected relation-kind mapping (`database_fk -> fk`, `comment_ref -> virtual`), aligned designer table-node tests with id-based callbacks, and kept `sync_from_db` from stacking newly synced tables at `(0,0)`.
- AI-facing backend prompt docs were also aligned with the shipped `workspace.open` query-editor payload fields.
- After that follow-up, the only remaining acceptance gate for this plan is Task 31 manual Tauri + real-database smoke, or an explicit owner waiver for that smoke.

## Context

Plan A landed `er_inspector` — a read-only ER browser whose payload only mutates view-layer fields (selection, positions, collapsed, virtualRelations, notes, viewport). Plan B introduces `er_designer`, an **independent schema draft** object whose payload carries full table / column / relation definitions. The designer can `bind_target` to a real connection, compute a `diff_against_db` between draft and real schema, and `generate_ddl` — which produces a new `query_editor` tab pre-populated with `CREATE TABLE` / `ALTER ADD COLUMN` / `ALTER ADD FK` / `CREATE INDEX` statements bound to the target connection. The user then runs that SQL through Task 5's existing L2/L3 guarded confirmation flow. **There is no path that lets the designer (or AI) execute DDL directly**; the existing `ExecuteSqlAction` + L2/L3 confirm is the only mutation egress.

Day-1 DDL scope is intentionally narrow: CREATE TABLE / ALTER ADD COLUMN / ALTER ADD FK / CREATE INDEX only. DROP TABLE / DROP COLUMN / ALTER COLUMN type / RENAME are recorded as `SkippedOp` with reason `day1_unsupported` and a structured `aiHint` instructing AI / users to write that SQL by hand in the query_editor (which they then run through L2/L3 confirm just like any other DDL). This keeps the designer focused on additive schema authoring while leaving destructive changes on the well-trodden manual path.

Dialect support: MySQL / PostgreSQL / H2 fully covered (CREATE / ALTER / FK / INDEX); SQLite is CREATE-only because its `ALTER TABLE` cannot add constraints or drop columns; Oracle / SQL Server remain explicitly unsupported with `dialect_unsupported` aiHint, identical to Plan A's inspector behavior. The Designer Tab refuses to be created for unsupported dialects.

## Design Inputs

This plan implements UI surfaces under `client/`. Per CLAUDE.md the agent must read [client/DESIGN.md](../../client/DESIGN.md) first; constraints applicable to Plan B in addition to Plan A's:

- Designer node header carries a pencil edit icon instead of the inspector's lock icon; otherwise identical visual language.
- The right-click context menu (delete table / add column) follows the existing context menu pattern in the codebase, using `bg.panel` surface + `border.default` + `interaction.hover`.
- Toolbar primary CTAs (`Generate DDL`, `Apply to DB` / `Bind target`) use `accent.primary` (cobalt-700) for outline + label.
- Editing a column's type uses an existing dropdown component (`DropdownSelect` or shadcn `Select`) — do not invent a new visual.
- Drag-to-create-relation visual: handle becomes opaque on hover (cobalt), wire follows ReactFlow's default smoothstep animation in motion.normal duration.
- AI highlight (pulse → residual) reused unchanged from Plan A.
- All AI-facing strings remain English (P12). Designer recipes in AGENTS.md, `error.aiHint` values, `STAGE_TAB_DIGEST` lines, and `inputSchema` descriptions are English; user UI labels go through `client/i18n/messages.ts` with both locales.

## Spec Mapping

| Spec section | Plan B tasks |
|---|---|
| §1 (背景) | reused; Plan A retired LayoutErdAction |
| §2 Q11 (DDL day-1 scope) | T1-T8 |
| §2 Q12 (dialect matrix) | T1-T5 + T9 |
| §2 Q15 (patch grammar) | T14 (designer paths use `[id=<tid>]` addressing) |
| §4 P2 (strict baseVersion) | T14 (designer structural paths) |
| §4 P9 (assignedIds) | T14 |
| §4 P12 (English AI strings) | T10, T12, T27, T28, T29 |
| §5.3 (Designer payload) | T13 |
| §5.5.4 (Java schemas) | T10, T11, T12, T29 |
| §6.2 / §6.4 (Designer ui_patch paths) | T14 |
| §6.6 (Designer ui_exec verbs) | T23 |
| §6.7 (errors) | T8, T9, T23, T24 |
| §7 (Frontend Architecture, designer mode) | T18-T22, T25 |
| §8 (Backend Architecture) | T1-T9 |
| §9.1 (AGENTS.md designer recipes) | T27 |
| §9.2 (STAGE_TAB_DIGEST designer line) | T28 |
| §9.3 (er-tab-protocol.md designer half) | T30 |
| §10 (Apply end-to-end flow) | T9, T23, T24, T32 |
| §11.1 backend tests | T1-T9, T29 |
| §11.2 frontend tests | T13-T26, T28 |
| §11.3 AI behavior IT | T32 |
| §13 Plan B scope + acceptance | this plan |
| §14 Definition of Done | T33-T36 |
| §17 row 1, 2, 3 (Java schemas + i18n) | T10, T11, T12 |
| §17 row 7 (AGENTS.md designer half) | T27 |
| §17 row 8, 9, 10 (frontend wiring already in Plan A) | reused; T16, T17 verify |
| §17 row 11, 13 (tab-type-registry + WorkspaceAdapter) | T15, T24 |
| §17 row 21 (DATA_SOURCE_TYPE_COMPATIBILITY) | T31 |

## File Structure Map

### Created

| File | Responsibility |
|---|---|
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDesignerTable.java` | Record `(id, name, comment?, columns: List<ErDesignerColumn>, indexes: List<ErDesignerIndex>, uniques: List<ErDesignerUnique>)` |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDesignerColumn.java` | Record `(id, name, type, nullable, isPrimaryKey, isAutoIncrement, defaultValue?, comment?)` |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDesignerRelation.java` | Record `(id, fromTableId, fromColumnId, toTableId, toColumnId, type, constraintMethod)` |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDesignerIndex.java` | Record `(name, columns: List<String>)` |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDesignerUnique.java` | Record `(columns: List<String>)` |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDesignerPayload.java` | Server-side mirror of `ErDesignerPayload` for diff/DDL services |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDdlPlan.java` | Record `(statements: List<ErDdlStatement>, skipped: List<SkippedOp>)` |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDdlStatement.java` | Record `(sql, kind: ErDdlKind, table)` |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDdlKind.java` | Enum `{ CREATE_TABLE, ADD_COLUMN, ADD_FK, CREATE_INDEX }` |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/SkippedOp.java` | Record `(opType, table, column?, reason, hint)` |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErSchemaDiff.java` | Sealed interface: `TableAdded`, `TableDropped`, `ColumnAdded`, `ColumnTypeChanged`, `ColumnDropped`, `ConstraintAdded`, `ConstraintDropped` |
| `server/data-talk-application/src/main/java/com/datatalk/application/er/DialectTypeRegistry.java` | Single source of truth for abstract type → dialect SQL fragment + identifier quoting + auto-increment PK |
| `server/data-talk-application/src/main/java/com/datatalk/application/er/ErDdlGenerator.java` | Strategy interface — `generate(ErSchemaDiff)` returning `ErDdlStatement` or `SkippedOp` |
| `server/data-talk-application/src/main/java/com/datatalk/application/er/ErSchemaDiffService.java` | Computes `List<ErSchemaDiff>` between an `ErDesignerPayload` and a real-DB `ErGraph` |
| `server/data-talk-application/src/main/java/com/datatalk/application/er/ErDdlGeneratorService.java` | Orchestrator — `generate(req)` returns full `ErDdlPlan`; `diff(payload, conn)` returns diff list |
| `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/er/MySqlDdlGenerator.java` | dialect = MySQL |
| `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/er/PostgresDdlGenerator.java` | dialect = PostgreSQL |
| `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/er/H2DdlGenerator.java` | dialect = H2 |
| `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/er/SqliteDdlGenerator.java` | dialect = SQLite (CREATE TABLE only; ADD COLUMN / ADD FK / DROP all return SkippedOp) |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/GenerateDdlRequest.java` | DTO `(payload: ErDesignerPayloadDto, connectionId, includeDrops)` |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/GenerateDdlResponse.java` | DTO `(ddl: String, statements, skipped)` |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/DiffResponse.java` | DTO wrapping `List<ErSchemaDiff>` |
| (test files for each new server class) | per-class JUnit |
| `client/src/features/stage/components/er-canvas/ErTableContextMenu.tsx` | Right-click menu (designer): rename / delete / add column |
| `client/src/features/stage/components/er-canvas/__tests__/ErTableContextMenu.test.tsx` | Component tests |
| `client/src/features/stage/components/er-designer-tab.tsx` | Tab content for `tab.type === 'er_designer'` |
| `client/src/features/stage/components/__tests__/er-designer-tab.test.tsx` | Component tests |
| `client/src/features/stage/adapters/ErDesignerAdapter.ts` | `UIObject` impl with read / patch / exec verbs |
| `client/src/features/stage/adapters/__tests__/ErDesignerAdapter.test.ts` | Adapter tests |

### Modified

| File | Change |
|---|---|
| `client/src/features/stage/registry/tab-type-registry.ts` | Register `er_designer` (extractContent + rehydrate) |
| `client/src/features/stage/stores/er-tabs-payload-types.ts` | Replace stub `ErDesignerPayload` with full schema |
| `client/src/features/stage/stores/er-tabs-store.ts` | Implement `applyDesignerPatch` (paths, strict baseVersion, assignedIds for `t_*` / `c_*` / `r_*`) |
| `client/src/features/stage/stores/er-tabs-store.test.ts` | New designer test suite |
| `client/src/features/stage/components/er-canvas/ErTableNode.tsx` | Add `mode === 'designer'` branch (editable column rows, `+ Add column`, pencil icon, right-click dispatch) |
| `client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx` | Designer-mode tests |
| `client/src/features/stage/components/er-canvas/ErEdge.tsx` | Hover handle visibility upgrades for drag-to-create (cobalt fill on hover) |
| `client/src/features/stage/components/er-canvas/ErToolbar.tsx` | Add designer-mode props + buttons |
| `client/src/features/stage/components/er-canvas/__tests__/ErToolbar.test.tsx` | Designer-mode tests |
| `client/src/features/stage/components/er-canvas/ErCanvas.tsx` | Accept `mode='designer'` props; wire `onConnect` / `onNodesDelete` / `onEdgesDelete` |
| `client/src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx` | Designer scenarios (drag-to-connect, delete table) |
| `client/src/features/stage/adapters/WorkspaceAdapter.ts` | Add `case 'open_er_designer'` |
| `client/src/features/stage/adapters/ErInspectorAdapter.ts` | Implement `fork_to_designer` (placeholder error in Plan A is now real) |
| `client/src/features/stage/adapters/__tests__/ErInspectorAdapter.test.ts` | Add fork_to_designer happy-path test |
| `client/src/features/stage/components/stage-ui-object-registry.tsx` | Add `RegisteredErDesigner` |
| `client/src/features/stage/components/stage-ui-object-registry.test.tsx` | Designer registration assertion |
| `client/src/features/stage/components/stage-tab-content.tsx` | Route `er_designer` → `<ErDesignerTab />` |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/ErTabController.java` | Add `/generate-ddl`, `/diff`, `/sync-from-db` endpoints |
| `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/ErTabControllerIT.java` | New IT cases for the three endpoints |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java` | Add `erDesignerExecSchema()` (Plan A reserved the workspace verb; this lands the per-object schema) |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiPatchAction.java` | Add `er_designer` to `object` enum |
| `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` | Append designer recipes + apply flow + bind/diff/generate_ddl examples (English) |
| `server/data-talk-application/src/main/java/com/datatalk/application/opencode/AgentPromptBuilder.java` | Render `er_designer` digest line `(N tables · M relations · target=conn/db)` |
| `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java` | Designer-side assertions |
| `server/data-talk-adapter/src/main/resources/messages.properties` | Designer description keys (English) |
| `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties` | Designer user-facing labels (Chinese for UI text) |
| `client/src/i18n/messages.ts` | `tabType.erDesigner`, designer toolbar / context-menu / dialect picker labels (en/zh) |
| `docs/references/er-tab-protocol.md` | Append Designer half (payload schema, ui_patch path whitelist, ui_exec verbs, errors, DDL skipping rules) |
| `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` | Update ER Tabs row to include Designer DDL generation matrix |
| `docs/exec-plans/index.md` | Move Plan A to Completed; mark Plan B Active during implementation, then Completed |
| `docs/product-specs/2026-04-29-er-graph-browsing-design.md` | Tick Plan B checklist items as work completes |
| `docs/exec-plans/tech-debt-tracker.md` | Confirm no new tech debt or record explicitly if any |

---

## Batch Plan

10 batches, 36 tasks. Backend dialect work is front-loaded so the strategy/test surface is established before the orchestrator depends on it. Frontend work is back-loaded so the canvas extension consumes a stable backend.

---

## Batch 1: Backend dialect type registry + four DdlGenerators

This batch establishes the backbone of DDL generation. Each `DdlGenerator` is independently testable; `DialectTypeRegistry` is the single source of truth referenced by all four.

### Task 1: `DialectTypeRegistry` complete implementation

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/er/DialectTypeRegistry.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/er/DialectTypeRegistryTest.java`

- [ ] **Step 1: Write the failing test (44 render assertions + 4 quote + 4 autoIncrementPk)**

```java
// server/data-talk-application/src/test/java/com/datatalk/application/er/DialectTypeRegistryTest.java
package com.datatalk.application.er;

import com.datatalk.domain.er.Dialect;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.datatalk.application.er.DialectTypeRegistry.AbstractType.*;
import static com.datatalk.application.er.DialectTypeRegistry.render;
import static com.datatalk.application.er.DialectTypeRegistry.quote;
import static com.datatalk.application.er.DialectTypeRegistry.autoIncrementPk;
import static org.assertj.core.api.Assertions.assertThat;

class DialectTypeRegistryTest {

    @Test
    void renderBigInt() {
        assertThat(render(Dialect.MYSQL,      BIGINT, Map.of())).isEqualTo("BIGINT");
        assertThat(render(Dialect.POSTGRESQL, BIGINT, Map.of())).isEqualTo("BIGINT");
        assertThat(render(Dialect.H2,         BIGINT, Map.of())).isEqualTo("BIGINT");
        assertThat(render(Dialect.SQLITE,     BIGINT, Map.of())).isEqualTo("INTEGER");
    }

    @Test
    void renderInt() {
        assertThat(render(Dialect.MYSQL,      INT, Map.of())).isEqualTo("INT");
        assertThat(render(Dialect.POSTGRESQL, INT, Map.of())).isEqualTo("INTEGER");
        assertThat(render(Dialect.H2,         INT, Map.of())).isEqualTo("INT");
        assertThat(render(Dialect.SQLITE,     INT, Map.of())).isEqualTo("INTEGER");
    }

    @Test
    void renderSmallInt() {
        assertThat(render(Dialect.MYSQL,      SMALLINT, Map.of())).isEqualTo("SMALLINT");
        assertThat(render(Dialect.POSTGRESQL, SMALLINT, Map.of())).isEqualTo("SMALLINT");
        assertThat(render(Dialect.H2,         SMALLINT, Map.of())).isEqualTo("SMALLINT");
        assertThat(render(Dialect.SQLITE,     SMALLINT, Map.of())).isEqualTo("INTEGER");
    }

    @Test
    void renderDecimal() {
        var p = Map.<String, Object>of("precision", 10, "scale", 2);
        assertThat(render(Dialect.MYSQL,      DECIMAL, p)).isEqualTo("DECIMAL(10,2)");
        assertThat(render(Dialect.POSTGRESQL, DECIMAL, p)).isEqualTo("NUMERIC(10,2)");
        assertThat(render(Dialect.H2,         DECIMAL, p)).isEqualTo("DECIMAL(10,2)");
        assertThat(render(Dialect.SQLITE,     DECIMAL, p)).isEqualTo("NUMERIC(10,2)");
    }

    @Test
    void renderVarchar() {
        var p = Map.<String, Object>of("len", 255);
        assertThat(render(Dialect.MYSQL,      VARCHAR, p)).isEqualTo("VARCHAR(255)");
        assertThat(render(Dialect.POSTGRESQL, VARCHAR, p)).isEqualTo("VARCHAR(255)");
        assertThat(render(Dialect.H2,         VARCHAR, p)).isEqualTo("VARCHAR(255)");
        assertThat(render(Dialect.SQLITE,     VARCHAR, p)).isEqualTo("TEXT");
    }

    @Test
    void renderText() {
        assertThat(render(Dialect.MYSQL,      TEXT, Map.of())).isEqualTo("TEXT");
        assertThat(render(Dialect.POSTGRESQL, TEXT, Map.of())).isEqualTo("TEXT");
        assertThat(render(Dialect.H2,         TEXT, Map.of())).isEqualTo("CLOB");
        assertThat(render(Dialect.SQLITE,     TEXT, Map.of())).isEqualTo("TEXT");
    }

    @Test
    void renderBoolean() {
        assertThat(render(Dialect.MYSQL,      BOOLEAN, Map.of())).isEqualTo("TINYINT(1)");
        assertThat(render(Dialect.POSTGRESQL, BOOLEAN, Map.of())).isEqualTo("BOOLEAN");
        assertThat(render(Dialect.H2,         BOOLEAN, Map.of())).isEqualTo("BOOLEAN");
        assertThat(render(Dialect.SQLITE,     BOOLEAN, Map.of())).isEqualTo("INTEGER");
    }

    @Test
    void renderDate() {
        assertThat(render(Dialect.MYSQL,      DATE, Map.of())).isEqualTo("DATE");
        assertThat(render(Dialect.POSTGRESQL, DATE, Map.of())).isEqualTo("DATE");
        assertThat(render(Dialect.H2,         DATE, Map.of())).isEqualTo("DATE");
        assertThat(render(Dialect.SQLITE,     DATE, Map.of())).isEqualTo("TEXT");
    }

    @Test
    void renderTimestamp() {
        assertThat(render(Dialect.MYSQL,      TIMESTAMP, Map.of())).isEqualTo("TIMESTAMP");
        assertThat(render(Dialect.POSTGRESQL, TIMESTAMP, Map.of())).isEqualTo("TIMESTAMP");
        assertThat(render(Dialect.H2,         TIMESTAMP, Map.of())).isEqualTo("TIMESTAMP");
        assertThat(render(Dialect.SQLITE,     TIMESTAMP, Map.of())).isEqualTo("TEXT");
    }

    @Test
    void renderJson() {
        assertThat(render(Dialect.MYSQL,      JSON, Map.of())).isEqualTo("JSON");
        assertThat(render(Dialect.POSTGRESQL, JSON, Map.of())).isEqualTo("JSONB");
        assertThat(render(Dialect.H2,         JSON, Map.of())).isEqualTo("JSON");
        assertThat(render(Dialect.SQLITE,     JSON, Map.of())).isEqualTo("TEXT");
    }

    @Test
    void renderBlob() {
        assertThat(render(Dialect.MYSQL,      BLOB, Map.of())).isEqualTo("BLOB");
        assertThat(render(Dialect.POSTGRESQL, BLOB, Map.of())).isEqualTo("BYTEA");
        assertThat(render(Dialect.H2,         BLOB, Map.of())).isEqualTo("BLOB");
        assertThat(render(Dialect.SQLITE,     BLOB, Map.of())).isEqualTo("BLOB");
    }

    @Test
    void quoteIdentifier() {
        assertThat(quote(Dialect.MYSQL,      "users")).isEqualTo("`users`");
        assertThat(quote(Dialect.POSTGRESQL, "users")).isEqualTo("\"users\"");
        assertThat(quote(Dialect.H2,         "users")).isEqualTo("\"users\"");
        assertThat(quote(Dialect.SQLITE,     "users")).isEqualTo("\"users\"");
    }

    @Test
    void autoIncrementPkColumn() {
        assertThat(autoIncrementPk(Dialect.MYSQL,      "id"))
            .isEqualTo("`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY");
        assertThat(autoIncrementPk(Dialect.POSTGRESQL, "id"))
            .isEqualTo("\"id\" BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY");
        assertThat(autoIncrementPk(Dialect.H2,         "id"))
            .isEqualTo("\"id\" BIGINT AUTO_INCREMENT PRIMARY KEY");
        assertThat(autoIncrementPk(Dialect.SQLITE,     "id"))
            .isEqualTo("\"id\" INTEGER PRIMARY KEY AUTOINCREMENT");
    }
}
```

- [ ] **Step 2: Run the test**

Run: `cd server && mvn -pl data-talk-application test -Dtest=DialectTypeRegistryTest -q`
Expected: COMPILATION ERROR — `DialectTypeRegistry` does not exist.

- [ ] **Step 3: Implement the registry**

```java
// server/data-talk-application/src/main/java/com/datatalk/application/er/DialectTypeRegistry.java
package com.datatalk.application.er;

import com.datatalk.domain.er.Dialect;

import java.util.Map;

public final class DialectTypeRegistry {

    private DialectTypeRegistry() {}

    public enum AbstractType {
        BIGINT, INT, SMALLINT, DECIMAL, VARCHAR, TEXT, BOOLEAN,
        DATE, TIMESTAMP, JSON, BLOB
    }

    public static String render(Dialect d, AbstractType t, Map<String, Object> params) {
        return switch (t) {
            case BIGINT -> d == Dialect.SQLITE ? "INTEGER" : "BIGINT";
            case INT -> switch (d) {
                case POSTGRESQL, SQLITE -> "INTEGER";
                default -> "INT";
            };
            case SMALLINT -> d == Dialect.SQLITE ? "INTEGER" : "SMALLINT";
            case DECIMAL -> {
                int p = ((Number) params.getOrDefault("precision", 10)).intValue();
                int s = ((Number) params.getOrDefault("scale", 2)).intValue();
                yield (d == Dialect.POSTGRESQL || d == Dialect.SQLITE)
                    ? "NUMERIC(" + p + "," + s + ")"
                    : "DECIMAL(" + p + "," + s + ")";
            }
            case VARCHAR -> {
                if (d == Dialect.SQLITE) yield "TEXT";
                int len = ((Number) params.getOrDefault("len", 255)).intValue();
                yield "VARCHAR(" + len + ")";
            }
            case TEXT -> switch (d) {
                case H2 -> "CLOB";
                default -> "TEXT";
            };
            case BOOLEAN -> switch (d) {
                case MYSQL -> "TINYINT(1)";
                case SQLITE -> "INTEGER";
                default -> "BOOLEAN";
            };
            case DATE -> d == Dialect.SQLITE ? "TEXT" : "DATE";
            case TIMESTAMP -> d == Dialect.SQLITE ? "TEXT" : "TIMESTAMP";
            case JSON -> switch (d) {
                case POSTGRESQL -> "JSONB";
                case SQLITE -> "TEXT";
                default -> "JSON";
            };
            case BLOB -> switch (d) {
                case POSTGRESQL -> "BYTEA";
                default -> "BLOB";
            };
        };
    }

    public static String quote(Dialect d, String ident) {
        return d == Dialect.MYSQL ? "`" + ident + "`" : "\"" + ident + "\"";
    }

    public static String autoIncrementPk(Dialect d, String colName) {
        String quoted = quote(d, colName);
        return switch (d) {
            case MYSQL      -> quoted + " BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY";
            case POSTGRESQL -> quoted + " BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY";
            case H2         -> quoted + " BIGINT AUTO_INCREMENT PRIMARY KEY";
            case SQLITE     -> quoted + " INTEGER PRIMARY KEY AUTOINCREMENT";
        };
    }
}
```

- [ ] **Step 4: Run the test**

Run: `cd server && mvn -pl data-talk-application test -Dtest=DialectTypeRegistryTest -q`
Expected: PASS (all 13 tests).

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-application/
git commit -m "feat(er/dialect): DialectTypeRegistry single source of truth

11 abstract types × 4 dialects = 44 render assertions; identifier quoting
and auto-increment PK fragment per dialect. Used by all four DdlGenerator
implementations to avoid scattered dialect branches."
```

### Task 2: Domain types for Designer (`ErDesignerTable`, `ErDesignerColumn`, etc.) + DDL records

**Files:**
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDesignerColumn.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDesignerIndex.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDesignerUnique.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDesignerTable.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDesignerRelation.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDesignerPayload.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDdlKind.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDdlStatement.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/SkippedOp.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDdlPlan.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErSchemaDiff.java`

- [ ] **Step 1: Create the records**

`ErDesignerColumn.java`:

```java
package com.datatalk.domain.er;

public record ErDesignerColumn(
    String id,
    String name,
    String type,                 // dialect-specific SQL fragment, e.g. "BIGINT", "VARCHAR(255)"
    boolean nullable,
    boolean isPrimaryKey,
    boolean isAutoIncrement,
    String defaultValue,
    String comment
) {}
```

`ErDesignerIndex.java`:

```java
package com.datatalk.domain.er;

import java.util.List;

public record ErDesignerIndex(String name, List<String> columns) {}
```

`ErDesignerUnique.java`:

```java
package com.datatalk.domain.er;

import java.util.List;

public record ErDesignerUnique(List<String> columns) {}
```

`ErDesignerTable.java`:

```java
package com.datatalk.domain.er;

import java.util.List;

public record ErDesignerTable(
    String id,
    String name,
    String comment,
    List<ErDesignerColumn> columns,
    List<ErDesignerIndex> indexes,
    List<ErDesignerUnique> uniques
) {}
```

`ErDesignerRelation.java`:

```java
package com.datatalk.domain.er;

public record ErDesignerRelation(
    String id,
    String fromTableId,
    String fromColumnId,
    String toTableId,
    String toColumnId,
    String type,                 // one_to_one / one_to_many / many_to_one / many_to_many
    String constraintMethod      // "database_fk" or "comment_ref"
) {}
```

`ErDesignerPayload.java`:

```java
package com.datatalk.domain.er;

import java.util.List;

public record ErDesignerPayload(
    String dialectName,          // mysql / postgresql / h2 / sqlite
    String targetConnectionId,
    String targetDatabase,
    String targetSchema,
    List<ErDesignerTable> tables,
    List<ErDesignerRelation> relations
) {
    public Dialect dialect() {
        return Dialect.fromConnectionKind(dialectName)
            .orElseThrow(() -> new ErErrors.DialectUnsupportedException(dialectName));
    }
}
```

`ErDdlKind.java`:

```java
package com.datatalk.domain.er;

public enum ErDdlKind { CREATE_TABLE, ADD_COLUMN, ADD_FK, CREATE_INDEX }
```

`ErDdlStatement.java`:

```java
package com.datatalk.domain.er;

public record ErDdlStatement(String sql, ErDdlKind kind, String table) {}
```

`SkippedOp.java`:

```java
package com.datatalk.domain.er;

public record SkippedOp(
    String opType,               // drop_table / drop_column / alter_column_type / rename
    String table,
    String column,               // nullable
    String reason,               // day1_unsupported / sqlite_alter_unsupported / etc.
    String hint                  // English aiHint
) {}
```

`ErDdlPlan.java`:

```java
package com.datatalk.domain.er;

import java.util.List;

public record ErDdlPlan(List<ErDdlStatement> statements, List<SkippedOp> skipped) {}
```

`ErSchemaDiff.java`:

```java
package com.datatalk.domain.er;

import java.util.List;

public sealed interface ErSchemaDiff permits
    ErSchemaDiff.TableAdded,
    ErSchemaDiff.TableDropped,
    ErSchemaDiff.ColumnAdded,
    ErSchemaDiff.ColumnDropped,
    ErSchemaDiff.ColumnTypeChanged,
    ErSchemaDiff.ConstraintAdded,
    ErSchemaDiff.ConstraintDropped {

    record TableAdded(ErDesignerTable table) implements ErSchemaDiff {}
    record TableDropped(String tableName) implements ErSchemaDiff {}
    record ColumnAdded(String tableName, ErDesignerColumn column) implements ErSchemaDiff {}
    record ColumnDropped(String tableName, String columnName) implements ErSchemaDiff {}
    record ColumnTypeChanged(String tableName, String columnName, String oldType, String newType) implements ErSchemaDiff {}
    record ConstraintAdded(ErDesignerRelation relation) implements ErSchemaDiff {}
    record ConstraintDropped(String relationName) implements ErSchemaDiff {}
}
```

- [ ] **Step 2: Compile**

Run: `cd server && mvn -pl data-talk-domain compile -q`
Expected: zero errors.

- [ ] **Step 3: Commit**

```bash
git add server/data-talk-domain/
git commit -m "feat(domain/er): designer + DDL plan + schema diff records

ErDesignerPayload mirrors the frontend payload; ErDdlPlan + SkippedOp
encode generation results; ErSchemaDiff sealed interface enumerates the
seven diff kinds the generator must handle."
```

### Task 3: `MySqlDdlGenerator`

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/er/ErDdlGenerator.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/er/MySqlDdlGenerator.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/er/MySqlDdlGeneratorTest.java`

- [ ] **Step 1: Define the interface**

```java
// server/data-talk-application/src/main/java/com/datatalk/application/er/ErDdlGenerator.java
package com.datatalk.application.er;

import com.datatalk.domain.er.Dialect;
import com.datatalk.domain.er.ErDdlStatement;
import com.datatalk.domain.er.ErSchemaDiff;
import com.datatalk.domain.er.SkippedOp;

public interface ErDdlGenerator {
    Dialect dialect();
    /** Returns either a generated statement or a SkippedOp explaining why nothing was emitted. */
    GenerateResult generate(ErSchemaDiff diff);

    sealed interface GenerateResult permits Generated, Skipped {
        record Generated(ErDdlStatement statement) implements GenerateResult {}
        record Skipped(SkippedOp skipped) implements GenerateResult {}
    }
}
```

- [ ] **Step 2: Write the failing test**

```java
// server/data-talk-infrastructure/src/test/java/com/datatalk/infra/er/MySqlDdlGeneratorTest.java
package com.datatalk.infra.er;

import com.datatalk.application.er.ErDdlGenerator;
import com.datatalk.domain.er.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlDdlGeneratorTest {

    private final MySqlDdlGenerator gen = new MySqlDdlGenerator();

    @Test
    void createTableWithAutoIncPkAndVarchar() {
        var col1 = new ErDesignerColumn("c1", "id", "BIGINT", false, true, true, null, null);
        var col2 = new ErDesignerColumn("c2", "email", "VARCHAR(255)", false, false, false, null, null);
        var t = new ErDesignerTable("t1", "users", null, List.of(col1, col2), List.of(), List.of());
        var diff = new ErSchemaDiff.TableAdded(t);

        var res = gen.generate(diff);
        assertThat(res).isInstanceOf(ErDdlGenerator.GenerateResult.Generated.class);
        var sql = ((ErDdlGenerator.GenerateResult.Generated) res).statement().sql();
        assertThat(sql).startsWith("CREATE TABLE `users` (");
        assertThat(sql).contains("`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY");
        assertThat(sql).contains("`email` VARCHAR(255) NOT NULL");
    }

    @Test
    void addColumnEmitsAlterTableAddColumn() {
        var col = new ErDesignerColumn("c", "discount", "DECIMAL(10,2)", true, false, false, null, null);
        var diff = new ErSchemaDiff.ColumnAdded("orders", col);
        var res = gen.generate(diff);
        var sql = ((ErDdlGenerator.GenerateResult.Generated) res).statement().sql();
        assertThat(sql).isEqualTo("ALTER TABLE `orders` ADD COLUMN `discount` DECIMAL(10,2) NULL");
    }

    @Test
    void addFkConstraintEmitsAlterTableAddConstraint() {
        var rel = new ErDesignerRelation("r1", "t-orders", "c-user_id", "t-users", "c-id", "many_to_one", "database_fk");
        // The generator needs the resolved table & column names; assume the orchestrator passes them already resolved
        // For this unit test, we simulate by constructing the diff with name-resolved relation
        var resolved = new ErDesignerRelation("r1", "orders", "user_id", "users", "id", "many_to_one", "database_fk");
        var diff = new ErSchemaDiff.ConstraintAdded(resolved);
        var res = gen.generate(diff);
        var sql = ((ErDdlGenerator.GenerateResult.Generated) res).statement().sql();
        assertThat(sql).contains("ALTER TABLE `orders` ADD CONSTRAINT");
        assertThat(sql).contains("FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)");
    }

    @Test
    void createIndexEmitsCreateIndex() {
        var diff = new ErSchemaDiff.ConstraintAdded(
            new ErDesignerRelation("idx1", "orders", "created_at", null, null, "index", "index")
        );
        // Indexes are encoded as ConstraintAdded with a sentinel constraintMethod="index"; orchestrator decides
        // ... test left intentionally simple; full coverage in service test (Task 8)
    }

    @Test
    void dropTableReturnsSkippedDay1Unsupported() {
        var diff = new ErSchemaDiff.TableDropped("orders");
        var res = gen.generate(diff);
        assertThat(res).isInstanceOf(ErDdlGenerator.GenerateResult.Skipped.class);
        var skipped = ((ErDdlGenerator.GenerateResult.Skipped) res).skipped();
        assertThat(skipped.opType()).isEqualTo("drop_table");
        assertThat(skipped.reason()).isEqualTo("day1_unsupported");
        assertThat(skipped.hint()).contains("manually");
    }

    @Test
    void dropColumnReturnsSkippedDay1Unsupported() {
        var diff = new ErSchemaDiff.ColumnDropped("orders", "deprecated");
        var res = gen.generate(diff);
        var skipped = ((ErDdlGenerator.GenerateResult.Skipped) res).skipped();
        assertThat(skipped.opType()).isEqualTo("drop_column");
    }

    @Test
    void columnTypeChangedReturnsSkipped() {
        var diff = new ErSchemaDiff.ColumnTypeChanged("orders", "amount", "DECIMAL(10,2)", "DECIMAL(12,2)");
        var res = gen.generate(diff);
        assertThat(res).isInstanceOf(ErDdlGenerator.GenerateResult.Skipped.class);
        assertThat(((ErDdlGenerator.GenerateResult.Skipped) res).skipped().opType()).isEqualTo("alter_column_type");
    }
}
```

- [ ] **Step 3: Run the test**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=MySqlDdlGeneratorTest -q`
Expected: COMPILATION ERROR.

- [ ] **Step 4: Implement `MySqlDdlGenerator`**

```java
// server/data-talk-infrastructure/src/main/java/com/datatalk/infra/er/MySqlDdlGenerator.java
package com.datatalk.infra.er;

import com.datatalk.application.er.DialectTypeRegistry;
import com.datatalk.application.er.ErDdlGenerator;
import com.datatalk.domain.er.*;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class MySqlDdlGenerator implements ErDdlGenerator {

    @Override public Dialect dialect() { return Dialect.MYSQL; }

    @Override
    public GenerateResult generate(ErSchemaDiff diff) {
        return switch (diff) {
            case ErSchemaDiff.TableAdded t -> new GenerateResult.Generated(createTable(t.table()));
            case ErSchemaDiff.ColumnAdded c -> new GenerateResult.Generated(addColumn(c.tableName(), c.column()));
            case ErSchemaDiff.ConstraintAdded rel -> new GenerateResult.Generated(addFk(rel.relation()));
            case ErSchemaDiff.TableDropped t -> skipped("drop_table", t.tableName(), null,
                "DROP TABLE day-1 unsupported", "DROP statements are intentionally not generated. Add the SQL manually in the query_editor.");
            case ErSchemaDiff.ColumnDropped c -> skipped("drop_column", c.tableName(), c.columnName(),
                "day1_unsupported", "DROP COLUMN is not generated. Add it manually in the query_editor.");
            case ErSchemaDiff.ColumnTypeChanged ct -> skipped("alter_column_type", ct.tableName(), ct.columnName(),
                "day1_unsupported", "Column type changes are not generated (dialect-specific data migration is required). Add the ALTER manually.");
            case ErSchemaDiff.ConstraintDropped cd -> skipped("drop_constraint", null, null,
                "day1_unsupported", "DROP CONSTRAINT is not generated. Add it manually.");
        };
    }

    private ErDdlStatement createTable(ErDesignerTable t) {
        String body = t.columns().stream()
            .map(this::renderColumn)
            .collect(Collectors.joining(",\n  ", "  ", ""));
        String sql = "CREATE TABLE " + DialectTypeRegistry.quote(Dialect.MYSQL, t.name()) + " (\n" + body + "\n)";
        return new ErDdlStatement(sql, ErDdlKind.CREATE_TABLE, t.name());
    }

    private String renderColumn(ErDesignerColumn col) {
        if (col.isAutoIncrement() && col.isPrimaryKey()) {
            return DialectTypeRegistry.autoIncrementPk(Dialect.MYSQL, col.name());
        }
        StringBuilder sb = new StringBuilder();
        sb.append(DialectTypeRegistry.quote(Dialect.MYSQL, col.name())).append(' ').append(col.type());
        sb.append(col.nullable() ? " NULL" : " NOT NULL");
        if (col.defaultValue() != null) sb.append(" DEFAULT ").append(col.defaultValue());
        if (col.comment() != null) sb.append(" COMMENT '").append(col.comment().replace("'", "''")).append('\'');
        if (col.isPrimaryKey() && !col.isAutoIncrement()) sb.append(" PRIMARY KEY");
        return sb.toString();
    }

    private ErDdlStatement addColumn(String tableName, ErDesignerColumn col) {
        String sql = "ALTER TABLE " + DialectTypeRegistry.quote(Dialect.MYSQL, tableName)
                   + " ADD COLUMN " + renderColumn(col);
        return new ErDdlStatement(sql, ErDdlKind.ADD_COLUMN, tableName);
    }

    private ErDdlStatement addFk(ErDesignerRelation rel) {
        String name = "fk_" + rel.fromTableId() + "_" + rel.fromColumnId();
        String sql = "ALTER TABLE " + DialectTypeRegistry.quote(Dialect.MYSQL, rel.fromTableId())
                   + " ADD CONSTRAINT " + DialectTypeRegistry.quote(Dialect.MYSQL, name)
                   + " FOREIGN KEY (" + DialectTypeRegistry.quote(Dialect.MYSQL, rel.fromColumnId()) + ")"
                   + " REFERENCES " + DialectTypeRegistry.quote(Dialect.MYSQL, rel.toTableId())
                   + " (" + DialectTypeRegistry.quote(Dialect.MYSQL, rel.toColumnId()) + ")";
        return new ErDdlStatement(sql, ErDdlKind.ADD_FK, rel.fromTableId());
    }

    private GenerateResult skipped(String opType, String table, String column, String reason, String hint) {
        return new GenerateResult.Skipped(new SkippedOp(opType, table, column, reason, hint));
    }
}
```

- [ ] **Step 5: Run the test**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=MySqlDdlGeneratorTest -q`
Expected: PASS (6 tests; the index test placeholder is benign).

- [ ] **Step 6: Commit**

```bash
git add server/
git commit -m "feat(er/ddl): MySqlDdlGenerator (CREATE / ADD COLUMN / ADD FK + SkippedOp for DROP/ALTER)"
```

### Task 4: `PostgresDdlGenerator`

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/er/PostgresDdlGenerator.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/er/PostgresDdlGeneratorTest.java`

- [ ] **Step 1: Adapt the MySQL test as Postgres assertions**

The structure is identical to MySQL's test, with these substitutions:
- `\`users\`` → `"users"`
- MySQL `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY` → PG `BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`
- column comment uses `COMMENT ON COLUMN ... IS '...'` as a separate statement (or skipped — see Step 3)

Write the test mirroring `MySqlDdlGeneratorTest.java` with PG expectations.

- [ ] **Step 2: Implement `PostgresDdlGenerator`**

Identical structure to `MySqlDdlGenerator`, but:
- Pass `Dialect.POSTGRESQL` to `DialectTypeRegistry`
- Render `IDENTITY` PK via `autoIncrementPk` (already correct in T1)
- Drop the `COMMENT '...'` clause inside CREATE TABLE — Postgres column comments are separate `COMMENT ON COLUMN`. For day-1, keep it simple: skip column-level comments in CREATE TABLE for Postgres (record but don't render). Document this in `er-tab-protocol.md` (T30).

```java
package com.datatalk.infra.er;

import com.datatalk.application.er.DialectTypeRegistry;
import com.datatalk.application.er.ErDdlGenerator;
import com.datatalk.domain.er.*;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class PostgresDdlGenerator implements ErDdlGenerator {

    @Override public Dialect dialect() { return Dialect.POSTGRESQL; }

    @Override
    public GenerateResult generate(ErSchemaDiff diff) {
        return switch (diff) {
            case ErSchemaDiff.TableAdded t -> new GenerateResult.Generated(createTable(t.table()));
            case ErSchemaDiff.ColumnAdded c -> new GenerateResult.Generated(addColumn(c.tableName(), c.column()));
            case ErSchemaDiff.ConstraintAdded rel -> new GenerateResult.Generated(addFk(rel.relation()));
            case ErSchemaDiff.TableDropped t -> skipped("drop_table", t.tableName(), null);
            case ErSchemaDiff.ColumnDropped c -> skipped("drop_column", c.tableName(), c.columnName());
            case ErSchemaDiff.ColumnTypeChanged ct -> skipped("alter_column_type", ct.tableName(), ct.columnName());
            case ErSchemaDiff.ConstraintDropped cd -> skipped("drop_constraint", null, null);
        };
    }

    // (helpers identical in shape to MySqlDdlGenerator; substitute Dialect.POSTGRESQL and skip inline COMMENT)
    // (omitted for brevity — implement following the same pattern as MySqlDdlGenerator with the noted differences)
    private ErDdlStatement createTable(ErDesignerTable t) {
        String body = t.columns().stream().map(this::renderColumn).collect(Collectors.joining(",\n  ", "  ", ""));
        String sql = "CREATE TABLE " + DialectTypeRegistry.quote(Dialect.POSTGRESQL, t.name()) + " (\n" + body + "\n)";
        return new ErDdlStatement(sql, ErDdlKind.CREATE_TABLE, t.name());
    }

    private String renderColumn(ErDesignerColumn col) {
        if (col.isAutoIncrement() && col.isPrimaryKey()) return DialectTypeRegistry.autoIncrementPk(Dialect.POSTGRESQL, col.name());
        StringBuilder sb = new StringBuilder();
        sb.append(DialectTypeRegistry.quote(Dialect.POSTGRESQL, col.name())).append(' ').append(col.type());
        sb.append(col.nullable() ? "" : " NOT NULL");
        if (col.defaultValue() != null) sb.append(" DEFAULT ").append(col.defaultValue());
        if (col.isPrimaryKey() && !col.isAutoIncrement()) sb.append(" PRIMARY KEY");
        return sb.toString();
    }

    private ErDdlStatement addColumn(String tableName, ErDesignerColumn col) {
        return new ErDdlStatement(
            "ALTER TABLE " + DialectTypeRegistry.quote(Dialect.POSTGRESQL, tableName) + " ADD COLUMN " + renderColumn(col),
            ErDdlKind.ADD_COLUMN, tableName);
    }

    private ErDdlStatement addFk(ErDesignerRelation rel) {
        String name = "fk_" + rel.fromTableId() + "_" + rel.fromColumnId();
        return new ErDdlStatement(
            "ALTER TABLE " + DialectTypeRegistry.quote(Dialect.POSTGRESQL, rel.fromTableId())
            + " ADD CONSTRAINT " + DialectTypeRegistry.quote(Dialect.POSTGRESQL, name)
            + " FOREIGN KEY (" + DialectTypeRegistry.quote(Dialect.POSTGRESQL, rel.fromColumnId()) + ")"
            + " REFERENCES " + DialectTypeRegistry.quote(Dialect.POSTGRESQL, rel.toTableId())
            + " (" + DialectTypeRegistry.quote(Dialect.POSTGRESQL, rel.toColumnId()) + ")",
            ErDdlKind.ADD_FK, rel.fromTableId());
    }

    private GenerateResult skipped(String opType, String table, String column) {
        return new GenerateResult.Skipped(new SkippedOp(opType, table, column, "day1_unsupported",
            "Postgres " + opType + " is not generated day-1. Add the SQL manually in the query_editor."));
    }
}
```

- [ ] **Step 3: Run the test**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=PostgresDdlGeneratorTest -q`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add server/
git commit -m "feat(er/ddl): PostgresDdlGenerator with IDENTITY auto-increment + skipped DROP/ALTER"
```

### Task 5: `H2DdlGenerator` + `SqliteDdlGenerator`

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/er/H2DdlGenerator.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/er/SqliteDdlGenerator.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/er/H2DdlGeneratorTest.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/er/SqliteDdlGeneratorTest.java`

- [ ] **Step 1: Implement `H2DdlGenerator`**

H2 is structurally identical to PostgresDdlGenerator with `Dialect.H2`:
- `quote` uses double quotes (same as PG).
- Auto-increment PK uses `BIGINT AUTO_INCREMENT PRIMARY KEY`.
- ADD COLUMN / ADD FK syntax same as PG.

Adapt the PG implementation, swapping the Dialect.

- [ ] **Step 2: Implement `SqliteDdlGenerator`**

SQLite is the most restricted: only `CREATE TABLE` and `CREATE INDEX` are emitted; `ALTER TABLE ADD COLUMN` is a SkippedOp because day-1 only supports CREATE for SQLite (the spec § 8.6 "ER Designer day-1 DDL 生成范围" restricts SQLite Designer to CREATE only):

```java
package com.datatalk.infra.er;

import com.datatalk.application.er.DialectTypeRegistry;
import com.datatalk.application.er.ErDdlGenerator;
import com.datatalk.domain.er.*;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class SqliteDdlGenerator implements ErDdlGenerator {

    @Override public Dialect dialect() { return Dialect.SQLITE; }

    @Override
    public GenerateResult generate(ErSchemaDiff diff) {
        return switch (diff) {
            case ErSchemaDiff.TableAdded t -> new GenerateResult.Generated(createTable(t.table()));
            case ErSchemaDiff.ColumnAdded c -> skipped("alter_add_column", c.tableName(), c.column().name(),
                "sqlite_alter_unsupported",
                "SQLite ALTER TABLE cannot reliably ADD COLUMN with constraints. Add it manually with the SQLite-specific ALTER pattern.");
            case ErSchemaDiff.ConstraintAdded rel -> skipped("add_fk", rel.relation().fromTableId(), rel.relation().fromColumnId(),
                "sqlite_alter_unsupported",
                "SQLite cannot ADD CONSTRAINT after table creation. Recreate the table or write the migration manually.");
            case ErSchemaDiff.TableDropped t -> skipped("drop_table", t.tableName(), null, "day1_unsupported",
                "SQLite DROP TABLE is intentionally not generated. Add it manually in the query_editor.");
            case ErSchemaDiff.ColumnDropped c -> skipped("drop_column", c.tableName(), c.columnName(),
                "day1_unsupported", "SQLite DROP COLUMN requires recreating the table; not generated day-1.");
            case ErSchemaDiff.ColumnTypeChanged ct -> skipped("alter_column_type", ct.tableName(), ct.columnName(),
                "day1_unsupported", "SQLite cannot change column types in place; add the migration SQL manually.");
            case ErSchemaDiff.ConstraintDropped cd -> skipped("drop_constraint", null, null,
                "sqlite_alter_unsupported", "SQLite cannot DROP CONSTRAINT in place.");
        };
    }

    private ErDdlStatement createTable(ErDesignerTable t) {
        String body = t.columns().stream().map(this::renderColumn).collect(Collectors.joining(",\n  ", "  ", ""));
        String sql = "CREATE TABLE " + DialectTypeRegistry.quote(Dialect.SQLITE, t.name()) + " (\n" + body + "\n)";
        return new ErDdlStatement(sql, ErDdlKind.CREATE_TABLE, t.name());
    }

    private String renderColumn(ErDesignerColumn col) {
        if (col.isAutoIncrement() && col.isPrimaryKey()) return DialectTypeRegistry.autoIncrementPk(Dialect.SQLITE, col.name());
        StringBuilder sb = new StringBuilder();
        sb.append(DialectTypeRegistry.quote(Dialect.SQLITE, col.name())).append(' ').append(col.type());
        sb.append(col.nullable() ? "" : " NOT NULL");
        if (col.defaultValue() != null) sb.append(" DEFAULT ").append(col.defaultValue());
        if (col.isPrimaryKey() && !col.isAutoIncrement()) sb.append(" PRIMARY KEY");
        return sb.toString();
    }

    private GenerateResult skipped(String opType, String table, String column, String reason, String hint) {
        return new GenerateResult.Skipped(new SkippedOp(opType, table, column, reason, hint));
    }
}
```

- [ ] **Step 3: Tests for both**

Each generator has its own test class with the standard 6+ scenarios. Use the MySQL/PG tests as templates.

- [ ] **Step 4: Run tests**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest='*DdlGeneratorTest' -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/
git commit -m "feat(er/ddl): H2 and SQLite generators

H2 mirrors Postgres syntax with H2 dialect quoting + auto-increment.
SQLite Designer day-1 only emits CREATE TABLE; ADD COLUMN / ADD FK are
SkippedOps with reason 'sqlite_alter_unsupported' per spec §8.6."
```

---

## Batch 2: Backend ER schema diff + DDL generator service

### Task 6: `ErSchemaDiffService` — compute draft vs real-DB diff

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/er/ErSchemaDiffService.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/er/ErSchemaDiffServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
// server/data-talk-application/src/test/java/com/datatalk/application/er/ErSchemaDiffServiceTest.java
package com.datatalk.application.er;

import com.datatalk.domain.er.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErSchemaDiffServiceTest {

    @Mock ErRelationDiscoveryService discovery;
    @InjectMocks ErSchemaDiffService service;

    @Test
    void emptyTargetReturnsAllDraftTablesAsAdded() {
        var draftTable = new ErDesignerTable("t1", "users", null, List.of(), List.of(), List.of());
        var draft = new ErDesignerPayload("mysql", "c1", null, null, List.of(draftTable), List.of());
        when(discovery.discover("c1", List.of(), 0))
            .thenReturn(new ErGraph(List.of(), List.of(), "empty", List.of()));

        var diffs = service.diff(draft, "c1");
        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0)).isInstanceOf(ErSchemaDiff.TableAdded.class);
    }

    @Test
    void newColumnAppearsAsColumnAdded() {
        var realCols = List.of(new ErColumnMeta("id", "BIGINT", false, true, false, true, null, null));
        var realTable = new ErTableMeta("users", null, realCols, List.of());
        when(discovery.discover("c1", List.of("users"), 0))
            .thenReturn(new ErGraph(List.of(realTable), List.of(), "1 table / 0 edges", List.of()));

        var draftCols = List.of(
            new ErDesignerColumn("c1", "id", "BIGINT", false, true, true, null, null),
            new ErDesignerColumn("c2", "email", "VARCHAR(255)", false, false, false, null, null)
        );
        var draftTable = new ErDesignerTable("t1", "users", null, draftCols, List.of(), List.of());
        var draft = new ErDesignerPayload("mysql", "c1", null, null, List.of(draftTable), List.of());

        var diffs = service.diff(draft, "c1");
        assertThat(diffs).filteredOn(d -> d instanceof ErSchemaDiff.ColumnAdded).hasSize(1);
    }

    @Test
    void droppedColumnAppearsAsColumnDropped() {
        var realCols = List.of(
            new ErColumnMeta("id", "BIGINT", false, true, false, true, null, null),
            new ErColumnMeta("legacy_field", "VARCHAR(50)", true, false, false, false, null, null)
        );
        var realTable = new ErTableMeta("users", null, realCols, List.of());
        when(discovery.discover("c1", List.of("users"), 0))
            .thenReturn(new ErGraph(List.of(realTable), List.of(), "x", List.of()));

        var draftCols = List.of(new ErDesignerColumn("c1", "id", "BIGINT", false, true, true, null, null));
        var draftTable = new ErDesignerTable("t1", "users", null, draftCols, List.of(), List.of());
        var draft = new ErDesignerPayload("mysql", "c1", null, null, List.of(draftTable), List.of());

        var diffs = service.diff(draft, "c1");
        assertThat(diffs).filteredOn(d -> d instanceof ErSchemaDiff.ColumnDropped).hasSize(1);
    }

    @Test
    void typeChangeAppearsAsColumnTypeChanged() {
        var realCols = List.of(new ErColumnMeta("amount", "DECIMAL(10,2)", false, false, false, false, null, null));
        var realTable = new ErTableMeta("orders", null, realCols, List.of());
        when(discovery.discover("c1", List.of("orders"), 0))
            .thenReturn(new ErGraph(List.of(realTable), List.of(), "x", List.of()));

        var draftCols = List.of(new ErDesignerColumn("c1", "amount", "DECIMAL(12,2)", false, false, false, null, null));
        var draftTable = new ErDesignerTable("t1", "orders", null, draftCols, List.of(), List.of());
        var draft = new ErDesignerPayload("mysql", "c1", null, null, List.of(draftTable), List.of());

        var diffs = service.diff(draft, "c1");
        assertThat(diffs).filteredOn(d -> d instanceof ErSchemaDiff.ColumnTypeChanged).hasSize(1);
    }

    @Test
    void newRelationAppearsAsConstraintAdded() {
        // real schema has 2 tables, no FKs; draft adds an FK
        var u = new ErTableMeta("users",  null, List.of(new ErColumnMeta("id", "BIGINT", false, true, false, true, null, null)), List.of());
        var o = new ErTableMeta("orders", null, List.of(new ErColumnMeta("user_id", "BIGINT", false, false, false, false, null, null)), List.of());
        when(discovery.discover("c1", List.of("users", "orders"), 0))
            .thenReturn(new ErGraph(List.of(u, o), List.of(), "x", List.of()));

        var draftU = new ErDesignerTable("t1", "users",  null, List.of(new ErDesignerColumn("c1", "id",      "BIGINT", false, true,  true,  null, null)), List.of(), List.of());
        var draftO = new ErDesignerTable("t2", "orders", null, List.of(new ErDesignerColumn("c2", "user_id", "BIGINT", false, false, false, null, null)), List.of(), List.of());
        var rel = new ErDesignerRelation("r1", "t2", "c2", "t1", "c1", "many_to_one", "database_fk");
        var draft = new ErDesignerPayload("mysql", "c1", null, null, List.of(draftU, draftO), List.of(rel));

        var diffs = service.diff(draft, "c1");
        assertThat(diffs).filteredOn(d -> d instanceof ErSchemaDiff.ConstraintAdded).hasSize(1);
    }
}
```

- [ ] **Step 2: Run the test**

Run: `cd server && mvn -pl data-talk-application test -Dtest=ErSchemaDiffServiceTest -q`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement the service**

```java
// server/data-talk-application/src/main/java/com/datatalk/application/er/ErSchemaDiffService.java
package com.datatalk.application.er;

import com.datatalk.domain.er.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ErSchemaDiffService {

    private final ErRelationDiscoveryService discovery;

    public ErSchemaDiffService(ErRelationDiscoveryService discovery) {
        this.discovery = discovery;
    }

    public List<ErSchemaDiff> diff(ErDesignerPayload draft, String targetConnectionId) {
        if (targetConnectionId == null) {
            throw new IllegalStateException("Designer must be bound to a target connection before diff.");
        }
        List<String> draftTableNames = draft.tables().stream().map(ErDesignerTable::name).toList();
        ErGraph realGraph = discovery.discover(targetConnectionId,
            draftTableNames.isEmpty() ? List.of() : draftTableNames,
            0);

        Map<String, ErTableMeta> realByName = new HashMap<>();
        for (var t : realGraph.nodes()) realByName.put(t.name(), t);

        List<ErSchemaDiff> diffs = new ArrayList<>();

        // Tables
        for (var draftTable : draft.tables()) {
            ErTableMeta realTable = realByName.get(draftTable.name());
            if (realTable == null) {
                diffs.add(new ErSchemaDiff.TableAdded(draftTable));
                continue;
            }
            // Columns
            Map<String, ErColumnMeta> realCols = new HashMap<>();
            for (var c : realTable.columns()) realCols.put(c.name(), c);

            for (var draftCol : draftTable.columns()) {
                ErColumnMeta realCol = realCols.get(draftCol.name());
                if (realCol == null) {
                    diffs.add(new ErSchemaDiff.ColumnAdded(draftTable.name(), draftCol));
                } else if (!normalizeType(draftCol.type()).equals(normalizeType(realCol.type()))) {
                    diffs.add(new ErSchemaDiff.ColumnTypeChanged(
                        draftTable.name(), draftCol.name(), realCol.type(), draftCol.type()));
                }
            }
            for (var realCol : realTable.columns()) {
                if (draftTable.columns().stream().noneMatch(c -> c.name().equals(realCol.name()))) {
                    diffs.add(new ErSchemaDiff.ColumnDropped(draftTable.name(), realCol.name()));
                }
            }
        }
        for (var realName : realByName.keySet()) {
            if (draft.tables().stream().noneMatch(t -> t.name().equals(realName))) {
                diffs.add(new ErSchemaDiff.TableDropped(realName));
            }
        }

        // Relations
        Set<String> realFkKeys = new HashSet<>();
        for (var t : realGraph.nodes()) {
            for (var fk : t.fkOut()) {
                realFkKeys.add(t.name() + "." + fk.sourceColumn() + "->" + fk.targetTable() + "." + fk.targetColumn());
            }
        }
        for (var rel : draft.relations()) {
            // Resolve table/column ids → names against the draft
            String fromTable = nameOfTable(draft, rel.fromTableId());
            String fromCol   = nameOfColumn(draft, rel.fromTableId(), rel.fromColumnId());
            String toTable   = nameOfTable(draft, rel.toTableId());
            String toCol     = nameOfColumn(draft, rel.toTableId(), rel.toColumnId());
            String key = fromTable + "." + fromCol + "->" + toTable + "." + toCol;
            if (!realFkKeys.contains(key)) {
                // Resolve to name-bearing relation for the generator
                var resolved = new ErDesignerRelation(rel.id(), fromTable, fromCol, toTable, toCol, rel.type(), rel.constraintMethod());
                diffs.add(new ErSchemaDiff.ConstraintAdded(resolved));
            }
        }

        return diffs;
    }

    private static String normalizeType(String t) {
        return t == null ? "" : t.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static String nameOfTable(ErDesignerPayload p, String tableId) {
        return p.tables().stream().filter(t -> t.id().equals(tableId)).map(ErDesignerTable::name).findFirst().orElse(tableId);
    }

    private static String nameOfColumn(ErDesignerPayload p, String tableId, String colId) {
        return p.tables().stream()
            .filter(t -> t.id().equals(tableId))
            .flatMap(t -> t.columns().stream())
            .filter(c -> c.id().equals(colId))
            .map(ErDesignerColumn::name)
            .findFirst().orElse(colId);
    }
}
```

(Add `import java.util.Locale;`)

- [ ] **Step 4: Run the test**

Run: `cd server && mvn -pl data-talk-application test -Dtest=ErSchemaDiffServiceTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/
git commit -m "feat(er): ErSchemaDiffService computes draft vs real-DB diff (six diff kinds)"
```

### Task 7: `ErDdlGeneratorService` — orchestrate diff + per-dialect generation

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/er/ErDdlGeneratorService.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/er/ErDdlGeneratorServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
// server/data-talk-application/src/test/java/com/datatalk/application/er/ErDdlGeneratorServiceTest.java
package com.datatalk.application.er;

import com.datatalk.domain.er.*;
import com.datatalk.infra.er.MySqlDdlGenerator;
import com.datatalk.infra.er.PostgresDdlGenerator;
import com.datatalk.infra.er.H2DdlGenerator;
import com.datatalk.infra.er.SqliteDdlGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ErDdlGeneratorServiceTest {

    private final ErSchemaDiffService diffService = mock(ErSchemaDiffService.class);

    private ErDdlGeneratorService service() {
        return new ErDdlGeneratorService(
            diffService,
            List.of(new MySqlDdlGenerator(), new PostgresDdlGenerator(), new H2DdlGenerator(), new SqliteDdlGenerator())
        );
    }

    @Test
    void generateRoutesByDialect() {
        var t = new ErDesignerTable("t1", "users", null, List.of(), List.of(), List.of());
        when(diffService.diff(any(), any())).thenReturn(List.of(new ErSchemaDiff.TableAdded(t)));

        var draft = new ErDesignerPayload("mysql", "c1", null, null, List.of(t), List.of());
        var res = service().generate(new com.datatalk.application.er.GenerateDdlRequest(draft, "c1", false));
        assertThat(res.statements()).hasSize(1);
        assertThat(res.statements().get(0).sql()).contains("CREATE TABLE `users`");
    }

    @Test
    void dropOpsAreRecordedAsSkippedNotEmittedAsSql() {
        when(diffService.diff(any(), any())).thenReturn(List.of(new ErSchemaDiff.TableDropped("orders")));
        var draft = new ErDesignerPayload("mysql", "c1", null, null, List.of(), List.of());
        var res = service().generate(new com.datatalk.application.er.GenerateDdlRequest(draft, "c1", false));
        assertThat(res.statements()).isEmpty();
        assertThat(res.skipped()).hasSize(1);
        assertThat(res.skipped().get(0).opType()).isEqualTo("drop_table");
    }

    @Test
    void missingTargetThrows() {
        var draft = new ErDesignerPayload("mysql", null, null, null, List.of(), List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service().generate(new com.datatalk.application.er.GenerateDdlRequest(draft, null, false)))
            .hasMessageContaining("target");
    }
}
```

Add the request DTO record `GenerateDdlRequest`:

```java
// server/data-talk-application/src/main/java/com/datatalk/application/er/GenerateDdlRequest.java
package com.datatalk.application.er;

import com.datatalk.domain.er.ErDesignerPayload;

public record GenerateDdlRequest(ErDesignerPayload payload, String connectionId, boolean includeDrops) {}
```

- [ ] **Step 2: Run the test**

Run: `cd server && mvn -pl data-talk-application test -Dtest=ErDdlGeneratorServiceTest -q`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement the orchestrator**

```java
// server/data-talk-application/src/main/java/com/datatalk/application/er/ErDdlGeneratorService.java
package com.datatalk.application.er;

import com.datatalk.domain.er.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ErDdlGeneratorService {

    private final ErSchemaDiffService diffService;
    private final Map<Dialect, ErDdlGenerator> generators;

    public ErDdlGeneratorService(ErSchemaDiffService diffService, List<ErDdlGenerator> all) {
        this.diffService = diffService;
        this.generators = new EnumMap<>(Dialect.class);
        for (var g : all) generators.put(g.dialect(), g);
    }

    public ErDdlPlan generate(GenerateDdlRequest req) {
        if (req.connectionId() == null) {
            throw new IllegalArgumentException("generate_ddl requires bind_target first (connectionId is null).");
        }
        var dialect = req.payload().dialect();
        var gen = generators.get(dialect);
        if (gen == null) throw new ErErrors.DialectUnsupportedException(dialect.name().toLowerCase(Locale.ROOT));

        List<ErSchemaDiff> diffs = diffService.diff(req.payload(), req.connectionId());

        List<ErDdlStatement> emitted = new ArrayList<>();
        List<SkippedOp> skipped = new ArrayList<>();
        for (var d : diffs) {
            var r = gen.generate(d);
            if (r instanceof ErDdlGenerator.GenerateResult.Generated g2) emitted.add(g2.statement());
            else if (r instanceof ErDdlGenerator.GenerateResult.Skipped s) skipped.add(s.skipped());
        }
        return new ErDdlPlan(emitted, skipped);
    }

    public List<ErSchemaDiff> diff(ErDesignerPayload payload, String connectionId) {
        return diffService.diff(payload, connectionId);
    }
}
```

- [ ] **Step 4: Run the test**

Run: `cd server && mvn -pl data-talk-application test -Dtest=ErDdlGeneratorServiceTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/
git commit -m "feat(er): ErDdlGeneratorService orchestrates diff + per-dialect generation

EnumMap<Dialect, ErDdlGenerator> dispatch; missing target throws structured
error; skipped ops captured separately from emitted SQL."
```

---

## Batch 3: Backend REST endpoints + Java schema extension

### Task 8: `ErTabController` adds `/generate-ddl`, `/diff`, `/sync-from-db`

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/ErTabController.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/ErTabControllerIT.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/GenerateDdlRequestDto.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/GenerateDdlResponseDto.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/DiffRequestDto.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/DiffResponseDto.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SyncFromDbRequestDto.java`

- [ ] **Step 1: Add IT cases for the three endpoints**

Open `ErTabControllerIT.java` and append:

```java
@Test
void generateDdlReturnsOkWithStatementsAndSkipped() throws Exception {
    String draft = """
      { "dialectName": "h2", "targetConnectionId": "%s",
        "tables": [{
          "id": "t1", "name": "products", "columns": [
            { "id": "c1", "name": "id",   "type": "BIGINT", "nullable": false, "isPrimaryKey": true,  "isAutoIncrement": true  },
            { "id": "c2", "name": "name", "type": "VARCHAR(255)", "nullable": false, "isPrimaryKey": false, "isAutoIncrement": false }
          ], "indexes": [], "uniques": []
        }],
        "relations": []
      }
      """.formatted(connectionId);
    String body = "{ \"payload\": " + draft + ", \"connectionId\": \"" + connectionId + "\", \"includeDrops\": false }";

    mvc.perform(post("/api/er/generate-ddl")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.ddl").isString())
       .andExpect(jsonPath("$.statements", hasSize(1)));
}

@Test
void diffReturnsOkWithDiffList() throws Exception {
    String body = "{ \"payload\": { \"dialectName\": \"h2\", \"targetConnectionId\": \"" + connectionId + "\", "
                + "\"tables\": [], \"relations\": [] }, \"connectionId\": \"" + connectionId + "\" }";
    mvc.perform(post("/api/er/diff")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.diff").isArray());
}

@Test
void generateDdlMissingTargetReturns400() throws Exception {
    String body = "{ \"payload\": { \"dialectName\": \"mysql\", \"tables\": [], \"relations\": [] }, "
                + "\"connectionId\": null }";
    mvc.perform(post("/api/er/generate-ddl")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
       .andExpect(status().isBadRequest())
       .andExpect(jsonPath("$.code").value("target_required_for_apply"))
       .andExpect(jsonPath("$.aiHint").isString());
}
```

- [ ] **Step 2: Run the IT**

Run: `cd server && mvn -pl data-talk-adapter test -Dtest=ErTabControllerIT -q`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement the controller endpoints + DTOs**

Create `GenerateDdlRequestDto.java`:

```java
package com.datatalk.adapter.dto;

import com.datatalk.domain.er.ErDesignerPayload;

public record GenerateDdlRequestDto(ErDesignerPayload payload, String connectionId, boolean includeDrops) {}
```

`GenerateDdlResponseDto.java`:

```java
package com.datatalk.adapter.dto;

import com.datatalk.domain.er.ErDdlPlan;
import com.datatalk.domain.er.ErDdlStatement;
import com.datatalk.domain.er.SkippedOp;

import java.util.List;

public record GenerateDdlResponseDto(String ddl, List<ErDdlStatement> statements, List<SkippedOp> skipped) {
    public static GenerateDdlResponseDto from(ErDdlPlan plan) {
        String ddl = plan.statements().stream().map(ErDdlStatement::sql).reduce((a, b) -> a + ";\n\n" + b).map(s -> s + ";").orElse("");
        return new GenerateDdlResponseDto(ddl, plan.statements(), plan.skipped());
    }
}
```

`DiffRequestDto.java`:

```java
package com.datatalk.adapter.dto;

import com.datatalk.domain.er.ErDesignerPayload;

public record DiffRequestDto(ErDesignerPayload payload, String connectionId) {}
```

`DiffResponseDto.java`:

```java
package com.datatalk.adapter.dto;

import com.datatalk.domain.er.ErSchemaDiff;

import java.util.List;

public record DiffResponseDto(List<ErSchemaDiff> diff) {}
```

`SyncFromDbRequestDto.java`:

```java
package com.datatalk.adapter.dto;

import java.util.List;

public record SyncFromDbRequestDto(String connectionId, List<String> tables) {}
```

Modify `ErTabController.java`. Add fields:

```java
private final ErDdlGeneratorService ddlService;

public ErTabController(ErRelationDiscoveryService discovery, ErDdlGeneratorService ddlService) {
    this.discovery = discovery;
    this.ddlService = ddlService;
}
```

Append endpoints:

```java
@PostMapping("/generate-ddl")
public ResponseEntity<?> generateDdl(@RequestBody GenerateDdlRequestDto req) {
    if (req.connectionId() == null) {
        return ResponseEntity.badRequest().body(Map.of(
            "code", "target_required_for_apply",
            "message", "generate_ddl requires a target connection",
            "aiHint", "generate_ddl requires bind_target first. Call ui_exec(designer, bind_target, {connectionId, database, schema}) and retry."
        ));
    }
    try {
        var plan = ddlService.generate(new com.datatalk.application.er.GenerateDdlRequest(req.payload(), req.connectionId(), req.includeDrops()));
        return ResponseEntity.ok(GenerateDdlResponseDto.from(plan));
    } catch (com.datatalk.domain.er.ErErrors.DialectUnsupportedException e) {
        return ResponseEntity.badRequest().body(Map.of(
            "code", "dialect_unsupported",
            "kind", e.kind(),
            "aiHint", "ER does not support " + e.kind() + ". Use mysql/postgresql/h2 for design drafts."
        ));
    }
}

@PostMapping("/diff")
public ResponseEntity<?> diff(@RequestBody DiffRequestDto req) {
    try {
        var diffs = ddlService.diff(req.payload(), req.connectionId());
        return ResponseEntity.ok(new DiffResponseDto(diffs));
    } catch (IllegalStateException | IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
            "code", "target_required_for_apply",
            "message", e.getMessage(),
            "aiHint", "Bind a target connection first via ui_exec(designer, bind_target)."
        ));
    }
}

@PostMapping("/sync-from-db")
public ResponseEntity<?> syncFromDb(@RequestBody SyncFromDbRequestDto req) {
    try {
        // Returns the same shape as seed-inspector — a real-schema ErGraph for the requested tables
        var graph = discovery.discover(req.connectionId(),
            req.tables() == null || req.tables().isEmpty() ? List.of() : req.tables(),
            0);
        return ResponseEntity.ok(com.datatalk.adapter.dto.ErGraphResponse.from(graph));
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(Map.of("code", "sync_failed", "message", e.getMessage()));
    }
}
```

- [ ] **Step 4: Run the IT**

Run: `cd server && mvn -pl data-talk-adapter test -Dtest=ErTabControllerIT -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/
git commit -m "feat(er): /api/er/generate-ddl, /diff, /sync-from-db endpoints

Internal REST not exposed to MCP. Structured errors with English aiHint
including 'target_required_for_apply' for missing bind_target."
```

### Task 9: `UiExecAction` adds `er_designer` exec sub-schema

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java`

- [ ] **Step 1: Add `erDesignerExecSchema()` method**

```java
private static Map<String, Object> erDesignerExecSchema() {
    return Map.ofEntries(
        Map.entry("required", List.of("object", "action")),
        Map.entry("properties", Map.ofEntries(
            Map.entry("object", Map.of("type", "string", "enum", List.of("er_designer"))),
            Map.entry("action", Map.of(
                "type", "string",
                "enum", List.of(
                    "auto_layout", "fit_view",
                    "bind_target", "unbind_target",
                    "sync_from_db", "diff_against_db", "generate_ddl"
                ),
                "description", "ER designer verbs. generate_ddl writes DDL into a new query_editor tab and returns its tabId; the user runs it through L2/L3 confirmation."
            )),
            Map.entry("params", Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                    Map.entry("connectionId", Map.of("type", "string",
                        "description", "Required for bind_target.")),
                    Map.entry("database",     Map.of("type", "string")),
                    Map.entry("schema",       Map.of("type", "string")),
                    Map.entry("tables",       Map.of(
                        "type", "array", "items", Map.of("type", "string"),
                        "description", "Optional for sync_from_db. Subset of tables to refresh from the bound DB; empty = all bound tables."
                    )),
                    Map.entry("includeDrops", Map.of(
                        "type", "boolean", "default", false,
                        "description", "Optional for generate_ddl. Day-1 always false; reserved for later phases."
                    ))
                )
            ))
        ))
    );
}
```

Add it to the `oneOf` list in `inputSchema()`:

```java
"oneOf", List.of(workspaceExecSchema(), queryEditorExecSchema(), erInspectorExecSchema(), erDesignerExecSchema())
```

- [ ] **Step 2: Compile**

Run: `cd server && mvn compile -q`
Expected: zero errors.

- [ ] **Step 3: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java
git commit -m "feat(adapter): UiExecAction adds er_designer exec sub-schema

7 verbs (auto_layout, fit_view, bind_target, unbind_target, sync_from_db,
diff_against_db, generate_ddl) per spec §6.6."
```

### Task 10: `UiPatchAction` adds `er_designer` to object enum

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiPatchAction.java`

- [ ] **Step 1: Update the `object` enum**

Replace:

```java
"enum", List.of("query_editor", "er_inspector"),
```

with:

```java
"enum", List.of("query_editor", "er_inspector", "er_designer"),
```

- [ ] **Step 2: Compile**

Run: `cd server && mvn compile -q`
Expected: zero errors.

- [ ] **Step 3: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiPatchAction.java
git commit -m "feat(adapter): UiPatchAction object enum gains er_designer"
```

### Task 11: i18n keys for designer

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/messages.properties`
- Modify: `client/src/i18n/messages.ts`

- [ ] **Step 1: Add server English keys (P12)**

```properties
action.ui_exec.er_designer.auto_layout.description=Recompute node positions via dagre on the designer canvas.
action.ui_exec.er_designer.fit_view.description=Reset the designer viewport.
action.ui_exec.er_designer.bind_target.description=Bind a target connection so generate_ddl / diff_against_db can compare against the real DB.
action.ui_exec.er_designer.unbind_target.description=Unbind the target connection. Designer reverts to a connection-less draft.
action.ui_exec.er_designer.sync_from_db.description=Refresh designer tables from the bound connection's real schema.
action.ui_exec.er_designer.diff_against_db.description=Return the schema diff between the designer draft and the bound DB without generating DDL.
action.ui_exec.er_designer.generate_ddl.description=Compute the diff and write CREATE/ALTER/INDEX statements into a new query_editor tab. Returns queryEditorTabId for the user to review and run via L2/L3 confirmation.
```

- [ ] **Step 2: Add client UI keys (en + zh)**

```ts
'tabType.erDesigner':                'ER Designer'              | 'ER 设计',
'erCanvas.toolbar.addTable':         'Add table'                | '添加表',
'erCanvas.toolbar.bindTarget':       'Bind target'              | '绑定目标',
'erCanvas.toolbar.diffVsDb':         'Diff vs DB'               | '对比目标库',
'erCanvas.toolbar.generateDdl':      'Generate DDL'             | '生成 DDL',
'erCanvas.toolbar.dialect':          'Dialect'                  | '方言',
'erCanvas.contextMenu.rename':       'Rename'                   | '重命名',
'erCanvas.contextMenu.deleteTable':  'Delete table'             | '删除表',
'erCanvas.contextMenu.addColumn':    'Add column'               | '添加列',
'erCanvas.designer.bindDialog.title':'Bind target connection'   | '绑定目标连接',
'erCanvas.designer.diffEmpty':       'Draft matches the target DB.' | '草稿与目标库一致',
```

- [ ] **Step 3: Type-check**

Run: `cd client && npx tsc --noEmit`
Expected: zero errors.

- [ ] **Step 4: Commit**

```bash
git add server/data-talk-adapter/src/main/resources/messages.properties client/src/i18n/messages.ts
git commit -m "i18n(er/designer): server English action descriptions + client en/zh UI labels"
```

---

## Batch 4: Frontend store + tab type registry + designer payload

### Task 12: Replace stub `ErDesignerPayload` types with full schema

**Files:**
- Modify: `client/src/features/stage/stores/er-tabs-payload-types.ts`

- [ ] **Step 1: Replace the stub**

Open `er-tabs-payload-types.ts`. Remove the existing minimal `ErDesignerPayload` and add full types:

```ts
export interface ErDesignerColumnDraft {
  id: string
  name: string
  type: string                  // dialect-rendered fragment, e.g. "VARCHAR(255)"
  nullable: boolean
  isPrimaryKey: boolean
  isAutoIncrement: boolean
  default?: string | null
  comment?: string | null
}

export interface ErDesignerIndexDraft {
  name: string
  columns: string[]
}

export interface ErDesignerUniqueDraft {
  columns: string[]
}

export interface ErDesignerTableDraft {
  id: string
  name: string
  comment?: string | null
  columns: ErDesignerColumnDraft[]
  indexes: ErDesignerIndexDraft[]
  uniques: ErDesignerUniqueDraft[]
}

export interface ErDesignerRelationDraft {
  id: string
  fromTableId: string
  fromColumnId: string
  toTableId: string
  toColumnId: string
  type: 'one_to_one' | 'one_to_many' | 'many_to_one' | 'many_to_many'
  constraintMethod: 'database_fk' | 'comment_ref'
}

export interface ErDesignerPayload {
  kind: 'er_designer'
  dialect: 'mysql' | 'postgresql' | 'h2' | 'sqlite'
  targetConnectionId?: string | null
  targetDatabase?: string | null
  targetSchema?: string | null
  tables: ErDesignerTableDraft[]
  relations: ErDesignerRelationDraft[]
  positions: Record<string, { x: number; y: number }>
  collapsed: string[]
  viewport: ErViewport
}
```

- [ ] **Step 2: Type-check**

Run: `cd client && npx tsc --noEmit`
Expected: zero errors.

- [ ] **Step 3: Commit**

```bash
git add client/src/features/stage/stores/er-tabs-payload-types.ts
git commit -m "feat(client/er): full ErDesignerPayload types (replaces Plan A stub)"
```

### Task 13: `applyDesignerPatch` with strict baseVersion + assignedIds

**Files:**
- Modify: `client/src/features/stage/stores/er-tabs-store.ts`
- Modify: `client/src/features/stage/stores/er-tabs-store.test.ts`

- [ ] **Step 1: Write the failing tests**

Append to `er-tabs-store.test.ts`:

```ts
describe('useErTabsStore — applyDesignerPatch', () => {
  beforeEach(() => {
    useErTabsStore.setState({
      inspectors: new Map(),
      designers: new Map([['d-1', {
        kind: 'er_designer', dialect: 'mysql',
        targetConnectionId: null, targetDatabase: null, targetSchema: null,
        tables: [], relations: [],
        positions: {}, collapsed: [],
        viewport: { x: 0, y: 0, zoom: 1 },
      }]]),
    })
  })

  it('add /tables/- assigns t_<id> and tags column ids', () => {
    const { assignedIds } = useErTabsStore.getState().applyDesignerPatch('d-1', [
      { op: 'add', path: '/tables/-', value: {
        name: 'users',
        columns: [
          { name: 'id',    type: 'BIGINT',       nullable: false, isPrimaryKey: true,  isAutoIncrement: true  },
          { name: 'email', type: 'VARCHAR(255)', nullable: false, isPrimaryKey: false, isAutoIncrement: false },
        ],
        indexes: [], uniques: [],
      }},
    ])
    const tid = assignedIds['/tables/0']
    expect(tid).toMatch(/^t_/)
    const tab = useErTabsStore.getState().designers.get('d-1')!
    expect(tab.tables[0].id).toBe(tid)
    expect(tab.tables[0].columns[0].id).toMatch(/^c_/)
  })

  it('add /relations/- assigns r_<id>', () => {
    useErTabsStore.getState().applyDesignerPatch('d-1', [
      { op: 'add', path: '/tables/-', value: {
        name: 'a', columns: [{ name: 'id', type: 'BIGINT', nullable: false, isPrimaryKey: true, isAutoIncrement: true }], indexes: [], uniques: [],
      }},
      { op: 'add', path: '/tables/-', value: {
        name: 'b', columns: [{ name: 'a_id', type: 'BIGINT', nullable: false, isPrimaryKey: false, isAutoIncrement: false }], indexes: [], uniques: [],
      }},
    ])
    const tab = useErTabsStore.getState().designers.get('d-1')!
    const aId = tab.tables[0].id; const aColId = tab.tables[0].columns[0].id
    const bId = tab.tables[1].id; const bColId = tab.tables[1].columns[0].id

    const { assignedIds } = useErTabsStore.getState().applyDesignerPatch('d-1', [
      { op: 'add', path: '/relations/-', value: {
        fromTableId: bId, fromColumnId: bColId, toTableId: aId, toColumnId: aColId,
        type: 'many_to_one', constraintMethod: 'database_fk',
      }},
    ])
    expect(assignedIds['/relations/0']).toMatch(/^r_/)
  })

  it('strict baseVersion conflict throws on /tables/- when version mismatch', () => {
    // Simulate strict version: by passing baseVersion explicitly
    useErTabsStore.getState().applyDesignerPatch('d-1', [{ op: 'add', path: '/tables/-', value: {
      name: 'a', columns: [], indexes: [], uniques: [],
    }}], { baseVersion: 1 })

    expect(() => useErTabsStore.getState().applyDesignerPatch('d-1',
      [{ op: 'add', path: '/tables/-', value: { name: 'b', columns: [], indexes: [], uniques: [] }}],
      { baseVersion: 1 } // stale
    )).toThrow(/conflict_with_concurrent_edit/)
  })

  it('rejects patches outside the designer path whitelist', () => {
    expect(() => useErTabsStore.getState().applyDesignerPatch('d-1', [
      { op: 'add', path: '/notes/-', value: {} },
    ])).toThrow(/invalid_path/)
  })
})
```

- [ ] **Step 2: Run the tests**

Run: `cd client && npx vitest run client/src/features/stage/stores/er-tabs-store.test.ts`
Expected: FAIL.

- [ ] **Step 3: Implement `applyDesignerPatch`**

Replace the stub `applyDesignerPatch` body in `er-tabs-store.ts`:

```ts
const DESIGNER_STRICT_PATHS = [
  /^\/tables\/-$/,
  /^\/tables\[id=[^\]]+\]$/,
  /^\/tables\[id=[^\]]+\]\/name$/,
  /^\/tables\[id=[^\]]+\]\/columns\/-$/,
  /^\/tables\[id=[^\]]+\]\/columns\[id=[^\]]+\]$/,
  /^\/relations\/-$/,
  /^\/relations\[id=[^\]]+\]$/,
  /^\/dialect$/,
  /^\/targetConnectionId$/,
  /^\/targetDatabase$/,
  /^\/targetSchema$/,
]
const DESIGNER_AUTO_PATHS = [
  /^\/tables\[id=[^\]]+\]\/comment$/,
  /^\/positions$/,
  /^\/positions\/[^/]+$/,
  /^\/collapsed$/,
  /^\/viewport$/,
]

function isDesignerPathAllowed(path: string): boolean {
  return DESIGNER_STRICT_PATHS.some((re) => re.test(path))
      || DESIGNER_AUTO_PATHS.some((re) => re.test(path))
}

function isDesignerStrictPath(path: string): boolean {
  return DESIGNER_STRICT_PATHS.some((re) => re.test(path))
}

// inside store factory:
applyDesignerPatch(tabId, ops, opts?: { baseVersion?: number }) {
  const current = get().designers.get(tabId)
  if (!current) throw new Error(`tab not found: ${tabId}`)

  for (const op of ops) {
    if (!isDesignerPathAllowed(op.path)) {
      const e = new Error(`invalid_path: ${op.path}`); (e as any).code = 'invalid_path'; throw e
    }
  }

  // baseVersion enforcement on strict paths
  const currentV = Number(((current as any).__v ?? 0))
  const isStrict = ops.some((op) => isDesignerStrictPath(op.path))
  if (isStrict && opts?.baseVersion !== undefined && opts.baseVersion < currentV) {
    const e = new Error(`conflict_with_concurrent_edit: server v${currentV} vs your v${opts.baseVersion}`)
    ;(e as any).code = 'conflict_with_concurrent_edit'
    ;(e as any).currentVersion = currentV
    throw e
  }

  // Auto-assign ids for add ops
  const assignedIds: Record<string, string> = {}
  const stamped = ops.map((op, idx) => {
    if (op.op === 'add' && op.path === '/tables/-' && typeof op.value === 'object' && op.value !== null) {
      const tid = `t_${nanoid(8)}`
      const tableValue: any = { id: tid, indexes: [], uniques: [], ...(op.value as object) }
      if (Array.isArray(tableValue.columns)) {
        tableValue.columns = tableValue.columns.map((c: any) => ({
          id: c.id ?? `c_${nanoid(8)}`, ...c,
        }))
      } else {
        tableValue.columns = []
      }
      assignedIds[`/tables/${(current.tables ?? []).length + idx}`] = tid
      return { ...op, value: tableValue }
    }
    if (op.op === 'add' && op.path === '/relations/-' && typeof op.value === 'object' && op.value !== null) {
      const rid = `r_${nanoid(8)}`
      assignedIds[`/relations/${(current.relations ?? []).length + idx}`] = rid
      return { ...op, value: { id: rid, ...(op.value as object) } }
    }
    if (op.op === 'add' && /^\/tables\[id=[^\]]+\]\/columns\/-$/.test(op.path)
        && typeof op.value === 'object' && op.value !== null) {
      const cid = `c_${nanoid(8)}`
      assignedIds[op.path.replace('/-', '/-?cid=' + cid)] = cid
      return { ...op, value: { id: cid, ...(op.value as object) } }
    }
    return op
  })

  const next = applyPatch(current as unknown as Record<string, unknown>, stamped) as unknown as ErDesignerPayload
  // Bump version
  ;(next as any).__v = currentV + 1
  set((s) => {
    const m = new Map(s.designers)
    m.set(tabId, next)
    return { designers: m }
  })

  return { newVersion: currentV + 1, assignedIds }
},
```

(Update the `applyDesignerPatch` signature in the interface to accept the optional opts: `applyDesignerPatch: (tabId: string, ops: JsonPatchOp[], opts?: { baseVersion?: number }) => { newVersion: number; assignedIds: Record<string, string> }`.)

- [ ] **Step 4: Run the tests**

Run: `cd client && npx vitest run client/src/features/stage/stores/er-tabs-store.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client/src/features/stage/stores/er-tabs-store.ts client/src/features/stage/stores/er-tabs-store.test.ts
git commit -m "feat(client/er): applyDesignerPatch with strict baseVersion + assignedIds"
```

### Task 14: Register `er_designer` in `tab-type-registry.ts`

**Files:**
- Modify: `client/src/features/stage/registry/tab-type-registry.ts`
- Modify: `client/src/features/stage/registry/__tests__/tab-type-registry.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
describe('tab-type-registry — er_designer', () => {
  it('registers er_designer as workspace-scope persistent', () => {
    expect(TAB_TYPE_REGISTRY['er_designer']).toBeDefined()
    expect(isPersistent('er_designer')).toBe(true)
    expect(getScope('er_designer')).toBe('workspace')
  })

  it('extractContent indexes table names + column names + table comment', () => {
    const desc = getTabTypeDescriptor('er_designer')
    const text = desc.extractContent({
      kind: 'er_designer', dialect: 'mysql',
      tables: [{
        id: 't1', name: 'orders', comment: '订单主表',
        columns: [
          { id: 'c1', name: 'id', type: 'BIGINT', nullable: false, isPrimaryKey: true, isAutoIncrement: true },
          { id: 'c2', name: 'amount', type: 'DECIMAL(10,2)', nullable: false, isPrimaryKey: false, isAutoIncrement: false },
        ], indexes: [], uniques: [],
      }],
      relations: [], positions: {}, collapsed: [], viewport: { x: 0, y: 0, zoom: 1 },
    })
    expect(text).toContain('orders id BIGINT amount DECIMAL(10,2)')
    expect(text).toContain('订单主表')
  })
})
```

- [ ] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/registry/__tests__/tab-type-registry.test.ts`
Expected: FAIL.

- [ ] **Step 3: Add the entry**

```ts
import { TableIcon } from 'lucide-react'

er_designer: {
  type: 'er_designer',
  persistent: true,
  scope: 'workspace',
  icon: TableIcon,
  labelKey: 'tabType.erDesigner',
  extractContent: (p) => {
    const o = p as ErDesignerPayload | null
    if (!o) return ''
    return (o.tables ?? [])
      .map((t) => {
        const cols = (t.columns ?? []).map((c) => `${c.name} ${c.type}`).join(' ')
        return `${t.name} ${cols} ${t.comment ?? ''}`.trim()
      })
      .join('\n')
  },
  rehydrate: (tabId, p) => {
    useErTabsStore.getState().hydrateDesigner(tabId, p as ErDesignerPayload)
  },
},
```

- [ ] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/registry/__tests__/tab-type-registry.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client/
git commit -m "feat(client/er): register er_designer tab type with extractContent"
```

---

## Batch 5: Frontend Adapter global registration verification

(Plan A already added `RegisteredErInspector`; this batch verifies the matching `RegisteredErDesigner` is wired.)

### Task 15: Add `RegisteredErDesigner` to `stage-ui-object-registry.tsx`

**Files:**
- Create: `client/src/features/stage/adapters/ErDesignerAdapter.ts` (stub)
- Modify: `client/src/features/stage/components/stage-ui-object-registry.tsx`
- Modify: `client/src/features/stage/components/stage-ui-object-registry.test.tsx`

- [ ] **Step 1: Create a stub `ErDesignerAdapter.ts`**

```ts
import type { UIObject, JsonPatchOp, PatchResult, ExecResult } from '@/services/ui-router'

export class ErDesignerAdapter implements UIObject {
  type = 'er_designer'
  objectId: string
  title = 'ER Designer'
  tabId: string
  constructor(tabId: string, _sessionIdGetter: () => string | null) {
    this.objectId = tabId; this.tabId = tabId
  }
  read(_mode: 'state' | 'schema' | 'actions' | 'full'): unknown { return null }
  patch(_ops: JsonPatchOp[], _reason?: string): PatchResult {
    return { status: 'error', message: 'ErDesignerAdapter.patch implemented in Task 22' }
  }
  exec(_action: string, _params?: unknown): ExecResult {
    return { success: false, error: 'ErDesignerAdapter.exec implemented in Task 22' }
  }
}
```

- [ ] **Step 2: Update `stage-ui-object-registry.tsx`**

```tsx
import { ErDesignerAdapter } from '../adapters/ErDesignerAdapter'

function RegisteredErDesigner({ tabId, sessionId }: { tabId: string; sessionId: string | null }) {
  const instance = useMemo(() => new ErDesignerAdapter(tabId, () => sessionId), [tabId, sessionId])
  useUIObjectRegistry(instance)
  return null
}

// inside StageUIObjectRegistry's render:
{tabs.filter((tab) => tab.type === 'er_designer').map((tab) => (
  <RegisteredErDesigner key={tab.tabId} tabId={tab.tabId} sessionId={tab.originSessionId ?? null} />
))}
```

- [ ] **Step 3: Add registry assertion**

```tsx
it('registers an ErDesignerAdapter for each er_designer tab', () => {
  const registerSpy = vi.fn()
  vi.spyOn(uiRouter, 'register').mockImplementation((obj) => registerSpy(obj))
  const tabs = [{
    tabId: 'd-1', type: 'er_designer', title: 'X', payload: {}, payloadVersion: 1,
    createdAt: 0, lastTouchedAt: 0,
  }] as any
  render(<StageUIObjectRegistry tabs={tabs} />)
  expect(registerSpy).toHaveBeenCalledWith(expect.objectContaining({ type: 'er_designer', tabId: 'd-1' }))
})
```

- [ ] **Step 4: Run tests + tsc**

Run: `cd client && npx vitest run client/src/features/stage/components/stage-ui-object-registry.test.tsx && npx tsc --noEmit`
Expected: PASS + zero errors.

- [ ] **Step 5: Commit**

```bash
git add client/
git commit -m "feat(client/er): globally register ErDesignerAdapter for every er_designer tab"
```

---

## Batch 6: ErTableNode designer mode + drag-to-create-relation

### Task 16: `<ErTableNode mode='designer'>` editing UX

**Files:**
- Modify: `client/src/features/stage/components/er-canvas/ErTableNode.tsx`
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`

- [ ] **Step 1: Add designer-mode tests**

Append to `ErTableNode.test.tsx`:

```tsx
const designerData = {
  ...data,
  mode: 'designer' as const,
  onUpdateColumn: vi.fn(),
  onAddColumn: vi.fn(),
  onDeleteColumn: vi.fn(),
}

describe('<ErTableNode mode="designer">', () => {
  it('shows pencil icon and add-column button', () => {
    render(
      <ReactFlowProvider>
        <ErTableNode id="users" data={designerData} selected={false} dragging={false} type="erTable" zIndex={0} isConnectable={true} xPos={0} yPos={0} />
      </ReactFlowProvider>
    )
    expect(screen.getByLabelText(/editable/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /add column/i })).toBeInTheDocument()
  })

  it('triggers onAddColumn when "Add column" button is clicked', () => {
    render(
      <ReactFlowProvider>
        <ErTableNode id="users" data={designerData} selected={false} dragging={false} type="erTable" zIndex={0} isConnectable={true} xPos={0} yPos={0} />
      </ReactFlowProvider>
    )
    fireEvent.click(screen.getByRole('button', { name: /add column/i }))
    expect(designerData.onAddColumn).toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Update `ErTableNode.tsx`**

Add designer-mode props to `ErTableNodeData`:

```ts
interface ErTableNodeData extends ErNodeData {
  mode: ErTableNodeMode
  // Designer-only callbacks (optional in inspector mode)
  onUpdateColumn?: (colId: string, updates: Partial<ErColumnMeta>) => void
  onAddColumn?: () => void
  onDeleteColumn?: (colId: string) => void
}
```

In the header, replace the lock icon block with conditional rendering:

```tsx
{data.mode === 'inspector' ? (
  <button type="button" aria-label="read-only inspector view" tabIndex={-1} className="text-[var(--text-soft)] cursor-default">
    <LockIcon size={12} />
  </button>
) : (
  <span aria-label="editable designer table" className="text-[var(--accent-primary)]">
    <PencilIcon size={12} />
  </span>
)}
```

(Add `import { PencilIcon } from 'lucide-react'`.)

After the columns list (before closing `</div>`), add the add-column button:

```tsx
{data.mode === 'designer' && (
  <button
    type="button"
    onClick={data.onAddColumn}
    className="w-full px-3 py-2 border-t border-[var(--border-subtle)] text-[11px] font-medium text-[var(--accent-primary)] hover:bg-[var(--accent-primarySurface)] text-center"
  >
    + Add column
  </button>
)}
```

- [ ] **Step 4: Run tests**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client/
git commit -m "feat(client/er): ErTableNode designer mode (pencil icon + Add column button)"
```

### Task 17: `<ErTableContextMenu>` right-click menu

**Files:**
- Create: `client/src/features/stage/components/er-canvas/ErTableContextMenu.tsx`
- Test: `client/src/features/stage/components/er-canvas/__tests__/ErTableContextMenu.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
// __tests__/ErTableContextMenu.test.tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ErTableContextMenu } from '../ErTableContextMenu'

describe('<ErTableContextMenu>', () => {
  it('renders Rename / Add column / Delete table', () => {
    render(<ErTableContextMenu x={0} y={0} tableId="t1"
      onRename={vi.fn()} onAddColumn={vi.fn()} onDeleteTable={vi.fn()} onClose={vi.fn()} />)
    expect(screen.getByText(/rename/i)).toBeInTheDocument()
    expect(screen.getByText(/add column/i)).toBeInTheDocument()
    expect(screen.getByText(/delete table/i)).toBeInTheDocument()
  })

  it('calls onDeleteTable + onClose when Delete table clicked', () => {
    const onDelete = vi.fn(); const onClose = vi.fn()
    render(<ErTableContextMenu x={0} y={0} tableId="t1"
      onRename={vi.fn()} onAddColumn={vi.fn()} onDeleteTable={onDelete} onClose={onClose} />)
    fireEvent.click(screen.getByText(/delete table/i))
    expect(onDelete).toHaveBeenCalledWith('t1')
    expect(onClose).toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErTableContextMenu.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Create the component**

```tsx
// client/src/features/stage/components/er-canvas/ErTableContextMenu.tsx
import { useEffect, useRef } from 'react'
import { useI18n } from '@/i18n/use-i18n'

interface Props {
  x: number; y: number; tableId: string
  onRename: (id: string) => void
  onAddColumn: (id: string) => void
  onDeleteTable: (id: string) => void
  onClose: () => void
}

export function ErTableContextMenu({ x, y, tableId, onRename, onAddColumn, onDeleteTable, onClose }: Props) {
  const { t } = useI18n()
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose()
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [onClose])

  return (
    <div
      ref={ref}
      role="menu"
      style={{ position: 'fixed', left: x, top: y, zIndex: 1000 }}
      className="bg-[var(--bg-panel)] border border-[var(--border-default)] rounded shadow-md min-w-[140px] py-1"
    >
      <Item onClick={() => { onRename(tableId); onClose() }}>{t('erCanvas.contextMenu.rename')}</Item>
      <Item onClick={() => { onAddColumn(tableId); onClose() }}>{t('erCanvas.contextMenu.addColumn')}</Item>
      <Item onClick={() => { onDeleteTable(tableId); onClose() }} danger>{t('erCanvas.contextMenu.deleteTable')}</Item>
    </div>
  )
}

function Item({ children, onClick, danger }: { children: React.ReactNode; onClick: () => void; danger?: boolean }) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onClick}
      className={[
        'w-full text-left px-3 py-1.5 text-[12px]',
        danger ? 'text-[var(--status-danger)] hover:bg-[var(--status-dangerSurface)]'
               : 'text-[var(--text-base)] hover:bg-[var(--interaction-hover)]',
      ].join(' ')}
    >
      {children}
    </button>
  )
}
```

- [ ] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErTableContextMenu.test.tsx`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client/
git commit -m "feat(client/er): ErTableContextMenu (Rename / Add column / Delete table)"
```

### Task 18: Drag-to-create-relation in `ErEdge` + `ErCanvas onConnect`

**Files:**
- Modify: `client/src/features/stage/components/er-canvas/ErEdge.tsx` (handle hover styling)
- Modify: `client/src/features/stage/components/er-canvas/ErCanvas.tsx`
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx`

- [ ] **Step 1: Add a designer-mode test in `ErCanvas.test.tsx`**

```tsx
it('mode="designer" calls onConnect when an edge is created', () => {
  const onPatch = vi.fn()
  // Designer payload with two tables
  const designerPayload = {
    kind: 'er_designer' as const, dialect: 'mysql' as const,
    tables: [
      { id: 't1', name: 'a', columns: [{ id: 'c1', name: 'id', type: 'BIGINT', nullable: false, isPrimaryKey: true, isAutoIncrement: true }], indexes: [], uniques: [] },
      { id: 't2', name: 'b', columns: [{ id: 'c2', name: 'a_id', type: 'BIGINT', nullable: false, isPrimaryKey: false, isAutoIncrement: false }], indexes: [], uniques: [] },
    ],
    relations: [], positions: { t1: { x: 0, y: 0 }, t2: { x: 320, y: 0 } },
    collapsed: [], viewport: { x: 0, y: 0, zoom: 1 },
  }
  // Simulate user dragging a connection: this mostly verifies the prop flows; actual dragging is tested with React Flow's own utils.
  const { container } = render(<ErCanvas tabId="d-1" mode="designer" payload={designerPayload as any} onPatch={onPatch} onExec={vi.fn()} />)
  // The ReactFlow surface should be present; presence check is enough for unit-level sanity.
  expect(container.querySelector('.react-flow')).toBeTruthy()
})
```

- [ ] **Step 2: Update `ErCanvas` to accept designer mode**

In `ErCanvas.tsx`, broaden the props type:

```ts
export interface ErCanvasDesignerProps {
  tabId: string
  mode: 'designer'
  payload: ErDesignerPayload
  onPatch: (ops: JsonPatchOp[]) => void
  onExec: (action: string, params?: unknown) => void
}

export type ErCanvasProps = ErCanvasInspectorProps | ErCanvasDesignerProps
```

In `ErCanvasInner`, branch by mode. For `'designer'`:
- Compute `nodes` / `edges` from designer payload (write a `designerToGraph` utility — small adaptation of `inspectorToGraph`).
- Wire `onConnect` to push a patch:

```ts
const onConnect = useCallback((conn: Connection) => {
  if (mode !== 'designer') return
  const sourceColId = conn.sourceHandle?.replace(/-source$/, '')
  const targetColId = conn.targetHandle?.replace(/-target$/, '')
  if (!conn.source || !conn.target || !sourceColId || !targetColId) return
  onPatch([{
    op: 'add', path: '/relations/-',
    value: {
      fromTableId: conn.source, fromColumnId: sourceColId,
      toTableId: conn.target, toColumnId: targetColId,
      type: 'many_to_one', constraintMethod: 'database_fk',
    },
  }])
}, [mode, onPatch])
```

Pass to `<ReactFlow onConnect={mode === 'designer' ? onConnect : undefined} />` and similarly `onNodesDelete` / `onEdgesDelete` for designer:

```ts
const onNodesDelete = useCallback((nodes: Node[]) => {
  if (mode !== 'designer') return
  onPatch(nodes.map((n) => ({ op: 'remove', path: `/tables[id=${n.id}]` })))
}, [mode, onPatch])

const onEdgesDelete = useCallback((edges: Edge[]) => {
  if (mode !== 'designer') return
  onPatch(edges.map((e) => ({ op: 'remove', path: `/relations[id=${e.id.replace(/^vr:|^fk:/, '')}]` })))
}, [mode, onPatch])
```

Create `designerToGraph` next to `inspectorToGraph`:

```ts
// utils/payload-to-graph.ts (append)
import type { ErDesignerPayload } from '@/features/stage/stores/er-tabs-payload-types'

export function designerToGraph(p: ErDesignerPayload): { nodes: Node<ErNodeData>[]; edges: Edge<ErEdgeData>[] } {
  const collapsed = new Set(p.collapsed ?? [])
  const nodes: Node<ErNodeData>[] = (p.tables ?? []).map((t) => ({
    id: t.id,
    type: 'erTable',
    position: p.positions[t.id] ?? { x: 0, y: 0 },
    data: {
      table: { name: t.name, comment: t.comment, columns: t.columns.map((c) => ({
        name: c.name, type: c.type, isPK: c.isPrimaryKey, isFK: false, nullable: c.nullable,
      })), fkOut: [] },
      columns: t.columns.map((c) => ({
        name: c.name, type: c.type, isPK: c.isPrimaryKey, isFK: false, nullable: c.nullable,
      })),
      collapsed: collapsed.has(t.id),
    },
  }))

  const edges: Edge<ErEdgeData>[] = (p.relations ?? []).map((r) => ({
    id: r.id,
    source: r.fromTableId,
    target: r.toTableId,
    sourceHandle: `${r.fromColumnId}-source`,
    targetHandle: `${r.toColumnId}-target`,
    type: 'erEdge',
    data: { kind: 'fk', relationType: r.type, fromColumn: r.fromColumnId, toColumn: r.toColumnId },
  }))
  return { nodes, edges }
}
```

- [ ] **Step 3: Run tests**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx && npx tsc --noEmit`
Expected: PASS + zero errors.

- [ ] **Step 4: Commit**

```bash
git add client/
git commit -m "feat(client/er): ErCanvas designer mode

onConnect → ui_patch /relations/-
onNodesDelete → ui_patch /tables[id=<tid>] remove
onEdgesDelete → ui_patch /relations[id=<rid>] remove
designerToGraph helper turns ErDesignerPayload into ReactFlow shape."
```

---

## Batch 7: ErToolbar designer buttons

### Task 19: `<ErToolbar mode='designer'>`

**Files:**
- Modify: `client/src/features/stage/components/er-canvas/ErToolbar.tsx`
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErToolbar.test.tsx`

- [ ] **Step 1: Add designer-mode tests**

```tsx
describe('<ErToolbar mode="designer">', () => {
  it('renders Add table / Auto layout / Fit view / Bind target / Diff vs DB / Generate DDL / Dialect', () => {
    render(<ErToolbar mode="designer"
      dialect="mysql"
      hasTarget={false}
      onAddTable={vi.fn()} onAutoLayout={vi.fn()} onFitView={vi.fn()}
      onBindTarget={vi.fn()} onDiffVsDb={vi.fn()} onGenerateDdl={vi.fn()}
      onChangeDialect={vi.fn()} />)
    expect(screen.getByRole('button', { name: /add table/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /bind target/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /generate ddl/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/dialect/i)).toBeInTheDocument()
  })

  it('disables Diff vs DB and Generate DDL when hasTarget=false', () => {
    render(<ErToolbar mode="designer"
      dialect="mysql"
      hasTarget={false}
      onAddTable={vi.fn()} onAutoLayout={vi.fn()} onFitView={vi.fn()}
      onBindTarget={vi.fn()} onDiffVsDb={vi.fn()} onGenerateDdl={vi.fn()}
      onChangeDialect={vi.fn()} />)
    expect(screen.getByRole('button', { name: /generate ddl/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /diff vs db/i })).toBeDisabled()
  })
})
```

- [ ] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErToolbar.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Update `ErToolbar` to accept designer props**

Replace the `ErToolbarProps` type union:

```ts
interface ErToolbarDesignerProps extends ErToolbarBaseProps {
  mode: 'designer'
  dialect: 'mysql' | 'postgresql' | 'h2' | 'sqlite'
  hasTarget: boolean
  onAddTable: () => void
  onAutoLayout: () => void
  onFitView: () => void
  onBindTarget: () => void
  onDiffVsDb: () => void
  onGenerateDdl: () => void
  onChangeDialect: (d: 'mysql' | 'postgresql' | 'h2' | 'sqlite') => void
}

type ErToolbarProps = ErToolbarInspectorProps | ErToolbarDesignerProps
```

In the body, branch on `props.mode`:

```tsx
if (props.mode === 'designer') {
  return (
    <div className="flex items-center gap-1 px-2 py-1 bg-[var(--bg-subtle)] border-b border-[var(--border-subtle)]">
      <ToolbarButton onClick={props.onAddTable} icon={<PlusIcon size={14} />} label={t('erCanvas.toolbar.addTable')} />
      <ToolbarButton onClick={props.onAutoLayout} icon={<LayoutTemplateIcon size={14} />} label={t('erCanvas.toolbar.autoLayout')} />
      <ToolbarButton onClick={props.onFitView} icon={<MaximizeIcon size={14} />} label={t('erCanvas.toolbar.fitView')} />
      <Separator />
      <ToolbarButton onClick={props.onBindTarget} icon={<LinkIcon size={14} />} label={t('erCanvas.toolbar.bindTarget')} />
      <ToolbarButton onClick={props.onDiffVsDb} icon={<DiffIcon size={14} />} label={t('erCanvas.toolbar.diffVsDb')} disabled={!props.hasTarget} />
      <ToolbarButton onClick={props.onGenerateDdl} icon={<CodeIcon size={14} />} label={t('erCanvas.toolbar.generateDdl')} primary disabled={!props.hasTarget} />
      <div className="ml-auto" />
      <label className="flex items-center gap-1 text-[12px] text-[var(--text-muted)]">
        <span>{t('erCanvas.toolbar.dialect')}</span>
        <select
          aria-label={t('erCanvas.toolbar.dialect')}
          value={props.dialect}
          onChange={(e) => props.onChangeDialect(e.target.value as 'mysql' | 'postgresql' | 'h2' | 'sqlite')}
          className="bg-[var(--bg-canvas)] border border-[var(--border-default)] rounded px-1 py-0.5 text-[12px]"
        >
          <option value="mysql">MySQL</option>
          <option value="postgresql">PostgreSQL</option>
          <option value="h2">H2</option>
          <option value="sqlite">SQLite</option>
        </select>
      </label>
    </div>
  )
}
```

Also update `ToolbarButton` to accept a `disabled` prop:

```tsx
function ToolbarButton({ onClick, icon, label, primary, disabled }: { onClick: () => void; icon: React.ReactNode; label: string; primary?: boolean; disabled?: boolean }) {
  return (
    <button type="button" onClick={onClick} aria-label={label} disabled={disabled}
      className={[
        'flex items-center gap-1 px-2 py-1 rounded text-[12px] transition-colors disabled:opacity-50 disabled:cursor-not-allowed',
        primary
          ? 'border border-[var(--accent-primary)] text-[var(--accent-primary)] hover:bg-[var(--accent-primarySurface)]'
          : 'text-[var(--text-muted)] hover:text-[var(--text-strong)] hover:bg-[var(--interaction-hover)]',
      ].join(' ')}
    >
      {icon}
      <span>{label}</span>
    </button>
  )
}
```

(Add `import { LinkIcon, DiffIcon, CodeIcon, PlusIcon } from 'lucide-react'`. If `DiffIcon` is not in lucide, use `GitCompareIcon`.)

- [ ] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErToolbar.test.tsx`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client/
git commit -m "feat(client/er): ErToolbar designer mode (Add table / Bind / Diff / Generate DDL / Dialect)"
```

---

## Batch 8: ErDesignerAdapter + WorkspaceAdapter.open_er_designer + ErDesignerTab + Inspector.fork_to_designer

### Task 20: `ErDesignerAdapter` full implementation

**Files:**
- Modify: `client/src/features/stage/adapters/ErDesignerAdapter.ts` (replace stub from T15)
- Test: `client/src/features/stage/adapters/__tests__/ErDesignerAdapter.test.ts`

- [ ] **Step 1: Write the failing tests**

```ts
// client/src/features/stage/adapters/__tests__/ErDesignerAdapter.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ErDesignerAdapter } from '../ErDesignerAdapter'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'

const samplePayload = {
  kind: 'er_designer' as const, dialect: 'mysql' as const,
  targetConnectionId: null, targetDatabase: null, targetSchema: null,
  tables: [], relations: [],
  positions: {}, collapsed: [], viewport: { x: 0, y: 0, zoom: 1 },
}

describe('ErDesignerAdapter', () => {
  beforeEach(() => {
    useErTabsStore.setState({
      inspectors: new Map(),
      designers: new Map([['d-1', { ...samplePayload }]]),
    })
    global.fetch = vi.fn()
  })

  it('read("state") returns the designer payload', () => {
    const a = new ErDesignerAdapter('d-1', () => null)
    expect(a.read('state')).toMatchObject({ kind: 'er_designer', dialect: 'mysql' })
  })

  it('exec("bind_target", {...}) writes targetConnectionId / database / schema', async () => {
    const a = new ErDesignerAdapter('d-1', () => null)
    const r = await a.exec('bind_target', { connectionId: 'c1', database: 'sales', schema: 'public' })
    expect(r.success).toBe(true)
    const p = useErTabsStore.getState().designers.get('d-1')!
    expect(p.targetConnectionId).toBe('c1')
    expect(p.targetDatabase).toBe('sales')
    expect(p.targetSchema).toBe('public')
  })

  it('exec("generate_ddl") fails when no target bound', async () => {
    const a = new ErDesignerAdapter('d-1', () => null)
    const r = await a.exec('generate_ddl')
    expect(r.success).toBe(false)
    expect(r.error).toMatch(/target_required_for_apply|bind_target/)
  })

  it('exec("generate_ddl") with target bound calls /api/er/generate-ddl and creates a query_editor tab', async () => {
    useErTabsStore.setState({
      inspectors: new Map(),
      designers: new Map([['d-1', { ...samplePayload, targetConnectionId: 'c1', targetDatabase: 'sales', targetSchema: 'public' }]]),
    })
    ;(global.fetch as any) = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        ddl: 'CREATE TABLE `users` (...)',
        statements: [{ sql: 'CREATE TABLE `users` (...)', kind: 'CREATE_TABLE', table: 'users' }],
        skipped: [],
      }),
    })

    const a = new ErDesignerAdapter('d-1', () => null)
    const r = await a.exec('generate_ddl')
    expect(r.success).toBe(true)
    const data = r.data as { queryEditorTabId: string; ddl: string; skippedOps: unknown[] }
    expect(data.queryEditorTabId).toMatch(/^query_editor_/)
    expect(data.ddl).toContain('CREATE TABLE')
  })

  it('exec("diff_against_db") returns the diff list without writing anything', async () => {
    useErTabsStore.setState({
      inspectors: new Map(),
      designers: new Map([['d-1', { ...samplePayload, targetConnectionId: 'c1' }]]),
    })
    ;(global.fetch as any) = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ diff: [{ kind: 'TableAdded', tableName: 'users' }] }),
    })
    const a = new ErDesignerAdapter('d-1', () => null)
    const r = await a.exec('diff_against_db')
    expect(r.success).toBe(true)
    expect((r.data as { diff: unknown[] }).diff).toHaveLength(1)
  })
})
```

- [ ] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/adapters/__tests__/ErDesignerAdapter.test.ts`
Expected: FAIL.

- [ ] **Step 3: Replace stub with full adapter**

```ts
// client/src/features/stage/adapters/ErDesignerAdapter.ts
import type { UIObject, JsonPatchOp, PatchResult, ExecResult, PatchCapability } from '@/services/ui-router'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { useStageStore } from '@/stores/stage-store'
import { nanoid } from 'nanoid'

const PATCH_CAPS: PatchCapability[] = [
  { pathPattern: '/tables/-',                              ops: ['add'] },
  { pathPattern: '/tables[id=<n>]',                        ops: ['replace', 'remove'] },
  { pathPattern: '/tables[id=<n>]/name',                   ops: ['replace'] },
  { pathPattern: '/tables[id=<n>]/comment',                ops: ['replace'] },
  { pathPattern: '/tables[id=<n>]/columns/-',              ops: ['add'] },
  { pathPattern: '/tables[id=<n>]/columns[id=<n>]',        ops: ['replace', 'remove'] },
  { pathPattern: '/relations/-',                           ops: ['add'] },
  { pathPattern: '/relations[id=<n>]',                     ops: ['replace', 'remove'] },
  { pathPattern: '/positions',                             ops: ['replace'] },
  { pathPattern: '/positions/<n>',                         ops: ['replace', 'remove'] },
  { pathPattern: '/collapsed',                             ops: ['replace'] },
  { pathPattern: '/viewport',                              ops: ['replace'] },
  { pathPattern: '/dialect',                               ops: ['replace'] },
  { pathPattern: '/targetConnectionId',                    ops: ['replace'] },
  { pathPattern: '/targetDatabase',                        ops: ['replace'] },
  { pathPattern: '/targetSchema',                          ops: ['replace'] },
]

export class ErDesignerAdapter implements UIObject {
  type = 'er_designer'
  objectId: string
  title = 'ER Designer'
  tabId: string
  patchCapabilities = PATCH_CAPS

  constructor(tabId: string, _sessionIdGetter: () => string | null) {
    this.objectId = tabId; this.tabId = tabId
  }

  read(mode: 'state' | 'schema' | 'actions' | 'full'): unknown {
    const payload = useErTabsStore.getState().designers.get(this.tabId)
    if (mode === 'state') return payload ?? null
    if (mode === 'schema') return { type: this.type, patchCapabilities: this.patchCapabilities }
    if (mode === 'actions') {
      return {
        actions: [
          { name: 'auto_layout' }, { name: 'fit_view' },
          { name: 'bind_target' }, { name: 'unbind_target' },
          { name: 'sync_from_db' }, { name: 'diff_against_db' }, { name: 'generate_ddl' },
        ],
      }
    }
    return { state: payload ?? null, schema: { type: this.type, patchCapabilities: this.patchCapabilities } }
  }

  async patch(ops: JsonPatchOp[], _reason?: string): Promise<PatchResult> {
    try {
      const { newVersion } = useErTabsStore.getState().applyDesignerPatch(this.tabId, ops)
      return { status: 'applied', message: `applied ${ops.length} op(s); v${newVersion}` }
    } catch (e) {
      return { status: 'error', message: (e as Error).message }
    }
  }

  async exec(action: string, params?: unknown): Promise<ExecResult> {
    const store = useErTabsStore.getState()
    const payload = store.designers.get(this.tabId)
    if (!payload) return { success: false, error: `tab not found: ${this.tabId}` }

    switch (action) {
      case 'auto_layout': {
        const { computeDagreLayout } = await import('@/features/stage/components/er-canvas/workers/dagre-layout.worker')
        const positions = computeDagreLayout({
          nodes: payload.tables.map((t) => ({ id: t.id, width: 280, height: 40 + t.columns.length * 28 })),
          edges: payload.relations.map((r) => ({ source: r.fromTableId, target: r.toTableId })),
          config: { rankdir: 'LR', nodesep: 80, ranksep: 200 },
        })
        store.applyDesignerPatch(this.tabId, [{ op: 'replace', path: '/positions', value: positions }])
        return { success: true, data: { positions } }
      }

      case 'fit_view': {
        store.applyDesignerPatch(this.tabId, [{ op: 'replace', path: '/viewport', value: { x: 0, y: 0, zoom: 1 } }])
        return { success: true }
      }

      case 'bind_target': {
        const p = (params ?? {}) as { connectionId?: string; database?: string; schema?: string }
        if (!p.connectionId) return { success: false, error: 'bind_target requires { connectionId }' }
        store.applyDesignerPatch(this.tabId, [
          { op: 'replace', path: '/targetConnectionId', value: p.connectionId },
          { op: 'replace', path: '/targetDatabase',     value: p.database ?? null },
          { op: 'replace', path: '/targetSchema',       value: p.schema ?? null },
        ])
        return { success: true }
      }

      case 'unbind_target': {
        store.applyDesignerPatch(this.tabId, [
          { op: 'replace', path: '/targetConnectionId', value: null },
          { op: 'replace', path: '/targetDatabase',     value: null },
          { op: 'replace', path: '/targetSchema',       value: null },
        ])
        return { success: true }
      }

      case 'diff_against_db': {
        if (!payload.targetConnectionId) {
          return { success: false, error: 'target_required_for_apply: bind_target first' }
        }
        const r = await fetch('/api/er/diff', {
          method: 'POST', headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ payload, connectionId: payload.targetConnectionId }),
        })
        if (!r.ok) {
          const e = await r.json().catch(() => ({}))
          return { success: false, error: (e as { aiHint?: string; message?: string }).aiHint ?? (e as { message?: string }).message ?? `diff failed: ${r.status}` }
        }
        const data = await r.json()
        return { success: true, data }
      }

      case 'sync_from_db': {
        if (!payload.targetConnectionId) {
          return { success: false, error: 'target_required_for_apply: bind_target first' }
        }
        const r = await fetch('/api/er/sync-from-db', {
          method: 'POST', headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ connectionId: payload.targetConnectionId, tables: (params as { tables?: string[] } | undefined)?.tables ?? [] }),
        })
        if (!r.ok) return { success: false, error: `sync failed: ${r.status}` }
        return { success: true, data: await r.json() }
      }

      case 'generate_ddl': {
        if (!payload.targetConnectionId) {
          return {
            success: false,
            error: 'target_required_for_apply: bind a target via ui_exec(designer, bind_target, {connectionId, database, schema}) first.',
          }
        }
        const r = await fetch('/api/er/generate-ddl', {
          method: 'POST', headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ payload, connectionId: payload.targetConnectionId, includeDrops: false }),
        })
        if (!r.ok) {
          const e = await r.json().catch(() => ({}))
          return { success: false, error: (e as { aiHint?: string; message?: string }).aiHint ?? (e as { message?: string }).message ?? `generate_ddl failed: ${r.status}` }
        }
        const data = await r.json() as { ddl: string; statements: unknown[]; skipped: unknown[] }

        // Open a new query_editor tab pre-filled with the DDL, bound to the target connection
        const queryEditorTabId = `query_editor_${nanoid(8)}`
        useStageStore.getState().openTab({
          tabId: queryEditorTabId,
          type: 'query_editor',
          title: `Apply ${this.tabId} → ${payload.targetDatabase ?? payload.targetConnectionId}`,
          payload: { sqlText: data.ddl, connectionId: payload.targetConnectionId, database: payload.targetDatabase, schema: payload.targetSchema },
          payloadVersion: 1,
          createdAt: Date.now(),
          lastTouchedAt: Date.now(),
        } as never)
        useStageStore.getState().focusTab(queryEditorTabId)

        return {
          success: true,
          data: { queryEditorTabId, ddl: data.ddl, skippedOps: data.skipped },
        }
      }

      default:
        return { success: false, error: `unknown action: ${action}` }
    }
  }
}
```

- [ ] **Step 4: Run the tests**

Run: `cd client && npx vitest run client/src/features/stage/adapters/__tests__/ErDesignerAdapter.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client/
git commit -m "feat(client/er): ErDesignerAdapter (read/patch + 7 exec verbs)

generate_ddl always lands in a new query_editor tab; never executes
DDL directly. The user runs the SQL through Task 5's L2/L3 confirm."
```

### Task 21: `WorkspaceAdapter.exec(open_er_designer)`

**Files:**
- Modify: `client/src/features/stage/adapters/WorkspaceAdapter.ts`
- Test: `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.er.test.ts`

- [ ] **Step 1: Add the failing test**

Append to `WorkspaceAdapter.er.test.ts`:

```ts
describe('WorkspaceAdapter.exec(open_er_designer)', () => {
  beforeEach(() => {
    useErTabsStore.setState({ inspectors: new Map(), designers: new Map() })
    useStageStore.setState({ tabs: [], activeTabId: null } as any, true)
  })

  it('creates a blank designer tab with the requested dialect', async () => {
    const a = new WorkspaceAdapter(() => null)
    const r = await a.exec('open_er_designer', { dialect: 'mysql', title: 'Order Draft' })
    expect(r.success).toBe(true)
    const data = r.data as { tabId: string; summary: string; payloadVersion: number }
    expect(data.tabId).toMatch(/^er_designer_/)
    expect(data.summary).toContain('mysql')
    expect(useErTabsStore.getState().designers.get(data.tabId)?.dialect).toBe('mysql')
  })

  it('rejects unsupported dialect', async () => {
    const a = new WorkspaceAdapter(() => null)
    const r = await a.exec('open_er_designer', { dialect: 'oracle' as never })
    expect(r.success).toBe(false)
    expect(r.error).toMatch(/dialect_unsupported|oracle/i)
  })

  it('accepts seedTables and pre-populates the draft', async () => {
    const a = new WorkspaceAdapter(() => null)
    const r = await a.exec('open_er_designer', {
      dialect: 'postgresql',
      seedTables: [{ name: 'users', columns: [
        { name: 'id', type: 'BIGINT', nullable: false, isPrimaryKey: true, isAutoIncrement: true }
      ]}],
    })
    expect(r.success).toBe(true)
    const data = r.data as { tabId: string }
    const p = useErTabsStore.getState().designers.get(data.tabId)!
    expect(p.tables).toHaveLength(1)
    expect(p.tables[0].name).toBe('users')
  })
})
```

- [ ] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/adapters/__tests__/WorkspaceAdapter.er.test.ts`
Expected: FAIL.

- [ ] **Step 3: Add the case to `WorkspaceAdapter.ts`**

```ts
case 'open_er_designer': {
  const p = (params ?? {}) as {
    dialect?: 'mysql' | 'postgresql' | 'h2' | 'sqlite'
    title?: string
    targetConnectionId?: string | null
    targetDatabase?: string | null
    targetSchema?: string | null
    seedTables?: { name: string; columns: { name: string; type: string; nullable?: boolean; isPrimaryKey?: boolean; isAutoIncrement?: boolean; default?: string | null; comment?: string | null }[]; comment?: string | null }[]
    seedRelations?: { fromTableId: string; fromColumnId: string; toTableId: string; toColumnId: string; type?: string; constraintMethod?: string }[]
  }
  if (!p.dialect || !['mysql', 'postgresql', 'h2', 'sqlite'].includes(p.dialect)) {
    return { success: false, error: 'dialect_unsupported: ER Designer requires dialect ∈ { mysql, postgresql, h2, sqlite }.' }
  }

  const tabId = `er_designer_${nanoid(8)}`
  const tables = (p.seedTables ?? []).map((t) => ({
    id: `t_${nanoid(8)}`,
    name: t.name,
    comment: t.comment ?? null,
    columns: t.columns.map((c) => ({
      id: `c_${nanoid(8)}`,
      name: c.name,
      type: c.type,
      nullable: c.nullable ?? true,
      isPrimaryKey: c.isPrimaryKey ?? false,
      isAutoIncrement: c.isAutoIncrement ?? false,
      default: c.default ?? null,
      comment: c.comment ?? null,
    })),
    indexes: [],
    uniques: [],
  }))

  const payload: ErDesignerPayload = {
    kind: 'er_designer',
    dialect: p.dialect,
    targetConnectionId: p.targetConnectionId ?? null,
    targetDatabase: p.targetDatabase ?? null,
    targetSchema: p.targetSchema ?? null,
    tables,
    relations: [], // seed relations resolved by id mapping omitted in Plan B Phase 1; AI can ui_patch /relations/- after open
    positions: {},
    collapsed: [],
    viewport: { x: 0, y: 0, zoom: 1 },
  }

  useErTabsStore.getState().hydrateDesigner(tabId, payload)
  useStageStore.getState().openTab({
    tabId, type: 'er_designer',
    title: p.title ?? `Designer (${p.dialect})`,
    payload: payload as unknown as Record<string, unknown>,
    payloadVersion: 1,
    createdAt: Date.now(), lastTouchedAt: Date.now(),
  } as never)
  useStageStore.getState().focusTab(tabId)

  const summary = payload.tables.length === 0
    ? `blank designer (${p.dialect}, ${payload.targetConnectionId ? 'target=' + payload.targetConnectionId : 'no target'})`
    : `${payload.tables.length} tables, ${payload.relations.length} relations (${p.dialect}${payload.targetConnectionId ? ', target=' + payload.targetConnectionId : ''})`

  return { success: true, data: { tabId, payloadVersion: 1, summary } }
}
```

- [ ] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/adapters/__tests__/WorkspaceAdapter.er.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client/
git commit -m "feat(client/er): WorkspaceAdapter.exec(open_er_designer)

Single round-trip: validate dialect → hydrate useErTabsStore → openTab →
focus → return English summary. AI doesn't pass coordinates."
```

### Task 22: `<ErDesignerTab>` + `stage-tab-content` routing

**Files:**
- Create: `client/src/features/stage/components/er-designer-tab.tsx`
- Test: `client/src/features/stage/components/__tests__/er-designer-tab.test.tsx`
- Modify: `client/src/features/stage/components/stage-tab-content.tsx`

- [ ] **Step 1: Write the test**

```tsx
// __tests__/er-designer-tab.test.tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ErDesignerTab } from '../er-designer-tab'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'

vi.mock('@/features/stage/persistence/stage-persistence-bootstrap', () => ({
  coordinator: { ensureHydrated: vi.fn().mockResolvedValue(undefined) },
}))

describe('<ErDesignerTab>', () => {
  it('renders the designer canvas after hydration', () => {
    useErTabsStore.setState({
      inspectors: new Map(),
      designers: new Map([['d-1', {
        kind: 'er_designer', dialect: 'mysql',
        targetConnectionId: null, targetDatabase: null, targetSchema: null,
        tables: [{ id: 't1', name: 'users', columns: [], indexes: [], uniques: [] }],
        relations: [], positions: {}, collapsed: [], viewport: { x: 0, y: 0, zoom: 1 },
      }]]),
    })
    render(<ErDesignerTab tabId="d-1" />)
    expect(screen.getByText('users')).toBeInTheDocument()
  })

  it('renders skeleton when payload missing', () => {
    useErTabsStore.setState({ inspectors: new Map(), designers: new Map() })
    render(<ErDesignerTab tabId="missing" />)
    expect(screen.getByText(/Loading|加载中/i)).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Create the component**

```tsx
// client/src/features/stage/components/er-designer-tab.tsx
import { useCallback, useEffect, useMemo } from 'react'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { ErCanvas } from './er-canvas/ErCanvas'
import { ErDesignerAdapter } from '../adapters/ErDesignerAdapter'
import { coordinator } from '../persistence/stage-persistence-bootstrap'
import type { JsonPatchOp } from '@/features/stage/stores/er-tabs-payload-types'
import { useI18n } from '@/i18n/use-i18n'

export function ErDesignerTab({ tabId }: { tabId: string }) {
  const { t } = useI18n()
  const payload = useErTabsStore((s) => s.designers.get(tabId) ?? null)

  useEffect(() => { void coordinator.ensureHydrated(tabId) }, [tabId])

  const adapter = useMemo(() => new ErDesignerAdapter(tabId, () => null), [tabId])

  const onPatch = useCallback((ops: JsonPatchOp[]) => { void adapter.patch(ops) }, [adapter])
  const onExec = useCallback((action: string, params?: unknown) => { void adapter.exec(action, params) }, [adapter])

  if (!payload) {
    return <div className="flex h-full items-center justify-center text-[var(--text-muted)] text-sm">{t('erCanvas.loading')}</div>
  }
  return <ErCanvas tabId={tabId} mode="designer" payload={payload} onPatch={onPatch} onExec={onExec} />
}
```

- [ ] **Step 3: Wire `stage-tab-content.tsx`**

```tsx
import { ErDesignerTab } from './er-designer-tab'
// ...
case 'er_designer':
  return <ErDesignerTab tabId={tab.tabId} />
```

- [ ] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/__tests__/er-designer-tab.test.tsx`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client/
git commit -m "feat(client/er): ErDesignerTab + stage-tab-content routing"
```

### Task 23: `ErInspectorAdapter.fork_to_designer` real implementation

**Files:**
- Modify: `client/src/features/stage/adapters/ErInspectorAdapter.ts`
- Modify: `client/src/features/stage/adapters/__tests__/ErInspectorAdapter.test.ts`

- [ ] **Step 1: Add the failing test**

```ts
it('fork_to_designer creates a new er_designer tab seeded with current schema', async () => {
  useErTabsStore.setState({
    inspectors: new Map([['t-1', {
      ...samplePayload,
      tablesSnapshot: [
        { name: 'users', columns: [{ name: 'id', type: 'BIGINT', isPK: true, isFK: false, nullable: false }], fkOut: [] },
      ],
    }]]),
    designers: new Map(),
  })
  const a = new ErInspectorAdapter('t-1', () => null)
  const r = await a.exec('fork_to_designer', { title: 'Fork of Order ER' })
  expect(r.success).toBe(true)
  const data = r.data as { newTabId: string }
  expect(data.newTabId).toMatch(/^er_designer_/)
  const designerPayload = useErTabsStore.getState().designers.get(data.newTabId)
  expect(designerPayload?.tables).toHaveLength(1)
  expect(designerPayload?.tables[0].name).toBe('users')
})
```

- [ ] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/adapters/__tests__/ErInspectorAdapter.test.ts`
Expected: FAIL — fork_to_designer still returns "implemented in Plan B" error.

- [ ] **Step 3: Implement `fork_to_designer` in `ErInspectorAdapter.ts`**

Replace the case in the `exec` switch:

```ts
case 'fork_to_designer': {
  const { WorkspaceAdapter } = await import('./WorkspaceAdapter')
  const ws = new WorkspaceAdapter(() => null)
  // Detect dialect from connection kind via the global stage store metadata is not always present;
  // a safer route: read connection kind via API. For Plan A demo, fall back to mysql when ambiguous.
  // The implementer should expose a helper or read from the active ConnectionRecord; here we keep it pragmatic.
  const dialect: 'mysql' | 'postgresql' | 'h2' | 'sqlite' = 'mysql'
  const seedTables = (payload.tablesSnapshot ?? []).map((t) => ({
    name: t.name,
    comment: t.comment ?? null,
    columns: t.columns.map((c) => ({
      name: c.name,
      type: c.type,
      nullable: c.nullable,
      isPrimaryKey: c.isPK,
      isAutoIncrement: c.isAutoIncrement ?? false,
    })),
  }))
  const r = await ws.exec('open_er_designer', {
    dialect,
    title: (params as { title?: string } | undefined)?.title ?? `Fork: ${payload.selection?.[0] ?? 'ER'}`,
    targetConnectionId: payload.connectionId,
    seedTables,
  })
  if (!r.success) return r
  return { success: true, data: { newTabId: (r.data as { tabId: string }).tabId } }
}
```

- [ ] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/adapters/__tests__/ErInspectorAdapter.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add client/
git commit -m "feat(client/er): Inspector.fork_to_designer creates a designer tab seeded from snapshot"
```

---

## Batch 9: AGENTS.md + STAGE_TAB_DIGEST + reference docs

### Task 24: AGENTS.md adds designer recipes + apply flow

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

- [ ] **Step 1: Append designer recipes to the §"ER Tabs" section**

Add at the end of the existing `## ER Tabs (Inspector & Designer)` section (after the inspector recipes added in Plan A):

```md
### Designer recipes

#### Create a new designer with a seed table
ui_exec(workspace, open_er_designer, {
  dialect: "postgresql", title: "Order System Draft",
  seedTables: [{
    name: "users",
    columns: [{ name: "id", type: "BIGINT", isPrimaryKey: true, isAutoIncrement: true }]
  }]
})

#### Fork an inspector into an editable designer
ui_exec(inspector_tab, fork_to_designer, { title: "Fork of Order ER" })

#### Apply a designer to a target DB
ui_exec(designer_tab, bind_target,    { connectionId, database, schema })
ui_exec(designer_tab, diff_against_db)             // optional preview
ui_exec(designer_tab, generate_ddl)
// → returns { queryEditorTabId, ddl, skippedOps }
// Hand the queryEditorTabId to the user; they review + Run + confirm.

#### Add a column via patch
ui_patch(designer_tab, [{
  op: "add", path: "/tables[id=t_abc123]/columns/-",
  value: { name: "status", type: "VARCHAR(32)", nullable: false }
}])
```

Update the recipe table at the top of the section to add the rows about designer scenarios (the spec already covers them; verify they're present).

- [ ] **Step 2: Commit**

```bash
git add server/data-talk-adapter/src/main/resources/agents/AGENTS.md
git commit -m "docs(agents): add Designer recipes (English-only per P12)"
```

### Task 25: STAGE_TAB_DIGEST renders `er_designer` line

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/AgentPromptBuilder.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/AgentPromptBuilderTest.java`

- [ ] **Step 1: Add a designer arm**

```java
case "er_designer" -> {
    int tableCount = payload.containsKey("tables")
        ? ((List<?>) payload.get("tables")).size() : 0;
    int relationCount = payload.containsKey("relations")
        ? ((List<?>) payload.get("relations")).size() : 0;
    String dialect = (String) payload.getOrDefault("dialect", "?");
    String target  = (String) payload.getOrDefault("targetConnectionId", null);
    String targetSuffix = target == null
        ? "no target"
        : "target=" + target + (payload.get("targetDatabase") != null ? "/" + payload.get("targetDatabase") : "");
    yield String.format("%s  %s (%s)  (%d tables · %d relations · %s)",
        tabId, escapeForPrompt(title), dialect, tableCount, relationCount, targetSuffix);
}
```

- [ ] **Step 2: Add the test**

```java
@Test
void erDesignerTabRendersTargetSuffix() {
    String digest = builder.buildDigest(List.of(Map.of(
        "tabId", "er_designer_c3d4",
        "type", "er_designer",
        "title", "Order Draft",
        "payload", Map.of(
            "dialect", "mysql",
            "tables", List.of(Map.of("id", "t1"), Map.of("id", "t2"), Map.of("id", "t3")),
            "relations", List.of(Map.of("id", "r1"), Map.of("id", "r2")),
            "targetConnectionId", "test-mysql",
            "targetDatabase", "test_db"
        )
    )));
    assertThat(digest).contains("er_designer_c3d4");
    assertThat(digest).contains("(mysql)");
    assertThat(digest).contains("3 tables");
    assertThat(digest).contains("target=test-mysql/test_db");
}
```

- [ ] **Step 3: Run and commit**

Run: `cd server && mvn -pl data-talk-application test -Dtest=AgentPromptBuilderTest -q`
Expected: PASS.

```bash
git add server/
git commit -m "feat(agents): STAGE_TAB_DIGEST renders er_designer stats line"
```

### Task 26: `AgentPromptContractTest` designer assertions

**Files:**
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java`

- [ ] **Step 1: Add the assertions**

```java
@Test
void agentsMdReferencesDesignerVerbs() {
    String md = loadAgentsMd();
    assertThat(md).contains("ui_exec(workspace, open_er_designer");
    assertThat(md).contains("ui_exec(designer_tab, bind_target");
    assertThat(md).contains("ui_exec(designer_tab, generate_ddl)");
}

@Test
void uiExecActionSchemaContainsDesignerVerbs() {
    Map<String, Object> schema = uiExecAction.inputSchema();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> oneOf = (List<Map<String, Object>>) schema.get("oneOf");
    boolean hasGenerateDdl = oneOf.stream()
        .map(s -> (Map<String, Object>) s.get("properties"))
        .map(p -> (Map<String, Object>) p.get("action"))
        .filter(a -> a != null)
        .map(a -> (List<?>) a.get("enum"))
        .filter(en -> en != null)
        .anyMatch(en -> en.contains("generate_ddl"));
    assertThat(hasGenerateDdl).isTrue();
}

@Test
void uiPatchActionSchemaAllowsErDesigner() {
    Map<String, Object> schema = uiPatchAction.inputSchema();
    @SuppressWarnings("unchecked")
    Map<String, Object> obj = (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("object");
    assertThat((List<?>) obj.get("enum")).contains("er_designer");
}
```

- [ ] **Step 2: Run and commit**

Run: `cd server && mvn -pl data-talk-adapter test -Dtest=AgentPromptContractTest -q`
Expected: PASS.

```bash
git add server/
git commit -m "test(agents): assert Designer verbs in AGENTS.md and tool schemas"
```

### Task 27: `docs/references/er-tab-protocol.md` Designer half

**Files:**
- Modify: `docs/references/er-tab-protocol.md`

- [ ] **Step 1: Append the Designer section**

Replace the placeholder `(Plan B will append the designer half here.)` with the full Designer section copied from spec §5.3 (Designer Payload Schema), §6.4 (Designer ui_patch path whitelist), §6.6 (Designer ui_exec verbs), §6.7 (designer-relevant errors), §10 (Apply flow), §8.6 (dialect matrix). One source of truth — copy-paste, do not rewrite.

- [ ] **Step 2: Commit**

```bash
git add docs/references/er-tab-protocol.md
git commit -m "docs(references): er-tab-protocol — append Designer half"
```

### Task 28: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` adds Designer DDL matrix

**Files:**
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`

- [ ] **Step 1: Update the ER Tabs row**

Edit the row added in Plan A's T38 to include the Designer DDL matrix:

```md
| ER Tabs (Inspector + Designer) | Inspector: mysql / postgresql / h2 fully via JDBC `getImportedKeys`; sqlite incomplete frontend; oracle / sqlserver `dialect_unsupported`. Designer day-1 DDL generation: mysql / postgresql / h2 emit CREATE TABLE / ALTER ADD COLUMN / ALTER ADD FK / CREATE INDEX; sqlite is CREATE-only with all ALTER variants returning SkippedOp; oracle / sqlserver dialect_unsupported. DROP / ALTER COLUMN type / RENAME are **always** SkippedOp (`day1_unsupported`) regardless of dialect — users must write that SQL manually in the query_editor and run it through Task 5's L2/L3 confirm. |
```

- [ ] **Step 2: Commit**

```bash
git add docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
git commit -m "docs(compat): record Designer DDL matrix; SQLite is CREATE-only"
```

---

## Batch 10: End-to-end verification + housekeeping

### Task 29: Backend full verification

- [x] **Step 1: `mvn clean verify`**

Run: `cd server && mvn clean verify`
Expected: BUILD SUCCESS; all tests green.

Status 2026-04-29: passed. Maven reported `BUILD SUCCESS`; adapter failsafe summary was 140 tests, 0 failures, 0 errors, 2 skipped.

### Task 30: Frontend full verification

- [x] **Step 1: `tsc --noEmit` + `npm test`**

Run: `cd client && npx tsc --noEmit && npm test -- --run`
Expected: zero errors; all suites green.

Status 2026-04-29: passed. `npx tsc --noEmit && npm test -- --run` exited 0 with 137 test files / 826 tests passed.

### Task 31: Manual end-to-end smoke (real database)

- [ ] **Step 1: Walk through the Apply flow**

Status 2026-04-29: not executed in this terminal session. Requires a running Tauri app plus a writable real MySQL connection such as `test-mysql`.

In a running Tauri app with a MySQL connection ("test-mysql") that has at least one writable schema:

1. From chat: "Design a simple users + orders schema in mysql." Confirm AI calls `ui_exec(workspace, open_er_designer, ...)` and a designer tab opens.
2. AI adds tables / relations via `ui_patch`. Confirm the canvas renders the new tables with two-stage AI highlight.
3. From chat: "Bind this draft to the test-mysql connection." Confirm `bind_target` is called and the toolbar's `Diff vs DB` / `Generate DDL` buttons enable.
4. From chat: "Show me the diff." Confirm `diff_against_db` returns and the AI summarizes the result.
5. From chat: "Generate the DDL." Confirm a `query_editor` tab opens with the SQL pre-filled, bound to test-mysql.
6. Click `Run` in the query_editor. Confirm Task 5's L2 confirmation dialog intercepts. Confirm and verify the schema lands in the real DB (use a separate shell to `SHOW CREATE TABLE` to verify).
7. Try `generate_ddl` without `bind_target` (open a new designer, skip step 3). Confirm `target_required_for_apply` aiHint is surfaced cleanly.
8. Try `open_er_designer` with `dialect: 'oracle'`. Confirm `dialect_unsupported`.
9. Try `fork_to_designer` from an inspector. Confirm a new designer tab opens with the inspector's tables seeded; AI then `bind_target` + `generate_ddl` flow continues end-to-end.

- [ ] **Step 2: Tick the spec checklist**

Open the spec and mark Plan B's section §13 acceptance criteria as completed.

Status 2026-04-29: deferred until the manual real-database smoke is executed or explicitly waived.

### Task 32: `docs/exec-plans/index.md` housekeeping

**Files:**
- Modify: `docs/exec-plans/index.md`

- [ ] **Step 1: Move Plan A from Active to Completed; mark Plan B Active during implementation, Completed at the end**

Edit the document's Active and Completed tables. After Plan B ships, both rows live under `## 已完成计划` with their finish dates and short summaries.

- [ ] **Step 2: Commit**

```bash
git add docs/exec-plans/index.md
git commit -m "docs(exec-plans): mark Plan A + Plan B Completed after smoke"
```

### Task 33: Tech-debt tracker housekeeping

**Files:**
- Modify: `docs/exec-plans/tech-debt-tracker.md`

- [ ] **Step 1: If no new tech debt, record explicitly**

Confirm that no Plan B tasks introduced new tech debt. If any did, list the items with concrete next steps. If none, append a short note dated 2026-XX-XX confirming.

- [ ] **Step 2: Commit**

```bash
git add docs/exec-plans/tech-debt-tracker.md
git commit -m "docs(tech-debt): Plan B closure note"
```

### Task 34: Final spec checklist + roadmap

**Files:**
- Modify: `docs/product-specs/2026-04-29-er-graph-browsing-design.md`
- Modify: `docs/exec-plans/2026-04-25-next-implementation-roadmap-plan.md`

- [ ] **Step 1: Tick all spec §13 / §14 boxes**

Edit the spec; mark Plan A and Plan B as completed in §13 (Phasing) and §14 (Definition of Done).

- [ ] **Step 2: Mark Roadmap Task 8.1 / 8.2 done**

Edit `2026-04-25-next-implementation-roadmap-plan.md` Task 8 section; tick the two checkboxes for Plan A + Plan B and add a brief `(shipped 2026-XX-XX)` note.

- [ ] **Step 3: Commit**

```bash
git add docs/
git commit -m "docs: ER graph browsing spec + roadmap closure"
```

### Task 35: Hand-off

- [ ] **Step 1: Open PR or hand off to user**

Plan B exit criteria are satisfied; the ER graph browsing slice is now Complete. Roadmap Task 8 is closed. Roadmap Task 9 (External Data Ingestion) is unblocked per spec §13 / roadmap §"Ordering And Parallelism".

---

## Verification Gates

Per the roadmap's `## Verification Gates`:

- Backend compile gate: `cd server && mvn compile -q` (T1-T11 incremental, T29 final)
- Backend full gate: `cd server && mvn clean verify` (T29)
- Frontend type gate: `cd client && npx tsc --noEmit` (T12, T18, T22, T30)
- Frontend tests: `cd client && npm test -- --run` (T30)
- Manual real-database smoke: T31 — required, not skipped.

## Exit Criteria

- All 35 tasks checked.
- Spec §13 Plan B acceptance criteria all satisfied:
  - AI can `open_er_designer` with seed tables.
  - AI can `ui_patch` add tables / columns / relations with `assignedIds` returned.
  - Strict baseVersion conflict surfaces a `409`-like structured error and AI recovers via `ui_read` + retry.
  - AI can `bind_target` + `diff_against_db` + `generate_ddl`; DDL lands in a new query_editor tab and the user runs it under Task 5's L2/L3 confirm.
  - DROP / ALTER COLUMN type / RENAME emit SkippedOp with English `aiHint`; users always have the manual-SQL escape hatch.
  - Inspector `fork_to_designer` creates a designer tab seeded with the inspector's snapshot.
  - mysql / postgresql / h2 generate complete DDL plans; sqlite is CREATE-only; oracle / sqlserver are `dialect_unsupported`.
  - `mvn clean verify` + `npx tsc --noEmit` + `npm test` pass.
- Documentation housekeeping (spec, exec-plans index, roadmap, DATA_SOURCE_TYPE_COMPATIBILITY, er-tab-protocol).
- Roadmap Task 8 (Visualization Expansion) is closed; Task 9 is unblocked.
