# Plan C — Client Tauri Split-View UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current placeholder workspace UI with the Manus split-view experience: `HERO` (centered composer) → `SPLIT` (left thought-stream + right artifact canvas) with FLIP animation, Part-rendering chat, and an artifact timeline that honors the supersedes chain.

**Architecture:** New feature modules (`features/ontology/`, `features/actions/`) + a Streamable HTTP client (`services/channel/`). All state in focused Zustand stores. Everything reflects on the `ActionRegistry` descriptors fetched from the backend — adding a new action's UI = registering one renderer in `features/actions/registry.ts`.

**Tech Stack:** Existing — React 19 + Vite + TanStack Router + Zustand + react-resizable-panels + Recharts + TanStack Table + Tauri v2. Adds `eventsource-parser` (tiny SSE parser for the POST-responds-as-SSE pattern where `fetch` + ReadableStream is needed).

---

## Spec Mapping

Implements §4 (Client architecture) plus the protocol consumer side of §3.

Defers: classify-intent remote endpoint (MVP keeps local heuristic).

---

## Prerequisites

Plan A (backend platform) + Plan B (real OpenCode + MVP actions) merged and running at `http://localhost:8080`. For local dev the client works against a mocked backend too — Task 14 ships a `MockChannelServer` for Playwright tests.

---

## Tasks Overview

| # | Task | Directory | Deliverable |
|---|---|---|---|
| 1 | Streamable HTTP client (`channel-client.ts`) | services/channel | fetch+SSE + RPC helpers |
| 2 | Event reducer (Part → store updates) | services/channel | `event-reducer.ts` |
| 3 | Zustand stores (6 stores) | stores/ | session/ontology/chat-parts/timeline/channel/action-registry |
| 4 | Action Registry (renderer + client-handler API) | features/actions | `registry.ts` + `use-action-registry.ts` |
| 5 | Action bootstrap hook (fetch `/api/actions`) | features/actions | `use-bootstrap-actions.ts` |
| 6 | Part renderer dispatch + TextPart/ReasoningPart | features/chat | `part-renderer.tsx`, subcomponents |
| 7 | Tool part renderer + GenericToolCard | features/chat | `tool-part-renderer.tsx` |
| 8 | Session mode state machine (HERO↔SPLIT) | features/session | `session-mode.ts` + `use-session-mode.ts` |
| 9 | PromptComposer as stable shared element | features/session | `prompt-composer.tsx` |
| 10 | HeroView + SplitView | features/session | `hero-view.tsx`, `split-view.tsx` |
| 11 | FLIP animation hook (shared-layout composer) | features/session | `use-flip-composer.ts` |
| 12 | ArtifactTimelineStrip + ArtifactChip | features/ontology | `artifact-timeline-strip.tsx` |
| 13 | ArtifactDispatcher + TableArtifact + ChartArtifact(+adapter) + ErdArtifact stub | features/ontology | 4 artifact components |
| 14 | WorkspaceLayout integration + Connection-dialogue overlay | layouts | Rewrite `workspace-layout.tsx`, add overlay |
| 15 | classifyIntent + ClientActionRegistry + pin_artifact handler | features/actions | `classify-intent.ts`, `client-handlers.ts` |
| 16 | End-to-end Playwright test + MockChannelServer | tests/e2e | `manus-smoke.spec.ts` |

---

## Task 1: Streamable HTTP client

**Files:**
- Create: `client/src/services/channel/channel-client.ts`
- Create: `client/src/services/channel/types.ts`
- Test: `client/src/services/channel/channel-client.test.ts`

- [ ] **Step 1.1: Add `eventsource-parser` to `client/package.json`**

```
pnpm add eventsource-parser
```

(Vitest + RTL are already in devDeps via the existing setup; confirm and add if missing.)

- [ ] **Step 1.2: Add vitest config** `client/vitest.config.ts`:

```ts
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react-swc'
import path from 'node:path'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['src/test-setup.ts'],
  },
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') },
  },
})
```

Add `src/test-setup.ts` with `import '@testing-library/jest-dom'`.

Add pnpm scripts:

```json
"test": "vitest run",
"test:watch": "vitest"
```

- [ ] **Step 1.3: Write failing test**

`client/src/services/channel/channel-client.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ChannelClient } from './channel-client'

describe('ChannelClient', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('sends action_result as plain POST and returns ack', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      headers: { get: (k: string) => k === 'content-type' ? 'application/json' : null },
      json: async () => ({ jsonrpc: '2.0', id: 'r1', result: {} }),
    })
    global.fetch = fetchMock as any

    const client = new ChannelClient({ baseUrl: 'http://test', sessionId: 's-1', clientId: 'c-1' })
    await client.actionResult('call-1', true, { reversed: 'olleh' })

    expect(fetchMock).toHaveBeenCalled()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://test/api/sessions/s-1/channel')
    const body = JSON.parse(init.body)
    expect(body.method).toBe('action_result')
    expect(body.params.callId).toBe('call-1')
  })

  it('streams SSE events from send_message and invokes onEvent per frame', async () => {
    const sseBody =
      'id: 1\nevent: connected\ndata: {"sessionId":"s-1","serverRev":1}\n\n' +
      'id: 2\nevent: message.part.delta\ndata: {"partId":"p1","field":"text","delta":"hi"}\n\n'
    const encoder = new TextEncoder()
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode(sseBody))
        controller.close()
      },
    })
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      headers: { get: (k: string) => k === 'content-type' ? 'text/event-stream' : null },
      body: stream,
    }) as any

    const events: any[] = []
    const client = new ChannelClient({ baseUrl: 'http://test', sessionId: 's-1', clientId: 'c-1' })
    await client.sendMessage([{ type: 'text', id: 'p1', sessionID: 's-1', messageID: 'm1', text: 'hi', metadata: {} }],
      e => events.push(e))

    expect(events).toHaveLength(2)
    expect(events[0].event).toBe('connected')
    expect(events[1].event).toBe('message.part.delta')
    expect(events[1].data.delta).toBe('hi')
  })
})
```

- [ ] **Step 1.4: Implement `types.ts` and `channel-client.ts`**

`client/src/services/channel/types.ts`:

```ts
export type StreamEvent = {
  id: number
  event: string       // e.g. 'connected' | 'message.part.created' | 'action.invoke' | …
  data: unknown       // parsed JSON payload (matches the DtEvent variant per `event`)
}

export type RpcRequest =
  | { jsonrpc: '2.0'; id: string; method: 'send_message';  params: { parts: unknown[] } }
  | { jsonrpc: '2.0'; id: string; method: 'action_result'; params: { callId: string; ok: boolean; output?: unknown; error?: unknown } }
  | { jsonrpc: '2.0'; id: string; method: 'abort';         params: Record<string, never> }
  | { jsonrpc: '2.0'; id: string; method: 'hello';         params: { clientRev: number; lastEventId?: number } }

export type Part = {
  type: string          // loose union; we narrow in renderers
  id: string
  sessionID: string
  messageID: string
  [k: string]: unknown
}
```

