import { test, expect } from '@playwright/test'
import { adapterClient } from './fixtures/adapter-client'

test('check actual error for missing connectionId', async ({ request }) => {
  const c = adapterClient(request)
  const rpc = await c.mcpCall('datatalk_ui_exec', {
    object: 'workspace',
    action: 'open_er_inspector',
    params: { tables: ['orders'] },
  })
  console.log('RPC result:', JSON.stringify(rpc, null, 2))
  expect(rpc.error).toBeDefined()
  console.log('Error message:', rpc.error?.message)
})
