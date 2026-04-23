# Client Design System Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `client/` 建立可执行的设计契约落点，并完成第一批设计系统对齐：`client/DESIGN.md`、全局 semantic token、基础 UI atoms，以及 `Sidebar / Composer / Stage` 三个核心工作台表面。

**Architecture:** 本计划采用“契约先行，语义映射承接，关键表面分批校准”的策略。先把 [DataTalk Client Design System Design](../product-specs/2026-04-23-datatalk-client-design-system-design.md) 落成 `client/DESIGN.md` 与 `globals.css` token 映射层，再更新基础 atoms（`Button / InputGroup / Table`），最后分别对齐 `Sidebar`、`PromptComposer` 和 `StageWindow` 三个高频表面。保持现有 `shadcn/ui` 组件协议和现有 store / feature 边界不变，避免一次性大范围视觉重构。

**Tech Stack:** React 19, TypeScript, Tailwind CSS v4, shadcn/ui, Vitest, Testing Library, `@google/design.md` CLI

---

## Spec Mapping

- [2026-04-23-datatalk-client-design-system-design.md](../product-specs/2026-04-23-datatalk-client-design-system-design.md)
  - §3/§4：把 `AI-native data research workbench` 与 `Dual-Core, One System` 固化到 `client/DESIGN.md`
  - §5：建立 `primitive → semantic → interaction → component alias` token 层，并映射到 `globals.css`
  - §6：落实字体、spacing、density 到基础组件尺寸与表面节奏
  - §7：对齐 `App Shell / Conversation Workspace / Instrument Panel`
  - §8：更新 `Button / InputGroup / Table / Sidebar / PromptComposer / Stage`
  - §9/§10：保持图表语义接口、动效和可访问性约束
  - §11/§12/§13：新增 `client/DESIGN.md`、在前端文档中登记其为唯一设计契约，并完成 lint / tests / `npx tsc --noEmit`

## File Structure

### Contract Layer

- Create: `client/DESIGN.md`
- Modify: `client/src/styles/globals.css`
- Modify: `docs/FRONTEND.md`

### Base UI Atoms

- Create: `client/src/components/ui/__tests__/design-system-foundation.test.tsx`
- Modify: `client/src/components/ui/button.tsx`
- Modify: `client/src/components/ui/input-group.tsx`
- Modify: `client/src/components/ui/table.tsx`

### Navigation Surface

- Modify: `client/src/components/ui/sidebar.tsx`
- Modify: `client/src/features/workspace/components/app-sidebar.tsx`
- Modify: `client/src/features/workspace/components/__tests__/app-sidebar.test.tsx`

### Conversation Surface

- Modify: `client/src/features/session/prompt-composer.tsx`
- Modify: `client/src/features/session/__tests__/prompt-composer.test.tsx`

### Instrument Surface

- Create: `client/src/features/stage/components/stage-workbench-empty-state.test.tsx`
- Modify: `client/src/features/stage/components/stage-window.tsx`
- Modify: `client/src/features/stage/components/stage-window.test.tsx`
- Modify: `client/src/features/stage/components/stage-workbench-empty-state.tsx`

### Plan Housekeeping

- Modify: `docs/exec-plans/2026-04-23-client-design-system-foundation-plan.md`
- Modify: `docs/exec-plans/index.md`

## Parallelization Notes

- Task 1 必须先完成，因为后续所有实现都依赖 `client/DESIGN.md` 与 semantic token 命名。
- Task 2（Base UI Atoms）是 Task 3/4/5 的共同前置，因为 `Sidebar / Composer / Stage` 都依赖统一的按钮、输入和表格语言。
- Task 3（Navigation Surface）、Task 4（Conversation Surface）和 Task 5（Instrument Surface）在 Task 2 完成后可以并行执行，写集互不重叠。
- 依据仓库“并行批次后统一验证”的规则，本计划只在最终做一轮 consolidated verification：设计 lint、目标 vitest 集、`npx tsc --noEmit`。

## Task 1: 建立 `client/DESIGN.md` 契约与 semantic token 映射层

**Files:**
- Create: `client/DESIGN.md`
- Modify: `client/src/styles/globals.css`

