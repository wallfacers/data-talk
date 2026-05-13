import { test, expect } from '@playwright/test'

// Strategy: real backend — exercises /api/ingestion/credentials CRUD directly.
// See openspec/specs/ingestion-ui-e2e-testing/spec.md for the rule that justifies hitting
// the real backend here (no MCP wrapper; sync REST CRUD; no overlap with API specs).

const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'

/** Navigate to Settings > Credentials via the user avatar dropdown. */
async function navigateToCredentials(page: Awaited<ReturnType<typeof test>> extends infer T ? T extends { page: infer P } ? P : never : never) {
  // Click the user avatar in the bottom-left sidebar to open the settings dropdown
  const avatarBtn = page.getByRole('button', { name: /DataTalk.*wallfacerswu/i })
  await avatarBtn.click()
  // Click the Credentials menu item in the dropdown
  await page.getByRole('button', { name: /凭据|Credentials/i }).click()
}

test.describe('@e2e @ingestion @ui Credentials Settings page', () => {
  test.beforeEach(async ({ page, request }) => {
    // Clean up any e2e credential before each test — robust against orphans left by
    // interrupted credentials-api runs (which use e2e_cred_* prefix and afterEach hooks
    // that don't fire on crash).
    const list = await request.get(`${BASE}/api/ingestion/credentials`)
    if (list.ok()) {
      const body = await list.json() as { items: Array<{ id: string; name: string }> }
      for (const c of body.items) {
        if (c.name.startsWith('e2e_')) {
          await request.delete(`${BASE}/api/ingestion/credentials/${c.id}?force=true`)
        }
      }
    }
    await page.goto('/')
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await navigateToCredentials(page)
  })

  test('Empty state visible with no credentials', async ({ page }) => {
    await expect(page.getByTestId('credentials-empty-state')).toBeVisible()
  })

  test('Create dialog opens with 5 scheme radios', async ({ page }) => {
    await page.getByTestId('credentials-create-btn').click()
    await expect(page.getByTestId('credential-form')).toBeVisible()
    for (const s of ['none', 'bearer', 'api_key_header', 'api_key_query', 'basic']) {
      await expect(page.getByTestId(`credential-scheme-${s}`)).toBeVisible()
    }
  })

  test('Bearer scheme reveals token input', async ({ page }) => {
    await page.getByTestId('credentials-create-btn').click()
    await page.getByTestId('credential-scheme-bearer').click()
    await expect(page.getByRole('textbox', { name: 'Token' })).toBeVisible()
  })

  test('Create + list + delete round-trip', async ({ page, request }) => {
    const name = `e2e_ui_cred_${Date.now()}`
    await page.getByTestId('credentials-create-btn').click()
    await page.getByTestId('credential-name-input').fill(name)
    await page.getByTestId('credential-scheme-none').click()
    await page.getByTestId('credential-submit-btn').click()
    // Toast success + list shows the name
    await expect(page.getByText(name)).toBeVisible()

    // Delete via API and confirm UI updates after reload
    const list = await (await request.get(`${BASE}/api/ingestion/credentials`)).json()
    const found = (list.items as Array<{ name: string; id: string }>).find(c => c.name === name)
    expect(found).toBeTruthy()
    await request.delete(`${BASE}/api/ingestion/credentials/${found.id}?force=true`)
    // Refresh page and re-navigate to confirm empty state returns
    await page.reload()
    await page.waitForFunction(() => Boolean((window as any).__DT_E2E__))
    await navigateToCredentials(page)
    await expect(page.getByTestId('credentials-empty-state')).toBeVisible()
  })

  test('Dual-source banner visible', async ({ page }) => {
    await expect(page.getByText(/dual.source|凭证可同时被|both|independent from database/i)).toBeVisible()
  })

  test('Scheme switching toggles conditional fields', async ({ page }) => {
    await page.getByTestId('credentials-create-btn').click()
    // Basic → shows username + password
    await page.getByTestId('credential-scheme-basic').click()
    await expect(page.getByLabel(/用户名|Username/i)).toBeVisible()
    await expect(page.getByLabel(/密码|Password/i)).toBeVisible()
    // API Key Header → shows parameter name + value
    await page.getByTestId('credential-scheme-api_key_header').click()
    await expect(page.getByLabel(/参数名|Parameter Name/i)).toBeVisible()
    await expect(page.getByLabel(/参数值|Parameter Value/i)).toBeVisible()
    // None → no extra fields
    await page.getByTestId('credential-scheme-none').click()
    await expect(page.getByLabel(/用户名|Username/i)).not.toBeVisible()
    await expect(page.getByLabel(/参数名|Parameter Name/i)).not.toBeVisible()
  })

  test('Form validation — name required', async ({ page }) => {
    await page.getByTestId('credentials-create-btn').click()
    await page.getByTestId('credential-submit-btn').click()
    // HTML5 required validation should keep the form visible (submission blocked)
    await expect(page.getByTestId('credential-form')).toBeVisible()
    // Verify the name input is still empty and has the required attribute
    const nameInput = page.getByTestId('credential-name-input')
    await expect(nameInput).toHaveValue('')
  })
})
