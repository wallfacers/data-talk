# Diagnostics & Mutation Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把三个 stub 诊断工具（`datatalk_lock_info` / `datatalk_pool_status` / `datatalk_table_space`）从硬编码 `unsupported` 升级为 MySQL / PostgreSQL 真实实现 + H2 部分实现 + Oracle 显式 unsupported；新增两个 confirmable mutation action（`datatalk_terminate_session` / `datatalk_optimize_table`）形成诊断→建议→执行闭环。

**Architecture:** domain ← application ← infrastructure ← adapter 四层；新增 `AbstractDiagnosticsProvider` 仅给本期 7 个新方法提供 `openConnection` / `queryForList` / `executeUpdate` 模板方法（不重构 explain/indexHints）；Service 层注入 `DiagnosticsThresholdProperties` 做 recommendations 阈值参数化；Mutation 走 Phase 1 preview / Phase 2 execute 两阶段独立方法（非 dryRun 标志位）；闭环靠 `DiagnosticRecommendation.suggestedActionId` + `suggestedToolName` 双字段把诊断结果引向 mutation action。Oracle 本期保持 unsupported（连接栈未闭环，依据 [DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md) Hard Rule "Do not add prompt-only support"）；H2 受技术限制（嵌入式无服务端视图）只对 `table_space` 做 partial。

**Tech Stack:** Spring Boot 3.5（`@ConfigurationProperties`、`@DataTalkAction` Action SPI）、Java 21（sealed interface / record / 模板方法）、JDBC `DriverManager`、JUnit 5、AssertJ、Mockito、embedded H2、Maven multi-module。

**Spec:** [docs/product-specs/2026-04-29-diagnostics-mutation-design.md](../product-specs/2026-04-29-diagnostics-mutation-design.md)

---

## Files

### Domain (新增 / 修改 record)

- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/DiagnosticCapability.java`
- Rewrite: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/LockReport.java`
- Rewrite: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/PoolReport.java`
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/SpaceReport.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/DiagnosticRecommendation.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/TerminateSessionPreview.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/TerminateSessionResult.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/OptimizeTablePreview.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/diagnostics/OptimizeTableResult.java`

### Application (接口 + service + 配置)

- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsProvider.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsService.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsThresholdProperties.java`
- Modify: `server/data-talk-adapter/src/main/resources/application.yml`
- Modify: `server/data-talk-adapter/src/test/resources/application.yml` (test profile 同步默认值)

### Infrastructure (4 个 provider + 抽象基类)

- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/AbstractDiagnosticsProvider.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/MySqlDiagnosticsProvider.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/PostgreSqlDiagnosticsProvider.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/H2DiagnosticsProvider.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/OracleDiagnosticsProvider.java`

### Adapter (action + i18n + AGENTS.md)

- Rewrite: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/LockInfoAction.java`
- Rewrite: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/PoolStatusAction.java`
- Rewrite: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/TableSpaceAction.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/TerminateSessionConfirmableAction.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/OptimizeTableConfirmableAction.java`
- Modify: `server/data-talk-adapter/src/main/resources/messages.properties`
- Modify: `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties`
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

### Tests

- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/MySqlDiagnosticsProviderTest.java`
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/PostgreSqlDiagnosticsProviderTest.java`
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/H2DiagnosticsProviderTest.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/OracleDiagnosticsProviderTest.java`
- Create / Modify: `server/data-talk-application/src/test/java/com/datatalk/application/diagnostics/DiagnosticsServiceTest.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/LockInfoActionTest.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/PoolStatusActionTest.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/TableSpaceActionTest.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/TerminateSessionConfirmableActionTest.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/OptimizeTableConfirmableActionTest.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/diagnostics/DiagnosticsClosedLoopIT.java`

### Docs

- Modify: `docs/product-specs/index.md` (已注册，post-execution 移到 Completed)
- Modify: `docs/exec-plans/index.md` (Active 注册 → Completed 移动)
- Modify: `docs/product-specs/2026-04-29-diagnostics-mutation-design.md` (post-execution 状态 Active → Completed)
- Modify: `~/.data-talk/opencode/datatalk-tools-test-report.md` (post-execution 把"不可用"段改写)

---

## Task 1: Register Plan

- [ ] 把本计划写入 `docs/exec-plans/2026-04-29-diagnostics-mutation-plan.md`（已写）。
- [ ] 在 `docs/exec-plans/index.md` 的「活跃计划」表格新增一行：链接 + 创建日期 `2026-04-29` + 摘要（参考其他 plan 摘要风格）。
- [ ] 自检 `docs/product-specs/index.md` 第 256 行的 spec 摘要已对齐当前修订（已对齐 Oracle unsupported 描述）。
- [ ] commit：`docs(exec-plans): register diagnostics & mutation actions plan`

## Task 2: Domain Layer — Records & Capability Enum (Batch 1)

**目的：** 把 spec §3 列出的所有 domain 形态落到代码，给后续 application / infra / adapter 编译提供类型基座。

- [ ] 修改 `DiagnosticCapability.java`：把 `CONNECTION_POOL` 重命名为 `POOL_STATUS`，新增 `TERMINATE_SESSION` 和 `OPTIMIZE_TABLE`。最终 7 个值：`EXPLAIN, INDEX_HINTS, LOCK_INFO, POOL_STATUS, TABLE_SPACE, TERMINATE_SESSION, OPTIMIZE_TABLE`。
- [ ] 重写 `LockReport.java` 为 `LockReport(List<LockEntry> blockingChain, List<DiagnosticRecommendation> recommendations)`；嵌套 record `LockEntry(String table, String lockType, String holderId, String waiterId, Long waitMillis, String holderSql, String waiterSql)`。
- [ ] 重写 `PoolReport.java` 为 `PoolReport(String scope, Integer activeConnections, Integer idleConnections, Integer maxConnections, Integer threadsRunning, Integer waitingConnections, String identifier, List<DiagnosticRecommendation> recommendations)`。
- [ ] 修改 `SpaceReport.java` 为 `SpaceReport(List<TableSpaceEntry> tables, List<DiagnosticRecommendation> recommendations)`；嵌套 record 加字段：`TableSpaceEntry(String table, String schemaName, long rowCount, long dataSizeBytes, long indexSizeBytes, Long freeSpaceBytes)`。
- [ ] 创建 `DiagnosticRecommendation.java`：`record DiagnosticRecommendation(String severity, String summary, String suggestedActionId, String suggestedToolName, Map<String,Object> suggestedActionArgs, String suggestedSql)`。
- [ ] 创建 `TerminateSessionPreview.java`：`record TerminateSessionPreview(String engine, String sessionId, String willRunSql, String currentSql)`。
- [ ] 创建 `TerminateSessionResult.java`：`record TerminateSessionResult(boolean ok, String sessionId, String message)`。
- [ ] 创建 `OptimizeTablePreview.java`：`record OptimizeTablePreview(String engine, String table, String schemaName, String willRunSql, Long currentDataFree, Long currentTotalSize, List<DiagnosticRecommendation> recommendations)`。
- [ ] 创建 `OptimizeTableResult.java`：`record OptimizeTableResult(boolean ok, String table, String schemaName, Long durationMs, Long reclaimedBytes, String message)`。
- [ ] 编译验证：`cd server && mvn compile -q -pl data-talk-domain` 必须零错。
- [ ] **关键收尾**：`mvn install -pl data-talk-domain -am -DskipTests` 把新 jar 推到 `~/.m2`，否则后续 batch 编译拉到旧版（CLAUDE.md "Backend Run vs Compile" 强制要求）。
- [ ] commit：`feat(domain): add diagnostics records & capability enum for mutation actions`

