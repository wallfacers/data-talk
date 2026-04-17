# OpenCode Session Title Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打通 OpenCode `session.*` 事件家族翻译链路，让 `session.updated` 推送的智能标题自动同步到 DataTalk 本地并刷新前端；保留用户手动 `renameSession` 的锁定优先级。

**Architecture:** 后端订阅 OpenCode `/event` SSE → `OpenCodeEventLoop` 解析 session-level 事件 → `OpenCodeEventTranslator` 翻译为 `DtEvent` 并对 `session.updated` 触发 `SessionTitleSyncer.apply` 持久化到 SQLite（仅当 `title_locked=0`）→ 经 `SessionBus` 广播到客户端 SSE → 前端 `use-channel.ts` sink 接 `session.meta.updated` 事件并 `invalidateQueries(['sessions'])` 刷新 ChatHeader。

**Tech Stack:** Spring Boot 3.5 / Java 21 / JdbcTemplate / Flyway SQLite / Jackson / JUnit 5 / AssertJ / WireMock 3.x — React 19 / TanStack Query / Zustand / vitest

**Spec:** [docs/product-specs/2026-04-18-opencode-session-title-sync-design.md](../product-specs/2026-04-18-opencode-session-title-sync-design.md)

---

## Task 1: V4 迁移 + `SessionRecord` 加 titleLocked

**Files:**
- Create: `server/data-talk-infrastructure/src/main/resources/db/migration/V4__session_title_locked.sql`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRecord.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRepository.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/persistence/SessionRepositoryTest.java`

- [ ] **Step 1: 写迁移脚本**

```sql
-- V4__session_title_locked.sql
ALTER TABLE sessions ADD COLUMN title_locked INTEGER NOT NULL DEFAULT 0;
```

- [ ] **Step 2: 扩展 `SessionRecord`**

```java
package com.datatalk.application.persistence;

public record SessionRecord(
    String id,
    String connectionId,
    String title,
    boolean hasEverSent,
    String openCodeSid,
    long createdAt,
    long updatedAt,
    boolean titleLocked
) {}
```

- [ ] **Step 3: 扩展 `SessionRepository` MAPPER 和 upsert**

`SessionRepository.java` 内替换 `MAPPER` 和 `upsert` 为下列代码，并新增 `applyAutoTitle`、`updateTitleAndLock` 方法：

```java
private static final RowMapper<SessionRecord> MAPPER = (rs, i) -> new SessionRecord(
    rs.getString("id"),
    rs.getString("connection_id"),
    rs.getString("title"),
    rs.getInt("has_ever_sent") == 1,
    rs.getString("opencode_sid"),
    rs.getLong("created_at"),
    rs.getLong("updated_at"),
    rs.getInt("title_locked") == 1
);

public void upsert(SessionRecord s) {
    jdbc.update("""
        INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
        VALUES(?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
          connection_id = excluded.connection_id,
          title         = excluded.title,
          has_ever_sent = excluded.has_ever_sent,
          opencode_sid  = excluded.opencode_sid,
          updated_at    = excluded.updated_at,
          title_locked  = excluded.title_locked
        """,
        s.id(), s.connectionId(), s.title(), s.hasEverSent() ? 1 : 0,
        s.openCodeSid(), s.createdAt(), s.updatedAt(), s.titleLocked() ? 1 : 0
    );
}

/** 仅当 title_locked=0 时写入；返回受影响行数（0 表示被锁定跳过）。 */
public int applyAutoTitle(String id, String title, long now) {
    return jdbc.update(
        "UPDATE sessions SET title = ?, updated_at = ? WHERE id = ? AND title_locked = 0",
        title, now, id);
}

/** 原子设置 title + 锁定；替代分离的 updateTitle + lockTitle 避免竞态。 */
public int updateTitleAndLock(String id, String title, long now) {
    return jdbc.update(
        "UPDATE sessions SET title = ?, title_locked = 1, updated_at = ? WHERE id = ?",
        title, now, id);
}
```

保留原 `updateTitle` 方法以兼容，但 `SessionService.rename` 将在 Task 2 改用 `updateTitleAndLock`。

- [ ] **Step 4: 修所有构造 `SessionRecord` 的调用点**

`SessionService.create` 里构造改成：

```java
SessionRecord rec = new SessionRecord(id, connectionId, safeTitle, false, null, now, now, false);
```

`SessionService.rename` 里构造改成（Task 2 会再改此方法，这里先补构造参数让编译通过）：

```java
return new SessionRecord(existing.id(), existing.connectionId(), title,
    existing.hasEverSent(), existing.openCodeSid(), existing.createdAt(), now,
    existing.titleLocked());
