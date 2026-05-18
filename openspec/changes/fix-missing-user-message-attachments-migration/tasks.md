## 1. Migration

- [ ] 1.1 新增 `server/data-talk-infrastructure/src/main/resources/db/migration/V29__user_message_attachments.sql`，包含 `CREATE TABLE IF NOT EXISTS user_message_attachments (id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, message_id TEXT NOT NULL, position INTEGER NOT NULL, part_json TEXT NOT NULL, created_at INTEGER NOT NULL)` + `CREATE INDEX IF NOT EXISTS idx_user_message_attachments_session_message ON user_message_attachments(session_id, message_id, position, id)`
- [ ] 1.2 从 `server/data-talk-infrastructure/src/main/resources/db/migration/V1__init.sql` 删除上述 `user_message_attachments` 表 + 索引段

## 2. 验证

- [ ] 2.1 fresh DB 验证：`rm -f ~/.data-talk/datatalk.db && cd server && mvn install -pl data-talk-infrastructure -am -DskipTests && mvn spring-boot:run -pl data-talk-adapter`；启动成功后 `sqlite3 ~/.data-talk/datatalk.db "SELECT version FROM flyway_schema_history"` 含 1 + 29 两行；`SELECT name FROM sqlite_master WHERE type='table' AND name='user_message_attachments'` 返回一行
- [ ] 2.2 老 DB 升级验证：在备份过的旧 DB 上启动，确认 V29 被应用、表创建成功；若 V1 checksum 报错按 design.md Step 3 手工 repair 一次
- [ ] 2.3 `cd server && mvn verify` 全模块 BUILD SUCCESS（含 `UserMessageAttachmentRepositoryTest` 全绿）

## 3. BUG 闭环

- [ ] 3.1 `docs/bugs/BUG-0061-missing-flyway-migration-for-user-message-attachments.md` 状态 `open` → `fixed`，backfill `fixCommit` + `fixPlanRef` + `fixedAt`
- [ ] 3.2 `docs/bugs/index.md` 同步 BUG-0061 行状态，从 In Progress 移到 Closed (Fixed)；模块聚合视图同步

## 4. 收尾

- [ ] 4.1 运行 `openspec validate fix-missing-user-message-attachments-migration --strict` 确认 delta 合法
- [ ] 4.2 提交 commit `fix(db): add V29 migration for user_message_attachments (close BUG-0061)`，推 develop
- [ ] 4.3 在 PR / release notes 提示老用户：若启动报 `FlywayException: Validate failed` 关于 V1 checksum，运行 `flyway repair` 或手工更新 schema_history 一次
- [ ] 4.4 合入 develop 后运行 `/opsx:archive fix-missing-user-message-attachments-migration`
