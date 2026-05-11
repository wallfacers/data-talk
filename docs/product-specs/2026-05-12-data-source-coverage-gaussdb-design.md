# Data Source Coverage: GaussDB Design

- **Date:** 2026-05-12
- **Status:** Draft
- **Wave:** C step 6 (final)
- **Parent:** [Wave C Data Source Coverage Design](./2026-05-08-data-source-coverage-wave-c-design.md)
- **Kind:** `gaussdb`
- **Scope:** 集中式密码连接 only；分布式 / DWS / 多 IP 模式明确排除

## 1. Purpose

Wave C 第 6 个 kind。GaussDB 是华为企业级 PG-fork 数据库，集中式模式协议兼容 PostgreSQL。本 spec 验证 GaussDB 作为 PG-fork kind 消费 `PgForkReuseRule` 跨 kind 复用 kit 的正确性，确认驱动可达性、系统模式过滤、风险分级和前端连接表单。

**核心输出：**
- `ConnectionKind.GAUSSDB` + `gaussdbjdbc` 驱动 wiring
- 6 个 GaussDB `*ReuseIT` 具体测试子类（消费 PgForkReuseRule）
- GaussDB 专属系统模式过滤器
- 双通道风险分类器
- 前端连接表单 + picker + i18n

## 2. Compatibility Gate Application

按 [DATA_SOURCE_TYPE_COMPATIBILITY.md](../../docs/DATA_SOURCE_TYPE_COMPATIBILITY.md) 强制清单逐项应用：

| Area | Coverage | Notes |
|------|----------|-------|
| Connection form | Password (host/port/username/password/database) | 默认端口 8000；无 SSL/TLS/HTTP 选项 |
| JDBC URL | `jdbc:postgresql://host:8000/database` | 驱动类 `org.postgresql.Driver`；URL 前缀和驱动类与 PG 完全相同，DriverManager 按注册顺序路由。DataTalk 通过 ConnectionKind 在应用层决定 URL 端口，JDBC 层不区分 |
| Driver | `com.huaweicloud:gaussdbjdbc:v2.0-8.218.0` | Maven Central 可达 |
| SQL Splitter | PG splitter 复用 | `PostgresJdbcSqlStatementSplitter` |
| Metadata Discovery | PG-fork 复用 | GaussDB 专属系统模式 11 项（不含 public） |
| Risk Classifier | PG 基线 + GaussDB 专属 L3 | 5 条 anchored patterns；Channel 2 dialect_unsupported N/A |
| Diagnostics | 全 9 hooks dialect_unsupported | 同 openGauss/KingbaseES 模式 |
| Frontend | PG-fork 复用模式 | connection fields + picker + formatter + i18n |
| MCP / AGENTS.md | ConnectionObjectType enum + prompt rules | 标准模板 |
| Driver coexistence | gaussdbjdbc 内含 `org.postgresql.Driver` | 与现有 PG 驱动类同名；URL 前缀相同（`jdbc:postgresql://`），DataTalk 通过 ConnectionKind 在应用层区分目标实例 |

## 3. Design Inputs

- 总控 spec: [2026-05-08-data-source-coverage-wave-c-design.md](./2026-05-08-data-source-coverage-wave-c-design.md)
- PgForkReuseRule 生产者: [2026-05-08-data-source-coverage-opengauss-design.md](./2026-05-08-data-source-coverage-opengauss-design.md)
- 同模式消费参考: [2026-05-08-data-source-coverage-kingbase-design.md](./2026-05-08-data-source-coverage-kingbase-design.md)
- 路线图: [2026-04-25-next-implementation-roadmap-plan.md](../exec-plans/2026-04-25-next-implementation-roadmap-plan.md) Task 9
- 兼容 gate: [DATA_SOURCE_TYPE_COMPATIBILITY.md](../../docs/DATA_SOURCE_TYPE_COMPATIBILITY.md)

## 4. Support Statement

**Target outcome:** GaussDB 集中式 first-class 支持。

- [x] Connection form: host / port / username / password / database（默认端口 8000）
- [x] JDBC URL: `jdbc:postgresql://host:8000/database`
- [x] 驱动: `com.huaweicloud:gaussdbjdbc:v2.0-8.218.0`
- [x] SQL splitter: PG `PostgresJdbcSqlStatementSplitter` 路由
- [x] Metadata discovery: PG-fork 复用 + GaussDB 专属系统模式过滤
- [x] Risk classifier: PG 基线 + GaussDB 专属 L3
- [x] Diagnostics: structured unsupported
- [x] Frontend: connection form + picker + formatter + i18n
- [x] MCP: ConnectionObjectType enum
- [x] AGENTS.md prompt rules
- [x] PgForkReuseRule 6 具体子类

