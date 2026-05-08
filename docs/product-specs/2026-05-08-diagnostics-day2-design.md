# Diagnostics Day-2 设计:11 个新增 kind 的 EXPLAIN 与 INDEX_HINTS 真实化

| 字段 | 值 |
|---|---|
| 日期 | 2026-05-08 |
| 状态 | Draft → 待 user 审阅 |
| 作者 | brainstorming session |
| 适用范围 | server/data-talk-application + server/data-talk-infrastructure(only)+ docs |
| 前置文档 | [DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md) §Diagnostics Compatibility Checklist;Wave A/B/C 各 kind 的 child design |
| 取代关系 | 不取代任何 child spec;补充各 child spec 的 "Day-2: EXPLAIN diagnostics" 待办项 |

## 1. Scope

把 Wave A/B/C 已 ship 的 11 个 kind 的 `Diagnostics.explain` 与 `Diagnostics.indexHints` 从 `structured unsupported` 升级为真实 EXPLAIN / 索引推荐。

其余 5 个 capability(`LOCK_INFO / POOL_STATUS / TABLE_SPACE / TERMINATE_SESSION / OPTIMIZE_TABLE`)在这 11 个 kind 上**继续保持 structured unsupported**,不在本 spec 范围。Day-3 另立 spec。

11 个 kind × 2 capability 矩阵:

| Kind | EXPLAIN | INDEX_HINTS | EXPLAIN 输出格式 | EXPLAIN SQL |
|---|---|---|---|---|
| sqlite | ✅ 真实 | ✅ 真实 | text(JDBC 行集 detail 列) | `EXPLAIN QUERY PLAN <sql>` |
| sqlserver | ✅ 真实 | ✅ 真实 | xml | `SET SHOWPLAN_XML ON;` 然后 user_sql |
| mariadb | ✅ 真实(已通过 MySQL provider 复用) | ✅ 真实(同) | json | 同 MySQL `EXPLAIN FORMAT=JSON` |
| tidb | ✅ 真实 | ✅ 真实 | tabular | `EXPLAIN <sql>`(JDBC 行集 5 列) |
| duckdb | ✅ 真实 | ⛔ unsupported(reason) | text | `EXPLAIN <sql>` |
| clickhouse | ✅ 真实 | ⛔ unsupported(reason) | text | `EXPLAIN PLAN <sql>` |
| apache_doris | ✅ 真实 | ⛔ unsupported(reason) | text(JDBC 单列多行) | `EXPLAIN <sql>` |
| starrocks | ✅ 真实 | ⛔ unsupported(reason) | text(同 doris) | `EXPLAIN <sql>` |
| presto | ✅ 真实 | ⛔ unsupported(reason) | text | `EXPLAIN (TYPE LOGICAL) <sql>` |
| trino | ✅ 真实 | ⛔ unsupported(reason) | text | `EXPLAIN (TYPE LOGICAL) <sql>` |
| hive | ✅ 真实 | ⛔ unsupported(reason) | text(STAGE PLANS) | `EXPLAIN <sql>` |

### 1.1 强约束

- **EXPLAIN 一律不执行 user_sql**。所有 kind 选择"只规划不执行"的语法变体。SQL Server 通过 `SET SHOWPLAN_XML ON` 实现;DuckDB / TiDB 不加 `ANALYZE`;ClickHouse 用 `EXPLAIN PLAN` 而非 `EXPLAIN PIPELINE/ESTIMATE`;Trino/Presto 用 `TYPE LOGICAL` 而非 `DISTRIBUTED/IO/VALIDATE`。
- **INDEX_HINTS 仅行存 4 家(sqlite/sqlserver/mariadb/tidb)推荐 BTREE 二级索引**。OLAP/联邦/数仓 7 家结构化引导(per-kind reason 文案),不做方言化推荐。
- **每 kind 一个独立 provider**(方案 A)。不抽 ExplainParser 协议派系基类,只在 `AbstractDiagnosticsProvider` 加 5 个共享 helper。

### 1.2 Out of Scope(明确不做)

- ANALYZE / 实际执行查询的 EXPLAIN 路径(全 kind 统一禁,不引入 opt-in 开关)
- ClickHouse `EXPLAIN PIPELINE / ESTIMATE / SYNTAX`、Hive `EXPLAIN VECTORIZATION / DEPENDENCY`、Trino/Presto `TYPE DISTRIBUTED / IO / VALIDATE`
- LOCK_INFO / POOL_STATUS / TABLE_SPACE / TERMINATE_SESSION / OPTIMIZE_TABLE 在新 kind 上的真实化
- TiDB Statement Summary / ADMIN SHOW DDL(留给 LOCK / long-running 范畴的 Day-3)
- OLAP/联邦/数仓 7 家的方言化索引推荐(rollup / sort key / inverted index / partition / data skipping index)
- 前端 ExplainPlanCard 视觉改造(本 spec 不动前端,除 i18n 字符串)

## 2. Architecture

### 2.1 Domain 层

**改动:0**。`DiagnosticCapability` 7 项不变;`DiagnosticResult` / `ExplainPlan` / `ExplainNode` / `ScanType` / `IndexRecommendation` / `Impact` 数据形状已能承载所有 kind 的输出。

`ExplainPlan(dialect, rawText, nodes, cost, warnings)` 中:
- `dialect`:填 `conn.kind()` 的 canonical 值(`"sqlite"` / `"apache_doris"` 等)
- `rawText`:实质是"原始 payload",承载 JSON / XML / 文本 / 行集序列化为 JSON 数组皆可。前端当作不透明字符串展示。

