import type { Page, Locator } from '@playwright/test'

export class ErDesignerPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  private get canvas(): Locator {
    return this.page.locator('[data-er-tab-id]').first()
  }

  async getTableNodes(): Promise<Array<{ id: string; name: string; columns: Array<{ name: string; type: string }> }>> {
    const tables = this.page.locator('[data-er-tab-id] .text-text-strong').filter({ hasText: /./ })
    const count = await tables.count()
    const result: Array<{ id: string; name: string; columns: Array<{ name: string; type: string }> }> = []
    for (let i = 0; i < count; i++) {
      const name = (await tables.nth(i).textContent())?.trim() ?? ''
      if (!name) continue
      const columnRows = this.page.locator(`[data-testid^="er-row-"]`)
      const cols: Array<{ name: string; type: string }> = []
      const rowCount = await columnRows.count()
      for (let j = 0; j < rowCount; j++) {
        const testid = await columnRows.nth(j).getAttribute('data-testid')
        if (testid) {
          const colName = testid.replace('er-row-', '')
          const typeText = await columnRows.nth(j).textContent() ?? ''
          const typeMatch = typeText.match(/\b([A-Z]+(?:\([^)]+\))?)\b/)
          cols.push({ name: colName, type: typeMatch?.[1] ?? 'UNKNOWN' })
        }
      }
      result.push({ id: `t_${name.toLowerCase()}`, name, columns: cols })
    }
    return result
  }

  async clickBindTarget(): Promise<void> {
    const toolbar = this.page.locator('[data-er-tab-id]').locator('button').filter({ hasText: /绑定|bind|target/i }).first()
    if (await toolbar.count() > 0) {
      await toolbar.click()
      await this.page.waitForTimeout(300)
    }
  }

  async fillBindTarget(opts: { connectionId: string; database?: string; schema?: string }): Promise<void> {
    const dialog = this.page.locator('[role="dialog"]')
    const connSelect = dialog.locator('#er-bind-target-connection').first()
    if (await connSelect.count() > 0) {
      await connSelect.click()
      await dialog.locator(`[data-value="${opts.connectionId}"]`).or(dialog.locator('text=' + opts.connectionId)).first().click()
    }
    if (opts.database) {
      const dbSelect = dialog.locator('#er-bind-target-database').first()
      if (await dbSelect.count() > 0) {
        await dbSelect.click()
        await dialog.locator(`[data-value="${opts.database}"]`).or(dialog.locator('text=' + opts.database)).first().click()
      }
    }
    if (opts.schema) {
      const schemaSelect = dialog.locator('#er-bind-target-schema').first()
      if (await schemaSelect.count() > 0) {
        await schemaSelect.click()
        await dialog.locator(`[data-value="${opts.schema}"]`).or(dialog.locator('text=' + opts.schema)).first().click()
      }
    }
  }

  async clickDiffAgainstDb(): Promise<void> {
    const btn = this.page.locator('[data-er-tab-id]').locator('button').filter({ hasText: /对比|diff|差异/i }).first()
    if (await btn.count() > 0) {
      await btn.click()
      await this.page.waitForTimeout(500)
    }
  }

  async clickGenerateDdl(): Promise<{ queryEditorTabId: string; ddl: string }> {
    const btn = this.page.locator('[data-er-tab-id]').locator('button').filter({ hasText: /生成 DDL|DDL|generate/i }).first()
    if (await btn.count() > 0) {
      await btn.click()
      await this.page.waitForTimeout(1_000)
    }
    return { queryEditorTabId: '', ddl: '' }
  }

  async getActiveBaseVersion(): Promise<number> {
    const version = await this.page.evaluate(() => {
      const el = document.querySelector('[data-er-tab-id]')
      return el?.getAttribute('data-payload-version')
    })
    return version ? parseInt(version, 10) : 0
  }

  async getDialect(): Promise<string> {
    const dialect = await this.page.evaluate(() => {
      const el = document.querySelector('[data-er-tab-id]')
      return el?.getAttribute('data-dialect')
    })
    return dialect ?? ''
  }
}
