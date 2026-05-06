# File Artifact System · Part 5 — 删除流 + 治理设计

| 元 | 值 |
|----|-----|
| 日期 | 2026-05-07 |
| 状态 | Draft（brainstorming 产出，待 user review；通过后由 writing-plans 拆 Part 5a / 5b 两份正式 child plan） |
| 范围 | Backend (application/adapter)、Frontend (Stage modals / Settings Maintenance / Orphan drawer) |
| 父 spec | [2026-04-29-opencode-workdir-and-artifact-system-design.md](./2026-04-29-opencode-workdir-and-artifact-system-design.md) |
| 关联 plan | [Part 1 (shipped)](../exec-plans/2026-04-29-file-artifact-system-part1-domain-and-migration-plan.md) · [Part 2 (shipped)](../exec-plans/2026-04-30-file-artifact-system-part2-watcher-reconcile-plan.md) · [Part 3 (planned)](../exec-plans/2026-04-30-file-artifact-system-part3-mcp-and-agents-template-plan.md) · [Part 4 (planned)](../exec-plans/2026-04-30-file-artifact-system-part4-frontend-tabs-plan.md) |

## 0. 与父 spec 的偏离声明

本 design 在父 spec 基础上做了 **一项关键策略调整**，必须在此显式登记，并在 Part 5 实现时反向更新父 spec §6.2 / §11：

- **Connection DELETE 时 archived 文件不再丢弃**（父 spec §6.2 的 "force 时所有 archived 文件 mv 到 _trash + status 改 discarded + rm workspaces/<connId>/" 被本 design 取代）。
- **取代方案**：archived 行 `connection_id` 置 NULL（与 session 删除时 archived 行 `session_id` 置 NULL 对称），**文件不动，留在原 `~/.data-talk/workspaces/<已删 cid>/`**；同步把原 connection 的 `name` / `id` / 删除时戳写入 `metadata_json` 的 `orphanedFromConnection` / `orphanedFromConnectionId` / `orphanedAt`，确保孤儿在 UI 仍可显示原归属（避免 connection 行删除后名字消失）；UI 层在 Settings → Maintenance 提供"孤儿归档资产"管理界面（§B.3）让用户主动 reattach 到其它 connection 或丢弃。
- **理由**：父 spec §1.1 把 archived 定义为"跨 session 存活的用户级长期资产"。connection 删除是用户级高影响操作；一刀切丢弃 archived 资产违背该定位、与 session 删除的孤儿处理也不对称。本调整把"是否清理 archived 资产"的决策权显式交还给用户。
- **代价**：磁盘上会留 `workspaces/<已删 cid>/` 孤儿目录；reconciler 必须扩展为"workspaces/ 下文件 + DB row.connection_id IS NULL"也算 valid，不重新登记（§C.4 风险表）。

其余父 spec 条款 Part 5 严格遵守，包含 §3.1 子目录软隔离、§4 物理目录布局、§5.4 路径八条规则、§6.7 DtEvent 5 类型枚举（无新增）、§9 M5/M6 里程碑指引（M5→Part 5a、M6→Part 5b 重新对齐）。

## 1. 范围拆分

Part 5 拆为两个独立可 ship 的 child plan，避免单 plan 失控（参考 Part 4 plan 已 2259 行）：

### 1.1 Part 5a — 删除流 + archive/discard 后端端点

**目标**：解锁 Part 4 已经在前端调用但端点尚未上线的 `archiveFile` / `discardFile`；完成 session/connection 两阶段删除流。

**范围**：
- 后端 REST：`POST /api/sessions/{sid}/files/{fid}/archive`、`POST /api/files/{fid}/discard`、`DELETE /api/sessions/{sid}?force=true`、`DELETE /api/connections/{cid}?force=true`
- application 层：`FileArtifactService` 增加 `archive` / `discard` use case；扩展 `SessionService.delete` / `ConnectionService.delete` 增加两阶段
- 前端：session DELETE 终局确认 modal（参父 spec §7.5 线框，不改）+ connection DELETE 独立 modal（§A.3 新设计）+ Part 4 占位调用切真端点
- i18n keys：`files.deleteModal.*` + `connections.deleteModal.*`
- 测试：FileArtifactServiceTest 追加、FileArtifactControllerIT 扩展、SessionControllerIT 扩展、新增 ConnectionDeletionIT、前端 modal vitest

### 1.2 Part 5b — Housekeeping + Legacy 迁移 + Maintenance UI（含孤儿整理）

**目标**：独立可 ship 的治理类后台 + Maintenance 设置页 + Q2 决策引入的孤儿资产管理界面。

**范围**：
- 后端 application：`HousekeepingScheduler`（4 个任务：rotateOpencodeBackups / rotateOpencodeLogs / cleanupTrash / reconcileFileArtifacts）+ `LegacyMigrationRunner`（一次性 + .legacy-migrated marker）
- 后端 REST：`GET /api/maintenance/storage-overview`、`POST /api/maintenance/cleanup-trash`、`GET /api/maintenance/orphaned-files`、`POST /api/files/{fid}/reattach { connectionId }`
- 前端：Settings → Maintenance tab + 孤儿归档资产 Drawer（含批量 reattach / 批量 discard）
- i18n keys：`maintenance.*` 含 `maintenance.orphans.*`
- 测试：HousekeepingSchedulerTest、LegacyMigrationRunnerTest、MaintenanceControllerIT、maintenance-tab vitest、orphan-archives-drawer vitest

### 1.3 5a / 5b 顺序与依赖

