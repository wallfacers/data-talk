# Bang Query Chat Visibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `!select` / `!with` 直查在聊天区显示并可持久化恢复：消息表现为普通用户气泡但带 `SQL 直查` 标记，输入框在命中直查模式时进入变色感知态，并保证刷新后历史顺序稳定。

**Architecture:** 后端新增一张轻量 synthetic user message 表，专门存储 DataTalk 自己生成的直查用户消息；`HistoryService` 将 OpenCode 历史与这类 synthetic user message 统一合并并稳定排序后返回给前端。前端继续走现有 `bang_query` Tab 执行链，但在提交 `!select` / `!with` 时先确保存在 active session，再持久化 synthetic user message，并在 `UserBubble` 与 Composer 上做最小可感知视觉增强。

**Tech Stack:** Spring Boot 3.5 + Java 21 + SQLite/Flyway + JUnit 5；React 19 + TypeScript + Zustand + TanStack Query + Vitest。

**Spec:** [../product-specs/2026-04-21-bang-query-chat-visibility-design.md](../product-specs/2026-04-21-bang-query-chat-visibility-design.md)

**Execution Notes (2026-04-21):**
- 已完成实现与验证。
- 偏差说明：`BangQueryMessageCreateRequest` 最终保留为 `HistoryController` 内部 record，而不是单独的 adapter DTO 文件；这样可以维持现有 infrastructure controller 边界，不引入反向依赖。

---

## File Structure Map

### Create

- `server/data-talk-infrastructure/src/main/resources/db/migration/V9__synthetic_bang_query_messages.sql`
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SyntheticSessionMessageRecord.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SyntheticSessionMessageRepository.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/channel/SyntheticSessionMessageService.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/dto/BangQueryMessageCreateRequest.java`
- `client/src/services/api/bang-query-message.ts`

### Modify

- `server/data-talk-application/src/main/java/com/datatalk/application/channel/HistoryService.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/HistoryController.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/channel/HistoryServiceTest.java`
- `client/src/features/session/prompt-composer.tsx`
- `client/src/features/session/__tests__/prompt-composer.test.tsx`
- `client/src/features/session/hooks/use-session-history.ts`
- `client/src/features/session/hooks/__tests__/use-session-history.test.tsx`
- `client/src/features/chat/components/turn/user-bubble.tsx`
- `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx`
- `client/src/features/chat/components/helpers/__tests__/use-session-turns.test.ts`
- `client/src/i18n/messages.ts`
- `docs/exec-plans/index.md`

### Likely Untouched

- `client/src/features/stage/utils/open-bang-query-tab.ts` — 查询结果继续走现有 Stage/bang_query 路径
- `client/src/services/api/query.ts` — `/api/query` 接口形状不变
- `client/src/features/chat/components/turn/session-turn.tsx` — turn 结构应可复用现有 user-only turn 支持，除非测试证明需要补丁

---

## Task 1: 后端持久化 Synthetic Bang Query User Message

**Files:**
- Create: `server/data-talk-infrastructure/src/main/resources/db/migration/V9__synthetic_bang_query_messages.sql`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SyntheticSessionMessageRecord.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SyntheticSessionMessageRepository.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/channel/SyntheticSessionMessageService.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/dto/BangQueryMessageCreateRequest.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/HistoryController.java`

- [x] **Step 1.1: 写失败测试 — history 可返回 synthetic user message**

```java
@Test
void getMessagesMergesSyntheticBangQueryMessagesBeforeOpenCodeAssistantReply() {
    // synthetic user message + gateway assistant message
    // assert merged array size/order/metadata
}
```

- [x] **Step 1.2: 跑失败测试确认当前后端没有 synthetic 消息源**

