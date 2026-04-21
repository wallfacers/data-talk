import type { SqlExecuteResultItem } from '@/services/api/sql'
import { cn } from '@/lib/utils'

type SqlResultTabsProps = {
  results: SqlExecuteResultItem[]
  activeResultId: string | null
  onSelect: (resultId: string) => void
}

export function SqlResultTabs({ results, activeResultId, onSelect }: SqlResultTabsProps) {
  return (
    <div className="flex h-[39px] shrink-0 items-end overflow-x-auto border-b border-border/50 bg-muted/20 px-2">
      <div role="tablist" className="flex min-w-0 items-end">
        {results.map((result) => {
          const isActive = result.resultId === activeResultId
          const activeToneClass =
            result.kind === 'error'
              ? 'border-t-destructive text-destructive'
              : 'border-t-foreground/80 text-foreground'
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
                'flex h-[38px] max-w-[240px] shrink-0 items-center border-r px-3 pt-[1px] text-xs transition-colors duration-150',
                isActive
                  ? cn('border-r-border/50 border-t-[3px] bg-background', activeToneClass)
                  : 'border-r-border/30 border-t-[3px] border-t-transparent bg-muted/40 text-muted-foreground hover:bg-muted/65 hover:text-foreground',
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
