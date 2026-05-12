import { test, expect } from '@playwright/test'
import * as fs from 'node:fs'
import * as path from 'node:path'
import { adapterClient } from './fixtures/adapter-client'

const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'

test.describe('@e2e @ingestion @preflight @skill Ingestion preflight', () => {
  test('backend reports healthy', async ({ request }) => {
    const res = await request.get(`${BASE}/actuator/health`)
    expect(res.status()).toBeLessThan(500)
  })

  test('confirm endpoint mounted (404 not 500)', async ({ request }) => {
    const res = await request.post(`${BASE}/api/ingestion/jobs/__nonexistent__/confirm`, { data: {} })
    expect([404, 409]).toContain(res.status())
  })

  test('data-ingestion skill bundle extracted to ~/.data-talk', () => {
    const home = process.env.HOME ?? '/root'
    const skillDir = path.join(home, '.data-talk', '.opencode', 'skills', 'data-ingestion')
    test.skip(!fs.existsSync(path.dirname(skillDir)), 'no .opencode/ — backend may not have started locally')
    expect(fs.existsSync(path.join(skillDir, 'SKILL.md'))).toBe(true)
    expect(fs.readdirSync(path.join(skillDir, 'recipes')).length).toBeGreaterThanOrEqual(5)
  })

  test('AGENTS.md classpath read via backend', () => {
    // No dedicated backend endpoint exists for reading AGENTS.md from classpath.
    // SKILL.md presence on disk is checked in the skill-bundle test above.
    test.fixme(true, 'No dedicated endpoint exists yet — SKILL.md presence is checked instead')
  })

  test('MCP tools/list registers all 6 ingestion tools', async ({ request }) => {
    const res = await request.post(`${BASE}/mcp`, {
      data: { jsonrpc: '2.0', id: 1, method: 'tools/list', params: {} },
    })
    expect(res.ok()).toBe(true)
    const body = await res.json()
    const names = (body.result?.tools ?? []).map((t: { name: string }) => t.name)
    expect(names).toEqual(expect.arrayContaining([
      'http_request', 'infer_ingestion_schema',
      'create_ingestion_table', 'ingest_payload',
      'get_ingestion_job', 'list_ingestion_jobs',
    ]))
  })
})
