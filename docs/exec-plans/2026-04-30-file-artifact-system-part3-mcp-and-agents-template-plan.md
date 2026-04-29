# File Artifact System · Part 3 — MCP Tool & AGENTS Template

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the AI-side end of the file artifact contract: ship the `datatalk_archive_artifact` MCP tool as an `@DataTalkAction` handler in the adapter layer, embed the AGENTS.md `## Output Files & Artifacts` section (with `<!-- file-artifact-section:begin / :end -->` markers + `{{ACTIVE_SESSION_DIR}}` placeholder) into the classpath template at `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`, and lock both with contract tests so future refactors cannot silently delete them. After Part 3 completes, AI agents running inside OpenCode have a published, schema-typed entrypoint to promote `TEMPORARY` files to `CANDIDATE` status, and the AGENTS template documents the workflow verbatim per spec §5.5.

**Architecture:** Action handler lives in adapter layer (`com.datatalk.adapter.actions`), follows the project's existing `@DataTalkAction` auto-registration convention (no central registry edit). Handler is a stateless Spring bean, takes `FileArtifactService` + `FileArtifactRepository` + `ActiveSessionDirProvider` + `IdGenerator` + `Clock` via constructor injection. Path safety re-runs the full eight-rule guard from Part 1's `FileArtifactService.guardPath` even though Part 2's watcher already enforces it on FS events — archive promotion is an explicit user/AI intent and must be validated independently of FS watcher trust (defense in depth, spec §5.4 last paragraph). `PathSafetyError` enum values are mapped to wire-format MCP error codes per spec §5.2. AGENTS.md template gains exactly one new section, wrapped with stable HTML comment markers (spec §10 risk row mitigation), and uses the already-implemented `{{ACTIVE_SESSION_DIR}}` placeholder rendered by `AgentPromptBuilder` (Part 1 Task 14).

**Tech Stack:** Spring Boot 3.5, Java 21 (sealed switch / pattern), `ActionHandler<Map, Map>` interface, JUnit 5, AssertJ, WireMock 3.x via existing FakeOpenCodeServer pattern (or in-process handler invocation, mirroring `ConnectionManagementActionsIT` and `ExecuteSqlActionIT`).

**Spec:** [docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md](../product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md) — §3.3 ②, §5.1, §5.2, §5.3, §5.4, §5.5, §8.1, §10.

**关联 Part：**
- Part 1 — Migration + Domain (✅ shipped 2026-04-29)
- Part 2 — ArtifactWatcherService (io.methvin) + reconcile + symlink rejection (待补正式计划)
- Part 3 (本计划) — `ArchiveArtifactActionHandler` MCP + classpath AGENTS.md `## Output Files & Artifacts` + `AgentsTemplateContractTest`
- Part 4 — Frontend Files Tab + Files Library Tab + Zustand store + i18n
- Part 5 — session/connection DELETE 两阶段 + Chat 卡片 + 终局确认 modal + HousekeepingScheduler + LegacyMigrationRunner + Settings Maintenance

**执行状态：** 未开始（计划登记 only）。

---

## Files

### Adapter (新增 ActionHandler)

- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ArchiveArtifactAction.java`

### Adapter tests (action handler IT + AGENTS template contract)

- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ArchiveArtifactActionHandlerIT.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentsTemplateContractTest.java`

### Application (扩展 FileArtifactService 仅一个新方法)

- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java`
  - 增加 `archiveCandidate(sessionId, requestedPath, kind, title, summary)` 编排方法（包装 guardPath + insert/upgrade row + return id）。物理 mv 仍由 Part 5 实现。本方法只做 `INSERT/UPDATE` 到 status=CANDIDATE。

### Application tests (新增方法的单测 + 已有 PromptBuilder 测试追加)

- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceTest.java`
  - 追加 `archiveCandidate` 业务单测：guardPath 失败 → 返回错误；TEMPORARY 行已存在 → 升 candidate；不存在 → 新建 CANDIDATE 行；已是 CANDIDATE → 幂等 warn。
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/stage/AgentPromptBuilderTest.java`
  - 追加 2 例：注入完整模板字符串（含 `## Output Files & Artifacts` 节 + `{{ACTIVE_SESSION_DIR}}` 占位），分别 active session present / absent，断言渲染后内容包含 `./sessions/<sid>/` 或 `<no active session>`。

### Adapter resource (AGENTS.md 模板修改)

- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
  - 在 `{{STAGE_TAB_DIGEST}}` 前插入新节，节首尾包 `<!-- file-artifact-section:begin -->` / `<!-- file-artifact-section:end -->`，节内嵌 `{{ACTIVE_SESSION_DIR}}` 占位与 `datatalk_archive_artifact` 调用示例。

### Docs

- Modify: `docs/exec-plans/index.md`（活跃计划表更新本 Part 3 状态从“(待补正式计划)”切换到正式 plan 链接 + 摘要）

---

## Task 1: 注册计划

- [ ] 已存在 `docs/exec-plans/2026-04-30-file-artifact-system-part3-mcp-and-agents-template-plan.md`（本文件）
- [ ] 修改 `docs/exec-plans/index.md`「活跃计划」表：删除原 `File Artifact System · Part 3 — MCP Tool & AGENTS Template (待补正式计划)` 占位行；新增正式登记行：

```markdown
| [File Artifact System · Part 3 — MCP Tool & AGENTS Template](./2026-04-30-file-artifact-system-part3-mcp-and-agents-template-plan.md) | 2026-04-30 | OpenCode 工作目录与 File Artifact 系统 v2 落地 Part 3：在 adapter 增加 `@DataTalkAction(id="datatalk.archive_artifact")` 的 `ArchiveArtifactAction`（输入 path/kind/title/summary、复用 Part 1 `FileArtifactService.guardPath` 八条规则、`PathSafetyError` → MCP 错误码映射、幂等 already_archived 返回 ok+warn）；classpath `agents/AGENTS.md` 在 `{{STAGE_TAB_DIGEST}}` 前插入 `## Output Files & Artifacts` 节并以 `<!-- file-artifact-section:begin/:end -->` HTML 注释包裹（spec §10 防误删）；新增 `AgentsTemplateContractTest` 锁定该节与占位；扩展 `AgentPromptBuilderTest` 验证占位在 active session 有/无两种渲染下的输出。 |
```

- [ ] commit:

```bash
git add docs/exec-plans/2026-04-30-file-artifact-system-part3-mcp-and-agents-template-plan.md \
        docs/exec-plans/index.md
