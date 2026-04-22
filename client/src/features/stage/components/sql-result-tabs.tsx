import type { SqlExecuteResultItem } from '@/services/api/sql'
import { cn } from '@/lib/utils'

type SqlResultTabsProps = {
  results: SqlExecuteResultItem[]
  activeResultId: string | null
  onSelect: (resultId: string) => void
}

export function SqlResultTabs({ results, activeResultId, onSelect }: SqlResultTabsProps) {
  return (
    <div className="flex h-[39px] shrink-0 items-end overflow-x-auto overflow-y-hidden border-b border-border/50 bg-muted/20 px-2">
      <div role="tablist" className="flex min-w-max items-end">
        {results.map((result) => {
          const isActive = result.resultId === activeResultId
          const activeToneClass =
            result.kind === 'error'
              ? 'data-[state=active]:border-b-destructive text-destructive'
              : 'data-[state=active]:border-b-foreground text-foreground'
          return (
            <button
              key={result.resultId}
              type="button"
              role="tab"
              aria-selected={isActive}
              data-state={isActive ? 'active' : 'inactive'}
              data-kind={result.kind}
              title={result.title}
              className={cn(
                'relative flex h-[38px] max-w-[240px] shrink-0 items-center border-b-2 border-b-transparent px-3 pt-[1px] text-xs transition-colors duration-150',
                isActive
                  ? cn('bg-background', activeToneClass)
                  : 'text-muted-foreground hover:border-b-border/60 hover:text-foreground',
              )}
              onClick={() => onSelect(result.resultId)}
            >
              <span className="truncate">{result.title}</span>
            </button>
          )
        })}
      </div>
    </div>
  )
}
