# File Artifact System · Part 1 — Migration & Domain Layer

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 落地 file artifact 系统的类型基座 —— Flyway V14 migration（新表 `file_artifact`）+ Domain 层（`FileArtifact` sealed interface + 配套 enum/record）+ Application 层服务骨架（`FileArtifactService` + `SessionWorkdirService`）+ DtEvent 5 个新事件 + `AgentPromptBuilder` `{{ACTIVE_SESSION_DIR}}` 占位扩展。本 Part 完成后，REST 注入路径手工归档可走通；watcher/MCP/前端尚未接入（留 Part 2-5）。

**Architecture:** 严格遵循 spec §3.2 的依赖方向 domain ← application ← infrastructure ← adapter。新表 `file_artifact` 的 `session_id`/`connection_id` 不设 FK（spec §4 决议），application 层管理引用。状态机 4 个严格状态（无 Removed）。`SessionWorkdirService` 负责 `~/.data-talk/opencode/sessions/<sid>/` 子目录生命周期 —— 子目录软隔离（spec §3.1）。`AgentPromptBuilder` 新增 `{{ACTIVE_SESSION_DIR}}` 占位与 `STAGE_TAB_DIGEST` 解耦。

**Tech Stack:** Spring Boot 3.5、Java 21（sealed interface / record / pattern switch）、Flyway 9.x SQLite、JUnit 5、AssertJ、Mockito、嵌入式 H2。

**Spec:** [docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md](../product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md)

**关联 Part：**
- Part 1 (本计划) — Migration + Domain
- Part 2 — ArtifactWatcherService (io.methvin) + reconcile + symlink 拒绝
- Part 3 — ArchiveArtifactActionHandler MCP + classpath AGENTS.md + AgentsTemplateContractTest
- Part 4 — Frontend Files Tab + Files Library Tab + Zustand store + i18n
- Part 5 — session/connection DELETE 两阶段 + Chat 卡片 + 终局确认 modal + HousekeepingScheduler + LegacyMigrationRunner + Settings Maintenance

**执行状态（2026-04-29）**：已完成。按用户指定的 Subagent-Driven 批处理方式执行，代码改动统一联调；计划中分 task commit / final commit 步骤未单独执行，当前保持为工作区未提交变更，等待人工统一提交。可选手工 Demo 未执行，已由自动化 focused tests + `mvn clean verify -q` 覆盖 Part 1 完成门槛。H2 测试用 `schema.sql` 使用普通 index；生产 SQLite V14 migration 保留 partial index。

---

## Files

### Migration

- Create: `server/data-talk-infrastructure/src/main/resources/db/migration/V14__file_artifact.sql`

### Domain (新增 sealed interface + record + enum)

- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/FileArtifact.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/FileArtifactScope.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/FileArtifactStatus.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/FileArtifactKind.java`
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java`

### Domain tests

- Create: `server/data-talk-domain/src/test/java/com/datatalk/domain/fileartifact/FileArtifactTest.java`

### Application (record + service 骨架 + repository 接口)

- Create: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactRepository.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/SessionWorkdirService.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/SessionWorkdirRoot.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/PathSafetyError.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/stage/AgentPromptBuilder.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/stage/ActiveSessionDirProvider.java`

### Application tests

- Create: `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceTest.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/SessionWorkdirServiceTest.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/stage/AgentPromptBuilderTest.java`

### Infrastructure (JDBC repository 实现)

- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepository.java`

### Infrastructure tests

- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepositoryIT.java`

### Adapter (REST controller 骨架)

- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/FileArtifactController.java`

### Adapter tests

- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/FileArtifactControllerIT.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/FlywayMigrationIT.java`
- Modify: `server/data-talk-adapter/src/test/resources/schema.sql`

### Docs

- Modify: `docs/exec-plans/index.md`（活跃计划登记本 Part 与 Part 2-5 的占位）

---

## Task 1: 注册计划

- [x] 已存在 `docs/exec-plans/2026-04-29-file-artifact-system-part1-domain-and-migration-plan.md`（本文件）
- [x] 在 `docs/exec-plans/index.md` 「活跃计划」表格新增一行：

```markdown
| [File Artifact System · Part 1 — Migration & Domain](./2026-04-29-file-artifact-system-part1-domain-and-migration-plan.md) | 2026-04-29 | OpenCode 工作目录与 File Artifact 系统 v2 落地 Part 1：Flyway V14 新表 `file_artifact`（session_id/connection_id 不设 FK、application 层管理引用、状态严格 4 个）+ Domain sealed `FileArtifact` 与 enum + DtEvent 5 个新事件 + `SessionWorkdirService` 子目录软隔离骨架 + `AgentPromptBuilder` `{{ACTIVE_SESSION_DIR}}` 占位扩展 + REST 注入路径手工归档骨架。Part 2 (watcher) / Part 3 (MCP+AGENTS) / Part 4 (前端) / Part 5 (删除流+治理) 在 Part 1 落地后续写。 |
```

- [x] 同时为 Part 2-5 添加占位行（标记 `(待 Part 1 落地后续写)`）。
- [x] commit:

```bash
git add docs/exec-plans/2026-04-29-file-artifact-system-part1-domain-and-migration-plan.md docs/exec-plans/index.md
git commit -m "docs(exec-plans): register file artifact system part 1 plan"
```

## Task 2: Flyway V14 Migration

**目的：** 新增 `file_artifact` 表，与现有 `artifacts` 表共存且无引用冲突；提供索引与状态约束。

### 2.1 写 migration SQL

- [x] 创建 `server/data-talk-infrastructure/src/main/resources/db/migration/V14__file_artifact.sql`：

```sql
-- File Artifact System (spec 2026-04-29-opencode-workdir-and-artifact-system-design)
-- 与现有 V1/V3 中的 artifacts(payload 型) 表完全独立；命名为单数 file_artifact 区分。
-- session_id / connection_id 不加 FK：
--   archived 行需在 session 删除后保留并把 session_id 置 NULL；
--   application 层 (FileArtifactService / SessionService.deleteRecord) 主动管理引用。

CREATE TABLE file_artifact (
  id            TEXT PRIMARY KEY,
  scope         TEXT NOT NULL CHECK(scope IN ('session','workspace')),
  status        TEXT NOT NULL CHECK(status IN ('temporary','candidate','archived','discarded')),
  kind          TEXT NOT NULL CHECK(kind IN ('report','er_diagram','sql_script','dataset','other')),
  session_id    TEXT,                                           -- nullable; archived 行 session 删后置 NULL
  connection_id TEXT,                                           -- nullable until promoted; archived 必填
  filename      TEXT NOT NULL,
  physical_path TEXT NOT NULL,
  size_bytes    INTEGER NOT NULL,
  mime_type     TEXT,
  title         TEXT,
  summary       TEXT,
  created_at    INTEGER NOT NULL,                               -- millis since epoch
  updated_at    INTEGER NOT NULL,
  archived_at   INTEGER,
  metadata_json TEXT
);

CREATE INDEX idx_file_artifact_session    ON file_artifact(session_id)    WHERE scope = 'session';
CREATE INDEX idx_file_artifact_connection ON file_artifact(connection_id) WHERE scope = 'workspace';
CREATE INDEX idx_file_artifact_status     ON file_artifact(status);
```

### 2.2 同步测试 schema.sql

- [x] 修改 `server/data-talk-adapter/src/test/resources/schema.sql`：在文件末尾追加上面的 `CREATE TABLE file_artifact ...` 与 3 个 index（**不要**写 SQLite 的 `CHECK(scope IN ...)`，H2 也支持，照搬即可）。

### 2.3 跑 FlywayMigrationIT 验证

- [x] 修改 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/FlywayMigrationIT.java`：增加一个测试方法验证 V14 migrated 后 `file_artifact` 表存在：

```java
@Test
void v14_creates_file_artifact_table() throws Exception {
    try (var conn = dataSource.getConnection();
         var rs = conn.getMetaData().getTables(null, null, "FILE_ARTIFACT", null)) {
        assertThat(rs.next()).as("file_artifact table exists after V14").isTrue();
    }
}
```

- [x] 运行验证：

```bash
cd server && mvn -pl data-talk-adapter test -Dtest=FlywayMigrationIT -q
```

预期：新测试通过；历史 V1-V13 的回归测试不受影响。

### 2.4 验证现有 artifacts 表不受影响

- [x] 在同文件新增一个回归测试，确保现有 `artifacts` 表（payload 型）schema 完整：