- [ ] **Step 1: 写 `client/DESIGN.md`，把 approved spec 落为 design contract**

```md
---
name: DataTalk Client
product: AI-native data research workbench
themes:
  strategy: dual-theme
primitives:
  neutral:
    25: "#FCFDFE"
    50: "#F8FAFC"
    200: "#E2E8F0"
    500: "#64748B"
    900: "#0F172A"
  cobalt:
    400: "#60A5FA"
    700: "#1D4ED8"
  amber:
    400: "#FBBF24"
    500: "#F59E0B"
semantic:
  light:
    bg:
      app: neutral.25
      panel: neutral.0
      subtle: neutral.50
    text:
      strong: neutral.900
      muted: neutral.600
  dark:
    bg:
      app: neutral.950
      panel: neutral.900
      subtle: neutral.800
typography:
  font:
    ui: "'Source Sans 3', 'Noto Sans SC', 'PingFang SC', sans-serif"
    mono: "'JetBrains Mono', 'SFMono-Regular', monospace"
components:
  sidebar:
    bg: bg.subtle
  composer:
    bg: bg.panel
---

## Overview
DataTalk 是一台可协作、可推理、可操作的数据研究仪器。

## Principles
- Precision First
- Dual-Core, One System
- Neutral Backbone, Focused Signal
```

- [ ] **Step 2: 运行 design lint，确认 `client/DESIGN.md` 可被 `design.md` CLI 解析**

Run:

```bash
cd client && npx @google/design.md lint DESIGN.md
```

Expected:

```text
Lint completed with 0 errors
```

- [ ] **Step 3: 改造 `globals.css`，建立“现有 shadcn token ← 新 semantic token”映射层**

```css
:root {
  --dt-bg-app: oklch(0.985 0.002 255);
  --dt-bg-panel: oklch(1 0 0);
  --dt-bg-subtle: oklch(0.975 0.003 255);
  --dt-text-strong: oklch(0.19 0.01 255);
  --dt-text-muted: oklch(0.49 0.02 255);
  --dt-border-subtle: oklch(0.91 0.006 255);
  --dt-accent-primary: oklch(0.53 0.19 259);
  --dt-accent-primary-surface: oklch(0.96 0.02 255);
  --background: var(--dt-bg-app);
  --card: var(--dt-bg-panel);
  --muted: var(--dt-bg-subtle);
  --foreground: var(--dt-text-strong);
  --muted-foreground: var(--dt-text-muted);
  --border: var(--dt-border-subtle);
  --primary: var(--dt-accent-primary);
}

.dark {
  --dt-bg-app: oklch(0.16 0.01 255);
  --dt-bg-panel: oklch(0.21 0.01 255);
  --dt-bg-subtle: oklch(0.25 0.01 255);
  --dt-text-strong: oklch(0.98 0 0);
  --dt-text-muted: oklch(0.72 0.01 255);
  --dt-border-subtle: oklch(1 0 0 / 0.12);
  --dt-accent-primary: oklch(0.68 0.14 258);
  --dt-accent-primary-surface: oklch(0.31 0.05 258 / 0.36);
}
```

- [ ] **Step 4: 保持现有 Tailwind semantic class 可用，不在第一轮就把全项目改成裸 CSS 变量类**

```css
@theme inline {
  --color-background: var(--background);
  --color-card: var(--card);
  --color-muted: var(--muted);
  --color-foreground: var(--foreground);
  --color-muted-foreground: var(--muted-foreground);
  --color-border: var(--border);
  --color-primary: var(--primary);
}
```

## Task 2: 对齐基础 atoms：`Button / InputGroup / Table`

**Files:**
- Create: `client/src/components/ui/__tests__/design-system-foundation.test.tsx`
- Modify: `client/src/components/ui/button.tsx`
- Modify: `client/src/components/ui/input-group.tsx`
- Modify: `client/src/components/ui/table.tsx`

- [ ] **Step 1: 先写 failing tests，锁定基础组件的设计语义**

