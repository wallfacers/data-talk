# Intelligent Operations —— 数据库诊断平台

> 状态：Draft · 2026-04-27 · wallfacers
>
> 范围：以 EXPLAIN 执行计划 + 索引推荐为第一 slice，建立可扩展的数据库诊断平台底座；预留锁分析、连接池监控、空间占用等后续能力的接口与 stub；覆盖 MySQL / PostgreSQL / H2，Oracle 以 stub 形式注册占位。

---

## 1. 背景与动机

### 1.1 现状

DataTalk 当前的 AI 操作能力聚焦于"写 SQL + 执行 SQL"，缺乏对**查询性能问题**的系统化诊断能力：

- AI 被问"为什么慢"时，只能凭上下文经验推断，无法拿到真实 `EXPLAIN` 数据
- 用户手动跑 `EXPLAIN` 后结果以纯文本出现在聊天流，AI 没有结构化解析路径
- 索引推荐、表统计、锁分析等能力完全缺失，属于传统 DB 工具的标配
- 不同方言（MySQL / PostgreSQL / H2 / Oracle）的 EXPLAIN 格式差异，需要统一的规范化层

### 1.2 产品总设计坐标

来自 [docs/product-specs/index.md §3.6 性能优化](./index.md)：

| 功能 | 阶段 |
|---|---|
| 执行计划分析（EXPLAIN 可视化 + AI 解读） | 二期 |
| 慢查询识别 | 三期 |
| 索引推荐 | 三期 |
| 表统计信息 | 三期 |

本 spec 提前把索引推荐和执行计划做进同一 slice（技术上天然绑定），同时为慢查询、表统计、锁分析建立平台底座。

### 1.3 路线图坐标

[2026-04-25 Next Implementation Roadmap](../exec-plans/2026-04-25-next-implementation-roadmap-plan.md) 的 **Task 7（与 Task 6 并行设计，Task 6 完成后执行）**。Task 8（可视化扩展）不依赖本 spec，可独立推进。

---

## 2. 决策摘要

| 编号 | 决策 |
|---|---|
| Q1 | **第一 slice 范围**：EXPLAIN + 索引推荐；两个 Action 对 AI 独立可见，`datatalk_index_hints` 内部串联 EXPLAIN，AI 无需理解两步协议 |
| Q2 | **架构分层**：`DiagnosticsProvider`（Strategy，方言适配）+ Action 层（AI 暴露）双层解耦；新方言只加 Provider 实现，新诊断能力只加 Action |
| Q3 | **EXPLAIN 规范化**：MySQL 用 `EXPLAIN FORMAT=JSON`，PostgreSQL 用 `EXPLAIN (FORMAT JSON, ANALYZE false)`，H2 走文本解析；三方言统一输出 `ExplainPlan` record |
| Q4 | **Oracle 占位**：`OracleDiagnosticsProvider` 所有能力返回 `UNSUPPORTED`；AGENTS.md 标注不可调用 |
| Q5 | **UI 双入口**：SQL 编辑器工具栏手动触发 + AI 工具调用；结果写入同一 `DiagnosticTab`（`TabScope.WORKBENCH`，依赖 Task 6） |
| Q6 | **stub 能力预注册**：`LockReport / PoolReport / SpaceReport` 数据结构 + `DiagnosticCapability` 枚举 + Action 占位（返回 UNSUPPORTED）在第一 spec 全部定义；后续 spec 只填实现 |
| Q7 | **只读原则**：所有诊断能力为 L1（只读），不创建索引、不改 schema；索引推荐是建议文本，执行须走现有 Guarded DDL/DML 流程 |

---

## 3. 范围、非目标、不变量

### 3.1 范围（第一 spec）

1. **EXPLAIN 规范化**：MySQL / PG / H2 三方言解析，统一 `ExplainPlan` 树结构
2. **索引推荐**：基于 EXPLAIN 输出的服务端推断，返回结构化 `IndexRecommendation` 列表
3. **DiagnosticsProvider 接口**：定义全部能力边界（EXPLAIN / INDEX_HINTS / LOCK_INFO / CONNECTION_POOL / TABLE_SPACE）
4. **stub 能力**：Lock / Pool / Space 接口定义 + Provider stub + Action 占位注册
5. **Action 层**：`datatalk_explain_query` + `datatalk_index_hints`，暴露给 AI（MCP）
6. **REST 端点**：`POST /api/sessions/{id}/diagnostics/explain` + `/index-hints`，供前端直调
7. **前端**：工具栏 Explain 按钮、Activity Rail Diagnostics 面板、聊天 `<DiagnosticsCard>`、`DiagnosticTab`（WORKBENCH scope）
8. **AI 集成**：AGENTS.md 诊断工作流规范 + stub 能力标注不可用

