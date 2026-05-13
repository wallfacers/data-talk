import { test, expect, type Page } from '@playwright/test'
import type { IngestionJobView } from '@/features/ingestion/api/ingestion-api'

/**
 * Ingestion Library UI Fix Verification
 *
 * Covers three fixes:
 *   1. Created column: yyyy-MM-dd HH:mm:ss (formatDateTime)
 *   2. Double-click dedup: open-or-focus pattern
 *   3. Style alignment with sql-result-table.tsx
 *
 * Strategy: page.route() — mocks GET /api/ingestion/jobs at the HTTP boundary.
 * Tabs opened programmatically via window.__DT_E2E__.stage().openTab().
 */

const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'

// ---------------------------------------------------------------------------
// Mock data factory
// ---------------------------------------------------------------------------

const NOW = Date.now()

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
    rowsInserted: 100,
    bytesFetched: 4096,
    mapping: null,
    mappingHash: null,
    createdBy: { kind: 'ai', sessionId: 'sess_mock', label: 'AI · mock' },
    heartbeatAt: NOW,
    createdAt: NOW,
    updatedAt: NOW,
    completedAt: NOW,
    errorMessage: null,
    ...overrides,
  }
}

/**
 * Seed dataset — 10 jobs covering all status types, varied dates, row counts,
 * long URLs, and schema-less targets.
 */
