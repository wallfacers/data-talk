# Tech Debt Batch — Session Events, POST Timeout Config & Multi-Session SSE Pool

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 一次性清除 7 项已验证仍存在的技术债：TD-001（H2 语义）、TD-013（POST 超时可配置）、TD-014/015/016/017（前端未消费 session 事件）、TD-MULTI-SESSION-SSE-POOL（多 session SSE 订阅池）。

**Architecture:** Task 1–3 互相独立，均为最小改动（单文件或双文件）；Task 4 在前端新增 `useBackgroundSessionSubscribe` hook + `BackgroundSubscriber` 组件，在 `SessionCanvas` 渲染背景订阅者，生命周期由 `streamingBySession` 驱动（streaming 结束 → 组件卸载 → SSE 自动关闭）。

**Tech Stack:** React 19 / TypeScript / Zustand / Vitest · Spring Boot 3.5 / Java 21

**依赖关系:** Task 1–3 完全并行；Task 4 独立，不依赖 Task 1–3。Task 5（文档）在所有任务完成后执行。

---

## 文件结构地图

### Task 1 — 前端 session 事件处理（TD-014/015/016/017）
- Modify: `client/src/services/channel/use-channel.ts`
- Modify: `client/src/services/channel/use-channel.test.ts`

### Task 2 — POST 流超时可配置（TD-013）
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/ChannelController.java`
- Modify: `server/data-talk-adapter/src/main/resources/application.yml`

### Task 3 — H2 demo 数据源语义明确化（TD-001）
- Modify: `server/data-talk-adapter/src/main/resources/application.yml`

### Task 4 — 多 session SSE 订阅池（TD-MULTI-SESSION-SSE-POOL）
- Create: `client/src/features/session/hooks/use-background-session-subscribe.ts`
- Create: `client/src/features/session/hooks/__tests__/use-background-session-subscribe.test.tsx`
- Modify: `client/src/features/session/session-canvas.tsx`

### Task 5 — 文档收口
- Modify: `docs/exec-plans/tech-debt-tracker.md`
- Modify: `docs/references/opencode-protocol.md`
- Modify: `docs/exec-plans/index.md`

---

## Task 1: 前端 session 事件处理（TD-014/015/016/017）

**Files:**
- Modify: `client/src/services/channel/use-channel.ts`
- Modify: `client/src/services/channel/use-channel.test.ts`

当前 `buildEventSink` 没有处理 `session.error`、`session.created`、`session.deleted`、`session.compacted`、`session.diff` 五个事件。服务端已正确透传，前端只需加分支。

- [x] **Step 1.1: 在 `use-channel.test.ts` 末尾追加四组失败测试**

```ts
describe('buildEventSink → session.error (TD-014)', () => {
  beforeEach(() => {
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(['ses_a']),
    })
  })

  it('clears streaming flag on session.error', () => {
    const qc = new QueryClient()
    const sink = buildEventSink('ses_a', null, qc, null)
    sink({ event: 'session.error', data: { error: 'model unavailable' } } as any)
    expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(false)
  })
})

describe('buildEventSink → session.created / session.deleted (TD-015)', () => {
  it('invalidates sessions cache on session.created', () => {
    const qc = new QueryClient()
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const sink = buildEventSink('s1', null, qc, 'c1')
    sink({ event: 'session.created', data: { sessionId: 's2', title: 'new', version: 1 } } as any)
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['sessions', 'c1'] })
  })

  it('invalidates sessions cache on session.deleted', () => {
    const qc = new QueryClient()
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const sink = buildEventSink('s1', null, qc, 'c1')
    sink({ event: 'session.deleted', data: { sessionId: 's1' } } as any)
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['sessions', 'c1'] })
  })
})

describe('buildEventSink → session.compacted (TD-016)', () => {
  it('does not throw on session.compacted event', () => {
    const qc = new QueryClient()
    const sink = buildEventSink('s1', null, qc, null)
    expect(() =>
      sink({ event: 'session.compacted', data: { sessionId: 's1' } } as any)
    ).not.toThrow()
  })
})

