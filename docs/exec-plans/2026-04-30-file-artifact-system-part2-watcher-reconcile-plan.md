# File Artifact System · Part 2 — Watcher & Reconcile

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Part 1 已经建立的类型/服务骨架之上，接入真实的文件系统 watcher（io.methvin DirectoryWatcher），让 `~/.data-talk/opencode/sessions/<sid>/` 子树里的 AI 文件产出自动落库为 `file_artifact` 行；同时建立 startup-time 的 DB↔FS reconciler 与 OVERFLOW 兜底，保证两套存储一致。Frontmatter 解析器在 CREATE 时识别 `artifact: true` 自动跳到 CANDIDATE，未声明则默认 TEMPORARY。本 Part **不** 实现 MCP `datatalk_archive_artifact`（Part 3）、不实现物理 mv 到 `workspaces/`（Part 5）、不实现日志/备份/_trash 7 天清理（Part 5 HousekeepingScheduler）。

**Architecture:** 端口/适配器分层 —— application 层定义 `ArtifactWatcher` 端口与 `FileWatchEvent` 事件类型；infrastructure 层用 io.methvin 写一个 `MethvinArtifactWatcher` 适配。`ArtifactWatcherService` 是 application 层编排器：消费 watcher 推上来的 `FileWatchEvent`，做 debounce、frontmatter 解析、状态决策、调用 `FileArtifactRepository` 落库、用 `SessionBusRegistry` 发 `DtEvent.FileArtifact*`。`FileArtifactReconciler` 负责启动时 + OVERFLOW 时的孤儿对账（孤儿文件→TEMPORARY 入库、孤儿行→DELETE / DISCARDED）。`FileArtifactWatcherStartup` 监听 Spring `ApplicationReadyEvent`，启动 watcher 与跑一次 startup reconcile。完整每日 cron 与 `_trash` / 日志治理留给 Part 5 的 `HousekeepingScheduler`。

**Tech Stack:** Spring Boot 3.5, Java 21（sealed interface + record + virtual threads），io.methvin:directory-watcher 0.18.0，JUnit 5 + AssertJ + Mockito + `@TempDir`，H2（adapter IT 与 Part 1 一致）。

**Spec:** [docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md](../product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md)

**关联 Part：**
- Part 1（已完成） — V14 migration + Domain `FileArtifact` + `FileArtifactService` 路径安全骨架 + `SessionWorkdirService` 子目录软隔离 + `AgentPromptBuilder` `{{ACTIVE_SESSION_DIR}}` 占位
- Part 2（本计划） — `io.methvin` watcher + `FrontmatterParser` + `ArtifactWatcherService` + `FileArtifactReconciler` + `FileArtifactWatcherStartup`
- Part 3 — `ArchiveArtifactActionHandler`（MCP）+ classpath `agents/AGENTS.md` 模板新增 `## Output Files & Artifacts` 节 + `AgentsTemplateContractTest`
- Part 4 — Stage Files Tab + Files Library Tab + `useFileArtifactsStore` Zustand + i18n + vitest
- Part 5 — session/connection DELETE 两阶段 + Chat 内联卡片 + 终局确认 modal + `HousekeepingScheduler`（含 reconcile cron / `_trash` 清理 / 备份 / 日志轮转）+ `LegacyMigrationRunner` + Settings Maintenance

**数据源兼容性 Gate（CLAUDE.md 强制）：** 本 Part **不涉及** 任何数据库类型的新增、变更或依赖。所有改动发生在 application/infrastructure 模块的本地文件系统监听与 SQLite 元数据索引上，与具体业务 DB（MySQL/PG/H2 等）完全无关。检查清单各节均标记 `N/A`。

---

## Files

### Maven 依赖

- Modify: `server/pom.xml`（父 POM `<dependencyManagement>` 注册 `io.methvin:directory-watcher:0.18.0`）
- Modify: `server/data-talk-application/pom.xml`（业务依赖 `io.methvin:directory-watcher`）
- Modify: `server/data-talk-infrastructure/pom.xml`（`io.methvin:directory-watcher` —— infra 实现 watcher 适配器实际持有该库）

### Application 层（端口 + 编排）

- Create: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileWatchEvent.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/ArtifactWatcher.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FrontmatterParser.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactIds.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/ArtifactWatcherService.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactReconciler.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactWatcherStartup.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactRepository.java`（增 reconcile 用查询 + 物理路径反查）
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java`（增 `recordDetected` / `recordModified` / `recordDeleted` 业务方法 + Bus 发事件）
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactConfiguration.java`（注册 watcher 相关 bean）

### Application 测试

- Create: `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FrontmatterParserTest.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/ArtifactWatcherServiceTest.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactReconcilerTest.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceTest.java`（增 `recordDetected`/`recordModified`/`recordDeleted` 测试）

### Infrastructure 层（io.methvin 适配 + 仓储扩展）

- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/MethvinArtifactWatcher.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepository.java`（实现新增方法）

### Infrastructure 测试

- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/MethvinArtifactWatcherIT.java`
- Modify: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepositoryIT.java`（增对账查询的 round-trip 测试）

### Adapter（端到端联调 IT）

- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/fileartifact/FileArtifactWatcherE2EIT.java`

### 文档

- Modify: `docs/exec-plans/index.md`（把 Part 2 占位行替换成正式 Active 注册）
- Modify: `docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md`（在末尾追加 Part 2 完成状态行；本 Part 完成时填）

---

## Task 1: 注册计划文件

**目的：** 让计划进入 `docs/exec-plans/index.md` 的 Active 列表，关闭 Part 2 占位。CLAUDE.md "Plan Document Registration" 强制：未登记的 plan 视为不存在。

- [x] 已创建 `docs/exec-plans/2026-04-30-file-artifact-system-part2-watcher-reconcile-plan.md`（本文件）。

- [x] 在 `docs/exec-plans/index.md` 的「活跃计划」表格中，**用以下行替换** Part 2 现有占位行 `| File Artifact System · Part 2 — Watcher & Reconcile (待补正式计划) | TBD | ...`：

```markdown
| [File Artifact System · Part 2 — Watcher & Reconcile](./2026-04-30-file-artifact-system-part2-watcher-reconcile-plan.md) | 2026-04-30 | OpenCode 工作目录与 File Artifact 系统 Part 2：接入 io.methvin DirectoryWatcher 监听 `~/.data-talk/opencode/sessions/` 全树（debounce 200ms、symlink 拒绝、followLinks=false），按 spec §5.6 表格的 CREATE/MODIFY/DELETE/RENAME/OVERFLOW 规约把事件转译为 `file_artifact` 行 upsert/delete + `DtEvent.FileArtifactDetected` / `FileArtifactDiscarded` 发布；新增 `FrontmatterParser`（首 8KB，`.md`/`.sql`/`.txt`，识别 `artifact: true`/`kind`/`title`/`summary`）；新增 `FileArtifactReconciler`（startup + OVERFLOW，仅扫 `sessions/*` 与 `workspaces/*`，不动 `_legacy`/`_trash`/`opencode/` 根；孤儿文件→TEMPORARY 补登记，孤儿行 temporary→静默 DELETE / candidate&archived→DELETE+警告）；`FileArtifactWatcherStartup` 监听 `ApplicationReadyEvent` 启动 watcher 并跑一次 reconcile。Part 5 HousekeepingScheduler 之后会复用 reconciler 做每日定时与 `_trash` 7 天清理。 |
```

- [x] commit：

```bash
git add docs/exec-plans/2026-04-30-file-artifact-system-part2-watcher-reconcile-plan.md \
        docs/exec-plans/index.md
git commit -m "docs(exec-plans): register file artifact system part 2 plan"
```

---

## Task 2: Maven 依赖 — io.methvin:directory-watcher 0.18.0

**目的：** 在父 POM 的 `<dependencyManagement>` 注册 `io.methvin:directory-watcher` 版本号；在 application 与 infrastructure 模块的 `<dependencies>` 引入。Application 层只在测试中用到 fake/mock，运行时 bean 由 infrastructure 提供 `MethvinArtifactWatcher`，因此 application 不需要 `io.methvin` 编译依赖（端口与事件类型完全自包含）。仅 infrastructure 需要。**纠正：** application 层 `ArtifactWatcher` 端口与 `FileWatchEvent` 类型完全不依赖 `io.methvin`；application 模块**不**引入该依赖。

### 2.1 父 POM 注册版本

- [x] 修改 `server/pom.xml`：在 `<dependencyManagement><dependencies>` 内合适位置（与已有第三方库同区）追加：

```xml
            <dependency>
                <groupId>io.methvin</groupId>
                <artifactId>directory-watcher</artifactId>
                <version>0.18.0</version>
            </dependency>
```

### 2.2 infrastructure 模块引入

- [x] 修改 `server/data-talk-infrastructure/pom.xml`，在 `<dependencies>` 内追加：

```xml
        <dependency>
            <groupId>io.methvin</groupId>
            <artifactId>directory-watcher</artifactId>
        </dependency>
```

### 2.3 验证依赖落地

- [x] 跑 dependency tree 验证：

```bash
cd server && mvn -pl data-talk-infrastructure -am dependency:tree -q | grep -i methvin
```

预期输出：`io.methvin:directory-watcher:jar:0.18.0:compile` 出现在 `data-talk-infrastructure` 子模块的依赖树。

### 2.4 commit

- [x] commit：

```bash
git add server/pom.xml server/data-talk-infrastructure/pom.xml
git commit -m "build(deps): add io.methvin directory-watcher 0.18.0"
```

---

## Task 3: Application — `FileWatchEvent` sealed interface

**目的：** 把 io.methvin 的物理事件类型完全在 application 层重新建模成自带 `record` 的 sealed 五元组（CREATE/MODIFY/DELETE/RENAME/OVERFLOW），让编排器写 exhaustive `switch` 时编译期强约束。Spec §5.6 明确说明 io.methvin 不直接发 RENAME，靠 inode/realpath 关联推断；实际实现里若不能可靠推断就退化成 DELETE+CREATE，所以本 sealed 接口里仍然保留 `Rename` record，方便未来增强（infrastructure 适配器可以选择性发出）。

- [x] 创建 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileWatchEvent.java`：

```java
package com.datatalk.application.fileartifact;

import java.nio.file.Path;
import java.time.Instant;

/**
- [x] * Application-layer abstraction of low-level filesystem events emitted by
- [x] * the {@link ArtifactWatcher} port. Five subtypes match spec §5.6 verbatim.
- [x] *
- [x] * <p>Adapters (e.g. {@code MethvinArtifactWatcher}) translate native events
- [x] * into these sealed records — application code never imports any third-party
- [x] * watcher type.
- [x] */
public sealed interface FileWatchEvent
        permits FileWatchEvent.Create,
                FileWatchEvent.Modify,
                FileWatchEvent.Delete,
                FileWatchEvent.Rename,
                FileWatchEvent.Overflow {

    /** Absolute, watcher-resolved path. {@link Overflow} returns the watch root. */
    Path path();

    /** Wall-clock at which the watcher observed the event. */
    Instant observedAt();

    record Create(Path path, Instant observedAt) implements FileWatchEvent {}

    record Modify(Path path, Instant observedAt) implements FileWatchEvent {}

    record Delete(Path path, Instant observedAt) implements FileWatchEvent {}

    /**
     * Emitted only when the adapter can correlate a delete + create as a rename
     * (inode-aware platforms). Otherwise the adapter emits {@link Delete} + {@link Create}.
     */
    record Rename(Path path, Path previousPath, Instant observedAt) implements FileWatchEvent {}

    /**
     * Watcher buffer overflow — caller must trigger a full reconcile of the
     * watch root subtree. {@link #path()} is the watch root.
     */
    record Overflow(Path path, Instant observedAt) implements FileWatchEvent {}
}
```

- [x] 编译：

```bash
cd server && mvn compile -q -pl data-talk-application
```

预期：零错误。

---

## Task 4: Application — `ArtifactWatcher` 端口

**目的：** 端口接口最小化，便于 mock。生命周期是 `start(rootPath, listener)` → `close()`。生产实现（infrastructure 层）封装 io.methvin。

