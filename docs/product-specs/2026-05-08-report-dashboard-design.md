# Report / Dashboard Design

- **Date**: 2026-05-08
- **Status**: Draft — pending codex review
- **Owner**: wallfacers
- **Related**:
  - [client/DESIGN.md](../../client/DESIGN.md) — 设计合同（必读约束）
  - [docs/product-specs/index.md](./index.md) §3.5 / §3.11 / §5 — 产品口径
  - [docs/exec-plans/2026-04-25-next-implementation-roadmap-plan.md](../exec-plans/2026-04-25-next-implementation-roadmap-plan.md) Task 8 — Visualization Expansion
  - [docs/product-specs/2026-04-23-ai-text-to-chart-fence-design.md](./2026-04-23-ai-text-to-chart-fence-design.md) — chart fence 协议（Dashboard 复用渲染器）
  - [docs/product-specs/2026-04-20-stage-ui-object-protocol-design.md](./2026-04-20-stage-ui-object-protocol-design.md) — Stage UI Object 协议（ui_read/patch/exec）
  - [docs/product-specs/2026-04-28-shared-stage-workbench-design.md](./2026-04-28-shared-stage-workbench-design.md) — baseVersion / EditConflictMarkdownFormatter
  - [docs/product-specs/2026-04-27-cross-session-workbench-tabs-design.md](./2026-04-27-cross-session-workbench-tabs-design.md) — 跨 session 持久化 + ui_find FTS
  - [docs/product-specs/2026-04-29-er-graph-browsing-design.md](./2026-04-29-er-graph-browsing-design.md) — Stage Tab 双 type 模式参考
  - [docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md](./2026-04-29-opencode-workdir-and-artifact-system-design.md) — file_artifact Part 1-5
  - [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md) — 数据源兼容门
  - [docs/bugs/index.md](../bugs/index.md) — BUG Tracking Gate

---

## 1. 背景与目标

DataTalk 当前的图表能力是**单图工件**：AI 写 ` ```chart ` 围栏，前端内联渲染，"打开到工作台"创建 session 级 `chart_artifact` Tab，单图独立，不可复用、不能组合。这与"对话式生成企业级大屏"的产品愿景之间隔着一个**多 widget 容器**：把多张图、KPI、表格、文本、筛选器组合到同一 Tab，跨 session 持续演进，AI 与用户在不同会话间接力修改。

[index.md §3.5](./index.md) 列了"报表组合（多图表 Dashboard）"为三期可视化候选，[index.md §3.11](./index.md) 把"报表/Dashboard 跨 session 协作"作为持久化工作对象的下一站，[index.md §5](./index.md) 用户场景里"把昨天那个销售看板的 GMV 字段改成万元单位"是该能力的口径锚点。Task 8 ER 切片闭环后，本 spec 把 Dashboard 作为下一个一等可视化 Stage Tab type 落地。

### 目标（按 phase 切片，A 路线 vertical slice）

- 单 Tab 类型 `dashboard`，workspace 持久化，跨 session 可被任意会话打开继续改
- file_artifact 文件持久化，归属 connection，Files Library 可见，可导出可归档
- 12 栏响应式栅格 v1；LayoutEngine 抽象，未来叠加自由画布大屏模式（v2）
- Widget 全集：chart / KPI / table / markdown / filter / section title / divider / image
- 全局 + 局部参数双层；无参数 widget 支持
- AI 编辑 dual-track：` ```dashboard ` 围栏首生成 + ui_patch JSON Patch 增量改
- Hybrid connection：dashboard 默认 + widget 可 override
- 乐观并发 + 自动快照 + 手动命名版本 + 回滚

### 非目标（Out of Scope，详见 §14）

自由画布 layout、auto-refresh / 流式数据、kiosk 全屏模式、theme override、Dashboard 间嵌套引用、Iframe widget、URL 公开分享、运行时实时协作（多人光标）。

---

## 2. Design Inputs（来自 client/DESIGN.md，强制约束）

- `components.chart.focus = accent.primary` / `compare = accent.warn` / `grid = border.subtle` — 与现有 chart fence 共用配色
- 一张图只有**一个**主强调目标；green/red 仅 outcome/health；neutral 承载历史背景系列
- Stage 全局非 per-session：dashboard Tab 全局可见，不随 session 切换变化
- 新 widget 全部映射 semantic token，禁止原色字面量
- 表格 widget 沿用 `table.headerBg / rowHover / rowSelected`，与 `sql-result-table` 同一视觉系统
- Motion 仅确认状态变化（拖拽对齐反馈、widget 增删过渡），`motion.normal + easing.standard`，遵守 `prefers-reduced-motion`
- 控件五态（idle/hover/active/focus/disabled）必须显式映射 token — 工具栏按钮、widget 选中边框、参数控件全覆盖
- 焦点环 `interaction.focusRing`，键盘可达：Tab 走 widget、方向键调位置、Enter 编辑、Esc 退编辑
- 无障碍：图表必须有 accessible name，KPI 大数字必须 ARIA 描述，色彩对比 ≥ 4.5:1

---

## 3. 触点四层

| 层 | 路径 | 改动 |
|---|---|---|
| Agent 提示词 | `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` | 新增 "Dashboards" 节，介绍 ` ```dashboard ` 围栏 + ui_patch 增量改 + `dashboard` Tab type |
| Chat 渲染 | `client/src/features/chat/components/markdown/` 新增 `dashboard-block.tsx`、`dashboard-renderer.tsx`、`dashboard-block-toolbar.tsx`；`markdown.tsx` 加 `decorateDashboardBlocks` | 与 chart fence 同构 |
| Stage Tab | `client/src/features/dashboard/`（新建模块）：`dashboard-canvas.tsx`、`dashboard-tab.tsx`、`widgets/<type>-widget.tsx`、`stores/dashboard-tabs-store.ts`、`stores/parameter-store.ts`、`hooks/use-dashboard-runtime.ts`、`engines/grid-layout-engine.ts`（未来 `engines/canvas-layout-engine.ts`）；注册 `dashboard` 到 `tab-type-registry.ts`，`payloadSource: 'dashboard'` | 主要新增点 |
| Backend | `server/data-talk-application/.../dashboard/DashboardArtifactService.java`、`DashboardSchemaValidator.java`、`server/data-talk-application/.../sql/ParameterizedSqlExecutor.java`（参数绑定 + 安全转义，复用 SqlStatementGuard）、`server/data-talk-adapter/.../actions/RenderDashboardAction.java`（Executor.SERVER）、`DashboardController` REST；file_artifact Part 1-5 复用，新 artifact kind `dashboard` | 复用持久化系统 + 新 action |

---

## 4. 整体数据模型

### 4.1 顶层 Dashboard JSON

```typescript
// 落到 .dashboard.json file_artifact
interface DashboardDocument {
  schemaVersion: 1                          // 从 v1 就有，未来升 schema 时单调递增
  id: string                                // file_artifact id
  title: string
  description?: string                      // markdown 描述
  defaultConnectionId?: string              // 创建时从 session 固化，可后续改
  parameters: ParameterDef[]                // 全局参数，跨 widget 共享
  widgets: Widget[]                         // 所有 widget；位置由 layout 决定
  layout: GridLayout                        // v1 仅 grid，未来 union with CanvasLayout
  version: number                           // 单调递增；ui_patch 必填 baseVersion
  createdAt: number
  updatedAt: number
  // revisions 不存这里 — 走 dashboard_revisions 旁表（§9）
}

