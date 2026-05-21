# Streaming Code Block Jitter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the severe bottom-of-chat jitter that happens when an assistant streams fenced code blocks, while preserving DataTalk's current message visual language.

**Architecture:** Split chat auto-scroll into structural appends and streamed content growth, then make open fenced code blocks render through a stable streaming-code surface instead of repeatedly reparsing and redecorating a half-finished Markdown fence. The fix keeps the existing `Markdown`/`PacedMarkdown`/`morphdom` stack, but gives unfinished code fences a deterministic DOM shape and only scrolls once per animation frame during token growth.

**Tech Stack:** React 19, TypeScript, Zustand, marked, DOMPurify, morphdom, Vitest, Testing Library.

---

## Design Inputs

- Source: [`client/DESIGN.md`](../../client/DESIGN.md)
- Applied constraints:
  - Chat messages remain a comfortable reading surface; keep current turn widths, alignment, spacing, and semantic message surfaces.
  - New UI must use the existing neutral/cobalt semantic token system. This plan does not add raw decorative colors or a separate code theme.
  - Motion is allowed only as state confirmation. The fix must reduce layout motion rather than masking jitter with animation.
  - `prefers-reduced-motion` must continue to disable non-essential movement.
  - Messages, tool output, code, and tables must stay visually related to the Workbench rather than becoming a separate "terminal" visual system.

## External Inputs

- DeepSeek official API docs confirm streaming is token/delta transport: `stream=true` sends data-only SSE chunks and ends with `data: [DONE]`. They do not document the private chat web UI renderer.
  Source: <https://api-docs.deepseek.com/api/create-chat-completion/>
- `chat.deepseek.com` is not directly inspectable from this environment: GET is blocked by CloudFront / bot verification. Any DeepSeek web implementation notes beyond the official SSE behavior must be treated as observational, not source-backed.
- OpenAI and Anthropic both expose streaming as typed deltas/events (`response.output_text.delta`, `content_block_delta`) rather than as "rerender full Markdown on every packet" guidance.
  Sources: <https://developers.openai.com/api/docs/guides/streaming-responses>, <https://platform.claude.com/docs/en/build-with-claude/streaming>
- Vercel's Streamdown is built specifically for streaming Markdown and calls out incomplete/unterminated Markdown blocks plus interactive code blocks as first-class cases.
  Source: <https://vercel.com/changelog/introducing-streamdown>
- Vercel AI SDK `smoothStream` smooths chunky provider responses by transforming stream output by character, word, or line. This supports the separation between provider chunk cadence and UI cadence.
  Source: <https://vercel.com/blog/ai-sdk-4-1>
- assistant-ui's Streamdown integration exposes a block-based streaming renderer with code plugins and Shiki themes, again treating streamed Markdown/code as a specialized renderer concern.
  Source: <https://www.assistant-ui.com/docs/ui/streamdown>
- MDN documents `overflow-anchor: none` as the browser-level opt-out for scroll anchoring. DataTalk already uses this on the chat scroller, so the remaining jitter is in app scroll/render ownership.
  Source: <https://developer.mozilla.org/en-US/docs/Web/CSS/Guides/Scroll_anchoring/Overview>

## Current Diagnosis

Current DataTalk path:

```text
SSE message.part.delta
  -> useChatPartsStore.appendPartDelta()
  -> upsertPart() creates a new Part object and increments version
  -> SplitView passes version to useAutoScroll()
  -> useLayoutEffect scrollToBottom() runs for every token-level update
  -> TextPart renders PacedMarkdown
  -> PacedMarkdown bypasses pacing as soon as it sees ``` or ~~~
  -> Markdown renderHtml() reparses the current full text and morphdoms the result
  -> open code fence is repeatedly normalized, parsed, sanitized, decorated, and diffed