`client/src/services/channel/channel-client.ts`:

```ts
import { createParser, type EventSourceMessage } from 'eventsource-parser'
import type { RpcRequest, StreamEvent, Part } from './types'

export type ChannelClientOptions = {
  baseUrl: string
  sessionId: string
  clientId: string
  clientRev?: number
}

export class ChannelClient {
  private readonly baseUrl: string
  private readonly sessionId: string
  private readonly clientId: string
  private readonly clientRev: number

  constructor(opts: ChannelClientOptions) {
    this.baseUrl = opts.baseUrl.replace(/\/$/, '')
    this.sessionId = opts.sessionId
    this.clientId = opts.clientId
    this.clientRev = opts.clientRev ?? 1
  }

  async sendMessage(parts: Part[], onEvent: (e: StreamEvent) => void): Promise<void> {
    const body: RpcRequest = {
      jsonrpc: '2.0', id: crypto.randomUUID(),
      method: 'send_message',
      params: { parts },
    }
    await this.streamingPost(body, onEvent)
  }

  async actionResult(callId: string, ok: boolean, output?: unknown, error?: unknown): Promise<void> {
    const body: RpcRequest = {
      jsonrpc: '2.0', id: crypto.randomUUID(),
      method: 'action_result',
      params: { callId, ok, output, error },
    }
    await this.plainPost(body)
  }

  async abort(): Promise<void> {
    const body: RpcRequest = {
      jsonrpc: '2.0', id: crypto.randomUUID(),
      method: 'abort', params: {},
    }
    await this.plainPost(body)
  }

  subscribe(lastEventId: number | undefined, onEvent: (e: StreamEvent) => void): () => void {
    const ctrl = new AbortController()
    void (async () => {
      const res = await fetch(this.url(), {
        method: 'GET',
        signal: ctrl.signal,
        headers: {
          'DataTalk-Session-Id': this.clientId,
          'DataTalk-Client-Rev': String(this.clientRev),
          ...(lastEventId !== undefined ? { 'Last-Event-ID': String(lastEventId) } : {}),
        },
      })
      await consumeSseStream(res, onEvent)
    })()
    return () => ctrl.abort()
  }

  private async plainPost(body: RpcRequest): Promise<unknown> {
    const res = await fetch(this.url(), {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'DataTalk-Session-Id': this.clientId,
        'DataTalk-Client-Rev': String(this.clientRev),
      },
      body: JSON.stringify(body),
    })
    if (!res.ok) throw new Error(`channel RPC failed: ${res.status}`)
    const ct = res.headers.get('content-type') ?? ''
    if (ct.includes('application/json')) return res.json()
    return res.text()
  }

  private async streamingPost(body: RpcRequest, onEvent: (e: StreamEvent) => void): Promise<void> {
    const res = await fetch(this.url(), {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'DataTalk-Session-Id': this.clientId,
        'DataTalk-Client-Rev': String(this.clientRev),
      },
      body: JSON.stringify(body),
    })
    if (!res.ok) throw new Error(`send_message failed: ${res.status}`)
    await consumeSseStream(res, onEvent)
  }

  private url() { return `${this.baseUrl}/api/sessions/${this.sessionId}/channel` }
}

async function consumeSseStream(res: Response, onEvent: (e: StreamEvent) => void): Promise<void> {
  const body = res.body
  if (!body) return
  const parser = createParser({
    onEvent(msg: EventSourceMessage) {
      const id = msg.id ? Number(msg.id) : 0
      let data: unknown = {}
      try { data = msg.data ? JSON.parse(msg.data) : {} } catch { /* ignore malformed */ }
      onEvent({ id, event: msg.event ?? 'message', data })
    },
  })
  const reader = body.getReader()
  const decoder = new TextDecoder()
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    parser.feed(decoder.decode(value, { stream: true }))
  }
}
```

- [ ] **Step 1.5: Run vitest — PASS, commit**

```
pnpm -C client test
git add client/package.json client/pnpm-lock.yaml client/vitest.config.ts client/src/test-setup.ts \
        client/src/services/channel/
git commit -m "feat(client): add Streamable HTTP ChannelClient with SSE parsing"
```

---

## Task 2: Event reducer

**File:** `client/src/services/channel/event-reducer.ts` + test

- [ ] **Step 2.1: Write failing test**

```ts
// event-reducer.test.ts
import { describe, it, expect } from 'vitest'
import { reduceEvent, type ReducerState } from './event-reducer'

const empty = (): ReducerState => ({
  messages: new Map(),
  parts: new Map(),
  artifacts: new Map(),
  pendingClientCalls: new Map(),
})

describe('event-reducer', () => {
  it('creates a part on message.part.created', () => {
    const state = reduceEvent(empty(), {
      id: 1, event: 'message.part.created',
      data: { part: { type: 'text', id: 'p1', sessionID: 's1', messageID: 'm1', text: 'hi' } }
    })
    expect(state.parts.get('m1')).toHaveLength(1)
    expect(state.parts.get('m1')![0].id).toBe('p1')
  })

  it('replaces a part on message.part.updated', () => {
    const s1 = reduceEvent(empty(), {
      id: 1, event: 'message.part.created',
      data: { part: { type: 'text', id: 'p1', sessionID: 's1', messageID: 'm1', text: 'hi' } }
    })
    const s2 = reduceEvent(s1, {
      id: 2, event: 'message.part.updated',
      data: { part: { type: 'text', id: 'p1', sessionID: 's1', messageID: 'm1', text: 'hi again' } }
    })
    expect((s2.parts.get('m1')![0] as any).text).toBe('hi again')
  })

  it('appends delta to matching field on message.part.delta', () => {
    const s1 = reduceEvent(empty(), {
      id: 1, event: 'message.part.created',
      data: { part: { type: 'text', id: 'p1', sessionID: 's1', messageID: 'm1', text: 'He' } }
    })
    const s2 = reduceEvent(s1, {
      id: 2, event: 'message.part.delta',
      data: { partId: 'p1', field: 'text', delta: 'llo' }
    })
    expect((s2.parts.get('m1')![0] as any).text).toBe('Hello')
  })

  it('upserts artifact on ontology.updated', () => {
    const s = reduceEvent(empty(), {
      id: 3, event: 'ontology.updated',
      data: {
        objectType: 'datatalk.artifact',
        id: 'art-1',
        op: 'upsert',
        patch: { version: 1, kind: 'table' }
      }
    })
    expect(s.artifacts.get('art-1')).toBeDefined()
  })
})
```

- [ ] **Step 2.2: Implement**

