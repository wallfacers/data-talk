## Why

Task 10（外部数据采集）原本基于 Java HTTP 抓取管线的完整实现已被提交 `2b64d808` 删除，架构替换为 Python/Node.js 脚本运行器 + `data-collection` skill。当前基础设施功能可用但存在 2 个阻塞性缺陷（i18n 缺失导致 AI 无法正确理解 action、UI 停止按钮不同步后端状态）和多个体验/质量缺口，需要系统性补齐才能达到产品就绪状态。

## What Changes

- 补充 `datatalk_script_run`、`datatalk_script_stop`、`datatalk_script_list` 三个 MCP action 的 i18n 描述密钥（中英文）
- 修复 UI Stop 按钮：前端停止脚本后同步调用后端 `runComplete` 更新 `CANCELLED` 状态
- 补齐后端脚本运行器测试覆盖：`ScriptRunService`、`ScriptTokenStore`、`ScriptDataBatchService`、`ScriptRunRepository`、三个 ActionHandler、两个 Controller
- 补齐前端脚本模块测试：`useScriptExecute` hook、`script-workbench-store`、核心组件
- xterm.js 控制台面板适配 semantic token 实现暗/亮主题切换
- 补充 `data-collection` skill 配方：CSV 解析写入、HTML 解析、分页 API 遍历、认证处理、大文件流式下载
- `targetTable` 回写：`ScriptDataController.write()` 首次写入时更新 `script_run.target_table`
- CLAUDE.md 幽灵引用清理：删除已不存在的 Ingestion 相关章节
- 残留 `.opencode/skills/data-ingestion/` 技能包清理

## Capabilities

### New Capabilities

- `data-collection-recipes`: data-collection skill 补充配方（CSV 解析、HTML 解析、分页遍历、认证、大文件下载），提升 AI 脚本生成质量和场景覆盖

### Modified Capabilities

- `script-execution`: 修改"用户手动停止脚本"场景 — UI Stop 按钮 MUST 同步调用后端 `cancelRun`，不再仅依赖 AI 调用的 `StopScriptActionHandler`
- `script-editor-tab`: 修改"主题切换"场景 — xterm.js 控制台 MUST 使用 semantic token（`bg.canvas`、`text.primary`）替代硬编码暗色主题
- `script-data-write`: 新增 targetTable 回写需求 — 首次 write API 调用时 MUST 更新 `script_run.target_table`

## Impact

- Affected code: `ScriptDataController`, `useScriptExecute`, `ScriptConsolePanel`, action i18n keys, CLAUDE.md
- Affected specs: `script-execution`, `script-editor-tab`, `script-data-write`
- No database migration needed (复用现有 `script_run` 表)
- No new dependencies
- No breaking changes
