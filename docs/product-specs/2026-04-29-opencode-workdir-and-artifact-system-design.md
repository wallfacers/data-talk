# OpenCode 工作目录与 File Artifact 系统设计

| 元 | 值 |
|----|-----|
| 日期 | 2026-04-29 |
| 状态 | Draft v2 (Part 1+2+3+4+5a implemented; Part 5b pending) |
| 范围 | Backend (domain/application/infrastructure/adapter) · Frontend (Stage tabs/Chat/Settings) · OpenCode runtime 集成 |
| 修订 | 第二版：响应代码核实后的审阅意见，撤回 per-session cwd 假设、改子目录软隔离；新模型命名 `file_artifact` 与现有 `artifacts` 表区分；session_id 不设 FK、application 层管理引用 |

## 1. 背景与问题

DataTalk 底层运行 OpenCode 作为 AI 进程。在使用过程中，`~/.data-talk/opencode/`（DataTalk 注入目录）与 `~/.local/share/opencode/`（OpenCode 自管目录）持续产出未规范化文件：

- AI 在某次 session 里写出报告 / ER 图 / 临时数据，没有"归属"概念，文件直接落到 OpenCode cwd 根目录（已观察到 `datatalk-tools-test-report.md` 这类孤儿）
- `opencode.json.dt-bak-*` 历史备份每次启动新增一份，已堆积 18+
- `~/.local/share/opencode/log/*.log` 启动日志已堆积 11+
- DataTalk 删除 session 时未联动清理 OpenCode `session_diff/ses_*.json`、`tool-output/tool_*` 等
- 用户已规划未来在该目录持续产出报告、ER 图等持久化资产

**核心诉求**：建立一套**双轨双维度**的 file artifact 系统，区分 AI 中间产物（临时）与用户级资产（持久），并以可治理的方式整合既有堆积。

### 1.1 与现有 `artifacts` 系统的区分

代码库已存在 `artifacts` 表（V1/V3 migration），用于存储 SQL/table/chart/erd 三种 **payload 型** 产出（`kind IN ('table','chart','erd')`），与 session 强 FK CASCADE 绑定。本设计**不修改**该系统。

本设计新增的 `file_artifact` 表是 **物理文件型** 产出（CSV、Markdown 报告、SQL 脚本、PNG 等真实文件），与现有 payload 型 `artifacts` 在概念、存储、生命周期上独立：

| 维度 | 现有 `artifacts`（payload 型） | 新增 `file_artifact`（文件型） |
|------|--------------------------------|--------------------------------|
| 来源 | DataTalk action 执行产物（SQL 结果、图表 spec） | AI 在 OpenCode workdir 写出的物理文件 |
| 存储 | SQLite payload_ref + 内容字段 | 物理文件（FS）+ SQLite 元数据索引 |
| 生命周期 | FK CASCADE 跟随 session 删除 | application 层管理；archived 行可跨 session 存活 |
| 归属维度 | session 单一 | session（temporary/candidate）+ connection（archived）双维 |

两套系统通过命名严格区分：UI 文案、表名、端点名一律使用 `file` / `files` / `file_artifact`，避免与现有 `artifacts` 混淆。

## 2. 设计输入

### 2.1 来自 client/DESIGN.md 的前端约束

- **三层骨架不变**：Sidebar / Conversation lane / Stage（Instrument lane）
- **Stage state 全局共享**：`StageTab` 实例不携带 `scope` 字段；scope 仅在 `tab-type-registry` 类型元数据。Tab 实例全局，**渲染内容**是 `activeSessionId` / `activeConnectionId` 的函数
- **Token-only**：颜色用 semantic token，不用 raw primitive
- **Stage chrome `bg.subtle` / surface `bg.canvas`** / tabIdle `text.muted` / tabActive `accent.primary`
- **Accent 限定**：cobalt 仅用于 focus / selection / primary action
- **Status 色规约**：success=green、warning=amber（候选/未归档）、danger=red（删除/丢弃）、info=sky
- **Density**：tabs/toolbar=compact；终局确认弹窗=focused
- **Motion**：120/180/240ms；仅作状态变化的确认；无装饰动画
- **Accessibility**：状态不能仅靠颜色（图标+色双通道）；icon-only 按钮带 aria-label

### 2.2 来自 CLAUDE.md 的架构约束

- 依赖方向：domain ← application ← infrastructure ← adapter
- 新增 Action 用 `@DataTalkAction` 注解自动注册，零核心代码改动
- DtEvent 变更要求 application 层 exhaustive switch 同步
- 后端：JUnit 5 + AssertJ；OpenCode 协议交互用 WireMock (FakeOpenCodeServer)
- 前端：vitest

### 2.3 数据源兼容性 Gate

本设计 **不涉及** 新增/变更/依赖任何数据库类型。所有 file artifact 操作发生在文件系统层，与具体 DB 类型无关。
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md) 检查：N/A（file artifact 系统不分 DB 类型）。

### 2.4 来自代码核实的关键约束

- **OpenCode 是单进程**：`OpenCodeProcessManager:109` `ProcessBuilder.directory(homeDir/opencode)` —— 进程级唯一 cwd，无法 per-session 切换。所有 DataTalk session 共享一个 OpenCode 进程
- **现有 `artifacts` 表**：`V1__init.sql` / `V3__cascade_session_delete.sql`，`session_id REFERENCES sessions(id) ON DELETE CASCADE`，本设计不动它
- **AGENTS.md 渲染来源**：`server/data-talk-adapter/src/main/resources/agents/AGENTS.md` 是 classpath 模板源；`AgentPromptBuilder` 渲染并写出运行时副本到 `~/.data-talk/opencode/AGENTS.md`。运行时追加无效（启动会被覆盖），必须改 classpath 模板
- **i18n 结构**：`client/src/i18n/messages.ts`（单文件，非 `locales/{en,zh-CN}.json`）

## 3. 总体架构

### 3.1 运行时隔离：子目录软隔离

由于 OpenCode 是单进程、cwd 固定，**不能** per-session 切换 cwd。改为**子目录软隔离**：