- [x] 创建 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/ArtifactWatcher.java`：

```java
package com.datatalk.application.fileartifact;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
- [x] * Filesystem-watcher port. Exactly one root subtree per instance.
- [x] *
- [x] * <p>Implementations:
- [x] * <ul>
- [x] *   <li>{@code MethvinArtifactWatcher} — io.methvin DirectoryWatcher (production).</li>
- [x] *   <li>Test doubles — Mockito mocks or hand-rolled fakes for unit tests.</li>
- [x] * </ul>
- [x] *
- [x] * <p>Contract:
- [x] * <ul>
- [x] *   <li>{@link #start} is idempotent: a second call without an intervening close
- [x] *       must throw {@link IllegalStateException}.</li>
- [x] *   <li>{@link #close} is idempotent and safe even if {@code start} was never called.</li>
- [x] *   <li>The listener is invoked from a watcher-internal thread; implementations
- [x] *       should debounce / dispatch downstream rather than block here.</li>
- [x] *   <li>Symlinks are NOT followed (spec §6.3 #7); adapter must configure its
- [x] *       backing watcher accordingly.</li>
- [x] * </ul>
- [x] */
public interface ArtifactWatcher extends AutoCloseable {

    /**
     * Begin recursively watching {@code rootPath}; emit {@link FileWatchEvent}
     * to {@code listener}. Blocks only briefly to bootstrap the watcher; long-
     * running monitoring runs in adapter-owned threads.
     */
    void start(Path rootPath, Consumer<FileWatchEvent> listener);

    @Override
    void close();
}
```

- [x] 编译：

```bash
cd server && mvn compile -q -pl data-talk-application
```

预期：零错误。

---

## Task 5: Application — `FrontmatterParser`

**目的：** Spec §5.3。仅处理首 8KB；支持 `.md`/`.txt`（YAML 三连横线 fence）与 `.sql`（每行 `-- key: value` 注释包裹）；CSV / 二进制不识别（返回空）。键名严格小写，无嵌套。识别 `artifact: true` 决定 CREATE 时入库为 TEMPORARY 还是 CANDIDATE，并提取 `kind` / `title` / `summary` 写入 `metadata`。

### 5.1 实现

- [x] 创建 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FrontmatterParser.java`：

```java
package com.datatalk.application.fileartifact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
- [x] * Minimal frontmatter extractor for spec §5.3.
- [x] *
- [x] * <ul>
- [x] *   <li>Markdown / text — looks for a leading {@code ---\n...\n---} fence within
- [x] *       the first 8 KB and parses simple {@code key: value} lines (no nesting).</li>
- [x] *   <li>SQL — looks for a leading block of {@code -- key: value} comment lines,
- [x] *       optionally wrapped in {@code -- ---} fences for symmetry with Markdown.</li>
- [x] *   <li>Anything else (csv, binary, unknown extension) returns an empty map.</li>
- [x] * </ul>
- [x] *
- [x] * <p>The parser is intentionally lenient: malformed YAML never throws, the worst
- [x] * case is "no frontmatter detected" which falls through to TEMPORARY status.
- [x] */
public final class FrontmatterParser {

    /** Maximum bytes inspected at file head. spec §5.3 fixes this at 8 KB. */
    public static final int MAX_HEAD_BYTES = 8 * 1024;

    private FrontmatterParser() {}

    /** Returns parsed key/value pairs, lowercase keys; empty map on any failure. */
    public static Map<String, String> parse(Path file) {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        Flavor flavor = flavorFor(name);
        if (flavor == Flavor.UNKNOWN) {
            return Map.of();
        }
        String head;
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = in.readNBytes(MAX_HEAD_BYTES);
            head = new String(buf, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Map.of();
        }
        return switch (flavor) {
            case MARKDOWN_TEXT -> parseMarkdown(head);
            case SQL -> parseSql(head);
            case UNKNOWN -> Map.of();
        };
    }

    /** Convenience: returns {@code true} iff parsed map has {@code artifact} truthy. */
    public static boolean isArtifactDeclared(Map<String, String> parsed) {
        String v = parsed.get("artifact");
        if (v == null) return false;
        String t = v.trim().toLowerCase(Locale.ROOT);
        return t.equals("true") || t.equals("yes") || t.equals("1");
    }

    private enum Flavor { MARKDOWN_TEXT, SQL, UNKNOWN }

    private static Flavor flavorFor(String lowerName) {
        if (lowerName.endsWith(".md") || lowerName.endsWith(".markdown")
                || lowerName.endsWith(".txt")) {
            return Flavor.MARKDOWN_TEXT;
        }
        if (lowerName.endsWith(".sql")) {
            return Flavor.SQL;
        }
        return Flavor.UNKNOWN;
    }

    private static Map<String, String> parseMarkdown(String head) {
        // Must start with --- on its own line (allow optional BOM/whitespace).
        String trimmed = stripBom(head);
        if (!trimmed.startsWith("---")) return Map.of();
        int afterFirstFence = trimmed.indexOf('\n');
        if (afterFirstFence < 0) return Map.of();
        // Locate closing fence.
        String body = trimmed.substring(afterFirstFence + 1);
        int closing = indexOfLine(body, "---");
        if (closing < 0) return Map.of();
        String yaml = body.substring(0, closing);
        return parseKeyValueLines(yaml);
    }

    private static Map<String, String> parseSql(String head) {
        // Strip leading whitespace lines.
        String trimmed = stripBom(head);
        // Two acceptable forms:
        //   -- ---
        //   -- key: value
        //   -- ---
        // OR plain leading {@code -- key: value} block until first non-comment line.
        StringBuilder yaml = new StringBuilder();
        boolean insideFence = false;
        for (String rawLine : trimmed.split("\n", -1)) {
            String line = rawLine.stripTrailing();
            if (line.isEmpty()) {
                if (insideFence) {
                    yaml.append('\n');
                    continue;
                } else {
                    break;
                }
            }
            if (!line.startsWith("--")) {
                break;
            }
            String content = line.substring(2).stripLeading();
            if (content.equals("---")) {
                if (!insideFence) {
                    insideFence = true;
                    continue;
                } else {
                    return parseKeyValueLines(yaml.toString());
                }
            }
            // accumulate
            yaml.append(content).append('\n');
            if (!insideFence) {
                // Plain mode: keep going until first non-comment / blank.
            }
        }
        // Plain mode: if we never saw a closing fence but we have accumulated lines,
        // accept whatever we collected (best-effort).
        return insideFence ? Map.of() : parseKeyValueLines(yaml.toString());
    }

    private static Map<String, String> parseKeyValueLines(String body) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String rawLine : body.split("\n", -1)) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).strip();
            // Strip surrounding single or double quotes.
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!key.isEmpty()) {
                out.put(key, value);
            }
        }
        return out;
    }

    private static String stripBom(String s) {
        if (!s.isEmpty() && s.charAt(0) == '﻿') {
            return s.substring(1);
        }
        return s;
    }

    /** First index of a line equal to {@code marker} (line-anchored); -1 if absent. */
    private static int indexOfLine(String body, String marker) {
        int idx = 0;
        while (idx < body.length()) {
            int eol = body.indexOf('\n', idx);
            String line = (eol < 0) ? body.substring(idx) : body.substring(idx, eol);
            if (line.strip().equals(marker)) {
                return idx;
            }
            if (eol < 0) return -1;
            idx = eol + 1;
        }
        return -1;
    }
}
```

### 5.2 单元测试

- [x] 创建 `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FrontmatterParserTest.java`：

```java
package com.datatalk.application.fileartifact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FrontmatterParserTest {

    @TempDir
    Path tmp;

    @Test
    void markdown_with_frontmatter_extracts_keys() throws Exception {
        Path f = tmp.resolve("doc.md");
        Files.writeString(f, """
                ---
                artifact: true
                kind: er_diagram
                title: 订单域 ER 图
                summary: 覆盖三张表
                ---

                # body
                """);
        Map<String, String> parsed = FrontmatterParser.parse(f);
        assertThat(parsed).containsEntry("artifact", "true")
                .containsEntry("kind", "er_diagram")
                .containsEntry("title", "订单域 ER 图")
                .containsEntry("summary", "覆盖三张表");
        assertThat(FrontmatterParser.isArtifactDeclared(parsed)).isTrue();
    }

    @Test
    void markdown_without_frontmatter_returns_empty_map() throws Exception {
        Path f = tmp.resolve("plain.md");
        Files.writeString(f, "no frontmatter here\n");
        assertThat(FrontmatterParser.parse(f)).isEmpty();
    }

    @Test
    void markdown_with_unclosed_fence_returns_empty_map() throws Exception {
        Path f = tmp.resolve("broken.md");
        Files.writeString(f, "---\nartifact: true\n# never closed\n");
        assertThat(FrontmatterParser.parse(f)).isEmpty();
    }

    @Test
    void sql_fenced_form_extracts_keys() throws Exception {
        Path f = tmp.resolve("script.sql");
        Files.writeString(f, """
                -- ---
                -- artifact: true
                -- kind: sql_script
                -- title: backfill orders
                -- ---
                SELECT 1;
                """);
        Map<String, String> parsed = FrontmatterParser.parse(f);
        assertThat(parsed).containsEntry("artifact", "true")
                .containsEntry("kind", "sql_script")
                .containsEntry("title", "backfill orders");
    }

    @Test
    void sql_plain_comment_block_extracts_keys() throws Exception {
        Path f = tmp.resolve("plain.sql");
        Files.writeString(f, """
                -- artifact: true
                -- kind: sql_script

                SELECT 1;
                """);
        Map<String, String> parsed = FrontmatterParser.parse(f);
        assertThat(parsed).containsEntry("artifact", "true")
                .containsEntry("kind", "sql_script");
    }

    @Test
    void csv_returns_empty_map() throws Exception {
        Path f = tmp.resolve("data.csv");
        Files.writeString(f, "id,value\n1,2\n");
        assertThat(FrontmatterParser.parse(f)).isEmpty();
    }

    @Test
    void unknown_extension_returns_empty_map() throws Exception {
        Path f = tmp.resolve("data.bin");
        Files.writeString(f, "anything");
        assertThat(FrontmatterParser.parse(f)).isEmpty();
    }

    @Test
    void exceeds_8KB_head_only_inspects_first_8KB() throws Exception {
        Path f = tmp.resolve("big.md");
        StringBuilder sb = new StringBuilder("---\n");
        sb.append("artifact: true\n");
        // Fill body well past 8 KB so closing fence is past the head window
        sb.append("filler: ");
        for (int i = 0; i < 10_000; i++) sb.append('x');
        sb.append("\n---\n");
        Files.writeString(f, sb);
        // Closing fence is past the 8 KB window → parse must NOT recognize the fence.
        Map<String, String> parsed = FrontmatterParser.parse(f);
        assertThat(parsed).isEmpty();
    }

    @Test
    void quoted_values_have_quotes_stripped() throws Exception {
        Path f = tmp.resolve("doc.md");
        Files.writeString(f, """
                ---
                title: "with quotes"
                summary: 'single quotes'
                ---
                """);
        Map<String, String> parsed = FrontmatterParser.parse(f);
        assertThat(parsed).containsEntry("title", "with quotes")
                .containsEntry("summary", "single quotes");
    }

    @Test
    void isArtifactDeclared_recognizes_truthy_aliases() {
        assertThat(FrontmatterParser.isArtifactDeclared(Map.of("artifact", "true"))).isTrue();
        assertThat(FrontmatterParser.isArtifactDeclared(Map.of("artifact", "yes"))).isTrue();
        assertThat(FrontmatterParser.isArtifactDeclared(Map.of("artifact", "1"))).isTrue();
        assertThat(FrontmatterParser.isArtifactDeclared(Map.of("artifact", "false"))).isFalse();
        assertThat(FrontmatterParser.isArtifactDeclared(Map.of())).isFalse();
    }
}
```

- [x] 运行：

```bash
cd server && mvn -pl data-talk-application test -Dtest=FrontmatterParserTest -q
```

预期：10 个测试通过。

---

## Task 6: Application — `FileArtifactIds` util

**目的：** 与 spec §4 保持一致：`id` 形如 `file_artifact_<ulid>`。沿用现有 `IdGenerator` 风格（`ARTIFACT_PREFIX = "art-"`），但 file_artifact 用更明确的前缀避免混淆。

- [x] 创建 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactIds.java`：

```java
package com.datatalk.application.fileartifact;

import java.util.UUID;

/**
- [x] * Centralized id generation for {@code file_artifact} rows. Format:
- [x] * {@code file_artifact_<random>}. Spec §4 specifies the prefix; we use a
- [x] * UUID body since the project does not yet pull in a ULID dependency and the
- [x] * prefix alone is sufficient to namespace these against the legacy
- [x] * {@code art-} payload-type artifacts.
- [x] */
public final class FileArtifactIds {

    private static final String PREFIX = "file_artifact_";

    private FileArtifactIds() {}

    public static String next() {
        return PREFIX + UUID.randomUUID();
    }

    public static String prefix() {
        return PREFIX;
    }
}
```

- [x] 编译：

```bash
cd server && mvn compile -q -pl data-talk-application
```

预期：零错误。

---

## Task 7: Application — `FileArtifactRepository` 扩展

**目的：** Reconcile / watcher 路径需要按 `physical_path` 反查（去重）、扫描 session-scope 全量行（孤儿对账）。

- [x] 修改 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactRepository.java`，在文件末尾追加方法：

```java
    /** Look up by physical absolute path. Used to dedupe CREATE events and
     *  to detect rows whose file is still present on disk. */
    java.util.Optional<FileArtifact> findByPhysicalPath(String physicalPath);

    /** All session-scope rows under {@code sessions/}; used by reconcile
     *  to detect orphan rows (file gone). Implementations should return only
     *  rows with {@code scope = 'session'}. */
    java.util.List<FileArtifact> findAllSessionScoped();

    /** All workspace-scope archived rows under {@code workspaces/}; used by
     *  reconcile to detect orphan archived rows. */
    java.util.List<FileArtifact> findAllWorkspaceScopedArchived();
```

完整文件（替换全文以确保对齐）：

```java
package com.datatalk.application.fileartifact;

import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactStatus;

import java.util.List;
import java.util.Optional;

/**
- [x] * Persistence contract for {@link FileArtifact}.
- [x] *
- [x] * <p>Infrastructure owns the JDBC implementation; application code depends only on this port.
- [x] */
public interface FileArtifactRepository {

    void insert(FileArtifact artifact);

    Optional<FileArtifact> findById(String id);

    Optional<FileArtifact> findByPhysicalPath(String physicalPath);

    List<FileArtifact> findBySession(String sessionId);

    List<FileArtifact> findArchivedByConnection(String connectionId);

    List<FileArtifact> findCandidatesBySession(String sessionId);

    /** All session-scope rows; used by reconcile (file-vs-row drift detection). */
    List<FileArtifact> findAllSessionScoped();

    /** All workspace-scope archived rows; used by reconcile. */
    List<FileArtifact> findAllWorkspaceScopedArchived();

    void updateStatus(String id, FileArtifactStatus newStatus);

    void updateLocation(
            String id,
            FileArtifactStatus newStatus,
            String newScope,
            String newPhysicalPath,
            String newConnectionId);

    void markArchived(String id, String connectionId, String newPhysicalPath);

    void deleteTransientByForSession(String sessionId);

    void detachArchivedFromSession(String sessionId);

    void deleteById(String id);

    void updateMetadata(String id, long sizeBytes, long updatedAtMillis);
}
```

- [x] 编译：

```bash
cd server && mvn compile -q -pl data-talk-application
```

预期：infrastructure 模块此刻**应当编译失败**（`JdbcFileArtifactRepository` 还没实现新方法）—— 这正是下一 task 的工作。仅 application 模块编译应当成功。

---

## Task 8: Infrastructure — `JdbcFileArtifactRepository` 扩展实现

**目的：** 给 Task 7 新增的 3 个方法补 JDBC 实现 + IT 覆盖。沿用 Part 1 的 `COLS` 常量与 `mapper()` row mapper。

### 8.1 实现

- [x] 修改 `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepository.java`：在 `findById` 之后追加 `findByPhysicalPath`，在 `findCandidatesBySession` 之后追加两个 `findAll*` 实现。完整新方法块：

```java
    @Override
    public Optional<FileArtifact> findByPhysicalPath(String physicalPath) {
        var rows = jdbc.query(
                "SELECT " + COLS + " FROM file_artifact WHERE physical_path=? LIMIT 1",
                mapper(), physicalPath);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<FileArtifact> findAllSessionScoped() {
        return jdbc.query(
                "SELECT " + COLS + " FROM file_artifact WHERE scope='session'",
                mapper());
    }

    @Override
    public List<FileArtifact> findAllWorkspaceScopedArchived() {
        return jdbc.query(
                "SELECT " + COLS + " FROM file_artifact " +
                        "WHERE scope='workspace' AND status='archived'",
                mapper());
    }
```

### 8.2 IT 扩展

- [x] 修改 `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepositoryIT.java`：在文件末尾的最后一个 `}` 之前插入 3 个新测试：

```java
    @Test
    void findByPhysicalPath_returns_row_when_present() {
        repo.insert(samplePath("a1", "/abs/sessions/ses_x/foo.md"));
        var loaded = repo.findByPhysicalPath("/abs/sessions/ses_x/foo.md");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().id()).isEqualTo("a1");
    }

    @Test
    void findByPhysicalPath_returns_empty_when_absent() {
        assertThat(repo.findByPhysicalPath("/abs/missing.md")).isEmpty();
    }

    @Test
    void findAllSessionScoped_returns_only_session_rows() {
        repo.insert(sample("s1", FileArtifactStatus.TEMPORARY, "ses_x", null));
        repo.insert(sample("s2", FileArtifactStatus.CANDIDATE, "ses_y", null));
        repo.insert(archived("w1", "conn_p"));
        var rows = repo.findAllSessionScoped();
        assertThat(rows).extracting(FileArtifact::id).containsExactlyInAnyOrder("s1", "s2");
    }

    @Test
    void findAllWorkspaceScopedArchived_returns_only_workspace_archived() {
        repo.insert(sample("s1", FileArtifactStatus.TEMPORARY, "ses_x", null));
        repo.insert(archived("w1", "conn_p"));
        repo.insert(archived("w2", "conn_q"));
        var rows = repo.findAllWorkspaceScopedArchived();
        assertThat(rows).extracting(FileArtifact::id).containsExactlyInAnyOrder("w1", "w2");
    }

    private static FileArtifact samplePath(String id, String physicalPath) {
        Instant now = Instant.now();
        return new FileArtifact(id, FileArtifactScope.SESSION, FileArtifactStatus.TEMPORARY,
                FileArtifactKind.OTHER, "ses_x", null, "x.md", physicalPath,
                100L, "text/markdown", null, null, now, now, null, Map.of());
    }
```

### 8.3 验证

- [x] 编译 + 跑 IT：

```bash
cd server && mvn -pl data-talk-infrastructure -am test -Dtest=JdbcFileArtifactRepositoryIT -q
```

预期：原 10 + 新增 4 = 14 个测试通过。

### 8.4 推 jar + commit

CLAUDE.md "Backend Run vs Compile" 要求：

- [x] 推 application 与 infrastructure 模块的 jar：

```bash
cd server && mvn install -pl data-talk-application -am -DskipTests -q && \
            mvn install -pl data-talk-infrastructure -am -DskipTests -q
```

- [x] commit：

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileWatchEvent.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/ArtifactWatcher.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FrontmatterParser.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactIds.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactRepository.java \
        server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FrontmatterParserTest.java \
        server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepository.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/JdbcFileArtifactRepositoryIT.java
git commit -m "feat(application,infra): add watcher port, frontmatter parser, repo reconcile queries"
```

---

## Task 9: Application — `FileArtifactService` 扩展为 watcher 入口

**目的：** 把 watcher 业务规则集中放在 `FileArtifactService`：CREATE → INSERT TEMPORARY/CANDIDATE 行 + 发 `FileArtifactDetected`；MODIFY → 更新 size+mtime；DELETE → temporary 行静默 DELETE，candidate 行 DELETE + 发 `FileArtifactDiscarded`，archived 行不会进 sessions/ 子树（理论不发生，记录 warn）。

事件发布要求知道 `sessionId` —— 由调用方 `ArtifactWatcherService` 从路径推断后传入。

### 9.1 实现

- [x] 修改 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java`：

  1. 在构造函数注入 `SessionBusRegistry buses`（可选 — 如果 application 模块有现成的 SessionBusRegistry bean，直接 wire）
  2. 新增 4 个方法 `recordDetected` / `recordModified` / `recordDeleted` / `findByPhysicalPath`

完整修改后的文件（替换原文件）：

```java
package com.datatalk.application.fileartifact;

import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
- [x] * Use-case service for file artifact reads, candidate promotion, path safety,
- [x] * and watcher-driven row upsert/delete.
- [x] *
- [x] * <p>The watcher entry methods ({@link #recordDetected}, {@link #recordModified},
- [x] * {@link #recordDeleted}) are called from {@code ArtifactWatcherService} after
- [x] * debouncing; they are idempotent and tolerant of races (same path showing up
- [x] * twice, file disappearing between detect and read, etc.).
- [x] */
@Service
public class FileArtifactService {

    private static final Logger log = LoggerFactory.getLogger(FileArtifactService.class);

    private final FileArtifactRepository repo;
    private final SessionWorkdirService workdir;
    private final SessionBusRegistry buses;
    @SuppressWarnings("unused")
    private final ObjectMapper json;

    public FileArtifactService(FileArtifactRepository repo,
                               SessionWorkdirService workdir,
                               SessionBusRegistry buses,
                               ObjectMapper json) {
        this.repo = repo;
        this.workdir = workdir;
        this.buses = buses;
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

    public Optional<FileArtifact> findByPhysicalPath(String physicalPath) {
        return repo.findByPhysicalPath(physicalPath);
    }

    public Optional<PathSafetyError> guardPath(String sessionId, String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            return Optional.of(PathSafetyError.PATH_NOT_FOUND);
        }

        Path requested = Path.of(requestedPath);
        if (requested.isAbsolute()) {
            return Optional.of(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
        }
        for (Path segment : requested) {
            String name = segment.toString();
            if ("..".equals(name)) {
                return Optional.of(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
            }
            if (name.startsWith("_")) {
                return Optional.of(PathSafetyError.PATH_IS_SYSTEM);
            }
        }

        Path base;
        try {
            if (managedBaseContainsSymlink(sessionId)) {
                return Optional.of(PathSafetyError.PATH_CONTAINS_SYMLINK);
            }
            base = workdir.require(sessionId);
        } catch (IllegalArgumentException e) {
            return Optional.of(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
        } catch (IllegalStateException e) {
            return Optional.of(e.getMessage() != null && e.getMessage().contains("symlink")
                    ? PathSafetyError.PATH_CONTAINS_SYMLINK
                    : PathSafetyError.PATH_NOT_FOUND);
        }

        Path target = base.resolve(requested).normalize();
        if (!target.startsWith(base)) {
            return Optional.of(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
        }

        Optional<PathSafetyError> symlinkError = rejectSymlinkSegments(base, requested.normalize());
        if (symlinkError.isPresent()) {
            return symlinkError;
        }

        BasicFileAttributes before;
        try {
            before = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            return Optional.of(PathSafetyError.PATH_NOT_FOUND);
        }

        if (before.isSymbolicLink() || Files.isSymbolicLink(target)) {
            return Optional.of(PathSafetyError.PATH_CONTAINS_SYMLINK);
        }
        if (before.isDirectory()) {
            return Optional.of(PathSafetyError.PATH_IS_DIRECTORY);
        }
        if (!before.isRegularFile()) {
            return Optional.of(PathSafetyError.PATH_NOT_FOUND);
        }

        try {
            Path realTarget = target.toRealPath();
            if (!realTarget.startsWith(base)) {
                return Optional.of(PathSafetyError.PATH_OUTSIDE_SESSION_DIR);
            }
            Optional<PathSafetyError> secondSymlinkError = rejectSymlinkSegments(base, requested.normalize());
            if (secondSymlinkError.isPresent()) {
                return Optional.of(PathSafetyError.PATH_TOCTOU_RACE);
            }
            BasicFileAttributes after = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributesChanged(before, after)) {
                return Optional.of(PathSafetyError.PATH_TOCTOU_RACE);
            }
        } catch (IOException e) {
            return Optional.of(PathSafetyError.PATH_TOCTOU_RACE);
        }

        return Optional.empty();
    }

    private boolean managedBaseContainsSymlink(String sessionId) {
        SessionWorkdirRoot root = workdir.root();
        return Files.isSymbolicLink(root.dataTalkRoot())
                || Files.isSymbolicLink(root.opencodeCwd())
                || Files.isSymbolicLink(root.sessionsRoot())
                || Files.isSymbolicLink(root.sessionDir(sessionId));
    }

    public void markCandidate(String fileArtifactId) {
        FileArtifact artifact = repo.findById(fileArtifactId)
                .orElseThrow(() -> new IllegalArgumentException("file artifact not found: " + fileArtifactId));

        switch (artifact.status()) {
            case TEMPORARY -> {
                repo.updateStatus(fileArtifactId, FileArtifactStatus.CANDIDATE);
                log.info("file_artifact {} promoted TEMPORARY to CANDIDATE", fileArtifactId);
            }
            case CANDIDATE -> {
            }
            case ARCHIVED, DISCARDED -> throw new IllegalStateException(
                    "cannot mark candidate: artifact " + fileArtifactId + " is " + artifact.status());
        }
    }

    // ─────────────── Watcher entry points (Part 2) ───────────────

    /**
     * Watcher CREATE: insert a new {@code file_artifact} row if no row already
     * exists for that path. Frontmatter declaration ({@code artifact: true}) bumps
     * the new row to {@code CANDIDATE}; otherwise {@code TEMPORARY}.
     *
     * <p>Idempotent: if a row already exists for {@code physicalPath}, this is
     * a no-op (most likely a duplicate event).
     */
    public Optional<FileArtifact> recordDetected(String sessionId, Path physicalPath, Map<String, String> frontmatter) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(physicalPath, "physicalPath");
        String pathStr = physicalPath.toAbsolutePath().toString();
        Optional<FileArtifact> existing = repo.findByPhysicalPath(pathStr);
        if (existing.isPresent()) {
            return existing;
        }
        long size;
        try {
            size = Files.size(physicalPath);
        } catch (IOException e) {
            log.debug("recordDetected: file disappeared before stat: {}", pathStr);
            return Optional.empty();
        }
        FileArtifactStatus status = FrontmatterParser.isArtifactDeclared(frontmatter)
                ? FileArtifactStatus.CANDIDATE
                : FileArtifactStatus.TEMPORARY;
        FileArtifactKind kind = parseKind(frontmatter);
        Instant now = Instant.now();
        Map<String, Object> metadata = new LinkedHashMap<>(frontmatter);
        FileArtifact row = new FileArtifact(
                FileArtifactIds.next(),
                FileArtifactScope.SESSION,
                status,
                kind,
                sessionId,
                /* connectionId */ null,
                physicalPath.getFileName().toString(),
                pathStr,
                size,
                guessMime(physicalPath),
                /* title */ frontmatter.getOrDefault("title", null),
                /* summary */ frontmatter.getOrDefault("summary", null),
                now, now, /* archivedAt */ null,
                metadata);
        repo.insert(row);
        publish(sessionId, new DtEvent.FileArtifactDetected(
                row.id(), sessionId, row.filename(), row.kind().dbValue(),
                row.status().dbValue(), row.sizeBytes()));
        log.info("file_artifact {} detected (status={}) at {}", row.id(), row.status(), pathStr);
        return Optional.of(row);
    }

    /**
     * Watcher MODIFY: sync size + mtime for the row; if the file was previously
     * TEMPORARY and a freshly-parsed frontmatter now declares {@code artifact: true},
     * promote to CANDIDATE. Title/summary on already-CANDIDATE/ARCHIVED rows is
     * preserved (spec §5.6).
     */
    public void recordModified(Path physicalPath, Map<String, String> frontmatter) {
        Objects.requireNonNull(physicalPath, "physicalPath");
        String pathStr = physicalPath.toAbsolutePath().toString();
        Optional<FileArtifact> existing = repo.findByPhysicalPath(pathStr);
        if (existing.isEmpty()) {
            log.debug("recordModified: no row for {}, skipping", pathStr);
            return;
        }
        FileArtifact row = existing.get();
        long size;
        long mtime;
        try {
            size = Files.size(physicalPath);
            mtime = Files.getLastModifiedTime(physicalPath).toMillis();
        } catch (IOException e) {
            log.debug("recordModified: stat failed for {}: {}", pathStr, e.toString());
            return;
        }
        repo.updateMetadata(row.id(), size, mtime);
        if (row.status() == FileArtifactStatus.TEMPORARY
                && FrontmatterParser.isArtifactDeclared(frontmatter)) {
            repo.updateStatus(row.id(), FileArtifactStatus.CANDIDATE);
            publish(row.sessionId(), new DtEvent.FileArtifactArchiveRequested(
                    row.id(), row.sessionId(), parseKind(frontmatter).dbValue(),
                    frontmatter.getOrDefault("title", row.title()),
                    frontmatter.getOrDefault("summary", row.summary())));
        }
    }

    /**
     * Watcher DELETE: temporary → silent DELETE; candidate → DELETE + Discarded
     * event (implicit user discard via filesystem); archived → out of band
     * (archived files live under {@code workspaces/}, not watched).
     */
    public void recordDeleted(Path physicalPath) {
        Objects.requireNonNull(physicalPath, "physicalPath");
        String pathStr = physicalPath.toAbsolutePath().toString();
        Optional<FileArtifact> existing = repo.findByPhysicalPath(pathStr);
        if (existing.isEmpty()) {
            return;
        }
        FileArtifact row = existing.get();
        switch (row.status()) {
            case TEMPORARY -> repo.deleteById(row.id());
            case CANDIDATE -> {
                repo.deleteById(row.id());
                publish(row.sessionId(), new DtEvent.FileArtifactDiscarded(
                        row.id(), "watcher_delete"));
            }
            case ARCHIVED -> log.warn(
                    "file_artifact {} marked ARCHIVED but watcher saw DELETE under {} (path was: {})",
                    row.id(), workdir.root().sessionsRoot(), pathStr);
            case DISCARDED -> repo.deleteById(row.id());
        }
    }

    private void publish(String sessionId, DtEvent event) {
        if (sessionId == null) return;
        try {
            buses.getOrCreate(sessionId).publish(event);
        } catch (Exception e) {
            log.warn("publish event failed for session={}: {}", sessionId, e.toString());
        }
    }

    private static FileArtifactKind parseKind(Map<String, String> frontmatter) {
        String raw = frontmatter.get("kind");
        if (raw == null) return FileArtifactKind.OTHER;
        try {
            return FileArtifactKind.fromDb(raw.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FileArtifactKind.OTHER;
        }
    }

    private static String guessMime(Path file) {
        try {
            return Files.probeContentType(file);
        } catch (IOException e) {
            return null;
        }
    }

    private static Optional<PathSafetyError> rejectSymlinkSegments(Path base, Path requested) {
        Path current = base;
        for (Path segment : requested) {
            String name = segment.toString();
            if (".".equals(name) || name.isBlank()) {
                continue;
            }
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                return Optional.of(PathSafetyError.PATH_CONTAINS_SYMLINK);
            }
        }
        return Optional.empty();
    }

    private static boolean attributesChanged(BasicFileAttributes before, BasicFileAttributes after) {
        if (before.fileKey() == null || after.fileKey() == null) {
            return true;
        }
        return !Objects.equals(before.fileKey(), after.fileKey())
                || before.size() != after.size()
                || !Objects.equals(before.lastModifiedTime(), after.lastModifiedTime())
                || !Objects.equals(before.creationTime(), after.creationTime())
                || before.isRegularFile() != after.isRegularFile()
                || before.isDirectory() != after.isDirectory()
                || before.isSymbolicLink() != after.isSymbolicLink();
    }
}
```

### 9.2 单元测试扩展

- [x] 修改 `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceTest.java`：
  1. 把构造函数与 `@BeforeEach` 改为注入 mock 的 `SessionBusRegistry buses`，并 stub `buses.getOrCreate(any())` 返回 mock `SessionBus`，验证 `publish(...)` 被调用。
  2. 新增以下测试块（追加到现有测试之后，保持类闭合大括号在最末）：

```java
    // ─────────── watcher entry points (Part 2) ───────────

    @Test
    void recordDetected_inserts_temporary_when_no_artifact_declaration() throws Exception {
        Path file = sessionDir.resolve("foo.csv");
        java.nio.file.Files.writeString(file, "id,val\n1,2\n");
        when(repo.findByPhysicalPath(file.toAbsolutePath().toString())).thenReturn(Optional.empty());
        ArgumentCaptor<FileArtifact> ac = ArgumentCaptor.forClass(FileArtifact.class);
        com.datatalk.application.session.SessionBus bus = mock(com.datatalk.application.session.SessionBus.class);
        when(buses.getOrCreate("ses_abc")).thenReturn(bus);

        var inserted = svc.recordDetected("ses_abc", file, java.util.Map.of());

        verify(repo).insert(ac.capture());
        assertThat(ac.getValue().status()).isEqualTo(FileArtifactStatus.TEMPORARY);
        assertThat(ac.getValue().sessionId()).isEqualTo("ses_abc");
        assertThat(ac.getValue().filename()).isEqualTo("foo.csv");
        verify(bus).publish(any(com.datatalk.domain.event.DtEvent.FileArtifactDetected.class));
        assertThat(inserted).isPresent();
    }

    @Test
    void recordDetected_inserts_candidate_when_artifact_declared() throws Exception {
        Path file = sessionDir.resolve("orders-er.md");
        java.nio.file.Files.writeString(file, "---\nartifact: true\nkind: er_diagram\ntitle: T\n---\n");
        when(repo.findByPhysicalPath(file.toAbsolutePath().toString())).thenReturn(Optional.empty());
        com.datatalk.application.session.SessionBus bus = mock(com.datatalk.application.session.SessionBus.class);
        when(buses.getOrCreate("ses_abc")).thenReturn(bus);
        ArgumentCaptor<FileArtifact> ac = ArgumentCaptor.forClass(FileArtifact.class);

        svc.recordDetected("ses_abc", file, java.util.Map.of(
                "artifact", "true", "kind", "er_diagram", "title", "T"));

        verify(repo).insert(ac.capture());
        assertThat(ac.getValue().status()).isEqualTo(FileArtifactStatus.CANDIDATE);
        assertThat(ac.getValue().kind()).isEqualTo(com.datatalk.domain.fileartifact.FileArtifactKind.ER_DIAGRAM);
        assertThat(ac.getValue().title()).isEqualTo("T");
    }

    @Test
    void recordDetected_is_idempotent_when_row_already_exists() throws Exception {
        Path file = sessionDir.resolve("foo.csv");
        java.nio.file.Files.writeString(file, "x");
        FileArtifact existing = stub(FileArtifactStatus.TEMPORARY);
        when(repo.findByPhysicalPath(file.toAbsolutePath().toString())).thenReturn(Optional.of(existing));

        var result = svc.recordDetected("ses_abc", file, java.util.Map.of());

        verify(repo, org.mockito.Mockito.never()).insert(any());
        assertThat(result).contains(existing);
    }

    @Test
    void recordModified_promotes_temporary_to_candidate_when_frontmatter_appears() throws Exception {
        Path file = sessionDir.resolve("foo.md");
        java.nio.file.Files.writeString(file, "---\nartifact: true\n---\n");
        FileArtifact existing = new FileArtifact(
                "fid", FileArtifactScope.SESSION, FileArtifactStatus.TEMPORARY,
                com.datatalk.domain.fileartifact.FileArtifactKind.OTHER,
                "ses_abc", null, "foo.md", file.toAbsolutePath().toString(),
                10L, "text/markdown", null, null,
                Instant.now(), Instant.now(), null, java.util.Map.of());
        when(repo.findByPhysicalPath(file.toAbsolutePath().toString())).thenReturn(Optional.of(existing));
        com.datatalk.application.session.SessionBus bus = mock(com.datatalk.application.session.SessionBus.class);
        when(buses.getOrCreate("ses_abc")).thenReturn(bus);

        svc.recordModified(file, java.util.Map.of("artifact", "true", "kind", "report"));

        verify(repo).updateMetadata(eq("fid"), org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyLong());
        verify(repo).updateStatus("fid", FileArtifactStatus.CANDIDATE);
        verify(bus).publish(any(com.datatalk.domain.event.DtEvent.FileArtifactArchiveRequested.class));
    }

    @Test
    void recordModified_does_not_overwrite_already_candidate_metadata() throws Exception {
        Path file = sessionDir.resolve("foo.md");
        java.nio.file.Files.writeString(file, "x");
        FileArtifact existing = new FileArtifact(
                "fid", FileArtifactScope.SESSION, FileArtifactStatus.CANDIDATE,
                com.datatalk.domain.fileartifact.FileArtifactKind.REPORT,
                "ses_abc", null, "foo.md", file.toAbsolutePath().toString(),
                10L, "text/markdown", "OldTitle", "OldSummary",
                Instant.now(), Instant.now(), null, java.util.Map.of());
        when(repo.findByPhysicalPath(file.toAbsolutePath().toString())).thenReturn(Optional.of(existing));
        com.datatalk.application.session.SessionBus bus = mock(com.datatalk.application.session.SessionBus.class);
        when(buses.getOrCreate("ses_abc")).thenReturn(bus);

        svc.recordModified(file, java.util.Map.of("artifact", "true", "kind", "other", "title", "NewTitle"));

        verify(repo).updateMetadata(eq("fid"), org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyLong());
        verify(repo, org.mockito.Mockito.never()).updateStatus(any(), any());
    }

    @Test
    void recordDeleted_silently_deletes_temporary_row() {
        FileArtifact existing = stub(FileArtifactStatus.TEMPORARY);
        when(repo.findByPhysicalPath("/abs/x.md")).thenReturn(Optional.of(existing));
        com.datatalk.application.session.SessionBus bus = mock(com.datatalk.application.session.SessionBus.class);
        // bus may or may not be touched — for TEMPORARY no event is emitted.

        svc.recordDeleted(Path.of("/abs/x.md"));

        verify(repo).deleteById("fid");
    }

    @Test
    void recordDeleted_emits_discarded_event_for_candidate_row() {
        FileArtifact existing = stub(FileArtifactStatus.CANDIDATE);
        when(repo.findByPhysicalPath("/abs/x.md")).thenReturn(Optional.of(existing));
        com.datatalk.application.session.SessionBus bus = mock(com.datatalk.application.session.SessionBus.class);
        when(buses.getOrCreate("ses_abc")).thenReturn(bus);

        svc.recordDeleted(Path.of("/abs/x.md"));

        verify(repo).deleteById("fid");
        verify(bus).publish(any(com.datatalk.domain.event.DtEvent.FileArtifactDiscarded.class));
    }

    @Test
    void recordDeleted_is_noop_when_row_missing() {
        when(repo.findByPhysicalPath("/abs/missing.md")).thenReturn(Optional.empty());
        svc.recordDeleted(Path.of("/abs/missing.md"));
        verify(repo, org.mockito.Mockito.never()).deleteById(any());
    }
```

并在 `@BeforeEach` 之外的字段块新增：

```java
    com.datatalk.application.session.SessionBusRegistry buses;
```

`@BeforeEach` 内更新构造：

```java
        buses = mock(com.datatalk.application.session.SessionBusRegistry.class);
        svc = new FileArtifactService(repo, workdir, buses, new ObjectMapper());
```

### 9.3 验证

- [x] 跑测试：

```bash
cd server && mvn -pl data-talk-application test -Dtest=FileArtifactServiceTest -q
```

预期：现有 14 + 新增 8 = 22 测试通过。

---

## Task 10: Infrastructure — `MethvinArtifactWatcher` 实现

**目的：** 实现 application 层 `ArtifactWatcher` 端口；用 `io.methvin.directorywatcher.DirectoryWatcher` 做 recursive watch；不跟随 symlink；overflow 上抛。

### 10.1 实现

- [x] 创建 `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/MethvinArtifactWatcher.java`：

```java
package com.datatalk.infra.fileartifact;

import com.datatalk.application.fileartifact.ArtifactWatcher;
import com.datatalk.application.fileartifact.FileWatchEvent;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
- [x] * Production {@link ArtifactWatcher} backed by io.methvin DirectoryWatcher.
- [x] *
- [x] * <p>Configured with {@code fileHashing=false} (events fire on path changes
- [x] * without content hashing — trade smaller memory for occasional duplicate
- [x] * MODIFY which the application-side debouncer will absorb).
- [x] *
- [x] * <p>Symlinks are not followed: the underlying library walks the tree once at
- [x] * start, and subsequent emitted paths under symlinks are filtered out.
- [x] */
public class MethvinArtifactWatcher implements ArtifactWatcher {

    private static final Logger log = LoggerFactory.getLogger(MethvinArtifactWatcher.class);

    private DirectoryWatcher backing;
    private CompletableFuture<Void> watchFuture;
    private ScheduledExecutorService executor;
    private boolean started;

    @Override
    public synchronized void start(Path rootPath, Consumer<FileWatchEvent> listener) {
        if (started) {
            throw new IllegalStateException("already started");
        }
        try {
            Files.createDirectories(rootPath);
        } catch (IOException e) {
            throw new RuntimeException("failed to create watch root: " + rootPath, e);
        }
        AtomicInteger seq = new AtomicInteger();
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "artifact-watcher-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        try {
            this.backing = DirectoryWatcher.builder()
                    .path(rootPath)
                    .fileHashing(false)
                    .listener(event -> dispatch(event, listener))
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("failed to start directory watcher on " + rootPath, e);
        }
        this.watchFuture = backing.watchAsync(executor);
        this.started = true;
        log.info("artifact watcher started at {}", rootPath);
    }

    private static void dispatch(DirectoryChangeEvent event, Consumer<FileWatchEvent> listener) {
        Path p = event.path();
        // Reject events for symlinks themselves (spec §6.3 #7 & §5.4 #5).
        if (event.isDirectory()) {
            return;
        }
        if (Files.isSymbolicLink(p)) {
            return;
        }
        Instant now = Instant.now();
        switch (event.eventType()) {
            case CREATE -> listener.accept(new FileWatchEvent.Create(p, now));
            case MODIFY -> listener.accept(new FileWatchEvent.Modify(p, now));
            case DELETE -> listener.accept(new FileWatchEvent.Delete(p, now));
            case OVERFLOW -> listener.accept(new FileWatchEvent.Overflow(p, now));
        }
    }

    @Override
    public synchronized void close() {
        if (!started) {
            return;
        }
        started = false;
        try {
            if (backing != null) backing.close();
        } catch (IOException e) {
            log.warn("artifact watcher close failed: {}", e.toString());
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        if (watchFuture != null) {
            watchFuture.cancel(true);
        }
        log.info("artifact watcher closed");
    }
}
```

### 10.2 IT — 真实 watcher

- [x] 创建 `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/MethvinArtifactWatcherIT.java`：

```java
package com.datatalk.infra.fileartifact;

import com.datatalk.application.fileartifact.FileWatchEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;

class MethvinArtifactWatcherIT {

    @TempDir
    Path root;

    MethvinArtifactWatcher watcher;
    List<FileWatchEvent> events;

    @BeforeEach
    void setUp() {
        watcher = new MethvinArtifactWatcher();
        events = new CopyOnWriteArrayList<>();
    }

    @AfterEach
    void tearDown() {
        if (watcher != null) watcher.close();
    }

    @Test
    void emits_create_event_when_file_appears() throws Exception {
        watcher.start(root, events::add);
        Path nested = root.resolve("ses_abc");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("foo.csv"), "id,val\n1,2\n");

        await().atMost(ofSeconds(5)).until(() ->
                events.stream().anyMatch(e -> e instanceof FileWatchEvent.Create
                        && e.path().getFileName().toString().equals("foo.csv")));
    }

    @Test
    void emits_modify_event_when_file_content_changes() throws Exception {
        Path nested = root.resolve("ses_abc");
        Files.createDirectories(nested);
        Path file = nested.resolve("foo.md");
        Files.writeString(file, "v1");
        watcher.start(root, events::add);
        // Give the watcher a beat to settle initial CREATE replay.
        Thread.sleep(200);
        events.clear();
        Files.writeString(file, "v2");

        await().atMost(ofSeconds(5)).until(() ->
                events.stream().anyMatch(e -> e instanceof FileWatchEvent.Modify
                        && e.path().getFileName().toString().equals("foo.md")));
    }

    @Test
    void emits_delete_event_when_file_removed() throws Exception {
        Path nested = root.resolve("ses_abc");
        Files.createDirectories(nested);
        Path file = nested.resolve("foo.txt");
        Files.writeString(file, "x");
        watcher.start(root, events::add);
        Thread.sleep(200);
        events.clear();
        Files.delete(file);

        await().atMost(ofSeconds(5)).until(() ->
                events.stream().anyMatch(e -> e instanceof FileWatchEvent.Delete
                        && e.path().getFileName().toString().equals("foo.txt")));
    }

    @Test
    void start_is_not_reentrant() {
        watcher.start(root, events::add);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> watcher.start(root, events::add));
    }

    @Test
    void close_is_idempotent_without_start() {
        // Should not throw or hang.
        watcher.close();
        watcher.close();
    }

    @Test
    void symlink_paths_are_filtered_out() throws Exception {
        Path victim = root.resolve("victim.md");
        Files.writeString(victim, "secret");
        Path nested = root.resolve("ses_abc");
        Files.createDirectories(nested);
        watcher.start(root, events::add);
        Thread.sleep(200);
        events.clear();
        try {
            Files.createSymbolicLink(nested.resolve("link.md"), victim);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
            org.junit.jupiter.api.Assumptions.abort("symlink not supported on this filesystem");
            return;
        }
        // Brief wait — we *expect* no event for the symlink.
        Thread.sleep(800);
        assertThat(events).noneMatch(e -> e.path().getFileName().toString().equals("link.md"));
    }
}
```

注：`org.awaitility:awaitility` 在 Spring Boot starter test 里自带，无需额外依赖。

### 10.3 验证

- [x] 跑 IT：

```bash
cd server && mvn -pl data-talk-infrastructure -am test -Dtest=MethvinArtifactWatcherIT -q
```

预期：6 测试通过（`symlink_paths_are_filtered_out` 在不支持 symlink 的 FS 上 abort 视为 pass）。

---

## Task 11: Application — `ArtifactWatcherService` 编排器

**目的：** 接收 watcher 推上来的 `FileWatchEvent`，做：
1. **过滤**：路径必须在 `sessionsRoot` 下；段数必须 ≥ 2（即 `<sessionsRoot>/<sid>/<file>`）；文件名首字母不是 `.`；后缀不在忽略列表（`.tmp` / `.swp` / `.partial` / `.swo`）；`.meta.json` 直接忽略。
2. **debounce**：每 path × event-type 200ms 合并；mtime 稳定 ≥ 200ms 才视为"已写完"。
3. **dispatch**：CREATE → 解析 frontmatter → `service.recordDetected(...)`；MODIFY → 同上 → `service.recordModified(...)`；DELETE → `service.recordDeleted(...)`；RENAME → 视为 DELETE+CREATE；OVERFLOW → 调 `reconciler.reconcileSubtree(sessionsRoot)`。
4. **生命周期**：通过 `start()` 启动；watcher 实际启动由 `FileArtifactWatcherStartup` 在 `ApplicationReadyEvent` 后调用。

### 11.1 实现

- [x] 创建 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/ArtifactWatcherService.java`：

```java
package com.datatalk.application.fileartifact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
- [x] * Orchestrates the {@link ArtifactWatcher} feed: filter → debounce → dispatch.
- [x] *
- [x] * <p>Design notes:
- [x] * <ul>
- [x] *   <li>{@link #start} is called from {@code FileArtifactWatcherStartup} after
- [x] *       Spring is ready, so all dependencies are wired before watch begins.</li>
- [x] *   <li>Debouncing uses a single-threaded scheduler; each new event for the
- [x] *       same {@code (path, kind)} resets the timer to 200 ms.</li>
- [x] *   <li>The dispatch worker runs on the same scheduler — keeps event ordering
- [x] *       per path. Long IO (frontmatter parse, JDBC) runs there.</li>
- [x] * </ul>
- [x] */
@Service
public class ArtifactWatcherService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ArtifactWatcherService.class);

    private static final long DEBOUNCE_MS = 200L;
    private static final Set<String> IGNORED_SUFFIXES = Set.of(".tmp", ".swp", ".partial", ".swo");

    private final ArtifactWatcher watcher;
    private final FileArtifactService service;
    private final SessionWorkdirService workdir;
    private final FileArtifactReconciler reconciler;

    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<DebounceKey, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private volatile boolean started;

    public ArtifactWatcherService(ArtifactWatcher watcher,
                                  FileArtifactService service,
                                  SessionWorkdirService workdir,
                                  FileArtifactReconciler reconciler) {
        this.watcher = watcher;
        this.service = service;
        this.workdir = workdir;
        this.reconciler = reconciler;
        AtomicInteger seq = new AtomicInteger();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "file-artifact-watcher-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** Idempotent. Called from {@code FileArtifactWatcherStartup} after Spring is ready. */
    public synchronized void start() {
        if (started) return;
        Path root = workdir.root().sessionsRoot();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            log.error("failed to create sessions root {}: {}", root, e.toString());
            return;
        }
        watcher.start(root, this::onEvent);
        started = true;
        log.info("file artifact watcher orchestrator started on {}", root);
    }

    @Override
    public synchronized void close() {
        if (!started) return;
        started = false;
        watcher.close();
        scheduler.shutdownNow();
        pending.clear();
    }

    void onEvent(FileWatchEvent event) {
        switch (event) {
            case FileWatchEvent.Overflow ov -> {
                log.warn("watcher OVERFLOW at {}; triggering reconcile", ov.path());
                scheduler.execute(reconciler::runFullReconcile);
            }
            case FileWatchEvent.Create c -> debounce(c.path(), DispatchKind.CREATE);
            case FileWatchEvent.Modify m -> debounce(m.path(), DispatchKind.MODIFY);
            case FileWatchEvent.Delete d -> debounce(d.path(), DispatchKind.DELETE);
            case FileWatchEvent.Rename r -> {
                debounce(r.previousPath(), DispatchKind.DELETE);
                debounce(r.path(), DispatchKind.CREATE);
            }
        }
    }

    private void debounce(Path path, DispatchKind kind) {
        DebounceKey key = new DebounceKey(path, kind);
        ScheduledFuture<?> previous = pending.put(key,
                scheduler.schedule(() -> dispatch(key), DEBOUNCE_MS, TimeUnit.MILLISECONDS));
        if (previous != null) previous.cancel(false);
    }

    private void dispatch(DebounceKey key) {
        pending.remove(key);
        Path path = key.path();
        if (!isPathRelevant(path)) {
            return;
        }
        String sessionId = inferSessionId(path);
        if (sessionId == null) {
            // path was under sessions/ but at depth 1 (a file directly under sessions/),
            // which is a violation — ignore here, reconciler may clean up later.
            return;
        }
        try {
            switch (key.kind()) {
                case CREATE -> {
                    if (!Files.exists(path)) return;
                    if (!isStable(path)) {
                        // Reschedule once with the same key.
                        debounce(path, DispatchKind.CREATE);
                        return;
                    }
                    Map<String, String> fm = FrontmatterParser.parse(path);
                    service.recordDetected(sessionId, path, fm);
                }
                case MODIFY -> {
                    if (!Files.exists(path)) return;
                    if (!isStable(path)) {
                        debounce(path, DispatchKind.MODIFY);
                        return;
                    }
                    Map<String, String> fm = FrontmatterParser.parse(path);
                    service.recordModified(path, fm);
                }
                case DELETE -> service.recordDeleted(path);
            }
        } catch (Exception e) {
            log.warn("dispatch {} {} failed: {}", key.kind(), path, e.toString());
        }
    }

    private boolean isPathRelevant(Path path) {
        Path sessionsRoot = workdir.root().sessionsRoot().toAbsolutePath().normalize();
        Path abs = path.toAbsolutePath().normalize();
        if (!abs.startsWith(sessionsRoot)) return false;
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        if (name.isEmpty() || name.startsWith(".")) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String suf : IGNORED_SUFFIXES) {
            if (lower.endsWith(suf)) return false;
        }
        return true;
    }

    /** {@code <sessionsRoot>/<sessionId>/...} → returns sessionId; null at root depth or above. */
    String inferSessionId(Path path) {
        Path sessionsRoot = workdir.root().sessionsRoot().toAbsolutePath().normalize();
        Path abs = path.toAbsolutePath().normalize();
        Path relative = sessionsRoot.relativize(abs);
        if (relative.getNameCount() < 2) return null;
        return relative.getName(0).toString();
    }

    /** mtime stable for at least DEBOUNCE_MS — used to avoid 0-byte zombies. */
    private static boolean isStable(Path path) {
        try {
            long mtime = Files.getLastModifiedTime(path).toMillis();
            long age = System.currentTimeMillis() - mtime;
            return age >= DEBOUNCE_MS;
        } catch (IOException e) {
            return false;
        }
    }

    private enum DispatchKind { CREATE, MODIFY, DELETE }

    private record DebounceKey(Path path, DispatchKind kind) {}
}
```

### 11.2 单元测试

- [x] 创建 `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/ArtifactWatcherServiceTest.java`：

```java
package com.datatalk.application.fileartifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static java.time.Duration.ofSeconds;

class ArtifactWatcherServiceTest {

    @TempDir
    Path tmp;

    SessionWorkdirRoot root;
    SessionWorkdirService workdir;
    FileArtifactService service;
    FileArtifactReconciler reconciler;
    ArtifactWatcher watcher;
    ArtifactWatcherService orchestrator;
    AtomicReference<Consumer<FileWatchEvent>> capturedListener;

    @BeforeEach
    void setUp() throws Exception {
        root = new SessionWorkdirRoot(tmp, tmp.resolve("opencode"));
        workdir = new SessionWorkdirService(root, new ObjectMapper());
        Files.createDirectories(root.sessionsRoot());
        service = mock(FileArtifactService.class);
        reconciler = mock(FileArtifactReconciler.class);
        watcher = mock(ArtifactWatcher.class);
        capturedListener = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<FileWatchEvent> l = (Consumer<FileWatchEvent>) inv.getArgument(1);
            capturedListener.set(l);
            return null;
        }).when(watcher).start(any(), any());
        orchestrator = new ArtifactWatcherService(watcher, service, workdir, reconciler);
        orchestrator.start();
    }

    @Test
    void create_event_dispatches_recordDetected_with_session_id() throws Exception {
        Path session = root.sessionDir("ses_abc");
        Files.createDirectories(session);
        Path file = session.resolve("foo.csv");
        Files.writeString(file, "x");
        // Force mtime to be old enough that isStable returns true.
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(2)));

        capturedListener.get().accept(new FileWatchEvent.Create(file, Instant.now()));

        verify(service, timeout(2000)).recordDetected(eq("ses_abc"), eq(file), any());
    }

    @Test
    void modify_event_dispatches_recordModified() throws Exception {
        Path session = root.sessionDir("ses_x");
        Files.createDirectories(session);
        Path file = session.resolve("foo.md");
        Files.writeString(file, "x");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(2)));

        capturedListener.get().accept(new FileWatchEvent.Modify(file, Instant.now()));

        verify(service, timeout(2000)).recordModified(eq(file), any());
    }

    @Test
    void delete_event_dispatches_recordDeleted_even_when_file_absent() {
        Path file = root.sessionDir("ses_x").resolve("ghost.csv");
        capturedListener.get().accept(new FileWatchEvent.Delete(file, Instant.now()));
        verify(service, timeout(2000)).recordDeleted(file);
    }

    @Test
    void overflow_event_triggers_full_reconcile() {
        capturedListener.get().accept(new FileWatchEvent.Overflow(root.sessionsRoot(), Instant.now()));
        verify(reconciler, timeout(2000)).runFullReconcile();
    }

    @Test
    void rename_dispatches_delete_then_create() throws Exception {
        Path session = root.sessionDir("ses_y");
        Files.createDirectories(session);
        Path oldP = session.resolve("a.md");
        Path newP = session.resolve("b.md");
        Files.writeString(newP, "x");
        Files.setLastModifiedTime(newP, java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(2)));

        capturedListener.get().accept(new FileWatchEvent.Rename(newP, oldP, Instant.now()));

        verify(service, timeout(2000)).recordDeleted(oldP);
        verify(service, timeout(2000)).recordDetected(eq("ses_y"), eq(newP), any());
    }

    @Test
    void path_directly_under_sessions_root_is_ignored() throws Exception {
        Path orphan = root.sessionsRoot().resolve("orphan.md");
        Files.writeString(orphan, "x");
        Files.setLastModifiedTime(orphan, java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(2)));

        capturedListener.get().accept(new FileWatchEvent.Create(orphan, Instant.now()));

        // Wait past debounce window then verify nothing fired.
        Thread.sleep(500);
        verify(service, never()).recordDetected(any(), any(), any());
    }

    @Test
    void hidden_files_are_ignored() throws Exception {
        Path session = root.sessionDir("ses_x");
        Files.createDirectories(session);
        Path hidden = session.resolve(".meta.json");
        Files.writeString(hidden, "{}");

        capturedListener.get().accept(new FileWatchEvent.Create(hidden, Instant.now()));
        Thread.sleep(500);
        verify(service, never()).recordDetected(any(), any(), any());
    }

    @Test
    void tmp_suffix_files_are_ignored() throws Exception {
        Path session = root.sessionDir("ses_x");
        Files.createDirectories(session);
        Path partial = session.resolve("foo.csv.partial");
        Files.writeString(partial, "x");

        capturedListener.get().accept(new FileWatchEvent.Create(partial, Instant.now()));
        Thread.sleep(500);
        verify(service, never()).recordDetected(any(), any(), any());
    }

    @Test
    void inferSessionId_returns_first_segment_after_sessions_root() {
        Path expected = root.sessionDir("ses_target").resolve("sub").resolve("file.md");
        assertThat(orchestrator.inferSessionId(expected)).isEqualTo("ses_target");
    }

    @Test
    void debounced_repeated_modifies_collapse_to_one_dispatch() throws Exception {
        Path session = root.sessionDir("ses_x");
        Files.createDirectories(session);
        Path file = session.resolve("foo.md");
        Files.writeString(file, "x");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(2)));
        // Fire 5 modify events within the debounce window.
        for (int i = 0; i < 5; i++) {
            capturedListener.get().accept(new FileWatchEvent.Modify(file, Instant.now()));
            Thread.sleep(20);
        }
        // Wait for debounce to flush.
        await().atMost(ofSeconds(2)).untilAsserted(() ->
                verify(service, org.mockito.Mockito.times(1)).recordModified(eq(file), any()));
    }
}
```

### 11.3 验证

- [x] 跑测试：

```bash
cd server && mvn -pl data-talk-application test -Dtest=ArtifactWatcherServiceTest -q
```

预期：10 个测试通过。

---

## Task 12: Application — `FileArtifactReconciler`

**目的：** Spec §6.4 的 `reconcileFileArtifacts`。
- **孤儿文件**（FS 有 / DB 无）：在 `sessions/<sid>/<file>` 中发现一个无对应行的真实文件 → 调 `service.recordDetected(sid, path, frontmatter)` 补登记为 TEMPORARY。
- **孤儿行**（DB 有 / FS 无）：
  - `temporary` 行的文件不在 → 静默 `repo.deleteById(...)`。
  - `candidate` 行的文件不在 → `repo.deleteById(...)` + 发 `FileArtifactDiscarded("reconcile")`。
  - `archived` 行的文件不在 `workspaces/<connId>/` → `repo.deleteById(...)` + WARN 日志（spec 说"发警告 DtEvent"，但本设计未为 archived 缺失专门定义 DtEvent；统一用 `FileArtifactDiscarded("reconcile")` 配合 WARN 也合规，spec §6.4 同意此通用化）。
- **孤儿 cwd**（FS 有 `sessions/<sid>/`，DB 无该 session）：本 Part 暂不强制清理（删除有副作用，需要 SessionRepository 查询，留给 Part 5 的 HousekeepingScheduler）。仅记一条 INFO 日志 `orphan_session_dir <sid>` 供运维观察。

不扫 `_legacy` / `_trash` / `opencode/` 根（spec §6.4）。

### 12.1 实现

- [x] 创建 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactReconciler.java`：

