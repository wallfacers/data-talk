import type { Page } from '@playwright/test'
import { expect } from '@playwright/test'
import { ConnectionManagerPage } from './connection-manager.page'

/**
 * High-level Page Object for the Data Sources settings page.
 * Delegates form interactions to ConnectionManagerPage while
 * providing page-level navigation and table assertions.
 */
export class DataSourcesPage {
  readonly page: Page
  readonly connectionManager: ConnectionManagerPage

  constructor(page: Page) {
    this.page = page
    this.connectionManager = new ConnectionManagerPage(page)
  }

  // ── Delegation aliases (preserve existing callers) ──

  get openCreateDialog() { return this.connectionManager.openCreateDialog.bind(this.connectionManager) }
  get chooseDatabaseType() { return this.connectionManager.chooseDatabaseType.bind(this.connectionManager) }
  get fillHost() { return this.connectionManager.fillHost.bind(this.connectionManager) }
  get fillPort() { return this.connectionManager.fillPort.bind(this.connectionManager) }
  get fillUsername() { return this.connectionManager.fillUsername.bind(this.connectionManager) }
  get fillPassword() { return this.connectionManager.fillPassword.bind(this.connectionManager) }
  get fillDatabaseName() { return this.connectionManager.fillDatabaseName.bind(this.connectionManager) }
  get fillDisplayName() { return this.connectionManager.fillDisplayName.bind(this.connectionManager) }
  get chooseOceanBaseMySqlMode() { return this.connectionManager.chooseOceanBaseMySqlMode.bind(this.connectionManager) }
  get fillOceanBaseTenant() { return this.connectionManager.fillOceanBaseTenant.bind(this.connectionManager) }
  get fillOceanBaseCluster() { return this.connectionManager.fillOceanBaseCluster.bind(this.connectionManager) }
  get assertDamengDefaults() { return this.connectionManager.assertDamengDefaults.bind(this.connectionManager) }
  get assertOceanBaseDefaults() { return this.connectionManager.assertOceanBaseDefaults.bind(this.connectionManager) }
  get save() { return this.connectionManager.save.bind(this.connectionManager) }
  get assertValidationMessage() { return this.connectionManager.assertValidationMessage.bind(this.connectionManager) }

  // ── Page-level navigation ──

  async goto(): Promise<void> {
    await this.page.goto('/settings/data-sources')
    await expect(this.page.getByRole('heading', { name: /数据源|Data Source/i })).toBeVisible()
  }

  async openSettingsFromMenu(): Promise<void> {
    await this.page.getByRole('button', { name: /设置|Settings/i }).click()
    await this.page.getByRole('menuitem', { name: /数据源|Data Source/i }).click()
    await expect(this.page.getByRole('heading', { name: /数据源|Data Source/i })).toBeVisible()
  }

  // ── Connection table ──

  connectionRow(name: string) {
    return this.page.getByRole('row', { name })
  }

  async getConnectionNames(): Promise<string[]> {
    const rows = this.page.locator('[role="row"], tbody tr')
    const count = await rows.count()
    const names: string[] = []
    for (let i = 0; i < count; i++) {
      const text = await rows.nth(i).textContent()
      if (text && text.trim()) names.push(text.trim())
    }
    return names
  }

  async deleteConnection(name: string): Promise<void> {
    const row = this.connectionRow(name)
    const deleteBtn = row.getByRole('button', { name: /删除|Delete/i })
    await expect(deleteBtn).toBeVisible()
    await deleteBtn.click()
  }

  async editConnection(name: string): Promise<void> {
    const row = this.connectionRow(name)
    const editBtn = row.getByRole('button', { name: /编辑|Edit/i })
    await expect(editBtn).toBeVisible()
    await editBtn.click()
  }
}
