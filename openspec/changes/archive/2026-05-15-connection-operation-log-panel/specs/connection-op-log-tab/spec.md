## ADDED Requirements

### Requirement: operation_log tab 类型注册

系统 SHALL 在 TabTypeRegistry 中注册 `operation_log` tab 类型，scope 为 `workspace`，persistent 为 `true`，payloadSource 为 `stage_tab`。

#### Scenario: tab 类型可用

- **GIVEN** TabTypeRegistry 已初始化
- **WHEN** 调用 `getTabTypeDescriptor('operation_log')`
- **THEN** 返回 descriptor，包含 `{ type: 'operation_log', scope: 'workspace', persistent: true, payloadSource: 'stage_tab' }`

### Requirement: operation_log tab payload 结构

StageTab 的 payload SHALL 包含 `kind: 'operation_log'`、`connectionId`、`connectionName`，可选 `filters` 对象（status/operation/table/dateRange）。

#### Scenario: 创建 tab 时的 payload

- **GIVEN** 用户打开 connection `conn-1`（名称 "prod-mysql"）的操作日志
- **WHEN** 创建 StageTab
- **THEN** payload SHALL 为 `{ kind: 'operation_log', connectionId: 'conn-1', connectionName: 'prod-mysql' }`

### Requirement: 同一 connection 只保留一个 operation_log tab

使用 `buildStageTabIdentity()` 机制，identity key 为 `operation_log::connectionId`。打开已存在的 tab 时 SHALL focus 而非新建。

#### Scenario: 重复打开同一 connection 的日志

- **GIVEN** connection `conn-1` 的 operation_log tab 已存在
- **WHEN** 用户再次触发打开 `conn-1` 的操作日志
- **THEN** SHALL focus 现有 tab，不创建新 tab

#### Scenario: 不同 connection 各自独立

- **GIVEN** connection `conn-1` 的 operation_log tab 已打开
- **WHEN** 用户打开 connection `conn-2` 的操作日志
- **THEN** SHALL 创建新 tab，两个 tab 共存

### Requirement: OperationLogTab SHALL 遵守 DESIGN.md surface token 体系

OperationLogTab 的所有表面 SHALL 使用 client/DESIGN.md 定义的 semantic token，禁止硬编码原始颜色值：

| 区域 | surface token | 说明 |
|------|-------------|------|
| Tab chrome（过滤栏、分页栏） | `bg.soft` | Stage tab 级 chrome |
| 表格工作区（表格 body） | `bg.canvas` | 工作内容表面 |
| 表格 header 行 | `bg.subtle` | 表头背景 |
| 展开详情面板 | `bg.panel` | 独立内容面板 |
| 批量操作栏 | `bg.elevated` + shadow | 浮层表面 |

#### Scenario: 表面 token 使用

- **GIVEN** OperationLogTab 渲染
- **WHEN** 检查所有 surface 颜色值
- **THEN** SHALL 全部使用 DESIGN.md semantic token（bg.soft / bg.canvas / bg.subtle / bg.panel / bg.elevated）
- **AND** SHALL NOT 包含任何硬编码的十六进制或 RGB 颜色值

### Requirement: OperationLogTab SHALL 遵守 DESIGN.md typography token 体系

OperationLogTab 的所有文字 SHALL 使用 compact 密度（因属于 table 类组件），并使用 DESIGN.md 定义的 typography token：

| 元素 | typography token | text token |
|------|-----------------|------------|
| 表格 header | `ui-xs` | `text.muted` |
| 表格单元格 | `ui-sm` | `text.base` |
| Affected Rows 列 | `mono-sm` | `text.base` |
| SQL 内容（详情行） | `mono-sm` | `text.base` |
| Badge 文字 | `ui-xs` | `text.inverse` 或 `text.strong`（取决于 badge 背景） |
| 元数据（session/expires） | `ui-xs` | `text.muted` |
| 分页信息 | `ui-xs` | `text.muted` |
| 批量操作栏文字 | `ui-sm` | `text.strong` |

#### Scenario: typography token 使用

- **GIVEN** OperationLogTab 渲染
- **WHEN** 检查所有文字的 font-family / font-size / line-height / font-weight
- **THEN** SHALL 全部符合 DESIGN.md typography token 定义
- **AND** 表格行高 SHALL 符合 compact 密度标准

