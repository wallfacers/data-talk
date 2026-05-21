# Dashboard ↔ File Artifact Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire `DashboardArtifactService` into the `file_artifact` system using the External-Registered Artifact pattern (设计 §3.1 / §3.5 选定方案 A) so dashboard 持久化复用 connection-detach、Files Library、reconciler、housekeeping 等已有 lifecycle 能力。

**Architecture:** Dashboard 物理文件继续落 `~/.data-talk/dashboards/<id>.dashboard.json`（不绑 connection 目录）；通过 `FileArtifactService.registerExternal` 在 `file_artifact` 表登记 `external=1` 行；`replaceBytesAtomic` 提供 ui_patch 高频原子覆盖写；`FileArtifactReconciler.reconcileExternalDirs` 双向清理外部目录孤儿。Migration 用 V18（V15-V17 已占用）。

**Tech Stack:** Spring Boot 3.5 / Java 21 / SQLite (Flyway) / JdbcTemplate / JUnit 5 + AssertJ / React 19 + TypeScript (vitest)

**关联文档:**
- 上游 spec: [docs/product-specs/2026-05-09-dashboard-file-artifact-integration-design.md](../product-specs/2026-05-09-dashboard-file-artifact-integration-design.md)
- 解锁: [docs/exec-plans/2026-05-08-report-dashboard-p1-plan.md](./2026-05-08-report-dashboard-p1-plan.md) Task B4

---

## Pre-flight: 现状校正（spec 与代码差异）

执行前请知悉以下事实——它们与上游 spec §1.1 / §5 描述存在差异，本计划按代码现状执行，**不**按 spec 旧措辞：

| Spec §1.1 / §5 措辞 | 实际代码现状 | 本计划处理 |
|---|---|---|
| `FileArtifactKind` 枚举仅 5 值，要"加 DASHBOARD" | FileArtifactKind.java:15 **已含 `DASHBOARD`** | 跳过枚举改动；只补 V18 migration 的 CHECK 约束 |
| Migration 编号 `V15__file_artifact_dashboard.sql` | V15/V16/V17 已被 oracle/sqlserver/duckdb 占用 | **改用 V18__file_artifact_dashboard.sql** |
| `DashboardArtifactService` / `DashboardStore` 不存在，需新建 | 两类**已存在**于 `application/dashboard/` 包 | 改造现有类，不重建 |
| DashboardStore 已被生产 wiring | `DashboardStore` 构造器要 `Path baseDir`，**没有 `@Bean Path` 提供方** → 生产环境实例化必然失败 | 在 `DashboardConfiguration` 新加 `@Bean Path dashboardsBaseDir` 并把 `DashboardStore` 的角色压缩为内部 atomic-write helper |
| `FileArtifact` record 已有 `external` 字段 | record **缺** `external` 字段 | A1 加字段 + 改 row mapper / insert SQL |
| Repository 已有 `findExternalRowsByDir` | **不存在** | A1 加该方法 |

---

## File Structure

```
server/data-talk-infrastructure/src/main/resources/db/migration/
  V18__file_artifact_dashboard.sql                          [CREATE]

server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/
  FileArtifact.java                                          [MODIFY]  +external boolean

server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/
  FileArtifactRepository.java                                [MODIFY]  +findExternalRowsByDir +findExternalRowsByDir signature
  FileArtifactService.java                                   [MODIFY]  +registerExternal +readBytes +replaceBytesAtomic
  FileArtifactReconciler.java                                [MODIFY]  +reconcileExternalDirs, runFullReconcile 末尾调用
  SessionWorkdirRoot.java                                    [MODIFY]  +dashboardsRoot +externalManagedRoots
  AtomicFileWriter.java                                      [CREATE]  package-private helper
  FileArtifactNotFoundException.java                         [CREATE]  unchecked, status 404
  FileArtifactConflictException.java                         [CREATE]  unchecked, status 409

server/data-talk-application/src/main/java/com/datatalk/application/dashboard/
  DashboardArtifactService.java                              [MODIFY]  改用 FileArtifactService.registerExternal/readBytes/replaceBytesAtomic
  DashboardStore.java                                        [MODIFY 或 DELETE]  压缩为内部 atomic 写 helper（或直接删并下沉到 AtomicFileWriter）
  DashboardConfiguration.java                                [CREATE]  @Bean Path dashboardsBaseDir + 显式 wiring

server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/
  JdbcFileArtifactRepository.java                            [MODIFY]  COLS 加 external + insert/mapper 同步; 新方法 findExternalRowsByDir

server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/
  FileArtifactServiceRegisterExternalTest.java               [CREATE]
  FileArtifactServiceReadBytesTest.java                      [CREATE]
  FileArtifactServiceReplaceBytesAtomicTest.java             [CREATE]
  FileArtifactReconcilerExternalDirsTest.java                [CREATE]

server/data-talk-application/src/test/java/com/datatalk/application/dashboard/
  DashboardArtifactServiceTest.java                          [MODIFY]  替换 DashboardStore 直接持久化为 FileArtifactService 集成

server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/
  DashboardControllerTest.java                               [MODIFY]  同上

server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/
  V18MigrationTest.java                                      [CREATE]  Flyway up + V14→V18 数据兼容

client/src/services/api/
  file-artifacts.ts                                          [MODIFY]  FileArtifactKind 联合类型加 'dashboard'

client/src/features/stage/stores/
  file-artifacts-store.ts                                    [MODIFY]  EMPTY_KIND_GROUPS + selectConnectionFiles 加 dashboard

docs/product-specs/
  2026-04-29-opencode-workdir-and-artifact-system-design.md  [MODIFY]  §6 加 status 语义校准注释

docs/exec-plans/
  2026-05-08-report-dashboard-p1-plan.md                     [MODIFY]  解除 B4 BLOCKED 状态
  index.md                                                   [MODIFY]  本 plan 登记 + 完成时迁移
```

---

## Design Inputs（仅前端 A3 涉及）

A3（前端）依据 [client/DESIGN.md](../../client/DESIGN.md) 约束：
- A3 仅修改类型定义与 store 内的 group 初始化，**不**新增视觉/布局/交互；后端字段映射的纯类型同步，无需新增组件设计
- 现有 Files Library 渲染分组靠 `selectConnectionFiles` 返回的 `ConnectionFileGroups`；新增 `dashboard: []` 槽位后，UI 是否要为 dashboard kind 加专属图标 / 双击行为 → **不在本 plan 范围**（属于后续 dashboard UI 集成 plan）

---

## Risks & Known Issues

按 BUG Tracking Gate 已 grep `docs/bugs/` 关键字 `dashboard` / `file_artifact` / `reconcile` / `migration`：未发现登记的 open BUG 与本 plan 范围冲突。本 plan 引入的下列风险需在执行中关注：

1. **V18 migration 不可在生产已有 dashboard 行的环境回滚**——回滚需要先备份 `~/.data-talk/dashboards/*.dashboard.json` 并删除所有 `external=1` 行；helper 见 Task A1.S6
2. **`status==ARCHIVED ⇒ read-only`** 的隐含假设要重新校准（spec §6）；Task A2.S10 显式审计现有调用点
3. **DashboardStore 现状**：构造器要 `Path baseDir` 但生产无 `@Bean` 提供——意味着改造前 DashboardArtifactService 在生产里实际无法启动；本计划同步修复 wiring（Task A2.S9）
4. **TOCTOU**：`registerExternal` 内 `Files.size(absolutePath)` 与调用方写盘之间的时间窗口；缓解方案见 spec §A2.S1 注释
5. **Flyway DDL 事务**：SQLite DDL 默认在事务内，但 `DROP TABLE + RENAME` 序列若 Flyway 中途崩溃理论上可能留下 `file_artifact_new`；migration 自身无法完全规避，依赖 Flyway 重启时检测——Task A1.S2 注释明确

---

## Task A1: V18 Migration + Repository / Domain 字段扩展

