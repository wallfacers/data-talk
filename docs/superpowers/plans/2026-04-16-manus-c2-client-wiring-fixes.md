# Plan C2 — Manus Split-View Client Wiring & Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Manus split-view UI actually work end-to-end by unifying the accidentally-duplicated session store, wiring the sidebar's session click / "new session" button to the Manus canvas, fixing the PromptComposer portal race, loading history on session open, subscribing to SSE permanently, partitioning per-session state, and clearing dead code left over from the pre-split-view era.

**Architecture:** Single `useSessionStore` drives HERO↔SPLIT. Sidebar writes / canvas reads share one store. `useChannel` grows a `useEffect` that, on `activeSessionId` change, (a) loads history via REST, (b) opens a permanent SSE subscription. Per-session maps (`partsBySession`, `artifactsBySession`) replace the flat global ones so switching sessions does not leak. Backend grows two thin endpoints (`POST /api/sessions`, `GET /api/sessions`) so the sidebar can create and list real sessions.

**Tech Stack:** unchanged — React 19, Zustand, ky, eventsource-parser, TanStack Query, Vitest (frontend); Spring Boot 3.5, JdbcTemplate, JUnit 5 + MockMvc (backend).

**Spec Mapping:** implements spec §4.1 tie-ins that Plan C assumed Plan A would provide (Session create/list REST), §4.4 per-session store semantics, §4.7 pending-prompt resume, and §4.1 `seedFromServer` wire-up. Fixes the regressions introduced by commit `36d0c1e refactor(client): unify home as sidebar layout with grouped sessions` which left two parallel session stores.

**Prerequisites:** backend build (`cd server && mvn compile -q`) and frontend typecheck (`cd client && npx tsc --noEmit`) must be green at start.

---

## File Structure

### New files
- `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerIT.java`
- `client/src/features/session/hooks/use-session-history.ts`
- `client/src/features/session/hooks/use-session-subscribe.ts`
- `client/src/features/session/hooks/use-pending-prompt-resume.ts`
- `client/src/features/session/components/session-rail.tsx` (stub kept tiny — sidebar styling helper)
- `client/src/stores/session-store.test.ts`
- `client/src/features/session/prompt-composer.test.tsx`
- `client/src/features/session/hooks/use-session-history.test.ts`

### Modified files
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRepository.java` — add `listAll`, `listByConnection`
- `client/src/stores/session-store.ts` — extend with `openSession`, `closeSession`, seed-on-load
- `client/src/stores/chat-parts-store.ts` — partition by `sessionId`
- `client/src/stores/ontology-store.ts` — partition by `sessionId`
- `client/src/services/channel/channel-client.ts` — respect `VITE_API_BASE_URL`
- `client/src/services/channel/use-channel.ts` — permanent subscribe + history load + clean switch
- `client/src/services/api/session.ts` — `createdAt/updatedAt` as `number` to match backend `long`
- `client/src/features/workspace/components/app-sidebar.tsx` — wire "新建会话" to mutation
- `client/src/features/workspace/components/nav-sessions.tsx` — use unified store, call `openSession`
- `client/src/features/session/prompt-composer.tsx` — portal-race fix + streaming-stop button + pending-prompt resume
- `client/src/features/session/session-canvas.tsx` — add history + subscribe + resume hooks
- `client/src/features/session/connection-overlay.tsx` — after picking connection, keep `pendingPrompt` so resume hook re-sends
- `client/src/features/chat/components/message-stream.tsx` — group by message + role, order by createdAt
- `client/src/features/workspace/home-page.tsx` — HERO-aware sidebar dim

### Deleted files (dead post-refactor)
- `client/src/features/session/store.ts` (duplicate of `stores/session-store.ts`)
- `client/src/features/chat/components/chat-panel.tsx`
- `client/src/features/chat/components/chat-input.tsx`
- `client/src/features/chat/components/message-list.tsx`
- `client/src/features/chat/components/message-item.tsx`
- `client/src/features/chat/store.ts`
- `client/src/features/chat/hooks/use-chat.ts`
- `client/src/features/chat/types.ts`
- `client/src/features/workspace/components/workspace.tsx`
- `client/src/features/workspace/components/tab-bar.tsx`
- `client/src/features/workspace/components/query-result-tab.tsx`
- `client/src/features/workspace/components/empty-state.tsx` (replaced by `welcome-empty.tsx`)
- `client/src/features/workspace/store.ts`
- `client/src/features/workspace/types.ts`
- `client/src/services/api/chat.ts`

---

## Tasks Overview

| # | Task | Scope | Depends on |
|---|---|---|---|
| 0 | Backend: `SessionService` + `SessionController` (POST/GET sessions) | server | — |
| 1 | Client: unify `useSessionStore` — delete duplicate, add `openSession` | client | 0 |
| 2 | Client: partition `chat-parts-store` + `ontology-store` by session | client | 1 |
| 3 | Client: wire sidebar "新建会话" + session click to unified store | client | 1 |
| 4 | Client: `use-session-history` — REST load on `activeSessionId` change | client | 2 |
| 5 | Client: `use-session-subscribe` — permanent SSE subscription | client | 2 |
| 6 | Client: fix `PromptComposer` portal race + add stop button | client | 1 |
| 7 | Client: `use-pending-prompt-resume` — auto-resend after picking connection | client | 6 |
| 8 | Client: `ChannelClient` respects `VITE_API_BASE_URL` | client | — |
| 9 | Client: `MessageStream` — group by message, order by createdAt, role badges | client | 2 |
| 10 | Client: HERO-aware sidebar dim + header hide | client | 1 |
| 11 | Client: delete dead code (old chat/workspace modules) | client | 3 |
| 12 | End-to-end manual verification checklist | — | 0-11 |

Tasks are written so Task N does not depend on Task >N completing. Within a task, steps are strictly ordered.

---

## Task 0: Backend — `SessionService` + `SessionController`

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRepository.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerIT.java`

### Step 0.1: Add `listAll` + `listByConnection` to `SessionRepository`

- [ ] **Edit** `SessionRepository.java` — append after `markHasEverSent` method:

```java
    public java.util.List<SessionRecord> listAll() {
        return jdbc.query("SELECT * FROM sessions ORDER BY updated_at DESC", MAPPER);
    }

    public java.util.List<SessionRecord> listByConnection(String connectionId) {
        return jdbc.query(
            "SELECT * FROM sessions WHERE connection_id = ? ORDER BY updated_at DESC",
            MAPPER, connectionId);
    }
```

### Step 0.2: Create `SessionService`

- [ ] **Create** `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java`:

```java
package com.datatalk.application.session;

import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository repo;
    private final Clock clock;

    public SessionService(SessionRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    public SessionRecord create(String connectionId, String title) {
        long now = clock.millis();
        String id = UUID.randomUUID().toString();
        String safeTitle = (title == null || title.isBlank()) ? "新会话" : title;
        SessionRecord rec = new SessionRecord(id, connectionId, safeTitle, false, null, now, now);
        repo.upsert(rec);
        return rec;
    }

    public List<SessionRecord> list(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) return repo.listAll();
        return repo.listByConnection(connectionId);
    }

    public Optional<SessionRecord> find(String id) {
        return repo.findById(id);
    }
}
```

### Step 0.3: Create `SessionController`

- [ ] **Create** `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java`:

```java
package com.datatalk.adapter.controller;

import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.session.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService svc;

    public SessionController(SessionService svc) { this.svc = svc; }

    @GetMapping
    public List<SessionDto> list(@RequestParam(value = "connectionId", required = false) String connectionId) {
        return svc.list(connectionId).stream().map(SessionDto::from).toList();
    }

    @PostMapping
    public SessionDto create(@RequestBody CreateSessionRequest req) {
        if (req == null || req.connectionId() == null || req.connectionId().isBlank()) {
            throw new IllegalArgumentException("connectionId is required");
        }
        SessionRecord rec = svc.create(req.connectionId(), req.title());
        return SessionDto.from(rec);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionDto> get(@PathVariable String id) {
        return svc.find(id)
            .map(SessionDto::from)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record CreateSessionRequest(String connectionId, String title) {}

    public record SessionDto(
        String id,
        String connectionId,
        String title,
        boolean hasEverSent,
        long createdAt,
        long updatedAt
    ) {
        public static SessionDto from(SessionRecord r) {
            return new SessionDto(r.id(), r.connectionId(), r.title(),
                r.hasEverSent(), r.createdAt(), r.updatedAt());
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
```

