# ER Tab Protocol Reference

Authoritative contract for `er_inspector` in Plan A and `er_designer` in
Plan B. This reference is for AI agents and contributors who need the payload
shape, `ui_patch` path whitelist, `ui_exec` verbs, and error codes without
re-reading the full product spec.

Source of truth: [ER graph browsing design](../product-specs/2026-04-29-er-graph-browsing-design.md).

## Inspector

`er_inspector` is a read-only view of a real database schema with local
annotation overlay. It can refresh from the connection and persist layout,
selection, notes, viewport, and virtual relations. It must not mutate real
tables or columns.

### Payload Schema

```jsonc
{
  "kind": "er_inspector",
  "connectionId": "conn-prod-mysql",
  "database": "ecommerce",
  "schema": null,
  "selection": ["users", "orders", "products"],
  "neighborDepth": 1,
  "layout": "dagre-LR",
  "tablesSnapshot": [
    {
      "name": "users",
      "comment": null,
      "columns": [
        { "name": "id", "type": "BIGINT", "isPK": true, "isFK": false },
        { "name": "email", "type": "VARCHAR(255)", "isPK": false, "isFK": false }
      ],
      "fkOut": [
        { "fromColumn": "id", "toTable": "orders", "toColumn": "user_id" }
      ]
    }
  ],
  "snapshotAt": 1714123456789,
  "positions": {
    "users": { "x": 0, "y": 0 },
    "orders": { "x": 320, "y": 0 },
    "products": { "x": 640, "y": 200 }
  },
  "collapsed": ["products"],
  "virtualRelations": [
    {
      "id": "vr_xk3p9q",
      "from": { "table": "orders", "column": "user_email" },
      "to": { "table": "users", "column": "email" },
      "type": "many_to_one",
      "note": "implicit link in app code"
    }
  ],
  "notes": {
    "orders": "Primary order table"
  },
  "viewport": { "x": 0, "y": 0, "zoom": 1.0 }
}
```

### Patch Grammar

`ui_patch` uses the existing RFC 6902 subset implemented by
`client/src/services/ui-router/jsonPatch.ts`:

| Rule | Contract |
|---|---|
| Ops | `add`, `remove`, `replace`; `merge`, `move`, `copy`, and `test` are not supported. |
| Object keys | Use JSON Pointer paths such as `/selection`, `/notes/orders`, `/viewport`. |
| Array index | Use `/collapsed/0` only when positional removal is acceptable. |
| Array match | Prefer stable match addressing such as `/virtualRelations[id=vr_xk3p9q]`. |
| Array tail | Use `/-` for append, for example `/virtualRelations/-`. |
| Batch | Ops apply in order; any failure rolls back the whole patch. |
| Version | Inspector view paths are last-write-wins and may omit `baseVersion` unless the adapter requires stricter behavior later. |

### Patch Path Whitelist

| path | ops | default baseVersion |
|---|---|---|
| `/selection` | replace | auto |
| `/neighborDepth` | replace | auto |
| `/positions` | replace | auto, last-write-wins |
| `/positions/{tableName}` | replace, remove | auto |
| `/collapsed` | replace | auto |
| `/virtualRelations` | replace | auto |
| `/virtualRelations/-` | add | auto |
| `/virtualRelations[id=<vrId>]` | replace, remove | auto |
| `/notes` | replace | auto |
| `/notes/{tableName}` | replace, remove | auto |
| `/viewport` | replace | auto, last-write-wins |

Attempting to patch structural schema paths such as `/tables` or
`/tablesSnapshot` must return `immutable_path_in_inspector`.

### Exec Verbs

| action | params | output | purpose |
|---|---|---|---|
| `refresh` | none | `{updatedTables, removedTables, payloadVersion}` | Re-read real schema and update the inspector snapshot. |
| `auto_layout` | none | `{positions, payloadVersion}` | Recompute node positions with the client dagre worker. |
| `fit_view` | none | `{viewport, payloadVersion}` | Reset the viewport. |
| `add_neighbors` | `{table}` | `{addedTables, payloadVersion}` | Add the table's direct FK neighbors to `selection`. |
| `fork_to_designer` | `{title?}` | `{newTabId, payloadVersion}` | Create a designer tab copied from this inspector. Reserved for Plan B; Plan A returns a structured error. |

AI callers must not pass coordinates. Layout is client-owned; use
`ui_exec(er_inspector, auto_layout)` to relayout.

### Errors

