import { useState, useCallback, useRef } from 'react'
import { useI18n } from '@/i18n/use-i18n'
import { useIngestionJobsQuery } from './hooks/use-ingestion-jobs-query'
import { useStageStore } from '@/stores/stage-store'
import { batchDeleteIngestionJobs } from './api/ingestion-api'
import { toast } from 'sonner'
import { useQueryClient } from '@tanstack/react-query'
import { ingestionJobsKey } from './api/ingestion-api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Search, Download, Trash2, Eye, Loader2, ChevronLeft, ChevronRight } from 'lucide-react'
import type { IngestionJobView } from './api/ingestion-api'
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

const STATUS_OPTIONS = ['all', 'fetching', 'fetched', 'mapped', 'confirmed', 'writing', 'completed', 'failed', 'cancelled'] as const

const PAGE_SIZE = 100

function statusColor(status: string): string {
  switch (status) {
    case 'completed': return 'bg-status-success/15 text-status-success border-status-success/30'
    case 'failed': case 'cancelled': return 'bg-status-danger/15 text-status-danger border-status-danger/30'
    case 'writing': return 'bg-primary/10 text-primary border-primary/30'
    case 'fetching': return 'bg-status-info/15 text-status-info border-status-info/30'
    default: return 'bg-muted text-muted-foreground border-border'
  }
}

const stickyHeaderCellClass = 'sticky top-0 z-20 h-8 border-b border-border/50 bg-muted px-3'

function formatDateTime(ms: number): string {
  const d = new Date(ms)
  const yyyy = d.getFullYear()
  const MM = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const HH = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const ss = String(d.getSeconds()).padStart(2, '0')
  return `${yyyy}-${MM}-${dd} ${HH}:${mm}:${ss}`
}

