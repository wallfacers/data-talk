import { Page, Locator, expect } from '@playwright/test'

export class ChatPanelPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  async sendMessage(text: string): Promise<void> {
    const composer = this.page.locator('#composer-slot textarea')
      .or(this.page.locator('[data-testid="composer-input"]'))
      .or(this.page.locator('textarea[placeholder*="查询"]'))
    await composer.fill(text)
    await this.page.keyboard.press('Enter')
  }

  async getLastMessage(): Promise<string> {
    const messages = this.page.locator('[data-testid="chat-message"]')
      .or(this.page.locator('[data-testid="message-bubble"]'))
    const count = await messages.count()
    if (count === 0) return ''
    return await messages.last().textContent() ?? ''
  }

  async getLastToolCallCard(): Promise<{ toolName: string; status: string } | null> {
    const cards = this.page.locator('[data-testid="tool-call-card"]')
      .or(this.page.locator('[data-testid="tool-card"]'))
    const count = await cards.count()
    if (count === 0) return null
    const last = cards.last()
    const toolName = await last.getAttribute('data-tool-name') ?? await last.getAttribute('data-tool') ?? ''
    const status = await last.getAttribute('data-status') ?? ''
    return { toolName, status }
  }

  async waitForAiResponse(timeout = 60_000): Promise<void> {
    // Wait for streaming indicator to disappear or tool call to complete
    const indicator = this.page.locator('[data-testid="ai-streaming-indicator"]')
      .or(this.page.locator('text="AI 正在准备"'))
      .or(this.page.locator('text="思考中"'))
    try {
      await expect(indicator).toHaveCount(0, { timeout })
    } catch {
      // If no streaming indicator exists, just wait for any new message
    }
    // Ensure at least one message exists
    const messages = this.page.locator('[data-testid="chat-message"]')
    await expect(messages).not.toHaveCount(0, { timeout: 5_000 })
  }

  async isDegradedBannerVisible(): Promise<boolean> {
    const banner = this.page.locator('[data-testid="degraded-banner"]')
      .or(this.page.locator('text=/AI 服务不可用/'))
      .or(this.page.locator('text=/服务不可用/'))
    return (await banner.count()) > 0
  }
}
