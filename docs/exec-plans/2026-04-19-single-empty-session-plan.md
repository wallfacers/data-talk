# Single Empty Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 保证应用内同一时刻最多只存在一个 `hasEverSent=false` 的空白会话——前端按钮锁 + 本地查重避免无效请求，后端 `synchronized` + `findEmpty()` 幂等做权威校验。

**Architecture:** 后端 `SessionService.create` 引入 `synchronized` 块保护 "查找空白 → 插入新记录" 复合操作；`SessionDto` 新增 `reusedEmpty` 响应字段让前端感知复用。前端 `app-sidebar` 在 `mutationFn` 内用 `qc.getQueryData` 本地查重 + `onClick` 加 `isPending` 短路守卫。

**Tech Stack:** Spring Boot 3.5 / Java 21 / JdbcTemplate / JUnit 5 / AssertJ ; React 19 / TanStack Query / vitest / testing-library

**Spec:** [2026-04-19-single-empty-session-design.md](../product-specs/2026-04-19-single-empty-session-design.md)

---

## 实施阶段总览

| Phase | 范围 | 可 mock 独立验证 |
|---|---|---|
| 0 | 后端：Repository/Service/DTO/Controller + 5 个测试 | ✅ |
| 1 | 前端：app-sidebar 前置查重 + 短路 + 3 个测试 | ✅（mock http） |
| 2 | 统一验证 + 文档归档 | - |

---

## Phase 0：后端幂等

### Task 0.1：SessionRepository.findEmpty()

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRepository.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/session/SessionServiceTest.java`（在现有文件里追加一个直连 repo 的 case）

- [x] **Step 1：在 `SessionRepository.java` 追加方法（紧跟在 `findById` 之后）**

```java
public Optional<SessionRecord> findEmpty() {
    var list = jdbc.query(
        "SELECT * FROM sessions WHERE has_ever_sent = 0 ORDER BY created_at DESC LIMIT 1",
        MAPPER);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
}
```

- [x] **Step 2：跑编译**

Run: `cd server && mvn compile -q`
Expected: 0 error

- [x] **Step 3：commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRepository.java
git commit -m "feat(repo): add findEmpty() for session idempotency lookup"
```

---

### Task 0.2：SessionService.create 幂等改造

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/session/CreateSessionResult.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/session/SessionServiceTest.java`

- [x] **Step 1：创建 `CreateSessionResult.java`（值对象）**

```java
package com.datatalk.application.session;

import com.datatalk.application.persistence.SessionRecord;

/** Result of SessionService.create — carries the record plus a flag indicating
 *  whether an existing empty session was reused instead of creating a new one. */
public record CreateSessionResult(SessionRecord record, boolean reusedEmpty) {}
```

- [x] **Step 2：修改 `SessionService.create` 返回类型并加锁**

将原 `create(...)` 方法（约 L36-43）整体替换为：

```java
private final Object createLock = new Object();

public CreateSessionResult create(String connectionId, String title) {
    synchronized (createLock) {
        Optional<SessionRecord> existing = repo.findEmpty();
        if (existing.isPresent()) {
            return new CreateSessionResult(existing.get(), true);
        }
        long now = clock.millis();
        String id = UUID.randomUUID().toString();
        String effectiveTitle = Strings.defaultIfBlank(title, "新会话");
        SessionRecord rec = new SessionRecord(id, connectionId, effectiveTitle, false, null, now, now, false);
        repo.upsert(rec);
        return new CreateSessionResult(rec, false);
    }
}
```

`createLock` 字段紧跟在其它 final 字段声明之后。保留所有其他方法原样。

- [x] **Step 3：修复现有测试以匹配新签名**

在 `SessionServiceTest.java` 中，把现有的两个 create 测试改为断言 `.record()`：

```java
@Test
void create_defaultsBlankTitleToNewSession() {
    SessionRecord fromNull = svc.create(null, null).record();
    SessionRecord fromBlank = svc.create(null, "   ").record();
    SessionRecord fromEmpty = svc.create(null, "").record();

    assertThat(fromNull.title()).isEqualTo("新会话");
    assertThat(fromBlank.title()).isEqualTo("新会话");
    assertThat(fromEmpty.title()).isEqualTo("新会话");
}

