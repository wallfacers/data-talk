# Diagnostics & Mutation Actions Design

**日期**: 2026-04-29
**状态**: Completed
**关联**: 接续 [Intelligent Operations Design](./2026-04-27-intelligent-operations-design.md) 中预留的 lock / pool / space stub 实现

## 0. Compatibility Gate Acknowledgment

依据 [CLAUDE.md "Data Source Type Compatibility Gate"](../../CLAUDE.md) 与 [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)，本 spec 触及"diagnostics"与"MCP action schema"两个 gate 范围（gate doc §When This Gate Applies 第 3、4、6 项）。完成 checklist 标注：

| Gate Checklist 段 | 适用性 | 说明 |
|---|---|---|
| Domain Layer | **适用** | 改 `DiagnosticCapability` 枚举值；改 / 新增 5 个 record（`LockReport` / `PoolReport` / `SpaceReport` / `DiagnosticRecommendation` / `TerminateSessionResult` / `OptimizeTableResult`）；application 层 switch 同步 |
| Application Connection Layer | **N/A** | 本 spec 不动连接层（不加 kind、不改 JdbcUrlBuilder、不改 DTO） |
| Persistence And Metadata DB | **N/A** | 不加迁移、不改 `connections` 表 |
| JDBC Driver And Runtime Packaging | **N/A** | 不引新驱动；MySQL / PG / H2 / Oracle 驱动维持现状（Oracle 不真用） |
| Dynamic SQL Execution Repository | **N/A** | mutation SQL（KILL / OPTIMIZE / VACUUM）走 provider 内部模板方法独立通路，不污染 `DynamicSqlExecutionRepository` 与 `execute_sql` 只读契约 |
| Result Values, Analytics, Visualization, And Reports | **N/A** | 诊断 / mutation 输出走自己的 record 序列化，不进 chart / artifact 路径 |
| SQL Statement Splitting | **N/A** | 单语句 mutation，不拆分 |
| SQL Risk Analysis And Guards | **新风险类**待标注 | mutation action 走 `RiskLevel.L2` + confirmable 流程，**与现有 SqlExecutionRisk 体系正交**（不经 Calcite 风险分析、不进 SqlStatementGuard）。详见 §10 |
| Schema Discovery And Target Resolution | **N/A** | session data context 既有，不改 |
| Adapter Actions And Ontology | **适用** | 新增 / 重写 5 个 Action；i18n 中英文同步 |
| Diagnostics Compatibility Checklist | **适用（核心）** | 详见各节 |
| MCP And Runtime Agent Prompt Checklist | **适用** | AGENTS.md 改动 + AgentPromptContractTest 新断言；MCP 工具命名严格遵循 `datatalk_*` (OpenCode) ↔ `datatalk.*` (内部 actionId) 映射 |
| UI Object And `ui_xxx` Compatibility Checklist | **N/A** | 无前端 / UI 改动；mutation 走 AI 编排的二阶段 confirm，不需要特化 UI |
| Frontend Compatibility Checklist | **N/A** | 同上 |
| Documentation Checklist | **适用** | 本 spec、对应 exec-plan、AGENTS.md、`~/.data-talk/opencode/datatalk-tools-test-report.md` 都需更新；不触发 `docs/generated/db-schema.md` |
| Minimum Test Matrix | **适用** | 详见 §14 |

`OracleDiagnosticsProvider` 继续使用 `supportedDriverTypes() = Set.of("oracle")` 但 `supportedCapabilities()` 不加新值——这与 gate doc §47 "Oracle: Stub only" 现状一致，**不更新 §47 表**（本 spec 没有把 Oracle 推进任何阶段）。

`PostgreSqlDiagnosticsProvider.supportedDriverTypes()` 必须含 `"postgres"` 与 `"postgresql"` 两个 alias，与 gate doc §47 既有规范一致。

## 1. 目标

将三个 stub 诊断工具（`datatalk_lock_info` / `datatalk_pool_status` / `datatalk_table_space`）从硬编码 `unsupported` 升级为跨引擎真实实现，并新增两个 confirmable mutation action（`datatalk_terminate_session` / `datatalk_optimize_table`）形成诊断→建议→执行的完整闭环。

**引擎覆盖**:
- **MySQL / PostgreSQL**（`postgres` / `postgresql` 两个 alias 都路由）: 完整真实实现（所有 5 个能力）
- **H2**: 按能力部分实现 / 部分 unsupported，逐个显式声明（**技术限制**——嵌入式无服务端连接视图、无锁等待视图、无表空间字段，不可绕过）
- **Oracle**: 本期保持 unsupported stub（**架构限制**——连接栈未闭环：`ConnectionKind` 无 oracle 常量、`JdbcUrlBuilder` 不支持、无 ojdbc 驱动依赖）。依据 [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md) 第 8 条 Hard Rule "Do not add prompt-only support"。OracleDiagnosticsProvider 的新方法全部返回 `Unsupported("Oracle diagnostics not yet available")`，`supportedCapabilities()` 不含任何新能力

**Oracle Follow-up Plan**（独立后续 plan，不在本 spec 范围）: 当 Oracle 连接栈完成（ConnectionKind / JdbcUrlBuilder / ojdbc 依赖 / 前端连接表单 / schema 发现 / SQL 执行）后，**只需修改 OracleDiagnosticsProvider** —— 实现 5 个新方法 + `supportedCapabilities()` 加新值。**无需修改** domain records / DiagnosticsService / 任何 Action / AGENTS.md / i18n keys（i18n key 已预留 `diagnostics.*.unsupported.oracle`，启用时仅替换为成功路径文案）。

**技术限制 vs 架构限制的区别**: H2 的 unsupported 是物理事实（嵌入式 DB 没有这些视图）；Oracle 的 unsupported 是工程债（视图存在，只是连接栈没补齐）。两者都用 `DiagnosticResult.unsupported(...)`，但 reason 文案区分清楚（"H2 does not expose ..." vs "Oracle diagnostics not yet available"），让 AI 给用户的回应也能体现差别。

## 2. 架构与组件

模块边界不变：domain ← application ← infrastructure ← adapter。

### 改动地图

| 层 | 改动 | 性质 |
|---|---|---|
| domain | `DiagnosticCapability` 重命名 `CONNECTION_POOL` → `POOL_STATUS`，新增 `TERMINATE_SESSION` / `OPTIMIZE_TABLE`；`LockReport` / `PoolReport` / `SpaceReport` 字段扩展（含 `recommendations` 字段）；新增 `DiagnosticRecommendation` / `TerminateSessionResult` / `OptimizeTableResult` | 扩展 sealed model |
| application | `DiagnosticsProvider` 接口方法重命名 + 签名扩展 + 新增；`DiagnosticsService` 加 5 个 public 方法 + recommendations 生成逻辑（注入 `DiagnosticsThresholdProperties`）；新增 `DiagnosticsThresholdProperties` (`@ConfigurationProperties("datatalk.diagnostics")`)；`application.yml` 加默认阈值 | 接口扩展 + service 层补齐 + 阈值参数化 |
| infrastructure | 新增 `AbstractDiagnosticsProvider` 抽象基类，提供模板方法 `openConnection` / `queryForList` / `executeUpdate`（**仅供新方法使用，不重构 explain/indexHints**）；MySQL/PG Provider 继承基类并实现真实 SQL；Oracle 保持 unsupported（`supportedCapabilities()` 不含新能力）；H2 按能力部分实现 / 部分 unsupported | 真正实现（MySQL/PG） + 可测性基类 |
| adapter | 重写 3 个诊断 Action 调用 service；新增 2 个 confirmable mutation Action（RiskLevel.L2）；AGENTS.md 更新；i18n 键添加（中英双套，约 24 个 key，详见 §11）；输入校验（SQL 注入防护，正则白名单） | wire-up + 新 mutation action + i18n |

