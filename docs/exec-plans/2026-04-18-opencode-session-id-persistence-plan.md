# OpenCode Session ID Persistence + Cascade Delete Plan

**Status:** 已完成（2026-04-18）

**Execution notes:**
- 无 Flyway 改动——`opencode_sid` 列早在 V1 已建，只是链路没接起来。
- `SessionRepository.updateOpenCodeSid`、`ChannelService.sendMessage` 读 DB→fallback map→create、`SessionService.delete` 级联 gateway、`OpenCodeHttpClient.deleteSession`（`DELETE /session/:id`）、`OpenCodeGatewayBeans.preloadSessionMap` 启动预热——全部落位。
- 回归 `mvn verify`：107 application + adapter/infra 全绿。
- `OpenCodeGateway` 构造签名新增 `SessionDeleter`，影响 2 处调用点（adapter bean + OpenCodeGatewayTest）已同步。
- 实机 smoke 由用户重启后端发"你好" + kill+重启发"我刚才说了什么"验证。

**Goal:** 消除 AI 多轮对话失忆。把 DataTalk session ↔ OpenCode session 的绑定从**内存 `ConcurrentHashMap`** 提升为 SQLite 持久化，后端重启后绑定仍然有效；同时删 DataTalk session 时级联删 OpenCode session。

**Architecture:** 无 schema 变更（V1 已经有 `sessions.opencode_sid TEXT` 列、`SessionRecord.openCodeSid` 字段、`SessionRepository.upsert` 已写入），**接线未接**：`SessionService.create` 固定塞 null、`ChannelService.sendMessage` 只查内存 map 不查 DB、启动时 `OpenCodeSessionMap` 不预热。本计划补齐接线 + 新增写回 + 级联删。

**Tech Stack:** Spring Boot、JdbcTemplate、WireMock、JUnit 5；依赖不变。

---

## 背景（现况盘点）

| 组件 | 状态 | 需要做的事 |
|------|------|-----------|
| `sessions.opencode_sid` SQL 列 | ✅ 已存在（V1） | — |
| `SessionRecord.openCodeSid` 字段 | ✅ 已存在 | — |
| `SessionRepository.upsert` 写 opencode_sid | ✅ 已写 | 加独立 `updateOpenCodeSid(id, ocSid, now)` 方法供 ChannelService 调 |
| `SessionService.create` 写 opencode_sid | ❌ 总是写 null | 保持 null；首次发消息再 lazy 写 |
| `OpenCodeSessionMap` 启动加载 | ❌ 始终空 | Adapter 加 `ApplicationRunner` 从 DB 预热 |
| `ChannelService.sendMessage` 查 DB | ❌ 只查内存 map | 先从 SessionRecord 拿 ocSid；空才 `createOpenCodeSession` + 写 DB + bind map |
| `SessionService.delete` 级联 OpenCode | ❌ 只删本地 | 读 record 拿 ocSid；调 OpenCodeGateway.delete；sessionMap.unbind |
| `OpenCodeGateway` delete 能力 | ❌ 缺失 | 新增 `SessionDeleter` 接口 + `deleteOpenCodeSession(ocSid)` 方法 |
| `OpenCodeHttpClient.deleteSession` | ❌ 缺失 | 新增 `DELETE /session/:id`（OpenCode 官方 API，参考 `open-db-studio/src-tauri/src/agent/client.rs:61-78`）|

## 范围

**改动文件：**

- `data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRepository.java` — 加 `updateOpenCodeSid`
- `data-talk-application/src/main/java/com/datatalk/application/channel/ChannelService.java` — sendMessage 读 DB 分支
- `data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java` — delete 级联
- `data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeGateway.java` — 新增 SessionDeleter + deleteOpenCodeSession
- `data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java` — 新增 `deleteSession`
- `data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java` — 绑 deleter；加 `ApplicationRunner` 预热 SessionMap
- 测试（新增/修改）：
    - `SessionRepositoryTest` — 新方法
    - `ChannelServiceTest` — sendMessage 两条路径（DB 有 / DB 无）
    - `SessionServiceTest` — delete 级联调 deleter
    - `OpenCodeHttpClientIT` — WireMock 验 `DELETE /session/:id`
    - `OpenCodeSessionMapInitializerTest`（新文件）— 启动从 DB 预热

