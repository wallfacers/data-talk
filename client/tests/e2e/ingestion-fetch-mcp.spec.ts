import { test, expect } from '@playwright/test'
import { adapterClient } from './fixtures/adapter-client'
import { startMockIngestionServer, type MockServer, seedCredential } from './fixtures/ingestion-fixtures'

let mock: MockServer

test.beforeAll(async () => { mock = await startMockIngestionServer() })
test.afterAll(async () => { await mock.stop() })
test.beforeEach(() => mock.reset())

test.describe('@e2e @ingestion @api HTTP fetch via datatalk_http_request', () => {
  test('JSON array payload yields jobId + payloadArtifactId', async ({ request }) => {
    test.fixme(true, 'Blocked on BUG-0013: http_request returns null for required output fields')
    const client = adapterClient(request)
    const res = await client.mcpCall('http_request', { url: `${mock.baseUrl}/json/users`, payloadFormat: 'JSON' })
    expect(res.error).toBeUndefined()
    expect(res.result?.jobId).toMatch(/^[a-zA-Z0-9_-]+$/)
    expect(res.result?.payloadArtifactId).toBeTruthy()
    expect(res.result?.status).toBe('fetched')
    expect(mock.hits[0]?.url).toBe('/json/users')
  })

  test('JSONL payload', async ({ request }) => {
    test.fixme(true, 'Blocked on BUG-0013: http_request output schema validation masks errors')
    const res = await adapterClient(request).mcpCall('http_request', { url: `${mock.baseUrl}/jsonl/events`, payloadFormat: 'JSONL' })
    expect(res.error).toBeUndefined()
    expect(res.result?.payloadFormat).toBe('jsonl')
  })

  test('CSV payload', async ({ request }) => {
    test.fixme(true, 'Blocked on BUG-0013: http_request output schema validation masks errors')
    const res = await adapterClient(request).mcpCall('http_request', { url: `${mock.baseUrl}/csv/orders`, payloadFormat: 'CSV' })
    expect(res.error).toBeUndefined()
    expect(res.result?.payloadFormat).toBe('csv')
  })

  test('HTML <table> payload', async ({ request }) => {
    test.fixme(true, 'Blocked on BUG-0013: http_request output schema validation masks errors')
    const res = await adapterClient(request).mcpCall('http_request', { url: `${mock.baseUrl}/html/leaderboard`, payloadFormat: 'HTML' })
    expect(res.error).toBeUndefined()
    expect(res.result?.payloadFormat).toBe('html')
  })

  test('Bearer auth header forwarded', async ({ request }) => {
    test.fixme(true, 'Blocked on BUG-0013: http_request output schema validation masks errors')
    const credId = await seedCredential(request, 'bearer', { secret: 'test-token-42' })
    const res = await adapterClient(request).mcpCall('http_request', {
      url: `${mock.baseUrl}/auth/bearer`,
      credentialId: credId,
      payloadFormat: 'JSON',
    })
    expect(res.error).toBeUndefined()
    expect(mock.hits.find((h) => h.url === '/auth/bearer')?.headers.authorization).toBe('Bearer test-token-42')
  })

  test('API key header auth', async ({ request }) => {
    test.fixme(true, 'Blocked on BUG-0013: http_request output schema validation masks errors')
    const credId = await seedCredential(request, 'api_key_header', {
      configNonSecret: { headerName: 'X-Api-Key' },
      secret: 'secret-key-7',
    })
    const res = await adapterClient(request).mcpCall('http_request', {
      url: `${mock.baseUrl}/auth/api-key-header`,
      credentialId: credId,
      payloadFormat: 'JSON',
    })
    expect(res.error).toBeUndefined()
  })

  test('API key query auth appends ?api_key=', async ({ request }) => {
    test.fixme(true, 'Blocked on BUG-0013: http_request output schema validation masks errors')
    const credId = await seedCredential(request, 'api_key_query', {
      configNonSecret: { queryName: 'api_key' },
      secret: 'secret-key-7',
    })
    const res = await adapterClient(request).mcpCall('http_request', {
      url: `${mock.baseUrl}/auth/api-key-query`,
      credentialId: credId,
      payloadFormat: 'JSON',
    })
    expect(res.error).toBeUndefined()
    const hit = mock.hits.find((h) => h.url.startsWith('/auth/api-key-query'))
    expect(hit).toBeTruthy()
  })

  test('Basic auth header forwarded', async ({ request }) => {
    test.fixme(true, 'Blocked on BUG-0013: http_request output schema validation masks errors')
    const expectedAuth = `Basic ${Buffer.from('alice:p@ss').toString('base64')}`
    const credId = await seedCredential(request, 'basic', {
      configNonSecret: { username: 'alice' },
      secret: 'p@ss',
    })
    const res = await adapterClient(request).mcpCall('http_request', {
      url: `${mock.baseUrl}/auth/basic`,
      credentialId: credId,
      payloadFormat: 'JSON',
    })
    expect(res.error).toBeUndefined()
    expect(mock.hits.find((h) => h.url === '/auth/basic')?.headers.authorization).toBe(expectedAuth)
  })

  test('Pagination — page param walks N pages then stops', async ({ request }) => {
    test.fixme(true, 'Blocked on BUG-0013: http_request output schema validation masks errors')
    const res = await adapterClient(request).mcpCall('http_request', {
      url: `${mock.baseUrl}/paged/page`,
      payloadFormat: 'JSON',
      pagination: { type: 'page', param: 'page', initial: 1, maxPages: 5 },
    })
    expect(res.error).toBeUndefined()
    expect(mock.hits.filter((h) => h.url.startsWith('/paged/page')).length).toBe(4)
    expect(res.result?.rowCount).toBe(6)
  })

  test('Pagination — offset param', async ({ request }) => {
    test.fixme(true, 'Blocked on BUG-0013: http_request output schema validation masks errors')
    const res = await adapterClient(request).mcpCall('http_request', {
      url: `${mock.baseUrl}/paged/offset`,
      payloadFormat: 'JSON',
      pagination: { type: 'offset', param: 'offset', initial: 0, pageSize: 2, maxPages: 5 },
    })
    expect(res.error).toBeUndefined()
    expect(mock.hits.filter((h) => h.url.startsWith('/paged/offset')).length).toBe(4)
    expect(res.result?.rowCount).toBe(6)
  })

  test('Pagination — cursor param', async ({ request }) => {
    test.fixme(true, 'Blocked on BUG-0013: http_request output schema validation masks errors')
    const res = await adapterClient(request).mcpCall('http_request', {
      url: `${mock.baseUrl}/paged/cursor`,
      payloadFormat: 'JSON',
      pagination: { type: 'cursor', param: 'cursor', initial: 'A', maxPages: 5 },
    })
    expect(res.error).toBeUndefined()
    expect(mock.hits.filter((h) => h.url.startsWith('/paged/cursor')).length).toBeGreaterThanOrEqual(3)
    expect(res.result?.rowCount).toBe(5)
  })

  test('Oversized payload trips payload-max-bytes and surfaces error', async ({ request }) => {
    test.fixme(true, 'BUG-0015: oversized payload not detected as failed')
    const res = await adapterClient(request).mcpCall('http_request', {
      url: `${mock.baseUrl}/error/oversized`,
      payloadFormat: 'JSON',
    })
    if (res.error) {
      expect(res.error.code).toBe(-32603)
      expect(res.error.message).toMatch(/too large|payload_too_large/i)
    } else {
      expect(res.result?.status).toBe('failed')
      expect(res.result?.errorCode).toBe('INGESTION_PAYLOAD_TOO_LARGE')
    }
  })
})
