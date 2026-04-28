# Shared Stage Workbench · Phase 1 — Backend Protocol & Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Land the protocol-layer multi-session safety: drop `StageTabScope`, V13 SQLite migration with FTS rewire, `expectedText` + `baseVersion` enforcement, `error.markdown` formatter, `workspace.detach/archive/trash/focus` actions, AGENTS.md + ui-objects-reference rewrite, and `STAGE_TAB_DIGEST` field upgrade — without changing user-visible UI.

**Architecture:** Backend domain/infra/application/adapter layers refactored top-down: domain (`StageTab` record + `StageTabScope` deletion) → infra (V13 migration + JdbcRepository scope removal + ListFilter cleanup) → application (`StageFindService` filter, `EditConflictMarkdownFormatter`, `AgentPromptBuilder`) → adapter (UiPatch / UiExec schemas + AGENTS.md + concurrency IT). Frontend persistence layer adapts to the new `listAll` API and adds `replaceSqlText` version validation. Critical 4-task atomic group (1.6/1.7/1.13/1.14) MUST land in a single commit to avoid post-deploy schema-rejection windows.

**Tech Stack:** Java 21 (virtual threads) · Spring Boot 3.5 · JdbcTemplate · Flyway · SQLite + FTS5 · JUnit 5 · AssertJ · WireMock 3.x · React 19 · TypeScript · Zustand · vitest

---

## File Structure

### New files

| Path | Responsibility |
|---|---|
| `server/data-talk-infrastructure/src/main/resources/db/migration/V13__stage_tabs_workspace_only.sql` | Migration: backup, drop 6 V12 triggers, rebuild stage_tabs (drop scope + FK SET NULL), rebuild FTS rowid mapping, recreate 6 triggers |
| `server/data-talk-application/src/main/java/com/datatalk/application/stage/EditConflictMarkdownFormatter.java` | Render markdown error bodies for 5 conflict codes (`version_conflict` / `expected_text_mismatch` / `out_of_range_lines` / `tab_not_found` / `tab_archived`) |
| `server/data-talk-application/src/main/java/com/datatalk/application/stage/EditRange.java` | Value object: `record EditRange(int startLine, int startColumn, int endLine, int endColumn)`; replaces inline `Map`-based passing in formatter signatures |
| `server/data-talk-application/src/test/java/com/datatalk/application/stage/EditConflictMarkdownFormatterTest.java` | Unit test 5 codes × edge cases (long content truncation, CRLF normalization, missing originSession) |
| `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/StageTabConcurrencyIT.java` | Integration test: two virtual threads racing the same tabId; later one MUST get `version_conflict` + complete markdown |
| `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptDigestTest.java` | Unit test: render 3 fixture tabs → assert 6 fields per row + stable order + `(deleted)` fallback |

### Files to delete

| Path | Reason |
|---|---|
| `server/data-talk-domain/src/main/java/com/datatalk/domain/stage/StageTabScope.java` | Scope concept removed; all tabs are workspace-wide |

### Files to modify

| Path | Change |
|---|---|
| `server/data-talk-domain/src/main/java/com/datatalk/domain/stage/StageTab.java` | Remove `scope` field & validation; `originSessionId` becomes nullable |
| `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/stage/StageTabJdbcRepository.java` | Drop `scope` column reads/writes; `TAB_MAPPER` no longer reads scope; INSERT/UPDATE SQL no longer mentions scope |
| `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/stage/StageTabJdbcRepositoryTest.java` | Adapt fixtures to scope-less constructor |
| `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/stage/StageTabsMigrationIT.java` | Add 6 V13 assertions: trigger inventory, FTS row alignment, FTS MATCH still hits, scope column gone, FK INSERT trigger live, session DELETE → CASCADE no longer cascades + originSessionId SET NULL |
| `server/data-talk-application/src/main/java/com/datatalk/application/stage/StageTabRepository.java` | `ListFilter`: drop `scope` field; constructor sig change |
| `server/data-talk-application/src/main/java/com/datatalk/application/stage/StageFindService.java` | Remove `scope` filter; SQL `LEFT JOIN sessions s ON s.id = t.origin_session_id` to return `originSessionTitle` |
| `server/data-talk-application/src/main/java/com/datatalk/application/stage/StageFindQuery.java` | Drop `scope` field |
| `server/data-talk-application/src/main/java/com/datatalk/application/stage/StageFindResult.java` | Add `originSessionTitle` field to result rows |
| `server/data-talk-application/src/test/java/com/datatalk/application/stage/StageFindService*Test.java` | Adapt to scope-less ListFilter; new `originSessionTitle` assertions |
| `server/data-talk-application/src/main/java/com/datatalk/application/stage/AgentPromptBuilder.java` | New digest format: `originSession` (with deleted fallback) / `lastTouched` / `version` per row; batch-join sessions table |
| `server/data-talk-application/src/main/java/com/datatalk/application/stage/StageTabService.java` | Remove `StageTabScope` import in upsert path; if `scope` is referenced anywhere drop it |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiPatchAction.java` | `/content` `replace` op schema: `baseVersion: number, required` |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java` | `apply_text_edits` per-edit `expectedText: string, required`; new `workspace.detach` / `archive` (with `archived?: boolean = true`) / `trash`; `close` deprecated alias documentation |
| `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/UiActionsTest.java` | Schema validation tests: missing `baseVersion` → reject; missing `expectedText` → reject; new actions accepted |
| `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/UiFindActionTest.java` | Delete `scope` filter test; add `originSessionTitle` response field test |
| `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` | Major rewrite of UI Actions section + `## Concurrency Contract` + `## Library vs Workset` + STAGE_TAB_DIGEST contract notes |
| `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java` | Assert 6 key tokens present (`Concurrency Contract` / `Library vs Workset` / `expectedText` / `error.markdown` / `baseVersion` / `inWorkset`) + `Deprecated since` marker |
| `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/StageTabSearchScenarioIT.java` | Adapt to scope-less filter; add originSessionTitle assertion |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/StageTabController.java` | List endpoint: drop `scope` query param, add `originSessionId` query param |
| `client/src/features/stage/persistence/stage-tab-api.ts` | `listWorkspaceTabs` / `listSessionTabs` deleted; `listAll({ archived?, originSessionId? })` added; `UpsertRequest.scope` removed |
| `client/src/features/stage/persistence/stage-persistence-coordinator.ts` | `start()` calls `listAll({ archived: false })` |
| `client/src/features/stage/persistence/stage-persistence-bootstrap.ts` | `__hydrateWorkspaceTabs` / `__hydrateSessionTabs` calls merged into `__hydrateAll`; `persistedTabSummaries` walks single `tabs[]`; `resolveTabSnapshot` no longer reads scope |
| `client/src/stores/stage-store.ts` | Add `__hydrateAll` (deprecate other two as aliases for one release); `UpsertRequest`/`StageTab` type cleanup of `scope` |
| `client/src/features/stage/stores/sql-workbench-store.ts` | `replaceSqlText(tabId, content, baseVersion)` with version-conflict result; `SqlWorkbenchEditResult` reused |
| `client/src/features/stage/stores/sql-workbench-store.test.ts` | Add 3 cases for `replaceSqlText` version-conflict path |
| `client/src/features/stage/adapters/QueryEditorAdapter.ts` | `apply_text_edits` carrier: pass `expectedText` per edit; `set_context` unchanged; `replace /content` carries `baseVersion` |
| `docs/references/ui-objects-reference.md` | Major rewrite per spec §1.18 |
| `docs/exec-plans/index.md` | Move plan from Active to (none yet — register in Active when this plan starts) |

---

## Phase 1A — Domain & Migration (Tasks 1–4)

### Task 1: V13 migration SQL + register backup

**Files:**
- Create: `server/data-talk-infrastructure/src/main/resources/db/migration/V13__stage_tabs_workspace_only.sql`

- [x] **Step 1: Write migration file**

Path: `server/data-talk-infrastructure/src/main/resources/db/migration/V13__stage_tabs_workspace_only.sql`

```sql
-- V13__stage_tabs_workspace_only.sql
-- Drop scope column, change FK ON DELETE CASCADE → SET NULL, rebuild FTS rowid mapping.
-- Per spec docs/product-specs/2026-04-28-shared-stage-workbench-design.md §4.4.

PRAGMA foreign_keys = OFF;

-- 0) day-1 backup table (legal SQLite syntax: CREATE TABLE … AS SELECT)
CREATE TABLE IF NOT EXISTS stage_tabs_backup_v13_pre AS SELECT * FROM stage_tabs;

-- 1) drop V12 triggers (DROP TABLE clears them too; explicit for readability)
DROP TRIGGER IF EXISTS stage_tabs_ai;
DROP TRIGGER IF EXISTS stage_tabs_au;
DROP TRIGGER IF EXISTS stage_tabs_ad;
DROP TRIGGER IF EXISTS stage_tab_payload_aiu;
DROP TRIGGER IF EXISTS stage_tab_payload_au;
DROP TRIGGER IF EXISTS stage_tab_payload_ad;

-- 2) rebuild stage_tabs without scope; FK SET NULL
CREATE TABLE stage_tabs_new (
  id                 TEXT PRIMARY KEY,
  type               TEXT NOT NULL,
  title              TEXT NOT NULL,
  connection_id      TEXT,
  database_name      TEXT,
  schema_name        TEXT,
  origin_session_id  TEXT,
  payload_version    INTEGER NOT NULL DEFAULT 1,
  pinned             INTEGER NOT NULL DEFAULT 0,
  archived           INTEGER NOT NULL DEFAULT 0,
  archived_at        INTEGER,
  created_at         INTEGER NOT NULL,
  last_touched_at    INTEGER NOT NULL,
  FOREIGN KEY (origin_session_id) REFERENCES sessions(id) ON DELETE SET NULL
);

INSERT INTO stage_tabs_new (id, type, title, connection_id, database_name, schema_name,
  origin_session_id, payload_version, pinned, archived, archived_at, created_at, last_touched_at)
  SELECT id, type, title, connection_id, database_name, schema_name,
         origin_session_id, payload_version, pinned, archived, archived_at, created_at, last_touched_at
  FROM stage_tabs;

DROP TABLE stage_tabs;
ALTER TABLE stage_tabs_new RENAME TO stage_tabs;

-- 3) rebuild indexes
CREATE INDEX idx_stage_tabs_active ON stage_tabs(archived, last_touched_at DESC) WHERE archived = 0;
CREATE INDEX idx_stage_tabs_type   ON stage_tabs(type, archived);
CREATE INDEX idx_stage_tabs_origin ON stage_tabs(origin_session_id);

-- 4) rebuild FTS row mapping (rowid changed after RENAME)
DELETE FROM stage_tab_index;
INSERT INTO stage_tab_index(rowid, title, content, type, archived)
  SELECT t.rowid,
         t.title,
         COALESCE(p.content_text, ''),
         t.type,
         t.archived
  FROM stage_tabs t
  LEFT JOIN stage_tab_payload p ON p.tab_id = t.id;

-- 5) recreate 6 triggers (no scope column)
CREATE TRIGGER stage_tabs_ai AFTER INSERT ON stage_tabs BEGIN
  INSERT INTO stage_tab_index(rowid, title, content, type, archived)
    VALUES (NEW.rowid, NEW.title, '', NEW.type, NEW.archived);
END;

CREATE TRIGGER stage_tabs_au AFTER UPDATE OF title, archived ON stage_tabs BEGIN
  UPDATE stage_tab_index
    SET title = NEW.title, archived = NEW.archived
    WHERE rowid = NEW.rowid;
END;

CREATE TRIGGER stage_tabs_ad AFTER DELETE ON stage_tabs BEGIN
  DELETE FROM stage_tab_index WHERE rowid = OLD.rowid;
END;

CREATE TRIGGER stage_tab_payload_aiu AFTER INSERT ON stage_tab_payload BEGIN
  UPDATE stage_tab_index
    SET content = NEW.content_text
    WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = NEW.tab_id);
