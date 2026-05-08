# Dashboard ↔ File Artifact 集成增强 Design

- **Created:** 2026-05-09
- **Status:** Design (pending plan)
- **作者代理:** Claude Opus 4.7 (1M context)
- **触发上下文:** [docs/exec-plans/2026-05-08-report-dashboard-p1-plan.md](../exec-plans/2026-05-08-report-dashboard-p1-plan.md) Task B4 在审查中发现假设的 `FileArtifactService.create` / `readBytes` / `replaceBytes` API 与 `FileArtifactKind.DASHBOARD` 枚举值在当前代码中均不存在；本 spec 给出对应的集成增强方案，使 Dashboard 文档真正进入 file_artifact 表与生命周期管理体系。
- **关联 spec:**
  - [docs/product-specs/2026-05-08-report-dashboard-design.md](./2026-05-08-report-dashboard-design.md) §10 持久化语义
  - [docs/product-specs/2026-05-07-file-artifact-system-part5-design.md](./2026-05-07-file-artifact-system-part5-design.md) §A.1 connection 删除两阶段
  - [docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md](./2026-04-29-opencode-workdir-and-artifact-system-design.md) §6 file_artifact 系统总体

---

## 1. Problem Statement

### 1.1 现状（事实）

仓库当前**没有** `DashboardStore` / `DashboardArtifactService` 类，也**没有**启用 `FileArtifactKind.DASHBOARD` 枚举值——所有相关讨论都基于尚未执行的 `2026-05-08-report-dashboard-p1-plan.md` Task B4 提案。该 plan 的代码片段假设以下 API 已存在，但**事实上均不存在**：

- `FileArtifactService.create(...)` — 不存在；现有 `FileArtifactService` 仅暴露 `archiveCandidate` / `archive` / `discard`（`server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java:369/497/563`）
- `FileArtifactService.readBytes(...)` — 不存在
- `FileArtifactService.replaceBytes(...)` — 不存在
- `FileArtifactKind.DASHBOARD` — 当前 `FileArtifactKind` 枚举仅 5 值 `REPORT / ER_DIAGRAM / SQL_SCRIPT / DATASET / OTHER`（`server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/FileArtifactKind.java`）
- `file_artifact.kind` 列被 CHECK 约束硬编码到 5 值（V14 migration），加 `dashboard` 必须做 SQLite 整表 rebuild

### 1.2 真实缺口（5 项）

如果 dashboard 持久化绕开 file_artifact 体系（自建 `~/.data-talk/dashboards/` + 自管 DB 行），下游有 5 个真实缺口：

1. **`FileArtifactKind.DASHBOARD` 死代码**：枚举值不启用，与 spec §10.1 的"file_artifact.kind = 'dashboard'"约定割裂
2. **`ConnectionDeletionService` 不清理 dashboard 孤儿**：当前 `detachArchivedFromConnection` 只 UPDATE `file_artifact` 表中 `scope='workspace' AND status='archived'` 的行（`JdbcFileArtifactRepository.java:248`）。dashboard 不在此表 → connection 删除时 dashboard 无任何归属切换 → spec §10.5 两阶段语义断
3. **Files Library 看不见 dashboard**：前端 `connection-files` controller 列举 `file_artifact` 表的 archived workspace 行；dashboard 不在表里 → Library 渲染缺位
4. **Housekeeping 失效**：`HousekeepingScheduler` / `_trash` 7 天清理 / `FileArtifactReconciler` 全部基于 `file_artifact` 表运转，dashboard 一律豁免，长期运行盘上累积孤儿
5. **前端 TypeScript 类型不同步**：`client/src/services/api/file-artifacts.ts` 的 `FileArtifactKind` 联合类型为 `'report' | 'er_diagram' | 'sql_script' | 'dataset' | 'other'`，缺 `'dashboard'`；`ConnectionFileGroups` 的 `EMPTY_KIND_GROUPS` 也无 dashboard 条目。即使后端把 dashboard 行写入 file_artifact 表，前端反序列化也无法正确分组

> **澄清**：原诊断曾把"FTS 全文搜索 dashboard 不在索引内"列为缺口，经核实**不成立**——`file_artifact` 表本身**没有**FTS 索引（V14 migration 只有 3 个普通索引：`session_id` / `connection_id` / `status`）。FTS 索引在 `stage_tab_index` 虚拟表（V12 migration），由 dashboard Stage Tab 通过 `tab-type-registry.dashboard.extractContent`（spec §10.3）灌入 stage_tabs 路径——本 spec 不涉及 FTS。

### 1.3 核心矛盾

