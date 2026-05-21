import { test } from '@playwright/test'

test('check initial page load APIs', async ({ page }) => {
  page.on('request', req => {
    console.log('REQ:', req.method(), req.url().split('?')[0])
  })
  await page.goto('/')
  await page.waitForSelector('textarea', { timeout: 15_000 })
  await page.waitForTimeout(3000)
})
