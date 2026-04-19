# Refresh-Resilient Streaming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 浏览器刷新发生在 AI 流响应过程中时，UI 从 history + 缓冲 replay 无缝重建状态，继续接收增量 token，并保持"思考中…"/working 指示正确，用户感知不到中断。

**Architecture:** 后端 `SessionBus` 已有 30s 驱逐延迟 + 500 条 / 5 min buffer + replay on subscribe，核心机制就位。本计划填三个前端缺口 + 一个后端副作用：(1) 持久化 `lastEventIdBySession` 到 sessionStorage 以便 replay 从正确位点起；(2) 持久化 `streamingBySession` 并通过 `session.idle` 事件清零，使得刷新后首包延迟期仍显示"思考中…"；(3) 后端 `ChannelController.stream` 移除提前发布的 `session.status=idle`（TD-013 的后端半），避免误发 idle 污染 bus；(4) verification。

**Tech Stack:** Zustand (+ persist middleware, sessionStorage), React 19, TanStack Query (未改动), Vitest, Java 21 / Spring Boot 3.5, JUnit 5 + AssertJ.

---

## Reference

- 前置修复（已完成，不在本计划范围）：
  - `Message.Role` Jackson 小写序列化（`DtEvent.MessageCreated/Updated` 的 role 字段统一为小写）
  - `buildEventSink` role 规范化（防御层）
- 相关债务：
  - `docs/exec-plans/tech-debt-tracker.md` TD-013 — `ChannelController.java` 1000ms 恩典期 + `SessionIdle` 未消费。本计划清理其中后端误发 `session.status=idle` 的半边；**不**重构 POST 流生命周期（POST 仍在 1s 后 `emitter.complete()`，但不再污染 bus 的 idle 语义）
  - TD-MULTI-SESSION-SSE-POOL — 本计划不改订阅池模型
- Files in scope:
  - Modify: `client/src/stores/channel-store.ts`
  - Modify: `client/src/stores/chat-parts-store.ts`
  - Modify: `client/src/stores/__tests__/chat-parts-store.test.ts`
  - Create: `client/src/stores/__tests__/channel-store.test.ts`
  - Modify: `client/src/services/channel/use-channel.ts`
  - Modify: `client/src/services/channel/use-channel.test.ts`
  - Modify: `client/src/features/session/hooks/use-session-subscribe.ts`
  - Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/ChannelController.java`
  - Modify: `docs/exec-plans/index.md`
  - Modify: `docs/exec-plans/tech-debt-tracker.md`

---

## Task 1 — Replace global `lastEventId` with per-session slice in channel-store (TDD)

**Rationale:** 现有 `lastEventId: number | undefined` 是全局单值，切 session 会串；且 `setLastEventId` 在代码库中从未被调用 → 每次订阅从 0 重放所有缓冲。改为 `lastEventIdBySession: Map<string, number>` 并通过 `sessionStorage` 持久化（per-tab，避免多标签互干）。

**Files:**
- Modify: `client/src/stores/channel-store.ts`
- Create: `client/src/stores/__tests__/channel-store.test.ts`

- [ ] **Step 1: Write failing tests**

Create `client/src/stores/__tests__/channel-store.test.ts`:

```typescript
import { describe, it, expect, beforeEach } from 'vitest'
import { useChannelStore } from '../channel-store'

describe('channel-store', () => {
  beforeEach(() => {
    useChannelStore.setState({
      isConnected: false,
      lastEventIdBySession: new Map(),
    })
    sessionStorage.clear()
  })

  it('setLastEventId writes to the session-keyed map', () => {
    useChannelStore.getState().setLastEventId('ses_a', 42)
    expect(useChannelStore.getState().lastEventIdBySession.get('ses_a')).toBe(42)
  })

  it('setLastEventId is monotonic — smaller ids do not overwrite', () => {
    useChannelStore.getState().setLastEventId('ses_a', 42)
    useChannelStore.getState().setLastEventId('ses_a', 10)
    expect(useChannelStore.getState().lastEventIdBySession.get('ses_a')).toBe(42)
  })

  it('different sessions have independent cursors', () => {
    useChannelStore.getState().setLastEventId('ses_a', 10)
    useChannelStore.getState().setLastEventId('ses_b', 99)
    expect(useChannelStore.getState().lastEventIdBySession.get('ses_a')).toBe(10)
    expect(useChannelStore.getState().lastEventIdBySession.get('ses_b')).toBe(99)
  })

  it('persists lastEventIdBySession across a fresh import (sessionStorage round-trip)', async () => {
    useChannelStore.getState().setLastEventId('ses_a', 7)
    // Force persist middleware to flush (synchronous for createJSONStorage(sessionStorage))
    const raw = sessionStorage.getItem('data-talk.channel')
    expect(raw).toBeTruthy()
    const parsed = JSON.parse(raw!)
    // Map → serialized as { "ses_a": 7 } via the store's replacer
    expect(parsed.state.lastEventIdBySession).toMatchObject({ ses_a: 7 })
  })
})
```

- [ ] **Step 2: Run tests, confirm failure**

```bash
cd client && npx vitest run src/stores/__tests__/channel-store.test.ts
```

Expected: 4 FAIL — `setLastEventId is not a function` / `lastEventIdBySession is undefined` / `raw is null`.

- [ ] **Step 3: Implement the slice with persist middleware**

The codebase uses Zustand 5 (`"zustand": "^5.0.2"` in `client/package.json`). Zustand 5 removed `serialize`/`deserialize` — Map/Set round-trip is done via a custom `storage` adapter with a JSON replacer / reviver, shared across all stores in this project from now on.

(a) Create `client/src/stores/persisted-storage.ts`:

```typescript
import type { StateStorage } from 'zustand/middleware'