## Task 3: Application Layer — Interface, Service, Config (Batch 2a)

**目的：** 把 `DiagnosticsProvider` 接口扩到 7 个新方法；把 `DiagnosticsService` 加 7 个新 public 方法（3 诊断 + 2 preview + 2 execute）+ recommendations 生成；引入参数化阈值。

### 3.1 ConfigurationProperties

- [ ] 创建 `DiagnosticsThresholdProperties.java`（`@ConfigurationProperties("datatalk.diagnostics")`）。结构按 spec §5：

```java
@ConfigurationProperties("datatalk.diagnostics")
public record DiagnosticsThresholdProperties(Lock lock, Pool pool, Space space) {
    public DiagnosticsThresholdProperties {
        lock  = lock  != null ? lock  : new Lock(5_000L);
        pool  = pool  != null ? pool  : new Pool(0.80, 0.95);
        space = space != null ? space : new Space(0.30, 104_857_600L);
    }
    public record Lock(long longWaitMs) {}
    public record Pool(double warnRatio, double criticalRatio) {}
    public record Space(double reclaimRatio, long minDataSizeBytes) {}
}
```

- [ ] 在 `server/data-talk-adapter/src/main/resources/application.yml` 加默认值：

```yaml
datatalk:
  diagnostics:
    lock:
      long-wait-ms: 5000
    pool:
      warn-ratio: 0.80
      critical-ratio: 0.95
    space:
      reclaim-ratio: 0.30
      min-data-size-bytes: 104857600
```

- [ ] 同样写入 `server/data-talk-adapter/src/test/resources/application.yml`，避免测试 profile 拿不到默认值。
- [ ] 在 application 模块的 `@SpringBootApplication`/configuration class 启用 `@EnableConfigurationProperties(DiagnosticsThresholdProperties.class)`（如果当前已有总注册点就加一行；如果没有就在 `DiagnosticsService` 类上加 `@EnableConfigurationProperties(DiagnosticsThresholdProperties.class)`）。

### 3.2 DiagnosticsProvider 接口扩展

- [ ] 修改 `DiagnosticsProvider.java`：
  - 重命名 `connectionPoolInfo()` → `poolStatus()`，返回类型保持 `DiagnosticResult<PoolReport>`（PoolReport 已重写）。
  - `tableSpaceInfo()` 签名改为 `tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables)`。
  - 新增 4 个方法：`terminateSessionPreview`、`terminateSession`、`optimizeTablePreview`、`optimizeTable`，签名见 spec §4。

### 3.3 DiagnosticsService — diagnostic 方法 (3 个)

- [ ] **RED**：先写 `DiagnosticsServiceTest`，新增/补充覆盖：
  - `lockInfo` 在 provider 不支持 `LOCK_INFO` 时返回 `Unsupported`。
  - `lockInfo` 成功时把 provider 返回的 `LockReport(blockingChain, [])` + 注入的阈值 props 触发 `terminate` recommendation（mock waitMillis 6000）。
  - `poolStatus` 在 active/max 比例 = 0.85 时给 warning recommendation；= 0.96 给 critical。
  - `tableSpaceInfo` 在 `freeSpace=400MB / data=900MB` 时给 optimize recommendation；在 data=50MB 时不给。
- [ ] 跑 `mvn test -pl data-talk-application -Dtest=DiagnosticsServiceTest`，确认全 RED。
- [ ] **GREEN**：在 `DiagnosticsService` 加：
  - 注入 `DiagnosticsThresholdProperties props`。
  - `public DiagnosticResult<LockReport> lockInfo(String sessionId)` —— 套用 spec §5 流程：resolveContext / requireProvider / 检查 capability / 解密 / 调 provider / **重建 LockReport**（基于 props 生成 recommendations）。
  - `public DiagnosticResult<PoolReport> poolStatus(String sessionId)` —— 同上，按 `props.pool.warnRatio` / `props.pool.criticalRatio` 生成。
  - `public DiagnosticResult<SpaceReport> tableSpaceInfo(String sessionId, List<String> tables)` —— 按 `props.space.reclaimRatio` / `props.space.minDataSizeBytes` 给每个 entry 生成 recommendation。
  - 三态 sealed switch 全覆盖：`Ok` 重建 + 透传；`Unsupported` 透传 reason；`DiagnosticError` 透传 errorType + message。
- [ ] 跑 `mvn test -pl data-talk-application -Dtest=DiagnosticsServiceTest`，全 GREEN。

### 3.4 DiagnosticsService — mutation 方法 (4 个)

- [ ] **RED**：在 `DiagnosticsServiceTest` 加：
  - `terminateSessionPreview` 在 capability 缺失时 `Unsupported`。
  - `terminateSessionPreview` 成功时透传 provider 的 `TerminateSessionPreview`（不动 recommendations）。
  - `terminateSession`（Phase 2）成功时透传 `TerminateSessionResult{ok=true}`；session_not_found 时透传 `ok=false`。
  - `optimizeTablePreview` 透传 provider 给的 `OptimizeTablePreview` 含 critical 警告（service 不二次 mutate）。
  - `optimizeTable` execute 透传 `OptimizeTableResult`。
