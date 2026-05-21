import { test } from '@playwright/test'

test('check session id sources', async ({ page }) => {
  await page.goto('/')
  await page.waitForSelector('textarea', { timeout: 15_000 })
  
  // Check URL
  console.log('URL:', page.url())
  
  // Check localStorage
  const ls = await page.evaluate(() => {
    const items: Record<string, string> = {}
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key) items[key] = localStorage.getItem(key) ?? ''
    }
    return items
  })
  console.log('localStorage:', JSON.stringify(ls, null, 2))
  
  // Check window globals
  const globals = await page.evaluate(() => {
    const keys = Object.keys(window).filter(k => k.toLowerCase().includes('session') || k.toLowerCase().includes('sid'))
    return keys.slice(0, 10)
  })
  console.log('Window session-like globals:', globals)
})
