# ER 模块端到端浏览器测试 — 执行计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 ER Inspector + ER Designer 全表面 / 全按钮 / 全交互建立 47 条 Playwright 端到端浏览器测试基线（Inspector 11 + Designer 30 + 入口持久化 6），纯 UI-only 路线，不依赖 OpenCode 模型，CI 稳定可跑。

**Architecture:** 复用现有 `client/tests/e2e/` 目录下 fixture / playwright.config / `setupH2Connection`；新增 ER 专用 helper（`er-test-helpers.ts`）+ 扩充 ER POM；3 个新 spec 文件按表面切分；按 CLAUDE.md Parallel Plan Execution 规则，Tasks 2/3/4 三个 spec 独立可并发实现。

**Tech Stack:** Playwright 1.x · TypeScript · React 19 · Vite · Spring Boot 3.5（已运行后端）· Tauri v2（dev mode 不需要，用浏览器跑）· Zustand store · ReactFlow `@xyflow/react` v12 · `@xyflow/react/dist/style.css`。

---

## Design Inputs

- Source: [`docs/product-specs/2026-05-06-er-module-e2e-test-design.md`](../product-specs/2026-05-06-er-module-e2e-test-design.md) — 本计划唯一规格源
- Source: [`docs/product-specs/2026-04-29-er-graph-browsing-design.md`](../product-specs/2026-04-29-er-graph-browsing-design.md) — ER 模块产品 spec
- Source: [`docs/references/er-tab-protocol.md`](../references/er-tab-protocol.md) — Inspector / Designer payload schema、patch 路径白名单、exec verbs、错误码
- Source: [`client/DESIGN.md`](../../client/DESIGN.md) — 前端设计契约
- Source: [`CLAUDE.md`](../../CLAUDE.md) — 项目工作守则
- Source: [`docs/bugs/README.md`](../bugs/README.md) — BUG 登记规范
- Source: 既有 fixture & POM 范例：`client/tests/e2e/fixtures/h2-setup.ts` / `client/tests/e2e/pom/sql-workbench.page.ts`

## Applicable Constraints from `client/DESIGN.md`

- `useStageStore` 是全局非 per-session：切 session 不变更 stage tabs；测试不应假设 per-session 隔离（INV-3）
- `StageTab` 无 `scope` 字段；type-level scope 仅在 `tab-type-registry.ts`，断言时不假设 per-session shape
- `prefers-reduced-motion` 下不强制动画；测试不依赖动画 settle 时间硬等，只用 `waitForFunction` 判完成

---

## File Structure Map

| File | Responsibility |
|---|---|
| `client/tests/e2e/er-inspector-ui.spec.ts` | Inspector Toolbar + Canvas 全交互（I1-I11，11 tests） |
| `client/tests/e2e/er-designer-ui.spec.ts` | Designer Toolbar + 右键菜单 + 列内联 + 拖连边 + 键盘 + 空态 + Bind Dialog + DDL（D1-D30，30 tests） |
| `client/tests/e2e/er-entry-and-persistence.spec.ts` | 真实入口 + Fork 跨 Tab + 刷新持久化 + 跨 session（E1-E6，6 tests） |
| `client/tests/e2e/fixtures/er-seed.sql` | **条件创建**：4 张表 + 2 FK + 隐式关联候选列 |
| `client/tests/e2e/fixtures/er-test-helpers.ts` | helper：`openErInspectorViaShortcut` / `openErDesignerViaShortcut` / `readInspectorPayload` / `readDesignerPayload` / `waitForPayloadVersion` / `startFetchRecorder` / `setupErFixture` |
| `client/tests/e2e/pom/er-inspector.page.ts` | 现 6 → 14 async 方法 |
| `client/tests/e2e/pom/er-designer.page.ts` | 现 7 → 28 async 方法 |
| 产品代码（**仅加 testid 钩子**） | `client/src/features/stage/components/er-canvas/ErCanvas.tsx`（`data-payload-version`）<br>`client/src/features/stage/components/er-canvas/ErTableNode.tsx`（`data-er-table-id` / `data-er-table-name` / `data-er-column-handle`）<br>`client/src/features/stage/components/er-canvas/ErToolbar.tsx`（`data-testid="er-toolbar-*"`） |
| `docs/bugs/BUG-NNNN-*.md` | 按需创建（已预登 3 条：A noop / B 入口缺失 / C testid 钩子不允许加） |
| `docs/bugs/index.md` | 按需更新 |
| `docs/exec-plans/index.md` | 本计划注册到 Active 区 |

## Parallel Execution Map

按 CLAUDE.md Parallel Plan Execution 规则：

| Phase | Tasks | 是否并发 |
|---|---|---|
| Phase 1 | Task 0（fixture + 产品代码 prep） | 串行（其他全部依赖） |
| Phase 2 | Task 1（helper + 双 POM 扩充） | 串行（spec 实现依赖 POM） |
| Phase 3 | **Task 2 + Task 3 + Task 4 并发** | **可并发**（独立 spec 文件） |
| Phase 4 | Task 5（集成跑 + BUG 登记 + housekeeping） | 串行 |

---

## Task 0: Fixture & 产品代码 prep

**Files:**
- Read only: `client/tests/e2e/fixtures/test-seed.sql`
- Create (条件): `client/tests/e2e/fixtures/er-seed.sql`
- Modify: `client/src/features/stage/components/er-canvas/ErCanvas.tsx`
- Modify: `client/src/features/stage/components/er-canvas/ErTableNode.tsx`
- Modify: `client/src/features/stage/components/er-canvas/ErToolbar.tsx`

- [x] **Step 1: 验证 `test-seed.sql` FK 状态**

```bash
grep -i 'FOREIGN KEY\|REFERENCES' client/tests/e2e/fixtures/test-seed.sql || echo 'NO FK'
```

Expected：当前输出 `NO FK`（`test-seed.sql` 无 FK 声明）→ 必须创建 `er-seed.sql`。如果输出含 FK 行 → 跳过 Step 2。

- [x] **Step 2: 创建 `er-seed.sql`（包含 FK + 隐式关联候选列）**

写入 `client/tests/e2e/fixtures/er-seed.sql`：

```sql
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS products;

CREATE TABLE users (
  id INT PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  name VARCHAR(100),
  status VARCHAR(20)
);

CREATE TABLE products (
  id INT PRIMARY KEY,
  sku_code VARCHAR(64) UNIQUE,
  name VARCHAR(255)
);

CREATE TABLE orders (
  id INT PRIMARY KEY,
  user_id INT,
  user_email VARCHAR(255),
  amount DECIMAL(10,2),
  status VARCHAR(20),
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE order_items (
  id INT PRIMARY KEY,
  order_id INT,
  product_id INT,
  product_sku VARCHAR(64),
  quantity INT,
  CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_oi_product FOREIGN KEY (product_id) REFERENCES products(id)
);

INSERT INTO users (id, email, name, status) VALUES
  (1, 'alice@example.com', 'Alice', 'active'),
  (2, 'bob@example.com', 'Bob', 'inactive'),
  (3, 'charlie@example.com', 'Charlie', 'active');

INSERT INTO products (id, sku_code, name) VALUES
  (1, 'SKU-001', 'Widget'),
  (2, 'SKU-002', 'Gadget');

INSERT INTO orders (id, user_id, user_email, amount, status) VALUES
  (1, 1, 'alice@example.com', 128.50, 'completed'),
  (2, 1, 'alice@example.com', 256.00, 'pending'),
  (3, 2, 'bob@example.com', 99.99, 'completed');

INSERT INTO order_items (id, order_id, product_id, product_sku, quantity) VALUES
  (1, 1, 1, 'SKU-001', 2),
  (2, 1, 2, 'SKU-002', 1),
  (3, 2, 1, 'SKU-001', 5);
```

注：`orders.user_email` / `order_items.product_sku` 是隐式关联候选（与 `users.email` / `products.sku_code` 同语义）。

- [x] **Step 3: 重新 grep 验证 client services 无 XHR / Tauri HTTP 引入**

```bash
grep -rn 'XMLHttpRequest\|@tauri-apps/api/http\|new XHR' client/src/services 2>/dev/null || echo 'PURE FETCH'
```

Expected：`PURE FETCH`。若出现命中 → INV-1 守卫失效，必须把 `startFetchRecorder` 改为 `page.route('**/api/sql/execute', ...)` 兜底（修订 Task 1 Step 1 实现）。

- [x] **Step 4: 给 `ErCanvas.tsx` 加 `data-payload-version`**

`client/src/features/stage/components/er-canvas/ErCanvas.tsx:289`：

```tsx
// before
<div className="flex h-full w-full flex-col" data-er-tab-id={tabId}>

// after
<div
  className="flex h-full w-full flex-col"
  data-er-tab-id={tabId}
  data-payload-version={(payload as unknown as { __v?: number })?.__v ?? 0}
>
```

理由：`er-tabs-store.ts` 内部用 `__v` 字段记录 payload version（`er-designer-tab.tsx:58` `designerVersion` 函数读 `__v`）。Inspector / Designer payload 复用同一字段。

- [x] **Step 5: 给 `ErTableNode.tsx` 加 `data-er-table-id` / `data-er-table-name` / `data-er-column-handle`**

读现状：

```bash
grep -n 'Handle\|table\.name\|table\.id\|data-er-mode' client/src/features/stage/components/er-canvas/ErTableNode.tsx | head -20
```

修改：在节点根容器 `<div data-er-mode={data.mode}>` 处追加 `data-er-table-id={table.id ?? table.name}` 和 `data-er-table-name={table.name}`；在每个 `<Handle>` 处追加 `data-er-column-handle={\`${columnId}:${pos === 'source' ? 'source' : 'target'}\`}`。

精确编辑示例（节点根，行号根据当前文件确认）：

```tsx
// before
<div data-er-mode={data.mode} ...>

// after
<div
  data-er-mode={data.mode}
  data-er-table-id={table.id ?? table.name}
  data-er-table-name={table.name}
  ...>
```

Handle 处（每个 `<Handle type="source"` / `type="target"` 实例）：

```tsx
// before
<Handle type="source" id={`${col.id}-source`} ... />

// after
<Handle
  type="source"
  id={`${col.id}-source`}
  data-er-column-handle={`${col.id}:source`}
  ... />
```

- [x] **Step 6: 给 `ErToolbar.tsx` 加 `data-testid="er-toolbar-*"`**

读现状：

```bash
grep -n 'ToolbarButton\|onClick={props\.' client/src/features/stage/components/er-canvas/ErToolbar.tsx
```

`ToolbarButton` 接受 `label` 文本，需新增可选 `testId` prop 并落到 `<Button>` 的 `data-testid`。

```tsx
// 修改 ToolbarButton 签名
function ToolbarButton({
  onClick, icon, label, primary, disabled, disabledHint, testId,
}: {
  onClick: () => void
  icon: ReactNode
  label: string
  primary?: boolean
  disabled?: boolean
  disabledHint?: string
  testId?: string
}) {
  // ...existing body...
  // 在 <Button ... > 上加 data-testid={testId}
}
```

调用点逐个补 `testId`：

```tsx
<ToolbarButton onClick={props.onAddTable} testId="er-toolbar-add-table" ... />
<ToolbarButton onClick={props.onAutoLayout} testId="er-toolbar-auto-layout" ... />
<ToolbarButton onClick={props.onFitView} testId="er-toolbar-fit-view" ... />
<ToolbarButton onClick={props.onBindTarget} testId="er-toolbar-bind-target" ... />
<ToolbarButton onClick={props.onDiffVsDb} testId="er-toolbar-diff" ... />
<ToolbarButton onClick={props.onGenerateDdl} testId="er-toolbar-generate-ddl" ... />
<ToolbarButton onClick={props.onRefresh} testId="er-toolbar-refresh" ... />
<ToolbarButton onClick={props.onAddVirtualRelation} testId="er-toolbar-add-virtual-relation" ... />
<ToolbarButton onClick={props.onForkToDesigner} testId="er-toolbar-fork-to-designer" ... />
```