Run: `cd server && mvn -q -pl data-talk-application -am -Dtest=HistoryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: FAIL，因为 `HistoryService` 当前只透传 OpenCode 历史。

- [x] **Step 1.3: 新增 migration**

```sql
CREATE TABLE synthetic_session_messages (
  id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  kind TEXT NOT NULL,
  text TEXT NOT NULL,
  metadata_json TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX idx_synthetic_session_messages_session
  ON synthetic_session_messages(session_id, created_at, id);
```

- [x] **Step 1.4: 实现 repository / record**

```java
public record SyntheticSessionMessageRecord(
    String id,
    String sessionId,
    String kind,
    String text,
    String metadataJson,
    long createdAt
) {}
```

- [x] **Step 1.5: 实现 create 服务**

```java
public SyntheticSessionMessageRecord createBangQueryUserMessage(
    String sessionId,
    String text,
    long createdAt
)
```

约束：
- `kind` 固定 `bang_query_user`
- `metadata_json` 至少包含 `displayKind=bang_query_user` 和 `queryMode=direct_sql`

- [x] **Step 1.6: 暴露写入接口**

建议接口：

```http
POST /api/sessions/{sessionId}/messages/bang-query
{
  "text": "!select 1",
  "createdAt": 1713650000000
}
```

返回：

```json
{
  "id": "sqm_xxx",
  "sessionId": "s1",
  "createdAt": 1713650000000,
  "kind": "bang_query_user"
}
```

- [x] **Step 1.7: 再跑后端专项测试**

Run: `cd server && mvn -q -pl data-talk-application -am -Dtest=HistoryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS

---

## Task 2: 后端 History Merge And Stable Ordering

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/channel/HistoryService.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/channel/HistoryServiceTest.java`

- [x] **Step 2.1: 写失败测试 — 同毫秒时排序稳定**

```java
@Test
void getMessagesUsesStableTieBreakerForSameCreatedAt() {
    // same createdAt for synthetic user and assistant
    // assert synthetic user appears first
}
```

- [x] **Step 2.2: 实现统一 merge**

后端输出统一为：

```json
[
  {
    "info": { "id": "sqm_1", "role": "user", "sessionID": "s1", "time": { "created": 1 } },
    "parts": [
      { "type": "text", "id": "prt_1", "sessionID": "s1", "messageID": "sqm_1", "text": "!select 1",
        "metadata": { "displayKind": "bang_query_user", "queryMode": "direct_sql" } }
    ]
  }
]
```

- [x] **Step 2.3: 排序规则实现**

排序键：
1. `createdAt`
2. source weight: synthetic-user < opencode-user < opencode-assistant < other
3. stable `id`

- [x] **Step 2.4: 再跑测试**

Run: `cd server && mvn -q -pl data-talk-application -am -Dtest=HistoryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: PASS

- [x] **Step 2.5: 后端编译**

Run: `cd server && mvn -q -pl data-talk-domain,data-talk-application,data-talk-infrastructure -am compile`
Expected: PASS

---

## Task 3: Composer 直查提交改为“先持久化消息，再执行查询”

**Files:**
- Create: `client/src/services/api/bang-query-message.ts`
- Modify: `client/src/features/session/prompt-composer.tsx`
- Modify: `client/src/features/session/__tests__/prompt-composer.test.tsx`

- [x] **Step 3.1: 写失败测试 — `!select` 会先创建 synthetic message**

```tsx
it('persists bang query user message before executing direct query', async () => {
  // no AI sendMessage
  // expect createBangQueryMessage called with raw "!select 1"
})
```

- [x] **Step 3.2: 写失败测试 — 无 active session 时先建会话**

```tsx
it('creates session before persisting bang query message when no active session exists', async () => {
  // createSession -> createBangQueryMessage -> executeQuery/openBangQueryTab
})
```

- [x] **Step 3.3: 实现 API helper**

```ts
export function createBangQueryMessage(sessionId: string, text: string, createdAt: number)
```

- [x] **Step 3.4: 修改 `prompt-composer.tsx`**

流程：
- 命中 `!select` / `!with`
- 若无 `activeConnectionId`，继续走 chooser
- 若无 `activeSessionId`，先 `createSession(activeConnectionId, initialTitle)`
- 调用 `createBangQueryMessage(sessionId, rawBangInput, now)`
- 再执行 `openBangQueryTab`
- 失败时保留 synthetic message，不回滚

- [x] **Step 3.5: 再跑专项测试**

Run: `cd client && npx vitest run src/features/session/__tests__/prompt-composer.test.tsx`
Expected: PASS

---

## Task 4: 前端历史恢复与气泡标记

**Files:**
- Modify: `client/src/features/session/hooks/use-session-history.ts`
- Modify: `client/src/features/session/hooks/__tests__/use-session-history.test.tsx`
- Modify: `client/src/features/chat/components/turn/user-bubble.tsx`
- Modify: `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx`
- Modify: `client/src/features/chat/components/helpers/__tests__/use-session-turns.test.ts`
- Modify: `client/src/i18n/messages.ts`

- [x] **Step 4.1: 写失败测试 — 恢复后的 synthetic user message 能显示成 user turn**

```tsx
it('renders bang query history item as a user-only turn with badge', async () => {
  // history payload contains metadata.displayKind=bang_query_user
  // expect SQL direct badge visible
})
```

- [x] **Step 4.2: 实现 `use-session-history` 兼容**

确保后端返回的 synthetic item 直接进入 `replaceSession()`，不被归一化逻辑抹掉 metadata。

- [x] **Step 4.3: 实现 `UserBubble` badge**

规则：
- inspect first text part metadata
- `displayKind === 'bang_query_user'` 时显示 `t('bangQuery.userBadge')`

- [x] **Step 4.4: 验证 turn 顺序**

如果后端 merge 后顺序稳定，则 `useSessionTurns` 理应无需逻辑变更；仅在测试证明不成立时才补代码。

- [x] **Step 4.5: 再跑专项测试**

Run: `cd client && npx vitest run src/features/session/hooks/__tests__/use-session-history.test.tsx src/features/chat/components/turn/__tests__/session-turn.test.tsx src/features/chat/components/helpers/__tests__/use-session-turns.test.ts`
Expected: PASS

---

## Task 5: Composer 输入框直查态视觉

**Files:**
- Modify: `client/src/features/session/prompt-composer.tsx`
- Modify: `client/src/features/session/__tests__/prompt-composer.test.tsx`
- Modify: `client/src/i18n/messages.ts`

- [x] **Step 5.1: 写失败测试 — `!select` 输入时出现直查态标签**

```tsx
it('shows direct query mode label when input matches bang select/with', async () => {
  // type !select 1
  // expect label "直查模式"
})
```

- [x] **Step 5.2: 实现直查态检测**

```ts
const isBangQueryMode = /^!\s*(select|with)\b/i.test(text.trim())
```

- [x] **Step 5.3: 实现视觉反馈**

最小方案：
- `textarea` 文本颜色切换
- 容器边框/背景进入直查态 token
- 展示 `直查模式` 小标签

- [x] **Step 5.4: 再跑专项测试**

Run: `cd client && npx vitest run src/features/session/__tests__/prompt-composer.test.tsx`
Expected: PASS

---

## Task 6: Consolidated Verification

**Files:** no new files

- [x] **Step 6.1: 前端验证**

Run: `cd client && npx vitest run src/features/session/__tests__/prompt-composer.test.tsx src/features/session/hooks/__tests__/use-session-history.test.tsx src/features/chat/components/turn/__tests__/session-turn.test.tsx src/features/chat/components/helpers/__tests__/use-session-turns.test.ts`

- [x] **Step 6.2: 前端类型检查**

Run: `cd client && npx tsc --noEmit`

- [x] **Step 6.3: 后端验证**

Run: `cd server && mvn -q -pl data-talk-application -am -Dtest=HistoryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [x] **Step 6.4: 后端编译 / 安装**

Run: `cd server && mvn -q -pl data-talk-domain,data-talk-application,data-talk-infrastructure -am compile`
Run: `cd server && mvn -q -pl data-talk-adapter -am -Dmaven.test.skip=true install`

- [x] **Step 6.5: 手动烟测（本轮未执行，留给人工补做）**

- 无 active session 时输入 `!select 1`：自动创建/进入会话，聊天区出现带 `SQL 直查` 标记的 user bubble，同时打开 bang query tab
- 已有 session 时输入 `!select 1`：消息立刻出现在当前会话里
- 刷新页面后重新打开该会话：消息仍存在，顺序不乱
- `!with ...` 同样进入直查态
- 普通 `!help` 或其他非 `select/with` 输入：不进入直查态，仍走 AI 路径

- [x] **Step 6.6: Housekeeping**

- 勾完本计划所有 checkbox
- 在 `docs/exec-plans/index.md` 把条目从 Active 移到 Completed
- 若消息历史契约新增 synthetic source 说明，同步回写 `docs/FRONTEND.md` / `docs/BACKEND.md`