```

测试类 `ChannelServiceTest.java` 和 `ChannelServiceModelParamTest.java` 有 3 处 `new SessionRecord("s1", "test", "T", false, null, 100L, 100L)` 和 1 处 `new SessionRecord("s-1", null, "T", false, null, 100L, 100L)`，全部末尾加 `, false`：

```java
new SessionRecord("s1", "test", "T", false, null, 100L, 100L, false)
new SessionRecord("s-1", null, "T", false, null, 100L, 100L, false)
```

- [ ] **Step 5: 写 SessionRepositoryTest（如不存在则新建）**

```java
package com.datatalk.application.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class SessionRepositoryTest {

    private SessionRepository repo;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DataSource ds = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("classpath:schema-for-tests/sessions-v4.sql")
            .build();
        jdbc = new JdbcTemplate(ds);
        repo = new SessionRepository(jdbc);
    }

    @Test
    void applyAutoTitle_updatesWhenUnlocked() {
        repo.upsert(new SessionRecord("s1", "c1", "新会话", false, null, 100L, 100L, false));
        int rows = repo.applyAutoTitle("s1", "AI 生成标题", 200L);
        assertThat(rows).isEqualTo(1);
        assertThat(repo.findById("s1")).get()
            .extracting(SessionRecord::title).isEqualTo("AI 生成标题");
    }

    @Test
    void applyAutoTitle_skipsWhenLocked() {
        repo.upsert(new SessionRecord("s1", "c1", "手动命名", false, null, 100L, 100L, true));
        int rows = repo.applyAutoTitle("s1", "AI 生成标题", 200L);
        assertThat(rows).isEqualTo(0);
        assertThat(repo.findById("s1")).get()
            .extracting(SessionRecord::title).isEqualTo("手动命名");
    }

    @Test
    void updateTitleAndLock_setsBothAtomically() {
        repo.upsert(new SessionRecord("s1", "c1", "新会话", false, null, 100L, 100L, false));
        int rows = repo.updateTitleAndLock("s1", "我的查询", 200L);
        assertThat(rows).isEqualTo(1);
        SessionRecord r = repo.findById("s1").orElseThrow();
        assertThat(r.title()).isEqualTo("我的查询");
        assertThat(r.titleLocked()).isTrue();
    }
}
```

配套脚本 `server/data-talk-application/src/test/resources/schema-for-tests/sessions-v4.sql`（如目录不存在则 `mkdir`）：

```sql
CREATE TABLE sessions (
  id TEXT PRIMARY KEY,
  connection_id TEXT,
  title TEXT NOT NULL,
  has_ever_sent INTEGER NOT NULL DEFAULT 0,
  opencode_sid TEXT,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  title_locked INTEGER NOT NULL DEFAULT 0
);
```

- [ ] **Step 6: 运行测试 + 编译全量**

```bash
cd server && mvn -pl data-talk-application test -Dtest=SessionRepositoryTest
cd server && mvn install -pl data-talk-application -am -DskipTests
```

Expected: SessionRepositoryTest 3 tests PASS；install 无编译错误。

- [ ] **Step 7: 提交**

```bash
git add server/data-talk-infrastructure/src/main/resources/db/migration/V4__session_title_locked.sql \
        server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRecord.java \
        server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRepository.java \
        server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java \
        server/data-talk-application/src/test/java/com/datatalk/application/persistence/SessionRepositoryTest.java \
        server/data-talk-application/src/test/resources/schema-for-tests/sessions-v4.sql \
        server/data-talk-application/src/test/java/com/datatalk/application/channel/ChannelServiceTest.java \
        server/data-talk-application/src/test/java/com/datatalk/application/channel/ChannelServiceModelParamTest.java
git commit -m "feat(persistence): add title_locked column + applyAutoTitle repository method"
```

---

## Task 2: `SessionService.rename` 原子化 + `SessionDto.titleLocked` 暴露

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/dto/SessionDto.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/session/SessionServiceTest.java`（如已存在则扩展，否则新建）

- [ ] **Step 1: 写 SessionServiceTest rename 行为**

```java
@Test
void rename_locksTitleAtomically() {
    SessionService svc = new SessionService(repo, Clock.fixed(Instant.ofEpochMilli(500L), ZoneOffset.UTC));
    repo.upsert(new SessionRecord("s1", "c1", "新会话", false, null, 100L, 100L, false));

    SessionRecord result = svc.rename("s1", "我的查询");

    assertThat(result.title()).isEqualTo("我的查询");
    assertThat(result.titleLocked()).isTrue();
    SessionRecord persisted = repo.findById("s1").orElseThrow();
    assertThat(persisted.titleLocked()).isTrue();
}
```

（如 SessionServiceTest 不存在，同样用 H2 + schema-for-tests/sessions-v4.sql 初始化 repo，照 SessionRepositoryTest 做法。）

- [ ] **Step 2: 运行测试确认失败（rename 尚未 set title_locked）**

```bash
cd server && mvn -pl data-talk-application test -Dtest=SessionServiceTest#rename_locksTitleAtomically
```

Expected: FAIL 断言 `titleLocked` 为 `true`，实际为 `false`。

- [ ] **Step 3: 改 SessionService.rename 用 updateTitleAndLock**

```java
public SessionRecord rename(String id, String title) {
    if (title == null || title.isBlank()) {
        throw new IllegalArgumentException("title must not be blank");
    }
    SessionRecord existing = repo.findById(id)
        .orElseThrow(() -> new NoSuchElementException("session not found: " + id));
    long now = clock.millis();
    repo.updateTitleAndLock(id, title, now);
    return new SessionRecord(existing.id(), existing.connectionId(), title,
        existing.hasEverSent(), existing.openCodeSid(), existing.createdAt(), now, true);
}
```

- [ ] **Step 4: 扩展 SessionDto 加 titleLocked**

```java
package com.datatalk.dto;

public record SessionDto(
    String id,
    String connectionId,
    String title,
    boolean hasEverSent,
    long createdAt,
    long updatedAt,
    boolean titleLocked
) {}
```

改 `SessionController.toDto`：

```java
private static SessionDto toDto(SessionRecord r) {
    return new SessionDto(r.id(), r.connectionId(), r.title(),
        r.hasEverSent(), r.createdAt(), r.updatedAt(), r.titleLocked());
}
```

- [ ] **Step 5: 运行测试 + install**

```bash
cd server && mvn -pl data-talk-application test -Dtest=SessionServiceTest
cd server && mvn install -pl data-talk-application,data-talk-adapter -am -DskipTests
```

Expected: SessionServiceTest PASS；install 无编译错误。

- [ ] **Step 6: 提交**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java \
        server/data-talk-application/src/main/java/com/datatalk/dto/SessionDto.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java \
        server/data-talk-application/src/test/java/com/datatalk/application/session/SessionServiceTest.java
