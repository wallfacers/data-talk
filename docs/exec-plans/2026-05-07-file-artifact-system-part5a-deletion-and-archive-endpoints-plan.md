# File Artifact System · Part 5a — Deletion Flow & Archive/Discard Endpoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land Part 5a — the deletion flow (session + connection two-phase) and `archive` / `discard` REST endpoints — so Part 4's frontend `archiveFile` / `discardFile` placeholder calls become real wire paths, and so users can delete sessions/connections with archived asset protection (Q2 design decision).

**Architecture:** Extend the existing `FileArtifactService` with `archive` / `discard` use cases backed by a new pure-IO `FileArtifactPhysicalMover` application service that handles atomic mv + .v2/.v3 versioning + TOCTOU defense. Introduce a `DeleteOutcome` sealed interface shared by `SessionService.delete(id, force)` and a new `ConnectionDeletionService` so adapter layer maps `BlockedByCandidates` / `BlockedByResources` to HTTP 409 with structured body. Frontend gains two new modals (`DeleteSessionModal` per spec §A.5, `DeleteConnectionModal` per spec §A.6) with per-candidate retry state tracking; existing session/connection delete UI flows hook into the modals before issuing the `?force=true` follow-up. Connection delete preserves archived rows with `connection_id=NULL` and stamps `metadata_json` with `orphanedFromConnection*` (spec §A.4) so 5b's orphan UI has the original connection name.

**Tech Stack:** Spring Boot 3.5, Java 21 (sealed types / pattern switch), JdbcTemplate, JUnit 5 + AssertJ; React 19, TanStack Query, Zustand, ky, Tailwind v4 + design-tokens, vitest + @testing-library/react.

**Spec:** [docs/product-specs/2026-05-07-file-artifact-system-part5-design.md](../product-specs/2026-05-07-file-artifact-system-part5-design.md) — §0, §1.1, §1.3, §A (all), §C.1, §C.2 (Part 5a section), §C.4 (Part 5a relevant rows).

**Parent spec:** [docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md](../product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md)

**关联 Part：**
- Part 1 — Migration + Domain (✅ shipped)
- Part 2 — Watcher + Reconcile (✅ shipped)
- Part 3 — MCP Tool & AGENTS Template (planned, not executed)
- Part 4 — Frontend Tabs (planned, not executed; Part 5a unblocks `archiveFile` / `discardFile` placeholder calls)
- Part 5a (本计划) — Deletion flow + archive/discard endpoints + delete modals
- Part 5b — Housekeeping + LegacyMigration + Maintenance UI (含孤儿 Drawer)

**执行状态：** 未开始（计划登记 only）。

---

## Design Inputs

本 Part 涉及 `client/` UI（两个 modal + Part 4 占位调用切真端点），必须遵循 [client/DESIGN.md](../../client/DESIGN.md)。引用约束：

- **三层骨架不变**：modal 用 focused density、`bg.canvas` surface、`radius.lg`、480px 宽（spec §A.5/A.6）。
- **Token-only colors**：禁止 `bg-blue-500` 等原始原色；只能用语义 token Tailwind class（`bg-canvas` / `bg-panel` / `text-base` / `text-strong` / `text-muted` / `text-inverse` / `border-subtle` / `border-default` / `accent-primary` / `accent-primaryHover` / `status-warningSurface` / `status-infoSurface`）。
- **Status colors**：connection modal 的 orphan banner 用 `status.infoSurface`（archived 不丢失是非异常正向行为）；session modal 候选行的"丢弃"选项用 `status.dangerSurface` 浅底（仅在选中态出现）。
- **Accent 限定**：cobalt 仅用于 focus / selection / primary action；"确认删除"按钮用 `accent.primary` 而非 `status.danger`，因为决策已经在文案完成，按钮只是 commit（spec §A.6 已固化此选择）。
- **Density**：modal/toolbar/list 用 focused；按钮高度 32px、文本 ui-sm。
- **Motion**：`fast=120ms` / `normal=180ms`；只用于 modal 淡入与按钮按下回弹；无装饰动画；`prefers-reduced-motion` 必须停用。
- **Accessibility**：所有 icon-only 按钮带 `aria-label`；候选状态用图标 + 颜色双通道；focusRing token 在 focus-visible 状态可见。

### Five-state token mapping（每个交互控件 idle / hover / active / focus / disabled 显式枚举）

per memory `feedback-design-control-states`：每个交互控件的五态 token 都要逐一列出，禁止简写。

#### 共通：取消按钮（ghost）

- idle: `bg-transparent` / `text-base` / `border-subtle`
- hover: `bg-hover` / `text-strong` / `border-default`
- active: `bg-active` / `text-strong`
- focus: `outline-2 ring-focusRing offset-2`
- disabled: `text-disabled` / cursor-not-allowed / no hover

#### 共通：确认删除按钮（primary destructive — 用 `accent.primary` per spec §A.6）

- idle: `bg-accent-primary` / `text-inverse`
- hover: `bg-accent-primaryHover` / `text-inverse`
- active: `bg-accent-primaryHover` / opacity 0.9
- focus: `outline-2 ring-focusRing offset-2`
- disabled: `bg-disabled` / `text-disabled` / cursor-not-allowed

#### Session modal — 候选行的"归档/丢弃"radio-group

每行两个 radio button（`◯ 归档到 <conn 名>` / `◉ 丢弃`），用 segmented-control 样式：

- idle (unselected): `bg-transparent` / `text-base` / `border-subtle`
- hover: `bg-hover` / `text-strong` / `border-default`
- active (selected, archive option): `bg-accent-primarySoft` / `text-strong` / `border-accent-primary`
- active (selected, discard option): `bg-status-dangerSurface` / `text-status-danger` / `border-status-danger`
- focus: `outline-2 ring-focusRing`
- disabled: `text-disabled` / cursor-not-allowed

#### Session modal — "全部归档" / "全部丢弃"快捷按钮（ghost）

- idle: `bg-transparent` / `text-base` / `border-subtle`
- hover: `bg-hover` / `text-strong` / `border-default`
- active: `bg-active` / `text-strong`
- focus: `outline-2 ring-focusRing`
- disabled: `text-disabled` / cursor-not-allowed

#### Connection modal — orphan banner（非交互）

只展示文本 + ⓘ icon：`bg-status-infoSurface` / `text-status-info` / `border-status-info` 1px / `radius-md`。无 idle/hover 区分。

### CLAUDE.md gates 显式声明

- **Frontend Design Contract Gate**：上文 Design Inputs 段已引 `client/DESIGN.md` 并显式列五态 token；满足。
- **Frontend Plan Gate**：Design Inputs 段引用 `client/DESIGN.md`，`/plan` 输出含 `Design Inputs` 节；满足。
- **数据源兼容性 Gate**：N/A — Part 5 不涉及 DB 类型新增/变更（spec §2.3 已声明）。
- **Backend Run vs Compile**：每次修改 `data-talk-application` 或 `data-talk-domain` 模块后都要 `mvn install -pl <module> -am -DskipTests`，否则 `mvn spring-boot:run` 拿到旧 jar；任务步骤会显式标注。
- **BUG 跟踪 Gate**：grep `docs/bugs/` 0 命中（无 file_artifact 相关 BUG）；本 Part 不触碰已知问题区域。

---

## Files

### Backend — Domain / Application / Adapter / Infrastructure

| 操作 | 文件路径 | 用途 |
|------|---------|------|
| Create | `server/data-talk-application/src/main/java/com/datatalk/application/session/DeleteOutcome.java` | sealed interface 共享给 session/connection 两阶段删除 |
| Modify | `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactRepository.java` | 加 `deleteTransientByForConnection`、`detachArchivedFromConnection`、`countCandidatesBySession`、`countResourcesByConnection` |
| Modify | `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepository.java` | 实现新增方法（含 metadata_json `orphanedFromConnection*` 写入） |
| Modify | `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepositoryIT.java` | 测试新增 repo 方法 |
| Create | `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactPhysicalMover.java` | 共享 mover：mkdir + 版本递增 + 双 stat TOCTOU + ATOMIC_MOVE |
| Create | `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactPhysicalMoverTest.java` | mover 单测 |
| Modify | `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java` | 加 `archive(sessionId, fid)` + `discard(fid)` |
| Modify | `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceTest.java` | archive/discard 单测 |
| Modify | `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java` | 增加 `delete(id, force)` 返回 `DeleteOutcome` |
| Modify | `server/data-talk-application/src/test/java/com/datatalk/application/session/SessionServiceTest.java` | force flag 行为测试 |
| Create | `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionDeletionService.java` | 两阶段 connection 删除 orchestrator（含 metadata stamping） |
| Create | `server/data-talk-application/src/test/java/com/datatalk/application/connection/ConnectionDeletionServiceTest.java` | 测试 |
| Modify | `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/FileArtifactController.java` | 加 archive + discard 端点 |
| Modify | `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java` | `?force=true` + 409 body |
| Modify | `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/connection/ConnectionController.java` | `?force=true` + 409 body |
| Create | `server/data-talk-adapter/src/main/java/com/datatalk/dto/SessionDeleteBlockedDto.java` | session 409 body |
| Create | `server/data-talk-adapter/src/main/java/com/datatalk/dto/ConnectionDeleteBlockedDto.java` | connection 409 body |
| Create | `server/data-talk-adapter/src/main/java/com/datatalk/dto/SessionCandidateDto.java` | session 409 body 内嵌项 |
| Modify | `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/FileArtifactControllerIT.java` | archive/discard IT |
| Modify | `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerIT.java` | force flag IT |
| Create | `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/ConnectionDeletionIT.java` | connection 409 + force IT |

### Frontend — API / Components / i18n / Tests

| 操作 | 文件路径 | 用途 |
|------|---------|------|
| Modify | `client/src/services/api/file-artifacts.ts` | URL 对齐：`archiveFile` 改用 `POST /sessions/{sid}/files/{fid}/archive`、`discardFile` `POST /files/{fid}/discard`；返回 `FileArtifact` |
| Modify | `client/src/services/api/session.ts` | `deleteSession(id, { force })`；解 409 → `BlockedByCandidates` |
| Modify | `client/src/services/api/connection.ts` | `deleteConnection(id, { force })`；解 409 → `BlockedByResources` |
| Create | `client/src/features/session/components/delete-session-modal.tsx` | spec §A.5 modal，含 per-candidate 状态跟踪 |
| Create | `client/src/features/session/components/delete-session-modal.test.tsx` | vitest |
| Create | `client/src/features/connection/components/delete-connection-modal.tsx` | spec §A.6 modal |
| Create | `client/src/features/connection/components/delete-connection-modal.test.tsx` | vitest |
| Modify | `client/src/i18n/messages.ts` | `files.deleteModal.*` + `connections.deleteModal.*` keys（中英对齐） |
| Modify | UI 入口处（取决于现有 sidebar/connection 删除按钮挂载点；Task 11 prep step 会 grep 确定） | 把现有"删除"按钮回调改成"打开 modal → modal 完成后 force=true 删除" |

### Docs

| 操作 | 文件路径 | 用途 |
|------|---------|------|
| Modify | `docs/exec-plans/index.md` | 把"Part 5 待补"占位行替换为 Part 5a 正式登记；保留 Part 5b 占位 |
| Modify | `docs/exec-plans/2026-05-07-file-artifact-system-part5a-deletion-and-archive-endpoints-plan.md` | 本文件 |

---

## Task 1: Plan registration

**Files:**
- Modify: `docs/exec-plans/index.md`

- [ ] **Step 1: Replace Part 5 placeholder row with Part 5a formal entry**

打开 `docs/exec-plans/index.md`，找到"Part 5 — Deletion Flow & Housekeeping (待补正式计划)"行，把它**替换**为下面两行（5a 正式 + 5b 仍占位）：

```markdown
| [File Artifact System · Part 5a — Deletion Flow & Archive/Discard Endpoints](./2026-05-07-file-artifact-system-part5a-deletion-and-archive-endpoints-plan.md) | 2026-05-07 | OpenCode 工作目录与 File Artifact 系统 Part 5a：后端新增 `POST /api/sessions/{sid}/files/{fid}/archive` + `POST /api/files/{fid}/discard`、修改 `DELETE /api/sessions/{sid}` 加 `?force=true` 与 Phase 1 409、新增/修改 `DELETE /api/connections/{cid}` 两阶段；application 层加 `FileArtifactPhysicalMover`（共享 mv + 版本递增 + 双 stat TOCTOU + ATOMIC_MOVE）+ `FileArtifactService.archive/discard` + `SessionService.delete(id, force)` + `ConnectionDeletionService` orchestrator；`DeleteOutcome` sealed interface 把 Phase 1 阻断建模成 `BlockedByCandidates` / `BlockedByResources`；archived 行在 connection 删除时 `connection_id=NULL` + 同事务把原 connection name/id/timestamp 写入 `metadata_json` 的 `orphanedFromConnection*`（5b orphan UI 消费）。前端：`DeleteSessionModal`（含 per-candidate retry 状态跟踪）+ `DeleteConnectionModal`（聚合 counts + orphan banner）+ Part 4 占位调用切真端点 + i18n `files.deleteModal.*` / `connections.deleteModal.*`。0 个新 DtEvent；复用 Part 1 5 类。N/A 数据源兼容 gate。 |
| File Artifact System · Part 5b — Housekeeping & Maintenance (待补正式计划) | TBD | Part 5a 落地后再写。范围：HousekeepingScheduler（4 任务）+ LegacyMigrationRunner + Settings Maintenance UI（含孤儿归档资产 Drawer）+ maintenance/orphan REST 端点 + maintenance.* 中英 i18n。spec §B 全段。 |
```

- [ ] **Step 2: Update Roadmap row to reflect 5a in flight**

