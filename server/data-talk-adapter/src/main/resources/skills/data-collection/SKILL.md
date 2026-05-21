---
name: data-collection
description: |
  Collect external data via Python/Node.js scripts and write into SQL connections.
  数据采集脚本、爬虫脚本、API 数据拉取、抓取网页数据、script runner、Python script、Node.js script、data scraping、web scraping、data fetching.
---

# Data Collection Skill

Execute Python or Node.js scripts locally to fetch, scrape, or process external data, then write results into user database connections.

## ❗ DO NOT

**These patterns will fail or violate the contract — do not do them:**

- **DO NOT** install or import database drivers in scripts (`pip install pymysql`, `pip install psycopg2`, `npm install mysql2`, `import pymysql`, `from sqlalchemy import create_engine`, etc.). Scripts run in the user's local Python/Node env and have **no direct DB access** by design.
- **DO NOT** construct JDBC URLs, host/port/password literals, or any DB connection string in scripts. The script's only allowed channel to write data is the **HTTP write API** below.
- **DO NOT** call `cursor.execute(...)`, raw SQL, or any `INSERT` / `CREATE TABLE` SQL from the script. Identifier quoting, schema creation, and dialect handling are owned by `ScriptDataWriteService` server-side (see `IdentifierQuoter` for the dialect dispatch table). Scripts emit rows as JSON via the write API and the server generates dialect-correct DDL/DML.
- **DO NOT** read `DT_CONNECTION_ID` and try to look it up — pass it through verbatim as the `connectionId` field in the write request body. The token + connectionId pair is verified server-side.

**Why this matters**: Scripts that bypass `POST {DT_BACKEND_URL}/api/script-data/write` get one of: missing-driver error, wrong dialect (MySQL backtick vs ANSI quote) SQL syntax error, identifier-collision crash on reserved keyword columns (`select`, `order`, …), or silent data corruption. The write API is the single contract that handles all of this correctly across the 19 supported `ConnectionKind` values.

## Tool Surface

| Tool | Purpose |
|---|---|
| `datatalk_script_run` | Prepare a script run — validates environment, creates run record, returns runId + token |
| `datatalk_script_stop` | Cancel a running script |
| `datatalk_script_list` | List script run history |

## Orchestration Sequence

1. **Determine requirements**: What data to collect, target connection, target table
2. **Generate script**: Write Python/Node.js code using `datatalk_ui_exec` → `apply_text_edits` on a `script_editor` tab
3. **Open editor**: If no script_editor tab is open, call `datatalk_ui_exec` object=`script_editor` action=`open`
4. **Write code**: Apply generated script via `apply_text_edits`
5. **User runs**: User clicks Run button (or AI can instruct user to run)
6. **Data write**: Script uses `requests`/`fetch` to get data, then calls backend write API
7. **Verify**: Check console output and run status

## Script Environment Variables

When a script runs, these environment variables are injected:
- `DT_BACKEND_URL` — Backend base URL for REST API calls
- `DT_SCRIPT_TOKEN` — One-time authentication token for write operations
- `DT_CONNECTION_ID` — Target database connection ID

## Data Write API (called by scripts)

### Single batch write
```
POST {DT_BACKEND_URL}/api/script-data/write
Body: { token, connectionId, tableName, rows[], createTable }
```

### Streaming batch write
```
POST {DT_BACKEND_URL}/api/script-data/batch
Body: { token, connectionId, sessionId?, tableName, rows[], createTable }

POST {DT_BACKEND_URL}/api/script-data/batch/close
Body: { sessionId }
```

## Error Handling

- If script fails, check `exitCode` and `stderr` in console output
- Common issues: missing runtime, network errors, authentication failures
- Token expires after 10 minutes — regenerate if needed via `datatalk_script_run`

## Output Protocol

After script execution completes:
1. Console output shows in the xterm.js panel
2. Backend records the run in `script_run` table with status, duration, rows written
3. User can query history via `datatalk_script_list`
