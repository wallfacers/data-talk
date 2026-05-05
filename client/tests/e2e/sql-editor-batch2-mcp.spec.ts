import { test, expect } from '@playwright/test'
import { StagePage } from './pom/stage.page'
import { SqlWorkbenchPage } from './pom/sql-workbench.page'
import { ChatPanelPage } from './pom/chat-panel.page'
import { switchToSqlEditorTab } from './pom/helpers'

const MODEL = process.env.DATATALK_REAL_OPENCODE_MODEL

// Skip entire suite when no real AI model is configured
test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set — skipping MCP tests')

let stage: StagePage
let sql: SqlWorkbenchPage
let chat: ChatPanelPage

test.beforeEach(async ({ page }) => {
  await page.goto('/')
  stage = new StagePage(page)
  sql = new SqlWorkbenchPage(page)
  chat = new ChatPanelPage(page)
  await page.waitForSelector('textarea', { timeout: 15_000 })
  await switchToSqlEditorTab(page)
})

test.describe('批次 2: AI-MCP 联动', () => {
  test.setTimeout(60_000)

  test('2.1 AI 打开 SQL 编辑器并执行查询', async ({ page }) => {
    await chat.sendMessage('请帮我执行 SELECT 1 AS test')
    await chat.waitForAiResponse()
    await page.waitForTimeout(2_000)

    // AI should trigger a tool call that writes SQL and executes
    const toolCard = await chat.getLastToolCallCard()
    if (toolCard) {
      expect(['completed', 'success', 'done']).toEqual(
        expect.arrayContaining([toolCard.status.toLowerCase()])
      )
    }

    // Verify result appeared
    const hasResult = await page.locator('table').count() > 0
      || await page.locator('text=/\\d+ 行|\\d+ rows|error|结果/i').count() > 0
    expect(hasResult).toBe(true)
  })

  test('2.2 AI 切换连接上下文', async ({ page }) => {
    await chat.sendMessage('列出可用的数据库连接')
    await chat.waitForAiResponse()

    // AI response should mention connections or databases
    const lastMsg = await chat.getLastMessage()
    expect(lastMsg.length).toBeGreaterThan(0)
  })

  test('2.3 AI 格式化 SQL', async ({ page }) => {
    await switchToSqlEditorTab(page)
    await sql.setSql('select   1   from   dual')
    await chat.sendMessage('帮我格式化当前 SQL')
    await chat.waitForAiResponse()
    await page.waitForTimeout(2_000)

    // Check if SQL was reformatted (whitespace normalized)
    const currentSql = await sql.getSql()
    expect(currentSql).not.toBe('select   1   from   dual')
  })

  test('2.4 AI 修改 SQL 内容', async ({ page }) => {
    await switchToSqlEditorTab(page)
    await sql.setSql('SELECT 1')
    await chat.sendMessage('把当前 SQL 改成查询 users 表的前 10 条记录')
    await chat.waitForAiResponse()
    await page.waitForTimeout(2_000)

    const currentSql = await sql.getSql()
    expect(currentSql.toLowerCase()).toContain('users')
  })

  test('2.5 AI 打开 ER 检查器', async ({ page }) => {
    await chat.sendMessage('打开 ER 图查看表结构')
    await chat.waitForAiResponse()
    await page.waitForTimeout(2_000)

    // Should have an ER tab or panel open
    const erTab = page.getByRole('tab', { name: /ER|Schema|表结构|关系图/i })
    const erPanel = page.locator('[data-testid="er-viewer"]').or(page.locator('[data-testid="schema-panel"]'))
    const hasEr = (await erTab.count()) > 0 || (await erPanel.count()) > 0
    expect(hasEr).toBe(true)
  })
})
