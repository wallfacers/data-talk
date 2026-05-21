// Ported from /home/wushengzhou/workspace/github/open-db-studio/src/components/ERDesigner/ERCanvas/EREdge.tsx
// (pathToSegments / segmentIntersection / computeCrossings).
// Pure geometry: styling and line-jump arc rendering belong in ErEdge.tsx.

import { getSmoothStepPath, Position } from '@xyflow/react'

export interface Point {
  x: number
  y: number
}

export interface Segment {
  x1: number
  y1: number
  x2: number
  y2: number
}

export interface CrossingPoint extends Point {
  isHorizontal: boolean
}

interface HandleBoundsLike {
  id?: string | null
  x: number
  y: number
  width: number
  height: number
}

interface NodeLike {
  internals?: {
    handleBounds?: {
      source?: HandleBoundsLike[]
      target?: HandleBoundsLike[]
    }
    positionAbsolute?: Point
  }
}

interface EdgeLike {
  id: string
  source?: string
  target?: string
  sourceHandle?: string | null
  targetHandle?: string | null
}

const BORDER_RADIUS = 8
const EPSILON = 0.02

export function pathToSegments(d: string): Segment[] {
  const segs: Segment[] = []
  const re = /([MLHVQCSZ])\s*([\d.,eE\s+-]*)/gi
  let cx = 0
  let cy = 0
  let m: RegExpExecArray | null

  while ((m = re.exec(d)) !== null) {
    const command = m[1].toUpperCase()
    const nums = m[2].trim().split(/[\s,]+/).filter(Boolean).map(Number)

    switch (command) {
      case 'M':
        cx = nums[0]
        cy = nums[1]
        break
      case 'L': {
        const nx = nums[0]
        const ny = nums[1]
        segs.push({ x1: cx, y1: cy, x2: nx, y2: ny })
        cx = nx
        cy = ny
        break
      }
      case 'H': {
        const nx = nums[0]
        segs.push({ x1: cx, y1: cy, x2: nx, y2: cy })
        cx = nx
        break
      }
      case 'V': {
        const ny = nums[0]
        segs.push({ x1: cx, y1: cy, x2: cx, y2: ny })
        cy = ny
        break
      }
      case 'Q':
        cx = nums[2]
        cy = nums[3]
        break
      case 'C':
        cx = nums[4]
        cy = nums[5]
        break
    }
  }

  return segs
}

export function segmentIntersection(a: Segment, b: Segment): Point | null {
  const dax = a.x2 - a.x1
  const day = a.y2 - a.y1
  const dbx = b.x2 - b.x1
  const dby = b.y2 - b.y1
  const denom = dax * dby - day * dbx

  if (Math.abs(denom) < 1e-10) {
    return null
  }

  const t = ((b.x1 - a.x1) * dby - (b.y1 - a.y1) * dbx) / denom
  const u = ((b.x1 - a.x1) * day - (b.y1 - a.y1) * dax) / denom

  if (t <= EPSILON || t >= 1 - EPSILON || u <= EPSILON || u >= 1 - EPSILON) {
    return null
  }

  return { x: a.x1 + t * dax, y: a.y1 + t * day }
}

export function getEdgeEndpoints(
  edge: { source: string; target: string; sourceHandle?: string | null; targetHandle?: string | null },
  nodeLookup: Map<string, NodeLike>,
): { sourceX: number; sourceY: number; targetX: number; targetY: number } | null {
  const sourceNode = nodeLookup.get(edge.source)
  const targetNode = nodeLookup.get(edge.target)
  if (!sourceNode || !targetNode) {
    return null
  }

  const sourceBounds = sourceNode.internals?.handleBounds?.source
  const targetBounds = targetNode.internals?.handleBounds?.target
  const sourcePos = sourceNode.internals?.positionAbsolute
  const targetPos = targetNode.internals?.positionAbsolute
  if (!sourceBounds?.length || !targetBounds?.length || !sourcePos || !targetPos) {
    return null
  }

  const sourceHandle = edge.sourceHandle
    ? sourceBounds.find((handle) => handle.id === edge.sourceHandle)
    : sourceBounds[0]
  const targetHandle = edge.targetHandle
    ? targetBounds.find((handle) => handle.id === edge.targetHandle)
    : targetBounds[0]
  if (!sourceHandle || !targetHandle) {
    return null
  }

  return {
    sourceX: sourcePos.x + sourceHandle.x + sourceHandle.width / 2,
    sourceY: sourcePos.y + sourceHandle.y + sourceHandle.height / 2,
    targetX: targetPos.x + targetHandle.x + targetHandle.width / 2,
    targetY: targetPos.y + targetHandle.y + targetHandle.height / 2,
  }
}

export function computeCrossings(
  myPath: string,
  myId: string,
  storeEdges: EdgeLike[],
  nodeLookup: Map<string, NodeLike>,
): CrossingPoint[] {
  const mySegs = pathToSegments(myPath)
  const myIdx = storeEdges.findIndex((edge) => edge.id === myId)
  if (myIdx <= 0) {
    return []
  }

  const crossings: CrossingPoint[] = []

  for (let i = 0; i < myIdx; i += 1) {
    const other = storeEdges[i]
    if (!other.source || !other.target) {
      continue
    }

    const endpoints = getEdgeEndpoints(
      {
        source: other.source,
        target: other.target,
        sourceHandle: other.sourceHandle,
        targetHandle: other.targetHandle,
      },
      nodeLookup,
    )
    if (!endpoints) {
      continue
    }

    const [otherPath] = getSmoothStepPath({
      ...endpoints,
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
      borderRadius: BORDER_RADIUS,
    })
    const otherSegs = pathToSegments(otherPath)

    for (const mySeg of mySegs) {
      const isHorizontal = Math.abs(mySeg.y2 - mySeg.y1) < Math.abs(mySeg.x2 - mySeg.x1)

      for (const otherSeg of otherSegs) {
        const point = segmentIntersection(mySeg, otherSeg)
        if (point) {
          crossings.push({ ...point, isHorizontal })
        }
      }
    }
  }

  return crossings
}
