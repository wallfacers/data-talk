---
name: file-upload-routing
description: Routes uploaded files to appropriate actions based on pre-analysis summary and user intent. Activated when user message contains a file_upload part.
---

# File Upload Routing

## Activation

This skill activates when a user message contains a `file_upload` part. The part includes:
- `fileId` — unique identifier for the uploaded file
- `filename` — original filename
- `mimeType` — detected MIME type
- `sizeBytes` — file size
- `analysis` — pre-processed metadata (type, summary)

The user's accompanying text message determines the **intent**. You MUST classify intent before choosing an action.

## Intent Classification

For CSV, Excel, JSON (array_of_objects), and SQL files (when `analysis.summary.statementTypes` contains only INSERT, DROP, and/or CREATE, and `analysis.summary.targetTables` has exactly 1 entry), classify the user's intent from their message:

| Intent Signals | Intent | Action |
|---|---|---|
| "导入" / "入库" / "建表" / "import" / "load" / "导入到数据库" | **Import** | `datatalk_import_data` |
| "分析" / "统计" / "看看" / "趋势" / "分布" / "analyze" / "summary" | **Analyze** | `datatalk_file_read` (read sample) + `datatalk_execute_sql` |
| Unclear / no explicit signal | **Ask** | Ask user: "你想分析这个文件，还是把数据导入到数据库表中？" |

For Text, Image, and Unknown files, follow the type-specific rules below — no intent classification needed.

## Routing Rules

### SQL Files (analysis.type = "SQL")
- Examine `analysis.summary.statementTypes` to understand what's in the file
- Examine `analysis.summary.targetTables` to identify affected tables
- **Import intent gate**: If user intent = Import AND `statementTypes` contains only INSERT, DROP, and/or CREATE AND `targetTables` has exactly 1 entry AND all DROP/CREATE target the same table as the INSERT statements → use `datatalk_import_data` with `source: { type: "file", fileId }` and `target: { connectionId, tableName }` (derive tableName from `targetTables[0]`). DDL+INSERT mixed SQL files (e.g., mysqldump format with DROP TABLE + CREATE TABLE + INSERT) are supported by `datatalk_import_data`. The service extracts and executes DDL first, then streams INSERT data. Skip riskLevel routing.
  - **Fallback when `datatalk_import_data` rejects the file** (dialect-incompatible parsing, backtick quoting unrecognized, `unsupported_sql_dialect`, or any non-recoverable validation error from the import service): do NOT silently bounce the file into the query editor. Instead, read `analysis.summary.preview` and call `datatalk_execute_sql` directly with the file contents — the AI chat path accepts arbitrary INSERT / CREATE / DROP SQL (only DELETE triggers the in-chat `confirmationId` flow, see [[sql-execution]]). Tell the user "import_data 不支持该 SQL 方言，直接通过 execute_sql 执行" and run it.
- Otherwise, fall through to riskLevel routing:
  - L1 (SELECT only): Open in query editor (the user wants to inspect / tweak before running), or call `datatalk_execute_sql` if the user just wants results in chat.
  - L2 (DML — INSERT / UPDATE / non-DELETE mutation): Show `analysis.summary.preview` to the user, get acknowledgement, then run via `datatalk_execute_sql`. DELETE statements still trigger the in-chat `confirmationId` flow — see [[sql-execution]].
  - L3 (DDL — CREATE / ALTER / DROP / TRUNCATE): Show the affected schema objects in the preview, get explicit acknowledgement from the user because of the schema-changing impact, then run via `datatalk_execute_sql`. There is no separate "guarded DDL flow" — `execute_sql` accepts DDL directly.
- Show `analysis.summary.preview` to user so they can see what statements were detected

### CSV/Excel Files (analysis.type = "CSV" or "EXCEL")

**Import intent** — use `datatalk_import_data`:
- Source: `{ type: "file", fileId }`
- Target: propose `connectionId` (use active session connection), `tableName` (derive from filename without extension)
- `createTable: true` for first file; `createTable: false` for subsequent files to same table
- Propose column schema based on `analysis.summary.detectedTypes` (CSV) or sheet headers (Excel)
- After import, report `rowsImported`, `columns`, and show `sampleRows` (first 3 rows) to user
- **Multi-file scenario**: user drops multiple CSV files → call `datatalk_import_data` once per file sequentially (first with `createTable: true`, rest with `createTable: false`)

**Analyze intent** — use `datatalk_file_read` to read a sample, then discuss findings with the user. Do NOT import.

### JSON Files (analysis.type = "JSON")
- `structure = "array_of_objects"`: Apply the same intent classification as CSV/Excel above
- `structure = "object"`: Summarize keys and ask user intent
- `parseError = true`: Inform user, ask what to do

### Text Files (analysis.type = "TEXT")
- Summarize content from `analysis.summary.preview`
- Extract key information (errors, timestamps, patterns) if visible
- Do NOT suggest database import

### Unknown Files (analysis.type = "UNKNOWN")
- Describe available metadata (size, partial preview)
- Ask user for intent

### Image Files (analysis.type = "IMAGE")
- Acknowledge the uploaded image to the user
- Report dimensions from `analysis.summary.width` × `analysis.summary.height` and `analysis.summary.format`
- The image content is already attached to the same user message as a native file part — you can see it directly, no extra tool call required
- For legacy messages where only `fileId` is present (no inline image part), fall back to `datatalk_file_read` to retrieve `data:{mimeType};base64,{encoded}`
- Do NOT suggest database import
- Common use cases: screenshot analysis, chart interpretation, diagram explanation

## On-Demand File Reading

Use `datatalk_file_read` action to read specific portions of the file:
- Parameters: `fileId` (required), `offset` (default 0), `limit` (default/max 4096)
- For image files: returns `data:{mimeType};base64,{encoded}` — no offset/limit applied
- Use sparingly — only read what you need to make a routing decision
- For text files: never attempt to read the entire file at once

## Cross-References

- `datatalk_file_read` — on-demand file content reading
- `datatalk_import_data` — import file data or cross-DB query into a database table
- `datatalk_export_data` — export query/table results as CSV/JSON/XLSX/SQL_INSERT
- SQL guarded execution flow (skill:sql-execution) — for SQL file execution
- skill:query-editor-workflow — for opening SQL in query editor
