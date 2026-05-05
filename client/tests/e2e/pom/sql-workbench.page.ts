import { Page, Locator, expect } from '@playwright/test'

export class SqlWorkbenchPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  // Monaco Editor helpers
  async waitForMonacoReady(timeout = 20_000): Promise<void> {
    // Monaco may lazy-load; wait longer for the container
    const editorContainer = this.page.locator('.monaco-editor').first()
    await expect(editorContainer).toBeVisible({ timeout: 15_000 })

    // Wait for window.monaco
    await this.page.waitForFunction(
      () => typeof (window as any).monaco !== 'undefined',
      null,
      { timeout: 10_000, polling: 500 }
    )

    // Wait for model ready
    await this.page.waitForFunction(
      () => {
        const m = (window as any).monaco
        return m?.editor?.getModels && m.editor.getModels().length > 0
      },
      null,
      { timeout: 5_000, polling: 200 }
    )
  }

  async setSql(text: string): Promise<void> {
    try {
      await this.waitForMonacoReady()
    } catch {
      throw new Error('Monaco editor not available')
    }
    await this.page.evaluate((sql) => {
      const models = (window as any).monaco?.editor?.getModels()
      if (models && models.length > 0) {
        models[0].setValue(sql)
      }
    }, text)
  }

  async getSql(): Promise<string> {
    await this.waitForMonacoReady()
    return this.page.evaluate(() => {
      const models = (window as any).monaco?.editor?.getModels()
      return models?.[0]?.getValue() ?? ''
    })
  }

  // Toolbar actions
  async pressRun(): Promise<void> {
    await this.page.keyboard.press('Control+Enter')
  }

  async clickRunButton(): Promise<void> {
    const btn = this.page.locator('[data-testid="sql-run-button"]').or(this.page.locator('button:has-text("运行")'))
    await btn.click()
  }

  async clickFormatButton(): Promise<void> {
    const btn = this.page.locator('[data-testid="sql-format-button"]').or(this.page.locator('button:has-text("格式化")'))
    await btn.click()
  }

  // Context selectors
  async toggleUseSessionContext(): Promise<void> {
    const sw = this.page.locator('[data-testid="use-session-context-switch"]')
      .or(this.page.locator('label:has-text("固定 session 上下文") + button'))
    await sw.click()
  }

  async setConnection(value: string): Promise<void> {
    const select = this.page.locator('[data-testid="sql-connection-select"]').or(this.page.locator('text=连接').locator('..').locator('button'))
    await select.click()
    await this.page.locator(`[data-testid="select-item"]:has-text("${value}")`).or(this.page.locator(`text="${value}"`)).first().click()
  }

  async setDatabase(value: string): Promise<void> {
    const select = this.page.locator('[data-testid="sql-database-select"]')
    await select.click()
    await this.page.locator(`[data-testid="select-item"]:has-text("${value}")`).or(this.page.locator(`text="${value}"`)).first().click()
  }

  async setSchema(value: string): Promise<void> {
    const select = this.page.locator('[data-testid="sql-schema-select"]')
    await select.click()
    await this.page.locator(`[data-testid="select-item"]:has-text("${value}")`).or(this.page.locator(`text="${value}"`)).first().click()
  }

  async setLimit(value: 10 | 100 | 1000): Promise<void> {
    const select = this.page.locator('[data-testid="sql-limit-select"]')
    await select.click()
    await this.page.locator(`text="${value}"`).first().click()
  }

  // Result panel assertions
  async getResultRowCount(): Promise<number | null> {
    const text = this.page.locator('text=/\\d+ 行|\\d+ rows/i').first()
      .or(this.page.locator('[data-testid="sql-result-row-count"]'))
    if (await text.count() === 0) return null
    const content = await text.textContent() ?? ''
    const match = content.match(/(\d+)/)
    return match ? parseInt(match[1], 10) : null
  }

  async getResultExecutionTime(): Promise<string | null> {
    const text = this.page.locator('[data-testid="sql-execution-time"]')
    if (await text.count() === 0) return null
    return text.first().textContent()
  }

  async getResultTabs(): Promise<string[]> {
    const tabs = this.page.locator('[data-testid="sql-result-tab"]')
    const count = await tabs.count()
    const titles: string[] = []
    for (let i = 0; i < count; i++) {
      titles.push(await tabs.nth(i).textContent() ?? '')
    }
    return titles
  }

  async switchResultTab(index: number): Promise<void> {
    const tabs = this.page.locator('[data-testid="sql-result-tab"]')
    await tabs.nth(index).click()
  }

  async getRiskMessage(): Promise<string | null> {
    const panel = this.page.locator('[data-testid="sql-risk-panel"]')
      .or(this.page.locator('text=/高风险/'))
    if (await panel.count() === 0) return null
    return panel.first().textContent()
  }

  async waitForResult(timeout = 10_000): Promise<void> {
    // Result panel may appear as table, error message, or running indicator
    const result = this.page.locator('table').first()
      .or(this.page.locator('text=/\\d+ 行|\\d+ rows|error|错误|running|执行中/i').first())
      .or(this.page.locator('[data-testid="sql-result-panel"]'))
      .or(this.page.locator('[data-testid="sql-risk-panel"]'))
    await expect(result).toBeVisible({ timeout })
  }

  // Dock button
  async clickSqlDockButton(): Promise<void> {
    const btn = this.page.locator('[data-testid="dock-sql-button"]').or(this.page.locator('button[title*="SQL"]'))
    await btn.click()
  }
}
