# SQL Risk Classification & IT CI Gate — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 `TD-020` 与 `TD-021`：在后端统一 action 预处理层引入基于 Apache Calcite 的 SQL AST 风险判级，并让 `mvn clean verify` 自动执行 `*IT.java` 集成测试。

**Architecture:** 在 `ActionDispatcher` 的 schema 校验之后、handler 执行之前插入 SQL 风险分析预处理：`SqlBearingActionInspector` 负责识别 action/input 中的 SQL，`CalciteSqlRiskAnalyzer` 负责解析 AST 并输出标准化风险结果，结果通过 `ActionContext` 扩展元数据传入 handler，并写回 action 输出 metadata。测试门禁方面，保留 surefire 跑单测，在 adapter 模块引入 failsafe 执行 `*IT.java`，把 `verify` 固化为后端完整回归入口。

**Tech Stack:** Spring Boot 3.5 / Java 21 / Apache Calcite / JUnit 5 / AssertJ / Maven Surefire + Failsafe

**设计参照:** [docs/product-specs/2026-04-20-sql-risk-classification-and-it-ci-gate-design.md](../product-specs/2026-04-20-sql-risk-classification-and-it-ci-gate-design.md)

**非目标:**
- 不新增 GitHub Actions 或外部 CI 平台配置
- 不做 SQL 审计日志或影响行数精确估算
- 不重构现有非 SQL action 的输入/输出结构
- 不移除前端 `resolveRisk` 正则 fallback，只把它降级为兼容旧数据兜底

**依赖关系:**
- T1 仅负责计划登记
- T2（SQL 风险模型 + Calcite analyzer）是 T3/T4 的前置
- T3（dispatcher/context 集成）与 T5（failsafe 门禁）可并行
- T4（SQL action 输出回写与回归测试）依赖 T2/T3
- T6（文档与最终验证）依赖以上全部

---

## 文件结构地图

**后端 SQL 风险判级核心**
- Modify: `server/data-talk-application/pom.xml`
  作用：引入 Apache Calcite 依赖。
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/action/ActionExecutionMetadata.java`
  作用：承载统一预处理结果，至少包含 `sqlRisk`、`fallbackUsed`、`requiresStrongConfirmation`。
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/action/ActionContext.java`
  作用：为 handler 提供预处理 metadata，同时保留现有 4 参构造兼容旧调用点。
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlRiskAnalysis.java`
  作用：标准化 SQL 判级结果模型。
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlRiskAnalyzer.java`
  作用：application 层风险分析接口。
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java`
  作用：基于 Apache Calcite 的默认实现，覆盖 L1/L2/L3 与解析失败降级。
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlBearingActionInspector.java`
  作用：统一识别 action 是否携带 SQL，并从 input 中提取 `sql`。

**统一预处理接入**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java`
  作用：在 schema 校验后调用 inspector/analyzer，生成 `ActionExecutionMetadata` 并注入执行上下文。
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/ToolCallBridge.java`
  作用：构造带空 metadata 的 `ActionContext`，让 OpenCode 侧入口与本地入口一致。
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/action/ActionHandler.java`
  作用：如有必要，仅同步泛型/注释以体现 `ActionContext` 现已承载执行元数据；不改动 `handle(...)` 方法签名。

**SQL-bearing action 输出回写**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
  作用：从 `ctx.metadata()` 读取动态风险判级，把 `riskLevel` / `riskReason` / `fallbackUsed` 回写到输出 `metadata`。
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java` 对应 `outputSchema()`
  作用：把新增的 `metadata` 字段纳入 schema，避免 dispatcher 输出校验失败。

