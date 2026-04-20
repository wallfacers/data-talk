# AI Message Code Window and Table Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一 AI 消息中的代码块窗体视觉与 Markdown 表格渲染，让所有走 `Markdown` / `PacedMarkdown` 的代码展示区域都呈现带顶部 chrome 的浅色 code window，并为 pipe table 增加稳定的规范化、滚动容器与 token 驱动样式。

**Architecture:** 保持现有 `markdown.tsx -> marked -> DOMPurify -> morphdom` 主链路不变，把新增能力收口到 Markdown 层。实现上分成两条共享管线：一条在 `marked.parse()` 前做窄范围的 pipe table 规范化；另一条在 HTML 落地后统一装饰 code window / SQL header / table wrapper。视觉皮肤集中在 `markdown.css`，reasoning 仅做边距和层级协调，不另起渲染系统。

**Tech Stack:** React 19, marked, DOMPurify, morphdom, Tailwind token system, Vitest, Testing Library

**Spec:** [../product-specs/2026-04-20-ai-message-code-window-and-table-design.md](../product-specs/2026-04-20-ai-message-code-window-and-table-design.md)

**Execution Status:** Completed on 2026-04-20. Implementation and targeted verification finished in the shared worktree; commit steps were intentionally skipped because the worktree already contained unrelated in-flight changes. Manual light/dark UI smoke validation remains for human visual QA.

---

## File Structure

**Create:**
- `client/src/features/chat/components/markdown/markdown-table.ts` — pipe table 规范化与 `<table>` 装饰辅助函数
- `client/src/features/chat/components/markdown/__tests__/markdown-table.test.ts` — 表格规范化边界测试

**Modify:**
- `client/src/features/chat/components/markdown/markdown.tsx` — 接入 table normalization、统一 code window DOM 结构、合并 copy/SQL header 装饰
- `client/src/features/chat/components/markdown/markdown.css` — code window 与 markdown table 的视觉皮肤
- `client/src/features/chat/components/markdown/sql-code-block.ts` — 只输出 SQL 语义 header 内容，移除独立皮肤决定权
- `client/src/features/chat/components/markdown/__tests__/markdown.test.tsx` — code window / SQL / table wrapper 渲染断言
- `client/src/features/chat/components/markdown/__tests__/markdown-stream.test.ts` — 补流式场景下表格渐进降级断言
- `client/src/features/chat/components/turn/reasoning-part.tsx` — 调整 reasoning 内容区，使其与新 code window 间距协调
- `client/src/features/chat/components/turn/__tests__/reasoning-part.test.tsx` — reasoning 中 fenced code 的回归测试

**Untouched:**
- `client/src/features/chat/components/turn/text-part.tsx` — 继续通过 `Markdown` / `PacedMarkdown` 自动获得新渲染能力
- `client/src/features/chat/components/turn/tool-part.tsx` — 不直接改渲染逻辑，依赖 Markdown 层统一收口

---

### Task 1: Add failing regression tests for code windows and markdown tables

**Files:**
- Create: `client/src/features/chat/components/markdown/__tests__/markdown-table.test.ts`
- Modify: `client/src/features/chat/components/markdown/__tests__/markdown.test.tsx`
- Modify: `client/src/features/chat/components/markdown/__tests__/markdown-stream.test.ts`
- Modify: `client/src/features/chat/components/turn/__tests__/reasoning-part.test.tsx`

- [x] **Step 1: Write the failing tests for code window chrome, SQL header, table wrapper, and normalization boundaries**

Add assertions like:

```tsx
it('renders fenced code inside a code window with chrome slots', async () => {
  const { container } = render(<Markdown text={'```powershell\n$env:JAVA_HOME=\"D:/software/java\"\n```'} cacheKey="code-1" />)
  await new Promise((r) => setTimeout(r, 20))
  expect(container.querySelector('[data-component="markdown-code"]')).not.toBeNull()
  expect(container.querySelector('[data-slot="markdown-code-bar"]')).not.toBeNull()
  expect(container.querySelector('[data-slot="markdown-code-language"]')?.textContent).toMatch(/PowerShell/i)
  expect(container.querySelector('[data-slot="markdown-copy-button"]')).not.toBeNull()
})

