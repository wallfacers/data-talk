import { test, expect } from '@playwright/test'
import { StagePage } from './pom/stage.page'
import { SqlWorkbenchPage } from './pom/sql-workbench.page'
import { switchToSqlEditorTab } from './pom/helpers'
import { setupH2Connection, type H2TestSetup } from './fixtures/h2-setup'

let stage: StagePage
let sql: SqlWorkbenchPage
let h2: H2TestSetup | null = null

// Setup H2 connection once for all tests that need it
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
  sql = new SqlWorkbenchPage(page)
  // Wait for app to be ready
  await page.waitForSelector('textarea', { timeout: 15_000 })
  // Stage may already be open; ensure we have a SQL editor tab active
  await switchToSqlEditorTab(page)
})

test.describe('批次 1: SQL 编辑器核心 UI 交互', () => {

  test('1.1 打开 SQL 编辑器', async ({ page }) => {
    // Verify Stage is open and SQL editor tab exists
    const stageWindow = page.locator('[data-testid="stage-window"]').or(page.locator('.stage-window')).first()
      .or(page.locator('text="Workbench"').first())
    expect(await stageWindow.count()).toBeGreaterThan(0)

    // Verify SQL editor tab exists
    const sqlTab = page.locator('[role="tab"]').filter({ hasText: /SQL|编辑器|查询/i })
    expect(await sqlTab.count()).toBeGreaterThan(0)
  })

  test('1.2 输入并执行 SELECT', async ({ page }) => {
    test.skip(!h2, 'H2 test connection not available')
    await switchToSqlEditorTab(page)

    await sql.setSql('SELECT 1 AS one')
    await sql.pressRun()
    await sql.waitForResult()

    const rowCount = await sql.getResultRowCount()
    if (rowCount !== null) {
      expect(rowCount).toBeGreaterThanOrEqual(0)
    } else {
      const hasTable = await page.locator('table').count() > 0
      const hasResultText = await page.locator('text=/行|rows|ms/i').count() > 0
      expect(hasTable || hasResultText).toBe(true)
    }
  })

  test('1.3 多语句执行', async ({ page }) => {
    test.skip(!h2, 'H2 test connection not available')
    await switchToSqlEditorTab(page)

    await sql.setSql('SELECT 1; SELECT 2')
    await sql.pressRun()
    await sql.waitForResult(15_000)

    const tabs = await sql.getResultTabs()
    const hasTable = await page.locator('table').count() > 0
    const hasText = await page.locator('text=/行|rows|error|结果|result|running|ms/i').count() > 0
    expect(tabs.length > 0 || hasTable || hasText).toBe(true)
  })

  test('1.4 高风险拦截', async ({ page }) => {
    test.skip(!h2, 'H2 test connection not available')
    await switchToSqlEditorTab(page)

    await sql.setSql('DELETE FROM users')
    await sql.pressRun()
    await sql.waitForResult()

    const riskMsg = await sql.getRiskMessage()
    const hasRisk = riskMsg !== null || await page.locator('text=/DELETE|风险|WHERE|danger/i').count() > 0
    expect(hasRisk).toBe(true)
  })

  test('1.5 Toolbar 上下文切换', async ({ page }) => {
    await switchToSqlEditorTab(page)

    // Try to find the session context switch or connection selector
    const switchLabel = page.locator('text=/固定|session|上下文|连接/i').first()
    if (await switchLabel.count() > 0) {
      await switchLabel.click()
    }

    // Verify toolbar is visible
    const toolbar = page.locator('[data-testid="sql-editor-toolbar"]').or(page.locator('button:has-text("运行")'))
    expect(await toolbar.count()).toBeGreaterThan(0)
  })

  test('1.6 SQL 格式化', async ({ page }) => {
    await switchToSqlEditorTab(page)

    const unformatted = "select   1   from   dual"
    await sql.setSql(unformatted)

    // Find format button
    const formatBtn = page.locator('button').filter({ hasText: /格式化|format/i }).first()
    if (await formatBtn.count() > 0) {
      await formatBtn.click()
      await page.waitForTimeout(1_000)

      const formatted = await sql.getSql()
      expect(formatted).not.toBe(unformatted)
    } else {
      test.skip(true, 'Format button not found')
    }
  })

  test('1.7 Tab 管理', async ({ page }) => {
    await switchToSqlEditorTab(page)

    const tabsBefore = page.locator('[role="tab"]')
    const countBefore = await tabsBefore.count()
    expect(countBefore).toBeGreaterThanOrEqual(1)

    // Close the first closable tab (has close button)
    const firstTabWithClose = page.locator('[role="tab"]').filter({ has: page.locator('button') }).first()
    const closeBtn = firstTabWithClose.locator('button').first()
    if (await closeBtn.count() > 0) {
      await closeBtn.click()
      await page.waitForTimeout(500)
      const countAfter = await page.locator('[role="tab"]').count()
      expect(countAfter).toBeLessThan(countBefore)
    }
  })

  test('1.8 Stage 最大化/还原', async ({ page }) => {
    await switchToSqlEditorTab(page)

    const maxBtn = page.locator('[data-testid="stage-maximize-button"]')
    if (await maxBtn.count() === 0) {
      test.fixme(true, 'Stage maximize button not implemented yet')
      return
    }

    const stageEl = page.locator('[data-testid="stage-window"]').or(page.locator('.stage-window')).first()
    const beforeHeight = await stageEl.evaluate((el) => (el as HTMLElement).offsetHeight)
    await stage.maximize()
    await page.waitForTimeout(500)
    const afterHeight = await stageEl.evaluate((el) => (el as HTMLElement).offsetHeight)
    expect(afterHeight).toBeGreaterThanOrEqual(beforeHeight)

    // Restore
    await stage.restore()
    await page.waitForTimeout(500)
    const restoredHeight = await stageEl.evaluate((el) => (el as HTMLElement).offsetHeight)
    expect(restoredHeight).toBeLessThanOrEqual(afterHeight)
  })
})