### 2.2 Application 层

**改动:1 处**。

- `DiagnosticsService.unsupportedReason(kind, capability)`:`lock / pool / space / terminate / optimize` 五个分支保持现状(继续 unsupported);**不需要为 explain 加 fallback**——11 家 explain 现在都成功,unsupported 不再走这里。
- `DiagnosticsService.indexHints(...)` 路径中,7 家 OLAP/联邦/数仓 kind 的 `provider.indexHints` 调用会返回 `Unsupported(per-kind reason)`,Service 层不做拦截(provider 自管 reason)。

### 2.3 Infrastructure 层(主要工作量)

目录:`server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/`

#### 2.3.1 `AbstractDiagnosticsProvider` 新增 5 个 protected helper

```java
protected ScanType parseScanType(String dialectToken, Map<String, ScanType> overrides);

protected List<ExplainNode> mapTabularPlanToNodes(
    List<Map<String,Object>> rows,
    TabularLayout layout
);

protected List<ExplainNode> mapTextPlanToNodes(
    String rawText,
    TextPlanGrammar grammar
);

protected List<ExplainNode> mapXmlPlanToNodes(String rawXml);   // SQL Server 专用,放基类便于测试

protected <T> DiagnosticResult<T> mapPermissionOrDriverError(
    SQLException e,
    String capability,
    String kind
);
```

`TabularLayout` / `TextPlanGrammar` 是新加的小 record:

```java
public record TabularLayout(
    String idCol,           // 节点 id 列(含 ASCII 树前缀,如 "└─")
    String parentCol,       // 可选 parent id 列(SQLite),null 时按 id 列前缀缩进推断
    String operatorPattern, // 从 id 列提取 operator 的正则(如 "(\\w+)_\\d+" for TiDB)
    String rowsCol,         // 行数列(估算)
    String objectCol,       // 表 / 对象列
    String infoCol          // 操作信息列
);

public record TextPlanGrammar(
    String name,                              // grammar 名,便于错误定位
    Function<String, Integer> indentFn,       // 行 → 缩进层级
    Function<String, String> operatorFn,      // 行 → operator 名
    Function<String, Optional<String>> tableFn,    // 行 → 提取表名(可选)
    Function<String, Optional<Long>> rowsFn        // 行 → 提取行数(可选)
);
```

`MySqlDiagnosticsProvider.parseQueryBlock`(line 301-319)上移为 `AbstractDiagnosticsProvider.parseMySqlJsonPlan`,MySQL 现有 provider 改 `super.parseMySqlJsonPlan(...)` 调用——这是项目唯一一处对现有 first-class provider 的内部重构。

#### 2.3.3 基类 helper 不适用的 provider:SqlServer

`AbstractDiagnosticsProvider.queryForList(...)` 每次调用都自管 connection 生命周期(`try-with-resources` 开/关 connection)。**SqlServerDiagnosticsProvider.explain 不能复用 `queryForList`**:`SET SHOWPLAN_XML ON; <user_sql>; SET SHOWPLAN_XML OFF` 必须在**同一个 JDBC connection 内顺序执行**(SHOWPLAN_XML 是 session-scoped 设置),provider 需要自行用 `openConnection()` + `Statement` 管理生命周期(详见 §3.1.2 伪码)。其他 10 个 provider 的 explain 都是单条 SQL 调用,正常走 `queryForList`。

#### 2.3.2 11 个 provider 修改类型

| Provider | 修改 | 行数估算(净增) |
|---|---|---|
| `MySqlDiagnosticsProvider` | 仅 `parseQueryBlock` 上移 | 0 净增 |
| `MariaDbDiagnosticsProvider` | **不存在,不创建**——继续走 MySQL provider 的 `supportedDriverTypes` 包含 `"mariadb"` | 0 |
| `SqliteDiagnosticsProvider` | 替换 explain/indexHints 桩 → 真实 | +180 |
| `SqlServerDiagnosticsProvider` | 替换 explain/indexHints 桩 → 真实(XML 解析) | +220 |
| `DuckDbDiagnosticsProvider` | 替换 explain 桩;indexHints 改 unsupported(带 reason) | +120 |
| `ClickHouseDiagnosticsProvider` | 同上 | +120 |
| `DorisDiagnosticsProvider` | 替换 explain 桩(text);indexHints unsupported | +130 |
| `StarrocksDiagnosticsProvider` | 同 Doris(grammar 复用) | +130 |
| `PrestoDiagnosticsProvider` | 替换 explain 桩(text);indexHints unsupported | +120 |
| `TrinoDiagnosticsProvider` | 同 Presto(grammar 复用) | +120 |
| `HiveDiagnosticsProvider` | 替换 explain 桩(text, STAGE PLANS);indexHints unsupported | +140 |
| `TiDbDiagnosticsProvider` | 替换 explain/indexHints 桩(tabular,行存) | +200 |

合计净增约 1500 行(provider 主体)。基类 helper 约 +250 行。`SqlColumnExtractor`(已存在)被 sqlite / sqlserver / mariadb / tidb 4 家共用做"WHERE/JOIN 列提取 → 推荐 BTREE"。

### 2.4 Adapter 层

**改动:0**。`ExplainQueryAction` / `IndexHintsAction` controller 逻辑不变;MCP `datatalk_explain_query` / `datatalk_index_hints` 工具 schema 不变。

`messages.properties` / `messages_zh_CN.properties` 新增 i18n keys 见 §4.4。

