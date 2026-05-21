# SQL Editor MCP E2E 测试执行计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 启动真实全栈环境（embedded OpenCode + Spring Boot + Vite），用 Playwright 对 SQL 编辑器的全部 MCP 适配器方法和核心 UI 交互进行端到端测试，发现的 BUG 按 `docs/bugs/` 规范登记。

**Architecture:** Playwright 浏览器 → React 19 前端 → Spring Boot 后端 → embedded OpenCode AI 服务。批次 1 验证用户直接 UI 交互；批次 2 验证 AI 通过 MCP 调用 action 的联动链路；批次 3 验证边界与容错。

**Tech Stack:** Playwright, TypeScript, Spring Boot 3.5, Java 21, embedded OpenCode 1.4.7, React 19, Vite.

---

## Design Inputs

- Source: [`docs/product-specs/2026-05-05-sql-editor-mcp-e2e-test-design.md`](../product-specs/2026-05-05-sql-editor-mcp-e2e-test-design.md)
- Source: [`docs/product-specs/2026-04-30-sql-editor-toolbar-context-design.md`](../product-specs/2026-04-30-sql-editor-toolbar-context-design.md)
- Source: [`docs/product-specs/2026-04-21-stage-sql-workbench-rebuild-design.md`](../product-specs/2026-04-21-stage-sql-workbench-rebuild-design.md)
- Source: [`docs/product-specs/2026-04-24-opencode-mcp-tool-migration-design.md`](../product-specs/2026-04-24-opencode-mcp-tool-migration-design.md)
- Source: [`client/DESIGN.md`](../../client/DESIGN.md)
- Source: [`docs/bugs/README.md`](../bugs/README.md)

## Scope

- 新建/扩展 Playwright E2E 测试文件覆盖 SQL 编辑器全部 MCP adapter action
- 批次 1：8 个用户直接 UI 交互场景
- 批次 2：5 个 AI-MCP 联动场景（通过聊天触发）
- 批次 3：5 个边界与容错场景
- 发现的运行时偏差按 BUG 规范登记到 `docs/bugs/`

## Non-Goals

- 不新增/修改 SQL 编辑器功能代码（纯测试 + BUG 登记）
- 不测试 ER Designer 完整画布交互
- 不测试 SQL 结果导出、诊断 Explain Plan（有独立 spec）
- 不覆盖所有数据库类型兼容性

## File Structure Map

| File | Responsibility |
|------|----------------|
| `client/playwright.config.ts` | Playwright 配置（baseURL、timeout、retries、trace） |
| `client/tests/e2e/sql-editor-batch1-ui.spec.ts` | 批次 1：核心 UI 直接交互 |
| `client/tests/e2e/sql-editor-batch2-mcp.spec.ts` | 批次 2：AI-MCP 联动 |
| `client/tests/e2e/sql-editor-batch3-edge.spec.ts` | 批次 3：边界与容错 |
| `client/tests/e2e/pom/stage.page.ts` | Page Object: Stage 外壳 |
| `client/tests/e2e/pom/sql-workbench.page.ts` | Page Object: SQL 工作台 |
| `client/tests/e2e/pom/chat-panel.page.ts` | Page Object: 聊天面板 |
| `client/tests/e2e/fixtures/test-seed.sql` | H2 测试库 seed 数据 |
| `docs/bugs/BUG-NNNN-*.md` | 发现的 BUG 文件（按需创建） |
| `docs/bugs/index.md` | BUG 索引更新 |
| `docs/exec-plans/2026-05-05-sql-editor-mcp-e2e-test-plan.md` | 本计划 |

---

## Task 0: 测试基础设施搭建

**Files:**
- Create: `client/playwright.config.ts`
- Create dir: `client/tests/e2e/pom/`
- Create dir: `client/tests/e2e/fixtures/`