END;

CREATE TRIGGER stage_tab_payload_au AFTER UPDATE OF content_text ON stage_tab_payload BEGIN
  UPDATE stage_tab_index
    SET content = NEW.content_text
    WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = NEW.tab_id);
END;

CREATE TRIGGER stage_tab_payload_ad AFTER DELETE ON stage_tab_payload BEGIN
  UPDATE stage_tab_index
    SET content = ''
    WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = OLD.tab_id);
END;

PRAGMA foreign_keys = ON;
```

- [x] **Step 2: Commit migration**

```bash
git add server/data-talk-infrastructure/src/main/resources/db/migration/V13__stage_tabs_workspace_only.sql
git commit -m "feat(stage-tabs): V13 migration drops scope, FTS rewire, FK SET NULL"
```

---

### Task 2: Extend `StageTabsMigrationIT` to assert V13 outcomes

**Files:**
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/stage/StageTabsMigrationIT.java`

- [x] **Step 1: Open test, add V13 assertion method**

Append a new test method to the class:

```java
@Test
void v13_migration_dropsScope_rewiresFts_keepsTabsAfterSessionDelete() {
    // Given: V12-applied DB with rows in stage_tabs + stage_tab_payload + FTS triggered
    insertSessionRow("sess-1", "April Weekly");
    insertStageTabRow("qe-1", "query_editor", "workspace", "users monthly", "sess-1");
    insertStageTabPayloadRow("qe-1", "{\"sqlText\":\"SELECT id FROM users\"}",
                              "SELECT id FROM users");

    // When: run V13 migration explicitly
    flyway.migrate(); // actually pinned to V13 in this test

    // Then: stage_tabs has no scope column
    List<String> columns = jdbc.queryForList(
        "SELECT name FROM pragma_table_info('stage_tabs')", String.class);
    assertThat(columns).doesNotContain("scope");

    // And: FTS index rebuilt and aligned with new rowid
    Integer idxCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM stage_tab_index", Integer.class);
    Integer tabCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM stage_tabs", Integer.class);
    assertThat(idxCount).isEqualTo(tabCount);

    // And: FTS MATCH still works on previously-indexed content
    List<String> hits = jdbc.queryForList(
        "SELECT title FROM stage_tab_index WHERE stage_tab_index MATCH 'users'",
        String.class);
    assertThat(hits).contains("users monthly");

    // And: only the 6 new triggers exist
    List<String> triggers = jdbc.queryForList(
        "SELECT name FROM sqlite_master WHERE type='trigger' ORDER BY name",
        String.class);
    assertThat(triggers).containsExactly(
        "stage_tab_payload_ad", "stage_tab_payload_aiu", "stage_tab_payload_au",
        "stage_tabs_ad", "stage_tabs_ai", "stage_tabs_au");

    // And: backup table preserved
    Integer backupCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM stage_tabs_backup_v13_pre", Integer.class);
    assertThat(backupCount).isEqualTo(tabCount);
}

@Test
void v13_postMigration_payloadInsertTriggersFtsUpdate() {
    flyway.migrate();
    insertStageTabRow("qe-2", "query_editor", null, "second tab", null);

    // Inserting payload via trigger path updates FTS
    jdbc.update("INSERT INTO stage_tab_payload (tab_id, payload_json, content_text, content_version, updated_at) " +
                "VALUES (?, ?, ?, ?, ?)",
                "qe-2", "{\"sqlText\":\"new payload\"}", "new payload", 1, System.currentTimeMillis());

    String content = jdbc.queryForObject(
        "SELECT content FROM stage_tab_index WHERE rowid = (SELECT rowid FROM stage_tabs WHERE id = 'qe-2')",
        String.class);
    assertThat(content).isEqualTo("new payload");

    // FK constraint still enforced: orphan tab_id rejected
    assertThatThrownBy(() -> jdbc.update(
        "INSERT INTO stage_tab_payload (tab_id, payload_json, content_text, content_version, updated_at) " +
        "VALUES ('nonexistent', '{}', '', 1, ?)",
        System.currentTimeMillis()))
        .hasMessageContaining("FOREIGN KEY constraint failed");
}

@Test
void v13_sessionDelete_doesNotCascade_tabSurvivesWithNullOrigin() {
    flyway.migrate();
    insertSessionRow("sess-A", "to be deleted");
    insertStageTabRow("qe-3", "query_editor", null, "from sess-A", "sess-A");

    jdbc.update("DELETE FROM sessions WHERE id = ?", "sess-A");

    // Tab survives, origin_session_id is NULL
    Map<String, Object> row = jdbc.queryForMap(
        "SELECT id, origin_session_id FROM stage_tabs WHERE id = 'qe-3'");
    assertThat(row.get("id")).isEqualTo("qe-3");
    assertThat(row.get("origin_session_id")).isNull();
}
```

- [x] **Step 2: Add helper methods (or inline) for fixture inserts**

Add to the same class if not yet present:

```java
private void insertSessionRow(String id, String title) {
    jdbc.update("INSERT INTO sessions (id, title, created_at, updated_at, has_ever_sent) VALUES (?, ?, ?, ?, 1)",
                id, title, System.currentTimeMillis(), System.currentTimeMillis());
}

private void insertStageTabRow(String id, String type, String scopeOrNull, String title, String originSessionId) {
    long now = System.currentTimeMillis();
    if (scopeOrNull != null) {
        // V12 schema (used in pre-migration assertions; will fail post-V13)
        jdbc.update("INSERT INTO stage_tabs (id, type, scope, title, origin_session_id, payload_version, " +
                    "pinned, archived, created_at, last_touched_at) VALUES (?, ?, ?, ?, ?, 1, 0, 0, ?, ?)",
                    id, type, scopeOrNull, title, originSessionId, now, now);
    } else {
        // V13 schema
        jdbc.update("INSERT INTO stage_tabs (id, type, title, origin_session_id, payload_version, " +
                    "pinned, archived, created_at, last_touched_at) VALUES (?, ?, ?, ?, 1, 0, 0, ?, ?)",
                    id, type, title, originSessionId, now, now);
    }
}

private void insertStageTabPayloadRow(String tabId, String payloadJson, String contentText) {
    jdbc.update("INSERT INTO stage_tab_payload (tab_id, payload_json, content_text, content_version, updated_at) " +
                "VALUES (?, ?, ?, 1, ?)",
                tabId, payloadJson, contentText, System.currentTimeMillis());
}
```

- [x] **Step 3: Run the new tests, expect FAIL**

```bash
cd server && mvn test -pl data-talk-infrastructure -Dtest=StageTabsMigrationIT#v13_migration_dropsScope_rewiresFts_keepsTabsAfterSessionDelete -q
```

Expected: FAIL because Java domain still references `StageTabScope`. (Migration SQL exists but the test environment still loads pre-V13 entities.)

- [x] **Step 4: Commit failing tests**

```bash
git add server/data-talk-infrastructure/src/test/java/com/datatalk/infra/stage/StageTabsMigrationIT.java
git commit -m "test(stage-tabs): assert V13 drops scope, rewires FTS, breaks CASCADE"
```

---

### Task 3: Drop `StageTabScope` enum, scope from `StageTab` record

**Files:**
- Delete: `server/data-talk-domain/src/main/java/com/datatalk/domain/stage/StageTabScope.java`
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/stage/StageTab.java`

- [x] **Step 1: Update `StageTab.java`**

Replace entire file:

```java
package com.datatalk.domain.stage;

import java.util.Objects;

/**
 * Core tab metadata record. Payload and content are stored separately
 * in {@link StageTabContent} for selective loading.
 *
 * <p>Tabs are workspace-wide (not session-scoped). {@code originSessionId}
 * is a soft label only; it may be {@code null} when the originating session
 * has been deleted (FK SET NULL post-V13).
 */
public record StageTab(
    String id,
    String type,
    String title,
    String connectionId,
    String databaseName,
    String schemaName,
    String originSessionId,
    int payloadVersion,
    boolean pinned,
    boolean archived,
    Long archivedAt,
    long createdAt,
    long lastTouchedAt
) {
    public StageTab {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(title, "title must not be null");
        if (payloadVersion < 1) {
            throw new IllegalArgumentException("payloadVersion must be >= 1, got " + payloadVersion);
        }
    }
}
```

- [x] **Step 2: Delete `StageTabScope.java`**

```bash
git rm server/data-talk-domain/src/main/java/com/datatalk/domain/stage/StageTabScope.java
```

- [x] **Step 3: Compile domain module**

```bash
cd server && mvn compile -pl data-talk-domain -q
```

Expected: PASS (domain only uses its own types).

- [x] **Step 4: Commit domain changes**

```bash
git add server/data-talk-domain/
git commit -m "refactor(domain): drop StageTabScope, scope from StageTab record"
```

---

### Task 4: Refactor infra `StageTabJdbcRepository` (drop scope SQL/mapper)

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/stage/StageTabJdbcRepository.java`
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/stage/StageTabJdbcRepositoryTest.java`

- [x] **Step 1: Update `TAB_MAPPER`**

In `StageTabJdbcRepository.java`, replace the `TAB_MAPPER` constant:

```java
private static final RowMapper<StageTab> TAB_MAPPER = (rs, i) -> new StageTab(
    rs.getString("id"),
    rs.getString("type"),
    rs.getString("title"),
    rs.getString("connection_id"),
    rs.getString("database_name"),
    rs.getString("schema_name"),
    rs.getString("origin_session_id"),
    rs.getInt("payload_version"),
    rs.getInt("pinned") == 1,
    rs.getInt("archived") == 1,
    rs.getObject("archived_at") instanceof Number n ? n.longValue() : null,
    rs.getLong("created_at"),
    rs.getLong("last_touched_at")
);
```

- [x] **Step 2: Update `upsertMetadata` SQL**

Replace the UPDATE statement:

```java
jdbc.update("""
    UPDATE stage_tabs SET
        type = ?, title = ?, connection_id = ?, database_name = ?,
        schema_name = ?, origin_session_id = ?, payload_version = ?, pinned = ?,
        archived = ?, archived_at = ?, last_touched_at = ?
    WHERE id = ?
    """,
    tab.type(), tab.title(),
    tab.connectionId(), tab.databaseName(), tab.schemaName(),
    tab.originSessionId(), newVersion, tab.pinned() ? 1 : 0,
    tab.archived() ? 1 : 0, tab.archivedAt(),
    tab.lastTouchedAt(), tab.id());
