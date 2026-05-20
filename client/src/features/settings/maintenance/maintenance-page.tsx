import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select'
import { useI18n } from '@/i18n/use-i18n'
import { toast } from 'sonner'
import {
  getStorageOverview, cleanupTrash, cleanupLegacy, getOrphanedFiles, reattachFile,
  getDashboards, getReports, getExports, getSemantic, getUploads,
  deleteDashboard, deleteReport, deleteExport, deleteSemantic, deleteUpload,
  type OrphanedFileDto, type DashboardResourceDto, type ReportResourceDto, type ExportResourceDto,
  type SemanticResourceDto, type UploadResourceDto, type StorageOverviewDto,
} from '@/services/api/maintenance'
import { discardFile } from '@/services/api/file-artifacts'
import { useConnectionStore } from '@/features/connection/store'
import { useState, useRef } from 'react'
import { RefreshCw, ArrowLeft, Trash2 } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
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

const RESOURCE_TABS = ['dashboards', 'reports', 'exports', 'semantic', 'uploads'] as const
type ResourceTab = typeof RESOURCE_TABS[number]

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
    <div className="max-w-5xl">
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

      {/* Resource Directory Section */}
      <ResourceDirectoryView overview={d} />
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
                <span
                  className="truncate block text-strong max-w-[220px]"
                  title={f.filename}
                  onDoubleClick={(ev) => {
                    const range = document.createRange()
                    range.selectNodeContents(ev.currentTarget)
                    const sel = window.getSelection()
                    sel?.removeAllRanges()
                    sel?.addRange(range)
                  }}
                >
                  {f.filename}
                </span>
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

// ---------------------------------------------------------------------------
// Resource Directory View
// ---------------------------------------------------------------------------

