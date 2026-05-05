import { Page, expect } from '@playwright/test'

/**
 * Ensure an SQL editor tab is active. Strategy:
 * 1. Click existing tab by name
 * 2. Fallback: click dock button to create a new SQL tab
 * 3. Wait for Monaco editor to appear
 */
export async function switchToSqlEditorTab(page: Page): Promise<void> {
  const sqlTab = page.getByRole('tab', { name: /SQL 编辑器|SQL Editor/i })
  if (await sqlTab.count() > 0) {
    await sqlTab.first().click()
    await page.waitForSelector('.monaco-editor', { timeout: 5_000 }).catch(() => {})
    return
  }

  const testTab = page.getByRole('tab', { name: /测试查询编辑器|Test Query Editor/i })
  if (await testTab.count() > 0) {
    await testTab.first().click()
    await page.waitForSelector('.monaco-editor', { timeout: 5_000 }).catch(() => {})
    return
  }

  // No SQL tab — click dock button to create one
  const dockBtn = page.locator('[data-testid="dock-sql-button"]')
    .or(page.locator('button').filter({ hasText: /SQL|sql/i }).first())
  if (await dockBtn.count() > 0) {
    await dockBtn.first().click()
    await page.waitForSelector('.monaco-editor', { timeout: 10_000 }).catch(() => {})
  }
}
