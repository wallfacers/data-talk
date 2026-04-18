# 执行计划跟踪器

所有执行计划的集中索引。计划是一等工件，进行版本控制。

## 活跃计划

| 计划 | 状态 | 摘要 |
|------|------|------|
| [Composer Model Picker Dialog](./2026-04-18-composer-model-picker-dialog-plan.md) | 计划中 | Composer 模型选择器 Popover → 960×540 双栏对话框，触发按钮样式不变 |

## 已完成计划

| 计划 | 完成日期 | 摘要 |
|------|---------|------|
| [OpenCode Session ID Persistence + Cascade Delete](./2026-04-18-opencode-session-id-persistence-plan.md) | 2026-04-18 | dtSid↔ocSid 绑定从内存 map 提升为 SQLite 持久化 + 启动预热；删 session 级联调 OpenCode `DELETE /session/:id`；修后端重启后 AI 多轮失忆 |
| [Request Logging & Tracing](./2026-04-18-request-logging-plan.md) | 2026-04-18 | HTTP 请求耗时统计、traceId 全链路日志、慢请求 WARN 告警（HandlerInterceptor + MDC） |
| [OpenCode 1.4.7 Outbound Schema Fix](./2026-04-18-opencode-147-outbound-schema-plan.md) | 2026-04-18 | DataTalk→OpenCode 出站 body 规范化：model 拆 `{providerID, modelID}`、part.id 加 `prt_` 前缀、`synthetic`/`ignored`/`time` 补默认 |
| [OpenCode 1.4.7 Envelope Adapter](./2026-04-18-opencode-147-envelope-adapter-plan.md) | 2026-04-18 | 适配 1.4.7 事件 envelope（payload 在 `properties.*` 下），AI 响应事件重新进 SessionBus；新增 9 份真实 fixture + 11 条 parse 单测 |
| [Message Parts Jackson Fix](./2026-04-18-message-parts-jackson-fix-plan.md) | 2026-04-18 | 修 `MessageRepository.save` 泛型擦除丢 `@JsonTypeInfo` 判别符，GET `/messages` 500 的根因 |
| [Session Canvas UX Fixes](./2026-04-18-session-canvas-ux-fixes-plan.md) | 2026-04-18 | 4 个前端 bug：composer 消失/位置/持久化/气泡样式（SplitView 底部 slot + session-store persist + meta-gated 渲染 + 错误 toast） |
| [API Prefix Decouple](./2026-04-18-api-prefix-decouple-plan.md) | 2026-04-18 | `VITE_API_BASE_URL` 只存 origin，前端集中管理 `/api` 前缀常量，防止 `/api/api` 重复 |
| [Connection Test Status Persistence](./2026-04-18-connection-test-status-persistence-plan.md) | 2026-04-18 | 持久化连接测试结果到 DB，重启后可见上次测试状态 |
| [Drop Connection Gate + Default Model](./2026-04-18-drop-connection-gate-default-model-plan.md) | 2026-04-18 | 去掉发消息对 DB 连接的依赖 + 自动选择首个可用模型 |
| [OpenCode Session Title Sync](./2026-04-18-opencode-session-title-sync-plan.md) | 2026-04-18 | 完整 session.* 事件家族翻译 + title 自动同步 + title_locked 锁定机制 |
| [Logging Standardization](./2026-04-18-logging-standardization-plan.md) | 2026-04-18 | logback-spring.xml 配置、System.err 修复、文件日志滚动输出 |
| [TD-006 API Types Sync](./2026-04-18-td006-api-types-sync-plan.md) | 2026-04-18 | 后端 DTO 统一提取 + SpringDoc OpenAPI + 前端类型生成 + 字段名统一 |
| [AI Settings · Part 2 · Frontend](./2026-04-17-ai-settings-part2-frontend.md) | 2026-04-17 | 设置中心 UI + 对话框 + Chat 模型选择器 |
| [AI Settings · Part 1 · Backend](./2026-04-17-ai-settings-part1-backend.md) | 2026-04-17 | OpenCode 代理 + 偏好持久化 + 连接 CRUD |
| [Plan B: MVP Actions](./2026-04-16-manus-b-mvp-actions.md) | 2026-04-17 | 6 个 MVP Action Handler + 真实 OpenCode SSE 集成 |
| [Plan C: Client Split View](./2026-04-16-manus-c-client-split-view.md) | 2026-04-17 | Manus 风格前端分屏交互、HERO→SPLIT 动画、工件时间线 |
| [Plan C2: Client Wiring Fixes](./2026-04-16-manus-c2-client-wiring-fixes.md) | 2026-04-17 | 合并 session store、补 sessions 端点、portal race、历史加载、SSE 常驻 |
| [Model Config Page](./2026-04-16-model-config-page-plan.md) | 2026-04-17 | 模型配置页面：提供商管理 / 模型可见性 / 自定义提供商（Mock 数据） |
| [OpenCode Embedded Process](./2026-04-16-opencode-embedded-process-plan.md) | 2026-04-17 | Spring Boot 嵌入管理 OpenCode 进程：自动下载 / 动态端口 / 生命周期 |
| [Stage Reveal Animation](./2026-04-17-stage-reveal-animation-plan.md) | 2026-04-17 | Stage 气泡式 clip-path 开/关动画 + 圆角内 bg-muted 色差修复 |
| [Stage As Computer](./2026-04-17-stage-as-computer-plan.md) | 2026-04-17 | 右栏外壳化（macOS 风格 titlebar）+ 小电脑按钮可关可开 + 智能自弹 + 删 /preview |
| [Plan A: Backend Platform (Part 1)](./2026-04-16-manus-a-backend-platform.md) | 2026-04-16 | Ontology/Action Registry, Domain 模型 |
| [Plan A Part 2](./2026-04-16-manus-a-backend-platform-part2.md) | 2026-04-16 | SessionBus, ChannelService, JSON-RPC |
| [Plan A Part 2→3 Adapter](./2026-04-16-manus-a-backend-platform-part2-to-part3-adapter.md) | 2026-04-16 | 适配层集成桥接 |
| [Plan A Part 3](./2026-04-16-manus-a-backend-platform-part3.md) | 2026-04-16 | Flyway SQLite, 持久化仓储 |
| [Plan A Part 4](./2026-04-16-manus-a-backend-platform-part4.md) | 2026-04-16 | OpenCode Gateway, ToolCallBridge, E2E Smoke Test |
| [Client Rebuild](./2026-04-16-client-rebuild-tauri-vite-plan.md) | 2026-04-16 | Tauri v2 + React 19 + Vite 客户端骨架 |
| [Tech Debt Tracker](./tech-debt-tracker.md) | — | 已知技术债务集中记录（P0/P1/P2 优先级） |
| [UI Demo Stage Animation Debt](./ui-demo-stage-animation-debt.md) | 2026-04-17 | Demo 预览模式与 Stage 滑动动画技术债 |

## 工作流

1. 设计 spec 经评审通过后，创建执行计划
2. 计划提交到 `docs/exec-plans/` 并在本文件中登记
3. 执行过程中在计划文件内用 checkbox 标记进度
4. 完成后从「活跃」移到「已完成」
5. 发现的技术债务记录到 [tech-debt-tracker.md](tech-debt-tracker.md)