git commit -m "feat(session): rename atomically locks title + expose titleLocked in DTO"
```

---

## Task 3: 新增 `SessionInfo` record 和 `OcEvent` session.* 分支

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/SessionInfo.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OcEvent.java`
- Test: （不新增测试，Task 5 Translator 扩展测试会覆盖）

- [ ] **Step 1: 创建 SessionInfo**

```java
package com.datatalk.application.opencode;

/**
 * OpenCode 的 session 对象最小视图。对应 OpenCode `session.*` 事件 payload 里的 {@code info} 节点。
 */
public record SessionInfo(String id, String title, long version) {}
```

- [ ] **Step 2: 扩展 OcEvent**

`OcEvent.java` 在 `SessionStatus` 之后追加 7 个 record（其余分支保持不变）：

```java
record SessionCreated(SessionInfo info) implements OcEvent {}
record SessionUpdated(SessionInfo info) implements OcEvent {}
record SessionDeleted(SessionInfo info) implements OcEvent {}
record SessionIdle(SessionInfo info) implements OcEvent {}
record SessionError(SessionInfo info, String error) implements OcEvent {}
record SessionCompacted(SessionInfo info) implements OcEvent {}
record SessionDiff(SessionInfo info, java.util.Map<String, Object> payload) implements OcEvent {}
```

- [ ] **Step 3: 编译确认**

```bash
cd server && mvn compile -pl data-talk-application -q
```

Expected: 无编译错误。现有 switch-exhaustive 的地方（`OpenCodeEventTranslator.translate`）会报 "switch doesn't cover all cases" 错 —— 预期行为，Task 5 修。本步如果直接 compile 会 failed，使用 `-Dmaven.compile.failOnError=false` 仅查看是否 class 本身语法正确：

```bash
cd server && mvn compile -pl data-talk-application -q -Dmaven.compile.failOnError=false
```

- [ ] **Step 4: 提交（延后至 Task 5 一同提交，避免中间态编译失败）**

本 Task 不单独 commit，随 Task 5 一起提交，保持主干每个 commit 都可编译。

---

## Task 4: 扩展 `DtEvent` 新增 7 个 record