- OpenCode 进程 cwd 仍为 `~/.data-talk/opencode/`（不动）
- DataTalk 在该 cwd 下创建 `sessions/<sid>/` 子目录树
- AGENTS.md 模板注入 `{{ACTIVE_SESSION_DIR}}` 占位 → `AgentPromptBuilder` 在每次发消息前根据当前活跃 session 渲染为 `./sessions/<sid>/`，并通过 OpenCode prompt 系统更新
- AI 通过 prompt 指引始终把 session 工作文件写到 `./sessions/<sid>/` 下
- 后端 `archive_artifact` 路径校验保证物理隔离正确性，**不依赖 AI 自律**

**为什么这样做能工作**：
1. Watcher 监听整个 `sessions/` 目录，能识别哪个 session 的子目录
2. AI 误把文件写到根目录或别处时，watcher detect 后归类为"无主 temporary"或被路径校验拒绝
3. 失去原方案"AI 完全无感"的优势，但获得现实可行性

### 3.2 责任分层

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Client (Tauri/React)                                                     │
│  · Stage Files Tab    (type scope=session,  内容跟 activeSessionId)      │
│  · Stage Files Library Tab (type scope=workspace, 内容跟 activeConnectionId) │
│  · Chat 内联 file artifact 卡片                                           │
│  · Session 删除终局确认弹窗                                              │
│  · Settings → Maintenance（存储概览）                                    │
└──────────────┬──────────────────────────────────────────────────────────┘
               │ REST: /sessions/{id}/files, /connections/{id}/files
               │ SSE:  DtEvent.FileArtifact*
               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ Backend (Spring Boot 3.5, Java 21)                                       │
│                                                                          │
│  Adapter (REST)        ──→ FileArtifactController                        │
│                         ──→ ArchiveArtifactActionHandler (@DataTalkAction)│
│                                                                          │
│  Application                                                             │
│  ├─ FileArtifactService          # 业务编排 + 状态机                     │
│  ├─ SessionWorkdirService        # 每 session 子目录生命周期              │
│  ├─ ArtifactWatcherService       # io.methvin DirectoryWatcher 封装      │
│  ├─ HousekeepingScheduler        # 备份/log/trash 滚动 + reconcile       │
│  ├─ LegacyMigrationRunner        # 一次性历史迁移                        │
│  └─ AgentPromptBuilder (扩展)     # 注入 {{ACTIVE_SESSION_DIR}} 占位     │
│                                                                          │
│  Domain                                                                  │
│  ├─ FileArtifact (sealed)        # status: Temporary|Candidate|Archived|Discarded │
│  ├─ FileArtifactScope (Session | Workspace)                              │
│  ├─ FileArtifactKind (Report | ErDiagram | SqlScript | Dataset | Other) │
│  └─ DtEvent: FileArtifactDetected / FileArtifactArchiveRequested /      │
│              FileArtifactArchived / FileArtifactDiscarded / LegacyMigrated │
│                                                                          │
│  Infrastructure                                                          │
│  ├─ FilesystemFileArtifactRepository # 物理路径 + frontmatter 解析       │
│  └─ FileArtifactMetadataRepository   # SQLite 索引（复用 data-talk.db） │
└──────────────┬──────────────────────────────────────────────────────────┘
               │ OpenCode 进程 cwd = ~/.data-talk/opencode/  (单进程，不变)
               │ AI 写文件目标 ./sessions/<sid>/<filename>  (通过 AGENTS prompt 引导)
               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ OpenCode Runtime (单进程)                                                │
│  · cwd: ~/.data-talk/opencode/  (固定)                                   │
│  · 当前 active session 子目录: ./sessions/<sid>/                          │
│  · AI 用原生 write/edit/bash 写到该子目录 → 默认 Temporary               │
│  · 显式调用 MCP datatalk_archive_artifact() → 提升为 Candidate           │
│  · AGENTS.md 含 {{ACTIVE_SESSION_DIR}} 占位 + 归档指引                    │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.3 关键数据流

**① AI 写临时文件**
```
AI bash python sample.py > sessions/<sid>/sample.csv
→ ~/.data-talk/opencode/sessions/<sid>/sample.csv
→ DirectoryWatcher CREATE → DebounceQueue 200ms → AsyncEventDispatcher
→ INSERT file_artifact (status=temporary, session_id=<sid>)
→ DtEvent.FileArtifactDetected → SSE → Stage Files Tab
```

**② AI 主动归档**
```
MCP datatalk_archive_artifact(path="orders-er.md", kind="er_diagram")
  (path 是相对于当前 active session subdir 的相对路径)
→ FileArtifactService 路径校验 (含 symlink/realpath 二次校验)
→ UPDATE file_artifact SET status=candidate, kind, title, summary
→ DtEvent.FileArtifactArchiveRequested
```

**③ 用户提升为 connection 资产**
```
POST /sessions/{sid}/files/{id}/archive
→ atomic mv opencode/sessions/<sid>/orders-er.md → workspaces/<connId>/orders-er.md
→ 重名自动 .v2/.v3 后缀
→ UPDATE status=archived, scope=workspace, session_id 保留 (用于"来自 session 'xxx'" UI)
→ DtEvent.FileArtifactArchived
```

**④ session 删除（两阶段，application 层管理）**
```
DELETE /sessions/{sid}
  Phase 1: 若 file_artifact 有 status='candidate' AND session_id=<sid> → 409 + 列表 → 客户端弹终局确认
  Phase 2: DELETE /sessions/{sid}?force=true
           → 现有 SessionService.deleteRecord() 走 FK CASCADE 处理 messages/artifacts/events 等
           → application 层主动管理 file_artifact（无 FK 自动级联）：
              · DELETE FROM file_artifact WHERE session_id=<sid> AND status IN ('temporary','candidate')
              · UPDATE file_artifact SET session_id=NULL WHERE session_id=<sid> AND status='archived'
                (archived 文件已在 workspaces/<connId>/，session_id 置 NULL，UI 显示"来自已删除会话"灰显)
           → SessionWorkdirService.delete(sid):
              · ArtifactWatcherService.unregisterSubtree(<sid>)
              · rm -rf opencode/sessions/<sid>/
              · 联动清理 OpenCode session_diff/ses_<sid>.json、tool-output/tool_*
```

## 4. 物理目录布局

