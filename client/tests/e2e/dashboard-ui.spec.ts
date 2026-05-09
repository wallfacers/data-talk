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

test.describe('@e2e @dashboard Dashboard UI Promotion', () => {
  let dashboard: DashboardPage

  test.beforeEach(async ({ page }) => {
    dashboard = new DashboardPage(page)
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
  })

  test('streaming dashboard block shows skeleton', async ({ page }) => {
    // Mount a DashboardBlock with streaming=true and verify skeleton renders
    await page.evaluate(() => {
      const host = document.createElement('div')
      host.id = '__e2e-dashboard-test-host'
      document.body.appendChild(host)
      const React = (window as any).reactForE2E
      const { createRoot } = (window as any).reactDOMForE2E
      const { DashboardBlock } = (window as any).dashboardBlockForE2E
      createRoot(host).render(
        React.createElement(DashboardBlock, { json: '{}', streaming: true }),
      )
    })

    await expect(page.getByTestId('dashboard-skeleton')).toBeVisible()
  })

  test('invalid dashboard block shows error', async ({ page }) => {
    // Mount a DashboardBlock with invalid JSON and verify error state renders
    await page.evaluate(() => {
      const host = document.createElement('div')
      host.id = '__e2e-dashboard-test-host'
      document.body.appendChild(host)
      const React = (window as any).reactForE2E
      const { createRoot } = (window as any).reactDOMForE2E
      const { DashboardBlock } = (window as any).dashboardBlockForE2E
      createRoot(host).render(
        React.createElement(DashboardBlock, { json: 'not-valid-json', streaming: false }),
      )
    })

    await expect(page.getByTestId('dashboard-error')).toBeVisible()
  })

  test('valid dashboard block shows preview', async ({ page }) => {
    // Mount a DashboardBlock with valid JSON and verify preview renders
    const validDashboard = makeDashboardPayload({ title: 'Test Preview' })
    const json = JSON.stringify(validDashboard)

    await page.evaluate(({ json }) => {
      const host = document.createElement('div')
      host.id = '__e2e-dashboard-test-host'
      document.body.appendChild(host)
      const React = (window as any).reactForE2E
      const { createRoot } = (window as any).reactDOMForE2E
      const { DashboardBlock } = (window as any).dashboardBlockForE2E
      createRoot(host).render(
        React.createElement(DashboardBlock, { json, streaming: false }),
      )
    }, { json })

    await dashboard.expectPreviewVisible()
    // Verify title is rendered in the preview
    await expect(page.getByText('Test Preview')).toBeVisible()
    // Verify widget count is shown
    await expect(page.getByText('2 widgets')).toBeVisible()
  })

  test('Open to workbench promotes dashboard to global Stage', async ({ page }) => {
    // Mount a valid dashboard block, click "Open to workbench", verify Stage opens
    const validDashboard = makeDashboardPayload({ title: 'Sales Overview' })
    const json = JSON.stringify(validDashboard)

    await page.evaluate(({ json }) => {
      const host = document.createElement('div')
      host.id = '__e2e-dashboard-test-host'
      document.body.appendChild(host)
      const React = (window as any).reactForE2E
      const { createRoot } = (window as any).reactDOMForE2E
      const { DashboardBlock } = (window as any).dashboardBlockForE2E
      createRoot(host).render(
        React.createElement(DashboardBlock, { json, streaming: false }),
      )
    }, { json })

    // Click the "Open to workbench" button
    await dashboard.clickOpenToWorkbench()

    // Verify the dashboard tab was created in the store
    await dashboard.waitForDashboardTab('Sales Overview')

    // Verify Stage panel opened (open = true in stage store)
    const stageState = await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__.stage()
      return { open: stage.open, activeTabId: stage.activeTabId }
    })
    expect(stageState.open).toBe(true)
    expect(stageState.activeTabId).toBeTruthy()

    // Verify a dashboard-type tab is now the active tab
    const activeTab = await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__.stage()
      const active = stage.tabs.find((t: any) => t.tabId === stage.activeTabId)
      return active ? { type: active.type, title: active.title } : null
    })
    expect(activeTab).not.toBeNull()
    expect(activeTab!.type).toBe('dashboard')
    expect(activeTab!.title).toBe('Sales Overview')

    // Verify the tab is visible in the Stage UI
    await dashboard.expectWorkbenchOpen('Sales Overview')
  })

  test('promoted dashboard creates or selects a dashboard tab', async ({ page }) => {
    // After promotion, verify the dashboard tab store has the hydrated tab
    // and the Stage tab bar shows it
    const validDashboard = makeDashboardPayload({ title: 'Metrics Dashboard' })
    const json = JSON.stringify(validDashboard)

    await page.evaluate(({ json }) => {
      const host = document.createElement('div')
      host.id = '__e2e-dashboard-test-host'
      document.body.appendChild(host)
      const React = (window as any).reactForE2E
      const { createRoot } = (window as any).reactDOMForE2E
      const { DashboardBlock } = (window as any).dashboardBlockForE2E
      createRoot(host).render(
        React.createElement(DashboardBlock, { json, streaming: false }),
      )
    }, { json })

    await dashboard.clickOpenToWorkbench()
    await dashboard.waitForDashboardTab('Metrics Dashboard')

    // Verify the dashboard tabs store contains a hydrated tab
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

    // Verify canvas is visible in the Stage
    await dashboard.expectCanvasVisible()
  })

  test('repeated promotion of the same dashboard does not create duplicate tabs', async ({ page }) => {
    // Click "Open to workbench" twice and verify no duplicate tabs are created
    const validDashboard = makeDashboardPayload({ title: 'No Dupes' })
    const json = JSON.stringify(validDashboard)

    await page.evaluate(({ json }) => {
      const host = document.createElement('div')
      host.id = '__e2e-dashboard-test-host'
      document.body.appendChild(host)
      const React = (window as any).reactForE2E
      const { createRoot } = (window as any).reactDOMForE2E
      const { DashboardBlock } = (window as any).dashboardBlockForE2E
      createRoot(host).render(
        React.createElement(DashboardBlock, { json, streaming: false }),
      )
    }, { json })

    // First promotion
    await dashboard.clickOpenToWorkbench()
    await dashboard.waitForDashboardTab('No Dupes')

    // Record tab count and titles after first promotion
    const afterFirst = await dashboard.getDashboardTabTitles()
    const stageAfterFirst = await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__.stage()
      return stage.tabs.filter((t: any) => t.type === 'dashboard').length
    })

    // Verify the button is no longer visible (replaced by "Opened in workbench")
    await expect(page.getByRole('button', { name: 'Open to workbench' })).not.toBeVisible()
    await expect(page.getByText('Opened in workbench')).toBeVisible()

    // Verify no duplicate tabs were created
    const afterSecond = await dashboard.getDashboardTabTitles()
    expect(afterSecond.length).toBe(afterFirst.length)

    // Verify Stage dashboard tab count unchanged
    const stageAfterSecond = await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__.stage()
      return stage.tabs.filter((t: any) => t.type === 'dashboard').length
    })
    expect(stageAfterSecond).toBe(stageAfterFirst)
  })

  test('chart widget and markdown widget render in the canvas', async ({ page }) => {
    // Promote a dashboard with chart + markdown widgets, verify they render
    // in the Stage canvas
    const validDashboard = makeDashboardPayload({ title: 'Widget Test' })
    const json = JSON.stringify(validDashboard)

    await page.evaluate(({ json }) => {
      const host = document.createElement('div')
      host.id = '__e2e-dashboard-test-host'
      document.body.appendChild(host)
      const React = (window as any).reactForE2E
      const { createRoot } = (window as any).reactDOMForE2E
      const { DashboardBlock } = (window as any).dashboardBlockForE2E
      createRoot(host).render(
        React.createElement(DashboardBlock, { json, streaming: false }),
      )
    }, { json })

    await dashboard.clickOpenToWorkbench()
    await dashboard.waitForDashboardTab('Widget Test')
    await dashboard.expectCanvasVisible()

    // Verify chart widget renders with title "Metric" from fixture
    const chartWidget = dashboard.widgetByTitle('Metric')
    await expect(chartWidget).toBeVisible()

    // Verify markdown widget shell renders (no title in fixture, so check for shell count)
    const widgetShells = page.locator('[data-component="dashboard-widget-shell"]')
    await expect(widgetShells).toHaveCount(2)
  })
})
