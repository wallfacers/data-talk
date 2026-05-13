## 1. Preflight

- [x] 1.1 Read `client/DESIGN.md` 全文，重点 sections：semantic tokens (`accent.warn`, `status.danger`, `bg.canvas`, `bg.panel`, `text.strong/muted`)、`density.comfortable`、`Focused Modal Surface` layout mode、components 契约
- [x] 1.2 Read `client/src/features/sql-confirmation/sql-confirmation-card.tsx`（107 行，全文）与 `sql-confirmation-card.test.tsx`（76 行，全文）
- [x] 1.3 Read `client/src/features/stage/components/sql-workbench-tab.tsx` 第 780–820 行（AlertDialog 调用上下文）以及 `handleCancelConfirmation`、`handleConfirmExecute` 回调定义（约第 631–647 行）
- [x] 1.4 Read `client/src/components/ui/alert-dialog.tsx` 全文，确认 `AlertDialogFooter`、`AlertDialogCancel`、`AlertDialogAction` 的导出 / 行为
- [x] 1.5 Read `client/src/components/ui/badge.tsx` 与 `client/src/components/ui/button.tsx`，确认 Badge 的 variants（destructive 已存在，warning 不存在 → L2 用 className 覆盖）
- [x] 1.6 Grep `client/tests/e2e/` 中所有引用 `sql-confirmation-dialog`、`sql-risk-panel` 的位置，记录必须保留的 testid 列表
- [x] 1.7 Run `cd client && npx tsc --noEmit` 确认基线零类型错误

## 2. Refactor `SqlConfirmationCard`：去除外卡壳 + 内标题 + 按钮区

- [x] 2.1 修改 `SqlConfirmationCardProps`：移除 `pending`、`onCancel`、`onExecute` 三个 prop（按钮策略外迁到父组件）
- [x] 2.2 移除组件内 `useRef<HTMLButtonElement>` 与 `useEffect` 聚焦逻辑（焦点改由 Radix `AlertDialogCancel` 托管）
- [x] 2.3 移除根 `<div>` 的 `rounded-md border p-4` 与 amber/danger 背景填充类；改为：
  - 根 `<div className="space-y-3" data-testid="sql-risk-panel" role="group" aria-label={...}>`（保留 `sql-risk-panel` testid）
  - 顶部插入 4px 彩色边带：`<div aria-hidden className={cn('h-1 w-full rounded-full', isL3 ? 'bg-[var(--dt-status-danger)]' : 'bg-[var(--dt-accent-warn)]')} />`（采用 `w-full` 填满 dialog 内容宽度，不需要负 margin 跨越 padding；实施时验证视觉满意）
- [x] 2.4 删除原"●  受限变更 / 破坏性操作"标题行（第 50–59 行）—— 标题等级由父组件的 Badge 承载
- [x] 2.5 保留 SQL 预览 `<pre>`，但 className 调整为 `rounded-md bg-[var(--dt-bg-canvas)] p-3 font-mono text-sm overflow-x-auto whitespace-pre text-[var(--dt-text-strong)] border border-[var(--dt-border-subtle)]`（改用 token，加细边以保持代码块边界）
- [x] 2.6 保留"受影响对象 / Affected objects"标签与对象列表，但文字改回中性色（`text-[var(--dt-text-strong)]` 标签 + `text-[var(--dt-text-muted)]` 列表项），不再用 amber/danger
- [x] 2.7 保留 L2/L3 body 描述段；文字改用 `text-[var(--dt-text-base)]`
- [x] 2.8 保留 L3 不可撤销提示，但仅这一行保留 `text-[var(--dt-status-danger)] font-semibold`（warning 强调）
- [x] 2.9 整体段间距改为 `space-y-3`（dialog body 内紧凑度，配 `density.comfortable`）

## 3. Refactor `sql-workbench-tab.tsx`：使用 AlertDialogFooter 渲染按钮

- [x] 3.1 在 `AlertDialog open` 块顶部 import 增加：`AlertDialogFooter`、`AlertDialogCancel`、`AlertDialogAction`、`Badge`
- [x] 3.2 在 `<AlertDialogHeader>` 内，紧邻 `<AlertDialogTitle>` 渲染 Badge：
  - L3：`<Badge variant="destructive">{t('sqlConfirmation.l3.title')}</Badge>`
  - L2：`<Badge variant="secondary" className="bg-[var(--dt-accent-warn-surface)] text-[var(--dt-accent-warn)] border-[color-mix(in_srgb,var(--dt-accent-warn)_30%,transparent)]">{t('sqlConfirmation.l2.title')}</Badge>`
  - 通过 `tabState.confirmation.level === 'L3'` 三元判定
  - Header 容器用 `<div className="flex flex-wrap items-center gap-2">` 包裹 Title + Badge 同行（不动 AlertDialogHeader 默认 grid 布局）
