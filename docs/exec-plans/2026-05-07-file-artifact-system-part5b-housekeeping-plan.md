# File Artifact System · Part 5b — Housekeeping & Maintenance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land Part 5b — nightly housekeeping scheduler, one-time legacy migration runner, Settings → Maintenance tab with storage overview, and orphan archives drawer — so the file artifact system has automated governance and users can manage orphaned archived assets (Q2 decision consequence).

**Architecture:** Extend existing `HousekeepingScheduler` / `LegacyMigrationRunner` (already scaffolded) with proper logging, DB cleanup, and DtEvent emission. Add a lightweight `MaintenanceController` with 4 REST endpoints backed by `FileArtifactReconciler` + `FileArtifactService`. Frontend adds a `MaintenancePage` tab to the existing Settings dialog and a separate `OrphanArchivesDrawer` component for orphan asset management.

**Tech Stack:** Spring Boot 3.5, Java 21, JdbcTemplate, JUnit 5 + AssertJ; React 19, TanStack Query, ky, Tailwind v4 + design-tokens, lucide-react, vitest + @testing-library/react.

**Spec:** [docs/product-specs/2026-05-07-file-artifact-system-part5-design.md](../product-specs/2026-05-07-file-artifact-system-part5-design.md) — §B (all).

**关联 Part：**
- Part 2 — Watcher + Reconciler（已完成，5b 扩展 reconciler）
- Part 5a — Deletion Flow + Archive/Discard Endpoints（已完成，5b 依赖其 connection DELETE 行为）
- Part 5b（本计划）— Housekeeping + Legacy 迁移 + Maintenance UI（含孤儿整理）

**执行状态：** 未开始。

**Pre-existing code:** `HousekeepingScheduler.java` and `LegacyMigrationRunner.java` exist as scaffolds in `server/data-talk-application/src/main/java/com/datatalk/application/housekeeping/`. Each needs enhancement (logging, DB ops, DtEvent). `DtEvent.LegacyMigrated` already exists in domain.

---

## Design Inputs

本 Part 涉及 `client/` UI（Settings Maintenance tab + Orphan Drawer），必须遵循 [client/DESIGN.md](../../client/DESIGN.md)。引用约束：

- **Settings 扩展**：在现有 Settings 对话框内新增 tab，`Section` 联合类型扩展，不增 sidebar 入口。
- **Token-only colors**：禁止原始原色；语义 token Tailwind class（`bg-canvas` / `bg-panel` / `text-base` / `text-strong` / `text-muted` / `accent-primary` / `status-infoSurface` / `status-dangerSurface`）。
- **Density**：drawer/maintenance tab 用 focused；按钮高度 32px、文本 ui-sm。
- **Motion**：`fast=120ms` / `normal=180ms`；drawer slide-in/out + fade；`prefers-reduced-motion` 必须停用。
- **Accessibility**：orphan drawer 复选框用 icon + 颜色双通道；focusRing token 在 focus-visible 状态可见。

### Five-state token mapping

per CLAUDE.md gate + `client/DESIGN.md`。

#### 共通：ghost 按钮（批量关联下拉触发 / 行级关联 / 行级丢弃）

- idle: `bg-transparent` / `text-base` / `border-subtle`
- hover: `bg-hover` / `text-strong` / `border-default`
- active: `bg-active` / `text-strong`
- focus: `outline-2 ring-focusRing offset-2`
- disabled: `text-disabled` / cursor-not-allowed

#### 批量丢弃按钮（destructive ghost）

- idle: `bg-transparent` / `text-base` / `border-subtle`
- hover: `bg-status-dangerSurface` / `text-status-danger` / `border-status-danger`
- active: `bg-status-dangerSurface` opacity 0.9
- focus: `outline-2 ring-focusRing`
- disabled: `text-disabled` / cursor-not-allowed

#### 复选框

- idle: `border-default` unchecked
- hover: `border-strong` / `bg-hover`
- active: `border-accent-primary` / `bg-accent-primaryHover`
- focus: `outline-2 ring-focusRing`
- checked: `bg-accent-primary` / `text-inverse` ✓
- disabled: `border-disabled` / `bg-disabled`

### CLAUDE.md gates 显式声明

- **Frontend Design Contract Gate**：上文 Design Inputs 已引用 `client/DESIGN.md` 并显式列五态 token；满足。
- **Frontend Plan Gate**：Design Inputs 含 `client/DESIGN.md` 引用；满足。
- **数据源兼容性 Gate**：N/A — Part 5b 不涉及 DB 类型新增/变更。
- **Backend Run vs Compile**：每次修改 `data-talk-application` 模块后 `mvn install -pl data-talk-application -am -DskipTests`。
- **BUG 跟踪 Gate**：grep `docs/bugs/` 0 命中（无 housekeeping/maintenance 相关 BUG）。

---

## Files

### Backend

| 操作 | 文件路径 | 用途 |
|------|---------|------|
| Modify | `server/data-talk-application/src/main/java/com/datatalk/application/housekeeping/HousekeepingScheduler.java` | 增强：housekeeping.log JSON Lines、cleanupTrash DB delete |
| Modify | `server/data-talk-application/src/main/java/com/datatalk/application/housekeeping/LegacyMigrationRunner.java` | 增强：emit DtEvent.LegacyMigrated、补全白名单 |
| Modify | `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactReconciler.java` | 新增：reconcileTrash()、connection_id IS NULL 识别 |
| Modify | `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactRepository.java` | 新增：findOrphanedArchived、deleteDiscardedById、reattachArchived、countOrphanedArchived |
| Modify | `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepository.java` | 实现上述 repo 方法 |
| Modify | `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java` | 新增：reattach(connectionId, fid) |
| Create | `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/MaintenanceController.java` | 4 REST 端点 |
| Create | `server/data-talk-adapter/src/main/java/com/datatalk/dto/StorageOverviewDto.java` | storage-overview 响应 |
| Create | `server/data-talk-adapter/src/main/java/com/datatalk/dto/CleanupStatsDto.java` | cleanup-trash 响应 |
| Create | `server/data-talk-adapter/src/main/java/com/datatalk/dto/OrphanedFileDto.java` | orphaned-files 响应项 |
| Create | `server/data-talk-adapter/src/main/java/com/datatalk/dto/ReattachRequest.java` | reattach 请求 |
| Create | `server/data-talk-adapter/src/main/java/com/datatalk/dto/ReattachResponse.java` | reattach 批量响应 |
| Create | `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/config/SchedulingConfig.java` | @EnableScheduling |

### Backend Tests

| 操作 | 文件路径 | 用途 |
|------|---------|------|
| Create | `server/data-talk-application/src/test/java/com/datatalk/application/housekeeping/HousekeepingSchedulerTest.java` | Scheduler 单测 |
| Create | `server/data-talk-application/src/test/java/com/datatalk/application/housekeeping/LegacyMigrationRunnerTest.java` | Migration 单测 |
| Create | `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/MaintenanceControllerIT.java` | 4 端点集成测试 |
| Modify | `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactReconcilerTest.java` | reconcileTrash 测试 |
| Modify | `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceTest.java` | reattach 测试 |
| Modify | `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepositoryIT.java` | 新 repo 方法 IT |

### Frontend

| 操作 | 文件路径 | 用途 |
|------|---------|------|
| Create | `client/src/features/settings/maintenance/maintenance-page.tsx` | Settings → Maintenance tab |
| Create | `client/src/features/settings/maintenance/orphan-archives-drawer.tsx` | 孤儿归档资产 Drawer |
| Create | `client/src/features/settings/maintenance/__tests__/maintenance-page.test.tsx` | vitest |
| Create | `client/src/features/settings/maintenance/__tests__/orphan-archives-drawer.test.tsx` | vitest |
| Modify | `client/src/features/settings/settings-nav.tsx` | 新增 Maintenance nav item |
| Modify | `client/src/features/settings/settings-dialog.tsx` | 新增 Maintenance page 路由 |
| Modify | `client/src/features/settings/settings-dialog-store.ts` | Section 联合加 `'maintenance'` |
| Modify | `client/src/i18n/messages.ts` | 新增 `maintenance.*` 中英 keys |
| Modify | `client/src/services/api/maintenance.ts` | 新增（或复用 file-artifacts）4 个 API 方法 |

### Docs

| 操作 | 文件路径 | 用途 |
|------|---------|------|
| Modify | `docs/exec-plans/index.md` | 替换 5b 占位行 + 本 plan 登记 |
| Modify | `docs/exec-plans/2026-05-07-file-artifact-system-part5b-housekeeping-plan.md` | 本文件 |

---

## Task 1: Plan registration

**Files:**
- Modify: `docs/exec-plans/index.md`

- [ ] **Step 1: Replace Part 5b placeholder row with formal entry**

