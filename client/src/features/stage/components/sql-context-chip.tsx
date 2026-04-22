import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

export type SqlContextValue = {
  connectionId: string
  connectionName: string | null
  database: string | null
  schema: string | null
}

type SqlContextChipProps = {
  mode: 'session' | 'override'
  context: SqlContextValue | null
  onSetTabContext: (context: SqlContextValue) => void
  onResetTabContext: () => void
}

function formatContextSummary(context: SqlContextValue | null) {
  if (!context) return null
  return [context.connectionName ?? context.connectionId, context.database, context.schema]
    .filter(Boolean)
    .join(' / ')
}

export function SqlContextChip({
  mode,
  context,
  onSetTabContext,
  onResetTabContext,
}: SqlContextChipProps) {
  const { t } = useI18n()
  const summary = formatContextSummary(context)

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            variant={mode === 'override' ? 'secondary' : 'outline'}
            size="sm"
            className="max-w-full gap-2"
            disabled={!context}
          >
            <Badge variant={mode === 'override' ? 'default' : 'outline'} className="shrink-0">
              {mode === 'override' ? t('stage.context.badge.override') : t('stage.context.badge.session')}
            </Badge>
            <span className="truncate">
              {mode === 'override' ? t('stage.context.label.override') : t('stage.context.label.session')}
            </span>
            {summary ? <span className="truncate text-xs text-muted-foreground">{summary}</span> : null}
          </Button>
        }
      />
      <DropdownMenuContent align="start" className="w-52">
        {mode === 'override' ? (
          <DropdownMenuItem onClick={onResetTabContext}>
            <span>{t('stage.context.action.useSession')}</span>
          </DropdownMenuItem>
        ) : (
          <DropdownMenuItem onClick={() => context && onSetTabContext(context)}>
            <span>{t('stage.context.action.pinCurrent')}</span>
          </DropdownMenuItem>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
