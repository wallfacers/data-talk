# 去除 DB 连接强依赖 + 默认模型自动选择 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐任务实施。所有步骤使用 checkbox (`- [ ]`) 跟踪进度。

**Goal:** 允许用户在**没有任何数据库连接**的情况下创建会话并发送消息；同时自动把 `current_model` 设为首个可用 provider 下的首个可用 model，用户显式选择时以用户选择为准。

**Architecture:**
- **后端**：DB schema 已经允许 `connection_id NULL`（`V1__init.sql:7` 未加 NOT NULL），`SessionService.create`、`SessionRepository.upsert`、SQL 已全路径支持 null。唯一阻塞点是 `SessionController.java:29-30` 的硬校验。去掉该校验即可。Action 层（`ExecuteSql/ReadSchema/LayoutErd`）仍要求 `connectionId`，但那是 LLM 调用时才会触发，没有连接时 LLM 无法调 action — 本身就是合理降级，**本计划不动**。
- **前端**：
  - 移除 `prompt-composer.tsx`、`app-sidebar.tsx`、`use-sessions.ts`、`use-pending-prompt-resume.ts` 里对 `activeConnectionId` 的门控。`createSession` 与 `listSessions` 改为可空 `connectionId`。
  - 新增 `useAutoSelectDefaultModel` hook：当 `currentModelId == null` 且模型列表加载完成并存在"已连接 provider × 启用 model"组合时，调用 `setCurrentModel` 持久化首个可用组合；挂载在应用顶层（`__root.tsx`），只在加载完成后触发一次。
- **类型同步**：`types/generated/api.ts` 的 `SessionDto.connectionId` 与 `SessionCreateRequest.connectionId` 改为 `string | null`。

**Tech Stack:** Spring Boot 3.5 / JUnit 5 / AssertJ（后端）· React 19 / TanStack Query / Zustand / Vitest（前端）

**Parallel strategy (per CLAUDE.md):** Phase B（后端契约）独立；Phase C（前端类型+API）依赖 B 的契约放宽但仅是文字对齐，先写前端不会出错；Phase D（前端门控）依赖 C；Phase E（默认模型 hook）独立于 B/C/D；Phase F（测试修复）收尾；最终 Phase G 统一验证。Phase D 的 4 个子任务之间互相独立，可并行；Phase E 独立于其他 phase，可与 B/C/D 并行分派。

---

## 文件结构

### 后端改动
- **Modify**：`server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java` — 删除 `connectionId` 必填校验
- **Modify**：`server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerIT.java` — 新增 null-connection 路径用例

### 前端改动
- **Modify**：`client/src/types/generated/api.ts` — `SessionDto.connectionId` 与 `SessionCreateRequest.connectionId` 改可空
- **Modify**：`client/src/services/api/session.ts` — `createSession` 签名放宽
- **Modify**：`client/src/features/session/prompt-composer.tsx` — 去掉连接门控，传 `null`
- **Modify**：`client/src/features/workspace/components/app-sidebar.tsx` — 去掉连接门控，传 `null`
- **Modify**：`client/src/features/session/hooks/use-sessions.ts` — 去掉 `enabled: connectionId !== null`
- **Modify**：`client/src/features/session/hooks/use-pending-prompt-resume.ts` — 去掉 `activeConn` 依赖
- **Create**：`client/src/features/session/hooks/use-auto-select-default-model.ts` — 新 hook
- **Create**：`client/src/features/session/hooks/__tests__/use-auto-select-default-model.test.tsx` — 单测
- **Modify**：`client/src/routes/__root.tsx` — 挂载新 hook
- **Modify**：`client/src/features/session/chat-header.test.tsx` — 删除过时 `activeConnectionId` setup 行

### 文档收尾
- **Modify**：`docs/exec-plans/index.md` — 添加活跃条目；完成后移到已完成

---

## Phase B：后端放宽 `connectionId` 校验

### Task B1：新增"null connectionId 也能创建会话"的 controller 集成测试

