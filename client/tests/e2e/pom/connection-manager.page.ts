import type { Page, Locator } from '@playwright/test'

export class ConnectionManagerPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  private get dataSourcePickerButton(): Locator {
    return this.page.locator('button').filter({ hasText: /选择数据源|dataSources\.select/i }).first()
      .or(this.page.locator('button').filter({ has: this.page.locator('svg') }).filter({ hasText: /./ }).first())
  }

  async openPicker(): Promise<void> {
    const btn = this.dataSourcePickerButton
    if (await btn.count() > 0) {
      await btn.click()
      await this.page.waitForTimeout(300)
    }
  }

  async getConnectionTitles(): Promise<string[]> {
    const items = this.page.locator('[role="dialog"] [role="radio"], [role="dialog"] button, [role="dialog"] li')
    const count = await items.count()
    const titles: string[] = []
    for (let i = 0; i < count; i++) {
      const text = await items.nth(i).textContent()
      if (text && text.trim()) titles.push(text.trim())
    }
    return titles
  }

  async selectConnection(name: string): Promise<void> {
    const dialog = this.page.locator('[role="dialog"]')
    const item = dialog.locator('button, [role="radio"], li').filter({ hasText: name }).first()
    if (await item.count() > 0) {
      await item.click()
      await this.page.waitForTimeout(300)
    }
  }

  async clickAddConnection(): Promise<void> {
    const dialog = this.page.locator('[role="dialog"]')
    const addBtn = dialog.locator('button').filter({ hasText: /新建|添加|add|create/i }).first()
    if (await addBtn.count() > 0) {
      await addBtn.click()
      await this.page.waitForTimeout(300)
    }
  }

  async fillNewConnection(opts: {
    name: string
    kind: string
    host?: string
    port?: number
    username?: string
    password?: string
    databaseName?: string
  }): Promise<void> {
    const dialog = this.page.locator('[role="dialog"]')

    const nameInput = dialog.locator('input[name="name"], input[placeholder*="名称"]').first()
    if (await nameInput.count() > 0) await nameInput.fill(opts.name)

    const kindSelect = dialog.locator('select[name="kind"]').first()
    if (await kindSelect.count() > 0) await kindSelect.selectOption(opts.kind)

    if (opts.host) {
      const hostInput = dialog.locator('input[name="host"]').first()
      if (await hostInput.count() > 0) await hostInput.fill(opts.host)
    }

    if (opts.port) {
      const portInput = dialog.locator('input[name="port"]').first()
      if (await portInput.count() > 0) await portInput.fill(String(opts.port))
    }

    if (opts.databaseName) {
      const dbInput = dialog.locator('input[name="databaseName"]').first()
      if (await dbInput.count() > 0) await dbInput.fill(opts.databaseName)
    }

    if (opts.username) {
      const userInput = dialog.locator('input[name="username"]').first()
      if (await userInput.count() > 0) await userInput.fill(opts.username)
    }

    if (opts.password) {
      const passInput = dialog.locator('input[name="password"]').first()
      if (await passInput.count() > 0) await passInput.fill(opts.password)
    }
  }

  async submitNewConnection(): Promise<void> {
    const dialog = this.page.locator('[role="dialog"]')
    const submit = dialog.locator('button[type="submit"]').first()
      .or(dialog.locator('button').filter({ hasText: /保存|确认|确定|Save|OK/i }).first())
    if (await submit.count() > 0) {
      await submit.click()
      await this.page.waitForTimeout(500)
    }
  }

  async clickTestConnection(name?: string): Promise<string | null> {
    if (name) {
      await this.openPicker()
      await this.selectConnection(name)
    }
    const dialog = this.page.locator('[role="dialog"]')
    const testBtn = dialog.locator('button').filter({ hasText: /测试|Test/i }).first()
    if (await testBtn.count() > 0) {
      await testBtn.click()
      await this.page.waitForTimeout(1_000)
    }
    const toast = this.page.locator('[role="status"]').first()
      .or(this.page.locator('text=/成功|失败|ok|error/i').first())
    if (await toast.count() > 0) {
      return (await toast.textContent()) ?? null
    }
    return null
  }

  async clickEditConnection(name: string): Promise<void> {
    await this.openPicker()
    const dialog = this.page.locator('[role="dialog"]')
    const row = dialog.locator('button, li, [role="radio"]').filter({ hasText: name }).first()
    const editBtn = row.locator('button').filter({ hasText: /编辑|Edit/i }).first()
      .or(dialog.locator('button').filter({ hasText: /编辑.*连接|编辑.*数据源/i }).first())
    if (await editBtn.count() > 0) {
      await editBtn.click()
      await this.page.waitForTimeout(300)
    }
  }
}
