import { create } from 'zustand'
import type {
  ErDesignerPayload,
  ErInspectorPayload,
  JsonPatchOp,
} from './er-tabs-payload-types'

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

  applyInspectorPatch(_tabId, _ops) {
    throw new Error('applyInspectorPatch: implement in Task 11')
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
