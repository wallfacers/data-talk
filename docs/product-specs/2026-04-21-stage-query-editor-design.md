# Stage Query Editor 设计

**日期**：2026-04-21  
**关联债务**：TD-022、TD-023  
**范围**：前端 Stage 特性全量接通 store + 新增 Query Editor tab（SQL 编辑器 + 结果面板）+ 后端直连执行端点

---

## 1. 背景与目标

当前 `stage-window.tsx` 使用硬编码 `mockTabs` 和 `useState` 管理激活 tab，未接通 `useStageStore`；`stage-dock.tsx` 四个工具按钮无 `onClick` 实现；`stage-tab-bar.tsx` 右键菜单和关闭按钮无实现。这使 Stage 停留在 UI 原型阶段，无法真正使用。

目标：
1. 接通 `stage-window` / `stage-tab-bar` / `stage-dock` 到 `useStageStore`
2. 新增可用的 `query_editor` tab，包含 CodeMirror SQL 编辑器与结果面板
3. 新增后端直连端点 `POST /api/sql/execute`，复用 `CalciteSqlRiskAnalyzer`
4. 实现 AI 预填直接执行、用户手写风险判级的双路径逻辑

ER/Report/Dashboard Dock 按钮本次**不实现**，保留 UI 占位。

---

## 2. 架构总览

```
useStageStore
  ├── listTabs(sessionId)        → StageWindow 渲染 tab 列表
  ├── activeTabIdBySession       → StageWindow 激活态
  ├── focusTab(tabId)            → StageTabBar tab 点击
  ├── closeTab(tabId)            → StageTabBar X 按钮 + 右键菜单
  └── openTab(tab)               → StageDock SQL 按钮

StageTabContent（路由分发）
  ├── 'bang_query'   → BangQueryTab（现有）
  ├── 'query_editor' → QueryEditorTab（新增）
  └── default        → 未知类型占位
```

---

## 3. 前端组件设计

### 3.1 改动组件

#### `stage-window.tsx`
- 删除 `mockTabs` 常量和 `useState(activeTabId)`
- 读取：`useStageStore(s => s.listTabs(sessionId))` 作为 tab 列表
- 读取：`useStageStore(s => s.activeTabIdBySession.get(sessionId) ?? s.activeWorkspaceTabId)` 作为激活 tab
- tab 点击：`onClick` 改为调用 `store.focusTab(tabId)`
- 内容渲染：删除本地 `renderContent()`，改由 `<StageTabContent />` 负责

#### `stage-tab-bar.tsx`
- X 按钮 `onClick`：调用 `store.closeTab(tab.tabId)`，`e.stopPropagation()`
- 右键菜单实现：
  - "关闭此选项卡" → `store.closeTab(tab.tabId)`
  - "关闭其他" → `listTabs(sid).filter(t => t.tabId !== tab.tabId).forEach(t => store.closeTab(t.tabId))`
  - "全部关闭" → `listTabs(sid).forEach(t => store.closeTab(t.tabId))`
  - "关闭左侧" / "关闭右侧" → 按索引过滤后批量关闭

#### `stage-dock.tsx`
- SQL 按钮 `onClick`：
  ```ts
  store.openTab({
    tabId: `query_editor_${Date.now()}`,
    type: 'query_editor',
    title: 'SQL 编辑器',
    scope: 'session',
    originSessionId: activeSessionId,
    connectionId: activeConnection?.id,
    payload: { sql: '', source: 'user' },
    createdAt: Date.now(),
  })
  ```
- ER/Report/Dashboard 按钮：保留 UI，暂不添加 `onClick`

#### `stage-tab-content.tsx`
- 新增 `case 'query_editor': return <QueryEditorTab tab={tab} />`

### 3.2 新增组件

#### `query-editor-tab.tsx`

布局（垂直两栏，分割线可拖拽）：

```
┌─────────────────────────────────────────┐
│  [连接名/数据库]        [Ctrl+Enter 运行] │  工具栏
├─────────────────────────────────────────┤
│  SELECT * FROM orders                   │
│  WHERE status = 'pending'               │  CodeMirror 6 SQL 编辑区
│                                         │
├── 可拖拽分割线 ────────────────────────────┤
│  ✓ 42 行 · 38ms              [复制][导出] │  状态栏
├─────────────────────────────────────────┤
│  id │ name   │ status   │ amount        │
│   1 │ Alice  │ pending  │ 128.00        │  DataGrid 结果面板
└─────────────────────────────────────────┘
```

**高风险拦截 UI**（替换结果面板）：
```
⚠ 高风险操作：DELETE 语句缺少 WHERE 子句
  原因：bulk_delete
  [取消]  [发给 AI 审查 →]
```

**AI 预填态**：`tab.payload.source === 'ai'` 时预填 `tab.payload.sql`，工具栏标注"AI 生成"角标，运行按钮文案改为"直接执行"。

