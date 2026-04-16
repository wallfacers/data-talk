import { test, expect } from '@playwright/test'

test('HERO → SPLIT + artifact appears', async ({ page }) => {
  await page.goto('http://localhost:5173/')

  // Assert HERO: centered composer, no split panels
  await expect(page.locator('#composer-slot textarea')).toBeVisible()
  await expect(page.getByText('AI 正在准备…')).toHaveCount(0)

  // Create a session via the sidebar (or mock-injected store)
  // For MVP test we assume a session is pre-seeded via a dev shortcut.

  await page.locator('#composer-slot textarea').fill('show me the users table')
  await page.keyboard.press('Enter')

  // Assert SPLIT: composer slid left, timeline chip present
  await expect(page.getByRole('button', { name: /表/ })).toBeVisible({ timeout: 5000 })
  await expect(page.getByRole('table')).toBeVisible()
})
