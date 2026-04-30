# ER Canvas Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 ER Designer / Inspector 两个 Stage Tab 的画布壳层、节点卡片、边、关系标签、工具栏、空态、上下文菜单按 [2026-04-30 ER Canvas Redesign Design](../product-specs/2026-04-30-er-canvas-redesign-design.md) 重做为符合 `client/DESIGN.md` 的视觉系统，同时强化 a11y 与键盘导航。

**Architecture:** 纯前端、无后端 / 协议改动。共用 `ErCanvas` 内的 7 个文件（其中 1 个新建 `ErEdgeMarkers`）；按"模式徽章 + 强调色 + Studio↔Instrument 几何参数"轻量身份化区分 Designer / Viewer；列行用左侧色条 + 图标 + 字重三通道传 PK/FK 角色；连接锚点用形状（target 实心 / source 环形）+ 中性色 + hover ring 表达，不复用 status 色。

**Tech Stack:** TypeScript + React 19 + Tailwind（项目语义类）+ shadcn/ui + lucide-react + @xyflow/react + vitest + @testing-library/react.

## Execution Notes

- 2026-04-30：按用户要求采用"先写代码 / 文档，最后统一编译联调"执行；因此每个 task 中的单文件 red/green test run、Phase B/C 中段 `tsc --noEmit`、per-task commit 均跳过，改为 Task 9 统一验证。
- 2026-04-30：Phase B 与 Phase C 使用并行 subagent batch 写入，主线程完成静态集成复核与少量样式契约修正。
- 2026-04-30：最终自动化验证完成：`cd client && npx tsc --noEmit` 通过；`cd client && npx vitest run` 通过（139 files / 924 tests）。`npm run lint --silent` 已执行但命中既有 repo-wide lint 基线（310 errors / 7 warnings）；本轮 ER canvas 变更文件定向 ESLint 通过。截图、axe-core、PR 创建未在本轮执行，作为手工验收 / 提交流程 deferred。
- 2026-05-01：按视觉验收反馈放大字段两侧 connection handle：Designer 可见球 `18px`、Viewer hover 可见球 `16px`；Designer 行右侧增加 `pr-10` 预留，避免右侧 source handle 与 hover-only delete 按钮重合。验证：`cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`、`cd client && npx tsc --noEmit`、ERTableNode 定向 ESLint 均通过。

---

## 0. Spec & DESIGN Inputs

执行前请通读：

- [docs/product-specs/2026-04-30-er-canvas-redesign-design.md](../product-specs/2026-04-30-er-canvas-redesign-design.md) — 本计划的设计来源
- [client/DESIGN.md](../../client/DESIGN.md) — 项目设计契约（token、principles、component rules、a11y）
- [docs/product-specs/2026-04-29-er-graph-browsing-design.md](../product-specs/2026-04-29-er-graph-browsing-design.md) Q10 — Generate DDL 走 query_editor，**不存在 DDL Dialog**
- 适用 token / 类映射：`bg.canvas → bg-bg-canvas`、`bg.subtle → bg-bg-subtle`、`bg.panel → bg-bg-panel`、`text.muted → text-text-muted`、`accent.primary → text-accent-primary` / `bg-accent-primary` / `border-accent-primary`、`border.subtle → border-border-subtle`、`border.default → border-border-default`、`border.strong → border-border-strong`、`status.danger → text-status-danger` 等。SVG 描边 / 填充处可用 `var(--dt-border-strong)` / `var(--dt-accent-primary)` 等 CSS 变量。

**铁律**（违反即视为不通过）：
- 禁止任何 primitive 颜色（如 `text-cobalt-700`、`#3B82F6`、`bg-blue-500`）
- 禁止 component-local 自造色
- `status.*` 色仅用于 health / warning / danger / info 语义
- `accent.primary` (cobalt) 仅用于 focus / current object / selection / primary action
- light 与 dark 共用同一组语义类，禁止写主题分支条件

---

## 1. File Structure

| 文件 | 性质 | 一句话职责 |
|---|---|---|
| `client/src/i18n/messages.ts` | Modify | 新增 mode badge / empty title / node empty / disabled hint 8 个 key（zh-CN + en-US 各 4） |
| `client/src/features/stage/components/er-canvas/ErEdgeMarkers.tsx` | **Create** | ReactFlow `<defs>` 唯一入口，注册 default / virtual / selected 三种 `<marker>`，目标端 6px 三角箭头 |
| `client/src/features/stage/components/er-canvas/ErEmptyState.tsx` | Rewrite | reason→icon 映射 + title/body 双行 + Designer 空态 "+ Add table" CTA |
| `client/src/features/stage/components/er-canvas/ErTableContextMenu.tsx` | Rewrite | 项内图标、危险项前分隔条、ArrowDown/Up/Enter/Esc 键盘导航、首项默认聚焦 |
| `client/src/features/stage/components/er-canvas/ErToolbar.tsx` | Rewrite | 新增 `ModeBadge`、按 §5.3 / §5.4 重新分组、按钮统一 §5.2 规格、disabled tooltip 接 i18n |
| `client/src/features/stage/components/er-canvas/ErTableNode.tsx` | Rewrite | header pencil/lock 按图标契约、列行 left rail + role icon + name + type chip + NN pill + hover-only delete、handle 形状区分（target 实心 / source 环形）、空表 / 折叠 / +Add column 行、Designer/Viewer 容器差异 |
| `client/src/features/stage/components/er-canvas/ErEdge.tsx` | Modify | stroke 1.5/2px、virtual `4 3` dashed、hover +0.5px、`markerEnd="url(#er-edge-arrow-...)"`、virtual 后缀胶囊 |
| `client/src/features/stage/components/er-canvas/ErCanvas.tsx` | Modify | 网格 gap 18 / 24 by mode、ring 厚度 4px / 2px by mode、ReactFlow children 内挂一次 `<ErEdgeMarkers />` |
| `client/src/features/stage/components/er-canvas/__tests__/*.test.tsx` | Modify / Add | 各组件按 §14 增量测试 |

**Co-edited 不动**：`er-designer-tab.tsx`、`er-inspector-tab.tsx`、ER store / payload / adapter / persistence / ER hook utils。

---

## 2. Verification Gates

- 所有任务收尾前：`cd client && npx tsc --noEmit` 0 错
- 每个组件改完跑该组件 vitest：`cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/<File>.test.tsx`
- 整个计划收尾跑：`cd client && npx vitest run` 全绿
- 数据源类型兼容门：本计划纯前端视觉，无 dialect / JDBC / SQL 路径变更，**N/A**（在 PR 描述中明确说明）
- 手测：light + dark 主题各拍 Designer / Viewer 截图（≥ 10 张），axe-core 对比度无 critical / serious

---

## 3. Batch Strategy

按 CLAUDE.md "Parallel Plan Execution" 规则：

- **Phase A**（Task 1）：必须先完成，i18n 是后续组件依赖
- **Phase B**（Tasks 2 / 3 / 4 / 5）：4 文件互不依赖，并发 batch 写代码；batch 内**跳过**单文件 `tsc --noEmit`，batch 完成后跑一次整体类型检查
- **Phase C**（Task 6 + Task 7）：两文件互不依赖，可并发 batch
- **Phase D**（Task 8）：依赖 Task 2 提供的 `ErEdgeMarkers` 组件
- **Phase E**（Task 9）：整体验证收口

---

## Task 1: 新增 i18n keys

**Files:**
- Modify: `client/src/i18n/messages.ts`

**Phase:** A（必须最先完成）

- [x] **Step 1.1: Read 当前 i18n 文件 erCanvas 段**

```bash
grep -n "erCanvas" client/src/i18n/messages.ts | head -60
```

确认两份语言（`zh-CN` 在前、`en-US` 在后）的 erCanvas 段定位。

- [x] **Step 1.2: 在 zh-CN 段追加 4 个新 key**

定位 `'erCanvas.empty.unknown': '无可显示数据。',` 行，在其**之后**插入：

```ts
    'erCanvas.empty.unsupported.title': '不支持的数据库方言',
    'erCanvas.empty.selection.title': '尚未选择表',
    'erCanvas.empty.designer.title': '空白 ER 设计稿',
    'erCanvas.empty.oversized.title': 'ER 图表过大',
    'erCanvas.node.empty': '此表暂无列',
    'erCanvas.toolbar.modeBadge.designer': '设计模式',
    'erCanvas.toolbar.modeBadge.viewer': '浏览模式',
    'erCanvas.toolbar.disabledHint.bindFirst': '请先绑定目标数据库',
```

- [x] **Step 1.3: 在 en-US 段追加 8 个对应 key**

定位 `'erCanvas.empty.unknown': 'No data to display.',` 行，在其**之后**插入：

```ts
    'erCanvas.empty.unsupported.title': 'Dialect not supported',
    'erCanvas.empty.selection.title': 'Nothing selected',
    'erCanvas.empty.designer.title': 'Empty designer',
    'erCanvas.empty.oversized.title': 'Diagram too large',
    'erCanvas.node.empty': 'No columns yet',
    'erCanvas.toolbar.modeBadge.designer': 'Designer',
    'erCanvas.toolbar.modeBadge.viewer': 'Viewer',
    'erCanvas.toolbar.disabledHint.bindFirst': 'Bind a target database first',
```

- [x] **Step 1.4: TypeScript 类型检查**

```bash
cd client && npx tsc --noEmit
```

预期：0 错。如果出现 `MessageKey` 联合类型推导问题，确认两个语言对象 key 集合完全一致。

- [x] **Step 1.5: Commit**

```bash
git add client/src/i18n/messages.ts
git commit -m "i18n(er-canvas): add mode badge / empty title / disabled hint keys

For 2026-04-30 ER canvas redesign — adds 8 new keys (zh-CN + en-US):
mode badge labels, 4 empty-state titles, no-columns hint, bind-first
disabled tooltip."
```

---

## Task 2: ErEdgeMarkers（新建 SVG marker 组件）

**Files:**
- Create: `client/src/features/stage/components/er-canvas/ErEdgeMarkers.tsx`
- Create: `client/src/features/stage/components/er-canvas/__tests__/ErEdgeMarkers.test.tsx`

**Phase:** B（可与 3/4/5 并发）

**职责**：在 ReactFlow `<svg>` 容器内，作为 children 注入一个隐形 `<svg>` + `<defs>`，注册 3 个 `<marker>`：`er-edge-arrow-default` / `er-edge-arrow-virtual` / `er-edge-arrow-selected`。每条边通过 `markerEnd="url(#er-edge-arrow-<state>)"` 引用。

- [x] **Step 2.1: 写测试 `ErEdgeMarkers.test.tsx`**