describe('buildEventSink → session.diff (TD-017)', () => {
  it('does not throw on session.diff event with unknown payload', () => {
    const qc = new QueryClient()
    const sink = buildEventSink('s1', null, qc, null)
    expect(() =>
      sink({ event: 'session.diff', data: { sessionId: 's1', payload: { unknown: true } } } as any)
    ).not.toThrow()
  })
})
```

- [x] **Step 1.2: 运行测试确认全部失败**

```bash
cd client && npx vitest run src/services/channel/use-channel.test.ts
```

期望：4 组新测试报 FAIL（`session.error` 不清 streaming；`invalidateQueries` 未调用；等等）。

- [x] **Step 1.3: 在 `use-channel.ts` 的 `buildEventSink` 中增加四个事件分支**

在文件顶部的 import 区，补充 `toast` 导入（紧接已有的 `showErrorToast` import 行）：

```ts
import { toast } from 'sonner'
```

然后在 `buildEventSink` 的 `} else if (event === 'action.invoke' && client) {` 块**之前**插入以下四个分支：

```ts
    } else if (event === 'session.error') {
      // session-level error (e.g. provider auth failure, model unavailable)
      // treat as turn-done: clear spinner, surface error to user
      const { error } = data as { error?: string }
      useChatPartsStore.getState().markSessionTurnCompleted(sessionId)
      useChatPartsStore.getState().setStreaming(sessionId, false)
      showErrorToast(normalizeError(new Error(error ?? 'Session error')))
    } else if (event === 'session.created' || event === 'session.deleted') {
      // another client created or deleted a session — refresh the list
      queryClient.invalidateQueries({ queryKey: ['sessions', connectionId ?? null] })
    } else if (event === 'session.compacted') {
      // OpenCode compacted the context; inform user via non-blocking toast
      toast.info('AI 上下文已压缩，早期消息可能不再可用')
    } else if (event === 'session.diff') {
      // payload semantics undocumented in OpenCode 1.4.7 — safely ignored
    }
```

完整位置示例（紧接 `ontology.updated` 块之后，`action.invoke` 块之前）：

```ts
    } else if (event === 'ontology.updated') {
      // ... (existing code unchanged)
    } else if (event === 'session.error') {
      const { error } = data as { error?: string }
      useChatPartsStore.getState().markSessionTurnCompleted(sessionId)
      useChatPartsStore.getState().setStreaming(sessionId, false)
      showErrorToast(normalizeError(new Error(error ?? 'Session error')))
    } else if (event === 'session.created' || event === 'session.deleted') {
      queryClient.invalidateQueries({ queryKey: ['sessions', connectionId ?? null] })
    } else if (event === 'session.compacted') {
      toast.info('AI 上下文已压缩，早期消息可能不再可用')
    } else if (event === 'session.diff') {
      // payload semantics undocumented in OpenCode 1.4.7 — safely ignored
    } else if (event === 'action.invoke' && client) {
      // ... (existing code unchanged)
    }
```

- [x] **Step 1.4: 运行测试确认全部通过**

```bash
cd client && npx vitest run src/services/channel/use-channel.test.ts
```

期望：所有测试 PASS（含既有测试 + 4 组新测试）。

- [x] **Step 1.5: 类型检查**

```bash
cd client && npx tsc --noEmit
```

期望：0 errors。

---

## Task 2: POST 流超时可配置（TD-013）

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/ChannelController.java`
- Modify: `server/data-talk-adapter/src/main/resources/application.yml`

当前两个相关常量均为硬编码 `static final`：
```java
private static final long POST_STREAM_TIMEOUT_MS = 10L * 60_000L;
private static final long TURN_WAIT_TIMEOUT_MS   = POST_STREAM_TIMEOUT_MS;
```

目标：将二者统一替换为构造器注入的实例字段，通过 `@Value` 读取配置。

- [x] **Step 2.1: 修改 `ChannelController.java`**

删除这两行 `static final` 常量，新增一个实例字段：

```java
// 删除：
// private static final long POST_STREAM_TIMEOUT_MS = 10L * 60_000L;
// private static final long TURN_WAIT_TIMEOUT_MS   = POST_STREAM_TIMEOUT_MS;

// 新增（与 heartbeatIntervalMs 并列）：
private final long postStreamTimeoutMs;
```