// JSON replacer that tags Maps and Sets so the reviver can reconstruct them.
// Plain arrays / objects / primitives pass through unchanged.
function replacer(_key: string, value: unknown): unknown {
  if (value instanceof Map) return { __type: 'Map', value: Array.from(value.entries()) }
  if (value instanceof Set) return { __type: 'Set', value: Array.from(value) }
  return value
}

function reviver(_key: string, value: unknown): unknown {
  if (value && typeof value === 'object' && '__type' in value) {
    const tagged = value as { __type: string; value: unknown }
    if (tagged.__type === 'Map') return new Map(tagged.value as Iterable<[unknown, unknown]>)
    if (tagged.__type === 'Set') return new Set(tagged.value as Iterable<unknown>)
  }
  return value
}

/**
 * Persist adapter that handles Map/Set round-trip. Use with Zustand 5's
 * `persist(..., { storage: sessionStorageWithMaps, ... })`. For
 * localStorage, pass `localStorage` into the factory instead.
 */
export function createMapAwareStorage(backing: Storage): StateStorage {
  return {
    getItem: (name) => {
      const str = backing.getItem(name)
      if (str == null) return null
      try {
        // Zustand 5 expects a JSON string back; it parses it again itself.
        // We re-serialize after reviving so the outer layer gets a normal
        // JSON string whose nested tagged objects are already materialized
        // back to Map/Set by our reviver.
        return JSON.stringify(JSON.parse(str, reviver))
      } catch {
        return null
      }
    },
    setItem: (name, value) => {
      // Zustand 5 passes a pre-serialized JSON string here, but we need to
      // re-serialize ourselves to apply the tagging replacer. Round-trip
      // through parse → stringify-with-replacer.
      backing.setItem(name, JSON.stringify(JSON.parse(value), replacer))
    },
    removeItem: (name) => backing.removeItem(name),
  }
}
```

Wait — Zustand 5's `StateStorage.getItem` is expected to return the raw JSON string (not a revived object). Since the revived Map/Set are inside the serialized tree, we need to either (a) parse → revive → re-stringify (keeps shape but loses Map/Set again), or (b) intercept at the middleware-merge layer.

The cleanest path: serialize Maps/Sets as tagged plain objects on write, then revive them in the store's `merge` hook on read. That keeps the storage adapter a plain `createJSONStorage` wrapper and puts the Map/Set reconstruction inline with the store definition.

**Discard the adapter above** and use the following pattern instead:

(a) Delete `persisted-storage.ts` if you created it (it was a dead end).

(b) Replace the entire contents of `client/src/stores/channel-store.ts`:

```typescript
import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'

type ChannelState = {
  isConnected: boolean
  lastEventIdBySession: Map<string, number>
  setConnected: (on: boolean) => void
  setLastEventId: (sessionId: string, id: number) => void
}

// Shape of what we write to sessionStorage under `state.lastEventIdBySession`:
// a plain `{ [sessionId]: number }` object. The Map is rebuilt in `merge`.
type PersistedShape = {
  lastEventIdBySession: Record<string, number>
}

