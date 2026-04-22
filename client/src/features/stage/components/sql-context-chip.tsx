import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
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
              {mode === 'override' ? 'Override' : 'Session'}
            </Badge>
            <span className="truncate">{mode === 'override' ? 'Tab override' : 'Session context'}</span>
            {summary ? <span className="truncate text-xs text-muted-foreground">{summary}</span> : null}
          </Button>
        }
      />
      <DropdownMenuContent align="start" className="w-52">
        {mode === 'override' ? (
          <DropdownMenuItem onClick={onResetTabContext}>
            <span>Use session context</span>
          </DropdownMenuItem>
        ) : (
          <DropdownMenuItem onClick={() => context && onSetTabContext(context)}>
            <span>Pin current context</span>
          </DropdownMenuItem>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
