/**
 * Suite: E-commerce Operations Dashboard E2E Tests.
 *
 * Realistic business scenario: AI generates a comprehensive e-commerce
 * operations dashboard (GMV, orders, categories, regional analysis), renders
 * it in the chat as a fenced dashboard block, promotes it to the Stage
 * workbench, and verifies all widgets and layout integrity.
 *
 * Run: npx playwright test tests/e2e/ecommerce-dashboard.spec.ts
 * Tags: @e2e @dashboard @ecommerce
 */
import { test, expect, Page } from '@playwright/test'
import { DashboardPage } from './pom/dashboard.page'
import { makeEcommerceDashboardPayload } from './fixtures/dashboard-fixtures'

/** Helper to mount a DashboardBlock wrapped with I18nProvider for E2E tests. */
async function renderDashboardBlock(page: Page, props: { json: string; streaming: boolean }) {
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

test.describe('@e2e @dashboard @ecommerce E-commerce Operations Dashboard', () => {
  let dashboard: DashboardPage

  test.beforeEach(async ({ page }) => {
    dashboard = new DashboardPage(page)
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
  })

  test('ecommerce dashboard block shows preview with all KPI cards', async ({ page }) => {
    const ecommerceDash = makeEcommerceDashboardPayload()
    const json = JSON.stringify(ecommerceDash)
    await renderDashboardBlock(page, { json, streaming: false })

    await dashboard.expectPreviewVisible()
    await expect(page.getByText('电商运营大屏')).toBeVisible()

    // Verify KPI cards are rendered in the preview widget list
    await expect(page.getByText('今日 GMV')).toBeVisible()
    await expect(page.getByText('今日订单')).toBeVisible()
    await expect(page.getByText('活跃买家')).toBeVisible()
    await expect(page.getByText('转化率')).toBeVisible()

    // Verify widget count: 1 header + 4 KPIs + 6 charts + 1 footer = 12
    await expect(page.getByText('12 widget(s)')).toBeVisible()
  })

  test('ecommerce dashboard promotion opens Stage with correct title', async ({ page }) => {
    const ecommerceDash = makeEcommerceDashboardPayload()
    const json = JSON.stringify(ecommerceDash)
    await renderDashboardBlock(page, { json, streaming: false })

    await dashboard.clickOpenToWorkbench()
    await dashboard.waitForDashboardTab('电商运营大屏')

    const stageState = await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__.stage()
      return { open: stage.open, activeTabId: stage.activeTabId }
    })
    expect(stageState.open).toBe(true)
    expect(stageState.activeTabId).toBeTruthy()

    const activeTab = await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__.stage()
      return stage.tabs.find((t: any) => t.tabId === stage.activeTabId)
    })
    expect(activeTab).not.toBeUndefined()
    expect(activeTab.type).toBe('dashboard')
    expect(activeTab.title).toBe('电商运营大屏')
  })

  test('ecommerce dashboard canvas renders all chart widgets with titles', async ({ page }) => {
    const ecommerceDash = makeEcommerceDashboardPayload()
    const json = JSON.stringify(ecommerceDash)
    await renderDashboardBlock(page, { json, streaming: false })

    await dashboard.clickOpenToWorkbench()
    await dashboard.waitForDashboardTab('电商运营大屏')
    await dashboard.expectCanvasVisible()

    const chartTitles = [
      '月度 GMV 趋势',
      '订单状态分布',
      'TOP10 品类销售额',
      '支付方式占比',
      'TOP8 省份销售额',
      '近30天订单趋势',
    ]
    for (const title of chartTitles) {
      await expect(dashboard.widgetByTitle(title)).toBeVisible()
    }
  })

  test('ecommerce dashboard preserves widget count in dashboard tabs store', async ({ page }) => {
    const ecommerceDash = makeEcommerceDashboardPayload()
    const json = JSON.stringify(ecommerceDash)
    await renderDashboardBlock(page, { json, streaming: false })

    await dashboard.clickOpenToWorkbench()
    await dashboard.waitForDashboardTab('电商运营大屏')

    const dashState = await page.evaluate(() => {
      const store = (window as any).__DT_E2E__.dashboard()
      const tab = Array.from(store.tabs.values()).find(
        (t: any) => t.dashboard.title === '电商运营大屏',
      )
      if (!tab) return null
      return {
        widgetCount: tab.dashboard.widgets.length,
        widgetTypes: tab.dashboard.widgets.map((w: any) => w.type),
        chartCount: tab.dashboard.widgets.filter((w: any) => w.type === 'chart').length,
        markdownCount: tab.dashboard.widgets.filter((w: any) => w.type === 'markdown').length,
      }
    })

    expect(dashState).not.toBeNull()
    expect(dashState!.widgetCount).toBe(12)
    expect(dashState!.chartCount).toBe(6)
    expect(dashState!.markdownCount).toBe(6)
  })

  test('ecommerce dashboard widget positions fit within 12-column grid', async ({ page }) => {
    const ecommerceDash = makeEcommerceDashboardPayload()
    const json = JSON.stringify(ecommerceDash)
    await renderDashboardBlock(page, { json, streaming: false })

    await dashboard.clickOpenToWorkbench()
    await dashboard.waitForDashboardTab('电商运营大屏')

    const gridViolations = await page.evaluate(() => {
      const store = (window as any).__DT_E2E__.dashboard()
      const tab = Array.from(store.tabs.values()).find(
        (t: any) => t.dashboard.title === '电商运营大屏',
      )
      if (!tab) return []
      return tab.dashboard.widgets
        .filter((w: any) => w.position.x + w.position.w > 12)
        .map((w: any) => `${w.id}: x=${w.position.x} + w=${w.position.w} = ${w.position.x + w.position.w}`)
    })
    expect(gridViolations).toEqual([])
  })

  test('ecommerce dashboard repeated promotion does not create duplicates', async ({ page }) => {
    const ecommerceDash = makeEcommerceDashboardPayload()
    const json = JSON.stringify(ecommerceDash)
    await renderDashboardBlock(page, { json, streaming: false })

    await dashboard.clickOpenToWorkbench()
    await dashboard.waitForDashboardTab('电商运营大屏')

    const afterFirst = await dashboard.getDashboardTabTitles()

    await expect(page.getByRole('button', { name: 'Open to workbench' })).not.toBeVisible()
    await expect(page.getByText('Opened in workbench')).toBeVisible()

    const afterSecond = await dashboard.getDashboardTabTitles()
    expect(afterSecond.length).toBe(afterFirst.length)
  })

  test('ecommerce dashboard API contract — promote and retrieve', async ({ request }) => {
    const ecommerceDash = makeEcommerceDashboardPayload()

    const promoteResp = await request.post('/api/dashboards/promote', {
      data: ecommerceDash,
    })
    expect(promoteResp.ok()).toBe(true)
    const promoteBody = await promoteResp.json()
    expect(promoteBody.id).toMatch(/^dash_ecommerce_/)
    expect(promoteBody.version).toBe(1)

    const getResp = await request.get(`/api/dashboards/${promoteBody.id}`)
    expect(getResp.ok()).toBe(true)
    const getBody = await getResp.json()

    expect(getBody.title).toBe('电商运营大屏')
    expect(getBody.widgets.length).toBe(12)
    expect(getBody.schemaVersion).toBe(1)
    expect(getBody.layout.cols).toBe(12)
  })

  test('ecommerce dashboard visual integrity — chart widgets have echartsOption', async ({ page }) => {
    const ecommerceDash = makeEcommerceDashboardPayload()
    const json = JSON.stringify(ecommerceDash)
    await renderDashboardBlock(page, { json, streaming: false })

    await dashboard.clickOpenToWorkbench()
    await dashboard.waitForDashboardTab('电商运营大屏')

    const chartValidation = await page.evaluate(() => {
      const store = (window as any).__DT_E2E__.dashboard()
      const tab = Array.from(store.tabs.values()).find(
        (t: any) => t.dashboard.title === '电商运营大屏',
      )
      if (!tab) return []
      return tab.dashboard.widgets
        .filter((w: any) => w.type === 'chart')
        .map((w: any) => ({
          id: w.id,
          title: w.options?.title || '(no title)',
          hasEchartsOption: !!w.options?.echartsOption,
          hasDataMapping: !!w.options?.dataMapping,
          seriesCount: w.options?.echartsOption?.series?.length || 0,
        }))
    })

    expect(chartValidation.length).toBe(6)
    for (const chart of chartValidation) {
      expect(chart.hasEchartsOption).toBe(true)
      expect(chart.hasDataMapping).toBe(true)
      expect(chart.seriesCount).toBeGreaterThanOrEqual(1)
    }
  })
})