`2026-05-08-report-dashboard-design.md` §10.1 经 Hybrid widget override 论证，**明确选择**：

> dashboard 物理路径 `~/.data-talk/dashboards/<dashboardId>.dashboard.json`（**不**按 connection 目录分仓 — Hybrid connection 下 widget 可 override 连接，强绑 connection 目录会与 override 矛盾；改 defaultConnectionId 时不触发文件移动）

但 `FileArtifactService` 当前的目录约定是：

- `scope=session` ↔ `~/.data-talk/sessions/<sid>/...`
- `scope=workspace` ↔ `~/.data-talk/workspaces/<connectionId>/...`

`workspaces/<connectionId>/` 是硬编码的——`scope=workspace` 时物理路径必须由 `connection_id` 决定。这与 §10.1 "connection_id 字段表归属、不表物理位置"的诉求**直接冲突**。

---

## 2. Constraints (Non-negotiable)

| 编号 | 约束 | 来源 |
|---|---|---|
| C1 | dashboard 物理路径**不**绑 connection 目录；改 `defaultConnectionId` 不触发文件移动 | 2026-05-08-report-dashboard-design.md §10.1 |
| C2 | connection 删除两阶段语义（detach 阶段把 dashboard 行 connection_id 置 NULL，文件不动）必须等价 file_artifact Part 5a `Q2` 决策 | 2026-05-08-report-dashboard-design.md §10.5；2026-05-07-file-artifact-system-part5-design.md §A.1 |
| C3 | ui_patch 原子性 + baseVersion 校验必须保留；校验**必须基于持久化态**（不允许 batched/buffered 落盘） | 2026-05-08-report-dashboard-design.md §11.3 |
| C4 | dashboard 体积 ≤ 256 KB | 2026-05-08-report-dashboard-design.md §10.1 |
| C5 | 不引入新的 `FileArtifactScope` 枚举值（保持 SESSION / WORKSPACE 二态） | 现有抽象边界 |

---

## 3. 方案空间与对比

### 3.1 方案 A — External-Registered Artifact（推荐）

**思路**：dashboard 自管 `~/.data-talk/dashboards/` 目录的物理写盘（atomic write：temp + fsync + rename），通过新 API 在 `file_artifact` 表登记一行（`scope=workspace`、`status=archived`、`connection_id=defaultConnectionId | NULL`、`external=1`、`physical_path=绝对路径`），让 reconciler / connection 删除 / Files Library / housekeeping 一致复用。

### 3.2 方案 B — Kind-Driven Directory Router

**思路**：`FileArtifactService` 学会"`scope=workspace` 时按 `kind` 决定目录"——`DASHBOARD` 落 `workspaces/_dashboards/<id>.dashboard.json`，`connection_id` 字段独立维护。

### 3.3 方案 C — 改 spec，dashboard 强绑 connection 目录

**思路**：抛弃 §10.1，把 dashboard 路径放回 `workspaces/<defaultConnectionId>/dashboards/<id>.dashboard.json`，重绑 connection 时触发文件移动。

### 3.4 取舍对比

| 维度 | 方案 A | 方案 B | 方案 C |
|---|---|---|---|
| 是否破 C1 (§10.1) | 否 | 否 | **破**（直接回退） |
| 是否破 C2 (§10.5) | 否 | 否 | 部分（"connection 删除文件不动"语义需要重证明） |
| FileArtifactService 改动量 | 中（+3 API + 1 列 + 1 reconciler 旁路） | 中-大（PhysicalMover/Reconciler/SessionWorkdirRoot 三处分支） | 几乎零 |
| FrontmatterParser 改动 | 否（external 行不走 frontmatter） | 是（API-created kind=DASHBOARD 不应走 frontmatter） | 否 |
| 未来通用性 | 高（`registerExternal` 抽象适用于其它"物理路径自管"场景） | 中（仅服务 dashboard，未来要复用还得继续重构） | 无 |
| 决策风险 | 低（external 标记是简单旁路） | 中（reconciler 与 PhysicalMover 改动放大测试面） | **高**（spec §10.1 决策被推翻，hybrid override 语义需重证） |

### 3.5 选 A 的论证

- **保住 spec §10.1 / §10.5 决策**：方案 A 不需要重新论证 hybrid widget override 语义
- **改动可控**：reconciler 加一段旁路 + Service 加 3 个新 API + DB 加 1 列；不重写已有的 `archiveCandidate` / `archive` / `discard` 三套主流程
- **未来复用价值**：`registerExternal` 是有泛用价值的——若以后有"导入的外部文件"、"IDE 外部工作区文件"等"物理路径不由 FileArtifactService 拥有但需要进 file_artifact 表"的场景，可直接复用
- **避免 frontmatter parser 复杂化**：dashboard 是 JSON 文件，无法塞 YAML frontmatter；方案 A 通过 `registerExternal` 绕开 frontmatter 路径，方案 B 必须新增"API-created 元数据外部传入"分支

