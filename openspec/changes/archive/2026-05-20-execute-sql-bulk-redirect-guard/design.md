## Context

`datatalk_execute_sql` 是 OpenCode 暴露给 LLM 的核心 MCP 工具之一，由 `ExecuteSqlAction` 实现，接受任意 `sql` 字符串并落 artifact。当前实现存在三条互不依赖的弱点：

1. **token 经济性失控**：AI 把整个 SQL 文件内容 inline 进 tool_call.input，单次 20KB 文件 ≈ 5,000 输出 token，失败重试 + tool_history 复读把单次操作放大到 20,000+ token（BUG-0069 实测）
2. **来源不可信**：`input.source: "ai" | "user"` 字段由调用方填写，AI 可自由伪造 `source=user`
3. **专用工具被绕**：`datatalk_import_data` 的批处理、流式写、错误分段回滚、dialect-aware 引用全部被"AI 自己拼 SQL 走 execute_sql"路径短路

BUG-0065 / BUG-0067 / BUG-0069 三起 BUG 已经证明：**纯 SKILL.md 文本约束（软提示）会被 LLM 系统性绕过**。修法必须升级为"入口路径强制可信信号 + 后端硬拒绝 + 自愈 nextAction"三层组合。

同时必须保护合法路径：**用户在 query_editor 主动点"运行"** 触发的 SQL 哪怕上 MB 也不应被拦截 —— 它根本不进 AI token 上下文，是用户的合法操作。

## Goals / Non-Goals

**Goals:**
- AI 触发的批量 SQL（>4KB / >20 个 INSERT / 源自文件）100% 被拒绝并返回 `nextAction` 指向 `datatalk_import_data`
- 用户在 query_editor 触发的任意大小 SQL 100% 放行
- AI 无法通过伪造 `input.source=user` 绕过 guard
- 拒绝响应携带足够元数据让 AI 0 推理直达正确路径（不陷入"再试一次"循环）
- 防漂移：合同测试断言 SKILL.md / messages.properties 关键短语存在

**Non-Goals:**
- 不动 `datatalk_import_data` 工具本身（已在 dialect-aware change 修过）
- 不引入 LLM-side 字符计数（这是后端硬契约，不靠 AI 自律）
- 不动前端 query_editor 执行路径（仅后端 controller 单边强制 USER 注入）
- 不解决 BUG-0065 D 段的 `extraJdbcParams` 透传（继续 DEFERRED）
- 不引入新方言或修改现有方言行为

## Decisions

### D1 — CallerKind 注入位置：ActionContext.metadata 而非 input 字段

**决策**：扩展 `ActionExecutionMetadata` 添加 `CallerKind callerKind` 字段，由后端入口路径强制注入；`ExecuteSqlAction` 通过 `ctx.metadata().callerKind()` 取值，**不再读 `input.get("source")`**。

**为什么不用 input.source**：
- input 由 AI 在 MCP tool_call 中自由填写，无法防伪造
- 历史 input.source 字段保留兼容（日志、统计仍用），但不再用于安全判断
- ActionContext 由后端构造，AI 不可触达

**为什么不用 ThreadLocal / SecurityContext**：
- ActionContext 已经是动作执行的契约载体，扩展自然
- ThreadLocal 在虚拟线程下行为不可预测
- 无需引入 Spring Security 仅为一个字段

**入口路径表**：
| 入口 | 注入值 | 注入点 |
|---|---|---|
| `SqlExecuteController.POST /sql/execute` | `USER` | controller 构造 ActionContext 时硬编码 |
| `McpActionBridge` (MCP 工具调用) | `AI` | bridge 构造 ActionContext 时硬编码 |
| `ActionDispatcher` (内部 dispatch) | `AI` | dispatcher 兜底 AI（保守默认） |

### D2 — 阈值：字节 4096 + INSERT 计数 20 + file-origin（任一触发即拒，但只针对含写入语句的 SQL）

**决策**：三个 OR 条件中**任一**满足即拒绝，但**所有闸均隐含前提**：SQL 包含 INSERT/UPDATE/DELETE/DDL 至少一条（即非纯 SELECT/EXPLAIN/SHOW/DESCRIBE）。纯只读 SQL 不论多大一律放行。

**为什么只读 SQL 必须放行**：
- 纯 SELECT（哪怕 10KB 多 CTE / 多 JOIN）**没有 import_data 替代方案** —— import_data 是写入工具
- 拒绝纯 SELECT 就是给 AI 制造死路：它只能优化 query 或放弃，但优化责任在 AI，不该用 guard 强制
- token 浪费虽然存在，但属于 prompt 工程问题，不是合同违规