git commit -m "docs(exec-plans): register file artifact system part 3 plan"
```

---

## Task 2: 应用层 — `FileArtifactService.archiveCandidate` 编排方法

**目的：** Part 1 已落地 `guardPath` + `markCandidate`。Part 3 的 MCP handler 不能直接组合两者：guardPath 只校验 path 安全；markCandidate 假定 row 已存在。AI 调 archive 时该 path 可能还没被 watcher detect（Part 2 才上线 watcher，且即便 Part 2 已上线，AI 写完文件就立即调 archive 时也存在 race window）。因此 service 层需要一个原子编排方法把"路径校验 + 找/建 row + 升级到 CANDIDATE"封进一个 use case。

### 2.1 设计契约

- **签名**：`ArchiveCandidateOutcome archiveCandidate(String sessionId, String requestedPath, FileArtifactKind kind, String title, String summary, Clock clock, IdGenerator ids)`
  - 注：本方法不持有 `Clock` / `IdGenerator` 注入（避免给 service 加新依赖），由调用方传入；service 已有的 `repo` / `workdir` 字段够用。
- **返回**：sealed `ArchiveCandidateOutcome` —
  - `Success(String fileArtifactId, String physicalPath, boolean alreadyArchived)`
  - `PathRejected(PathSafetyError error)`
- **逻辑流**：
  1. `guardPath(sessionId, requestedPath)`，若返回 error → 包成 `PathRejected` 返回
  2. 解析 `target = workdir.require(sessionId).resolve(requestedPath).toRealPath()`，转 `physicalPath = target.toString()`；读 size = `Files.size(target)`
  3. 查 `repo.findBySession(sessionId)` 中 `physicalPath` 等于解析路径的行：
     - 存在且 status=ARCHIVED → `Success(id, physicalPath, alreadyArchived=true)` （幂等）
     - 存在且 status=DISCARDED → 视作 not found 重新插入新 row（旧 row 已物理移走）
     - 存在且 status IN (TEMPORARY, CANDIDATE) → `repo.updateStatus(id, CANDIDATE)`（idempotent for CANDIDATE）；用 `repo.updateMetadata` 同步 size + mtime；如果 title/summary 非空则进一步 `updateAuxiliary`（见 2.3 新增 repo 方法）；返回 `Success(id, physicalPath, alreadyArchived=false)`
     - 不存在 → `repo.insert(new FileArtifact(...))` 直接以 status=CANDIDATE 插入新行；返回 `Success(newId, physicalPath, alreadyArchived=false)`
  4. 物理 mv 不在本 Part 做（Part 5 才把文件 mv 到 `workspaces/`）
- **不做的事**：
  - 不发 `DtEvent.FileArtifactArchiveRequested`（事件发射归 adapter 层 handler 调用 `SessionBus`，service 层保持纯）

### 2.2 实现

- [ ] 修改 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java`，在文件末尾追加：

```java
    // ───────── archiveCandidate use case (spec §3.3 ② / §5.2 / §5.4) ─────────

    public sealed interface ArchiveCandidateOutcome {
        record Success(String fileArtifactId, String physicalPath, boolean alreadyArchived)
                implements ArchiveCandidateOutcome {}
        record PathRejected(PathSafetyError error) implements ArchiveCandidateOutcome {}
    }

    /**
     * Idempotent archive-candidate promotion driven by the
     * {@code datatalk_archive_artifact} MCP tool.
     *
     * <p>Spec §3.3 ② / §5.2 / §5.4. The path-safety guard is re-run in full
     * even though Part 2's watcher enforces the same rules on FS events —
     * archive promotion is an explicit user/AI intent and cannot trust
     * the watcher's transient state.
     *
     * <p>Returns {@link ArchiveCandidateOutcome.Success} with
     * {@code alreadyArchived=true} when the file artifact is already in
     * status {@code ARCHIVED}; the caller (MCP handler) maps this to
     * {@code ok + warn=already_archived} per spec §5.2.
     */
    public ArchiveCandidateOutcome archiveCandidate(
            String sessionId,
            String requestedPath,
            com.datatalk.domain.fileartifact.FileArtifactKind kind,
            String title,
            String summary,
            java.time.Clock clock,
            com.datatalk.application.channel.IdGenerator ids) {

        Optional<PathSafetyError> guard = guardPath(sessionId, requestedPath);
        if (guard.isPresent()) {
            return new ArchiveCandidateOutcome.PathRejected(guard.get());
        }

        Path base = workdir.require(sessionId);
        Path target;
        long size;
        try {
            target = base.resolve(requestedPath).toRealPath();
            size = Files.size(target);
        } catch (IOException e) {
            return new ArchiveCandidateOutcome.PathRejected(PathSafetyError.PATH_NOT_FOUND);
        }
        String physicalPath = target.toString();
        String filename = target.getFileName().toString();

        FileArtifact existing = repo.findBySession(sessionId).stream()
                .filter(a -> physicalPath.equals(a.physicalPath()))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            return switch (existing.status()) {
                case ARCHIVED -> new ArchiveCandidateOutcome.Success(
                        existing.id(), existing.physicalPath(), true);
                case DISCARDED -> insertCandidate(
                        ids.next(), sessionId, existing.connectionId(),
                        kind, filename, physicalPath, size, title, summary, clock);
                case TEMPORARY, CANDIDATE -> {
                    if (existing.status() == FileArtifactStatus.TEMPORARY) {
                        repo.updateStatus(existing.id(), FileArtifactStatus.CANDIDATE);
                    }
                    repo.updateMetadata(existing.id(), size, clock.millis());
                    yield new ArchiveCandidateOutcome.Success(
                            existing.id(), existing.physicalPath(), false);
                }
            };
        }

        return insertCandidate(
                ids.next(), sessionId, null, kind, filename, physicalPath, size, title, summary, clock);
    }

    private ArchiveCandidateOutcome.Success insertCandidate(
            String newId,
            String sessionId,
            String connectionId,
            com.datatalk.domain.fileartifact.FileArtifactKind kind,
            String filename,
            String physicalPath,
            long size,
            String title,
            String summary,
            java.time.Clock clock) {
        java.time.Instant now = java.time.Instant.now(clock);
        String id = "file_artifact_" + newId;
        FileArtifact row = new FileArtifact(
                id,
                com.datatalk.domain.fileartifact.FileArtifactScope.SESSION,
                FileArtifactStatus.CANDIDATE,
                kind,
                sessionId,
                connectionId,
                filename,
                physicalPath,
                size,
                null,
                (title == null || title.isBlank()) ? null : title,
                (summary == null || summary.isBlank()) ? null : summary,
                now,
                now,
                null,
                java.util.Map.of()
        );
        repo.insert(row);
        return new ArchiveCandidateOutcome.Success(id, physicalPath, false);
    }
```

注：`Path` / `Files` / `IOException` / `FileArtifact` / `FileArtifactStatus` / `Optional` 等已在文件 import 顶部。`Files.size`、`FileArtifactKind`、`Instant`、`IdGenerator` 这几个全限定写在新方法内部，避免改动现有 imports；最终编译时 IDE 可能希望提取 import，由实现者自行决定。

