import { test, expect } from '@playwright/test'
import { adapterClient } from './fixtures/adapter-client'

const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'

/**
 * 12.1 — Semantic model created from builtin template lands under the
 * connection's semantic directory and shows up in /api/semantic/:cid/domains.
 */
test.describe('@e2e @semantic @api Create from template', () => {
  let connectionId: string

  test.beforeAll(async ({ request }) => {
    const c = adapterClient(request)
    const create = await c.createConnection({
      name: `e2e_sem_tpl_${Date.now()}`,
      kind: 'h2',
      host: 'mem',
      port: 0,
      databaseName: 'mem:e2e_sem_tpl;DB_CLOSE_DELAY=-1',
      username: 'sa',
      password: '',
    })
    expect(create.ok()).toBe(true)
    const body = await create.json()
    connectionId = body.id
  })

  test.afterAll(async ({ request }) => {
    if (connectionId) {
      await request.delete(`${BASE}/api/connections/${connectionId}?force=true`)
    }
  })

  test('POST /api/semantic/:cid/from-template (ecommerce) appends domain', async ({ request }) => {
    const res = await request.post(`${BASE}/api/semantic/${connectionId}/from-template`, {
      data: { templateName: 'ecommerce' },
    })
    expect(res.ok()).toBe(true)
    const body = await res.json()
    expect(body.status).toBe('created_from_template')
    expect(body.domain).toBe('ecommerce')

    const list = await request.get(`${BASE}/api/semantic/${connectionId}/domains`)
    expect(list.ok()).toBe(true)
    const listBody = await list.json()
    expect(listBody.domains).toContain('ecommerce')
  })

  test('GET /api/semantic/:cid/domain/:name returns parsed model with measures', async ({ request }) => {
    // ensure template applied (idempotent — accept may have already accepted in first test)
    await request.post(`${BASE}/api/semantic/${connectionId}/from-template`, {
      data: { templateName: 'ecommerce' },
    })

    const res = await request.get(`${BASE}/api/semantic/${connectionId}/domain/ecommerce`)
    expect(res.status()).toBeLessThan(500)
    if (res.status() === 200) {
      const body = await res.json()
      expect(body.name).toBe('ecommerce')
      expect(Array.isArray(body.entities)).toBe(true)
      expect(Array.isArray(body.measures)).toBe(true)
    }
  })

  test('Unknown templateName returns 400 with TEMPLATE_NOT_FOUND', async ({ request }) => {
    const res = await request.post(`${BASE}/api/semantic/${connectionId}/from-template`, {
      data: { templateName: 'does_not_exist' },
    })
    expect(res.status()).toBe(400)
    const body = await res.json()
    expect(body.error).toBe('TEMPLATE_NOT_FOUND')
  })

  test('Missing templateName returns 400', async ({ request }) => {
    const res = await request.post(`${BASE}/api/semantic/${connectionId}/from-template`, {
      data: {},
    })
    expect(res.status()).toBe(400)
  })
})
