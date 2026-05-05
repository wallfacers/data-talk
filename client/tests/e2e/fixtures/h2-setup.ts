import { readFileSync } from 'fs'
import { fileURLToPath } from 'url'
import { dirname, join } from 'path'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

const BACKEND_URL = process.env.DATATALK_BACKEND_URL ?? 'http://localhost:8080'

export interface TestDbSetup {
  connectionId: string
  cleanup: () => Promise<void>
}

async function apiFetch(path: string, init?: RequestInit): Promise<Response> {
  return fetch(`${BACKEND_URL}${path}`, init)
}

export async function setupTestDb(): Promise<TestDbSetup> {
  // Find an existing connection that's already tested and working
  const listResp = await apiFetch('/api/connections')
  const listBody = await listResp.json()
  const workingConn = (listBody.connections ?? []).find(
    (c: any) => c.lastTestStatus === 'ok'
  )

  if (!workingConn) {
    throw new Error('No working connection found (lastTestStatus !== ok)')
  }

  const connectionId = workingConn.id

  // Seed test data
  const seedPath = join(__dirname, 'test-seed.sql')
  const seedSql = readFileSync(seedPath, 'utf-8')

  const statements = seedSql
    .split(';')
    .map(s => s.trim())
    .filter(s => s.length > 0)

  for (const stmt of statements) {
    const execResp = await apiFetch('/api/sql/execute', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        connectionId,
        sql: stmt,
        source: 'ai',
        confirmed: true,
      }),
    })

    if (!execResp.ok && execResp.status !== 422 && execResp.status !== 404) {
      const body = await execResp.text()
      console.warn(`Seed SQL warning "${stmt.slice(0, 50)}...": ${execResp.status} ${body}`)
    }
  }

  return {
    connectionId,
    cleanup: async () => {
      // Reusing existing connection — no cleanup needed
    },
  }
}