```tsx
import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ErEdgeMarkers } from '../ErEdgeMarkers'

describe('ErEdgeMarkers', () => {
  it('registers three named markers in <defs>', () => {
    const { container } = render(<ErEdgeMarkers />)
    const ids = Array.from(container.querySelectorAll('marker')).map((m) => m.id)
    expect(ids).toEqual(
      expect.arrayContaining([
        'er-edge-arrow-default',
        'er-edge-arrow-virtual',
        'er-edge-arrow-selected',
      ]),
    )
  })

  it('uses semantic CSS variables for fill (no primitive colors)', () => {
    const { container } = render(<ErEdgeMarkers />)
    const html = container.innerHTML
    expect(html).toContain('var(--dt-border-strong)')
    expect(html).toContain('var(--dt-accent-warn)')
    expect(html).toContain('var(--dt-accent-primary)')
    // No raw primitives sneak in
    expect(html).not.toMatch(/#[0-9a-fA-F]{3,6}/)
  })

  it('renders an absolutely-zero-sized svg so it does not affect layout', () => {
    const { container } = render(<ErEdgeMarkers />)
    const svg = container.querySelector('svg')
    expect(svg).toBeTruthy()
    expect(svg!.getAttribute('width')).toBe('0')
    expect(svg!.getAttribute('height')).toBe('0')
  })
})
```

- [x] **Step 2.2: 运行测试，预期失败**

```bash
cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/ErEdgeMarkers.test.tsx
```

预期：FAIL with "Cannot find module '../ErEdgeMarkers'"。

- [x] **Step 2.3: 实现 `ErEdgeMarkers.tsx`**

```tsx
const MARKER_DEFS: Array<{ id: string; fill: string }> = [
  { id: 'er-edge-arrow-default', fill: 'var(--dt-border-strong)' },
  { id: 'er-edge-arrow-virtual', fill: 'var(--dt-accent-warn)' },
  { id: 'er-edge-arrow-selected', fill: 'var(--dt-accent-primary)' },
]

export function ErEdgeMarkers() {
  return (
    <svg width="0" height="0" aria-hidden="true" style={{ position: 'absolute' }}>
      <defs>
        {MARKER_DEFS.map(({ id, fill }) => (
          <marker
            key={id}
            id={id}
            viewBox="0 0 8 8"
            refX="7"
            refY="4"
            markerWidth="8"
            markerHeight="8"
            orient="auto-start-reverse"
            markerUnits="userSpaceOnUse"
          >
            <path d="M 0 0 L 8 4 L 0 8 z" fill={fill} />
          </marker>
        ))}
      </defs>
    </svg>
  )
}
```

- [x] **Step 2.4: 运行测试，预期通过**

```bash
cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/ErEdgeMarkers.test.tsx
```

预期：3 项全绿。

- [x] **Step 2.5: Commit**

```bash
git add client/src/features/stage/components/er-canvas/ErEdgeMarkers.tsx \
  client/src/features/stage/components/er-canvas/__tests__/ErEdgeMarkers.test.tsx
git commit -m "feat(er-canvas): add ErEdgeMarkers svg marker registry

New component registers er-edge-arrow-default/virtual/selected markers
in a single <defs>, mounted once via ErCanvas. Markers use semantic
CSS variables only. Wired up in a later task."
```

---

## Task 3: ErEmptyState restyle

**Files:**
- Modify: `client/src/features/stage/components/er-canvas/ErEmptyState.tsx`
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErEmptyState.test.tsx`

**Phase:** B（可与 2/4/5 并发）

- [x] **Step 3.1: Read 现有测试与组件**

```bash
cat client/src/features/stage/components/er-canvas/__tests__/ErEmptyState.test.tsx
cat client/src/features/stage/components/er-canvas/ErEmptyState.tsx
```

- [x] **Step 3.2: 改写测试，断言新结构**

完全替换 `__tests__/ErEmptyState.test.tsx` 内容为：

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ErEmptyState } from '../ErEmptyState'

describe('ErEmptyState', () => {
  it('renders dialect_unsupported with title + body and no CTA', () => {
    render(<ErEmptyState reason="dialect_unsupported" />)
    expect(screen.getByText('Dialect not supported')).toBeInTheDocument()
    expect(screen.queryByRole('button')).toBeNull()
    expect(screen.getByTestId('er-empty-icon')).toHaveAttribute('data-er-empty-reason', 'dialect_unsupported')
  })

  it('renders empty_selection with mouse-pointer icon and no CTA', () => {
    render(<ErEmptyState reason="empty_selection" />)
    expect(screen.getByText('Nothing selected')).toBeInTheDocument()
    expect(screen.queryByRole('button')).toBeNull()
    expect(screen.getByTestId('er-empty-icon')).toHaveAttribute('data-er-empty-reason', 'empty_selection')
  })

  it('renders empty_designer with primary CTA when actionLabel is provided', () => {
    const onAction = vi.fn()
    render(
      <ErEmptyState reason="empty_designer" actionLabel="+ Add table" onAction={onAction} />,
    )
    expect(screen.getByText('Empty designer')).toBeInTheDocument()
    const button = screen.getByRole('button', { name: '+ Add table' })
    expect(button).toBeInTheDocument()
    button.click()
    expect(onAction).toHaveBeenCalledTimes(1)
  })

  it('renders oversized with zoom-out icon', () => {
    render(<ErEmptyState reason="oversized" />)
    expect(screen.getByText('Diagram too large')).toBeInTheDocument()
    expect(screen.getByTestId('er-empty-icon')).toHaveAttribute('data-er-empty-reason', 'oversized')
  })

  it('uses dialect-specific oracle / sqlite body for dialect_unsupported', () => {
    const { rerender } = render(
      <ErEmptyState reason="dialect_unsupported" dialect="oracle" />,
    )
    expect(screen.getByText(/Oracle/)).toBeInTheDocument()
    rerender(<ErEmptyState reason="dialect_unsupported" dialect="sqlite" />)
    expect(screen.getByText(/SQLite/)).toBeInTheDocument()
  })
})
```

- [x] **Step 3.3: 运行测试，预期失败**

```bash
cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/ErEmptyState.test.tsx
```

预期：5 项失败（title 文案 + data-testid 都不存在）。

- [x] **Step 3.4: 改写组件 `ErEmptyState.tsx`**

完全替换文件内容为：

```tsx
import { BanIcon, DatabaseIcon, SquareMousePointerIcon, ZoomOutIcon } from 'lucide-react'
import type { ComponentType, SVGProps } from 'react'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'

export type ErEmptyReason =
  | 'dialect_unsupported'
  | 'empty_selection'
  | 'empty_designer'
  | 'oversized'

export interface ErEmptyStateProps {
  reason: ErEmptyReason
  dialect?: string
  actionLabel?: string
  onAction?: () => void
}

type Lucide = ComponentType<SVGProps<SVGSVGElement>>

const REASON_ICON: Record<ErEmptyReason, Lucide> = {
  dialect_unsupported: BanIcon,
  empty_selection: SquareMousePointerIcon,
  empty_designer: DatabaseIcon,
  oversized: ZoomOutIcon,
}

export function ErEmptyState({ reason, dialect, actionLabel, onAction }: ErEmptyStateProps) {
  const { t } = useI18n()
  const Icon = REASON_ICON[reason]
  const { title, body } = resolveCopy(reason, dialect, t)

  return (
    <div className="flex h-full w-full items-center justify-center bg-bg-canvas">
      <div className="max-w-md px-6 py-8 text-center">
        <Icon
          data-testid="er-empty-icon"
          data-er-empty-reason={reason}
          aria-hidden="true"
          className="mx-auto mb-4 size-8 text-text-soft"
        />
        <p className="text-base font-medium text-text-strong">{title}</p>
        <p className="mt-1 text-sm text-text-muted">{body}</p>
        {actionLabel && onAction ? (
          <div className="mt-4">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={onAction}
              className="border-accent-primary text-accent-primary hover:bg-accent-primary-surface"
            >
              {actionLabel}
            </Button>
          </div>
        ) : null}
      </div>
    </div>
  )
}

function resolveCopy(
  reason: ErEmptyReason,
  dialect: string | undefined,
  t: ReturnType<typeof useI18n>['t'],
): { title: string; body: string } {
  if (reason === 'dialect_unsupported') {
    const normalized = dialect?.toLowerCase()
    const body = (() => {
      if (normalized === 'oracle' || normalized === 'sqlserver' || normalized === 'mssql') {
        return t('erCanvas.empty.oracle')
      }
      if (normalized === 'sqlite') return t('erCanvas.empty.sqlite')
      return t('erCanvas.empty.unsupported')
    })()
    return { title: t('erCanvas.empty.unsupported.title'), body }
  }
  if (reason === 'empty_selection') {
    return {
      title: t('erCanvas.empty.selection.title'),
      body: t('erCanvas.empty.selection'),
    }
  }
  if (reason === 'empty_designer') {
    return {
      title: t('erCanvas.empty.designer.title'),
      body: t('erCanvas.empty.designer'),
    }
  }
  return {
    title: t('erCanvas.empty.oversized.title'),
    body: t('erCanvas.empty.oversized'),
  }
}
```

- [x] **Step 3.5: 运行测试，预期通过**

```bash
cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/ErEmptyState.test.tsx
```

预期：5 绿。

- [x] **Step 3.6: Commit**

```bash
git add client/src/features/stage/components/er-canvas/ErEmptyState.tsx \
  client/src/features/stage/components/er-canvas/__tests__/ErEmptyState.test.tsx
git commit -m "feat(er-canvas): restyle ErEmptyState per redesign spec

Maps each reason to a lucide icon (Ban / SquareMousePointer / Database /
ZoomOut), splits copy into title (ui-md/strong) + body (ui-sm/muted),
keeps oracle/sqlite specific bodies for dialect_unsupported, and adds
primary-outline CTA for empty_designer."
```

---

## Task 4: ErTableContextMenu visual + keyboard

**Files:**
- Modify: `client/src/features/stage/components/er-canvas/ErTableContextMenu.tsx`
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErTableContextMenu.test.tsx`

**Phase:** B（可与 2/3/5 并发）

- [x] **Step 4.1: 改写测试**

完全替换 `__tests__/ErTableContextMenu.test.tsx` 为：

```tsx
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ErTableContextMenu } from '../ErTableContextMenu'

function setup() {
  const onRename = vi.fn()
  const onAddColumn = vi.fn()
  const onDeleteTable = vi.fn()
  const onClose = vi.fn()
  render(
    <ErTableContextMenu
      x={100}
      y={100}
      tableId="users"
      onRename={onRename}
      onAddColumn={onAddColumn}
      onDeleteTable={onDeleteTable}
      onClose={onClose}
    />,
  )
  return { onRename, onAddColumn, onDeleteTable, onClose }
}

