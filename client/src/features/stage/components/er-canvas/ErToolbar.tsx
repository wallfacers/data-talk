import type { ReactNode } from 'react'
import {
  CodeIcon,
  DiffIcon,
  GitForkIcon,
  LayoutTemplateIcon,
  LinkIcon,
  MaximizeIcon,
  PlusIcon,
  RefreshCwIcon,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select'
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

type DesignerDialect = 'mysql' | 'postgresql' | 'h2' | 'sqlite'
const DESIGNER_DIALECT_OPTIONS: Array<{ value: DesignerDialect; label: string }> = [
  { value: 'mysql', label: 'MySQL' },
  { value: 'postgresql', label: 'PostgreSQL' },
  { value: 'h2', label: 'H2' },
  { value: 'sqlite', label: 'SQLite' },
]

interface ErToolbarDesignerProps {
  mode: 'designer'
  dialect: DesignerDialect
  hasTarget: boolean
  onAddTable: () => void
  onAutoLayout: () => void
  onFitView: () => void
  onBindTarget: () => void
  onDiffVsDb: () => void
  onGenerateDdl: () => void
  onChangeDialect: (dialect: DesignerDialect) => void
}

export type ErToolbarProps = ErToolbarInspectorProps | ErToolbarDesignerProps

export function ErToolbar(props: ErToolbarProps) {
  const { t } = useI18n()
  const label = useFallbackLabel(t)

  if (props.mode === 'designer') {
    return (
      <div className="flex min-h-10 items-center gap-1 border-b border-border-subtle bg-bg-subtle px-2 py-1">
        <ToolbarButton
          onClick={props.onAddTable}
          icon={<PlusIcon />}
          label={label('erCanvas.toolbar.addTable', 'Add table')}
        />
        <ToolbarButton onClick={props.onAutoLayout} icon={<LayoutTemplateIcon />} label={t('erCanvas.toolbar.autoLayout')} />
        <ToolbarButton onClick={props.onFitView} icon={<MaximizeIcon />} label={t('erCanvas.toolbar.fitView')} />

        <Separator />

        <ToolbarButton
          onClick={props.onBindTarget}
          icon={<LinkIcon />}
          label={label('erCanvas.toolbar.bindTarget', 'Bind target')}
          primary
        />
        <ToolbarButton
          onClick={props.onDiffVsDb}
          icon={<DiffIcon />}
          label={label('erCanvas.toolbar.diffVsDb', 'Diff vs DB')}
          disabled={!props.hasTarget}
        />
        <ToolbarButton
          onClick={props.onGenerateDdl}
          icon={<CodeIcon />}
          label={label('erCanvas.toolbar.generateDdl', 'Generate DDL')}
          primary
          disabled={!props.hasTarget}
        />

        <div className="ml-auto" />

        <label className="flex items-center gap-1.5 text-xs text-text-muted">
          <span>{label('erCanvas.toolbar.dialect', 'Dialect')}</span>
          <Select
            value={props.dialect}
            onValueChange={(value) => props.onChangeDialect(value as DesignerDialect)}
          >
            <SelectTrigger
              size="sm"
              aria-label={label('erCanvas.toolbar.dialect', 'Dialect')}
              className="min-w-28 border-border-default bg-bg-canvas px-2 text-xs text-text-base"
            >
              <span className="flex flex-1 text-left">
                {DESIGNER_DIALECT_OPTIONS.find((option) => option.value === props.dialect)?.label ?? props.dialect}
              </span>
            </SelectTrigger>
            <SelectContent>
              {DESIGNER_DIALECT_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </label>
      </div>
    )
  }

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
  disabled,
}: {
  onClick: () => void
  icon: ReactNode
  label: string
  primary?: boolean
  disabled?: boolean
}) {
  return (
    <Button
      type="button"
      size="sm"
      variant={primary ? 'outline' : 'ghost'}
      onClick={onClick}
      aria-label={label}
      disabled={disabled}
      className={
        primary
          ? 'border-[var(--dt-accent-primary)] text-[var(--dt-accent-primary)] hover:bg-[var(--dt-accent-primary-surface)]'
          : 'text-text-muted hover:text-text-strong'
      }
    >
      {icon}
      <span>{label}</span>
    </Button>
  )
}

function Separator() {
  return <div className="mx-1 h-4 w-px bg-border-default" />
}

function useFallbackLabel(t: ReturnType<typeof useI18n>['t']) {
  return (key: string, fallback: string) => {
    const translated = t(key as Parameters<typeof t>[0])
    return translated === key ? fallback : translated
  }
}
