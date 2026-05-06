import type { Page } from '@playwright/test'
import { ChatPanelPage } from '../pom/chat-panel.page'
import { getLatestOpenCodeSession } from './mcp-context'

let cachedSession: { dataTalkSessionId: string; openCodeSessionId: string } | null = null

export function getCachedHybridSession(): typeof cachedSession {
  return cachedSession
}

/**
 * Ensure the current browser page has an active OpenCode SSE subscriber.
 *
 * Client-side actions (ui_exec / ui_patch / ui_read) require a subscriber.
 * Because Playwright gives each test a fresh page, we send a trivial chat
 * message so the frontend opens the SSE channel and registers the session
 * in the backend SQLite bridge table.
 */
export async function ensureHybridSession(page: Page, message = 'hello') {
  const before = getLatestOpenCodeSession()
  const chat = new ChatPanelPage(page)
  await chat.sendMessage(message)
  await chat.waitForAiResponse()

  for (let i = 0; i < 50; i++) {
    const session = getLatestOpenCodeSession()
    if (session) {
      // Accept if no prior session or the OpenCode session ID changed,
      // indicating this page established a fresh SSE connection.
      if (!before || session.openCodeSessionId !== before.openCodeSessionId) {
        cachedSession = session
        return session
      }
    }
    await page.waitForTimeout(100)
  }
  throw new Error('OpenCode session did not appear in SQLite bridge after 5s')
}
