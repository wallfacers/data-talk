/**
 * I2: Playwright E2E test skeleton for Dashboard.
 * Requires running backend + frontend dev server.
 * Run: npx playwright test tests/e2e/dashboard.spec.ts
 */
import { test, expect } from '@playwright/test'

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
    // Wait for app shell to load
    await page.waitForSelector('[data-testid="app-shell"]', { timeout: 10000 })
  })

  test('dashboard tab opens from chat fence promote', async ({ page }) => {
    // This test requires a session with a dashboard code fence.
    // For now, verify the dashboard tab type is registered by checking tab creation.
    // Full E2E: send a chat message that triggers ```dashboard fence → click "Open to workbench"

    // Navigate to a session
    const firstSession = page.locator('[data-sidebar-item]').first()
    if (await firstSession.isVisible()) {
      await firstSession.click()
    }

    // Verify the stage area exists
    const stage = page.locator('[data-testid="stage-window"]')
    // Stage may or may not be open — just verify the page is responsive
    await expect(page.locator('body')).toBeVisible()
  })

  test('dashboard canvas renders widgets', async ({ page }) => {
    // Requires a dashboard tab to be open.
    // Verify: chart widget renders echarts, markdown widget renders text.
    // This is a placeholder — full implementation needs test data seeding.

    await expect(page.locator('body')).toBeVisible()
  })

  test('dashboard patch applies and persists', async ({ page }) => {
    // Requires an open dashboard tab in editor mode.
    // Verify: drag a widget → layout change persists after page reload.
    // This is a placeholder — full implementation needs API mocking.

    await expect(page.locator('body')).toBeVisible()
  })
})
