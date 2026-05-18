---
id: BUG-0061
title: V1__init.sql 后期追加 user_message_attachments 表却没有独立的 Vxx 迁移，导致老 DB 缺表 500 错误
status: open
priority: P1
source: e2e
modules: [server, persistence, flyway]
discovered: 2026-05-18
discoveredBy: agent
testRunId: openspec/changes/batch-image-attachments-via-fileparts/ (E2E run)
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

`server/data-talk-infrastructure/src/main/resources/db/migration/V1__init.sql` 当前包含 `CREATE TABLE user_message_attachments (...)`，但仓库中没有任何独立的 `V29__...sql` 迁移文件提供同一张表。Flyway 一旦在历史时刻应用过 V1 ，再次启动时不会重新执行 V1（哪怕文件内容已变更，校验和差异也会被 Flyway 当成错误而非自动追加），导致**所有早于此次 V1 编辑的开发者本地 DB 都缺失该表**。

调用 `GET /api/sessions/{id}/messages` 时，`HistoryService.getMessages` → `UserMessageAttachmentRepository.findBySession` 直接执行 `SELECT * FROM user_message_attachments WHERE session_id = ?`，触发：

```
[SQLITE_ERROR] SQL error or missing database (no such table: user_message_attachments)
```

返回 HTTP 500，前端历史消息无法回放。

## Reproduction Steps

1. 在 2026-05-17 之前完成过一次后端启动（V1 已应用）
2. 拉取最新 develop 分支，启动 `mvn spring-boot:run -pl data-talk-adapter`
3. 打开任一历史 session
4. 前端调用 `GET /api/sessions/{id}/messages` → 500
5. 控制台报 `MaxUploadSizeExceededException` 旁的 `SQLITE_ERROR ... no such table: user_message_attachments`

## Expected vs Actual

- **Expected**：Flyway 在启动时检测到缺表，自动通过新 `V29__user_message_attachments.sql` 迁移创建表与索引
- **Actual**：缺表，500，前端历史回放失效

## Environment

- Backend commit: develop @ 2026-05-18
- DB: `server/data-talk-adapter/data/datatalk.db`（任何应用过 V1 但晚于 V1 表追加的 DB）

## Root Cause (hypothesis)

`user_message_attachments` 是 BUG-0056 修复时新增的表（参见该 BUG `fixPlanRef`）。当时直接修改了 V1 而非创建新的 V29 迁移。Flyway 不会对已应用的版本回放，只会用 checksum 校验；checksum 不一致时报错（但本地开发常忽略 / 用 repair 跳过）。

## Suggested Fix

- 新增 `server/data-talk-infrastructure/src/main/resources/db/migration/V29__user_message_attachments.sql`，内含 `CREATE TABLE IF NOT EXISTS user_message_attachments (...)` + `CREATE INDEX IF NOT EXISTS idx_user_message_attachments_session_message ...`
- 从 V1 移除该表定义（避免 checksum mismatch 影响新装环境；新装也走 V29 路径）
- 在 release notes 提示老用户运行 `flyway repair` 一次

## Workaround (临时)

手工建表：

```sql
sqlite3 ~/.data-talk/datatalk.db "
CREATE TABLE IF NOT EXISTS user_message_attachments (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    message_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    part_json TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_user_message_attachments_session_message
    ON user_message_attachments(session_id, message_id, position, id);
"
```

## Notes

发现于 `batch-image-attachments-via-fileparts` change 的 E2E 验证阶段。与本次图片改造**无关**，纯粹是历史 DB 迁移漂移。
