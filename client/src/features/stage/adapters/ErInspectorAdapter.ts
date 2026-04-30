import type { ActionDef, ExecResult, JsonPatchOp, PatchCapability, PatchResult, UIObject } from '@/services/ui-router'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import type { ErDesignerPayload, ErInspectorPayload, ErTableSnapshot } from '@/features/stage/stores/er-tabs-payload-types'

interface SeedInspectorResponse {
  nodes: ErTableSnapshot[]
  edges: unknown[]
  summary: string
  warnings: string[]
}

const PATCH_CAPABILITIES: PatchCapability[] = [
  { pathPattern: '/selection', ops: ['replace'] },
  { pathPattern: '/neighborDepth', ops: ['replace'] },
  { pathPattern: '/positions', ops: ['replace'] },
  { pathPattern: '/positions/<table>', ops: ['replace', 'remove'] },
  { pathPattern: '/collapsed', ops: ['replace'] },
  { pathPattern: '/virtualRelations', ops: ['replace'] },
  { pathPattern: '/virtualRelations/-', ops: ['add'] },
  { pathPattern: '/virtualRelations[id=<id>]', ops: ['replace', 'remove'] },
  { pathPattern: '/notes', ops: ['replace'] },
  { pathPattern: '/notes/<table>', ops: ['replace', 'remove'] },
  { pathPattern: '/viewport', ops: ['replace'] },
]

const ACTIONS: ActionDef[] = [
  {
    name: 'refresh',
    description: 'Re-read tables from the connection',
    paramsSchema: { type: 'object', properties: {} },
  },
  {
    name: 'auto_layout',
    description: 'Recompute node positions via dagre',
    paramsSchema: { type: 'object', properties: {} },
  },
  {
    name: 'fit_view',
    description: 'Reset viewport to fit all nodes',
    paramsSchema: { type: 'object', properties: {} },
  },
  {
    name: 'add_neighbors',
    description: 'Pull direct FK neighbors of a table into selection',
    paramsSchema: {
      type: 'object',
      required: ['table'],
      properties: { table: { type: 'string' } },
    },
  },
  {
    name: 'fork_to_designer',
    description: 'Create a designer tab seeded from this inspector',
    paramsSchema: { type: 'object', properties: {} },
  },
]

async function fetchSeedInspector(request: {
  connectionId: string
  tables: string[]
  neighborDepth: number
}): Promise<SeedInspectorResponse> {
  const response = await fetch('/api/er/seed-inspector', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    const detail = await response.json().catch(() => ({})) as { message?: string; code?: string; aiHint?: string }
    const error = new Error(detail.message ?? `seed-inspector failed: ${response.status}`)
    Object.assign(error, { code: detail.code, aiHint: detail.aiHint })
    throw error
  }
  return response.json() as Promise<SeedInspectorResponse>
}

function tableNames(nodes: ErTableSnapshot[]): string[] {
  return nodes.map((node) => node.name)
}

function hydrateSnapshot(tabId: string, payload: ErInspectorPayload, graph: SeedInspectorResponse): void {
  useErTabsStore.getState().hydrateInspector(tabId, {
    ...payload,
    selection: tableNames(graph.nodes),
    tablesSnapshot: graph.nodes,
    snapshotAt: Date.now(),
  })
}

export class ErInspectorAdapter implements UIObject {
  type = 'er_inspector'
  patchCapabilities = PATCH_CAPABILITIES
  objectId: string
  tabId: string
  title = 'ER Diagram Viewer'

  constructor(tabId: string, _sessionIdGetter: () => string | null) {
    this.objectId = tabId
    this.tabId = tabId
  }

  read(mode: 'state' | 'schema' | 'actions' | 'full'): unknown {
    const payload = useErTabsStore.getState().inspectors.get(this.tabId) ?? null
    if (mode === 'state') return payload
    if (mode === 'schema') {
      return {
        type: this.type,
        patchCapabilities: this.patchCapabilities,
      }
    }
    if (mode === 'actions') return ACTIONS
    return {
      state: payload,
      schema: { type: this.type, patchCapabilities: this.patchCapabilities },
      actions: ACTIONS,
    }
  }

  patch(ops: JsonPatchOp[], _reason?: string): PatchResult {
    try {
      const { newVersion, assignedIds } = useErTabsStore.getState().applyInspectorPatch(this.tabId, ops)
      return {
        status: 'applied',
        message: `applied ${ops.length} op(s); new version ${newVersion}`,
        newVersion,
        assignedIds,
      }
    } catch (error) {
      return { status: 'error', message: (error as Error).message }
    }
  }

