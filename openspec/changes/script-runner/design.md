## Context

DataTalk 当前数据采集模块 (ingestion) 基于 HTTP fetch → schema inference → mapping → DDL → batch INSERT 的固定流水线，涵盖 domain/application/infrastructure/adapter 四层共 ~40 个 Java 文件、~15 个前端文件、6 个 MCP tools、1 个 skill（含 5 recipes + 3 examples）、4 个 Flyway 迁移。该模块无法应对登录态采集、反爬策略、动态渲染、多步骤抽取等复杂场景。

DtEvent sealed interface 包含 8 个 ingestion 相关事件类型（`IngestionJobCreated` 到 `IngestionFailed`），exhaustive switch 位于 `DtEvent.typeName()`。agent-skill-routing spec 中 `data-ingestion` skill 被 Trigger Gate 和 Skill Index 引用，`OpenCodeGatewayBeans` 中注册了 `data-ingestion` skill sync。

Tauri v2 已安装 `tauri-plugin-shell`（Rust 端 `Cargo.toml` + `lib.rs` 已初始化），但未暴露任何 shell 执行命令。前端 Monaco 编辑器已用于 SQL 编辑器，xterm.js 需新增。

## Goals / Non-Goals

**Goals:**

- 用 Python/Node.js 脚本运行器替代固定 ingestion 流水线，支持任意采集逻辑
- 脚本在用户本地执行（Tauri spawn），通过后端 REST API 写入数据到已连接数据库
- 支持 AI 多轮对话编写/修改脚本（apply_text_edits 模式，与 SQL editor 一致）
- 支持批量写入和流式写入两种数据粒度
- 控制台输出体验接近 IDEA Run Console（xterm.js + ANSI 色彩 + 主题适配）
- 完整记录每次运行的脚本内容、输出、状态、耗时

**Non-Goals:**

- 不提供沙箱隔离——脚本在用户本地运行，安全责任由用户承担
- 不内置 Python/Node.js 运行时——依赖用户本地环境，仅提供环境检测和安装引导
- 不支持浏览器渲染（Puppeteer/Playwright）——留给后续增强
- 不做 Docker 容器化执行环境
- 不做脚本版本管理（Git 级别）——只保留运行记录

## Decisions

### D1: 脚本执行位置 — Tauri 本地子进程

**选择**: Tauri Rust 端 spawn 子进程，运行用户本地 Python/Node.js。

**替代方案**: Server 端 ProcessBuilder 执行。缺点：(1) 服务器 IP 易被目标网站封禁；(2) 需要沙箱隔离；(3) 数据经服务器中转增加延迟。

**理由**: 逆向脚本通常需要直连目标网站，本地执行避免 IP 封锁；`tauri-plugin-shell` 已安装，只需加 command；与 IDEA 本地运行代码体验一致。

**实现**: Rust `run_script` Tauri command → 写临时文件 → `Command::new(python3/node)` → stdout/stderr 通过 Tauri event 流式回传前端。

### D2: 数据写入 — 后端 REST API 协议

**选择**: 脚本通过 HTTP 调用后端 REST API 写入数据，不直连数据库。

**替代方案**: 脚本直连数据库。缺点：(1) 需要将 DB 密码暴露给脚本；(2) 脚本需安装 DB driver；(3) 无法复用后端连接池和 DDL 逻辑。

**理由**: 复用现有 datasource 连接池和 DDL 推断逻辑；脚本只需 `requests`/`fetch`，零额外依赖；后端控制写入权限和速率。

**API 设计**:
- `POST /api/script-data/write` — 单批写入，自动建表
- `POST /api/script-data/batch` — 多批流式写入（保持写入 session）
- `GET /api/script-data/schema/{table}` — 查询目标表 schema

### D3: 认证 — ScriptToken 一次性令牌

**选择**: 用户点击"运行"时后端签发 UUID 令牌（TTL=10min，一次性），通过环境变量 `DT_SCRIPT_TOKEN` 注入子进程。脚本用此令牌调后端 API。

**替代方案**: JWT / Session cookie。过于复杂，脚本在子进程中无浏览器上下文。

**理由**: 简单、无状态、与 ingestion 的 ConfirmedToken 模式一致。令牌绑定 connectionId 和 runId，后端可校验权限。

### D4: 控制台 — xterm.js + ANSI 色彩 + 主题适配

**选择**: 使用 xterm.js 渲染控制台输出，解析 ANSI escape codes，颜色映射到 DESIGN.md semantic tokens。

**替代方案**: 自定义 `<pre>` + `ansi-to-html`。缺点：无 scrollback、无选中复制、终端特性不完整。

