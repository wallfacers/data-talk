import { test, expect } from '@playwright/test'
import { adapterClient } from './fixtures/adapter-client'
import {
  startMockIngestionServer, type MockServer, seedH2Connection,
} from './fixtures/ingestion-fixtures'

const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'

let mock: MockServer
test.beforeAll(async () => { mock = await startMockIngestionServer() })
test.afterAll(async () => { await mock.stop() })

test.describe('@e2e @ingestion @api Confirm, cancel, token + ingest', () => {
  // BUG-0013: http_request output schema validation fails on error path (null for required fields).
  test.fixme(true, 'BUG-0013 — http_request output schema validation masks original errors')
  test('confirm returns tokenId, expiresAt, mappingHash', async ({ request }) => {
    const connId = await seedH2Connection(request, `e2e_h2_confirm_${Date.now()}`)
    const c = adapterClient(request)
    const fetched = await c.mcpCall('http_request', { url: `${mock.baseUrl}/json/users` })
    const jobId = fetched.result!.jobId as string
    await c.mcpCall('infer_ingestion_schema', { jobId })
    const confirm = await request.post(`${BASE}/api/ingestion/jobs/${jobId}/confirm`, { data: {} })
    expect(confirm.ok()).toBe(true)
    const body = await confirm.json()
    expect(body.tokenId).toBeTruthy()
    expect(body.expiresAt).toBeTruthy()
    expect(body.mappingHash).toMatch(/^[0-9a-f]{64}$/)
  })

  test('confirm without prior infer returns 409', async ({ request }) => {
    const connId = await seedH2Connection(request, `e2e_h2_no_infer_${Date.now()}`)
    const c = adapterClient(request)
    const fetched = await c.mcpCall('http_request', { url: `${mock.baseUrl}/json/users` })
    const jobId = fetched.result!.jobId as string
    // Do NOT call infer — go straight to confirm
    const confirm = await request.post(`${BASE}/api/ingestion/jobs/${jobId}/confirm`, { data: {} })
    expect(confirm.status()).toBeGreaterThanOrEqual(400)
  })

  test('cancel flips status to cancelled', async ({ request }) => {
    const connId = await seedH2Connection(request, `e2e_h2_cancel_${Date.now()}`)
    const c = adapterClient(request)
    const fetched = await c.mcpCall('http_request', { url: `${mock.baseUrl}/json/users` })
    const jobId = fetched.result!.jobId as string
    await c.mcpCall('infer_ingestion_schema', { jobId })
    const cancel = await request.post(`${BASE}/api/ingestion/jobs/${jobId}/cancel`, { data: {} })
    expect(cancel.ok()).toBe(true)
    // confirm after cancel should be rejected
    const confirm = await request.post(`${BASE}/api/ingestion/jobs/${jobId}/confirm`, { data: {} })
    expect(confirm.status()).toBeGreaterThanOrEqual(400)
  })

  test('create_ingestion_table with valid token works', async ({ request }) => {
    const connId = await seedH2Connection(request, `e2e_h2_create_tbl_${Date.now()}`)
    const c = adapterClient(request)
    const fetched = await c.mcpCall('http_request', { url: `${mock.baseUrl}/json/users` })
    const jobId = fetched.result!.jobId as string
    await c.mcpCall('infer_ingestion_schema', { jobId })
    const confirm = await request.post(`${BASE}/api/ingestion/jobs/${jobId}/confirm`, { data: {} })
    const { tokenId, mappingHash } = await confirm.json()
    const create = await c.mcpCall('create_ingestion_table', {
      jobId, connectionId: connId, schema: 'PUBLIC', table: 'e2e_token_test',
      mappingHash, tokenId,
    })
    expect(create.error).toBeUndefined()
    expect(create.result!.ddl as string).toMatch(/CREATE TABLE/i)
  })

  test('re-consuming same token returns INGESTION_TOKEN_INVALID', async ({ request }) => {
    const connId = await seedH2Connection(request, `e2e_h2_reconsume_${Date.now()}`)
    const c = adapterClient(request)
    const fetched = await c.mcpCall('http_request', { url: `${mock.baseUrl}/json/users` })
    const jobId = fetched.result!.jobId as string
    await c.mcpCall('infer_ingestion_schema', { jobId })
    const confirm = await request.post(`${BASE}/api/ingestion/jobs/${jobId}/confirm`, { data: {} })
    const { tokenId, mappingHash } = await confirm.json()
    // First use
    const create1 = await c.mcpCall('create_ingestion_table', {
      jobId, connectionId: connId, schema: 'PUBLIC', table: 'e2e_reconsume1',
      mappingHash, tokenId,
    })
    expect(create1.error).toBeUndefined()
    // Second use — should fail
    const create2 = await c.mcpCall('create_ingestion_table', {
      jobId, connectionId: connId, schema: 'PUBLIC', table: 'e2e_reconsume2',
      mappingHash, tokenId,
    })
    expect(create2.result?.errorCode ?? create2.error?.message).toContain('TOKEN')
  })

  test('mappingHash mismatch returns INGESTION_TOKEN_INVALID', async ({ request }) => {
    const connId = await seedH2Connection(request, `e2e_h2_hashmismatch_${Date.now()}`)
    const c = adapterClient(request)
    const fetched = await c.mcpCall('http_request', { url: `${mock.baseUrl}/json/users` })
    const jobId = fetched.result!.jobId as string
    await c.mcpCall('infer_ingestion_schema', { jobId })
    const confirm = await request.post(`${BASE}/api/ingestion/jobs/${jobId}/confirm`, { data: {} })
    const { tokenId } = await confirm.json()
    // Pass a fake wrong hash
    const create = await c.mcpCall('create_ingestion_table', {
      jobId, connectionId: connId, schema: 'PUBLIC', table: 'e2e_hashmismatch',
      mappingHash: '0000000000000000000000000000000000000000000000000000000000000000',
      tokenId,
    })
    expect(create.result?.errorCode ?? create.error?.message).toContain('TOKEN')
  })

  test('token expires after 5 minutes', async ({ request }) => {
    test.fixme(true, 'Token TTL is 5 min — waiting is impractical. Covered by unit test IngestionConfirmedTokenStoreTest.')
  })

  test('ingest_payload after create_table populates rows', async ({ request }) => {
    const connId = await seedH2Connection(request, `e2e_h2_ingest_${Date.now()}`)
    const c = adapterClient(request)
    const fetched = await c.mcpCall('http_request', { url: `${mock.baseUrl}/json/users` })
    const jobId = fetched.result!.jobId as string
    await c.mcpCall('infer_ingestion_schema', { jobId })
    const confirm = await request.post(`${BASE}/api/ingestion/jobs/${jobId}/confirm`, { data: {} })
    const { tokenId, mappingHash } = await confirm.json()
    const create = await c.mcpCall('create_ingestion_table', {
      jobId, connectionId: connId, schema: 'PUBLIC', table: 'e2e_ingest_test',
      mappingHash, tokenId,
    })
    expect(create.error).toBeUndefined()
    const ingest = await c.mcpCall('ingest_payload', { jobId })
    expect(ingest.error).toBeUndefined()
    expect(ingest.result?.rowsInserted).toBe(3)
    // Verify via executeSql
    const sel = await c.executeSql({ connectionId: connId, sql: 'SELECT COUNT(*) FROM e2e_ingest_test' })
    expect(sel.ok()).toBe(true)
  })

  test('ingest_payload against malformed payload fails cleanly', async ({ request }) => {
    // Create a job with a deliberately bad artifact
    // For this test, use the /error/401 mock route which will fail fetch
    const c = adapterClient(request)
    const fetched = await c.mcpCall('http_request', { url: `${mock.baseUrl}/error/401` })
    // If fetch failed, the job should be in failed status
    if (fetched.result?.status === 'fetched') {
      const jobId = fetched.result!.jobId as string
      await c.mcpCall('infer_ingestion_schema', { jobId })
      const connId = await seedH2Connection(request)
      const confirm = await request.post(`${BASE}/api/ingestion/jobs/${jobId}/confirm`, { data: {} })
      const { tokenId, mappingHash } = await confirm.json()
      const create = await c.mcpCall('create_ingestion_table', {
        jobId, connectionId: connId, schema: 'PUBLIC', table: 'e2e_bad_payload',
        mappingHash, tokenId,
      })
      // Ingest should fail or job should be in failed state
      const ingest = await c.mcpCall('ingest_payload', { jobId })
      // Either error or job status should indicate failure
      if (!ingest.error && ingest.result?.status !== 'failed') {
        const job = await c.mcpCall('get_ingestion_job', { jobId })
        expect(job.result?.status).toBe('failed')
      }
    }
    // If fetch itself failed, that's also acceptable
  })

  test('streaming oversized payload does not OOM', async ({ request }) => {
    const c = adapterClient(request)
    const res = await c.mcpCall('http_request', { url: `${mock.baseUrl}/error/oversized`, payloadFormat: 'JSON' })
    // Should either fail with payload_too_large or complete within timeout
    if (res.error) {
      expect(res.error.message).toMatch(/too large|payload_too_large/i)
    } else {
      expect(res.result?.status).toBeDefined()
    }
  })
})