**Files:**
- Create: `server/data-talk-infrastructure/src/main/resources/db/migration/V18__file_artifact_dashboard.sql`
- Create: `server/data-talk-infrastructure/src/main/resources/db/migration/V18.1__migrate_rollback_helper.sql`（备份脚本，注释形态，不参与自动迁移；详 S6）
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/FileArtifact.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactRepository.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepository.java`
- Create test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/V18MigrationTest.java`
- Create test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepositoryExternalTest.java`

**依赖:** 无。其它 A2/A3 任务依赖本 task 完成。

### A1.S1 — 写 V18 migration 失败测试

- [x] **Step 1: 写 V18MigrationTest（先写失败测试）**

```java
// server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/V18MigrationTest.java
package com.datatalk.infra.fileartifact;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class V18MigrationTest {

    @Test
    void v18_adds_dashboard_kind_and_external_column(@TempDir Path tmp) throws Exception {
        String url = "jdbc:sqlite:" + tmp.resolve("dt.db");
        Flyway flyway = Flyway.configure()
                .dataSource(url, null, null)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {

            // V18 应允许 kind='dashboard' 与 external=1
            s.executeUpdate(
                "INSERT INTO file_artifact " +
                "(id,scope,status,kind,filename,physical_path,size_bytes,created_at,updated_at,external) " +
                "VALUES ('d1','workspace','archived','dashboard','x.json','/abs/x.json',10,1,1,1)"
            );

            // V14 已有的 5 个 kind 仍然合法
            s.executeUpdate(
                "INSERT INTO file_artifact " +
                "(id,scope,status,kind,filename,physical_path,size_bytes,created_at,updated_at,external) " +
                "VALUES ('r1','session','temporary','report','r.md','/abs/r.md',5,1,1,0)"
            );

            // external 列存在且默认 0
            try (ResultSet rs = s.executeQuery("SELECT external FROM file_artifact WHERE id='r1'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(0);
            }

            // CHECK 约束生效：external 只能 0/1
            try {
                s.executeUpdate(
                    "INSERT INTO file_artifact " +
                    "(id,scope,status,kind,filename,physical_path,size_bytes,created_at,updated_at,external) " +
                    "VALUES ('bad','session','temporary','report','b.md','/abs/b.md',1,1,1,2)"
                );
                throw new AssertionError("CHECK constraint on external should reject value 2");
            } catch (java.sql.SQLException expected) {
                assertThat(expected.getMessage()).containsIgnoringCase("CHECK");
            }
        }
    }
}
```

- [x] **Step 2: 跑测试确认失败**

Run: `cd server && mvn -pl data-talk-infrastructure -am test -Dtest=V18MigrationTest -q`
Expected: FAIL — `V18__file_artifact_dashboard.sql` 不存在 / `external` 列不存在

### A1.S2 — 写 V18 migration SQL

- [x] **Step 3: 创建 V18__file_artifact_dashboard.sql**

```sql
-- server/data-talk-infrastructure/src/main/resources/db/migration/V18__file_artifact_dashboard.sql
-- Spec: 2026-05-09-dashboard-file-artifact-integration-design §5.1
--
-- SQLite CHECK 约束不能 ALTER；整表 rebuild 把 'dashboard' 加入 kind 白名单 + 新增 external 列。
-- Flyway 将本脚本作为单个 migration 在事务中执行；SQLite DDL 在事务内的崩溃语义由 Flyway 重启检测兜底。

CREATE TABLE file_artifact_new (
  id            TEXT PRIMARY KEY,
  scope         TEXT NOT NULL CHECK(scope IN ('session','workspace')),
  status        TEXT NOT NULL CHECK(status IN ('temporary','candidate','archived','discarded')),
  kind          TEXT NOT NULL CHECK(kind IN ('report','er_diagram','sql_script','dataset','dashboard','other')),
  session_id    TEXT,
  connection_id TEXT,
  filename      TEXT NOT NULL,
  physical_path TEXT NOT NULL,
  size_bytes    INTEGER NOT NULL,
  mime_type     TEXT,
  title         TEXT,
  summary       TEXT,
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL,
  archived_at   INTEGER,
  metadata_json TEXT,
  external      INTEGER NOT NULL DEFAULT 0 CHECK(external IN (0, 1))
);

INSERT INTO file_artifact_new
  (id, scope, status, kind, session_id, connection_id, filename, physical_path,
   size_bytes, mime_type, title, summary, created_at, updated_at, archived_at,
   metadata_json, external)
SELECT
   id, scope, status, kind, session_id, connection_id, filename, physical_path,
   size_bytes, mime_type, title, summary, created_at, updated_at, archived_at,
   metadata_json, 0
FROM file_artifact;

DROP TABLE file_artifact;
ALTER TABLE file_artifact_new RENAME TO file_artifact;

-- 重建索引（保留 V14 三个 + 新加 external 部分索引）
CREATE INDEX idx_file_artifact_session    ON file_artifact(session_id)    WHERE scope = 'session';
CREATE INDEX idx_file_artifact_connection ON file_artifact(connection_id) WHERE scope = 'workspace';
CREATE INDEX idx_file_artifact_status     ON file_artifact(status);
CREATE INDEX idx_file_artifact_external   ON file_artifact(external)      WHERE external = 1;
```

- [x] **Step 4: 跑 V18MigrationTest 确认通过**

Run: `cd server && mvn -pl data-talk-infrastructure -am test -Dtest=V18MigrationTest -q`
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add server/data-talk-infrastructure/src/main/resources/db/migration/V18__file_artifact_dashboard.sql \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/V18MigrationTest.java
git commit -m "feat(file-artifact): V18 migration adds dashboard kind + external column"
```

### A1.S6 — 写 rollback helper（注释脚本）

- [x] **Step 6: 创建 V18.1 helper（仅作为文档/手工执行用，文件名 `migrate-rollback-helper.sql` 放 docs/）**

不放入 `db/migration/` 下避免被 Flyway 当作 migration 自动执行。改放：

```sql
-- docs/migrate-rollback-helper-V18.sql
-- 回滚 V18 前手工执行：列出 dashboard 行并要求人工确认数据已备份。
-- 使用：
--   sqlite3 ~/.data-talk/datatalk.db < docs/migrate-rollback-helper-V18.sql
--   然后人工 cp -r ~/.data-talk/dashboards/ ~/.data-talk/_rollback_$(date +%s)/
--   再删除 file_artifact 中 kind='dashboard' OR external=1 的行
--   最后才能允许 down migration

SELECT id, filename, physical_path, created_at
FROM file_artifact
WHERE kind = 'dashboard' OR external = 1;
```

```bash
mkdir -p docs && touch docs/migrate-rollback-helper-V18.sql
# 写入上述内容
git add docs/migrate-rollback-helper-V18.sql
git commit -m "docs(file-artifact): V18 rollback helper sql"
```

### A1.S3 — `FileArtifact` record 加 `external` 字段

- [x] **Step 7: 修改 FileArtifact.java，加 external 字段**

```java
// server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/FileArtifact.java
package com.datatalk.domain.fileartifact;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

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
        Map<String, Object> metadata,
        boolean external
) {
    public Optional<String> sessionIdOpt() { return Optional.ofNullable(sessionId); }
    public Optional<String> connectionIdOpt() { return Optional.ofNullable(connectionId); }
    public Optional<String> mimeTypeOpt() { return Optional.ofNullable(mimeType); }
    public Optional<String> titleOpt() { return Optional.ofNullable(title); }
    public Optional<String> summaryOpt() { return Optional.ofNullable(summary); }
    public Optional<Instant> archivedAtOpt() { return Optional.ofNullable(archivedAt); }
}
```

- [x] **Step 8: 跑全量编译确认所有现有 `new FileArtifact(...)` 调用点报错**

Run: `cd server && mvn compile -q`
Expected: 编译报错，提示 record 构造器参数不匹配。逐个修复：搜索 `new FileArtifact(` 给所有调用点末尾追加 `, false`（除 dashboard 路径外，所有现有路径都是 managed 行 → external=false）。

```bash
grep -rn "new FileArtifact(" server/ --include="*.java"
```

逐个改：在最末参数 `metadata` 后加 `, false`。涉及位置（已 grep 得）：
- `FileArtifactService.java` 的 `recordDetected`、`archiveCandidate` 两处
- 测试代码中所有 `new FileArtifact(...)` 用例

- [x] **Step 9: 再次编译确认通过**

Run: `cd server && mvn compile -q`
Expected: BUILD SUCCESS

### A1.S4 — Repository 接口扩展 + JDBC 实现同步

- [x] **Step 10: 修改 FileArtifactRepository.java 加新方法签名**

在文件末尾、`record ConnectionResourceCounts(...)` 前插入：

```java
    /**
     * 列出 external=1 行中 physical_path 位于 dirAbsolute（绝对路径）下的所有行。
     * 用于 reconcileExternalDirs 的反向孤儿（DB 有 × 盘没）扫描。
     * Match 规则：物理路径 LIKE dirAbsolute + '/%'；调用方负责传入归一化后的绝对路径。
     */
    List<FileArtifact> findExternalRowsByDir(String dirAbsolute);
```

- [x] **Step 11: 写 JdbcFileArtifactRepositoryExternalTest 失败测试**

```java
// server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepositoryExternalTest.java
package com.datatalk.infra.fileartifact;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcFileArtifactRepositoryExternalTest {

    private FileArtifactRepository repo;

    @BeforeEach
    void setup(@TempDir Path tmp) {
        String url = "jdbc:sqlite:" + tmp.resolve("dt.db");
        Flyway.configure().dataSource(url, null, null)
              .locations("classpath:db/migration").load().migrate();
        var ds = new DriverManagerDataSource(url);
        repo = new JdbcFileArtifactRepository(new JdbcTemplate(ds), new ObjectMapper());
    }

    @Test
    void insert_and_round_trip_external_row() {
        FileArtifact row = newExternalRow("d1", "/data/dashboards/d1.dashboard.json");
        repo.insert(row);
        FileArtifact loaded = repo.findById("d1").orElseThrow();
        assertThat(loaded.external()).isTrue();
        assertThat(loaded.physicalPath()).isEqualTo("/data/dashboards/d1.dashboard.json");
    }

    @Test
    void findExternalRowsByDir_returns_only_external_rows_under_dir() {
        repo.insert(newExternalRow("d1", "/data/dashboards/d1.dashboard.json"));
        repo.insert(newExternalRow("d2", "/data/dashboards/d2.dashboard.json"));
        repo.insert(newExternalRow("e1", "/elsewhere/e1.dashboard.json"));
        // managed (external=false) 行也不应被返回
        repo.insert(newManagedRow("m1", "/data/dashboards/m1.txt"));

        List<FileArtifact> rows = repo.findExternalRowsByDir("/data/dashboards");
        assertThat(rows).extracting(FileArtifact::id)
                        .containsExactlyInAnyOrder("d1", "d2");
    }

    private static FileArtifact newExternalRow(String id, String path) {
        Instant now = Instant.now();
        return new FileArtifact(
                id, FileArtifactScope.WORKSPACE, FileArtifactStatus.ARCHIVED,
                FileArtifactKind.DASHBOARD, null, null,
                Path.of(path).getFileName().toString(), path,
                10L, "application/json", null, null,
                now, now, now, Map.of(), true);
    }

    private static FileArtifact newManagedRow(String id, String path) {
        Instant now = Instant.now();
        return new FileArtifact(
                id, FileArtifactScope.WORKSPACE, FileArtifactStatus.ARCHIVED,
                FileArtifactKind.OTHER, null, "conn1",
                Path.of(path).getFileName().toString(), path,
                10L, null, null, null,
                now, now, now, Map.of(), false);
    }
}
```

- [x] **Step 12: 跑测试确认失败**

Run: `cd server && mvn -pl data-talk-infrastructure -am test -Dtest=JdbcFileArtifactRepositoryExternalTest -q`
Expected: FAIL — `external` 列没接到 INSERT / mapper

- [x] **Step 13: 修改 JdbcFileArtifactRepository.java**

a) `COLS` 常量加 `external`：

