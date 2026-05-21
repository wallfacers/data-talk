import { test, expect } from '@playwright/test'
import { adapterClient } from './fixtures/adapter-client'

/**
 * Optional real database smoke tests.
 * Skipped when the required environment variable is absent.
 * Not part of default CI or local runs.
 */
test.describe('Real Database Smoke @realdb @datasource', () => {
  test.skip(!process.env.DATATALK_E2E_MYSQL_URL &&
            !process.env.DATATALK_E2E_TIDB_URL &&
            !process.env.DATATALK_E2E_OCEANBASE_URL &&
            !process.env.DATATALK_E2E_DAMENG_URL,
    'No real database environment variables set')

  test('create, test, SELECT 1, and clean up MySQL connection', async ({ request }) => {
    test.skip(!process.env.DATATALK_E2E_MYSQL_URL, 'DATATALK_E2E_MYSQL_URL not set')

    const client = adapterClient(request)
    const url = new URL(process.env.DATATALK_E2E_MYSQL_URL!)

    // Create connection
    const createResp = await client.createConnection({
      name: 'e2e_mysql_smoke',
      kind: 'mysql',
      host: url.hostname,
      port: url.port ? parseInt(url.port) : 3306,
      databaseName: url.pathname.slice(1) || 'test',
      username: url.username || 'root',
      password: url.password || '',
    })
    expect(createResp.ok()).toBe(true)
    const created = await createResp.json()
    const connId = created.id

    // Test connection
    const testResp = await client.testConnection(connId)
    expect(testResp.ok()).toBe(true)

    // Run SELECT 1
    const sqlResp = await client.executeSql({
      connectionId: connId,
      sql: 'SELECT 1 AS value',
      source: 'ai',
      confirmed: true,
    })
    expect(sqlResp.ok()).toBe(true)
    const sqlBody = await sqlResp.json()
    expect(sqlBody.rows).toBeDefined()

    // Clean up
    const deleteResp = await client.deleteConnection(connId)
    expect(deleteResp.ok()).toBe(true)
  })

  test('create, test, SELECT 1, and clean up TiDB connection', async ({ request }) => {
    test.skip(!process.env.DATATALK_E2E_TIDB_URL, 'DATATALK_E2E_TIDB_URL not set')

    const client = adapterClient(request)
    const url = new URL(process.env.DATATALK_E2E_TIDB_URL!)

    const createResp = await client.createConnection({
      name: 'e2e_tidb_smoke',
      kind: 'tidb',
      host: url.hostname,
      port: url.port ? parseInt(url.port) : 4000,
      databaseName: url.pathname.slice(1) || 'test',
      username: url.username || 'root',
      password: url.password || '',
    })
    expect(createResp.ok()).toBe(true)
    const created = await createResp.json()
    const connId = created.id

    const testResp = await client.testConnection(connId)
    expect(testResp.ok()).toBe(true)

    const sqlResp = await client.executeSql({
      connectionId: connId,
      sql: 'SELECT 1 AS value',
      source: 'ai',
      confirmed: true,
    })
    expect(sqlResp.ok()).toBe(true)

    const deleteResp = await client.deleteConnection(connId)
    expect(deleteResp.ok()).toBe(true)
  })

  test('create, test, SELECT 1, and clean up OceanBase connection', async ({ request }) => {
    test.skip(!process.env.DATATALK_E2E_OCEANBASE_URL, 'DATATALK_E2E_OCEANBASE_URL not set')

    const client = adapterClient(request)
    const url = new URL(process.env.DATATALK_E2E_OCEANBASE_URL!)

    const createResp = await client.createConnection({
      name: 'e2e_oceanbase_smoke',
      kind: 'oceanbase',
      host: url.hostname,
      port: url.port ? parseInt(url.port) : 2881,
      databaseName: url.pathname.slice(1) || null,
      username: url.username || 'root',
      password: url.password || '',
      compatibilityMode: 'mysql',
      oceanbaseTenant: url.username || 'sys',
    })
    expect(createResp.ok()).toBe(true)
    const created = await createResp.json()
    const connId = created.id

    const testResp = await client.testConnection(connId)
    expect(testResp.ok()).toBe(true)

    const sqlResp = await client.executeSql({
      connectionId: connId,
      sql: 'SELECT 1 AS value FROM DUAL',
      source: 'ai',
      confirmed: true,
    })
    expect(sqlResp.ok()).toBe(true)

    const deleteResp = await client.deleteConnection(connId)
    expect(deleteResp.ok()).toBe(true)
  })

  test('create, test, SELECT 1, and clean up Dameng connection', async ({ request }) => {
    test.skip(!process.env.DATATALK_E2E_DAMENG_URL, 'DATATALK_E2E_DAMENG_URL not set')

    const client = adapterClient(request)
    const url = new URL(process.env.DATATALK_E2E_DAMENG_URL!)

    const createResp = await client.createConnection({
      name: 'e2e_dameng_smoke',
      kind: 'dameng',
      host: url.hostname,
      port: url.port ? parseInt(url.port) : 5236,
      databaseName: url.pathname.slice(1) || null,
      username: url.username || 'SYSDBA',
      password: url.password || '',
    })
    expect(createResp.ok()).toBe(true)
    const created = await createResp.json()
    const connId = created.id

    const testResp = await client.testConnection(connId)
    expect(testResp.ok()).toBe(true)

    // Dameng uses SELECT 1 (no DUAL needed)
    const sqlResp = await client.executeSql({
      connectionId: connId,
      sql: 'SELECT 1 AS value',
      source: 'ai',
      confirmed: true,
    })
    expect(sqlResp.ok()).toBe(true)

    const deleteResp = await client.deleteConnection(connId)
    expect(deleteResp.ok()).toBe(true)
  })
})