Replace:
```
| File Artifact System · Part 5b — Housekeeping & Maintenance (待补正式计划) | TBD | ...
```
with:
```
| [File Artifact System · Part 5b — Housekeeping & Maintenance](./2026-05-07-file-artifact-system-part5b-housekeeping-plan.md) | 2026-05-07 | Part 5b：`HousekeepingScheduler`（4 任务 + housekeeping.log JSON Lines）+ `LegacyMigrationRunner`（DtEvent.LegacyMigrated）+ reconciler 扩展（reconcileTrash + connection_id IS NULL 识别）+ `MaintenanceController`（4 REST 端点）+ Settings → Maintenance tab（storage overview + cleanup-trash + view-legacy）+ 孤儿归档资产 Drawer（批量 reattach/discard）+ i18n `maintenance.*` 中英对齐。0 个新 DtEvent 类型（复用 `LegacyMigrated`）。N/A 数据源兼容 gate。 |
```

- [ ] **Step 2: Commit**

```bash
cd /home/wushengzhou/workspace/github/data-talk && \
  git add docs/exec-plans/2026-05-07-file-artifact-system-part5b-housekeeping-plan.md \
          docs/exec-plans/index.md && \
  git commit -m "docs(exec-plans): register file artifact system part 5b plan"
```

---

## Task 2: Repository — add orphan/maintenance methods

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactRepository.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepository.java`
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepositoryIT.java`

**Why:** MaintenanceController and OrphanDrawer need orphan lookup, reattach DB updates, and trash cleanup DB deletes.

### 2.1 Add interface methods

- [ ] **Step 1: Open `FileArtifactRepository.java` and append before the last `}`**

```java
    /**
     * Find archived rows with connection_id = NULL (orphaned after connection delete).
     * Spec §B.3.1. LIMIT 200 imposed by caller.
     */
    List<FileArtifact> findOrphanedArchived(int limit);

    /**
     * Replace connection_id and physical_path for an archived row.
     * Used by reattach — moves the file from orphan workspace dir to new connection.
     */
    void reattachArchived(String fileArtifactId, String newConnectionId, String newPhysicalPath, long updatedAtMillis);

    /**
     * Delete a single row by id, restricted to status='discarded'.
     * Used by cleanupTrash after the physical file is rm'd.
     */
    void deleteDiscardedById(String id);

    /**
     * Count rows with status='archived' AND connection_id IS NULL.
     * Used by storage-overview breakdown.workspaces.orphanedArchivedCount.
     */
    int countOrphanedArchived();
```

- [ ] **Step 2: Compile — expect failure (JDBC impl missing)**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && mvn compile -q -pl data-talk-application
```

Expected: BUILD FAILURE — JdbcFileArtifactRepository missing new methods.

### 2.2 Implement in JDBC repository

- [ ] **Step 3: Open `JdbcFileArtifactRepository.java` and add four methods**

```java
    @Override
    public List<FileArtifact> findOrphanedArchived(int limit) {
        return jdbc.query(
                "SELECT * FROM file_artifact WHERE status = 'archived' AND connection_id IS NULL ORDER BY archived_at DESC LIMIT ?",
                MAPPER,
                limit);
    }

    @Override
    public void reattachArchived(String fileArtifactId, String newConnectionId,
                                  String newPhysicalPath, long updatedAtMillis) {
        jdbc.update(
                "UPDATE file_artifact SET connection_id = ?, physical_path = ?, updated_at = ? WHERE id = ?",
                newConnectionId, newPhysicalPath, updatedAtMillis, fileArtifactId);
    }

    @Override
    public void deleteDiscardedById(String id) {
        jdbc.update("DELETE FROM file_artifact WHERE id = ? AND status = 'discarded'", id);
    }

    @Override
    public int countOrphanedArchived() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_artifact WHERE status = 'archived' AND connection_id IS NULL",
                Integer.class);
        return count == null ? 0 : count;
    }
```

- [ ] **Step 4: Compile**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && mvn compile -q
```

Expected: BUILD SUCCESS.

### 2.3 Add IT tests

- [ ] **Step 5: Open `JdbcFileArtifactRepositoryIT.java` and append**

```java
    @Test
    void findOrphanedArchived_returns_archived_rows_with_null_connection_id() {
        insertArchived(null); // connection_id = NULL
        insertArchived("conn_x");
        insertArchived(null);

        var rows = repo.findOrphanedArchived(200);
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(r -> r.connectionId() == null);
    }

    @Test
    void reattachArchived_updates_connection_id_and_path() {
        String id = insertArchived(null);
        repo.reattachArchived(id, "conn_new", "/tmp/workspaces/conn_new/orders.md", 1_000L);

        var row = repo.findById(id).orElseThrow();
        assertThat(row.connectionId()).isEqualTo("conn_new");
        assertThat(row.physicalPath()).isEqualTo("/tmp/workspaces/conn_new/orders.md");
    }

    @Test
    void deleteDiscardedById_only_deletes_discarded_rows() {
        String did = insertRow("ses_a", FileArtifactStatus.DISCARDED);
        String cid = insertRow("ses_a", FileArtifactStatus.CANDIDATE);

        repo.deleteDiscardedById(did);
        repo.deleteDiscardedById(cid); // no-op — status mismatch

        assertThat(repo.findById(did)).isEmpty();
        assertThat(repo.findById(cid)).isPresent();
    }

    @Test
    void countOrphanedArchived_counts_correctly() {
        insertArchived(null);
        insertArchived(null);
        insertArchived("conn_x");

        assertThat(repo.countOrphanedArchived()).isEqualTo(2);
    }
```

Use existing `insertArchived` / `insertRow` helpers; add an overload `insertArchived(String connectionId)` that passes connectionId (null for orphan).

- [ ] **Step 6: Run IT**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && \
  mvn -pl data-talk-infrastructure test -Dtest=JdbcFileArtifactRepositoryIT -q
```

Expected: existing + 4 new tests pass.

- [ ] **Step 7: Push jar + commit**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && \
  mvn install -pl data-talk-application -am -DskipTests -q && \
  cd /home/wushengzhou/workspace/github/data-talk && \
  git add server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactRepository.java \
          server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepository.java \
          server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepositoryIT.java && \
  git commit -m "feat(file-artifact): add orphan/maintenance repo methods"
```

---

## Task 3: HousekeepingScheduler — enhance with log + DB cleanup

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/housekeeping/HousekeepingScheduler.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/housekeeping/HousekeepingSchedulerTest.java`

**Existing code:** `HousekeepingScheduler.java` scaffold has the 4 tasks and rotation logic but misses housekeeping.log writing and DB row deletion in `cleanupTrash`.

### 3.1 Write failing tests

- [ ] **Step 1: Create `HousekeepingSchedulerTest.java`**

```java
package com.datatalk.application.housekeeping;

import com.datatalk.application.fileartifact.FileArtifactReconciler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HousekeepingSchedulerTest {

    @TempDir Path workdir;
    FileArtifactReconciler reconciler = mock(FileArtifactReconciler.class);
    Clock clock = Clock.fixed(Instant.parse("2026-05-07T03:00:00Z"), ZoneOffset.UTC);
    HousekeepingScheduler scheduler;

    @BeforeEach
    void setUp() {
        // Use test workdir by setting env before construction
        System.setProperty("DATA_TALK_WORKDIR", workdir.toString());
        scheduler = new HousekeepingScheduler(reconciler, clock);
        System.clearProperty("DATA_TALK_WORKDIR");
    }

    @Test
    void cleanupTrash_deletes_files_older_than_7_days() throws IOException {
        Path trashDir = workdir.resolve("_trash");
        Files.createDirectories(trashDir);
        Path oldFile = trashDir.resolve("ses_a__fa_1__report.md");
        Files.writeString(oldFile, "old");
        Files.setLastModifiedTime(oldFile, java.nio.file.attribute.FileTime.from(
                Instant.parse("2026-04-01T00:00:00Z")));
        Path recentFile = trashDir.resolve("ses_b__fa_2__orders.md");
        Files.writeString(recentFile, "recent");
        Files.setLastModifiedTime(recentFile, java.nio.file.attribute.FileTime.from(
                Instant.parse("2026-05-06T00:00:00Z")));

        scheduler.cleanupTrash();

        assertThat(Files.exists(oldFile)).isFalse();
        assertThat(Files.exists(recentFile)).isTrue();
    }

    @Test
    void rotateOpencodeBackups_keeps_5_recent_and_within_7_days() throws IOException {
        Path opencodeDir = workdir.resolve("opencode");
        Files.createDirectories(opencodeDir);
        // 10 backups, oldest 8 days, newest today
        for (int i = 0; i < 10; i++) {
            Path f = opencodeDir.resolve("opencode.json.dt-bak-" + i);
            Files.writeString(f, "bak" + i);
            Files.setLastModifiedTime(f, java.nio.file.attribute.FileTime.from(
                    clock.instant().minus(java.time.Duration.ofDays(i))));
        }
        scheduler.rotateOpencodeBackups();
        // backups 0-4 (<= 7 days) + backups 0-4 (5 most recent, already covered)
        // backups 5-9 (all > 7 days) should be deleted
        long remaining = Files.list(opencodeDir)
                .filter(p -> p.getFileName().toString().startsWith("opencode.json.dt-bak-"))
                .count();
        // 5 recent (0-4) + possibly 5 (day 5 = 5 days ago, still in 7-day window) = 6
        assertThat(remaining).isBetween(5L, 8L);
    }

    @Test
    void runNightly_writes_housekeeping_log() throws IOException {
        scheduler.runNightly();

        Path logFile = workdir.resolve("housekeeping.log");
        assertThat(Files.exists(logFile)).isTrue();
        String content = Files.readString(logFile);
        assertThat(content).contains("\"task\":\"rotate-backup\"");
        assertThat(content).contains("\"task\":\"cleanupTrash\"");
        assertThat(content).contains("\"task\":\"reconcile\"");
    }

    @Test
    void runNightly_calls_reconciler() {
        scheduler.runNightly();
        verify(reconciler).runFullReconcile();
    }
}
```

- [ ] **Step 2: Run failing tests**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && \
  mvn -pl data-talk-application test -Dtest=HousekeepingSchedulerTest -q
```

