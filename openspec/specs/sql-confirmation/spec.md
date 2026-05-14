# sql-confirmation Specification

## Purpose

定义 SQL 风险确认对话框（L2 受限变更 / L3 破坏性操作前的人机确认）的 UI 行为：单层对话框结构、风险等级视觉标记、SQL 与受影响对象信息展示、按钮布局与初始焦点、acked-risk 不匹配时的失效提示、与 `client/DESIGN.md` 设计契约的一致性。

本 spec 在归档时作为 capability `sql-confirmation` 的基线落入 `openspec/specs/sql-confirmation/`。

## Requirements

### Requirement: 对话框 SHALL 仅渲染单层卡片，不嵌套子卡片

SQL 风险确认对话框 SHALL 使用单一的 shadcn `AlertDialogContent` 作为视觉容器。内部内容区域 (`SqlConfirmationCard` 渲染结果) SHALL NOT 包含独立的圆角、外边框或大色块填充背景（即不构成"对话框内的另一张卡片"）。

#### Scenario: dialog 内只有一层圆角边框容器

- **GIVEN** SQL workbench 触发 L2 风险（如 `UPDATE orders SET ... WHERE id = 1`）
- **WHEN** 确认对话框打开
- **THEN** DOM 中只有 `AlertDialogContent` 一个元素具有 `rounded` + `border` + 阴影类
- **AND** 其子元素 `[data-testid="sql-risk-panel"]` SHALL NOT 同时具有 `rounded-md`、`border`、整片背景填充类

#### Scenario: L3 触发时仍只有一层卡片

- **GIVEN** SQL workbench 触发 L3 风险（如 `DROP TABLE temp_log`）
- **WHEN** 确认对话框打开
- **THEN** DOM 结构与 L2 一致，仅风险标识（彩色边带 + Badge）从 amber 切到 danger，不引入新的卡片层

### Requirement: 标题 SHALL 仅有一个，风险等级以 Badge 形式呈现

对话框 SHALL 仅渲染一个标题（`AlertDialogTitle = stage.queryEditor.confirmation.title`，中文"确认执行"/英文"Confirm execution"）。风险等级（L2 / L3）SHALL 以 Badge（pill 标签）形式紧邻标题渲染，使用对应的 i18n key (`sqlConfirmation.l2.title` / `sqlConfirmation.l3.title`)。SHALL NOT 再在内容区渲染第二个"受限变更 / 破坏性操作"作为独立标题行。

#### Scenario: L2 标题与 Badge 同行

- **GIVEN** 风险等级为 L2
- **WHEN** 对话框打开
- **THEN** `AlertDialogHeader` 内 SHALL 渲染两个并排元素：`AlertDialogTitle` 文本"确认执行"（zh）或"Confirm execution"（en），以及紧邻其后的 Badge，Badge 文本 SHALL 为"受限变更"（zh）或"Bounded mutation"（en）
- **AND** Badge 视觉 SHALL 使用 amber 语义 token (`accent.warn` / `accent.warnSurface`)，不使用 `destructive` variant

#### Scenario: L3 标题与 Badge 同行

- **GIVEN** 风险等级为 L3
- **WHEN** 对话框打开
- **THEN** `AlertDialogHeader` 内 Badge 文本 SHALL 为"破坏性操作"（zh）或"Destructive operation"（en）
- **AND** Badge 视觉 SHALL 使用 `destructive` variant（基于 `status.danger` token）

#### Scenario: 内容区无重复标题

- **GIVEN** 对话框打开（L2 或 L3）
- **WHEN** 检查 `[data-testid="sql-risk-panel"]` 内的所有文本节点
- **THEN** SHALL NOT 包含与 Badge 文案重复的独立标题行（同样的文本"受限变更" / "Bounded mutation" / "破坏性操作" / "Destructive operation"）

### Requirement: 风险等级 SHALL 通过 4px 顶部彩色边带提供视觉锚点

对话框内容区 (`SqlConfirmationCard` 的根节点) SHALL 在顶部渲染一条 4px 高的彩色细边带（`aria-hidden`），作为风险等级的辅助视觉锚点。色值：L2 使用 `accent.warn`（amber），L3 使用 `status.danger`（red）。边带 SHALL 与对话框宽度对齐（左右扩展到 `AlertDialogContent` 的内边界）。

#### Scenario: L2 顶部边带为 amber

- **GIVEN** L2 风险
- **WHEN** 对话框打开
- **THEN** `[data-testid="sql-risk-panel"]` 顶部 SHALL 存在一个 `aria-hidden` 元素，高度等于 `4px` (`h-1`)，背景色映射到 `var(--dt-accent-warn)`

#### Scenario: L3 顶部边带为 danger

- **GIVEN** L3 风险
- **WHEN** 对话框打开
- **THEN** 同上元素的背景色 SHALL 映射到 `var(--dt-status-danger)`

### Requirement: 按钮 SHALL 位于 AlertDialogFooter，符合项目对话框统一风格

