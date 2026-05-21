# SQL Risk Classification & IT CI Gate Design

**日期**: 2026-04-20
**状态**: Draft approved for planning
**关联技术债**: `TD-020`, `TD-021`
**关联文档**:
- [产品规格总览](./index.md)
- [技术债跟踪器](../exec-plans/tech-debt-tracker.md)
- [后端开发指南](../BACKEND.md)
- [质量标准](../QUALITY.md)
- [安全指南](../SECURITY.md)

## 1. 背景与问题

当前仓库在两处和既有规格不一致：

1. `TD-020` 仅完成了前端 `resolveRisk` 优先级链和 `part.state.metadata.riskLevel` 的透传消费，但**没有**完成“后端基于 SQL AST 强制判级”的规格闭环。现状仍然允许前端用正则兜底，这与产品规格 §2 “后端强制判级，不信任 AI 自报”不一致。
2. `TD-021` 在文档中标记为 `*IT.java` 未纳入 CI。代码核对后，`server/data-talk-adapter/pom.xml` 里确实没有 `maven-failsafe-plugin` 或显式 `surefire includes`，因此 `ChannelControllerIT`、`TypicalQueryE2EIT` 等集成测试不会被 `mvn clean verify` 自动执行。

这两项债务都已经影响项目的“规格可信度”：

- `TD-020` 影响安全和产品承诺
- `TD-021` 影响测试门禁的真实性

## 2. 目标

本设计一次性完成两个闭环：

### 2.1 `TD-020`

建立**后端通用 SQL AST 风险判级能力**，要求：

- 使用 Apache Calcite 解析 SQL
- 在 action 执行前的统一预处理层完成判级
- 对所有“携带 SQL 输入”的 action 适用，而不是只补某个 renderer
- 将判级结果标准化回写到 tool part / action 输出可消费的 metadata
- 前端保留现有优先级链，但把正则降级为“兼容旧数据 fallback”

### 2.2 `TD-021`

建立**后端集成测试真实门禁**，要求：

- `mvn clean verify` 自动执行 `*IT.java`
- 文档明确 `verify` 是后端完整回归入口
- 技术债文档同步更新，避免后续重复误判

## 3. 非目标

- 本次不实现三期“操作审计日志”
- 本次不实现所有 SQL 语义分析，只聚焦风险分级所需的 AST 识别
- 本次不引入跨数据库方言完整兼容层；Calcite 无法解析的 SQL 采用保守降级策略
- 本次不重构所有现有 action 输入结构，只为 SQL-bearing action 增加最小必要约定
- 本次不新增 GitHub Actions 或外部 CI 平台配置；先保证 Maven `verify` 语义正确

## 4. 设计决策

### 4.1 采用 Apache Calcite，而不是 JSqlParser

选择 Calcite 的原因：

- 它更接近“通用 SQL 分析基础设施”，后续可扩展到影响行数预估、语句规范化、更多语义检查
- 这次需求已经明确选择“规格闭环”，不是最小修补
- DataTalk 的 SQL 风险控制最终会从简单分级演进到更深的结构分析，Calcite 的长期价值更高

代价：

- 接入更重
- 方言兼容和错误处理需要自己收口

因此本次设计会把 Calcite 包装在 application 层自己的接口后面，避免未来换实现时波及调用方。

### 4.2 风险判级挂在统一 action 预处理层，而不是各自 handler 内

统一预处理层的挂载点是 `ActionDispatcher.dispatch(...)`。

理由：

- 这是当前 server / opencode / client action 的统一执行入口
- 可以在 schema 校验后、handler 真正执行前完成 SQL 分析
- 能让“SQL-bearing action”共享一套策略、错误模型、降级逻辑和测试基座
- 避免在 `ExecuteSqlAction`、未来 `preview_sql`、导入 SQL 等 handler 里复制逻辑

### 4.3 风险来源优先级

风险来源调整为：

1. **后端动态 AST 判级结果**
2. 后端静态 `ActionDescriptor.riskLevel`
3. 前端旧正则 fallback

这意味着：

- 静态 `riskLevel` 继续存在，用于非 SQL action 或后端未识别 SQL 的保底
- SQL-bearing action 一旦被统一预处理层识别，动态结果优先于注解值

## 5. 架构设计

