# Large Schema Context Guards Design

## Problem

`datatalk_read_schema` is safe for small schemas after the 2026-04-27 bounds work, but it can still return too many table summaries when a selected database/schema contains hundreds or thousands of tables. Column detail reads can also grow too large if the caller passes many explicit tables or wide tables. In parallel, `datatalk_execute_sql` advertises `pageSize` but currently reads the full result set before returning a 100-row preview, creating backend memory and storage risk even when the chat context stays small.

## Goals

- Keep schema metadata reads realtime; do not introduce persistent schema cache or index.
- Make `datatalk_read_schema` safe for large schemas through search, pagination, explicit description limits, and truncation metadata.
- Add a final MCP output budget guard so accidental large action output does not flood OpenCode context.
- Make chat-path SQL execution honor `pageSize` and report truncation.
- Update runtime agent instructions so the AI narrows schema reads instead of enumerating everything.

## Non-Goals

- No persistent schema cache, background indexer, schema digest store, or async refresh.
- No frontend UI changes.
- No streaming SQL result export changes.
- No semantic table search; first slice is literal table/column pattern matching through realtime JDBC metadata.

## Design

`datatalk_read_schema` keeps its existing tool name for compatibility and extends the input contract:

- `mode`: optional `discover` or `describe`. Missing mode is inferred: explicit `tables` means `describe`, otherwise `discover`.
- `pattern`: optional string used to narrow table discovery. The action passes a SQL metadata pattern to `DatabaseMetaData.getTables` where possible and still applies case-insensitive filtering in Java for consistent behavior.
- `limit`: optional integer. Discovery defaults to 50 and caps at 100. Describe caps requested tables at 20.
- `cursor`: optional integer-like offset token for discovery pagination.
- `searchColumns`: optional boolean. When true with a non-blank `pattern`, discovery may include tables whose column names match the pattern. This remains realtime and bounded.

Discovery mode returns table summaries only:

```json
{
  "schema": [{ "name": "orders" }],
  "mode": "discover",
  "returnedCount": 1,
  "totalCount": 1,
  "truncated": false,
  "nextCursor": null
}
```

Describe mode requires explicit tables and returns columns only for those tables. It rejects more than 20 requested tables. Wide-table column output is capped per table, with `columnsTruncated=true` on that table.

MCP tool results still include structured content for normal responses. If serialized output exceeds the configured budget, the text content and structured content are replaced with a compact truncation payload that tells the agent to narrow the request. This is a guardrail, not the primary pagination mechanism.

`datatalk_execute_sql` honors `pageSize` by reading at most `pageSize + 1` rows, returning only the first `pageSize`, and adding `truncated=true`. Default page size is 100, maximum is 1000.

## Agent Behavior

The runtime `AGENTS.md` tells the AI:

- Use schema discovery with `pattern` and `limit` for large schemas.
- If `truncated=true`, narrow by business keyword or ask the user to choose.
- Use describe mode only for explicit relevant tables.
- Never pass a large table list to describe mode.
- Avoid broad raw row queries; use bounded `pageSize` or aggregated SQL.

## Testing

- `ReadSchemaActionIT` covers discovery pagination, pattern filtering, column search, too many requested tables, and wide-column truncation.
- `DataTalkMcpServiceTest` covers oversized output replacement.
- `ExecuteSqlActionTest` covers `pageSize`, default bounds, and `truncated=true`.
- `AgentPromptContractTest` pins the large-schema workflow language.

