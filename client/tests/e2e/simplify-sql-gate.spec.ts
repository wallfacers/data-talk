import { test, expect } from '@playwright/test'
import { adapterClient } from './fixtures/adapter-client'
import { ChatPanelPage } from './pom/chat-panel.page'
import { mountToolRecorder } from './fixtures/mcp-tool-recorder'

const CONN_ID = 'cb2d0259-edd5-4091-873b-6cfee09eec1a'
const DB = 'e2e_sql_gate'

// ═══════════════════════════════════════════════════════════════════════
// Group 1: REST API Contract Tests (no AI needed)
// Verifies the SQL execution behavior via /api/sql/execute directly.
// ═══════════════════════════════════════════════════════════════════════
test.describe('SQL Gate Simplification — REST API Contract', () => {
  test('SELECT executes directly without confirmation', async ({ request }) => {
    const client = adapterClient(request)
    const resp = await client.executeSql({
      connectionId: CONN_ID,
      database: DB,
      sql: 'SELECT COUNT(*) AS cnt FROM e2e_verify',
      source: 'user',
    })
    expect(resp.ok()).toBe(true)
    const body = await resp.json()
    expect(body.status).toBe('executed')
    expect(body.confirmation).toBeNull()
    expect(body.results[0].rows.length).toBeGreaterThanOrEqual(1)
  })

  test('CREATE TABLE executes directly', async ({ request }) => {
    const client = adapterClient(request)
    const resp = await client.executeSql({
      connectionId: CONN_ID,
      database: DB,
      sql: 'CREATE TABLE IF NOT EXISTS e2e_temp (id INT PRIMARY KEY)',
      source: 'user',
    })
    expect(resp.ok()).toBe(true)
    const body = await resp.json()
    expect(body.status).toBe('executed')
    expect(body.confirmation).toBeNull()
  })

  test('DROP TABLE executes directly', async ({ request }) => {
    const client = adapterClient(request)
    await client.executeSql({
      connectionId: CONN_ID,
      database: DB,
      sql: 'CREATE TABLE IF NOT EXISTS e2e_drop_target (id INT)',
      source: 'user',
    })

    const resp = await client.executeSql({
      connectionId: CONN_ID,
      database: DB,
      sql: 'DROP TABLE e2e_drop_target',
      source: 'user',
    })
    expect(resp.ok()).toBe(true)
    const body = await resp.json()
    expect(body.status).toBe('executed')
    expect(body.confirmation).toBeNull()
  })

  test('INSERT executes with riskAck=L2', async ({ request }) => {
    const client = adapterClient(request)
    const resp = await client.executeSql({
      connectionId: CONN_ID,
      database: DB,
      sql: "INSERT INTO e2e_verify (name, age) VALUES ('e2e_test', 99)",
      source: 'user',
      confirmed: true,
      riskAck: 'L2',
    })
    expect(resp.ok()).toBe(true)
    const body = await resp.json()
    expect(body.status).toBe('executed')
    expect(body.results[0].affectedRows).toBe(1)
  })

  test('UPDATE executes with riskAck=L2', async ({ request }) => {
    const client = adapterClient(request)
    const resp = await client.executeSql({
      connectionId: CONN_ID,
      database: DB,
      sql: "UPDATE e2e_verify SET age = 100 WHERE name = 'e2e_test'",
      source: 'user',
      confirmed: true,
      riskAck: 'L2',
    })
    expect(resp.ok()).toBe(true)
    const body = await resp.json()
    expect(body.status).toBe('executed')
    expect(body.confirmation).toBeNull()
  })

  test('DELETE requires confirmation (editor path retains risk gate)', async ({ request }) => {
    const client = adapterClient(request)
    const resp = await client.executeSql({
      connectionId: CONN_ID,
      database: DB,
      sql: "DELETE FROM e2e_verify WHERE name = 'e2e_test'",
      source: 'user',
    })
    expect(resp.ok()).toBe(true)
    const body = await resp.json()
    expect(body.status).toBe('requires_confirmation')
    expect(body.confirmation).not.toBeNull()
    expect(body.confirmation.level).toBeDefined()
    expect(body.confirmation.sqlPreview).toContain('DELETE')
  })

  test('DELETE confirmed executes with proper riskAck', async ({ request }) => {
    const client = adapterClient(request)
    const preflight = await client.executeSql({
      connectionId: CONN_ID,
      database: DB,
      sql: 'DELETE FROM e2e_verify WHERE age = 100',
      source: 'user',
    })
    const preBody = await preflight.json()
    expect(preBody.status).toBe('requires_confirmation')
    const riskLevel = preBody.confirmation.level

    const resp = await client.executeSql({
      connectionId: CONN_ID,
      database: DB,
      sql: 'DELETE FROM e2e_verify WHERE age = 100',
      source: 'user',
      confirmed: true,
      riskAck: riskLevel,
    })
    expect(resp.ok()).toBe(true)
    const body = await resp.json()
    expect(body.status).toBe('executed')
  })

  test('TRUNCATE executes directly (DDL)', async ({ request }) => {
    const client = adapterClient(request)
    await client.executeSql({
      connectionId: CONN_ID,
      database: DB,
      sql: 'CREATE TABLE IF NOT EXISTS e2e_trunc (id INT)',
      source: 'user',
    })

    const resp = await client.executeSql({
      connectionId: CONN_ID,
      database: DB,
      sql: 'TRUNCATE TABLE e2e_trunc',
      source: 'user',
    })
    expect(resp.ok()).toBe(true)
    const body = await resp.json()
    expect(body.status).toBe('executed')
  })
})