interface ParameterDef {
  id: string                                // 'global:<name>' / 'local:<widgetId>:<name>'
  scope: 'global' | 'local'
  ownerWidgetId?: string                    // local 时必填
  name: string                              // SQL 占位符 :name
  type: 'date' | 'date_range' | 'string' | 'number' | 'string_list'
  default: unknown
}

interface Widget {
  id: string                                // 稳定 id，作为 ui_patch target
  type: 'chart' | 'kpi' | 'table' | 'markdown' | 'filter' | 'section' | 'divider' | 'image'
  position: GridPosition                    // { x, y, w, h, z? }
  parameters?: ParameterDef[]               // 局部参数定义
  query?: WidgetQuery                       // chart/KPI/table 必填；其他不需要
  options: unknown                          // 各 widget renderer 守卫
}

interface WidgetQuery {
  connectionId?: string                     // 缺省走 dashboard.defaultConnectionId
  sql: string                               // 占位符 :name 必须出现在 paramRefs 的 key 中
  paramRefs: Record<string, string>         // { sqlPlaceholderName → paramId }，必填，无 regex fallback
}

interface GridLayout {
  engine: 'grid'
  cols: 12
  rowHeight: number                         // px；默认 32
  gap: number                               // px；默认 8
}

interface GridPosition {
  x: number; y: number; w: number; h: number
  z?: number                                // 默认 0；grid 重叠时决定渲染序，canvas 即层级
}
```

### 4.2 关键不变量

- `version` 单调递增；ui_patch 必带 `baseVersion`，不匹配返 409 + EditConflictMarkdownFormatter
- `paramRefs` 是**强制 map**，每个 SQL `:placeholder` 必须在 paramRefs 的 key 中有对应映射；缺失视为 schema error，不存在 regex fallback
- 让依赖图 O(1) 计算 + AI 必须申明意图；从根上消除 `:dateRange` 是 `global:dateRange` 还是 `local:w1:dateRange` 的二义性
- date_range 类型 ParameterDef 允许 sub-accessor：paramId 可写为 `global:dateRange.start` / `global:dateRange.end`，validator 仅在 ParameterDef.type === 'date_range' 时放行 `.start` / `.end`
- `defaultConnectionId` 缺失时 dashboard 仍合法（widget 全自带 connection 也行），渲染前由 runtime 校验
- file_artifact 体积上限 256 KB（与 chart fence 一致）；超限 promote / patch 直接 reject

---

## 5. Widget 契约

每种 widget 的 `options` 由 renderer 守卫；`DashboardSchemaValidator`（前端 + 后端共用同一份 JSON Schema）在 ui_patch、` ```dashboard ` 围栏首生成、file_artifact 落盘三处都要校验。

### 5.1 `chart`

```typescript
interface ChartWidgetOptions {
  title?: string
  echartsOption: Record<string, unknown>      // 与 chart fence 共用 echarts-for-react
  dataMapping: { rowsAsDataset: true }        // v1 固定：SQL 行集塞 echartsOption.dataset.source
  emphasis?: 'cobalt' | 'amber' | 'neutral'   // DESIGN.md 单一主强调
}
```

约束：
- `query` 必填；空 SQL 渲染骨架 + 提示
- 复用现有 `chart-renderer.tsx`、`chart-theme.ts`，bundle 不增
- 流式生成（` ```dashboard ` 围栏未完整时）继续走骨架

### 5.2 `kpi`

```typescript
interface KpiWidgetOptions {
  label: string                                // "今日 GMV"
  valueColumn: string                          // SQL 结果哪一列作主数字
  format: 'number' | 'currency' | 'percent' | 'duration'
  decimals?: number
  prefix?: string                              // "¥"
  suffix?: string                              // " 万"
  delta?: {
    valueColumn: string
    format: 'percent' | 'number'
    direction: 'up_good' | 'down_good'         // 决定 status.success/danger 语义
  }
  sparkline?: { timeColumn: string; valueColumn: string; seriesType: 'line' | 'bar' }
}
```

约束：
- `query` 必填；SQL 返回单行多列；空结果渲染 dash + "暂无数据"
- `delta.direction` 决定数字着色 — `up_good` 时正向用 `status.success`，反之 `status.danger`；color-only 不传递语义，必须配 ↑/↓ 图标
- sparkline 复用 chart renderer 但走简化 option 模板（无轴 / legend / tooltip 详情）

### 5.3 `table`

```typescript
interface TableWidgetOptions {
  title?: string
  columnFormat?: Record<string, ColumnFormat>
  pagination: { pageSize: number }             // v1 仅客户端分页
  density: 'comfortable' | 'compact'
}
type ColumnFormat = { kind: 'number' | 'currency' | 'percent' | 'date' | 'datetime' | 'mono'; decimals?: number }
```

约束：
- `query` 必填；行数上限沿用 SQL 执行的 `maxRows + truncated`
- 复用 `sql-result-table` 内部组件（提取共用函数到 `data-grid` 模块）；headerBg/rowHover/rowSelected 跑同一 token

### 5.4 `markdown`

```typescript
interface MarkdownWidgetOptions {
  text: string
  textAlign?: 'left' | 'center' | 'right'
}
```

约束：
- 无 `query`；纯静态文本
- 复用 `chat/components/markdown/markdown.tsx`，注入 `excludeBlockDecorators: ['chart', 'dashboard']` 防递归
- text ≤ 32 KB

### 5.5 `filter`

```typescript
interface FilterWidgetOptions {
  paramId: string                              // 绑定的参数 id（global 或 local）
  control: 'date_picker' | 'date_range' | 'select' | 'multi_select' | 'text_input' | 'number_range'
  label?: string
  placeholder?: string
  options?: SelectOption[]                     // select / multi_select 静态选项
  optionsQuery?: WidgetQuery                   // 动态选项；其结果加入依赖图
}
```

约束：
- 必须绑定一个**已存在**的 `ParameterDef`；validator 跨 widget 校验 paramId 存在
- `select` / `multi_select` 二选一来源：静态 `options` 或 `optionsQuery`；同时给视为 schema 错
- `optionsQuery` 不能引用绑定的 paramId 自身（防自环）— validator 阻止
- `optionsQuery` 可引用其他参数（如全局 region → 该 region 下的城市列表）；被引用参数变化时 optionsQuery 自动重查 — 该 widget 作为依赖节点出现在依赖图（§7.3）中
- 选项重查后若当前绑定 paramId 的选中值不在新选项列表中：fallback 到 ParameterDef.default（若 default 也不在）→ 首项 → multi_select 时空数组 / 单选时 null
- 控件五态全 token；键盘可达：方向键改值 + Enter 提交 + Esc 复位

### 5.6 `section` (title)

```typescript
interface SectionWidgetOptions {
  title: string
  subtitle?: string
  level: 'h2' | 'h3'                           // typography ui-xl / ui-lg
}
```

无 `query`。仅装饰，dashboard 内分段标题，与画布顶部 dashboard.title 区分。

### 5.7 `divider`

```typescript
interface DividerWidgetOptions {
  orientation: 'horizontal' | 'vertical'
}
```

无 `query`。`border.subtle` 1px 线，占据完整 cell 宽 / 高。

### 5.8 `image`

```typescript
interface ImageWidgetOptions {
  src: string                                  // v1 仅 https://；本地上传 phase 2
  alt: string                                  // 强制非空，无障碍要求
  fit: 'cover' | 'contain' | 'fill'
}
```

约束：
- v1 只接受 `https://` 协议；http / data: / file: 一律 schema reject（XSS / SSRF 防护）
- `alt` 必填非空（DESIGN.md：accessible name）
- 失败回退：占位图 + alt 文本