### 2.3 编译验证

- [ ] 运行：

```bash
cd server && mvn compile -q -pl data-talk-application
```

预期：零错误。

### 2.4 单测

- [ ] 修改 `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceTest.java`，在文件末尾追加：

```java
    // ─────────── archiveCandidate use case ───────────

    @Test
    void archiveCandidate_returns_PathRejected_for_dotdot_path() {
        java.time.Clock clk = java.time.Clock.systemUTC();
        com.datatalk.application.channel.IdGenerator ids = new com.datatalk.application.channel.IdGenerator();
        FileArtifactService.ArchiveCandidateOutcome out = svc.archiveCandidate(
                "ses_abc", "../escape.md",
                com.datatalk.domain.fileartifact.FileArtifactKind.OTHER,
                null, null, clk, ids);
        assertThat(out).isInstanceOf(FileArtifactService.ArchiveCandidateOutcome.PathRejected.class);
        assertThat(((FileArtifactService.ArchiveCandidateOutcome.PathRejected) out).error())
                .isEqualTo(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
    }

    @Test
    void archiveCandidate_inserts_new_candidate_row_when_no_existing() throws Exception {
        Files.writeString(sessionDir.resolve("orders-er.md"), "# ER\n");
        when(repo.findBySession("ses_abc")).thenReturn(java.util.List.of());

        java.time.Clock clk = java.time.Clock.fixed(
                java.time.Instant.parse("2026-04-30T10:00:00Z"), java.time.ZoneOffset.UTC);
        com.datatalk.application.channel.IdGenerator ids = new com.datatalk.application.channel.IdGenerator();

        FileArtifactService.ArchiveCandidateOutcome out = svc.archiveCandidate(
                "ses_abc", "orders-er.md",
                com.datatalk.domain.fileartifact.FileArtifactKind.ER_DIAGRAM,
                "Orders ER", "Covers orders/order_items", clk, ids);

        assertThat(out).isInstanceOf(FileArtifactService.ArchiveCandidateOutcome.Success.class);
        FileArtifactService.ArchiveCandidateOutcome.Success ok =
                (FileArtifactService.ArchiveCandidateOutcome.Success) out;
        assertThat(ok.alreadyArchived()).isFalse();
        assertThat(ok.fileArtifactId()).startsWith("file_artifact_");
        verify(repo).insert(any(FileArtifact.class));
    }

    @Test
    void archiveCandidate_promotes_existing_temporary_row() throws Exception {
        Files.writeString(sessionDir.resolve("x.md"), "# x\n");
        Path real = sessionDir.resolve("x.md").toRealPath();
        FileArtifact existing = new FileArtifact(
                "file_artifact_existing",
                com.datatalk.domain.fileartifact.FileArtifactScope.SESSION,
                FileArtifactStatus.TEMPORARY,
                com.datatalk.domain.fileartifact.FileArtifactKind.OTHER,
                "ses_abc", null, "x.md", real.toString(),
                10L, null, null, null,
                java.time.Instant.now(), java.time.Instant.now(), null, java.util.Map.of());
        when(repo.findBySession("ses_abc")).thenReturn(java.util.List.of(existing));

        java.time.Clock clk = java.time.Clock.systemUTC();
        com.datatalk.application.channel.IdGenerator ids = new com.datatalk.application.channel.IdGenerator();

        FileArtifactService.ArchiveCandidateOutcome out = svc.archiveCandidate(
                "ses_abc", "x.md",
                com.datatalk.domain.fileartifact.FileArtifactKind.OTHER,
                null, null, clk, ids);

        assertThat(out).isInstanceOf(FileArtifactService.ArchiveCandidateOutcome.Success.class);
        FileArtifactService.ArchiveCandidateOutcome.Success ok =
                (FileArtifactService.ArchiveCandidateOutcome.Success) out;
        assertThat(ok.alreadyArchived()).isFalse();
        verify(repo).updateStatus("file_artifact_existing", FileArtifactStatus.CANDIDATE);
    }

    @Test
    void archiveCandidate_is_idempotent_for_archived_row() throws Exception {
        Files.writeString(sessionDir.resolve("done.md"), "# done\n");
        Path real = sessionDir.resolve("done.md").toRealPath();
        FileArtifact existing = new FileArtifact(
                "file_artifact_archived",
                com.datatalk.domain.fileartifact.FileArtifactScope.WORKSPACE,
                FileArtifactStatus.ARCHIVED,
                com.datatalk.domain.fileartifact.FileArtifactKind.REPORT,
                "ses_abc", "conn_xyz", "done.md", real.toString(),
                10L, null, "T", "S",
                java.time.Instant.now(), java.time.Instant.now(),
                java.time.Instant.now(), java.util.Map.of());
        when(repo.findBySession("ses_abc")).thenReturn(java.util.List.of(existing));

        java.time.Clock clk = java.time.Clock.systemUTC();
        com.datatalk.application.channel.IdGenerator ids = new com.datatalk.application.channel.IdGenerator();

        FileArtifactService.ArchiveCandidateOutcome out = svc.archiveCandidate(
                "ses_abc", "done.md",
                com.datatalk.domain.fileartifact.FileArtifactKind.REPORT,
                null, null, clk, ids);

        assertThat(out).isInstanceOf(FileArtifactService.ArchiveCandidateOutcome.Success.class);
        assertThat(((FileArtifactService.ArchiveCandidateOutcome.Success) out).alreadyArchived()).isTrue();
        verify(repo, org.mockito.Mockito.never()).updateStatus(any(), any());
        verify(repo, org.mockito.Mockito.never()).insert(any());
    }
```

- [ ] 运行：

```bash
cd server && mvn -pl data-talk-application test -Dtest=FileArtifactServiceTest -q
```

预期：原有 14 个 + 新 4 个 = 18 个测试通过。

### 2.5 关键收尾 + commit

- [ ] 把 application 模块新 jar 推到 `~/.m2`（CLAUDE.md "Backend Run vs Compile"）：

```bash
cd server && mvn install -pl data-talk-application -am -DskipTests -q
```

- [ ] commit:

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java \
        server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceTest.java
git commit -m "feat(application): add FileArtifactService.archiveCandidate use case"
```

---

## Task 3: AGENTS.md 模板修改 — 嵌入 `## Output Files & Artifacts` 节

**目的：** 让 AI 在 OpenCode 运行时收到归档指南；通过 HTML 注释 marker 防止后续无意删除（spec §10 风险行）。

### 3.1 编辑 classpath 模板

- [ ] 修改 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`：在文件末尾 `{{STAGE_TAB_DIGEST}}` 一行**之前**插入新节。完整插入文本：

```markdown
<!-- file-artifact-section:begin -->
## Output Files & Artifacts

