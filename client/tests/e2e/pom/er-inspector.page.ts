import type { Page, Locator } from '@playwright/test'

export class ErInspectorPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  private get canvas(): Locator {
    return this.page.locator('[data-er-tab-id]').first()
  }

  async getTableNodes(): Promise<string[]> {
    const nodes = this.page.locator('[data-er-tab-id] .text-text-strong').filter({ hasText: /./ })
    const count = await nodes.count()
    const names: string[] = []
    for (let i = 0; i < count; i++) {
      const text = await nodes.nth(i).textContent()
      if (text) names.push(text.trim())
    }
    return [...new Set(names)]
  }

  async getRelations(): Promise<Array<{ from: string; to: string; type: string }>> {
    const edges = this.page.locator('[data-er-tab-id] [data-er-edge]')
    const count = await edges.count()
    const relations: Array<{ from: string; to: string; type: string }> = []
    for (let i = 0; i < count; i++) {
      const from = await edges.nth(i).getAttribute('data-from-table')
      const to = await edges.nth(i).getAttribute('data-to-table')
      const type = await edges.nth(i).getAttribute('data-relation-type')
      if (from && to) {
        relations.push({ from, to, type: type ?? 'unknown' })
      }
    }
    return relations
  }

  async getVirtualRelations(): Promise<Array<{ from: string; to: string; type: string }>> {
    const all = await this.getRelations()
    const edges = this.page.locator('[data-er-tab-id] [data-er-edge]')
    const result: Array<{ from: string; to: string; type: string }> = []
    for (let i = 0; i < all.length; i++) {
      const isVirtual = await edges.nth(i).getAttribute('data-is-virtual')
      if (isVirtual === 'true') result.push(all[i])
    }
    return result
  }

  async clickAddNeighbors(table: string): Promise<void> {
    const node = this.page.locator('[data-er-tab-id]').locator('.text-text-strong').filter({ hasText: table }).first()
    if (await node.count() === 0) return
    await node.click({ button: 'right' })
    await this.page.waitForTimeout(300)
    const menuItem = this.page.locator('[role="menuitem"]').filter({ hasText: /添加邻居|add.*neighbor|neighbors/i }).first()
    if (await menuItem.count() > 0) await menuItem.click()
  }

  async clickForkToDesigner(): Promise<void> {
    const node = this.page.locator('[data-er-tab-id]').locator('.text-text-strong').first()
    if (await node.count() === 0) return
    await node.click({ button: 'right' })
    await this.page.waitForTimeout(300)
    const menuItem = this.page.locator('[role="menuitem"]').filter({ hasText: /复制到设计器|fork.*designer|designer/i }).first()
    if (await menuItem.count() > 0) await menuItem.click()
  }

  async getActiveTabId(): Promise<string> {
    const canvas = this.canvas
    const id = await canvas.getAttribute('data-er-tab-id')
    return id ?? ''
  }
}