**Files:**
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerIT.java`

- [x] **Step 1: 在 `SessionControllerIT` 末尾追加测试方法**

在 `SessionControllerIT` 类内部（闭合大括号前）新增：

```java
    @Test
    void 创建会话允许省略_connectionId() throws Exception {
        mvc.perform(post("/api/sessions")
                .contentType("application/json")
                .content("{\"title\":\"无连接会话\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.connectionId").doesNotExist())
            .andExpect(jsonPath("$.title").value("无连接会话"));
    }

    @Test
    void 创建会话允许_connectionId_显式为_null() throws Exception {
        mvc.perform(post("/api/sessions")
                .contentType("application/json")
                .content("{\"connectionId\":null,\"title\":\"空连接\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connectionId").doesNotExist());
    }

    @Test
    void 创建会话允许_connectionId_为空字符串并视为_null() throws Exception {
        mvc.perform(post("/api/sessions")
                .contentType("application/json")
                .content("{\"connectionId\":\"\",\"title\":\"空串\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connectionId").doesNotExist());
    }
```

说明：
- `jsonPath("$.connectionId").doesNotExist()` 对应 Jackson 默认会把 `null` 字段序列化出来 — 因此需要该字段值为 null **并且** Spring 的 JSON 配置省略 null。若当前全局配置**不省略 null**，则改用 `jsonPath("$.connectionId").value(nullValue())`，并从 `import static org.hamcrest.Matchers.nullValue` 引入匹配器。实施时先跑一次，按实际输出调整断言。

- [x] **Step 2: 运行新测试验证它们 FAIL**

```bash
cd server && mvn -q -pl data-talk-adapter -Dtest=SessionControllerIT test
```

期望：三条新用例因 `IllegalArgumentException: connectionId is required` 而返回 400 / 500（看全局异常映射），断言失败。

- [x] **Step 3: 修改 `SessionController.create`**

文件：`server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java`

把：

```java
    @PostMapping
    public SessionDto create(@RequestBody SessionCreateRequest req) {
        if (req == null || req.connectionId() == null || req.connectionId().isBlank()) {
            throw new IllegalArgumentException("connectionId is required");
        }
        SessionRecord rec = svc.create(req.connectionId(), req.title());
        return toDto(rec);
    }
```

替换为：

```java
    @PostMapping
    public SessionDto create(@RequestBody SessionCreateRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("request body is required");
        }
        String connectionId = (req.connectionId() == null || req.connectionId().isBlank())
            ? null : req.connectionId();
        SessionRecord rec = svc.create(connectionId, req.title());
        return toDto(rec);
    }
```

- [x] **Step 4: 运行全部 SessionControllerIT 与 SessionServiceTest 验证**

```bash
cd server && mvn -q -pl data-talk-adapter -Dtest=SessionControllerIT test
cd server && mvn -q -pl data-talk-application -Dtest=SessionServiceTest test
```

期望：全部 PASS。若 Step 1 里 `doesNotExist()` 断言与实际响应不符，改为 `value(nullValue())` 并重跑。

- [x] **Step 5: 编译全量 server**

```bash
cd server && mvn -q install -pl data-talk-application,data-talk-adapter -am -DskipTests
```

期望：BUILD SUCCESS。

- [x] **Step 6: 提交**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerIT.java
git commit -m "feat(session): allow null connectionId when creating session"
```

---

## Phase C：前端 API / 类型 放宽

### Task C1：类型声明允许 `connectionId` 可空

**Files:**
- Modify: `client/src/types/generated/api.ts`
- Modify: `client/src/services/api/session.ts`

- [x] **Step 1: 更新 `types/generated/api.ts`**

把：

```ts
export interface SessionDto {
  id: string
  connectionId: string
  title: string
  hasEverSent: boolean
  createdAt: number
  updatedAt: number
  titleLocked: boolean
}

export interface SessionCreateRequest {
  connectionId: string
  title: string
}
```

