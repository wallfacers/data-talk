import { useState } from 'react'
import { AlertTriangleIcon, CheckIcon, ChevronRightIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import type { ExplainNode, ScanType } from '@/features/stage/types/diagnostics'

type ExplainPlanTreeProps = {
  nodes: ExplainNode[]
}

function scanTypeStyle(scanType: ScanType): string {
  switch (scanType) {
    case 'FULL_SCAN':
      return 'bg-status-danger-surface'
    case 'INDEX_SCAN':
    case 'CONST':
      return 'bg-status-success-surface'
    case 'REF':
    case 'INDEX_RANGE':
      return 'bg-accent-primary/10'
    default:
      return ''
  }
}

function scanTypeIcon(scanType: ScanType) {
  switch (scanType) {
    case 'FULL_SCAN':
      return <AlertTriangleIcon className="size-3.5 shrink-0 text-status-danger" />
    case 'INDEX_SCAN':
    case 'CONST':
      return <CheckIcon className="size-3.5 shrink-0 text-status-success" />
    default:
      return null
  }
}

function PlanNode({ node, depth }: { node: ExplainNode; depth: number }) {
  const [expanded, setExpanded] = useState(true)
  const { t } = useI18n()
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
          <Tooltip>
            <TooltipTrigger
              render={
                <button
                  type="button"
                  onClick={() => setExpanded((prev) => !prev)}
                  className="shrink-0 rounded p-0.5 hover:bg-muted"
                  aria-label={expanded ? t('diagnostics.collapse') : t('diagnostics.expand')}
                >
                  <ChevronRightIcon
                    className={cn('size-3 transition-transform', expanded && 'rotate-90')}
                  />
                </button>
              }
            />
            <TooltipContent>{expanded ? t('diagnostics.collapse') : t('diagnostics.expand')}</TooltipContent>
          </Tooltip>
        ) : (
          <span className="inline-block w-4 shrink-0" />
        )}

        {scanTypeIcon(node.scanType)}

        <span className="font-medium">{node.operation}</span>

        {node.table && (
          <span className="text-muted-foreground">{t('diagnostics.on')} {node.table}</span>
        )}

        <span className="ml-auto shrink-0 font-mono tabular-nums text-muted-foreground">
          {node.rows.toLocaleString()} {t('diagnostics.rows')}
        </span>

        {node.cost != null && (
          <span className="shrink-0 font-mono tabular-nums text-muted-foreground">
            {t('diagnostics.cost')} {node.cost}
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
  const { t } = useI18n()

  if (nodes.length === 0) {
    return <p className="px-2 py-4 text-xs text-muted-foreground">{t('diagnostics.noPlanNodes')}</p>
  }

  return (
    <ul role="tree" aria-label={t('diagnostics.explainPlanTree')} className="space-y-0.5">
      {nodes.map((node, i) => (
        <PlanNode key={i} node={node} depth={0} />
      ))}
    </ul>
  )
}