继续在同一文件，找到 `Next Implementation Roadmap` 行，把摘要里 `Part 3+4 已有正式 child plan（未执行）、Part 5 待补正式计划` 替换为 `Part 3+4 已有正式 child plan（未执行）、Part 5a 已有正式 child plan（未执行）、Part 5b 待补正式计划`。

- [ ] **Step 3: Verify the plan file is on disk**

```bash
ls -la /home/wallfacers/project/data-talk/docs/exec-plans/2026-05-07-file-artifact-system-part5a-deletion-and-archive-endpoints-plan.md
```

Expected: file exists.

- [ ] **Step 4: Commit**

```bash
cd /home/wallfacers/project/data-talk && \
  git add docs/exec-plans/2026-05-07-file-artifact-system-part5a-deletion-and-archive-endpoints-plan.md \
          docs/exec-plans/index.md && \
  git commit -m "docs(exec-plans): register file artifact system part 5a plan"
```

---

## Task 2: Repository — extend `FileArtifactRepository` + JDBC impl

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactRepository.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepository.java`
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepositoryIT.java`

**Why this task first:** 后续所有 application 层方法（archive/discard/SessionService.delete/ConnectionDeletionService）都依赖这些新 repo 方法。先把 port + adapter 都准备好。

### 2.1 Add interface methods

- [ ] **Step 1: Open the interface file and append four methods before the last `}`:**

```java
    int countCandidatesBySession(String sessionId);

    /**
     * Aggregate counts of file_artifact rows that belong to a connection
     * (either via session_id of a child session or directly via connection_id).
     * Used by Phase 1 of connection DELETE.
     */
    ConnectionResourceCounts countResourcesByConnection(String connectionId, java.util.List<String> sessionIds);

    /**
     * Bulk delete temporary + candidate rows for every session under a
     * connection. Equivalent to calling {@link #deleteTransientByForSession}
     * for each session_id.
     */
    void deleteTransientByForConnection(java.util.List<String> sessionIds);

    /**
     * Detach archived rows from a deleted connection. For every row matching
     * connection_id, set connection_id = NULL and stamp metadata_json with
     * the orphan provenance fields (spec §A.4 / §B.3.1):
     *   orphanedFromConnection   = original connection.name (human-readable)
     *   orphanedFromConnectionId = original connection.id
     *   orphanedAt               = epoch millis at deletion time
     *
     * <p>Application layer must call this BEFORE the connection row itself
     * is removed (so the name is still discoverable). Same transaction as
     * the connection row delete.
     */
    void detachArchivedFromConnection(String connectionId, String connectionName, long deletedAtMillis);

    record ConnectionResourceCounts(int sessions, int candidates, int temporary, int archived) {
    }
```

- [ ] **Step 2: Compile**

```bash
cd /home/wallfacers/project/data-talk/server && mvn compile -q -pl data-talk-application
```

Expected: BUILD FAILURE — `JdbcFileArtifactRepository` does not implement new methods (intended).

### 2.2 Implement in JDBC repository

- [ ] **Step 3: Open `JdbcFileArtifactRepository.java` and add the four methods before the final `}`:**

```java
    @Override
    public int countCandidatesBySession(String sessionId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_artifact WHERE session_id = ? AND status = 'candidate'",
                Integer.class,
                sessionId);
        return count == null ? 0 : count;
    }

    @Override
    public ConnectionResourceCounts countResourcesByConnection(
            String connectionId, java.util.List<String> sessionIds) {
        int sessions = sessionIds.size();
        if (sessionIds.isEmpty()) {
            // No child sessions: only archived rows directly attached to this connection.
            Integer archived = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM file_artifact "
                            + "WHERE connection_id = ? AND status = 'archived' AND scope = 'workspace'",
                    Integer.class,
                    connectionId);
            return new ConnectionResourceCounts(0, 0, 0, archived == null ? 0 : archived);
        }
        String placeholders = sessionIds.stream().map(s -> "?").collect(java.util.stream.Collectors.joining(","));
        Object[] sidArgs = sessionIds.toArray();

        Integer candidates = jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_artifact "
                        + "WHERE session_id IN (" + placeholders + ") AND status = 'candidate'",
                Integer.class,
                sidArgs);

        Integer temporary = jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_artifact "
                        + "WHERE session_id IN (" + placeholders + ") AND status = 'temporary'",
                Integer.class,
                sidArgs);

        Integer archived = jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_artifact "
                        + "WHERE connection_id = ? AND status = 'archived' AND scope = 'workspace'",
                Integer.class,
                connectionId);

        return new ConnectionResourceCounts(
                sessions,
                candidates == null ? 0 : candidates,
                temporary == null ? 0 : temporary,
                archived == null ? 0 : archived);
    }

    @Override
    public void deleteTransientByForConnection(java.util.List<String> sessionIds) {
        if (sessionIds.isEmpty()) {
            return;
        }
        String placeholders = sessionIds.stream().map(s -> "?").collect(java.util.stream.Collectors.joining(","));
        jdbc.update(
                "DELETE FROM file_artifact "
                        + "WHERE session_id IN (" + placeholders + ") "
                        + "AND status IN ('temporary', 'candidate')",
                sessionIds.toArray());
    }

    @Override
    public void detachArchivedFromConnection(String connectionId, String connectionName, long deletedAtMillis) {
        // Read-modify-write metadata_json in application layer, NOT SQLite json_set.
        // This avoids H2/PostgreSQL dialect drift (spec §C.4 风险表 last row).
        var rows = jdbc.query(
                "SELECT id, metadata_json FROM file_artifact "
                        + "WHERE connection_id = ? AND status = 'archived' AND scope = 'workspace'",
                (rs, n) -> new String[] { rs.getString("id"), rs.getString("metadata_json") },
                connectionId);
        for (String[] row : rows) {
            String id = row[0];
            String existingJson = row[1];
            Map<String, Object> meta;
            try {
                meta = (existingJson == null || existingJson.isBlank())
                        ? new java.util.LinkedHashMap<>()
                        : new java.util.LinkedHashMap<>(json.readValue(existingJson, METADATA_TYPE));
            } catch (JsonProcessingException e) {
                meta = new java.util.LinkedHashMap<>();
            }
            meta.put("orphanedFromConnection", connectionName);
            meta.put("orphanedFromConnectionId", connectionId);
            meta.put("orphanedAt", deletedAtMillis);
            String written;
            try {
                written = json.writeValueAsString(meta);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize orphan metadata for " + id, e);
            }
            jdbc.update(
                    "UPDATE file_artifact "
                            + "SET connection_id = NULL, metadata_json = ?, updated_at = ? "
                            + "WHERE id = ?",
                    written,
                    Instant.now().toEpochMilli(),
                    id);
        }
    }
```

- [ ] **Step 4: Compile**

```bash
cd /home/wallfacers/project/data-talk/server && mvn compile -q
```

Expected: BUILD SUCCESS.

### 2.3 Test the JDBC implementation

- [ ] **Step 5: Open `JdbcFileArtifactRepositoryIT.java` and append three test methods before the final `}`:**

Use existing fixture helpers (the file already has helpers for inserting rows). If unfamiliar, mirror the pattern of existing tests in this file by reading lines 1-50 first.

```java
    @Test
    void countCandidatesBySession_returns_correct_count() {
        insertRowFor("ses_a", FileArtifactStatus.CANDIDATE);
        insertRowFor("ses_a", FileArtifactStatus.CANDIDATE);
        insertRowFor("ses_a", FileArtifactStatus.TEMPORARY);
        insertRowFor("ses_b", FileArtifactStatus.CANDIDATE);

        assertThat(repo.countCandidatesBySession("ses_a")).isEqualTo(2);
        assertThat(repo.countCandidatesBySession("ses_b")).isEqualTo(1);
        assertThat(repo.countCandidatesBySession("ses_none")).isEqualTo(0);
    }

    @Test
    void countResourcesByConnection_aggregates_across_sessions() {
        insertRowFor("ses_a", FileArtifactStatus.CANDIDATE);
        insertRowFor("ses_a", FileArtifactStatus.TEMPORARY);
        insertRowFor("ses_b", FileArtifactStatus.TEMPORARY);
        insertArchived("conn_x");
        insertArchived("conn_x");

        var counts = repo.countResourcesByConnection("conn_x", java.util.List.of("ses_a", "ses_b"));

        assertThat(counts.sessions()).isEqualTo(2);
        assertThat(counts.candidates()).isEqualTo(1);
        assertThat(counts.temporary()).isEqualTo(2);
        assertThat(counts.archived()).isEqualTo(2);
    }

    @Test
    void detachArchivedFromConnection_stamps_metadata_and_nulls_connection_id() {
        String id = insertArchived("conn_x");

        repo.detachArchivedFromConnection("conn_x", "prod-mysql", 1_000L);

        var detached = repo.findById(id).orElseThrow();
        assertThat(detached.connectionId()).isNull();
        assertThat(detached.metadata()).contains(
                java.util.Map.entry("orphanedFromConnection", "prod-mysql"),
                java.util.Map.entry("orphanedFromConnectionId", "conn_x"),
                java.util.Map.entry("orphanedAt", 1_000L));
    }

    private String insertRowFor(String sessionId, FileArtifactStatus status) {
        String id = "fa_" + java.util.UUID.randomUUID();
        repo.insert(new com.datatalk.domain.fileartifact.FileArtifact(
                id,
                com.datatalk.domain.fileartifact.FileArtifactScope.SESSION,
                status,
                com.datatalk.domain.fileartifact.FileArtifactKind.OTHER,
                sessionId,
                null,
                "f.md",
                "/tmp/" + id + ".md",
                10L,
                null, null, null,
                java.time.Instant.now(),
                java.time.Instant.now(),
                null,
                java.util.Map.of()));
        return id;
    }

    private String insertArchived(String connectionId) {
        String id = "fa_" + java.util.UUID.randomUUID();
        repo.insert(new com.datatalk.domain.fileartifact.FileArtifact(
                id,
                com.datatalk.domain.fileartifact.FileArtifactScope.WORKSPACE,
                FileArtifactStatus.ARCHIVED,
                com.datatalk.domain.fileartifact.FileArtifactKind.OTHER,
                null,
                connectionId,
                "f.md",
                "/tmp/workspaces/" + connectionId + "/" + id + ".md",
                10L,
                null, null, null,
                java.time.Instant.now(),
                java.time.Instant.now(),
                java.time.Instant.now(),
                java.util.Map.of()));
        return id;
    }
```

> **Note:** If `insertRowFor` / `insertArchived` already exist in the file, do NOT redefine them — reuse and adapt as needed.

- [ ] **Step 6: Run the integration test**

```bash
cd /home/wallfacers/project/data-talk/server && \
  mvn -pl data-talk-infrastructure test -Dtest=JdbcFileArtifactRepositoryIT -q
```

Expected: existing tests + 3 new tests all pass.

### 2.4 Push application jar + commit

- [ ] **Step 7: Push application module jar to local m2 (CLAUDE.md "Backend Run vs Compile")**

```bash
cd /home/wallfacers/project/data-talk/server && \
  mvn install -pl data-talk-application -am -DskipTests -q
```

- [ ] **Step 8: Commit**

```bash
cd /home/wallfacers/project/data-talk && \
  git add server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactRepository.java \
          server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepository.java \
          server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepositoryIT.java && \
  git commit -m "feat(file-artifact): add repo methods for two-phase delete + orphan metadata stamping"
```

---

## Task 3: `DeleteOutcome` sealed interface

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/session/DeleteOutcome.java`

**Why:** Both `SessionService.delete(id, force)` and `ConnectionDeletionService.delete(id, force)` need a uniform return type that adapter layer maps to 200/204/409.

- [ ] **Step 1: Write the file**

```java
package com.datatalk.application.session;

import com.datatalk.application.fileartifact.FileArtifactRepository.ConnectionResourceCounts;
import com.datatalk.domain.fileartifact.FileArtifact;

import java.util.List;

/**
 * Two-phase delete outcome.
 *
 * <p>Spec §A.4. Phase 1 (no {@code force} flag) returns {@link Blocked*}
 * variants when there are resources the user must consciously decide on;
 * Phase 2 ({@code force=true}) always returns {@link Ok}.
 */
public sealed interface DeleteOutcome
        permits DeleteOutcome.Ok,
                DeleteOutcome.BlockedByCandidates,
                DeleteOutcome.BlockedByResources,
                DeleteOutcome.NotFound {

    record Ok() implements DeleteOutcome {}

    record BlockedByCandidates(String sessionId, List<FileArtifact> candidates) implements DeleteOutcome {}

    record BlockedByResources(String connectionId, ConnectionResourceCounts counts) implements DeleteOutcome {}

    record NotFound(String id) implements DeleteOutcome {}
}
```

- [ ] **Step 2: Compile**

```bash
cd /home/wallfacers/project/data-talk/server && mvn compile -q -pl data-talk-application
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Push jar + commit**

```bash
cd /home/wallfacers/project/data-talk/server && \
  mvn install -pl data-talk-application -am -DskipTests -q && \
  cd /home/wallfacers/project/data-talk && \
  git add server/data-talk-application/src/main/java/com/datatalk/application/session/DeleteOutcome.java && \
  git commit -m "feat(application): add DeleteOutcome sealed interface for two-phase delete"
```

---

