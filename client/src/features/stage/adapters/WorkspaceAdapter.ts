import type { UIObject, ActionDef, ExecResult, PatchResult } from '@/services/ui-router'
import { execError } from '@/services/ui-router'
import { useDataSourcePickerStore } from '@/features/session/data-source-picker/data-source-picker-store'
import { useStageStore, type StageTab } from '@/stores/stage-store'

const ACTIONS: ActionDef[] = [
  { name: 'open', description: 'Open a new tab', paramsSchema: {
    type: 'object', required: ['type'],
    properties: {
      type: { type: 'string' },
      title: { type: 'string' },
      connection_id: { type: 'string' },
      database: { type: 'string' },
      schema: { type: 'string' },
      payload: { type: 'object' },
    },
  } },
  { name: 'close', description: 'Close a tab', paramsSchema: {
    type: 'object', required: ['target'], properties: { target: { type: 'string' } },
  } },
  { name: 'focus', description: 'Focus a tab', paramsSchema: {
    type: 'object', required: ['target'], properties: { target: { type: 'string' } },
  } },
  { name: 'choose_connection', description: 'Prompt user to choose a data source', paramsSchema: {
    type: 'object', properties: { preferredConnectionId: { type: 'string' } },
  } },
]

// workspace-level Tab 默认 scope 注册表：允许新增类型时不改 WorkspaceAdapter
const WORKSPACE_SCOPE_TYPES = new Set<string>(['bang_query', 'query_editor', 'er_canvas', 'markdown_note'])

export class WorkspaceAdapter implements UIObject {
  type = 'workspace'
  objectId = 'workspace'
  title = 'Workspace'

  constructor(private getSessionId: () => string | null) {}

  read(mode: 'state' | 'schema' | 'actions' | 'full'): unknown {
    switch (mode) {
      case 'state': {
        const sid = this.getSessionId()
        const tabs = useStageStore.getState().listTabs(sid)
        const activeTabId = sid
          ? useStageStore.getState().activeTabIdBySession.get(sid) ?? useStageStore.getState().activeWorkspaceTabId
          : useStageStore.getState().activeWorkspaceTabId
        return { tabs: tabs.map((t) => ({ tabId: t.tabId, type: t.type, title: t.title, connectionId: t.connectionId })), activeTabId }
      }
      case 'actions': return ACTIONS
      case 'schema': return { type: 'object', properties: { tabs: { type: 'array' }, activeTabId: { type: ['string', 'null'] } } }
      case 'full': return { state: this.read('state'), actions: ACTIONS, schema: this.read('schema') }
    }
  }

  patch(): PatchResult { return { status: 'error', message: 'workspace is read-only; use exec' } }

  async exec(action: string, params?: unknown): Promise<ExecResult> {
    const p = (params ?? {}) as {
      type?: string
      title?: string
      connection_id?: string
      database?: string
      schema?: string
      payload?: unknown
      target?: string
      preferredConnectionId?: string
    }
    const store = useStageStore.getState()
    switch (action) {
      case 'open': {
        if (!p.type) return execError('Missing param: type')
        const sid = this.getSessionId()
        const tabId = `${p.type}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
        const scope: StageTab['scope'] = WORKSPACE_SCOPE_TYPES.has(p.type) ? 'workspace' : 'session'
        const tab: StageTab = {
          tabId, type: p.type, title: p.title ?? p.type, scope,
          connectionId: p.connection_id, database: p.database, schema: p.schema,
          originSessionId: sid ?? undefined,
          payload: p.payload ?? {}, createdAt: Date.now(),
        }
        if (scope === 'session' && !sid) return execError('Cannot open session-scoped tab without active session')
        store.openTab(tab)
        return { success: true, data: { tabId } }
      }
      case 'close': {
        if (!p.target) return execError('Missing param: target')
        store.closeTab(p.target)
        return { success: true }
      }
      case 'focus': {
        if (!p.target) return execError('Missing param: target')
        store.focusTab(p.target)
        return { success: true }
      }
      case 'choose_connection': {
        const result = await useDataSourcePickerStore.getState().requestPick({
          reason: 'ui_exec',
          preferredConnectionId: p.preferredConnectionId ?? null,
        })
        return { success: true, data: result }
      }
      default: return execError(`Unknown action: ${action}`, `Available: [${ACTIONS.map((a) => a.name).join(', ')}]`)
    }
  }
}