@Test
void create_preservesExplicitTitle() {
    SessionRecord rec = svc.create(null, "我的会话").record();
    assertThat(rec.title()).isEqualTo("我的会话");
}
```

注意：上述两个 test 原本第一次 create 产生空白（`hasEverSent=false`）后会被后续 create 复用。改进的做法：在每个 assertion 后 `repo.upsert(new SessionRecord(fromNull.id(), ..., true, ...))`（把 hasEverSent 置 true），让下一次 create 走新建路径。或者拆成各自 `@BeforeEach` 清理 DB 的独立小测试——**沿用简单方案**：每次 create 后立即 `repo.markHasEverSent(rec.id(), ...)`，这样三次 create 各走新建路径。

具体改写 `create_defaultsBlankTitleToNewSession`：

```java
@Test
void create_defaultsBlankTitleToNewSession() {
    SessionRecord fromNull = svc.create(null, null).record();
    repo.markHasEverSent(fromNull.id(), 600L);
    SessionRecord fromBlank = svc.create(null, "   ").record();
    repo.markHasEverSent(fromBlank.id(), 601L);
    SessionRecord fromEmpty = svc.create(null, "").record();

    assertThat(fromNull.title()).isEqualTo("新会话");
    assertThat(fromBlank.title()).isEqualTo("新会话");
    assertThat(fromEmpty.title()).isEqualTo("新会话");
}
```

- [x] **Step 4：在 `SessionServiceTest.java` 追加 3 个幂等专项测试（放在文件末尾，闭合花括号前）**

```java
@Test
void create_reusesExistingEmpty() {
    // Seed an empty session
    long now0 = 100L;
    repo.upsert(new SessionRecord("existing_empty", "c1", "新会话", false, null, now0, now0, false));

    CreateSessionResult result = svc.create("c2", "任意标题");

    assertThat(result.reusedEmpty()).isTrue();
    assertThat(result.record().id()).isEqualTo("existing_empty");
    // DB should still contain exactly one row
    assertThat(repo.listAll()).hasSize(1);
}

@Test
void create_whenNoEmpty_createsNew() {
    // Seed a session that has already been used
    repo.upsert(new SessionRecord("used", "c1", "sent", true, null, 100L, 100L, false));

    CreateSessionResult result = svc.create("c1", null);

    assertThat(result.reusedEmpty()).isFalse();
    assertThat(result.record().hasEverSent()).isFalse();
    assertThat(result.record().id()).isNotEqualTo("used");
    assertThat(repo.listAll()).hasSize(2);
}

