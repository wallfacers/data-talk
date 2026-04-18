import { useState, type ReactNode } from 'react'
import { TextShimmer } from '../effects/text-shimmer'
import type { RiskLevel } from '../helpers/risk'
import { getRiskStyles } from '../helpers/risk'
import { cn } from '@/lib/utils'
import './basic-tool.css'

export type TriggerTitle = {
  title: string
  subtitle?: string
  args?: string[]
  action?: ReactNode
}

const isTriggerTitle = (v: unknown): v is TriggerTitle =>
  typeof v === 'object' && v !== null && 'title' in (v as Record<string, unknown>)

export type BasicToolStatus = 'pending' | 'running' | 'completed' | 'error'

export function BasicTool(props: {
  icon: string
  risk?: RiskLevel | null
  variant?: 'risk' | 'question'
  trigger: TriggerTitle | ReactNode
  children?: ReactNode
  status?: BasicToolStatus
  hideDetails?: boolean
  defaultOpen?: boolean
  forceOpen?: boolean
  locked?: boolean
}) {
  const [openState, setOpenState] = useState(props.defaultOpen ?? false)
  const open = props.forceOpen || openState
  const pending = props.status === 'pending' || props.status === 'running'
  const riskStyles = props.variant === 'question' ? null : getRiskStyles(props.risk ?? null)

  const handleToggle = () => {
    if (pending) return
    if (props.locked && open) return
    setOpenState((v) => !v)
  }

  const t = props.trigger
  return (
    <div
      data-component="basic-tool"
      data-status={props.status}
      data-variant={props.variant ?? 'risk'}
      className={cn(
        'rounded-md border my-2',
        riskStyles?.border,
        props.variant === 'question' && 'border-blue-400/50',
      )}
    >
      <button
        type="button"
        data-component="tool-trigger"
        data-open={open ? 'true' : 'false'}
        onClick={handleToggle}
        className="flex w-full items-center gap-2 px-3 py-2 text-left"
      >
        {riskStyles && (
          <span
            data-slot="risk-dot"
            className={cn('size-2 rounded-full', riskStyles.dot)}
            aria-label={riskStyles.label}
          />
        )}
        {props.variant === 'question' && (
          <span data-slot="question-icon" className="text-blue-500">
            ?
          </span>
        )}
        {isTriggerTitle(t) ? (
          <div className="flex min-w-0 flex-1 items-baseline gap-2">
            <span data-slot="basic-tool-tool-title" className="font-medium">
              <TextShimmer text={t.title} active={pending} />
            </span>
            {!pending && t.subtitle && (
              <span
                data-slot="basic-tool-tool-subtitle"
                className="text-xs text-muted-foreground truncate"
              >
                {t.subtitle}
              </span>
            )}
            {!pending &&
              t.args?.map((a, i) => (
                <span
                  key={i}
                  data-slot="basic-tool-tool-arg"
                  className="text-xs font-mono text-muted-foreground"
                >
                  {a}
                </span>
              ))}
          </div>
        ) : (
          t
        )}
        {!pending && !props.hideDetails && !props.locked && props.children && (
          <span
            data-slot="basic-tool-arrow"
            className={cn('transition-transform', open && 'rotate-180')}
          >
            ▼
          </span>
        )}
      </button>
      {open && !props.hideDetails && props.children && (
        <div data-slot="basic-tool-body" className="border-t px-3 py-2">
          {props.children}
        </div>
      )}
    </div>
  )
}