Your current session has a dedicated working subdirectory at:

  {{ACTIVE_SESSION_DIR}}

(Example: `./sessions/ses_abc123def/`. Note the relative path — your shell's cwd is the parent.)

**Default (Temporary)**: Use `write`, `edit`, or `bash` to create intermediate files inside that subdirectory (CSV samples, scratch scripts, debug logs). These are auto-tracked but treated as ephemeral and will be cleaned up when the session is deleted.

**Promote to Archive Candidate**: When you produce a deliverable the user will want to keep — analysis reports, ER diagrams, SQL scripts, datasets — call `datatalk_archive_artifact` with the file path (relative to the session subdir) and a `kind`:

  datatalk_archive_artifact(
    path="orders-er.md",
    kind="er_diagram",
    title="Orders domain ER",
    summary="Covers orders/order_items/payments relationships"
  )

The user then decides in their UI whether to permanently archive it to the connection's asset library.

**Rules**:
- Always write into your session subdirectory ({{ACTIVE_SESSION_DIR}}), not the parent cwd
- Never write into directories prefixed with `_` (system reserved)
- Never use symlinks
- For Markdown / SQL deliverables, you may also add a YAML frontmatter block with `artifact: true, kind: ...` — this is a fallback hint if you forget to call the tool, but the tool is the primary mechanism

<!-- file-artifact-section:end -->

```

最终文件末尾仍然是已有的 `{{STAGE_TAB_DIGEST}}`（不动）。

### 3.2 校验既有内容未被破坏

- [ ] 编辑前后用 `diff` 检查：除新插入的 33 行（含 `:begin` / `:end` marker、空行、节标题、规则正文）以外，文件其余内容字节级一致。Self-check：

```bash
git diff server/data-talk-adapter/src/main/resources/agents/AGENTS.md | head -80
```

预期：只在 `{{STAGE_TAB_DIGEST}}` 之前看到 `+` 增行，无 `-` 删行。

### 3.3 已有 contract test 回归

- [ ] 跑现有 `AgentPromptContractTest` 验证整体模板未破坏（既不引入中文也未删 registered tools 的引用，新节英文且 `datatalk_archive_artifact` 是已注册 tool —— Task 4 后才注册，所以本步骤先暂不验证 tool 注册等价性，只验证模板完整性）：

```bash
cd server && mvn -pl data-talk-adapter test -Dtest=AgentPromptContractTest -q
```

预期：通过（既有 3 个测试 —— `runtimePromptReferencesOnlyRegisteredMcpTools` 此时仍通过，因为新节出现的 `datatalk_archive_artifact` 在 Task 4 完成后才会被注册；如果 Task 3 单独跑会失败 — 那么把 Task 3 与 Task 4 视为一对，在 Task 4 结束后再统一跑此校验。本步骤暂不强制单跑）。

> 注：实施时如果按本计划顺序（先 Task 3 再 Task 4），合理做法是 **先做 Task 4 的 ActionHandler**（让 `datatalk_archive_artifact` 进入注册表），再做 Task 3 的模板修改，最后回到 Task 5 contract test。本计划保持文档顺序为"模板 → handler → contract test"以保留章节连续性，但 commit 顺序应在 Task 4 commit 之后再 commit Task 3 的模板改动。下面的 commit 步骤标注为"在 Task 4 完成后再执行"。

### 3.4 commit（在 Task 4 完成后执行）

- [ ] 暂缓 commit；继续做 Task 4。Task 4 完成后再回到 3.4 执行：

```bash
git add server/data-talk-adapter/src/main/resources/agents/AGENTS.md
git commit -m "docs(agents): add Output Files & Artifacts section with file-artifact markers"
```

---

## Task 4: 新增 `ArchiveArtifactAction` ActionHandler

**目的：** 让 `datatalk_archive_artifact` 通过 `@DataTalkAction` 进入 `ActionRegistry` → MCP namespace；handler 内部调用 Task 2 的 `archiveCandidate` 编排方法并把结果映射成 spec §5.2 的入站/出站 wire shape。

### 4.1 实现

- [ ] 创建 `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ArchiveArtifactAction.java`：

```java
package com.datatalk.adapter.actions;

import com.datatalk.application.channel.IdGenerator;
import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.application.fileartifact.FileArtifactService.ArchiveCandidateOutcome;
import com.datatalk.application.fileartifact.PathSafetyError;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.action.RiskLevel;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * MCP entrypoint for AI-driven archive candidate promotion.
 *
 * <p>Spec §3.3 ② / §5.1 / §5.2. Wire-format input/output match spec §5.2:
 * <pre>
 *   input  = { path: string, kind: enum, title?: string, summary?: string }
 *   output (success)  = { ok: true, fileArtifactId, status: "candidate", physicalPath }
 *                       (idempotent already-archived adds warn="already_archived")
 *   output (failure)  = { ok: false, error: "<wire code>" }
 * </pre>
 *
 * <p>Path safety re-runs the eight-rule guard from {@code FileArtifactService.guardPath}
 * defensively — even if Part 2's watcher already enforces the rules on FS events,
 * archive promotion is an explicit user/AI intent that cannot trust transient watcher state.
 */
@Component
@DataTalkAction(
    id = "datatalk.archive_artifact",
    executor = Executor.SERVER,
    description = "action.archive_artifact.description",
    timeoutMs = 5_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.ARTIFACT }
)
public class ArchiveArtifactAction implements ActionHandler<Map, Map> {

    private final FileArtifactService svc;
    private final IdGenerator ids;
    private final Clock clock;

    public ArchiveArtifactAction(FileArtifactService svc, IdGenerator ids, Clock clock) {
        this.svc = svc;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("path", "kind"),
            "properties", Map.of(
                "path", Map.of(
                    "type", "string",
                    "description", "Path relative to current session subdir (./sessions/<sid>/). " +
                                   "Example: 'orders-er.md' or 'reports/weekly.md'. No traversal (..). No symlinks."
                ),
                "kind", Map.of(
                    "type", "string",
                    "enum", List.of("report", "er_diagram", "sql_script", "dataset", "other")
                ),
                "title", Map.of(
                    "type", "string",
                    "description", "Optional human-readable title (default: filename)."
                ),
                "summary", Map.of(
                    "type", "string",
                    "description", "Optional one-paragraph summary."
                )
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("ok"),
            "properties", Map.of(
                "ok", Map.of("type", "boolean"),
                "fileArtifactId", Map.of("type", "string"),
                "status", Map.of("type", "string"),
                "physicalPath", Map.of("type", "string"),
                "warn", Map.of("type", "string"),
                "error", Map.of("type", "string")
            )
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.CREATE_ARTIFACT); }

