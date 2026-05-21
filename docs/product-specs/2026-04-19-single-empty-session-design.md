# Single Empty Session — Design

**Date:** 2026-04-19
**Author:** wallfacers
**Status:** Approved

## 1. 背景与目标

当前"创建会话"按钮（侧边栏常驻 + 收起态浮动）直接触发 `POST /api/sessions`，无任何幂等与查重。用户疯狂点击、或在已存在未使用的空白会话时再点新建，都会导致侧边栏堆积多个空白占位，且消耗 OpenCode session slot。

**目标**：应用内同一时刻**最多存在 1 个空白会话**（`hasEverSent=false`）。点击"创建会话"若已存在空白，直接路由跳转到它；否则才真正新建。前端加按钮锁防快速重复点击；后端做幂等权威校验以应对跨 tab / 跨客户端并发。

**非目标**：
- 不做跨 JVM / 分布式锁（单机桌面应用，YAGNI）
- 不清理历史遗留的多条空白会话（避免误删）
- 不新增 DB 字段与迁移（复用现有 `sessions.has_ever_sent`）

## 2. 决策参数

| # | 决策点 | 值 |
|---|---|---|
| Q1 | 空白会话定义 | `hasEverSent = false`（消息数 == 0，权威信号由发消息时后端翻转） |
| Q2 | 命中已有空白时的行为 | 直接路由到已有 session，不新增条目 |
| Q3 | 防重手段 | 前端 `isPending` 短路 + 本地查重；后端 `synchronized` 幂等 |
| Q4 | 作用范围 | 全局（不按 connectionId 隔离） |

## 3. 架构与数据流

```
[侧边栏 "创建会话" 按钮]                [Composer 无 session 时自动创建]
       │                                        │
       ├─ 前端本地查重：                        │
       │    useSessions().find(!hasEverSent)
       │    ├─ 命中 → openSession(emptyId)  ← 不发请求
       │    └─ 未命中 → mutate()            ← 发请求
       │                                        │
       └──────────────┬─────────────────────────┘
                      ↓
              POST /api/sessions { connectionId, title? }
                      ↓
          SessionController.create
                      ↓
          SessionService.create (synchronized on createLock)
                ├─ repo.findEmpty() → 有 → 返回现有（reusedEmpty=true）
                └─ 无 → repo.insert(new)         （reusedEmpty=false）
                      ↓
          response: { ...Session, reusedEmpty: boolean }
                      ↓
              前端 openSession(id)
```

**不变量**：`SELECT COUNT(*) FROM sessions WHERE has_ever_sent=0` 在 `SessionService.create` 返回前永远 ≤ 1（由 JVM 内 `synchronized (createLock)` + SQLite 单 writer 保证）。

## 4. 组件拆分

### 4.1 后端（4 个文件）

#### `SessionRepository.java`

新增方法：
```java
Optional<SessionRecord> findEmpty();
```

SQL：
```sql
SELECT id, connection_id, title, has_ever_sent,
       opencode_sid, created_at, updated_at, title_locked
FROM sessions
WHERE has_ever_sent = 0
ORDER BY created_at DESC
LIMIT 1
```

`ORDER BY created_at DESC` 的作用：即使历史遗留多条空白，取最新那条作为"下一个复用目标"，避免复用到陈旧残留。

#### `SessionService.java`

```java
private final Object createLock = new Object();

public CreateResult create(String connectionId, String title) {
    synchronized (createLock) {
        Optional<SessionRecord> existing = repo.findEmpty();
        if (existing.isPresent()) {
            return new CreateResult(existing.get(), true);
        }
        long now = clock.millis();
        String id = UUID.randomUUID().toString();
        String effectiveTitle = Strings.defaultIfBlank(title, "新会话");
        SessionRecord rec = new SessionRecord(id, connectionId, effectiveTitle,
            false, null, now, now, false);
        repo.upsert(rec);
        return new CreateResult(rec, false);
    }
}

public record CreateResult(SessionRecord record, boolean reusedEmpty) {}
```

**锁选择理由**：SQLite 写本身串行（进程级单 writer），但 `findEmpty()`（读）+ `upsert()`（写）之间若不加额外锁，两线程可能同时读到 `Optional.empty()` 然后各自 insert。`synchronized (createLock)` 在 JVM 内守护这个复合操作。创建路径非热点，锁代价可忽略。

#### `SessionDto.java` / `SessionCreateRequest.java` / `SessionController.java`

`SessionDto` 新增 `reusedEmpty` 字段：
```java
public record SessionDto(
    String id, String connectionId, String title,
    boolean hasEverSent, long createdAt, long updatedAt, boolean titleLocked,
    boolean reusedEmpty
) {}
```

Controller POST 分支从 `CreateResult` 映射：
```java
CreateResult r = service.create(req.connectionId(), req.title());
return SessionDto.of(r.record(), r.reusedEmpty());
```

`listSessions` / `renameSession` / `findById` 等路径返回的 DTO 中 `reusedEmpty=false`（默认值，只在 POST 创建场景有语义）。

#### `SessionServiceTest.java`

新增 3 个测试：