替换为：

```ts
export interface SessionDto {
  id: string
  connectionId: string | null
  title: string
  hasEverSent: boolean
  createdAt: number
  updatedAt: number
  titleLocked: boolean
}

export interface SessionCreateRequest {
  connectionId: string | null
  title: string
}
```

> 注：该文件标注为"generated"，实际生成器见 `docs/exec-plans/2026-04-18-td006-api-types-sync-plan.md`。本次**直接手改**；后续再回流到生成器配置。在文件最顶部的 `// auto-generated` 注释下方（若存在）追加一行：`// manual override 2026-04-18: connectionId nullable (see drop-connection-gate plan)`。若文件没有顶部注释，不必添加。

- [x] **Step 2: 更新 `services/api/session.ts`**

把：

```ts
export function createSession(connectionId: string, title: string) {
  return http.post('sessions', { json: { connectionId, title } }).json<Session>()
}
```

替换为：

```ts
export function createSession(connectionId: string | null, title: string) {
  return http.post('sessions', { json: { connectionId, title } }).json<Session>()
}
```

- [x] **Step 3: 类型检查**

```bash
cd client && npx tsc --noEmit
```

期望：零错误（调用方暂时仍传 string，类型收窄下兼容）。

- [x] **Step 4: 提交**

```bash
git add client/src/types/generated/api.ts client/src/services/api/session.ts
git commit -m "feat(api): widen SessionDto.connectionId and createSession param to nullable"
```

---

## Phase D：前端移除连接门控（4 个独立子任务，可并行批次执行）

### Task D1：`prompt-composer.tsx` 去掉连接校验

**Files:**
- Modify: `client/src/features/session/prompt-composer.tsx`

- [x] **Step 1: 修改 `onSubmit` 判断分支**

在 `prompt-composer.tsx` 中找到：

```tsx
    if (!activeSessionId) {
      if (!hasActiveModel) {
        setPendingPrompt(t)
        setPendingModelPrompt(true)
        return
      }
      if (!activeConnectionId) {
        toast.error('请先在侧边栏选择或创建连接')
        return
      }
      setText('')
      setPendingPrompt(t)
      try {
        const sess = await createSession(activeConnectionId, '新会话')
        qc.invalidateQueries({ queryKey: ['sessions', activeConnectionId] })
        openSession(sess.id, sess.hasEverSent)
```

替换为：

```tsx
    if (!activeSessionId) {
      if (!hasActiveModel) {
        setPendingPrompt(t)
        setPendingModelPrompt(true)
        return
      }
      setText('')
      setPendingPrompt(t)
      try {
        const sess = await createSession(activeConnectionId, '新会话')
        qc.invalidateQueries({ queryKey: ['sessions', activeConnectionId ?? null] })
        openSession(sess.id, sess.hasEverSent)
```

- [x] **Step 2: 移除已无用的 `toast` 调用保留（仍用于 createSession 错误提示），但移除 `useConnectionStore` import 中未使用的部分**

检查：`activeConnectionId` 在剩余代码里仍被 `createSession` 用（传 `null` 也合法）。保留 import。不动。

- [x] **Step 3: 类型检查**

```bash
cd client && npx tsc --noEmit
```

期望：零错误。

- [x] **Step 4: 提交**

```bash
git add client/src/features/session/prompt-composer.tsx
git commit -m "feat(composer): remove connection gating — allow sending without DB connection"
```

---

### Task D2：`app-sidebar.tsx` 去掉创建会话的连接校验

**Files:**
- Modify: `client/src/features/workspace/components/app-sidebar.tsx`

- [x] **Step 1: 修改 `createMut.mutationFn`**

在 `app-sidebar.tsx` 中找到：