```java
package com.datatalk.application.fileartifact;

import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
- [x] * Brings the SQLite {@code file_artifact} index back in line with the
- [x] * filesystem state under {@code sessions/<sid>/} and {@code workspaces/<cid>/}.
- [x] *
- [x] * <p>Invocation points (Part 2):
- [x] * <ul>
- [x] *   <li>{@code FileArtifactWatcherStartup} on Spring {@code ApplicationReadyEvent}.</li>
- [x] *   <li>{@code ArtifactWatcherService} on watcher OVERFLOW.</li>
- [x] * </ul>
- [x] *
- [x] * <p>Part 5 will additionally schedule {@link #runFullReconcile} on a daily cron
- [x] * via {@code HousekeepingScheduler}.
- [x] */
@Component
public class FileArtifactReconciler {

    private static final Logger log = LoggerFactory.getLogger(FileArtifactReconciler.class);

    private static final Set<String> IGNORED_SUFFIXES = Set.of(".tmp", ".swp", ".partial", ".swo");

    private final FileArtifactRepository repo;
    private final FileArtifactService service;
    private final SessionWorkdirService workdir;
    private final SessionBusRegistry buses;

    public FileArtifactReconciler(FileArtifactRepository repo,
                                  FileArtifactService service,
                                  SessionWorkdirService workdir,
                                  SessionBusRegistry buses) {
        this.repo = repo;
        this.service = service;
        this.workdir = workdir;
        this.buses = buses;
    }

    /** Full reconcile: sessions/* + workspaces/*. Logs counts for observability. */
    public synchronized void runFullReconcile() {
        long started = System.currentTimeMillis();
        Stats sessionStats = reconcileSessionsTree();
        Stats workspaceStats = reconcileWorkspacesTree();
        log.info("file_artifact reconcile done in {}ms — sessions: {} | workspaces: {}",
                System.currentTimeMillis() - started, sessionStats, workspaceStats);
    }

    private Stats reconcileSessionsTree() {
        Path root = workdir.root().sessionsRoot();
        if (!Files.isDirectory(root)) {
            return new Stats(0, 0, 0);
        }
        // 1. Snapshot DB rows by physical_path.
        List<FileArtifact> dbRows = repo.findAllSessionScoped();
        Map<String, FileArtifact> byPath = dbRows.stream()
                .collect(java.util.stream.Collectors.toMap(
                        FileArtifact::physicalPath, r -> r, (a, b) -> a));

        // 2. Walk FS once. For each candidate file, ensure a row exists.
        int orphanFilesAdopted = 0;
        Set<String> seen = new HashSet<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (!isAdoptableFile(p, root)) continue;
                String abs = p.toAbsolutePath().toString();
                seen.add(abs);
                if (byPath.containsKey(abs)) continue;
                String sid = sessionIdOf(p, root);
                if (sid == null) continue;
                Map<String, String> fm = FrontmatterParser.parse(p);
                if (service.recordDetected(sid, p, fm).isPresent()) {
                    orphanFilesAdopted++;
                }
            }
        } catch (IOException e) {
            log.warn("reconcile sessions walk failed: {}", e.toString());
        }

        // 3. Detect orphan rows (file gone).
        int rowsDeleted = 0;
        int rowsDiscarded = 0;
        for (FileArtifact row : dbRows) {
            if (seen.contains(row.physicalPath())) continue;
            switch (row.status()) {
                case TEMPORARY -> {
                    repo.deleteById(row.id());
                    rowsDeleted++;
                }
                case CANDIDATE -> {
                    repo.deleteById(row.id());
                    if (row.sessionId() != null) {
                        try {
                            buses.getOrCreate(row.sessionId()).publish(
                                    new DtEvent.FileArtifactDiscarded(row.id(), "reconcile"));
                        } catch (Exception e) {
                            log.debug("publish discarded failed: {}", e.toString());
                        }
                    }
                    rowsDiscarded++;
                }
                case ARCHIVED -> {
                    log.warn("file_artifact {} ARCHIVED but file missing under sessions/ (path was: {})",
                            row.id(), row.physicalPath());
                }
                case DISCARDED -> {
                    repo.deleteById(row.id());
                    rowsDeleted++;
                }
            }
        }

        return new Stats(orphanFilesAdopted, rowsDeleted, rowsDiscarded);
    }

    private Stats reconcileWorkspacesTree() {
        Path root = workdir.root().workspacesRoot();
        if (!Files.isDirectory(root)) {
            return new Stats(0, 0, 0);
        }
        // For Part 2 we only check orphan archived rows; orphan files in workspaces
        // are treated as user-managed (Part 5 handles inverse adoption).
        List<FileArtifact> dbRows = repo.findAllWorkspaceScopedArchived();
        int rowsDeleted = 0;
        for (FileArtifact row : dbRows) {
            Path p = Path.of(row.physicalPath());
            if (Files.exists(p)) continue;
            log.warn("file_artifact {} ARCHIVED row missing on disk: {}", row.id(), row.physicalPath());
            repo.deleteById(row.id());
            if (row.sessionId() != null) {
                try {
                    buses.getOrCreate(row.sessionId()).publish(
                            new DtEvent.FileArtifactDiscarded(row.id(), "reconcile"));
                } catch (Exception e) {
                    log.debug("publish discarded failed: {}", e.toString());
                }
            }
            rowsDeleted++;
        }
        return new Stats(0, rowsDeleted, 0);
    }

    private boolean isAdoptableFile(Path p, Path sessionsRoot) {
        if (!Files.isRegularFile(p)) return false;
        if (Files.isSymbolicLink(p)) return false;
        String name = p.getFileName().toString();
        if (name.startsWith(".")) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String suf : IGNORED_SUFFIXES) {
            if (lower.endsWith(suf)) return false;
        }
        return sessionIdOf(p, sessionsRoot) != null;
    }

    private static String sessionIdOf(Path p, Path sessionsRoot) {
        Path abs = p.toAbsolutePath().normalize();
        Path rel = sessionsRoot.toAbsolutePath().normalize().relativize(abs);
        if (rel.getNameCount() < 2) return null;
        String sid = rel.getName(0).toString();
        if (sid.startsWith("_") || sid.startsWith(".")) return null;
        return sid;
    }

    /** Small bag for log lines. */
    private record Stats(int adopted, int rowsDeleted, int rowsDiscarded) {}
}
```