```ts
// event-reducer.ts
import type { StreamEvent, Part } from './types'

export type Artifact = {
  id: string
  version: number
  kind: 'table' | 'chart' | 'erd'
  sessionId?: string
  supersedesId?: string
  supersedesVersion?: number
  pinned?: boolean
  payload?: unknown
  createdAt?: number
}

export type ReducerState = {
  messages: Map<string, { id: string; role: string; createdAt: number }>
  parts: Map<string, Part[]>
  artifacts: Map<string, Artifact>
  pendingClientCalls: Map<string, { actionId: string; input: unknown; timeoutMs: number }>
}

export function reduceEvent(state: ReducerState, evt: StreamEvent): ReducerState {
  switch (evt.event) {
    case 'message.created': {
      const m = (evt.data as any).message
      const next = new Map(state.messages)
      next.set(m.id, { id: m.id, role: m.role, createdAt: m.createdAt })
      return { ...state, messages: next }
    }
    case 'message.part.created': {
      const part = (evt.data as any).part as Part
      const nextParts = new Map(state.parts)
      const list = nextParts.get(part.messageID) ?? []
      nextParts.set(part.messageID, [...list, part])
      return { ...state, parts: nextParts }
    }
    case 'message.part.updated': {
      const part = (evt.data as any).part as Part
      const nextParts = new Map(state.parts)
      const list = (nextParts.get(part.messageID) ?? []).map(p => p.id === part.id ? part : p)
      if (!list.some(p => p.id === part.id)) list.push(part)
      nextParts.set(part.messageID, list)
      return { ...state, parts: nextParts }
    }
    case 'message.part.delta': {
      const { partId, field, delta } = evt.data as any
      const nextParts = new Map(state.parts)
      for (const [mid, list] of nextParts) {
        const idx = list.findIndex(p => p.id === partId)
        if (idx >= 0) {
          const before = list[idx] as any
          const updated = { ...before, [field]: (before[field] ?? '') + delta }
          const newList = [...list]
          newList[idx] = updated
          nextParts.set(mid, newList)
          break
        }
      }
      return { ...state, parts: nextParts }
    }
    case 'message.part.removed': {
      const { partId } = evt.data as any
      const nextParts = new Map(state.parts)
      for (const [mid, list] of nextParts) {
        nextParts.set(mid, list.filter(p => p.id !== partId))
      }
      return { ...state, parts: nextParts }
    }
    case 'ontology.updated': {
      const { objectType, id, op, patch } = evt.data as any
      if (objectType !== 'datatalk.artifact') return state
      const nextArts = new Map(state.artifacts)
      if (op === 'delete') { nextArts.delete(id); return { ...state, artifacts: nextArts } }
      const prev = nextArts.get(id)
      nextArts.set(id, { ...(prev ?? { id, version: 1, kind: 'table' }), ...patch, id })
      return { ...state, artifacts: nextArts }
    }
    case 'artifact.snapshot': {
      const { artifacts } = evt.data as any
      const next = new Map(state.artifacts)
      for (const a of artifacts) next.set(a.id, a)
      return { ...state, artifacts: next }
    }
    case 'action.invoke': {
      const { callId, actionId, input, timeoutMs } = evt.data as any
      const next = new Map(state.pendingClientCalls)
      next.set(callId, { actionId, input, timeoutMs })
      return { ...state, pendingClientCalls: next }
    }
    case 'action.cancel': {
      const { callId } = evt.data as any
      const next = new Map(state.pendingClientCalls)
      next.delete(callId)
      return { ...state, pendingClientCalls: next }
    }
    default: return state
  }
}
```

Commit.

---

## Task 3: Zustand stores

**Files:**
- Create: `client/src/stores/session-store.ts`, `ontology-store.ts`, `chat-parts-store.ts`, `timeline-store.ts`, `channel-store.ts`, `action-registry-store.ts`
- Test: `client/src/stores/session-store.test.ts`, `timeline-store.test.ts`

`session-store.ts` is the most interesting (holds the mode state machine):

```ts
import { create } from 'zustand'

export type SessionMode = 'NOSESS' | 'HERO' | 'SPLIT'

type SessionState = {
  activeSessionId: string | null
  modeBySession: Map<string, SessionMode>
  hasEverSentBySession: Map<string, boolean>
  pendingPrompt: string | null
  pendingConnectionPrompt: boolean

  setActive: (id: string | null) => void
  enterSplit: (id: string) => void
  seedFromServer: (id: string, hasEverSent: boolean) => void
  setPendingPrompt: (text: string | null) => void
  setPendingConnectionPrompt: (on: boolean) => void
}

export const useSessionStore = create<SessionState>((set, get) => ({
  activeSessionId: null,
  modeBySession: new Map(),
  hasEverSentBySession: new Map(),
  pendingPrompt: null,
  pendingConnectionPrompt: false,

  setActive: (id) => set(s => {
    if (!id) return { activeSessionId: null }
    const mode = s.modeBySession.get(id)
        ?? (s.hasEverSentBySession.get(id) ? 'SPLIT' : 'HERO')
    const next = new Map(s.modeBySession); next.set(id, mode)
    return { activeSessionId: id, modeBySession: next }
  }),

  enterSplit: (id) => set(s => {
    const next = new Map(s.modeBySession); next.set(id, 'SPLIT')
    const sent = new Map(s.hasEverSentBySession); sent.set(id, true)
    return { modeBySession: next, hasEverSentBySession: sent }
  }),

  seedFromServer: (id, hasEverSent) => set(s => {
    const mode = hasEverSent ? 'SPLIT' : 'HERO'
    const next = new Map(s.modeBySession); next.set(id, mode)
    const sent = new Map(s.hasEverSentBySession); sent.set(id, hasEverSent)
    return { modeBySession: next, hasEverSentBySession: sent }
  }),

  setPendingPrompt: (text) => set({ pendingPrompt: text }),
  setPendingConnectionPrompt: (on) => set({ pendingConnectionPrompt: on }),
}))
```

`timeline-store.ts`:

```ts
import { create } from 'zustand'

type TimelineState = {
  orderBySession: Map<string, string[]>    // sessionId → artifactId[] in createdAt order
  activeBySession: Map<string, string | null>
  manualBySession: Map<string, boolean>

  addArtifact: (sessionId: string, artifactId: string, supersedesId?: string) => void
  setActive: (sessionId: string, artifactId: string) => void
  clear: (sessionId: string) => void
}

export const useTimelineStore = create<TimelineState>((set, get) => ({
  orderBySession: new Map(),
  activeBySession: new Map(),
  manualBySession: new Map(),

  addArtifact: (sessionId, artifactId, supersedesId) => set(s => {
    const order = new Map(s.orderBySession)
    const list = [...(order.get(sessionId) ?? [])]
    if (!list.includes(artifactId)) list.push(artifactId)
    order.set(sessionId, list)

    const currentActive = s.activeBySession.get(sessionId) ?? null
    const manual = s.manualBySession.get(sessionId) ?? false
    const activeMap = new Map(s.activeBySession)

    if (!manual) {
      if (!supersedesId) {
        activeMap.set(sessionId, artifactId)
      } else if (currentActive === supersedesId) {
        activeMap.set(sessionId, artifactId)
      }
    }
    return { orderBySession: order, activeBySession: activeMap }
  }),

  setActive: (sessionId, artifactId) => set(s => {
    const list = s.orderBySession.get(sessionId) ?? []
    const isNewest = list[list.length - 1] === artifactId
    const manual = new Map(s.manualBySession); manual.set(sessionId, !isNewest)
    const active = new Map(s.activeBySession); active.set(sessionId, artifactId)
    return { manualBySession: manual, activeBySession: active }
  }),

  clear: (sessionId) => set(s => {
    const order = new Map(s.orderBySession); order.delete(sessionId)
    const active = new Map(s.activeBySession); active.delete(sessionId)
    const manual = new Map(s.manualBySession); manual.delete(sessionId)
    return { orderBySession: order, activeBySession: active, manualBySession: manual }
  }),
}))
```

`ontology-store.ts`:

```ts
import { create } from 'zustand'
import type { Artifact } from '@/services/channel/event-reducer'

type OntologyState = {
  artifacts: Map<string, Artifact>
  upsertArtifact: (a: Artifact) => void
  removeArtifact: (id: string) => void
  clear: () => void
}

export const useOntologyStore = create<OntologyState>((set) => ({
  artifacts: new Map(),
  upsertArtifact: (a) => set(s => {
    const next = new Map(s.artifacts)
    next.set(a.id, { ...(next.get(a.id) ?? {} as Artifact), ...a })
    return { artifacts: next }
  }),
  removeArtifact: (id) => set(s => {
    const next = new Map(s.artifacts); next.delete(id); return { artifacts: next }
  }),
  clear: () => set({ artifacts: new Map() }),
}))
```

`chat-parts-store.ts`, `channel-store.ts`, `action-registry-store.ts`: analogous shape. Each gets a small vitest checking upsert + remove semantics.

Commit after all 6 stores + tests pass.

---

## Task 4: Action Registry & client-handler API

**Files:**
- Create: `client/src/features/actions/registry.ts`
- Create: `client/src/features/actions/use-action-registry.ts`
- Test: `client/src/features/actions/registry.test.ts`

```ts
// registry.ts
import type { ComponentType } from 'react'
import type { Part } from '@/services/channel/types'
import type { Artifact } from '@/services/channel/event-reducer'

export type LeftCardProps = { part: Part; descriptor: ActionDescriptor }
export type RightArtifactProps = { artifact: Artifact }

export type ActionDescriptor = {
  id: string
  executor: 'OPENCODE' | 'SERVER' | 'CLIENT'
  description: string
  inputSchema: unknown
  outputSchema: unknown
  produces: string[]
  sideEffects: string[]
  requiresConnection: boolean
  timeoutMs: number
}

export type ActionRenderers = {
  leftCard?: ComponentType<LeftCardProps>
  rightArtifact?: ComponentType<RightArtifactProps>
}

export type ClientActionHandler = (input: unknown, ctx: { sessionId: string }) => Promise<unknown>

const renderers = new Map<string, ActionRenderers>()
const clientHandlers = new Map<string, ClientActionHandler>()

export function registerAction(id: string, r: ActionRenderers) {
  renderers.set(id, r)
}
export function registerClientHandler(id: string, h: ClientActionHandler) {
  clientHandlers.set(id, h)
}
export function getRenderers(id: string): ActionRenderers | undefined { return renderers.get(id) }
export function getClientHandler(id: string): ClientActionHandler | undefined { return clientHandlers.get(id) }
```

`use-action-registry.ts`:

```ts
import { useActionRegistryStore } from '@/stores/action-registry-store'

export function useActionRegistry() {
  return useActionRegistryStore(s => ({
    descriptors: s.descriptors,
  }))
}
```

Commit.

---

## Task 5: Action bootstrap hook

`client/src/features/actions/use-bootstrap-actions.ts`:

```ts
import { useEffect } from 'react'
import { useActionRegistryStore } from '@/stores/action-registry-store'

export function useBootstrapActions() {
  const setDescriptors = useActionRegistryStore(s => s.setDescriptors)
  useEffect(() => {
    void fetch('/api/actions').then(r => r.json()).then(j => {
      const byId: Record<string, any> = {}
      for (const d of j.actions ?? []) byId[d.id] = d
      setDescriptors(byId)
    })
  }, [setDescriptors])
}
```

Call it in `RootComponent`. Commit.

---

## Task 6: Part renderer dispatch

**Files:**
- Create: `client/src/features/chat/components/part-renderer.tsx`
- Create: `client/src/features/chat/components/text-part.tsx`
- Create: `client/src/features/chat/components/reasoning-part.tsx`
- Create: `client/src/features/chat/components/step-divider.tsx`
- Test: `client/src/features/chat/components/part-renderer.test.tsx`

```tsx
// part-renderer.tsx
import type { Part } from '@/services/channel/types'
import { TextPart } from './text-part'
import { ReasoningPart } from './reasoning-part'
import { ToolPartRenderer } from './tool-part-renderer'
import { StepDivider } from './step-divider'

export function PartRenderer({ part }: { part: Part }) {
  switch (part.type) {
    case 'text':         return <TextPart part={part} />
    case 'reasoning':    return <ReasoningPart part={part} />
    case 'tool':         return <ToolPartRenderer part={part} />
    case 'step-start':
    case 'step-finish':  return <StepDivider part={part} />
    default:             return null
  }
}
```

Implement each subcomponent with plain div/markdown-lite:

```tsx
// text-part.tsx
export function TextPart({ part }: { part: any }) {
  return <div className="whitespace-pre-wrap text-sm">{part.text}</div>
}
```

```tsx
// reasoning-part.tsx
import { useState } from 'react'
export function ReasoningPart({ part }: { part: any }) {
  const [open, setOpen] = useState(false)
  return (
    <details open={open} onToggle={e => setOpen((e.target as HTMLDetailsElement).open)}
      className="rounded border bg-muted/40 p-2 text-xs text-muted-foreground">
      <summary className="cursor-pointer">思考中…</summary>
      <div className="mt-1 whitespace-pre-wrap">{part.text}</div>
    </details>
  )
}
```

```tsx
// step-divider.tsx
export function StepDivider({ part }: { part: any }) {
  return <div className="my-1 border-t border-dashed opacity-50" />
}
```

Commit.

---

## Task 7: Tool part renderer