**Files:**
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java`
- Test: `server/data-talk-domain/src/test/java/com/datatalk/domain/event/DtEventJsonTest.java`（如不存在则新建；如已存在则扩展）

- [ ] **Step 1: 扩展 DtEvent**

在 `SessionStatus` 之后追加 7 个 record：

```java
@JsonTypeName("session.created")
record SessionCreated(String sessionId, String title, long version) implements DtEvent {}
@JsonTypeName("session.meta.updated")
record SessionMetaUpdated(String sessionId, String title, boolean titleLocked, long version) implements DtEvent {}
@JsonTypeName("session.deleted")
record SessionDeleted(String sessionId) implements DtEvent {}
@JsonTypeName("session.idle")
record SessionIdle(String sessionId) implements DtEvent {}
@JsonTypeName("session.error")
record SessionError(String sessionId, String error) implements DtEvent {}
@JsonTypeName("session.compacted")
record SessionCompacted(String sessionId) implements DtEvent {}
@JsonTypeName("session.diff")
record SessionDiff(String sessionId, java.util.Map<String, Object> payload) implements DtEvent {}
```

> 命名说明：OpenCode 原事件是 `session.updated`，但 DtEvent 已有 `MessageUpdated`，为避免语义混淆，`SessionUpdated` 在 DtEvent 侧改名为 `SessionMetaUpdated`，前端 SSE type 对应 `session.meta.updated`。

- [ ] **Step 2: 写 JSON round-trip 测试**

```java
package com.datatalk.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DtEventJsonTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void sessionMetaUpdated_roundTrip() throws Exception {
        DtEvent.SessionMetaUpdated e = new DtEvent.SessionMetaUpdated("s1", "AI 标题", false, 2L);
        String json = om.writeValueAsString(e);
        assertThat(json).contains("\"type\":\"session.meta.updated\"");
        DtEvent back = om.readValue(json, DtEvent.class);
        assertThat(back).isEqualTo(e);
    }

    @Test
    void sessionIdle_roundTrip() throws Exception {
        DtEvent.SessionIdle e = new DtEvent.SessionIdle("s1");
        String json = om.writeValueAsString(e);
        assertThat(json).contains("\"type\":\"session.idle\"");
        assertThat(om.readValue(json, DtEvent.class)).isEqualTo(e);
    }

    @Test
    void sessionError_roundTrip() throws Exception {
        DtEvent.SessionError e = new DtEvent.SessionError("s1", "boom");
        String json = om.writeValueAsString(e);
        assertThat(json).contains("\"type\":\"session.error\"");
        assertThat(om.readValue(json, DtEvent.class)).isEqualTo(e);
    }

    @Test
    void sessionDiff_roundTrip() throws Exception {
        DtEvent.SessionDiff e = new DtEvent.SessionDiff("s1", Map.of("a", 1));
        String json = om.writeValueAsString(e);
        assertThat(json).contains("\"type\":\"session.diff\"");
        assertThat(om.readValue(json, DtEvent.class)).isEqualTo(e);
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
cd server && mvn -pl data-talk-domain test -Dtest=DtEventJsonTest
```

Expected: 4 PASS。

- [ ] **Step 4: 提交**

```bash
git add server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java \
        server/data-talk-domain/src/test/java/com/datatalk/domain/event/DtEventJsonTest.java
git commit -m "feat(domain): add 7 session.* DtEvent variants for OpenCode event family"
```

---

## Task 5: 新增 `SessionTitleSyncer` + `OpenCodeEventTranslator` 扩展

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/SessionTitleSyncer.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventTranslator.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/SessionTitleSyncerTest.java`（新建）
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventTranslatorTest.java`（扩展）

- [ ] **Step 1: 写 SessionTitleSyncerTest**

```java
package com.datatalk.application.opencode;

import com.datatalk.application.persistence.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionTitleSyncerTest {

    private SessionRepository repo;
    private OpenCodeSessionMap sessionMap;
    private SessionTitleSyncer syncer;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(SessionRepository.class);
        sessionMap = new OpenCodeSessionMap();
        syncer = new SessionTitleSyncer(repo, sessionMap,
            Clock.fixed(Instant.ofEpochMilli(500L), ZoneOffset.UTC));
    }

    @Test
    void apply_writesWhenMapped() {
        sessionMap.bind("dt-1", "oc-1");
        syncer.apply("oc-1", "AI 标题");
        verify(repo).applyAutoTitle(eq("dt-1"), eq("AI 标题"), eq(500L));
    }

    @Test
    void apply_noOpWhenOcSessionNotMapped() {
        syncer.apply("oc-unknown", "AI 标题");
        verify(repo, never()).applyAutoTitle(Mockito.anyString(), Mockito.anyString(), anyLong());
    }

    @Test
    void apply_tolerantOfRepositoryException() {
        sessionMap.bind("dt-1", "oc-1");
        when(repo.applyAutoTitle(Mockito.anyString(), Mockito.anyString(), anyLong()))
            .thenThrow(new RuntimeException("db down"));
        // 不应向外抛：syncer 容错 translator/event loop 才不被阻断
        syncer.apply("oc-1", "AI 标题");
        verify(repo).applyAutoTitle(eq("dt-1"), eq("AI 标题"), eq(500L));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
cd server && mvn -pl data-talk-application test -Dtest=SessionTitleSyncerTest
```

Expected: FAIL "cannot find symbol class SessionTitleSyncer"。

- [ ] **Step 3: 创建 SessionTitleSyncer**

```java
package com.datatalk.application.opencode;

import com.datatalk.application.persistence.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 把 OpenCode 推送的 session title 同步到本地 SessionRepository。
 * 仅在 {@code title_locked=0} 时覆盖；外层调用方（translator）不应感知失败 —
 * 这里捕获并记录所有异常，让事件翻译链路继续运行。
 */
@Component
public class SessionTitleSyncer {

    private static final Logger log = LoggerFactory.getLogger(SessionTitleSyncer.class);

    private final SessionRepository repo;
    private final OpenCodeSessionMap sessionMap;
    private final Clock clock;

    public SessionTitleSyncer(SessionRepository repo, OpenCodeSessionMap sessionMap, Clock clock) {
        this.repo = repo;
        this.sessionMap = sessionMap;
        this.clock = clock;
    }

    /** 经 OpenCodeSessionMap 查 dtSessionId，若能映射则尝试 applyAutoTitle；title_locked=1 时 DB 层自行跳过。 */
    public void apply(String ocSessionId, String newTitle) {
        if (ocSessionId == null || newTitle == null || newTitle.isBlank()) return;
        String dtSessionId = sessionMap.dataTalkFor(ocSessionId);
        if (dtSessionId == null) return;
        try {
            repo.applyAutoTitle(dtSessionId, newTitle, clock.millis());
        } catch (RuntimeException e) {
            log.warn("applyAutoTitle failed for session={} title='{}'", dtSessionId, newTitle, e);
        }
    }
}
```

- [ ] **Step 4: 运行 SessionTitleSyncerTest 验证通过**

```bash
cd server && mvn -pl data-talk-application test -Dtest=SessionTitleSyncerTest
```

Expected: 3 PASS。

- [ ] **Step 5: 扩展 OpenCodeEventTranslator**

替换类体为下列代码（注入 syncer，translate 扩展 7 分支；保留 seenParts/seenMessages 去重逻辑不变）：

```java
package com.datatalk.application.opencode;

import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.part.Part;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OpenCodeEventTranslator {

    private final SessionTitleSyncer titleSyncer;
    private final Map<String, Set<String>> seenParts = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> seenMessages = new ConcurrentHashMap<>();

    public OpenCodeEventTranslator(SessionTitleSyncer titleSyncer) {
        this.titleSyncer = titleSyncer;
    }

    public List<DtEvent> translate(String sessionId, OcEvent in) {
        return switch (in) {
            case OcEvent.ServerConnected c -> Collections.emptyList();

            case OcEvent.SessionStatus s ->
                List.of(new DtEvent.SessionStatus(s.status(), s.retryInfo()));

            case OcEvent.SessionCreated e ->
                List.of(new DtEvent.SessionCreated(sessionId, e.info().title(), e.info().version()));

            case OcEvent.SessionUpdated e -> {
                titleSyncer.apply(e.info().id(), e.info().title());
                // titleLocked 字段先透传 false；真实状态由客户端 GET /api/sessions 查询返回，
                // 这里的 DtEvent 只做"有 title 更新发生"的语义广播 + 触发前端 invalidate
                yield List.of(new DtEvent.SessionMetaUpdated(
                    sessionId, e.info().title(), false, e.info().version()));
            }

            case OcEvent.SessionDeleted e ->
                List.of(new DtEvent.SessionDeleted(sessionId));

            case OcEvent.SessionIdle e ->
                List.of(new DtEvent.SessionIdle(sessionId));

            case OcEvent.SessionError e ->
                List.of(new DtEvent.SessionError(sessionId, e.error()));

            case OcEvent.SessionCompacted e ->
                List.of(new DtEvent.SessionCompacted(sessionId));

            case OcEvent.SessionDiff e ->
                List.of(new DtEvent.SessionDiff(sessionId, e.payload()));

            case OcEvent.MessageUpdated m -> {
                Set<String> msgs = seenMessages.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());
                if (msgs.add(m.message().id())) {
                    yield List.of(new DtEvent.MessageCreated(m.message()));
                }
                yield List.of(new DtEvent.MessageUpdated(m.message()));
            }

            case OcEvent.MessagePartUpdated p -> {
                Set<String> parts = seenParts.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());
                String partId = p.part().id();
                if (parts.add(partId)) {
                    yield List.of(new DtEvent.MessagePartCreated(p.part()));
                }
                yield List.of(new DtEvent.MessagePartUpdated(p.part()));
            }

            case OcEvent.MessagePartDelta d ->
                List.of(new DtEvent.MessagePartDelta(d.partId(), d.field(), d.delta()));

            case OcEvent.MessagePartRemoved r ->
                List.of(new DtEvent.MessagePartRemoved(r.partId()));

            case OcEvent.Unknown u -> Collections.emptyList();
        };
    }

    public void forget(String sessionId) {
        seenParts.remove(sessionId);
        seenMessages.remove(sessionId);
    }
}
```

- [ ] **Step 6: 扩展 OpenCodeEventTranslatorTest**

在现有测试类里，把 `new OpenCodeEventTranslator()` 改为 `new OpenCodeEventTranslator(syncer)`，新增 mock syncer：

```java
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;

