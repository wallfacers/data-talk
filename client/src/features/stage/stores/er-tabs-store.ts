import { create } from 'zustand'
import { applyPatch } from '@/services/ui-router/jsonPatch'
import type {
  ErDesignerColumnDraft,
  ErDesignerPayload,
  ErDesignerRelationDraft,
  ErDesignerTableDraft,
  ErInspectorPayload,
  ErVirtualRelation,
  JsonPatchOp,
} from './er-tabs-payload-types'

type DesignerBaseVersion = number | 'auto'
// Designer ops carry the same shape as JsonPatchOp; baseVersion/expectedVersion
// were hoisted into the protocol type, so this alias only documents intent.
type DesignerPatchOp = JsonPatchOp

const INSPECTOR_PATH_WHITELIST = [
  /^\/selection$/,
  /^\/neighborDepth$/,
  /^\/positions$/,
  /^\/positions\/[^/]+$/,
  /^\/collapsed$/,
  /^\/virtualRelations$/,
  /^\/virtualRelations\/-$/,
  /^\/virtualRelations\[id=[^\]]+\]$/,
  /^\/notes$/,
  /^\/notes\/[^/]+$/,
  /^\/viewport$/,
]

function isInspectorPathAllowed(path: string): boolean {
  return INSPECTOR_PATH_WHITELIST.some((pattern) => pattern.test(path))
}

function immutablePathError(path: string): Error {
  const error = new Error(`immutable_path_in_inspector: ${path}`)
  Object.assign(error, {
    code: 'immutable_path_in_inspector',
    aiHint:
      'Inspector tabs are read-only views of real schema. To edit tables, fork this tab to a designer first via ui_exec(fork_to_designer).',
  })
  return error
}

function createVirtualRelationId(): string {
  return createStableId('vr')
}

function createDesignerId(prefix: 't' | 'c' | 'r'): string {
  return createStableId(prefix)
}

function createStableId(prefix: string): string {
  const bytes = globalThis.crypto?.getRandomValues?.(new Uint8Array(6))
  if (bytes) {
    return `${prefix}_${Array.from(bytes, (byte) => byte.toString(36).padStart(2, '0')).join('').slice(0, 8)}`
  }
  return `${prefix}_${Math.random().toString(36).slice(2, 10).padEnd(8, '0')}`
}

function stampVirtualRelationAddOps(current: ErInspectorPayload, ops: JsonPatchOp[]) {
  const assignedIds: Record<string, string> = {}
  let tailAdds = 0
  const baseLength = current.virtualRelations.length

  const stamped = ops.map((op) => {
    if (op.op !== 'add' || op.path !== '/virtualRelations/-' || typeof op.value !== 'object' || op.value === null) {
      return op
    }

    const value = op.value as Partial<ErVirtualRelation>
    const id = typeof value.id === 'string' && value.id.length > 0 ? value.id : createVirtualRelationId()
    assignedIds[`/virtualRelations/${baseLength + tailAdds}`] = id
    tailAdds += 1
    return { ...op, value: { ...value, id } }
  })

  return { stamped, assignedIds }
}

export interface ErInspectorView {
  nodes: {
    id: string
    label: string
    columns: { name: string; type: string; isPK: boolean; isFK: boolean }[]
  }[]
  edges: {
    id: string
    source: string
    target: string
    sourceColumn: string
    targetColumn: string
    kind: 'fk' | 'virtual'
  }[]
}

const DESIGNER_STRUCTURAL_PATHS = [
  /^\/tables$/,
  /^\/tables\/-$/,
  /^\/tables\[id=[^\]]+\](?:\/(?:id|name|comment|columns|indexes|uniques))?$/,
  /^\/tables\[id=[^\]]+\]\/columns\/-$/,
  /^\/tables\[id=[^\]]+\]\/columns\[id=[^\]]+\](?:\/(?:id|name|type|nullable|isPrimaryKey|isAutoIncrement|default|comment))?$/,
  /^\/tables\[id=[^\]]+\]\/indexes(?:\/-|\/\d+)?$/,
  /^\/tables\[id=[^\]]+\]\/indexes\/\d+\/(?:name|columns)$/,
  /^\/tables\[id=[^\]]+\]\/uniques(?:\/-|\/\d+)?$/,
  /^\/tables\[id=[^\]]+\]\/uniques\/\d+\/columns$/,
  /^\/relations$/,
  /^\/relations\/-$/,
  /^\/relations\[id=[^\]]+\](?:\/(?:id|fromTableId|fromColumnId|toTableId|toColumnId|type|constraintMethod))?$/,
  /^\/dialect$/,
  /^\/targetConnectionId$/,
  /^\/targetDatabase$/,
  /^\/targetSchema$/,
]

