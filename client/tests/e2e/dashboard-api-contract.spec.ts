/**
 * Dashboard API contract tests.
 *
 * Exercises the adapter's REST endpoints directly (no browser UI) to
 * assert status codes, response shapes, and error semantics for the
 * dashboard promote / get / patch surface.
 *
 * Run: npx playwright test tests/e2e/dashboard-api-contract.spec.ts
 */
import { test, expect } from '@playwright/test'
import { adapterClient } from './fixtures/adapter-client'
import { makeDashboardPayload, makeOversizedDashboardPayload } from './fixtures/dashboard-fixtures'

test.describe('@contract @dashboard Dashboard API Contracts', () => {
  // ── helpers ──────────────────────────────────────────────────────────────

  function readBody(res: { json: () => Promise<unknown>; status: () => number }) {
    return res.json() as Promise<Record<string, unknown>>
  }

  // ── promote valid dashboard -> 201 with id / version ─────────────────────

  test('@contract @dashboard promote valid dashboard -> 201 and returns dashboard id/version', async ({ request }) => {
    const client = adapterClient(request)
    const payload = makeDashboardPayload()
    const res = await client.dashboardPromote(payload)
    expect(res.status()).toBe(201)

    const body = await readBody(res)
    expect(body.id).toMatch(/^dash_/)
    expect(body.version).toBe(1)
  })

  // ── get promoted dashboard -> 200 and stable payload ─────────────────────

  test('@contract @dashboard get promoted dashboard -> 200 and stable payload', async ({ request }) => {
    const client = adapterClient(request)
    const payload = makeDashboardPayload({ title: 'Stable Payload Test' })
    const promoteRes = await client.dashboardPromote(payload)
    const { id } = await readBody(promoteRes) as { id: string }

    const getRes = await client.dashboardGet(id as string)
    expect(getRes.status()).toBe(200)

    const body = await readBody(getRes)
    expect(body.id).toBe(id)
    expect(body.title).toBe('Stable Payload Test')
    expect(body.schemaVersion).toBe(1)
    expect(body.version).toBe(1)
    expect(Array.isArray(body.widgets)).toBe(true)
    expect(body.widgets).toHaveLength(2)
  })

  // ── patch with valid baseVersion -> success and version increment ────────

  test('@contract @dashboard patch with valid baseVersion -> success and version increment', async ({ request }) => {
    const client = adapterClient(request)
    const payload = makeDashboardPayload()
    const promoteRes = await client.dashboardPromote(payload)
    const { id } = await readBody(promoteRes) as { id: string }

    const patchRes = await client.dashboardPatch(id as string, {
      baseVersion: 1,
      ops: [{ op: 'replace', path: '/title', value: 'Patched Title' }],
    })
    expect(patchRes.status()).toBe(200)

    const body = await readBody(patchRes)
    expect(body.version).toBe(2)

    // Verify persisted
    const getRes = await client.dashboardGet(id as string)
    const persisted = await readBody(getRes)
    expect(persisted.title).toBe('Patched Title')
    expect(persisted.version).toBe(2)
  })

  // ── patch with stale baseVersion -> 409 version conflict ─────────────────

  test('@contract @dashboard patch with stale baseVersion -> 409 version conflict', async ({ request }) => {
    const client = adapterClient(request)
    const payload = makeDashboardPayload()
    const promoteRes = await client.dashboardPromote(payload)
    const { id } = await readBody(promoteRes) as { id: string }

    // Dashboard is at version 1; send baseVersion=0 (stale)
    const patchRes = await client.dashboardPatch(id as string, {
      baseVersion: 0,
      ops: [{ op: 'replace', path: '/title', value: 'Stale Patch' }],
    })
    expect(patchRes.status()).toBe(409)

    const body = await readBody(patchRes)
    expect(body.code).toBe('version_conflict')
    expect(body.expected).toBe(0)
    expect(body.actual).toBe(1)
  })

  // ── get missing dashboard -> 404 ─────────────────────────────────────────

  test('@contract @dashboard get missing dashboard -> 404', async ({ request }) => {
    const client = adapterClient(request)
    const res = await client.dashboardGet('dash_nonexistent_9999')
    expect(res.status()).toBe(404)

    const body = await readBody(res)
    expect(body.code).toBe('not_found')
  })

  // ── promote structurally invalid dashboard -> 422 ────────────────────────

  test('@contract @dashboard promote structurally invalid dashboard -> 422 validation error', async ({ request }) => {
    const client = adapterClient(request)
    // Missing required fields: schemaVersion, id, widgets, layout, etc.
    const res = await client.dashboardPromote({ garbage: true })
    expect(res.status()).toBe(422)

    const body = await readBody(res)
    expect(body.code).toBe('validation_error')
    expect(Array.isArray(body.errors)).toBe(true)
    expect((body.errors as string[]).length).toBeGreaterThan(0)
  })

  // ── promote oversized dashboard -> 413 ───────────────────────────────────

  test('@contract @dashboard promote oversized dashboard -> 413 with maxSize detail', async ({ request }) => {
    const client = adapterClient(request)
    const payload = makeOversizedDashboardPayload()
    const res = await client.dashboardPromote(payload)
    expect(res.status()).toBe(413)

    const body = await readBody(res)
    expect(body.code).toBe('payload_too_large')
    expect(typeof body.maxSize).toBe('number')
    expect(body.maxSize).toBe(256 * 1024)
  })

  // ── promote missing dashboard body -> 400 ────────────────────────────────

  test('@contract @dashboard promote missing dashboard body -> 400 invalid_request', async ({ request }) => {
    const client = adapterClient(request)
    // Send an empty JSON object — no "dashboard" field
    const res = await client.dashboardPromote({})
    expect(res.status()).toBe(400)

    const body = await readBody(res)
    expect(body.code).toBe('invalid_request')
  })

  // ── patch empty operations -> 400 ────────────────────────────────────────

  test('@contract @dashboard patch empty operations -> 400 invalid_request', async ({ request }) => {
    const client = adapterClient(request)
    const payload = makeDashboardPayload()
    const promoteRes = await client.dashboardPromote(payload)
    const { id } = await readBody(promoteRes) as { id: string }

    const patchRes = await client.dashboardPatch(id as string, {
      baseVersion: 1,
      ops: [],
    })
    expect(patchRes.status()).toBe(400)

    const body = await readBody(patchRes)
    expect(body.code).toBe('invalid_request')
  })

  // ── patch missing baseVersion -> assert current adapter behavior ─────────

  test('@contract @dashboard patch missing baseVersion -> asserts actual adapter contract', async ({ request }) => {
    const client = adapterClient(request)
    const payload = makeDashboardPayload()
    const promoteRes = await client.dashboardPromote(payload)
    const { id } = await readBody(promoteRes) as { id: string }

    // baseVersion is a primitive int in DashboardPatchRequest; Jackson binds
    // missing to 0.  The promoted dashboard is at version 1, so the server
    // will see baseVersion=0 != currentVersion=1 and return 409 conflict.
    const patchRes = await client.dashboardPatch(id as string, {
      ops: [{ op: 'replace', path: '/title', value: 'No Version' }],
    })

    const status = patchRes.status()
    const body = await readBody(patchRes)

    // Document: missing baseVersion -> 409 (not 400), because the Java
    // record uses primitive int which defaults to 0.
    expect(status).toBe(409)
    expect(body.code).toBe('version_conflict')
    expect(body.expected).toBe(0)
    expect(body.actual).toBe(1)
  })
})
