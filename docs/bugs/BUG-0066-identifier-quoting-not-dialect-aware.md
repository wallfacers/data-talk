---
id: BUG-0066
title: 数据移动 4 处 quoteIdentifier 硬编码 ANSI 双引号，MySQL 默认 sql_mode 下 import_data / cross-DB copy / 导出回灌 / undo log 全崩
status: fixed
priority: P1
source: manual-report
modules: [data-import, data-export, script-data-write, undo-log]
discovered: 2026-05-19
discoveredBy: human
testRunId: null
fixCommit: 2ab9039f
fixPlanRef: openspec/changes/dialect-aware-import-export-and-friction-fix/
duplicateOf: null
regression: false
---

## Summary

`DataImportService` / `ScriptDataWriteService` / `DataExportService` / `InverseSqlGenerator` 4 处独立的 `quoteIdentifier` 实现都把标识符硬编码成 ANSI 双引号（其中 `InverseSqlGenerator` 甚至**完全不引用**）。MySQL 默认 `sql_mode` 不识别 `"id"` 形式标识符（视为字符串字面量），导致 `datatalk_import_data`、cross-DB 表复制、SQL 导出回灌、undo log 含特殊字符列名等场景全部失败。

## Reproduction Steps

1. 启动后端 + Tauri dev，绑定 MySQL 8.x 连接（默认 sql_mode，未启用 ANSI_QUOTES）。
2. 上传任意 .sql 文件（含 `INSERT INTO td_orders ... VALUES (...)` 等纯 INSERT 语句）。
3. 在 chat 输入"将数据导入到 datatalk_ctx"，让 AI 调 `datatalk_import_data(target.tableName=td_orders, target.connectionId=<mysql-id>, createTable=true)`。

## Expected vs Actual

- **Expected**：后端生成形如 `` CREATE TABLE `td_orders` (...) `` / `` INSERT INTO `td_orders` (...) VALUES (?, ?...) `` 的反引号 DDL/INSERT，MySQL 接受。
- **Actual**：后端生成 `CREATE TABLE "td_orders" (...)` —— MySQL 解析为"创建一张名字是字符串 `td_orders` 的表"，抛 `SQLSyntaxErrorException: You have an error in your SQL syntax`。`datatalk_import_data` 错误透传给 AI，AI 反复尝试 `script_run` 直连 / `update_connection` 加 `ANSI_QUOTES` 等错误退路，单次任务耗 20+ 轮工具调用仍未完成。

类似失败路径：
- `DataExportService` 导出 .sql 文件含 `INSERT INTO "td_orders" ...` —— 无法回灌同一 MySQL 实例
- `ScriptDataWriteService.writeStream` MySQL→MySQL cross-DB 复制同样崩
- `InverseSqlGenerator` 含保留字（`select` / `order` / `group`）或空格 / 引号字符列名生成 inverse SQL 时直接 raw 拼，运行必爆

## Environment

- Backend commit: c10289fc
- Frontend commit: c10289fc
- OS / Browser: WSL2 Ubuntu / Tauri dev
- Data source: MySQL 8.x（默认 sql_mode，不含 ANSI_QUOTES）

## Evidence

代码位点（`grep -n 'quoteIdentifier' server/data-talk-application/src/main`）：
- `DataImportService.java:361` — `return "\"" + id.replace("\"", "\"\"") + "\"";`
- `ScriptDataWriteService.java:228` — 同
- `DataExportService.java:428` — 同
- `InverseSqlGenerator.java:87` — `return identifier;`（完全不引用）

与文档不一致的"谎言"：
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` L984 声称 SQL 导出"按方言派发"（反引号 / 方括号 / 双引号），但代码 backed out

## Root Cause

四处独立实现都用 ANSI 双引号是历史惯性 —— 当年只测试 PostgreSQL / H2 / SQLite 三种"双引号兼容"DB，没引入方言抽象。后续接入 MySQL / SQLServer 时未补这一层，每次扩展只复用旧 helper。`InverseSqlGenerator` 是 undo-log 后续 change 加的，根本没意识到要引用。

## Fix

引入 `application/dialect/IdentifierQuoter`（按 `ConnectionKind` 派发反引号 / 双引号 / 方括号 + 转义），4 处调用点统一改为 `IdentifierQuoter.quote(id, kind)`。详细方案见 `openspec/changes/dialect-aware-import-export-and-friction-fix/design.md`（决策 D1-D5）。

修复后覆盖 19 种 first-class kind：
- BACKTICK：mysql, mariadb, tidb, oceanbase, apache_doris, starrocks, clickhouse
- DOUBLE_QUOTE：postgresql, h2, sqlite, oracle, duckdb, kingbase, dameng, gaussdb, hive, trino, presto
- BRACKET：sqlserver

未识别 kind fallback 双引号 + WARN 日志（不抛异常）。

`InverseSqlGenerator` 公共方法签名追加 `String connectionKind` 入参（**BREAKING 内部 API**），由 caller 从 `ConnectionRecord` 取出。

## Verification

- 单元测试：`IdentifierQuoterTest` 覆盖 19 kind × 三种 quote style 矩阵 + 大小写不敏感 + 嵌入引号字符转义 + null fallback
- 集成测试：`DataImportServiceTest` / `DataExportServiceTest` / `ScriptDataWriteServiceTest` / `InverseSqlGeneratorTest` 增加 MySQL 反引号 + 保留字列名场景
- E2E 手测：MySQL 连接重跑"导入 .sql 文件"原始场景 → 一次成功，rowsImported > 0
- 回归：`docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 新增 `## Identifier Quoting Per Dialect` 章节，并修正 L984 那条已 backed out 的声明

## Notes

- 与 [BUG-0067](BUG-0067-script-run-direct-db-connect-misuse.md) 同源（同一次失败 E2E）
- 本 BUG 仅覆盖标识符引用方言失配。值字面量引用（`'foo'` / `''`）已在 `escapeSqlValue` 处理，与本 BUG 无关
- 现有的 `IngestionDdlAdapter`（per-dialect MysqlIngestionDdlAdapter 等）只覆盖 ingestion 路径，不能复用 —— 本 BUG 修复独立组件 `IdentifierQuoter`
