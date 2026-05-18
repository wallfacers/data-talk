---
name: file-upload-routing
description: Routes uploaded files to appropriate actions based on pre-analysis summary. Activated when user message contains a file_upload part.
---

# File Upload Routing

## Activation

This skill activates when a user message contains a `file_upload` part. The part includes:
- `fileId` — unique identifier for the uploaded file
- `filename` — original filename
- `mimeType` — detected MIME type
- `sizeBytes` — file size
- `analysis` — pre-processed metadata (type, summary)

## Routing Rules

### SQL Files (analysis.type = "SQL")
- Examine `analysis.summary.statementTypes` to understand what's in the file
- Examine `analysis.summary.targetTables` to identify affected tables
- L1 (SELECT only): Open in query editor, suggest running
- L2 (DML): Describe impact, require confirmation via guarded DML flow
- L3 (DDL): Warn about schema changes, require confirmation via guarded DDL flow
- Show `analysis.summary.preview` to user so they can see what statements were detected

### CSV/Excel Files (analysis.type = "CSV" or "EXCEL")
- If `analysis.summary.headers` exists, structured data was detected
- Suggest creating a table and importing the data
- Propose table name based on filename (without extension)
- Propose column schema based on `analysis.summary.detectedTypes` (CSV) or sheet headers (Excel)
- TODO(Task 13): Use `datatalk_import_data` action to execute the import

### JSON Files (analysis.type = "JSON")
- `structure = "array_of_objects"`: Route like CSV, suggest table import
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
- `datatalk_import_data` (TODO: Task 13) — data import into database
- SQL guarded execution flow (skill:sql-execution) — for SQL file execution
- skill:query-editor-workflow — for opening SQL in query editor