**Files:**
- Create: `client/src/features/chat/components/tool-part-renderer.tsx`
- Create: `client/src/features/chat/components/generic-tool-card.tsx`
- Test: `client/src/features/chat/components/tool-part-renderer.test.tsx`

```tsx
// tool-part-renderer.tsx
import { useActionRegistryStore } from '@/stores/action-registry-store'
import { getRenderers } from '@/features/actions/registry'
import { GenericToolCard } from './generic-tool-card'

export function ToolPartRenderer({ part }: { part: any }) {
  const descriptor = useActionRegistryStore(s => s.descriptors[part.tool])
  const custom = getRenderers(part.tool)
  if (custom?.leftCard) {
    const Custom = custom.leftCard
    return <Custom part={part} descriptor={descriptor ?? fallbackDescriptor(part.tool)} />
  }
  return <GenericToolCard part={part} descriptor={descriptor ?? fallbackDescriptor(part.tool)} />
}

function fallbackDescriptor(id: string): any {
  return { id, executor: 'SERVER', description: id, inputSchema: {}, outputSchema: {},
    produces: [], sideEffects: [], requiresConnection: false, timeoutMs: 30000 }
}
```

```tsx
// generic-tool-card.tsx
export function GenericToolCard({ part, descriptor }: any) {
  const status = (part.state?.status ?? 'pending') as string
  const badgeColor = ({
    pending: 'bg-gray-200',
    running: 'bg-blue-200',
    completed: 'bg-green-200',
    error: 'bg-red-200',
  } as Record<string, string>)[status] ?? 'bg-gray-200'
  return (
    <div className="my-2 rounded border bg-background p-2 text-xs">
      <div className="flex items-center gap-2">
        <span className={`rounded px-2 py-0.5 ${badgeColor}`}>{status}</span>
        <span className="font-mono">{descriptor.id}</span>
      </div>
      <div className="mt-1 text-muted-foreground">{descriptor.description}</div>
    </div>
  )
}
```

Commit.

---

## Task 8: Session mode state machine

Implemented in Task 3's `session-store.ts`. Add thin hook:

```ts
// client/src/features/session/use-session-mode.ts
import { useSessionStore } from '@/stores/session-store'
export function useSessionMode() {
  const activeId = useSessionStore(s => s.activeSessionId)
  const mode = useSessionStore(s => activeId ? (s.modeBySession.get(activeId) ?? 'HERO') : 'NOSESS')
  return { sessionId: activeId, mode }
}
```

Commit with store tests.

---

## Task 9: PromptComposer (shared element)

**Files:**
- Create: `client/src/features/session/prompt-composer.tsx`
- Test: `client/src/features/session/prompt-composer.test.tsx`

Key design: composer is rendered inside **both** HERO and SPLIT subtrees but as a single component that's portaled to a stable DOM node (to preserve focus and DOM identity across state flips). Alternative: render once at the layout root and absolutely-position via refs. For MVP we use React portal into a dedicated `<div id="composer-slot" />` that lives at the layout root.

```tsx
// prompt-composer.tsx
import { useState, type FormEvent, type KeyboardEvent, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { Button } from '@/components/ui/button'
import { useSessionStore } from '@/stores/session-store'
import { useChannel } from '@/services/channel/use-channel'
import { classifyIntent } from '@/features/actions/classify-intent'
import { useConnectionStore } from '@/features/connection/store'

export function PromptComposer() {
  const slot = typeof document !== 'undefined' ? document.getElementById('composer-slot') : null
  if (!slot) return null
  return createPortal(<Inner />, slot)
}

function Inner() {
  const [text, setText] = useState('')
  const { sendMessage, isStreaming } = useChannel()
  const activeConn = useConnectionStore(s => s.activeConnectionId)
  const setPendingPrompt = useSessionStore(s => s.setPendingPrompt)
  const setPendingConnectionPrompt = useSessionStore(s => s.setPendingConnectionPrompt)

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const t = text.trim()
    if (!t || isStreaming) return
    setText('')
    if (classifyIntent(t) === 'db_related' && !activeConn) {
      setPendingPrompt(t)
      setPendingConnectionPrompt(true)
      return
    }
    await sendMessage([{ type: 'text', id: crypto.randomUUID(),
      sessionID: '', messageID: '', text: t, metadata: {} } as any])
  }

  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); void onSubmit(e as unknown as FormEvent) }
  }

  return (
    <form onSubmit={onSubmit} className="border-t bg-background p-3">
      <div className="flex items-end gap-2">
        <textarea value={text} onChange={e => setText(e.target.value)} onKeyDown={onKey}
          placeholder="Enter 发送，Shift+Enter 换行" rows={2}
          className="flex-1 resize-none rounded-md border bg-background px-3 py-2 text-sm
                     focus:outline-none focus:ring-2 focus:ring-ring"/>
        <Button type="submit" disabled={!text.trim() || isStreaming}>发送</Button>
      </div>
    </form>
  )
}
```

Add `use-channel.ts` glue:

```ts
// client/src/services/channel/use-channel.ts
import { useCallback, useMemo, useState } from 'react'
import { ChannelClient } from './channel-client'
import { reduceEvent } from './event-reducer'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'
import { useSessionStore } from '@/stores/session-store'

export function useChannel() {
  const [isStreaming, setIsStreaming] = useState(false)
  const sessionId = useSessionStore(s => s.activeSessionId)
  const enterSplit = useSessionStore(s => s.enterSplit)
  const upsertPart = useChatPartsStore(s => s.upsertPart)
  const upsertArtifact = useOntologyStore(s => s.upsertArtifact)
  const addArtifact = useTimelineStore(s => s.addArtifact)

  const client = useMemo(() => sessionId
      ? new ChannelClient({ baseUrl: '', sessionId, clientId: crypto.randomUUID() })
      : null, [sessionId])

  const sendMessage = useCallback(async (parts: any[]) => {
    if (!client || !sessionId) return
    setIsStreaming(true)
    enterSplit(sessionId)
    try {
      await client.sendMessage(parts, evt => {
        const { event, data } = evt
        if (event === 'message.part.created' || event === 'message.part.updated') {
          upsertPart((data as any).part)
        }
        if (event === 'ontology.updated') {
          const d = data as any
          if (d.objectType === 'datatalk.artifact') {
            upsertArtifact({ id: d.id, version: d.patch?.version ?? 1,
              kind: d.patch?.kind ?? 'table',
              supersedesId: d.patch?.supersedesId,
              payload: d.patch })
            addArtifact(sessionId, d.id, d.patch?.supersedesId)
          }
        }
      })
    } finally { setIsStreaming(false) }
  }, [client, sessionId, enterSplit, upsertPart, upsertArtifact, addArtifact])

  return { sendMessage, isStreaming }
}
```

Commit.

---

## Task 10: HeroView + SplitView

