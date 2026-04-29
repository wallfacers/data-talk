import type { JsonPatchOp } from '@/services/ui-router/types'

export type { JsonPatchOp }

export interface ErColumnMeta {
  name: string
  type: string
  nullable: boolean
  isPK: boolean
  isFK: boolean
  isAutoIncrement?: boolean
  default?: string | null
  comment?: string | null
}

export interface ErRelationSnapshot {
  fromColumn: string
  toTable: string
  toColumn: string
}

export interface ErTableSnapshot {
  name: string
  comment?: string | null
  columns: ErColumnMeta[]
  fkOut: ErRelationSnapshot[]
}

export interface ErVirtualRelation {
  id: string
  from: { table: string; column: string }
  to: { table: string; column: string }
  type: 'one_to_one' | 'one_to_many' | 'many_to_one' | 'many_to_many'
  note?: string
}

export interface ErViewport {
  x: number
  y: number
  zoom: number
}

export interface ErInspectorPayload {
  kind: 'er_inspector'
  connectionId: string
  database?: string | null
  schema?: string | null
  selection: string[]
  neighborDepth: 0 | 1 | 2
  layout: 'dagre-LR'
  tablesSnapshot?: ErTableSnapshot[]
  snapshotAt?: number
  positions: Record<string, { x: number; y: number }>
  collapsed: string[]
  virtualRelations: ErVirtualRelation[]
  notes: Record<string, string>
  viewport: ErViewport
}

export interface ErDesignerPayload {
  kind: 'er_designer'
  dialect: 'mysql' | 'postgresql' | 'h2' | 'sqlite'
  targetConnectionId?: string | null
  targetDatabase?: string | null
  targetSchema?: string | null
  tables: unknown[]
  relations: unknown[]
  positions: Record<string, { x: number; y: number }>
  collapsed: string[]
  viewport: ErViewport
}
