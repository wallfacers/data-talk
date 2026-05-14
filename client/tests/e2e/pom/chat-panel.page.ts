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

  async waitForAiResponse(timeout = 180_000): Promise<void> {
    // AI response lifecycle:
    // 1. [data-testid="assistant-thinking-shell"] appears (visible) when AI starts
    // 2. Shell becomes invisible when content starts streaming
    // 3. The composer's stop button (Loader2Icon, type="button" variant=destructive)
    //    is rendered while `isStreaming` is true. Once `session.idle` arrives,
    //    `setStreaming(sessionId, false)` flips it back to the submit button
    //    (type="submit"). That submit-vs-stop transition is the authoritative
    //    signal — relying solely on shell visibility OR basic-tool count
    //    stability fires prematurely between successive tool batches.
    const thinkingShell = this.page.locator('[data-testid="assistant-thinking-shell"]')

    // Phase 1: Wait for AI to start (shell appears). Best-effort: a very fast
    // first chunk can skip the shell entirely.
    try {
      await expect(thinkingShell).toBeVisible({ timeout: 15000 })
    } catch {
      // Shell may never appear if AI responds instantly or errors
    }

    // Phase 2: Wait for the streaming flag to clear by polling the composer.
    // While `isStreaming` is true, the composer renders an icon button without
    // type=submit; once cleared, the send button is a `type="submit"`. We
    // require the submit form to be present and the stop loader (`.animate-spin`)
    // to be gone for a stable settle window so tool parts that render *after*
    // session.idle still land in the recorder.
    await this.page.waitForFunction(
      (settleMs) => {
        const composer = document.getElementById('composer-slot')
        if (!composer) return false
        const hasSubmit = !!composer.querySelector('button[type="submit"]')
        const hasStopLoader = !!composer.querySelector('button .animate-spin')
        const settled = hasSubmit && !hasStopLoader
        const key = '__dtAiResponseSettleV2'
        const winAny = window as unknown as Record<string, { settled: boolean; time: number } | undefined>
        const last = winAny[key]
        if (!last || last.settled !== settled) {
          winAny[key] = { settled, time: Date.now() }
          return false
        }
        return settled && Date.now() - last.time >= settleMs
      },
      1500,
      { timeout, polling: 250 },
    )

    // Phase 3: After streaming clears, give React a tick to flush any final
    // tool-part renders so the recorder tap sees the terminal status.
    await this.page.waitForTimeout(200)
    // Belt-and-braces: ensure thinking shell is hidden (it should already be).
    await expect(thinkingShell).toBeHidden({ timeout: 5_000 }).catch(() => undefined)
  }

  async isDegradedBannerVisible(): Promise<boolean> {
    const banner = this.page.locator('[data-testid="degraded-banner"]')
      .or(this.page.locator('text=/AI 服务不可用/'))
      .or(this.page.locator('text=/服务不可用/'))
    return (await banner.count()) > 0
  }
}
