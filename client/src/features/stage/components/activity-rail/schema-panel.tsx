import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { cn } from '@/lib/utils'

export type SchemaPanelContext = {
  connectionId: string
  connectionName: string | null
  database: string | null
  schema: string | null
}

export type SchemaPanelItem =
  | {
      id: string
      kind: 'connection' | 'database' | 'schema'
      label: string
      context: SchemaPanelContext
    }
  | {
      id: string
      kind: 'table' | 'column'
      label: string
      insertText: string
    }

type SchemaPanelProps = {
  items: SchemaPanelItem[]
  onSetTabContext: (context: SchemaPanelContext) => void
  onInsertText: (text: string) => void
}

export function SchemaPanel({ items, onSetTabContext, onInsertText }: SchemaPanelProps) {
  const { t } = useI18n()
  const kindLabels: Record<SchemaPanelItem['kind'], string> = {
    connection: t('stage.activityRail.schema.kind.connection'),
    database: t('stage.activityRail.schema.kind.database'),
    schema: t('stage.activityRail.schema.kind.schema'),
    table: t('stage.activityRail.schema.kind.table'),
    column: t('stage.activityRail.schema.kind.column'),
  }

  return (
    <div data-testid="schema-panel" className="space-y-3">
      {items.length > 0 ? (
        <div role="tree" aria-label={t('stage.activityRail.schema.tree')} className="space-y-1">
          {items.map((item) => {
            const isInsertNode = item.kind === 'table' || item.kind === 'column'
            return (
              <div key={item.id} role="treeitem" aria-level={1}>
                <Button
                  type="button"
                  variant="ghost"
                  aria-label={item.label}
                  className={cn(
                    'h-auto w-full justify-start gap-2 rounded-md px-2 py-1.5 text-left text-xs',
                    isInsertNode ? 'text-muted-foreground' : 'text-foreground',
                  )}
                  onClick={() => {
                    if (item.kind === 'connection' || item.kind === 'database' || item.kind === 'schema') {
                      onSetTabContext(item.context)
                    }
                  }}
                  onDoubleClick={() => {
                    if (item.kind === 'table' || item.kind === 'column') {
                      onInsertText(item.insertText)
                    }
                  }}
                >
                  <Badge variant={isInsertNode ? 'secondary' : 'outline'} className="shrink-0">
                    {kindLabels[item.kind]}
                  </Badge>
                  <span className="min-w-0 truncate">{item.label}</span>
                </Button>
              </div>
            )
          })}
        </div>
      ) : (
        <div className="rounded-lg border border-dashed border-border/60 bg-muted/20 px-3 py-4 text-xs text-muted-foreground">
          {t('stage.activityRail.schema.empty')}
        </div>
      )}
    </div>
  )
}
