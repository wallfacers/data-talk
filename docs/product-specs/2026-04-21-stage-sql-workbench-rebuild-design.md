# Stage SQL Workbench Rebuild Design

## 背景

当前 Stage 已存在多轮演进，但现有 SQL 编辑器链路仍然是“单结果、轻编辑器、旧布局补丁”的组合，无法承载可靠的工作台体验。旧的 ER / Report / Dashboard 页面质量不达标，本次不保留旧实现，而是以 SQL 工作台为先，重建可持续扩展的 Stage 主线。

本设计直接替换当前 Stage SQL 编辑器实现，并同步重建其前后端执行链路。目标不是兼容旧工作台，而是交付一个可继续长出结果集展示、图表、ER 图能力的新骨架。

## 目标

- 将 Stage 重建为以 SQL 编辑器为核心的新工作台。
- 前端编辑器主体、顶部编辑器工作区、结果集 Tab 交互对齐 `open-db-studio`。
- 后端将当前单结果 SQL 执行接口重构为多语句、多结果返回模型。
- 结果表格保留 DataTalk 当前组件体系优先，但视觉必须和新工作台完全一致。
- 删除旧的非 SQL Stage 页面与旧 SQL 链路，避免兼容包袱。

## 非目标

- 本次不实现新的图表页、ER 图页、Dashboard 页。
- 本次不复用或保留旧的 ER / Report / Dashboard 占位页面。
- 本次不迁移 `open-db-studio` 的整套结果表格实现、右键菜单、虚拟滚动交互。
- 本次不保留旧 Stage SQL 编辑器的兼容模式或 feature flag。

## 产品边界

### 当前阶段交付

- Stage 壳继续存在，但其核心工作内容收敛为 SQL 工作台。
- Stage 顶部主 Tab 代表 SQL 文档页。
- 每个 SQL 文档页内部包含：
  - 上方编辑器工作区
  - Monaco SQL 编辑器
  - 下方结果集 Tab 条
  - 激活结果对应的结果面板
- 执行支持多语句，并按语句顺序返回多结果。

### 后续扩展位

本次重建完成后，Stage 可以在同一工作台模型下继续长出：

- 查询结果的二次展示能力
- 图表展示能力
- ER 图展示能力

这些能力未来基于新工作台继续扩展，而不是复用本次删除的旧页面。

## 前端设计

### 总体结构

- `StageWindow` 保留为外壳组件，负责标题栏、Stage 布局、侧栏、Stage 顶部主 Tab。
- `StageTabBar` 仅负责 SQL 文档 Tab，不再承载结果集切换。
- `StageTabContent` 只渲染新的 SQL 工作台组件，不再分发旧的 `er_canvas` / `report` / `dashboard` / `unsupported` 页面。
- `StageToolRow` 只保留 SQL 相关动作入口。

### SQL 工作台组件树

- `SqlWorkbenchTab`
  - SQL 工作台总装组件，替代现有 `QueryEditorTab`
- `SqlEditorHeader`
  - 连接、数据库、schema、运行状态、执行动作
- `SqlMonacoEditor`
  - Monaco 编辑器封装，对齐 `open-db-studio` 的编辑区结构与交互
- `SqlResultTabs`
  - 结果集 Tab 条，映射后端返回的 `results[]`
- `SqlResultPanel`
  - 根据当前激活结果项渲染不同面板
- `SqlResultTable`
  - 结果表格，优先复用 DataTalk 当前框架与表格能力，但重做视觉
- `SqlDmlSummaryPanel`
  - DML 汇总面板
- `SqlErrorResultPanel`
  - 错误结果面板

### 视觉约束

- 以 `open-db-studio` 的 SQL 编辑器结构、层级、间距、结果集 Tab 关系为参考。
- 颜色系统必须使用 DataTalk 当前主题 token，禁止直接搬 `open-db-studio` 主题色。
- 能用现有 UI 框架组件的地方优先用现有组件；框架不能满足时，再自己实现。
- 结果表格允许沿用 DataTalk 当前表格能力，但视觉必须与新工作台整体一致：
  - 背景层次
  - Tab 高亮
  - 边框密度
  - hover / empty / error / running 状态

## 前端状态模型

### Stage 级状态

`useStageStore` 继续负责：

- Stage 顶部主 Tab 的打开、关闭、激活
- Stage 最大化、侧栏展开收起
- session/workspace 层的 Stage 壳状态

### SQL 工作台级状态

新增 `useSqlWorkbenchStore`，按 `tabId` 管理每个 SQL 文档页状态：

- `sqlText`
- `executeStatus`
- `results`
- `activeResultId`
- `resolvedContext`
- `contextNotice`