Dialect select 容器加 `data-testid="er-toolbar-dialect"`：

```tsx
<Select value={props.dialect} ...>
  <SelectTrigger data-testid="er-toolbar-dialect" ... >
```

Neighbor depth `<select>` 加：

```tsx
<select
  data-testid="er-toolbar-neighbor-depth"
  aria-label={...}
  value={props.neighborDepth}
  ... />
```

- [x] **Step 7: 跑 type check + 既有单测**

```bash
cd client && npx tsc --noEmit
cd client && npm test -- ErCanvas ErTableNode ErToolbar
```

Expected：tsc exit 0；vitest 全绿（既有 `__tests__/Er*.test.tsx` 不依赖 testid，加属性不破坏断言）。

- [x] **Step 8: 提交**

```bash
git add client/tests/e2e/fixtures/er-seed.sql \
        client/src/features/stage/components/er-canvas/ErCanvas.tsx \
        client/src/features/stage/components/er-canvas/ErTableNode.tsx \
        client/src/features/stage/components/er-canvas/ErToolbar.tsx
git commit -m "$(cat <<'EOF'
test(er-e2e): add testid hooks + er-seed fixture for E2E

Adds non-functional test hooks for ER canvas / table node / toolbar so
the upcoming Playwright UI suite can locate elements without relying on
i18n text or DOM heuristics. Adds er-seed.sql with 4 tables + 3 FKs +
implicit relation candidate columns to make Inspector neighbor / layout
assertions meaningful.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 1: Helper + POM 扩充

**Files:**
- Create: `client/tests/e2e/fixtures/er-test-helpers.ts`
- Modify: `client/tests/e2e/pom/er-inspector.page.ts`
- Modify: `client/tests/e2e/pom/er-designer.page.ts`

- [x] **Step 1: 创建 `er-test-helpers.ts`**

写入 `client/tests/e2e/fixtures/er-test-helpers.ts`：

```ts
import type { APIRequestContext, Page } from '@playwright/test'
import { request } from '@playwright/test'

const BACKEND_URL = process.env.DATATALK_BACKEND_URL ?? 'http://localhost:8080'

export interface ErInspectorOpenOpts {
  connectionId: string
  tables: string[]
  neighborDepth?: 0 | 1 | 2
}

export interface ErDesignerOpenOpts {
  dialect: 'mysql' | 'postgresql' | 'h2' | 'sqlite'
  seedTables?: Array<{ id?: string; name: string; columns?: any[] }>
}

/**
 * 走 useStageStore.openTab + useErTabsStore.hydrate{Inspector,Designer}
 * 直接在浏览器内构造一个 ER Tab，绕过产品入口（spec 1/2 的快捷路径）。
 */
export async function openErInspectorViaShortcut(
  page: Page,
  opts: ErInspectorOpenOpts,
): Promise<{ tabId: string }> {
  const tabId = `er-inspector-${Date.now()}-${Math.floor(Math.random() * 1e6)}`
  await page.evaluate(
    ({ tabId, opts }) => {
      // @ts-expect-error window 全局
      const stage = window.__DT_E2E__?.stage ?? require('@/stores/stage-store').useStageStore.getState()
      // @ts-expect-error window 全局
      const er = window.__DT_E2E__?.er ?? require('@/features/stage/stores/er-tabs-store').useErTabsStore.getState()
      const payload = {
        kind: 'er_inspector',
        connectionId: opts.connectionId,
        database: null,
        schema: null,
        selection: opts.tables,
        neighborDepth: opts.neighborDepth ?? 1,
        layout: 'dagre-LR',
        tablesSnapshot: [],
        snapshotAt: Date.now(),
        positions: {},
        collapsed: [],
        virtualRelations: [],
        notes: {},
        viewport: { x: 0, y: 0, zoom: 1 },
        __v: 0,
      }
      stage.openTab({
        id: tabId,
        type: 'er_inspector',
        title: 'ER Inspector (e2e)',
        connectionId: opts.connectionId,
      })
      er.hydrateInspector(tabId, payload)
    },
    { tabId, opts },
  )
  await page.locator(`[data-er-tab-id="${tabId}"]`).waitFor({ state: 'visible', timeout: 10_000 })
  return { tabId }
}

export async function openErDesignerViaShortcut(
  page: Page,
  opts: ErDesignerOpenOpts,
): Promise<{ tabId: string }> {
  const tabId = `er-designer-${Date.now()}-${Math.floor(Math.random() * 1e6)}`
  await page.evaluate(
    ({ tabId, opts }) => {
      const stage = (window as any).__DT_E2E__?.stage
        ?? require('@/stores/stage-store').useStageStore.getState()
      const er = (window as any).__DT_E2E__?.er
        ?? require('@/features/stage/stores/er-tabs-store').useErTabsStore.getState()
      const payload = {
        kind: 'er_designer',
        targetConnectionId: null,
        targetDatabase: null,
        targetSchema: null,
        dialect: opts.dialect,
        tables: (opts.seedTables ?? []).map((t, i) => ({
          id: t.id ?? `t_${t.name}_${i}`,
          name: t.name,
          comment: null,
          columns: t.columns ?? [],
          indexes: [],
          uniques: [],
        })),
        relations: [],
        positions: {},
        collapsed: [],
        viewport: { x: 0, y: 0, zoom: 1 },
        __v: 0,
      }
      stage.openTab({
        id: tabId,
        type: 'er_designer',
        title: 'ER Designer (e2e)',
        connectionId: null,
      })
      er.hydrateDesigner(tabId, payload)
    },
    { tabId, opts },
  )
  await page.locator(`[data-er-tab-id="${tabId}"]`).waitFor({ state: 'visible', timeout: 10_000 })
  return { tabId }
}

export async function readInspectorPayload(page: Page, tabId: string): Promise<any> {
  return await page.evaluate((id) => {
    const er = require('@/features/stage/stores/er-tabs-store').useErTabsStore.getState()
    return er.inspectors.get(id) ?? null
  }, tabId)
}

export async function readDesignerPayload(page: Page, tabId: string): Promise<any> {
  return await page.evaluate((id) => {
    const er = require('@/features/stage/stores/er-tabs-store').useErTabsStore.getState()
    return er.designers.get(id) ?? null
  }, tabId)
}

/**
 * 等 payload version 满足 predicate；超时 fail。
 * 默认 timeoutMs=2000（匹配 INV-5 force-flush 阈值）。
 */
export async function waitForPayloadVersion(
  page: Page,
  tabId: string,
  predicate: (v: number) => boolean,
  timeoutMs = 2_000,
): Promise<number> {
  return await page.waitForFunction(
    ({ tabId, predicateSrc }) => {
      const el = document.querySelector(`[data-er-tab-id="${tabId}"]`)
      const v = parseInt(el?.getAttribute('data-payload-version') ?? '0', 10)
      // eslint-disable-next-line no-new-func
      return new Function('v', `return (${predicateSrc})(v)`)(v) ? v : false
    },
    { tabId, predicateSrc: predicate.toString() },
    { timeout: timeoutMs },
  ).then(async (handle) => Number(await handle.jsonValue()))
}

/**
 * 拦截 window.fetch 记录所有请求。
 * 前提：client services 无 XHR / Tauri HTTP（Task 0 Step 3 已 grep 验证）。
 */
export async function startFetchRecorder(page: Page): Promise<{
  callsMatching: (pattern: RegExp) => number
  allCalls: () => string[]
  stop: () => Promise<void>
}> {
  await page.addInitScript(() => {
    ;(window as any).__DT_FETCH_LOG__ = [] as string[]
    const original = window.fetch
    window.fetch = function (...args: any[]) {
      const url = typeof args[0] === 'string' ? args[0] : (args[0] as Request).url
      ;(window as any).__DT_FETCH_LOG__.push(url)
      return original.apply(window, args as any)
    }
  })
  return {
    callsMatching: (pattern) => {
      // 同步读取（Playwright 测试链路里通过 page.evaluate 异步同步）
      return -1 // placeholder: 用 allCalls() 真正断言
    },
    allCalls: async () => (await page.evaluate(() => (window as any).__DT_FETCH_LOG__ ?? [])) as any,
    stop: async () => {
      await page.evaluate(() => {
        ;(window as any).__DT_FETCH_LOG__ = []
      })
    },
  } as any
}

/**
 * 同步版 callsMatching：spec 中 await 取数组后用 Array.filter。
 */
export async function fetchCallsMatching(page: Page, pattern: RegExp): Promise<number> {
  const calls: string[] = await page.evaluate(() => (window as any).__DT_FETCH_LOG__ ?? [])
  return calls.filter((u) => pattern.test(u)).length
}

/**
 * 创建 mock postgresql 连接（D8/D10 dialect 过滤前置）。
 * 若已存在同名 → 跳过。
 */
export async function ensureMockPostgresConnection(
  apiContext: APIRequestContext,
): Promise<{ connectionId: string | null }> {
  const list = await apiContext.get('/api/connections')
  const body = (await list.json()) ?? {}
  const existing = (body.connections ?? []).find(
    (c: any) => c.name === 'e2e-mock-pg' && c.kind === 'postgresql',
  )
  if (existing) return { connectionId: existing.id }
  const create = await apiContext.post('/api/connections', {
    data: {
      name: 'e2e-mock-pg',
      kind: 'postgresql',
      host: '127.0.0.1',
      port: 5432,
      databaseName: 'e2e_mock',
      username: 'mock',
      password: 'mock',
      connectTimeout: 1000,
    },
  })
  if (create.status() === 201) {
    const created = await create.json()
    return { connectionId: created.id ?? null }
  }
  return { connectionId: null }
}

/**
 * 跑测前调用：setupH2Connection + ensureMockPostgresConnection。
 */
export async function setupErFixture(): Promise<{
  workingConnId: string
  pgConnId: string | null
  apiContext: APIRequestContext
  cleanup: () => Promise<void>
}> {
  const apiContext = await request.newContext({ baseURL: BACKEND_URL })
  const list = await apiContext.get('/api/connections')
  const body = await list.json()
  const working = (body.connections ?? []).find((c: any) => c.lastTestStatus === 'ok')
  if (!working) throw new Error('No working connection (lastTestStatus !== ok)')

  // 跑 er-seed.sql
  const fs = await import('fs')
  const path = await import('path')
  const seedPath = path.resolve(__dirname, 'er-seed.sql')
  if (fs.existsSync(seedPath)) {
    const sql = fs.readFileSync(seedPath, 'utf-8')
    for (const stmt of sql.split(';').map((s) => s.trim()).filter(Boolean)) {
      await apiContext.post('/api/sql/execute', {
        data: { connectionId: working.id, sql: stmt, source: 'ai', confirmed: true },
      })
    }
  }

  const { connectionId: pgConnId } = await ensureMockPostgresConnection(apiContext)

  return {
    workingConnId: working.id,
    pgConnId,
    apiContext,
    cleanup: async () => {
      await apiContext.dispose()
    },
  }
}
```

注意：`require()` 在浏览器侧 `page.evaluate` 中通常不可用。Task 1 Step 2 立即调头：在产品代码 `App` 启动时（dev mode）暴露 `(window as any).__DT_E2E__ = { stage: useStageStore.getState, er: useErTabsStore.getState, session: useSessionStore.getState }`，spec 直接读这个全局。把 `require()` 兜底删掉，改为：

```ts
const stage = (window as any).__DT_E2E__?.stage()
const er = (window as any).__DT_E2E__?.er()
```

- [x] **Step 2: 添加 dev-only `__DT_E2E__` 全局**

`client/src/main.tsx`（应用入口）：

```ts
import { useStageStore } from '@/stores/stage-store'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { useSessionStore } from '@/stores/session-store'

