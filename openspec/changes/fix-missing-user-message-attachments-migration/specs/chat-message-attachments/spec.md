## ADDED Requirements

### Requirement: user_message_attachments 表通过独立 Flyway 迁移创建

`user_message_attachments` 表的 schema 定义 SHALL 由 `db/migration/V29__user_message_attachments.sql` 单一文件提供，不再由 `V1__init.sql` 包含。

V29 SHALL 使用 `CREATE TABLE IF NOT EXISTS` + `CREATE INDEX IF NOT EXISTS`，对以下三类环境幂等：

1. **Fresh DB**：从无到有创建（V1 仅含其他表）
2. **历史已应用 V1 含建表语句的环境**：跳过建表（表已存在）
3. **手工 workaround 已建表的环境**（含 BUG-0061 临时修复用户）：跳过建表

#### Scenario: Fresh DB 启动 user_message_attachments 表可达

- **GIVEN** `~/.data-talk/datatalk.db` 不存在
- **WHEN** 后端首次启动并执行 Flyway migrate
- **THEN** `flyway_schema_history` SHALL 含 version=1 和 version=29 两行
- **AND** `sqlite_master` SHALL 含 name=`user_message_attachments` 的 table
- **AND** `sqlite_master` SHALL 含 name=`idx_user_message_attachments_session_message` 的 index
- **AND** `GET /api/sessions/{id}/messages` 对任意 session id 都 SHALL NOT 因缺表返回 500

#### Scenario: 老环境升级到含 V29 的版本

- **GIVEN** 一个曾经应用过含建表语句的 V1 但缺 `user_message_attachments` 表的 DB
- **WHEN** 后端启动并执行 Flyway migrate
- **THEN** V29 SHALL 被应用并把 `user_message_attachments` 表 + 索引创建出来
- **AND** `flyway_schema_history` SHALL 新增 version=29 行
- **AND** `GET /api/sessions/{id}/messages` 对历史 session SHALL 返回 200 并正常回放消息

#### Scenario: 手工已建表的环境保持幂等

- **GIVEN** 一个开发者运行过 BUG-0061 文档的 sqlite3 workaround 手工建过表的 DB
- **WHEN** 后端启动并执行 Flyway migrate
- **THEN** V29 SHALL 因 `IF NOT EXISTS` 跳过实际 CREATE 但仍登记到 `flyway_schema_history`
- **AND** 不 SHALL 因表已存在抛 `SQLITE_ERROR`

### Requirement: V1__init.sql 不再包含 user_message_attachments 定义

`V1__init.sql` SHALL NOT 包含 `user_message_attachments` 表或对应索引的 DDL。这一职责完全转移到 V29。

#### Scenario: 静态校验 V1 不含 user_message_attachments

- **GIVEN** 仓库 develop 分支 HEAD
- **WHEN** 检查 `server/data-talk-infrastructure/src/main/resources/db/migration/V1__init.sql` 内容
- **THEN** 文件 SHALL NOT 包含字面字符串 `user_message_attachments`