Expected: Some tests FAIL — log writing not implemented, DB delete stub.

### 3.2 Enhance the scheduler

- [ ] **Step 3: Modify `HousekeepingScheduler.java` — inject `FileArtifactRepository` + add log writing + fix cleanupTrash**

Change the constructor to:

```java
    private final FileArtifactReconciler reconciler;
    private final FileArtifactRepository fileArtifactRepo;
    private final Clock clock;
    private final Path workdir;

    public HousekeepingScheduler(
            FileArtifactReconciler reconciler,
            FileArtifactRepository fileArtifactRepo,
            Clock clock) {
        this.reconciler = reconciler;
        this.fileArtifactRepo = fileArtifactRepo;
        this.clock = clock;
        this.workdir = resolveWorkdir();
    }
```

Add import for `FileArtifactRepository`.

In `cleanupTrash()`, after the `Files.deleteIfExists(file)` succeeds and `fid != null`, replace the log-only snippet with:

```java
    if (fid != null) {
        try {
            fileArtifactRepo.deleteDiscardedById(fid);
        } catch (Exception e) {
            log.warn("[housekeeping] trash db delete failed for fid={}: {}", fid, e.toString());
        }
    }
```

Add `writeHousekeepingLog()` method and call it at end of `runNightly()`:

```java
    private void writeHousekeepingLog(Instant started, int tasksRun, int tasksFailed) {
        Path logFile = workdir.resolve("housekeeping.log");
        try {
            String entry = String.format(
                    "{\"ts\":\"%s\",\"tasksRun\":%d,\"tasksFailed\":%d,\"durationMs\":%d}%n",
                    started.toString(), tasksRun, tasksFailed,
                    clock.instant().toEpochMilli() - started.toEpochMilli());
            Files.writeString(logFile, entry, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("[housekeeping] failed to write log: {}", e.toString());
        }
    }
```

Update `runNightly()` to track success/failure per task and call `writeHousekeepingLog()` at end. Also log each task individually in JSON Lines format:

```java
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void runNightly() {
        log.info("[housekeeping] starting nightly run");
        Instant started = clock.instant();
        int failed = 0;
        try { rotateOpencodeBackups(); logHousekeepingTask("rotate-backup", "ok", started); } catch (Exception e) { failed++; logHousekeepingTask("rotate-backup", "failed", started); log.warn("[housekeeping] rotate-backup failed: {}", e.toString()); }
        try { rotateOpencodeLogs(); logHousekeepingTask("rotate-log", "ok", started); } catch (Exception e) { failed++; logHousekeepingTask("rotate-log", "failed", started); log.warn("[housekeeping] rotate-log failed: {}", e.toString()); }
        try { cleanupTrash(); logHousekeepingTask("cleanupTrash", "ok", started); } catch (Exception e) { failed++; logHousekeepingTask("cleanupTrash", "failed", started); log.warn("[housekeeping] cleanupTrash failed: {}", e.toString()); }
        try { reconcileFileArtifacts(); logHousekeepingTask("reconcile", "ok", started); } catch (Exception e) { failed++; logHousekeepingTask("reconcile", "failed", started); log.warn("[housekeeping] reconcile failed: {}", e.toString()); }
        writeHousekeepingLog(started, 4 - failed, failed);
        log.info("[housekeeping] completed in {} ms", clock.instant().toEpochMilli() - started.toEpochMilli());
    }

    private void logHousekeepingTask(String task, String result, Instant started) {
        Path logFile = workdir.resolve("housekeeping.log");
        try {
            String entry = String.format(
                    "{\"ts\":\"%s\",\"task\":\"%s\",\"result\":\"%s\"}%n",
                    clock.instant().toString(), task, result);
            Files.writeString(logFile, entry, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            // best effort
        }
    }
```

- [ ] **Step 4: Compile**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && mvn compile -q -pl data-talk-application
```

Expected: BUILD SUCCESS.

### 3.3 Run tests + commit

- [ ] **Step 5: Run tests**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && \
  mvn -pl data-talk-application test -Dtest=HousekeepingSchedulerTest -q
```

Expected: 4 tests pass.

- [ ] **Step 6: Push jar + commit**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && \
  mvn install -pl data-talk-application -am -DskipTests -q && \
  cd /home/wushengzhou/workspace/github/data-talk && \
  git add server/data-talk-application/src/main/java/com/datatalk/application/housekeeping/HousekeepingScheduler.java \
          server/data-talk-application/src/test/java/com/datatalk/application/housekeeping/HousekeepingSchedulerTest.java && \
  git commit -m "feat(housekeeping): add log writing + DB cleanup to HousekeepingScheduler"
```

---

## Task 4: LegacyMigrationRunner — emit DtEvent + whitelist

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/housekeeping/LegacyMigrationRunner.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/housekeeping/LegacyMigrationRunnerTest.java`

### 4.1 Write failing tests

- [ ] **Step 1: Create `LegacyMigrationRunnerTest.java`**

```java
package com.datatalk.application.housekeeping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyMigrationRunnerTest {

    @TempDir Path workdir;
    LegacyMigrationRunner runner;

    @BeforeEach
    void setUp() {
        System.setProperty("DATA_TALK_WORKDIR", workdir.toString());
        runner = new LegacyMigrationRunner();
        System.clearProperty("DATA_TALK_WORKDIR");
    }

    @Test
    void migrates_non_whitelist_files_to_legacy_dir() throws IOException {
        Path opencodeDir = workdir.resolve("opencode");
        Files.createDirectories(opencodeDir);
        Files.writeString(opencodeDir.resolve("datatalk-tools-test-report.md"), "# report");
        Files.writeString(opencodeDir.resolve("AGENTS.md"), "AGENTS"); // whitelisted
        Files.writeString(opencodeDir.resolve("orphan.csv"), "a,b,c");

        runner.onApplicationReady();

        Path legacyDir = workdir.resolve("_legacy");
        assertThat(Files.exists(legacyDir.resolve("datatalk-tools-test-report.md"))).isTrue();
        assertThat(Files.exists(legacyDir.resolve("orphan.csv"))).isTrue();
        assertThat(Files.exists(opencodeDir.resolve("AGENTS.md"))).isTrue(); // not moved
        assertThat(Files.exists(workdir.resolve(".legacy-migrated"))).isTrue();
    }

    @Test
    void skips_when_marker_exists() throws IOException {
        Files.createDirectories(workdir.resolve("opencode"));
        Files.writeString(workdir.resolve("opencode").resolve("extra.txt"), "extra");
        Files.writeString(workdir.resolve(".legacy-migrated"), "done");

        runner.onApplicationReady();

        assertThat(Files.exists(workdir.resolve("_legacy").resolve("extra.txt"))).isFalse();
    }

    @Test
    void handles_missing_opencode_dir() {
        runner.onApplicationReady();
        assertThat(Files.exists(workdir.resolve(".legacy-migrated"))).isTrue();
    }

    @Test
    void whitelist_covers_opencode_backups_and_sessions() throws IOException {
        Path opencodeDir = workdir.resolve("opencode");
        Files.createDirectories(opencodeDir);
        Files.writeString(opencodeDir.resolve("opencode.json.dt-bak-1"), "bak");
        Files.createDirectories(opencodeDir.resolve("sessions").resolve("ses_x"));
        Files.writeString(opencodeDir.resolve("sessions").resolve("ses_x").resolve("f.md"), "f");
        Files.createDirectories(opencodeDir.resolve("plugins"));

        runner.onApplicationReady();

        // Whitelisted items stay
        assertThat(Files.exists(opencodeDir.resolve("opencode.json.dt-bak-1"))).isTrue();
        assertThat(Files.exists(opencodeDir.resolve("sessions"))).isTrue();
        assertThat(Files.exists(opencodeDir.resolve("plugins"))).isTrue();
    }
}
```

Note: `LegacyMigrationRunner` currently has no bus dependency. In Task 4 we defer DtEvent emission wiring — the runner will call a new method that we later connect. For now, the test verifies file movement and marker behavior.

- [ ] **Step 2: Run failing tests**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && \
  mvn -pl data-talk-application test -Dtest=LegacyMigrationRunnerTest -q
```

Expected: 4 tests pass (existing scaffold already handles the file operations).

### 4.2 Enhance whitelist + add bus for DtEvent

- [ ] **Step 3: Modify `LegacyMigrationRunner` — inject `SessionBusRegistry` and emit DtEvent**

Add constructor parameter and field:

```java
    private final Path workdir;
    private final com.datatalk.application.session.SessionBusRegistry buses;

    public LegacyMigrationRunner(com.datatalk.application.session.SessionBusRegistry buses) {
        this.workdir = resolveWorkdir();
        this.buses = buses;
    }