### 2.5 Frontend 层

**唯一改动:** `client/src/i18n/messages.ts` 加 11 条 `diagnostics.dialect.<kind>` 显示标签(用于 ExplainPlanCard 顶部 Dialect 标签):
```
diagnostics.dialect.sqlite        = "SQLite"
diagnostics.dialect.sqlserver     = "SQL Server"
diagnostics.dialect.mariadb       = "MariaDB"
diagnostics.dialect.tidb          = "TiDB"
diagnostics.dialect.duckdb        = "DuckDB"
diagnostics.dialect.clickhouse    = "ClickHouse"
diagnostics.dialect.apache_doris  = "Apache Doris"
diagnostics.dialect.starrocks     = "StarRocks"
diagnostics.dialect.presto        = "Presto"
diagnostics.dialect.trino         = "Trino"
diagnostics.dialect.hive          = "Apache Hive"
```

`ExplainPlanCard` / `IndexRecommendationsList` / `DiagnosticsCard` 渲染逻辑不动——已能渲染 ok / unsupported / error 三态。

### 2.6 文档同步

见 §6。

## 3. 逐 kind 实现细节

### 3.1 行存 4 家(EXPLAIN + INDEX_HINTS 都做)

#### 3.1.1 sqlite

- **EXPLAIN SQL:** `EXPLAIN QUERY PLAN <user_sql>`
- **输出格式:** JDBC 行集,列 `id / parent / notused / detail`
- **解析:** `mapTabularPlanToNodes` + `TabularLayout(idCol="id", parentCol="parent", detailCol="detail")`
- **ScanType 映射(基于 detail 列子串):**
  - 含 `"SCAN "` 且不含 `"USING INDEX"` → `FULL_SCAN`
  - 含 `"SEARCH "` + `"USING INDEX"` 且 detail 包含 `(=` → `REF`
  - 含 `"SEARCH "` + `"USING INDEX"` → `INDEX_RANGE`
  - 含 `"USING COVERING INDEX"` → `INDEX_SCAN`
  - 含 `"USING ROWID"` 或 `"USING INTEGER PRIMARY KEY"` → `CONST`
  - 其他 → `OTHER`
- **表名提取:** 正则 `(?:SCAN|SEARCH) (\w+)` 从 detail 提取
- **INDEX_HINTS:** FULL_SCAN 节点 → `SqlColumnExtractor.extract(sql, table)` → 推荐 BTREE。SQLite 不返回行数 → `Impact.MEDIUM` 一律
- **权限/错误:** 无权限模型,错误归 `EXPLAIN_ERROR`
- **测试:** `jdbc:sqlite::memory:` fixture(`orders(id, user_id, amount)` + `idx_user`),3 条 SQL 断言

#### 3.1.2 sqlserver

- **EXPLAIN SQL 序列(同一 connection 内):**
  ```sql
  SET SHOWPLAN_XML ON;
  <user_sql>;
  SET SHOWPLAN_XML OFF;
  ```
- **关键约束:**
  - 同一 JDBC connection 内顺序执行三句(`SHOWPLAN_XML` 是 session-scoped)
  - 第二句返回 XML 字符串(单列结果集),**不会执行 user_sql**(包括 DML/DDL)
  - **不走基类 `queryForList`**:基类 helper 每次调用都开关 connection,无法跨 statement 保持 SHOWPLAN_XML 设置。Provider 自管生命周期:
    ```java
    try (Connection c = openConnection(conn, decryptedPassword);
         Statement stmt = c.createStatement()) {
        stmt.execute("SET SHOWPLAN_XML ON");
        try (ResultSet rs = stmt.executeQuery(userSql)) {
            if (rs.next()) rawXml = rs.getString(1);   // XML,不执行 userSql
        }
        try { stmt.execute("SET SHOWPLAN_XML OFF"); } catch (SQLException ignored) {}
    }
    ```
- **XML 解析:** JDK 自带 `javax.xml.parsers.DocumentBuilder`(0 新增依赖)
  - `//RelOp` 节点 → 一个 `ExplainNode`
  - `@PhysicalOp` → operator
  - `@EstimateRows` → rows
  - `@EstimatedTotalSubtreeCost` → cost
  - 嵌套 `RelOp` → children
- **ScanType 映射(@PhysicalOp):**
  - `Table Scan` → `FULL_SCAN`
  - `Clustered Index Scan` / `Index Scan` → `INDEX_SCAN`
  - `Clustered Index Seek` / `Index Seek` + `SeekPredicates` 等值 → `REF`
  - `Clustered Index Seek` / `Index Seek` 范围 → `INDEX_RANGE`
  - `Constant Scan` → `CONST`
  - 其他(`Hash Match` / `Sort` / etc.)→ `OTHER`
- **INDEX_HINTS:** `Table Scan` 节点 → `Object/@Table` 提取表名 → `SqlColumnExtractor` → 推荐 BTREE。Impact:`EstimateRows > 10000 = HIGH, > 100 = MEDIUM, else LOW`
- **权限/错误:** 普通用户需要数据库级 `SHOWPLAN` 权限。SQL Server 错误码 `262` → `unsupported(diagnostics.explain.unsupported.sqlserver_permission)`
- **测试策略:** `parseSqlServerShowplanXml(String)` 抽为基类静态方法,纯字符串 fixture 测试 6 个场景。集成测试 `@Disabled` 留 testcontainers 钩子

#### 3.1.3 mariadb