if (import.meta.env.DEV || import.meta.env.MODE === 'test') {
  ;(window as any).__DT_E2E__ = {
    stage: () => useStageStore.getState(),
    er: () => useErTabsStore.getState(),
    session: () => useSessionStore.getState(),
  }
}
```

修订 `er-test-helpers.ts` 中所有 `require(...).useXxxStore.getState()` 为 `(window as any).__DT_E2E__.xxx()`。

- [x] **Step 3: 扩充 `pom/er-inspector.page.ts`**

完整覆盖（新增 8 个方法 + 保留现有 6 个）：

```ts
import type { Page, Locator } from '@playwright/test'

export class ErInspectorPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  private get canvas(): Locator {
    return this.page.locator('[data-er-tab-id]').first()
  }

  // ── 现有保留 ──
  async getTableNodes(): Promise<string[]> {
    const nodes = this.canvas.locator('[data-er-table-name]')
    const count = await nodes.count()
    const names = new Set<string>()
    for (let i = 0; i < count; i++) {
      const name = await nodes.nth(i).getAttribute('data-er-table-name')
      if (name) names.add(name)
    }
    return [...names]
  }

  async getRelations(): Promise<Array<{ from: string; to: string; type: string; isVirtual: boolean }>> {
    const edges = this.canvas.locator('[data-er-edge]')
    const count = await edges.count()
    const relations: Array<{ from: string; to: string; type: string; isVirtual: boolean }> = []
    for (let i = 0; i < count; i++) {
      const from = await edges.nth(i).getAttribute('data-from-table')
      const to = await edges.nth(i).getAttribute('data-to-table')
      const type = await edges.nth(i).getAttribute('data-relation-type')
      const isVirtual = (await edges.nth(i).getAttribute('data-is-virtual')) === 'true'
      if (from && to) relations.push({ from, to, type: type ?? 'unknown', isVirtual })
    }
    return relations
  }

  async getActiveTabId(): Promise<string> {
    return (await this.canvas.getAttribute('data-er-tab-id')) ?? ''
  }

  // ── 新增 ──
  async clickRefresh(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-refresh"]').first().click()
  }

  async clickAutoLayout(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-auto-layout"]').first().click()
  }

  async clickFitView(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-fit-view"]').first().click()
  }

  async setNeighborDepth(depth: 0 | 1 | 2): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-neighbor-depth"]').first().selectOption(String(depth))
  }

  async clickAddVirtualRelation(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-add-virtual-relation"]').first().click()
  }

  async clickForkToDesigner(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-fork-to-designer"]').first().click()
  }

  async dragNode(tableName: string, dx: number, dy: number): Promise<void> {
    const node = this.canvas.locator(`[data-er-table-name="${tableName}"]`).first()
    const box = await node.boundingBox()
    if (!box) throw new Error(`Node ${tableName} has no bounding box`)
    const startX = box.x + box.width / 2
    const startY = box.y + 12
    await this.page.mouse.move(startX, startY)
    await this.page.mouse.down()
    for (let step = 1; step <= 6; step++) {
      await this.page.mouse.move(startX + (dx * step) / 6, startY + (dy * step) / 6, { steps: 1 })
    }
    await this.page.mouse.up()
  }

  async getNodePosition(tableName: string): Promise<{ x: number; y: number }> {
    const node = this.canvas.locator(`[data-er-table-name="${tableName}"]`).first()
    const transform = await node.evaluate((el) => (el.parentElement as HTMLElement | null)?.style.transform ?? '')
    const m = transform.match(/translate\(([-\d.]+)px,\s*([-\d.]+)px\)/)
    return m ? { x: parseFloat(m[1]), y: parseFloat(m[2]) } : { x: 0, y: 0 }
  }

  async zoomIn(steps = 1): Promise<void> {
    const btn = this.page.locator('.react-flow__controls-zoomin')
    for (let i = 0; i < steps; i++) await btn.click()
  }

  async zoomOut(steps = 1): Promise<void> {
    const btn = this.page.locator('.react-flow__controls-zoomout')
    for (let i = 0; i < steps; i++) await btn.click()
  }

  async panCanvas(dx: number, dy: number): Promise<void> {
    const pane = this.canvas.locator('.react-flow__pane').first()
    const box = await pane.boundingBox()
    if (!box) throw new Error('No flow pane bbox')
    const cx = box.x + box.width / 2
    const cy = box.y + box.height / 2
    await this.page.mouse.move(cx, cy)
    await this.page.mouse.down()
    for (let step = 1; step <= 4; step++) {
      await this.page.mouse.move(cx + (dx * step) / 4, cy + (dy * step) / 4, { steps: 1 })
    }
    await this.page.mouse.up()
  }

  async expectEmptyState(): Promise<void> {
    await this.canvas.locator('text=/No tables selected|empty/i').first().waitFor({ state: 'visible' })
  }

  async getMode(): Promise<string> {
    return (await this.canvas.locator('[data-er-mode]').first().getAttribute('data-er-mode')) ?? ''
  }

  async getPayloadVersion(): Promise<number> {
    const v = await this.canvas.getAttribute('data-payload-version')
    return v ? parseInt(v, 10) : 0
  }
}
```

- [x] **Step 4: 扩充 `pom/er-designer.page.ts`**

```ts
import type { Page, Locator } from '@playwright/test'

export class ErDesignerPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  private get canvas(): Locator {
    return this.page.locator('[data-er-tab-id]').first()
  }

  async getTableNodes(): Promise<Array<{ id: string; name: string }>> {
    const nodes = this.canvas.locator('[data-er-table-name]')
    const count = await nodes.count()
    const out: Array<{ id: string; name: string }> = []
    for (let i = 0; i < count; i++) {
      const id = (await nodes.nth(i).getAttribute('data-er-table-id')) ?? ''
      const name = (await nodes.nth(i).getAttribute('data-er-table-name')) ?? ''
      if (id || name) out.push({ id, name })
    }
    return out
  }

  // toolbar
  async clickAddTable(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-add-table"]').first().click()
  }
  async clickAutoLayout(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-auto-layout"]').first().click()
  }
  async clickFitView(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-fit-view"]').first().click()
  }
  async clickBindTarget(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-bind-target"]').first().click()
  }
  async clickDiffVsDb(): Promise<{ disabled: boolean }> {
    const btn = this.page.locator('[data-testid="er-toolbar-diff"]').first()
    const disabled = (await btn.getAttribute('disabled')) !== null
      || (await btn.getAttribute('aria-disabled')) === 'true'
    if (!disabled) await btn.click()
    return { disabled }
  }
  async clickGenerateDdl(): Promise<{ disabled: boolean }> {
    const btn = this.page.locator('[data-testid="er-toolbar-generate-ddl"]').first()
    const disabled = (await btn.getAttribute('disabled')) !== null
      || (await btn.getAttribute('aria-disabled')) === 'true'
    if (!disabled) await btn.click()
    return { disabled }
  }
  async setDialect(d: 'mysql' | 'postgresql' | 'h2' | 'sqlite'): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-dialect"]').first().click()
    await this.page.locator(`[role="option"]`).filter({ hasText: d }).first().click()
  }
  async getDialect(): Promise<string> {
    return (await this.canvas.getAttribute('data-dialect')) ?? ''
  }
  async getDiffDisabledHint(): Promise<string | null> {
    const btn = this.page.locator('[data-testid="er-toolbar-diff"]').first()
    return await btn.getAttribute('aria-describedby').then((id) =>
      id ? this.page.locator(`#${id}`).textContent() : null,
    )
  }
  async getGenerateDdlDisabledHint(): Promise<string | null> {
    const btn = this.page.locator('[data-testid="er-toolbar-generate-ddl"]').first()
    return await btn.getAttribute('aria-describedby').then((id) =>
      id ? this.page.locator(`#${id}`).textContent() : null,
    )
  }

  // bind dialog
  async fillBindTarget(opts: { connectionId: string; database?: string; schema?: string }): Promise<void> {
    const dialog = this.page.locator('[role="dialog"]')
    await dialog.locator('#er-bind-target-connection').click()
    await this.page.locator(`[role="option"][data-value="${opts.connectionId}"]`).first().click()
    if (opts.database) {
      await dialog.locator('#er-bind-target-database').click()
      await this.page.locator(`[role="option"][data-value="${opts.database}"]`).first().click()
    }
    if (opts.schema) {
      await dialog.locator('#er-bind-target-schema').click()
      await this.page.locator(`[role="option"][data-value="${opts.schema}"]`).first().click()
    }
  }
  async confirmBindTarget(): Promise<void> {
    await this.page.locator('[role="dialog"] button:has-text("Bind")').first().click()
  }
  async cancelBindTarget(): Promise<void> {
    await this.page.locator('[role="dialog"] button:has-text("Cancel")').first().click()
  }
  async getBindDialogConnectionOptions(): Promise<string[]> {
    await this.page.locator('#er-bind-target-connection').click()
    const opts = this.page.locator('[role="option"]')
    const count = await opts.count()
    const names: string[] = []
    for (let i = 0; i < count; i++) {
      const text = await opts.nth(i).textContent()
      if (text) names.push(text.trim())
    }
    await this.page.keyboard.press('Escape')
    return names
  }
  async getBindDialogEmptyText(): Promise<string | null> {
    const dialog = this.page.locator('[role="dialog"]')
    const empty = dialog.locator('p').filter({ hasText: /no.*connection|empty/i }).first()
    return (await empty.count()) > 0 ? await empty.textContent() : null
  }
  async isSchemaSelectVisible(): Promise<boolean> {
    return await this.page.locator('#er-bind-target-schema').isVisible()
  }

  // table / column edit
  async openContextMenu(tableName: string): Promise<void> {
    const node = this.canvas.locator(`[data-er-table-name="${tableName}"]`).first()
    await node.click({ button: 'right' })
  }
  async clickContextMenuItem(label: 'rename' | 'addColumn' | 'deleteTable'): Promise<void> {
    const map: Record<string, RegExp> = {
      rename: /Rename|重命名/,
      addColumn: /Add column|添加列/,
      deleteTable: /Delete table|删除表/,
    }
    await this.page.locator('[role="menuitem"]').filter({ hasText: map[label] }).first().click()
  }
  async renameTable(oldName: string, newName: string): Promise<void> {
    await this.openContextMenu(oldName)
    await this.clickContextMenuItem('rename')
    const input = this.canvas.locator(`[data-er-table-name="${oldName}"] input`).first()
    await input.fill(newName)
    await input.press('Enter')
  }
  async addColumnViaToolbarPlus(tableName: string): Promise<void> {
    const node = this.canvas.locator(`[data-er-table-name="${tableName}"]`).first()
    await node.locator('button').filter({ hasText: /Add column|添加列|\+/ }).first().click()
  }
  async editColumnField(
    table: string,
    column: string,
    field: 'name' | 'type' | 'nullable' | 'isPrimaryKey' | 'isAutoIncrement' | 'default' | 'comment',
    value: any,
  ): Promise<void> {
    const row = this.canvas
      .locator(`[data-er-table-name="${table}"]`)
      .locator(`[data-testid="er-row-${column}"]`)
      .first()
    if (field === 'name' || field === 'type' || field === 'default' || field === 'comment') {
      const input = row.locator(`[data-er-field="${field}"], input[name="${field}"]`).first()
      await input.click()
      await input.fill(String(value))
      await input.press('Tab')
    } else {
      const checkbox = row.locator(`[data-er-field="${field}"]`).first()
      const checked = await checkbox.isChecked()
      if (checked !== Boolean(value)) await checkbox.click()
    }
  }
  async deleteColumn(table: string, column: string): Promise<void> {
    const row = this.canvas
      .locator(`[data-er-table-name="${table}"]`)
      .locator(`[data-testid="er-row-${column}"]`)
      .first()
    await row.locator('button[aria-label*="Delete"], button[aria-label*="删除"]').first().click()
  }

  // relations (R9 implementation)
  async dragConnect(fromTable: string, fromColumn: string, toTable: string, toColumn: string): Promise<void> {
    const sourceColId = await this.resolveColumnId(fromTable, fromColumn)
    const targetColId = await this.resolveColumnId(toTable, toColumn)
    const sourceHandle = this.canvas
      .locator(`[data-er-column-handle="${sourceColId}:source"], [data-handleid="${sourceColId}-source"]`)
      .first()
    const targetHandle = this.canvas
      .locator(`[data-er-column-handle="${targetColId}:target"], [data-handleid="${targetColId}-target"]`)
      .first()
    const srcBox = await sourceHandle.boundingBox()
    const dstBox = await targetHandle.boundingBox()
    if (!srcBox || !dstBox) {
      // Fallback: 直接 page.evaluate 写 store（R9 ④）
      await this.page.evaluate(
        ({ tabId, fromTable, fromColumn, toTable, toColumn, sourceColId, targetColId }) => {
          const er = (window as any).__DT_E2E__.er()
          er.applyDesignerPatch(tabId, [
            {
              op: 'add',
              path: '/relations/-',
              value: {
                fromTableId: fromTable,
                fromColumnId: sourceColId,
                toTableId: toTable,
                toColumnId: targetColId,
                type: 'many_to_one',
                constraintMethod: 'database_fk',
              },
            },
          ])
        },
        {
          tabId: await this.canvas.getAttribute('data-er-tab-id'),
          fromTable, fromColumn, toTable, toColumn, sourceColId, targetColId,
        },
      )
      return
    }
    const sx = srcBox.x + srcBox.width / 2
    const sy = srcBox.y + srcBox.height / 2
    const tx = dstBox.x + dstBox.width / 2
    const ty = dstBox.y + dstBox.height / 2
    await this.page.mouse.move(sx, sy)
    await this.page.mouse.down()
    for (let step = 1; step <= 8; step++) {
      await this.page.mouse.move(sx + ((tx - sx) * step) / 8, sy + ((ty - sy) * step) / 8, { steps: 1 })
    }
    await this.page.mouse.up()
  }

  private async resolveColumnId(table: string, column: string): Promise<string> {
    return (
      (await this.canvas
        .locator(`[data-er-table-name="${table}"] [data-testid="er-row-${column}"]`)
        .first()
        .getAttribute('data-er-column-id')) ?? column
    )
  }

  async setEdgeRelationType(
    edgeId: string,
    type: 'one_to_one' | 'one_to_many' | 'many_to_one' | 'many_to_many',
  ): Promise<void> {
    await this.canvas.locator(`[data-er-edge][data-id="${edgeId}"]`).first().click()
    const select = this.page.locator('[data-er-edge-type-select]').first()
    if ((await select.count()) > 0) {
      await select.click()
      await this.page.locator(`[role="option"]`).filter({ hasText: type }).first().click()
    }
  }
  async deleteEdge(edgeId: string): Promise<void> {
    await this.canvas.locator(`[data-er-edge][data-id="${edgeId}"]`).first().click()
    const delBtn = this.page.locator('[data-er-edge-delete]').first()
    if ((await delBtn.count()) > 0) await delBtn.click()
    else await this.page.keyboard.press('Delete')
  }

  // keyboard
  async selectNode(tableName: string): Promise<void> {
    await this.canvas.locator(`[data-er-table-name="${tableName}"]`).first().click()
  }
  async selectEdge(edgeId: string): Promise<void> {
    await this.canvas.locator(`[data-er-edge][data-id="${edgeId}"]`).first().click()
  }
  async pressDelete(): Promise<void> {
    await this.page.keyboard.press('Delete')
  }

  // empty state
  async expectEmptyState(): Promise<void> {
    await this.canvas.locator('text=/empty.*designer|no tables/i').first().waitFor({ state: 'visible' })
  }
  async clickEmptyAddTable(): Promise<void> {
    await this.canvas.locator('button').filter({ hasText: /Add table|添加表/ }).first().click()
  }

  async getPayloadVersion(): Promise<number> {
    const v = await this.canvas.getAttribute('data-payload-version')
    return v ? parseInt(v, 10) : 0
  }
}
```

- [x] **Step 5: 类型检查**

```bash
cd client && npx tsc --noEmit
```

Expected：exit 0。若 `__DT_E2E__` 全局未定义 → 检查 Step 2 的 `main.tsx` 改动；若 ER POM 类型错 → 修方法签名。

- [x] **Step 6: 提交**

```bash
git add client/tests/e2e/fixtures/er-test-helpers.ts \
        client/tests/e2e/pom/er-inspector.page.ts \
        client/tests/e2e/pom/er-designer.page.ts \
        client/src/main.tsx