### 3.2 非目标（day-1 不做）

- ❌ `EXPLAIN ANALYZE`（实际执行计划）—— 有副作用，留后续 spec 加开关
- ❌ 慢查询日志读取 / slow_log 分析
- ❌ 锁分析、连接池监控、空间占用的实际实现（仅 stub）
- ❌ Oracle 真实实现（仅 stub）
- ❌ 索引自动创建——推荐结果须用户手动或经 AI + Guarded DDL 确认执行
- ❌ 多轮诊断历史持久化——第一 slice 靠 `DiagnosticTab` 的 Task 6 持久化兜底
- ❌ 语义层 SQL 优化建议（重写 JOIN 顺序等）

### 3.3 核心不变量

- **所有诊断 Action 均为 L1（只读），不修改任何数据或 schema**
- **`DiagnosticsProvider.supportedCapabilities()` 始终诚实声明支持集；Action 层不假设 Provider 支持未声明的能力**
- **工具栏与 AI 工具调用共享同一条 `DiagnosticsService` 路径，结果严格一致**
- **索引推荐是文本建议，执行必须走 `ExecuteSqlAction` 的 Guarded DDL 流程**

---

## 4. 架构设计

### 4.1 层次结构

```
domain
  DiagnosticCapability          (enum)
  DiagnosticsProvider           (interface — 方言适配契约)
  ExplainPlan / ExplainNode     (规范化执行计划)
  IndexRecommendation           (索引推荐)
  LockReport / PoolReport / SpaceReport  (stub 数据结构)
  DiagnosticResult<T>           (统一包装：ok | unsupported | error)

application
  DiagnosticsService            (编排：找 Provider → 调用 → 格式化)
  DiagnosticsProviderRegistry   (按 driverType 路由到正确 Provider)

infrastructure
  MySqlDiagnosticsProvider      (EXPLAIN FORMAT=JSON + index hint 解析)
  PostgreSqlDiagnosticsProvider (EXPLAIN FORMAT JSON + index hint 解析)
  H2DiagnosticsProvider         (文本 EXPLAIN 解析)
  OracleDiagnosticsProvider     (所有能力返回 UNSUPPORTED)

adapter / actions
  ExplainQueryAction            (datatalk_explain_query, Executor.SERVER, L1)
  IndexHintsAction              (datatalk_index_hints,  Executor.SERVER, L1)
  LockInfoAction                (datatalk_lock_info,    Executor.SERVER, 占位)
  PoolStatusAction              (datatalk_pool_status,  Executor.SERVER, 占位)
  TableSpaceAction              (datatalk_table_space,  Executor.SERVER, 占位)

adapter / controller
  DiagnosticsController         (REST — 供前端工具栏直调)
```

### 4.2 DiagnosticsProvider 接口

```java
// domain 层
public interface DiagnosticsProvider {
    Set<DiagnosticCapability> supportedCapabilities();

    DiagnosticResult<ExplainPlan>            explain(String sql, ResolvedExecutionContext ctx);
    DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan, ResolvedExecutionContext ctx);
    DiagnosticResult<LockReport>             lockInfo(ResolvedExecutionContext ctx);
    DiagnosticResult<PoolReport>             connectionPoolInfo(ResolvedExecutionContext ctx);
    DiagnosticResult<SpaceReport>            tableSpaceInfo(ResolvedExecutionContext ctx);
}
```

`DiagnosticResult<T>` 是 sealed interface，三个子类型：
- `Ok<T>` — 成功，携带数据
- `Unsupported` — provider 不支持此能力，附可读原因
- `DiagnosticError` — 执行失败，附错误类型和原始消息

### 4.3 方言路由

`DiagnosticsProviderRegistry` 按 `connection.driverType`（`mysql` / `postgresql` / `h2` / `oracle`）映射到对应 Provider 实现，注入为 Spring bean。新方言只需注册一个新 `@Component` 实现 `DiagnosticsProvider`，注册表通过 `@Autowired List<DiagnosticsProvider>` 自动收集。

