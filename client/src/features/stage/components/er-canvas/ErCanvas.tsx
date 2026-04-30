import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Background,
  BackgroundVariant,
  Controls,
  ReactFlow,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
  type Connection,
  type Edge,
  type Node,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'

import { ErEmptyState } from './ErEmptyState'
import { MemoErEdge } from './ErEdge'
import { ErEdgeMarkers } from './ErEdgeMarkers'
import { ErTableContextMenu } from './ErTableContextMenu'
import { MemoErTableNode } from './ErTableNode'
import { ErToolbar } from './ErToolbar'
import { useDagreLayout } from './hooks/useDagreLayout'
import { useErKeyboard } from './hooks/useErKeyboard'
import { inspectorToGraph, type ErEdgeData, type ErNodeData } from './utils/payload-to-graph'
import { useI18n } from '@/i18n/use-i18n'
import type {
  ErColumnMeta,
  ErDesignerPayload,
  ErDesignerRelationDraft,
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

const nodeTypes = { erTable: MemoErTableNode }
const edgeTypes = { erEdge: MemoErEdge }

export function ErCanvas(props: ErCanvasProps) {
  return (
    <ReactFlowProvider>
      <ErCanvasInner {...props} />
    </ReactFlowProvider>
  )
}

function ErCanvasInner(props: ErCanvasProps) {
  const { tabId, mode, payload, onPatch, onExec } = props
  const { t } = useI18n()
  const [contextMenu, setContextMenu] = useState<{ tableId: string; x: number; y: number } | null>(null)
  const { nodes: rawNodes, edges: rawEdges } = useMemo(
    () => mode === 'designer'
      ? designerToGraph(payload as ErDesignerPayload)
      : inspectorToGraph(payload as ErInspectorPayload),
    [mode, payload],
  )
  const designerDialect = mode === 'designer' ? (payload as ErDesignerPayload).dialect : undefined

  type ErFlowNode = Node<ErNodeData & {
    mode: 'inspector' | 'designer'
    dialect?: ErDesignerPayload['dialect']
  }>

  const decorate = useCallback((node: Node<ErNodeData>): ErFlowNode => ({
    ...node,
    data: {
      ...node.data,
      mode,
      dialect: designerDialect,
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
  }), [designerDialect, mode, onPatch])

  const decorateEdge = useCallback((edge: Edge<ErEdgeData>): Edge<ErEdgeData> => {
    if (!edge.data) return edge

    return {
      ...edge,
      data: {
        ...edge.data,
        mode,
        onUpdateRelationType: mode === 'designer'
          ? (relationType: ErDesignerRelationDraft['type']) => onPatch(buildDesignerRelationTypePatch(edge, relationType))
          : undefined,
        onDeleteRelation: mode === 'designer'
          ? () => onPatch(buildDesignerEdgeDeletePatches([edge]))
          : undefined,
      },
    }
  }, [mode, onPatch])

  const initialNodes = useMemo(() => rawNodes.map(decorate), [rawNodes, decorate])
  const initialEdges = useMemo(() => rawEdges.map(decorateEdge), [rawEdges, decorateEdge])
  const [nodes, setNodes, onNodesChange] = useNodesState<ErFlowNode>(initialNodes)
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge<ErEdgeData>>(initialEdges)
  const selectedNodesRef = useRef<Node[]>([])
  const selectedEdgesRef = useRef<Edge[]>([])

  // Reconcile external payload changes into local node state without disturbing
  // in-flight drag positions. We keep existing nodes' positions; only updates to
  // table data (or new/removed nodes) propagate.
  useEffect(() => {
    setNodes((current) => {
      const decorated = rawNodes.map(decorate)
      const byId = new Map(current.map((n) => [n.id, n]))
      const next: ErFlowNode[] = []
      const seen = new Set<string>()
      for (const incoming of decorated) {
        seen.add(incoming.id)
        const existing = byId.get(incoming.id)
        if (existing) {
          next.push({
            ...existing,
            position: existing.dragging ? existing.position : incoming.position,
            data: incoming.data,
          })
        } else {
          next.push(incoming)
        }
      }
      // Drop nodes that no longer exist in payload
      if (next.length === current.length && current.every((n) => seen.has(n.id))) {
        return next
      }
      return next
    })
  }, [rawNodes, decorate, setNodes])

  useEffect(() => {
    setEdges(rawEdges.map(decorateEdge))
  }, [decorateEdge, rawEdges, setEdges])

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

  const onDeleteSelection = useCallback(() => {
    if (mode !== 'designer') return

    const selectedNodes = selectedNodesRef.current.length > 0
      ? selectedNodesRef.current
      : nodes.filter((node) => node.selected)
    const selectedEdges = selectedEdgesRef.current.length > 0
      ? selectedEdgesRef.current
      : edges.filter((edge) => edge.selected)

    const designerRelations = (payload as ErDesignerPayload).relations ?? []
    const ops = dedupePatchPaths([
      ...buildDesignerNodeDeletePatches(selectedNodes, designerRelations),
      ...buildDesignerEdgeDeletePatches(selectedEdges),
    ])
    if (ops.length > 0) onPatch(ops)
  }, [edges, mode, nodes, onPatch, payload])

  useErKeyboard({ enabled: true, onAutoLayout, onFitView, onDelete: onDeleteSelection })

  const onNodeDragStop = useCallback(
    (_event: unknown, _node: Node, draggedNodes: Node[]) => {
      const ops: JsonPatchOp[] = draggedNodes.map((n) => ({
        op: 'replace',
        path: `/positions/${n.id}`,
        value: n.position,
      }))
      if (ops.length > 0) onPatch(ops)
    },
    [onPatch],
  )

  const onConnect = useCallback((connection: Connection) => {
    if (mode !== 'designer') return
    const ops = buildDesignerConnectPatch(connection)
    if (ops.length > 0) onPatch(ops)
  }, [mode, onPatch])

  const onNodesDelete = useCallback((deletedNodes: Node[]) => {
    if (mode !== 'designer') return
    onPatch(buildDesignerNodeDeletePatches(deletedNodes, (payload as ErDesignerPayload).relations ?? []))
  }, [mode, onPatch, payload])

  const onEdgesDelete = useCallback((deletedEdges: Edge[]) => {
    if (mode !== 'designer') return
    onPatch(buildDesignerEdgeDeletePatches(deletedEdges))
  }, [mode, onPatch])

  const onSelectionChange = useCallback(({ nodes: selectedNodes, edges: selectedEdges }: { nodes: Node[]; edges: Edge[] }) => {
    selectedNodesRef.current = selectedNodes
    selectedEdgesRef.current = selectedEdges
  }, [])

  if (mode === 'inspector' && ((payload as ErInspectorPayload).selection ?? []).length === 0) {
    return <ErEmptyState reason="empty_selection" />
  }

  const designerPayload = payload as ErDesignerPayload
  const isEmptyDesigner = mode === 'designer' && (designerPayload.tables?.length ?? 0) === 0

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
        {isEmptyDesigner ? (
          <ErEmptyState
            reason="empty_designer"
            actionLabel={mode === 'designer' ? t('erCanvas.toolbar.addTable') : undefined}
            onAction={mode === 'designer' ? onAddTable : undefined}
          />
        ) : (
          <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={nodeTypes}
            edgeTypes={edgeTypes}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onNodeDragStop={onNodeDragStop}
            onConnect={mode === 'designer' ? onConnect : undefined}
            onNodesDelete={mode === 'designer' ? onNodesDelete : undefined}
            onEdgesDelete={mode === 'designer' ? onEdgesDelete : undefined}
            onSelectionChange={mode === 'designer' ? onSelectionChange : undefined}
            deleteKeyCode={null}
            fitView
            fitViewOptions={{ padding: 0.2, maxZoom: 1 }}
            minZoom={0.1}
            maxZoom={2}
            onlyRenderVisibleElements
            proOptions={{ hideAttribution: true }}
          >
            <Background
              color={mode === 'designer'
                ? 'var(--dt-border-subtle)'
                : 'color-mix(in srgb, var(--dt-border-subtle) 60%, transparent)'}
              gap={mode === 'designer' ? 18 : 24}
              size={1}
              variant={BackgroundVariant.Dots}
            />
            <Controls showInteractive={false} />
            <ErEdgeMarkers />
          </ReactFlow>
        )}
      </div>
      {contextMenu && (
        <ErTableContextMenu
          x={contextMenu.x}
          y={contextMenu.y}
          tableId={contextMenu.tableId}
          onRename={(tableId) => onExec('rename_table', { tableId })}
          onAddColumn={(tableId) => addColumn(tableId, onPatch)}
          onDeleteTable={(tableId) => onPatch(buildDesignerNodeDeletePatches(
            [{ id: tableId }],
            (payload as ErDesignerPayload).relations ?? [],
          ))}
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
  constraintMethod?: ErDesignerRelationDraft['constraintMethod']
}

export function designerToGraph(payload: ErDesignerPayload): {
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
      kind: relation.constraintMethod === 'comment_ref' ? 'virtual' : 'fk',
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

export function buildDesignerNodeDeletePatches(
  nodes: Array<Pick<Node, 'id'>>,
  relations: Array<Pick<ErDesignerRelationDraft, 'id' | 'fromTableId' | 'toTableId'>> = [],
): JsonPatchOp[] {
  const deletedTableIds = new Set(nodes.map((node) => node.id))
  return [
    ...relations
      .filter((relation) => deletedTableIds.has(relation.fromTableId) || deletedTableIds.has(relation.toTableId))
      .map((relation) => ({ op: 'remove', path: `/relations[id=${relation.id}]` } as JsonPatchOp)),
    ...nodes.map((node) => ({ op: 'remove', path: `/tables[id=${node.id}]` } as JsonPatchOp)),
  ]
}

export function buildDesignerEdgeDeletePatches(edges: Array<Pick<Edge, 'id'>>): JsonPatchOp[] {
  return edges.map((edge) => ({
    op: 'remove',
    path: `/relations[id=${designerRelationIdFromEdgeId(edge.id)}]`,
  }))
}

export function buildDesignerRelationTypePatch(
  edge: Pick<Edge, 'id'>,
  relationType: ErDesignerRelationDraft['type'],
): JsonPatchOp[] {
  return [{
    op: 'replace',
    path: `/relations[id=${designerRelationIdFromEdgeId(edge.id)}]/type`,
    value: relationType,
  }]
}

function designerRelationIdFromEdgeId(edgeId: string | number): string {
  return String(edgeId).replace(/^vr:|^fk:/, '')
}

function dedupePatchPaths(ops: JsonPatchOp[]): JsonPatchOp[] {
  const seen = new Set<string>()
  const deduped: JsonPatchOp[] = []
  for (const op of ops) {
    const key = `${op.op}:${op.path}`
    if (seen.has(key)) continue
    seen.add(key)
    deduped.push(op)
  }
  return deduped
}
