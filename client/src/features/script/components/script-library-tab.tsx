import { useEffect, useState } from 'react'
import { ClockIcon, HashIcon, LayersIcon, SearchIcon, TerminalIcon } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'
import { useConnectionStore } from '@/features/connection/store'
import { scriptApi, type ScriptRunListItem } from '../api/script-api'

const STATUS_COLOR: Record<string, string> = {
  running: 'text-warning',
  completed: 'text-success',
  failed: 'text-error',
  cancelled: 'text-muted',
}

const STATUS_LABEL_KEY: Record<string, string> = {
  running: 'script.library.status.running',
  completed: 'script.library.status.completed',
  failed: 'script.library.status.failed',
  cancelled: 'script.library.status.cancelled',
}

function formatDuration(ms: number | null): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`
  const mins = Math.floor(ms / 60_000)
  const secs = Math.round((ms % 60_000) / 1000)
  return `${mins}m ${secs}s`
}

function matchesSearch(run: ScriptRunListItem, query: string): boolean {
  if (!query) return true
  const haystack = `${run.name ?? ''} ${run.targetTable ?? ''} ${run.language}`.toLowerCase()
  return haystack.includes(query.toLowerCase())
}

export function ScriptLibraryTab() {
  const { t } = useI18n()
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)
  const [runs, setRuns] = useState<ScriptRunListItem[]>([])
  const [loading, setLoading] = useState(false)
  const [query, setQuery] = useState('')

  useEffect(() => {
    if (!activeConnectionId) return
    setLoading(true)
    scriptApi
      .listRuns(activeConnectionId, 200)
      .then((res) => setRuns(res.runs))
      .catch(() => setRuns([]))
      .finally(() => setLoading(false))
  }, [activeConnectionId])

  const filtered = runs.filter((r) => matchesSearch(r, query))

  if (!activeConnectionId) {
    return (
      <div className="flex h-full items-center justify-center bg-canvas px-6 text-sm text-muted">
        {t('script.library.empty.noConnection')}
      </div>
    )
  }

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center bg-canvas px-6 text-sm text-muted">
        {t('common.loading')}
      </div>
    )
  }

  if (runs.length === 0) {
    return (
      <div className="flex h-full items-center justify-center bg-canvas px-6 text-sm text-muted">
        {t('script.library.empty.noRuns')}
      </div>
    )
  }

  return (
    <div className="flex h-full w-full min-h-0 flex-col bg-canvas">
      <div className="flex items-center gap-2 border-b border-subtle bg-subtle px-4 py-2">
        <div className="relative flex-1 max-w-md">
          <SearchIcon
            aria-hidden
            className="pointer-events-none absolute left-2 top-1/2 h-4 w-4 -translate-y-1/2 text-soft"
          />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('script.library.search.placeholder')}
            aria-label={t('script.library.search.placeholder')}
            className="h-8 w-full rounded-md border border-subtle bg-panel pl-8 pr-2 text-sm text-base placeholder:text-soft hover:border-default focus:border-default focus:outline-2 focus:outline-offset-1 focus:outline-focusRing disabled:bg-subtle disabled:text-disabled"
          />
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
        {filtered.length === 0 ? (
          <div className="text-center text-sm text-muted py-8">
            {t('script.library.empty.noMatch')}
          </div>
        ) : (
          <div className="space-y-2">
            {filtered.map((run) => (
              <RunRow key={run.id} run={run} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function RunRow({ run }: { run: ScriptRunListItem }) {
  const { t } = useI18n()

  return (
    <div className="rounded-md border border-subtle bg-panel px-3 py-2">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0">
          <TerminalIcon aria-hidden className="h-4 w-4 text-muted shrink-0" />
          <span className="truncate text-sm text-strong">
            {run.name ?? run.id}
          </span>
          <span className={`text-xs font-medium shrink-0 ${STATUS_COLOR[run.status] ?? 'text-muted'}`}>
            {t(STATUS_LABEL_KEY[run.status] as never)}
          </span>
        </div>
        <span className="text-xs text-muted shrink-0">
          {run.language === 'python' ? 'Python' : 'JavaScript'}
        </span>
      </div>
      <div className="mt-0.5 flex flex-wrap items-center gap-x-4 text-xs text-muted">
        {run.startedAt && (
          <span className="inline-flex items-center gap-1">
            <ClockIcon aria-hidden className="h-3 w-3" />
            {new Date(run.startedAt).toLocaleString()}
          </span>
        )}
        <span className="inline-flex items-center gap-1">
          <HashIcon aria-hidden className="h-3 w-3" />
          {formatDuration(run.durationMs)}
        </span>
        {run.targetTable && (
          <span className="inline-flex items-center gap-1">
            <LayersIcon aria-hidden className="h-3 w-3" />
            {run.targetTable} ({run.rowsWritten} rows)
          </span>
        )}
      </div>
    </div>
  )
}