### 5.9 widget × query 强类型对应

| widget | `query` | `parameters` | 必备校验 |
|---|---|---|---|
| chart | 必填 | 可选 | echartsOption 是 object |
| kpi | 必填 | 可选 | valueColumn 在 SQL select 中 |
| table | 必填 | 可选 | 至少 1 列 |
| filter | 仅 optionsQuery 可选 | 必绑 paramId | paramId 存在且非自身 owner |
| markdown / section / divider / image | 禁止 | 禁止 | 上述各自 |

---

## 6. Layout Engine

### 6.1 抽象接口

```typescript
// client/src/features/dashboard/engines/layout-engine.ts
interface LayoutEngine<L extends Layout = Layout> {
  kind: L['engine']
  validate(layout: L, widgets: Widget[]): ValidationResult
  pack(widgets: Widget[], container: ContainerSize): RenderedPosition[]
  beginDrag(widgetId: string, layout: L): DragSession
  defaultPosition(widgetType: Widget['type']): GridPosition
}

interface RenderedPosition {
  widgetId: string
  rect: { left: number; top: number; width: number; height: number; zIndex: number }
}
```

**为什么抽象**：v2 自由画布大屏是已知未来扩展。把 layout 提到接口背后，v1 只交付 `GridLayoutEngine`，v2 加 `CanvasLayoutEngine` 时 dashboard JSON schema 不动、ui_patch 路径不动、widget 契约不动；只增 `dashboard.layout.engine = 'canvas'` 分支。

### 6.2 GridLayoutEngine v1

- **栅格**：12 列，`rowHeight = 32px`（`dashboard.layout.rowHeight` 可覆盖），`gap = 8px`
- **响应式三档断点**：
  - 容器宽度 ≥ 1024px → 12 栏（桌面）
  - 768-1023px → 6 栏（笔记本竖屏 / 分屏；widget w 自动 ceil(w / 2)，相邻 widget 横向不溢出）
  - < 768px → 单列（移动 / 极窄分屏；保持 widgets 的 y 顺序）
  - 6 栏与单列下禁止拖拽
- **冲突解决**：拖拽 widget A 落到 B 占据的格子，B 向下推；schema 落盘前 validator 强制不重叠
- **位置默认值（w × h，12 栏断点）**：`chart=6×8`、`kpi=3×3`、`table=12×10`、`markdown=12×4`、`filter=3×2`、`section=12×2`、`divider=12×1`、`image=4×4`
- **新 widget 默认 (x, y) 自动排布**：先扫第一行剩余空隙能否容纳 width — 能则填到第一行尾；不能则换行从 x=0 开始；算法等价于 react-grid-layout 的 compact 但仅作用于"新增 widget 的初始位"
- **拖拽与缩放**：8 个边角 + 4 条边的缩放手柄；拖拽时显示对齐网格虚线（motion confirms state change，不装饰）
- **依赖**：`react-grid-layout`（npm 周下载 200K+，active maintenance）；layout-engine 接口在它之上薄包一层

#### 6.2.1 react-grid-layout 序列化对接

- **渲染时**：`DashboardCanvas` 把 `GridPosition[]` 转换为 react-grid-layout 的 `layout` 数组（`{ i: widgetId, x, y, w, h }`）；其余字段（`minW / maxW / static / isDraggable / isResizable`）由 canvas 在 viewer 模式下统一注入 `static: true`，editor 模式下根据当前选中态注入 `isDraggable / isResizable` — 这些运行时属性**不持久化**到 dashboard JSON
- **onLayoutChange 反向**：把新 layout 数组转回 `GridPosition[]`，触发 ui_patch 写入 file_artifact
- **compact**：v1 启用垂直压缩（`verticalCompact = true`）；用户拖完松手后冲突 widget 自动上推，避免长尾空白行

### 6.3 布局不变量（强制 — validator 落盘前阻断）

- grid 模式下任意两个 widget 几何区不允许重叠
- **`z` 字段在 grid 模式下 validator 强制为 0**（非 0 视为 schema 错）；该字段为 v2 canvas layout engine 预留，grid 模式不消费
- 容器 padding 全 dashboard 统一 24px（`spacing.6`），不可 widget override
- widget 外壳的边框、间距、圆角、阴影全走 token；widget options 仅控**内部**配置
- 切 light/dark 主题不改任何几何参数；只换 token 映射
- 拖拽期间 snap 到整数格，松手对齐；不允许浮点 x/y/w/h
- KPI / chart / table 内部 padding 由 token 统一，options 不可覆盖

---

## 7. 参数系统

### 7.1 命名空间

```
global:<name>                    // dashboard.parameters[]
local:<widgetId>:<name>          // widget.parameters[]
<paramId>.<subAccessor>          // 仅 date_range 类型放行 .start / .end
```

强制：
- ParameterDef.scope 与 id 前缀必须匹配；validator 双向校验
- local 参数 `ownerWidgetId` 必须等于其挂载的 widget id
- 同 `name` 允许跨 scope，但 id 不同
- 删除 widget 时连同其 local 参数级联删除；如有外部 paramRefs 引用，validator 阻止删除
- **sub-accessor 限制**：`.start` / `.end` 仅在 ParameterDef.type === `'date_range'` 时合法；其他 type 出现 sub-accessor 直接 schema reject。`stripSubAccessor(paramId)` 用于依赖图按 ParameterDef 维度索引（§7.3）

### 7.2 ParameterStore（per-Tab Zustand）

```typescript
interface ParameterRuntimeState {
  values: Map<string, unknown>            // paramId → 当前运行时值
  dependencies: Map<string, Set<string>>  // paramId → 依赖此参数的 widgetId 集合
  setValue(paramId: string, value: unknown): void
}
```

**关键策略：值不持久化进 dashboard.json**。dashboard JSON 只存 schema + 默认值；运行时值在 ParameterStore 里。

理由：
- Dashboard 是共享工件，不同 session / 用户打开应看到一致初始态
- 用户改筛选不污染原始 dashboard 文件
- 与"自动快照 + 手动版本"语义清晰：版本快照只快照 schema

