## Why

`server/data-talk-infrastructure/src/main/resources/db/migration/V1__init.sql` 当前包含 `CREATE TABLE user_message_attachments`，但仓库中没有任何独立的 `V29__...sql` 迁移文件。Flyway 一旦在历史时刻已经应用过 V1，再次启动时不会回放 V1（哪怕文件内容已变更，checksum 差异只会报错而不会自动追加），导致**所有早于 V1 这次编辑的开发者本地 DB 都缺这张表**。

调用 `GET /api/sessions/{id}/messages` 时，`HistoryService.getMessages → UserMessageAttachmentRepository.findBySession` 直接执行 `SELECT * FROM user_message_attachments WHERE session_id = ?`，触发 `SQLITE_ERROR ... no such table: user_message_attachments` → HTTP 500，前端历史消息无法回放。

参见 [BUG-0061](../../../docs/bugs/BUG-0061-missing-flyway-migration-for-user-message-attachments.md)（P1, status open, 由 `batch-image-attachments-via-fileparts` E2E 验证阶段发现）。

## What Changes

- 新增 `server/data-talk-infrastructure/src/main/resources/db/migration/V29__user_message_attachments.sql`，内含 `CREATE TABLE IF NOT EXISTS user_message_attachments (...)` + `CREATE INDEX IF NOT EXISTS idx_user_message_attachments_session_message ...`，与 V1 中追加的定义完全一致
- 从 `V1__init.sql` 移除该表 + 索引定义（避免新装环境 checksum 不一致，新装也统一走 V29 路径）
- 在 BUG-0061 文件追加修复说明（建议老用户运行一次 `flyway repair` 或手工建表，details 已在 BUG 文档"Workaround"小节）
- 在 release notes / CHANGELOG 提示老用户首次启动会自动应用 V29

## Capabilities

### Modified Capabilities

- `chat-message-attachments`: 历史消息附件持久化依赖 `user_message_attachments` 表在 fresh DB 和 incremental upgrade DB 两类环境都能正常存在；新增 V29 迁移使该表的可达性独立于 V1 checksum

## Impact

- **代码**：仅 `server/data-talk-infrastructure/src/main/resources/db/migration/` 下两个 SQL 文件（V1 删一段 + 新增 V29）
- **运行时**：首次启动会自动应用 V29，对已通过其他途径手工建表的环境兼容（`CREATE TABLE IF NOT EXISTS`）
- **测试**：现有 `UserMessageAttachmentRepositoryTest` 用例集自动覆盖该表 schema；本 change 无需新增测试
- **风险**：checksum 漂移已经存在；新增 V29 不会让现状更糟，反而修正未来 fresh DB 行为
- **关联 BUG**：close BUG-0061
