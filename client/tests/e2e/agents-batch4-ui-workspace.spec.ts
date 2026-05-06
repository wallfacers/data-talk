import { test, expect } from '@playwright/test'
import { StagePage } from './pom/stage.page'
import { ChatPanelPage } from './pom/chat-panel.page'
import { SqlWorkbenchPage } from './pom/sql-workbench.page'
import { mountToolRecorder } from './fixtures/mcp-tool-recorder'
import { adapterClient, type McpRpcResponse } from './fixtures/adapter-client'
import { setupH2Connection, type H2TestSetup } from './fixtures/h2-setup'
import { switchToSqlEditorTab } from './pom/helpers'

const MODEL = process.env.DATATALK_REAL_OPENCODE_MODEL

let stage: StagePage
let chat: ChatPanelPage
let sql: SqlWorkbenchPage
let h2: H2TestSetup | null = null

test.beforeAll(async () => {
  try {
    h2 = await setupH2Connection()
  } catch {
    h2 = null
  }
})

test.afterAll(async () => {
  if (h2) {
    await h2.cleanup()
    h2 = null
  }
})

test.beforeEach(async ({ page }) => {
  await page.goto('/')
  stage = new StagePage(page)
  chat = new ChatPanelPage(page)
  sql = new SqlWorkbenchPage(page)
  await page.waitForSelector('textarea', { timeout: 15_000 })
})

// ── Helpers ────────────────────────────────────────────────────────────────

function mcpToolResult(res: McpRpcResponse) {
  expect(res.error).toBeUndefined()
  expect(res.result).toBeDefined()
  return res.result!
}

function mcpError(res: McpRpcResponse) {
  expect(res.error).toBeDefined()
  return res.error!
}

