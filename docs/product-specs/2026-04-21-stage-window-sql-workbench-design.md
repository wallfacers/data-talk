# Stage Window SQL Workbench 设计

**日期**：2026-04-21  
**范围**：读取 `client/public/prototypes/stage-window-data-tool.html` 的布局结构，重构当前 Stage Window 为多面板 SQL 工作台；所有通用控件必须使用 `shadcn/ui`；`query_editor` 升级为统一 SQL 工作页；移除 `bang_query` 独立模型，并同步更新 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

---

## 1. 背景

当前 Stage 已完成第一轮 workbench 化重构，具备：

- `StageWindow` 的窗口壳体
- 左侧 `StageSidebar`（工具行 + 资源树）
- 右侧 `StageTabBar` 与 `StageTabContent`
- 基础 `query_editor`

但它距离目标原型仍有三类明显缺口：

1. **布局完成度不足**：当前 `query_editor` 仍是“上工具栏 + 中间编辑器 + 底部结果”的基础垂直切分，缺少原型中的多面板工作台层级。
2. **SQL 体验被拆成两套**：`query_editor` 与 `bang_query` 并存，一个偏编辑页，一个偏只读结果页，导致查询入口、tab 类型、文档协议都重复。
3. **协议与文档将失配**：当前 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` 与 `docs/references/ui-objects-reference.md` 仍把 `bang_query` 作为可见 UIObject。如果前端删除 `bang_query` 而文档不改，AI 侧提示词会立即过期。

用户已经明确这次改造的硬要求：

- 布局可以参考原型
- 按钮、展示内容、颜色、交互实现必须遵守当前项目前端规范
- 除 SQL 编辑器和未来 ER 画布外，其他通用控件必须使用 `shadcn/ui`
- 如果工作量较长，可以拆成分批实施

---

## 2. 目标与非目标

### 2.1 本期目标

1. 将 Stage 升级为更接近原型的 **多面板 SQL 工作台**
2. 保留现有 `StageWindow -> StageSidebar -> StageTabBar -> StageTabContent` 的工作台骨架，不再另起第二套布局体系
3. 让 `query_editor` 成为唯一 SQL 工作页模型，承接：
   - 手动打开 SQL 编辑器
   - 资源树上下文打开 SQL 编辑器
   - `!select` / `!with` 直查入口
   - AI 预填 SQL 打开
4. 删除 `bang_query` 独立页面、独立 adapter、独立 tab 类型与对应文档说明
5. 同步更新协议文档与资源目录下的 `AGENTS.md`，避免实现与 AI 提示词分叉

### 2.2 非目标

1. 本期不实现新的后端 SQL 能力，继续复用现有 `POST /api/sql/execute`
2. 本期不实现真实 ER 设计器，仅保留后续可挂 `ReactFlow` 的工作台壳体
3. 本期不实现报表与 Dashboard 的真实功能，仅统一其占位布局
4. 本期不新增第二套全局 Stage store，继续复用 `useStageStore`
5. 本期不把 Stage 重新改回自定义控件系统，除 `CodeMirror` / `ReactFlow` 外不引入新的独立 UI 框架

---

## 3. 硬约束

### 3.1 `shadcn/ui` 是强约束

除以下例外外，所有通用控件必须使用现有项目的 `shadcn/ui` 组件：

- SQL 编辑器：允许使用 `CodeMirror`
- ER 画布：允许使用 `ReactFlow`

这意味着以下区域必须回归 `shadcn/ui + 项目 token`：

- 顶部窗口按钮
- tab strip
- toolbar
- badge / status pill
- context menu
- inspector 卡片
- empty state
- 结果区外壳
- 资源树中的操作按钮

原型只能借 **布局结构、区域密度、信息分层**，不能直接照搬其自定义按钮、输入框、色板或拟物 chrome。

### 3.2 视觉语言沿用项目现有 token

本次不建立“原型专用主题”。颜色、边框、阴影、圆角、前景/弱化文本，继续以当前项目 `background / card / border / muted / primary / accent` 体系为准。

允许做的仅是：

- 调整面板嵌套层级
- 提高信息密度
- 通过 `Card`、`Separator`、`Badge`、`ScrollArea` 重组视觉节奏

### 3.3 文档与实现必须同批收敛

本次不是“先改 UI，后面再补文档”。以下文件属于定义的一部分：

- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- `docs/references/ui-objects-reference.md`
- `docs/product-specs/index.md`

如果 `bang_query` 已删除，但这些文档仍把它当成有效对象，则任务不算完成。

---

## 4. 总体架构

Stage 继续保留 workbench 架构，但右侧内容区升级为更完整的工作台内容模型。

```text
StageWindow
  ├── WindowTitleBar
  └── WorkbenchBody
       ├── StageSidebar
       │    ├── ToolRow
       │    └── ResourceBrowser
       └── WorkspacePane
            ├── StageTabBar
            └── StageTabContent
                 ├── QueryEditorTab  -> 多面板 SQL 工作台
                 ├── ErCanvasTab     -> 同壳体占位 / 后续 ReactFlow
                 ├── ReportTab       -> 同壳体占位
                 └── DashboardTab    -> 同壳体占位