```java
private static final String COLS = """
        id, scope, status, kind, session_id, connection_id, filename, physical_path,
        size_bytes, mime_type, title, summary, created_at, updated_at, archived_at, metadata_json, external
        """;
```

b) `insert` 方法加一列 + 一个占位符：

```java
@Override
public void insert(FileArtifact artifact) {
    jdbc.update(
            "INSERT INTO file_artifact (" + COLS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            artifact.id(),
            artifact.scope().dbValue(),
            artifact.status().dbValue(),
            artifact.kind().dbValue(),
            artifact.sessionId(),
            artifact.connectionId(),
            artifact.filename(),
            artifact.physicalPath(),
            artifact.sizeBytes(),
            artifact.mimeType(),
            artifact.title(),
            artifact.summary(),
            artifact.createdAt().toEpochMilli(),
            artifact.updatedAt().toEpochMilli(),
            instantToMillis(artifact.archivedAt()),
            writeMetadata(artifact.metadata()),
            artifact.external() ? 1 : 0);
}
```

c) `mapper()` 末尾追加 `external` 解析：

```java
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
            instantOrNull(rs, "archived_at"),
            readMetadata(rs.getString("metadata_json")),
            rs.getInt("external") == 1);
}
```

d) 在文件末尾追加 `findExternalRowsByDir`：

```java
@Override
public List<FileArtifact> findExternalRowsByDir(String dirAbsolute) {
    String prefix = dirAbsolute.endsWith("/") ? dirAbsolute + "%" : dirAbsolute + "/%";
    return jdbc.query(
            "SELECT " + COLS + " FROM file_artifact WHERE external = 1 AND physical_path LIKE ?",
            mapper(),
            prefix);
}
```

- [x] **Step 14: 跑测试确认通过**

Run: `cd server && mvn -pl data-talk-infrastructure -am test -Dtest=JdbcFileArtifactRepositoryExternalTest -q`
Expected: PASS

- [x] **Step 15: 全量编译 + 跑现有 file_artifact 相关测试确认无回归**

Run: `cd server && mvn -pl data-talk-infrastructure -am test -Dtest='*FileArtifact*' -q`
Expected: PASS（所有现有 `new FileArtifact(...)` 改造后仍通过）

- [x] **Step 16: Commit**

```bash
git add server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/FileArtifact.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactRepository.java \
        server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepository.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepositoryExternalTest.java
# 加上前面修改的所有 new FileArtifact(...) 调用点
git add -u server/
git commit -m "feat(file-artifact): add external boolean to record + repo round-trip"
```

---

## Task A2: FileArtifactService 三 API + Reconciler 旁路 + Status 语义校准

**依赖:** A1（FileArtifact.external + V18 migration + repo.findExternalRowsByDir 必须先在）

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/AtomicFileWriter.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactNotFoundException.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactConflictException.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/SessionWorkdirRoot.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactReconciler.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardArtifactService.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardConfiguration.java`
- Modify or Delete: `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardStore.java`
- Create tests: 4 个新测试文件
- Modify tests: `DashboardArtifactServiceTest.java` / `DashboardControllerTest.java`

### A2.S1 — `AtomicFileWriter` + 异常类

- [x] **Step 1: 创建 AtomicFileWriter（package-private helper）**

```java
// server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/AtomicFileWriter.java
package com.datatalk.application.fileartifact;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * 原子写盘：写到 target.tmp → fsync 内容 → ATOMIC_MOVE rename → fsync 父目录。
 * package-private，仅 file_artifact / dashboard 内部复用。
 */
final class AtomicFileWriter {

    private AtomicFileWriter() {}

