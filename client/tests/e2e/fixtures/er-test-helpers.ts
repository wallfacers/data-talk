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

export async function openErInspectorViaShortcut(
  page: Page,
  opts: ErInspectorOpenOpts,
): Promise<{ tabId: string }> {
  const tabId = `er-inspector-${Date.now()}-${Math.floor(Math.random() * 1e6)}`
  // Build mock tablesSnapshot so the canvas renders nodes immediately
  const mockSnapshot = opts.tables.map((name) => {
    const baseColumns = [
      { name: 'id', type: 'BIGINT', isPK: true, isFK: false, nullable: false },
      { name: 'name', type: 'VARCHAR(255)', isPK: false, isFK: false, nullable: true },
    ]
    const fkOut: Array<{ fromColumn: string; toTable: string; toColumn: string }> = []
    if (name === 'orders') {
      fkOut.push({ fromColumn: 'user_id', toTable: 'users', toColumn: 'id' })
      baseColumns.push({ name: 'user_id', type: 'BIGINT', isPK: false, isFK: true, nullable: true })
      baseColumns.push({ name: 'amount', type: 'DECIMAL(10,2)', isPK: false, isFK: false, nullable: true })
    }
    if (name === 'order_items') {
      fkOut.push({ fromColumn: 'order_id', toTable: 'orders', toColumn: 'id' })
      fkOut.push({ fromColumn: 'product_id', toTable: 'products', toColumn: 'id' })
      baseColumns.push({ name: 'order_id', type: 'BIGINT', isPK: false, isFK: true, nullable: true })
      baseColumns.push({ name: 'product_id', type: 'BIGINT', isPK: false, isFK: true, nullable: true })
    }
    if (name === 'products') {
      baseColumns.push({ name: 'sku_code', type: 'VARCHAR(64)', isPK: false, isFK: false, nullable: true })
    }
    return { name, comment: null, columns: baseColumns, fkOut }
  })
  await page.evaluate(
    ({ tabId, opts, mockSnapshot }) => {
      const stage = (window as any).__DT_E2E__?.stage()
      const er = (window as any).__DT_E2E__?.er()
      const payload = {
        kind: 'er_inspector',
        connectionId: opts.connectionId,
        database: null,
        schema: null,
        selection: opts.tables,
        neighborDepth: opts.neighborDepth ?? 1,
        layout: 'dagre-LR',
        tablesSnapshot: mockSnapshot,
        snapshotAt: Date.now(),
        positions: {},
        collapsed: [],
        virtualRelations: [],
        notes: {},
        viewport: { x: 0, y: 0, zoom: 1 },
        __v: 0,
      }
      stage.openTab({
        tabId,
        type: 'er_inspector',
        title: 'ER Inspector (e2e)',
        connectionId: opts.connectionId,
        payload,
        createdAt: Date.now(),
        payloadVersion: 1,
      })
      er.hydrateInspector(tabId, payload)
    },
    { tabId, opts, mockSnapshot },
  )
  await page.locator(`[data-er-tab-id="${tabId}"]`).first().waitFor({ state: 'visible', timeout: 10_000 })
  const { ErInspectorPage } = await import('../pom/er-inspector.page')
  return { tabId, inspector: new ErInspectorPage(page, tabId) }
}

export async function openErDesignerViaShortcut(
  page: Page,
  opts: ErDesignerOpenOpts,
): Promise<{ tabId: string }> {
  const tabId = `er-designer-${Date.now()}-${Math.floor(Math.random() * 1e6)}`
  await page.evaluate(
    ({ tabId, opts }) => {
      const stage = (window as any).__DT_E2E__?.stage()
      const er = (window as any).__DT_E2E__?.er()
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
        tabId,
        type: 'er_designer',
        title: 'ER Designer (e2e)',
        connectionId: null,
        payload,
        createdAt: Date.now(),
        payloadVersion: 1,
      })
      er.hydrateDesigner(tabId, payload)
    },
    { tabId, opts },
  )
  await page.locator(`[data-er-tab-id="${tabId}"]`).first().waitFor({ state: 'visible', timeout: 10_000 })
  const { ErDesignerPage } = await import('../pom/er-designer.page')
  return { tabId, designer: new ErDesignerPage(page, tabId) }
}

export async function readInspectorPayload(page: Page, tabId: string): Promise<any> {
  return await page.evaluate((id) => {
    const er = (window as any).__DT_E2E__?.er()
    return er.inspectors.get(id) ?? null
  }, tabId)
}

export async function readDesignerPayload(page: Page, tabId: string): Promise<any> {
  return await page.evaluate((id) => {
    const er = (window as any).__DT_E2E__?.er()
    return er.designers.get(id) ?? null
  }, tabId)
}

export async function waitForPayloadVersion(
  page: Page,
  tabId: string,
  predicate: (v: number) => boolean,
  timeoutMs = 2_000,
): Promise<number> {
  const start = Date.now()
  while (Date.now() - start < timeoutMs) {
    const v = await page.evaluate((id) => {
      const el = document.querySelector(`[data-er-tab-id="${id}"]`)
      return parseInt(el?.getAttribute('data-payload-version') ?? '0', 10)
    }, tabId)
    if (predicate(v)) return v
    await page.waitForTimeout(100)
  }
  throw new Error(`waitForPayloadVersion timed out after ${timeoutMs}ms for tab ${tabId}`)
}

export async function fetchCallsMatching(page: Page, pattern: RegExp): Promise<number> {
  const calls: string[] = await page.evaluate(() => (window as any).__DT_FETCH_LOG__ ?? [])
  return calls.filter((u) => pattern.test(u)).length
}

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

  // Run er-seed.sql
  const fs = await import('fs')
  const path = await import('path')
  const seedPath = path.resolve(process.cwd(), 'tests/e2e/fixtures/er-seed.sql')
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
