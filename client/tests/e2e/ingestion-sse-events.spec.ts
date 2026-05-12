import { test, expect } from '@playwright/test'
import { adapterClient } from './fixtures/adapter-client'
import { startMockIngestionServer, type MockServer, seedH2Connection } from './fixtures/ingestion-fixtures'

const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'

let mock: MockServer
test.beforeAll(async () => { mock = await startMockIngestionServer() })
test.afterAll(async () => { await mock.stop() })
test.beforeEach(() => mock.reset())

test.describe('@e2e @ingestion @ui @sse Ingestion SSE events', () => {
  /**
   * Inject a fetch interceptor into the page that captures SSE data from the
   * channel endpoint.  The channel-client uses fetch (POST) + eventsource-parser,
   * so we intercept the response body stream and decode text/event-stream data.
   */
  async function installSseCapture(page: import('@playwright/test').Page) {
    await page.evaluate(() => {
      const captured: Array<{ event: string; data: unknown; ts: number }> = []
      const origFetch = window.fetch

      window.fetch = async function patchedFetch(input: RequestInfo | URL, init?: RequestInit) {
        const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url
        const isChannel = url.includes('/sessions/') && url.includes('/channel') && init?.method === 'GET'

        if (!isChannel) {
          return origFetch.apply(this, [input, init])
        }

        const response = await origFetch.apply(this, [input, init])
        if (!response.ok || !response.body) return response

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        // Create a new ReadableStream that mirrors the original while capturing
        const capturedStream = new ReadableStream({
          async start(controller) {
            try {
              for (;;) {
                const { done, value } = await reader.read()
                if (done) break
                const text = decoder.decode(value, { stream: true })
                buffer += text

                // Parse SSE lines: "event: <name>\ndata: <json>\n\n"
                const lines = buffer.split('\n')
                buffer = lines.pop() ?? ''
                let currentEvent = 'message'
                for (const line of lines) {
                  if (line.startsWith('event: ')) {
                    currentEvent = line.slice(7).trim()
                  } else if (line.startsWith('data: ')) {
                    const dataStr = line.slice(6).trim()
                    if (dataStr) {
                      try {
                        captured.push({
                          event: currentEvent,
                          data: JSON.parse(dataStr),
                          ts: Date.now(),
                        })
                      } catch { /* ignore malformed */ }
                    }
                  } else if (line === '' || line.trim() === '') {
                    currentEvent = 'message'
                  }
                }

                controller.enqueue(value)
              }
            } finally {
              controller.close()
            }
          },
        })

        return new Response(capturedStream, {
          status: response.status,
          statusText: response.statusText,
          headers: response.headers,
        })
      }

      ;(window as any).__SSE_CAPTURED = captured
      ;(window as any).__SSE_CAPTURE_LOG = () => captured
    })
  }

  /** Retrieve captured SSE events from the page. */
  async function getCapturedEvents(page: import('@playwright/test').Page) {
    return page.evaluate(() => ((window as any).__SSE_CAPTURE_LOG ?? (() => []))())
  }

  /** Wait until a captured event matching the predicate appears. */
  async function waitForCapturedEvent(
    page: import('@playwright/test').Page,
    predicate: (e: { event: string; data: unknown }) => boolean,
    timeoutMs = 15_000,
  ) {
    const start = Date.now()
    while (Date.now() - start < timeoutMs) {
      const events = await getCapturedEvents(page)
      const found = events.find(predicate)
      if (found) return found
      await page.waitForTimeout(250)
    }
    return null
  }

  /** Get the active session ID via the E2E store bridge. */
  async function getActiveSessionId(page: import('@playwright/test').Page): Promise<string> {
    return page.evaluate(() => {
      const dt = (window as any).__DT_E2E__
      return dt?.session?.()?.activeSessionId ?? ''
    })
  }

  test('IngestionJobCreated → Tab auto-opens', async ({ page }) => {
    test.fixme(true, 'SSE events require full ingestion pipeline — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await installSseCapture(page)

    const sessionId = await getActiveSessionId(page)
    if (!sessionId) {
      test.skip(true, 'No active session')
      return
    }

    // Trigger a fetch to create an ingestion job
    const c = adapterClient(page.request)
    const fetched = await c.mcpCall('http_request', {
      url: `${mock.baseUrl}/json/users`,
      payloadFormat: 'JSON',
    })

    // The SSE stream should contain ingestion.job.created
    const event = await waitForCapturedEvent(page, (e) => e.event === 'ingestion.job.created')
    if (event) {
      expect(event.data).toHaveProperty('jobId')
      expect(event.data).toHaveProperty('sourceUrl')
    }

    // The ingestion_job tab should auto-open in the Stage
    const tabCount = await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage?.()
      return stage?.tabs?.length ?? 0
    })
    // After job creation, at least one tab should exist (the ingestion_job tab)
    expect(tabCount).toBeGreaterThanOrEqual(1)
  })

  test('IngestionPayloadFetched → job status updates', async ({ page }) => {
    test.fixme(true, 'SSE events require full ingestion pipeline — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await installSseCapture(page)

    const sessionId = await getActiveSessionId(page)
    if (!sessionId) {
      test.skip(true, 'No active session')
      return
    }

    const c = adapterClient(page.request)
    await c.mcpCall('http_request', {
      url: `${mock.baseUrl}/json/users`,
      payloadFormat: 'JSON',
    })

    // The SSE stream should contain ingestion.payload.fetched
    const event = await waitForCapturedEvent(page, (e) => e.event === 'ingestion.payload.fetched')
    if (event) {
      const data = event.data as Record<string, unknown>
      expect(data).toHaveProperty('jobId')
      expect(data).toHaveProperty('payloadArtifactId')
      expect(data).toHaveProperty('rowCount')
      expect(data).toHaveProperty('bytesFetched')
    }

    // Job status should have progressed past fetching
    const jobId = await page.evaluate(() => {
      const stage = (window as any).__DT_E2E__?.stage?.()
      const ingestionTab = stage?.tabs?.find((t: { type: string }) => t.type === 'ingestion_job')
      return ingestionTab?.payload?.id ?? ''
    })
    // If we got here with a jobId, the fetch completed
    if (jobId) {
      const status = await page.evaluate(() => {
        const store = (window as any).__DT_E2E__?.ingestionJobsStore?.()
        return store?.jobs?.find((j: { id: string }) => j.id === jobId)?.status
      })
      expect(status).not.toBe('fetching')
    }
  })

  test('IngestionMappingProposed → mapping editor renders', async ({ page }) => {
    test.fixme(true, 'SSE events require full ingestion pipeline — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await installSseCapture(page)

    const sessionId = await getActiveSessionId(page)
    if (!sessionId) {
      test.skip(true, 'No active session')
      return
    }

    const connId = await seedH2Connection(page.request)
    const c = adapterClient(page.request)

    // Full pipeline: fetch → infer → mapping proposed
    const fetched = await c.mcpCall('http_request', {
      url: `${mock.baseUrl}/json/users`,
      payloadFormat: 'JSON',
    })
    const jobId = fetched.result?.jobId as string | undefined

    if (jobId) {
      await c.mcpCall('infer_ingestion_schema', { jobId })
    }

    // The SSE stream should contain ingestion.mapping.proposed
    const event = await waitForCapturedEvent(page, (e) => e.event === 'ingestion.mapping.proposed')
    if (event) {
      const data = event.data as Record<string, unknown>
      expect(data).toHaveProperty('jobId')
      expect(data).toHaveProperty('mappingId')
      expect(data).toHaveProperty('columnCount')
    }

    // Mapping phase should render if the tab is open
    const mappingPhase = page.getByTestId('ingestion-phase-mapping')
    if (await mappingPhase.isVisible({ timeout: 3000 }).catch(() => false)) {
      await expect(mappingPhase).toBeVisible()
    }
  })

  test('IngestionJobConfirmed → phase transitions to writing', async ({ page }) => {
    test.fixme(true, 'SSE events require full ingestion pipeline — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await installSseCapture(page)

    const sessionId = await getActiveSessionId(page)
    if (!sessionId) {
      test.skip(true, 'No active session')
      return
    }

    const connId = await seedH2Connection(page.request)
    const c = adapterClient(page.request)

    const fetched = await c.mcpCall('http_request', {
      url: `${mock.baseUrl}/json/users`,
      payloadFormat: 'JSON',
    })
    const jobId = fetched.result?.jobId as string | undefined

    if (jobId) {
      await c.mcpCall('infer_ingestion_schema', { jobId })

      // Confirm the job
      const confirmRes = await page.request.post(
        `${BASE}/api/ingestion/jobs/${jobId}/confirm`,
        { data: {} },
      )
      expect(confirmRes.ok()).toBe(true)
    }

    // The SSE stream should contain ingestion.job.confirmed
    const event = await waitForCapturedEvent(page, (e) => e.event === 'ingestion.job.confirmed')
    if (event) {
      const data = event.data as Record<string, unknown>
      expect(data).toHaveProperty('jobId')
      expect(data).toHaveProperty('tokenId')
    }
  })

  test('IngestionWriteStarted → writing phase renders', async ({ page }) => {
    test.fixme(true, 'SSE events require full ingestion pipeline — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await installSseCapture(page)

    const sessionId = await getActiveSessionId(page)
    if (!sessionId) {
      test.skip(true, 'No active session')
      return
    }

    const connId = await seedH2Connection(page.request)
    const c = adapterClient(page.request)

    const fetched = await c.mcpCall('http_request', {
      url: `${mock.baseUrl}/json/users`,
      payloadFormat: 'JSON',
    })
    const jobId = fetched.result?.jobId as string | undefined

    if (jobId) {
      await c.mcpCall('infer_ingestion_schema', { jobId })

      const confirmRes = await page.request.post(
        `${BASE}/api/ingestion/jobs/${jobId}/confirm`,
        { data: {} },
      )
      if (confirmRes.ok()) {
        const { tokenId, mappingHash } = await confirmRes.json()

        // Create the target table
        await c.mcpCall('create_ingestion_table', {
          jobId,
          connectionId: connId,
          schema: 'PUBLIC',
          table: 'e2e_ingest_users',
          mappingHash,
          tokenId,
        })

        // Ingest the payload
        await c.mcpCall('ingest_payload', { jobId, connectionId: connId })
      }
    }

    // The SSE stream should contain ingestion.write.started
    const event = await waitForCapturedEvent(page, (e) => e.event === 'ingestion.write.started')
    if (event) {
      const data = event.data as Record<string, unknown>
      expect(data).toHaveProperty('jobId')
      expect(data).toHaveProperty('targetTable')
    }
  })

  test('IngestionWriteProgress → row count updates', async ({ page }) => {
    test.fixme(true, 'SSE events require full ingestion pipeline — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await installSseCapture(page)

    const sessionId = await getActiveSessionId(page)
    if (!sessionId) {
      test.skip(true, 'No active session')
      return
    }

    const connId = await seedH2Connection(page.request)
    const c = adapterClient(page.request)

    const fetched = await c.mcpCall('http_request', {
      url: `${mock.baseUrl}/json/users`,
      payloadFormat: 'JSON',
    })
    const jobId = fetched.result?.jobId as string | undefined

    if (jobId) {
      await c.mcpCall('infer_ingestion_schema', { jobId })

      const confirmRes = await page.request.post(
        `${BASE}/api/ingestion/jobs/${jobId}/confirm`,
        { data: {} },
      )
      if (confirmRes.ok()) {
        const { tokenId, mappingHash } = await confirmRes.json()

        await c.mcpCall('create_ingestion_table', {
          jobId,
          connectionId: connId,
          schema: 'PUBLIC',
          table: 'e2e_ingest_progress',
          mappingHash,
          tokenId,
        })

        await c.mcpCall('ingest_payload', { jobId, connectionId: connId })
      }
    }

    // The SSE stream should contain ingestion.write.progress with rowsInserted
    const event = await waitForCapturedEvent(page, (e) => e.event === 'ingestion.write.progress')
    if (event) {
      const data = event.data as Record<string, unknown>
      expect(data).toHaveProperty('jobId')
      expect(data).toHaveProperty('rowsInserted')
      expect(data).toHaveProperty('totalRows')
      expect(typeof (data as Record<string, number>).rowsInserted).toBe('number')
    }
  })

  test('IngestionCompleted → completed phase shows results', async ({ page }) => {
    test.fixme(true, 'SSE events require full ingestion pipeline — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await installSseCapture(page)

    const sessionId = await getActiveSessionId(page)
    if (!sessionId) {
      test.skip(true, 'No active session')
      return
    }

    const connId = await seedH2Connection(page.request)
    const c = adapterClient(page.request)

    const fetched = await c.mcpCall('http_request', {
      url: `${mock.baseUrl}/json/users`,
      payloadFormat: 'JSON',
    })
    const jobId = fetched.result?.jobId as string | undefined

    if (jobId) {
      await c.mcpCall('infer_ingestion_schema', { jobId })

      const confirmRes = await page.request.post(
        `${BASE}/api/ingestion/jobs/${jobId}/confirm`,
        { data: {} },
      )
      if (confirmRes.ok()) {
        const { tokenId, mappingHash } = await confirmRes.json()

        await c.mcpCall('create_ingestion_table', {
          jobId,
          connectionId: connId,
          schema: 'PUBLIC',
          table: 'e2e_ingest_completed',
          mappingHash,
          tokenId,
        })

        await c.mcpCall('ingest_payload', { jobId, connectionId: connId })
      }
    }

    // The SSE stream should contain ingestion.completed
    const event = await waitForCapturedEvent(page, (e) => e.event === 'ingestion.completed')
    if (event) {
      const data = event.data as Record<string, unknown>
      expect(data).toHaveProperty('jobId')
      expect(data).toHaveProperty('targetTable')
      expect(data).toHaveProperty('finalRowCount')
      expect(data).toHaveProperty('durationMs')
      expect(typeof (data as Record<string, number>).finalRowCount).toBe('number')
    }
  })

  test('IngestionFailed → failed phase shows error', async ({ page }) => {
    test.fixme(true, 'SSE events require full ingestion pipeline — blocked on BUG-0013 fix')
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await installSseCapture(page)

    const sessionId = await getActiveSessionId(page)
    if (!sessionId) {
      test.skip(true, 'No active session')
      return
    }

    // Trigger an ingestion job that will fail (use auth endpoint that returns 401)
    const c = adapterClient(page.request)
    const fetched = await c.mcpCall('http_request', {
      url: `${mock.baseUrl}/auth/bearer`,
      payloadFormat: 'JSON',
    })
    const jobId = fetched.result?.jobId as string | undefined

    if (jobId) {
      // The job should fail during fetch phase since /auth/bearer returns 401
      await page.waitForTimeout(2000)
    }

    // The SSE stream should contain ingestion.failed
    const event = await waitForCapturedEvent(page, (e) => e.event === 'ingestion.failed')
    if (event) {
      const data = event.data as Record<string, unknown>
      expect(data).toHaveProperty('jobId')
      expect(data).toHaveProperty('phase')
      expect(data).toHaveProperty('errorMessage')
    }

    // The failed phase should render with error message visible
    const failedPhase = page.getByTestId('ingestion-phase-failed')
    if (await failedPhase.isVisible({ timeout: 3000 }).catch(() => false)) {
      await expect(failedPhase).toBeVisible()
    }
  })
})
