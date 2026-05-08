import { useId, type ReactNode } from 'react'
import {
  Code,
  Diff,
  GitFork,
  LayoutTemplate,
  Link,
  LockIcon,
  Maximize,
  PencilIcon,
  Plus,
  RefreshCw,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
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

type DesignerDialect = 'mysql' | 'postgresql' | 'h2' | 'sqlite' | 'mariadb'
const DESIGNER_DIALECT_OPTIONS: Array<{ value: DesignerDialect; label: string }> = [
  { value: 'mysql', label: 'MySQL' },
  { value: 'postgresql', label: 'PostgreSQL' },
  { value: 'h2', label: 'H2' },
  { value: 'sqlite', label: 'SQLite' },
  { value: 'mariadb', label: 'MariaDB' },
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
    const disabledHint = label('erCanvas.toolbar.disabledHint.bindFirst', 'Bind a target database first')

    return (
      <div className="flex min-h-10 items-center gap-2 border-b border-border-subtle bg-bg-subtle px-3 py-1">
        <ModeBadge mode="designer" label={label('erCanvas.toolbar.modeBadge.designer', 'Designer')} />

        <ToolbarButton
          onClick={props.onAddTable}
          icon={<Plus className="size-4" />}
          label={label('erCanvas.toolbar.addTable', 'Add table')}
          testId="er-toolbar-add-table"
        />
        <ToolbarButton
          onClick={props.onAutoLayout}
          icon={<LayoutTemplate className="size-4" />}
          label={label('erCanvas.toolbar.autoLayout', 'Auto layout')}
          testId="er-toolbar-auto-layout"
        />
        <ToolbarButton
          onClick={props.onFitView}
          icon={<Maximize className="size-4" />}
          label={label('erCanvas.toolbar.fitView', 'Fit view')}
          testId="er-toolbar-fit-view"
        />

        <Separator />

        <ToolbarButton
          onClick={props.onBindTarget}
          icon={<Link className="size-4" />}
          label={label('erCanvas.toolbar.bindTarget', 'Bind target')}
          primary
          testId="er-toolbar-bind-target"
        />
        <ToolbarButton
          onClick={props.onDiffVsDb}
          icon={<Diff className="size-4" />}
          label={label('erCanvas.toolbar.diffVsDb', 'Diff vs DB')}
          disabledHint={disabledHint}
          primary
          disabled={!props.hasTarget}
          testId="er-toolbar-diff"
        />
        <ToolbarButton
          onClick={props.onGenerateDdl}
          icon={<Code className="size-4" />}
          label={label('erCanvas.toolbar.generateDdl', 'Generate DDL')}
          disabledHint={disabledHint}
          primary
          disabled={!props.hasTarget}
          testId="er-toolbar-generate-ddl"
        />

        <div className="ml-auto" />

        <Tooltip>
          <TooltipTrigger render={
            <span className="sr-only">{label('erCanvas.toolbar.dialect', 'Dialect')}</span>
          }>
            <Select
              value={props.dialect}
              onValueChange={(value) => props.onChangeDialect(value as DesignerDialect)}
            >
              <SelectTrigger
                size="sm"
                data-testid="er-toolbar-dialect"
                aria-label={label('erCanvas.toolbar.dialect', 'Dialect')}
                className="min-w-28 rounded-md border-border-default bg-bg-canvas px-2 text-xs text-text-base"
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
          </TooltipTrigger>
          <TooltipContent side="bottom" sideOffset={4}>
            {label('erCanvas.toolbar.dialect', 'Dialect')}
          </TooltipContent>
        </Tooltip>
      </div>
    )
  }

  return (
    <div className="flex min-h-10 items-center gap-2 border-b border-border-subtle bg-bg-subtle px-3 py-1">
      <ModeBadge mode="inspector" label={label('erCanvas.toolbar.modeBadge.viewer', 'Viewer')} />

      <ToolbarButton
        onClick={props.onRefresh}
        icon={<RefreshCw className="size-4" />}
        label={label('erCanvas.toolbar.refresh', 'Refresh')}
        testId="er-toolbar-refresh"
      />
      <ToolbarButton
        onClick={props.onAutoLayout}
        icon={<LayoutTemplate className="size-4" />}
        label={label('erCanvas.toolbar.autoLayout', 'Auto layout')}
        testId="er-toolbar-auto-layout"
      />
      <ToolbarButton
        onClick={props.onFitView}
        icon={<Maximize className="size-4" />}
        label={label('erCanvas.toolbar.fitView', 'Fit view')}
        testId="er-toolbar-fit-view"
      />

      <Separator />

      <Tooltip>
        <TooltipTrigger render={
          <span className="sr-only">{label('erCanvas.toolbar.neighborDepth', 'Neighbor depth')}</span>
        }>
          <select
            data-testid="er-toolbar-neighbor-depth"
            aria-label={label('erCanvas.toolbar.neighborDepth', 'Neighbor depth')}
            value={props.neighborDepth}
            onChange={(event) => props.onChangeNeighborDepth(Number(event.target.value) as 0 | 1 | 2)}
            className="h-7 rounded-md border border-border-default bg-bg-canvas px-2 text-xs text-text-base outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing"
          >
            <option value={0}>0</option>
            <option value={1}>1</option>
            <option value={2}>2</option>
          </select>
        </TooltipTrigger>
        <TooltipContent side="bottom" sideOffset={4}>
          {label('erCanvas.toolbar.neighborDepth', 'Neighbor depth')}
        </TooltipContent>
      </Tooltip>

      <Separator />

      <ToolbarButton
        onClick={props.onAddVirtualRelation}
        icon={<Plus className="size-4" />}
        label={label('erCanvas.toolbar.addVirtualRelation', 'Add virtual relation')}
        testId="er-toolbar-add-virtual-relation"
      />

      <div className="ml-auto" />

      <ToolbarButton
        onClick={props.onForkToDesigner}
        icon={<GitFork className="size-4" />}
        label={label('erCanvas.toolbar.forkToDesigner', 'Fork to designer')}
        primary
        testId="er-toolbar-fork-to-designer"
      />
    </div>
  )
}