- **强依赖**：5b 的 reconciler 复用 Part 2 的 `FileArtifactReconciler.reconcileSessionsAndWorkspaces()`（已实现）；5b 的孤儿入口语义依赖 5a 的 connection DELETE 行为（archived → connection_id=NULL）落地。
- **建议执行顺序**：先 5a → 再 5b。5a 完成后 Part 4 前端已可全功能 ship。
- **可否并行**：5a 与 5b 在代码层无文件级冲突（5b 不动 5a 的删除流端点）；如果有两批 agent 并行，必须 mock 5a 的 connection DELETE 行为以让 5b 测试 orphan 流程。本 design 不强制并行，按顺序最简单。

## 2. 关键设计决策（与父 spec 增量）

| 决策点 | 选择 | 替代方案 | 选定理由 |
|--------|------|----------|---------|
| Q1 范围拆分 | A — 5a + 5b 二分 | 三分 / 单一 | 5a 解锁 Part 4 是最小完整闭环；5b 独立治理可独立 ship |
| Q2 connection DELETE archived 处理 | B — connection_id=NULL 保留，文件不动 | A 父 spec 强清理 / C 用户 checkbox 选 | archived = 用户长期资产；与 session 删除的孤儿处理对称；偏离父 spec §6.2 已在本节 §0 登记 |
| Q3 connection DELETE 是否独立 modal | A — 独立 modal，聚合 counts + orphan banner | 复用 session modal / native confirm | 交互语义不同（session 逐项决策 vs connection 聚合确认）；对 Q2 决策的 UI 兑现 |
| §B.3' MVP 孤儿管理 | 含完整 reattach drawer | 仅显示 count + 文档链接 | 用户明确要求一次做完；避免 follow-up 拖延 |

## 3. 已读不回的边界

- **Chat 内联 file artifact 卡片**已在 Part 4 plan Task 11 设计完毕（含 datatalk-archive-artifact renderer + 状态徽章 + Stage 跳转），**不在 Part 5 范围**。
- **HousekeepingScheduler 不动 OpenCode 自管的 db/wal/storage/migration/auth.json**（父 spec §6.4）。
- **AI 跨 session 复用资产工具**（如 `datatalk_list_artifacts`）父 spec §11 已声明 out-of-scope；Part 5 不涉及。
- **archived 文件版本控制**父 spec §11 已声明 out-of-scope；用 §A.2 `.v2/.v3` 后缀已够。

---

## §A. Part 5a 设计

### A.1 新增 / 修改的 REST 端点

| 端点 | 方法 | 行为 |
|------|------|------|
| `/api/sessions/{sid}/files/{fid}/archive` | POST | Candidate→Archived 物理 mv：`opencode/sessions/<sid>/<file>` → `workspaces/<connId>/<file>`，重名 .v2/.v3，更新行 `status=archived`、`scope=workspace`、`session_id` 保留、`connection_id` 来自 session.connection_id、`archived_at=now`、`physical_path` 指向新路径。返回 `200 OK + FileArtifact`。 |
| `/api/files/{fid}/discard` | POST | 任意 status → `_trash`：`<原路径>` → `~/.data-talk/_trash/<connId-or-sid>__<fid>__<filename>`，更新行 `status=discarded`、`physical_path` 指向 _trash 文件。返回 `204 No Content`。 |
| `/api/sessions/{sid}` | DELETE | **修改**。新增 query param `?force=true`。Phase 1（无 force 且有 candidate）：返回 `409 + { sessionId, candidates: [...] }`。Phase 2（force=true 或无 candidate）：现有 `SessionService.deleteRecord` 走 FK CASCADE；application 层主动管理 file_artifact（temporary/candidate 行 DELETE、archived 行 session_id=NULL、子目录 rm -rf、联动清理 OpenCode session_diff/tool-output）。 |
| `/api/connections/{cid}` | DELETE | **新增（如不存在）/ 修改**。Phase 1（无 force 且 counts > 0）：返回 `409 + { connectionId, counts }`。Phase 2（`?force=true`）：递归删该 connection 下所有 session（内部调用 session DELETE 但不抛 409）；archived 行 `connection_id=NULL`（Q2）；不动 `workspaces/<cid>/` 物理目录。 |

### A.2 wire shape

#### 409 Conflict — session DELETE Phase 1

```json
HTTP 409 Conflict
{
  "error": "session_has_archive_candidates",
  "sessionId": "ses_abc",
  "candidates": [
    {
      "id": "file_artifact_xxx",
      "filename": "orders-er.md",
      "kind": "er_diagram",
      "sizeBytes": 8400,
      "title": "Orders ER",
      "summary": "Covers orders/order_items..."
    }
  ]
}
```

`title` / `summary` 可为空字符串或缺省。前端终局 modal 直接渲染该数组。

#### 409 Conflict — connection DELETE Phase 1

```json
HTTP 409 Conflict
{
  "error": "connection_has_resources",
  "connectionId": "conn_xyz",
  "counts": {
    "sessions": 12,
    "candidates": 3,
    "temporary": 8,
    "archived": 8
  }
}
```

`counts` 字段全部为 `int >= 0`。前端 modal 据此渲染聚合行 + orphan banner。

#### 200 OK — archive / reattach 成功

```json
HTTP 200 OK
{
  "id": "file_artifact_xxx",
  "scope": "workspace",
  "status": "archived",
  "kind": "er_diagram",
  "sessionId": "ses_abc",            // archive 时保留；reattach 时可能仍是原 session
  "connectionId": "conn_xyz",
  "filename": "orders-er.v2.md",     // 若发生版本递增
  "physicalPath": "/home/.../workspaces/conn_xyz/orders-er.v2.md",
  ... // 完整 FileArtifact 字段
}
```

#### 4xx / 5xx 错误码

