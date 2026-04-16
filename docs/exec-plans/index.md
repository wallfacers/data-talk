# 执行计划跟踪器

所有执行计划的集中索引。计划是一等工件，进行版本控制。

## 活跃计划

| 计划 | 阶段 | 状态 | 摘要 |
|------|------|------|------|
| [Plan B: MVP Actions](../superpowers/plans/2026-04-16-manus-b-mvp-actions.md) | 规划 | pending | 6 个 MVP Action Handler: read_schema, execute_sql, render_chart, render_erd, pin_artifact, supersede_artifact |
| [Plan C: Client Split View](../superpowers/plans/2026-04-16-manus-c-client-split-view.md) | 规划 | pending | Manus 风格前端分屏交互、HERO→SPLIT 动画、工件时间线 |
| [Plan C2: Client Wiring Fixes](../superpowers/plans/2026-04-16-manus-c2-client-wiring-fixes.md) | 规划 | pending | 合并重复的 session store、补后端 sessions 端点、portal race、历史加载、SSE 常驻、死代码清理 |

## 已完成计划

| 计划 | 完成日期 | 摘要 |
|------|---------|------|
| [Stage As Computer](../superpowers/plans/2026-04-17-stage-as-computer-plan.md) | 2026-04-17 | 右栏外壳化（macOS 风格 titlebar）+ 小电脑按钮可关可开 + 智能自弹 + 删 /preview |
| [Plan A: Backend Platform (Part 1)](../superpowers/plans/2026-04-16-manus-a-backend-platform.md) | 2026-04-16 | Ontology/Action Registry, Domain 模型 |
| [Plan A Part 2](../superpowers/plans/2026-04-16-manus-a-backend-platform-part2.md) | 2026-04-16 | SessionBus, ChannelService, JSON-RPC |
| [Plan A Part 2→3 Adapter](../superpowers/plans/2026-04-16-manus-a-backend-platform-part2-to-part3-adapter.md) | 2026-04-16 | 适配层集成桥接 |
| [Plan A Part 3](../superpowers/plans/2026-04-16-manus-a-backend-platform-part3.md) | 2026-04-16 | Flyway SQLite, 持久化仓储 |
| [Plan A Part 4](../superpowers/plans/2026-04-16-manus-a-backend-platform-part4.md) | 2026-04-16 | OpenCode Gateway, ToolCallBridge, E2E Smoke Test |
| [Client Rebuild](../superpowers/plans/2026-04-16-client-rebuild-tauri-vite-plan.md) | 2026-04-16 | Tauri v2 + React 19 + Vite 客户端骨架 |

## 工作流

1. 设计 spec 经评审通过后，创建执行计划
2. 计划提交到 `docs/superpowers/plans/` 并在本文件中登记
3. 执行过程中在计划文件内用 checkbox 标记进度
4. 完成后从「活跃」移到「已完成」
5. 发现的技术债务记录到 [tech-debt-tracker.md](tech-debt-tracker.md)