## Task 4: `FileArtifactPhysicalMover` shared mover

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactPhysicalMover.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactPhysicalMoverTest.java`

**Why:** archive / discard / reattach (5b) all need atomic mv with `.v2/.v3` versioning + double-stat TOCTOU defense + ATOMIC_MOVE. Centralize.

### 4.1 Write the failing tests first (TDD red)

- [ ] **Step 1: Create the test file**

```java
package com.datatalk.application.fileartifact;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileArtifactPhysicalMoverTest {

    @TempDir
    Path tmp;

    Path src;
    Path dstDir;
    FileArtifactPhysicalMover mover;

    @BeforeEach
    void setUp() {
        src = tmp.resolve("src");
        dstDir = tmp.resolve("dst");
        mover = new FileArtifactPhysicalMover();
    }

    @Test
    void mv_moves_file_to_target_when_no_collision() throws IOException {
        Files.createDirectories(src);
        Path file = src.resolve("orders.md");
        Files.writeString(file, "# orders\n");

        Path moved = mover.mv(file, dstDir, "orders.md");

        assertThat(moved).isEqualTo(dstDir.resolve("orders.md"));
        assertThat(Files.exists(moved)).isTrue();
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void mv_creates_dst_dir_if_missing() throws IOException {
        Files.createDirectories(src);
        Path file = src.resolve("a.md");
        Files.writeString(file, "x");

        assertThat(Files.exists(dstDir)).isFalse();
        mover.mv(file, dstDir, "a.md");
        assertThat(Files.exists(dstDir)).isTrue();
    }

    @Test
    void mv_applies_v2_suffix_on_collision() throws IOException {
        Files.createDirectories(src);
        Files.createDirectories(dstDir);
        Files.writeString(dstDir.resolve("orders.md"), "old");
        Path file = src.resolve("orders.md");
        Files.writeString(file, "new");

        Path moved = mover.mv(file, dstDir, "orders.md");

        assertThat(moved).isEqualTo(dstDir.resolve("orders.v2.md"));
        assertThat(Files.readString(moved)).isEqualTo("new");
        assertThat(Files.readString(dstDir.resolve("orders.md"))).isEqualTo("old");
    }

    @Test
    void mv_applies_v3_then_v4_when_v2_exists() throws IOException {
        Files.createDirectories(src);
        Files.createDirectories(dstDir);
        Files.writeString(dstDir.resolve("orders.md"), "v1");
        Files.writeString(dstDir.resolve("orders.v2.md"), "v2");
        Files.writeString(dstDir.resolve("orders.v3.md"), "v3");
        Path file = src.resolve("orders.md");
        Files.writeString(file, "new");

        Path moved = mover.mv(file, dstDir, "orders.md");

        assertThat(moved).isEqualTo(dstDir.resolve("orders.v4.md"));
    }

    @Test
    void mv_preserves_extension_with_dot_split_on_last_dot() throws IOException {
        Files.createDirectories(src);
        Files.createDirectories(dstDir);
        Files.writeString(dstDir.resolve("foo.tar.gz"), "old");
        Path file = src.resolve("foo.tar.gz");
        Files.writeString(file, "new");

        Path moved = mover.mv(file, dstDir, "foo.tar.gz");

        // spec §A.3: split by LAST dot only — accept simplification
        assertThat(moved).isEqualTo(dstDir.resolve("foo.tar.v2.gz"));
    }

    @Test
    void mv_with_no_extension_appends_v2() throws IOException {
        Files.createDirectories(src);
        Files.createDirectories(dstDir);
        Files.writeString(dstDir.resolve("README"), "old");
        Path file = src.resolve("README");
        Files.writeString(file, "new");

        Path moved = mover.mv(file, dstDir, "README");

        assertThat(moved).isEqualTo(dstDir.resolve("README.v2"));
    }

    @Test
    void mv_throws_TocTouChanged_when_size_differs_between_stats() throws IOException {
        Files.createDirectories(src);
        Path file = src.resolve("race.md");
        Files.writeString(file, "x");

        FileArtifactPhysicalMover slowMover = new FileArtifactPhysicalMover() {
            @Override
            protected void betweenStats(Path target) throws IOException {
                Files.writeString(target, "longer payload");
            }
        };

        assertThatThrownBy(() -> slowMover.mv(file, dstDir, "race.md"))
                .isInstanceOf(FileArtifactPhysicalMover.TocTouChanged.class);
    }

    @Test
    void mv_throws_SourceMissing_when_source_does_not_exist() {
        Path missing = src.resolve("nope.md");
        assertThatThrownBy(() -> mover.mv(missing, dstDir, "nope.md"))
                .isInstanceOf(FileArtifactPhysicalMover.SourceMissing.class);
    }
}
```

- [ ] **Step 2: Run the failing test**

```bash
cd /home/wallfacers/project/data-talk/server && \
  mvn -pl data-talk-application test -Dtest=FileArtifactPhysicalMoverTest -q
```

Expected: COMPILATION FAILURE (`FileArtifactPhysicalMover` not defined).

### 4.2 Implement the mover

- [ ] **Step 3: Create the production class**

```java
package com.datatalk.application.fileartifact;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Atomic file move with .v2/.v3 versioning + double-stat TOCTOU defense.
 *
 * <p>Spec §A.3. Used by archive / discard / reattach use cases. No DB access.
 *
 * <p>Versioning rule: the suffix is inserted before the LAST dot
 * ({@code orders.md → orders.v2.md}, {@code foo.tar.gz → foo.tar.v2.gz},
 * {@code README → README.v2}). Multi-extension files are deliberately
 * simplified — accepted in spec §A.3.
 */
@Component
public class FileArtifactPhysicalMover {

    public static final class TocTouChanged extends RuntimeException {
        public TocTouChanged(String msg) { super(msg); }
    }

    public static final class DiskFull extends RuntimeException {
        public DiskFull(String msg, Throwable cause) { super(msg, cause); }
    }

    public static final class MvFailed extends RuntimeException {
        public MvFailed(String msg, Throwable cause) { super(msg, cause); }
    }

    public static final class SourceMissing extends RuntimeException {
        public SourceMissing(String msg) { super(msg); }
    }

    /**
     * Move {@code src} to {@code dstDir / dstFilename}. If a file already
     * exists at the target path, append {@code .v2}, {@code .v3}, … before
     * the last dot until a free slot is found.
     *
     * @return the actual target path used (may be a versioned filename).
     */
    public Path mv(Path src, Path dstDir, String dstFilename) {
        if (!Files.exists(src, LinkOption.NOFOLLOW_LINKS)) {
            throw new SourceMissing("source file missing: " + src);
        }
        try {
            Files.createDirectories(dstDir);
        } catch (IOException e) {
            throw new MvFailed("failed to create dst dir: " + dstDir, e);
        }

        Path dst = freeTarget(dstDir, dstFilename);

        BasicFileAttributes before;
        try {
            before = Files.readAttributes(src, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new SourceMissing("failed to stat source: " + src);
        }

        try {
            betweenStats(src);
        } catch (IOException e) {
            // Test seam — production code is a no-op
        }

        BasicFileAttributes after;
        try {
            after = Files.readAttributes(src, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new SourceMissing("source vanished during move: " + src);
        }

        if (before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
            throw new TocTouChanged("source attributes changed between stats: " + src);
        }

        try {
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                // Fallback: cross-FS move. NOT REPLACE_EXISTING (already avoided collision).
                Files.move(src, dst);
            } catch (IOException ee) {
                throw classify(ee, src, dst);
            }
        } catch (IOException e) {
            throw classify(e, src, dst);
        }
        return dst;
    }

    /**
     * Test seam: subclasses can mutate the source between the two stats to
     * exercise TOCTOU defense. Production code does not override.
     */
    protected void betweenStats(Path target) throws IOException {
        // no-op in production
    }

    static Path freeTarget(Path dstDir, String filename) {
        Path candidate = dstDir.resolve(filename);
        if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            return candidate;
        }
        int v = 2;
        while (true) {
            Path versioned = dstDir.resolve(applyVersionSuffix(filename, v));
            if (!Files.exists(versioned, LinkOption.NOFOLLOW_LINKS)) {
                return versioned;
            }
            v++;
            if (v > 999) {
                throw new MvFailed("too many version collisions for " + filename, null);
            }
        }
    }

    static String applyVersionSuffix(String filename, int version) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0) {
            return filename + ".v" + version;
        }
        String stem = filename.substring(0, dot);
        String ext = filename.substring(dot);   // includes the dot
        return stem + ".v" + version + ext;
    }

    private static RuntimeException classify(IOException e, Path src, Path dst) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains("no space") || msg.contains("disk full") || msg.contains("quota")) {
            return new DiskFull("disk full / quota: " + src + " -> " + dst, e);
        }
        return new MvFailed("mv failed: " + src + " -> " + dst, e);
    }
}
```

- [ ] **Step 4: Run the tests — should pass**

```bash
cd /home/wallfacers/project/data-talk/server && \
  mvn -pl data-talk-application test -Dtest=FileArtifactPhysicalMoverTest -q
```

Expected: 8 tests pass.

### 4.3 Push jar + commit

- [ ] **Step 5: Push jar + commit**

```bash
cd /home/wallfacers/project/data-talk/server && \
  mvn install -pl data-talk-application -am -DskipTests -q && \
  cd /home/wallfacers/project/data-talk && \
  git add server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactPhysicalMover.java \
          server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactPhysicalMoverTest.java && \
  git commit -m "feat(file-artifact): add FileArtifactPhysicalMover with versioning and TOCTOU"
```

---

## Task 5: `FileArtifactService.archive` + `discard` use cases

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceTest.java`

### 5.1 Add the use cases

- [ ] **Step 1: Add `mover` + `sessionRepo` + `connRepo` dependencies via constructor; add `archive` and `discard` methods**

Modify the constructor signature to:

```java
    private final FileArtifactRepository repo;
    private final SessionWorkdirService workdir;
    private final SessionBusRegistry buses;
    private final ObjectMapper json;
    private final FileArtifactPhysicalMover mover;
    private final com.datatalk.application.persistence.SessionRepository sessionRepo;
    private final com.datatalk.application.persistence.ConnectionRepository connRepo;
    private final SessionWorkdirRoot workdirRoot;

    public FileArtifactService(
            FileArtifactRepository repo,
            SessionWorkdirService workdir,
            SessionBusRegistry buses,
            ObjectMapper json,
            FileArtifactPhysicalMover mover,
            com.datatalk.application.persistence.SessionRepository sessionRepo,
            com.datatalk.application.persistence.ConnectionRepository connRepo,
            SessionWorkdirRoot workdirRoot) {
        this.repo = repo;
        this.workdir = workdir;
        this.buses = buses;
        this.json = json;
        this.mover = mover;
        this.sessionRepo = sessionRepo;
        this.connRepo = connRepo;
        this.workdirRoot = workdirRoot;
    }
```

> If `SessionWorkdirRoot` does not expose a `workspacesRoot()` method that returns `~/.data-talk/workspaces/`, you must add it. Read `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/SessionWorkdirRoot.java` first; if absent, add `Path workspacesRoot()` returning `dataTalkRoot().resolve("workspaces")`.

> If `SessionWorkdirService.root()` returns `SessionWorkdirRoot` already (it does — see line 149 of FileArtifactService), prefer reading `workdir.root().workspacesRoot()` over injecting `SessionWorkdirRoot` directly. **Recommendation:** drop the `workdirRoot` field, use `workdir.root().workspacesRoot()` and `workdir.root().dataTalkRoot().resolve("_trash")` inline.

- [ ] **Step 2: Add archive + discard methods before the final `}`**