| code | HTTP | 含义 |
|------|------|------|
| `not_found` | 404 | fid / sid / cid 不存在 |
| `wrong_status` | 409 | archive 时行 status ≠ candidate；discard 已是 discarded |
| `path_*` (8 类) | 409 | 复用 Part 1 `PathSafetyError.wire()` |
| `disk_full` | 507 | mv 时磁盘满 / 配额 |
| `mv_failed` | 503 | mv 其它 IO 异常；可 retry |
| `toctou_changed` | 503 | mv 前 stat 检测到属性变；可 retry |

### A.3 物理 mv 安全（archive / discard / reattach 共享逻辑）

集中到一个 `FileArtifactPhysicalMover` application 服务，三个端点都调用。复用 Part 1 `FileArtifactService.guardPath` 八条规则（已被 Part 3 复用一次）。

```
mv(src: Path, dstDir: Path, dstFilename: String, mode: ATOMIC_MOVE) -> Path:
  1. mkdir -p dstDir
  2. dst = dstDir / dstFilename
     while exists(dst):
        dst = applyVersionSuffix(dst)   // foo.md → foo.v2.md → foo.v3.md
                                         // 必须按最后一个 "." 切分以保留扩展名
  3. stat(src) 取 mtime + size 作为 expected
  4. stat(src) 再取一次 → expected 不一致 → throw TocTouChanged
  5. Files.move(src, dst, ATOMIC_MOVE)
     - DiskFull → throw DiskFull
     - AtomicMoveNotSupported → fallback Files.move(src, dst, REPLACE_EXISTING=false) + 显式不跨 FS
     - 其它 IO → throw MvFailed
  6. return dst
```

`applyVersionSuffix("foo.md") -> "foo.v2.md"`；`"foo.v2.md" -> "foo.v3.md"`；`"foo"`（无扩展）`-> "foo.v2"`；`"foo.tar.gz"` 仅作用最后一段 `-> "foo.tar.v2.gz"`（接受这个简化，避免对 multi-suffix 做特例）。

### A.4 application 层职责拆分

```
FileArtifactService（已存在；扩展）
├── archive(sessionId, fileArtifactId) -> FileArtifact
│     调 sessionRepo 拿 connection_id；调 mover.mv；UPDATE row；emit DtEvent.FileArtifactArchived
├── discard(fileArtifactId) -> void
│     调 mover.mv 到 _trash；UPDATE row；emit DtEvent.FileArtifactDiscarded
├── deleteSessionFileArtifacts(sessionId)  // 仅 application 层使用
│     DELETE WHERE session_id AND status IN (temporary, candidate)
│     UPDATE SET session_id=NULL WHERE session_id AND status='archived'
└── deleteConnectionFileArtifacts(connectionId)
      1. 调 connectionRepo.findById(connectionId) 拿 connection.name（必须在删除 connection 行 *之前* 拿）
      2. 调 sessionRepo 拿 connection 下所有 session_id；逐个调 deleteSessionFileArtifacts
      3. 对剩余 archived 行批量更新（同一事务）：
         UPDATE file_artifact SET
           connection_id = NULL,
           metadata_json = json_set(
             COALESCE(metadata_json, '{}'),
             '$.orphanedFromConnection', <connection.name>,
             '$.orphanedFromConnectionId', <connectionId>,
             '$.orphanedAt', <now>
           )
         WHERE connection_id = <connectionId> AND status = 'archived'
      4. 这 3 个 metadata_json 字段被 §B.3.1 orphaned-files 端点读出展示给用户；
         reattach 成功后清空（json_remove），避免遗留

SessionService.delete(sessionId, force: boolean) -> DeleteOutcome
  ├── 若 !force：检查 candidate count，若 > 0 返回 BlockedByCandidates(list)
  └── force：FK CASCADE deleteRecord + deleteSessionFileArtifacts + workdir.rm + opencode 联动清理

ConnectionService.delete(connectionId, force: boolean) -> DeleteOutcome
  ├── 若 !force：聚合 counts（sessions / candidates / temporary / archived），若任一 > 0 返回 BlockedByResources(counts)
  └── force：递归对每个子 session 调用 SessionService.delete(force=true) + deleteConnectionFileArtifacts + connRepo.delete
```

`DeleteOutcome` 是 sealed interface — `Ok | BlockedByCandidates(List<FileArtifact>) | BlockedByResources(ConnectionDeleteCounts)`。Adapter 层把 `Blocked*` 映射为 409。

### A.5 终局确认 modal — session

直接采用父 spec §7.5 线框。要点：
- 仅列 candidates；temporary 默认随 session 删（用户已知 session 子目录是工作区）
- 每个 candidate 一行单选：`[◯ 归档到 <connection 名>] [◉ 丢弃]`
- 顶部"全部归档" / "全部丢弃" 快捷
- "确认删除" 按钮直到所有候选有决策才 enabled
- focused density、prefers-reduced-motion 关闭装饰动画
- focusRing 与 i18n key 严格按 `client/DESIGN.md`

执行阶段：用户点 "确认删除" 后，前端串行：
1. 对每个 candidate 调 archive 或 discard 端点；**前端本地跟踪 per-candidate 操作状态**（`pending | in_progress | done | failed`），写到 modal 内 store
2. 全部 ack 后调 `DELETE /api/sessions/{sid}?force=true`
3. 任一步失败 → toast + 不进入 step 2，让用户 retry

**Retry 幂等约束**：retry 时前端**仅对 `failed` 状态的 candidate** 重新发起请求；`done` 的不再调用。这是必须的，否则已成功 archive 的 candidate 在 retry 时会因源文件已 mv 走而拿到 `not_found`，把整个 retry 卡死。modal 关闭前 store 不清理，关 modal 才丢状态。