### Requirement: OperationLogTab SHALL 遵守 DESIGN.md interaction token 体系

所有交互状态 SHALL 使用 DESIGN.md semantic interaction token：

| 交互 | token |
|------|-------|
| 行 hover | `interaction.hover` |
| 行 selected（checkbox 选中行） | `interaction.selected` |
| 可聚焦元素 focus | `interaction.focusRing` |
| 排序 header 指示器 | `accent.primary` |

#### Scenario: 行 hover 效果

- **GIVEN** 表格行渲染
- **WHEN** 鼠标 hover 到某行
- **THEN** 行背景 SHALL 使用 `interaction.hover` token

#### Scenario: 行 selected 效果

- **GIVEN** 用户通过 checkbox 选中某行
- **WHEN** 选中状态生效
- **THEN** 行背景 SHALL 使用 `interaction.selected` token

#### Scenario: focus ring

- **GIVEN** 键盘 Tab 导航到表格内的可聚焦元素（checkbox、Undo 按钮、展开按钮）
- **WHEN** 元素获得 focus-visible
- **THEN** SHALL 显示 `interaction.focusRing` 焦点环

### Requirement: 操作日志表格展示

OperationLogTab SHALL 使用 @tanstack/react-table 渲染操作日志列表，列包含：checkbox、时间、操作类型、表名、affected rows、状态 badge、操作按钮。

#### Scenario: 表格列定义

- **GIVEN** OperationLogTab 渲染
- **WHEN** 数据加载完成
- **THEN** 表格 SHALL 显示以下列：
  - Checkbox（行选择）
  - 时间（createdAt，格式化为 HH:mm:ss，`ui-sm` + `text.base`）
  - 操作类型（INSERT/UPDATE/DELETE，见 operation badge requirement）
  - 表名（tableName，`ui-sm` + `text.base`）
  - Affected Rows（affectedRows，`mono-sm` + `text.base`）
  - 状态（见 status badge requirement）
  - 操作（Undo 按钮，仅 active+undoable 记录显示）

### Requirement: Status badge SHALL 使用文字+颜色双通道

Status badge SHALL 同时包含文字标签和颜色，不可仅靠颜色传达状态。颜色 SHALL 使用 DESIGN.md status 语义 token：

| Status | 文字 | 颜色 token | 背景 token |
|--------|------|-----------|-----------|
| pending | "Pending" | `status.info` | `status.infoSurface` |
| active | "Active" | `status.success` | `status.successSurface` |
| undone | "Undone" | `text.muted` | `bg.subtle` |
| expired | "Expired" | `status.warning` | `status.warningSurface` |

Badge 文字 SHALL 使用 `ui-xs` typography。

#### Scenario: active 状态 badge

- **GIVEN** 一条 status=active 的记录
- **WHEN** 渲染 status badge
- **THEN** SHALL 显示文字 "Active" + 绿色背景（`status.successSurface`）+ 绿色文字（`status.success`）

#### Scenario: undone 状态 badge

- **GIVEN** 一条 status=undone 的记录
- **WHEN** 渲染 status badge
- **THEN** SHALL 显示文字 "Undone" + 灰色背景（`bg.subtle`）+ 灰色文字（`text.muted`）

#### Scenario: 色盲用户可识别

- **GIVEN** 用户为色盲
- **WHEN** 查看 status badge
- **THEN** SHALL 能通过文字标签区分所有状态（不依赖颜色差异）

### Requirement: Operation badge SHALL 使用文字+颜色双通道

Operation type badge SHALL 同时包含文字标签和颜色，使用 DESIGN.md status 语义 token：

| Operation | 文字 | 颜色 token | 背景 token |
|-----------|------|-----------|-----------|
| INSERT | "INSERT" | `status.info` | `status.infoSurface` |
| UPDATE | "UPDATE" | `status.warning` | `status.warningSurface` |
| DELETE | "DELETE" | `status.danger` | `status.dangerSurface` |

Badge 文字 SHALL 使用 `ui-xs` typography + `mono-sm` font-family。

#### Scenario: INSERT 操作 badge

- **GIVEN** 一条 operation=INSERT 的记录
- **WHEN** 渲染 operation badge
- **THEN** SHALL 显示文字 "INSERT" + sky 色背景（`status.infoSurface`）+ sky 色文字（`status.info`）