```tsx
describe('design-system foundation', () => {
  it('supports a tonal button variant for secondary emphasis', () => {
    render(<Button variant="tonal">Focus</Button>)
    expect(screen.getByRole('button')).toHaveClass('bg-primary/10')
    expect(screen.getByRole('button')).toHaveClass('text-primary')
  })

  it('renders InputGroup as a panel-grade shell instead of a plain input border', () => {
    render(
      <InputGroup>
        <InputGroupTextarea aria-label="composer" />
      </InputGroup>,
    )
    expect(screen.getByRole('group')).toHaveClass('rounded-2xl')
    expect(screen.getByRole('group')).toHaveClass('bg-card/95')
  })

  it('renders Table with structured header and selected-row semantics', () => {
    render(
      <Table>
        <TableHeader>
          <tr><TableHead>Name</TableHead></tr>
        </TableHeader>
        <TableBody>
          <TableRow data-state="selected"><TableCell>Alice</TableCell></TableRow>
        </TableBody>
      </Table>,
    )
    expect(screen.getByText('Name').closest('th')).toHaveClass('bg-muted/60')
    expect(screen.getByText('Alice').closest('tr')).toHaveClass('data-[state=selected]:bg-primary/8')
  })
})
```

- [ ] **Step 2: 运行基础组件测试，确认先红**

Run:

```bash
cd client && npx vitest run src/components/ui/__tests__/design-system-foundation.test.tsx
```

Expected:

```text
FAIL  design-system-foundation.test.tsx
+ Expected class "bg-primary/10" / "rounded-2xl" / "bg-muted/60" not found
```

- [ ] **Step 3: 实现 `Button` 的 `tonal` 语义、`InputGroup` 的 panel shell、`Table` 的结构化表头/选中态**

```tsx
const buttonVariants = cva(
  "inline-flex items-center justify-center rounded-lg border border-transparent text-sm font-medium transition-all ...",
  {
    variants: {
      variant: {
        default: "bg-primary text-primary-foreground hover:bg-primary/92",
        tonal: "border-primary/15 bg-primary/10 text-primary hover:bg-primary/14",
        outline: "border-border bg-card hover:bg-muted/80",
        ghost: "text-muted-foreground hover:bg-muted/80 hover:text-foreground",
        destructive: "bg-destructive/10 text-destructive hover:bg-destructive/16",
      },
    },
  },
)
```

```tsx
<div
  data-slot="input-group"
  role="group"
  className={cn(
    "group/input-group relative flex min-h-11 w-full items-stretch rounded-2xl border border-border/80 bg-card/95 shadow-[0_8px_24px_rgba(15,23,42,0.06)] transition-all ...",
    className,
  )}
>
```

```tsx
function TableHeader({ className, ...props }: React.ComponentProps<"thead">) {
  return (
    <thead
      data-slot="table-header"
      className={cn("bg-muted/60 text-muted-foreground [&_tr]:border-b", className)}
      {...props}
    />
  )
}

function TableRow({ className, ...props }: React.ComponentProps<"tr">) {
  return (
    <tr
      data-slot="table-row"
      className={cn(
        "border-b transition-colors hover:bg-muted/45 data-[state=selected]:bg-primary/8 data-[state=selected]:text-foreground",
        className,
      )}
      {...props}
    />
  )
}
```

- [ ] **Step 4: 重新运行基础组件测试，确认转绿**

Run:

```bash
cd client && npx vitest run src/components/ui/__tests__/design-system-foundation.test.tsx
```

Expected:

```text
PASS  design-system-foundation.test.tsx
```

## Task 3: 对齐导航骨架：`sidebar.tsx` 与 `AppSidebar`

**Files:**
- Modify: `client/src/components/ui/sidebar.tsx`
- Modify: `client/src/features/workspace/components/app-sidebar.tsx`
- Modify: `client/src/features/workspace/components/__tests__/app-sidebar.test.tsx`

- [ ] **Step 1: 扩充 `app-sidebar` 测试，锁定“骨架弱、唯一强调主动作”的导航语义**