---

## 5. 领域模型

### 5.1 EXPLAIN 规范化

```java
// domain 层 record
public record ExplainPlan(
    String dialect,            // "mysql" | "postgresql" | "h2" | "oracle"
    String rawText,            // 原始 EXPLAIN 输出，供用户直接查看
    List<ExplainNode> nodes,   // 规范化节点树（根节点列表）
    @Nullable Double totalCostEstimate,
    List<String> warnings      // 如 "Full table scan on orders (est. 1M rows)"
) {}

public record ExplainNode(
    String operation,          // "Seq Scan" / "Hash Join" / "Full Table Scan" …
    @Nullable String table,
    ScanType scanType,         // FULL_SCAN | INDEX_SCAN | INDEX_RANGE | CONST | REF | OTHER
    long rows,
    @Nullable Double cost,
    @Nullable String extra,    // MySQL Extra 列 / PG 附加信息
    List<ExplainNode> children
) {}

public enum ScanType {
    FULL_SCAN,    // 最差，status.danger 标记
    INDEX_RANGE,  // 中等，status.info 标记
    INDEX_SCAN,   // 好，status.success 标记
    CONST,        // 最优（主键/唯一索引点查），text.soft 标记
    REF,          // 非唯一索引，status.info 标记
    OTHER         // 无法归类，text.muted 标记
}
```

### 5.2 索引推荐

```java
public record IndexRecommendation(
    String table,
    List<String> columns,
    String indexType,      // "BTREE"（默认）| "HASH"
    Impact impact,         // HIGH | MEDIUM | LOW
    String rationale       // 人类可读，例如 "Full scan on orders(user_id)，预计命中率 85%"
) {}

public enum Impact { HIGH, MEDIUM, LOW }
```

### 5.3 Stub 数据结构（接口定义，方法体空）

```java
public record LockReport(List<LockEntry> locks) {}
public record LockEntry(String table, String lockType, String holder, String waiter) {}

public record PoolReport(int active, int idle, int maxSize, String poolName) {}

public record SpaceReport(List<TableSpaceEntry> tables) {}
public record TableSpaceEntry(String table, long rowCount, long dataSizeBytes, long indexSizeBytes) {}
```

### 5.4 DiagnosticCapability 枚举

```java
public enum DiagnosticCapability {
    EXPLAIN,
    INDEX_HINTS,
    LOCK_INFO,         // stub
    CONNECTION_POOL,   // stub
    TABLE_SPACE        // stub
}
```

---

## 6. Action 层与 REST 端点

### 6.1 实现 Action（第一 slice）

**`datatalk_explain_query`**
```
Executor:    SERVER
riskLevel:   L1
exposeToMcp: true
输入: { "sql": "<SQL 文本>" }
      （connection context 从当前 session 自动取，AI 无需传）
输出: {
  "dialect": "mysql",
  "rawText": "...",
  "nodes": [ { "operation": "...", "scanType": "FULL_SCAN", ... } ],
  "totalCostEstimate": 1234.5,
  "warnings": ["Full table scan on orders"],
  "unsupported": false
}
```

**`datatalk_index_hints`**
```
Executor:    SERVER
riskLevel:   L1
exposeToMcp: true
输入: { "sql": "<SQL 文本>" }
      （内部自动串联 explain → indexHints，AI 无需先调 explain）
输出: {
  "recommendations": [
    { "table": "orders", "columns": ["user_id"], "impact": "HIGH",
      "rationale": "Full scan on orders(user_id)，建议创建 BTREE 索引" }
  ],
  "explainSummary": "发现 2 处 Full Table Scan，建议优先为 orders.user_id 创建索引",
  "unsupported": false
}
```

### 6.2 Stub Action（占位注册，第一 spec 不实现）

| Action | MCP 名称 | 返回 |
|---|---|---|
| `LockInfoAction` | `datatalk_lock_info` | `{ "unsupported": true, "reason": "Lock analysis not yet available for this dialect" }` |
| `PoolStatusAction` | `datatalk_pool_status` | 同上 |
| `TableSpaceAction` | `datatalk_table_space` | 同上 |

### 6.3 REST 端点