### 12.2 单元测试

- [x] 创建 `server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactReconcilerTest.java`：

```java
package com.datatalk.application.fileartifact;

import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileArtifactReconcilerTest {

    @TempDir
    Path tmp;

    SessionWorkdirRoot root;
    SessionWorkdirService workdir;
    FileArtifactRepository repo;
    FileArtifactService service;
    SessionBusRegistry buses;
    SessionBus bus;
    FileArtifactReconciler reconciler;

    @BeforeEach
    void setUp() throws Exception {
        root = new SessionWorkdirRoot(tmp, tmp.resolve("opencode"));
        workdir = new SessionWorkdirService(root, new ObjectMapper());
        Files.createDirectories(root.sessionsRoot());
        Files.createDirectories(root.workspacesRoot());
        repo = mock(FileArtifactRepository.class);
        service = mock(FileArtifactService.class);
        buses = mock(SessionBusRegistry.class);
        bus = mock(SessionBus.class);
        when(buses.getOrCreate(any())).thenReturn(bus);
        reconciler = new FileArtifactReconciler(repo, service, workdir, buses);
    }

    @Test
    void orphan_file_under_session_dir_is_adopted_as_temporary() throws Exception {
        Path session = root.sessionDir("ses_x");
        Files.createDirectories(session);
        Path file = session.resolve("foo.csv");
        Files.writeString(file, "id,val\n1,2\n");
        when(repo.findAllSessionScoped()).thenReturn(List.of());
        when(repo.findAllWorkspaceScopedArchived()).thenReturn(List.of());
        when(service.recordDetected(eq("ses_x"), eq(file), any())).thenReturn(Optional.of(stub("a1")));

        reconciler.runFullReconcile();

        verify(service).recordDetected(eq("ses_x"), eq(file), any());
    }

    @Test
    void orphan_temporary_row_is_silently_deleted() {
        FileArtifact row = stubAt("a1", FileArtifactStatus.TEMPORARY,
                root.sessionDir("ses_x").resolve("missing.csv").toAbsolutePath().toString());
        when(repo.findAllSessionScoped()).thenReturn(List.of(row));
        when(repo.findAllWorkspaceScopedArchived()).thenReturn(List.of());

        reconciler.runFullReconcile();

        verify(repo).deleteById("a1");
        verify(bus, never()).publish(any());
    }

    @Test
    void orphan_candidate_row_emits_discarded_event() {
        FileArtifact row = stubAt("a1", FileArtifactStatus.CANDIDATE,
                root.sessionDir("ses_x").resolve("missing.md").toAbsolutePath().toString());
        when(repo.findAllSessionScoped()).thenReturn(List.of(row));
        when(repo.findAllWorkspaceScopedArchived()).thenReturn(List.of());

        reconciler.runFullReconcile();

        verify(repo).deleteById("a1");
        verify(bus).publish(any(DtEvent.FileArtifactDiscarded.class));
    }

    @Test
    void orphan_workspace_archived_row_is_deleted_and_logged() {
        FileArtifact row = new FileArtifact("w1", FileArtifactScope.WORKSPACE,
                FileArtifactStatus.ARCHIVED, FileArtifactKind.REPORT,
                "ses_x", "conn_p", "x.md",
                root.workspaceDir("conn_p").resolve("missing.md").toAbsolutePath().toString(),
                10L, "text/markdown", null, null,
                Instant.now(), Instant.now(), Instant.now(), Map.of());
        when(repo.findAllSessionScoped()).thenReturn(List.of());
        when(repo.findAllWorkspaceScopedArchived()).thenReturn(List.of(row));

        reconciler.runFullReconcile();

        verify(repo).deleteById("w1");
    }

    @Test
    void hidden_files_and_underscore_dirs_are_skipped() throws Exception {
        Path session = root.sessionDir("ses_x");
        Files.createDirectories(session);
        Files.writeString(session.resolve(".meta.json"), "{}");
        Files.createDirectories(root.sessionsRoot().resolve("_archive"));
        Files.writeString(root.sessionsRoot().resolve("_archive").resolve("ignored.md"), "x");
        when(repo.findAllSessionScoped()).thenReturn(List.of());
        when(repo.findAllWorkspaceScopedArchived()).thenReturn(List.of());

        reconciler.runFullReconcile();

        verify(service, never()).recordDetected(any(), any(), any());
    }

    @Test
    void file_directly_under_sessions_root_is_skipped() throws Exception {
        Path orphan = root.sessionsRoot().resolve("orphan.md");
        Files.writeString(orphan, "x");
        when(repo.findAllSessionScoped()).thenReturn(List.of());
        when(repo.findAllWorkspaceScopedArchived()).thenReturn(List.of());

        reconciler.runFullReconcile();

        verify(service, never()).recordDetected(any(), any(), any());
    }

    private static FileArtifact stub(String id) {
        Instant now = Instant.now();
        return new FileArtifact(id, FileArtifactScope.SESSION, FileArtifactStatus.TEMPORARY,
                FileArtifactKind.OTHER, "ses_x", null, "x.md", "/abs/x.md",
                10L, "text/markdown", null, null, now, now, null, Map.of());
    }

    private static FileArtifact stubAt(String id, FileArtifactStatus status, String path) {
        Instant now = Instant.now();
        return new FileArtifact(id, FileArtifactScope.SESSION, status,
                FileArtifactKind.OTHER, "ses_x", null,
                Path.of(path).getFileName().toString(), path,
                10L, "text/markdown", null, null, now, now, null, Map.of());
    }
}
```