### A.6 终局确认 modal — connection（新设计）

```
┌─Modal · bg.canvas · radius.lg · 480px · focused density──────────┐
│  删除连接 "prod-mysql"？                                         │
│                                                                   │
│  此连接关联：                                                     │
│  · 12 个会话（含历史消息和事件，将一并删除）                     │
│  · 3 个候选文件、8 个临时文件（将自动清理）                      │
│                                                                   │
│  ⓘ status.infoSurface · 1px border.default                       │
│    8 个已归档文件不会删除，会保留为孤儿资产；可在               │
│    Settings → Maintenance → 孤儿归档资产 中找到并整理。          │
│                                                                   │
│             [取消]                  [确认删除连接]               │
│                                  accent.primary                   │
└───────────────────────────────────────────────────────────────────┘
```

不让用户对每个文件单独决策（数量级太大）。banner 用 `status.infoSurface` 而非 warningSurface，因为 archived 不丢失是非异常正向行为。

#### 五态 token 映射（每个交互控件，per `client/DESIGN.md` + memory `feedback-design-control-states`）

- **取消按钮**（ghost）
  - idle: `bg-transparent` / `text-base` / `border-subtle`
  - hover: `bg-hover` / `text-strong` / `border-default`
  - active: `bg-active` / `text-strong`
  - focus: `outline-2 ring-focusRing`
  - disabled: `text-disabled` / cursor-not-allowed
- **确认删除按钮**（primary destructive — 用 `accent.primary` 因决策已在文案完成，按钮只 commit；不用 `status.danger` 以与 client/DESIGN.md primary action 规约一致）
  - idle: `bg-accent-primary` / `text-inverse`
  - hover: `bg-accent-primaryHover` / `text-inverse`
  - active: `bg-accent-primaryHover` / opacity 0.9
  - focus: `outline-2 ring-focusRing offset-2`
  - disabled: `bg-disabled` / `text-disabled` / cursor-not-allowed

session modal 的"全部归档" / "全部丢弃" 切换控件五态映射另在 5a child plan 完整列出（与 Part 4 五态规范同形）。

---

## §B. Part 5b 设计

### B.1 HousekeepingScheduler

**调度方式**：`@Scheduled(cron="0 0 3 * * *", zone="UTC")` Spring 原生 cron。**启动时不触发**，避免与 Part 2 `FileArtifactWatcherStartup` 已有的启动 reconcile 重复。

**4 个任务**（顺序执行，互不依赖；任一失败不影响其它）：

| 任务 | 策略 | 失败处理 |
|------|------|----------|
| `rotateOpencodeBackups` | 扫 `~/.data-talk/opencode/opencode.json.dt-bak-*` 按 mtime 排序：保留 *最近 5 份* ∪ *最近 7 天* 的并集（确保活跃期不丢） | 单文件 rm 失败 → log warn 跳过；不抛 |
| `rotateOpencodeLogs` | 扫 `~/.local/share/opencode/log/*.log` 同策略；**保底至少留 5 份**（哪怕都超 7 天） | 同上 |
| `cleanupTrash` | 扫 `~/.data-talk/_trash/*`，mtime > 7 天 → `rm <文件>` + 解析文件名前缀 `<connId-or-sid>__<fid>__<filename>` 拿到 fid → `DELETE FROM file_artifact WHERE id=<fid> AND status='discarded'` | rm 失败 → log warn；DB delete 失败 → log warn + 文件已 rm 不回滚（最终一致） |
| `reconcileFileArtifacts` | 调 Part 2 `FileArtifactReconciler.reconcileSessionsAndWorkspaces()` + 新增 `reconcileTrash()`：扫 _trash 物理孤儿（FS 有 / DB 无 discarded 行）→ rm；DB 孤儿（DB 有 discarded 行 / FS 无）→ DELETE 行 + warn DtEvent.FileArtifactDiscarded（reason="reconcile_lost"） | reconciler 已有错误处理，外层 catch + log |

**Part 2 reconciler 接口扩展**（必须的最小改动）：当前 reconciler 把 `workspaces/<cid>/` 下文件 + DB row 没找到当作孤儿补登记 TEMPORARY；本 design 的 Q2 决策引入 `connection_id IS NULL` 的 archived 行（文件留在原 workspaces/），reconciler 必须把这种状态识别为 valid，**不重新登记**。具体：reconciler 找文件对应 row 时改用 `physical_path` 而不仅仅 `connection_id + filename`，且对 `connection_id IS NULL` 的 archived 行也要 match。

**记录**：所有任务结果写 `~/.data-talk/housekeeping.log`（JSON Lines，按月轮转 `housekeeping-YYYY-MM.log`）。schema：
```json
{ "ts": "2026-05-07T03:00:00.123Z", "task": "cleanupTrash", "action": "rm", "target": "/path/...", "result": "ok" }
```
Maintenance UI 的"查看 housekeeping 日志"读最近 100 行。

### B.2 LegacyMigrationRunner

`@EventListener(ApplicationReadyEvent.class)` 触发（保证 Flyway + reconciler 已就位）；用 `~/.data-talk/.legacy-migrated` marker 文件防重复。

**白名单**（保留原位）：
```
.current, .gitignore, AGENTS.md, opencode.json,
opencode.json.dt-bak-*, package.json, package-lock.json,
plugins/, node_modules/, v*/, sessions/   // ← Part 1 引入的子目录树
```

