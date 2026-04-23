# SQL Tab 内置 Activity Rail 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Stage 窗口顶层的 `StageActivityRail`（Schema/历史/大纲）下移到 SQL 编辑器 Tab 内部，仅在 `query_editor` 类型 Tab 中渲染；文件预览等其它 Tab 不再显示该 rail。

**Architecture:** 删除 `StageWindow` 中 `<StageActivityRail>` 渲染；将 `SqlWorkbenchTab` 最外层从 `flex-col` 改为 `flex-row`，内部沿用现有 `sql-workbench-layout`（`flex-col`，承载编辑器 + 结果上下分割）作为主区，右侧并列一个 `<StageActivityRail sessionId={tab.originSessionId ?? null} />`。Rail 组件签名与状态作用域 `activeRailPanelBySession` 不动。

**Tech Stack:** React 19 + TypeScript + Vitest + @testing-library/react + Tailwind utility classes

**Spec:** `docs/product-specs/2026-04-23-sql-tab-internal-rail-design.md`

> **2026-04-23 Status Update**
>
> - 本计划对应的目标代码已经落在 `stage-window` / `sql-workbench-tab` / 相关测试文件中，不再是纯 `pending` 状态。
> - 定向验证已通过：`stage-window.test.tsx`、`sql-workbench-tab.test.tsx`、`file-preview-tab.test.tsx` 共 44 条测试通过，`cd client && npx tsc --noEmit` 通过。
> - 更大范围回归尚未收口：`cd client && npx vitest run src/features/stage` 与 `cd client && npm test` 当前各有 2 个失败，落在 `QueryEditorAdapter.test.ts` 与 `stage-ui-object-registry.test.tsx`，属于并行中的 `Query Editor Object Actions` 契约迁移影响，不是 rail 布局本身的回归。
> - 手动视觉 smoke 尚未执行，因此本文当前状态应视为“代码已落地，待完整回归与文档收口”，而非 Completed。

---

## File Structure

| 文件 | 角色 | 改动 |
|------|------|------|
| `client/src/features/stage/components/stage-window.tsx` | Stage 窗口外壳 | 删除 `<StageActivityRail>` 引用与渲染 |
| `client/src/features/stage/components/sql-workbench-tab.tsx` | SQL 工作台 Tab | 外层包横向 flex；右侧挂 `<StageActivityRail>` |
| `client/src/features/stage/components/stage-window.test.tsx` | Stage 窗口测试 | 翻转「rail 出现在窗口层」断言 |
| `client/src/features/stage/components/sql-workbench-tab.test.tsx` | SQL Tab 测试 | mock `StageActivityRail`；新增「rail 出现在 SQL Tab 内」断言 |
| `client/src/features/stage/components/file-preview-tab.test.tsx` | 文件预览 Tab 测试 | 新增「rail 不出现在文件预览 Tab 内」断言 |
| `client/src/features/stage/components/activity-rail/stage-activity-rail.tsx` | Activity Rail 组件 | **不动** |
| `client/src/stores/stage-store.ts` | Stage 状态 | **不动** |

---

## Task 1: 翻转 stage-window 对 rail 的断言（红）

**Files:**
- Modify: `client/src/features/stage/components/stage-window.test.tsx:197-212`

- [x] **Step 1: 改写「rail 在窗口层」用例为「不再在窗口层」**

把原断言反向，并改测试名以反映新意图。

```typescript
it('does not render the activity rail at the window level (rail moved into SQL tab)', () => {
  useStageStore.setState({
    tabsBySession: new Map(),
    activeTabIdBySession: new Map(),
    workspaceTabs: [],
    activeWorkspaceTabId: null,
    activeRailPanelBySession: new Map(),
  })

  render(<StageWindow sessionId="s1" />)

  expect(screen.queryByTestId('stage-activity-rail')).toBeNull()
  expect(screen.queryByTestId('stage-sidebar')).toBeNull()
  expect(screen.queryByTestId('stage-resource-browser')).toBeNull()
  expect(screen.queryByTestId('stage-tool-row')).toBeNull()
})
```