**字节 4096**：经 token 经济性反推：
- 1 token ≈ 3-4 字符（英文 SQL）→ 4096 字节 ≈ 1,200 token
- < 1,200 token 视为"可接受的 ad-hoc 成本"
- > 1,200 token 视为"应该走 import_data 的批量"
- 前提：SQL 含写入语句（否则放行）

**INSERT 计数 20**：按"单条 INSERT 平均 200 字节"推算，20 条 ≈ 4KB，与字节闸自然对齐；INSERT 计数闸防"单行多 VALUES 压缩"绕过字节闸的情况。

**file-origin**：最本质的语义闸 —— 一旦 SQL 源自文件读取（input metadata 含 sourceFileId），无论体量都该走 import_data。这是 BUG-0069 的直接病根。前提同样：SQL 含写入语句（若用户上传纯 SELECT 文件由 AI 读后执行，那是合法 ad-hoc 分析场景）。

**判定顺序**：
1. SqlRiskAnalyzer 解析 statements
2. 若所有 statements 均为只读类（SELECT/EXPLAIN/SHOW/DESCRIBE/WITH-only-SELECT）→ 放行
3. 否则按 size / INSERT count / origin 三闸任一触发即 reject

**替代方案考虑**：
- 按"语句数总和" → 误伤多 SELECT JOIN（无意义）
- 按 token 估算 → 不可计算，且依赖 tokenizer 实现
- 按行数 → 易被"单行 1000 VALUES"绕过
- 拒绝纯 SELECT 大 SQL → 给 AI 制造死路（无替代工具）

### D3 — 拒绝响应结构：复用 `nextAction` 自愈契约

**决策**：返回 `{status:"rejected", error:{code,message,reason}, nextAction:{action,params}}`。

```json
{
  "status": "rejected",
  "error": {
    "code": "use_import_data",
    "message": "SQL contains 100 INSERT statements (limit 20). Use datatalk_import_data instead.",
    "reason": "insert_count_threshold"
  },
  "nextAction": {
    "action": "datatalk_import_data",
    "params": {
      "source": {"type": "file", "fileId": "<回填若有>"},
      "target": {"connectionId": "<from ctx>", "tableName": "<从 INSERT INTO X 解析>"}
    }
  }
}
```

**为什么 reason 必须是机器可读 enum**：
- `"size_threshold"` / `"insert_count_threshold"` / `"originated_from_file"` 让 AI 能区分调整策略
- AI 可以根据 reason 选择性提供 fileId 或自己 split 任务
- 合同测试可基于 reason 枚举做完整性断言

**为什么 nextAction 必须给 params**：
- AI 0 推理直达 import_data 调用，不需要"我得想想怎么构造 source.fileId"
- 服务端能解析 INSERT INTO X 得到 tableName 时直接填，AI 无需 SQL 解析
- connectionId 从 ctx 取得，AI 不需要重新协商

### D4 — SqlRiskAnalyzer 复用 vs 新增 statement counter

**决策**：复用 `SqlRiskAnalyzer`（dialect-aware change 已增强其方言敏感的 statement split 能力）。

**INSERT 计数定义**：`statements.stream().filter(s -> s.kind() == StatementKind.INSERT).count()`。
- `INSERT ... SELECT` 计 1（与单行 INSERT 等价）
- `INSERT ... ON DUPLICATE KEY UPDATE` 计 1（MySQL 单语句）
- `INSERT ALL` (Oracle) 计 1（Oracle 单语句，多 INTO 仍是 1 个 INSERT）
- 多个独立 `INSERT INTO ...; INSERT INTO ...;` 计 N

**为什么不新建 counter**：
- 现有 SqlRiskAnalyzer 已经按方言 split，结果与执行路径一致
- 新建独立 counter 可能与执行路径分歧，引入新 bug

### D5 — sourceFileId 元数据传递

**决策**：在 `ExecuteSqlAction.input` 中接受可选 `sourceFileId` 字段（描述清楚是 AI 应该填的）；如果 AI 调用 `datatalk_execute_sql` 时 `sql` 内容由 `datatalk_file_read` 拼接来，AI **必须**填写 `sourceFileId`。

**问题**：AI 可能不填 sourceFileId 绕过 origin 闸。

**缓解**：
- 字节 / count 两道闸兜底 —— 文件拼接的 SQL 几乎必然超 4KB 或超 20 INSERT
- 描述明示"若不填且后续发现源自文件，视为合同违规"
- 该字段长期可演化为后端通过 file_read 调用历史关联（暂不实现，design 留开）