    @Override
    public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        return CompletableFuture.supplyAsync(() -> execute(ctx, input));
    }

    private Map<String, Object> execute(ActionContext ctx, Map<String, Object> input) {
        String sessionId = ctx.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return errorBody("path_not_found");
        }

        String path = stringOrNull(input, "path");
        String kindWire = stringOrNull(input, "kind");
        String title = stringOrNull(input, "title");
        String summary = stringOrNull(input, "summary");

        if (path == null || path.isBlank()) {
            return errorBody("path_not_found");
        }
        if (kindWire == null || kindWire.isBlank()) {
            return errorBody("path_not_found");
        }

        FileArtifactKind kind;
        try {
            kind = FileArtifactKind.fromDb(kindWire);
        } catch (IllegalArgumentException e) {
            return errorBody("path_not_found");
        }

        ArchiveCandidateOutcome outcome = svc.archiveCandidate(
                sessionId, path, kind, title, summary, clock, ids);

        return switch (outcome) {
            case ArchiveCandidateOutcome.PathRejected r -> errorBody(toWire(r.error()));
            case ArchiveCandidateOutcome.Success s -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("ok", true);
                body.put("fileArtifactId", s.fileArtifactId());
                body.put("status", "candidate");
                body.put("physicalPath", s.physicalPath());
                if (s.alreadyArchived()) {
                    body.put("warn", "already_archived");
                }
                yield body;
            }
        };
    }

    private static Map<String, Object> errorBody(String wire) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("error", wire);
        return body;
    }

    private static String toWire(PathSafetyError e) {
        // PathSafetyError.wire() already yields snake_case; pass through.
        return e.wire();
    }

    private static String stringOrNull(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
```

### 4.2 i18n description key（避免 `Translator.get` 缺 key 警告）

DataTalk action description 是 i18n key（参考其它 handler 使用 `action.execute_sql.description` 等）。本 handler 用 `action.archive_artifact.description`。

- [ ] 检查 `server/data-talk-adapter/src/main/resources/i18n/messages*.properties`（如果项目用 properties）或 `server/data-talk-application/src/main/resources/i18n/`：

```bash
find /home/wallfacers/project/data-talk/server -name "messages*.properties" -o -name "messages*.yml" 2>/dev/null
```

- [ ] 在每个语言文件中追加：

```properties
action.archive_artifact.description=Mark a file in the current session subdir as an archive candidate, signaling the user that this output is worth preserving as a long-term asset for the active database connection.
```

中文版本（如有 `messages_zh*.properties`）：

```properties
action.archive_artifact.description=将当前 session 子目录中的文件标记为归档候选，提示用户该产出值得作为长期资产保留到当前连接的资产库。
```

> 如果项目尚无 i18n message 文件机制（仅在源码里直接读 key），则可跳过本步；adapter 启动时会回退到 key 字面量。运行 `mvn -pl data-talk-adapter test` 时若有缺 key 警告再补。

### 4.3 编译验证

- [ ] 运行：

```bash
cd server && mvn compile -q -pl data-talk-adapter
```

预期：零错误。

### 4.4 commit Task 3 + Task 4 一起

Task 3 的 AGENTS.md 模板修改与 Task 4 的 ActionHandler 是配对的：模板里出现的 `datatalk_archive_artifact` 必须在注册表里有对应 handler，否则 `AgentPromptContractTest.runtimePromptReferencesOnlyRegisteredMcpTools` 会红。两者一起 commit。

- [ ] 跑既有 contract test 验证一致性：

```bash
cd server && mvn -pl data-talk-adapter test -Dtest=AgentPromptContractTest -q
```

预期：3 个既有测试通过；`datatalk_archive_artifact` 现在也在 registry 里，模板内对它的引用合法。

- [ ] commit：

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ArchiveArtifactAction.java \
        server/data-talk-adapter/src/main/resources/agents/AGENTS.md
git commit -m "feat(adapter): add datatalk_archive_artifact MCP action and AGENTS template section"
```

> 如果 Task 4.2 修改了 i18n properties，把对应文件一并加入 `git add`。

---

## Task 5: `ArchiveArtifactActionHandlerIT` — handler 集成测试

**目的：** 端到端验证 spec §5.2 的输入/输出契约：happy path + 6 类失败 + idempotent。沿用 `ConnectionManagementActionsIT` 的 `@SpringBootTest` + `@Autowired Action` 模式，不走 OpenCode HTTP；在 service 层之上、handler 直接调用，等价于 OpenCode → MCP → DataTalk 入站后的 dispatch。

### 5.1 测试 fixture 准备

- 关键依赖：测试需要在临时目录下伪造 `~/.data-talk/opencode/sessions/<sid>/` 子目录树，并把 `SessionWorkdirRoot` bean 替换为指向 `@TempDir`。最小侵入是在测试上下文用 `@DynamicPropertySource` 把 `datatalk.workdir.data-talk-root` 指向 `@TempDir`。

### 5.2 编写 IT

- [ ] 创建 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ArchiveArtifactActionHandlerIT.java`：

```java
package com.datatalk.adapter.actions;

import com.datatalk.DataTalkApplication;
import com.datatalk.application.fileartifact.SessionWorkdirService;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = DataTalkApplication.class)
class ArchiveArtifactActionHandlerIT {

    @TempDir
    static Path TMP;

    @DynamicPropertySource
    static void overrideRoot(DynamicPropertyRegistry registry) {
        registry.add("datatalk.workdir.data-talk-root", () -> TMP.toString());
    }

    @Autowired
    ArchiveArtifactAction action;

    @Autowired
    SessionWorkdirService workdir;

    Path sessionDir;

    @BeforeAll
    void seed() {
        sessionDir = workdir.getOrCreate("ses_arc", "conn_xyz");
    }