- **实现:** 0 行新代码——`MySqlDiagnosticsProvider.supportedDriverTypes()` 已包含 `"mariadb"`
- **Day-2 工作:**
  1. `MariaDbDiagnosticsCompatibilityIT @Disabled` testcontainers 子类,真连 mariadb 跑 `EXPLAIN FORMAT=JSON` 验证 `parseMySqlJsonPlan` 在 MariaDB 输出上能解析
  2. 验证 INDEX_HINTS 推荐路径与 MySQL 一致
  3. 修订 COMPATIBILITY 表 mariadb 行的 Diagnostics 描述:从 "compatibility verification" 改为 "verified — reuses MySqlDiagnosticsProvider via supportedDriverTypes"
- **风险:** MariaDB 10.4+ 引入 `analyze: true` / `r_filtered` 字段。Jackson 默认容忍未知字段,测试覆盖

#### 3.1.4 tidb

- **EXPLAIN SQL:** `EXPLAIN <user_sql>`(不加 `ANALYZE`)
- **输出格式:** JDBC 行集,5 列:`id / estRows / task / access object / operator info`
- **示例:**
  ```
  id                       estRows    task        access object       operator info
  TableReader_7            3323.33    root                            data:Selection_6
  └─Selection_6            3323.33    cop[tikv]                       lt(test.t.a, 1)
    └─TableFullScan_5      10000.00   cop[tikv]   table:t             keep order:false
  ```
- **解析:** `mapTabularPlanToNodes` + `TabularLayout`,operator 提取正则 `(\w+)_\d+`,树结构按 id 列前导 ASCII(`└─` / `├─` / `│ `)缩进推断
- **ScanType 映射(operator 名):**
  - `TableFullScan` → `FULL_SCAN`
  - `IndexFullScan` → `INDEX_SCAN`
  - `IndexRangeScan` / `IndexLookUp` / `TableRangeScan` → `INDEX_RANGE`
  - `PointGet` / `BatchPointGet` → `CONST`
  - `IndexJoin` 内的 inner-side `IndexRangeScan` → `REF`
  - 其他(`Selection` / `Projection` / `HashAgg` / `Sort`)→ `OTHER`
- **表名:** `access object` 列格式 `table:t` 或 `table:t, partition:p0`
- **INDEX_HINTS:** `TableFullScan` 节点 → 推荐 BTREE。Impact:`estRows > 10000 = HIGH, > 1000 = MEDIUM, else LOW`
- **权限/错误:** EXPLAIN 不需要特殊权限。`Access denied` 错误 → `unsupported(diagnostics.explain.unsupported.tidb_permission)`
- **测试:** `parseTidbExplainRows(List<Map<String,Object>>)` 静态方法纯单测 + `@Disabled` 集成钩子

### 3.2 OLAP / 联邦 / 数仓 7 家(EXPLAIN 真实,INDEX_HINTS unsupported)

#### 3.2.1 duckdb

- **EXPLAIN SQL:** `EXPLAIN <user_sql>`
- **输出格式:** 单行单列文本,缩进算子树(用 `┌`/`└` 框线)
- **解析:** `mapTextPlanToNodes` + `TextPlanGrammar.duckDb()`。按 `┌`/`└` 切块,块内首行非框线行是 operator 名
- **ScanType:** `SEQ_SCAN` → `FULL_SCAN`,`INDEX_SCAN` / `INDEX_LOOKUP` → `INDEX_SCAN`,其他 → `OTHER`
- **INDEX_HINTS:** `unsupported(diagnostics.index_hints.unsupported.duckdb)`
- **权限/错误:** 无权限模型,错误归 `EXPLAIN_ERROR`
- **测试:** `jdbc:duckdb::memory:` 内嵌真连——**唯一与 SQLite 并列的纯单测内闭环验证 kind**

#### 3.2.2 clickhouse

- **EXPLAIN SQL:** `EXPLAIN PLAN <user_sql>`(不执行)
- **输出格式:** JDBC 行集,单列 `explain`,值是缩进文本树
- **关键 token:** `Expression` / `ReadFromMergeTree (database.table)` / `Aggregating` / `Sorting`,缩进 2 空格 = 一层
- **ScanType:**
  - `ReadFromMergeTree` + 子块 `PrimaryKey` 中 `Granules: N/M` 中 N==M → `FULL_SCAN`
  - `ReadFromMergeTree` + `Granules: N/M` 中 N<<M → `INDEX_RANGE`
  - `ReadFromStorage` / `ReadFromBuffer` → `OTHER`
- **行数:** 从 `Granules: N/M` 推算(每 granule 默认 8192 行)
- **INDEX_HINTS:** `unsupported(diagnostics.index_hints.unsupported.clickhouse)`
- **权限/错误:** 错误码 `497` (`ACCESS_DENIED`)→ `unsupported(...permission)`
- **测试:** 纯文本解析单测 + `@Disabled` 集成钩子

#### 3.2.3 apache_doris

- **EXPLAIN SQL:** `EXPLAIN <user_sql>`
- **输出格式:** JDBC 行集,**单列 `Explain String`**(列名带空格),内容是多行文本(多 `PLAN FRAGMENT` 块)
- **解析:** `String.join("\n", rows)` 拼回 → `mapTextPlanToNodes` + `TextPlanGrammar.doris()`
- **关键 token:** 行首 `<数字>:<OperatorName>` 是节点(如 `0:OlapScanNode`、`2:HASH JOIN`),`TABLE:` `cardinality=` `PREAGGREGATION:` `ROLLUP:` 是属性
- **ScanType:**
  - `OlapScanNode` + `PREAGGREGATION: ON` + 无 `PREDICATES` → `FULL_SCAN`
  - `OlapScanNode` + `PREDICATES` + 命中非 base table 的 `ROLLUP` → `INDEX_SCAN`
  - `OlapScanNode` + `PREDICATES` → `INDEX_RANGE`
  - 其他(`EXCHANGE` / `SORT` / `AGGREGATION`)→ `OTHER`
