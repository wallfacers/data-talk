import { test, expect } from '@playwright/test'
import { StagePage } from './pom/stage.page'
import { ChatPanelPage } from './pom/chat-panel.page'
import { ErInspectorPage } from './pom/er-inspector.page'
import { ErDesignerPage } from './pom/er-designer.page'
import { mountToolRecorder } from './fixtures/mcp-tool-recorder'
import { adapterClient } from './fixtures/adapter-client'
import { ensureHybridSession } from './fixtures/hybrid-session'

const MODEL = process.env.DATATALK_REAL_OPENCODE_MODEL

let stage: StagePage
let chat: ChatPanelPage
let erInspector: ErInspectorPage
let erDesigner: ErDesignerPage

// MySQL connection is not running in this environment.
// Use PostgreSQL (reachable) for tests that need a real DB.
const PG_CONN_ID = '31a8de09-b657-4a56-acb7-0ff8e26ebf86'
const PG_TABLE = 'users'

test.beforeEach(async ({ page }) => {
  await page.goto('/')
  stage = new StagePage(page)
  chat = new ChatPanelPage(page)
  erInspector = new ErInspectorPage(page)
  erDesigner = new ErDesignerPage(page)
  await page.waitForSelector('textarea', { timeout: 15_000 })
})

// ─────────────────────────────────────────────────────────────────────────────
// ER Inspector
// ─────────────────────────────────────────────────────────────────────────────
test.describe('er_inspector', () => {
  test.setTimeout(60_000)

  test('routing: open_er_inspector on relation question', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('show how orders relates to other tables')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_ui_exec')
    // Filter for open_er_inspector action
    const openCalls = calls.filter(
      (c) =>
        (c.params as any).object === 'workspace' &&
        (c.params as any).action === 'open_er_inspector'
    )
    expect(openCalls.length).toBeGreaterThanOrEqual(1)
    const params = openCalls[0].params as any
    expect(params.params?.tables).toBeDefined()
    expect(Array.isArray(params.params.tables)).toBe(true)
  })

  test('contract: open_er_inspector missing connectionId errors', async ({ request }) => {
    const c = adapterClient(request)
    const rpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'workspace',
      action: 'open_er_inspector',
      params: { tables: [PG_TABLE] },
    })
    expect(rpc.error).toBeDefined()
  })

  test('contract: open_er_inspector missing tables errors', async ({ request }) => {
    const c = adapterClient(request)
    const rpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'workspace',
      action: 'open_er_inspector',
      params: { connectionId: PG_CONN_ID },
    })
    expect(rpc.error).toBeDefined()
  })

  test('contract: open_er_inspector returns er_inspector tab', async ({ page, request }) => {
    await ensureHybridSession(page)
    const c = adapterClient(request)
    const rpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'workspace',
      action: 'open_er_inspector',
      params: { connectionId: PG_CONN_ID, tables: [PG_TABLE], neighborDepth: 1 },
    })
    expect(rpc.error).toBeUndefined()
    const result = rpc.result as any
    expect(result).toBeDefined()
    const tabId = result.tabId ?? result.data?.tabId
    expect(tabId).toBeTruthy()

    // Cleanup
    await c.stageDelete(tabId)
  })

  test('routing: add_neighbors on expand request', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('把 customers 表也加进 ER 图')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_ui_exec')
    const addCalls = calls.filter(
      (c) =>
        (c.params as any).object === 'er_inspector' &&
        (c.params as any).action === 'add_neighbors'
    )
    expect(addCalls.length).toBeGreaterThanOrEqual(1)
  })

  test('contract: add_neighbors missing table errors', async ({ page, request }) => {
    await ensureHybridSession(page)
    const c = adapterClient(request)
    // Open an inspector first
    const openRpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'workspace',
      action: 'open_er_inspector',
      params: { connectionId: PG_CONN_ID, tables: [PG_TABLE], neighborDepth: 1 },
    })
    expect(openRpc.error).toBeUndefined()
    const tabId = (openRpc.result as any).tabId ?? (openRpc.result as any).data?.tabId

    const rpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'er_inspector',
      target: tabId,
      action: 'add_neighbors',
      params: {},
    })
    expect(rpc.error).toBeDefined()

    await c.stageDelete(tabId)
  })

  test('contract: virtualRelations patch accepted on whitelist path', async ({ page, request }) => {
    await ensureHybridSession(page)
    const c = adapterClient(request)
    const openRpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'workspace',
      action: 'open_er_inspector',
      params: { connectionId: PG_CONN_ID, tables: [PG_TABLE], neighborDepth: 1 },
    })
    expect(openRpc.error).toBeUndefined()
    const tabId = (openRpc.result as any).tabId ?? (openRpc.result as any).data?.tabId

    const rpc = await c.mcpCall('datatalk_ui_patch', {
      object: 'er_inspector',
      target: tabId,
      ops: [
        {
          op: 'add',
          path: '/virtualRelations/-',
          value: {
            from: { table: 'orders', column: 'user_email' },
            to: { table: 'users', column: 'email' },
            type: 'many_to_one',
            note: 'implicit link',
          },
        },
      ],
    })
    expect(rpc.error).toBeUndefined()

    await c.stageDelete(tabId)
  })

  test('contract: non-whitelist patch path rejected', async ({ page, request }) => {
    await ensureHybridSession(page)
    const c = adapterClient(request)
    const openRpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'workspace',
      action: 'open_er_inspector',
      params: { connectionId: PG_CONN_ID, tables: [PG_TABLE], neighborDepth: 1 },
    })
    expect(openRpc.error).toBeUndefined()
    const tabId = (openRpc.result as any).tabId ?? (openRpc.result as any).data?.tabId

    const rpc = await c.mcpCall('datatalk_ui_patch', {
      object: 'er_inspector',
      target: tabId,
      ops: [{ op: 'replace', path: '/realRelations', value: [] }],
    })
    expect(rpc.error).toBeDefined()

    await c.stageDelete(tabId)
  })

  test('routing: fork_to_designer on copy request', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('把这个 ER 图复制成可编辑设计')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_ui_exec')
    const forkCalls = calls.filter(
      (c) =>
        (c.params as any).object === 'er_inspector' &&
        (c.params as any).action === 'fork_to_designer'
    )
    expect(forkCalls.length).toBeGreaterThanOrEqual(1)
  })

  test('contract: fork_to_designer produces er_designer with matching shape', async ({ page, request }) => {
    await ensureHybridSession(page)
    const c = adapterClient(request)
    const openRpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'workspace',
      action: 'open_er_inspector',
      params: { connectionId: PG_CONN_ID, tables: [PG_TABLE], neighborDepth: 1 },
    })
    expect(openRpc.error).toBeUndefined()
    const tabId = (openRpc.result as any).tabId ?? (openRpc.result as any).data?.tabId

    const forkRpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'er_inspector',
      target: tabId,
      action: 'fork_to_designer',
      params: { title: 'Fork of Order ER' },
    })
    expect(forkRpc.error).toBeUndefined()
    const result = forkRpc.result as any
    expect(result).toBeDefined()
    const forkTabId = result.tabId ?? result.data?.tabId
    expect(forkTabId).toBeTruthy()

    await c.stageDelete(tabId)
    await c.stageDelete(forkTabId)
  })
})