### 关键边界

1. **mutation 与诊断解耦**: `TerminateSessionConfirmableAction` 不依赖 `LockInfoAction`，AI 可独立调用。诊断 action 只是建议它。
2. **跨引擎差异隔离**: 所有引擎特定的 SQL 封装在各 provider 内部，不污染 service 与 adapter 层。
3. **能力声明**: 所有能力通过 `DiagnosticCapability` 表达，service 调用前检查 `supportedCapabilities().contains(...)`。
4. **Recommendations 归属**: Reports 的 `recommendations` 字段由 Service 层填充。Provider 返回时该字段为空列表。Service 根据 provider 返回的原始数据应用阈值规则，构造 `DiagnosticRecommendation` 并重建 report 实例。Provider 不引用 action ID，不违反依赖方向。
5. **Oracle 本期边界 + Follow-up 隔离**: `OracleDiagnosticsProvider` 的 `supportedCapabilities()` 仅含 `EXPLAIN` + `INDEX_HINTS`（维持现状），新 5 个能力全部 unsupported。本期**完全不动** `ConnectionKind` / `JdbcUrlBuilder` / pom.xml / 前端连接表单。当未来独立 plan 补齐 Oracle 连接栈时，启用 Oracle 诊断只需在 `OracleDiagnosticsProvider` 实现 5 个方法 + `supportedCapabilities()` 加新值——**Service / Domain / Adapter / AGENTS.md / i18n key 全部不动**。这一隔离由"Provider 是 SPI、capabilities 是声明式"的设计天然保证。

## 3. Data Model

### DiagnosticCapability 枚举

```java
public enum DiagnosticCapability {
    EXPLAIN, INDEX_HINTS, LOCK_INFO,
    POOL_STATUS,           // 重命名自 CONNECTION_POOL
    TABLE_SPACE,
    TERMINATE_SESSION,     // 新增
    OPTIMIZE_TABLE         // 新增
}
```

### LockReport

```java
public record LockReport(
    List<LockEntry> blockingChain,                               // 字段改名 locks → blockingChain
    List<DiagnosticRecommendation> recommendations               // Service 层填充
) {
    public record LockEntry(
        String table,           // 锁所在表，可空（metadata lock 时）
        String lockType,        // SHARED | EXCLUSIVE | INTENT_SHARED | INTENT_EXCLUSIVE | METADATA | OTHER
        String holderId,        // session/pid/transaction 标识，按引擎语义
        String waiterId,
        Long   waitMillis,      // 等待时长 ms，nullable
        String holderSql,       // holder 当前 SQL，截断 200 字符，nullable
        String waiterSql        // waiter 当前 SQL，截断 200 字符，nullable
    ) {}
}
```

### PoolReport（服务端视角重写）

```java
public record PoolReport(
    String  scope,                // 固定 "server"，预留未来 "client"
    Integer activeConnections,    // 服务端活动会话数
    Integer idleConnections,      // 非 running 会话数
    Integer maxConnections,       // 服务端 max_connections / sessions 上限
    Integer threadsRunning,       // 真正在跑 query 的线程，nullable
    Integer waitingConnections,   // 在等锁/等 IO 的会话数，nullable
    String  identifier,           // 形如 "host:port"
    List<DiagnosticRecommendation> recommendations               // Service 层填充
) {}
```

### SpaceReport

```java
public record SpaceReport(
    List<TableSpaceEntry> tables,
    List<DiagnosticRecommendation> recommendations               // Service 层填充
) {
    public record TableSpaceEntry(
        String table,
        String schemaName,        // 新增：跨 schema 时区分
        long   rowCount,
        long   dataSizeBytes,
        long   indexSizeBytes,
        Long   freeSpaceBytes     // 可回收空间，nullable
    ) {}
}
```

### DiagnosticRecommendation

```java
public record DiagnosticRecommendation(
    String severity,                            // info | warning | critical
    String summary,                             // 已 i18n 的人话
    String suggestedActionId,                   // 内部 action ID，如 "datatalk.terminate_session"，可空
    String suggestedToolName,                   // MCP 工具名，如 "datatalk_terminate_session"，可空
    Map<String,Object> suggestedActionArgs,     // 直接喂给目标 action 的 input
    String suggestedSql                         // 兜底原始 SQL，可空
) {}
```

**命名规范**:
- `suggestedActionId`: 内部 `datatalk.*` 格式（与 `@DataTalkAction(id=...)` 一致）
- `suggestedToolName`: MCP 运行时 `datatalk_*` 格式（与 AGENTS.md 文档名一致）
- 两者并存，AI 可按需选用

由 `DiagnosticsService` 生成。Provider 返回 report 时 `recommendations` 为空列表，Service 根据阈值重建 report 并填入 recommendations。

### TerminateSessionPreview（Phase 1 数据载体）

```java
public record TerminateSessionPreview(
    String engine,                  // "mysql" | "postgresql"
    String sessionId,               // 目标 session id（已 echo）
    String willRunSql,              // 即将执行的 SQL 文本，例如 "KILL 12345"
    String currentSql               // 目标 session 当前正在跑的 SQL，nullable（已自然结束 / 无 SQL 时）
) {}
```

### TerminateSessionResult（Phase 2 数据载体）

```java
public record TerminateSessionResult(
    boolean ok,                     // true=已 kill；false=session 不存在（MySQL 1094 / PG false 路径，message 解释）
    String sessionId,
    String message                  // i18n 解析后的人话
) {}
```

### OptimizeTablePreview（Phase 1 数据载体）

```java
public record OptimizeTablePreview(
    String engine,                  // "mysql" | "postgresql"
    String table,
    String schemaName,
    String willRunSql,              // 例如 "OPTIMIZE TABLE `test`.`users`"
    Long currentDataFree,           // MySQL 用：执行前 data_free，便于 Phase 2 算 reclaimedBytes 差值；PG 时为 null
    Long currentTotalSize,          // PG 用：执行前 pg_total_relation_size；MySQL 时为 null
    List<DiagnosticRecommendation> recommendations  // 强制含一条 severity=critical 的锁警告
) {}
```

### OptimizeTableResult（Phase 2 数据载体）

```java
public record OptimizeTableResult(
    boolean ok,
    String table,
    String schemaName,
    Long durationMs,                // 执行耗时
    Long reclaimedBytes,            // 用 preview 阶段的 currentDataFree / currentTotalSize 与执行后值算差，nullable
    String message
) {}
```

**数据载体契约**:
- Preview record 由 Action 层在 Phase 1 调用 Provider 的 preview 方法获得；Action 拿 `willRunSql` + 业务 ID 字段算 `confirmation_token`（见 §8），并把 Preview record 嵌套到 Phase 1 输出 JSON 的 `preview` 字段。
- `OptimizeTablePreview.recommendations` 直接成为 Phase 1 输出 JSON 的顶层 `recommendations` 数组（不嵌在 `preview` 内），与诊断工具的 `recommendations` 同型，便于 AI 复用解析逻辑。
- Phase 2 入参除了业务字段还有 `confirmationToken`；Action 再调用 Provider preview 方法重算 token，匹配后才调 Provider 的 execute 方法。
- Phase 2 的 `OptimizeTableResult.reclaimedBytes` 由 Provider 在 execute 内部用 Phase 1 拿到的 `currentDataFree` / `currentTotalSize`（重新查一次执行前快照）减去执行后值得到。

## 4. DiagnosticsProvider 接口