// 类字段
private final SessionTitleSyncer syncer = Mockito.mock(SessionTitleSyncer.class);
private final OpenCodeEventTranslator tr = new OpenCodeEventTranslator(syncer);
```

新增 4 个测试：

```java
@Test
void sessionUpdatedTriggersSyncerAndEmitsMetaUpdated() {
    var info = new SessionInfo("oc-1", "AI 标题", 2L);
    List<DtEvent> out = tr.translate("dt-1", new OcEvent.SessionUpdated(info));
    verify(syncer).apply(eq("oc-1"), eq("AI 标题"));
    assertThat(out).singleElement().isInstanceOf(DtEvent.SessionMetaUpdated.class);
}

@Test
void sessionIdleDoesNotTriggerSyncer() {
    tr.translate("dt-1", new OcEvent.SessionIdle(new SessionInfo("oc-1", null, 1L)));
    verifyNoInteractions(syncer);
}

@Test
void sessionErrorIsTranslated() {
    List<DtEvent> out = tr.translate("dt-1",
        new OcEvent.SessionError(new SessionInfo("oc-1", null, 1L), "boom"));
    assertThat(out).singleElement().isInstanceOf(DtEvent.SessionError.class);
    assertThat(((DtEvent.SessionError) out.get(0)).error()).isEqualTo("boom");
}

@Test
void sessionCreatedIsTranslated() {
    var info = new SessionInfo("oc-1", "T", 1L);
    List<DtEvent> out = tr.translate("dt-1", new OcEvent.SessionCreated(info));
    assertThat(out).singleElement().isInstanceOf(DtEvent.SessionCreated.class);
}
```

- [ ] **Step 7: 运行全部扩展测试**

```bash
cd server && mvn -pl data-talk-application test -Dtest=OpenCodeEventTranslatorTest,SessionTitleSyncerTest
```

Expected: 全部 PASS。

- [ ] **Step 8: 提交**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/opencode/SessionInfo.java \
        server/data-talk-application/src/main/java/com/datatalk/application/opencode/OcEvent.java \
        server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventTranslator.java \
        server/data-talk-application/src/main/java/com/datatalk/application/opencode/SessionTitleSyncer.java \
        server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventTranslatorTest.java \
        server/data-talk-application/src/test/java/com/datatalk/application/opencode/SessionTitleSyncerTest.java
git commit -m "feat(opencode): translate session.* event family, sync title via SessionTitleSyncer"
```

---

## Task 6: `OpenCodeEventLoop` 扩展 parseOcEvent + extractSessionId

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventLoopTest.java`（扩展）

- [ ] **Step 1: 读现有 OpenCodeEventLoopTest 找 WireMock 用法**

```bash
cat server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventLoopTest.java
```

参照其模式。如果已有 `/event` mock，添加一个 session.updated frame case；没有则新建 test method。

- [ ] **Step 2: 扩展 parseOcEvent switch（替换现有方法）**

```java
private OcEvent parseOcEvent(String name, String json) {
    try {
        JsonNode node = json.isEmpty() ? om.createObjectNode() : om.readTree(json);
        return switch (name) {
            case "server.connected" -> new OcEvent.ServerConnected();
            case "session.status"   -> new OcEvent.SessionStatus(
                node.path("status").asText("idle"),
                om.convertValue(node.path("retryInfo"), Map.class)
            );
            case "session.created"   -> new OcEvent.SessionCreated(parseSessionInfo(node));
            case "session.updated"   -> new OcEvent.SessionUpdated(parseSessionInfo(node));
            case "session.deleted"   -> new OcEvent.SessionDeleted(parseSessionInfo(node));
            case "session.idle"      -> new OcEvent.SessionIdle(parseSessionInfo(node));
            case "session.error"     -> new OcEvent.SessionError(
                parseSessionInfo(node), node.path("error").asText(""));
            case "session.compacted" -> new OcEvent.SessionCompacted(parseSessionInfo(node));
            case "session.diff"      -> new OcEvent.SessionDiff(
                parseSessionInfo(node), om.convertValue(node, Map.class));
            case "message.updated"  -> new OcEvent.MessageUpdated(
                om.treeToValue(node.path("info"), Message.class));
            case "message.part.updated" -> new OcEvent.MessagePartUpdated(
                om.treeToValue(node.path("part"), Part.class));
            case "message.part.delta"   -> new OcEvent.MessagePartDelta(
                node.path("partID").asText(),
                node.path("field").asText(),
                node.path("delta").asText());
            case "message.part.removed" -> new OcEvent.MessagePartRemoved(
                node.path("partID").asText());
            default -> new OcEvent.Unknown(name, om.convertValue(node, Map.class));
        };
    } catch (Exception e) {
        return new OcEvent.Unknown(name, Map.of("parseError", e.getMessage()));
    }
}