**v1 运行时值的轻量持久化（避免"上次设的筛选没了"反馈）**：
- 用 `localStorage` 按 key `<dashboardId>.<paramId>` 缓存运行时值，TTL 30 天，每个 dashboard 上限 32 KB（超出按 LRU 淘汰）
- 打开 Tab 优先级：`localStorage` → `ParameterDef.default`
- 用户在历史抽屉点"恢复初始值"会清空当前 dashboard 的所有 localStorage 项
- v2 候选迁移：`dashboard_user_state` 旁表（按 userId × dashboardId × paramId 索引），本 spec 不做

### 7.3 依赖图与重查策略

构建时机：dashboard load + 每次 ui_patch 后重建。

```
graph = new Map()
for widget in dashboard.widgets:
  if widget.query:
    refs = Object.values(widget.query.paramRefs)         // 强制 map，无 fallback
    for paramId in refs:
      graph.get(stripSubAccessor(paramId)).add(widget.id)  // 去掉 .start / .end 子段，按 ParameterDef 维度索引
  if widget.options.optionsQuery:                          // filter 动态选项
    refs = Object.values(widget.options.optionsQuery.paramRefs)
    for paramId in refs:
      graph.get(stripSubAccessor(paramId)).add(widget.id)
```

环检测：filter widget 绑定 paramId X，其 optionsQuery 也引用 X → 自环，validator 拒绝。多 widget 间循环不可能（widget 不能引用别 widget 的 query 结果，仅引用 param）。

重查触发：`setValue(X, v)` → 取 `graph.get(X)` → 并发触发命中 widget 重查（虚拟线程友好）。同 widget 在重查窗口内 debounce 按控件分：
- 滑块 / 文本输入：250ms
- date picker / select：100ms

每次重查带递增 `reqId`；widget 只接受 reqId ≥ 当前的回包，旧请求晚到直接丢。

**批处理与合并**：
- 同一 `setValue` 调用引发的所有依赖 widget 重查合并为**一次并发批次** — 5 个 widget 依赖同一 paramId 时，参数改一次发起 5 个并发 query，不重复发起
- 多 paramId 在 debounce 窗口内连续变化（如"重置筛选"清 3 个参数）：debounce 收尾后**合并依赖集去重**，仅发起一轮重查
- date_range 控件改 start + end 是**单次** setValue（值是 `{ start, end }` 对象），不触发两次 debounce
- optionsQuery 重查与其绑定参数的 setValue 走**同一批次** — 选项重算与依赖该参数的其他 widget 重查并发跑，不串行

### 7.4 SQL 参数绑定（防注入）

后端 `ParameterizedSqlExecutor`：
1. 解析 SQL 中的 `:name` 占位符 — 通过 `widget.query.paramRefs[name]` 查得 paramId（**强制 map，无 fallback**）；缺映射立即 reject，不下发 SQL
2. 通过 paramId 查得 ParameterDef 与运行时值（处理 `.start` / `.end` sub-accessor 抽取 date_range 子值）
3. 翻译为 JDBC `?`，按位置传值
4. 多值参数（`string_list` / `multi_select`）展开为 `(?, ?, ?)` 并按数组长度填位
5. 类型校验：运行时值与 ParameterDef.type 不匹配立即拒绝，不下发 SQL
6. 走现有 `SqlStatementGuard` 风险判级 — 参数化绑定不绕过 L1/L2/L3 风控；widget query 限 L1（SELECT/WITH）
7. 不做字符串拼接；`:name` 不允许出现在 `WHERE col = ':name'`、`SELECT :name FROM t`、`ORDER BY :name` 等"非 value 位置"，validator 拒绝

### 7.5 Filter widget 与参数的 UI 流

```
[date_range filter widget UI]   →   ParameterStore.setValue('global:dateRange', [start, end])
                                            ↓
                                  graph.get('global:dateRange') = {chart_w1, kpi_w2, table_w3}
                                            ↓
                        并发 re-execute 三个 widget query；每个 widget 自己 loading
                                            ↓
                                   widget 内显示 loader + 完成态切换
```

filter widget 自身不触发 dashboard 重渲染，只触发依赖 widgets 的子树重查。

### 7.6 AI 改参数定义

`ui_patch` 添加 / 删除 / 修改 `parameters[]`：
- 添加：直接 push，运行时取 default
- 修改 type / default：所有引用此 param 的 widget 用新 default 重查（保护：旧值如果与新 type 不兼容置为 default）
- 删除：所有 paramRefs 引用此 param 的 widget 报 schema 错（patch reject 409 + EditConflictMarkdownFormatter 提示先解绑）

---

## 8. AI 编辑 Dual-Track 协议

两条通路最终都要通过同一份 JSON Schema 验证 + 同一份 `DashboardSchemaValidator` 守门。

### 8.1 通路 A：` ```dashboard ` 围栏（一次性生成）

**触发**：用户在 chat 里说"帮我生成一个销售看板"，AI 在 markdown 流式输出中写出：

````
```dashboard
{
  "schemaVersion": 1,
  "title": "销售看板",
  "defaultConnectionId": "<from session>",
  "parameters": [...],
  "widgets": [...],
  "layout": { "engine": "grid", "cols": 12, "rowHeight": 32, "gap": 8 }
}
```
````

**流式行为**（与 chart fence 同构）：
- 流式期 `<dashboard-block streaming="true">`：JSON 未闭合，渲染骨架（容器轮廓 + "正在生成 N 个 widget"）
- JSON 闭合 + parse OK：渲染预览（mini 版栅格，所有 widget 实际跑 query）
- parse / schema validate 失败：聊天里显示 EditConflictMarkdownFormatter 同款友好错误卡 + 提示 AI 修正

**Promotion to workbench**（聊天块工具栏按钮"打开到工作台"）：
- 调 `DashboardController.promote(json)`
- 后端：schema validate → 创建 file_artifact (kind=`dashboard`) → 创建 stage_tab (type=`dashboard`, payload={fileArtifactId, displayTitle: dashboard.title}) → 返回 dashboardId
- 前端：把这个新 Tab 设为 active

**与 ui_patch 的衔接**：fence 创建 dashboard 后，**后续修改一律走 ui_patch**，不再用 fence 复写 — 防止"AI 一句话改字段就重发整张 JSON 200 KB"。AI 在系统 prompt 中被强制：promote 后的 Tab 要用 `datatalk_ui_read(dashboard, <id>)` + `datatalk_ui_patch(...)`，不再 fence。

### 8.2 通路 B：`ui_patch` JSON Patch（增量改）

```jsonc
{
  "tool": "datatalk_ui_patch",
  "params": {
    "type": "dashboard",
    "objectId": "<dashboardId>",
    "baseVersion": 7,
    "patches": [
      { "op": "replace", "path": "/widgets[id=chart_w1]/query/sql",
        "value": "SELECT date_trunc('week', created_at) AS bucket, SUM(amount) FROM orders WHERE created_at BETWEEN :startDate AND :endDate GROUP BY 1" },
      { "op": "replace", "path": "/widgets[id=chart_w1]/query/paramRefs",
        "value": { "startDate": "global:dateRange.start", "endDate": "global:dateRange.end" } }
    ]
  }
}
```

**允许的 JSON Patch op**：`add` / `remove` / `replace`。**禁止** `test` / `copy` / `move`（与既有 `UiPatchAction` 协议对齐 — `move` 可由 remove + add 替代）。

