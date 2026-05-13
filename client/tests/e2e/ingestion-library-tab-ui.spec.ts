import { test, expect, type Page } from '@playwright/test'
import type { IngestionJobView } from '@/features/ingestion/api/ingestion-api'

// Strategy: page.route() mocking — see openspec/specs/ingestion-ui-e2e-testing/spec.md
// Library tab is a stateless list renderer over GET /api/ingestion/jobs. Backend lifecycle
// is covered by the ingestion-*-mcp API specs; UI-side coverage stays at the HTTP boundary.

const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'

test.describe('@e2e @ingestion @ui Ingestion library tab', () => {
  const now = Date.now()

  test.beforeEach(async ({ page, request }) => {
    // Clean up server-side stage_tab persistence — openTab persists tabs to
    // /api/stage/tabs, and without this hook ingestion_* tabs accumulate across
    // runs (observed: 34 tabs wedging the workbench panel). Also clear
    // localStorage for the open/active flags.
    const list = await request.get(`${BASE}/api/stage/tabs?limit=200`)
    if (list.ok()) {
      const body = await list.json() as { items?: Array<{ tabId: string; type: string }> } | Array<{ tabId: string; type: string }>
      const items = Array.isArray(body) ? body : (body.items ?? [])
      for (const t of items) {
        if (t.type?.startsWith('ingestion_')) {
          await request.delete(`${BASE}/api/stage/tabs/${t.tabId}`)
        }
      }
    }
    await page.addInitScript(() => localStorage.clear())
  })

  function mockJob(overrides: Partial<IngestionJobView> = {}): IngestionJobView {
    return {
      id: `job_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      name: 'mock job',
      sourceUrl: 'http://example.com/api/data',
      status: 'completed',
      payloadFormat: 'json',
      payloadArtifactId: 'artifact_123',
      connectionId: 'conn_42',
      targetSchema: 'public',
      targetTable: 'users',
      rowCount: 100,
      rowsInserted: 0,
      bytesFetched: 4096,
      mapping: null,
      mappingHash: null,
      createdBy: { kind: 'ai', sessionId: 'sess_mock', label: 'AI · mock' },
      heartbeatAt: now,
      createdAt: now,
      updatedAt: now,
      completedAt: now,
      errorMessage: null,
      ...overrides,
    }
  }

  async function openLibraryTab(page: Page) {
    await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      if (stage?.openTab) {
        stage.openTab({
          tabId: `lib_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
          type: 'ingestion_library',
          title: 'Ingestion Library',
          payload: {},
          createdAt: Date.now(),
        })
      }
    })
  }

  test('Library Tab opens via __DT_E2E__', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await openLibraryTab(page)
    await expect(page.getByTestId('ingestion-library-tab')).toBeVisible()
  })

  test('List populates with seeded jobs', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    // Mock the jobs API to return 5 jobs with varied statuses
    const jobs = [
      mockJob({ id: 'job_001', sourceUrl: 'http://alpha.example.com/users', status: 'completed' }),
      mockJob({ id: 'job_002', sourceUrl: 'http://beta.example.com/orders', status: 'failed' }),
      mockJob({ id: 'job_003', sourceUrl: 'http://gamma.example.com/events', status: 'fetching' }),
      mockJob({ id: 'job_004', sourceUrl: 'http://delta.example.com/logs', status: 'mapped' }),
      mockJob({ id: 'job_005', sourceUrl: 'http://epsilon.example.com/metrics', status: 'writing' }),
    ]

    await page.route(`**/api/ingestion/jobs*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: jobs, total: jobs.length }),
      })
    })

    await openLibraryTab(page)

    // All 5 rows should render
    const rows = page.locator('[data-testid^="ingestion-library-row-"]')
    await expect(rows).toHaveCount(5, { timeout: 10000 })

    // Verify specific rows are visible
    await expect(page.getByTestId('ingestion-library-row-job_001')).toBeVisible()
    await expect(page.getByTestId('ingestion-library-row-job_005')).toBeVisible()

    // Total count footer should display
    await expect(page.getByText('5 jobs total')).toBeVisible()
  })

  test('Status filter narrows the rendered set', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    const jobs = [
      mockJob({ id: 'job_completed_1', sourceUrl: 'http://ok1.example.com', status: 'completed' }),
      mockJob({ id: 'job_completed_2', sourceUrl: 'http://ok2.example.com', status: 'completed' }),
      mockJob({ id: 'job_failed_1', sourceUrl: 'http://err.example.com', status: 'failed' }),
    ]

    // Mock honors the `status` query param so the test exercises the real
    // server-side-filter contract — list endpoint must respect ?status=<x>.
    await page.route(`**/api/ingestion/jobs*`, async (route) => {
      const url = new URL(route.request().url())
      const statusFilter = url.searchParams.get('status')
      const filtered = statusFilter ? jobs.filter((j) => j.status === statusFilter) : jobs
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: filtered, total: filtered.length }),
      })
    })

    await openLibraryTab(page)
    await expect(page.locator('[data-testid^="ingestion-library-row-"]')).toHaveCount(3, { timeout: 10000 })

    // Filter to 'completed'
    await page.getByTestId('ingestion-status-filter').click()
    await page.getByRole('option', { name: 'Completed' }).click()

    // After server-side filter, only 2 completed jobs should remain
    await expect(page.locator('[data-testid^="ingestion-library-row-"]')).toHaveCount(2)
    await expect(page.getByText('ok1.example.com')).toBeVisible()
    await expect(page.getByText('ok2.example.com')).toBeVisible()
    await expect(page.getByText('err.example.com')).not.toBeVisible()
  })

  test('Search input filters by source URL / target table / id', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    const jobs = [
      mockJob({ id: 'job_alpha', sourceUrl: 'http://alpha.example.com/users', targetTable: 'users' }),
      mockJob({ id: 'job_beta', sourceUrl: 'http://beta.example.com/orders', targetTable: 'orders' }),
      mockJob({ id: 'job_gamma', sourceUrl: 'http://gamma.example.com/events', targetTable: 'events' }),
    ]

    await page.route(`**/api/ingestion/jobs*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: jobs, total: jobs.length }),
      })
    })

    await openLibraryTab(page)
    await expect(page.locator('[data-testid^="ingestion-library-row-"]')).toHaveCount(3, { timeout: 10000 })

    // Search by source URL substring — only alpha should remain
    await page.getByTestId('ingestion-search-input').fill('alpha')
    await expect(page.getByTestId('ingestion-library-row-job_alpha')).toBeVisible()
    await expect(page.getByTestId('ingestion-library-row-job_beta')).not.toBeVisible()
    await expect(page.getByTestId('ingestion-library-row-job_gamma')).not.toBeVisible()

    // Search by target table
    await page.getByTestId('ingestion-search-input').fill('orders')
    await expect(page.getByTestId('ingestion-library-row-job_beta')).toBeVisible()
    await expect(page.getByTestId('ingestion-library-row-job_alpha')).not.toBeVisible()

    // Search by job id
    await page.getByTestId('ingestion-search-input').fill('job_gamma')
    await expect(page.getByTestId('ingestion-library-row-job_gamma')).toBeVisible()
    await expect(page.getByTestId('ingestion-library-row-job_alpha')).not.toBeVisible()

    // Clear search — all 3 reappear
    await page.getByTestId('ingestion-search-input').fill('')
    await expect(page.locator('[data-testid^="ingestion-library-row-"]')).toHaveCount(3)
  })

  test('Double-clicking a row opens ingestion_job Tab', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    const jobs = [
      mockJob({ id: 'job_dblclick', sourceUrl: 'http://dblclick.example.com/data', status: 'completed' }),
    ]

    // Single handler covering both /jobs (list) and /jobs/{id} (detail).
    // Uses a regex so it also catches the detail path (default `*` glob stops at `/`).
    await page.route(/\/api\/ingestion\/jobs(\/[^/?#]+)?(\?.*)?$/, async (route) => {
      const url = new URL(route.request().url())
      const m = url.pathname.match(/\/api\/ingestion\/jobs\/([^/]+)$/)
      if (m) {
        const id = m[1]
        const found = jobs.find((j) => j.id === id)
        if (!found) return route.fulfill({ status: 404, body: '{}' })
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(found) })
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: jobs, total: jobs.length }),
      })
    })

    await openLibraryTab(page)
    const row = page.getByTestId('ingestion-library-row-job_dblclick')
    await expect(row).toBeVisible({ timeout: 10000 })

    // Double-click the row
    await row.dblclick()

    // An ingestion_job tab should now be visible
    await expect(page.getByTestId('ingestion-job-tab')).toBeVisible({ timeout: 5000 })
  })

  test('Double-clicking the same job row twice opens only one tab', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    const jobs = [
      mockJob({ id: 'job_dedup', sourceUrl: 'http://dedup.example.com/data', status: 'completed' }),
    ]

    await page.route(/\/api\/ingestion\/jobs(\/[^/?#]+)?(\?.*)?$/, async (route) => {
      const url = new URL(route.request().url())
      const m = url.pathname.match(/\/api\/ingestion\/jobs\/([^/]+)$/)
      if (m) {
        const id = m[1]
        const found = jobs.find((j) => j.id === id)
        if (!found) return route.fulfill({ status: 404, body: '{}' })
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(found) })
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: jobs, total: jobs.length }),
      })
    })

    await openLibraryTab(page)
    const row = page.getByTestId('ingestion-library-row-job_dedup')
    await expect(row).toBeVisible({ timeout: 10000 })

    // First double-click opens the job tab
    await row.dblclick()
    await expect(page.getByTestId('ingestion-job-tab')).toBeVisible({ timeout: 5000 })

    // Verify only one tab with this tabId exists
    const tabCount1 = await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      const tabs = stage?.listTabs?.() ?? []
      return tabs.filter((t: { tabId: string }) => t.tabId === 'ingestion_job_job_dedup').length
    })
    expect(tabCount1).toBe(1)

    // Switch back to library tab and double-click the same row again
    await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      const libTab = stage?.listTabs?.()?.find((t: { type: string }) => t.type === 'ingestion_library')
      if (libTab) stage?.focusTab?.(libTab.tabId)
    })
    await page.waitForTimeout(300)
    const rowAgain = page.getByTestId('ingestion-library-row-job_dedup')
    await expect(rowAgain).toBeVisible({ timeout: 5000 })
    await rowAgain.dblclick()

    // After second double-click, still only one tab (focused, not duplicated)
    const tabCount2 = await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      const tabs = stage?.listTabs?.() ?? []
      return tabs.filter((t: { tabId: string }) => t.tabId === 'ingestion_job_job_dedup').length
    })
    expect(tabCount2).toBe(1)
  })

  test('Single-click selects row, double-click opens job tab', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    const jobs = [
      mockJob({ id: 'job_select', sourceUrl: 'http://select.example.com/data', status: 'completed' }),
      mockJob({ id: 'job_other', sourceUrl: 'http://other.example.com/data', status: 'completed' }),
    ]

    await page.route(/\/api\/ingestion\/jobs(\/[^/?#]+)?(\?.*)?$/, async (route) => {
      const url = new URL(route.request().url())
      const m = url.pathname.match(/\/api\/ingestion\/jobs\/([^/]+)$/)
      if (m) {
        const id = m[1]
        const found = jobs.find((j) => j.id === id)
        if (!found) return route.fulfill({ status: 404, body: '{}' })
        return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(found) })
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: jobs, total: jobs.length }),
      })
    })

    await openLibraryTab(page)
    const row = page.getByTestId('ingestion-library-row-job_select')
    await expect(row).toBeVisible({ timeout: 10000 })

    // Single-click selects the row (no tab open yet)
    await row.click()
    // After single-click, the job tab should NOT be open yet
    await expect(page.getByTestId('ingestion-job-tab')).not.toBeVisible({ timeout: 2000 })

    // Double-click opens the job tab
    await row.dblclick()
    await expect(page.getByTestId('ingestion-job-tab')).toBeVisible({ timeout: 5000 })
  })

  test('Created column shows yyyy-MM-dd HH:mm:ss format', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    // Use a fixed timestamp to ensure deterministic format
    const fixedTime = new Date('2026-05-12T18:50:21').getTime()
    const jobs = [
      mockJob({ id: 'job_date', sourceUrl: 'http://date.example.com/data', status: 'completed', createdAt: fixedTime }),
    ]

    await page.route(`**/api/ingestion/jobs*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: jobs, total: jobs.length }),
      })
    })

    await openLibraryTab(page)
    const row = page.getByTestId('ingestion-library-row-job_date')
    await expect(row).toBeVisible({ timeout: 10000 })

    // Verify the Created cell shows yyyy-MM-dd HH:mm:ss
    await expect(row.getByText('2026-05-12 18:50:21')).toBeVisible()
  })

  test('Empty state when no jobs exist', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    await page.route(`**/api/ingestion/jobs*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [], total: 0 }),
      })
    })

    await openLibraryTab(page)
    await expect(page.getByText('No ingestion jobs found')).toBeVisible({ timeout: 10000 })
  })
})
