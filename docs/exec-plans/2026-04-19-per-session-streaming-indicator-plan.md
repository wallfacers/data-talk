# Per-Session Streaming Indicator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lift `useChannel().isStreaming` from hook-local state into `chat-parts-store.streamingBySession: Set<string>` so the composer's "还在跑" indicator follows the active session correctly across switches.

**Architecture:** Minimal store slice. Add `streamingBySession` + `setStreaming` to the existing `chat-parts-store`; rewrite `useChannel` to read from store (keyed by `activeSessionId`) and drive set/clear in `sendMessage` / `retryPendingUser` try/finally. No SSE / backend / API changes.

**Tech Stack:** Zustand, React 19, Vitest, TanStack Query (untouched but deps preserved).

---

## Reference

- Spec: `docs/product-specs/2026-04-19-per-session-streaming-indicator-design.md`
- Files in scope:
  - Modify: `client/src/stores/chat-parts-store.ts`
  - Modify: `client/src/services/channel/use-channel.ts`
  - Modify: `client/src/stores/chat-parts-store.test.ts` (add slice tests)
  - Modify or create: `client/src/services/channel/use-channel.test.ts` (add multi-session cases)
- Not modified (intentionally):
  - `channel-client.ts` — no AbortSignal change
  - `use-session-subscribe.ts` — tech debt TD-MULTI-SESSION-SSE-POOL (see Task 4)
  - `prompt-composer.tsx` / `user-bubble.tsx` / `use-pending-prompt-resume.ts` — inherit the new semantics via `useChannel()` without API shape change

---

## Task 1 — Add `streamingBySession` slice to chat-parts-store (TDD)

**Files:**
- Modify: `client/src/stores/chat-parts-store.ts`
- Modify: `client/src/stores/chat-parts-store.test.ts`

- [ ] **Step 1: Write failing tests for the new slice**

Append to `client/src/stores/chat-parts-store.test.ts` (inside the existing `describe('useChatPartsStore', ...)` block or a new one):

```typescript
describe('streamingBySession', () => {
  beforeEach(() => {
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
    })
  })

  it('setStreaming(on=true) marks the session as streaming', () => {
    useChatPartsStore.getState().setStreaming('A', true)
    expect(useChatPartsStore.getState().streamingBySession.has('A')).toBe(true)
  })

  it('setStreaming(on=false) clears the session', () => {
    useChatPartsStore.getState().setStreaming('A', true)
    useChatPartsStore.getState().setStreaming('A', false)
    expect(useChatPartsStore.getState().streamingBySession.has('A')).toBe(false)
  })

  it('streaming flags for different sessions are independent', () => {
    const { setStreaming } = useChatPartsStore.getState()
    setStreaming('A', true)
    setStreaming('B', true)
    expect(useChatPartsStore.getState().streamingBySession.has('A')).toBe(true)
    expect(useChatPartsStore.getState().streamingBySession.has('B')).toBe(true)
    setStreaming('A', false)
    expect(useChatPartsStore.getState().streamingBySession.has('A')).toBe(false)
    expect(useChatPartsStore.getState().streamingBySession.has('B')).toBe(true)
  })

  it('clearSession also removes the streaming flag', () => {
    useChatPartsStore.getState().setStreaming('A', true)
    useChatPartsStore.getState().clearSession('A')
    expect(useChatPartsStore.getState().streamingBySession.has('A')).toBe(false)
  })
})
```

- [ ] **Step 2: Run tests, confirm they fail**

Run: `cd client && npx vitest run src/stores/chat-parts-store.test.ts`
Expected: 4 new tests FAIL with "setStreaming is not a function" / "streamingBySession is undefined".

- [ ] **Step 3: Add the slice to the store**

In `client/src/stores/chat-parts-store.ts`:

(a) Extend the `ChatPartsState` type with the new field and method:

```typescript
type ChatPartsState = {
  partsBySession: Map<string, Map<string, Part[]>>
  infoBySession: Map<string, Map<string, MessageInfo>>
  partIndexBySession: Map<string, Map<string, { messageId: string; idx: number }>>
  streamingBySession: Set<string>

  upsertPart: (sessionId: string, part: Part) => void
  upsertInfo: (sessionId: string, info: MessageInfo) => void
  upsertMany: (sessionId: string, parts: Part[]) => void
  replaceSession: (sessionId: string, list: Array<{ info: MessageInfo; parts: Part[] }>) => void
  removePart: (sessionId: string, messageId: string, partId: string) => void
  clearSession: (sessionId: string) => void
  getParts: (sessionId: string) => Part[]
  findPart: (sessionId: string, partId: string) => Part | null
  setStreaming: (sessionId: string, on: boolean) => void

  upsertPendingUser: (sessionId: string, text: string) => string
  promotePendingUser: (sessionId: string, pendingId: string, realId: string) => void
  markPendingUserFailed: (sessionId: string, pendingId: string, reason: string) => void
  removePendingUser: (sessionId: string, pendingId: string) => void
}
```