**测试与门禁**
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzerTest.java`
  作用：覆盖 SELECT / INSERT / UPDATE / DELETE / DROP / CTE-DML / parse failure。
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/session/ActionDispatcherTest.java`
  作用：验证 SQL-bearing action 在 dispatch 时注入动态风险，非 SQL action 不受影响。
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ExecuteSqlActionIT.java`
  作用：验证执行结果包含 `metadata.riskLevel` 且 SELECT 仍可正常落 artifact。
- Modify: `server/data-talk-adapter/pom.xml`
  作用：接入 `maven-failsafe-plugin`，显式纳入 `**/*IT.java`。

**文档与债务收口**
- Modify: `docs/exec-plans/tech-debt-tracker.md`
  作用：标记 `TD-020`、`TD-021` 已完成，保留实现边界说明。
- Modify: `docs/BACKEND.md`
  作用：把 `mvn clean verify` 固化为完整回归入口，并说明 SQL 风险判级位于 `ActionDispatcher` 预处理层。
- Modify: `docs/QUALITY.md`
  作用：明确 adapter IT 属于 `verify` 门禁的一部分。
- Modify: `docs/exec-plans/2026-04-20-sql-risk-classification-it-ci-gate-plan.md`
  作用：执行中勾选 checklist、记录偏差。
- Modify: `docs/exec-plans/index.md`
  作用：本计划完成后从 Active 移到 Completed。

---

## Task 1: 计划登记

**Files:**
- Create: `docs/exec-plans/2026-04-20-sql-risk-classification-it-ci-gate-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] 记录目标、非目标、文件结构地图、任务依赖与验证方式
- [x] 在 `docs/exec-plans/index.md` 活跃计划中登记本计划

---

## Task 2: SQL 风险模型与 Calcite 分析器

**Files:**
- Modify: `server/data-talk-application/pom.xml`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/action/ActionExecutionMetadata.java`
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/action/ActionContext.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlRiskAnalysis.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlRiskAnalyzer.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlBearingActionInspector.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzerTest.java`

- [x] 在 `data-talk-application` 模块引入 Apache Calcite 依赖，不污染 adapter/infrastructure
- [x] 定义 `SqlRiskAnalysis` 与 `ActionExecutionMetadata`，统一表达 `riskLevel`、`reason`、`requiresStrongConfirmation`、`fallbackUsed`
- [x] 为 `ActionContext` 增加 metadata 承载能力，并保留现有 4 参调用点兼容构造
- [x] 实现 `SqlBearingActionInspector`，首批识别所有 input 中显式包含 `sql: string` 的 action
- [x] 实现 `CalciteSqlRiskAnalyzer`，覆盖：
  - `SELECT` / `EXPLAIN` / `SHOW` / `DESCRIBE` => `L1`
  - `INSERT` / `CREATE VIEW` / `CREATE INDEX` / 受限 `UPDATE` => `L2`
  - `DELETE` / `DROP` / `ALTER` / `TRUNCATE` / `GRANT` / `REVOKE` / 无 `WHERE` 的 `UPDATE/DELETE` / 含 DML 的 `WITH` / 高风险多语句 => `L3`
- [x] 对解析失败实现保守降级：只读 action 允许 fallback，mutation action 直接判 `L3`
- [x] 补齐 `CalciteSqlRiskAnalyzerTest`，覆盖 `SELECT`、`INSERT`、`UPDATE ... WHERE`、`UPDATE` 无 `WHERE`、`DELETE`、`WITH ... UPDATE`、`DROP TABLE`、非法 SQL

---

## Task 3: ActionDispatcher 统一预处理接入

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/ToolCallBridge.java`
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/action/ActionHandler.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/session/ActionDispatcherTest.java`

- [x] 在 `ActionDispatcher.dispatch(...)` 的 schema 校验之后插入 SQL 风险预处理
- [x] 仅当 `SqlBearingActionInspector` 能提取到 SQL 时才计算动态风险；非 SQL action 保持零行为变化
- [x] 让 dispatcher 把 `SqlRiskAnalysis` 写入 `ActionContext.metadata()`，供 server/client/opencode 三类 executor 共用
- [x] 保持静态 `ActionDescriptor.riskLevel` 不变，用作未识别 SQL 或非 SQL action 的保底
- [x] 在 `ActionDispatcherTest` 新增断言：
  - SQL-bearing action 的 handler 能看到 `ctx.metadata().sqlRisk()`
  - 非 SQL action 的 metadata 为空
  - 解析失败但只读 action 仍继续执行

---

## Task 4: SQL-bearing action 输出 metadata 回写

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ExecuteSqlActionIT.java`
- Test: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/discovery/DiscoveryControllerIT.java`

- [x] 在 `ExecuteSqlAction` 中读取 `ctx.metadata().sqlRisk()`，将动态风险回写到输出 `metadata`
- [x] 扩展 `ExecuteSqlAction.outputSchema()`，允许返回：
  - `metadata.riskLevel`
  - `metadata.riskReason`
  - `metadata.fallbackUsed`
- [x] 保持 `SqlStatementGuard.assertSelectOnly(sql)` 现有行为不变；本次只做判级与透传，不改变执行准入
- [x] 更新 `ExecuteSqlActionIT`：
  - `SELECT` 成功时断言 `metadata.riskLevel == L1`
  - 非 SELECT 仍被 guard 拒绝
  说明：本地无 Docker，实际红绿验证改由新增的 `ExecuteSqlActionTest`（H2）完成；`ExecuteSqlActionIT` 仅同步断言，留待具备 Testcontainers 环境时执行。
- [x] 回归 `DiscoveryControllerIT`，确认静态 `ActionDescriptor.riskLevel=L1` 仍对 discovery 输出生效，避免前端描述页回退

---

## Task 5: Maven IT 门禁接入

**Files:**
- Modify: `server/data-talk-adapter/pom.xml`

- [x] 在 adapter 模块接入 `maven-failsafe-plugin`
- [x] 显式纳入 `**/*IT.java`，并绑定 `integration-test` / `verify`
- [x] 保持默认 surefire 负责 `*Test.java`，避免单测/集测职责混淆
- [x] 确认 `mvn clean verify` 会执行现有 `ChannelControllerIT`、`TypicalQueryE2EIT`、`ExecuteSqlActionIT` 等集成测试
  说明：通过 `help:effective-pom` 可见 failsafe 配置已生效；`mvn verify` 实测进入 adapter 测试阶段，但完整跑完仍受本机 Docker/Testcontainers 可用性约束。

---

## Task 6: 文档、债务收口与最终验证

**Files:**
- Modify: `docs/exec-plans/tech-debt-tracker.md`
- Modify: `docs/BACKEND.md`
- Modify: `docs/QUALITY.md`
- Modify: `docs/exec-plans/2026-04-20-sql-risk-classification-it-ci-gate-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] 更新 `tech-debt-tracker.md`，将 `TD-020`、`TD-021` 标记为已完成，并注明：
  - `TD-020` 已由后端 Calcite AST 判级闭环
  - `TD-021` 已由 failsafe + `mvn clean verify` 门禁闭环