```java
@Test
void v14_does_not_alter_existing_artifacts_table() throws Exception {
    try (var conn = dataSource.getConnection();
         var rs = conn.getMetaData().getColumns(null, null, "ARTIFACTS", "KIND")) {
        assertThat(rs.next()).isTrue();
        // 现有 kind 字段允许 'table','chart','erd'，本期 migration 不动它
    }
}
```

- [x] 运行：

```bash
cd server && mvn -pl data-talk-adapter test -Dtest=FlywayMigrationIT -q
```

预期：通过。

### 2.5 commit

- [x] commit：

```bash
git add server/data-talk-infrastructure/src/main/resources/db/migration/V14__file_artifact.sql \
        server/data-talk-adapter/src/test/resources/schema.sql \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/FlywayMigrationIT.java
git commit -m "feat(infra): add V14 migration for file_artifact table"
```

## Task 3: Domain — FileArtifactStatus enum

**目的：** 4 个严格状态（无 Removed）。

- [x] 创建 `server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/FileArtifactStatus.java`：

```java
package com.datatalk.domain.fileartifact;

/**
 * Lifecycle states for {@link FileArtifact}.
 *
 * <p>Strict 4 states. There is intentionally no {@code REMOVED} —
 * reconcile / discard flows DELETE the row directly when the file is gone,
 * or move the file into _trash and mark {@link #DISCARDED}.
 */
public enum FileArtifactStatus {
    TEMPORARY,   // AI wrote a file, no archive intent yet
    CANDIDATE,   // AI or user signaled "worth keeping" (via MCP tool or frontmatter)
    ARCHIVED,    // User promoted to connection-scoped library (file moved to workspaces/<connId>/)
    DISCARDED;   // File moved to _trash/, will be physically deleted after 7 days

    public String dbValue() {
        return name().toLowerCase();
    }

    public static FileArtifactStatus fromDb(String dbValue) {
        return valueOf(dbValue.toUpperCase());
    }
}
```

## Task 4: Domain — FileArtifactScope enum

- [x] 创建 `server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/FileArtifactScope.java`：

```java
package com.datatalk.domain.fileartifact;

/**
 * Ownership dimension of a {@link FileArtifact}.
 *
 * <p>SESSION scope: file lives under {@code ~/.data-talk/opencode/sessions/<sid>/}.
 * <p>WORKSPACE scope: file has been promoted to {@code ~/.data-talk/workspaces/<connId>/}.
 */
public enum FileArtifactScope {
    SESSION,
    WORKSPACE;

    public String dbValue() {
        return name().toLowerCase();
    }

    public static FileArtifactScope fromDb(String dbValue) {
        return valueOf(dbValue.toUpperCase());
    }
}
```

## Task 5: Domain — FileArtifactKind enum

- [x] 创建 `server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/FileArtifactKind.java`：

```java
package com.datatalk.domain.fileartifact;

/**
 * AI / user-declared category of a {@link FileArtifact}.
 *
 * <p>Used for grouping in Files Library UI and for emoji / icon mapping.
 * Mirrors the {@code kind} enum in the {@code datatalk_archive_artifact} MCP tool schema.
 */
public enum FileArtifactKind {
    REPORT,
    ER_DIAGRAM,
    SQL_SCRIPT,
    DATASET,
    OTHER;

    public String dbValue() {
        return name().toLowerCase();
    }

    public static FileArtifactKind fromDb(String dbValue) {
        return valueOf(dbValue.toUpperCase());
    }
}
```

## Task 6: Domain — FileArtifact sealed record

**目的：** 一个 record 承载所有 4 状态（不为每个状态单独建 record；状态用字段表达比 sealed 多类型更紧凑，且 DB schema 是单表）。Spec §6.1 状态机由 application 层校验跃迁合法性。

- [x] 创建 `server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/FileArtifact.java`：

```java
package com.datatalk.domain.fileartifact;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * A physical file produced by the AI inside an OpenCode session subdir,
 * or promoted to a connection-scoped library.
 *
 * <p>Lifecycle: see {@link FileArtifactStatus}. Single record by design — the
 * 4 states share most fields and live in a single SQLite row; differentiating
 * each state into a sealed sub-record would not buy clarity at the cost of
 * many runtime conversions in repository layer.
 *
 * @param id            Stable id, prefix {@code file_artifact_}.
 * @param scope         Session-scoped or workspace-scoped.
 * @param status        Lifecycle state.
 * @param kind          AI/user category.
 * @param sessionId     Originating session; archived rows may have this NULL after session deletion.
 * @param connectionId  Owning connection (mandatory once status >= ARCHIVED).
 * @param filename      File basename (without directory parts).
 * @param physicalPath  Absolute path on disk (sessions/, workspaces/, or _trash/).
 * @param sizeBytes     File size in bytes.
 * @param mimeType      Detected mime, may be null.
 * @param title         Human-readable title; defaults to filename.
 * @param summary       One-paragraph description.
 * @param createdAt     Row creation time.
 * @param updatedAt     Last metadata update time (synced to file mtime when watcher detects MODIFY).
 * @param archivedAt    Filled when status == ARCHIVED.
 * @param metadata      Parsed frontmatter + extension; may be empty map.
 */
public record FileArtifact(
        String id,
        FileArtifactScope scope,
        FileArtifactStatus status,
        FileArtifactKind kind,
        String sessionId,
        String connectionId,
        String filename,
        String physicalPath,
        long sizeBytes,
        String mimeType,
        String title,
        String summary,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt,
        Map<String, Object> metadata
) {
    public Optional<String> sessionIdOpt() { return Optional.ofNullable(sessionId); }
    public Optional<String> connectionIdOpt() { return Optional.ofNullable(connectionId); }
    public Optional<Instant> archivedAtOpt() { return Optional.ofNullable(archivedAt); }
    public Optional<String> mimeTypeOpt() { return Optional.ofNullable(mimeType); }
    public Optional<String> titleOpt() { return Optional.ofNullable(title); }
    public Optional<String> summaryOpt() { return Optional.ofNullable(summary); }
}
```

## Task 7: Domain — FileArtifactTest

**目的：** 验证 enum 的 dbValue/fromDb 往返；验证 record 构造。

- [x] 创建 `server/data-talk-domain/src/test/java/com/datatalk/domain/fileartifact/FileArtifactTest.java`：

```java
package com.datatalk.domain.fileartifact;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FileArtifactTest {

    @Test
    void status_dbValue_round_trip() {
        for (FileArtifactStatus s : FileArtifactStatus.values()) {
            assertThat(FileArtifactStatus.fromDb(s.dbValue())).isEqualTo(s);
        }
    }

    @Test
    void scope_dbValue_round_trip() {
        for (FileArtifactScope s : FileArtifactScope.values()) {
            assertThat(FileArtifactScope.fromDb(s.dbValue())).isEqualTo(s);
        }
    }

    @Test
    void kind_dbValue_round_trip() {
        for (FileArtifactKind k : FileArtifactKind.values()) {
            assertThat(FileArtifactKind.fromDb(k.dbValue())).isEqualTo(k);
        }
    }

    @Test
    void status_has_exactly_four_states() {
        // Guard: spec §6.1 explicitly says no REMOVED state.
        assertThat(FileArtifactStatus.values()).hasSize(4);
        assertThat(FileArtifactStatus.values())
                .containsExactly(
                        FileArtifactStatus.TEMPORARY,
                        FileArtifactStatus.CANDIDATE,
                        FileArtifactStatus.ARCHIVED,
                        FileArtifactStatus.DISCARDED);
    }

    @Test
    void file_artifact_record_constructs() {
        Instant now = Instant.now();
        FileArtifact a = new FileArtifact(
                "file_artifact_01",
                FileArtifactScope.SESSION,
                FileArtifactStatus.TEMPORARY,
                FileArtifactKind.OTHER,
                "ses_abc",
                null,
                "sample.csv",
                "/home/u/.data-talk/opencode/sessions/ses_abc/sample.csv",
                2_100_000L,
                "text/csv",
                null,
                null,
                now,
                now,
                null,
                Map.of());
        assertThat(a.id()).isEqualTo("file_artifact_01");
        assertThat(a.connectionIdOpt()).isEmpty();
        assertThat(a.archivedAtOpt()).isEmpty();
    }
}
```

- [x] 运行：

```bash
cd server && mvn -pl data-talk-domain test -Dtest=FileArtifactTest -q
```

预期：4 个测试通过。

## Task 8: Domain — DtEvent 新增 5 类型

**目的：** 给 application/adapter 后续使用提供事件类型；编译期通过 sealed permits 强约束。

- [x] 修改 `server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java`：在文件末尾（`}` 关闭 sealed interface 之前）追加 5 个 record，并把它们添加到 `permits` 列表（如果存在显式 permits）。如果当前 sealed interface 没有显式 permits（依赖同包推断），仅在 interface 内追加 record 即可。

