# SQL 编辑器固定 Session 上下文持久化

## 动机

工作台 SQL 编辑器的"固定 session 上下文"开关关闭后，用户可手动选择连接 / 数据库 / Schema 作为该 Tab 的独立执行上下文。当前这些 override 值仅保存在前端 `useStageStore` 内存中，页面刷新或客户端重启后丢失，恢复为跟随 session 上下文。

用户期望：手动选定的执行上下文与 SQL 正文一样，是 Tab 工作状态的固有部分，应持久化到后端数据库，下次打开时自动恢复到上次的状态。

## 需求

- SQL 编辑器 Tab 的 `contextOverride`（connectionId / database / schema）随 `stage_tab_payload` 一同持久化
- 页面刷新后，Stage 恢复 Tab 时从后端加载 `contextOverride` 并还原到 toolbar 控件
- `stage-persistence-bootstrap.ts` 在 override 变化时触发持久化快照（复用现有 debounce 机制）
- 切换 session 后回到同一 Tab，override 不变（Stage 全局态不变性）

## 影响范围

- 后端：`stage_tab_payload` 表 `content` JSON 字段需包含 `contextOverride`
- 前端：`stage-persistence-bootstrap.ts` 已有 `contextOverride` 快照逻辑（commit 2026-04-29），需确认端到端闭环
- 无新 Flyway migration