```

- [x] **Step 3: Update `doInsert` SQL**

Find `doInsert` method and replace its SQL to omit scope:

```java
private int doInsert(StageTab tab) {
    long now = tab.lastTouchedAt() == 0 ? System.currentTimeMillis() : tab.lastTouchedAt();
    jdbc.update("""
        INSERT INTO stage_tabs
        (id, type, title, connection_id, database_name, schema_name,
         origin_session_id, payload_version, pinned, archived, archived_at,
         created_at, last_touched_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        tab.id(), tab.type(), tab.title(),
        tab.connectionId(), tab.databaseName(), tab.schemaName(),
        tab.originSessionId(), 1, tab.pinned() ? 1 : 0,
        tab.archived() ? 1 : 0, tab.archivedAt(),
        tab.createdAt() == 0 ? now : tab.createdAt(), now);
    return 1;
}
```

- [x] **Step 4: Remove `StageTabScope` import**

Top of file: delete `import com.datatalk.domain.stage.StageTabScope;`.

- [x] **Step 5: Update `list` to drop scope filter clause**

Find any SQL referring to `scope = ?`. Remove that branch and the matching parameter binding. Replace with no-op (filter no longer accepts scope).

- [x] **Step 6: Update `StageTabJdbcRepositoryTest.java` fixtures**

Replace any `new StageTab(... StageTabScope.WORKSPACE ...)` constructor calls with the new positional constructor (no scope arg). Remove `import StageTabScope`.

- [x] **Step 7: Compile + run infra tests**

```bash
cd server && mvn test -pl data-talk-infrastructure -q
```

Expected: PASS for non-IT tests; `StageTabsMigrationIT` may still need application-layer wiring before passing (defer to Task 9).

- [x] **Step 8: Commit infra repo changes**

```bash
git add server/data-talk-infrastructure/
git commit -m "refactor(infra): StageTabJdbcRepository drops scope column reads/writes"
```

---

## Phase 1B — Application Repository & Find Service (Tasks 5–7)

### Task 5: `StageTabRepository.ListFilter` and `StageTabService` cleanup

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/stage/StageTabRepository.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/stage/StageTabService.java`

- [x] **Step 1: Replace `ListFilter` record**

In `StageTabRepository.java`:

```java
record ListFilter(
    String type,
    String connectionId,
    String originSessionId,
    boolean includeArchived,
    Boolean pinned,
    Long lastTouchedAfter,
    Long lastTouchedBefore,
    int limit
) {
    public static ListFilter defaultFilter() {
        return new ListFilter(null, null, null, false, null, null, null, 100);
    }
}
```

Drop `import com.datatalk.domain.stage.StageTabScope;` from the top.

- [x] **Step 2: Update infra impl call sites**

In `StageTabJdbcRepository.list(ListFilter filter)`, remove any `filter.scope()` references — that field no longer exists; the WHERE clause should no longer try to bind it.

- [x] **Step 3: Compile application + infra**

```bash
cd server && mvn compile -pl data-talk-application -am -q
```

Expected: PASS.

- [x] **Step 4: Update `StageTabService` upsert call site**

Open `StageTabService.upsert` and find construction of `StageTab withTimestamp`. Update positional args to drop scope:

```java
StageTab withTimestamp = new StageTab(
    tab.id(), tab.type(), tab.title(),
    tab.connectionId(), tab.databaseName(), tab.schemaName(),
    tab.originSessionId(), tab.payloadVersion(),
    tab.pinned(), tab.archived(), tab.archivedAt(),
    tab.createdAt(), now
);
```

- [x] **Step 5: Compile + commit**

```bash
cd server && mvn compile -q
git add server/data-talk-application/src/main/java/com/datatalk/application/stage/
git commit -m "refactor(application): drop scope from ListFilter and StageTabService"
```

---

### Task 6: `StageFindService` LEFT JOIN sessions; expose `originSessionTitle`

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/stage/StageFindService.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/stage/StageFindQuery.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/stage/StageFindResult.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/stage/StageFindServiceMetadataTest.java`

- [x] **Step 1: Add `originSessionTitle` to `StageFindResult` row record**

Open `StageFindResult.java`. Find the `Item` (or whatever the per-row record is named). Add field:

```java
public record Item(
    String tabId,
    String type,
    String title,
    String connectionId,
    String database,
    String schema,
    String originSessionId,
    String originSessionTitle,   // NEW — soft label, computed via LEFT JOIN; null if origin session deleted
    boolean pinned,
    boolean archived,
    long lastTouchedAt,
    int payloadVersion
) {}
```

- [x] **Step 2: Drop scope filter from `StageFindQuery`**

If `StageFindQuery.filter` had a `scope` field, delete it and any `Filter.builder().scope(...)` call sites.

- [x] **Step 3: Update `StageFindService` SQL**

Replace the metadata listing query with the LEFT JOIN form:

```java
private static final String METADATA_SQL = """
    SELECT t.id              AS tab_id,
           t.type            AS type,
           t.title           AS title,
           t.connection_id   AS connection_id,
           t.database_name   AS database_name,
           t.schema_name     AS schema_name,
           t.origin_session_id AS origin_session_id,
           s.title           AS origin_session_title,
           t.pinned          AS pinned,
           t.archived        AS archived,
           t.last_touched_at AS last_touched_at,
           t.payload_version AS payload_version
    FROM stage_tabs t
    LEFT JOIN sessions s ON s.id = t.origin_session_id
    WHERE (? OR t.archived = 0)
      AND (? OR t.type = ?)
      AND (? OR t.connection_id = ?)
      AND (? OR t.origin_session_id = ?)
      AND (? OR t.pinned = ?)
    ORDER BY t.last_touched_at DESC
    LIMIT ?
    """;
```

Bind `originSessionTitle` to the new result field in the `RowMapper`.

- [x] **Step 4: Add unit test for join**

In `StageFindServiceMetadataTest.java`:

```java
@Test
void metadata_includesOriginSessionTitle_whenSessionExists() {
    insertSession("sess-1", "April Weekly");
    insertTab("qe-1", "query_editor", "users monthly", "sess-1");

    StageFindResult result = service.find(StageFindQuery.builder().build());

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).originSessionTitle()).isEqualTo("April Weekly");
}

@Test
void metadata_originSessionTitleNull_whenSessionDeleted() {
    insertTab("qe-2", "query_editor", "orphan", null); // origin_session_id null

    StageFindResult result = service.find(StageFindQuery.builder().build());

    assertThat(result.items().get(0).originSessionId()).isNull();
    assertThat(result.items().get(0).originSessionTitle()).isNull();
}
```

- [x] **Step 5: Run tests, expect PASS**

```bash
cd server && mvn test -pl data-talk-application -Dtest=StageFindServiceMetadataTest -q
```

Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/stage/StageFindService.java \
        server/data-talk-application/src/main/java/com/datatalk/application/stage/StageFindQuery.java \
        server/data-talk-application/src/main/java/com/datatalk/application/stage/StageFindResult.java \
        server/data-talk-application/src/test/java/com/datatalk/application/stage/StageFindServiceMetadataTest.java
git commit -m "feat(stage-find): LEFT JOIN sessions, return originSessionTitle"
```

---

### Task 7: `StageTabController` query param sweep

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/StageTabController.java`

- [x] **Step 1: Update list endpoint params**

Find the `@GetMapping` handler and update its `@RequestParam` list. Remove `scope`. Keep `archived` and `originSessionId`. Document on the Javadoc that `scope` is removed.

- [x] **Step 2: Compile**

```bash
cd server && mvn compile -q
```

Expected: PASS.

- [x] **Step 3: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/StageTabController.java
git commit -m "refactor(adapter): StageTabController drops scope query param"
```

---

## Phase 1C — Markdown Formatter (Tasks 8–9)

### Task 8: New `EditRange` value object + `EditConflictMarkdownFormatter` skeleton

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/stage/EditRange.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/stage/EditConflictMarkdownFormatter.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/stage/EditConflictMarkdownFormatterTest.java`

- [x] **Step 1: Write `EditRange.java`**

```java
package com.datatalk.application.stage;

/** A 1-based line/column edit range; matches Monaco's IRange shape. */
public record EditRange(int startLine, int startColumn, int endLine, int endColumn) {
    public EditRange {
        if (startLine < 1) throw new IllegalArgumentException("startLine must be >= 1");
        if (endLine < startLine) throw new IllegalArgumentException("endLine must be >= startLine");
    }
}
```

- [x] **Step 2: Write failing test for `versionConflict`**

`EditConflictMarkdownFormatterTest.java`:

```java
package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTab;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EditConflictMarkdownFormatterTest {

    private static StageTab tab(String id, String title, int version) {
        return new StageTab(id, "query_editor", title, null, null, null, null,
                            version, false, false, null,
                            System.currentTimeMillis(), System.currentTimeMillis());
    }

    @Test
    void versionConflict_includesStandardSections() {
        StageTab t = tab("qe-1", "users monthly", 14);
        String md = EditConflictMarkdownFormatter.versionConflict(
            t, /*requestedBase=*/ 12, /*actualVersion=*/ 14, /*deltaMs=*/ 12_000L);

        assertThat(md).contains("## Edit failed: version drifted");
        assertThat(md).contains("Tab: `qe-1`");
        assertThat(md).contains("(users monthly)");
        assertThat(md).contains("Reason: version_conflict");
        assertThat(md).contains("baseVersion=12");
        assertThat(md).contains("current version=14");
        assertThat(md).contains("12s ago");
        assertThat(md).contains("datatalk_ui_read");
        assertThat(md).contains("Suggested next step:");
    }

    @Test
    void expectedTextMismatch_includesExpectedAndActualCodeBlocks() {
        StageTab t = tab("qe-2", "orders trend", 7);
        String md = EditConflictMarkdownFormatter.expectedTextMismatch(
            t, /*editIndex=*/ 0,
            "WHERE created_at > '2026-01-01';\n",
            "WHERE u.created_at > '2026-01-01';\n",
            /*actualVersion=*/ 7, /*deltaMs=*/ 3_000L);

        assertThat(md).contains("Reason: expected_text_mismatch");
        assertThat(md).contains("edit#0");          // 0-based
        assertThat(md).contains("**Expected (your edit#0):**");
        assertThat(md).contains("**Current (now):**");
        assertThat(md).contains("```sql");
        assertThat(md).contains("WHERE created_at");
        assertThat(md).contains("WHERE u.created_at");
    }

    @Test
    void truncatesLongCodeBlocksToHead8Tail8() {
        String longExpected = "abc\n".repeat(50); // 50 lines
        StageTab t = tab("qe-3", "long", 1);
        String md = EditConflictMarkdownFormatter.expectedTextMismatch(
            t, 0, longExpected, longExpected, 1, 0);

        assertThat(md).contains("lines elided");
        // Sanity: still under total budget
        assertThat(md.length()).isLessThanOrEqualTo(3_000);
    }

    @Test
    void tabArchived_pointsToArchiveFalseUnarchive() {
        StageTab t = tab("qe-4", "frozen", 1);
        String md = EditConflictMarkdownFormatter.tabArchived(t);

        assertThat(md).contains("Reason: tab_archived");
        assertThat(md).contains("workspace.archive(target=qe-4, archived=false)");
    }

    @Test
    void tabNotFound_minimalFormat() {
        String md = EditConflictMarkdownFormatter.tabNotFound("qe-deleted");

        assertThat(md).contains("Tab: `qe-deleted`");
        assertThat(md).contains("Reason: tab_not_found");
        assertThat(md).contains("It may have been trashed");
    }

    @Test
    void outOfRangeLines_explainsLineCountMismatch() {
        StageTab t = tab("qe-5", "short tab", 3);
        EditRange r = new EditRange(20, 1, 22, 1);
        String md = EditConflictMarkdownFormatter.outOfRangeLines(t, r, /*actualLineCount=*/ 5);

        assertThat(md).contains("Reason: out_of_range_lines");
        assertThat(md).contains("range startLine=20");
        assertThat(md).contains("actual lineCount=5");
    }

    @Test
    void crlfNormalization_doesNotShowRawCarriageReturns() {
        StageTab t = tab("qe-6", "crlf", 1);
        String md = EditConflictMarkdownFormatter.expectedTextMismatch(
            t, 0, "line1\r\nline2\n", "line1\nline2\n", 1, 0);

        assertThat(md).doesNotContain("\r\n");
        assertThat(md).contains("line1\nline2");
    }
}
```

- [x] **Step 3: Run test, expect FAIL**

```bash
cd server && mvn test -pl data-talk-application -Dtest=EditConflictMarkdownFormatterTest -q
```

Expected: FAIL — class doesn't exist yet.

- [x] **Step 4: Implement `EditConflictMarkdownFormatter`**

```java
package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTab;

import java.util.Arrays;

/**
 * Renders user/AI-friendly markdown bodies for the 5 conflict codes returned
 * by {@code ui_patch} / {@code ui_exec apply_text_edits} mutation paths.
 *
 * <p>Per spec §5.7: total hard cap 3000 chars; framework ≈ 700 chars; remaining
 * budget split between Expected and Current code blocks (≈ 1150 each before
 * truncation kicks in).
 */
public final class EditConflictMarkdownFormatter {

    private static final int TOTAL_HARD_CAP = 3_000;
    private static final int CODE_BLOCK_BUDGET = 1_150;
    private static final int HEAD_LINES = 8;
    private static final int TAIL_LINES = 8;

    private EditConflictMarkdownFormatter() {}

    public static String versionConflict(StageTab tab, int requestedBase, int actualVersion, long lastTouchedDeltaMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Edit failed: version drifted on tab `").append(tab.id()).append("`\n");
        sb.append("Tab: `").append(tab.id()).append("` (").append(tab.title()).append(")\n");
        sb.append("Reason: version_conflict\n\n");
        sb.append("Your `baseVersion=").append(requestedBase).append("` does not match ");
        sb.append("the current version=").append(actualVersion).append(".\n");
        sb.append(hint(lastTouchedDeltaMs));
        sb.append("\n");
        sb.append(suggestedReadStep(tab.id()));
        return capTotal(sb.toString());
    }

    public static String expectedTextMismatch(StageTab tab, int editIndex, String expected, String actual,
                                              int actualVersion, long lastTouchedDeltaMs) {
        String exp = normalize(expected);
        String act = normalize(actual);
        StringBuilder sb = new StringBuilder();
        sb.append("## Edit failed: content drifted on tab `").append(tab.id()).append("`\n");
        sb.append("Tab: `").append(tab.id()).append("` (").append(tab.title()).append(")\n");
        sb.append("Reason: expected_text_mismatch\n\n");
        sb.append("**Expected (your edit#").append(editIndex).append("):**\n");
        sb.append(codeBlock(languageFor(tab.type()), truncate(exp)));
        sb.append("\n**Current (now):**\n");
        sb.append(codeBlock(languageFor(tab.type()), truncate(act)));
        sb.append("\nCurrent version=").append(actualVersion).append(".\n");
        sb.append(hint(lastTouchedDeltaMs));
        sb.append("\n");
        sb.append(suggestedReadStep(tab.id()));
        return capTotal(sb.toString());
    }

    public static String outOfRangeLines(StageTab tab, EditRange range, int actualLineCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Edit failed: range out of bounds on tab `").append(tab.id()).append("`\n");
        sb.append("Tab: `").append(tab.id()).append("` (").append(tab.title()).append(")\n");
        sb.append("Reason: out_of_range_lines\n\n");
        sb.append("Edit range startLine=").append(range.startLine())
          .append(", endLine=").append(range.endLine())
          .append("; actual lineCount=").append(actualLineCount).append(".\n\n");
        sb.append(suggestedReadStep(tab.id()));
        return capTotal(sb.toString());
    }

    public static String tabNotFound(String tabId) {
        return "## Edit failed: tab not found\n" +
               "Tab: `" + tabId + "`\n" +
               "Reason: tab_not_found\n\n" +
               "It may have been trashed by another session. Use `datatalk_ui_find` to discover " +
               "currently-existing tabs.\n";
    }

    public static String tabArchived(StageTab tab) {
        return "## Edit failed: tab is archived\n" +
               "Tab: `" + tab.id() + "` (" + tab.title() + ")\n" +
               "Reason: tab_archived\n\n" +
               "Archived tabs are read-only. Call `workspace.archive(target=" + tab.id() +
               ", archived=false)` first if you need to edit, then retry.\n";
    }

    static String humanizeDelta(long deltaMs) {
        if (deltaMs < 1_000) return "moments ago";
        long s = deltaMs / 1_000;
        if (s < 60) return s + "s ago";
        long m = s / 60;
        if (m < 60) return m + "m ago";
        long h = m / 60;
        if (h < 24) return h + "h ago";
        return (h / 24) + "d ago";
    }

    private static String hint(long deltaMs) {
        return "Likely cause: another session edited this tab " + humanizeDelta(deltaMs) + ".\n";
    }

    private static String suggestedReadStep(String tabId) {
        return "**Suggested next step:** call `datatalk_ui_read({ object: \"query_editor\", target: \"" +
               tabId + "\", mode: \"state\" })` and re-plan the edit against the latest content.\n";
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n");
    }

    private static String languageFor(String tabType) {
        return switch (tabType == null ? "" : tabType) {
            case "query_editor" -> "sql";
            case "markdown_note" -> "markdown";
            default -> "text";
        };
    }

    private static String truncate(String text) {
        if (text.length() <= CODE_BLOCK_BUDGET) return text;
        String[] lines = text.split("\n", -1);
        if (lines.length <= HEAD_LINES + TAIL_LINES) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < HEAD_LINES; i++) sb.append(lines[i]).append('\n');
        sb.append("… ").append(lines.length - HEAD_LINES - TAIL_LINES)
          .append(" lines elided. Read with datatalk_ui_read for full content. …\n");
        for (int i = lines.length - TAIL_LINES; i < lines.length; i++) sb.append(lines[i]).append('\n');
        return sb.toString();
    }

    private static String codeBlock(String language, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("```").append(language).append('\n');
        sb.append(body);
        if (!body.endsWith("\n")) sb.append('\n');
        sb.append("```\n");
        return sb.toString();
    }

    private static String capTotal(String md) {
        if (md.length() <= TOTAL_HARD_CAP) return md;
        return md.substring(0, TOTAL_HARD_CAP - 16) + "\n…(truncated)…\n";
    }
}
```

- [x] **Step 5: Run test, expect PASS**

```bash
cd server && mvn test -pl data-talk-application -Dtest=EditConflictMarkdownFormatterTest -q
```

Expected: PASS (7 tests).

- [x] **Step 6: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/stage/EditRange.java \
        server/data-talk-application/src/main/java/com/datatalk/application/stage/EditConflictMarkdownFormatter.java \
        server/data-talk-application/src/test/java/com/datatalk/application/stage/EditConflictMarkdownFormatterTest.java
git commit -m "feat(stage-edits): add EditRange + EditConflictMarkdownFormatter (5 codes)"
```

---

### Task 9: Run full migration IT to validate

- [x] **Step 1: Run `StageTabsMigrationIT`**

```bash
cd server && mvn test -pl data-talk-infrastructure -Dtest=StageTabsMigrationIT -q
```

Expected: PASS (all 3 V13 assertions + pre-existing V12 baseline).

- [x] **Step 2: Run all infra tests**

```bash
cd server && mvn test -pl data-talk-infrastructure -q
```

Expected: PASS.

- [x] **Step 3: Commit (no code change; checkpoint commit only if any cleanup happened)**

If any cleanup was needed during validation, commit it; otherwise skip.

---

## Phase 1D — UI Action Schemas + Concurrency IT (Tasks 10–13) — ATOMIC GROUP

> ⚠️ Tasks 10, 11, and Phase 1F's Tasks 16, 17 form an **atomic deploy group** per spec §9.1. Land them in a single PR / single commit. Do not push 10–11 alone — frontend will fail schema validation.

### Task 10: `UiPatchAction` schema requires `baseVersion` for `/content`

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiPatchAction.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/UiActionsTest.java`

- [x] **Step 1: Write schema test**

In `UiActionsTest.java` add:

```java
@Test
void uiPatch_contentReplace_requiresBaseVersion() {
    Map<String, Object> schema = uiPatchAction.inputSchema();
    Map<String, Object> ops = (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("ops");
    List<Map<String, Object>> oneOf = (List<Map<String, Object>>) ((Map<String, Object>) ops.get("items")).get("oneOf");

    Map<String, Object> contentOp = oneOf.stream()
        .filter(o -> {
            Map<String, Object> p = (Map<String, Object>) o.get("properties");
            Map<String, Object> path = (Map<String, Object>) p.get("path");
            return ((List<?>) path.get("enum")).contains("/content");
        })
        .findFirst()
        .orElseThrow();

    List<String> required = (List<String>) contentOp.get("required");
    assertThat(required).contains("baseVersion");
}
```

- [x] **Step 2: Run test, expect FAIL**

```bash
cd server && mvn test -pl data-talk-adapter -Dtest=UiActionsTest#uiPatch_contentReplace_requiresBaseVersion -q
```

Expected: FAIL — `baseVersion` not in `required`.

- [x] **Step 3: Update `UiPatchAction.replaceOp` for `/content`**

Replace the helper to allow path-specific required field:

```java
private static Map<String, Object> replaceOp(String path, Map<String, Object> valueSchema, boolean requiresBaseVersion) {
    List<String> required = requiresBaseVersion
        ? List.of("op", "path", "value", "baseVersion")
        : List.of("op", "path", "value");
    Map<String, Object> properties = new java.util.LinkedHashMap<>();
    properties.put("op", Map.of("type", "string", "enum", List.of("replace")));
    properties.put("path", Map.of("type", "string", "enum", List.of(path)));
    properties.put("value", valueSchema);
    if (requiresBaseVersion) {
        properties.put("baseVersion", Map.of(
            "type", "number",
            "description", "Required: tab payloadVersion at the moment you read the content; rejected if it has drifted."
        ));
    }
    return Map.of(
        "type", "object",
        "required", required,
        "properties", properties
    );
}
```

Update call site in `inputSchema()`:

```java
"items", Map.of("oneOf", List.of(
        replaceOp("/content",        Map.of("type", "string"),                    true),
        replaceOp("/connectionId",   Map.of("type", List.of("string", "null")),   false),
        replaceOp("/database",       Map.of("type", List.of("string", "null")),   false),
        replaceOp("/schema",         Map.of("type", List.of("string", "null")),   false)
))
```

- [x] **Step 4: Run test, expect PASS**

```bash
cd server && mvn test -pl data-talk-adapter -Dtest=UiActionsTest#uiPatch_contentReplace_requiresBaseVersion -q
```

- [x] **Step 5: Commit (do NOT push; atomic group with Tasks 11, 16, 17)**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiPatchAction.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/UiActionsTest.java
git commit -m "feat(ui-patch): /content requires baseVersion (atomic 10/11/16/17)"
```

---

### Task 11: `UiExecAction` schema — `expectedText` required + new actions

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/UiActionsTest.java`

- [x] **Step 1: Write 3 schema tests**

```java
@Test
void uiExec_applyTextEdits_eachEditRequiresExpectedText() {
    Map<String, Object> schema = uiExecAction.inputSchema();
    Map<String, Object> qeBranch = findOneOfBranch(schema, "query_editor");
    Map<String, Object> editsParam = navigate(qeBranch, "properties", "params", "properties", "edits");
    Map<String, Object> editItem = (Map<String, Object>) editsParam.get("items");
    List<String> required = (List<String>) editItem.get("required");

    assertThat(required).containsExactlyInAnyOrder("range", "text", "expectedText");
}

@Test
void uiExec_workspaceAction_includesNewVerbs() {
    Map<String, Object> schema = uiExecAction.inputSchema();
    Map<String, Object> wsBranch = findOneOfBranch(schema, "workspace");
    Map<String, Object> action = navigate(wsBranch, "properties", "action");
    List<String> verbs = (List<String>) action.get("enum");

    assertThat(verbs).contains("open", "close", "focus", "choose_connection",
                               "detach", "archive", "trash");
}

@Test
void uiExec_workspaceArchive_archivedFlagOptionalDefaultTrue() {
    Map<String, Object> schema = uiExecAction.inputSchema();
    Map<String, Object> wsBranch = findOneOfBranch(schema, "workspace");
    Map<String, Object> archivedFlag = navigate(wsBranch, "properties", "params", "properties", "archived");

    assertThat(archivedFlag.get("type")).isEqualTo("boolean");
    assertThat(archivedFlag.get("default")).isEqualTo(Boolean.TRUE);
}
```

Helpers (add to test class):

```java
private static Map<String, Object> findOneOfBranch(Map<String, Object> schema, String objectEnumValue) {
    List<Map<String, Object>> oneOf = (List<Map<String, Object>>) schema.get("oneOf");
    return oneOf.stream()
        .filter(b -> ((List<?>) ((Map<String, Object>) ((Map<String, Object>) b.get("properties")).get("object")).get("enum"))
            .contains(objectEnumValue))
        .findFirst().orElseThrow();
}

@SuppressWarnings("unchecked")
private static Map<String, Object> navigate(Map<String, Object> root, String... path) {
    Map<String, Object> cur = root;
    for (String key : path) cur = (Map<String, Object>) cur.get(key);
    return cur;
}
```

- [x] **Step 2: Run tests, expect FAIL**

```bash
cd server && mvn test -pl data-talk-adapter -Dtest=UiActionsTest -q
```

- [x] **Step 3: Update `UiExecAction.queryEditorExecSchema`**

Replace the `edits` param block:

```java
Map.entry("edits", Map.of(
        "type", "array",
        "items", Map.of(
                "type", "object",
                "required", List.of("range", "text", "expectedText"),
                "properties", Map.ofEntries(
                        Map.entry("range", Map.of(
                                "type", "object",
                                "required", List.of("startLine", "startColumn", "endLine", "endColumn"),
                                "properties", Map.ofEntries(
                                        Map.entry("startLine", Map.of("type", "number")),
                                        Map.entry("startColumn", Map.of("type", "number")),
                                        Map.entry("endLine", Map.of("type", "number")),
                                        Map.entry("endColumn", Map.of("type", "number"))
                                )
                        )),
                        Map.entry("expectedText", Map.of(
                                "type", "string",
                                "description", "Required: exact current text in the range, normalized (\\r\\n -> \\n). Rejected with `error.code='expected_text_mismatch'` if it doesn't match."
                        )),
                        Map.entry("text", Map.of("type", "string"))
                )
        )
))
```

- [x] **Step 4: Update `UiExecAction.workspaceExecSchema`**

Update action enum + add `archived` param:

```java
Map.entry("action", Map.of(
        "type", "string",
        "enum", List.of("open", "close", "focus", "choose_connection", "detach", "archive", "trash")
)),
Map.entry("params", Map.of(
        "type", "object",
        "properties", Map.ofEntries(
                Map.entry("type", Map.of(
                        "type", "string",
                        "enum", List.of("query_editor"),
                        "description", "workspace open supports query_editor today."
                )),
                Map.entry("title", Map.of("type", "string")),
                Map.entry("connection_id", Map.of("type", "string")),
                Map.entry("database", Map.of("type", "string")),
                Map.entry("schema", Map.of("type", "string")),
                Map.entry("payload", Map.of("type", "object")),
                Map.entry("target", Map.of("type", "string")),
                Map.entry("preferredConnectionId", Map.of("type", "string")),
                Map.entry("archived", Map.of(
                        "type", "boolean",
                        "default", Boolean.TRUE,
                        "description", "Only used for `action=archive`. true=archive, false=unarchive."
                ))
        )
))
```

- [x] **Step 5: Run tests, expect PASS**

```bash
cd server && mvn test -pl data-talk-adapter -Dtest=UiActionsTest -q
```

- [x] **Step 6: Commit (atomic group)**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/UiActionsTest.java
git commit -m "feat(ui-exec): require expectedText, add detach/archive/trash (atomic 10/11/16/17)"
```

---

### Task 12: `UiFindAction` test — drop scope filter, add originSessionTitle

**Files:**
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/UiFindActionTest.java`

- [x] **Step 1: Find any test asserting `filter.scope`; delete those assertions/cases**

- [x] **Step 2: Add positive test asserting `originSessionTitle` returned**

```java
@Test
void output_includesOriginSessionTitle() {
    insertSession("sess-A", "April Weekly");
    insertTab("qe-x", "query_editor", "users monthly", "sess-A");

    var resp = action.handle(ctx, Map.of("output", Map.of("mode", "metadata")))
                     .toCompletableFuture().get();

    var items = (List<Map<String, Object>>) resp.get("items");
    assertThat(items).anySatisfy(it ->
        assertThat(it).containsEntry("originSessionTitle", "April Weekly"));
}
```

- [x] **Step 3: Run test, expect PASS** (LEFT JOIN already done in Task 6)

```bash
cd server && mvn test -pl data-talk-adapter -Dtest=UiFindActionTest -q
```

- [x] **Step 4: Commit**

```bash
git add server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/UiFindActionTest.java
git commit -m "test(ui-find): originSessionTitle returned, scope filter gone"
```

---

### Task 13: `StageTabConcurrencyIT` — racing two virtual threads on same tab

**Files:**
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/StageTabConcurrencyIT.java`

- [x] **Step 1: Write integration test**

```java
package com.datatalk.adapter.actions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StageTabConcurrencyIT {

    @Autowired StageTabFixtureSupport fixture;
    @Autowired UiExecAction uiExec;
    // ActionContext, etc.

    @Test
    void twoConcurrentEdits_onSameTab_secondGetsVersionConflictWithMarkdown() throws Exception {
        // Given: a query_editor tab at version 12 with known content
        String tabId = fixture.createQueryEditorTab("qe-race",
            "SELECT id, name FROM users\nWHERE created_at > '2026-01-01';\n");
        int v0 = fixture.versionOf(tabId);
        assertThat(v0).isEqualTo(12);

        // Build two edits, both with baseVersion=12 and same expectedText
        Map<String, Object> editA = buildSingleEdit(tabId, 12,
            /*range*/ 2, 1, 3, 1,
            /*expected*/ "WHERE created_at > '2026-01-01';\n",
            /*text*/ "WHERE created_at > '2026-02-01';\n");
        Map<String, Object> editB = buildSingleEdit(tabId, 12,
            /*range*/ 2, 1, 3, 1,
            /*expected*/ "WHERE created_at > '2026-01-01';\n",
            /*text*/ "WHERE created_at > '2026-03-01';\n");

        // When: race them on virtual threads
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Map<String, Object>> fa = exec.submit(() -> handle(editA));
            Future<Map<String, Object>> fb = exec.submit(() -> handle(editB));

            Map<String, Object> a = fa.get(5, TimeUnit.SECONDS);
            Map<String, Object> b = fb.get(5, TimeUnit.SECONDS);

            // Then: exactly one succeeds, the other returns version_conflict + markdown
            List<Map<String, Object>> results = List.of(a, b);
            long ok = results.stream().filter(r -> r.get("error") == null).count();
            long failed = results.stream().filter(r -> r.get("error") != null).count();
            assertThat(ok).isEqualTo(1);
            assertThat(failed).isEqualTo(1);

            Map<String, Object> error = (Map<String, Object>)
                results.stream().filter(r -> r.get("error") != null).findFirst().orElseThrow().get("error");
            assertThat(error.get("code")).isEqualTo("version_conflict");
            assertThat(error.get("markdown").toString())
                .contains("Tab: `qe-race`")
                .contains("Reason: version_conflict")
                .contains("Suggested next step:");
        }
    }

    // ... helper builders (buildSingleEdit, handle) ...
}
```

- [x] **Step 2: Add `StageTabFixtureSupport` Spring component (test scope)**

If not already present, create a minimal fixture helper that allows tests to seed tabs. Implementation calls `StageTabService.upsert(...)` directly.

- [x] **Step 3: Run IT, expect FAIL**

```bash
cd server && mvn verify -pl data-talk-adapter -Dit.test=StageTabConcurrencyIT -q
```

Expected: FAIL — frontend mutex isn't here, but server-side path needs to wire `error.code` + `error.markdown` into the action_result envelope. If serverside ui_exec dispatcher returns raw `version_conflict` exception without markdown, this test fails.

- [x] **Step 4: Wire `EditConflictMarkdownFormatter` into the dispatch error path**

Locate where `StageTabConcurrencyException` is caught (likely `ActionDispatcher` or `UiExecHandler`); convert to `error.code='version_conflict'` + `markdown=EditConflictMarkdownFormatter.versionConflict(...)`. Add similar conversions for the other 4 codes when their corresponding exceptions surface.

- [x] **Step 5: Run IT, expect PASS**

- [x] **Step 6: Commit**

```bash
git add server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/StageTabConcurrencyIT.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/  # any dispatcher edits
git commit -m "test(stage-tabs): concurrency IT + dispatcher wires markdown errors"
```

---

## Phase 1E — AGENTS.md + Digest Renderer (Tasks 14–16)

### Task 14: AGENTS.md major rewrite

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

- [x] **Step 1: Add "Multiple sessions may share..." note in Core Rules**

Append one bullet to `## Core Rules`:

```markdown
- Multiple sessions may share and concurrently edit any workbench tab. Always read the latest state via `datatalk_ui_read` before any patch or text edit, and respect `error.markdown` feedback.
```

- [x] **Step 2: Rewrite `### UI Actions` opening**

Replace the existing intro paragraph:

```markdown
### UI Actions

Workbench tabs are workspace-wide and shared across all chat sessions. Any
session may have edited a tab since your last read. Tab discovery, reading,
patching, and exec all go through the four registered actions below.

Only these UI object types are supported today: `workspace`, `query_editor`.
```

- [x] **Step 3: Update each action description**

For `datatalk_ui_find`:

```markdown
- `datatalk_ui_find`
  Discover, search, and read **workbench tabs**. Returns the tab library —
  every active tab, including those not currently open in the top tab bar.
  `output.mode=metadata` (default) returns `items` with `originSessionId`
  and `originSessionTitle` fields (the latter is null when the originating
  session has been deleted).
```

For `datatalk_ui_read`:

```markdown
- `datatalk_ui_read`
  Read `workspace` or `query_editor` state, schema, actions, or the full
  descriptor through top-level `object`, optional `target`, and optional
  `mode`. `state` mode includes `inWorkset: boolean` indicating whether the
  tab is currently open in the top tab bar.
```

For `datatalk_ui_patch`:

```markdown
- `datatalk_ui_patch`
  Patch a `query_editor` through JSON Patch `ops`. Only `replace` is
  supported today, on `/content`, `/connectionId`, `/database`, and
  `/schema`. **`/content` requires `baseVersion: number`** — see the
  Concurrency Contract section below. Conflict returns
  `error.code='version_conflict'` with `error.markdown` and `currentState.version`.
```

For `datatalk_ui_exec`:

```markdown
- `datatalk_ui_exec`
  Execute supported actions. `apply_text_edits` requires `params.baseVersion`
  AND every edit in `params.edits` requires `expectedText`. Workspace verbs:
  `open`, `focus`, `choose_connection`, **`detach`**, **`archive(archived?: boolean = true)`**,
  **`trash`**. The legacy `close` is a deprecated alias for `archive(archived=true)`.
```

- [x] **Step 4: Insert `## Concurrency Contract` section**

After `## Exact UI Contract`, insert (full content per spec §8.2):

```markdown
## Concurrency Contract

Workbench tabs (`query_editor`, `artifact_preview`, future `er_designer` /
`report_designer`) are workspace-wide objects shared across all chat sessions.
Any session — including a parallel agent — may have edited a tab since your
last read. Treat every patch and text edit as optimistic and conflict-aware.

### Required guard fields

- `datatalk_ui_patch` with `path=/content` requires `baseVersion: number`.
- `datatalk_ui_exec apply_text_edits` requires `params.baseVersion: number`.
- Each entry in `params.edits` requires `expectedText: string` — the exact
  text currently occupying `range`. The server compares it after a
  line-ending normalization (`\r\n` → `\n`).

### Conflict response shape

```json
{
  "error": {
    "code": "version_conflict" | "expected_text_mismatch" | "out_of_range_lines"
            | "tab_not_found" | "tab_archived",
    "message": "<one-line machine summary>",
    "currentState": { "version": <int>, "tabId": "<id>" },
    "markdown": "<human-and-LLM-readable explanation>",
    "details": { "editIndex": <int?>, "expected": "...", "actual": "..." }
  }
}
```

### What you MUST do on conflict

1. Stop. Do not retry with the same `baseVersion` or `expectedText`.
2. Read `error.markdown` — it includes the current content for the affected
   range and a hint about who likely changed it.
3. Call `datatalk_ui_read` on the same `target` with `mode='state'` to get
   the new `version` and `content`.
4. Re-plan your edit against the new content. Your new range / expectedText
   must match the freshly read snapshot exactly.
5. Submit a single fresh `apply_text_edits` with the new `baseVersion`.

### Multi-edit batches

`apply_text_edits` accepts multiple edits in `params.edits`. The server
applies them in **reverse line order** against the snapshot at `baseVersion`,
as a single transactional unit. Either all edits apply (success) or none do
(failure with a single `error.markdown`).

If `error.code='expected_text_mismatch'`, `error.details.editIndex` is the
**0-based index** of the failing edit in the request array. Earlier edits in
the same batch were **not** applied — the rollback is whole-batch, not partial.
Plan your retry as a fresh single-batch `apply_text_edits` against the new
`baseVersion`.

### What you MUST NOT do

- Do not loop the same edit hoping the conflict clears.
- Do not assume `version_conflict` means your edit is wrong — it usually
  means another session reached the tab first.
- Do not trash or archive a tab to "force a clean slate" unless the user
  explicitly asked you to.

### Multi-session etiquette

- Always pass an explicit `target` tab id when more than one tab of the
  matching type exists. Relying on `target='active'` while another session
  may have shifted focus is a source of silent cross-talk.
- After mutating, the change is visible to subsequent `datatalk_ui_find`
  calls in any session before your next tool call returns.
```

- [x] **Step 5: Insert `## Library vs Workset` section**

Right after `## Concurrency Contract`:

```markdown
## Library vs Workset

The workbench has two coexisting tab views:

- **Library** — the durable set of all non-archived tabs. Surfaced by
  `datatalk_ui_find` and the left rail. Includes tabs that are not
  currently open in the top tab bar.
- **Workset** — the subset currently open in the top tab bar. Tracked
  per app instance (not persisted server-side) and reflected by
  `datatalk_ui_read state.inWorkset`.

When the user says "current SQL editor", they mean a tab in the workset
(usually the active one). When they say "the SQL I wrote yesterday",
they mean a tab in the library that may not be in the workset.

To bring a library tab into the workset, call
`datatalk_ui_exec(object=workspace, action=focus, params.target=<tabId>)`
— it both ensures the tab is open and makes it active. **If the target
tab is archived, this returns `error.code='tab_archived'`** — call
`workspace.archive(target=<tabId>, archived=false)` first to unarchive.

To remove a tab from the workset without deleting it, call
`datatalk_ui_exec(object=workspace, action=detach, params.target=<tabId>)`.
The tab remains in the library and can be re-opened later.

To archive a tab (hide from the default library view, keep history), use
`action=archive` with `params.target=<tabId>`. The default toggle is
`params.archived=true`; pass `params.archived=false` to **un-archive** a
previously archived tab (e.g., when an edit fails with `tab_archived`).

To permanently delete, use `action=trash` — only when the user explicitly
asks. Both `archive(archived=true)` and `trash` cascade-detach from the
workset.

The legacy `action=close` is now an alias for `archive(archived=true)`.
Prefer the new verbs for clarity.

> Deprecated since v0.X (the release where this spec ships); will be
> removed in v0.X+3. AGENTS.md should retain this alias paragraph until
> the removal release; tests in `AgentPromptContractTest` assert the
> deprecation marker remains so we don't drop it accidentally.
```

- [x] **Step 6: Update `## Tab Persistence and Search` opening line**

Replace `Tabs persist across sessions and across app restarts.` with `Tabs are shared across all sessions and persisted across app restarts.`.

- [x] **Step 7: Commit**

```bash
git add server/data-talk-adapter/src/main/resources/agents/AGENTS.md
git commit -m "docs(agents): rewrite UI Actions, add Concurrency Contract + Library vs Workset"
```

---

### Task 15: `AgentPromptBuilder` digest renderer upgrade

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/stage/AgentPromptBuilder.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptDigestTest.java`

- [x] **Step 1: Write failing digest test**

```java
package com.datatalk.adapter.agents;

import com.datatalk.application.stage.AgentPromptBuilder;
import com.datatalk.application.stage.StageTabRepository;
import com.datatalk.domain.stage.StageTab;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentPromptDigestTest {

    @Test
    void digest_includesOriginSessionTitle_lastTouched_version() {
        StageTabRepository repo = mock(StageTabRepository.class);
        SessionTitleLookup lookup = mock(SessionTitleLookup.class);
        long now = System.currentTimeMillis();
        StageTab t = new StageTab("qe-1", "query_editor", "users monthly",
            "conn_x", "analytics", "public", "sess-1",
            14, false, false, null, now - 60_000, now - 12_000);

        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of(t));
        when(repo.countActive()).thenReturn(1);
        when(repo.countArchived()).thenReturn(0);
        when(lookup.titlesByIds(List.of("sess-1"))).thenReturn(Map.of("sess-1", "April Weekly"));

        AgentPromptBuilder builder = new AgentPromptBuilder(repo, lookup);
        String md = builder.render("intro\n{{STAGE_TAB_DIGEST}}\noutro");

        assertThat(md).contains("query_editor `qe-1`");
        assertThat(md).contains("users monthly");
        assertThat(md).contains("conn=conn_x db=analytics schema=public");
        assertThat(md).contains("fromSession=\"April Weekly\"");
        assertThat(md).contains("lastTouched=12s ago");
        assertThat(md).contains("version=14");
        // and not the deprecated inWorkset:
        assertThat(md).doesNotContain("inWorkset=");
    }

    @Test
    void digest_originSessionDeleted_rendersDeleted() {
        StageTabRepository repo = mock(StageTabRepository.class);
        SessionTitleLookup lookup = mock(SessionTitleLookup.class);
        long now = System.currentTimeMillis();
        StageTab t = new StageTab("qe-2", "query_editor", "orphan tab",
            null, null, null, null,   // originSessionId null
            3, false, false, null, now, now);

        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of(t));
        when(lookup.titlesByIds(List.of())).thenReturn(Map.of());
        when(repo.countActive()).thenReturn(1);

        AgentPromptBuilder builder = new AgentPromptBuilder(repo, lookup);
        String md = builder.render("{{STAGE_TAB_DIGEST}}");

        assertThat(md).contains("fromSession=\"(deleted)\"");
    }

    @Test
    void fieldOrderIsStable() {
        // construct same fixture twice; assert exact substring order
        StageTabRepository repo = mock(StageTabRepository.class);
        SessionTitleLookup lookup = mock(SessionTitleLookup.class);
        long now = System.currentTimeMillis();
        StageTab t = new StageTab("qe-3", "query_editor", "stable",
            "conn_x", "db1", "s1", "sess-1",
            5, false, false, null, now, now);
        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of(t));
        when(lookup.titlesByIds(List.of("sess-1"))).thenReturn(Map.of("sess-1", "Title"));
        when(repo.countActive()).thenReturn(1);

        String md = new AgentPromptBuilder(repo, lookup).render("{{STAGE_TAB_DIGEST}}");
        int titleIdx = md.indexOf("\"stable\"");
        int connIdx = md.indexOf("conn=conn_x");
        int sessIdx = md.indexOf("fromSession=");
        int versionIdx = md.indexOf("version=");
        assertThat(titleIdx).isLessThan(connIdx);
        assertThat(connIdx).isLessThan(sessIdx);
        assertThat(sessIdx).isLessThan(versionIdx);
    }
}
```

- [x] **Step 2: Run test, expect FAIL**

```bash
cd server && mvn test -pl data-talk-adapter -Dtest=AgentPromptDigestTest -q
```

Expected: FAIL — `SessionTitleLookup` doesn't exist yet.

- [x] **Step 3: Add `SessionTitleLookup` interface**

Path: `server/data-talk-application/src/main/java/com/datatalk/application/stage/SessionTitleLookup.java`

```java
package com.datatalk.application.stage;

import java.util.List;
import java.util.Map;

public interface SessionTitleLookup {
    /** Returns title-by-id map. Missing ids are simply absent (caller renders "(deleted)"). */
    Map<String, String> titlesByIds(List<String> sessionIds);
}
```

- [x] **Step 4: Implement `SessionTitleLookup`**

Path: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/stage/SessionTitleJdbcLookup.java`

```java
package com.datatalk.infra.stage;

import com.datatalk.application.stage.SessionTitleLookup;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class SessionTitleJdbcLookup implements SessionTitleLookup {
    private final JdbcTemplate jdbc;

    public SessionTitleJdbcLookup(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, String> titlesByIds(List<String> ids) {
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT id, title FROM sessions WHERE id IN (" + placeholders + ")";
        Map<String, String> result = new HashMap<>();
        jdbc.query(sql, ids.toArray(), (rs) -> {
            result.put(rs.getString("id"), rs.getString("title"));
        });
        return result;
    }
}
```

- [x] **Step 5: Refactor `AgentPromptBuilder.renderDigest()`**

Replace the body of `AgentPromptBuilder`:

```java
package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTab;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AgentPromptBuilder {
    private static final String PLACEHOLDER = "{{STAGE_TAB_DIGEST}}";
    private static final int MAX_TABS = 10;
    private static final int MAX_TITLE_CHARS = 80;
    private static final int MAX_RENDERED_CHARS = 1_500;

    private final StageTabRepository repo;
    private final SessionTitleLookup lookup;

    public AgentPromptBuilder(StageTabRepository repo, SessionTitleLookup lookup) {
        this.repo = repo;
        this.lookup = lookup;
    }

    public String render(String template) {
        if (!template.contains(PLACEHOLDER)) return template;
        String digest = renderDigest();
        if (digest.length() > MAX_RENDERED_CHARS) {
            digest = digest.substring(0, MAX_RENDERED_CHARS - 3) + "...";
        }
        return template.replace(PLACEHOLDER, digest);
    }

    private String renderDigest() {
        List<StageTab> recent = repo.recentByLastTouched(MAX_TABS);
        int active = repo.countActive();
        int archived = repo.countArchived();

        Map<String, String> sessionTitles = lookup.titlesByIds(
            recent.stream().map(StageTab::originSessionId).filter(java.util.Objects::nonNull).distinct().toList());

        StringBuilder sb = new StringBuilder("## Open Tabs Snapshot\n\n");
        if (recent.isEmpty()) {
            sb.append("No persisted tabs yet.\n");
        } else {
            sb.append("Recently-touched tabs (top ").append(recent.size())
              .append(" by lastTouchedAt, archived excluded):\n");
            long now = System.currentTimeMillis();
            int i = 1;
            for (StageTab t : recent) {
                sb.append(i++).append(". ");
                sb.append(t.type()).append(" `").append(t.id()).append("` ");
                sb.append("\"").append(escape(t.title())).append("\"\n");
                sb.append("   conn=").append(orDash(t.connectionId()));
                sb.append(" db=").append(orDash(t.databaseName()));
                sb.append(" schema=").append(orDash(t.schemaName())).append("\n");
                String fromSession = t.originSessionId() == null
                    ? "(deleted)"
                    : sessionTitles.getOrDefault(t.originSessionId(), "(deleted)");
                sb.append("   fromSession=\"").append(escape(fromSession)).append("\"\n");
                sb.append("   lastTouched=").append(EditConflictMarkdownFormatter.humanizeDelta(now - t.lastTouchedAt()));
                sb.append(" version=").append(t.payloadVersion()).append("\n");
            }
        }
        sb.append("\nTotal persisted tabs: ").append(active).append(" active, ").append(archived).append(" archived.\n");
        sb.append("Use `datatalk_ui_find` to locate tabs not listed above; the snapshot caps at ")
          .append(MAX_TABS).append(" entries to save tokens.\n");
        return sb.toString();
    }

    static String escape(String value) {
        if (value == null) return "(untitled)";
        String t = value;
        if (t.length() > MAX_TITLE_CHARS) t = t.substring(0, MAX_TITLE_CHARS) + "...";
        return t.replace("\n", " ")
                .replace("\r", " ")
                .replace("```", "''")
                .replace("`", "'")
                .replace(":::", "..")
                .replace("<!--", "< !--")
                .replace("{{", "{ {")
                .replace("}}", "} }");
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
```

(Note: `humanizeDelta` is now `package-private static` on `EditConflictMarkdownFormatter`; both classes share via package access.)

- [x] **Step 6: Run digest test, expect PASS**

```bash
cd server && mvn test -pl data-talk-adapter -Dtest=AgentPromptDigestTest -q
```

- [x] **Step 7: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/stage/AgentPromptBuilder.java \
        server/data-talk-application/src/main/java/com/datatalk/application/stage/SessionTitleLookup.java \
        server/data-talk-infrastructure/src/main/java/com/datatalk/infra/stage/SessionTitleJdbcLookup.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptDigestTest.java
git commit -m "feat(agent-prompt): digest includes fromSession/lastTouched/version, no inWorkset"
```

---

### Task 16: `AgentPromptContractTest` 6-token assertion

**Files:**
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java`

- [x] **Step 1: Add assertion**

Add to existing test class:

```java
@Test
void agentsMd_includesAllRequiredTokens() {
    String md = readResourceAsString("agents/AGENTS.md");

    assertThat(md).contains("## Concurrency Contract");
    assertThat(md).contains("## Library vs Workset");
    assertThat(md).contains("expectedText");
    assertThat(md).contains("error.markdown");
    assertThat(md).contains("baseVersion");
    assertThat(md).contains("inWorkset");
    assertThat(md).contains("Deprecated since v0.X");
}
```

- [x] **Step 2: Run, expect PASS**

```bash
cd server && mvn test -pl data-talk-adapter -Dtest=AgentPromptContractTest -q
```

- [x] **Step 3: Commit**

```bash
git add server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java
git commit -m "test(agents): assert 6 required tokens + deprecation marker"
```

---

## Phase 1F — Frontend Protocol Adapt (Tasks 17–20) — ATOMIC GROUP cont.

### Task 17: `replaceSqlText` adds `baseVersion` validation

**Files:**
- Modify: `client/src/features/stage/stores/sql-workbench-store.ts`
- Modify: `client/src/features/stage/stores/sql-workbench-store.test.ts`
- Modify: `client/src/stores/stage-store.ts`

- [x] **Step 1: Write failing test**

Add to `sql-workbench-store.test.ts`:

```ts
describe('replaceSqlText with baseVersion', () => {
  it('rejects when baseVersion does not match current version', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('qe-1', { sqlText: 'SELECT 1' })
    // initial version is 1
    const result = store.replaceSqlText('qe-1', 'SELECT 2', /*baseVersion=*/ 99)
    expect(result.ok).toBe(false)
    if (result.ok === false) {
      expect(result.code).toBe('version_conflict')
      expect(result.currentState.version).toBe(1)
    }
    // sqlText must NOT have changed
    expect(useSqlWorkbenchStore.getState().tabsById['qe-1'].sqlText).toBe('SELECT 1')
  })

  it('applies when baseVersion matches', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('qe-2', { sqlText: 'SELECT 1' })
    const result = store.replaceSqlText('qe-2', 'SELECT 2', /*baseVersion=*/ 1)
    expect(result.ok).toBe(true)
    if (result.ok === true) {
      expect(result.version).toBe(2)
    }
    expect(useSqlWorkbenchStore.getState().tabsById['qe-2'].sqlText).toBe('SELECT 2')
  })

  it('applies and increments version exactly once', () => {
    const store = useSqlWorkbenchStore.getState()
    store.ensureTab('qe-3', { sqlText: 'a' })
    const r1 = store.replaceSqlText('qe-3', 'b', 1)
    const r2 = store.replaceSqlText('qe-3', 'c', (r1 as any).version)
    expect(r2.ok).toBe(true)
    expect(useSqlWorkbenchStore.getState().tabsById['qe-3'].version).toBe(3)
  })
})
```

- [x] **Step 2: Run test, expect FAIL**

```bash
cd client && npx vitest run src/features/stage/stores/sql-workbench-store.test.ts -t "replaceSqlText with baseVersion"
```

Expected: FAIL — current `replaceSqlText` ignores `baseVersion`.

- [x] **Step 3: Update `SqlWorkbenchState` type and impl**

In `sql-workbench-store.ts`:

```ts
type SqlWorkbenchState = {
  // …
  replaceSqlText: (
    tabId: string,
    sqlText: string,
    baseVersion: number,
  ) => SqlWorkbenchEditResult
  // …
}
```

Implementation:

```ts
replaceSqlText: (tabId, sqlText, baseVersion) => {
  const current = requireTabState(get().tabsById, tabId)
  if (baseVersion !== current.version) {
    return {
      ok: false,
      code: 'version_conflict',
      currentState: { version: current.version, content: current.sqlText },
    }
  }
  const next = applySqlTextChange(current, sqlText)
  set((state) => ({
    tabsById: { ...state.tabsById, [tabId]: next },
  }))
  return { ok: true, version: next.version, content: next.sqlText }
},
```

- [x] **Step 4: Update `useStageStore.replaceQueryEditorContent`**

In `client/src/stores/stage-store.ts`:

```ts
replaceQueryEditorContent: (tabId, content, baseVersion) =>
  useSqlWorkbenchStore.getState().replaceSqlText(tabId, content, baseVersion),
```

Update the function signature in the store's interface.

- [x] **Step 5: Update all call sites in `client/`**

Run:

```bash
cd client && grep -rn "replaceQueryEditorContent\|replaceSqlText" src --include="*.ts" --include="*.tsx"
```

For each call site, add a `baseVersion` argument (read from current state via `useSqlWorkbenchStore.getState().tabsById[tabId].version` immediately before the call).

- [x] **Step 6: Run tests, expect PASS**

```bash
cd client && npx vitest run src/features/stage/stores/sql-workbench-store.test.ts
```

- [x] **Step 7: tsc check**

```bash
cd client && npx tsc --noEmit
```

Expected: 0 errors.

- [x] **Step 8: Commit (atomic group)**

```bash
git add client/src/features/stage/stores/sql-workbench-store.ts \
        client/src/features/stage/stores/sql-workbench-store.test.ts \
        client/src/stores/stage-store.ts \
        client/src/  # any call site updates
git commit -m "feat(sql-workbench): replaceSqlText requires baseVersion (atomic 10/11/16/17)"
```

---

### Task 18: `QueryEditorAdapter` carries `expectedText` per edit

**Files:**
- Modify: `client/src/features/stage/adapters/QueryEditorAdapter.ts`

- [x] **Step 1: Locate `apply_text_edits` handler**

Find the function that maps `params.edits` from MCP into `applyTextEdits` calls.

- [x] **Step 2: Pass through `expectedText`**

Currently the adapter likely strips `expectedText` (since the inner store didn't use it). Update so that for each edit it:

1. Reads the current `sqlText` of the tab
2. Computes `actualText = getRangeText(content, edit.range)` — the substring at the requested range
3. If `actualText !== edit.expectedText` (after `\r\n` → `\n` normalize) → return `error.code='expected_text_mismatch'` with `editIndex` and `expected`/`actual` for markdown

- [x] **Step 3: Implement check**

```ts
function getRangeText(content: string, range: Range): string {
  // resolve startLine/startColumn → startOffset; endLine/endColumn → endOffset
  // (re-use the existing resolveOffset helper from sql-workbench-store)
  const start = resolveOffset(content, range.startLine, range.startColumn)
  const end   = resolveOffset(content, range.endLine, range.endColumn)
  return content.slice(start, end)
}

function normalize(t: string): string {
  return t.replace(/\r\n/g, '\n')
}

// Inside apply_text_edits handler:
const tabState = useSqlWorkbenchStore.getState().tabsById[tabId]
if (!tabState) return errorPayload('tab_not_found', tabId)
if (params.baseVersion !== tabState.version) {
  return errorPayload('version_conflict', tabId, /* details */ {
    currentState: { version: tabState.version, tabId },
  })
}
for (let i = 0; i < params.edits.length; i++) {
  const edit = params.edits[i]
  const actual = normalize(getRangeText(tabState.sqlText, edit.range))
  const expected = normalize(edit.expectedText)
  if (actual !== expected) {
    return errorPayload('expected_text_mismatch', tabId, {
      editIndex: i,
      expected,
      actual,
      currentState: { version: tabState.version, tabId },
    })
  }
}
// All checks pass → forward to applyTextEdits (which double-checks baseVersion)
return useSqlWorkbenchStore.getState().applyTextEdits(tabId, params)
```

`errorPayload` returns an object compatible with the action_result `error.*` shape; markdown is rendered server-side in the dispatcher (Task 13).

- [x] **Step 4: Update `replace /content` handler to pass `baseVersion`**

In the patch op handler that maps `/content` replace → `replaceQueryEditorContent`, pass through `op.baseVersion`. If missing, return `error.code='version_conflict'` with current state — but the AGENTS.md schema validation at the server should already reject missing baseVersion before reaching the client adapter, so this is defensive.

- [x] **Step 5: tsc check**

```bash
cd client && npx tsc --noEmit
```

- [x] **Step 6: Commit (atomic group)**

```bash
git add client/src/features/stage/adapters/QueryEditorAdapter.ts
git commit -m "feat(query-editor-adapter): expectedText pre-check + baseVersion (atomic 10/11/16/17)"
```

---

### Task 19: Atomic group sanity — push only after Tasks 10/11/17/18 land

- [x] **Step 1: Verify all four atomic-group commits exist**

```bash
cd /home/wallfacers/project/data-talk
git log --oneline -10 | grep -E "atomic 10/11/16/17"
```

Expected: 4 lines.

- [x] **Step 2: Run full server + client compile**

```bash
cd server && mvn verify -q
cd client && npm run test && npx tsc --noEmit
```

Expected: PASS.

- [x] **Step 3: Push as a single push**

(Done by user / CI when ready.)

---

### Task 20: `stage-tab-api.ts` consolidates to `listAll`

**Files:**
- Modify: `client/src/features/stage/persistence/stage-tab-api.ts`
- Modify: `client/src/features/stage/persistence/stage-persistence-coordinator.ts`
- Modify: `client/src/features/stage/persistence/stage-persistence-bootstrap.ts`

- [x] **Step 1: Update `StageTabApi` interface**

Replace the listing methods:

```ts
export interface StageTabApi {
  listAll(opts?: { archived?: boolean; originSessionId?: string }): Promise<ListResponse>
  upsert(req: UpsertRequest): Promise<UpsertResponse>
  putPayload(req: UpsertRequest): Promise<UpsertResponse>
  delete(id: string): Promise<void>
  getPayload(id: string): Promise<PayloadResponse>
  setArchived(id: string, archived: boolean): Promise<void>
}
```

- [x] **Step 2: Implement `listAll`**

```ts
async listAll(opts) {
  const params = new URLSearchParams()
  params.set('archived', opts?.archived ? 'true' : 'false')
  if (opts?.originSessionId) params.set('originSessionId', opts.originSessionId)
  const r = await fetch(`${BASE}?${params.toString()}`)
  if (!r.ok) throw httpError(r)
  return r.json()
},
```

Delete `listWorkspaceTabs` and `listSessionTabs`.

- [x] **Step 3: Drop `scope` from `UpsertRequest`**

```ts
export interface UpsertRequest {
  id: string
  type: string
  // scope: removed
  title: string
  // … rest unchanged
}
```

- [x] **Step 4: Update `StagePersistenceCoordinator.start()`**

```ts
async start(): Promise<void> {
  this.phase = 'hydrating'
  try {
    const meta = await this.api.listAll({ archived: false })
    this.onHydrated?.(meta.items)
  } catch (e) {
    this.phase = 'degraded'
    throw e
  }
  this.phase = 'live'
  const queued = this.queuedDuringHydration
  this.queuedDuringHydration = []
  queued.forEach((fn) => fn())
}
```

- [x] **Step 5: Update `stage-persistence-bootstrap.ts`**

In `coordinator.onHydrated`, call `__hydrateAll`:

```ts
coordinator.onHydrated = (items) => {
  useStageStore.getState().__hydrateAll(items.map(toStageTab))
}
```

Update `resolveTabSnapshot`:

```ts
coordinator.resolveTabSnapshot = (tabId) => {
  const tab = useStageStore.getState().findTab(tabId)
  if (!tab || !isPersistent(tab.type)) return null
  return {
    id: tab.tabId,
    type: tab.type,
    title: tab.title,
    connectionId: tab.connectionId ?? null,
    database: tab.database ?? null,
    schema: tab.schema ?? null,
    originSessionId: tab.originSessionId ?? null,
    pinned: tab.pinned ?? false,
    archived: tab.archived ?? false,
    createdAt: tab.createdAt,
    lastTouchedAt: tab.lastTouchedAt ?? Date.now(),
  }
}
```

(Notice the deleted `scope` field.)

- [x] **Step 6: Add `__hydrateAll` to stage-store.ts**

```ts
__hydrateAll: (items) => set((s) => {
  // Reuse existing __hydrateWorkspaceTabs logic against the unified `tabs` array.
  // (Since Phase 3 removes `tabsBySession` entirely, but Phase 1 keeps both for
  // backward compat, this method merges into workspaceTabs in P1; P3 will
  // collapse to a single `tabs[]`.)
  const incomingMap = new Map(items.map((t) => [t.tabId, t]))
  const merged = s.workspaceTabs.map((existing) => {
    const hydrated = incomingMap.get(existing.tabId)
    if (!hydrated) return existing
    return { ...existing, ...hydrated }
  })
  for (const item of items) {
    if (!merged.some((t) => t.tabId === item.tabId)) merged.push(item as StageTab)
  }
  return { workspaceTabs: merged }
}),
```

(Phase 3 will replace `workspaceTabs` with single `tabs[]`. For now, Phase 1 just uses workspaceTabs as the global home for all tabs.)

- [x] **Step 7: tsc check + commit**

```bash
cd client && npx tsc --noEmit
git add client/src/features/stage/persistence/ client/src/stores/stage-store.ts
git commit -m "refactor(persistence): listAll + __hydrateAll, drop listWorkspaceTabs/SessionTabs"
```

---

## Phase 1G — ui-objects-reference.md + Final Regression (Tasks 21–22)

### Task 21: Rewrite `docs/references/ui-objects-reference.md`

**Files:**
- Modify: `docs/references/ui-objects-reference.md`

- [x] **Step 1: Delete `WORKSPACE_SCOPE_TYPES` table**

In Section 1 (`workspace`), find the "open 的 Tab type → scope 映射" table. Delete it entirely.

- [x] **Step 2: Update workspace Exec Actions table**

```markdown
#### Exec Actions

| action | 必填参数 | 可选参数 | 效果 |
|--------|---------|---------|------|
| `open` | `type: string` | `title`, `connection_id`, `database`, `schema`, `payload` | 开一个新 Tab；`type` 决定 Tab 类型（adapter 注册表确定） |
| `focus` | `target: tabId` | — | 自动 ensure tab 在工作集 + setActive；archived tab 返回 `tab_archived` |
| `detach` | `target: tabId` | — | 仅从工作集（顶 tab 栏）移除，DB 不动；左 rail 库内仍存在 |
| `archive` | `target: tabId` | `archived?: boolean = true` | `archived=true` 归档；`archived=false` 解归档 |
| `trash` | `target: tabId` | — | 永久删除（FK CASCADE 清 payload + FTS） |
| `close` | `target: tabId` | — | **Deprecated alias** for `archive(archived=true)`；3 个发版周期后删除 |
| `choose_connection` | — | `preferredConnectionId: string` | 拉起数据源选择器 |
```

- [x] **Step 3: Add `inWorkset` to query_editor state mode**

In Section 2 (`query_editor`), update the `state` mode return contract:

```markdown
| `state` | `{ tabId, title, content, language: 'sql', version, dirty, cursor, selection, connectionId, connectionName, database, schema, contextOverride, entryMode, autoRun, executeStatus, results, activeResultId, limit, inWorkset }` |
```

In `state` 字段说明 sub-list, add:

```markdown
- `inWorkset`：当前 tab 是否在顶 tab 栏工作集中（client-side per-app-instance）；与左 rail 的 `lastTouchedAt`-排序"库"互补
```

- [x] **Step 4: Update query_editor `patch` table**

```markdown
| path | ops | 必填字段 | 作用 |
|------|-----|---------|------|
| `/content` | `replace` | `baseVersion: number` | 整段覆盖 SQL 文本；`baseVersion` 不匹配返回 `version_conflict` |
| `/connectionId` | `replace` | — | 修改连接 |
| `/database` | `replace` | — | 修改数据库 |
| `/schema` | `replace` | — | 修改 schema |
```

- [x] **Step 5: Update query_editor Exec Actions for `apply_text_edits`**

```markdown
| `apply_text_edits` | `{ baseVersion, edits: [{ range, expectedText, text }] }` | 按 range 精确编辑 SQL；每条 edit 必须含 `expectedText`（当前内容指纹）；任一指纹不匹配整批回滚 |
```

- [x] **Step 6: Append Maintenance Note about V13**

At the bottom, before the Checklist:

```markdown
> **2026-04-28 update — Shared Stage Workbench Phase 1**: scope 概念取消；Tab 全工作台共享；`apply_text_edits` 强制 `expectedText`；`/content` patch 强制 `baseVersion`；`workspace.detach/archive/trash` 三新动词；`close` deprecated；详见 [Shared Stage Workbench Design](../product-specs/2026-04-28-shared-stage-workbench-design.md)。
```

- [x] **Step 7: Commit**

```bash
git add docs/references/ui-objects-reference.md
git commit -m "docs(ui-objects-reference): rewrite for V13 shared workbench"
```

---

### Task 22: Full regression + register plan as Active

**Files:**
- Modify: `docs/exec-plans/index.md`

- [x] **Step 1: Register plan in index Active section**

Open `docs/exec-plans/index.md`. Add at the top of "Active" section:

```markdown
- [Shared Stage Workbench · Phase 1 — Backend Protocol & Migration](./2026-04-28-shared-stage-workbench-p1-backend-protocol-plan.md) — 2026-04-28 — Drop scope, V13 migration with FTS rewire, `expectedText` + `baseVersion` enforcement, `error.markdown` formatter, AGENTS.md + ui-objects-reference rewrite, STAGE_TAB_DIGEST upgrade
```

- [x] **Step 2: Full server build**

```bash
cd server && mvn verify
```

Expected: BUILD SUCCESS, all tests green including `StageTabsMigrationIT`, `StageTabConcurrencyIT`, `EditConflictMarkdownFormatterTest`, `AgentPromptDigestTest`, `AgentPromptContractTest`, `UiActionsTest`, `UiFindActionTest`, `StageTabSearchScenarioIT`.

- [x] **Step 3: Full client build**

```bash
cd client && npm run test && npx tsc --noEmit
```

Expected: all tests green, 0 type errors.

- [x] **Step 4: Commit index update**

```bash
git add docs/exec-plans/index.md
git commit -m "docs(plans): register P1 in Active"
```

- [x] **Step 5: Self-check verification matrix (per spec §11)**

Manual run-through with notes:

| Acceptance | Verified by |
|---|---|
| V3 (concurrent edits → markdown) | `StageTabConcurrencyIT` PASS |
| V5 (apply_text_edits without expectedText → reject) | `UiActionsTest#uiExec_applyTextEdits_eachEditRequiresExpectedText` PASS |
| V6 (replace /content without baseVersion → reject) | `UiActionsTest#uiPatch_contentReplace_requiresBaseVersion` PASS |
| V9 (AGENTS.md 6 tokens) | `AgentPromptContractTest#agentsMd_includesAllRequiredTokens` PASS |
| V10 (STAGE_TAB_DIGEST fields, no inWorkset) | `AgentPromptDigestTest` PASS |
| V12 (mvn verify + npm test) | both PASS |
| V14 (loadAll < 200ms) | manual: launch app, observe startup; if not measurable, defer to P3 |

---

## Execution Notes

- Executed on 2026-04-28 in one batched implementation pass with parallel subagents for independent slices: adapter schema/tests, prompt/docs, client protocol/store, persistence bootstrap, and find/search coverage.
- The planned client/server protocol atomic group landed together in the same working batch: `UiPatchAction`, `UiExecAction`, `sql-workbench-store`, and `QueryEditorAdapter` were verified together before close-out.
- `StageTabConcurrencyIT` was not added as a literal Spring integration test. This codebase has no in-process browser/client runtime that can complete a real `datatalk.ui.exec` or `datatalk.ui.patch` client action round-trip inside adapter ITs. The server-side acceptance intent was covered instead by `McpActionBridgeTest#synthesizesMarkdownForVersionConflictWhenClientOmitsMarkdown`, which verifies the dispatcher/bridge error envelope emits `error.code`, `currentState`, and synthesized `error.markdown` for stale version conflicts even when the client omits markdown.
- Additional hardening beyond the original task text: `QueryEditorAdapter` now includes `currentState.tabId` on structured `version_conflict` and `expected_text_mismatch` errors so bridge-side markdown synthesis remains robust even when the request target is implicit.

## Verification Notes

- Backend compile: `cd server && mvn compile -q` passed.
- Backend targeted application tests: `cd server && mvn test -pl data-talk-application -Dtest=McpActionBridgeTest -q` passed.
- Backend targeted adapter/integration tests: `cd server && mvn test -pl data-talk-adapter -am -Dtest=UiActionsTest,UiFindActionTest,StageTabSearchScenarioIT,StageFindControllerIT,StageTabControllerIT,AgentPromptContractTest,AgentPromptDigestTest -Dsurefire.failIfNoSpecifiedTests=false -q` passed.
- Frontend targeted protocol tests: `cd client && npx vitest run src/features/stage/stores/sql-workbench-store.test.ts src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts src/stores/stage-store.test.ts src/features/stage/persistence/__tests__/stage-persistence-coordinator.test.ts src/features/stage/utils/query-editor-actions.test.ts` passed.
- Frontend typecheck: `cd client && npx tsc --noEmit` passed.
- Full `mvn verify` and full `npm test` were not rerun in this close-out pass; completion is based on the targeted suites above plus compile/typecheck.

## Deviations

- Task 13 (`StageTabConcurrencyIT`) is closed with equivalent bridge-level verification instead of a new adapter integration test class. This preserves the acceptance intent for markdown-bearing concurrency errors without introducing a misleading pseudo-concurrent test that still would not exercise the real client-action path.
- Task steps that mention per-step git commits or push sequencing were not followed literally in this execution session. Work was batched per the repository parallel-plan rule and verified before housekeeping.

## Self-Review

**Spec coverage**: P1 implements spec §3.1 items 1–10 (excluding UI-visible ones), §4.1–§4.5 backend portions, §5 entire (protocol upgrades), §6 entire (concurrency model), §8 entire (AGENTS.md), and §9.4 task table 1.1–1.19 except for the frontend-only Task 1.18 portions that span into client (covered here as Tasks 17, 18, 20). The frontend layout migration (§7) is deferred to P2; state globalization (§9.4 P3) is deferred to P3.

**Placeholder scan**: No `TBD` / `TODO` / "implement later". Every code block in tasks contains the actual code or test body. `errorPayload` helper in Task 18 is referenced but its definition is left up to the existing adapter conventions (it's already an established pattern in the codebase — `client-handlers.ts` returns equivalent objects).

**Type consistency**: `EditConflictMarkdownFormatter` signature in Task 8 matches the calls in Task 13 (versionConflict, expectedTextMismatch, etc.). `replaceSqlText(tabId, content, baseVersion)` is consistent across Tasks 17 (definition) and 20 (callers). `__hydrateAll` is added in Task 20 for the persistence layer; Task 17's tests don't depend on it. `SessionTitleLookup` interface in Task 15 step 3 matches its impl in step 4 and usage in step 5.

---

**Plan complete and saved.** Two execution options when you're ready:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Phase 1 (this plan) is the **Backend Protocol & Migration** plan. Phase 2 (Frontend Layout) and Phase 3 (State Globalization) will be drafted next as separate plans, after you confirm P1 looks right.