### Requirement: 表格排序

用户 SHALL 能点击列 header 对时间、表名、affected rows、状态列进行升序/降序排序。排序指示器（箭头图标）SHALL 使用 `accent.primary` 颜色。

#### Scenario: 按时间排序

- **GIVEN** 表格显示多条记录
- **WHEN** 用户点击"时间"列 header
- **THEN** 记录按 createdAt 升序排列
- **AND** header 中显示排序箭头，颜色为 `accent.primary`
- **AND** 再次点击切换为降序

### Requirement: 表格分页

表格 SHALL 使用服务端分页，每页 50 条。底部显示分页控件（上一页/下一页/页码）和总数。分页信息文字 SHALL 使用 `ui-xs` + `text.muted`。

#### Scenario: 分页控件

- **GIVEN** 查询返回 total=142
- **WHEN** 当前在第 1 页
- **THEN** 底部 SHALL 显示 "Page 2 of 3 · 142 operations"
- **AND** 上一页/下一页按钮
- **AND** 分页信息使用 `ui-xs` + `text.muted`

### Requirement: 过滤栏

OperationLogTab 顶部 SHALL 展示过滤栏，包含 status 下拉、operation 下拉、table 文本输入、date range picker、SQL 搜索输入。过滤变更 SHALL 触发 API 重新查询。过滤栏表面 SHALL 使用 `bg.soft`，底部边框 SHALL 使用 `border.default`。

#### Scenario: 组合过滤

- **GIVEN** 用户选择 status=active，operation=UPDATE，table 输入 "order"
- **WHEN** 过滤提交
- **THEN** API 请求 SHALL 包含 `status=active&operation=UPDATE&table=order`
- **AND** 表格刷新显示过滤结果

#### Scenario: 清空过滤

- **GIVEN** 过滤条件已设置
- **WHEN** 用户点击 "Clear" 按钮
- **THEN** 所有过滤条件重置，表格显示全部记录

#### Scenario: 过滤栏 surface

- **GIVEN** 过滤栏渲染
- **WHEN** 检查背景和边框
- **THEN** 背景 SHALL 为 `bg.soft`
- **AND** 底部边框 SHALL 为 `border.default`

### Requirement: 展开行详情

用户 SHALL 能点击行展开详情面板，展示 original SQL、inverse SQL、before_state（自适应展示）、session 信息、过期时间。展开动画 SHALL 使用 `motion.normal`（180ms）+ `easing.enter`。详情面板表面 SHALL 使用 `bg.panel`，顶部边框 SHALL 使用 `border.subtle`。

#### Scenario: 展开行

- **GIVEN** 表格显示一条 INSERT 记录
- **WHEN** 用户点击行展开按钮
- **THEN** 行下方展开详情面板（180ms enter 动画），包含：
  - original SQL（`mono-sm` code block）
  - inverse SQL（`mono-sm` code block）
  - before_state（自适应展示，见 before_state requirement）
  - Session title（`ui-xs` + `text.muted`）
  - 过期时间（`ui-xs` + `text.muted`）
- **AND** 详情面板背景为 `bg.panel`，顶部边框为 `border.subtle`

#### Scenario: 折叠行

- **GIVEN** 详情面板已展开
- **WHEN** 用户再次点击展开按钮
- **THEN** 面板折叠（180ms exit 动画，`easing.exit`）

### Requirement: before_state 自适应展示

before_state 展示 SHALL 根据数据特征自动选择展示方式：
- 行数 ≤ 5 且列数 ≤ 8：JSON 树形展开
- 行数 > 5 或列数 > 8：mini data-grid 表格
- UPDATE 操作：高亮变更字段的 diff 对比

所有展示方式的文字 SHALL 使用 `mono-sm` typography。

#### Scenario: 少量数据使用 JSON 树

- **GIVEN** before_state 解析为 2 行、4 列
- **WHEN** 展示 before_state
- **THEN** 使用 JSON 树形展开展示，文字使用 `mono-sm`

#### Scenario: 大量数据使用表格

- **GIVEN** before_state 解析为 50 行、12 列
- **WHEN** 展示 before_state
- **THEN** 使用 mini data-grid 表格展示，表格遵循 DESIGN.md Table 规则（`bg.subtle` header、`interaction.hover` row hover）