    static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (FileChannel ch = FileChannel.open(tmp,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ch.write(java.nio.ByteBuffer.wrap(bytes));
                ch.force(true);
            }
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            // best-effort 父目录 fsync（POSIX；Windows 上忽略）
            try (FileChannel dirCh = FileChannel.open(target.getParent(), StandardOpenOption.READ)) {
                dirCh.force(true);
            } catch (IOException ignored) { /* 非 POSIX 平台 */ }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
```

- [x] **Step 2: 创建两个 unchecked 异常类**

```java
// server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactNotFoundException.java
package com.datatalk.application.fileartifact;

public class FileArtifactNotFoundException extends RuntimeException {
    private final String fileArtifactId;
    public FileArtifactNotFoundException(String fileArtifactId, String detail) {
        super("file_artifact not found: " + fileArtifactId + " (" + detail + ")");
        this.fileArtifactId = fileArtifactId;
    }
    public String fileArtifactId() { return fileArtifactId; }
}
```

```java
// server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactConflictException.java
package com.datatalk.application.fileartifact;

public class FileArtifactConflictException extends RuntimeException {
    public FileArtifactConflictException(String message) { super(message); }
    public FileArtifactConflictException(String message, Throwable cause) { super(message, cause); }
}
```

### A2.S2 — `SessionWorkdirRoot` 加 `dashboardsRoot()` / `externalManagedRoots()`

- [x] **Step 3: 修改 SessionWorkdirRoot.java**

```java
// server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/SessionWorkdirRoot.java
package com.datatalk.application.fileartifact;

import java.nio.file.Path;
import java.util.List;

public record SessionWorkdirRoot(Path dataTalkRoot, Path opencodeCwd) {

    public Path sessionsRoot() { return opencodeCwd.resolve("sessions"); }
    public Path sessionDir(String sessionId) { return sessionsRoot().resolve(sessionId); }
    public Path workspacesRoot() { return dataTalkRoot.resolve("workspaces"); }
    public Path workspaceDir(String connectionId) { return workspacesRoot().resolve(connectionId); }
    public Path trashRoot() { return dataTalkRoot.resolve("_trash"); }
    public Path legacyRoot() { return dataTalkRoot.resolve("_legacy"); }

    /**
     * dashboard 物理目录：~/.data-talk/dashboards/。
     * Spec §5.4：FileArtifactService 仅做 DB 登记，不主动写盘；写盘由 DashboardArtifactService 自管。
     */
    public Path dashboardsRoot() { return dataTalkRoot.resolve("dashboards"); }

    /**
     * 所有"由 caller 自管物理写盘、FileArtifactService 仅登记 DB 行"的目录。
     * 用于 FileArtifactReconciler.reconcileExternalDirs 的孤儿清理。
     */
    public List<Path> externalManagedRoots() { return List.of(dashboardsRoot()); }
}
```

### A2.S3 — `FileArtifactService.registerExternal` (TDD)

- [x] **Step 4: 写 FileArtifactServiceRegisterExternalTest 失败测试**

```java
// server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceRegisterExternalTest.java
package com.datatalk.application.fileartifact;

import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileArtifactServiceRegisterExternalTest {

    @Test
    void registerExternal_inserts_row_with_external_true_and_reads_size_from_disk(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("d1.dashboard.json");
        Files.writeString(file, "{\"a\":1}");

        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("d1")).thenReturn(Optional.empty());
        FileArtifactService svc = newServiceWith(repo);

        FileArtifact result = svc.registerExternal(
                "d1", FileArtifactKind.DASHBOARD, FileArtifactScope.WORKSPACE,
                /* connectionId */ null, /* sessionId */ null, file,
                "Title", "Sum", Map.of("k","v"));

        ArgumentCaptor<FileArtifact> cap = ArgumentCaptor.forClass(FileArtifact.class);
        verify(repo).insert(cap.capture());
        FileArtifact inserted = cap.getValue();
        assertThat(inserted.id()).isEqualTo("d1");
        assertThat(inserted.external()).isTrue();
        assertThat(inserted.scope()).isEqualTo(FileArtifactScope.WORKSPACE);
        assertThat(inserted.status()).isEqualTo(FileArtifactStatus.ARCHIVED);
        assertThat(inserted.kind()).isEqualTo(FileArtifactKind.DASHBOARD);
        assertThat(inserted.physicalPath()).isEqualTo(file.toAbsolutePath().normalize().toString());
        assertThat(inserted.sizeBytes()).isEqualTo(Files.size(file));
        assertThat(inserted.title()).isEqualTo("Title");
        assertThat(inserted.metadata()).containsEntry("k", "v");
        assertThat(result).isEqualTo(inserted);
    }

    @Test
    void registerExternal_throws_if_file_missing(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.json");
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        FileArtifactService svc = newServiceWith(repo);
        assertThatThrownBy(() -> svc.registerExternal(
                "x", FileArtifactKind.DASHBOARD, FileArtifactScope.WORKSPACE,
                null, null, missing, null, null, Map.of()))
            .isInstanceOf(IOException.class);
        verify(repo, never()).insert(any());
    }

    @Test
    void registerExternal_throws_conflict_if_id_exists(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("d2.json");
        Files.writeString(file, "{}");
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("d2")).thenReturn(Optional.of(mock(FileArtifact.class)));
        FileArtifactService svc = newServiceWith(repo);
        assertThatThrownBy(() -> svc.registerExternal(
                "d2", FileArtifactKind.DASHBOARD, FileArtifactScope.WORKSPACE,
                null, null, file, null, null, Map.of()))
            .isInstanceOf(FileArtifactConflictException.class);
        verify(repo, never()).insert(any());
    }

    private FileArtifactService newServiceWith(FileArtifactRepository repo) {
        // 其它依赖注入用 mock：本 svc 方法只用 repo
        return new FileArtifactService(
                repo,
                mock(SessionWorkdirService.class),
                mock(com.datatalk.application.session.SessionBusRegistry.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(FileArtifactPhysicalMover.class),
                mock(com.datatalk.application.persistence.SessionRepository.class),
                mock(com.datatalk.application.persistence.ConnectionRepository.class));
    }
}
```

- [x] **Step 5: 跑测试确认失败**

Run: `cd server && mvn -pl data-talk-application -am test -Dtest=FileArtifactServiceRegisterExternalTest -q`
Expected: FAIL — `registerExternal` 不存在

- [x] **Step 6: 在 FileArtifactService.java 末尾追加 `registerExternal`**

```java
    /**
     * 登记外部物理文件到 file_artifact 表。物理文件由 caller 自管（已写盘），
     * 本方法不写盘，仅 INSERT DB 行；INSERT 前 Files.size(absolutePath) 自读 size。
     *
     * Spec: 2026-05-09-dashboard-file-artifact-integration-design §5.3
     *
     * @throws IOException 文件不存在或不可读
     * @throws FileArtifactConflictException id 已存在
     */
    public FileArtifact registerExternal(
            String id,
            FileArtifactKind kind,
            FileArtifactScope scope,
            String connectionId,
            String sessionId,
            Path absolutePath,
            String title,
            String summary,
            Map<String, Object> metadata) throws IOException {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(absolutePath, "absolutePath");
        if (!absolutePath.isAbsolute()) {
            throw new IllegalArgumentException("absolutePath must be absolute: " + absolutePath);
        }
        if (repo.findById(id).isPresent()) {
            throw new FileArtifactConflictException("file_artifact id already exists: " + id);
        }
        long size = Files.size(absolutePath); // throws IOException if missing
        Path normalized = absolutePath.toAbsolutePath().normalize();
        Instant now = Instant.now();
        FileArtifact row = new FileArtifact(
                id, scope, FileArtifactStatus.ARCHIVED, kind,
                sessionId, connectionId,
                normalized.getFileName().toString(), normalized.toString(),
                size, guessMime(normalized), title, summary,
                now, now, now,
                metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata),
                /* external */ true);
        repo.insert(row);
        return row;
    }
```

- [x] **Step 7: 跑测试确认通过**

Run: `cd server && mvn -pl data-talk-application -am test -Dtest=FileArtifactServiceRegisterExternalTest -q`
Expected: PASS

### A2.S4 — `FileArtifactService.readBytes` (TDD)

- [x] **Step 8: 写 FileArtifactServiceReadBytesTest**

```java
// server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceReadBytesTest.java
package com.datatalk.application.fileartifact;

import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FileArtifactServiceReadBytesTest {

    @Test
    void readBytes_external_row_reads_absolute_path(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("d.json");
        Files.writeString(file, "{\"x\":1}");
        FileArtifact row = newRow("d1", file.toString(), /* external */ true);
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("d1")).thenReturn(Optional.of(row));
        FileArtifactService svc = newServiceWith(repo);

        byte[] bytes = svc.readBytes("d1");
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("{\"x\":1}");
    }

    @Test
    void readBytes_throws_when_id_unknown() {
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("none")).thenReturn(Optional.empty());
        FileArtifactService svc = newServiceWith(repo);
        assertThatThrownBy(() -> svc.readBytes("none"))
            .isInstanceOf(FileArtifactNotFoundException.class);
    }

    @Test
    void readBytes_throws_when_external_file_missing(@TempDir Path tmp) {
        Path file = tmp.resolve("ghost.json"); // 不创建
        FileArtifact row = newRow("d2", file.toString(), true);
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("d2")).thenReturn(Optional.of(row));
        FileArtifactService svc = newServiceWith(repo);
        assertThatThrownBy(() -> svc.readBytes("d2"))
            .isInstanceOf(FileArtifactNotFoundException.class);
    }

    private FileArtifact newRow(String id, String path, boolean external) {
        Instant now = Instant.now();
        return new FileArtifact(
                id, FileArtifactScope.WORKSPACE, FileArtifactStatus.ARCHIVED,
                FileArtifactKind.DASHBOARD, null, null,
                Path.of(path).getFileName().toString(), path,
                10L, "application/json", null, null,
                now, now, now, Map.of(), external);
    }

    private FileArtifactService newServiceWith(FileArtifactRepository repo) {
        return new FileArtifactService(
                repo,
                mock(SessionWorkdirService.class),
                mock(com.datatalk.application.session.SessionBusRegistry.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(FileArtifactPhysicalMover.class),
                mock(com.datatalk.application.persistence.SessionRepository.class),
                mock(com.datatalk.application.persistence.ConnectionRepository.class));
    }
}
```

- [x] **Step 9: 跑测试确认失败**

Run: `cd server && mvn -pl data-talk-application -am test -Dtest=FileArtifactServiceReadBytesTest -q`
Expected: FAIL

- [x] **Step 10: 在 FileArtifactService.java 追加 `readBytes`**

```java
    /**
     * 通用读字节。external 行直接按绝对路径读；managed 行的处理留待未来需要时扩展
     * （当前 P1 不需要 managed 行的 readBytes 路径）。
     *
     * @throws FileArtifactNotFoundException artifact id 不存在 / external 物理文件不存在
     */
    public byte[] readBytes(String fileArtifactId) {
        FileArtifact row = repo.findById(fileArtifactId)
                .orElseThrow(() -> new FileArtifactNotFoundException(fileArtifactId, "row missing"));
        Path file = row.external() ? Path.of(row.physicalPath())
                                   : workdir.root().dataTalkRoot().resolve(row.physicalPath());
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new FileArtifactNotFoundException(fileArtifactId, "physical file missing: " + file);
        }
    }
