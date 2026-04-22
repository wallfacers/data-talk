# SQL Tab 内置 Activity Rail 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Stage 窗口顶层的 `StageActivityRail`（Schema/历史/大纲）下移到 SQL 编辑器 Tab 内部，仅在 `query_editor` 类型 Tab 中渲染；文件预览等其它 Tab 不再显示该 rail。

**Architecture:** 删除 `StageWindow` 中 `<StageActivityRail>` 渲染；将 `SqlWorkbenchTab` 最外层从 `flex-col` 改为 `flex-row`，内部沿用现有 `sql-workbench-layout`（`flex-col`，承载编辑器 + 结果上下分割）作为主区，右侧并列一个 `<StageActivityRail sessionId={tab.originSessionId ?? null} />`。Rail 组件签名与状态作用域 `activeRailPanelBySession` 不动。

**Tech Stack:** React 19 + TypeScript + Vitest + @testing-library/react + Tailwind utility classes

**Spec:** `docs/product-specs/2026-04-23-sql-tab-internal-rail-design.md`

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

- [ ] **Step 1: 改写「rail 在窗口层」用例为「不再在窗口层」**

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

- [ ] **Step 2: 运行该用例确认 FAIL**

Run: `cd client && npx vitest run src/features/stage/components/stage-window.test.tsx -t "does not render the activity rail at the window level"`

Expected: FAIL — 当前 StageWindow 仍渲染了 rail，`queryByTestId('stage-activity-rail')` 不为 null。

---

## Task 2: 从 StageWindow 删除 rail（绿）

**Files:**
- Modify: `client/src/features/stage/components/stage-window.tsx:7,157`

- [ ] **Step 1: 删除 StageActivityRail import**

把第 7 行：

```typescript
import { StageActivityRail } from './activity-rail/stage-activity-rail'
```

删除。

- [ ] **Step 2: 删除 StageActivityRail 渲染**

把第 157 行的 `<StageActivityRail sessionId={sessionId ?? null} />` 删除。删除后包裹它的 flex row 容器只剩一个 `<section>` 子元素，保留容器即可（不需要解构主区结构）。

修改后第 116–159 行附近的结构：

```tsx
<div className="flex min-h-0 flex-1 overflow-hidden bg-background/88">
  <section className="flex min-w-0 flex-1 flex-col overflow-hidden">
    {/* 已有内容保持不动 */}
  </section>
</div>
```

- [ ] **Step 3: 重新运行 Task 1 用例确认 PASS**

Run: `cd client && npx vitest run src/features/stage/components/stage-window.test.tsx -t "does not render the activity rail at the window level"`

Expected: PASS

- [ ] **Step 4: 运行 stage-window 全套测试确认无回归**

Run: `cd client && npx vitest run src/features/stage/components/stage-window.test.tsx`

Expected: 全部 PASS（其他用例不依赖 rail，应不受影响）。

---

## Task 3: SQL Tab 测试新增「rail 渲染在内部」断言（红）

**Files:**
- Modify: `client/src/features/stage/components/sql-workbench-tab.test.tsx`

- [ ] **Step 1: 在 mock 区追加 StageActivityRail mock**

在文件顶部已有 mock 块（约第 86–110 行 `vi.mock('@/features/connection/store', …)` 附近）后，新增：

```typescript
vi.mock('./activity-rail/stage-activity-rail', () => ({
  StageActivityRail: ({ sessionId }: { sessionId: string | null }) => (
    <div data-testid="stage-activity-rail-stub" data-session-id={sessionId ?? ''} />
  ),
}))
```

- [ ] **Step 2: 在 `describe('SqlWorkbenchTab', …)` 内追加新用例**

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

- [ ] **Step 3: 运行新增用例确认 FAIL**

Run: `cd client && npx vitest run src/features/stage/components/sql-workbench-tab.test.tsx -t "renders the activity rail inside the SQL tab"`

Expected: FAIL — 当前 SqlWorkbenchTab 没有渲染 rail；`getByTestId('stage-activity-rail-stub')` 抛错。

---

## Task 4: SqlWorkbenchTab 内嵌 rail（绿）