it('wraps rendered tables with the unified scroll container', async () => {
  const markdown = '| name | value |\\n| --- | --- |\\n| JAVA_HOME | graalvm |'
  const { container } = render(<Markdown text={markdown} cacheKey="table-1" />)
  await screen.findByRole('table')
  expect(container.querySelector('[data-component="markdown-table"]')).not.toBeNull()
  expect(container.querySelector('[data-slot="markdown-table-scroll"] table')).not.toBeNull()
})
```

Create `markdown-table.test.ts` with normalization boundaries:

```ts
it('normalizes ai pipe tables with blank lines around the separator', () => {
  const input = '| Name | Value |\\n\\n| --- | --- |\\n| JAVA_HOME | GraalVM |'
  expect(normalizePipeTables(input)).toContain('| Name | Value |\\n| --- | --- |\\n| JAVA_HOME | GraalVM |')
})

it('does not rewrite code fences or plain paragraphs', () => {
  const input = '```sql\\nselect a | b from t\\n```\\n\\nA | B is plain text'
  expect(normalizePipeTables(input)).toContain('select a | b from t')
  expect(normalizePipeTables(input)).toContain('A | B is plain text')
})
```

Extend `reasoning-part.test.tsx`:

```tsx
it('renders fenced code in reasoning with the shared code window wrapper', async () => {
  const codePart = { ...part, text: '```js\\nconsole.log(1)\\n```' }
  const { container } = render(<ReasoningPart part={codePart} info={info} />)
  await new Promise((r) => setTimeout(r, 20))
  expect(container.querySelector('[data-component="markdown-code"]')).not.toBeNull()
})
```

- [x] **Step 2: Run the targeted tests to verify they fail**

Run:

```bash
cd client && npx vitest run \
  src/features/chat/components/markdown/__tests__/markdown.test.tsx \
  src/features/chat/components/markdown/__tests__/markdown-stream.test.ts \
  src/features/chat/components/markdown/__tests__/markdown-table.test.ts \
  src/features/chat/components/turn/__tests__/reasoning-part.test.tsx
```

Expected:

```text
FAIL  markdown.test.tsx
FAIL  markdown-table.test.ts
FAIL  reasoning-part.test.tsx
```

because the current renderer has no `markdown-code-bar` / table wrapper / normalization helper yet.

- [x] **Step 3: Commit the failing test scaffold**

```bash
git add client/src/features/chat/components/markdown/__tests__/markdown.test.tsx \
  client/src/features/chat/components/markdown/__tests__/markdown-stream.test.ts \
  client/src/features/chat/components/markdown/__tests__/markdown-table.test.ts \
  client/src/features/chat/components/turn/__tests__/reasoning-part.test.tsx
git commit -m "test: cover ai markdown code window and table rendering"
```

Status: Completed. Red-state verification ended at `2 failed files, 6 failed tests, 12 passed`, matching the missing code-window/table-helper behavior. Commit step skipped in the shared dirty worktree.

### Task 2: Introduce markdown table normalization and unified post-render decorators

**Files:**
- Create: `client/src/features/chat/components/markdown/markdown-table.ts`
- Modify: `client/src/features/chat/components/markdown/markdown.tsx`
- Test: `client/src/features/chat/components/markdown/__tests__/markdown-table.test.ts`
- Test: `client/src/features/chat/components/markdown/__tests__/markdown-stream.test.ts`

- [x] **Step 1: Implement the table helper module with narrow normalization rules**

Create `markdown-table.ts` with explicit exported helpers:

```ts
const PIPE_ALIGN_RE = /^\s*\|?(?:\s*:?-{3,}:?\s*\|)+\s*:?-{3,}:?\s*\|?\s*$/