```tsx
it('renders the primary create-session CTA as the sidebar’s single emphasized action', async () => {
  renderWithProviders([])
  const createButton = screen.getByText('创建会话').closest('button')
  expect(createButton).toHaveClass('bg-primary')
  expect(createButton).toHaveClass('text-primary-foreground')
})

it('renders the collapsed floating control pill with a bordered instrument shell', async () => {
  document.cookie = 'sidebar_state=false'
  renderWithProviders([])
  const floating = document.querySelector('.fixed.left-4.top-1\\.5') as HTMLElement
  expect(floating.className).toContain('ring-sidebar-border')
})
```

- [ ] **Step 2: 运行导航测试，确认先红**

Run:

```bash
cd client && npx vitest run src/features/workspace/components/__tests__/app-sidebar.test.tsx
```

Expected:

```text
FAIL  app-sidebar.test.tsx
+ Missing stronger shell / CTA class assertions
```

- [ ] **Step 3: 改造 `sidebar.tsx` 与 `app-sidebar.tsx`，把导航骨架校准为“低存在感骨架 + 唯一主 CTA”**

```tsx
function SidebarInset({ className, ...props }: React.ComponentProps<"main">) {
  return (
    <main
      data-slot="sidebar-inset"
      className={cn(
        "relative flex w-full flex-1 flex-col bg-background md:peer-data-[variant=inset]:m-2 md:peer-data-[variant=inset]:ml-0 md:peer-data-[variant=inset]:rounded-2xl md:peer-data-[variant=inset]:border md:peer-data-[variant=inset]:border-border/70 md:peer-data-[variant=inset]:shadow-[0_16px_40px_rgba(15,23,42,0.08)]",
        className,
      )}
      {...props}
    />
  )
}
```

```tsx
<div className="fixed left-4 top-1.5 z-50 flex items-center gap-1 rounded-full bg-sidebar/92 p-1 shadow-lg ring-1 ring-sidebar-border backdrop-blur-sm">
  <SidebarTrigger className="size-7 rounded-full" />
  <Button
    variant="ghost"
    size="icon-sm"
    className="size-7 rounded-full"
    onClick={handleCreate}
    disabled={createMut.isPending}
  >
    <PlusIcon className="size-4" />
  </Button>
</div>
```

```tsx
<SidebarMenuButton
  tooltip={t('workspace.createSession')}
  className="min-w-8 justify-center bg-primary text-primary-foreground shadow-sm hover:bg-primary/92 active:bg-primary/92"
  onClick={handleCreate}
  disabled={createMut.isPending}
>
```

- [ ] **Step 4: 重新运行导航测试，确认转绿**

Run:

```bash
cd client && npx vitest run src/features/workspace/components/__tests__/app-sidebar.test.tsx
```

Expected:

```text
PASS  app-sidebar.test.tsx
```

## Task 4: 对齐协作主表面：`PromptComposer`

**Files:**
- Modify: `client/src/features/session/prompt-composer.tsx`
- Modify: `client/src/features/session/__tests__/prompt-composer.test.tsx`

- [ ] **Step 1: 先补 failing tests，锁定默认态与 bang-query 态的表面语义**

```tsx
it('renders the composer as a panel-grade instrument surface', async () => {
  renderWithClient(<PromptComposer />)
  const shell = screen.getByRole('group')
  expect(shell).toHaveClass('rounded-2xl')
  expect(shell).toHaveClass('border-border/80')
})

it('switches into a warn-toned shell when bang query mode is active', async () => {
  renderWithClient(<PromptComposer />)
  fireEvent.change(screen.getByPlaceholderText('用自然语言查询你的数据库...'), {
    target: { value: '!select 1' },
  })
  const shell = screen.getByRole('group')
  expect(shell).toHaveAttribute('data-bang-query-mode', 'true')
  expect(shell.className).toContain('border-amber-500/45')
})
```

- [ ] **Step 2: 运行 composer 测试，确认新增断言先红**

Run:

```bash
cd client && npx vitest run src/features/session/__tests__/prompt-composer.test.tsx
```

Expected:

```text
FAIL  prompt-composer.test.tsx
+ Expected updated surface classes for default / bang-query mode
```

- [ ] **Step 3: 调整 `PromptComposer`，把它从“普通输入框”提升为研究工作台控制面板**