export const useChannelStore = create<ChannelState>()(
  persist(
    (set) => ({
      isConnected: false,
      lastEventIdBySession: new Map<string, number>(),
      setConnected: (on) => set({ isConnected: on }),
      setLastEventId: (sessionId, id) => set((s) => {
        const prev = s.lastEventIdBySession.get(sessionId) ?? 0
        if (id <= prev) return {}
        const next = new Map(s.lastEventIdBySession)
        next.set(sessionId, id)
        return { lastEventIdBySession: next }
      }),
    }),
    {
      name: 'data-talk.channel',
      storage: createJSONStorage(() => sessionStorage),
      // Persist only the cursor map. `isConnected` is transient.
      // Convert Map → plain object on write so JSON.stringify works.
      partialize: (s) => ({
        lastEventIdBySession: Object.fromEntries(s.lastEventIdBySession),
      }) as unknown as ChannelState,
      // Rebuild the Map on read. Zustand 5's `merge` hook runs after
      // `createJSONStorage` returns the parsed object.
      merge: (persisted, current) => {
        const p = persisted as Partial<PersistedShape> | undefined
        const raw = (p?.lastEventIdBySession ?? {}) as Record<string, number>
        return {
          ...current,
          lastEventIdBySession: new Map(Object.entries(raw)),
        }
      },
    },
  ),
)
```

The `partialize → plain object` + `merge → Map` pattern is the canonical Zustand 5 way for Map/Set round-trip, and is what this plan adopts across channel-store and chat-parts-store (Task 4).

- [ ] **Step 4: Run tests, confirm pass**

```bash
cd client && npx vitest run src/stores/__tests__/channel-store.test.ts
```

Expected: 4 PASS.

- [ ] **Step 5: tsc**

```bash
cd client && npx tsc --noEmit
```

Expected: 0 errors. If `serialize`/`deserialize` APIs differ in the project's Zustand version, fall back to the `storage: { getItem, setItem }` shape equivalently — each key under `state.lastEventIdBySession` is serialized as a plain object, deserialized back to a Map.

- [ ] **Step 6: Commit**

```bash
git add client/src/stores/channel-store.ts client/src/stores/__tests__/channel-store.test.ts
git commit -m "$(cat <<'EOF'
feat(channel-store): per-session lastEventId with sessionStorage persistence

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2 — `buildEventSink` records `evt.id` into `lastEventIdBySession` (TDD)

**Rationale:** SSE 每帧携带 `id` (monotonic per-session, from `SessionBus.seq`). 前端收到一帧 → 更新 cursor → 下次刷新订阅时可以从正确位置续。

**Files:**
- Modify: `client/src/services/channel/use-channel.ts`
- Modify: `client/src/services/channel/use-channel.test.ts`

- [ ] **Step 1: Write failing tests**

Append to `client/src/services/channel/use-channel.test.ts` (inside the existing `describe('buildEventSink')` block or a new sibling `describe`):

```typescript
import { useChannelStore } from '@/stores/channel-store'
// … reuse the existing QueryClient wrapper / imports already in the file

describe('buildEventSink → lastEventId tracking', () => {
  beforeEach(() => {
    useChannelStore.setState({ lastEventIdBySession: new Map(), isConnected: false })
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
    })
  })

  it('updates lastEventIdBySession on every processed event', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildEventSink('ses_a', null, qc, null, null)

    sink({ id: 5, event: 'message.created', data: { message: { id: 'm1', role: 'assistant', sessionId: 'oc', time: { created: 1 } } } })
    sink({ id: 7, event: 'message.part.delta', data: { partId: 'p1', field: 'text', delta: 'hi' } })

    expect(useChannelStore.getState().lastEventIdBySession.get('ses_a')).toBe(7)
  })

  it('does not move cursor backward if an out-of-order event slips through', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildEventSink('ses_a', null, qc, null, null)

    sink({ id: 20, event: 'message.created', data: { message: { id: 'm1', role: 'assistant', sessionId: 'oc', time: { created: 1 } } } })
    sink({ id: 5,  event: 'message.created', data: { message: { id: 'm2', role: 'assistant', sessionId: 'oc', time: { created: 2 } } } })

    expect(useChannelStore.getState().lastEventIdBySession.get('ses_a')).toBe(20)
  })
})
```

- [ ] **Step 2: Run tests, confirm failure**

```bash
cd client && npx vitest run src/services/channel/use-channel.test.ts
```

Expected: the 2 new `lastEventId tracking` tests FAIL (cursor stays at 0).

- [ ] **Step 3: Wire the cursor update**

In `client/src/services/channel/use-channel.ts`, add the import and call:

(a) Add import near the other store imports:

```typescript
import { useChannelStore } from '@/stores/channel-store'
```

(b) Inside the `buildEventSink` returned function, as the **first** thing after destructuring, write:

```typescript
  return (evt: StreamEvent) => {
    const { event, data } = evt
    if (typeof evt.id === 'number' && evt.id > 0) {
      useChannelStore.getState().setLastEventId(sessionId, evt.id)
    }
    if (event === 'message.created' || event === 'message.updated') {
      // …
```

`setLastEventId` is already monotonic (Task 1 Step 3), so out-of-order frames are safely ignored.

- [ ] **Step 4: Run tests, confirm pass**

```bash
cd client && npx vitest run src/services/channel/use-channel.test.ts
```

Expected: all PASS (existing + 2 new).

- [ ] **Step 5: tsc**