**Files:**
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`

- [ ] **Step 1: 引入 StageActivityRail**

在 import 区追加（按字母顺序放在合适位置）：

```typescript
import { StageActivityRail } from './activity-rail/stage-activity-rail'
```

- [ ] **Step 2: 改造最外层 JSX**

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

- [ ] **Step 3: 重跑 Task 3 新增用例确认 PASS**

Run: `cd client && npx vitest run src/features/stage/components/sql-workbench-tab.test.tsx -t "renders the activity rail inside the SQL tab"`

Expected: PASS

- [ ] **Step 4: 跑 SQL Tab 全套测试无回归**

Run: `cd client && npx vitest run src/features/stage/components/sql-workbench-tab.test.tsx`

Expected: 全部 PASS。注意 splitter 拖拽用例（`supports dragging the horizontal splitter above result tabs`）依赖 `sql-workbench-layout` 的 `getBoundingClientRect`，本次修改没动 layout 高度链路，应不受影响。

---

## Task 5: 文件预览 Tab 不出现 rail 的断言

**Files:**
- Modify: `client/src/features/stage/components/file-preview-tab.test.tsx`

- [ ] **Step 1: 新增断言用例**

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

- [ ] **Step 2: 运行该用例确认 PASS**

Run: `cd client && npx vitest run src/features/stage/components/file-preview-tab.test.tsx -t "does not render the activity rail inside the file preview tab"`

Expected: PASS（FilePreviewTab 从未引入 rail，本来就不会渲染；该用例是回归保护）。

- [ ] **Step 3: 跑 file-preview 全套测试**

Run: `cd client && npx vitest run src/features/stage/components/file-preview-tab.test.tsx`

Expected: 全部 PASS。

---

## Task 6: 跨文件全量校验

**Files:**
- 无新增 / 修改

- [ ] **Step 1: 类型检查**

Run: `cd client && npx tsc --noEmit`

Expected: 零错误。

- [ ] **Step 2: stage 相关 vitest**

Run: `cd client && npx vitest run src/features/stage`

Expected: 全部 PASS。重点关注 `stage-window.test.tsx`、`sql-workbench-tab.test.tsx`、`file-preview-tab.test.tsx`、`stage-activity-rail.test.tsx`。

- [ ] **Step 3: 全量前端测试（兜底）**

Run: `cd client && npm test`

Expected: 全部 PASS。

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

## Task 8: 文档收尾

**Files:**
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-23-sql-tab-internal-rail-plan.md`（本文件）

- [ ] **Step 1: 把本计划在 index 中从 Active 移到 Completed**

编辑 `docs/exec-plans/index.md`：

1. 在 `## 已完成计划` 表头下追加一行：

```markdown
| [SQL Tab Internal Activity Rail](./2026-04-23-sql-tab-internal-rail-plan.md) | 2026-04-23 | 把 Stage 窗口顶层的 `StageActivityRail`（Schema/历史/大纲）下移到 `SqlWorkbenchTab` 内部；外层从 flex-col 改为 flex-row，rail 仅在 `query_editor` 类型 Tab 中渲染；状态作用域 `activeRailPanelBySession` 与 rail 组件签名不动；相关 stage vitest 与 `npx tsc --noEmit` 全部通过。 |
```

2. 如该计划在执行中曾被加入 Active 表格，从 Active 删除对应行。

- [ ] **Step 2: 把本计划文件内的 task checkbox 全部勾选完成**

在本文件底部追加一节：

```markdown
## Status

- 状态：Completed
- 完成日期：2026-04-23
- 关联 spec：`docs/product-specs/2026-04-23-sql-tab-internal-rail-design.md`
```

- [ ] **Step 3: 提交所有改动**

Run:

```bash
cd /home/wallfacers/project/data-talk && git status
```

确认改动文件清单符合预期（5 个源/测试文件 + 2 个文档文件）。

随后单次 commit：

```bash
cd /home/wallfacers/project/data-talk && git add \
  client/src/features/stage/components/stage-window.tsx \
  client/src/features/stage/components/sql-workbench-tab.tsx \
  client/src/features/stage/components/stage-window.test.tsx \
  client/src/features/stage/components/sql-workbench-tab.test.tsx \
  client/src/features/stage/components/file-preview-tab.test.tsx \
  docs/exec-plans/2026-04-23-sql-tab-internal-rail-plan.md \
  docs/exec-plans/index.md
git commit -m "$(cat <<'EOF'
feat(stage): move activity rail into SQL editor tab

Rail (Schema/History/Outline) was rendered at StageWindow level
regardless of active tab, polluting non-SQL tabs (file preview etc.)
with empty/meaningless panels. Move it inside SqlWorkbenchTab so it
only appears for query_editor tabs and is visually contained within
the tab rectangle. State scope (activeRailPanelBySession) and the
StageActivityRail component itself are unchanged.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Notes / Deviations

（执行中如有偏离原计划，写到此处）
