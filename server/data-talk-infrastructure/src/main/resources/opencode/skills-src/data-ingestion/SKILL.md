---
name: data-ingestion
description: Use when the user asks to fetch external data (REST/JSON/CSV/static HTML table) and write it into a SQL connection. Triggers on phrases like 抓取/拉取/采集/落库/导入/ingest/scrape/sync from API/import from URL. Provides the full chain: HTTP fetch → schema inference → user-confirmed CREATE TABLE → batch INSERT. Day-1 supports MySQL/PostgreSQL/H2/SQLite as ingestion targets and None/Bearer/API Key/Basic auth schemes.
---

# Data Ingestion Skill

## When to use

- "把 https://api.example.com/orders 的数据导入到我的 MySQL 连接"
- "从 CSV URL 抓取数据并创建一张新表"
- "Ingest JSON data from this REST endpoint into PostgreSQL"
- "把网页上的 HTML 表格数据采集到 H2 数据库"
- "Import the CSV from https://data.gov/dataset.csv into my database"

## When NOT to use

- Reading from an already-existing connection — use `datatalk_read_schema` / `datatalk_execute_sql`
- Dashboard / chart generation — use chart fence
- Platform-specific (Taobao / JD / Pinduoduo / Douyin) — dedicated skills (follow-up)
- JavaScript-rendered web pages — Day-1 unsupported
- Excel / XML / TSV / Parquet — Day-1 unsupported

## Tool surface

| MCP Tool | Input | Return |
|----------|-------|--------|
| `datatalk_http_request` | url, method, headers, credentialId, payloadFormat, pagination, timeoutMs | jobId, payloadArtifactId, status, rowsFetched, bytesFetched |
| `datatalk_infer_ingestion_schema` | jobId, dialect, sampleSize | mappingId, columns[], suggestedDdl, rowsAnalyzed |
| `datatalk_create_ingestion_table` | jobId, connectionId, schema, table, mappingHash, tokenId | jobId, targetTable, ddl |
| `datatalk_ingest_payload` | jobId, batchSize | jobId, rowsInserted, durationMs |
| `datatalk_get_ingestion_job` | jobId | job (full view) |
| `datatalk_list_ingestion_jobs` | status?, connectionId?, limit, offset | items[], total |

## Recommended orchestration sequence

1. **Fetch**: Call `datatalk_http_request` with the target URL, format, and optional pagination. Returns `jobId`.
2. **Infer**: Call `datatalk_infer_ingestion_schema` with `jobId`. Returns column mapping + suggested DDL.
3. **Present to user**: "I've prepared the ingestion preview. Please review the column mapping in the Tab and click [Confirm and Ingest] when ready."
4. **Wait for confirm**: User clicks confirm in the ingestion_job Tab. The backend issues a `IngestionConfirmedToken` (5-min single-use).
5. **Create table**: Call `datatalk_create_ingestion_table` with `jobId`, `connectionId`, `schema`, `table`, `mappingHash`, and `tokenId`.
6. **Insert data**: Call `datatalk_ingest_payload` with `jobId` and optional `batchSize`.

## Pagination heuristics

- If API returns a JSON array under a key (e.g., `data`), check if `data.length < pageSize` → last page
- For `page` pagination: start from page 1, increment until empty array or `maxPages` reached
- For `offset` pagination: increment by `pageSize`, terminate when result count < `pageSize`
- For `cursor` pagination: pass next cursor from response, terminate when no next cursor
- Default: no pagination (single request)

## Error handling

| Error code | Action |
|------------|--------|
| `INGESTION_SSRF_BLOCKED` | Ask user to use a public HTTPS URL or contact admin |
| `INGESTION_PAYLOAD_TOO_LARGE` | Reduce pageSize, narrow time range, or reduce maxPages |
| `INGESTION_AUTH_FAILED` | Check credentials in Settings → Credentials |
| `INGESTION_TOKEN_INVALID` | Re-open the ingestion Tab, click Confirm again (fresh 5-min token) |
| `INGESTION_DIALECT_UNSUPPORTED` | Switch to mysql/postgresql/h2/sqlite connection |
| `INGESTION_FETCH_FAILED` | Check URL, network, timeout |
| `INGESTION_FORMAT_UNSUPPORTED` | Day-1 supports JSON/JSONL/CSV/HTML only |
| `INGESTION_INFER_FAILED` | Check payload file is valid |

## DDL dialect limits

Day-1 supports: `mysql`, `postgresql`, `h2`, `sqlite`

Unsupported (return `INGESTION_DIALECT_UNSUPPORTED`): `mariadb`, `oracle`, `sqlserver`, `duckdb`, `clickhouse`, `apache_doris`, `starrocks`, `trino`, `presto`, `hive`, `tidb`, `oceanbase`, `dameng`, `kingbase`, `gaussdb`

Each unsupported kind gets a per-dialect follow-up child plan adding an `IngestionDdlAdapter` implementation.
