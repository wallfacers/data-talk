import type { UIObject, ActionDef, ExecResult, PatchResult } from '@/services/ui-router'
import { execError } from '@/services/ui-router'
import { useConnectionStore } from '@/features/connection/store'
import { useDataSourcePickerStore } from '@/features/session/data-source-picker/data-source-picker-store'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import type { ErInspectorPayload, ErTableSnapshot } from '@/features/stage/stores/er-tabs-payload-types'
import { useStageStore, type StageTab } from '@/stores/stage-store'
import { normalizeQueryEditorPayload } from '@/features/stage/utils/normalize-query-editor-payload'
import { generateUuid } from '@/lib/uuid'
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
  { name: 'open_er_inspector', description: 'Open a read-only ER inspector tab from selected tables', paramsSchema: {
    type: 'object',
    required: ['connectionId', 'tables'],
    properties: {
      connectionId: { type: 'string' },
      connection_id: { type: 'string' },
      tables: { type: 'array' },
      neighborDepth: { type: 'number' },
      database: { type: 'string' },
      schema: { type: 'string' },
      title: { type: 'string' },
    },
  } },
]

const WORKSPACE_SCOPE_TYPES = new Set<string>(['er_canvas', 'markdown_note', 'report', 'dashboard'])

interface SeedInspectorResponse {
  nodes: ErTableSnapshot[]
  edges: unknown[]
  summary: string
  warnings: string[]
}

type OpenErInspectorParams = {
  connectionId?: string
  connection_id?: string
  tables?: unknown
  neighborDepth?: unknown
  database?: string
  schema?: string
  title?: string
}

function normalizeNeighborDepth(value: unknown): 0 | 1 | 2 {
  return value === 0 || value === 1 || value === 2 ? value : 1
}

function tableNames(nodes: ErTableSnapshot[]): string[] {
  return nodes.map((node) => node.name)
}

async function fetchSeedInspector(request: {
  connectionId: string
  tables: string[]
  neighborDepth: 0 | 1 | 2
}): Promise<SeedInspectorResponse> {
  const response = await fetch('/api/er/seed-inspector', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    const detail = await response.json().catch(() => ({})) as { code?: string; message?: string; aiHint?: string }
    throw Object.assign(new Error(detail.message ?? `seed-inspector failed: ${response.status}`), {
      code: detail.code,
      aiHint: detail.aiHint,
    })
  }
  return response.json() as Promise<SeedInspectorResponse>
}

function toExecError(error: unknown): ExecResult {
  const detail = error as { code?: string; message?: string; aiHint?: string }
  if (detail.code && detail.message) {
    return execError({
      code: detail.code,
      message: detail.message,
      hint: detail.aiHint,
    })
  }
  return execError(error instanceof Error ? error.message : 'ER inspector request failed')
}

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
      case 'open_er_inspector': {
        const input = (params ?? {}) as OpenErInspectorParams
        const connectionId = input.connectionId ?? input.connection_id
        if (!connectionId) return execError('Missing param: connectionId')
        const tables = Array.isArray(input.tables)
          ? input.tables.filter((table): table is string => typeof table === 'string' && table.length > 0)
          : []
        if (tables.length === 0) return execError('Missing param: tables')
        const neighborDepth = normalizeNeighborDepth(input.neighborDepth)

        try {
          const graph = await fetchSeedInspector({ connectionId, tables, neighborDepth })
          const tabId = `er_inspector_${generateUuid()}`
          const selection = tableNames(graph.nodes)
          const payload: ErInspectorPayload = {
            kind: 'er_inspector',
            connectionId,
            database: input.database ?? null,
            schema: input.schema ?? null,
            selection,
            neighborDepth,
            layout: 'dagre-LR',
            tablesSnapshot: graph.nodes,
            snapshotAt: Date.now(),
            positions: {},
            collapsed: [],
            virtualRelations: [],
            notes: {},
            viewport: { x: 0, y: 0, zoom: 1 },
          }
          const tab: StageTab = {
            tabId,
            type: 'er_inspector',
            title: input.title ?? `ER: ${tables.join(', ')}`,
            connectionId,
            database: input.database,
            schema: input.schema,
            payload,
            createdAt: Date.now(),
          }
          store.openTab(tab)
          store.openStage()
          useErTabsStore.getState().hydrateInspector(tabId, payload)
          return {
            success: true,
            data: { tabId, summary: graph.summary, edges: graph.edges.length, tables: selection, warnings: graph.warnings },
          }
        } catch (error) {
          return toExecError(error)
        }
      }
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
