import { request, APIRequestContext } from '@playwright/test'
import { readFileSync } from 'fs'
import { join } from 'path'

const BACKEND_URL = process.env.DATATALK_BACKEND_URL ?? 'http://localhost:8080'
const H2_CONNECTION_NAME = 'e2e-test-h2'

export interface H2TestSetup {
  connectionId: string
  apiContext: APIRequestContext
  cleanup: () => Promise<void>
}

export async function setupH2Connection(): Promise<H2TestSetup> {
  const apiContext = await request.newContext({ baseURL: BACKEND_URL })

  // Check if test connection already exists
  const listResp = await apiContext.get('/api/connections')
  const listBody = await listResp.json()
  const existing = (listBody.connections ?? []).find(
    (c: any) => c.name === H2_CONNECTION_NAME
  )

  let connectionId: string

  if (existing) {
    connectionId = existing.id
  } else {
    // Create H2 in-memory connection
    // H2 kind must match what the backend supports — check DATA_SOURCE_TYPE_COMPATIBILITY
    const createResp = await apiContext.post('/api/connections', {
      data: {
        name: H2_CONNECTION_NAME,
        kind: 'h2',
        host: 'mem',
        port: 0,
        databaseName: 'testdb;DB_CLOSE_DELAY=-1',
        username: 'sa',
        password: '',
        connectTimeout: 5000,
      },
    })

    if (createResp.status() !== 201) {
      const body = await createResp.text()
      throw new Error(`Failed to create H2 connection: ${createResp.status()} ${body}`)
    }

    const created = await createResp.json()
    connectionId = created.id
  }

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
        source: 'e2e-setup',
        confirmed: true,
        riskAck: 'LOW',
      },
    })

    if (execResp.status() !== 200) {
      const body = await execResp.text()
      throw new Error(`Failed to execute seed SQL "${stmt.slice(0, 50)}...": ${execResp.status()} ${body}`)
    }
  }

  return {
    connectionId,
    apiContext,
    cleanup: async () => {
      await apiContext.delete(`/api/connections/${connectionId}`).catch(() => {})
      await apiContext.dispose()
    },
  }
}