- [x] 3.3 `<SqlConfirmationCard>` 调用更新：仅传 `risk` 与 `sqlPreview`（移除 `pending`、`onCancel`、`onExecute`）
- [x] 3.4 在 `SqlConfirmationCard` 之后、Footer 之前保留 `confirmationInvalid.message` 段落（不动 testid）
- [x] 3.5 新增 `<AlertDialogFooter>` 紧贴 `AlertDialogContent` 底部：
  - 取消按钮：`<AlertDialogCancel disabled={pending} onClick={handleCancelConfirmation}>{t('sqlConfirmation.cancel')}</AlertDialogCancel>`（base-ui 的 `AlertDialogCancel` 已默认 `variant="outline"`，无需手写 Button 包装；children/disabled/onClick 通过 mergeProps 流入渲染 Button）
  - 执行按钮：`<AlertDialogAction variant={isL3 ? 'destructive' : 'default'} disabled={pending} onClick={() => void handleConfirmExecute()}>{pending ? t('sqlConfirmation.executing') : t('sqlConfirmation.execute')}</AlertDialogAction>`
  - 顺序：Cancel 在左、Execute 在右（与项目其它 dialog 一致；`AlertDialogFooter` 自带 `sm:flex-row sm:justify-end`）

## 4. Update unit test `sql-confirmation-card.test.tsx`

- [x] 4.1 删除"按钮在 Card 内"相关断言（原第 56–75 行：initial focus / onCancel-onExecute-click / pending 三段测试）—— 这些行为现在由父组件 Footer 承担
- [x] 4.2 修改 L2/L3 渲染断言：改为断言 Card 内 NOT 出现"Bounded mutation"/"Destructive operation"作为标题（Badge 由父组件渲染）
- [x] 4.3 保留 SQL 预览渲染断言（"renders the SQL preview as a read-only code block"）
- [x] 4.4 保留"lists each affected object"断言
- [x] 4.5 保留"will modify data in orders" / "will permanently affect temp_log" / "This action cannot be undone." 文案断言（这些都属于 Card 主体内容）
- [x] 4.6 新增断言：L2 时根容器应渲染 amber 彩色边带 `[aria-hidden]` 子元素；L3 时应渲染 danger 彩色边带子元素（基于 className regex 检查）
- [x] 4.7 新增 sql-risk-panel testid 存在性断言 + 空对象数组 em-dash 占位符断言（增强 spec 覆盖）

## 5. （可选）新增 Footer 行为单测

- [ ] 5.1 暂未在 `sql-workbench-tab.test.tsx`（不存在）补充按钮行为单测。Footer 按钮的交互测试通过 e2e（section 7.5）间接覆盖。
- [x] 5.2 决议：`sql-workbench-tab.test.tsx` 不存在 → 直接走 e2e 覆盖路径，不强制新建单测（与 design.md 一致）

## 6. E2E 兼容验证

- [x] 6.1 grep `client/tests/e2e/` 中 `sql-confirmation-dialog` / `sql-risk-panel` 全部用法 → 仅 `pom/sql-workbench.page.ts` 第 134/145 行引用 `sql-risk-panel`；`sql-confirmation-dialog` 与 `sql-confirmation-invalid-message` 未在 e2e 中被直接 selector 命中，仅作为产品代码 testid 暴露
- [x] 6.2 检查 `client/tests/e2e/pom/sql-workbench.page.ts` 内 `sql-risk-panel` 的等待 / 交互逻辑 → `getRiskMessage()` 取 textContent，`waitForResult()` 用作 OR locator，新 panel 仍含 SQL 预览/对象列表/描述/警告，textContent 非空且语义保留
- [x] 6.3 grep e2e 是否对按钮位置有 selector 依赖 → 无（POM 中无 `[role=dialog] button` 或 footer 子元素 selector 依赖）

## 7. Consolidated verification

- [x] 7.1 Run `cd client && npx tsc --noEmit` — 零类型错误
- [x] 7.2 Run `cd client && npx vitest run src/features/sql-confirmation` — 20/20 通过（9 个 Card 单测 + 11 个父子集成测试 `sql-confirmation-dialog.integration.test.tsx`）
- [x] 7.3 Run `cd client && npx vitest run src/features/stage` — sql-workbench 相关 0 失败；预存 stage-window ER designer empty-state 1 处失败已确认与本变更无关（baseline `git stash` 复现）
- [ ] 7.4 启动 `cd client && npm run dev` 并手动触发一次 L2（如 `UPDATE` 带 WHERE）+ 一次 L3（如 `DROP TABLE`）SQL 目测验证 — **待用户在本地视觉验证**（WSL 无桌面浏览器，无法 agent 自验）。结构等效已由 11 个集成测试覆盖（dialog 单层 / Badge 与 Title 同行 / Footer 内按钮 / 顶部彩色边带 / invalid 位置 / pending 行为 / 三个 testid 保留）
- [x] 7.5 Run 受影响 e2e: `npx playwright test sql-editor-batch1-ui.spec.ts` — **baseline 已损坏**（baseline `git stash` 跑 1.4 同样报 "Monaco editor not available"，截图显示 stage 根本未打开）。该失败与本变更**无关**，是预存 e2e infrastructure 问题；本变更不引入新 e2e 回归
- [x] 7.6 BUG 登记：本轮 e2e 中**发现 0 个新 BUG**（与本变更相关）。e2e baseline 失败属于已存在的环境/setup 问题，超出本变更范围，未在 `docs/bugs/` 新建条目（如确属未登记的预存问题，建议另开 BUG 跟踪 e2e infrastructure 失效）**