- [ ] 跑测试，确认 RED。
- [ ] **GREEN**：在 `DiagnosticsService` 加：
  - `public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(String sessionId, String targetSessionId)`
  - `public DiagnosticResult<TerminateSessionResult> terminateSession(String sessionId, String targetSessionId)`
  - `public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(String sessionId, String table, String schemaName)`
  - `public DiagnosticResult<OptimizeTableResult> optimizeTable(String sessionId, String table, String schemaName)`
  - 四个方法的实现都是：resolveContext / requireProvider / 检查 capability / 解密 / 直接调 provider 同名方法 / 透传三态。**Mutation Service 层不重建 record**（preview 的 recommendations 由 provider 注入；execute 不带 recommendations）。
- [ ] 测试全 GREEN。

### 3.5 编译收尾

- [ ] `cd server && mvn compile -q` 全模块零错（此时 infra 层 4 个 provider 还没改，会因方法签名不匹配编译失败——需要先打通 application 层编译，infra 在 Task 4-7 处理）。
- [ ] **注意**：因为 `DiagnosticsProvider` 接口扩了，4 个 provider 在 Task 4 之前都不能编译。所以本 task 只能跑到 `mvn compile -pl data-talk-application -am`（不带 infra），或者先用临时 default 实现压住 infra 编译；推荐路径——继续走 Task 4 让所有 provider 至少先 stub 占住接口。
- [ ] **不**做 `mvn install`——等 Task 4 之后整模块一起 install。
- [ ] commit：`feat(application): extend diagnostics service & provider interface for new capabilities`

## Task 4: Infrastructure — AbstractDiagnosticsProvider Base + Stub All Providers (Batch 2b 引子)

**目的：** 让 4 个 provider 立刻通过编译，把"接口扩了所有 provider 都炸"变成"接口扩了所有 provider 都返回 unsupported，可以增量替换"。

- [ ] 创建 `AbstractDiagnosticsProvider.java` 作为 abstract class implements `DiagnosticsProvider`。包含：

```java
abstract class AbstractDiagnosticsProvider implements DiagnosticsProvider {
    protected final Translator translator;

    protected AbstractDiagnosticsProvider(Translator translator) {
        this.translator = translator;
    }

    /** 仅给本 spec 新增的 7 个方法使用；explain/indexHints 仍走原内联 DriverManager 调用 */
    protected Connection openConnection(ConnectionRecord conn, String decryptedPassword) throws SQLException {
        ConnectionRecord effective = withDatabaseOverride(conn, conn.databaseName());
        return DriverManager.getConnection(JdbcUrlBuilder.build(effective), effective.username(), decryptedPassword);
    }

    /** 仅供模板方法分发的子类调用 */
    protected List<Map<String, Object>> queryForList(ConnectionRecord conn, String decryptedPassword, String sql, Object... params) throws SQLException {
        try (Connection c = openConnection(conn, decryptedPassword);
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    protected int executeUpdate(ConnectionRecord conn, String decryptedPassword, String sql) throws SQLException {
        try (Connection c = openConnection(conn, decryptedPassword);
             Statement s = c.createStatement()) {
            return s.executeUpdate(sql);
        }
    }

    /** 复用现有 MySqlDiagnosticsProvider 里的 withDatabaseOverride 模式 */
    protected ConnectionRecord withDatabaseOverride(ConnectionRecord conn, String database) {
        if (database == null || database.isBlank()) return conn;
        return new ConnectionRecord(
            conn.id(), conn.name(), conn.kind(), conn.host(), conn.port(), database,
            conn.username(), conn.passwordEnc(), conn.schemaDigest(),
            conn.createdAt(), conn.connectTimeout(), conn.lastTestStatus(), conn.lastTestAt()
        );
    }
}
```

- [ ] 让 `MySqlDiagnosticsProvider`、`PostgreSqlDiagnosticsProvider`、`H2DiagnosticsProvider`、`OracleDiagnosticsProvider` 都继承 `AbstractDiagnosticsProvider`（构造函数把 translator 传给 super）；删除每个类内部已有的 `withDatabaseOverride` 私有方法。
- [ ] 在 4 个 provider 里给所有新接口方法（`poolStatus` 替换原 `connectionPoolInfo`、`tableSpaceInfo` 新签名、4 个 mutation 方法）加 stub 实现，全部返回 `DiagnosticResult.unsupported(translator.get("diagnostics.<name>.unsupported.<engine>"))`。i18n key 暂时硬编码 fallback 字符串，Task 11 才补完整 properties 文件。
- [ ] **注意**：旧 `lockInfo` / 旧 `connectionPoolInfo` / 旧 `tableSpaceInfo` 实现都要更新——因为它们的返回类型 record 变了。最简策略：暂时统一返回 `unsupported`，Task 5-9 再分别真实实现。
- [ ] 编译验证：`cd server && mvn compile -q` 全模块零错。
- [ ] commit：`refactor(infra): introduce AbstractDiagnosticsProvider; stub all new capability methods`

## Task 5: H2 Provider — Real Implementation (Batch 2b)

**目的：** H2 是嵌入式，多数能力受技术限制只能 unsupported。先把 H2 收口，给 application 层集成测试一个能跑的 provider。

- [ ] **RED**：在 `H2DiagnosticsProviderTest.java` 加测试用例：
  - `lockInfo` 返回 `Unsupported(reason match "lock waits")`。
  - `poolStatus` 返回 `Unsupported(reason match "embedded mode")`。
  - `tableSpaceInfo` 在 H2 内嵌 DB 创建几张表后返回 `Ok`，每个 entry 含 `table`、`schemaName="PUBLIC"`、`rowCount > 0`、`dataSizeBytes=0`、`indexSizeBytes=0`、`freeSpaceBytes=null`。
  - `terminateSessionPreview`、`terminateSession`、`optimizeTablePreview`、`optimizeTable` 全部返回 `Unsupported`。
  - `supportedCapabilities()` 仅含 `EXPLAIN, INDEX_HINTS, TABLE_SPACE`。