```
POST /api/sessions/{sessionId}/diagnostics/explain
  Content-Type: application/json
  Body: { "sql": "SELECT ..." }
  Response: ExplainPlan（与 Action 输出结构一致）

POST /api/sessions/{sessionId}/diagnostics/index-hints
  Content-Type: application/json
  Body: { "sql": "SELECT ..." }
  Response: { recommendations: [...], explainSummary: "..." }
```

用 POST 避免 SQL 文本长度超过 GET query string 限制。两个端点复用 `DiagnosticsService`，工具栏调用与 AI 工具调用结果严格一致。

---

## 7. 方言实现策略

| 方言 | EXPLAIN 命令 | ScanType 来源 | 状态 |
|---|---|---|---|
| MySQL | `EXPLAIN FORMAT=JSON <sql>` | `access_type` 字段映射 | 实现 |
| PostgreSQL | `EXPLAIN (FORMAT JSON, ANALYZE false) <sql>` | node `Node Type` 字段映射 | 实现 |
| H2 | `EXPLAIN <sql>`（文本） | 文本正则解析 | 实现（精度较低，H2 为开发测试用途） |
| Oracle | — | — | stub，返回 UNSUPPORTED |

**ScanType 映射规则（MySQL）：**

| access_type | ScanType |
|---|---|
| `ALL` | FULL_SCAN |
| `range` | INDEX_RANGE |
| `ref` / `eq_ref` | REF |
| `index` | INDEX_SCAN |
| `const` / `system` | CONST |
| 其他 | OTHER |

**ScanType 映射规则（PostgreSQL）：**

| Node Type | ScanType |
|---|---|
| `Seq Scan` | FULL_SCAN |
| `Index Scan` | INDEX_SCAN |
| `Index Only Scan` | INDEX_SCAN |
| `Bitmap Index Scan` | INDEX_RANGE |
| `Bitmap Heap Scan` | INDEX_RANGE |
| 其他 | OTHER |

---

## 8. 前端设计

**设计约束来源：[client/DESIGN.md](../../client/DESIGN.md)**

- Stage chrome: `bg.subtle`；工作面: `bg.canvas`；Activity Rail: `bg.panel`
- ScanType 着色唯一来源：`status.*` token（FULL_SCAN=`status.dangerSurface`，INDEX=`status.successSurface`，RANGE/REF=`status.infoSurface`，CONST=`text.soft`，OTHER=`text.muted`）
- `accent.primary`（cobalt）仅用于当前焦点、选中态和主操作按钮
- SQL 文本、cost/rows 数字、列名：`mono-sm` / `mono-md`
- 标签与说明文字：`ui-sm`，元数据: `text.muted`
- 工具栏图标按钮必须有 `aria-label`（accessible name）
- 动效：`motion.normal 180ms + easing.standard`，仅用于状态确认，非装饰

### 8.1 SQL 编辑器工具栏 — Explain 按钮

- 位置：现有工具栏（Run / Cancel / Format / Limit）右侧，新增 `Explain` 按钮
- 图标：inspect / search-code 类图标；`aria-label="Explain Query"`
- 禁用条件：编辑器内容为空，或当前连接不支持 EXPLAIN（`supportedCapabilities` 不含 `EXPLAIN`）
- 点击：调用 `POST /diagnostics/index-hints`，加载中显示 spinner，成功后打开 / 聚焦 `diagnostic` Tab

### 8.2 Activity Rail — Diagnostics 面板

- Rail 图标：第五个（Schema / History / Outline / AI Assist 之后）
- 面板宽 280px，bg: `bg.panel`
- 内容：
  - **有结果时**：上次运行的 SQL 片段（`mono-sm`，截断 80 字符）+ 扫描类型分布行 + 推荐数量 badge + "在工作台打开"链接
  - **无结果时**：空态文案"运行 Explain 查看执行计划"
- 不自动刷新；每次工具栏触发或 AI 调用后更新

### 8.3 聊天区 DiagnosticsCard

AI 调用 `datatalk_explain_query` 或 `datatalk_index_hints` 时渲染，复用 `message.toolSurface`（`bg.panel`）：

```
┌─────────────────────────────────────────┐
│ [方言 badge]  执行计划分析               │
│ ─────────────────────────────────────── │
│ ⚠ 2 处 Full Table Scan                  │  ← status.warningSurface
│ ✓ 索引推荐 2 条（HIGH: 1, MEDIUM: 1）  │
│                          [在工作台打开] │
└─────────────────────────────────────────┘
```

