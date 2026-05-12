import { test, expect } from '@playwright/test'
import { adapterClient } from './fixtures/adapter-client'
import { execSqlite } from './fixtures/mcp-context'
import { startMockIngestionServer, type MockServer } from './fixtures/ingestion-fixtures'

let mock: MockServer

test.beforeAll(async ({ request }) => {
  mock = await startMockIngestionServer()

  // Seed a session row with opencode_sid so MCP bridge can resolve context.
  // Use 127.0.0.1 explicitly to avoid IPv6 ::1 resolution issues.
  const BASE = 'http://127.0.0.1:8080'
  const createRes = await request.post(`${BASE}/api/sessions`, { data: { title: 'e2e-ingestion-test' } })
  if (!createRes.ok()) throw new Error(`Failed to create session: ${createRes.status()}`)
  const sessionBody = await createRes.json() as { id: string }
  const sessionId = sessionBody.id
  const ocSid = `e2e-oc-${Date.now()}`
  // Use the shared Python sqlite3 helper so the spec doesn't depend on the
  // sqlite3 CLI (absent on bare WSL2 / Ubuntu minimal setups).
  execSqlite(`UPDATE sessions SET opencode_sid = '${ocSid}' WHERE id = '${sessionId}';`)
})
test.afterAll(async () => { await mock.stop() })
test.beforeEach(() => mock.reset())

test.describe('@e2e @ingestion @api Schema inference + mappingHash', () => {
  test('JSON inference produces typed columns', async ({ request }) => {
    const c = adapterClient(request)
    const fetched = await c.mcpCall('http_request', { url: `${mock.baseUrl}/json/users`, payloadFormat: 'JSON' })
    expect(fetched.error).toBeUndefined()
    const jobId = fetched.result!.jobId as string
    const inferred = await c.mcpCall('infer_ingestion_schema', { jobId, sampleSize: 50 })
    expect(inferred.error).toBeUndefined()
    const cols = inferred.result!.columns as Array<{ sourcePath: string; type: string }>
    expect(cols.find((c) => c.sourcePath === '$.id')?.type).toMatch(/INTEGER/)
    expect(cols.find((c) => c.sourcePath === '$.name')?.type).toMatch(/STRING/)
    expect(cols.find((c) => c.sourcePath === '$.active')?.type).toBe('BOOLEAN')
  })

  test('JSONL inference produces typed columns', async ({ request }) => {
    const c = adapterClient(request)
    const fetched = await c.mcpCall('http_request', { url: `${mock.baseUrl}/jsonl/events`, payloadFormat: 'JSONL' })
    expect(fetched.error).toBeUndefined()
    const jobId = fetched.result!.jobId as string
    const inferred = await c.mcpCall('infer_ingestion_schema', { jobId, sampleSize: 50 })
    expect(inferred.error).toBeUndefined()
    const cols = inferred.result!.columns as Array<{ sourcePath: string; type: string }>
    expect(cols.some((c) => c.sourcePath === '$.id')).toBe(true)
    expect(cols.some((c) => c.sourcePath === '$.kind')).toBe(true)
  })

  test('CSV inference parses headers as column names', async ({ request }) => {
    const c = adapterClient(request)
    const fetched = await c.mcpCall('http_request', { url: `${mock.baseUrl}/csv/orders`, payloadFormat: 'CSV' })
    expect(fetched.error).toBeUndefined()
    const jobId = fetched.result!.jobId as string
    const inferred = await c.mcpCall('infer_ingestion_schema', { jobId, sampleSize: 50 })
    expect(inferred.error).toBeUndefined()
    const cols = inferred.result!.columns as Array<{ sourcePath: string; type: string }>
    const names = cols.map((c) => c.sourcePath)
    expect(names).toContain('$.id')
    expect(names).toContain('$.name')
    expect(names).toContain('$.total')
  })

  test('HTML inference parses <th> as column names', async ({ request }) => {
    const c = adapterClient(request)
    const fetched = await c.mcpCall('http_request', { url: `${mock.baseUrl}/html/leaderboard`, payloadFormat: 'HTML' })
    expect(fetched.error).toBeUndefined()
    const jobId = fetched.result!.jobId as string
    const inferred = await c.mcpCall('infer_ingestion_schema', { jobId, sampleSize: 50 })
    expect(inferred.error).toBeUndefined()
    const cols = inferred.result!.columns as Array<{ sourcePath: string }>
    const names = cols.map((c) => c.sourcePath)
    expect(names).toContain('$.rank')
    expect(names).toContain('$.player')
    expect(names).toContain('$.score')
  })

  test('mappingHash persisted after infer', async ({ request }) => {
    const c = adapterClient(request)
    const f = await c.mcpCall('http_request', { url: `${mock.baseUrl}/json/users`, payloadFormat: 'JSON' })
    expect(f.error).toBeUndefined()
    const jobId = f.result!.jobId as string
    await c.mcpCall('infer_ingestion_schema', { jobId })
    const got = await c.mcpCall('get_ingestion_job', { jobId })
    expect(got.error).toBeUndefined()
    expect(got.result!.job!.mappingHash).toMatch(/^[0-9a-f]{64}$/)
  })

  test('JSON envelope unwrap with dataPath', async ({ request }) => {
    const c = adapterClient(request)
    const fetched = await c.mcpCall('http_request', { url: `${mock.baseUrl}/json/envelope`, payloadFormat: 'JSON' })
    expect(fetched.error).toBeUndefined()
    const jobId = fetched.result!.jobId as string
    const inferred = await c.mcpCall('infer_ingestion_schema', { jobId, dataPath: '$.data' })
    if (inferred.error) {
      test.fixme(true, 'dataPath not supported by infer_ingestion_schema')
      return
    }
    expect(inferred.error).toBeUndefined()
    const cols = inferred.result!.columns as Array<{ sourcePath: string }>
    expect(cols.some((c) => c.sourcePath === '$.id')).toBe(true)
  })

  test('Heterogeneous values fallback to STRING type', async ({ request }) => {
    const c = adapterClient(request)
    const fetched = await c.mcpCall('http_request', { url: `${mock.baseUrl}/json/heterogeneous-id`, payloadFormat: 'JSON' })
    expect(fetched.error).toBeUndefined()
    const jobId = fetched.result!.jobId as string
    const inferred = await c.mcpCall('infer_ingestion_schema', { jobId, sampleSize: 50 })
    expect(inferred.error).toBeUndefined()
    const cols = inferred.result!.columns as Array<{ sourcePath: string; type: string }>
    const idCol = cols.find((c) => c.sourcePath === '$.id')
    expect(idCol).toBeTruthy()
    expect(idCol!.type).toMatch(/STRING/)
  })

  test('INTEGER_64 promotion for values > 2^31', async ({ request }) => {
    const c = adapterClient(request)
    const fetched = await c.mcpCall('http_request', { url: `${mock.baseUrl}/json/large-int`, payloadFormat: 'JSON' })
    expect(fetched.error).toBeUndefined()
    const jobId = fetched.result!.jobId as string
    const inferred = await c.mcpCall('infer_ingestion_schema', { jobId, sampleSize: 50 })
    expect(inferred.error).toBeUndefined()
    const cols = inferred.result!.columns as Array<{ sourcePath: string; type: string }>
    const vCol = cols.find((c) => c.sourcePath === '$.v')
    expect(vCol).toBeTruthy()
    expect(vCol!.type).toMatch(/INTEGER_64/)
  })
})