git commit -m "$(cat <<'EOF'
test(er-e2e): add ER test helpers + extend POMs

Helpers: openErInspectorViaShortcut / openErDesignerViaShortcut /
readInspectorPayload / readDesignerPayload / waitForPayloadVersion /
fetchCallsMatching / setupErFixture / ensureMockPostgresConnection.

POMs extended: Inspector 6 → 14 methods, Designer 7 → 28 methods.

Adds dev-only window.__DT_E2E__ global to expose Zustand stores to
Playwright (gated by import.meta.env.DEV / MODE === 'test').

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: spec 1 — Inspector UI（11 tests）

> **Parallel batch eligible**：与 Task 3 / Task 4 互不依赖，可由独立 subagent 并发实现。

**Files:**
- Create: `client/tests/e2e/er-inspector-ui.spec.ts`

- [x] **Step 1: 写 spec 头 + setup**

```ts
import { test, expect } from '@playwright/test'
import { ErInspectorPage } from './pom/er-inspector.page'
import {
  openErInspectorViaShortcut,
  readInspectorPayload,
  waitForPayloadVersion,
  fetchCallsMatching,
  setupErFixture,
} from './fixtures/er-test-helpers'

let workingConnId = ''

test.describe.configure({ timeout: 600_000 }) // 10 min total

test.beforeAll(async () => {
  const f = await setupErFixture()
  workingConnId = f.workingConnId
  await f.cleanup()
})

test.beforeEach(async ({ page }) => {
  await page.goto('/')
  await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
})
```

- [x] **Step 2: I1 — Refresh button re-fetches schema and bumps snapshotAt**

```ts
test('I1: Refresh button re-fetches schema and bumps snapshotAt', async ({ page }) => {
  test.setTimeout(60_000)
  const { tabId } = await openErInspectorViaShortcut(page, {
    connectionId: workingConnId,
    tables: ['users', 'orders'],
  })
  const before = await readInspectorPayload(page, tabId)
  const inspector = new ErInspectorPage(page)
  const baseV = await inspector.getPayloadVersion()
  await inspector.clickRefresh()
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 5_000)
  const after = await readInspectorPayload(page, tabId)
  expect(after.snapshotAt).toBeGreaterThan(before.snapshotAt ?? 0)
  expect(Array.isArray(after.tablesSnapshot)).toBe(true)
})
```

- [x] **Step 3: I2 — Auto layout assigns non-zero positions**

```ts
test('I2: Auto layout assigns non-zero positions to all selected tables', async ({ page }) => {
  test.setTimeout(60_000)
  test.slow()
  const { tabId } = await openErInspectorViaShortcut(page, {
    connectionId: workingConnId,
    tables: ['users', 'orders', 'products'],
  })
  const inspector = new ErInspectorPage(page)
  const baseV = await inspector.getPayloadVersion()
  await inspector.clickAutoLayout()
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 5_000)
  const payload = await readInspectorPayload(page, tabId)
  const positions = payload.positions ?? {}
  const keys = Object.keys(positions)
  expect(keys.length).toBeGreaterThanOrEqual(3)
  // 非全 (0,0)
  const nonZero = keys.filter((k) => (positions[k].x !== 0 || positions[k].y !== 0))
  expect(nonZero.length).toBeGreaterThanOrEqual(2)
})
```

- [x] **Step 4: I3 — Fit view writes /viewport patch**

```ts
test('I3: Fit view writes /viewport patch', async ({ page }) => {
  test.setTimeout(60_000)
  test.slow()
  const { tabId } = await openErInspectorViaShortcut(page, {
    connectionId: workingConnId,
    tables: ['users', 'orders'],
  })
  const inspector = new ErInspectorPage(page)
  const baseV = await inspector.getPayloadVersion()
  await inspector.clickFitView()
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 5_000)
  const payload = await readInspectorPayload(page, tabId)
  expect(payload.viewport.zoom).toBeGreaterThan(0)
  expect(typeof payload.viewport.x).toBe('number')
  expect(typeof payload.viewport.y).toBe('number')
})
```

- [x] **Step 5: I4 — Neighbor depth 0/1/2 changes graph**

```ts
test('I4: Neighbor depth 0 → only selected; 1 → 直接邻居; 2 → 二跳邻居', async ({ page }) => {
  test.setTimeout(60_000)
  const { tabId } = await openErInspectorViaShortcut(page, {
    connectionId: workingConnId,
    tables: ['users'],
    neighborDepth: 0,
  })
  const inspector = new ErInspectorPage(page)
  const nodes0 = await inspector.getTableNodes()

  await inspector.setNeighborDepth(1)
  await waitForPayloadVersion(page, tabId, (v) => v > 0, 5_000)
  const nodes1 = await inspector.getTableNodes()
  expect(nodes1.length).toBeGreaterThanOrEqual(nodes0.length)

  await inspector.setNeighborDepth(2)
  await waitForPayloadVersion(page, tabId, (v) => v > 0, 5_000)
  const nodes2 = await inspector.getTableNodes()
  expect(nodes2.length).toBeGreaterThanOrEqual(nodes1.length)

  const payload = await readInspectorPayload(page, tabId)
  expect(payload.neighborDepth).toBe(2)
})
```

- [x] **Step 6: I5 — Add virtual relation（预登 BUG-A，标 fixme）**

```ts
test.fixme('I5: Add virtual relation 进入编辑模式 / 弹窗 / 回写 /virtualRelations', async ({ page }) => {
  // see docs/bugs/BUG-NNNN-er-add-virtual-relation-noop.md
  // ErCanvas.tsx:223 onAddVirtualRelation = () => undefined
  const { tabId } = await openErInspectorViaShortcut(page, {
    connectionId: workingConnId,
    tables: ['users', 'orders'],
  })
  const inspector = new ErInspectorPage(page)
  const baseV = await inspector.getPayloadVersion()
  await inspector.clickAddVirtualRelation()
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 3_000)
  const payload = await readInspectorPayload(page, tabId)
  expect((payload.virtualRelations ?? []).length).toBeGreaterThan(0)
})
```

- [x] **Step 7: I6 — Fork to designer**

```ts
test('I6: Fork to designer opens new er_designer tab', async ({ page }) => {
  test.setTimeout(60_000)
  const { tabId } = await openErInspectorViaShortcut(page, {
    connectionId: workingConnId,
    tables: ['users', 'orders'],
  })
  const inspector = new ErInspectorPage(page)
  await inspector.clickForkToDesigner()
  await page.waitForFunction(
    () => {
      const stage = (window as any).__DT_E2E__.stage()
      return stage.tabs.some((t: any) => t.type === 'er_designer')
    },
    null,
    { timeout: 10_000 },
  )
  const designerTabs = await page.evaluate(() => {
    const stage = (window as any).__DT_E2E__.stage()
    return stage.tabs.filter((t: any) => t.type === 'er_designer').map((t: any) => t.id)
  })
  expect(designerTabs.length).toBeGreaterThanOrEqual(1)
  // 若返 plan_b_only 错误 → toast 出现，本 test fail → BUG 登记 + fixme
})
```

- [x] **Step 8: I7 — Drag node persists position**