- [ ] 测试全 RED（除 capabilities 外，其它已是 stub 的 RED 是"reason 文案不匹配"或方法返回类型/调用对不上）。
- [ ] **GREEN**：
  - 实现 `tableSpaceInfo`：用 `INFORMATION_SCHEMA.TABLES` JOIN H2-specific 行数视图。例如：

    ```sql
    SELECT table_schema, table_name, row_count_estimate
    FROM information_schema.tables
    WHERE table_schema NOT IN ('INFORMATION_SCHEMA', 'PUBLIC.PG_CATALOG')
      AND (? IS NULL OR table_name IN (...))
    LIMIT 200
    ```

    映射为 `TableSpaceEntry(table=table_name, schemaName=table_schema, rowCount=row_count_estimate, dataSizeBytes=0, indexSizeBytes=0, freeSpaceBytes=null)`；返回 `SpaceReport(tables, List.of())`（recommendations 由 service 层决定加不加）。
  - 其它 6 个方法保持 `unsupported`，但 reason 文案改用最终 i18n key（`diagnostics.lock.unsupported.h2` 等）。
  - 更新 `supportedCapabilities()` = `Set.of(EXPLAIN, INDEX_HINTS, TABLE_SPACE)`。
- [ ] 测试全 GREEN。
- [ ] commit：`feat(infra/h2): implement table_space (partial); declare other diagnostics unsupported`

## Task 6: Oracle Provider — Explicit Unsupported (Batch 2b)

**目的：** Oracle 本期完全 unsupported，但要诚实声明每个方法的 reason，并且 `supportedCapabilities()` 不含任何新能力，给 follow-up 留干净接口。

- [ ] 创建 `OracleDiagnosticsProviderTest.java`（如尚无）。覆盖：
  - `lockInfo` / `poolStatus` / `tableSpaceInfo` / 4 个 mutation 方法都返回 `Unsupported(reason contains "Oracle diagnostics not yet available")`。
  - `supportedCapabilities()` 仅含 `EXPLAIN, INDEX_HINTS`（维持现状）。
  - `supportedDriverTypes()` 含 `"oracle"`。
- [ ] **GREEN**：在 `OracleDiagnosticsProvider`：
  - 7 个新方法都返回 `DiagnosticResult.unsupported(translator.get("diagnostics.<x>.unsupported.oracle"))`（i18n key 在 Task 11 补，先用对应键名；翻译器 fallback 会回到 key 名）。
  - `supportedCapabilities()` 不动。
- [ ] 测试 GREEN。
- [ ] commit：`feat(infra/oracle): declare diagnostics & mutation explicitly unsupported (architecture pending)`

## Task 7: MySQL Provider — Diagnostic Methods (Batch 2b 并行)

**目的：** 真实实现 MySQL 的 `lockInfo` / `poolStatus` / `tableSpaceInfo`，含权限不足回退 + identifier quoting + truncation。

> **可与 Task 9 并行**（PG 的诊断方法）。

### 7.1 RED — Provider 测试子类 + canned data

- [ ] 在 `MySqlDiagnosticsProviderTest`（继承自 protected 模板方法）补测试：
  - 创建测试子类 `TestableMySqlDiagnosticsProvider extends MySqlDiagnosticsProvider`，override `queryForList`（按 SQL 文本分发返回不同 canned `List<Map>`）。
  - **lockInfo 成功路径**：mock `performance_schema.data_lock_waits` JOIN 返回一行（holder=42, waiter=43, table='users', wait 8s, holderSql='UPDATE users SET ...', waiterSql='SELECT ...'）。断言 `LockReport.blockingChain[0]` 字段全部正确。
  - **lockInfo 权限不足**：override 抛 `SQLException("PFS denied", "42000", 1142)`。断言返回 `Unsupported(reason contains "permission")`。
  - **poolStatus 成功路径**：mock `SHOW STATUS LIKE 'Threads_connected'` 返回 25、`Threads_running` 返回 5、`max_connections` 返回 100、`performance_schema.threads` waiting 计数返回 2。断言 `PoolReport(scope="server", active=25, idle=20, max=100, threadsRunning=5, waiting=2, identifier="<host:port>", recommendations=[])`。
  - **tableSpaceInfo 成功路径**：`tables=null` 走全表扫描，mock 返回 3 张表，断言 entries 字段映射正确，recommendations 为 `[]`。
  - **tableSpaceInfo 显式 tables**：传 `["users", "orders"]`，断言 SQL WHERE 子句含 `table_name IN`。
  - **tableSpaceInfo 上限 200**：mock 返回 250 行，断言 result 截断到 200。
- [ ] 跑测试 RED。

### 7.2 GREEN — 真实 SQL 实现

- [ ] 实现 `lockInfo`（spec §6）：

```java
@Override
public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String pwd, String database) {
    String sql = """
        SELECT
          dlw.requesting_engine_lock_id    AS waiter_lock,
          dlw.blocking_engine_lock_id      AS holder_lock,
          dl_w.OBJECT_NAME                 AS waiter_table,
          dl_w.LOCK_TYPE                   AS waiter_lock_type,
          dl_h.OBJECT_NAME                 AS holder_table,
          dl_h.LOCK_TYPE                   AS holder_lock_type,
          t_w.PROCESSLIST_ID               AS waiter_thread,
          t_h.PROCESSLIST_ID               AS holder_thread,
          t_w.PROCESSLIST_INFO             AS waiter_sql,
          t_h.PROCESSLIST_INFO             AS holder_sql,
          TIMESTAMPDIFF(MICROSECOND, trx.trx_wait_started, NOW(6)) / 1000 AS wait_ms
        FROM performance_schema.data_lock_waits dlw
        JOIN performance_schema.data_locks dl_w ON dlw.requesting_engine_lock_id = dl_w.ENGINE_LOCK_ID
        JOIN performance_schema.data_locks dl_h ON dlw.blocking_engine_lock_id = dl_h.ENGINE_LOCK_ID
        JOIN performance_schema.threads t_w   ON dlw.requesting_thread_id = t_w.THREAD_ID
        JOIN performance_schema.threads t_h   ON dlw.blocking_thread_id   = t_h.THREAD_ID
        LEFT JOIN information_schema.innodb_trx trx ON trx.trx_mysql_thread_id = t_w.PROCESSLIST_ID
        """;
    try {
        var rows = queryForList(conn, pwd, sql);
        var chain = rows.stream().map(this::toLockEntry).toList();
        return DiagnosticResult.ok(new LockReport(chain, List.of()));
    } catch (SQLException e) {
        return mapPermissionOrError(e, "lock");
    }
}

private LockReport.LockEntry toLockEntry(Map<String, Object> row) {
    return new LockReport.LockEntry(
        truncate((String) row.get("holder_table"), 64),
        normalizeLockType((String) row.get("holder_lock_type")),
        String.valueOf(row.get("holder_thread")),
        String.valueOf(row.get("waiter_thread")),
        toLong(row.get("wait_ms")),
        truncate((String) row.get("holder_sql"), 200),
        truncate((String) row.get("waiter_sql"), 200)
    );
}

private DiagnosticResult<LockReport> mapPermissionOrError(SQLException e, String capability) {
    String state = e.getSQLState();
    int code = e.getErrorCode();
    if ("42000".equals(state) && (code == 1142 || code == 1227)) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.error.permission_denied"));
    }
    return DiagnosticResult.error("MYSQL_" + capability.toUpperCase() + "_ERROR", e.getMessage());
}
```