```tsx
  const createMut = useMutation({
    mutationFn: async () => {
      if (!hasActiveModel) throw new Error('请先在设置中配置模型')
      if (!activeConnectionId) throw new Error('请先在连接列表中选择一个连接')
      return createSession(activeConnectionId, '新会话')
    },
    onSuccess: (sess) => {
      openSession(sess.id, sess.hasEverSent)
      qc.invalidateQueries({ queryKey: ['sessions', activeConnectionId] })
    },
```

替换为：

```tsx
  const createMut = useMutation({
    mutationFn: async () => {
      if (!hasActiveModel) throw new Error('请先在设置中配置模型')
      return createSession(activeConnectionId, '新会话')
    },
    onSuccess: (sess) => {
      openSession(sess.id, sess.hasEverSent)
      qc.invalidateQueries({ queryKey: ['sessions', activeConnectionId ?? null] })
    },
```

- [x] **Step 2: 类型检查**

```bash
cd client && npx tsc --noEmit
```

期望：零错误。

- [x] **Step 3: 提交**

```bash
git add client/src/features/workspace/components/app-sidebar.tsx
git commit -m "feat(sidebar): remove connection gating on create-session"
```

---

### Task D3：`use-sessions.ts` 移除 enabled 条件

**Files:**
- Modify: `client/src/features/session/hooks/use-sessions.ts`

- [x] **Step 1: 改为无条件启用**

把：

```ts
export function useSessions() {
  const connectionId = useConnectionStore((s) => s.activeConnectionId)

  return useQuery({
    queryKey: ['sessions', connectionId],
    queryFn: () => listSessions(connectionId ?? undefined),
    enabled: connectionId !== null,
  })
}
```

替换为：

```ts
export function useSessions() {
  const connectionId = useConnectionStore((s) => s.activeConnectionId)

  return useQuery({
    queryKey: ['sessions', connectionId ?? null],
    queryFn: () => listSessions(connectionId ?? undefined),
  })
}
```

语义：
- 无 connection 时 query 仍然触发，`listSessions(undefined)` 走 `GET /api/sessions`（无 `connectionId` 参数），后端 `SessionService.list(null)` 返回 `repo.listAll()` — 符合预期。
- 有 connection 时 query key 带 connection id，按连接过滤；切换 connection 自然触发重新拉取。

- [x] **Step 2: 类型检查**

```bash
cd client && npx tsc --noEmit
```

期望：零错误。

- [x] **Step 3: 提交**

```bash
git add client/src/features/session/hooks/use-sessions.ts
git commit -m "feat(sessions): always fetch session list (drop connection-id gating)"
```

---

### Task D4：`use-pending-prompt-resume.ts` 去掉 `activeConn` 依赖

**Files:**
- Modify: `client/src/features/session/hooks/use-pending-prompt-resume.ts`

- [x] **Step 1: 移除 `activeConn` 读取与 effect 依赖**

把整个文件替换为：

```ts
import { useEffect } from 'react'
import { useSessionStore } from '@/stores/session-store'
import { useChannel } from '@/services/channel/use-channel'
import { createTextPart } from '@/services/channel/types'
import { useHasActiveModel } from './use-has-active-model'

export function usePendingPromptResume() {
  const pendingPrompt = useSessionStore((s) => s.pendingPrompt)
  const modelOverlayOn = useSessionStore((s) => s.pendingModelPrompt)
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const setPendingPrompt = useSessionStore((s) => s.setPendingPrompt)
  const { sendMessage, isStreaming } = useChannel()
  const hasActiveModel = useHasActiveModel()

  useEffect(() => {
    if (modelOverlayOn || !pendingPrompt || !hasActiveModel || !activeSessionId || isStreaming) return

    const draft = pendingPrompt
    setPendingPrompt(null)
    void sendMessage([createTextPart(activeSessionId, draft)])
  }, [modelOverlayOn, pendingPrompt, hasActiveModel, activeSessionId, isStreaming, setPendingPrompt, sendMessage])
}
```

变更：
- 删除 `import { useConnectionStore }` 与 `const activeConn = ...`
- effect 条件里删除 `!activeConn`
- 依赖数组里删除 `activeConn`

