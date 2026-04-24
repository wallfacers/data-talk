import { useState, type ReactNode } from 'react'
import { ChevronDownIcon } from 'lucide-react'
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
      <div className="flex w-full items-start gap-2 px-3 py-2">
        <button
          type="button"
          data-component="tool-trigger"
          data-open={open ? 'true' : 'false'}
          onClick={handleToggle}
          className="flex min-w-0 flex-1 items-start gap-2 text-left"
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
            <div data-slot="basic-tool-trigger-content" className="flex min-w-0 flex-1 flex-col gap-1">
              <div
                data-slot="basic-tool-trigger-heading"
                className="flex min-w-0 flex-wrap items-baseline gap-x-2 gap-y-1"
              >
                <span
                  data-slot="basic-tool-tool-title"
                  className="min-w-0 font-medium [overflow-wrap:anywhere]"
                >
                  <TextShimmer text={t.title} active={pending} />
                </span>
                {!pending && t.subtitle && (
                  <span
                    data-slot="basic-tool-tool-subtitle"
                    className="min-w-0 max-w-full truncate text-xs text-muted-foreground"
                  >
                    {t.subtitle}
                  </span>
                )}
              </div>
              {!pending && t.args && t.args.length > 0 && (
                <div data-slot="basic-tool-trigger-args" className="flex min-w-0 flex-wrap gap-x-2 gap-y-1">
                  {t.args.map((a, i) => (
                    <span
                      key={i}
                      data-slot="basic-tool-tool-arg"
                      className="min-w-0 max-w-full break-all text-xs font-mono text-muted-foreground"
                    >
                      {a}
                    </span>
                  ))}
                </div>
              )}
            </div>
          ) : (
            t
          )}
          {!pending && !props.hideDetails && !props.locked && props.children && (
            <span
              data-slot="basic-tool-arrow"
              className={cn(
                'mt-0.5 flex size-4 shrink-0 items-center justify-center text-muted-foreground transition-transform',
                open && 'rotate-180',
              )}
            >
              <ChevronDownIcon className="pointer-events-none size-4" />
            </span>
          )}
        </button>
        {!pending && isTriggerTitle(t) && t.action && (
          <div data-slot="basic-tool-trigger-action" className="relative z-10 shrink-0">
            {t.action}
          </div>
        )}
      </div>
      {open && !props.hideDetails && props.children && (
        <div data-slot="basic-tool-body" className="border-t px-3 py-2">
          {props.children}
        </div>
      )}
    </div>
  )
}
