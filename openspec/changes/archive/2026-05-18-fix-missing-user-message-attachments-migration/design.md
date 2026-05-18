## Context

`user_message_attachments` 是 BUG-0056 修复时新增的持久化表，参见 archived change `2026-05-17-user-bubble-attachments`。当时的做法是直接在 `V1__init.sql` 末尾追加表定义而非新增独立 `V2__...sql` 迁移。

Flyway 的语义保证：
- 已应用版本的 SQL 内容若发生变化（checksum 不一致），Flyway 在下次启动时会 **报错**（`FlywayException: Validate failed`），而不是回放 V1 重新建表
- 即使运行 `flyway repair`，也只是把 schema_history 表里的 checksum 更新成新的，不会自动建表
- 唯一可靠的迁移路径是新增更高版本号 `V2__...sql`

所以现状的破裂面：
1. 历史已应用过 V1 的开发者 / CI 环境：本地 `~/.data-talk/datatalk.db` 缺 `user_message_attachments` 表，启动后任何历史 session 查询都 500
2. fresh 环境（新机器、删 DB 重来）：因为 V1 内含建表语句，能正常工作；但 V1 的 checksum 跟旧环境不一致

本 change 一次性修这两个面。

## Goals / Non-Goals

**Goals:**

- 老 DB 升级路径：首次启动自动应用 V2，建表 + 索引，HTTP 500 消失
- 新 DB 安装路径：V1 不再含该表定义，由 V2 创建，二者 checksum 在所有环境保持稳定
- 对手工建表的环境（含 BUG-0061 临时 workaround 用户）幂等兼容（`IF NOT EXISTS`）

**Non-Goals:**

- 不引入 Flyway baseline 重整 / repair 自动化 — 由开发者按 release notes 自行执行
- 不调整 `user_message_attachments` 的列结构 — 完全复用 V1 当前定义
- 不修复其他可能的 V1 漂移（若有），由后续独立 change 处理

## Decisions

### Decision 1: 新增 V2 而非修改 V1 checksum

**Alternatives:**

a) 改 V1 checksum + 文档要求所有人 `flyway repair`：高摩擦，每个 dev 都得手动操作，CI 也要修
b) 新增 `V2__user_message_attachments.sql` 同时从 V1 删除该表：低摩擦，Flyway 自动应用 V2，新装 / 老环境都走同一路径 ✅

**Chosen: b)**

**Rationale:** Flyway 的设计哲学就是"版本号递增 + 永不修改已应用版本"。V2 路径让升级行为对 dev 完全透明，唯一额外成本是新 fresh DB 也会多一次 migration（< 10ms），可忽略。

### Decision 2: 同步从 V1 删除该表定义

**Alternatives:**

a) V1 保留、V2 用 `CREATE TABLE IF NOT EXISTS`：新装环境 V1 建表后 V2 是 no-op；checksum 仍跟历史 V1 不一致，不解决核心问题
b) V1 删除、V2 唯一建表点：所有环境的 V1 checksum 回到追加前的状态，跟最早期 V1 一致；新装走 V2 ✅

**Chosen: b)**

**Rationale:** Decision 1 + Decision 2 组合才能让 V1 checksum 在"任何曾经应用过 V1 的环境"和"新装环境"之间保持一致。否则 Decision 1 单独修不了 checksum 漂移的源头。

### Decision 3: 使用 `IF NOT EXISTS` 防御手工建表

**Rationale:** BUG-0061 文档已提供 sqlite3 CLI workaround，部分 dev 可能已经手工建过表。V2 用 `CREATE TABLE IF NOT EXISTS` + `CREATE INDEX IF NOT EXISTS` 对这部分环境幂等，不会因表已存在而失败。

## Risks / Trade-offs

- **Risk**: V1 checksum 仍会跟"已应用过含建表语句版本的 V1"的 DB 不一致 → 启动报 FlywayException
  - **Mitigation**: 在 release notes 明确提示「老用户首次启动前需运行 `flyway repair` 或在 schema_history 表手工更新 V1 checksum」。这是一次性操作，比当前持续 500 的体验好
- **Risk**: 多个开发者并发修 V2 文件名冲突
  - **Mitigation**: V2 是已知 next 可用版本号（grep V[0-9]+ 验证），本 change 单文件单 PR 不会冲突
- **Trade-off**: 不引入自动 repair = 一次性手动成本；引入则需写 CLI 工具，超出本 change 范围

## Migration Plan

1. 新增 `V2__user_message_attachments.sql`
2. 从 `V1__init.sql` 删除 `CREATE TABLE user_message_attachments ...` 段和对应 `CREATE INDEX ...`
3. 在 BUG-0061 文档 "Fix Plan" 小节追加：合入 develop 后 dev 启动若报 checksum 错，运行 `sqlite3 ~/.data-talk/datatalk.db "UPDATE flyway_schema_history SET checksum = <new> WHERE version='1'"`，或者干脆 `flyway repair` + 重启
4. 验证：
   - fresh DB：删 `~/.data-talk/datatalk.db` 重启，确认 `flyway_schema_history` 含 V1 + V2 两行
   - 老 DB：保留现有 DB，确认启动后自动应用 V2，表创建成功；如报 V1 checksum 错，按 Step 3 手工修一次
5. 关闭 BUG-0061，backfill `fixCommit` + `fixedAt`

## Open Questions

- 是否需要在 `application.yml` 启用 `spring.flyway.validate-on-migrate=false` 临时绕过 V1 checksum 校验？倾向否 — 让校验报错并由 dev 手工修一次比静默继续更安全