### 12.3 验证

- [x] 跑测试：

```bash
cd server && mvn -pl data-talk-application test -Dtest=FileArtifactReconcilerTest -q
```

预期：6 测试通过。

---

## Task 13: Application — `FileArtifactWatcherStartup`（生命周期 wiring）

**目的：** Spring `ApplicationReadyEvent` 触发后启动 watcher 与跑一次 startup reconcile。在 `@PreDestroy` 阶段优雅关闭 watcher。

### 13.1 实现

- [x] 创建 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactWatcherStartup.java`：

```java
package com.datatalk.application.fileartifact;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
- [x] * Wires the watcher orchestrator + initial reconcile into the Spring lifecycle.
- [x] *
- [x] * <p>Order:
- [x] * <ol>
- [x] *   <li>Spring container ready → run startup reconcile (catches drift while
- [x] *       the app was offline).</li>
- [x] *   <li>Start the {@link ArtifactWatcherService} which begins recursive watch on
- [x] *       {@code sessions/} for live events.</li>
- [x] * </ol>
- [x] *
- [x] * <p>Reverse order on shutdown: stop watcher first (avoid event arrival during
- [x] * shutdown), then no reconcile needed (DB will be re-checked on next startup).
- [x] */
@Component
public class FileArtifactWatcherStartup {