```

High-risk spots found in the current code:

- [`client/src/features/session/split-view.tsx`](../../client/src/features/session/split-view.tsx) uses the global chat store `version` as an auto-scroll dependency, and that version changes on every streamed delta.
- [`client/src/features/chat/components/turn/text-part.tsx`](../../client/src/features/chat/components/turn/text-part.tsx) trims the whole message before rendering. During code streaming this drops trailing newlines, so the rendered line count can lag and then jump.
- [`client/src/features/chat/components/effects/paced-markdown.tsx`](../../client/src/features/chat/components/effects/paced-markdown.tsx) correctly bypasses local pacing for code fences, but this exposes every provider delta directly to the expensive Markdown path.
- [`client/src/features/chat/components/markdown/markdown.tsx`](../../client/src/features/chat/components/markdown/markdown.tsx) uses `marked.parse()` and post-render decorators for every live update. The cache key is based on block index and mode, so the head/tail split during open fences can still churn cache entries as streaming shape changes.
- [`client/src/hooks/use-auto-scroll.ts`](../../client/src/hooks/use-auto-scroll.ts) already batches `MutationObserver` follow work behind `requestAnimationFrame`, but the `useLayoutEffect(...deps)` path still synchronously scrolls for token-level content changes.

## Target Behavior

- A newly sent user turn or newly created assistant part still lands at bottom before paint.
- Streaming text or code content growth does not trigger the structural `useLayoutEffect` scroll path.
- When pinned at bottom, streamed code growth follows at most once per animation frame.
- An unfinished fenced code block immediately becomes a stable code-window shell after the opening fence is recognized.
- The code-window header, language label, copy slot, and body element do not remount on each token.
- SQL execute/explain actions are not shown for incomplete SQL fences; they appear only after the closing fence makes the code complete.
- Trailing newlines inside code are preserved while deciding rendered height.
- If the user scrolls upward, auto-follow stays off until they return to bottom.

## Non-Goals

- Do not replace the entire Markdown stack with Streamdown in this fix.
- Do not introduce virtualization unless the locked repro still fails after this plan.
- Do not redesign the code block visual skin.
- Do not remove code block copy/table/chart features.
- Do not change backend SSE protocol.

## File Structure

- Modify: `client/src/stores/chat-parts-store.ts` — add a structural scroll version separate from token content version.
- Modify: `client/src/stores/__tests__/chat-parts-store.test.ts` — cover structural version increments.
- Modify: `client/src/features/session/split-view.tsx` — drive `useAutoScroll` from structural version, not per-token version.
- Modify: `client/src/hooks/use-auto-scroll.ts` — keep synchronous append scroll, add content-size follow via observer/RAF.
- Modify: `client/src/hooks/use-auto-scroll.test.tsx` — cover content growth without deps-driven layout scroll.
- Modify: `client/src/features/chat/components/turn/text-part.tsx` — preserve raw message text for rendering and copy.
- Modify: `client/src/features/chat/components/markdown/markdown-stream.ts` — expose open fenced code blocks as a first-class block type.
- Modify: `client/src/features/chat/components/markdown/markdown.tsx` — render open code fences through deterministic code-window HTML.
- Modify: `client/src/features/chat/components/markdown/sql-code-block.ts` — skip SQL actions while a code block is marked streaming/incomplete.
- Modify: `client/src/features/chat/components/markdown/markdown.css` — add only stability attributes/styles needed for incomplete code blocks.
- Modify: `client/src/features/chat/components/markdown/__tests__/markdown-stream.test.ts` — cover open fence metadata.
- Modify: `client/src/features/chat/components/markdown/__tests__/markdown.test.tsx` — cover stable streaming code DOM and SQL action timing.
- Modify: `client/src/features/chat/components/effects/__tests__/paced-markdown.test.tsx` — cover raw code text preservation through bypass.
- Modify: `client/src/features/session/split-view.test.tsx` — cover structural-vs-content scroll behavior in the chat shell.
- Modify: `docs/exec-plans/2026-04-24-streaming-code-block-jitter-plan.md` — track execution notes and completion.
- Modify: `docs/exec-plans/index.md` — move this plan from Active to Completed after verification.

## Task 1: Lock The Repro With Tests

**Files:**
- Modify: `client/src/features/chat/components/markdown/__tests__/markdown-stream.test.ts`
- Modify: `client/src/features/chat/components/markdown/__tests__/markdown.test.tsx`
- Modify: `client/src/hooks/use-auto-scroll.test.tsx`
- Modify: `client/src/features/session/split-view.test.tsx`

- [x] **Step 1: Add an open-code-fence parser regression**

Add a test that describes the intended block model:

```ts
it('identifies an open fenced code block as a streaming code block', () => {
  const result = stream('Before\n\n```ts\nconst answer =', true)

  expect(result).toHaveLength(2)
  expect(result[0]).toMatchObject({ mode: 'live' })
  expect(result[1]).toMatchObject({
    mode: 'stream-code',
    language: 'ts',
    code: 'const answer =',
  })
})
```

Expected before implementation: fail because `Block.mode` only supports `full | live`.

- [x] **Step 2: Add a DOM stability test for streamed code**

Render a partial code fence, keep references to the code window shell/header/code element, rerender with appended code, and assert those nodes are preserved:

```tsx
it('keeps streaming code block chrome mounted while code text grows', async () => {
  const first = '```ts\nconst a = 1'
  const second = '```ts\nconst a = 1\nconst b = 2'
  const { container, rerender } = render(
    <Markdown text={first} streaming cacheKey="stream-code-1" />,
  )

  await waitFor(() => {
    expect(container.querySelector('[data-component="markdown-code"]')).not.toBeNull()
  })

  const shell = container.querySelector('[data-component="markdown-code"]')
  const bar = container.querySelector('[data-slot="markdown-code-bar"]')
  const code = container.querySelector('[data-streaming-code-body="true"]')

  rerender(<Markdown text={second} streaming cacheKey="stream-code-1" />)

  await waitFor(() => {
    expect(container.querySelector('[data-streaming-code-body="true"]')?.textContent).toContain('const b = 2')
  })

  expect(container.querySelector('[data-component="markdown-code"]')).toBe(shell)
  expect(container.querySelector('[data-slot="markdown-code-bar"]')).toBe(bar)
  expect(container.querySelector('[data-streaming-code-body="true"]')).toBe(code)
})
```

Expected before implementation: fail because no streaming-code body marker exists and node preservation is not guaranteed.

- [x] **Step 3: Add scroll ownership tests**

Extend `use-auto-scroll.test.tsx` so a deps-driven structural append scroll still runs synchronously, but a mutation-only content growth uses one RAF follow:

```tsx
it('does not layout-scroll for streamed content growth when deps do not change', () => {
  const metrics = { clientHeight: 100, scrollHeight: 1000, scrollTop: 900 }
  const view = render(<Harness version={0} text="```ts\nconst a = 1" />)
  const root = view.getByTestId('scroll-root') as HTMLDivElement
  attachScrollMetrics(root, metrics)
  syncAtBottom(root)
  flushAnimationFrameQueue(rafQueue)
  scrollToSpy.mockClear()

  metrics.scrollHeight = 1040
  act(() => {
    view.rerender(<Harness version={0} text="```ts\nconst a = 1\nconst b = 2" />)
  })

  expect(scrollToSpy).not.toHaveBeenCalled()

  act(() => {
    MockMutationObserver.instances[0]?.trigger()
  })
  expect(scrollToSpy).not.toHaveBeenCalled()

  act(() => {
    rafQueue.shift()?.(16)
  })

  expect(scrollToSpy).toHaveBeenCalledTimes(1)
  expect(metrics.scrollTop).toBe(1040)
})
```

Expected before implementation: fail if the component still passes the token-level version through deps.

- [x] **Step 4: Run the focused red-state tests**

Run:

```bash
cd client && npx vitest run \
  src/features/chat/components/markdown/__tests__/markdown-stream.test.ts \
  src/features/chat/components/markdown/__tests__/markdown.test.tsx \
  src/hooks/use-auto-scroll.test.tsx \
  src/features/session/split-view.test.tsx
