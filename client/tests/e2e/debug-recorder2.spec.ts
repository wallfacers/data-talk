import { test, expect } from '@playwright/test'
import { ChatPanelPage } from './pom/chat-panel.page'

const MODEL = process.env.DATATALK_REAL_OPENCODE_MODEL

test('debug recorder scan', async ({ page }) => {
  test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')
  await page.goto('/')
  await page.waitForSelector('textarea', { timeout: 15_000 })

  const chat = new ChatPanelPage(page)
  await chat.sendMessage('当前用的是哪个连接和数据库？')
  await chat.waitForAiResponse()

  // Wait a bit for any late rendering
  await page.waitForTimeout(2000)

  // Directly evaluate the same scan logic
  const recorder = await page.evaluate(() => {
    const buttons = document.querySelectorAll('[data-component="session-turn"] button')
    const results: { text: string; starts: boolean }[] = []
    buttons.forEach((btn) => {
      const text = btn.textContent?.trim() ?? ''
      results.push({ text, starts: text.startsWith('datatalk_') })
    })
    return results
  })
  console.log('Direct scan:', JSON.stringify(recorder, null, 2))

  // Also check outerHTML of buttons that start with datatalk_
  const toolButtons = await page.evaluate(() => {
    const btns = document.querySelectorAll('[data-component="session-turn"] button')
    return Array.from(btns)
      .filter((b) => (b.textContent?.trim() ?? '').startsWith('datatalk_'))
      .map((b) => b.outerHTML.substring(0, 300))
  })
  console.log('Tool buttons HTML:', JSON.stringify(toolButtons, null, 2))

  expect(toolButtons.length).toBeGreaterThanOrEqual(1)
})