```
~/.data-talk/
├── data-talk.db                                       # 元数据（新增 file_artifact 表）
├── metadata.db                                        # 业务元数据（不动）
├── housekeeping.log                                   # JSON-Lines，按月轮转
├── .legacy-migrated                                   # 一次性迁移完成 marker
│
├── opencode/                                          # OpenCode 进程 cwd（固定，不变）
│   ├── .current, v1.4.7/, node_modules/, plugins/, ...
│   ├── opencode.json + opencode.json.dt-bak-*         # 备份滚动 (最近 5 + 7 天)
│   ├── AGENTS.md                                      # 启动时由 AgentPromptBuilder 写出
│   │                                                  # 含 {{ACTIVE_SESSION_DIR}} 渲染后值
│   └── sessions/                                      # ← 新增：每 session 软隔离子目录
│       └── <sessionId>/
│           ├── .meta.json                             # { sessionId, connectionId, createdAt }
│           └── <AI 写的文件>
│
├── workspaces/                                        # ← 新增：connection 维度归档资产
│   └── <connectionId>/                                # （在 OpenCode cwd 外，AI 不可见）
│       ├── .index.json
│       └── <已归档资产>
│
├── _trash/                                            # ← 新增：7 天保留窗口
│   └── <connId-or-sid>__<artifactId>__<filename>     # （在 OpenCode cwd 外，AI 不可见）
│
└── _legacy/                                           # ← 新增：一次性迁移目的地
    └── datatalk-tools-test-report.md                  # （在 OpenCode cwd 外，AI 不可见）
```

**关键决策**：
- `sessions/` 必须在 OpenCode cwd 内（`opencode/sessions/`），这样 AI 用相对路径 `./sessions/<sid>/foo.csv` 即可
- `workspaces/`、`_trash/`、`_legacy/` 在 cwd **外**（`~/.data-talk/` 直接子项），对 AI 完全不可见，避免误操作

### 命名约定

- `_` 前缀目录 = 系统保留，AI 不可写入；后端 `archive_artifact` 路径校验拦截
- 重名归档：自动 `.v2.md` / `.v3.md` 后缀，旧版不动，不询问
- `_trash` 文件名前缀编码原归属（`<connId-or-sid>__<artifactId>__<filename>`），便于 7 天清理时对账

### SQLite 索引（`data-talk.db`，新表 `file_artifact`）

```sql
CREATE TABLE file_artifact (
  id              TEXT PRIMARY KEY,            -- file_artifact_<ulid>
  scope           TEXT NOT NULL,               -- 'session' | 'workspace'
  status          TEXT NOT NULL,               -- 'temporary' | 'candidate' | 'archived' | 'discarded'
  kind            TEXT NOT NULL,               -- 'report' | 'er_diagram' | 'sql_script' | 'dataset' | 'other'
  session_id      TEXT,                        -- 不加 FK；application 层管理引用；archived 行 session 删除后置 NULL
  connection_id   TEXT,                        -- 不加 FK；session-scoped 时记录所属 connection 以备升档定位
  filename        TEXT NOT NULL,
  physical_path   TEXT NOT NULL,
  size_bytes      INTEGER NOT NULL,
  mime_type       TEXT,
  title           TEXT,
  summary         TEXT,
  created_at      INTEGER NOT NULL,
  updated_at      INTEGER NOT NULL,            -- 文件 mtime 同步
  archived_at     INTEGER,                     -- Archived 时填
  metadata_json   TEXT                         -- frontmatter 解析结果 + 其他元
);

CREATE INDEX idx_file_artifact_session ON file_artifact(session_id) WHERE scope = 'session';
CREATE INDEX idx_file_artifact_connection ON file_artifact(connection_id) WHERE scope = 'workspace';
CREATE INDEX idx_file_artifact_status ON file_artifact(status);
```

**关键决策**：
- `session_id` / `connection_id` **不加 FK**。原因：archived 行需要在 session 删除后保留（FK ON DELETE SET NULL 可行但增加 schema 约束复杂度，application 层逻辑已经管理引用，FK 不必要）
- 状态枚举严格 4 个：Temporary, Candidate, Archived, Discarded（无 Removed —— DB 孤儿行直接删，物理已 mv 走的进 _trash 标 Discarded）

**双存储一致性**：文件系统是事实之源；SQLite 是查询索引。通过启动时 + 每日 reconcile 保持对账。

## 5. 协议契约

### 5.1 三层契约

```
默认 (子目录软隔离)                         ← OpenCode 原生 write/edit/bash 到 ./sessions/<sid>/
        ↓
显式 MCP 工具 (datatalk_archive_artifact)   ← 推荐路径，AI 主动声明
        ↓
frontmatter 元数据                          ← 兜底/补充，仅 .md/.sql 等文本
```

### 5.2 MCP 工具：`datatalk_archive_artifact`

工具名保留（外部已建立心智模型；DataTalk MCP namespace 自带 `datatalk_` 前缀，与现有 payload 型 artifacts 命名空间不冲突）。

```json
{
  "name": "datatalk_archive_artifact",
  "description": "Mark a file in the current session subdir as an archive candidate, signaling the user that this output is worth preserving as a long-term asset for the active database connection.",
  "inputSchema": {
    "type": "object",
    "required": ["path", "kind"],
    "properties": {
      "path":    { "type": "string", "description": "Path relative to current session subdir (./sessions/<sid>/). Example: 'orders-er.md' or 'reports/weekly.md'. No traversal (..). No symlinks." },
      "kind":    { "type": "string", "enum": ["report", "er_diagram", "sql_script", "dataset", "other"] },
      "title":   { "type": "string", "description": "Optional human-readable title (default: filename)." },
      "summary": { "type": "string", "description": "Optional one-paragraph summary." }
    }
  }
}
```

**返回（成功）**：`{ ok: true, fileArtifactId, status: "candidate", physicalPath }`

**返回（失败）**：`path_outside_session_dir` / `path_not_found` / `path_is_directory` / `path_is_system` / `path_contains_symlink` / `already_archived (idempotent ok+warn)`

### 5.3 frontmatter 约定

仅识别文件首部 8KB 内的 YAML frontmatter：

```markdown
---
artifact: true
kind: er_diagram
title: 订单域 ER 图
summary: 覆盖 orders/order_items/payments 三表的实体关系
---
```