- [x] **Step 2: 运行该用例确认 FAIL**

Run: `cd client && npx vitest run src/features/stage/components/stage-window.test.tsx -t "does not render the activity rail at the window level"`

Expected: FAIL — 当前 StageWindow 仍渲染了 rail，`queryByTestId('stage-activity-rail')` 不为 null。

---

## Task 2: 从 StageWindow 删除 rail（绿）

**Files:**
- Modify: `client/src/features/stage/components/stage-window.tsx:7,157`

- [x] **Step 1: 删除 StageActivityRail import**

把第 7 行：

```typescript
import { StageActivityRail } from './activity-rail/stage-activity-rail'
```

删除。

- [x] **Step 2: 删除 StageActivityRail 渲染**

把第 157 行的 `<StageActivityRail sessionId={sessionId ?? null} />` 删除。删除后包裹它的 flex row 容器只剩一个 `<section>` 子元素，保留容器即可（不需要解构主区结构）。

修改后第 116–159 行附近的结构：

```tsx
<div className="flex min-h-0 flex-1 overflow-hidden bg-background/88">
  <section className="flex min-w-0 flex-1 flex-col overflow-hidden">
    {/* 已有内容保持不动 */}
  </section>
</div>
```

- [x] **Step 3: 重新运行 Task 1 用例确认 PASS**

Run: `cd client && npx vitest run src/features/stage/components/stage-window.test.tsx -t "does not render the activity rail at the window level"`

Expected: PASS

- [x] **Step 4: 运行 stage-window 全套测试确认无回归**

Run: `cd client && npx vitest run src/features/stage/components/stage-window.test.tsx`

Expected: 全部 PASS（其他用例不依赖 rail，应不受影响）。

---

## Task 3: SQL Tab 测试新增「rail 渲染在内部」断言（红）

**Files:**
- Modify: `client/src/features/stage/components/sql-workbench-tab.test.tsx`

- [x] **Step 1: 在 mock 区追加 StageActivityRail mock**

在文件顶部已有 mock 块（约第 86–110 行 `vi.mock('@/features/connection/store', …)` 附近）后，新增：

```typescript
vi.mock('./activity-rail/stage-activity-rail', () => ({
  StageActivityRail: ({ sessionId }: { sessionId: string | null }) => (
    <div data-testid="stage-activity-rail-stub" data-session-id={sessionId ?? ''} />
  ),
}))
```

- [x] **Step 2: 在 `describe('SqlWorkbenchTab', …)` 内追加新用例**

在已有用例之后追加：

```typescript
it('renders the activity rail inside the SQL tab and propagates the origin sessionId', () => {
  render(
    <SqlWorkbenchTab
      tab={{
        ...tab,
        tabId: 'tab-rail',
        originSessionId: 'sess-99',
      }}
    />,
  )

  const rail = screen.getByTestId('stage-activity-rail-stub')
  expect(rail).toBeTruthy()
  expect(rail.getAttribute('data-session-id')).toBe('sess-99')

  const tabRoot = screen.getByTestId('sql-workbench-tab')
  expect(tabRoot.contains(rail)).toBe(true)
  expect(tabRoot.className).toContain('flex-row')
})

it('passes empty sessionId to the rail when the SQL tab has no origin session', () => {
  render(<SqlWorkbenchTab tab={{ ...tab, tabId: 'tab-rail-workspace' }} />)

  const rail = screen.getByTestId('stage-activity-rail-stub')
  expect(rail.getAttribute('data-session-id')).toBe('')
})
```

> 说明：fixture `tab` 默认无 `originSessionId`，因此第二个用例会拿到 `null` → DOM 上的空字符串。

- [x] **Step 3: 运行新增用例确认 FAIL**

Run: `cd client && npx vitest run src/features/stage/components/sql-workbench-tab.test.tsx -t "renders the activity rail inside the SQL tab"`