**不改：**

- Flyway migration（列已存在）
- `OpenCodeSessionMap` 本身（接口稳定；仅新增启动时调 bind）
- `OpenCodeEventLoop`（仍用 sessionMap.dataTalkFor；预热后即可命中）
- 前端
- `rename` / `applyAutoTitle` 等路径——它们不触碰 opencode_sid

---

## Task 1：SessionRepository.updateOpenCodeSid

**Files:** `data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRepository.java`, `data-talk-application/src/test/java/com/datatalk/application/persistence/SessionRepositoryTest.java`

- [x] **Step 1**: 方法签名
    ```java
    public int updateOpenCodeSid(String id, String openCodeSid, long now) {
        return jdbc.update(
            "UPDATE sessions SET opencode_sid = ?, updated_at = ? WHERE id = ?",
            openCodeSid, now, id);
    }
    ```
- [x] **Step 2**: 单测：upsert 一行（opencode_sid=null）→ `updateOpenCodeSid` → `findById` 验值 + updatedAt 有推进

---

## Task 2：ChannelService.sendMessage 读 DB

**Files:** `ChannelService.java`, `ChannelServiceTest.java`

- [x] **Step 1**: 现状 `sendMessage` 里
    ```java
    String ocSid = sessionMap.openCodeFor(sessionId);
    if (ocSid == null) {
        ocSid = gateway.createOpenCodeSession();
        sessionMap.bind(sessionId, ocSid);
    }
    ```
    改为：
    ```java
    SessionRecord rec = sessions.findById(sessionId).orElseThrow(...);
    String ocSid = rec.openCodeSid();
    if (ocSid == null || ocSid.isBlank()) {
        ocSid = sessionMap.openCodeFor(sessionId); // fallback memory cache
    }
    if (ocSid == null || ocSid.isBlank()) {
        ocSid = gateway.createOpenCodeSession();
        sessions.updateOpenCodeSid(sessionId, ocSid, now);
        sessionMap.bind(sessionId, ocSid);
    } else {
        sessionMap.bind(sessionId, ocSid); // idempotent; ensures event loop reverse lookup works
    }
    ```
    注意：最上面的 `sessions.findById(sessionId).isEmpty()` 守卫保留——实际上可以复用 `rec` 避免两次查询。
- [x] **Step 2**: 单测 A：rec.openCodeSid=null → verify `gateway.createOpenCodeSession` 被调 + `sessions.updateOpenCodeSid` 被调 + `sessionMap.bind` 被调
- [x] **Step 3**: 单测 B：rec.openCodeSid="oc-existing" → verify **不** 调 `gateway.createOpenCodeSession`，body 被 `gateway.forwardUserMessage("oc-existing", ...)`

---

## Task 3：OpenCodeGateway + HttpClient 新增 delete

**Files:** `OpenCodeGateway.java`, `OpenCodeHttpClient.java`, `OpenCodeGatewayBeans.java` + 测试

- [x] **Step 1**: `OpenCodeGateway` 新增内部接口 + 方法
    ```java
    public interface SessionDeleter { void delete(String openCodeSessionId); }
    // ctor 增加 SessionDeleter 参数
    public void deleteOpenCodeSession(String ocSid) { deleter.delete(ocSid); }
    ```
- [x] **Step 2**: `OpenCodeHttpClient` 新增
    ```java
    public void deleteSession(String sessionId) {
        wc.delete().uri("/session/{id}", sessionId)
            .retrieve().toBodilessEntity().block();
    }
    ```
- [x] **Step 3**: `OpenCodeGatewayBeans` 组装 Bean 时把 `httpClient::deleteSession` 作为 `SessionDeleter` 传入 gateway
- [x] **Step 4**: WireMock 单测验证 `DELETE /session/ses_xxx` 被调用一次；4xx 时抛出（由 `.retrieve()` 默认语义覆盖）

---