- [ ] 实现 `poolStatus`：4 次独立 query（避免 SHOW STATUS 不能 join），按 `Threads_connected - Threads_running` 算 `idleConnections`；`identifier = conn.host() + ":" + conn.port()`；权限不足走 `mapPermissionOrError`。
- [ ] 实现 `tableSpaceInfo`：

```sql
SELECT
  table_schema    AS schema_name,
  table_name      AS table_name,
  table_rows      AS row_count,
  data_length     AS data_size,
  index_length    AS index_size,
  data_free       AS free_size
FROM information_schema.tables
WHERE table_schema = ?
  AND ( ? = 0 OR table_name IN (<placeholders>) )
ORDER BY data_length + index_length DESC
LIMIT 200
```

  动态构造 placeholders 数量；`tables` 为空/null 时第二个参数填 `0`。Service 调用时 `database` 来自 session 上下文。
- [ ] `supportedCapabilities()` 加 `LOCK_INFO, POOL_STATUS, TABLE_SPACE`（保留 EXPLAIN, INDEX_HINTS）。
- [ ] 测试 GREEN。

### 7.3 收尾

- [ ] 跑 `mvn test -pl data-talk-infrastructure -Dtest=MySqlDiagnosticsProviderTest`。
- [ ] commit：`feat(infra/mysql): implement lock_info, pool_status, table_space`

## Task 8: MySQL Provider — Mutation Methods (Batch 2b 并行)

**目的：** 实现 `terminateSessionPreview` / `terminateSession` / `optimizeTablePreview` / `optimizeTable`，含自杀保护、`KILL` 错误码 1094 处理、identifier quoting、preview/execute 数据载体。

> **可与 Task 10 并行**（PG mutation）。

### 8.1 terminate_session

- [ ] **RED**：测试覆盖
  - **Preview 成功**：`SELECT CONNECTION_ID()` 返回 99，target=42 → 查 `performance_schema.threads WHERE PROCESSLIST_ID=42` 返回 currentSql='SELECT ...'；断言 `TerminateSessionPreview(engine="mysql", sessionId="42", willRunSql="KILL 42", currentSql="SELECT ...")`。
  - **Preview 自杀**：`SELECT CONNECTION_ID()` 返回 42，target=42 → `Unsupported("self_termination_blocked")`。
  - **Preview target 不存在**：`performance_schema.threads` 查不到 → 仍返回 preview，但 `currentSql=null`（不报错——execute 阶段才能确认是否真存在）。
  - **Execute 成功**：mock `executeUpdate("KILL 42")` 返回 0；断言 `TerminateSessionResult(ok=true, sessionId="42", message=...)`。
  - **Execute target_not_found**：mock `executeUpdate` 抛 `SQLException("Unknown thread", "HY000", 1094)` → 断言 `TerminateSessionResult(ok=false, message=i18n("diagnostics.terminate.session_not_found"))`。
  - **Execute 权限不足**：抛 `SQLException("denied", "42000", 1227)` → `Unsupported(...)`。
- [ ] 测试 RED。
- [ ] **GREEN** 实现：
  - `terminateSessionPreview`：先查 `SELECT CONNECTION_ID()` 与 target 比；不等才查 `PROCESSLIST_INFO`；构造 willRunSql。
  - `terminateSession`：catch SQLException，errorCode 1094 → `Ok(ok=false)`；其它走 `mapPermissionOrError`。
- [ ] 测试 GREEN。

### 8.2 optimize_table

- [ ] **RED**：测试覆盖
  - **Preview 成功**：input `table="users", schemaName=null`，session.database="test_store" → fallback 后 schemaName="test_store"；查 `data_free` 返回 1024000；断言 `OptimizeTablePreview(engine="mysql", table="users", schemaName="test_store", willRunSql="OPTIMIZE TABLE \`test_store\`.\`users\`", currentDataFree=1024000, currentTotalSize=null, recommendations=[critical lock_warning_mysql])`。
  - **Preview no_database**：schemaName=null + session.database=null → `Unsupported("no_database_selected")`。
  - **Execute 成功**：先查 `data_free` 前=1024000，跑 `OPTIMIZE TABLE`，再查 `data_free` 后=200000 → `OptimizeTableResult(ok=true, table="users", schemaName="test_store", durationMs>0, reclaimedBytes=824000, ...)`。
  - **Identifier quoting**：表名 `\`tab"le\``（边界）应被 quote 为 `` `tab"le` `` 不抛错（实际 Action 层正则白名单先挡，这里测 provider 假设 input 已合规但 quoting 仍正确）。
- [ ] 测试 RED。
- [ ] **GREEN** 实现：
  - `optimizeTablePreview`：注入 critical recommendation 用 `Translator.get("diagnostics.optimize.preview.lock_warning_mysql")`；返回 `OptimizeTablePreview(...)` 含 recommendations。
  - `optimizeTable`：System.currentTimeMillis() 计时；`executeUpdate("OPTIMIZE TABLE `<schema>`.`<table>`")`；reclaimedBytes 从前后 data_free 差值。
- [ ] 测试 GREEN。

### 8.3 收尾

- [ ] `supportedCapabilities()` 再加 `TERMINATE_SESSION, OPTIMIZE_TABLE`，最终 `Set.of(EXPLAIN, INDEX_HINTS, LOCK_INFO, POOL_STATUS, TABLE_SPACE, TERMINATE_SESSION, OPTIMIZE_TABLE)`。
- [ ] 跑 `mvn test -pl data-talk-infrastructure -Dtest=MySqlDiagnosticsProviderTest`。
- [ ] commit：`feat(infra/mysql): implement terminate_session, optimize_table mutations with two-phase preview`

