import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select'
import { useI18n } from '@/i18n/use-i18n'
import { toast } from 'sonner'
import { getStorageOverview, cleanupTrash, cleanupLegacy, getOrphanedFiles, reattachFile, type OrphanedFileDto } from '@/services/api/maintenance'
import { discardFile } from '@/services/api/file-artifacts'
import { useConnectionStore } from '@/features/connection/store'
import { useState } from 'react'
import { RefreshCw, ArrowLeft } from 'lucide-react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'

function fmtBytes(n: number) {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

export function MaintenancePage() {
  const { t } = useI18n()
  const qc = useQueryClient()
  const [view, setView] = useState<'overview' | 'orphans'>('overview')
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [confirmLegacyOpen, setConfirmLegacyOpen] = useState(false)

  const overview = useQuery({
    queryKey: ['maintenance', 'storage-overview'],
    queryFn: getStorageOverview,
  })

  const orphans = useQuery({
    queryKey: ['maintenance', 'orphaned-files'],
    queryFn: getOrphanedFiles,
  })

  const cleanup = useMutation({
    mutationFn: cleanupTrash,
    onSuccess: () => {
      setConfirmOpen(false)
      toast.success(t('maintenance.toast.cleanupTrashDone'))
      qc.invalidateQueries({ queryKey: ['maintenance'] })
    },
  })

  const cleanupLegacyMut = useMutation({
    mutationFn: cleanupLegacy,
    onSuccess: () => {
      setConfirmLegacyOpen(false)
      toast.success(t('maintenance.toast.cleanupLegacyDone'))
      qc.invalidateQueries({ queryKey: ['maintenance'] })
    },
    onError: (err) => {
      setConfirmLegacyOpen(false)
      toast.error(err instanceof Error ? err.message : String(err))
    },
  })

  if (view === 'orphans') {
    return (
      <OrphanArchivesView
        files={orphans.data ?? []}
        isLoading={orphans.isLoading}
        onRefresh={() => orphans.refetch()}
        onBack={() => { setView('overview'); qc.invalidateQueries({ queryKey: ['maintenance'] }) }}
      />
    )
  }

  if (overview.isLoading) return <div className="text-sm text-muted-foreground">{t('common.loading')}</div>

  const d = overview.data
  if (!d) return null

  const orphanCount = orphans.data?.length ?? 0

  return (
    <div className="max-w-3xl">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t('maintenance.tab.title')}</h1>
        <Button variant="ghost" size="sm" onClick={() => qc.invalidateQueries({ queryKey: ['maintenance'] })}>
          <RefreshCw className="mr-1 size-3.5" />
          {t('maintenance.storageOverview.refresh')}
        </Button>
      </div>

      <div className="mb-4 space-y-1 text-sm">
        <div>
          <span className="text-sm font-medium">{t('maintenance.storageOverview.workdir')}</span>
          <span className="ml-2 text-sm font-mono text-strong break-all">
            {d.workdir}
          </span>
        </div>
        <div className="text-sm font-medium">
          {t('maintenance.storageOverview.totalSize')}: <span className="text-strong">{fmtBytes(d.totalBytes)}</span>
        </div>
      </div>

      <div className="mb-4 rounded-md border border-default bg-panel p-3">
        <ul className="space-y-1.5 text-sm">
          {Object.entries(d.breakdown).map(([key, item]) => (
            <li key={key} className="flex items-center justify-between text-sm">
              <div className="flex items-center gap-1.5 text-sm font-medium">
                <span>├─</span>
                <span>{item.label}</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="font-mono text-sm">{fmtBytes(item.bytes)}</span>
                {key === 'workspaces' && orphanCount > 0 && (
                  <button
                    type="button"
                    className="text-status-info text-xs hover:underline"
                    onClick={() => setView('orphans')}
                  >
                    {t('maintenance.storageOverview.orphanedCount', { n: orphanCount })}
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      </div>

      <div className="mb-6 flex gap-2">
        <Button
          variant="destructive"
          onClick={() => setConfirmOpen(true)}
          disabled={cleanup.isPending}
        >
          {t('maintenance.action.cleanupTrash')}
        </Button>
        <Button
          variant="destructive"
          onClick={() => setConfirmLegacyOpen(true)}
          disabled={cleanupLegacyMut.isPending}
        >
          {t('maintenance.action.cleanupLegacy')}
        </Button>
      </div>

      <AlertDialog
        open={confirmOpen}
        onOpenChange={(open) => {
          if (!cleanup.isPending) setConfirmOpen(open)
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('maintenance.action.cleanupTrash')}</AlertDialogTitle>
            <AlertDialogDescription>{t('maintenance.toast.cleanupTrashDone')}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>
              {t('common.cancel')}
            </AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={cleanup.isPending}
              onClick={(event) => {
                event.preventDefault()
                cleanup.mutate()
              }}
            >
              {cleanup.isPending ? t('common.saving') : t('common.delete')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog
        open={confirmLegacyOpen}
        onOpenChange={(open) => {
          if (!cleanupLegacyMut.isPending) setConfirmLegacyOpen(open)
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('maintenance.action.cleanupLegacy')}</AlertDialogTitle>
            <AlertDialogDescription>{t('maintenance.toast.cleanupLegacyDone')}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>
              {t('common.cancel')}
            </AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={cleanupLegacyMut.isPending}
              onClick={(event) => {
                event.preventDefault()
                cleanupLegacyMut.mutate()
              }}
            >
              {cleanupLegacyMut.isPending ? t('common.saving') : t('common.delete')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

function OrphanArchivesView({
  files,
  isLoading,
  onBack,
  onRefresh,
}: {
  files: OrphanedFileDto[]
  isLoading: boolean
  onBack: () => void
  onRefresh: () => void
}) {
  const { t } = useI18n()
  const connections = useConnectionStore(s => s.connections)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [targetConn, setTargetConn] = useState<string>(connections[0]?.id ?? '')
  const [processing, setProcessing] = useState(false)

  function toggle(id: string) {
    setSelected(s => { const n = new Set(s); if (n.has(id)) n.delete(id); else n.add(id); return n })
  }

  function toggleAll() {
    if (selected.size === files.length) setSelected(new Set())
    else setSelected(new Set(files.map(f => f.id)))
  }

  async function doReattach(id: string, connId: string) {
    try { await reattachFile(id, connId); return { id, ok: true } }
    catch { return { id, ok: false } }
  }

  async function doDiscard(id: string) {
    try { await discardFile(id); return { id, ok: true } }
    catch { return { id, ok: false } }
  }

  async function batchDiscard() {
    setProcessing(true)
    const ids = [...selected]
    let ok = 0
    for (const id of ids) {
      const r = await doDiscard(id)
      if (r.ok) ok++
    }
    toast(t('maintenance.orphans.toast.batchResult', { ok, fail: ids.length - ok }))
    setSelected(new Set())
    setProcessing(false)
    onRefresh()
  }

  if (isLoading) return <div className="text-sm text-muted-foreground">{t('common.loading')}</div>

  return (
    <div className="max-w-3xl">
      <Button variant="ghost" size="icon-sm" onClick={onBack} className="mb-3 -ml-1.5">
        <ArrowLeft className="size-4" />
      </Button>

      <h2 className="text-lg font-semibold text-strong mb-2">
        {t('maintenance.orphans.drawer.title', { n: files.length })}
      </h2>

      <p className="text-sm text-muted-foreground mb-4">{t('maintenance.orphans.drawer.description')}</p>

      {connections.length === 0 && (
        <div className="mx-0 mb-4 p-2 bg-status-infoSurface text-status-info border border-status-info rounded-md text-xs">
          {t('maintenance.orphans.drawer.bannerNoConnection')}
        </div>
      )}

      <div className="flex items-center gap-2 mb-4 pb-3 border-b border-subtle">
        {connections.length > 0 && (
          <>
            <Select value={targetConn} onValueChange={v => { if (v != null) setTargetConn(v) }}>
              <SelectTrigger size="sm">
                <span className="flex-1 text-left text-xs">
                  {connections.find(c => c.id === targetConn)?.name}
                </span>
              </SelectTrigger>
              <SelectContent>
                {connections.map(c => (
                  <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button variant="ghost" size="sm" disabled={selected.size === 0 || processing} onClick={async () => {
              setProcessing(true)
              let ok = 0
              for (const id of selected) { const r = await doReattach(id, targetConn); if (r.ok) ok++ }
              toast(t('maintenance.orphans.toast.reattachPartial', { ok, fail: selected.size - ok }))
              setSelected(new Set())
              setProcessing(false)
              onRefresh()
            }}>{t('maintenance.orphans.drawer.reattachBulk')}</Button>
          </>
        )}
        <Button variant="ghost" size="sm" disabled={selected.size === 0 || processing} onClick={batchDiscard}>
          {t('maintenance.orphans.drawer.discardBulk')}
        </Button>
      </div>

      <table className="w-full text-sm table-fixed">
        <thead className="text-left text-muted-foreground">
          <tr>
            <th className="pb-2 w-8 align-middle">
              <Checkbox checked={selected.size === files.length && files.length > 0} onCheckedChange={toggleAll} />
            </th>
            <th className="pb-2 align-middle">{t('maintenance.orphans.drawer.columnFile')}</th>
            <th className="pb-2 w-32 align-middle">{t('maintenance.orphans.drawer.columnKind')}</th>
            <th className="pb-2 w-24 align-middle">{t('maintenance.orphans.drawer.columnSize')}</th>
            {connections.length > 0 && (
              <>
                <th className="pb-2 w-28 align-middle"></th>
                <th className="pb-2 w-20 align-middle pl-3">{t('dataSources.actions')}</th>
              </>
            )}
          </tr>
        </thead>
        <tbody>
          {files.map(f => (
            <tr key={f.id} className="border-t">
              <td className="py-2 align-middle">
                <Checkbox checked={selected.has(f.id)} onCheckedChange={() => toggle(f.id)} />
              </td>
              <td className="py-2 align-middle">
                <span className="truncate block text-strong">{f.filename}</span>
                {f.orphanedFromConnection && (
                  <div className="text-xs text-muted-foreground mt-0.5">{t('maintenance.orphans.drawer.originalConnection', { name: f.orphanedFromConnection })}</div>
                )}
              </td>
              <td className="py-2 align-middle text-muted-foreground">{f.kind}</td>
              <td className="py-2 align-middle font-mono text-muted-foreground">{(f.sizeBytes / 1024).toFixed(1)} KB</td>
              {connections.length > 0 && (
                <>
                  <td className="py-2 align-middle">
                    <Button variant="ghost" size="sm" disabled={processing} onClick={async () => { await doReattach(f.id, targetConn); onRefresh() }}>
                      {t('maintenance.orphans.drawer.reattach')}
                    </Button>
                  </td>
                  <td className="py-2 align-middle">
                    <Button variant="ghost" size="sm" disabled={processing} onClick={async () => { await doDiscard(f.id); onRefresh() }}>
                      {t('maintenance.orphans.drawer.discard')}
                    </Button>
                  </td>
                </>
              )}
              {connections.length === 0 && (
                <td className="py-2 align-middle">
                  <Button variant="ghost" size="sm" disabled={processing} onClick={async () => { await doDiscard(f.id); onRefresh() }}>
                    {t('maintenance.orphans.drawer.discard')}
                  </Button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