- [x] **Step 1: 创建 `client/playwright.config.ts`** (baseURL 实际使用 localhost:1420)

  内容：
  ```typescript
  import { defineConfig, devices } from '@playwright/test'

  export default defineConfig({
    testDir: './tests/e2e',
    fullyParallel: false,         // 批次内串行，避免数据竞争
    workers: 1,                   // 单 worker，确保 Stage 全局状态不冲突
    retries: 0,                   // E2E 不自动重试，失败即登记 BUG
    reporter: [['list'], ['html', { outputFolder: 'tmp/playwright/report' }]],
    use: {
      baseURL: 'http://localhost:5173',
      trace: 'on-first-retry',    // 失败时自动收集 trace
      screenshot: 'only-on-failure',
      video: 'off',               // 视频太占空间，trace 已够
      actionTimeout: 10_000,      // 单个 action 超时 10s
      navigationTimeout: 15_000,
    },
    projects: [
      { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    ],
  })
  ```

- [x] **Step 2: 创建 POM 目录**

  ```bash
  mkdir -p client/tests/e2e/pom
  mkdir -p client/tests/e2e/fixtures
  ```

---

## Task 1: 环境验证与前置检查

**Files:**
- Read only: various config files

- [ ] **Step 1: 确认 OpenCode 二进制存在**

  Run:
  ```bash
  ls -la ~/.data-talk/opencode/v1.4.7/opencode
  ```
  Expected: 文件存在且可执行。

- [ ] **Step 2: 确认 AI 模型已配置**

  Run:
  ```bash
  echo $DATATALK_REAL_OPENCODE_MODEL
  ```
  Expected: 非空，格式为 `<provider>/<model>`。

- [ ] **Step 3: 确认后端可编译启动**

  Run:
  ```bash
  cd server && mvn compile -q
  ```
  Expected: exit 0。

- [ ] **Step 4: 确认前端可编译**

  Run:
  ```bash
  cd client && npx tsc --noEmit
  ```
  Expected: exit 0。

- [ ] **Step 5: 确认 Playwright 依赖已安装**

  Run:
  ```bash
  cd client && npm ls @playwright/test
  ```
  Expected: 显示已安装版本。

  若未安装：
  ```bash
  cd client && npm install -D @playwright/test
  npx playwright install chromium
  ```

- [ ] **Step 6: 确认现有 Playwright 可运行**

  Run:
  ```bash
  cd client && npx playwright test --list
  ```
  Expected: 列出已有测试文件，无配置错误。

- [ ] **Step 7: 确认 H2 测试连接配置可用（不启动后端）**

  检查 `server/data-talk-adapter/src/main/resources/application.yml` 或 `application-test.yml` 中是否有 H2 内存数据源配置；或检查前端是否有预设的 H2 连接模板。测试场景需要 `users` 和 `orders` 表存在（见 Task 0.5）。

---

## Task 2: Page Object Model 搭建

**Files:**
- Create: `client/tests/e2e/pom/stage.page.ts`
- Create: `client/tests/e2e/pom/sql-workbench.page.ts`
- Create: `client/tests/e2e/pom/chat-panel.page.ts`

- [ ] **Step 1: `StagePage`**

  封装 Stage 外壳交互：
  - `openStage()` — 点击 composer 底部电脑图标
  - `closeStage()` — 点击关闭
  - `maximize()` / `restore()` — 最大化/还原
  - `isOpen()` / `isMaximized()` — 状态断言
  - `getTabTitles()` — 获取 tab 标题列表
  - `clickTab(title)` — 点击指定 tab
  - `closeTabByTitle(title)` — 关闭指定 tab
  - `rightClickTab(title)` — 右键菜单
  - `createSession(title?)` — 点击侧边栏「+ 创建会话」
  - `switchSession(sessionTitle)` — 点击侧边栏指定会话
  - `getSessionTitles()` — 获取侧边栏会话标题列表

- [ ] **Step 2: `SqlWorkbenchPage`**

  封装 SQL 工作台：
  - `setSql(text)` — 通过 `page.evaluate` 调用 Monaco `model.setValue()`（三层等待：DOM → window.monaco → model ready）
  - `getSql()` → string — 通过 `page.evaluate` 调用 `model.getValue()`
  - `pressRun()` — Ctrl+Enter
  - `clickRunButton()`
  - `clickFormatButton()`
  - `getResultRowCount()` → number | null
  - `getResultExecutionTime()` → string | null
  - `getResultTabs()` → string[]
  - `switchResultTab(index)`
  - `getRiskMessage()` → string | null
  - `setConnection(value)` — 连接下拉
  - `setDatabase(value)` — 数据库下拉
  - `setSchema(value)` — Schema 下拉
  - `setLimit(value)` — 分页限制下拉
  - `toggleUseSessionContext()` — 开关
  - `waitForResult(timeout = 10_000)` — 等待结果或风险拦截
  - `waitForMonacoReady(timeout = 12_000)` — 三层 Monaco 就绪等待