在构造器参数列表末尾新增一个参数（保持其余参数不变）：

```java
public ChannelController(JsonRpcCodec codec, ChannelService svc,
                         SessionBusRegistry buses, ObjectMapper om,
                         SseHeartbeatScheduler heartbeat,
                         @Value("${app.sse.heartbeat-interval-ms:30000}") long heartbeatIntervalMs,
                         @Value("${datatalk.channel.post-stream-timeout-ms:600000}") long postStreamTimeoutMs) {
    this.codec  = codec;
    this.svc    = svc;
    this.buses  = buses;
    this.om     = om;
    this.heartbeat = heartbeat;
    this.heartbeatIntervalMs   = heartbeatIntervalMs;
    this.postStreamTimeoutMs   = postStreamTimeoutMs;
}
```

在 `stream(...)` 方法中，两处使用常量的地方改为实例字段：

```java
// 原：ResponseBodyEmitter emitter = new ResponseBodyEmitter(POST_STREAM_TIMEOUT_MS);
ResponseBodyEmitter emitter = new ResponseBodyEmitter(postStreamTimeoutMs);

// 原：long deadline = System.currentTimeMillis() + TURN_WAIT_TIMEOUT_MS;
long deadline = System.currentTimeMillis() + postStreamTimeoutMs;
```

- [x] **Step 2.2: 在 `application.yml` 的 `datatalk:` 块下追加配置项**

在现有 `datatalk.channel.flush-interval` 同级位置追加（如果 `datatalk.channel` 节点不存在则新增）：

```yaml
datatalk:
  channel:
    post-stream-timeout-ms: 600000   # POST SSE 单 turn 最长存活（毫秒），默认 10 分钟
```

（确保缩进与现有 `datatalk.opencode` 等节点对齐。）

- [x] **Step 2.3: 编译验证**

```bash
cd server && mvn compile -q
```

期望：BUILD SUCCESS，0 errors。

---

## Task 3: H2 demo 数据源语义明确化（TD-001）

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/application.yml`

当前：
```yaml
  # H2 demo datasource (primary)
  datasource:
    url: jdbc:h2:mem:placeholder
```

`placeholder` 名称暗示"临时占位"，但实际上此数据源承担真实角色：由 `schema-demo.sql` 初始化演示数据，通过 `@Primary demoJdbcTemplate` 提供默认 JdbcTemplate fallback。改名 + 注释明确语义，消除误解。

- [x] **Step 3.1: 修改 `application.yml`**

将 H2 datasource 段替换为：

```yaml
  # Demo / fallback datasource — in-memory H2 initialized from schema-demo.sql + data-demo.sql.
  # Acts as the @Primary JdbcTemplate when no user-specific connection is active.
  # Not a placeholder: it seeds explorable demo data for development and smoke tests.
  datasource:
    url: jdbc:h2:mem:demodb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