```tsx
<InputGroup
  data-bang-query-mode={isBangQueryMode ? 'true' : undefined}
  className={cn(
    "rounded-2xl border-border/80 bg-card/95 shadow-[0_12px_28px_rgba(15,23,42,0.08)] transition-all focus-within:border-primary/40 focus-within:shadow-[0_18px_40px_rgba(15,23,42,0.10)]",
    isBangQueryMode && "border-amber-500/45 bg-amber-50/70 focus-within:border-amber-500/70 dark:bg-amber-950/18",
  )}
>
```

```tsx
<InputGroupTextarea
  className={cn(
    "h-[90px] resize-none overflow-y-auto px-4 py-4 text-[14px] leading-6 text-foreground",
    isBangQueryMode && "text-amber-900 placeholder:text-amber-700/60 dark:text-amber-100",
  )}
/>
```

```tsx
{isStreaming ? (
  <Button type="button" variant="destructive" size="icon-xs" className="rounded-full" ...>
    <Loader2Icon className="size-3.5 animate-spin" />
  </Button>
) : (
  <Button
    type="submit"
    size="icon-xs"
    aria-disabled={!canSend}
    className="rounded-full bg-primary text-primary-foreground shadow-sm hover:bg-primary/92"
  >
    <ArrowUpIcon className="size-3.5" />
  </Button>
)}
```

- [ ] **Step 4: 重新运行 composer 测试，确认行为与新表面样式都转绿**

Run:

```bash
cd client && npx vitest run src/features/session/__tests__/prompt-composer.test.tsx
```

Expected:

```text
PASS  prompt-composer.test.tsx
```

## Task 5: 对齐仪器主表面：`StageWindow` 与 `StageWorkbenchEmptyState`

**Files:**
- Create: `client/src/features/stage/components/stage-workbench-empty-state.test.tsx`
- Modify: `client/src/features/stage/components/stage-window.tsx`
- Modify: `client/src/features/stage/components/stage-window.test.tsx`
- Modify: `client/src/features/stage/components/stage-workbench-empty-state.tsx`

- [ ] **Step 1: 先写 failing tests，锁定 Stage shell 和空状态卡片的设计目标**

```tsx
it('renders Stage with an instrument-grade shell instead of a generic card shell', () => {
  const { container } = render(<StageWindow sessionId="s1" />)
  const shell = container.firstElementChild as HTMLElement
  expect(shell.className).toContain('rounded-[22px]')
  expect(shell.className).toContain('border-border/75')
  expect(shell.className).toContain('bg-muted/25')
})

it('renders Stage empty cards as structured instrument tiles', () => {
  render(<StageWorkbenchEmptyState onOpenSqlEditor={vi.fn()} />)
  const sqlCard = screen.getByRole('button', { name: /SQL 编辑器/ })
  expect(sqlCard.className).toContain('rounded-2xl')
  expect(sqlCard.className).toContain('hover:border-primary/45')
})
```

- [ ] **Step 2: 运行 Stage 相关测试，确认先红**

Run:

```bash
cd client && npx vitest run src/features/stage/components/stage-window.test.tsx src/features/stage/components/stage-workbench-empty-state.test.tsx
```

Expected:

```text
FAIL  stage-window.test.tsx
FAIL  stage-workbench-empty-state.test.tsx
+ Expected updated shell / empty-state surface classes
```

- [ ] **Step 3: 在不回滚现有 dirty worktree 改动的前提下，叠加 Stage 的 Foundation 视觉校准**

```tsx
return (
  <div className="flex h-full w-full flex-col overflow-hidden rounded-[22px] border border-border/75 bg-muted/25 shadow-[0_20px_48px_rgba(15,23,42,0.10)] ring-1 ring-black/5 transition-all duration-200">
    <div className="group flex h-10 shrink-0 select-none items-center justify-between border-b border-border/60 bg-background/65 backdrop-blur-sm">
```

```tsx
<button
  key={id}
  type="button"
  disabled={!enabled}
  data-state={enabled ? 'ready' : 'pending'}
  className={cn(
    "group flex min-h-[104px] w-full items-start justify-between gap-4 rounded-2xl border px-4 py-3 text-left transition-colors",
    enabled
      ? "border-border/60 bg-background/96 shadow-[0_8px_24px_rgba(15,23,42,0.05)] hover:border-primary/45 hover:bg-primary/5"
      : "cursor-not-allowed border-border/45 bg-muted/35 text-muted-foreground opacity-75",
  )}
>
```