const DESIGNER_VIEW_PATHS = [
  /^\/positions$/,
  /^\/positions\/[^/]+$/,
  /^\/collapsed$/,
  /^\/viewport$/,
]

function isDesignerStructuralPath(path: string): boolean {
  return DESIGNER_STRUCTURAL_PATHS.some((pattern) => pattern.test(path))
}

function isDesignerPathAllowed(path: string): boolean {
  return isDesignerStructuralPath(path) || DESIGNER_VIEW_PATHS.some((pattern) => pattern.test(path))
}

function versionOf(payload: ErDesignerPayload): number {
  const version = (payload as unknown as { __v?: unknown }).__v
  return typeof version === 'number' ? version : 0
}

function designerPatchVersion(
  op: DesignerPatchOp,
  opts?: { baseVersion?: DesignerBaseVersion },
): DesignerBaseVersion | undefined {
  if (op.baseVersion !== undefined) return op.baseVersion
  if (op.expectedVersion !== undefined) return op.expectedVersion
  if (opts?.baseVersion !== undefined) return opts.baseVersion
  return undefined
}

function conflictError(currentVersion: number, baseVersion: number): Error {
  const error = new Error(`conflict_with_concurrent_edit: current version ${currentVersion}, patch baseVersion ${baseVersion}`)
  Object.assign(error, {
    code: 'conflict_with_concurrent_edit',
    currentVersion,
    baseVersion,
    aiHint:
      'Re-read the ER designer state with ui_read(mode="state") to get the latest version, then retry the structural patch with a fresh baseVersion.',
  })
  return error
}

function missingBaseVersionError(path: string, currentVersion: number): Error {
  const error = new Error(`missing_base_version: structural designer patch ${path} requires a numeric baseVersion`)
  Object.assign(error, {
    code: 'missing_base_version',
    path,
    currentVersion,
    aiHint:
      'Designer structural patches (tables / columns / relations / dialect / target*) require a numeric baseVersion read from ui_read(mode="state"). The wire-default "auto" literal is NOT accepted here — strict versioning is mandatory so concurrent edits surface as 409. View paths (positions/collapsed/viewport) may still omit baseVersion.',
  })
  return error
}

function invalidDesignerPathError(path: string): Error {
  const error = new Error(`invalid_path: ${path}`)
  Object.assign(error, {
    code: 'invalid_path',
    aiHint:
      'Patch only designer schema paths (/tables, /columns, /relations, target fields, dialect) or view paths (/positions, /collapsed, /viewport).',
  })
  return error
}

function normalizeDesignerColumn(
  column: Partial<ErDesignerColumnDraft>,
  assignedIds: Record<string, string>,
  assignedPath: string,
): ErDesignerColumnDraft {
  const id = typeof column.id === 'string' && column.id.length > 0 ? column.id : createDesignerId('c')
  if (column.id !== id) assignedIds[assignedPath] = id
  return {
    id,
    name: typeof column.name === 'string' ? column.name : 'column',
    type: typeof column.type === 'string' ? column.type : 'TEXT',
    nullable: typeof column.nullable === 'boolean' ? column.nullable : true,
    isPrimaryKey: typeof column.isPrimaryKey === 'boolean' ? column.isPrimaryKey : false,
    isAutoIncrement: typeof column.isAutoIncrement === 'boolean' ? column.isAutoIncrement : false,
    default: column.default,
    comment: column.comment,
  }
}

function normalizeDesignerTable(
  table: Partial<ErDesignerTableDraft>,
  assignedIds: Record<string, string>,
  tablePath: string,
): ErDesignerTableDraft {
  const id = typeof table.id === 'string' && table.id.length > 0 ? table.id : createDesignerId('t')
  if (table.id !== id) assignedIds[tablePath] = id
  return {
    id,
    name: typeof table.name === 'string' ? table.name : 'table',
    comment: table.comment,
    columns: Array.isArray(table.columns)
      ? table.columns.map((column, index) => normalizeDesignerColumn(column, assignedIds, `${tablePath}/columns/${index}`))
      : [],
    indexes: Array.isArray(table.indexes) ? table.indexes : [],
    uniques: Array.isArray(table.uniques) ? table.uniques : [],
  }
}