## Task 9: PostgreSQL Provider — Diagnostic Methods (Batch 2b 并行)

**目的：** PG 的 `lockInfo` / `poolStatus` / `tableSpaceInfo` 真实实现；alias 路由（`postgres` + `postgresql`）。

> **可与 Task 7 并行**。

### 9.1 RED + GREEN

- [ ] 在 `PostgreSqlDiagnosticsProviderTest` 加测试用例（mirror Task 7 的形态，但用 PG 的 SQL）。
- [ ] **GREEN** 实现 SQL（spec §6）：

```sql
-- lockInfo
SELECT
  blocking.pid                              AS holder_pid,
  blocked.pid                               AS waiter_pid,
  blocking.query                            AS holder_sql,
  blocked.query                             AS waiter_sql,
  blocked_locks.relation::regclass::text    AS table_name,
  blocked_locks.mode                        AS lock_type,
  EXTRACT(EPOCH FROM (now() - blocked.state_change)) * 1000 AS wait_ms
FROM pg_catalog.pg_locks blocked_locks
JOIN pg_catalog.pg_stat_activity blocked
  ON blocked_locks.pid = blocked.pid
JOIN pg_catalog.pg_stat_activity blocking
  ON blocking.pid = ANY(pg_blocking_pids(blocked.pid))
JOIN pg_catalog.pg_locks blocking_locks
  ON blocking_locks.pid = blocking.pid
WHERE NOT blocked_locks.granted
```

```sql
-- poolStatus
SELECT
  count(*) FILTER (WHERE state='active')                                        AS active,
  count(*) FILTER (WHERE state='idle')                                          AS idle,
  count(*) FILTER (WHERE wait_event IS NOT NULL AND wait_event_type='Lock')     AS waiting
FROM pg_stat_activity
WHERE backend_type='client backend'
```
  + 单独跑 `SHOW max_connections` 拿 max；threadsRunning 用 active。

```sql
-- tableSpaceInfo
SELECT
  n.nspname                          AS schema_name,
  c.relname                          AS table_name,
  COALESCE(s.n_live_tup, 0)          AS row_count,
  pg_relation_size(c.oid)            AS data_size,
  pg_indexes_size(c.oid)             AS index_size,
  GREATEST(pg_total_relation_size(c.oid) - pg_relation_size(c.oid) - pg_indexes_size(c.oid), 0) AS free_size
FROM pg_class c
JOIN pg_namespace n  ON c.relnamespace = n.oid
LEFT JOIN pg_stat_user_tables s ON s.relid = c.oid
WHERE c.relkind='r'
  AND n.nspname NOT IN ('pg_catalog','information_schema')
  AND ( ? = 0 OR c.relname = ANY(?) )
ORDER BY pg_total_relation_size(c.oid) DESC
LIMIT 200
```

  `tables` 数组用 `Connection.createArrayOf("text", tables.toArray())`；空时第一个参数填 0、第二个填空数组。
- [ ] 在 `PostgreSqlDiagnosticsProvider.supportedDriverTypes()` 确认含 `"postgres", "postgresql"` 双 alias（依据 `DATA_SOURCE_TYPE_COMPATIBILITY.md` §47）。
- [ ] 权限不足：PG SQLState `42501` → `Unsupported(translator.get("diagnostics.error.permission_denied"))`；其它 SQLException → `DiagnosticError`。
- [ ] `supportedCapabilities()` 加 `LOCK_INFO, POOL_STATUS, TABLE_SPACE`。
- [ ] 测试 GREEN。
- [ ] commit：`feat(infra/postgres): implement lock_info, pool_status, table_space`

## Task 10: PostgreSQL Provider — Mutation Methods (Batch 2b 并行)

> **可与 Task 8 并行**。

### 10.1 terminate_session

- [ ] **RED + GREEN**（同 Task 8 形态，PG 实现）：
  - `terminateSessionPreview`：`SELECT pg_backend_pid()` 比 target 做自杀保护；查 `pg_stat_activity.query WHERE pid=?` 拿 currentSql；构造 `willRunSql = "SELECT pg_terminate_backend(<pid>)"`。
  - `terminateSession`：执行 `SELECT pg_terminate_backend(?)`，**读取返回值 boolean**：
    - true → `Ok(TerminateSessionResult{ok=true})`
    - false → `Ok(TerminateSessionResult{ok=false, message=i18n("diagnostics.terminate.session_not_found")})`
  - 权限不足（SQLState 42501）→ `Unsupported`。
- [ ] 测试 GREEN。

### 10.2 optimize_table

- [ ] **RED + GREEN**：
  - `optimizeTablePreview`：schemaName fallback `?? session.schema ?? "public"`；查 `pg_total_relation_size((<schema>.<table>)::regclass)` 拿 currentTotalSize；critical recommendation 用 i18n key `diagnostics.optimize.preview.lock_warning_pg`。
  - `optimizeTable`：用 plain `Statement` 跑 `VACUUM (FULL, VERBOSE) "<schema>"."<table>"`（VACUUM 不能在事务里跑，autoCommit 必须 true，注意 `Connection.setAutoCommit(true)` before）；执行后再查 `pg_total_relation_size` 算 reclaimedBytes 差值。
- [ ] 测试 GREEN。

### 10.3 收尾

- [ ] `supportedCapabilities()` 加 `TERMINATE_SESSION, OPTIMIZE_TABLE`。
- [ ] commit：`feat(infra/postgres): implement terminate_session, optimize_table mutations`

## Task 11: i18n Keys — messages.properties (中英双套)

**目的：** 把 spec §11 列出的 24 个 i18n key 在英文 + 简中两个 properties 文件落地，所有 provider / service 的字面 reason 文案换成 `translator.get(...)` 调用。

- [ ] 在 `server/data-talk-adapter/src/main/resources/messages.properties` 加 24 个新 key（spec §11 表格"示例值（英）"列）。
- [ ] 在 `messages_zh_CN.properties` 加同样 24 个 key（"示例值（中）"列）。
- [ ] 把 Task 5-10 中暂用的 fallback 字符串全部替换成 `translator.get("diagnostics.<x>.<y>")` 调用（含模板参数 `{0}` `{1}` 用 `translator.get(key, args...)` 重载）。
- [ ] 跑 `mvn test -pl data-talk-infrastructure`（H2 + 4 provider 全部通过）。
- [ ] commit：`feat(i18n): add 24 diagnostics & mutation message keys (en + zh-CN)`