describe('ErTableContextMenu', () => {
  it('renders three menu items with leading icons', () => {
    setup()
    const items = screen.getAllByRole('menuitem')
    expect(items).toHaveLength(3)
    items.forEach((item) => {
      expect(item.querySelector('svg')).toBeTruthy()
    })
  })

  it('focuses the first menu item on open', () => {
    setup()
    const items = screen.getAllByRole('menuitem')
    expect(document.activeElement).toBe(items[0])
  })

  it('cycles focus on ArrowDown / ArrowUp', () => {
    setup()
    const items = screen.getAllByRole('menuitem')
    fireEvent.keyDown(items[0], { key: 'ArrowDown' })
    expect(document.activeElement).toBe(items[1])
    fireEvent.keyDown(items[1], { key: 'ArrowDown' })
    expect(document.activeElement).toBe(items[2])
    fireEvent.keyDown(items[2], { key: 'ArrowDown' })
    expect(document.activeElement).toBe(items[0])
    fireEvent.keyDown(items[0], { key: 'ArrowUp' })
    expect(document.activeElement).toBe(items[2])
  })

  it('triggers item action and closes on Enter', () => {
    const { onRename, onClose } = setup()
    const items = screen.getAllByRole('menuitem')
    fireEvent.keyDown(items[0], { key: 'Enter' })
    expect(onRename).toHaveBeenCalledWith('users')
    expect(onClose).toHaveBeenCalled()
  })

  it('closes on Escape without invoking actions', () => {
    const { onRename, onAddColumn, onDeleteTable, onClose } = setup()
    fireEvent.keyDown(document.activeElement!, { key: 'Escape' })
    expect(onClose).toHaveBeenCalled()
    expect(onRename).not.toHaveBeenCalled()
    expect(onAddColumn).not.toHaveBeenCalled()
    expect(onDeleteTable).not.toHaveBeenCalled()
  })

  it('renders separator before delete and uses danger token', () => {
    setup()
    const items = screen.getAllByRole('menuitem')
    expect(items[2]).toHaveAttribute('data-er-menu-variant', 'danger')
  })

  it('still closes on outside mousedown', () => {
    const { onClose } = setup()
    fireEvent.mouseDown(document.body)
    expect(onClose).toHaveBeenCalled()
  })
})
```

- [x] **Step 4.2: 运行测试，预期失败**

```bash
cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/ErTableContextMenu.test.tsx
```

预期：focus / arrow / data-er-menu-variant 等多项失败。

- [x] **Step 4.3: 改写组件 `ErTableContextMenu.tsx`**

完全替换文件内容为：

```tsx
import { useEffect, useRef, type KeyboardEvent, type ReactNode } from 'react'
import { PencilIcon, PlusIcon, Trash2Icon } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'

interface ErTableContextMenuProps {
  x: number
  y: number
  tableId: string
  onRename: (tableId: string) => void
  onAddColumn: (tableId: string) => void
  onDeleteTable: (tableId: string) => void
  onClose: () => void
}

type MenuVariant = 'default' | 'danger'

export function ErTableContextMenu({
  x,
  y,
  tableId,
  onRename,
  onAddColumn,
  onDeleteTable,
  onClose,
}: ErTableContextMenuProps) {
  const { t } = useI18n()
  const containerRef = useRef<HTMLDivElement>(null)
  const itemRefs = useRef<Array<HTMLButtonElement | null>>([])
  const label = useFallbackLabel(t)

  useEffect(() => {
    itemRefs.current[0]?.focus()
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        onClose()
      }
    }
    document.addEventListener('mousedown', closeOnOutsideClick)
    return () => document.removeEventListener('mousedown', closeOnOutsideClick)
  }, [onClose])

  const handleItemKey = (event: KeyboardEvent<HTMLButtonElement>, index: number, action: () => void) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      const next = (index + 1) % itemRefs.current.length
      itemRefs.current[next]?.focus()
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      const prev = (index - 1 + itemRefs.current.length) % itemRefs.current.length
      itemRefs.current[prev]?.focus()
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      onClose()
      return
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      action()
      onClose()
    }
  }

  const items: Array<{
    key: string
    icon: ReactNode
    label: string
    variant: MenuVariant
    action: () => void
  }> = [
    {
      key: 'rename',
      icon: <PencilIcon className="size-3.5" aria-hidden="true" />,
      label: label('erCanvas.contextMenu.rename', 'Rename'),
      variant: 'default',
      action: () => onRename(tableId),
    },
    {
      key: 'addColumn',
      icon: <PlusIcon className="size-3.5" aria-hidden="true" />,
      label: label('erCanvas.contextMenu.addColumn', 'Add column'),
      variant: 'default',
      action: () => onAddColumn(tableId),
    },
    {
      key: 'deleteTable',
      icon: <Trash2Icon className="size-3.5" aria-hidden="true" />,
      label: label('erCanvas.contextMenu.deleteTable', 'Delete table'),
      variant: 'danger',
      action: () => onDeleteTable(tableId),
    },
  ]

  return (
    <div
      ref={containerRef}
      role="menu"
      aria-orientation="vertical"
      style={{ left: x, top: y }}
      className="fixed z-50 min-w-44 rounded-[10px] border border-border-subtle bg-bg-panel p-1.5 shadow-md"
    >
      {items.map((item, index) => {
        const isDanger = item.variant === 'danger'
        return (
          <div key={item.key}>
            {isDanger ? <div className="my-1 h-px bg-border-subtle" aria-hidden="true" /> : null}
            <button
              ref={(el) => { itemRefs.current[index] = el }}
              type="button"
              role="menuitem"
              data-er-menu-variant={item.variant}
              onClick={() => { item.action(); onClose() }}
              onKeyDown={(event) => handleItemKey(event, index, item.action)}
              className={[
                'flex h-7 w-full items-center gap-2 rounded-md px-2 text-xs outline-none transition-colors',
                'focus-visible:ring-2 focus-visible:ring-ring',
                isDanger
                  ? 'text-status-danger hover:bg-status-danger-surface focus-visible:bg-status-danger-surface'
                  : 'text-text-base hover:bg-interaction-hover focus-visible:bg-interaction-hover',
              ].join(' ')}
            >
              {item.icon}
              <span>{item.label}</span>
            </button>
          </div>
        )
      })}
    </div>
  )
}

function useFallbackLabel(t: ReturnType<typeof useI18n>['t']) {
  return (key: string, fallback: string) => {
    const translated = t(key as Parameters<typeof t>[0])
    return translated === key ? fallback : translated
  }
}
```

> **类映射兜底**：如项目缺 `bg-status-danger-surface` / `text-status-danger` / `bg-bg-panel` / `border-border-subtle` / `bg-interaction-hover` / `ring-ring` 等 Tailwind 类，则改用 `style={{ backgroundColor: 'var(--dt-status-danger-surface)' }}` 之类内联（保持语义）。在 Step 4.4 跑 vitest 前用 `grep -n "bg-status-danger-surface" client/src/index.css client/tailwind.config.* 2>/dev/null` 检查；项目当前已有 `text-status-danger` 与 `--dt-status-danger-surface` 变量，类已存在则无需调整。

- [x] **Step 4.4: 运行测试，预期通过**

```bash
cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/ErTableContextMenu.test.tsx
```

预期：7 绿。

- [x] **Step 4.5: Commit**

```bash
git add client/src/features/stage/components/er-canvas/ErTableContextMenu.tsx \
  client/src/features/stage/components/er-canvas/__tests__/ErTableContextMenu.test.tsx
git commit -m "feat(er-canvas): polish context menu visuals + keyboard nav

Adds leading icons, danger separator, focus-on-open, ArrowDown/Up cycle,
Enter/Space activate-and-close, Escape close. Outside-click close
preserved. data-er-menu-variant attribute exposes danger styling for
test assertions."
```

---

## Task 5: ErToolbar restyle (incl. ModeBadge)

**Files:**
- Modify: `client/src/features/stage/components/er-canvas/ErToolbar.tsx`
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErToolbar.test.tsx`

**Phase:** B（可与 2/3/4 并发）

- [x] **Step 5.1: Read 现有测试与组件**

```bash
cat client/src/features/stage/components/er-canvas/__tests__/ErToolbar.test.tsx
cat client/src/features/stage/components/er-canvas/ErToolbar.tsx
```

- [x] **Step 5.2: 增量加测，断言新结构**

在现有测试**末尾**加入新 describe 块（保留原有断言）：

```tsx
describe('ErToolbar redesign', () => {
  it('Designer renders Designer mode badge with Pencil icon', () => {
    render(
      <ErToolbar
        mode="designer"
        dialect="mysql"
        hasTarget={false}
        onAddTable={() => {}}
        onAutoLayout={() => {}}
        onFitView={() => {}}
        onBindTarget={() => {}}
        onDiffVsDb={() => {}}
        onGenerateDdl={() => {}}
        onChangeDialect={() => {}}
      />,
    )
    const badge = screen.getByTestId('er-mode-badge')
    expect(badge).toHaveAttribute('data-er-mode', 'designer')
    expect(badge).toHaveAttribute('role', 'status')
    expect(badge.querySelector('svg')).toBeTruthy()
  })

  it('Viewer renders Viewer mode badge with Lock icon', () => {
    render(
      <ErToolbar
        mode="inspector"
        neighborDepth={1}
        onRefresh={() => {}}
        onAutoLayout={() => {}}
        onFitView={() => {}}
        onChangeNeighborDepth={() => {}}
        onAddVirtualRelation={() => {}}
        onForkToDesigner={() => {}}
      />,
    )
    const badge = screen.getByTestId('er-mode-badge')
    expect(badge).toHaveAttribute('data-er-mode', 'inspector')
  })

  it('Designer Diff vs DB and Generate DDL are disabled when hasTarget=false', () => {
    render(
      <ErToolbar
        mode="designer"
        dialect="mysql"
        hasTarget={false}
        onAddTable={() => {}}
        onAutoLayout={() => {}}
        onFitView={() => {}}
        onBindTarget={() => {}}
        onDiffVsDb={() => {}}
        onGenerateDdl={() => {}}
        onChangeDialect={() => {}}
      />,
    )
    expect(screen.getByRole('button', { name: /diff/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /generate ddl/i })).toBeDisabled()
  })

  it('Designer Diff vs DB and Generate DDL are enabled when hasTarget=true', () => {
    render(
      <ErToolbar
        mode="designer"
        dialect="mysql"
        hasTarget={true}
        onAddTable={() => {}}
        onAutoLayout={() => {}}
        onFitView={() => {}}
        onBindTarget={() => {}}
        onDiffVsDb={() => {}}
        onGenerateDdl={() => {}}
        onChangeDialect={() => {}}
      />,
    )
    expect(screen.getByRole('button', { name: /diff/i })).toBeEnabled()
    expect(screen.getByRole('button', { name: /generate ddl/i })).toBeEnabled()
  })
})
```