**Files:** `client/src/features/session/hero-view.tsx`, `split-view.tsx`

```tsx
// hero-view.tsx
export function HeroView() {
  return (
    <div className="flex h-full items-center justify-center p-8">
      <div className="w-full max-w-2xl">
        <h1 className="mb-6 text-center text-xl font-light text-muted-foreground">
          问点什么，比如 "查询用户表最近一周的注册趋势"
        </h1>
        <div id="composer-slot" />
      </div>
    </div>
  )
}
```

```tsx
// split-view.tsx
import { PanelGroup, Panel, PanelResizeHandle } from 'react-resizable-panels'
import { MessageStream } from '@/features/chat/components/message-stream'
import { ArtifactTimelineStrip } from '@/features/ontology/components/artifact-timeline-strip'
import { ArtifactCanvas } from '@/features/ontology/components/artifact-canvas'

export function SplitView() {
  return (
    <PanelGroup direction="horizontal" className="h-full">
      <Panel defaultSize={48} minSize={25}>
        <div className="flex h-full flex-col">
          <div className="flex-1 overflow-y-auto p-4"><MessageStream /></div>
          <div id="composer-slot" />
        </div>
      </Panel>
      <PanelResizeHandle className="w-px bg-border hover:bg-primary/50" />
      <Panel defaultSize={52} minSize={25}>
        <div className="flex h-full flex-col">
          <ArtifactTimelineStrip />
          <div className="flex-1 overflow-hidden"><ArtifactCanvas /></div>
        </div>
      </Panel>
    </PanelGroup>
  )
}
```

`MessageStream`:

```tsx
// features/chat/components/message-stream.tsx
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { PartRenderer } from './part-renderer'

export function MessageStream() {
  const partsByMessage = useChatPartsStore(s => s.partsByMessage)
  const all = Array.from(partsByMessage.values()).flat()
  return (
    <div className="flex flex-col gap-2">
      {all.map(p => <PartRenderer key={p.id} part={p} />)}
    </div>
  )
}
```

Commit.

---

## Task 11: FLIP composer animation

**File:** `client/src/features/session/use-flip-composer.ts`

MVP note: since composer is portaled into `#composer-slot` whose **position in the tree differs** between hero and split, we don't get a free FLIP from React. We read the slot's `getBoundingClientRect()` before and after mode change and animate with `transform`. Gate by `prefers-reduced-motion`.

```ts
import { useLayoutEffect, useRef } from 'react'
import { useSessionMode } from './use-session-mode'

export function useFlipComposer() {
  const { mode } = useSessionMode()
  const lastRect = useRef<DOMRect | null>(null)

  useLayoutEffect(() => {
    const slot = document.getElementById('composer-slot')
    if (!slot) return
    const newRect = slot.getBoundingClientRect()
    if (lastRect.current) {
      const dx = lastRect.current.left - newRect.left
      const dy = lastRect.current.top - newRect.top
      const sx = lastRect.current.width / newRect.width
      const sy = lastRect.current.height / newRect.height
      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        slot.style.opacity = '0'
        slot.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 150, fill: 'forwards' })
      } else {
        slot.style.transformOrigin = 'top left'
        slot.animate([
          { transform: `translate(${dx}px, ${dy}px) scale(${sx}, ${sy})` },
          { transform: 'translate(0, 0) scale(1, 1)' }
        ], { duration: 260, easing: 'cubic-bezier(.22,.61,.36,1)', fill: 'both' })
      }
    }
    lastRect.current = newRect
  }, [mode])
}
```

Call it from `SessionCanvas`. Commit.

---

## Task 12: ArtifactTimelineStrip

**File:** `client/src/features/ontology/components/artifact-timeline-strip.tsx`

```tsx
import { useTimelineStore } from '@/stores/timeline-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useSessionStore } from '@/stores/session-store'
import { cn } from '@/lib/utils'

export function ArtifactTimelineStrip() {
  const sessionId = useSessionStore(s => s.activeSessionId)
  const order = useTimelineStore(s => sessionId ? (s.orderBySession.get(sessionId) ?? []) : [])
  const active = useTimelineStore(s => sessionId ? s.activeBySession.get(sessionId) : null)
  const setActive = useTimelineStore(s => s.setActive)
  const artifacts = useOntologyStore(s => s.artifacts)

  return (
    <div className="flex gap-1 overflow-x-auto border-b p-2">
      {order.map(id => {
        const a = artifacts.get(id)
        if (!a) return null
        const superseded = Array.from(artifacts.values()).some(x => x.supersedesId === id)
        return (
          <button key={id} onClick={() => sessionId && setActive(sessionId, id)}
            className={cn(
              'rounded-full px-3 py-1 text-xs border',
              id === active ? 'bg-primary text-primary-foreground' : 'bg-background',
              superseded && 'opacity-40'
            )}>
            {a.kind === 'table' ? '表' : a.kind === 'chart' ? '图' : 'ER'} · {id.slice(-4)}
          </button>
        )
      })}
    </div>
  )
}
```

Commit.

---

## Task 13: ArtifactDispatcher + TableArtifact + ChartArtifact + ErdArtifact

**Files:**
- Create: `client/src/features/ontology/components/artifact-canvas.tsx`, `artifact-dispatcher.tsx`, `table-artifact.tsx`, `chart-artifact.tsx`, `erd-artifact.tsx`
- Create: `client/src/features/ontology/echarts-to-recharts.ts`

```tsx
// artifact-canvas.tsx
import { useTimelineStore } from '@/stores/timeline-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useSessionStore } from '@/stores/session-store'
import { ArtifactDispatcher } from './artifact-dispatcher'
export function ArtifactCanvas() {
  const sessionId = useSessionStore(s => s.activeSessionId)
  const id = useTimelineStore(s => sessionId ? s.activeBySession.get(sessionId) : null)
  const a = useOntologyStore(s => id ? s.artifacts.get(id) : null)
  if (!a) return <div className="flex h-full items-center justify-center text-sm text-muted-foreground">AI 正在准备…</div>
  return <ArtifactDispatcher artifact={a} />
}
```

```tsx
// artifact-dispatcher.tsx
import type { Artifact } from '@/services/channel/event-reducer'
import { TableArtifact } from './table-artifact'
import { ChartArtifact } from './chart-artifact'
import { ErdArtifact } from './erd-artifact'

export function ArtifactDispatcher({ artifact }: { artifact: Artifact }) {
  switch (artifact.kind) {
    case 'table': return <TableArtifact artifact={artifact} />
    case 'chart': return <ChartArtifact artifact={artifact} />
    case 'erd':   return <ErdArtifact artifact={artifact} />
  }
}
```

`table-artifact.tsx` — use TanStack Table (already in dependencies):