```bash
cd client && npx tsc --noEmit
```

Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add client/src/services/channel/use-channel.ts client/src/services/channel/use-channel.test.ts
git commit -m "$(cat <<'EOF'
feat(channel): buildEventSink records per-session lastEventId

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3 — `useSessionSubscribe` uses per-session cursor on reconnect

**Files:**
- Modify: `client/src/features/session/hooks/use-session-subscribe.ts`

- [ ] **Step 1: Read the existing hook**

Current (`client/src/features/session/hooks/use-session-subscribe.ts`):

```typescript
import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { buildEventSink, useChannelClient } from '@/services/channel/use-channel'
import { useChannelStore } from '@/stores/channel-store'
import { useConnectionStore } from '@/features/connection/store'

const subscribedSessions = new Set<string>()

export function useSessionSubscribe(sessionId: string | null) {
  const client = useChannelClient(sessionId)
  const queryClient = useQueryClient()
  const setConnected = useChannelStore((s) => s.setConnected)
  const lastEventId = useChannelStore((s) => s.lastEventId)
  const connectionId = useConnectionStore((s) => s.activeConnectionId)

  useEffect(() => {
    if (!client || !sessionId) { setConnected(false); return }
    if (subscribedSessions.has(sessionId)) { setConnected(false); return }

    subscribedSessions.add(sessionId)
    const sink = buildEventSink(sessionId, client, queryClient, connectionId)
    setConnected(true)
    const unsub = client.subscribe(lastEventId, sink)
    return () => { unsub(); subscribedSessions.delete(sessionId); setConnected(false) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client, sessionId, setConnected, queryClient])
}
```

- [ ] **Step 2: Swap global cursor for per-session lookup**

Replace the entire file with:

```typescript
import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { buildEventSink, useChannelClient } from '@/services/channel/use-channel'
import { useChannelStore } from '@/stores/channel-store'
import { useConnectionStore } from '@/features/connection/store'

const subscribedSessions = new Set<string>()

export function useSessionSubscribe(sessionId: string | null) {
  const client = useChannelClient(sessionId)
  const queryClient = useQueryClient()
  const setConnected = useChannelStore((s) => s.setConnected)
  const connectionId = useConnectionStore((s) => s.activeConnectionId)

  useEffect(() => {
    if (!client || !sessionId) { setConnected(false); return }
    if (subscribedSessions.has(sessionId)) { setConnected(false); return }

    // Resume from the cursor we persisted in sessionStorage (per tab).
    // Zustand hydrates synchronously for sessionStorage, so a getState() read
    // here is safe — no need to subscribe the effect to the Map for the
    // initial subscribe call.
    const resumeFrom = useChannelStore.getState().lastEventIdBySession.get(sessionId)

    subscribedSessions.add(sessionId)
    const sink = buildEventSink(sessionId, client, queryClient, connectionId)
    setConnected(true)
    const unsub = client.subscribe(resumeFrom, sink)
    return () => { unsub(); subscribedSessions.delete(sessionId); setConnected(false) }
    // Deliberately omit connectionId from deps — it's captured in the sink
    // closure; a mid-stream connection switch is rare and would require a
    // fresh session anyway.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client, sessionId, setConnected, queryClient])
}
```

- [ ] **Step 3: tsc**

```bash
cd client && npx tsc --noEmit
```

Expected: 0 errors.

- [ ] **Step 4: Commit**