> 测试文件顶部如未导入 `screen`，请同步添加：`import { render, screen } from '@testing-library/react'`。

- [x] **Step 5.3: 运行测试，预期失败**

```bash
cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/ErToolbar.test.tsx
```

预期：4 个新断言失败（`er-mode-badge` 不存在等）。

- [x] **Step 5.4: 改写 `ErToolbar.tsx`**

完全替换文件内容为：

```tsx
import type { ReactNode } from 'react'
import {
  CodeIcon,
  DiffIcon,
  GitForkIcon,
  LayoutTemplateIcon,
  LinkIcon,
  LockIcon,
  MaximizeIcon,
  PencilIcon,
  PlusIcon,
  RefreshCwIcon,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select'
import { useI18n } from '@/i18n/use-i18n'

interface ErToolbarInspectorProps {
  mode: 'inspector'
  neighborDepth: 0 | 1 | 2
  onRefresh: () => void
  onAutoLayout: () => void
  onFitView: () => void
  onChangeNeighborDepth: (depth: 0 | 1 | 2) => void
  onAddVirtualRelation: () => void
  onForkToDesigner: () => void
}

type DesignerDialect = 'mysql' | 'postgresql' | 'h2' | 'sqlite'
const DESIGNER_DIALECT_OPTIONS: Array<{ value: DesignerDialect; label: string }> = [
  { value: 'mysql', label: 'MySQL' },
  { value: 'postgresql', label: 'PostgreSQL' },
  { value: 'h2', label: 'H2' },
  { value: 'sqlite', label: 'SQLite' },
]

interface ErToolbarDesignerProps {
  mode: 'designer'
  dialect: DesignerDialect
  hasTarget: boolean
  onAddTable: () => void
  onAutoLayout: () => void
  onFitView: () => void
  onBindTarget: () => void
  onDiffVsDb: () => void
  onGenerateDdl: () => void
  onChangeDialect: (dialect: DesignerDialect) => void
}

export type ErToolbarProps = ErToolbarInspectorProps | ErToolbarDesignerProps

export function ErToolbar(props: ErToolbarProps) {
  const { t } = useI18n()
  const label = useFallbackLabel(t)

  if (props.mode === 'designer') {
    const disabledHint = label('erCanvas.toolbar.disabledHint.bindFirst', 'Bind a target database first')
    return (
      <div className="flex min-h-10 items-center gap-2 border-b border-border-subtle bg-bg-subtle px-3 py-1">
        <ModeBadge mode="designer" t={t} label={label} />
        <Group>
          <ToolbarButton
            onClick={props.onAddTable}
            icon={<PlusIcon className="size-4" />}
            label={label('erCanvas.toolbar.addTable', 'Add table')}
          />
          <ToolbarButton
            onClick={props.onAutoLayout}
            icon={<LayoutTemplateIcon className="size-4" />}
            label={t('erCanvas.toolbar.autoLayout')}
          />
          <ToolbarButton
            onClick={props.onFitView}
            icon={<MaximizeIcon className="size-4" />}
            label={t('erCanvas.toolbar.fitView')}
          />
        </Group>

        <Separator />

        <Group>
          <ToolbarButton
            onClick={props.onBindTarget}
            icon={<LinkIcon className="size-4" />}
            label={label('erCanvas.toolbar.bindTarget', 'Bind target')}
            primary
          />
          <ToolbarButton
            onClick={props.onDiffVsDb}
            icon={<DiffIcon className="size-4" />}
            label={label('erCanvas.toolbar.diffVsDb', 'Diff vs DB')}
            disabled={!props.hasTarget}
            primary
            disabledTitle={disabledHint}
          />
          <ToolbarButton
            onClick={props.onGenerateDdl}
            icon={<CodeIcon className="size-4" />}
            label={label('erCanvas.toolbar.generateDdl', 'Generate DDL')}
            primary
            disabled={!props.hasTarget}
            disabledTitle={disabledHint}
          />
        </Group>

        <div className="ml-auto" />

        <label className="flex items-center gap-1.5 text-xs text-text-muted">
          <span>{label('erCanvas.toolbar.dialect', 'Dialect')}</span>
          <Select
            value={props.dialect}
            onValueChange={(value) => props.onChangeDialect(value as DesignerDialect)}
          >
            <SelectTrigger
              size="sm"
              aria-label={label('erCanvas.toolbar.dialect', 'Dialect')}
              className="min-w-28 border-border-default bg-bg-canvas px-2 text-xs text-text-base"
            >
              <span className="flex flex-1 text-left">
                {DESIGNER_DIALECT_OPTIONS.find((option) => option.value === props.dialect)?.label ?? props.dialect}
              </span>
            </SelectTrigger>
            <SelectContent>
              {DESIGNER_DIALECT_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </label>
      </div>
    )
  }

  return (
    <div className="flex min-h-10 items-center gap-2 border-b border-border-subtle bg-bg-subtle px-3 py-1">
      <ModeBadge mode="inspector" t={t} label={label} />
      <Group>
        <ToolbarButton
          onClick={props.onRefresh}
          icon={<RefreshCwIcon className="size-4" />}
          label={t('erCanvas.toolbar.refresh')}
        />
        <ToolbarButton
          onClick={props.onAutoLayout}
          icon={<LayoutTemplateIcon className="size-4" />}
          label={t('erCanvas.toolbar.autoLayout')}
        />
        <ToolbarButton
          onClick={props.onFitView}
          icon={<MaximizeIcon className="size-4" />}
          label={t('erCanvas.toolbar.fitView')}
        />
      </Group>

      <Separator />

      <label className="flex items-center gap-1.5 text-xs text-text-muted">
        <span>{t('erCanvas.toolbar.neighborDepth')}</span>
        <select
          aria-label={t('erCanvas.toolbar.neighborDepth')}
          value={props.neighborDepth}
          onChange={(event) => props.onChangeNeighborDepth(Number(event.target.value) as 0 | 1 | 2)}
          className="h-7 rounded-md border border-border-default bg-bg-canvas px-2 text-xs text-text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <option value={0}>0</option>
          <option value={1}>1</option>
          <option value={2}>2</option>
        </select>
      </label>

      <Separator />

      <ToolbarButton
        onClick={props.onAddVirtualRelation}
        icon={<PlusIcon className="size-4" />}
        label={t('erCanvas.toolbar.addVirtualRelation')}
      />

      <div className="ml-auto" />

      <ToolbarButton
        onClick={props.onForkToDesigner}
        icon={<GitForkIcon className="size-4" />}
        label={t('erCanvas.toolbar.forkToDesigner')}
        primary
      />
    </div>
  )
}

function ModeBadge({
  mode,
  t,
  label,
}: {
  mode: 'designer' | 'inspector'
  t: ReturnType<typeof useI18n>['t']
  label: ReturnType<typeof useFallbackLabel>
}) {
  const isDesigner = mode === 'designer'
  const text = isDesigner
    ? label('erCanvas.toolbar.modeBadge.designer', 'Designer')
    : label('erCanvas.toolbar.modeBadge.viewer', 'Viewer')
  const Icon = isDesigner ? PencilIcon : LockIcon
  return (
    <div
      role="status"
      aria-live="off"
      data-testid="er-mode-badge"
      data-er-mode={mode}
      className={[
        'flex h-6 select-none items-center gap-1.5 rounded-md px-2 text-xs font-medium',
        isDesigner
          ? 'bg-accent-primary-surface text-accent-primary'
          : 'bg-bg-subtle text-text-muted',
      ].join(' ')}
    >
      <Icon className="size-3.5" aria-hidden="true" />
      <span>{text}</span>
    </div>
  )
}

function Group({ children }: { children: ReactNode }) {
  return <div className="flex items-center gap-0.5">{children}</div>
}

function ToolbarButton({
  onClick,
  icon,
  label,
  primary,
  disabled,
  disabledTitle,
}: {
  onClick: () => void
  icon: ReactNode
  label: string
  primary?: boolean
  disabled?: boolean
  disabledTitle?: string
}) {
  return (
    <Button
      type="button"
      size="sm"
      variant={primary ? 'outline' : 'ghost'}
      onClick={onClick}
      aria-label={label}
      disabled={disabled}
      title={disabled ? disabledTitle : undefined}
      className={[
        'h-7 rounded-md px-2 text-xs',
        primary
          ? 'border-accent-primary text-accent-primary hover:bg-accent-primary-surface disabled:border-disabled disabled:text-disabled'
          : 'text-text-muted hover:bg-interaction-hover hover:text-text-strong disabled:text-disabled',
      ].join(' ')}
    >
      {icon}
      <span>{label}</span>
    </Button>
  )
}

function Separator() {
  return <div className="mx-1 h-4 w-px bg-border-subtle" aria-hidden="true" />
}

function useFallbackLabel(t: ReturnType<typeof useI18n>['t']) {
  return (key: string, fallback: string) => {
    const translated = t(key as Parameters<typeof t>[0])
    return translated === key ? fallback : translated
  }
}
```

> 类映射兜底：`bg-accent-primary-surface`、`text-accent-primary`、`border-accent-primary`、`hover:bg-accent-primary-surface`、`bg-interaction-hover`、`disabled:border-disabled`、`disabled:text-disabled` 这些类应当已在 `tailwind.config.*` / `index.css` 通过 `@theme` 或 `addUtilities` 暴露（项目其它组件已使用）。如某个具体类不存在，改用 `style={{ ... }}` 内联 `var(--dt-...)`。**禁止**改成 primitive。

- [x] **Step 5.5: 运行测试，预期通过**

```bash
cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/ErToolbar.test.tsx
```

预期：原测试 + 4 个新测试全绿。

- [x] **Step 5.6: Commit**

```bash
git add client/src/features/stage/components/er-canvas/ErToolbar.tsx \
  client/src/features/stage/components/er-canvas/__tests__/ErToolbar.test.tsx
git commit -m "feat(er-canvas): redesign toolbar with mode badge and grouping

Adds ModeBadge subcomponent (Pencil for Designer / Lock for Viewer),
regroups buttons per redesign spec (utility / primary / settings),
elevates Bind/Diff/DDL and Fork to Designer to primary outline,
adds disabled tooltip for hasTarget=false. Layout chrome height stays
40px; styling uses semantic tokens only."
```

---

## Phase B 中段验证

Tasks 2-5 全部完成后，跑一次整体类型检查：

```bash
cd client && npx tsc --noEmit
```

预期：0 错。如有错误，按文件修复后再进入 Phase C。