```

本次不再让 Stage 出现第二条旧 tab chrome。`SplitView` 中的旧 `StageTabStrip` 将从默认渲染链路退出，避免与新的 `StageTabBar` 重复。

---

## 5. 页面布局映射

## 5.1 WindowTitleBar

顶部窗口栏保留，但从“简单标题 + 控制按钮”升级为更完整的工作台头部：

- 左侧：当前工作对象标题 + 类型徽标
- 中部：当前 `connection / database / schema` 摘要
- 右侧：最大化 / 关闭按钮

实现方式：

- `Button` 负责窗口控制
- `Badge` 负责对象类型和状态
- `Separator` 用于轻量分组
- 必要时用 `Tooltip` 展示完整上下文

不复刻原型里的自定义圆角小按钮和配色，只借“标题信息更丰富”的结构。

## 5.2 StageSidebar

左侧栏延续“工具入口 + 资源树”的结构，不重做信息架构：

- 顶部工具区：继续提供 SQL / ER / Report / Dashboard
- 下方资源树：继续显示 connection / database / schema 与工具动作
- 收起态：只保留图标入口与展开按钮，不做迷你树

实现方式以 `Button`、`Badge`、`ScrollArea`、`Separator` 为主，强调工作台导航感，而不是原型那种高自定义面板。

## 5.3 WorkspacePane

右侧统一使用共享的 `StageTabBar` 和 `WorkbenchScaffold`：

- 顶部：浏览器式但克制的 `StageTabBar`
- 下方：按 tab 类型切换不同内容页

不再出现一个 tab 类型一套完全不同的外壳。即使 ER / Report / Dashboard 先是占位，也必须挂在同一层内容脚手架上。

## 5.4 QueryEditorTab 的多面板布局

`query_editor` 是本期最完整的内容页，直接吸收原型的多面板结构，但全部改用项目规范实现：

```text
QueryEditorWorkbench
  ├── QueryToolbar
  ├── QueryMainGrid
  │    ├── EditorCard
  │    │    ├── EditorHeader
  │    │    ├── CodeMirror
  │    │    └── EmbeddedResultRegion
  │    └── QueryInspectorCard
  └── OptionalEmpty / Error / Risk state
```

### 主区域

- 左侧主列：编辑器主工作区
- 右侧副列：inspector / metadata / helper 信息

### 编辑器卡片

- 顶部显示来源、上下文、运行按钮、风险提示
- 中间是 `CodeMirror`
- 底部嵌入结果区域，不另开第二个 SQL 结果页

### 结果区

- 默认展示单个结果表格
- 使用 `Card`、`Table`、`ScrollArea`、`Badge` 组织结构
- 显示行数、耗时、截断状态、错误态、风险态

### Inspector

第一批先承接真实可落地信息：

- 当前 connection / database / schema
- tab 来源（manual / resource / direct_sql / ai_generated）
- 最近执行摘要
- 风险或上下文提醒

若某些内容暂时没有真实数据，则允许用轻量占位块，但外壳必须已经到位。

## 5.5 空态与非 SQL 页

无活动 tab 时，Stage 不再直接裸露旧内容容器，而是显示统一空态工作台：

- 当前上下文摘要
- 快捷入口
- 最近 artifact / 推荐操作占位

ER / Report / Dashboard 首批不做真实功能，但要共用 `WorkbenchScaffold` 的同一视觉结构，避免出现“只有 SQL 页像工作台，其他页像临时占位”的断层。

---

## 6. SQL 工作页统一模型

`query_editor` 升级为唯一 SQL 工作页模型，取代 `bang_query`。

### 6.1 入口模式

`query_editor` payload 统一包含入口模式：

```ts
type QueryEditorEntryMode =
  | 'manual'
  | 'resource'
  | 'direct_sql'
  | 'ai_generated'
