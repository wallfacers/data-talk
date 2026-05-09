import type { Page, Locator } from '@playwright/test'
import { expect } from '@playwright/test'

export class ConnectionManagerPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  // ── Dialog lifecycle ──

  async openCreateDialog(): Promise<void> {
    await this.page.getByRole('button', { name: /新增|新建|添加|Create|Add/i }).click()
    await expect(this.page.getByRole('dialog')).toBeVisible()
  }

  async clickAddConnection(): Promise<void> {
    const dialog = this.page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    const addBtn = dialog.getByRole('button', { name: /新建|添加|add|create/i })
    await addBtn.click()
  }

  // ── Database type selection ──

  async chooseDatabaseType(type: 'mysql' | 'postgresql' | 'tidb' | 'oceanbase' | 'dameng'): Promise<void> {
    const typeCombobox = this.page.getByRole('combobox', { name: '类型' })
    await typeCombobox.click()
    const option = this.page.getByRole('option', { name: new RegExp(type, 'i') })
    await option.click()
  }

  // ── Form fields ──

  fillHost(host: string): Promise<void> {
    return this.page.getByLabel('主机').fill(host)
  }

  fillPort(port: string): Promise<void> {
    return this.page.getByLabel('端口').fill(port)
  }

  fillUsername(username: string): Promise<void> {
    return this.page.getByLabel('用户名').fill(username)
  }

  fillPassword(password: string): Promise<void> {
    return this.page.getByLabel('密码').fill(password)
  }

  fillDatabaseName(databaseName: string): Promise<void> {
    return this.page.getByLabel(/数据库/).fill(databaseName)
  }

  fillDisplayName(displayName: string): Promise<void> {
    return this.page.getByLabel(/名称|DisplayName/).fill(displayName)
  }

  // ── OceanBase specific ──

  async chooseOceanBaseMySqlMode(): Promise<void> {
    const modeSelector = this.page.getByRole('combobox', { name: /兼容模式|compatibility/i })
    if (await modeSelector.isVisible()) {
      await modeSelector.click()
      await this.page.getByRole('option', { name: /MySQL|mysql/i }).click()
    }
  }

  fillOceanBaseTenant(tenant: string): Promise<void> {
    return this.page.getByLabel(/租户|tenant/i).fill(tenant)
  }

  fillOceanBaseCluster(cluster: string): Promise<void> {
    return this.page.getByLabel(/集群|cluster/i).fill(cluster)
  }

  // ── Dameng defaults assertion ──

  async assertDamengDefaults(): Promise<void> {
    const portInput = this.page.getByLabel('端口')
    await expect(portInput).toHaveValue('5236')
    // Dameng has no compatibility-mode selector
    await expect(this.page.getByRole('combobox', { name: /兼容模式|compatibility/i })).not.toBeVisible()
  }

  // ── OceanBase defaults assertion ──

  async assertOceanBaseDefaults(): Promise<void> {
    const portInput = this.page.getByLabel('端口')
    await expect(portInput).toHaveValue('2881')
    // OceanBase shows MySQL compatibility mode by default
    const modeSelector = this.page.getByRole('combobox', { name: /兼容模式|compatibility/i })
    if (await modeSelector.isVisible()) {
      await expect(modeSelector).toHaveText(/MySQL/i)
    }
  }

  // ── Submit / save ──

  async save(): Promise<void> {
    const submitBtn = this.page.getByRole('button', { name: /保存|确认|确定|Save|OK/i })
    await submitBtn.click()
  }

  // ── Validation ──

  async assertValidationMessage(messageKeyOrText: string): Promise<void> {
    // Try role=status first (toast/alert), then fallback to any visible text
    const status = this.page.getByRole('status')
    if (await status.isVisible()) {
      await expect(status).toContainText(messageKeyOrText)
    } else {
      await expect(this.page.getByText(messageKeyOrText)).toBeVisible()
    }
  }

  // ── Connection picker (dialog) ──

  async openPicker(): Promise<void> {
    const btn = this.page.locator('button').filter({ hasText: /选择数据源|dataSources\.select/i }).first()
      .or(this.page.locator('button').filter({ has: this.page.locator('svg') }).filter({ hasText: /./ }).first())
    await btn.click()
    await expect(this.page.getByRole('dialog')).toBeVisible()
  }

  async getConnectionTitles(): Promise<string[]> {
    const items = this.page.getByRole('dialog').locator('button, [role="radio"], li')
    const count = await items.count()
    const titles: string[] = []
    for (let i = 0; i < count; i++) {
      const text = await items.nth(i).textContent()
      if (text && text.trim()) titles.push(text.trim())
    }
    return titles
  }

  async selectConnection(name: string): Promise<void> {
    const dialog = this.page.getByRole('dialog')
    const item = dialog.locator('button, [role="radio"], li').filter({ hasText: name }).first()
    await expect(item).toBeVisible()
    await item.click()
  }

  // ── Fill connection form (legacy compatibility) ──

  async fillNewConnection(opts: {
    name: string
    kind: string
    host?: string
    port?: number
    username?: string
    password?: string
    databaseName?: string
  }): Promise<void> {
    const dialog = this.page.getByRole('dialog')

    const nameInput = dialog.locator('input[name="name"], input[placeholder*="名称"]').first()
    await expect(nameInput).toBeVisible()
    await nameInput.fill(opts.name)

    const kindSelect = dialog.locator('select[name="kind"]').first()
    if (await kindSelect.isVisible()) await kindSelect.selectOption(opts.kind)

    if (opts.host) {
      const hostInput = dialog.locator('input[name="host"]').first()
      if (await hostInput.isVisible()) await hostInput.fill(opts.host)
    }

    if (opts.port) {
      const portInput = dialog.locator('input[name="port"]').first()
      if (await portInput.isVisible()) await portInput.fill(String(opts.port))
    }

    if (opts.databaseName) {
      const dbInput = dialog.locator('input[name="databaseName"]').first()
      if (await dbInput.isVisible()) await dbInput.fill(opts.databaseName)
    }

    if (opts.username) {
      const userInput = dialog.locator('input[name="username"]').first()
      if (await userInput.isVisible()) await userInput.fill(opts.username)
    }

    if (opts.password) {
      const passInput = dialog.locator('input[name="password"]').first()
      if (await passInput.isVisible()) await passInput.fill(opts.password)
    }
  }

  async submitNewConnection(): Promise<void> {
    const submit = this.page.getByRole('dialog').getByRole('button', { name: /保存|确认|确定|Save|OK/i })
    await submit.click()
  }

  async clickTestConnection(name?: string): Promise<string | null> {
    if (name) {
      await this.openPicker()
      await this.selectConnection(name)
    }
    const testBtn = this.page.getByRole('dialog').getByRole('button', { name: /测试|Test/i })
    await expect(testBtn).toBeVisible()
    await testBtn.click()
    const toast = this.page.getByRole('status').first()
    if (await toast.isVisible()) {
      return (await toast.textContent()) ?? null
    }
    return null
  }

  async clickEditConnection(name: string): Promise<void> {
    await this.openPicker()
    const dialog = this.page.getByRole('dialog')
    const row = dialog.locator('button, li, [role="radio"]').filter({ hasText: name }).first()
    await expect(row).toBeVisible()
    const editBtn = row.getByRole('button', { name: /编辑|Edit/i })
    if (await editBtn.isVisible()) {
      await editBtn.click()
    } else {
      // Fallback: look for edit button in dialog
      const fallback = dialog.getByRole('button', { name: new RegExp(`编辑.*${name}|编辑.*连接|编辑.*数据源`) })
      await expect(fallback).toBeVisible()
      await fallback.click()
    }
  }
}