(b) Initialize the field in `create<ChatPartsState>((set, get) => ({ ... }))`:

```typescript
streamingBySession: new Set<string>(),
```

(c) Implement `setStreaming`:

```typescript
setStreaming: (sessionId, on) => set((s) => {
  const next = new Set(s.streamingBySession)
  if (on) next.add(sessionId)
  else next.delete(sessionId)
  return { streamingBySession: next }
}),
```

(d) Update the existing `clearSession` to also drop the streaming flag. Locate:

```typescript
clearSession: (sessionId) => set((s) => {
  const parts = new Map(s.partsBySession); parts.delete(sessionId)
  const info = new Map(s.infoBySession); info.delete(sessionId)
  const index = new Map(s.partIndexBySession); index.delete(sessionId)
  return { partsBySession: parts, infoBySession: info, partIndexBySession: index }
}),
```

Replace with:

```typescript
clearSession: (sessionId) => set((s) => {
  const parts = new Map(s.partsBySession); parts.delete(sessionId)
  const info = new Map(s.infoBySession); info.delete(sessionId)
  const index = new Map(s.partIndexBySession); index.delete(sessionId)
  const streaming = new Set(s.streamingBySession); streaming.delete(sessionId)
  return { partsBySession: parts, infoBySession: info, partIndexBySession: index, streamingBySession: streaming }
}),
```

- [ ] **Step 4: Run tests, confirm they pass**

Run: `cd client && npx vitest run src/stores/chat-parts-store.test.ts`
Expected: all tests PASS (pre-existing + 4 new).

- [ ] **Step 5: tsc check**

Run: `cd client && npx tsc --noEmit`
Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add client/src/stores/chat-parts-store.ts client/src/stores/chat-parts-store.test.ts
git commit -m "$(cat <<'EOF'
feat(store): streamingBySession slice with set/clear and clearSession cleanup

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2 — Migrate `useChannel.isStreaming` to store (TDD)

**Files:**
- Modify: `client/src/services/channel/use-channel.ts`
- Modify: `client/src/services/channel/use-channel.test.ts`

- [ ] **Step 1: Write failing tests for the new multi-session behavior**

In `client/src/services/channel/use-channel.test.ts`, add:

```typescript
import { renderHook, act } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import { useChannel } from './use-channel'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useSessionStore } from '@/stores/session-store'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'

const wrapper = ({ children }: { children: ReactNode }) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return createElement(QueryClientProvider, { client: qc }, children)
}

describe('useChannel.isStreaming (per-session)', () => {
  beforeEach(() => {
    useChatPartsStore.setState({ streamingBySession: new Set<string>() })
    useSessionStore.setState({ activeSessionId: null })
  })

  it('reflects the active session and flips when active switches', () => {
    const { result, rerender } = renderHook(() => useChannel(), { wrapper })

    act(() => { useSessionStore.setState({ activeSessionId: 'A' }) })
    rerender()
    expect(result.current.isStreaming).toBe(false)

    act(() => { useChatPartsStore.getState().setStreaming('A', true) })
    rerender()
    expect(result.current.isStreaming).toBe(true)

    act(() => { useSessionStore.setState({ activeSessionId: 'B' }) })
    rerender()
    expect(result.current.isStreaming).toBe(false)

    act(() => { useChatPartsStore.getState().setStreaming('B', true) })
    rerender()
    expect(result.current.isStreaming).toBe(true)

    act(() => { useSessionStore.setState({ activeSessionId: 'A' }) })
    rerender()
    expect(result.current.isStreaming).toBe(true)  // A still streaming
  })

  it('returns false when activeSessionId is null regardless of store state', () => {
    useChatPartsStore.getState().setStreaming('A', true)
    const { result } = renderHook(() => useChannel(), { wrapper })
    expect(result.current.isStreaming).toBe(false)
  })
})
```

If a minimal `useChannel.test.ts` already exists (from Phase 0 of AI message rendering), append these two `describe/it` blocks — do not overwrite existing tests. If not, create the file with the imports shown.

- [ ] **Step 2: Run tests, confirm they fail**

Run: `cd client && npx vitest run src/services/channel/use-channel.test.ts`
Expected: 2 new tests FAIL (isStreaming doesn't react to store changes — it's still local `useState`).

- [ ] **Step 3: Rewrite `useChannel` isStreaming to read from store**

In `client/src/services/channel/use-channel.ts`:

(a) Remove the hook-local state line:

```typescript
// DELETE:
const [isStreaming, setIsStreaming] = useState(false)
```