```java
public interface DiagnosticsProvider {
    Set<String> supportedDriverTypes();
    Set<DiagnosticCapability> supportedCapabilities();

    // 既有（不动）
    DiagnosticResult<ExplainPlan>               explain(String sql, ConnectionRecord conn, String decryptedPassword, String database, String schema);
    DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan, ConnectionRecord conn, String decryptedPassword);

    // 既有（保留签名，改实现）— lockInfo / tableSpaceInfo 签名扩展
    DiagnosticResult<LockReport>                lockInfo(ConnectionRecord conn, String decryptedPassword, String database);
    DiagnosticResult<SpaceReport>               tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables);

    // 重命名：connectionPoolInfo → poolStatus
    DiagnosticResult<PoolReport>                poolStatus(ConnectionRecord conn, String decryptedPassword);

    // 新增：mutation Phase 1（preview）
    DiagnosticResult<TerminateSessionPreview>   terminateSessionPreview(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database);
    DiagnosticResult<OptimizeTablePreview>      optimizeTablePreview(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database);

    // 新增：mutation Phase 2（execute）
    DiagnosticResult<TerminateSessionResult>    terminateSession(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database);
    DiagnosticResult<OptimizeTableResult>       optimizeTable(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database);
}
```

**变更说明**:
- `connectionPoolInfo()` → `poolStatus()`: 方法重命名，返回类型从旧 `PoolReport(int, int, int, String)` 改为新 `PoolReport(scope, activeConnections, ..., recommendations)`
- `tableSpaceInfo()`: 签名扩展，新增 `List<String> tables` 参数（null/empty 表示全库扫描，上限 200）
- `lockInfo()`: 签名不变，返回类型从旧 `LockReport(List<LockEntry>)` 改为新 `LockReport(blockingChain, recommendations)`
- `terminateSessionPreview()` / `terminateSession()` / `optimizeTablePreview()` / `optimizeTable()`: 全新增；preview 与 execute 一一对应，分别承担 Phase 1（含 read-only 元信息查询 + 文案构造）与 Phase 2（实际 mutation）

**为什么 preview/execute 分两个方法而非单方法 + dryRun 标志位**:
1. preview 与 execute 的副作用边界清晰（preview 严格只读）；类型系统区分 `TerminateSessionPreview` 和 `TerminateSessionResult` 让 Action 层无法误把 preview 当 execute 结果序列化
2. preview 失败语义（如目标 session 不存在）不会"污染" execute 路径；execute 也不需要再判 dryRun 分支
3. 测试可以独立 mock 两个方法的返回，避免共享 dryRun 测试矩阵

**统一返回类型决策**: 所有 provider 方法（含 4 个 mutation 方法）统一走 `DiagnosticResult<T>` 三态。H2 的 `terminateSessionPreview` / `terminateSession` 都返回 `Unsupported("...")`，**不允许 preview 成功而 execute 失败**——Provider 层契约：若任一阶段 unsupported，preview 阶段就报，Action 层不应再调 execute。Action 层做三态 → JSON 转换。

**Provider `recommendations` 约定**: Provider 返回的 `LockReport` / `PoolReport` / `SpaceReport` 的 `recommendations` 字段必须为空列表（`List.of()`）。Service 层负责填充。

## 5. DiagnosticsService 新方法

```java
// 诊断（3 个）
public DiagnosticResult<LockReport>                lockInfo(String sessionId);
public DiagnosticResult<PoolReport>                poolStatus(String sessionId);
public DiagnosticResult<SpaceReport>               tableSpaceInfo(String sessionId, List<String> tables);

// Mutation Phase 1 — preview（2 个）
public DiagnosticResult<TerminateSessionPreview>   terminateSessionPreview(String sessionId, String targetSessionId);
public DiagnosticResult<OptimizeTablePreview>      optimizeTablePreview(String sessionId, String table, String schemaName);

// Mutation Phase 2 — execute（2 个）
public DiagnosticResult<TerminateSessionResult>    terminateSession(String sessionId, String targetSessionId);
public DiagnosticResult<OptimizeTableResult>       optimizeTable(String sessionId, String table, String schemaName);
```

每个方法内部流程：
1. `resolveContext(sessionId)` → 获取 ConnectionRecord + database + schema
2. `requireProvider(conn.kind())` → 获取 provider
3. 检查 `supportedCapabilities().contains(...)` → 不满足则返回 Unsupported
4. 解密密码
5. 调用 provider 方法
6. **诊断方法**: provider 返回裸 report（`recommendations` 为空列表）后，service 根据 **阈值规则** 生成 `DiagnosticRecommendation` 列表，**重建 report 实例**（record 不可变，构造新实例替换空 recommendations）

### Recommendations 生成规则（Service 层，参数化阈值）

**v1 通过 `@ConfigurationProperties` 暴露阈值**（默认值不变，运维可在 `application.yml` 调整，无需重发版）:

```yaml
datatalk:
  diagnostics:
    lock:
      long-wait-ms: 5000              # 阻塞链中等待时长 > 此值触发 terminate 建议
    pool:
      warn-ratio: 0.80                # active/max 比例触发 warning
      critical-ratio: 0.95            # active/max 比例触发 critical
    space:
      reclaim-ratio: 0.30             # freeSpace / (data+index) 比例触发 optimize 建议
      min-data-size-bytes: 104857600  # 100MB；表数据小于此值不建议 optimize（投入产出比低）
```

对应 Java 配置类：

```java
@ConfigurationProperties("datatalk.diagnostics")
public record DiagnosticsThresholdProperties(
    Lock lock,
    Pool pool,
    Space space
) {
    public record Lock(long longWaitMs)              { /* default 5000 */ }
    public record Pool(double warnRatio, double criticalRatio)   { /* defaults 0.80 / 0.95 */ }
    public record Space(double reclaimRatio, long minDataSizeBytes) { /* defaults 0.30 / 100MB */ }
}
```

**触发逻辑**（Service 层，依赖注入 `DiagnosticsThresholdProperties`）:

| Recommendation | 触发条件 | severity | suggestedActionId | suggestedToolName | suggestedActionArgs |
|---|---|---|---|---|---|
| terminate holder | `waitMillis > lock.longWaitMs` | `warning` | `datatalk.terminate_session` | `datatalk_terminate_session` | `{sessionId: holderId}` |
| pool warning | `active / max > pool.warnRatio` 且 `≤ pool.criticalRatio` | `warning` | null | null | `{}` |
| pool critical | `active / max > pool.criticalRatio` | `critical` | null | null | `{}` |
| optimize table | `freeSpace / (data + index) > space.reclaimRatio` 且 `dataSize > space.minDataSizeBytes` | `info` | `datatalk.optimize_table` | `datatalk_optimize_table` | `{table, schemaName}` |

阈值常量**禁止散落在 service 代码里**——所有引用必须经 `DiagnosticsThresholdProperties` getter。

## 6. Per-Engine SQL

### 引擎覆盖矩阵

| 能力 | MySQL | PostgreSQL | Oracle | H2 |
|------|-------|------------|--------|-----|
| lock_info | 实现 | 实现 | **unsupported**（连接栈未闭环） | **unsupported**（无锁视图） |
| pool_status | 实现 | 实现 | **unsupported** | **unsupported**（嵌入式无 server 统计） |
| table_space | 实现 | 实现 | **unsupported** | partial（表名 + rowCount，大小为 0） |
| terminate_session | 实现 | 实现 | **unsupported** | **unsupported** |
| optimize_table | 实现 | 实现 | **unsupported** | **unsupported** |

### lock_info — 阻塞链

