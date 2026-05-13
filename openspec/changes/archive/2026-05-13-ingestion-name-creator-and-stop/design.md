## Context

数据采集（ingestion）链路当前 100% 由 AI 经 `datatalk_http_request` MCP 工具触发。`ingestion_job` 表只存 `sourceUrl` 与系统生成的 `ing_<random16>` ID，库列表 (`ingestion-library-tab.tsx`) 与 Tab 标题（`Job <id前8位>`）都无语义标识。

`/api/ingestion/jobs/{id}/cancel`（`IngestionController.cancel`）只对 DB 做 `updateStatus('cancelled')`，而 `IngestionExecutor.executeBatchInsert` 在 HTTP/MCP 线程同步执行，循环里既不读 cancel-flag 也不响应 `Thread.interrupt`，写入会盖回 `completed`。`writing` 阶段前端只有 Mapping 页有 Cancel 按钮，库列表与 writing 阶段无任何停止入口。进程崩溃后状态永远停留在 `fetching` / `writing`，与现实不一致。

**当前 V20 migration** 的 status CHECK 已包含 `cancelled`，但目标表清理无任何路径——已 commit 的批次留在用户业务库，errorMessage 无指引。

## Goals / Non-Goals

**Goals:**

- 库列表与 Tab 标题用人类可读的 `name` 识别任务，AI 必传
- 区分 AI / user 创建路径，可点击跳回原会话；会话被删后展示文字不漂移
- Stop API 真正中断运行中的 fetch / write 线程；运行期主动 stop 时清空目标表（DROP），用户业务库零残留
- 状态最终一致：启动 sweeper + 心跳 sweeper 两层保险
- UI Stop 入口覆盖所有非终态阶段，且对僵尸任务有 force-stop 升级路径

**Non-Goals:**

- **不**做分布式部署下的 stop 协调（`IngestionRunRegistry` 单进程内存）；多节点场景留扩展点，本次不实现
- **不**做"追加到已存在表"（append mode）—— 当前 `IngestionExecutor.createTable` 总是新建表，DROP 策略只对这个假设成立
- **不**自动 DROP 崩溃 / 心跳超时分支留下的残留表——保留 partial 行 + errorMessage 指引用户手动清理
- **不**在 `created_by_label` 上做反向同步（会话改名后不更新历史 label）
- **不**修改现有 cancel 端点的行为（保留 backward compat，但 UI 与 AGENTS 全部切换到 stop）

## Decisions

### D1. `name` 必传 vs 可选 + 派生 fallback

**选**：AI 必传（`required: ["url", "payloadFormat", "name"]`），后端 `name TEXT NOT NULL`。

**理由**：用户明确要求"具备含义的任务名称"。若可选 + 派生，AI 会偷懒不传，长尾全是机械名，又回到现状。MCP schema 校验失败会让 AI 自动重试时带上 name，强制约束自洽。

**替代**：A1 后端派生 / A3 可选 + fallback。已在 explore 中评估，弃用。

### D2. Creator 三列设计（kind + sessionId + label）

**选**：`created_by_kind TEXT NOT NULL`、`created_by_session_id TEXT`、`created_by_label TEXT`。

**理由**：
- `kind` 渲染图标 + 未来扩展过滤
- `session_id` 点击跳回会话；会话被删时 nullable 不 fk 约束（避免历史 ingestion 被级联清掉）
- `label` 写入时一次性物化（fetcher 通过 `SessionLookup` 查 session.title），与 session 解耦——会话被删/改名后 ingestion 列表显示不漂

**替代**：
- *单 kind 列 + 渲染时 join session*：删 session 后展示空白；放弃
- *kind + sessionId 两列、渲染时 lookup*：同上漂移问题
- *kind + label 两列、丢 sessionId*：失去"跳回会话"能力

### D3. AI 不自报 creator（后端自动判定）

**选**：`HttpRequestActionHandler` 在调用 fetcher 时显式传 `CreatorKind.AI`，不让 AI 在 inputSchema 里声明。

**理由**：creator 是身份事实，由调用方自动识别比让 AI 自报可信度更高。AGENTS.md 也无需为此加约束，节省 prompt token。

### D4. Stop 实现路径：进程内 RunRegistry + cancel-flag + interrupt

**选**：

```java
class IngestionRunRegistry {
  private final ConcurrentHashMap<String, RunHandle> active = new ConcurrentHashMap<>();
  
  record RunHandle(Thread worker, AtomicBoolean cancelled, String stage) {}
  
  RunHandle register(String jobId, Thread worker);
  void requestStop(String jobId);  // cancelled.set(true) + worker.interrupt()
  void unregister(String jobId);
  boolean isActive(String jobId);
}
```

`IngestionExecutor.executeBatchInsert` 修改：

```java
while (rs.hasNext()) {
  if (handle.cancelled().get() || Thread.interrupted()) {
    conn.rollback();
    throw new IngestionCancelledException(rowsCommitted);
  }
  // ... existing per-row code
  if (count % batchSize == 0) {
    ps.executeBatch(); conn.commit(); rowsCommitted += batch;
    jobRepo.updateHeartbeat(jobId, now());  // tick heartbeat
    eventPublisher.publish(...)
  }
}
```