---

## 4. 关键决策点（5 条）

### 4.1 D1：external 标记的存放位置 — 新加 `external` BOOLEAN 列

**选定**：在 V15 migration 重建 `file_artifact` 表时（反正 `kind` CHECK 约束要 rebuild 才能加 `'dashboard'`），同步新增 `external BOOLEAN NOT NULL DEFAULT 0` 列。

**论证**：
- `kind` CHECK 必须 rebuild → 多加一列边际成本几乎为零
- 直接 SQL 过滤：`WHERE external = 0`（reconciler 高频扫描场景），不必逐行解析 `metadata_json`
- 未来扩展路径清晰：若出现第 3 种 path 归属（imported / shared 等），可再次 rebuild 把 `BOOLEAN external` 升级为 `TEXT path_owner CHECK (path_owner IN ('managed', 'external', ...))`

**否决**：
- 塞 `metadata_json`（已被 orphan 标记字段使用）：reconciler 扫描时每行解析 JSON 性能差、不可索引、不可加 CHECK
- `physical_path_kind` 三态枚举：当前只有"自管 vs 外部"两态，第三种场景不存在 → YAGNI

### 4.2 D2：Dashboard 物理文件的写盘 owner — `DashboardArtifactService` 自管

**选定**：`DashboardArtifactService` 自己负责物理写盘（temp file + fsync + atomic rename），落盘成功后调用 `FileArtifactService.registerExternal(...)` **仅登记 DB 行**；`FileArtifactService` 不踏 `~/.data-talk/dashboards/` 物理路径。

**论证**：
- 边界清晰：`FileArtifactService` 责任收敛在 DB 行 + lifecycle，不参与外部路径文件 IO
- patch 高频写入路径短：每次 patch 直接走 `replaceBytesAtomic(artifactId, bytes)`，不绕一层"先 read external metadata 再决定写哪"
- atomic-write 实现一处即可：复用 `DashboardArtifactService` 内的 atomic writer，不必在 `FileArtifactService` 重做

**否决**：
- `FileArtifactService.registerExternal` 接管写盘：`FileArtifactService` 必须显式知道外部路径分布，API 形态变得"接受 path 又接受 bytes 又决定怎么写"，三种心智混在一起
- 抽 `AtomicFileWriter` 公共类：YAGNI，dashboard 是当前唯一外部 atomic write 场景；将来真有第二类需求再抽

### 4.3 D3：dashboard 行的 status 取值 — 复用 `archived`，重新校准语义

**选定**：dashboard 行直接以 `status=archived` 落 file_artifact 表；`archived_at` 在 promote 时刻设置；后续每次 patch 不改 status，只改 `updated_at` 与 `size_bytes`。**重要语义校准**：spec 之后 `archived` 不再等价 `frozen / read-only`——其精确含义是"已固化到 workspace 命名空间"，**允许**通过专属 API（如 `replaceBytesAtomic`）改写。

**论证**：
- `detachArchivedFromConnection` 当前 SQL 写死 `status='archived'`（`JdbcFileArtifactRepository.java:248`）→ dashboard 自动被覆盖 → ConnectionDeletionService 零改动
- `Files Library` controller `listArchivedByConnection` 也只看 archived 行 → dashboard 自动出现
- `FileArtifactReconciler.reconcileWorkspacesTree` 只对 archived workspace 行做存在性检查 → dashboard 行有 external=1 旁路即可保护

**否决**：
- 新加 `live` 状态值：CHECK 又要 rebuild 一次，且后续 detach SQL / Files Library SQL / Reconciler 都要改 `IN ('archived', 'live')` 三处，扩大测试面，未带来真实区分价值
- 用 NULL status：与现有 NOT NULL CHECK 冲突；status 列的初衷是生命周期，不应被 path 归属"借用"

**语义校准的**传播范围**：
- `FileArtifactReconciler` / Files Library / 任何下游消费者**不得**假设 `archived = read-only`
- spec **必须**明文记录此校准（本 spec §6）
- 实施时对所有触及 `status==ARCHIVED` 的现有代码做一次审计（plan Task A2 范围）

### 4.4 D4：patch 高频写入的落盘节奏 — 同步落盘，前端 debounce

**选定**：每次到达后端的 ui_patch 立即触发 atomic write + DB version bump；后端**不**做 batching / coalescing。前端 `dashboard-tabs-store` 在拖拽 / resize 期间合并改动，拖拽结束（或 250ms 静默）才发出一次 ui_patch。