| 引擎 | 实现 | 备注 |
|------|------|------|
| MySQL | `performance_schema.data_lock_waits` JOIN `data_locks` JOIN `performance_schema.threads`（拿 processlist_id + SQL）；`waitMillis` = `NOW() - trx_wait_started` from `information_schema.innodb_trx`；需要 `performance_schema=ON`（默认开） | MySQL 8+ |
| PostgreSQL | `pg_locks` 中 `granted=false` JOIN `pg_stat_activity` 拿 PID/SQL；`pg_blocking_pids(pid)` 反查 holder；`waitMillis` = `now() - state_change` when `state='active'` | 需要 `track_activities=on`（默认开） |
| Oracle | 本期 `unsupported("Oracle diagnostics not yet available")` | 连接栈未闭环：`ConnectionKind` 无 oracle，`JdbcUrlBuilder` 不支持 |
| H2 | `unsupported("H2 does not expose lock waits")` | 嵌入式真没有 |

### pool_status — 服务端连接

| 引擎 | 实现 |
|------|------|
| MySQL | `SHOW STATUS LIKE 'Threads_connected'` → active + idle（idle = Threads_connected - Threads_running）；`SHOW STATUS LIKE 'Threads_running'` → threadsRunning；`SHOW VARIABLES LIKE 'max_connections'` → maxConnections；`performance_schema.threads` 中 `processlist_state LIKE 'Waiting%'` 计数 → waitingConnections；identifier = host:port from ConnectionRecord |
| PostgreSQL | `SELECT count(*) FILTER (WHERE state='active') AS active, count(*) FILTER (WHERE state='idle') AS idle, count(*) FILTER (WHERE wait_event IS NOT NULL AND wait_event_type='Lock') AS waiting FROM pg_stat_activity WHERE backend_type='client backend'`；`SHOW max_connections`；threadsRunning = active |
| Oracle | 本期 `unsupported("Oracle diagnostics not yet available")` |
| H2 | `unsupported("H2 embedded mode does not expose server connection stats")` |

### table_space — 表空间

| 引擎 | 实现 |
|------|------|
| MySQL | `INFORMATION_SCHEMA.TABLES WHERE table_schema=:db [AND table_name IN (:tables)]`：data_length → dataSizeBytes，index_length → indexSizeBytes，table_rows → rowCount，data_free → freeSpaceBytes，schemaName = table_schema。上限 200 张表 |
| PostgreSQL | `pg_class` JOIN `pg_namespace` JOIN `pg_stat_user_tables`：`pg_relation_size(oid)` / `pg_indexes_size(oid)` / `n_live_tup`；freeSpace 近似；schemaName = nspname |
| Oracle | 本期 `unsupported("Oracle diagnostics not yet available")` |
| H2 | `INFORMATION_SCHEMA.TABLES` 拿表名 + rowCount；dataSizeBytes / indexSizeBytes / freeSpaceBytes 全为 0/null |

### terminate_session — Mutation

| 引擎 | Preview SQL | Execute SQL | 自杀保护 |
|------|-------------|-------------|----------|
| MySQL | 从 `performance_schema.threads` 反查 `PROCESSLIST_ID` 对应的 `PROCESSLIST_INFO`（当前 SQL）；构造 `KILL <id>` 文本 | `KILL <id>`（plain statement，不走 prepared） | Preview 阶段查 `SELECT CONNECTION_ID()`，若 target == self → `Unsupported("self_termination_blocked")` |
| PostgreSQL | 从 `pg_stat_activity` 查 `query` WHERE `pid = :target`；构造 `SELECT pg_terminate_backend(<pid>)` | `SELECT pg_terminate_backend(<pid>)` | Preview 查 `SELECT pg_backend_pid()` |
| Oracle | 本期 `unsupported("Oracle diagnostics not yet available")` | — | — |
| H2 | `unsupported("H2 does not support session termination")` | — | — |

#### 边界 case — Execute 阶段的"目标 session 已不存在"

两个引擎对"想 kill 的 session 已经自然结束"的反馈不同，但都**不应该当 error 处理**——AI 看到 error 会以为系统坏了，实际只是用户慢了一拍：

| 引擎 | 现象 | Provider 处理 |
|------|------|----------------|
| MySQL | `KILL <id>` 抛 `SQLException`，errorCode = `1094` (`ER_NO_SUCH_THREAD`)，SQLState = `HY000` | Provider 捕获该 SQLException 后，匹配 errorCode == 1094 → 返回 `Ok(TerminateSessionResult{ok=false, sessionId, message=i18n("diagnostics.terminate.session_not_found")})`，**不返回 DiagnosticError** |
| PostgreSQL | `SELECT pg_terminate_backend(<pid>)` 正常返回 boolean = `false` | Provider 读取返回值，false → 返回 `Ok(TerminateSessionResult{ok=false, sessionId, message=i18n("diagnostics.terminate.session_not_found")})` |
| MySQL/PG | 其他 SQLException（权限不足、网络中断等） | 走通用 errorCode/SQLState 映射；权限不足回 `Unsupported`，其他回 `DiagnosticError` |

`TerminateSessionResult.ok` 字段就是为这种场景预留的——`ok=true` 表示 session 真的被 kill；`ok=false` 表示"调用成功但目标已不在了"。Action 层把整个 `TerminateSessionResult` 序列化为 JSON，AI 看 `ok=false` + message 自然知道发生了什么。

### optimize_table — Mutation

| 引擎 | Preview | Execute | reclaimedBytes | 安全警告（强制附 critical recommendation）|
|------|---------|---------|----------------|----------|
| MySQL | 构造 `OPTIMIZE TABLE <schema>.<table>` 文本；查执行前 `data_free` | `OPTIMIZE TABLE <schema>.<table>` | 执行后 `data_free` 差值 | i18n key `diagnostics.optimize.preview.lock_warning_mysql` |
| PostgreSQL | 构造 `VACUUM (FULL, VERBOSE) <schema>.<table>` 文本；查执行前 `pg_total_relation_size(oid)` | `VACUUM (FULL, VERBOSE) <schema>.<table>` | 执行前后 `pg_total_relation_size` 差值 | i18n key `diagnostics.optimize.preview.lock_warning_pg` |
| Oracle | 本期 `unsupported("Oracle diagnostics not yet available")` | — | — | — |
| H2 | `unsupported("H2 does not support reclaiming space; ANALYZE only updates stats")` | — | — | — |

**安全警告契约**: optimize_table 的 preview 阶段，Provider **必须**在返回的 `OptimizeTablePreview.recommendations` 中附一条 `severity=critical` 的 `DiagnosticRecommendation`，`summary` 字段值由 `Translator.get(...)` 用上表对应的 i18n key 解析得到（已注入 i18n 后的字符串）。这条 recommendation 由 **Provider 层注入**（不同于诊断工具的 recommendations 由 Service 层基于阈值注入）——因为它与"具体 SQL 形态"绑定（MySQL 用 OPTIMIZE TABLE 文案、PG 用 VACUUM FULL 文案），引擎差异封装在 Provider 内最自然。Service 层透传 preview 不二次处理 recommendations。Action 层把 `OptimizeTablePreview.recommendations` 直接展开为 Phase 1 输出 JSON 的顶层 `recommendations` 字段。

#### `schemaName` 跨引擎缺省策略

input 中 `schemaName` 为 null 时各引擎不同 fallback——**Provider 内部**决定，**Action / Service 层不感知差异**：

| 引擎 | session 上下文模型 | `schemaName` 缺省值 | 备注 |
|------|---------------------|---------------------|------|
| MySQL | 只有 `database`，无独立 schema | `schemaName ??= session.database` | MySQL 的 `database` 在 SQL 中等同于 schema；如果 `session.database` 也为 null → `Unsupported("no_database_selected")` |
| PostgreSQL | `database` + `schema`，且支持 search_path | `schemaName ??= session.schema ?? "public"` | PG 的 `database` 是隔离 catalog，session 必有；schema 缺省 `public`（PG 默认） |
| Oracle | 本期 unsupported，跳过 | — | follow-up 时 fallback 用 `session.schema ?? username.toUpperCase()` |
| H2 | unsupported | — | — |

