import { test } from '@playwright/test'

test('check network for session', async ({ page }) => {
  const sessions: any[] = []
  page.on('request', req => {
    if (req.url().includes('/api/sessions') || req.url().includes('/send_message') || req.url().includes('/subscribe')) {
      console.log('REQUEST:', req.method(), req.url())
    }
  })
  page.on('response', res => {
    if (res.url().includes('/api/sessions') || res.url().includes('/send_message') || res.url().includes('/subscribe')) {
      console.log('RESPONSE:', res.status(), res.url())
      res.json().then(j => console.log('BODY:', JSON.stringify(j).slice(0, 200))).catch(() => {})
    }
  })
  
  await page.goto('/')
  await page.waitForSelector('textarea', { timeout: 15_000 })
  
  // Send a message
  const composer = page.locator('textarea')
  await composer.fill('hello')
  await page.keyboard.press('Enter')
  
  await page.waitForTimeout(8000)
})
