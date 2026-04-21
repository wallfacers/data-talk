import type { UIObject, JsonPatchOp, PatchResult, ExecResult, ActionDef, PatchCapability } from '@/services/ui-router'
import { execError } from '@/services/ui-router'
import { applyPatch } from '@/services/ui-router'
import { useStageStore } from '@/stores/stage-store'
import { executeQuery as defaultExecuteQuery } from '@/services/api/query'
import { resolveTabDataContext } from '@/features/stage/utils/resolve-tab-data-context'

const CAPS: PatchCapability[] = [
  { pathPattern: '/pinned', ops: ['replace'], description: 'Pin or unpin this tab' },
]

const ACTIONS: ActionDef[] = [
  { name: 'rerun', description: 'Re-run the SQL', paramsSchema: { type: 'object', properties: {} } },
  { name: 'focus', description: 'Focus this tab', paramsSchema: { type: 'object', properties: {} } },
  { name: 'close', description: 'Close this tab', paramsSchema: { type: 'object', properties: {} } },
]

interface BangPayload {
  sql: string
  rows?: Array<Record<string, unknown>>
  lastRun?: { columns: string[]; rowCount: number; durationMs: number; truncated: boolean }
  contextNotice?: string | null
}

export class BangQueryAdapter implements UIObject {
  type = 'bang_query'
  objectId: string
  title: string
  connectionId?: string
  patchCapabilities = CAPS

  constructor(
    tabId: string,
    private deps: { executeQuery?: typeof defaultExecuteQuery } = {},
  ) {
    this.objectId = tabId
    const tab = useStageStore.getState().workspaceTabs.find((t) => t.tabId === tabId)
    this.title = tab?.title ?? 'Bang Query'
    this.connectionId = tab?.connectionId
  }

  private getTab() {
    return useStageStore.getState().workspaceTabs.find((t) => t.tabId === this.objectId)
  }

  read(mode: 'state' | 'schema' | 'actions' | 'full'): unknown {
    const tab = this.getTab()
    switch (mode) {
      case 'state': {
        const payload = (tab?.payload ?? {}) as BangPayload
        return {
          sql: payload.sql,
          connectionId: tab?.connectionId,
          connectionName: tab?.connectionName,
          database: tab?.database,
          schema: tab?.schema,
          lastRun: payload.lastRun,
          pinned: tab?.pinned ?? false,
          // 故意不暴露 rows：结果行不进 AI 上下文
        }
      }
      case 'actions': return ACTIONS
      case 'schema': return { type: 'object', properties: { sql: { type: 'string' }, pinned: { type: 'boolean' } }, patchCapabilities: CAPS }
      case 'full': return { state: this.read('state'), actions: ACTIONS, schema: this.read('schema') }
    }
  }

  patch(ops: JsonPatchOp[]): PatchResult {
    const tab = this.getTab()
    if (!tab) return { status: 'error', message: 'Tab not found' }
    try {
      const next = applyPatch({ pinned: tab.pinned ?? false }, ops)
      useStageStore.setState((s) => {
        const i = s.workspaceTabs.findIndex((t) => t.tabId === this.objectId)
        if (i < 0) return s
        const arr = [...s.workspaceTabs]
        arr[i] = { ...arr[i], pinned: next.pinned }
        return { workspaceTabs: arr }
      })
      return { status: 'applied' }
    } catch (e) {
      return { status: 'error', message: String(e) }
    }
  }

  async exec(action: string): Promise<ExecResult> {
    const tab = this.getTab()
    if (!tab) return execError('Tab not found')
    const store = useStageStore.getState()
    switch (action) {
      case 'rerun': {
        const payload = tab.payload as BangPayload
        if (!tab.connectionId) return execError('Tab has no connectionId')
        const run = this.deps.executeQuery ?? defaultExecuteQuery
        const resolved = resolveTabDataContext(
          {
            originSessionId: tab.originSessionId ?? null,
            connectionId: tab.connectionId ?? null,
            connectionName: tab.connectionName ?? null,
            database: tab.database ?? null,
            schema: tab.schema ?? null,
          },
          null,
          { inheritSessionContext: false },
        )
        const result = await run({
          connectionId: resolved.connectionId ?? tab.connectionId,
          sql: payload.sql,
          sessionId: resolved.sessionId ?? undefined,
          database: resolved.database,
          schema: resolved.schema,
        })
        store.updateTabPayload(this.objectId, () => ({
          sql: payload.sql,
          rows: result.rows,
          contextNotice: result.contextNotice ?? null,
          lastRun: { columns: result.columns, rowCount: result.rowCount, durationMs: result.durationMs, truncated: result.rows.length < result.rowCount },
        }))
        useStageStore.setState((s) => {
          const i = s.workspaceTabs.findIndex((t) => t.tabId === this.objectId)
          if (i < 0) return s
          const next = [...s.workspaceTabs]
          next[i] = {
            ...next[i],
            connectionId: result.resolvedContext?.connectionId ?? next[i].connectionId,
            connectionName: result.resolvedContext?.connectionName ?? next[i].connectionName,
            database: result.resolvedContext?.database ?? next[i].database,
            schema: result.resolvedContext?.schema ?? next[i].schema,
          }
          return { workspaceTabs: next }
        })
        return { success: true, data: { rowCount: result.rowCount, durationMs: result.durationMs } }
      }
      case 'focus': store.focusTab(this.objectId); return { success: true }
      case 'close':  store.closeTab(this.objectId); return { success: true }
      default: return execError(`Unknown action: ${action}`)
    }
  }
}