**重要**: Action 层不做 fallback——传 null 给 Service，Service 把 `ResolvedExecutionContext` 一并交给 Provider，Provider 负责合成最终的 `schemaName`。这样跨引擎差异完全封装在 infra 层。

## 7. 输入校验与 SQL 注入防护

Mutation SQL 涉及拼接（`KILL <id>` / `OPTIMIZE TABLE <schema>.<table>`），需要在 Action 层和 Provider 层双重防护。

### Action 层校验

| 字段 | 规则 | 不满足时 |
|------|------|----------|
| `sessionId` (terminate) | 非空字符串，匹配 `^[0-9]+$` 或 `^[0-9]+,[0-9]+$`（Oracle 格式预留） | 返回 `{error: {type: "INVALID_INPUT", message: "..."}}` |
| `table` (optimize) | 非空，匹配 `^[a-zA-Z_][a-zA-Z0-9_]{0,63}$`（标准 SQL identifier，不含点/分号/引号） | 同上 |
| `schemaName` (optimize) | 可空；非空时同 table 规则 | 同上 |
| `tables[]` (table_space) | 可空数组；非空时每个元素同 table 规则 | 同上 |

### Provider 层 quoting

Provider 在拼接 SQL 时使用引擎特定的 identifier quoting：
- MySQL: `` `identifier` ``（反引号）
- PostgreSQL / H2: `"identifier"`（双引号）
- Oracle: `"identifier"`（双引号）

拼接后的 SQL 形如 `OPTIMIZE TABLE \`test\`.\`users\``（MySQL）或 `VACUUM (FULL, VERBOSE) "test"."users"`（PG），不含任何用户输入裸拼。

## 8. ConfirmationToken

复用 `UpdateConnectionConfirmableAction` 的 SHA-256 无状态范式。

**字段命名规范**（与现有 confirmable action 对齐）:
- **输出字段**: `confirmation_token`（snake_case）
- **输入字段**: `confirmationToken`（camelCase）
- 与 `UpdateConnectionConfirmableAction` 保持一致（输出: `confirmation_token`，输入: `confirmationToken`）

### terminate_session

```
SHA-256(
    sessionId          // 调用者 session
  + targetSessionId    // 要 kill 的 session
  + willRunSql         // 实际将执行的 SQL 文本（如 "KILL 12345"）
)
```

Preview 阶段由 provider 返回 `willRunSql` + `currentSql`，action 用这些字段算 token。

### optimize_table

```
SHA-256(
    sessionId
  + table
  + schemaName
  + willRunSql         // 如 "OPTIMIZE TABLE test.users"
)
```

## 9. Action Input/Output Schema

### datatalk_lock_info

```jsonc
// input
{}

// output (Ok)
{
  "blockingChain": [{ "table", "lockType", "holderId", "waiterId", "waitMillis", "holderSql", "waiterSql" }],
  "recommendations": [{ "severity", "summary", "suggestedActionId", "suggestedToolName", "suggestedActionArgs", "suggestedSql" }]
}

// output (Unsupported)
{ "unsupported": true, "reason": "..." }
```

### datatalk_pool_status

```jsonc
// input
{}

// output (Ok)
{
  "scope", "activeConnections", "idleConnections", "maxConnections",
  "threadsRunning", "waitingConnections", "identifier",
  "recommendations": [{ "severity", "summary", "suggestedActionId", "suggestedToolName", "suggestedActionArgs", "suggestedSql" }]
}
```

### datatalk_table_space

```jsonc
// input
{ "tables": ["users", "orders"] }    // 可选；null/empty 扫描全库 user 表（上限 200）

// output (Ok)
{
  "tables": [{ "table", "schemaName", "rowCount", "dataSizeBytes", "indexSizeBytes", "freeSpaceBytes" }],
  "recommendations": [{ "severity", "summary", "suggestedActionId", "suggestedToolName", "suggestedActionArgs", "suggestedSql" }]
}
```

### datatalk_terminate_session

```jsonc
// input — Phase 1 (preview)
{ "sessionId": "12345", "confirm": false }

// Phase 1 output
{
  "confirm_required": true,
  "confirmation_token": "...",        // snake_case 输出
  "preview": { "engine": "mysql", "sessionId": "12345", "willRunSql": "KILL 12345", "currentSql": "SELECT ..." }
}

// input — Phase 2 (execute)
{ "sessionId": "12345", "confirm": true, "confirmationToken": "..." }   // camelCase 输入

// Phase 2 output (Ok)
{ "ok": true, "sessionId": "12345", "message": "Session terminated" }

// Phase 2 output (Unsupported)
{ "unsupported": true, "reason": "self_termination_blocked" }
```

### datatalk_optimize_table

```jsonc
// input — Phase 1
{ "table": "users", "schemaName": null, "confirm": false }

// Phase 1 output
{
  "confirm_required": true,
  "confirmation_token": "...",        // snake_case 输出
  "preview": { "engine": "mysql", "table": "users", "schemaName": "test", "willRunSql": "OPTIMIZE TABLE `test`.`users`" },
  "recommendations": [{ "severity": "critical", "summary": "Will lock the table..." }]
}

// input — Phase 2
{ "table": "users", "schemaName": null, "confirm": true, "confirmationToken": "..." }   // camelCase 输入

// Phase 2 output (Ok)
{ "ok": true, "table": "users", "schemaName": "test", "durationMs": 3200, "reclaimedBytes": 1048576, "message": "..." }
```

## 10. 错误处理

### 三态序列化（诊断 + mutation 共用）

与现有 `ExplainQueryAction` / `IndexHintsAction` 保持一致（参考 `ExplainQueryAction.java:75-77`）：

| DiagnosticResult 变体 | Action 层 JSON |
|----------------------|----------------|
| `Ok(value)` | 展开为 `{...value fields}` |
| `Unsupported(reason)` | `{ "unsupported": true, "reason": "..." }` |
| `DiagnosticError(type, msg)` | `{ "error": { "type": "...", "message": "..." } }` |

**重要**: `DiagnosticError` 不抛 `DataTalkException`，而是返回 error JSON 对象。这与现有 explain/index 行为一致（`DiagnosticsController` 和 `ExplainQueryAction` 都是返回 error 对象，不抛异常）。

### 权限不足回退

Provider 内部捕获 `SQLException`，匹配引擎特定错误码后转 `DiagnosticResult.unsupported()`：

| 引擎 | SQLState | ErrorCode | 说明 |
|------|----------|-----------|------|
| MySQL | `42000` | `1227` / `1142` | 权限不足 |
| PostgreSQL | `42501` | — | 权限不足 |

### 超时

- 诊断查询: 5s（参考现有 explain）
- Mutation Phase 2（execute）: 30s（OPTIMIZE 大表可能慢，超时则 suggestions 里建议"后台维护窗口"）

## 11. AGENTS.md 变更

**文档命名风格**: AGENTS.md 内工具名统一用下划线（如 `` `datatalk_lock_info` ``），与 `@DataTalkAction(id=...)` 的点号（`datatalk.lock_info`）是不同层级的命名约定。

**强制约束 — 全英文**: AGENTS.md 是 runtime prompt，`AgentPromptContractTest.runtimePromptStaysEnglishAndAvoidsUnsupportedWorkspaceTargets`（`server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java:47-58`）通过正则 `\\p{IsHan}` 断言**不含任何汉字**。本节后续所有新增段落必须英文撰写；中文描述仅出现在本 spec 文档中作为意图说明，不进 AGENTS.md。

