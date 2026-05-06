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
  await page.evaluate(
    ({ tabId, opts }) => {
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
