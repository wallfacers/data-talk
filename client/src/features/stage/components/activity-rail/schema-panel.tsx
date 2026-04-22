import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
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

const KIND_LABELS: Record<SchemaPanelItem['kind'], string> = {
  connection: 'Connection',
  database: 'Database',
  schema: 'Schema',
  table: 'Table',
  column: 'Column',
}

export function SchemaPanel({ items, onSetTabContext, onInsertText }: SchemaPanelProps) {
  return (
    <div data-testid="schema-panel" className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <div className="text-sm font-medium text-foreground">Schema</div>
          <div className="text-xs text-muted-foreground">Click context nodes, double-click leaf nodes to insert.</div>
        </div>
        <Badge variant="outline" className="shrink-0">
          {items.length}
        </Badge>
      </div>

      {items.length > 0 ? (
        <div role="tree" aria-label="Schema tree" className="space-y-1">
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
                    {KIND_LABELS[item.kind]}
                  </Badge>
                  <span className="min-w-0 truncate">{item.label}</span>
                </Button>
              </div>
            )
          })}
        </div>
      ) : (
        <div className="rounded-lg border border-dashed border-border/60 bg-muted/20 px-3 py-4 text-xs text-muted-foreground">
          No schema context available.
        </div>
      )}
    </div>
  )
}