private SessionInfo parseSessionInfo(JsonNode node) {
    JsonNode info = node.path("info");
    if (info.isMissingNode() || info.isNull()) {
        return new SessionInfo(null, null, 0L);
    }
    return new SessionInfo(
        info.path("id").asText(null),
        info.hasNonNull("title") ? info.path("title").asText() : null,
        info.path("version").asLong(0L)
    );
}
```

- [ ] **Step 3: 扩展 extractSessionId**

```java
private static String extractSessionId(OcEvent e) {
    return switch (e) {
        case OcEvent.MessageUpdated m    -> m.message().sessionId();
        case OcEvent.MessagePartUpdated p -> p.part().sessionID();
        case OcEvent.SessionCreated s    -> s.info().id();
        case OcEvent.SessionUpdated s    -> s.info().id();
        case OcEvent.SessionDeleted s    -> s.info().id();
        case OcEvent.SessionIdle s       -> s.info().id();
        case OcEvent.SessionError s      -> s.info().id();
        case OcEvent.SessionCompacted s  -> s.info().id();
        case OcEvent.SessionDiff s       -> s.info().id();
        case OcEvent.SessionStatus s     -> null;
        default                          -> null;
    };
}
```

- [ ] **Step 4: 扩展 OpenCodeEventLoopTest 加 session.updated 端到端用例**

参照现有 `parsesSseFramesIntoOcEvents` 的 WireMock 模式（dynamicPort + stubFor /event + awaitility）。在 `OpenCodeEventLoopTest` 类内追加：

```java
@Test
void sessionUpdatedReachesSessionBus() {
    String sse = """
        event: session.updated
        data: {"info":{"id":"oc-1","title":"AI 标题","version":2}}

        """;
    wm.stubFor(get(urlEqualTo("/event"))
        .willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(sse)));

    List<OcEvent> received = new ArrayList<>();
    OpenCodeSessionMap map = new OpenCodeSessionMap();
    map.bind("dt-1", "oc-1");

    SessionBus mockBus = Mockito.mock(SessionBus.class);
    SessionBusRegistry buses = Mockito.mock(SessionBusRegistry.class);
    when(buses.getOrCreate("dt-1")).thenReturn(mockBus);

    SessionTitleSyncer syncer = Mockito.mock(SessionTitleSyncer.class);
    OpenCodeEventTranslator tr = new OpenCodeEventTranslator(syncer);
    OpenCodeEventLoop loop = new OpenCodeEventLoop(
        "http://localhost:" + wm.port(), new ObjectMapper(), tr, buses, map, received::add);

    loop.start();
    await().atMost(Duration.ofSeconds(3)).until(() -> !received.isEmpty());
    loop.stop();

    assertThat(received.get(0)).isInstanceOf(OcEvent.SessionUpdated.class);
    Mockito.verify(syncer).apply("oc-1", "AI 标题");
    Mockito.verify(mockBus).publish(any(DtEvent.SessionMetaUpdated.class));
}
```

Imports 补充：`import static org.mockito.ArgumentMatchers.any;`（文件顶部已有 `any`，确认即可）。

- [ ] **Step 5: 运行测试 + install**

```bash
cd server && mvn -pl data-talk-application test -Dtest=OpenCodeEventLoopTest
cd server && mvn install -pl data-talk-application -am -DskipTests
```

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java \
        server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventLoopTest.java
git commit -m "feat(opencode): parse session.* SSE frames, route via OpenCodeSessionMap"
```

---

## Task 7: 前端同步生成类型 + `use-channel.ts` 接 `session.meta.updated`

**Files:**
- Generate: `client/src/types/generated/api.ts`（跑 `npm run gen:api`）
- Modify: `client/src/services/channel/use-channel.ts`
- Test: `client/src/services/channel/use-channel.test.ts`（新建）

- [ ] **Step 1: 启动后端并重新生成前端 types**

后端 `SessionDto` 已在 Task 2 加了 `titleLocked`。假设 repo 已有 `npm run gen:api` 脚本（`docs/exec-plans/index.md` 里提到 TD-006 已建立类型生成）。

```bash
cd server && mvn spring-boot:run -pl data-talk-adapter &
# 等后端起来（看到 "Started DataTalkApplication"）
cd client && npm run gen:api
# 停掉后端
kill %1
```

验证 `client/src/types/generated/api.ts` 的 `SessionDto` 现在有 `titleLocked: boolean`。

- [ ] **Step 2: 写 use-channel sink 测试**

```typescript
// client/src/services/channel/use-channel.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import { buildEventSink } from './use-channel'

describe('buildEventSink · session.meta.updated', () => {
  let qc: QueryClient
  beforeEach(() => {
    qc = new QueryClient()
  })

  it('triggers sessions query invalidation', () => {
    const spy = vi.spyOn(qc, 'invalidateQueries')
    // buildEventSink 内部引用了全局 queryClient；如是这种写法我们需通过模块 mock
    // 这里假设 buildEventSink 接受 qc 作为参数（若当前不是则 Step 3 要调整签名）
    const sink = buildEventSink('s1', null, qc)
    sink({ event: 'session.meta.updated', data: { sessionId: 's1', title: 'AI', titleLocked: false, version: 2 } } as any)
    expect(spy).toHaveBeenCalledWith({ queryKey: ['sessions'] })
  })
})
```

> 当前 `buildEventSink` 签名是 `(sessionId, client)`，Step 3 会改为 `(sessionId, client, queryClient)` 以便注入 mock。调用方 `useChannel` 里用 `useQueryClient()` 获取。

- [ ] **Step 3: 修改 `use-channel.ts`**

在 `buildEventSink` 函数签名追加 `queryClient` 参数并添加 `session.meta.updated` 分支：

