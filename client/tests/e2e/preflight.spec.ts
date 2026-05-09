import { test } from '@playwright/test'
import { recordCheck, writePreflightReport } from './fixtures/preflight-report'

const ADAPTER_BASE_URL = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'

/**
 * Generate a short run-id from the current timestamp for the preflight report.
 */
function runId(): string {
  return `preflight-${Date.now()}`
}

test('adapter health is reachable @preflight @preflight-api', async ({ request }) => {
  const id = runId()

  try {
    const res = await request.get(`${ADAPTER_BASE_URL}/actuator/health`, { timeout: 10_000 })

    if (res.status() === 200) {
      const body = await res.json().catch(() => null)
      recordCheck({
        name: 'adapter_health',
        tag: '@preflight-api',
        status: 'pass',
        detail: `HTTP ${res.status()} from ${ADAPTER_BASE_URL}/actuator/health — ${JSON.stringify(body)}`,
      })
    } else {
      recordCheck({
        name: 'adapter_health',
        tag: '@preflight-api',
        status: 'fail',
        detail: `HTTP ${res.status()} from ${ADAPTER_BASE_URL}/actuator/health`,
      })
    }
  } catch (err: unknown) {
    // Fallback: try root endpoint
    try {
      const res2 = await request.get(`${ADAPTER_BASE_URL}/`, { timeout: 10_000 })
      if (res2.ok()) {
        recordCheck({
          name: 'adapter_health',
          tag: '@preflight-api',
          status: 'pass',
          detail: `HTTP ${res2.status()} from ${ADAPTER_BASE_URL}/ (fallback)`,
        })
      } else {
        recordCheck({
          name: 'adapter_health',
          tag: '@preflight-api',
          status: 'fail',
          detail: `Fallback root also returned ${res2.status()}`,
        })
      }
    } catch (err2: unknown) {
      recordCheck({
        name: 'adapter_health',
        tag: '@preflight-api',
        status: 'fail',
        detail: `Unreachable at ${ADAPTER_BASE_URL}: ${(err2 as Error).message}`,
      })
    }
  }

  const reportPath = writePreflightReport(id)
  console.log(`Preflight report written to: ${reportPath}`)
})

test('browser baseURL is reachable @preflight @preflight-e2e', async ({ page }) => {
  const id = runId()
  const baseURL = test.info().project.use.baseURL ?? 'http://localhost:1420'

  try {
    const res = await page.goto(baseURL, { waitUntil: 'domcontentloaded', timeout: 15_000 })

    if (res && res.ok()) {
      // Verify the body has visible content (not a blank/error page)
      const bodyText = await page.locator('body').innerText({ timeout: 5_000 }).catch(() => '')
      const hasContent = bodyText.trim().length > 0

      recordCheck({
        name: 'browser_baseurl',
        tag: '@preflight-e2e',
        status: hasContent ? 'pass' : 'fail',
        detail: hasContent
          ? `HTTP ${res.status()} from ${baseURL}, body has visible content`
          : `HTTP ${res.status()} from ${baseURL}, but body is empty`,
      })
    } else {
      recordCheck({
        name: 'browser_baseurl',
        tag: '@preflight-e2e',
        status: 'fail',
        detail: res ? `HTTP ${res.status()} from ${baseURL}` : `Navigation failed — no response`,
      })
    }
  } catch (err: unknown) {
    recordCheck({
      name: 'browser_baseurl',
      tag: '@preflight-e2e',
      status: 'fail',
      detail: `Unreachable at ${baseURL}: ${(err as Error).message}`,
    })
  }

  const reportPath = writePreflightReport(id)
  console.log(`Preflight report written to: ${reportPath}`)
})