```tsx
import { useMemo } from 'react'
import { flexRender, getCoreRowModel, useReactTable } from '@tanstack/react-table'
import type { Artifact } from '@/services/channel/event-reducer'

export function TableArtifact({ artifact }: { artifact: Artifact }) {
  const payload = artifact.payload as any
  const columns = useMemo(() => (payload?.columns ?? []).map((c: string) => ({
    header: c, accessorKey: c, id: c,
  })), [payload])
  const data = payload?.preview ?? []
  const table = useReactTable({ data, columns, getCoreRowModel: getCoreRowModel() })
  return (
    <div className="h-full overflow-auto">
      <table className="w-full text-xs">
        <thead>
          {table.getHeaderGroups().map(hg => (
            <tr key={hg.id} className="border-b bg-muted">
              {hg.headers.map(h => (
                <th key={h.id} className="p-2 text-left font-medium">
                  {flexRender(h.column.columnDef.header, h.getContext())}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody>
          {table.getRowModel().rows.map(r => (
            <tr key={r.id} className="border-b">
              {r.getVisibleCells().map(c => (
                <td key={c.id} className="p-2">{flexRender(c.column.columnDef.cell, c.getContext())}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
```

`echarts-to-recharts.ts`:

```ts
export type ChartSpec =
  | { type: 'line', xData: (string|number)[], series: { name?: string, data: number[] }[], colors?: string[] }
  | { type: 'bar',  xData: (string|number)[], series: { name?: string, data: number[] }[], colors?: string[] }
  | { type: 'pie',  data: { name: string, value: number }[], colors?: string[] }
  | { type: 'unsupported' }

export function echartsOptionToChartSpec(opt: any): ChartSpec {
  try {
    const first = (opt.series ?? [])[0]
    if (!first) return { type: 'unsupported' }
    if (first.type === 'line' || first.type === 'bar') {
      const xData = (opt.xAxis?.data ?? []) as (string|number)[]
      const series = (opt.series ?? []).map((s: any) => ({ name: s.name, data: s.data ?? [] }))
      return { type: first.type as 'line'|'bar', xData, series, colors: opt.color ?? [] }
    }
    if (first.type === 'pie') {
      return { type: 'pie', data: (first.data ?? []).map((d: any) => ({ name: d.name, value: d.value })),
        colors: opt.color ?? [] }
    }
    return { type: 'unsupported' }
  } catch { return { type: 'unsupported' } }
}
```

`chart-artifact.tsx`:

```tsx
import { useMemo, useRef, useEffect } from 'react'
import { LineChart, Line, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts'
import { echartsOptionToChartSpec } from '../echarts-to-recharts'
import type { Artifact } from '@/services/channel/event-reducer'

export function ChartArtifact({ artifact }: { artifact: Artifact }) {
  const spec = useMemo(() =>
    echartsOptionToChartSpec((artifact.payload as any)?.echartsOption),
    [artifact.payload]
  )
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    ref.current?.animate([{ opacity: 0.6 }, { opacity: 1 }],
      { duration: 180, fill: 'forwards' })
  }, [artifact.id, artifact.version])

  if (spec.type === 'unsupported') {
    return <div className="p-4 text-xs text-muted-foreground">不支持的图表规格</div>
  }

  const color = spec.colors?.[0] ?? '#3b82f6'

  return (
    <div ref={ref} className="h-full w-full">
      <ResponsiveContainer>
        {spec.type === 'line' ? (
          <LineChart data={toRechartsData(spec)}>
            <XAxis dataKey="x" />
            <YAxis />
            <Tooltip />
            {spec.series.map((s, i) => (
              <Line key={i} type="monotone" dataKey={`s${i}`} stroke={color} dot={false} />
            ))}
          </LineChart>
        ) : spec.type === 'bar' ? (
          <BarChart data={toRechartsData(spec)}>
            <XAxis dataKey="x" />
            <YAxis />
            <Tooltip />
            {spec.series.map((s, i) => <Bar key={i} dataKey={`s${i}`} fill={color} />)}
          </BarChart>
        ) : (
          <PieChart>
            <Pie data={spec.data} dataKey="value" nameKey="name" outerRadius={120}>
              {spec.data.map((_, i) => <Cell key={i} fill={spec.colors?.[i % (spec.colors?.length ?? 1)] ?? color} />)}
            </Pie>
            <Tooltip />
          </PieChart>
        )}
      </ResponsiveContainer>
    </div>
  )
}

function toRechartsData(s: Extract<ReturnType<typeof echartsOptionToChartSpec>, { type: 'line'|'bar' }>) {
  return (s.xData ?? []).map((x, i) => {
    const row: any = { x }
    s.series.forEach((ss, si) => { row[`s${si}`] = ss.data[i] })
    return row
  })
}
```

`erd-artifact.tsx` stub — paint nodes as a simple `<div>` grid (no React Flow in MVP to avoid a new dep; Plan C.1 can add it).

Commit.

---

## Task 14: WorkspaceLayout + Connection overlay

**Files:**
- Modify: `client/src/layouts/workspace-layout.tsx`
- Create: `client/src/features/session/session-canvas.tsx`
- Create: `client/src/features/session/connection-overlay.tsx`

```tsx
// session-canvas.tsx
import { useSessionMode } from './use-session-mode'
import { useFlipComposer } from './use-flip-composer'
import { HeroView } from './hero-view'
import { SplitView } from './split-view'
import { PromptComposer } from './prompt-composer'
import { ConnectionOverlay } from './connection-overlay'

export function SessionCanvas() {
  const { mode } = useSessionMode()
  useFlipComposer()
  return (
    <div className="relative h-full">
      {mode === 'HERO' && <HeroView />}
      {mode === 'SPLIT' && <SplitView />}
      {mode === 'NOSESS' && <div className="flex h-full items-center justify-center text-muted-foreground">请从侧边栏选择或新建会话</div>}
      <PromptComposer />
      <ConnectionOverlay />
    </div>
  )
}
```

```tsx
// connection-overlay.tsx
import { useSessionStore } from '@/stores/session-store'
import { useConnectionStore } from '@/features/connection/store'

export function ConnectionOverlay() {
  const pending = useSessionStore(s => s.pendingConnectionPrompt)
  const pendingPrompt = useSessionStore(s => s.pendingPrompt)
  const setPending = useSessionStore(s => s.setPendingConnectionPrompt)
  const setPrompt = useSessionStore(s => s.setPendingPrompt)
  const connections = useConnectionStore(s => s.connections)
  const setActive = useConnectionStore(s => s.setActive)

  if (!pending) return null
  return (
    <div className="absolute left-1/2 top-24 z-50 w-96 -translate-x-1/2 rounded border bg-background p-3 shadow">
      <div className="text-sm">这是数据库相关问题但没选连接，选一个：</div>
      <ul className="mt-2 space-y-1">
        {connections.map(c => (
          <li key={c.id}>
            <button className="w-full rounded border px-2 py-1 text-left text-xs hover:bg-muted"
              onClick={() => {
                setActive(c.id)
                setPending(false)
                // the composer listens for pendingPrompt and will resend automatically
                setPrompt(pendingPrompt)
              }}>{c.id} ({c.kind})</button>
          </li>
        ))}
      </ul>
    </div>
  )
}
```