- `artifact: true` 必填（不写=默认 Temporary）
- 与 MCP 工具调用冲突时，**MCP 工具调用赢**（更晚发生、更明确）
- SQL 文件用 `-- key: value` 注释包裹
- CSV / 二进制无 frontmatter，**只能**通过 MCP 工具归档

### 5.4 路径安全（后端必做二次校验，含 symlink/TOCTOU 防护）

`FileArtifactService.archiveArtifact(sessionId, requestedPath)` 拒绝路径的完整规则：

```
1. requestedPath 必须是相对路径（不是 absolute）
2. requestedPath 不得包含 ".." 段
3. 解析为绝对路径：base = realpath(opencode/sessions/<sid>); target = realpath(base / requestedPath, NOFOLLOW_LINKS)
   target 不在 base 之下 → reject (path_outside_session_dir)
4. requestedPath 路径段任一以 "_" 开头 → reject (path_is_system)
5. Files.readAttributes(target, NOFOLLOW_LINKS) 必须不是 symlink；如果是 symlink → reject (path_contains_symlink)
6. target 必须存在且是普通文件（不是目录、不是 socket/pipe） → reject (path_not_found / path_is_directory)
7. mv 之前再 stat 一次（TOCTOU 最小化窗口），属性变化 → reject + retry-able
8. mv 用 Files.move(... ATOMIC_MOVE)（不 REPLACE_EXISTING；目标重名时由 .v2/.v3 后缀策略生成新路径再 move）
```

不信任 OpenCode 自身的子目录隔离（AI 理论上能 `bash mkdir ../outside`、写 symlink）；后端校验是最后防线。

### 5.5 AGENTS.md 模板修改（classpath 源）

修改 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` 模板，在 `## Stage Tab Digest` 之后插入新节：

```markdown
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
```

`AgentPromptBuilder` 扩展 `{{ACTIVE_SESSION_DIR}}` 占位渲染：在每次 prompt rebuild 时（或当 active session 切换时）替换为 `./sessions/<currentSessionId>/`。当无活跃 session 时渲染为占位文本 `<no active session>`，并整段加 `(no session active)` 警告头。

### 5.6 Watcher 与 frontmatter 生命周期

**FS 事件 → file_artifact 状态变化的精确规约**：

| FS 事件 | 行为 |
|---------|------|
| **CREATE** (新文件出现) | mtime 稳定 ≥ 200ms 后；解析 frontmatter（仅 .md/.sql/.txt 等文本，首 8KB）；INSERT file_artifact (status=temporary 或 candidate 取决于 frontmatter.artifact=true)；mime_type 探测；DtEvent.FileArtifactDetected |
| **MODIFY** (内容变化) | mtime + size_bytes 同步；如果文件之前是 temporary 且新 frontmatter 出现 `artifact: true` → 升 candidate；如果文件已是 candidate/archived，不主动重抓 title/summary（避免覆盖用户/AI 已设置）|
| **DELETE** (文件消失) | 若 status=temporary → 直接 DELETE 行（无声）；若 status=candidate → DELETE 行 + DtEvent.FileArtifactDiscarded（隐式丢弃）；若 status=archived → 不会发生（archived 文件在 workspaces/，watcher 不监听该路径） |
| **RENAME** (move 同 watcher 内) | 平台支持 inode 关联时视为 path 更新；不支持时视为 DELETE+CREATE（同上规则；artifact id 不复用） |
| **OVERFLOW** | 触发该 sub-watch 全量 reconcile |

**frontmatter 解析时机**：CREATE / MODIFY 后的 debounce 窗口结束时解析一次；缓存到 metadata_json；解析失败 = 无 frontmatter。

**watcher 监听范围**：仅 `~/.data-talk/opencode/sessions/`（递归）；不监听 `workspaces/`/`_trash/`/`_legacy/`（这些靠应用层调用维持一致性）。

## 6. 生命周期

### 6.1 状态机（4 状态）

```
                ┌──────────────┐
   AI 写文件   │  Temporary   │ ─── [丢弃] ────────────────┐
   ─────────▶ │              │                              │
                └──────┬───────┘                              ▼
                       │ AI 调 archive_artifact /     ┌─────────────┐
                       │ frontmatter artifact=true    │  Discarded  │
                       ▼                              │  (in _trash) │
                ┌──────────────┐                      └──────┬──────┘
                │  Candidate   │ ─── [丢弃] ─────────▶      │
                └──────┬───────┘                              │
                       │ 用户 [归档] / 终局确认选"归档"      │
                       ▼                                      │
                ┌──────────────┐                              │
                │   Archived   │ ─── 用户 [删除] ────────────▶│
                │   (workspace)│                              │
                └──────────────┘                              ▼
                                                       7 天后物理删除
                                                       + DELETE row
```

### 6.2 触发点规约

| 触发 | 行为 |
|------|------|
| session 创建 | mkdir `opencode/sessions/<sid>/` + 写 .meta.json + watcher.register 子树 |
| AI 写文件到 session 子目录 | watcher CREATE → debounce 200ms → 解析 frontmatter → INSERT file_artifact → DtEvent |
| AI 调 archive_artifact | 路径校验（§5.4 八条规则）→ UPDATE status=candidate → DtEvent |
| 用户 [归档] (Candidate→Archived) | atomic mv 到 workspaces/<connId>/，重名自动版本递增 → UPDATE → DtEvent |
| 用户 [丢弃] | atomic mv 到 _trash/，文件名编码归属 → UPDATE status=discarded → DtEvent |
| session DELETE Phase 1 | 若有 candidate → 409 + 列表 |
| session DELETE Phase 2 (force) | 现有 deleteRecord() 走 FK CASCADE 处理 messages/artifacts/events；application 层主动 DELETE file_artifact (temporary/candidate WHERE session_id) + UPDATE archived 行 session_id=NULL；rm -rf opencode/sessions/<sid>/；联动清理 OpenCode session_diff/tool-output |
| connection DELETE | 同上两阶段：若有资源 → 409 + 数量 → force 时 archived 行保留（connection_id=NULL + metadata_json `orphanedFromConnection*` 标记，Q2 设计决策 Part 5a 落地）；temporary/candidate 在 child session 级联删除中清理；rm workspaces/<connId>/ |