- 警告行：`status.warningSurface` bg，`status.warning` 文字
- "在工作台打开"：`accent.primary` 文字链接，点击后聚焦 `diagnostic` Tab

### 8.4 DiagnosticTab（工作台主面板）

- Tab type: `diagnostic`；注册 `TabScope.WORKBENCH`（通过 Task 6 tab-type-registry）
- Tab 标题：`EXPLAIN · <表名或 SQL 前 20 字符>`

**布局：**
```
┌──────────────────────────────────────────────────────┐
│ [连接 chip]  [方言 badge]  SQL: SELECT ... ▼         │  ← 顶部 chrome，bg.subtle
├──────────────────────────┬───────────────────────────┤
│   执行计划树（60%）       │   索引推荐（40%）          │  ← bg.canvas
│                           │                           │
│  ▼ Hash Join              │  ● HIGH  orders.user_id   │
│    ▼ Seq Scan orders ⚠   │    BTREE (user_id)        │
│      rows: 1,200,000      │    "Full scan on orders…" │
│    ▼ Index Scan users ✓  │                           │
│      rows: 1              │  ● MEDIUM orders.status   │
│                           │    BTREE (status)         │
│                           │    "Range scan 可优化"    │
├──────────────────────────┴───────────────────────────┤
│ ▶ 原始 EXPLAIN 输出（折叠）                           │  ← bg.subtle，mono-sm
└──────────────────────────────────────────────────────┘
```

**颜色约定（ScanType → token）：**

| ScanType | 行背景 | 图标 |
|---|---|---|
| FULL_SCAN | `status.dangerSurface` | ⚠ `status.danger` |
| INDEX_RANGE / REF | `status.infoSurface` | ● `status.info` |
| INDEX_SCAN | `status.successSurface` | ✓ `status.success` |
| CONST | 无背景 | — `text.soft` |
| OTHER | 无背景 | — `text.muted` |

**IndexRecommendation impact → badge：**

| Impact | badge 颜色 |
|---|---|
| HIGH | `status.dangerSurface` + `status.danger` 文字 |
| MEDIUM | `status.warningSurface` + `status.warning` 文字 |
| LOW | `status.infoSurface` + `status.info` 文字 |

**交互：**
- ExplainNode 可展开 / 折叠（`▶ / ▼`），键盘 Space / Enter 触发
- 底部原始文本折叠区：`Ctrl/Cmd+\`` 全局快捷键展开
- 所有可交互控件有 `aria-label` 和 `focusRing`

---

## 9. AI 集成

### 9.1 AGENTS.md 新增工具描述

```markdown
## datatalk_explain_query
获取 SQL 的规范化执行计划树和原始 EXPLAIN 输出。

输入: sql (string)
输出: dialect, rawText, nodes (树), totalCostEstimate, warnings

何时调用: 用户询问查询性能、执行计划、"为什么慢"等问题。
不调用时机: 用户只问 SQL 语法正确性，不涉及性能。

## datatalk_index_hints
基于 EXPLAIN 输出推断索引优化建议（内部自动串联 EXPLAIN，无需先调 explain）。

输入: sql (string)
输出: recommendations (列表，含 table/columns/impact/rationale), explainSummary

何时调用: 用户明确要索引建议；或 explain 结果存在 FULL_SCAN 节点。
不调用时机: 表数据量极小（< 1000 行）时 FULL_SCAN 通常无优化价值。

## datatalk_lock_info / datatalk_pool_status / datatalk_table_space
当前不可用（返回 unsupported: true）。不要调用。
```

### 9.2 诊断工作流规范

注入 AGENTS.md 的行为规范：

```
性能诊断标准流程:
1. 收到性能问题 → 先调 datatalk_explain_query 获取真实执行计划
2. 若 nodes 中存在 FULL_SCAN 或 warnings 非空 → 调 datatalk_index_hints 获取推荐
3. 把 explainSummary + rationale 合并为自然语言，告知用户
4. 提示用户"诊断结果已在工作台打开"

禁止行为:
- 不得凭经验推断索引，必须基于实际 EXPLAIN 结果
- 不得一次性拉取整个 schema 后再分析；先 explain，按需再 datatalk_read_schema
- 索引推荐不得直接执行；如用户确认要建索引，生成 CREATE INDEX SQL 并走 Guarded DDL 流程
```