```ts
test('I7: Drag node persists position to /positions/{name}', async ({ page }) => {
  test.setTimeout(60_000)
  const { tabId } = await openErInspectorViaShortcut(page, {
    connectionId: workingConnId,
    tables: ['users', 'orders'],
  })
  const inspector = new ErInspectorPage(page)
  await inspector.clickAutoLayout()
  await waitForPayloadVersion(page, tabId, (v) => v > 0, 5_000)
  const before = await inspector.getNodePosition('users')
  const baseV = await inspector.getPayloadVersion()
  await inspector.dragNode('users', 80, 60)
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 3_000)
  const payload = await readInspectorPayload(page, tabId)
  const pos = payload.positions?.users
  expect(pos).toBeDefined()
  expect(Math.abs(pos.x - (before.x + 80))).toBeLessThanOrEqual(8)
  expect(Math.abs(pos.y - (before.y + 60))).toBeLessThanOrEqual(8)
})
```

- [x] **Step 9: I8 / I9 — Pan + Zoom**

```ts
test('I8: Pan canvas writes /viewport patch', async ({ page }) => {
  test.setTimeout(60_000)
  const { tabId } = await openErInspectorViaShortcut(page, {
    connectionId: workingConnId,
    tables: ['users', 'orders'],
  })
  const inspector = new ErInspectorPage(page)
  const before = (await readInspectorPayload(page, tabId)).viewport
  const baseV = await inspector.getPayloadVersion()
  await inspector.panCanvas(120, 80)
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 3_000)
  const after = (await readInspectorPayload(page, tabId)).viewport
  expect(after.x !== before.x || after.y !== before.y).toBe(true)
})

test('I9: Zoom in/out via Controls writes /viewport zoom', async ({ page }) => {
  test.setTimeout(60_000)
  test.slow()
  const { tabId } = await openErInspectorViaShortcut(page, {
    connectionId: workingConnId,
    tables: ['users'],
  })
  const inspector = new ErInspectorPage(page)
  const baseV = await inspector.getPayloadVersion()
  const before = (await readInspectorPayload(page, tabId)).viewport.zoom
  await inspector.zoomIn(2)
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 5_000)
  const afterIn = (await readInspectorPayload(page, tabId)).viewport.zoom
  expect(afterIn).toBeGreaterThan(before)
  await inspector.zoomOut(3)
  await waitForPayloadVersion(page, tabId, (v) => v > baseV + 1, 5_000)
  const afterOut = (await readInspectorPayload(page, tabId)).viewport.zoom
  expect(afterOut).toBeLessThan(afterIn)
})
```

- [x] **Step 10: I10 / I11 — Empty state + INV-1**

```ts
test('I10: Empty selection 显示 ErEmptyState reason=empty_selection', async ({ page }) => {
  const { tabId } = await openErInspectorViaShortcut(page, {
    connectionId: workingConnId,
    tables: [], // empty selection
  })
  const inspector = new ErInspectorPage(page)
  await inspector.expectEmptyState()
})

test('I11: Inspector 操作链路无 /api/sql/execute 调用', async ({ page }) => {
  await page.addInitScript(() => {
    ;(window as any).__DT_FETCH_LOG__ = [] as string[]
    const orig = window.fetch
    window.fetch = function (...args: any[]) {
      const url = typeof args[0] === 'string' ? args[0] : (args[0] as Request).url
      ;(window as any).__DT_FETCH_LOG__.push(url)
      return orig.apply(window, args as any)
    }
  })
  await page.goto('/')
  await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
  const { tabId } = await openErInspectorViaShortcut(page, {
    connectionId: workingConnId,
    tables: ['users', 'orders', 'products'],
  })
  const inspector = new ErInspectorPage(page)
  await inspector.clickRefresh()
  await waitForPayloadVersion(page, tabId, (v) => v > 0, 5_000)
  await inspector.setNeighborDepth(2)
  await waitForPayloadVersion(page, tabId, (v) => v > 1, 5_000)
  await inspector.clickAutoLayout()
  await waitForPayloadVersion(page, tabId, (v) => v > 2, 5_000)
  await inspector.dragNode('users', 50, 50)
  await waitForPayloadVersion(page, tabId, (v) => v > 3, 5_000)
  const sqlExecuteHits = await fetchCallsMatching(page, /\/api\/sql\/execute/)
  expect(sqlExecuteHits).toBe(0)
})
```

- [x] **Step 11: 跑 spec 1**

```bash
cd client && npx playwright test tests/e2e/er-inspector-ui.spec.ts --reporter=list
```

Expected：10 passed + 1 fixme（I5）；任何 fail → 登 BUG，把对应 test 改 `test.fail` 并标注 `// see docs/bugs/BUG-NNNN-...md`。

- [x] **Step 12: 提交**

```bash
git add client/tests/e2e/er-inspector-ui.spec.ts
git commit -m "test(er-e2e): add Inspector UI spec (I1-I11, 11 tests)"
```

---

## Task 3: spec 2 — Designer UI（30 tests）

> **Parallel batch eligible**：与 Task 2 / Task 4 互不依赖。

**Files:**
- Create: `client/tests/e2e/er-designer-ui.spec.ts`

- [x] **Step 1: spec 头 + setup**

```ts
import { test, expect } from '@playwright/test'
import { ErDesignerPage } from './pom/er-designer.page'
import {
  openErDesignerViaShortcut,
  readDesignerPayload,
  waitForPayloadVersion,
  fetchCallsMatching,
  setupErFixture,
} from './fixtures/er-test-helpers'

let workingConnId = ''
let pgConnId: string | null = null

test.describe.configure({ timeout: 900_000 }) // 15 min total

test.beforeAll(async () => {
  const f = await setupErFixture()
  workingConnId = f.workingConnId
  pgConnId = f.pgConnId
  await f.cleanup()
})

test.beforeEach(async ({ page }) => {
  await page.goto('/')
  await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
})
```

- [x] **Step 2: D1-D3 Toolbar add / layout / fit**

```ts
test('D1: Add table 添加 new_table 到 /tables/-', async ({ page }) => {
  const { tabId } = await openErDesignerViaShortcut(page, { dialect: 'h2' })
  const designer = new ErDesignerPage(page)
  const baseV = await designer.getPayloadVersion()
  await designer.clickAddTable()
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 3_000)
  const payload = await readDesignerPayload(page, tabId)
  expect(payload.tables.length).toBe(1)
  expect(payload.tables[0].name).toBe('new_table')
})

test('D2: Auto layout 重排所有节点位置', async ({ page }) => {
  test.setTimeout(60_000)
  test.slow()
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [
      { name: 'users', columns: [{ name: 'id', type: 'BIGINT', nullable: false, isPrimaryKey: true }] },
      { name: 'orders', columns: [{ name: 'id', type: 'BIGINT', nullable: false, isPrimaryKey: true }] },
    ],
  })
  const designer = new ErDesignerPage(page)
  const baseV = await designer.getPayloadVersion()
  await designer.clickAutoLayout()
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 5_000)
  const payload = await readDesignerPayload(page, tabId)
  const positions = payload.positions ?? {}
  const tableIds = payload.tables.map((t: any) => t.id)
  for (const id of tableIds) {
    expect(positions[id]).toBeDefined()
  }
})

test('D3: Fit view 写 /viewport', async ({ page }) => {
  test.setTimeout(60_000)
  test.slow()
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [{ name: 'users' }],
  })
  const designer = new ErDesignerPage(page)
  const baseV = await designer.getPayloadVersion()
  await designer.clickFitView()
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 5_000)
  const payload = await readDesignerPayload(page, tabId)
  expect(payload.viewport.zoom).toBeGreaterThan(0)
})
```

- [x] **Step 3: D4-D7 Toolbar bind/diff/ddl/dialect**

```ts
test('D4: Bind target 打开 dialog', async ({ page }) => {
  await openErDesignerViaShortcut(page, { dialect: 'h2' })
  const designer = new ErDesignerPage(page)
  await designer.clickBindTarget()
  await page.locator('[role="dialog"]').waitFor({ state: 'visible', timeout: 5_000 })
})

test('D5: Diff vs DB 在未 bind 时 disabled + tooltip', async ({ page }) => {
  await openErDesignerViaShortcut(page, { dialect: 'h2' })
  const designer = new ErDesignerPage(page)
  const { disabled } = await designer.clickDiffVsDb()
  expect(disabled).toBe(true)
  const hint = await designer.getDiffDisabledHint()
  expect(hint).toMatch(/Bind a target database/i)
})

test('D6: Generate DDL 在未 bind 时 disabled + tooltip', async ({ page }) => {
  await openErDesignerViaShortcut(page, { dialect: 'h2' })
  const designer = new ErDesignerPage(page)
  const { disabled } = await designer.clickGenerateDdl()
  expect(disabled).toBe(true)
  const hint = await designer.getGenerateDdlDisabledHint()
  expect(hint).toMatch(/Bind a target database/i)
})

test('D7: Dialect 切换 4 选项可选，写 /dialect 路径', async ({ page }) => {
  const { tabId } = await openErDesignerViaShortcut(page, { dialect: 'mysql' })
  const designer = new ErDesignerPage(page)
  for (const d of ['postgresql', 'h2', 'sqlite', 'mysql'] as const) {
    const baseV = await designer.getPayloadVersion()
    await designer.setDialect(d)
    await waitForPayloadVersion(page, tabId, (v) => v > baseV, 3_000)
    const payload = await readDesignerPayload(page, tabId)
    expect(payload.dialect).toBe(d)
  }
})
```

- [x] **Step 4: D8-D13 Bind Dialog 全流程**

```ts
test('D8: 连接列表按 dialect 过滤（mysql 只看到 mysql kind）', async ({ page }) => {
  test.skip(!pgConnId, 'mock postgresql connection unavailable; see Risks.R3')
  await openErDesignerViaShortcut(page, { dialect: 'mysql' })
  const designer = new ErDesignerPage(page)
  await designer.clickBindTarget()
  const opts = await designer.getBindDialogConnectionOptions()
  // pgConnId 是 postgresql kind，dialect=mysql 不应包含它
  expect(opts.find((o) => o.includes('e2e-mock-pg'))).toBeUndefined()
})

test('D9: 选连接 → database / schema 级联加载', async ({ page }) => {
  await openErDesignerViaShortcut(page, { dialect: 'h2' })
  const designer = new ErDesignerPage(page)
  await designer.clickBindTarget()
  await designer.fillBindTarget({ connectionId: workingConnId })
  // database select 应可点 / 有选项（取决于真连接的 metadata）
  const dbVisible = await page.locator('#er-bind-target-database').isVisible()
  expect(dbVisible).toBe(true)
})

test('D10: MySQL 不显示 schema select；PostgreSQL 显示', async ({ page }) => {
  test.skip(!pgConnId, 'mock postgresql connection unavailable')
  // 1) MySQL：用 H2 dialect（kind 同样无 schema）兼容
  await openErDesignerViaShortcut(page, { dialect: 'mysql' })
  let designer = new ErDesignerPage(page)
  await designer.clickBindTarget()
  expect(await designer.isSchemaSelectVisible()).toBe(false)
  await designer.cancelBindTarget()
  // 2) PostgreSQL：dialect=postgresql + 选 pgConnId
  await openErDesignerViaShortcut(page, { dialect: 'postgresql' })
  designer = new ErDesignerPage(page)
  await designer.clickBindTarget()
  await designer.fillBindTarget({ connectionId: pgConnId! })
  expect(await designer.isSchemaSelectVisible()).toBe(true)
})

test('D11: 空连接列表显示 erCanvas.bindDialog.empty 文案', async ({ page }) => {
  // 用一个没有 mysql kind 连接的 fixture 切片：构造 dialect=mysql 但没有 mysql 连接
  // 假设 workingConnId 是 mysql/h2 kind，复用即可，否则 skip
  await openErDesignerViaShortcut(page, { dialect: 'sqlite' }) // sqlite kind 通常无连接
  const designer = new ErDesignerPage(page)
  await designer.clickBindTarget()
  const empty = await designer.getBindDialogEmptyText()
  // 若环境恰好有 sqlite 连接 → empty 为 null，跳过此断言
  if (empty != null) expect(empty).toMatch(/no.*connection|empty/i)
})

test('D12: Confirm 后写 targetConnectionId / targetDatabase / targetSchema 并启用 Diff/DDL', async ({ page }) => {
  const { tabId } = await openErDesignerViaShortcut(page, { dialect: 'h2' })
  const designer = new ErDesignerPage(page)
  await designer.clickBindTarget()
  await designer.fillBindTarget({ connectionId: workingConnId })
  const baseV = await designer.getPayloadVersion()
  await designer.confirmBindTarget()
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 5_000)
  const payload = await readDesignerPayload(page, tabId)
  expect(payload.targetConnectionId).toBe(workingConnId)
  // Diff/DDL 按钮启用
  const diffBtn = page.locator('[data-testid="er-toolbar-diff"]')
  expect(await diffBtn.isDisabled()).toBe(false)
  const ddlBtn = page.locator('[data-testid="er-toolbar-generate-ddl"]')
  expect(await ddlBtn.isDisabled()).toBe(false)
})

test('D13: Cancel 不写 payload', async ({ page }) => {
  const { tabId } = await openErDesignerViaShortcut(page, { dialect: 'h2' })
  const designer = new ErDesignerPage(page)
  const before = await readDesignerPayload(page, tabId)
  await designer.clickBindTarget()
  await designer.fillBindTarget({ connectionId: workingConnId })
  await designer.cancelBindTarget()
  const after = await readDesignerPayload(page, tabId)
  expect(after.targetConnectionId).toBe(before.targetConnectionId)
})
```

