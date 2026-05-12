import { test, expect } from '@playwright/test'

const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'

test.describe('@e2e @ingestion @api Credential REST', () => {
  test.afterEach(async ({ request }) => {
    // Clean up any credential whose name starts with e2e_cred_
    const list = await request.get(`${BASE}/api/ingestion/credentials`)
    if (!list.ok()) return
    const body = await list.json() as { items: Array<{ id: string; name: string }> }
    for (const c of body.items) {
      if (c.name.startsWith('e2e_cred_')) {
        await request.delete(`${BASE}/api/ingestion/credentials/${c.id}?force=true`)
      }
    }
  })

  test('create + get + delete for scheme=none', async ({ request }) => {
    const create = await request.post(`${BASE}/api/ingestion/credentials`, {
      data: { name: `e2e_cred_none_${Date.now()}`, authScheme: 'none', configNonSecret: {}, secret: null },
    })
    expect(create.ok()).toBe(true)
    const { id } = await create.json()
    expect(id).toBeTruthy()

    const got = await (await request.get(`${BASE}/api/ingestion/credentials/${id}`)).json()
    expect(got.authScheme).toBe('none')
    expect(got.hasSecret).toBe(false)

    const del = await request.delete(`${BASE}/api/ingestion/credentials/${id}`)
    expect(del.status()).toBe(204)
  })

  test('create with scheme=bearer surfaces hasSecret=true and never leaks secret', async ({ request }) => {
    const create = await request.post(`${BASE}/api/ingestion/credentials`, {
      data: { name: `e2e_cred_bearer_${Date.now()}`, authScheme: 'bearer', configNonSecret: {}, secret: 'plaintext-bearer-token' },
    })
    const { id } = await create.json()
    const got = await (await request.get(`${BASE}/api/ingestion/credentials/${id}`)).json()
    expect(got.hasSecret).toBe(true)
    expect(JSON.stringify(got)).not.toContain('plaintext-bearer-token')
  })

  test('api_key_header keeps headerName in configNonSecret', async ({ request }) => {
    const create = await request.post(`${BASE}/api/ingestion/credentials`, {
      data: {
        name: `e2e_cred_apikey_hdr_${Date.now()}`,
        authScheme: 'api_key_header',
        configNonSecret: { headerName: 'X-Api-Key' },
        secret: 's3cret',
      },
    })
    const { id } = await create.json()
    const got = await (await request.get(`${BASE}/api/ingestion/credentials/${id}`)).json()
    expect(got.configNonSecret.headerName).toBe('X-Api-Key')
    expect(got.hasSecret).toBe(true)
  })

  test('api_key_query keeps queryName in configNonSecret', async ({ request }) => {
    const create = await request.post(`${BASE}/api/ingestion/credentials`, {
      data: {
        name: `e2e_cred_apikey_q_${Date.now()}`,
        authScheme: 'api_key_query',
        configNonSecret: { queryName: 'api_key' },
        secret: 'q-secret',
      },
    })
    const { id } = await create.json()
    const got = await (await request.get(`${BASE}/api/ingestion/credentials/${id}`)).json()
    expect(got.configNonSecret.queryName).toBe('api_key')
    expect(got.hasSecret).toBe(true)
  })

  test('basic auth keeps username in configNonSecret', async ({ request }) => {
    const create = await request.post(`${BASE}/api/ingestion/credentials`, {
      data: {
        name: `e2e_cred_basic_${Date.now()}`,
        authScheme: 'basic',
        configNonSecret: { username: 'alice' },
        secret: 'p@ss',
      },
    })
    const { id } = await create.json()
    const got = await (await request.get(`${BASE}/api/ingestion/credentials/${id}`)).json()
    expect(got.configNonSecret.username).toBe('alice')
    expect(got.hasSecret).toBe(true)
  })

  test('invalid scheme returns 4xx', async ({ request }) => {
    const res = await request.post(`${BASE}/api/ingestion/credentials`, {
      data: { name: `e2e_cred_bad_${Date.now()}`, authScheme: 'oauth2', configNonSecret: {}, secret: null },
    })
    expect(res.status()).toBeGreaterThanOrEqual(400)
    expect(res.status()).toBeLessThan(500)
  })

  test('duplicate name rejected', async ({ request }) => {
    const name = `e2e_cred_dup_${Date.now()}`
    const a = await request.post(`${BASE}/api/ingestion/credentials`, {
      data: { name, authScheme: 'none', configNonSecret: {}, secret: null },
    })
    expect(a.ok()).toBe(true)
    const b = await request.post(`${BASE}/api/ingestion/credentials`, {
      data: { name, authScheme: 'none', configNonSecret: {}, secret: null },
    })
    expect(b.status()).toBeGreaterThanOrEqual(400)
  })

  test('list returns all created credentials', async ({ request }) => {
    const namePrefix = `e2e_cred_list_${Date.now()}`
    for (let i = 0; i < 3; i++) {
      await request.post(`${BASE}/api/ingestion/credentials`, {
        data: { name: `${namePrefix}_${i}`, authScheme: 'none', configNonSecret: {}, secret: null },
      })
    }
    const list = await request.get(`${BASE}/api/ingestion/credentials`)
    expect(list.ok()).toBe(true)
    const body = await list.json() as { items: Array<{ name: string }> }
    const count = body.items.filter(c => c.name.startsWith(namePrefix)).length
    expect(count).toBeGreaterThanOrEqual(3)
  })

  test('delete with in-use credential returns 409 unless force=true', async ({ request }) => {
    test.fixme(true, 'Requires an ingestion_job referencing the credential — see ingestion-execute-mcp')
  })
})
