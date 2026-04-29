import { useCallback, useMemo, useState } from 'react'
import {
  Background,
  Controls,
  ReactFlow,
  ReactFlowProvider,
  type Connection,
  type Edge,
  type EdgeChange,
  type Node,
  type NodeChange,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'

import { ErEdge } from './ErEdge'
import { ErEmptyState } from './ErEmptyState'
import { ErTableContextMenu } from './ErTableContextMenu'
import { ErTableNode } from './ErTableNode'
import { ErToolbar } from './ErToolbar'
import { useDagreLayout } from './hooks/useDagreLayout'
import { useErKeyboard } from './hooks/useErKeyboard'
import { inspectorToGraph, type ErEdgeData, type ErNodeData } from './utils/payload-to-graph'
import type {
  ErColumnMeta,
  ErDesignerPayload,
  ErInspectorPayload,
  ErTableSnapshot,
  JsonPatchOp,
} from '@/features/stage/stores/er-tabs-payload-types'

export interface ErCanvasInspectorProps {
  tabId: string
  mode: 'inspector'
  payload: ErInspectorPayload
  onPatch: (ops: JsonPatchOp[]) => void
  onExec: (action: string, params?: unknown) => void
}

export interface ErCanvasDesignerProps {
  tabId: string
  mode: 'designer'
  payload: ErDesignerPayload
  onPatch: (ops: JsonPatchOp[]) => void
  onExec: (action: string, params?: unknown) => void
}

export type ErCanvasProps = ErCanvasInspectorProps | ErCanvasDesignerProps

const nodeTypes = { erTable: ErTableNode }
const edgeTypes = { erEdge: ErEdge }

export function ErCanvas(props: ErCanvasProps) {
  return (
    <ReactFlowProvider>
      <ErCanvasInner {...props} />
    </ReactFlowProvider>
  )
}

function ErCanvasInner(props: ErCanvasProps) {
  const { tabId, mode, payload, onPatch, onExec } = props
  const [contextMenu, setContextMenu] = useState<{ tableId: string; x: number; y: number } | null>(null)
  const { nodes: rawNodes, edges } = useMemo(
    () => mode === 'designer'
      ? designerToGraph(payload as ErDesignerPayload)
      : inspectorToGraph(payload as ErInspectorPayload),
    [mode, payload],
  )
  const nodes: Node<ErNodeData & { mode: 'inspector' | 'designer' }>[] = useMemo(() => (
    rawNodes.map((node) => ({
      ...node,
      data: {
        ...node.data,
        mode,
        onAddColumn: mode === 'designer' ? () => addColumn(node.id, onPatch) : undefined,
        onDeleteColumn: mode === 'designer' ? (columnId: string) => {
          onPatch([{ op: 'remove', path: `/tables[id=${node.id}]/columns[id=${columnId}]` }])
        } : undefined,
        onUpdateColumn: mode === 'designer' ? (columnId: string, updates: Partial<ErColumnMeta>) => {
          const ops: JsonPatchOp[] = Object.entries(updates).map(([key, value]) => ({
            op: 'replace',
            path: `/tables[id=${node.id}]/columns[id=${columnId}]/${key}`,
            value,
          }))
          onPatch(ops)
        } : undefined,
        onOpenContextMenu: mode === 'designer' ? setContextMenu : undefined,
      },
    }))
  ), [mode, onPatch, rawNodes])
  const { layout } = useDagreLayout()

  const onAutoLayout = useCallback(async () => {
    if (mode === 'designer') {
      onExec('auto_layout')
      return
    }

    const positions = await layout({
      nodes: nodes.map((node) => ({
        id: node.id,
        width: 288,
        height: 40 + node.data.columns.length * 28,
      })),
      edges: (edges as Edge<ErEdgeData>[]).map((edge) => ({ source: edge.source, target: edge.target })),
      config: { rankdir: 'LR', nodesep: 80, ranksep: 200 },
    })
    onPatch([{ op: 'replace', path: '/positions', value: positions }])
  }, [edges, layout, mode, nodes, onExec, onPatch])

  const onFitView = useCallback(() => {
    onExec('fit_view')
  }, [onExec])
  const onRefresh = useCallback(() => {
    onExec('refresh')
  }, [onExec])
  const onForkToDesigner = useCallback(() => {
    onExec('fork_to_designer')
  }, [onExec])
  const onAddTable = useCallback(() => {
    onPatch([{
      op: 'add',
      path: '/tables/-',
      value: {
        name: 'new_table',
        columns: [],
        indexes: [],
        uniques: [],
      },
    }])
  }, [onPatch])
  const onBindTarget = useCallback(() => {
    onExec('bind_target')
  }, [onExec])
  const onDiffVsDb = useCallback(() => {
    onExec('diff_against_db')
  }, [onExec])
  const onGenerateDdl = useCallback(() => {
    onExec('generate_ddl')
  }, [onExec])
  const onChangeDialect = useCallback((dialect: ErDesignerPayload['dialect']) => {
    onPatch([{ op: 'replace', path: '/dialect', value: dialect }])
  }, [onPatch])
  const onAddVirtualRelation = useCallback(() => undefined, [])
  const onChangeNeighborDepth = useCallback((depth: 0 | 1 | 2) => {
    onPatch([{ op: 'replace', path: '/neighborDepth', value: depth }])
  }, [onPatch])

  useErKeyboard({ enabled: true, onAutoLayout, onFitView })

  const onNodesChange = useCallback((changes: NodeChange[]) => {
    const ops: JsonPatchOp[] = []
    for (const change of changes) {
      if (change.type === 'position' && change.position && change.dragging === false) {
        ops.push({ op: 'replace', path: `/positions/${change.id}`, value: change.position })
      }
    }
    if (ops.length > 0) onPatch(ops)
  }, [onPatch])

  const onEdgesChange = useCallback((_changes: EdgeChange[]) => undefined, [])

  const onConnect = useCallback((connection: Connection) => {
    if (mode !== 'designer') return
    const ops = buildDesignerConnectPatch(connection)
    if (ops.length > 0) onPatch(ops)
  }, [mode, onPatch])

  const onNodesDelete = useCallback((deletedNodes: Node[]) => {
    if (mode !== 'designer') return
    onPatch(buildDesignerNodeDeletePatches(deletedNodes))
  }, [mode, onPatch])

  const onEdgesDelete = useCallback((deletedEdges: Edge[]) => {
    if (mode !== 'designer') return
    onPatch(buildDesignerEdgeDeletePatches(deletedEdges))
  }, [mode, onPatch])

  if (mode === 'inspector' && ((payload as ErInspectorPayload).selection ?? []).length === 0) {
    return <ErEmptyState reason="empty_selection" />
  }

  return (
    <div className="flex h-full w-full flex-col" data-er-tab-id={tabId}>
      {mode === 'designer' ? (
        <ErToolbar
          mode="designer"
          dialect={(payload as ErDesignerPayload).dialect}
          hasTarget={Boolean((payload as ErDesignerPayload).targetConnectionId)}
          onAddTable={onAddTable}
          onAutoLayout={onAutoLayout}
          onFitView={onFitView}
          onBindTarget={onBindTarget}
          onDiffVsDb={onDiffVsDb}
          onGenerateDdl={onGenerateDdl}
          onChangeDialect={onChangeDialect}
        />
      ) : (
        <ErToolbar
          mode="inspector"
          neighborDepth={(payload as ErInspectorPayload).neighborDepth}
          onRefresh={onRefresh}
          onAutoLayout={onAutoLayout}
          onFitView={onFitView}
          onChangeNeighborDepth={onChangeNeighborDepth}
          onAddVirtualRelation={onAddVirtualRelation}
          onForkToDesigner={onForkToDesigner}
        />
      )}
      <div className="min-h-0 flex-1 bg-bg-canvas">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={mode === 'designer' ? onConnect : undefined}
          onNodesDelete={mode === 'designer' ? onNodesDelete : undefined}
          onEdgesDelete={mode === 'designer' ? onEdgesDelete : undefined}
          fitView
          fitViewOptions={{ padding: 0.2, maxZoom: 1 }}
          minZoom={0.1}
          maxZoom={2}
          proOptions={{ hideAttribution: true }}
        >
          <Background color="var(--dt-border-subtle)" gap={20} size={1} />
          <Controls showInteractive={false} />
        </ReactFlow>
      </div>
      {contextMenu && (
        <ErTableContextMenu
          x={contextMenu.x}
          y={contextMenu.y}
          tableId={contextMenu.tableId}
          onRename={(tableId) => onExec('rename_table', { tableId })}
          onAddColumn={(tableId) => addColumn(tableId, onPatch)}
          onDeleteTable={(tableId) => onPatch([{ op: 'remove', path: `/tables[id=${tableId}]` }])}
          onClose={() => setContextMenu(null)}
        />
      )}
    </div>
  )
}

type DesignerColumn = {
  id?: string
  name: string
  type: string
  nullable: boolean
  isPrimaryKey?: boolean
  isPK?: boolean
  isFK?: boolean
  isAutoIncrement?: boolean
  defaultValue?: string | null
  default?: string | null
  comment?: string | null
}

type DesignerTable = {
  id?: string
  name: string
  comment?: string | null
  columns?: DesignerColumn[]
}

type DesignerRelation = {
  id?: string
  fromTableId: string
  fromColumnId: string
  toTableId: string
  toColumnId: string
  type?: string
}

function designerToGraph(payload: ErDesignerPayload): {
  nodes: Node<ErNodeData>[]
  edges: Edge<ErEdgeData>[]
} {
  const collapsed = new Set(payload.collapsed ?? [])
  const tables = (payload.tables ?? []) as DesignerTable[]
  const nodes: Node<ErNodeData>[] = tables.map((table) => {
    const tableId = table.id ?? table.name
    const columns = (table.columns ?? []).map(toColumnMeta)
    const tableSnapshot: ErTableSnapshot = {
      name: table.name,
      comment: table.comment,
      columns,
      fkOut: [],
    }

    return {
      id: tableId,
      type: 'erTable',
      position: payload.positions[tableId] ?? { x: 0, y: 0 },
      data: {
        table: tableSnapshot,
        columns,
        collapsed: collapsed.has(tableId),
      },
    }
  })

  const edges: Edge<ErEdgeData>[] = ((payload.relations ?? []) as DesignerRelation[]).map((relation) => ({
    id: relation.id ?? `${relation.fromTableId}.${relation.fromColumnId}->${relation.toTableId}.${relation.toColumnId}`,
    source: relation.fromTableId,
    target: relation.toTableId,
    sourceHandle: `${relation.fromColumnId}-source`,
    targetHandle: `${relation.toColumnId}-target`,
    type: 'erEdge',
    data: {
      kind: 'fk',
      relationType: relation.type ?? 'many_to_one',
      fromColumn: relation.fromColumnId,
      toColumn: relation.toColumnId,
    },
  }))

  return { nodes, edges }
}

function toColumnMeta(column: DesignerColumn): ErColumnMeta {
  return {
    id: column.id,
    name: column.name,
    type: column.type,
    nullable: column.nullable,
    isPK: column.isPrimaryKey ?? column.isPK ?? false,
    isFK: column.isFK ?? false,
    isAutoIncrement: column.isAutoIncrement,
    default: column.defaultValue ?? column.default,
    comment: column.comment,
  } as ErColumnMeta & { id?: string }
}

function addColumn(tableId: string, onPatch: (ops: JsonPatchOp[]) => void) {
  onPatch([{
    op: 'add',
    path: `/tables[id=${tableId}]/columns/-`,
    value: {
      name: 'new_column',
      type: 'VARCHAR(255)',
      nullable: true,
      isPrimaryKey: false,
      isAutoIncrement: false,
    },
  }])
}

export function buildDesignerConnectPatch(
  connection: Pick<Connection, 'source' | 'target' | 'sourceHandle' | 'targetHandle'>,
): JsonPatchOp[] {
  const sourceColumnId = connection.sourceHandle?.replace(/-source$/, '')
  const targetColumnId = connection.targetHandle?.replace(/-target$/, '')
  if (!connection.source || !connection.target || !sourceColumnId || !targetColumnId) return []

  return [{
    op: 'add',
    path: '/relations/-',
    value: {
      fromTableId: connection.source,
      fromColumnId: sourceColumnId,
      toTableId: connection.target,
      toColumnId: targetColumnId,
      type: 'many_to_one',
      constraintMethod: 'database_fk',
    },
  }]
}

export function buildDesignerNodeDeletePatches(nodes: Array<Pick<Node, 'id'>>): JsonPatchOp[] {
  return nodes.map((node) => ({ op: 'remove', path: `/tables[id=${node.id}]` }))
}

export function buildDesignerEdgeDeletePatches(edges: Array<Pick<Edge, 'id'>>): JsonPatchOp[] {
  return edges.map((edge) => ({
    op: 'remove',
    path: `/relations[id=${String(edge.id).replace(/^vr:|^fk:/, '')}]`,
  }))
}