**候选不搬运文件**：物理移动只发生在 Candidate → Archived 这一次跃迁。理由：候选可能被丢弃（提前搬运浪费 IO），且 AI 可能继续编辑。

### 6.3 文件监听

**选择**：`io.methvin:directory-watcher`（跨平台原生，macOS 走 FSEvents JNI，避开 JDK polling fallback）

**关键工程细节**：

1. **Debounce 200ms**：合并写入过程的多次 MODIFY
2. **异步事件分派**：监听线程仅入队；业务用独立 executor 消费
3. **生命周期**：session 创建/删除时 register/unregister 该 session 子目录的 sub-watch
4. **OVERFLOW 兜底**：触发该 session 子目录全量 reconcile
5. **大文件识别**：mtime 稳定 ≥ 200ms 才视为"已写完"，避免 0 字节闪现
6. **白名单过滤**：忽略 `.tmp/.swp/.partial/.swo`
7. **symlink 不跟随**：watcher 配置 `followLinks=false`，符号链接产生的文件不进 artifact 系统

### 6.4 自动治理（HousekeepingScheduler）

启动时 + 每日 03:00 UTC 跑一次：

| 任务 | 策略 |
|------|------|
| `rotateOpencodeBackups` | `opencode.json.dt-bak-*` 保留最近 5 份 + 最近 7 天（并集） |
| `rotateOpencodeLogs` | `~/.local/share/opencode/log/*.log` 保留最近 5 份 + 7 天（保底 5 份哪怕都超 7 天） |
| `cleanupTrash` | 扫描 `_trash/*` mtime > 7 天 → 物理删该文件 + `DELETE FROM file_artifact WHERE id=<artifactId>`（artifactId 从文件名前缀解出） |
| `reconcileFileArtifacts` | 数据库 vs 磁盘对账：仅扫 `opencode/sessions/*` 与 `workspaces/*`（不含 `_legacy`/`_trash`/`_tmp`/`opencode/` 根）。**孤儿文件**（FS 有 / DB 无）→ 补登记 status=temporary。**孤儿行**（DB 有 / FS 无）→ temporary 静默 DELETE 行；candidate 走 discard 流程（UPDATE status=discarded）但因文件已不在不复制到 _trash 而是直接 DELETE 行 + 发警告 DtEvent；archived 同处理（DELETE 行 + 警告）。**孤儿 cwd**（FS 有 sessions/<sid>/ / DB 无 session）→ 视为已删除残留，rm -rf + log |

**不删 OpenCode 自管的 db/wal/storage/migration/auth.json**（属 OpenCode 契约表面）。

### 6.5 一次性历史迁移（LegacyMigrationRunner）

```
@PostConstruct 启动时：
  if exists ~/.data-talk/.legacy-migrated:
      return
  扫描 ~/.data-talk/opencode/ 直接子项（不递归）：
    白名单（保留原位）:
      .current, .gitignore, AGENTS.md, opencode.json,
      opencode.json.dt-bak-*, package.json, package-lock.json,
      plugins/, node_modules/, v*/, sessions/  (新加：本设计的 session 子目录树)
    其余 → mv 到 ~/.data-talk/_legacy/
  touch .legacy-migrated
  发 DtEvent.LegacyMigrated → 客户端 toast
```

**已知会被迁移的**：`datatalk-tools-test-report.md`

`_legacy/` 不进 file artifact 体系（没有 session/connection 归属）。

### 6.6 异常路径与幂等

| 场景 | 行为 |
|------|------|
| archive_artifact 时文件被外部删除 | 返回 path_not_found；file_artifact 行被 watcher 同步删除 |
| 归档 mv 失败（磁盘满 / 权限） | 事务回滚（保留 candidate），返回 5xx，前端 toast |
| 归档时 mv 前 stat 检测到属性变（TOCTOU） | 返回 5xx with retry hint |
| watcher OVERFLOW | 触发 session 子目录全量 reconcile |
| session 删除时 watcher.unregister 失败 | 容忍，继续 rm |
| _trash 中文件被外部 mv 走 | 7 天清理时 file not found 静默跳过 |
| archive_artifact 重复调用 | 幂等 `ok=true + warn` |
| AI 写到 sessions/ 之外（违规） | watcher 仍 detect 到 sessions/ 内的子树文件；写到根目录的文件不会进 file_artifact 系统，被 reconcile 视为 sessions/ 外文件忽略；下次治理由其他机制处理 |

### 6.7 DtEvent 新增

```java
sealed interface DtEvent permits ..., FileArtifactDetected, FileArtifactArchiveRequested,
                                 FileArtifactArchived, FileArtifactDiscarded,
                                 LegacyMigrated { ... }

record FileArtifactDetected(String fileArtifactId, String sessionId, FileArtifactKind kind, ...) implements DtEvent {}
record FileArtifactArchiveRequested(String fileArtifactId, ...) implements DtEvent {}
record FileArtifactArchived(String fileArtifactId, String connectionId, ...) implements DtEvent {}
record FileArtifactDiscarded(String fileArtifactId, String reason, ...) implements DtEvent {}
record LegacyMigrated(int filesMovedCount) implements DtEvent {}
```

application 层 exhaustive switch 必须新增对应 case；编译期强约束。**5 个事件，无 Removed**（孤儿行直接 DB DELETE，无中间态）。

## 7. UI 详细设计

### 7.1 Tab 类型注册

```ts
// client/src/features/stage/tab-type-registry.ts
TAB_TYPES = {
  ...,
  FILES: {
    id: 'files',
    label: 'Files',
    icon: FileTextIcon,
    scope: 'session',
    contentSource: 'activeSession',
  },
  FILES_LIBRARY: {
    id: 'files-library',
    label: 'Files Library',
    icon: PackageIcon,
    scope: 'workspace',
    contentSource: 'activeConnection',
  },
}
```

Stage 持有 tab 实例 ID 列表（全局共享）；面板组件内部消费 `useSessionStore.activeSessionId` / `useConnectionStore.activeConnectionId` 渲染数据。切换 session/connection 不改 Stage 状态。

### 7.2 Files Tab（session-content）