**理由**：每批边界检查 cancel-flag，cancel 延迟 ≤ batchSize（默认 1000 行，通常 < 1s）。`Thread.interrupt()` 兜底打断 socket I/O（fetch 阶段的 HTTP 拉取）。`IngestionCancelledException` 不走 fail 分支，走专门的 cleanup。

**替代**：
- *单一大事务，cancel 走 rollback*：batchSize=1000 改成 transaction-per-job → 巨大事务 → 用户业务库压力 → 弃用
- *DB 轮询 cancel-flag*：每 N 行查一次 SQLite 状态，延迟 + IO 开销 → 弃用
- *Thread.stop()*：deprecated 不安全

### D5. Cancel 时目标表清理：DROP（运行期主动 stop）vs 保留（崩溃恢复）

**选**：

| Cancel 触发源 | 处理 |
|---|---|
| 运行期主动 stop（API / UI 点击）| 中断线程 → rollback 当前 batch → **DROP TABLE** target_schema.target_table → 删 payload artifact → `cancelled` |
| 启动 sweeper（进程崩溃恢复）| **不 DROP**，状态翻 `failed`，errorMessage = "server restarted while running — manually drop target table if needed" |
| 心跳 sweeper（5min 无 heartbeat）| **不 DROP**，状态翻 `failed`，errorMessage = "task heartbeat lost — manually drop target table if needed" |

**理由**：用户明确"历史的直接处理掉"是针对主动 stop 场景。崩溃 / 超时分支无法确认 DROP 是否本意（可能是网络抖动短暂卡顿，进程其实正常），按"安全保守 + 提示用户"原则不自动 DROP。文字提示在 UI 的 FailedPhase 渲染。

`IngestionDdlAdapter` 加方法：

```java
String generateDropTable(String schema, String table);  // 4 个方言: DROP TABLE IF EXISTS [schema.]table
```

### D6. 状态最终一致：启动 sweeper + 心跳 sweeper 两层保险

**选**：

```java
@Service
class IngestionStartupSweeper {
  @PostConstruct  // Spring 启动后一次性执行
  void sweepStaleJobs() {
    int n = jobRepo.batchFailByStatus(
      List.of("pending","fetching","mapping","confirmed","writing"),
      "server restarted while running");
    log.info("Marked {} stale ingestion jobs as failed", n);
  }
}

@Service
class IngestionHeartbeatSweeper {
  @Scheduled(fixedDelay = 60_000)
  void sweepDeadHeartbeats() {
    long deadline = now() - 5 * 60_000;
    jobRepo.batchFailIfHeartbeatBefore(
      List.of("fetching","writing"), deadline,
      "task heartbeat lost");
  }
}
```

`executeBatchInsert` 与 `IngestionPayloadFetcher.fetchPage` 每次循环都 tick `heartbeat_at = now()`。

**理由**：进程内 RunRegistry 是单进程的，重启就丢；启动 sweep 解决"重启后僵尸"，心跳 sweep 解决"线程死了但进程没死"（OOM 后线程被 GC 但 Spring 仍在跑）。两者覆盖不同失败模式。

### D7. `POST /jobs/{id}/stop` 替代旧 `/cancel`

**选**：新增 `POST /jobs/{id}/stop?force=false`，旧 `/cancel` 端点保留但内部 forward 到 stop（标 `@Deprecated`），下一个 change 移除。

返回语义：

| 情况 | HTTP |
|---|---|
| 活任务，已发取消信号 | `202 Accepted`（异步完成） |
| 非运行阶段，立即翻 cancelled + 清理 | `204 No Content` |
| 已是终态 | `409 Conflict` |
| 不存在 | `404 Not Found` |
| `force=true` 即使 RunRegistry 没有也直接翻 cancelled | `204 No Content`（幂等） |

**理由**：`stop` 命名比 `cancel` 准确——后者在 HTTP 语境里常被理解为客户端取消请求；这里是服务端中断长任务。force 用于跨进程僵尸（RunRegistry 不知道但 DB 还在 writing）。

### D8. `IngestionRunHandle` 写入路径与 fetcher / executor 解耦

**选**：让 `HttpRequestActionHandler` 与 `IngestionController.confirm`（同步触发 ingestPayload）在调用前注册 handle、调用后 unregister；fetcher / executor 通过构造器注入 `IngestionRunRegistry`，每批边界轮询自己的 handle。

**理由**：avoid `ThreadLocal`，保持纯函数式调用栈；register/unregister 用 try-with-resources 风格（`AutoCloseable`）确保异常路径也能清理：

```java
try (RegistryEntry __ = registry.register(jobId)) {
  executor.ingestPayload(jobId, batchSize, sessionId);
}
```

### D9. Frontend Stop UX：常驻按钮 + force-stop 升级

**选**：

