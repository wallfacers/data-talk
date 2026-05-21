---
name: er-tabs
description: Use when the user asks about ER diagrams, schema visualisation, or schema design / fork / apply. Triggers on ER 图 / ER 设计器 / 实体关系 / 看表关系 / 设计表结构 / fork to designer / generate DDL / ER inspector / ER designer / draw ER / annotate relation. Covers the two ER tab types — er_inspector (read-only ER Diagram Viewer with annotation overlay) and er_designer (independent schema draft producing DDL) — plus dialect support (MySQL/PostgreSQL/H2/MariaDB live; SQLite CREATE-only; DuckDB/ClickHouse/Doris/StarRocks/Oracle/SQL Server unsupported) and the rule that designer DDL is never auto-applied.
---

# ER Tabs Skill (Inspector & Designer)

DataTalk has two ER tab types:

- **er_inspector** — the user-facing ER Diagram Viewer: a read-only view of a real schema with annotation overlay (virtual relations, notes). Use it to *show* relationships.
- **er_designer** — the user-facing ER Diagram Designer: an independent schema draft that can generate DDL for a target connection. Use it to *design* schemas. Designer verbs are live: bind a target, diff the draft against the DB, generate DDL into a query_editor tab, then have the user review and click Run (the editor calls `datatalk_execute_sql` under the hood — see `[[sql-execution]]`).

Payload shape, patch paths, exec verbs and error contracts: `docs/references/er-tab-protocol.md`.

This skill owns the keywords `er_inspector` and `er_designer`. General UI tool semantics (`datatalk_ui_find` / `datatalk_ui_read` / `datatalk_ui_patch` / `datatalk_ui_exec`) live in `[[ui-contract]]`; this skill only covers their ER-specific calls.

## When to use

- "画一下 orders 表跟其它表的关系" / "show how X relates to other tables"
- "看一下整个数据库的 ER 图" / "show me the ER for db Y"
- "标注一下 A 和 B 之间隐含的关联" / "annotate an implicit link between A and B"
- "帮我设计一套订单系统的库表" / "design a schema for an order system"
- "把生产库 fork 成一个草稿来改" / "fork prod into a draft to edit"
- "把这个草稿应用到测试库" / "apply this draft to the test DB"
- "在 ER 标签里搜一下用了 user_email 的表" / "find the ER tab containing X"

## When NOT to use

- Pure SQL authoring or ad-hoc query writing → query_editor flow ([[query-editor-workflow]]).
- Executing the generated DDL against a real connection → that runs through `datatalk_execute_sql` (see [[sql-execution]]); the editor side of the hand-off (focus / Run) is [[query-editor-workflow]].
- Optimistic-lock failures (`version_conflict`, `expectedVersion` handling on `ui_patch`) → [[concurrency-contract]].
- ER on **DuckDB, ClickHouse, Apache Doris, StarRocks, Oracle, SQL Server** — ER does not support these dialects. Use query_editor + `datatalk_read_schema` instead.

## When to open which

| User says | Open | Notes |
|---|---|---|
| "show how X relates to other tables" | er_inspector (ER Diagram Viewer) | tables=[X], neighborDepth=1 |
| "show me the ER for db Y" | er_inspector (ER Diagram Viewer) | tables = read_schema(db=Y, limit=100) |
| "annotate an implicit link between A and B" | (existing er_inspector) | ui_patch /virtualRelations |
| "design a schema for ..." | er_designer (ER Diagram Designer) | dialect required (mysql/postgresql/h2/mariadb; sqlite CREATE-only) |
| "fork prod into a draft to edit" | er_inspector → fork_to_designer | preserves table & column shapes |
| "apply this draft to the test DB" | er_designer + bind_target + diff_against_db + generate_ddl | DDL lands in a query_editor tab; user reviews and clicks Run, which calls `datatalk_execute_sql` ([[sql-execution]]) |
| "find the ER tab containing X" | datatalk_ui_find ([[ui-contract]]) | filter.type=er_inspector or er_designer + query.mode=fts pattern=X |

## Hard rules

1. **Inspectors are read-only views.** Do not patch an inspector to "change a real column type". Structural changes belong in a designer or in a query_editor.
2. **Designer never executes DDL on its own.** `generate_ddl` produces a query_editor tab populated with the DDL text — the user (or AI, on user instruction) must run it via `datatalk_execute_sql` (see [[sql-execution]]) for the change to actually hit the database.
3. **DDL must be user-acknowledged.** Even though `datatalk_execute_sql` will accept the DDL directly, an er_designer-generated DDL touches schema state — preview the DDL to the user before running. Do **not** claim a designer action applied schema changes until the SQL has actually been executed.
4. **Unsupported dialects.** DuckDB, ClickHouse, Apache Doris, StarRocks, Oracle and SQL Server are not supported by ER. Use query_editor + `datatalk_read_schema` instead. MariaDB **is** supported by ER (reuses MySQL DDL generation). SQLite is CREATE-only.
5. **No coordinates.** Do not pass coordinates to ER tabs. Layout is computed client-side; `auto_layout` is one `datatalk_ui_exec` call away if a relayout is wanted.

