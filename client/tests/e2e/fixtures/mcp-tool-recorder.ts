import type { Page } from '@playwright/test'

export interface RecordedCall {
  tool: string
  params: Record<string, unknown>
  timestamp: number
}

function recorderInitScript() {
  if ((window as any).__mcpRecorder) return
  ;(window as any).__mcpRecorder = []

  function extractToolName(btn: HTMLButtonElement): string {
    // TextShimmer renders the text twice (base + shimmer spans), so
    // prefer the dedicated base span if present.
    const base = btn.querySelector('[data-slot="text-shimmer-char-base"]')
    if (base) return base.textContent?.trim() ?? ''

    const text = btn.textContent?.trim() ?? ''
    if (!text.startsWith('datatalk_')) return text

    // Heuristic: dedupe when the string is exactly "X" + "X"
    if (text.length % 2 === 0) {
      const half = text.length / 2
      if (text.substring(0, half) === text.substring(half)) {
        return text.substring(0, half)
      }
    }
    return text
  }

  function scan() {
    const buttons = document.querySelectorAll(
      '[data-component="session-turn"] button'
    )
    buttons.forEach((btn) => {
      const text = extractToolName(btn as HTMLButtonElement)
      if (!text.startsWith('datatalk_')) return
      if ((btn as any).__mcpRecorded) return
      ;(btn as any).__mcpRecorded = true

      ;(window as any).__mcpRecorder.push({
        tool: text,
        params: {},
        timestamp: Date.now(),
      })
    })
  }

  const observer = new MutationObserver(scan)
  observer.observe(document.body, { childList: true, subtree: true })
  ;(window as any).__mcpRecorderObserver = observer
  scan()
}

/**
 * Mount a tool recorder that captures AI-triggered tool calls.
 *
 * Strategy: the frontend renders tool invocations as DOM buttons
 * inside [data-component="session-turn"] with text starting with
 * "datatalk_". We observe the DOM and extract the tool name from
 * button textContent.
 *
 * NOTE: In the DataTalk architecture the actual MCP /mcp JSON-RPC
 * exchange happens server-side (OpenCode ↔ backend).  The frontend
 * only sees the results streamed back through the chat channel.
 * Therefore this recorder inspects rendered UI state rather than
 * intercepting fetch.
 */
export async function mountToolRecorder(page: Page) {
  // Register for future navigations
  await page.addInitScript(recorderInitScript)
  // If page is already loaded, inject immediately
  await page.evaluate(recorderInitScript)

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