- [x] 更新 `docs/BACKEND.md`，说明：
  - SQL 风险判级位于 `ActionDispatcher` 统一预处理层
  - 后端完整验证命令为 `cd server && mvn clean verify`
- [x] 更新 `docs/QUALITY.md`，明确 `*IT.java` 属于正式回归门禁
- [ ] 运行并记录验证命令：
  - `cd server && mvn compile -q`
  - `cd server && mvn -q -pl data-talk-application test -Dtest=CalciteSqlRiskAnalyzerTest,ActionDispatcherTest`
  - `cd server && mvn -q -pl data-talk-adapter -am verify`
  说明：前两项已完成；第三项已实际执行到 failsafe，并确认 `*IT.java` 进入门禁，但本机结果未全绿：`ExecuteSqlActionIT` / `LayoutErdActionIT` / `ReadSchemaActionIT` 受 Docker/Testcontainers 不可用影响，另有既存 `AiPrefsMigrationIT`、`TypicalQueryE2EIT`、`EndToEndSmokeIT` 失败/报错，需单独治理。
- [ ] 勾完本计划 checklist，并在完成后把索引从 Active 移到 Completed

## 决策日志

- 采用 Apache Calcite，而不是 JSqlParser：目标是规格闭环与后续可扩展性，不做一次性正则增强。
- 统一挂在 `ActionDispatcher`，而不是在 `ExecuteSqlAction` 里单点内嵌：避免未来 `preview_sql`、导入 SQL、客户端确认类 action 重复接入。
- 扩展 `ActionContext` 承载执行 metadata，而不是改 `ActionHandler.handle(...)` 签名：减少对现有 action 与测试的扰动，同时保留统一上下文。
- `ExecuteSqlAction` 本次只回写风险 metadata，不改变 `SqlStatementGuard` 的 SELECT-only 限制；执行准入策略后续若扩到 mutation action，再开独立 spec。