**论证**：
- C3 要求 baseVersion 校验基于持久化态——后端 buffer 会让"未落盘的内存版"成为另一个并发写入者看不到的隐藏顶
- 同步落盘 = 简单可调试；崩溃恢复无需 WAL replay
- `replaceBytesAtomic` 通过 OS 层 atomic rename 保证原子性，不存在"半写文件"风险
- 前端 debounce 是用户感知层最佳节流位置：拖拽中不发请求，松手才发

**否决**：
- 后端再加一层 debounce buffer：与 C3 冲突；崩溃丢数据；并发 patch 检测失效
- WAL 模式：完整子系统，远超 P1 需求

### 4.5 D5：dashboards/ 目录反向孤儿清理 — `FileArtifactReconciler` 扩展一档

**选定**：`FileArtifactReconciler` 新增 `reconcileExternalDirs(List<Path> dirs)` 方法。在 `runFullReconcile` 末尾调用，传入 `[dashboardsRoot()]`。该方法**双向覆盖**外部目录的孤儿：

- **盘有 × DB 无** → 删盘文件
- **DB 有（external=1 行）× 盘没** → `deleteById` + `publishDiscarded`

**不做** adopt（不试图把"盘有 × DB 无"的文件认领为新 dashboard，因为 dashboard 元数据 id/version/timestamps 必须由 promote 流程生成，不能由扫描推导）。

**论证**：
- 集中 housekeeping owner：reconciler 已经是孤儿清理责任方，dashboard 不另起独立定时任务
- 双向合并到同一方法：单次扫盘（`Files.list(dir)`）+ 单次 DB 查询（`findExternalRowsByDir`）即可同时覆盖两方向，避免两次 IO
- "盘有 × DB 无"出现窗口：dashboard atomic rename 完成后、`registerExternal` INSERT 完成前进程被 kill
- "DB 有 × 盘没"出现窗口：用户手动删 dashboard 文件 / 磁盘损坏 / FS 一致性故障

**事实勘察**：现有 `reconcileWorkspacesTree`（行 143-156）只对 `external=0` 的 archived 行做 `Files.exists` 检查；本决策让 external 行从该路径**旁路**（详 §5.4），统一交给 `reconcileExternalDirs` 双向处理。

---

## 5. Target Architecture

### 5.1 数据库 Schema 变更（V15 migration）

SQLite CHECK 约束不能直接 ALTER，必须整表 rebuild：

```sql
-- V15__file_artifact_dashboard.sql

-- 1. 新表
CREATE TABLE file_artifact_new (
  id            TEXT PRIMARY KEY,
  scope         TEXT NOT NULL CHECK(scope IN ('session','workspace')),
  status        TEXT NOT NULL CHECK(status IN ('temporary','candidate','archived','discarded')),
  kind          TEXT NOT NULL CHECK(kind IN ('report','er_diagram','sql_script','dataset','dashboard','other')),  -- 加 dashboard
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
  external      INTEGER NOT NULL DEFAULT 0 CHECK(external IN (0, 1))  -- 新加
);

-- 2. Backfill（external 默认 0，兼容历史所有行）
INSERT INTO file_artifact_new
  (id, scope, status, kind, session_id, connection_id, filename, physical_path,
   size_bytes, mime_type, title, summary, created_at, updated_at, archived_at,
   metadata_json, external)
SELECT
   id, scope, status, kind, session_id, connection_id, filename, physical_path,
   size_bytes, mime_type, title, summary, created_at, updated_at, archived_at,
   metadata_json, 0
FROM file_artifact;

-- 3. 替换
DROP TABLE file_artifact;
ALTER TABLE file_artifact_new RENAME TO file_artifact;

-- 4. 重建索引（保留原索引语义 + 新加 external 索引）
CREATE INDEX idx_file_artifact_session    ON file_artifact(session_id)    WHERE scope = 'session';
CREATE INDEX idx_file_artifact_connection ON file_artifact(connection_id) WHERE scope = 'workspace';
CREATE INDEX idx_file_artifact_status     ON file_artifact(status);
CREATE INDEX idx_file_artifact_external   ON file_artifact(external)      WHERE external = 1;
```

### 5.2 Domain 层

`com.datatalk.domain.fileartifact.FileArtifactKind` 增加枚举值：

```java
public enum FileArtifactKind {
    REPORT, ER_DIAGRAM, SQL_SCRIPT, DATASET, DASHBOARD, OTHER;  // 加 DASHBOARD

    @JsonValue
    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }
    // dbValue("dashboard") ↔ DASHBOARD
}
```

