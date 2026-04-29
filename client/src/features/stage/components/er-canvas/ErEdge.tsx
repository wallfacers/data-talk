import { useMemo } from 'react'
import {
  EdgeLabelRenderer,
  Position,
  getSmoothStepPath,
  useStore,
  type Edge,
  type EdgeProps,
} from '@xyflow/react'

import { computeCrossings, pathToSegments } from './utils/crossings'
import { resolveLabelPos } from './utils/label-positioning'
import type { ErEdgeData } from './utils/payload-to-graph'
import { buildSelfRefPath } from './utils/self-ref-path'

const BORDER_RADIUS = 8
const JUMP_RADIUS = 6

const RELATION_LABEL: Record<string, string> = {
  one_to_one: '1:1',
  one_to_many: '1:N',
  many_to_one: 'N:1',
  many_to_many: 'N:N',
}

type ErNodeLookupEntry = {
  internals?: {
    positionAbsolute?: { x: number; y: number }
  }
  measured?: {
    height?: number
  }
}

type CrossingNodeLookup = Parameters<typeof computeCrossings>[3]

export function ErEdge(props: EdgeProps<Edge<ErEdgeData>>) {
  const {
    id,
    source,
    target,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    data,
    selected,
    markerEnd,
    markerStart,
  } = props

  const storeEdges = useStore((state) => state.edges)
  const nodeLookup = useStore((state) => state.nodeLookup)
  const isSelfRef = source === target
  const isVirtual = data?.kind === 'virtual'

  const [edgePath, fallbackLabelX, fallbackLabelY] = useMemo(() => {
    if (isSelfRef) {
      const node = nodeLookup.get(source) as ErNodeLookupEntry | undefined
      const nodeTopY = node?.internals?.positionAbsolute?.y ?? Math.min(sourceY, targetY)
      const measuredHeight = node?.measured?.height ?? Math.abs(targetY - sourceY)
      const nodeBottomY = nodeTopY + (measuredHeight || 160)

      return buildSelfRefPath(sourceX, sourceY, targetX, targetY, nodeTopY, nodeBottomY)
    }

    return getSmoothStepPath({
      sourceX,
      sourceY,
      targetX,
      targetY,
      sourcePosition: sourcePosition ?? Position.Right,
      targetPosition: targetPosition ?? Position.Left,
      borderRadius: BORDER_RADIUS,
    })
  }, [
    isSelfRef,
    nodeLookup,
    source,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  ])

  const crossings = useMemo(() => {
    if (isSelfRef) {
      return []
    }

    return computeCrossings(edgePath, id, storeEdges, nodeLookup as unknown as CrossingNodeLookup)
  }, [edgePath, id, isSelfRef, nodeLookup, storeEdges])

  const labelPos = useMemo(() => {
    if (isSelfRef) {
      return { x: fallbackLabelX, y: fallbackLabelY }
    }

    return resolveLabelPos(pathToSegments(edgePath), []) ?? { x: fallbackLabelX, y: fallbackLabelY }
  }, [edgePath, fallbackLabelX, fallbackLabelY, isSelfRef])

  const stroke = selected
    ? 'var(--dt-accent-primary)'
    : isVirtual
      ? 'var(--dt-accent-warn)'
      : 'var(--dt-border-strong)'
  const strokeWidth = selected ? 2.5 : 2
  const strokeDasharray = isVirtual ? '6 3' : undefined
  const relationLabel = RELATION_LABEL[data?.relationType ?? 'one_to_many'] ?? '1:N'

  return (
    <>
      <path
        d={edgePath}
        fill="none"
        markerEnd={markerEnd}
        markerStart={markerStart}
        stroke={stroke}
        strokeDasharray={strokeDasharray}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={strokeWidth}
      />
      {crossings.map((crossing, index) => {
        const radius = JUMP_RADIUS
        const arc = crossing.isHorizontal
          ? `M ${crossing.x - radius},${crossing.y} A ${radius},${radius} 0 0,1 ${crossing.x + radius},${crossing.y}`
          : `M ${crossing.x},${crossing.y - radius} A ${radius},${radius} 0 0,1 ${crossing.x},${crossing.y + radius}`

        return (
          <g key={`${crossing.x}:${crossing.y}:${index}`}>
            <circle cx={crossing.x} cy={crossing.y} fill="var(--dt-bg-canvas)" r={radius + 1} />
            <path
              d={arc}
              fill="none"
              stroke={stroke}
              strokeDasharray={strokeDasharray}
              strokeLinecap="round"
              strokeWidth={strokeWidth}
            />
          </g>
        )
      })}
      <EdgeLabelRenderer>
        <div
          className="pointer-events-none absolute rounded border border-[var(--dt-border-default)] bg-[var(--dt-bg-canvas)] px-1.5 py-0.5 font-mono text-[11px] text-[var(--dt-text-muted)]"
          style={{
            transform: `translate(-50%, -50%) translate(${labelPos.x}px, ${labelPos.y}px)`,
          }}
        >
          {relationLabel}
          {isVirtual ? <span className="ml-1 text-[var(--dt-accent-warn)]">virtual</span> : null}
        </div>
      </EdgeLabelRenderer>
    </>
  )
}
