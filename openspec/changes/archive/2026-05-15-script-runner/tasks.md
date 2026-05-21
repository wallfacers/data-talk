## 1. 清理旧 ingestion 模块

- [x] 1.1 删除 `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/` 全部 14 个文件
- [x] 1.2 删除 `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/` 全部 16 个文件
- [x] 1.3 删除 `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/ingestion/` 全部 4 个文件
- [x] 1.4 删除 `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ingestion/` 全部 6 个 ActionHandler
- [x] 1.5 删除 `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/IngestionController.java`
- [x] 1.6 删除 `server/data-talk-adapter/src/main/resources/skills/data-ingestion/` 全部文件（SKILL.md + 5 recipes + 3 examples）
- [x] 1.7 删除所有 ingestion 相关测试文件（25+ files）：domain tests, application tests, infrastructure ITs, adapter ITs, controller tests
- [x] 1.8 删除 `client/src/features/ingestion/` 全部 15+ 文件
- [x] 1.9 修改 DtEvent.java：删除 8 个 Ingestion* record 类型 + typeName() switch 中对应 case 分支
- [x] 1.10 修改 `OpenCodeGatewayBeans`：删除 `data-ingestion` skill sync 调用
- [x] 1.11 修改 AGENTS.md：删除 Registered Actions 表中 6 个 ingestion tool 行、Trigger Gate 中 `skill:data-ingestion` 行、Skill Index 中 `skill:data-ingestion` 行
- [x] 1.12 修复 tab-type-registry.ts：删除 `ingestion_job` 和 `ingestion_library` tab 类型注册
- [x] 1.13 清理前端对 ingestion tab 类型的引用（StageTab 相关导入、useIngestionJobsStore 引用等）
- [x] 1.14 **验证**: `mvn compile -q` + `npx tsc --noEmit` + grep 残留引用确认零编译错误

## 2. 后端 Domain 层

- [x] 2.1 创建 `ScriptLanguage` enum（`PYTHON`, `JAVASCRIPT`）在 `domain/script/`
- [x] 2.2 创建 `ScriptStatus` sealed interface（`Running`, `Completed`, `Failed`, `Cancelled`）在 `domain/script/`
- [x] 2.3 创建 `ScriptRun` record（id, scriptContent, language, status, exitCode, stdoutText, connectionId, targetTable, rowsWritten, name, createdByKind, createdBySessionId, errorMessage, startedAt, finishedAt, durationMs）在 `domain/script/`
- [x] 2.4 修改 DtEvent.java：新增 3 个 script record 类型（`ScriptRunStarted`, `ScriptRunOutput`, `ScriptRunCompleted`）+ typeName() switch 分支
- [x] 2.5 **验证**: `mvn install -pl data-talk-domain -am -DskipTests`

## 3. 后端 Application 层

- [x] 3.1 创建 `ScriptTokenStore`（ConcurrentHashMap, TTL=10min, 绑定 connectionId + runId）在 `app/script/`
- [x] 3.2 创建 `ScriptRunRepository` interface（save, findById, list, updateStatus）在 `app/script/repository/`
- [x] 3.3 创建 `ScriptRunService`（创建 run、签发 token、记录完成、查询历史）在 `app/script/`
- [x] 3.4 创建 `ScriptDataWriteService`（批量写入、自动建表、schema 推断）在 `app/script/`
- [x] 3.5 创建 `ScriptDataBatchService`（流式写入 session 管理、超时清理）在 `app/script/`
- [x] 3.6 **验证**: `mvn install -pl data-talk-application -am -DskipTests`

## 4. 后端 Infrastructure 层

- [x] 4.1 创建 Flyway V26 migration：DROP `ingestion_job`、`ingestion_credential`、`ingestion_vault_store`；CREATE `script_run` 表
- [x] 4.2 创建 `JdbcScriptRunRepository` 实现 `ScriptRunRepository` interface
- [x] 4.3 **验证**: `mvn install -pl data-talk-infrastructure -am -DskipTests`

## 5. 后端 Adapter 层

- [x] 5.1 创建 `RunScriptActionHandler`（`@DataTalkAction("datatalk_script_run")`）：校验环境、签发 token、返回 runId
- [x] 5.2 创建 `StopScriptActionHandler`（`@DataTalkAction("datatalk_script_stop")`）：取消运行
- [x] 5.3 创建 `ListScriptRunsActionHandler`（`@DataTalkAction("datatalk_script_list")`）：查询历史
- [x] 5.4 创建 `ScriptController` REST controller：`POST /api/script/run-prepare`、`POST /api/script/{runId}/complete`
- [x] 5.5 创建 `ScriptDataController` REST controller：`POST /api/script-data/write`、`POST /api/script-data/batch`、`POST /api/script-data/batch/close`、`GET /api/script-data/schema/{table}`
- [x] 5.6 更新 `OpenCodeGatewayBeans`：新增 `data-collection` skill sync 调用
- [x] 5.7 **验证**: `mvn compile -q`

