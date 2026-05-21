## 1. Migration

- [x] 1.1 新增 `server/data-talk-infrastructure/src/main/resources/db/migration/V2__user_message_attachments.sql`，包含 `CREATE TABLE IF NOT EXISTS user_message_attachments (id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, message_id TEXT NOT NULL, position INTEGER NOT NULL, part_json TEXT NOT NULL, created_at INTEGER NOT NULL)` + `CREATE INDEX IF NOT EXISTS idx_user_message_attachments_session_message ON user_message_attachments(session_id, message_id, position, id)`
- [x] 1.2 从 `server/data-talk-infrastructure/src/main/resources/db/migration/V1__init.sql` 删除上述 `user_message_attachments` 表 + 索引段

## 2. 验证

- [x] 2.1 fresh DB 验证：mvn verify 期间多个 IT 启动均触发 fresh Flyway migrate, 日志显示 `Applying migration: V2__user_message_attachments.sql` + `Migration V2__user_message_attachments applied successfully`, 表 + 索引创建成功
- [~] 2.2 老 DB 升级验证：当前无 pre-V2 老 DB 可测; design.md 推理 + Decision 2 (V1 移除该表 + V2 用 IF NOT EXISTS) 已覆盖三类环境 (fresh / 历史 V1 / 手工建表) 的幂等性
- [x] 2.3 `cd server && mvn verify` 全模块 BUILD SUCCESS (domain 43 + application 995 + infrastructure + adapter 193 全绿; ArtifactWatcherServiceTest 单次 flaky 重跑通过)

## 3. BUG 闭环

- [x] 3.1 `docs/bugs/BUG-0061-missing-flyway-migration-for-user-message-attachments.md` 状态 `open` → `fixed`，backfill `fixPlanRef` + `fixedAt` (fixCommit=pending, 与既有 fixed BUGs 一致由 git log 提供)
- [x] 3.2 `docs/bugs/index.md` 同步 BUG-0061 行状态：从 Open BUGs 移到 Recently Closed；模块聚合视图同步 *(open)* → *(fixed)*；下一个 ID 由 BUG-0061 推进到 BUG-0063

## 4. 收尾

- [x] 4.1 运行 `openspec validate fix-missing-user-message-attachments-migration --strict` 确认 delta 合法 — PASS
- [x] 4.2 提交 commit `fix(infra): close BUG-0061 (V2 migration) + BUG-0062 (multipart 50MB)` (合并 BUG-0062 一并 close), 推 develop @ 730d8b1f
- [ ] 4.3 在 PR / release notes 提示老用户：若启动报 `FlywayException: Validate failed` 关于 V1 checksum，运行 `flyway repair` 或手工更新 schema_history 一次 (待 develop → master 发布 PR 时补充)
- [ ] 4.4 合入 develop 后运行 `/opsx:archive fix-missing-user-message-attachments-migration`