    @BeforeEach
    void cleanFiles() throws Exception {
        // Re-create a clean session subtree for each test
        if (Files.exists(sessionDir)) {
            try (var walk = Files.walk(sessionDir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
            }
        }
        sessionDir = workdir.getOrCreate("ses_arc", "conn_xyz");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(Map<String, Object> input) throws Exception {
        return (Map<String, Object>) action.handle(
                new ActionContext("ses_arc", "call-1", "conn_xyz", "oc-1"),
                input).toCompletableFuture().get();
    }

    @Test
    void happy_path_md_file_returns_ok_and_candidate_id() throws Exception {
        Files.writeString(sessionDir.resolve("orders-er.md"), "# ER\n");
        Map<String, Object> out = invoke(Map.of(
                "path", "orders-er.md",
                "kind", "er_diagram",
                "title", "Orders ER",
                "summary", "covers orders/order_items"));

        assertThat(out).containsEntry("ok", true);
        assertThat(out).containsEntry("status", "candidate");
        assertThat((String) out.get("fileArtifactId")).startsWith("file_artifact_");
        assertThat((String) out.get("physicalPath")).endsWith("orders-er.md");
        assertThat(out).doesNotContainKey("warn");
        assertThat(out).doesNotContainKey("error");
    }

    @Test
    void path_traversal_rejected() throws Exception {
        Map<String, Object> out = invoke(Map.of(
                "path", "../foo.md",
                "kind", "report"));
        assertThat(out).containsEntry("ok", false);
        assertThat(out).containsEntry("error", "path_outside_session_dir");
    }

    @Test
    void absolute_path_rejected() throws Exception {
        Map<String, Object> out = invoke(Map.of(
                "path", "/etc/passwd",
                "kind", "other"));
        assertThat(out).containsEntry("ok", false);
        assertThat(out).containsEntry("error", "path_outside_session_dir");
    }

    @Test
    void underscore_prefixed_directory_rejected() throws Exception {
        Map<String, Object> out = invoke(Map.of(
                "path", "_systemdir/foo.md",
                "kind", "other"));
        assertThat(out).containsEntry("ok", false);
        assertThat(out).containsEntry("error", "path_is_system");
    }

    @Test
    void symlink_pointing_outside_rejected() throws Exception {
        Path victim = TMP.resolve("victim.md");
        Files.writeString(victim, "secret");
        Path link = sessionDir.resolve("escape.md");
        Files.createSymbolicLink(link, victim);

        Map<String, Object> out = invoke(Map.of(
                "path", "escape.md",
                "kind", "other"));
        assertThat(out).containsEntry("ok", false);
        // Either symlink-blocked or outside-session-dir is acceptable per spec §5.4 rule 3 / 5;
        // we assert it is one of the two well-known guard codes.
        assertThat(out.get("error"))
                .isIn("path_contains_symlink", "path_outside_session_dir");
    }

    @Test
    void directory_not_file_rejected() throws Exception {
        Files.createDirectories(sessionDir.resolve("a-subdir"));
        Map<String, Object> out = invoke(Map.of(
                "path", "a-subdir",
                "kind", "other"));
        assertThat(out).containsEntry("ok", false);
        assertThat(out).containsEntry("error", "path_is_directory");
    }

    @Test
    void missing_file_rejected() throws Exception {
        Map<String, Object> out = invoke(Map.of(
                "path", "missing.md",
                "kind", "other"));
        assertThat(out).containsEntry("ok", false);
        assertThat(out).containsEntry("error", "path_not_found");
    }

    @Test
    void already_archived_returns_ok_with_warn() throws Exception {
        Files.writeString(sessionDir.resolve("once.md"), "# once\n");

        // First call: regular CANDIDATE (no warn).
        Map<String, Object> first = invoke(Map.of(
                "path", "once.md",
                "kind", "report"));
        assertThat(first).containsEntry("ok", true);
        assertThat(first).doesNotContainKey("warn");

        // Simulate user promoting to ARCHIVED via direct repo manipulation.
        com.datatalk.application.fileartifact.FileArtifactRepository repo =
                springCtx.getBean(com.datatalk.application.fileartifact.FileArtifactRepository.class);
        String fid = (String) first.get("fileArtifactId");
        repo.markArchived(fid, "conn_xyz", (String) first.get("physicalPath"));

        // Second call on the same path with the same handler: idempotent ok+warn.
        Map<String, Object> second = invoke(Map.of(
                "path", "once.md",
                "kind", "report"));
        assertThat(second).containsEntry("ok", true);
        assertThat(second).containsEntry("warn", "already_archived");
        assertThat(second).containsEntry("fileArtifactId", fid);
    }

    @Autowired
    org.springframework.context.ApplicationContext springCtx;
}
```

注：`already_archived_returns_ok_with_warn` 直接通过注入的 `FileArtifactRepository` bean `markArchived` 把行强行升到 `ARCHIVED`，模拟 Part 5 的归档动作（Part 5 才有真正的 promote-to-archived REST 入口；这里只是为了在 Part 3 的 handler 上验证幂等返回）。

### 5.3 跑测试

- [ ] 运行：

```bash
cd server && mvn -pl data-talk-adapter test -Dtest=ArchiveArtifactActionHandlerIT -q
```

预期：8 个测试通过。

### 5.4 关键收尾 + commit

- [ ] commit：

```bash
git add server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ArchiveArtifactActionHandlerIT.java
git commit -m "test(adapter): add ArchiveArtifactActionHandlerIT covering happy path and path safety"
```

---

## Task 6: `AgentsTemplateContractTest` — 模板内容契约锁

**目的：** spec §10 风险行明确指出"AGENTS.md classpath 模板被多处修改时合并冲突"是已知风险，缓解措施是 marker 注释 + contract test。本任务实现该 contract test，覆盖：
1. 模板包含 `## Output Files & Artifacts` 节标题
2. 模板包含成对的 `<!-- file-artifact-section:begin -->` / `<!-- file-artifact-section:end -->` HTML 注释 marker
3. 模板包含 `{{ACTIVE_SESSION_DIR}}` 占位
4. 节内容包含 `datatalk_archive_artifact` 工具调用引用
5. 节确实位于成对 marker 之间（防止后续编辑误把节体删掉只留 marker）

### 6.1 编写 contract test

- [ ] 创建目录 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/`（如已有则跳过创建）。

- [ ] 创建 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentsTemplateContractTest.java`：

```java
package com.datatalk.adapter.agents;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test guarding the {@code agents/AGENTS.md} classpath template against
 * accidental deletion of the {@code ## Output Files & Artifacts} section
 * (spec §5.5 / §10 risk row).
 *
 * <p>The section is bracketed by HTML comment markers so future refactors can
 * locate and preserve it. This test fails loudly if either marker, the section
 * heading, the {@code {{ACTIVE_SESSION_DIR}}} placeholder, or the
 * {@code datatalk_archive_artifact} tool reference disappears.
 */
class AgentsTemplateContractTest {

    private static final String BEGIN = "<!-- file-artifact-section:begin -->";
    private static final String END = "<!-- file-artifact-section:end -->";

    private String load() throws IOException {
        try (var in = new ClassPathResource("agents/AGENTS.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void template_contains_file_artifact_section_markers() throws IOException {
        String tpl = load();
        assertThat(tpl).contains(BEGIN);
        assertThat(tpl).contains(END);
    }

    @Test
    void template_markers_appear_in_correct_order() throws IOException {
        String tpl = load();
        int begin = tpl.indexOf(BEGIN);
        int end = tpl.indexOf(END);
        assertThat(begin).as("begin marker present").isGreaterThanOrEqualTo(0);
        assertThat(end).as("end marker present").isGreaterThan(begin);
    }

    @Test
    void template_contains_output_files_section_heading_between_markers() throws IOException {
        String tpl = load();
        String section = sectionBody(tpl);
        assertThat(section).contains("## Output Files & Artifacts");
    }

    @Test
    void template_contains_active_session_dir_placeholder_inside_section() throws IOException {
        String tpl = load();
        String section = sectionBody(tpl);
        assertThat(section).contains("{{ACTIVE_SESSION_DIR}}");
    }

    @Test
    void template_references_archive_tool_inside_section() throws IOException {
        String tpl = load();
        String section = sectionBody(tpl);
        assertThat(section).contains("datatalk_archive_artifact");
    }

    @Test
    void template_section_includes_default_promote_and_rules_subheaders() throws IOException {
        String tpl = load();
        String section = sectionBody(tpl);
        assertThat(section).contains("**Default (Temporary)**");
        assertThat(section).contains("**Promote to Archive Candidate**");
        assertThat(section).contains("**Rules**");
    }

    private static String sectionBody(String tpl) {
        int begin = tpl.indexOf(BEGIN);
        int end = tpl.indexOf(END);
        if (begin < 0 || end < 0 || end <= begin) {
            throw new AssertionError("file-artifact-section markers missing or out of order");
        }
        return tpl.substring(begin + BEGIN.length(), end);
    }
}
```

### 6.2 运行

- [ ] 运行：

```bash
cd server && mvn -pl data-talk-adapter test -Dtest=AgentsTemplateContractTest -q
```

预期：6 个测试通过。

### 6.3 关键收尾 + commit

- [ ] commit：

```bash
git add server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentsTemplateContractTest.java
git commit -m "test(adapter): add AgentsTemplateContractTest locking file-artifact section"
```

---

## Task 7: `AgentPromptBuilderTest` — `{{ACTIVE_SESSION_DIR}}` 在新节体内的渲染验证

**目的：** Part 1 已实现 `{{ACTIVE_SESSION_DIR}}` 占位渲染；Part 1 单测验证了短模板字符串里的替换行为。Part 3 新节体里也使用了该占位（出现两次：一处在解释段、一处在 Rules 段）。这里追加 2 例测试，验证当模板就是真正的新节体时，**两处** `{{ACTIVE_SESSION_DIR}}` 都被替换，并在无 active session 时都被替换为 `<no active session>`。

### 7.1 修改测试文件

- [ ] 修改 `server/data-talk-application/src/test/java/com/datatalk/application/stage/AgentPromptBuilderTest.java`，在文件内合适位置追加（不删除既有方法）：

```java
    @Test
    void render_replaces_both_placeholder_occurrences_when_active_session_present() {
        when(activeDir.currentSessionId()).thenReturn(Optional.of("ses_xyz"));
        String tpl = """
                <!-- file-artifact-section:begin -->
                ## Output Files & Artifacts

                Your current session has a dedicated working subdirectory at:

                  {{ACTIVE_SESSION_DIR}}

                **Rules**:
                - Always write into your session subdirectory ({{ACTIVE_SESSION_DIR}}), not the parent cwd
                <!-- file-artifact-section:end -->
                """;
        String result = builder.render(tpl);
        assertThat(result).doesNotContain("{{ACTIVE_SESSION_DIR}}");
        // The placeholder appears twice in the section body — both must be replaced.
        long occurrences = result.lines().filter(l -> l.contains("./sessions/ses_xyz/")).count();
        assertThat(occurrences).isEqualTo(2L);
    }

    @Test
    void render_replaces_both_placeholder_occurrences_with_sentinel_when_no_session() {
        when(activeDir.currentSessionId()).thenReturn(Optional.empty());
        String tpl = """
                Your current session has a dedicated working subdirectory at:

                  {{ACTIVE_SESSION_DIR}}

                **Rules**:
                - Always write into your session subdirectory ({{ACTIVE_SESSION_DIR}}), not the parent cwd
                """;
        String result = builder.render(tpl);
        assertThat(result).doesNotContain("{{ACTIVE_SESSION_DIR}}");
        long occurrences = result.lines().filter(l -> l.contains("<no active session>")).count();
        assertThat(occurrences).isEqualTo(2L);
    }
```

### 7.2 运行

- [ ] 运行：

```bash
cd server && mvn -pl data-talk-application test -Dtest=AgentPromptBuilderTest -q
```

预期：原有测试 + 新 2 例 = 全部通过。

### 7.3 关键收尾 + commit

- [ ] commit：

```bash
git add server/data-talk-application/src/test/java/com/datatalk/application/stage/AgentPromptBuilderTest.java
git commit -m "test(application): cover ACTIVE_SESSION_DIR replacement in file-artifact section"
```

---

## Task 8: 完整回归 + Part 3 收尾

### 8.1 完整测试套件

- [ ] 跑完整测试，确认无回归：

```bash
cd server && mvn clean verify -q
```

预期：BUILD SUCCESS；既有 Part 1 测试、`AgentPromptContractTest`、`ConnectionManagementActionsIT`、`ExecuteSqlActionIT` 等均不受影响。

### 8.2 运行时模板渲染快速 sanity（可选）

- [ ] 启动 adapter，确认 `~/.data-talk/opencode/AGENTS.md` 写出后包含新节：

```bash
cd server && mvn spring-boot:run -pl data-talk-adapter
# 另一终端
grep -A 5 "file-artifact-section:begin" ~/.data-talk/opencode/AGENTS.md
```

预期：能看到 `## Output Files & Artifacts` 节体。

> 此为可选 smoke；如果 8.1 通过即可宣告 Part 3 完成。

### 8.3 文档 housekeeping（CLAUDE.md 强制）

- [ ] 在本 plan 文件每个 Task 的 checkbox 勾完后，回到 `docs/exec-plans/index.md`：
  - 把"活跃计划"表中的本 Part 3 行删除
  - 在"已完成计划"表头部新增一行：

```markdown
| [File Artifact System · Part 3 — MCP Tool & AGENTS Template](./2026-04-30-file-artifact-system-part3-mcp-and-agents-template-plan.md) | 2026-04-30 | Part 3 落地：`ArchiveArtifactAction`（`@DataTalkAction id=datatalk.archive_artifact`）+ `FileArtifactService.archiveCandidate` 编排方法（复用 Part 1 `guardPath` 八条规则、`PathSafetyError` → wire code 映射、幂等 already_archived 返回 ok+warn）+ classpath `agents/AGENTS.md` 新增 `## Output Files & Artifacts` 节（含 `<!-- file-artifact-section:begin/:end -->` marker、`{{ACTIVE_SESSION_DIR}}` 占位与 `datatalk_archive_artifact` 调用示例）+ `AgentsTemplateContractTest`（6 例锁内容/marker/占位/section 头）+ `ArchiveArtifactActionHandlerIT`（8 例 happy path + traversal/symlink/system/directory/missing/idempotent）+ `AgentPromptBuilderTest` 追加 2 例（双占位渲染 active/absent）。`mvn clean verify -q` 通过；手工 smoke `grep file-artifact-section:begin ~/.data-talk/opencode/AGENTS.md` 可选执行。 |
```

- [ ] 在 spec 文件 `docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md` 末尾的 `**Part 1 (Migration & Domain) status**` 行下追加：

```markdown
**Part 3 (MCP & AGENTS Template) status**：已完成 — `datatalk_archive_artifact` MCP action 上线、AGENTS.md classpath 模板新增 `## Output Files & Artifacts` 节并以 marker 注释包裹防误删、契约测试锁定。Part 2 (watcher) / Part 4 (前端) / Part 5 (删除流+治理) 待续。
```

### 8.4 最终 commit

- [ ] 把 housekeeping 改动一并提交：

```bash
git add docs/exec-plans/index.md \
        docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md \
        docs/exec-plans/2026-04-30-file-artifact-system-part3-mcp-and-agents-template-plan.md
git commit -m "docs: mark file artifact system part 3 as completed"
```

---

## Verification gate

执行结束前必须按顺序确认以下命令零错误：

1. `cd server && mvn compile -q` → BUILD SUCCESS
2. `cd server && mvn -pl data-talk-application test -Dtest=FileArtifactServiceTest -q` → 18 tests PASS
3. `cd server && mvn -pl data-talk-application test -Dtest=AgentPromptBuilderTest -q` → all PASS
4. `cd server && mvn -pl data-talk-adapter test -Dtest=ArchiveArtifactActionHandlerIT -q` → 8 tests PASS
5. `cd server && mvn -pl data-talk-adapter test -Dtest=AgentsTemplateContractTest -q` → 6 tests PASS
6. `cd server && mvn -pl data-talk-adapter test -Dtest=AgentPromptContractTest -q` → 既有 3 tests PASS（验证模板未引入未注册 tool）
7. `cd server && mvn clean verify -q` → BUILD SUCCESS

任一命令 FAIL 必须排查到根因后修复，不允许 skip。

CLAUDE.md gates 显式声明：
- 数据源兼容性 Gate：本 Part **不涉及** DB 类型新增/变更（只动 file artifact + AI prompt + MCP tool） → N/A，spec §2.3 已声明。
- Backend Run vs Compile：Task 2.5 已 `mvn install -pl data-talk-application -am -DskipTests` 推 jar，确保 adapter 模块编译时拿到最新 service。
- Frontend Design Contract Gate：本 Part **不动** `client/`，无需读 `client/DESIGN.md`。

---

## Out of scope

以下内容明确不在 Part 3 范围内，由后续 Part 接手：

- **Watcher 集成** — `ArtifactWatcherService`（io.methvin DirectoryWatcher）的 register/unregister、debounce 200ms、CREATE/MODIFY/DELETE/RENAME/OVERFLOW 事件路由 → **Part 2**
- **frontmatter 解析** — Markdown / SQL frontmatter 提取并自动升 candidate → **Part 2**（与 watcher 在同一职责边界）
- **物理 mv 到 workspaces/** — `Candidate → Archived` 跃迁的原子 mv、重名 .v2/.v3 后缀策略、`workspaces/<connId>/` 物理布局 → **Part 5**
- **DtEvent 发射** — `FileArtifactArchiveRequested` 事件经 SessionBus → SSE → 前端的发射时机、payload shape、序列化 → **Part 5**（与归档/丢弃事件统一规划）
- **REST `/api/files/{fid}/archive` 端点** — 用户在 UI 点 [✓ 归档] 时走的 HTTP 路径（不同于 MCP 路径） → **Part 5**
- **前端 Files Tab UI** — Stage Files Tab 类型注册、`useFileArtifactsStore`、Chat 内联 file artifact 卡片、终局确认 modal → **Part 4 / Part 5**
- **HousekeepingScheduler 与 LegacyMigrationRunner** — 启动时 + 每日定时治理、`opencode.json.dt-bak-*` 备份滚动、`_trash/` 7 天清理、一次性历史迁移 → **Part 5**
- **Active session 上下文绑定** — 真正的 `ActiveSessionDirProvider` 实现接 OpenCode 当前 session id（Part 1 默认 bean 永远返回 empty，渲染为 `<no active session>` sentinel）→ **本 Part 仍使用默认 bean**；真正绑定 active session 的 Provider 实现留给 Part 5 接 SessionBus 时统一替换。
- **OpenCode HTTP/SSE 真实 e2e** — 用真实 OpenCode 进程跑一次 AI 调 `datatalk_archive_artifact` 的端到端 → 与 `RealOpenCodeMcpBridgeIT` 同类的 opt-in 测试，留作未来人工/CI 烟测，不在本 Part DoD 中

---

## Self-Review Checklist (执行前/执行中检查)

- [ ] **Spec 覆盖**：spec §3.3 ②（AI 主动归档 data flow）、§5.1（三层契约）、§5.2（MCP 工具完整 input/output schema + 错误码）、§5.3（frontmatter；本 Part 仅在 AGENTS.md 中提及，解析归 Part 2）、§5.4（路径安全 8 条；本 Part 复用 Part 1 实现并端到端测试）、§5.5（AGENTS.md 模板 + `{{ACTIVE_SESSION_DIR}}` 占位）、§8.1（test 矩阵 ArchiveArtifactActionHandlerIT + AgentsTemplateContractTest）、§10（marker 缓解）—— 全部有任务落地。
- [ ] **Placeholder 扫描**：本计划无 TBD/TODO；每段代码都是完整可粘贴的。
- [ ] **类型一致性**：`PathSafetyError.wire()` snake_case 输出与 spec §5.2 错误码字面量逐字对齐（`path_outside_session_dir` / `path_not_found` / `path_is_directory` / `path_is_system` / `path_contains_symlink` + 幂等 `already_archived` warn）。
- [ ] **CLAUDE.md "Backend Run vs Compile"**：Task 2.5 触发 `mvn install -pl data-talk-application -am -DskipTests`；adapter 测试在 application jar 推送之后再跑。
- [ ] **数据源兼容性 Gate**：N/A（spec §2.3 已声明 file artifact 与 DB 类型解耦）。
- [ ] **AGENTS.md 不删现有内容**：Task 3.2 显式 `git diff` 校验只增不删。
- [ ] **`<!-- file-artifact-section:begin/:end -->` 强制存在**：Task 6 的 contract test 保证后续无人能静默删除节体。

---

**Part 3 完成定义（Definition of Done）**：
1. 所有 8 个 Task 的 checkbox 全部打勾
2. `mvn clean verify` BUILD SUCCESS
3. spec 文档末尾追加 Part 3 完成状态行
4. `docs/exec-plans/index.md` 把本 Part 移到「已完成计划」表
5. 准备好 Part 4 plan 写作（前端 Files Tab + Files Library Tab + Zustand store + i18n + vitest）
