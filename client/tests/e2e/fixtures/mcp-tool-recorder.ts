import type { Page } from '@playwright/test'

export interface RecordedCall {
  tool: string
  params: Record<string, unknown>
  status?: string
  callId?: string
  partId?: string
  timestamp: number
}

function recorderInitScript() {
  const w = window as unknown as {
    __mcpRecorder?: RecordedCall[]
    __mcpRecorderSeen?: Set<string>
    __dtToolPartTap?: (entry: {
      tool: string
      input: Record<string, unknown> | undefined
      status: string | undefined
      callID: string | undefined
      partId: string
    }) => void
  }
  if (w.__mcpRecorder) return
  w.__mcpRecorder = []
  w.__mcpRecorderSeen = new Set<string>()

  w.__dtToolPartTap = (entry) => {
    const seen = w.__mcpRecorderSeen!
    const key = entry.callID ?? entry.partId
    // Reset the same call's record when it transitions status (pending → running
    // → completed) so the final `input` (which the model may stream in) wins.
    const sameKey = `${key}|${entry.status ?? ''}`
    if (seen.has(sameKey)) return
    seen.add(sameKey)
    const list = w.__mcpRecorder!
    // Drop earlier rows for the same callId so callsFor returns latest snapshot.
    for (let i = list.length - 1; i >= 0; i--) {
      const k = list[i].callId ?? list[i].partId
      if (k === key) { list.splice(i, 1) }
    }
    const tool = entry.tool.startsWith('datatalk_')
      ? entry.tool
      : entry.tool.startsWith('datatalk.')
        ? 'datatalk_' + entry.tool.slice('datatalk.'.length).replace(/[.\-]/g, '_')
        : entry.tool
    list.push({
      tool,
      params: (entry.input ?? {}) as Record<string, unknown>,
      status: entry.status,
      callId: entry.callID,
      partId: entry.partId,
      timestamp: Date.now(),
    })
  }
}

/**
 * Mount a tool recorder that captures AI-triggered tool calls.
 *
 * Strategy: production code in `tool-part.tsx` calls `window.__dtToolPartTap`
 * (a no-op if undefined) with `{ tool, input, status, callID, partId }`. The
 * recorder installs the tap before the page navigates and accumulates entries
 * deduped by `(callID, status)`.
 *
 * NOTE: In the DataTalk architecture the actual MCP /mcp JSON-RPC
 * exchange happens server-side (OpenCode ↔ backend). The frontend only sees
 * tool parts streamed back through the chat channel, so this recorder reads
 * those rendered parts via the React-level tap.
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
          ((window as unknown as { __mcpRecorder?: RecordedCall[] }).__mcpRecorder ?? []).filter(
            (c: RecordedCall) => c.tool === t,
          ),
        tool,
      ),
    all: async (): Promise<RecordedCall[]> =>
      page.evaluate(
        () => (window as unknown as { __mcpRecorder?: RecordedCall[] }).__mcpRecorder ?? [],
      ),
    clear: async (): Promise<void> => {
      await page.evaluate(() => {
        const w = window as unknown as { __mcpRecorder?: RecordedCall[]; __mcpRecorderSeen?: Set<string> }
        w.__mcpRecorder = []
        w.__mcpRecorderSeen = new Set<string>()
      })
    },
  }
}