function ModeBadge({ mode, label }: { mode: ErToolbarProps['mode']; label: string }) {
  const isDesigner = mode === 'designer'
  const Icon = isDesigner ? PencilIcon : LockIcon

  return (
    <Tooltip>
      <TooltipTrigger render={
        <span
          role="status"
          aria-live="off"
          data-testid="er-mode-badge"
          data-er-mode={mode}
          className={
            isDesigner
              ? 'inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-accent-primary-surface text-xs font-medium text-accent-primary'
              : 'inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-bg-subtle text-xs font-medium text-text-muted'
          }
        >
          <Icon className="size-3.5" />
        </span>
      } />
      <TooltipContent side="bottom" sideOffset={4}>
        {label}
      </TooltipContent>
    </Tooltip>
  )
}

function ToolbarButton({
  onClick,
  icon,
  label,
  primary,
  disabled,
  disabledHint,
  testId,
}: {
  onClick: () => void
  icon: ReactNode
  label: string
  primary?: boolean
  disabled?: boolean
  disabledHint?: string
  testId?: string
}) {
  const disabledHintId = useId()
  const hasDisabledHint = Boolean(disabled && disabledHint)
  const button = (
    <Button
      type="button"
      size="sm"
      variant={primary ? 'outline' : 'ghost'}
      onClick={onClick}
      aria-label={label}
      aria-describedby={hasDisabledHint ? disabledHintId : undefined}
      disabled={disabled}
      data-testid={testId}
      className={[
        primary
          ? 'h-7 w-7 rounded-md border-accent-primary p-0 text-xs text-accent-primary hover:bg-accent-primary-surface disabled:border-interaction-disabled disabled:text-interaction-disabled'
          : 'h-7 w-7 rounded-md p-2 text-xs text-text-muted hover:bg-interaction-hover hover:text-text-strong disabled:text-interaction-disabled',
        hasDisabledHint ? 'pointer-events-none' : '',
      ].join(' ')}
    >
      {icon}
    </Button>
  )

  if (hasDisabledHint) {
    return (
      <Tooltip>
        <TooltipTrigger render={<span className="inline-flex cursor-not-allowed pointer-events-auto" />}>
          {button}
        </TooltipTrigger>
        <TooltipContent>{disabledHint}</TooltipContent>
        <span id={disabledHintId} className="sr-only">{disabledHint}</span>
      </Tooltip>
    )
  }

  return (
    <Tooltip>
      <TooltipTrigger render={button} />
      <TooltipContent side="bottom" sideOffset={4}>{label}</TooltipContent>
    </Tooltip>
  )
}

function Separator() {
  return <div className="mx-1 h-4 w-px bg-border-subtle" aria-hidden="true" />
}

function useFallbackLabel(t: ReturnType<typeof useI18n>['t']) {
  return (key: string, fallback: string) => {
    const translated = t(key as Parameters<typeof t>[0])
    return translated === key ? fallback : translated
  }
}