`FileArtifact` record 新加 `external` 字段（`boolean`，对应 DB 列）。

### 5.3 FileArtifactService 新 API（3 个）

```java
/**
 * 登记外部物理文件到 file_artifact 表。物理文件由 caller 自管（已写盘），本方法不做磁盘 IO 写入，
 * 仅 INSERT DB 行；INSERT 前通过 Files.size(absolutePath) 自读 size，避免 caller 传错。
 *
 * id: caller 生成（DashboardArtifactService 用 DashboardIds），方法不校验唯一性，由 PK 约束保证。
 *
 * @param absolutePath 必须是绝对路径，且在调用时刻可被 JVM 进程读取
 * @throws IOException 文件不存在或不可读
 * @throws ArtifactConflictException id 已存在（PK 冲突）
 */
FileArtifact registerExternal(
    String id,
    FileArtifactKind kind,
    FileArtifactScope scope,         // 当前仅支持 WORKSPACE，后续可扩
    String connectionId,             // 可空
    String sessionId,                // 可空（spec §10.1：dashboard sessionId=null）
    Path absolutePath,
    String title,
    String summary,
    Map<String, Object> metadata     // 可空
) throws IOException;

/**
 * 通用读字节。同时支持 managed 行（physical_path 是相对 workdir 的路径）和 external 行（绝对路径）。
 * managed 行：拼接 SessionWorkdirRoot.root() + physical_path 后读
 * external 行：直接按 physical_path 读
 *
 * @throws ArtifactNotFoundException artifact id 不存在；或物理文件不存在（与 managed 行
 *                                   "DB 有 × 盘没" 一致行为）。HTTP 映射 404。
 */
byte[] readBytes(String artifactId);

/**
 * 原子覆盖写。
 * managed 行：写 workspaces/<cid>/<filename> 路径（保持现行语义；本 spec 不修改 managed 行写盘）
 * external 行：
 *   1. ensureParentDir(absolutePath.parent)  // 若父目录不存在，Files.createDirectories
 *   2. atomic write: 写到 absolutePath + ".tmp" → fsync → ATOMIC_MOVE rename → fsync parent
 *   3. UPDATE file_artifact SET size_bytes = bytes.length, updated_at = now WHERE id = ?
 *
 * 不修改 status / connection_id / kind / external 等其它字段。
 *
 * @throws ArtifactNotFoundException artifact id 不存在
 * @throws IOException 写盘失败（temp 文件残留由调用方负责清理）
 */
FileArtifact replaceBytesAtomic(String artifactId, byte[] bytes) throws IOException;
```

> **API 命名说明**：原 P1 plan B4 假设的 `create` / `readBytes` / `replaceBytes` 含义与上述新 API 大体对应：`create` → `registerExternal`（更精确表达 "DB 登记，不写盘" 语义），`readBytes` 直接复用，`replaceBytes` → `replaceBytesAtomic`（强调原子性合约）。

### 5.4 FileArtifactReconciler 扩展

`runFullReconcile` 末尾新增一次 external dirs 扫描：

```java
public synchronized void runFullReconcile() {
    Set<String> knownSessionIds = knownSessionIds();
    reconcileSessionsTree(knownSessionIds);
    reconcileWorkspacesTree(knownSessionIds);
    reconcileExternalDirs(workdir.root().externalManagedRoots());  // 新增
}

/**
 * 列出每个外部目录下的常规文件，反查 file_artifact.physical_path（external=1 行）。
 * DB 无对应行 → 删盘文件（孤儿清理）。
 * 不做 adopt：dashboard 元数据由 promote 生成，不能从扫描推导。
 */
private void reconcileExternalDirs(List<Path> dirs);
```

`SessionWorkdirRoot` 新方法：

```java
/**
 * 返回 dashboards/ 物理目录的绝对路径：System.getProperty("user.home") + "/.data-talk/dashboards/"
 * （或测试环境下 datatalkRoot + "/dashboards/"）
 */
public Path dashboardsRoot();

/**
 * 所有"由 FileArtifactService 仅做 DB 登记、不主动写盘"的目录列表。
 * 当前仅 dashboardsRoot()。未来若有第二种 external kind，加入此列表。
 */
public List<Path> externalManagedRoots();
```

`reconcileWorkspacesTree` **保持现状不动**——它只对 `external=0` 行做 `Files.exists` 检查，对 external 行需要旁路：