```typescript
import type { QueryClient } from '@tanstack/react-query'
// ...

export function buildEventSink(
  sessionId: string,
  client: ChannelClient | null,
  queryClient: QueryClient,
) {
  return (evt: StreamEvent) => {
    const { event, data } = evt
    if (event === 'message.created') {
      // ... 原逻辑不变
    } else if (event === 'session.meta.updated') {
      queryClient.invalidateQueries({ queryKey: ['sessions'] })
    } else if (event === 'message.part.created' || event === 'message.part.updated') {
      // ... 原逻辑
    }
    // ... 其余不变
  }
}
```

在 `useChannel` 里：

```typescript
import { useQueryClient } from '@tanstack/react-query'

export function useChannel() {
  const queryClient = useQueryClient()
  // ...
  const sendMessage = useCallback(
    async (parts: any[]) => {
      if (!client || !sessionId) return
      setIsStreaming(true)
      enterSplit(sessionId)
      const sink = buildEventSink(sessionId, client, queryClient)
      // ...
    },
    [/* 加 queryClient 依赖 */]
  )
}
```

- [ ] **Step 4: 运行测试 + 类型检查**

```bash
cd client && npx vitest run src/services/channel/use-channel.test.ts
cd client && npx tsc --noEmit
```

Expected: 1 PASS；无类型错误。

- [ ] **Step 5: 提交**

```bash
git add client/src/types/generated/api.ts \
        client/src/services/channel/use-channel.ts \
        client/src/services/channel/use-channel.test.ts
git commit -m "feat(client): invalidate sessions query on session.meta.updated SSE event"
```

---

## Task 8: ChannelControllerIT 端到端集成验证

现有 `ChannelControllerIT` **未启动 fake OpenCode**（它直接跑 Spring Boot context，send_message 的 SSE 响应靠 `ChannelService` 内部事件 + 1000ms 恩典期）。要端到端测"OpenCode 推 session.updated → DB 持久化 + 前端 SSE"，最直接的做法是在 IT 里直接 `@Autowired` `OpenCodeEventTranslator` 作为"OpenCode 推送"的代理触发器，避免起外部 HTTP mock。

**Files:**
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/channel/ChannelControllerIT.java`

- [ ] **Step 1: 确认 setUp 构造 SessionRecord 已用 Task 1 的 8 参数版本**

Task 1 已改过 `setUp` 里的 `new SessionRecord("s-1", null, "T", false, null, 100L, 100L)` → `..., false)`。这里再确认一次编译通过。

- [ ] **Step 2: 追加 3 个测试 + 新的 @Autowired 字段**

在 ChannelControllerIT 类体顶部加新字段：

```java
@Autowired com.datatalk.application.opencode.OpenCodeEventTranslator translator;
@Autowired com.datatalk.application.opencode.OpenCodeSessionMap ocSessionMap;
@Autowired com.datatalk.application.session.SessionBusRegistry buses;
```

然后追加 3 个测试方法：

```java
@Test
void sessionUpdatedFromTranslatorSyncsTitle() {
    ocSessionMap.bind("s-1", "oc-1");
    var info = new com.datatalk.application.opencode.SessionInfo("oc-1", "AI 标题", 2L);
    translator.translate("s-1", new com.datatalk.application.opencode.OcEvent.SessionUpdated(info));

    SessionRecord reloaded = sessions.findById("s-1").orElseThrow();
    assertThat(reloaded.title()).isEqualTo("AI 标题");
    assertThat(reloaded.titleLocked()).isFalse();
}

@Test
void sessionUpdatedRespectsTitleLocked() {
    // 覆盖 s-1 为锁定状态
    sessions.upsert(new SessionRecord("s-1", null, "手动命名", false, null, 100L, 100L, true));
    ocSessionMap.bind("s-1", "oc-1");

    var info = new com.datatalk.application.opencode.SessionInfo("oc-1", "AI 标题", 2L);
    translator.translate("s-1", new com.datatalk.application.opencode.OcEvent.SessionUpdated(info));

    SessionRecord reloaded = sessions.findById("s-1").orElseThrow();
    assertThat(reloaded.title()).isEqualTo("手动命名");
    assertThat(reloaded.titleLocked()).isTrue();
}

@Test
void sendMessageForwardsSessionMetaUpdatedToClient() throws Exception {
    // 后台线程：客户端连上 SSE 后 publish SessionMetaUpdated 到 session bus
    new Thread(() -> {
        try { Thread.sleep(200); } catch (InterruptedException ignored) { return; }
        buses.getOrCreate("s-1").publish(
            new com.datatalk.domain.event.DtEvent.SessionMetaUpdated(
                "s-1", "AI 标题", false, 2L));
    }).start();

    String body = om.writeValueAsString(Map.of(
        "jsonrpc", "2.0", "id", "r1", "method", "send_message",
        "params", Map.of("parts", List.of(Map.of(
            "type", "text", "id", "p1", "sessionID", "s-1",
            "messageID", "ignored", "text", "hello", "metadata", Map.of()
        )))
    ));

    List<String> lines = new CopyOnWriteArrayList<>();
    client.post()
        .uri("/api/sessions/s-1/channel")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToFlux(String.class)
        .take(Duration.ofSeconds(2))
        .doOnNext(lines::add)
        .blockLast(Duration.ofSeconds(3));

    String all = String.join("\n", lines);
    assertThat(all).contains("session.meta.updated");
    assertThat(all).contains("\"title\":\"AI 标题\"");
}
```

- [ ] **Step 2: 运行 IT**

```bash
cd server && mvn -pl data-talk-adapter test -Dtest=ChannelControllerIT
```

Expected: PASS。

- [ ] **Step 3: 提交**

```bash
git add server/data-talk-adapter/src/test/java/com/datatalk/adapter/channel/ChannelControllerIT.java
git commit -m "test(channel): verify session.updated syncs title + emits session.meta.updated"
```

---

## Task 9: 前端 chat-header 测试 + 手动 E2E

**Files:**
- Modify: `client/src/features/session/chat-header.test.tsx`

- [ ] **Step 1: 扩展 chat-header.test.tsx**

参考现有 `chat-header.test.tsx` 的 setUp（mock `useSessions` + `useSessionStore.setState`）。加一个测试：

```tsx
import { rerender } from '@testing-library/react' // 如已用 render 的 rerender API
import * as useSessionsModule from './hooks/use-sessions'