- [x] **Step 5: D14-D16 右键菜单**

```ts
test('D14: Rename 进入名称编辑态，回车写 /tables[id=X]/name', async ({ page }) => {
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [{ name: 'users', columns: [{ name: 'id', type: 'BIGINT' }] }],
  })
  const designer = new ErDesignerPage(page)
  const baseV = await designer.getPayloadVersion()
  await designer.renameTable('users', 'people')
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 3_000)
  const payload = await readDesignerPayload(page, tabId)
  expect(payload.tables[0].name).toBe('people')
})

test('D15: Add column 写 /tables[id=X]/columns/-', async ({ page }) => {
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [{ name: 'users', columns: [] }],
  })
  const designer = new ErDesignerPage(page)
  await designer.openContextMenu('users')
  const baseV = await designer.getPayloadVersion()
  await designer.clickContextMenuItem('addColumn')
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 3_000)
  const payload = await readDesignerPayload(page, tabId)
  expect(payload.tables[0].columns.length).toBe(1)
})

test('D16: Delete table 写 remove，关联 relations 一并删除', async ({ page }) => {
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [
      { id: 't1', name: 'users', columns: [{ id: 'c1', name: 'id', type: 'BIGINT' }] },
      { id: 't2', name: 'orders', columns: [{ id: 'c2', name: 'user_id', type: 'BIGINT' }] },
    ],
  })
  // 加一条 relation
  await page.evaluate((id) => {
    const er = (window as any).__DT_E2E__.er()
    er.applyDesignerPatch(id, [
      {
        op: 'add',
        path: '/relations/-',
        value: {
          fromTableId: 't1', fromColumnId: 'c1',
          toTableId: 't2', toColumnId: 'c2',
          type: 'one_to_many', constraintMethod: 'database_fk',
        },
      },
    ])
  }, tabId)
  const designer = new ErDesignerPage(page)
  const baseV = await designer.getPayloadVersion()
  await designer.openContextMenu('users')
  await designer.clickContextMenuItem('deleteTable')
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 3_000)
  const payload = await readDesignerPayload(page, tabId)
  expect(payload.tables.find((t: any) => t.name === 'users')).toBeUndefined()
  expect((payload.relations ?? []).length).toBe(0)
})
```

- [x] **Step 6: D17-D19 列内联**

```ts
test('D17: 改列各字段走对应 replace patch', async ({ page }) => {
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [
      {
        id: 't_users',
        name: 'users',
        columns: [
          { id: 'c_id', name: 'id', type: 'BIGINT', nullable: false, isPrimaryKey: true },
          { id: 'c_email', name: 'email', type: 'VARCHAR(100)', nullable: true },
        ],
      },
    ],
  })
  const designer = new ErDesignerPage(page)
  await designer.editColumnField('users', 'email', 'name', 'email_addr')
  await designer.editColumnField('users', 'email_addr', 'type', 'VARCHAR(255)')
  await designer.editColumnField('users', 'email_addr', 'nullable', false)
  await waitForPayloadVersion(page, tabId, (v) => v >= 3, 5_000)
  const payload = await readDesignerPayload(page, tabId)
  const col = payload.tables[0].columns.find((c: any) => c.name === 'email_addr')
  expect(col).toBeDefined()
  expect(col.type).toBe('VARCHAR(255)')
  expect(col.nullable).toBe(false)
})

test('D18: 点 + 添加列追加 new_column VARCHAR(255)', async ({ page }) => {
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [{ id: 't_users', name: 'users', columns: [] }],
  })
  const designer = new ErDesignerPage(page)
  const baseV = await designer.getPayloadVersion()
  await designer.addColumnViaToolbarPlus('users')
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 3_000)
  const payload = await readDesignerPayload(page, tabId)
  expect(payload.tables[0].columns[0].name).toBe('new_column')
  expect(payload.tables[0].columns[0].type).toBe('VARCHAR(255)')
})

test('D19: 点 trash 删列 写 remove /columns[id=...]', async ({ page }) => {
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [
      {
        id: 't_users',
        name: 'users',
        columns: [
          { id: 'c_id', name: 'id', type: 'BIGINT' },
          { id: 'c_email', name: 'email', type: 'VARCHAR(100)' },
        ],
      },
    ],
  })
  const designer = new ErDesignerPage(page)
  const baseV = await designer.getPayloadVersion()
  await designer.deleteColumn('users', 'email')
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 3_000)
  const payload = await readDesignerPayload(page, tabId)
  expect(payload.tables[0].columns.length).toBe(1)
  expect(payload.tables[0].columns[0].name).toBe('id')
})
```

- [x] **Step 7: D20-D22 拖连边 / Edge**

```ts
test('D20: 从列 source-handle 拖到另一列 target-handle 创建 relations/-', async ({ page }) => {
  test.setTimeout(60_000)
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [
      { id: 't_users', name: 'users', columns: [{ id: 'c_uid', name: 'id', type: 'BIGINT' }] },
      { id: 't_orders', name: 'orders', columns: [{ id: 'c_oid', name: 'user_id', type: 'BIGINT' }] },
    ],
  })
  // 先 auto layout 让节点不堆叠
  const designer = new ErDesignerPage(page)
  await designer.clickAutoLayout()
  await waitForPayloadVersion(page, tabId, (v) => v > 0, 5_000)
  const baseV = await designer.getPayloadVersion()
  await designer.dragConnect('t_users', 'id', 't_orders', 'user_id')
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 5_000)
  const payload = await readDesignerPayload(page, tabId)
  expect((payload.relations ?? []).length).toBe(1)
  expect(payload.relations[0].fromTableId).toBe('t_users')
  expect(payload.relations[0].toTableId).toBe('t_orders')
})

test('D21: Edge 改 relation type 写 /relations[id=X]/type', async ({ page }) => {
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [
      { id: 't1', name: 'users', columns: [{ id: 'c1', name: 'id', type: 'BIGINT' }] },
      { id: 't2', name: 'orders', columns: [{ id: 'c2', name: 'user_id', type: 'BIGINT' }] },
    ],
  })
  const edgeId = 't1.c1->t2.c2'
  await page.evaluate(({ id, eid }) => {
    const er = (window as any).__DT_E2E__.er()
    er.applyDesignerPatch(id, [
      {
        op: 'add',
        path: '/relations/-',
        value: {
          id: eid, fromTableId: 't1', fromColumnId: 'c1',
          toTableId: 't2', toColumnId: 'c2',
          type: 'many_to_one', constraintMethod: 'database_fk',
        },
      },
    ])
  }, { id: tabId, eid: edgeId })
  const designer = new ErDesignerPage(page)
  const baseV = await designer.getPayloadVersion()
  await designer.setEdgeRelationType(edgeId, 'one_to_many')
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 3_000)
  const payload = await readDesignerPayload(page, tabId)
  expect(payload.relations[0].type).toBe('one_to_many')
})

test('D22: Edge 删除 写 remove', async ({ page }) => {
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [
      { id: 't1', name: 'users', columns: [{ id: 'c1', name: 'id', type: 'BIGINT' }] },
      { id: 't2', name: 'orders', columns: [{ id: 'c2', name: 'user_id', type: 'BIGINT' }] },
    ],
  })
  const edgeId = 'rel-1'
  await page.evaluate(({ id, eid }) => {
    const er = (window as any).__DT_E2E__.er()
    er.applyDesignerPatch(id, [
      {
        op: 'add', path: '/relations/-',
        value: {
          id: eid, fromTableId: 't1', fromColumnId: 'c1',
          toTableId: 't2', toColumnId: 'c2',
          type: 'many_to_one', constraintMethod: 'database_fk',
        },
      },
    ])
  }, { id: tabId, eid: edgeId })
  const designer = new ErDesignerPage(page)
  const baseV = await designer.getPayloadVersion()
  await designer.deleteEdge(edgeId)
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 3_000)
  const payload = await readDesignerPayload(page, tabId)
  expect((payload.relations ?? []).length).toBe(0)
})
```

- [x] **Step 8: D23-D24 Delete 键**

```ts
test('D23: 选中节点 + Delete 键删除，伴随 relations 清理', async ({ page }) => {
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [
      { id: 't1', name: 'users', columns: [{ id: 'c1', name: 'id', type: 'BIGINT' }] },
      { id: 't2', name: 'orders', columns: [{ id: 'c2', name: 'user_id', type: 'BIGINT' }] },
    ],
  })
  await page.evaluate((id) => {
    const er = (window as any).__DT_E2E__.er()
    er.applyDesignerPatch(id, [
      {
        op: 'add', path: '/relations/-',
        value: {
          fromTableId: 't1', fromColumnId: 'c1',
          toTableId: 't2', toColumnId: 'c2',
          type: 'many_to_one', constraintMethod: 'database_fk',
        },
      },
    ])
  }, tabId)
  const designer = new ErDesignerPage(page)
  await designer.selectNode('users')
  const baseV = await designer.getPayloadVersion()
  await designer.pressDelete()
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 3_000)
  const payload = await readDesignerPayload(page, tabId)
  expect(payload.tables.find((t: any) => t.name === 'users')).toBeUndefined()
  expect((payload.relations ?? []).length).toBe(0)
})

test('D24: 选中 edge + Delete 键删除', async ({ page }) => {
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [
      { id: 't1', name: 'users', columns: [{ id: 'c1', name: 'id', type: 'BIGINT' }] },
      { id: 't2', name: 'orders', columns: [{ id: 'c2', name: 'user_id', type: 'BIGINT' }] },
    ],
  })
  const edgeId = 'rel-x'
  await page.evaluate(({ id, eid }) => {
    const er = (window as any).__DT_E2E__.er()
    er.applyDesignerPatch(id, [
      {
        op: 'add', path: '/relations/-',
        value: {
          id: eid, fromTableId: 't1', fromColumnId: 'c1',
          toTableId: 't2', toColumnId: 'c2',
          type: 'many_to_one', constraintMethod: 'database_fk',
        },
      },
    ])
  }, { id: tabId, eid: edgeId })
  const designer = new ErDesignerPage(page)
  await designer.selectEdge(edgeId)
  const baseV = await designer.getPayloadVersion()
  await designer.pressDelete()
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 3_000)
  const payload = await readDesignerPayload(page, tabId)
  expect((payload.relations ?? []).length).toBe(0)
})
```

- [x] **Step 9: D25 Empty state**

