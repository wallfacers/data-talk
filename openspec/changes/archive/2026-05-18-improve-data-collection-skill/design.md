## Context

当前 `data-collection` skill 和脚本运行器基础设施是提交 `2b64d808` 后替代旧 Java ingestion 管线的架构。脚本运行器已具备基本功能：script_editor tab、Monaco 编辑器、xterm.js 控制台、Tauri 子进程执行、REST 写入 API。但存在数个缺陷和质量缺口需要补齐。

**当前架构**：
- 用户/AI 在 script_editor tab 编辑 Python/JS 脚本
- Tauri spawn 子进程运行脚本，stdout/stderr 流式回传
- 脚本通过 `POST /api/script-data/write` 将数据写入目标数据库
- AI 通过 `datatalk_script_run` / `datatalk_script_stop` / `datatalk_script_list` action 编排

## Goals / Non-Goals

**Goals:**
- 修复 2 个阻塞性缺陷：i18n action 描述缺失、UI Stop 不联动后端
- 补齐后端和前端测试覆盖
- xterm.js 控制台适配 semantic token 暗/亮主题切换
- 补充 data-collection skill 常用数据采集配方
- 清理 CLAUDE.md 幽灵引用和残留文件

**Non-Goals:**
- 不恢复旧的 Java ingestion HTTP 管线
- 不新增 MCP action（复用现有 `datatalk_script_*` 三个 action）
- 不在前端新增脚本编辑器之外的 UI 组件
- 不改造 batch write 的 ConcurrentHashMap 为持久化存储（低优先级，不影响核心功能）

## Decisions

### 1. UI Stop 修复方案：前端直接调用 runComplete

**方案**：`useScriptExecute.stop()` 中，在 `tauriStopScript` 后直接调用 `scriptApi.runComplete(runId, { exitCode: -1, stdoutText })`。

**替代方案**：Tauri `stop_script` Rust 命令中调用后端 — 拒绝了，因为 Tauri Rust 侧不应耦合 HTTP 客户端逻辑。

**理由**：前端已有 `runComplete` 调用路径（在 `onScriptCompleted` 回调中），复用即可。停止时 exitCode 使用 SIGTERM 约定值 143（128+15）或 -1。

### 2. xterm.js 主题方案：CSS 变量注入 + useTheme hook

**方案**：`ScriptConsolePanel` 通过已有的 theme hook 读取当前 light/dark 状态，构建 xterm.js `ITerminalOptions.theme` 对象，使用 `client/DESIGN.md` semantic tokens：
- Dark: background=`neutral.900`, foreground=`neutral.100`, cursor=`neutral.400`, selectionBackground=`neutral.800`
- Light: background=`neutral.25`, foreground=`neutral.800`, cursor=`neutral.500`, selectionBackground=`neutral.100`
- ANSI colors 按照 spec 要求的映射关系（Red→status.error, Green→status.success, Yellow→status.warning, Blue→accent.primary, Cyan→chart-cyan, Magenta→chart-pink）

**替代方案**：CSS class 切换 — 拒绝了，因为 xterm.js 使用 Canvas 渲染，不受 CSS 变量影响，必须通过 JS API 设置 theme。

**Token 映射**：DESIGN.md 使用 color name（如 `cobalt.700`），实际渲染时通过 CSS 变量 `var(--color-*)` 注入。xterm.js theme 需要硬编码 hex 值，从 DESIGN.md 和 Tailwind config 读取对应 hex。

### 3. i18n 密钥方案：在 messages.properties 中新增 3 个 key

```properties
action.script_run.description=Prepare and execute a Python/Node.js script with environment validation
action.script_stop.description=Cancel a running script
action.script_list.description=List script run history for a connection
```

无需新增 Java 枚举或常量类，因为 `@DataTalkAction` 注解的 description 已经通过 `MessageSource` 从 `action.{key}.description` 读取。

### 4. targetTable 回写方案：首次 write 调用时更新

`ScriptDataController.write()` 在成功写入后，调用 `ScriptRunService.updateTargetTable(token.runId, tableName)`。仅在 `targetTable == null` 时更新（避免重复写入）。

### 5. 配方组织方案：按数据源类型分类

在 `data-collection/recipes/` 下按以下结构补充：
- `python-rest-pagination.md` — 分页遍历 REST API（page/offset/cursor）
- `python-html-parse.md` — BeautifulSoup HTML 解析
- `python-csv-parse.md` — csv 模块读取 CSV 文件写入数据库
- `python-auth-patterns.md` — Bearer/API Key/Basic Auth 认证模式
- `python-stream-download.md` — 大文件流式下载+分批写入

每个配方保持 ~50-80 行，包含完整可运行代码模板和环境变量说明。

## Risks / Trade-offs

- [前端 Stop 竞态] 用户点击 Stop 后 SIGTERM 到进程退出有时间窗口，前端先调用 `runComplete` 可能与实际退出状态不一致 → 使用 exitCode=-1 标记主动停止，后端按 CANCELLED 处理
- [xterm.js 主题切换闪烁] terminal.dispose() + 重建可能导致闪烁 → 使用 `term.options.theme = newTheme` 而非重建实例
- [ANSI 颜色映射] 硬编码 hex 值意味着如果 DESIGN.md token 变更，需同步更新 → 风险较低，DESIGN.md token 稳定且变更频率极低

## Migration Plan

1. 所有改动为叠加式（新增 i18n key、新增配方文件、新增测试），无需 Flyway 迁移
2. CLAUDE.md 和残留文件清理为纯删除操作，无运行依赖
3. 部署后首次使用即可生效，无需数据迁移