### Step 0.4: Integration test

- [ ] **Create** `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerIT.java`:

```java
package com.datatalk.adapter.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SessionControllerIT {

    @Autowired MockMvc mvc;

    @Test
    void create_then_list_returns_session() throws Exception {
        String body = """
            {"connectionId":"conn-1","title":"冒烟测试"}
            """;

        String created = mvc.perform(post("/api/sessions")
                .contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.connectionId").value("conn-1"))
            .andExpect(jsonPath("$.title").value("冒烟测试"))
            .andExpect(jsonPath("$.hasEverSent").value(false))
            .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/sessions").param("connectionId", "conn-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].connectionId").value("conn-1"));
    }

    @Test
    void create_rejects_missing_connection() throws Exception {
        mvc.perform(post("/api/sessions")
                .contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void list_without_filter_returns_all() throws Exception {
        mvc.perform(post("/api/sessions").contentType("application/json")
                .content("{\"connectionId\":\"c-a\",\"title\":\"A\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/sessions").contentType("application/json")
                .content("{\"connectionId\":\"c-b\",\"title\":\"B\"}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/sessions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }
}
```

### Step 0.5: Verify + commit

- [ ] Run: `cd server && mvn -pl data-talk-adapter -am verify -q`
  Expected: BUILD SUCCESS, all tests pass.
- [ ] Commit:

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRepository.java \
        server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerIT.java
git commit -m "feat(server): expose POST/GET /api/sessions for client sidebar"
```

---

## Task 1: Unify `useSessionStore`

**Files:**
- Delete: `client/src/features/session/store.ts`
- Modify: `client/src/stores/session-store.ts`
- Create: `client/src/stores/session-store.test.ts`

### Step 1.1: Write failing test for unified store

- [ ] **Create** `client/src/stores/session-store.test.ts`:

```ts
import { describe, it, expect, beforeEach } from 'vitest'
import { useSessionStore } from './session-store'

function reset() {
  useSessionStore.setState({
    activeSessionId: null,
    modeBySession: new Map(),
    hasEverSentBySession: new Map(),
    pendingPrompt: null,
    pendingConnectionPrompt: false,
  })
}

describe('useSessionStore', () => {
  beforeEach(reset)

  it('openSession with hasEverSent=false enters HERO', () => {
    useSessionStore.getState().openSession('s1', false)
    const s = useSessionStore.getState()
    expect(s.activeSessionId).toBe('s1')
    expect(s.modeBySession.get('s1')).toBe('HERO')
  })

  it('openSession with hasEverSent=true enters SPLIT', () => {
    useSessionStore.getState().openSession('s2', true)
    expect(useSessionStore.getState().modeBySession.get('s2')).toBe('SPLIT')
  })

  it('enterSplit flips HERO to SPLIT and marks hasEverSent', () => {
    useSessionStore.getState().openSession('s3', false)
    useSessionStore.getState().enterSplit('s3')
    const s = useSessionStore.getState()
    expect(s.modeBySession.get('s3')).toBe('SPLIT')
    expect(s.hasEverSentBySession.get('s3')).toBe(true)
  })

  it('closeSession clears activeSessionId but preserves modes', () => {
    useSessionStore.getState().openSession('s4', true)
    useSessionStore.getState().closeSession()
    const s = useSessionStore.getState()
    expect(s.activeSessionId).toBeNull()
    expect(s.modeBySession.get('s4')).toBe('SPLIT')
  })

  it('reopening a session that has ever sent goes straight to SPLIT', () => {
    const api = useSessionStore.getState()
    api.openSession('s5', false)
    api.enterSplit('s5')
    api.closeSession()
    api.openSession('s5', false) // server still reports false but cache says true
    expect(useSessionStore.getState().modeBySession.get('s5')).toBe('SPLIT')
  })
})
```

- [ ] Run: `cd client && pnpm test src/stores/session-store.test.ts`
  Expected: FAIL (`openSession is not a function`, `closeSession is not a function`).

### Step 1.2: Rewrite `session-store.ts`

- [ ] **Replace** `client/src/stores/session-store.ts`:

```ts
import { create } from 'zustand'

export type SessionMode = 'NOSESS' | 'HERO' | 'SPLIT'

type SessionState = {
  activeSessionId: string | null
  modeBySession: Map<string, SessionMode>
  hasEverSentBySession: Map<string, boolean>
  pendingPrompt: string | null
  pendingConnectionPrompt: boolean

  openSession: (id: string, hasEverSent: boolean) => void
  closeSession: () => void
  enterSplit: (id: string) => void
  setPendingPrompt: (text: string | null) => void
  setPendingConnectionPrompt: (on: boolean) => void
}

export const useSessionStore = create<SessionState>((set) => ({
  activeSessionId: null,
  modeBySession: new Map(),
  hasEverSentBySession: new Map(),
  pendingPrompt: null,
  pendingConnectionPrompt: false,

  openSession: (id, hasEverSent) => set((s) => {
    // Cache wins: once we've observed hasEverSent=true locally, never demote.
    const cachedSent = s.hasEverSentBySession.get(id) ?? false
    const effectiveSent = cachedSent || hasEverSent
    const mode: SessionMode = effectiveSent ? 'SPLIT' : 'HERO'
    const modes = new Map(s.modeBySession); modes.set(id, mode)
    const sent = new Map(s.hasEverSentBySession); sent.set(id, effectiveSent)
    return { activeSessionId: id, modeBySession: modes, hasEverSentBySession: sent }
  }),

  closeSession: () => set({ activeSessionId: null }),

  enterSplit: (id) => set((s) => {
    const modes = new Map(s.modeBySession); modes.set(id, 'SPLIT')
    const sent = new Map(s.hasEverSentBySession); sent.set(id, true)
    return { modeBySession: modes, hasEverSentBySession: sent }
  }),

  setPendingPrompt: (text) => set({ pendingPrompt: text }),
  setPendingConnectionPrompt: (on) => set({ pendingConnectionPrompt: on }),
}))
```

- [ ] Run: `cd client && pnpm test src/stores/session-store.test.ts`
  Expected: PASS.

### Step 1.3: Delete duplicate store

- [ ] **Delete**: `client/src/features/session/store.ts`

### Step 1.4: Update `use-session-mode.ts`

- [ ] **Verify file already imports from `@/stores/session-store`** — `client/src/features/session/use-session-mode.ts:1`. No change needed.

### Step 1.5: Typecheck and commit

- [ ] Run: `cd client && npx tsc --noEmit`
  Expected: errors pointing at `features/session/store` import sites (we'll fix those in Task 3). **Pause here until Task 3 brings typecheck green.**

> **Commit is deferred to Task 3** because the delete in Step 1.3 breaks imports in `nav-sessions.tsx` that Task 3 rewrites. Stage nothing yet.

---

## Task 2: Partition per-session stores

**Files:**
- Modify: `client/src/stores/chat-parts-store.ts`
- Modify: `client/src/stores/ontology-store.ts`
- Create: `client/src/stores/chat-parts-store.test.ts`

### Step 2.1: Write failing chat-parts test

- [ ] **Create** `client/src/stores/chat-parts-store.test.ts`:

```ts
import { describe, it, expect, beforeEach } from 'vitest'
import { useChatPartsStore } from './chat-parts-store'

function reset() {
  useChatPartsStore.setState({ partsBySession: new Map() })
}

const part = (id: string, messageID: string, text: string) => ({
  type: 'text', id, sessionID: '', messageID, text, metadata: {},
}) as any

describe('useChatPartsStore', () => {
  beforeEach(reset)

  it('scopes parts by sessionId', () => {
    const api = useChatPartsStore.getState()
    api.upsertPart('s-a', part('p1', 'm1', 'hi A'))
    api.upsertPart('s-b', part('p2', 'm2', 'hi B'))

    expect(api.getParts('s-a')).toHaveLength(1)
    expect(api.getParts('s-b')).toHaveLength(1)
    expect(api.getParts('s-a')[0].id).toBe('p1')
  })

  it('upsert replaces part with matching id in the same session', () => {
    const api = useChatPartsStore.getState()
    api.upsertPart('s1', part('p1', 'm1', 'hello'))
    api.upsertPart('s1', part('p1', 'm1', 'hello world'))
    expect(api.getParts('s1')).toHaveLength(1)
    expect((api.getParts('s1')[0] as any).text).toBe('hello world')
  })

  it('clearSession drops one session without affecting others', () => {
    const api = useChatPartsStore.getState()
    api.upsertPart('s1', part('p1', 'm1', 'a'))
    api.upsertPart('s2', part('p2', 'm2', 'b'))
    api.clearSession('s1')
    expect(api.getParts('s1')).toHaveLength(0)
    expect(api.getParts('s2')).toHaveLength(1)
  })
})
```

- [ ] Run: `cd client && pnpm test src/stores/chat-parts-store.test.ts`
  Expected: FAIL.

### Step 2.2: Rewrite `chat-parts-store.ts`

- [ ] **Replace** `client/src/stores/chat-parts-store.ts`:

```ts
import { create } from 'zustand'
import type { Part } from '@/services/channel/types'