## 6. Tauri 脚本执行引擎

- [x] 6.1 在 `lib.rs` 中实现 `run_script` Tauri command：写临时文件 → spawn 子进程 → 流式 stdout/stderr → Tauri event 回传
- [x] 6.2 在 `lib.rs` 中实现 `stop_script` Tauri command：发送 SIGTERM（5s timeout → SIGKILL）
- [x] 6.3 在 `lib.rs` 中实现 `detect_script_env` Tauri command：检测 PATH 中 python3/node 版本
- [x] 6.4 更新 `capabilities/default.json`：添加 shell 权限
- [x] 6.5 创建 TypeScript wrapper `client/src/services/tauri/script-runner.ts`：封装 invoke calls + event listeners
- [x] 6.6 **验证**: `cd client/src-tauri && cargo check`

## 7. 前端 Script Editor Store

- [x] 7.1 安装 xterm.js 依赖：`@xterm/xterm` + `@xterm/addon-fit`
- [x] 7.2 创建 `client/src/features/script/stores/script-workbench-store.ts`：per-tab state（scriptText, version, language, executeStatus, consoleOutput, connectionId, envInfo）+ actions
- [x] 7.3 创建 `client/src/features/script/api/script-api.ts`：HTTP API client（run-prepare, complete, write, batch）
- [x] 7.4 创建 `client/src/features/script/hooks/use-script-execute.ts`：编排 run-prepare → Tauri run_script → complete 流程
- [x] 7.5 **验证**: `npx tsc --noEmit`

## 8. 前端 Script Editor UI

- [x] 8.1 创建 `client/src/features/script/components/script-editor-tab.tsx`：Monaco 编辑器 + 工具栏 + 控制台面板布局
  - Design Inputs: 编辑器区域使用 `bg.canvas`；工具栏按钮遵循 five-state 模式（default/hover/active/focus/disabled）
- [x] 8.2 创建 `client/src/features/script/components/script-monaco-editor.tsx`：Monaco wrapper，支持 Python/JS language mode 切换
- [x] 8.3 创建 `client/src/features/script/components/script-console-panel.tsx`：xterm.js 控制台，ANSI 色彩映射到 DESIGN.md semantic tokens，dark/light 主题切换
  - Design Inputs: 控制台背景 `bg.canvas`；ANSI Red→status.error, Green→status.success, Yellow→status.warning, Blue→accent.primary
- [x] 8.4 创建 `client/src/features/script/components/script-toolbar.tsx`：Run/Stop 按钮 + 语言选择器 + 连接选择器
  - Design Inputs: Run 按钮 `accent.primary`；Stop 按钮 `status.error` variant；disabled 状态 opacity 降低
- [x] 8.5 创建 `client/src/features/script/components/script-env-guide.tsx`：环境检测不可用时的安装引导面板
- [x] 8.6 注册 `script_editor` tab 类型到 `tab-type-registry.ts`：type=`script_editor`, persistent=true, scope=`workspace`, icon=TerminalIcon
- [x] 8.7 **验证**: `npx tsc --noEmit`

## 9. Skills & AI 路由

- [x] 9.1 创建 `server/data-talk-adapter/src/main/resources/skills/data-collection/SKILL.md`：frontmatter（name + description 中英双语触发词）+ tool surface + orchestration sequence + error handling + output protocol
- [x] 9.2 创建 `skills/data-collection/recipes/python-rest-fetch.md`：Python requests 示例 + 调后端 write API
- [x] 9.3 创建 `skills/data-collection/recipes/node-fetch-scrape.md`：Node.js fetch 示例 + 调后端 write API
- [x] 9.4 创建 `skills/data-collection/recipes/python-streaming-write.md`：流式写入分批示例
- [x] 9.5 更新 AGENTS.md Registered Actions 表：新增 `datatalk_script_run`、`datatalk_script_stop`、`datatalk_script_list`，删除旧 6 个 ingestion tools
- [x] 9.6 更新 AGENTS.md Trigger Gate：新增 script runner 路由行，删除 `skill:data-ingestion` 行
- [x] 9.7 更新 AGENTS.md Skill Index：替换 `data-ingestion` 为 `data-collection`
- [x] 9.8 更新 AGENTS.md Intent Routing Gate：新增脚本采集相关 intent 路由
- [x] 9.9 **验证**: 骨架行数 ≤ 350；grep `skill:data-ingestion` 零命中；`skill:data-collection` 命中 ≥ 1

## 10. 集成验证

- [x] 10.1 后端全量测试：`cd server && mvn verify`（修复所有因 ingestion 删除导致的测试编译/运行失败）
- [x] 10.2 前端全量测试：`cd client && npx tsc --noEmit && npx vitest run`
- [x] 10.3 端到端 smoke：启动后端 + 前端，打开 script_editor tab，运行简单 Python 脚本（print hello world），验证控制台输出
- [x] 10.4 更新 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`：记录 script-data-write 支持的数据库类型和兼容性