- `create_reusesExistingEmpty`：预置一个 `hasEverSent=false` 的 session；调 `create(...)` → 返回同一 id、`reusedEmpty=true`、DB 记录数不变。
- `create_whenNoEmpty_createsNew`：DB 只含 `hasEverSent=true` 的 session；调 `create(...)` → 新记录、`reusedEmpty=false`、DB 记录数+1。
- `create_concurrentInvocations_yieldSingleSession`：`ExecutorService` 10 个线程 + `CountDownLatch` 同时调 `create`；事后断言 `hasEverSent=false` 记录数 == 1。

### 4.2 前端（3 个文件）

#### `client/src/services/api/session.ts`

类型自动从后端 SpringDoc 重生成（`npm run generate:api-types`）；`Session` 类型自动带 `reusedEmpty: boolean`。手写层无改动。

#### `client/src/features/workspace/components/app-sidebar.tsx`

```typescript
const createMut = useMutation({
  mutationFn: async () => {
    if (!hasActiveModel) throw new Error('请先在设置中配置模型')
    const sessions = qc.getQueryData<Session[]>(
      ['sessions', activeConnectionId ?? null]
    ) ?? []
    const empty = sessions.find((s) => !s.hasEverSent)
    if (empty) return { ...empty, reusedEmpty: true }
    return createSession(activeConnectionId ?? undefined)
  },
  onSuccess: (sess) => {
    openSession(sess.id, sess.hasEverSent)
    if (!sess.reusedEmpty) {
      qc.invalidateQueries({
        queryKey: ['sessions', activeConnectionId ?? null],
      })
    }
  },
})
```

两处 `onClick` 从 `() => createMut.mutate()` 改为：
```typescript
onClick={() => { if (!createMut.isPending) createMut.mutate() }}
```

防止 `mutate()` 在 TanStack Query 内部被排队执行多次。

#### `client/src/features/session/prompt-composer.tsx:77`（可选 / 非必需）

隐式创建路径自动受益于后端幂等；可顺手加本地查重提效，与 app-sidebar 模式一致。本 spec 不强制要求改动。

#### 前端单测（`app-sidebar.test.tsx` 新增 3 case）

- 缓存里已有空白 → 点击不触发 HTTP
- 缓存无空白 → 点击触发 HTTP
- `isPending=true` 期间快速重复点击只触发一次 mutate

## 5. 错误处理与边界

| 场景 | 行为 |
|---|---|
| 未配置 model | 前端本地拦截抛 toast，不发请求 |
| 无 active connection | `connectionId=null`，创建空白不绑连接（允许） |
| 并发多次 create | 第 1 个持锁 insert；第 2 个后进入，`findEmpty()` 命中，`reusedEmpty=true` |
| 前端缓存漂移：本地无空白但后端有 | POST 发出，后端返回 `reusedEmpty=true`，前端跳转到该 id |
| 前端缓存漂移反向：本地有空白但后端已删 | 前端 `openSession(staleId)` → 加载 404 走现有错误边界 |
| OpenCode 离线 | 创建成功（bind OpenCode session 是惰性的），`openCodeSid=null` |

**守护**：
- JVM 内并发：`synchronized (createLock)` 原子化 `findEmpty + insert`
- SQLite 写并发：单 writer 本身串行，与 Java 锁形成双层冗余
- 跨进程（多 JVM）：不支持（单机桌面应用无此场景，见 §7 技术债）

## 6. 验收

### 自动（CI 跑）

- 后端：`SessionServiceTest` 新增 3 个 case + `SessionControllerTest` 确认响应体含 `reusedEmpty` 字段
- 前端：`app-sidebar.test.tsx` 新增 3 case
- 前后端：`npx tsc --noEmit` 0 error / `mvn verify` 零失败

### 手动（补充原 Phase 6 清单为 S17-S21）

- **S17**：侧边栏连点"创建会话" 10 次 → 列表只多出 1 条空白
- **S18**：空白存在时再点新建 → 无 HTTP 请求，路由跳到已有空白
- **S19**：在空白会话里发 1 条消息后再点新建 → 正常新建
- **S20**：2 tab 同时狂点 → 刷新后空白总数 ≤ 1
- **S21**：清空前端 sessions 缓存后点新建，本地以为无空白但后端有 → 跳转到该空白、不产生重复

## 7. 技术债

`docs/exec-plans/tech-debt-tracker.md` 登记一条 P2：

> **TD-SINGLE-EMPTY-SESSION-MULTINODE** (P2)：`SessionService.create` 的 `synchronized` 仅在单 JVM 内有效。未来扩展至多节点时需改为 DB 唯一约束（partial unique index on `sessions(connection_id) WHERE has_ever_sent=0`）；SQLite 原生不支持 partial unique，迁移到 PG 时一并处理。

## 8. 文档范围

- 本 spec：`docs/product-specs/2026-04-19-single-empty-session-design.md`
- 登记：`docs/product-specs/index.md` §8
- 执行计划：`docs/exec-plans/2026-04-19-single-empty-session-plan.md`（由 writing-plans 产出）
- 索引：`docs/exec-plans/index.md` Active → 完工移 Completed
- 技术债：`docs/exec-plans/tech-debt-tracker.md` 加 TD-SINGLE-EMPTY-SESSION-MULTINODE
- 不涉及：`CLAUDE.md` / `ARCHITECTURE.md` / schema / migrations

## 9. 不变/不碰

- `SessionRecord` 字段不变（复用 `hasEverSent`）
- Flyway 无迁移
- OpenCode / Channel / Store / Phase 0-5 AI 消息渲染：无耦合
- 浏览器路由：无变化
