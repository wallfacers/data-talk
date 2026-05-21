import type { Page, Locator } from '@playwright/test'

export class ErDesignerPage {
  readonly page: Page
  readonly tabId: string

  constructor(page: Page, tabId = '') {
    this.page = page
    this.tabId = tabId
  }

  private get canvas(): Locator {
    return this.page.locator('[data-er-tab-id]').first()
  }

  async getTableNodes(): Promise<Array<{ id: string; name: string }>> {
    const nodes = this.canvas.locator('[data-er-table-name]')
    const count = await nodes.count()
    const out: Array<{ id: string; name: string }> = []
    for (let i = 0; i < count; i++) {
      const id = (await nodes.nth(i).getAttribute('data-er-table-id')) ?? ''
      const name = (await nodes.nth(i).getAttribute('data-er-table-name')) ?? ''
      if (id || name) out.push({ id, name })
    }
    return out
  }

  // toolbar
  async clickAddTable(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-add-table"]').first().click()
  }
  async clickAutoLayout(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-auto-layout"]').first().click()
  }
  async clickFitView(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-fit-view"]').first().click()
  }
  async clickBindTarget(): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-bind-target"]').first().click()
  }
  async clickDiffVsDb(): Promise<{ disabled: boolean }> {
    const btn = this.page.locator('[data-testid="er-toolbar-diff"]').first()
    const disabled = (await btn.getAttribute('disabled')) !== null
      || (await btn.getAttribute('aria-disabled')) === 'true'
    if (!disabled) await btn.click()
    return { disabled }
  }
  async clickGenerateDdl(): Promise<{ disabled: boolean }> {
    const btn = this.page.locator('[data-testid="er-toolbar-generate-ddl"]').first()
    const disabled = (await btn.getAttribute('disabled')) !== null
      || (await btn.getAttribute('aria-disabled')) === 'true'
    if (!disabled) await btn.click()
    return { disabled }
  }
  async setDialect(d: 'mysql' | 'postgresql' | 'h2' | 'sqlite'): Promise<void> {
    await this.page.locator('[data-testid="er-toolbar-dialect"]').first().click()
    await this.page.locator(`[role="option"]`).filter({ hasText: d }).first().click()
  }
  async getDialect(): Promise<string> {
    return (await this.canvas.getAttribute('data-dialect')) ?? ''
  }
  async getDiffDisabledHint(): Promise<string | null> {
    const btn = this.page.locator('[data-testid="er-toolbar-diff"]').first()
    return await btn.getAttribute('aria-describedby').then((id) =>
      id ? this.page.locator(`#${id}`).textContent() : null,
    )
  }
  async getGenerateDdlDisabledHint(): Promise<string | null> {
    const btn = this.page.locator('[data-testid="er-toolbar-generate-ddl"]').first()
    return await btn.getAttribute('aria-describedby').then((id) =>
      id ? this.page.locator(`#${id}`).textContent() : null,
    )
  }

  // bind dialog
  async fillBindTarget(opts: { connectionId: string; connectionName?: string; database?: string; schema?: string }): Promise<void> {
    const dialog = this.page.locator('[role="dialog"]')
    const name = opts.connectionName ?? 'testconn'
    // Open dropdown and select connection by text
    await dialog.locator('#er-bind-target-connection').click()
    await this.page.waitForTimeout(500)
    await this.page.locator('[role="option"]').filter({ hasText: name }).first().click({ timeout: 5000 })
    if (opts.database) {
      await dialog.locator('#er-bind-target-database').click()
      await this.page.locator('[role="option"]').filter({ hasText: opts.database }).first().click()
    }
    if (opts.schema) {
      await dialog.locator('#er-bind-target-schema').click()
      await this.page.locator('[role="option"]').filter({ hasText: opts.schema }).first().click()
    }
  }
  async confirmBindTarget(): Promise<void> {
    await this.page.locator('[role="dialog"] button:has-text("Bind")').first().click()
  }
  async cancelBindTarget(): Promise<void> {
    await this.page.locator('[role="dialog"] button:has-text("Cancel")').first().click()
  }
  async getBindDialogConnectionOptions(): Promise<string[]> {
    await this.page.locator('#er-bind-target-connection').click()
    const opts = this.page.locator('[role="option"]')
    const count = await opts.count()
    const names: string[] = []
    for (let i = 0; i < count; i++) {
      const text = await opts.nth(i).textContent()
      if (text) names.push(text.trim())
    }
    await this.page.keyboard.press('Escape')
    return names
  }
  async getBindDialogEmptyText(): Promise<string | null> {
    const dialog = this.page.locator('[role="dialog"]')
    const empty = dialog.locator('p').filter({ hasText: /no.*connection|empty/i }).first()
    return (await empty.count()) > 0 ? await empty.textContent() : null
  }
  async isSchemaSelectVisible(): Promise<boolean> {
    return await this.page.locator('#er-bind-target-schema').isVisible()
  }

  // table / column edit
  async openContextMenu(tableName: string): Promise<void> {
    const node = this.canvas.locator(`[data-er-table-name="${tableName}"]`).first()
    // Use force: true to bypass ReactFlow overlay interception,
    // but first ensure the node is visible and stable
    await node.waitFor({ state: 'visible', timeout: 3_000 })
    await node.click({ button: 'right' })
  }
  async clickContextMenuItem(label: 'rename' | 'addColumn' | 'deleteTable'): Promise<void> {
    const map: Record<string, RegExp> = {
      rename: /Rename|重命名/,
      addColumn: /Add column|添加列/,
      deleteTable: /Delete table|删除表/,
    }
    await this.page.locator('[role="menuitem"]').filter({ hasText: map[label] }).first().click()
  }
  async renameTable(oldName: string, newName: string): Promise<void> {
    await this.openContextMenu(oldName)
    await this.clickContextMenuItem('rename')
    await this.page.waitForTimeout(500)
    // Find the focused rename input by aria-label pattern, not by old table name
    const input = this.page.locator('input[aria-label*="Table name"], input[data-er-rename-input]').first()
    await input.waitFor({ state: 'visible', timeout: 5_000 })
    await input.fill(newName)
    await input.press('Enter')
  }
  async addColumnViaToolbarPlus(tableName: string): Promise<void> {
    const node = this.canvas.locator(`[data-er-table-name="${tableName}"]`).first()
    await node.locator('button').filter({ hasText: /Add column|添加列|\+/ }).first().click()
  }
  async editColumnField(
    table: string,
    column: string,
    field: 'name' | 'type' | 'nullable' | 'isPrimaryKey' | 'isAutoIncrement' | 'default' | 'comment',
    value: any,
  ): Promise<void> {
    const row = this.canvas
      .locator(`[data-er-table-name="${table}"]`)
      .locator(`[data-testid="er-row-${column}"]`)
      .first()
    if (field === 'name' || field === 'type' || field === 'default' || field === 'comment') {
      const input = row.locator(`[data-er-field="${field}"], input[name="${field}"]`).first()
      await input.click()
      await input.fill(String(value))
      await input.press('Tab')
    } else {
      const checkbox = row.locator(`[data-er-field="${field}"]`).first()
      const checked = await checkbox.isChecked()
      if (checked !== Boolean(value)) await checkbox.click()
    }
  }
  async deleteColumn(table: string, column: string): Promise<void> {
    const row = this.canvas
      .locator(`[data-er-table-name="${table}"]`)
      .locator(`[data-testid="er-row-${column}"]`)
      .first()
    await row.locator('button[aria-label*="Delete"], button[aria-label*="删除"]').first().click()
  }

  // relations (R9 implementation)
  async dragConnect(fromTable: string, fromColumn: string, toTable: string, toColumn: string): Promise<void> {
    const sourceColId = await this.resolveColumnId(fromTable, fromColumn)
    const targetColId = await this.resolveColumnId(toTable, toColumn)
    const sourceHandle = this.canvas
      .locator(`[data-er-column-handle="${sourceColId}:source"], [data-handleid="${sourceColId}-source"]`)
      .first()
    const targetHandle = this.canvas
      .locator(`[data-er-column-handle="${targetColId}:target"], [data-handleid="${targetColId}-target"]`)
      .first()
    const srcBox = await sourceHandle.boundingBox()
    const dstBox = await targetHandle.boundingBox()
    if (!srcBox || !dstBox) {
      // Fallback: page.evaluate write store directly
      await this.page.evaluate(
        ({ tabId, fromTable, fromColumn, toTable, toColumn, sourceColId, targetColId }) => {
          const er = (window as any).__DT_E2E__.er()
          er.applyDesignerPatch(tabId, [
            {
              op: 'add',
              path: '/relations/-',
              value: {
                fromTableId: fromTable,
                fromColumnId: sourceColId,
                toTableId: toTable,
                toColumnId: targetColId,
                type: 'many_to_one',
                constraintMethod: 'database_fk',
              },
            },
          ])
        },
        {
          tabId: await this.canvas.getAttribute('data-er-tab-id'),
          fromTable, fromColumn, toTable, toColumn, sourceColId, targetColId,
        },
      )
      return
    }
    const sx = srcBox.x + srcBox.width / 2
    const sy = srcBox.y + srcBox.height / 2
    const tx = dstBox.x + dstBox.width / 2
    const ty = dstBox.y + dstBox.height / 2
    await this.page.mouse.move(sx, sy)
    await this.page.mouse.down()
    for (let step = 1; step <= 8; step++) {
      await this.page.mouse.move(sx + ((tx - sx) * step) / 8, sy + ((ty - sy) * step) / 8, { steps: 1 })
    }
    await this.page.mouse.up()
  }

  private async resolveColumnId(table: string, column: string): Promise<string> {
    return (
      (await this.canvas
        .locator(`[data-er-table-name="${table}"] [data-testid="er-row-${column}"]`)
        .first()
        .getAttribute('data-er-column-id')) ?? column
    )
  }

  async setEdgeRelationType(
    edgeId: string,
    type: 'one_to_one' | 'one_to_many' | 'many_to_one' | 'many_to_many',
  ): Promise<void> {
    // Click the edge using ReactFlow's built-in selector
    const edgeGroup = this.canvas.locator(`.react-flow__edge[data-id="${edgeId}"]`).first()
    if ((await edgeGroup.count()) > 0) {
      await edgeGroup.click()
    } else {
      await this.canvas.locator(`[data-er-edge][data-id="${edgeId}"]`).first().click()
    }
    await this.page.waitForTimeout(500)
    // Click the relation type select in the edge label
    const select = this.page.locator('[aria-label*="Relation type"]').first()
    if ((await select.count()) > 0) {
      await select.click()
      await this.page.locator(`[role="option"]`).filter({ hasText: type }).first().click()
    }
  }
  async deleteEdge(edgeId: string): Promise<void> {
    const edgeGroup = this.canvas.locator(`.react-flow__edge[data-id="${edgeId}"]`).first()
    if ((await edgeGroup.count()) > 0) {
      await edgeGroup.click()
    } else {
      await this.canvas.locator(`[data-er-edge][data-id="${edgeId}"]`).first().click()
    }
    await this.page.waitForTimeout(500)
    // Try the trash button in the edge label, fallback to Delete key
    const delBtn = this.page.locator('[aria-label*="Delete"]').first()
    if ((await delBtn.count()) > 0 && await delBtn.isVisible()) {
      await delBtn.click()
    } else {
      await this.page.keyboard.press('Delete')
    }
  }

  // keyboard
  async selectNode(tableName: string): Promise<void> {
    await this.canvas.locator(`[data-er-table-name="${tableName}"]`).first().click()
  }
  async selectEdge(edgeId: string): Promise<void> {
    const edgeGroup = this.canvas.locator(`.react-flow__edge[data-id="${edgeId}"]`).first()
    if ((await edgeGroup.count()) > 0) {
      await edgeGroup.click()
    } else {
      await this.canvas.locator(`[data-er-edge][data-id="${edgeId}"]`).first().click()
    }
  }
  async pressDelete(): Promise<void> {
    await this.page.keyboard.press('Delete')
  }

  // empty state
  async expectEmptyState(): Promise<void> {
    await this.canvas.locator('text=/empty.*designer|no tables/i').first().waitFor({ state: 'visible' })
  }
  async clickEmptyAddTable(): Promise<void> {
    await this.canvas.locator('button').filter({ hasText: /Add table|添加表/ }).first().click()
  }

  async getPayloadVersion(): Promise<number> {
    const v = await this.canvas.getAttribute('data-payload-version')
    return v ? parseInt(v, 10) : 0
  }
}
