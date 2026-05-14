---
id: BUG-0045
title: IngestionHeartbeatSweeperIT / IngestionStartupSweeperIT 测试库无 ingestion_job 表（H2 schema 未初始化）
status: open
priority: P2
source: regression-test-sweep
modules: [ingestion]
discovered: 2026-05-14
discoveredBy: agents-md-skills-refactor implementation
testRunId: null
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

`mvn -pl data-talk-adapter verify` 在 develop baseline（commit 144ed086，未应用 agents-md-skills-refactor 改动）即可复现：两个 ingestion sweeper IT 因测试 H2 库找不到 `ingestion_job` 表而失败：

- `IngestionHeartbeatSweeperIT.flipsRowsWithStaleHeartbeat`
- `IngestionStartupSweeperIT.flipsAllNonTerminalStatusesToFailed`

两条 IT 在 `@BeforeEach` / 测试体内通过 `JdbcTemplate.update("DELETE FROM ingestion_job WHERE id LIKE '...'")` 清表，但 H2 上下文未跑 Flyway 创建 `ingestion_job`，直接报 `Table "INGESTION_JOB" not found; SQL statement: DELETE FROM ingestion_job ...`。

## Reproduction Steps

1. Checkout `develop` (commit 144ed086) 之前任意提交。
2. `cd server && mvn install -pl data-talk-adapter -am -DskipTests -q`
3. `mvn -pl data-talk-adapter failsafe:integration-test failsafe:verify -q -Dit.test='IngestionHeartbeatSweeperIT,IngestionStartupSweeperIT'`
4. 观察：2 errors / 0 failures，皆为 `BadSqlGrammarException: Table "INGESTION_JOB" not found`。

## Expected vs Actual

- **Expected**：测试启动时通过 Flyway / DDL 初始化 H2 `ingestion_job` 表；`DELETE FROM ingestion_job` 成功（0 行受影响是允许的）。
- **Actual**：表不存在，立即 `BadSqlGrammarException`。

## Environment

- Backend commit (baseline)：144ed086
- JVM：OpenJDK 21
- Test DB：H2（嵌入式 JDBC，由测试 datasource 配置）

## Evidence

```
Caused by: org.h2.jdbc.JdbcSQLSyntaxErrorException:
  Table "INGESTION_JOB" not found; SQL statement:
  DELETE FROM ingestion_job WHERE id LIKE 'hb_sweep_%' [42102-232]
```

完整 stack 见 `target/failsafe-reports/com.datatalk.adapter.ingestion.IngestionHeartbeatSweeperIT.txt`。

## Root Cause Hypothesis

两条 IT 未启用 `@SpringBootTest`（或类似机制）来加载 Flyway-backed datasource，或测试 datasource 配置未指向同一份 Flyway 迁移路径。`ingestion_job` 表由 `data-talk-infrastructure` 的 Flyway 迁移创建，IT 上下文若用裸 `H2 in-memory` 配置而未跑迁移，自然找不到表。

具体定位需读 IT 类 + 测试 datasource 配置（本 BUG 仅登记，不在 agents-md-skills-refactor 范围内修复）。

## Notes

- 与 `agents-md-skills-refactor` 实施无关（baseline 已经失败）。
- 在 agents-md-skills-refactor PR 验证窗口里，`mvn verify` 命令通过 `-Dit.test='!IngestionHeartbeatSweeperIT,!IngestionStartupSweeperIT'` 排除这两条 IT 即可观察其余 IT 全绿；含本变更后单测 180/180、IT 366/366 通过。
- 修复策略候选：(a) 给 sweeper IT 加 `@AutoConfigureTestDatabase(replace = NONE)` + Flyway autorun；(b) 改成 `JdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ingestion_job ...")` 自建表；(c) 用 `@SpringBootTest` 完整上下文。
- 实施期发现 develop branch 上存在 5 个未提交 WIP 文件：(1) `server/data-talk-adapter/src/test/resources/schema.sql`（补 `ingestion_job` / `ingestion_credential` / `ingestion_vault_store` 三表的 DROP / CREATE）、(2) `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/IngestionJobStatus.java`（加 `confirmed` 枚举值）、(3) `server/data-talk-infrastructure/src/main/resources/db/migration/V20__ingestion.sql`（`awaiting_confirm` → `confirmed`）、(4) `server/data-talk-adapter/src/test/java/com/datatalk/adapter/ingestion/IngestionHeartbeatSweeperIT.java`（`@Autowired JdbcTemplate jdbc` → `@Autowired @Qualifier("datatalkJdbc") JdbcTemplate jdbc`）、(5) `server/data-talk-adapter/src/test/java/com/datatalk/adapter/ingestion/IngestionStartupSweeperIT.java`（同 (4)）。组合起来是 enum 重命名 + IT 测试指向正确的 datatalk datasource，**预期会修掉本 BUG**。本 BUG 仅登记现象，不在 `agents-md-skills-refactor` 范围内修；待该 WIP 提交后通过 sweeper IT 重跑核验，再视情况关闭本 BUG。