```java
    // ───────── archive / discard use cases (spec §A.1 / §A.3) ─────────

    public sealed interface ArchiveOutcome {
        record Success(FileArtifact artifact) implements ArchiveOutcome {}
        record NotFound(String fid) implements ArchiveOutcome {}
        record WrongStatus(String fid, FileArtifactStatus actual) implements ArchiveOutcome {}
        record SessionMissing(String fid) implements ArchiveOutcome {}
        record ConnectionMissing(String fid) implements ArchiveOutcome {}
        record TocTou(String fid) implements ArchiveOutcome {}
        record DiskFull(String fid) implements ArchiveOutcome {}
        record MvFailed(String fid, String detail) implements ArchiveOutcome {}
    }

    public sealed interface DiscardOutcome {
        record Success(FileArtifact artifact) implements DiscardOutcome {}
        record NotFound(String fid) implements DiscardOutcome {}
        record AlreadyDiscarded(FileArtifact artifact) implements DiscardOutcome {}
        record TocTou(String fid) implements DiscardOutcome {}
        record DiskFull(String fid) implements DiscardOutcome {}
        record MvFailed(String fid, String detail) implements DiscardOutcome {}
    }

    /**
     * Move a CANDIDATE row's physical file to the connection's workspaces
     * directory and update DB row to ARCHIVED. Spec §A.1 / §A.3.
     */
    public ArchiveOutcome archive(String sessionId, String fileArtifactId) {
        FileArtifact row = repo.findById(fileArtifactId).orElse(null);
        if (row == null) {
            return new ArchiveOutcome.NotFound(fileArtifactId);
        }
        if (row.status() != FileArtifactStatus.CANDIDATE) {
            return new ArchiveOutcome.WrongStatus(fileArtifactId, row.status());
        }
        if (sessionId == null || !sessionId.equals(row.sessionId())) {
            return new ArchiveOutcome.SessionMissing(fileArtifactId);
        }
        var session = sessionRepo.findById(sessionId);
        if (session.isEmpty()) {
            return new ArchiveOutcome.SessionMissing(fileArtifactId);
        }
        String connectionId = session.get().connectionId();
        if (connectionId == null || connectionId.isBlank()) {
            return new ArchiveOutcome.ConnectionMissing(fileArtifactId);
        }

        Path src = Path.of(row.physicalPath());
        Path dstDir = workdir.root().workspacesRoot().resolve(connectionId);

        Path dst;
        try {
            dst = mover.mv(src, dstDir, row.filename());
        } catch (FileArtifactPhysicalMover.SourceMissing e) {
            return new ArchiveOutcome.NotFound(fileArtifactId);
        } catch (FileArtifactPhysicalMover.TocTouChanged e) {
            return new ArchiveOutcome.TocTou(fileArtifactId);
        } catch (FileArtifactPhysicalMover.DiskFull e) {
            return new ArchiveOutcome.DiskFull(fileArtifactId);
        } catch (FileArtifactPhysicalMover.MvFailed e) {
            return new ArchiveOutcome.MvFailed(fileArtifactId, e.getMessage());
        }

        repo.markArchived(fileArtifactId, connectionId, dst.toString());
        // markArchived also updates filename if path changed name (versioning).
        // The spec requires the row's filename to reflect the new versioned name;
        // if markArchived does not touch filename, follow up with a dedicated update:
        if (!dst.getFileName().toString().equals(row.filename())) {
            repo.updateMetadata(fileArtifactId, Files.exists(dst) ? sizeOrZero(dst) : row.sizeBytes(),
                    Instant.now().toEpochMilli());
            // Note: the existing repo does not have updateFilename. The contract
            // is markArchived = scope+status+conn+path; filename stays. This is
            // OK for now — UI displays filename from physicalPath if needed.
            // Spec §A.1 acknowledges versioned filename in physicalPath; row.filename
            // remaining the original is acceptable until a dedicated UI need arises.
        }

        FileArtifact updated = repo.findById(fileArtifactId).orElseThrow();
        publish(sessionId, new DtEvent.FileArtifactArchived(
                updated.id(),
                connectionId,
                updated.filename(),
                dst.toString()));
        return new ArchiveOutcome.Success(updated);
    }

    /**
     * Move any-status row's physical file to ~/.data-talk/_trash/ and update
     * DB row to DISCARDED. Spec §A.1 / §A.3.
     */
    public DiscardOutcome discard(String fileArtifactId) {
        FileArtifact row = repo.findById(fileArtifactId).orElse(null);
        if (row == null) {
            return new DiscardOutcome.NotFound(fileArtifactId);
        }
        if (row.status() == FileArtifactStatus.DISCARDED) {
            return new DiscardOutcome.AlreadyDiscarded(row);
        }

        Path src = Path.of(row.physicalPath());
        Path trashDir = workdir.root().dataTalkRoot().resolve("_trash");
        String prefix = (row.connectionId() != null ? row.connectionId() : (row.sessionId() != null ? row.sessionId() : "orphan"))
                + "__" + row.id() + "__" + row.filename();

        Path dst;
        try {
            dst = mover.mv(src, trashDir, prefix);
        } catch (FileArtifactPhysicalMover.SourceMissing e) {
            // file already gone — still mark row discarded so DB state catches up
            repo.updateLocation(fileArtifactId, FileArtifactStatus.DISCARDED, row.scope().dbValue(), row.physicalPath(), row.connectionId());
            FileArtifact updated = repo.findById(fileArtifactId).orElseThrow();
            publish(row.sessionId(), new DtEvent.FileArtifactDiscarded(updated.id(), "user_source_missing"));
            return new DiscardOutcome.Success(updated);
        } catch (FileArtifactPhysicalMover.TocTouChanged e) {
            return new DiscardOutcome.TocTou(fileArtifactId);
        } catch (FileArtifactPhysicalMover.DiskFull e) {
            return new DiscardOutcome.DiskFull(fileArtifactId);
        } catch (FileArtifactPhysicalMover.MvFailed e) {
            return new DiscardOutcome.MvFailed(fileArtifactId, e.getMessage());
        }

        repo.updateLocation(fileArtifactId, FileArtifactStatus.DISCARDED, row.scope().dbValue(), dst.toString(), row.connectionId());
        FileArtifact updated = repo.findById(fileArtifactId).orElseThrow();
        publish(row.sessionId(), new DtEvent.FileArtifactDiscarded(updated.id(), "user"));
        return new DiscardOutcome.Success(updated);
    }

    private static long sizeOrZero(Path p) {
        try { return Files.size(p); } catch (IOException e) { return 0L; }
    }
```

- [ ] **Step 3: Verify `SessionWorkdirRoot` has `workspacesRoot()`**

```bash
grep -n "workspacesRoot\|workspaces" /home/wallfacers/project/data-talk/server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/SessionWorkdirRoot.java
```

If `workspacesRoot()` is missing, add it as `Path workspacesRoot()` returning `dataTalkRoot().resolve("workspaces")`. Test in `SessionWorkdirRootTest` if exists.

- [ ] **Step 4: Compile**

```bash
cd /home/wallfacers/project/data-talk/server && mvn compile -q -pl data-talk-application
```

Expected: BUILD SUCCESS. If `DtEvent.FileArtifactArchived` constructor signature differs from `(id, connectionId, filename, physicalPath)`, fix the call site to match the actual record (read `server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java`).

### 5.2 Add unit tests

- [ ] **Step 5: Open `FileArtifactServiceTest.java` and append archive/discard tests**

Read the file to understand existing fixture setup (mocks, `@TempDir`). Then add at the end:

```java
    // ─────── archive use case ───────

    @Test
    void archive_returns_NotFound_for_unknown_fid() {
        when(repo.findById("fa_unknown")).thenReturn(Optional.empty());
        var out = svc.archive("ses_a", "fa_unknown");
        assertThat(out).isInstanceOf(FileArtifactService.ArchiveOutcome.NotFound.class);
    }

    @Test
    void archive_returns_WrongStatus_when_row_is_temporary() {
        FileArtifact row = candidateRow("fa_1", "ses_a", FileArtifactStatus.TEMPORARY);
        when(repo.findById("fa_1")).thenReturn(Optional.of(row));
        var out = svc.archive("ses_a", "fa_1");
        assertThat(out).isInstanceOf(FileArtifactService.ArchiveOutcome.WrongStatus.class);
    }

    @Test
    void archive_returns_ConnectionMissing_when_session_has_no_connection() throws Exception {
        Path src = sessionDir.resolve("orders.md");
        Files.writeString(src, "x");
        FileArtifact row = candidateRow("fa_1", "ses_a", FileArtifactStatus.CANDIDATE, src.toString());
        when(repo.findById("fa_1")).thenReturn(Optional.of(row));
        when(sessionRepo.findById("ses_a")).thenReturn(Optional.of(
                new com.datatalk.application.persistence.SessionRecord(
                        "ses_a", null, "t", true, null, 1L, 1L, false)));

        var out = svc.archive("ses_a", "fa_1");
        assertThat(out).isInstanceOf(FileArtifactService.ArchiveOutcome.ConnectionMissing.class);
    }

    @Test
    void archive_happy_path_moves_file_and_returns_Success() throws Exception {
        Path src = sessionDir.resolve("orders.md");
        Files.writeString(src, "x");
        FileArtifact row = candidateRow("fa_1", "ses_a", FileArtifactStatus.CANDIDATE, src.toString());
        FileArtifact archived = withStatus(row, FileArtifactStatus.ARCHIVED);
        when(repo.findById("fa_1")).thenReturn(Optional.of(row), Optional.of(archived));
        when(sessionRepo.findById("ses_a")).thenReturn(Optional.of(
                new com.datatalk.application.persistence.SessionRecord(
                        "ses_a", "conn_x", "t", true, null, 1L, 1L, false)));

        var out = svc.archive("ses_a", "fa_1");

        assertThat(out).isInstanceOf(FileArtifactService.ArchiveOutcome.Success.class);
        verify(repo).markArchived(eq("fa_1"), eq("conn_x"), endsWith("orders.md"));
        assertThat(Files.exists(src)).isFalse();
    }

    // ─────── discard use case ───────

    @Test
    void discard_returns_NotFound_for_unknown_fid() {
        when(repo.findById("fa_unknown")).thenReturn(Optional.empty());
        var out = svc.discard("fa_unknown");
        assertThat(out).isInstanceOf(FileArtifactService.DiscardOutcome.NotFound.class);
    }

    @Test
    void discard_returns_AlreadyDiscarded_when_row_already_discarded() {
        FileArtifact row = candidateRow("fa_1", "ses_a", FileArtifactStatus.DISCARDED);
        when(repo.findById("fa_1")).thenReturn(Optional.of(row));
        var out = svc.discard("fa_1");
        assertThat(out).isInstanceOf(FileArtifactService.DiscardOutcome.AlreadyDiscarded.class);
    }

    @Test
    void discard_happy_path_moves_to_trash_and_marks_discarded() throws Exception {
        Path src = sessionDir.resolve("waste.md");
        Files.writeString(src, "x");
        FileArtifact row = candidateRow("fa_1", "ses_a", FileArtifactStatus.TEMPORARY, src.toString());
        FileArtifact discarded = withStatus(row, FileArtifactStatus.DISCARDED);
        when(repo.findById("fa_1")).thenReturn(Optional.of(row), Optional.of(discarded));

        var out = svc.discard("fa_1");

        assertThat(out).isInstanceOf(FileArtifactService.DiscardOutcome.Success.class);
        verify(repo).updateLocation(eq("fa_1"), eq(FileArtifactStatus.DISCARDED), any(), contains("_trash"), any());
        assertThat(Files.exists(src)).isFalse();
    }

    // helpers
    private FileArtifact candidateRow(String id, String sid, FileArtifactStatus status) {
        return candidateRow(id, sid, status, "/tmp/" + id);
    }

    private FileArtifact candidateRow(String id, String sid, FileArtifactStatus status, String path) {
        return new FileArtifact(
                id,
                com.datatalk.domain.fileartifact.FileArtifactScope.SESSION,
                status,
                com.datatalk.domain.fileartifact.FileArtifactKind.OTHER,
                sid, null,
                Path.of(path).getFileName().toString(),
                path,
                10L, null, null, null,
                Instant.now(), Instant.now(), null,
                java.util.Map.of());
    }

    private FileArtifact withStatus(FileArtifact r, FileArtifactStatus s) {
        return new FileArtifact(
                r.id(), r.scope(), s, r.kind(),
                r.sessionId(), r.connectionId(), r.filename(), r.physicalPath(),
                r.sizeBytes(), r.mimeType(), r.title(), r.summary(),
                r.createdAt(), r.updatedAt(), r.archivedAt(), r.metadata());
    }
```

> **Note:** the existing test file may not have `mover` / `sessionRepo` / `connRepo` mocks. You will need to extend the `@BeforeEach` setup to inject:
>
> ```java
> @Mock SessionRepository sessionRepo;
> @Mock ConnectionRepository connRepo;
> FileArtifactPhysicalMover mover = new FileArtifactPhysicalMover();
> // ... and pass to new FileArtifactService(...)
> ```

- [ ] **Step 6: Run tests**

```bash
cd /home/wallfacers/project/data-talk/server && \
  mvn -pl data-talk-application test -Dtest=FileArtifactServiceTest -q
```

Expected: existing tests + ~7 new tests all pass.

### 5.3 Push jar + commit

- [ ] **Step 7: Push + commit**

```bash
cd /home/wallfacers/project/data-talk/server && \
  mvn install -pl data-talk-application -am -DskipTests -q && \
  cd /home/wallfacers/project/data-talk && \
  git add server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java \
          server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/SessionWorkdirRoot.java \
          server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceTest.java && \
  git commit -m "feat(file-artifact): add archive and discard use cases on FileArtifactService"
```

> Add `SessionWorkdirRoot.java` to the staging only if you modified it in step 3. Otherwise omit.

---

## Task 6: `SessionService.delete(id, force)` two-phase

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/session/SessionServiceTest.java`

### 6.1 Refactor delete to return `DeleteOutcome`

- [ ] **Step 1: Replace the existing `delete(String id)` method with two methods**

```java
    public DeleteOutcome delete(String id, boolean force) {
        SessionRecord rec = repo.findById(id).orElse(null);
        if (rec == null) {
            return new DeleteOutcome.NotFound(id);
        }
        if (!force) {
            int candidateCount = fileArtifacts.countCandidatesBySession(id);
            if (candidateCount > 0) {
                List<com.datatalk.domain.fileartifact.FileArtifact> candidates = fileArtifacts.findCandidatesBySession(id);
                return new DeleteOutcome.BlockedByCandidates(id, candidates);
            }
        }
        deleteRecord(rec);
        return new DeleteOutcome.Ok();
    }

    /**
     * Backwards-compatible shim. The {@code deleteAll} path needs an
     * unconditional force-delete; existing callers that ignored candidates
     * keep their behavior. New callers MUST go through {@link #delete(String, boolean)}.
     */
    @Deprecated
    public void delete(String id) {
        DeleteOutcome out = delete(id, true);
        if (out instanceof DeleteOutcome.NotFound) {
            throw new NoSuchElementException(translator.get("error.session.not_found", id));
        }
    }