function normalizeDesignerRelation(
  relation: Partial<ErDesignerRelationDraft>,
  assignedIds: Record<string, string>,
  relationPath: string,
): ErDesignerRelationDraft {
  const id = typeof relation.id === 'string' && relation.id.length > 0 ? relation.id : createDesignerId('r')
  if (relation.id !== id) assignedIds[relationPath] = id
  return {
    id,
    fromTableId: relation.fromTableId ?? '',
    fromColumnId: relation.fromColumnId ?? '',
    toTableId: relation.toTableId ?? '',
    toColumnId: relation.toColumnId ?? '',
    type: relation.type ?? 'many_to_one',
    constraintMethod: relation.constraintMethod ?? 'database_fk',
  }
}

function tableIdFromColumnAddPath(path: string): string | null {
  return path.match(/^\/tables\[id=([^\]]+)\]\/columns\/-$/)?.[1] ?? null
}

function stampDesignerAddOps(current: ErDesignerPayload, ops: DesignerPatchOp[]) {
  const assignedIds: Record<string, string> = {}
  let tableAdds = 0
  let relationAdds = 0
  const columnAddsByTableId = new Map<string, number>()

  const stamped = ops.map((op): DesignerPatchOp => {
    if (op.op === 'add' && op.path === '/tables/-' && typeof op.value === 'object' && op.value !== null) {
      const tableIndex = current.tables.length + tableAdds
      tableAdds += 1
      return {
        ...op,
        value: normalizeDesignerTable(op.value as Partial<ErDesignerTableDraft>, assignedIds, `/tables/${tableIndex}`),
      }
    }

    if (op.op === 'add' && op.path === '/relations/-' && typeof op.value === 'object' && op.value !== null) {
      const relationIndex = current.relations.length + relationAdds
      relationAdds += 1
      return {
        ...op,
        value: normalizeDesignerRelation(op.value as Partial<ErDesignerRelationDraft>, assignedIds, `/relations/${relationIndex}`),
      }
    }

    const tableId = op.op === 'add' ? tableIdFromColumnAddPath(op.path) : null
    if (tableId && typeof op.value === 'object' && op.value !== null) {
      const table = current.tables.find((item) => item.id === tableId)
      const previousAdds = columnAddsByTableId.get(tableId) ?? 0
      columnAddsByTableId.set(tableId, previousAdds + 1)
      const columnIndex = (table?.columns.length ?? 0) + previousAdds
      return {
        ...op,
        value: normalizeDesignerColumn(
          op.value as Partial<ErDesignerColumnDraft>,
          assignedIds,
          `/tables[id=${tableId}]/columns/${columnIndex}`,
        ),
      }
    }

    return op
  })

  return { stamped, assignedIds }
}

interface ErTabsState {
  inspectors: Map<string, ErInspectorPayload>
  designers: Map<string, ErDesignerPayload>

  hydrateInspector: (tabId: string, payload: ErInspectorPayload) => void
  hydrateDesigner: (tabId: string, payload: ErDesignerPayload) => void

  applyInspectorPatch: (
    tabId: string,
    ops: JsonPatchOp[],
  ) => { newVersion: number; assignedIds: Record<string, string> }
  applyDesignerPatch: (
    tabId: string,
    ops: DesignerPatchOp[],
    opts?: { baseVersion?: DesignerBaseVersion },
  ) => { newVersion: number; assignedIds: Record<string, string> }

  getInspectorView: (tabId: string) => ErInspectorView | null
  getDesignerView: (tabId: string) => ErInspectorView | null
}