```

- [x] **Step 11: 跑测试确认通过**

Run: `cd server && mvn -pl data-talk-application -am test -Dtest=FileArtifactServiceReadBytesTest -q`
Expected: PASS

### A2.S5 — `FileArtifactService.replaceBytesAtomic` (TDD)

- [x] **Step 12: 写 FileArtifactServiceReplaceBytesAtomicTest**

```java
// server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceReplaceBytesAtomicTest.java
package com.datatalk.application.fileartifact;

import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FileArtifactServiceReplaceBytesAtomicTest {

    @Test
    void replaceBytesAtomic_external_writes_file_and_updates_size(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("nested").resolve("d.json");
        FileArtifact row = newExternal("d1", target.toString());
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("d1")).thenReturn(Optional.of(row));
        FileArtifactService svc = newServiceWith(repo);

        byte[] bytes = "{\"a\":2}".getBytes(StandardCharsets.UTF_8);
        FileArtifact updated = svc.replaceBytesAtomic("d1", bytes);

        assertThat(Files.readAllBytes(target)).isEqualTo(bytes);
        verify(repo).updateMetadata(eq("d1"), eq((long) bytes.length), anyLong());
        assertThat(updated.id()).isEqualTo("d1");
    }

    @Test
    void replaceBytesAtomic_throws_when_row_missing() {
        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findById("nope")).thenReturn(Optional.empty());
        FileArtifactService svc = newServiceWith(repo);
        assertThatThrownBy(() -> svc.replaceBytesAtomic("nope", new byte[]{1}))
            .isInstanceOf(FileArtifactNotFoundException.class);
    }

    private FileArtifact newExternal(String id, String path) {
        Instant now = Instant.now();
        return new FileArtifact(
                id, FileArtifactScope.WORKSPACE, FileArtifactStatus.ARCHIVED,
                FileArtifactKind.DASHBOARD, null, null,
                Path.of(path).getFileName().toString(), path,
                0L, "application/json", null, null,
                now, now, now, Map.of(), true);
    }

    private FileArtifactService newServiceWith(FileArtifactRepository repo) {
        return new FileArtifactService(
                repo,
                mock(SessionWorkdirService.class),
                mock(com.datatalk.application.session.SessionBusRegistry.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(FileArtifactPhysicalMover.class),
                mock(com.datatalk.application.persistence.SessionRepository.class),
                mock(com.datatalk.application.persistence.ConnectionRepository.class));
    }
}
```

- [x] **Step 13: 跑测试确认失败**

Run: `cd server && mvn -pl data-talk-application -am test -Dtest=FileArtifactServiceReplaceBytesAtomicTest -q`
Expected: FAIL

- [x] **Step 14: 在 FileArtifactService.java 追加 `replaceBytesAtomic`**

```java
    /**
     * 原子覆盖写。
     * external 行：AtomicFileWriter（temp + fsync + rename + fsync parent）→ updateMetadata 行 size_bytes/updated_at。
     * managed 行：本 P1 不实现（dashboard 是当前唯一消费者）；未来需要时再扩。
     *
     * <p>Caller 责任：体积上限校验（如 dashboard 256 KB）。本方法不做 size guard。
     *
     * @throws FileArtifactNotFoundException artifact id 不存在
     * @throws IllegalStateException managed 行（暂不支持）
     */
    public FileArtifact replaceBytesAtomic(String fileArtifactId, byte[] bytes) throws IOException {
        FileArtifact row = repo.findById(fileArtifactId)
                .orElseThrow(() -> new FileArtifactNotFoundException(fileArtifactId, "row missing"));
        if (!row.external()) {
            throw new IllegalStateException("replaceBytesAtomic on managed row not supported (id=" + fileArtifactId + ")");
        }
        Path target = Path.of(row.physicalPath());
        AtomicFileWriter.writeAtomically(target, bytes);
        long now = System.currentTimeMillis();
        repo.updateMetadata(fileArtifactId, bytes.length, now);
        return repo.findById(fileArtifactId).orElseThrow(
                () -> new IllegalStateException("row vanished after replaceBytesAtomic: " + fileArtifactId));
    }
```

- [x] **Step 15: 跑测试确认通过**

Run: `cd server && mvn -pl data-talk-application -am test -Dtest=FileArtifactServiceReplaceBytesAtomicTest -q`
Expected: PASS

### A2.S6 — `FileArtifactReconciler.reconcileExternalDirs` (TDD)

- [x] **Step 16: 写 FileArtifactReconcilerExternalDirsTest**

```java
// server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactReconcilerExternalDirsTest.java
package com.datatalk.application.fileartifact;

import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FileArtifactReconcilerExternalDirsTest {

    @Test
    void reconcileExternalDirs_deletes_files_with_no_db_row(@TempDir Path tmp) throws IOException {
        Path orphan = Files.writeString(tmp.resolve("orphan.dashboard.json"), "{}");
        Path tracked = Files.writeString(tmp.resolve("tracked.dashboard.json"), "{}");

        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findExternalRowsByDir(tmp.toString())).thenReturn(List.of(
                externalRow("t1", tracked.toString())
        ));
        // 仅 tracked 的物理路径在 DB 有对应行
        when(repo.findByPhysicalPath(tracked.toString())).thenReturn(java.util.Optional.of(externalRow("t1", tracked.toString())));
        when(repo.findByPhysicalPath(orphan.toString())).thenReturn(java.util.Optional.empty());

        FileArtifactReconciler r = newReconciler(repo, tmp);
        r.reconcileExternalDirs(List.of(tmp));

        assertThat(Files.exists(orphan)).isFalse(); // 盘有 × DB 无 → 删盘
        assertThat(Files.exists(tracked)).isTrue();
        verify(repo, never()).deleteById("t1"); // tracked 行不应被删
    }

    @Test
    void reconcileExternalDirs_deletes_db_rows_with_missing_physical(@TempDir Path tmp) {
        Path missing = tmp.resolve("ghost.dashboard.json"); // 不创建文件
        FileArtifact row = externalRow("g1", missing.toString());

        FileArtifactRepository repo = mock(FileArtifactRepository.class);
        when(repo.findExternalRowsByDir(tmp.toString())).thenReturn(List.of(row));
        SessionBusRegistry buses = mock(SessionBusRegistry.class);

        FileArtifactReconciler r = newReconciler(repo, buses, tmp);
        r.reconcileExternalDirs(List.of(tmp));

        verify(repo).deleteById("g1");
    }

    private FileArtifact externalRow(String id, String path) {
        Instant now = Instant.now();
        return new FileArtifact(
                id, FileArtifactScope.WORKSPACE, FileArtifactStatus.ARCHIVED,
                FileArtifactKind.DASHBOARD, null, null,
                Path.of(path).getFileName().toString(), path,
                10L, null, null, null, now, now, now, Map.of(), true);
    }

    private FileArtifactReconciler newReconciler(FileArtifactRepository repo, Path tmp) {
        return newReconciler(repo, mock(SessionBusRegistry.class), tmp);
    }

    private FileArtifactReconciler newReconciler(FileArtifactRepository repo, SessionBusRegistry buses, Path tmp) {
        SessionWorkdirService workdir = mock(SessionWorkdirService.class);
        when(workdir.root()).thenReturn(new SessionWorkdirRoot(tmp.getParent(), tmp.getParent().resolve("opencode")));
        return new FileArtifactReconciler(
                repo,
                mock(FileArtifactService.class),
                workdir,
                buses,
                mock(SessionRepository.class));
    }
}
```

- [x] **Step 17: 跑测试确认失败**

Run: `cd server && mvn -pl data-talk-application -am test -Dtest=FileArtifactReconcilerExternalDirsTest -q`
Expected: FAIL — `reconcileExternalDirs` 不存在

- [x] **Step 18: 在 FileArtifactReconciler.java 末尾追加 `reconcileExternalDirs` + 在 `runFullReconcile` 末尾调用**

```java
// 修改 runFullReconcile
public synchronized void runFullReconcile() {
    Set<String> knownSessionIds = knownSessionIds();
    reconcileSessionsTree(knownSessionIds);
    reconcileWorkspacesTree(knownSessionIds);
    reconcileExternalDirs(workdir.root().externalManagedRoots());
}