@Test
void create_concurrentInvocations_yieldSingleEmpty() throws Exception {
    int threadCount = 10;
    java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(threadCount);
    java.util.concurrent.CountDownLatch fire = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threadCount);
    java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

    try {
        for (int i = 0; i < threadCount; i++) {
            exec.submit(() -> {
                ready.countDown();
                try {
                    fire.await();
                    svc.create(null, null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        fire.countDown();
        done.await();
    } finally {
        exec.shutdown();
    }

    long emptyCount = repo.listAll().stream().filter(r -> !r.hasEverSent()).count();
    assertThat(emptyCount).isEqualTo(1L);
}
```

导入已在文件顶部齐全（JUnit、AssertJ）；`java.util.concurrent.*` 使用全限定名避免新增 import。

- [x] **Step 5：跑测试**

Run: `cd server && mvn test -pl data-talk-application -Dtest=SessionServiceTest`
Expected: 所有 SessionServiceTest 通过（包含原有 + 新增的 3 个）

- [x] **Step 6：commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/session/CreateSessionResult.java \
        server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java \
        server/data-talk-application/src/test/java/com/datatalk/application/session/SessionServiceTest.java
git commit -m "feat(session): single-empty idempotency with synchronized + reusedEmpty flag"
```

---

### Task 0.3：SessionDto 加 reusedEmpty + Controller 映射

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/dto/SessionDto.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java`
- Test（若已存在）: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerTest.java`（新建；如项目尚无 controller 集成测试框架则跳过 Step 4-5，仅做 Step 1-3）

- [x] **Step 1：修改 `SessionDto.java`**

```java
package com.datatalk.dto;

public record SessionDto(
    String id,
    String connectionId,
    String title,
    boolean hasEverSent,
    long createdAt,
    long updatedAt,
    boolean titleLocked,
    boolean reusedEmpty
) {}
```

- [x] **Step 2：修改 `SessionController.java` — `toDto` 加重载 + create 分支**

```java
@PostMapping
public SessionDto create(@RequestBody SessionCreateRequest req) {
    if (req == null) {
        throw new IllegalArgumentException("request body is required");
    }
    String connectionId = (req.connectionId() == null || req.connectionId().isBlank())
        ? null : req.connectionId();
    CreateSessionResult r = svc.create(connectionId, req.title());
    return toDto(r.record(), r.reusedEmpty());
}

private static SessionDto toDto(SessionRecord r) {
    return toDto(r, false);
}

private static SessionDto toDto(SessionRecord r, boolean reusedEmpty) {
    return new SessionDto(r.id(), r.connectionId(), r.title(),
        r.hasEverSent(), r.createdAt(), r.updatedAt(), r.titleLocked(), reusedEmpty);
}
```

同时在顶部 import 加：
```java
import com.datatalk.application.session.CreateSessionResult;
```

- [x] **Step 3：跑整包编译**

Run: `cd server && mvn install -pl data-talk-application -am -DskipTests`
然后: `cd server && mvn compile -q`
Expected: 0 error

- [x] **Step 4：检查是否已有 Controller 测试框架**

Run: `find server -name "SessionControllerTest*" -path "*/test/*" 2>/dev/null`

**若存在**：跳到 Step 5。
**若不存在**（很可能）：**跳过 Step 5**，不新建控制器测试。`SessionServiceTest.create_reusesExistingEmpty` 已覆盖核心语义；HTTP 序列化通过 mvn verify 阶段的任何现有 smoke test 间接验证。

- [x] **Step 5（条件执行）：若已有 Controller 测试框架，追加一个 response-shape 断言**

（此步视 Step 4 结果执行或跳过）追加：
```java
@Test
void post_sessions_responseContainsReusedEmptyFlag() throws Exception {
    String body = """
        {"connectionId": "c1", "title": null}
        """;
    mockMvc.perform(post("/api/sessions").contentType(APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reusedEmpty").exists())
        .andExpect(jsonPath("$.reusedEmpty").isBoolean());
}
```

- [x] **Step 6：commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/dto/SessionDto.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java
# 若 Step 5 执行，也 add 对应测试文件
git commit -m "feat(adapter): expose reusedEmpty in POST /api/sessions response"
```

---

### Task 0.4：Phase 0 后端统一验证

- [x] **Step 1：全量编译**

Run: `cd server && mvn clean verify`
Expected: BUILD SUCCESS，全部测试通过

- [x] **Step 2：记录失败并不进入 Phase 1（如失败）**

若有失败，先定位并修复（大概率是 Dto 字段序列化顺序 / 测试断言），修复后再继续。

---

## Phase 1：前端

### Task 1.1：重新生成 API 类型（或手动补字段）

**Files:**
- Modify: `client/src/types/generated/api.ts`（自动生成或手改）

**背景：** `client/package.json` 的 `gen:api` 脚本通过 `openapi-typescript http://localhost:8080/v3/api-docs` 从后端抓 OpenAPI 重新生成。这要求后端正在运行。

- [x] **Step 1：选择路径**

**路径 A（推荐，需启动后端）**：
```bash
# 第 1 窗口
cd server && mvn install -pl data-talk-application -am -DskipTests
cd server && mvn spring-boot:run -pl data-talk-adapter
# 等后端起来（看到端口 8080 日志）
# 第 2 窗口
cd client && npm run gen:api
```

**路径 B（若后端启动不便）**：手动在 `client/src/types/generated/api.ts` 的 `SessionDto` 定义里加字段：

Grep 定位（可能形如）：
```typescript
SessionDto: {
  id: string
  connectionId?: string | null
  title: string
  hasEverSent: boolean
  createdAt: number
  updatedAt: number
  titleLocked: boolean
}
```

改为：
```typescript
SessionDto: {
  id: string
  connectionId?: string | null
  title: string
  hasEverSent: boolean
  createdAt: number
  updatedAt: number
  titleLocked: boolean
  reusedEmpty: boolean
}
```

- [x] **Step 2：typecheck**

Run: `cd client && npx tsc --noEmit`
Expected: 0 error（`reusedEmpty` 已对 `Session` 类型可见）

- [x] **Step 3：commit**

```bash
git add client/src/types/generated/api.ts
git commit -m "chore(types): regenerate api types — SessionDto gains reusedEmpty"
```

---

### Task 1.2：app-sidebar 前置查重 + 短路 guard + 单测

**Files:**
- Modify: `client/src/features/workspace/components/app-sidebar.tsx`
- Test: `client/src/features/workspace/components/__tests__/app-sidebar.test.tsx`（新建）

- [x] **Step 1：写失败测试**

新建 `client/src/features/workspace/components/__tests__/app-sidebar.test.tsx`：

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AppSidebar } from '../app-sidebar'
import { SidebarProvider } from '@/components/ui/sidebar'

// Mock modules
vi.mock('@/services/api/session', () => ({
  createSession: vi.fn(),
}))
vi.mock('@/features/connection/store', () => ({
  useConnectionStore: (sel: any) => sel({ activeConnectionId: 'c1' }),
}))
vi.mock('@/stores/session-store', () => ({
  useSessionStore: (sel: any) => sel({ openSession: vi.fn() }),
}))
vi.mock('@/features/session/hooks/use-has-active-model', () => ({
  useHasActiveModel: () => true,
}))
vi.mock('./nav-sessions', () => ({ NavSessions: () => null }))
vi.mock('./nav-user', () => ({ NavUser: () => null }))

import { createSession } from '@/services/api/session'

function renderWithProviders(initialSessions: any[] = []) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  qc.setQueryData(['sessions', 'c1'], initialSessions)
  render(
    <QueryClientProvider client={qc}>
      <SidebarProvider>
        <AppSidebar />
      </SidebarProvider>
    </QueryClientProvider>,
  )
  return { qc }
}

describe('AppSidebar — 创建会话', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('本地缓存无空白 session → 点击触发 createSession HTTP', async () => {
    (createSession as any).mockResolvedValueOnce({
      id: 'new_1', connectionId: 'c1', title: '新会话',
      hasEverSent: false, createdAt: 1, updatedAt: 1, titleLocked: false,
      reusedEmpty: false,
    })
    renderWithProviders([
      { id: 'old', hasEverSent: true, title: 'old', connectionId: 'c1',
        createdAt: 0, updatedAt: 0, titleLocked: false, reusedEmpty: false },
    ])

    fireEvent.click(screen.getByText('创建会话'))

    await waitFor(() => expect(createSession).toHaveBeenCalledTimes(1))
  })

  it('本地缓存已有空白 session → 点击不触发 HTTP', async () => {
    renderWithProviders([
      { id: 'empty_1', hasEverSent: false, title: '新会话', connectionId: 'c1',
        createdAt: 0, updatedAt: 0, titleLocked: false, reusedEmpty: false },
    ])

    fireEvent.click(screen.getByText('创建会话'))

    // 给 mutation 一个 tick 跑完
    await new Promise((r) => setTimeout(r, 50))
    expect(createSession).not.toHaveBeenCalled()
  })

  it('isPending 期间多次点击仅触发一次 mutate', async () => {
    let resolveFn: (v: any) => void = () => {}
    ;(createSession as any).mockImplementationOnce(
      () => new Promise((r) => { resolveFn = r }),
    )
    renderWithProviders([])

    const btn = screen.getByText('创建会话')
    fireEvent.click(btn)
    fireEvent.click(btn)
    fireEvent.click(btn)

    // 三次 click，mutate 只应 fire 一次（isPending 短路）
    await waitFor(() => expect(createSession).toHaveBeenCalledTimes(1))

    resolveFn({
      id: 'x', connectionId: 'c1', title: '新会话',
      hasEverSent: false, createdAt: 1, updatedAt: 1, titleLocked: false,
      reusedEmpty: false,
    })
  })
})
```

- [x] **Step 2：跑测试确认失败**

Run: `cd client && npx vitest run src/features/workspace/components/__tests__/app-sidebar.test.tsx`
Expected: FAIL（当前实现：HTTP 仍被调用 / 多点击触发多次 mutate）

- [x] **Step 3：修改 `app-sidebar.tsx`**

Read 当前文件，定位 `createMut = useMutation(...)` 块（约 L49-58）和两处 `onClick` 位置（L70 和 L102）。

将 `createMut` 替换为：

```typescript
const createMut = useMutation({
  mutationFn: async () => {
    if (!hasActiveModel) throw new Error('请先在设置中配置模型')
    const cached = qc.getQueryData<Session[]>(['sessions', activeConnectionId ?? null]) ?? []
    const empty = cached.find((s) => !s.hasEverSent)
    if (empty) return { ...empty, reusedEmpty: true }
    return createSession(activeConnectionId ?? undefined)
  },
  onSuccess: (sess) => {
    openSession(sess.id, sess.hasEverSent)
    if (!sess.reusedEmpty) {
      qc.invalidateQueries({ queryKey: ['sessions', activeConnectionId ?? null] })
    }
  },
})

const handleCreate = () => {
  if (createMut.isPending) return
  createMut.mutate()
}
```

顶部 import 追加：
```typescript
import type { Session } from '@/services/api/session'
```

两处 `onClick={() => createMut.mutate()}` 改为 `onClick={handleCreate}`（浮动按钮 L70 + 侧边栏按钮 L102）。

- [x] **Step 4：跑测试确认通过**

Run: `cd client && npx vitest run src/features/workspace/components/__tests__/app-sidebar.test.tsx`
Expected: PASS（3 个测试）

- [x] **Step 5：typecheck**

Run: `cd client && npx tsc --noEmit`
Expected: 0 error

- [x] **Step 6：commit**

```bash
git add client/src/features/workspace/components/app-sidebar.tsx \
        client/src/features/workspace/components/__tests__/app-sidebar.test.tsx
git commit -m "feat(sidebar): dedupe create-session — local lookup + isPending short-circuit"
```

---

## Phase 2：验证 + 归档

### Task 2.1：全量验证

- [x] **Step 1：后端全量 mvn verify**

Run: `cd server && mvn clean verify`
Expected: BUILD SUCCESS

- [x] **Step 2：前端 tsc + vitest**

Run: `cd client && npx tsc --noEmit && npx vitest run`
Expected: 0 error；新增 3 case 通过；原有计划无关失败（chat-header WIP / split-view / providers / stage-toggle-button）允许。

- [x] **Step 3：手动冒烟（需要启动全栈）**

```bash
cd server && mvn spring-boot:run -pl data-talk-adapter   # 窗口 1
cd client && npm run tauri dev                           # 窗口 2
```

- 手动场景 **S17**：疯狂点"创建会话" 10 次 → 侧边栏只多出 1 条空白
- 手动场景 **S18**：空白存在时再点 → Devtools Network 无新 POST /sessions
- 手动场景 **S19**：在空白会话里发一条消息后再点新建 → 正常新建

若通过，继续 Task 2.2；若失败，定位修复。

---

### Task 2.2：文档归档

**Files:**
- Modify: `docs/exec-plans/index.md`（Active → Completed）
- Modify: `docs/exec-plans/tech-debt-tracker.md`（加 TD-SINGLE-EMPTY-SESSION-MULTINODE）
- Modify: `docs/exec-plans/2026-04-19-single-empty-session-plan.md`（所有 checkbox 打勾）

- [x] **Step 1：登记 plan 到 Active（实施前就登记）**

在 `docs/exec-plans/index.md` 的 "活跃计划" 表格里加：
```
| [Single Empty Session](./2026-04-19-single-empty-session-plan.md) | 计划中 | 全局最多 1 个空白会话：后端 synchronized 幂等 + reusedEmpty 响应；前端本地查重 + isPending 短路；零 migration |
```

- [x] **Step 2：加 tech-debt 记录**

在 `docs/exec-plans/tech-debt-tracker.md` P2 区块追加：

```markdown
### TD-SINGLE-EMPTY-SESSION-MULTINODE (P2)

`SessionService.create` 的 `synchronized (createLock)` 仅在单 JVM 内有效。若未来扩展为多节点部署，需改为 DB 唯一约束（partial unique index `ON sessions(connection_id) WHERE has_ever_sent = 0`）。SQLite 原生不支持 partial unique，届时需配合数据库类型切换到 PG 一并处理。现状单机桌面应用无此需求。
```

- [x] **Step 3：完工迁移 Completed + 勾选 plan checkboxes**

将 Active 行迁到 "已完成计划" 表头下（日期 2026-04-19，摘要不变）。

在本 plan 文件全局将 `- [x]` 替换为 `- [x]`（若使用 sed：`sed -i 's/- \[ \]/- [x]/g' docs/exec-plans/2026-04-19-single-empty-session-plan.md`）。

- [x] **Step 4：commit**

```bash
git add docs/exec-plans/index.md \
        docs/exec-plans/tech-debt-tracker.md \
        docs/exec-plans/2026-04-19-single-empty-session-plan.md
git commit -m "docs: mark single-empty-session plan completed + log TD-MULTINODE"
```

---

## Self-Review Checklist（实施前通读）

- [x] 所有任务路径都是绝对路径格式（`server/...` / `client/...` / `docs/...`）
- [x] 每个 Phase 有明确的验证步骤（Phase 0 → mvn test；Phase 1 → vitest + tsc；Phase 2 → full verify）
- [x] `CreateSessionResult` 作为独立 record 文件放 `application.session` 包下（非内嵌类）
- [x] `SessionDto.reusedEmpty` 作为 non-null `boolean`（Java primitive 默认 false），列表/单查 GET 场景固定返回 false
- [x] 前端 `createMut.mutationFn` 返回的 "本地命中" 对象显式带上 `reusedEmpty: true`，与后端形态一致
- [x] 前端两处"新建会话"按钮（浮动 L70 + 侧边栏 L102）共享 `handleCreate`
- [x] 无 breaking change to Flyway / OpenCode / Channel / 其它 plan
- [x] Phase 0 SessionServiceTest 原有两个 create 测试改造：每次 `svc.create` 后 `repo.markHasEverSent(id, ...)` 把上一条标为已用，避免第 2/3 次 create 因幂等命中第 1 条

---

## 注意事项

- 后端 `CreateSessionResult` 是破坏性签名改动，但现仅 SessionController 一个生产调用者，SessionServiceTest 两个测试调用者。改完即完，无遗漏扫描需求。
- 前端测试里 mock 了 sidebar / store / http，**不**依赖真实后端；CI 可独立跑。
- Task 1.1 若走路径 B（手改 generated 文件），在本次提交后建议给 README 或 CLAUDE.md 的 "Frontend Types Generation" 段加一条 note：每次后端 DTO 变更需后续 `npm run gen:api` 重刷。此项列入建议但不强制。
