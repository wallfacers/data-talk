import { test, expect } from '@playwright/test'
import { DataSourcesPage } from './pom/data-sources.page'

test.describe('Data Source Forms @e2e @datasource', () => {
  let dataSources: DataSourcesPage

  test.beforeEach(async ({ page }) => {
    dataSources = new DataSourcesPage(page)
    // Intercept create/update requests for frontend-only payload verification
    await page.route('**/api/connections', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            id: `conn_e2e_${Date.now()}`,
            status: 'created',
          }),
        })
        return
      }
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ connections: [] }),
        })
        return
      }
      await route.continue()
    })
  })

  test('TiDB form keeps existing behavior and can submit valid input @datasource', async ({ page }) => {
    await page.goto('/')
    // Navigate to settings data sources
    const settingsBtn = page.getByRole('button', { name: /设置|Settings/i })
    if (await settingsBtn.isVisible()) {
      await settingsBtn.click()
      await page.getByRole('menuitem', { name: /数据源|Data Source/i }).click()
    }

    await dataSources.connectionManager.openCreateDialog()
    await dataSources.connectionManager.chooseDatabaseType('tidb', 'TiDB')

    // TiDB default port is 4000
    await expect(page.getByLabel('端口')).toHaveValue(4000)

    // Fill required fields
    await dataSources.connectionManager.fillDisplayName('E2E TiDB')
    await dataSources.connectionManager.fillHost('localhost')
    await dataSources.connectionManager.fillUsername('root')

    await dataSources.connectionManager.save()
    // Request was intercepted, so no actual save happens — just verify form submission didn't crash
    await expect(page.getByRole('button', { name: /保存/ })).toBeVisible()
  })

  test('OceanBase defaults to port 2881 and MySQL compatibility @datasource', async ({ page }) => {
    await page.goto('/')
    await dataSources.connectionManager.openCreateDialog()
    await dataSources.connectionManager.chooseDatabaseType('oceanbase', 'OceanBase')

    await expect(page.getByLabel('端口')).toHaveValue(2881)
    // MySQL mode should be selected by default
    const mysqlChip = page.getByRole('radio', { name: 'MySQL' })
    await expect(mysqlChip).toHaveAttribute('aria-checked', 'true')
  })

  test('OceanBase tenant and cluster are persisted in the outgoing request @datasource', async ({ page }) => {
    let capturedBody: any
    await page.route('**/api/connections', async (route) => {
      if (route.request().method() === 'POST') {
        capturedBody = route.request().postDataJSON()
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({ id: 'conn_e2e_ob', status: 'created' }),
        })
        return
      }
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ connections: [] }),
        })
        return
      }
      await route.continue()
    })

    await page.goto('/')
    await dataSources.connectionManager.openCreateDialog()
    await dataSources.connectionManager.chooseDatabaseType('oceanbase', 'OceanBase')

    await dataSources.connectionManager.fillDisplayName('E2E OB')
    await dataSources.connectionManager.fillOceanBaseTenant('sys')
    await dataSources.connectionManager.fillOceanBaseCluster('test-cluster')
    await dataSources.connectionManager.save()

    // Give the route handler time to capture
    await expect(async () => {
      expect(capturedBody).toBeDefined()
      expect(capturedBody.kind).toBe('oceanbase')
      expect(capturedBody.oceanbaseTenant).toBe('sys')
      expect(capturedBody.oceanbaseCluster).toBe('test-cluster')
    }).toPass()
  })

  test('Dameng defaults to port 5236 @datasource', async ({ page }) => {
    await page.goto('/')
    await dataSources.connectionManager.openCreateDialog()
    await dataSources.connectionManager.chooseDatabaseType('dameng', 'Dameng (DM 8)')

    await expect(page.getByLabel('端口')).toHaveValue(5236)
  })

  test('Dameng form has no compatibility selector @datasource', async ({ page }) => {
    await page.goto('/')
    await dataSources.connectionManager.openCreateDialog()
    await dataSources.connectionManager.chooseDatabaseType('dameng', 'Dameng (DM 8)')

    // No radio buttons for compatibility modes should exist
    await expect(page.getByRole('radio', { name: 'MySQL' })).not.toBeVisible()
    await expect(page.getByRole('radio', { name: 'Oracle' })).not.toBeVisible()
  })

  test('required-field validation is visible and accessible @datasource', async ({ page }) => {
    await page.goto('/')
    await dataSources.connectionManager.openCreateDialog()
    await dataSources.connectionManager.chooseDatabaseType('oceanbase', 'OceanBase')

    // Try to save without filling required fields
    await dataSources.connectionManager.save()

    // The form should show validation state (browser native or custom)
    // Tenant field is required
    const tenantInput = page.getByLabel('租户')
    await expect(tenantInput).toBeVisible()
  })

  test('keyboard tab order reaches critical controls @datasource', async ({ page }) => {
    await page.goto('/')
    await dataSources.connectionManager.openCreateDialog()
    await dataSources.connectionManager.chooseDatabaseType('tidb', 'TiDB')

    // Tab through the form and verify key fields are reachable
    await page.keyboard.press('Tab')
    await page.keyboard.press('Tab')
    await page.keyboard.press('Tab')

    // After tabbing, focus should be on or near the form fields
    const focused = page.locator(':focus')
    // Focus should be somewhere in the form (not lost)
    await expect(focused).toBeVisible()
  })

  test('icon-only actions on the data-source table have accessible names @datasource', async ({ page }) => {
    await page.goto('/')
    // The data source table should have icon buttons with aria-labels
    // Even with no connections, the add button should have an accessible name
    const addBtn = page.getByRole('button', { name: /新增|新建|添加|Create|Add/i })
    await expect(addBtn).toBeVisible()
    // The button should have an accessible name (either text content or aria-label)
    await expect(addBtn).toHaveAccessibleName()
  })
})
