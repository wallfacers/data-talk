# Schema Read Bounds Design

**Date:** 2026-04-27  
**Status:** approved for implementation  
**Scope:** Backend `datatalk_read_schema` behavior and runtime agent instructions.

## Problem

`datatalk_read_schema` currently returns every table with every column when the caller omits `tables`. Large schemas can exceed OpenCode message limits, causing the tool output to be truncated and written to a temporary file. The exposed input schema advertises a `tables` parameter, but the current Java handler does not apply it, so callers cannot bound the response by table name.

## Design

`datatalk_read_schema` should be safe by default and detailed only on request.

- When `tables` is omitted or empty, return a compact table summary: each item has `name` and no `columns`.
- When `tables` contains names, return column metadata only for matching tables.
- Keep the existing output key `schema` so current renderers and clients continue to work.
- Preserve existing `connectionId`, `database`, and `schema` context inheritance.
- Do not add pagination in this change. The smallest reliable fix is to make the already-declared `tables` argument work and avoid full-column expansion by default.

## Agent Prompt

The runtime prompt in `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` must teach agents to avoid broad schema reads:

- Prefer explicit `tables` when column details are needed.
- Use an unscoped schema read only for table discovery.
- Treat truncated output as a large-output continuation problem, not a tool failure.

The prompt remains English-only to satisfy `AgentPromptContractTest`.

## Testing

Backend tests cover:

- Default read returns table names without `columns`.
- Explicit `tables` returns column metadata for only requested tables.
- Runtime prompt contains the schema-reading and truncation guidance.

## Non-Goals

- No new MCP tool.
- No frontend UI change.
- No `limit/pageSize` protocol expansion in this slice.
