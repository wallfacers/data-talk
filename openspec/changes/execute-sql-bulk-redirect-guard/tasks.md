## 1. BUG 登记（合规闸前置）

- [x] 1.1 在 `docs/bugs/` 新建 `BUG-0070-execute-sql-bulk-bypass-import-data.md`（status=fixed，fixCommit 占位 pending；本 change 实施完成后填回）
- [x] 1.2 在 `docs/bugs/index.md` 把下一个分配 ID 改为 `BUG-0071`，并在 In Progress 表加 BUG-0070 行
- [x] 1.3 BUG 文档正文链 BUG-0065 / BUG-0067 / BUG-0069 同族对比表（参照 BUG-0069 文档 Notes 段写法）

## 2. domain 层：CallerKind enum + ActionExecutionMetadata 扩展

- [x] 2.1 新建 `server/data-talk-domain/src/main/java/com/datatalk/domain/action/CallerKind.java`：enum `{USER, AI}`
- [x] 2.2 修改 `server/data-talk-domain/src/main/java/com/datatalk/domain/action/ActionExecutionMetadata.java`：record 添加 `CallerKind callerKind` 字段（向下兼容：`empty()` 返回 `callerKind = AI` 作为保守默认）
- [x] 2.3 检查所有 `ActionExecutionMetadata.empty()` / `new ActionExecutionMetadata(...)` 调用点（grep `ActionExecutionMetadata`）—— 编译报错驱动修复，让显式构造方都补 callerKind 参数

## 3. application 层：BulkSqlGuard

- [x] 3.1 新建 `server/data-talk-application/src/main/java/com/datatalk/application/sql/BulkSqlGuard.java`：
  - 注入 `SqlRiskAnalyzer`（用于 INSERT 计数）
  - public `Verdict evaluate(String sql, CallerKind callerKind, String sourceFileId, String connectionId)`
  - USER → 直接返回 `Verdict.pass()`
  - AI → 依次检查 size_threshold（>4096 字节）/ insert_count_threshold（>20）/ originated_from_file（sourceFileId 非空）
  - 触发任一即返回 `Verdict.reject(reason, message, nextActionParams)`
- [x] 3.2 新建 `server/data-talk-application/src/main/java/com/datatalk/application/sql/BulkSqlVerdict.java`（或作为 BulkSqlGuard 内部 record）：
  - 字段：`boolean shouldReject`, `String reason`, `String message`, `Map<String,Object> nextActionParams`
  - 工具方法：`pass()` / `reject(...)`
- [x] 3.3 在 BulkSqlGuard 内实现 `parseTargetTable(sql)` —— 解析 INSERT INTO X 取得 tableName；多表返回 null
- [x] 3.4 单元测试 `server/data-talk-application/src/test/java/com/datatalk/application/sql/BulkSqlGuardTest.java`：
  - USER 路径无条件放行（含 100KB SQL 写入）
  - AI 路径纯 SELECT 10KB（多 CTE 多 JOIN）放行 —— 前提闸"含写入语句"未满足
  - AI 路径 SELECT + 1 条小 INSERT 但 size > 4096 触发 size 闸
  - AI 路径单语句多 VALUES INSERT（INSERT 计数 = 1 但字节 > 4096）触发 size 闸
  - AI 路径 21 条独立 INSERT 总计 < 4KB 触发 count 闸 + reason=insert_count_threshold
  - AI 路径 origin 闸（sourceFileId="f-1"，1KB SQL 含 5 条 INSERT）触发 reject + reason=originated_from_file
  - AI 路径纯 SELECT + sourceFileId（用户上传 SELECT 文件让 AI 读后执行的合法场景）放行
  - parseTargetTable 单表正常 / 多表返回 null / 大小写不敏感

## 4. adapter 层：入口路径强制注入 CallerKind

- [x] 4.1 修改 `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SqlExecuteController.java`：
  - 该路径架构上不构造 `ActionContext`（HTTP `POST /sql/execute` → `SqlExecuteService` → JDBC，绕过 ActionHandler/dispatcher），因此 BulkSqlGuard 天然无法触达；契约由架构层级强制（USER 路径不进 ActionContext 流即不进 guard），无需源码改动
  - `req.source()` 参数保留向下兼容（仅用于日志/统计），不影响安全决策
- [x] 4.2 修改 `server/data-talk-application/src/main/java/com/datatalk/application/opencode/McpActionBridge.java`：
  - 构造 ActionContext 时强制 `metadata = ActionExecutionMetadata.aiInitiated()`
- [x] 4.3 修改 `server/data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java`：
  - 构造 ActionContext 兜底走 `ActionExecutionMetadata.aiInitiated()`（如果没有上游显式设置）
- [x] 4.4 在 ActionExecutionMetadata 添加 `userInitiated()` / `aiInitiated()` 静态 factory