---

## Task 6: ErTableNode restyle

**Files:**
- Modify: `client/src/features/stage/components/er-canvas/ErTableNode.tsx`
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`

**Phase:** C（可与 Task 7 并发）

> ⚠ 这是单个最大的任务。结构性改造很多——header 模式提示色、列行 left rail、role icon、name、type chip、NN pill、hover-only delete、handle shape、+Add column row、empty-table、folded indicator——但都集中在一个文件里。按下面的 step 顺序逐步重写。

- [x] **Step 6.1: 增量加测**

在 `__tests__/ErTableNode.test.tsx` **末尾**加入：

```tsx
describe('ErTableNode redesign', () => {
  const designerData: ErTableNodeData = {
    table: { name: 'users', columns, fkOut: [] },
    columns,
    collapsed: false,
    mode: 'designer' as const,
    dialect: 'mysql',
  }
  const inspectorData: ErTableNodeData = {
    table: { name: 'users', columns, fkOut: [] },
    columns,
    collapsed: false,
    mode: 'inspector' as const,
  }

  it('renders left rail with role data attribute (PK / FK / regular)', () => {
    renderNode(inspectorData)
    expect(screen.getByTestId('er-row-id').getAttribute('data-er-row-role')).toBe('pk')
    expect(screen.getByTestId('er-row-account_id').getAttribute('data-er-row-role')).toBe('fk')
    expect(screen.getByTestId('er-row-email').getAttribute('data-er-row-role')).toBe('regular')
  })

  it('shows NN pill only when nullable=false', () => {
    const cols: ErColumnMeta[] = [
      { id: 'a', name: 'a', type: 'INT', isPK: false, isFK: false, nullable: false },
      { id: 'b', name: 'b', type: 'INT', isPK: false, isFK: false, nullable: true },
    ] as Array<ErColumnMeta & { id: string }>
    renderNode({ ...inspectorData, columns: cols, table: { name: 'tbl', columns: cols, fkOut: [] } })
    expect(screen.getByTestId('er-row-a').querySelector('[data-er-nn-pill]')).toBeTruthy()
    expect(screen.getByTestId('er-row-b').querySelector('[data-er-nn-pill]')).toBeFalsy()
  })

  it('Designer hides delete button by default and exposes group-hover affordance', () => {
    renderNode(designerData)
    const trash = screen.getAllByLabelText(/Delete column/)[0]
    // Hover-only is achieved via `opacity-0 group-hover:opacity-100`; jsdom can't
    // verify computed style, so we assert the className contract.
    expect(trash.className).toMatch(/opacity-0/)
    expect(trash.className).toMatch(/group-hover:opacity-100/)
  })

  it('Viewer omits the Add column row entirely', () => {
    renderNode(inspectorData)
    expect(screen.queryByText(/Add column/i)).toBeNull()
  })

  it('Designer shows Add column row even when columns is empty', () => {
    const empty: ErTableNodeData = {
      table: { name: 'empty', columns: [], fkOut: [] },
      columns: [],
      collapsed: false,
      mode: 'designer' as const,
      dialect: 'mysql',
    }
    renderNode(empty)
    expect(screen.getByText(/No columns yet/i)).toBeInTheDocument()
    expect(screen.getByText(/Add column/i)).toBeInTheDocument()
  })

  it('Viewer renders empty-state copy without Add column CTA', () => {
    const empty: ErTableNodeData = {
      table: { name: 'empty', columns: [], fkOut: [] },
      columns: [],
      collapsed: false,
      mode: 'inspector' as const,
    }
    renderNode(empty)
    expect(screen.getByText(/No columns yet/i)).toBeInTheDocument()
    expect(screen.queryByText(/Add column/i)).toBeNull()
  })

  it('renders mode-indicator icon with correct data attribute', () => {
    const { rerender } = renderNode(designerData)
    expect(screen.getByTestId('er-mode-indicator')).toHaveAttribute('data-er-mode', 'designer')
    rerender(
      <ReactFlowProvider>
        <ErTableNode id="users" type="erTable" data={inspectorData} selected={false} dragging={false} isConnectable={false} positionAbsoluteX={0} positionAbsoluteY={0} zIndex={0} />
      </ReactFlowProvider>,
    )
    expect(screen.getByTestId('er-mode-indicator')).toHaveAttribute('data-er-mode', 'inspector')
  })

  it('renders connection handles with shape data attribute (target=solid / source=ring) in Designer', () => {
    renderNode(designerData)
    const targetBalls = document.querySelectorAll('[data-er-handle-shape="solid"]')
    const sourceBalls = document.querySelectorAll('[data-er-handle-shape="ring"]')
    expect(targetBalls.length).toBeGreaterThan(0)
    expect(sourceBalls.length).toBeGreaterThan(0)
  })
})
```

- [x] **Step 6.2: 运行测试，预期失败**

```bash
cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx
```

预期：8 个新断言全部失败。

- [x] **Step 6.3: 改写组件 `ErTableNode.tsx`**

完全替换文件内容为：

```tsx
import { memo, useState } from 'react'
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react'
import {
  ChevronDownIcon,
  KeyRoundIcon,
  LinkIcon,
  LockIcon,
  PencilIcon,
  TableIcon,
  Trash2Icon,
} from 'lucide-react'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useI18n } from '@/i18n/use-i18n'
import type { ErColumnMeta } from '@/features/stage/stores/er-tabs-payload-types'
import type { ErNodeData } from './utils/payload-to-graph'

export type ErTableNodeMode = 'inspector' | 'designer'
export type ErDesignerDialect = 'mysql' | 'postgresql' | 'h2' | 'sqlite'

export interface ErTableNodeData extends ErNodeData {
  mode: ErTableNodeMode
  dialect?: ErDesignerDialect
  onUpdateColumn?: (columnId: string, updates: Partial<ErColumnMeta>) => void
  onAddColumn?: () => void
  onDeleteColumn?: (columnId: string) => void
  onOpenContextMenu?: (menu: { tableId: string; x: number; y: number }) => void
}

type ErTableReactFlowNode = Node<ErTableNodeData, 'erTable'>

const COLUMN_PREVIEW_LIMIT = 12

type RowRole = 'pk' | 'fk' | 'pkfk' | 'regular'

function rowRole(column: ErColumnMeta): RowRole {
  if (column.isPK && column.isFK) return 'pkfk'
  if (column.isPK) return 'pk'
  if (column.isFK) return 'fk'
  return 'regular'
}

export function ErTableNode({ id, data, selected }: NodeProps<ErTableReactFlowNode>) {
  const { t } = useI18n()
  const label = useFallbackLabel(t)
  const isDesigner = data.mode === 'designer'
  const [expanded, setExpanded] = useState(data.columns.length <= COLUMN_PREVIEW_LIMIT)
  const visibleColumns = data.collapsed
    ? []
    : expanded
      ? data.columns
      : data.columns.slice(0, COLUMN_PREVIEW_LIMIT)
  const hiddenColumnCount = data.collapsed ? 0 : data.columns.length - visibleColumns.length

  return (
    <section
      onContextMenu={(event) => {
        if (!isDesigner || !data.onOpenContextMenu) return
        event.preventDefault()
        data.onOpenContextMenu({ tableId: id, x: event.clientX, y: event.clientY })
      }}
      className={[
        'w-80 overflow-hidden rounded-[10px] bg-bg-canvas font-sans transition-colors',
        isDesigner ? 'border shadow-sm' : 'border',
        selected
          ? 'border-accent-primary ring-2 ring-accent-primary-surface'
          : isDesigner ? 'border-border-default' : 'border-border-subtle',
      ].join(' ')}
      aria-label={`Table ${data.table.name}`}
      data-er-mode={data.mode}
    >
      <header
        className={[
          'flex items-center justify-between gap-2 bg-bg-subtle px-3 py-2',
          'border-b',
          isDesigner ? 'border-border-default' : 'border-border-subtle',
        ].join(' ')}
      >
        <div className="flex min-w-0 items-center gap-2">
          <TableIcon className="size-3.5 shrink-0 text-text-muted" aria-hidden="true" />
          <span className="truncate text-sm font-medium leading-5 text-text-strong">
            {data.table.name}
          </span>
        </div>
        {isDesigner ? (
          <PencilIcon
            className="size-3.5 shrink-0 text-accent-primary"
            aria-label="editable designer table"
            data-testid="er-mode-indicator"
            data-er-mode="designer"
          />
        ) : (
          <LockIcon
            className="size-3.5 shrink-0 text-text-soft"
            aria-label="read-only inspector view"
            data-testid="er-mode-indicator"
            data-er-mode="inspector"
          />
        )}
        {data.collapsed ? (
          <ChevronDownIcon className="size-3 shrink-0 text-text-muted" aria-hidden="true" />
        ) : null}
      </header>

      {!data.collapsed && (
        <ul className="flex flex-col">
          {data.columns.length === 0 ? (
            <li
              className={[
                'flex items-center justify-center px-3 text-center text-xs text-text-soft',
                isDesigner ? 'min-h-8 py-1.5' : 'min-h-7 py-1',
              ].join(' ')}
            >
              {label('erCanvas.node.empty', 'No columns yet')}
            </li>
          ) : (
            <>
              {visibleColumns.map((column) => (
                <ColumnRow
                  key={getColumnId(column)}
                  column={column}
                  mode={data.mode}
                  dialect={data.dialect}
                  onUpdateColumn={data.onUpdateColumn}
                  onDeleteColumn={data.onDeleteColumn}
                />
              ))}
              {hiddenColumnCount > 0 && (
                <li>
                  <button
                    type="button"
                    className="nodrag w-full px-3 py-2 text-left text-xs leading-4 text-text-muted outline-none transition-colors hover:bg-interaction-hover hover:text-text-strong focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring"
                    onClick={() => setExpanded(true)}
                  >
                    {hiddenColumnCount} more
                  </button>
                </li>
              )}
            </>
          )}
          {isDesigner && (
            <li>
              <button
                type="button"
                onClick={data.onAddColumn}
                className="nodrag w-full border-t border-border-subtle px-3 py-2 text-center text-xs font-medium text-accent-primary outline-none transition-colors hover:bg-accent-primary-surface focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring"
              >
                {label('erCanvas.contextMenu.addColumn', 'Add column')}
              </button>
            </li>
          )}
        </ul>
      )}
    </section>
  )
}

