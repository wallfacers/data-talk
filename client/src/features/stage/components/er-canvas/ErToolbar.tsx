import type { ReactNode } from 'react'
import {
  GitForkIcon,
  LayoutTemplateIcon,
  MaximizeIcon,
  PlusIcon,
  RefreshCwIcon,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'

interface ErToolbarInspectorProps {
  mode: 'inspector'
  neighborDepth: 0 | 1 | 2
  onRefresh: () => void
  onAutoLayout: () => void
  onFitView: () => void
  onChangeNeighborDepth: (depth: 0 | 1 | 2) => void
  onAddVirtualRelation: () => void
  onForkToDesigner: () => void
}

export type ErToolbarProps = ErToolbarInspectorProps

export function ErToolbar(props: ErToolbarProps) {
  const { t } = useI18n()

  return (
    <div className="flex min-h-10 items-center gap-1 border-b border-border-subtle bg-bg-subtle px-2 py-1">
      <ToolbarButton onClick={props.onRefresh} icon={<RefreshCwIcon />} label={t('erCanvas.toolbar.refresh')} />
      <ToolbarButton onClick={props.onAutoLayout} icon={<LayoutTemplateIcon />} label={t('erCanvas.toolbar.autoLayout')} />
      <ToolbarButton onClick={props.onFitView} icon={<MaximizeIcon />} label={t('erCanvas.toolbar.fitView')} />

      <Separator />

      <label className="flex items-center gap-1.5 text-xs text-text-muted">
        <span>{t('erCanvas.toolbar.neighborDepth')}</span>
        <select
          aria-label={t('erCanvas.toolbar.neighborDepth')}
          value={props.neighborDepth}
          onChange={(event) => props.onChangeNeighborDepth(Number(event.target.value) as 0 | 1 | 2)}
          className="h-7 rounded-md border border-border-default bg-bg-canvas px-2 text-xs text-text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <option value={0}>0</option>
          <option value={1}>1</option>
          <option value={2}>2</option>
        </select>
      </label>

      <Separator />

      <ToolbarButton
        onClick={props.onAddVirtualRelation}
        icon={<PlusIcon />}
        label={t('erCanvas.toolbar.addVirtualRelation')}
      />

      <div className="ml-auto" />

      <ToolbarButton
        onClick={props.onForkToDesigner}
        icon={<GitForkIcon />}
        label={t('erCanvas.toolbar.forkToDesigner')}
        primary
      />
    </div>
  )
}

function ToolbarButton({
  onClick,
  icon,
  label,
  primary,
}: {
  onClick: () => void
  icon: ReactNode
  label: string
  primary?: boolean
}) {
  return (
    <Button
      type="button"
      size="sm"
      variant={primary ? 'outline' : 'ghost'}
      onClick={onClick}
      aria-label={label}
      className={primary ? 'border-primary text-primary hover:bg-primary/10' : 'text-text-muted hover:text-text-strong'}
    >
      {icon}
      <span>{label}</span>
    </Button>
  )
}

function Separator() {
  return <div className="mx-1 h-4 w-px bg-border-default" />
}