Expected: FAIL — 当前 SqlWorkbenchTab 没有渲染 rail；`getByTestId('stage-activity-rail-stub')` 抛错。

---

## Task 4: SqlWorkbenchTab 内嵌 rail（绿）

**Files:**
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`

- [x] **Step 1: 引入 StageActivityRail**

在 import 区追加（按字母顺序放在合适位置）：

```typescript
import { StageActivityRail } from './activity-rail/stage-activity-rail'
```

- [x] **Step 2: 改造最外层 JSX**

把 `return (...)` 中最外层结构改为横向 flex，主区沿用 `sql-workbench-layout`，右侧挂 rail。

定位文件第 540–642 行的 `return` 块。把：

```tsx
<div data-testid="sql-workbench-tab" className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col bg-background">
  <div ref={splitLayoutRef} data-testid="sql-workbench-layout" className="flex min-h-0 flex-1 flex-col">
    {/* … 编辑器 + 结果上下分割（不变）… */}
  </div>
</div>
```

改为：

```tsx
<div data-testid="sql-workbench-tab" className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-row bg-background">
  <div ref={splitLayoutRef} data-testid="sql-workbench-layout" className="flex min-h-0 min-w-0 flex-1 flex-col">
    {/* … 编辑器 + 结果上下分割（保持不动）… */}
  </div>
  <StageActivityRail sessionId={tab.originSessionId ?? null} />
</div>
```

要点：

- 外层 `flex-col` → `flex-row`
- `sql-workbench-layout` 追加 `min-w-0 flex-1`，确保编辑器横向自适应
- rail 是该 flex row 第二个子元素，其自身 className 已带 `shrink-0` 与 `border-l`

中间「编辑器 toolbar + Monaco + 结果分割」全部保持原样，不动。

- [x] **Step 3: 重跑 Task 3 新增用例确认 PASS**

Run: `cd client && npx vitest run src/features/stage/components/sql-workbench-tab.test.tsx -t "renders the activity rail inside the SQL tab"`

Expected: PASS

- [x] **Step 4: 跑 SQL Tab 全套测试无回归**

Run: `cd client && npx vitest run src/features/stage/components/sql-workbench-tab.test.tsx`

Expected: 全部 PASS。注意 splitter 拖拽用例（`supports dragging the horizontal splitter above result tabs`）依赖 `sql-workbench-layout` 的 `getBoundingClientRect`，本次修改没动 layout 高度链路，应不受影响。

---

## Task 5: 文件预览 Tab 不出现 rail 的断言

**Files:**
- Modify: `client/src/features/stage/components/file-preview-tab.test.tsx`

- [x] **Step 1: 新增断言用例**

在 `describe('FilePreviewTab', …)` 内追加：

```typescript
it('does not render the activity rail inside the file preview tab', () => {
  const tab = buildTabFromRawOutput(
    [
      '<path>/workspace/src/app/example.ts</path>',
      '<type>file</type>',
      '<content>export const answer = 42\n</content>',
    ].join('\n'),
  )

  expect(tab).not.toBeNull()
  if (!tab) return

  render(<FilePreviewTab tab={tab} />)

  expect(screen.queryByTestId('stage-activity-rail')).toBeNull()
  expect(screen.queryByTestId('stage-activity-rail-stub')).toBeNull()
})
```

- [x] **Step 2: 运行该用例确认 PASS**

Run: `cd client && npx vitest run src/features/stage/components/file-preview-tab.test.tsx -t "does not render the activity rail inside the file preview tab"`

Expected: PASS（FilePreviewTab 从未引入 rail，本来就不会渲染；该用例是回归保护）。

- [x] **Step 3: 跑 file-preview 全套测试**

Run: `cd client && npx vitest run src/features/stage/components/file-preview-tab.test.tsx`

Expected: 全部 PASS。

---

## Task 6: 跨文件全量校验

**Files:**
- 无新增 / 修改

- [x] **Step 1: 类型检查**

Run: `cd client && npx tsc --noEmit`

Expected: 零错误。

- [ ] **Step 2: stage 相关 vitest**

Run: `cd client && npx vitest run src/features/stage`

Expected: 全部 PASS。重点关注 `stage-window.test.tsx`、`sql-workbench-tab.test.tsx`、`file-preview-tab.test.tsx`、`stage-activity-rail.test.tsx`。

2026-04-23 实际结果：命令已执行，但当前失败 2 条非 rail 用例：

- `src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts`
- `src/features/stage/components/stage-ui-object-registry.test.tsx`

两者都反映 `query_editor` 对象契约迁移中的并行改动；rail 相关文件和断言本身保持通过。

- [ ] **Step 3: 全量前端测试（兜底）**

Run: `cd client && npm test`

Expected: 全部 PASS。

2026-04-23 实际结果：命令已执行，失败点与 Step 2 相同，仍是上述 2 条 `query_editor` / registry 契约用例，不属于 rail 布局本身回归。

---

## Task 7: 手动视觉验证

**Files:**
- 无

- [ ] **Step 1: 启动前端**

Run: `cd client && npm run dev`

- [ ] **Step 2: 视觉验证 checklist**

在浏览器打开应用后逐项确认：

1. 打开任一会话 → 进入 Stage → 创建一个 SQL 编辑器 Tab：
   - rail 出现在 Tab 内容矩形右侧
   - rail 顶边对齐 SQL 工具栏顶边，不超过上方 Tab 标签栏
   - rail 底边贴 Tab 底
2. 在同一 session 打开第二个 SQL Tab，先在 Tab A 点开 outline 面板：
   - 切到 Tab B，rail 仍展开 outline，但内容换成 Tab B 的 SQL 大纲
   - 再切回 Tab A，outline 内容回到 Tab A 自己的
3. 从聊天里点 read 工具的「同步到工作台」打开文件预览 Tab：
   - 文件预览 Tab 内**不**出现任何 rail 图标列或滑出面板
4. 切回 SQL Tab：rail 重新出现，状态保留

- [ ] **Step 3: 在计划中勾掉对应 checklist 项**

如发现视觉异常，记录在本计划「Notes / Deviations」节，再开必要的修复 task。

---

## Task 8: 文档状态同步

**Files:**
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-23-sql-tab-internal-rail-plan.md`（本文件）
- Modify: `docs/exec-plans/2026-04-21-implementation-roadmap-plan.md`
- Modify: `docs/product-specs/2026-04-23-sql-tab-internal-rail-design.md`