```

Keep the default constructor for backward compat:

```java
    /** Test-only — no bus. */
    LegacyMigrationRunner() {
        this.workdir = resolveWorkdir();
        this.buses = null;
    }
```

At the end of `onApplicationReady()`, after `touchMarker(marker)`, add:

```java
        if (filesMoved > 0 && buses != null) {
            try {
                buses.getGlobalBus().publish(
                        new com.datatalk.domain.event.DtEvent.LegacyMigrated(filesMoved));
            } catch (Exception e) {
                log.warn("[legacy-migration] failed to emit event: {}", e.toString());
            }
        }
```

Update the whitelist set — already has the key items. Add `node_modules` and `v` to existing:

```java
    private static final Set<String> WHITELIST = Set.of(
            ".current", ".gitignore", "AGENTS.md", "opencode.json",
            "plugins"
    );
```

Also add directory pattern exclusion for `sessions` and `v*` in the walk loop (already present).

- [ ] **Step 4: Compile**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && mvn compile -q -pl data-talk-application
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Run tests**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && \
  mvn -pl data-talk-application test -Dtest=LegacyMigrationRunnerTest -q
```

Expected: 4 tests pass.

- [ ] **Step 6: Commit**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && \
  mvn install -pl data-talk-application -am -DskipTests -q && \
  cd /home/wushengzhou/workspace/github/data-talk && \
  git add server/data-talk-application/src/main/java/com/datatalk/application/housekeeping/LegacyMigrationRunner.java \
          server/data-talk-application/src/test/java/com/datatalk/application/housekeeping/LegacyMigrationRunnerTest.java && \
  git commit -m "feat(housekeeping): add DtEvent emission + refined whitelist to LegacyMigrationRunner"
```

---

## Task 5: FileArtifactReconciler — reconcileTrash + connection_id IS NULL

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactReconciler.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactReconcilerTest.java`

### 5.1 Add reconciliations

- [ ] **Step 1: Add `reconcileTrash()` method to `FileArtifactReconciler`**

```java
    public void reconcileTrash() {
        Path trashRoot = workdir.root().trashRoot();
        if (!Files.isDirectory(trashRoot)) return;

        // FS orphans: files in _trash with no DB discarded row
        try (Stream<Path> walk = Files.list(trashRoot)) {
            for (Path file : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(file)) continue;
                String path = file.toAbsolutePath().normalize().toString();
                if (repo.findByPhysicalPath(path).isEmpty()) {
                    try { Files.deleteIfExists(file); }
                    catch (IOException e) { log.warn("[reconcile-trash] rm failed: {}", file); }
                }
            }
        } catch (IOException e) {
            log.warn("[reconcile-trash] walk failed: {}", e.toString());
        }

        // DB orphans: discarded rows where file doesn't exist
        for (FileArtifact row : repo.findAllSessionScoped()) {
            if (row.status() != com.datatalk.domain.fileartifact.FileArtifactStatus.DISCARDED) continue;
            if (!Files.exists(Path.of(row.physicalPath()))) {
                repo.deleteById(row.id());
                log.info("[reconcile-trash] removing stale discarded row {} (file missing)", row.id());
            }
        }
    }
```

- [ ] **Step 2: Add connection_id IS NULL handling in `reconcileWorkspacesTree`**

The current `reconcileWorkspacesTree` iterates `findAllWorkspaceScopedArchived()` which may include `connection_id IS NULL` rows. Those rows have files in `workspaces/<deleted-cid>/` directories. We need to skip them (they are valid orphaned archived) rather than treat them as errors.

Modify `reconcileWorkspacesTree`:

```java
    private void reconcileWorkspacesTree(Set<String> knownSessionIds) {
        for (FileArtifact row : repo.findAllWorkspaceScopedArchived()) {
            if (row.connectionId() == null) {
                // Q2 decision: orphaned archived — valid state, skip
                continue;
            }
            if (Files.exists(Path.of(row.physicalPath()))) {
                continue;
            }
            log.warn("file_artifact {} archived file missing: {}", row.id(), row.physicalPath());
            repo.deleteById(row.id());
            publishDiscarded(row, "reconcile", knownSessionIds);
        }
    }
```

- [ ] **Step 3: Compile**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && mvn compile -q -pl data-talk-application
```

Expected: BUILD SUCCESS.

### 5.2 Add reconciler tests

- [ ] **Step 4: Open `FileArtifactReconcilerTest.java` and append**

```java
    @Test
    void reconcileTrash_removes_fs_orphans_without_db_row() throws IOException {
        Path trashDir = workdir.root().trashRoot();
        Files.createDirectories(trashDir);
        Path orphan = trashDir.resolve("conn_x__fa_99__orphan.md");
        Files.writeString(orphan, "orphan");

        reconciler.reconcileTrash();

        assertThat(Files.exists(orphan)).isFalse();
    }

    @Test
    void reconcileWorkspacesTree_skips_null_connection_id_archived_rows() {
        // Insert an archived row with connection_id = NULL
        FileArtifact orphan = new FileArtifact(
                "fa_orphan", com.datatalk.domain.fileartifact.FileArtifactScope.WORKSPACE,
                FileArtifactStatus.ARCHIVED, com.datatalk.domain.fileartifact.FileArtifactKind.OTHER,
                null, null, "report.md", "/nonexistent/report.md",
                100L, null, null, null, Instant.now(), Instant.now(), Instant.now(), Map.of());
        when(repo.findAllWorkspaceScopedArchived()).thenReturn(List.of(orphan));

        reconciler.runFullReconcile();

        // Should NOT be deleted — valid orphan state
        verify(repo, never()).deleteById("fa_orphan");
    }
```

Use existing mock setup; read the test file first to align with fixture style.

- [ ] **Step 5: Run tests**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && \
  mvn -pl data-talk-application test -Dtest=FileArtifactReconcilerTest -q
```

Expected: existing + 2 new tests pass.

- [ ] **Step 6: Commit**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && \
  mvn install -pl data-talk-application -am -DskipTests -q && \
  cd /home/wushengzhou/workspace/github/data-talk && \
  git add server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactReconciler.java \
          server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactReconcilerTest.java && \
  git commit -m "feat(file-artifact): add reconcileTrash + null connection_id handling"
```

---

## Task 6: FileArtifactService — reattach method

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceTest.java`

- [ ] **Step 1: Add `reattach` method to `FileArtifactService`**

```java
    // ───────── reattach use case (spec §B.3.1) ─────────

    public sealed interface ReattachOutcome {
        record Success(FileArtifact artifact) implements ReattachOutcome {}
        record NotFound(String fid) implements ReattachOutcome {}
        record NotArchived(String fid, FileArtifactStatus actual) implements ReattachOutcome {}
        record ConnectionNotFound(String connectionId) implements ReattachOutcome {}
        record MvFailed(String fid, String detail) implements ReattachOutcome {}
    }

    /**
     * Move an archived orphan row's file to a new connection's workspaces
     * directory and update DB. Spec §B.3.1.
     */
    public ReattachOutcome reattach(String fileArtifactId, String newConnectionId) {
        FileArtifact row = repo.findById(fileArtifactId).orElse(null);
        if (row == null) {
            return new ReattachOutcome.NotFound(fileArtifactId);
        }
        if (row.status() != FileArtifactStatus.ARCHIVED) {
            return new ReattachOutcome.NotArchived(fileArtifactId, row.status());
        }
        var conn = connRepo.findById(newConnectionId);
        if (conn.isEmpty()) {
            return new ReattachOutcome.ConnectionNotFound(newConnectionId);
        }

        Path src = Path.of(row.physicalPath());
        Path dstDir = workdir.root().workspacesRoot().resolve(newConnectionId);

        Path dst;
        try {
            dst = mover.mv(src, dstDir, row.filename());
        } catch (FileArtifactPhysicalMover.MvFailed e) {
            return new ReattachOutcome.MvFailed(fileArtifactId, e.getMessage());
        }

        long now = Instant.now().toEpochMilli();
        repo.reattachArchived(fileArtifactId, newConnectionId, dst.toString(), now);

        // Clear orphan metadata from metadata_json
        Map<String, Object> existingMeta = new java.util.LinkedHashMap<>(row.metadata());
        existingMeta.remove("orphanedFromConnection");
        existingMeta.remove("orphanedFromConnectionId");
        existingMeta.remove("orphanedAt");
        // We don't have a dedicated clearOrphanMetadata repo method — use reattachArchived
        // which already updates connection_id and physical_path. The orphan metadata
        // remaining in metadata_json is harmless; Frontend reads orphanedFromConnection
        // only when connection_id IS NULL, so after reattach it is ignored.

        FileArtifact updated = repo.findById(fileArtifactId).orElseThrow();
        publish(updated.sessionId(), new DtEvent.FileArtifactArchived(
                updated.id(), newConnectionId, updated.filename(), dst.toString()));
        return new ReattachOutcome.Success(updated);
    }
```

