import { test, expect } from '@playwright/test'

let lastPageId = ''

test.beforeEach(async ({ page }) => {
  const pageId = (page as any)._guid || Math.random().toString()
  console.log('beforeEach pageId:', pageId, 'same as last?', pageId === lastPageId)
  lastPageId = pageId
})

test('page reuse test 1', async ({ page }) => {
  await page.goto('/')
})

test('page reuse test 2', async ({ page }) => {
  await page.goto('/')
})