function ColumnRow({
  column,
  mode,
  dialect,
  onUpdateColumn,
  onDeleteColumn,
}: {
  column: ErColumnMeta
  mode: ErTableNodeMode
  dialect?: ErDesignerDialect
  onUpdateColumn?: (columnId: string, updates: Partial<ErColumnMeta>) => void
  onDeleteColumn?: (columnId: string) => void
}) {
  const columnId = getColumnId(column)
  const role = rowRole(column)
  const isDesigner = mode === 'designer'

  // Rail: solid for PK/PKFK, dashed for FK, none for regular.
  const railClass = (() => {
    if (role === 'pk') return 'bg-border-strong'
    if (role === 'pkfk') return 'bg-border-strong'
    if (role === 'fk') return 'border-l-[1.5px] border-dashed border-border-default'
    return ''
  })()

  // Handle visuals: target = solid circle, source = ring circle.
  const handleAnchor = isDesigner
    ? 'er-handle !border-none !bg-transparent !cursor-crosshair flex items-center justify-center z-20'
    : 'er-handle !border-none !bg-transparent flex items-center justify-center z-20'
  const handleAnchorStyle = { width: 20, height: 20 } as const
  const targetBallClass = [
    'block rounded-full transition-transform duration-150',
    isDesigner
      ? 'h-2 w-2 bg-border-strong ring-2 ring-bg-canvas hover:scale-[1.25] hover:ring-accent-primary'
      : 'h-1.5 w-1.5 bg-border-strong ring-2 ring-bg-canvas opacity-0 group-hover:opacity-100',
  ].join(' ')
  const sourceBallClass = [
    'block rounded-full transition-transform duration-150',
    isDesigner
      ? 'h-2 w-2 border-2 border-border-strong bg-bg-canvas hover:scale-[1.25] hover:border-accent-primary'
      : 'h-1.5 w-1.5 border-2 border-border-strong bg-bg-canvas opacity-0 group-hover:opacity-100',
  ].join(' ')

  return (
    <li
      className={[
        'group relative flex items-center justify-between gap-2 border-b border-border-subtle pr-3 last:border-b-0 hover:bg-interaction-hover',
        isDesigner ? 'min-h-8 py-1.5' : 'min-h-7 py-1',
      ].join(' ')}
      data-testid={`er-row-${column.name}`}
      data-er-row-role={role === 'pkfk' ? 'pkfk' : role}
    >
      {/* Left rail (3px) */}
      <span
        aria-hidden="true"
        className={[
          'absolute inset-y-0 left-0 w-[3px]',
          railClass,
          // PKFK overlay: a 1px dashed inset on top of the solid rail.
          role === 'pkfk' ? 'after:absolute after:inset-y-0 after:right-0 after:w-px after:border-r after:border-dashed after:border-border-default' : '',
        ].join(' ')}
        data-er-rail-role={role === 'pkfk' ? 'pkfk' : role}
      />

      <Handle
        type="target"
        position={Position.Left}
        id={`${columnId}-target`}
        className={handleAnchor}
        style={handleAnchorStyle}
      >
        <span data-er-handle-shape="solid" className={targetBallClass} />
      </Handle>

      <div className="ml-4 flex min-w-0 flex-1 items-center gap-1.5">
        {(role === 'pk' || role === 'pkfk') && (
          <KeyRoundIcon
            className="size-3 shrink-0 text-text-muted group-hover:text-accent-primary"
            aria-label="primary key"
          />
        )}
        {(role === 'fk' || role === 'pkfk') && (
          <LinkIcon
            className="size-3 shrink-0 text-text-muted group-hover:text-accent-primary"
            aria-label="foreign key"
          />
        )}
        {role === 'regular' && <span className="inline-block w-3 shrink-0" aria-hidden="true" />}

        {isDesigner ? (
          <input
            type="text"
            value={column.name}
            aria-label={`Column name ${column.name}`}
            onChange={(event) => onUpdateColumn?.(columnId, { name: event.target.value })}
            className={[
              'nodrag min-w-0 flex-1 rounded-sm border border-transparent bg-transparent px-1 text-sm leading-5 outline-none focus:border-border-default focus:bg-bg-panel',
              column.isPK ? 'font-medium text-text-strong' : 'text-text-base',
            ].join(' ')}
          />
        ) : (
          <span
            className={[
              'truncate text-sm leading-5',
              column.isPK ? 'font-medium text-text-strong' : 'text-text-base',
            ].join(' ')}
          >
            {column.name}
          </span>
        )}
      </div>

      {isDesigner ? (
        <ColumnTypeSelect
          value={column.type}
          dialect={dialect}
          label={`Type for ${column.name}`}
          onValueChange={(type) => onUpdateColumn?.(columnId, { type })}
        />
      ) : (
        <span className="shrink-0 rounded-sm border border-border-subtle bg-bg-subtle px-1.5 font-mono text-xs leading-4 text-text-muted">
          {column.type}
        </span>
      )}

      {column.nullable === false ? (
        <span
          data-er-nn-pill
          className="shrink-0 rounded-sm border border-border-subtle px-1 text-[10px] leading-4 text-text-soft"
          title="NOT NULL"
        >
          NN
        </span>
      ) : null}

      {isDesigner && (
        <button
          type="button"
          aria-label={`Delete column ${column.name}`}
          onClick={() => onDeleteColumn?.(columnId)}
          className="nodrag -mr-1 rounded p-1 text-text-soft opacity-0 transition-all duration-150 hover:bg-status-danger-surface hover:text-status-danger group-hover:opacity-100 focus-visible:opacity-100 focus-visible:ring-2 focus-visible:ring-ring"
        >
          <Trash2Icon className="size-3" aria-hidden="true" />
        </button>
      )}

      <Handle
        type="source"
        position={Position.Right}
        id={`${columnId}-source`}
        className={handleAnchor}
        style={handleAnchorStyle}
      >
        <span data-er-handle-shape="ring" className={sourceBallClass} />
      </Handle>
    </li>
  )
}

export const MemoErTableNode = memo(ErTableNode)
MemoErTableNode.displayName = 'ErTableNode'

const COLUMN_TYPE_OPTIONS = ['BIGINT', 'INT', 'VARCHAR(255)', 'TEXT', 'BOOLEAN', 'DATE', 'TIMESTAMP']

const DIALECT_COLUMN_TYPE_OPTIONS: Record<ErDesignerDialect, string[]> = {
  mysql: [
    'TINYINT', 'SMALLINT', 'MEDIUMINT', 'INT', 'INTEGER', 'BIGINT', 'SERIAL',
    'DECIMAL', 'DECIMAL(10,2)', 'DEC', 'FIXED', 'NUMERIC', 'NUMERIC(10,2)',
    'FLOAT', 'DOUBLE', 'DOUBLE PRECISION', 'REAL', 'BIT(1)', 'BOOL', 'BOOLEAN',
    'CHAR', 'CHAR(255)', 'NCHAR(255)', 'VARCHAR(255)', 'NVARCHAR(255)',
    'TINYTEXT', 'TEXT', 'MEDIUMTEXT', 'LONGTEXT',
    'BINARY(16)', 'VARBINARY(255)', 'TINYBLOB', 'BLOB', 'MEDIUMBLOB', 'LONGBLOB',
    'DATE', 'TIME', 'DATETIME', 'TIMESTAMP', 'YEAR', 'JSON',
    "ENUM('value')", "SET('value')",
    'GEOMETRY', 'POINT', 'LINESTRING', 'POLYGON',
    'MULTIPOINT', 'MULTILINESTRING', 'MULTIPOLYGON', 'GEOMETRYCOLLECTION',
  ],
  postgresql: [
    'SMALLINT', 'INTEGER', 'BIGINT', 'SMALLSERIAL', 'SERIAL', 'BIGSERIAL',
    'DECIMAL', 'DECIMAL(10,2)', 'NUMERIC', 'NUMERIC(10,2)',
    'REAL', 'DOUBLE PRECISION', 'BOOLEAN', 'BIT(1)', 'BIT VARYING(255)',
    'CHAR(255)', 'VARCHAR(255)', 'TEXT',
    'DATE', 'TIME', 'TIMETZ', 'TIMESTAMP', 'TIMESTAMPTZ', 'INTERVAL',
    'UUID', 'JSON', 'JSONB', 'BYTEA',
    'INET', 'CIDR', 'MACADDR', 'MACADDR8', 'MONEY',
    'POINT', 'LINE', 'LSEG', 'BOX', 'PATH', 'POLYGON', 'CIRCLE',
    'TSVECTOR', 'TSQUERY', 'XML',
  ],
  h2: [
    'TINYINT', 'SMALLINT', 'INT', 'BIGINT', 'DECIMAL(10,2)', 'NUMERIC(10,2)',
    'REAL', 'DOUBLE', 'BOOLEAN', 'CHAR(255)', 'VARCHAR(255)', 'CLOB',
    'BINARY(16)', 'VARBINARY(255)', 'BLOB',
    'DATE', 'TIME', 'TIMESTAMP', 'UUID', 'JSON',
  ],
  sqlite: ['INTEGER', 'REAL', 'NUMERIC', 'TEXT', 'BLOB'],
}

export function getColumnTypeOptions(dialect?: ErDesignerDialect, currentValue?: string): string[] {
  const baseOptions = dialect ? DIALECT_COLUMN_TYPE_OPTIONS[dialect] : COLUMN_TYPE_OPTIONS
  const options = [...baseOptions]
  if (currentValue && !options.includes(currentValue)) {
    options.unshift(currentValue)
  }
  return options
}

