## MODIFIED Requirements

### Requirement: undo_log 表 SHALL 支持按 connection 高效查询

`undo_log` 表 SHALL 新增复合索引 `idx_undo_log_conn_status_created(connection_id, status, created_at DESC)`，优化按 connection + status 过滤并按时间降序排序的分页查询性能。

#### Scenario: 索引存在性

- **GIVEN** Flyway 迁移执行完成
- **WHEN** 检查 undo_log 表索引
- **THEN** SHALL 存在 `idx_undo_log_conn_status_created` 索引，列为 `(connection_id, status, created_at DESC)`

#### Scenario: 分页查询使用索引

- **GIVEN** undo_log 表有 10000+ 条记录
- **WHEN** 执行 `SELECT ... FROM undo_log WHERE connection_id = ? AND status = ? ORDER BY created_at DESC LIMIT ?`
- **THEN** 查询计划 SHALL 使用 `idx_undo_log_conn_status_created` 索引
