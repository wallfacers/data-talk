---
id: BUG-0032
title: `seedH2Connection` fixture 用 `database` 字段名，被后端 `databaseName` DTO 忽略 → H2 fallback 到 `mem:test` 全测试共享
status: fixed
priority: P1
source: e2e-playwright
modules: [ingestion, testing]
discovered: 2026-05-13
discoveredBy: agent
testRunId: null
fixCommit: "uncommitted (Batch 5 hotfix)"
fixPlanRef: docs/exec-plans/2026-05-13-ingestion-e2e-bugs-fix-plan.md
duplicateOf: null
regression: false
---

## Summary
`tests/e2e/fixtures/ingestion-fixtures.ts` 的 `seedH2Connection` 用 `database` 字段名提交 connection，但后端 `ConnectionCreateRequest` DTO 用 `databaseName`，Jackson 解出 null。JdbcUrlBuilder 看到 db=null fallback 到 `jdbc:h2:mem:test` —— **所有并行 E2E test 共用一个 in-memory H2 instance**，create_ingestion_table / ingest_payload 期间 H2 connection close 后 database 丢失 → INSERT 时 `Table "X" not found (this database is empty)`。

## Reproduction Steps
1. 跑 `ingestion-execute-mcp.spec.ts:110 ingest_payload after create_table populates rows`。
2. 后端 INGEST-DEBUG 日志显示 `executeDdl url=jdbc:h2:mem:test` 而不是预期的命名 mem DB。

## Expected vs Actual
- **Expected**: 每个 test 一个独立命名 in-memory H2 DB，可重入。
- **Actual**: 所有 test 共用 `mem:test`，且 H2 default 在最后 connection 关闭时 dispose memory database，下个 connection 重建为空。

## Environment
- Frontend commit: working tree

## Root Cause
两层叠加：
1. **fixture 字段名笔误**：`database` 不是 backend DTO 字段；JSON 反序列化把它当成 unknown field 忽略。
2. **H2 in-memory 生命周期**：plain `jdbc:h2:mem:X` 没有 `DB_CLOSE_DELAY=-1`，最后一个 connection 关闭后 dispose database，state 丢失。

## Fix
修 fixture（不动 backend，因为后端 DTO 命名权威）：
```ts
databaseName: `mem:e2e_${Date.now()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL`,
```
- 把 `database` 改成 `databaseName` 对齐 DTO。
- 把 db value 改成完整 H2 URL 片段（`mem:NAME;DB_CLOSE_DELAY=-1;MODE=PostgreSQL`），保持每 test 独立 + 持久 + Postgres 兼容模式。
- 删掉无效的 `options: { mode: 'PostgreSQL' }`（DTO 没有这个字段，本来就 noop）。

## Verification
- E2E: `ingestion-ddl-mcp.spec.ts:28 H2 round-trip — CREATE TABLE then SELECT` 通过。
- E2E: `ingestion-execute-mcp.spec.ts:110 ingest_payload populates rows` 通过。
- 后端日志：URL 变成 `jdbc:h2:mem:e2e_<ts>;DB_CLOSE_DELAY=-1;MODE=PostgreSQL`。

## Notes
真正修产品 bug 的方式是给 `ConnectionCreateRequest` 加 `@JsonAlias("database")` 兼容 frontend 历史用法；但 frontend 实际用的就是 `databaseName`，fixture 的 `database` 完全是写错，未对齐。修 fixture 是最小修复路径。