**path 寻址约定**（与既有 ER adapter / `pathResolver.ts` 的 `[matchKey=value]` 扩展对齐）：
- widget 用 `/widgets[id=<widgetId>]/...` matchKey 语法而非 `/widgets/<index>/...` — id 索引语义稳定，AI 不需要数 index
- 同理 `/parameters[id=<paramId>]/...`
- DashboardAdapter 在前端把 `[id=<widgetId>]` / `[id=<paramId>]` 反查 array index 做实际 RFC 6902 操作；该寻址语义已在 `client/src/services/ui-router/pathResolver.ts` 落地
- **add 操作例外**：path 仅允许 RFC 6902 标准的 `/widgets/-`（追加）与 `/parameters/-`（追加）；不允许 `/widgets/<index>` 数字寻址，也不允许 `/widgets[id=<id>]`（id 此时尚不存在）

**add 操作 widgetId / paramId 自携**：
```jsonc
// AI 追加一个 KPI widget
{
  "op": "add",
  "path": "/widgets/-",
  "value": {
    "id": "kpi_w7",                       // AI 自带 id；validator 校验 (a) dashboard 内唯一 (b) 格式 <type>_w_<token>
    "type": "kpi",
    "position": { "x": 0, "y": 12, "w": 3, "h": 3 },
    "query": {
      "sql": "SELECT SUM(amount) AS gmv FROM orders WHERE created_at >= :startDate",
      "paramRefs": { "startDate": "global:dateRange.start" }
    },
    "options": { "label": "总 GMV", "valueColumn": "gmv", "format": "currency" }
  }
}
```
- AI 不携 `id` → validator reject（避免后端隐式生成造成"AI 不知道刚加的 widget id 是什么"的回路断裂）
- id 撞已有 widget → reject
- id 格式必须匹配 `<type>_w_<alphanum>{4,16}`（widget）或 `(global|local):<...>`（parameter），便于 grep / 调试

**baseVersion 协议**：
- patch 必带 `baseVersion`
- 服务端校验：`dashboard.version == baseVersion` → 应用 patch + version++
- 不匹配 → 返 409 + EditConflictMarkdownFormatter

**返回值**：
```jsonc
{
  "ok": true,
  "newVersion": 8,
  "snapshotId": "rev_xxx"   // 自动快照 id（P5 才有；P1-P4 返回 null）
}
```

**校验链**（patch 应用后、落盘前）：
1. JSON Schema 校验（widget 必填字段 / 类型 / enum）
2. 跨 widget 校验：filter.paramId 必须存在；widget.query.paramRefs 中的 paramId 必须存在；optionsQuery 不能引用绑定的 paramId 自身
3. 布局校验：grid 模式下任意两 widget 几何不重叠
4. 失败 → reject patch，返 409 + 错误指向具体 path

### 8.3 `ui_exec` 动词

| 动词 | 参数 | 语义 |
|---|---|---|
| `dashboard.create` | `{ title?, defaultConnectionId? }` | 创建空 dashboard + Tab；返 dashboardId |
| `dashboard.archive` | `{ dashboardId }` | 走 file_artifact archive 通路 |
| `dashboard.export` | `{ dashboardId, format: 'png' \| 'pdf' }` | P6 才上 |
| `dashboard.snapshot` | `{ dashboardId, name }` | 手动命名版本（P5） |
| `dashboard.rollback` | `{ dashboardId, snapshotId }` | 回滚到某版本（P5） |
| `dashboard.set_default_connection` | `{ dashboardId, connectionId }` | 改默认连接 |

不在 ui_exec 列表里的修改一律走 ui_patch（标题、widget 增删改、参数定义改、layout 改…）。

### 8.4 `ui_read` 输出

```jsonc
{
  "type": "dashboard",
  "objectId": "<dashboardId>",
  "version": 8,
  "title": "销售看板",
  "defaultConnectionId": "<id>",
  "parameters": [...],
  "widgets": [...],
  "layout": {...},
  "summary": {
    "widgetCount": 7,
    "parameterCount": 3,
    "lastEditedBy": "ai",
    "lastEditedAt": "2026-05-08T...",
    "currentParameterValues": {
      "global:dateRange": ["2026-04-01", "2026-04-30"]
    }
  }
}
```

`ui_read` 默认返完整 JSON + summary。dashboard ≥ 64 KB → summary-only 模式，AI 必须显式 `range` 参数读特定 path（沿用 large-schema 边界保护）。

### 8.5 AGENTS.md 注入

