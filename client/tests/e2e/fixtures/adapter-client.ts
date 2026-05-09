import type { APIRequestContext } from '@playwright/test'

const BASE = process.env.DATATALK_ADAPTER_BASE_URL ?? 'http://localhost:8080'

export interface McpToolResult {
  content?: Array<{ type: string; text?: string }>
  isError?: boolean
  [k: string]: unknown
}

export interface McpRpcResponse {
  jsonrpc: '2.0'
  id?: number | string | null
  result?: McpToolResult
  error?: { code: number; message: string }
}

/**
 * HTTP helpers for direct backend interaction.
 *
 * MCP actions are exposed through the OpenCode JSON-RPC endpoint at /mcp
 * (method "tools/call").  Direct REST endpoints exist for connections,
 * sessions, stage tabs, ER tabs, SQL execution, diagnostics, and artifacts.
 */
export function adapterClient(request: APIRequestContext) {
  return {
    // ── MCP JSON-RPC (tool contract layer) ──
    mcpCall: async (name: string, arguments_: Record<string, unknown>, opts?: { sessionId?: string; timeout?: number }): Promise<McpRpcResponse> => {
      // Backend /mcp expects MCP tool name (e.g. 'read_schema'), not OpenCode name ('datatalk_read_schema')
      const mcpToolName = name.startsWith('datatalk_') ? name.slice('datatalk_'.length) : name

      // Inject bridge fields required by McpActionBridge
      const { getBridgeNonce, getLatestOpenCodeSession, getOpenCodeSessionForConnection, getOpenCodeSessionFor } = await import('./mcp-context')
      const { getCachedHybridSession } = await import('./hybrid-session')
      const connId = (arguments_ as any).connectionId as string | undefined
      let ctx = null
      if (opts?.sessionId) {
        const ocSid = getOpenCodeSessionFor(opts.sessionId)
        if (ocSid) ctx = { dataTalkSessionId: opts.sessionId, openCodeSessionId: ocSid }
      }
      if (!ctx) {
        const cached = getCachedHybridSession()
        if (cached) {
          ctx = cached
        } else {
          ctx = connId ? (getOpenCodeSessionForConnection(connId) ?? getLatestOpenCodeSession()) : getLatestOpenCodeSession()
        }
      }
      const nonce = getBridgeNonce()
      const args: Record<string, unknown> = { ...arguments_ }
      if (ctx && nonce) {
        args.__dtOpenCodeSessionId = ctx.openCodeSessionId
        args.__dtCallId = `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
        args.__dtBridgeNonce = nonce
      }

      const res = await request.post(`${BASE}/mcp`, {
        data: {
          jsonrpc: '2.0',
          id: 1,
          method: 'tools/call',
          params: { name: mcpToolName, arguments: args },
        },
        timeout: opts?.timeout ?? 30_000,
      })
      const json = await res.json() as McpRpcResponse

      // Unwrap McpToolResult: backend wraps action output in { content, structuredContent, isError }.
      // Prefer structuredContent; fall back to parsing the first text content block.
      // When isError=true, surface the parsed content as an rpc.error so tests can assert uniformly.
      if (json.result && !json.error) {
        const result = json.result as any
        if (result.isError === true && result.content && Array.isArray(result.content) && result.content.length > 0) {
          try {
            const parsed = JSON.parse(result.content[0].text)
            if (parsed.message || parsed.code) {
              return {
                ...json,
                result: undefined,
                error: {
                  code: parsed.code ?? -32603,
                  message: parsed.message ?? 'tool execution error',
                },
              }
            }
            return { ...json, result: parsed }
          } catch { /* fall through */ }
        }
        if (result.structuredContent) {
          return { ...json, result: result.structuredContent as any }
        }
        if (result.content && Array.isArray(result.content) && result.content.length > 0) {
          try {
            const parsed = JSON.parse(result.content[0].text)
            return { ...json, result: parsed }
          } catch { /* fall through */ }
        }
      }
      return json
    },

    // ── Sessions ──
    createSession: (body?: { connectionId?: string; title?: string }) =>
      request.post(`${BASE}/api/sessions`, { data: body ?? {} }),

    listSessions: () => request.get(`${BASE}/api/sessions`),

    getSession: (id: string) => request.get(`${BASE}/api/sessions/${id}`),

    // ── Session Data Context ──
    getDataContext: (sessionId: string) =>
      request.get(`${BASE}/api/sessions/${sessionId}/data-context`),

    setDataContext: (sessionId: string, body: Record<string, unknown>) =>
      request.put(`${BASE}/api/sessions/${sessionId}/data-context`, { data: body }),

    resolveUseTarget: (sessionId: string, target: string) =>
      request.post(`${BASE}/api/sessions/${sessionId}/data-context/resolve-use`, {
        data: { target },
      }),

    listConnectionTargets: (sessionId: string, connectionId?: string) =>
      request.get(`${BASE}/api/sessions/${sessionId}/data-context/targets`, {
        params: connectionId ? { connectionId } : undefined,
      }),

    validateDataContext: (sessionId: string) =>
      request.post(`${BASE}/api/sessions/${sessionId}/data-context/validate`),

    // ── Connections ──
    listConnections: () => request.get(`${BASE}/api/connections`),

    createConnection: (body: Record<string, unknown>) =>
      request.post(`${BASE}/api/connections`, { data: body }),

    testConnection: (id: string) => request.post(`${BASE}/api/connections/${id}/test`),

    updateConnection: (id: string, body: Record<string, unknown>) =>
      request.put(`${BASE}/api/connections/${id}`, { data: body }),

    deleteConnection: (id: string) => request.delete(`${BASE}/api/connections/${id}`),

    // ── Stage / Tabs ──
    stageListTabs: (params?: Record<string, string | number | boolean>) =>
      request.get(`${BASE}/api/stage/tabs`, { params }),

    stageFind: (body: Record<string, unknown>) =>
      request.post(`${BASE}/api/stage/find`, { data: body }),

    stageUpsertTab: (id: string, body: Record<string, unknown>) =>
      request.put(`${BASE}/api/stage/tabs/${id}`, { data: body }),

    stageGetPayload: (id: string) => request.get(`${BASE}/api/stage/tabs/${id}/payload`),

    stageArchive: (id: string, archived: boolean) =>
      request.patch(`${BASE}/api/stage/tabs/${id}/archive`, { data: { archived } }),

    stageDelete: (id: string) => request.delete(`${BASE}/api/stage/tabs/${id}`),

    stagePayloadBeacon: (id: string, body: Record<string, unknown>) =>
      request.post(`${BASE}/api/stage/tabs/${id}/payload-beacon`, { data: body }),

    // ── SQL ──
    executeSql: (body: Record<string, unknown>) =>
      request.post(`${BASE}/api/sql/execute`, { data: body }),

    // ── Diagnostics ──
    explainQuery: (sessionId: string, sql: string) =>
      request.post(`${BASE}/api/sessions/${sessionId}/diagnostics/explain`, {
        data: { sql },
      }),

    indexHints: (sessionId: string, sql: string) =>
      request.post(`${BASE}/api/sessions/${sessionId}/diagnostics/index-hints`, {
        data: { sql },
      }),

    // ── ER ──
    erSeedInspector: (body: Record<string, unknown>) =>
      request.post(`${BASE}/api/er/seed-inspector`, { data: body }),

    erGenerateDdl: (body: Record<string, unknown>) =>
      request.post(`${BASE}/api/er/generate-ddl`, { data: body }),

    erDiff: (body: Record<string, unknown>) =>
      request.post(`${BASE}/api/er/diff`, { data: body }),

    erSyncFromDb: (body: Record<string, unknown>) =>
      request.post(`${BASE}/api/er/sync-from-db`, { data: body }),

    // ── Artifacts ──
    renderChart: (sessionId: string, body: Record<string, unknown>) =>
      request.post(`${BASE}/api/sessions/${sessionId}/artifacts/chart`, { data: body }),

    // ── Dashboard ──
    dashboardPromote: (body: unknown) =>
      request.post(`${BASE}/api/dashboards/promote`, { data: body }),

    dashboardGet: (dashboardId: string) =>
      request.get(`${BASE}/api/dashboards/${dashboardId}`),

    dashboardPatch: (dashboardId: string, body: unknown) =>
      request.patch(`${BASE}/api/dashboards/${dashboardId}`, { data: body }),
  }
}
