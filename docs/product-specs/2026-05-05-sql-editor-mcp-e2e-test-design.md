# SQL Editor MCP E2E 测试设计

**日期**：2026-05-05
**状态**：approved
**范围**：基于真实后端（含 embedded OpenCode）+ 前端的 Playwright 端到端测试，验证 SQL 编辑器的全部 MCP 适配器方法和核心 UI 交互

---

## 1. 目标

验证以下两条链路在真实运行环境中的正确性：

1. **用户直接操作链路**：用户通过前端 UI 直接操作 SQL 编辑器（输入 SQL、点击运行、切换上下文、格式化、关闭 tab 等）
2. **AI-MCP 联动链路**：用户通过聊天发送自然语言指令，AI 通过 MCP 调用 `query_editor.*` / `workspace.*` action，前端 SQL 编辑器状态正确变化

## 2. 架构策略

采用**真实全栈启动 + Playwright 浏览器驱动**：

```
Playwright Browser
       │
       ▼
┌─────────────────┐
│   React 19 App  │  ← 前端 (localhost:5173 或 Tauri webview)
│  sql-workbench  │
└────────┬────────┘
         │ HTTP/SSE
         ▼
┌─────────────────┐
│ Spring Boot 3.5 │  ← 后端 (port 8080)
│  /mcp /api/sql  │     embedded OpenCode (port 4196+)
└─────────────────┘
         │
         ▼
┌─────────────────┐
│  OpenCode 1.4.7 │  ← AI 推理服务
│  MCP client     │
└─────────────────┘
```

**启动顺序**：
1. `cd server && mvn spring-boot:run -pl data-talk-adapter`（自动启动 embedded OpenCode）
2. `cd client && npm run dev`（Vite dev server）
3. Playwright 连接浏览器，执行测试套件

## 3. 测试环境前提

| 条件 | 要求 |
|------|------|
| OpenCode 二进制 | `~/.data-talk/opencode/v1.4.7/opencode` 已存在或可自动下载 |
| AI 模型 | 已配置可用模型（环境变量 `DATATALK_REAL_OPENCODE_MODEL`） |
| 数据源 | 至少一个 H2 / MySQL / PG 测试连接已配置 |
| 测试数据 | H2 内存库已预置 `users`、`orders` 表及 seed 数据（见 §9） |
| 后端端口 | 8080 可用 |
| 前端端口 | 5173 可用 |
| Playwright | `@playwright/test` 已安装且 browsers 已下载 |

## 4. 测试批次与矩阵

### 批次 1：SQL 编辑器核心 UI 交互（不经过 AI）

| # | 场景 | 操作步骤 | 断言点 | MCP 关联 |
|---|------|---------|--------|---------|
| 1.1 | 打开 SQL 编辑器 | 点击 Dock SQL 按钮 | Stage 展开、query_editor tab 创建、标题为 "SQL 编辑器" | `workspace.open` |
| 1.2 | 输入并执行 SELECT | 输入 `SELECT 1 AS one`，按 Ctrl+Enter | 结果面板出现 1 行、列名 "ONE"、状态栏显示耗时 | `query_editor.run_sql` |
| 1.3 | 多语句执行 | 输入 `SELECT 1; SELECT 2`，执行 | 结果集出现 2 个 Tab、可切换、每个显示对应数据 | `query_editor.run_sql` |
| 1.4 | 高风险拦截 | 输入 `DELETE FROM users`（无 WHERE），执行 | 不执行、显示风险拦截面板、提示 "bulk_delete" | `query_editor.run_sql` |
| 1.5 | Toolbar 上下文切换 | 关闭 "固定 session 上下文" 开关、选择连接/数据库/Schema | 下拉值正确、执行时 resolvedContext 对应 | `query_editor.set_context` |
| 1.6 | SQL 格式化 | 输入未格式化的 SQL，点击格式化按钮 | 编辑器内容被美化排版 | `query_editor.format_sql` |
| 1.7 | Tab 管理 | X 按钮关闭、右键菜单（关闭其他/全部/左侧/右侧） | tab 列表正确变化、store 状态同步 | `workspace.detach` / `workspace.trash` |
| 1.8 | Stage 最大化/还原 | 点击最大化按钮、再点击还原 | 布局变化、编辑器高度自适应 | — |

### 批次 2：AI-MCP 联动（通过聊天触发）

| # | 场景 | 用户输入 | 预期 AI 行为 | 前端断言 |
|---|------|---------|------------|---------|
| 2.1 | AI 打开并执行查询 | "帮我在 SQL 编辑器里查询 users 表的所有数据" | 调用 `workspace.open` (query_editor) + `query_editor.run_sql` | Stage 展开、tab 打开、结果面板有数据 |
| 2.2 | AI 切换连接上下文 | "把当前 SQL 编辑器的连接切换到 test_db" | 调用 `query_editor.set_context` | toolbar 连接/数据库下拉值变化 |
| 2.3 | AI 格式化 SQL | "帮我格式化当前 SQL 编辑器里的内容" | 调用 `query_editor.format_sql` | 编辑器内容排版变化 |
| 2.4 | AI 修改 SQL 内容 | "把当前 SQL 的 WHERE 条件改成 status='active'" | 调用 `query_editor.apply_text_edits` | 编辑器内容被精确替换 |
| 2.5 | AI 打开 ER 检查器 | "打开 ER 检查器查看 users 和 orders 表的关系" | 调用 `workspace.open_er_inspector` | er_inspector tab 打开、canvas 渲染 |