```
┌─Stage Tabs──────────────────────────────────────────────────────────┐
│ SQL │ Chart │ Files (3) ● │ Files Library (8) │  ＋                │
├─────┴───────┴─────────────┴───────────────────┴─────────────────────┤
│  ▼ TEMPORARY (2)            text.muted · ui-xs                      │
│   ╭── bg.panel · radius.md · border.subtle ──────────────╮         │
│   │ 📄 sample.csv                                  2.1 MB │         │
│   │    Dataset · 2026-04-29 14:23                          │         │
│   │              [打开]  [📌 标为候选]  [丢弃]            │         │
│   ╰────────────────────────────────────────────────────────╯        │
│                                                                      │
│  ▼ ARCHIVE CANDIDATES (1)   accent.warn dot · ui-xs                 │
│   ╭── status.warningSurface · accent.warn 1px 左边框 ─────╮         │
│   │ 📊 orders-er.md                            8.4 KB     │         │
│   │    ER Diagram · "覆盖 orders/order_items/payments..."  │         │
│   │              [打开]  [✓ 归档]  [丢弃]                  │         │
│   ╰─────────────────────────────────────────────────────────╯       │
└──────────────────────────────────────────────────────────────────────┘
```

- 候选状态用色 + 边框双通道，不仅靠颜色（无障碍）
- [✓ 归档] 是 accent.primary 主按钮；其余 ghost
- 折叠分组 header 可点击折叠（compact density）
- Empty state（无激活 session）：'选择或新建一个会话以查看 AI 产出文件'

### 7.3 Files Library Tab（connection-content）

```
┌─Stage─────────────────────────────────────────────────────────────────┐
│  📦 prod-mysql · 文件资产库         [搜索: ___________]  [筛选 ▾]    │
│                                                                       │
│  ▼ ER Diagrams (2)                                                   │
│   ╭───────────────────────────────────────────────────────────╮     │
│   │ 📊 orders-er.md                            8.4 KB         │     │
│   │    归档于 2026-04-29 · 来自 session "订单分析"           │     │
│   │                          [打开]  [复制路径]  [删除]       │     │
│   ╰───────────────────────────────────────────────────────────╯     │
│                                                                       │
│  ▼ Reports (3) ▼ SQL Scripts (3) ▼ Datasets (0) ▼ Other (0)         │
└───────────────────────────────────────────────────────────────────────┘
```

- 按 `kind` 自动分组
- 搜索：filename + title + summary 全文
- 筛选：按 kind / 按归档时间范围
- '来自 session' 是可点击 link，跳到该 session（已不存在则灰显，文案变 '来自 已删除会话'）
- Empty state（切换 connection 后）：'当前 connection 还没有归档资产'

### 7.4 Chat 内联 file artifact 卡片

```
┌─Assistant message─────────────────────────────────────────────────┐
│  我已基于 50 万行样本生成订单域 ER 图。                          │
│                                                                    │
│  🔧 datatalk_archive_artifact                                     │
│  ╭─ bg.panel · radius.md · border.subtle ────────────────╮       │
│  │  📊 orders-er.md  ·  📌 Archive Candidate              │       │
│  │  ER Diagram · 8.4 KB                                    │       │
│  │  [Stage 查看]  [立即归档]  [丢弃]                       │       │
│  ╰────────────────────────────────────────────────────────╯       │
└────────────────────────────────────────────────────────────────────┘
```

- 卡片状态徽章随后端事件更新（候选 → 已归档 → 已丢弃）
- 卡片在 Chat 历史里持久存在
- '立即归档' = Files Tab 的 [✓ 归档] 快捷
- 'Stage 查看' 自动打开 Files Tab 并滚动到该候选

### 7.5 Session 删除终局确认弹窗（focused density）

```
┌─Modal · bg.canvas · radius.lg · 480px────────────────────────────┐
│  删除会话 "订单分析"？                                          │
│  此会话有 2 个候选文件未归档。删除会话将一并清理这些文件，      │
│  请先决定如何处置：                                              │
│                                                                   │
│   📊 orders-er.md  · ER Diagram · 8.4 KB                         │
│      ◯ 归档到 prod-mysql 资产库     ◉ 丢弃                       │
│   📊 weekly-report.md  · Report · 32.4 KB                        │
│      ◉ 归档到 prod-mysql 资产库     ◯ 丢弃                       │
│                                                                   │
│   □ 全部归档    □ 全部丢弃                                       │
│                                                                   │
│             [取消]                  [确认删除会话]              │
│                                  accent.primary                   │
└───────────────────────────────────────────────────────────────────┘
```

- 仅列 candidates；temporary 默认随 session 删（用户已知 session 子目录是工作区）
- '确认删除' 直到所有候选有决策才 enabled
- 用 accent.primary（决策已在上方完成；按钮只是 commit）
- prefers-reduced-motion: 弹窗淡入用 fast/normal

### 7.6 Settings → Maintenance（轻量页）

不增加 sidebar 入口；现有 Settings 路由内新 tab：

```
Settings: General │ Models │ Connections │ Maintenance ●

存储概览
  工作目录    /home/.../.data-talk
  总占用      342 MB
   ├─ OpenCode 基础设施   286 MB
   ├─ Sessions            21 MB   (3 个活跃 session)
   ├─ Workspaces (资产)   28 MB   (8 资产 / 2 connection)
   ├─ _trash              5 MB    (3 项 · 7 天后清理)
   └─ _legacy             2 MB

操作
  [立即清空 _trash]  [查看 _legacy 目录...]  [查看 housekeeping 日志]
```

### 7.7 状态徽章规约

| 状态 | 视觉 | Token |
|------|------|-------|
| Temporary | 📄 + 元信息灰 | text.muted |
| Candidate | 📌 + 浅黄背景 + 黄色左边框 | warn / status.warningSurface |
| Archived | 📦 + 中性卡片 | bg.panel |
| Discarded | 不显示，仅 _trash 可见 | — |

### 7.8 i18n

i18n key 添加到 `client/src/i18n/messages.ts`（按现有结构，与同级 key 平级）：

- `files.tabs.session`、`files.tabs.library`
- `files.section.temporary`、`files.section.candidates`
- `files.action.archive`、`files.action.discard`、`files.action.markAsCandidate`
- `files.deleteModal.title`、`files.deleteModal.allArchive`、`files.deleteModal.allDiscard`、`files.deleteModal.confirm`
- `maintenance.tab.title`、`maintenance.overview.totalSize`、...
- 中英文都要齐