```tsx
<div className="flex size-10 items-center justify-center rounded-xl border border-primary/20 bg-primary/10 text-primary">
  <DatabaseIcon className="size-5" />
</div>
```

- [ ] **Step 4: 重新运行 Stage 相关测试，确认转绿**

Run:

```bash
cd client && npx vitest run src/features/stage/components/stage-window.test.tsx src/features/stage/components/stage-workbench-empty-state.test.tsx
```

Expected:

```text
PASS  stage-window.test.tsx
PASS  stage-workbench-empty-state.test.tsx
```

## Task 6: Consolidated verification 与文档收尾

**Files:**
- Modify: `docs/FRONTEND.md`
- Modify: `docs/exec-plans/2026-04-23-client-design-system-foundation-plan.md`
- Modify: `docs/exec-plans/index.md`

- [ ] **Step 1: 在前端开发指南中登记 `client/DESIGN.md` 为唯一设计契约**

```md
### 设计契约

- `client/DESIGN.md` 是客户端视觉规则的唯一真源
- 新 UI 开发前先读取 `client/DESIGN.md`
- 组件与页面优先复用 semantic token，不直接写裸色值
```

- [ ] **Step 2: 运行 design lint + 目标 vitest 集 + TypeScript typecheck**

Run:

```bash
cd client && npx @google/design.md lint DESIGN.md
cd client && npx vitest run \
  src/components/ui/__tests__/design-system-foundation.test.tsx \
  src/features/workspace/components/__tests__/app-sidebar.test.tsx \
  src/features/session/__tests__/prompt-composer.test.tsx \
  src/features/stage/components/stage-window.test.tsx \
  src/features/stage/components/stage-workbench-empty-state.test.tsx
cd client && npx tsc --noEmit
```

Expected:

```text
Lint completed with 0 errors
All targeted vitest files PASS
TypeScript found 0 errors
```

- [ ] **Step 3: 把本计划的 checkbox 全部按实际结果勾完，并在索引中从 Active 移到 Completed**

```md
## 已完成计划

| 计划 | 完成日期 | 摘要 |
|------|---------|------|
| [Client Design System Foundation](./2026-04-23-client-design-system-foundation-plan.md) | 2026-04-23 | `client/DESIGN.md`、semantic token 映射、Button/InputGroup/Table、Sidebar、PromptComposer、Stage Foundation 表面已落地，design lint、目标 vitest 与 `npx tsc --noEmit` 全部通过。 |
```

- [ ] **Step 4: 若实现中对 spec 有实质收敛，只做最小文档同步，不扩大 spec 范围**

```md
- 若首轮只 shipped foundation surfaces，则在 spec §12“分阶段落地顺序”补一行状态说明；
- 不要把 settings/chart/message 全量重做误写成已完成。
```

## Decisions

- 本计划故意限定为 `Foundation / Phase 1`，不在同一批次内处理 `settings / charts / full message surfaces / hero`。
- 保留现有 `shadcn` semantic token 入口（`bg-background / bg-card / text-muted-foreground / border-border`），通过 `globals.css` 映射新设计语义，降低回归风险。
- 字体先以 fallback stack 承接；如果 Web Font 引入造成布局波动，单开后续计划处理。
- 执行阶段若发现 `client/src/features/stage/components/stage-window.tsx` 已有未提交改动，必须先阅读当前 diff 并在其基础上叠加视觉更新，不得回滚无关变更。

## Self-Review

- Spec coverage: `client/DESIGN.md`、semantic token、atoms、navigation、conversation、instrument、design lint、前端文档与计划收尾均有任务对应。
- Placeholder scan: 无 `TODO / TBD / 适当处理` 这类模糊描述；每个任务都指向具体文件与命令。
- Type consistency: 统一使用 `client/DESIGN.md` 作为契约名，统一使用 `Foundation / Phase 1` 描述本次落地范围，统一以 `semantic token 映射` 作为实现策略。