/**
 * 双向清理外部目录（caller 自管物理写盘的目录）孤儿。
 * 正向：盘有 × DB 无 → 删盘文件
 * 反向：DB 有（external=1）× 盘没 → deleteById + publishDiscarded
 *
 * 不做 adopt（dashboard 元数据 id/version/timestamps 必须由 promote 流程生成）。
 *
 * Spec: 2026-05-09-dashboard-file-artifact-integration-design §5.4
 */
public void reconcileExternalDirs(List<Path> dirs) {
    Set<String> knownSessionIds = knownSessionIds();
    for (Path dir : dirs) {
        if (!Files.isDirectory(dir)) continue;

        // 正向：盘上文件无对应 DB 行 → 删盘
        try (Stream<Path> walk = Files.list(dir)) {
            for (Path file : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(file)) continue;
                String fname = file.getFileName().toString();
                if (fname.startsWith(".") || fname.endsWith(".tmp")) continue;
                String absolutePath = file.toAbsolutePath().normalize().toString();
                if (repo.findByPhysicalPath(absolutePath).isEmpty()) {
                    try {
                        Files.deleteIfExists(file);
                        log.info("[reconcile-external] removed orphan file {}", absolutePath);
                    } catch (IOException e) {
                        log.warn("[reconcile-external] rm failed: {}", file, e);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[reconcile-external] walk failed for {}: {}", dir, e.toString());
        }

        // 反向：DB 有 external 行 × 盘没 → 删行
        for (FileArtifact row : repo.findExternalRowsByDir(dir.toAbsolutePath().normalize().toString())) {
            if (Files.exists(Path.of(row.physicalPath()))) continue;
            log.warn("[reconcile-external] external row {} missing physical {}", row.id(), row.physicalPath());
            repo.deleteById(row.id());
            publishDiscarded(row, "reconcile-external", knownSessionIds);
        }
    }
}
```

- [x] **Step 19: 修改 `reconcileWorkspacesTree` 加 external 旁路**

在 `reconcileWorkspacesTree` 的 for 循环内、`row.connectionId() == null` 后加：

```java
if (row.external()) continue; // external 行由 reconcileExternalDirs 处理
```

- [x] **Step 20: 跑测试确认通过**

Run: `cd server && mvn -pl data-talk-application -am test -Dtest=FileArtifactReconcilerExternalDirsTest -q`
Expected: PASS

### A2.S7 — `DashboardConfiguration` Bean wiring + `DashboardStore` 角色压缩

- [x] **Step 21: 创建 DashboardConfiguration**

```java
// server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardConfiguration.java
package com.datatalk.application.dashboard;

import com.datatalk.application.fileartifact.SessionWorkdirRoot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class DashboardConfiguration {

    @Bean
    public Path dashboardsBaseDir(SessionWorkdirRoot root) throws IOException {
        Path dir = root.dashboardsRoot();
        Files.createDirectories(dir);
        return dir;
    }
}
```

> 注：`DashboardStore` 类签名是 `DashboardStore(Path baseDir, ObjectMapper mapper)`。Spring 会用上面的 `Path dashboardsBaseDir` Bean + 已有的 `ObjectMapper` Bean 自动装配 `@Component` 标注的 `DashboardStore`。

- [x] **Step 22: 修改 DashboardArtifactService — 改用 FileArtifactService API**

替换 `DashboardArtifactService.java` 全文（保留异常类、Locks、DashboardIds 用法）：

```java
package com.datatalk.application.dashboard;

import com.datatalk.application.fileartifact.FileArtifactConflictException;
import com.datatalk.application.fileartifact.FileArtifactNotFoundException;
import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.application.fileartifact.SessionWorkdirRoot;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DashboardArtifactService {

    private static final int MAX_PAYLOAD_BYTES = 256 * 1024; // 256 KB

    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    private final FileArtifactService fileArtifactService;
    private final SessionWorkdirRoot workdirRoot;
    private final DashboardSchemaValidator validator;
    private final JsonPatchApplier patchApplier;
    private final ObjectMapper mapper;
    private final Clock clock;

    public DashboardArtifactService(
            FileArtifactService fileArtifactService,
            SessionWorkdirRoot workdirRoot,
            DashboardSchemaValidator validator,
            JsonPatchApplier patchApplier,
            ObjectMapper mapper,
            Clock clock) {
        this.fileArtifactService = fileArtifactService;
        this.workdirRoot = workdirRoot;
        this.validator = validator;
        this.patchApplier = patchApplier;
        this.mapper = mapper;
        this.clock = clock;
    }

    public record PromoteResult(String id, int version) {}
    public record PatchResult(int version) {}

    public PromoteResult promote(JsonNode dashboard) {
        return promote(dashboard, /* originSessionId */ null);
    }

    public PromoteResult promote(JsonNode dashboard, String originSessionId) {
        // 1. 预校验 size（此时还未写盘）
        int approxSize = dashboard.toString().length();
        if (approxSize > MAX_PAYLOAD_BYTES) {
            throw new PayloadTooLargeException(approxSize, MAX_PAYLOAD_BYTES);
        }
        ValidationResult validation = validator.validate(dashboard);
        if (!validation.ok()) throw new ValidationException(validation);

        String id = DashboardIds.newDashboardId();
        long now = clock.millis();
        ObjectNode mutable = dashboard.deepCopy();
        mutable.put("id", id);
        mutable.put("version", 1);
        mutable.put("createdAt", now);
        mutable.put("updatedAt", now);

        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(mutable);
        } catch (IOException e) {
            throw new DashboardPersistenceException("serialize failed: " + id, e);
        }
        if (bytes.length > MAX_PAYLOAD_BYTES) {
            throw new PayloadTooLargeException(bytes.length, MAX_PAYLOAD_BYTES);
        }

        Path target = workdirRoot.dashboardsRoot().resolve(id + ".dashboard.json");
        try {
            com.datatalk.application.fileartifact.AtomicFileWriterBridge.write(target, bytes);
        } catch (IOException e) {
            throw new DashboardPersistenceException("atomic write failed: " + id, e);
        }

        try {
            String connectionId = mutable.path("defaultConnectionId").asText(null);
            String title = mutable.path("title").asText(null);
            Map<String, Object> meta = originSessionId == null ? Map.of()
                                                                : Map.of("originSessionId", originSessionId);
            fileArtifactService.registerExternal(
                    id, FileArtifactKind.DASHBOARD, FileArtifactScope.WORKSPACE,
                    /* connectionId */ connectionId,
                    /* sessionId */ null,
                    target, title, /* summary */ null, meta);
        } catch (IOException | FileArtifactConflictException e) {
            // 登记失败：清理已写盘文件，避免孤儿（reconciler 兜底但主动清更干净）
            try { Files.deleteIfExists(target); } catch (IOException ignored) {}
            throw new DashboardPersistenceException("registerExternal failed: " + id, e);
        }

        return new PromoteResult(id, 1);
    }

    public JsonNode load(String id) {
        try {
            byte[] bytes = fileArtifactService.readBytes(id);
            return mapper.readTree(bytes);
        } catch (FileArtifactNotFoundException e) {
            throw new DashboardNotFoundException(id);
        } catch (IOException e) {
            throw new DashboardPersistenceException("load failed: " + id, e);
        }
    }

    public PatchResult patch(String id, int baseVersion, List<JsonPatchApplier.PatchOp> ops) {
        Object lock = locks.computeIfAbsent(id, k -> new Object());
        synchronized (lock) {
            return doPatch(id, baseVersion, ops);
        }
    }

    private PatchResult doPatch(String id, int baseVersion, List<JsonPatchApplier.PatchOp> ops) {
        JsonNode current = load(id);
        JsonNode patched = patchApplier.apply(current, baseVersion, ops);

        ValidationResult validation = validator.validate(patched);
        if (!validation.ok()) throw new ValidationException(validation);

        ObjectNode mutable = (ObjectNode) patched;
        mutable.put("updatedAt", clock.millis());

        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(mutable);
        } catch (IOException e) {
            throw new DashboardPersistenceException("serialize failed: " + id, e);
        }
        if (bytes.length > MAX_PAYLOAD_BYTES) {
            throw new PayloadTooLargeException(bytes.length, MAX_PAYLOAD_BYTES);
        }
        try {
            fileArtifactService.replaceBytesAtomic(id, bytes);
        } catch (IOException e) {
            throw new DashboardPersistenceException("replaceBytesAtomic failed: " + id, e);
        } catch (FileArtifactNotFoundException e) {
            throw new DashboardNotFoundException(id);
        }
        return new PatchResult(mutable.get("version").asInt());
    }

    public static final class PayloadTooLargeException extends RuntimeException {
        private final int size;
        private final int maxSize;
        public PayloadTooLargeException(int size, int maxSize) {
            super("Dashboard payload too large: " + size + " > " + maxSize);
            this.size = size; this.maxSize = maxSize;
        }
        public int getSize() { return size; }
        public int getMaxSize() { return maxSize; }
    }

    public static final class ValidationException extends RuntimeException {
        private final ValidationResult result;
        public ValidationException(ValidationResult result) {
            super("Dashboard validation failed: " + result.errors());
            this.result = result;
        }
        public ValidationResult getResult() { return result; }
    }

    public static final class DashboardNotFoundException extends RuntimeException {
        public DashboardNotFoundException(String id) { super("Dashboard not found: " + id); }
    }

    public static final class DashboardPersistenceException extends RuntimeException {
        public DashboardPersistenceException(String message, Throwable cause) { super(message, cause); }
    }
}
```

- [x] **Step 23: 把 `AtomicFileWriter` 暴露到 dashboard 包**

`AtomicFileWriter` 当前是 package-private。需要让 dashboard 包能调用，最小改动：在 `fileartifact` 包内加一个 public bridge：

```java
// server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/AtomicFileWriterBridge.java
package com.datatalk.application.fileartifact;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Public bridge for callers outside the fileartifact package that need to
 * perform an atomic write into a directory whose lifecycle they own (currently
 * only {@code dashboard} promote → ~/.data-talk/dashboards).
 */
