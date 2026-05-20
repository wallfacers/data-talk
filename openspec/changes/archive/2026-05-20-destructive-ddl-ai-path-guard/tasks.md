## 1. 核心拦截逻辑 (ExecuteSqlAction)

- [x] 1.1 在 `ExecuteSqlAction.java` 中新增 `containsDestructiveDdl(String sql)` 方法：按 `;` 切分 SQL，逐句检测首关键词（DROP/TRUNCATE/GRANT/REVOKE/DENY/KILL/SHUTDOWN/PURGE），正则匹配 `SET GLOBAL`（`(?i)^\s*SET\s+GLOBAL\b`）、`ALTER...DROP`（`(?i)ALTER\s+\w+\s+.*\bDROP\b`）、`INSERT OVERWRITE`（`(?i)^\s*INSERT\s+OVERWRITE\b`）。与 `containsDelete()` 同构
- [x] 1.2 在 `ExecuteSqlAction.execute()` 的 `containsDelete()` 之前新增 `containsDestructiveDdl()` 拦截门（检查顺序：DDL 门 → DELETE 门 → 执行）：命中时返回 `{ status: "redirect_to_editor", reason: "destructive_ddl", sql, affectedObjects: risk.affectedObjects(), message, suggestion: "use_query_editor" }`，不执行任何数据库操作。必须先于 containsDelete：否则 `DELETE FROM t; DROP TABLE t` 会被 DELETE 门先捕获，确认后回放整批导致 DROP 照样执行。`affectedObjects` 复用 line 229 已计算的 `risk.affectedObjects()`（`fallbackFor` 即使解析失败也会通过 `DDL_OBJECT_PATTERNS` 填充对象名）。不依赖 `risk.riskLevel()` 作为前提条件
- [x] 1.3 更新类级别 Javadoc 注释，反映新的拦截策略（不再是 "Only DELETE requires confirmation"）

## 2. AI 指令更新 (AGENTS.md + Skill)

- [x] 2.1 更新 `AGENTS.md` Identity 节：从 "Only DELETE statements require conversational confirmation" 改为包含破坏性 DDL 拦截和 redirect_to_editor 流程的完整说明。明确列出被拦截的操作类型（DROP/TRUNCATE/ALTER...DROP/GRANT/REVOKE/KILL/SHUTDOWN 等）和放行的操作（CREATE/INSERT/UPDATE/ALTER...ADD/MODIFY 等）
- [x] 2.2 更新 `sql-execution/SKILL.md` 执行契约：移除 "no READ-ONLY gate" 和 "just run it" 的 DDL 描述，新增 Destructive DDL redirect 规则。钉死 AI 收到 redirect_to_editor 后的单步动作：`datatalk_ui_exec(object="workspace", action="open", params={type:"query_editor", title:"<标题>", payload:{initialSql:"<sql>", autoRun:false}})` → 告知用户在编辑器中执行。注意：必须提供 `title`（schema 强制要求），必须设 `autoRun=false`，不需要单独 focus（open 自带聚焦，且 focus 需要未知的 tab id）
- [x] 2.3 更新 sql-execution SKILL.md 的 YAML frontmatter description：补充 redirect 触发关键词（drop/truncate/删除/删表/清空/截断 等）

## 3. 测试

- [x] 3.1 在 `ExecuteSqlActionTest.java` 新增测试：DROP TABLE 返回 `redirect_to_editor`（使用 H2 连接，验证主流库 Calcite 解析失败场景下拦截仍生效）
- [x] 3.2 新增测试：TRUNCATE TABLE 返回 `redirect_to_editor`（同上，H2 连接）
- [x] 3.3 新增测试：ALTER TABLE DROP COLUMN 返回 `redirect_to_editor`
- [x] 3.4 新增测试：GRANT / REVOKE 返回 `redirect_to_editor`
- [x] 3.5 新增测试：CREATE TABLE 仍直接执行（不拦截）
- [x] 3.6 新增测试：ALTER TABLE ADD COLUMN 仍直接执行（不拦截）
- [x] 3.7 DELETE 仍走 `requires_confirmation` 流程（已有测试覆盖，无需新增）
- [x] 3.8 新增测试：多语句 `DROP TABLE t1; CREATE TABLE t2 (id INT)` 返回 `redirect_to_editor`（因含 DROP 语句）
- [x] 3.9 新增测试：混合批次 `DELETE FROM t; DROP TABLE t` 返回 `redirect_to_editor`（验证 DDL 门先于 DELETE 门，不走 requires_confirmation）
- [x] 3.10 确认所有现有测试仍然通过（22 tests, 0 failures）

## 4. 验证

- [x] 4.1 运行 `mvn compile -q` 确认零编译错误
- [x] 4.2 运行 `mvn test -pl data-talk-adapter -Dtest=ExecuteSqlActionTest` 确认 22 tests 全部通过，BUILD SUCCESS