取消按钮与执行按钮 SHALL 渲染在 `<AlertDialogFooter>` 内，而 SHALL NOT 渲染在 `SqlConfirmationCard` 的内容区。Footer SHALL 继承基础布局（`flex flex-col-reverse gap-2 sm:flex-row sm:justify-end`），使按钮在桌面态右对齐、有标准间距。**Footer SHALL NOT 渲染顶部分割线或灰色 muted 背景**（基础组件 `AlertDialogFooter` / `DialogFooter` 的默认 className 已在本变更中清理 `border-t` / `bg-muted/50` / 负 margin / `rounded-b-xl`），与对话框主内容视觉合一。

按钮 variant SHALL 遵循项目级"按程度配色"规则（影响所有确认弹框，不限于 SQL 确认）：

| 操作程度 | Button variant | 颜色 |
|---|---|---|
| 取消 / 辅助 | `outline` | 中性边框 |
| 普通确认 / 保存 | `default` | cobalt 实色 + 白字 |
| 受限变更（L2、有条件 DML、可恢复操作） | `warning` | amber 实色 + 白字 |
| 破坏性（L3、DROP / TRUNCATE / 删除 / 不可撤销） | `destructive` | red 实色 + 白字 |

SQL 风险确认的 Execute 按钮按上述规则：L2 使用 `warning`，L3 使用 `destructive`。取消按钮使用 `outline`。

#### Scenario: 按钮位于 dialog 底部 Footer 区域

- **GIVEN** 对话框打开
- **WHEN** 检查 DOM
- **THEN** "取消"按钮与"执行"按钮 SHALL 是 `AlertDialogFooter` 的直接或孙子元素
- **AND** SHALL NOT 是 `[data-testid="sql-risk-panel"]` 的子元素

#### Scenario: L3 执行按钮为 destructive variant（实色红 + 白字）

- **GIVEN** L3 风险
- **WHEN** 对话框打开
- **THEN** "执行"按钮 SHALL 携带 `variant="destructive"` 对应的 className（`bg-destructive` 实色红 + `text-white`，与项目所有破坏性确认按钮对齐）

#### Scenario: L2 执行按钮为 warning variant（实色黄 + 白字）

- **GIVEN** L2 风险
- **WHEN** 对话框打开
- **THEN** "执行"按钮 SHALL 携带 `variant="warning"` 对应的 className（`bg-[var(--dt-accent-warn)]` amber 实色 + `text-white`），视觉权重介于 `default`（普通确认）与 `destructive`（破坏性）之间，与"受限变更"程度匹配

### Requirement: 取消按钮 SHALL 在对话框打开时获得初始焦点

为了防止误触发执行，对话框打开时 SHALL 将键盘焦点放在"取消"按钮上。该焦点行为可通过 Radix `<AlertDialogCancel>` 的默认聚焦或手动 ref 实现。

#### Scenario: 默认焦点在 Cancel

- **GIVEN** 对话框关闭（L2 或 L3 均适用）
- **WHEN** 对话框打开
- **THEN** `document.activeElement` SHALL 是"取消"按钮（`role=button`，可见文本为 `sqlConfirmation.cancel`）

### Requirement: SQL 预览、受影响对象、L2/L3 描述、L3 不可撤销提示 SHALL 全部保留

对话框内容区 SHALL 渲染下列信息（无一可少）：

1. SQL 预览块：以 `<pre>` 等同语义 + 等宽字体 (`typography.mono-sm`) 展示 `sqlPreview` 全文，超长 SHALL 横向滚动 (`overflow-x-auto`)；背景与边框 SHALL 跟随风险等级：L2 使用 `accent.warnSurface` 背景 + amber 半透明边框，L3 使用 `status.dangerSurface` 背景 + danger 半透明边框。该染色与顶部彩色边带、Badge 颜色共同形成同色系视觉提示。
2. "受影响对象"标签 + 对象列表：标签使用 `sqlConfirmation.affectedObjects` 的 i18n 值；列表以等宽字体逐行展示 `affectedObjects`；当数组为空时占位符为 `—`。
3. L2/L3 描述段：L2 渲染 `sqlConfirmation.l2.body`，L3 渲染 `sqlConfirmation.l3.body`，文案中 `{objects}` 插入对象列表的 `join(', ')` 串。
4. **L3 仅当 `level === 'L3'` 且该操作不在 undo-log 可回滚范围内时，渲染 `sqlConfirmation.l3.irreversible`**，使用 `status.danger` 强调色 + `font-semibold`。对 DML 操作（INSERT/UPDATE/DELETE），SHALL 改为渲染 `sqlConfirmation.l3.undoable` 提示（"此操作可在 3 天内通过 Undo 回滚"/"This action can be undone within 3 days"），使用 `accent.info` 色调。

#### Scenario: L2 完整渲染主体内容

- **GIVEN** `risk = { level: 'L2', reason: 'update_with_where', affectedObjects: ['orders'] }`、`sqlPreview = "UPDATE orders SET status = 'paid' WHERE id = 1"`
- **WHEN** 对话框打开
- **THEN** 内容区 SHALL 同时包含：SQL 文本节点（含 `UPDATE orders SET status`）、"受影响对象"标签、列表项 `orders`、描述段"将修改 orders 中的数据。"
- **AND** SHALL NOT 包含 `sqlConfirmation.l3.irreversible` 文案