```java
private void reconcileWorkspacesTree(Set<String> knownSessionIds) {
    for (FileArtifact row : repo.findAllWorkspaceScopedArchived()) {
        if (row.connectionId() == null) continue;  // Q2 现有决策
        if (row.external()) continue;              // 新增旁路：external 行物理位置由专属 reconciler 管
        if (Files.exists(Path.of(row.physicalPath()))) continue;
        // ...原有 deleteById + publishDiscarded
    }
}
```

> **决策延伸**：external 行的"DB 有 × 盘没"由谁清理？答：仍由 `reconcileWorkspacesTree` 处理——只是触发逻辑要重写为"对 external 行用绝对路径检查"。本 spec 简化策略：external 行 DB→盘 缺失校验合并到 `reconcileExternalDirs` 里——它扫盘列出 external dirs 实存文件后，**第二步**遍历 `repo.findExternalRowsByDir(dir)` 行做存在性反查；不存在则 `repo.deleteById` + `publishDiscarded`。这样 `reconcileExternalDirs` 同时覆盖正向（盘有 × DB 无 → 删盘）与反向（DB 有 × 盘没 → 删行）孤儿。

### 5.5 DashboardArtifactService（消费侧，本 spec 给出形态契约）

```java
@Service
public class DashboardArtifactService {

    static final long MAX_PAYLOAD_BYTES = 256L * 1024;

    public JsonNode promote(JsonNode incoming, String originSessionId) throws IOException {
        ObjectNode doc = (ObjectNode) incoming.deepCopy();
        String id = DashboardIds.newDashboardId();
        long now = System.currentTimeMillis();
        doc.put("id", id);
        doc.put("version", 1L);
        doc.put("createdAt", now);
        doc.put("updatedAt", now);

        validator.validate(doc).throwIfInvalid();

        byte[] bytes = mapper.writeValueAsBytes(doc);
        if (bytes.length > MAX_PAYLOAD_BYTES) throw new PayloadTooLargeException();

        Path target = workdir.root().dashboardsRoot().resolve(id + ".dashboard.json");
        atomicWriter.writeAtomically(target, bytes);  // temp + fsync + rename + fsync parent

        try {
            String connectionId = doc.path("defaultConnectionId").asText(null);
            fileArtifactService.registerExternal(
                id, FileArtifactKind.DASHBOARD, FileArtifactScope.WORKSPACE,
                connectionId, /* sessionId */ null, target,
                doc.path("title").asText(null), /* summary */ null,
                Map.of("originSessionId", originSessionId)
            );
        } catch (Exception e) {
            // 登记失败：清理已写盘文件，避免孤儿（reconciler 兜底但主动清更干净）
            Files.deleteIfExists(target);
            throw e;
        }

        return doc;
    }

    public JsonNode load(String dashboardId) throws IOException {
        byte[] bytes = fileArtifactService.readBytes(dashboardId);
        return mapper.readTree(bytes);
    }

    public JsonNode patch(String dashboardId, long baseVersion, List<Map<String, Object>> ops)
            throws IOException {
        JsonNode current = load(dashboardId);
        JsonNode patched = applier.apply(current, baseVersion, ops);  // baseVersion 校验 + 原子应用
        ((ObjectNode) patched).put("version", baseVersion + 1);
        ((ObjectNode) patched).put("updatedAt", System.currentTimeMillis());

        byte[] bytes = mapper.writeValueAsBytes(patched);
        if (bytes.length > MAX_PAYLOAD_BYTES) throw new PayloadTooLargeException();

        fileArtifactService.replaceBytesAtomic(dashboardId, bytes);
        return patched;
    }
}
```

### 5.6 ConnectionDeletionService（零代码改动）

事实验证：`JdbcFileArtifactRepository.detachArchivedFromConnection`（行 245-267）当前实现是：

```sql
SELECT id, metadata_json FROM file_artifact
WHERE connection_id = ? AND scope = 'workspace' AND status = 'archived';

-- 对每行：
UPDATE file_artifact SET connection_id = NULL, metadata_json = ? WHERE id = ?;
-- metadata_json 合并 orphanedFromConnection / orphanedFromConnectionId / orphanedAt 字段
```

**它不触碰任何物理文件**——所以 dashboard external 行（绝对路径）天然兼容，无任何 SQL 或 Java 改动。spec §10.5 的"connection 删除后 connection_id=NULL，物理文件保留"语义自动闭环。

### 5.7 Files Library（前端补 2 行 + 后端零改动）

后端 `FileArtifactController.listArchivedForConnection` 已正确按 `connection_id + scope='workspace' + status='archived'` 列出，dashboard 行天然出现，**后端零改动**。

前端必须补：

