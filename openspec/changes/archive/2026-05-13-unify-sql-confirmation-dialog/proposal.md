## Why

SQL 风险确认对话框（`SqlConfirmationCard` + `AlertDialog` 组合）当前存在三个明显的视觉与一致性问题，已被用户截图反馈：

1. **双层卡片嵌套**：外层 `AlertDialogContent` 是 shadcn 的白色对话框外壳（带边框、padding、`stage.queryEditor.confirmation.title` 标题"确认执行"），内部 `SqlConfirmationCard` 又渲染了一个带边框、有色背景（L2 用 amber、L3 用 danger）、有自己标题的"子卡片"。两层卡片重叠造成视觉割裂，看起来像是"对话框里塞了另一张卡片"。

2. **标题重复 / 信息层级混乱**：外层 `AlertDialogTitle` 已经说"确认执行"，内层子卡片又显示"受限变更" / "破坏性操作"作为第二个 H 级标题，违反单一标题原则。风险等级本应是一个"状态标签 / 副标题"而不是另一个卡片标题。

3. **按钮布局风格不统一**：取消 / 执行按钮被塞进内层子卡片右下角，没有使用 shadcn `AlertDialogFooter`。结果是这个对话框的按钮位置、间距、对齐都与项目其它对话框不一致（例如其它 AlertDialog 都用 `AlertDialogFooter` 的右对齐 / 默认间距）。同时按钮挤在一块小区域内、与右侧 dialog 边缘距离极小，按钮风格不像项目其它 Dialog 那样有充足留白。

`client/DESIGN.md` 的相关契约明确：
- "Calm in Light, Crisp in Dark" — 不应让 dialog 看起来视觉过载。
- `Focused Modal Surface` 是首要 page mode 之一，其密度应当统一。
- 按钮颜色应该使用 `accent.primary` (cobalt) 和 `status.danger` 语义 token，但**位置 / 布局**应保持项目内一致。

## What Changes

- **取消 `SqlConfirmationCard` 自带的卡片外壳**：移除外层 `<div>` 的 `rounded-md border p-4` 与 amber/danger 背景填充，让卡片不再形成独立的视觉容器，而是作为对话框的内容主体直接铺开。
- **取消 `SqlConfirmationCard` 内的重复标题**：删除"受限变更 / 破坏性操作"这个内部标题行，把风险等级改为对话框副标题处的一个 `Badge`（彩色 pill 标签：L2 = amber、L3 = danger），与外层 `AlertDialogTitle` "确认执行"共同构成完整标题。
- **将按钮移到 `AlertDialogFooter`**：从 `SqlConfirmationCard` 内移除按钮 `<div>`；在 `sql-workbench-tab.tsx` 的 `AlertDialogContent` 内统一用 `<AlertDialogFooter>` 包裹"取消 / 执行"，统一项目内对话框风格（与其它 AlertDialog 一致的右对齐、间距、按钮密度）。
- **保留全部信息**：SQL 文本预览、受影响对象列表、L2/L3 文案、L3 不可撤销警告、Cancel 初始焦点、pending 状态、`confirmationInvalid.message` 失效提示 — 一项不少。
- **风险等级仍有视觉提示**：在对话框内容区顶部用一条 4px 高的彩色 status 边带（amber for L2、danger for L3）+ Badge 文字，替代原来的整块卡片填色，既不浪费视觉权重也不丢失"这是高风险操作"的提示。

## Capabilities

### Modified Capabilities

- `sql-confirmation`（新建该 capability spec — 当前 `openspec/specs/` 下尚无 SQL 风险确认相关 spec，本变更在 `openspec/changes/<name>/specs/sql-confirmation/spec.md` 中新增对应规约，作为 MODIFIED 增量进入归档时合并到 `openspec/specs/sql-confirmation/`）。

> Note: 由于 `openspec/specs/sql-confirmation/` 目前不存在，按 OpenSpec 惯例本变更同时承担"建立 spec 基线"职责，归档时直接落入新建的 capability 目录。

## Impact

- **Files changed (client)**:
  - `client/src/features/sql-confirmation/sql-confirmation-card.tsx` — 移除外卡壳 / 内标题 / 按钮区，新增风险 Badge 与彩色边带，按钮通过 props/外层渲染。
  - `client/src/features/stage/components/sql-workbench-tab.tsx` — 改造 `AlertDialogContent`：把按钮抽到 `<AlertDialogFooter>`，传入 `onCancel` / `onExecute` / `pending`。
  - `client/src/features/sql-confirmation/sql-confirmation-card.test.tsx` — 更新 unit test：移除对内部标题文案"Bounded mutation / Destructive operation"作为 `<h*>` 级标题的断言，改为断言 Badge 文案 + 副标题；按钮断言改为查 dialog 而不是 card；初始焦点仍在 Cancel。
- **No backend changes**.
- **No DB / data-source type compatibility** changes — `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` is **N/A**（不涉及数据源类型，纯前端 UI 布局重构）。
- **Design Inputs**: `client/DESIGN.md` applies. 主要 token：
  - `accent.warn` / `accent.warnSurface`（L2 Badge 与边带）
  - `status.danger` / `status.dangerSurface`（L3 Badge 与边带）
  - `bg.canvas`（SQL 预览代码块的内容背景）
  - `bg.panel`（dialog 内容主体背景，由 `AlertDialogContent` 默认提供）
  - `text.strong` / `text.muted`（副标题 / 受影响对象标签）
  - `typography.mono-sm`（SQL 文本 / 对象名）
  - `spacing.3` / `spacing.4`（Dialog 内 section 间距）
  - `density.comfortable`（dialog 内 body 区域密度）
  - `motion.fast`（按钮 hover 过渡）
- **Open BUGs overlapping**: 已检查 `docs/bugs/index.md` — 无与 `sql-confirmation` / SQL workbench risk dialog 模块直接相关的 open BUG（最近开放 BUG 集中在 ingestion 模块且均已 fixed，BUG-0034 之后无新增）。无 wontfix / duplicate 冲突。
- **Risks**:
  - 单元测试 (`sql-confirmation-card.test.tsx`) 的"initial focus on Cancel"断言依赖 cancelRef 仍在卡片内或作为 prop 透传到外层 Footer 的 Button。需在重构时确保 ref 路径仍有效。
  - 当前 E2E `sql-workbench.page.ts` 通过 `data-testid="sql-confirmation-dialog"` 与 `data-testid="sql-risk-panel"` 定位元素。重构后必须保留这两个 testid（dialog testid 放在 `AlertDialogContent`，`sql-risk-panel` testid 仍标识对话框主体的内容区），否则会击穿现有 E2E。
  - `confirmationInvalid.message` 在 `sql-workbench-tab.tsx` 第 807-811 行作为 dialog 内独立段落渲染，本次重构需保持其相对于 Footer 的位置正确（在 Footer 之上，跟主体内容同一信息区）。