**理由**: xterm.js 提供完整终端体验（scrollback、选中、复制粘贴），Python `colorama`/`rich` 输出直接渲染。主题通过 `xterm.theme` API 注入，跟随 app dark/light 主题切换。

**颜色映射**: ANSI Red → `--destructive` / `status.error`; ANSI Green → `status.success`; ANSI Yellow → `status.warning`; ANSI Blue → `accent.primary`; ANSI Cyan → `chart-cyan`; ANSI Magenta → `--chart-pink`。

### D5: 前端状态 — script-workbench-store (Zustand)

**选择**: 新建 `script-workbench-store.ts`，模式对齐 `sql-workbench-store.ts`。

**理由**: SQL editor 已验证的架构——per-tab state、version conflict detection、execution state machine。script editor 复用相同模式，但 execution state 简化（`idle` | `running` | `success` | `error`，无需 confirmation flow）。

### D6: DtEvent 变更

**选择**: 删除 8 个 ingestion 事件类型，新增 3 个 script 事件类型：
- `ScriptRunStarted(runId, language, connectionId)`
- `ScriptRunOutput(runId, output, channel)` — channel: stdout/stderr
- `ScriptRunCompleted(runId, exitCode, durationMs, rowsWritten)`

**理由**: DtEvent 是 sealed interface，所有 subtype 变更影响 `typeName()` exhaustive switch。只改 domain 层一处，无外部 exhaustive switch。`DtEventTypeIdResolver` 通过反射自动发现 permitted subclasses，新增 subtype 无需手动注册。

### D7: Tab 类型 — `script_editor`

**选择**: 新增 `script_editor` tab 类型，注册到 `tab-type-registry.ts`。scope: `workspace`，persistent: true，payloadSource: `stage_tab`。

**理由**: 与 `query_editor` 平级，全局持久化，不受 session 切换影响。AI 通过 `ui_patch`/`ui_exec` 操作脚本内容，与 SQL editor 交互模式完全一致。

### D8: 环境检测

**选择**: Tauri 端 `detect_script_env` command，检测 `python3`/`node` 是否在 PATH 中可用。返回版本号或 null。前端在用户首次打开脚本 tab 或点击运行时调用，不可用则显示安装引导面板。

## Risks / Trade-offs

- **[安全] 用户运行任意代码** → Tauri 本地模式，与 IDEA 运行 Java 等价；UI 明确提示"脚本在本地执行"
- **[环境] 用户未安装 Python/Node** → 环境检测 + 安装引导面板；语言选择器只显示已检测到的语言
- **[迁移] 旧数据全丢** → 用户明确选择不兼容升级；V26 migration DROP 旧表前无需备份
- **[网络] 后端 API 端口可能不固定** → `DT_BACKEND_URL` 从 Tauri 侧注入，前端知道当前后端地址
- **[性能] 大数据量流式写入** → 批量 INSERT + virtual thread，与当前 ingestion batch 写入等价
- **[兼容] DtEvent 类型名变更** → 纯 breaking change，前端 SSE handler 同步更新
- **[残留引用] 删除量大可能遗漏** → 全局 `mvn compile` + `tsc --noEmit` + grep 验证

## Migration Plan

1. **Phase 1 — 清理**: 删除所有 ingestion 代码 + Flyway V26 DROP 旧表 + 修复编译
2. **Phase 2 — 后端核心**: Domain (ScriptRun, ScriptLanguage, ScriptStatus) + App (ScriptRunService, ScriptTokenStore, ScriptDataWriteService) + Infra (JdbcScriptRunRepository) + Adapter (ActionHandlers + Controllers)
3. **Phase 3 — Tauri 执行引擎**: Rust run_script command + detect_script_env + 权限配置
4. **Phase 4 — 前端**: script-workbench-store + script-editor-tab + script-console-panel + tab-type-registry
5. **Phase 5 — Skills & AI 路由**: data-collection SKILL.md + AGENTS.md 更新 + ActionHandler 注册

**回滚策略**: V26 migration 可逆（DROP 前备份 DDL），但旧代码完全删除后无法回滚到旧 ingestion——需 git revert 整个 change。

## Open Questions

- **ScriptDataWriteService 的 schema 推断逻辑**：是否复用当前 ingestion 的 TypeInferrer/TabularValueCoercer？还是重新实现简化版？（建议：提取到 domain 层复用）
- **流式写入 session 的生命周期**：何时关闭？超时？显式 close？
- **xterm.js 版本选择**：`@xterm/xterm` v5 还是 `xterm` v4？（建议 v5，新 API）
