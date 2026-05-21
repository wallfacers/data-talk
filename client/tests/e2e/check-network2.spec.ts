import { test } from '@playwright/test'

test('check channel response', async ({ page }) => {
  page.on('response', async res => {
    if (res.url().includes('/channel') || res.url().includes('/subscribe')) {
      console.log('RESPONSE:', res.status(), res.url())
      try {
        const text = await res.text()
        console.log('BODY:', text.slice(0, 300))
      } catch {}
    }
  })
  
  await page.goto('/')
  await page.waitForSelector('textarea', { timeout: 15_000 })
  
  const composer = page.locator('textarea')
  await composer.fill('hello')
  await page.keyboard.press('Enter')
  
  await page.waitForTimeout(8000)
})