| code | HTTP | trigger | aiHint |
|---|---:|---|---|
| `dialect_unsupported` | 400 | dialect is oracle, sqlserver, or not listed | `ER does not support {dialect}. Use query_editor with read_schema for inspection, or pick mysql/postgresql/h2 for design drafts.` |
| `connection_unavailable` | 404 | `connectionId` no longer exists | `The target connection no longer exists. Call datatalk_list_connections and ask the user to pick a valid one.` |
| `tables_not_found` | 404 | one or more requested tables do not exist | `Tables {missing} were not found in the connection. Use datatalk_read_schema with pattern to confirm exact names; common typos: {suggestions}.` |
| `er_payload_oversized` | 413 | more than 100 tables or payload over 1MB | `Payload exceeds limit. Narrow down using read_schema with pattern/limit, or split into multiple ER tabs by domain.` |
| `invalid_path` | 400 | path is outside the whitelist | `Path {path} is not patchable on this tab type. Allowed paths: {allowed}.` |
| `invalid_op` | 400 | op/path combination is not allowed | `Op {op} is not allowed on path {path}. See ER protocol reference.` |
| `conflict_with_concurrent_edit` | 409 | strict `baseVersion` conflict | `Tab was modified concurrently (server v{currentVersion} vs your v{baseVersion}). Re-read the tab and re-apply your patch on top of the latest state.` |
| `schema_validation_failed` | 422 | value does not match schema | `Validation failed: {fieldErrors}. Check the er-tab-protocol reference for field types and constraints.` |
| `immutable_path_in_inspector` | 400 | inspector attempts to change real schema paths | `Inspector tabs are read-only views of real schema. To edit tables, fork this tab to a designer first via ui_exec(fork_to_designer).` |

### Search Recipe

```js
ui_find({
  filter: { type: "er_inspector" },
  query: { mode: "fts", pattern: "user_email" },
  output: { mode: "metadata", headLimit: 10 }
})
```

## Designer

`er_designer` is an independent schema draft. It can bind to a target
connection, compare the draft against the real database, and generate DDL into a
new `query_editor` tab. It never executes DDL directly; the user reviews and
runs the generated SQL through the existing L2/L3 guarded execution flow.

### Payload Schema

```jsonc
{
  "kind": "er_designer",
  "targetConnectionId": "conn-test-mysql",
  "targetDatabase": "test_db",
  "targetSchema": null,
  "dialect": "mysql",
  "tables": [
    {
      "id": "t_abc123",
      "name": "users",
      "comment": "user table",
      "columns": [
        {
          "id": "c_def456",
          "name": "id",
          "type": "BIGINT",
          "nullable": false,
          "isPrimaryKey": true,
          "isAutoIncrement": true,
          "default": null,
          "comment": "user PK"
        }
      ],
      "indexes": [],
      "uniques": [{ "columns": ["email"] }]
    }
  ],
  "relations": [
    {
      "id": "r_ghi012",
      "fromTableId": "t_abc123",
      "fromColumnId": "c_def456",
      "toTableId": "t_jkl345",
      "toColumnId": "c_mno678",
      "type": "one_to_many",
      "constraintMethod": "database_fk"
    }
  ],
  "positions": { "t_abc123": { "x": 0, "y": 0 } },
  "collapsed": [],
  "viewport": { "x": 0, "y": 0, "zoom": 1.0 }
}
```

`dialect` is required and must be one of `mysql`, `postgresql`, `h2`, or
`sqlite`. Oracle and SQL Server are unsupported. `targetConnectionId`,
`targetDatabase`, and `targetSchema` are nullable until `bind_target`.

### Patch Path Whitelist

Designer patches use the same RFC 6902 subset and `[id=<value>]` array-match
addressing described for Inspector. Structural paths require a numeric
top-level `baseVersion`; view-only paths may omit `baseVersion`.

| path | ops | default baseVersion |
|---|---|---|
| `/tables` | replace | strict |
| `/tables/-` | add | strict |
| `/tables[id=<tableId>]` | replace, remove | strict |
| `/tables[id=<tableId>]/name` | replace | strict |
| `/tables[id=<tableId>]/comment` | replace | strict |
| `/tables[id=<tableId>]/columns` | replace | strict |
| `/tables[id=<tableId>]/columns/-` | add | strict |
| `/tables[id=<tableId>]/columns[id=<columnId>]` | replace, remove | strict |
| `/tables[id=<tableId>]/columns[id=<columnId>]/{name,type,nullable,isPrimaryKey,isAutoIncrement,default,comment}` | replace | strict |
| `/tables[id=<tableId>]/indexes` | replace | strict |
| `/tables[id=<tableId>]/indexes/-` | add | strict |
| `/tables[id=<tableId>]/indexes[name=<indexName>]` | replace, remove | strict |
| `/tables[id=<tableId>]/uniques` | replace | strict |
| `/tables[id=<tableId>]/uniques/-` | add | strict |
| `/relations` | replace | strict |
| `/relations/-` | add | strict |
| `/relations[id=<relationId>]` | replace, remove | strict |
| `/positions` | replace | auto |
| `/positions/{tableId}` | replace, remove | auto |
| `/collapsed` | replace | auto |
| `/viewport` | replace | auto |
| `/dialect` | replace | strict |
| `/targetConnectionId`, `/targetDatabase`, `/targetSchema` | replace | strict |

The server action schema intentionally keeps path support broad. Client
adapters validate exact path/op combinations and return structured
`invalid_path`, `invalid_op`, or `schema_validation_failed` errors.

### Exec Verbs