**为什么不强行追踪 file_read → execute_sql 因果链**：
- 实现复杂（要跨 session 跟踪工具调用拓扑）
- 字节+count 闸已能覆盖 99% 实际场景
- 留作 follow-up 改动空间

### D6 — Description i18n 双语对齐

**决策**：`messages.properties`（英文）与 `messages_zh_CN.properties`（中文）的 `action.execute_sql.description` 同步更新，**长度均控制在 600 字符以内**（避免触发 SkillRoutingContractTest 的描述长度上限）。

**为什么必须双语**：AI 选择的 prompt locale 取决于 session 设置，单边修改会有歧义死角。

**为什么 600 字符**：与现有 sql-execution skill description 上限一致，dialect-aware change 已建立此契约。

### D7 — query_editor 路径的明示

**决策**：description 末段必须包含**正面陈述**："When the user runs SQL from their query editor UI, this guard does not apply. You (the AI) cannot trigger that path — only the user can."

**为什么**：
- 防 AI 矫枉过正（看见 reject 后连合法的 ad-hoc query 也不敢用）
- 防 AI 学到错误规避（试图把所有 SQL 都"包装成用户行为"绕 guard）
- 让 AI 理解 user/ai 二分的语义边界，不再尝试 source=user 伪造

### D8 — 合同测试：description 关键短语断言

**决策**：新增 `ExecuteSqlDescriptionContractTest` 单独断言 description 文本契约：
- 含 "MUST USE datatalk_import_data" / "use_import_data" / "4096" / "20 INSERT"
- 含 "query editor UI" / "you (the AI) cannot trigger" 这种用户路径明示

**为什么独立测试**：与 SkillRoutingContractTest 解耦 —— 后者关注 SKILL.md 文件，新测试关注 i18n properties。两者职责清晰、维护边界不重叠。

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| `INSERT ... SELECT` 大查询（单语句但拷贝 1M 行）被错误放行 | 接受。该场景本质是 server-side 操作，不进 AI token；如未来需要也可加入"SELECT 子句涉及表 ≠ INSERT 目标表 且预估行数 > N"闸，但不在本 change 范围 |
| AI 通过 `datatalk_export_data` + `datatalk_import_data` 链做大型迁移时被误拦 | 不会。两条路都是专用工具，不经过 execute_sql guard |
| 历史 client 仍在 input 里发 `source: "user"` | 兼容：input.source 字段保留但不用于 guard 判断，行为变更对客户端无感（行为差异只发生在 AI 路径） |
| 测试环境/Spring 上下文里手动构造 ActionContext 时忘记设 callerKind | `ActionExecutionMetadata.empty()` 兜底为 `AI`（保守默认）—— 单元测试若需放行需显式构造 `USER` |
| `BulkSqlGuard` 在 confirmation 流程中重复触发 | guard 仅在首次执行（无 confirmationId）时生效；持有 confirmationId 的二次调用走 `executeConfirmation` 分支，不再过 guard（已经过批准的 SQL 不应被拒） |
| MySQL `INSERT INTO ... VALUES (...),(...),(...)` 单语句多元组绕过 INSERT count 闸 | 字节闸兜底：100 元组的单语句 INSERT 必超 4KB |

## Migration Plan

**部署顺序**（无破坏性变更，可热部署）：
1. domain 层：发布 `CallerKind` enum + `ActionExecutionMetadata` 新字段（向下兼容，旧调用走默认值 AI）
2. application 层：发布 `BulkSqlGuard`（独立 bean，未被 wire 不生效）
3. adapter 层：`ExecuteSqlAction` wire BulkSqlGuard + 三个入口注入 CallerKind + description 更新

**回滚策略**：
- 单一改动可独立回滚（domain/application/adapter）
- 如生产发现误拦，关闭 BulkSqlGuard wire（在 ExecuteSqlAction 构造器注入处替换为 no-op 实现）即可降级
- 不涉及数据库 schema 变更，无 Flyway migration

**灰度策略**（可选）：
- 引入 `datatalk.bulk-sql-guard.enabled: true` 配置项，默认开启
- 生产环境如需观察一段时间可设 false 暂时降级（但本 change 默认不引入此 flag，避免又出现 BUG-0067 类"flag 一开就忘"问题）

## Open Questions

无。所有阈值、来源识别、响应结构在 D1–D8 已确定。

如未来出现 false-positive，可在 follow-up change 中调整：
- 阈值上调（4096 → 8192）
- 加入按用户角色 / 数据源 size 的动态阈值
- 引入 file_read → execute_sql 因果链追踪（D5 缓解措施的硬化版）