## Task 12: Application 层 install 收尾

- [ ] `cd server && mvn install -pl data-talk-application -am -DskipTests`（`-am` 把 domain 也带上，确保 ~/.m2 全是新版本）。
- [ ] `mvn install -pl data-talk-infrastructure -am -DskipTests`（同理）。

## Task 13: Adapter — Rewrite 3 Diagnostic Actions (Batch 3a)

**目的：** 让 `LockInfoAction` / `PoolStatusAction` / `TableSpaceAction` 真正调 `DiagnosticsService`，序列化对齐 `ExplainQueryAction`。

> 三个 action 互相独立，可并行编辑。

### 13.1 LockInfoAction

- [ ] **RED** `LockInfoActionTest`（Mock `DiagnosticsService`）：
  - Service 返回 `Ok(LockReport)` → action JSON `{ blockingChain: [...], recommendations: [...] }`。
  - Service 返回 `Unsupported("...")` → `{ unsupported: true, reason: "..." }`。
  - Service 返回 `DiagnosticError("X", "msg")` → `{ error: { type: "X", message: "msg" } }`。
- [ ] 测试 RED。
- [ ] **GREEN**：
  - 删除现有硬编码 `unsupported` 实现。
  - 注入 `DiagnosticsService diagnosticsService`。
  - `handle()` 内调 `diagnosticsService.lockInfo(ctx.sessionId())`，三态 sealed switch 序列化（参考 `ExplainQueryAction:65-79` 套路）。
  - inputSchema 改为空 object（无入参）。
  - outputSchema 描述 blockingChain / recommendations / unsupported / reason / error 字段。
  - **保留** `@DataTalkAction(id="datatalk.lock_info", executor=SERVER, riskLevel={L1}, category={QUERY}, exposeToMcp=true, requiresConnection=true)`。
- [ ] 测试 GREEN。

### 13.2 PoolStatusAction

- [ ] 同 13.1 形态——RED test → 删 stub → 注入 service → 三态序列化 → outputSchema 描述 9 个字段（scope, active, idle, max, threadsRunning, waiting, identifier, recommendations, unsupported/error）。
- [ ] commit：`feat(adapter): wire LockInfoAction, PoolStatusAction to DiagnosticsService`

### 13.3 TableSpaceAction

- [ ] 同 13.1，但 inputSchema 加 `tables: { type: array, items: { type: string }, optional }`。
- [ ] handle() 接 `tables` 参数（缺省 null）传给 `diagnosticsService.tableSpaceInfo(sessionId, tables)`。
- [ ] **输入校验（spec §7）**：每个 `tables[i]` 用正则 `^[a-zA-Z_][a-zA-Z0-9_]{0,63}$` 校验，不合规返 `{ error: { type: "INVALID_INPUT", message: i18n("diagnostics.error.invalid_table_name") } }`。
- [ ] 测试 GREEN。
- [ ] commit：`feat(adapter): wire TableSpaceAction with tables[] input validation`

## Task 14: Adapter — TerminateSessionConfirmableAction (Batch 3a)

**目的：** 实现 mutation 的二阶段流程，复用 `UpdateConnectionConfirmableAction` 的 SHA-256 token 范式。

- [ ] **RED** `TerminateSessionConfirmableActionTest`（Mock `DiagnosticsService`）：
  - **Phase 1（preview）**：input `{sessionId:"42", confirm:false}` → service.terminateSessionPreview 返回 `Ok(TerminateSessionPreview{...})` → action JSON `{ confirm_required: true, confirmation_token: "<SHA-256>", preview: {...} }`。
  - **Phase 1 self-suicide**：service 返回 `Unsupported("self_termination_blocked")` → `{ unsupported: true, reason: "..." }`。
  - **Phase 2（execute）**：input `{sessionId:"42", confirm:true, confirmationToken:"<correct>"}` → 重算 token 一致 → 调 service.terminateSession → 返回 `{ ok:true, sessionId:"42", message:"..." }`。
  - **Phase 2 token mismatch**：input `{confirmationToken:"wrong"}` → 抛 `IllegalArgumentException("...token_mismatch...")`（与 UpdateConnectionConfirmable 模式一致）。
  - **Phase 2 missing token**：confirm:true 但无 token → 抛 `IllegalArgumentException("...token_required...")`。
  - **Phase 2 session_not_found**：service 返回 `Ok(ok=false)` → action JSON `{ ok:false, sessionId:"42", message:"..." }`。
  - **Input 验证**：`sessionId` 不匹配 `^[0-9]+$|^[0-9]+,[0-9]+$` → `{ error: { type: "INVALID_INPUT", ... } }`。
- [ ] 测试 RED。
- [ ] **GREEN**：
  - 创建 `TerminateSessionConfirmableAction.java`，注解 `@DataTalkAction(id="datatalk.terminate_session", executor=SERVER, timeoutMs=30_000, riskLevel={L2}, category={MUTATION}, exposeToMcp=true, requiresConnection=true)`。
  - inputSchema：`sessionId: string (required)`, `confirm: boolean`, `confirmationToken: string`。
  - outputSchema：`confirm_required, confirmation_token, preview, ok, sessionId, message, unsupported, reason, error`。
  - 输入校验（正则白名单）→ Phase 决策：
    - `confirm == false`（含未传）→ Phase 1：调 `diagnosticsService.terminateSessionPreview`；三态 switch；Ok 时算 token = SHA-256(`sessionId + targetSessionId + willRunSql`)，组装 `{ confirm_required:true, confirmation_token, preview }`。
    - `confirm == true` → Phase 2：重新调 preview 算 token，与 input.confirmationToken 比较；不匹配 throw；匹配后调 execute。
  - Token 算法复用 `UpdateConnectionConfirmableAction:229-` 的 SHA-256 helper（如有 `confirmationToken(...)` 抽 utility）。
- [ ] 测试 GREEN。
- [ ] commit：`feat(adapter): add TerminateSessionConfirmableAction (L2 mutation, two-phase confirm)`

## Task 15: Adapter — OptimizeTableConfirmableAction (Batch 3a)