| action | params | output | purpose |
|---|---|---|---|
| `auto_layout` | none | `{positions, payloadVersion}` | Recompute node positions with the client dagre worker. |
| `fit_view` | none | `{viewport, payloadVersion}` | Reset the viewport. |
| `bind_target` | `{connectionId, database?, schema?}` | `{payloadVersion}` | Bind a real database target for diff and DDL generation. |
| `unbind_target` | none | `{payloadVersion}` | Remove the target binding. |
| `sync_from_db` | `{tables?}` | `{payload: ErDesignerPayload}` | Refresh designer tables from the bound database. The server returns the merged payload (real-DB tables replace matching draft entries by name; existing table/column ids are preserved when names match; `database_fk` relations are rebuilt; non-fk relations are kept and re-mapped). The frontend hydrates this payload while preserving its local view fields (`positions` / `collapsed` / `viewport`); if synced tables introduce ids that do not yet exist in the local `positions` map, the client assigns fallback scattered coordinates so new tables do not stack at `(0,0)`. When `tables` is omitted/empty the server uses the full draft table list. |
| `diff_against_db` | none | `{diff: [...]}` | Return schema diff without generating SQL. |
| `generate_ddl` | `{includeDrops?: false}` | `{queryEditorTabId, ddl, skippedOps, payloadVersion}` | Generate DDL and write it into a new `query_editor` tab. |

### Apply Flow

```js
ui_exec(designer_tab, bind_target, { connectionId, database, schema })
ui_exec(designer_tab, diff_against_db)
ui_exec(designer_tab, generate_ddl)
```

`generate_ddl` returns `queryEditorTabId`, `ddl`, and `skippedOps`. The DDL is
already placed in a `query_editor` tab bound to the target connection. The user
must review it, press Run, and complete L2/L3 confirmation. AI agents must not
describe `generate_ddl` as having applied schema changes.

### DDL Generation Scope

| Dialect | CREATE TABLE | ALTER ADD COLUMN | ALTER ADD FK | CREATE INDEX |
|---|---|---|---|---|
| MySQL | supported | supported | supported | supported |
| PostgreSQL | supported | supported | supported | supported |
| H2 | supported | supported | supported | supported |
| SQLite | supported | skipped | skipped | supported |
| Oracle | unsupported | unsupported | unsupported | unsupported |
| SQL Server | unsupported | unsupported | unsupported | unsupported |

DROP TABLE, DROP COLUMN, ALTER COLUMN type, and RENAME are always skipped with
`reason: "day1_unsupported"` regardless of dialect. The `SkippedOp.aiHint`
must tell the AI/user to write manual SQL in `query_editor` and run it through
guarded execution.

### Designer Errors

| code | HTTP | trigger | aiHint |
|---|---:|---|---|
| `target_required_for_apply` | 400 | `diff_against_db`, `sync_from_db`, or `generate_ddl` without a bound target | `generate_ddl requires bind_target first. Call ui_exec(designer, bind_target, {connectionId, database, schema}) and retry.` |
| `dialect_unsupported` | 400 | dialect is oracle, sqlserver, or not listed | `ER does not support {dialect}. Use query_editor with read_schema for inspection, or pick mysql/postgresql/h2 for design drafts.` |
| `ddl_generation_partial_skipped` | 200 | generated plan contains skipped operations | `Generated DDL written to query_editor {queryEditorTabId}. Skipped operations require manual SQL: {skippedOps}. Have the user write them in query_editor and run with L2/L3 confirmation.` |
| `connection_unavailable` | 404 | target connection no longer exists | `The target connection no longer exists. Call datatalk_list_connections and ask the user to pick a valid one.` |
| `invalid_path` | 400 | path is outside the whitelist | `Path {path} is not patchable on this tab type. Allowed paths: {allowed}.` |
| `invalid_op` | 400 | op/path combination is not allowed | `Op {op} is not allowed on path {path}. See ER protocol reference.` |
| `conflict_with_concurrent_edit` | 409 | strict `baseVersion` conflict | `Tab was modified concurrently (server v{currentVersion} vs your v{baseVersion}). Re-read the tab and re-apply your patch on top of the latest state.` |
| `missing_base_version` | 400 | designer structural patch omitted `baseVersion` (or sent the convenience `'auto'` literal that view paths accept) | `Designer structural patches require a numeric baseVersion read from ui_read(mode="state"). The wire-default 'auto' literal is rejected here so concurrent edits surface as 409. View paths (positions/collapsed/viewport) may still omit baseVersion.` |
| `tables_not_found` | 404 | `sync_from_db` was given a name that does not exist in the bound DB | `Tables {missing} were not found in the connection. Use datatalk_read_schema with pattern to confirm exact names before sync_from_db.` |
| `sync_failed` | 400 | sync_from_db cannot proceed (no bound target, empty draft + no `tables`, etc.) | `Bind the designer to a target connection and pass either an explicit tables list or have at least one draft table before calling sync_from_db.` |
| `invalid_request` | 400 | `sync_from_db` payload is missing the required current designer payload | `Pass the designer payload returned by ui_read(mode='state') so the server can merge real-DB tables back into the draft.` |
| `schema_validation_failed` | 422 | value does not match schema | `Validation failed: {fieldErrors}. Check the er-tab-protocol reference for field types and constraints.` |