```

建议扩展后的 payload：

```ts
type QueryEditorPayload = {
  entryMode: QueryEditorEntryMode
  initialSql?: string
  autoRun?: boolean
  lastRun?: {
    columns: string[]
    rowCount: number
    executionMs: number
    truncated: boolean
  } | null
  initialResult?: {
    columns: string[]
    rows: unknown[][]
    rowCount: number
    executionMs: number
    truncated: boolean
  } | null
  contextNotice?: string | null
  source?: 'user' | 'ai'
  connectionId?: string
  connectionName?: string
  database?: string
  schema?: string
}
```

### 6.2 统一行为

- `manual`：顶部工具行打开的全局 SQL 编辑器
- `resource`：资源树某个 connection/database/schema 下打开的 SQL 编辑器
- `direct_sql`：`!select` / `!with` 直查入口替代 `bang_query`
- `ai_generated`：AI 填入 SQL 打开的编辑器

这四种模式共享同一个 React 组件和视觉结构，只在默认执行行为与头部标识上不同。

### 6.3 保留现有 scope 规则

为了控制第一批改动风险，tab scope 先保持现有双轨：

- 顶部工具行 `SQL 编辑器`：`workspace` 级，全局单实例去重
- 资源树打开的 SQL 编辑器：`session` 级，按 `connectionId + database + schema` 去重
- `direct_sql`：`session` 级，每次新开一个独立查询页，保留聊天来源与执行快照

这样可以消除 `bang_query`，但不必同时推翻当前 `StageStore` 的 scope 设计。

---

## 7. 交互与数据流

## 7.1 顶部工具行

- 点击 `SQL 编辑器`
  - 若已有全局 SQL 编辑器 tab，则直接聚焦
  - 否则创建新的 `query_editor` workspace tab

## 7.2 资源树工具动作

- 单击 connection / database / schema 节点只更新上下文与选中态
- 单击节点下的 `SQL 编辑器` 动作时：
  - 按 `connectionId + database + schema` 查重
  - 若存在则聚焦
  - 若不存在则创建新的 `query_editor` session tab

## 7.3 `!select` / `!with` 直查入口

当前 `prompt-composer.tsx` 的直查分支保留，但打开目标改为 `query_editor`：

1. 先持久化用户 `!select ...` 消息，保留聊天区可见性
2. 解析当前 `connectionId / database / schema`
3. 调用新 helper，例如 `openDirectSqlQueryEditorTab`
4. 新建 `entryMode: 'direct_sql'` 的 `query_editor`
5. 将 SQL 注入编辑器，并设置：
   - `autoRun: true`，或
   - 若已有首轮执行结果，则写入 `initialResult`

这样用户看到的结果是：

- 聊天区保留一条直查消息
- Stage 中打开的是完整 SQL 工作台
- 不再存在第二种只读 SQL 页

## 7.4 查询执行状态

`QueryEditorTab` 内部继续围绕现有 `use-sql-execute`，但布局上升级为统一工作台：

- `idle`
- `running`
- `success`
- `risk_blocked`
- `error`

### 约束

- 自动执行失败时，不清空 SQL，保留错误态供用户重试
- 风险拦截时，不切换页面，直接在工作台结果区显示告警块
- 已打开 tab 的上下文快照不因 sidebar 后续切换而漂移

---

## 8. 组件与代码层变更

### 8.1 `StageWindow`

- 保留窗口壳体与左右布局
- 升级 title bar 信息密度
- 统一右侧工作台内容区域
- 移除默认链路中的旧 `StageTabStrip`

### 8.2 `QueryEditorTab`

- 从基础编辑器页重构为多面板工作台
- 引入 `QueryToolbar`、`EmbeddedResultRegion`、`QueryInspectorCard`
- 用 `shadcn/ui` 外壳承接所有非编辑器区域

### 8.3 `BangQuery` 退场

需要删除或退出默认链路的内容包括：

- `BangQueryTab`
- `BangQueryAdapter`
- `open-bang-query-tab.ts`
- `stage-tab-content.tsx` 中的 `bang_query` 分支
- `WorkspaceAdapter` 中 `bang_query` 的打开与 scope 注册
- 所有围绕 `bang_query` 的文档说明与测试

### 8.4 新增 `QueryEditorAdapter`

既然 `bang_query` 要退场，而 `AGENTS.md` 又要同步更新，就必须补上真实的 `query_editor` UIObject adapter。

建议新增：

- `client/src/features/stage/adapters/QueryEditorAdapter.ts`

最小能力：

- `read(state)`：返回 SQL、入口模式、上下文、最近执行摘要、当前状态
- `read(schema/actions/full)`：提供协议自描述
- `exec(focus)` / `exec(close)`：工作区控制

本期不要求把整张结果表暴露给 AI；行数据继续留在 UI 侧，不进入 AI 上下文。

### 8.5 `WorkspaceAdapter`

同步改动：

- 删除 `bang_query` 相关 type 说明
- `workspace.open` 仍支持 `query_editor`
- 文档中对 `query_editor` 的说明与真实 scope / payload 规则保持一致

---

## 9. 协议与文档同步

本次必须同步更新以下定义源：

1. `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
   - 删除 `bang_query` 对象定义
   - 将“打开 / 读取 SQL 工作页”的说明改到 `query_editor`
   - 保证 AI 指引与 Stage 真实行为一致