- 库列表行 hover 出现 ⏹ 按钮，点击 → confirm dialog → `POST /stop`
- Tab header 加常驻 Stop 按钮（非终态显示），与 Delete 按钮并排
- 调用后若 30s 内 status 未变（轮询通过 `useIngestionJobQuery`），按钮变为 "Force stop" 二次按钮，点击 → `POST /stop?force=true`
- Force-stop 二次按钮带 `bg-destructive` 警示色

**Design tokens**（已与 `client/DESIGN.md` 对齐）：
- 普通 Stop：`hover:bg-destructive/10 hover:text-destructive` —— 与 Delete 同源
- Force Stop：`bg-destructive/15 text-destructive border-destructive/30` —— 与 status='cancelled' badge 同色

### D10. AGENTS.md 提示词调整范围

**选**：仅在 `## Data Ingestion` 段顶部加 1 句强约束 + tool 列表里的 `datatalk_http_request` 描述补 1 个 input。

**理由**：错误码表 / 工具链顺序 / 凭据管理 / format 支持等其它部分均无影响。改动最小化降低 prompt 风险（BUG-0040 教训：AGENTS.md 字面变更可能触发 LLM 幻觉别的）。

## Risks / Trade-offs

- **[残留表]** 崩溃恢复不 DROP 目标表 → 用户业务库可能积累半成品表 / 行
  - **Mitigation**: errorMessage 给明确文案 "manually drop target table"；FailedPhase UI 渲染 Copy-able SQL `DROP TABLE IF EXISTS schema.table;`
- **[label 漂移]** 会话改名后 ingestion 列表的 creator label 不更新
  - **Mitigation**: 预期行为，已在 proposal 标注；删除会话后才能感觉到差异，正面收益（不空白）大于负面
- **[单进程 RunRegistry]** 多节点部署 stop 无法跨节点
  - **Mitigation**: 当前 DataTalk 是单节点桌面 / 自托管；未来分布式时改 DB row-level lock + owner_instance_id（design 留扩展点，本次不做）
- **[V23 migration 改 status]** 现有 status CHECK 是否需要 rebuild？
  - **Mitigation**: 不需要——枚举集合不变，只是新加列。SQLite ADD COLUMN 不影响 CHECK
- **[Stop 后 DROP 失败]** DROP TABLE 网络/权限失败
  - **Mitigation**: 状态仍翻 cancelled，errorMessage 记录 `"stopped but table cleanup failed: <reason>"`；UI 显示原文案 + Copy-able DROP SQL
- **[已 commit batches]** 即使主动 stop，rollback 只能回滚最后一个未 commit batch，前面 batches 已 commit → 必须靠 DROP 才能清干净
  - **Mitigation**: DROP TABLE 整张表直接消失，前面 commit 也无意义；这正是选 DROP 而非 DELETE 的根本原因
- **[heartbeat 假阳性]** 短暂网络阻塞 5min 可能被误判
  - **Mitigation**: 5min 已是相对宽容窗口；用户可在 Settings 里调（本次不暴露，硬编码）；HTTP fetch 阶段每页都 tick，writing 阶段每批 tick，正常情况下 tick 间隔远 < 5min

## Migration Plan

1. **V23 migration**（SQLite，纯 ADD COLUMN，无回填风险）：
   ```sql
   ALTER TABLE ingestion_job ADD COLUMN name TEXT;  -- 历史行 NULL，向后兼容
   ALTER TABLE ingestion_job ADD COLUMN created_by_kind TEXT DEFAULT 'ai';
   ALTER TABLE ingestion_job ADD COLUMN created_by_session_id TEXT;
   ALTER TABLE ingestion_job ADD COLUMN created_by_label TEXT;
   ALTER TABLE ingestion_job ADD COLUMN heartbeat_at INTEGER;
   ```
   注意：`name` 用 nullable 兼容历史行，但 AI 必传由 MCP schema 在写入层强制；旧行渲染时 fallback 到 `Job <id前8位>`
2. **后端**：先 build domain（IngestionJob 加字段）→ application（RunRegistry / Sweeper / DROP）→ adapter（Stop API / AGENTS.md / schemas）。每步 `mvn install -pl <module> -am -DskipTests` 同步到 `~/.m2`，最后 `mvn -pl data-talk-adapter spring-boot:run` 验证
3. **前端**：API client → tab-type-registry title resolver → library/job tabs UI → i18n keys。每步 `npx tsc --noEmit`
4. **AGENTS.md** 改动放在最后一步：先验证后端 schema 拒 missing name，再加 prompt 强约束（避免 prompt 与 schema 不同步导致 AI 错误率峰值）
5. **Rollback**：若 stop 真中断有问题，先把 `IngestionExecutor` 的 cancel-flag 检查删掉（退化为旧的"翻 status 但线程跑完"行为）；DB schema 不回滚（ADD COLUMN 不影响旧代码）

## Open Questions

无开放项。所有决策已在 explore 阶段对齐：name 必传、creator 三列闭环、启动 sweeper + 心跳双保险、运行期 stop 才 DROP、合成一个 change。
