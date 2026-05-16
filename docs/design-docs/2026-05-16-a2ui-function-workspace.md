---
status: review
title: A2UI + Function Pool 工作台架构（下一代）
date: 2026-05-16
audience: 平台架构、后端、前端、AI Agent 工程方向
---

# A2UI + Function Pool 工作台架构（下一代）

## TL;DR

把 DataTalk 工作台围绕一个核心抽象——**Function**——重新组织。所有"能在系统里发生的事情"都是 Function：执行 SQL 是 Function，渲染 KPI 卡片是 Function，打开一个 Tab 是 Function，编排一个 Dashboard 也是 Function。

A2UI（[a2ui.org](https://a2ui.org/) Composer 协议，v0.9 草案）作为**"调用 UI 类 Function 的声明式语法"**——它不是独立体系，而是 Function 模型在 UI 层的具体形态：surface、component、dataModel、userAction 都是为了让 Agent 用一致方式描述"想让用户看到什么"。

云厂商 **Function 调用池**思想体现在两个层面：(a) Agent 像消费 Lambda 一样消费现有 Function；(b) Agent 在池子里找不到所需能力时**现写、现编译、现部署**新 Function（Live Synthesis），通过严格的沙箱与治理保证安全。

## 1. 背景

### 1.1 DataTalk 当前协议形态

DataTalk 当前的 AI ↔ UI 交互是**命令式 RPC**：

- 后端定义 `@DataTalkAction` 注解的 Spring Bean（`ActionHandler<I, O>`），Spring 启动时由 `ActionRegistry` 自动注册
- `ActionDispatcher` 支持三种执行模式：`SERVER`（后端同步）、`OPENCODE`（AI 侧执行）、`CLIENT`（推送到前端）
- 通过 `SessionBus`（per-session 事件总线、16ms 批量合并）发送 `DtEvent.ActionInvoke` SSE 帧到前端，前端执行后回 `action_result`（JSON-RPC 风格）
- UI 主导权在前端：`useStageStore` 管 Tab 生命周期，13 种 Tab 类型在 `tab-type-registry.ts` 中静态注册；后端只能通过 `datatalk.ui.patch`（RFC 6902 JSON Pointer）修改已有 Tab 内容，**不能直接打开/关闭 Tab**
- Dashboard widget 类型（chart / kpi / table 等）在 Bezel schema 中**静态枚举**，运行时无法新增 widget 类型

这种形态的局限：

1. UI 长成什么样写死在前端代码里——Agent 想要一个"双轴 KPI + 实时刷新"组件就得等开发者发版
2. 能力扩展依赖 Spring Bean 部署——加一个 Action 需要重启服务
3. UI 描述（payload）和能力调用（action）是两套语义，Agent 要同时理解两个心智模型

### 1.2 A2UI Composer 协议要点

A2UI 是一个**面向 LLM 生成的、声明式的 UI 协议**。核心特性：

| 维度 | A2UI v0.9 形态 |
|------|----------------|
| 协议风格 | 声明式（Agent 描述目标 UI，前端 diff 渲染） |
| 组件树 | **邻接表**（扁平 list + ID 引用），非嵌套 JSON——LLM 流式生成友好（见下注） |
| 状态 | 组件结构与 dataModel **分离**；组件通过 JSON Pointer 绑定到数据 |
| 数据模型 | 每个 surface 一棵 JSON 树，按 path 增量更新（接近 JSON Patch 语义） |
| 事件回传 | `userAction { name, surfaceId, sourceComponentId, context }`，前端预 resolve 绑定值 |
| 传输 | SSE（Agent→Client）+ REST（Client→Agent） |
| 扩展 | Catalog 机制——可自定义组件库；客户端通过 `supportedCatalogIds` 协商 |

> **邻接表对 LLM 友好的原因**：LLM 可以逐条 emit component 对象，不需要维护嵌套括号平衡；增量更新只改受影响的节点 ID，不触动整棵树；解析端按 ID 装配，错位风险显著低于嵌套树。

核心消息（v0.9）：

- `createSurface { surfaceId, catalogId, theme?, sendDataModel? }`
- `updateComponents { surfaceId, components: [...] }`
- `updateDataModel { surfaceId, path, value }`
- `deleteSurface { surfaceId }`

A2UI 与 DataTalk 现状的**亲和性**很高：

- A2UI surface ≈ DataTalk Stage Tab
- A2UI updateDataModel ≈ DataTalk ui.patch
- A2UI userAction ≈ DataTalk action_result（但语义更准确）
- A2UI catalog ≈ DataTalk Bezel widget schema 的可扩展版本

### 1.3 云厂商 Function 思想的迁移价值

云厂商 Function（Lambda / Cloud Run / Workers）的关键性质：

1. **统一可调用单元**：一切能力以"输入→输出"函数形式暴露
2. **schema-first**：调用契约（input/output schema）是第一公民，运行时实现可换
3. **运行时部署**：热部署，不需要主进程重启
4. **沙箱执行**：与控制面隔离，受配额管控
5. **可发现**：通过注册表/目录索引

把这套思想搬到 DataTalk：**Agent 能力扩展不再是"加 Spring Bean → 重启服务"，而是"调用 registry.create → 几秒内可用"**。AI 是这个 Function Pool 的**主要消费者**，也是**合法的生产者**之一。

## 2. 设计目标与不变量

### 2.1 目标

- **G1**：建立**唯一**的可调用单元抽象——Function——统辖现有 Action、UI 渲染、Tab 生命周期、widget 组件
- **G2**：让 Agent 通过 schema 自描述发现所有能力（`registry.list / get`），无需读懂任何业务代码
- **G3**：支持运行时新增 Function（dynamic kind，GraalJS / 受限前端沙箱），AI 可在会话内"秒上架"新能力
- **G4**：UI 层接入 A2UI Composer 协议（v0.9 优先），让 Agent 用声明式 surface + dataModel 描述工作台任意区域
- **G5**：保留 DataTalk 现有刚性能力（JDBC 连接管理、SQL 风险确认、SSRF 防护、ingestion artifact 路径约定、撤销日志）不被 dynamic function 绕开

### 2.2 不变量

- **I1**：刚性能力**只能**是 `kind=native` 的 Function（Spring Bean），永不允许由 AI 重写或绕过
- **I2**：dynamic function 不能直接访问资源（JDBC、文件系统、网络、Spring `ApplicationContext`），只能通过 Capability Broker
- **I3**：UI catalog 的 component renderer 在**早期阶段必须由平台开发者签发**；AI 只能组合现有 component，不能新增 component 类型——AI 提交新 component 类型须走人工审核队列
- **I4**：所有 Function 的 create / invoke / quarantine / revoke 事件**必须**写 `function_audit_log`
- **I5**：高风险 Function 调用（写库、删数据、跨租户、外发请求）**必须**经用户审批 surface，确认结果作为 `userAction` 回传 Gateway 校验
- **I6**：A2UI surface 的 dataModel 是该 surface 状态的 single source of truth；前端断线重连时由 Gateway 重放当前 snapshot

## 3. 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│ Agent (OpenCode)                                            │
│   tool calls: registry.list / registry.create / fn.invoke   │
└────────────────────────────┬────────────────────────────────┘
                             │ A2UI Wire Protocol (SSE + REST)
                             │   - createSurface / updateComponents
                             │   - updateDataModel
                             │   - userAction（用户事件回传）
                             │   - functionInvoke（非 UI function 调用）
┌────────────────────────────┴────────────────────────────────┐
│ Spring Boot · Function Gateway                              │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Function Registry（统一注册中心）                        │ │
│ │  ├─ Native:  @DataTalkAction 现有 Bean（刚性能力）        │ │
│ │  ├─ Dynamic: GraalJS / JSR-223 热部署函数（AI 创建）      │ │
│ │  └─ UI Catalog: Bezel/Stage surface component 定义        │ │
│ └─────────────────────────────────────────────────────────┘ │
│  Function Compiler · Backend Sandbox · Capability Broker    │
│  Surface Composer · Catalog Store · Audit Log               │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────┴────────────────────────────────┐
│ Tauri + React · Function Host                               │
│  A2UI Renderer（surface 管理 / dataModel diff）              │
│  Frontend Sandbox（Web Worker · iframe · Shadow Realm）     │
│  Frontend Capability Registry（前端 native function：       │
│    stage.openTab / clipboard.write / route.navigate ...）   │
└─────────────────────────────────────────────────────────────┘
```

三个不可妥协的点：

1. **协议入口只有 Function Registry**——Agent 不直接发 SSE 帧、不直接知道 Stage Tab 存在；它只看到一池子带 schema 的 Function，部分返回 UI（surface），部分返回数据/计算结果
2. **A2UI Surface = Stage Tab**——一个 surface 对应一个 Tab；后端 `createSurface` 等于"开 Tab"，`deleteSurface` 等于"关 Tab"；`useStageStore` 改造为 surface 注册器
3. **Function 是名词，A2UI 是形容词**——A2UI 描述"那种返回 UI 的 Function 长什么样"

**SessionBus 不受本设计影响**：现有 SessionBus（per-session 事件总线 + 16 ms 批量合并）继续作为后端事件分发通道；A2UI surface 消息（createSurface / updateComponents / updateDataModel / userAction）通过 SessionBus 流转，只是新增的事件 case，不改变 SessionBus 的契约与性能特征。

## 4. 核心抽象词典

> 这一节是给后续 AI 与新工程师**照着实现**的术语字典，每个术语给出：定义 / 形态 / 生命周期 / 与现有概念映射 / 最小示例。

### 4.1 Function（函数）

**定义**：系统中**唯一的可调用单元**。任何"做一件事"的能力都建模为 Function。

**形态**（JSON，存储在 Function Registry）：

```json
{
  "id": "datatalk.sql.execute",
  "version": "1.2.0",
  "kind": "native | dynamic | ui",
  "title": "Execute SQL against a connection",
  "description": "Agent 看到的自然语言描述",
  "inputSchema":  { /* JSON Schema */ },
  "outputSchema": { /* JSON Schema */ },
  "sideEffects": ["db.read", "audit.write"],
  "executor": {
    "runtime": "spring-bean | graaljs | webworker | iframe",
    "ref": "com.datatalk.SqlExecuteHandler#invoke"
  },
  "ownership": {
    "createdBy": "system | agent:<sessionId> | user:<userId>",
    "createdAt": "2026-05-16T...",
    "tenant": "..."
  }
}
```

**生命周期**：`drafted` → `compiled` → `registered` → `deprecated` → `revoked`

**与现有概念映射**：

- `@DataTalkAction` 注解的 Spring Bean → `kind=native` Function
- `executor=SERVER/OPENCODE/CLIENT` → 合并为 `executor.runtime`
- `actionId` → `function.id`

### 4.2 Function Kind（函数三类）

| Kind | 谁实现 | 在哪跑 | 能力上限 | 可被 AI 创建 |
|------|--------|--------|----------|--------------|
| **native** | Spring Bean（Java/Kotlin） | 后端 JVM 主进程 | 完整（JDBC、文件系统、配置） | ❌ 仅平台开发者 |
| **dynamic** | GraalJS（JavaScript） | 后端 GraalJS 沙箱 | 受限（仅 Capability Broker 授权的能力） | ✅ AI 可创建 |
| **ui** | Catalog Component（声明式 JSON + 白名单 hook） | 前端 React / iframe 沙箱 | 受限（仅前端 Capability Registry 授权的能力） | ✅ AI 可组合既有 component；新增 component 类型须经人工审核 |

**关键规则**：刚性能力（JDBC 连接管理、SQL 风险确认、SSRF 防护、ingestion artifact 路径）**只能**是 `native`。dynamic function 想用必须经 Capability Broker，broker 负责风险确认、审计、配额。

### 4.3 Surface（A2UI UI 区域 = Stage Tab）

**定义**：A2UI 协议里的 UI 容器，对应屏幕上一块可见的、独立可寻址的区域。

**形态**：

```json
{
  "surfaceId": "stage.tab.query_editor.<uuid>",
  "catalogId": "datatalk.stage.v1",
  "root": "root_node",
  "theme": { "primaryColor": "#..." },
  "ownership": { "sessionId": "...", "scope": "workspace | session" }
}
```

**生命周期**（A2UI 标准消息）：

- `createSurface` → 前端按 surfaceId 创建容器（≈ `useStageStore.addTab()`）
- `updateComponents` → 推送/更新组件邻接表（≈ 当前的 `ui.patch` 但语义更纯）
- `updateDataModel` → 推送/更新数据（≈ Tab payload 更新）
- `deleteSurface` → 销毁容器（≈ `useStageStore.removeTab()`）

**与现有概念映射**：

- 现有 `StageTab`（13 种类型）→ 13 种 surface 模板，每个模板对应一个**返回 surface 的 ui function**：`stage.openQueryEditor` / `stage.openDashboard` / ...
- `tab-type-registry.ts` → 拆为 (a) Native function 注册（开 Tab 的入口）、(b) Catalog 注册（surface 用哪个组件库）

### 4.4 Component（A2UI 组件节点）

**定义**：surface 内邻接表的一个节点。**只是数据**，不是代码——前端按 catalogId 找到对应 renderer 去画。

**形态**（v0.9 风格，扁平）：

```json
{
  "id": "kpi_main",
  "component": "KpiCard",
  "label": "GMV (Today)",
  "value": { "$bind": "/metrics/gmv" },
  "trend": { "$bind": "/metrics/gmv_trend" },
  "onClick": "drill_down"
}
```

**与现有概念映射**：Bezel widget（`kpi_w_xxx`、`chart_w_xxx`、`table_w_xxx`）→ Catalog 内的 Component 类型；widget id regex `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$` 保留为 component.id 的命名约束之一。

### 4.5 DataModel（surface 的数据源）

**定义**：每个 surface 拥有一棵 JSON 数据树，组件通过 `{"$bind": "/path"}` 绑定到树上的字段。数据变了组件自动重渲染。

**形态**：任意 JSON 对象，按 JSON Pointer 寻址。

```json
{
  "metrics": { "gmv": 12345, "gmv_trend": [/* ... */] },
  "filters": { "dateRange": "7d" }
}
```

**消息**：`updateDataModel { surfaceId, path: "/metrics/gmv", value: 12345 }`，不带 `value` 即删除。

**与现有概念映射**：现有 Tab `payload`（散落字段）→ surface 的 dataModel（结构化、可绑定）；现有 `ui.patch` JSON Pointer 操作 → 直接对应 `updateDataModel`（协议形状很接近，可平滑迁移）。

### 4.6 userAction（用户事件回传）

**定义**：用户在 surface 上点击 / 输入 / 选择后，前端向 Gateway/Agent 发送的事件消息。与 A2UI 标准对齐，是 Agent 与用户互动的唯一回路。

**形态**：

```json
{
  "userAction": {
    "name": "drill_down",
    "surfaceId": "stage.tab.dashboard.xxx",
    "sourceComponentId": "kpi_main",
    "timestamp": "2026-05-16T...",
    "context": {
      "metric": "gmv",
      "dateRange": "7d"
    }
  }
}
```

`context` 是前端 resolve 所有绑定值后塞入的快照——Agent 拿到的就是"用户点击时屏幕上的真值"，不需要再回查 dataModel。

**与现有概念映射**：现有 `action_result`（CLIENT executor 的回写）→ 改为 `userAction`，语义从"客户端执行完了"转为"用户做了什么"。

### 4.7 Catalog（组件 + 函数的目录）

**定义**：一组绑在一起、可独立部署的 Component + Function 集合。类比：npm package、Cloudflare Worker bundle。

**形态**：

```json
{
  "catalogId": "datatalk.dashboard.v1",
  "version": "1.0.3",
  "components": [
    { "name": "KpiCard",   "propsSchema": {/*...*/}, "renderer": "..." },
    { "name": "ChartLine", "propsSchema": {/*...*/}, "renderer": "..." }
  ],
  "functions": [
    { "id": "dashboard.compose", "kind": "dynamic", "source": "..." }
  ],
  "permissions": { "needs": ["fn:datatalk.sql.execute"] }
}
```

**生命周期**：catalog 是热部署的单位——上传 → 编译 → 沙箱审查 → 整体生效；卸载也是整体。

### 4.8 Function Registry（注册中心）

**定义**：后端**唯一的真理源**，持久化所有 Function 元数据 + 源码 + 编译产物。

**职责**：

- `list({kind?, tag?, query?})`：Agent 发现池子里有什么
- `get(id, version?)`：拿单个 Function 的 schema
- `create(spec)`：提交新 dynamic/ui Function，触发编译 + 沙箱
- `invoke(id, input, ctx)`：调用（同步或异步）
- `deprecate(id)` / `revoke(id)`：治理操作

**持久化**：DataTalk 现有 SQLite 元数据库新增表 `function_registry`、`catalog_registry`、`function_audit_log`。

### 4.9 Capability Broker（权限/资源中介）

**定义**：dynamic / ui Function **唯一**能调到刚性能力（JDBC、文件、网络）的通道。集中做权限校验、配额、审计、风险确认。

**调用形态**（dynamic JS function 内部）：

```javascript
export async function invoke(input, ctx) {
  // 不能 import jdbc，不能 fetch 外网，只能通过 broker
  const rows = await ctx.broker.call("datatalk.sql.execute", {
    connectionId: input.connectionId,
    sql: "SELECT * FROM orders WHERE amount > ?",
    params: [input.threshold]
  });
  return { rows };
}
```

Broker 对每次 `call` 做：检查调用方 Function 的 `sideEffects` 声明是否覆盖此请求 → 实际请求资源 → 高风险时拉起审批 surface → 审计写入。

### 4.10 Live Synthesis（运行时合成新 Function 的工作流）

**定义**：Agent 发现池子里没有需要的 Function 时，**现写、现编译、现部署、现调用**的完整流程。这是"云厂商 Function 体验"的核心。

**典型时序**：

```
1. User:  "帮我把订单表按月聚合，渲染成柱状图"
2. Agent: registry.list({query: "monthly aggregate"}) → 空
3. Agent: registry.create({
     id: "user.<sid>.agg_orders_by_month",
     kind: "dynamic",
     inputSchema: {...},
     outputSchema: {...},
     source: "export async function invoke(input, ctx) { ... }"
   })
4. Gateway: 编译 → GraalJS 沙箱预检 → 落库 → 返回 functionId
5. 【可选 yellow 风险】用户审批 surface："Agent 想新建一个 function 'agg_orders_by_month'，查看代码 / 同意 / 拒绝"
6. Agent: fn.invoke(functionId, { connectionId, table: "orders" })
7. Agent: 把结果包成 surface（用 ui catalog 里的 ChartBar），下发到 Stage
```

**治理钩子**：所有 create/invoke 进 `function_audit_log`；用户可在工作台看到"本次会话 Agent 新建的 Function"列表，一键回滚单个或批量撤销。

## 5. 数据流时序

### 5.1 场景 A · 已有 Function：问→渲染→交互

```
User                Agent (OpenCode)         Function Gateway         Function Host (前端)
 │                       │                         │                         │
 │ "今天 GMV 怎么样"     │                         │                         │
 │──────────────────────▶│                         │                         │
 │                       │ registry.list(          │                         │
 │                       │   query="GMV today")    │                         │
 │                       │────────────────────────▶│                         │
 │                       │◀────[fn list + schema]──│                         │
 │                       │                         │                         │
 │                       │ fn.invoke(              │                         │
 │                       │   "biz.metric.gmv",     │                         │
 │                       │   {date: "2026-05-16"}) │                         │
 │                       │────────────────────────▶│                         │
 │                       │                         │─┐ Capability Broker     │
 │                       │                         │ │ → datatalk.sql.execute│
 │                       │                         │◀┘ (native)              │
 │                       │◀──────[fn result]───────│                         │
 │                       │                         │                         │
 │                       │ fn.invoke(              │                         │
 │                       │  "stage.openKpiCard",   │                         │
 │                       │  {value: 12345,         │                         │
 │                       │   trend: [...]})        │                         │
 │                       │────────────────────────▶│                         │
 │                       │                         │ createSurface           │
 │                       │                         │─────[SSE]──────────────▶│ surface 创建
 │                       │                         │ updateComponents        │
 │                       │                         │─────[SSE]──────────────▶│ 邻接表 diff
 │                       │                         │ updateDataModel         │
 │                       │                         │─────[SSE]──────────────▶│ 数据填充
 │                       │                         │                         │ ✓ 渲染
 │◀──────────────────────────────────[屏幕看到 KPI 卡片]─────────────────────│
 │                       │                         │                         │
 │ (用户点了一下卡片想下钻)                        │                         │
 │──────────────────────────────────────────────────────────────────────────▶│
 │                       │                         │◀──── userAction ────────│
 │                       │                         │    {name:"drill_down",  │
 │                       │                         │     context:{metric...}}│
 │                       │◀───────[Agent 接收]─────│                         │
 │                       │ ... Agent 继续推理 ...                            │
```

关键点：

- Agent **只与 Function Registry 对话**，不直接发 SSE 消息——所有 SSE 帧由 Gateway 内的 **Surface Composer** 代为生成
- "渲染 KPI 卡片"本身是一次 `fn.invoke("stage.openKpiCard", ...)`，该 Function `kind=ui`，`executor.runtime` 指向 dashboard catalog 的 `KpiCard` component
- 用户事件经 Gateway 回到 Agent，**保留 A2UI userAction 形态**（不再叫 action_result）

### 5.2 场景 B · Live Synthesis：池子里没有，现写现部署

```
User                Agent (OpenCode)         Function Gateway         Function Host (前端)
 │ "按月聚合订单画柱状图"│                         │                         │
 │──────────────────────▶│                         │                         │
 │                       │ registry.list(          │                         │
 │                       │   query="monthly agg")  │                         │
 │                       │────────────────────────▶│                         │
 │                       │◀────[empty]─────────────│                         │
 │                       │                         │                         │
 │                       │ registry.create({       │                         │
 │                       │  id:"user.sid.aom",     │                         │
 │                       │  kind:"dynamic",        │                         │
 │                       │  inputSchema,outputSchema,                        │
 │                       │  source:"<JS code>"})   │                         │
 │                       │────────────────────────▶│                         │
 │                       │                         │─┐ Function Compiler     │
 │                       │                         │ │  - GraalJS parse      │
 │                       │                         │ │  - 静态分析(白名单 API)│
 │                       │                         │ │  - dry-run 沙箱       │
 │                       │                         │ │  - 写 function_audit  │
 │                       │                         │◀┘                       │
 │                       │                         │                         │
 │                       │                         │ ┌─[如果 yellow/red]─────┐│
 │                       │                         │ │ createSurface         ││
 │                       │                         │ │ ("approval_dialog")   ││
 │                       │                         │ │──────[SSE]───────────▶│ 弹"AI 想新建 fn"
 │                       │                         │ │◀── userAction ────────│ 用户点同意/拒绝
 │                       │                         │ └───────────────────────┘│
 │                       │◀───[functionId+ok]──────│                         │
 │                       │                         │                         │
 │                       │ fn.invoke(functionId,   │                         │
 │                       │  {table:"orders"})      │                         │
 │                       │────────────────────────▶│ 沙箱执行 → broker →     │
 │                       │                         │ datatalk.sql.execute    │
 │                       │◀────[rows]──────────────│                         │
 │                       │                         │                         │
 │                       │ fn.invoke(              │                         │
 │                       │  "stage.openChart",     │                         │
 │                       │  {type:"bar",data:...}) │ Surface Composer        │
 │                       │────────────────────────▶│─────[SSE * 3]──────────▶│ 渲染
```

关键点：

- `registry.create` 不是黑盒——**编译期**做静态分析（禁用 `eval` / `Function` / `require('fs')` / `fetch`），只允许调 `ctx.broker.*` 与白名单 API
- 高风险（写库、外网、文件 I/O）触发**用户审批 surface**——审批本身也是 A2UI surface，闭环一致
- 新 Function 默认作用域 `user.<sessionId>.*`，会话结束可选择「保留以复用 / 丢弃」（用户在"AI 函数面板"管理）

### 5.3 错误、超时、版本

| 维度 | 策略 |
|------|------|
| **fn.invoke 超时** | Gateway 默认 30 s（可由 `inputSchema.x-timeoutMs` 覆盖至最大 120 s）→ 超时回 `error.timeout`，Agent 决定重试或换 Function |
| **沙箱逃逸 / OOM** | GraalJS 沙箱配 CPU/内存上限；触限直接 kill 该 invoke，Function 标 `quarantine`；连续 3 次进 `deprecated` |
| **A2UI 协议错误** | 前端 renderer 发 `error` 消息回 Gateway，含 `surfaceId` + 异常类型；Gateway 转发给 Agent，Agent 决定补救 |
| **Function 版本变更** | `id + version` 双键；Agent `registry.list` 拿到 `id@version`；旧版本保留以支持 in-flight 调用，gracefully deprecate |
| **catalog 版本协商** | A2UI 标准的 `supportedCatalogIds` 机制——前端在 SSE 握手时声明能渲染哪些 catalogId，Gateway 选最高匹配 |
| **dataModel 冲突** | 同一 surface 多 Function 并发改 dataModel：按 path 串行化（surface 维度 single-writer），冲突时后写胜出；值发生变化才记审计，等值覆盖记为 `noop` 级别（默认不持久化，仅可调试开关打开时记录） |
| **断线重连** | surface 状态以 dataModel 为 single source of truth；前端重连时 Gateway 重放当前 dataModel snapshot，components 不变即不重传 |

### 5.4 与现有协议的翻译层

迁移期间 `DtEvent.ActionInvoke` 仍存在——Gateway 维护映射表，把旧事件包装成 `fn.invoke`：

```
DtEvent.ActionInvoke(actionId, input)
  ↓ (legacy bridge)
fn.invoke("legacy." + actionId, input)
  ↓ Function Registry 标 kind=native, executor.ref=旧 ActionHandler
```

新代码直接走 `fn.invoke`，旧代码不动；当所有调用方迁完，legacy bridge 整体下线。

## 6. Live Synthesis 治理与安全

这一节是**最容易翻车**的部分。"AI 现写现部署"在 demo 里很惊艳，在生产里没有治理就是定时炸弹。下面是不可让步的红线。

### 6.1 安全红线

| 红线 | 具体含义 | 违反时的后果 |
|------|----------|--------------|
| **R1** | dynamic function 永远不能直连资源（不能 import jdbc、不能 `fetch`、不能 `Files.read`、不能访问 Spring `ApplicationContext`） | 编译期静态分析拒绝注册 |
| **R2** | 刚性能力是 native 专属（JDBC 连接管理、SQL 风险确认、文件上传 SSRF、ingestion 路径约束、撤销日志写入） | dynamic 想用必须经 broker，broker 自审计 |
| **R3** | dynamic function 不能持久化全局状态——只能用 `ctx.scratch`（invoke 结束清空）和 `ctx.store`（**租户 + 用户 + function** 三重作用域隔离） | 越权写入 = 沙箱终止 + Function quarantine |
| **R4** | catalog 不能引入未审核的前端代码——UI catalog 的 component renderer 必须用**声明式 JSON + 白名单 hook**（事件名映射），不允许携带任意 JS | 含 JS 字符串的 catalog 进入待人工审核队列，不直接生效 |
| **R5** | 跨租户 / 跨用户调用必须显式跳板——默认只看到 `ctx.tenantId + ctx.userId`，跨界走 native `tenancy.crossInvoke` 强制审计 | 静态分析 + 运行时双重拦截 |
| **R6** | 审批弹窗不能被 Function 自己关闭——审批 surface 由 Gateway 创建并控制，Function 不能 `deleteSurface` 自己的审批窗 | 尝试即抛 `PermissionDeniedError` |

### 6.2 静态分析白名单

dynamic Function 源码进 Registry 前过编译器 AST 扫描：

**允许的全局**：`ctx`（broker + scratch + store + tenantId/userId）、`console.log`（重定向到审计日志）、`Math`、`Date`、`JSON`、纯函数式标准库。

**禁用的全局/语法**：

```
eval, Function 构造器, with 语句, import (动态), require, fetch,
WebSocket, XMLHttpRequest, globalThis, process, Polyglot, Java,
__proto__ 赋值, Object.setPrototypeOf
```

**必须存在的形态**：

```javascript
export async function invoke(input, ctx) { /* ... */ }
```

顶层不允许副作用语句（赋值、调用），只允许函数声明和 `export`。

不满足 → `registry.create` 返回 `compilation_rejected`，附具体行号。Agent 收到拒绝后重写，**最多 3 次**，超过则停止并向用户报告。

### 6.3 运行时沙箱配额

| 资源 | 默认上限 | 可调（schema 内声明 + 用户授权） |
|------|----------|------------------------------------|
| CPU 时间 | 5 s | 30 s |
| 堆内存 | 64 MB | 256 MB |
| broker 调用次数 | 50 次/invoke | 500 次 |
| 单次 broker 返回数据 | 10 MB | 50 MB |
| 总 wall-clock | 30 s | 120 s |

超限 → invoke 立刻 kill，写 `function_audit_log.kind=quarantine`。同一 Function 24h 内 ≥3 次 quarantine → 自动 `deprecated`，下次 `registry.list` 不返回。

**与现有 native 超时的关系**：broker 调用的 native function 有自己的超时（如 `datatalk.sql.execute` 的 SQL 执行 60 s），**不计入** dynamic function 自身的 CPU 时间配额（CPU 仅记 JS 执行时间），**但计入 wall-clock**。换言之：dynamic function 可以串联多个慢 native 调用，每个 native 调用受自身超时约束，但 dynamic function 总挂钟时间一旦超过其 wall-clock 上限即被 kill（含其中所有 native 调用的累计耗时）。

### 6.4 用户审批 UX 三级分类

| 风险级别 | 触发条件 | UX |
|----------|----------|-----|
| **green**（默读） | 只读 broker 调用（`db.read` / `schema.lookup` / `semantic.query`），无 `store` 写入 | 不弹窗，**事后**在"AI 函数面板"可见 |
| **yellow**（一次确认） | 写 `ctx.store`，或调用 ≥1 个被标 `risky:true` 的 native（如导出文件） | 弹一次性 surface："同意 / 拒绝 / 详情查看源码" |
| **red**（每次确认） | 写库（`db.write`）、删数据（`db.delete`）、跨租户、外发邮件 | 每次 invoke 都弹审批，含影响范围预览（如 affected rows） |

审批 surface 本身是 A2UI surface，统一观感。审批结果作为 `userAction` 回传，Gateway 校验来源后决定放行/拒绝。

### 6.5 可观测性 & 回滚

每个 Function 必须留下：

1. **`function_audit_log`**：created / compiled / invoked / quarantined / deprecated / revoked 全生命周期事件，含 createdBy（哪个 Agent session）、用户审批快照、调用链 trace id
2. **"AI 函数面板"**：工作台一个常驻 surface，列本会话 / 本租户范围内 AI 创建的所有 dynamic Function。极简结构草稿：

   ```json
   {
     "surfaceId": "system.ai_function_panel",
     "catalogId": "datatalk.system.v1",
     "components": [
       {
         "id": "fn_table",
         "component": "FunctionTable",
         "rows": { "$bind": "/functions" },
         "columns": ["id", "createdAt", "createdBy", "riskLevel", "invokeCount", "status"],
         "rowActions": ["view_source", "replay_trace", "revoke"]
       },
       {
         "id": "bulk_bar",
         "component": "ToolBar",
         "actions": ["revoke_last_5min", "revoke_all_in_session", "export_audit_csv"]
       }
     ]
   }
   ```

   - 字段：`id` / `createdAt` / `createdBy`（agent session id）/ `riskLevel`（green | yellow | red）/ `invokeCount`（24h）/ `status`（registered | quarantine | deprecated）
   - 行操作（`rowActions`）：`view_source` 打开源码 viewer surface；`replay_trace` 拉起调用链时序回放；`revoke` 直接撤销并写审计
   - 批量操作（`bulk_bar.actions`）：撤销最近 5 分钟、撤销本会话全部、导出审计 CSV
3. **调用链回放**：选中 fn invoke，看完整 broker 调用树、参数、返回、耗时
4. **配额仪表板**：当前租户的 dynamic Function 数、24h 调用量、quarantine 计数

### 6.6 Catalog 与 dynamic Function 治理差异

UI catalog 风险面更小但攻击面不同：

- 风险小：渲染端无后端权限，最多 XSS 自己
- 攻击面：被 prompt injection 的 Agent 可能塞入"看似正常的 UI 但偷渡用户数据回外部"

策略：

- **catalog 必须由平台开发者签发**（早期阶段）
- AI 早期**只能组合现有 catalog component**（拼装 surface JSON），**不能新增 component 类型**
- 后期才考虑"AI 可提交新 component"，且必须走人工审核队列，不秒上线

这一条比 dynamic Function 严格——UI 类东西用户感知最强，事故影响最大。

### 6.7 与现有安全机制集成

| 现有机制 | 新架构怎么继承 |
|----------|----------------|
| SQL 风险确认门控（确认令牌、撤销日志） | `datatalk.sql.execute` native function 的 sideEffect 含 `db.write` → broker 自动拉起确认 surface + 写 undo_log |
| Ingestion artifact 路径约定（`~/.data-talk/ingestion/<jobId>/...` + 500MB cap） | `ingestion.fetch` native function 内部硬编码路径与限额，dynamic function 不能改 |
| SSRF deny（生产 profile） | 所有 HTTP fetch 走 native `http.fetch`，dynamic 不能直接发请求；e2e profile 通过 broker 切换 |
| 用户偏好 / 连接元数据 | dynamic 只能通过 `ctx.broker.call("preference.read", ...)` 取，不能直读 SQLite |
| BUG 跟踪门控 | Function compile / quarantine / revoke 自动写 `function_audit_log`，不进 `docs/bugs/`（BUG 跟踪保留给"人发现的行为偏差"） |

## 7. 可行性结论

### 7.1 总体可行性：✅ 可行，但属于根本性架构换代

**支持可行的依据**：

1. **协议亲和**：A2UI Composer 协议与 DataTalk 现有 Stage Tab + `ui.patch` 模型在结构上接近——surface ≈ Tab，updateDataModel ≈ ui.patch，userAction ≈ action_result。迁移不是发明新概念，而是把现有事实标准化
2. **现有抽象就是"准 Function"**：`@DataTalkAction` + `ActionHandler<I, O>` + JSON Schema 已经具备 Function 的全部特征，只缺动态注册与沙箱
3. **GraalJS 在 Spring Boot 内成熟**：GraalVM 提供生产级 JS 隔离与配额能力，社区有大量样例
4. **A2UI 协议本身可用**：v0.8 已 stable，v0.9 草案语法更适合 LLM 生成；可以从 v0.8 起步，等 v0.9 成熟切换

**风险与限制**：

1. **三件并行重构**：协议 + Function Pool + 渲染层同时演进，工程复杂度叠加；任何一条线 stall 都会阻塞其他
2. **A2UI 标准在演进**：v0.9 草案，未来可能有 breaking change（虽然 v0.8 已 stable 可作为兜底）
3. **GraalJS 隔离强度需 PoC 验证**：CPU / 内存 / IO 配额在 Spring Boot 共进程内的实际效果，需要在真实负载下验证（OOMKiller 行为、长尾延迟、JIT 副作用）
4. **Live Synthesis 的安全模型**：prompt injection 通过 Function 攻击是新的威胁面——AI 写出来的 Function 看似无害但组合后越权，需要在静态分析 + 运行时拦截 + 审计 三层防御
5. **现有 60+ Action / 22 种 DtEvent / 13 种 Tab 的迁移成本**：纯工程工作量大，必须有翻译层（5.4 节）支撑渐进迁移
6. **Tauri 进程模型**：前端 Web Worker / iframe 沙箱在 Tauri 中的行为与浏览器存在差异，需要 PoC

### 7.2 不能做的部分（砍掉或保留现状）

按用户"必须适配协议、不满足就砍需求"的原则，需识别**不该砍**的反向清单：

| 必须保留刚性，不进入 A2UI/Function 改造范围 |
|---------------------------------------------|
| JDBC 连接生命周期与连接池管理 |
| SQL AST 风险分级（L1/L2/L3）与执行确认门控 |
| 撤销日志（undo_log）与撤销流程 |
| 文件上传 SSRF 防护与 ingestion artifact 路径约定 |
| 用户偏好 / 模型配置 / 提供商管理（涉及凭证安全） |
| OpenCode 进程嵌入与生命周期管理 |
| BUG 跟踪与 docs/bugs/ 流程 |

这些以 `kind=native` Function 出现在 Registry，**只能由平台开发者实现**，AI 即便能 `registry.list` 看到，也不能 `registry.create` 同 id 的替代品。

### 7.3 推荐路径骨架

> 以下推荐路径**刻意将三件事解耦为顺序推进**，正是为了化解 §7.1 中指出的"三件并行"工程复杂度风险——每个阶段都有独立可验收的产出，单边失败不阻塞其他线。

按"Function Pool 先行、A2UI 后接"的思路（方案 C），阶段顺序：

1. **能力层先动**：把 `@DataTalkAction` 重构为 Function Registry，支持 GraalJS 热部署 dynamic function——这一步即可让 AI 体验"秒上架 Function"
2. **协议层接入**：引入 A2UI Wire Protocol（v0.9，必要时回落 v0.8），UI function 走 surface/component/dataModel/userAction 四件套
3. **UI 三线渐进迁移**：Stage Tab → Dashboard widget → Chat 流式卡片，每条线独立节奏
4. **治理层并行**：Capability Broker、审批 surface、AI 函数面板、function_audit_log 与协议同步建设
5. **旧协议下线**：所有调用方迁完后移除 `DtEvent.ActionInvoke` 命令式 RPC

> 本文档**不展开** roadmap 时间表与里程碑——该工作量需要单独规划，并与产品节奏对齐。本设计的目标是把"目标态"定义清楚，让任何后续 AI / 工程师能照着实施。

## 8. 风险与开放问题

### 8.1 待 PoC 验证

| 项 | 验收条件 |
|----|----------|
| GraalJS 沙箱在 Spring Boot 共进程内的 CPU / 内存配额实际效果 | 100 并发 invoke 下，单 Function 超限不影响同进程其他 Function；配额误差 < 10%；OOM 触发 kill 不导致主进程崩溃 |
| A2UI v0.9 草案在 Tauri WebView 中的渲染性能（大 surface、高频 updateDataModel） | 单 surface 含 200 个 component、dataModel 1 Hz 更新时，主线程 frame time P95 < 16 ms |
| dataModel 高频更新（如实时刷新的 KPI）在 SSE + diff 渲染下的端到端延迟 | Gateway 发出 `updateDataModel` 到前端完成渲染 P95 < 200 ms（局域网） |
| 静态分析白名单的覆盖率——能否拦截 Prompt Injection 写出的常见越权代码 | 在 ≥50 例红队 prompt（覆盖 R1–R6 红线场景）下，0 例通过编译进入 Registry；合法 dynamic function 误报率 < 5% |

### 8.2 待设计

- [ ] Catalog 版本发布流程（开发者签发、签名验证）
- [ ] dynamic function 持久化策略（按会话 / 按用户 / 按租户的具体语义边界）
- [ ] "AI 函数面板" surface 的精确 schema 与交互
- [ ] 多 Agent 并发场景下的 Function Registry 一致性（同 session 多线程 Agent 并发 `registry.create`）
- [ ] 离线/弱网下 dataModel snapshot 的本地缓存与冲突解决

### 8.3 开放问题

- A2UI Composer 协议自身的演进节奏与 DataTalk 升级节奏如何对齐？是否需要 fork 一个"DataTalk A2UI Profile"作为兼容缓冲？
- Function Pool 是否需要**跨租户共享层**（公共 function 市场）？长期还是仅限单租户内？
- Live Synthesis 生成的 function 是否可以反向"晋升"为 native（人工 review 通过后纳入 Spring Bean）？

## 9. 与现有文档的关系

- **延续并扩展**：[core-beliefs.md](core-beliefs.md) §3「Action 即能力边界」——本文档把 `Action Registry` 演进为更广义的 `Function Registry`，原有"不允许绕过 Registry 的后门能力"原则**继续生效**，且扩展到 dynamic / ui 两类新成员
- **延续**：[core-beliefs.md](core-beliefs.md) §5「事件驱动优先」与 §6「AI 即推理层，后端即执行层」——A2UI surface 消息流由 SessionBus 同等承载（不改变其契约），OpenCode 仍只通过 Function 请求能力
- **取代**：`docs/exec-plans/` 中所有命令式 RPC 风格的 Action 设计（迁移完成后归 stale）
- **依赖**：本设计的实施会显著触及 `client/src/stores/stage-store.ts`（`useStageStore` Tab 生命周期）、`client/src/features/stage/registry/tab-type-registry.ts`（Tab 类型注册）与 [client/DESIGN.md](../../client/DESIGN.md) 的视觉契约——任何具体落地 OpenSpec change **必须**先与上述三处对齐
- **新增外部依赖**：A2UI 官方规范——[a2ui.org/specification/v0.8-a2ui/](https://a2ui.org/specification/v0.8-a2ui/) 与 [a2ui.org/specification/v0.9-a2ui/](https://a2ui.org/specification/v0.9-a2ui/)

---

**作者**：wallfacers · 与 Claude（brainstorming）共笔
**版本**：draft v1（2026-05-16）