### 9.3 与 Task 6 Tab 摘要的集成

Task 6 完成后，`{{STAGE_TAB_DIGEST}}` 注入的摘要中会包含 `diagnostic` 类型 Tab。AI 感知"用户当前打开了哪个 SQL 的诊断结果"后，在追问时可直接引用 Tab 内容，无需重新运行 EXPLAIN。

---

## 10. 测试策略

### 10.1 后端（JUnit 5 + AssertJ + MockMvc）

**DiagnosticsProvider 单测（三方言各一套）：**
- `explain()` 返回正确 `ScanType` 枚举映射（覆盖所有 access_type / Node Type 值）
- `explain()` 空结果集 / 无权限 SQL 返回 `DiagnosticError`，不抛异常
- H2 文本解析边界：多 JOIN、子查询、`UNION` 的节点树正确性
- `OracleDiagnosticsProvider` 所有方法返回 `Unsupported`

**DiagnosticsService 单测：**
- `supportedCapabilities()` 检查拦截：调用 stub 能力时返回 `Unsupported`，不调用 Provider
- `DiagnosticsProviderRegistry` 按 `driverType` 路由正确
- 无当前 connection 时返回结构化错误，不 500

**Action / Controller IT（MockMvc）：**
- `ExplainQueryAction` 与 `POST /diagnostics/explain` 输出结构严格一致
- `IndexHintsAction` 内部串联 EXPLAIN，单次调用返回完整推荐
- 无当前 session connection 时，两端点均返回 HTTP 400 + 结构化错误体
- Stub Action（lock / pool / space）返回 `unsupported: true`，HTTP 200

### 10.2 前端（Vitest）

**DiagnosticsCard：**
- FULL_SCAN 警告 badge 正确渲染（`status.warningSurface`）
- 无推荐时不渲染推荐区块
- "在工作台打开"点击触发正确 Tab 打开 action

**ExplainPlanTree 组件：**
- ScanType → `status.*` token 映射覆盖所有枚举值
- 深层嵌套节点可正确折叠 / 展开
- cost / rows 使用 `mono-sm` 字体类

**DiagnosticsTab：**
- 工具栏 Explain 按钮：无 SQL 时禁用；有 SQL 时可点击
- 点击触发 `POST /diagnostics/index-hints` 并打开 Tab
- Tab 注册为 `TabScope.WORKBENCH`（类型测试）

**ActivityRail DiagnosticsPanel：**
- 空态文案正确渲染
- 有结果时显示摘要行与推荐数量

---

## 11. 后续扩展路径

本 spec 为以下能力预留了接口，每项单独立一个子 spec：

| 能力 | Provider 方法 | 说明 |
|---|---|---|
| 锁分析 | `lockInfo()` | MySQL: `SHOW ENGINE INNODB STATUS` / `information_schema.INNODB_LOCKS`；PG: `pg_locks` |
| 连接池监控 | `connectionPoolInfo()` | HikariCP JMX / `SHOW STATUS LIKE 'Threads%'` |
| 空间占用 | `tableSpaceInfo()` | MySQL: `information_schema.TABLES`；PG: `pg_total_relation_size()` |
| Oracle 实现 | 所有方法 | Oracle 专属 `EXPLAIN PLAN` + `DBMS_XPLAN.DISPLAY` |
| EXPLAIN ANALYZE | 新增方法 | 实际执行计划（需加安全开关，有副作用） |
| 慢查询识别 | 新增方法 | MySQL slow log / PG `pg_stat_statements` |

---

## 12. 文档与计划关联

- 产品总设计：[docs/product-specs/index.md §3.6](./index.md)
- 路线图：[docs/exec-plans/2026-04-25-next-implementation-roadmap-plan.md Task 7](../exec-plans/2026-04-25-next-implementation-roadmap-plan.md)
- 依赖 spec：[Cross-Session Workbench Tabs Design](./2026-04-27-cross-session-workbench-tabs-design.md)（`TabScope.WORKBENCH` 注册表）
- 相关 spec：[Guarded DDL/DML Execution Design](./2026-04-25-guarded-ddl-dml-execution-design.md)（索引推荐执行路径）
- 设计契约：[client/DESIGN.md](../../client/DESIGN.md)