**强制约束 — 工具名注册**: `AgentPromptContractTest.runtimePromptReferencesOnlyRegisteredMcpTools`（同文件第 31-44 行）断言 AGENTS.md 中所有 `datatalk_*` 名字都已在 `ActionRegistry` 注册。这意味着 §13 Batch 3 的实施顺序硬约束：**必须先注册 `TerminateSessionConfirmableAction` / `OptimizeTableConfirmableAction` 这两个新 action（带 `@DataTalkAction(id=...)` 注解 + Spring `@Component` 扫描），再向 AGENTS.md 写入 `datatalk_terminate_session` / `datatalk_optimize_table`**。否则 contract test 将失败。

### 删除

移除第 118-119 行 "Not yet available" 声明。

### Query Diagnostics 新增

```markdown
- `datatalk_lock_info`
  Get the current blocking chain — holder/waiter pairs, lock types, wait duration, and current SQL.
  Input: `{}` (uses session connection context).
  Output: `{ blockingChain, recommendations }` or `{ unsupported: true, reason }`.
  Call when: user says "query is stuck", "blocked", "hung", "who is locking", or any wait/timeout complaint.
  Do not call when: the user only asks about query performance (use `datatalk_explain_query` instead).

- `datatalk_pool_status`
  Get server-side connection statistics — active, idle, max connections, running threads.
  Input: `{}`.
  Output: `{ scope, activeConnections, idleConnections, maxConnections, threadsRunning, waitingConnections, identifier, recommendations }` or `{ unsupported: true, reason }`.
  Call when: user asks "how many connections", "connection pool full", "too many sessions", or server capacity questions.
  Do not call when: the user asks about their own DataTalk connection settings.

- `datatalk_table_space`
  Get table storage statistics — row count, data size, index size, reclaimable space.
  Input: `{ "tables": ["t1", "t2"] }` (optional; defaults to all user tables, capped at 200).
  Output: `{ tables, recommendations }` or `{ unsupported: true, reason }`.
  Call when: user asks "table size", "disk usage", "space", "how big is X", or storage-related questions.
  Do not call when: the user only asks about row counts (use a SELECT COUNT query instead).
```

### Mutation Actions 新增

```markdown
### Mutation Actions

- `datatalk_terminate_session`
  Kill a database session. Two-phase: preview (confirm=false) shows what will run; confirm (confirm=true) executes.
  Input: `{ "sessionId": "12345", "confirm": false }` → `{ confirm_required, confirmation_token, preview: { engine, sessionId, willRunSql, currentSql } }`.
  Second call: `{ "sessionId": "12345", "confirm": true, "confirmationToken": "..." }` → `{ ok, sessionId, message }`.
  Call when: `datatalk_lock_info` identifies a blocking holder and the user agrees to terminate it.
  Do not call when: the user has not confirmed. Always present the preview first.

- `datatalk_optimize_table`
  Reclaim table space (OPTIMIZE TABLE / VACUUM FULL by engine). Two-phase confirmable.
  Input: `{ "table": "users", "schemaName": null, "confirm": false }` → preview with `willRunSql`.
  Second call with `confirm: true` + `confirmationToken` → `{ ok, table, schemaName, durationMs, reclaimedBytes, message }`.
  Call when: `datatalk_table_space` shows significant reclaimable space and the user agrees.
  Do not call when: on a production system during peak hours without explicit user acknowledgment of locking impact.
```

### Diagnostics Workflow Rules 追加

在现有第 6 条后追加：

```markdown
7. Lock complaint received -> call `datatalk_lock_info`.
8. `datatalk_lock_info` returns blocking chain with waitMillis > 5000 and recommends `datatalk_terminate_session` -> present holder details to user, ask confirmation, then call `datatalk_terminate_session` with `confirm=false` for preview.
9. User confirms terminate -> call `datatalk_terminate_session` with `confirm=true` and the `confirmation_token` from preview.
10. Storage/space question -> call `datatalk_table_space`.
11. `datatalk_table_space` shows > 30% reclaimable space and recommends `datatalk_optimize_table` -> warn about table locking, proceed with preview if user agrees.
12. Capacity/connection count question -> call `datatalk_pool_status`.
13. If a diagnostic tool returns `{ unsupported: true }`, inform the user the capability is not available for their engine and explain the reason.
```

### i18n Key 清单（必须中英双套同步）

新增到 `server/data-talk-adapter/src/main/resources/messages.properties` (英) 与 `messages_zh_CN.properties` (中)：

| Key | 用途 | 示例值（英） | 示例值（中） |
|---|---|---|---|
| `diagnostics.lock.unsupported.h2` | LockReport 在 H2 的 reason | `H2 does not expose lock waits` | `H2 不暴露锁等待视图` |
| `diagnostics.lock.unsupported.oracle` | LockReport 在 Oracle 的 reason | `Oracle diagnostics not yet available` | `Oracle 诊断功能尚未可用` |
| `diagnostics.lock.recommendation.terminate` | 阻塞链长等待时的 terminate 建议 | `Holder session {0} has blocked for {1}s; consider terminating it` | `holder 会话 {0} 已阻塞 {1} 秒，可考虑终止` |
| `diagnostics.pool.unsupported.h2_embedded` | PoolReport 在 H2 嵌入式的 reason | `H2 embedded mode does not expose server connection stats` | `H2 嵌入式模式不暴露服务端连接统计` |
| `diagnostics.pool.unsupported.oracle` | 同 lock.unsupported.oracle 复用文案 | — | — |
| `diagnostics.pool.recommendation.high_usage_warning` | warn-ratio 触发 | `Connection usage is at {0}%, approaching capacity` | `连接占用 {0}%，接近上限` |
| `diagnostics.pool.recommendation.high_usage_critical` | critical-ratio 触发 | `Connection usage is at {0}%, near maximum capacity` | `连接占用 {0}%，逼近最大容量` |
| `diagnostics.space.unsupported.h2_no_size` | SpaceReport 在 H2 的 partial 说明 | `H2 does not expose storage size; row counts only` | `H2 不暴露存储大小，仅行数可用` |
| `diagnostics.space.unsupported.oracle` | 同上复用 | — | — |
| `diagnostics.space.recommendation.optimize_table` | reclaim-ratio + min-data-size 触发 | `Table {0} has {1}% reclaimable space ({2})` | `表 {0} 有 {1}% 可回收空间 ({2})` |
| `diagnostics.terminate.unsupported.h2` | terminate_session 在 H2 | `H2 does not support session termination` | `H2 不支持会话终止` |
| `diagnostics.terminate.unsupported.oracle` | 同上 oracle | — | — |
| `diagnostics.terminate.unsupported.self` | 自杀保护 | `Cannot terminate the current connection` | `不能终止当前连接` |
| `diagnostics.terminate.session_not_found` | MySQL 1094 / PG false 路径 | `Target session no longer exists; it may have already ended` | `目标会话已不存在，可能已自然结束` |
| `diagnostics.optimize.unsupported.h2` | optimize_table 在 H2 | `H2 does not support reclaiming space; ANALYZE only updates statistics` | `H2 不支持空间回收，ANALYZE 仅更新统计信息` |
| `diagnostics.optimize.unsupported.oracle` | 同上 oracle | — | — |
| `diagnostics.optimize.unsupported.no_database` | MySQL 缺 session.database | `No database selected; cannot determine table location` | `未选择数据库，无法确定表位置` |
| `diagnostics.optimize.preview.lock_warning_mysql` | OPTIMIZE TABLE 警告 | `OPTIMIZE TABLE locks the table for the duration of the operation; may impact production traffic` | `OPTIMIZE TABLE 会在执行期间持有表锁，可能影响生产流量` |
| `diagnostics.optimize.preview.lock_warning_pg` | VACUUM FULL 警告 | `VACUUM FULL takes ACCESS EXCLUSIVE lock; the table will be unreadable during the operation` | `VACUUM FULL 取 ACCESS EXCLUSIVE 锁，执行期间表不可读` |
| `diagnostics.error.invalid_session_id` | session id 正则白名单 | `Invalid session id format` | `会话 ID 格式无效` |
| `diagnostics.error.invalid_table_name` | table 标识符白名单 | `Invalid table name format` | `表名格式无效` |
| `diagnostics.error.invalid_schema_name` | schemaName 白名单 | `Invalid schema name format` | `schema 名格式无效` |
| `diagnostics.error.confirmation_token_required` | mutation 二阶段缺 token | `confirmationToken is required when confirm=true` | `confirm=true 时必须提供 confirmationToken` |
| `diagnostics.error.confirmation_token_mismatch` | mutation 二阶段 token 不匹配 | `confirmationToken does not match preview` | `confirmationToken 与 preview 不匹配` |
| `diagnostics.error.permission_denied` | SQLState 42501 / errorCode 1227/1142 | `Insufficient privileges to read diagnostic views` | `查询诊断视图权限不足` |

