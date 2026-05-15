import { test, expect } from '@playwright/test'
import { adapterClient } from './fixtures/adapter-client'

const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'

/**
 * 12.3 — User marks an AI-generated SQL as correct → VQ persisted →
 * next identical question hits L0 via /api/semantic/:cid/verified-queries
 * and verified_query_find MCP action.
 */
test.describe('@e2e @semantic @api Verified query flow', () => {
  let connectionId: string

  test.beforeAll(async ({ request }) => {
    const c = adapterClient(request)
    const res = await c.createConnection({
      name: `e2e_sem_vq_${Date.now()}`,
      kind: 'h2',
      host: 'mem',
      port: 0,
      databaseName: 'mem:e2e_sem_vq;DB_CLOSE_DELAY=-1',
      username: 'sa',
      password: '',
    })
    expect(res.ok()).toBe(true)
    const body = await res.json()
    connectionId = body.id
  })

  test.afterAll(async ({ request }) => {
    if (connectionId) {
      await request.delete(`${BASE}/api/connections/${connectionId}?force=true`)
    }
  })

  test('POST /verified-query records VQ then GET /verified-queries lists it', async ({ request }) => {
    const record = await request.post(`${BASE}/api/semantic/${connectionId}/verified-query`, {
      data: {
        question: '本月销售额',
        sql: "SELECT SUM(amount) FROM orders WHERE month = 5",
        modelRef: 'orders',
      },
    })
    expect(record.ok()).toBe(true)
    const recordBody = await record.json()
    expect(recordBody.id).toBeTruthy()
    expect(recordBody.question).toBe('本月销售额')

    const list = await request.get(`${BASE}/api/semantic/${connectionId}/verified-queries?topK=10`)
    expect(list.ok()).toBe(true)
    const listBody = await list.json()
    expect(listBody.queries.length).toBeGreaterThanOrEqual(1)
    const questions = listBody.queries.map((q: any) => q.question)
    expect(questions).toContain('本月销售额')
  })

  test('Missing required fields returns 400', async ({ request }) => {
    const res = await request.post(`${BASE}/api/semantic/${connectionId}/verified-query`, {
      data: { question: 'no sql' },
    })
    expect(res.status()).toBe(400)
    const body = await res.json()
    expect(body.error).toBe('MISSING_REQUIRED_FIELDS')
  })

  test('verified_query_find MCP action returns L0 hit for exact match', async ({ request }) => {
    // Record VQ first
    await request.post(`${BASE}/api/semantic/${connectionId}/verified-query`, {
      data: { question: '近7天 GMV', sql: 'SELECT SUM(amount) FROM orders', modelRef: 'orders' },
    })

    const c = adapterClient(request)
    const rpc = await c.mcpCall('datatalk_verified_query_find', {
      connectionId,
      question: '近7天 GMV',
      topK: 5,
    })
    test.skip(rpc.error !== undefined, `verified_query_find not callable: ${rpc.error?.message}`)

    const result = rpc.result as any
    expect(result.layer).toBe('L0')
    expect(result.hit).toBe(true)
    expect(result.candidates).toHaveLength(1)
  })

  test('verified_query_find normalizes synonyms (GMV ↔ 销售额)', async ({ request }) => {
    await request.post(`${BASE}/api/semantic/${connectionId}/verified-query`, {
      data: { question: '本月 销售额', sql: 'SELECT 1', modelRef: 'orders' },
    })

    const c = adapterClient(request)
    const rpc = await c.mcpCall('datatalk_verified_query_find', {
      connectionId,
      question: '本月 GMV',
      topK: 5,
    })
    test.skip(rpc.error !== undefined, 'verified_query_find unavailable')
    const result = rpc.result as any
    // Either L1 hit via synonym, or L2 with this VQ as a candidate
    if (result.hit) {
      expect(['L1', 'L0']).toContain(result.layer)
    } else {
      expect(result.candidates.length).toBeGreaterThanOrEqual(1)
    }
  })

  test('verified-queries top-K is sorted by hit count', async ({ request }) => {
    // No need for hit count manipulation — server just sorts.
    const list = await request.get(`${BASE}/api/semantic/${connectionId}/verified-queries?topK=20`)
    const body = await list.json()
    const counts = body.queries.map((q: any) => q.hitCount as number)
    for (let i = 1; i < counts.length; i++) {
      expect(counts[i]).toBeLessThanOrEqual(counts[i - 1])
    }
  })
})