结果项结构：

```ts
type SqlWorkbenchResultItem = {
  resultId: string
  kind: 'result_set' | 'dml_summary' | 'error'
  title: string
  statementIndex: number
  statementText: string
  columns: string[]
  rows: unknown[][]
  rowCount: number
  executionMs: number
  truncated: boolean
  affectedRows?: number | null
  errorMessage?: string | null
}
```

### 行为约束

- 切换 Stage 顶部主 Tab 时，每个 SQL 文档页的编辑内容和结果集保持不丢失。
- 同一个 SQL 文档页重新执行时，整体替换该 tab 的当前结果集列表。
- 关闭 SQL 文档页时，连同其工作台状态一起清理。
- 启动时若 store 中残留非 SQL 的旧 Stage tab，直接过滤掉，不渲染。

## 后端设计

### 执行接口

本次直接重构现有 `POST /api/sql/execute`，不保留旧的单结果契约。

请求仍然沿用现有上下文字段：

- `connectionId`
- `sessionId`
- `database`
- `schema`
- `source`
- `sql`

响应改为：

```ts
type SqlWorkbenchExecuteResponse = {
  resolvedContext: ResolvedDataContext | null
  contextNotice: string | null
  results: Array<{
    resultId: string
    kind: 'result_set' | 'dml_summary' | 'error'
    title: string
    statementIndex: number
    statementText: string
    columns: string[]
    rows: unknown[][]
    rowCount: number
    executionMs: number
    truncated: boolean
    affectedRows?: number | null
    errorMessage?: string | null
  }>
}
```

### 执行语义

- 对输入 SQL 进行多语句拆分。
- 按语句顺序逐条执行。
- 查询类语句返回 `result_set`。
- 连续 DML/DDL 语句聚合为单个 `dml_summary`。
- 某条语句失败时产出 `error` 项，并保持结果顺序与原 SQL 一致。

### 风险控制

- 风险控制保留，但按批量执行语义重排。
- 用户手动执行时，先对整批语句做风险分析。
- 必须阻断的高风险语句命中时，整批不执行，直接返回 `422`。
- AI 来源保持现有风险豁免边界，遵从当前产品策略。

### 上下文解析

保留并继续使用当前 DataTalk 的 session/context 自动解析链路：

- `connectionId`
- `database`
- `schema`
- session 继承
- 表定位自动补全

新工作台只重构执行模型，不推翻当前上下文解析体系。

## 拆除与迁移

### 前端拆除

- 废弃旧 SQL 组件：
  - `query-editor-tab.tsx`
  - `query-editor-toolbar.tsx`
  - `query-editor-result-panel.tsx`
  - `use-sql-execute.ts`
- 删除旧 Stage placeholder 路径：
  - `stage-placeholder-tab.tsx`
  - `StageTabContent` 中的非 SQL 分发
- 删除旧 Stage 非 SQL 入口与渲染测试

### 后端拆除

- 重写现有 `SqlExecuteController` 与 `SqlExecuteService`
- 废弃旧单结果 DTO 结构
- 用新多结果 DTO 替换客户端 `SqlResult` 契约

## 测试策略

### 前端

- 重写 Stage SQL 编辑器相关单测，覆盖：
  - SQL 文档 Tab 渲染
  - Monaco 编辑器挂载与内容持久
  - 多结果集 Tab 渲染与切换
  - `result_set / dml_summary / error` 三类面板
  - 重新执行后的结果替换
  - 关闭 tab 的状态清理
  - 旧非 SQL tab 被过滤

### 后端

- 重写 `/api/sql/execute` 集成测试，覆盖：
  - 单语句查询
  - 多语句混合
  - 连续 DML 聚合
  - 中间语句失败
  - 风险阻断
  - session/context 继承

### 构建验证

- `cd client && npx tsc --noEmit`
- `cd server && mvn compile -q`
- 前后端目标测试集通过后，才算本次重构完成

## 风险与控制

- 风险：一次性替换链路较大
  - 对策：前端/后端各自先以测试锁边界，再并行实现
- 风险：旧 Stage store 残留脏状态影响启动
  - 对策：在 Stage tab 读取入口统一做旧类型过滤
- 风险：Monaco 集成导致前端测试不稳定
  - 对策：保持编辑器 wrapper 轻薄，测试中统一 mock Monaco 外层适配

## 结论

本次不是给现有 Stage 打补丁，而是直接把 Stage SQL 主线整体替换为新的 SQL 工作台。旧的非 SQL 页面和旧 SQL 单结果链路全部退场；新的工作台先交付 SQL 能力，但其状态模型和结果模型必须能继续支持后续图表与 ER 展示扩展。