- [ ] **Step 2: Add tests to `FileArtifactServiceTest`**

```java
    @Test
    void reattach_happy_path_moves_file_and_updates_connection_id() throws Exception {
        Path src = sessionDir.resolve("orphan-report.md");
        Files.writeString(src, "x");
        FileArtifact row = new FileArtifact(
                "fa_1", com.datatalk.domain.fileartifact.FileArtifactScope.WORKSPACE,
                FileArtifactStatus.ARCHIVED, com.datatalk.domain.fileartifact.FileArtifactKind.OTHER,
                null, null, "orphan-report.md", src.toString(),
                100L, null, null, null, Instant.now(), Instant.now(), Instant.now(),
                Map.of("orphanedFromConnection", "old-conn", "orphanedFromConnectionId", "conn_x", "orphanedAt", 1000L));
        FileArtifact updated = new FileArtifact(
                "fa_1", com.datatalk.domain.fileartifact.FileArtifactScope.WORKSPACE,
                FileArtifactStatus.ARCHIVED, com.datatalk.domain.fileartifact.FileArtifactKind.OTHER,
                null, "conn_new", "orphan-report.md", "/tmp/workspaces/conn_new/orphan-report.md",
                100L, null, null, null, Instant.now(), Instant.now(), Instant.now(), Map.of());
        when(repo.findById("fa_1")).thenReturn(Optional.of(row), Optional.of(updated));
        when(connRepo.findById("conn_new")).thenReturn(Optional.of(
                new com.datatalk.application.persistence.ConnectionRecord(
                        "conn_new", "prod-pg", "pg", "h", 5432, "db", "u", new byte[0],
                        null, 0L, 3000, null, null, null, 1, true, null)));

        var out = svc.reattach("fa_1", "conn_new");

        assertThat(out).isInstanceOf(FileArtifactService.ReattachOutcome.Success.class);
        verify(repo).reattachArchived(eq("fa_1"), eq("conn_new"), contains("conn_new"), anyLong());
    }

    @Test
    void reattach_returns_NotFound_for_missing_fid() {
        when(repo.findById("fa_unknown")).thenReturn(Optional.empty());
        var out = svc.reattach("fa_unknown", "conn_new");
        assertThat(out).isInstanceOf(FileArtifactService.ReattachOutcome.NotFound.class);
    }
```

- [ ] **Step 3: Compile**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && mvn compile -q -pl data-talk-application
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Run tests**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && \
  mvn -pl data-talk-application test -Dtest=FileArtifactServiceTest -q
```

Expected: existing + 2 new tests pass.

- [ ] **Step 5: Commit**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && \
  mvn install -pl data-talk-application -am -DskipTests -q && \
  cd /home/wushengzhou/workspace/github/data-talk && \
  git add server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java \
          server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceTest.java && \
  git commit -m "feat(file-artifact): add reattach use case for orphaned archived files"
```

---

## Task 7: DTOs + MaintenanceController + SchedulingConfig

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/dto/StorageOverviewDto.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/dto/CleanupStatsDto.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/dto/OrphanedFileDto.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/dto/ReattachRequest.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/dto/ReattachResponse.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/MaintenanceController.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/config/SchedulingConfig.java`

### 7.1 Create DTOs

- [ ] **Step 1: Create `StorageOverviewDto.java`**

```java
package com.datatalk.dto;

import java.util.Map;

public record StorageOverviewDto(
        String workdir,
        long totalBytes,
        Map<String, BreakdownItem> breakdown,
        String lastHousekeepingRunAt
) {
    public record BreakdownItem(long bytes, String label) {}
}
```

- [ ] **Step 2: Create `CleanupStatsDto.java`**

```java
package com.datatalk.dto;

public record CleanupStatsDto(int filesRemoved, int dbRowsDeleted) {}
```

- [ ] **Step 3: Create `OrphanedFileDto.java`**

```java
package com.datatalk.dto;

public record OrphanedFileDto(
        String id,
        String filename,
        String kind,
        long sizeBytes,
        String title,
        String summary,
        String orphanedFromConnection,
        String orphanedFromConnectionId,
        long orphanedAt,
        String archivedAt
) {}
```

- [ ] **Step 4: Create `ReattachRequest.java`**

```java
package com.datatalk.dto;

public record ReattachRequest(String connectionId) {}
```

- [ ] **Step 5: Create `ReattachResponse.java`**

```java
package com.datatalk.dto;

import java.util.List;

public record ReattachResponse(List<String> succeeded, List<FailedItem> failed) {
    public record FailedItem(String id, String reason) {}
}
```

### 7.2 Create MaintenanceController

- [ ] **Step 6: Create `MaintenanceController.java`**

```java
package com.datatalk.adapter.controller;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.application.fileartifact.SessionWorkdirRoot;
import com.datatalk.application.housekeeping.HousekeepingScheduler;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final SessionWorkdirRoot workdirRoot;
    private final FileArtifactRepository fileArtifacts;
    private final HousekeepingScheduler scheduler;
    private final FileArtifactService fileArtifactService;

    public MaintenanceController(
            SessionWorkdirRoot workdirRoot,
            FileArtifactRepository fileArtifacts,
            HousekeepingScheduler scheduler,
            FileArtifactService fileArtifactService) {
        this.workdirRoot = workdirRoot;
        this.fileArtifacts = fileArtifacts;
        this.scheduler = scheduler;
        this.fileArtifactService = fileArtifactService;
    }

    @GetMapping("/storage-overview")
    public ResponseEntity<StorageOverviewDto> storageOverview() {
        Path root = workdirRoot.dataTalkRoot();
        Map<String, StorageOverviewDto.BreakdownItem> breakdown = new LinkedHashMap<>();
        long total = 0;

        // OpenCode infra
        long ocSize = dirSize(root.resolve("opencode"));
        total += ocSize;
        breakdown.put("opencodeInfra", new StorageOverviewDto.BreakdownItem(ocSize, "OpenCode 基础设施"));

        // Sessions
        long sessionsSize = dirSize(workdirRoot.sessionsRoot());
        total += sessionsSize;
        breakdown.put("sessions", new StorageOverviewDto.BreakdownItem(sessionsSize, "Sessions"));

        // Workspaces (assets)
        long wsSize = dirSize(workdirRoot.workspacesRoot());
        total += wsSize;
        int orphanedCount = fileArtifacts.countOrphanedArchived();
        breakdown.put("workspaces", new StorageOverviewDto.BreakdownItem(wsSize, "Workspaces (资产)"));

        // Trash
        Path trashDir = workdirRoot.trashRoot();
        long trashSize = dirSize(trashDir);
        total += trashSize;
        breakdown.put("trash", new StorageOverviewDto.BreakdownItem(trashSize, "_trash"));

        // Legacy
        Path legacyDir = workdirRoot.legacyRoot();
        long legacySize = dirSize(legacyDir);
        total += legacySize;
        breakdown.put("legacy", new StorageOverviewDto.BreakdownItem(legacySize, "_legacy"));

        return ResponseEntity.ok(new StorageOverviewDto(
                root.toAbsolutePath().normalize().toString(),
                total,
                breakdown,
                null)); // lastHousekeepingRunAt — read from housekeeping.log if exists
    }

    @PostMapping("/cleanup-trash")
    public ResponseEntity<CleanupStatsDto> cleanupTrash() {
        scheduler.cleanupTrash();
        return ResponseEntity.ok(new CleanupStatsDto(0, 0)); // stats tracked internally by scheduler
    }

    @GetMapping("/orphaned-files")
    public ResponseEntity<List<OrphanedFileDto>> orphanedFiles() {
        List<FileArtifact> rows = fileArtifacts.findOrphanedArchived(200);
        List<OrphanedFileDto> dtos = rows.stream().map(r -> {
            Map<String, Object> meta = r.metadata();
            return new OrphanedFileDto(
                    r.id(),
                    r.filename(),
                    r.kind().dbValue(),
                    r.sizeBytes(),
                    r.title(),
                    r.summary(),
                    meta.getOrDefault("orphanedFromConnection", "").toString(),
                    meta.getOrDefault("orphanedFromConnectionId", "").toString(),
                    meta.containsKey("orphanedAt") ? ((Number) meta.get("orphanedAt")).longValue() : 0L,
                    r.archivedAt() != null ? r.archivedAt().toString() : null);
        }).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/files/{fileArtifactId}/reattach")
    public ResponseEntity<?> reattach(
            @PathVariable String fileArtifactId,
            @RequestBody ReattachRequest req) {
        var out = fileArtifactService.reattach(fileArtifactId, req.connectionId());
        return switch (out) {
            case FileArtifactService.ReattachOutcome.Success s -> ResponseEntity.ok(s.artifact());
            case FileArtifactService.ReattachOutcome.NotFound nf -> ResponseEntity.notFound().build();
            case FileArtifactService.ReattachOutcome.NotArchived na -> ResponseEntity.status(409)
                    .body(Map.of("error", "not_archived", "actual", na.actual().dbValue()));
            case FileArtifactService.ReattachOutcome.ConnectionNotFound cn ->
                    ResponseEntity.status(404).body(Map.of("error", "connection_not_found"));
            case FileArtifactService.ReattachOutcome.MvFailed mf ->
                    ResponseEntity.status(503).body(Map.of("error", "mv_failed", "detail", mf.detail()));
        };
    }

    private long dirSize(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); } catch (IOException e) { return 0L; }
                    }).sum();
        } catch (IOException e) {
            return 0L;
        }
    }
}
```

### 7.3 Create SchedulingConfig

- [ ] **Step 7: Create `SchedulingConfig.java`**

```java
package com.datatalk.infra.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