#### `tab.payload` 类型

```ts
type QueryEditorPayload = {
  sql: string
  source: 'ai' | 'user'
  connectionId?: string  // 优先用 tab 携带，fallback 到 session 当前连接
}
```

#### `use-sql-execute.ts`

```ts
interface UseSqlExecuteResult {
  execute: (sql: string, connectionId: string, source: 'ai' | 'user') => Promise<void>
  result: SqlResult | null         // { columns, rows, rowCount, executionMs, truncated }
  risk: SqlRisk | null             // { level, reason }，仅 risk_blocked 时有值
  status: 'idle' | 'running' | 'success' | 'risk_blocked' | 'error'
  errorMessage: string | null
}
```

#### `services/api/sql.ts`（新增客户端）

```ts
POST /api/sql/execute
```

### 3.3 "发给 AI 审查"行为

复用现有 `useSendMessage()` hook，消息格式：

```
请帮我检查这段 SQL 是否安全，如果可以执行请帮我执行：

\`\`\`sql
<用户输入的 SQL>
\`\`\`
```

发送后 stage 不关闭，用户在 chat 里看 AI 回复。

---

## 4. 执行流程

```
用户点 Run（或 Ctrl+Enter）
    │
    ├─ source === 'ai'
    │       └─→ POST /api/sql/execute {source:'ai'}
    │               └─→ 直接执行 → 结果面板
    │
    └─ source === 'user'
            └─→ POST /api/sql/execute {source:'user'}
                    ├─ risk LOW/MEDIUM → 执行 → 结果面板
                    └─ risk HIGH → HTTP 422 → 高风险警告 UI
                                        └─ 点"发给 AI 审查" → sendMessage(sql)
```

---

## 5. 后端端点

### 5.1 接口

```
POST /api/sql/execute
Content-Type: application/json

Request:
{
  "connectionId": "c-xxx",
  "sql": "SELECT * FROM orders WHERE status = 'pending'",
  "source": "ai" | "user"
}

Response 200:
{
  "columns": ["id", "name", "status", "amount"],
  "rows": [[1, "Alice", "pending", 128.00], ...],
  "rowCount": 42,
  "executionMs": 38,
  "truncated": false
}

Response 422（风险拦截，仅 source=user 触发）:
{
  "riskLevel": "HIGH",
  "riskReason": "bulk_delete"
}

Response 400: connectionId 不存在 / sql 为空
Response 500: JDBC 执行异常
```

### 5.2 层次

```
data-talk-adapter
  └── SqlExecuteController        新增，POST /api/sql/execute

data-talk-application
  └── SqlExecuteService           新增，编排风险判级 + JDBC 执行
        ├── CalciteSqlRiskAnalyzer  已有，直接注入
        └── DynamicDataSourceRegistry  已有，获取用户库连接
```

### 5.3 行数上限与配置

- 单次最多返回 **5000 行**，超出时 `truncated: true`
- 配置项：`datatalk.sql.max-rows`（默认 5000），写入 `application.yml` 通用配置段（与 `datatalk.channel.*` 同级）

### 5.4 安全边界

| source | 行为 |
|--------|------|
| `ai`   | 跳过风险判级，直接执行 |
| `user` | `CalciteSqlRiskAnalyzer.analyze(sql, Category.QUERY)`，HIGH → 422 不执行 |

---

## 6. 依赖库

- **CodeMirror 6**：`@codemirror/view` + `@codemirror/state` + `@codemirror/lang-sql`
- 市面上 Metabase/Redash/Supabase 均采用此方案；体积轻、无 iframe、React 集成简单

---

## 7. 测试

### 后端（JUnit 5 + AssertJ）

| 文件 | 场景 |
|------|------|
| `SqlExecuteControllerIT.java` | SELECT 返回结果；HIGH 风险 422；connectionId 不存在 400；超 5000 行 truncated=true |

### 前端（vitest）

| 文件 | 场景 |
|------|------|
| `use-sql-execute.test.ts` | ai source 跳过风险直接成功；user source 低风险成功；user source 高风险 risk_blocked |
| `query-editor-tab.test.tsx` | 渲染编辑区；Run 触发 execute；risk_blocked 显示警告 UI；点"发给 AI 审查"触发 sendMessage |
| `stage-window.test.tsx` | store 有 tab 时渲染列表；无 tab 时不渲染 tab bar |

### 手工验收

1. Dock SQL 按钮 → 打开 query_editor tab
2. 输入 `SELECT 1` → Ctrl+Enter → 结果面板出现 1 行
3. 输入 `DELETE FROM orders` → Run → 高风险警告
4. 点"发给 AI 审查" → chat 发出消息
5. AI 预填 SQL tab → Run → 直接执行无警告
6. Tab X 按钮 / 右键菜单关闭可用
