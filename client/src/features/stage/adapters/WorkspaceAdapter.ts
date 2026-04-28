import type { UIObject, ActionDef, ExecResult, PatchResult } from '@/services/ui-router'
import { execError } from '@/services/ui-router'
import { useConnectionStore } from '@/features/connection/store'
import { useDataSourcePickerStore } from '@/features/session/data-source-picker/data-source-picker-store'
import { useStageStore, type StageTab } from '@/stores/stage-store'
import { normalizeQueryEditorPayload } from '@/features/stage/utils/normalize-query-editor-payload'
import { QueryEditorAdapter } from './QueryEditorAdapter'

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
  { name: 'detach', description: 'Remove a tab from the workset without archiving it', paramsSchema: {
    type: 'object', required: ['target'], properties: { target: { type: 'string' } },
  } },
  { name: 'archive', description: 'Archive or unarchive a tab', paramsSchema: {
    type: 'object',
    required: ['target'],
    properties: {
      target: { type: 'string' },
      archived: { type: 'boolean', default: true },
    },
  } },
  { name: 'trash', description: 'Permanently delete a tab', paramsSchema: {
    type: 'object', required: ['target'], properties: { target: { type: 'string' } },
  } },
  { name: 'focus', description: 'Focus a tab', paramsSchema: {
    type: 'object', required: ['target'], properties: { target: { type: 'string' } },
  } },
  { name: 'choose_connection', description: 'Prompt user to choose a data source for a database-related request', paramsSchema: {
    type: 'object', properties: { preferredConnectionId: { type: 'string' } },
  } },
]

const WORKSPACE_SCOPE_TYPES = new Set<string>(['er_canvas', 'markdown_note', 'report', 'dashboard'])

export class WorkspaceAdapter implements UIObject {
  type = 'workspace'
  objectId = 'workspace'
  title = 'Workspace'

  constructor(private getSessionId: () => string | null) {}

  read(mode: 'state' | 'schema' | 'actions' | 'full'): unknown {
    switch (mode) {
      case 'state': {
        const sid = this.getSessionId()
        const tabs = useStageStore.getState().listTabs()
        const activeTabId = useStageStore.getState().activeTabId
        return {
          tabs: tabs.map((t) => {
            if (t.type !== 'query_editor') {
              return {
                tabId: t.tabId,
                type: t.type,
                title: t.title,
                connectionId: t.connectionId,
                contextOverride: undefined,
              }
            }

            const queryEditorState = new QueryEditorAdapter(t.tabId, () => sid).read('state') as {
              connectionId: string | null
              contextOverride: unknown
            }

            return {
              tabId: t.tabId,
              type: t.type,
              title: t.title,
              connectionId: queryEditorState.connectionId,
              contextOverride: queryEditorState.contextOverride,
            }
          }),
          activeTabId,
        }
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
      archived?: boolean
    }
    const store = useStageStore.getState()
    switch (action) {
      case 'open': {
        if (!p.type) return execError('Missing param: type')
        const sid = this.getSessionId()
        if (p.type === 'query_editor') {
          const payload = normalizeQueryEditorPayload(p.payload)
          const connectionId = p.connection_id ?? payload.connectionId ?? undefined
          const connectionName = p.connection_id && payload.connectionId !== p.connection_id
            ? undefined
            : payload.connectionName ?? undefined
          const database = p.database ?? payload.database ?? undefined
          const schema = p.schema ?? payload.schema ?? undefined
          const { tabId } = store.openQueryEditor({
            sessionId: sid,
            baseTitle: p.title ?? p.type,
            openMode: connectionId ? 'reuse_by_resource_context' : 'always_new',
            entryMode: 'ui_exec',
            initialContent: payload.initialSql,
            autoRun: payload.autoRun,
            connectionId,
            connectionName,
            database,
            schema,
          })
          store.openStage()
          return { success: true, data: { tabId } }
        }

        const tabId = `${p.type}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
        const isWorkspaceScoped = WORKSPACE_SCOPE_TYPES.has(p.type)
        if (!isWorkspaceScoped && !sid) return execError('Cannot open session-scoped tab without active session')
        const tab: StageTab = {
          tabId, type: p.type, title: p.title ?? p.type,
          connectionId: p.connection_id, database: p.database, schema: p.schema,
          originSessionId: sid ?? undefined,
          payload: p.payload ?? {},
          createdAt: Date.now(),
        }
        store.openTab(tab)
        if (sid) store.openStage()
        return { success: true, data: { tabId } }
      }
      case 'detach': {
        if (!p.target) return execError('Missing param: target')
        store.detachFromWorkset(p.target)
        return { success: true }
      }
      case 'archive': {
        if (!p.target) return execError('Missing param: target')
        store.archiveTab(p.target, p.archived ?? true)
        return { success: true }
      }
      case 'trash': {
        if (!p.target) return execError('Missing param: target')
        await store.trashTab(p.target)
        return { success: true }
      }
      case 'focus': {
        if (!p.target) return execError('Missing param: target')
        const tab = store.findTab(p.target)
        if (!tab) return execError({ code: 'tab_not_found', message: `Tab not found: ${p.target}` })
        if (tab.archived) {
          return execError({
            code: 'tab_archived',
            message: 'The tab is archived. Unarchive it before focusing.',
            hint: `Call workspace.archive(target=${p.target}, archived=false) before focus.`,
          })
        }
        store.focusTab(p.target)
        return { success: true }
      }
      case 'choose_connection': {
        const result = await useDataSourcePickerStore.getState().requestPick({
          reason: 'ui_exec',
          preferredConnectionId: p.preferredConnectionId ?? null,
        })
        if (!('cancelled' in result)) {
          useConnectionStore.getState().setActive(result.connectionId)
        }
        return { success: true, data: result }
      }
      default: return execError(`Unknown action: ${action}`, `Available: [${ACTIONS.map((a) => a.name).join(', ')}]`)
    }
  }
}