**Intentionally unsupported (Day-1):**
- 分布式 / DWS 模式
- SSL / TLS / Kerberos / HTTP 传输
- 多 IP 集群连接
- EXPLAIN diagnostics 真实执行
- ER DDL generation
- ER Inspector: Day-1 返回 `dialect_unsupported`
- ER Designer: Day-1 返回 `dialect_unsupported`

## 5. Kind Naming

- **Canonical:** `gaussdb`
- **Aliases:** 无
- **Normalization rules:**

| Input | Normalized | Action |
|-------|------------|--------|
| `gaussdb` | `gaussdb` | accept |
| `GaussDB` | `gaussdb` | toLowerCase |
| `GAUSSDB` | `gaussdb` | toLowerCase |
| `gauss_db` | reject | 提示 "gauss_db is not a recognized kind; did you mean gaussdb?" |
| `opengauss` | reject | 提示 "opengauss is a separate kind; use opengauss" |

- **Reject boundary:** `ConnectionKind.normalize()` 中仅接受 `gaussdb`（case-insensitive）；`gauss_db` 含下划线且不是官方产品名，不静默转换而是显式拒绝并提示。

## 6. Connection And Persistence

### 6.1 Driver Decision

| Artifact | `com.huaweicloud:gaussdbjdbc:v2.0-8.218.0` |
|----------|---------------------------------------------|
| Group ID | `com.huaweicloud` |
| License | 商业（华为云 SDK 许可）；Maven Central 公开分发 |
| Driver class | `org.postgresql.Driver` |
| URL prefix | `jdbc:postgresql://` |
| Default port | `8000` |
| Coexistence risk | 与 `org.postgresql:postgresql` 驱动类同名；URL 前缀相同 |

### 6.2 Coexistence Strategy

`gaussdbjdbc` 内部是 PG 驱动的华为 fork，`org.postgresql.Driver` 类同时处理 `jdbc:postgresql://` 连接到 PG 和 GaussDB。

**风险分析：** classpath 上同时存在 `org.postgresql:postgresql`（标准 PG 驱动）和 `com.huaweicloud:gaussdbjdbc`（华为 fork），两者都提供 `org.postgresql.Driver` 类。Maven 的 classpath 顺序取决于声明顺序。`DriverManager` 按注册顺序使用第一个 `acceptsURL("jdbc:postgresql://")` 返回 true 的 Driver 实例。这意味着所有 PG 连接也会走华为 fork 的 Driver。

**设计决策：** 接受华为 fork 驱动处理所有 `jdbc:postgresql://` 连接。理由：
1. gaussdbjdbc 是 PG 驱动的严格超集 fork，保持向后兼容
2. DataTalk 的 PG 集成测试（`PostgresSqlStatementSplitterTest` 等）会在 gaussdbjdbc 加入 classpath 后继续运行，如有回归可立即发现

**测试保障：** 新增 `GaussDBDriverCoexistenceTest` 验证：
- `DriverManager.getDrivers()` 枚举中 `org.postgresql.Driver` 只出现一次（不会重复注册）
- 标准 PG URL `jdbc:postgresql://host:5432/db` 仍能成功创建连接
- GaussDB URL `jdbc:postgresql://host:8000/db` 也能成功创建连接

**备选方案（如回归发生）：** 在 `JdbcUrlBuilder` 中返回显式驱动类实例而非依赖 `DriverManager` 自动发现：`Driver driver = (Driver) Class.forName("org.postgresql.Driver").getDeclaredConstructor().newInstance(); Connection conn = driver.connect(url, props);`。此方案绕过 DriverManager 注册顺序问题，但增加维护复杂度，仅在测试证明回归时启用。

### 6.3 JdbcUrlBuilder

GaussDB 复用 PG 的 `PostgresJdbcSqlStatementSplitter`，URL 构造也沿用 PG 模式。

```java
// ConnectionKind.GAUSSDB branch in JdbcUrlBuilder
case GAUSSDB -> {
    String url = "jdbc:postgresql://%s:%d/%s".formatted(host, port, database);
    props.putIfAbsent("loginTimeout", "10");
    yield new JdbcConnection(url, "org.postgresql.Driver", props);
}
```

### 6.4 ConnectionService Timeout Branch

GaussDB 复用 PG 的 timeout 参数策略（秒级）。`ConnectionService.testConnection` 中 GAUSSDB 分支折叠到 PG-family 分支，注入 `connectTimeout` + `socketTimeout`（秒级），与 openGauss/KingbaseES 一致。不单独新增 GAUSSDB timeout 分支。

### 6.5 Connection Fields

| Field | Required | Default | Notes |
|-------|----------|---------|-------|
| host | yes | localhost | |
| port | yes | 8000 | GaussDB 默认端口，非 PG 的 5432 |
| username | yes | — | |
| password | yes | — | |
| database | yes | — | GaussDB 集中版数据库名 |

