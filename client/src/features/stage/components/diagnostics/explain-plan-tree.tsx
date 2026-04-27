import { useState } from 'react'
import { AlertTriangleIcon, CheckIcon, ChevronRightIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { ExplainNode, ScanType } from '@/features/stage/types/diagnostics'

type ExplainPlanTreeProps = {
  nodes: ExplainNode[]
}

function scanTypeStyle(scanType: ScanType): string {
  switch (scanType) {
    case 'FULL_SCAN':
      return 'bg-destructive/10'
    case 'INDEX_SCAN':
    case 'CONST':
      return 'bg-green-500/10'
    case 'REF':
    case 'INDEX_RANGE':
      return 'bg-sky-500/10'
    default:
      return ''
  }
}

function scanTypeIcon(scanType: ScanType) {
  switch (scanType) {
    case 'FULL_SCAN':
      return <AlertTriangleIcon className="size-3.5 shrink-0 text-destructive" />
    case 'INDEX_SCAN':
    case 'CONST':
      return <CheckIcon className="size-3.5 shrink-0 text-green-600" />
    default:
      return null
  }
}

function PlanNode({ node, depth }: { node: ExplainNode; depth: number }) {
  const [expanded, setExpanded] = useState(true)
  const hasChildren = node.children.length > 0

  return (
    <li role="treeitem" aria-expanded={hasChildren ? expanded : undefined}>
      <div
        className={cn(
          'flex items-center gap-1.5 rounded px-2 py-1 text-xs',
          scanTypeStyle(node.scanType),
        )}
        style={{ paddingLeft: depth * 16 + 8 }}
      >
        {hasChildren ? (
          <button
            type="button"
            onClick={() => setExpanded((prev) => !prev)}
            className="shrink-0 rounded p-0.5 hover:bg-muted"
            aria-label={expanded ? 'Collapse' : 'Expand'}
          >
            <ChevronRightIcon
              className={cn('size-3 transition-transform', expanded && 'rotate-90')}
            />
          </button>
        ) : (
          <span className="inline-block w-4 shrink-0" />
        )}

        {scanTypeIcon(node.scanType)}

        <span className="font-medium">{node.operation}</span>

        {node.table && (
          <span className="text-muted-foreground">on {node.table}</span>
        )}

        <span className="ml-auto shrink-0 font-mono tabular-nums text-muted-foreground">
          {node.rows.toLocaleString()} rows
        </span>

        {node.cost != null && (
          <span className="shrink-0 font-mono tabular-nums text-muted-foreground">
            cost {node.cost}
          </span>
        )}
      </div>

      {hasChildren && expanded && (
        <ul role="group">
          {node.children.map((child, i) => (
            <PlanNode key={i} node={child} depth={depth + 1} />
          ))}
        </ul>
      )}
    </li>
  )
}

export function ExplainPlanTree({ nodes }: ExplainPlanTreeProps) {
  if (nodes.length === 0) {
    return <p className="px-2 py-4 text-xs text-muted-foreground">No plan nodes available.</p>
  }

  return (
    <ul role="tree" aria-label="Explain plan tree" className="space-y-0.5">
      {nodes.map((node, i) => (
        <PlanNode key={i} node={node} depth={0} />
      ))}
    </ul>
  )
}
