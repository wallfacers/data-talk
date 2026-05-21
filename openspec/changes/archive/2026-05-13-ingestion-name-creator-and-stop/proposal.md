## Why

数据采集库当前每行只能用 `Job <id前8位>` 这种机器 ID 识别任务，库列表缺乏语义名与创建者归因，AI 与人工创建路径无法区分。Cancel 接口实际只翻 DB 状态字段，不中断正在运行的 fetch / write 线程，且 `writing` 阶段没有任何前端入口；进程崩溃后任务永远停留在 `writing` / `fetching`，状态与现实不一致；目标表残留半数已 commit 的行无清理路径，导致用户业务库长期被"半成品数据"污染。

## What Changes

- 在 `ingestion_job` 表新增 `name`（NOT NULL，AI 必传，1–80 chars）、`created_by_kind`（'ai' / 'user'）、`created_by_session_id`、`created_by_label`（写入时物化的显示文字，与 session 解耦）、`heartbeat_at`，并通过 V23 migration 一次性引入
- `datatalk_http_request` MCP 工具 inputSchema 加 `name`（required），AGENTS.md `Data Ingestion` 章节强约束 AI 必须提供有意义的 name
- `datatalk_get_ingestion_job` / `datatalk_list_ingestion_jobs` outputSchema 增加 `name`、`createdBy: { kind, sessionId, label }`
- 库列表（`ingestion-library-tab`）新增 **Name** 列与 **Creator** 列；Tab 标题改用 `name`；Creator 单元格可点击跳回原会话
- 新增 `POST /api/ingestion/jobs/{id}/stop`（带 `?force=true` 选项），实现**真停止**：进程内 `IngestionRunRegistry` 记录活任务句柄；stop 通过中断 + cancel-flag 让 `executeBatchInsert` 在 batch 边界退出；运行期主动 stop 时 DROP 目标表（这次新建的）并删 payload artifact，做到目标库零残留
- 库列表每行 hover 时显示 ⏹ Stop（非终态可见）；详情 Tab header 加常驻 Stop 按钮覆盖所有非终态阶段，并支持 30s 后升级为 force-stop
- **状态最终一致**：启动时 sweeper 将所有遗留 `pending`/`fetching`/`mapping`/`confirmed`/`writing` 状态翻 `failed`（errorMessage = "server restarted while running"）；运行期 `@Scheduled` 每 60s 巡检，超过 5 min 无 heartbeat 的活任务翻 `failed`。崩溃恢复**不自动 DROP** 残留表，errorMessage 提示用户手动清理
- IngestionDdlAdapter 新增 `generateDropTable(schema, table)` 接口方法，4 个方言（mysql/pg/h2/sqlite）实现 `DROP TABLE IF EXISTS`

## Capabilities

### New Capabilities
- `ingestion-lifecycle`: 采集任务的元数据（name、creator 归因）、真停止机制、僵尸救援、目标表清理策略与状态最终一致性保证

### Modified Capabilities
- `ingestion-ui-e2e-testing`: 新增 stop / 创建者展示 / name 列的 mock 场景，扩展 `mockJob()` 类型契约以覆盖新字段

## Impact

- **DB**：新增 V23 migration（`ingestion_job` 加 5 列）；rebuild status CHECK（保持现有枚举不变）
- **Domain**：`IngestionJob` record 加 5 个字段；`IngestionJobRepository` 加 `updateHeartbeat`、`listByStatusIn`；新增 `CreatorKind` 枚举
- **Application**：新增 `IngestionRunRegistry`（进程内 `ConcurrentHashMap<jobId, RunHandle>`）、`IngestionStopService`、`IngestionStartupSweeper`（`@PostConstruct` 一次性翻状态）、`IngestionHeartbeatSweeper`（`@Scheduled` 60s）；`IngestionExecutor.executeBatchInsert` 改为感知 cancel-flag 并 tick heartbeat；`IngestionPayloadFetcher` 落库时写入 creator 三列与 name
- **Adapter**：`HttpRequestActionHandler.inputSchema` 加 `name` required；`GetIngestionJobActionHandler` / `ListIngestionJobsActionHandler` outputSchema 透传 name + createdBy；新增 `POST /jobs/{id}/stop`；`AGENTS.md` Data Ingestion 段加强约束；新增 `SessionLookup`（轻量）用于写入时物化 label
- **Frontend**：`ingestion-library-tab.tsx` 加 Name / Creator 列与 Stop 按钮；`ingestion-job-tab.tsx` header 加 Stop；`api/ingestion-api.ts` 加 `stopIngestionJob(id, force?)` 与 `IngestionJobView.name` / `.createdBy`；`tab-type-registry` 标题渲染改 name；新增 i18n keys
- **MCP schema**：3 个 handler 的 schema；不动 `ui_find` / `ui_read` / `ui_exec` 代码（自动受益）
- **Tests**：JUnit + WireMock 覆盖 stop 真中断、startup sweeper、heartbeat sweeper、DROP TABLE；Playwright E2E 覆盖 name 必填、creator 展示、stop UI、force-stop 升级、僵尸恢复

## Design Inputs

- `client/DESIGN.md` § components.table：sticky header / 行高 `h-8` / 选中态 `bg-interaction-selected` / hover `bg-muted/50` —— 已在现有库列表使用，新加列须保持
- `client/DESIGN.md` § density.compact："sidebar, table, toolbar" 走紧凑密度，新增 Name 列 max-width 与 Creator 单元格图标尺寸 `size-3.5` 沿用现有约定
- `client/DESIGN.md` § semantic.status：Stop 按钮 hover `bg-destructive/10 hover:text-destructive` 与 Delete 同源
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`：`DROP TABLE IF EXISTS` 在 4 个 Day-1 方言（mysql / pg / h2 / sqlite）均原生支持，无方言陷阱；扩展型方言（doris/clickhouse/oracle/sqlserver…）当前不在 ingestion 支持范围（受 `INGESTION_DIALECT_UNSUPPORTED` 阻断），N/A
- `docs/I18N.md`：新增 i18n 走单花括号 `{...}` 模板（避免 BUG-0014 同款双花括号陷阱）

## Risks / Known Issues

- 当前 `docs/bugs/` 中 ingestion 相关 BUG 全部 `fixed`，无开放 BUG 与本次改动重叠
- 残留风险：崩溃 / 心跳超时分支**不自动 DROP** 目标表，可能在用户业务库留下半数行；errorMessage 必须清晰提示用户手动清理，否则会被误读为"再次运行就会去重续写"
- `created_by_label` 是写入时物化文字，会话之后改名不会同步，属预期行为（避免删除会话后展示空白）
- `IngestionRunRegistry` 是单进程内存，未来若引入多节点部署需改为分布式锁 / DB heartbeat 拉取 owner——本次不涉及，但在 design.md 留扩展点