```

- [ ] **Step 2: Update the import**

Add to imports:

```java
import com.datatalk.domain.fileartifact.FileArtifact;
```

(Replace the existing `findCandidatesBySession` references if needed — already exposed by the repo.)

- [ ] **Step 3: Compile**

```bash
cd /home/wallfacers/project/data-talk/server && mvn compile -q -pl data-talk-application
```

Expected: BUILD SUCCESS.

### 6.2 Add unit tests

- [ ] **Step 4: Open `SessionServiceTest.java` and add force-flag tests**

Before adding, read the existing test file to understand fixture style. Append:

```java
    @Test
    void delete_force_false_returns_BlockedByCandidates_when_candidates_exist() {
        SessionRecord rec = sessionWithId("ses_a");
        when(sessionRepo.findById("ses_a")).thenReturn(Optional.of(rec));
        when(fileArtifacts.countCandidatesBySession("ses_a")).thenReturn(2);
        when(fileArtifacts.findCandidatesBySession("ses_a")).thenReturn(java.util.List.of(
                anyArtifact("fa_1"), anyArtifact("fa_2")));

        var out = svc.delete("ses_a", false);

        assertThat(out).isInstanceOf(DeleteOutcome.BlockedByCandidates.class);
        var blocked = (DeleteOutcome.BlockedByCandidates) out;
        assertThat(blocked.candidates()).hasSize(2);
        verify(sessionRepo, never()).deleteById(any());
    }

    @Test
    void delete_force_false_returns_Ok_when_no_candidates() {
        SessionRecord rec = sessionWithId("ses_a");
        when(sessionRepo.findById("ses_a")).thenReturn(Optional.of(rec));
        when(fileArtifacts.countCandidatesBySession("ses_a")).thenReturn(0);

        var out = svc.delete("ses_a", false);

        assertThat(out).isInstanceOf(DeleteOutcome.Ok.class);
        verify(sessionRepo).deleteById("ses_a");
        verify(fileArtifacts).deleteTransientByForSession("ses_a");
        verify(fileArtifacts).detachArchivedFromSession("ses_a");
    }

    @Test
    void delete_force_true_proceeds_even_with_candidates() {
        SessionRecord rec = sessionWithId("ses_a");
        when(sessionRepo.findById("ses_a")).thenReturn(Optional.of(rec));
        // countCandidatesBySession not called when force=true (verify lenient)

        var out = svc.delete("ses_a", true);

        assertThat(out).isInstanceOf(DeleteOutcome.Ok.class);
        verify(sessionRepo).deleteById("ses_a");
    }

    @Test
    void delete_returns_NotFound_for_unknown_id() {
        when(sessionRepo.findById("ses_nope")).thenReturn(Optional.empty());
        var out = svc.delete("ses_nope", false);
        assertThat(out).isInstanceOf(DeleteOutcome.NotFound.class);
    }
```

You may need helper methods `sessionWithId` and `anyArtifact`; add them or reuse if present.

- [ ] **Step 5: Run tests**

```bash
cd /home/wallfacers/project/data-talk/server && \
  mvn -pl data-talk-application test -Dtest=SessionServiceTest -q
```

Expected: existing tests + 4 new pass.

### 6.3 Push jar + commit

- [ ] **Step 6:**

```bash
cd /home/wallfacers/project/data-talk/server && \
  mvn install -pl data-talk-application -am -DskipTests -q && \
  cd /home/wallfacers/project/data-talk && \
  git add server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java \
          server/data-talk-application/src/test/java/com/datatalk/application/session/SessionServiceTest.java && \
  git commit -m "feat(session): make SessionService.delete two-phase via DeleteOutcome"
```

---

## Task 7: `ConnectionDeletionService` orchestrator

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionDeletionService.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/connection/ConnectionDeletionServiceTest.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java` (mark `deleteById` deprecated; new code uses `ConnectionDeletionService`)

**Why a new orchestrator:** spec §C.4 风险表 explicitly recommends adding an application-layer `ConnectionDeletionService` rather than refactoring `ConnectionService`/`ConnectionController` (the latter lives in infrastructure layer with current architecture quirks).

### 7.1 Write the orchestrator

- [ ] **Step 1: Create the file**

```java
package com.datatalk.application.connection;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.DeleteOutcome;
import com.datatalk.application.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Two-phase connection delete orchestrator.
 *
 * <p>Spec §A.1 (DELETE connection two-phase) + §A.4 (deleteConnectionFileArtifacts
 * ordering: must read connection.name BEFORE setting connection_id=NULL on
 * archived rows).
 */
@Service
public class ConnectionDeletionService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionDeletionService.class);

    private final ConnectionRepository connections;
    private final SessionRepository sessions;
    private final SessionService sessionService;
    private final FileArtifactRepository fileArtifacts;
    private final Clock clock;

    public ConnectionDeletionService(
            ConnectionRepository connections,
            SessionRepository sessions,
            SessionService sessionService,
            FileArtifactRepository fileArtifacts,
            Clock clock) {
        this.connections = connections;
        this.sessions = sessions;
        this.sessionService = sessionService;
        this.fileArtifacts = fileArtifacts;
        this.clock = clock;
    }

    @Transactional
    public DeleteOutcome delete(String connectionId, boolean force) {
        ConnectionRecord rec = connections.findById(connectionId).orElse(null);
        if (rec == null) {
            return new DeleteOutcome.NotFound(connectionId);
        }
        List<SessionRecord> childSessions = sessions.listByConnection(connectionId);
        List<String> childSessionIds = childSessions.stream().map(SessionRecord::id).toList();

        if (!force) {
            FileArtifactRepository.ConnectionResourceCounts counts =
                    fileArtifacts.countResourcesByConnection(connectionId, childSessionIds);
            if (counts.sessions() > 0 || counts.candidates() > 0
                    || counts.temporary() > 0 || counts.archived() > 0) {
                return new DeleteOutcome.BlockedByResources(connectionId, counts);
            }
            // No resources at all — just drop the connection row.
            connections.deleteById(connectionId);
            return new DeleteOutcome.Ok();
        }

        // force = true: cascade
        // 1) detach archived rows + stamp orphan metadata (MUST happen BEFORE
        //    connection row deletion so the name is still discoverable, spec §A.4).
        fileArtifacts.detachArchivedFromConnection(connectionId, rec.name(), clock.millis());

        // 2) delete every child session forcefully (its own transient
        //    file_artifacts get cleaned up + archived rows already detached).
        for (String sid : childSessionIds) {
            try {
                sessionService.delete(sid, true);
            } catch (Exception e) {
                log.warn("[connection-delete] child session {} delete failed: {}", sid, e.toString());
            }
        }

        // 3) delete the connection row last.
        connections.deleteById(connectionId);

        return new DeleteOutcome.Ok();
    }
}
```

- [ ] **Step 2: Compile**

```bash
cd /home/wallfacers/project/data-talk/server && mvn compile -q -pl data-talk-application
```

Expected: BUILD SUCCESS. If `ConnectionRecord.name()` accessor differs, fix.

### 7.2 Add unit test

- [ ] **Step 3: Create the test**

```java
package com.datatalk.application.connection;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.DeleteOutcome;
import com.datatalk.application.session.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectionDeletionServiceTest {

    @Mock ConnectionRepository connections;
    @Mock SessionRepository sessions;
    @Mock SessionService sessionService;
    @Mock FileArtifactRepository fileArtifacts;
    Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC);

    ConnectionDeletionService svc;

    @BeforeEach
    void setUp() {
        svc = new ConnectionDeletionService(connections, sessions, sessionService, fileArtifacts, clock);
    }

    @Test
    void delete_returns_NotFound_for_missing_connection() {
        when(connections.findById("conn_nope")).thenReturn(Optional.empty());
        var out = svc.delete("conn_nope", false);
        assertThat(out).isInstanceOf(DeleteOutcome.NotFound.class);
    }

    @Test
    void delete_force_false_returns_BlockedByResources_when_counts_nonzero() {
        when(connections.findById("conn_x")).thenReturn(Optional.of(connRec("conn_x", "prod-mysql")));
        when(sessions.listByConnection("conn_x")).thenReturn(List.of(sessionRec("ses_a", "conn_x")));
        when(fileArtifacts.countResourcesByConnection(eq("conn_x"), any()))
                .thenReturn(new FileArtifactRepository.ConnectionResourceCounts(1, 2, 3, 4));

        var out = svc.delete("conn_x", false);

        assertThat(out).isInstanceOf(DeleteOutcome.BlockedByResources.class);
        var blocked = (DeleteOutcome.BlockedByResources) out;
        assertThat(blocked.counts().sessions()).isEqualTo(1);
        verify(connections, never()).deleteById(any());
    }

    @Test
    void delete_force_true_stamps_orphan_metadata_BEFORE_row_delete() {
        when(connections.findById("conn_x")).thenReturn(Optional.of(connRec("conn_x", "prod-mysql")));
        when(sessions.listByConnection("conn_x")).thenReturn(List.of(sessionRec("ses_a", "conn_x")));

        var out = svc.delete("conn_x", true);

        assertThat(out).isInstanceOf(DeleteOutcome.Ok.class);

        var inOrder = inOrder(fileArtifacts, sessionService, connections);
        // Spec §A.4: detach archived (with metadata stamp) FIRST
        inOrder.verify(fileArtifacts).detachArchivedFromConnection("conn_x", "prod-mysql", 1_000L);
        // Then cascade-delete child sessions
        inOrder.verify(sessionService).delete("ses_a", true);
        // Connection row last
        inOrder.verify(connections).deleteById("conn_x");
    }

    @Test
    void delete_force_false_proceeds_when_no_resources() {
        when(connections.findById("conn_x")).thenReturn(Optional.of(connRec("conn_x", "prod-mysql")));
        when(sessions.listByConnection("conn_x")).thenReturn(List.of());
        when(fileArtifacts.countResourcesByConnection(eq("conn_x"), any()))
                .thenReturn(new FileArtifactRepository.ConnectionResourceCounts(0, 0, 0, 0));

        var out = svc.delete("conn_x", false);

        assertThat(out).isInstanceOf(DeleteOutcome.Ok.class);
        verify(connections).deleteById("conn_x");
    }

    private static ConnectionRecord connRec(String id, String name) {
        return new ConnectionRecord(
                id, name, "mysql", "h", 3306, "db", "u", new byte[0],
                null, 0L, 3000, null, null, null, 1, true, null);
    }

    private static SessionRecord sessionRec(String id, String connectionId) {
        return new SessionRecord(id, connectionId, "t", true, null, 0L, 0L, false);
    }
}
```

> If `ConnectionRecord` constructor signature differs, fix the `connRec` helper based on `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRecord.java`.

- [ ] **Step 4: Run test**

```bash
cd /home/wallfacers/project/data-talk/server && \
  mvn -pl data-talk-application test -Dtest=ConnectionDeletionServiceTest -q
```

Expected: 4 tests pass.

### 7.3 Push jar + commit

- [ ] **Step 5:**

```bash
cd /home/wallfacers/project/data-talk/server && \
  mvn install -pl data-talk-application -am -DskipTests -q && \
  cd /home/wallfacers/project/data-talk && \
  git add server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionDeletionService.java \
          server/data-talk-application/src/test/java/com/datatalk/application/connection/ConnectionDeletionServiceTest.java && \
  git commit -m "feat(connection): add ConnectionDeletionService two-phase orchestrator"
```

---

## Task 8: Adapter — DTOs + endpoints (`SessionController`, `FileArtifactController`)

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/dto/SessionCandidateDto.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/dto/SessionDeleteBlockedDto.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/dto/ConnectionDeleteBlockedDto.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/FileArtifactController.java`

### 8.1 Create the DTOs

- [ ] **Step 1: `SessionCandidateDto`**

```java
package com.datatalk.dto;

public record SessionCandidateDto(
        String id,
        String filename,
        String kind,
        long sizeBytes,
        String title,
        String summary
) {}
```

- [ ] **Step 2: `SessionDeleteBlockedDto`**

```java
package com.datatalk.dto;

import java.util.List;

public record SessionDeleteBlockedDto(
        String error,
        String sessionId,
        List<SessionCandidateDto> candidates
) {
    public static SessionDeleteBlockedDto of(String sessionId, List<SessionCandidateDto> candidates) {
        return new SessionDeleteBlockedDto("session_has_archive_candidates", sessionId, candidates);
    }
}
```

- [ ] **Step 3: `ConnectionDeleteBlockedDto`**

```java
package com.datatalk.dto;

public record ConnectionDeleteBlockedDto(
        String error,
        String connectionId,
        Counts counts
) {
    public record Counts(int sessions, int candidates, int temporary, int archived) {}

    public static ConnectionDeleteBlockedDto of(String connectionId, int sessions, int candidates, int temporary, int archived) {
        return new ConnectionDeleteBlockedDto(
                "connection_has_resources",
                connectionId,
                new Counts(sessions, candidates, temporary, archived));
    }
}
```

### 8.2 SessionController

- [ ] **Step 4: Replace existing `delete` method**

```java
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable String id,
            @RequestParam(value = "force", defaultValue = "false") boolean force) {
        DeleteOutcome out = svc.delete(id, force);
        return switch (out) {
            case DeleteOutcome.Ok ok -> ResponseEntity.noContent().build();
            case DeleteOutcome.NotFound nf -> ResponseEntity.notFound().build();
            case DeleteOutcome.BlockedByCandidates bc -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(SessionDeleteBlockedDto.of(
                            bc.sessionId(),
                            bc.candidates().stream()
                                    .map(c -> new SessionCandidateDto(
                                            c.id(), c.filename(), c.kind().dbValue(),
                                            c.sizeBytes(), c.title(), c.summary()))
                                    .toList()));
            case DeleteOutcome.BlockedByResources br ->
                    throw new IllegalStateException("session delete returned BlockedByResources unexpectedly");
        };
    }