**实现规范**:
- Key 命名按 `diagnostics.<capability>.<role>.<context>` 层级组织
- 涉及阈值的文案（如 `high_usage_warning`）用 `{0}` 占位，由 service 层调用 `Translator.get(key, value)` 注入
- 未来新引擎复用 `*.oracle` 风格 key（`*.<engine>`）；本期文案对所有 unsupported 都先填，后续更新只换 value 不增 key

## 12. 前端影响

**无前端改动**。确认流程由 AI 编排：
- AI 在工具返回中看到 `confirm_required: true`
- AI 用自然语言向用户展示 preview 信息并询问是否继续
- 用户同意后，AI 发送第二次工具调用（`confirm: true` + `confirmationToken`）
- 前端 `GenericTool` 渲染器显示工具结果 JSON，无需特化卡片

这与 `datatalk_update_connection_confirmable` 现有行为一致。

## 13. 实现批次

按依赖方向（domain → app → infra → adapter）分阶段推进；同阶段内的独立 provider 可并行。每个 Batch 末尾的 verification step 是硬性 gate，未过不进下一 Batch。

### Batch 1: Domain 层扩展（串行）

- `DiagnosticCapability`: 重命名 `CONNECTION_POOL` → `POOL_STATUS`，新增 `TERMINATE_SESSION` / `OPTIMIZE_TABLE`
- `LockReport`: `locks` → `blockingChain`，新增 `recommendations` 字段，`LockEntry` 4 → 8 字段
- `PoolReport`: 完全重写（scope / activeConnections / idleConnections / maxConnections / threadsRunning / waitingConnections / identifier / recommendations）
- `SpaceReport`: 新增 `recommendations` 字段，`TableSpaceEntry` 新增 `schemaName`
- 新增 `DiagnosticRecommendation`（含 `suggestedActionId` + `suggestedToolName` 双字段）
- 新增 `TerminateSessionResult` / `OptimizeTableResult`
- **检查 application 层 exhaustive switch**: 按 CLAUDE.md "Bug Fixes" 节，domain sealed/record 改动须扫 application 层 switch（`DiagnosticsService.explain()` / `indexHints()` 已有 switch 模式，新方法跟随）
- **Verification gate**: `cd server && mvn compile -q` 全部模块零错误
- **关键收尾**: 跑 `mvn install -pl data-talk-domain -am -DskipTests` —— CLAUDE.md "Backend Run vs Compile" 节明确 `mvn compile` **不会**刷新 jar 到 `~/.m2`，下游 batch 编译会拉到旧版。Batch 1 之后**必须** install，否则 Batch 2 的 application 层引用新枚举值会报符号未解析

### Batch 2a: Application 层（串行）

- 新增 `DiagnosticsThresholdProperties` (`@ConfigurationProperties("datatalk.diagnostics")` record)；在 `DataTalkAdapterApplication`（或 application 层 `@EnableConfigurationProperties`）注册
- `application.yml` / `application.properties` 添加默认值（值见 §5）
- `DiagnosticsProvider` 接口扩展（`connectionPoolInfo()` → `poolStatus()` 重命名签名 + 返回类型；`tableSpaceInfo()` 签名加 `List<String> tables`；新增 4 个方法：`terminateSessionPreview()` + `terminateSession()` + `optimizeTablePreview()` + `optimizeTable()`，覆盖 2 个 mutation capability 的 Phase 1/Phase 2）
- `DiagnosticsService`: 新增 7 个 public 方法（3 诊断 + 2 preview + 2 execute）+ recommendations 生成逻辑（注入 `DiagnosticsThresholdProperties`）
- **Verification gate**: `mvn compile -q`
- **收尾 install**: `mvn install -pl data-talk-application -am -DskipTests`

### Batch 2b: Infrastructure Provider 层（**并行**：MySQL / PostgreSQL / H2 / Oracle）

> 4 个 provider 互相不依赖，可由并行 subagent 同时实施（参考 CLAUDE.md "Parallel Plan Execution"）。每个 provider 一个独立子任务。

- **MySQL Provider** — 真实 SQL 实现 5 个能力（详见 §6 各表 MySQL 行）；`supportedCapabilities()` 加 5 个新值
- **PostgreSQL Provider** — 同上 + `supportedDriverTypes()` 必须含 `"postgres"` 与 `"postgresql"` 两个 alias（依据 [DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md) §47 现状表）
- **H2 Provider** — 按 §6 矩阵：lock_info / pool_status / terminate_session / optimize_table 全 `Unsupported`；table_space 实现 partial（仅 table + schemaName + rowCount，size 字段为 0/null）；`supportedCapabilities()` 仅加 `TABLE_SPACE`
- **Oracle Provider** — 5 个新方法全部 `Unsupported("Oracle diagnostics not yet available")`；`supportedCapabilities()` **不动**（仅保留现有 EXPLAIN / INDEX_HINTS）
- 每个 provider 同步实现 §14 的 protected 模板方法（仅新方法走模板）以支持单测
- 单 batch 内**跳过**单 provider 的 `mvn compile`，由 Batch 2b 收尾统一 verify（参考 CLAUDE.md "Parallel Plan Execution" 节"Within a batch, skip per-edit verification"）
- **Verification gate**: 所有 provider 写完后跑 `mvn compile -q` + provider 单测（`mvn test -pl data-talk-infrastructure`）
- **收尾 install**: `mvn install -pl data-talk-infrastructure -am -DskipTests`

### Batch 3a: Adapter Action 注册（先行，AGENTS.md 后做）

- 重写 `LockInfoAction` / `PoolStatusAction` / `TableSpaceAction`：删硬编码 `unsupported`，注入 `DiagnosticsService`，三态序列化对齐 `ExplainQueryAction:75`
- 新增 `TerminateSessionConfirmableAction`（`@DataTalkAction(id="datatalk.terminate_session", riskLevel=L2, category=MUTATION, exposeToMcp=true)`）
- 新增 `OptimizeTableConfirmableAction`（同上 id="datatalk.optimize_table"）
- Action 层输入校验（正则白名单，详见 §7）
- i18n 键添加（中英文双套，§11 清单）
- **Verification gate**: `mvn compile -q` + Action 单测 + `mvn test -pl data-talk-adapter -Dtest=AgentPromptContractTest` 必须通过（此时 AGENTS.md 还未提到新工具，contract test 仍 pass）

### Batch 3b: AGENTS.md 更新（必须在 3a 之后）

- 删除现有 §"Not yet available" 段（line 118-119）
- 追加 Query Diagnostics 段（5 个工具的英文 prompt；§11 已给草稿）
- 追加 Mutation Actions 段
- 扩充 Diagnostics Workflow Rules（追加第 7-13 条闭环编排规则）
- **Verification gate**: `mvn test -pl data-talk-adapter -Dtest=AgentPromptContractTest` 全 13 个 test 通过（特别是 `runtimePromptReferencesOnlyRegisteredMcpTools` 与 `runtimePromptStaysEnglishAndAvoidsUnsupportedWorkspaceTargets`）