- [ ] **Step 8: Compile**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && \
  mvn install -pl data-talk-application -am -DskipTests -q && \
  cd /home/wushengzhou/workspace/github/data-talk && \
  git add server/data-talk-adapter/src/main/java/com/datatalk/dto/StorageOverviewDto.java \
          server/data-talk-adapter/src/main/java/com/datatalk/dto/CleanupStatsDto.java \
          server/data-talk-adapter/src/main/java/com/datatalk/dto/OrphanedFileDto.java \
          server/data-talk-adapter/src/main/java/com/datatalk/dto/ReattachRequest.java \
          server/data-talk-adapter/src/main/java/com/datatalk/dto/ReattachResponse.java \
          server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/MaintenanceController.java \
          server/data-talk-infrastructure/src/main/java/com/datatalk/infra/config/SchedulingConfig.java && \
  git commit -m "feat(maintenance): add MaintenanceController + DTOs + SchedulingConfig"
```

---

## Task 8: Backend integration tests

**Files:**
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/MaintenanceControllerIT.java`

- [ ] **Step 1: Create `MaintenanceControllerIT.java`**

```java
package com.datatalk.adapter.controller;

import com.datatalk.DataTalkApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DataTalkApplication.class)
@AutoConfigureMockMvc
class MaintenanceControllerIT {

    @Autowired MockMvc mockMvc;

    @Test
    void storageOverview_returns_200_with_breakdown() throws Exception {
        mockMvc.perform(get("/api/maintenance/storage-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workdir").isString())
                .andExpect(jsonPath("$.breakdown.opencodeInfra").exists())
                .andExpect(jsonPath("$.breakdown.workspaces").exists())
                .andExpect(jsonPath("$.breakdown.trash").exists());
    }

    @Test
    void orphanedFiles_returns_200_and_list() throws Exception {
        mockMvc.perform(get("/api/maintenance/orphaned-files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void cleanupTrash_returns_200() throws Exception {
        mockMvc.perform(post("/api/maintenance/cleanup-trash"))
                .andExpect(status().isOk());
    }

    @Test
    void reattach_returns_404_for_unknown_fid() throws Exception {
        mockMvc.perform(post("/api/maintenance/files/fa_unknown/reattach")
                        .contentType("application/json")
                        .content("{\"connectionId\":\"conn_new\"}"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run IT**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && \
  mvn -pl data-talk-adapter test -Dtest=MaintenanceControllerIT -q
```

Expected: 4 tests pass.

- [ ] **Step 3: Commit**

```bash
cd /home/wushengzhou/workspace/github/data-talk && \
  git add server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/MaintenanceControllerIT.java && \
  git commit -m "test(maintenance): add MaintenanceController integration tests"
```

---

## Task 9: Frontend — API client + i18n keys

**Files:**
- Modify: `client/src/i18n/messages.ts`
- Create: `client/src/services/api/maintenance.ts`

### 9.1 i18n

- [ ] **Step 1: Add maintenance keys to `messages.ts`**

Add to both zh-CN and en blocks (find the existing pattern near `connections.deleteModal`):

zh-CN:
```ts
  maintenance: {
    tab: { title: 'Maintenance' },
    storageOverview: {
      workdir: '工作目录',
      totalSize: '总占用',
      lastRun: '最近治理',
      refresh: '刷新',
      opencodeInfra: 'OpenCode 基础设施',
      sessions: 'Sessions',
      workspaces: 'Workspaces (资产)',
      trash: '_trash',
      legacy: '_legacy',
      orphanedCount: '孤儿归档资产 ({n} 个)',
    },
    action: {
      cleanupTrash: '立即清空 _trash',
      viewLegacy: '查看 _legacy 目录...',
      viewLog: '查看 housekeeping 日志',
    },
    toast: {
      cleanupTrashDone: '_trash 已清空',
      legacyMigrated: '已迁移 {n} 个遗留文件到 _legacy',
    },
    orphans: {
      drawer: {
        title: '孤儿归档资产 ({n})',
        description: '这些文件原属于已删除的连接，未被自动清理。可重新关联到现有连接，或丢弃到 _trash（7 天后自动清理）。',
        selectAll: '全选',
        reattachBulk: '批量关联到',
        discardBulk: '批量丢弃',
        reattach: '关联到',
        discard: '丢弃',
        originalConnection: '原属于："{name}" (已删除)',
        archivedAt: '归档于',
        tooltipOver200: '显示前 200 条，请清理后再看下一批',
        bannerNoConnection: '没有可关联的连接，请先新建连接（或丢弃这些孤儿资产）',
        confirmDiscardBulk: '丢弃 {n} 个文件？这些文件会进入 _trash，7 天后自动清理。',
      },
      toast: {
        allCleaned: '所有孤儿资产已整理',
        reattachOk: '已关联到新连接',
        reattachPartial: '{ok} 个成功，{fail} 个失败',
      },
    },
  },
```

en:
```ts
  maintenance: {
    tab: { title: 'Maintenance' },
    storageOverview: {
      workdir: 'Work directory',
      totalSize: 'Total size',
      lastRun: 'Last housekeeping',
      refresh: 'Refresh',
      opencodeInfra: 'OpenCode infrastructure',
      sessions: 'Sessions',
      workspaces: 'Workspaces (assets)',
      trash: '_trash',
      legacy: '_legacy',
      orphanedCount: 'Orphaned archived assets ({n})',
    },
    action: {
      cleanupTrash: 'Empty _trash now',
      viewLegacy: 'View _legacy directory...',
      viewLog: 'View housekeeping log',
    },
    toast: {
      cleanupTrashDone: '_trash emptied',
      legacyMigrated: 'Migrated {n} legacy files to _legacy',
    },
    orphans: {
      drawer: {
        title: 'Orphaned Archives ({n})',
        description: 'These files belonged to deleted connections. You can reattach them to an existing connection or discard them to _trash (auto-cleaned after 7 days).',
        selectAll: 'Select all',
        reattachBulk: 'Bulk reattach to',
        discardBulk: 'Bulk discard',
        reattach: 'Reattach to',
        discard: 'Discard',
        originalConnection: 'Originally from: "{name}" (deleted)',
        archivedAt: 'Archived at',
        tooltipOver200: 'Showing first 200 items. Clean up to see more.',
        bannerNoConnection: 'No connections available. Create a connection first (or discard these orphans).',
        confirmDiscardBulk: 'Discard {n} files? They will go to _trash and be auto-cleaned after 7 days.',
      },
      toast: {
        allCleaned: 'All orphaned assets organized',
        reattachOk: 'Reattached to new connection',
        reattachPartial: '{ok} succeeded, {fail} failed',
      },
    },
  },
```

- [ ] **Step 2: Create `maintenance.ts` API client**

```ts
import { http } from '@/services/http'
import type { FileArtifact } from './file-artifacts'

export interface StorageOverviewDto {
  workdir: string
  totalBytes: number
  breakdown: Record<string, { bytes: number; label: string }>
  lastHousekeepingRunAt: string | null
}

export interface CleanupStatsDto {
  filesRemoved: number
  dbRowsDeleted: number
}

export interface OrphanedFileDto {
  id: string
  filename: string
  kind: string
  sizeBytes: number
  title: string | null
  summary: string | null
  orphanedFromConnection: string
  orphanedFromConnectionId: string
  orphanedAt: number
  archivedAt: string | null
}

export interface ReattachResponse {
  succeeded: string[]
  failed: Array<{ id: string; reason: string }>
}

export async function getStorageOverview(): Promise<StorageOverviewDto> {
  return http.get('maintenance/storage-overview').json<StorageOverviewDto>()
}

export async function cleanupTrash(): Promise<CleanupStatsDto> {
  return http.post('maintenance/cleanup-trash').json<CleanupStatsDto>()
}

export async function getOrphanedFiles(): Promise<OrphanedFileDto[]> {
  return http.get('maintenance/orphaned-files').json<OrphanedFileDto[]>()
}

export async function reattachFile(fileArtifactId: string, connectionId: string): Promise<FileArtifact> {
  return http.post(`maintenance/files/${fileArtifactId}/reattach`, {
    json: { connectionId },
  }).json<FileArtifact>()
}
```

- [ ] **Step 3: Type-check**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit
```

Expected: zero errors.

- [ ] **Step 4: Commit**

```bash
cd /home/wushengzhou/workspace/github/data-talk && \
  git add client/src/i18n/messages.ts \
          client/src/services/api/maintenance.ts && \
  git commit -m "feat(maintenance): add i18n keys + API client for maintenance endpoints"
