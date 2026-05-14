## Why

当前数据采集模块 (ingestion) 采用固定的 HTTP fetch → schema inference → mapping → DDL → batch INSERT 流程，仅支持 REST/CSV/HTML 等简单数据源，无法应对需要登录态、反爬、动态渲染、多步骤抽取的复杂采集场景。用户需要一个灵活的脚本运行器，可以用 Python/Node.js 编写任意采集逻辑，通过后端 API 将数据写入已连接的数据库——类似于 IDEA 运行 Java 代码的体验。

## What Changes

- **BREAKING**: 完全删除现有 ingestion 模块（domain/application/infrastructure/adapter 四层 + client 前端 + skills + Flyway 表），不兼容升级
- **新增 Script Runner 后端**: 脚本运行管理服务，签发一次性令牌，提供 REST API 供脚本调用来写入数据（批量 + 流式两种粒度）
- **新增 Script Runner 前端**: 新 `script_editor` tab 类型，Monaco 代码编辑器（Python/JS 语法高亮），xterm.js 控制台输出面板（ANSI 色彩 + 主题适配），支持 AI 多轮对话修改代码
- **新增 Tauri 脚本执行**: Rust 端 spawn 子进程运行 Python/Node.js 脚本，stdout/stderr 流式回传
- **新增 execution history**: `script_run` 表记录每次运行的脚本内容、语言、状态、输出、耗时、写入行数等
- **替换 AGENTS.md & Skills**: 删除 6 个 ingestion MCP tools → 新增 3 个 script tools + 1 个 data-write tool；替换 `data-ingestion` skill → `data-collection` skill
- **环境检测**: 首次运行时自动检测用户本地 Python/Node.js 环境，不可用时给出安装引导

## Capabilities

### New Capabilities

- `script-execution`: 脚本生命周期管理——创建、编辑、运行、停止、历史记录；Tauri 本地子进程执行；stdout/stderr 流式输出；ScriptToken 签发与验证
- `script-editor-tab`: 前端 `script_editor` tab——Monaco 代码编辑器 + xterm.js 控制台面板 + 运行/停止/格式化工具栏；AI 对话式多轮代码编辑（apply_text_edits）；Zustand store 状态管理
- `script-data-write`: 脚本通过后端 REST API 写入数据——批量写入（一次全量）和流式写入（分批增量）两种粒度；自动建表；schema 推断；复用现有 datasource 连接

### Modified Capabilities

- `agent-skill-routing`: 删除 ingestion 相关 6 个 tool 路由，新增 script runner 相关 tool 路由和 trigger gate 规则

## Impact

### 代码删除
- `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/` — 全部 14 个文件
- `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/` — 全部 16 个文件
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/ingestion/` — 全部 4 个文件
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ingestion/` — 全部 6 个 ActionHandler
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/IngestionController.java`
- `client/src/features/ingestion/` — 全部 15+ 文件
- `server/data-talk-adapter/src/main/resources/skills/data-ingestion/` — SKILL.md + 5 recipes + 3 examples
- 相关测试文件（25+）

### 代码新增
- Backend: `domain/script/`, `app/script/`, `infra/script/`, `adapter/actions/script/`, `adapter/ScriptController`, `adapter/ScriptDataController`
- Frontend: `client/src/features/script/` (editor, console, store, api, hooks)
- Tauri: Rust `run_script` command + shell permissions
- Flyway V26 migration: DROP 旧 ingestion 表 + CREATE script_run 表
- Skills: `data-collection/` SKILL.md + recipes

### API 变更
- **BREAKING**: 删除 6 个 ingestion MCP tools (`datatalk_http_request`, `datatalk_infer_ingestion_schema`, `datatalk_create_ingestion_table`, `datatalk_ingest_payload`, `datatalk_get_ingestion_job`, `datatalk_list_ingestion_jobs`)
- **BREAKING**: 删除 ingestion REST endpoints (`/api/ingestion/*`)
- 新增 MCP tools: `datatalk_script_run`, `datatalk_script_stop`, `datatalk_script_list`
- 新增 REST endpoints: `/api/script/*`, `/api/script-data/write`, `/api/script-data/batch`

### 依赖
- 新增 npm 依赖: `@xterm/xterm`, `@xterm/addon-fit`
- 新增 npm 依赖: `monaco` Python/JavaScript language support（已通过现有 Monaco 集成可用）
- Tauri: `tauri-plugin-shell` 已安装，需配置权限

### Design Inputs (client/DESIGN.md)
- Console 面板使用 `bg.canvas` 作为工作内容背景，与 Monaco 编辑器一致
- ANSI 色彩映射到 DESIGN.md 定义的 semantic tokens: red → `status.error`, green → `status.success`, yellow → `status.warning`, blue → `accent.primary`
- 工具栏 Run/Stop 按钮遵循 five-state 交互模式 (default/hover/active/focus/disabled)
- Tab 类型注册到 `tab-type-registry.ts`，scope 为 `workspace`
- Stage state 为全局、非 per-session

### 已知风险
- `docs/bugs/` 中有 22 个 ingestion 相关 BUG（全部 fixed），删除旧模块后这些 BUG 记录成为历史
- `DtEvent` sealed interface 包含 ingestion 相关 event types，删除后需同步更新 exhaustive switches
- 用户本地可能未安装 Python/Node.js，需要友好的环境检测和引导
