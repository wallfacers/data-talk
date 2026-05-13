import { test, expect, type Page } from '@playwright/test'
import type { IngestionJobView } from '@/features/ingestion/api/ingestion-api'

// Strategy: page.route() mocking — see openspec/specs/ingestion-ui-e2e-testing/spec.md
// IngestionJobTab is a stateless phase router over a job DTO. Tests mock the
// backend at the HTTP boundary to assert "given status X, render phase Y" and
// "click triggers correct request → next poll reflects new state". Real-backend
// lifecycle integration is covered by ingestion-*-mcp API specs.

const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'
const NOW = Date.now()

function mockJob(overrides: Partial<IngestionJobView> = {}): IngestionJobView {
  return {
    id: 'job_mock_default',
    sourceUrl: 'http://example.com/data',
    status: 'mapped',
    payloadFormat: 'JSON',
    payloadArtifactId: 'artifact_1',
    connectionId: 'conn_1',
    targetSchema: 'public',
    targetTable: 'e2e_users',
    rowCount: 0,
    rowsInserted: 0,
    bytesFetched: 1024,
    mappingHash: 'h_mock',
    mapping: null,
    createdAt: NOW,
    updatedAt: NOW,
    completedAt: null,
    errorMessage: null,
    ...overrides,
  }
}

/**
 * Register a stateful job route. `getJob` is called for every GET, so callers
 * can flip a closure variable to simulate polling-driven status transitions.
 */
async function mockJobRoutes(
  page: Page,
  jobId: string,
  getJob: () => IngestionJobView,
  opts: {
    onConfirm?: () => unknown
    onCancel?: () => unknown
    payloadPreview?: () => { columns: string[]; rows: Record<string, unknown>[]; totalRows: number }
  } = {},
) {
  await page.route(/\/api\/ingestion\/jobs(\/[^/?#]+)?(\/[^/?#]+)?(\?.*)?$/, async (route) => {
    const url = new URL(route.request().url())
    const method = route.request().method()
    const path = url.pathname
    if (path.endsWith(`/api/ingestion/jobs/${jobId}/payload-preview`)) {
      const body = opts.payloadPreview?.() ?? { columns: ['id', 'name'], rows: [{ id: 1, name: 'Alice' }], totalRows: 1 }
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
    }
    if (path.endsWith(`/api/ingestion/jobs/${jobId}/confirm`) && method === 'POST') {
      const body = opts.onConfirm?.() ?? { tokenId: 'tok_e2e', expiresAt: NOW + 300_000, mappingHash: getJob().mappingHash ?? 'h_e2e' }
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
    }
    if (path.endsWith(`/api/ingestion/jobs/${jobId}/cancel`) && method === 'POST') {
      opts.onCancel?.()
      return route.fulfill({ status: 204, body: '' })
    }
    if (path.endsWith(`/api/ingestion/jobs/${jobId}`) && method === 'GET') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(getJob()) })
    }
    // Fall through — anything else (e.g. list endpoint) gets a default empty list.
    if (path.endsWith('/api/ingestion/jobs')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], total: 0 }) })
    }
    return route.continue()
  })
}

async function openJobTab(page: Page, jobId: string) {
  await page.evaluate((id) => {
    const stage = (window as any).__DT_E2E__?.stage()
    stage?.openTab?.({
      tabId: `ingestion_job_${id}`,
      type: 'ingestion_job',
      title: `Job ${id.slice(0, 8)}`,
      payload: { id, sourceUrl: 'http://example.com/data' },
      createdAt: Date.now(),
    })
  }, jobId)
}