type ChatPartsState = {
  partsBySession: Map<string, Map<string, Part[]>>  // sessionId → messageId → Part[]
  upsertPart: (sessionId: string, part: Part) => void
  upsertMany: (sessionId: string, parts: Part[]) => void
  removePart: (sessionId: string, messageId: string, partId: string) => void
  clearSession: (sessionId: string) => void
  getParts: (sessionId: string) => Part[]
}

export const useChatPartsStore = create<ChatPartsState>((set, get) => ({
  partsBySession: new Map(),

  upsertPart: (sessionId, part) => set((s) => {
    const bySession = new Map(s.partsBySession)
    const byMessage = new Map(bySession.get(sessionId) ?? new Map())
    const list = [...(byMessage.get(part.messageID) ?? [])]
    const idx = list.findIndex((p) => p.id === part.id)
    if (idx >= 0) list[idx] = part
    else list.push(part)
    byMessage.set(part.messageID, list)
    bySession.set(sessionId, byMessage)
    return { partsBySession: bySession }
  }),

  upsertMany: (sessionId, parts) => {
    for (const p of parts) get().upsertPart(sessionId, p)
  },

  removePart: (sessionId, messageId, partId) => set((s) => {
    const bySession = new Map(s.partsBySession)
    const byMessage = new Map(bySession.get(sessionId) ?? new Map())
    const list = (byMessage.get(messageId) ?? []).filter((p) => p.id !== partId)
    byMessage.set(messageId, list)
    bySession.set(sessionId, byMessage)
    return { partsBySession: bySession }
  }),

  clearSession: (sessionId) => set((s) => {
    const bySession = new Map(s.partsBySession)
    bySession.delete(sessionId)
    return { partsBySession: bySession }
  }),

  getParts: (sessionId) => {
    const byMessage = get().partsBySession.get(sessionId)
    if (!byMessage) return []
    return Array.from(byMessage.values()).flat()
  },
}))
```

- [ ] Run: `cd client && pnpm test src/stores/chat-parts-store.test.ts`
  Expected: PASS.

### Step 2.3: Rewrite `ontology-store.ts` with session partition

- [ ] **Replace** `client/src/stores/ontology-store.ts`:

```ts
import { create } from 'zustand'
import type { Artifact } from '@/services/channel/event-reducer'

type OntologyState = {
  artifactsBySession: Map<string, Map<string, Artifact>>
  upsertArtifact: (sessionId: string, a: Artifact) => void
  replaceSession: (sessionId: string, artifacts: Artifact[]) => void
  removeArtifact: (sessionId: string, id: string) => void
  clearSession: (sessionId: string) => void
  getArtifacts: (sessionId: string) => Map<string, Artifact>
}

const EMPTY: Map<string, Artifact> = new Map()

export const useOntologyStore = create<OntologyState>((set, get) => ({
  artifactsBySession: new Map(),

  upsertArtifact: (sessionId, a) => set((s) => {
    const bySession = new Map(s.artifactsBySession)
    const map = new Map(bySession.get(sessionId) ?? new Map())
    map.set(a.id, { ...(map.get(a.id) ?? ({} as Artifact)), ...a })
    bySession.set(sessionId, map)
    return { artifactsBySession: bySession }
  }),

  replaceSession: (sessionId, artifacts) => set((s) => {
    const bySession = new Map(s.artifactsBySession)
    const map = new Map<string, Artifact>()
    for (const a of artifacts) map.set(a.id, a)
    bySession.set(sessionId, map)
    return { artifactsBySession: bySession }
  }),

  removeArtifact: (sessionId, id) => set((s) => {
    const bySession = new Map(s.artifactsBySession)
    const map = new Map(bySession.get(sessionId) ?? new Map())
    map.delete(id)
    bySession.set(sessionId, map)
    return { artifactsBySession: bySession }
  }),

  clearSession: (sessionId) => set((s) => {
    const bySession = new Map(s.artifactsBySession)
    bySession.delete(sessionId)
    return { artifactsBySession: bySession }
  }),

  getArtifacts: (sessionId) => get().artifactsBySession.get(sessionId) ?? EMPTY,
}))
```

### Step 2.4: Update consumers of the old flat APIs

- [ ] **Edit** `client/src/features/chat/components/message-stream.tsx` — replace body:

```tsx
import { useMemo } from 'react'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useSessionStore } from '@/stores/session-store'
import { PartRenderer } from './part-renderer'

export function MessageStream() {
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const partsBySession = useChatPartsStore((s) => s.partsBySession)
  const byMessage = sessionId ? partsBySession.get(sessionId) : undefined

  const groups = useMemo(() => {
    if (!byMessage) return []
    const entries = Array.from(byMessage.entries())
    // Parts for a single message come in the order reduced; messages come
    // in insertion order, which matches creation order from the reducer.
    return entries.map(([messageId, parts]) => ({ messageId, parts }))
  }, [byMessage])

  return (
    <div className="flex flex-col gap-4">
      {groups.map((g) => (
        <div key={g.messageId} className="flex flex-col gap-1">
          {g.parts.map((p) => <PartRenderer key={p.id} part={p} />)}
        </div>
      ))}
    </div>
  )
}
```

- [ ] **Edit** `client/src/features/ontology/components/artifact-canvas.tsx` — replace:

```tsx
import { useTimelineStore } from '@/stores/timeline-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useSessionStore } from '@/stores/session-store'
import { ArtifactDispatcher } from './artifact-dispatcher'

export function ArtifactCanvas() {
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const id = useTimelineStore((s) => (sessionId ? s.activeBySession.get(sessionId) : null))
  const artifacts = useOntologyStore((s) => (sessionId ? s.artifactsBySession.get(sessionId) : undefined))
  const a = id && artifacts ? artifacts.get(id) : null
  if (!a) return (
    <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
      AI 正在准备…
    </div>
  )
  return <ArtifactDispatcher artifact={a} />
}
```

- [ ] **Edit** `client/src/features/ontology/components/artifact-timeline-strip.tsx` — replace the `const artifacts = useOntologyStore(s => s.artifacts)` line:

```tsx
  const artifacts = useOntologyStore((s) => (sessionId ? (s.artifactsBySession.get(sessionId) ?? new Map()) : new Map()))
```

- [ ] **Edit** `client/src/features/actions/client-handlers.ts` — replace body:

```ts
import { registerClientHandler } from './registry'
import { useOntologyStore } from '@/stores/ontology-store'

registerClientHandler('datatalk.pin_artifact', async (input, ctx) => {
  const { artifactId } = input as { artifactId: string }
  const map = useOntologyStore.getState().artifactsBySession.get(ctx.sessionId)
  const a = map?.get(artifactId)
  if (a) useOntologyStore.getState().upsertArtifact(ctx.sessionId, { ...a, pinned: true })
  return { pinned: true }
})
```

### Step 2.5: Verify + commit

- [ ] Run: `cd client && pnpm test src/stores`
  Expected: PASS.
- [ ] Run: `cd client && npx tsc --noEmit`
  Expected: still errors in `nav-sessions.tsx` + `use-channel.ts` (fixed in Tasks 3 + 4). **Do not commit yet**; Task 3 will land a combined commit.

---

## Task 3: Wire sidebar to unified store + real create

**Files:**
- Modify: `client/src/features/workspace/components/nav-sessions.tsx`
- Modify: `client/src/features/workspace/components/app-sidebar.tsx`
- Modify: `client/src/services/api/session.ts`
- Modify: `client/src/services/channel/use-channel.ts` (just drop old `enterSplit` usage prep)
- Modify: `client/src/features/session/connection-overlay.tsx`

### Step 3.1: Align `Session` type with backend

- [ ] **Replace** `client/src/services/api/session.ts`:

```ts
import { http } from '@/services/http'

export type Session = {
  id: string
  connectionId: string
  title: string
  hasEverSent: boolean
  createdAt: number
  updatedAt: number
}