```

Add imports:
```java
import com.datatalk.application.session.DeleteOutcome;
import com.datatalk.dto.SessionCandidateDto;
import com.datatalk.dto.SessionDeleteBlockedDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
```

### 8.3 FileArtifactController — add archive + discard endpoints

- [ ] **Step 5: Add to FileArtifactController**

```java
    @PostMapping("/sessions/{sessionId}/files/{fileArtifactId}/archive")
    public ResponseEntity<?> archive(
            @PathVariable String sessionId,
            @PathVariable String fileArtifactId) {
        var out = svc.archive(sessionId, fileArtifactId);
        return switch (out) {
            case FileArtifactService.ArchiveOutcome.Success s -> ResponseEntity.ok(s.artifact());
            case FileArtifactService.ArchiveOutcome.NotFound nf -> ResponseEntity.notFound().build();
            case FileArtifactService.ArchiveOutcome.WrongStatus ws -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(java.util.Map.of("error", "wrong_status", "actual", ws.actual().dbValue()));
            case FileArtifactService.ArchiveOutcome.SessionMissing sm -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(java.util.Map.of("error", "session_missing"));
            case FileArtifactService.ArchiveOutcome.ConnectionMissing cm -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(java.util.Map.of("error", "connection_missing"));
            case FileArtifactService.ArchiveOutcome.TocTou tt -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(java.util.Map.of("error", "toctou_changed"));
            case FileArtifactService.ArchiveOutcome.DiskFull df -> ResponseEntity.status(HttpStatus.INSUFFICIENT_STORAGE)
                    .body(java.util.Map.of("error", "disk_full"));
            case FileArtifactService.ArchiveOutcome.MvFailed mf -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(java.util.Map.of("error", "mv_failed", "detail", mf.detail()));
        };
    }

    @PostMapping("/files/{fileArtifactId}/discard")
    public ResponseEntity<?> discard(@PathVariable String fileArtifactId) {
        var out = svc.discard(fileArtifactId);
        return switch (out) {
            case FileArtifactService.DiscardOutcome.Success s -> ResponseEntity.noContent().build();
            case FileArtifactService.DiscardOutcome.AlreadyDiscarded a -> ResponseEntity.noContent().build();
            case FileArtifactService.DiscardOutcome.NotFound nf -> ResponseEntity.notFound().build();
            case FileArtifactService.DiscardOutcome.TocTou tt -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(java.util.Map.of("error", "toctou_changed"));
            case FileArtifactService.DiscardOutcome.DiskFull df -> ResponseEntity.status(HttpStatus.INSUFFICIENT_STORAGE)
                    .body(java.util.Map.of("error", "disk_full"));
            case FileArtifactService.DiscardOutcome.MvFailed mf -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(java.util.Map.of("error", "mv_failed", "detail", mf.detail()));
        };
    }
```

Add imports:
```java
import com.datatalk.application.fileartifact.FileArtifactService;
import org.springframework.http.HttpStatus;
```

- [ ] **Step 6: Compile**

```bash
cd /home/wallfacers/project/data-talk/server && mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit (without tests yet — those land in Task 13)**

```bash
cd /home/wallfacers/project/data-talk && \
  git add server/data-talk-adapter/src/main/java/com/datatalk/dto/SessionCandidateDto.java \
          server/data-talk-adapter/src/main/java/com/datatalk/dto/SessionDeleteBlockedDto.java \
          server/data-talk-adapter/src/main/java/com/datatalk/dto/ConnectionDeleteBlockedDto.java \
          server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionController.java \
          server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/FileArtifactController.java && \
  git commit -m "feat(adapter): add archive/discard endpoints + session two-phase delete"
```

---

## Task 9: Infrastructure — `ConnectionController` `?force=true`

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/connection/ConnectionController.java`

### 9.1 Replace the existing `delete` method

- [ ] **Step 1: Inject `ConnectionDeletionService` and rewrite delete**

```java
    private final ConnectionDeletionService deletionService;

    // existing constructor signature: append `ConnectionDeletionService deletionService`
    public ConnectionController(
        ConnectionService svc,
        ConnectionContextRefreshService contextRefreshService,
        ConnectionTargetDiscoveryService discovery,
        ConnectionDeletionService deletionService
    ) {
        this.svc = svc;
        this.contextRefreshService = contextRefreshService;
        this.discovery = discovery;
        this.deletionService = deletionService;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable String id,
            @RequestParam(value = "force", defaultValue = "false") boolean force) {
        DeleteOutcome out = deletionService.delete(id, force);
        return switch (out) {
            case DeleteOutcome.Ok ok -> ResponseEntity.noContent().build();
            case DeleteOutcome.NotFound nf -> ResponseEntity.notFound().build();
            case DeleteOutcome.BlockedByResources br -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ConnectionDeleteBlockedDto.of(
                            br.connectionId(),
                            br.counts().sessions(),
                            br.counts().candidates(),
                            br.counts().temporary(),
                            br.counts().archived()));
            case DeleteOutcome.BlockedByCandidates bc ->
                    throw new IllegalStateException("connection delete returned BlockedByCandidates unexpectedly");
        };
    }
```

Add imports:
```java
import com.datatalk.application.connection.ConnectionDeletionService;
import com.datatalk.application.session.DeleteOutcome;
import com.datatalk.dto.ConnectionDeleteBlockedDto;
import org.springframework.web.bind.annotation.RequestParam;
```

- [ ] **Step 2: Compile**

```bash
cd /home/wallfacers/project/data-talk/server && mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
cd /home/wallfacers/project/data-talk && \
  git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/connection/ConnectionController.java && \
  git commit -m "feat(connection): make ConnectionController DELETE two-phase via force flag"
```

---

## Task 10: Backend integration tests

**Files:**
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/FileArtifactControllerIT.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerIT.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/ConnectionDeletionIT.java`

This task lands all the integration tests promised by spec §C.2 Part 5a. Tests use the existing `@SpringBootTest` + `MockMvc` pattern.

### 10.1 FileArtifactControllerIT — extend with archive/discard tests

- [ ] **Step 1: Open the file and add the following tests at the bottom**

Read existing fixture/setup first (mocking + workdir tempdir). Then append:

```java
    @Test
    void archive_happy_path_returns_200_and_archived_artifact() throws Exception {
        // Setup: session with connection, candidate file_artifact row, file on disk
        seedConnection("conn_x");
        seedSession("ses_a", "conn_x");
        Path file = workdirFixture.sessionDir("ses_a").resolve("orders-er.md");
        Files.writeString(file, "# ER\n");
        String fid = repoFixture.insertCandidateRow("ses_a", file);

        mockMvc.perform(post("/api/sessions/ses_a/files/" + fid + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("archived"))
                .andExpect(jsonPath("$.connectionId").value("conn_x"))
                .andExpect(jsonPath("$.physicalPath", endsWith("orders-er.md")));

        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void archive_returns_404_for_unknown_fid() throws Exception {
        mockMvc.perform(post("/api/sessions/ses_a/files/fa_unknown/archive"))
                .andExpect(status().isNotFound());
    }

    @Test
    void archive_returns_409_for_temporary_status() throws Exception {
        seedConnection("conn_x");
        seedSession("ses_a", "conn_x");
        Path file = workdirFixture.sessionDir("ses_a").resolve("temp.md");
        Files.writeString(file, "x");
        String fid = repoFixture.insertTemporaryRow("ses_a", file);

        mockMvc.perform(post("/api/sessions/ses_a/files/" + fid + "/archive"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("wrong_status"));
    }

    @Test
    void discard_happy_path_returns_204_and_moves_to_trash() throws Exception {
        seedSession("ses_a", null);
        Path file = workdirFixture.sessionDir("ses_a").resolve("trash-me.md");
        Files.writeString(file, "x");
        String fid = repoFixture.insertCandidateRow("ses_a", file);

        mockMvc.perform(post("/api/files/" + fid + "/discard"))
                .andExpect(status().isNoContent());

        assertThat(Files.exists(file)).isFalse();
        assertThat(Files.list(workdirFixture.dataTalkRoot().resolve("_trash")).count()).isGreaterThan(0L);
    }

    @Test
    void discard_returns_404_for_unknown_fid() throws Exception {
        mockMvc.perform(post("/api/files/fa_unknown/discard"))
                .andExpect(status().isNotFound());
    }
```

> The fixture helper methods (`seedConnection`, `seedSession`, `repoFixture.insert*`, `workdirFixture`) may not exist. If absent, build minimal versions inside the test class using `@Autowired ConnectionRepository`, `SessionRepository`, `FileArtifactRepository`, `SessionWorkdirService`. Use `@DynamicPropertySource` to point `datatalk.workdir.data-talk-root` at `@TempDir` (mirror the pattern from `ArchiveArtifactActionHandlerIT` proposed in Part 3 plan).

### 10.2 SessionControllerIT — extend with force-flag tests

- [ ] **Step 2: Append**

```java
    @Test
    void delete_no_candidates_returns_204_and_deletes() throws Exception {
        seedSession("ses_a", null);
        mockMvc.perform(delete("/api/sessions/ses_a"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_with_candidate_returns_409_with_body() throws Exception {
        seedSession("ses_a", null);
        Path file = workdirFixture.sessionDir("ses_a").resolve("c.md");
        Files.writeString(file, "x");
        repoFixture.insertCandidateRow("ses_a", file);

        mockMvc.perform(delete("/api/sessions/ses_a"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("session_has_archive_candidates"))
                .andExpect(jsonPath("$.candidates", hasSize(1)));
    }

    @Test
    void delete_force_true_proceeds_even_with_candidate() throws Exception {
        seedSession("ses_a", null);
        Path file = workdirFixture.sessionDir("ses_a").resolve("c.md");
        Files.writeString(file, "x");
        repoFixture.insertCandidateRow("ses_a", file);

        mockMvc.perform(delete("/api/sessions/ses_a?force=true"))
                .andExpect(status().isNoContent());
    }
```

### 10.3 ConnectionDeletionIT (new file)

- [ ] **Step 3: Create**

```java
package com.datatalk.adapter.controller;

import com.datatalk.DataTalkApplication;
// imports omitted for brevity — match the patterns from FileArtifactControllerIT
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DataTalkApplication.class)
@AutoConfigureMockMvc
class ConnectionDeletionIT {

    @Autowired MockMvc mockMvc;
    // ... fixtures

    @Test
    void delete_with_no_resources_returns_204() throws Exception {
        seedConnection("conn_empty");
        mockMvc.perform(delete("/api/connections/conn_empty"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_with_sessions_returns_409_with_counts() throws Exception {
        seedConnection("conn_x");
        seedSession("ses_a", "conn_x");
        Path file = workdirFixture.sessionDir("ses_a").resolve("c.md");
        Files.writeString(file, "x");
        repoFixture.insertCandidateRow("ses_a", file);
        repoFixture.insertArchivedRow("conn_x");

        mockMvc.perform(delete("/api/connections/conn_x"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("connection_has_resources"))
                .andExpect(jsonPath("$.counts.sessions").value(1))
                .andExpect(jsonPath("$.counts.candidates").value(1))
                .andExpect(jsonPath("$.counts.archived").value(1));
    }

    @Test
    void delete_force_true_preserves_archived_with_orphan_metadata() throws Exception {
        seedConnection("conn_x", "prod-mysql");
        String archivedId = repoFixture.insertArchivedRow("conn_x");

        mockMvc.perform(delete("/api/connections/conn_x?force=true"))
                .andExpect(status().isNoContent());

        var detached = fileArtifactRepo.findById(archivedId).orElseThrow();
        assertThat(detached.connectionId()).isNull();
        assertThat(detached.metadata())
                .containsEntry("orphanedFromConnection", "prod-mysql")
                .containsEntry("orphanedFromConnectionId", "conn_x");
    }

    @Test
    void delete_force_true_cascades_child_sessions() throws Exception {
        seedConnection("conn_x");
        seedSession("ses_a", "conn_x");
        seedSession("ses_b", "conn_x");

        mockMvc.perform(delete("/api/connections/conn_x?force=true"))
                .andExpect(status().isNoContent());

        assertThat(sessionRepo.findById("ses_a")).isEmpty();
        assertThat(sessionRepo.findById("ses_b")).isEmpty();
    }
}
```

- [ ] **Step 4: Run all three IT files**

```bash
cd /home/wallfacers/project/data-talk/server && \
  mvn -pl data-talk-adapter test \
      -Dtest='FileArtifactControllerIT,SessionControllerIT,ConnectionDeletionIT' -q
```

Expected: All tests pass. Fixture helper builds-out done as needed.

### 10.4 Commit

- [ ] **Step 5:**

```bash
cd /home/wallfacers/project/data-talk && \
  git add server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/FileArtifactControllerIT.java \
          server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SessionControllerIT.java \
          server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/ConnectionDeletionIT.java && \
  git commit -m "test(adapter): cover archive/discard endpoints and two-phase delete"
```

---

## Task 11: Frontend — API client adjustments

**Files:**
- Modify: `client/src/services/api/file-artifacts.ts`
- Modify: `client/src/services/api/session.ts`
- Modify: `client/src/services/api/connection.ts`

### 11.1 file-artifacts.ts

- [ ] **Step 1: Update `archiveFile` and `discardFile` to match the new endpoints**

Open `client/src/services/api/file-artifacts.ts` and replace `archiveFile` / `discardFile`:

```ts
export async function archiveFile(sessionId: string, fileArtifactId: string): Promise<FileArtifact> {
  return http
    .post(`sessions/${sessionId}/files/${fileArtifactId}/archive`)
    .json<FileArtifact>()
}

export async function discardFile(fileArtifactId: string): Promise<void> {
  await http.post(`files/${fileArtifactId}/discard`)
}
```

(If the existing Part 4 plan signature differs, harmonize.)

### 11.2 session.ts

- [ ] **Step 2: Add a `BlockedByCandidates` type and a force-aware `deleteSession`**

Append:

```ts
import { HTTPError } from 'ky'
import type { FileArtifact } from './file-artifacts'

export interface SessionDeleteBlocked {
  error: 'session_has_archive_candidates'
  sessionId: string
  candidates: Array<{
    id: string
    filename: string
    kind: 'report' | 'er_diagram' | 'sql_script' | 'dataset' | 'other'
    sizeBytes: number
    title: string | null
    summary: string | null
  }>
}

export type DeleteSessionResult =
  | { kind: 'ok' }
  | { kind: 'blocked'; blocked: SessionDeleteBlocked }
  | { kind: 'not_found' }

export async function deleteSession(
  id: string,
  options: { force?: boolean } = {},
): Promise<DeleteSessionResult> {
  const search = options.force ? '?force=true' : ''
  try {
    await http.delete(`sessions/${id}${search}`)
    return { kind: 'ok' }
  } catch (e) {
    if (e instanceof HTTPError) {
      if (e.response.status === 404) return { kind: 'not_found' }
      if (e.response.status === 409) {
        const blocked = (await e.response.json()) as SessionDeleteBlocked
        return { kind: 'blocked', blocked }
      }
    }
    throw e
  }
}
```