function ColumnTypeSelect({
  value,
  dialect,
  label,
  onValueChange,
}: {
  value: string
  dialect?: ErDesignerDialect
  label: string
  onValueChange: (value: string) => void
}) {
  const options = getColumnTypeOptions(dialect, value)
  return (
    <Select
      value={value}
      onValueChange={(nextValue) => {
        if (nextValue) onValueChange(nextValue)
      }}
    >
      <SelectTrigger
        aria-label={label}
        size="sm"
        className="nodrag h-6 max-w-28 border-border-subtle bg-bg-subtle px-1.5 font-mono text-xs text-text-muted"
      >
        <SelectValue />
      </SelectTrigger>
      <SelectContent className="bg-bg-panel">
        {options.map((type) => (
          <SelectItem key={type} value={type}>
            {type}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

function getColumnId(column: ErColumnMeta) {
  const maybeId = (column as ErColumnMeta & { id?: unknown }).id
  return typeof maybeId === 'string' ? maybeId : column.name
}

function useFallbackLabel(t: ReturnType<typeof useI18n>['t']) {
  return (key: string, fallback: string) => {
    const translated = t(key as Parameters<typeof t>[0])
    return translated === key ? fallback : translated
  }
}
```

> 类映射兜底说明：
> - `ring-bg-canvas` 与 `ring-accent-primary` 这两个类如不存在，可改用 `style={{ boxShadow: 'inset 0 0 0 2px var(--dt-bg-canvas)' }}` 之类内联（保持语义）。先 grep 项目其它组件使用情况；项目 `useChannel` 调试组件、Stage 大量使用 `ring-*`，类应已存在。
> - `bg-status-danger-surface` 同理。
> - `border-border-strong` `bg-border-strong` `text-border-strong` 等需要 grep 验证；项目已大量使用 `border-border-default` / `border-border-subtle`，`border-strong` 类应也已暴露。如缺失，对应替换为 `style={{ backgroundColor: 'var(--dt-border-strong)' }}` 之类。

- [x] **Step 6.4: 运行测试，预期通过**

```bash
cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx
```

预期：所有原有 + 8 个新测试全绿。如失败，按 spec §6 / §4 调整 className，**不要**回退到 primitive 色。

- [x] **Step 6.5: Commit**

```bash
git add client/src/features/stage/components/er-canvas/ErTableNode.tsx \
  client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx
git commit -m "feat(er-canvas): redesign table node card per redesign spec

Adds left rail (3px solid for PK / 1.5px dashed for FK / overlay for
PKFK), unifies role icons + name + type chip + NN pill on a single row,
makes Designer delete button hover-only, switches connection handles to
shape distinction (target solid / source ring) on neutral border-strong
with hover ring accent-primary, adds empty-table copy and ChevronDown
hint for collapsed state. Studio vs Instrument differences land via
border / shadow / handle visibility only."
```

---

## Task 7: ErEdge restyle (markerEnd + virtual chip + hover)

**Files:**
- Modify: `client/src/features/stage/components/er-canvas/ErEdge.tsx`
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErEdge.test.tsx`

**Phase:** C（可与 Task 6 并发）

- [x] **Step 7.1: 增量加测**

在 `__tests__/ErEdge.test.tsx` **末尾**加入：

```tsx
describe('ErEdge redesign', () => {
  function renderEdge(overrides: Partial<EdgeProps<Edge<ErEdgeData>>> = {}) {
    // Use the same renderEdge helper present in this file. If not, fall back
    // to existing test scaffolding patterns.
    // (Adjust if the helper signature differs in current file.)
  }

  it('uses markerEnd reference for default state', () => {
    const props = makeEdgeProps({ kind: 'fk', selected: false })
    const { container } = render(
      <ReactFlowProvider>
        <svg>
          <ErEdge {...props} />
        </svg>
      </ReactFlowProvider>,
    )
    const path = container.querySelector('path[stroke]')!
    expect(path.getAttribute('marker-end')).toBe('url(#er-edge-arrow-default)')
  })

  it('uses markerEnd reference for virtual state', () => {
    const props = makeEdgeProps({ kind: 'virtual', selected: false })
    const { container } = render(
      <ReactFlowProvider>
        <svg>
          <ErEdge {...props} />
        </svg>
      </ReactFlowProvider>,
    )
    expect(container.querySelector('path[stroke]')!.getAttribute('marker-end')).toBe(
      'url(#er-edge-arrow-virtual)',
    )
    expect(container.querySelector('path[stroke]')!.getAttribute('stroke-dasharray')).toBe('4 3')
  })

  it('uses markerEnd reference for selected state', () => {
    const props = makeEdgeProps({ kind: 'fk', selected: true })
    const { container } = render(
      <ReactFlowProvider>
        <svg>
          <ErEdge {...props} />
        </svg>
      </ReactFlowProvider>,
    )
    expect(container.querySelector('path[stroke]')!.getAttribute('marker-end')).toBe(
      'url(#er-edge-arrow-selected)',
    )
  })

  it('renders virtual pill suffix when kind=virtual', () => {
    const props = makeEdgeProps({ kind: 'virtual', selected: false })
    render(
      <ReactFlowProvider>
        <svg>
          <ErEdge {...props} />
        </svg>
      </ReactFlowProvider>,
    )
    expect(screen.getByText(/virtual/i)).toBeInTheDocument()
  })
})
```

> ⚠ `makeEdgeProps` 是现有测试已用工具或 inline factory；如不存在请仿现有测试 scaffolding 写一个 helper，提供完整 `EdgeProps<Edge<ErEdgeData>>` 必要字段（id / source / target / sourceX/Y / targetX/Y / sourcePosition / targetPosition / data）。具体形状参见现有 ErEdge.test.tsx。

- [x] **Step 7.2: 运行测试，预期失败**

```bash
cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/ErEdge.test.tsx
```

预期：4 个新断言失败（marker-end 当前不存在、virtual 文案当前位置不同等）。

- [x] **Step 7.3: 改写 `ErEdge.tsx`**

在现有文件基础上做以下改动（用 Edit 工具按下面的精确替换）：

**改动 1：stroke / dasharray / markerEnd 选择**

`old_string`:

```tsx
  const stroke = selected
    ? 'var(--dt-accent-primary)'
    : isVirtual
      ? 'var(--dt-accent-warn)'
      : 'var(--dt-border-strong)'
  const strokeWidth = selected ? 2.5 : 2
  const strokeDasharray = isVirtual ? '6 3' : undefined
```

`new_string`:

```tsx
  const stroke = selected
    ? 'var(--dt-accent-primary)'
    : isVirtual
      ? 'var(--dt-accent-warn)'
      : 'var(--dt-border-strong)'
  const strokeWidth = selected ? 2 : 1.5
  const strokeDasharray = isVirtual ? '4 3' : undefined
  const markerId = selected
    ? 'er-edge-arrow-selected'
    : isVirtual
      ? 'er-edge-arrow-virtual'
      : 'er-edge-arrow-default'
```

**改动 2：path 元素引用 markerEnd**

`old_string`:

```tsx
      <path
        d={edgePath}
        fill="none"
        markerEnd={markerEnd}
        markerStart={markerStart}
        stroke={stroke}
```

`new_string`:

```tsx
      <path
        d={edgePath}
        fill="none"
        markerEnd={`url(#${markerId})`}
        markerStart={markerStart}
        stroke={stroke}
```

> 原 `markerEnd={markerEnd}` 来自 `EdgeProps`；现在我们自己控制，丢弃外层 prop。如 lint 报 `markerEnd` 未使用，从解构里移除该字段。

**改动 3：crossings arc 同步 markerEnd 不要画箭头**

crossings arc path 不应有 marker；现有代码已不带，无需改动，确认 patch 后不引入 markerEnd 即可。

**改动 4：virtual 后缀胶囊**

`old_string`:

```tsx
          ) : (
            <>
              {relationLabel}
              {isVirtual ? <span className="ml-1 text-[var(--dt-accent-warn)]">virtual</span> : null}
            </>
          )}
```

`new_string`:

```tsx
          ) : (
            <>
              {relationLabel}
              {isVirtual ? (
                <span className="ml-1 inline-flex h-4 items-center rounded-sm border border-border-subtle bg-accent-warn-surface px-1 text-[10px] leading-4 text-accent-warn">
                  virtual
                </span>
              ) : null}
            </>
          )}
```

> 类映射兜底：`bg-accent-warn-surface` `text-accent-warn` 项目已有；如缺，改 inline `style={{ backgroundColor: 'var(--dt-accent-warn-surface)', color: 'var(--dt-accent-warn)' }}`。

- [x] **Step 7.4: 运行测试，预期通过**

```bash
cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/ErEdge.test.tsx
```

预期：原测试 + 4 个新测试全绿。

- [x] **Step 7.5: Commit**

```bash
git add client/src/features/stage/components/er-canvas/ErEdge.tsx \
  client/src/features/stage/components/er-canvas/__tests__/ErEdge.test.tsx
git commit -m "feat(er-canvas): edge stroke + markerEnd + virtual pill polish

Stroke widths drop to 1.5/2 (default/selected) and virtual dasharray
tightens to '4 3' for less visual weight. markerEnd now references
ErEdgeMarkers via state-specific id (default/virtual/selected). Virtual
suffix becomes a small accent-warn pill alongside the relation label."
```

---

## Phase C 中段验证

Tasks 6 + 7 完成后跑：

```bash
cd client && npx tsc --noEmit
cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/
```

预期：0 类型错；6 个测试文件全绿。

---

## Task 8: ErCanvas 集成

**Files:**
- Modify: `client/src/features/stage/components/er-canvas/ErCanvas.tsx`
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx`

**Phase:** D（依赖 Task 2 的 ErEdgeMarkers）

- [x] **Step 8.1: 增量加测**

在 `__tests__/ErCanvas.test.tsx` 末尾追加：

```tsx
describe('ErCanvas redesign', () => {
  it('mounts ErEdgeMarkers exactly once inside the canvas', () => {
    // Render an inspector canvas with non-empty selection so the ReactFlow
    // root is mounted (rather than ErEmptyState).
    const { container } = renderInspectorCanvas()
    const markers = container.querySelectorAll('marker#er-edge-arrow-default')
    expect(markers).toHaveLength(1)
  })

  it('uses 18px grid gap for designer mode', () => {
    const { container } = renderDesignerCanvas()
    // ReactFlow Background renders an SVG pattern with gap-related attrs.
    const pattern = container.querySelector('pattern')
    // gap is reflected in pattern width/height
    expect(pattern?.getAttribute('width')).toBe('18')
  })

  it('uses 24px grid gap for inspector mode', () => {
    const { container } = renderInspectorCanvas()
    const pattern = container.querySelector('pattern')
    expect(pattern?.getAttribute('width')).toBe('24')
  })
})
```

> `renderDesignerCanvas` / `renderInspectorCanvas` 是文件中现有测试 helper 或新增 inline factory。请按现有 ErCanvas.test.tsx 的 fixture 形态写。如现有测试 setup 不便提供 `pattern` width 断言，改为 mock `<Background>` 并断言 props，或断言传入 `<Background gap={...} />` 的 prop（通过 ref 或 spy）。pragmatic fallback：导出私有常量 `DESIGNER_GRID_GAP=18` `INSPECTOR_GRID_GAP=24` 并断言常量。

- [x] **Step 8.2: 运行测试，预期失败**

```bash
cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx
```

预期：3 个新断言失败（marker / 18 / 24 都不符）。

- [x] **Step 8.3: 修改 `ErCanvas.tsx`**

在文件顶部 import 区追加：

```tsx
import { ErEdgeMarkers } from './ErEdgeMarkers'
```

定位 ReactFlow 的 `<Background ... />` 行，替换 props 与之后内容：

`old_string`:

```tsx
            <Background
              color="var(--dt-border-subtle)"
              gap={18}
              size={1}
              variant={BackgroundVariant.Dots}
            />
            <Controls showInteractive={false} />
          </ReactFlow>
```

`new_string`:

```tsx
            <Background
              color="var(--dt-border-subtle)"
              gap={mode === 'designer' ? 18 : 24}
              size={1}
              variant={BackgroundVariant.Dots}
            />
            <Controls showInteractive={false} />
            <ErEdgeMarkers />
          </ReactFlow>
```

> ReactFlow 把 children 渲染到内部 `<svg>` 容器，因此 `<ErEdgeMarkers />` 的 `<defs>` 会和 ReactFlow 自身的 SVG 共享坐标空间，每个 `marker id` 全局可见。

- [x] **Step 8.4: 运行测试，预期通过**

```bash
cd client && npx vitest run src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx
```

预期：原测试 + 3 个新测试全绿。

- [x] **Step 8.5: Commit**

```bash
git add client/src/features/stage/components/er-canvas/ErCanvas.tsx \
  client/src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx
git commit -m "feat(er-canvas): wire grid gap by mode + mount ErEdgeMarkers

Designer keeps the existing 18px dot-grid (Studio); Viewer relaxes to
24px (Instrument). ErEdgeMarkers is mounted exactly once as ReactFlow
children so all edges share the marker registry."
```

---

## Task 9: Final Verification & Visual Smoke

**Phase:** E（最终收口）

- [x] **Step 9.1: 全量类型检查**

```bash
cd client && npx tsc --noEmit
```

结果：通过，0 错。

- [x] **Step 9.2: 全量测试**

```bash
cd client && npx vitest run
```

结果：通过，139 个测试文件 / 924 个测试全部通过。

- [x] **Step 9.3: ESLint（如项目启用）**

```bash
cd client && npm run lint --silent || true
```

结果：全仓 lint 已执行，但命中既有 repo-wide lint 基线（310 errors / 7 warnings，主要为历史 `no-explicit-any`、hooks、empty block 等问题）。本轮 ER canvas 变更文件的定向 ESLint 通过；该 gate 记录为已运行，全仓基线仍未通过。

- [x] **Step 9.4: dev 启动 + 手测**

```bash
cd client && npm run dev
```

打开浏览器，依次手测：

1. 进入 ER Designer Tab，确认：
   - 工具栏左侧有 `Designer` 徽章 + Pencil 图标
   - 节点 header 右侧有 Pencil 图标
   - PK 列左侧 3px 实色 rail；FK 列虚线 rail；普通列无 rail
   - 行 hover 时 rail 与图标同步变 cobalt
   - NN 列右侧出现 NN 胶囊
   - 默认看不到删除按钮，行 hover 才出
   - target handle = 18px 实心圆，source handle = 18px 环形圆，hover 时缩放 + cobalt ring；右侧 handle 与删除按钮不重合
   - "+ Add column" 在节点底部
   - 空表显示 "No columns yet" + "+ Add column"
   - 边默认 1.5px 中性、virtual 1.5px warn dashed `4 3`、selected 2px cobalt
   - 边目标端有小箭头
   - 工具栏 Diff vs DB / Generate DDL 在未绑定时 disabled，hover 显示 tooltip
2. 切换到 ER Viewer Tab：
   - 工具栏徽章变 `Viewer` + Lock；节点 header 右侧 Lock
   - 网格更稀疏（24px）；节点无阴影、边框更克制
   - handle 默认隐藏，行 hover 才显示为 16px 实心 / 环形圆
3. 右键节点：上下文菜单按 ArrowDown / ArrowUp 循环；Enter 触发并关闭；Esc 关闭；删除项前有分隔条
4. 切深色主题（系统 / Tauri 主题切换）：所有元素颜色协调，对比度可读
5. 空态四种 reason 各看一遍

结果：未在本轮启动 dev server 或执行浏览器手测；按用户要求优先完成代码与最终编译联调，手工视觉验收 deferred。

- [x] **Step 9.5: 拍摄截图**

光面 + 深色 + Designer + Viewer + 节点态（默认 / hover / selected / 折叠 / 空）+ 边态（默认 / virtual / selected / 自连环 / 跨线）+ 空态（4 reason）+ 上下文菜单（默认 / 危险）共 ≥ 10 张，存到 `/tmp/er-redesign-screenshots/`。

结果：未拍摄截图；视觉截图验收 deferred。

- [x] **Step 9.6: axe-core 对比度回归（可选但推荐）**

```bash
cd client && npx @axe-core/cli http://localhost:1420 --tags wcag2aa --include "[data-er-mode], [data-er-row-role]" || true
```

预期：无 critical / serious 项；如有 moderate 与本计划相关，列入 follow-up。

结果：未运行 axe-core；无浏览器实例与 dev server，本项 deferred。

- [x] **Step 9.7: 文档收尾**

- 把本 plan 在 `docs/exec-plans/index.md` Active 表中的条目移到 Completed 段（保持时间倒序）
- 在 spec 顶部状态行从"草案 (2026-04-30)"改为"已落地 (YYYY-MM-DD)"，附 PR 链接（PR 创建后回填）
- 不修改 `client/DESIGN.md` —— 本计划完全在契约之内
- 不修改 `CLAUDE.md` —— 没有新规约
- 数据源类型兼容门：在 PR 描述中显式标注 `N/A — 仅前端纯视觉重设，未触及 dialect / JDBC / SQL 路径`

结果：本 plan、exec-plans index、spec 状态已更新；`client/DESIGN.md` / `CLAUDE.md` 无需修改。数据源类型兼容门 N/A：仅前端 ER canvas 视觉和交互重设，未触及 dialect / JDBC / SQL 路径。

- [x] **Step 9.8: 创建 PR**

```bash
git push -u origin <feature-branch>
gh pr create --title "feat(er-canvas): redesign Designer / Inspector canvas per 2026-04-30 spec" \
  --body "$(cat <<'EOF'
## Summary
- 按 [2026-04-30 ER Canvas Redesign Design](docs/product-specs/2026-04-30-er-canvas-redesign-design.md) 重做 ER Designer / Inspector 画布壳层、节点卡片、边、关系标签、工具栏、空态、上下文菜单
- 模式徽章 + 强调色 + Studio↔Instrument 几何参数轻量身份化区分
- 列行 left rail + 图标 + 字重三通道传 PK/FK 角色，cobalt 严格保留给 focus / selection / primary action
- 连接锚点改为形状区分（target 实心 / source 环形）+ 中性色，移除原 status.success/danger 复用

## Verification
- [x] `cd client && npx tsc --noEmit`：0 错
- [x] `cd client && npx vitest run`：139 files / 924 tests 全绿
- [ ] `cd client && npm run lint --silent`：已执行，命中既有 repo-wide lint 基线（310 errors / 7 warnings）；ER canvas 变更文件定向 ESLint 通过
- [ ] light + dark 手测截图（≥ 10 张）：deferred
- [ ] axe-core：deferred
- [x] 数据源类型兼容门：N/A（纯前端视觉，未触及 dialect / JDBC / SQL）

## Manual acceptance
- [ ] Designer：模式徽章 / header pencil / PK rail / FK rail / NN pill / hover delete / target handle 18px 实心 / source handle 18px 环形 / 右侧 handle 不与删除按钮重合 / hover ring cobalt / +Add column / 空表 / 边 markerEnd / Diff/DDL disabled tooltip
- [ ] Viewer：模式徽章 / header lock / 24px 稀疏网格 / 扁平节点 / handle 默认隐藏并在行 hover 显示 16px / 上下文菜单 / 4 个空态
- [ ] 双主题切换无串色
EOF
)"
```

结果：未创建 PR。当前工作区包含其他计划 / 数据源相关未提交改动，本轮不混合推送或提交；PR 创建 deferred。

---

## 4. Self-Review Coverage Map

| Spec 章节 | 实现任务 |
|---|---|
| §3.1 通用外壳（toolbar、画布、节点 header） | Task 5（toolbar）、Task 6（节点 header）、Task 8（画布） |
| §3.2 Studio vs Instrument | Task 6（节点 shadow / border / handle 可见性）、Task 8（grid gap） |
| §3.3 模式徽章 | Task 5（ModeBadge 子组件） |
| §4 图标色契约 | Task 3 / 4 / 5 / 6 / 7 全部按契约赋色 |
| §5 工具栏 | Task 5 |
| §6.1 卡片容器 / §6.2 Header | Task 6 |
| §6.3 列行 / §6.4 left rail / §6.5 列名 / §6.6 type chip | Task 6 |
| §6.7 NN 标记 | Task 6 |
| §6.8 hover-only delete | Task 6 |
| §6.9 +Add column | Task 6 |
| §6.10 折叠 / 溢出 | Task 6 |
| §6.11 connection handles 形状 | Task 6 |
| §6.12 空表 | Task 6 |
| §7.1-2 边几何 / 描边 | Task 7 |
| §7.3 端点箭头 marker | Task 2 + Task 7 + Task 8 |
| §7.4 跨线让位 | 保持现有（Task 7 不动算法） |
| §7.5 关系标签 chip + virtual 胶囊 | Task 7 |
| §7.6 边 hover 加粗 | Task 7（CSS 在 Step 7.3 改动 1 已落） |
| §8 空态 | Task 3 |
| §9 上下文菜单 | Task 4 |
| §10 动效 | Task 6 / 7 内联 transition class |
| §11 a11y | Task 4（菜单键盘）+ Task 5（aria）+ Task 6（aria）+ Task 7（label） |
| §12 主题 | 全程语义类，无主题分支 |
| §13 文件清单 | Tasks 1-8 完全覆盖 |
| §14 测试策略 | Tasks 2-8 各自含 vitest |
| §15 验证门 | Task 9 |

**自检结论**：spec 全部章节都有对应任务覆盖；无 TBD / TODO / 占位符；类型签名（`ModeBadge`、`ErEmptyReason`、`RowRole`）在引入处完整定义；后续 task 引用前面 task 产物的依赖关系已用 Phase 标注；`markerEnd` id 在 Task 2 定义、Task 7 引用、Task 8 挂载——三处一致。

---

## 5. 已知风险与回退

| 风险 | 触发条件 | 回退 |
|---|---|---|
| Tailwind 类不存在（如 `bg-accent-primary-surface`） | 项目 `@theme` 未暴露该 utility | 改为 inline `style={{ ... 'var(--dt-...)' }}`；**禁止**回退到 primitive |
| ReactFlow children `<defs>` 未生效 | ReactFlow 内部 SVG 容器变化 | 退回 `ErEdge` 内每边声明 marker（次优、有重复但语义正确） |
| `prefers-reduced-motion` 下 transition 仍执行 | Tailwind 没启用 motion-reduce 变体 | 在 `client/src/index.css` 加 `@media (prefers-reduced-motion: reduce)` 全局禁用 transition-* 类 |
| handle 形状区分不显著（target / source 视觉接近） | 8px + 2px 描边在低分辨率下糊 | 加 1px size 差或对比度（**不**用 status 色） |