## 5. adapter 层：ExecuteSqlAction 接入 BulkSqlGuard

- [x] 5.1 修改 `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`：
  - 构造方法注入 `BulkSqlGuard`
  - `execute()` 中在 confirmation 分支判断之后、`executeNormal` 之前调用 `bulkGuard.evaluate(...)`
  - guard 返回 reject 时构造响应：`{status:"rejected", error, nextAction}` 并直接返回
- [x] 5.2 inputSchema 增加可选字段 `sourceFileId`：`Map.of("type", "string")`
- [x] 5.3 outputSchema 增加 `error` 与 `nextAction` 两个对象的 schema 定义（避免 schema 校验失败）
- [x] 5.4 单元测试 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ExecuteSqlActionBulkGuardTest.java`：
  - mock BulkSqlGuard 返回 reject，断言响应结构含 status=rejected / error.code=use_import_data / nextAction
  - mock 返回 pass，断言正常执行路径不变
  - confirmation 流程不触发 guard（持有 confirmationId 走 executeConfirmation 分支）

## 6. i18n description 重写

- [x] 6.1 修改 `server/data-talk-adapter/src/main/resources/messages.properties` 的 `action.execute_sql.description`：
  - 含 "Execute ONE SQL statement"
  - 含 "❗ MUST USE datatalk_import_data INSTEAD when ANY is true:" + 三条阈值
  - 含 "REJECTED" + "error.code=use_import_data" + "nextAction"
  - 含 "execute_sql IS the right tool for:" 正面用例
  - 含 "When the user runs SQL from their query editor UI, this guard does not apply"
  - 长度 ≤ 600 字符
- [x] 6.2 修改 `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties` 的 `action.execute_sql.description`：等价中文翻译，长度 ≤ 600 字符
- [x] 6.3 新建 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ExecuteSqlDescriptionContractTest.java`：
  - 加载两个 properties 文件
  - 断言英文含 "MUST USE datatalk_import_data" / "use_import_data" / "4096" / "20 INSERT" / "query editor"
  - 断言中文含等价短语（"必须使用 datatalk_import_data" / "use_import_data" / "4096" / "20 条 INSERT" / "查询编辑器"）
  - 断言两份长度均 ≤ 600 字符

## 7. sql-execution skill DO NOT 段

- [x] 7.1 修改 `server/data-talk-adapter/src/main/resources/skills/sql-execution/SKILL.md`：
  - 在 `# SQL Execution` 标题之后、首个 `## ...` 之前插入 `## ❗ DO NOT` 段
  - 内容：禁止 AI 把 >4096 字节 / >20 INSERT / 源自文件的 SQL 走 execute_sql；明示 use_import_data 错误码；明示用户编辑器路径放行
  - 参照 file-upload-routing SKILL.md SQL Files 段 DO NOT 子段的措辞风格
- [x] 7.2 扩展 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/SkillRoutingContractTest.java`：
  - 新增 `sqlExecutionSkill_hasDoNotSectionForBulkSql` 测试
  - 断言 SKILL.md 含 "❗ DO NOT" / "datatalk_import_data" / "use_import_data" / "4096" / "20 INSERT" / "query editor" 字面短语
- [x] 7.3 确认 sql-execution skill description 仍 ≤ 600 字符（drive-by 防止本次插入意外撑爆）：533 chars

## 8. 验收 / 端到端

- [x] 8.1 `cd server && mvn install -pl data-talk-domain -am -DskipTests`（domain 改了，必须装到 m2 才能 spring-boot:run 看见）
- [x] 8.2 `cd server && mvn install -pl data-talk-application -am -DskipTests`（application 改了，同上）
- [x] 8.3 `cd server && mvn clean verify`：全测试套件绿（4 模块 BUILD SUCCESS）—— 193 IT tests, 0 failures / 0 errors / 5 skipped
- [ ] 8.4 浏览器手测（playwright-cli）：BUG-0069 原始场景 —— 上传 20KB SQL 文件，发 "导入到 datatalk_ctx"
  - 期望：第一次 AI tool_call 即 `datatalk_import_data`（不出现 file_read + execute_sql）
  - 如果 AI 仍选 execute_sql：后端返回 status=rejected + nextAction，AI 应在第二次直接调 import_data 成功
- [ ] 8.5 浏览器手测：在 query_editor 粘贴 100KB SQL 点击运行
  - 期望：正常执行，不返回 rejected
- [ ] 8.6 浏览器手测：用户编辑器执行 SELECT 10KB 复杂查询
  - 期望：USER 路径放行，与 8.5 行为一致
- [x] 8.7 把本 change 实际 commit hash 回填 BUG-0070 文档 `fixCommit` 字段 + `docs/bugs/index.md` 同步行（commit `61923d3b`，docs backfill in `<follow-up commit hash>`）