#### Scenario: L3 DML 操作显示可回滚提示

- **GIVEN** `risk = { level: 'L3', reason: 'delete_without_where', affectedObjects: ['temp_log'] }`、`sqlPreview = "DELETE FROM temp_log WHERE created_at < '2025-01-01'"`、目标表有主键
- **WHEN** 对话框打开
- **THEN** 内容区 SHALL 包含 SQL 文本、"受影响对象"标签、描述段
- **AND** SHALL NOT 渲染 `sqlConfirmation.l3.irreversible`（"此操作无法撤销。"）
- **AND** SHALL 渲染 `sqlConfirmation.l3.undoable`（"此操作可在 3 天内通过 Undo 回滚。"）提示，使用 `accent.info` 色调

#### Scenario: L3 DDL 操作仍显示不可撤销提示

- **GIVEN** `risk = { level: 'L3', reason: 'drop_table', affectedObjects: ['temp_log'] }`、`sqlPreview = "DROP TABLE temp_log"`（DDL 操作不在 undo 范围）
- **WHEN** 对话框打开
- **THEN** 内容区 SHALL 渲染 `sqlConfirmation.l3.irreversible`（"此操作无法撤销。"），行为与原 spec 一致

#### Scenario: 受影响对象数组为空时占位

- **GIVEN** `risk.affectedObjects = []`
- **WHEN** 对话框打开
- **THEN** 描述段中 `{objects}` 位置渲染 `—`（em-dash），列表区域 SHALL NOT 渲染任何 `<li>`

### Requirement: 失效提示（confirmationInvalid）SHALL 渲染在内容区与 Footer 之间

当后端返回 `confirmationInvalid`（acked risk 与 currentRisk 不匹配）时，提示信息 SHALL 渲染在 `SqlConfirmationCard` 内容区之后、`AlertDialogFooter` 之前，使用 `data-testid="sql-confirmation-invalid-message"` 与 `status.danger` 色，文案来自 `confirmationInvalid.message`。

#### Scenario: 失效提示位置与样式

- **GIVEN** `tabState.confirmation` 存在且 `tabState.confirmationInvalid` 非空
- **WHEN** 对话框打开
- **THEN** DOM 中 SHALL 存在 `[data-testid="sql-confirmation-invalid-message"]` 元素
- **AND** 该元素 SHALL 位于 `[data-testid="sql-risk-panel"]` 之后、`AlertDialogFooter` 之前
- **AND** 文本内容 SHALL 等于 `tabState.confirmationInvalid.message`
- **AND** 文字色 SHALL 映射到 `var(--dt-status-danger)`

### Requirement: pending 状态 SHALL 同时禁用两个按钮

当 `tabState.executeStatus === 'confirming'`（已点击执行、等待后端确认结果中），取消与执行按钮 SHALL 均 `disabled`；执行按钮文案 SHALL 切换为 `sqlConfirmation.executing`（"执行中…" / "Executing…"）。

#### Scenario: pending 时两个按钮都不可点

- **GIVEN** 对话框打开、用户已点击执行
- **WHEN** `executeStatus` 变为 `'confirming'`
- **THEN** "取消"按钮 SHALL 是 `disabled`
- **AND** "执行"按钮 SHALL 是 `disabled`
- **AND** "执行"按钮可见文本 SHALL 是"执行中…"（zh）或"Executing…"（en）

### Requirement: 关键 testid SHALL 在重构后保持不变

为了不破坏现有 E2E（`client/tests/e2e/`），重构后 DOM SHALL 继续暴露下列 testid：

- `sql-confirmation-dialog`：标识 `AlertDialogContent` 根容器
- `sql-risk-panel`：标识 `SqlConfirmationCard` 内容区根节点
- `sql-confirmation-invalid-message`：标识失效提示段落

#### Scenario: 所有原有 testid 仍存在

- **GIVEN** 重构完成
- **WHEN** 对话框打开
- **THEN** DOM 中 SHALL 同时存在上述三个 testid 元素
- **AND** 它们指向语义等价的节点（dialog 外壳、内容区、失效提示）

### Requirement: 视觉与文本 token SHALL 全部来自 client/DESIGN.md

所有颜色 / 字号 / 字体族 / 间距 / 圆角 SHALL 通过 DataTalk 设计 token（`--dt-*` CSS 变量或对应 Tailwind class）使用，SHALL NOT 直接硬编码十六进制色或原始颜色值（`#fef3c7` 之类）。

#### Scenario: 所有颜色经过 token

- **GIVEN** 重构完成
- **WHEN** 检查 `sql-confirmation-card.tsx` 与对话框相关 JSX
- **THEN** 所有 `bg-`、`text-`、`border-` 类 SHALL 引用 `var(--dt-*)` 或 Tailwind 别名 (`bg-bg-canvas`、`text-text-strong` 等)
- **AND** SHALL NOT 直接出现 `#` 开头的字面色值或非 token 颜色名（`bg-yellow-50`、`text-red-700` 之类）
