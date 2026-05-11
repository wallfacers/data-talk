import type { UIObject, ActionDef, ExecResult, PatchResult } from '@/services/ui-router'
import { execError } from '@/services/ui-router'
import { useConnectionStore } from '@/features/connection/store'
import { useDataSourcePickerStore } from '@/features/session/data-source-picker/data-source-picker-store'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import type {
  ErDesignerIndexDraft,
  ErDesignerPayload,
  ErDesignerRelationDraft,
  ErDesignerTableDraft,
  ErDesignerUniqueDraft,
  ErInspectorPayload,
  ErTableSnapshot,
} from '@/features/stage/stores/er-tabs-payload-types'
import { useStageStore, type StageTab } from '@/stores/stage-store'
import { normalizeQueryEditorPayload } from '@/features/stage/utils/normalize-query-editor-payload'
import { translateMessage } from '@/i18n/messages'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
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
      payload: {
        type: 'object',
        properties: {
          initialSql: { type: 'string' },
          content: { type: 'string' },
          sql: { type: 'string' },
          autoRun: { type: 'boolean' },
          connectionId: { type: 'string' },
          connectionName: { type: 'string' },
          database: { type: 'string' },
          schema: { type: 'string' },
        },
      },
    },
  } },
  { name: 'detach', description: 'Remove a tab from the workset without archiving it', paramsSchema: {
    type: 'object', required: ['target'], properties: { target: { type: 'string' } },
  } },
  { name: 'archive', description: 'Archive or unarchive a tab', paramsSchema: {
    type: 'object',
    anyOf: [{ required: ['target'] }, { required: ['targets'] }],
    properties: {
      target: { type: 'string' },
      targets: { type: 'array', items: { type: 'string' } },
      archived: { type: 'boolean', default: true },
    },
  } },
  { name: 'trash', description: 'Permanently delete a tab', paramsSchema: {
    type: 'object',
    anyOf: [{ required: ['target'] }, { required: ['targets'] }],
    properties: {
      target: { type: 'string' },
      targets: { type: 'array', items: { type: 'string' } },
    },
  } },
  { name: 'rename', description: 'Rename a tab', paramsSchema: {
    type: 'object',
    required: ['target', 'title'],
    properties: {
      target: { type: 'string' },
      title: { type: 'string' },
    },
  } },
  { name: 'pin', description: 'Pin or unpin a tab', paramsSchema: {
    type: 'object',
    required: ['target'],
    properties: {
      target: { type: 'string' },
      pinned: { type: 'boolean', default: true },
    },
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
  { name: 'open_er_designer', description: 'Open an ER designer tab for schema drafting', paramsSchema: {
    type: 'object',
    required: ['dialect'],
    properties: {
      dialect: { type: 'string' },
      title: { type: 'string' },
      targetConnectionId: { type: 'string' },
      targetDatabase: { type: 'string' },
      targetSchema: { type: 'string' },
      seedTables: { type: 'array' },
      seedRelations: { type: 'array' },
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

type SupportedDesignerDialect = ErDesignerPayload['dialect']
type DesignerRelationType = ErDesignerRelationDraft['type']
type DesignerConstraintMethod = ErDesignerRelationDraft['constraintMethod']

type SeedDesignerColumn = {
  name?: unknown
  type?: unknown
  nullable?: unknown
  isPrimaryKey?: unknown
  isPK?: unknown
  isAutoIncrement?: unknown
  default?: unknown
  comment?: unknown
}

type SeedDesignerTable = {
  name?: unknown
  comment?: unknown
  columns?: unknown
}

type SeedDesignerRelation = {
  fromTable?: unknown
  fromTableId?: unknown
  fromColumn?: unknown
  fromColumnId?: unknown
  toTable?: unknown
  toTableId?: unknown
  toColumn?: unknown
  toColumnId?: unknown
  type?: unknown
  constraintMethod?: unknown
}

type OpenErDesignerParams = {
  dialect?: unknown
  title?: string
  targetConnectionId?: string | null
  targetDatabase?: string | null
  targetSchema?: string | null
  seedTables?: unknown
  seedRelations?: unknown
}

function normalizeNeighborDepth(value: unknown): 0 | 1 | 2 {
  return value === 0 || value === 1 || value === 2 ? value : 1
}

function isSupportedDesignerDialect(value: unknown): value is SupportedDesignerDialect {
  return value === 'mysql' || value === 'postgresql' || value === 'h2' || value === 'sqlite' || value === 'mariadb'
}

function createDesignerId(prefix: 't' | 'c' | 'r'): string {
  return `${prefix}_${generateUuid()}`
}

function isDesignerRelationType(value: unknown): value is DesignerRelationType {
  return value === 'one_to_one' || value === 'one_to_many' || value === 'many_to_one' || value === 'many_to_many'
}

function isDesignerConstraintMethod(value: unknown): value is DesignerConstraintMethod {
  return value === 'database_fk' || value === 'comment_ref'
}

function defaultErDesignerTitle(dialect: SupportedDesignerDialect): string {
  return `${translateMessage(getCurrentLanguage(), 'stage.toolRow.er')} (${dialect})`
}

function normalizeSeedTables(seedTables: unknown): {
  tables: ErDesignerTableDraft[]
  tableIdsByName: Map<string, string>
  columnIdsByTableAndName: Map<string, Map<string, string>>
} {
  const tables = Array.isArray(seedTables) ? seedTables as SeedDesignerTable[] : []
  const tableIdsByName = new Map<string, string>()
  const columnIdsByTableAndName = new Map<string, Map<string, string>>()
  const normalized = tables
    .filter((table) => typeof table.name === 'string' && table.name.length > 0)
    .map((table) => {
      const tableId = createDesignerId('t')
      const columnIds = new Map<string, string>()
      tableIdsByName.set(table.name as string, tableId)
      columnIdsByTableAndName.set(table.name as string, columnIds)
      const columns = (Array.isArray(table.columns) ? table.columns as SeedDesignerColumn[] : [])
        .filter((column) => typeof column.name === 'string' && column.name.length > 0)
        .map((column) => {
          const columnId = createDesignerId('c')
          columnIds.set(column.name as string, columnId)
          return {
            id: columnId,
            name: column.name as string,
            type: typeof column.type === 'string' && column.type.length > 0 ? column.type : 'VARCHAR(255)',
            nullable: typeof column.nullable === 'boolean' ? column.nullable : true,
            isPrimaryKey: column.isPrimaryKey === true || column.isPK === true,
            isAutoIncrement: column.isAutoIncrement === true,
            default: typeof column.default === 'string' ? column.default : null,
            comment: typeof column.comment === 'string' ? column.comment : null,
          }
        })
      return {
        id: tableId,
        name: table.name as string,
        comment: typeof table.comment === 'string' ? table.comment : null,
        columns,
        indexes: [] as ErDesignerIndexDraft[],
        uniques: [] as ErDesignerUniqueDraft[],
      }
    })
  return { tables: normalized, tableIdsByName, columnIdsByTableAndName }
}

function normalizeSeedRelations(
  seedRelations: unknown,
  tableIdsByName: Map<string, string>,
  columnIdsByTableAndName: Map<string, Map<string, string>>,
) : ErDesignerRelationDraft[] {
  if (!Array.isArray(seedRelations)) return []
  return (seedRelations as SeedDesignerRelation[]).flatMap((relation) => {
    const fromTableName = typeof relation.fromTable === 'string' ? relation.fromTable : null
    const toTableName = typeof relation.toTable === 'string' ? relation.toTable : null
    const fromTableId = typeof relation.fromTableId === 'string'
      ? relation.fromTableId
      : fromTableName ? tableIdsByName.get(fromTableName) : undefined
    const toTableId = typeof relation.toTableId === 'string'
      ? relation.toTableId
      : toTableName ? tableIdsByName.get(toTableName) : undefined
    const fromColumnId = typeof relation.fromColumnId === 'string'
      ? relation.fromColumnId
      : fromTableName && typeof relation.fromColumn === 'string'
        ? columnIdsByTableAndName.get(fromTableName)?.get(relation.fromColumn)
        : undefined
    const toColumnId = typeof relation.toColumnId === 'string'
      ? relation.toColumnId
      : toTableName && typeof relation.toColumn === 'string'
        ? columnIdsByTableAndName.get(toTableName)?.get(relation.toColumn)
        : undefined
    if (!fromTableId || !toTableId || !fromColumnId || !toColumnId) return []
    return [{
      id: createDesignerId('r'),
      fromTableId,
      fromColumnId,
      toTableId,
      toColumnId,
      type: isDesignerRelationType(relation.type) ? relation.type : 'many_to_one',
      constraintMethod: isDesignerConstraintMethod(relation.constraintMethod) ? relation.constraintMethod : 'database_fk',
    }]
  })
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
        const store = useStageStore.getState()
        const sid = this.getSessionId()
        const tabs = store.listTabs()
        return {
          open: store.open,
          maximized: store.maximized,
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
              connectionName: string | null
              database: string | null
              schema: string | null
              useSessionContext: boolean
              contextSource: 'session' | 'override' | 'tab'
              contextOverride: unknown
              limit: 10 | 100 | 1000 | null
            }

            return {
              tabId: t.tabId,
              type: t.type,
              title: t.title,
              connectionId: queryEditorState.connectionId,
              connectionName: queryEditorState.connectionName,
              database: queryEditorState.database,
              schema: queryEditorState.schema,
              useSessionContext: queryEditorState.useSessionContext,
              contextSource: queryEditorState.contextSource,
              contextOverride: queryEditorState.contextOverride,
              limit: queryEditorState.limit,
            }
          }),
          activeTabId: store.activeTabId,
        }
      }
      case 'actions': return ACTIONS
      case 'schema': return { type: 'object', properties: { open: { type: 'boolean' }, maximized: { type: 'boolean' }, tabs: { type: 'array' }, activeTabId: { type: ['string', 'null'] } } }
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
      targets?: string[]
      tabs?: unknown[]
      preferredConnectionId?: string
      archived?: boolean
      pinned?: boolean
    }
    const store = useStageStore.getState()
    switch (action) {
      case 'open_er_designer': {
        const input = (params ?? {}) as OpenErDesignerParams
        const dialect = input.dialect ?? 'mysql'
        if (!isSupportedDesignerDialect(dialect)) {
          const message = `dialect_unsupported: ER Designer does not support dialect: ${String(dialect)}`
          return {
            success: false,
            error: message,
            data: {
              code: 'dialect_unsupported',
              message,
              aiHint: 'Use query_editor to write dialect-specific DDL manually, then run it through normal confirmation.',
            },
          }
        }
        const tabId = `er_designer_${generateUuid()}`
        const { tables, tableIdsByName, columnIdsByTableAndName } = normalizeSeedTables(input.seedTables)
        const relations = normalizeSeedRelations(input.seedRelations, tableIdsByName, columnIdsByTableAndName)
        const payload: ErDesignerPayload = {
          kind: 'er_designer',
          dialect,
          targetConnectionId: input.targetConnectionId ?? null,
          targetDatabase: input.targetDatabase ?? null,
          targetSchema: input.targetSchema ?? null,
          tables,
          relations,
          positions: {},
          collapsed: [],
          viewport: { x: 0, y: 0, zoom: 1 },
        }
        const tab: StageTab = {
          tabId,
          type: 'er_designer',
          title: input.title ?? defaultErDesignerTitle(dialect),
          connectionId: input.targetConnectionId ?? undefined,
          database: input.targetDatabase ?? undefined,
          schema: input.targetSchema ?? undefined,
          payload,
          payloadVersion: 1,
          createdAt: Date.now(),
        }
        // Order matters: stage tab must be registered before erTabsStore is
        // updated. The erTabsStore subscriber in stage-persistence-bootstrap
        // looks up the stage tab via findTab(tabId) before scheduling a
        // content write — if the tab is missing, the write is silently
        // dropped, the payload never reaches the server, and the next
        // ensureHydrated() returns 404.
        store.openTab(tab)
        store.openStage()
        useErTabsStore.getState().hydrateDesigner(tabId, payload)
        const summary = `${tables.length === 0 ? 'blank designer' : `${tables.length} tables, ${relations.length} relations`} (${dialect}${payload.targetConnectionId ? `, target=${payload.targetConnectionId}` : ', no target'})`
        return { success: true, data: { tabId, newTabId: tabId, payloadVersion: 1, summary } }
      }
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
            title: input.title ?? `ER Diagram Viewer: ${tables.join(', ')}`,
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
        const batch = Array.isArray(p.tabs) && p.tabs.length > 0
        const items = batch ? p.tabs! : [p]
        const sid = this.getSessionId()
        const tabIds: string[] = []
        for (const raw of items) {
          const item = (raw ?? {}) as typeof p
          if (!item.type) return execError('Missing param: type')
          if (item.type === 'query_editor') {
            const payload = normalizeQueryEditorPayload(item.payload)
            const connectionId = item.connection_id ?? payload.connectionId ?? undefined
            const connectionName = item.connection_id && payload.connectionId !== item.connection_id
              ? undefined
              : payload.connectionName ?? undefined
            const database = item.database ?? payload.database ?? undefined
            const schema = item.schema ?? payload.schema ?? undefined
            const { tabId } = store.openQueryEditor({
              sessionId: sid,
              baseTitle: item.title ?? item.type,
              openMode: connectionId ? 'reuse_by_resource_context' : 'always_new',
              entryMode: 'ui_exec',
              initialContent: payload.initialSql,
              autoRun: payload.autoRun,
              connectionId,
              connectionName,
              database,
              schema,
            })
            tabIds.push(tabId)
          } else {
            const tabId = `${item.type}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
            const isWorkspaceScoped = WORKSPACE_SCOPE_TYPES.has(item.type)
            if (!isWorkspaceScoped && !sid) return execError('Cannot open session-scoped tab without active session')
            const tab: StageTab = {
              tabId, type: item.type, title: item.title ?? item.type,
              connectionId: item.connection_id, database: item.database, schema: item.schema,
              originSessionId: sid ?? undefined,
              payload: item.payload ?? {},
              createdAt: Date.now(),
            }
            store.openTab(tab)
            tabIds.push(tabId)
          }
        }
        store.openStage()
        return { success: true, data: batch ? { tabIds } : { tabId: tabIds[0] } }
      }
      case 'detach': {
        if (!p.target) return execError('Missing param: target')
        store.detachFromWorkset(p.target)
        return { success: true }
      }
      case 'archive': {
        const archiveTargets = p.targets ?? (p.target ? [p.target] : null)
        if (!archiveTargets) return execError('Missing param: target or targets')
        const succeeded: string[] = []
        for (const t of archiveTargets) {
          if (store.findTab(t)) {
            store.archiveTab(t, p.archived ?? true)
            succeeded.push(t)
          }
        }
        return { success: true, data: { succeeded } }
      }
      case 'trash': {
        const trashTargets = p.targets ?? (p.target ? [p.target] : null)
        if (!trashTargets) return execError('Missing param: target or targets')
        const succeeded: string[] = []
        const failed: { target: string; error: string }[] = []
        for (const t of trashTargets) {
          try {
            await store.trashTab(t)
            succeeded.push(t)
          } catch (e) {
            failed.push({ target: t, error: e instanceof Error ? e.message : String(e) })
          }
        }
        if (failed.length === 0) return { success: true, data: { succeeded } }
        return { success: succeeded.length > 0, data: { succeeded, failed } }
      }
      case 'rename': {
        if (!p.target) return execError('Missing param: target')
        if (!p.title) return execError('Missing param: title')
        const renameTab = store.findTab(p.target)
        if (!renameTab) return execError({ code: 'tab_not_found', message: `Tab not found: ${p.target}` })
        store.setTabTitle(p.target, p.title)
        return { success: true }
      }
      case 'pin': {
        if (!p.target) return execError('Missing param: target')
        const pinTab = store.findTab(p.target)
        if (!pinTab) return execError({ code: 'tab_not_found', message: `Tab not found: ${p.target}` })
        store.setTabPinned(p.target, p.pinned ?? true)
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
        store.openStage()
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