    private static final Logger log = LoggerFactory.getLogger(FileArtifactWatcherStartup.class);

    private final ArtifactWatcherService orchestrator;
    private final FileArtifactReconciler reconciler;

    public FileArtifactWatcherStartup(ArtifactWatcherService orchestrator,
                                      FileArtifactReconciler reconciler) {
        this.orchestrator = orchestrator;
        this.reconciler = reconciler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            log.info("file artifact: running startup reconcile");
            reconciler.runFullReconcile();
        } catch (Exception e) {
            log.warn("file artifact startup reconcile failed: {}", e.toString());
        }
        try {
            orchestrator.start();
        } catch (Exception e) {
            log.error("file artifact watcher start failed: {}", e.toString(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            orchestrator.close();
        } catch (Exception e) {
            log.warn("file artifact watcher close failed: {}", e.toString());
        }
    }
}
```

### 13.2 注册 `ArtifactWatcher` bean

`MethvinArtifactWatcher` 在 infrastructure 层；Spring 默认只扫 `com.datatalk.adapter` 与组件下游包。需要确保 infra 也被 component-scan 拾到（项目惯例已经如此 —— 看 Part 1 `JdbcFileArtifactRepository` 直接 `@Repository` 即生效）。

但 `MethvinArtifactWatcher` 没加 `@Component`（实现是 plain class，便于多次构造在测试中）。我们用 `@Bean` 显式注册：

- [x] 修改 `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactConfiguration.java`，把它扩展为：

```java
package com.datatalk.application.fileartifact;

import com.datatalk.application.stage.ActiveSessionDirProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Configuration
public class FileArtifactConfiguration {