### 5.1 新增组件

#### `SqlRiskAnalyzer`

application 层接口，负责将原始 SQL 转为标准风险分析结果。

职责：

- 调用 Calcite parser
- 识别语句主类型和危险模式
- 返回统一结果对象，而不是直接返回 `RiskLevel`

建议结果结构：

- `riskLevel: L1 | L2 | L3 | null`
- `reason: String`
- `requiresStrongConfirmation: boolean`
- `astParsed: boolean`
- `fallbackUsed: boolean`

#### `CalciteSqlRiskAnalyzer`

`SqlRiskAnalyzer` 的默认实现。

职责：

- 解析单条或多条 SQL
- 对 `SELECT / EXPLAIN / SHOW / DESCRIBE` 判为 `L1`
- 对 `INSERT / CREATE INDEX / CREATE VIEW / 单条低风险 UPDATE` 判为 `L2`
- 对 `DELETE / DROP / ALTER / TRUNCATE / GRANT / REVOKE / 批量 UPDATE / 批量 DELETE / 含 DML 的复杂语句` 判为 `L3`
- 对解析失败或无法安全判定的语句采取保守策略

#### `SqlBearingActionInspector`

统一预处理层的辅助组件。

职责：

- 根据 action id 和 input shape 提取 SQL 字段
- 判断某个 action 是否属于 SQL-bearing action
- 把分析结果写入标准 metadata

### 5.2 `ActionDispatcher` 预处理扩展

`ActionDispatcher.dispatch(...)` 新增“schema 校验之后、handler 执行之前”的预处理步骤：

1. 校验 input schema
2. 若 action 为 SQL-bearing：
   - 提取 SQL
   - 调用 `SqlRiskAnalyzer`
   - 生成 `ActionExecutionMetadata`
3. 将 metadata 注入后续执行上下文
4. handler 正常执行
5. 对需要向前端暴露的 tool part / output，携带 `metadata.riskLevel`

这里需要一个新的上下文扩展机制。当前 `ActionContext` 只有 session / call / connection / openCodeSessionId，不足以承载预处理元数据。设计上建议：

- 扩展 `ActionContext`
  或
- 新增 `ActionExecutionContext`，把原 `ActionContext` 包进去

推荐后者，避免污染 domain record 的简单语义。

### 5.3 风险判级规则

本次采用“结构优先、保守兜底”的规则。

#### `L1`

- `SELECT`
- `EXPLAIN`
- `SHOW`
- `DESCRIBE`
- 纯元数据读取

#### `L2`

- `INSERT`
- `CREATE INDEX`
- `CREATE VIEW`
- `UPDATE` 且满足“可识别为受限更新”的最小条件

#### `L3`

- `DELETE`
- `DROP`
- `ALTER`
- `TRUNCATE`
- `GRANT`
- `REVOKE`
- `UPDATE` 无 `WHERE`
- `DELETE` 无 `WHERE`
- 多语句混合且任一语句为高风险
- `WITH ... UPDATE/DELETE`
- Calcite 成功解析但无法证明是低风险的 mutation

#### 解析失败策略

对于 SQL-bearing action：

- 如果是只读执行路径，解析失败不阻断执行，但 `riskLevel` 退回静态 descriptor / fallback 链
- 如果是 mutation preview / confirm 路径，解析失败按 `L3` 处理

原因：不能把“不认识”当成“安全”。

## 6. 数据流

### 6.1 SQL 风险分析流

1. OpenCode 或客户端发起 action invoke
2. `ActionDispatcher` 读取 `actionId` 与 `input`
3. `SqlBearingActionInspector` 提取 SQL
4. `CalciteSqlRiskAnalyzer` 返回结构化风险结果
5. 结果写入执行 metadata
6. handler 执行
7. 返回给前端的 part / output 带上 `state.metadata.riskLevel`
8. 前端 `resolveRisk` 先吃 part-level，再吃 descriptor，再吃正则 fallback

### 6.2 集成测试门禁流

1. 开发者运行 `cd server && mvn clean verify`
2. surefire 继续负责单测
3. failsafe 负责 `*IT.java`
4. `verify` 阶段聚合失败状态
5. 文档把 `verify` 标记为完整回归入口

## 7. 代码落点

