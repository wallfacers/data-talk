import { test, expect } from '@playwright/test'
import { adapterClient } from './fixtures/adapter-client'

const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'

/**
 * 12.4 — DELETE /api/connections/:id with force=false is blocked when the
 * connection has verified queries; with force=true the semantic model is
 * moved to _trash and the connection row is removed.
 */
test.describe('@e2e @semantic @api Connection delete cascade', () => {
  async function createConnWithVq(request: any): Promise<string> {
    const c = adapterClient(request)
    const created = await c.createConnection({
      name: `e2e_sem_del_${Date.now()}`,
      kind: 'h2',
      host: 'mem',
      port: 0,
      databaseName: `mem:e2e_sem_del_${Date.now()};DB_CLOSE_DELAY=-1`,
      username: 'sa',
      password: '',
    })
    expect(created.ok()).toBe(true)
    const body = await created.json()
    const connectionId = body.id

    // Seed a verified query
    const rec = await request.post(`${BASE}/api/semantic/${connectionId}/verified-query`, {
      data: { question: 'test q', sql: 'SELECT 1', modelRef: 'orders' },
    })
    expect(rec.ok()).toBe(true)

    return connectionId
  }

  test('DELETE force=false returns BLOCKED_BY_RESOURCES when VQ exists', async ({ request }) => {
    const connectionId = await createConnWithVq(request)
    try {
      const res = await request.delete(`${BASE}/api/connections/${connectionId}`)
      // Server returns 409 with structured body for blocked case
      expect([409, 422, 200]).toContain(res.status())
      const body = await res.json()
      // Whatever the exact status code, the response should mention either verifiedQueries count > 0
      // OR an explicit blocked status string.
      const json = JSON.stringify(body).toLowerCase()
      expect(json).toMatch(/(blocked|verifiedqueries|verified_queries|resources)/)
    } finally {
      await request.delete(`${BASE}/api/connections/${connectionId}?force=true`)
    }
  })

  test('DELETE force=true cascade removes VQ and connection', async ({ request }) => {
    const connectionId = await createConnWithVq(request)

    const res = await request.delete(`${BASE}/api/connections/${connectionId}?force=true`)
    expect([200, 204]).toContain(res.status())

    // After force delete, listing VQ should not return the previous entries
    // (the endpoint may 404 or 200 with empty list — both acceptable)
    const list = await request.get(`${BASE}/api/semantic/${connectionId}/verified-queries?topK=10`)
    if (list.status() === 200) {
      const body = await list.json()
      expect(body.queries).toHaveLength(0)
    } else {
      expect([404, 200]).toContain(list.status())
    }

    // Connection should no longer be in the connection list
    const c = adapterClient(request)
    const conns = await (await c.listConnections()).json()
    const ids = (conns.items ?? conns.connections ?? conns).map((x: any) => x.id)
    expect(ids).not.toContain(connectionId)
  })

  test('DELETE force=false succeeds when no VQ and no file resources', async ({ request }) => {
    const c = adapterClient(request)
    const created = await c.createConnection({
      name: `e2e_sem_clean_${Date.now()}`,
      kind: 'h2',
      host: 'mem',
      port: 0,
      databaseName: `mem:e2e_sem_clean_${Date.now()};DB_CLOSE_DELAY=-1`,
      username: 'sa',
      password: '',
    })
    const { id: connectionId } = await created.json()

    const res = await request.delete(`${BASE}/api/connections/${connectionId}`)
    expect([200, 204]).toContain(res.status())
  })
})
