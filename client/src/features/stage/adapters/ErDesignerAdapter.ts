import type { ActionDef, ExecResult, JsonPatchOp, PatchCapability, PatchResult, UIObject } from '@/services/ui-router'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import type { ErDesignerPayload } from '@/features/stage/stores/er-tabs-payload-types'
import { useStageStore } from '@/stores/stage-store'

type DesignerTable = {
  id: string
  name: string
  columns: { id?: string; name: string; type: string }[]
}

type DesignerRelation = {
  id: string
  fromTableId: string
  toTableId: string
}

type DesignerPosition = { x: number; y: number }

const PATCH_CAPABILITIES: PatchCapability[] = [
  { pathPattern: '/tables/-', ops: ['add'] },
  { pathPattern: '/tables[id=<id>]', ops: ['replace', 'remove'] },
  { pathPattern: '/tables[id=<id>]/name', ops: ['replace'] },
  { pathPattern: '/tables[id=<id>]/comment', ops: ['replace'] },
  { pathPattern: '/tables[id=<id>]/columns/-', ops: ['add'] },
  { pathPattern: '/tables[id=<id>]/columns[id=<id>]', ops: ['replace', 'remove'] },
  { pathPattern: '/relations/-', ops: ['add'] },
  { pathPattern: '/relations[id=<id>]', ops: ['replace', 'remove'] },
  { pathPattern: '/positions', ops: ['replace'] },
  { pathPattern: '/positions/<table>', ops: ['replace', 'remove'] },
  { pathPattern: '/collapsed', ops: ['replace'] },
  { pathPattern: '/viewport', ops: ['replace'] },
  { pathPattern: '/dialect', ops: ['replace'] },
  { pathPattern: '/targetConnectionId', ops: ['replace'] },
  { pathPattern: '/targetDatabase', ops: ['replace'] },
  { pathPattern: '/targetSchema', ops: ['replace'] },
]

const ACTIONS: ActionDef[] = [
  { name: 'auto_layout', description: 'Recompute designer node positions', paramsSchema: { type: 'object', properties: {} } },
  { name: 'fit_view', description: 'Reset designer viewport', paramsSchema: { type: 'object', properties: {} } },
  {
    name: 'bind_target',
    description: 'Bind a target connection for diff and DDL generation',
    paramsSchema: {
      type: 'object',
      required: ['connectionId'],
      properties: { connectionId: { type: 'string' }, database: { type: 'string' }, schema: { type: 'string' } },
    },
  },
  { name: 'unbind_target', description: 'Clear the target connection binding', paramsSchema: { type: 'object', properties: {} } },
  {
    name: 'sync_from_db',
    description: 'Refresh designer payload from the bound database where available',
    paramsSchema: { type: 'object', properties: { tables: { type: 'array' } } },
  },
  { name: 'diff_against_db', description: 'Return the schema diff between designer draft and bound database', paramsSchema: { type: 'object', properties: {} } },
  {
    name: 'generate_ddl',
    description: 'Generate DDL into a new query_editor tab; does not execute SQL',
    paramsSchema: { type: 'object', properties: { includeDrops: { type: 'boolean' } } },
  },
]

function targetRequired(): ExecResult {
  return {
    success: false,
    error: 'target_required_for_apply: bind_target is required before diff_against_db, sync_from_db, or generate_ddl.',
  }
}

async function readError(response: Response, fallback: string): Promise<string> {
  const detail = await response.json().catch(() => ({})) as { aiHint?: string; message?: string }
  return detail.aiHint ?? detail.message ?? fallback
}

function asDesignerTables(payload: ErDesignerPayload): DesignerTable[] {
  return Array.isArray(payload.tables) ? payload.tables as DesignerTable[] : []
}

function asDesignerRelations(payload: ErDesignerPayload): DesignerRelation[] {
  return Array.isArray(payload.relations) ? payload.relations as DesignerRelation[] : []
}

function hydrateDesigner(tabId: string, payload: ErDesignerPayload): void {
  useErTabsStore.getState().hydrateDesigner(tabId, payload)
}

function mergeDesignerPositions(
  existingPositions: ErDesignerPayload['positions'],
  syncedTables: ErDesignerPayload['tables'],
): ErDesignerPayload['positions'] {
  const mergedPositions = { ...existingPositions }
  const existing = Object.values(existingPositions)
  const maxX = existing.reduce((current, position) => Math.max(current, position.x), 0)
  const minY = existing.reduce((current, position) => Math.min(current, position.y), 0)
  let missingIndex = 0

  for (const table of syncedTables) {
    if (mergedPositions[table.id]) continue
    mergedPositions[table.id] = buildFallbackDesignerPosition(maxX, minY, missingIndex)
    missingIndex += 1
  }

  return mergedPositions
}

function buildFallbackDesignerPosition(maxX: number, minY: number, index: number): DesignerPosition {
  const column = index % 3
  const row = Math.floor(index / 3)
  return {
    x: maxX + 360 + column * 360,
    y: Math.max(minY, 80) + row * 220,
  }
}

export class ErDesignerAdapter implements UIObject {
  type = 'er_designer'
  objectId: string
  tabId: string
  title = 'ER Designer'
  patchCapabilities = PATCH_CAPABILITIES

  constructor(tabId: string, _sessionIdGetter: () => string | null) {
    this.objectId = tabId
    this.tabId = tabId
  }

