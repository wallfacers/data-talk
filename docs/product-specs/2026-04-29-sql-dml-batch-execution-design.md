# SQL DML Batch Execution Design

## 背景

`/api/sql/execute` 当前已经支持多语句脚本：先通过方言化 splitter 得到语句列表，再在一个 JDBC transaction 中逐条 `Statement.execute()`。结果层会把连续无结果集语句聚合成一个 `dml_summary`，但执行层仍然逐条往数据库发送 SQL。用户批量插入同一张表或连续执行 DML 时，这会产生不必要的 round trip 和解析开销。

## 目标

- 对连续 DML 语句建立执行计划，减少逐条执行开销。
- 同时支持两类优化：
  - JDBC batch：连续 `INSERT` / `UPDATE` / `DELETE` 用 `Statement.addBatch()` + `executeBatch()`。
  - 同表 `INSERT ... VALUES` rewrite：连续同前缀 `INSERT INTO table(columns...) VALUES ...` 合并为单条 multi-values insert。
- 保持现有事务语义：任一执行单元失败则 rollback 整个 SQL batch。
- 保持现有 API / UI 契约：结果仍返回 `result_set`、`dml_summary`、`error`，前端无需改动。
- 风险分析仍基于用户原始 SQL，而不是重写后的 SQL。

## 非目标

- 不把 `SELECT` 聚合为 `UNION` 或 JDBC batch。
- 不把 `UPDATE` 重写为 `CASE WHEN`，不把 `DELETE` 重写为 `IN (...)`。
- 不优化 DDL、`CALL`、存储过程块、未知语句。
- 不新增前端控件或配置。

## 执行策略

新增 `SqlExecutionPlanner`，输入 splitter 产出的有序语句，输出有序 execution units：

- `SingleStatement`：普通单条语句，沿用当前 `Statement.execute()`。
- `DmlBatch`：连续 DML 语句。若可 rewrite，则只执行一条合并后的 SQL；否则使用 JDBC batch 执行原始语句列表。

rewrite 只在非常窄的安全窗口启用：

- 必须是连续语句。
- 每条都必须是 `INSERT INTO ... VALUES ...`。
- `VALUES` 前缀规范化后一致。
- `VALUES` 后只能是纯 tuple list，不能带 `ON DUPLICATE KEY UPDATE`、`RETURNING`、额外查询或客户端命令。

## 错误处理

- `SingleStatement` 保持现有错误定位。
- `DmlBatch` 失败时返回一个 `error` item，`statementIndex` 指向 batch 起始语句，`statementText` 为原始语句列表拼接。数据库事务立即 rollback。
- 如果后续要利用 `BatchUpdateException.updateCounts` 精确定位失败语句，可在不改变外部契约的基础上增强。

## 数据源兼容要求

`docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 需要记录：

- 哪些数据库类型允许 JDBC batch。
- 哪些数据库类型允许 SQL rewrite。
- rewrite 必须是 opt-in 的窄规则，不能作为所有 DML 的默认行为。

## 测试策略

- `SqlExecutionPlannerTest` 覆盖 execution unit 划分、同表 insert rewrite、`INSERT ... SELECT` 不 rewrite、`SELECT` 阻断 DML batch。
- `SqlExecuteServiceTest` 覆盖 batch/rewrite 后仍返回 `dml_summary`，且结果可被后续 `SELECT` 读到。
- 运行后端定向测试和 `mvn compile -q`。