2. `docs/references/ui-objects-reference.md`
   - 删除 `bang_query` 章节
   - 新增或替换为 `query_editor` 章节
   - 更新 `workspace.open` 的有效 `type` 列表与行为说明

3. `docs/product-specs/index.md`
   - 登记本设计文档

协议文档如果继续描述不存在的 `bang_query`，则视为未完成。

---

## 10. 错误处理

### 10.1 无上下文

若打开 SQL 页时没有可用连接：

- 不直接展示空白编辑器
- 显示明确 empty state
- 引导用户从 sidebar 选资源，或通过选择器补全上下文

### 10.2 自动执行失败

`direct_sql` 自动执行失败时：

- 保留原 SQL
- 结果区显示错误态
- 用户可直接修改后重试

### 10.3 风险阻断

高风险 SQL 继续使用现有风险判级逻辑，但显示方式改为工作台内的标准告警块，不再像“另一页结果”。

### 10.4 结果截断

结果区必须显式展示：

- 行数
- 耗时
- 是否截断

不允许只显示表格，不显示执行摘要。

---

## 11. 测试策略

### 11.1 前端单元测试

至少覆盖：

- `stage-window.test.tsx`
  - 新 workbench 壳体
  - empty state
  - 共享 tab bar
  - 旧 `StageTabStrip` 不再进入默认路径
- `query-editor-tab.test.tsx`
  - 多面板布局
  - `manual / resource / direct_sql / ai_generated` 四种入口模式
  - 自动执行
  - 风险阻断
  - 无上下文空态
- `prompt-composer.test.tsx`
  - `!select` / `!with` 仍持久化用户消息
  - 但打开的是 `query_editor`，而不是 `bang_query`
- `WorkspaceAdapter` / `QueryEditorAdapter` tests
  - `query_editor` 对象注册
  - `workspace.open` 行为
  - `bang_query` 移除后的协议一致性

### 11.2 回归测试

需要删除或改写所有引用以下假设的测试：

- `bang_query` 仍存在
- `openBangQueryTab` 仍是有效入口
- UI Object `bang_query` 仍可 `read/exec/patch`

### 11.3 验证命令

- `cd client && npx tsc --noEmit`
- 相关 vitest 测试集

---

## 12. 分批实施建议

### Batch 1：Stage 壳体与内容脚手架

- `StageWindow`
- `StageSidebar`
- `StageTabBar`
- empty state
- `WorkbenchScaffold`

### Batch 2：`query_editor` 多面板化

- toolbar
- editor shell
- embedded result region
- inspector
- 状态布局与空态

### Batch 3：`bang_query` 退场与入口替换

- `prompt-composer` 改走 `query_editor`
- 删除 `BangQueryTab` / `BangQueryAdapter` / helper / 分发分支
- 调整相关 store / adapter / tests

### Batch 4：协议与文档同步

- `server/.../resources/agents/AGENTS.md`
- `docs/references/ui-objects-reference.md`
- 索引与关联 spec / plan

分批实施的目的不是拖延收尾，而是让布局、统一模型、协议清理三件事分别可验证。

---

## 13. 设计结论

本次改造的核心不是“把 Stage 画得更像原型”，而是收敛出一个长期稳定的 SQL 工作台模型：

- Stage 保留已有 workbench 外壳
- `query_editor` 成为唯一 SQL 工作页
- 原型只借布局，不借控件系统
- `shadcn/ui` 继续作为通用 UI 的唯一实现基线
- `bang_query` 退出实现、协议和文档三层定义

只有这样，Stage 才能在后续继续承接 ER、报表、Dashboard，而不是维持多套并行但互相重叠的页面模型。
