import { request, APIRequestContext } from '@playwright/test'
import { readFileSync } from 'fs'
import { fileURLToPath } from 'url'
import { dirname, join } from 'path'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

const BACKEND_URL = process.env.DATATALK_BACKEND_URL ?? 'http://localhost:8080'
const TEST_CONNECTION_NAME = 'e2e-test-db'
const TEST_CONNECTION_KIND = 'mysql'
const TEST_CONNECTION_HOST = 'localhost'
const TEST_CONNECTION_PORT = 3306
const TEST_CONNECTION_DB = 'test'

export interface H2TestSetup {
  connectionId: string
  apiContext: APIRequestContext
  cleanup: () => Promise<void>
}

export async function setupH2Connection(): Promise<H2TestSetup> {
  const apiContext = await request.newContext({ baseURL: BACKEND_URL })

  // Find an existing MySQL connection that's already tested and working
  const listResp = await apiContext.get('/api/connections')
  const listBody = await listResp.json()
  const workingConn = (listBody.connections ?? []).find(
    (c: any) => c.lastTestStatus === 'ok'
  )

  if (!workingConn) {
    throw new Error('No working MySQL connection found (lastTestStatus !== ok)')
  }

  const connectionId = workingConn.id

  // Seed test data
  const seedPath = join(__dirname, 'test-seed.sql')
  const seedSql = readFileSync(seedPath, 'utf-8')

  // Split by semicolons and execute each statement
  const statements = seedSql
    .split(';')
    .map(s => s.trim())
    .filter(s => s.length > 0)

  for (const stmt of statements) {
    const execResp = await apiContext.post('/api/sql/execute', {
      data: {
        connectionId,
        sql: stmt,
        source: 'ai',
        confirmed: true,
      },
    })

    if (execResp.status() !== 200 && execResp.status() !== 422 && execResp.status() !== 404) {
      const body = await execResp.text()
      console.warn(`Seed SQL warning "${stmt.slice(0, 50)}...": ${execResp.status()} ${body}`)
    }
  }

  return {
    connectionId,
    apiContext,
    cleanup: async () => {
      // Don't delete the connection — we're reusing an existing one
      await apiContext.dispose()
    },
  }
}