- [x] **Step 1: 同步 index 中的真实状态**

将 `docs/exec-plans/index.md` 中本计划从 `pending` 调整为 `in_progress`，并明确说明：

- 代码与定向 rail 验证已落地
- 更大范围 stage / 全量前端回归仍被并行中的 `Query Editor Object Actions` 契约变更阻塞
- 手动视觉 smoke 尚未执行，因此暂不进入 Completed

- [x] **Step 2: 在本计划、总 roadmap 与 design 文件中记录当前阻塞**

更新本文、`2026-04-21-implementation-roadmap-plan.md` 与 design 文件头状态，明确：

- rail 布局改造已经落在代码
- 当前剩余的是 broader suite / manual smoke / 最终 Completed 搬迁
- `Query Editor Object Actions` 是当前真正影响收口的并行主线

- [ ] **Step 3: 待 broader suite 与手动视觉验证收口后，再转入 Completed**

转入 Completed 的前提保留为：

- `cd client && npx vitest run src/features/stage` 全绿
- `cd client && npm test` 全绿
- Task 7 手动视觉 smoke 完成

## Status

- 状态：in_progress
- 最近更新：2026-04-23
- 关联 spec：`docs/product-specs/2026-04-23-sql-tab-internal-rail-design.md`
- 说明：代码与定向 rail 验证已落地；broader suite 与视觉 smoke 待收口

---

## Notes / Deviations

（执行中如有偏离原计划，写到此处）
