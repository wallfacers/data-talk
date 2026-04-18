# AI 消息渲染迁移 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 OpenCode 桌面端的"绚烂"消息渲染机制（Markdown 增量、PacedMarkdown、TextShimmer、BasicTool 折叠、ContextToolGroup 分组、ToolRegistry）React 化迁移到 DataTalk 客户端，叠加 DataTalk 风险分级、SQL 代码块增强、Artifact 跳转特化；同时前置承接 history-opencode-passthrough 的 OpenCode 原生 shape + 乐观 UI。

**Architecture:** 前端 React 19 + Zustand + TanStack Query 基础上新增 `client/src/features/chat/components/{turn,effects,markdown,tools,helpers}` 子模块；保留现有 channel/store 基建，切换到 OpenCode 原生 Part shape；用 marked + DOMPurify + morphdom 做 Markdown 增量渲染；用 motion React API 做折叠高度动画；通过 ToolRegistry + ActionRegistry customRenderer 两级字典完成工具卡渲染。

**Tech Stack:** React 19 / TypeScript / Zustand / TanStack Query / Vitest / testing-library ; 新增：marked / dompurify / morphdom / motion

**Spec:** [2026-04-19-ai-message-rendering-migration-design.md](../product-specs/2026-04-19-ai-message-rendering-migration-design.md)

**Co-Spec (后端):** [2026-04-19-history-opencode-passthrough-sync.md](./2026-04-19-history-opencode-passthrough-sync.md)

---

## 实施阶段总览

| 阶段 | 范围 | 可 mock 独立验证 |
|---|---|---|
| 0 | Store / types 切到 OpenCode shape + 乐观 UI + replaceSession | ✅ |
| 1 | Markdown + PacedMarkdown + TextShimmer + TextReveal 基础效果层 | ✅ |
| 2 | BasicTool + ToolRegistry + 各 renderer | ✅ |
| 3 | TurnList + SessionTurn + UserBubble + AssistantStream + ContextToolGroup | ✅ |
| 4 | UnknownPart + ErrorCard + ToolErrorBoundary | ✅ |
| 5 | SQL 代码块增强（1a 流程）+ artifact-created 跳转 | ✅ |
| 6 | 联调（后端完工后走 6 个验收场景）| ❌ 需真实后端 |

**阶段 0 + 后端透传完工即可解除原 bug**（AI 消息持久化、切换不丢失）。阶段 1-5 为视觉增强，可独立迭代上线。

---

## Phase 0：Store / Types / 乐观 UI / replaceSession

### Task 0.1：定义 OpenCode 原生 Part / MessageInfo 类型

**Files:**
- Modify: `client/src/services/channel/types.ts`

- [ ] **Step 1：替换 `types.ts` 中的 Part 类型定义为 OpenCode 原生 shape**

```typescript
// client/src/services/channel/types.ts

export type StreamEvent = {
  id: number
  event: string
  data: unknown
}

export type RpcRequest =
  | { jsonrpc: '2.0'; id: string; method: 'send_message';  params: { parts: unknown[] } }
  | { jsonrpc: '2.0'; id: string; method: 'action_result'; params: { callId: string; ok: boolean; output?: unknown; error?: unknown } }
  | { jsonrpc: '2.0'; id: string; method: 'abort';         params: Record<string, never> }
  | { jsonrpc: '2.0'; id: string; method: 'hello';         params: { clientRev: number; lastEventId?: number } }

export type PartTime = { start?: number; end?: number }

export type BasePart = {
  id: string
  sessionID: string
  messageID: string
  metadata?: Record<string, unknown>
}

export type TextPart = BasePart & {
  type: 'text'
  text: string
  time?: PartTime
  synthetic?: boolean
}

export type ReasoningPart = BasePart & {
  type: 'reasoning'
  text: string
  time?: PartTime
}

export type ToolState = {
  status: 'pending' | 'running' | 'completed' | 'error'
  input?: Record<string, any>
  output?: unknown
  metadata?: Record<string, any>
  error?: string
  title?: string
}

export type ToolPart = BasePart & {
  type: 'tool'
  tool: string
  state: ToolState
  callID?: string
}

export type StepStartPart = BasePart & { type: 'step-start' }
export type StepFinishPart = BasePart & {
  type: 'step-finish'
  reason?: string
  tokens?: Record<string, unknown>
  cost?: number
}

export type CompactionPart = BasePart & { type: 'compaction' }

export type FilePart = BasePart & {
  type: 'file'
  url?: string
  filename?: string
  source?: { text?: { start: number; end: number } }
}

export type AgentPart = BasePart & {
  type: 'agent'
  source?: { start: number; end: number }
}

export type Part =
  | TextPart | ReasoningPart | ToolPart
  | StepStartPart | StepFinishPart | CompactionPart
  | FilePart | AgentPart
  | (BasePart & { type: string; [k: string]: unknown })   // unknown fallback

// MessageInfo = OpenCode info 对象整块
export type MessageInfo = {
  id: string
  role: 'user' | 'assistant' | 'system'
  sessionID: string
  time: { created: number; completed?: number }
  providerID?: string
  modelID?: string
  tokens?: Record<string, unknown>
  parentID?: string
  agent?: string
  mode?: string
  path?: Record<string, unknown>
  error?: { name: string; data?: { message?: string } }
  finish?: string
  summary?: { diffs?: Array<{ file: string; before: string; after: string }> }

  // 乐观 UI 扩展字段（仅前端）
  __pending?: boolean
  __failed?: boolean
  __failReason?: string
  __retrying?: boolean
}

import { generateUuid } from '@/lib/uuid'

export function createTextPart(sessionId: string, text: string): TextPart {
  return {
    type: 'text',
    id: generateUuid(),
    sessionID: sessionId,
    messageID: '',
    text,
    metadata: {},
  }
}
```

- [ ] **Step 2：typecheck 确认无残留 messageId/sessionId 小写引用**

Run: `cd client && npx tsc --noEmit 2>&1 | head -50`
Expected: 会有编译错误（store / channel-client / hooks 等都要改），记录下来作为 Task 0.2-0.7 的工作量参考。

- [ ] **Step 3：commit**

```bash
git add client/src/services/channel/types.ts
git commit -m "refactor(types): switch Part / MessageInfo to OpenCode native shape"
```

---

### Task 0.2：重构 chat-parts-store 使用 MessageInfo + 新签名

**Files:**
- Modify: `client/src/stores/chat-parts-store.ts`
- Test: `client/src/stores/__tests__/chat-parts-store.test.ts`（新增）

- [ ] **Step 1：写失败测试 - 验证 infoBySession 结构**

```typescript
// client/src/stores/__tests__/chat-parts-store.test.ts
import { describe, it, expect, beforeEach } from 'vitest'
import { useChatPartsStore } from '../chat-parts-store'
import type { MessageInfo, Part } from '@/services/channel/types'

describe('chat-parts-store', () => {
  beforeEach(() => {
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
    })
  })

  it('upsertInfo writes info to infoBySession', () => {
    const info: MessageInfo = {
      id: 'msg_1', role: 'user', sessionID: 'ses_a',
      time: { created: 1000 },
    }
    useChatPartsStore.getState().upsertInfo('ses_a', info)
    const map = useChatPartsStore.getState().infoBySession.get('ses_a')
    expect(map?.get('msg_1')).toEqual(info)
  })

  it('upsertPart uses OpenCode-native messageID / id', () => {
    const part: Part = {
      type: 'text', id: 'prt_1', sessionID: 'ses_a', messageID: 'msg_1',
      text: 'hello', metadata: {},
    } as Part
    useChatPartsStore.getState().upsertPart('ses_a', part)
    const entry = useChatPartsStore.getState().findPart('ses_a', 'prt_1')
    expect((entry as any)?.text).toBe('hello')
  })
})
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd client && npx vitest run src/stores/__tests__/chat-parts-store.test.ts`
Expected: FAIL（`upsertInfo` / `infoBySession` 不存在）

- [ ] **Step 3：修改 chat-parts-store.ts**

```typescript
// client/src/stores/chat-parts-store.ts
import { create } from 'zustand'
import type { Part, MessageInfo } from '@/services/channel/types'

type ChatPartsState = {
  partsBySession: Map<string, Map<string, Part[]>>
  infoBySession: Map<string, Map<string, MessageInfo>>
  partIndexBySession: Map<string, Map<string, { messageId: string; idx: number }>>

  upsertPart: (sessionId: string, part: Part) => void
  upsertInfo: (sessionId: string, info: MessageInfo) => void
  upsertMany: (sessionId: string, parts: Part[]) => void
  removePart: (sessionId: string, messageId: string, partId: string) => void
  clearSession: (sessionId: string) => void
  getParts: (sessionId: string) => Part[]
  findPart: (sessionId: string, partId: string) => Part | null
}

export const useChatPartsStore = create<ChatPartsState>((set, get) => ({
  partsBySession: new Map(),
  infoBySession: new Map(),
  partIndexBySession: new Map(),

  upsertPart: (sessionId, part) => set((s) => {
    const bySession = new Map(s.partsBySession)
    const byMessage = new Map(bySession.get(sessionId) ?? new Map())
    const index = new Map(s.partIndexBySession.get(sessionId) ?? new Map())
    const list = [...(byMessage.get(part.messageID) ?? [])]
    const existing = index.get(part.id)
    const idx = existing ? existing.idx : list.findIndex((p) => p.id === part.id)
    if (idx >= 0) list[idx] = part
    else {
      list.push(part)
      index.set(part.id, { messageId: part.messageID, idx: list.length - 1 })
    }
    byMessage.set(part.messageID, list)
    bySession.set(sessionId, byMessage)
    const indexBySession = new Map(s.partIndexBySession)
    indexBySession.set(sessionId, index)
    return { partsBySession: bySession, partIndexBySession: indexBySession }
  }),

  upsertInfo: (sessionId, info) => set((s) => {
    const bySession = new Map(s.infoBySession)
    const map = new Map(bySession.get(sessionId) ?? new Map())
    map.set(info.id, { ...(map.get(info.id) ?? info), ...info })
    bySession.set(sessionId, map)
    return { infoBySession: bySession }
  }),

  upsertMany: (sessionId, parts) => {
    for (const p of parts) get().upsertPart(sessionId, p)
  },

  removePart: (sessionId, messageId, partId) => set((s) => {
    const bySession = new Map(s.partsBySession)
    const byMessage = new Map(bySession.get(sessionId) ?? new Map())
    const indexBySession = new Map(s.partIndexBySession)
    const index = new Map(indexBySession.get(sessionId) ?? new Map())
    const list = (byMessage.get(messageId) ?? []).filter((p: Part) => p.id !== partId)
    index.delete(partId)
    byMessage.set(messageId, list)
    bySession.set(sessionId, byMessage)
    indexBySession.set(sessionId, index)
    return { partsBySession: bySession, partIndexBySession: indexBySession }
  }),

  clearSession: (sessionId) => set((s) => {
    const parts = new Map(s.partsBySession); parts.delete(sessionId)
    const info = new Map(s.infoBySession); info.delete(sessionId)
    const index = new Map(s.partIndexBySession); index.delete(sessionId)
    return { partsBySession: parts, infoBySession: info, partIndexBySession: index }
  }),

  getParts: (sessionId) => {
    const byMessage = get().partsBySession.get(sessionId)
    if (!byMessage) return []
    return Array.from(byMessage.values()).flat()
  },

  findPart: (sessionId, partId) => {
    const index = get().partIndexBySession.get(sessionId)
    if (!index) return null
    const entry = index.get(partId)
    if (!entry) return null
    const byMessage = get().partsBySession.get(sessionId)
    if (!byMessage) return null
    const list = byMessage.get(entry.messageId)
    return list?.[entry.idx] ?? null
  },
}))
```

- [ ] **Step 4：运行测试确认通过**

Run: `cd client && npx vitest run src/stores/__tests__/chat-parts-store.test.ts`
Expected: PASS

- [ ] **Step 5：commit**

```bash
git add client/src/stores/chat-parts-store.ts client/src/stores/__tests__/chat-parts-store.test.ts
git commit -m "refactor(store): rename metaBySession → infoBySession, use MessageInfo"
```

---

### Task 0.3：加 replaceSession 原子替换方法

**Files:**
- Modify: `client/src/stores/chat-parts-store.ts`
- Test: `client/src/stores/__tests__/chat-parts-store.test.ts`

- [ ] **Step 1：写失败测试**

```typescript
// 追加到 chat-parts-store.test.ts
it('replaceSession atomically replaces all parts and info in one set', () => {
  const store = useChatPartsStore.getState()
  const renderSpy = vi.fn()
  const unsub = useChatPartsStore.subscribe(renderSpy)

  store.replaceSession('ses_a', [
    {
      info: { id: 'msg_1', role: 'user', sessionID: 'ses_a', time: { created: 1000 } },
      parts: [
        { type: 'text', id: 'prt_1', sessionID: 'ses_a', messageID: 'msg_1', text: 'hi', metadata: {} } as Part,
      ],
    },
    {
      info: { id: 'msg_2', role: 'assistant', sessionID: 'ses_a', time: { created: 2000 } },
      parts: [
        { type: 'text', id: 'prt_2', sessionID: 'ses_a', messageID: 'msg_2', text: 'hello', metadata: {} } as Part,
      ],
    },
  ])

  expect(useChatPartsStore.getState().infoBySession.get('ses_a')?.size).toBe(2)
  expect(useChatPartsStore.getState().partsBySession.get('ses_a')?.size).toBe(2)
  expect(renderSpy).toHaveBeenCalledTimes(1)   // 关键：只触发 1 次 set
  unsub()
})
```

补充 import：`import { vi } from 'vitest'`

- [ ] **Step 2：运行测试确认失败**

Run: `cd client && npx vitest run src/stores/__tests__/chat-parts-store.test.ts`
Expected: FAIL（`replaceSession` 不存在）

- [ ] **Step 3：在 store 里加 replaceSession**

```typescript
// 在 ChatPartsState 类型加一行：
replaceSession: (sessionId: string, list: Array<{ info: MessageInfo; parts: Part[] }>) => void

// 在实现里加：
replaceSession: (sessionId, list) => set((s) => {
  const partsBySession = new Map(s.partsBySession)
  const infoBySession = new Map(s.infoBySession)
  const partIndexBySession = new Map(s.partIndexBySession)

  const byMessage = new Map<string, Part[]>()
  const byInfo = new Map<string, MessageInfo>()
  const index = new Map<string, { messageId: string; idx: number }>()

  for (const { info, parts } of list) {
    byInfo.set(info.id, info)
    byMessage.set(info.id, [...parts])
    parts.forEach((p, i) => index.set(p.id, { messageId: info.id, idx: i }))
  }

  partsBySession.set(sessionId, byMessage)
  infoBySession.set(sessionId, byInfo)
  partIndexBySession.set(sessionId, index)
  return { partsBySession, infoBySession, partIndexBySession }
}),
```

- [ ] **Step 4：运行测试确认通过**

Run: `cd client && npx vitest run src/stores/__tests__/chat-parts-store.test.ts`
Expected: PASS（全部用例）

- [ ] **Step 5：commit**

```bash
git add client/src/stores/chat-parts-store.ts client/src/stores/__tests__/chat-parts-store.test.ts
git commit -m "feat(store): add replaceSession for atomic history load"
```

---

### Task 0.4：乐观 UI — pending user 消息 API