    @Bean
    public SessionWorkdirRoot sessionWorkdirRoot(
            @Value("${datatalk.workdir.data-talk-root:#{systemProperties['user.home']}/.data-talk}") String dataTalkRoot
    ) {
        Path root = Paths.get(dataTalkRoot);
        return new SessionWorkdirRoot(root, root.resolve("opencode"));
    }

    @Bean
    @ConditionalOnMissingBean
    public ActiveSessionDirProvider defaultActiveSessionDirProvider() {
        return () -> Optional.empty();
    }
}
```

并在 `infrastructure` 模块新建 `WatcherConfiguration` 注册 `MethvinArtifactWatcher` bean —— infra 持有第三方依赖，bean 注册放 infra 更符合分层：

- [x] 创建 `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/WatcherConfiguration.java`：

```java
package com.datatalk.infra.fileartifact;

import com.datatalk.application.fileartifact.ArtifactWatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WatcherConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public ArtifactWatcher artifactWatcher() {
        return new MethvinArtifactWatcher();
    }
}
```

### 13.3 编译

- [x] 编译验证：

```bash
cd server && mvn compile -q -pl data-talk-application && \
            mvn compile -q -pl data-talk-infrastructure
```

预期：零错误。

---

## Task 14: Adapter — 端到端 IT

**目的：** 用 SpringBootTest 启完整容器（H2 + Flyway），用临时目录覆盖 `datatalk.workdir.data-talk-root`，写一个文件到 `sessions/<sid>/`，验证：
1. `file_artifact` 行被 watcher 写入。
2. 行的 status 与 frontmatter 一致。

- [x] 创建 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/fileartifact/FileArtifactWatcherE2EIT.java`：

