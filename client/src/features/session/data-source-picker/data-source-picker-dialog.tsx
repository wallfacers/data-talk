import { useLayoutEffect, useMemo, useState } from 'react'
import { CheckCircle2Icon, SearchIcon, XCircleIcon } from 'lucide-react'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { ContextMenu, ContextMenuContent, ContextMenuItem, ContextMenuTrigger } from '@/components/ui/context-menu'
import type { Connection } from '@/services/api/connection'
import { SETTINGS_DIALOG_DIMENSIONS } from '@/features/settings/shared/utils'
import { useI18n } from '@/i18n/use-i18n'
import { cn } from '@/lib/utils'
import { openOrFocusOpLogTab } from '@/features/op-log/utils/open-op-log-tab'
import { useStageStore } from '@/stores/stage-store'
import { rankConnections } from './recent-connections'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  connections: Connection[]
  recentConnectionIds: string[]
  preferredConnectionId: string | null
  onPick: (connection: Connection) => void
}

export function DataSourcePickerDialog({
  open,
  onOpenChange,
  connections,
  recentConnectionIds,
  preferredConnectionId,
  onPick,
}: Props) {
  const { t } = useI18n()
  const [q, setQ] = useState('')

  useLayoutEffect(() => {
    if (!open) return
    setQ('')
  }, [open])

  const ranked = useMemo(
    () => rankConnections(connections, recentConnectionIds, q),
    [connections, recentConnectionIds, q],
  )

  const ordered = useMemo(() => {
    if (!preferredConnectionId) return ranked
    const preferred = ranked.find((connection) => connection.id === preferredConnectionId)
    if (!preferred) return ranked
    return [preferred, ...ranked.filter((connection) => connection.id !== preferredConnectionId)]
  }, [ranked, preferredConnectionId])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        showCloseButton={false}
        className={`flex flex-col !p-0 overflow-hidden ${SETTINGS_DIALOG_DIMENSIONS}`}
      >
        <DialogHeader className="flex flex-row items-center justify-between gap-4 border-b px-6 py-4">
          <DialogTitle className="text-lg font-medium">{t('dataSources.select')}</DialogTitle>
          <div className="relative w-60">
            <SearchIcon className="absolute left-2 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="h-8 pl-8"
              placeholder={t('dataSources.searchPlaceholder')}
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </div>
        </DialogHeader>

        <div className="flex flex-1 flex-col overflow-y-auto p-4">
          {ordered.length ? (
            <div className="flex flex-col gap-2">
              {ordered.map((connection) => (
                <ContextMenu key={connection.id}>
                  <ContextMenuTrigger
                    aria-label={connection.name}
                    onClick={() => {
                      onPick(connection)
                      onOpenChange(false)
                    }}
                    className={cn(
                      'flex w-full items-start justify-between rounded-lg border px-3 py-3 text-left hover:bg-accent/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
                      preferredConnectionId === connection.id && 'border-primary/40 bg-accent/30',
                    )}
                  >
                    <div className="min-w-0">
                      <div className="truncate text-sm font-medium">{connection.name}</div>
                      <div className="truncate text-xs text-muted-foreground">
                        {formatConnectionMeta(connection)}
                      </div>
                    </div>
                    <StatusBadge
                      status={connection.lastTestStatus}
                      okLabel={t('dataSources.status.ok')}
                      failedLabel={t('dataSources.status.failed')}
                    />
                  </ContextMenuTrigger>
                  <ContextMenuContent className="bg-bg-elevated border border-border-default">
                    <ContextMenuItem
                      className="text-text-base"
                      onClick={() => {
                        openOrFocusOpLogTab({
                          getState: useStageStore.getState,
                          connectionId: connection.id,
                          connectionName: connection.name,
                        })
                      }}
                    >
                      View Operation Log
                    </ContextMenuItem>
                  </ContextMenuContent>
                </ContextMenu>
              ))}
            </div>
          ) : (
            <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
              {t('dataSources.emptySearch')}
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}

function formatConnectionMeta(connection: Connection) {
  const kind = connection.kind.toUpperCase()
  if (connection.kind.toLowerCase() === 'duckdb') {
    const db = connection.databaseName ?? ''
    return [kind, db === ':memory:' ? 'In-memory' : db || 'In-memory'].join(' · ')
  }
  return [kind, connection.host, connection.databaseName ?? ''].filter(Boolean).join(' · ')
}

function StatusBadge({
  status,
  okLabel,
  failedLabel,
}: {
  status: string | null
  okLabel: string
  failedLabel: string
}) {
  if (status === 'ok') {
    return (
      <span className="inline-flex items-center gap-1 text-xs text-green-600">
        <CheckCircle2Icon className="size-3.5" />
        {okLabel}
      </span>
    )
  }
  if (status === 'fail') {
    return (
      <span className="inline-flex items-center gap-1 text-xs text-red-600">
        <XCircleIcon className="size-3.5" />
        {failedLabel}
      </span>
    )
  }
  return null
}
