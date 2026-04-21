import { useState } from 'react'
import { RefreshCwIcon, XIcon } from 'lucide-react'
import { DataGrid } from '@/features/data-grid/components/data-grid'
import { Button } from '@/components/ui/button'
import { useConnectionStore } from '@/features/connection/store'
import { useStageStore } from '@/stores/stage-store'
import { BangQueryAdapter } from '@/features/stage/adapters/BangQueryAdapter'
import { showErrorToast, normalizeError } from '@/services/http-error'
import { useI18n } from '@/i18n/use-i18n'
import { resolveTabDataContext } from '@/features/stage/utils/resolve-tab-data-context'

interface BangPayload {
  sql: string
  rows?: Array<Record<string, unknown>>
  lastRun?: { columns: string[]; rowCount: number; durationMs: number; truncated: boolean }
  connectionId?: string
  connectionName?: string
  database?: string
  schema?: string
  contextNotice?: string | null
}

export function BangQueryTab({ tabId }: { tabId: string }) {
  const { t } = useI18n()
  const tab = useStageStore((s) => s.workspaceTabs.find((x) => x.tabId === tabId))
  const setActiveConnection = useConnectionStore((s) => s.setActive)
  const connections = useConnectionStore((s) => s.connections)
  const [expanded, setExpanded] = useState(false)
  const [rerunning, setRerunning] = useState(false)
  if (!tab) return null

  const payload = tab.payload as BangPayload
  const resolvedContext = resolveTabDataContext(
    {
      originSessionId: tab.originSessionId ?? null,
      connectionId: payload.connectionId ?? tab.connectionId ?? null,
      connectionName: payload.connectionName ?? tab.connectionName ?? null,
      database: payload.database ?? tab.database ?? null,
      schema: payload.schema ?? tab.schema ?? null,
    },
    null,
    {
      inheritSessionContext: false,
      connectionNameLookup: (connectionId) => connections.find((connection) => connection.id === connectionId)?.name ?? null,
    },
  )
  const connectionLabel = resolvedContext.connectionName ?? resolvedContext.connectionId ?? ''
  const contextDetails = [resolvedContext.database, resolvedContext.schema].filter(Boolean).join(' / ')
  const rows = payload.rows ?? []
  const columns = (payload.lastRun?.columns ?? []).map((c) => ({ key: c, header: c }))

  const onRerun = async () => {
    setRerunning(true)
    try {
      const adapter = new BangQueryAdapter(tabId)
      const res = await adapter.exec('rerun')
      if (!res.success) showErrorToast(normalizeError(new Error(res.error ?? 'rerun failed')))
    } catch (err) {
      showErrorToast(normalizeError(err))
    } finally {
      setRerunning(false)
    }
  }
  const onClose = () => useStageStore.getState().closeTab(tabId)
  const onUseThisSource = () => {
    if (!tab.connectionId) return
    setActiveConnection(tab.connectionId)
  }

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-2 border-b px-3 py-2 text-xs">
        <span className="rounded border px-1.5 font-mono text-[10px]">{t('bangQuery.label')}</span>
        {tab.connectionId && (
          <button
            type="button"
            onClick={onUseThisSource}
            className="rounded border px-1.5 text-[10px] text-muted-foreground hover:bg-accent/50"
          >
            {connectionLabel}
          </button>
        )}
        {contextDetails && (
          <span className="whitespace-nowrap text-muted-foreground">{contextDetails}</span>
        )}
        {payload.contextNotice && (
          <span className="whitespace-nowrap text-amber-600 dark:text-amber-400">{payload.contextNotice}</span>
        )}
        {tab.connectionId && (
          <Button size="sm" variant="ghost" className="h-6 px-2 text-[10px]" onClick={onUseThisSource}>
            {t('bangQuery.useThisSource')}
          </Button>
        )}
        <div
          className={`flex-1 min-w-0 font-mono text-xs ${expanded ? 'whitespace-pre-wrap' : 'truncate'}`}
          onClick={() => setExpanded((v) => !v)}
          role="button"
          tabIndex={0}
        >
          {payload.sql}
        </div>
        {payload.lastRun && (
          <>
            <span className="text-muted-foreground whitespace-nowrap">{payload.lastRun.rowCount} rows</span>
            <span className="text-muted-foreground whitespace-nowrap">{payload.lastRun.durationMs}ms</span>
          </>
        )}
        <Button size="icon-xs" variant="ghost" onClick={onRerun} disabled={rerunning} aria-label={t('bangQuery.rerun')}>
          <RefreshCwIcon className={`size-3.5 ${rerunning ? 'animate-spin' : ''}`} />
        </Button>
        <Button size="icon-xs" variant="ghost" onClick={onClose} aria-label={t('bangQuery.close')}>
          <XIcon className="size-3.5" />
        </Button>
      </div>
      <div className="flex-1 min-h-0 overflow-hidden">
        <DataGrid columns={columns} rows={rows} />
      </div>
    </div>
  )
}
