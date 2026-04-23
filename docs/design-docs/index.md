# 设计文档目录

所有设计文档的集中索引。每份 spec 需标注验证状态。

## 状态说明

| 状态     | 含义                         |
|---------|------------------------------|
| draft   | 初稿，尚未评审                 |
| review  | 评审中                        |
| approved| 已批准，可进入实施              |
| shipped | 已实施完毕并上线               |
| stale   | 已过时，仅作历史参考            |

## 设计文档清单

| 文档 | 状态 | 摘要 |
|------|------|------|
| [query-editor-object-actions-design](../product-specs/2026-04-23-query-editor-object-actions-design.md) | shipped | `query_editor` 已收敛为由 `StageStore` 统一打开、命名、聚焦和编辑的对象；`WorkspaceAdapter` / `QueryEditorAdapter` 已对 AI 暴露稳定的 `state / actions / capabilities` 与文件式 SQL 编辑语义 |
| [datatalk-client-design-system-design](../product-specs/2026-04-23-datatalk-client-design-system-design.md) | shipped | 为 `client/` 建立可执行的设计系统契约，并落地 `client/DESIGN.md`、semantic token 映射与基础工作台表面 |
| [chat-auto-scroll-reentry-design](../product-specs/2026-04-23-chat-auto-scroll-reentry-design.md) | shipped | 修复聊天区 auto-follow 接管条件：用户主动离开底部后，流式更新不再强制滚底，只有重新回到底部才恢复自动跟随 |
| [sql-tab-internal-rail-design](../product-specs/2026-04-23-sql-tab-internal-rail-design.md) | shipped | SQL Tab 内置 rail 已完成收口：`StageWindow` 顶层 Activity Rail 下移到 `SqlWorkbenchTab`，并完成自动化回归与手动视觉 smoke checklist；非 SQL Tab 不再显示 rail |
| [read-file-preview-design](../product-specs/2026-04-22-read-file-preview-design.md) | shipped | 为 `read` 工具文件输出增加专属前端渲染与“同步到工作台”入口，在当前会话 Stage 中创建或聚焦 `file_preview` Tab |
| [stage-sql-editor-format-design](../product-specs/2026-04-22-stage-sql-editor-format-design.md) | shipped | 为 Stage Query Editor 增加统一 SQL 格式化能力，按钮与快捷键共用同一前端格式化链路 |
| [workspace-i18n-design](../product-specs/2026-04-22-workspace-i18n-design.md) | shipped | 补齐工作台相关前端文案与后端静态元数据、结果标题、关键错误的双语 i18n |
| [stage-sql-workbench-polish-design](../product-specs/2026-04-22-stage-sql-workbench-polish-design.md) | shipped | 在 Stage SQL Workbench Rebuild 之上完成 IDE 级打磨：右侧 Activity Rail、工具栏、状态栏、breadcrumb 与 AI Assist 会话隔离 |
| [postgres-sql-splitter-design](../product-specs/2026-04-22-postgres-sql-splitter-design.md) | shipped | 为 `/api/sql/execute` 的 PostgreSQL 多语句执行引入方言化 splitter 边界与 PgJDBC parser 首版实现 |
| [stage-sql-workbench-rebuild-design](../product-specs/2026-04-21-stage-sql-workbench-rebuild-design.md) | shipped | Stage SQL 主线彻底重做：前端迁移 Monaco + 编辑器工作区 + 结果集 Tab，后端 `/api/sql/execute` 升级为多语句 / 多结果契约 |
| [stage-window-sql-workbench-design](../product-specs/2026-04-21-stage-window-sql-workbench-design.md) | shipped | 将 Stage 升级为基于 shadcn/ui 的多面板 SQL 工作台，`query_editor` 成为唯一 SQL 工作页 |
| [stage-window-layout-refactor-design](../product-specs/2026-04-21-stage-window-layout-refactor-design.md) | shipped | Stage 重构为左侧导航侧栏 + 右侧 Tabs 工作区的浏览器式工作台：顶部轻量工具行、下方连接资源浏览器、Chrome-inspired 顶部页签，并移除底部 Dock |
| [session-data-context-and-ai-datasource-management-design](../product-specs/2026-04-21-session-data-context-and-ai-datasource-management-design.md) | shipped | 建立 session 级 `connectionId + database + schema` 统一上下文，统一 `use xxx` / `!sql` / AI / Stage 的解析链路，并补齐 AI 数据源管理边界 |
| [bang-query-badge-minimization-design](../product-specs/2026-04-21-bang-query-badge-minimization-design.md) | shipped | 将 bang-query 用户气泡的显式文字 badge 收敛为低存在感图标，减少视觉打扰 |
| [stage-query-editor-design](../product-specs/2026-04-21-stage-query-editor-design.md) | shipped | Stage 接通 store，并新增 Query Editor tab、SQL 编辑执行与结果面板 |
| [bang-query-chat-visibility-design](../product-specs/2026-04-21-bang-query-chat-visibility-design.md) | shipped | 为 `!select` / `!with` 直查补齐聊天区可见性、持久化与历史合并排序 |
| [ai-message-table-actions-and-structured-format-design](../product-specs/2026-04-20-ai-message-table-actions-and-structured-format-design.md) | shipped | 为 AI 消息表格补齐复制 / 导出动作栏与结构化格式输出 |
| [composer-data-source-picker-design](../product-specs/2026-04-20-composer-data-source-picker-design.md) | approved | Composer 增加与模型并列的数据源选择器，并接入 chooser host 与来源数据源固化 |
| [ai-message-code-window-and-table-design](../product-specs/2026-04-20-ai-message-code-window-and-table-design.md) | shipped | 统一 AI 消息代码块窗体视觉与 Markdown 表格样式增强 |
| [stage-ui-object-protocol-design](../product-specs/2026-04-20-stage-ui-object-protocol-design.md) | shipped | Stage UI 对象协议 P1 已收口：`UIRouter`、4 个 CLIENT Action 桥接、`StageStore` 多 Tab、`workspace/query_editor` 对象面与 Composer `!` direct SQL 主链路均已落地 |
| [blank-session-list-actions-design](../product-specs/2026-04-20-blank-session-list-actions-design.md) | shipped | 空白会话继续保留，但不再暴露更多操作，避免无意义管理动作 |
| [sql-risk-classification-and-it-ci-gate-design](../product-specs/2026-04-20-sql-risk-classification-and-it-ci-gate-design.md) | shipped | SQL AST 风险判级与 IT gate 已落地并完成收口：2026-04-23 复跑 `mvn compile -q`、application 定向单测与 adapter `verify` 均通过，`*IT.java` 已纳入门禁 |
| [sse-heartbeat-design](../product-specs/2026-04-20-sse-heartbeat-design.md) | shipped | SSE GET 订阅改为无限 timeout + 心跳探活，并降低超时噪音日志 |
| [assistant-model-metadata-propagation-design](../product-specs/2026-04-20-assistant-model-metadata-propagation-design.md) | shipped | assistant provider/model 元数据全链路透传，并清理过时消息字段兼容链 |
| [client-rebuild-tauri-vite-design](../product-specs/2026-04-16-client-rebuild-tauri-vite-design.md) | shipped | Tauri v2 + React 19 + Vite 客户端重建方案 |
| [manus-split-view-design](../product-specs/2026-04-16-manus-split-view-design.md) | shipped | Manus 风格分屏交互 + Action Registry + Ontology 层 |
| [model-config-page-design](../product-specs/2026-04-16-model-config-page-design.md) | shipped | 模型配置页面：提供商管理 / 模型可见性 / 自定义提供商 |
| [opencode-embedded-process-design](../product-specs/2026-04-16-opencode-embedded-process-design.md) | shipped | Spring Boot 嵌入管理 OpenCode 进程：自动下载 / 动态端口 / 生命周期 |
| [stage-as-computer-design](../product-specs/2026-04-17-stage-as-computer-design.md) | shipped | Stage 外壳化（macOS titlebar）+ 可关可开 + 智能自弹 + 删 /preview |
| [stage-reveal-animation-design](../product-specs/2026-04-17-stage-reveal-animation-design.md) | shipped | Stage 气泡式开/关动画（clip-path circle）+ 圆角内 bg-muted 色差 |
| [ai-settings-opencode-port](../product-specs/2026-04-17-ai-settings-opencode-port.md) | shipped | AI 设置中心：数据源 / 提供商 / 模型三页，对齐 OpenCode Desktop |

状态同步说明：本次按文档成熟度与执行计划状态治理，粗稿 / memo 记为 `draft`，已有活跃执行计划记为 `approved`，对应计划已完成记为 `shipped`。2026-04-23 已补齐最近新增的 4/21-4/23 design doc 条目，并修正已完成计划对应的状态漂移。

## 核心理念

见 [core-beliefs.md](core-beliefs.md) — 定义了本项目的智能体优先操作原则。

## 新增设计文档

1. 在 `docs/product-specs/` 中创建 `YYYY-MM-DD-<topic>-design.md`
2. 在本文件中添加索引条目
3. 标注初始状态为 `draft`
4. 经评审后更新状态