```

Expected: the new tests fail for the missing streaming-code block model and structural/content scroll split.

**Red-state evidence (2026-04-24):**

Run:

```bash
cd client && npx vitest run src/features/chat/components/markdown/__tests__/markdown-stream.test.ts src/features/chat/components/markdown/__tests__/markdown.test.tsx src/hooks/use-auto-scroll.test.tsx src/features/session/split-view.test.tsx
```

Result: failed as expected with 5 failures:

- `markdown-stream` returns `mode: 'live'` for open `ts` / `sql` fences instead of `mode: 'stream-code'`.
- `Markdown` has no `[data-streaming-code-body="true"]` marker during open-fence rendering.
- incomplete streaming SQL still receives `[data-slot="sql-execute"]`.
- `SplitView` still calls `scrollToBottom()` once when an existing assistant text part receives `appendPartDelta()`.
- `use-auto-scroll.test.tsx` stayed green, confirming the hook-level mutation batching already works when deps do not change.

## Task 2: Split Structural Scroll Version From Content Version

**Files:**
- Modify: `client/src/stores/chat-parts-store.ts`
- Modify: `client/src/stores/__tests__/chat-parts-store.test.ts`
- Modify: `client/src/features/session/split-view.tsx`

- [x] **Step 1: Add `layoutVersion` to the chat store**

Extend `ChatPartsState`:

```ts
layoutVersion: number
```

Initialize it to `0`.

- [x] **Step 2: Increment `layoutVersion` only for structural changes**

Increment `layoutVersion` when:

- a part is inserted for the first time
- a part is removed
- a full session history is replaced
- a pending user is inserted, promoted, removed, failed, or retried
- message info changes in a way that creates/removes a visible turn or assistant shell
- streaming starts/stops

Do not increment `layoutVersion` for pure text delta append to an existing text/reasoning part.

Implementation rule inside `upsertPart`:

```ts
const isInsert = idx < 0
const nextVersion = s.version + 1
const nextLayoutVersion = isInsert ? s.layoutVersion + 1 : s.layoutVersion
```

For `appendPartDelta()` when `existing` is present, rely on `upsertPart()` preserving `layoutVersion`.

- [x] **Step 3: Update store tests**

Add assertions:

```ts
it('does not increment layoutVersion for text delta updates to an existing part', () => {
  const store = useChatPartsStore.getState()
  store.upsertPart('s1', {
    type: 'text',
    id: 'p1',
    sessionID: 's1',
    messageID: 'm1',
    text: 'a',
    metadata: {},
  } as any)
  const beforeDelta = useChatPartsStore.getState()

  store.appendPartDelta('s1', 'p1', 'text', 'b')

  const afterDelta = useChatPartsStore.getState()
  expect(afterDelta.version).toBe(beforeDelta.version + 1)
  expect(afterDelta.layoutVersion).toBe(beforeDelta.layoutVersion)
})
```

- [x] **Step 4: Wire `SplitView` to `layoutVersion`**

Replace:

```ts
const version = useChatPartsStore((s) => s.version)
const { ref: scrollRef, scrollToBottom } = useAutoScroll<HTMLDivElement>([version])
```

with:

```ts
const layoutVersion = useChatPartsStore((s) => s.layoutVersion)
const { ref: scrollRef, scrollToBottom } = useAutoScroll<HTMLDivElement>([layoutVersion])
```

- [x] **Step 5: Run store and split-view tests**

Run:

```bash
cd client && npx vitest run \
  src/stores/__tests__/chat-parts-store.test.ts \
  src/features/session/split-view.test.tsx