- `client/src/services/api/file-artifacts.ts`：`FileArtifactKind` 联合类型加 `'dashboard'`
- `client/src/features/files/connection-files.ts`（或 `EMPTY_KIND_GROUPS` 所在位置）：分组初始化加 `dashboard: []`
- 视情况补 `dashboard` kind 的图标 / i18n label / 双击行为（双击 → 打开 dashboard Stage Tab）

> **此前 §5 大纲曾误称"前端零改动"——已修正。**

### 5.8 ArtifactWatcherService（不动）

`ArtifactWatcherService` 仅监听 `sessions/` 目录（用于 detect → record + frontmatter 触发）；它本来就不监听 `workspaces/` 也不监听 `dashboards/`。dashboard 自管写盘不会被 watcher 误识别为外部 drop-in。**零改动**。

### 5.9 模块依赖关系

```
DashboardArtifactService
  ├─ FileArtifactService.registerExternal/readBytes/replaceBytesAtomic   (新)
  ├─ SessionWorkdirRoot.dashboardsRoot()                                  (新)
  ├─ AtomicFileWriter (private; temp + fsync + rename)                    (新, 不导出复用)
  ├─ DashboardSchemaValidator                                              (P1 task B2)
  └─ JsonPatchApplier                                                       (P1 task B3)

FileArtifactReconciler
  └─ reconcileExternalDirs(externalManagedRoots())                        (新分支)

ConnectionDeletionService
  └─ FileArtifactRepository.detachArchivedFromConnection                  (现状, 不改)
```

---

## 6. Status 语义重新校准（重要）

**校准前**（隐含）：`status = 'archived'` ⇒ 已固化到 workspace 命名空间 ⇒ frozen / read-only ⇒ 不再变。

**校准后**：`status = 'archived'` ⇒ 已固化到 workspace 命名空间。**内容是否仍可改写由 `kind` + 业务路径决定**：

| kind | 是否允许改写 archived 行内容 | 改写路径 |
|---|---|---|
| `report` / `sql_script` / `dataset` / `er_diagram` / `other` | 否（保留现有"frozen"语义） | — |
| `dashboard` | 是 | `replaceBytesAtomic` （由 ui_patch 触发） |

**强制传播范围**（plan A2 task 范围）：

- 审计所有 `status == ARCHIVED` 或 `findAllWorkspaceScopedArchived` 的现有调用点
- 凡假设"archived = 不变"的代码（例如缓存策略、ETag 计算、显示"只读"标记的 UI）必须显式排除 dashboard kind 或重新对待

**Spec / 文档同步**：

- 本 spec §6 是权威定义
- `2026-04-29-opencode-workdir-and-artifact-system-design.md` §6 file_artifact 总体描述需要在 plan 落地 PR 中追加一行注释指向本 spec
- `2026-05-08-report-dashboard-design.md` §10.1 已隐含此假设（"file_artifact.kind = 'dashboard'" + 多次 patch 改写），不需要改

---

## 7. Out of Scope

- **不引入 `path_owner` 三态枚举**（YAGNI；BOOLEAN external 已足够，未来再升级）
- **不引入 WAL / batched writes / write coalescing**（前端 debounce 已经覆盖；后端同步落盘是 baseVersion 校验的前提）
- **不扩展第 7 种 `FileArtifactKind`**（仅加 `DASHBOARD`，其它新 kind 走独立 spec）
- **不动 `ArtifactWatcherService`**（仍只监听 sessions/）
- **不重新设计 `FrontmatterParser`**（external 行不走 frontmatter，`registerExternal` 元数据由 caller 显式传入）
- **不引入 `AtomicFileWriter` 公共类**（dashboard 是当前唯一外部 atomic write 场景；将来真有第二类需求再抽，spec §3.5 论证）
- **不实现 dashboard 的 `discarded` lifecycle**（用户删除 dashboard 走 `archive → discard` 路径属于后续 plan 范围；本 spec 仅保证物理文件 / DB 行的进入路径）
- **不动现有 `archiveCandidate` / `archive` / `discard` 三套主流程**（managed 行行为完全保留）

---

## 8. Migration Strategy & Rollback

### 8.1 部署窗口

- V15 migration 在应用启动时由 Flyway 执行
- 整表 rebuild 期间需要短暂的写入暂停；预期 < 1s（开发机几 K 行 file_artifact 数据），生产首次启动可能略长
- Rebuild 流程不丢任何现有行（INSERT ... SELECT 全字段保留 + `external=0` 默认）

### 8.2 Rollback 策略

如果 V15 出问题需要回滚：