新增 "Dashboards" 节：
- 介绍 ` ```dashboard ` 围栏 + 何时用（首次生成）
- 介绍 ui_patch 何时用（后续增量改）
- 路径寻址要用 widgetId / paramId 而非数组下标
- 参数 SQL 占位符规范：`:name` 只能在 value 位置，不能在 identifier
- "改字段单元价值"清单（增删 widget / 改 SQL / 改 layout / 调参数 / 改 widget options）— AI 推断 patch 操作的快速参考表
- 与 chart fence 关系：单图 fence，多图组合或要复用就 dashboard

`STAGE_TAB_DIGEST` 摘要扩展：dashboard Tab 行展示 `widgetCount × parameterCount × defaultConnection × lastEditedBy`，让 AI 跨 session 定位 dashboard 不必逐个 ui_read。

### 8.6 chart fence "添加到 Dashboard" 路径

聊天里 chart fence 的工具栏按钮新增两个动作：

- **"新建 Dashboard 含此图"**：前端调 `dashboard.create` ui_exec → 拿到 dashboardId → 立即调 ui_patch 加一个 chart widget（widget.options.echartsOption 直接复制 fence JSON）→ 切到新 Tab
- **"添加到当前 Dashboard"**：仅当 active Stage Tab 是 dashboard 类型时启用；前端 ui_patch 加 chart widget 到当前 dashboard，位置自动 `y = max(y) + 1`

两条都在前端跑，不需新 backend action。

---

## 9. 版本与并发

### 9.1 乐观并发

`dashboard.version: number` 单调递增。ui_patch 必带 `baseVersion`；服务端原子比较 + 应用 + 自增。不匹配返 409 + EditConflictMarkdownFormatter。这一层已在 cross-session-tabs / shared-stage-workbench 落地，本 spec 直接复用。

### 9.2 自动快照表

```sql
CREATE TABLE dashboard_revisions (
  revision_id        TEXT PRIMARY KEY,
  dashboard_id       TEXT NOT NULL REFERENCES file_artifact(id) ON DELETE CASCADE,
  version            INTEGER NOT NULL,           -- 与当时 dashboard.version 一致
  actor_kind         TEXT NOT NULL,              -- 'user' | 'ai' | 'system'
  actor_id           TEXT,                       -- userId 或 sessionId（来源）
  created_at         INTEGER NOT NULL,
  change_summary     TEXT NOT NULL,              -- JSON：[{op,path,humanLabel}]
  payload_snapshot   TEXT NOT NULL,              -- 完整 dashboard JSON 副本（≤ 256 KB）
  name               TEXT,                       -- NULL = 自动；非 NULL = 手动命名版本
  UNIQUE (dashboard_id, version)
);
CREATE INDEX idx_dashboard_revisions_lookup ON dashboard_revisions(dashboard_id, created_at DESC);
```

**migration**：V18（V17 是 `duckdb_readonly`，V14 是 `file_artifact`，V13 是 stage_tabs_workspace_only；P5 落地时按当时最新 V 号顺延）。

**触发**：每次 ui_patch / fence promotion 成功后，在同事务里 INSERT。

**change_summary 生成**：把 JSON Patch ops 翻译为人话（"修改 widget '上周销售趋势' 的 SQL"）。规则在 `DashboardChangeSummaryRenderer`。

**执行位置**：`DashboardChangeSummaryRenderer` 在**后端 Java** 跑，与 patch 应用同事务；前端只读 revisions 表里的 pre-rendered 文本，不重新生成。理由：(a) 后端有 authoritative patch + result，单源生成避免前后端不一致；(b) 未来 export / audit 直接复用同一份摘要；(c) i18n 由后端注入 — 与既有 EditConflictMarkdownFormatter / DashboardSchemaValidator 错误信息策略一致。

**保留策略**：每个 dashboard 保留最近 50 条自动快照 + 全部命名版本。HousekeepingScheduler 每天扫一次清理超量自动快照；命名版本永远保留。

**全量存而非 diff 链**：256 KB 上限可控，diff 链回滚 / 命名版本读取需 reduce N 个 patch，复杂度爆涨。明确选**全量**。

**存储上限估算**：256 KB × 50 自动快照 = 12.8 MB / dashboard 上限。10 个 dashboard ≈ 128 MB，50 个 ≈ 640 MB。SQLite 单机本地存储一般无问题，但 Settings Maintenance 应在 P5 落地时暴露 dashboard revisions 占用空间项，让用户能识别异常增长。

### 9.3 手动命名版本

`ui_exec dashboard.snapshot { dashboardId, name }`：把当前 version 对应的 revision 的 `name` 字段写为传入值。`name` 不允许重复（per dashboard），validator 强制；命名版本不可改名，只能新建。

### 9.4 回滚

`ui_exec dashboard.rollback { dashboardId, snapshotId }`：
1. 加载 snapshot.payload_snapshot
2. 校验 schema（snapshot 里的 schema 也得过 validator — 防止旧版本不再合法）
3. 写回 file_artifact，version = 当前 version + 1
4. 新插入一条 revision，change_summary = `[{ humanLabel: "回滚到 v<X>（<原 name 或时间>）" }]`
5. **不删旧 revision** — 历史完整保留

回滚不覆盖 ParameterStore 当前值（不持久化）；如某 paramId 在回滚后被删，前端将该 filter widget 当前值归零。

### 9.5 UI 表面

dashboard Tab 工具栏新增"历史版本"抽屉按钮。抽屉内：
- 命名版本钉在顶部（星标），按 created_at 降序
- 自动快照按 created_at 降序，actor 图标区分 user / AI
- 每条显示 `version × time × actor × changeSummary 一行`
- 点击 → 右侧预览（只读 dashboard 渲染）+ 工具按钮 "回滚到此版本" / "命名为版本"

抽屉宽度遵循 DESIGN.md `bg.panel` + `border.subtle` + `motion.normal` 滑入。

---

## 10. 持久化与 file_artifact 集成

### 10.1 文件归属

- `file_artifact.kind = 'dashboard'`
- `file_artifact.connection_id = <绑定 defaultConnectionId>`，`session_id = NULL`（workspace scope）
- 物理路径：`~/.data-talk/dashboards/<dashboardId>.dashboard.json`（**不**按 connection 目录分仓 — Hybrid connection 下 widget 可 override 连接，强绑 connection 目录会与 override 矛盾；改 defaultConnectionId 时不触发文件移动）
- `file_artifact.connection_id` 字段仅用于 Files Library scoping 与 connection 删除两阶段；不承担文件物理位置语义
- 体积上限：256 KB

### 10.2 stage_tab_payload 仅存指针

```jsonc
{
  "fileArtifactId": "dash_xxx",
  "displayTitle": "销售看板"
}
```

载入流：Tab 加载 → 从 file_artifact 读 dashboard JSON → 注入 `useDashboardTabsStore`。修改流：用户 / AI 改 → ParameterStore 不动（运行时态），dashboard JSON 改后 → 写回 file_artifact + bump version。

### 10.3 FTS extractContent

`tab-type-registry.dashboard.extractContent` 拼出可被 `ui_find` 命中的内容：
```
<title>
<description markdown stripped>
<widget1.title> <widget1.options.label> <widget1.query.sql>
<widget2.title> ... 
<param1.name> <param1.default>
...
```

**截断上限**：extractContent 输出 ≤ 4 KB；超出时按"高优先字段优先收集 + SQL/markdown 正文按 widget 顺序截尾"两阶段策略：
1. 先全收 `<title>` + 所有 `widget.title` / `widget.options.label` / `section.title` + 所有 `param.name`
2. 余量分给 `description` + `widget.query.sql` + `markdown.text` 按 widget 顺序追加，到 4 KB 截断
保证 dashboard 标题与 widget / parameter 标题永远在 FTS 索引内。

### 10.4 Files Library

新增 dashboard kind 视图：
- 显示 dashboards 列表，按 `updatedAt` 降序
- 项右键 → 打开 / 归档 / 导出（"复制为新 dashboard" 是 P6 之后的 fork 候选，v1 不出）
- 双击 → 如有同 dashboardId 的 Stage Tab 已开则聚焦，否则打开新 Tab

### 10.5 Connection 删除（两阶段，复用 file_artifact Part 5a）

- 第一阶段（409 阻断）：检测到 connection 下还有 active dashboards → 终局确认 modal："此 connection 下有 N 个 dashboards，删除后这些 dashboards 将归档为 connection_id=NULL，可后续在 Maintenance Drawer 重新绑定。"
- 第二阶段（force=true）：执行；`dashboards` 行 connection_id 置 NULL（文件路径不变，因不在 connection 目录下），Files Library 下归到"未绑定 connection"分组；已开的 dashboard Tab 进入"原 default connection 已断"空态，用户可重绑或归档；widget-override 到其他 connection 的 widget 不受影响仍可渲染

与 file_artifact Part 5a Q2 决策完全对齐，**不重新发明删除语义**。

---

## 11. 错误处理与安全

### 11.1 错误分类与码

| 场景 | HTTP 状态 | 前端表现 | AI 回路 |
|---|---|---|---|
| baseVersion 不匹配 | 409 | EditConflictMarkdownFormatter 卡片 | AI 收 markdown 错误，自动 ui_read 重拉再 patch |
| Schema validation 失败 | 422 | 红色 banner 列出错位置（path） | AI 收结构化错误，按 path 修补 |
| Connection 不存在 | 404 | widget 内空态 + "重新绑定 connection" | AI 调 `dashboard.set_default_connection` 修 |
| SQL 执行失败 | 200（widget 内态） | widget 内 ChartError 风格红卡 + SQL + 错信 | AI 修 SQL via ui_patch |
| 权限不足 | 403 | toast | AI 不应改写 — 上报用户 |
| Payload 超 256 KB | 413 | toast + 阻断保存 | AI 必须拆 widget |
| 参数循环 | 422 | 422 内含 paramId 链路 | AI 解环 |

### 11.2 widget 间隔离

每个 widget 的 query 是独立 backend 调用（虚拟线程并发）。一个 widget query 失败 / 超时（默认 30s）只让该 widget 进入错误态，其他 widget 正常渲染。dashboard 顶部出 amber banner "N of M widgets 加载失败"，不阻断使用。

`widget.query.timeoutMs` 字段在 v1 不暴露给用户 / AI，固定 30s；如未来大屏数据仓重查询需要，再加该字段（默认 30s）。

### 11.3 patch 原子性

一个 `ui_patch` 调用里多个 ops **要么全应用要么全不应用**。任何一 op 失败（schema / 跨 widget / 布局 validator）→ 整个 patch reject，dashboard 不变。这是 AI 友好的关键 — 不会出现"半套 patch"造成的中间态。

### 11.4 SQL 注入防线

§7.4 已详述。补强：
- ParameterizedSqlExecutor 走 JDBC PreparedStatement，**不接受字符串拼接**
- `:name` 占位符只允许在 value 位置；AST 层校验，identifier / table / column / order by 位置出现 `:name` 一律 reject
- 多值参数（string_list / multi_select）展开为 `(?, ?, ?)`
- 参数 type 与 ParameterDef.type 不匹配 → 不下发 SQL
- 沿用 SqlStatementGuard 的 L1/L2/L3 风险判级 — dashboard widget query 限 L1（SELECT/WITH），其他风险等级 SQL **不允许**作为 widget query

### 11.5 XSS / 不安全资源

- markdown widget：复用 chat 的 markdown 渲染（含 sanitizer），禁 raw HTML，仅允许标准 markdown + ` ```chart ` / ` ```dashboard ` 围栏被显式排除（防递归）
- image widget：仅 `https://`；http / data: / file: / javascript: 一律 schema reject
- ECharts option 中的 `formatter` 字段：仅允许 **string**（如 `'{b}: {c}'` 模板）和 **object**（嵌套 axis label 等配置）；**function** 类型（含序列化为字符串的函数体如 `"function(p){...}"`、JS 表达式字符串）一律 schema reject — 走 echarts 默认 formatter 即可，复杂格式化由 valueColumn / KPI format 承接