Version-guard rules for `ui_patch` (`baseVersion`, `expectedText`, `version_conflict`, `expectedVersion`) are defined in [[concurrency-contract]] — follow them on any ER patch that touches structural paths.

## Recipe shortcuts

### Open an inspector for a table and its neighbors

```
ui_exec(workspace, open_er_inspector, { connectionId, tables: ["orders"], neighborDepth: 1 })
```

### Add a virtual (non-FK) relation

```
ui_patch(inspector_tab, [{
  op: "add", path: "/virtualRelations/-",
  value: { from: {table:"orders",column:"user_email"},
           to:   {table:"users", column:"email"},
           type: "many_to_one", note: "implicit link in app code" }
}])
```

### Search for an ER tab by content

```
ui_find({
  filter: { type: "er_inspector" },
  query:  { mode: "fts", pattern: "user_email" },
  output: { mode: "metadata", headLimit: 10 }
})
```

### Create a new designer with a seed table

```
ui_exec(workspace, open_er_designer, {
  dialect: "postgresql",
  title: "Order System Draft",
  seedTables: [{
    name: "users",
    columns: [{ name: "id", type: "BIGINT", isPrimaryKey: true, isAutoIncrement: true }]
  }]
})
```

### Fork an inspector into an editable designer

```
ui_exec(inspector_tab, fork_to_designer, { title: "Fork of Order ER" })
```

### Apply a designer to a target DB

```
ui_exec(designer_tab, bind_target, { connectionId, database, schema })
ui_exec(designer_tab, diff_against_db)
ui_exec(designer_tab, generate_ddl)
```

The `generate_ddl` response includes `queryEditorTabId`, `ddl`, and `skippedOps`. DDL lands in a query_editor tab; hand the `queryEditorTabId` back to the user so they can review and click Run — the Run button calls `datatalk_execute_sql` (see [[sql-execution]]). The query_editor side of this hand-off (how the tab is opened, focused, and edited) follows [[query-editor-workflow]].

### Add a column via patch

```
ui_patch(designer_tab, [{
  op: "add",
  path: "/tables[id=t_abc123]/columns/-",
  value: { name: "status", type: "VARCHAR(32)", nullable: false }
}])
```

## Recommended workflow

End-to-end flow for "apply a draft schema to a real DB":

1. **Open designer.** `datatalk_ui_exec object=workspace action=open_er_designer params={ dialect, title, seedTables? }`. For "fork prod", run `fork_to_designer` against an existing er_inspector instead — it preserves table and column shapes from the inspected schema.
2. **Bind target.** `datatalk_ui_exec object=er_designer target=<tabId> action=bind_target params={ connectionId, database?, schema? }`. The target must be a dialect supported by ER (see Hard rules).
3. **Iterate the draft.** Use `datatalk_ui_patch` against the er_designer for structural paths (`/tables`, `/relations`, `/dialect`, `/targetConnectionId`, `/targetDatabase`, `/targetSchema`); follow the version-guard rules in [[concurrency-contract]]. View paths (`/positions`, `/collapsed`, `/viewport`) are not subject to the structural version guard. Never pass coordinates explicitly — use `auto_layout` instead.
4. **Diff against the bound DB.** `datatalk_ui_exec object=er_designer target=<tabId> action=diff_against_db`. The response highlights what would change.
5. **Generate DDL.** `datatalk_ui_exec object=er_designer target=<tabId> action=generate_ddl`. The response includes `queryEditorTabId`, `ddl`, and `skippedOps`. The DDL lands in a query_editor tab — the er_designer itself has **not** applied anything to the real database.
6. **User reviews and runs the DDL.** Hand the `queryEditorTabId` back to the user; the query_editor side of the hand-off (focus / edit / Run) is described in [[query-editor-workflow]]. Clicking Run in the editor calls `datatalk_execute_sql` ([[sql-execution]]) — the only chat-path execution gate is the DELETE confirmation flow, which does not apply to pure DDL. Do not claim the schema is applied until the SQL has actually executed.

For "annotate-only" workflows, stay in step 1–3 with an er_inspector (no `bind_target` / `diff_against_db` / `generate_ddl`) and use `ui_patch` against `/virtualRelations` only.