export const useErTabsStore = create<ErTabsState>((set, get) => ({
  inspectors: new Map(),
  designers: new Map(),

  hydrateInspector(tabId, payload) {
    set((state) => {
      const next = new Map(state.inspectors)
      next.set(tabId, payload)
      return { inspectors: next }
    })
  },

  hydrateDesigner(tabId, payload) {
    set((state) => {
      const next = new Map(state.designers)
      next.set(tabId, payload)
      return { designers: next }
    })
  },

  applyInspectorPatch(tabId, ops) {
    const current = get().inspectors.get(tabId)
    if (!current) throw new Error(`tab not found: ${tabId}`)

    for (const op of ops) {
      if (!isInspectorPathAllowed(op.path)) {
        throw immutablePathError(op.path)
      }
    }

    const { stamped, assignedIds } = stampVirtualRelationAddOps(current, ops)
    const next = applyPatch(
      current as unknown as Record<string, unknown>,
      stamped,
    ) as unknown as ErInspectorPayload

    set((state) => {
      const inspectors = new Map(state.inspectors)
      inspectors.set(tabId, next)
      return { inspectors }
    })

    const previousVersion = typeof (current as unknown as { __v?: unknown }).__v === 'number'
      ? (current as unknown as { __v: number }).__v
      : 0
    return { newVersion: previousVersion + 1, assignedIds }
  },

  applyDesignerPatch(tabId, ops, opts) {
    const current = get().designers.get(tabId)
    if (!current) throw new Error(`tab not found: ${tabId}`)

    const currentVersion = versionOf(current)
    for (const op of ops) {
      if (!isDesignerPathAllowed(op.path)) {
        throw invalidDesignerPathError(op.path)
      }
      if (isDesignerStructuralPath(op.path)) {
        const baseVersion = designerPatchVersion(op, opts)
        // Spec (Q12 in 2026-04-29-er-graph-browsing-design): designer structural
        // changes are STRICT — undefined and the convenience 'auto' literal are
        // both rejected so the AI is forced to read the version first.
        if (typeof baseVersion !== 'number') {
          throw missingBaseVersionError(op.path, currentVersion)
        }
        if (baseVersion !== currentVersion) {
          throw conflictError(currentVersion, baseVersion)
        }
      }
    }

    const { stamped, assignedIds } = stampDesignerAddOps(current, ops)
    const next = applyPatch(
      current as unknown as Record<string, unknown>,
      stamped,
    ) as unknown as ErDesignerPayload
    ;(next as unknown as { __v: number }).__v = currentVersion + 1

    set((state) => {
      const designers = new Map(state.designers)
      designers.set(tabId, next)
      return { designers }
    })

    return { newVersion: currentVersion + 1, assignedIds }
  },

  getInspectorView(tabId) {
    const payload = get().inspectors.get(tabId)
    if (!payload) return null

    const nodes = (payload.tablesSnapshot ?? []).map((table) => ({
      id: table.name,
      label: table.name,
      columns: table.columns.map((column) => ({
        name: column.name,
        type: column.type,
        isPK: column.isPK,
        isFK: column.isFK,
      })),
    }))

    const edges: ErInspectorView['edges'] = []
    for (const table of payload.tablesSnapshot ?? []) {
      for (const fk of table.fkOut) {
        edges.push({
          id: `${table.name}.${fk.fromColumn}->${fk.toTable}.${fk.toColumn}`,
          source: table.name,
          target: fk.toTable,
          sourceColumn: fk.fromColumn,
          targetColumn: fk.toColumn,
          kind: 'fk',
        })
      }
    }

    for (const relation of payload.virtualRelations) {
      edges.push({
        id: relation.id,
        source: relation.from.table,
        target: relation.to.table,
        sourceColumn: relation.from.column,
        targetColumn: relation.to.column,
        kind: 'virtual',
      })
    }

    return { nodes, edges }
  },

  getDesignerView(tabId) {
    const payload = get().designers.get(tabId)
    if (!payload) return null

    const fkColumnIds = new Set(payload.relations.map((relation) => relation.fromColumnId))
    const tableById = new Map(payload.tables.map((table) => [table.id, table]))
    const columnById = new Map<string, ErDesignerColumnDraft>()
    for (const table of payload.tables) {
      for (const column of table.columns) {
        columnById.set(column.id, column)
      }
    }

    const nodes = payload.tables.map((table) => ({
      id: table.id,
      label: table.name,
      columns: table.columns.map((column) => ({
        name: column.name,
        type: column.type,
        isPK: column.isPrimaryKey,
        isFK: fkColumnIds.has(column.id),
      })),
    }))

    const edges: ErInspectorView['edges'] = payload.relations.map((relation): ErInspectorView['edges'][number] => ({
      id: relation.id,
      source: relation.fromTableId,
      target: relation.toTableId,
      sourceColumn: columnById.get(relation.fromColumnId)?.name ?? relation.fromColumnId,
      targetColumn: columnById.get(relation.toColumnId)?.name ?? relation.toColumnId,
      kind: relation.constraintMethod === 'database_fk' ? 'fk' : 'virtual',
    })).filter((edge) => tableById.has(edge.source) && tableById.has(edge.target))

    return { nodes, edges }
  },
}))