### 7.1 后端实现

预计新增或修改：

- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlRiskAnalyzer.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlRiskAnalysis.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlBearingActionInspector.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java`
- `server/data-talk-domain/src/main/java/com/datatalk/domain/action/ActionContext.java` 或替代上下文对象
- `server/data-talk-adapter/pom.xml`

### 7.2 测试

预计新增或修改：

- `server/data-talk-application/src/test/java/.../CalciteSqlRiskAnalyzerTest.java`
- `server/data-talk-application/src/test/java/.../ActionDispatcherSqlRiskTest.java`
- `server/data-talk-adapter/src/test/java/.../DiscoveryControllerIT.java`
- 必要时补一个 adapter 级 IT，验证 `verify` 下确实执行 `*IT.java`

### 7.3 文档

预计新增或修改：

- `docs/exec-plans/tech-debt-tracker.md`
- `docs/BACKEND.md`
- `docs/QUALITY.md`
- 对应执行计划文件与索引

## 8. 错误处理与兼容性

### 8.1 Calcite 解析失败

必须显式区分两类失败：

- 语法不支持
- 语义上无法安全分类

两者都不能默默吞掉。需要在分析结果里标记：

- 是否成功解析
- 是否使用保守降级

### 8.2 旧前端兼容

前端已经支持：

- `part.state.metadata.riskLevel`
- `descriptor.riskLevel`
- 本地正则 fallback

因此后端上线后不要求前端同步改动即可受益。前端本次只需保留现有链路和测试，不做新行为设计。

### 8.3 非 SQL action

非 SQL-bearing action 不应被新预处理层影响。

要求：

- 无 SQL 输入的 action 不做额外解析
- 静态 descriptor 行为保持不变
- 现有 `L1` metadata / chart / schema 等 action 零行为变化

## 9. 测试策略

### 9.1 `TD-020`

必须覆盖：

- `SELECT` → `L1`
- `INSERT` → `L2`
- `DELETE` → `L3`
- `UPDATE ... WHERE ...` → `L2`
- `UPDATE` 无 `WHERE` → `L3`
- `WITH cte AS (...) UPDATE ...` → `L3`
- 多语句中含 `DROP` → `L3`
- Calcite 解析失败 → 保守降级路径
- `ActionDispatcher` 对 SQL-bearing action 注入 metadata
- 前端已有 `resolveRisk` 测试继续通过

### 9.2 `TD-021`

必须覆盖：

- `mvn clean verify` 下 `*IT.java` 被执行
- 至少选一条 adapter `*IT.java` 验证其确实参与 verify，而不是仅可手动点跑
- 文档和 AGENTS/开发指南语义一致

## 10. 风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| Calcite 不兼容部分方言 SQL | 误判或无法解析 | 明确保守降级策略；测试覆盖 MySQL / PostgreSQL 常见语法 |
| 统一预处理层改坏现有 action 调度 | action 全链路回归 | 以 `ActionDispatcher` 为中心补回归测试 |
| `*IT.java` 纳入后验证时间变长 | 本地开发体验下降 | 保留按模块/按类定向测试命令，完整回归统一走 `verify` |
| 文档与实现再次漂移 | 后续认知混乱 | 同一计划中同步更新 tech debt / backend guide / quality gate |

## 11. 验收标准

以下全部满足才算完成：

1. 后端存在通用 SQL AST 风险分析组件，依赖 Calcite
2. SQL-bearing action 在统一预处理层完成动态判级
3. 前端风险展示优先采用后端 part-level metadata
4. `TD-020` 在技术债文档中改为“已完成”或“剩余范围明确”
5. `mvn clean verify` 自动执行 adapter `*IT.java`
6. `TD-021` 在技术债文档中清除
7. `docs/BACKEND.md` 和 `docs/QUALITY.md` 明确 `verify` 是完整门禁

## 12. 推荐执行顺序

推荐拆成单一执行计划、分两批并行实现：

### Batch A

- `TD-021` Maven 门禁
- `TD-020` SQL 风险分析核心与单测

### Batch B

- `ActionDispatcher` 接入
- adapter / discovery / 文档同步
- 全量 `mvn clean verify`

这样可以先把测试门禁修好，再让后续风险判级改动自动受益于真实 IT 覆盖。
