import dagre from 'dagre'

export interface LayoutNode {
  id: string
  width: number
  height: number
}

export interface LayoutEdge {
  source: string
  target: string
}

export interface LayoutConfig {
  rankdir: 'LR' | 'TB'
  nodesep: number
  ranksep: number
}

export interface LayoutInput {
  nodes: LayoutNode[]
  edges: LayoutEdge[]
  config: LayoutConfig
}

export type LayoutPositions = Record<string, { x: number; y: number }>

export function computeDagreLayout(input: LayoutInput): LayoutPositions {
  if (input.nodes.length === 0) return {}

  const graph = new dagre.graphlib.Graph()
  graph.setGraph(input.config)
  graph.setDefaultEdgeLabel(() => ({}))

  for (const node of input.nodes) {
    graph.setNode(node.id, { width: node.width, height: node.height })
  }
  for (const edge of input.edges) {
    graph.setEdge(edge.source, edge.target)
  }

  dagre.layout(graph)

  const positions: LayoutPositions = {}
  for (const node of input.nodes) {
    const graphNode = graph.node(node.id)
    if (!graphNode) continue
    positions[node.id] = {
      x: graphNode.x - node.width / 2,
      y: graphNode.y - node.height / 2,
    }
  }
  return positions
}

const globalScopeName = (globalThis as { constructor?: { name?: string } }).constructor?.name ?? ''

if (typeof self !== 'undefined' && globalScopeName.includes('WorkerGlobalScope')) {
  self.onmessage = (event: MessageEvent<LayoutInput>) => {
    const start = performance.now()
    const positions = computeDagreLayout(event.data)
    const durationMs = performance.now() - start
    self.postMessage({ positions, durationMs })
  }
}