### 11.6 大 payload 边界

- file_artifact ≤ 256 KB（落盘强制）
- `ui_read` 返回 ≥ 64 KB → summary-only，AI 必须显式 `range` 读特定 path
- widget query 行集 → 沿用现有 `maxRows + truncated` 元数据
- echartsOption 体积 → 按 chart fence 既定 256 KB 子上限校验（避免一个 widget 吃光 dashboard 配额）

### 11.7 参数环 / 跨 widget 完整性

`DashboardSchemaValidator` 在 patch 应用前跑：
- 所有 `paramRefs` / `filter.paramId` 引用必须解析到现存 ParameterDef
- filter.optionsQuery.paramRefs 不能含其绑定的 paramId 自身
- 删除 widget 时其 local 参数必须无外引用，否则报"先解绑"
- 任意 widget 几何不重叠（grid 模式）

---

## 12. 测试策略

### 12.1 前端 (vitest + jsdom)

- `DashboardSchemaValidator`：每种 schema error path 单元测试 — widget 必填字段缺失 / paramId 引用不存在 / image 非 https / ECharts formatter 是 function / image alt 缺
- `GridLayoutEngine`：拖拽冲突解析 / 默认位置 / 响应式折叠 / **任意两 widget 不重叠** invariant 模糊测试（属性测试 fast-check 100 次随机 layout）
- `ParameterStore` + 依赖图：构建 / 环检测 / 删 widget 级联 / `setValue` 触发依赖 widget 重查 + 旧 reqId 丢弃
- 8 个 widget renderer：每种 widget 给定 options + query 结果 → 输出 DOM 快照
- `ui_patch` 路径解析：widgetId / paramId 反查 array index 的 RFC 6902 wrapper
- ` ```dashboard ` 围栏流式状态机：streaming → preview → error 三态切换；JSON 闭合检测
- chart fence 新动作：`新建 Dashboard 含此图` 与 `添加到当前 Dashboard` 仅在 active Tab 是 dashboard 时启用

**注意**：layout / overflow / scroll 类 bug 必须靠 Tauri/E2E 真实验证（CLAUDE.md memory 约束），jsdom 跑过不等于布局正确。

### 12.2 后端 (JUnit 5 + AssertJ + WireMock)

- `DashboardSchemaValidator`：与前端共用同一份 JSON Schema，跨 widget 校验 / 布局 invariants 一致性测试
- `DashboardArtifactService`：promote / load / patch / rollback 全路径
- `ParameterizedSqlExecutor`：
  - `:name` 在 value 位置 → 正确 PreparedStatement 替换
  - `:name` 在 identifier / ORDER BY / SELECT projection 位置 → reject
  - 多值参数 `string_list = ['a','b','c']` → SQL 重写为 `IN (?,?,?)` + 三个参数
  - type 不匹配 → reject
  - 跨 dialect 测试矩阵（MySQL / PostgreSQL / H2 / SQLite / SQL Server / MariaDB / Oracle 只读）— 走 PreparedStatement 标准路径
- `BaseVersionConflictTest`：两个并发 patch，第二个收 409 + EditConflictMarkdownFormatter 包装
- `DashboardRevisionTable`：自动快照插入 / 手动命名 / 50 条裁剪 + 命名永留 / 回滚不删旧
- `RenderDashboardAction`：via FakeOpenCodeServer 的 ui_exec / ui_read / ui_patch 端到端

### 12.3 集成与 E2E (Playwright)

按 docs/bugs BUG Tracking Gate，所有偏差登记到 `docs/bugs/`：

- **创建路径**：用户 prompt → AI 输出 ` ```dashboard ` 围栏 → 流式骨架 → 闭合预览 → 工具栏"打开到工作台" → Stage Tab 出现 → widget 全部渲染（fixture seed 数据）
- **AI 增量改路径**：AI ui_patch 改 widget SQL → 新 version → revision 表追一条 → 前端 ParameterStore 触发依赖 widget 重查 → 表面更新
- **冲突路径**：开两个 session 同时 ui_patch 同 dashboard → 第二个收 409 + 友好错误卡
- **跨 session 路径**：session A 创建 dashboard → 切到 session B → ui_find 搜得到 → 打开同 Tab → 看到一致内容
- **回滚路径**：手动 snapshot v3 → 改若干次 → 回滚到 v3 → version 仍递增（不破坏历史）+ 内容回到 v3
- **chart fence 添加路径**：聊天有 ```chart fence → 点"添加到当前 Dashboard"（active Tab 是 dashboard 时启用） → widget 出现在 dashboard 末尾
- **预登 BUG 占位**：layout drag jank / 参数依赖循环误报 / FTS 索引滞后 — 真实跑测时按 BUG-Tracking-Gate 登记

### 12.4 数据源兼容门（CLAUDE.md gate）

本 spec 不新增 connection kind，**消费**现有 connection。`ParameterizedSqlExecutor` 走 JDBC PreparedStatement 标准路径，dialect 中性。Coverage gate checklist 多数 N/A：

| 维度 | 状态 |
|---|---|
| Connection kind / URL builder | **N/A** — 不新增 kind |
| SQL Splitter / Risk | **N/A** — 仅消费现有 SqlStatementGuard，dashboard widget 限 L1（SELECT/WITH） |
| Metadata 发现 | **N/A** — 不读 metadata |
| ER | **N/A** |
| Diagnostics | **N/A** |
| 前端连接表单 | **N/A** |
| MCP schema | dashboard widget 复用现有 `datatalk_execute_sql` 路径，无新增 MCP tool；ui_patch / ui_exec / ui_read 已是 generic protocol，dashboard 仅是新 type |
| AGENTS.md | **新增 Dashboards 节**（已在 §8.5 描述） |

显式确认：所有 dashboard widget 在 dashboard 路径上仍受 `SqlStatementGuard` 风险判级保护，L2/L3 SQL 不允许作为 widget query — widget 不是绕过 SQL 风险门的后门。

### 12.5 性能基线（定性指标）

Dashboard 是数据密集型 Tab，必须给出明确性能口径，避免上线后用户反馈"卡"才发现：

- **大量 widget 渲染**：20 widget 同时挂载 + 数据已就绪 → 首次完整 paint ≤ 1s（M1 笔记本 / Chromium）
- **参数级联延迟**：`setValue` 到第一个依赖 widget 进入 loading 态 ≤ 500ms
- **table widget 大行集**：1000 行客户端分页（pageSize 50），分页切换 ≤ 100ms，无可感知 jank
- **大 JSON parse + validate**：256 KB dashboard.json 加载 → schema validate 完成 ≤ 100ms（不含 widget query 执行）
- **revision 抽屉打开**：50 条 revisions 列表渲染 ≤ 200ms（懒加载 payload_snapshot — 仅在用户点"查看"时拉具体 snapshot，列表只用 metadata）
- **依赖图重建**：100 widget × 20 paramId 的 dashboard，每次 ui_patch 后重建依赖图 ≤ 50ms

性能不达标视为 P1 BUG，登 `docs/bugs/`。性能 E2E 在 P5 落地后专门跑一轮基线 benchmark，作为后续 phase 不退化的回归基线。

---

## 13. Phase 计划（A 路线，vertical slice）

每 phase 一份独立 child plan，登 `docs/exec-plans/index.md`。

| Phase | 范围 | 关键产物 |
|---|---|---|
| **P1** | file_artifact + dashboard Tab + GridLayoutEngine + LayoutEngine 抽象 + chart widget + markdown widget + ` ```dashboard ` 围栏 + 简化 ui_patch（widget add/remove/replace + layout） | 端到端可演示：聊天生成 → 打开到工作台 → 看到栅格 + 图 + markdown |
| **P2** | KPI + section title + divider + image widget；ui_patch 路径完整化（含层级 path）；widget × query 强类型对应表落码 | 全静态 widget 集 |
| **P3** | table widget + 全局参数 + ParameterizedSqlExecutor + AGENTS.md 注入参数协议 | "改 SQL 用 :date" 流跑通 |
| **P4** | filter widget + 局部参数 + 依赖图调度器 + 多控件 debounce 策略 + reqId 防错乱 | "改时间范围 → 多 widget 联动重查" 跑通 |
| **P5** | dashboard_revisions 表（V18 migration，按 P5 落地时最新 V 号顺延）+ 自动快照 + 手动命名版本 + 回滚 + 历史抽屉 UI | "回滚到昨天的版本"跑通 |
| **P6** | 导出 PNG / PDF + chart fence "新建 Dashboard 含此图" / "添加到当前 Dashboard" + Files Library Dashboard 视图 + connection 删除两阶段 | 体验闭环 |