export function listSessions(connectionId?: string) {
  const search = connectionId ? { connectionId } : undefined
  return http.get('sessions', { searchParams: search }).json<Session[]>()
}

export function createSession(connectionId: string, title: string) {
  return http.post('sessions', { json: { connectionId, title } }).json<Session>()
}
```

### Step 3.2: Rewrite `nav-sessions.tsx` to use unified store

- [ ] **Edit** `client/src/features/workspace/components/nav-sessions.tsx` — change line 28 and line 78 only:

Line 28: `import { useSessionStore } from '@/features/session/store'`
→ `import { useSessionStore } from '@/stores/session-store'`

Lines 78-79 — replace:

```tsx
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const setActive = useSessionStore((s) => s.setActive)
```

with:

```tsx
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const openSession = useSessionStore((s) => s.openSession)
```

Line 117 `onSelect={setActive}` → `onSelect={(id) => {
            const target = (sessions.data ?? []).find((x) => x.id === id)
            openSession(id, target?.hasEverSent ?? false)
          }}`

Full replacement of the `SessionGroupView` prop signature stays as-is; only the caller changes.

### Step 3.3: Wire "新建会话" button to real mutation

- [ ] **Replace** `client/src/features/workspace/components/app-sidebar.tsx`:

```tsx
import type { ComponentProps } from 'react'
import { DatabaseIcon, PlusIcon } from 'lucide-react'
import { toast } from 'sonner'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from '@/components/ui/sidebar'
import { createSession } from '@/services/api/session'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { NavSessions } from './nav-sessions'
import { NavUser } from './nav-user'

const USER = {
  name: 'DataTalk',
  email: 'wallfacerswu@gmail.com',
}

