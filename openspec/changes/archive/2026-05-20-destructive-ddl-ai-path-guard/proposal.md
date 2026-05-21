## Why

AI 通过 `datatalk_execute_sql` 聊天路径可以不经确认直接执行 `DROP TABLE`、`TRUNCATE`、`ALTER TABLE DROP COLUMN` 等破坏性 DDL。当前仅 `DELETE` 语句走对话式确认流程，其他 L3 操作虽然在风险分析中被正确识别，但没有任何拦截门。用户说"给我删除 td_orders"时，AI 尝试 DROP → 被阻 → 尝试 DELETE → 需确认 → 换 TRUNCATE 直接执行成功，表数据被清空。需要立即堵住这个安全漏洞。

## What Changes

- `ExecuteSqlAction` 新增 destructive DDL 拦截门：DROP/TRUNCATE/GRANT/REVOKE/KILL/SHUTDOWN 等 L3 操作拒绝直接执行，返回 `redirect_to_editor` 状态，引导 AI 打开 query_editor 让用户自行执行
- 保留 DELETE 的现有确认流程不变
- `ALTER ... DROP *`（DROP COLUMN/DROP PARTITION/DROP CONSTRAINT）通过正则补充拦截
- 放开建设性 L3 操作：CREATE *、ALTER ... ADD/MODIFY、RENAME、MERGE、OPTIMIZE、VACUUM 等 AI 可直接执行
- 更新 AGENTS.md Identity 节和 sql-execution SKILL.md，明确 AI 不能直接执行删除/破坏性 SQL

## Capabilities

### New Capabilities

（无新 capability，破坏性 DDL 拦截是现有 `conversational-sql-confirmation` 的扩展）

### Modified Capabilities

- `conversational-sql-confirmation`: 扩展确认机制，从仅拦截 DELETE 扩展到拦截所有破坏性 DDL（DROP/TRUNCATE/ALTER...DROP/GRANT/REVOKE/KILL/SHUTDOWN 等），并增加 `redirect_to_editor` 响应类型

## Impact

- **代码**：`ExecuteSqlAction.java` — 新增 `containsDestructiveDdl()` 方法（基于原始 SQL 关键词扫描，与方言无关）和 `redirect_to_editor` 响应分支
- **Spec**：`openspec/specs/conversational-sql-confirmation/spec.md` — 修改"非DELETE直接执行"场景，新增 DDL 拦截场景
- **Skill**：`sql-execution/SKILL.md` — 更新执行契约，新增 DDL redirect 流程说明，钉死 AI 动作链
- **Skill**：`AGENTS.md` Identity 节 — 更新指令
- **测试**：`ExecuteSqlActionTest.java` — 新增 TRUNCATE/DROP/ALTER...DROP 拦截用例，含 H2 连接（验证 Calcite 解析失败场景）和多语句用例
- **无前端改动**：redirect_to_editor 的实际路由由 AI 侧（AGENTS.md + skill 指令）控制，前端无需修改
- **无数据库迁移**：不涉及元数据表变更
- **数据源类型兼容性**：拦截机制基于 SQL 文本关键词扫描，与 Calcite 风险分析器无关，对所有 20 种方言统一生效。无需更新 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
