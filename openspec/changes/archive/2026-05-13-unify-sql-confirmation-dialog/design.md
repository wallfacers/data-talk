## Context

SQL 风险确认对话框是 SQL Workbench 在执行 L2（受限变更，如 `UPDATE ... WHERE`、`DELETE ... WHERE`）和 L3（破坏性操作，如 `DROP TABLE`、`TRUNCATE`）SQL 前必须经过的强制人机确认环节。当前实现由两部分组成：

- **外壳**：`client/src/features/stage/components/sql-workbench-tab.tsx` 第 790–813 行，使用 shadcn 的 `<AlertDialog open>` + `<AlertDialogContent data-testid="sql-confirmation-dialog">` + `<AlertDialogHeader><AlertDialogTitle>{t('stage.queryEditor.confirmation.title')}</AlertDialogTitle></AlertDialogHeader>`，其中 i18n key 显示"确认执行 / Confirm execution"。
- **内卡**：`client/src/features/sql-confirmation/sql-confirmation-card.tsx`（共 107 行），在 `AlertDialogContent` 内独立渲染一个 `<div className="rounded-md border p-4 ...">`，自带一个标题行（amber/danger 颜色 + 圆点 + 文字"受限变更 / 破坏性操作"），SQL 预览 `<pre>`，受影响对象列表，描述段，L3 不可撤销警告，最后是一个 `flex justify-end gap-2` 的按钮组。

参考用户截图（`D:\b097f7829e5848dabe1c9e920b749912.png`）：

```
┌─ AlertDialogContent ──────────────────┐
│  确认执行                              │  ← AlertDialogTitle
│                                        │
│  ┌─ inner amber card ───────────────┐  │
│  │ ● 受限变更                       │  │  ← 内部第二个标题
│  │ ┌──────────────────────────────┐ │  │
│  │ │ DELETE FROM t_quality_rule … │ │  │  ← SQL 预览
│  │ └──────────────────────────────┘ │  │
│  │ 受影响对象                       │  │
│  │ t_quality_rule                   │  │
│  │ 将修改 t_quality_rule 中的数据。 │  │
│  │                  [取消] [执行]   │  │  ← 按钮在内卡里
│  └──────────────────────────────────┘  │
└────────────────────────────────────────┘
```

问题如截图所示：两个标题、两层卡片、按钮在内卡右下角（距离 dialog 右边缘极近、与项目其它 AlertDialog 不同）。

参考项目内其它正确的 AlertDialog 实现：例如 `client/src/features/connection/` 等模块的对话框，按钮位于 `<AlertDialogFooter>`，对齐遵循 shadcn 默认（`flex flex-col-reverse gap-2 sm:flex-row sm:justify-end`），有充足右边距。

## Design Inputs (client/DESIGN.md)

| Constraint | Source | Current State | Target State |
|---|---|---|---|
| `accent.warn` / `accent.warnSurface` 用于警告 | semantic.light/dark.accent | 已在内卡背景使用整片 amber 填充 | 改为 4px 顶部彩色边带 + Badge 标签 |
| `status.danger` / `status.dangerSurface` 用于破坏性 | semantic.\*.status | 已在内卡背景使用整片 danger 填充 | 改为 4px 顶部彩色边带 + Badge 标签 |
| `bg.canvas` 用于读 / 工作主面 | semantic.\*.bg.canvas | SQL `<pre>` 已使用 `bg-bg-canvas` | 保留 |
| `bg.panel` 用于受控表面 | semantic.\*.bg.panel | `AlertDialogContent` 默认使用 | 保留 |
| `text.strong` / `text.muted` 文本层级 | semantic.\*.text | 内卡用 amber/danger 文字色彩 | 主体文本改回 `text.strong` / `text.muted` 中性色；只在 Badge 与边带保留风险色 |
| `typography.mono-sm` 用于技术内容 | typography.mono-sm | SQL `<pre>` + 对象列表已用 `font-mono` | 保留 |
| `Focused Modal Surface` 是 page mode | layout-modes | 当前 dialog 已是 | 保留，但去掉内部第二层卡片以符合"密度统一"原则 |
| `density.comfortable`: "dialog body" | density | 内卡 `p-4 space-y-3` 已偏紧 | dialog body 直接用 `space-y-4` 段间距 |
| `focusRing` 在 Cancel 上 | interaction.focusRing | 已通过 useRef + focus 实现 | 保留（ref 通过 prop 暴露到 Footer 的 Button） |
| 按钮 variant: outline + default/destructive | components 暗示 | 已正确（L2 用 default，L3 用 destructive） | 保留 |
| 按钮位置：`AlertDialogFooter` 标准对齐 | shadcn AlertDialog 契约 | **未使用 Footer** — 是核心问题 | 必须改为 `<AlertDialogFooter>` |