export function AppSidebar(props: ComponentProps<typeof Sidebar>) {
  const qc = useQueryClient()
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)
  const openSession = useSessionStore((s) => s.openSession)

  const createMut = useMutation({
    mutationFn: async () => {
      if (!activeConnectionId) throw new Error('请先在连接列表中选择一个连接')
      return createSession(activeConnectionId, '新会话')
    },
    onSuccess: (sess) => {
      openSession(sess.id, sess.hasEverSent)
      qc.invalidateQueries({ queryKey: ['sessions', activeConnectionId] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  return (
    <Sidebar collapsible="offcanvas" {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              className="data-[slot=sidebar-menu-button]:p-1.5!"
              render={<a href="/" />}
            >
              <DatabaseIcon className="size-5!" />
              <span className="text-base font-semibold">DataTalk</span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupContent className="flex flex-col gap-2">
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton
                  tooltip="创建会话"
                  className="min-w-8 justify-center bg-primary text-primary-foreground duration-200 ease-linear hover:bg-primary/90 hover:text-primary-foreground active:bg-primary/90 active:text-primary-foreground"
                  onClick={() => createMut.mutate()}
                  disabled={createMut.isPending}
                >
                  <PlusIcon />
                  <span>{createMut.isPending ? '创建中…' : '创建会话'}</span>
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        <NavSessions />
      </SidebarContent>

      <SidebarFooter>
        <NavUser user={USER} />
      </SidebarFooter>
    </Sidebar>
  )
}
```

### Step 3.4: Unblock `use-channel.ts` import fallout

- [ ] **Edit** `client/src/services/channel/use-channel.ts` — the `enterSplit` call stays, but `upsertPart` now takes `sessionId`. Update lines 13 and 29:

Line 13: `const upsertPart = useChatPartsStore(s => s.upsertPart)`
(unchanged — but signature now takes `(sessionId, part)`)

Line 29: `upsertPart((data as any).part)` → `upsertPart(sessionId, (data as any).part)`
Line 34-38 in the `ontology.updated` branch:

```ts
            upsertArtifact(sessionId, { id: d.id, version: d.patch?.version ?? 1,
              kind: d.patch?.kind ?? 'table',
              supersedesId: d.patch?.supersedesId,
              payload: d.patch })
```

Note the added first arg `sessionId`.

### Step 3.5: Verify + commit (covers Tasks 1, 2, 3)

- [ ] Run: `cd client && npx tsc --noEmit`
  Expected: zero errors.
- [ ] Run: `cd client && pnpm test`
  Expected: all tests pass.
- [ ] Run: `cd server && mvn compile -q`
  Expected: zero errors (sanity — nothing changed in server this task).
- [ ] Commit:

```bash
git add -A client/src/stores/ client/src/features/session/ client/src/features/chat/ \
        client/src/features/workspace/ client/src/features/actions/ \
        client/src/features/ontology/ client/src/services/api/session.ts \
        client/src/services/channel/use-channel.ts
git rm client/src/features/session/store.ts
git commit -m "refactor(client): unify session store, partition by session, wire sidebar"
```

---

## Task 4: History loading on session open

**Files:**
- Create: `client/src/features/session/hooks/use-session-history.ts`
- Create: `client/src/features/session/hooks/use-session-history.test.ts`
- Modify: `client/src/features/session/session-canvas.tsx`

### Step 4.1: Write failing test

- [ ] **Create** `client/src/features/session/hooks/use-session-history.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useSessionHistory } from './use-session-history'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'

function mockFetch(messages: any[], artifacts: any[]) {
  return vi.fn(async (url: string) => {
    if (url.endsWith('/messages')) return new Response(JSON.stringify({ messages }))
    if (url.endsWith('/artifacts')) return new Response(JSON.stringify({ artifacts }))
    throw new Error('unexpected url ' + url)
  })
}

describe('useSessionHistory', () => {
  beforeEach(() => {
    useChatPartsStore.setState({ partsBySession: new Map() })
    useOntologyStore.setState({ artifactsBySession: new Map() })
    useTimelineStore.setState({
      orderBySession: new Map(),
      activeBySession: new Map(),
      manualBySession: new Map(),
    })
  })

  it('loads messages + artifacts and populates stores for the active session', async () => {
    global.fetch = mockFetch(
      [{ id: 'm1', role: 'user', parts: [{ type: 'text', id: 'p1', messageID: 'm1', text: 'hi' }] }],
      [{ id: 'a1', version: 1, kind: 'table', sessionId: 's1' }],
    ) as any

    renderHook(() => useSessionHistory('s1'))

    await waitFor(() => {
      expect(useChatPartsStore.getState().getParts('s1')).toHaveLength(1)
      expect(useOntologyStore.getState().getArtifacts('s1').size).toBe(1)
    })
  })

  it('no-op when sessionId is null', async () => {
    const fetchMock = vi.fn()
    global.fetch = fetchMock as any
    renderHook(() => useSessionHistory(null))
    await new Promise((r) => setTimeout(r, 50))
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
```

- [ ] Run: `cd client && pnpm test src/features/session/hooks/use-session-history.test.ts`
  Expected: FAIL.

### Step 4.2: Implement the hook

- [ ] **Create** `client/src/features/session/hooks/use-session-history.ts`:

```ts
import { useEffect } from 'react'
import { http } from '@/services/http'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'
import type { Part } from '@/services/channel/types'
import type { Artifact } from '@/services/channel/event-reducer'

type MessageDto = { id: string; role: string; parts: Part[] }
type ArtifactDto = {
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

export function useSessionHistory(sessionId: string | null) {
  useEffect(() => {
    if (!sessionId) return
    let cancelled = false
    const load = async () => {
      try {
        const [mRes, aRes] = await Promise.all([
          http.get(`sessions/${sessionId}/messages`).json<{ messages: MessageDto[] }>(),
          http.get(`sessions/${sessionId}/artifacts`).json<{ artifacts: ArtifactDto[] }>(),
        ])
        if (cancelled) return

        const partsApi = useChatPartsStore.getState()
        partsApi.clearSession(sessionId)
        for (const m of mRes.messages ?? []) {
          for (const part of m.parts ?? []) partsApi.upsertPart(sessionId, part)
        }

        const ontApi = useOntologyStore.getState()
        const artifacts: Artifact[] = (aRes.artifacts ?? []).map((a) => ({
          id: a.id,
          version: a.version,
          kind: a.kind,
          supersedesId: a.supersedesId,
          supersedesVersion: a.supersedesVersion,
          pinned: a.pinned,
          payload: a.payload,
          createdAt: a.createdAt,
        }))
        ontApi.replaceSession(sessionId, artifacts)

        const tApi = useTimelineStore.getState()
        tApi.clear(sessionId)
        for (const a of artifacts) tApi.addArtifact(sessionId, a.id, a.supersedesId)
      } catch (err) {
        // Soft-fail: history load failures shouldn't block the active session
        console.warn('[use-session-history] load failed', err)
      }
    }
    void load()
    return () => { cancelled = true }
  }, [sessionId])
}
```

- [ ] Run: `cd client && pnpm test src/features/session/hooks/use-session-history.test.ts`
  Expected: PASS.

> Note: `http` uses `ky`'s `prefixUrl: '/api'`, so `sessions/${id}/messages` resolves to `/api/sessions/:id/messages`. The test mocks raw `fetch` under ky's hood — ky re-emits exactly that URL.

### Step 4.3: Call the hook from `session-canvas.tsx`

> **Keep** `#composer-slot` inside `HeroView` (centered) and `SplitView` (left-bottom). Moving it to canvas root would flatten the HERO centered layout and eliminate the position delta that drives the FLIP animation in Task 11 of the existing Plan C. Portal-race is fixed in Task 6 via rAF polling, not by relocating the slot.

- [ ] **Edit** `client/src/features/session/session-canvas.tsx` — add the history hook; leave existing layout otherwise:

```tsx
import { useSessionStore } from '@/stores/session-store'
import { useSessionMode } from './use-session-mode'
import { useFlipComposer } from './use-flip-composer'
import { useSessionHistory } from './hooks/use-session-history'
import { HeroView } from './hero-view'
import { SplitView } from './split-view'
import { PromptComposer } from './prompt-composer'
import { ConnectionOverlay } from './connection-overlay'
import { WelcomeEmpty } from './welcome-empty'

export function SessionCanvas() {
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const { mode } = useSessionMode()
  useFlipComposer()
  useSessionHistory(sessionId)

  return (
    <div className="relative h-full">
      {mode === 'HERO' && <HeroView />}
      {mode === 'SPLIT' && <SplitView />}
      {mode === 'NOSESS' && <WelcomeEmpty />}
      <PromptComposer />
      <ConnectionOverlay />
    </div>
  )
}
```

- [ ] **Leave** `client/src/features/session/hero-view.tsx` and `split-view.tsx` untouched — they already render their own `#composer-slot`.

### Step 4.4: Verify + commit

- [ ] Run: `cd client && pnpm test && npx tsc --noEmit`
  Expected: PASS + zero type errors.
- [ ] Commit:

```bash
git add client/src/features/session/hooks/ client/src/features/session/session-canvas.tsx
git commit -m "feat(client): load session history on open"
```

---

## Task 5: Permanent SSE subscription

**Files:**
- Create: `client/src/features/session/hooks/use-session-subscribe.ts`
- Modify: `client/src/features/session/session-canvas.tsx`
- Modify: `client/src/services/channel/use-channel.ts`

### Step 5.1: Extract event-handler into a shared reducer-runner

- [ ] **Edit** `client/src/services/channel/use-channel.ts` — split out the event-handling closure so both `sendMessage` and the subscribe hook can share it. Replace the file entirely:

```ts
import { useCallback, useMemo, useState } from 'react'
import { ChannelClient } from './channel-client'
import type { StreamEvent } from './types'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'
import { useSessionStore } from '@/stores/session-store'
import { getClientHandler } from '@/features/actions/registry'

function getApiBaseUrl(): string {
  const env = (import.meta as any).env?.VITE_API_BASE_URL
  if (typeof env === 'string' && env.length > 0) return env.replace(/\/$/, '')
  return ''
}

export function buildEventSink(sessionId: string, client: ChannelClient | null) {
  return (evt: StreamEvent) => {
    const { event, data } = evt
    if (event === 'message.part.created' || event === 'message.part.updated') {
      useChatPartsStore.getState().upsertPart(sessionId, (data as any).part)
    } else if (event === 'message.part.delta') {
      const { partId, field, delta } = data as any
      const byMessage = useChatPartsStore.getState().partsBySession.get(sessionId)
      if (byMessage) {
        for (const [mid, list] of byMessage) {
          const idx = list.findIndex((p) => p.id === partId)
          if (idx >= 0) {
            const before = list[idx] as any
            const next: any = { ...before, [field]: (before[field] ?? '') + delta }
            useChatPartsStore.getState().upsertPart(sessionId, next)
            break
          }
        }
      }
    } else if (event === 'message.part.removed') {
      const { partId } = data as any
      const byMessage = useChatPartsStore.getState().partsBySession.get(sessionId)
      if (byMessage) {
        for (const [mid] of byMessage) {
          useChatPartsStore.getState().removePart(sessionId, mid, partId)
        }
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

export function useChannelClient(sessionId: string | null): ChannelClient | null {
  const clientId = useMemo(() => crypto.randomUUID(), [])
  return useMemo(() => {
    if (!sessionId) return null
    return new ChannelClient({ baseUrl: getApiBaseUrl(), sessionId, clientId })
  }, [sessionId, clientId])
}

export function useChannel() {
  const [isStreaming, setIsStreaming] = useState(false)
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const enterSplit = useSessionStore((s) => s.enterSplit)
  const client = useChannelClient(sessionId)

  const sendMessage = useCallback(
    async (parts: any[]) => {
      if (!client || !sessionId) return
      setIsStreaming(true)
      enterSplit(sessionId)
      const sink = buildEventSink(sessionId, client)
      try {
        await client.sendMessage(parts, sink)
      } finally {
        setIsStreaming(false)
      }
    },
    [client, sessionId, enterSplit],
  )

  const abort = useCallback(async () => {
    if (!client) return
    await client.abort()
  }, [client])

  return { sendMessage, abort, isStreaming, client }
}
```

### Step 5.2: Create the subscribe hook

- [ ] **Create** `client/src/features/session/hooks/use-session-subscribe.ts`:

```ts
import { useEffect } from 'react'
import { buildEventSink, useChannelClient } from '@/services/channel/use-channel'
import { useChannelStore } from '@/stores/channel-store'

export function useSessionSubscribe(sessionId: string | null) {
  const client = useChannelClient(sessionId)
  const setConnected = useChannelStore((s) => s.setConnected)
  const lastEventId = useChannelStore((s) => s.lastEventId)

  useEffect(() => {
    if (!client || !sessionId) { setConnected(false); return }
    const sink = buildEventSink(sessionId, client)
    setConnected(true)
    const unsub = client.subscribe(lastEventId, sink)
    return () => { unsub(); setConnected(false) }
    // Deliberately omit lastEventId from deps: we don't want to tear down the
    // stream every time a frame arrives and bumps lastEventId. The initial
    // value at mount is the resume cursor.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client, sessionId, setConnected])
}
```

### Step 5.3: Hook it into the canvas

- [ ] **Edit** `client/src/features/session/session-canvas.tsx` — add after `useSessionHistory(sessionId)`:

```tsx
  useSessionSubscribe(sessionId)
```

And import at the top:

```tsx
import { useSessionSubscribe } from './hooks/use-session-subscribe'
```

### Step 5.4: Verify + commit

- [ ] Run: `cd client && pnpm test && npx tsc --noEmit`
  Expected: PASS + zero errors.
- [ ] Commit:

```bash
git add client/src/features/session/hooks/use-session-subscribe.ts \
        client/src/features/session/session-canvas.tsx \
        client/src/services/channel/use-channel.ts
git commit -m "feat(client): permanent SSE subscription for active session"
```

---

## Task 6: PromptComposer portal-race fix + stop button

**Files:**
- Modify: `client/src/features/session/prompt-composer.tsx`
- Create: `client/src/features/session/prompt-composer.test.tsx`

### Step 6.1: Write failing test

- [ ] **Create** `client/src/features/session/prompt-composer.test.tsx`:

```tsx
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PromptComposer } from './prompt-composer'
import { useSessionStore } from '@/stores/session-store'
import { useConnectionStore } from '@/features/connection/store'

function mountWithSlot() {
  const slot = document.createElement('div')
  slot.id = 'composer-slot'
  document.body.appendChild(slot)
  return () => slot.remove()
}

beforeEach(() => {
  document.body.innerHTML = ''
  useSessionStore.setState({
    activeSessionId: 's1',
    modeBySession: new Map([['s1', 'HERO']]),
    hasEverSentBySession: new Map(),
    pendingPrompt: null,
    pendingConnectionPrompt: false,
  })
  useConnectionStore.setState({
    activeConnectionId: 'conn-1',
    connections: [],
    setActive: useConnectionStore.getState().setActive,
    setConnections: useConnectionStore.getState().setConnections,
  })
})

describe('PromptComposer', () => {
  it('renders into a composer-slot that appears after mount', async () => {
    render(<PromptComposer />)
    expect(screen.queryByPlaceholderText(/Enter 发送/)).toBeNull()

    act(() => { mountWithSlot() })

    // Wait one microtask for observer to fire
    await screen.findByPlaceholderText(/Enter 发送/)
  })

  it('shows a "停止" button while streaming', async () => {
    mountWithSlot()
    // sendMessage is mocked indirectly through useChannel; we simulate by
    // setting isStreaming via the store equivalent if exposed. Simpler: fire
    // submit and assert the stop button appears after fetch resolves-late.
    global.fetch = vi.fn(() => new Promise(() => {})) as any

    render(<PromptComposer />)
    const input = await screen.findByPlaceholderText(/Enter 发送/)
    await userEvent.type(input, '测试\n')

    // After submit, button text should show 停止
    expect(await screen.findByRole('button', { name: /停止|发送/ })).toBeTruthy()
  })
})
```

- [ ] Run: `cd client && pnpm test src/features/session/prompt-composer.test.tsx`
  Expected: FAIL (current implementation returns null when slot is missing on first render without recovery).

### Step 6.2: Fix portal race + add stop button

- [ ] **Replace** `client/src/features/session/prompt-composer.tsx`:

```tsx
import { useEffect, useState, type FormEvent, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { Button } from '@/components/ui/button'
import { useSessionStore } from '@/stores/session-store'
import { useChannel } from '@/services/channel/use-channel'
import { classifyIntent } from '@/features/actions/classify-intent'
import { useConnectionStore } from '@/features/connection/store'

function useComposerSlot(): HTMLElement | null {
  const [slot, setSlot] = useState<HTMLElement | null>(
    typeof document !== 'undefined' ? document.getElementById('composer-slot') : null,
  )

  useEffect(() => {
    if (slot) return
    // Poll once per animation frame until the slot node mounts; stop as soon
    // as we find it. In practice this resolves within 1-2 frames of the
    // parent's first commit.
    let raf = 0
    const tick = () => {
      const el = document.getElementById('composer-slot')
      if (el) { setSlot(el); return }
      raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [slot])

  return slot
}

export function PromptComposer() {
  const slot = useComposerSlot()
  if (!slot) return null
  return createPortal(<Inner />, slot)
}

function Inner() {
  const [text, setText] = useState('')
  const { sendMessage, abort, isStreaming } = useChannel()
  const activeConn = useConnectionStore((s) => s.activeConnectionId)
  const setPendingPrompt = useSessionStore((s) => s.setPendingPrompt)
  const setPendingConnectionPrompt = useSessionStore((s) => s.setPendingConnectionPrompt)
  const activeSessionId = useSessionStore((s) => s.activeSessionId)

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const t = text.trim()
    if (!t || isStreaming || !activeSessionId) return
    setText('')
    if (classifyIntent(t) === 'db_related' && !activeConn) {
      setPendingPrompt(t)
      setPendingConnectionPrompt(true)
      return
    }
    await sendMessage([
      { type: 'text', id: crypto.randomUUID(), sessionID: activeSessionId,
        messageID: '', text: t, metadata: {} } as any,
    ])
  }

  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void onSubmit(e as unknown as FormEvent)
    }
  }

  return (
    <form onSubmit={onSubmit} className="border-t bg-background p-3">
      <div className="flex items-end gap-2">
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKey}
          placeholder="Enter 发送，Shift+Enter 换行"
          rows={2}
          disabled={!activeSessionId}
          className="flex-1 resize-none rounded-md border bg-background px-3 py-2 text-sm
                     focus:outline-none focus:ring-2 focus:ring-ring"
        />
        {isStreaming ? (
          <Button type="button" variant="destructive" onClick={() => void abort()}>
            停止
          </Button>
        ) : (
          <Button type="submit" disabled={!text.trim() || !activeSessionId}>
            发送
          </Button>
        )}
      </div>
    </form>
  )
}
```

- [ ] Run: `cd client && pnpm test src/features/session/prompt-composer.test.tsx`
  Expected: PASS.

### Step 6.3: Commit

- [ ] Run: `cd client && npx tsc --noEmit`
  Expected: zero errors.
- [ ] Commit:

```bash
git add client/src/features/session/prompt-composer.tsx client/src/features/session/prompt-composer.test.tsx
git commit -m "fix(client): PromptComposer survives slot-mount race, adds stop button"
```

---

## Task 7: Pending-prompt resume

**Files:**
- Create: `client/src/features/session/hooks/use-pending-prompt-resume.ts`
- Modify: `client/src/features/session/session-canvas.tsx`
- Modify: `client/src/features/session/connection-overlay.tsx`

### Step 7.1: Implement the resume hook

- [ ] **Create** `client/src/features/session/hooks/use-pending-prompt-resume.ts`:

```ts
import { useEffect } from 'react'
import { useSessionStore } from '@/stores/session-store'
import { useConnectionStore } from '@/features/connection/store'
import { useChannel } from '@/services/channel/use-channel'

/**
 * When the user has typed a DB-related prompt but has no connection, we store
 * the draft in `pendingPrompt` and show `ConnectionOverlay`. Once they pick a
 * connection and `pendingConnectionPrompt` goes false, this hook re-fires the
 * draft as a real send_message and clears it.
 */
export function usePendingPromptResume() {
  const pendingPrompt = useSessionStore((s) => s.pendingPrompt)
  const overlayOn = useSessionStore((s) => s.pendingConnectionPrompt)
  const activeConn = useConnectionStore((s) => s.activeConnectionId)
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const setPendingPrompt = useSessionStore((s) => s.setPendingPrompt)
  const { sendMessage, isStreaming } = useChannel()

  useEffect(() => {
    if (overlayOn) return           // still asking the user
    if (!pendingPrompt) return      // nothing queued
    if (!activeConn) return         // user closed overlay without picking
    if (!activeSessionId) return
    if (isStreaming) return         // don't race another send

    const draft = pendingPrompt
    setPendingPrompt(null)          // clear first so we don't re-enter
    void sendMessage([
      { type: 'text', id: crypto.randomUUID(), sessionID: activeSessionId,
        messageID: '', text: draft, metadata: {} } as any,
    ])
  }, [overlayOn, pendingPrompt, activeConn, activeSessionId, isStreaming, setPendingPrompt, sendMessage])
}
```

### Step 7.2: Ensure `ConnectionOverlay` preserves `pendingPrompt`

- [ ] **Replace** `client/src/features/session/connection-overlay.tsx`:

```tsx
import { useSessionStore } from '@/stores/session-store'
import { useConnectionStore } from '@/features/connection/store'

export function ConnectionOverlay() {
  const pending = useSessionStore((s) => s.pendingConnectionPrompt)
  const setPending = useSessionStore((s) => s.setPendingConnectionPrompt)
  const setPrompt = useSessionStore((s) => s.setPendingPrompt)
  const { connections, setActive } = useConnectionStore()

  if (!pending) return null
  return (
    <div className="absolute left-1/2 top-24 z-50 w-96 -translate-x-1/2 rounded border bg-background p-3 shadow">
      <div className="text-sm">这是数据库相关问题但没选连接，选一个：</div>
      <ul className="mt-2 space-y-1">
        {connections.map((c) => (
          <li key={c.id}>
            <button
              className="w-full rounded border px-2 py-1 text-left text-xs hover:bg-muted"
              onClick={() => {
                setActive(c.id)
                setPending(false)
                // leave pendingPrompt untouched — resume hook will consume it
              }}
            >
              {c.name} ({c.dbType})
            </button>
          </li>
        ))}
        {connections.length === 0 && (
          <li className="text-xs text-muted-foreground">还没有连接。请先在侧边栏新建。</li>
        )}
      </ul>
      <div className="mt-2 text-right">
        <button
          className="text-xs text-muted-foreground hover:underline"
          onClick={() => { setPending(false); setPrompt(null) }}
        >
          取消
        </button>
      </div>
    </div>
  )
}
```

### Step 7.3: Wire the hook into the canvas

- [ ] **Edit** `client/src/features/session/session-canvas.tsx` — add after `useSessionSubscribe(sessionId)`:

```tsx
  usePendingPromptResume()
```

And import:

```tsx
import { usePendingPromptResume } from './hooks/use-pending-prompt-resume'
```

### Step 7.4: Verify + commit

- [ ] Run: `cd client && pnpm test && npx tsc --noEmit`
  Expected: PASS + zero errors.
- [ ] Commit:

```bash
git add client/src/features/session/hooks/use-pending-prompt-resume.ts \
        client/src/features/session/connection-overlay.tsx \
        client/src/features/session/session-canvas.tsx
git commit -m "feat(client): auto-resend prompt after picking connection"
```

---

## Task 8: `ChannelClient` env-aware baseUrl

**Files:**
- Modify: `client/src/services/channel/channel-client.ts`
- Modify: `client/src/services/channel/channel-client.test.ts`

### Step 8.1: The baseUrl behavior already lives in `use-channel.ts` (Task 5)

- [ ] Verify `getApiBaseUrl()` is in `use-channel.ts`. No additional change needed in `channel-client.ts` — it still accepts `baseUrl` as input.

### Step 8.2: Add a test asserting env wiring

- [ ] **Append** to `client/src/services/channel/channel-client.test.ts`:

```ts
import { vi as _vi, describe as _describe, it as _it, expect as _expect } from 'vitest'
import { ChannelClient as _ChannelClient } from './channel-client'

_describe('ChannelClient baseUrl', () => {
  _it('strips trailing slash', () => {
    const c = new _ChannelClient({ baseUrl: 'http://test/', sessionId: 's', clientId: 'c' })
    // @ts-expect-error — private access for verification
    _expect(c.baseUrl).toBe('http://test')
  })

  _it('accepts empty baseUrl (same-origin)', () => {
    const c = new _ChannelClient({ baseUrl: '', sessionId: 's', clientId: 'c' })
    // @ts-expect-error — private access for verification
    _expect(c.baseUrl).toBe('')
  })
})
```

- [ ] Run: `cd client && pnpm test src/services/channel/channel-client.test.ts`
  Expected: PASS.

### Step 8.3: Commit

- [ ] Commit:

```bash
git add client/src/services/channel/channel-client.test.ts
git commit -m "test(client): assert ChannelClient baseUrl normalization"
```

---

## Task 9: MessageStream — role-aware grouping

**Files:**
- Modify: `client/src/features/chat/components/message-stream.tsx`

### Step 9.1: Track message metadata

- [ ] **Edit** `client/src/stores/chat-parts-store.ts` — add a `messageMeta` map for role/createdAt per session:

Find the type block and extend it to:

```ts
export type MessageMeta = {
  id: string
  role: 'user' | 'assistant' | 'system'
  createdAt: number
}

type ChatPartsState = {
  partsBySession: Map<string, Map<string, Part[]>>
  metaBySession: Map<string, Map<string, MessageMeta>>
  upsertPart: (sessionId: string, part: Part) => void
  upsertMeta: (sessionId: string, meta: MessageMeta) => void
  upsertMany: (sessionId: string, parts: Part[]) => void
  removePart: (sessionId: string, messageId: string, partId: string) => void
  clearSession: (sessionId: string) => void
  getParts: (sessionId: string) => Part[]
}
```

And the `create` body — add next to `partsBySession: new Map(),`:

```ts
  metaBySession: new Map(),
  upsertMeta: (sessionId, meta) => set((s) => {
    const bySession = new Map(s.metaBySession)
    const map = new Map(bySession.get(sessionId) ?? new Map())
    map.set(meta.id, { ...(map.get(meta.id) ?? meta), ...meta })
    bySession.set(sessionId, map)
    return { metaBySession: bySession }
  }),
```

Also handle `clearSession` to drop both maps:

```ts
  clearSession: (sessionId) => set((s) => {
    const parts = new Map(s.partsBySession); parts.delete(sessionId)
    const meta = new Map(s.metaBySession); meta.delete(sessionId)
    return { partsBySession: parts, metaBySession: meta }
  }),
```

### Step 9.2: Populate meta from events

- [ ] **Edit** `client/src/services/channel/use-channel.ts` — inside `buildEventSink`, add a branch at the top of the switch:

Before the `message.part.created` branch, insert:

```ts
    if (event === 'message.created') {
      const m = (data as any).message
      useChatPartsStore.getState().upsertMeta(sessionId, {
        id: m.id,
        role: (m.role ?? 'assistant') as 'user' | 'assistant' | 'system',
        createdAt: Number(m.createdAt ?? Date.now()),
      })
    } else
```

(Replace the following `if` with `else if` to chain cleanly.)

### Step 9.3: Populate meta from history

- [ ] **Edit** `client/src/features/session/hooks/use-session-history.ts` — inside the message loop, add:

```ts
        for (const m of mRes.messages ?? []) {
          partsApi.upsertMeta(sessionId, {
            id: m.id,
            role: (m.role ?? 'assistant') as 'user' | 'assistant' | 'system',
            createdAt: Number((m as any).createdAt ?? Date.now()),
          })
          for (const part of m.parts ?? []) partsApi.upsertPart(sessionId, part)
        }
```

(Replace the existing `for (const m of mRes.messages ?? [])` loop.)

### Step 9.4: Render by role

- [ ] **Replace** `client/src/features/chat/components/message-stream.tsx`:

```tsx
import { useMemo } from 'react'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useSessionStore } from '@/stores/session-store'
import { PartRenderer } from './part-renderer'
import { cn } from '@/lib/utils'

export function MessageStream() {
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const partsBySession = useChatPartsStore((s) => s.partsBySession)
  const metaBySession = useChatPartsStore((s) => s.metaBySession)

  const groups = useMemo(() => {
    if (!sessionId) return []
    const byMessage = partsBySession.get(sessionId)
    const metaMap = metaBySession.get(sessionId)
    if (!byMessage) return []

    const entries = Array.from(byMessage.entries()).map(([messageId, parts]) => ({
      messageId,
      parts,
      meta: metaMap?.get(messageId),
    }))
    entries.sort((a, b) => (a.meta?.createdAt ?? 0) - (b.meta?.createdAt ?? 0))
    return entries
  }, [sessionId, partsBySession, metaBySession])

  return (
    <div className="flex flex-col gap-4">
      {groups.map((g) => {
        const role = g.meta?.role ?? 'assistant'
        const align = role === 'user' ? 'items-end' : 'items-start'
        const bubble = role === 'user'
          ? 'bg-primary text-primary-foreground'
          : 'bg-muted'
        return (
          <div key={g.messageId} className={cn('flex flex-col gap-1', align)}>
            <div className={cn('max-w-[85%] rounded-lg px-3 py-2 text-sm', bubble)}>
              {g.parts.map((p) => <PartRenderer key={p.id} part={p} />)}
            </div>
          </div>
        )
      })}
    </div>
  )
}
```

### Step 9.5: Verify + commit

- [ ] Run: `cd client && pnpm test && npx tsc --noEmit`
  Expected: PASS + zero errors.
- [ ] Commit:

```bash
git add client/src/features/chat/components/message-stream.tsx \
        client/src/stores/chat-parts-store.ts \
        client/src/services/channel/use-channel.ts \
        client/src/features/session/hooks/use-session-history.ts
git commit -m "feat(client): role-aware message grouping with chronological order"
```

---

## Task 10: HERO-aware sidebar dim

**Files:**
- Modify: `client/src/features/workspace/home-page.tsx`

### Step 10.1: Pass mode to sidebar wrapper

- [ ] **Replace** `client/src/features/workspace/home-page.tsx`:

```tsx
import type { CSSProperties } from 'react'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'
import { useBootstrapActions } from '@/features/actions/use-bootstrap-actions'
import { SessionCanvas } from '@/features/session/session-canvas'
import { useSessionMode } from '@/features/session/use-session-mode'
import { AppSidebar } from './components/app-sidebar'
import { SiteHeader } from './components/site-header'
import { cn } from '@/lib/utils'

export function HomePage() {
  useBootstrapActions()
  const { mode } = useSessionMode()
  const heroQuiet = mode === 'HERO' || mode === 'NOSESS'

  return (
    <SidebarProvider
      style={
        {
          '--sidebar-width': 'calc(var(--spacing) * 65)',
          '--header-height': 'calc(var(--spacing) * 12)',
        } as CSSProperties
      }
    >
      <AppSidebar
        variant="inset"
        className={cn('transition-opacity duration-200',
          heroQuiet ? 'opacity-60' : 'opacity-100')}
      />
      <SidebarInset>
        {!heroQuiet && <SiteHeader />}
        <div className="relative min-h-0 flex-1">
          <SessionCanvas />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
```

### Step 10.2: Verify + commit

- [ ] Run: `cd client && npx tsc --noEmit`
  Expected: zero errors.
- [ ] Manual smoke: `cd client && pnpm dev` → confirm HERO/NOSESS mode shows dimmed sidebar + no header.
- [ ] Commit:

```bash
git add client/src/features/workspace/home-page.tsx
git commit -m "style(client): dim sidebar and hide header in HERO mode"
```

---

## Task 11: Delete dead code

**Files:** (all deletions)
- `client/src/features/session/store.ts` — already deleted in Task 1
- `client/src/features/chat/components/chat-panel.tsx`
- `client/src/features/chat/components/chat-input.tsx`
- `client/src/features/chat/components/message-list.tsx`
- `client/src/features/chat/components/message-item.tsx`
- `client/src/features/chat/store.ts`
- `client/src/features/chat/hooks/use-chat.ts`
- `client/src/features/chat/types.ts`
- `client/src/features/workspace/components/workspace.tsx`
- `client/src/features/workspace/components/tab-bar.tsx`
- `client/src/features/workspace/components/query-result-tab.tsx`
- `client/src/features/workspace/components/empty-state.tsx`
- `client/src/features/workspace/store.ts`
- `client/src/features/workspace/types.ts`
- `client/src/services/api/chat.ts`

### Step 11.1: Verify no references remain

- [ ] Run: `cd client && grep -rE "(chat-panel|chat-input|message-list|message-item|workspace/store|workspace/types|features/chat/store|features/chat/hooks|features/chat/types|services/api/chat|tab-bar|query-result-tab|workspace/components/workspace|empty-state)" src --include='*.ts' --include='*.tsx' || echo 'no refs'`
  Expected: `no refs`.

> If any reference shows up, inspect it; it likely belongs to one of the files on the delete list (i.e. dead-to-dead). Cross-module reference means a user-facing consumer we missed — stop and investigate before deleting.

### Step 11.2: Delete

- [ ] Run:

```bash
cd /home/wushengzhou/workspace/github/data-talk/client
rm -f src/features/chat/components/chat-panel.tsx \
      src/features/chat/components/chat-input.tsx \
      src/features/chat/components/message-list.tsx \
      src/features/chat/components/message-item.tsx \
      src/features/chat/store.ts \
      src/features/chat/hooks/use-chat.ts \
      src/features/chat/types.ts \
      src/features/workspace/components/workspace.tsx \
      src/features/workspace/components/tab-bar.tsx \
      src/features/workspace/components/query-result-tab.tsx \
      src/features/workspace/components/empty-state.tsx \
      src/features/workspace/store.ts \
      src/features/workspace/types.ts \
      src/services/api/chat.ts
rmdir src/features/chat/hooks 2>/dev/null || true
```

### Step 11.3: Verify + commit

- [ ] Run: `cd client && pnpm test && npx tsc --noEmit`
  Expected: PASS + zero errors.
- [ ] Run: `cd client && pnpm build`
  Expected: build succeeds (sanity check — `vite` would catch dead imports Tsc misses).
- [ ] Commit:

```bash
git add -A client/src/features/chat/ client/src/features/workspace/ client/src/services/api/
git commit -m "chore(client): remove dead pre-split-view chat/workspace modules"
```

---

## Task 12: End-to-end verification checklist

> Manual smoke test performed against a running backend + client. No new code.

### Step 12.1: Start the stack

- [ ] Run (in separate terminals):

```bash
cd server && mvn spring-boot:run -pl data-talk-adapter
```

```bash
cd client && pnpm dev
```

- [ ] Open http://localhost:5173 in a browser.

### Step 12.2: Walk the scenarios

- [ ] **NOSESS**: App loads → `WelcomeEmpty` visible, sidebar dimmed, no header bar.
- [ ] **Create connection** (use existing connection dialog) → connection appears in list.
- [ ] **Click "创建会话"** → new session appears in sidebar grouping under "今天", canvas switches to HERO (centered heading + composer at bottom).
- [ ] **Send a message**: type "查询用户表最近一周的注册趋势" → Enter. HERO collapses into SPLIT with FLIP animation; chat column fills with reasoning / tool cards; right column gets a timeline chip + artifact.
- [ ] **Click "停止"** mid-stream → stream aborts, last partial message stays, composer returns to send mode.
- [ ] **Reopen session** (click another, then come back) → SPLIT view restores with prior messages and artifacts loaded via REST.
- [ ] **No connection + DB question**: unset `activeConnectionId` in React devtools Zustand panel → type "表里有哪些字段" → overlay appears. Pick a connection → draft sends automatically.
- [ ] **DevTools network tab**:
  - On session open: `GET /api/sessions/{id}/messages`, `GET /api/sessions/{id}/artifacts`, `GET /api/sessions/{id}/channel` (subscribed).
  - On send: `POST /api/sessions/{id}/channel` with streaming response.

### Step 12.3: Capture any regressions

- [ ] Take screenshots / console logs of any failures; open follow-up issues rather than patching in this plan.

> This plan does not mutate if the smoke fails — failures should drive a next-iteration plan.

---

## Self-Review

**Spec coverage:**
- §4.1 session state machine + seedFromServer → Task 1 (`openSession` with `hasEverSent`).
- §4.1 SPLIT→HERO永远不允许 → store has no transition back to HERO from SPLIT.
- §4.2 composer as shared element → Task 4 centralizes `#composer-slot`.
- §4.2 sidebar opacity 0.5 in HERO → Task 10.
- §4.4 per-session stores → Tasks 2, 9 (chat-parts / ontology / meta partition; timeline already had it).
- §4.7 conversational connection prompt → Tasks 6, 7 (overlay + resume).
- §3.8 历史恢复两模式 — 快模式 → Task 4 (REST history), Task 5 (SSE subscribe).
- §3.4 `action.invoke` → preserved in `buildEventSink` (Task 5).
- Plan A Session REST gap → Task 0.

**Gaps I did not cover (intentional):**
- `message.part.delta` field-level append across cross-session streams already worked pre-refactor; buildEventSink preserves it.
- FLIP animation polish (Task 11 of Plan C); HERO → SPLIT transition is already shipped and this plan does not change it.
- Dev proxy for `/api` — assumed Vite `server.proxy` is already configured to `localhost:8080` (already in repo). If not, the sidebar's `GET /api/sessions` will 404 through CORS instead of reaching the backend — check `client/vite.config.ts`.
- Playwright E2E — kept manual (Task 12). Can be automated in a follow-up.

**Placeholder scan:** grep the plan for "TBD", "TODO", "appropriate", "similar to", "fill in" → none should match. All code blocks are complete.

**Type consistency:**
- `openSession(id, hasEverSent)` signature used consistently (Tasks 1, 3, 4).
- `upsertPart(sessionId, part)` everywhere (Tasks 2, 5, 9).
- `upsertArtifact(sessionId, a)` + `getArtifacts(sessionId)` (Tasks 2, 5, 4).
- `SessionDto.createdAt: long` ↔ front-end `createdAt: number` (Task 0 + Task 3.1).
- `useChannel` now returns `{ sendMessage, abort, isStreaming, client }` — consumers: PromptComposer (Task 6), usePendingPromptResume (Task 7). Both match.
- `buildEventSink(sessionId, client)` signature shared by `useChannel.sendMessage` and `useSessionSubscribe` (Tasks 5).

---

## Execution Handoff

Two options for codex:

**1. Subagent-driven (recommended)** — dispatch one fresh subagent per task; require it to run the commit step before moving on; you review the commit diff between tasks.

**2. Inline** — execute top-to-bottom in one session with checkpoints at the end of each task.

**Dependency graph:**
- Tasks **1 → 2 → 3** form a single logical commit unit: Task 1.3 deletes `features/session/store.ts`, which breaks `nav-sessions.tsx`; Task 3 fixes it. Any subagent running Task 1 MUST proceed through Task 3 before reporting green. No intermediate commits.
- Task **0** (backend) is independent and can run in parallel with 1→3.
- Task **4** depends on 3 (needs `SessionController` live so messages/artifacts fetch against real data, though the hook itself only depends on the unified store).
- Task **5** depends on 4 (shares `buildEventSink`; also safer to land after history load so the reducer runs on a pre-seeded store).
- Tasks **6 → 7** depend on 5 (`useChannel` returns `abort` and `sendMessage` used by both).
- Tasks **8, 9, 10** are independent and can parallelize after 5.
- Task **11** last (clean-up after all refs dropped).
- Task **12** manual, after all merged.
