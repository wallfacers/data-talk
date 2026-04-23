import { useEffect, useRef, useState } from 'react'
import { AlertCircleIcon, ChevronDownIcon, PenSquareIcon, TableIcon, XIcon } from 'lucide-react'
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuSeparator,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'
import type { SqlExecuteResultItem } from '@/services/api/sql'
import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'

type SqlResultTabsProps = {
  results: SqlExecuteResultItem[]
  activeResultId: string | null
  onSelect: (resultId: string) => void
  onClose: (resultId: string) => void
  onCloseOthers: (resultId: string) => void
  onCloseAll: () => void
}

export function SqlResultTabs({
  results,
  activeResultId,
  onSelect,
  onClose,
  onCloseOthers,
  onCloseAll,
}: SqlResultTabsProps) {
  const { t } = useI18n()
  const [overflowOpen, setOverflowOpen] = useState(false)
  const [hasOverflow, setHasOverflow] = useState(false)
  const tabScrollRef = useRef<HTMLDivElement | null>(null)
  const overflowRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    const updateOverflow = () => {
      const container = tabScrollRef.current
      if (!container) {
        setHasOverflow(false)
        return
      }
      setHasOverflow(container.scrollWidth > container.clientWidth + 1)
    }

    updateOverflow()
    const rafId = window.requestAnimationFrame(updateOverflow)
    window.addEventListener('resize', updateOverflow)
    return () => {
      window.cancelAnimationFrame(rafId)
      window.removeEventListener('resize', updateOverflow)
    }
  }, [results])

  useEffect(() => {
    if (!hasOverflow && overflowOpen) {
      setOverflowOpen(false)
    }
  }, [hasOverflow, overflowOpen])

  useEffect(() => {
    if (!overflowOpen) return
    const onPointerDown = (event: PointerEvent) => {
      const root = overflowRef.current
      if (!root) return
      if (root.contains(event.target as Node)) return
      setOverflowOpen(false)
    }
    window.addEventListener('pointerdown', onPointerDown)
    return () => window.removeEventListener('pointerdown', onPointerDown)
  }, [overflowOpen])

  useEffect(() => {
    if (!activeResultId) return
    const container = tabScrollRef.current
    if (!container) return

    const rafId = window.requestAnimationFrame(() => {
      const activeTab = Array.from(container.querySelectorAll<HTMLElement>('[data-result-id]'))
        .find((element) => element.dataset.resultId === activeResultId)
      activeTab?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' })
    })

    return () => window.cancelAnimationFrame(rafId)
  }, [activeResultId, results.length])

  useEffect(() => {
    const container = tabScrollRef.current
    if (!container) return
    const onWheel = (e: WheelEvent) => {
      if (e.deltaY === 0) return
      e.preventDefault()
      container.scrollBy({ left: e.deltaY, behavior: 'smooth' })
    }
    container.addEventListener('wheel', onWheel, { passive: false })
    return () => container.removeEventListener('wheel', onWheel)
  }, [])

  return (
    <div className="flex min-h-[39px] shrink-0 items-end border-b border-border/50 bg-muted/20 px-2">
      <div ref={tabScrollRef} className="min-w-0 flex-1 overflow-x-auto overflow-y-hidden [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        <div role="tablist" className="flex min-w-max items-end">
          {results.map((result) => {
            const isActive = result.resultId === activeResultId
            const hasOthers = results.length > 1
            const hasAnyResults = results.length > 0
            const activeToneClass =
              result.kind === 'error'
                ? 'data-[state=active]:border-b-destructive text-destructive'
                : 'data-[state=active]:border-b-foreground text-foreground'
            const icon =
              result.kind === 'error' ? (
                <AlertCircleIcon className="size-3 shrink-0" />
              ) : result.kind === 'dml_summary' ? (
                <PenSquareIcon className="size-3 shrink-0" />
              ) : (
                <TableIcon className="size-3 shrink-0" />
              )
            return (
              <ContextMenu key={result.resultId}>
                <ContextMenuTrigger
                  render={
                    <div
                      data-result-id={result.resultId}
                      data-state={isActive ? 'active' : 'inactive'}
                      className={cn('group/result relative -mb-px flex', isActive ? 'z-10' : 'z-0')}
                    >
                      <button
                        type="button"
                        role="tab"
                        aria-selected={isActive}
                        data-state={isActive ? 'active' : 'inactive'}
                        data-kind={result.kind}
                        title={result.title}
                        className={cn(
                          'relative flex h-[38px] max-w-[240px] shrink-0 items-center gap-1 border-b-2 border-b-transparent px-3 pt-[1px] text-xs transition-colors duration-150',
                          isActive
                            ? activeToneClass
                            : 'text-muted-foreground hover:border-b-border/60 hover:text-foreground',
                        )}
                        onClick={() => onSelect(result.resultId)}
                      >
                        {icon}
                        <span className="truncate">{result.title}</span>
                        <div
                          role="button"
                          aria-label={t('stage.menu.close')}
                          className={cn(
                            'ml-0.5 flex size-4 items-center justify-center rounded-sm transition-all',
                            isActive
                              ? 'opacity-100 hover:bg-muted/80'
                              : 'opacity-0 group-hover/result:opacity-100 hover:bg-muted/70',
                          )}
                          onClick={(event) => {
                            event.stopPropagation()
                            onClose(result.resultId)
                          }}
                        >
                          <XIcon className="size-3" />
                        </div>
                      </button>
                    </div>
                  }
                />
                <ContextMenuContent className="w-44 font-sans text-xs">
                  <ContextMenuItem
                    disabled={!hasAnyResults}
                    onClick={() => onClose(result.resultId)}
                  >
                    {t('stage.menu.close')}
                  </ContextMenuItem>
                  <ContextMenuItem
                    disabled={!hasOthers}
                    onClick={() => onCloseOthers(result.resultId)}
                  >
                    {t('stage.menu.closeOthers')}
                  </ContextMenuItem>
                  <ContextMenuSeparator />
                  <ContextMenuItem
                    disabled={!hasAnyResults}
                    onClick={() => onCloseAll()}
                  >
                    {t('stage.menu.closeAll')}
                  </ContextMenuItem>
                </ContextMenuContent>
              </ContextMenu>
            )
          })}
        </div>
      </div>

      {results.length > 0 && hasOverflow ? (
        <div ref={overflowRef} className="relative ml-2 flex h-[38px] shrink-0 items-center">
          <button
            type="button"
            data-testid="sql-result-overflow-trigger"
            aria-label={t('stage.tabBar.moreTabs')}
            aria-expanded={overflowOpen}
            onClick={() => setOverflowOpen((prev) => !prev)}
            className="inline-flex size-6 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          >
            <ChevronDownIcon className="size-3.5" />
          </button>

          {overflowOpen ? (
            <div
              data-testid="sql-result-overflow-menu"
              className="absolute right-0 top-[calc(100%+4px)] z-40 w-64 rounded-lg border border-border/70 bg-popover p-1 shadow-md"
            >
              <div className="max-h-64 overflow-y-auto">
                {results.map((result) => {
                  const isActive = result.resultId === activeResultId
                  return (
                    <div key={`overflow-${result.resultId}`} className="group flex items-center gap-1">
                      <button
                        type="button"
                        className={cn(
                          'min-w-0 flex-1 rounded-md px-2 py-1 text-left text-xs transition-colors',
                          isActive
                            ? 'bg-accent/70 text-foreground'
                            : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                        )}
                        onClick={() => {
                          onSelect(result.resultId)
                          setOverflowOpen(false)
                        }}
                      >
                        <span className="truncate">{result.title}</span>
                      </button>
                      <button
                        type="button"
                        aria-label={t('stage.menu.close')}
                        className="inline-flex size-6 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                        onClick={(event) => {
                          event.stopPropagation()
                          onClose(result.resultId)
                        }}
                      >
                        <XIcon className="size-3.5" />
                      </button>
                    </div>
                  )
                })}
              </div>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