export function IngestionLibraryTab() {
  const { t } = useI18n()
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [search, setSearch] = useState('')
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [page, setPage] = useState(1)
  const [deleting, setDeleting] = useState(false)
  const [showConfirmDelete, setShowConfirmDelete] = useState(false)
  const openTab = useStageStore((s) => s.openTab)
  const focusTab = useStageStore((s) => s.focusTab)
  const listTabs = useStageStore((s) => s.listTabs)
  const detachFromWorkset = useStageStore((s) => s.detachFromWorkset)
  const queryClient = useQueryClient()

  const { data } = useIngestionJobsQuery(
    statusFilter !== 'all'
      ? { status: statusFilter, limit: PAGE_SIZE, offset: (page - 1) * PAGE_SIZE }
      : { limit: PAGE_SIZE, offset: (page - 1) * PAGE_SIZE }
  )

  const jobs = (data?.items ?? []).filter((job) => {
    if (!search) return true
    const q = search.toLowerCase()
    return (
      job.sourceUrl.toLowerCase().includes(q) ||
      job.id.toLowerCase().includes(q) ||
      (job.targetTable ?? '').toLowerCase().includes(q)
    )
  })

  const total = data?.total ?? 0
  const pageCount = Math.min(Math.max(1, Math.ceil(total / PAGE_SIZE)), 100)
  const allSelected = jobs.length > 0 && jobs.every((j) => selectedIds.has(j.id))
  const lastClickTimeRef = useRef(0)

  const openJobTab = useCallback((job: IngestionJobView) => {
    lastClickTimeRef.current = 0 // 双击已消费，重置
    const tabId = `ingestion_job_${job.id}`
    const existing = listTabs().find((t) => t.tabId === tabId)
    if (existing) {
      focusTab(tabId)
      return
    }
    openTab({
      tabId,
      type: 'ingestion_job',
      title: `Job ${job.id.slice(0, 8)}`,
      payload: { id: job.id, sourceUrl: job.sourceUrl },
      createdAt: Date.now(),
    })
  }, [openTab, focusTab, listTabs])

  const handleBatchDelete = useCallback(async () => {
    if (deleting || selectedIds.size === 0) return
    setDeleting(true)
    try {
      const ids = Array.from(selectedIds)
      await batchDeleteIngestionJobs(ids)
      for (const id of ids) {
        const tabId = `ingestion_job_${id}`
        const existing = listTabs().find((t) => t.tabId === tabId)
        if (existing) detachFromWorkset(tabId)
      }
      setSelectedIds(new Set())
      await queryClient.invalidateQueries({ queryKey: ingestionJobsKey })
      if (page > 1 && jobs.length === ids.length) {
        setPage((p) => Math.max(1, p - 1))
      }
      toast.success(t('ingestion.toast.batch_delete.success', { count: ids.length }))
    } catch (e) {
      toast.error(t('ingestion.toast.batch_delete.failed'))
    } finally {
      setDeleting(false)
    }
  }, [deleting, selectedIds, detachFromWorkset, listTabs, queryClient, page, jobs.length, t])

  const handleDeleteClick = useCallback(() => {
    if (selectedIds.size > 0) setShowConfirmDelete(true)
  }, [selectedIds.size])

  const executeDelete = useCallback(() => {
    setShowConfirmDelete(false)
    void handleBatchDelete()
  }, [handleBatchDelete])

  const toggleRow = useCallback((id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }, [])

  const handleRowClick = useCallback((job: IngestionJobView) => {
    const now = Date.now()
    if (now - lastClickTimeRef.current < 300) {
      // 双击的第一次 click，忽略选中
      openJobTab(job)
      return
    }
    lastClickTimeRef.current = now
    // 延迟执行，等待可能的第二次 click（双击）
    setTimeout(() => {
      if (Date.now() - lastClickTimeRef.current >= 250) {
        toggleRow(job.id)
      }
    }, 250)
  }, [openJobTab, toggleRow])

  const toggleAll = useCallback(() => {
    if (allSelected) {
      setSelectedIds(new Set())
    } else {
      setSelectedIds(new Set(jobs.map((j) => j.id)))
    }
  }, [allSelected, jobs])

  const handleView = useCallback(() => {
    for (const id of selectedIds) {
      const job = jobs.find((j) => j.id === id)
      if (job) openJobTab(job)
    }
  }, [selectedIds, jobs, openJobTab])

  const handleStatusChange = useCallback((v: string | null) => {
    if (v != null) {
      setStatusFilter(v)
      setPage(1)
      setSelectedIds(new Set())
    }
  }, [])

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearch(e.target.value)
    setPage(1)
  }, [])

  return (
    <>
    <div className="flex flex-col h-full" data-testid="ingestion-library-tab">
      <div className="flex shrink-0 flex-wrap items-center justify-between gap-2 border-b border-border/50 px-3 py-2">
        <div className="flex items-center gap-1.5">
          <Download className="size-3.5 shrink-0 text-muted-foreground" />
          <span className="text-xs text-muted-foreground">{t('ingestion.library.title')}</span>
        </div>
        <div className="flex items-center gap-2">
          <Select value={statusFilter} onValueChange={handleStatusChange}>
            <SelectTrigger size="sm" data-testid="ingestion-status-filter" className="w-[140px] text-xs">
              <SelectValue placeholder={t('ingestion.library.columns.status')} />
            </SelectTrigger>
            <SelectContent>
              {STATUS_OPTIONS.map((s) => (
                <SelectItem key={s} value={s} className="text-xs">
                  {s === 'all' ? t('ingestion.status.all') : t(`ingestion.status.${s}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <div className="flex h-7 items-center gap-1.5 rounded-md border border-border px-2 transition-colors focus-within:border-ring focus-within:ring-3 focus-within:ring-ring/50">
            <Search className="size-3.5 shrink-0 text-muted-foreground" />
            <input
              type="text"
              data-testid="ingestion-search-input"
              value={search}
              onChange={handleSearchChange}
              placeholder={t('ingestion.library.searchPlaceholder')}
              className="w-36 bg-transparent text-xs outline-none placeholder:text-muted-foreground"
            />
          </div>
        </div>
      </div>

      <div data-result-scrollbar="header-offset" className="min-h-0 flex-1 overflow-auto">
        <Table scrollContainer={false} className="min-w-max text-xs">
          <TableHeader className="bg-muted">
            <TableRow className="hover:bg-transparent">
              <TableHead className={`${stickyHeaderCellClass} w-10`}>
                <Checkbox
                  checked={allSelected}
                  onCheckedChange={toggleAll}
                  aria-label={t('ingestion.library.selectAll')}
                  className="size-3.5"
                />
              </TableHead>
              <TableHead className={`${stickyHeaderCellClass} font-medium`}>{t('ingestion.library.columns.status')}</TableHead>
              <TableHead className={`${stickyHeaderCellClass} font-medium`}>{t('ingestion.library.sourceUrl')}</TableHead>
              <TableHead className={`${stickyHeaderCellClass} font-medium`}>{t('ingestion.library.columns.target')}</TableHead>
              <TableHead className={`${stickyHeaderCellClass} font-medium w-20`}>{t('ingestion.library.columns.rows')}</TableHead>
              <TableHead className={`${stickyHeaderCellClass} font-medium w-40`}>{t('ingestion.library.columns.created')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {jobs.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center text-muted-foreground py-8">
                  {t('ingestion.no.jobs')}
                </TableCell>
              </TableRow>
            ) : (
              jobs.map((job) => (
                <TableRow
                  key={job.id}
                  data-testid={`ingestion-library-row-${job.id}`}
                  onClick={() => handleRowClick(job)}
                  onDoubleClick={() => openJobTab(job)}
                  className={`border-b border-border/30 hover:bg-muted/50 cursor-pointer ${
                    selectedIds.has(job.id) ? 'bg-primary/8' : ''
                  }`}
                >
                  <TableCell className="px-3 py-1.5" onClick={(e) => e.stopPropagation()}>
                    <Checkbox
                      checked={selectedIds.has(job.id)}
                      onCheckedChange={() => toggleRow(job.id)}
                      aria-label={t('ingestion.library.selectJob', { id: job.id })}
                      className="size-3.5"
                    />
                  </TableCell>
                  <TableCell className="px-3 py-1.5">
                    <Badge variant="outline" className={`text-xs px-1.5 py-0 ${statusColor(job.status)}`}>
                      {t(`ingestion.status.${job.status}` as any)}
                    </Badge>
                  </TableCell>
                  <TableCell className="max-w-[300px] px-3 py-1.5 truncate" title={job.sourceUrl}>
                    {job.sourceUrl}
                  </TableCell>
                  <TableCell className="px-3 py-1.5">
                    {job.targetSchema && job.targetTable ? `${job.targetSchema}.${job.targetTable}` : job.targetTable ?? '—'}
                  </TableCell>
                  <TableCell className="px-3 py-1.5">
                    {job.rowCount?.toLocaleString() ?? '—'}
                  </TableCell>
                  <TableCell className="px-3 py-1.5 font-mono">
                    {formatDateTime(job.createdAt)}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      <div className="flex shrink-0 items-center gap-3 border-t border-border/50 px-3 py-2">
        <span className="text-xs text-muted-foreground">
          {t('ingestion.library.jobCount', { count: total })}
        </span>
        {selectedIds.size > 0 && (
          <span className="text-xs text-muted-foreground">
            {t('ingestion.library.selectedCount', { count: selectedIds.size })}
          </span>
        )}
        {selectedIds.size > 0 && (
          <div className="flex items-center gap-2">
            <button
              data-testid="ingestion-view-btn"
              onClick={handleView}
              className="flex items-center gap-1 rounded-md px-2 py-1 text-xs text-muted-foreground transition-colors hover:bg-muted/50"
            >
              <Eye className="size-3.5" />
              {t('ingestion.library.view')}
            </button>
            <button
              data-testid="ingestion-delete-btn"
              onClick={handleDeleteClick}
              disabled={deleting}
              className="flex items-center gap-1 rounded-md px-2 py-1 text-xs text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive disabled:opacity-50"
            >
              {deleting ? <Loader2 className="size-3.5 animate-spin" /> : <Trash2 className="size-3.5" />}
              {selectedIds.size > 1 ? t('ingestion.library.deleteCount', { count: selectedIds.size }) : t('ingestion.library.delete')}
            </button>
          </div>
        )}
        {pageCount > 1 && (
          <div className="ml-auto flex items-center gap-2">
            <span className="text-xs text-muted-foreground">
              {t('ingestion.library.pageIndicator', { current: page, total: pageCount })}
            </span>
            <Button
              size="sm"
              variant="outline"
              disabled={page === 1}
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              className="h-6 px-2 text-xs"
            >
              <ChevronLeft className="size-3.5" />
            </Button>
            <Button
              size="sm"
              variant="outline"
              disabled={page === pageCount}
              onClick={() => setPage((p) => Math.min(pageCount, p + 1))}
              className="h-6 px-2 text-xs"
            >
              <ChevronRight className="size-3.5" />
            </Button>
          </div>
        )}
      </div>
    </div>

    {/* Delete confirmation dialog */}
    <AlertDialog open={showConfirmDelete} onOpenChange={(open) => { if (!open && !deleting) setShowConfirmDelete(false) }}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t('common.delete')}</AlertDialogTitle>
          <AlertDialogDescription>
            {t('ingestion.library.delete.confirm', { count: selectedIds.size })}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={deleting}>{t('common.cancel')}</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={deleting}
            onClick={(event) => {
              event.preventDefault()
              executeDelete()
            }}
          >
            {deleting ? t('common.loading') : t('common.delete')}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
    </>
  )
}
