## 1. 后端：对话式确认机制（SqlPendingConfirmationStore）

- [x] 1.1 在 `data-talk-application` 模块创建 `SqlPendingConfirmationStore`（内存 ConcurrentHashMap，5 分钟 TTL，定时清理），记录结构包含 sql / connectionId / sessionId / database / schema / source
- [x] 1.2 为 `SqlPendingConfirmationStore` 编写单元测试（创建/取出/过期清理/重启丢失）
- [x] 1.3 修改 `SqlExecuteService.execute()` — 新增 `confirmationId` 参数，当 `confirmationId` 非空时从 Store 取出挂起上下文直接执行（跳过风险分析）；新增 `source` 参数区分 AI/编辑器路径（AI 路径仅对 DELETE 拦截，编辑器路径保留原 L2/L3 逻辑）
- [x] 1.4 修改 `ExecuteSqlAction` — 移除 L2/L3 `blocked_in_chat` 分支；当 AI 路径检测到 DELETE 语句时，生成 confirmationId 存入 Store 并返回 `requires_confirmation`；当 `confirmationId` 非空时走二次确认执行路径
- [x] 1.5 重写 `ExecuteSqlActionTest` — 移除 L2/L3 blocked_in_chat 断言；新增 DELETE requires_confirmation、确认执行、确认过期、确认 ID 无效场景；验证非 DELETE DDL/DML 直接执行

> 验证：`cd server && mvn compile -q` + `mvn test -pl data-talk-application,data-talk-adapter -Dtest="*SqlPendingConfirmationStore*,*ExecuteSqlAction*"`

## 2. 后端：SqlStreamReader DDL 提取

- [x] 2.1 修改 `SqlStreamReader` — 解析阶段识别 `DROP TABLE IF EXISTS <name>` 和 `CREATE TABLE <name> (...)` 语句，分离为 `ddlPrefix`（String）和 INSERT 流数据；遇到不支持的 DDL（ALTER, CREATE INDEX 等）抛出 `UNSUPPORTED_DDL` 错误
- [x] 2.2 修改 `DataImportService.importFromFile()` SQL 文件路径 — 当 `ddlPrefix` 非空时，先通过 JDBC `Statement.execute()` 执行 DDL，再走原有 INSERT 批处理
- [x] 2.3 更新 `DataImportServiceTest` — 新增 DDL+INSERT 混合导入、DDL 目标表不匹配、不支持的 DDL、DROP TABLE 无 IF EXISTS 场景
- [x] 2.4 更新 `SqlStreamReaderTest` — 新增 DDL 语句识别、DDL+INSERT 分离、混合语句解析场景

> 验证：`cd server && mvn compile -q` + `mvn test -pl data-talk-application -Dtest="*SqlStreamReader*,*DataImportService*"`

## 3. 前端：移除 BlockedInChatCard

- [x] 3.1 从 `execute-sql.tsx` 移除 `BlockedInChatCard` 组件、`status === 'blocked_in_chat'` 分支及相关 import（`useStageStore`, `useConnectionStore`, `useSessionStore`, `Button`）；简化 `ExecuteSql` 为纯结果渲染
- [x] 3.2 清理 i18n 中仅 `BlockedInChatCard` 使用的 key（`chat.blockedInChat.*`, `chat.openInWorkbench`）
- [x] 3.3 更新 `execute-sql` 相关前端测试（移除 blocked_in_chat 场景断言）

> 验证：`cd client && npx tsc --noEmit`

## 4. AI 路由规则更新

- [x] 4.1 更新 `AGENTS.md` `## Registered Actions` — `datatalk_execute_sql` 描述从 "read-only SELECT/WITH" 改为"执行任意 SQL，仅 DELETE 需对话确认"；移除 `## Hard Constraints` 中的 execute_sql read-only 约束
- [x] 4.2 更新 `file-upload-routing/SKILL.md` SQL Files 路由规则 — 放宽条件为"statementTypes 仅包含 INSERT/DROP/CREATE 且 targetTables.size = 1"时可走 import_data
- [x] 4.3 验证 AGENTS.md 体积合规（去掉空行/注释后非空行 ≤ 350）

> 验证：`cd server && mvn compile -q`

## 5. 集成验证

- [x] 5.1 全量后端编译 + 测试：`cd server && mvn clean verify`
- [x] 5.2 全量前端类型检查：`cd client && npx tsc --noEmit`
- [x] 5.3 前端测试：`cd client && npx vitest run`