- [x] **Step 2: 类型检查**

```bash
cd client && npx tsc --noEmit
```

期望：零错误。

- [x] **Step 3: 提交**

```bash
git add client/src/features/session/hooks/use-pending-prompt-resume.ts
git commit -m "feat(resume): drop activeConnection dependency — resume pending prompt without DB connection"
```

---

## Phase E：默认模型自动选择

### Task E1：写失败的单测 `use-auto-select-default-model`

**Files:**
- Create: `client/src/features/session/hooks/__tests__/use-auto-select-default-model.test.tsx`

- [x] **Step 1: 写测试文件**

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import * as api from '@/features/settings/shared/api'
import { useAutoSelectDefaultModel } from '../use-auto-select-default-model'

function wrapper(qc: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
}

describe('useAutoSelectDefaultModel', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('当 currentModelId 为 null 时，选中首个已连接 provider 的首个启用模型并 PATCH', async () => {
    vi.spyOn(api, 'fetchModels').mockResolvedValue({
      providers: [
        { id: 'a-broken', name: 'A', connected: false, models: [{ id: 'm0', name: 'M0', enabled: true }] },
        { id: 'b-ok', name: 'B', connected: true, models: [
          { id: 'm1', name: 'M1', enabled: false },
          { id: 'm2', name: 'M2', enabled: true },
        ]},
      ],
    })
    vi.spyOn(api, 'getCurrentModel').mockResolvedValue({ modelId: null })
    const patch = vi.spyOn(api, 'setCurrentModel').mockResolvedValue(undefined)

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useAutoSelectDefaultModel(), { wrapper: wrapper(qc) })

    await waitFor(() => expect(patch).toHaveBeenCalledWith('b-ok/m2'))
    expect(patch).toHaveBeenCalledTimes(1)
  })

  it('当 currentModelId 已有值时，不触发 PATCH', async () => {
    vi.spyOn(api, 'fetchModels').mockResolvedValue({
      providers: [
        { id: 'b-ok', name: 'B', connected: true, models: [{ id: 'm2', name: 'M2', enabled: true }]},
      ],
    })
    vi.spyOn(api, 'getCurrentModel').mockResolvedValue({ modelId: 'b-ok/m2' })
    const patch = vi.spyOn(api, 'setCurrentModel').mockResolvedValue(undefined)

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useAutoSelectDefaultModel(), { wrapper: wrapper(qc) })

    // 多等一轮宏任务确保 effect 已跑完
    await new Promise((r) => setTimeout(r, 20))
    expect(patch).not.toHaveBeenCalled()
  })

  it('没有已连接 provider 或没有启用模型时，不触发 PATCH', async () => {
    vi.spyOn(api, 'fetchModels').mockResolvedValue({
      providers: [
        { id: 'a', name: 'A', connected: false, models: [{ id: 'm', name: 'M', enabled: true }] },
        { id: 'b', name: 'B', connected: true, models: [{ id: 'm', name: 'M', enabled: false }] },
      ],
    })
    vi.spyOn(api, 'getCurrentModel').mockResolvedValue({ modelId: null })
    const patch = vi.spyOn(api, 'setCurrentModel').mockResolvedValue(undefined)

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useAutoSelectDefaultModel(), { wrapper: wrapper(qc) })

    await new Promise((r) => setTimeout(r, 20))
    expect(patch).not.toHaveBeenCalled()
  })

  it('同一会话 providers 多次变化不会重复 PATCH', async () => {
    vi.spyOn(api, 'fetchModels').mockResolvedValue({
      providers: [
        { id: 'b', name: 'B', connected: true, models: [{ id: 'm', name: 'M', enabled: true }]},
      ],
    })
    vi.spyOn(api, 'getCurrentModel').mockResolvedValue({ modelId: null })
    const patch = vi.spyOn(api, 'setCurrentModel').mockResolvedValue(undefined)

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { rerender } = renderHook(() => useAutoSelectDefaultModel(), { wrapper: wrapper(qc) })

    await waitFor(() => expect(patch).toHaveBeenCalledTimes(1))

    rerender()
    rerender()
    await new Promise((r) => setTimeout(r, 20))
    expect(patch).toHaveBeenCalledTimes(1)
  })
})
```

- [x] **Step 2: 运行测试验证 FAIL**

```bash
cd client && npx vitest run src/features/session/hooks/__tests__/use-auto-select-default-model.test.tsx
```

期望：FAIL，错误为"Cannot find module '../use-auto-select-default-model'"。

### Task E2：实现 `useAutoSelectDefaultModel` hook

**Files:**
- Create: `client/src/features/session/hooks/use-auto-select-default-model.ts`

- [x] **Step 1: 写实现**

```ts
import { useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  aiQueryKeys,
  fetchModels,
  getCurrentModel,
  setCurrentModel,
  type ProviderDto,
} from '@/features/settings/shared/api'
import { formatModelId } from '@/features/settings/shared/utils'