public final class AtomicFileWriterBridge {
    private AtomicFileWriterBridge() {}
    public static void write(Path target, byte[] bytes) throws IOException {
        AtomicFileWriter.writeAtomically(target, bytes);
    }
}
```

- [x] **Step 24: 删除 `DashboardStore`（不再需要）+ 移除 `@Component` Bean**

新 `DashboardArtifactService` 不再注入 `DashboardStore`。删除 `DashboardStore.java`：

```bash
git rm server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardStore.java
```

> 影响范围：测试文件 `DashboardArtifactServiceTest.java` 与 `DashboardControllerTest.java` 中 `new DashboardStore(...)` 构造调用需在 A2.S8 改造为 mock `FileArtifactService` + 真实 `SessionWorkdirRoot(tempDir, ...)`。

### A2.S8 — 改造已有 dashboard 测试

- [x] **Step 25: 修改 DashboardArtifactServiceTest.java**

把所有 `DashboardStore store = new DashboardStore(tempDir, mapper);` 替换为：

```java
// 假设 tempDir 是 @TempDir Path
SessionWorkdirRoot root = new SessionWorkdirRoot(tempDir, tempDir.resolve("opencode"));
java.nio.file.Files.createDirectories(root.dashboardsRoot());

// 用一个 in-memory FakeFileArtifactRepository 或 mock + ArgumentCaptor
FakeFileArtifactRepository repo = new FakeFileArtifactRepository();
FileArtifactService fileArtifactService = new FileArtifactService(
        repo,
        Mockito.mock(SessionWorkdirService.class),
        Mockito.mock(SessionBusRegistry.class),
        mapper,
        Mockito.mock(FileArtifactPhysicalMover.class),
        Mockito.mock(SessionRepository.class),
        Mockito.mock(ConnectionRepository.class));

DashboardArtifactService service = new DashboardArtifactService(
        fileArtifactService, root, validator, patchApplier, mapper, clock);
```

`FakeFileArtifactRepository` 是测试同包内的 in-memory 实现（用 `Map<String, FileArtifact>`），实现 `FileArtifactRepository` 接口。

> 给本 task 的执行者：在 `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/` 下新增一个 `FakeFileArtifactRepository.java` 测试 helper（用 `HashMap<String, FileArtifact>` 实现接口；不需要实现非测试方法时抛 `UnsupportedOperationException`）。

- [x] **Step 26: 修改 DashboardControllerTest.java（同样替换 store 构造）**

同上模式。

- [x] **Step 27: 跑 dashboard 包全部测试确认 PASS**

Run: `cd server && mvn -pl data-talk-application -am test -Dtest='Dashboard*Test' -q`
Expected: PASS

Run: `cd server && mvn -pl data-talk-adapter -am test -Dtest='Dashboard*Test' -q`
Expected: PASS

### A2.S9 — Status 语义校准代码审计

- [x] **Step 28: grep 现有 ARCHIVED 假设的调用点**

```bash
grep -rn "FileArtifactStatus.ARCHIVED\|status = 'archived'\|status='archived'\|findAllWorkspaceScopedArchived\|findArchivedByConnection" \
    server/ --include="*.java"
```

- [x] **Step 29: 对每处审计：是否假设 archived = read-only / 不变？**

预期需要审计的调用点（按现有代码）：
- `JdbcFileArtifactRepository.findArchivedByConnection` / `detachArchivedFromConnection` / `markArchived` — 不假设内容不变（仅按 connection 分组），✓ 无需改动
- `FileArtifactReconciler.reconcileWorkspacesTree` — 已在 A2.S6.Step19 加 external 旁路，✓
- `FileArtifactService.archive` / `discard` / `reattach` — managed 行流程，dashboard external 行不会走这些路径，✓

**预期结果：仅需 reconcileWorkspacesTree 加 external 旁路**（已在 Step 19 完成）；其它调用点均不假设"archived = 不变"。

如果 grep 发现新调用点假设 archived 不变（例如未来加的缓存策略），按 spec §6 要求显式排除 dashboard kind 或 external 行。

- [x] **Step 30: 在 spec 与文档落实校准**

修改 `docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md` §6 file_artifact 系统总体描述，追加一行注释：

```markdown
> **状态语义校准（2026-05-09）**：`status = 'archived'` 不再等价 `frozen / read-only`；其精确含义是"已固化到 workspace 命名空间"。dashboard kind 通过 `FileArtifactService.replaceBytesAtomic` 高频改写 archived 行内容。详见 [docs/product-specs/2026-05-09-dashboard-file-artifact-integration-design.md](./2026-05-09-dashboard-file-artifact-integration-design.md) §6。
```

### A2.S10 — 全量编译 + 集成测试

- [x] **Step 31: 全量后端测试**

Run: `cd server && mvn clean verify -q`
Expected: BUILD SUCCESS, 0 failures

- [x] **Step 32: Commit A2 成果**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/AtomicFileWriter.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/AtomicFileWriterBridge.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactNotFoundException.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactConflictException.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/SessionWorkdirRoot.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactReconciler.java \
        server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardArtifactService.java \
        server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardConfiguration.java \
        server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/ \
        server/data-talk-application/src/test/java/com/datatalk/application/dashboard/ \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/DashboardControllerTest.java \
        docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md
git rm server/data-talk-application/src/main/java/com/datatalk/application/dashboard/DashboardStore.java
git commit -m "feat(file-artifact): registerExternal/readBytes/replaceBytesAtomic + dashboard wiring"
```

---

## Task A3: 前端 TypeScript 类型 + EMPTY_KIND_GROUPS 同步

**依赖:** A1 完成（DB 列与后端 dbValue 已含 `dashboard`）即可，A2 不阻塞 A3 编译。

**Files:**
- Modify: `client/src/services/api/file-artifacts.ts`
- Modify: `client/src/features/stage/stores/file-artifacts-store.ts`

### A3.S1 — 联合类型加 'dashboard'

- [x] **Step 1: 修改 client/src/services/api/file-artifacts.ts**

```typescript
export type FileArtifactKind = 'report' | 'er_diagram' | 'sql_script' | 'dataset' | 'dashboard' | 'other'
```

- [x] **Step 2: 修改 client/src/features/stage/stores/file-artifacts-store.ts**

`EMPTY_KIND_GROUPS` 加 `dashboard: []`：

```typescript
const EMPTY_KIND_GROUPS: ConnectionFileGroups = {
  report: [],
  er_diagram: [],
  sql_script: [],
  dataset: [],
  dashboard: [],
  other: [],
}
```

`selectConnectionFiles` 内部的 `groups` 字面量同步：

