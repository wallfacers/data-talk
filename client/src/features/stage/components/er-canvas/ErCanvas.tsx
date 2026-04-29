import { useCallback, useMemo } from 'react'
import {
  Background,
  Controls,
  ReactFlow,
  ReactFlowProvider,
  type Edge,
  type EdgeChange,
  type Node,
  type NodeChange,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'

import { ErEdge } from './ErEdge'
import { ErEmptyState } from './ErEmptyState'
import { ErTableNode } from './ErTableNode'
import { ErToolbar } from './ErToolbar'
import { useDagreLayout } from './hooks/useDagreLayout'
import { useErKeyboard } from './hooks/useErKeyboard'
import { inspectorToGraph, type ErEdgeData, type ErNodeData } from './utils/payload-to-graph'
import type { ErInspectorPayload, JsonPatchOp } from '@/features/stage/stores/er-tabs-payload-types'

export interface ErCanvasInspectorProps {
  tabId: string
  mode: 'inspector'
  payload: ErInspectorPayload
  onPatch: (ops: JsonPatchOp[]) => void
  onExec: (action: string, params?: unknown) => void
}

const nodeTypes = { erTable: ErTableNode }
const edgeTypes = { erEdge: ErEdge }

export function ErCanvas(props: ErCanvasInspectorProps) {
  return (
    <ReactFlowProvider>
      <ErCanvasInner {...props} />
    </ReactFlowProvider>
  )
}

function ErCanvasInner({ tabId, payload, onPatch, onExec }: ErCanvasInspectorProps) {
  const { nodes: rawNodes, edges } = useMemo(() => inspectorToGraph(payload), [payload])
  const nodes: Node<ErNodeData & { mode: 'inspector' }>[] = useMemo(() => (
    rawNodes.map((node) => ({
      ...node,
      data: { ...node.data, mode: 'inspector' as const },
    }))
  ), [rawNodes])
  const { layout } = useDagreLayout()

  const onAutoLayout = useCallback(async () => {
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
  }, [edges, layout, nodes, onPatch])

  const onFitView = useCallback(() => {
    onExec('fit_view')
  }, [onExec])
  const onRefresh = useCallback(() => {
    onExec('refresh')
  }, [onExec])
  const onForkToDesigner = useCallback(() => {
    onExec('fork_to_designer')
  }, [onExec])
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

  if ((payload.selection ?? []).length === 0 || (payload.tablesSnapshot ?? []).length === 0) {
    return <ErEmptyState reason="empty_selection" />
  }

  return (
    <div className="flex h-full w-full flex-col" data-er-tab-id={tabId}>
      <ErToolbar
        mode="inspector"
        neighborDepth={payload.neighborDepth}
        onRefresh={onRefresh}
        onAutoLayout={onAutoLayout}
        onFitView={onFitView}
        onChangeNeighborDepth={onChangeNeighborDepth}
        onAddVirtualRelation={onAddVirtualRelation}
        onForkToDesigner={onForkToDesigner}
      />
      <div className="min-h-0 flex-1 bg-bg-canvas">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
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
    </div>
  )
}
