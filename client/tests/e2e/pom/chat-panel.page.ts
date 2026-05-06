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
    const turns = this.page.locator('[data-component="session-turn"]')
    const count = await turns.count()
    if (count === 0) return ''
    return await turns.last().textContent() ?? ''
  }

  async getLastToolCallCard(): Promise<{ toolName: string; status: string } | null> {
    // Tool calls are rendered as buttons with datatalk_ prefix inside session turns.
    // TextShimmer doubles the textContent, so read from the base span when present.
    const toolButtons = this.page.locator('[data-component="session-turn"] button')
      .filter({ hasText: /^datatalk_/ })
    const count = await toolButtons.count()
    if (count === 0) return null
    const last = toolButtons.last()
    const toolName = await last.locator('[data-slot="text-shimmer-char-base"]').textContent()
      .catch(() => last.textContent()) ?? ''
    return { toolName: toolName.trim(), status: '' }
  }

  async waitForAiResponse(timeout = 60_000): Promise<void> {
    // AI response lifecycle:
    // 1. [data-testid="assistant-thinking-shell"] appears (visible) when AI starts
    // 2. Shell becomes invisible when content starts streaming
    // 3. Shell is removed from DOM when response completes
    const thinkingShell = this.page.locator('[data-testid="assistant-thinking-shell"]')

    // Phase 1: Wait for AI to start (shell appears)
    try {
      await expect(thinkingShell).toBeVisible({ timeout: 15000 })
    } catch {
      // Shell may never appear if AI responds instantly or errors
    }

    // Phase 2: Wait for AI to finish (shell hidden/removed)
    await expect(thinkingShell).toBeHidden({ timeout })

    // Phase 3: Tool calls may render *after* the thinking shell disappears.
    // Wait until the count of [data-component="basic-tool"] cards stays
    // stable for a short period (1.5 s) so late tool buttons are captured.
    await this.page.waitForFunction(
      () => {
        const key = '__dtAiResponseSettle'
        const count = document.querySelectorAll('[data-component="basic-tool"]').length
        const last = (window as any)[key]
        if (!last || last.count !== count) {
          ;(window as any)[key] = { count, time: Date.now() }
          return false
        }
        return Date.now() - last.time >= 1500
      },
      { timeout: timeout - 15000, polling: 500 }
    )
  }

  async isDegradedBannerVisible(): Promise<boolean> {
    const banner = this.page.locator('[data-testid="degraded-banner"]')
      .or(this.page.locator('text=/AI 服务不可用/'))
      .or(this.page.locator('text=/服务不可用/'))
    return (await banner.count()) > 0
  }
}