/**
 * 当用户尚未选择模型时（`current-model` 后端返回 null），
 * 自动挑选首个 `connected=true` provider 下首个 `enabled=true` model 写回后端。
 * 用户显式选择后不再覆盖。
 *
 * 挂载位置：应用根组件（__root.tsx），每个渲染树只需挂一次。
 */
export function useAutoSelectDefaultModel() {
  const qc = useQueryClient()
  const fired = useRef(false)

  const { data: modelsData, isSuccess: modelsLoaded } = useQuery({
    queryKey: aiQueryKeys.models,
    queryFn: fetchModels,
    staleTime: Infinity,
  })
  const { data: current, isSuccess: currentLoaded } = useQuery({
    queryKey: aiQueryKeys.currentModel,
    queryFn: getCurrentModel,
    staleTime: Infinity,
  })

  const mut = useMutation({
    mutationFn: (id: string) => setCurrentModel(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: aiQueryKeys.currentModel }),
  })

  useEffect(() => {
    if (fired.current) return
    if (!modelsLoaded || !currentLoaded) return
    if (current?.modelId) {
      // 用户已选 / 曾选 → 尊重
      fired.current = true
      return
    }
    const picked = pickFirstAvailable(modelsData?.providers ?? [])
    if (!picked) return // 无可用模型 — 不触发，等用户配置
    fired.current = true
    mut.mutate(picked)
  }, [modelsLoaded, currentLoaded, current?.modelId, modelsData?.providers, mut])
}

function pickFirstAvailable(providers: ProviderDto[]): string | null {
  for (const p of providers) {
    if (!p.connected) continue
    const m = p.models.find((x) => x.enabled)
    if (m) return formatModelId(p.id, m.id)
  }
  return null
}
```

- [x] **Step 2: 运行测试验证 PASS**

```bash
cd client && npx vitest run src/features/session/hooks/__tests__/use-auto-select-default-model.test.tsx
```

期望：4 条用例全部 PASS。

- [x] **Step 3: 类型检查**

```bash
cd client && npx tsc --noEmit
```

期望：零错误。

- [x] **Step 4: 提交**

```bash
git add client/src/features/session/hooks/use-auto-select-default-model.ts \
        client/src/features/session/hooks/__tests__/use-auto-select-default-model.test.tsx