**Files:**
- Modify: `client/src/stores/chat-parts-store.ts`
- Test: `client/src/stores/__tests__/chat-parts-store.test.ts`

- [ ] **Step 1：写失败测试**

```typescript
it('upsertPendingUser writes pending info + text part, returns pendingId', () => {
  const store = useChatPartsStore.getState()
  const pendingId = store.upsertPendingUser('ses_a', 'hello')
  expect(pendingId).toMatch(/^pending_/)

  const info = useChatPartsStore.getState().infoBySession.get('ses_a')?.get(pendingId)
  expect(info?.__pending).toBe(true)
  expect(info?.role).toBe('user')

  const parts = useChatPartsStore.getState().partsBySession.get('ses_a')?.get(pendingId)
  expect(parts).toHaveLength(1)
  expect((parts?.[0] as any).text).toBe('hello')
})

it('promotePendingUser renames pendingId → realId preserving content', () => {
  const store = useChatPartsStore.getState()
  const pendingId = store.upsertPendingUser('ses_a', 'hello')
  store.promotePendingUser('ses_a', pendingId, 'msg_real_1')

  const byInfo = useChatPartsStore.getState().infoBySession.get('ses_a')!
  expect(byInfo.get(pendingId)).toBeUndefined()
  expect(byInfo.get('msg_real_1')?.__pending).toBeFalsy()

  const parts = useChatPartsStore.getState().partsBySession.get('ses_a')?.get('msg_real_1')
  expect((parts?.[0] as any).text).toBe('hello')
})

it('markPendingUserFailed sets __failed flag', () => {
  const store = useChatPartsStore.getState()
  const pendingId = store.upsertPendingUser('ses_a', 'hello')
  store.markPendingUserFailed('ses_a', pendingId, 'network error')
  const info = useChatPartsStore.getState().infoBySession.get('ses_a')?.get(pendingId)
  expect(info?.__failed).toBe(true)
  expect(info?.__failReason).toBe('network error')
})

it('removePendingUser removes info and parts', () => {
  const store = useChatPartsStore.getState()
  const pendingId = store.upsertPendingUser('ses_a', 'hello')
  store.removePendingUser('ses_a', pendingId)
  expect(useChatPartsStore.getState().infoBySession.get('ses_a')?.get(pendingId)).toBeUndefined()
  expect(useChatPartsStore.getState().partsBySession.get('ses_a')?.get(pendingId)).toBeUndefined()
})
```

- [ ] **Step 2：运行确认失败**

Run: `cd client && npx vitest run src/stores/__tests__/chat-parts-store.test.ts`

- [ ] **Step 3：在 store 实现这四个方法**

```typescript
// 类型签名加入 ChatPartsState：
upsertPendingUser: (sessionId: string, text: string) => string
promotePendingUser: (sessionId: string, pendingId: string, realId: string) => void
markPendingUserFailed: (sessionId: string, pendingId: string, reason: string) => void
removePendingUser: (sessionId: string, pendingId: string) => void

// 实现：
upsertPendingUser: (sessionId, text) => {
  const pendingId = `pending_${generateUuid()}`
  const partId = `pending_prt_${generateUuid()}`
  get().upsertInfo(sessionId, {
    id: pendingId, role: 'user', sessionID: sessionId,
    time: { created: Date.now() },
    __pending: true,
  })
  get().upsertPart(sessionId, {
    type: 'text', id: partId, sessionID: sessionId, messageID: pendingId,
    text, metadata: {},
  } as Part)
  return pendingId
},

promotePendingUser: (sessionId, pendingId, realId) => set((s) => {
  const byInfo = new Map(s.infoBySession.get(sessionId) ?? new Map<string, MessageInfo>())
  const pendingInfo = byInfo.get(pendingId)
  if (!pendingInfo) return {}

  const realInfo: MessageInfo = { ...pendingInfo, id: realId }
  delete realInfo.__pending
  delete realInfo.__failed
  delete realInfo.__failReason
  delete realInfo.__retrying
  byInfo.delete(pendingId)
  byInfo.set(realId, realInfo)

  const byMsg = new Map(s.partsBySession.get(sessionId) ?? new Map<string, Part[]>())
  const pendingParts = byMsg.get(pendingId) ?? []
  byMsg.delete(pendingId)
  byMsg.set(realId, pendingParts.map((p) => ({ ...p, messageID: realId })))

  const index = new Map(s.partIndexBySession.get(sessionId) ?? new Map())
  for (const [pid, entry] of index.entries()) {
    if (entry.messageId === pendingId) index.set(pid, { ...entry, messageId: realId })
  }

  const infoBySession = new Map(s.infoBySession); infoBySession.set(sessionId, byInfo)
  const partsBySession = new Map(s.partsBySession); partsBySession.set(sessionId, byMsg)
  const partIndexBySession = new Map(s.partIndexBySession); partIndexBySession.set(sessionId, index)
  return { infoBySession, partsBySession, partIndexBySession }
}),

markPendingUserFailed: (sessionId, pendingId, reason) => set((s) => {
  const byInfo = new Map(s.infoBySession.get(sessionId) ?? new Map<string, MessageInfo>())
  const info = byInfo.get(pendingId)
  if (!info) return {}
  byInfo.set(pendingId, { ...info, __failed: true, __failReason: reason, __retrying: false })
  const infoBySession = new Map(s.infoBySession); infoBySession.set(sessionId, byInfo)
  return { infoBySession }
}),

removePendingUser: (sessionId, pendingId) => set((s) => {
  const byInfo = new Map(s.infoBySession.get(sessionId) ?? new Map<string, MessageInfo>())
  byInfo.delete(pendingId)
  const byMsg = new Map(s.partsBySession.get(sessionId) ?? new Map<string, Part[]>())
  byMsg.delete(pendingId)
  const index = new Map(s.partIndexBySession.get(sessionId) ?? new Map())
  for (const [pid, entry] of Array.from(index.entries())) {
    if (entry.messageId === pendingId) index.delete(pid)
  }
  const infoBySession = new Map(s.infoBySession); infoBySession.set(sessionId, byInfo)
  const partsBySession = new Map(s.partsBySession); partsBySession.set(sessionId, byMsg)
  const partIndexBySession = new Map(s.partIndexBySession); partIndexBySession.set(sessionId, index)
  return { infoBySession, partsBySession, partIndexBySession }
}),
```

加 import：`import { generateUuid } from '@/lib/uuid'`

- [ ] **Step 4：测试通过**

Run: `cd client && npx vitest run src/stores/__tests__/chat-parts-store.test.ts`
Expected: PASS

- [ ] **Step 5：commit**

```bash
git add client/src/stores/chat-parts-store.ts client/src/stores/__tests__/chat-parts-store.test.ts
git commit -m "feat(store): optimistic pending user message API"
```

---

### Task 0.5：改 buildEventSink 使用 OpenCode 原生字段

**Files:**
- Modify: `client/src/services/channel/use-channel.ts`

- [ ] **Step 1：替换 buildEventSink 的 message.created / delta 字段名**

在 `use-channel.ts:21-75` 范围内把 `upsertMeta` 全部改成 `upsertInfo`，并使用原生 `info` 对象：

```typescript
export function buildEventSink(sessionId: string, client: ChannelClient | null, queryClient: QueryClient, connectionId: string | null = null) {
  return (evt: StreamEvent) => {
    const { event, data } = evt
    if (event === 'message.created') {
      const m = (data as any).info ?? (data as any).message   // 兼容过渡
      if (!m) return
      useChatPartsStore.getState().upsertInfo(sessionId, {
        id: m.id,
        role: m.role,
        sessionID: m.sessionID ?? sessionId,
        time: m.time ?? { created: Date.now() },
        providerID: m.providerID,
        modelID: m.modelID,
        parentID: m.parentID,
        agent: m.agent,
        mode: m.mode,
        error: m.error,
        finish: m.finish,
        tokens: m.tokens,
      })
    } else if (event === 'session.meta.updated') {
      queryClient.invalidateQueries({ queryKey: ['sessions', connectionId] })
    } else if (event === 'message.part.created' || event === 'message.part.updated') {
      const part = (data as any).part
      useChatPartsStore.getState().upsertPart(sessionId, part)

      // 检测首个 role=user 且 messageID 非 pending_* 的 part，触发 promote
      if (part?.messageID && !part.messageID.startsWith('pending_')) {
        const infoMap = useChatPartsStore.getState().infoBySession.get(sessionId)
        const info = infoMap?.get(part.messageID)
        if (info?.role === 'user') {
          for (const [id, i] of infoMap!) {
            if (i.__pending && id.startsWith('pending_')) {
              useChatPartsStore.getState().promotePendingUser(sessionId, id, part.messageID)
              break
            }
          }
        }
      }
    } else if (event === 'message.part.delta') {
      const { partId, field, delta } = data as { partId: string; field: string; delta: string }
      const store = useChatPartsStore.getState()
      const existing = store.findPart(sessionId, partId)
      if (existing) {
        const prev = (existing as Record<string, unknown>)[field] ?? ''
        const next = { ...existing, [field]: String(prev) + delta }
        store.upsertPart(sessionId, next as Part)
      }
    } else if (event === 'message.part.removed') {
      const { partId } = data as any
      const store = useChatPartsStore.getState()
      const index = store.partIndexBySession.get(sessionId)
      if (index) {
        const entry = index.get(partId)
        if (entry) store.removePart(sessionId, entry.messageId, partId)
      }
    } else if (event === 'ontology.updated') {
      const d = data as any
      if (d.objectType === 'datatalk.artifact') {
        useOntologyStore.getState().upsertArtifact(sessionId, {
          id: d.id,
          version: d.patch?.version ?? 1,
          kind: d.patch?.kind ?? 'table',
          supersedesId: d.patch?.supersedesId,
          payload: d.patch,
        })
        useTimelineStore.getState().addArtifact(sessionId, d.id, d.patch?.supersedesId)
      }
    } else if (event === 'action.invoke' && client) {
      const { callId, actionId, input } = data as any
      const handler = getClientHandler(actionId)
      if (handler) {
        handler(input, { sessionId })
          .then((output) => client.actionResult(callId, true, output))
          .catch((err) => client.actionResult(callId, false, undefined,
            { code: 'client_action_error', message: String(err) }))
      }
    }
  }
}
```

删除未使用的 import：`import { normalizeError, normalizeRole, showErrorToast } from '@/services/http-error'` → 保留 `normalizeError, showErrorToast`，移除 `normalizeRole`。

- [ ] **Step 2：typecheck**

Run: `cd client && npx tsc --noEmit`
Expected: 本文件无错

- [ ] **Step 3：commit**

```bash
git add client/src/services/channel/use-channel.ts
git commit -m "refactor(channel): buildEventSink uses OpenCode native shape + promote pending"
```

---

### Task 0.6：useSessionHistory 走 replaceSession + 兼容新响应格式

**Files:**
- Modify: `client/src/features/session/hooks/use-session-history.ts`

- [ ] **Step 1：按 OpenCode 响应格式重写**

```typescript
// client/src/features/session/hooks/use-session-history.ts
import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { http } from '@/services/http'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'
import type { MessageInfo, Part } from '@/services/channel/types'
import type { Artifact } from '@/services/channel/event-reducer'

type HistoryItem = { info: MessageInfo; parts: Part[] }
type HistoryResponse = HistoryItem[] | { messages: Array<{ id: string; role: string; parts: Part[]; createdAt?: number }> }

function normalizeHistory(raw: HistoryResponse): HistoryItem[] {
  if (Array.isArray(raw)) return raw
  // 向后兼容旧响应 {messages:[...]}（后端过渡期）
  return (raw.messages ?? []).map((m) => ({
    info: {
      id: m.id,
      role: m.role as 'user' | 'assistant' | 'system',
      sessionID: '',  // 旧响应没有
      time: { created: Number(m.createdAt ?? Date.now()) },
    },
    parts: m.parts,
  }))
}

type ArtifactDto = {
  id: string; version: number; kind: 'table' | 'chart' | 'erd'
  sessionId?: string; supersedesId?: string; supersedesVersion?: number
  pinned?: boolean; payload?: unknown; createdAt?: number
}

const historyQueryKeys = {
  messages: (sessionId: string) => ['session-history', 'messages', sessionId] as const,
  artifacts: (sessionId: string) => ['session-history', 'artifacts', sessionId] as const,
}

export function useSessionHistory(sessionId: string | null) {
  const { data: messagesData, error: messagesError } = useQuery({
    queryKey: sessionId ? historyQueryKeys.messages(sessionId) : ['session-history', 'messages', null],
    queryFn: () => http.get(`sessions/${sessionId}/messages`, { silent: true } as any).json<HistoryResponse>(),
    enabled: !!sessionId,
    staleTime: 0,
    retry: 1,
  })

  const { data: artifactsData } = useQuery({
    queryKey: sessionId ? historyQueryKeys.artifacts(sessionId) : ['session-history', 'artifacts', null],
    queryFn: () => http.get(`sessions/${sessionId}/artifacts`, { silent: true } as any).json<{ artifacts: ArtifactDto[] }>(),
    enabled: !!sessionId,
    staleTime: 0,
    retry: 1,
  })

  useEffect(() => {
    if (!sessionId || !messagesData || !artifactsData) return

    const list = normalizeHistory(messagesData)
    useChatPartsStore.getState().replaceSession(sessionId, list)

    const ontApi = useOntologyStore.getState()
    const artifacts: Artifact[] = (artifactsData.artifacts ?? []).map((a) => ({
      id: a.id, version: a.version, kind: a.kind,
      supersedesId: a.supersedesId, supersedesVersion: a.supersedesVersion,
      pinned: a.pinned, payload: a.payload, createdAt: a.createdAt,
    }))
    ontApi.replaceSession(sessionId, artifacts)

    const tApi = useTimelineStore.getState()
    tApi.clear(sessionId)
    for (const a of artifacts) tApi.addArtifact(sessionId, a.id, a.supersedesId)
  }, [sessionId, messagesData, artifactsData])

  return { error: messagesError }
}
```

- [ ] **Step 2：typecheck**

Run: `cd client && npx tsc --noEmit`

- [ ] **Step 3：commit**

```bash
git add client/src/features/session/hooks/use-session-history.ts
git commit -m "refactor(session-history): atomic replaceSession + OpenCode shape"
```

---

### Task 0.7：sendMessage 接入 pending user + 失败降级

**Files:**
- Modify: `client/src/services/channel/use-channel.ts`

- [ ] **Step 1：重写 sendMessage 带乐观 UI + 失败处理**