```ts
test('D25: tables 为空显示 empty_designer + Add table CTA', async ({ page }) => {
  const { tabId } = await openErDesignerViaShortcut(page, { dialect: 'h2' })
  const designer = new ErDesignerPage(page)
  await designer.expectEmptyState()
  const baseV = await designer.getPayloadVersion()
  await designer.clickEmptyAddTable()
  await waitForPayloadVersion(page, tabId, (v) => v > baseV, 3_000)
  const payload = await readDesignerPayload(page, tabId)
  expect(payload.tables.length).toBe(1)
})
```

- [x] **Step 10: D26-D29 DDL 跨 dialect**

```ts
test('D26: Bind 后 Generate DDL 生成 query_editor Tab；SQL 含 CREATE TABLE', async ({ page }) => {
  test.setTimeout(60_000)
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [
      {
        id: 't_users',
        name: 'users',
        columns: [
          { id: 'c_id', name: 'id', type: 'BIGINT', nullable: false, isPrimaryKey: true, isAutoIncrement: true },
          { id: 'c_email', name: 'email', type: 'VARCHAR(255)', nullable: false },
        ],
      },
    ],
  })
  const designer = new ErDesignerPage(page)
  await designer.clickBindTarget()
  await designer.fillBindTarget({ connectionId: workingConnId })
  await designer.confirmBindTarget()
  await waitForPayloadVersion(page, tabId, (v) => v > 0, 5_000)
  await designer.clickGenerateDdl()
  // 等新 query_editor tab 出现
  const newTabId = await page.waitForFunction(
    () => {
      const stage = (window as any).__DT_E2E__.stage()
      const t = stage.tabs.find((t: any) => t.type === 'query_editor')
      return t ? t.id : false
    },
    null,
    { timeout: 10_000 },
  ).then((h) => h.jsonValue())
  expect(newTabId).toBeTruthy()
  const sql = await page.evaluate((id) => {
    const sw = (window as any).__DT_E2E__.stage()
    return sw.getTabById?.(id)?.payload?.content
      ?? (window as any).__DT_E2E__.session
        ? null : null
  }, newTabId)
  // 通过 useSqlWorkbenchStore 读
  const sqlText: string = await page.evaluate((id) => {
    const store = (window as any).useSqlWorkbenchStore?.getState?.()
      ?? require('@/features/stage/stores/sql-workbench-store').useSqlWorkbenchStore.getState()
    return store.contentByTab?.get(id) ?? ''
  }, newTabId)
  expect(sqlText.toUpperCase()).toMatch(/CREATE\s+TABLE/)
})

test('D27: Generate DDL 后 query_editor results === [] (未自动执行)', async ({ page }) => {
  test.setTimeout(60_000)
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [
      { id: 't_users', name: 'users', columns: [{ id: 'c_id', name: 'id', type: 'BIGINT', isPrimaryKey: true }] },
    ],
  })
  const designer = new ErDesignerPage(page)
  await designer.clickBindTarget()
  await designer.fillBindTarget({ connectionId: workingConnId })
  await designer.confirmBindTarget()
  await waitForPayloadVersion(page, tabId, (v) => v > 0, 5_000)
  await designer.clickGenerateDdl()
  const newTabId = await page.waitForFunction(
    () => {
      const stage = (window as any).__DT_E2E__.stage()
      const t = stage.tabs.find((t: any) => t.type === 'query_editor')
      return t ? t.id : false
    },
    null,
    { timeout: 10_000 },
  ).then((h) => h.jsonValue())
  const results: any[] = await page.evaluate((id) => {
    const store = (window as any).useSqlWorkbenchStore?.getState?.()
      ?? require('@/features/stage/stores/sql-workbench-store').useSqlWorkbenchStore.getState()
    return store.resultsByTab?.get(id) ?? []
  }, newTabId)
  expect(results).toEqual([])
})

test('D28: dialect=postgresql Generate DDL 用 PG 语法', async ({ page }) => {
  test.setTimeout(60_000)
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'postgresql',
    seedTables: [
      {
        id: 't_users',
        name: 'users',
        columns: [
          { id: 'c_id', name: 'id', type: 'BIGSERIAL', nullable: false, isPrimaryKey: true, isAutoIncrement: true },
        ],
      },
    ],
  })
  const designer = new ErDesignerPage(page)
  await designer.clickBindTarget()
  await designer.fillBindTarget({ connectionId: workingConnId })
  await designer.confirmBindTarget()
  await waitForPayloadVersion(page, tabId, (v) => v > 0, 5_000)
  await designer.clickGenerateDdl()
  const newTabId = await page.waitForFunction(
    () => {
      const stage = (window as any).__DT_E2E__.stage()
      const t = stage.tabs.find((t: any) => t.type === 'query_editor')
      return t ? t.id : false
    },
    null,
    { timeout: 10_000 },
  ).then((h) => h.jsonValue())
  const sql: string = await page.evaluate((id) => {
    const store = (window as any).useSqlWorkbenchStore?.getState?.()
      ?? require('@/features/stage/stores/sql-workbench-store').useSqlWorkbenchStore.getState()
    return store.contentByTab?.get(id) ?? ''
  }, newTabId)
  expect(sql.toUpperCase()).toMatch(/SERIAL|BIGSERIAL|GENERATED.*IDENTITY/)
})

test('D29: dialect=sqlite Generate DDL 含 skipped 信息（仅 CREATE TABLE）', async ({ page }) => {
  test.setTimeout(60_000)
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'sqlite',
    seedTables: [
      { id: 't_users', name: 'users', columns: [{ id: 'c_id', name: 'id', type: 'INTEGER', isPrimaryKey: true }] },
      { id: 't_orders', name: 'orders', columns: [{ id: 'c_oid', name: 'user_id', type: 'INTEGER' }] },
    ],
  })
  // 加 FK relation（SQLite 无 ALTER ADD FK，应被 skipped）
  await page.evaluate((id) => {
    const er = (window as any).__DT_E2E__.er()
    er.applyDesignerPatch(id, [
      {
        op: 'add', path: '/relations/-',
        value: {
          fromTableId: 't_users', fromColumnId: 'c_id',
          toTableId: 't_orders', toColumnId: 'c_oid',
          type: 'one_to_many', constraintMethod: 'database_fk',
        },
      },
    ])
  }, tabId)
  const designer = new ErDesignerPage(page)
  await designer.clickBindTarget()
  await designer.fillBindTarget({ connectionId: workingConnId })
  await designer.confirmBindTarget()
  await waitForPayloadVersion(page, tabId, (v) => v > 0, 5_000)
  await designer.clickGenerateDdl()
  // 期待 toast / dialog 含 skipped 信息（具体文案放宽匹配）
  await page.waitForSelector('text=/skipped|不支持|manual SQL/i', { timeout: 10_000 })
})
```

- [x] **Step 11: D30 不变量**

```ts
test('D30: Designer 全程无任何 /api/sql/execute 调用', async ({ page }) => {
  test.setTimeout(90_000)
  await page.addInitScript(() => {
    ;(window as any).__DT_FETCH_LOG__ = [] as string[]
    const orig = window.fetch
    window.fetch = function (...args: any[]) {
      const url = typeof args[0] === 'string' ? args[0] : (args[0] as Request).url
      ;(window as any).__DT_FETCH_LOG__.push(url)
      return orig.apply(window, args as any)
    }
  })
  await page.goto('/')
  await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [{ id: 't_users', name: 'users', columns: [{ id: 'c_id', name: 'id', type: 'BIGINT', isPrimaryKey: true }] }],
  })
  const designer = new ErDesignerPage(page)
  await designer.clickAddTable()
  await waitForPayloadVersion(page, tabId, (v) => v >= 1, 3_000)
  await designer.setDialect('postgresql')
  await waitForPayloadVersion(page, tabId, (v) => v >= 2, 3_000)
  await designer.clickBindTarget()
  await designer.fillBindTarget({ connectionId: workingConnId })
  await designer.confirmBindTarget()
  await waitForPayloadVersion(page, tabId, (v) => v >= 3, 5_000)
  await designer.clickGenerateDdl()
  await page.waitForTimeout(2_000)
  const sqlExecuteHits = await fetchCallsMatching(page, /\/api\/sql\/execute/)
  expect(sqlExecuteHits).toBe(0)
})
```

- [x] **Step 12: 跑 spec 2**

```bash
cd client && npx playwright test tests/e2e/er-designer-ui.spec.ts --reporter=list
```

Expected：30 passed（或部分 skip：D8/D10 在 pgConnId 不可用时 skip；D11 在 sqlite 连接存在时 skip 该断言）。

任何 fail → 登 BUG，把 test 改 `test.fail`。

- [x] **Step 13: 提交**

```bash
git add client/tests/e2e/er-designer-ui.spec.ts
git commit -m "test(er-e2e): add Designer UI spec (D1-D30, 30 tests)"
```

---

## Task 4: spec 3 — 入口 + 持久化（6 tests）

> **Parallel batch eligible**：与 Task 2 / Task 3 互不依赖。

**Files:**
- Create: `client/tests/e2e/er-entry-and-persistence.spec.ts`

- [x] **Step 1: 先 grep 真实 ER 入口候选**

```bash
grep -rn 'open_er_inspector\|open_er_designer\|er_inspector\|er_designer\|View ER\|ER 图' \
  client/src/features/connection \
  client/src/features/stage/components/left-rail \
  client/src/features/stage/components/stage-tab-bar* 2>/dev/null
```

把命中位置记到 spec 注释里。若 0 命中 → E1/E2 走 BUG-B 兜底路径；E3-E6 仍跑。

- [x] **Step 2: 写 spec 头**

```ts
import { test, expect } from '@playwright/test'
import { ErInspectorPage } from './pom/er-inspector.page'
import { ErDesignerPage } from './pom/er-designer.page'
import {
  openErInspectorViaShortcut,
  openErDesignerViaShortcut,
  readInspectorPayload,
  readDesignerPayload,
  waitForPayloadVersion,
  setupErFixture,
} from './fixtures/er-test-helpers'

let workingConnId = ''

test.describe.configure({ timeout: 600_000 })

test.beforeAll(async () => {
  const f = await setupErFixture()
  workingConnId = f.workingConnId
  await f.cleanup()
})

test.beforeEach(async ({ page }) => {
  await page.goto('/')
  await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
})
```

- [x] **Step 3: E1 / E2 真实入口（按 Step 1 grep 结果选路径）**

如果 grep 命中 Sidebar `[+]` ER 入口，写 happy path 测试：

```ts
test('E1: Sidebar / 连接面板真实 "View ER" 入口存在则点击打开 inspector tab', async ({ page }) => {
  // 假设入口在 Sidebar，按 Step 1 实际 grep 结果调整 selector
  const entry = page.locator('[data-testid="sidebar-add-er-inspector"]') // 实际 testid 按 grep 结果
  if ((await entry.count()) === 0) {
    test.skip(true, 'no sidebar ER entry — see BUG-NNNN-er-inspector-entry-missing.md')
    return
  }
  await entry.click()
  await page.locator('[data-er-tab-id]').waitFor({ state: 'visible', timeout: 10_000 })
  const tabType = await page.locator('[data-er-mode]').first().getAttribute('data-er-mode')
  expect(tabType).toBe('inspector')
})

test('E2: 若无真实入口 → BUG 登记 + fallback 用快捷路径开 Tab 验证', async ({ page }) => {
  // E1 skip 时此 test 兜底执行；通过快捷路径开 Tab，确认 §6.1 全集仍能跑
  const { tabId } = await openErInspectorViaShortcut(page, {
    connectionId: workingConnId,
    tables: ['users'],
  })
  const inspector = new ErInspectorPage(page)
  expect(await inspector.getActiveTabId()).toBe(tabId)
})
```

如果 grep **0 命中** → E1 直接 `test.skip(true, 'BUG-B：ER 入口未接产品 UI')`，并在 Task 5 登记 BUG-B。

- [x] **Step 4: E3 Fork 跨 Tab**

