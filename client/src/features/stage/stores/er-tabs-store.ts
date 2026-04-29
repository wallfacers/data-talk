import { create } from 'zustand'
import { applyPatch } from '@/services/ui-router/jsonPatch'
import type {
  ErDesignerPayload,
  ErInspectorPayload,
  ErVirtualRelation,
  JsonPatchOp,
} from './er-tabs-payload-types'

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
  const bytes = globalThis.crypto?.getRandomValues?.(new Uint8Array(6))
  if (bytes) {
    return `vr_${Array.from(bytes, (byte) => byte.toString(36).padStart(2, '0')).join('').slice(0, 8)}`
  }
  return `vr_${Math.random().toString(36).slice(2, 10).padEnd(8, '0')}`
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
    ops: JsonPatchOp[],
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

  applyDesignerPatch(_tabId, _ops) {
    throw new Error('applyDesignerPatch: implement in Plan B')
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

  getDesignerView(_tabId) {
    return null
  },
}))