```java
    // ───────────────────── File Artifact events (spec 2026-04-29) ─────────────────────

    /** Watcher detected a new file in a session subdir; row inserted as TEMPORARY (or CANDIDATE if frontmatter declared). */
    record FileArtifactDetected(
            String fileArtifactId,
            String sessionId,
            String filename,
            String kind,                 // domain enum dbValue (lowercase)
            String status,               // 'temporary' | 'candidate'
            long sizeBytes
    ) implements DtEvent {}

    /** AI invoked datatalk_archive_artifact; status promoted TEMPORARY → CANDIDATE. */
    record FileArtifactArchiveRequested(
            String fileArtifactId,
            String sessionId,
            String kind,
            String title,
            String summary
    ) implements DtEvent {}

    /** User pressed [归档] (or session-delete final-confirm chose 归档); file moved to workspaces/<connId>/. */
    record FileArtifactArchived(
            String fileArtifactId,
            String sessionId,            // nullable: filled if archive happened during a live session
            String connectionId,
            String filename,
            String physicalPath
    ) implements DtEvent {}

    /** User pressed [丢弃] (or final-confirm chose 丢弃); file moved to _trash/ with status=discarded. */
    record FileArtifactDiscarded(
            String fileArtifactId,
            String reason                // 'user_action' | 'session_deleted' | 'reconcile'
    ) implements DtEvent {}

    /** One-shot legacy migration completed at startup; surfaces a toast in the UI. */
    record LegacyMigrated(
            int filesMovedCount
    ) implements DtEvent {}
```

- [x] 检查现有 `DtEvent.java` 内是否使用 `permits` 显式列表；若使用，把 5 个新 record 加到 permits 列表末尾。（grep `permits` 关键字定位。）

- [x] 编译验证：

```bash
cd server && mvn compile -q -pl data-talk-domain
```

预期：零错误。

### Task 8 关键收尾（CLAUDE.md "Backend Run vs Compile"）

- [x] 把新 jar 推到 `~/.m2`：

```bash
cd server && mvn install -pl data-talk-domain -am -DskipTests -q
```

否则后续模块拉到旧 jar 编译失败。

- [x] commit：

```bash
git add server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/ \
        server/data-talk-domain/src/test/java/com/datatalk/domain/fileartifact/ \
        server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java
git commit -m "feat(domain): add FileArtifact types and DtEvent.FileArtifact* events"
```

## Task 9: Application — FileArtifactRepository 接口

**目的：** 抽象出持久化能力，application 层只依赖接口；JDBC 实现见 Task 13。

- [x] 创建 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactRepository.java`：

```java
package com.datatalk.application.fileartifact;

import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactStatus;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link FileArtifact}.
 *
 * <p>Implementation lives in infrastructure ({@code JdbcFileArtifactRepository}).
 * No JPA / mapper magic; raw {@code JdbcTemplate} per project convention.
 */
public interface FileArtifactRepository {

    void insert(FileArtifact artifact);

    Optional<FileArtifact> findById(String id);

    /** Files belonging to the session, regardless of status. */
    List<FileArtifact> findBySession(String sessionId);

    /** Workspace-scoped (archived) files for a connection. */
    List<FileArtifact> findArchivedByConnection(String connectionId);

    /** Used by Phase 1 of session DELETE: detect blocking candidates. */
    List<FileArtifact> findCandidatesBySession(String sessionId);

    void updateStatus(String id, FileArtifactStatus newStatus);

    /** Atomically update path + status + scope (for promote / discard mv). */
    void updateLocation(
            String id,
            FileArtifactStatus newStatus,
            String newScope,
            String newPhysicalPath,
            String newConnectionId);

    /** Promote: keep session_id, switch scope, fill archivedAt, record new path. */
    void markArchived(
            String id,
            String connectionId,
            String newPhysicalPath);

    /** Application-managed cascade: session DELETE Phase 2. */
    void deleteTransientByForSession(String sessionId);   // status IN ('temporary','candidate')
    void detachArchivedFromSession(String sessionId);      // UPDATE session_id=NULL where status='archived'

    /** Used by reconcile / cleanupTrash. */
    void deleteById(String id);

    /** Update mtime / size after watcher MODIFY event. */
    void updateMetadata(String id, long sizeBytes, long updatedAtMillis);
}
```

## Task 10: Application — PathSafetyError sealed enum

**目的：** 让 service 层的路径校验失败原因可枚举、可测试。

- [x] 创建 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/PathSafetyError.java`：

```java
package com.datatalk.application.fileartifact;

/**
 * Reasons the path-safety guard rejects an MCP {@code datatalk_archive_artifact} call.
 *
 * <p>Wire-format strings (snake_case) match spec §5.2 documented error codes for the MCP tool.
 */
public enum PathSafetyError {
    PATH_OUTSIDE_SESSION_DIR("path_outside_session_dir"),
    PATH_NOT_FOUND          ("path_not_found"),
    PATH_IS_DIRECTORY       ("path_is_directory"),
    PATH_IS_SYSTEM          ("path_is_system"),
    PATH_CONTAINS_SYMLINK   ("path_contains_symlink"),
    PATH_TOCTOU_RACE        ("path_toctou_race");

    private final String wire;

    PathSafetyError(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }
}
```

## Task 11: Application — SessionWorkdirService

**目的：** 负责 `~/.data-talk/opencode/sessions/<sid>/` 子目录的 mkdir / 删除 / cwd 解析；不做监听（Part 2）。也提供 `~/.data-talk/workspaces/<connId>/` 与 `~/.data-talk/_trash/` 的 base path。

### 11.1 SessionWorkdirRoot record

- [x] 创建 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/SessionWorkdirRoot.java`：

```java
package com.datatalk.application.fileartifact;

import java.nio.file.Path;

/**
 * Layout anchors for the file-artifact filesystem.
 *
 * <p>{@code dataTalkRoot} = {@code ~/.data-talk/} (parent of opencode/, workspaces/, _trash/, _legacy/).
 * <p>{@code opencodeCwd}  = {@code ~/.data-talk/opencode/} (OpenCode process cwd; sessions/ lives under it).
 *
 * <p>Provided as a record so different deployments / tests can inject custom paths
 * (e.g. tmp dir for IT tests).
 */
public record SessionWorkdirRoot(Path dataTalkRoot, Path opencodeCwd) {

    public Path sessionsRoot()   { return opencodeCwd.resolve("sessions"); }
    public Path sessionDir(String sessionId)         { return sessionsRoot().resolve(sessionId); }
    public Path workspacesRoot() { return dataTalkRoot.resolve("workspaces"); }
    public Path workspaceDir(String connectionId)    { return workspacesRoot().resolve(connectionId); }
    public Path trashRoot()      { return dataTalkRoot.resolve("_trash"); }
    public Path legacyRoot()     { return dataTalkRoot.resolve("_legacy"); }
}
```

### 11.2 SessionWorkdirService

- [x] 创建 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/SessionWorkdirService.java`：

```java
package com.datatalk.application.fileartifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Lifecycle manager for per-session subdirectories under
 * {@code ~/.data-talk/opencode/sessions/<sid>/}.
 *
 * <p>Soft-isolation strategy (spec §3.1): OpenCode runs as a single process with
 * a fixed cwd; we manage subdirs and inject {@code {{ACTIVE_SESSION_DIR}}} into
 * AGENTS.md so the AI knows where to write.
 *
 * <p>Watcher registration is wired in Part 2; this service deals only with
 * directory mkdir/delete and metadata bookkeeping.
 */
@Service
public class SessionWorkdirService {

    private static final Logger log = LoggerFactory.getLogger(SessionWorkdirService.class);
    private static final String META_FILENAME = ".meta.json";

    private final SessionWorkdirRoot root;
    private final ObjectMapper json;

    public SessionWorkdirService(SessionWorkdirRoot root, ObjectMapper json) {
        this.root = root;
        this.json = json;
    }

    /** Idempotent: creates session subdir + .meta.json if missing. Returns absolute path. */
    public Path getOrCreate(String sessionId, String connectionId) {
        Path dir = root.sessionDir(sessionId);
        try {
            Files.createDirectories(dir);
            Path meta = dir.resolve(META_FILENAME);
            if (!Files.exists(meta)) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("sessionId", sessionId);
                body.put("connectionId", connectionId);
                body.put("createdAt", Instant.now().toString());
                Files.writeString(meta, json.writerWithDefaultPrettyPrinter().writeValueAsString(body));
            }
            return dir.toRealPath();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create session workdir " + dir, e);
        }
    }

    /** Returns absolute path to the session subdir without creating it; throws if missing. */
    public Path require(String sessionId) {
        Path dir = root.sessionDir(sessionId);
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("Session workdir does not exist: " + dir);
        }
        try {
            return dir.toRealPath();
        } catch (IOException e) {
            throw new RuntimeException("Failed to resolve realpath of " + dir, e);
        }
    }

    /** Recursive delete of the session subdir. Tolerant if dir is missing or partially gone. */
    public void delete(String sessionId) {
        Path dir = root.sessionDir(sessionId);
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); }
                catch (IOException e) { log.warn("Failed to delete {}: {}", p, e.toString()); }
            });
        } catch (IOException e) {
            log.warn("Failed to walk session workdir for delete {}: {}", dir, e.toString());
        }
    }

    public SessionWorkdirRoot root() { return root; }

    /** Convenience: relative path string used in AGENTS.md placeholder, e.g. "./sessions/ses_abc/". */
    public String relativeForPrompt(String sessionId) {
        return "./sessions/" + sessionId + "/";
    }
}
```