(b) Remove the `useState` import if no other code in the file uses it (it does — `useCallback`, `useMemo` are separate). Check the top of file; likely the line is `import { useCallback, useMemo, useState } from 'react'`. Change to:

```typescript
import { useCallback, useMemo } from 'react'
```

(c) Add store select after the existing `sessionId` line inside `useChannel`:

```typescript
export function useChannel() {
  const queryClient = useQueryClient()
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const isStreaming = useChatPartsStore((s) =>
    sessionId ? s.streamingBySession.has(sessionId) : false
  )
  const enterSplit = useSessionStore((s) => s.enterSplit)
  const client = useChannelClient(sessionId)
  const connectionId = useConnectionStore((s) => s.activeConnectionId)
  // ...
```

(d) Rewrite `sendMessage` to use `setStreaming` (same semantics, per-session). The current body ends with `setIsStreaming(false)` in finally; change all occurrences:

```typescript
const sendMessage = useCallback(
  async (parts: any[]) => {
    if (!client || !sessionId) return

    const firstText = parts.find((p) => p?.type === 'text') as { text?: string } | undefined
    const pendingText = typeof firstText?.text === 'string' ? firstText.text : ''
    const pendingId = useChatPartsStore.getState().upsertPendingUser(sessionId, pendingText)

    useChatPartsStore.getState().setStreaming(sessionId, true)
    enterSplit(sessionId)
    const sink = buildEventSink(sessionId, client, queryClient, connectionId, pendingId)
    try {
      await client.sendMessage(parts, sink)
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err)
      useChatPartsStore.getState().markPendingUserFailed(sessionId, pendingId, msg)
      showErrorToast(normalizeError(err))
    } finally {
      useChatPartsStore.getState().setStreaming(sessionId, false)
    }
  },
  [client, sessionId, enterSplit, queryClient, connectionId],
)
```

(e) Rewrite `retryPendingUser` the same way:

```typescript
const retryPendingUser = useCallback(
  async (pendingId: string, parts: any[]) => {
    if (!client || !sessionId) return
    useChatPartsStore.setState((s) => {
      const byInfo = new Map(s.infoBySession.get(sessionId) ?? new Map())
      const info = byInfo.get(pendingId)
      if (!info) return {}
      byInfo.set(pendingId, { ...info, __failed: false, __retrying: true, __failReason: undefined })
      const infoBySession = new Map(s.infoBySession); infoBySession.set(sessionId, byInfo)
      return { infoBySession }
    })
    useChatPartsStore.getState().setStreaming(sessionId, true)
    const sink = buildEventSink(sessionId, client, queryClient, connectionId)
    try {
      await client.sendMessage(parts, sink)
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err)
      useChatPartsStore.getState().markPendingUserFailed(sessionId, pendingId, msg)
      showErrorToast(normalizeError(err))
    } finally {
      useChatPartsStore.getState().setStreaming(sessionId, false)
    }
  },
  [client, sessionId, queryClient, connectionId],
)
```

(f) The hook's return shape is unchanged:

```typescript
return { sendMessage, abort, isStreaming, client, retryPendingUser, removePendingUser }
```

- [ ] **Step 4: Run tests, confirm they pass**

Run: `cd client && npx vitest run src/services/channel/use-channel.test.ts`
Expected: all tests PASS (pre-existing `buildEventSink` tests + 2 new).

- [ ] **Step 5: Run the full store test suite to catch regressions**

Run: `cd client && npx vitest run src/stores/ src/services/channel/`
Expected: all PASS.

- [ ] **Step 6: tsc check**

Run: `cd client && npx tsc --noEmit`
Expected: 0 errors.

- [ ] **Step 7: Commit**

```bash
git add client/src/services/channel/use-channel.ts client/src/services/channel/use-channel.test.ts
git commit -m "$(cat <<'EOF'
refactor(channel): per-session isStreaming via store, preserve hook surface

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3 — Full verification sweep

**Files:** (no code changes, verification only)

- [ ] **Step 1: Full client typecheck**

Run: `cd client && npx tsc --noEmit`
Expected: 0 errors.

- [ ] **Step 2: Full client test run**

Run: `cd client && npx vitest run`
Expected: zero new failures. Pre-existing failing tests (chat-header / split-view / providers / stage-toggle from the AI message rendering migration epilogue) stay at their current counts — compare to the baseline in `docs/exec-plans/2026-04-19-ai-message-rendering-migration-plan.md` Phase 6 epilogue.

- [ ] **Step 3: Visual sanity (optional but recommended)**

Start `npm run dev` and manually run scenarios M1-M4 from the spec §6:
- **M1**: send in A → switch to B → B shows send button; back to A → spinner + stop still visible until A completes
- **M2**: A + B both streaming → each composer shows its own state
- **M3**: A completes while on B → returning to A shows send button
- **M4**: click stop on A's spinner → composer flips back to send

(If no model is configured locally, skip; CI-level tests cover the state transitions.)

- [ ] **Step 4: No commit for this task** (verification only)

---

## Task 4 — Housekeeping: tech debt + index + plan marking

**Files:**
- Modify: `docs/exec-plans/tech-debt-tracker.md` (append TD-MULTI-SESSION-SSE-POOL)
- Modify: `docs/exec-plans/index.md` (move from Active to Completed on finish)
- Modify: `docs/product-specs/index.md` §8 (register the new spec — should happen **now** as part of normal registration; see Step 2 below)
- Modify: `docs/exec-plans/2026-04-19-per-session-streaming-indicator-plan.md` (self — check off tasks)

- [ ] **Step 1: Register tech debt**

Append to `docs/exec-plans/tech-debt-tracker.md` (follow the existing row format of prior TD-* entries):

```markdown
### TD-MULTI-SESSION-SSE-POOL (P2)