## Goals / Non-Goals

**Goals:**
- 让对话框只有"一层卡片"：外层 shadcn `AlertDialogContent`。
- 让标题只有一个：`AlertDialogTitle = 确认执行`，风险等级以 Badge 形式作为辅助标签。
- 按钮统一进入 `<AlertDialogFooter>`，与项目内其它 AlertDialog 风格一致。
- 信息全部保留：SQL 预览、受影响对象、L2/L3 文案、L3 不可撤销警告、Cancel 初始焦点、pending 状态、`confirmationInvalid.message`。
- 风险等级仍然第一眼可识别（彩色边带 + Badge），不丢失警示力。

**Non-Goals:**
- 不修改风险判定逻辑（`tabState.confirmation` 数据流、`SqlConfirmationPayload` 类型、`confirmationInvalid` 流程）。
- 不调整 i18n key（保留全部 13 个 `sqlConfirmation.*` key），仅可能调整 key 在 DOM 中的渲染位置。
- 不引入新依赖（Badge 已通过 `client/src/components/ui/badge.tsx` 存在，如果不存在则用 `<span>` + 自定义类，避免新增组件）。
- 不改动 backend 协议或 `useSqlWorkbenchStore` store 形状。
- 不改动 E2E `data-testid` 命名（`sql-confirmation-dialog`、`sql-risk-panel`、`sql-confirmation-invalid-message` 必须保留）。

## Decisions

### Decision 1 — 风险等级以 Badge + 4px 顶部边带替代整块卡片填充

**选择**：删除 `SqlConfirmationCard` 当前外层 `<div className="rounded-md border p-4 ... bg-amber/danger ...">`。改为：
- 在 dialog 内容区顶部插入一条 `h-1`（4px）的彩色细边带（amber 或 danger 实色），用作风险等级的视觉锚点；
- 在 `AlertDialogHeader` 中（标题旁）放一个 Badge：L2 文案"受限变更"用 amber、L3 文案"破坏性操作"用 danger。

**理由**：
- 现状的整块 amber/danger 填色让对话框看起来像在 dialog 里又塞了一张警告卡片（即用户截图的视觉问题）。
- 边带 + Badge 是一种克制的"严重程度指示器"，符合 DESIGN.md 的"Neutral Backbone, Focused Signal"原则——只在必要位置使用强调色，主体仍保持中性。
- Badge 文案直接复用 `sqlConfirmation.l2.title` / `sqlConfirmation.l3.title`，i18n key 不变。

**Rejected alternatives**：
- 在 SQL 预览 `<pre>` 块上加左侧 4px 风险色 border：会让 SQL 块与对话框其它内容割裂，且当 SQL 较长出现 `overflow-x-auto` 时左侧色条会被 SQL 内容遮挡。
- 用图标（如 AlertTriangle / Skull）替代 Badge：图标已经隐含在外层 `<AlertDialog>` 的默认语义里，再加图标重复且消耗水平空间。

### Decision 2 — 按钮搬到 `AlertDialogFooter`，由父组件（`sql-workbench-tab.tsx`）渲染

**选择**：从 `SqlConfirmationCard` 中**删除**按钮区块（当前第 86–104 行）。`SqlConfirmationCard` 变成"纯内容渲染"组件，只渲染：彩色边带 + SQL 预览 + 受影响对象列表 + 描述段 + L3 不可撤销提示。按钮由 `sql-workbench-tab.tsx` 在 `<AlertDialogFooter>` 内直接渲染，使用项目标准 shadcn `<AlertDialogCancel>` / `<AlertDialogAction>`（或 `<Button>` 配 onClick）。

**理由**：
- shadcn `AlertDialogFooter` 已经处理好 mobile 反向堆叠、桌面右对齐、间距 (`gap-2`)、与对话框 padding 对齐的所有细节。复用该组件 = 自动统一全项目对话框风格。
- 按钮 ref（cancel focus）通过将 `cancelRef` 提升到父组件、或者改用 `<AlertDialogCancel>` 自带的默认聚焦行为（shadcn 的 `AlertDialogCancel` 会在 dialog 打开时自动获得焦点）来满足"初始焦点在取消按钮"约束。
- Badge 出现的位置由父组件控制（紧邻 `AlertDialogTitle`），从而避免子卡片再渲染标题。

**Rejected alternatives**：
- 把按钮保留在 `SqlConfirmationCard` 内但去掉外层卡片背景：仍然不会出现在 `AlertDialogFooter` 标准位置，按钮风格与项目其它对话框不一致。该方案被驳回。
- 同时在 Card 内和 Footer 内提供按钮（让 caller 二选一）：违反单一职责，且增加 API 复杂度。