it('reflects updated session title when useSessions returns new title', () => {
  // 初始 useSessions 返回 title="新会话"
  const spy = vi.spyOn(useSessionsModule, 'useSessions')
  spy.mockReturnValue({ data: [{ id: 's1', connectionId: 'c1', title: '新会话', hasEverSent: true, createdAt: 0, updatedAt: 0, titleLocked: false }] } as any)

  const { rerender } = render(<ChatHeader />)
  expect(screen.getByText('新会话')).toBeInTheDocument()

  // 模拟 invalidate 后 useSessions 返回新 title
  spy.mockReturnValue({ data: [{ id: 's1', connectionId: 'c1', title: 'AI 标题', hasEverSent: true, createdAt: 0, updatedAt: 0, titleLocked: false }] } as any)
  rerender(<ChatHeader />)
  expect(screen.getByText('AI 标题')).toBeInTheDocument()
})
```

> 注：`screen` 和 `render` 已在现有测试顶部 import；`vi.spyOn(useSessionsModule, 'useSessions')` 需要 useSessions 是 named export（已确认 `client/src/features/session/hooks/use-sessions.ts:5` 是 named export）。

- [ ] **Step 2: 运行前端测试 + 类型**

```bash
cd client && npx vitest run src/features/session/chat-header.test.tsx
cd client && npx tsc --noEmit
```

Expected: PASS。

- [ ] **Step 3: 手动 E2E**

1. 启动真实 OpenCode：`cd ../opencode && bun run start`（或项目配置的启动方式）
2. 启动后端：`cd server && mvn spring-boot:run -pl data-talk-adapter`
3. 启动前端：`cd client && npm run tauri dev`
4. 新建连接 + 会话，发一条"查询今日订单销量"
5. 观察 ChatHeader 在几秒内从 `新会话` 变为 OpenCode 生成的智能标题 ✓
6. 手动点 ChatHeader 的"重命名"改为 `我的查询`
7. 再发一条消息，确认 title 保持 `我的查询` 不被 OpenCode 覆盖 ✓

填写观察结果到 plan 末尾的"Verification Notes"区块，否则不视为完成。

- [ ] **Step 4: 提交**

```bash
git add client/src/features/session/chat-header.test.tsx
git commit -m "test(chat-header): assert title updates after useSessions refetch"
```

---

## Task 10: 文档 housekeeping

**Files:**
- Modify: `docs/exec-plans/tech-debt-tracker.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/references/opencode-protocol.md`

- [ ] **Step 1: 登记技术债 TD-013 ~ TD-017**

在 `docs/exec-plans/tech-debt-tracker.md` 的「当前债务」表末尾追加：

```markdown
| TD-013 | P1 | adapter / client | `DtEvent.SessionIdle` 定义但未消费；`ChannelController.java:110-121` 仍用 1000ms 恩典期关流 | Plan 2026-04-18 opencode-session-title-sync |
| TD-014 | P2 | client | `DtEvent.SessionError` 定义但未消费 | 同上 |
| TD-015 | P2 | client | `DtEvent.SessionCreated / SessionDeleted` 定义但未消费（多客户端协作场景） | 同上 |
| TD-016 | P2 | client | `DtEvent.SessionCompacted` 定义但未消费（OpenCode 上下文压缩提示） | 同上 |
| TD-017 | P2 | client | `DtEvent.SessionDiff` 定义但未消费；payload 语义待调研 | 同上 |
```

- [ ] **Step 2: 更新 OpenCode 协议参考文档**

在 `docs/references/opencode-protocol.md` 的 "OpenCode 事件 → DtEvent 映射" 表追加：

```markdown
| session.created | SessionCreated | 会话创建（多客户端协作） |
| session.updated | SessionMetaUpdated | OpenCode 自动生成 / 更新 title；持久化到本地 SessionRepository（title_locked=0 时） |
| session.deleted | SessionDeleted | 会话删除 |
| session.idle | SessionIdle | 响应完成信号（用于流生命周期判定） |
| session.error | SessionError | 会话级错误 |
| session.compacted | SessionCompacted | 上下文压缩 |
| session.diff | SessionDiff | diff 事件（payload 待调研） |
```

- [ ] **Step 3: 把 plan 从活跃移到已完成**

在 `docs/exec-plans/index.md` 里：

- 从「活跃计划」表删除 opencode-session-title-sync（若曾加入）
- 在「已完成计划」表顶部追加：

```markdown
| [OpenCode Session Title Sync](./2026-04-18-opencode-session-title-sync-plan.md) | 2026-04-18 | 完整 session.* 事件家族翻译 + title 自动同步 + title_locked 锁定机制 |
```

- [ ] **Step 4: 提交**

```bash
git add docs/exec-plans/tech-debt-tracker.md \
        docs/references/opencode-protocol.md \
        docs/exec-plans/index.md \
        docs/exec-plans/2026-04-18-opencode-session-title-sync-plan.md
git commit -m "docs: archive opencode-session-title-sync plan + register TD-013..017"
```

---

## Verification Notes（手动 E2E 记录）

*执行 Task 9 Step 3 时把观察结果填入下方：*

- 标题自动生成时延：____ 秒
- 锁定后 OpenCode 未覆盖：✅ / ❌
- 异常或边界情况：____