```typescript
const groups: ConnectionFileGroups = {
  report: [],
  er_diagram: [],
  sql_script: [],
  dataset: [],
  dashboard: [],
  other: [],
}
```

### A3.S2 — 类型检查 + 已有 vitest 回归

- [x] **Step 3: 跑 tsc 确认无类型错误**

Run: `cd client && npx tsc --noEmit`
Expected: 0 errors

- [x] **Step 4: 跑前端 vitest 确认无回归**

Run: `cd client && npx vitest run --reporter=verbose 2>&1 | tail -30`
Expected: 所有测试通过

- [x] **Step 5: Commit A3 成果**

```bash
git add client/src/services/api/file-artifacts.ts \
        client/src/features/stage/stores/file-artifacts-store.ts
git commit -m "feat(client): file artifact kind union + groups support dashboard"
```

---

## Task A4: 文档收尾 + index 登记 + 解锁 P1 B4

**依赖:** A1 + A2 + A3 全部完成

### A4.S1 — 更新上游文档

- [x] **Step 1: 解除 P1 B4 BLOCKED 状态**

修改 `docs/exec-plans/2026-05-08-report-dashboard-p1-plan.md` 中 Task B4 区域：

将 `BLOCKED: depends on file_artifact integration spec` 改为 `Unblocked by 2026-05-09-dashboard-file-artifact-integration-plan.md (A2 完成)`，并简化 B4 内容为引用本 plan 的产物。

- [x] **Step 2: 在 docs/exec-plans/index.md 登记本 plan 为 Completed**

在 `index.md` 的 "Completed" 区段添加：

```markdown
- 2026-05-09 — Dashboard ↔ File Artifact 集成增强 — [plan](./2026-05-09-dashboard-file-artifact-integration-plan.md) — 解锁 P1 B4，dashboard 进入 file_artifact lifecycle
```

如果 plan 是新增 Active：先添加到 Active 区段，所有 task 完成后再迁移到 Completed。

### A4.S2 — Spec 状态翻牌

- [x] **Step 3: 修改 spec 头部 Status 字段**

`docs/product-specs/2026-05-09-dashboard-file-artifact-integration-design.md` 顶部：

```markdown
- **Status:** Implemented (plan: 2026-05-09-dashboard-file-artifact-integration-plan.md)
```

并补一段"Spec 与代码差异校正"小节，列出本计划 Pre-flight 表格中提到的 6 项，避免后续读者按旧 spec 措辞行事。

### A4.S3 — Final commit + 完整 verify

- [x] **Step 4: 跑后端全量 verify**

Run: `cd server && mvn clean verify -q`
Expected: BUILD SUCCESS

- [x] **Step 5: 跑前端 type check + test**

Run: `cd client && npx tsc --noEmit && npx vitest run`
Expected: 0 errors, all pass

- [x] **Step 6: Commit 文档更新**

```bash
git add docs/exec-plans/2026-05-08-report-dashboard-p1-plan.md \
        docs/exec-plans/index.md \
        docs/exec-plans/2026-05-09-dashboard-file-artifact-integration-plan.md \
        docs/product-specs/2026-05-09-dashboard-file-artifact-integration-design.md
git commit -m "docs(dashboard-fileartifact): finish plan + index registration + unlock P1 B4"
```

---

## Self-Review

**1. Spec coverage:**

| Spec 章节 | 覆盖 task |
|---|---|
| §1.2 缺口 D1 (FileArtifactKind.DASHBOARD) | A1 (V18 CHECK) + A3 (TS 联合类型) |
| §1.2 缺口 D2 (ConnectionDeletionService) | 零代码改动；A2 测试断言验证 |
| §1.2 缺口 D3 (Files Library) | A3 (前端 group) + 后端零改动 |
| §1.2 缺口 D4 (Housekeeping reconciler) | A2.S6 reconcileExternalDirs |
| §1.2 缺口 D5 (前端 TypeScript) | A3 全部 |
| §4.1 D1 external BOOLEAN 列 | A1.S2 V18 + A1.S3-S4 record/repo |
| §4.2 D2 DashboardArtifactService 自管写盘 | A2.S7 重构 + AtomicFileWriter |
| §4.3 D3 status=archived 复用 + 语义校准 | A2.S3-S5 默认 ARCHIVED + A2.S9 审计 + A2.S10 spec 文档 |
| §4.4 D4 同步落盘 | A2.S5 replaceBytesAtomic 即时 update |
| §4.5 D5 reconcileExternalDirs | A2.S6 双向孤儿 |
| §5.1 V18 migration | A1.S2 |
| §5.2 Domain 层 | A1.S3 (FileArtifactKind 已在；FileArtifact +external) |
| §5.3 三 API | A2.S3-S5 |
| §5.4 Reconciler 旁路 | A2.S6 + reconcileWorkspacesTree 加 external skip |
| §5.5 DashboardArtifactService 形态 | A2.S7 重构 |
| §5.6 ConnectionDeletionService 零改动 | A2.S9 审计验证 |
| §5.7 Files Library 前端补 2 行 | A3.S1 |
| §5.8 ArtifactWatcherService 不动 | 无 task（spec §5.8 明确 N/A） |
| §6 Status 语义校准 | A2.S9 审计 + A2.S10 文档 |
| §8.2 Rollback helper | A1.S6 |
| §9 Test Strategy | A1.S1/S11、A2.S4/S8/S12/S16 全覆盖；前端 A3.S2 类型回归 |
| §10 实施分解 A1/A2/A3 | 本 plan 同名 task |

**Data Source Type Compatibility Gate:** N/A — 本 plan 不涉及任何 database/data-source 类型的添加/修改/依赖；改动局限于 file_artifact metadata DB 表（SQLite，应用元数据库），不涉及用户连接的目标数据库。

**Frontend Design Contract Gate (A3 适用):** A3 仅修改类型定义与 store 内的 group 初始化；不涉及视觉/布局/交互；不引入新组件或新视觉规则；client/DESIGN.md 约束仅在"类型与后端字段保持一致"层面适用，已在 A3 步骤覆盖。

**2. Placeholder scan:** 所有"TBD"/"add appropriate handling"/"similar to"/"implement later"已肉眼复查未出现；每个步骤都有可执行命令或完整代码块。

**3. Type consistency:**
- `FileArtifactService.registerExternal(...)` 签名在 A2.S3 与 A2.S7 调用处一致（`String id, FileArtifactKind kind, FileArtifactScope scope, String connectionId, String sessionId, Path absolutePath, String title, String summary, Map<String, Object> metadata`）
- `FileArtifactService.readBytes(String fileArtifactId): byte[]` 在 A2.S4 / A2.S7 一致
- `FileArtifactService.replaceBytesAtomic(String fileArtifactId, byte[] bytes): FileArtifact` 在 A2.S5 / A2.S7 一致
- `SessionWorkdirRoot.dashboardsRoot()` / `externalManagedRoots()` 在 A2.S2 定义、A2.S6 调用 / A2.S7 调用 一致
- `FileArtifactRepository.findExternalRowsByDir(String dirAbsolute)` 在 A1.S4 定义、A2.S6 调用 一致
- `FileArtifact` record 在 A1.S3 加 `external` 字段后，所有新调用点（A2.S3-S5）尾参均为 `true`/`false`，A1.S3 步骤已要求批量修复现有调用点

---

## Execution Handoff

Plan executed and completed 2026-05-09. All 4 tasks shipped:

- **A1**: V18 migration (`V18__file_artifact_dashboard.sql`) + `FileArtifact.external` boolean + `JdbcFileArtifactRepository` external support + `findExternalRowsByDir`
- **A2**: `FileArtifactService.registerExternal` / `readBytes` / `replaceBytesAtomic` + `AtomicFileWriter` + `FileArtifactNotFoundException` / `FileArtifactConflictException` + `FileArtifactReconciler.reconcileExternalDirs` + `SessionWorkdirRoot.dashboardsRoot` / `externalManagedRoots` + `DashboardConfiguration` @Bean wiring + `DashboardArtifactService` 重构（移除 DashboardStore，改用 FileArtifactService）
- **A3**: 前端 `FileArtifactKind` 联合类型加 `'dashboard'` + `EMPTY_KIND_GROUPS` 同步
- **A4**: 文档收尾（status 语义校准 `archived` ≠ frozen）

## Completion Log

| Field | Value |
|---|---|
| Completed | 2026-05-09 |
| Status | All 4 tasks verified in code (2026-05-12 housekeeping re-check): V18 migration present, all Java service methods exist, frontend types aligned, DashboardStore removed. |