## 7. Metadata Discovery

### 7.1 Reuse Path

PG-fork 复用: `JdbcMetadataDiscoveryService` 标准 JDBC `DatabaseMetaData` 路径。

### 7.2 System Schema Filter

PG 基线 5 项 + GaussDB 专属 6 项，共 11 项（`public` 不过滤，保持与 PG 一致）：

```java
Set<String> GAUSSDB_SYSTEM_SCHEMAS = Set.of(
    // PG 基线（不含 public）
    "pg_catalog", "information_schema", "pg_toast", "pg_temp_1", "pg_toast_temp_1",
    // GaussDB 专属
    "db_scheduler", "db4ai", "pkg_service", "sqladvisor", "wdr_snapshot", "snapshot"
);
```

### 7.3 Discovery Semantics

同 openGauss: 过滤 `GAUSSDB_SYSTEM_SCHEMAS` 后的 schema 列表作为用户可见范围。`public` schema 保留并始终可见（与 PG 行为一致）。

## 8. SQL Execution / Splitter / Risk Classifier

### 8.1 Splitter Routing

`ConnectionKind.GAUSSDB` → `PostgresJdbcSqlStatementSplitter`（同 openGauss/KingbaseES 路由）。

### 8.2 Batch DML Routing

PG-fork batch DML 复用（同 openGauss/KingbaseES）。

### 8.3 Risk Classifier — Dual Channel

**Channel 1: PG baseline patterns**（继承自 PG splitter/risk 基线）

**Channel 2: GaussDB-specific L3 patterns**

```java
static final List<SqlRiskRule> GAUSSDB_SPECIFIC_L3 = List.of(
    SqlRiskRule.l3("CREATE\\s+RESOURCE\\s+POOL"),
    SqlRiskRule.l3("ALTER\\s+COORDINATOR"),
    SqlRiskRule.l3("DROP\\s+NODE"),
    SqlRiskRule.l3("SHUTDOWN"),
    SqlRiskRule.l3("ALTER\\s+SYSTEM\\s+SET")
);
```

**Channel 2 (dialect_unsupported): N/A** — GaussDB 集中式没有需要 dialect_unsupported 的方言命令（不像 KingbaseES 有 KBBACKUP/KBRESTORE 等 CLI 工具命令）。本通道有意留空。

```java
// GaussDB-specific L3 anchored patterns
static final List<SqlRiskRule> GAUSSDB_SPECIFIC_L3 = List.of(
    SqlRiskRule.l3("CREATE\\s+RESOURCE\\s+POOL"),
    SqlRiskRule.l3("ALTER\\s+COORDINATOR"),
    SqlRiskRule.l3("DROP\\s+NODE"),
    SqlRiskRule.l3("SHUTDOWN"),
    SqlRiskRule.l3("ALTER\\s+SYSTEM\\s+SET")
);
```

### 8.4 Splitter Equivalence Tests

GaussDB 和 PG splitter 对相同 SQL 文本产生相同分片结果。6 个标准 case 来自 PgForkReuseRule 抽象基线：

| # | Case | Description |
|---|------|-------------|
| 1 | Dollar-quoted function body | `$$...$$` 和 `$tag$...$tag$` 包裹的函数体内部分号不分片 |
| 2 | PL/pgSQL block | `BEGIN...END` 块内分号不分片 |
| 3 | psql meta-command | `\d`, `\c`, `\dt` 等不通过 JDBC 执行 |
| 4 | Mixed scripts | DDL + DML + function 定义混合文本 |
| 5 | String/comment edge cases | 单引号内分号、`--` 和 `/* */` 注释内分号不分片 |
| 6 | DELIMITER stored procedure | `CREATE OR REPLACE FUNCTION ... $$ ... $$ LANGUAGE plpgsql` |

## 9. Diagnostics Provider

`GaussDBDiagnosticsProvider` 全 9 hooks `dialect_unsupported`:

| Hook | Return | i18n key |
|------|--------|----------|
| explainPlan | `unsupported("gaussdb.explain.unsupported")` | gaussdb 不支持 EXPLAIN 诊断 |
| indexHints | `unsupported("gaussdb.index_hints.unsupported")` | gaussdb 不支持索引推荐 |
| tableSize | `unsupported("gaussdb.table_size.unsupported")` | |
| indexUsage | `unsupported("gaussdb.index_usage.unsupported")` | |
| longRunningQueries | `unsupported("gaussdb.long_running.unsupported")` | |
| tableBloat | `unsupported("gaussdb.table_bloat.unsupported")` | |
| connectionStats | `unsupported("gaussdb.connection_stats.unsupported")` | |
| lockInfo | `unsupported("gaussdb.lock_info.unsupported")` | |
| replicationLag | `unsupported("gaussdb.replication_lag.unsupported")` | |

