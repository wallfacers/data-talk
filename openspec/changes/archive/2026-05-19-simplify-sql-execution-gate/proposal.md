## Why

当前 SQL 风险闸门（L1/L2/L3）对所有 AI 发起的写操作一刀切拦截，强制路由到编辑器 AlertDialog 确认。用户明确要求"建表+导入数据"时，AI 生成的 DDL+DML SQL 被判为 L3 拦截 → 编辑器弹窗 → UX 断裂。实际场景中，用户信任 AI 执行他们明确要求的操作，不必要的确认摩擦是最大痛点。

## What Changes

- **BREAKING** 移除 `ExecuteSqlAction` 对 L2/L3 的 `blocked_in_chat` 拦截逻辑。AI 调用 `datatalk_execute_sql` 时，SELECT / INSERT / UPDATE / CREATE / DROP / ALTER / TRUNCATE 全部直接执行，返回结果到聊天
- **新增对话式确认**：仅 DELETE 语句（含 DELETE 无 WHERE）在 AI 路径返回 `requires_confirmation` 状态，AI 向用户展示影响并请求口头确认，用户在聊天中回复后 AI 二次调用执行
- **BREAKING** 移除前端 `BlockedInChatCard` 组件和"Open in Workbench"路由。编辑器 AlertDialog（`sql-confirmation`）仅保留给用户手动在编辑器中执行的 L2/L3 SQL，AI 路径不再走编辑器
- **增强 `SqlStreamReader`**：解析 SQL 文件时提取 DDL 前缀（DROP TABLE / CREATE TABLE），`DataImportService` 先执行 DDL 再流式 INSERT
- **放宽 file-upload-routing 路由**：当 intent = import 且 `targetTables = 1` 时，DDL（DROP/CREATE）+ INSERT 混合 SQL 文件可走 `datatalk_import_data`
- 移除 `CalciteSqlRiskAnalyzer` 对 AI 路径的风险分级（后端仍为编辑器用户手动执行保留 L2/L3 风险分析）

## Capabilities

### New Capabilities
- `conversational-sql-confirmation`: AI 路径下 DELETE 操作的对话式确认机制 — action 返回 `requires_confirmation` + 影响摘要，AI 向用户展示并在获得确认后二次调用执行

### Modified Capabilities
- `sql-confirmation`: 编辑器 AlertDialog 确认仅保留给用户手动执行的 SQL，移除 AI 路径的 `blocked_in_chat` → 编辑器路由。前端组件简化
- `data-import`: `SqlStreamReader` 增加 DDL 前缀提取能力，支持 DDL(DROP/CREATE) + INSERT 混合 SQL 文件导入
- `agent-skill-routing`: `file-upload-routing` skill 路由规则放宽，SQL 文件 DDL+INSERT 混合可走 import_data；`AGENTS.md` 硬约束从"execute_sql 只能 SELECT"改为"除 DELETE 外直接执行，DELETE 需对话确认"

## Impact

### 后端
- `ExecuteSqlAction` — 移除 L2/L3 blocked_in_chat 分支，新增 DELETE requires_confirmation 返回路径
- `SqlExecuteService` — AI 路径跳过风险闸门（编辑器路径保留），新增 `confirmationId` 机制支持二次确认
- `CalciteSqlRiskAnalyzer` — 保留用于编辑器路径，AI 路径不再调用
- `SqlStreamReader` — 增加 DDL 语句识别与提取
- `DataImportService` — SQL 文件导入流程增加 DDL 前置执行步骤
- `ImportDataActionHandler` — 无参数变更，内部增强

### 前端
- `execute-sql.tsx` — 移除 `BlockedInChatCard`，简化为纯结果渲染
- `sql-workbench-tab.tsx` — AlertDialog 仅响应用户手动执行的确认需求，移除 AI 触发的确认入口
- AGENTS.md + file-upload-routing SKILL.md — 路由规则重写

### 测试
- `ExecuteSqlActionTest` — 重写：移除 L2/L3 阻断断言，新增 DELETE requires_confirmation 场景
- `DataImportServiceTest` / `SqlStreamReaderTest` — 新增 DDL+INSERT 混合场景
- 前端测试 — `BlockedInChatCard` 相关用例移除

### 不受影响
- 编辑器手动执行的 L2/L3 确认流程（`sql-confirmation` spec 场景）保持不变
- `undo-log` 功能不受影响
- `datatalk_import_data` 的跨库复制、CSV/Excel/JSON 导入不受影响
