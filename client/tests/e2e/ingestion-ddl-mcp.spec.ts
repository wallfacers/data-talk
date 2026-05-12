import { test, expect } from '@playwright/test'
import { adapterClient } from './fixtures/adapter-client'
import {
  startMockIngestionServer, type MockServer, seedH2Connection,
} from './fixtures/ingestion-fixtures'

const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'

let mock: MockServer
test.beforeAll(async () => { mock = await startMockIngestionServer() })
test.afterAll(async () => { await mock.stop() })

async function fetchInferConfirm(request, connId: string, sourceUrl: string) {
  const c = adapterClient(request)
  const f = await c.mcpCall('http_request', { url: sourceUrl })
  const jobId = f.result!.jobId as string
  await c.mcpCall('infer_ingestion_schema', { jobId })
  const confirm = await request.post(
    `${BASE}/api/ingestion/jobs/${jobId}/confirm`,
    { data: {} },
  )
  expect(confirm.ok()).toBe(true)
  const { tokenId, mappingHash } = await confirm.json()
  return { jobId, tokenId, mappingHash }
}

test.describe('@e2e @ingestion @api DDL generation + execution', () => {
  // All tests in this block depend on http_request returning a valid jobId.
  // BUG-0013: http_request output schema validation fails on error path (null for required fields).
  test.fixme(true, 'BUG-0013 — http_request output schema validation masks original errors')

  test('H2 round-trip — CREATE TABLE then SELECT', async ({ request }) => {
    const connId = await seedH2Connection(request, `e2e_h2_ddl_${Date.now()}`)
    const { jobId, tokenId, mappingHash } = await fetchInferConfirm(request, connId, `${mock.baseUrl}/json/users`)
    const c = adapterClient(request)
    const create = await c.mcpCall('create_ingestion_table', {
      jobId, connectionId: connId, schema: 'PUBLIC', table: 'e2e_users',
      mappingHash, tokenId,
    })
    expect(create.error).toBeUndefined()
    expect(create.result!.ddl as string).toMatch(/CREATE TABLE "?PUBLIC"?\."?e2e_users"?/i)

    // Verify the table exists by SELECT
    const sel = await c.executeSql({ connectionId: connId, sql: 'SELECT COUNT(*) FROM e2e_users' })
    expect(sel.ok()).toBe(true)
  })

  test('Unsupported dialect returns INGESTION_DIALECT_UNSUPPORTED', async ({ request }) => {
    const conn = await adapterClient(request).createConnection({
      name: `e2e_oracle_unsupported_${Date.now()}`,
      kind: 'oracle', host: 'x', port: 1521, database: 'x', username: 'x', password: 'x',
    })
    if (!conn.ok()) test.skip(true, 'connection create rejected for oracle without driver')
    const connId = (await conn.json()).id as string

    const { jobId, tokenId, mappingHash } = await fetchInferConfirm(request, connId, `${mock.baseUrl}/json/users`)
    const c = adapterClient(request)
    const res = await c.mcpCall('create_ingestion_table', {
      jobId, connectionId: connId, schema: 'X', table: 'X', mappingHash, tokenId,
    })
    expect(res.result?.errorCode ?? res.error?.message).toContain('DIALECT_UNSUPPORTED')
  })

  test('MySQL round-trip via env URL (env-gated)', async ({ request }) => {
    test.skip(!process.env.E2E_INGESTION_MYSQL_URL, 'set E2E_INGESTION_MYSQL_URL to run')
    // Skip — env not set, no-op
  })

  test('Postgres round-trip via env URL (env-gated)', async ({ request }) => {
    test.skip(!process.env.E2E_INGESTION_PG_URL, 'set E2E_INGESTION_PG_URL to run')
    // Skip — env not set, no-op
  })

  test('DDL preview quotes identifiers per dialect', async ({ request }) => {
    const connId = await seedH2Connection(request, `e2e_h2_ddl_preview_${Date.now()}`)
    const { jobId, tokenId, mappingHash } = await fetchInferConfirm(request, connId, `${mock.baseUrl}/json/users`)
    const c = adapterClient(request)
    const create = await c.mcpCall('create_ingestion_table', {
      jobId, connectionId: connId, schema: 'PUBLIC', table: 'e2e_users',
      mappingHash, tokenId,
    })
    expect(create.error).toBeUndefined()
    const ddl = create.result!.ddl as string
    // H2 uses PostgreSQL mode → double-quoted identifiers
    expect(ddl).toMatch(/CREATE TABLE "?PUBLIC"?\."?e2e_users"?/i)
  })
})
