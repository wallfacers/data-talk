## ADDED Requirements

### Requirement: InverseSqlGenerator 生成的 SQL SHALL 按目标 connection kind 引用标识符

`InverseSqlGenerator` 在为 undo log 生成 inverse DELETE / UPDATE / INSERT 语句时，表名与列名 SHALL 通过 `IdentifierQuoter.quote(id, kind)` 按当时连接 kind 引用。`InverseSqlGenerator` 公共方法签名 SHALL 接受 `String connectionKind` 入参，由调用方（`UndoLogService` / `UndoLogRepository`）从 `ConnectionRecord` 取出后传入。

#### Scenario: MySQL 表名含空格生成正确的反引号 inverse DELETE

- **GIVEN** 源 connection kind 为 `mysql`，表 `` user data ``（含空格）含 PK `id`
- **AND** 一条原始 INSERT 写入了 pk=42 的行
- **WHEN** `InverseSqlGenerator.buildInverseForInsert("user data", "id", List.of("42"), "mysql")` 被调用
- **THEN** 返回 `` DELETE FROM `user data` WHERE `id` = 42 ``

#### Scenario: PostgreSQL 含保留字列名生成正确的双引号 inverse UPDATE

- **GIVEN** 源 connection kind 为 `postgresql`，表 `orders` 含列 `select`（保留字）
- **AND** 一条原始 UPDATE 修改了 `select` 列
- **WHEN** `InverseSqlGenerator.buildInverseForUpdate(...)` 用 kind=`postgresql` 被调用
- **THEN** 生成的 UPDATE 包含 `SET "select" = 'old_value'` 而非裸 `SET select = ...`

#### Scenario: SQL Server 列名含右方括号字符 SHALL 被正确转义

- **GIVEN** 源 connection kind 为 `sqlserver`，列名 `weird]col`
- **WHEN** `InverseSqlGenerator` 用 kind=`sqlserver` 生成 inverse SQL
- **THEN** 标识符形如 `[weird]]col]`（右括号转义为双右括号）

#### Scenario: kind 为 null SHALL fallback 双引号且不抛异常

- **GIVEN** 调用方未能解析出 connection kind（例：旧 undo 记录无 kind 信息）
- **WHEN** `InverseSqlGenerator.buildInverseForDelete(..., null)`
- **THEN** 标识符用 ANSI 双引号引用
- **AND** 系统输出 WARN 日志
- **AND** 不抛异常
