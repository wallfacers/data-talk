import type { Page } from '@playwright/test'

export interface RecordedCall {
  tool: string
  params: Record<string, unknown>
  timestamp: number
}

/**
 * Mount a tool recorder that captures AI-triggered tool calls.
 *
 * Strategy: the frontend renders tool invocations as DOM cards
 * (data-testid="tool-call-card" / "tool-card") after receiving
 * SSE stream parts of type 'tool'.  We poll the DOM and extract
 * tool name + input from each card.
 *
 * NOTE: In the DataTalk architecture the actual MCP /mcp JSON-RPC
 * exchange happens server-side (OpenCode ↔ backend).  The frontend
 * only sees the results streamed back through the chat channel.
 * Therefore this recorder inspects rendered UI state rather than
 * intercepting fetch.
 */
export async function mountToolRecorder(page: Page) {
  await page.addInitScript(() => {
    ;(window as any).__mcpRecorder = []

    const observer = new MutationObserver(() => {
      const cards = document.querySelectorAll(
        '[data-testid="tool-call-card"], [data-testid="tool-card"]'
      )
      cards.forEach((card) => {
        const toolName =
          card.getAttribute('data-tool-name') ??
          card.getAttribute('data-tool') ??
          card.querySelector('[data-tool-name]')?.getAttribute('data-tool-name') ??
          ''
        if (!toolName) return

        const existing = ((window as any).__mcpRecorder as RecordedCall[]).some(
          (c) => c.tool === toolName && Math.abs(c.timestamp - Date.now()) < 500
        )
        if (existing) return

        const paramsAttr = card.getAttribute('data-params')
        let params: Record<string, unknown> = {}
        if (paramsAttr) {
          try {
            params = JSON.parse(paramsAttr)
          } catch {}
        }

        ;(window as any).__mcpRecorder.push({
          tool: toolName,
          params,
          timestamp: Date.now(),
        })
      })
    })

    observer.observe(document.body, { childList: true, subtree: true })
    ;(window as any).__mcpRecorderObserver = observer
  })

  return {
    callsFor: async (tool: string): Promise<RecordedCall[]> =>
      page.evaluate(
        (t) =>
          ((window as any).__mcpRecorder ?? []).filter(
            (c: RecordedCall) => c.tool === t
          ),
        tool
      ),
    all: async (): Promise<RecordedCall[]> =>
      page.evaluate(() => (window as any).__mcpRecorder ?? []),
    clear: async (): Promise<void> => {
      await page.evaluate(() => {
        ;(window as any).__mcpRecorder = []
      })
    },
  }
}