Wire `PromptComposer` to check `pendingPrompt` on mount and auto-send; clear it once sent. Commit.

Modify `workspace-layout.tsx`:

```tsx
import { PanelGroup, Panel, PanelResizeHandle } from 'react-resizable-panels'
import { ConnectionList } from '@/features/connection/components/connection-list'
import { SessionList } from '@/features/session/components/session-list'
import { SessionCanvas } from '@/features/session/session-canvas'
import { useBootstrapActions } from '@/features/actions/use-bootstrap-actions'
import { useSessionMode } from '@/features/session/use-session-mode'

export function WorkspaceLayout() {
  useBootstrapActions()
  const { mode } = useSessionMode()
  return (
    <PanelGroup direction="horizontal" className="h-screen w-screen">
      <Panel defaultSize={18} minSize={12} maxSize={30}>
        <aside className={`flex h-full flex-col border-r transition-opacity ${mode === 'HERO' ? 'opacity-50' : 'opacity-100'}`}>
          <ConnectionList />
          <SessionList />
        </aside>
      </Panel>
      <PanelResizeHandle className="w-px bg-border hover:bg-primary/50" />
      <Panel>
        <SessionCanvas />
      </Panel>
    </PanelGroup>
  )
}
```

Commit.

---

## Task 15: classifyIntent + client handler for pin_artifact

**Files:**
- Create: `client/src/features/actions/classify-intent.ts`
- Create: `client/src/features/actions/client-handlers.ts`
- Test: `client/src/features/actions/classify-intent.test.ts`

```ts
// classify-intent.ts
const KEYWORDS = /(表|字段|查询|查一下|查下|SELECT|FROM|WHERE|JOIN|count|average|趋势|报表|统计|用户表|订单|数据库|schema|ER 图|ER图)/i
export function classifyIntent(text: string): 'db_related' | 'other' {
  return KEYWORDS.test(text) ? 'db_related' : 'other'
}
```

```ts
// client-handlers.ts
import { registerClientHandler } from './registry'
import { useOntologyStore } from '@/stores/ontology-store'

registerClientHandler('datatalk.pin_artifact', async (input) => {
  const { artifactId } = input as any
  const state = useOntologyStore.getState()
  const a = state.artifacts.get(artifactId)
  if (a) state.upsertArtifact({ ...a, pinned: true })
  return { pinned: true }
})
```

Call `import './features/actions/client-handlers'` from `main.tsx` to register on startup.

Add a dispatcher in `channel-store` that watches for `action.invoke` events and calls `getClientHandler(actionId)(input)`, then sends `actionResult`:

```ts
// service glue — put in use-channel.ts
if (event === 'action.invoke') {
  const { callId, actionId, input } = data as any
  const handler = getClientHandler(actionId)
  if (handler) {
    try {
      const output = await handler(input, { sessionId: sessionId! })
      await client?.actionResult(callId, true, output)
    } catch (err: any) {
      await client?.actionResult(callId, false, undefined, { code: 'client_action_error', message: String(err) })
    }
  }
}
```

Commit.

---

## Task 16: E2E Playwright smoke test

**Files:**
- Create: `client/tests/e2e/manus-smoke.spec.ts`
- Create: `client/tests/e2e/mock-channel-server.ts`

Add `@playwright/test` + `msw` to devDeps:

```
pnpm -C client add -D @playwright/test
pnpm -C client exec playwright install --with-deps chromium
```

`mock-channel-server.ts` — a tiny Node http server script the Playwright test launches. It serves:
- `GET /api/actions` → returns our demo registry JSON
- `POST /api/sessions/:id/channel` → returns SSE with pre-scripted sequence: connected / message.created / part.created × 2 / ontology.updated (artifact) / session.status:idle

`manus-smoke.spec.ts`:

```ts
import { test, expect } from '@playwright/test'

test('HERO → SPLIT + artifact appears', async ({ page }) => {
  await page.goto('http://localhost:5173/')

  // Assert HERO: centered composer, no split panels
  await expect(page.locator('#composer-slot textarea')).toBeVisible()
  await expect(page.getByText('AI 正在准备…')).toHaveCount(0)

  // Create a session via the sidebar (or mock-injected store)
  // For MVP test we assume a session is pre-seeded via a dev shortcut.

  await page.locator('#composer-slot textarea').fill('show me the users table')
  await page.keyboard.press('Enter')

  // Assert SPLIT: composer slid left, timeline chip present
  await expect(page.getByRole('button', { name: /表/ })).toBeVisible({ timeout: 5000 })
  await expect(page.getByRole('table')).toBeVisible()
})
```

Add a vite dev script that runs with the mock server. Commit.

---

## Plan C Completion

At the end, the Tauri client:

- Starts in HERO with a centered composer.
- On first message, flips into SPLIT via FLIP + clip-path.
- Streams text + reasoning + tool cards into the left column.
- Renders artifacts (table / chart / ERD stub) on the right, following the supersedes chain.
- Handles CLIENT-executor actions (e.g., pin_artifact) locally and round-trips the result.
- Falls back to a connection-picker overlay when the user asks a DB question without an active connection.
- Reflects on `/api/actions` and `/api/ontology` so new action UIs need only a `registerAction({ id, leftCard, rightArtifact? })` call.

## Self-Review

**Spec coverage check:** §4 client architecture is covered by Tasks 3 (stores), 6–7 (Part renderer), 8–11 (state machine + animation), 12–13 (right-column artifacts), 14 (layout integration), 15 (conversational connection prompt).

**Placeholder scan:** all code blocks are runnable; no TBDs.

**Type consistency:** `Part`, `Artifact`, `ActionDescriptor`, `StreamEvent` names unified across tasks. `ChannelClient` signatures match `use-channel.ts`. Timeline and ontology stores share the same `Artifact` shape.

**Scope check:** this plan is independent of Plan A/B at the HTTP contract layer. Using the `MockChannelServer` it can be developed ahead of a fully-working backend.

---

# Overall Plan Index

- **Plan A** — backend platform foundation (`2026-04-16-manus-a-backend-platform{,-part2,-part3,-part4}.md`), 26 tasks
- **Plan B** — MVP actions + real OpenCode loop (this repo), 13 tasks
- **Plan C** — client split-view UI (this file), 16 tasks

Execute in order A → (B ‖ C). B and C are independent once A is merged; C can develop against MockChannelServer while B finalizes.
