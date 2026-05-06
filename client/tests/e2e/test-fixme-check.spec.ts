import { test, expect } from '@playwright/test'
import { adapterClient } from './fixtures/adapter-client'

test('contract: open_er_inspector missing connectionId errors', async ({ request }) => {
  const c = adapterClient(request)
  const rpc = await c.mcpCall('datatalk_ui_exec', {
    object: 'workspace',
    action: 'open_er_inspector',
    params: { tables: ['orders'] },
  })
  expect(rpc.error).toBeDefined()
})