- **行数:** 从 `cardinality=N` 提取
- **INDEX_HINTS:** `unsupported(diagnostics.index_hints.unsupported.apache_doris)`
- **权限/错误:** 默认 SELECT 权限即可

#### 3.2.4 starrocks

- **EXPLAIN SQL:** `EXPLAIN <user_sql>`
- **输出格式:** 与 Doris 高度相似(StarRocks fork 自 Doris)
- **解析:** **复用 `TextPlanGrammar.doris()`**——grammar 共享,provider class 仍独立(方案 A 原则:每 kind 一个 provider)
- **ScanType / INDEX_HINTS / 测试:** 同 Doris
- **INDEX_HINTS 文案差异:** `diagnostics.index_hints.unsupported.starrocks` 文案聚焦 sort key / bitmap index / bloom filter / PREAGGREGATION

#### 3.2.5 / 3.2.6 presto / trino

- **EXPLAIN SQL:** `EXPLAIN (TYPE LOGICAL) <user_sql>`
- **输出格式:** 单行单列文本,Fragment 树,前导 `- ` 表层级
- **解析:** `mapTextPlanToNodes` + `TextPlanGrammar.trino()`(presto 与 trino 共享)。前导 `- ` 计数 / 2 为深度,operator 取 `- ` 后到 `[` 或空格之前的 token
- **ScanType:**
  - `TableScan[connector:db.table]` → `FULL_SCAN`(逻辑计划无下推过滤,默认全扫)
  - `RemoteExchange` / `LocalExchange` / `Aggregate` / `Filter` / `Project` → `OTHER`
- **关键 warning:** Provider 在 ExplainPlan.warnings 加一行 `diagnostics.warning.federated_connector_pushdown` = "Trino/Presto 计划展示的 TableScan 实际可能被底层 connector 下推优化;请到底层数据源(如 Hive/Iceberg)EXPLAIN 验证。"
- **INDEX_HINTS:** `unsupported(diagnostics.index_hints.unsupported.<presto|trino>)`(指向底层 connector)
- **权限/错误:** 错误码 message 含 `Access Denied` → `unsupported(...permission)`

#### 3.2.7 hive

- **EXPLAIN SQL:** `EXPLAIN <user_sql>`(不加 `EXTENDED` / `DEPENDENCY` / `VECTORIZATION`)
- **输出格式:** JDBC 行集,单列 `Explain`,值是多行文本(`STAGE DEPENDENCIES` + `STAGE PLANS` 多阶段)
- **解析:** `mapTextPlanToNodes` + `TextPlanGrammar.hive()`。STAGE PLANS 段下每个 Stage 平铺为 ExplainNode 列表 + 每棵子树。缩进 2 空格 = 一层
- **关键 token:** `TableScan` / `Filter Operator` / `Select Operator` / `Reduce Output Operator` / `Group By Operator`(行首 token);`alias:` `predicate:` `Statistics: Num rows:` `(type:` 是属性
- **ScanType(Day-2 简化版):**
  - `TableScan` → `FULL_SCAN`(不区分 partition pruning,因为需要 schema discovery 配合)
  - 其他 → `OTHER`
- **关键 warning:** `diagnostics.warning.hive_partition_check` = "请人工核对谓词是否包含分区列,以确认是否触发 partition pruning。"
- **INDEX_HINTS:** `unsupported(diagnostics.index_hints.unsupported.hive)`
- **权限/错误:** message 含 `Permission denied` 或 `HiveAccessControlException` → `unsupported(...permission)`

## 4. 错误模型与权限映射

### 4.1 三态契约(不变)

`DiagnosticResult<T>` sealed:`Ok(T)` / `Unsupported(reason)` / `DiagnosticError(errorType, message)`。前端 `ExplainPlanCard` / `IndexRecommendationsList` 已能渲染三态。

### 4.2 三态使用规则

| 场景 | 应该返回 |
|---|---|
| EXPLAIN 成功,有解析后的节点 | `Ok(ExplainPlan)`(节点列表可空) |
| user_sql 语法错误 | `DiagnosticError("EXPLAIN_SYNTAX_ERROR", msg)` |
| 权限不足 | `Unsupported(per-kind reason)` |
| 驱动连接失败 / 网络错误 | `DiagnosticError("CONNECTION_ERROR", msg)` |
| Provider 内部解析异常(XML 损坏 / 文本未识别) | `DiagnosticError("EXPLAIN_PARSE_ERROR", msg + raw payload 前 500 字符)` |
| 7 家 OLAP 的 indexHints | `Unsupported(per-kind reason)` |

**纪律:** 解析失败必须显式返回 `EXPLAIN_PARSE_ERROR`,不得返回空 nodes 假装成功。

### 4.3 权限错误识别

`mapPermissionOrDriverError(SQLException, capability, kind)` 集中识别:

| Kind | 识别条件 | 归一为 |
|---|---|---|
| sqlite | N/A | `EXPLAIN_ERROR` |
| sqlserver | SQLState `42000` + ErrorCode `262` | `unsupported(diagnostics.explain.unsupported.sqlserver_permission)` |
| mariadb | 由 MySQL provider 处理(已存在) | 现状 |
| tidb | SQLState `28000` 或 message 含 `Access denied` | `unsupported(diagnostics.explain.unsupported.tidb_permission)` |
| duckdb | N/A | `EXPLAIN_ERROR` |
| clickhouse | ErrorCode `497`(`ACCESS_DENIED`) | `unsupported(diagnostics.explain.unsupported.clickhouse_permission)` |
| apache_doris | message 含 `Access denied for user` | `unsupported(diagnostics.explain.unsupported.doris_permission)` |
| starrocks | 同 doris | `unsupported(diagnostics.explain.unsupported.starrocks_permission)` |
| presto | message 含 `Access Denied`(Presto 大写) | `unsupported(diagnostics.explain.unsupported.presto_permission)` |
| trino | message 含 `Access Denied` | `unsupported(diagnostics.explain.unsupported.trino_permission)` |
| hive | message 含 `Permission denied` 或 `HiveAccessControlException` | `unsupported(diagnostics.explain.unsupported.hive_permission)` |

### 4.4 i18n keys 清单

新增到 `messages.properties` + `messages_zh_CN.properties`(具体文件名以仓库实际路径为准):

```
# INDEX_HINTS unsupported per-kind 引导
diagnostics.index_hints.unsupported.duckdb
diagnostics.index_hints.unsupported.clickhouse
diagnostics.index_hints.unsupported.apache_doris
diagnostics.index_hints.unsupported.starrocks
diagnostics.index_hints.unsupported.presto
diagnostics.index_hints.unsupported.trino
diagnostics.index_hints.unsupported.hive

# EXPLAIN 权限不足 per-kind
diagnostics.explain.unsupported.sqlserver_permission
diagnostics.explain.unsupported.tidb_permission
diagnostics.explain.unsupported.clickhouse_permission
diagnostics.explain.unsupported.doris_permission
diagnostics.explain.unsupported.starrocks_permission
diagnostics.explain.unsupported.presto_permission
diagnostics.explain.unsupported.trino_permission
diagnostics.explain.unsupported.hive_permission

# Warnings(随 ExplainPlan.warnings 返回)
diagnostics.warning.federated_connector_pushdown   # trino + presto 共用
diagnostics.warning.hive_partition_check
```

合计 **28 条 key × 2 语言 = 56 条文案**:
- **后端 17 条**:7 条 INDEX_HINTS unsupported per-kind + 8 条 EXPLAIN 权限 per-kind + 2 条 warning(`federated_connector_pushdown` / `hive_partition_check`)。
- **前端 11 条**:`client/src/i18n/messages.ts` 新增 `diagnostics.dialect.<kind>`(见 §2.5)。

中文/英文文案骨架(spec 不固化最终措辞,留 implementation 阶段定稿):

- `diagnostics.index_hints.unsupported.duckdb` ≈ "DuckDB 列存通常无需手工创建 B-tree 索引。如行扫描偏多,优先检查 EXPLAIN 中 zone map 命中情况。"
- `diagnostics.index_hints.unsupported.clickhouse` ≈ "ClickHouse 通过 ORDER BY 主键和 data skipping index 优化扫描,而非 B-tree 二级索引。请使用 `EXPLAIN PLAN` 检查分区裁剪和 index granule 命中率。"
- `diagnostics.index_hints.unsupported.apache_doris` ≈ "Apache Doris 通过 ROLLUP / 物化视图 / inverted index 优化扫描。请检查 EXPLAIN 中 ROLLUP 命中情况。"
- `diagnostics.index_hints.unsupported.starrocks` ≈ "StarRocks 通过 sort key / bitmap index / bloom filter 优化扫描,而非 B-tree 索引。请检查 EXPLAIN 中 PREAGGREGATION 与 rollup 命中。"
- `diagnostics.index_hints.unsupported.presto` ≈ "Presto 不存储数据,索引由底层 connector 决定。请到底层数据源(如 Hive / Iceberg / MySQL connector)检查索引。"
- `diagnostics.index_hints.unsupported.trino` ≈ "Trino 不存储数据,索引由底层 connector 决定。请到底层数据源(如 Hive / Iceberg / MySQL connector)检查索引。"
- `diagnostics.index_hints.unsupported.hive` ≈ "Hive 性能优化以 partitioning / bucketing 为主,而非 B-tree 索引。请检查 partition pruning。"
- `diagnostics.warning.federated_connector_pushdown` ≈ "Trino/Presto 计划展示的 TableScan 实际可能被底层 connector 下推优化;请到底层数据源(如 Hive/Iceberg)EXPLAIN 验证。"
- `diagnostics.warning.hive_partition_check` ≈ "Hive EXPLAIN 未自动识别分区裁剪。请人工核对谓词是否包含分区列;必要时使用 `EXPLAIN EXTENDED` 查看详细分区信息。"

## 5. 测试策略

### 5.1 测试金字塔