**逻辑**：
1. 若 `.legacy-migrated` 存在 → return
2. 扫 `~/.data-talk/opencode/` **直接子项**（不递归）
3. 不在白名单的 → mv 到 `~/.data-talk/_legacy/`（mkdir -p）
4. touch `.legacy-migrated`
5. emit DtEvent.LegacyMigrated（前端 toast 显示数量）

**已知会被迁移的**：`datatalk-tools-test-report.md`、可能的孤儿 csv/log。

`_legacy/` 不进 file_artifact 体系（无 session/connection 归属）；用户在 Maintenance 看到目录路径，[查看 _legacy 目录...] 调 Tauri shell.open，不在 UI 内展开内容（避免误操作）。

### B.3 Settings → Maintenance UI

**位置**：现有 Settings 路由内新 tab，不增 sidebar 入口。

#### B.3.1 新增 REST 端点

| 端点 | 方法 | 行为 |
|------|------|------|
| `/api/maintenance/storage-overview` | GET | 返回存储概览（lazy 计算，预计 < 200ms） |
| `/api/maintenance/cleanup-trash` | POST | 立即跑 cleanupTrash（不等 cron）；返回清理统计 |
| `/api/maintenance/orphaned-files` | GET | 返回 `status='archived' AND connection_id IS NULL` 的 `FileArtifact[]`，含原 `session_id` 与 `metadata_json` 的 `orphanedFromConnection` / `orphanedFromConnectionId` / `orphanedAt`（由 §A.4 在 connection 删除时写入）；`LIMIT 200` 兜底 |
| `/api/files/{fid}/reattach` | POST `{ connectionId }` | 把 archived 文件从 `workspaces/<已删 cid>/` mv 到 `workspaces/<新 cid>/`，复用 §A.3 mover；UPDATE row `connection_id=<new>`、`physical_path=<new>`；同时 `json_remove(metadata_json, '$.orphanedFromConnection', '$.orphanedFromConnectionId', '$.orphanedAt')` 清孤儿元；emit DtEvent.FileArtifactArchived（前端按新 connection_id 重新分组） |

#### B.3.2 storage-overview 响应

```json
{
  "workdir": "/home/.../.data-talk",
  "totalBytes": 358000000,
  "breakdown": {
    "opencodeInfra":  { "bytes": 286000000, "label": "OpenCode 基础设施" },
    "sessions":       { "bytes": 21000000,  "sessionCount": 3, "label": "Sessions" },
    "workspaces":     { "bytes": 28000000,  "archivedCount": 8, "connectionCount": 2, "orphanedArchivedCount": 3, "label": "Workspaces (资产)" },
    "trash":          { "bytes": 5000000,   "count": 3, "oldestDays": 4, "label": "_trash" },
    "legacy":         { "bytes": 2000000,   "count": 1, "label": "_legacy" }
  },
  "lastHousekeepingRunAt": "2026-05-07T03:00:00Z"
}
```

#### B.3.3 UI 主页

```
Settings: General │ Models │ Connections │ Maintenance ●

存储概览                                          [刷新]
  工作目录    /home/.../.data-talk
  总占用      342 MB                        最近治理 03:00 UTC
   ├─ OpenCode 基础设施   286 MB
   ├─ Sessions            21 MB   (3 个活跃 session)
   ├─ Workspaces (资产)   28 MB   (8 资产 / 2 connection)
   │   └─ ⚠ 孤儿归档资产 (3 个)   [整理...]
   │      连接已被删除，文件保留为孤儿。
   ├─ _trash              5 MB    (3 项 · 最旧 4 天)
   └─ _legacy             2 MB    (1 项)

操作
  [立即清空 _trash]  [查看 _legacy 目录...]  [查看 housekeeping 日志]
```

**数据新鲜度**：tab 打开时 lazy 计算（同步 `Files.walkFileTree`，预计 < 200ms）+ 手动 [刷新]；不做后台缓存；计算中显示 skeleton。

#### B.3.4 孤儿归档资产 Drawer

孤儿计数行 [整理...] 按钮 → 右侧 Drawer（width 480px，density=focused，不另开 route）。

```
┌─ Drawer · 孤儿归档资产 (3) ─────────────────────────────┐
│  这些文件原属于已删除的连接，未被自动清理。可重新关联    │
│  到现有连接，或丢弃到 _trash（7 天后自动清理）。         │
│                                                          │
│  [全选]  [批量关联到 ▾ prod-mysql]  [批量丢弃]          │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │ ☐ 📊 orders-er.md        ER Diagram · 8.4 KB     │   │
│  │   原属于："开发-mysql" (已删除)                  │   │
│  │   归档于 2026-04-29                              │   │
│  │            [关联到 ▾]  [丢弃]                    │   │
│  ├──────────────────────────────────────────────────┤   │
│  │ ☐ 📄 weekly-report.md    Report · 32.4 KB        │   │
│  │   ...                                             │   │
│  └──────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
```

**交互**：
- 行级 "原属于" 字段从 `metadata_json.orphanedFromConnection` 读取（由 §A.4 在 connection 删除时写入），格式 `原属于："<原 connection 名>" (已删除)`；若该字段缺失（极端 fallback），降级为 `原属于：已删除的连接 (<conn id>)`，从 physical_path 解析 cid
- "关联到 ▾" 下拉显示 *当前所有活跃 connection* 列表（从 `useConnectionStore` 取）
- 选完 connection → POST reattach → 行从列表消失（drawer 自动刷新）
- 批量：勾选 + 顶部批量操作；批量 reattach 到同一 connection；批量 discard 全部 mv `_trash`
- Empty state（孤儿被清完）：drawer 自动关闭 + toast `"所有孤儿资产已整理"`
- 批量中途失败：每个文件独立请求；返回 `{ succeeded: [...ids], failed: [{ id, reason }] }`；UI 列出 failed 子集 + retry
- 数量 > 200：API 返回最多 200，drawer 顶部提示 "显示前 200 条，请清理后再看下一批"
- **0 个活跃 connection**（用户删完了唯一 connection 只剩孤儿）：批量 reattach 下拉 + 行级 "关联到 ▾" 都 disabled，顶部 banner 显示 `"没有可关联的连接，请先新建连接（或丢弃这些孤儿资产）"`；批量丢弃 + 行级丢弃仍可用