- [ ] **Step 3: `ChatPanelPage`**

  封装聊天面板：
  - `sendMessage(text)` — 输入并发送
  - `getLastMessage()` → string
  - `getLastToolCallCard()` → { toolName, status }
  - `waitForAiResponse(timeout = 60_000)` — 等待 AI 响应完成（Batch 2 统一 60s）
  - `isDegradedBannerVisible()` — degraded 状态检查

---

## Task 3: 测试数据准备

**Files:**
- Create: `client/tests/e2e/fixtures/test-seed.sql`

- [ ] **Step 1: 创建 H2 seed SQL**

  `client/tests/e2e/fixtures/test-seed.sql`：
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
  MERGE INTO users (id, name, email, status) VALUES
    (1, 'Alice', 'alice@example.com', 'active'),
    (2, 'Bob', 'bob@example.com', 'inactive'),
    (3, 'Charlie', 'charlie@example.com', 'active');
  MERGE INTO orders (id, user_id, amount, status) VALUES
    (1, 1, 128.50, 'completed'),
    (2, 1, 256.00, 'pending'),
    (3, 2, 99.99, 'completed');
  ```

- [ ] **Step 2: 创建 H2 测试连接**

  测试启动后，通过前端 UI 或 REST API 创建一个指向 `jdbc:h2:mem:testdb` 的 DataTalk 连接：
  - 连接名："E2E Test H2"
  - Driver：H2
  - URL：`jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1`
  - User：sa
  - Password：（空）

  连接创建后执行 seed SQL（通过 SQL 编辑器或 REST API）。

- [ ] **Step 3: 验证数据就绪**

  执行 `SELECT COUNT(*) FROM users` 应返回 3；`SELECT COUNT(*) FROM orders` 应返回 3。

---

## Task 4: 批次 1 — 核心 UI 直接交互（8 场景）

**Files:**
- Create: `client/tests/e2e/sql-editor-batch1-ui.spec.ts`

**前置条件：** 后端已启动、H2 测试连接已创建且 seed 数据已执行。

- [ ] **Step 1: 场景 1.1 — 打开 SQL 编辑器**

  步骤：登录 → 点击 Dock SQL 按钮
  断言：Stage 展开、query_editor tab 标题包含 "SQL" 或 "编辑器"

- [ ] **Step 2: 场景 1.2 — 输入并执行 SELECT**

  步骤：打开 SQL 编辑器 → 输入 `SELECT 1 AS one` → Ctrl+Enter
  断言：结果面板出现、rowCount = 1、列名包含 "ONE"、状态栏显示执行耗时

- [ ] **Step 3: 场景 1.3 — 多语句执行**

  步骤：输入 `SELECT 1; SELECT 2` → 执行
  断言：结果集 Tab 数量为 2、可切换、每个显示对应数据

- [ ] **Step 4: 场景 1.4 — 高风险拦截**

  步骤：输入 `DELETE FROM users`（确保无 WHERE）→ 执行
  断言：不执行、显示风险拦截面板、提示包含 "DELETE" 或 "风险"

- [ ] **Step 5: 场景 1.5 — Toolbar 上下文切换**

  步骤：关闭 "固定 session 上下文" 开关 → 选择连接/数据库
  断言：下拉值正确变化、执行时落点对应所选连接

- [ ] **Step 6: 场景 1.6 — SQL 格式化**

  步骤：输入未格式化的 SQL → 点击格式化按钮
  断言：编辑器内容排版变化（如换行增加）

- [ ] **Step 7: 场景 1.7 — Tab 管理**

  步骤：打开多个 SQL tab → X 关闭、右键关闭其他/全部
  断言：tab 列表正确变化

- [ ] **Step 8: 场景 1.8 — Stage 最大化/还原**

  步骤：点击最大化 → 点击还原
  断言：布局变化、编辑器区域高度变化

---

## Task 5: 批次 2 — AI-MCP 联动（5 场景）

**Files:**
- Create: `client/tests/e2e/sql-editor-batch2-mcp.spec.ts`

**前置条件：** OpenCode 就绪、AI 模型可响应、H2 测试连接和数据已就绪。

**超时策略：** 每个场景 `waitForAiResponse` timeout 统一为 60s（含 OpenCode 冷启动 + 模型推理）。

- [ ] **Step 1: 场景 2.1 — AI 打开并执行查询**

  用户输入：`"帮我在 SQL 编辑器里查询 users 表的所有数据"`
  断言锚点：Stage 展开 + query_editor tab 出现 + 结果面板 `rowCount > 0`
  辅助锚点：聊天区最后一个 tool call 卡片状态为 "completed"

- [ ] **Step 2: 场景 2.2 — AI 切换连接上下文**

  前置：已有 SQL tab
  用户输入：`"把当前 SQL 编辑器的连接切换到 test_db"`
  断言锚点：toolbar 连接下拉值变化 + 数据库下拉值变化
  辅助锚点：聊天区无错误提示

- [ ] **Step 3: 场景 2.3 — AI 格式化 SQL**

  前置：SQL tab 里有未格式化内容
  用户输入：`"帮我格式化当前 SQL 编辑器里的内容"`
  断言锚点：编辑器内容排版变化（换行数增加或关键字大写）
  辅助锚点：聊天区 tool call 卡片状态为 "completed"

- [ ] **Step 4: 场景 2.4 — AI 修改 SQL 内容**

  前置：SQL tab 里有 `SELECT * FROM users`
  用户输入：`"把当前 SQL 的 WHERE 条件改成 status='active'"`
  断言锚点：编辑器内容包含预期子串 `WHERE status='active'`
  辅助锚点：聊天区 tool call 卡片状态为 "completed"

- [ ] **Step 5: 场景 2.5 — AI 打开 ER 检查器**

  用户输入：`"打开 ER 检查器查看 users 和 orders 表的关系"`
  断言锚点：er_inspector tab 出现 + canvas 区域节点数 > 0
  辅助锚点：聊天区 tool call 卡片状态为 "completed"

---

## Task 6: 批次 3 — 边界与容错（5 场景）

**Files:**
- Create: `client/tests/e2e/sql-editor-batch3-edge.spec.ts`

- [ ] **Step 1: 场景 3.1 — 无连接执行 SQL**

  步骤：断开所有连接（若前端无显式断开 UI，则通过 API 删除所有连接）→ 新建 SQL tab → 执行 `SELECT 1`
  断言：错误提示可见、引导选择连接

- [ ] **Step 2: 场景 3.2 — 切换会话状态保持**

  **操作方式**：
  1. `stagePage.createSession("会话 A")` — 点击侧边栏「+ 创建会话」，输入标题
  2. 在会话 A 中打开 SQL tab，输入内容 `SELECT 'session-a'`
  3. `stagePage.createSession("会话 B")` — 创建第二个会话
  4. `stagePage.switchSession("会话 A")` — 点击侧边栏「会话 A」

  断言：SQL tab 仍在列表中、编辑器内容仍为 `SELECT 'session-a'`（Stage global 设计：切换会话不改变 Stage 状态）

- [ ] **Step 3: 场景 3.3 — 刷新恢复**

  **技术前提**：DataTalk 前端通过 localStorage + 后端持久化恢复 session/tab 状态。Vite dev server HMR 不破坏状态，但 F5 硬刷新会重建页面。

  步骤：
  1. 打开 SQL tab，输入 `SELECT 'before-refresh'`
  2. 等待 2s 确保持久化写入（stage-persistence 有 debounce）
  3. 按 F5 刷新页面
  4. 等待页面加载完成
  5. 检查 Stage 是否自动展开（若刷新前 Stage 是展开的）

  断言：SQL tab 恢复、payload 正确反序列化、编辑器内容恢复为 `SELECT 'before-refresh'`

  **风险说明**：若刷新后 localStorage 被清理或后端 hydration 失败，此场景即暴露 BUG。若当前实现不支持刷新恢复，则登记为已知缺失（不视为测试失败，但需记录）。

- [ ] **Step 4: 场景 3.4 — AI 执行高风险 SQL**

  用户输入：`"删除 users 表里 id=1 的记录"`
  断言：AI 生成 DELETE → 预览卡片出现 → 用户确认 → 执行 → 结果正确

- [ ] **Step 5: 场景 3.5 — 分页限制生效**

  步骤：选择 limit=10 → 执行 `SELECT * FROM users`（users 表只有 3 行，需用 UNION ALL 构造更多行或换大表）
  断言：结果行数 ≤10、截断提示可见（若数据 >10 行）

---

## Task 7: 执行测试、收集证据、登记 BUG

**Files:**
- Create/Modify: `docs/bugs/BUG-NNNN-*.md`（按需）
- Modify: `docs/bugs/index.md`

- [ ] **Step 1: 启动全栈环境**

  后台启动：
  ```bash
  cd server && mvn spring-boot:run -pl data-talk-adapter &
  cd client && npm run dev &
  ```
  等待：后端 health ready、前端可访问、`/api/health` 返回 ok。

- [ ] **Step 2: 创建 H2 测试连接并导入 seed 数据**

  通过前端 UI 或 REST API 创建 H2 连接并执行 seed SQL。这是所有批次的前置条件。

- [ ] **Step 3: 运行批次 1**

  ```bash
  cd client && npx playwright test tests/e2e/sql-editor-batch1-ui.spec.ts --headed
  ```
  收集：截图、trace、控制台日志、后端日志片段。

- [ ] **Step 4: 运行批次 2**

  ```bash
  cd client && npx playwright test tests/e2e/sql-editor-batch2-mcp.spec.ts --headed
  ```
  注意：AI 响应有自然变体，断言聚焦在"action 是否被调用"和"前端状态是否变化"，不硬编码 AI 回复文本。超时统一 60s。

- [ ] **Step 5: 运行批次 3**

  ```bash
  cd client && npx playwright test tests/e2e/sql-editor-batch3-edge.spec.ts --headed
  ```

- [ ] **Step 6: 整理测试报告**

  汇总：总场景数、通过数、失败数、BUG 登记数。
  格式：`本次 E2E 测试共执行 X 个场景，发现 N 个 BUG，已登记到 docs/bugs/`

- [ ] **Step 7: 登记 BUG**

  对每一个失败的场景：
  1. 读 `docs/bugs/index.md` 获取当前编号
  2. 创建 `docs/bugs/BUG-NNNN-<kebab-slug>.md`，按模板填写
  3. 更新 `docs/bugs/index.md`（当前编号 +1、Open BUGs 表插入、By Module、By Source）
  4. 截图 ≤500KB 入 `docs/bugs/assets/BUG-NNNN/`；trace/HAR 入 `tmp/`

---

## Task 8: Consolidated Verification 与文档收尾

**Files:**
- Modify: `docs/exec-plans/2026-05-05-sql-editor-mcp-e2e-test-plan.md`
- Modify: `docs/exec-plans/index.md`

- [ ] **Step 1: 回填 checklist**

  将本计划中所有 `- [ ]` 按实际结果改为 `- [x]` 或带注释的 `- [ ]`（跳过原因）。

- [ ] **Step 2: 更新 exec-plans index**

  将本计划从 Active 移到 Completed，附测试报告摘要和 BUG 统计。

- [ ] **Step 3: 最终报告**

  在对话中输出最终报告，明确包含：
  - 执行场景总数
  - 通过/失败数
  - 发现的 BUG 数量及文件路径
  - 未覆盖的风险说明（如有）

---

## Execution Notes

- 批次 1 和批次 3 大部分场景不依赖 AI，可以并行执行（但 workers=1 保证串行）。
- 批次 2 依赖 OpenCode 和 AI 模型，必须串行执行，每个场景 `waitForAiResponse` timeout 统一为 **60s**（含 OpenCode 冷启动 + 模型 first-token 延迟 + 推理）。
- 如果 AI 响应不稳定导致 flaky，记录为 BUG（`source: e2e-playwright`）而非重试掩盖。
- 按 CLAUDE.md `BUG Tracking Gate` 规则：N=0 也要明确说。
- 场景 3.3（刷新恢复）若当前实现不支持，不视为测试失败，但需在报告中明确标注为 "已知缺失 / 需产品决策"。