### Batch 4: 全链路验证

- `cd server && mvn clean verify` 全量测试套件
- 集成测试覆盖闭环场景：
  1. `lock_info` 返回阻塞链 + recommendation → AI 调 `terminate_session` preview → 拿 token → confirm execute → 验证 session 真被 kill
  2. `table_space` 返回 reclaim 建议 → AI 调 `optimize_table` preview → confirm execute → 验证 reclaimedBytes 非 0
- 手工跑一遍报告里失败的 3 个工具 + 新 2 个 mutation，更新测试报告至全部 ✅
- **文档归档**（CLAUDE.md "Post-Execution Document Housekeeping"）:
  - 本 spec 的 `状态: Active` → `状态: Completed`
  - 移到 `docs/product-specs/index.md` Completed 段
  - 关联的 exec-plan 同步移到 Completed
  - 更新 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` §47 现状表（如果触及）
  - 更新 `~/.data-talk/opencode/datatalk-tools-test-report.md` 的"不可用工具"段

## 14. 测试策略

现有 provider 使用 `DriverManager.getConnection()` 直接建连，非注入式 `JdbcTemplate`。无法用 Mockito mock。本 spec 引入"protected 模板方法 + 测试子类 override"的策略，但**严格限定边界**：

### 模板方法的应用范围

**只对本 spec 新增的 7 个方法应用**：`lockInfo` / `poolStatus` / `tableSpaceInfo` / `terminateSessionPreview` / `terminateSession` / `optimizeTablePreview` / `optimizeTable`。

**不动现有的 `explain` / `indexHints`**——理由:
1. 现有 explain/indexHints 已通过 H2 集成测试覆盖（`MySqlDiagnosticsProviderTest` / `PostgreSqlDiagnosticsProviderTest` 等若存在），改它们等于"在诊断 spec 里夹带 refactor"，违反 CLAUDE.md "Don't add features beyond what the task requires"
2. 跨方法风格分裂的问题确实存在，但属于**未来重构**议题（建议挂 `docs/exec-plans/tech-debt-tracker.md`），不在本 spec 解决
3. 新方法独占模板方法 = 新代码的可测性提升，旧代码不受影响

### 模板方法签名（infra 层 Provider 抽象基类）

```java
// 新增到 server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/AbstractDiagnosticsProvider.java
abstract class AbstractDiagnosticsProvider implements DiagnosticsProvider {

    /** 仅给新增 5 个方法的实现使用；explain/indexHints 仍走原内联 DriverManager 调用 */
    protected Connection openConnection(ConnectionRecord conn, String decryptedPassword) throws SQLException {
        return DriverManager.getConnection(JdbcUrlBuilder.build(conn), conn.username(), decryptedPassword);
    }

    /** 仅给新增 5 个方法的实现使用 */
    protected List<Map<String,Object>> queryForList(ConnectionRecord conn, String decryptedPassword, String sql, Object... params) throws SQLException {
        // 标准 PreparedStatement / ResultSet → List<Map> 转换
    }

    /** mutation 路径 */
    protected int executeUpdate(ConnectionRecord conn, String decryptedPassword, String sql, Object... params) throws SQLException { ... }
}
```

测试子类 override `openConnection` / `queryForList` / `executeUpdate` 返回 canned 数据，不真正连 DB。

### 测试矩阵

| 层 | 测试方式 | 覆盖点 |
|---|---|---|
| Provider — MySQL/PG/Oracle (infra) | 模板方法 override 测试子类 + canned `List<Map>` | SQL 构造正确性（assertion on captured SQL string）、结果映射、权限不足回退（throw `SQLException("permission denied", "42501")`）、`SQLException(errorCode=1094)` → `Ok(ok=false)`、自杀保护（mock `SELECT CONNECTION_ID()` 返回 target id）、identifier quoting |
| Provider — H2 (infra) | `@SpringBootTest` + 真实 embedded H2 实例 | H2 的真实 `Unsupported` / partial 行为；不 mock |
| Provider 单测覆盖范围 | 每个 capability × 每个非 H2 引擎 ≥ 3 个 case：成功路径 / 权限不足 / 边界 case（target 已不存在 / 自杀）；mutation capability 的 preview 与 execute 分别独立测试 | 总 ≈ (3 诊断 capability + 2 mutation × 2 phase) × (3 engine: MySQL/PG/Oracle) × 3 ≈ 63 单测；Oracle 大部分是 unsupported 简化（每方法 1 case） |
| Service (app) | Mock `DiagnosticsProviderRegistry` + Mock `DiagnosticsProvider` | 能力检查（`supportedCapabilities()` 不含 → `Unsupported`）、委托调用、Unsupported 传播、Recommendations 阈值触发（注入 mock `DiagnosticsThresholdProperties` 验证不同阈值组合） |
| Action (adapter) | Mock `DiagnosticsService` | 输入校验（正则白名单：合法/非法 sessionId/table/schema）、DiagnosticResult 三态序列化（对齐 `ExplainQueryAction:75-77`）、二阶段 confirm 流程（Phase 1 调 service preview 方法 → 返回 `confirm_required: true` + token；Phase 2 重算 token 校验后调 service execute 方法）、`confirmation_token`（输出 snake_case） / `confirmationToken`（输入 camelCase）字段命名、token mismatch 拒绝、Phase 1 输出 JSON 顶层 `recommendations` 字段从 `OptimizeTablePreview.recommendations` 直接展开 |
| 集成 — Confirmable 闭环 (adapter) | `@SpringBootTest` + embedded H2（H2 全 unsupported，验证 prompt path） + Mockito 替换 provider | lock_info / table_space 在 H2 走 unsupported 路径，AGENTS.md 规则验证；闭环路径用替换 provider mock 模拟 MySQL 行为 |
| 闭环场景 (adapter) | Action 层 mock service | lock_info 返回阻塞链 + recommendation（含 `suggestedToolName=datatalk_terminate_session`）→ AI 应解析 recommendation → terminate preview 用 recommendation.suggestedActionArgs.sessionId → confirm 二阶段；同型 table_space → optimize_table |
| Prompt Contract | `AgentPromptContractTest` 既有 + 新增断言 | 新断言：AGENTS.md 含 `datatalk_terminate_session` / `datatalk_optimize_table` / `Mutation Actions`；不含汉字 |

### H2 测试预期

| 能力 | 预期结果 |
|------|----------|
| lock_info | `Unsupported("H2 does not expose lock waits")` |
| pool_status | `Unsupported("H2 embedded mode does not expose server connection stats")` |
| table_space | Ok，tables 含 table/schemaName/rowCount，dataSizeBytes=0, indexSizeBytes=0, freeSpaceBytes=null，recommendations=[] |
| terminate_session | `Unsupported("H2 does not support session termination")` |
| optimize_table | `Unsupported("H2 does not support reclaiming space; ANALYZE only updates stats")` |

### CI 要求

- 所有新 i18n key 在 messages.properties 与 messages_zh_CN.properties 都存在（已有项目内 i18n smoke test 模式可复用）
- `mvn clean verify` 在 CI 通过；本地 `mvn -T 4C verify` 并行可加速

### H2 测试预期

| 能力 | 预期结果 |
|------|----------|
| lock_info | `Unsupported("H2 does not expose lock waits")` |
| pool_status | `Unsupported("H2 embedded mode does not expose server connection stats")` |
| table_space | Ok，tables 含 table/schemaName/rowCount，dataSizeBytes=0, indexSizeBytes=0, freeSpaceBytes=null |
| terminate_session | `Unsupported("H2 does not support session termination")` |
| optimize_table | `Unsupported("H2 does not support reclaiming space")` |
