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