i18n keys: `diagnostics.gaussdb.*.unsupported` / `诊断.gaussdb.*.unsupported`

## 10. Reuse Outputs

**Consumption:**
- `PgForkReuseRule` 6 个抽象基类 → 6 个 `GaussDB*ReuseIT` 具体子类
- `PostgresJdbcSqlStatementSplitter` 分词路由
- PG result value normalizer 基线

**Production:** 无。本 kind 是 PgForkReuseRule 的终端消费者，不产出新的跨 kind 复用 kit。

### 10.1 PgForkReuseIT Subclasses

| Abstract Base | Concrete Subclass | Test Focus |
|---------------|-------------------|------------|
| `AbstractPgForkSplitterIT` | `GaussDBSplitterIT` | 分词等价 |
| `AbstractPgForkMetadataIT` | `GaussDBMetadataIT` | catalog/schema/table 列举 |
| `AbstractPgForkQueryIT` | `GaussDBQueryIT` | SELECT/INSERT/UPDATE 正确性 |
| `AbstractPgForkResultNormalizerIT` | `GaussDBResultNormalizerIT` | 类型映射 |
| `AbstractPgForkBatchDmlIT` | `GaussDBBatchDmlIT` | 批量 DML |
| `AbstractPgForkProcedureIT` | `GaussDBProcedureIT` | 存储过程语法 |

所有 IT 子类标注 `@Disabled("GaussDB testcontainer not available in CI")`。

## 11. Day-2 / Day-3 Upgrade Path

**Day-2:**
- EXPLAIN diagnostics 真实执行（`EXPLAIN ANALYZE`）
- ER DDL generation（GaussDB DDL 方言）
- SSL/TLS 传输加密
- 多 IP 集群连接

**Day-3:**
- 分布式 / DWS 模式支持
- Kerberos 认证
- HTTP 传输
- 华为云 GaussDB 品牌图标

## 12. Out-of-Scope / T2 Fixture / i18n / AGENTS.md

### 12.1 Out-of-Scope

- 分布式模式（CN/DN 分离）
- DWS（数据仓库服务）
- 多 IP 高可用连接字符串
- SSL / Kerberos / HTTP 传输
- GaussDB 品牌图标（使用通用数据库图标）

### 12.2 T2 Fixture Tier

T3 — 无公共 testcontainer 镜像。所有 IT 标注 `@Disabled`，通过 manual smoke script 验证。

### 12.3 i18n Keys

GaussDB 是 PG-fork 表单复用模式，前端连接表单的字段标签（host/port/username/password/database）直接复用 PG 的通用 i18n keys，不新建 per-field GaussDB 专属 key。

GaussDB 专属 i18n keys 仅限以下场景：

| Key | en | zh | Purpose |
|-----|----|----|---------|
| `connection.kind.gaussdb.label` | GaussDB | GaussDB | picker 显示名 |
| `connection.kind.gaussdb.defaultPort` | 8000 | 8000 | 默认端口 |
| `diagnostics.dialect_unsupported.gaussdb.*` | (per-hook) | (per-hook) | diagnostics unsupported messages |
| `risk.gaussdb.create_resource_pool.description` | GaussDB resource pool management | GaussDB 资源池管理 | L3 risk description |
| `risk.gaussdb.alter_coordinator.description` | GaussDB coordinator management | GaussDB 协调节点管理 | L3 risk description |
| `risk.gaussdb.drop_node.description` | GaussDB node removal | GaussDB 节点移除 | L3 risk description |
| `risk.gaussdb.shutdown.description` | GaussDB instance shutdown | GaussDB 实例关闭 | L3 risk description |
| `risk.gaussdb.alter_system_set.description` | GaussDB system config change | GaussDB 系统配置变更 | L3 risk description |

### 12.4 AGENTS.md Timing

实施末尾追加 `## GaussDB` section 到 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`，包含 kind 命名、URL 格式、风险分级规则和 Day-2 留白说明。

## Verification Matrix

| # | Command | Pass Criteria |
|---|---------|---------------|
| V1 | `cd server && mvn compile -q` | BUILD SUCCESS |
| V2 | `cd server && mvn -pl data-talk-application -am test -Dtest='*GaussDB*' -q` | All tests green |
| V3 | `cd client && npx tsc --noEmit` | 0 errors |
| V4 | `cd client && npx vitest run --reporter=verbose 2>&1 \| tail -30` | All pass |
| V5 | Manual smoke: connect to real GaussDB instance | Connection OK, schema listing OK, query OK |
| V6 | `cd server && mvn -pl data-talk-application -am test -Dtest='GaussDBDriverCoexistenceTest' -q` | PG + GaussDB 驱动共存无回归 |
| V7 | AGENTS.md GaussDB section prompt contract test | kind/URL/risk rules 与 spec 一致 |