function seedJobs(): IngestionJobView[] {
  return [
    mockJob({ id: 'seed_completed_1', sourceUrl: 'https://api.example.com/v2/users', status: 'completed', targetSchema: 'public', targetTable: 'imported_users', rowCount: 12345, createdAt: new Date('2026-05-13T09:15:30').getTime() }),
    mockJob({ id: 'seed_completed_2', sourceUrl: 'https://data.example.com/export/orders', status: 'completed', targetSchema: 'sales', targetTable: 'orders', rowCount: 89012, createdAt: new Date('2026-05-12T23:59:59').getTime() }),
    mockJob({ id: 'seed_failed_1', sourceUrl: 'https://unreliable.example.com/feed', status: 'failed', targetSchema: null, targetTable: null, rowCount: null, errorMessage: 'Connection refused', createdAt: new Date('2026-05-12T14:30:00').getTime() }),
    mockJob({ id: 'seed_fetching_1', sourceUrl: 'https://slow.example.com/big-dataset', status: 'fetching', targetSchema: 'raw', targetTable: 'big_data', rowCount: null, createdAt: new Date('2026-05-13T10:00:00').getTime() }),
    mockJob({ id: 'seed_mapped_1', sourceUrl: 'https://staging.example.com/api/v1/products', status: 'mapped', targetSchema: 'inventory', targetTable: 'products', rowCount: 500, createdAt: new Date('2026-01-01T00:00:00').getTime() }),
    mockJob({ id: 'seed_writing_1', sourceUrl: 'https://batch.example.com/daily-sync', status: 'writing', targetSchema: 'public', targetTable: 'daily_sync', rowCount: 2500, createdAt: new Date('2026-05-13T08:00:00').getTime() }),
    mockJob({ id: 'seed_cancelled_1', sourceUrl: 'https://cancelled.example.com/abort', status: 'cancelled', targetSchema: null, targetTable: null, rowCount: null, createdAt: new Date('2026-05-11T16:45:00').getTime() }),
    mockJob({ id: 'seed_fetched_1', sourceUrl: 'https://cdn.example.com/static/catalog.json', status: 'fetched', targetSchema: 'catalog', targetTable: 'items', rowCount: 0, createdAt: new Date('2026-05-13T07:22:11').getTime() }),
    mockJob({ id: 'seed_confirmed_1', sourceUrl: 'https://partner.example.com/feeds/transactions', status: 'confirmed', targetSchema: 'finance', targetTable: 'transactions', rowCount: 999999, createdAt: new Date('2026-05-10T12:00:00').getTime() }),
    mockJob({ id: 'seed_long_url', sourceUrl: 'https://very-long-subdomain.deeply-nested.example.com/api/v3/endpoint-with-many-segments/data-export?format=json&limit=10000&offset=0&fields=id,name,email,created_at,updated_at,status', status: 'completed', targetSchema: 'public', targetTable: 'long_import', rowCount: 42, createdAt: new Date('2026-05-13T11:11:11').getTime() }),
  ]
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function openLibraryTab(page: Page) {
  await page.evaluate(() => {
    const stage = (window as any).__DT_E2E__?.stage()
    if (stage?.openTab) {
      stage.openTab({
        tabId: `lib_fix_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
        type: 'ingestion_library',
        title: 'Ingestion Library',
        payload: {},
        createdAt: Date.now(),
      })
    }
  })
}

/** Install page.route() mock for GET /api/ingestion/jobs and /api/ingestion/jobs/:id */
function mockJobsRoute(page: Page, jobs: IngestionJobView[], opts?: { filterByStatus?: boolean }) {
  const pattern = /\/api\/ingestion\/jobs(\/[^/?#]+)?(\?.*)?$/
  page.route(pattern, async (route) => {
    const url = new URL(route.request().url())
    const m = url.pathname.match(/\/api\/ingestion\/jobs\/([^/]+)$/)
    if (m) {
      const found = jobs.find((j) => j.id === m[1])
      if (!found) return route.fulfill({ status: 404, body: '{}' })
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(found) })
    }
    let result = jobs
    if (opts?.filterByStatus) {
      const statusParam = url.searchParams.get('status')
      if (statusParam) result = jobs.filter((j) => j.status === statusParam)
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ items: result, total: result.length }),
    })
  })
}

// ---------------------------------------------------------------------------
// Test suite
// ---------------------------------------------------------------------------

test.describe('@e2e @ingestion @ui Ingestion Library UI fix verification', () => {

  test.beforeEach(async ({ page, request }) => {
    // Clean up server-side stage_tab persistence
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

  // =========================================================================
  // Fix 1: Created time format — yyyy-MM-dd HH:mm:ss
  // =========================================================================

  test('FIX-1: Created column uses yyyy-MM-dd HH:mm:ss format', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    const jobs = seedJobs()
    mockJobsRoute(page, jobs)
    await openLibraryTab(page)

    const rows = page.locator('[data-testid^="ingestion-library-row-"]')
    await expect(rows).toHaveCount(jobs.length, { timeout: 10000 })

    // Spot-check specific dates from seed data
    const expected: [string, string][] = [
      ['seed_completed_1', '2026-05-13 09:15:30'],
      ['seed_completed_2', '2026-05-12 23:59:59'],
      ['seed_failed_1', '2026-05-12 14:30:00'],
      ['seed_long_url', '2026-05-13 11:11:11'],
      ['seed_cancelled_1', '2026-05-11 16:45:00'],
    ]
    for (const [id, dateStr] of expected) {
      const row = page.getByTestId(`ingestion-library-row-${id}`)
      await expect(row).toBeVisible()
      await expect(row.locator('td').nth(4)).toHaveText(dateStr)
    }
  })

  test('FIX-1: Date format handles edge cases — midnight, single-digit month', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    const jobs = [
      mockJob({ id: 'edge_midnight', createdAt: new Date('2026-01-01T00:00:00').getTime() }),
      mockJob({ id: 'edge_end_of_day', createdAt: new Date('2026-12-31T23:59:59').getTime() }),
      mockJob({ id: 'edge_short_month', createdAt: new Date('2026-03-05T08:07:09').getTime() }),
    ]
    mockJobsRoute(page, jobs)
    await openLibraryTab(page)

    await expect(page.locator('[data-testid^="ingestion-library-row-"]')).toHaveCount(3, { timeout: 10000 })

    await expect(page.getByTestId('ingestion-library-row-edge_midnight').locator('td').nth(4)).toHaveText('2026-01-01 00:00:00')
    await expect(page.getByTestId('ingestion-library-row-edge_end_of_day').locator('td').nth(4)).toHaveText('2026-12-31 23:59:59')
    await expect(page.getByTestId('ingestion-library-row-edge_short_month').locator('td').nth(4)).toHaveText('2026-03-05 08:07:09')
  })

  // =========================================================================
  // Fix 2: Double-click dedup — open-or-focus pattern
  // =========================================================================

  test('FIX-2: Double-clicking same row twice produces only one tab', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    const jobs = [mockJob({ id: 'dedup_test', status: 'completed' })]
    mockJobsRoute(page, jobs)
    await openLibraryTab(page)

    const row = page.getByTestId('ingestion-library-row-dedup_test')
    await expect(row).toBeVisible({ timeout: 10000 })

    // First double-click
    await row.dblclick()
    await expect(page.getByTestId('ingestion-job-tab')).toBeVisible({ timeout: 5000 })

    // Verify exactly one tab
    const count1 = await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      return (stage?.listTabs?.() ?? []).filter((t: any) => t.tabId === 'ingestion_job_dedup_test').length
    })
    expect(count1).toBe(1)

    // Switch back to library tab
    await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      const lib = stage?.listTabs?.().find((t: any) => t.type === 'ingestion_library')
      if (lib) stage?.focusTab?.(lib.tabId)
    })
    await page.waitForTimeout(300)
    await expect(page.getByTestId('ingestion-library-row-dedup_test')).toBeVisible({ timeout: 5000 })

    // Second double-click
    await page.getByTestId('ingestion-library-row-dedup_test').dblclick()

    // Still only one tab
    const count2 = await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      return (stage?.listTabs?.() ?? []).filter((t: any) => t.tabId === 'ingestion_job_dedup_test').length
    })
    expect(count2).toBe(1)
  })

  test('FIX-2: Different rows open different tabs', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    const jobs = [
      mockJob({ id: 'dedup_a', status: 'completed' }),
      mockJob({ id: 'dedup_b', status: 'completed' }),
    ]
    mockJobsRoute(page, jobs)
    await openLibraryTab(page)

    await expect(page.locator('[data-testid^="ingestion-library-row-"]')).toHaveCount(2, { timeout: 10000 })

    // Double-click first row
    await page.getByTestId('ingestion-library-row-dedup_a').dblclick()
    await expect(page.getByTestId('ingestion-job-tab')).toBeVisible({ timeout: 5000 })

    // Switch back
    await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      const lib = stage?.listTabs?.().find((t: any) => t.type === 'ingestion_library')
      if (lib) stage?.focusTab?.(lib.tabId)
    })
    await page.waitForTimeout(300)

    // Double-click second row
    await page.getByTestId('ingestion-library-row-dedup_b').dblclick()
    await page.waitForTimeout(500)

    // Both tabs should exist
    const tabs = await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      const all = stage?.listTabs?.() ?? []
      return {
        a: all.filter((t: any) => t.tabId === 'ingestion_job_dedup_a').length,
        b: all.filter((t: any) => t.tabId === 'ingestion_job_dedup_b').length,
      }
    })
    expect(tabs.a).toBe(1)
    expect(tabs.b).toBe(1)
  })

  // =========================================================================
  // Fix 3: Style alignment with sql-result-table.tsx
  // =========================================================================

  test('FIX-3: Table uses text-xs and min-w-max matching sql-result-table', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    mockJobsRoute(page, seedJobs())
    await openLibraryTab(page)
    await expect(page.locator('[data-testid^="ingestion-library-row-"]')).toHaveCount(10, { timeout: 10000 })

    // <table> has min-w-max text-xs
    const table = page.locator('[data-testid="ingestion-library-tab"] table')
    const tableClass = await table.getAttribute('class') ?? ''
    expect(tableClass).toContain('min-w-max')
    expect(tableClass).toContain('text-xs')
  })

  test('FIX-3: Header cells are sticky with bg-muted and correct border', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    mockJobsRoute(page, seedJobs())
    await openLibraryTab(page)
    await expect(page.locator('[data-testid^="ingestion-library-row-"]')).toHaveCount(10, { timeout: 10000 })

    const headerCells = page.locator('[data-testid="ingestion-library-tab"] thead th')
    const count = await headerCells.count()
    expect(count).toBe(5)

    for (let i = 0; i < count; i++) {
      const cls = await headerCells.nth(i).getAttribute('class') ?? ''
      // sticky top-0 z-20
      expect(cls, `header cell ${i}: sticky top-0`).toContain('sticky')
      expect(cls, `header cell ${i}: top-0`).toContain('top-0')
      expect(cls, `header cell ${i}: z-20`).toContain('z-20')
      // h-8
      expect(cls, `header cell ${i}: h-8`).toContain('h-8')
      // border-b border-border/50
      expect(cls, `header cell ${i}: border-b`).toContain('border-b')
      expect(cls, `header cell ${i}: border-border/50`).toContain('border-border/50')
      // bg-muted
      expect(cls, `header cell ${i}: bg-muted`).toContain('bg-muted')
      // px-3
      expect(cls, `header cell ${i}: px-3`).toContain('px-3')
    }
  })

  test('FIX-3: Header row has hover:bg-transparent', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    mockJobsRoute(page, seedJobs())
    await openLibraryTab(page)
    await expect(page.locator('[data-testid^="ingestion-library-row-"]')).toHaveCount(10, { timeout: 10000 })

    const headerRow = page.locator('[data-testid="ingestion-library-tab"] thead tr')
    const cls = await headerRow.getAttribute('class') ?? ''
    expect(cls).toContain('hover:bg-transparent')
  })

  test('FIX-3: Data rows have border-b border-border/30 and px-3 py-1.5 cells', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    mockJobsRoute(page, seedJobs())
    await openLibraryTab(page)
    await expect(page.locator('[data-testid^="ingestion-library-row-"]')).toHaveCount(10, { timeout: 10000 })

    // First data row
    const row = page.getByTestId('ingestion-library-row-seed_completed_1')
    const rowCls = await row.getAttribute('class') ?? ''
    expect(rowCls, 'data row: border-b').toContain('border-b')
    expect(rowCls, 'data row: border-border/30').toContain('border-border/30')

    // Data cells
    const cells = row.locator('td')
    const cellCount = await cells.count()
    expect(cellCount).toBe(5)

    for (let i = 0; i < cellCount; i++) {
      const cls = await cells.nth(i).getAttribute('class') ?? ''
      expect(cls, `cell ${i}: px-3`).toContain('px-3')
      expect(cls, `cell ${i}: py-1.5`).toContain('py-1.5')
    }
  })

  test('FIX-3: Search box is native input with h-7 wrapper', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    mockJobsRoute(page, seedJobs())
    await openLibraryTab(page)
    await expect(page.locator('[data-testid^="ingestion-library-row-"]')).toHaveCount(10, { timeout: 10000 })

    // Verify native <input>, not shadcn Input
    const input = page.getByTestId('ingestion-search-input')
    const tagName = await input.evaluate((el) => el.tagName.toLowerCase())
    expect(tagName).toBe('input')

    // Wrapper div has h-7, rounded-md, border
    const wrapper = input.locator('..')
    const wrapperCls = await wrapper.getAttribute('class') ?? ''
    expect(wrapperCls, 'search wrapper: h-7').toContain('h-7')
    expect(wrapperCls, 'search wrapper: rounded-md').toContain('rounded-md')
    expect(wrapperCls, 'search wrapper: border-border').toContain('border-border')

    // Input has text-xs
    const inputCls = await input.getAttribute('class') ?? ''
    expect(inputCls, 'search input: text-xs').toContain('text-xs')
  })

  test('FIX-3: Status filter SelectTrigger matches search box height', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    mockJobsRoute(page, seedJobs())
    await openLibraryTab(page)
    await expect(page.locator('[data-testid^="ingestion-library-row-"]')).toHaveCount(10, { timeout: 10000 })

    const trigger = page.getByTestId('ingestion-status-filter')
    const triggerHeight = await trigger.evaluate((el) => el.getBoundingClientRect().height)

    const searchWrapper = page.getByTestId('ingestion-search-input').locator('..')
    const searchHeight = await searchWrapper.evaluate((el) => el.getBoundingClientRect().height)

    // Both must be exactly 28px (h-7)
    expect(triggerHeight, 'SelectTrigger height').toBe(28)
    expect(searchHeight, 'search wrapper height').toBe(28)
    expect(triggerHeight).toBe(searchHeight)
  })

  test('FIX-3: Toolbar has border-b border-border/50 px-3 py-2', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    mockJobsRoute(page, seedJobs())
    await openLibraryTab(page)
    await expect(page.locator('[data-testid^="ingestion-library-row-"]')).toHaveCount(10, { timeout: 10000 })

    // The toolbar is the first child div after the root container
    const toolbar = page.locator('[data-testid="ingestion-library-tab"] > div').first()
    const cls = await toolbar.getAttribute('class') ?? ''
    expect(cls, 'toolbar: border-b').toContain('border-b')
    expect(cls, 'toolbar: border-border/50').toContain('border-border/50')
    expect(cls, 'toolbar: px-3').toContain('px-3')
    expect(cls, 'toolbar: py-2').toContain('py-2')
  })

  test('FIX-3: Footer has border-t border-border/50 px-3 py-2 text-xs', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    mockJobsRoute(page, seedJobs())
    await openLibraryTab(page)
    await expect(page.locator('[data-testid^="ingestion-library-row-"]')).toHaveCount(10, { timeout: 10000 })

    // Footer is the last child div
    const footer = page.locator('[data-testid="ingestion-library-tab"] > div').last()
    const footerCls = await footer.getAttribute('class') ?? ''
    expect(footerCls, 'footer: border-t').toContain('border-t')
    expect(footerCls, 'footer: border-border/50').toContain('border-border/50')
    expect(footerCls, 'footer: px-3').toContain('px-3')
    expect(footerCls, 'footer: py-2').toContain('py-2')

    // Footer text is text-xs
    const footerText = footer.locator('span')
    const textCls = await footerText.getAttribute('class') ?? ''
    expect(textCls, 'footer text: text-xs').toContain('text-xs')
  })

  test('FIX-3: Icons are size-3.5 with text-muted-foreground', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    mockJobsRoute(page, seedJobs())
    await openLibraryTab(page)
    await expect(page.locator('[data-testid^="ingestion-library-row-"]')).toHaveCount(10, { timeout: 10000 })

    // Download icon in toolbar
    const downloadIcon = page.locator('[data-testid="ingestion-library-tab"] svg.lucide-download')
    const dlCls = await downloadIcon.getAttribute('class') ?? ''
    expect(dlCls, 'download icon: size-3.5').toContain('size-3.5')
    expect(dlCls, 'download icon: text-muted-foreground').toContain('text-muted-foreground')

    // Search icon
    const searchIcon = page.locator('[data-testid="ingestion-library-tab"] svg.lucide-search')
    const srCls = await searchIcon.getAttribute('class') ?? ''
    expect(srCls, 'search icon: size-3.5').toContain('size-3.5')
    expect(srCls, 'search icon: text-muted-foreground').toContain('text-muted-foreground')
  })

  test('FIX-3: Data cells use default foreground color (no text-muted-foreground)', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    mockJobsRoute(page, seedJobs())
    await openLibraryTab(page)
    await expect(page.locator('[data-testid^="ingestion-library-row-"]')).toHaveCount(10, { timeout: 10000 })

    const row = page.getByTestId('ingestion-library-row-seed_completed_1')
    const cells = row.locator('td')

    // Rows column (index 3) — default foreground, NOT muted
    const rowsCls = await cells.nth(3).getAttribute('class') ?? ''
    expect(rowsCls, 'rows cell should NOT be muted').not.toContain('text-muted-foreground')

    // Created column (index 4) — default foreground with mono, NOT muted
    const createdCls = await cells.nth(4).getAttribute('class') ?? ''
    expect(createdCls, 'created cell should NOT be muted').not.toContain('text-muted-foreground')
    expect(createdCls, 'created cell: font-mono').toContain('font-mono')
  })
})