## 8. 测试策略

### 8.1 后端测试矩阵

| 层 | 文件 | 关键测试 |
|----|------|---------|
| Domain | `FileArtifactTest` | sealed interface exhaustive switch；状态跃迁合法性 |
| Application | `FileArtifactServiceTest` | 路径八条校验（含 symlink/realpath/TOCTOU 模拟）；状态机；幂等；版本递增 |
| Application | `SessionWorkdirServiceTest` | 子目录创建/清理；OpenCode session_diff 联动清理 |
| Application | `ArtifactWatcherServiceTest` | debounce；OVERFLOW reconcile；register/unregister；symlink 不跟随 |
| Application | `HousekeepingSchedulerTest` | N+D 并集策略；trash 时间窗；reconcile 孤儿处理 |
| Application | `LegacyMigrationRunnerTest` | 白/黑名单（含 sessions/ 加入白名单）；marker 防重复 |
| Application | `AgentPromptBuilderActiveSessionDirTest` | `{{ACTIVE_SESSION_DIR}}` 占位渲染；无 session 时占位文本 |
| Adapter | `AgentsTemplateContractTest` | classpath AGENTS.md 含 `## Output Files & Artifacts` 节（防意外删除） |
| Infrastructure | `FilesystemFileArtifactRepositoryIT` | 真实 IO；frontmatter 解析（md/sql/无）；symlink 拒绝 |
| Adapter | `FileArtifactControllerIT` | REST 全路径；session DELETE 409+force；connection 级联 |
| Adapter | `ArchiveArtifactActionHandlerIT` | MCP 入站；FakeOpenCodeServer 验证调用流 |

### 8.2 关键集成测试

- **端到端归档**：H2 + 真实 FS + FakeOpenCodeServer，Temporary→Candidate→Archived 全链路
- **重名版本递增**：连续归档同名 3 次验证 .v2/.v3 后缀正确
- **session 删除阻断**：留 candidate → 409 → force=true → temporary/candidate 行删除 + archived 行 session_id=NULL 保留
- **路径攻击拒绝**：构造 symlink 指向 cwd 外文件 → archive 应拒绝；`..` traversal → 应拒绝；`_xxx/` → 应拒绝
- **现有 artifacts 共存**：FK CASCADE 删除 session 时，新 file_artifact 表的 archived 行不被错误清理

### 8.3 前端测试矩阵

| 文件 | 测试 |
|------|------|
| `files-tab.test.tsx` | 分组渲染；按钮回调；activeSession 切换刷新；空 session 态 |
| `files-library-tab.test.tsx` | 分组；搜索/筛选；activeConnection 切换刷新 |
| `chat-file-artifact-card.test.tsx` | 状态徽章随事件更新 |
| `delete-session-modal.test.tsx` | 候选清单；批量；确认按钮 disable 直到决策完成 |
| `maintenance-tab.test.tsx` | 存储概览；清空操作；i18n |

### 8.4 不测什么（YAGNI）

- 不为 io.methvin 库自身写测试
- 不写性能基准（变更频率低，不预设性能目标）
- 不重复测 Stage 容器集成（已有）

## 9. 实施里程碑

| M | 范围 | Demo |
|---|------|------|
| **M0** | 代码核实预检（已部分完成）+ Flyway migration 起草（V14 file_artifact 表）+ 命名空间冲突最终确认 | migration SQL 通过 H2 + SQLite 双跑；现有 artifacts 表行为回归测试 |
| **M1** | Domain (FileArtifact sealed) + FileArtifactService + SessionWorkdirService + DtEvent 5 类型 + AgentPromptBuilder 占位扩展 | 单元 + 集成测试通过；REST 注入路径手工归档可走通 |
| **M2** | ArtifactWatcherService (io.methvin) + symlink 拒绝 + OVERFLOW reconcile + 启动时 + 定时 | 手动 touch sessions/<sid>/foo.csv 后日志出现 FileArtifactDetected |
| **M3** | ArchiveArtifactActionHandler + classpath AGENTS.md 模板修改 + AgentsTemplateContractTest + WireMock 测试 | 真实 OpenCode session AI 调 archive_artifact 后端正确登记 |
| **M4** | 前端 Files Tab + Files Library Tab + Zustand store + i18n + vitest | UI 看到文件出现/归档移动 |
| **M5** | session DELETE 两阶段 + connection DELETE 级联 + Chat 卡片 + 终局确认 modal | ✅ Part 5a shipped：`DeleteOutcome` sealed + `PhysicalMover` TOCTOU + archive/discard REST + `DeleteSessionModal` + `DeleteConnectionModal`（orphan banner）；后端 73+32+19 测试全绿，前端 982 测试全绿 |
| **M6** | HousekeepingScheduler + LegacyMigrationRunner + Settings Maintenance + housekeeping.log | 启动 toast；存储概览页；datatalk-tools-test-report.md 进 _legacy/ |

每个 M 独立 PR、独立可演示。M0 是新增的预检阶段（响应审阅 #2 #3）。M1-M3 后端 + 协议先稳；M4-M5 前端叠加；M6 治理收尾。

## 10. 风险与权衡

| 风险 | 缓解 |
|------|------|
| AI 不遵守 AGENTS.md 约定，写到 sessions/ 之外 | watcher 仅监听 sessions/，违规文件不进 file_artifact 系统；reconcile 治理；后续可加 prompt-level reminders |
| 现有 `artifacts` 表与新 `file_artifact` 命名混淆 | 命名严格区分（file_artifact / files / FileArtifact）；spec §1.1 显式说明；UI 文案"Files Library"避免与"Artifacts"撞 |
| AI 用 symlink 绕过隔离 | 路径校验 LinkOption.NOFOLLOW_LINKS + watcher.followLinks=false 双重防护 |
| TOCTOU race（校验通过后文件被替换） | mv 前再 stat；ATOMIC_MOVE；最坏情况返回 5xx + retry hint |
| 文件系统 vs SQLite 不一致 | 启动时 + 每日 reconcile；事实之源是文件系统 |
| io.methvin 在某平台异常 | 抽象在 ArtifactWatcherService 接口背后；fallback 到全量轮询的接口预留 |
| AI 不调 archive_artifact 也不写 frontmatter | 接受为 Temporary，session 删时清理；UI 提供"标为候选"手动操作弥补 |
| sessions/ 累积变多（用户从不删 session） | reconcile 检测孤儿 cwd；未来可加阈值告警；本期不主动清理活跃 session |
| 归档时磁盘满 | mv 失败 → 事务回滚 → 5xx → toast；状态保留 candidate |
| AGENTS.md classpath 模板被多处修改时合并冲突 | 为新增节加 marker 注释（`<!-- file-artifact-section -->`）；contract test 防止意外删除 |

