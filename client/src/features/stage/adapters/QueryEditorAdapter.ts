import type { ActionDef, ExecResult, PatchResult, UIObject } from '@/services/ui-router'
import { execError } from '@/services/ui-router'
import { useStageStore } from '@/stores/stage-store'
import { normalizeQueryEditorPayload } from '@/features/stage/utils/normalize-query-editor-payload'

const ACTIONS: ActionDef[] = [
  {
    name: 'focus',
    description: 'Focus this query editor',
    paramsSchema: { type: 'object', properties: {} },
  },
  {
    name: 'close',
    description: 'Close this query editor',
    paramsSchema: { type: 'object', properties: {} },
  },
]

function clearSessionActiveTab(sessionId: string | null) {
  if (!sessionId) return
  const activeTabIdBySession = new Map(useStageStore.getState().activeTabIdBySession)
  activeTabIdBySession.set(sessionId, null)
  useStageStore.setState({ activeTabIdBySession })
}

export class QueryEditorAdapter implements UIObject {
  type = 'query_editor'

  constructor(
    public objectId: string,
    private getSessionId: () => string | null = () => null,
  ) {}

  get tabId() {
    return this.objectId
  }

  get title() {
    return this.getTab()?.title ?? 'Query Editor'
  }

  get connectionId() {
    const tab = this.getTab()
    return normalizeQueryEditorPayload(tab?.payload).connectionId ?? tab?.connectionId
  }

  get database() {
    const tab = this.getTab()
    return normalizeQueryEditorPayload(tab?.payload).database ?? tab?.database
  }

  private getTab() {
    const state = useStageStore.getState()
    return state.workspaceTabs.find((tab) => tab.tabId === this.objectId)
      ?? Array.from(state.tabsBySession.values()).flat().find((tab) => tab.tabId === this.objectId)
      ?? null
  }

  read(mode: 'state' | 'schema' | 'actions' | 'full'): unknown {
    const tab = this.getTab()
    const payload = normalizeQueryEditorPayload(tab?.payload)
    const state = {
      sql: payload.initialSql,
      source: payload.source,
      entryMode: payload.entryMode,
      connectionId: payload.connectionId ?? tab?.connectionId ?? null,
      connectionName: payload.connectionName ?? tab?.connectionName ?? null,
      database: payload.database ?? tab?.database ?? null,
      schema: payload.schema ?? tab?.schema ?? null,
      lastRun: payload.lastRun,
      contextNotice: payload.contextNotice,
    }

    switch (mode) {
      case 'state':
        return state
      case 'actions':
        return ACTIONS
      case 'schema':
        return {
          type: 'object',
          properties: {
            sql: { type: 'string' },
            source: { type: 'string' },
            entryMode: { type: 'string' },
            connectionId: { type: ['string', 'null'] },
            connectionName: { type: ['string', 'null'] },
            database: { type: ['string', 'null'] },
            schema: { type: ['string', 'null'] },
            lastRun: { type: ['object', 'null'] },
            contextNotice: { type: ['string', 'null'] },
          },
        }
      case 'full':
        return { state, schema: this.read('schema'), actions: ACTIONS }
    }
  }

  patch(_ops: unknown[] = [], _reason?: string): PatchResult {
    return { status: 'error', message: 'query_editor is read-only; edit through the UI' }
  }

  async exec(action: string): Promise<ExecResult> {
    const store = useStageStore.getState()
    const tab = this.getTab()
    switch (action) {
      case 'focus':
        store.focusTab(this.objectId)
        if (tab?.scope === 'workspace') clearSessionActiveTab(this.getSessionId())
        return { success: true }
      case 'close':
        store.closeTab(this.objectId)
        return { success: true }
      default:
        return execError(`Unknown action: ${action}`, `Available: [${ACTIONS.map((item) => item.name).join(', ')}]`)
    }
  }
}