- [ ] 同 Task 14 形态，input/output 按 spec §9：
  - input：`table: string (required)`, `schemaName: string?, confirm: bool, confirmationToken: string?`。
  - Phase 1 输出：`{ confirm_required, confirmation_token, preview: {engine, table, schemaName, willRunSql, currentDataFree, currentTotalSize}, recommendations: [...] }`（preview.recommendations 提到顶层）。
  - Token = SHA-256(`sessionId + table + schemaName + willRunSql`)。
  - 输入校验：`table` / `schemaName` 用 `^[a-zA-Z_][a-zA-Z0-9_]{0,63}$`。
  - Action handle 把 `OptimizeTablePreview.recommendations` 直接展开到 Phase 1 顶层 `recommendations` 字段（spec §6 安全警告契约）。
- [ ] commit：`feat(adapter): add OptimizeTableConfirmableAction (L2 mutation, two-phase confirm)`

## Task 16: AGENTS.md — Update Runtime Prompt (Batch 3b)

**目的：** 把 spec §11 给定的英文段落写入 AGENTS.md；删除"Not yet available"段；扩充 workflow rules。

> **顺序硬约束**：必须先完成 Task 13-15（action 已注册），否则 `AgentPromptContractTest.runtimePromptReferencesOnlyRegisteredMcpTools` 会失败。

### 16.1 RED — Prompt Contract Test 扩展

- [ ] 在 `AgentPromptContractTest` 加新断言（保持英文）：
  - prompt 含 `datatalk_terminate_session`、`datatalk_optimize_table`、`Mutation Actions`、`Lock complaint received`、`pg_terminate_backend`（不需要——但 `Holder session has been blocking` 必须含）。
  - prompt 不含汉字（已有断言，但确认仍 pass）。
  - prompt 不再含 `Not yet available`。
- [ ] 跑测试 RED。

### 16.2 GREEN — AGENTS.md 编辑

- [ ] 删除 `AGENTS.md` 第 118-119 行 "Not yet available" 段。
- [ ] 在 §"Query Diagnostics" 段插入 `datatalk_lock_info`、`datatalk_pool_status`、`datatalk_table_space` 三条工具说明（spec §11 给的英文文案）。
- [ ] 新增 §"Mutation Actions" 段，含 `datatalk_terminate_session`、`datatalk_optimize_table`（spec §11 给的英文）。
- [ ] 在 §"Diagnostics Workflow Rules" 现有第 6 条后追加第 7-13 条（spec §11 给定）。
- [ ] 跑 `mvn test -pl data-talk-adapter -Dtest=AgentPromptContractTest`，全 GREEN。
- [ ] commit：`feat(prompt): document diagnostics & mutation actions in runtime AGENTS.md`

## Task 17: Closed-Loop Integration Test (Batch 4)

**目的：** 端到端验证 lock_info → terminate_session 与 table_space → optimize_table 两条闭环路径，确保 recommendations 引用的 toolName 真能被调用。

- [ ] 创建 `DiagnosticsClosedLoopIT.java`（`@SpringBootTest`，使用 Mockito 替换 `DiagnosticsProvider` 注入桩 provider）。
- [ ] **场景 1：lock → terminate**：
  - Stub MySQL provider `lockInfo` 返回阻塞链 `wait_ms=8000`。
  - 调 `LockInfoAction.handle`，断言响应含 `recommendations[0].suggestedToolName == "datatalk_terminate_session"` 且 `suggestedActionArgs.sessionId == "<holderId>"`。
  - 用 recommendation 里的参数构造 input，调 `TerminateSessionConfirmableAction.handle`（confirm=false）→ 拿到 token + preview。
  - 二次调 confirm=true + token → execute 成功。
- [ ] **场景 2：table_space → optimize**：
  - Stub `tableSpaceInfo` 返回一张表 `dataSizeBytes=900_000_000, freeSpaceBytes=400_000_000`（30%+ reclaim）。
  - 调 `TableSpaceAction.handle`，断言 `recommendations[0].suggestedToolName == "datatalk_optimize_table"` 且 args.table 正确。
  - 调 `OptimizeTableConfirmableAction.handle` Phase 1 → Phase 2，验证 reclaimedBytes 非 0。
- [ ] commit：`test(adapter): closed-loop diagnostics→mutation integration tests`

## Task 18: Final Verification (Batch 4)

- [ ] `cd server && mvn clean verify` 全模块全测试（含 application 层 + infra 单测 + adapter IT）。
- [ ] 手工 smoke：
  - 启动 `mvn spring-boot:run -pl data-talk-adapter`。
  - 用 OpenCode 客户端依次调 `datatalk_lock_info`（应返回 OK，blockingChain 可能为空但不再 unsupported）、`datatalk_pool_status`、`datatalk_table_space`。
  - 调 `datatalk_terminate_session` 用一个不存在的 sessionId 走 confirm 流程，验证 ok=false 路径。
  - 调 `datatalk_optimize_table` 走一张测试表，验证 reclaimedBytes 字段正常返回。
- [ ] commit：`chore(verify): run mvn verify and manual smoke for diagnostics & mutations`（如无 source 改动，省略此 commit）。

## Task 19: Documentation Housekeeping (Batch 4 收尾)

> CLAUDE.md "Post-Execution Document Housekeeping" 强制要求：plan 完成后必须做这些。

- [ ] 把本计划文件中的所有 `- [ ]` 改成 `- [x]`，加 `## Status Notes` 章节列出实际偏差（参考其他完成 plan 的 Status Notes 风格）。
- [ ] `docs/exec-plans/index.md`：把本计划从「活跃计划」表移到「已完成计划」表，加完成日期与简要总结。
- [ ] `docs/product-specs/index.md`：把 Diagnostics & Mutation Design 从 "Active" 段移到 Completed 段（如果索引按状态分段）；如果索引仅是单表，更新摘要无需移动（已是单表，跳过 move 步骤，确保摘要准确）。
- [ ] `docs/product-specs/2026-04-29-diagnostics-mutation-design.md`：顶部 `状态: Active` 改 `状态: Completed`。
- [ ] `~/.data-talk/opencode/datatalk-tools-test-report.md`：把 §7 "不可用工具" 段重写——3 个工具不再 unsupported（除 H2 / Oracle 引擎外），新增 2 个 mutation 工具到测试覆盖；`成功率统计` 调整为 26 个真实工具 + 0 个 stub。
- [ ] commit：`docs(housekeeping): mark diagnostics & mutation plan completed; sync indexes & test report`
- [ ] push：把所有 commit 推到 remote develop 分支。

---

## Status Notes

(由执行 agent 在完成时填写：实际偏差、跳过的步骤、新发现的问题、必须更新的 canonical docs)