### 11.3 SessionWorkdirRoot bean wiring

`SessionWorkdirRoot` 需要 Spring bean 才能注入。在 application 模块的 configuration class 加 bean：

- [x] 找出现有 application 模块的 `@Configuration` 类（若没有就创建 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactConfiguration.java`）：

```java
package com.datatalk.application.fileartifact;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class FileArtifactConfiguration {

    @Bean
    public SessionWorkdirRoot sessionWorkdirRoot(
            @Value("${datatalk.workdir.data-talk-root:#{systemProperties['user.home']}/.data-talk}") String dataTalkRoot
    ) {
        Path root = Paths.get(dataTalkRoot);
        return new SessionWorkdirRoot(root, root.resolve("opencode"));
    }
}
```

### 11.4 SessionWorkdirServiceTest

- [x] 创建 `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/SessionWorkdirServiceTest.java`：

```java
package com.datatalk.application.fileartifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SessionWorkdirServiceTest {

    @TempDir
    Path tmp;

    SessionWorkdirRoot root;
    SessionWorkdirService svc;

    @BeforeEach
    void setUp() {
        root = new SessionWorkdirRoot(tmp, tmp.resolve("opencode"));
        svc = new SessionWorkdirService(root, new ObjectMapper());
    }

    @Test
    void getOrCreate_creates_dir_and_meta() {
        Path dir = svc.getOrCreate("ses_abc", "conn_xyz");
        assertThat(dir).isDirectory();
        assertThat(dir.resolve(".meta.json")).exists();
        String body = readSilent(dir.resolve(".meta.json"));
        assertThat(body).contains("\"sessionId\" : \"ses_abc\"");
        assertThat(body).contains("\"connectionId\" : \"conn_xyz\"");
    }

    @Test
    void getOrCreate_is_idempotent() {
        Path first = svc.getOrCreate("ses_abc", "conn_xyz");
        Path second = svc.getOrCreate("ses_abc", "conn_xyz");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void delete_recursively_removes_session_subtree() throws Exception {
        Path dir = svc.getOrCreate("ses_abc", "conn_xyz");
        Files.writeString(dir.resolve("sample.csv"), "id,val\n1,2");
        Files.createDirectories(dir.resolve("sub").resolve("nested"));
        svc.delete("ses_abc");
        assertThat(dir).doesNotExist();
    }

    @Test
    void delete_is_tolerant_of_missing_dir() {
        // No exception expected:
        svc.delete("ses_does_not_exist");
    }

    @Test
    void relativeForPrompt_renders_session_subdir() {
        assertThat(svc.relativeForPrompt("ses_abc")).isEqualTo("./sessions/ses_abc/");
    }

    private static String readSilent(Path p) {
        try { return Files.readString(p); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
```

- [x] 运行：

```bash
cd server && mvn -pl data-talk-application test -Dtest=SessionWorkdirServiceTest -q
```

预期：5 个测试通过。

## Task 12: Application — FileArtifactService

**目的：** Spec §5.4 的 8 条路径校验规则 + Temporary→Candidate→Archived 状态机。本 Part 不实现 watcher（Part 2）也不实现 archive 物理 mv（Part 5），先把 service API 立起来 + 路径安全完整。

- [x] 创建 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java`：

```java
package com.datatalk.application.fileartifact;

import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Optional;

/**
 * Use-case orchestrator for file artifacts.
 *
 * <p>Responsibilities in Part 1 (this Part):
 * <ul>
 *   <li>Path-safety guard (spec §5.4) — 8 rules, used by archive_artifact and reconcile.</li>
 *   <li>State machine transitions: TEMPORARY → CANDIDATE (markCandidate).</li>
 *   <li>Read APIs for controllers.</li>
 * </ul>
 *
 * <p>Reserved for later Parts: physical mv to workspaces/ (Part 5),
 * watcher integration (Part 2), MCP tool wiring (Part 3).
 */
@Service
public class FileArtifactService {

    private static final Logger log = LoggerFactory.getLogger(FileArtifactService.class);

    private final FileArtifactRepository repo;
    private final SessionWorkdirService workdir;
    private final ObjectMapper json;

    public FileArtifactService(
            FileArtifactRepository repo,
            SessionWorkdirService workdir,
            ObjectMapper json) {
        this.repo = repo;
        this.workdir = workdir;
        this.json = json;
    }

    public List<FileArtifact> listForSession(String sessionId) {
        return repo.findBySession(sessionId);
    }

    public List<FileArtifact> listArchivedForConnection(String connectionId) {
        return repo.findArchivedByConnection(connectionId);
    }

    public List<FileArtifact> findCandidatesForSession(String sessionId) {
        return repo.findCandidatesBySession(sessionId);
    }

    /**
     * Path safety guard for the {@code datatalk_archive_artifact} MCP tool.
     * Implements spec §5.4 rules 1–7. Rule 8 (atomic mv) lives in promotion code (Part 5).
     */
    public Optional<PathSafetyError> guardPath(String sessionId, String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            return Optional.of(PathSafetyError.PATH_NOT_FOUND);
        }
        if (Path.of(requestedPath).isAbsolute()) {
            return Optional.of(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
        }
        // Rule 2: explicit ".." segment rejection (defense in depth, even though rule 3 also covers it)
        for (Path seg : Path.of(requestedPath)) {
            if (seg.toString().equals("..")) {
                return Optional.of(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
            }
            // Rule 4: any path segment starting with "_" is system reserved
            if (seg.toString().startsWith("_")) {
                return Optional.of(PathSafetyError.PATH_IS_SYSTEM);
            }
        }
        Path base;
        try {
            base = workdir.require(sessionId);
        } catch (IllegalStateException e) {
            return Optional.of(PathSafetyError.PATH_NOT_FOUND);
        }
        Path target = base.resolve(requestedPath).normalize();
        // Rule 3: realpath containment under session base
        try {
            Path resolved = target.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!resolved.startsWith(base)) {
                return Optional.of(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
            }
            // Rule 5: must not be a symbolic link itself
            BasicFileAttributes attrs = Files.readAttributes(resolved, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (Files.isSymbolicLink(resolved)) {
                return Optional.of(PathSafetyError.PATH_CONTAINS_SYMLINK);
            }
            // Rule 6: must be a regular file
            if (attrs.isDirectory()) {
                return Optional.of(PathSafetyError.PATH_IS_DIRECTORY);
            }
            if (!attrs.isRegularFile()) {
                return Optional.of(PathSafetyError.PATH_NOT_FOUND);
            }
        } catch (IOException e) {
            return Optional.of(PathSafetyError.PATH_NOT_FOUND);
        }
        return Optional.empty();
    }

    /** Promote TEMPORARY to CANDIDATE (idempotent if already CANDIDATE; reject if ARCHIVED/DISCARDED). */
    public void markCandidate(String fileArtifactId) {
        FileArtifact a = repo.findById(fileArtifactId)
                .orElseThrow(() -> new IllegalArgumentException("file artifact not found: " + fileArtifactId));
        switch (a.status()) {
            case TEMPORARY:
                repo.updateStatus(fileArtifactId, FileArtifactStatus.CANDIDATE);
                log.info("file_artifact {} promoted TEMPORARY → CANDIDATE", fileArtifactId);
                break;
            case CANDIDATE:
                // idempotent
                break;
            case ARCHIVED:
            case DISCARDED:
                throw new IllegalStateException(
                        "cannot mark candidate: artifact " + fileArtifactId + " is " + a.status());
        }
    }
}
```

### 12.1 FileArtifactServiceTest

- [x] 创建 `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceTest.java`：

```java
package com.datatalk.application.fileartifact;

import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileArtifactServiceTest {

    @TempDir
    Path tmp;

    FileArtifactRepository repo;
    SessionWorkdirService workdir;
    FileArtifactService svc;
    Path sessionDir;

    @BeforeEach
    void setUp() throws Exception {
        repo = mock(FileArtifactRepository.class);
        SessionWorkdirRoot root = new SessionWorkdirRoot(tmp, tmp.resolve("opencode"));
        workdir = new SessionWorkdirService(root, new ObjectMapper());
        sessionDir = workdir.getOrCreate("ses_abc", "conn_xyz");
        svc = new FileArtifactService(repo, workdir, new ObjectMapper());
    }

    // ─────────── path safety: 8 rules ───────────

    @Test
    void guardPath_rejects_absolute_path() {
        var err = svc.guardPath("ses_abc", "/etc/passwd");
        assertThat(err).contains(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
    }

    @Test
    void guardPath_rejects_dotdot_traversal() {
        var err = svc.guardPath("ses_abc", "../../etc/passwd");
        assertThat(err).contains(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
    }

    @Test
    void guardPath_rejects_underscore_prefixed_segment() {
        var err = svc.guardPath("ses_abc", "_legacy/foo.md");
        assertThat(err).contains(PathSafetyError.PATH_IS_SYSTEM);
    }

    @Test
    void guardPath_rejects_missing_session_workdir() {
        var err = svc.guardPath("ses_does_not_exist", "foo.md");
        assertThat(err).contains(PathSafetyError.PATH_NOT_FOUND);
    }

    @Test
    void guardPath_rejects_missing_file() {
        var err = svc.guardPath("ses_abc", "missing.md");
        assertThat(err).contains(PathSafetyError.PATH_NOT_FOUND);
    }

    @Test
    void guardPath_rejects_directory() throws Exception {
        Files.createDirectories(sessionDir.resolve("a-subdir"));
        var err = svc.guardPath("ses_abc", "a-subdir");
        assertThat(err).contains(PathSafetyError.PATH_IS_DIRECTORY);
    }

    @Test
    void guardPath_rejects_symlink() throws Exception {
        Path victim = tmp.resolve("victim.md");
        Files.writeString(victim, "secret");
        Path link = sessionDir.resolve("link.md");
        Files.createSymbolicLink(link, victim);
        var err = svc.guardPath("ses_abc", "link.md");
        assertThat(err).contains(PathSafetyError.PATH_CONTAINS_SYMLINK);
    }

    @Test
    void guardPath_rejects_symlink_via_realpath_escape() throws Exception {
        // Symlink to a file outside session dir; realpath will escape but rule 5 should fire first.
        Path victim = tmp.resolve("outside.md");
        Files.writeString(victim, "x");
        Path link = sessionDir.resolve("escape.md");
        Files.createSymbolicLink(link, victim);
        var err = svc.guardPath("ses_abc", "escape.md");
        assertThat(err).isPresent();
        // Either PATH_CONTAINS_SYMLINK or PATH_OUTSIDE_SESSION_DIR is acceptable; we assert it's blocked.
    }

    @Test
    void guardPath_accepts_legitimate_relative_file() throws Exception {
        Files.writeString(sessionDir.resolve("orders-er.md"), "# ER\n");
        var err = svc.guardPath("ses_abc", "orders-er.md");
        assertThat(err).isEmpty();
    }

    @Test
    void guardPath_accepts_nested_relative_file() throws Exception {
        Files.createDirectories(sessionDir.resolve("reports"));
        Files.writeString(sessionDir.resolve("reports/weekly.md"), "# weekly\n");
        var err = svc.guardPath("ses_abc", "reports/weekly.md");
        assertThat(err).isEmpty();
    }

    // ─────────── markCandidate state machine ───────────

    @Test
    void markCandidate_promotes_temporary() {
        when(repo.findById("fid")).thenReturn(Optional.of(stub(FileArtifactStatus.TEMPORARY)));
        svc.markCandidate("fid");
        verify(repo).updateStatus("fid", FileArtifactStatus.CANDIDATE);
    }

    @Test
    void markCandidate_is_idempotent_for_candidate() {
        when(repo.findById("fid")).thenReturn(Optional.of(stub(FileArtifactStatus.CANDIDATE)));
        svc.markCandidate("fid");
        verify(repo, org.mockito.Mockito.never()).updateStatus(any(), any());
    }

    @Test
    void markCandidate_rejects_archived() {
        when(repo.findById("fid")).thenReturn(Optional.of(stub(FileArtifactStatus.ARCHIVED)));
        assertThatThrownBy(() -> svc.markCandidate("fid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ARCHIVED");
    }

    @Test
    void markCandidate_rejects_discarded() {
        when(repo.findById("fid")).thenReturn(Optional.of(stub(FileArtifactStatus.DISCARDED)));
        assertThatThrownBy(() -> svc.markCandidate("fid"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static FileArtifact stub(FileArtifactStatus s) {
        Instant now = Instant.now();
        return new FileArtifact("fid", FileArtifactScope.SESSION, s, FileArtifactKind.OTHER,
                "ses_abc", null, "x.md", "/abs/x.md", 1L, null, null, null, now, now, null, Map.of());
    }
}
```

- [x] 运行：

```bash
cd server && mvn -pl data-talk-application test -Dtest=FileArtifactServiceTest -q
```

预期：14 个测试通过。

## Task 13: Infrastructure — JdbcFileArtifactRepository

**目的：** 用 `JdbcTemplate` 实现 `FileArtifactRepository`；遵循项目现有 `ArtifactRepository` 等仓库的代码风格（grep 项目中其他 `Jdbc*Repository` 作参考）。

- [x] 创建 `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepository.java`：

```java
package com.datatalk.infra.fileartifact;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcFileArtifactRepository implements FileArtifactRepository {

    private static final String COLS =
            "id, scope, status, kind, session_id, connection_id, filename, physical_path, " +
            "size_bytes, mime_type, title, summary, created_at, updated_at, archived_at, metadata_json";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcFileArtifactRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insert(FileArtifact a) {
        jdbc.update(
                "INSERT INTO file_artifact (" + COLS + ") VALUES (" +
                        "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                a.id(),
                a.scope().dbValue(),
                a.status().dbValue(),
                a.kind().dbValue(),
                a.sessionId(),
                a.connectionId(),
                a.filename(),
                a.physicalPath(),
                a.sizeBytes(),
                a.mimeType(),
                a.title(),
                a.summary(),
                a.createdAt().toEpochMilli(),
                a.updatedAt().toEpochMilli(),
                a.archivedAt() == null ? null : a.archivedAt().toEpochMilli(),
                writeMetadata(a.metadata()));
    }

    @Override
    public Optional<FileArtifact> findById(String id) {
        var rows = jdbc.query("SELECT " + COLS + " FROM file_artifact WHERE id=?", mapper(), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<FileArtifact> findBySession(String sessionId) {
        return jdbc.query(
                "SELECT " + COLS + " FROM file_artifact WHERE session_id=? ORDER BY created_at DESC",
                mapper(), sessionId);
    }

    @Override
    public List<FileArtifact> findArchivedByConnection(String connectionId) {
        return jdbc.query(
                "SELECT " + COLS + " FROM file_artifact " +
                        "WHERE connection_id=? AND scope='workspace' AND status='archived' " +
                        "ORDER BY archived_at DESC",
                mapper(), connectionId);
    }

    @Override
    public List<FileArtifact> findCandidatesBySession(String sessionId) {
        return jdbc.query(
                "SELECT " + COLS + " FROM file_artifact WHERE session_id=? AND status='candidate'",
                mapper(), sessionId);
    }

    @Override
    public void updateStatus(String id, FileArtifactStatus newStatus) {
        long now = Instant.now().toEpochMilli();
        jdbc.update("UPDATE file_artifact SET status=?, updated_at=? WHERE id=?",
                newStatus.dbValue(), now, id);
    }

    @Override
    public void updateLocation(
            String id,
            FileArtifactStatus newStatus,
            String newScope,
            String newPhysicalPath,
            String newConnectionId) {
        long now = Instant.now().toEpochMilli();
        jdbc.update(
                "UPDATE file_artifact SET status=?, scope=?, physical_path=?, connection_id=?, updated_at=? WHERE id=?",
                newStatus.dbValue(), newScope, newPhysicalPath, newConnectionId, now, id);
    }

    @Override
    public void markArchived(String id, String connectionId, String newPhysicalPath) {
        long now = Instant.now().toEpochMilli();
        jdbc.update(
                "UPDATE file_artifact SET status='archived', scope='workspace', " +
                        "connection_id=?, physical_path=?, archived_at=?, updated_at=? WHERE id=?",
                connectionId, newPhysicalPath, now, now, id);
    }

    @Override
    public void deleteTransientByForSession(String sessionId) {
        jdbc.update(
                "DELETE FROM file_artifact WHERE session_id=? AND status IN ('temporary','candidate')",
                sessionId);
    }

    @Override
    public void detachArchivedFromSession(String sessionId) {
        jdbc.update(
                "UPDATE file_artifact SET session_id=NULL WHERE session_id=? AND status='archived'",
                sessionId);
    }

    @Override
    public void deleteById(String id) {
        jdbc.update("DELETE FROM file_artifact WHERE id=?", id);
    }

    @Override
    public void updateMetadata(String id, long sizeBytes, long updatedAtMillis) {
        jdbc.update("UPDATE file_artifact SET size_bytes=?, updated_at=? WHERE id=?",
                sizeBytes, updatedAtMillis, id);
    }

    private RowMapper<FileArtifact> mapper() {
        return (ResultSet rs, int rowNum) -> new FileArtifact(
                rs.getString("id"),
                FileArtifactScope.fromDb(rs.getString("scope")),
                FileArtifactStatus.fromDb(rs.getString("status")),
                FileArtifactKind.fromDb(rs.getString("kind")),
                rs.getString("session_id"),
                rs.getString("connection_id"),
                rs.getString("filename"),
                rs.getString("physical_path"),
                rs.getLong("size_bytes"),
                rs.getString("mime_type"),
                rs.getString("title"),
                rs.getString("summary"),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                Instant.ofEpochMilli(rs.getLong("updated_at")),
                getInstantOrNull(rs, "archived_at"),
                readMetadata(rs.getString("metadata_json")));
    }

    private static Instant getInstantOrNull(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : Instant.ofEpochMilli(v);
    }

    private String writeMetadata(Map<String, Object> m) {
        if (m == null || m.isEmpty()) return null;
        try { return json.writeValueAsString(m); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    private Map<String, Object> readMetadata(String s) {
        if (s == null || s.isBlank()) return Map.of();
        try { return json.readValue(s, new TypeReference<Map<String, Object>>() {}); }
        catch (JsonProcessingException e) { return Map.of(); }
    }
}
```

### 13.1 JdbcFileArtifactRepositoryIT

- [x] 创建 `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepositoryIT.java`：

```java
package com.datatalk.infra.fileartifact;

import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Import(JdbcFileArtifactRepository.class)
@ActiveProfiles("test")
class JdbcFileArtifactRepositoryIT {

    @Autowired
    JdbcTemplate jdbc;

    JdbcFileArtifactRepository repo;

    @BeforeEach
    void setUp() {
        repo = new JdbcFileArtifactRepository(jdbc, new ObjectMapper());
    }

    @Test
    void insert_then_findById_round_trips() {
        FileArtifact a = sample("file_artifact_1", FileArtifactStatus.TEMPORARY, "ses_abc", null);
        repo.insert(a);
        var loaded = repo.findById("file_artifact_1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().filename()).isEqualTo(a.filename());
        assertThat(loaded.get().status()).isEqualTo(FileArtifactStatus.TEMPORARY);
    }

    @Test
    void findBySession_returns_all_statuses_for_that_session() {
        repo.insert(sample("a1", FileArtifactStatus.TEMPORARY, "ses_x", null));
        repo.insert(sample("a2", FileArtifactStatus.CANDIDATE, "ses_x", null));
        repo.insert(sample("a3", FileArtifactStatus.TEMPORARY, "ses_other", null));
        List<FileArtifact> rows = repo.findBySession("ses_x");
        assertThat(rows).extracting(FileArtifact::id).containsExactlyInAnyOrder("a1", "a2");
    }

    @Test
    void findCandidatesBySession_filters_status() {
        repo.insert(sample("t1", FileArtifactStatus.TEMPORARY, "ses_x", null));
        repo.insert(sample("c1", FileArtifactStatus.CANDIDATE, "ses_x", null));
        var candidates = repo.findCandidatesBySession("ses_x");
        assertThat(candidates).extracting(FileArtifact::id).containsExactly("c1");
    }

    @Test
    void findArchivedByConnection_filters_status_and_scope() {
        repo.insert(archived("ar1", "conn_p"));
        repo.insert(archived("ar2", "conn_p"));
        repo.insert(archived("ar3", "conn_other"));
        var rows = repo.findArchivedByConnection("conn_p");
        assertThat(rows).extracting(FileArtifact::id).containsExactlyInAnyOrder("ar1", "ar2");
    }

    @Test
    void updateStatus_promotes_temporary_to_candidate() {
        repo.insert(sample("a1", FileArtifactStatus.TEMPORARY, "ses_x", null));
        repo.updateStatus("a1", FileArtifactStatus.CANDIDATE);
        assertThat(repo.findById("a1").orElseThrow().status())
                .isEqualTo(FileArtifactStatus.CANDIDATE);
    }

    @Test
    void deleteTransientByForSession_removes_temp_and_candidate_only() {
        repo.insert(sample("t1", FileArtifactStatus.TEMPORARY, "ses_x", null));
        repo.insert(sample("c1", FileArtifactStatus.CANDIDATE, "ses_x", null));
        repo.insert(archivedForSession("ar1", "ses_x", "conn_p"));
        repo.deleteTransientByForSession("ses_x");
        assertThat(repo.findBySession("ses_x")).extracting(FileArtifact::id).containsExactly("ar1");
    }

    @Test
    void detachArchivedFromSession_nulls_session_id() {
        repo.insert(archivedForSession("ar1", "ses_x", "conn_p"));
        repo.detachArchivedFromSession("ses_x");
        assertThat(repo.findBySession("ses_x")).isEmpty();
        // The archived row still exists but its session_id is now NULL — verify via direct SQL count
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_artifact WHERE id='ar1' AND session_id IS NULL",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void markArchived_sets_scope_status_path_archived_at() {
        repo.insert(sample("c1", FileArtifactStatus.CANDIDATE, "ses_x", null));
        repo.markArchived("c1", "conn_p", "/abs/workspaces/conn_p/x.md");
        var loaded = repo.findById("c1").orElseThrow();
        assertThat(loaded.status()).isEqualTo(FileArtifactStatus.ARCHIVED);
        assertThat(loaded.scope()).isEqualTo(FileArtifactScope.WORKSPACE);
        assertThat(loaded.physicalPath()).isEqualTo("/abs/workspaces/conn_p/x.md");
        assertThat(loaded.connectionId()).isEqualTo("conn_p");
        assertThat(loaded.archivedAtOpt()).isPresent();
    }

    @Test
    void updateMetadata_updates_size_and_mtime() {
        repo.insert(sample("a1", FileArtifactStatus.TEMPORARY, "ses_x", null));
        Instant target = Instant.parse("2026-04-29T10:00:00Z");
        repo.updateMetadata("a1", 9999L, target.toEpochMilli());
        var loaded = repo.findById("a1").orElseThrow();
        assertThat(loaded.sizeBytes()).isEqualTo(9999L);
        assertThat(loaded.updatedAt()).isEqualTo(target);
    }

    @Test
    void deleteById_removes_row() {
        repo.insert(sample("a1", FileArtifactStatus.TEMPORARY, "ses_x", null));
        repo.deleteById("a1");
        assertThat(repo.findById("a1")).isEmpty();
    }

    private static FileArtifact sample(String id, FileArtifactStatus s, String sid, String connId) {
        Instant now = Instant.now();
        return new FileArtifact(id, FileArtifactScope.SESSION, s, FileArtifactKind.OTHER,
                sid, connId, "x.md", "/abs/sessions/" + sid + "/x.md",
                123L, "text/markdown", null, null, now, now, null, Map.of());
    }

    private static FileArtifact archived(String id, String connId) {
        Instant now = Instant.now();
        return new FileArtifact(id, FileArtifactScope.WORKSPACE, FileArtifactStatus.ARCHIVED,
                FileArtifactKind.REPORT, null, connId, "x.md",
                "/abs/workspaces/" + connId + "/x.md",
                123L, "text/markdown", null, null, now, now, now, Map.of());
    }

    private static FileArtifact archivedForSession(String id, String sid, String connId) {
        Instant now = Instant.now();
        return new FileArtifact(id, FileArtifactScope.WORKSPACE, FileArtifactStatus.ARCHIVED,
                FileArtifactKind.REPORT, sid, connId, "x.md",
                "/abs/workspaces/" + connId + "/x.md",
                123L, "text/markdown", null, null, now, now, now, Map.of());
    }
}
```

- [x] 运行：

```bash
cd server && mvn -pl data-talk-infrastructure test -Dtest=JdbcFileArtifactRepositoryIT -q
```

预期：10 个测试通过。

### 13.2 关键收尾

- [x] 推 jar：

```bash
cd server && mvn install -pl data-talk-application -am -DskipTests -q
```

- [x] commit：

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/ \
        server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/ \
        server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/ \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/
git commit -m "feat(application,infra): add FileArtifactService, repository and JDBC impl"
```

## Task 14: AgentPromptBuilder — `{{ACTIVE_SESSION_DIR}}` 占位扩展

**目的：** 给 AGENTS.md 模板提供新占位符 `{{ACTIVE_SESSION_DIR}}`，由当前活跃 session 渲染为 `./sessions/<sid>/`；无活跃 session 时渲染为 `<no active session>`。

### 14.1 ActiveSessionDirProvider 接口

- [x] 创建 `server/data-talk-application/src/main/java/com/datatalk/application/stage/ActiveSessionDirProvider.java`：

```java
package com.datatalk.application.stage;

import java.util.Optional;

/**
 * Resolves the relative path of the currently-active session subdir
 * for the {@code {{ACTIVE_SESSION_DIR}}} placeholder in AGENTS.md.
 *
 * <p>Implemented in adapter layer where session context is bound (e.g. from
 * the active OpenCode session id propagated through the request/SSE flow).
 *
 * <p>Returns {@code Optional.empty()} if no session is active right now;
 * the prompt renderer will substitute a sentinel value.
 */
public interface ActiveSessionDirProvider {
    Optional<String> currentSessionId();
}
```

### 14.2 修改 AgentPromptBuilder

- [x] 修改 `server/data-talk-application/src/main/java/com/datatalk/application/stage/AgentPromptBuilder.java`：在现有渲染逻辑后追加新占位处理。

读取当前文件并定位 `render(...)` 方法，把 `return template.replace(PLACEHOLDER, digest);` 改为先做 STAGE_TAB_DIGEST 替换，再做 ACTIVE_SESSION_DIR 替换：

```java
    // ... existing code ...

    private static final String PLACEHOLDER_STAGE_DIGEST = "{{STAGE_TAB_DIGEST}}";
    private static final String PLACEHOLDER_ACTIVE_DIR = "{{ACTIVE_SESSION_DIR}}";
    private static final String NO_ACTIVE_SENTINEL = "<no active session>";

    // Replace existing PLACEHOLDER constant references accordingly.

    private final StageTabRepository repo;
    private final SessionTitleLookup lookup;
    private final ActiveSessionDirProvider activeDir;

    public AgentPromptBuilder(StageTabRepository repo,
                              SessionTitleLookup lookup,
                              ActiveSessionDirProvider activeDir) {
        this.repo = repo;
        this.lookup = lookup;
        this.activeDir = activeDir;
    }

    public String render(String template) {
        String result = template;
        if (result.contains(PLACEHOLDER_STAGE_DIGEST)) {
            String digest = renderDigest();
            if (digest.length() > MAX_RENDERED_CHARS) {
                digest = digest.substring(0, MAX_RENDERED_CHARS - 3) + "...";
            }
            result = result.replace(PLACEHOLDER_STAGE_DIGEST, digest);
        }
        if (result.contains(PLACEHOLDER_ACTIVE_DIR)) {
            String value = activeDir.currentSessionId()
                    .map(sid -> "./sessions/" + sid + "/")
                    .orElse(NO_ACTIVE_SENTINEL);
            result = result.replace(PLACEHOLDER_ACTIVE_DIR, value);
        }
        return result;
    }
```

完整修改后的 `AgentPromptBuilder.java` 应包含：
1. 两个 placeholder 常量
2. 三参数构造函数（新增 `ActiveSessionDirProvider`）
3. `render(String)` 方法支持两个占位的独立替换
4. 原 `renderDigest()` 方法保留不动

### 14.3 AgentPromptBuilderTest 扩展

- [x] 修改 `server/data-talk-application/src/test/java/com/datatalk/application/stage/AgentPromptBuilderTest.java`：在已有测试外新增 4 个测试覆盖新占位行为。如果文件不存在，先创建：

```java
package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPromptBuilderTest {

    StageTabRepository repo;
    SessionTitleLookup lookup;
    ActiveSessionDirProvider activeDir;
    AgentPromptBuilder builder;

    @BeforeEach
    void setUp() {
        repo = mock(StageTabRepository.class);
        lookup = mock(SessionTitleLookup.class);
        activeDir = mock(ActiveSessionDirProvider.class);
        when(repo.recentByLastTouched(10)).thenReturn(List.<StageTab>of());
        when(repo.countActive()).thenReturn(0);
        when(repo.countArchived()).thenReturn(0);
        when(lookup.titlesByIds(List.of())).thenReturn(Map.of());
        builder = new AgentPromptBuilder(repo, lookup, activeDir);
    }

    @Test
    void render_substitutes_active_session_dir_when_session_present() {
        when(activeDir.currentSessionId()).thenReturn(Optional.of("ses_abc"));
        String result = builder.render("Subdir is {{ACTIVE_SESSION_DIR}}.");
        assertThat(result).isEqualTo("Subdir is ./sessions/ses_abc/.");
    }

    @Test
    void render_substitutes_sentinel_when_no_active_session() {
        when(activeDir.currentSessionId()).thenReturn(Optional.empty());
        String result = builder.render("Subdir is {{ACTIVE_SESSION_DIR}}.");
        assertThat(result).isEqualTo("Subdir is <no active session>.");
    }

    @Test
    void render_handles_both_placeholders() {
        when(activeDir.currentSessionId()).thenReturn(Optional.of("ses_x"));
        String tpl = "Tabs:{{STAGE_TAB_DIGEST}} dir={{ACTIVE_SESSION_DIR}}";
        String result = builder.render(tpl);
        assertThat(result).contains("./sessions/ses_x/");
        assertThat(result).doesNotContain("{{ACTIVE_SESSION_DIR}}");
        assertThat(result).doesNotContain("{{STAGE_TAB_DIGEST}}");
    }

    @Test
    void render_passthrough_when_no_placeholders() {
        when(activeDir.currentSessionId()).thenReturn(Optional.of("ses_x"));
        String tpl = "no markers here";
        assertThat(builder.render(tpl)).isEqualTo(tpl);
    }
}
```

- [x] 提供一个简单的默认 `ActiveSessionDirProvider` bean，避免破坏现有 wiring。在 `FileArtifactConfiguration` 中加：

```java
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public ActiveSessionDirProvider defaultActiveSessionDirProvider() {
        return Optional::empty;  // implement currentSessionId() returning Optional.empty()
    }
```

> 注：上面用了 `Optional::empty` 作为 `ActiveSessionDirProvider` 的方法引用——但因为 `ActiveSessionDirProvider.currentSessionId()` 返回 `Optional<String>`，正确写法是：
>
> ```java
> return () -> Optional.empty();
> ```

修正：

```java
    @Bean
    @ConditionalOnMissingBean
    public ActiveSessionDirProvider defaultActiveSessionDirProvider() {
        return () -> Optional.empty();
    }
```

> Adapter 层后续会注入真正的 active session 提供者覆盖此默认值（Part 3 接 OpenCode 当前 session 上下文）。

### 14.4 编译 + 运行测试

- [x] 编译验证：

```bash
cd server && mvn compile -q -pl data-talk-application
```

- [x] 运行：

```bash
cd server && mvn -pl data-talk-application test -Dtest=AgentPromptBuilderTest -q
```

预期：4 个新测试通过；现有测试（如有）也通过。

### 14.5 关键收尾 + commit

- [x] 推 jar：

```bash
cd server && mvn install -pl data-talk-application -am -DskipTests -q
```

- [x] commit：

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/stage/ActiveSessionDirProvider.java \
        server/data-talk-application/src/main/java/com/datatalk/application/stage/AgentPromptBuilder.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactConfiguration.java \
        server/data-talk-application/src/test/java/com/datatalk/application/stage/AgentPromptBuilderTest.java
git commit -m "feat(application): add ACTIVE_SESSION_DIR placeholder to AgentPromptBuilder"
```

## Task 15: REST 控制器骨架

**目的：** 提供最小 REST 表面让 Part 1 的 Demo（手工 curl 走通归档）成立。归档端点本 Part 暂时只实现 markCandidate；物理 mv 到 workspaces/ 留 Part 5 实现。

### 15.1 FileArtifactController

- [x] 创建 `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/FileArtifactController.java`：

```java
package com.datatalk.adapter.controller;

import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.domain.fileartifact.FileArtifact;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FileArtifactController {

    private final FileArtifactService svc;

    public FileArtifactController(FileArtifactService svc) {
        this.svc = svc;
    }

    @GetMapping("/sessions/{sessionId}/files")
    public List<FileArtifact> listForSession(@PathVariable String sessionId) {
        return svc.listForSession(sessionId);
    }

    @GetMapping("/connections/{connectionId}/files")
    public List<FileArtifact> listForConnection(@PathVariable String connectionId) {
        return svc.listArchivedForConnection(connectionId);
    }

    /**
     * Marks the artifact as a CANDIDATE (state machine TEMPORARY → CANDIDATE).
     * Physical mv to workspaces/ happens in Part 5's archive endpoint.
     */
    @PostMapping("/files/{fileArtifactId}/mark-candidate")
    public ResponseEntity<Void> markCandidate(@PathVariable String fileArtifactId) {
        svc.markCandidate(fileArtifactId);
        return ResponseEntity.noContent().build();
    }
}
```

### 15.2 FileArtifactControllerIT

- [x] 创建 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/FileArtifactControllerIT.java`：

```java
package com.datatalk.adapter.controller;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@ActiveProfiles("test")
class FileArtifactControllerIT {

    @Autowired
    WebApplicationContext ctx;

    @Autowired
    FileArtifactRepository repo;

    MockMvc mvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
    }

    @Test
    void GET_sessions_files_returns_session_artifacts() throws Exception {
        repo.insert(stub("a1", FileArtifactStatus.TEMPORARY, "ses_x", null));
        repo.insert(stub("a2", FileArtifactStatus.CANDIDATE, "ses_x", null));
        repo.insert(stub("a3", FileArtifactStatus.TEMPORARY, "ses_other", null));

        mvc.perform(get("/api/sessions/ses_x/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void POST_mark_candidate_promotes_status() throws Exception {
        repo.insert(stub("a1", FileArtifactStatus.TEMPORARY, "ses_x", null));

        mvc.perform(post("/api/files/a1/mark-candidate"))
                .andExpect(status().isNoContent());

        assertThat(repo.findById("a1").orElseThrow().status())
                .isEqualTo(FileArtifactStatus.CANDIDATE);
    }

    private static FileArtifact stub(String id, FileArtifactStatus s, String sid, String connId) {
        Instant now = Instant.now();
        return new FileArtifact(id, FileArtifactScope.SESSION, s, FileArtifactKind.OTHER,
                sid, connId, "x.md", "/abs/sessions/" + sid + "/x.md",
                100L, "text/markdown", null, null, now, now, null, Map.of());
    }
}
```

- [x] 运行：

```bash
cd server && mvn -pl data-talk-adapter test -Dtest=FileArtifactControllerIT -q
```

预期：2 个测试通过。

### 15.3 commit

- [x] commit：

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/FileArtifactController.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/FileArtifactControllerIT.java
git commit -m "feat(adapter): add FileArtifactController REST skeleton"
```

## Task 16: 完整回归 + Part 1 收尾

### 16.1 完整测试套件

- [x] 跑完整测试，确认无回归：

```bash
cd server && mvn clean verify -q
```

预期：全部通过；现有 V1-V13 migration 测试、`artifacts`（payload 型）相关测试均不受影响。

### 16.2 现有 artifacts 表共存验证

- [x] 在 `FlywayMigrationIT` 加最后一个回归测试：在同一 schema 下能 INSERT 现有 `artifacts`（payload 型）行 + 新 `file_artifact` 行而互不干扰：

```java
@Test
void v14_coexists_with_existing_artifacts_table() {
    long now = System.currentTimeMillis();
    // Existing artifacts (payload type) — 沿用 V3 schema
    jdbc.update(
        "INSERT INTO artifacts(id, version, session_id, kind, produced_by, payload_ref, " +
        "payload_size, pinned, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
        "art_legacy", 1, "ses_x", "table", "test", "ref://x", 100, 0, now);
    // New file_artifact
    jdbc.update(
        "INSERT INTO file_artifact(id, scope, status, kind, session_id, filename, " +
        "physical_path, size_bytes, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
        "fart_new", "session", "temporary", "other", "ses_x", "x.md", "/abs/x.md", 100, now, now);
    // Assert both rows visible
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM artifacts", Integer.class)).isPositive();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_artifact", Integer.class)).isEqualTo(1);
}
```

- [x] 运行：

```bash
cd server && mvn -pl data-talk-adapter test -Dtest=FlywayMigrationIT -q
```

预期：通过。

### 16.3 手工 Demo（可选，作为收尾验证）

按照 spec §9 的 M1 demo 描述，REST 注入路径手工归档应能走通：

```bash
# 1. 启动后端
cd server && mvn spring-boot:run -pl data-talk-adapter

# 2. 直接 SQL INSERT 一条 file_artifact 行（模拟 Part 2 watcher 检测产物）
sqlite3 ~/.data-talk/data-talk.db "
INSERT INTO file_artifact(id, scope, status, kind, session_id, filename,
  physical_path, size_bytes, created_at, updated_at)
VALUES('fart_demo', 'session', 'temporary', 'other', 'ses_demo',
  'x.md', '/tmp/x.md', 100, 1714370400000, 1714370400000);"

# 3. curl 列表
curl http://localhost:8080/api/sessions/ses_demo/files

# 4. curl 升 candidate
curl -X POST http://localhost:8080/api/files/fart_demo/mark-candidate

# 5. 再列表，验证 status='candidate'
curl http://localhost:8080/api/sessions/ses_demo/files
```

- [x] 此步骤为可选 smoke test；如果 16.1 + 16.2 通过即可宣告 Part 1 完成。（2026-04-29：未执行手工 smoke，`mvn clean verify -q` + focused tests 已通过。）
- [x] 跑完后清理测试数据：（2026-04-29：手工 smoke 未执行，无 `fart_demo` 测试数据需要清理。）

```bash
sqlite3 ~/.data-talk/data-talk.db "DELETE FROM file_artifact WHERE id='fart_demo';"
```

### 16.4 文档 housekeeping（CLAUDE.md 强制）

- [x] 在 `docs/exec-plans/index.md` 把本 Part 1 标注为 `(in_progress)` → 实施过程中不动，等本 Part 全 task 完成后再移到 Completed
- [x] 在 spec 文件末尾 `**审阅状态**` 行下追加：

```markdown
**Part 1 (Migration & Domain) status**：执行中 / 已完成 — 落地 V14 migration、Domain `FileArtifact` 类型与 enum、DtEvent 5 事件、`SessionWorkdirService` 子目录骨架、`AgentPromptBuilder` `{{ACTIVE_SESSION_DIR}}` 占位扩展、REST skeleton。后续 Part 2 (watcher) / Part 3 (MCP+AGENTS) / Part 4 (前端) / Part 5 (删除流+治理) 待续。
```

实际"已完成"标注在所有 task 都打勾后填。

### 16.5 最终 commit

- [x] 把 housekeeping 改动一并提交：

```bash
git add docs/exec-plans/index.md \
        docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md
git commit -m "docs: update exec-plans index for file-artifact part 1 progress"
```

---

## Self-Review Checklist (执行前/执行中检查)

- [x] **Spec 覆盖**：检查 spec §1.1（artifacts 区分）、§3.1（子目录软隔离）、§3.2（依赖分层）、§4（migration）、§5.4（路径校验 8 条）、§6.1（4 状态）、§6.7（DtEvent 5 个）—— 本 Part 是否都有 task 落地。**status**：✅
- [x] **Placeholder 扫描**：本计划无 TBD/TODO；每段代码都是完整可粘贴的。
- [x] **类型一致性**：`FileArtifactStatus` / `FileArtifactScope` / `FileArtifactKind` 在所有 task 中拼写一致；`fileArtifactId` 字段名贯穿 controller/service/repo/event。
- [x] **CLAUDE.md "Backend Run vs Compile"**：每次跨 module 改动后都有 `mvn install -pl <module> -am -DskipTests`。
- [x] **数据源兼容性 Gate**：本 Part **不涉及** 任何 DB 类型新增/变更；标记 N/A（spec §2.3 已声明）。

---

**Part 1 完成定义（Definition of Done）**：
1. 所有 16 个 Task 的 checkbox 全部打勾
2. `mvn clean verify` 全绿
3. spec 文档 §13 后追加 Part 1 完成状态
4. `docs/exec-plans/index.md` 把本 Part 移到 Completed 行
5. 准备好 Part 2 plan 写作（watcher/reconcile/symlink 拒绝）