  read(mode: 'state' | 'schema' | 'actions' | 'full'): unknown {
    const payload = useErTabsStore.getState().designers.get(this.tabId) ?? null
    if (mode === 'state') return payload
    if (mode === 'schema') return { type: this.type, patchCapabilities: this.patchCapabilities }
    if (mode === 'actions') return ACTIONS
    return {
      state: payload,
      schema: { type: this.type, patchCapabilities: this.patchCapabilities },
      actions: ACTIONS,
    }
  }

  patch(ops: JsonPatchOp[], _reason?: string): PatchResult {
    try {
      const { newVersion, assignedIds } = useErTabsStore.getState().applyDesignerPatch(this.tabId, ops)
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
    const payload = useErTabsStore.getState().designers.get(this.tabId)
    if (!payload) return { success: false, error: `tab not found: ${this.tabId}` }

    switch (action) {
      case 'auto_layout': {
        const { computeDagreLayout } = await import('@/features/stage/components/er-canvas/workers/dagre-layout.worker')
        const tables = asDesignerTables(payload)
        const relations = asDesignerRelations(payload)
        const positions = computeDagreLayout({
          nodes: tables.map((table) => ({
            id: table.id,
            width: 288,
            height: 40 + table.columns.length * 28,
          })),
          edges: relations.map((relation) => ({ source: relation.fromTableId, target: relation.toTableId })),
          config: { rankdir: 'LR', nodesep: 80, ranksep: 200 },
        })
        hydrateDesigner(this.tabId, { ...payload, positions })
        return { success: true, data: { positions } }
      }

      case 'fit_view': {
        hydrateDesigner(this.tabId, { ...payload, viewport: { x: 0, y: 0, zoom: 1 } })
        return { success: true }
      }

      case 'bind_target': {
        const input = (params ?? {}) as { connectionId?: string; connection_id?: string; database?: string | null; schema?: string | null }
        const connectionId = input.connectionId ?? input.connection_id
        if (!connectionId) return { success: false, error: 'bind_target requires { connectionId }' }
        hydrateDesigner(this.tabId, {
          ...payload,
          targetConnectionId: connectionId,
          targetDatabase: input.database ?? null,
          targetSchema: input.schema ?? null,
        })
        return { success: true, data: { targetConnectionId: connectionId } }
      }

      case 'unbind_target': {
        hydrateDesigner(this.tabId, {
          ...payload,
          targetConnectionId: null,
          targetDatabase: null,
          targetSchema: null,
        })
        return { success: true }
      }

      case 'diff_against_db': {
        if (!payload.targetConnectionId) return targetRequired()
        const response = await fetch('/api/er/diff', {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ payload, connectionId: payload.targetConnectionId }),
        })
        if (!response.ok) return { success: false, error: await readError(response, `diff_against_db failed: ${response.status}`) }
        return { success: true, data: await response.json() }
      }

      case 'sync_from_db': {
        if (!payload.targetConnectionId) return targetRequired()
        const input = (params ?? {}) as { tables?: string[] }
        const response = await fetch('/api/er/sync-from-db', {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({
            payload,
            connectionId: payload.targetConnectionId,
            tables: Array.isArray(input.tables) ? input.tables : [],
          }),
        })
        if (!response.ok) return { success: false, error: await readError(response, `sync_from_db failed: ${response.status}`) }
        const data = await response.json() as { payload?: Partial<ErDesignerPayload> }
        if (!data?.payload) {
          return { success: false, error: 'sync_from_db: server response missing `payload`' }
        }
        const syncedTables = Array.isArray(data.payload.tables) ? data.payload.tables : []
        // Server returns the merged tables/relations + target context. Preserve the
        // local-only view fields (positions / collapsed / viewport) so layout and
        // zoom survive the refresh. Newly added tables need fallback positions
        // because the backend merge does not own frontend view-state coordinates.
        const merged: ErDesignerPayload = {
          ...payload,
          dialect: data.payload.dialect ?? payload.dialect,
          targetConnectionId: data.payload.targetConnectionId ?? payload.targetConnectionId,
          targetDatabase: data.payload.targetDatabase ?? payload.targetDatabase,
          targetSchema: data.payload.targetSchema ?? payload.targetSchema,
          tables: syncedTables,
          relations: Array.isArray(data.payload.relations) ? data.payload.relations : [],
          positions: mergeDesignerPositions(payload.positions, syncedTables),
        }
        hydrateDesigner(this.tabId, merged)
        return { success: true, data: { payload: merged } }
      }

      case 'generate_ddl': {
        if (!payload.targetConnectionId) return targetRequired()
        const input = (params ?? {}) as { includeDrops?: boolean }
        const response = await fetch('/api/er/generate-ddl', {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({
            payload,
            connectionId: payload.targetConnectionId,
            includeDrops: input.includeDrops === true,
          }),
        })
        if (!response.ok) return { success: false, error: await readError(response, `generate_ddl failed: ${response.status}`) }
        const data = await response.json() as { ddl: string; statements?: unknown[]; skipped?: unknown[]; skippedOps?: unknown[] }
        const { tabId } = useStageStore.getState().openQueryEditor({
          sessionId: null,
          baseTitle: `DDL: ${this.tabId}`,
          openMode: 'always_new',
          entryMode: 'ui_exec',
          initialContent: data.ddl,
          autoRun: false,
          connectionId: payload.targetConnectionId,
          database: payload.targetDatabase ?? null,
          schema: payload.targetSchema ?? null,
        })
        useStageStore.getState().openStage()
        return {
          success: true,
          data: {
            queryEditorTabId: tabId,
            ddl: data.ddl,
            statements: data.statements ?? [],
            skippedOps: data.skipped ?? data.skippedOps ?? [],
          },
        }
      }

      default:
        return { success: false, error: `unknown action: ${action}` }
    }
  }
}