git commit -m "feat(model): auto-select first available model when current-model is unset"
```

### Task E3：在应用根挂载 hook

**Files:**
- Modify: `client/src/routes/__root.tsx`

- [x] **Step 1: 读取根路由当前内容**

```bash
cat client/src/routes/__root.tsx | head -80
```

期望：能看到 `export const Route = createRootRoute(...)` 或类似定义。

- [x] **Step 2: 在 Root 组件里调用 `useAutoSelectDefaultModel()`**

在 `__root.tsx` 顶部 imports 区加：

```tsx
import { useAutoSelectDefaultModel } from '@/features/session/hooks/use-auto-select-default-model'
```

在 Root 组件函数体最开头加：

```tsx
useAutoSelectDefaultModel()
```

（若 Root 组件是匿名箭头函数并返回 JSX，改写为常规函数形态再加 hook 调用。）

说明：若 `__root.tsx` 使用 `component: () => <...>` 的内联形式，把 component 抽成命名函数 `function RootLayout() { useAutoSelectDefaultModel(); return <Outlet /> ... }`，再在 route 定义里引用它。

- [x] **Step 3: 类型检查**

```bash
cd client && npx tsc --noEmit
```

期望：零错误。

- [x] **Step 4: 手工验证（本地开发模式）**

```bash
cd client && npm run dev
```

操作：
1. 打开 app；打开 DevTools → Network 面板过滤 `current-model`
2. 在 Settings → 模型中启用至少一个 provider（假设已有 credentials）
3. 若 `GET /api/ai/current-model` 返回 `{"modelId": null}`，应立即看到一次 `PATCH /api/ai/current-model` 带 body `{"modelId":"<provider>/<model>"}`
4. 刷新页面，不应再次 PATCH（因为 fired 锁是组件级，但后端也已持久化）
5. 回到 Chat 主界面：输入框左上角应显示模型名

- [x] **Step 5: 提交**

```bash
git add client/src/routes/__root.tsx
git commit -m "feat(app): mount auto-select-default-model hook at root"
```

---

## Phase F：测试修复

### Task F1：修复 `chat-header.test.tsx` 对 `activeConnectionId` 的过时依赖

**Files:**
- Modify: `client/src/features/session/chat-header.test.tsx`

- [x] **Step 1: 清理 connectionId 的 setup**

把：

```tsx
import { useConnectionStore } from '@/features/connection/store'
```

移除该 import。

把：

```tsx
    // Setup: enable the connection so useSessions is enabled
    useConnectionStore.setState({ activeConnectionId: 'c1' })

    const initialSession: api.Session = {
      id: 's1',
      connectionId: 'c1',
      ...
```

替换为：

```tsx
    const initialSession: api.Session = {
      id: 's1',
      connectionId: null,
      ...
```

原因：移除门控后 `useSessions` 不再需要 `activeConnectionId`，而且 `connectionId` 现在类型为 `string | null`，直接用 `null` 体现新契约。其余字段保持。

- [x] **Step 2: 运行**

```bash
cd client && npx vitest run src/features/session/chat-header.test.tsx
```

期望：5 条用例全部 PASS。

- [x] **Step 3: 提交**

```bash
git add client/src/features/session/chat-header.test.tsx
git commit -m "test(chat-header): drop obsolete activeConnectionId setup"
```

---

## Phase G：整体验证与文档收尾

### Task G1：后端全量 verify

**Files:** —

- [x] **Step 1: 全量测试**

```bash
cd server && mvn -q verify
```

期望：BUILD SUCCESS；`SessionControllerIT` 新三条用例 + 原四条全部 PASS；其他测试全绿。若 `ExecuteSqlAction` 或 `ReadSchemaAction` 的集成测试因空 connectionId 失败，说明那些测试依旧显式传了有效 connectionId — 预期正常。

### Task G2：前端全量 lint + tsc + test

**Files:** —

- [x] **Step 1: 类型检查**

```bash
cd client && npx tsc --noEmit
```

期望：零错误。

- [x] **Step 2: 单元测试**

```bash
cd client && npm run test -- --run
```

期望：全部 PASS。

- [x] **Step 3: 开发服务器冒烟**

```bash
cd client && npm run dev
```

在浏览器中：
1. **清空本地数据库**（或确认 `connections` 表为空）
2. 打开 app，进入 Chat 主页
3. Settings 里配置至少一个 provider 凭据；什么连接都不建
4. 回到 Chat：prompt 输入框应自动显示一个模型名
5. 输入一条消息并回车；应成功创建会话、发送消息、进入 streaming 状态 — **不再弹 "请先在侧边栏选择或创建连接" 错误**
6. 侧边栏历史会话应能看到刚创建的会话（title: "新会话"）
7. 新点"创建会话"按钮也应直接创建成功

若步骤 5/6/7 任一失败，返回 Phase 1 排查。

### Task G3：文档收尾

**Files:**
- Modify: `docs/exec-plans/index.md`

- [x] **Step 1: 本计划完成后把活跃条目移到已完成**

在 `docs/exec-plans/index.md`：
- 从"活跃计划"表中删除本计划行
- 在"已完成计划"表顶部加一行：
  ```md
  | [Drop Connection Gate + Default Model](./2026-04-18-drop-connection-gate-default-model-plan.md) | 2026-04-18 | 去掉发消息对 DB 连接的依赖 + 自动选择首个可用模型 |
  ```

- [x] **Step 2: 把本计划所有未勾选框打上 `[x]`**，并在文件末尾追加一条：

```md
---

## 执行纪要（2026-04-18 完成）

- DB 层未动：`V1__init.sql` 里 `connection_id` 本就可空
- 后端仅修改 `SessionController.create` 的参数校验
- 前端新增一个顶层 hook 负责默认模型选择，其余改动集中在移除 `activeConnectionId` 的条件分支
- 未改 Actions 层（ExecuteSql / ReadSchema / LayoutErd）— 它们仍硬要求 connectionId，但这属于 LLM 端契约，无连接时 LLM 自然没法调 action，符合预期降级
```

- [x] **Step 3: 提交**

```bash
git add docs/exec-plans/index.md docs/exec-plans/2026-04-18-drop-connection-gate-default-model-plan.md
git commit -m "docs(plans): mark drop-connection-gate plan as completed"
```

---

## Self-Review 清单（写计划时已核对）

- **Spec 覆盖**：
  - ✅ "不需要数据库连接" → Task B1 (后端放宽校验) + Task D1/D2 (前端去门控)
  - ✅ "只要有可用的模型连接就能发消息" → Task D1 保留 `hasActiveModel` 门控
  - ✅ "默认选择可用模型供应商里默认一个可用模型" → Task E1/E2/E3
  - ✅ "如果用户选择了用用户选择的" → Task E2 里 `if (current?.modelId) { fired=true; return }` 尊重已有选择
  - ✅ "数据源判断逻辑去掉" → Task D1/D2/D3/D4 全部移除
- **Placeholder 扫描**：无 TBD / 省略代码块 / "similar to" 引用
- **类型一致性**：`SessionDto.connectionId` 与 `SessionCreateRequest.connectionId` 前后端均改为可空；`createSession` 签名同步；`useAutoSelectDefaultModel` 返回 void，挂载点契约一致

---

## 执行纪要（2026-04-18 完成）

- DB 层未动：`V1__init.sql` 里 `connection_id` 本就可空
- 后端仅修改 `SessionController.create` 的参数校验；Phase B 写的 3 条新测试同时替换掉了原 `create_rejects_missing_connection`（该用例依赖已被移除的 400 行为）
- 前端新增一个顶层 hook 负责默认模型选择，其余改动集中在移除 `activeConnectionId` 的条件分支
- 未改 Actions 层（ExecuteSql / ReadSchema / LayoutErd）— 它们仍硬要求 connectionId，但这属于 LLM 端契约，无连接时 LLM 自然没法调 action，符合预期降级
- Phase G 验证结果：
  - `mvn verify`：BUILD SUCCESS，`SessionControllerIT` 10/10（含 3 条新 null-path 用例）
  - 前端 `tsc --noEmit`：零错误
  - 前端 vitest：新 hook 4/4、`chat-header.test.tsx` 5/5 PASS；另有 3 个测试文件（`split-view`、`stage-toggle-button`、`providers-page`）共 6 条失败，已 stash 验证为 baseline 预存在失败，与本计划无关
