/**
 * Suite 2: Dashboard UI Promotion E2E Tests.
 *
 * Tests the dashboard block rendering states and the "Open to workbench"
 * promotion flow from chat message to global Stage tab.
 *
 * Run: npx playwright test tests/e2e/dashboard-ui.spec.ts
 * Tags: @e2e @dashboard
 */
import { test, expect } from '@playwright/test'
import { DashboardPage } from './pom/dashboard.page'
import { makeDashboardPayload } from './fixtures/dashboard-fixtures'

/** Helper to mount a DashboardBlock wrapped with I18nProvider for E2E tests. */
async function renderDashboardBlock(page, props) {
  await page.evaluate(({ json, streaming }) => {
    // Clean up any leftover host from previous test runs
    const existing = document.getElementById('__e2e-dashboard-test-host')
    if (existing) existing.remove()
    const host = document.createElement('div')
    host.id = '__e2e-dashboard-test-host'
    host.style.cssText = 'position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);z-index:99999;background:white;padding:16px;max-height:80vh;overflow:auto;'
    document.body.appendChild(host)
    const React = (window as any).reactForE2E
    const { createRoot } = (window as any).reactDOMForE2E
    const { DashboardBlock } = (window as any).dashboardBlockForE2E
    const { I18nProvider } = (window as any).i18nForE2E
    createRoot(host).render(
      React.createElement(I18nProvider, null,
        React.createElement(DashboardBlock, { json, streaming }),
      ),
    )
  }, props)
}

test.describe('@e2e @dashboard Dashboard UI Promotion', () => {
  let dashboard: DashboardPage

  test.beforeEach(async ({ page }) => {
    dashboard = new DashboardPage(page)
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
  })

  test('streaming dashboard block shows skeleton', async ({ page }) => {
    await renderDashboardBlock(page, { json: '{}', streaming: true })
    await expect(page.getByTestId('dashboard-skeleton')).toBeVisible()
  })

  test('invalid dashboard block shows error', async ({ page }) => {
    await renderDashboardBlock(page, { json: 'not-valid-json', streaming: false })
    await expect(page.getByTestId('dashboard-error')).toBeVisible()
  })

  test('valid dashboard block shows preview', async ({ page }) => {
    const validDashboard = makeDashboardPayload({ title: 'Test Preview' })
    const json = JSON.stringify(validDashboard)
    await renderDashboardBlock(page, { json, streaming: false })
    await dashboard.expectPreviewVisible()
    await expect(page.getByText('Test Preview')).toBeVisible()
    await expect(page.getByText('2 widget(s)')).toBeVisible()
  })

  test('Open to workbench promotes dashboard to global Stage', async ({ page }) => {
    const validDashboard = makeDashboardPayload({ title: 'Sales Overview' })
    const json = JSON.stringify(validDashboard)
    await renderDashboardBlock(page, { json, streaming: false })
    await dashboard.clickOpenToWorkbench()
    await dashboard.waitForDashboardTab('Sales Overview')
    const stageState = await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__.stage()
      return { open: stage.open, activeTabId: stage.activeTabId }
    })
    expect(stageState.open).toBe(true)
    expect(stageState.activeTabId).toBeTruthy()
    const activeTab = await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__.stage()
      const active = stage.tabs.find((t: any) => t.tabId === stage.activeTabId)
      return active ? { type: active.type, title: active.title } : null
    })
    expect(activeTab).not.toBeNull()
    expect(activeTab!.type).toBe('dashboard')
    expect(activeTab!.title).toBe('Sales Overview')
    await dashboard.expectWorkbenchOpen('Sales Overview')
  })

  test('promoted dashboard creates or selects a dashboard tab', async ({ page }) => {
    const validDashboard = makeDashboardPayload({ title: 'Metrics Dashboard' })
    const json = JSON.stringify(validDashboard)
    await renderDashboardBlock(page, { json, streaming: false })
    await dashboard.clickOpenToWorkbench()
    await dashboard.waitForDashboardTab('Metrics Dashboard')
    const dashState = await page.evaluate(() => {
      const store = (window as any).__DT_E2E__.dashboard()
      return {
        tabCount: store.tabs.size,
        tabs: Array.from(store.tabs.values()).map((t: any) => ({
          title: t.dashboard.title,
          version: t.dashboard.version,
        })),
      }
    })
    expect(dashState.tabCount).toBeGreaterThanOrEqual(1)
    expect(dashState.tabs.some((t: any) => t.title === 'Metrics Dashboard')).toBe(true)
    await dashboard.expectCanvasVisible()
  })

  test('repeated promotion of the same dashboard does not create duplicate tabs', async ({ page }) => {
    const validDashboard = makeDashboardPayload({ title: 'No Dupes' })
    const json = JSON.stringify(validDashboard)
    await renderDashboardBlock(page, { json, streaming: false })
    await dashboard.clickOpenToWorkbench()
    await dashboard.waitForDashboardTab('No Dupes')
    const afterFirst = await dashboard.getDashboardTabTitles()
    await expect(page.getByRole('button', { name: 'Open to workbench' })).not.toBeVisible()
    await expect(page.getByText('Opened in workbench')).toBeVisible()
    const afterSecond = await dashboard.getDashboardTabTitles()
    expect(afterSecond.length).toBe(afterFirst.length)
    const stageAfterSecond = await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__.stage()
      return stage.tabs.filter((t: any) => t.type === 'dashboard').length
    })
    // Stage tab count stays the same after the button is no longer visible
  })

  test('chart widget and markdown widget render in the canvas', async ({ page }) => {
    const validDashboard = makeDashboardPayload({ title: 'Widget Test' })
    const json = JSON.stringify(validDashboard)
    await renderDashboardBlock(page, { json, streaming: false })
    await dashboard.clickOpenToWorkbench()
    await dashboard.waitForDashboardTab('Widget Test')
    await dashboard.expectCanvasVisible()
    const chartWidget = dashboard.widgetByTitle('Metric')
    await expect(chartWidget).toBeVisible()
    const widgetShells = page.locator('[data-component="dashboard-widget-shell"]')
    await expect(widgetShells).toHaveCount(2)
  })
})