```

`DB_CLOSE_DELAY=-1` 防止 HikariCP 在释放连接后 H2 销毁库（与 `mem:placeholder` 默认行为一致，但现在名称有意义）。

- [x] **Step 3.2: 编译并运行启动验证**

```bash
cd server && mvn compile -q
```

期望：BUILD SUCCESS。

注：如需完整启动验证，`mvn spring-boot:run -pl data-talk-adapter` 应正常启动，`/api/sessions` 等端点可响应。

---

## Task 4: 多 session SSE 订阅池（TD-MULTI-SESSION-SSE-POOL）

**Files:**
- Create: `client/src/features/session/hooks/use-background-session-subscribe.ts`
- Create: `client/src/features/session/hooks/__tests__/use-background-session-subscribe.test.tsx`
- Modify: `client/src/features/session/session-canvas.tsx`

**问题：** 用户切走后，后台仍在流式输出的 session 的 GET SSE 被断开。若 AI 继续推送 `ontology.updated` / `session.meta.updated` 等事件，ring buffer（500 条 / 5 min TTL）溢出后数据丢失。

**方案：** 对所有 `streamingBySession` 中存在但非 `activeSessionId` 的 session，在 `SessionCanvas` 内渲染一个空的 `BackgroundSubscriber` 组件，该组件调用 `useBackgroundSessionSubscribe(sessionId)` 维持 SSE 连接。当 session 流结束（`session.idle` / `session.error` 清掉 `streamingBySession`）时，组件卸载，连接自然关闭。

**设计约束：**
- 背景订阅复用 `buildEventSink`（已处理所有事件类型），不另写 sink。
- 不调用 `setConnected`——连接指示器只反映活跃 session。
- 使用独立的 `bgSubscribedSessions` Set，与 `useSessionSubscribe` 互不干扰。
- 如用户切回某个后台 session，`BackgroundSubscriber` 卸载（因 `id === activeSessionId` 被过滤），`useSessionSubscribe` 重建活跃订阅，resume cursor 已由 `buildEventSink` 持续更新。

- [x] **Step 4.1: 新建 `use-background-session-subscribe.ts`**

```ts
import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { buildEventSink, useChannelClient } from '@/services/channel/use-channel'
import { useChannelStore } from '@/stores/channel-store'
import { useConnectionStore } from '@/features/connection/store'

// Separate from useSessionSubscribe's module-level set — no interference.
const bgSubscribedSessions = new Set<string>()

export function useBackgroundSessionSubscribe(sessionId: string) {
  const client    = useChannelClient(sessionId)
  const queryClient  = useQueryClient()
  const connectionId = useConnectionStore((s) => s.activeConnectionId)

  useEffect(() => {
    if (!client) return
    // Guard against duplicate subscriptions across React strict-mode double invocations.
    if (bgSubscribedSessions.has(sessionId)) return

    // Resume from the last persisted cursor so background events are not replayed from 0.
    const resumeFrom = useChannelStore.getState().lastEventIdBySession.get(sessionId)
    bgSubscribedSessions.add(sessionId)
    const sink = buildEventSink(sessionId, client, queryClient, connectionId)
    const unsub = client.subscribe(resumeFrom, sink)

    return () => {
      unsub()
      bgSubscribedSessions.delete(sessionId)
    }
  }, [client, sessionId, queryClient, connectionId])
}
```

- [x] **Step 4.2: 新建测试文件 `__tests__/use-background-session-subscribe.test.tsx`**

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createElement, type ReactNode } from 'react'
import { renderHook } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useChannelStore } from '@/stores/channel-store'

// ── mock useChannelClient ──────────────────────────────────────────────────
const unsubMock     = vi.fn()
const subscribeMock = vi.fn(() => unsubMock)
const clientMock    = {
  subscribe:    subscribeMock,
  actionResult: vi.fn(),
  sendMessage:  vi.fn(),
  abort:        vi.fn(),
}

vi.mock('@/services/channel/use-channel', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/services/channel/use-channel')>()
  return {
    ...original,
    useChannelClient: vi.fn(() => clientMock),
  }
})

// ── helpers ───────────────────────────────────────────────────────────────
function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return createElement(QueryClientProvider, { client: qc }, children)
}

// Use unique session IDs per test to avoid module-level Set contamination.
let counter = 0
function freshId() { return `bg-session-${++counter}` }

describe('useBackgroundSessionSubscribe', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useChannelStore.setState({ lastEventIdBySession: new Map(), isConnected: false })
  })

  it('subscribes to the given session on mount', () => {
    renderHook(() => {
      const { useBackgroundSessionSubscribe } = require('../use-background-session-subscribe')
      useBackgroundSessionSubscribe(freshId())
    }, { wrapper })
    expect(subscribeMock).toHaveBeenCalledOnce()
  })

  it('unsubscribes on unmount', () => {
    let hook: any
    const { unmount } = renderHook(() => {
      const { useBackgroundSessionSubscribe } = require('../use-background-session-subscribe')
      hook = useBackgroundSessionSubscribe
      hook(freshId())
    }, { wrapper })
    unmount()
    expect(unsubMock).toHaveBeenCalledOnce()
  })

  it('does not call useChannelStore.setConnected', () => {
    const setConnectedSpy = vi.spyOn(useChannelStore.getState(), 'setConnected')
    renderHook(() => {
      const { useBackgroundSessionSubscribe } = require('../use-background-session-subscribe')
      useBackgroundSessionSubscribe(freshId())
    }, { wrapper })
    expect(setConnectedSpy).not.toHaveBeenCalled()
  })
})
```

