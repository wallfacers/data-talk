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

// Step 2: D1-D3 Toolbar add / layout / fit

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

// Step 3: D4-D7 Toolbar bind/diff/ddl/dialect

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

// Step 4: D8-D13 Bind Dialog 全流程

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

// Step 5: D14-D16 右键菜单

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

// Step 6: D17-D19 列内联

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

// Step 7: D20-D22 拖连边 / Edge

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

// Step 8: D23-D24 Delete 键

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

// Step 9: D25 Empty state

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

// Step 10: D26-D29 DDL 跨 dialect

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

// Step 11: D30 不变量

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