#### B.3.5 五态 token 映射（drawer + maintenance tab 关键控件）

**search/filter（暂未引入 — 5b MVP 不做 search；arrange 列表足够小）**：N/A

**重要交互控件**：

- **批量关联下拉触发** (`button[role="combobox"]`)
  - idle: `bg-panel` / `border-subtle` / `text-base`
  - hover: `bg-panel` / `border-default` / `text-strong`
  - active（打开）: `bg-panel` / `border-default` / `text-strong` / chevron 旋转
  - focus: `outline-2 ring-focusRing`
  - disabled: `bg-subtle` / `text-disabled`
- **批量丢弃按钮**（destructive ghost）
  - idle: `bg-transparent` / `text-base` / `border-subtle`
  - hover: `bg-status-dangerSurface` / `text-status-danger` / `border-status-danger`
  - active: `bg-status-dangerSurface` opacity 0.9
  - focus: `outline-2 ring-focusRing`
  - disabled: `text-disabled` / cursor-not-allowed / no hover
- **行级 [关联到 ▾] / [丢弃]**（ghost）
  - 同 §A.6 ghost 五态
- **复选框** (`input[type="checkbox"]`)
  - idle: `border-default` / unchecked
  - hover: `border-strong` / `bg-hover`
  - active（按下）: `border-accent-primary` / `bg-accent-primaryHover`
  - focus: `outline-2 ring-focusRing`
  - checked: `bg-accent-primary` / `text-inverse` ✓
  - disabled: `border-disabled` / `bg-disabled` / cursor-not-allowed

### B.4 i18n keys

加到 `client/src/i18n/messages.ts`（中英对齐）。

```
maintenance.tab.title
maintenance.overview.workdir
maintenance.overview.totalSize
maintenance.overview.lastHousekeepingRunAt
maintenance.overview.refresh
maintenance.breakdown.opencodeInfra
maintenance.breakdown.sessions
maintenance.breakdown.workspaces
maintenance.breakdown.trash
maintenance.breakdown.legacy
maintenance.breakdown.orphanedArchivedCount

maintenance.action.cleanupTrash
maintenance.action.viewLegacy
maintenance.action.viewLog
maintenance.toast.cleanupTrashDone
maintenance.toast.legacyMigrated     // for DtEvent.LegacyMigrated

maintenance.orphans.entry            // "整理..."
maintenance.orphans.drawer.title
maintenance.orphans.description
maintenance.orphans.column.originalConnection
maintenance.orphans.column.archivedAt
maintenance.orphans.action.selectAll
maintenance.orphans.action.reattach
maintenance.orphans.action.reattachBulk
maintenance.orphans.action.discardBulk
maintenance.orphans.action.discard
maintenance.orphans.toast.allCleaned
maintenance.orphans.toast.reattachOk
maintenance.orphans.toast.reattachPartial
maintenance.orphans.confirm.discardBulk    // "丢弃 N 个文件？这些文件会进入 _trash..."
maintenance.orphans.tooltip.over200
maintenance.orphans.banner.noActiveConnection   // "没有可关联的连接，请先新建连接（或丢弃这些孤儿资产）"
```

5a session/connection modal i18n（与 5b 分离，但都在本 design 内统一登记）：

```
files.deleteModal.title
files.deleteModal.description
files.deleteModal.allArchive
files.deleteModal.allDiscard
files.deleteModal.confirm
files.deleteModal.cancel

connections.deleteModal.title
connections.deleteModal.summarySessions
connections.deleteModal.summaryCandidates
connections.deleteModal.summaryTemporary
connections.deleteModal.orphanBanner       // "N 个已归档文件不会删除，会保留为孤儿资产..."
connections.deleteModal.confirm
connections.deleteModal.cancel
```

---

## §C. DtEvent / 测试矩阵 / 风险

### C.1 DtEvent 增量

**0 个新事件**。父 spec §6.7 的 5 个事件已覆盖 Part 5 所有场景：

- `FileArtifactDetected` — 不变（Part 2 watcher）
- `FileArtifactArchiveRequested` — 不变（Part 1 markCandidate / Part 3 archiveCandidate）
- `FileArtifactArchived` — 5a archive 端点 + 5b reattach 端点共用（payload 含 `connectionId`，前端 store 据此 re-group）
- `FileArtifactDiscarded` — 5a discard 端点 + 5b cleanupTrash + reconcile 孤儿 candidate 共用（payload 含 `reason: "user" | "session_deleted" | "connection_deleted" | "reconcile_lost" | "trash_expired"`）
  - **`session_deleted` / `connection_deleted` 的 reason 仅用于 `temporary` / `candidate` 文件**（这些文件随 session/connection 删除而被 mv 到 `_trash`）。Q2 决策后 `archived` 文件**不**因 connection 删除而 discard，因此 `archived` 行不会带 `connection_deleted` reason；`archived` 行的"被丢弃"仅来自用户主动 discard（`reason="user"`）或 _trash 7 天清理（`reason="trash_expired"`，针对已是 discarded 状态的行）
- `LegacyMigrated` — 5b LegacyMigrationRunner（payload `filesMovedCount`）

