import { test, expect } from '@playwright/test'
import { ChatPanelPage } from './pom/chat-panel.page'
import { mountToolRecorder } from './fixtures/mcp-tool-recorder'

const MODEL = process.env.DATATALK_REAL_OPENCODE_MODEL

test('debug recorder', async ({ page }) => {
  test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
  await page.goto('/')
  await page.waitForSelector('textarea', { timeout: 15_000 })

  const recorder = await mountToolRecorder(page)
  const chat = new ChatPanelPage(page)
  await chat.sendMessage('当前用的是哪个连接和数据库？')
  await chat.waitForAiResponse()

  // Debug: print recorder state
  const all = await recorder.all()
  console.log('Recorder all:', JSON.stringify(all, null, 2))

  // Debug: scan buttons manually
  const buttons = await page.evaluate(() => {
    const btns = document.querySelectorAll('[data-component="session-turn"] button')
    return Array.from(btns).map((b) => ({
      text: b.textContent?.trim() ?? '',
      html: b.outerHTML.substring(0, 200),
    }))
  })
  console.log('Buttons:', JSON.stringify(buttons, null, 2))

  // Debug: check if data-component="session-turn" exists
  const turns = await page.evaluate(() => {
    const els = document.querySelectorAll('[data-component="session-turn"]')
    return els.length
  })
  console.log('Session turns count:', turns)

  expect(all.length).toBeGreaterThanOrEqual(1)
})