export function normalizePipeTables(text: string): string {
  const lines = text.split('\n')
  const out: string[] = []
  let inFence = false

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (/^\s*```/.test(line)) inFence = !inFence
    if (inFence) {
      out.push(line)
      continue
    }

    const next = lines[i + 1] ?? ''
    const next2 = lines[i + 2] ?? ''
    if (looksLikePipeRow(line) && !next.trim() && PIPE_ALIGN_RE.test(next2)) {
      out.push(line, next2)
      i += 2
      continue
    }

    out.push(line)
  }

  return out.join('\n')
}

export function decorateTables(root: HTMLElement) {
  for (const table of Array.from(root.querySelectorAll('table'))) {
    if (table.parentElement?.getAttribute('data-component') === 'markdown-table') continue
    const shell = document.createElement('div')
    shell.setAttribute('data-component', 'markdown-table')
    const scroll = document.createElement('div')
    scroll.setAttribute('data-slot', 'markdown-table-scroll')
    table.parentNode?.replaceChild(shell, table)
    shell.appendChild(scroll)
    scroll.appendChild(table)
  }
}
```

- [x] **Step 2: Wire normalization and table decoration into `markdown.tsx`**

Update the render path so each block is normalized before `marked.parse`, then decorate both code blocks and tables:

```ts
const normalized = normalizePipeTables(block.src)
const parsed = marked.parse(normalized, { async: false }) as string
```

and after `temp.innerHTML = html`:

```ts
decorateCodeBlocks(temp)
decorateSqlBlocks(temp, handlers)
decorateTables(temp)
```

Also keep `fallback(text)` unchanged so ambiguous input still returns the current plain-text escape path.

- [x] **Step 3: Run the focused markdown tests to verify Task 2 passes**

Run:

```bash
cd client && npx vitest run \
  src/features/chat/components/markdown/__tests__/markdown-table.test.ts \
  src/features/chat/components/markdown/__tests__/markdown-stream.test.ts
```

Expected:

```text
PASS  markdown-table.test.ts
PASS  markdown-stream.test.ts
```

- [x] **Step 4: Commit the markdown table helper work**

```bash
git add client/src/features/chat/components/markdown/markdown-table.ts \
  client/src/features/chat/components/markdown/markdown.tsx \
  client/src/features/chat/components/markdown/__tests__/markdown-table.test.ts \
  client/src/features/chat/components/markdown/__tests__/markdown-stream.test.ts
git commit -m "feat: normalize ai markdown tables"
```

Status: Completed. `cd client && npx vitest run src/features/chat/components/markdown/__tests__/markdown-table.test.ts src/features/chat/components/markdown/__tests__/markdown-stream.test.ts` passed with `8/8` tests green, and `cd client && npx tsc --noEmit` exited with code `0`. Commit step skipped in the shared dirty worktree.

### Task 3: Convert markdown code blocks into the shared desktop-style code window

**Files:**
- Modify: `client/src/features/chat/components/markdown/markdown.tsx`
- Modify: `client/src/features/chat/components/markdown/sql-code-block.ts`
- Modify: `client/src/features/chat/components/markdown/markdown.css`
- Test: `client/src/features/chat/components/markdown/__tests__/markdown.test.tsx`

- [x] **Step 1: Refactor code-block decoration to emit stable chrome slots**

Change the code wrapper DOM so each `pre` becomes:

```ts
const wrapper = document.createElement('div')
wrapper.setAttribute('data-component', 'markdown-code')

const bar = document.createElement('div')
bar.setAttribute('data-slot', 'markdown-code-bar')

const language = document.createElement('span')
language.setAttribute('data-slot', 'markdown-code-language')
language.textContent = formatCodeLanguage(pre.querySelector('code')?.className ?? '')

const actions = document.createElement('div')
actions.setAttribute('data-slot', 'markdown-code-actions')

const btn = document.createElement('button')
btn.setAttribute('data-slot', 'markdown-copy-button')
btn.setAttribute('aria-label', 'Copy')

bar.append(language, actions)
actions.append(btn)
wrapper.append(bar, pre)
```

Use a label map for common languages:

```ts
const LANGUAGE_LABELS: Record<string, string> = {
  js: 'JavaScript',
  ts: 'TypeScript',
  sh: 'Shell',
  bash: 'Bash',
  powershell: 'PowerShell',
  sql: 'SQL',
}
```

- [x] **Step 2: Collapse SQL-specific chrome into the shared action area**

Make `sql-code-block.ts` responsible only for inserting SQL-specific content into the shared code bar:

```ts
const bar = wrapper.querySelector('[data-slot="markdown-code-bar"]')
const actions = wrapper.querySelector('[data-slot="markdown-code-actions"]')

headerBadge.setAttribute('data-slot', 'sql-language-badge')
headerBadge.textContent = kind ? `SQL · ${kind}` : 'SQL'
bar?.prepend(headerBadge)
actions?.prepend(explainBtn)
if (risk === 'L1') actions?.prepend(execBtn)
```

Keep the existing custom events:

```ts
opts.onExecute(sql)
opts.onExplain(sql)
```

so prompt-composer integrations stay intact.

- [x] **Step 3: Implement the visual skin in `markdown.css`**

Replace the minimal `pre` styling with token-driven chrome + paper surface rules:

```css
[data-component="markdown-code"] {
  --code-window-bg: color-mix(in oklab, var(--background) 94%, var(--muted) 6%);
  --code-window-border: color-mix(in oklab, var(--border) 72%, var(--foreground) 8%);
  --code-window-bar: color-mix(in oklab, var(--muted) 72%, var(--background) 28%);
  border: 1px solid var(--code-window-border);
  border-radius: 18px;
  background: var(--code-window-bg);
  box-shadow: 0 10px 30px rgb(0 0 0 / 0.06);
  overflow: hidden;
}

[data-slot="markdown-code-bar"] {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid color-mix(in oklab, var(--code-window-border) 86%, transparent);
  background: var(--code-window-bar);
}

.dark [data-component="markdown-code"] {
  --code-window-bg: oklch(0.97 0.004 95);
  --code-window-bar: oklch(0.93 0.005 95);
}
```

and keep inline code separate:

```css
[data-component="markdown"] :not(pre) > code {
  border-radius: 999px;
  padding: 0.14rem 0.45rem;
}
```

- [x] **Step 4: Run the markdown renderer tests to verify the shared code window passes**

Run:

```bash
cd client && npx vitest run src/features/chat/components/markdown/__tests__/markdown.test.tsx
```

Expected:

```text
PASS  markdown.test.tsx
```

- [x] **Step 5: Commit the code window refactor**

```bash
git add client/src/features/chat/components/markdown/markdown.tsx \
  client/src/features/chat/components/markdown/sql-code-block.ts \
  client/src/features/chat/components/markdown/markdown.css \
  client/src/features/chat/components/markdown/__tests__/markdown.test.tsx
git commit -m "feat: add desktop-style ai code window"
```

Status: Completed. `cd client && npx vitest run src/features/chat/components/markdown/__tests__/markdown.test.tsx` passed with `6/6` tests green, and `cd client && npx tsc --noEmit` exited with code `0`. Commit step skipped in the shared dirty worktree.

### Task 4: Align markdown tables and reasoning surfaces with the new structured-content skin

**Files:**
- Modify: `client/src/features/chat/components/markdown/markdown.css`
- Modify: `client/src/features/chat/components/turn/reasoning-part.tsx`
- Test: `client/src/features/chat/components/turn/__tests__/reasoning-part.test.tsx`
- Test: `client/src/features/chat/components/markdown/__tests__/markdown.test.tsx`

- [x] **Step 1: Add the markdown table surface styling**

Append token-driven table rules:

```css
[data-component="markdown-table"] {
  margin: 0.9rem 0;
  border: 1px solid color-mix(in oklab, var(--border) 88%, transparent);
  border-radius: 16px;
  background: color-mix(in oklab, var(--background) 96%, var(--muted) 4%);
  overflow: hidden;
}

[data-slot="markdown-table-scroll"] {
  overflow-x: auto;
}

[data-component="markdown-table"] th {
  background: color-mix(in oklab, var(--muted) 82%, var(--background) 18%);
  font-weight: 600;
}

[data-component="markdown-table"] tr:hover td {
  background: color-mix(in oklab, var(--accent) 52%, transparent);
}
```

Ensure the selectors only target wrapped markdown tables, not the app’s data-grid components.

- [x] **Step 2: Reduce reasoning wrapper styling so embedded code windows sit cleanly**

Update `reasoning-part.tsx` from the heavy left-border wrapper:

```tsx
<div className="mt-3 mb-2 ml-2 rounded-2xl border border-border/50 bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
  {isPartStreaming ? (
    <PacedMarkdown text={text} cacheKey={part.id} streaming />
  ) : (
    <Markdown text={text} cacheKey={part.id} />
  )}
</div>
```

This keeps reasoning content visually grouped while letting the internal code window become the dominant surface.

- [x] **Step 3: Run reasoning and markdown tests to verify no regression**

Run:

```bash
cd client && npx vitest run \
  src/features/chat/components/turn/__tests__/reasoning-part.test.tsx \
  src/features/chat/components/markdown/__tests__/markdown.test.tsx
```

Expected:

```text
PASS  reasoning-part.test.tsx
PASS  markdown.test.tsx
```

- [x] **Step 4: Commit the reasoning and table polish**

```bash
git add client/src/features/chat/components/markdown/markdown.css \
  client/src/features/chat/components/turn/reasoning-part.tsx \
  client/src/features/chat/components/turn/__tests__/reasoning-part.test.tsx \
  client/src/features/chat/components/markdown/__tests__/markdown.test.tsx
git commit -m "style: unify ai markdown tables and reasoning surfaces"
```

Status: Completed. `cd client && npx vitest run src/features/chat/components/turn/__tests__/reasoning-part.test.tsx src/features/chat/components/markdown/__tests__/markdown.test.tsx` passed with `12/12` tests green, and `cd client && npx tsc --noEmit` exited with code `0`. Commit step skipped in the shared dirty worktree.

### Task 5: Run consolidated frontend verification and completion housekeeping

**Files:**
- Modify: `docs/exec-plans/2026-04-20-ai-message-code-window-and-table-plan.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/product-specs/index.md` (only if the shipped result changes the summary wording)
- Modify: any canonical frontend doc affected by new renderer conventions (`docs/FRONTEND.md` only if a reusable markdown rendering convention is introduced)

- [x] **Step 1: Run the full targeted frontend suite and type-check**

Run:

```bash
cd client && npx vitest run \
  src/features/chat/components/markdown/__tests__/markdown.test.tsx \
  src/features/chat/components/markdown/__tests__/markdown-stream.test.ts \
  src/features/chat/components/markdown/__tests__/markdown-table.test.ts \
  src/features/chat/components/turn/__tests__/reasoning-part.test.tsx

cd client && npx tsc --noEmit
```

Expected:

```text
All targeted vitest files PASS
Type check passes with zero output
```

- [x] **Step 2: Perform manual smoke validation in both light and dark themes**

Validate:

```text
1. assistant fenced code renders a rounded window with a visible top chrome bar
2. dark theme still shows a light paper-like code window rather than a dark code block
3. SQL code blocks still expose “执行 / 解释” in the shared action area
4. markdown pipe tables render as wrapped tables with horizontal scroll
5. incomplete streaming table text does not crash or corrupt the message body
```

- [x] **Step 3: Mark the plan complete and move the index entry to Completed**

Update this file so every executed checkbox is marked, note the final verification commands, and move the plan entry in `docs/exec-plans/index.md` from **活跃计划** to **已完成计划** with the completion date and one-line summary.

- [x] **Step 4: Commit the verified implementation and plan housekeeping**

```bash
git add client/src/features/chat/components/markdown/markdown.tsx \
  client/src/features/chat/components/markdown/markdown.css \
  client/src/features/chat/components/markdown/sql-code-block.ts \
  client/src/features/chat/components/markdown/markdown-table.ts \
  client/src/features/chat/components/markdown/__tests__/markdown.test.tsx \
  client/src/features/chat/components/markdown/__tests__/markdown-stream.test.ts \
  client/src/features/chat/components/markdown/__tests__/markdown-table.test.ts \
  client/src/features/chat/components/turn/reasoning-part.tsx \
  client/src/features/chat/components/turn/__tests__/reasoning-part.test.tsx \
  docs/exec-plans/2026-04-20-ai-message-code-window-and-table-plan.md \
  docs/exec-plans/index.md
git commit -m "feat: unify ai markdown code and table rendering"
```

Status: Completed with noted deviation. Final verification used:

```bash
cd client && npx vitest run \
  src/features/chat/components/markdown/__tests__/markdown.test.tsx \
  src/features/chat/components/markdown/__tests__/markdown-stream.test.ts \
  src/features/chat/components/markdown/__tests__/markdown-table.test.ts \
  src/features/chat/components/turn/__tests__/reasoning-part.test.tsx

cd client && npx tsc --noEmit
```

Observed result: all `4` targeted test files passed (`20/20` tests green) and `tsc` exited with code `0`.

Manual smoke validation note: not run in this terminal-only session; visual QA for light/dark theme rendering, SQL action chrome, and incomplete streaming table behavior is deferred to human inspection.

Documentation housekeeping note: `docs/product-specs/index.md` did not require wording changes, and no broader canonical frontend guideline update was needed beyond this plan/index completion.

Commit step skipped in the shared dirty worktree.