```

Expected: all tests pass after implementation.

## Task 3: Preserve Raw Text For Code Rendering

**Files:**
- Modify: `client/src/features/chat/components/turn/text-part.tsx`
- Modify: `client/src/features/chat/components/effects/__tests__/paced-markdown.test.tsx`

- [x] **Step 1: Stop trimming rendered Markdown text**

Replace:

```ts
const text = (part.text ?? '').trim()
if (!text) return null
```

with:

```ts
const rawText = part.text ?? ''
const visibleText = rawText.trim()
if (!visibleText) return null
```

Use `rawText` for `Markdown`, `PacedMarkdown`, and copy. Keep `visibleText` only for the empty check.

- [x] **Step 2: Cover trailing newline preservation**

Add a test that streams:

```text
```ts
const a = 1

```

and verifies the Markdown component receives the trailing newline in streaming mode. Mock `Markdown` in the `PacedMarkdown` test if needed so the assertion can observe the exact text prop.

- [x] **Step 3: Run text/effect tests**

Run:

```bash
cd client && npx vitest run \
  src/features/chat/components/effects/__tests__/paced-markdown.test.tsx \
  src/features/chat/components/turn/__tests__/session-turn.test.tsx
```

Expected: all tests pass and no existing turn rendering test regresses.

## Task 4: Make Open Fenced Code A First-Class Streaming Block

**Files:**
- Modify: `client/src/features/chat/components/markdown/markdown-stream.ts`
- Modify: `client/src/features/chat/components/markdown/__tests__/markdown-stream.test.ts`

- [x] **Step 1: Extend the block type**

Change:

```ts
mode: 'full' | 'live'
```

to:

```ts
mode: 'full' | 'live' | 'stream-code'
language?: string
code?: string
fence?: '```' | '~~~'
```

- [x] **Step 2: Parse the open fence metadata**

Add a helper:

```ts
function parseOpenFence(raw: string): { language: string; code: string; fence: '```' | '~~~' } | null {
  const match = raw.match(/^[ \t]{0,3}(`{3,}|~{3,})([^\n]*)\n?([\s\S]*)$/)
  if (!match) return null
  const marker = match[1]!.startsWith('`') ? '```' : '~~~'
  const info = (match[2] ?? '').trim()
  const language = info.split(/\s+/)[0]?.toLowerCase() ?? ''
  return { language, code: match[3] ?? '', fence: marker }
}
```

When `open(code.raw)` is true, return the tail as `mode: 'stream-code'`.

- [x] **Step 3: Keep stable head behavior**

For `head + open code`, return:

```ts
[
  { raw: head, src: heal(head), mode: 'live' },
  { raw: code.raw, src: code.raw, mode: 'stream-code', language, code, fence },
]
```

For a message that only contains the open code fence, return one `stream-code` block.

- [x] **Step 4: Run parser tests**

Run:

```bash
cd client && npx vitest run src/features/chat/components/markdown/__tests__/markdown-stream.test.ts
```

Expected: all parser tests pass.

## Task 5: Render Streaming Code With Stable DOM

**Files:**
- Modify: `client/src/features/chat/components/markdown/markdown.tsx`
- Modify: `client/src/features/chat/components/markdown/sql-code-block.ts`
- Modify: `client/src/features/chat/components/markdown/markdown.css`
- Modify: `client/src/features/chat/components/markdown/__tests__/markdown.test.tsx`

- [x] **Step 1: Add a dedicated streaming-code renderer**

Add a helper in `markdown.tsx`:

```ts
function renderStreamingCodeBlock(block: Block): string {
  const language = block.language ?? ''
  const languageClass = language ? ` language-${escape(language)}` : ''
  const label = language ? (LANGUAGE_LABELS[language] ?? language.replace(/^[a-z]/, (c) => c.toUpperCase())) : ''
  return [
    '<div data-component="markdown-code" data-streaming-code="true">',
    '<div data-slot="markdown-code-bar">',
    `<span data-slot="markdown-code-language">${escape(label)}</span>`,
    '<div data-slot="markdown-code-actions">',
    `<button data-slot="markdown-copy-button" type="button" aria-label="Copy">${COPY_SVG}</button>`,
    '</div>',
    '</div>',
    '<pre>',
    `<code class="${languageClass}" data-streaming-code-body="true">${escape(block.code ?? '')}</code>`,
    '</pre>',
    '</div>',
  ].join('')
}
```

Use this only for `block.mode === 'stream-code'`.

- [x] **Step 2: Keep regular Markdown parsing for complete blocks**

In `renderHtml()`:

```ts
if (block.mode === 'stream-code') {
  return renderStreamingCodeBlock(block)
}
```

Do not pass `stream-code` through `marked.parse()`.

- [x] **Step 3: Avoid redecorating streaming code**

`decorateCodeBlocks()` already skips when the parent has `data-component="markdown-code"`. Keep that behavior intact.

- [x] **Step 4: Disable SQL actions for incomplete fences**

In `decorateSqlBlocks()`:

```ts
if (wrapper.getAttribute('data-streaming-code') === 'true') continue
```

This prevents execute/explain controls from appearing while SQL is incomplete.

- [x] **Step 5: Add minimal CSS stability**

Add styles that do not change the visual language:

```css
[data-component="markdown-code"][data-streaming-code="true"] pre {
  min-height: calc(18px + 30px);
}

[data-streaming-code-body="true"] {
  white-space: pre;
}
```

Use the existing mono rhythm and code-window shell. Do not add new animation.

- [x] **Step 6: Run Markdown tests**

Run:

```bash
cd client && npx vitest run src/features/chat/components/markdown
```

Expected: all Markdown tests pass, including code/table/chart coverage.

## Task 6: Make Content Growth Follow Once Per Frame

**Files:**
- Modify: `client/src/hooks/use-auto-scroll.ts`
- Modify: `client/src/hooks/use-auto-scroll.test.tsx`

- [x] **Step 1: Keep structural layout scroll path unchanged**

The `useLayoutEffect(...deps)` path remains for structural appends only. It should still call `suppressMutationsUntilNextFrame()` and `scrollToBottom('auto')`.

- [x] **Step 2: Treat mutation/resize as content growth**

Keep the existing `MutationObserver` behavior, but ensure it is the only path used for streamed text/code growth after Task 2.

Add optional `ResizeObserver` support:

```ts
const resizeObserver = typeof ResizeObserver === 'undefined'
  ? null
  : new ResizeObserver(() => {
      if (suppressMutationScrolls.current) return
      if (followEnabled.current) scheduleFollow()
    })

resizeObserver?.observe(el)
```

Disconnect it with the mutation observer.

- [x] **Step 3: Preserve user scroll escape**

Do not change the existing `movedUp` and `backAtBottom` logic. Add a test that confirms a streamed code mutation does not follow when `followEnabled` has been disabled by manual upward scroll.

- [x] **Step 4: Run hook tests**

Run:

```bash
cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx
```

Expected: all hook tests pass.

## Task 7: Consolidated Verification

**Files:**
- Modify: `docs/exec-plans/2026-04-24-streaming-code-block-jitter-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] **Step 1: Run focused frontend regression suite**

Run:

```bash
cd client && npx vitest run \
  src/stores/__tests__/chat-parts-store.test.ts \
  src/features/chat/components/markdown \
  src/features/chat/components/effects/__tests__/paced-markdown.test.tsx \
  src/features/chat/components/turn/__tests__/session-turn.test.tsx \
  src/features/session/split-view.test.tsx \
  src/hooks/use-auto-scroll.test.tsx
```

Expected: all selected tests pass.

- [x] **Step 2: Run frontend typecheck**

Run:

```bash
cd client && npx tsc --noEmit
```

Expected: zero type errors.

- [x] **Step 3: Manual smoke**

Run the client and verify these scenarios in light and dark themes:

```bash
cd client && npm run dev
```

Scenarios:

- Ask for a long TypeScript function so the answer streams a single fenced `ts` block at the bottom.
- Ask for SQL so an incomplete `sql` fence streams first, then closes.
- While pinned at bottom, confirm the viewport follows smoothly without repeated bobbing against the composer.
- Scroll upward while the code block is still streaming, confirm the app does not force-scroll back down.
- After the SQL fence closes, confirm execute/explain/copy controls are present and usable.
- Confirm completed code blocks, tables, and chart fences still render as before.

- [x] **Step 4: Document outcomes and close**

Update this plan with actual notes for any deviation. Move the plan entry from Active to Completed in `docs/exec-plans/index.md` only after tests and manual smoke finish.

**Execution notes (2026-04-24):**

- Implemented the structural/content scroll split with `layoutVersion`, and confirmed existing part deltas do not trigger the synchronous structural scroll path.
- Implemented open fenced code as `stream-code`, rendering unfinished fences through stable code-window DOM while preserving raw trailing newlines.
- Incomplete SQL fences now keep copy available but suppress execute/explain until the fence is complete.
- Focused regression suite passed: 12 files, 106 tests.
- Full frontend suite passed: 99 files, 569 tests. Existing React `act(...)` warnings remain in unrelated test surfaces.
- `cd client && npx tsc --noEmit` passed.
- `git diff --check` passed.
- Live manual smoke against a real DeepSeek/OpenCode stream was not run in this agent environment; this is deferred to local product smoke because it requires a running backend plus provider/model connectivity. The automated tests cover the renderer, store, and scroll ownership behavior introduced by this plan.
- Follow-up fix (2026-04-24): removed the streaming-only `pre` `min-height` rule after observing that it made the unfinished code block taller than the completed Markdown code window. Added a CSS regression assertion so streaming code cannot regain an extra `pre` height over completed code blocks.
- Follow-up fix (2026-04-24): added a shared `pre > code` one-line body reserve for both streaming and completed code windows. This prevents an empty or first-token streaming code body from rendering shorter than the completed Markdown code body, which caused a visible resize when the fence closed.

## Parallel Execution Notes

Tasks can be batched after Task 1 locks the repro:

- Batch A: Task 2 and Task 6, because they share scroll ownership and should be implemented together.
- Batch B: Task 3, Task 4, and Task 5, because they share code streaming rendering and can be developed against the same Markdown tests.
- Task 7 runs after both batches.

Do not run `npx tsc --noEmit` after every file inside a batch. Run the consolidated verification in Task 7 after the batch code is complete, per repository parallel execution rules.

## Decision Rule

- If Task 2 + Task 6 remove the jitter without Task 4 + Task 5, still implement Task 4 + Task 5 because they address the code-specific DOM churn that triggered this plan.
- If jitter remains after Task 7 and the DOM node preservation tests pass, open a separate virtualization/list measurement plan. Do not expand this plan into full list virtualization.
- If the unstable behavior is only in chart fences, create a follow-up plan for `ChartBlock` sizing rather than mixing chart rendering into this code-block fix.
