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
| Version | Inspector paths default to `baseVersion: "auto"` unless the adapter requires stricter behavior later. |

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

`er_designer` is reserved for Plan B. It will add independent schema drafts,
target binding, diffing, and DDL generation into a guarded `query_editor` tab.
