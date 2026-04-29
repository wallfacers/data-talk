import type { Edge, Node } from '@xyflow/react'
import type {
  ErColumnMeta,
  ErInspectorPayload,
  ErTableSnapshot,
} from '@/features/stage/stores/er-tabs-payload-types'

export interface ErNodeData extends Record<string, unknown> {
  table: ErTableSnapshot
  columns: ErColumnMeta[]
  collapsed: boolean
}

export interface ErEdgeData extends Record<string, unknown> {
  kind: 'fk' | 'virtual'
  relationType?: string
  fromColumn: string
  toColumn: string
}

export function inspectorToGraph(payload: ErInspectorPayload): {
  nodes: Node<ErNodeData>[]
  edges: Edge<ErEdgeData>[]
} {
  const collapsed = new Set(payload.collapsed ?? [])
  const nodes: Node<ErNodeData>[] = (payload.tablesSnapshot ?? []).map((table) => ({
    id: table.name,
    type: 'erTable',
    position: payload.positions[table.name] ?? { x: 0, y: 0 },
    data: {
      table,
      columns: table.columns,
      collapsed: collapsed.has(table.name),
    },
  }))

  const edges: Edge<ErEdgeData>[] = []
  for (const table of payload.tablesSnapshot ?? []) {
    for (const fk of table.fkOut ?? []) {
      edges.push({
        id: `fk:${table.name}.${fk.fromColumn}->${fk.toTable}.${fk.toColumn}`,
        source: table.name,
        target: fk.toTable,
        sourceHandle: `${fk.fromColumn}-source`,
        targetHandle: `${fk.toColumn}-target`,
        type: 'erEdge',
        data: {
          kind: 'fk',
          relationType: 'many_to_one',
          fromColumn: fk.fromColumn,
          toColumn: fk.toColumn,
        },
      })
    }
  }

  for (const relation of payload.virtualRelations ?? []) {
    edges.push({
      id: `vr:${relation.id}`,
      source: relation.from.table,
      target: relation.to.table,
      sourceHandle: `${relation.from.column}-source`,
      targetHandle: `${relation.to.column}-target`,
      type: 'erEdge',
      data: {
        kind: 'virtual',
        relationType: relation.type,
        fromColumn: relation.from.column,
        toColumn: relation.to.column,
      },
    })
  }

  return { nodes, edges }
}
