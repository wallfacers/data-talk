import { Page, Locator, expect } from '@playwright/test'

export class StagePage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  // Stage toggle
  async openStage(): Promise<void> {
    const toggle = this.page.locator('[data-testid="stage-toggle-button"]').or(this.page.locator('button[title*="Stage"]').or(this.page.locator('button[title*="工作台"]')))
    if (await toggle.count() > 0 && !(await this.isOpen())) {
      await toggle.click()
    }
  }

  async closeStage(): Promise<void> {
    const closeBtn = this.page.locator('[data-testid="stage-close-button"]')
    if (await closeBtn.count() > 0 && await this.isOpen()) {
      await closeBtn.click()
    }
  }

  async maximize(): Promise<void> {
    const btn = this.page.locator('[data-testid="stage-maximize-button"]')
    if (await btn.count() > 0) await btn.click()
  }

  async restore(): Promise<void> {
    const btn = this.page.locator('[data-testid="stage-restore-button"]')
    if (await btn.count() > 0) await btn.click()
  }

  async isOpen(): Promise<boolean> {
    const stage = this.page.locator('[data-testid="stage-window"]')
    return (await stage.count()) > 0
  }

  async isMaximized(): Promise<boolean> {
    const stage = this.page.locator('[data-testid="stage-window"]')
    if (await stage.count() === 0) return false
    const cls = await stage.getAttribute('class')
    return cls?.includes('maximized') ?? false
  }

  // Tabs
  async getTabTitles(): Promise<string[]> {
    const tabs = this.page.locator('[data-testid="stage-tab-bar"] [data-testid="stage-tab-title"]')
    const count = await tabs.count()
    const titles: string[] = []
    for (let i = 0; i < count; i++) {
      titles.push(await tabs.nth(i).textContent() ?? '')
    }
    return titles
  }

  async clickTab(title: string): Promise<void> {
    const tab = this.page.locator(`[data-testid="stage-tab-bar"] [data-testid="stage-tab-title"]:has-text("${title}")`)
    await tab.click()
  }

  async closeTabByTitle(title: string): Promise<void> {
    const tab = this.page.locator(`[data-testid="stage-tab-bar"] [data-testid="stage-tab"]:has-text("${title}")`)
    const closeBtn = tab.locator('[data-testid="stage-tab-close"]')
    await closeBtn.click()
  }

  async rightClickTab(title: string): Promise<void> {
    const tab = this.page.locator(`[data-testid="stage-tab-bar"] [data-testid="stage-tab"]:has-text("${title}")`)
    await tab.click({ button: 'right' })
  }

  // Session sidebar
  async createSession(title?: string): Promise<void> {
    const addBtn = this.page.locator('[data-testid="new-session-button"]')
      .or(this.page.getByRole('button', { name: /create session|新建会话|创建会话/i }))
    await addBtn.click()
    if (title) {
      const input = this.page.locator('[data-testid="session-title-input"]')
      if (await input.count() > 0) {
        await input.fill(title)
        await input.press('Enter')
      }
    }
  }

  async switchSession(sessionTitle: string): Promise<void> {
    const session = this.page.locator(`[data-testid="session-list-item"]:has-text("${sessionTitle}")`)
      .or(this.page.locator(`[data-testid="session-item"]:has-text("${sessionTitle}")`))
    await session.nth(0).click()
  }

  async getSessionTitles(): Promise<string[]> {
    const items = this.page.locator('[data-testid="session-list-item"]')
      .or(this.page.locator('[data-testid="session-item"]'))
    const count = await items.count()
    const titles: string[] = []
    for (let i = 0; i < count; i++) {
      const text = await items.nth(i).textContent() ?? ''
      if (text.trim()) titles.push(text.trim())
    }
    return titles
  }
}