```bash
git add client/src/features/session/hooks/use-session-subscribe.ts
git commit -m "$(cat <<'EOF'
feat(channel): resume GET subscribe from per-session lastEventId

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4 — Persist `streamingBySession` + clear on `session.idle` (TDD)

**Rationale:** 刷新时前端内存态 `streamingBySession` 丢，导致刷新后首包延迟期（assistant message 尚未 replay）无"思考中…"动画。通过 (a) persist 到 sessionStorage 使刷新后 set 仍在，(b) 在 `buildEventSink` 观察 `session.idle` 事件清零，使 AI 真正完成时 set 自动退出。这里以 OpenCode 的 `session.idle`（`DtEvent.SessionIdle`，事件名 `"session.idle"`）为权威 done 信号，**不**依赖 `session.status=idle`（被 TD-013 在 Task 5 修复前会误发）。

**Files:**
- Modify: `client/src/stores/chat-parts-store.ts`
- Modify: `client/src/stores/__tests__/chat-parts-store.test.ts`
- Modify: `client/src/services/channel/use-channel.ts`
- Modify: `client/src/services/channel/use-channel.test.ts`

- [ ] **Step 1: Write failing tests for the persist + clear-on-idle behavior**

(a) Append to `client/src/stores/__tests__/chat-parts-store.test.ts` (inside existing `streamingBySession` describe if present, or a new one):

```typescript
describe('streamingBySession persistence', () => {
  beforeEach(() => {
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
    })
    sessionStorage.clear()
  })

  it('persists streamingBySession to sessionStorage', () => {
    useChatPartsStore.getState().setStreaming('ses_a', true)
    const raw = sessionStorage.getItem('data-talk.chat-parts')
    expect(raw).toBeTruthy()
    const parsed = JSON.parse(raw!)
    expect(parsed.state.streamingBySession).toEqual(['ses_a'])
  })

  it('does NOT persist the big partsBySession / infoBySession maps', () => {
    useChatPartsStore.getState().upsertInfo('ses_a', {
      id: 'm1', role: 'user', sessionID: 'ses_a', time: { created: 1 },
    })
    const raw = sessionStorage.getItem('data-talk.chat-parts')
    const parsed = JSON.parse(raw!)
    expect(parsed.state.partsBySession).toBeUndefined()
    expect(parsed.state.infoBySession).toBeUndefined()
  })
})
```

(b) Append to `client/src/services/channel/use-channel.test.ts`:

```typescript
describe('buildEventSink → turn-done clears streamingBySession', () => {
  beforeEach(() => {
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(['ses_a']),
    })
  })

  it('clears streamingBySession on session.idle event', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildEventSink('ses_a', null, qc, null, null)

    sink({ id: 1, event: 'session.idle', data: { sessionId: 'ses_a' } })

    expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(false)
  })

  it('clears streamingBySession on session.status=idle event', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildEventSink('ses_a', null, qc, null, null)

    sink({ id: 1, event: 'session.status', data: { status: 'idle', retryInfo: {} } })

    expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(false)
  })

  it('does NOT clear streamingBySession on session.status=busy', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildEventSink('ses_a', null, qc, null, null)

    sink({ id: 1, event: 'session.status', data: { status: 'busy', retryInfo: {} } })

    expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(true)
  })

  it('does not touch other sessions when one goes idle', () => {
    useChatPartsStore.setState({
      streamingBySession: new Set<string>(['ses_a', 'ses_b']),
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildEventSink('ses_a', null, qc, null, null)

    sink({ id: 1, event: 'session.idle', data: { sessionId: 'ses_a' } })

    expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(false)
    expect(useChatPartsStore.getState().streamingBySession.has('ses_b')).toBe(true)
  })
})
```

- [ ] **Step 2: Run tests, confirm failure**

```bash
cd client && npx vitest run src/stores/__tests__/chat-parts-store.test.ts src/services/channel/use-channel.test.ts
```

Expected: 2 store tests + 2 sink tests FAIL.

- [ ] **Step 3: Add persist middleware to chat-parts-store**

Open `client/src/stores/chat-parts-store.ts`. Currently it is `create<ChatPartsState>((set, get) => ({...}))`. Wrap it in `persist` using the same Zustand 5 `partialize + merge` pattern established in Task 1.

(a) Update imports at the top of the file:

```typescript
import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import type { Part, MessageInfo } from '@/services/channel/types'
import { generateUuid } from '@/lib/uuid'
```

(b) Change the export from `create<ChatPartsState>((set, get) => ({ ... }))` to:

```typescript
export const useChatPartsStore = create<ChatPartsState>()(
  persist(
    (set, get) => ({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),

      // ... all existing methods (upsertPart, upsertInfo, upsertMany,
      // replaceSession, removePart, clearSession, setStreaming, getParts,
      // findPart, upsertPendingUser, promotePendingUser,
      // markPendingUserFailed, removePendingUser) unchanged ...
    }),
    {
      name: 'data-talk.chat-parts',
      storage: createJSONStorage(() => sessionStorage),
      // Persist only the streaming flag set. Parts / info are rebuilt on
      // every mount from history + SSE replay — persisting them would
      // explode sessionStorage and risk stale state.
      // Zustand 5: convert Set → array on write so JSON.stringify works.
      partialize: (s) => ({
        streamingBySession: Array.from(s.streamingBySession),
      }) as unknown as ChatPartsState,
      // Rebuild the Set on read. The other slices (Maps of parts / info /
      // index) are NOT persisted — we inherit the default empty Maps from
      // `current` so they're not clobbered to `undefined`.
      merge: (persisted, current) => {
        const p = persisted as { streamingBySession?: string[] } | undefined
        const arr = p?.streamingBySession ?? []
        return {
          ...current,
          streamingBySession: new Set<string>(arr),
        }
      },
    },
  ),
)
```

Keep every existing method body unchanged — only the `create` wrapper and the config block at the bottom differ. Verify by diffing against the previous file; only the opening `create<...>()( persist(` + the `{ name: ..., storage: ..., partialize: ..., merge: ... }` block + the closing `)` should be new.

- [ ] **Step 4: Add turn-done handler in `buildEventSink`**

In `client/src/services/channel/use-channel.ts`, inside `buildEventSink`, add a new branch (place it right after the `message.completed` branch so error / idle handling cluster together):

```typescript
    } else if (event === 'message.completed') {
      // … existing handler unchanged
    } else if (event === 'session.idle' || (event === 'session.status' && (data as any)?.status === 'idle')) {
      // Turn-done signals: OpenCode's native `session.idle` (DtEvent.SessionIdle),
      // plus the backend's composite `session.status=idle` which ChannelController
      // now publishes only AFTER observing real turn completion. Consume both so
      // we clear the streaming flag on whichever arrives first via the bus. The
      // composer flips back to the send button and the thinking indicator exits.
      // Message-level time.completed (set via message.updated) drives per-message
      // UI state separately.
      useChatPartsStore.getState().setStreaming(sessionId, false)
    } else if (event === 'session.meta.updated') {
      // … existing handler unchanged
```

- [ ] **Step 5: Run tests, confirm pass**

```bash
cd client && npx vitest run src/stores/__tests__/chat-parts-store.test.ts src/services/channel/use-channel.test.ts
```

Expected: all PASS (pre-existing + 4 new).

- [ ] **Step 6: tsc + full vitest**

```bash
cd client && npx tsc --noEmit
cd client && npx vitest run
```

Expected: 0 tsc errors. Vitest: no new regressions relative to baseline (pre-existing failing tests from earlier epilogues stay at the same count).

- [ ] **Step 7: Commit**

```bash
git add client/src/stores/chat-parts-store.ts \
        client/src/stores/__tests__/chat-parts-store.test.ts \
        client/src/services/channel/use-channel.ts \
        client/src/services/channel/use-channel.test.ts
git commit -m "$(cat <<'EOF'
feat(channel): persist streamingBySession + clear on session.idle

Refresh-resilient streaming: 持久化 streamingBySession 到 sessionStorage 以便刷新
后首包延迟期仍显示"思考中…"; buildEventSink 消费 OpenCode 的 session.idle 事件作
为权威 done 信号清零。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5 — [SUPERSEDED] POST stream waits for turn-done signal

**Status: SUPERSEDED by parallel work before plan execution started.**

The user rewrote `ChannelController.stream` to subscribe a watcher on the per-session `SessionBus`, block the POST thread until it observes one of `DtEvent.SessionIdle`, `DtEvent.SessionError`, or `DtEvent.SessionStatus("idle", …)` (or the client disconnects / 10-min timeout elapses), and only THEN publish the final `session.status=idle` frame before closing the emitter. This is a stronger fix than the "just remove the publish" approach originally planned here — it makes the POST stream lifecycle correct with respect to real AI completion, which matters for synchronous callers of `send_message`.

New regression test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/channel/ChannelControllerTurnDoneTest.java` — pins the `isTurnDoneSignal` classifier and the publish-on-complete flow.

**Implication for Task 4:** `session.status=idle` is now authoritative too (only published after real completion). Task 4's `buildEventSink` handler should consume BOTH `session.idle` AND `session.status === 'idle'` to clear `streamingBySession`. Both paths flow through the bus and reach the client; consuming both is robust against minor backend-ordering changes.

- [x] **Step 1-6: superseded** — no work to do here. Skip to Task 6 verification.

## Task 5 — [ORIGINAL, NOT EXECUTED] Remove premature `session.status=idle` publish in `ChannelController.stream` (TD-013 back-half)

**Rationale:** `ChannelController.stream` 在 `svc.sendMessage` 返回后 sleep 1s 再 `bus.publish(SessionStatus("idle"))`。此 idle 是 POST 流自己的生命周期信号，与 AI 真实是否完成无关；它会写进 SessionBus 缓冲并被其他订阅者 replay，污染 `session.status` 语义。Task 4 的 `session.idle` 消费依赖 OpenCode 权威信号，但如果这条提前发布的 `session.status=idle` 被前端以后想消费 `session.status` 时误读，会导致错误的"已完成"判断。**本 task 仅删除误发的 publish 行；POST 流 1s 恩典期本身不动**（TD-013 其余部分留给后续架构调整）。

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/ChannelController.java`

- [ ] **Step 1: Locate the offending lines**

Current `ChannelController.stream(...)` contains (around line 116-124):

```java
            try {
                svc.sendMessage(sessionId, m.params().parts());
                // Hold the stream briefly so tests observe initial frames.
                // In production this will stay open until OpenCode completes
                // (Task 23 wires that up). For Plan A, close after short grace.
                for (int i = 0; i < 20 && !sub.isBroken(); i++) {
                    try { Thread.sleep(50); } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                bus.publish(new DtEvent.SessionStatus("idle", Map.of()));
                Thread.sleep(50);
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
```

- [ ] **Step 2: Remove the errant publish**

Replace the snippet above with:

```java
            try {
                svc.sendMessage(sessionId, m.params().parts());
                // Keep the POST stream open for a short grace so the client
                // receives the first buffered frames on the POST response
                // itself; after that, the long-lived GET subscription in
                // useSessionSubscribe carries the remainder of the AI's
                // output. We do NOT publish session.status=idle here — the
                // session is not actually idle; OpenCode emits its own
                // session.idle when the AI truly finishes, which is the
                // authoritative done signal consumed by the frontend.
                for (int i = 0; i < 20 && !sub.isBroken(); i++) {
                    try { Thread.sleep(50); } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
```

Three changes vs the original:
1. Comment rewritten (removes TODO about "Task 23 wires that up" which is stale context)
2. `bus.publish(new DtEvent.SessionStatus("idle", Map.of()));` — removed
3. `Thread.sleep(50);` after the publish — removed (was for flush propagation; no publish → no need)

- [ ] **Step 3: Find and update tests that assert the removed idle publish**

Search for anything pinning the old behavior:

```bash
cd server && grep -rn 'SessionStatus."idle"' data-talk-application/src/test data-talk-adapter/src/test data-talk-infrastructure/src/test 2>/dev/null
```

Inspect each hit:
- If a test asserts "idle frame arrives exactly once" on the POST response → rewrite: "POST response has no `session.status=idle` frame" (or drop that assertion if the test's point was something else).
- If a test depends on `session.status=idle` reaching the GET subscriber after `send_message` → re-orient it to use OpenCode-driven `session.idle` via the `FakeOpenCodeServer` fixtures.

If there are **zero** hits, skip this step.

- [ ] **Step 4: Compile and run the impacted test surfaces**

```bash
cd server && mvn compile -q
cd server && mvn -pl data-talk-adapter test -q -Dtest='ChannelControllerIT,*ChannelService*'
```

Expected: all pass. If the grep in Step 3 found assertions that break, fix them inline (they are correctness issues, not scope creep — the whole point of this task is to drop the false signal).

- [ ] **Step 5: Refresh downstream jars (spring-boot:run cache)**

```bash
cd server && mvn install -pl data-talk-infrastructure -am -DskipTests -q
```

(Required because `spring-boot:run` loads non-adapter modules from `~/.m2`, not `target/classes` — see CLAUDE.md "Backend Run vs Compile".)

- [ ] **Step 6: Commit**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/ChannelController.java
# If any tests were updated in Step 3, add them too:
# git add server/.../path/to/updated/TestFile.java
git commit -m "$(cat <<'EOF'
fix(channel): stop publishing spurious session.status=idle after POST grace

ChannelController.stream was flushing a session.status=idle frame to the bus
one second after send_message returned, regardless of whether the AI was
actually done. Other bus subscribers (GET SSE readers) were seeing this stale
"done" signal in buffer replay, which would corrupt any future session.status
observer. The authoritative done signal is OpenCode's session.idle
(DtEvent.SessionIdle), which the frontend now consumes in buildEventSink.

Resolves TD-013 back-half (POST stream grace period remains as-is).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6 — Verification sweep

**Files:** (no code changes)

- [ ] **Step 1: Full client tsc + vitest**

```bash
cd client && npx tsc --noEmit
cd client && npx vitest run
```

Expected: 0 tsc errors. Vitest: net-new passes from this plan's added tests; pre-existing failing tests (chat-header / split-view / providers / stage-toggle from earlier epilogues) unchanged.

- [ ] **Step 2: Full backend mvn verify**

```bash
cd server && mvn clean verify -q
```

Expected: all green. If anything red, diagnose before marking complete (CLAUDE.md: no skipping hooks / forcing).

- [ ] **Step 3: Manual refresh-continuity smoke (recommended — end-user validation)**

Start the stack:

```bash
# Terminal 1
cd server && mvn spring-boot:run -pl data-talk-adapter
# Terminal 2
cd client && npm run tauri dev
```

Run scenarios:

- **R1 (happy path)**: open a chat → send "写一首 200 字的关于春天的散文诗" → as soon as streaming starts (first token visible) → F5 refresh → expect: user bubble still there, assistant partial content present, new tokens continue appearing, final completion arrives, "思考中…" clears.

- **R2 (first-token delay refresh)**: send a prompt → immediately F5 within 500ms before any assistant content renders → expect: user bubble still there, "思考中…" shimmer shows, eventually assistant content streams in, completion arrives.

- **R3 (cursor efficiency)**: open DevTools Network, filter to `/api/sessions/*/channel` GET → refresh mid-stream → inspect the GET request headers: `Last-Event-ID` should be a number equal to the last frame seen before refresh (not blank / not 0). In the response, the first few frames should be *newer* than that id — the backend is not replaying already-seen frames.

- **R4 (late refresh, bus evicted)**: send a prompt → wait for AI completion → wait 60+ seconds (beyond eviction 30s) → refresh → expect: full history loads from OpenCode, no broken state, no spurious "思考中…", no duplicate bubbles.

- [ ] **Step 4: No commit** (verification only)

---

## Task 7 — Housekeeping: index + tech debt + plan self-check

**Files:**
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/tech-debt-tracker.md`
- Modify: `docs/exec-plans/2026-04-19-refresh-resilient-streaming-plan.md` (self)

- [ ] **Step 1: Register as Active in exec-plans/index.md**

Insert at top of "活跃计划":

```markdown
| [Refresh-Resilient Streaming](./2026-04-19-refresh-resilient-streaming-plan.md) | 计划中 | 刷新浏览器不中断 AI 流响应：persist per-session lastEventId + streamingBySession 到 sessionStorage；buildEventSink 消费 session.idle 清零；后端移除 POST 流误发的 session.status=idle（TD-013 后半） |
```

- [ ] **Step 2: Update TD-013 entry**

Edit the TD-013 row in `docs/exec-plans/tech-debt-tracker.md`:

```markdown
| TD-013 | P1 | adapter / client | ~~`DtEvent.SessionIdle` 定义但未消费；`ChannelController.java` 流生命周期仍用 1000ms 恩典期~~ 前端 buildEventSink 已于 Plan 2026-04-19 refresh-resilient-streaming 消费 session.idle；后端误发的 `session.status=idle` 已移除。**剩余：1000ms 恩典期自身未改（POST 流仍按时长关闭，而非按 AI 完成信号关闭）**，属更大的 POST 流架构重构范畴，保留此条以便追踪 | Plan 2026-04-18 opencode-session-title-sync + Plan 2026-04-19 refresh-resilient-streaming |
```

- [ ] **Step 3: On plan completion, flip Active → Completed**

After Tasks 1-6 all pass, edit `docs/exec-plans/index.md`:

(a) Remove the Active row added in Step 1.

(b) Insert at top of "已完成计划":

```markdown
| [Refresh-Resilient Streaming](./2026-04-19-refresh-resilient-streaming-plan.md) | 2026-04-19 | `lastEventIdBySession` / `streamingBySession` 持久化到 sessionStorage；`buildEventSink` 消费 `session.idle` 清零；后端移除 POST 流误发的 `session.status=idle`（清 TD-013 后半） |
```

- [ ] **Step 4: Mark all task checkboxes in this plan as complete**

Edit this plan file: flip every `- [ ]` to `- [x]` for Tasks 1-7 steps that were executed.

- [ ] **Step 5: Commit**

```bash
git add docs/exec-plans/index.md \
        docs/exec-plans/tech-debt-tracker.md \
        docs/exec-plans/2026-04-19-refresh-resilient-streaming-plan.md
git commit -m "$(cat <<'EOF'
docs: refresh-resilient streaming — housekeeping + TD-013 back-half resolved

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Definition of Done

- `channel-store.lastEventIdBySession` 持久化到 sessionStorage；`useSessionSubscribe` 用 per-session 游标续订
- `chat-parts-store.streamingBySession` 持久化到 sessionStorage；`buildEventSink` 消费 `session.idle` 自动清零
- `ChannelController.stream` 不再误发 `session.status=idle`
- 新增 8+ 单测（channel-store 4 / buildEventSink 4）全绿；预存在失败测试数不增
- `mvn verify` 绿
- R1-R4 手动场景通过
- `docs/exec-plans/index.md` 条目从 Active → Completed
- `tech-debt-tracker.md` TD-013 状态更新
- 5 次 commit 推送（Task 1-5 各一次 + Task 7 housekeeping）

---

## Scope Boundaries (Non-Goals)

明确**不做**的事，避免 scope creep：

1. **POST 流生命周期重构** — 保留 1000ms 恩典期。改为"按 AI 完成信号关闭"是独立架构决策，需先评估 HTTP 长连接的实际收益（现状 GET SSE 已承担续传角色），有价值再单独立计划。
2. **多标签页协调** — sessionStorage 天然 per-tab。两个标签页打开同一会话各自订阅，各自维护 cursor。不处理"标签 A 在发，标签 B 刷出来"的场景。
3. **跨后端重启恢复** — 后端 `SessionBus.seq` 从 `EventRepository.maxEventId` 启动，但 ring buffer 本身非持久化。后端重启后客户端 replay 得到空——需要自然等待新事件。属更大的恢复语义设计，不在本计划。
4. **订阅池（TD-MULTI-SESSION-SSE-POOL）** — 依然按 `activeSessionId` 单订阅；非活跃 session 的事件流失问题由那条单独的 tech debt 负责。
5. **`session.status=busy` 消费** — 本计划只消费权威 `session.idle`。是否把 `session.status=busy` 并入 `streamingBySession` 的激活源，留给后续讨论；当前 `sendMessage` 客户端侧显式 set 已经覆盖激活路径。
