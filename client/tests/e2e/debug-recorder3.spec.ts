import { test, expect } from '@playwright/test'
import { ChatPanelPage } from './pom/chat-panel.page'
import { mountToolRecorder } from './fixtures/mcp-tool-recorder'

const MODEL = process.env.DATATALK_REAL_OPENCODE_MODEL

test('debug recorder with mount', async ({ page }) => {
  test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
  await page.goto('/')
  await page.waitForSelector('textarea', { timeout: 15_000 })

  const recorder = await mountToolRecorder(page)
  const chat = new ChatPanelPage(page)
  await chat.sendMessage('当前用的是哪个连接和数据库？')
  await chat.waitForAiResponse()

  // Wait for late tool rendering
  await page.waitForTimeout(3000)

  const all = await recorder.all()
  console.log('Recorder all:', JSON.stringify(all, null, 2))

  // Try matching exact and partial
  const exact = await recorder.callsFor('datatalk_get_data_context')
  console.log('Exact match count:', exact.length)

  expect(all.length).toBeGreaterThanOrEqual(1)
})