export function ResourceDirectoryView({ overview }: { overview: StorageOverviewDto }) {
  const { t } = useI18n()
  const qc = useQueryClient()
  const [activeResourceTab, setActiveResourceTab] = useState<ResourceTab>('dashboards')
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)
  const [confirmDeleteBulkOpen, setConfirmDeleteBulkOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<{ key: string; label: string } | null>(null)
  const sectionRef = useRef<HTMLDivElement>(null)

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const resourceQuery = useQuery<any[]>({
    queryKey: ['maintenance', 'resources', activeResourceTab],
    queryFn: () => {
      switch (activeResourceTab) {
        case 'dashboards': return getDashboards()
        case 'reports': return getReports()
        case 'exports': return getExports()
        case 'semantic': return getSemantic()
        case 'uploads': return getUploads()
      }
    },
  })

  // ---- item identity helpers ----

  function getItemKey(item: unknown): string {
    switch (activeResourceTab) {
      case 'dashboards': return (item as DashboardResourceDto).id
      case 'reports': return (item as ReportResourceDto).id
      case 'exports': return (item as ExportResourceDto).exportId
      case 'semantic': {
        const s = item as SemanticResourceDto
        return `${s.domain}::${s.connectionId}`
      }
      case 'uploads': return (item as UploadResourceDto).id
    }
  }

  function getItemLabel(item: unknown): string {
    switch (activeResourceTab) {
      case 'dashboards': {
        const d = item as DashboardResourceDto
        return d.title || d.filename
      }
      case 'reports': return (item as ReportResourceDto).title
      case 'exports': return (item as ExportResourceDto).filename
      case 'semantic': {
        const s = item as SemanticResourceDto
        return `${s.domain} / ${s.connectionName}`
      }
      case 'uploads': return (item as UploadResourceDto).filename
    }
  }

  async function deleteByKey(key: string): Promise<void> {
    switch (activeResourceTab) {
      case 'dashboards': return deleteDashboard(key)
      case 'reports': return deleteReport(key)
      case 'exports': return deleteExport(key)
      case 'semantic': {
        const idx = key.indexOf('::')
        return deleteSemantic(key.slice(0, idx), key.slice(idx + 2))
      }
      case 'uploads': return deleteUpload(key)
    }
  }

  // ---- selection ----

  function toggleItem(key: string) {
    setSelected(s => { const n = new Set(s); if (n.has(key)) n.delete(key); else n.add(key); return n })
  }

  // ---- delete handlers ----

  async function handleSingleDelete() {
    if (!deleteTarget) return
    try {
      await deleteByKey(deleteTarget.key)
      toast.success(t('maintenance.resources.confirmDelete.description'))
      qc.invalidateQueries({ queryKey: ['maintenance', 'resources', activeResourceTab] })
      qc.invalidateQueries({ queryKey: ['maintenance', 'storage-overview'] })
    } catch (err) {
      toast.error(err instanceof Error ? err.message : String(err))
    }
    setConfirmDeleteOpen(false)
    setDeleteTarget(null)
  }

  async function handleBulkDelete() {
    let ok = 0
    for (const key of selected) {
      try { await deleteByKey(key); ok++ } catch { /* skip */ }
    }
    toast.success(t('maintenance.resources.confirmDeleteBulk.description', { count: ok }))
    setSelected(new Set())
    setConfirmDeleteBulkOpen(false)
    qc.invalidateQueries({ queryKey: ['maintenance', 'resources', activeResourceTab] })
    qc.invalidateQueries({ queryKey: ['maintenance', 'storage-overview'] })
  }

  // ---- formatting helpers ----

  function formatDate(ts: number): string {
    return new Date(ts).toLocaleDateString()
  }

  function formatExpiry(ts: number): { text: string; urgent: boolean } {
    const remaining = ts - Date.now()
    const mins = Math.floor(remaining / 60000)
    if (remaining <= 0) return { text: t('maintenance.resources.badge.expired'), urgent: true }
    if (mins < 10) return { text: t('maintenance.resources.badge.expiringSoon'), urgent: true }
    const d = new Date(ts)
    const yyyy = d.getFullYear()
    const MM = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    const HH = String(d.getHours()).padStart(2, '0')
    const mm = String(d.getMinutes()).padStart(2, '0')
    const ss = String(d.getSeconds()).padStart(2, '0')
    return { text: `${yyyy}-${MM}-${dd} ${HH}:${mm}:${ss}`, urgent: false }
  }

  function getFormatBadgeVariant(f: string): 'default' | 'secondary' | 'destructive' | 'outline' {
    const lower = f.toLowerCase()
    if (lower === 'pdf') return 'destructive'
    if (lower === 'md' || lower === 'markdown') return 'secondary'
    return 'outline'
  }

  function simplifyMime(mime: string): string {
    const parts = mime.split('/')
    if (parts.length !== 2) return mime
    const [type, sub] = parts
    if (type === 'text' && sub === 'csv') return 'CSV'
    if (type === 'text' && sub === 'plain') return 'Text'
    if (type === 'application' && sub === 'json') return 'JSON'
    if (type === 'application' && (sub.includes('spreadsheet') || sub.includes('excel'))) return 'Excel'
    if (type === 'image') return `${sub.toUpperCase()} Image`
    return `${type}/${sub}`
  }

  // ---- data ----

  const data = resourceQuery.data ?? []
  const isLoading = resourceQuery.isLoading
  const allSelected = data.length > 0 && selected.size === data.length

  // ---- conditional column rendering ----

  function selectCellText(el: HTMLElement) {
    const range = document.createRange()
    range.selectNodeContents(el)
    const sel = window.getSelection()
    sel?.removeAllRanges()
    sel?.addRange(range)
  }

  function renderMainCell(item: unknown) {
    switch (activeResourceTab) {
      case 'dashboards': {
        const d = item as DashboardResourceDto
        const label = d.title || d.filename
        return (
          <div>
            <div
              className="text-sm text-strong truncate max-w-[220px]"
              title={label}
              onDoubleClick={(ev) => selectCellText(ev.currentTarget)}
            >
              {label}
            </div>
            {d.originSessionId && (
              <div className="text-xs text-muted-foreground">
                {t('maintenance.resources.originSession', { name: d.originSessionId })}
              </div>
            )}
          </div>
        )
      }
      case 'reports': {
        const r = item as ReportResourceDto
        return (
          <div>
            <div
              className="text-sm text-strong truncate max-w-[220px]"
              title={r.title}
              onDoubleClick={(ev) => selectCellText(ev.currentTarget)}
            >
              {r.title}
            </div>
            {r.originSessionId && (
              <div className="text-xs text-muted-foreground">
                {t('maintenance.resources.originSession', { name: r.originSessionId })}
              </div>
            )}
          </div>
        )
      }
      case 'exports': {
        const e = item as ExportResourceDto
        return (
          <span
            className="text-sm text-strong block truncate max-w-[220px]"
            title={e.filename}
            onDoubleClick={(ev) => selectCellText(ev.currentTarget)}
          >
            {e.filename}
          </span>
        )
      }
      case 'semantic': {
        const s = item as SemanticResourceDto
        return (
          <div>
            <div
              className="text-sm text-strong truncate max-w-[220px]"
              title={s.domain}
              onDoubleClick={(ev) => selectCellText(ev.currentTarget)}
            >
              {s.domain}
            </div>
            <div className="text-xs text-muted-foreground">{s.connectionName}</div>
          </div>
        )
      }
      case 'uploads': {
        const u = item as UploadResourceDto
        return (
          <span
            className="text-sm text-strong block truncate max-w-[220px]"
            title={u.filename}
            onDoubleClick={(ev) => selectCellText(ev.currentTarget)}
          >
            {u.filename}
          </span>
        )
      }
    }
  }

  function renderExtraCells(item: unknown) {
    switch (activeResourceTab) {
      case 'dashboards': {
        const d = item as DashboardResourceDto
        return (
          <>
            <TableCell className="text-sm text-muted-foreground">{d.widgetCount}</TableCell>
            <TableCell className="font-mono text-sm text-muted-foreground">{fmtBytes(d.sizeBytes)}</TableCell>
            <TableCell className="text-sm text-muted-foreground">{formatDate(d.createdAt)}</TableCell>
          </>
        )
      }
      case 'reports': {
        const r = item as ReportResourceDto
        return (
          <>
            <TableCell>
              <div className="flex gap-1 flex-wrap">
                {r.availableFormats.map(f => (
                  <Badge key={f} variant={getFormatBadgeVariant(f)} className="text-[11px]">{f.toUpperCase()}</Badge>
                ))}
              </div>
            </TableCell>
            <TableCell className="font-mono text-sm text-muted-foreground">{fmtBytes(r.sizeBytes)}</TableCell>
            <TableCell className="text-sm text-muted-foreground">{formatDate(r.createdAt)}</TableCell>
          </>
        )
      }
      case 'exports': {
        const e = item as ExportResourceDto
        const expiry = formatExpiry(e.expiresAt)
        return (
          <>
            <TableCell>
              <Badge variant={getFormatBadgeVariant(e.format)} className="text-[11px]">{e.format.toUpperCase()}</Badge>
            </TableCell>
            <TableCell className="font-mono text-sm text-muted-foreground">{fmtBytes(e.sizeBytes)}</TableCell>
            <TableCell className="font-mono text-sm text-muted-foreground">{e.rowCount.toLocaleString()}</TableCell>
            <TableCell>
              <span className={cn('text-sm', expiry.urgent ? 'text-red-500' : 'text-muted-foreground')}>
                {expiry.text}
              </span>
            </TableCell>
          </>
        )
      }
      case 'semantic': {
        const s = item as SemanticResourceDto
        return (
          <>
            <TableCell>
              <Badge
                variant={s.status === 'active' ? 'default' : 'secondary'}
                className="text-[11px]"
              >
                {s.status === 'active' ? t('maintenance.resources.badge.active') : t('maintenance.resources.badge.pending')}
              </Badge>
            </TableCell>
            <TableCell className="font-mono text-sm text-muted-foreground">{fmtBytes(s.sizeBytes)}</TableCell>
            <TableCell className="text-sm text-muted-foreground">{formatDate(s.updatedAt)}</TableCell>
          </>
        )
      }
      case 'uploads': {
        const u = item as UploadResourceDto
        const expiry = formatExpiry(u.expiresAt)
        return (
          <>
            <TableCell className="text-sm text-muted-foreground">{simplifyMime(u.mimeType)}</TableCell>
            <TableCell className="font-mono text-sm text-muted-foreground">{fmtBytes(u.sizeBytes)}</TableCell>
            <TableCell>
              <span className={cn('text-sm', expiry.urgent ? 'text-red-500' : 'text-muted-foreground')}>
                {expiry.text}
              </span>
            </TableCell>
          </>
        )
      }
    }
  }

  function renderHeaders() {
    switch (activeResourceTab) {
      case 'dashboards':
        return (
          <>
            <TableHead>{t('maintenance.resources.column.name')}</TableHead>
            <TableHead className="w-20">{t('maintenance.resources.column.widgets')}</TableHead>
            <TableHead className="w-24">{t('maintenance.resources.column.size')}</TableHead>
            <TableHead className="w-28">{t('maintenance.resources.column.createdAt')}</TableHead>
            <TableHead className="w-32">{t('maintenance.resources.column.actions')}</TableHead>
          </>
        )
      case 'reports':
        return (
          <>
            <TableHead>{t('maintenance.resources.column.title')}</TableHead>
            <TableHead>{t('maintenance.resources.column.formats')}</TableHead>
            <TableHead className="w-24">{t('maintenance.resources.column.size')}</TableHead>
            <TableHead className="w-28">{t('maintenance.resources.column.createdAt')}</TableHead>
            <TableHead className="w-32">{t('maintenance.resources.column.actions')}</TableHead>
          </>
        )
      case 'exports':
        return (
          <>
            <TableHead>{t('maintenance.resources.column.filename')}</TableHead>
            <TableHead className="w-20">{t('maintenance.resources.column.format')}</TableHead>
            <TableHead className="w-24">{t('maintenance.resources.column.size')}</TableHead>
            <TableHead className="w-20">{t('maintenance.resources.column.rows')}</TableHead>
            <TableHead className="w-28">{t('maintenance.resources.column.expiresAt')}</TableHead>
            <TableHead className="w-32">{t('maintenance.resources.column.actions')}</TableHead>
          </>
        )
      case 'semantic':
        return (
          <>
            <TableHead>{t('maintenance.resources.column.name')}</TableHead>
            <TableHead className="w-20">{t('maintenance.resources.column.status')}</TableHead>
            <TableHead className="w-24">{t('maintenance.resources.column.size')}</TableHead>
            <TableHead className="w-28">{t('maintenance.resources.column.updatedAt')}</TableHead>
            <TableHead className="w-32">{t('maintenance.resources.column.actions')}</TableHead>
          </>
        )
      case 'uploads':
        return (
          <>
            <TableHead>{t('maintenance.resources.column.filename')}</TableHead>
            <TableHead className="w-24">{t('maintenance.resources.column.mimeType')}</TableHead>
            <TableHead className="w-24">{t('maintenance.resources.column.size')}</TableHead>
            <TableHead className="w-28">{t('maintenance.resources.column.expiresAt')}</TableHead>
            <TableHead className="w-32">{t('maintenance.resources.column.actions')}</TableHead>
          </>
        )
    }
  }

  return (
    <div className="mt-10">
      {/* Section header */}
      <h2 className="text-lg font-semibold text-strong mb-1">
        {t('maintenance.resources.sectionTitle')}
      </h2>
      <p className="text-sm text-muted-foreground mb-6">
        {t('maintenance.resources.sectionDescription')}
      </p>

      {/* Resource Directory Cards */}
      <div className="grid grid-cols-5 gap-3 mb-8" ref={sectionRef}>
        {RESOURCE_TABS.map(key => {
          const dir = overview.resourceDirectories?.[key]
          return (
            <Card
              key={key}
              size="sm"
              role="tab"
              aria-selected={activeResourceTab === key}
              className={cn(
                'cursor-pointer transition-colors',
                activeResourceTab === key
                  ? 'ring-primary bg-primary/5'
                  : 'hover:bg-primary/5'
              )}
              onClick={() => {
                setActiveResourceTab(key)
                setSelected(new Set())
                sectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
              }}
            >
              <CardHeader>
                <CardTitle className="text-sm">{t(`maintenance.resources.tab.${key}`)}</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-2xl font-semibold text-strong">{dir?.count ?? 0}</p>
                <p className="text-xs text-muted-foreground">{fmtBytes(dir?.sizeBytes ?? 0)}</p>
              </CardContent>
            </Card>
          )
        })}
      </div>

      {/* Resource Type Tabs */}
      <div className="flex gap-1 border-b border-subtle mb-4">
        {RESOURCE_TABS.map(key => (
          <button
            key={key}
            type="button"
            className={cn(
              'px-3 py-2 text-sm font-medium border-b-2 transition-colors -mb-px',
              activeResourceTab === key
                ? 'border-b-foreground text-foreground'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            )}
            onClick={() => {
              setActiveResourceTab(key)
              setSelected(new Set())
              sectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
            }}
          >
            {t(`maintenance.resources.tab.${key}`)}
          </button>
        ))}
      </div>

      {/* Batch Delete Bar */}
      {selected.size > 0 && (
        <div className="flex items-center gap-3 mb-3">
          <Button
            variant="destructive"
            size="sm"
            onClick={() => setConfirmDeleteBulkOpen(true)}
          >
            {t('maintenance.resources.action.deleteSelected')} ({selected.size})
          </Button>
        </div>
      )}

      {/* Loading Skeleton */}
      {isLoading && (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      )}

      {/* Empty State */}
      {!isLoading && data.length === 0 && (
        <div className="py-12 text-center text-sm text-muted-foreground">
          {t(`maintenance.resources.empty.${activeResourceTab}`)}
        </div>
      )}

      {/* Data Table */}
      {!isLoading && data.length > 0 && (
        <Table scrollContainer>
          <TableHeader>
            <TableRow>
              <TableHead className="w-8">
                <Checkbox
                  checked={allSelected}
                  onCheckedChange={() => {
                    if (allSelected) setSelected(new Set())
                    else setSelected(new Set(data.map(item => getItemKey(item))))
                  }}
                />
              </TableHead>
              {renderHeaders()}
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map(item => {
              const key = getItemKey(item)
              const isRowSelected = selected.has(key)
              return (
                <TableRow key={key} data-state={isRowSelected ? 'selected' : undefined}>
                  <TableCell>
                    <Checkbox checked={isRowSelected} onCheckedChange={() => toggleItem(key)} />
                  </TableCell>
                  <TableCell>
                    {renderMainCell(item)}
                  </TableCell>
                  {renderExtraCells(item)}
                  <TableCell>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-8 w-8 p-0 text-destructive hover:text-destructive hover:bg-destructive/10"
                      title={t('maintenance.resources.action.delete')}
                      onClick={() => {
                        setDeleteTarget({ key, label: getItemLabel(item) })
                        setConfirmDeleteOpen(true)
                      }}
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      )}

      {/* Single Delete Confirmation */}
      <AlertDialog
        open={confirmDeleteOpen}
        onOpenChange={(open) => { if (!open) { setConfirmDeleteOpen(false); setDeleteTarget(null) } }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('maintenance.resources.confirmDelete.title')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('maintenance.resources.confirmDelete.description')}
              {deleteTarget && (
                <span className="block mt-1 font-medium text-strong">{deleteTarget.label}</span>
              )}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('maintenance.resources.confirmDelete.cancel')}</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={(event) => { event.preventDefault(); handleSingleDelete() }}>
              {t('maintenance.resources.confirmDelete.confirm')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Bulk Delete Confirmation */}
      <AlertDialog open={confirmDeleteBulkOpen} onOpenChange={setConfirmDeleteBulkOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('maintenance.resources.confirmDeleteBulk.title')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('maintenance.resources.confirmDeleteBulk.description', { count: selected.size })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('maintenance.resources.confirmDelete.cancel')}</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={(event) => { event.preventDefault(); handleBulkDelete() }}>
              {t('maintenance.resources.confirmDelete.confirm')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
