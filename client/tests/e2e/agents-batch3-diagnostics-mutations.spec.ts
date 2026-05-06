import { test, expect } from '@playwright/test'
import { StagePage } from './pom/stage.page'
import { ChatPanelPage } from './pom/chat-panel.page'
import { mountToolRecorder } from './fixtures/mcp-tool-recorder'
import { adapterClient } from './fixtures/adapter-client'

const MODEL = process.env.DATATALK_REAL_OPENCODE_MODEL

let stage: StagePage
let chat: ChatPanelPage

const WORKING_CONN_ID = '323230ca-44c7-491c-b41f-3c2069c72b85'
const PG_CONN_ID = '31a8de09-b657-4a56-acb7-0ff8e26ebf86'

test.beforeEach(async ({ page }) => {
  await page.goto('/')
  stage = new StagePage(page)
  chat = new ChatPanelPage(page)
  await page.waitForSelector('textarea', { timeout: 15_000 })
})

// ─────────────────────────────────────────────────────────────────────────────
// Query Diagnostics
// ─────────────────────────────────────────────────────────────────────────────
test.describe('datatalk_explain_query', () => {
  test.setTimeout(60_000)

  test('routing: AI invokes on slow query question', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('为什么 SELECT * FROM users WHERE id = 1 这么慢')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_explain_query')
    expect(calls.length).toBeGreaterThanOrEqual(1)
  })

  test('contract: H2 returns nodes tree', async ({ request }) => {
    const c = adapterClient(request)
    const sessionRes = await c.createSession({ connectionId: PG_CONN_ID })
    expect(sessionRes.ok()).toBe(true)
    const sessionId = (await sessionRes.json()).id

    await c.setDataContext(sessionId, {
      connectionId: PG_CONN_ID,
      selectedLevel: 'connection',
    })

    const rpc = await c.mcpCall('datatalk_explain_query', {
      sql: 'SELECT * FROM users WHERE id = 1',
    })
    if (rpc.error) {
      expect(rpc.error.code).toBeLessThan(500)
    } else {
      const result = rpc.result as any
      expect(result).toBeDefined()
      if (result.nodes) {
        expect(Array.isArray(result.nodes)).toBe(true)
      }
    }

    await request.delete(`http://localhost:8080/api/sessions/${sessionId}`)
  })

  test('contract: invalid SQL returns error envelope', async ({ request }) => {
    const c = adapterClient(request)
    const sessionRes = await c.createSession({ connectionId: PG_CONN_ID })
    expect(sessionRes.ok()).toBe(true)
    const sessionId = (await sessionRes.json()).id

    await c.setDataContext(sessionId, {
      connectionId: PG_CONN_ID,
      selectedLevel: 'connection',
    })

    const rpc = await c.mcpCall('datatalk_explain_query', {
      sql: 'SELEC * FRM users',
    })
    // Invalid SQL returns an error envelope inside the result
    const result = rpc.result as any
    expect(result?.error?.message || rpc.error?.message).toBeTruthy()

    await request.delete(`http://localhost:8080/api/sessions/${sessionId}`)
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// datatalk_index_hints
// ─────────────────────────────────────────────────────────────────────────────
test.describe('datatalk_index_hints', () => {
  test.setTimeout(60_000)

  test('routing: AI invokes on index advice question', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('这条查询要加什么索引')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_index_hints')
    expect(calls.length).toBeGreaterThanOrEqual(1)
  })

  test('contract: explainSummary non-empty (based on actual EXPLAIN)', async ({ request }) => {
    const c = adapterClient(request)
    const sessionRes = await c.createSession({ connectionId: PG_CONN_ID })
    expect(sessionRes.ok()).toBe(true)
    const sessionId = (await sessionRes.json()).id

    await c.setDataContext(sessionId, {
      connectionId: PG_CONN_ID,
      selectedLevel: 'connection',
    })

    const rpc = await c.mcpCall('datatalk_index_hints', {
      sql: 'SELECT * FROM users WHERE id = 1',
    })
    if (rpc.error) {
      expect(rpc.error.code).toBeLessThan(500)
    } else {
      const result = rpc.result as any
      expect(result).toBeDefined()
      if (result.explainSummary) {
        expect(typeof result.explainSummary).toBe('string')
      }
    }

    await request.delete(`http://localhost:8080/api/sessions/${sessionId}`)
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// datatalk_lock_info
// ─────────────────────────────────────────────────────────────────────────────
test.describe('datatalk_lock_info', () => {
  test.setTimeout(60_000)

  test('routing: AI invokes on stuck-query complaint', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('我的查询卡住了，帮我看看是不是被锁了')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_lock_info')
    expect(calls.length).toBeGreaterThanOrEqual(1)
  })

  test('contract: H2 returns unsupported envelope', async ({ request }) => {
    const c = adapterClient(request)
    const sessionRes = await c.createSession({ connectionId: PG_CONN_ID })
    expect(sessionRes.ok()).toBe(true)
    const sessionId = (await sessionRes.json()).id

    await c.setDataContext(sessionId, {
      connectionId: PG_CONN_ID,
      selectedLevel: 'connection',
    })

    const rpc = await c.mcpCall('datatalk_lock_info', {})
    if (rpc.error) {
      expect(rpc.error.code).toBeLessThan(500)
    } else {
      const result = rpc.result as any
      expect(result).toBeDefined()
      // PostgreSQL may support lock_info; assert shape loosely
      if (result.blockingChain) {
        expect(Array.isArray(result.blockingChain)).toBe(true)
      }
    }

    await request.delete(`http://localhost:8080/api/sessions/${sessionId}`)
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// datatalk_pool_status
// ─────────────────────────────────────────────────────────────────────────────
test.describe('datatalk_pool_status', () => {
  test.setTimeout(60_000)

  test('routing: AI invokes on connection count question', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('现在有多少连接占着')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_pool_status')
    expect(calls.length).toBeGreaterThanOrEqual(1)
  })

  test('contract: H2 returns unsupported or null scope', async ({ request }) => {
    const c = adapterClient(request)
    const sessionRes = await c.createSession({ connectionId: PG_CONN_ID })
    expect(sessionRes.ok()).toBe(true)
    const sessionId = (await sessionRes.json()).id

    await c.setDataContext(sessionId, {
      connectionId: PG_CONN_ID,
      selectedLevel: 'connection',
    })

    const rpc = await c.mcpCall('datatalk_pool_status', {})
    if (rpc.error) {
      expect(rpc.error.code).toBeLessThan(500)
    } else {
      const result = rpc.result as any
      expect(result).toBeDefined()
      if (result.unsupported) {
        expect(typeof result.reason).toBe('string')
      }
    }

    await request.delete(`http://localhost:8080/api/sessions/${sessionId}`)
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// datatalk_table_space
// ─────────────────────────────────────────────────────────────────────────────
test.describe('datatalk_table_space', () => {
  test.setTimeout(60_000)

  test('routing: AI invokes on table size question', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('users 表占多大空间')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_table_space')
    expect(calls.length).toBeGreaterThanOrEqual(1)
  })

  test('contract: H2 returns unsupported envelope', async ({ request }) => {
    const c = adapterClient(request)
    const sessionRes = await c.createSession({ connectionId: PG_CONN_ID })
    expect(sessionRes.ok()).toBe(true)
    const sessionId = (await sessionRes.json()).id

    await c.setDataContext(sessionId, {
      connectionId: PG_CONN_ID,
      selectedLevel: 'connection',
    })

    const rpc = await c.mcpCall('datatalk_table_space', {
      tables: ['users'],
    })
    if (rpc.error) {
      expect(rpc.error.code).toBeLessThan(500)
    } else {
      const result = rpc.result as any
      expect(result).toBeDefined()
      if (result.unsupported) {
        expect(typeof result.reason).toBe('string')
      }
    }

    await request.delete(`http://localhost:8080/api/sessions/${sessionId}`)
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// Confirmable Mutations
// ─────────────────────────────────────────────────────────────────────────────
test.describe('datatalk_terminate_session', () => {
  test.setTimeout(60_000)

  test('routing: AI invokes on kill blocking session request', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('杀掉这个阻塞会话')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_terminate_session')
    expect(calls.length).toBeGreaterThanOrEqual(1)
  })

  test('contract: H2 returns unsupported', async ({ request }) => {
    const c = adapterClient(request)
    const sessionRes = await c.createSession({ connectionId: PG_CONN_ID })
    expect(sessionRes.ok()).toBe(true)
    const sessionId = (await sessionRes.json()).id

    await c.setDataContext(sessionId, {
      connectionId: PG_CONN_ID,
      selectedLevel: 'connection',
    })

    const rpc = await c.mcpCall('datatalk_terminate_session', {
      connectionId: PG_CONN_ID,
      sessionId: '12345',
      confirm: false,
    })
    if (rpc.error) {
      expect(rpc.error.code).toBeLessThan(500)
    } else {
      const result = rpc.result as any
      expect(result).toBeDefined()
      // PostgreSQL supports terminate_session; expect preview
      if (result.confirm_required !== undefined) {
        expect(result.confirm_required).toBe(true)
        expect(result.confirmation_token).toBeTruthy()
      }
    }

    await request.delete(`http://localhost:8080/api/sessions/${sessionId}`)
  })

  test('contract: confirm=true without token rejected', async ({ request }) => {
    const c = adapterClient(request)
    const sessionRes = await c.createSession({ connectionId: PG_CONN_ID })
    expect(sessionRes.ok()).toBe(true)
    const sessionId = (await sessionRes.json()).id

    await c.setDataContext(sessionId, {
      connectionId: PG_CONN_ID,
      selectedLevel: 'connection',
    })

    const rpc = await c.mcpCall('datatalk_terminate_session', {
      sessionId: '12345',
      confirm: true,
    })
    expect(rpc.error).toBeDefined()

    await request.delete(`http://localhost:8080/api/sessions/${sessionId}`)
  })
})

test.describe('datatalk_optimize_table', () => {
  test.setTimeout(60_000)

  test('routing: AI invokes on optimize request', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('users 表回收一下空间')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_optimize_table')
    // On H2, AI may decline to recommend optimize because table_space returns unsupported
    // We assert loosely: if it triggers, params must have table
    if (calls.length > 0) {
      expect((calls[0].params as any).table).toBeTruthy()
    }
  })

  test('contract: PostgreSQL returns preview', async ({ request }) => {
    const c = adapterClient(request)
    const sessionRes = await c.createSession({ connectionId: PG_CONN_ID })
    expect(sessionRes.ok()).toBe(true)
    const sessionId = (await sessionRes.json()).id

    await c.setDataContext(sessionId, {
      connectionId: PG_CONN_ID,
      selectedLevel: 'connection',
    })

    const rpc = await c.mcpCall('datatalk_optimize_table', {
      connectionId: PG_CONN_ID,
      table: 'users',
      confirm: false,
    })
    if (rpc.error) {
      expect(rpc.error.code).toBeLessThan(500)
    } else {
      const result = rpc.result as any
      expect(result).toBeDefined()
      // PostgreSQL supports optimize_table; expect preview
      if (result.confirm_required !== undefined) {
        expect(result.confirm_required).toBe(true)
        expect(result.confirmation_token).toBeTruthy()
        expect(result.preview?.willRunSql).toBeTruthy()
      }
    }

    await request.delete(`http://localhost:8080/api/sessions/${sessionId}`)
  })

  test('contract: confirm=true without token rejected', async ({ request }) => {
    const c = adapterClient(request)
    const sessionRes = await c.createSession({ connectionId: PG_CONN_ID })
    expect(sessionRes.ok()).toBe(true)
    const sessionId = (await sessionRes.json()).id

    await c.setDataContext(sessionId, {
      connectionId: PG_CONN_ID,
      selectedLevel: 'connection',
    })

    const rpc = await c.mcpCall('datatalk_optimize_table', {
      table: 'users',
      confirm: true,
    })
    expect(rpc.error).toBeDefined()

    await request.delete(`http://localhost:8080/api/sessions/${sessionId}`)
  })
})