  async exec(action: string, params?: unknown): Promise<ExecResult> {
    const store = useErTabsStore.getState()
    const payload = store.inspectors.get(this.tabId)
    if (!payload) return { success: false, error: `tab not found: ${this.tabId}` }

    try {
      switch (action) {
        case 'refresh': {
          const graph = await fetchSeedInspector({
            connectionId: payload.connectionId,
            tables: payload.selection,
            neighborDepth: payload.neighborDepth,
          })
          hydrateSnapshot(this.tabId, payload, graph)
          return { success: true, data: { summary: graph.summary, edges: graph.edges.length, warnings: graph.warnings } }
        }

        case 'auto_layout': {
          const { computeDagreLayout } = await import('@/features/stage/components/er-canvas/workers/dagre-layout.worker')
          const positions = computeDagreLayout({
            nodes: (payload.tablesSnapshot ?? []).map((table) => ({
              id: table.name,
              width: 288,
              height: 40 + table.columns.length * 28,
            })),
            edges: (payload.tablesSnapshot ?? []).flatMap((table) =>
              table.fkOut.map((fk) => ({ source: table.name, target: fk.toTable })),
            ),
            config: { rankdir: 'LR', nodesep: 80, ranksep: 200 },
          })
          store.applyInspectorPatch(this.tabId, [{ op: 'replace', path: '/positions', value: positions }])
          return { success: true, data: { positions } }
        }

        case 'fit_view': {
          store.applyInspectorPatch(this.tabId, [{ op: 'replace', path: '/viewport', value: { x: 0, y: 0, zoom: 1 } }])
          return { success: true }
        }

        case 'add_neighbors': {
          const table = (params as { table?: unknown } | undefined)?.table
          if (typeof table !== 'string' || table.length === 0) {
            return { success: false, error: 'add_neighbors requires { table }' }
          }
          const tables = Array.from(new Set([...(payload.selection ?? []), table]))
          const graph = await fetchSeedInspector({
            connectionId: payload.connectionId,
            tables,
            neighborDepth: payload.neighborDepth,
          })
          hydrateSnapshot(this.tabId, payload, graph)
          return { success: true, data: { addedTables: tableNames(graph.nodes), summary: graph.summary } }
        }

        case 'fork_to_designer':
          return this.forkToDesigner(payload, params)

        default:
          return { success: false, error: `unknown action: ${action}` }
      }
    } catch (error) {
      return { success: false, error: (error as Error).message }
    }
  }

  private async forkToDesigner(payload: ErInspectorPayload, params?: unknown): Promise<ExecResult> {
    const { WorkspaceAdapter } = await import('./WorkspaceAdapter')
    const input = (params ?? {}) as { title?: string; dialect?: ErDesignerPayload['dialect'] }
    const seedTables = (payload.tablesSnapshot ?? []).map((table) => ({
      name: table.name,
      comment: table.comment ?? null,
      columns: table.columns.map((column) => ({
        name: column.name,
        type: column.type,
        nullable: column.nullable,
        isPrimaryKey: column.isPK,
        isAutoIncrement: column.isAutoIncrement ?? false,
        default: column.default ?? null,
        comment: column.comment ?? null,
      })),
    }))
    const seedRelations = (payload.tablesSnapshot ?? []).flatMap((table) =>
      (table.fkOut ?? []).map((relation) => ({
        fromTable: table.name,
        fromColumn: relation.fromColumn,
        toTable: relation.toTable,
        toColumn: relation.toColumn,
        type: 'many_to_one',
        constraintMethod: 'database_fk',
      })),
    )
    const workspace = new WorkspaceAdapter(() => null)
    const result = await workspace.exec('open_er_designer', {
      dialect: input.dialect ?? 'mysql',
      title: input.title ?? `Fork: ${payload.selection[0] ?? 'ER'}`,
      targetConnectionId: payload.connectionId,
      targetDatabase: payload.database ?? null,
      targetSchema: payload.schema ?? null,
      seedTables,
      seedRelations,
    })
    if (!result.success) return result
    const tabId = (result.data as { tabId?: string }).tabId
    return { success: true, data: { ...(result.data as Record<string, unknown>), newTabId: tabId } }
  }
}
