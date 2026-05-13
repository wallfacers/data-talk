import { test, expect, type Page } from '@playwright/test'
import type { IngestionJobView } from '@/features/ingestion/api/ingestion-api'

// Strategy: page.route() mocking — pure UI-boundary coverage for the
// `ingestion-name-creator-and-stop` change. Backend lifecycle is covered by
// IngestionStopServiceTest + IngestionStartupSweeperIT + IngestionHeartbeatSweeperIT.

const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'
const NOW = Date.now()

function mockJob(overrides: Partial<IngestionJobView> = {}): IngestionJobView {
  return {
    id: `job_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    name: 'mock job',
    sourceUrl: 'http://example.com/api/data',
    status: 'writing',
    payloadFormat: 'json',
    payloadArtifactId: 'artifact_1',
    connectionId: 'conn_1',
    targetSchema: 'public',
    targetTable: 'users',
    rowCount: 100,
    rowsInserted: 0,
    bytesFetched: 4096,
    mapping: null,
    mappingHash: null,
    createdBy: { kind: 'ai', sessionId: 'sess_mock', label: 'AI · mock' },
    heartbeatAt: NOW,
    createdAt: NOW,
    updatedAt: NOW,
    completedAt: null,
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

async function mockJobsRoute(page: Page, jobs: IngestionJobView[]) {
  await page.route(`**/api/ingestion/jobs?*`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ items: jobs, total: jobs.length }),
    })
  })
  await page.route(`**/api/ingestion/jobs`, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: jobs, total: jobs.length }),
      })
    } else {
      await route.continue()
    }
  })
}

test.describe('@e2e @ingestion @ui Library — name + creator + stop', () => {
  test.beforeEach(async ({ page, request }) => {
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

  test('Name column renders job.name (and em-dash fallback when null)', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    const jobs = [
      mockJob({ id: 'job_named', name: 'Stripe customers — 2026-05' }),
      mockJob({ id: 'job_noname', name: null }),
    ]
    await mockJobsRoute(page, jobs)
    await openLibraryTab(page)
    await expect(page.getByTestId('ingestion-name-job_named')).toContainText('Stripe customers — 2026-05')
    await expect(page.getByTestId('ingestion-name-job_noname')).toContainText('—')
  })

  test('Creator cell is clickable when sessionId present', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    const jobs = [
      mockJob({ id: 'job_with_session', createdBy: { kind: 'ai', sessionId: 'sess_abc', label: 'first ingest' } }),
      mockJob({ id: 'job_no_session', createdBy: { kind: 'ai', sessionId: null, label: null } }),
    ]
    await mockJobsRoute(page, jobs)
    await openLibraryTab(page)

    const link = page.getByTestId('ingestion-creator-link-job_with_session')
    await expect(link).toBeVisible()
    await expect(link).toContainText('first ingest')

    // Non-session row renders the bare AI label rather than a button.
    await expect(page.getByTestId('ingestion-creator-link-job_no_session')).toHaveCount(0)
    await expect(page.getByTestId('ingestion-creator-job_no_session')).toContainText('AI')
  })

  test('Stop button visibility honours per-status set', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    const jobs = [
      mockJob({ id: 'job_writing', status: 'writing' }),
      mockJob({ id: 'job_fetching', status: 'fetching' }),
      mockJob({ id: 'job_completed', status: 'completed' }),
      mockJob({ id: 'job_failed', status: 'failed' }),
    ]
    await mockJobsRoute(page, jobs)
    await openLibraryTab(page)

    await expect(page.getByTestId('ingestion-stop-job_writing')).toHaveCount(1)
    await expect(page.getByTestId('ingestion-stop-job_fetching')).toHaveCount(1)
    await expect(page.getByTestId('ingestion-stop-job_completed')).toHaveCount(0)
    await expect(page.getByTestId('ingestion-stop-job_failed')).toHaveCount(0)
  })

  test('Stop dialog → confirm → POST /stop and toast', async ({ page }) => {
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))

    let stopCalls: Array<{ url: string }> = []
    await page.route('**/api/ingestion/jobs/job_stop_target/stop*', async (route) => {
      stopCalls.push({ url: route.request().url() })
      await route.fulfill({ status: 202, body: '' })
    })

    const jobs = [mockJob({ id: 'job_stop_target', status: 'writing', targetSchema: 'public', targetTable: 'demo_table' })]
    await mockJobsRoute(page, jobs)
    await openLibraryTab(page)

    await page.getByTestId('ingestion-stop-job_stop_target').click()
    await expect(page.getByTestId('ingestion-stop-dialog')).toBeVisible()
    await expect(page.getByTestId('ingestion-stop-dialog')).toContainText('public.demo_table')

    await page.getByTestId('ingestion-stop-confirm').click()
    await expect.poll(() => stopCalls.length).toBeGreaterThan(0)
    expect(stopCalls[0].url).not.toMatch(/force=true/)
  })
})