- [x] **Step 4.3: 运行测试确认失败（hook 文件尚未挂进组件，但 hook 本身应可测试）**

```bash
cd client && npx vitest run src/features/session/hooks/__tests__/use-background-session-subscribe.test.tsx
```

期望：3 个测试 PASS（hook 本身逻辑已实现）；如失败，先修 hook 再继续。

- [x] **Step 4.4: 修改 `session-canvas.tsx`，挂载后台订阅者**

在 `session-canvas.tsx` 开头新增导入：

```ts
import { useMemo } from 'react'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useBackgroundSessionSubscribe } from './hooks/use-background-session-subscribe'
```

在 `SessionCanvas` 函数体内、`useFlipComposer()` 之前，定义背景 session 列表：

```ts
const streamingBySession = useChatPartsStore((s) => s.streamingBySession)
const backgroundSessionIds = useMemo(
  () => [...streamingBySession.keys()].filter((id) => id !== sessionId),
  [streamingBySession, sessionId],
)
```

在文件内（`SessionCanvas` 外部，模块顶层）定义一个小组件避免在 map 里调用 hook：

```tsx
function BackgroundSubscriber({ sessionId }: { sessionId: string }) {
  useBackgroundSessionSubscribe(sessionId)
  return null
}
```

在 return JSX 的 `<div>` 内部最前面插入：

```tsx
{backgroundSessionIds.map((id) => (
  <BackgroundSubscriber key={id} sessionId={id} />
))}
```

完整修改后的 `session-canvas.tsx`：

```tsx
import { useMemo } from 'react'
import { useSessionStore } from '@/stores/session-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useFlipComposer } from './use-flip-composer'
import { useSessionHistory } from './hooks/use-session-history'
import { useSessionSubscribe } from './hooks/use-session-subscribe'
import { useBackgroundSessionSubscribe } from './hooks/use-background-session-subscribe'
import { usePendingConnectionResume } from './hooks/use-pending-connection-resume'
import { usePendingPromptResume } from './hooks/use-pending-prompt-resume'
import { SplitView } from './split-view'
import { PromptComposer } from './prompt-composer'
import { ModelOverlay } from './model-overlay'

function BackgroundSubscriber({ sessionId }: { sessionId: string }) {
  useBackgroundSessionSubscribe(sessionId)
  return null
}

export function SessionCanvas() {
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const streamingBySession = useChatPartsStore((s) => s.streamingBySession)
  const backgroundSessionIds = useMemo(
    () => [...streamingBySession.keys()].filter((id) => id !== sessionId),
    [streamingBySession, sessionId],
  )

  useFlipComposer()
  useSessionHistory(sessionId)
  useSessionSubscribe(sessionId)
  usePendingConnectionResume()
  usePendingPromptResume()

  return (
    <div className="relative h-full">
      {backgroundSessionIds.map((id) => (
        <BackgroundSubscriber key={id} sessionId={id} />
      ))}
      <SplitView />
      <PromptComposer />
      <ModelOverlay />
    </div>
  )
}
```

- [x] **Step 4.5: 类型检查**

```bash
cd client && npx tsc --noEmit
```

期望：0 errors。

---

## Task 5: 文档收口

**Files:**
- Modify: `docs/exec-plans/tech-debt-tracker.md`
- Modify: `docs/references/opencode-protocol.md`
- Modify: `docs/exec-plans/index.md`

- [x] **Step 5.1: 在 `tech-debt-tracker.md` 的「当前债务」表中，标记已解决的 7 项**

对以下每一行，将描述内容用 `~~删除线~~` 包裹，并在「来源」列补充完成日期：