```typescript
// use-channel.ts 顶部 import 补：
import { useChatPartsStore } from '@/stores/chat-parts-store'

// 替换现有 sendMessage：
const sendMessage = useCallback(
  async (parts: any[]) => {
    if (!client || !sessionId) return

    // 抽取首个 text part 的 text 作为 pending 文本
    const firstText = parts.find((p) => p?.type === 'text') as { text?: string } | undefined
    const pendingText = typeof firstText?.text === 'string' ? firstText.text : ''
    const pendingId = useChatPartsStore.getState().upsertPendingUser(sessionId, pendingText)

    setIsStreaming(true)
    enterSplit(sessionId)
    const sink = buildEventSink(sessionId, client, queryClient, connectionId)
    try {
      await client.sendMessage(parts, sink)
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err)
      useChatPartsStore.getState().markPendingUserFailed(sessionId, pendingId, msg)
      showErrorToast(normalizeError(err))
    } finally {
      setIsStreaming(false)
    }
  },
  [client, sessionId, enterSplit, queryClient, connectionId],
)

// 新增 retrySend / removePending 导出（供 UserBubble 使用）
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
    setIsStreaming(true)
    const sink = buildEventSink(sessionId, client, queryClient, connectionId)
    try {
      await client.sendMessage(parts, sink)
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err)
      useChatPartsStore.getState().markPendingUserFailed(sessionId, pendingId, msg)
      showErrorToast(normalizeError(err))
    } finally {
      setIsStreaming(false)
    }
  },
  [client, sessionId, queryClient, connectionId],
)

const removePendingUser = useCallback(
  (pendingId: string) => {
    if (!sessionId) return
    useChatPartsStore.getState().removePendingUser(sessionId, pendingId)
  },
  [sessionId],
)

return { sendMessage, abort, isStreaming, client, retryPendingUser, removePendingUser }
```

- [ ] **Step 2：typecheck**

Run: `cd client && npx tsc --noEmit`

- [ ] **Step 3：commit**

```bash
git add client/src/services/channel/use-channel.ts
git commit -m "feat(channel): optimistic pending user + retry/remove"
```

---

## Phase 1：Markdown / PacedMarkdown / TextShimmer / TextReveal 效果层

### Task 1.1：安装新依赖

**Files:**
- Modify: `client/package.json`

- [ ] **Step 1：安装依赖**

```bash
cd client && npm install marked dompurify morphdom motion
npm install -D @types/dompurify
```

- [ ] **Step 2：verify**

Run: `cd client && npx tsc --noEmit && cat package.json | grep -E "marked|dompurify|morphdom|motion"`
Expected: 四个包都在 dependencies 里

- [ ] **Step 3：commit**

```bash
git add client/package.json client/package-lock.json
git commit -m "deps: add marked / dompurify / morphdom / motion for message rendering"
```

---

### Task 1.2：markdown-stream.ts 流式分块

**Files:**
- Create: `client/src/features/chat/components/markdown/markdown-stream.ts`
- Test: `client/src/features/chat/components/markdown/__tests__/markdown-stream.test.ts`

- [ ] **Step 1：写失败测试**

```typescript
// markdown-stream.test.ts
import { describe, it, expect } from 'vitest'
import { stream } from '../markdown-stream'

describe('markdown-stream', () => {
  it('live=false returns single full block', () => {
    const result = stream('# hello\nworld', false)
    expect(result).toHaveLength(1)
    expect(result[0].mode).toBe('full')
    expect(result[0].src).toBe('# hello\nworld')
  })

  it('live=true with unclosed code block separates tail', () => {
    const text = '# title\n\n```js\nconst x ='
    const result = stream(text, true)
    expect(result.length).toBeGreaterThan(0)
    expect(result.at(-1)?.raw).toContain('```js')
  })

  it('handles empty text', () => {
    const result = stream('', true)
    expect(result.length).toBe(1)
  })
})
```

- [ ] **Step 2：运行确认失败**

Run: `cd client && npx vitest run src/features/chat/components/markdown/__tests__/markdown-stream.test.ts`

- [ ] **Step 3：实现 markdown-stream.ts**

（直接移植 opencode `packages/ui/src/components/markdown-stream.ts` 到 React 侧，JS 无副作用，实现不变）

```typescript
// client/src/features/chat/components/markdown/markdown-stream.ts
import { marked, type Tokens } from 'marked'

export type Block = {
  raw: string
  src: string
  mode: 'full' | 'live'
}

function refs(text: string) {
  return /^\[[^\]]+\]:\s+\S+/m.test(text) || /^\[\^[^\]]+\]:\s+/m.test(text)
}