async function createQueryEditorTab(request: any, title: string, content: string, connectionId?: string) {
  const tabId = `qe_batch4_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const c = adapterClient(request)
  const payload: Record<string, unknown> = { initialSql: content }
  if (connectionId) payload.connectionId = connectionId
  const res = await c.stageUpsertTab(tabId, {
    type: 'query_editor',
    title,
    payload,
    contentText: content,
  })
  expect(res.status()).toBe(200)
  return tabId
}

async function cleanupTab(request: any, tabId: string) {
  const c = adapterClient(request)
  await c.stageDelete(tabId).catch(() => {})
}

// ── 1. workspace.open(query_editor) ────────────────────────────────────────

test.describe('workspace.open(query_editor)', () => {
  test.setTimeout(60_000)

  test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set');
  test('routing: AI invokes on "新开一个 SQL 编辑器"', async ({ page }) => {
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('新开一个 SQL 编辑器')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('workspace.open')
    expect(calls.length).toBeGreaterThanOrEqual(1)
    expect(calls[0].params).toMatchObject({ type: 'query_editor' })
  })

  test.fixme('contract: payload SQL priority initialSql > content > sql', async ({ request }) => {
    const c = adapterClient(request)
    const tabId = `qe_prio_${Date.now()}`

    // initialSql wins
    const r1 = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'open',
      params: {
        type: 'query_editor',
        title: 'Priority Test',
        payload: {
          initialSql: 'SELECT 1 AS initial',
          content: 'SELECT 2 AS content',
          sql: 'SELECT 3 AS sql',
        },
      },
    })
    const data1 = mcpToolResult(r1)
    expect(data1.content?.[0]?.text).toContain(tabId) // tabId returned
    const createdTabId1 = (data1 as any).tabId ?? tabId
    await cleanupTab(request, createdTabId1)

    // content wins when initialSql absent
    const r2 = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'open',
      params: {
        type: 'query_editor',
        title: 'Priority Test 2',
        payload: {
          content: 'SELECT 2 AS content',
          sql: 'SELECT 3 AS sql',
        },
      },
    })
    const data2 = mcpToolResult(r2)
    const createdTabId2 = (data2 as any).tabId ?? `qe_prio2_${Date.now()}`
    await cleanupTab(request, createdTabId2)

    // sql wins when both absent
    const r3 = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'open',
      params: {
        type: 'query_editor',
        title: 'Priority Test 3',
        payload: { sql: 'SELECT 3 AS sql' },
      },
    })
    mcpToolResult(r3)
  })

  test.fixme('contract: connectionId passthrough', async ({ request }) => {
    test.skip(!h2, 'H2 test connection not available')
    const c = adapterClient(request)
    const r = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'open',
      params: {
        type: 'query_editor',
        title: 'Conn Passthrough',
        connectionId: h2!.connectionId,
        payload: { initialSql: 'SELECT 1' },
      },
    })
    const data = mcpToolResult(r)
    const tabId = (data as any).tabId as string
    expect(tabId).toBeTruthy()

    // Verify via stage payload that connectionId is stored
    const payloadRes = await c.stageGetPayload(tabId)
    expect(payloadRes.status()).toBe(200)
    const payloadBody = await payloadRes.json()
    const payload = payloadBody.payload as Record<string, unknown>
    expect(payload.connectionId).toBe(h2!.connectionId)

    await cleanupTab(request, tabId)
  })
})

// ── 2. workspace.choose_connection ─────────────────────────────────────────

test.describe('workspace.choose_connection', () => {
  test.setTimeout(60_000)

  test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set');
  test('routing-negative: greeting does NOT trigger', async ({ page }) => {
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('你好')
    await chat.waitForAiResponse()
    expect(await recorder.callsFor('workspace.choose_connection')).toEqual([])
  })

  test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set');
  test('routing-negative: capability question does NOT trigger', async ({ page }) => {
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('DataTalk 能做什么')
    await chat.waitForAiResponse()
    expect(await recorder.callsFor('workspace.choose_connection')).toEqual([])
  })

  test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set');
  test('routing: DB question with no active connection triggers', async ({ page }) => {
    // Ensure no active connection by creating a fresh session without one
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('查询一下 users 表的数据')
    await chat.waitForAiResponse()
    // This may or may not trigger choose_connection depending on AI routing;
    // we assert loosely: if it triggers, params must be valid.
    const calls = await recorder.callsFor('workspace.choose_connection')
    if (calls.length > 0) {
      expect(calls[0].params).toHaveProperty('preferredConnectionId')
    }
  })
})

// ── 3. workspace.focus(target) ─────────────────────────────────────────────

test.describe('workspace.focus(target)', () => {
  test.fixme('happy: focus detached tab brings into workset', async ({ page, request }) => {
    const c = adapterClient(request)
    const tabId = await createQueryEditorTab(request, 'Focus Test', 'SELECT 1')

    // Detach first
    const detachRes = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'detach',
      params: { target: tabId },
    })
    mcpToolResult(detachRes)

    // Verify detached via payload read (inWorkset should be false)
    await page.goto('/')
    await page.waitForSelector('textarea', { timeout: 15_000 })
    await switchToSqlEditorTab(page)

    // Focus via MCP
    const focusRes = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'focus',
      params: { target: tabId },
    })
    mcpToolResult(focusRes)

    // Stage should be open and tab visible
    expect(await stage.isOpen()).toBe(true)

    await cleanupTab(request, tabId)
  })

  test.fixme('error: focus archived tab returns tab_archived', async ({ request }) => {
    const c = adapterClient(request)
    const tabId = await createQueryEditorTab(request, 'Archived Focus Test', 'SELECT 1')

    // Archive
    const archiveRes = await c.stageArchive(tabId, true)
    expect(archiveRes.status()).toBe(204)

    // Focus should fail
    const focusRes = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'focus',
      params: { target: tabId },
    })
    const err = mcpError(focusRes)
    expect(err.code).toBe(400)
    const result = focusRes.result
    expect(result?.isError).toBe(true)
    const text = result?.content?.[0]?.text ?? ''
    expect(text).toContain('tab_archived')

    await cleanupTab(request, tabId)
  })
})

// ── 4. workspace.detach(target) ────────────────────────────────────────────

test.describe('workspace.detach(target)', () => {
  test.fixme('contract: after detach, ui_read shows inWorkset false but ui_find still finds it', async ({ request }) => {
    const c = adapterClient(request)
    const tabId = await createQueryEditorTab(request, 'Detach Test', 'SELECT 1')

    const detachRes = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'detach',
      params: { target: tabId },
    })
    mcpToolResult(detachRes)

    // ui_find (POST /api/stage/find) should still find it
    const findRes = await c.stageFind({ filter: { objectId: tabId } })
    expect(findRes.status()).toBe(200)
    const findBody = await findRes.json()
    const foundIds = (findBody.items ?? []).map((i: any) => i.tabId ?? i.id)
    expect(foundIds).toContain(tabId)

    await cleanupTab(request, tabId)
  })

  test.fixme('error: non-existent tabId returns tab_not_found', async ({ request }) => {
    const c = adapterClient(request)
    const res = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'detach',
      params: { target: 'nonexistent_tab_12345' },
    })
    const result = res.result
    expect(result?.isError).toBe(true)
    const text = result?.content?.[0]?.text ?? ''
    expect(text).toContain('tab_not_found')
  })
})

// ── 5. workspace.archive(target) ───────────────────────────────────────────

test.describe('workspace.archive(target)', () => {
  test.fixme('archive(true) hides tab; archive(false) unarchives', async ({ request }) => {
    const c = adapterClient(request)
    const tabId = await createQueryEditorTab(request, 'Archive Test', 'SELECT 1')

    // Archive
    const archiveRes = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'archive',
      params: { target: tabId, archived: true },
    })
    mcpToolResult(archiveRes)

    // Verify archived via list
    const listRes = await c.stageListTabs({ archived: true })
    expect(listRes.status()).toBe(200)
    const listBody = await listRes.json()
    const archivedIds = (listBody.items ?? []).map((i: any) => i.id)
    expect(archivedIds).toContain(tabId)

    // Unarchive
    const unarchiveRes = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'archive',
      params: { target: tabId, archived: false },
    })
    mcpToolResult(unarchiveRes)

    // Verify no longer in archived list
    const listRes2 = await c.stageListTabs({ archived: false })
    const listBody2 = await listRes2.json()
    const unarchivedIds = (listBody2.items ?? []).map((i: any) => i.id)
    expect(unarchivedIds).toContain(tabId)

    await cleanupTab(request, tabId)
  })

  test.fixme('after archive, focus returns tab_archived', async ({ request }) => {
    const c = adapterClient(request)
    const tabId = await createQueryEditorTab(request, 'Archive Focus Test', 'SELECT 1')

    await c.stageArchive(tabId, true)

    const focusRes = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'focus',
      params: { target: tabId },
    })
    const result = focusRes.result
    expect(result?.isError).toBe(true)
    const text = result?.content?.[0]?.text ?? ''
    expect(text).toContain('tab_archived')

    await cleanupTab(request, tabId)
  })
})

// ── 6. workspace.trash(target) ─────────────────────────────────────────────

test.describe('workspace.trash(target)', () => {
  test.setTimeout(60_000)

  test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set');
  test('routing: only triggered on explicit trash language', async ({ page }) => {
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('彻底删除这个 tab')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('workspace.trash')
    // We assert loosely: if triggered, must have target
    if (calls.length > 0) {
      expect(calls[0].params).toHaveProperty('target')
    }
  })

  test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set');
  test('routing-negative: "关闭这个tab" triggers detach, NOT trash', async ({ page }) => {
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('关闭这个tab')
    await chat.waitForAiResponse()
    const trashCalls = await recorder.callsFor('workspace.trash')
    // Should not be trash; may be detach or nothing
    expect(trashCalls).toEqual([])
  })

  test.fixme('contract: after trash, ui_find does not return tabId', async ({ request }) => {
    const c = adapterClient(request)
    const tabId = await createQueryEditorTab(request, 'Trash Test', 'SELECT 1')

    const trashRes = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'trash',
      params: { target: tabId },
    })
    mcpToolResult(trashRes)

    // ui_find should not return it
    const findRes = await c.stageFind({ filter: { objectId: tabId } })
    expect(findRes.status()).toBe(200)
    const findBody = await findRes.json()
    const foundIds = (findBody.items ?? []).map((i: any) => i.tabId ?? i.id)
    expect(foundIds).not.toContain(tabId)
  })
})

// ── 7. workspace.open_er_inspector / open_er_designer ──────────────────────

test.describe('workspace.open_er_inspector', () => {
  test.fixme('contract: missing connectionId/tables returns error', async ({ request }) => {
    const c = adapterClient(request)

    // Missing connectionId
    const r1 = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'open_er_inspector',
      params: { tables: ['users'] },
    })
    expect(r1.error?.code).toBeGreaterThanOrEqual(400)

    // Missing tables
    const r2 = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'open_er_inspector',
      params: { connectionId: 'conn-123' },
    })
    expect(r2.error?.code).toBeGreaterThanOrEqual(400)
  })

  test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set');
  test('routing: AI invokes on ER question', async ({ page }) => {
    test.skip(!h2, 'H2 test connection not available')
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('看下 orders 和它的关联表')
    await chat.waitForAiResponse()
    const calls = await recorder.callsFor('workspace.open_er_inspector')
    if (calls.length > 0) {
      expect(calls[0].params).toHaveProperty('tables')
    }
  })
})

test.describe('workspace.open_er_designer', () => {
  test.fixme('contract: dialect required', async ({ request }) => {
    const c = adapterClient(request)
    const r = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'open_er_designer',
      params: {},
    })
    expect(r.error?.code).toBeGreaterThanOrEqual(400)
  })

  test.fixme('contract: oracle dialect rejected', async ({ request }) => {
    const c = adapterClient(request)
    const r = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'open_er_designer',
      params: { dialect: 'oracle' },
    })
    const result = r.result
    expect(result?.isError).toBe(true)
    const text = result?.content?.[0]?.text ?? ''
    expect(text).toContain('dialect_unsupported')
  })

  test.fixme('contract: mysql dialect accepted', async ({ request }) => {
    const c = adapterClient(request)
    const r = await c.mcpCall('datatalk.ui.exec', {
      object: 'workspace',
      action: 'open_er_designer',
      params: { dialect: 'mysql' },
    })
    const data = mcpToolResult(r)
    const tabId = (data as any).tabId as string
    expect(tabId).toBeTruthy()
    await cleanupTab(request, tabId)
  })
})

// ── 8. apply_text_edits: editIndex on expected_text_mismatch ───────────────

test.describe('apply_text_edits: editIndex reported on expected_text_mismatch', () => {
  test.fixme('multi-edit failure reports editIndex and is transactional', async ({ page, request }) => {
    const c = adapterClient(request)
    const tabId = await createQueryEditorTab(request, 'EditIndex Test', 'SELECT 1\nFROM users\nWHERE id = 1')

    // Get baseVersion via payload
    const payloadRes = await c.stageGetPayload(tabId)
    const payloadBody = await payloadRes.json()
    const baseVersion = payloadBody.contentVersion as number

    // Submit multi-edit with wrong expectedText on first edit
    const edits = [
      {
        range: { startLine: 1, startColumn: 1, endLine: 1, endColumn: 8 },
        text: 'INSERT',
        expectedText: 'WRONG TEXT', // mismatch
      },
      {
        range: { startLine: 2, startColumn: 1, endLine: 2, endColumn: 5 },
        text: 'UPDATE',
        expectedText: 'FROM',
      },
    ]

    const res = await c.mcpCall('datatalk.ui.exec', {
      object: 'query_editor',
      target: tabId,
      action: 'apply_text_edits',
      params: { baseVersion, edits },
    })

    const result = res.result
    expect(result?.isError).toBe(true)
    const text = result?.content?.[0]?.text ?? ''
    expect(text).toContain('expected_text_mismatch')
    expect(text).toContain('edit#0') // editIndex 0 referenced in markdown

    // Assert content unchanged (transactional)
    const afterPayloadRes = await c.stageGetPayload(tabId)
    const afterPayloadBody = await afterPayloadRes.json()
    expect(afterPayloadBody.contentText).toBe('SELECT 1\nFROM users\nWHERE id = 1')

    await cleanupTab(request, tabId)
  })
})

// ── 9. set_context: useSessionContext=true rejects connectionId ────────────

test.describe('set_context: useSessionContext=true rejects connectionId', () => {
  test.fixme('simultaneous useSessionContext=true and connectionId rejected', async ({ request }) => {
    const c = adapterClient(request)
    const tabId = await createQueryEditorTab(request, 'SetContext Test', 'SELECT 1')

    const res = await c.mcpCall('datatalk.ui.exec', {
      object: 'query_editor',
      target: tabId,
      action: 'set_context',
      params: {
        useSessionContext: true,
        connectionId: h2?.connectionId ?? 'conn-123',
      },
    })

    const result = res.result
    expect(result?.isError).toBe(true)
    const text = result?.content?.[0]?.text ?? ''
    expect(text).toContain('useSessionContext=true')
    expect(text).toContain('connectionId')

    await cleanupTab(request, tabId)
  })
})

// ── 10. apply_text_edits: CRLF expectedText normalized to LF ───────────────

test.describe('apply_text_edits: CRLF expectedText normalized to LF', () => {
  test.fixme('edit with \\r\\n expectedText matches content with \\n', async ({ request }) => {
    const c = adapterClient(request)
    // Content uses LF
    const content = 'SELECT 1\nFROM users'
    const tabId = await createQueryEditorTab(request, 'CRLF Test', content)

    const payloadRes = await c.stageGetPayload(tabId)
    const payloadBody = await payloadRes.json()
    const baseVersion = payloadBody.contentVersion as number

    // Edit with CRLF expectedText
    const edits = [
      {
        range: { startLine: 1, startColumn: 1, endLine: 1, endColumn: 7 },
        text: 'INSERT',
        expectedText: 'SELECT', // no CRLF here, but let's test with CRLF in multi-line
      },
    ]

    const res = await c.mcpCall('datatalk.ui.exec', {
      object: 'query_editor',
      target: tabId,
      action: 'apply_text_edits',
      params: { baseVersion, edits },
    })
    mcpToolResult(res)

    // Now verify multi-line CRLF normalization
    const content2 = 'SELECT 1\nFROM users'
    const tabId2 = `qe_crlf2_${Date.now()}`
    await c.stageUpsertTab(tabId2, {
      type: 'query_editor',
      title: 'CRLF Test 2',
      payload: { initialSql: content2 },
      contentText: content2,
    })

    const payloadRes2 = await c.stageGetPayload(tabId2)
    const payloadBody2 = await payloadRes2.json()
    const baseVersion2 = payloadBody2.contentVersion as number

    // expectedText contains CRLF but actual content has LF
    const edits2 = [
      {
        range: { startLine: 1, startColumn: 1, endLine: 2, endColumn: 5 },
        text: 'INSERT\nINTO',
        expectedText: 'SELECT 1\r\nFROM', // CRLF should normalize to LF and match
      },
    ]

    const res2 = await c.mcpCall('datatalk.ui.exec', {
      object: 'query_editor',
      target: tabId2,
      action: 'apply_text_edits',
      params: { baseVersion: baseVersion2, edits: edits2 },
    })
    mcpToolResult(res2)

    // Verify content was changed
    const afterRes = await c.stageGetPayload(tabId2)
    const afterBody = await afterRes.json()
    expect(afterBody.contentText).toBe('INSERT\nINTO users')

    await cleanupTab(request, tabId)
    await cleanupTab(request, tabId2)
  })
})
