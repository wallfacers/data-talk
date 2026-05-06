import type { Page, Locator } from '@playwright/test'

export class ErInspectorPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  private get canvas(): Locator {
    return this.page.locator('[data-er-tab-id]').first()
  }

  // ── Existing methods preserved ──
  async getTableNodes(): Promise<string[]> {
    const nodes = this.canvas.locator('[data-er-table-name]')
    const count = await nodes.count()
    const names = new Set<string>()
    for (let i = 0; i < count; i++) {
      const name = await nodes.nth(i).getAttribute('data-er-table-name')
      if (name) names.add(name)
    }
    return [...names]
  }

  async getRelations(): Promise<Array<{ from: string; to: string; type: string; isVirtual: boolean }>> {
    const edges = this.canvas.locator('[data-er-edge]')
    const count = await edges.count()
    const relations: Array<{ from: string; to: string; type: string; isVirtual: boolean }> = []
    for (let i = 0; i < count; i++) {
      const from = await edges.nth(i).getAttribute('data-from-table')
      const to = await edges.nth(i).getAttribute('data-to-table')
      const type = await edges.nth(i).getAttribute('data-relation-type')
      const isVirtual = (await edges.nth(i).getAttribute('data-is-virtual')) === 'true'
      if (from && to) relations.push({ from, to, type: type ?? 'unknown', isVirtual })
    }
    return relations
  }

  async getActiveTabId(): Promise<string> {
    return (await this.canvas.getAttribute('data-er-tab-id')) ?? ''
  }

  // ── New methods ──
  async clickRefresh(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-refresh"]').first().click()
  }

  async clickAutoLayout(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-auto-layout"]').first().click()
  }

  async clickFitView(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-fit-view"]').first().click()
  }

  async setNeighborDepth(depth: 0 | 1 | 2): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-neighbor-depth"]').first().selectOption(String(depth))
  }

  async clickAddVirtualRelation(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-add-virtual-relation"]').first().click()
  }

  async clickForkToDesigner(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-fork-to-designer"]').first().click()
  }

  async dragNode(tableName: string, dx: number, dy: number): Promise<void> {
    const node = this.canvas.locator(`[data-er-table-name="${tableName}"]`).first()
    const box = await node.boundingBox()
    if (!box) throw new Error(`Node ${tableName} has no bounding box`)
    const startX = box.x + box.width / 2
    const startY = box.y + 12
    await this.page.mouse.move(startX, startY)
    await this.page.mouse.down()
    for (let step = 1; step <= 6; step++) {
      await this.page.mouse.move(startX + (dx * step) / 6, startY + (dy * step) / 6, { steps: 1 })
    }
    await this.page.mouse.up()
  }

  async getNodePosition(tableName: string): Promise<{ x: number; y: number }> {
    const node = this.canvas.locator(`[data-er-table-name="${tableName}"]`).first()
    const transform = await node.evaluate((el) => (el.parentElement as HTMLElement | null)?.style.transform ?? '')
    const m = transform.match(/translate\(([-\d.]+)px,\s*([-\d.]+)px\)/)
    return m ? { x: parseFloat(m[1]), y: parseFloat(m[2]) } : { x: 0, y: 0 }
  }

  async zoomIn(steps = 1): Promise<void> {
    const btn = this.page.locator('.react-flow__controls-zoomin')
    for (let i = 0; i < steps; i++) await btn.click()
  }

  async zoomOut(steps = 1): Promise<void> {
    const btn = this.page.locator('.react-flow__controls-zoomout')
    for (let i = 0; i < steps; i++) await btn.click()
  }

  async panCanvas(dx: number, dy: number): Promise<void> {
    const pane = this.canvas.locator('.react-flow__pane').first()
    const box = await pane.boundingBox()
    if (!box) throw new Error('No flow pane bbox')
    const cx = box.x + box.width / 2
    const cy = box.y + box.height / 2
    await this.page.mouse.move(cx, cy)
    await this.page.mouse.down()
    for (let step = 1; step <= 4; step++) {
      await this.page.mouse.move(cx + (dx * step) / 4, cy + (dy * step) / 4, { steps: 1 })
    }
    await this.page.mouse.up()
  }

  async expectEmptyState(): Promise<void> {
    await this.canvas.locator('text=/No tables selected|empty/i').first().waitFor({ state: 'visible' })
  }

  async getMode(): Promise<string> {
    return (await this.canvas.locator('[data-er-mode]').first().getAttribute('data-er-mode')) ?? ''
  }

  async getPayloadVersion(): Promise<number> {
    const v = await this.canvas.getAttribute('data-payload-version')
    return v ? parseInt(v, 10) : 0
  }
}