### 批次 3：边界与容错

| # | 场景 | 操作 | 预期行为 |
|---|------|------|---------|
| 3.1 | 无连接执行 SQL | 新建 SQL tab、断开所有连接、执行 `SELECT 1` | 错误提示、引导选择连接 |
| 3.2 | 切换会话状态保持 | 会话 A 打开 SQL tab → 切到会话 B → 切回 A | SQL tab 仍在、内容不丢失（Stage global 设计） |
| 3.3 | 刷新恢复 | 有 SQL tab 时刷新页面 | tab 恢复、payload 正确反序列化 |
| 3.4 | AI 执行高风险 SQL | "删除 users 表里 id=1 的记录" | AI 生成 DELETE → 预览卡片 → 用户确认 → 执行 |
| 3.5 | 分页限制生效 | 执行大数据量 SELECT | 结果截断提示、limit 下拉值与返回行数一致 |

## 5. 已知风险领域

基于代码和设计文档分析，以下区域最可能暴露 BUG：

| 风险 | 说明 | 测试重点 |
|------|------|---------|
| MCP 命名映射断裂 | AI 调用 MCP tool 时用的是 `datatalk_ui_exec` 等终名，内部 action 仍是短名。bridge 映射出错会导致 action 静默失败。 | 批次 2 全部场景 |
| Session 上下文 stale | `QueryEditorAdapter.getResolvedState()` 依赖多个 store，可能存在闭包过时。AI 修改 session context 后 toolbar 未实时联动。 | 1.5、2.2、3.2 |
| Result 面板状态漂移 | `sql-workbench-store` 按 tabId 管理结果，但 tab 关闭后 store 可能未清理，导致新 tab 复用旧结果。 | 1.7、1.2 |
| Monaco mount 不稳定 | 编辑器在测试中可能 mount 失败或内容不同步。 | 1.2、1.6、2.4 |
| OpenCode 启动时序 | 首轮对话时 MCP 可能未就绪，AI 看不到工具。前端应有 degraded banner。 | 2.1 |
| `apply_text_edits` 版本冲突 | AI 基于旧 version 发 patch，用户同时编辑导致 baseVersion 不匹配。 | 2.4 |
| ContextOverride 与 SessionContext 混叠 | `useSessionContext=true` 时，AI 传 `connectionId` 应被拒绝；但实现可能未正确拦截。 | 1.5、2.2 |
| ER inspector seed 失败 | `open_er_inspector` 后端调用 `/api/er/seed-inspector` 可能因连接不可用而失败，前端错误处理。 | 2.5 |

## 6. Playwright 测试策略

### 6.1 页面模型（Page Object Model）

```
SqlWorkbenchPage
├── toolbar: SqlToolbar
│   ├── runButton
│   ├── formatButton
│   ├── connectionSelect
│   ├── databaseSelect
│   ├── schemaSelect
│   ├── limitSelect
│   └── useSessionContextSwitch
├── editor: MonacoEditor
│   ├── setContent(text)
│   ├── getContent() → string
│   └── pressCtrlEnter()
├── resultPanel: SqlResultPanel
│   ├── resultTabs
│   ├── activeResultTable
│   ├── rowCountText
│   └── executionTimeText
└── statusBar: SqlStatusBar
```

```
ChatPanel
├── composerInput
├── sendButton
└── messageList
    └── lastToolCallCard
```

### 6.2 Monaco 交互策略

Monaco Editor 不是原生 `<textarea>`，不能直接用 `page.fill()`。采用 **page.evaluate + Monaco editor API** 方案：

```typescript
// setContent
await page.evaluate((text) => {
  const model = window.monaco.editor.getModels()[0]
  model?.setValue(text)
}, sql)

// getContent
const content = await page.evaluate(() => {
  const model = window.monaco.editor.getModels()[0]
  return model?.getValue() ?? ''
})
```

- 先等待 `.monaco-editor` DOM 出现（timeout 5s）
- 再等待 `window.monaco` 全局对象可用（轮询 500ms，最多 10 次）
- 再等待 `getModels().length > 0`（模型已挂载，轮询 200ms，最多 10 次）
- 只有三层全部通过后才执行 setValue/getValue
- 若任一层超时，测试失败并截图，**不降级为键盘逐字输入**（后者太慢且不可靠）

### 6.3 等待策略

