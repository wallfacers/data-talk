# External Data Ingestion via Skills — Generic HTTP Scaffolding Design

| 字段 | 值 |
|------|----|
| 日期 | 2026-05-12 |
| 状态 | Draft v1 |
| Roadmap | [Task 10 — External Data Ingestion via Skills](../exec-plans/2026-04-25-next-implementation-roadmap-plan.md#task-10-external-data-ingestion-via-skills-phase-3-placeholder) |
| 推广总设计 | [产品总设计 §3.12 外部数据接入与自动采集（Skill 驱动）](./index.md) |
| Phase | 三期 — 首份 child spec (shipped 2026-05-12) |
| 后续 child plans | 平台特定 skill（Taobao / JD / Pinduoduo / Douyin / etc）每个一份独立 child plan |

## 0. Gate 与前提

| Gate | 状态 |
|------|------|
| Task 8 至少一个生产切片 | ✅ ER Inspector + Designer 已 ship + 12 屏 Dashboard 重设计在途 |
| Task 9 至少一个稳定的目标数据源（first-class set 之外） | ✅ Wave A 4/4 + Wave B 7/7 + Wave C 5/6 共 16 个 first-class kind 在产 |
| Frontend Design Contract Gate（[`client/DESIGN.md`](../../client/DESIGN.md)） | ✅ 见 §6.0 Design Inputs |
| Data Source Type Compatibility Gate（[`docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`](../DATA_SOURCE_TYPE_COMPATIBILITY.md)） | ✅ 见 §10 |
| BUG Tracking Gate — 已检查 `docs/bugs/` 与 ingestion / http / skill 相关关键词 | ✅ 无相关 open BUG |

## 1. 动机与范围

### 1.1 动机

DataTalk 的可视化分析能力对「用户连接里已有的数据」很完整，但实际业务（电商运营、市场分析、舆情监控）经常需要把外部互联网数据先拉进来再分析。把这条链路通过 skill 系统插件化暴露，让 AI 在自然语言里完成「采集 → 落库 → 分析」全流程，DataTalk 主程序保持纯净、不绑死任何第三方平台。

### 1.2 Day-1 范围

「通用 HTTP scaffolding」一份 spec、一份 child plan、6 个串行 Phase 交付：

1. HTTP / REST / JSONL / CSV / 静态 HTML `<table>` 采集（含 page / offset / cursor 三型分页）
2. SecretVault 复用作 credential vault；4 种 auth scheme（None / Bearer / API Key / Basic）
3. 服务端 schema-inference helper（JSON / JSONL / CSV / HTML 四种 payload 格式）
4. Target-table CREATE TABLE 自动生成 + L2 二期风险流程二次确认
5. 入库后的 `ingestion_job` Tab + `ingestion_library` Tab（workspace scope 持久化）
6. `data-ingestion` skill 包（仿 bezel 路径）+ AGENTS.md 模板增量

### 1.3 显式不做（详见 §11 Out-of-Scope）

平台特定 skill、OAuth2/HMAC/mTLS、JS-rendered 网页、Excel/XML/TSV/Parquet、其余 15 个 first-class kind 写入、增量同步、定时调度、Staging table atomic swap、Schema migration、加密落盘、团队 vault、Webhook 触发、流式直写超 500MB、per-credential rate limit。

## 2. 架构与端到端数据流

### 2.1 三个新一等概念

| 概念 | 归属 | 寿命 |
|------|------|------|
| `ingestion_job` | 新 SQLite 表（V20） | 永久（用户主动归档/删除前） |
| `ingestion_payload` | `file_artifact` 新 kind，external-registered，盘上落 `~/.data-talk/ingestion/<jobId>/payload.<ext>` | 跟随 `ingestion_job` 行；job 删则归 `_trash` 走 housekeeping |
| `ingestion_credential` | 新 SQLite 表 + SecretVault id | 永久；与任何具体 connection 解耦，job 引用其 id |

### 2.2 Happy Path 11 步

1. 用户 Chat：「把 `https://api.example.com/orders?days=7` 抓进来」
2. AI 调 `datatalk_http_request(url, method, headers, queryParams, credentialId, payloadFormat, pagination?, htmlSelector?, timeoutMs)`
   - 服务端开 `ingestion_job` 行（status=`fetching`），发 `IngestionJobCreated` 事件
   - Spring `RestClient` + SecretVault 取 credential、按 scheme 注入
   - 分页循环（page/offset/cursor 三选一），每页 append 到 `.staging.<ext>` 临时文件
   - 命中 maxPages / `terminationHint` / 错误时停
   - atomic rename `.staging.<ext>` → `payload.<ext>`，register external file_artifact (kind=`ingestion_payload`)
   - 更新 ingestion_job: status=`fetched`, `payload_artifact_id`, `row_count`, `bytes_fetched`
   - 发 `IngestionPayloadFetched` 事件
   - 返回 `{ jobId, payloadArtifactId, status, rowsFetched, bytesFetched, pagesFetched }`
3. 前端通过 SSE 收到 `IngestionJobCreated` 事件 → `useIngestionJobsStore` 订阅器自动调 stage `openTab('ingestion_job', { id: jobId })`（无需 AI 显式 MCP 调用）
4. AI 调 `datatalk_infer_ingestion_schema(jobId, dialect, sampleSize)`
   - 服务端读 payload artifact，按 format 走 parser（JSON / JSONL / CSV / HTML）
   - 类型推断（含 dot-path 一层展开 + 多类型回退 TEXT(JSON)）
   - 返回 `{ mappingId, columns[], suggestedDdl, rowsAnalyzed, sampleRows[100] }`
   - ingestion_job.mapping_json 回填，status=`mapping`
5. Tab 渲染：phase stepper + 源信息 card + mapping editor + DDL preview + 前 100 行 sample
6. 用户在 Tab 内编辑 mapping（rename / type override / skip column），点「确认入库」
   - 前端 `POST /api/ingestion/jobs/<id>/confirm` 携 `mappingOverridesHash`
   - 服务端 `IngestionConfirmedTokenStore.issue(jobId, userId, mappingHash)` → 颁发 5 分钟单次性 token
   - 返回 `{ tokenId, expiresAt }`；Tab 切 status=`awaiting_confirm`
7. AI 调 `datatalk_create_ingestion_table(jobId, connectionId, targetSchema, targetTable, mappingOverrides, ingestionConfirmedToken)`
   - 服务端 token 校验 + 选 `IngestionDdlAdapter` + 生成 CREATE TABLE DDL
   - 走 `SqlStatementGuard.classifyAndAudit(ddl, connection, ingestionContext)` —— 写 audit 行 + 强制路径
   - `requiresConfirm=false`（已通过 Tab 确认，token 已 consume）
   - `ParameterizedSqlExecutor.executeDdl(...)` 真实执行
   - 更新 ingestion_job: target_schema / target_table / status=`writing`
8. AI 调 `datatalk_ingest_payload(jobId, batchSize=500)`
   - 流式读 payload artifact（JSON streaming / CSV / JSONL 逐行 / HTML 一次性 jsoup）
   - 按 mapping 投影 → batch INSERT → ParameterizedSqlExecutor
   - 每 batch 发 `IngestionWriteProgress` 事件 → SSE → Tab 进度条
9. 全部完成：status=`completed`, `rows_inserted`, `completed_at` 写入；发 `IngestionCompleted`
10. Tab 切 completed 视图：源 URL + 目标表 + 行数 + 用时 + payload artifact 链接
11. AI 顺势在同 session 出「已写入 orders 表，要不要画个折线图」→ 接 Task 8 链路（不在本 spec 范围）

### 2.3 异常分叉

- HTTP 失败 → status=`failed`, error_message 写入, payload artifact 不创建, Tab 显示「重试」
- Schema 推断失败 → status=`failed`, payload artifact 保留, 可「重新推断」
- Token 过期 / mapping 改动后 hash 不匹配 / 已 consume → 拒绝, 提示重新走 step 6
- INSERT batch 中途失败 → status=`failed`, error_message + 停止；**target table 不回滚**（保护用户已有数据，Day-1 决策）；用户在 Tab 点「清表重跑」或新建 job
- 用户主动取消 → status=`cancelled`, payload artifact 进 `_trash`

### 2.4 与现有主线耦合 / 解耦

**耦合点**：
- Task 5 L3 risk：CREATE TABLE 必经 SqlStatementGuard；INSERT batch 经 ParameterizedSqlExecutor —— 不开后门
- Task 6 跨 session 持久化：两个 Tab type 走 `tab-type-registry` workspace scope，进 `stage_tab` 表
- Task 11 file_artifact：复用 External-Registered 模式（与 Dashboard 同形态），新增 `ingestion_payload` kind 进 V20 CHECK rebuild

**解耦点**：不动 ER / ER Designer / Dashboard / chart fence / SqlStatementGuard 分类规则 / connection / database / schema 模型 / OpenCode 协议层 / SessionBus。

## 3. Backend Model

### 3.1 V20 Flyway Migration（`V20__ingestion.sql`）

合并三件事单文件交付（与 V18 合并 CHECK rebuild + external 列同形态）：

```sql
-- 1) ingestion_credential 表
CREATE TABLE ingestion_credential (
  id           TEXT PRIMARY KEY,
  name         TEXT NOT NULL UNIQUE,
  auth_scheme  TEXT NOT NULL CHECK(auth_scheme IN
                 ('none','bearer','api_key_header','api_key_query','basic')),
  config_json  TEXT NOT NULL,            -- 非密配置: {headerName?, queryName?, basicUsername?}
  vault_id     TEXT,                     -- NULL when scheme=none; SecretVault 内 id
  created_at   INTEGER NOT NULL,
  updated_at   INTEGER NOT NULL
);

-- 2) ingestion_job 表
CREATE TABLE ingestion_job (
  id                      TEXT PRIMARY KEY,
  source_url              TEXT NOT NULL,
  source_method           TEXT NOT NULL CHECK(source_method IN ('GET','POST')),
  source_headers_json     TEXT,
  source_query_params_json TEXT,
  source_body_json        TEXT,
  credential_id           TEXT,           -- 应用层 ON DELETE SET NULL（Day-1 显式逻辑实现，非 FK）
  pagination_json         TEXT,
  payload_format          TEXT NOT NULL CHECK(payload_format IN ('json','jsonl','csv','html')),
  payload_artifact_id     TEXT,           -- 应用层级联，非 FK
  status                  TEXT NOT NULL CHECK(status IN
                            ('pending','fetching','fetched','mapping','awaiting_confirm',
                             'writing','completed','failed','cancelled')),
  connection_id           TEXT,
  target_schema           TEXT,
  target_table            TEXT,
  mapping_json            TEXT,
  row_count               INTEGER,
  rows_inserted           INTEGER DEFAULT 0,
  bytes_fetched           INTEGER,
  created_at              INTEGER NOT NULL,
  updated_at              INTEGER NOT NULL,
  completed_at            INTEGER,
  error_message           TEXT
);
CREATE INDEX idx_ingestion_job_status     ON ingestion_job(status);
CREATE INDEX idx_ingestion_job_connection ON ingestion_job(connection_id) WHERE connection_id IS NOT NULL;
CREATE INDEX idx_ingestion_job_created    ON ingestion_job(created_at);

-- 3) 扩 file_artifact.kind CHECK：加入 'ingestion_payload'
-- 当前 V18 已含 ('report','er_diagram','sql_script','dataset','dashboard','other')
-- V20 整表重建模式（同 V18），新 CHECK：
--   kind IN ('report','er_diagram','sql_script','dataset','dashboard','ingestion_payload','other')
-- 流程：CREATE TABLE file_artifact_new (with new CHECK + 保持 external/index)
--      INSERT ... SELECT ... FROM file_artifact
--      DROP TABLE file_artifact
--      ALTER TABLE file_artifact_new RENAME TO file_artifact
--      重建 4 个现有索引（idx_file_artifact_session/connection/status/external）
```

**Rollback 路径**：down migration 前必须先 `DELETE FROM file_artifact WHERE kind='ingestion_payload'` + `DROP TABLE ingestion_job; DROP TABLE ingestion_credential;`，手工 helper `tools/rollback/V20-helper.sql` 提供。

**关键决策**：
- `ingestion_credential` 与 connection 解耦 —— 一份凭据可被多个 job 复用，跨多个 connection
- `credential_id` / `payload_artifact_id` 不加 SQL FK 约束（与 file_artifact `session_id`/`connection_id` 同治理风格，应用层 `IngestionJobService.deleteRecord` 级联）
- `IngestionJobStatus` 9 态：`pending → fetching → fetched → mapping → awaiting_confirm → writing → completed`；失败 → `failed`；用户取消 → `cancelled`

### 3.2 Domain（`server/data-talk-domain/`）

```java
package com.datatalk.domain.ingestion;

public sealed interface IngestionJobStatus
    permits Pending, Fetching, Fetched, Mapping, AwaitingConfirm,
            Writing, Completed, Failed, Cancelled { ... }

public enum AuthScheme { NONE, BEARER, API_KEY_HEADER, API_KEY_QUERY, BASIC }
public enum PayloadFormat { JSON, JSONL, CSV, HTML }
public enum PaginationType { NONE, PAGE, OFFSET, CURSOR }
public enum InferredType {
    BOOLEAN, INTEGER_32, INTEGER_64, DECIMAL,
    DATE, TIMESTAMP,
    STRING_64, STRING_256, STRING_500, STRING_LONG,
    JSON
}

public record IngestionJob(String id, String sourceUrl, String sourceMethod, /* ... */) {}
public record IngestionCredential(String id, String name, AuthScheme scheme,
                                  Map<String, String> configNonSecret, String vaultId,
                                  long createdAt, long updatedAt) {}
public record IngestionMapping(String mappingId, List<MappingColumn> columns) {}
public record MappingColumn(String sourcePath, String targetName, InferredType type,
                            boolean skip, List<String> sampleValues, boolean nullable) {}
public record PaginationSpec(PaginationType type, Map<String, Object> params,
                             int maxPages, TerminationHint terminationHint) {}
public record TerminationHint(TerminationHintType type, String jsonPath) {}
public enum TerminationHintType { EMPTY_ARRAY, JSON_PATH_COUNT_ZERO, HTTP_STATUS_404 }
```

**新 8 DtEvent**（extend `DtEvent` sealed interface — 注意 application 层 exhaustive switch 同步）：

```
IngestionJobCreated      { jobId, sourceUrl }
IngestionPayloadFetched  { jobId, payloadArtifactId, rowCount, bytesFetched }
IngestionMappingProposed { jobId, mappingId, columnCount }
IngestionJobConfirmed    { jobId, tokenId }
IngestionWriteStarted    { jobId, targetTable }
IngestionWriteProgress   { jobId, rowsInserted, totalRows }
IngestionCompleted       { jobId, targetTable, finalRowCount, durationMs }
IngestionFailed          { jobId, phase, errorMessage }
```

新扩 `FileArtifactKind` enum：加 `INGESTION_PAYLOAD` 项。

### 3.3 Application（`server/data-talk-application/`）

```
ingestion/
├── IngestionJobService.java               # CRUD + state machine
├── IngestionPayloadFetcher.java           # HttpClient + 分页循环 + 落盘 + register artifact
├── IngestionSchemaInferrer.java           # 4 format parser + type inference
├── IngestionExecutor.java                 # CREATE TABLE + INSERT batch（调 SqlStatementGuard）
├── IngestionCredentialService.java        # SecretVault hookup
├── IngestionConfirmedTokenStore.java      # 颁发 / 校验 / 过期 / 单次
├── ddl/
│   ├── IngestionDdlAdapter.java          # 接口
│   ├── MysqlIngestionDdlAdapter.java
│   ├── PostgresIngestionDdlAdapter.java
│   ├── H2IngestionDdlAdapter.java
│   └── SqliteIngestionDdlAdapter.java
└── parser/
    ├── JsonPayloadParser.java
    ├── JsonlPayloadParser.java
    ├── CsvPayloadParser.java              # RFC4180
    └── HtmlTablePayloadParser.java        # jsoup
```

### 3.4 `IngestionDdlAdapter`

```java
public interface IngestionDdlAdapter {
    boolean supports(ConnectionKind kind);

    /** Generate dialect-specific CREATE TABLE DDL. */
    String generateCreateTable(String schema, String table, List<MappingColumn> columns);

    /** Generate INSERT prepared SQL with ? placeholders. */
    String generateInsert(String schema, String table, List<MappingColumn> columns);

    /** Map InferredType to dialect-specific SQL type. */
    String sqlTypeFor(InferredType inferred);
}
```

**Day-1 类型映射统一表**：

| InferredType | MySQL | PostgreSQL | H2 | SQLite |
|--------------|-------|-----------|-----|--------|
| STRING_64 | VARCHAR(64) | VARCHAR(64) | VARCHAR(64) | TEXT |
| STRING_256 | VARCHAR(256) | VARCHAR(256) | VARCHAR(256) | TEXT |
| STRING_500 | VARCHAR(500) | VARCHAR(500) | VARCHAR(500) | TEXT |
| STRING_LONG | TEXT | TEXT | CLOB | TEXT |
| INTEGER_32 | INT | INTEGER | INT | INTEGER |
| INTEGER_64 | BIGINT | BIGINT | BIGINT | INTEGER |
| DECIMAL | DECIMAL(38,10) | NUMERIC(38,10) | DECIMAL(38,10) | NUMERIC |
| BOOLEAN | TINYINT(1) | BOOLEAN | BOOLEAN | INTEGER |
| TIMESTAMP | DATETIME | TIMESTAMP | TIMESTAMP | TEXT (ISO-8601) |
| DATE | DATE | DATE | DATE | TEXT |
| JSON | JSON | JSONB | CLOB | TEXT |

**Dialect 边界**：当前 `ConnectionKind` 共 19 个一等 kind，Day-1 ingestion 写入仅支持 4 个（mysql / postgresql / h2 / sqlite）。其余 15 个（mariadb / oracle / sqlserver / duckdb / clickhouse / apache_doris / starrocks / trino / presto / hive / tidb / oceanbase / dameng / kingbase / gaussdb）Day-1 在 ingestion 路径上抛 `IngestionDialectUnsupportedException` → 前端 Tab 显示 `dialect_unsupported` + i18n key `ingestion.dialect_unsupported.<kind>`。每个 unsupported kind 走独立 child plan 加 `IngestionDdlAdapter` 实现，与诊断 day2 plan / ER per-kind 同治理。

### 3.5 Infrastructure（`server/data-talk-infrastructure/`）

- `JdbcIngestionJobRepository`、`JdbcIngestionCredentialRepository`
- `OpenCodeBinaryResolver.ensureDataIngestionSkill(projectRoot)` —— 仿 `ensureBezelSkill` 形态
- `db/migration/V20__ingestion.sql`
- `FlywayMigrationIT` 新增：V20 应创建 `ingestion_job` + `ingestion_credential`，且 `file_artifact.kind` CHECK 接受 `'ingestion_payload'`，且 dashboard / external / 4 索引 不退化

### 3.6 Adapter（`server/data-talk-adapter/`）

**REST endpoints**（`IngestionController`）：

| Method | Path | 用途 |
|--------|------|------|
| GET    | `/api/ingestion/jobs` | 列表，filter by status / connection / page |
| GET    | `/api/ingestion/jobs/{id}` | 详情 |
| GET    | `/api/ingestion/jobs/{id}/payload-preview?limit=100` | 前 N 行 sample |
| POST   | `/api/ingestion/jobs/{id}/confirm` | 颁发 IngestionConfirmedToken |
| POST   | `/api/ingestion/jobs/{id}/cancel` | 主动取消 |
| GET    | `/api/ingestion/credentials` | credential 列表 |
| POST   | `/api/ingestion/credentials` | 创建 |
| PUT    | `/api/ingestion/credentials/{id}` | 更新 |
| DELETE | `/api/ingestion/credentials/{id}` | 删除（被引用时返 409 + 引用 jobs，强制 SET NULL 后再删） |

**新 6 个 `@DataTalkAction`**：详见 §4。

## 4. MCP Tool Surface + L2 Confirm

### 4.1 6 个 MCP Tool

#### 4.1.1 `datatalk_http_request`

```jsonc
// 入参
{
  "url": "https://api.example.com/orders",
  "method": "GET",
  "headers": { "Accept": "application/json" },
  "queryParams": { "days": "7" },
  "body": null,
  "credentialId": "cred_abc123",            // 可选
  "payloadFormat": "json",                   // json | jsonl | csv | html
  "pagination": {                            // 可选
    "type": "page",                          // page | offset | cursor
    "params": {
      "pageParam": "page", "sizeParam": "size",
      "pageSize": 50, "startPage": 1
    },
    "maxPages": 50,
    "terminationHint": {
      "type": "empty_array",                 // empty_array | json_path_count_zero | http_status_404
      "jsonPath": "$.data"
    }
  },
  "htmlSelector": "table.orders",            // 仅 payloadFormat=html
  "timeoutMs": 60000
}

// 返回
{
  "jobId": "ing_2026051200001",
  "payloadArtifactId": "fa_xxx",
  "status": "fetched",
  "rowsFetched": 1247,
  "bytesFetched": 458320,
  "pagesFetched": 25,
  "error": null
}
```

服务端**绝不**通过 MCP body 返回 raw payload —— 控制 OpenCode protocol 体积，防 AI 误把 payload 塞 context。

#### 4.1.2 `datatalk_infer_ingestion_schema`

```jsonc
// 入参
{ "jobId": "ing_xxx", "dialect": "mysql", "sampleSize": 100 }

// 返回
{
  "mappingId": "map_xxx",
  "columns": [
    { "sourcePath": "$.id", "targetName": "id",
      "inferredType": "INTEGER_64", "sampleValues": ["1001","1002"], "nullable": false }
  ],
  "suggestedDdl": "CREATE TABLE \"public\".\"orders\" ( ... )",
  "rowsAnalyzed": 100
}
```

**推断算法**：
- JSON / JSONL：取前 N 行，dot-path 展开**一层**（深嵌套 → 整体 JSON）
- CSV：第一行 header；类型从后续行内容投票
- HTML：第一行 `<th>` 或第一 `<tr>` 作 header
- 类型 fallback 顺序：BOOLEAN → INTEGER_32 → INTEGER_64 → DECIMAL → DATE → TIMESTAMP → STRING（容量按最长 sample）→ JSON
- 多类型混合 → 走最宽类型

#### 4.1.3 `datatalk_create_ingestion_table`

```jsonc
// 入参
{
  "jobId": "ing_xxx",
  "connectionId": "conn_xxx",
  "targetSchema": "public",
  "targetTable": "orders",
  "mappingOverrides": {
    "renames": { "$.id": "order_id" },
    "typeOverrides": { "$.customer.name": "STRING_500" },
    "skips": ["$.internal_field"]
  },
  "ingestionConfirmedToken": "ict_abc..."
}

// 返回（成功）
{ "status": "writing", "createdTable": "public.orders", "ddlExecuted": "CREATE TABLE ...", "error": null, "userHint": null }

// 返回（失败 — token 类错误必带 userHint 引导 AI 下一步动作）
{ "status": "failed", "createdTable": null, "ddlExecuted": null,
  "error": { "code": "INGESTION_TOKEN_INVALID", "reason": "expired" },
  "userHint": "Confirmation token expired or invalidated. Ask the user to reopen the ingestion_job(id=<jobId>) Tab and click [Confirm and Ingest] again — this re-issues a fresh 5-minute token." }
```

服务端流程：token 校验 → 选 adapter → 生成 DDL → SqlStatementGuard `classifyAndAudit(ingestionContext)` → `ParameterizedSqlExecutor.executeDdl(...)`。

**`userHint` 字段**：所有 6 个 MCP tool 的错误返回**必须**带 `userHint` 字段，给 AI 一条具体的「下一步动作」自然语言指引（不依赖 AI 自行推理协议细节）。典型场景：
- `INGESTION_TOKEN_INVALID` → 引导用户回 Tab 重新点 confirm
- `INGESTION_DIALECT_UNSUPPORTED` → 告知用户切换 connection 或等待对应 follow-up child plan
- `INGESTION_PAYLOAD_TOO_LARGE` → 提示拆分时间范围或缩 maxPages
- `INGESTION_SSRF_BLOCKED` → 提示 URL 命中 deny list，不可访问
- `INGESTION_AUTH_FAILED` → 引导用户去 Settings → Credentials 检查凭据

#### 4.1.4 `datatalk_ingest_payload`

```jsonc
// 入参
{ "jobId": "ing_xxx", "batchSize": 500 }

// 返回
{ "status": "completed", "rowsInserted": 1247, "durationMs": 8420, "error": null }
```

每 batch 发 `IngestionWriteProgress` 事件 → SSE → Tab 进度条。失败不回滚 target table。

#### 4.1.5 `datatalk_get_ingestion_job`

```jsonc
// 入参 → 返回
{ "jobId": "ing_xxx" } → IngestionJob 全字段
```

#### 4.1.6 `datatalk_list_ingestion_jobs`

```jsonc
// 入参 → 返回
{ "connectionId"?: "conn_xxx", "status"?: "completed", "createdAfter"?: 1715472000000, "limit"?: 50 }
→ { "items": [...], "total": N, "page": 1 }
```

### 4.2 `IngestionConfirmedToken` 协议

**目的**：把 Tab 内 confirm 与服务端 CREATE TABLE 执行用密码学方式 link，免重复 modal 但不绕开 SqlStatementGuard 强制路径。

**生命周期**：

| 步骤 | 行为 |
|------|------|
| 1 | 用户在 Tab 点 `[确认入库]` |
| 2 | 前端 `POST /api/ingestion/jobs/<id>/confirm`，body 含 `mappingOverridesHash = sha256(mappingOverrides_canonical_json)` |
| 3 | 服务端 `IngestionConfirmedTokenStore.issue(jobId, userId, mappingHash)`：tokenId=`ict_`+nanoId(16)，expiresAt=now+5min，consumed=false，存内存 `ConcurrentHashMap`；过期被惰性 GC |
| 4 | 返回 `{ tokenId, expiresAt }`；ingestion_job.status=`awaiting_confirm`；发 `IngestionJobConfirmed` 事件（token id 进 metadata） |
| 5 | AI 调 `datatalk_create_ingestion_table` 携带 tokenId |
| 6 | 服务端 `consume(tokenId, jobId, mappingOverridesHash)`：不存在 / 过期 / 已 consumed / jobId 不匹配 / mappingHash 不匹配 → 抛 `IngestionTokenInvalid` |
| 7 | SqlStatementGuard 拿到 `ingestionContext.tokenConsumed=true` 后：仍跑分类 + audit；`requiresConfirm=false`；仍可拒（DROP / 非 CREATE TABLE 等不合法 SQL）—— 纵深防御 |

**服务重启 token 全失效**：用户在 Tab 重新点 confirm 颁发新 token。job 状态在 DB 持久化，重启不丢。

### 4.3 审计 fan-out

| 事件 / 表 / 字段 | 审计点 |
|------|------|
| `ingestion_job` 表 | source URL / method / headers / credential_id / created_at |
| `IngestionPayloadFetched` 事件 + `file_artifact` 行 | payload reference（永久可回溯） |
| `IngestionJobConfirmed` 事件（含 tokenId） | 用户确认动作 |
| `IngestionWriteStarted/Completed/Failed` + `ingestion_job` 状态变更 | 写入生命周期 |
| `SqlStatementGuard` audit | CREATE DDL + 每条 INSERT batch |

合规 §3.8「source URL / run timing / raw payload references」三项全覆盖。如未来引入统一 `audit_log` 表，此两表可 JOIN。

## 5. Skill Bundle + AGENTS.md

### 5.1 `data-ingestion` 源码

```
server/data-talk-infrastructure/src/main/resources/opencode/skills-src/data-ingestion/
├── SKILL.md
├── recipes/
│   ├── basic-rest-fetch.md
│   ├── paginated-fetch.md
│   ├── csv-bulk-import.md
│   ├── html-table-scrape.md
│   └── error-recovery.md
└── examples/
    ├── github-issues.json
    ├── public-csv.json
    └── wikipedia-table.json
```

### 5.2 `SKILL.md` frontmatter

```yaml
---
name: data-ingestion
description: |
  Use when the user asks to fetch external data (REST/JSON/CSV/static HTML table) and write it
  into a SQL connection. Triggers on phrases like 抓取/拉取/采集/落库/导入/ingest/scrape/sync from
  API/import from URL. Provides the full chain: HTTP fetch → schema inference → user-confirmed
  CREATE TABLE → batch INSERT. Day-1 supports MySQL/PostgreSQL/H2/SQLite as ingestion targets and
  None/Bearer/API Key/Basic auth schemes.
---
```

仅用 `name` + `description` —— 与 bezel 同治理（OpenCode 与 Claude Code 共有字段）。

### 5.3 打包路径

`server/data-talk-infrastructure/pom.xml` maven-assembly-plugin 新增 descriptor `src/assembly/data-ingestion-skill.xml`，构建期输出 `opencode/skills/data-ingestion.tar.gz` + `opencode/skills/data-ingestion.version`（v1.0.0）入 classpath。

### 5.4 `OpenCodeBinaryResolver.ensureDataIngestionSkill`

完全仿 `ensureBezelSkill(Path projectRoot)`：marker `.data-ingestion-installed`，版本号比对，幂等，解压失败 noop。`OpenCodeProcessManager` 在 `ensureBezelSkill` 后追加调用。

### 5.5 `AGENTS.md` 模板增量

修改 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`（classpath template，运行时副本启动重写）。

新增 `## Data Ingestion (skill: data-ingestion)` 段：6 行内 link 到 SKILL.md + 6 个 MCP tool 名 + L2 不绕路提醒 + Day-1 dialect 限制 + Day-1 format 限制 + 平台特定 skill 后续 child plan 引导。

`AgentsTemplateContractTest` 断言：AGENTS.md 含 `## Data Ingestion` 段 + 含 `skills/data-ingestion/SKILL.md` 引用 —— 防后续模板重构误删。

## 6. Frontend Tab UI + Settings

### 6.0 Design Inputs（[`client/DESIGN.md`](../../client/DESIGN.md)）

| 约束 | 应用 |
|------|------|
| Stage state 全局，非 per-session | `ingestion_job` / `ingestion_library` 走 `tab-type-registry.scope='workspace'`，`StageTab` 实例无 `scope` 字段；切 session 不丢 Tab、不丢 mapping 编辑状态 |
| Tokens, not raw colors | 所有色值来自 `bg.* / text.* / border.* / accent.* / status.* / interaction.*` |
| 双通道状态信号 | phase 指示器、错误态用 icon + color 同时表达 |
| cobalt 严格保留 for focus / selection / primary action | 不撒在 chrome、不当装饰 |
| Stage chrome `bg.subtle`, surface `bg.canvas`, tabActive `accent.primary` | 直接复用 |
| Table headerBg `bg.subtle`, rowHover `interaction.hover`, rowSelected `interaction.selected`，数字列走 `mono-sm/md` | 直接复用 |
| Typography 仅 `ui-xs/sm/md/lg/xl/2xl` + `mono-sm/md` | 零新增 |
| Density：Tab 内 toolbar / metadata / table = `compact`；Confirm 区 = `focused` | — |
| Motion：phase 切换 / 进度条 = `normal` 180ms + `standard` easing；`prefers-reduced-motion` 必须禁用 | — |
| 控件五态显式 token 映射（auto-memory `feedback_design_control_states` 规则） | 见 §6.3 |

### 6.1 Tab 注册

```ts
// client/src/features/stage/types/tab-type-registry.ts
ingestion_job: {
  scope: 'workspace',
  icon: 'Download',
  defaultTitle: (t, props) => t('ingestion.job.title', { id: props.id.slice(0,8) }),
  uniquenessKey: (props) => `ingestion_job:${props.id}`,
  capabilities: ['close','rename','maximize'],
},
ingestion_library: {
  scope: 'workspace',
  icon: 'Library',
  defaultTitle: (t) => t('ingestion.library.title'),
  uniquenessKey: () => 'ingestion_library',
  capabilities: ['close','maximize'],
},
```

### 6.2 Feature 目录

```
client/src/features/ingestion/
├── ingestion-job-tab.tsx               # 主 Tab，phase 路由
├── phases/
│   ├── fetching-phase.tsx
│   ├── mapping-phase.tsx
│   ├── writing-phase.tsx
│   ├── completed-phase.tsx
│   └── failed-phase.tsx
├── ingestion-library-tab.tsx
├── components/
│   ├── mapping-editor.tsx
│   ├── payload-preview-table.tsx
│   ├── ddl-preview.tsx
│   ├── pagination-config-display.tsx
│   └── source-summary-card.tsx
├── stores/useIngestionJobsStore.ts
├── hooks/
│   ├── useIngestionJobQuery.ts
│   ├── useIngestionJobsQuery.ts        # list, 5s polling
│   └── usePayloadPreviewQuery.ts
└── api/ingestion-api.ts
```

### 6.3 控件五态 token 映射

#### Target Name `<input>` / Connection / Schema / Table 名 input

| 态 | 背景 | 文本 | 边框 |
|----|------|------|------|
| idle | `bg.panel` | `text.base` | `border.default` |
| hover | `bg.panel` | `text.base` | `border.strong` |
| focus | `bg.panel` | `text.strong` | `border.default` + outline `interaction.focusRing` 2px |
| active | `bg.panel` | `text.strong` | `interaction.active` |
| disabled | `bg.subtle` | `interaction.disabled` | `border.subtle` |

#### SQL Type `<select>`

同 input 五态；下拉打开时 trigger 走 `interaction.selected`。

#### Skip 复选框

| 态 | 框 | 勾 |
|----|----|----|
| idle (unchecked) | `bg.canvas` border `border.default` | hidden |
| hover (unchecked) | `bg.canvas` border `border.strong` | hidden |
| focus | + outline `interaction.focusRing` | — |
| checked | `accent.primary` 实色 | `text.inverse` check icon |
| disabled | `interaction.disabled` 灰底 | — |

#### Primary Button「确认入库」

| 态 | 背景 | 文本 | 边框 |
|----|------|------|------|
| idle | `accent.primary` | `text.inverse` | none |
| hover | `accent.primaryHover` | `text.inverse` | none |
| focus | `accent.primary` + outline `interaction.focusRing` 2px offset 2px | `text.inverse` | none |
| active | `accent.primaryHover` filter:brightness(0.95) | `text.inverse` | none |
| disabled | `interaction.disabled` 灰底 | `text.inverse` opacity 0.6 | none |

#### Secondary / Ghost Button「Cancel」

| 态 | 背景 | 文本 | 边框 |
|----|------|------|------|
| idle | transparent | `text.muted` | `border.default` |
| hover | `interaction.hover` | `text.base` | `border.strong` |
| focus | transparent + ring `interaction.focusRing` | `text.base` | `border.default` |
| active | `interaction.active` | `text.strong` | `border.strong` |
| disabled | transparent | `interaction.disabled` | `border.subtle` |

#### Mapping Table 行

| 态 | 背景 | 文本 |
|----|------|------|
| idle | `bg.canvas` | `text.base` |
| hover | `interaction.hover` | `text.base` |
| focus (键盘 row focus) | `interaction.hover` + outline `interaction.focusRing` inset | `text.base` |
| skip=true | `bg.subtle` | `text.muted` + line-through |
| disabled | `bg.subtle` | `interaction.disabled` |

（注：skip=true **不**走 `interaction.selected` —— selected 语义保留给「当前焦点行」，skip 是状态而非选择）

#### Password Input（Settings → Credentials）

| 态 | 背景 | 文本 | 边框 |
|----|------|------|------|
| idle | `bg.panel` | `text.base` (masked dots) | `border.default` |
| hover | `bg.panel` | `text.base` | `border.strong` |
| focus | `bg.panel` | `text.strong` | `border.default` + ring `interaction.focusRing` |
| active | `bg.panel` | `text.strong` | `interaction.active` |
| disabled | `bg.subtle` | `interaction.disabled` | `border.subtle` |

「显示/隐藏密码」眼睛 icon button：与 Secondary Button 五态相同 token。

#### Phase Stepper Dot

| 态 | dot 填充 | dot 边框 | icon |
|----|----------|----------|------|
| pending | transparent | `border.default` | (none) `text.muted` |
| current | `accent.primary` filled | `accent.primary` | `text.inverse` filled circle |
| done | `status.success` filled | `status.success` | `text.inverse` check |
| error | `status.danger` filled | `status.danger` | `text.inverse` alert-circle |

### 6.4 i18n

新增到 `client/src/i18n/messages.ts`（项目实际 i18n 位置，非 JSON locale 文件）：

```
ingestion.job.title
ingestion.job.phase.{fetching, mapping, awaiting_confirm, writing, completed, failed, cancelled}
ingestion.library.title
ingestion.library.columns.{status, source, target, rows, created}
ingestion.mapping.column.{source, target, type, skip}
ingestion.confirm.button / ingestion.cancel.button
ingestion.credential.scheme.{none, bearer, api_key_header, api_key_query, basic}
ingestion.dialect_unsupported.<kind>     # 15 个 unsupported first-class kind 全补齐
ingestion.error.{network_timeout, auth_failed, pagination_overflow, schema_inference_failed,
                 token_expired, token_already_consumed, payload_too_large, ssrf_blocked,
                 http_status_error, parse_error, ddl_unsupported}
```

### 6.5 Settings → Credentials sub-page

`client/src/features/settings/credentials/`：复用 Settings 现有左侧导航 + 右侧面板。

**与 Connection 凭据的关系说明**：Settings Credentials sub-page 顶部 **MUST** 显示一行 `bg.subtle` 信息条（icon=Info + `text.muted` ui-sm 字号）：「这些凭据用于外部 HTTP / REST 数据采集，与数据库连接凭据（Settings → Connections）独立。一份凭据可被多个采集 job 复用，跨多个目标数据库连接。」防止用户误以为这是数据库连接的密码管理界面。

- 列表：Name / Auth scheme badge / Created / Used by N jobs / [Edit] [Delete]
- 创建表单：Name 唯一 + Auth scheme 4 radio + 条件字段：
  - Bearer → `Token` (type=password)
  - API Key → 子单选 `Header | Query` + Name + Value (type=password)
  - Basic → Username + Password (type=password)
- 服务端 `POST/PUT /api/ingestion/credentials` → SecretVault.seal → vault_id 落库
- 删除：被引用时返 409，弹 confirm modal（status=warning），确认后 SET NULL on job rows

### 6.6 测试

- **vitest** (`*.test.tsx`)：mapping-editor 列编辑、phase 路由、双通道 status badge、控件 disabled 状态、credential form 条件渲染、5 态 token 映射快照
- **Playwright E2E** (`client/tests/e2e/ingestion.spec.ts`)：mock backend happy path（layout/溢出/滚动类断言**只能**走 Playwright，按 auto-memory `feedback_jsdom_layout_not_truth`）

## 7. Skill / AGENTS / Verification Gates

- 启动期 `ensureDataIngestionSkill` 幂等 + 版本升级生效
- `AgentsTemplateContractTest` 通过
- SKILL.md frontmatter 仅 `name` + `description` 字段
- Plan child 每 Phase 完成 `cd server && mvn clean verify` + (P4 起) `cd client && npx tsc --noEmit` + vitest 全绿才进下一 Phase

## 8. Out-of-Scope

| # | 项 | 不做原因 / follow-up 触发 |
|---|----|------|
| 1 | 平台特定 skill（淘宝 / 京东 / 拼多多 / 抖音电商 / GitHub Enterprise / Stripe / Slack 等） | Roadmap 硬约束：先通用后特定。每平台一份独立 child plan |
| 2 | OAuth2 / OAuth1 / HMAC / mTLS / Cookie 签名注入 | 复杂度高超 Day-1，auth_scheme 枚举留扩展位 |
| 3 | JavaScript-rendered 网页 | 需 headless browser，Tauri runtime 增 200MB+。AI 走 bash + curl fallback（用户显式同意） |
| 4 | XML / Excel(.xlsx) / TSV / Parquet / Protobuf payload | 单独 parser 工作量大，follow-up 独立 plan |
| 5 | Auto-table 写入到非 mysql/postgresql/h2/sqlite 的 15 个 first-class kind（mariadb / oracle / sqlserver / duckdb / clickhouse / apache_doris / starrocks / trino / presto / hive / tidb / oceanbase / dameng / kingbase / gaussdb） | 每 kind 独立 child plan 加 `IngestionDdlAdapter` 实现，与诊断 day2 / ER per-kind 同治理 |
| 6 | 增量同步 / cursor 续传 | Day-1 每 job 全量重跑；增量需 `last_cursor_value` / `incremental_strategy` 列与调度 |
| 7 | 定时 / 周期任务 | Day-1 全手动触发；Cron / Quartz 集成 follow-up |
| 8 | Staging table + atomic swap | Section 2 已锁定：失败不回滚 target table；原子化升级 follow-up |
| 9 | Schema migration（target 已存在表 + 新字段） | Day-1 仅支持 CREATE TABLE（表必须不存在）；ALTER 留 follow-up |
| 10 | Payload 加密落盘 | 与 Files Library 同治；如需加密，整 Task 11 file_artifact 一并升级 |
| 11 | 凭据共享 / 团队级 vault | SecretVault 当前单用户本地 master key 模型 |
| 12 | Webhook 触发 ingestion | server 不暴露反向 webhook 入口 |
| 13 | payload > 500MB 流式直写 | 硬上限 `http.fetch.payload-max-bytes=500MB`；无限流式 follow-up |
| 14 | Rate-limit per credential / per host | Day-1 仅做整 job 总超时 + maxPages；RPS 限流 follow-up |

## 9. Risks

| ID | Risk | 缓解 |
|----|------|------|
| R1 | SqlStatementGuard CHECK 对 CREATE TABLE 部分 dialect 不严 | `IngestionDdlAdapter.generateCreateTable` 仅出固定模板（不含 DROP/ALTER/嵌套 SELECT），guard 拒绝其它语句即纵深防御 |
| R2 | payload artifact 抢占磁盘 | 500MB 硬上限 + housekeeping 已识别 `_trash`；Files Library 用户可主动归档 |
| R3 | 多页 fetch 中途网络抖动 | 单 job 整体超时 + maxPages 兜底；中途失败 = 整 job 失败，不保留半成品；Day-1 不做断点续拉 |
| R4 | 凭据泄漏到 chat 历史 | Settings 表单收口；`datatalk_http_request` 仅接 `credentialId`；OpenCode session_diff 拿不到 raw secret |
| R5 | AI 调 http_request 抓内网 / file:// / metadata SSRF | URL 校验仅 `http://`/`https://`；deny list（`DATATALK_INGESTION_HOST_DENY` env，默认 `localhost`/`127.0.0.1`/`169.254.169.254`/`metadata.google.internal`/`metadata.azure.com` 等）；Settings 可关（带警告） |
| R6 | HTML jsoup parse 失败 / encoding 误判 | 从 HTTP `Content-Type` charset 读取，缺省 UTF-8；失败 → status=failed |
| R7 | `IngestionConfirmedToken` 内存丢失（服务重启） | 用户重新点 confirm；job 状态 DB 持久化，重启不丢 |
| R8 | Skill tar.gz 解压失败 | 与 bezel 同 fallback：log + noop，AI 仅靠 MCP description 仍可用 |
| R9 | 大 payload 反序列化 OOM | JSON streaming（Jackson）；CSV/JSONL 逐行；HTML 受 maxPages × per-page 大小双重制约 |
| R10 | AI 不读 SKILL.md 直接乱调 | MCP tool description 进 system prompt；input schema 校验强制 |
| R11 | BUG-0009 dashboard kind 渲染回归 | V20 CHECK rebuild 需在 migration 测试中验证 dashboard kind 仍生效 |
| R12 | i18n key 命中规则 | `dialect_unsupported.<kind>` 12 个 key 一次性补齐，缺则兜底英文不报错 |

## 10. Data Source Type Compatibility Gate（[`docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`](../DATA_SOURCE_TYPE_COMPATIBILITY.md)）

| 检查项 | 状态 |
|--------|------|
| 前端连接 UI | **N/A** — 本 spec 不新增 connection kind；ingestion 复用现有 connection.* 字段 |
| 后端 JDBC connection handling | **复用** — 通过现有 `ConnectionService` + JDBC pool；ingestion 不动 connection 模型 |
| Schema discovery | **复用** — `IngestionExecutor` 调用现有 metadata API 判断 target table 是否已存在 |
| SQL execution | **复用** — `ParameterizedSqlExecutor` 走现有 batch INSERT，零分支 |
| SQL splitting / risk analysis | **新增调用方** — `IngestionExecutor.createTable` 调 `SqlStatementGuard.classifyAndAudit` 加 `IngestionContext`；不动 guard 分类规则 |
| Diagnostics | **N/A** — 本 spec 不动诊断 |
| MCP action schemas | **新增** — 6 个 ingestion-specific action，详见 §4 |
| Runtime agent prompts | **新增** — AGENTS.md 加 `## Data Ingestion` 段 + `data-ingestion` skill SKILL.md |
| 新增 dialect 兼容点 | **明确**：Day-1 支持 mysql/postgresql/h2/sqlite 写入；其余 15 个 first-class kind（mariadb / oracle / sqlserver / duckdb / clickhouse / apache_doris / starrocks / trino / presto / hive / tidb / oceanbase / dameng / kingbase / gaussdb）抛 `IngestionDialectUnsupportedException` —— 在 `DATA_SOURCE_TYPE_COMPATIBILITY.md` 新增「Ingestion DDL Adapter」一节，明确每个 kind 当前状态（Day-1 supported / Day-1 unsupported / Day-2+ follow-up） |

## 11. Phase 切分（写入 child plan）

| Phase | 范围 | 验收 |
|-------|------|------|
| **P1 — DB + Domain + Credential** | V20 migration（含 file_artifact CHECK rebuild）；domain records / enums / 8 DtEvents；JdbcIngestionJobRepository / JdbcIngestionCredentialRepository；IngestionCredentialService + SecretVault hookup；Settings Credentials sub-page（CRUD UI） | `mvn clean verify` 全绿；新 FlywayMigrationIT 通过；Credentials UI 手工 smoke 4 scheme 创建删除；vitest 通过 |
| **P2 — HTTP Fetcher + Payload Artifact** | IngestionPayloadFetcher（RestClient + 3 种分页 + 落盘 + register external file_artifact）；`datatalk_http_request` action；SSRF deny list；6 MCP tools 占位 stub | WireMock FakeWebServer 集成测试：单次 / page / offset / cursor 三型 + Bearer/Basic/API Key + 失败回执 + SSRF 拒绝 + 500MB 上限 |
| **P3 — Schema Inferrer + Mapping** | IngestionSchemaInferrer（JSON/JSONL/CSV/HTML 4 format）；类型推断算法；`datatalk_infer_ingestion_schema` action；MappingColumn / IngestionMapping records | 单元测试覆盖 4 format × 多类型 sample；前 100 行 sample 端到端 |
| **P4 — Frontend Tab + Mapping Editor** | `ingestion_job` / `ingestion_library` Tab 注册；6 子组件（phase 路由 + mapping-editor + payload-preview-table + ddl-preview 等）；Zustand store + 3 个 TanStack Query hooks；REST endpoints；i18n keys；5 态 token 映射全列 | `cd client && npx tsc --noEmit`；vitest 通过；手工 Tauri 跑通 happy path Tab phase 切换 |
| **P5 — DDL Adapter + Executor + L2** | IngestionDdlAdapter 接口 + 4 实现（mysql/postgresql/h2/sqlite）；IngestionExecutor（CREATE + INSERT batch）；IngestionConfirmedTokenStore；`datatalk_create_ingestion_table` / `datatalk_ingest_payload` action；SqlStatementGuard 集成 + IngestionContext；SSE 进度事件链 | 4 dialect Testcontainers 集成测试：完整 fetch → infer → confirm → create → ingest happy path；token 校验失败拒绝；SqlStatementGuard 审计行写入断言 |
| **P6 — Skill Bundle + AGENTS.md + Verification** | skills-src/data-ingestion SKILL.md + recipes；maven-assembly descriptor；`ensureDataIngestionSkill`；AGENTS.md 模板增量；`AgentsTemplateContractTest`；端到端 Playwright spec | 启动 + 第二次启动幂等；版本号升级测试；AGENTS 契约测试；Playwright happy path 在 mock backend 下通过 |

Phase 1-6 **串行**：每 Phase 完成 `mvn clean verify` + (P4 起) `cd client && npx tsc --noEmit` + vitest 全绿才进下一 Phase。Phase 间不并发（后一 Phase 重度依赖前一 Phase 产物）。

**P4 在 P5 之前的设计理由**：P4 前端依赖的是 P3 schema-inference 的返回结构（mapping editor 渲染所需），不依赖 P5 的真实 CREATE/INSERT 执行。Tab 内 `writing` / `completed` 等后期 phase 视图可以靠 mock 事件渲染，P4 完成后可以单独跑 mapping editor + DDL preview + 双通道 phase stepper 的视觉与交互闭环；P5 接好后真实执行就联通。若由不同人前后端并行，前端开发期间用 WireMock backend stub 提供 `/api/ingestion/jobs/<id>` 与假 SSE 事件流即可。

## 12. Acceptance Checklist

- [ ] V20 migration 升级既存 SQLite 库不丢数据，rollback helper SQL 存在
- [ ] 4 个 dialect adapter 各自 fetch → write 端到端 happy path 通过
- [ ] 4 种 auth scheme（None / Bearer / API Key header / API Key query / Basic）端到端通过（mock 服务）
- [ ] 3 种分页模式（page / offset / cursor）端到端通过
- [ ] 4 种 payload format（JSON / JSONL / CSV / HTML）schema 推断 + 写入通过
- [ ] SSRF deny list 拦截 `localhost` / `127.0.0.1` / `169.254.169.254`
- [ ] payload 500MB 上限拦截
- [ ] SqlStatementGuard 写 audit 行有 ingestion 上下文标记
- [ ] IngestionConfirmedToken 过期 / 重用 / 串 jobId / mappingHash 篡改 一律拒绝
- [ ] `dialect_unsupported` 对 15 个 first-class kind 全部生效，i18n 已补
- [ ] Tab 在 session 切换后状态不丢（Stage 全局性验证）
- [ ] Files Library 显示 `ingestion_payload` kind 行，BUG-0009 dashboard kind 不退化
- [ ] `data-ingestion` skill 启动期解压幂等 + 版本升级生效
- [ ] AGENTS.md 模板契约测试通过
- [ ] 控件五态 token 映射在 vitest 快照与手工 Tauri 验证一致
- [ ] `cd server && mvn clean verify` 与 `cd client && npx tsc --noEmit` 全绿
- [ ] `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 新增「Ingestion DDL Adapter」节，列出当前 19 个 first-class kind 在 ingestion 路径的支持状态（4 个 Day-1 supported + 15 个 Day-1 unsupported）

## 13. Documentation Housekeeping（plan 完成时）

- 本 spec 已在 [`docs/product-specs/index.md`](./index.md) §8 注册
- 后续 child plan 注册到 [`docs/exec-plans/index.md`](../exec-plans/index.md) Active
- Plan 完成时按 CLAUDE.md `Post-Execution Document Housekeeping` 规则归档 + 反向更新 [`CLAUDE.md`](../../CLAUDE.md) / [`ARCHITECTURE.md`](../../ARCHITECTURE.md) / [`docs/generated/db-schema.md`](../generated/db-schema.md)
- 若 Day-2 启动平台特定 skill child plan，更新 §3.12 阶段标记从「三期 placeholder」→ 「三期 active」

## 14. References

- [产品总设计 §3.12 外部数据接入与自动采集（Skill 驱动）](./index.md)
- [Task 10 Roadmap entry](../exec-plans/2026-04-25-next-implementation-roadmap-plan.md#task-10-external-data-ingestion-via-skills-phase-3-placeholder)
- [Task 5 L3 Risk Flow（SqlStatementGuard）](./index.md)
- [Task 6 Cross-Session Workbench Tabs](./2026-04-27-cross-session-workbench-tabs-plan.md) — `tab-type-registry`、workspace scope 模式
- [Task 11 OpenCode Workdir & Artifact System](./2026-04-29-opencode-workdir-and-artifact-system-design.md) — file_artifact、External-Registered 模式
- [Dashboard ↔ File Artifact Integration Design](./2026-05-09-dashboard-file-artifact-integration-design.md) — V18 CHECK rebuild + external 列同形态模板
- [bezel — Premium Industrial Dashboard Skill 设计](./2026-05-11-bezel-skill-design.md) — Skill 包打包路径模板（maven-assembly + ensureBezelSkill）
- [`client/DESIGN.md`](../../client/DESIGN.md) — UI tokens / 五态控件契约
- [`docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`](../DATA_SOURCE_TYPE_COMPATIBILITY.md) — Data source 兼容 gate