### Phase 间执行约束

- 每个 phase 通过 plan + verify 后才登 Completed；上线前 `mvn clean verify` + `npx tsc --noEmit` + 关键 E2E 通过
- **P1 是基础设施前置**：file_artifact + dashboard Tab + GridLayoutEngine + chart/markdown 渲染回路必须先落，P2 / P3 / P4 都在 P1 完成之后开始
- P2（widget 集补齐）与 P3（table + 全局参数）可在 P1 后**并行**
- P4（filter + 局部参数 + 依赖图）依赖 P3（参数系统底座），不可并行
- P5（版本）与 P6（导出 + chart fence 添加）在 P4 之后才上；**P5 / P6 之间可并行**
- 任一 phase 发生范围扩张需新建独立子 spec 或更新本 spec，不在 plan 里隐性改产品边界
- 与 file_artifact Part 5b（HousekeepingScheduler）协同：P5 落地时确认 dashboard_revisions 不在 _trash 清理范围

---

## 14. Out of Scope（明确剔除）

- **自由画布 layout engine**：v2 加 `CanvasLayoutEngine`，本 spec 仅留接口
- **Auto-refresh / 流式数据**：监控大屏需要的"每 30 秒重查"；本 spec 全部 widget 是被动重查（用户改参数 / 手动刷新）
- **Kiosk / fullscreen / 大屏专用模式**：需要主题切换、隐藏 chrome、自适应 1920+ 屏宽
- **Theme override（监视墙暗主题）**：dashboard 仅消费 DESIGN.md 既有 light/dark 主题
- **Iframe widget**：安全面 / 跟马取得需独立评估
- **Dashboard URL 公开分享 / 嵌入第三方页面**：需身份与权限基建
- **跨 dashboard 嵌套引用**：dashboard 不能 widget 化进另一个 dashboard
- **多人实时协作（live cursor / presence）**：本 spec 仅做乐观并发，不做 CRDT
- **Dashboard 模板市场 / 共享中心**：分享语义未定义
- **AI 自动建议 widget 配色**：DESIGN.md 强制 emphasis token，AI 无定制空间
- **动态布局根据数据自动 reflow**：layout 由用户 / AI 显式决定，不智能调整
- **本地图片上传**：v1 仅 https URL；上传 phase 2
- **服务端表格分页**：v1 仅客户端分页
- **widget 级超时配置**：v1 固定 30s
- **dashboard 级 fork / 复制**：phase 2 的 `dashboard.fork` ui_exec 候选

---

## 15. Open Questions（待 codex 评审）

- Phase 划分粒度是否合理：P4 filter + 依赖图调度可能比预估大，是否拆为 P4a（filter widget + 全局参数依赖）+ P4b（局部参数 + reqId 防错乱）
- ` ```dashboard ` 围栏完成首生成后的"AI 不再用 fence 复写"约束需要在 prompt 工程层强制；如何检测 AI 违规需要补 telemetry
- KPI widget 是否应支持多数字（GMV 主 + 订单量副）— 当前是单数字，多数字靠并列 KPI widget；如产品反馈强烈再加 v2
- `ui_exec dashboard.fork` 是否在 P6 顺路加，还是单独 child spec
- Filter widget `text_input` 控件：全局正则 SQL 代入风险点是否值得保留？建议砍掉，仅留 select / date / number_range — 待 codex 决断