#### Scenario: UPDATE 操作显示 diff

- **GIVEN** UPDATE 操作的 before_state 包含变更前后数据
- **WHEN** 展示 before_state
- **THEN** 变更的字段 SHALL 高亮显示（旧值→新值）
- **AND** 旧值 SHALL 使用 `status.dangerSurface` 背景
- **AND** 新值 SHALL 使用 `status.successSurface` 背景
- **AND** 变更字段名文字 SHALL 包含删除线样式（确保色盲可识别）

### Requirement: 行选择与批量操作栏

用户 SHALL 能通过 checkbox 选择多行。选中 active+undoable 记录时，底部浮现批量操作栏，显示选中数量和 "Undo Selected" 按钮。批量操作栏 SHALL 使用 `bg.elevated` + shadow 表面，浮现动画 SHALL 使用 `motion.fast`（120ms）+ `easing.enter`。

#### Scenario: 选中可 undo 记录

- **GIVEN** 用户选中 3 条 active/undoable 记录
- **WHEN** 选中状态变更
- **THEN** 底部浮现操作栏（120ms enter 动画），显示 "3 selected"
- **AND** "Undo Selected" 按钮可用
- **AND** 操作栏背景为 `bg.elevated`，带 shadow 浮层效果
- **AND** 选中行背景使用 `interaction.selected` token

#### Scenario: 选中不可 undo 记录

- **GIVEN** 用户选中 2 条 active/undoable 和 1 条 expired 记录
- **WHEN** 点击 "Undo Selected"
- **THEN** 汇总确认对话框 SHALL 仅展示可 undo 的 2 条记录

### Requirement: 实时更新

OperationLogTab SHALL 订阅 connection 级 SSE 通道。收到 `undo_log.created` 或 `undo_log.status_changed` 事件后 SHALL invalidate TanStack Query 触发列表刷新。列表刷新 SHALL 不使用动画过渡（数据直接替换），避免频繁刷新时的视觉闪烁。

#### Scenario: 新操作实时出现

- **GIVEN** OperationLogTab 已打开，显示 10 条记录
- **WHEN** 其他 session 在同一 connection 上执行 INSERT
- **THEN** SSE 推送 `undo_log.created` 事件
- **AND** 列表自动刷新，显示 11 条记录（新记录出现在顶部）

#### Scenario: undo 后状态实时更新

- **GIVEN** OperationLogTab 显示一条 active 记录
- **WHEN** 其他 tab 对该记录执行 undo
- **THEN** SSE 推送 `undo_log.status_changed` 事件
- **AND** 该记录状态自动更新为 undone

### Requirement: Tab 打开入口 — Sidebar 右键菜单

Sidebar 的 connection 上下文菜单 SHALL 包含 "View Operation Log" 选项，点击后打开或 focus 对应 connection 的 operation_log tab。

#### Scenario: 从 sidebar 打开

- **GIVEN** 用户在 sidebar 右键点击 connection "prod-mysql"
- **WHEN** 选择 "View Operation Log"
- **THEN** 打开 stage，创建或 focus `operation_log` tab，connectionId 为该 connection

### Requirement: Tab 打开入口 — SQL 结果面板链接

SqlDmlSummaryPanel 的 undo 操作区域 SHALL 包含 "View all operations" 链接，点击后打开对应 connection 的 operation_log tab。链接 SHALL 使用 `accent.primary` 颜色，hover 时使用 `accent.primaryHover`。

#### Scenario: 从 DML 结果面板打开

- **GIVEN** SQL 执行结果显示 DML summary，关联 connection `conn-1`
- **WHEN** 用户点击 "View all operations" 链接
- **THEN** 打开 `conn-1` 的 operation_log tab

### Requirement: Tab 打开入口 — 工具栏新建

工具栏的 "New Tab" 选项 SHALL 包含 "Operation Log" 类型，用户选择后 SHALL 弹出 connection 选择器，选择后创建对应 operation_log tab。

#### Scenario: 从工具栏创建

- **GIVEN** 用户点击工具栏 "New Tab"
- **WHEN** 选择 "Operation Log"
- **THEN** 弹出 connection 选择器
- **AND** 选择 connection 后创建 operation_log tab