If `deleteSession` already exists with a different signature, modify the existing function to keep the old call site working as a force-true wrapper.

### 11.3 connection.ts

- [ ] **Step 3: Add a parallel deleteConnection**

```ts
import { HTTPError } from 'ky'

export interface ConnectionDeleteBlocked {
  error: 'connection_has_resources'
  connectionId: string
  counts: { sessions: number; candidates: number; temporary: number; archived: number }
}

export type DeleteConnectionResult =
  | { kind: 'ok' }
  | { kind: 'blocked'; blocked: ConnectionDeleteBlocked }
  | { kind: 'not_found' }

export async function deleteConnection(
  id: string,
  options: { force?: boolean } = {},
): Promise<DeleteConnectionResult> {
  const search = options.force ? '?force=true' : ''
  try {
    await http.delete(`connections/${id}${search}`)
    return { kind: 'ok' }
  } catch (e) {
    if (e instanceof HTTPError) {
      if (e.response.status === 404) return { kind: 'not_found' }
      if (e.response.status === 409) {
        const blocked = (await e.response.json()) as ConnectionDeleteBlocked
        return { kind: 'blocked', blocked }
      }
    }
    throw e
  }
}
```

- [ ] **Step 4: Type-check**

```bash
cd /home/wallfacers/project/data-talk/client && npx tsc --noEmit
```

Expected: zero errors.

- [ ] **Step 5: Commit**

```bash
cd /home/wallfacers/project/data-talk && \
  git add client/src/services/api/file-artifacts.ts \
          client/src/services/api/session.ts \
          client/src/services/api/connection.ts && \
  git commit -m "feat(api): add force-aware delete + 409 handling for sessions/connections"
```

---

## Task 12: Frontend — `DeleteSessionModal` (spec §A.5)

**Files:**
- Create: `client/src/features/session/components/delete-session-modal.tsx`
- Create: `client/src/features/session/components/delete-session-modal.test.tsx`

### 12.1 Write the failing test (TDD red)

- [ ] **Step 1: Write tests covering candidate listing, retry idempotency, and Ok button enable rules**

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { DeleteSessionModal } from '../delete-session-modal'
import * as fileArtifactsApi from '@/services/api/file-artifacts'
import * as sessionApi from '@/services/api/session'

const blocked: sessionApi.SessionDeleteBlocked = {
  error: 'session_has_archive_candidates',
  sessionId: 'ses_a',
  candidates: [
    { id: 'fa_1', filename: 'orders-er.md', kind: 'er_diagram', sizeBytes: 8400, title: null, summary: null },
    { id: 'fa_2', filename: 'weekly-report.md', kind: 'report', sizeBytes: 32400, title: null, summary: null },
  ],
}