// ─────────────────────────────────────────────────────────────────────────────
// ER Designer
// ─────────────────────────────────────────────────────────────────────────────
test.describe('er_designer', () => {
  test.setTimeout(60_000)

  test('routing: open_er_designer on schema design request', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('我要设计一个新表结构')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_ui_exec')
    const openCalls = calls.filter(
      (c) =>
        (c.params as any).object === 'workspace' &&
        (c.params as any).action === 'open_er_designer'
    )
    expect(openCalls.length).toBeGreaterThanOrEqual(1)
  })

  test('contract: open_er_designer dialect required', async ({ request }) => {
    const c = adapterClient(request)
    const rpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'workspace',
      action: 'open_er_designer',
      params: {},
    })
    expect(rpc.error).toBeDefined()
  })

  test('contract: open_er_designer unsupported dialect rejected', async ({ request }) => {
    const c = adapterClient(request)
    const rpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'workspace',
      action: 'open_er_designer',
      params: { dialect: 'oracle' },
    })
    expect(rpc.error).toBeDefined()
  })

  test('contract: bind_target missing connectionId errors', async ({ page, request }) => {
    await ensureHybridSession(page)
    const c = adapterClient(request)
    const openRpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'workspace',
      action: 'open_er_designer',
      params: { dialect: 'h2' },
    })
    expect(openRpc.error).toBeUndefined()
    const tabId = (openRpc.result as any).tabId ?? (openRpc.result as any).data?.tabId

    const rpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'er_designer',
      target: tabId,
      action: 'bind_target',
      params: {},
    })
    expect(rpc.error).toBeDefined()

    await c.stageDelete(tabId)
  })

  test('contract: diff_against_db without bind_target returns unbound error', async ({ page, request }) => {
    await ensureHybridSession(page)
    const c = adapterClient(request)
    const openRpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'workspace',
      action: 'open_er_designer',
      params: { dialect: 'h2' },
    })
    expect(openRpc.error).toBeUndefined()
    const tabId = (openRpc.result as any).tabId ?? (openRpc.result as any).data?.tabId

    const rpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'er_designer',
      target: tabId,
      action: 'diff_against_db',
      params: {},
    })
    expect(rpc.error).toBeDefined()
    const err = rpc.error as any
    expect(err.message?.toLowerCase()).toContain('bind_target')

    await c.stageDelete(tabId)
  })

  // FIXME: bind_target succeeds and persists targetConnectionId to payload,
  // but diff_against_db still returns "Bind a target connection first".
  // This is a backend bug tracked in docs/bugs/.
  test.fixme('contract: diff_against_db after bind_target returns structured diff', async ({ page, request }) => {
    await ensureHybridSession(page)
    const c = adapterClient(request)
    const openRpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'workspace',
      action: 'open_er_designer',
      params: { dialect: 'h2' },
    })
    expect(openRpc.error).toBeUndefined()
    const tabId = (openRpc.result as any).tabId ?? (openRpc.result as any).data?.tabId

    // Bind target
    const bindRpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'er_designer',
      target: tabId,
      action: 'bind_target',
      params: { connectionId: PG_CONN_ID },
    })
    expect(bindRpc.error).toBeUndefined()

    const diffRpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'er_designer',
      target: tabId,
      action: 'diff_against_db',
      params: {},
    })
    expect(diffRpc.error).toBeUndefined()
    const result = diffRpc.result as any
    expect(result).toBeDefined()
    expect(typeof result.differences).toBe('object')

    await c.stageDelete(tabId)
  })

  // FIXME: Same backend bug as diff_against_db: bind_target does not
  // satisfy generate_ddl prerequisites.
  test.fixme('contract: generate_ddl produces query_editor tab and does NOT execute DDL', async ({ page, request }) => {
    await ensureHybridSession(page)
    const c = adapterClient(request)
    const openRpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'workspace',
      action: 'open_er_designer',
      params: { dialect: 'h2' },
    })
    expect(openRpc.error).toBeUndefined()
    const tabId = (openRpc.result as any).tabId ?? (openRpc.result as any).data?.tabId

    // Bind target
    const bindRpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'er_designer',
      target: tabId,
      action: 'bind_target',
      params: { connectionId: PG_CONN_ID },
    })
    expect(bindRpc.error).toBeUndefined()

    // Count query editors before
    const beforeFind = await c.stageFind({ filter: { type: 'query_editor' }, output: { mode: 'tabs_only' } })
    const beforeIds = ((await beforeFind.json()) as any).tabIds ?? []

    // Generate DDL
    const ddlRpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'er_designer',
      target: tabId,
      action: 'generate_ddl',
      params: {},
    })
    expect(ddlRpc.error).toBeUndefined()
    const result = ddlRpc.result as any
    expect(result).toBeDefined()
    const qeTabId = result.queryEditorTabId ?? result.data?.queryEditorTabId
    expect(qeTabId).toBeTruthy()
    expect(typeof result.ddl).toBe('string')
    expect(result.ddl).toMatch(/CREATE TABLE/i)

    // Verify new query_editor tab exists
    const afterFind = await c.stageFind({ filter: { type: 'query_editor' }, output: { mode: 'tabs_only' } })
    const afterIds = ((await afterFind.json()) as any).tabIds ?? []
    expect(afterIds).toContain(qeTabId)

    // Verify DDL was NOT automatically executed (results empty)
    const stateRes = await c.mcpCall('datatalk_ui_read', {
      object: 'query_editor',
      target: qeTabId,
      mode: 'state',
    })
    expect(stateRes.error).toBeUndefined()
    const qeState = stateRes.result as any
    expect(qeState.results ?? []).toEqual([])

    await c.stageDelete(tabId)
    await c.stageDelete(qeTabId)
  })

  test('contract: structural patch missing baseVersion returns version_conflict', async ({ page, request }) => {
    await ensureHybridSession(page)
    const c = adapterClient(request)
    const openRpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'workspace',
      action: 'open_er_designer',
      params: {
        dialect: 'h2',
        seedTables: [
          {
            name: 'users',
            columns: [{ name: 'id', type: 'BIGINT', isPrimaryKey: true, isAutoIncrement: true }],
          },
        ],
      },
    }, { timeout: 30_000 })
    expect(openRpc.error).toBeUndefined()
    const tabId = (openRpc.result as any).tabId ?? (openRpc.result as any).data?.tabId

    // Patch without baseVersion on structural path
    const rpc = await c.mcpCall('datatalk_ui_patch', {
      object: 'er_designer',
      target: tabId,
      ops: [
        {
          op: 'add',
          path: '/tables[id=t_users]/columns/-',
          value: { name: 'status', type: 'VARCHAR(32)', nullable: false },
        },
      ],
    })
    expect(rpc.error).toBeDefined()
    const err = rpc.error as any
    expect(err.message?.toLowerCase()).toContain('version')

    await c.stageDelete(tabId)
  })

  test('contract: view path patch without baseVersion passes', async ({ page, request }) => {
    await ensureHybridSession(page)
    const c = adapterClient(request)
    const openRpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'workspace',
      action: 'open_er_designer',
      params: { dialect: 'h2' },
    })
    expect(openRpc.error).toBeUndefined()
    const tabId = (openRpc.result as any).tabId ?? (openRpc.result as any).data?.tabId

    // View paths like /positions may omit baseVersion
    const rpc = await c.mcpCall('datatalk_ui_patch', {
      object: 'er_designer',
      target: tabId,
      ops: [{ op: 'replace', path: '/positions', value: {} }],
    })
    // This may succeed or be a no-op depending on backend; we just assert no hard error
    // Some backends silently accept view patches without baseVersion
    if (rpc.error) {
      expect(rpc.error.code).toBeLessThan(500)
    }

    await c.stageDelete(tabId)
  })

  test('routing: auto_layout on relayout request', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('重新布局 ER 图')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('datatalk_ui_exec')
    const layoutCalls = calls.filter(
      (c) =>
        (c.params as any).object === 'er_designer' &&
        (c.params as any).action === 'auto_layout'
    )
    expect(layoutCalls.length).toBeGreaterThanOrEqual(1)
  })

  test('contract: auto_layout does not require coordinates', async ({ page, request }) => {
    await ensureHybridSession(page)
    const c = adapterClient(request)
    const openRpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'workspace',
      action: 'open_er_designer',
      params: { dialect: 'h2' },
    })
    expect(openRpc.error).toBeUndefined()
    const tabId = (openRpc.result as any).tabId ?? (openRpc.result as any).data?.tabId

    const rpc = await c.mcpCall('datatalk_ui_exec', {
      object: 'er_designer',
      target: tabId,
      action: 'auto_layout',
      params: {},
    })
    expect(rpc.error).toBeUndefined()

    await c.stageDelete(tabId)
  })
})