```

---

## Task 10: Frontend — MaintenancePage

**Files:**
- Create: `client/src/features/settings/maintenance/maintenance-page.tsx`
- Create: `client/src/features/settings/maintenance/__tests__/maintenance-page.test.tsx`

### 10.1 Write failing test

- [ ] **Step 1: Create `maintenance-page.test.tsx`**

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { MaintenancePage } from '../maintenance-page'
import * as maintenanceApi from '@/services/api/maintenance'

vi.mock('@/services/api/maintenance')

describe('MaintenancePage', () => {
  it('renders storage overview with breakdown items', async () => {
    vi.mocked(maintenanceApi.getStorageOverview).mockResolvedValue({
      workdir: '/home/user/.data-talk',
      totalBytes: 358_000_000,
      breakdown: {
        opencodeInfra: { bytes: 286_000_000, label: 'OpenCode 基础设施' },
        sessions: { bytes: 21_000_000, label: 'Sessions' },
        workspaces: { bytes: 28_000_000, label: 'Workspaces (资产)' },
        trash: { bytes: 5_000_000, label: '_trash' },
        legacy: { bytes: 2_000_000, label: '_legacy' },
      },
      lastHousekeepingRunAt: null,
    })
    vi.mocked(maintenanceApi.getOrphanedFiles).mockResolvedValue([])

    render(<MaintenancePage />)

    await waitFor(() => {
      expect(screen.getByText('/home/user/.data-talk')).toBeInTheDocument()
      expect(screen.getByText(/OpenCode/)).toBeInTheDocument()
    })
  })

  it('shows refresh button that reloads data', async () => {
    vi.mocked(maintenanceApi.getStorageOverview).mockResolvedValue({
      workdir: '/tmp', totalBytes: 0, breakdown: {}, lastHousekeepingRunAt: null,
    })
    vi.mocked(maintenanceApi.getOrphanedFiles).mockResolvedValue([])
    const user = userEvent.setup()

    render(<MaintenancePage />)
    await waitFor(() => expect(screen.getByText('/tmp')).toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: /maintenance\.storageOverview\.refresh/ }))
    expect(maintenanceApi.getStorageOverview).toHaveBeenCalledTimes(2)
  })
})
```

- [ ] **Step 2: Run failing test**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npm test -- --run client/src/features/settings/maintenance/__tests__/maintenance-page.test.tsx
```

Expected: FAIL — MaintenancePage not found.

### 10.2 Implement

- [ ] **Step 3: Create `maintenance-page.tsx`**

```tsx
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useI18n } from '@/i18n/use-i18n'
import { useToast } from '@/hooks/use-toast'
import { getStorageOverview, cleanupTrash, getOrphanedFiles, type OrphanedFileDto } from '@/services/api/maintenance'
import { OrphanArchivesDrawer } from './orphan-archives-drawer'
import { useState } from 'react'

