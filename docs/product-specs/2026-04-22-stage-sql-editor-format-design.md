# Stage SQL Editor Format Design

- **日期**：2026-04-22
- **状态**：shipped
- **前置**：
  - [Stage SQL Workbench Rebuild Design](./2026-04-21-stage-sql-workbench-rebuild-design.md)
  - [Stage SQL Workbench Polish Design](./2026-04-22-stage-sql-workbench-polish-design.md)

## 1. 背景与目标

Stage SQL 编辑器已经有 `Format` 动作入口，但当前缺少统一、稳定的 SQL pretty-format 行为。用户在 Query Editor 里编辑 AI 生成 SQL 或手写多行 SQL 时，需要一个显式触发的格式化入口，让结构层次更清晰，同时不影响执行链路和输入体验。

本次目标只覆盖 **Stage Query Editor**：

1. 提供可用的 SQL 格式化能力
2. 支持工具栏 `Format` 按钮和 `Cmd/Ctrl + Shift + F`
3. 两个入口统一走同一套格式化逻辑
4. 保持显式触发，不做输入时实时格式化

## 2. 范围与非目标

### 2.1 范围

- Stage SQL workbench 编辑器
- 现有 `SqlEditorToolbar` 的 `Format` 动作
- `SqlMonacoEditor` 快捷键注册
- 一个前端 SQL formatting helper
- 对应 vitest 单测

### 2.2 非目标

- 不改聊天消息中的 SQL code block 格式化
- 不改后端 SQL 解析、风险判级、执行契约
- 不做 Monaco command palette / code action 深集成
- 不做输入时自动格式化或保存时自动格式化
- 不引入 AST 级语义重写，只做 whitespace / line-break 层面的 pretty format

## 3. 方案选择

### 3.1 推荐方案

使用前端已安装依赖 `sql-formatter` 作为运行时格式化引擎，新增一个 Stage 内部 helper 负责：

- 方言映射
- 统一 formatter 配置
- 空字符串 / 异常兜底

`SqlWorkbenchTab` 负责触发格式化并回写 store；`SqlEditorToolbar` 和 `SqlMonacoEditor` 只负责发起事件，不持有格式化细节。

### 3.2 不选方案

- 只做按钮，不做快捷键：体验不完整
- 直接把格式化逻辑塞进 `SqlMonacoEditor`：会让编辑器组件承担业务语义
- 引入后端格式化服务：对当前需求过重，也会增加交互延迟

## 4. 交互与行为

### 4.1 触发入口

- 点击 Query Editor 工具栏 `Format`
- 在编辑器聚焦时按 `Cmd/Ctrl + Shift + F`

### 4.2 行为规则

- 格式化对象为当前 tab 的完整 SQL 文本
- 空文本或纯空白文本不做任何变更
- 格式化成功后，用结果覆盖当前编辑器内容
- 若格式化结果与原文本一致，不额外提示
- 若 formatter 抛错，编辑器文本保持不变；当前版本不额外弹 toast，先走静默失败

### 4.3 方言策略

优先从当前 Query Editor 上下文映射 dialect：

- PostgreSQL → `postgresql`
- MySQL → `mysql`
- H2 / unknown → 默认通用 SQL 方言

初版只要求覆盖仓库现有主路径中的常见连接类型；未知方言直接降级到默认 formatter language，不阻塞功能。

### 4.4 输出风格

统一使用一组稳定配置：

- 关键字大写
- 2 空格缩进
- 逻辑运算符换行前置
- 适中的表达式换行宽度

目标是让 `SELECT / FROM / JOIN / WHERE / GROUP BY / ORDER BY` 这些结构层次明显，但不引入额外个性化设置面板。

## 5. 实现设计

### 5.1 文件边界

- 新增 `client/src/features/stage/utils/format-sql.ts`
  - 暴露纯函数 helper
  - 封装 dialect 归一化和 `sql-formatter` 配置
- 修改 `client/src/features/stage/components/sql-workbench-tab.tsx`
  - 集中定义 `handleFormat`
  - 复用现有 `setSqlText`
- 修改 `client/src/features/stage/components/sql-monaco-editor.tsx`
  - 增加 `onFormat` prop
  - 注册 `Cmd/Ctrl + Shift + F`

### 5.2 责任划分

`format-sql.ts`：
- 输入原始 SQL + 可选 dialect/source
- 返回格式化后的 SQL
- 负责异常兜底与 fallback

`SqlWorkbenchTab`：
- 根据当前 tab 上下文推导方言
- 调用 helper
- 将结果写回编辑器 store

`SqlMonacoEditor`：
- 只注册快捷键，不直接 import formatter

## 6. 测试策略

需要覆盖：

1. helper 单测
   - 基本 `SELECT` 格式化
   - 空白字符串安全
   - dialect fallback 安全
2. workbench 组件单测
   - 点击 `Format` 后写回格式化结果
3. 编辑器组件单测
   - 注册 `Cmd/Ctrl + Shift + F` 后调用 `onFormat`

## 7. 风险与取舍

- `sql-formatter` 不是 AST 级规范化器，个别复杂方言语法可能只做到“尽量格式化”
- 静默失败意味着用户看不到错误原因，但换来最稳妥的编辑体验；后续如果真实使用中出现困惑，再补轻提示
- 初版不做选区格式化，只处理全文，换来实现简单和行为稳定

## 8. 验收标准

- Stage Query Editor 可以通过按钮和快捷键格式化 SQL
- 同一段 SQL 两种入口结果一致
- 不影响 Run/Cancel/Limit/结果面板既有行为
- 相关 vitest 通过
- `cd client && npx tsc --noEmit` 通过