| 元素 | 等待条件 |
|------|---------|
| SQL 执行结果 | `resultPanel.rowCountText` 出现或 `riskPanel` 出现，timeout 10s |
| AI 响应 | `messageList` 新增消息或工具卡片，timeout 60s |
| OpenCode ready | 页面加载后检查 degraded banner 不存在，timeout 30s |
| Monaco DOM 就绪 | `editor` 容器内 `.monaco-editor` 出现，timeout 5s |
| Monaco 全局可用 | `window.monaco` 可访问，轮询 timeout 5s |
| Monaco 模型就绪 | `monaco.editor.getModels().length > 0`，轮询 timeout 2s |

### 6.4 AI Action 可观测性与断言锚点

批次 2（AI-MCP 联动）的断言不依赖 AI 回复文本，而是依赖**前端状态变化**和**tool call 卡片**：

| 场景 | 主要断言锚点 | 辅助断言锚点 |
|------|-------------|-------------|
| 2.1 AI 打开并执行查询 | Stage 展开 + query_editor tab 出现 + 结果面板 `rowCount > 0` | 聊天区 tool call 卡片状态为 "completed" |
| 2.2 AI 切换连接上下文 | toolbar 连接下拉值变化 + 数据库下拉值变化 | 聊天区无错误提示 |
| 2.3 AI 格式化 SQL | 编辑器内容排版变化（换行数增加或关键字大写） | 聊天区 tool call 卡片状态为 "completed" |
| 2.4 AI 修改 SQL 内容 | 编辑器内容包含预期子串 | 聊天区 tool call 卡片状态为 "completed" |
| 2.5 AI 打开 ER 检查器 | er_inspector tab 出现 + canvas 区域非空（节点数 > 0） | 聊天区 tool call 卡片状态为 "completed" |

**Tool call 卡片观测方式**：在聊天消息列表中查找最后一个 `[data-testid="tool-call-card"]` 或包含 `datatalk_` 前缀工具名的元素，读取其 `data-status` 属性（pending / completed / error）。

### 6.5 证据收集（BUG 规范）

每次测试失败时自动收集：
- 截图：`tmp/playwright/YYYY-MM-DD/<test-name>-failure.png`
- Trace：`tmp/playwright/YYYY-MM-DD/trace.zip`
- 控制台日志：浏览器 console messages
- 后端日志：tail `server/data-talk-adapter/target/spring-boot.log` 最后 100 行

符合 BUG 登记条件时，按 `docs/bugs/README.md` 模板创建 BUG 文件。

## 9. 测试数据规划

批次 1（场景 1.4 高风险拦截）和批次 2（场景 2.1、2.4、2.5、3.4）需要 `users` 和 `orders` 表存在。

### Seed 数据来源

采用 **H2 内存库 + Flyway 测试 migration** 方案：

1. 在后端 `data-talk-adapter/src/test/resources/db/testdata/` 下创建 `V999__e2e_test_seed.sql`
2. SQL 内容：
   ```sql
   CREATE TABLE IF NOT EXISTS users (
     id INT PRIMARY KEY,
     name VARCHAR(100),
     email VARCHAR(100),
     status VARCHAR(20)
   );
   CREATE TABLE IF NOT EXISTS orders (
     id INT PRIMARY KEY,
     user_id INT,
     amount DECIMAL(10,2),
     status VARCHAR(20)
   );
   INSERT INTO users (id, name, email, status) VALUES
     (1, 'Alice', 'alice@example.com', 'active'),
     (2, 'Bob', 'bob@example.com', 'inactive'),
     (3, 'Charlie', 'charlie@example.com', 'active');
   INSERT INTO orders (id, user_id, amount, status) VALUES
     (1, 1, 128.50, 'completed'),
     (2, 1, 256.00, 'pending'),
     (3, 2, 99.99, 'completed');
   ```
3. 测试前手动创建一个指向该 H2 内存库的 DataTalk 连接（通过前端 UI 或 REST API）
4. 所有 E2E 测试共享同一连接，测试之间不清理数据（幂等设计：INSERT 用固定 ID，UPDATE 可重复执行）

### Fixture Setup / Teardown

- **Setup**：每个 spec 文件的 `test.beforeAll` 中检查 `/api/connections` 至少返回一个可用连接；若无，通过 API 创建 H2 测试连接
- **Teardown**：每个 spec 文件的 `test.afterAll` 中不删除连接和数据（H2 内存库随后端进程销毁而清理）
- **隔离**：批次间串行执行，避免数据竞争；批次内场景串行执行

## 10. 非目标

- 不测试 `open_er_designer` 的完整画布交互（超出本期范围）
- 不测试 SQL 结果导出功能（有独立设计 spec）
- 不测试诊断/Explain Plan 面板（有独立设计 spec）
- 不覆盖所有数据库类型的兼容性（由数据源覆盖计划负责）

## 11. 成功标准

- 批次 1 全部 8 个场景通过
- 批次 2 全部 5 个场景通过（允许 AI 响应有合理变体，核心 action 调用必须发生）
- 批次 3 全部 5 个场景通过
- 执行过程中发现的 BUG 全部按 `docs/bugs/` 规范登记
- 测试报告明确列出 "本次发现 N 个 BUG，已登记到 docs/bugs/"