// ═══════════════════════════════════════════════════════════════════════
// Group 2: AI Chat Flow Tests (requires DATATALK_REAL_OPENCODE_MODEL)
// Verifies the full chat → AI → MCP → result flow through the browser.
// ═══════════════════════════════════════════════════════════════════════
test.describe('SQL Gate Simplification — AI Chat Flow', () => {
  test.setTimeout(120_000)

  let chat: ChatPanelPage

  test.beforeEach(async ({ page }) => {
    await page.goto('/')
    chat = new ChatPanelPage(page)
    await page.waitForSelector('textarea', { timeout: 15_000 })
  })

  test('AI executes SELECT without any confirmation dialog', async ({ page }) => {
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage(`在 ${DB} 数据库的 e2e_verify 表中查询所有数据`)
    await chat.waitForAiResponse()

    const calls = await recorder.callsFor('datatalk_execute_sql')
    expect(calls.length).toBeGreaterThanOrEqual(1)
    expect(calls[calls.length - 1].status).toBe('completed')

    const dialog = page.locator('[role="dialog"]')
    expect(await dialog.count()).toBe(0)
  })

  test('AI executes CREATE TABLE directly without editor popup', async ({ page }) => {
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage(`请在 ${DB} 数据库中创建一个临时表 e2e_ai_test，包含 id INT 主键和 name VARCHAR(100)`)
    await chat.waitForAiResponse()

    const calls = await recorder.callsFor('datatalk_execute_sql')
    expect(calls.length).toBeGreaterThanOrEqual(1)

    const client = adapterClient(page.context().request)
    const verifyResp = await client.executeSql({
      connectionId: CONN_ID,
      database: DB,
      sql: `SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DB}' AND TABLE_NAME='e2e_ai_test'`,
      source: 'user',
    })
    const verifyBody = await verifyResp.json()
    expect(verifyBody.results[0].rows.length).toBeGreaterThanOrEqual(1)
  })

  test('AI executes DROP+CREATE without editor popup', async ({ page }) => {
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage(`请删除 ${DB}.e2e_ai_test 表并重新创建，结构不变`)
    await chat.waitForAiResponse()

    const calls = await recorder.callsFor('datatalk_execute_sql')
    expect(calls.length).toBeGreaterThanOrEqual(1)

    const client = adapterClient(page.context().request)
    const verifyResp = await client.executeSql({
      connectionId: CONN_ID,
      database: DB,
      sql: `SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DB}' AND TABLE_NAME='e2e_ai_test'`,
      source: 'user',
    })
    const verifyBody = await verifyResp.json()
    expect(verifyBody.results[0].rows.length).toBeGreaterThanOrEqual(1)
  })

  test('AI executes INSERT directly without editor popup', async ({ page }) => {
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage(`往 ${DB}.e2e_ai_test 表插入一条数据: name='test_insert'`)
    await chat.waitForAiResponse()

    const calls = await recorder.callsFor('datatalk_execute_sql')
    expect(calls.length).toBeGreaterThanOrEqual(1)
    expect(calls[calls.length - 1].status).toBe('completed')
  })

  test('AI handles DELETE with conversational confirmation', async ({ page }) => {
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage(`删除 ${DB}.e2e_ai_test 中 name='test_insert' 的记录`)
    await chat.waitForAiResponse()

    const calls = await recorder.callsFor('datatalk_execute_sql')
    expect(calls.length).toBeGreaterThanOrEqual(1)

    // No editor AlertDialog should appear
    const alertDialog = page.locator('[data-testid="sql-risk-dialog"]')
      .or(page.locator('[role="alertdialog"]'))
    expect(await alertDialog.count()).toBe(0)
  })

  test('AI executes UPDATE directly without editor popup', async ({ page }) => {
    const recorder = await mountToolRecorder(page)
    await chat.sendMessage(`把 ${DB}.e2e_ai_test 中 name='test_insert' 改成 name='test_updated'`)
    await chat.waitForAiResponse()

    const calls = await recorder.callsFor('datatalk_execute_sql')
    expect(calls.length).toBeGreaterThanOrEqual(1)
    expect(calls[calls.length - 1].status).toBe('completed')
  })
})

// ═══════════════════════════════════════════════════════════════════════
// Cleanup
// ═══════════════════════════════════════════════════════════════════════
test.describe('Cleanup', () => {
  test('drop e2e test tables and database', async ({ request }) => {
    const client = adapterClient(request)
    const tables = ['e2e_verify', 'e2e_batch', 'e2e_trunc', 'e2e_ai_test', 'e2e_drop_target', 'e2e_temp']
    for (const table of tables) {
      await client.executeSql({
        connectionId: CONN_ID,
        database: DB,
        sql: `DROP TABLE IF EXISTS ${table}`,
        source: 'user',
      }).catch(() => { /* ignore */ })
    }
    await client.executeSql({
      connectionId: CONN_ID,
      sql: `DROP DATABASE IF EXISTS ${DB}`,
      source: 'user',
    }).catch(() => { /* ignore */ })
  })
})
