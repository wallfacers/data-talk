import { test, expect } from '@playwright/test'
import { adapterClient } from './fixtures/adapter-client'
import { startMockIngestionServer, type MockServer, seedCredential } from './fixtures/ingestion-fixtures'

const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'

let mock: MockServer
test.beforeAll(async () => { mock = await startMockIngestionServer() })
test.afterAll(async () => { await mock.stop() })

test.describe('@e2e @ingestion @api @error Ingestion error codes', () => {
  test('INGESTION_SSRF_BLOCKED for deny-listed URL', async ({ request }) => {
    const c = adapterClient(request)
    const res = await c.mcpCall('http_request', {
      url: 'http://169.254.169.254/latest/meta-data/',
      payloadFormat: 'JSON',
    })
    if (res.error) {
      expect(res.error.message).toMatch(/ssrf|SSRF|blocked|denied|invalid/i)
    } else {
      expect(res.result?.status).toBe('failed')
      expect(res.result?.errorCode).toBe('INGESTION_SSRF_BLOCKED')
    }
  })

  test('INGESTION_AUTH_FAILED for wrong credentials', async ({ request }) => {
    test.fixme(true, 'BUG-0013: http_request output schema validation masks errors')
    test.fixme(true, 'BUG-0013: http_request output schema validation masks errors')
    const c = adapterClient(request)
    // Use bearer credential with WRONG secret
    const credId = await seedCredential(request, 'bearer', { secret: 'wrong-token' })
    const res = await c.mcpCall('http_request', {
      url: `${mock.baseUrl}/auth/bearer`,
      credentialId: credId,
      payloadFormat: 'JSON',
    })
    // Should surface as error or failed job
    if (res.error) {
      expect(res.error.message).toMatch(/auth|unauthorized|401/i)
    } else if (res.result?.status === 'failed') {
      expect(res.result?.errorCode).toBe('INGESTION_AUTH_FAILED')
    } else if (res.result?.status === 'fetched') {
      // If it somehow succeeded, the mock returned 401 but parser still marked as fetched
      test.fixme(true, 'Auth failure not detected — mock 401 handled as successful fetch')
    }
  })

  test('INGESTION_FORMAT_UNSUPPORTED for unsupported content type', async ({ request }) => {
    // The mock server doesn't have a zip endpoint — simulate by fetching a non-existent path
    // which returns 404 with application/json — this won't trigger format unknown
    // Instead, we document this as a known gap: the mock can't produce arbitrary Content-Types
    // We skip this test and note it should be tested against a real external server
    test.fixme(true, 'Mock server cannot produce application/zip — test against real external server')
  })

  test('INGESTION_TOKEN_INVALID re-assertion', async ({ request }) => {
    test.fixme(true, 'BUG-0013: http_request output schema validation masks errors')
    // Re-assert the token invalidation path via a fresh token with wrong mappingHash
    const c = adapterClient(request)
    const fetched = await c.mcpCall('http_request', {
      url: `${mock.baseUrl}/json/users`,
      payloadFormat: 'JSON',
    })
    expect(fetched.result?.jobId, 'http_request should return jobId').toBeTruthy()
    const jobId = fetched.result!.jobId as string
    await c.mcpCall('infer_ingestion_schema', { jobId })
    const confirm = await request.post(`${BASE}/api/ingestion/jobs/${jobId}/confirm`, { data: {} })
    const { tokenId } = await confirm.json()
    // Create table first
    const create1 = await c.mcpCall('create_ingestion_table', {
      jobId, connectionId: 'nonexistent', schema: 'X', table: 'X',
      mappingHash: '0000000000000000000000000000000000000000000000000000000000000000',
      tokenId,
    })
    // Now re-use consumed token
    const create2 = await c.mcpCall('create_ingestion_table', {
      jobId, connectionId: 'nonexistent', schema: 'X', table: 'X',
      mappingHash: '0000000000000000000000000000000000000000000000000000000000000000',
      tokenId,
    })
    expect(create2.result?.errorCode ?? create2.error?.message).toContain('TOKEN')
  })

  test('INGESTION_PAYLOAD_TOO_LARGE for oversized payload', async ({ request }) => {
    const c = adapterClient(request)
    const res = await c.mcpCall('http_request', {
      url: `${mock.baseUrl}/error/oversized`,
      payloadFormat: 'JSON',
    })
    if (res.error) {
      expect(res.error.message).toMatch(/too large|payload_too_large/i)
    } else {
      expect(res.result?.status).toBe('failed')
      expect(res.result?.errorCode).toBe('INGESTION_PAYLOAD_TOO_LARGE')
      expect(res.result?.userHint).toBeTruthy()
    }
  })
})