| TD-001 | `~~application.yml 使用 H2 内存库...~~ jdbc:h2:mem:demodb + 语义注释完成` | `TD-001 2026-04-20 已完成` |
| TD-013 | `~~POST 流自身的 10 min 超时上限...~~ 已提取为 datatalk.channel.post-stream-timeout-ms 可配置项` | `2026-04-20 已完成` |
| TD-014 | `~~DtEvent.SessionError 定义但未消费~~ buildEventSink 已消费：清 streaming + showErrorToast` | `2026-04-20 已完成` |
| TD-015 | `~~DtEvent.SessionCreated / SessionDeleted 定义但未消费~~ buildEventSink 已消费：invalidateQueries(['sessions', ...])` | `2026-04-20 已完成` |
| TD-016 | `~~DtEvent.SessionCompacted 定义但未消费~~ buildEventSink 已消费：toast.info 通知` | `2026-04-20 已完成` |
| TD-017 | `~~DtEvent.SessionDiff 定义但未消费；payload 语义待调研~~ buildEventSink 已安全忽略（OpenCode 1.4.7 payload 语义仍未公开文档化）` | `2026-04-20 已完成` |
| TD-MULTI-SESSION-SSE-POOL | `~~useSessionSubscribe 当前仅跟随 activeSessionId...~~ BackgroundSubscriber + useBackgroundSessionSubscribe 实现订阅池，streaming session 后台保持 SSE 存活` | `2026-04-20 已完成` |

并将这 7 行移动到文件底部的「已清除债务」表。

- [x] **Step 5.2: 更新 `docs/references/opencode-protocol.md` 的 session.diff 行**

将：
```
| session.diff | SessionDiff | diff 事件（payload 待调研） |
```
改为：
```
| session.diff | SessionDiff | diff 事件（payload 语义未在 OpenCode 1.4.7 文档中公开；前端安全忽略，不影响功能） |
```

- [x] **Step 5.3: 在 `docs/exec-plans/index.md` 登记本计划并在完成后移到 Completed**

在「活跃计划」表末尾追加行：
```
| [Tech Debt Batch — Session Events / POST Timeout / SSE Pool](./2026-04-20-tech-debt-batch-plan.md) | in_progress | 清除 TD-001/013/014/015/016/017/TD-MULTI-SESSION-SSE-POOL，共 7 项技术债，涵盖前端 session 事件消费、后端 POST 超时可配置、H2 datasource 语义明确、多 session SSE 后台订阅池。 |
```

全部任务完成后，将状态从 `in_progress` 改为 `completed`，并移到「已完成计划」表。

---

## 验证矩阵

| 验证命令 | 时机 | 期望结果 |
|---------|------|---------|
| `cd client && npx vitest run src/services/channel/use-channel.test.ts` | Task 1 完成后 | 所有测试 PASS |
| `cd client && npx vitest run src/features/session/hooks/__tests__/use-background-session-subscribe.test.tsx` | Task 4 Step 4.3 | 3 tests PASS |
| `cd client && npx tsc --noEmit` | Task 1 Step 1.5 / Task 4 Step 4.5 | 0 errors |
| `cd server && mvn compile -q` | Task 2 Step 2.3 / Task 3 Step 3.2 | BUILD SUCCESS |

## 决策日志

- `session.compacted` 使用 `toast.info` 硬编码字符串，不走 `useI18n()`（`buildEventSink` 是普通函数，无法调用 React context hook）。i18n 可在后续独立处理。
- `session.diff` 安全忽略而不是报错——未知 payload 不应破坏事件循环。
- 背景订阅复用 `buildEventSink` 而非自定义 sink——`buildEventSink` 已处理所有事件类型（含 `action.invoke`），背景 session 同样需要响应 AI 工具调用，否则 AI 会因缺少 `action_result` 卡住。
- `BackgroundSubscriber` 定义在 `session-canvas.tsx` 模块顶层（非文件内部），以符合 React hooks 规则（hook 不能在 map 回调里调用）。
- 背景订阅的生命周期完全由 `streamingBySession` 驱动（streaming 结束 → 组件卸载 → SSE 关闭），不需要额外的定时器或手动清理。
