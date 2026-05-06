import { test, expect } from '@playwright/test'
import { StagePage } from './pom/stage.page'
import { ChatPanelPage } from './pom/chat-panel.page'
import { ConnectionManagerPage } from './pom/connection-manager.page'
import { mountToolRecorder } from './fixtures/mcp-tool-recorder'
import { adapterClient } from './fixtures/adapter-client'
import { getLatestOpenCodeSession } from './fixtures/mcp-context'

const MODEL = process.env.DATATALK_REAL_OPENCODE_MODEL

let stage: StagePage
let chat: ChatPanelPage
let conn: ConnectionManagerPage

const WORKING_CONN_ID = '323230ca-44c7-491c-b41f-3c2069c72b85'

test.beforeEach(async ({ page }) => {
  await page.goto('/')
  stage = new StagePage(page)
  chat = new ChatPanelPage(page)
  conn = new ConnectionManagerPage(page)
  await page.waitForSelector('textarea', { timeout: 15_000 })
})

// ─────────────────────────────────────────────────────────────────────────────
// Session Data Context
// ─────────────────────────────────────────────────────────────────────────────
test.describe('Session Data Context', () => {
  test.setTimeout(60_000)

  test('routing: get_data_context on context question', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('当前用的是哪个连接和数据库？')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_get_data_context')
    expect(calls.length).toBeGreaterThanOrEqual(1)
  })

  test('contract: get_data_context returns fields', async ({ request }) => {
    const c = adapterClient(request)
    // Create a fresh session with no explicit context
    const sessionRes = await c.createSession({})
    expect(sessionRes.ok()).toBe(true)
    const session = await sessionRes.json()
    const sessionId = session.id

    const rpc = await c.mcpCall('datatalk_get_data_context', {})
    expect(rpc.error).toBeUndefined()
    const result = rpc.result as any
    expect(result).toBeDefined()
    expect(result).toHaveProperty('connectionId')
    expect(result).toHaveProperty('database')
    expect(result).toHaveProperty('schema')

    await request.delete(`http://localhost:8080/api/sessions/${sessionId}`)
  })

  test('routing: set_data_context on switch db request', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('切换到本地数据库')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_set_data_context')
    expect(calls.length).toBeGreaterThanOrEqual(1)
    const params = calls[0].params as any
    expect(params).toBeDefined()
    expect(params.selectedLevel).toBeDefined()
  })

  test('contract: set_data_context rejects missing selectedLevel', async ({ request }) => {
    const c = adapterClient(request)
    const sessionRes = await c.createSession({ connectionId: WORKING_CONN_ID })
    expect(sessionRes.ok()).toBe(true)
    const sessionId = (await sessionRes.json()).id

    const rpc = await c.mcpCall('datatalk_set_data_context', {
      connectionId: WORKING_CONN_ID,
      database: 'testdb',
    })
    // Missing selectedLevel should error
    expect(rpc.error).toBeDefined()

    await request.delete(`http://localhost:8080/api/sessions/${sessionId}`)
  })

  test('contract: set_data_context requires database when selectedLevel=database', async ({ request }) => {
    const c = adapterClient(request)
    const sessionRes = await c.createSession({ connectionId: WORKING_CONN_ID })
    expect(sessionRes.ok()).toBe(true)
    const sessionId = (await sessionRes.json()).id

    const rpc = await c.mcpCall('datatalk_set_data_context', {
      connectionId: WORKING_CONN_ID,
      selectedLevel: 'database',
    })
    expect(rpc.error).toBeDefined()

    await request.delete(`http://localhost:8080/api/sessions/${sessionId}`)
  })

  test('routing: resolve_use_target on use statement', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('use testdb')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_resolve_use_target')
    expect(calls.length).toBeGreaterThanOrEqual(1)
  })

  test('contract: resolve_use_target not_found for unknown', async ({ request }) => {
    const c = adapterClient(request)
    const sessionRes = await c.createSession({ connectionId: WORKING_CONN_ID })
    expect(sessionRes.ok()).toBe(true)
    const sessionId = (await sessionRes.json()).id

    const rpc = await c.mcpCall('datatalk_resolve_use_target', {
      target: 'unknown_db_xx_999',
    })
    expect(rpc.error).toBeUndefined()
    const result = rpc.result as any
    expect(result).toBeDefined()
    expect(result.status).toBe('not_found')

    await request.delete(`http://localhost:8080/api/sessions/${sessionId}`)
  })

  test('routing: list_connection_targets on listing dbs', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('列一下当前连接里有哪些数据库')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_list_connection_targets')
    expect(calls.length).toBeGreaterThanOrEqual(1)
  })

  test('contract: list_connection_targets without active connection errors', async ({ request }) => {
    const c = adapterClient(request)
    const sessionRes = await c.createSession({})
    expect(sessionRes.ok()).toBe(true)
    const sessionId = (await sessionRes.json()).id

    // REST endpoint requires active connection or explicit connectionId
    const res = await request.get(`http://localhost:8080/api/sessions/${sessionId}/data-context/targets`)
    expect(res.status()).toBeGreaterThanOrEqual(400)

    await request.delete(`http://localhost:8080/api/sessions/${sessionId}`)
  })

  test('routing: list_connections on saved connections question', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('我有哪些保存的数据源')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_list_connections')
    expect(calls.length).toBeGreaterThanOrEqual(1)
  })

  test('routing-negative: list_connections must NOT trigger on greetings', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('你好')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_list_connections')
    expect(calls.length).toBe(0)
  })

  test('contract: list_connections returns seed connections', async ({ request }) => {
    const c = adapterClient(request)
    const rpc = await c.mcpCall('datatalk_list_connections', {})
    expect(rpc.error).toBeUndefined()
    const result = rpc.result as any
    expect(result).toBeDefined()
    expect(Array.isArray(result.connections)).toBe(true)
    const ids = result.connections.map((c: any) => c.id)
    expect(ids).toContain(WORKING_CONN_ID)
  })

  test('routing: select_connection on switch connection request', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('切到本地数据库那个连接')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_select_connection')
    expect(calls.length).toBeGreaterThanOrEqual(1)
  })

  test('contract: select_connection missing connectionId errors', async ({ request }) => {
    const c = adapterClient(request)
    const rpc = await c.mcpCall('datatalk_select_connection', {})
    expect(rpc.error).toBeDefined()
  })

  test('contract: select_connection not_found for bad id', async ({ request }) => {
    const c = adapterClient(request)
    const sessionRes = await c.createSession({})
    expect(sessionRes.ok()).toBe(true)
    const sessionId = (await sessionRes.json()).id

    const rpc = await c.mcpCall('datatalk_select_connection', {
      connectionId: 'nonexistent-id-123',
    })
    expect(rpc.error).toBeDefined()

    await request.delete(`http://localhost:8080/api/sessions/${sessionId}`)
  })

  test('contract: select_connection updates get_data_context', async ({ request }) => {
    const c = adapterClient(request)
    // Use a DataTalk session that already has an OpenCode SID bound
    const ocSession = getLatestOpenCodeSession()
    if (!ocSession) {
      test.skip(true, 'No active OpenCode session available')
      return
    }
    const sessionId = ocSession.dataTalkSessionId

    const rpc = await c.mcpCall('datatalk_select_connection', {
      connectionId: WORKING_CONN_ID,
    }, { sessionId })
    expect(rpc.error).toBeUndefined()

    // Verify via REST that the session data context was updated
    const ctxRes = await c.getDataContext(sessionId)
    expect(ctxRes.ok()).toBe(true)
    const ctxBody = await ctxRes.json()
    expect(ctxBody.connectionId).toBe(WORKING_CONN_ID)
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// Connection Management
// ─────────────────────────────────────────────────────────────────────────────
test.describe('Connection Management', () => {
  test.setTimeout(60_000)

  test('routing: create_connection on new connection request', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('帮我新建一个 H2 内存连接 testconn')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_create_connection')
    expect(calls.length).toBeGreaterThanOrEqual(1)
    const params = calls[0].params as any
    expect(params.kind).toBeDefined()
  })

  test('contract: create_connection missing name errors', async ({ request }) => {
    const c = adapterClient(request)
    const rpc = await c.mcpCall('datatalk_create_connection', {
      kind: 'h2',
      databaseName: ':memory:',
    })
    expect(rpc.error).toBeDefined()
  })

  test('contract: create_connection sqlite missing databaseName accepted (schema optional)', async ({ request }) => {
    const c = adapterClient(request)
    // SQLite schema does NOT require databaseName; only name + kind are required.
    const rpc = await c.mcpCall('datatalk_create_connection', {
      name: 'sqlite-test-' + Date.now(),
      kind: 'sqlite',
    })
    expect(rpc.error).toBeUndefined()
  })

  test('contract: create_connection mysql missing host errors', async ({ request }) => {
    const c = adapterClient(request)
    const rpc = await c.mcpCall('datatalk_create_connection', {
      name: 'mysql-test-' + Date.now(),
      kind: 'mysql',
      port: 3306,
      username: 'root',
      password: 'root',
    })
    expect(rpc.error).toBeDefined()
  })

  test('contract: create_connection then list_connections includes it', async ({ request }) => {
    const c = adapterClient(request)
    const name = 'e2e-mysql-' + Date.now()
    const createRpc = await c.mcpCall('datatalk_create_connection', {
      name,
      kind: 'mysql',
      host: 'localhost',
      port: 3306,
      username: 'root',
      password: 'root',
    })
    expect(createRpc.error).toBeUndefined()
    const connId = (createRpc.result as any).id

    const listRpc = await c.mcpCall('datatalk_list_connections', {})
    expect(listRpc.error).toBeUndefined()
    const names = (listRpc.result as any).connections.map((x: any) => x.name)
    expect(names).toContain(name)

    // Cleanup
    await request.delete(`http://localhost:8080/api/connections/${connId}`)
  })

  test('routing: test_connection on test request', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('测一下本地数据库连接通不通')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_test_connection')
    expect(calls.length).toBeGreaterThanOrEqual(1)
  })

  test('contract: test_connection not_found for bad id', async ({ request }) => {
    const c = adapterClient(request)
    const rpc = await c.mcpCall('datatalk_test_connection', {
      connectionId: 'nonexistent-id-123',
    })
    expect(rpc.error).toBeDefined()
  })

  test('contract: test_connection reachable returns ok', async ({ request }) => {
    const c = adapterClient(request)
    // Use PostgreSQL connection which is known to be reachable
    const PG_CONN_ID = '31a8de09-b657-4a56-acb7-0ff8e26ebf86'
    const rpc = await c.mcpCall('datatalk_test_connection', {
      connectionId: PG_CONN_ID,
    })
    expect(rpc.error).toBeUndefined()
    const result = rpc.result as any
    expect(result).toBeDefined()
    expect(result.ok).toBe(true)
  })

  test('routing: update_connection_confirmable on modify request', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('把本地数据库连接的端口改成 3307')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_update_connection_confirmable')
    expect(calls.length).toBeGreaterThanOrEqual(1)
  })

  test('contract: update_connection_confirmable two-phase preview then commit', async ({ request }) => {
    const c = adapterClient(request)
    // Create a disposable mysql connection
    const createRpc = await c.mcpCall('datatalk_create_connection', {
      name: 'e2e-confirm-' + Date.now(),
      kind: 'mysql',
      host: 'localhost',
      port: 3306,
      username: 'root',
      password: 'root',
    })
    expect(createRpc.error).toBeUndefined()
    const connId = (createRpc.result as any).id

    const preview = await c.mcpCall('datatalk_update_connection_confirmable', {
      connectionId: connId,
      name: 'renamed-conn',
      kind: 'mysql',
      host: 'localhost',
      port: 3306,
      username: 'root',
      confirm: false,
    })
    expect(preview.error).toBeUndefined()
    const previewBody = preview.result as any
    expect(previewBody.confirm_required).toBe(true)
    expect(previewBody.confirmation_token).toBeTruthy()

    const commit = await c.mcpCall('datatalk_update_connection_confirmable', {
      connectionId: connId,
      name: 'renamed-conn',
      kind: 'mysql',
      host: 'localhost',
      port: 3306,
      username: 'root',
      confirm: true,
      confirmationToken: previewBody.confirmation_token,
    })
    expect(commit.error).toBeUndefined()
    expect((commit.result as any).ok).toBe(true)

    // Cleanup
    await request.delete(`http://localhost:8080/api/connections/${connId}`)
  })

  test('contract: update_connection_confirmable confirm=true without token rejected', async ({ request }) => {
    const c = adapterClient(request)
    const createRpc = await c.mcpCall('datatalk_create_connection', {
      name: 'e2e-confirm-reject-' + Date.now(),
      kind: 'mysql',
      host: 'localhost',
      port: 3306,
      username: 'root',
      password: 'root',
    })
    expect(createRpc.error).toBeUndefined()
    const connId = (createRpc.result as any).id

    const rpc = await c.mcpCall('datatalk_update_connection_confirmable', {
      connectionId: connId,
      name: 'x',
      kind: 'mysql',
      host: 'localhost',
      port: 3306,
      username: 'root',
      confirm: true,
    })
    expect(rpc.error).toBeDefined()

    await request.delete(`http://localhost:8080/api/connections/${connId}`)
  })
})