application 层 exhaustive switch 已对这 5 个全覆盖；5a/5b 不增加 case。

### C.2 测试矩阵

#### Part 5a 测试

| 层 | 文件 | 关键测试 |
|----|------|---------|
| Application | `FileArtifactServiceTest`（追加） | `archive` happy path、磁盘满模拟、TOCTOU race（mv 前文件被改）、版本递增 .v2/.v3、扩展名保留；`discard` 任意 status → DISCARDED；`deleteSessionFileArtifacts` 边界（archived 行 session_id=NULL、temporary/candidate 删行）；`deleteConnectionFileArtifacts` 边界（archived 行 connection_id=NULL、子 session 全删） |
| Application | （新增）`FileArtifactPhysicalMoverTest` | mover 单测：mkdir 首次创建、版本递增 .v2 .v3、TOCTOU、AtomicMoveNotSupported fallback |
| Adapter | `FileArtifactControllerIT`（扩展） | POST archive 8 例（happy / 404 / wrong status / disk full / mv failed / TOCTOU / repeated archive idempotent? / 路径校验）；POST discard 4 例；DELETE session 两阶段（无 candidate 直通、有 candidate 409 + body shape、force=true 走 Phase 2）；DELETE connection 两阶段（counts 聚合、archived 保留为孤儿、子 session 删除联动） |
| Adapter | `SessionControllerIT`（扩展） | session DELETE 行为兼容性（既有 deleteAll 不变） |
| Adapter | （新增）`ConnectionDeletionIT` | connection DELETE force 后 archived 行 connection_id=NULL、文件留在 workspaces/、子 session 全删 |
| Frontend | `delete-session-modal.test.tsx` | 候选清单、批量切换（全部归档 / 全部丢弃）、确认按钮 disable 直到决策完成、prefers-reduced-motion |
| Frontend | `delete-connection-modal.test.tsx` | 聚合 counts 渲染、orphan banner（archived > 0 时显示，= 0 时隐藏）、确认按钮 |
| Frontend | `file-artifacts-store.test.ts`（扩展，已在 Part 4 创建） | archive / discard / reattach 调用更新 store 的逻辑（与 Part 4 已写的 5 个 SSE event case 互补） |

#### Part 5b 测试

| 层 | 文件 | 关键测试 |
|----|------|---------|
| Application | `HousekeepingSchedulerTest` | 4 任务独立单测：N+D 并集策略（fixture A：5 份且都 < 7 天 → 全留；fixture B：10 份且最旧 30 天 → 留最近 5 份）；trash 7 天阈值；reconcile 复用 Part 2 接口 mock 验证；某任务异常不影响其他 |
| Application | `LegacyMigrationRunnerTest` | 白/黑名单（含 sessions/）；marker 防重复（已存在 → no-op）；空目录无副作用；DtEvent.LegacyMigrated 发射 |
| Application | `FileArtifactReconcilerTest`（扩展） | Q2 引入：connection_id IS NULL 的 archived 文件不被错误重新登记 |
| Adapter | `MaintenanceControllerIT` | GET storage-overview body shape；POST cleanup-trash 立即触发；GET orphaned-files 列表 + LIMIT 200 兜底；POST reattach happy + partial fail（多文件批量） |
| Frontend | `maintenance-tab.test.tsx` | 存储概览渲染、孤儿计数行（count > 0 显示、= 0 隐藏）、cleanup-trash 按钮、刷新、最近治理时间格式化 |
| Frontend | `orphan-archives-drawer.test.tsx` | drawer 渲染、单/批量 reattach、批量 discard、empty state 自动关闭、partial-failure 列出 failed 子集 |

### C.3 不测什么（YAGNI）

- 不测 `Files.move(ATOMIC_MOVE)` 在所有 FS 类型（trust JDK）
- 不做并发归档压测（单用户桌面应用）
- 不测 Maintenance UI 大数据量性能（孤儿 200 上限内）
- 不测 cron 真实跑（用 Spring `@SpringBootTest` 注入 `TaskScheduler` mock）
- 不为 io.methvin / Spring Scheduling 自身写测试

### C.4 风险与权衡

| 风险 | 缓解 |
|------|------|
| archive mv 时磁盘满 → 半成品文件 | ATOMIC_MOVE 保证原子；失败时 row 不变、UI retry；返回 `disk_full` 错误码 |
| connection 删除后 `workspaces/<已删 cid>/` 留在磁盘 | Q2 决策的代价；通过 §B.3 Maintenance UI 让用户主动整理；reconciler §B.1 改动确保不重新登记 |
| reattach 中途失败 → 部分文件已 mv，部分未 mv | 端点返回 `{ succeeded, failed }`，不强行整体回滚；前端展示 partial 状态；用户对 failed 子集 retry |
| Phase 1 409 响应 body 与 Part 4 前端契约漂移 | Part 5a 实现敲定后立即同步 Part 4 plan（已写好但未执行）的 archiveFile/discardFile 调用点；i18n keys 在 5a 一并加 |
| HousekeepingScheduler 与 Part 2 watcher startup reconcile 重复 | 5b cron 03:00 UTC 不在启动时跑；Part 2 startup reconcile 不变 |
| Legacy 迁移误把用户重要文件移走 | 白名单严格；`_legacy/` 不删除文件，只 mv；marker 防重复；DtEvent toast 让用户知晓；用户可手工取回 |
| `.v2/.v3` 后缀对扩展名敏感 | mover 按最后一个 `.` 切分以保留 ext；`foo.tar.gz` → `foo.tar.v2.gz`（接受简化） |
| 孤儿 drawer 数量超过 200 | API LIMIT 200 + 顶部提示"显示前 200 条"；不做分页（典型场景孤儿数应 < 几十） |
| OpenCode `session_diff/ses_*.json` 联动清理失败 | log warn 跳过，不阻塞 session 删除主流程；下一次 cron reconcile 时再清理 |
| ConnectionService 在 infrastructure 层（与 SessionService 在 adapter 不同） | 5a 实现 connection DELETE 两阶段时严守现有分层；如果需要应用层 orchestrator，新建 `ConnectionDeletionService` 在 application 层；不重构现有 ConnectionController（避免范围扩散） |
| §A.4 写 metadata_json `orphanedFromConnection` 时 connection 已 / 还未删除？ | 必须在 `connection_id` 置 NULL **之前** 拿到 connection.name（见 §A.4 顺序）；同一事务内先 `findById` 再 `update`，再删 connection 行；任一步失败整体回滚，避免 row 已 NULL 但 metadata 没写入造成"无法识别原归属"的 dead 孤儿 |
| 修改 `metadata_json` 用 SQLite `json_set` 在多平台（H2/PostgreSQL）兼容性？ | 现有 SQLite metadata 仓 = data-talk.db；H2 用作测试。H2 也支持 json 函数语法但非完全兼容。实现时建议改用 application 层先 `SELECT metadata_json` → Java 反序列化为 Map → 修改 → 序列化写回，避免依赖方言差异；性能上 archived 行批量更新一次性拉一个 connection 下的全部行可接受 |

