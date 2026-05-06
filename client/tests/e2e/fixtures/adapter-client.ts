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
    mcpCall: async (name: string, arguments_: Record<string, unknown>): Promise<McpRpcResponse> => {
      const res = await request.post(`${BASE}/mcp`, {
        data: {
          jsonrpc: '2.0',
          id: 1,
          method: 'tools/call',
          params: { name, arguments: arguments_ },
        },
      })
      return res.json() as Promise<McpRpcResponse>
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
  }
}