## Task 4：SessionService.delete 级联

**Files:** `SessionService.java`, `SessionServiceTest.java`

- [x] **Step 1**: 注入 `OpenCodeGateway` + `OpenCodeSessionMap`
- [x] **Step 2**: delete 流程
    ```java
    SessionRecord rec = repo.findById(id).orElseThrow(...);
    repo.deleteById(id);                        // 本地先删（FK cascade messages 等）
    String ocSid = rec.openCodeSid();
    if (ocSid != null && !ocSid.isBlank()) {
        try { gateway.deleteOpenCodeSession(ocSid); } catch (Exception e) {
            log.warn("[session] OpenCode side delete failed for {}: {}", ocSid, e.toString());
        }
        sessionMap.unbind(id);
    }
    ```
    顺序刻意：本地 DELETE 先，OpenCode 删失败只 warn 不抛——保证本地 UI 已经删掉，OpenCode 的孤儿会话可留待 admin 清理；比反过来更不容易让用户看到"删除失败但实际删了一半"的体验。
- [x] **Step 3**: 单测：rec.openCodeSid="oc-1" → 删完 verify `gateway.deleteOpenCodeSession("oc-1")` + `sessionMap.unbind("id")`；rec.openCodeSid=null → 不调 gateway

---

## Task 5：启动预热 OpenCodeSessionMap

**Files:** `data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java`（或新文件）

- [x] **Step 1**: 加 `@Bean ApplicationRunner` 或 `@Component`：
    ```java
    @Component
    public class OpenCodeSessionMapInitializer implements ApplicationRunner {
        // ctor(SessionRepository, OpenCodeSessionMap)
        @Override public void run(ApplicationArguments args) {
            for (SessionRecord s : repo.listAll()) {
                if (s.openCodeSid() != null && !s.openCodeSid().isBlank()) {
                    map.bind(s.id(), s.openCodeSid());
                }
            }
            log.info("[session-map] preloaded {} bindings from DB", count);
        }
    }
    ```
- [x] **Step 2**: 测试——构造一个 DB 里有 N 行、其中 K 行非空 ocSid，Runner 跑完后 `map.openCodeFor(id)` 命中 K 次

---

## Task 6：回归 + install + 收尾

- [x] **Step 1**: `cd server && mvn verify`
- [x] **Step 2**: `mvn -pl data-talk-application install -am -DskipTests`
- [x] **Step 3**: 手工 smoke：重启后端 → 发"你好"→ 收 AI 回复；再手动 kill 后端 → 重启 → 同一个 DataTalk session 继续发"你第一句说了什么"→ AI 应该记得第一句
- [x] **Step 4**: 把本计划从 index.md 活跃搬到已完成；在 CLAUDE.md / ARCHITECTURE.md 相关章节（如果有）补充"ocSid 持久化"一条

---

## 验收

- [x] `mvn verify` 全绿
- [x] 后端重启后同一 session 继续对话，AI 仍知道之前上下文（多轮不失忆）
- [x] 删 session 后 OpenCode 本地也没了这个 session（可用 `curl http://127.0.0.1:4096/session` 验证）
- [x] 启动日志出现 `[session-map] preloaded N bindings from DB`
- [x] `docs/exec-plans/index.md` 更新

## 风险与回滚

- **风险 1**：启动预热失败（DB corruption / schema mismatch）会阻塞整个 Spring context 起不来。Runner 内部 try/catch 每行 bind，失败只 warn，不让整个启动挂掉。
- **风险 2**：并发首次发消息（两个 request 同时看到 opencode_sid=null）可能创建双 OpenCode session，最后 `updateOpenCodeSid` 覆盖一个。对 dev 单用户场景可接受；如未来要解决，在 ChannelService 里 synchronize on sessionId。
- **风险 3**：OpenCode 端 DELETE 失败造成孤儿（磁盘堆积）。接受——本地 UX 优先；可加 P2 任务做定期对账。
- **回滚**：单 commit；`git revert` 可完全恢复。回滚后仍保留已修的 outbound schema / /global/event，只是重新变为"重启丢失 context"的状态。