function open(raw: string) {
  const match = raw.match(/^[ \t]{0,3}(`{3,}|~{3,})/)
  if (!match) return false
  const mark = match[1]
  if (!mark) return false
  const char = mark[0]
  const size = mark.length
  const last = raw.trimEnd().split('\n').at(-1)?.trim() ?? ''
  return !new RegExp(`^[\\t ]{0,3}${char}{${size},}[\\t ]*$`).test(last)
}

function heal(text: string): string {
  // 简化版：不引入 remend；未闭合强调/链接保持原样，交给 marked 自行处理
  return text
}

export function stream(text: string, live: boolean): Block[] {
  if (!live) return [{ raw: text, src: text, mode: 'full' }]
  const src = heal(text)
  if (!text) return [{ raw: text, src, mode: 'live' }]
  if (refs(text)) return [{ raw: text, src, mode: 'live' }]
  const tokens = marked.lexer(text)
  const tail = tokens.findLastIndex((token: any) => token.type !== 'space')
  if (tail < 0) return [{ raw: text, src, mode: 'live' }]
  const last = tokens[tail]
  if (!last || last.type !== 'code') return [{ raw: text, src, mode: 'live' }]
  const code = last as Tokens.Code
  if (!open(code.raw)) return [{ raw: text, src, mode: 'live' }]
  const head = tokens.slice(0, tail).map((t: any) => t.raw).join('')
  if (!head) return [{ raw: code.raw, src: code.raw, mode: 'live' }]
  return [
    { raw: head, src: heal(head), mode: 'live' },
    { raw: code.raw, src: code.raw, mode: 'live' },
  ]
}
```

- [ ] **Step 4：测试通过**

Run: `cd client && npx vitest run src/features/chat/components/markdown/__tests__/markdown-stream.test.ts`

- [ ] **Step 5：commit**

```bash
git add client/src/features/chat/components/markdown/
git commit -m "feat(markdown): streaming block splitter"
```

---

### Task 1.3：Markdown 组件（marked + DOMPurify + morphdom）

**Files:**
- Create: `client/src/features/chat/components/markdown/markdown.tsx`
- Create: `client/src/features/chat/components/markdown/markdown.css`
- Test: `client/src/features/chat/components/markdown/__tests__/markdown.test.tsx`

- [ ] **Step 1：写失败测试**

```tsx
// markdown.test.tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Markdown } from '../markdown'

describe('Markdown', () => {
  it('sanitizes script tags', () => {
    const { container } = render(<Markdown text="<script>alert(1)</script>hello" cacheKey="t1" />)
    expect(container.querySelector('script')).toBeNull()
  })

  it('renders headings', async () => {
    render(<Markdown text="# Title" cacheKey="t2" />)
    await screen.findByText('Title')
  })

  it('wraps pre blocks with copy button', async () => {
    const { container } = render(<Markdown text="```js\nconst x = 1\n```" cacheKey="t3" />)
    await new Promise((r) => setTimeout(r, 20))
    expect(container.querySelector('[data-component="markdown-code"]')).not.toBeNull()
    expect(container.querySelector('[data-slot="markdown-copy-button"]')).not.toBeNull()
  })
})
```

- [ ] **Step 2：运行确认失败**

Run: `cd client && npx vitest run src/features/chat/components/markdown/__tests__/markdown.test.tsx`

- [ ] **Step 3：实现 markdown.tsx**

```tsx
// markdown.tsx
import { useEffect, useRef, useState } from 'react'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import morphdom from 'morphdom'
import { stream } from './markdown-stream'
import './markdown.css'

type Entry = { hash: string; html: string }
const MAX_CACHE = 200
const cache = new Map<string, Entry>()

const PURIFY_CONFIG = {
  USE_PROFILES: { html: true, mathMl: true },
  SANITIZE_NAMED_PROPS: true,
  FORBID_TAGS: ['style'],
  FORBID_CONTENTS: ['style', 'script'],
}

function hash(text: string): string {
  let h = 0
  for (let i = 0; i < text.length; i++) h = ((h << 5) - h + text.charCodeAt(i)) | 0
  return h.toString(36)
}

function escape(text: string): string {
  return text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

function fallback(text: string): string {
  return escape(text).replace(/\r\n?/g, '\n').replace(/\n/g, '<br>')
}

function sanitize(html: string): string {
  if (!DOMPurify.isSupported) return ''
  return DOMPurify.sanitize(html, PURIFY_CONFIG)
}

function touch(key: string, value: Entry) {
  cache.delete(key)
  cache.set(key, value)
  if (cache.size > MAX_CACHE) {
    const first = cache.keys().next().value
    if (first) cache.delete(first)
  }
}

function decorateCodeBlocks(root: HTMLElement) {
  const pres = Array.from(root.querySelectorAll('pre'))
  for (const pre of pres) {
    if (pre.parentElement?.getAttribute('data-component') === 'markdown-code') continue
    const wrapper = document.createElement('div')
    wrapper.setAttribute('data-component', 'markdown-code')
    pre.parentNode?.replaceChild(wrapper, pre)
    wrapper.appendChild(pre)
    const btn = document.createElement('button')
    btn.setAttribute('data-slot', 'markdown-copy-button')
    btn.setAttribute('type', 'button')
    btn.setAttribute('aria-label', 'Copy')
    btn.textContent = 'Copy'
    wrapper.appendChild(btn)
  }
}

function renderHtml(text: string, cacheKey: string | undefined, streaming: boolean): string {
  if (!text) return ''
  try {
    const blocks = stream(text, streaming)
    const htmls = blocks.map((block, i) => {
      const blockHash = hash(block.raw)
      const key = cacheKey ? `${cacheKey}:${i}:${block.mode}` : undefined
      if (key) {
        const cached = cache.get(key)
        if (cached && cached.hash === blockHash) {
          touch(key, cached)
          return cached.html
        }
      }
      const parsed = marked.parse(block.src, { async: false }) as string
      const safe = sanitize(parsed)
      if (key) touch(key, { hash: blockHash, html: safe })
      return safe
    })
    return htmls.join('')
  } catch {
    return fallback(text)
  }
}

export function Markdown(props: {
  text: string
  cacheKey?: string
  streaming?: boolean
  className?: string
}) {
  const ref = useRef<HTMLDivElement>(null)
  const [, setTick] = useState(0)

  useEffect(() => {
    const container = ref.current
    if (!container) return
    const html = renderHtml(props.text, props.cacheKey, props.streaming ?? false)
    if (!html) {
      container.innerHTML = ''
      return
    }
    const temp = document.createElement('div')
    temp.innerHTML = html
    decorateCodeBlocks(temp)
    try {
      morphdom(container, temp, { childrenOnly: true })
    } catch (err) {
      // 第 6 节：raise 到 ErrorBoundary，这里允许向上抛
      throw err
    }
    setTick((t) => t + 1)

    const onClick = async (e: MouseEvent) => {
      const btn = (e.target as Element)?.closest?.('[data-slot="markdown-copy-button"]')
      if (!btn) return
      const code = btn.closest('[data-component="markdown-code"]')?.querySelector('code')
      const content = code?.textContent ?? ''
      if (!content) return
      await navigator.clipboard?.writeText?.(content)
      btn.setAttribute('data-copied', 'true')
      setTimeout(() => btn.removeAttribute('data-copied'), 2000)
    }
    container.addEventListener('click', onClick)
    return () => container.removeEventListener('click', onClick)
  }, [props.text, props.cacheKey, props.streaming])

  return <div ref={ref} data-component="markdown" className={props.className} />
}
```

- [ ] **Step 4：写 markdown.css（精简版）**

```css
/* markdown.css */
[data-component="markdown"] { line-height: 1.5; word-wrap: break-word; }
[data-component="markdown"] h1,
[data-component="markdown"] h2,
[data-component="markdown"] h3 { margin: 0.75em 0 0.5em; font-weight: 600; }
[data-component="markdown"] p { margin: 0.5em 0; }
[data-component="markdown"] pre { background: var(--muted, #f5f5f5); padding: 12px; border-radius: 6px; overflow-x: auto; }
[data-component="markdown"] code { font-family: ui-monospace, SFMono-Regular, monospace; font-size: 0.9em; }
[data-component="markdown"] :not(pre) > code { background: var(--muted, #f5f5f5); padding: 1px 4px; border-radius: 3px; }

[data-component="markdown-code"] { position: relative; }
[data-slot="markdown-copy-button"] {
  position: absolute; top: 6px; right: 6px;
  padding: 2px 8px; font-size: 12px; border: 1px solid var(--border, #ddd);
  background: var(--background, #fff); border-radius: 4px; cursor: pointer;
  opacity: 0; transition: opacity 150ms;
}
[data-component="markdown-code"]:hover [data-slot="markdown-copy-button"] { opacity: 1; }
[data-slot="markdown-copy-button"][data-copied="true"]::after { content: " ✓"; }
```

- [ ] **Step 5：测试通过 + commit**

Run: `cd client && npx vitest run src/features/chat/components/markdown/__tests__/markdown.test.tsx`

```bash
git add client/src/features/chat/components/markdown/
git commit -m "feat(markdown): morphdom-based incremental renderer with sanitize + LRU"
```

---

### Task 1.4：PacedMarkdown 节拍推进包装

**Files:**
- Create: `client/src/features/chat/components/effects/paced-markdown.tsx`
- Test: `client/src/features/chat/components/effects/__tests__/paced-markdown.test.tsx`

- [ ] **Step 1：写失败测试**

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { PacedMarkdown } from '../paced-markdown'

describe('PacedMarkdown', () => {
  it('streaming=false renders full text immediately', async () => {
    const { container } = render(<PacedMarkdown text="hello world" cacheKey="x" streaming={false} />)
    await new Promise((r) => setTimeout(r, 30))
    expect(container.textContent).toContain('hello world')
  })

  it('streaming=true reveals progressively', async () => {
    vi.useFakeTimers()
    const { container } = render(<PacedMarkdown text="hello world! this is streaming" cacheKey="x2" streaming={true} />)
    vi.advanceTimersByTime(24)
    const partial1 = container.textContent ?? ''
    vi.advanceTimersByTime(240)
    const partial2 = container.textContent ?? ''
    expect(partial2.length).toBeGreaterThanOrEqual(partial1.length)
    vi.useRealTimers()
  })
})
```

- [ ] **Step 2：实现 paced-markdown.tsx**

```tsx
// paced-markdown.tsx
import { useEffect, useRef, useState } from 'react'
import { Markdown } from '../markdown/markdown'

const PACE_MS = 24
const SNAP = /[\s.,!?;:)\]]/

function step(size: number): number {
  if (size <= 12) return 2
  if (size <= 48) return 4
  if (size <= 96) return 8
  return Math.min(24, Math.ceil(size / 8))
}

function next(text: string, start: number): number {
  const end = Math.min(text.length, start + step(text.length - start))
  const max = Math.min(text.length, end + 8)
  for (let i = end; i < max; i++) {
    if (SNAP.test(text[i] ?? '')) return i + 1
  }
  return end
}

export function PacedMarkdown(props: { text: string; cacheKey?: string; streaming: boolean; className?: string }) {
  const [shown, setShown] = useState(props.streaming ? '' : props.text)
  const shownRef = useRef(shown)
  const timerRef = useRef<ReturnType<typeof setTimeout>>()

  useEffect(() => { shownRef.current = shown }, [shown])

  useEffect(() => {
    const clear = () => { if (timerRef.current) { clearTimeout(timerRef.current); timerRef.current = undefined } }

    if (!props.streaming) {
      clear()
      setShown(props.text)
      return clear
    }

    // text 回退 / 完全不同 → 立即 sync
    if (!props.text.startsWith(shownRef.current) || props.text.length < shownRef.current.length) {
      clear()
      setShown(props.text)
      return clear
    }

    if (props.text.length === shownRef.current.length) return clear
    if (timerRef.current) return clear

    const tick = () => {
      timerRef.current = undefined
      const current = shownRef.current
      const target = props.text
      if (!props.streaming) { setShown(target); return }
      if (!target.startsWith(current) || target.length <= current.length) { setShown(target); return }
      const nextEnd = next(target, current.length)
      setShown(target.slice(0, nextEnd))
      if (nextEnd < target.length) timerRef.current = setTimeout(tick, PACE_MS)
    }
    timerRef.current = setTimeout(tick, PACE_MS)

    return clear
  }, [props.text, props.streaming])

  if (!shown) return null
  return <Markdown text={shown} cacheKey={props.cacheKey} streaming={props.streaming} className={props.className} />
}
```

- [ ] **Step 3：测试通过 + commit**

```bash
git add client/src/features/chat/components/effects/
git commit -m "feat(effects): PacedMarkdown for paced streaming reveal"
```

---

### Task 1.5：TextShimmer

**Files:**
- Create: `client/src/features/chat/components/effects/text-shimmer.tsx`
- Create: `client/src/features/chat/components/effects/text-shimmer.css`

- [ ] **Step 1：实现 text-shimmer.tsx**

```tsx
// text-shimmer.tsx
import { useEffect, useRef, useState } from 'react'
import './text-shimmer.css'

const SWAP_MS = 220

export function TextShimmer(props: { text: string; active?: boolean; className?: string; offset?: number }) {
  const active = props.active ?? true
  const [run, setRun] = useState(active)
  const timerRef = useRef<ReturnType<typeof setTimeout>>()

  useEffect(() => {
    if (timerRef.current) { clearTimeout(timerRef.current); timerRef.current = undefined }
    if (active) { setRun(true); return }
    timerRef.current = setTimeout(() => { timerRef.current = undefined; setRun(false) }, SWAP_MS)
    return () => { if (timerRef.current) clearTimeout(timerRef.current) }
  }, [active])

  return (
    <span
      data-component="text-shimmer"
      data-active={active ? 'true' : 'false'}
      className={props.className}
      style={{ '--text-shimmer-swap': `${SWAP_MS}ms`, '--text-shimmer-index': String(props.offset ?? 0) } as React.CSSProperties}
      aria-label={props.text}
    >
      <span data-slot="text-shimmer-char">
        <span data-slot="text-shimmer-char-base" aria-hidden="true">{props.text}</span>
        <span data-slot="text-shimmer-char-shimmer" data-run={run ? 'true' : 'false'} aria-hidden="true">{props.text}</span>
      </span>
    </span>
  )
}
```

- [ ] **Step 2：写 text-shimmer.css**

（直接移植 opencode 同文件全内容到此路径，保留 `@media (prefers-reduced-motion: reduce)` 分支。参见 spec §6.5）

```css
/* 复制自 opencode/packages/ui/src/components/text-shimmer.css 全部内容 */
/* 见 source file for full CSS */
[data-component="text-shimmer"] {
  --text-shimmer-step: 45ms;
  --text-shimmer-duration: 1200ms;
  --text-shimmer-swap: 220ms;
  --text-shimmer-index: 0;
  --text-shimmer-angle: 90deg;
  --text-shimmer-spread: 5.2ch;
  --text-shimmer-size: 360%;
  --text-shimmer-base-color: var(--text-weak, #888);
  --text-shimmer-peak-color: var(--text-strong, #111);
  --text-shimmer-sweep: linear-gradient(var(--text-shimmer-angle),
    transparent calc(50% - var(--text-shimmer-spread)),
    var(--text-shimmer-peak-color) 50%,
    transparent calc(50% + var(--text-shimmer-spread)));
  --text-shimmer-base: linear-gradient(var(--text-shimmer-base-color), var(--text-shimmer-base-color));
  display: inline-flex; align-items: baseline; font: inherit; letter-spacing: inherit; line-height: inherit;
}
[data-component="text-shimmer"] [data-slot="text-shimmer-char"] { display: inline-grid; white-space: pre; }
[data-component="text-shimmer"] [data-slot="text-shimmer-char-base"],
[data-component="text-shimmer"] [data-slot="text-shimmer-char-shimmer"] {
  grid-area: 1 / 1; white-space: pre;
  transition: opacity var(--text-shimmer-swap) ease-out;
}
[data-component="text-shimmer"] [data-slot="text-shimmer-char-base"] { color: inherit; opacity: 1; }
[data-component="text-shimmer"] [data-slot="text-shimmer-char-shimmer"] { color: var(--text-weaker, #aaa); opacity: 0; }
[data-component="text-shimmer"][data-active="true"] [data-slot="text-shimmer-char-shimmer"] { opacity: 1; }
[data-component="text-shimmer"] [data-slot="text-shimmer-char-shimmer"][data-run="true"] {
  animation: text-shimmer-sweep var(--text-shimmer-duration) infinite linear both;
  animation-delay: calc(var(--text-shimmer-step) * var(--text-shimmer-index) * -1);
  will-change: background-position;
}
@keyframes text-shimmer-sweep {
  0% { background-position: 100% 0, 0 0; }
  100% { background-position: 0% 0, 0 0; }
}
@supports ((-webkit-background-clip: text) or (background-clip: text)) {
  [data-component="text-shimmer"] [data-slot="text-shimmer-char-shimmer"] {
    color: transparent; -webkit-text-fill-color: transparent;
    background-image: var(--text-shimmer-sweep), var(--text-shimmer-base);
    background-size: var(--text-shimmer-size) 100%, 100% 100%;
    background-position: 100% 0, 0 0; background-repeat: no-repeat;
    -webkit-background-clip: text; background-clip: text;
  }
  [data-component="text-shimmer"][data-active="true"] [data-slot="text-shimmer-char-base"] { opacity: 0; }
}
@media (prefers-reduced-motion: reduce) {
  [data-component="text-shimmer"] [data-slot="text-shimmer-char-base"],
  [data-component="text-shimmer"] [data-slot="text-shimmer-char-shimmer"] { transition-duration: 0ms; }
  [data-component="text-shimmer"] [data-slot="text-shimmer-char-shimmer"] {
    animation: none !important; color: inherit; -webkit-text-fill-color: currentColor; background-image: none;
  }
}
```

- [ ] **Step 3：commit**

```bash
git add client/src/features/chat/components/effects/text-shimmer.*
git commit -m "feat(effects): TextShimmer CSS sweep animation"
```

---

### Task 1.6：TextReveal 逐词渐入

**Files:**
- Create: `client/src/features/chat/components/effects/text-reveal.tsx`
- Create: `client/src/features/chat/components/effects/text-reveal.css`

- [ ] **Step 1：实现（最小可工作版本）**

```tsx
// text-reveal.tsx
import { useEffect, useState, useMemo } from 'react'
import './text-reveal.css'

export function TextReveal(props: { text?: string; className?: string; travel?: number; duration?: number }) {
  const [renderedKey, setRenderedKey] = useState(0)
  const words = useMemo(() => (props.text ?? '').split(/(\s+)/).filter((w) => w.length > 0), [props.text])

  useEffect(() => { setRenderedKey((k) => k + 1) }, [props.text])

  if (!props.text) return null
  return (
    <span
      key={renderedKey}
      data-component="text-reveal"
      className={props.className}
      style={{ '--text-reveal-travel': `${props.travel ?? 25}px`, '--text-reveal-duration': `${props.duration ?? 700}ms` } as React.CSSProperties}
    >
      {words.map((w, i) => (
        <span key={i} data-slot="text-reveal-word" style={{ animationDelay: `${i * 30}ms` }}>{w}</span>
      ))}
    </span>
  )
}
```

- [ ] **Step 2：写 text-reveal.css**

```css
[data-component="text-reveal"] { display: inline; }
[data-slot="text-reveal-word"] {
  display: inline-block;
  opacity: 0;
  transform: translateY(var(--text-reveal-travel, 25px));
  filter: blur(4px);
  animation: text-reveal-in var(--text-reveal-duration, 700ms) cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
@keyframes text-reveal-in {
  to { opacity: 1; transform: translateY(0); filter: blur(0); }
}
@media (prefers-reduced-motion: reduce) {
  [data-slot="text-reveal-word"] { animation: none; opacity: 1; transform: none; filter: none; }
}
```

- [ ] **Step 3：commit**

```bash
git add client/src/features/chat/components/effects/text-reveal.*
git commit -m "feat(effects): TextReveal per-word fade-in"
```

---

### Task 1.7：AnimatedCount 数字滚动

**Files:**
- Create: `client/src/features/chat/components/effects/animated-count.tsx`

- [ ] **Step 1：实现（React 最小版）**

```tsx
// animated-count.tsx
import { useEffect, useState, useRef } from 'react'

export function AnimatedCount(props: { value: number; duration?: number }) {
  const [display, setDisplay] = useState(props.value)
  const prevRef = useRef(props.value)
  const duration = props.duration ?? 400

  useEffect(() => {
    const from = prevRef.current
    const to = props.value
    if (from === to) return
    const start = performance.now()
    let raf = 0
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - t, 3)
      setDisplay(Math.round(from + (to - from) * eased))
      if (t < 1) raf = requestAnimationFrame(tick)
      else prevRef.current = to
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [props.value, duration])

  return <span>{display}</span>
}
```

- [ ] **Step 2：commit**

```bash
git add client/src/features/chat/components/effects/animated-count.tsx
git commit -m "feat(effects): AnimatedCount number tween"
```

---

### Task 1.8：reasoning-heading.ts 提取标题

**Files:**
- Create: `client/src/features/chat/components/helpers/reasoning-heading.ts`
- Test: `client/src/features/chat/components/helpers/__tests__/reasoning-heading.test.ts`

- [ ] **Step 1：测试**

```typescript
import { describe, it, expect } from 'vitest'
import { extractHeading } from '../reasoning-heading'

describe('reasoning-heading', () => {
  it('extracts ATX heading', () => {
    expect(extractHeading('## Analyzing query')).toBe('Analyzing query')
  })
  it('extracts setext heading', () => {
    expect(extractHeading('Checking schema\n===')).toBe('Checking schema')
  })
  it('extracts bold line', () => {
    expect(extractHeading('**Thinking**')).toBe('Thinking')
  })
  it('extracts HTML h1', () => {
    expect(extractHeading('<h1>Parse</h1>')).toBe('Parse')
  })
  it('returns undefined for empty', () => {
    expect(extractHeading('')).toBeUndefined()
    expect(extractHeading('  \n')).toBeUndefined()
  })
})
```

- [ ] **Step 2：实现**

```typescript
// reasoning-heading.ts
function clean(s: string): string {
  return s
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/[*_~]+/g, '')
    .trim()
}

export function extractHeading(text: string): string | undefined {
  if (!text) return undefined
  const md = text.replace(/\r\n?/g, '\n')

  const html = md.match(/<h[1-6][^>]*>([\s\S]*?)<\/h[1-6]>/i)
  if (html?.[1]) {
    const v = clean(html[1].replace(/<[^>]+>/g, ' '))
    if (v) return v
  }
  const atx = md.match(/^\s{0,3}#{1,6}[ \t]+(.+?)(?:[ \t]+#+[ \t]*)?$/m)
  if (atx?.[1]) {
    const v = clean(atx[1])
    if (v) return v
  }
  const setext = md.match(/^([^\n]+)\n(?:=+|-+)\s*$/m)
  if (setext?.[1]) {
    const v = clean(setext[1])
    if (v) return v
  }
  const strong = md.match(/^\s*(?:\*\*|__)(.+?)(?:\*\*|__)\s*$/m)
  if (strong?.[1]) {
    const v = clean(strong[1])
    if (v) return v
  }
  return undefined
}
```

- [ ] **Step 3：测试通过 + commit**

```bash
git add client/src/features/chat/components/helpers/
git commit -m "feat(helpers): reasoning heading extractor"
```

---

### Task 1.9：risk.ts 优先级链

**Files:**
- Create: `client/src/features/chat/components/helpers/risk.ts`
- Test: `client/src/features/chat/components/helpers/__tests__/risk.test.ts`

- [ ] **Step 1：测试**

```typescript
import { describe, it, expect } from 'vitest'
import { resolveRisk, classifySqlRisk } from '../risk'

describe('risk', () => {
  it('classifySqlRisk L1 for SELECT', () => {
    expect(classifySqlRisk('SELECT * FROM t')).toBe('L1')
    expect(classifySqlRisk('  -- comment\nEXPLAIN SELECT x')).toBe('L1')
  })
  it('classifySqlRisk L2 for INSERT / UPDATE', () => {
    expect(classifySqlRisk('INSERT INTO t VALUES(1)')).toBe('L2')
    expect(classifySqlRisk('UPDATE t SET x=1')).toBe('L2')
    expect(classifySqlRisk('CREATE INDEX i ON t(x)')).toBe('L2')
  })
  it('classifySqlRisk L3 for DELETE / DROP', () => {
    expect(classifySqlRisk('DELETE FROM t')).toBe('L3')
    expect(classifySqlRisk('DROP TABLE t')).toBe('L3')
    expect(classifySqlRisk('TRUNCATE t')).toBe('L3')
  })
  it('classifySqlRisk returns null for WITH / unknown', () => {
    expect(classifySqlRisk('WITH x AS (SELECT 1) UPDATE y SET a=1')).toBeNull()
    expect(classifySqlRisk('gibberish')).toBeNull()
  })
  it('resolveRisk prefers part-level over descriptor', () => {
    expect(resolveRisk({ state: { metadata: { riskLevel: 'L3' } } } as any, { riskLevel: 'L1' } as any)).toBe('L3')
  })
  it('resolveRisk falls back to descriptor', () => {
    expect(resolveRisk({ state: { metadata: {} } } as any, { riskLevel: 'L2' } as any)).toBe('L2')
  })
  it('resolveRisk returns null when nothing available', () => {
    expect(resolveRisk({ state: {} } as any, {} as any)).toBeNull()
  })
})
```

- [ ] **Step 2：实现**

```typescript
// risk.ts
import type { ToolPart } from '@/services/channel/types'
import type { ActionDescriptor } from '@/features/actions/registry'

export type RiskLevel = 'L1' | 'L2' | 'L3'

const RISK_STYLES: Record<RiskLevel, { dot: string; border: string; label: string }> = {
  L1: { dot: 'bg-green-500', border: 'border-green-500/40', label: 'L1' },
  L2: { dot: 'bg-yellow-500', border: 'border-yellow-500/40', label: 'L2' },
  L3: { dot: 'bg-red-500', border: 'border-red-500/40', label: 'L3' },
}

export function getRiskStyles(risk: RiskLevel | null) {
  return risk ? RISK_STYLES[risk] : null
}

function stripComments(sql: string): string {
  return sql.replace(/\/\*[\s\S]*?\*\//g, '').replace(/--.*$/gm, '').trim()
}

export function classifySqlRisk(sql: string): RiskLevel | null {
  const clean = stripComments(sql).replace(/^\s+/, '')
  if (/^(SELECT|EXPLAIN|SHOW|DESC(?:RIBE)?)\b/i.test(clean)) return 'L1'
  if (/^(INSERT|UPDATE|CREATE\s+(?:INDEX|VIEW))\b/i.test(clean)) return 'L2'
  if (/^(DELETE|DROP|ALTER|TRUNCATE|GRANT|REVOKE)\b/i.test(clean)) return 'L3'
  return null
}

export function resolveRisk(part: ToolPart | undefined, descriptor: Pick<ActionDescriptor, 'riskLevel'> | undefined): RiskLevel | null {
  // 1. part-level
  const pLevel = (part as any)?.state?.metadata?.riskLevel
  if (pLevel === 'L1' || pLevel === 'L2' || pLevel === 'L3') return pLevel
  // 2. descriptor
  const dLevel = descriptor?.riskLevel
  if (dLevel === 'L1' || dLevel === 'L2' || dLevel === 'L3') return dLevel
  // 3. 前端正则粗判：仅对含 sql 的 input
  const sql = (part as any)?.state?.input?.sql
  if (typeof sql === 'string') {
    const r = classifySqlRisk(sql)
    if (r) return r
  }
  // 4. 保守
  return null
}
```

同时在 `features/actions/registry.ts` 的 `ActionDescriptor` 类型加可选字段（见 Phase 2.0）：

- [ ] **Step 3：测试通过 + commit**

```bash
git add client/src/features/chat/components/helpers/risk.*
git commit -m "feat(helpers): risk priority chain (part-level > descriptor > regex)"
```

---

## Phase 2：BasicTool + ToolRegistry + Renderers

### Task 2.0：扩展 ActionDescriptor

**Files:**
- Modify: `client/src/features/actions/registry.ts`

- [ ] **Step 1：在 `ActionDescriptor` 类型里加可选 `riskLevel` / `category`**

查找 `ActionDescriptor` 定义（grep），加入：

```typescript
export type ActionDescriptor = {
  // ...existing fields...
  riskLevel?: 'L1' | 'L2' | 'L3' | null
  category?: 'metadata' | 'query' | 'mutation' | 'artifact' | 'ddl' | 'question' | 'misc'
}
```

- [ ] **Step 2：typecheck + commit**

```bash
git add client/src/features/actions/registry.ts
git commit -m "feat(actions): extend ActionDescriptor with riskLevel / category"
```

---

### Task 2.1：BasicTool 折叠卡

**Files:**
- Create: `client/src/features/chat/components/tools/basic-tool.tsx`
- Create: `client/src/features/chat/components/tools/basic-tool.css`
- Test: `client/src/features/chat/components/tools/__tests__/basic-tool.test.tsx`

- [ ] **Step 1：测试**

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, fireEvent, screen } from '@testing-library/react'
import { BasicTool } from '../basic-tool'

describe('BasicTool', () => {
  it('pending wraps title with shimmer and hides expand arrow', () => {
    const { container } = render(<BasicTool icon="mcp" status="pending" trigger={{ title: 'Reading' }}>detail</BasicTool>)
    expect(container.querySelector('[data-component="text-shimmer"][data-active="true"]')).not.toBeNull()
    expect(container.querySelector('[data-slot="basic-tool-arrow"]')).toBeNull()
  })
  it('completed clicks trigger toggles open', () => {
    const { container } = render(<BasicTool icon="mcp" status="completed" trigger={{ title: 'Done' }}>body</BasicTool>)
    expect(container.querySelector('[data-open="true"]')).toBeNull()
    fireEvent.click(container.querySelector('[data-component="tool-trigger"]')!)
    expect(container.querySelector('[data-open="true"]')).not.toBeNull()
  })
  it('locked blocks collapse click', () => {
    const { container } = render(<BasicTool icon="mcp" status="completed" trigger={{ title: 'Q' }} locked defaultOpen>body</BasicTool>)
    fireEvent.click(container.querySelector('[data-component="tool-trigger"]')!)
    expect(container.querySelector('[data-open="true"]')).not.toBeNull()
  })
})
```

- [ ] **Step 2：实现 basic-tool.tsx**

```tsx
// basic-tool.tsx
import { ReactNode, useState } from 'react'
import { TextShimmer } from '../effects/text-shimmer'
import type { RiskLevel } from '../helpers/risk'
import { getRiskStyles } from '../helpers/risk'
import { cn } from '@/lib/utils'
import './basic-tool.css'

export type TriggerTitle = {
  title: string
  subtitle?: string
  args?: string[]
  action?: ReactNode
}

const isTriggerTitle = (v: unknown): v is TriggerTitle =>
  typeof v === 'object' && v !== null && 'title' in (v as any)

export type BasicToolStatus = 'pending' | 'running' | 'completed' | 'error'

export function BasicTool(props: {
  icon: string
  risk?: RiskLevel | null
  variant?: 'risk' | 'question'
  trigger: TriggerTitle | ReactNode
  children?: ReactNode
  status?: BasicToolStatus
  hideDetails?: boolean
  defaultOpen?: boolean
  forceOpen?: boolean
  locked?: boolean
}) {
  const [openState, setOpenState] = useState(props.defaultOpen ?? false)
  const open = props.forceOpen || openState
  const pending = props.status === 'pending' || props.status === 'running'
  const riskStyles = props.variant === 'question' ? null : getRiskStyles(props.risk ?? null)

  const handleToggle = () => {
    if (pending) return
    if (props.locked && open) return
    setOpenState((v) => !v)
  }

  const t = props.trigger
  return (
    <div
      data-component="basic-tool"
      data-status={props.status}
      data-variant={props.variant ?? 'risk'}
      className={cn('rounded-md border my-2', riskStyles?.border, props.variant === 'question' && 'border-blue-400/50')}
    >
      <button type="button" data-component="tool-trigger" data-open={open ? 'true' : 'false'} onClick={handleToggle} className="flex w-full items-center gap-2 px-3 py-2 text-left">
        {riskStyles && <span data-slot="risk-dot" className={cn('size-2 rounded-full', riskStyles.dot)} aria-label={riskStyles.label} />}
        {props.variant === 'question' && <span data-slot="question-icon" className="text-blue-500">?</span>}
        {isTriggerTitle(t) ? (
          <div className="flex min-w-0 flex-1 items-baseline gap-2">
            <span data-slot="basic-tool-tool-title" className="font-medium">
              <TextShimmer text={t.title} active={pending} />
            </span>
            {!pending && t.subtitle && <span data-slot="basic-tool-tool-subtitle" className="text-xs text-muted-foreground truncate">{t.subtitle}</span>}
            {!pending && t.args?.map((a, i) => <span key={i} data-slot="basic-tool-tool-arg" className="text-xs font-mono text-muted-foreground">{a}</span>)}
          </div>
        ) : t}
        {!pending && !props.hideDetails && !props.locked && props.children && (
          <span data-slot="basic-tool-arrow" className={cn('transition-transform', open && 'rotate-180')}>▼</span>
        )}
      </button>
      {open && !props.hideDetails && props.children && (
        <div data-slot="basic-tool-body" className="border-t px-3 py-2">{props.children}</div>
      )}
    </div>
  )
}
```

- [ ] **Step 3：写 basic-tool.css（最小）**

```css
[data-component="basic-tool"] { font-size: 14px; }
```

- [ ] **Step 4：commit**

```bash
git add client/src/features/chat/components/tools/basic-tool.*
git commit -m "feat(tools): BasicTool foldable card with shimmer title"
```

---

### Task 2.2：ToolRegistry 字典

**Files:**
- Create: `client/src/features/chat/components/tools/tool-registry.ts`
- Test: `client/src/features/chat/components/tools/__tests__/tool-registry.test.ts`

- [ ] **Step 1：测试**

```typescript
import { describe, it, expect } from 'vitest'
import { ToolRegistry } from '../tool-registry'

describe('ToolRegistry', () => {
  it('register/get roundtrip', () => {
    const r = () => null
    ToolRegistry.register('x1', r as any)
    expect(ToolRegistry.get('x1')).toBe(r)
  })
  it('miss returns undefined', () => {
    expect(ToolRegistry.get('__unknown__')).toBeUndefined()
  })
  it('override replaces existing', () => {
    const a = () => null
    const b = () => null
    ToolRegistry.register('x2', a as any)
    ToolRegistry.register('x2', b as any)
    expect(ToolRegistry.get('x2')).toBe(b)
  })
})
```

- [ ] **Step 2：实现**

```typescript
// tool-registry.ts
import type { ComponentType } from 'react'
import type { ActionDescriptor } from '@/features/actions/registry'
import type { ToolPart } from '@/services/channel/types'

export type ToolRendererProps = {
  part: ToolPart
  descriptor: ActionDescriptor
  defaultOpen?: boolean
}

export type ToolRenderer = ComponentType<ToolRendererProps>

const registry = new Map<string, ToolRenderer>()

export const ToolRegistry = {
  register(name: string, renderer: ToolRenderer) { registry.set(name, renderer) },
  get(name: string): ToolRenderer | undefined { return registry.get(name) },
}
```

- [ ] **Step 3：测试通过 + commit**

```bash
git add client/src/features/chat/components/tools/tool-registry.ts client/src/features/chat/components/tools/__tests__/
git commit -m "feat(tools): ToolRegistry dictionary"
```

---

### Task 2.3：ToolErrorBoundary

**Files:**
- Create: `client/src/features/chat/components/tools/tool-error-boundary.tsx`

- [ ] **Step 1：实现**

```tsx
// tool-error-boundary.tsx
import { Component, type ReactNode } from 'react'

export class ToolErrorBoundary extends Component<
  { fallback: ReactNode; children: ReactNode },
  { hasError: boolean }
> {
  state = { hasError: false }
  static getDerivedStateFromError() { return { hasError: true } }
  componentDidCatch(err: Error) { console.error('[ToolErrorBoundary]', err) }
  render() { return this.state.hasError ? this.props.fallback : this.props.children }
}
```

- [ ] **Step 2：commit**

```bash
git add client/src/features/chat/components/tools/tool-error-boundary.tsx
git commit -m "feat(tools): ToolErrorBoundary for renderer crash isolation"
```

---

### Task 2.4：GenericTool（默认渲染器）

**Files:**
- Create: `client/src/features/chat/components/tools/renderers/generic-tool.tsx`

- [ ] **Step 1：实现**

```tsx
// renderers/generic-tool.tsx
import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { resolveRisk } from '../../helpers/risk'

function summarizeInput(input: Record<string, any> | undefined): { subtitle?: string; args: string[] } {
  if (!input) return { args: [] }
  const keys = ['description', 'query', 'url', 'filePath', 'path', 'pattern', 'name', 'sql']
  const subtitle = keys.map((k) => input[k]).find((v): v is string => typeof v === 'string' && v.length > 0)
  const skip = new Set(keys)
  const args = Object.entries(input)
    .filter(([k]) => !skip.has(k))
    .flatMap(([k, v]) => {
      if (typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean') return [`${k}=${v}`]
      return []
    })
    .slice(0, 3)
  return { subtitle, args }
}

export function GenericTool(props: ToolRendererProps) {
  const { part, descriptor } = props
  const risk = resolveRisk(part, descriptor)
  const { subtitle, args } = summarizeInput(part.state.input)
  const variant = descriptor.category === 'question' ? 'question' : 'risk'
  return (
    <BasicTool
      icon="mcp"
      risk={risk}
      variant={variant}
      status={part.state.status}
      trigger={{ title: part.tool, subtitle, args }}
      defaultOpen={props.defaultOpen}
    >
      {part.state.output ? (
        <pre className="text-xs overflow-x-auto">{typeof part.state.output === 'string' ? part.state.output : JSON.stringify(part.state.output, null, 2)}</pre>
      ) : null}
    </BasicTool>
  )
}
```

- [ ] **Step 2：commit**

```bash
git add client/src/features/chat/components/tools/renderers/generic-tool.tsx
git commit -m "feat(tools): GenericTool default renderer"
```

---

### Task 2.5-2.7：metadata 类渲染器（describe_table / list_tables / show_schema）

**Files:**
- Create: `client/src/features/chat/components/tools/renderers/metadata-renderers.tsx`

- [ ] **Step 1：一次性实现三个（都是简单 BasicTool 包装）**

```tsx
// renderers/metadata-renderers.tsx
import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'

export function DescribeTable(props: ToolRendererProps) {
  const { part } = props
  const table = (part.state.input?.table as string) ?? (part.state.input?.name as string) ?? ''
  return (
    <BasicTool icon="mcp" risk="L1" status={part.state.status} trigger={{ title: '查看表结构', subtitle: table }}>
      {part.state.output ? <pre className="text-xs overflow-x-auto">{String(part.state.output)}</pre> : null}
    </BasicTool>
  )
}

export function ListTables(props: ToolRendererProps) {
  const { part } = props
  return (
    <BasicTool icon="mcp" risk="L1" status={part.state.status} trigger={{ title: '列出表' }}>
      {part.state.output ? <pre className="text-xs overflow-x-auto">{String(part.state.output)}</pre> : null}
    </BasicTool>
  )
}

export function ShowSchema(props: ToolRendererProps) {
  const { part } = props
  return (
    <BasicTool icon="mcp" risk="L1" status={part.state.status} trigger={{ title: '查看 schema' }}>
      {part.state.output ? <pre className="text-xs overflow-x-auto">{String(part.state.output)}</pre> : null}
    </BasicTool>
  )
}
```

- [ ] **Step 2：commit**

```bash
git add client/src/features/chat/components/tools/renderers/metadata-renderers.tsx
git commit -m "feat(tools): metadata renderers (describe_table / list_tables / show_schema)"
```

---

### Task 2.8：ExecuteSql renderer

**Files:**
- Create: `client/src/features/chat/components/tools/renderers/execute-sql.tsx`

- [ ] **Step 1：实现**

```tsx
// renderers/execute-sql.tsx
import { BasicTool } from '../basic-tool'
import { Markdown } from '../../markdown/markdown'
import type { ToolRendererProps } from '../tool-registry'
import { resolveRisk } from '../../helpers/risk'

export function ExecuteSql(props: ToolRendererProps) {
  const { part, descriptor } = props
  const sql = (part.state.input?.sql as string) ?? ''
  const risk = resolveRisk(part, descriptor)
  const output = part.state.output as { rows?: any[]; rowCount?: number; columns?: string[] } | undefined

  const subtitle = output?.rowCount !== undefined ? `${output.rowCount} 行` : ''

  return (
    <BasicTool
      icon="code"
      risk={risk}
      status={part.state.status}
      trigger={{ title: '执行 SQL', subtitle }}
      defaultOpen={props.defaultOpen}
    >
      {sql && <Markdown text={'```sql\n' + sql + '\n```'} cacheKey={`${part.id}:sql`} />}
      {output?.rows && output.rows.length > 0 && (
        <div className="mt-2 text-xs">
          <div className="text-muted-foreground">前 {Math.min(5, output.rows.length)} 行：</div>
          <pre className="overflow-x-auto">{JSON.stringify(output.rows.slice(0, 5), null, 2)}</pre>
        </div>
      )}
    </BasicTool>
  )
}
```

- [ ] **Step 2：commit**

```bash
git add client/src/features/chat/components/tools/renderers/execute-sql.tsx
git commit -m "feat(tools): ExecuteSql renderer"
```

---

### Task 2.9：PreviewSql renderer（L2/L3 确认流程）

**Files:**
- Create: `client/src/features/chat/components/tools/renderers/preview-sql.tsx`

- [ ] **Step 1：实现**

```tsx
// renderers/preview-sql.tsx
import { useState } from 'react'
import { BasicTool } from '../basic-tool'
import { Markdown } from '../../markdown/markdown'
import { Button } from '@/components/ui/button'
import type { ToolRendererProps } from '../tool-registry'
import { resolveRisk } from '../../helpers/risk'
import { useChannel } from '@/services/channel/use-channel'

export function PreviewSql(props: ToolRendererProps) {
  const { part, descriptor } = props
  const sql = (part.state.input?.sql as string) ?? ''
  const risk = resolveRisk(part, descriptor)
  const impactRows = (part.state.metadata?.impactRows as number | undefined)
  const callID = part.callID ?? part.id
  const { client } = useChannel()
  const [decided, setDecided] = useState<'confirmed' | 'cancelled' | null>(null)

  const decide = (ok: boolean) => {
    if (decided || !client) return
    setDecided(ok ? 'confirmed' : 'cancelled')
    client.actionResult(callID, true, { confirmed: ok })
  }

  return (
    <BasicTool
      icon="code"
      risk={risk}
      status={part.state.status}
      trigger={{ title: risk === 'L3' ? '强确认 SQL' : '预览 SQL', subtitle: impactRows !== undefined ? `影响 ${impactRows} 行` : '' }}
      forceOpen
      locked={part.state.status === 'pending' || part.state.status === 'running'}
    >
      {sql && <Markdown text={'```sql\n' + sql + '\n```'} cacheKey={`${part.id}:sql`} />}
      {impactRows !== undefined && <div className="mt-2 text-lg font-semibold">将影响 {impactRows} 行</div>}
      {!decided && (
        <div className="mt-3 flex gap-2">
          <Button size="sm" variant="default" onClick={() => decide(true)}>执行</Button>
          <Button size="sm" variant="outline" onClick={() => decide(false)}>取消</Button>
        </div>
      )}
      {decided && <div className="mt-2 text-xs text-muted-foreground">{decided === 'confirmed' ? '已确认执行' : '已取消'}</div>}
    </BasicTool>
  )
}
```

- [ ] **Step 2：commit**

```bash
git add client/src/features/chat/components/tools/renderers/preview-sql.tsx
git commit -m "feat(tools): PreviewSql L2/L3 confirmation renderer"
```

---

### Task 2.10：Question renderer

**Files:**
- Create: `client/src/features/chat/components/tools/renderers/question.tsx`

- [ ] **Step 1：实现**

```tsx
// renderers/question.tsx
import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'

export function Question(props: ToolRendererProps) {
  const { part } = props
  const status = part.state.status
  // pending/running 时整个 part 不渲染（阻塞于 composer 对话）
  if (status === 'pending' || status === 'running') return null

  const question = (part.state.input?.question as string) ?? ''
  const answer = (part.state.output as { answer?: string } | string | undefined)
  const answerText = typeof answer === 'string' ? answer : answer?.answer ?? ''

  return (
    <BasicTool icon="bubble" variant="question" status={status} trigger={{ title: question || '问题' }} defaultOpen>
      {answerText && <div className="text-sm">{answerText}</div>}
    </BasicTool>
  )
}
```

- [ ] **Step 2：commit**

```bash
git add client/src/features/chat/components/tools/renderers/question.tsx
git commit -m "feat(tools): Question renderer with independent visual"
```

---

### Task 2.11：ArtifactCreated renderer（跳 Stage）

**Files:**
- Create: `client/src/features/chat/components/tools/renderers/artifact-created.tsx`

- [ ] **Step 1：实现**

```tsx
// renderers/artifact-created.tsx
import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'

const KIND_ICONS: Record<string, string> = { table: '📊', chart: '📈', erd: '🔗' }

export function ArtifactCreated(props: ToolRendererProps) {
  const { part } = props
  const kind = (part.state.output as any)?.kind ?? (part.state.metadata?.kind as string) ?? 'table'
  const title = (part.state.output as any)?.title ?? (part.state.metadata?.title as string) ?? `${kind} artifact`
  const sessionId = useSessionStore.getState().activeSessionId

  const openStage = () => {
    if (sessionId) useStageStore.getState().openSession(sessionId)
  }

  return (
    <BasicTool
      icon="artifact"
      risk="L1"
      status={part.state.status}
      trigger={{ title: `${KIND_ICONS[kind] ?? '📦'} ${title}`, subtitle: '在 Stage 中查看 →', action: null }}
      hideDetails
    >
      <button onClick={openStage} className="text-xs text-primary hover:underline">打开 Stage</button>
    </BasicTool>
  )
}
```

**注意**：`useStageStore.openSession` 如不存在，查当前 store API 调整。

- [ ] **Step 2：verify store method exists**

Run: `grep -n 'openSession\|enterSplit' client/src/stores/stage-store.ts`

如果 `openSession` 不存在，改成已有的方法（`enterSplit` 或等价）。

- [ ] **Step 3：commit**

```bash
git add client/src/features/chat/components/tools/renderers/artifact-created.tsx
git commit -m "feat(tools): ArtifactCreated renderer with Stage jump"
```

---

### Task 2.12：统一注册所有内建 renderer

**Files:**
- Create: `client/src/features/chat/components/tools/renderers/index.ts`

- [ ] **Step 1：实现**

```typescript
// renderers/index.ts
import { ToolRegistry } from '../tool-registry'
import { ExecuteSql } from './execute-sql'
import { PreviewSql } from './preview-sql'
import { DescribeTable, ListTables, ShowSchema } from './metadata-renderers'
import { ArtifactCreated } from './artifact-created'
import { Question } from './question'

let registered = false
export function registerBuiltInRenderers() {
  if (registered) return
  registered = true
  ToolRegistry.register('execute_sql', ExecuteSql)
  ToolRegistry.register('preview_sql', PreviewSql)
  ToolRegistry.register('describe_table', DescribeTable)
  ToolRegistry.register('list_tables', ListTables)
  ToolRegistry.register('show_schema', ShowSchema)
  ToolRegistry.register('artifact_created', ArtifactCreated)
  ToolRegistry.register('question', Question)
}
```

- [ ] **Step 2：在 `client/src/main.tsx` 或根组件中调用一次 `registerBuiltInRenderers()`**

Grep: `grep -n 'ReactDOM.createRoot\|createRoot' client/src/main.tsx`

在 `createRoot` 前加：
```typescript
import { registerBuiltInRenderers } from '@/features/chat/components/tools/renderers'
registerBuiltInRenderers()
```

- [ ] **Step 3：commit**

```bash
git add client/src/features/chat/components/tools/renderers/index.ts client/src/main.tsx
git commit -m "feat(tools): register built-in renderers on bootstrap"
```

---

## Phase 3：TurnList / SessionTurn / UserBubble / AssistantStream / ContextToolGroup

### Task 3.1：group-parts.ts 纯函数

**Files:**
- Create: `client/src/features/chat/components/helpers/group-parts.ts`
- Test: `client/src/features/chat/components/helpers/__tests__/group-parts.test.ts`

- [ ] **Step 1：测试**

```typescript
import { describe, it, expect } from 'vitest'
import { groupParts } from '../group-parts'

const meta = { id: '', sessionID: 's', messageID: 'm', metadata: {} }
const toolPart = (id: string, tool: string, category?: string) => ({
  ...meta, id, type: 'tool', tool,
  state: { status: 'completed', input: {} },
  __descriptorCategory: category,
}) as any
const textPart = (id: string, text: string) => ({ ...meta, id, type: 'text', text }) as any

describe('groupParts', () => {
  it('merges consecutive metadata tools', () => {
    const parts = [
      toolPart('t1', 'describe_table', 'metadata'),
      toolPart('t2', 'list_tables', 'metadata'),
      textPart('x', 'hi'),
      toolPart('t3', 'describe_table', 'metadata'),
    ]
    const groups = groupParts(parts, (p: any) => p.__descriptorCategory === 'metadata')
    expect(groups.length).toBe(3)
    expect(groups[0].type).toBe('context-group')
    expect((groups[0] as any).refs.length).toBe(2)
    expect(groups[1].type).toBe('part')
    expect(groups[2].type).toBe('context-group')
  })
  it('empty array returns empty', () => {
    expect(groupParts([], () => false)).toEqual([])
  })
})
```

- [ ] **Step 2：实现**

```typescript
// group-parts.ts
import type { Part } from '@/services/channel/types'

export type PartGroup =
  | { type: 'part'; ref: Part }
  | { type: 'context-group'; refs: Part[]; key: string }

export type IsContextGroupTool = (part: Part) => boolean

export function groupParts(parts: Part[], isContextGroupTool: IsContextGroupTool): PartGroup[] {
  const result: PartGroup[] = []
  let group: Part[] = []

  const flush = () => {
    if (group.length > 0) {
      result.push({ type: 'context-group', refs: group, key: `ctx:${group[0].id}` })
      group = []
    }
  }

  for (const p of parts) {
    if (p.type === 'tool' && isContextGroupTool(p)) {
      group.push(p)
    } else {
      flush()
      result.push({ type: 'part', ref: p })
    }
  }
  flush()
  return result
}
```

- [ ] **Step 3：测试 + commit**

```bash
git add client/src/features/chat/components/helpers/group-parts.* client/src/features/chat/components/helpers/__tests__/group-parts.test.ts
git commit -m "feat(helpers): groupParts merges consecutive metadata tools"
```

---

### Task 3.2：use-session-turns.ts 派生 hook

**Files:**
- Create: `client/src/features/chat/components/helpers/use-session-turns.ts`
- Test: `client/src/features/chat/components/helpers/__tests__/use-session-turns.test.ts`

- [ ] **Step 1：测试（核心切块规则）**

```typescript
import { describe, it, expect, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useSessionTurns } from '../use-session-turns'

describe('useSessionTurns', () => {
  beforeEach(() => {
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
    })
  })

  it('splits on user messages', () => {
    const store = useChatPartsStore.getState()
    store.upsertInfo('s', { id: 'u1', role: 'user', sessionID: 's', time: { created: 1 } })
    store.upsertInfo('s', { id: 'a1', role: 'assistant', sessionID: 's', time: { created: 2 } })
    store.upsertInfo('s', { id: 'u2', role: 'user', sessionID: 's', time: { created: 3 } })
    store.upsertInfo('s', { id: 'a2', role: 'assistant', sessionID: 's', time: { created: 4 } })

    const { result } = renderHook(() => useSessionTurns('s'))
    expect(result.current).toHaveLength(2)
    expect(result.current[0].userMessageId).toBe('u1')
    expect(result.current[0].assistantMessageIds).toEqual(['a1'])
    expect(result.current[1].userMessageId).toBe('u2')
    expect(result.current[1].assistantMessageIds).toEqual(['a2'])
  })

  it('orphan assistant goes into user-less turn', () => {
    const store = useChatPartsStore.getState()
    store.upsertInfo('s', { id: 'a1', role: 'assistant', sessionID: 's', time: { created: 1 } })
    const { result } = renderHook(() => useSessionTurns('s'))
    expect(result.current).toHaveLength(1)
    expect(result.current[0].userMessageId).toBeUndefined()
    expect(result.current[0].assistantMessageIds).toEqual(['a1'])
  })
})
```

- [ ] **Step 2：实现**

```typescript
// use-session-turns.ts
import { useMemo } from 'react'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import type { MessageInfo } from '@/services/channel/types'

export type Turn = {
  userMessageId?: string
  userInfo?: MessageInfo
  assistantMessageIds: string[]
}

export function useSessionTurns(sessionId: string | null): Turn[] {
  const infoMap = useChatPartsStore((s) => (sessionId ? s.infoBySession.get(sessionId) : undefined))

  return useMemo(() => {
    if (!sessionId || !infoMap) return []
    const sorted = Array.from(infoMap.values()).sort((a, b) => (a.time.created ?? 0) - (b.time.created ?? 0))
    const turns: Turn[] = []
    let current: Turn | null = null

    for (const info of sorted) {
      if (info.role === 'user') {
        if (current) turns.push(current)
        current = { userMessageId: info.id, userInfo: info, assistantMessageIds: [] }
      } else if (info.role === 'assistant') {
        if (!current) current = { userMessageId: undefined, userInfo: undefined, assistantMessageIds: [] }
        current.assistantMessageIds.push(info.id)
      }
    }
    if (current) turns.push(current)
    return turns
  }, [sessionId, infoMap])
}
```

- [ ] **Step 3：commit**

```bash
git add client/src/features/chat/components/helpers/use-session-turns.* client/src/features/chat/components/helpers/__tests__/use-session-turns.test.ts
git commit -m "feat(helpers): useSessionTurns derive user-split turns"
```

---

### Task 3.3：PartDispatcher

**Files:**
- Create: `client/src/features/chat/components/turn/part-dispatcher.tsx`
- Create: `client/src/features/chat/components/turn/unknown-part.tsx`

- [ ] **Step 1：UnknownPart 实现**

```tsx
// unknown-part.tsx
import { useState } from 'react'

export function UnknownPart(props: { part: { type: string; [k: string]: unknown } }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="my-2 rounded border border-dashed border-yellow-500/40 p-2 text-xs">
      <button onClick={() => setOpen(!open)} className="flex items-center gap-2 text-yellow-700 dark:text-yellow-400">
        <span>⚠</span>
        <span>未知类型：<code className="font-mono">{props.part.type}</code></span>
      </button>
      {open && (
        <pre className="mt-2 overflow-x-auto text-[11px] text-muted-foreground">{JSON.stringify(props.part, null, 2)}</pre>
      )}
    </div>
  )
}
```

- [ ] **Step 2：PartDispatcher**

```tsx
// part-dispatcher.tsx
import type { ComponentType } from 'react'
import type { Part, MessageInfo } from '@/services/channel/types'
import { TextPart } from './text-part'
import { ReasoningPart } from './reasoning-part'
import { ToolPart } from './tool-part'
import { UnknownPart } from './unknown-part'

export type PartComponentProps = {
  part: Part
  info: MessageInfo
  showCopy?: boolean
  turnDurationMs?: number
}

export type PartComponent = ComponentType<PartComponentProps>

const PART_MAPPING: Record<string, PartComponent> = {
  text: TextPart,
  reasoning: ReasoningPart,
  tool: ToolPart,
  'step-start': () => null,   // 流内分隔，不渲染
  'step-finish': () => null,
  compaction: () => null,
}

export function PartDispatcher(props: PartComponentProps) {
  const Comp = PART_MAPPING[props.part.type]
  if (!Comp) return <UnknownPart part={props.part as any} />
  return <Comp {...props} />
}
```

- [ ] **Step 3：commit（先占位，text/reasoning/tool 在后续任务实现）**

```bash
git add client/src/features/chat/components/turn/part-dispatcher.tsx client/src/features/chat/components/turn/unknown-part.tsx
git commit -m "feat(turn): PartDispatcher + UnknownPart fallback"
```

---

### Task 3.4：TextPart（Markdown + PacedMarkdown）

**Files:**
- Create: `client/src/features/chat/components/turn/text-part.tsx`

- [ ] **Step 1：实现**

```tsx
// text-part.tsx
import { useState } from 'react'
import type { PartComponentProps } from './part-dispatcher'
import { Markdown } from '../markdown/markdown'
import { PacedMarkdown } from '../effects/paced-markdown'
import type { TextPart as TextPartType } from '@/services/channel/types'

export function TextPart(props: PartComponentProps) {
  const part = props.part as TextPartType
  const streaming = props.info.role === 'assistant' && typeof props.info.time.completed !== 'number'
  const text = (part.text ?? '').trim()
  const [copied, setCopied] = useState(false)

  if (!text) return null

  const handleCopy = async () => {
    await navigator.clipboard?.writeText?.(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div data-component="text-part" className="my-1">
      {streaming ? (
        <PacedMarkdown text={text} cacheKey={part.id} streaming />
      ) : (
        <Markdown text={text} cacheKey={part.id} />
      )}
      {props.showCopy && (
        <div className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
          <button onClick={handleCopy} className="hover:text-foreground">{copied ? '✓' : '复制'}</button>
          {props.info.role === 'assistant' && props.info.modelID && <span>· {props.info.modelID}</span>}
          {props.turnDurationMs !== undefined && props.turnDurationMs >= 0 && (
            <span>· {Math.round(props.turnDurationMs / 1000)}s</span>
          )}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 2：commit**

```bash
git add client/src/features/chat/components/turn/text-part.tsx
git commit -m "feat(turn): TextPart with paced markdown + copy"
```

---

### Task 3.5：ReasoningPart

**Files:**
- Create: `client/src/features/chat/components/turn/reasoning-part.tsx`

- [ ] **Step 1：实现**

```tsx
// reasoning-part.tsx
import type { PartComponentProps } from './part-dispatcher'
import { Markdown } from '../markdown/markdown'
import { PacedMarkdown } from '../effects/paced-markdown'
import type { ReasoningPart as RPartType } from '@/services/channel/types'

export function ReasoningPart(props: PartComponentProps) {
  const part = props.part as RPartType
  const streaming = props.info.role === 'assistant' && typeof props.info.time.completed !== 'number'
  const text = (part.text ?? '').trim()
  if (!text) return null
  return (
    <div data-component="reasoning-part" className="my-1 rounded border-l-2 border-muted pl-3 text-sm text-muted-foreground">
      {streaming ? (
        <PacedMarkdown text={text} cacheKey={part.id} streaming />
      ) : (
        <Markdown text={text} cacheKey={part.id} />
      )}
    </div>
  )
}
```

- [ ] **Step 2：commit**

```bash
git add client/src/features/chat/components/turn/reasoning-part.tsx
git commit -m "feat(turn): ReasoningPart"
```

---

### Task 3.6：ToolPart（接 ToolRegistry + ErrorBoundary）

**Files:**
- Create: `client/src/features/chat/components/turn/tool-part.tsx`

- [ ] **Step 1：实现**

```tsx
// tool-part.tsx
import type { PartComponentProps } from './part-dispatcher'
import type { ToolPart as ToolPartType } from '@/services/channel/types'
import { ToolRegistry } from '../tools/tool-registry'
import { GenericTool } from '../tools/renderers/generic-tool'
import { ToolErrorBoundary } from '../tools/tool-error-boundary'
import { useActionRegistryStore } from '@/stores/action-registry-store'
import { getRenderers } from '@/features/actions/registry'

export function ToolPart(props: PartComponentProps) {
  const part = props.part as ToolPartType
  const descriptor = useActionRegistryStore((s) => s.descriptors[part.tool]) ?? {
    id: part.tool, executor: 'SERVER', description: part.tool,
    inputSchema: {}, outputSchema: {}, produces: [], sideEffects: [],
    requiresConnection: false, timeoutMs: 30000,
  }

  // 优先级：actions/registry customRenderer > ToolRegistry > GenericTool
  const custom = getRenderers(part.tool)
  const Renderer = ToolRegistry.get(part.tool) ?? GenericTool

  if (custom?.leftCard) {
    const Custom = custom.leftCard
    return (
      <ToolErrorBoundary fallback={<GenericTool part={part} descriptor={descriptor} />}>
        <Custom part={part as any} descriptor={descriptor} />
      </ToolErrorBoundary>
    )
  }

  return (
    <ToolErrorBoundary fallback={<GenericTool part={part} descriptor={descriptor} />}>
      <Renderer part={part} descriptor={descriptor} />
    </ToolErrorBoundary>
  )
}
```

- [ ] **Step 2：commit**

```bash
git add client/src/features/chat/components/turn/tool-part.tsx
git commit -m "feat(turn): ToolPart dispatcher (custom > registry > generic)"
```

---

### Task 3.7：ContextToolGroup

**Files:**
- Create: `client/src/features/chat/components/turn/context-tool-group.tsx`

- [ ] **Step 1：实现**

```tsx
// context-tool-group.tsx
import { useState } from 'react'
import type { ToolPart } from '@/services/channel/types'
import { ToolPart as ToolPartRenderer } from './tool-part'
import { AnimatedCount } from '../effects/animated-count'
import { TextShimmer } from '../effects/text-shimmer'
import type { MessageInfo } from '@/services/channel/types'

export function ContextToolGroup(props: { parts: ToolPart[]; infos: Map<string, MessageInfo>; busy: boolean }) {
  const [open, setOpen] = useState(false)
  const count = props.parts.length

  return (
    <div className="my-2 rounded border">
      <button onClick={() => setOpen(!open)} className="flex w-full items-center justify-between px-3 py-2 text-left text-sm">
        <span>
          <TextShimmer text={props.busy ? '收集上下文中…' : '已收集上下文'} active={props.busy} />
          <span className="ml-2 text-xs text-muted-foreground">· <AnimatedCount value={count} /> 项</span>
        </span>
        <span className={open ? 'rotate-180 transition-transform' : 'transition-transform'}>▼</span>
      </button>
      {open && (
        <div className="border-t">
          {props.parts.map((p) => (
            <ToolPartRenderer key={p.id} part={p} info={props.infos.get(p.messageID)!} />
          ))}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 2：commit**

```bash
git add client/src/features/chat/components/turn/context-tool-group.tsx
git commit -m "feat(turn): ContextToolGroup collapsed context collector"
```

---

### Task 3.8：UserBubble（含 pending / failed）

**Files:**
- Create: `client/src/features/chat/components/turn/user-bubble.tsx`

- [ ] **Step 1：实现**

```tsx
// user-bubble.tsx
import { useState } from 'react'
import type { MessageInfo, Part, TextPart } from '@/services/channel/types'
import { useChannel } from '@/services/channel/use-channel'
import { cn } from '@/lib/utils'

function HighlightedText(props: { text: string }) {
  // Phase 1 占位：只透传文本（技术债 T-1）
  return <>{props.text}</>
}

export function UserBubble(props: { info: MessageInfo; parts: Part[] }) {
  const { info, parts } = props
  const text = (parts.find((p) => p.type === 'text') as TextPart | undefined)?.text ?? ''
  const [copied, setCopied] = useState(false)
  const channel = useChannel()

  const pending = !!info.__pending
  const failed = !!info.__failed
  const retrying = !!info.__retrying

  const handleCopy = async () => {
    await navigator.clipboard?.writeText?.(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const handleRetry = async () => {
    if (!retrying && channel.retryPendingUser) {
      await channel.retryPendingUser(info.id, [{ type: 'text', text }])
    }
  }

  const handleRemove = () => {
    channel.removePendingUser?.(info.id)
  }

  return (
    <div className={cn('flex flex-col items-end gap-1 my-2')}>
      <div className={cn(
        'max-w-[85%] rounded-lg px-3 py-2 text-sm',
        'bg-primary text-primary-foreground',
        pending && !failed && 'opacity-85',
        failed && 'border-2 border-red-500',
      )}>
        <HighlightedText text={text} />
        {retrying && <span className="ml-2 inline-block animate-spin">⟳</span>}
      </div>
      {failed && (
        <div className="flex gap-2 text-xs">
          <span className="text-red-500">⚠ 发送失败：{info.__failReason}</span>
          <button onClick={handleRetry} className="text-primary hover:underline">重试</button>
          <button onClick={handleRemove} className="text-muted-foreground hover:underline">删除</button>
        </div>
      )}
      {!pending && !failed && (
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <button onClick={handleCopy} className="hover:text-foreground">{copied ? '✓' : '复制'}</button>
          {info.time.created && <span>· {new Date(info.time.created).toLocaleTimeString()}</span>}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 2：commit**

```bash
git add client/src/features/chat/components/turn/user-bubble.tsx
git commit -m "feat(turn): UserBubble with pending / failed / retry"
```

---

### Task 3.9：AssistantStream

**Files:**
- Create: `client/src/features/chat/components/turn/assistant-stream.tsx`

- [ ] **Step 1：实现**

```tsx
// assistant-stream.tsx
import { useMemo } from 'react'
import type { MessageInfo, Part, ToolPart, TextPart as TextPartType } from '@/services/channel/types'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useActionRegistryStore } from '@/stores/action-registry-store'
import { PartDispatcher } from './part-dispatcher'
import { ContextToolGroup } from './context-tool-group'
import { groupParts, type PartGroup } from '../helpers/group-parts'

export function AssistantStream(props: {
  sessionId: string
  messages: MessageInfo[]
  working: boolean
  showCopyPartID: string | null
  turnDurationMs?: number
}) {
  const partsMap = useChatPartsStore((s) => s.partsBySession.get(props.sessionId))
  const infoMap = useChatPartsStore((s) => s.infoBySession.get(props.sessionId))
  const descriptors = useActionRegistryStore((s) => s.descriptors)

  const flat = useMemo(() => {
    if (!partsMap || !infoMap) return []
    const arr: Array<{ part: Part; info: MessageInfo }> = []
    for (const m of props.messages) {
      const parts = partsMap.get(m.id) ?? []
      for (const p of parts) {
        if (p.type === 'reasoning' && !(p as any).text?.trim()) continue
        if (p.type === 'text' && !(p as TextPartType).text?.trim()) continue
        if (p.type === 'tool') {
          const s = (p as ToolPart).state?.status
          if ((p as ToolPart).tool === 'todowrite') continue
          if ((p as ToolPart).tool === 'question' && (s === 'pending' || s === 'running')) continue
        }
        arr.push({ part: p, info: m })
      }
    }
    return arr
  }, [partsMap, infoMap, props.messages])

  const groups: PartGroup[] = useMemo(
    () => groupParts(flat.map((x) => x.part), (p) => {
      if (p.type !== 'tool') return false
      const desc = descriptors[(p as ToolPart).tool]
      return desc?.category === 'metadata'
    }),
    [flat, descriptors],
  )

  const lastKey = groups.at(-1) && (groups[groups.length - 1].type === 'context-group'
    ? (groups[groups.length - 1] as any).key
    : 'part:' + ((groups[groups.length - 1] as any).ref?.id))

  return (
    <div className="flex flex-col gap-1">
      {groups.map((g) => {
        if (g.type === 'context-group') {
          const busy = props.working && lastKey === g.key
          return (
            <ContextToolGroup
              key={g.key}
              parts={g.refs as ToolPart[]}
              infos={infoMap!}
              busy={busy}
            />
          )
        }
        const info = flat.find((x) => x.part.id === g.ref.id)?.info
        if (!info) return null
        const showCopy = props.showCopyPartID === g.ref.id
        return <PartDispatcher key={g.ref.id} part={g.ref} info={info} showCopy={showCopy} turnDurationMs={props.turnDurationMs} />
      })}
    </div>
  )
}
```

- [ ] **Step 2：commit**

```bash
git add client/src/features/chat/components/turn/assistant-stream.tsx
git commit -m "feat(turn): AssistantStream with groupParts + ContextToolGroup"
```

---

### Task 3.10：ErrorCard + unwrap

**Files:**
- Create: `client/src/features/chat/components/turn/error-card.tsx`

- [ ] **Step 1：实现**

```tsx
// error-card.tsx
function record(v: unknown): v is Record<string, unknown> {
  return !!v && typeof v === 'object' && !Array.isArray(v)
}

function parse(v: string): unknown {
  try { return JSON.parse(v) } catch { return undefined }
}

export function unwrap(message: string): string {
  const text = message.replace(/^Error:\s*/, '').trim()
  const read = (v: string) => {
    const first = parse(v)
    if (typeof first !== 'string') return first
    return parse(first.trim())
  }
  let json = read(text)
  if (json === undefined) {
    const start = text.indexOf('{'); const end = text.lastIndexOf('}')
    if (start !== -1 && end > start) json = read(text.slice(start, end + 1))
  }
  if (!record(json)) return message
  const err = record(json.error) ? json.error : undefined
  if (err) {
    const type = typeof err.type === 'string' ? err.type : undefined
    const msg = typeof err.message === 'string' ? err.message : undefined
    if (type && msg) return `${type}: ${msg}`
    if (msg) return msg
    if (type) return type
  }
  if (typeof json.message === 'string') return json.message
  if (typeof json.error === 'string') return json.error
  return message
}

export function ErrorCard(props: { message: string }) {
  return (
    <div className="my-2 rounded border border-red-500/50 bg-red-50 dark:bg-red-950/20 p-3 text-sm text-red-700 dark:text-red-300">
      {unwrap(props.message)}
    </div>
  )
}
```

- [ ] **Step 2：commit**

```bash
git add client/src/features/chat/components/turn/error-card.tsx
git commit -m "feat(turn): ErrorCard with unwrap JSON"
```

---

### Task 3.11：SessionTurn 组合

**Files:**
- Create: `client/src/features/chat/components/turn/session-turn.tsx`

- [ ] **Step 1：实现**

```tsx
// session-turn.tsx
import { useMemo } from 'react'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import type { MessageInfo, Part } from '@/services/channel/types'
import { UserBubble } from './user-bubble'
import { AssistantStream } from './assistant-stream'
import { ErrorCard } from './error-card'
import { TextShimmer } from '../effects/text-shimmer'
import { TextReveal } from '../effects/text-reveal'
import { extractHeading } from '../helpers/reasoning-heading'

export function SessionTurn(props: {
  sessionId: string
  userMessageId?: string
  assistantMessageIds: string[]
  userInfo?: MessageInfo
  isLastTurn: boolean
}) {
  const partsMap = useChatPartsStore((s) => s.partsBySession.get(props.sessionId))
  const infoMap = useChatPartsStore((s) => s.infoBySession.get(props.sessionId))

  const assistantMessages = useMemo(
    () => props.assistantMessageIds.map((id) => infoMap?.get(id)).filter((x): x is MessageInfo => !!x),
    [props.assistantMessageIds, infoMap],
  )

  const userParts: Part[] = useMemo(() => {
    if (!props.userMessageId || !partsMap) return []
    return partsMap.get(props.userMessageId) ?? []
  }, [props.userMessageId, partsMap])

  const working = props.isLastTurn && assistantMessages.some((m) => typeof m.time.completed !== 'number')
  const interrupted = assistantMessages.some((m) => m.error?.name === 'MessageAbortedError')
  const err = assistantMessages.find((m) => m.error && m.error.name !== 'MessageAbortedError')?.error

  const anyVisiblePart = useMemo(
    () => assistantMessages.some((m) => (partsMap?.get(m.id) ?? []).some((p) => {
      if (p.type === 'text') return !!(p as any).text?.trim()
      if (p.type === 'reasoning') return !!(p as any).text?.trim()
      return p.type === 'tool'
    })),
    [assistantMessages, partsMap],
  )

  const reasoningHeading = useMemo(() => {
    for (const m of assistantMessages) {
      const parts = partsMap?.get(m.id) ?? []
      for (const p of parts) {
        if (p.type === 'reasoning') {
          const h = extractHeading((p as any).text ?? '')
          if (h) return h
        }
      }
    }
    return undefined
  }, [assistantMessages, partsMap])

  const lastTextPartId = useMemo(() => {
    for (let i = assistantMessages.length - 1; i >= 0; i--) {
      const m = assistantMessages[i]
      const parts = partsMap?.get(m.id) ?? []
      for (let j = parts.length - 1; j >= 0; j--) {
        const p = parts[j]
        if (p.type === 'text' && (p as any).text?.trim()) return p.id
      }
    }
    return null
  }, [assistantMessages, partsMap])

  const showThinking = working && !err && !anyVisiblePart

  const turnDurationMs = useMemo(() => {
    const start = props.userInfo?.time.created
    if (typeof start !== 'number') return undefined
    let end: number | undefined
    for (const m of assistantMessages) {
      const c = m.time.completed
      if (typeof c === 'number') end = end === undefined ? c : Math.max(end, c)
    }
    return end !== undefined && end >= start ? end - start : undefined
  }, [props.userInfo, assistantMessages])

  return (
    <div data-component="session-turn" className="py-2">
      {props.userInfo && <UserBubble info={props.userInfo} parts={userParts} />}
      <AssistantStream
        sessionId={props.sessionId}
        messages={assistantMessages}
        working={working}
        showCopyPartID={working ? null : lastTextPartId}
        turnDurationMs={turnDurationMs}
      />
      {interrupted && (
        <div className="my-2 text-center text-xs text-muted-foreground">— 已中断 —</div>
      )}
      {showThinking && (
        <div className="my-1 text-sm text-muted-foreground">
          <TextShimmer text="思考中…" active />
          {reasoningHeading && <div className="mt-1 text-xs"><TextReveal text={reasoningHeading} /></div>}
        </div>
      )}
      {err?.data?.message && <ErrorCard message={err.data.message} />}
    </div>
  )
}
```

- [ ] **Step 2：commit**

```bash
git add client/src/features/chat/components/turn/session-turn.tsx
git commit -m "feat(turn): SessionTurn composition with thinking / interrupted / error"
```

---

### Task 3.12：TurnList 顶层 + 替换 MessageStream

**Files:**
- Create: `client/src/features/chat/components/turn/turn-list.tsx`
- Modify: `client/src/features/session/split-view.tsx`

- [ ] **Step 1：TurnList**

```tsx
// turn-list.tsx
import { SessionTurn } from './session-turn'
import { useSessionTurns } from '../helpers/use-session-turns'

export function TurnList(props: { sessionId: string | null }) {
  const turns = useSessionTurns(props.sessionId)
  if (!props.sessionId || turns.length === 0) return null
  return (
    <div className="flex flex-col gap-2">
      {turns.map((t, i) => (
        <SessionTurn
          key={t.userMessageId ?? `orphan:${i}`}
          sessionId={props.sessionId!}
          userMessageId={t.userMessageId}
          userInfo={t.userInfo}
          assistantMessageIds={t.assistantMessageIds}
          isLastTurn={i === turns.length - 1}
        />
      ))}
    </div>
  )
}
```

- [ ] **Step 2：替换 split-view 中的 `<MessageStream />`**

```tsx
// split-view.tsx
// 顶部：
import { TurnList } from '@/features/chat/components/turn/turn-list'
// 将 <MessageStream /> 替换为 <TurnList sessionId={sid} />

// 注意：原 hasMessages 检测要改成检测 infoBySession
const hasMessages = useChatPartsStore((s) => {
  const info = sid ? s.infoBySession.get(sid) : undefined
  return info ? info.size > 0 : false
})
```

- [ ] **Step 3：typecheck + commit**

```bash
cd client && npx tsc --noEmit
git add client/src/features/chat/components/turn/turn-list.tsx client/src/features/session/split-view.tsx
git commit -m "feat(turn): TurnList + wire into split-view"
```

---

## Phase 4：错误处理 & 边界

### Task 4.1：顶层 ErrorBoundary 包 TurnList

**Files:**
- Create: `client/src/features/chat/components/turn/turn-list-error-boundary.tsx`
- Modify: `client/src/features/session/split-view.tsx`

- [ ] **Step 1：实现**

```tsx
// turn-list-error-boundary.tsx
import { Component, type ReactNode } from 'react'

export class TurnListErrorBoundary extends Component<
  { children: ReactNode },
  { hasError: boolean; message?: string }
> {
  state = { hasError: false, message: undefined as string | undefined }
  static getDerivedStateFromError(err: Error) { return { hasError: true, message: err.message } }
  componentDidCatch(err: Error) { console.error('[TurnListErrorBoundary]', err) }

  render() {
    if (this.state.hasError) {
      return (
        <div className="rounded border border-red-500/40 bg-red-50 dark:bg-red-950/20 p-3 text-xs text-red-700 dark:text-red-300">
          本段渲染出错：<span className="font-mono">{this.state.message}</span>
          <button onClick={() => this.setState({ hasError: false })} className="ml-2 underline">重试</button>
        </div>
      )
    }
    return this.props.children
  }
}
```

- [ ] **Step 2：split-view 包 TurnList**

```tsx
<TurnListErrorBoundary>
  <TurnList sessionId={sid} />
</TurnListErrorBoundary>
```

- [ ] **Step 3：commit**

```bash
git add client/src/features/chat/components/turn/turn-list-error-boundary.tsx client/src/features/session/split-view.tsx
git commit -m "feat(turn): TurnListErrorBoundary for morphdom failure"
```

---

### Task 4.2：OpenCode 离线 UI

**Files:**
- Modify: `client/src/features/chat/components/turn/turn-list.tsx`
- Modify: `client/src/features/session/session-canvas.tsx`（暴露 error）

- [ ] **Step 1：turn-list 接收 error prop**

```tsx
// turn-list.tsx
export function TurnList(props: { sessionId: string | null; error?: Error | null }) {
  const turns = useSessionTurns(props.sessionId)

  if (props.error) {
    return (
      <div className="rounded border border-amber-500/40 bg-amber-50 dark:bg-amber-950/20 p-3 text-sm text-amber-800 dark:text-amber-300">
        AI 服务不可用：{props.error.message}
      </div>
    )
  }

  if (!props.sessionId || turns.length === 0) return null
  // ...剩余不变
}
```

- [ ] **Step 2：session-canvas 透传 error**

```tsx
// session-canvas.tsx
const { error: historyError } = useSessionHistory(sessionId)

// 将 historyError 通过 Context 或 prop 传到 SplitView → TurnList
// 简化实现：直接改 SessionCanvas 的导出链路
```

如果改动较大，优先级可以放在联调前，先留 TODO 注释。

- [ ] **Step 3：commit**

```bash
git add client/src/features/chat/components/turn/turn-list.tsx client/src/features/session/session-canvas.tsx
git commit -m "feat(turn): TurnList shows OpenCode offline message"
```

---

## Phase 5：SQL 代码块增强 + Artifact Stage 跳转

### Task 5.1：SQL code block decorator

**Files:**
- Create: `client/src/features/chat/components/markdown/sql-code-block.ts`
- Modify: `client/src/features/chat/components/markdown/markdown.tsx`

- [ ] **Step 1：实现 decorator**

```typescript
// sql-code-block.ts
import { classifySqlRisk } from '../helpers/risk'

function stripComments(s: string): string {
  return s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/--.*$/gm, '').trim()
}

function statementType(sql: string): string | null {
  const clean = stripComments(sql).replace(/^\s+/, '')
  const m = clean.match(/^([A-Z]+)/i)
  return m?.[1]?.toUpperCase() ?? null
}

export function decorateSqlBlocks(root: HTMLElement, opts: { onExecute: (sql: string) => void; onExplain: (sql: string) => void }) {
  const pres = Array.from(root.querySelectorAll('pre > code.language-sql')) as HTMLElement[]
  for (const code of pres) {
    const pre = code.parentElement!
    const wrapper = pre.parentElement
    if (!wrapper || wrapper.getAttribute('data-component') !== 'markdown-code') continue
    if (wrapper.querySelector('[data-slot="sql-header"]')) continue

    const sql = code.textContent ?? ''
    const kind = statementType(sql)
    const risk = classifySqlRisk(sql)

    const header = document.createElement('div')
    header.setAttribute('data-slot', 'sql-header')
    header.className = 'flex items-center gap-2 border-b bg-muted/40 px-2 py-1 text-xs'
    header.innerHTML = `
      <span class="rounded border px-1.5 font-mono text-[10px]">SQL${kind ? ' · ' + kind : ''}</span>
      ${risk === 'L1' ? '<button data-slot="sql-execute" class="rounded border px-2 hover:bg-background">执行</button>' : ''}
      <button data-slot="sql-explain" class="rounded border px-2 hover:bg-background">解释</button>
    `
    wrapper.insertBefore(header, pre)

    const execBtn = wrapper.querySelector('[data-slot="sql-execute"]') as HTMLButtonElement | null
    execBtn?.addEventListener('click', (e) => { e.stopPropagation(); opts.onExecute(sql) })
    const explainBtn = wrapper.querySelector('[data-slot="sql-explain"]') as HTMLButtonElement | null
    explainBtn?.addEventListener('click', (e) => { e.stopPropagation(); opts.onExplain(sql) })
  }
}
```

- [ ] **Step 2：在 markdown.tsx 里调用 decorateSqlBlocks**

在 `decorateCodeBlocks(temp)` 后添加：
```typescript
decorateSqlBlocks(temp, {
  onExecute: (sql) => window.dispatchEvent(new CustomEvent('datatalk.sql.execute', { detail: { sql } })),
  onExplain: (sql) => window.dispatchEvent(new CustomEvent('datatalk.sql.explain', { detail: { sql } })),
})
```

import：`import { decorateSqlBlocks } from './sql-code-block'`

（用全局 CustomEvent 解耦，上层 composer 监听）

- [ ] **Step 3：commit**

```bash
git add client/src/features/chat/components/markdown/sql-code-block.ts client/src/features/chat/components/markdown/markdown.tsx
git commit -m "feat(markdown): SQL code block header with execute/explain (event-based)"
```

---

### Task 5.2：Composer 监听 SQL 事件实现 1a 流程

**Files:**
- Modify: `client/src/features/session/prompt-composer.tsx`

- [ ] **Step 1：检查 composer 当前实现**

Run: `grep -n 'useState\|dispatchEvent\|value=' client/src/features/session/prompt-composer.tsx | head -20`

找出 composer text state 的 setter。

- [ ] **Step 2：添加事件监听**

在 composer 组件 useEffect 里：

```tsx
useEffect(() => {
  const onExecute = (e: Event) => {
    const detail = (e as CustomEvent).detail as { sql: string }
    const sql = detail.sql
    setText((prev) => {
      if (!prev.trim()) {
        // composer 空：填入并自动 submit
        setTimeout(() => { void submit() }, 0)   // submit 内部读 text state
        return sql
      }
      // 非空：追加不 submit
      showToast({ description: '已追加 SQL，请确认后发送' })
      return prev + '\n' + sql
    })
  }
  const onExplain = (e: Event) => {
    const detail = (e as CustomEvent).detail as { sql: string }
    setText((prev) => prev ? prev + '\n解释这条 SQL：\n' + detail.sql : '解释这条 SQL：\n' + detail.sql)
  }
  window.addEventListener('datatalk.sql.execute', onExecute)
  window.addEventListener('datatalk.sql.explain', onExplain)
  return () => {
    window.removeEventListener('datatalk.sql.execute', onExecute)
    window.removeEventListener('datatalk.sql.explain', onExplain)
  }
}, [])
```

注意：`submit` / `setText` / `showToast` 要用 composer 里实际存在的 API。若 submit 依赖 text 的最新值，用 ref 保持引用或用 flushSync 后再调。

- [ ] **Step 3：commit**

```bash
git add client/src/features/session/prompt-composer.tsx
git commit -m "feat(composer): listen sql execute/explain events (1a flow)"
```

---

## Phase 6：联调（手动）

### Task 6.1：六个验收场景手动走查

- [ ] 场景 1：切走再切回不丢 AI 消息
- [ ] 场景 2：刷新页面不丢
- [ ] 场景 3：500 字回复流式实时感（光标/滚动不跳）
- [ ] 场景 4：乐观 UI 发送 → ID 无缝切换（无闪烁无重复）
- [ ] 场景 5：空会话 `[]` 不报错
- [ ] 场景 6：OpenCode 离线 → 显示"AI 服务不可用"
- [ ] 场景 7：Markdown 代码块复制按钮悬浮显现，点击变 ✓
- [ ] 场景 8：SQL L1 代码块显示执行按钮，点击后 composer 出现相同 SQL 并自动发送
- [ ] 场景 9：SQL L3（如 DELETE）在 preview 渲染器显示影响行数 + 确认/取消
- [ ] 场景 10：工具 pending/running 标题 shimmer；completed 后停止
- [ ] 场景 11：AI 思考阶段（尚无 part）显示"思考中…" + reasoning heading TextReveal
- [ ] 场景 12：连续 describe_table/list_tables 被合并为 ContextToolGroup
- [ ] 场景 13：风险等级边框色：execute_sql 绿、preview_sql UPDATE 黄、preview_sql DELETE 红
- [ ] 场景 14：点击 artifact_created 卡片触发 Stage 打开
- [ ] 场景 15：pending user 发送失败 → 变红 → 点重试再发
- [ ] 场景 16：未知 part type → 显示 UnknownPart 占位不白屏

---

## 文档收尾

### Task 7.1：将本 plan 从 Active 迁至 Completed

**Files:**
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/product-specs/index.md`
- Modify: `docs/product-specs/2026-04-19-ai-message-rendering-migration-design.md`

- [ ] **Step 1：登记 plan 到 index.md Active**（在开始实施前就要做这步）

在 `docs/exec-plans/index.md` 的 Active 表格里加：
```
| [AI Message Rendering Migration](./2026-04-19-ai-message-rendering-migration-plan.md) | 2026-04-19 | 迁移 OpenCode 消息渲染 + DataTalk 特化 |
```

- [ ] **Step 2：完工时迁移至 Completed，并在 spec 里标记所有任务完成**

- [ ] **Step 3：commit 文档更新**

```bash
git add docs/exec-plans/index.md docs/product-specs/index.md docs/product-specs/2026-04-19-ai-message-rendering-migration-design.md
git commit -m "docs: move AI message rendering migration plan to Completed"
```

---

## Self-Review Checklist（实施前通读）

- [ ] 所有任务路径都是绝对路径格式（`client/src/...`）
- [ ] 每个新增组件都有对应的 import 点（`registerBuiltInRenderers` 在 main.tsx 调用）
- [ ] OpenCode 原生字段名一致（`sessionID`/`messageID`，不是 `sessionId`/`messageId`）
- [ ] `info.__pending` / `__failed` / `__retrying` / `__failReason` 四个前端字段统一
- [ ] `replaceSession` / `upsertPendingUser` / `promotePendingUser` / `markPendingUserFailed` / `removePendingUser` 五个新 store 方法
- [ ] ToolRegistry 7 个内建 renderer：execute_sql / preview_sql / describe_table / list_tables / show_schema / artifact_created / question
- [ ] 风险优先级链 `resolveRisk` 实现且被 BasicTool / renderer 使用
- [ ] 未知 part type 走 UnknownPart（不静默）
- [ ] morphdom 失败 raise 到 TurnListErrorBoundary
- [ ] SQL "执行"按钮 1a 流程：composer 空自动 submit / 非空追加 toast
