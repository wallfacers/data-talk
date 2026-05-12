import { test, expect } from '@playwright/test'
import { adapterClient } from './fixtures/adapter-client'
import { startMockIngestionServer, type MockServer, seedH2Connection } from './fixtures/ingestion-fixtures'

const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'

let mock: MockServer
test.beforeAll(async () => { mock = await startMockIngestionServer() })
test.afterAll(async () => { await mock.stop() })

test.describe('@e2e @ingestion @ui Ingestion job tab', () => {
  test('Tab opens in Stage via __DT_E2E__', async ({ page }) => {
    test.fixme(true, 'UI test requires backend job data — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    // Open an ingestion_job tab via the E2E store
    await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      if (stage?.openTab) {
        stage.openTab({ type: 'ingestion_job', payload: { id: 'job_test_001' } })
      }
    })
    await expect(page.getByTestId('ingestion-job-tab')).toBeVisible()
  })

  test('Phase router shows fetching phase', async ({ page }) => {
    test.fixme(true, 'UI test requires backend job data — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      if (stage?.openTab) {
        stage.openTab({ type: 'ingestion_job', payload: { id: 'job_fetching_test' } })
      }
    })
    await expect(page.getByTestId('ingestion-phase-fetching')).toBeVisible()
  })

  test('Mapping phase shows MappingEditor', async ({ page }) => {
    test.fixme(true, 'UI test requires backend job data — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      if (stage?.openTab) {
        stage.openTab({ type: 'ingestion_job', payload: { id: 'job_mapped_test' } })
      }
    })
    await expect(page.getByTestId('ingestion-phase-mapping')).toBeVisible()
  })

  test('MappingEditor seeds from job.mapping via Phase A hydration', async ({ page }) => {
    test.fixme(true, 'UI test requires backend job data — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      if (stage?.openTab) {
        stage.openTab({ type: 'ingestion_job', payload: { id: 'job_mapped_test' } })
      }
    })
    // MappingEditor renders mapping columns with targetName and type fields
    const mappingEditor = page.locator('[role="table"]').first()
    await expect(mappingEditor).toBeVisible({ timeout: 5000 })
  })

  test('Confirm button disabled when no columns', async ({ page }) => {
    test.fixme(true, 'UI test requires backend job data — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      if (stage?.openTab) {
        stage.openTab({ type: 'ingestion_job', payload: { id: 'job_no_cols_test' } })
      }
    })
    const btn = page.getByTestId('ingestion-confirm-btn')
    await expect(btn).toBeDisabled()
  })

  test('Confirm button calls API and Tab phase transitions to confirmed -> writing', async ({ page, request }) => {
    test.fixme(true, 'UI test requires backend job data — blocked on BUG-0013 fix')
    // Seeds a job in mapped status via the backend API, then clicks confirm
    // and verifies phase transitions to writing.
    // Depends on BUG-0013 fix to properly seed the job.
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    // Step 1: seed a mapped job via POST /api/ingestion/jobs
    const seedRes = await request.post(`${BASE}/api/ingestion/jobs`, {
      data: {
        sourceUrl: `${mock.baseUrl}/json/users`,
        connectionId: null,
        targetSchema: 'public',
        targetTable: 'e2e_confirm_test',
        payloadFormat: 'JSON',
      },
    })
    expect(seedRes.ok()).toBe(true)
    const job = await seedRes.json() as { id: string }
    // Step 2: open the tab
    await page.evaluate((jobId) => {
      const stage = (window as any).__DT_E2E__?.stage()
      if (stage?.openTab) {
        stage.openTab({ type: 'ingestion_job', payload: { id: jobId } })
      }
    }, job.id)
    await expect(page.getByTestId('ingestion-job-tab')).toBeVisible()
    // Step 3: click confirm
    const confirmBtn = page.getByTestId('ingestion-confirm-btn')
    await expect(confirmBtn).toBeEnabled()
    await confirmBtn.click()
    // Step 4: verify phase transitions to writing
    await expect(page.getByTestId('ingestion-phase-writing')).toBeVisible({ timeout: 10_000 })
  })

  test('Cancel button flips status to cancelled', async ({ page }) => {
    test.fixme(true, 'UI test requires backend job data — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      if (stage?.openTab) {
        stage.openTab({ type: 'ingestion_job', payload: { id: 'job_cancel_test' } })
      }
    })
    const cancelBtn = page.getByTestId('ingestion-cancel-btn')
    if (await cancelBtn.isVisible()) {
      await cancelBtn.click()
    }
    // After cancel, job should be in cancelled state, shown as failed phase
    await expect(page.getByTestId('ingestion-phase-failed')).toBeVisible({ timeout: 10_000 })
  })

  test('Failed phase shows error message', async ({ page }) => {
    test.fixme(true, 'UI test requires backend job data — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      if (stage?.openTab) {
        stage.openTab({ type: 'ingestion_job', payload: { id: 'job_failed_test' } })
      }
    })
    await expect(page.getByTestId('ingestion-phase-failed')).toBeVisible()
    // Error message and userHint should be visible
    const errorMessage = page.locator('text=/Connection refused|failed|error/i').first()
    await expect(errorMessage).toBeVisible()
  })

  test('Completed phase shows target table + row count', async ({ page }) => {
    test.fixme(true, 'UI test requires backend job data — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      if (stage?.openTab) {
        stage.openTab({ type: 'ingestion_job', payload: { id: 'job_completed_test' } })
      }
    })
    await expect(page.getByTestId('ingestion-phase-completed')).toBeVisible()
    // CompletedPhase renders rowCount and target table info
    await expect(page.locator('text=/rowsInserted|rows inserted|row count/i')).toBeVisible()
  })

  test('Phase stepper dots visible', async ({ page }) => {
    test.fixme(true, 'UI test requires backend job data — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      if (stage?.openTab) {
        stage.openTab({ type: 'ingestion_job', payload: { id: 'job_stepper_test' } })
      }
    })
    const dots = page.locator('[data-testid^="ingestion-stepper-dot-"]')
    await expect(dots).toHaveCount(6)
  })

  test('SourceSummaryCard shows URL, format, bytes', async ({ page }) => {
    test.fixme(true, 'UI test requires backend job data — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      if (stage?.openTab) {
        stage.openTab({ type: 'ingestion_job', payload: { id: 'job_summary_test' } })
      }
    })
    await expect(page.getByTestId('ingestion-source-summary')).toBeVisible()
    // URL, format badge, and bytes should all be present
    const card = page.getByTestId('ingestion-source-summary')
    await expect(card.locator('text=/http|https/i')).toBeVisible()
    await expect(card.locator('text=/JSON|CSV|JSONL/i')).toBeVisible()
  })

  test('DDL preview visible when columns exist', async ({ page }) => {
    test.fixme(true, 'UI test requires backend job data — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      if (stage?.openTab) {
        stage.openTab({ type: 'ingestion_job', payload: { id: 'job_ddl_test' } })
      }
    })
    await expect(page.getByTestId('ingestion-ddl-preview')).toBeVisible()
    // DDL preview renders a <pre> with CREATE TABLE
    await expect(page.locator('ingestion-ddl-preview pre, [data-testid="ingestion-ddl-preview"] pre')).toContainText('CREATE TABLE')
  })

  test('PayloadPreviewTable populated from /payload-preview endpoint', async ({ page }) => {
    test.fixme(true, 'UI test requires backend job data — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage()
      if (stage?.openTab) {
        stage.openTab({ type: 'ingestion_job', payload: { id: 'job_preview_test' } })
      }
    })
    // PayloadPreviewTable renders a table with data rows
    const previewTable = page.locator('[data-testid="payload-preview-table"], table').first()
    await expect(previewTable).toBeVisible({ timeout: 5000 })
  })
})