### C.5 范围之外（明确不做）

- session/connection DELETE 的"撤销"功能（删了就删了，靠 _trash 7 天反悔单文件）
- 跨 connection 移动 archived 文件（除孤儿 reattach 外）—— 留作 follow-up
- archived 文件的版本历史 / diff 视图（父 spec §11）
- 性能监控 dashboards（父 spec §11）
- 多用户 / 共享 ACL（父 spec §11）
- AI 跨 session 资产复用工具（父 spec §11，独立 brainstorming）
- `_legacy/` 内容浏览器（仅暴露目录路径 + Tauri shell.open）
- 孤儿资产搜索 / 筛选（200 上限内不必要）

## §D. 实施里程碑

| Sub-plan | 范围 | Demo |
|----------|------|------|
| **Part 5a** | application：archive/discard/deleteSession*/deleteConnection* + DeleteOutcome；adapter：4 个端点 + 409 wire shape；frontend：session modal + connection modal + Part 4 占位调用切真端点；i18n 全 keys；测试见 C.2 | 用户能从 Part 4 Files Tab 点 [✓ 归档] 真把文件 mv 到 workspaces/；session/connection 删除走两阶段并保护资产 |
| **Part 5b** | application：HousekeepingScheduler + LegacyMigrationRunner + reconciler 扩展；adapter：4 个 maintenance 端点；frontend：Settings Maintenance tab + 孤儿归档资产 Drawer；i18n maintenance.* + maintenance.orphans.*；测试见 C.2 | 启动后看到 LegacyMigrated toast + Maintenance tab 显示存储概览；孤儿 archived 资产可 reattach 或丢弃 |

每份 child plan 独立 PR、独立可演示。5a 解锁 Part 4 完整 ship；5b 是治理收尾。

## §E. 与父 spec 的同步项

Part 5 实现完成后，必须同步更新父 spec [2026-04-29-opencode-workdir-and-artifact-system-design.md](./2026-04-29-opencode-workdir-and-artifact-system-design.md)：

1. **§6.2 connection DELETE 行为段** — 改为 Q2 的"connection_id=NULL 保留"
2. **§9 实施里程碑表** — M5 → Part 5a / M6 → Part 5b 的对应关系明确
3. **§11 范围之外** — 增加"跨 connection 移动 archived 文件（除孤儿 reattach 外）"
4. **§12 数据建模** — 增加 maintenance / orphan REST 端点行
5. **末尾完成度快照** — 加 Part 5a / 5b 状态行

## 相关文档

- 父 spec：[OpenCode Workdir & Artifact System](./2026-04-29-opencode-workdir-and-artifact-system-design.md)
- Part 1 plan：[Migration & Domain](../exec-plans/2026-04-29-file-artifact-system-part1-domain-and-migration-plan.md)（shipped）
- Part 2 plan：[Watcher & Reconcile](../exec-plans/2026-04-30-file-artifact-system-part2-watcher-reconcile-plan.md)（shipped）
- Part 3 plan：[MCP Tool & AGENTS Template](../exec-plans/2026-04-30-file-artifact-system-part3-mcp-and-agents-template-plan.md)（planned, not executed）
- Part 4 plan：[Frontend Tabs](../exec-plans/2026-04-30-file-artifact-system-part4-frontend-tabs-plan.md)（planned, not executed）
- 客户端设计契约：[../../client/DESIGN.md](../../client/DESIGN.md)
- 数据源兼容 gate：[../DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md) — N/A，Part 5 不涉及 DB 类型

---

**Spec self-review 通过项**：

- 无 TBD / TODO 占位
- 内部一致：Q2 决策与 §A.6 modal banner、§B.3 孤儿入口、§C.4 风险表三处呼应
- 范围聚焦：5a + 5b 两份 child plan 各自闭环
- 无歧义：所有 wire shape 给出完整 JSON，所有错误码列出 HTTP 映射
- 数据源兼容性 Gate：N/A（已在文末声明）
- BUG 区域核对：grep `docs/bugs/` 0 命中（无 file_artifact / FileArtifact 相关 BUG）
- frontend design contract：§A.6 + §B.3.5 显式列出五态 token 映射，符合 `client/DESIGN.md` + memory `feedback-design-control-states`