```java
package com.datatalk.adapter.fileartifact;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.fileartifact.SessionWorkdirService;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;

@SpringBootTest
@ActiveProfiles("test")
class FileArtifactWatcherE2EIT {

    @TempDir
    static Path workdirRoot;

    @DynamicPropertySource
    static void overrideWorkdir(DynamicPropertyRegistry registry) {
        registry.add("datatalk.workdir.data-talk-root", () -> workdirRoot.toString());
    }

    @Autowired
    FileArtifactRepository repo;

    @Autowired
    SessionWorkdirService workdir;

    @Test
    void csv_dropped_into_session_subdir_appears_as_temporary_row() throws Exception {
        Path session = workdir.getOrCreate("ses_e2e", "conn_test");
        Path file = session.resolve("sample.csv");
        Files.writeString(file, "id,val\n1,2\n");
        // Force mtime in the past so isStable is true after one debounce cycle.
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.from(
                java.time.Instant.now().minusSeconds(2)));

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            List<FileArtifact> rows = repo.findBySession("ses_e2e");
            assertThat(rows).extracting(FileArtifact::filename).contains("sample.csv");
            assertThat(rows.stream().filter(r -> r.filename().equals("sample.csv")).findFirst()
                    .orElseThrow().status()).isEqualTo(FileArtifactStatus.TEMPORARY);
        });
    }

    @Test
    void md_with_frontmatter_lands_as_candidate() throws Exception {
        Path session = workdir.getOrCreate("ses_fm", "conn_test");
        Path file = session.resolve("orders-er.md");
        Files.writeString(file, "---\nartifact: true\nkind: er_diagram\ntitle: T\n---\n\n# body\n");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.from(
                java.time.Instant.now().minusSeconds(2)));

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            FileArtifact row = repo.findBySession("ses_fm").stream()
                    .filter(r -> r.filename().equals("orders-er.md"))
                    .findFirst()
                    .orElseThrow();
            assertThat(row.status()).isEqualTo(FileArtifactStatus.CANDIDATE);
            assertThat(row.title()).isEqualTo("T");
        });
    }
}
```

- [x] 跑 IT：

```bash
cd server && mvn -pl data-talk-adapter -am verify -Dit.test=FileArtifactWatcherE2EIT -DfailIfNoTests=false -q
```

预期：2 测试通过。如果在某些 CI 下 io.methvin 在 tmp 目录上的初始化耗时较长，可以把 `await().atMost` 升到 20s（实际本地预期 < 5s）。

---

## Task 15: 完整回归 + 推 jar

CLAUDE.md "Backend Run vs Compile" 要求每次跨模块改动都要 `mvn install` 推到本地 jar。

### 15.1 推 jar

- [x] 推 application 与 infrastructure：

```bash
cd server && mvn install -pl data-talk-application -am -DskipTests -q && \
            mvn install -pl data-talk-infrastructure -am -DskipTests -q
```

### 15.2 完整 verify

- [x] 跑全量 verify，确保无回归（包括 Part 1 的所有测试，FlywayMigrationIT、JdbcFileArtifactRepositoryIT、FileArtifactControllerIT 等）：

```bash
cd server && mvn clean verify -q
```

预期：BUILD SUCCESS；总测试数较 Part 1 完成时新增大约 30+ 条；现有 V1-V14 migration 测试与 `artifacts`（payload 型）相关测试均不受影响。

### 15.3 commit

- [x] commit 第二批改动：

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/ArtifactWatcherService.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactReconciler.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactWatcherStartup.java \
        server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactConfiguration.java \
        server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/ArtifactWatcherServiceTest.java \
        server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactReconcilerTest.java \
        server/data-talk-application/src/test/java/com/datatalk/application/fileartifact/FileArtifactServiceTest.java \
        server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/MethvinArtifactWatcher.java \
        server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/WatcherConfiguration.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/fileartifact/MethvinArtifactWatcherIT.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/fileartifact/FileArtifactWatcherE2EIT.java
git commit -m "feat(application,infra,adapter): wire io.methvin watcher and DB reconciler for file artifacts"
```

---

## Task 16: 文档 housekeeping + 最终 commit

CLAUDE.md "Post-Execution Document Housekeeping" 强制：所有 task 完成后必须更新 index 与 spec。

### 16.1 把 index 行从 Active 移到 Completed

- [x] 修改 `docs/exec-plans/index.md`：
  - 在「活跃计划」表格中**删除** Part 2 那行（已经在 Task 1 替换为正式 link）。
  - 在「已完成计划」表格头部（按日期降序的合适位置）**插入**：

```markdown
| [File Artifact System · Part 2 — Watcher & Reconcile](./2026-04-30-file-artifact-system-part2-watcher-reconcile-plan.md) | 2026-04-30 | OpenCode 工作目录与 File Artifact 系统 Part 2 完成：io.methvin DirectoryWatcher 接入（debounce 200ms、symlink 拒绝、CREATE/MODIFY/DELETE/RENAME/OVERFLOW 精确规约）；`FrontmatterParser`（首 8KB，`.md`/`.sql`/`.txt`，识别 `artifact: true` 自动入库 CANDIDATE）；`FileArtifactReconciler`（startup + OVERFLOW，仅扫 `sessions/*` 与 `workspaces/*`，不动 `_legacy`/`_trash`/`opencode/` 根；孤儿文件→TEMPORARY 补登记，孤儿 candidate 行→DELETE+`FileArtifactDiscarded`）；`FileArtifactWatcherStartup` 接 `ApplicationReadyEvent`；端到端 SpringBootTest IT 与单元测试覆盖通过。Part 5 HousekeepingScheduler 之后会复用 reconciler 做每日定时与 `_trash` 7 天清理。 |
```

### 16.2 更新 spec 完成度快照

- [x] 修改 `docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md`：在文末「**当前完成度快照（2026-04-30）**」段落更新：
  1. 把第 1 项改为：`已完成：Part 1 (Migration & Domain) + Part 2 (Watcher & Reconcile) — Part 2 接入 io.methvin DirectoryWatcher、FrontmatterParser、FileArtifactReconciler 与 FileArtifactWatcherStartup；端到端 IT 通过。`
  2. 把第 2 项改为：`未开始正式计划：Part 3 (MCP + AGENTS template)、Part 4 (frontend files tabs)、Part 5 (deletion flow + housekeeping)。`

### 16.3 更新 Roadmap Task 11 状态

- [x] 修改 `docs/exec-plans/2026-04-25-next-implementation-roadmap-plan.md`：找到 Task 11 章节中关于 File Artifact System 的现状描述（Part 1 完成、Part 2-5 待补正式计划），把 Part 2 状态从「待补正式计划」改为「已完成」，并更新摘要为 `Part 1+2 已完成；Part 3-5 待补正式计划`（具体行号见 `:395`）。

### 16.4 标记本计划所有 task 完成

- [x] 把本计划文件（`2026-04-30-file-artifact-system-part2-watcher-reconcile-plan.md`）每个 `- [ ]` 改为 `- [x]`；如有偏差，在 task 末尾加 `> 偏差：...` 注解。

### 16.5 final commit

- [x] commit：

```bash
git add docs/exec-plans/2026-04-30-file-artifact-system-part2-watcher-reconcile-plan.md \
        docs/exec-plans/index.md \
        docs/exec-plans/2026-04-25-next-implementation-roadmap-plan.md \
        docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md
git commit -m "docs: mark file artifact system part 2 complete and move to Completed index"
```

> 偏差：本轮按用户要求使用 1 个 subagent 执行，写代码阶段未跑编译，最后统一验证。子代理提前创建了单个提交 `850a923 feat: add artifact watcher and improve er designer`，其中包含本 Part 2 server 改动以及当时工作区已有的 ER 相关改动；收尾阶段未回滚这些既有改动，只追加最小修正与 housekeeping。验证期额外修正了四处：`FileArtifactWatcherStartup` 使用 Spring `DisposableBean` 替代 `@PreDestroy`，避免 application 模块新增 `jakarta.annotation-api`；`ArtifactWatcherService` 对 `OVERFLOW` 同步调用 `runFullReconcile()`，符合本计划“OVERFLOW 直接触发 full reconcile”的边界；`DiscoveryControllerIT` 的 `datatalk.ui.read` 描述期望同步到已存在的 ER inspector/designer action 文案。`FileArtifactWatcherE2EIT` 的 Awaitility 断言改为先 assert Optional 存在再读取，避免空 Optional 在轮询中直接抛出非断言异常。

---

## Self-Review Checklist (执行前/执行中检查)

- [x] **Spec 覆盖**：
  - §3.1 子目录软隔离（基线 watcher 监听 `sessions/`）→ Tasks 4/10/11
  - §5.6 watcher 事件 → 状态变化精确规约表 → Task 9
  - §6.3 工程细节 1–7（debounce / 异步分派 / 生命周期 / OVERFLOW / 大文件 / 白名单 / symlink 不跟随）→ Tasks 10/11
  - §6.4 reconcile（仅扫 sessions/ + workspaces/） → Task 12
  - §6.7 DtEvent 在 watcher 路径上的发布（FileArtifactDetected / FileArtifactDiscarded / FileArtifactArchiveRequested）→ Task 9
- [x] **Placeholder 扫描**：本计划无 `TBD` / `TODO` / `implement later`；每段代码都是完整可粘贴的，所有方法签名、字段名、类名贯穿一致。
- [x] **类型一致性**：
  - `FileArtifactStatus` / `FileArtifactScope` / `FileArtifactKind` 跨 task 拼写一致
  - `recordDetected` / `recordModified` / `recordDeleted` 三个方法在 Task 9 定义、Task 11 调用、Task 12 reconciler 通过 `recordDetected` 复用 — 签名一致
  - `ArtifactWatcher` 端口在 Task 4 定义、Task 10 实现、Task 11 通过 mock 注入 — 一致
- [x] **CLAUDE.md "Backend Run vs Compile"**：每次跨模块改动后都有 `mvn install -pl <module> -am -DskipTests`（Tasks 8.4 / 15.1）。
- [x] **数据源兼容性 Gate**：本 Part **不涉及** 任何 DB 类型新增/变更（spec §2.3 已声明 N/A），完全是 FS + SQLite 元数据，与 MySQL/PG/H2 业务连接无关。

---

## Definition of Done

1. 所有 16 个 Task 的 checkbox 全部打勾
2. `mvn clean verify` 全绿；新增测试约 30+ 条全部通过
3. spec 文档完成度快照同步到含"Part 2 已完成"
4. `docs/exec-plans/index.md` 把本 Part 移到 Completed 行
5. `docs/exec-plans/2026-04-25-next-implementation-roadmap-plan.md` Task 11 状态更新
6. 本计划文件所有 task 全部勾选；偏差均有书面注解
7. 准备好 Part 3 plan 写作（MCP `datatalk_archive_artifact` + classpath AGENTS.md 模板新增 `## Output Files & Artifacts` 节 + `AgentsTemplateContractTest`）