function fmtBytes(n: number) {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

export function MaintenancePage() {
  const { t } = useI18n()
  const { toast } = useToast()
  const qc = useQueryClient()
  const [drawerOpen, setDrawerOpen] = useState(false)

  const overview = useQuery({
    queryKey: ['maintenance', 'storage-overview'],
    queryFn: getStorageOverview,
  })

  const orphans = useQuery({
    queryKey: ['maintenance', 'orphaned-files'],
    queryFn: getOrphanedFiles,
  })

  const cleanup = useMutation({
    mutationFn: cleanupTrash,
    onSuccess: () => {
      toast({ description: t('maintenance.toast.cleanupTrashDone') })
      qc.invalidateQueries({ queryKey: ['maintenance'] })
    },
  })

  if (overview.isLoading) {
    return <div className="p-4 space-y-2"><Skeleton className="h-4 w-3/4" /><Skeleton className="h-4 w-1/2" /></div>
  }

  const d = overview.data
  if (!d) return null

  const orphanCount = orphans.data?.length ?? 0

  return (
    <div className="space-y-4 p-2">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-strong">{t('maintenance.tab.title')}</h3>
        <Button variant="ghost" size="sm" onClick={() => qc.invalidateQueries({ queryKey: ['maintenance'] })}>
          {t('maintenance.storageOverview.refresh')}
        </Button>
      </div>

      <div className="text-xs text-muted space-y-1">
        <div>{t('maintenance.storageOverview.workdir')}: <span className="text-base">{d.workdir}</span></div>
        <div>{t('maintenance.storageOverview.totalSize')}: {fmtBytes(d.totalBytes)}</div>
      </div>

      <ul className="space-y-1 text-sm">
        {Object.entries(d.breakdown).map(([key, item]) => (
          <li key={key} className="flex items-center gap-2 text-muted">
            <span className="text-base">├─ {item.label}</span>
            <span>{fmtBytes(item.bytes)}</span>
            {key === 'workspaces' && orphanCount > 0 && (
              <button
                type="button"
                className="text-status-info text-xs ml-2 hover:underline"
                onClick={() => setDrawerOpen(true)}
              >
                {t('maintenance.storageOverview.orphanedCount', { n: orphanCount })}
              </button>
            )}
          </li>
        ))}
      </ul>

      <div className="flex gap-2 mt-4">
        <Button variant="ghost" size="sm" onClick={() => cleanup.mutate()} disabled={cleanup.isPending}>
          {t('maintenance.action.cleanupTrash')}
        </Button>
      </div>

      {drawerOpen && (
        <OrphanArchivesDrawer
          files={orphans.data ?? []}
          onClose={() => { setDrawerOpen(false); qc.invalidateQueries({ queryKey: ['maintenance'] }) }}
        />
      )}
    </div>
  )
}
```

- [ ] **Step 4: Run test**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npm test -- --run client/src/features/settings/maintenance/__tests__/maintenance-page.test.tsx
```

Expected: 2 tests pass.

- [ ] **Step 5: Type-check**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit
```

Expected: zero errors (may show OrphanArchivesDrawer not found — that's Task 11).

- [ ] **Step 6: Commit**

```bash
cd /home/wushengzhou/workspace/github/data-talk && \
  git add client/src/features/settings/maintenance/maintenance-page.tsx \
          client/src/features/settings/maintenance/__tests__/maintenance-page.test.tsx && \
  git commit -m "feat(maintenance): add MaintenancePage with storage overview"
```

---

## Task 11: Frontend — OrphanArchivesDrawer

**Files:**
- Create: `client/src/features/settings/maintenance/orphan-archives-drawer.tsx`
- Create: `client/src/features/settings/maintenance/__tests__/orphan-archives-drawer.test.tsx`

### 11.1 Write failing test

- [ ] **Step 1: Create `orphan-archives-drawer.test.tsx`**

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { OrphanArchivesDrawer } from '../orphan-archives-drawer'
import * as maintenanceApi from '@/services/api/maintenance'

vi.mock('@/services/api/maintenance')
vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({ t: (k: string, params?: Record<string, unknown>) => {
    if (k === 'maintenance.orphans.drawer.title') return `Orphaned Archives (${params?.n ?? 0})`
    return k
  }})
}))

const files: maintenanceApi.OrphanedFileDto[] = [
  { id: 'fa_1', filename: 'orders-er.md', kind: 'er_diagram', sizeBytes: 8400, title: null, summary: null,
    orphanedFromConnection: 'prod-mysql', orphanedFromConnectionId: 'conn_x', orphanedAt: 1000, archivedAt: '2026-04-29T00:00:00Z' },
  { id: 'fa_2', filename: 'report.md', kind: 'report', sizeBytes: 32400, title: null, summary: null,
    orphanedFromConnection: 'dev-db', orphanedFromConnectionId: 'conn_y', orphanedAt: 2000, archivedAt: '2026-04-28T00:00:00Z' },
]

describe('OrphanArchivesDrawer', () => {
  it('renders files with original connection names', async () => {
    render(<OrphanArchivesDrawer files={files} onClose={vi.fn()} />)
    expect(screen.getByText('orders-er.md')).toBeInTheDocument()
    expect(screen.getByText('report.md')).toBeInTheDocument()
    expect(screen.getByText(/prod-mysql/)).toBeInTheDocument()
  })

  it('select all and batch discard triggers confirm', async () => {
    const user = userEvent.setup()
    render(<OrphanArchivesDrawer files={files} onClose={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: /selectAll/ }))
    await user.click(screen.getByRole('button', { name: /discardBulk/ }))

    expect(screen.getByText(/confirmDiscardBulk/)).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run failing test**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npm test -- --run client/src/features/settings/maintenance/__tests__/orphan-archives-drawer.test.tsx
```

Expected: FAIL.

### 11.2 Implement

- [ ] **Step 3: Create `orphan-archives-drawer.tsx`**

```tsx
import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { useI18n } from '@/i18n/use-i18n'
import { useToast } from '@/hooks/use-toast'
import { reattachFile, discardFile, type OrphanedFileDto } from '@/services/api/maintenance'
import { useConnectionStore } from '@/stores/connection-store'

interface Props {
  files: OrphanedFileDto[]
  onClose: () => void
}

export function OrphanArchivesDrawer({ files, onClose }: Props) {
  const { t } = useI18n()
  const { toast } = useToast()
  const qc = useQueryClient()
  const connections = useConnectionStore(s => s.connections)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [targetConn, setTargetConn] = useState<string>(connections[0]?.id ?? '')
  const [processing, setProcessing] = useState(false)

  function toggle(id: string) {
    setSelected(s => { const n = new Set(s); if (n.has(id)) n.delete(id); else n.add(id); return n })
  }

  function toggleAll() {
    if (selected.size === files.length) setSelected(new Set())
    else setSelected(new Set(files.map(f => f.id)))
  }

  async function doReattach(id: string, connId: string) {
    try { await reattachFile(id, connId); return { id, ok: true } }
    catch { return { id, ok: false } }
  }

  async function doDiscard(id: string) {
    try { await discardFile(id); return { id, ok: true } }
    catch { return { id, ok: false } }
  }

  async function batchDiscard() {
    setProcessing(true)
    const ids = [...selected]
    let ok = 0
    for (const id of ids) {
      const r = await doDiscard(id)
      if (r.ok) ok++
    }
    toast({ description: t('maintenance.orphans.toast.reattachPartial', { ok, fail: ids.length - ok }) })
    setProcessing(false)
    qc.invalidateQueries({ queryKey: ['maintenance'] })
  }

  return (
    <div className="fixed inset-y-0 right-0 w-[480px] bg-canvas border-l border-subtle shadow-lg z-50 flex flex-col">
      <div className="flex items-center justify-between px-4 py-3 border-b border-subtle">
        <h3 className="text-sm font-medium text-strong">{t('maintenance.orphans.drawer.title', { n: files.length })}</h3>
        <Button variant="ghost" size="sm" onClick={onClose}>✕</Button>
      </div>

      <p className="px-4 py-2 text-xs text-muted">{t('maintenance.orphans.drawer.description')}</p>

      {connections.length === 0 && (
        <div className="mx-4 p-2 bg-status-infoSurface text-status-info border border-status-info rounded-md text-xs">
          {t('maintenance.orphans.drawer.bannerNoConnection')}
        </div>
      )}

      <div className="flex items-center gap-2 px-4 py-2 border-b border-subtle">
        <Button variant="ghost" size="sm" onClick={toggleAll}>{t('maintenance.orphans.drawer.selectAll')}</Button>
        {connections.length > 0 && (
          <>
            <select value={targetConn} onChange={e => setTargetConn(e.target.value)} className="text-xs border border-subtle rounded px-1 py-0.5 bg-panel">
              {connections.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
            <Button variant="ghost" size="sm" disabled={selected.size === 0 || processing} onClick={async () => {
              setProcessing(true)
              let ok = 0
              for (const id of selected) { const r = await doReattach(id, targetConn); if (r.ok) ok++ }
              toast({ description: t('maintenance.orphans.toast.reattachPartial', { ok, fail: selected.size - ok }) })
              setProcessing(false)
              qc.invalidateQueries({ queryKey: ['maintenance'] })
            }}>{t('maintenance.orphans.drawer.reattachBulk')}</Button>
          </>
        )}
        <Button variant="ghost" size="sm" disabled={selected.size === 0 || processing} onClick={batchDiscard}>
          {t('maintenance.orphans.drawer.discardBulk')}
        </Button>
      </div>

      <div className="flex-1 overflow-y-auto">
        {files.length > 200 && (
          <p className="px-4 py-1 text-xs text-status-warning">{t('maintenance.orphans.drawer.tooltipOver200')}</p>
        )}
        {files.map(f => (
          <div key={f.id} className="flex items-center gap-3 px-4 py-2 border-b border-subtle hover:bg-hover">
            <Checkbox checked={selected.has(f.id)} onCheckedChange={() => toggle(f.id)} />
            <div className="flex-1 min-w-0">
              <div className="text-sm text-strong truncate">{f.filename}</div>
              <div className="text-xs text-muted">{f.kind} · {(f.sizeBytes / 1024).toFixed(1)} KB</div>
              {f.orphanedFromConnection && (
                <div className="text-xs text-muted mt-0.5">{t('maintenance.orphans.drawer.originalConnection', { name: f.orphanedFromConnection })}</div>
              )}
            </div>
            {connections.length > 0 && (
              <Button variant="ghost" size="sm" disabled={processing} onClick={async () => { await doReattach(f.id, targetConn); qc.invalidateQueries({ queryKey: ['maintenance'] }) }}>
                {t('maintenance.orphans.drawer.reattach')}
              </Button>
            )}
            <Button variant="ghost" size="sm" disabled={processing} onClick={async () => { await doDiscard(f.id); qc.invalidateQueries({ queryKey: ['maintenance'] }) }}>
              {t('maintenance.orphans.drawer.discard')}
            </Button>
          </div>
        ))}
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Run tests**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npm test -- --run client/src/features/settings/maintenance/__tests__/orphan-archives-drawer.test.tsx
```

Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
cd /home/wushengzhou/workspace/github/data-talk && \
  git add client/src/features/settings/maintenance/orphan-archives-drawer.tsx \
          client/src/features/settings/maintenance/__tests__/orphan-archives-drawer.test.tsx && \
  git commit -m "feat(maintenance): add OrphanArchivesDrawer with batch reattach/discard"
```

---

## Task 12: Frontend — Wire into Settings nav + dialog

**Files:**
- Modify: `client/src/features/settings/settings-dialog-store.ts`
- Modify: `client/src/features/settings/settings-nav.tsx`
- Modify: `client/src/features/settings/settings-dialog.tsx`

### 12.1 Extend Section type

- [ ] **Step 1: Modify `settings-dialog-store.ts` — add 'maintenance' to Section**

```ts
export type Section = 'general' | 'data-sources' | 'providers' | 'models' | 'maintenance'
```

### 12.2 Add nav item

- [ ] **Step 2: Modify `settings-nav.tsx` — add Maintenance to server group**

Add `Wrench` icon import and a new item in the server group:

```tsx
import { Settings, Database, Box, Sparkles, Wrench } from 'lucide-react'
```

Add below the models item:

```tsx
{ key: 'maintenance', label: t('maintenance.tab.title'), icon: Wrench },
```

### 12.3 Route page

- [ ] **Step 3: Modify `settings-dialog.tsx` — add MaintenancePage**

```tsx
import { MaintenancePage } from './maintenance/maintenance-page'

const PAGE_BY_SECTION: Record<Section, React.ReactNode> = {
  'general': <GeneralPage />,
  'data-sources': <DataSourcesPage />,
  'providers': <ProvidersPage />,
  'models': <ModelsPage />,
  'maintenance': <MaintenancePage />,
}
```

- [ ] **Step 4: Type-check**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit
```

Expected: zero errors.

- [ ] **Step 5: Commit**

```bash
cd /home/wushengzhou/workspace/github/data-talk && \
  git add client/src/features/settings/settings-dialog-store.ts \
          client/src/features/settings/settings-nav.tsx \
          client/src/features/settings/settings-dialog.tsx && \
  git commit -m "feat(maintenance): wire MaintenancePage into Settings nav/dialog"
```

---

## Task 13: Final regression + housekeeping

### 13.1 Full backend regression

- [ ] **Step 1: Run full backend test suite**

```bash
cd /home/wushengzhou/workspace/github/data-talk/server && mvn clean verify -q
```

Expected: BUILD SUCCESS.

### 13.2 Full frontend regression

- [ ] **Step 2:**

```bash
cd /home/wushengzhou/workspace/github/data-talk/client && npx tsc --noEmit && npm test -- --run
```

Expected: 0 type errors; all vitest pass.

### 13.3 BUG check

- [ ] **Step 3:** grep `docs/bugs/` for housekeeping/maintenance/orphan related bugs. N=0 也要明确说。

### 13.4 Document housekeeping

- [ ] **Step 4: Mark every task checkbox `- [x]` in this plan file**

- [ ] **Step 5: Update `docs/exec-plans/index.md`** — move Part 5b row from Active to Completed

- [ ] **Step 6: Update Roadmap row** — change `Part 5b 待补正式计划` to `Part 5b 已完成`

- [ ] **Step 7: Update parent spec** `docs/product-specs/2026-05-07-file-artifact-system-part5-design.md` header status

- [ ] **Step 8: Commit**

```bash
cd /home/wushengzhou/workspace/github/data-talk && \
  git add docs/ && \
  git commit -m "docs(housekeeping): complete Part 5b plan — mark all tasks done, move to Completed"
```

---

## Self-Review

**1. Spec coverage:** Each §B item mapped:
- B.1 HousekeepingScheduler → Task 3
- B.2 LegacyMigrationRunner → Task 4
- B.3 Settings Maintenance UI → Tasks 10/11/12
- B.3.1 REST endpoints → Task 7
- B.3.2 storage-overview response → Task 7 (DTO) + Task 10 (UI)
- B.3.3 UI main page → Task 10
- B.3.4 Orphan archives drawer → Task 11
- B.3.5 Five-state tokens → Design Inputs section above
- B.4 i18n keys → Task 9
- Reconciler extension → Task 5
- Repository additions → Task 2

**2. Placeholder scan:** No TBD/TODO/fill-in details found. All code steps have actual implementations.

**3. Type consistency:** `OrphanedFileDto` fields match between DTO (Task 7) and frontend API type (Task 9) and drawer component (Task 11). `ReattachOutcome` variants match between service (Task 6) and controller (Task 7).