### Decision 3 — Badge 实现：复用现有 `Badge` 组件，否则用 inline span

**选择**：先检查 `client/src/components/ui/badge.tsx` 是否存在。
- 若存在且支持 `variant`：使用 `<Badge variant="warning">` / `<Badge variant="destructive">`（或最接近的语义）。
- 若不存在：直接用 `<span>` + Tailwind 类（`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium`），背景用 `var(--dt-accent-warn-surface)` / `var(--dt-status-danger-surface)`，文字用 `var(--dt-accent-warn)` / `var(--dt-status-danger)`。

**理由**：保持零新增依赖原则。Badge 是显示用语义标签的通用模式，inline span 也完全够用。

### Decision 4 — 初始焦点策略

**选择**：使用 shadcn `<AlertDialogCancel asChild>` 包裹自定义 `<Button>`。`AlertDialogCancel` 由 Radix 提供，open 时会自动获得焦点（与 `useEffect(() => cancelRef.current?.focus())` 等价），且无需再维护 ref。

**理由**：
- 现有 `useEffect + cancelRef` 在 Card 内是因为 Card 是普通 div；改用 Radix 标准 cancel 之后，焦点行为由 Radix 自动管理，更稳健。
- 单元测试中的"Cancel 获得初始焦点"断言只需查 `getByRole('button', { name: 'Cancel' })` 是否 `toHaveFocus()`，行为等价。

### Decision 5 — `confirmationInvalid.message` 显示位置

**选择**：保留在 `<AlertDialogContent>` 内、内容区域底部（SQL 卡片内容之后、`<AlertDialogFooter>` 之前），仍使用 `data-testid="sql-confirmation-invalid-message"` + `text-[var(--dt-status-danger)]`，作为"内容区的最后一条提示"展示。

**理由**：
- 该提示是当 acked risk 与 currentRisk 不匹配时的 inline 警告，逻辑上属于内容（"内容已变化，请重新确认"），不是 footer 操作。
- 不影响布局规整：Footer 永远在最底部，invalid message 永远在 Footer 之上、主内容之下。
- 现有 E2E 通过 testid 取值，不变更 testid 即可。

## Risks & Mitigations

| Risk | Mitigation |
|---|---|
| 移除内部 `cancelRef + useEffect` 后初始焦点行为变化 | 使用 Radix `AlertDialogCancel`，焦点由库托管；unit test 仍可断言 Cancel 按钮获得焦点 |
| 单元测试 `sql-confirmation-card.test.tsx` 当前断言"内部标题"和"按钮在 Card 内" | 同步重写测试：Badge 文案断言、按钮断言改为查父组件 dialog，Card 单测改为只验证内容渲染（SQL 预览、对象列表、L3 警告） |
| E2E 通过 `sql-risk-panel` / `sql-confirmation-dialog` testid 定位 | 严格保留这两个 testid：`sql-confirmation-dialog` 留在 `AlertDialogContent`；`sql-risk-panel` 应用到 `SqlConfirmationCard` 的根 `<div>`（去掉卡壳样式但保留 testid 与 `role/aria-label`） |
| Badge 组件可能不存在 | Decision 3 给出降级方案（inline span + token class） |
| `AlertDialogCancel` 接收按钮属性后，`disabled={pending}` 的行为是否正确 | 通过 `<AlertDialogCancel asChild><Button disabled={pending}>...</Button></AlertDialogCancel>` 包装；pending 时按钮 disabled，避免 dialog 被意外关闭 |

## Migration Plan

无后端迁移、无数据迁移。前端单次提交即可完成：
1. 重构 `sql-confirmation-card.tsx`：移除卡片外壳、标题行、按钮区，新增彩色边带、保留主体内容；导出 `SqlConfirmationCardProps` 不再包含 `onCancel/onExecute/pending`（按钮策略外迁）。新增可选 prop `level` 用于决定边带颜色（其实已有 `risk.level`，无需新增 prop）。
2. 重构 `sql-workbench-tab.tsx`：在 `AlertDialogHeader` 中渲染 Badge；在 `AlertDialogContent` 内插入重构后的 `SqlConfirmationCard` + `confirmationInvalid` 提示 + `AlertDialogFooter`（取消 / 执行按钮）。
3. 同步更新单测 `sql-confirmation-card.test.tsx`：删除按钮 / 内部标题断言；保留 SQL 预览、对象列表、L3 不可撤销断言。
4. （可选但推荐）补充 unit test 验证彩色边带的 className / style 在 L2/L3 下分别落在 amber/danger token 上。
5. 运行 `cd client && npx tsc --noEmit` 与 `npm test` 验证类型与单测；运行受影响的 e2e（`sql-editor-batch1-ui.spec.ts` 等）确认 testid 选择器仍生效。
