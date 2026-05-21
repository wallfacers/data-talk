import { memo, useMemo } from 'react'
import {
  EdgeLabelRenderer,
  Position,
  getSmoothStepPath,
  useStore,
  type Edge,
  type EdgeProps,
} from '@xyflow/react'
import { Trash2Icon } from 'lucide-react'

import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select'
import { useI18n } from '@/i18n/use-i18n'
import type { ErDesignerRelationDraft } from '@/features/stage/stores/er-tabs-payload-types'
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

const RELATION_TYPE_OPTIONS: Array<{ value: ErDesignerRelationDraft['type']; label: string }> = [
  { value: 'one_to_one', label: '1:1' },
  { value: 'one_to_many', label: '1:N' },
  { value: 'many_to_one', label: 'N:1' },
  { value: 'many_to_many', label: 'N:N' },
]

type ErNodeLookupEntry = {
  internals?: {
    positionAbsolute?: { x: number; y: number }
  }
  measured?: {
    width?: number
    height?: number
  }
}

type CrossingNodeLookup = Parameters<typeof computeCrossings>[3]
type NodeLookupLike = { get: (nodeId: string) => unknown }

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
    markerStart,
  } = props
  const { t } = useI18n()

  const storeEdges = useStore((state) => state.edges)
  const nodeLookup = useStore((state) => state.nodeLookup)
  const isAnyNodeDragging = useStore((state) => state.nodes.some((node) => node.dragging))
  const isSelfRef = source === target
  const isVirtual = data?.kind === 'virtual'
  const isEditable = data?.mode === 'designer'
    && typeof data.onUpdateRelationType === 'function'
    && typeof data.onDeleteRelation === 'function'
  const sourcePoint = useMemo(
    () => alignEndpointToNodeBorder(nodeLookup, source, sourceX, sourceY, sourcePosition ?? Position.Right),
    [nodeLookup, source, sourcePosition, sourceX, sourceY],
  )
  const targetPoint = useMemo(
    () => alignEndpointToNodeBorder(nodeLookup, target, targetX, targetY, targetPosition ?? Position.Left),
    [nodeLookup, target, targetPosition, targetX, targetY],
  )

  const [edgePath, fallbackLabelX, fallbackLabelY] = useMemo(() => {
    if (isSelfRef) {
      const node = nodeLookup.get(source) as ErNodeLookupEntry | undefined
      const nodeTopY = node?.internals?.positionAbsolute?.y ?? Math.min(sourceY, targetY)
      const measuredHeight = node?.measured?.height ?? Math.abs(targetY - sourceY)
      const nodeBottomY = nodeTopY + (measuredHeight || 160)

      return buildSelfRefPath(sourcePoint.x, sourcePoint.y, targetPoint.x, targetPoint.y, nodeTopY, nodeBottomY)
    }

    return getSmoothStepPath({
      sourceX: sourcePoint.x,
      sourceY: sourcePoint.y,
      targetX: targetPoint.x,
      targetY: targetPoint.y,
      sourcePosition: sourcePosition ?? Position.Right,
      targetPosition: targetPosition ?? Position.Left,
      borderRadius: BORDER_RADIUS,
    })
  }, [
    isSelfRef,
    nodeLookup,
    source,
    sourcePoint.x,
    sourcePoint.y,
    targetPoint.x,
    targetPoint.y,
    sourceY,
    targetY,
    sourcePosition,
    targetPosition,
  ])

  const crossings = useMemo(() => {
    if (isSelfRef || isAnyNodeDragging) {
      return []
    }

    return computeCrossings(edgePath, id, storeEdges, nodeLookup as unknown as CrossingNodeLookup)
  }, [edgePath, id, isAnyNodeDragging, isSelfRef, nodeLookup, storeEdges])

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
  const strokeWidth = selected ? 2 : 1.5
  const strokeDasharray = isVirtual ? '4 3' : undefined
  const markerId = selected
    ? 'er-edge-arrow-selected'
    : isVirtual
      ? 'er-edge-arrow-virtual'
      : 'er-edge-arrow-default'
  const relationLabel = RELATION_LABEL[data?.relationType ?? 'one_to_many'] ?? '1:N'
  const editableRelationType = isRelationType(data?.relationType ?? '') ? data?.relationType : 'many_to_one'

  return (
    <>
      <path
        d={edgePath}
        fill="none"
        markerEnd={`url(#${markerId})`}
        markerStart={markerStart}
        stroke={stroke}
        strokeDasharray={strokeDasharray}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={strokeWidth}
        data-er-edge
        data-id={id}
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
          className={[
            isEditable
              ? 'pointer-events-auto flex items-center gap-0.5 rounded-md border border-[var(--dt-border-default)] bg-[var(--dt-bg-canvas)] px-1 py-0.5 shadow-sm'
              : 'pointer-events-none rounded border border-[var(--dt-border-default)] bg-[var(--dt-bg-canvas)] px-1.5 py-0.5 font-mono text-[11px] text-[var(--dt-text-muted)]',
            'absolute',
          ].join(' ')}
          style={{
            transform: `translate(-50%, -50%) translate(${labelPos.x}px, ${labelPos.y}px)`,
          }}
          onClick={(event) => event.stopPropagation()}
        >
          {isEditable ? (
            <>
              <Select
                value={editableRelationType}
                onValueChange={(nextValue) => {
                  if (typeof nextValue === 'string' && isRelationType(nextValue)) {
                    data?.onUpdateRelationType?.(nextValue)
                  }
                }}
              >
                <SelectTrigger
                  aria-label={t('erCanvas.edge.relationType')}
                  nativeButton={false}
                  render={<div />}
                  size="sm"
                  className="nodrag h-6 w-[50px] border-transparent bg-transparent px-1 font-mono text-[11px] text-[var(--dt-text-muted)] hover:bg-[var(--dt-interaction-hover)]"
                >
                  <span className="flex flex-1 text-left">{relationLabel}</span>
                </SelectTrigger>
                <SelectContent className="bg-[var(--dt-bg-panel)]">
                  {RELATION_TYPE_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <button
                type="button"
                aria-label={t('erCanvas.edge.deleteRelation')}
                onClick={() => data?.onDeleteRelation?.()}
                className="nodrag rounded p-0.5 text-[var(--dt-text-soft)] transition-colors hover:bg-[var(--dt-status-danger-surface)] hover:text-[var(--dt-status-danger)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--dt-interaction-focus-ring)]"
              >
                <Trash2Icon className="size-3" aria-hidden="true" />
              </button>
            </>
          ) : (
            <>
              {relationLabel}
              {isVirtual ? (
                <span className="ml-1 inline-flex h-4 rounded-sm border border-border-subtle bg-accent-warn-surface px-1 text-[10px] leading-4 text-accent-warn">
                  virtual
                </span>
              ) : null}
            </>
          )}
        </div>
      </EdgeLabelRenderer>
    </>
  )
}

export const MemoErEdge = memo(ErEdge)
MemoErEdge.displayName = 'ErEdge'

function isRelationType(value: string): value is ErDesignerRelationDraft['type'] {
  return RELATION_TYPE_OPTIONS.some((option) => option.value === value)
}

function alignEndpointToNodeBorder(
  nodeLookup: NodeLookupLike,
  nodeId: string,
  x: number,
  y: number,
  position: Position,
): { x: number; y: number } {
  const node = nodeLookup.get(nodeId) as ErNodeLookupEntry | undefined
  const absolute = node?.internals?.positionAbsolute
  const width = node?.measured?.width
  if (!absolute || typeof width !== 'number') return { x, y }

  if (position === Position.Left) return { x: absolute.x, y }
  if (position === Position.Right) return { x: absolute.x + width, y }
  return { x, y }
}