**背景**：`useSessionSubscribe` 当前仅跟随 `activeSessionId` 订阅 GET SSE 流。`sendMessage` 发起的 POST-SSE 不受影响（切走不断连，数据层正确）。但后端主动推送的 `ontology.updated` / `session.meta.updated` 等事件在用户切走超过 30s（`SessionBusRegistry` 的 eviction-delay）且 ring buffer 溢出（500 条 / 5 min TTL）后可能丢。

**触发概率**：低。需同时满足 "用户切走 > 30s" + "期间后端向该 session 推送 > 500 条事件或 > 5 min"。

**改造方向**：重做 `useSessionSubscribe` 为订阅池（pool of subscribed sessionIds + 生命周期策略），候选触发时机：
- streaming 中的 session 常驻订阅
- LRU 最近访问 N 个 session 常驻订阅
- 用户显式 pin 的 session 常驻订阅

**推迟原因**：实际用户反馈未触发该边界；当前方案已覆盖 composer 指示丢失这一核心痛点（见 2026-04-19-per-session-streaming-indicator-plan）。
```

- [ ] **Step 2: Register spec in product-specs/index.md §8**

Insert a new row at the top of the §8 table (above the `Single Empty Session` row):

```markdown
| [Per-Session Streaming Indicator](./2026-04-19-per-session-streaming-indicator-design.md) | 2026-04-19 | `useChannel().isStreaming` 从 hook-local useState 提升到 `chat-parts-store.streamingBySession: Set<string>`；切 session 后回到 A 正确显示"还在跑"指示；不改 SSE 订阅结构 |
```

- [ ] **Step 3: Register plan in exec-plans/index.md Active**

Insert a new row at the top of the "活跃计划" table:

```markdown
| [Per-Session Streaming Indicator](./2026-04-19-per-session-streaming-indicator-plan.md) | 计划中 | `useChannel.isStreaming` 提升到 store 按 sessionId 分片；切回仍在跑的 session 正确显示 spinner / 停止按钮；SSE 订阅池作为 P2 tech debt 登记 |
```

- [ ] **Step 4: On plan completion, flip Active → Completed**

After Tasks 1-3 all pass, edit `docs/exec-plans/index.md`:

(a) Remove the Active row added in Step 3.

(b) Insert at the top of "已完成计划" table:

```markdown
| [Per-Session Streaming Indicator](./2026-04-19-per-session-streaming-indicator-plan.md) | 2026-04-19 | `useChannel.isStreaming` 提升到 `chat-parts-store.streamingBySession` 按 sessionId 分片；切回后台仍在跑的 session 正确显示 spinner；登记 TD-MULTI-SESSION-SSE-POOL (P2) |
```

- [ ] **Step 5: Mark all tasks in this plan complete**

Edit `docs/exec-plans/2026-04-19-per-session-streaming-indicator-plan.md`:
- Flip every `- [ ]` checkbox to `- [x]` for Tasks 1-4 steps that were executed.

- [ ] **Step 6: Commit housekeeping**

```bash
git add docs/exec-plans/tech-debt-tracker.md docs/exec-plans/index.md docs/product-specs/index.md docs/exec-plans/2026-04-19-per-session-streaming-indicator-plan.md
git commit -m "$(cat <<'EOF'
docs: per-session streaming indicator — housekeeping + TD-MULTI-SESSION-SSE-POOL

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Definition of Done

- All 4 new store tests + 2 new hook tests pass
- `npx tsc --noEmit` 0 errors
- `npx vitest run` no new regressions
- M1-M4 visual scenarios verified (manual, user responsibility)
- Spec registered in `docs/product-specs/index.md` §8
- Plan entry moved from Active to Completed in `docs/exec-plans/index.md`
- TD-MULTI-SESSION-SSE-POOL logged in `docs/exec-plans/tech-debt-tracker.md`
- 3 commits pushed (Task 1, Task 2, Task 4 housekeeping)