| 层级 | 范围 | 工具 | CI 默认 |
|---|---|---|---|
| L1 单测(纯解析) | `parseSqlServerShowplanXml` / `parseTidbExplainRows` / `parseDuckDbTextPlan` 等 11 个静态解析方法 | JUnit 5 + AssertJ + 文本 / XML / Map fixture | ✅ 必跑 |
| L2 单测(provider 业务) | `XxxDiagnosticsProvider` capability 矩阵、错误归一化、unsupported reason | JUnit 5,fixture 由 L1 喂入 | ✅ 必跑 |
| L3 内嵌驱动集成 | sqlite + duckdb 真连(JVM 内嵌) | `jdbc:sqlite::memory:` / `jdbc:duckdb::memory:` | ✅ 必跑 |
| L4 testcontainers 集成 | sqlserver / tidb / clickhouse / doris / starrocks / presto / trino / hive | testcontainers 模块 | ⚠ `@Disabled`,本地手动 |
| L5 mariadb 兼容验证 | mariadb testcontainer | testcontainers mariadb | ⚠ `@Disabled` |

### 5.2 L1 解析测试 fixture 矩阵(每个解析方法至少 6 个)

```
1. happy path - 单表全扫
2. happy path - 索引扫描(行存 4 家 / OLAP 形态)
3. happy path - 嵌套(JOIN / Aggregate / Sort 子树)
4. edge - 空计划 / 空 fragment
5. edge - 未识别的 operator(grammar 优雅降级为 OTHER)
6. error - 损坏的 raw payload(XML 缺尾标签 / 文本截断 / 行集列名缺失)
```

**Fixture 来源:** 直接复制各官方文档 / 仓库 issue 的 EXPLAIN 输出,落到 `src/test/resources/diagnostics/<kind>/<scenario>.txt|xml|json`。**不要手写**——容易写错语法,改 grammar 也无法暴露真实 bug。

### 5.3 L2 provider 测试要点(每 provider)

```java
@Test void explain_returnsOk_whenPlanParses();
@Test void explain_returnsParseError_whenPayloadCorrupted();
@Test void explain_returnsUnsupported_whenPermissionDenied();
@Test void indexHints_returnsRecommendations_forFullScanNode();           // 仅行存 4 家
@Test void indexHints_returnsEmptyList_whenAllIndexed();                  // 仅行存 4 家
@Test void indexHints_returnsUnsupportedWithKindReason_forOlapKinds();    // 7 家
@Test void supportedDriverTypes_containsCanonicalKind();
@Test void supportedCapabilities_containsExplainAndConditionallyIndexHints();
```

### 5.4 L4/L5 testcontainers 钩子

建立 `DiagnosticsTestcontainersIT` 抽象基类,各 kind 一个 `@Disabled("manual smoke - enable when running against real container")` 子类。子类实现 `containerImage()` 与 `setupSchema()`。**这是 Day-2 的明确取舍**——不阻塞 CI,但留下"任何工程师本地一行命令可跑"的勾子。

### 5.5 回归测试

- `DiagnosticsServiceTest` 加 11 条 `routesToCorrectProvider` 断言(改写现有的 routing 矩阵)
- `DiagnosticsClosedLoopIT` 把 11 个 kind 的 ok / unsupported / error 三态各跑一遍(用 mock provider 注入)

### 5.6 测试规模估算

- 11 个新 `XxxDiagnosticsProviderTest`(每个 ~12 测试)= ~130 测试
- 11 个 fixture 目录 × 6 fixture = 66 fixture 文件
- 11 个 `DiagnosticsTestcontainersIT @Disabled` 子类
- `DiagnosticsServiceTest` routing 断言矩阵改写
- `MariaDbCompatibilityIT @Disabled`

## 6. 文档同步

### 6.1 必改

1. **`docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`**:
   - "Current Support Snapshot" 表里 11 个 kind 的描述全部改写,删除 "structured unsupported diagnostics",改为具体的 Day-2 状态描述。每个 kind 行都需要包含两个新增片段:
     - `Day-2 EXPLAIN: real (不执行 user_sql, 走 <SQL 变体>)`
     - `Day-2 INDEX_HINTS: <real BTREE recommendations | unsupported with kind-specific reason>`
   - **TiDB 行(line 67)** 改写要点:从当前 "`TiDbDiagnosticsProvider` returning structured `dialect_unsupported` for all 7 hooks" 改为 "EXPLAIN 真实(`EXPLAIN <sql>`,tabular 解析,不加 ANALYZE);INDEX_HINTS 真实(行存,FULL_SCAN → BTREE 推荐);LOCK/POOL/SPACE/TERMINATE/OPTIMIZE 仍 structured unsupported(Day-3:Statement Summary / ADMIN SHOW DDL)"
   - 类似地,其余 10 个 kind 行的 Diagnostics 段都需要更新到与本 spec 矩阵一致(MariaDB 改为 "verified — reuses MySqlDiagnosticsProvider via supportedDriverTypes")
   - Wave A/B/C Child Artifact Tracking 表里 Day-2 列收紧:`EXPLAIN diagnostics` 一列改为 "Done — see 2026-05-08-diagnostics-day2-plan.md",其他 capability(ER DDL、real connection smoke 等)继续保持原 Day-2 状态
2. **`server/data-talk-adapter/src/main/resources/agents/AGENTS.md`**(运行时 AI 提示词):
   - 关于 diagnostics 的描述从"许多新 kind 的 diagnostics 不支持"改为"EXPLAIN 全 kind 支持(只规划不执行);INDEX_HINTS 仅 sqlite / sqlserver / mariadb / tidb 推荐 BTREE,其他 kind 走 partitioning / sort key / connector pushdown 等替代策略"

### 6.2 新增

3. **本文档**:`docs/product-specs/2026-05-08-diagnostics-day2-design.md`
4. **后续 plan**:`docs/exec-plans/2026-05-08-diagnostics-day2-plan.md`(由 writing-plans skill 产出)