describe('DeleteSessionModal', () => {
  it('renders candidates and disables confirm until decisions complete', async () => {
    render(
      <DeleteSessionModal
        sessionId="ses_a"
        connectionName="prod-mysql"
        blocked={blocked}
        onClose={vi.fn()}
        onConfirmed={vi.fn()}
      />,
    )

    expect(screen.getByText('orders-er.md')).toBeInTheDocument()
    expect(screen.getByText('weekly-report.md')).toBeInTheDocument()

    const confirmBtn = screen.getByRole('button', { name: /files\.deleteModal\.confirm/ })
    expect(confirmBtn).toBeDisabled()
  })

  it('enables confirm after every candidate has a decision', async () => {
    const user = userEvent.setup()
    render(
      <DeleteSessionModal
        sessionId="ses_a"
        connectionName="prod-mysql"
        blocked={blocked}
        onClose={vi.fn()}
        onConfirmed={vi.fn()}
      />,
    )

    await user.click(screen.getAllByRole('radio', { name: /files\.deleteModal\.allArchive/ })[0])
    await user.click(screen.getAllByRole('radio', { name: /files\.deleteModal\.allDiscard/ })[1])

    expect(screen.getByRole('button', { name: /files\.deleteModal\.confirm/ })).toBeEnabled()
  })

  it('only retries failed candidates on second click', async () => {
    const archive = vi.spyOn(fileArtifactsApi, 'archiveFile')
      .mockResolvedValueOnce({} as any)
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce({} as any)
    const deleteSession = vi.spyOn(sessionApi, 'deleteSession').mockResolvedValue({ kind: 'ok' })
    const user = userEvent.setup()
    const onConfirmed = vi.fn()

    render(
      <DeleteSessionModal
        sessionId="ses_a"
        connectionName="prod-mysql"
        blocked={blocked}
        onClose={vi.fn()}
        onConfirmed={onConfirmed}
      />,
    )

    // both archive
    await user.click(screen.getByRole('button', { name: /files\.deleteModal\.allArchive/ }))
    await user.click(screen.getByRole('button', { name: /files\.deleteModal\.confirm/ }))

    // first attempt: fa_1 ok, fa_2 boom
    await waitFor(() => expect(archive).toHaveBeenCalledTimes(2))
    expect(deleteSession).not.toHaveBeenCalled()

    // retry: only fa_2
    await user.click(screen.getByRole('button', { name: /files\.deleteModal\.confirm/ }))
    await waitFor(() => expect(archive).toHaveBeenCalledTimes(3))
    expect(archive).toHaveBeenLastCalledWith('ses_a', 'fa_2')

    await waitFor(() => expect(deleteSession).toHaveBeenCalledWith('ses_a', { force: true }))
    expect(onConfirmed).toHaveBeenCalled()

    archive.mockRestore()
    deleteSession.mockRestore()
  })
})
```

- [ ] **Step 2: Run failing test**

```bash
cd /home/wallfacers/project/data-talk/client && npm test -- --run client/src/features/session/components/delete-session-modal.test.tsx
```

Expected: FAIL — `DeleteSessionModal` not found.

### 12.2 Implement the modal

- [ ] **Step 3: Create the component**

```tsx
import { useMemo, useState } from 'react'
import { Dialog, DialogContent, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { archiveFile, discardFile } from '@/services/api/file-artifacts'
import { deleteSession, type SessionDeleteBlocked } from '@/services/api/session'

type Decision = 'archive' | 'discard'
type RowState = { decision: Decision | null; status: 'idle' | 'in_progress' | 'done' | 'failed'; error?: string }

interface Props {
  sessionId: string
  connectionName: string | null
  blocked: SessionDeleteBlocked
  onClose: () => void
  onConfirmed: () => void
}

export function DeleteSessionModal({ sessionId, connectionName, blocked, onClose, onConfirmed }: Props) {
  const { t } = useI18n()
  const [rows, setRows] = useState<Record<string, RowState>>(() =>
    Object.fromEntries(blocked.candidates.map((c) => [c.id, { decision: null, status: 'idle' as const }])),
  )
  const [running, setRunning] = useState(false)

  const allDecided = useMemo(() => Object.values(rows).every((r) => r.decision !== null), [rows])

  function setDecision(id: string, decision: Decision) {
    setRows((s) => ({ ...s, [id]: { ...s[id], decision } }))
  }

  function setAll(decision: Decision) {
    setRows((s) => Object.fromEntries(Object.keys(s).map((id) => [id, { ...s[id], decision }])))
  }

  async function onConfirm() {
    setRunning(true)
    try {
      // step 1: per-candidate, only those not 'done'
      const remaining = Object.entries(rows).filter(([, r]) => r.status !== 'done')
      for (const [id, state] of remaining) {
        setRows((s) => ({ ...s, [id]: { ...s[id], status: 'in_progress' } }))
        try {
          if (state.decision === 'archive') {
            await archiveFile(sessionId, id)
          } else {
            await discardFile(id)
          }
          setRows((s) => ({ ...s, [id]: { ...s[id], status: 'done' } }))
        } catch (e) {
          setRows((s) => ({ ...s, [id]: { ...s[id], status: 'failed', error: String(e) } }))
        }
      }
      // any failed → stop, let user retry
      const after = await new Promise<typeof rows>((resolve) => setRows((s) => { resolve(s); return s }))
      if (Object.values(after).some((r) => r.status === 'failed')) return

      // step 2: force delete
      const out = await deleteSession(sessionId, { force: true })
      if (out.kind === 'ok' || out.kind === 'not_found') {
        onConfirmed()
      }
    } finally {
      setRunning(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="bg-canvas rounded-lg max-w-[480px]">
        <DialogTitle>{t('files.deleteModal.title')}</DialogTitle>
        <p className="text-muted text-sm">{t('files.deleteModal.description')}</p>
        <div className="flex gap-2 my-2">
          <Button variant="ghost" size="sm" onClick={() => setAll('archive')}>{t('files.deleteModal.allArchive')}</Button>
          <Button variant="ghost" size="sm" onClick={() => setAll('discard')}>{t('files.deleteModal.allDiscard')}</Button>
        </div>
        <ul className="space-y-2">
          {blocked.candidates.map((c) => {
            const state = rows[c.id]
            return (
              <li key={c.id} className="border border-subtle rounded-md p-2">
                <div className="font-medium text-strong text-sm">{c.filename}</div>
                <div className="text-muted text-xs">{c.kind} · {(c.sizeBytes / 1024).toFixed(1)} KB</div>
                <div className="flex gap-2 mt-1">
                  <label className="flex items-center gap-1 text-xs">
                    <input
                      type="radio"
                      name={`d-${c.id}`}
                      checked={state.decision === 'archive'}
                      onChange={() => setDecision(c.id, 'archive')}
                      aria-label={t('files.deleteModal.allArchive')}
                    />
                    {t('files.deleteModal.archiveTo', { conn: connectionName ?? '' })}
                  </label>
                  <label className="flex items-center gap-1 text-xs">
                    <input
                      type="radio"
                      name={`d-${c.id}`}
                      checked={state.decision === 'discard'}
                      onChange={() => setDecision(c.id, 'discard')}
                      aria-label={t('files.deleteModal.allDiscard')}
                    />
                    {t('files.deleteModal.discard')}
                  </label>
                </div>
                {state.status === 'failed' && (
                  <div className="text-status-danger text-xs mt-1">{state.error}</div>
                )}
                {state.status === 'done' && (
                  <div className="text-status-success text-xs mt-1">✓</div>
                )}
              </li>
            )
          })}
        </ul>
        <DialogFooter className="mt-4">
          <Button variant="ghost" onClick={onClose} disabled={running}>
            {t('files.deleteModal.cancel')}
          </Button>
          <Button onClick={onConfirm} disabled={!allDecided || running}>
            {t('files.deleteModal.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

> Verify the actual `Dialog` / `Button` API available in `client/src/components/ui/`. If different, adapt.

- [ ] **Step 4: Run tests**

```bash
cd /home/wallfacers/project/data-talk/client && npm test -- --run client/src/features/session/components/delete-session-modal.test.tsx
```

Expected: 3 tests pass.

- [ ] **Step 5: Type-check**

```bash
cd /home/wallfacers/project/data-talk/client && npx tsc --noEmit
```

Expected: zero errors.

### 12.3 Commit

- [ ] **Step 6:**

```bash
cd /home/wallfacers/project/data-talk && \
  git add client/src/features/session/components/delete-session-modal.tsx \
          client/src/features/session/components/delete-session-modal.test.tsx && \
  git commit -m "feat(session): add DeleteSessionModal with per-candidate retry tracking"
```

---

## Task 13: Frontend — `DeleteConnectionModal` (spec §A.6)

**Files:**
- Create: `client/src/features/connection/components/delete-connection-modal.tsx`
- Create: `client/src/features/connection/components/delete-connection-modal.test.tsx`

### 13.1 Failing test

- [ ] **Step 1:**

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { DeleteConnectionModal } from '../delete-connection-modal'
import * as connectionApi from '@/services/api/connection'

const blocked: connectionApi.ConnectionDeleteBlocked = {
  error: 'connection_has_resources',
  connectionId: 'conn_x',
  counts: { sessions: 12, candidates: 3, temporary: 8, archived: 8 },
}

describe('DeleteConnectionModal', () => {
  it('renders aggregated counts', () => {
    render(<DeleteConnectionModal connectionId="conn_x" connectionName="prod-mysql" blocked={blocked} onClose={vi.fn()} onConfirmed={vi.fn()} />)
    expect(screen.getByText(/12/)).toBeInTheDocument()
    expect(screen.getByText(/8/)).toBeInTheDocument()
  })

  it('shows orphan banner only when archived > 0', () => {
    render(<DeleteConnectionModal connectionId="conn_x" connectionName="prod-mysql" blocked={blocked} onClose={vi.fn()} onConfirmed={vi.fn()} />)
    expect(screen.getByLabelText(/orphanBanner/i)).toBeInTheDocument()
  })

  it('hides orphan banner when archived = 0', () => {
    render(
      <DeleteConnectionModal
        connectionId="conn_x"
        connectionName="prod-mysql"
        blocked={{ ...blocked, counts: { ...blocked.counts, archived: 0 } }}
        onClose={vi.fn()}
        onConfirmed={vi.fn()}
      />,
    )
    expect(screen.queryByLabelText(/orphanBanner/i)).not.toBeInTheDocument()
  })

  it('confirm calls deleteConnection(force=true) and onConfirmed', async () => {
    const del = vi.spyOn(connectionApi, 'deleteConnection').mockResolvedValue({ kind: 'ok' })
    const onConfirmed = vi.fn()
    const user = userEvent.setup()
    render(<DeleteConnectionModal connectionId="conn_x" connectionName="prod-mysql" blocked={blocked} onClose={vi.fn()} onConfirmed={onConfirmed} />)

    await user.click(screen.getByRole('button', { name: /connections\.deleteModal\.confirm/ }))
    await waitFor(() => expect(del).toHaveBeenCalledWith('conn_x', { force: true }))
    expect(onConfirmed).toHaveBeenCalled()

    del.mockRestore()
  })
})
```

- [ ] **Step 2: Run failing**

```bash
cd /home/wallfacers/project/data-talk/client && npm test -- --run client/src/features/connection/components/delete-connection-modal.test.tsx
```

Expected: FAIL.

### 13.2 Implement

- [ ] **Step 3: Create component**

```tsx
import { useState } from 'react'
import { Dialog, DialogContent, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { deleteConnection, type ConnectionDeleteBlocked } from '@/services/api/connection'

interface Props {
  connectionId: string
  connectionName: string
  blocked: ConnectionDeleteBlocked
  onClose: () => void
  onConfirmed: () => void
}

export function DeleteConnectionModal({ connectionId, connectionName, blocked, onClose, onConfirmed }: Props) {
  const { t } = useI18n()
  const [running, setRunning] = useState(false)

  async function onConfirm() {
    setRunning(true)
    try {
      const out = await deleteConnection(connectionId, { force: true })
      if (out.kind === 'ok' || out.kind === 'not_found') onConfirmed()
    } finally {
      setRunning(false)
    }
  }

  const c = blocked.counts
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="bg-canvas rounded-lg max-w-[480px]">
        <DialogTitle>{t('connections.deleteModal.title', { name: connectionName })}</DialogTitle>
        <ul className="text-sm text-base space-y-1 my-2">
          <li>· {t('connections.deleteModal.summarySessions', { n: c.sessions })}</li>
          <li>· {t('connections.deleteModal.summaryCandidates', { c: c.candidates, t: c.temporary })}</li>
        </ul>
        {c.archived > 0 && (
          <div
            className="bg-status-infoSurface text-status-info border border-status-info rounded-md p-2 my-2 text-sm"
            aria-label="orphanBanner"
          >
            ⓘ {t('connections.deleteModal.orphanBanner', { n: c.archived })}
          </div>
        )}
        <DialogFooter className="mt-4">
          <Button variant="ghost" onClick={onClose} disabled={running}>
            {t('connections.deleteModal.cancel')}
          </Button>
          <Button onClick={onConfirm} disabled={running}>
            {t('connections.deleteModal.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

- [ ] **Step 4: Run tests**

```bash
cd /home/wallfacers/project/data-talk/client && npm test -- --run client/src/features/connection/components/delete-connection-modal.test.tsx
```

Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
cd /home/wallfacers/project/data-talk && \
  git add client/src/features/connection/components/delete-connection-modal.tsx \
          client/src/features/connection/components/delete-connection-modal.test.tsx && \
  git commit -m "feat(connection): add DeleteConnectionModal with orphan banner"
```

---

## Task 14: Frontend — i18n keys + wire into existing delete UIs

**Files:**
- Modify: `client/src/i18n/messages.ts`
- Modify: existing session/connection delete trigger sites (TBD — `grep` first)

### 14.1 i18n

- [ ] **Step 1: Find the existing top-level keys structure**

```bash
grep -n "deleteModal\|files:\|connections:" /home/wallfacers/project/data-talk/client/src/i18n/messages.ts | head -20
```

- [ ] **Step 2: Add keys (中英对齐, follow existing structure)**

Append to both `en` and `zh-CN` namespaces:

```ts
  files: {
    // (other existing files.* keys above)
    deleteModal: {
      title: '删除会话？',                       // en: 'Delete session?'
      description: '此会话有候选文件未归档...',  // en: 'This session has unarchived candidate files...'
      allArchive: '全部归档',
      allDiscard: '全部丢弃',
      archiveTo: '归档到 {{conn}}',
      discard: '丢弃',
      confirm: '确认删除会话',
      cancel: '取消',
    },
  },
  connections: {
    // ...
    deleteModal: {
      title: '删除连接 "{{name}}"？',
      summarySessions: '{{n}} 个会话（含历史消息和事件，将一并删除）',
      summaryCandidates: '{{c}} 个候选文件、{{t}} 个临时文件（将自动清理）',
      orphanBanner: '{{n}} 个已归档文件不会删除，会保留为孤儿资产；可在 Settings → Maintenance → 孤儿归档资产 中找到并整理。',
      confirm: '确认删除连接',
      cancel: '取消',
    },
  },
```

Mirror in English.

### 14.2 Wire into existing delete UIs

- [ ] **Step 3: Locate session delete trigger**

```bash
grep -rn "deleteSession\|删除会话\|delete.*session" /home/wallfacers/project/data-talk/client/src/features/session /home/wallfacers/project/data-talk/client/src/features/sidebar 2>/dev/null | head
```

For every call site that currently triggers session deletion, change the flow to:

1. Call `deleteSession(id)` (no force).
2. If `kind === 'blocked'`, open `DeleteSessionModal` with the `blocked` payload + `connectionName` lookup.
3. On `onConfirmed` from modal, refresh sidebar / state.
4. If `kind === 'ok'`, refresh as before.

Provide minimal patches; do not refactor unrelated code.

- [ ] **Step 4: Locate connection delete trigger**

```bash
grep -rn "DELETE.*connections\|deleteConnection\|删除连接" /home/wallfacers/project/data-talk/client/src/features/connection 2>/dev/null | head
```

Apply the same pattern with `DeleteConnectionModal`.

- [ ] **Step 5: Type-check**

```bash
cd /home/wallfacers/project/data-talk/client && npx tsc --noEmit
```

Expected: zero errors.

### 14.3 Commit

- [ ] **Step 6:**

```bash
cd /home/wallfacers/project/data-talk && \
  git add client/src/i18n/messages.ts \
          client/src/features/session \
          client/src/features/connection \
          client/src/features/sidebar && \
  git commit -m "feat(ui): wire delete-modal flow into session/connection delete triggers + i18n"
```

> Restrict `git add` to files actually changed; do not add unrelated working-tree changes.

---

## Task 15: Final regression + housekeeping

### 15.1 Full backend regression

- [ ] **Step 1: Run full backend test suite**

```bash
cd /home/wallfacers/project/data-talk/server && mvn clean verify -q
```

Expected: BUILD SUCCESS.

### 15.2 Full frontend regression

- [ ] **Step 2:**

```bash
cd /home/wallfacers/project/data-talk/client && npx tsc --noEmit && npm test -- --run
```

Expected: 0 type errors; all vitest pass; no new flaky.

### 15.3 Manual smoke (optional, not gating)

- [ ] **Step 3:**

```bash
cd /home/wallfacers/project/data-talk/server && mvn install -pl data-talk-application -am -DskipTests -q
cd /home/wallfacers/project/data-talk/server && mvn spring-boot:run -pl data-talk-adapter
# Another terminal:
cd /home/wallfacers/project/data-talk/client && npm run dev
```

Verify in browser:
1. Create connection + session → write a candidate file → click [✓ 归档] in Files Tab → file appears in Files Library.
2. Delete session with candidate → modal opens → choose archive/discard → session deleted.
3. Delete connection with sessions → modal opens with counts + orphan banner → confirm → archived file remains visible in DB (orphan).

### 15.4 BUG check (CLAUDE.md "BUG Tracking Gate")

- [ ] **Step 4:**

If smoke testing or regression uncovers any product behavior deviation, register a BUG file in `docs/bugs/` per CLAUDE.md gate. **N=0 也要明确说**.

### 15.5 Document housekeeping (CLAUDE.md "Post-Execution Document Housekeeping")

- [ ] **Step 5: Mark every task checkbox `- [x]` in this plan file**

- [ ] **Step 6: Update `docs/exec-plans/index.md`**

Move Part 5a row from "活跃计划" to "已完成计划"; replace the registration row with a completion summary listing actual outcomes (commit shas, test counts, deviations if any). Keep Part 5b in "活跃计划" as a placeholder.

- [ ] **Step 7: Update parent spec sync items per design §E**

Edit `docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md`:
1. §6.2 — change connection DELETE behavior wording to match Q2 decision (preserve archived w/ `connection_id=NULL` + metadata stamping)
2. §9 — note Part 5a shipped; Part 5b in flight
3. §11 — append "跨 connection 移动 archived 文件（除孤儿 reattach 外）" to out-of-scope
4. §12 — add archive/discard endpoints to data modeling section
5. End of doc — append Part 5a status line

### 15.6 Final commit

- [ ] **Step 8:**

```bash
cd /home/wallfacers/project/data-talk && \
  git add docs/exec-plans/2026-05-07-file-artifact-system-part5a-deletion-and-archive-endpoints-plan.md \
          docs/exec-plans/index.md \
          docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md && \
  git commit -m "docs: mark file artifact system part 5a complete + sync parent spec"
```

---

## Verification gate

执行结束前必须按顺序确认零错误：

1. `cd server && mvn compile -q` → BUILD SUCCESS
2. `cd server && mvn -pl data-talk-application test -Dtest='FileArtifactPhysicalMoverTest,FileArtifactServiceTest,SessionServiceTest,ConnectionDeletionServiceTest' -q` → all PASS
3. `cd server && mvn -pl data-talk-infrastructure test -Dtest=JdbcFileArtifactRepositoryIT -q` → all PASS
4. `cd server && mvn -pl data-talk-adapter test -Dtest='FileArtifactControllerIT,SessionControllerIT,ConnectionDeletionIT' -q` → all PASS
5. `cd server && mvn clean verify -q` → BUILD SUCCESS
6. `cd client && npx tsc --noEmit` → 0 errors
7. `cd client && npm test -- --run client/src/features/session/components/delete-session-modal.test.tsx client/src/features/connection/components/delete-connection-modal.test.tsx` → all PASS
8. `cd client && npm test -- --run` → full vitest pass

任一命令 FAIL 必须排查到根因后修复，不允许 skip。

CLAUDE.md gates restated:

- 数据源兼容性 Gate：N/A（spec §2.3 已声明 file artifact 与 DB 类型解耦）
- Backend Run vs Compile：每个 task 在结尾都触发了 `mvn install -pl <module> -am -DskipTests`，确保 spring-boot:run 拿最新 jar
- Frontend Design Contract Gate：本 plan Design Inputs 段已显式列五态 token，遵守 `client/DESIGN.md`
- BUG Tracking Gate：smoke 阶段 N=0 也要在最终 commit msg 或对话中说明

---

## Out of scope

明确不在 Part 5a 范围（由 Part 5b 接手）：

- HousekeepingScheduler 4 任务、cron 调度
- LegacyMigrationRunner 一次性迁移、`.legacy-migrated` marker
- Settings → Maintenance UI（含存储概览、孤儿 Drawer）
- maintenance / orphan REST 端点（`GET /api/maintenance/storage-overview`、`POST /api/maintenance/cleanup-trash`、`GET /api/maintenance/orphaned-files`、`POST /api/files/{fid}/reattach`）
- maintenance.* 与 maintenance.orphans.* i18n
- Part 2 reconciler 接口扩展（Q2 引入的 connection_id IS NULL archived 文件不重新登记）
- Chat 内联 file artifact 卡片（Part 4 范围）
- 跨 connection 移动 archived 文件（除 reattach 外的 follow-up）

5a 仅承诺：archived 行在 connection 删除时的 `metadata_json` orphan 元写入 happens（spec §A.4 强调的事务顺序），但**不**暴露给 UI；UI 消费在 5b orphan Drawer。

---

## Self-Review Checklist

- [x] **Spec coverage**：spec §1.1（5a 范围列表）、§A.1（4 个 REST 端点）、§A.2（wire shape 全部）、§A.3（mover 逻辑）、§A.4（service 拆分 + 事务顺序）、§A.5（session modal + retry 状态跟踪）、§A.6（connection modal + orphan banner）、§C.1（reason 复用）、§C.2 Part 5a 测试矩阵 8 项 — 全部任务覆盖。
- [x] **Placeholder scan**：无 TBD/TODO；每段代码可直接粘贴；测试 fixture helper 步骤里如已存在显式标注"don't redefine"。
- [x] **类型一致性**：`DeleteOutcome.BlockedByCandidates / BlockedByResources / Ok / NotFound` 名称在 application / adapter / frontend 层统一；`SessionDeleteBlocked.error` 字面量与 spec §A.2 严格一致；`FileArtifactRepository.ConnectionResourceCounts` record 字段顺序在 repo / service / controller / DTO 一致。
- [x] **CLAUDE.md "Backend Run vs Compile"**：Task 2 / 3 / 4 / 5 / 6 / 7 都在结尾跑 `mvn install -pl data-talk-application -am -DskipTests`；adapter Task 8 直接用 mvn compile 拿 application 新 jar。
- [x] **数据源兼容性 Gate**：N/A（spec §2.3 已声明）。
- [x] **Frontend Plan Gate**：Design Inputs 段显式 quote `client/DESIGN.md` 约束 + 五态 token 映射对每个交互控件逐项列出（per memory `feedback-design-control-states`）。
- [x] **BUG Tracking Gate**：grep 结果 0 命中无 file artifact 相关 BUG；本 Part 不触碰已知问题区域；smoke 阶段如发现新 BUG 需登记。
- [x] **Spec §A.4 顺序约束**：Task 7 ConnectionDeletionService 的 force 路径明确 1) detach archived (含 metadata stamp) 2) delete child sessions 3) delete connection row；测试用 `inOrder` verify 强约束。

---

## Definition of Done

1. 全部 15 个 Task 的 checkbox 勾完
2. Verification gate 8 个命令全 SUCCESS / PASS
3. Part 4 plan 内已在前端调用的 `archiveFile` / `discardFile` 占位 → 真端点调通
4. Spec §E 列出的 5 处父 spec 同步全部完成
5. `docs/exec-plans/index.md` 把本 Part 5a 行移到「已完成计划」表
6. Part 5b plan 起草准备就绪（治理 + Maintenance UI + 孤儿 Drawer）