## 11. 范围之外（Out of Scope）

- AI 跨 session 复用资产（如 `datatalk_list_artifacts` / `datatalk_read_artifact`）—— 属"AI 知识沉淀"独立方向，需单独 brainstorming
- file_artifact 内容版本控制（每次归档保存 diff）—— 当前用 .v2/.v3 后缀已够用
- file_artifact 共享 / 多用户 ACL —— DataTalk 当前是单用户桌面应用
- file_artifact 全文索引（除了 filename + title + summary 的简单搜索）—— 规模未到必要
- `~/.config/opencode/` 冗余目录调研 —— 已观察到该目录存在，但深入治理留作 follow-up；本 spec 不覆盖
- per-session OpenCode 进程隔离 —— 工程量大，当前子目录软隔离已够用
- 性能基准与监控 dashboards —— 当前无明确性能目标
- 现有 `artifacts` 表（payload 型）的任何改造
- 跨 connection 移动 archived 文件（除孤儿 reattach 外）—— Part 5b 将提供孤儿 Drawer UI 供 reattach 到新连接

## 12. 数据建模新增 / 变更

### 新增表（Flyway V14）
- `file_artifact`（schema 见 §4）
- session_id / connection_id 不加 FK；application 层管理引用

### 新增 Domain 类型
- `FileArtifact`（sealed interface，4 个 record 实现对应 4 状态）
- `FileArtifactScope`（enum: Session, Workspace）
- `FileArtifactKind`（enum: Report, ErDiagram, SqlScript, Dataset, Other）
- `FileArtifactStatus`（enum: Temporary, Candidate, Archived, Discarded）—— 严格 4 个，无 Removed

### 新增 DtEvent 类型（5 个，无 Removed）
- `FileArtifactDetected` / `FileArtifactArchiveRequested` / `FileArtifactArchived` / `FileArtifactDiscarded` / `LegacyMigrated`

### 新增 REST endpoints
- `GET    /sessions/{sid}/files`
- `GET    /connections/{cid}/files`
- `POST   /sessions/{sid}/files/{fid}/archive`
- `POST   /files/{fid}/discard` (任意状态 → discarded → mv 到 _trash；统一删除入口)
- `DELETE /sessions/{sid}` 增加 `?force=true` 参数与 409 阻断（修改现有，需 backwards-compatible）
- `DELETE /connections/{cid}` 同上
- `GET    /maintenance/storage-overview`
- `POST   /maintenance/cleanup-trash`

### 新增 MCP Tools
- `datatalk_archive_artifact`（工具名保留；description 更新为 session-subdir-aware）

### 现有 AGENTS.md 模板修改
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` 新增 `## Output Files & Artifacts` 节
- `AgentPromptBuilder` 扩展 `{{ACTIVE_SESSION_DIR}}` 占位

### 新增前端 store
- `useFileArtifactsStore`（Zustand）：缓存当前 session/connection 的 file_artifact 列表，订阅 SSE 事件

### 新增前端组件 / 文件
- `client/src/features/stage/tabs/files-tab.tsx`
- `client/src/features/stage/tabs/files-library-tab.tsx`
- `client/src/features/chat/components/chat-file-artifact-card.tsx`
- `client/src/features/session/components/delete-session-modal.tsx`（可能扩展现有）
- `client/src/features/settings/maintenance-tab.tsx`

## 13. 相关文档

- [CLAUDE.md](../../CLAUDE.md) - 架构原则
- [client/DESIGN.md](../../client/DESIGN.md) - 前端设计契约
- [docs/PLANS.md](../PLANS.md) - 计划工作流
- [docs/RELIABILITY.md](../RELIABILITY.md) - 可靠性实践
- [docs/exec-plans/index.md](../exec-plans/index.md) - 执行计划目录

---

**审阅状态**：Draft v2，设计已代码核实，且 Part 1+2 已落地；Part 3-5 仍待正式 child plan 与实现。

**当前完成度快照（2026-04-30）**：
1. 已完成：Part 1 (Migration & Domain) + Part 2 (Watcher & Reconcile) — Part 2 接入 io.methvin DirectoryWatcher、FrontmatterParser、FileArtifactReconciler 与 FileArtifactWatcherStartup；端到端 IT 通过。
2. 未开始正式计划：Part 3 (MCP + AGENTS template)、Part 4 (frontend files tabs)、Part 5 (deletion flow + housekeeping)。
3. 文档状态：spec 继续作为 Task 11 总设计基线；后续实现应补 Part 3-5 各自的 execution plan，而不是继续直接堆到本 spec 里。

**第二版相对第一版的关键改动摘要**：
1. 撤回 per-session cwd 假设（OpenCode 单进程现实）→ 改子目录软隔离 + AGENTS.md `{{ACTIVE_SESSION_DIR}}` 占位
2. 新表/实体改名 `file_artifact` 与现有 `artifacts` 区分；UI tab "Files" / "Files Library"；MCP 工具名保留
3. session_id / connection_id 不设 FK，application 层管理引用；archived 行可在 session 删除后 session_id=NULL 保留
4. 路径校验扩展为 8 条规则（symlink/realpath/TOCTOU/原子 mv）
5. 状态枚举严格 4 个（无 Removed）；reconcile 路径不引入中间状态
6. AGENTS.md 改 classpath 模板（非运行时追加）；增加 contract test
7. i18n 路径修正为 `messages.ts` 单文件
8. 补充 watcher 事件 → 状态变化的精确规约（CREATE/MODIFY/DELETE/RENAME/OVERFLOW）
9. 新增 M0 预检阶段（migration 与命名冲突回归）