### 6.3 索引登记

5. `docs/product-specs/index.md` §8 注册本 spec
6. `docs/exec-plans/index.md` Active 区注册新 plan(plan 写出后)

### 6.4 不动

- `CLAUDE.md`(架构无变化,只是填充)
- `ARCHITECTURE.md`(同上)
- `client/DESIGN.md`(无前端架构改动)
- `docs/generated/db-schema.md`(无 metadata DB 迁移)
- `docs/DESIGN.md` / `docs/BACKEND.md` / `docs/FRONTEND.md`(模式无变化)

### 6.5 各 kind 子 spec

各 kind 的原始 child spec(如 `2026-05-01-data-source-coverage-clickhouse-design.md`)**不修改原文**。原 child spec 的 "Day-2: EXPLAIN diagnostics" 待办项由本 spec 接管,本 spec 在文头(本表)已明确"补充关系"。

## 7. 实施分批与依赖

11 家完全互相独立(方案 A 的核心好处)。建议批次:

| 批次 | 内容 | 依赖 |
|---|---|---|
| Batch 0 | `AbstractDiagnosticsProvider` 加 5 个 helper + 把 MySQL `parseQueryBlock` 上移为 `parseMySqlJsonPlan` | 必须先 |
| Batch 1(并发) | sqlite / duckdb 实现 + 内嵌驱动集成测试 | Batch 0 |
| Batch 2(并发) | tidb / apache_doris / starrocks / clickhouse provider | Batch 0 |
| Batch 3(并发) | sqlserver / hive / presto / trino provider | Batch 0 |
| Batch 4 | mariadb 兼容验证 IT + 文档同步 + i18n + AGENTS.md 改写 | Batch 1-3 |

Batch 1-3 可走 CLAUDE.md 的 "Parallel Plan Execution" — 同 batch 内多个 kind 并发派 subagent,batch 内跳过 per-edit 编译,batch 完成后跑一次完整 `mvn verify`。Batch 间串行(Batch 0 改基类,后续依赖)。

## 8. 风险与缓解

| 风险 | 触发条件 | 缓解 |
|---|---|---|
| Doris/StarRocks EXPLAIN 输出在不同版本格式漂移 | FE 升级后 `Explain String` 列名 / 字段顺序变 | L1 fixture 含多版本 fixture(2.x / 3.x);grammar 优雅降级为 OTHER 而非崩溃 |
| SQL Server `SET SHOWPLAN_XML ON` 在某些场景下被 RDBMS 拒绝(如 connection pool 复用) | DataTalk 当前用 DriverManager 不走 pool,风险低 | provider 内显式 `try { SHOWPLAN_XML OFF } catch ignored {}` 兜底 |
| TiDB EXPLAIN 字段名在版本间漂移(6.x → 7.x) | TiDB 主版本升级 | TabularLayout 用列名而非位置匹配,缺列时优雅降级 |
| Hive STAGE PLANS 多 Stage 父子关系丢失 | Hive 输出本身扁平化(STAGE_DEPENDENCIES 仅给依赖关系) | Day-2 接受扁平展示,STAGE_DEPENDENCIES 段不解析为 children;warning 提示 |
| 7 家 INDEX_HINTS unsupported 文案过短 / 过长 | i18n 文案需斟酌 | 文案在 implementation 阶段定稿,本 spec 给骨架方向 |
| testcontainers 在 CI 下载镜像不稳定 | 网络 / Docker hub rate limit | 全部 `@Disabled`,本地手动跑;CI 仅依赖 L1+L2+L3 |
| Trino/Presto 逻辑计划与实际下推差距过大,误导用户 | TableScan 总被识别为 FULL_SCAN | warning 明确告知;UI 上 warning 高亮显示 |
| DuckDB JDBC 在 CI Linux 环境 native library 加载失败 | duckdb_jdbc 依赖平台特定 `.so`(linux-x86_64 / macos-x86_64 / macos-aarch64),CI 环境若不在常见架构内 native lib 缺失 | Batch 1 第一个里程碑 = 在 CI 真跑通 DuckDB L3 测试,确认 native lib 自动加载;若失败回退为 `@Disabled` 与 SQL Server 同级处理 |

## 9. Definition of Done

- [ ] 11 个 provider 替换 unsupported 桩(MariaDB 除外,验证通过即可)
- [ ] `AbstractDiagnosticsProvider` 5 个 helper 全部加上,带单测
- [ ] 28 条 i18n keys(17 后端 + 11 前端 dialect 标签)× 2 语言 = 56 条文案落地
- [ ] L1+L2 测试覆盖率:每个 provider ≥ 12 测试,grammar 解析方法 ≥ 6 fixture
- [ ] L3 内嵌驱动 IT(sqlite + duckdb)CI 必跑通过
- [ ] L4 testcontainers `@Disabled` 钩子全 11 个就位
- [ ] `DiagnosticsServiceTest` routing 矩阵更新
- [ ] `DiagnosticsClosedLoopIT` 三态轮跑通过
- [ ] `DATA_SOURCE_TYPE_COMPATIBILITY.md` Snapshot 表 11 行改写
- [ ] `agents/AGENTS.md` runtime 提示词改写
- [ ] `docs/product-specs/index.md` §8 注册本 spec
- [ ] `docs/exec-plans/index.md` 注册对应 plan
- [ ] `cd server && mvn verify` 通过
- [ ] `cd client && npx tsc --noEmit` 通过(仅 i18n 字符串改动)