- **down migration**: V15 down 把表 rebuild 回 V14 schema（去 `dashboard` from kind CHECK + 删 `external` 列）
- **前提**: rollback 前必须**先**删除所有 `kind='dashboard' OR external=1` 的行（V14 schema 不容纳）；如果生产已有用户创建过 dashboard，rollback 必须人工导出 dashboard JSON 文件后再回滚 schema
- 实施 plan **必须**包含一个手工脚本 `migrate-rollback-helper.sql`：列出所有 dashboard 行 + 把对应 `~/.data-talk/dashboards/*.dashboard.json` 备份到 `~/.data-talk/_rollback_<timestamp>/`

### 8.3 与 V14 数据兼容

- V15 后所有 V14 创建的行天然 `external=0`（默认值），行为不变
- managed 行的 physical_path 仍然是相对路径（workspaces/<cid>/...），由 `readBytes` / `replaceBytesAtomic` 内部按 external 标志分支处理

---

## 9. Test Strategy 概要

### 9.1 后端单测（JUnit 5 + AssertJ）

- `FileArtifactServiceTest`:
  - `registerExternal` 成功路径：DB 行字段全部正确，size_bytes 由 Files.size 自读
  - `registerExternal` 失败：absolutePath 不存在 → IOException；id 已存在 → ArtifactConflictException
  - `readBytes` external 行：成功读出；物理文件被外删 → ArtifactNotFoundException
  - `readBytes` managed 行：保持现状行为（回归测试）
  - `replaceBytesAtomic` external 行：原子写、size_bytes 更新、parent dir 自动创建
  - `replaceBytesAtomic` managed 行：保持现状行为（回归测试）

- `FileArtifactReconcilerTest`:
  - `reconcileExternalDirs` 正向孤儿：盘上文件无对应 DB 行 → 删盘
  - `reconcileExternalDirs` 反向孤儿：external 行 DB 有 × 盘没 → 删行 + publishDiscarded
  - `reconcileExternalDirs` 不误删 managed 行物理文件
  - `reconcileWorkspacesTree` 对 external 行旁路：external 行不参与现有 archived 物理存在性校验

- `DashboardArtifactServiceTest`:
  - `promote` 流程完整：assign id/version → atomic write → registerExternal → 返回带 id 文档
  - `promote` 失败回滚：registerExternal 抛错时已写盘文件被清理
  - `load` 往返：promote → load 输出与 input 一致（id/version/timestamps 注入除外）
  - `patch` baseVersion 正确 → 应用 + version+1 + replaceBytesAtomic
  - `patch` baseVersion 错误 → VersionConflictException，文件不变

### 9.2 集成测试

- `ConnectionDeletionService` 端到端：创建 dashboard 绑定 connection → 删 connection → dashboard 行 connection_id=NULL，物理文件保留，可读
- `Files Library` controller：列出 connection 下 file_artifact 时含 dashboard 行
- Flyway V15 migration 测试：从 V14 schema 升级，已有行 external=0，加 dashboard 行后 V15 down rollback（含 helper 脚本）

### 9.3 前端单测（vitest）

- `FileArtifactKind` 类型与后端 dbValue 字符串一致性 diff（构建时校验）
- `EMPTY_KIND_GROUPS` 含 `dashboard` 条目
- Files Library 渲染含 dashboard 行不报错

### 9.4 端到端（Playwright，本 spec 不强制）

- promote → list in Files Library → 双击 → Stage Tab 打开 → patch → connection 删除 → Files Library 看见 dashboard 标"已脱离 connection" → 仍可双击打开（widget query 报错为预期）

---

## 10. 实施分解（写入下游 plan 用）

下游 plan（`2026-05-09-dashboard-file-artifact-integration-plan.md`）按以下 3 个 task 分解：

| Task | 责任 | 依赖 |
|---|---|---|
| **A1** | V15 migration + `FileArtifactKind.DASHBOARD` enum + `FileArtifact.external` 字段 + Repository 扩展（INSERT external 列、`findExternalRowsByDir`） | 无 |
| **A2** | `FileArtifactService.registerExternal` / `readBytes` / `replaceBytesAtomic` + `FileArtifactReconciler.reconcileExternalDirs` + `SessionWorkdirRoot.dashboardsRoot()` + status 语义校准代码审计 | A1 |
| **A3** | 前端 `FileArtifactKind` 联合类型补 `dashboard` + `EMPTY_KIND_GROUPS` 补条目 + Files Library 双击行为占位（Stage Tab 打开走 P1 既有路径） | A1（后端字段已存在） |

A2 完成后即解除 P1 plan B4 的 BLOCKED 状态。

---

## 11. Open Questions

无（5 个关键决策点已对齐）。后续若 implementation 中发现新问题，作为 plan 内 risk 处理而非 spec 修订。