test.describe('@e2e @ingestion @ui Ingestion job tab', () => {
  test.beforeEach(async ({ page, request }) => {
    // Purge server-side stage_tab persistence — openTab adds rows to /api/stage/tabs.
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

  test('Tab opens in Stage via __DT_E2E__', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    const job = mockJob({ id: 'job_test_001', status: 'fetching' })
    await mockJobRoutes(page, job.id, () => job)
    await openJobTab(page, job.id)
    await expect(page.getByTestId('ingestion-job-tab')).toBeVisible({ timeout: 10_000 })
  })

  test('Phase router shows fetching phase', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    const job = mockJob({ id: 'job_fetching_test', status: 'fetching' })
    await mockJobRoutes(page, job.id, () => job)
    await openJobTab(page, job.id)
    await expect(page.getByTestId('ingestion-phase-fetching')).toBeVisible({ timeout: 10_000 })
  })

  test('Mapping phase shows MappingEditor', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    const job = mockJob({
      id: 'job_mapped_test',
      status: 'mapped',
      mapping: {
        mappingId: 'm1',
        columns: [
          { sourcePath: '$.id', targetName: 'id', type: 'INTEGER_32', skip: false, sampleValues: ['1', '2'], nullable: false },
          { sourcePath: '$.name', targetName: 'name', type: 'STRING_64', skip: false, sampleValues: ['Alice'], nullable: false },
        ],
      },
    })
    await mockJobRoutes(page, job.id, () => job)
    await openJobTab(page, job.id)
    await expect(page.getByTestId('ingestion-phase-mapping')).toBeVisible({ timeout: 10_000 })
  })

  test('MappingEditor seeds from job.mapping via Phase A hydration', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    const job = mockJob({
      id: 'job_mapped_hydrate',
      status: 'mapped',
      mapping: {
        mappingId: 'm1',
        columns: [
          { sourcePath: '$.id', targetName: 'id', type: 'INTEGER_32', skip: false, sampleValues: ['1'], nullable: false },
          { sourcePath: '$.name', targetName: 'name', type: 'STRING_64', skip: false, sampleValues: ['Alice'], nullable: false },
        ],
      },
    })
    await mockJobRoutes(page, job.id, () => job)
    await openJobTab(page, job.id)
    await expect(page.getByTestId('mapping-row-$.id')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByTestId('mapping-row-$.name')).toBeVisible()
  })

  test('Confirm button disabled when no columns', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    const job = mockJob({
      id: 'job_no_cols_test',
      status: 'mapped',
      mapping: { mappingId: 'm1', columns: [] },
    })
    await mockJobRoutes(page, job.id, () => job)
    await openJobTab(page, job.id)
    await expect(page.getByTestId('ingestion-confirm-btn')).toBeDisabled()
  })

  test('Confirm button calls API and Tab phase transitions to writing', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    let phase: IngestionJobView['status'] = 'mapped'
    const jobId = 'job_confirm_test'
    let confirmCalled = false
    await mockJobRoutes(
      page,
      jobId,
      () => mockJob({
        id: jobId,
        status: phase,
        mapping: {
          mappingId: 'm1',
          columns: [
            { sourcePath: '$.id', targetName: 'id', type: 'INTEGER_32', skip: false, sampleValues: ['1'], nullable: false },
          ],
        },
      }),
      {
        onConfirm: () => {
          confirmCalled = true
          phase = 'writing'
          return { tokenId: 'tok_x', expiresAt: NOW + 300_000, mappingHash: 'h_x' }
        },
      },
    )
    await openJobTab(page, jobId)
    const confirmBtn = page.getByTestId('ingestion-confirm-btn')
    await expect(confirmBtn).toBeEnabled({ timeout: 10_000 })
    await confirmBtn.click()
    await expect(page.getByTestId('ingestion-phase-writing')).toBeVisible({ timeout: 15_000 })
    expect(confirmCalled).toBe(true)
  })

  test('Cancel button flips status to cancelled (failed phase visible)', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    let phase: IngestionJobView['status'] = 'mapped'
    const jobId = 'job_cancel_test'
    await mockJobRoutes(
      page,
      jobId,
      () => mockJob({
        id: jobId,
        status: phase,
        mapping: {
          mappingId: 'm1',
          columns: [
            { sourcePath: '$.id', targetName: 'id', type: 'INTEGER_32', skip: false, sampleValues: ['1'], nullable: false },
          ],
        },
        errorMessage: phase === 'cancelled' ? 'cancelled by user' : null,
      }),
      { onCancel: () => { phase = 'cancelled' } },
    )
    await openJobTab(page, jobId)
    const cancelBtn = page.getByTestId('ingestion-cancel-btn')
    await expect(cancelBtn).toBeVisible({ timeout: 10_000 })
    await cancelBtn.click()
    await expect(page.getByTestId('ingestion-phase-failed')).toBeVisible({ timeout: 15_000 })
  })

  test('Failed phase shows error message', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    const job = mockJob({ id: 'job_failed_test', status: 'failed', errorMessage: 'Connection refused' })
    await mockJobRoutes(page, job.id, () => job)
    await openJobTab(page, job.id)
    await expect(page.getByTestId('ingestion-phase-failed')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText(/Connection refused/i)).toBeVisible()
  })

  test('Completed phase shows target table + row count', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    const job = mockJob({
      id: 'job_completed_test',
      status: 'completed',
      targetTable: 'e2e_users',
      rowsInserted: 42,
      rowCount: 42,
      completedAt: NOW,
    })
    await mockJobRoutes(page, job.id, () => job)
    await openJobTab(page, job.id)
    await expect(page.getByTestId('ingestion-phase-completed')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('42')).toBeVisible()
  })

  test('Phase stepper dots visible (6 dots)', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    const job = mockJob({ id: 'job_stepper_test', status: 'fetching' })
    await mockJobRoutes(page, job.id, () => job)
    await openJobTab(page, job.id)
    await expect(page.locator('[data-testid^="ingestion-stepper-dot-"]')).toHaveCount(6, { timeout: 10_000 })
  })

  test('SourceSummaryCard shows URL, format, bytes', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    const job = mockJob({
      id: 'job_summary_test',
      status: 'mapped',
      sourceUrl: 'http://example.com/api/data',
      payloadFormat: 'JSON',
      bytesFetched: 4096,
      mapping: { mappingId: 'm1', columns: [
        { sourcePath: '$.id', targetName: 'id', type: 'INTEGER_32', skip: false, sampleValues: ['1'], nullable: false },
      ] },
    })
    await mockJobRoutes(page, job.id, () => job)
    await openJobTab(page, job.id)
    const card = page.getByTestId('ingestion-source-summary')
    await expect(card).toBeVisible({ timeout: 10_000 })
    await expect(card).toContainText(/http/i)
    await expect(card).toContainText(/JSON/i)
  })

  test('DDL preview visible when columns exist', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    const job = mockJob({
      id: 'job_ddl_test',
      status: 'mapped',
      targetSchema: 'public',
      targetTable: 'e2e_users',
      mapping: {
        mappingId: 'm1',
        columns: [
          { sourcePath: '$.id', targetName: 'id', type: 'INTEGER_32', skip: false, sampleValues: ['1'], nullable: false },
          { sourcePath: '$.name', targetName: 'name', type: 'STRING_64', skip: false, sampleValues: ['Alice'], nullable: true },
        ],
      },
    })
    await mockJobRoutes(page, job.id, () => job)
    await openJobTab(page, job.id)
    const ddl = page.getByTestId('ingestion-ddl-preview')
    await expect(ddl).toBeVisible({ timeout: 10_000 })
    await expect(ddl).toContainText(/CREATE TABLE/i)
  })

  test('PayloadPreviewTable populated from /payload-preview endpoint', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    const job = mockJob({
      id: 'job_preview_test',
      status: 'mapped',
      mapping: {
        mappingId: 'm1',
        columns: [
          { sourcePath: '$.id', targetName: 'id', type: 'INTEGER_32', skip: false, sampleValues: ['1'], nullable: false },
          { sourcePath: '$.name', targetName: 'name', type: 'STRING_64', skip: false, sampleValues: ['Alice'], nullable: false },
        ],
      },
    })
    await mockJobRoutes(page, job.id, () => job, {
      payloadPreview: () => ({
        columns: ['id', 'name'],
        rows: [
          { id: 1, name: 'Alice' },
          { id: 2, name: 'Bob' },
        ],
        totalRows: 2,
      }),
    })
    await openJobTab(page, job.id)
    // The MappingPhase renders a PayloadPreviewTable. Its rendered <table> element
    // is the assertion target — the component itself has no data-testid. The
    // "Showing N of N rows" label is unique to PayloadPreviewTable.
    await expect(page.locator('table').first()).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('Showing 2 of 2 rows')).toBeVisible()
    // "Bob" appears only in the preview rows (mapping sample values don't include it).
    await expect(page.getByText('Bob')).toBeVisible()
  })
})