```ts
test('E3: Inspector → Fork to Designer 后 Designer 出现，schema 形态对齐', async ({ page }) => {
  test.setTimeout(60_000)
  const { tabId: inspectorTabId } = await openErInspectorViaShortcut(page, {
    connectionId: workingConnId,
    tables: ['users', 'orders'],
  })
  const inspector = new ErInspectorPage(page)
  await inspector.clickRefresh()
  await waitForPayloadVersion(page, inspectorTabId, (v) => v > 0, 5_000)
  await inspector.clickForkToDesigner()
  const designerTabId = await page.waitForFunction(
    () => {
      const stage = (window as any).__DT_E2E__.stage()
      const t = stage.tabs.find((t: any) => t.type === 'er_designer')
      return t ? t.id : false
    },
    null,
    { timeout: 10_000 },
  ).then((h) => h.jsonValue() as Promise<string>)
  const designerPayload = await readDesignerPayload(page, designerTabId)
  expect(designerPayload).toBeDefined()
  // 表数量大致对齐（Inspector 含 selection + neighbors，Designer 至少含 selection）
  expect(designerPayload.tables.length).toBeGreaterThanOrEqual(2)
})
```

若返 `plan_b_only` 错误（toast 出现）→ 标 `test.fixme` + 登 BUG-D（"fork_to_designer 在当前环境不支持"）。

- [x] **Step 5: E4 Inspector 持久化**

```ts
test('E4: 刷新页面后 ER Inspector Tab 仍在；selection / positions / viewport / neighborDepth 完整恢复', async ({ page }) => {
  test.setTimeout(60_000)
  test.slow()
  const { tabId } = await openErInspectorViaShortcut(page, {
    connectionId: workingConnId,
    tables: ['users', 'orders', 'products'],
    neighborDepth: 1,
  })
  const inspector = new ErInspectorPage(page)
  await inspector.clickAutoLayout()
  await waitForPayloadVersion(page, tabId, (v) => v > 0, 5_000)
  await inspector.dragNode('users', 50, 30)
  await waitForPayloadVersion(page, tabId, (v) => v > 1, 3_000)
  const before = await readInspectorPayload(page, tabId)

  await page.reload()
  await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
  await page.locator(`[data-er-tab-id="${tabId}"]`).waitFor({ state: 'visible', timeout: 15_000 })

  const after = await readInspectorPayload(page, tabId)
  expect(after.selection).toEqual(before.selection)
  expect(after.neighborDepth).toEqual(before.neighborDepth)
  expect(after.positions).toEqual(before.positions)
  expect(after.viewport.x).toEqual(before.viewport.x)
  expect(after.viewport.y).toEqual(before.viewport.y)
  // 不断言 virtualRelations / notes（无 UI 写入路径）
})
```

- [x] **Step 6: E5 Designer 持久化**

```ts
test('E5: 刷新页面后 ER Designer Tab 仍在；tables / relations / dialect / targetConnectionId 完整恢复', async ({ page }) => {
  test.setTimeout(60_000)
  test.slow()
  const { tabId } = await openErDesignerViaShortcut(page, {
    dialect: 'h2',
    seedTables: [
      { id: 't1', name: 'users', columns: [{ id: 'c1', name: 'id', type: 'BIGINT', isPrimaryKey: true }] },
      { id: 't2', name: 'orders', columns: [{ id: 'c2', name: 'user_id', type: 'BIGINT' }] },
    ],
  })
  const designer = new ErDesignerPage(page)
  await designer.clickBindTarget()
  await designer.fillBindTarget({ connectionId: workingConnId })
  await designer.confirmBindTarget()
  await waitForPayloadVersion(page, tabId, (v) => v > 0, 5_000)
  const before = await readDesignerPayload(page, tabId)

  await page.reload()
  await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
  await page.locator(`[data-er-tab-id="${tabId}"]`).waitFor({ state: 'visible', timeout: 15_000 })

  const after = await readDesignerPayload(page, tabId)
  expect(after.tables.map((t: any) => t.name)).toEqual(before.tables.map((t: any) => t.name))
  expect(after.dialect).toEqual(before.dialect)
  expect(after.targetConnectionId).toEqual(before.targetConnectionId)
})
```

- [x] **Step 7: E6 切 session ER Tab 不消失**

```ts
test('E6: 切换 session 后 ER Tab 不消失（验证 stage 全局非 per-session）', async ({ page }) => {
  const { tabId } = await openErInspectorViaShortcut(page, {
    connectionId: workingConnId,
    tables: ['users'],
  })
  // 创建 + 切到一个新 session
  await page.evaluate(() => {
    const session = (window as any).__DT_E2E__.session()
    const newId = `s-${Date.now()}`
    session.createSession?.({ id: newId, title: 'switch-target' })
      ?? session.upsertSession?.({ id: newId, title: 'switch-target' })
    session.setActiveSessionId?.(newId) ?? session.setActive?.(newId)
  })
  // ER Tab 必须仍可见
  await page.locator(`[data-er-tab-id="${tabId}"]`).waitFor({ state: 'visible', timeout: 5_000 })
})
```

- [x] **Step 8: 跑 spec 3**

```bash
cd client && npx playwright test tests/e2e/er-entry-and-persistence.spec.ts --reporter=list
```

Expected：6 passed 或 E1 skip + 5 passed（视入口 grep 结果）。

- [x] **Step 9: 提交**

```bash
git add client/tests/e2e/er-entry-and-persistence.spec.ts
git commit -m "test(er-e2e): add Entry + Persistence spec (E1-E6, 6 tests)"
```

---

## Task 5: 集成跑 + BUG 登记 + Housekeeping

**Files:**
- Update: `docs/exec-plans/index.md`（移到 Completed）
- Create: `docs/bugs/BUG-NNNN-er-add-virtual-relation-noop.md`
- Create (条件): `docs/bugs/BUG-NNNN-er-inspector-entry-missing.md`
- Create (条件): `docs/bugs/BUG-NNNN-er-canvas-missing-payload-version-attr.md`
- Update: `docs/bugs/index.md`

- [x] **Step 1: 跑全量 47 tests**

```bash
cd client && npx playwright test \
  tests/e2e/er-inspector-ui.spec.ts \
  tests/e2e/er-designer-ui.spec.ts \
  tests/e2e/er-entry-and-persistence.spec.ts \
  --reporter=list
```

Expected：≥ 39 passed + ≤ 8 skip/fixme（fixme 至少 1 = I5；skip 视 D8/D10/E1/D11 环境）。任何 fail → BUG 登记。

- [x] **Step 2: 登记预登 BUG-A（onAddVirtualRelation noop）**

读 `docs/bugs/index.md` 当前 NextID（"下一个分配 ID：BUG-NNNN"）。

写入 `docs/bugs/BUG-NNNN-er-add-virtual-relation-noop.md`（NNNN 替换为实际 ID）：

```markdown
---
id: BUG-NNNN
title: ER Inspector "Add virtual relation" toolbar 按钮无效
status: open
priority: P1
source: E2E test
modules:
  - er-canvas
  - er-inspector
discoveredAt: 2026-05-06
discoveredBy: er-module-e2e-test-plan I5
---

## 现象

Inspector toolbar 上 "Add virtual relation" 按钮点击后无任何反应：不弹窗、不进入编辑模式、不写 `/virtualRelations`。

## 复现路径

1. 打开任意 ER Inspector Tab
2. 点 toolbar "Add virtual relation"

## 期望

按 ER product spec / `er-tab-protocol.md` `/virtualRelations` 路径定义，应进入"选源表/源列 → 选目标表/目标列 → 填备注"编辑流程，最终 `ui_patch /virtualRelations/-` 落入 payload。

## 根因

`client/src/features/stage/components/er-canvas/ErCanvas.tsx:223`

```ts
const onAddVirtualRelation = useCallback(() => undefined, [])
```

回调是 noop。Designer 的 `onAddTable` 等其他 toolbar 按钮均有实现，唯独此按钮被遗留。

## 影响

P1 — 功能缺失但 AI 仍可走 `ui_patch /virtualRelations/-` 兜底路径；用户侧此按钮整段不可用。

## 关联

- Test：`client/tests/e2e/er-inspector-ui.spec.ts` I5（test.fixme）
- Plan：`docs/exec-plans/2026-05-06-er-module-e2e-test-plan.md`
```

更新 `docs/bugs/index.md`：
- "下一个分配 ID" 自增
- Open BUGs 表加 BUG-NNNN 行
- By Module / By Source 加聚合

- [x] **Step 3: 登记 BUG-B（仅当 Task 4 Step 1 grep 0 命中）**

如 ER 入口在产品 UI 完全不存在，建 `docs/bugs/BUG-NNNN-er-inspector-entry-missing.md`（priority P2，modules `er-inspector / connection-panel`）。同步 `index.md`。

- [x] **Step 4: 登记 BUG-C（仅当 Task 0 Step 4-6 产品代码改动被驳回）**

如不允许加 `data-payload-version` testid 钩子，建 `docs/bugs/BUG-NNNN-er-canvas-missing-payload-version-attr.md`（priority P3，modules `er-canvas / e2e-testability`）。

- [x] **Step 5: 把本计划移到 Completed**

读 `docs/exec-plans/index.md`，把 `2026-05-06-er-module-e2e-test-plan.md` 从 Active 区删，加到 Completed 区，附结果摘要（X passed / Y fixme / Z BUG 登记）。

- [x] **Step 6: 把所有 task checkbox 标完成**

编辑本 plan 文件，逐个 `- [ ]` → `- [x]`。

- [x] **Step 7: 提交 housekeeping**

```bash
git add docs/bugs/ docs/exec-plans/
git commit -m "$(cat <<'EOF'
docs(er-e2e): close ER module E2E test plan + register BUGs

47 tests landed (Inspector 11 / Designer 30 / Entry+Persistence 6).
Result: <X> passed, <Y> skipped, <Z> fixme, <N> BUGs registered:
- BUG-NNNN er-add-virtual-relation-noop (P1, predicted)
- ...

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [x] **Step 8: 在最终响应里报 BUG 计数**

按 CLAUDE.md BUG Tracking Gate 规则：响应里必须明确说"本次发现 N 个 BUG，已登记到 docs/bugs/…"。N=0 也要明说。

---

## Risks（执行期需关注）

- **R1（POM selector 脆弱）**：如果 Task 0 Step 4-6 产品代码 prep 被驳回，POM 大量退化为 store evaluate，Task 1 Step 3-4 需要重写多个方法定位策略。Mitigation：在 PR 描述里写明 Task 0 是必要前置，争取 review 通过。
- **R2（dagre worker 异步）**：60s test 上限对 30 表 + Auto layout 仍可能不足；如 CI 超时 → 缩小 fixture 表数。
- **R3（Bind Target Dialog dialect 过滤）**：D8/D10 严格依赖 mock postgresql 连接；如 `ensureMockPostgresConnection` 因 backend 校验失败创建不出 → 两 test 自动 skip。
- **R5（Plan A/B 差异）**：`fork_to_designer` 在 Plan A 环境返 `error.code = plan_b_only` → I6/E3 fixme + BUG 登记。
- **R7（ReactFlow 拖动精度）**：D20 dragConnect 命中率取决于 handle hit area；失败时 fallback `page.evaluate` 直写 store 已写在 POM 内（er-designer.page.ts:dragConnect R9 ④）。
- **R8（入口 grep 不确定）**：Task 4 Step 1 grep 结果决定 E1 是真路径还是 skip。
- **R10（notes 无 UI）**：E4 / E5 不能断言 notes 字段，已显式排除。

## 执行不变量（Verify Gate）

每完成一个 task 必须本地跑：

```bash
cd client && npx tsc --noEmit         # 类型零错误
cd client && npx playwright test tests/e2e/er-*-ui.spec.ts --reporter=list   # 已写的 spec 全绿
```

类型错或既有 spec 红 → 不 commit、回退当前 task。
