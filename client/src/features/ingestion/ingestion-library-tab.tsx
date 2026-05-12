import { useState, useCallback } from 'react'
import { useI18n } from '@/i18n/use-i18n'
import { useIngestionJobsQuery } from './hooks/use-ingestion-jobs-query'
import { useStageStore } from '@/stores/stage-store'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
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
import { Search, Download } from 'lucide-react'
import type { IngestionJobView } from './api/ingestion-api'

const STATUS_OPTIONS = ['all', 'fetching', 'fetched', 'mapped', 'confirmed', 'writing', 'completed', 'failed', 'cancelled'] as const

function statusColor(status: string): string {
  switch (status) {
    case 'completed': return 'bg-status-success/15 text-status-success border-status-success/30'
    case 'failed': case 'cancelled': return 'bg-status-danger/15 text-status-danger border-status-danger/30'
    case 'writing': return 'bg-accent-primarySurface text-accent-primary border-accent-primary/30'
    case 'fetching': return 'bg-status-info/15 text-status-info border-status-info/30'
    default: return 'bg-bg-subtle text-text-muted border-border-default'
  }
}

export function IngestionLibraryTab() {
  const { t } = useI18n()
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [search, setSearch] = useState('')
  const openTab = useStageStore((s) => s.openTab)

  const { data } = useIngestionJobsQuery(
    statusFilter !== 'all' ? { status: statusFilter, limit: 100 } : { limit: 100 }
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

  const openJobTab = useCallback((job: IngestionJobView) => {
    openTab({
      tabId: `ingestion_job_${job.id}`,
      type: 'ingestion_job',
      title: `Job ${job.id.slice(0, 8)}`,
      payload: { id: job.id, sourceUrl: job.sourceUrl },
      createdAt: Date.now(),
    })
  }, [openTab])

  return (
    <div className="flex flex-col h-full" data-testid="ingestion-library-tab">
      <div className="flex items-center gap-2 px-3 py-2 border-b border-border-default">
        <div className="flex items-center gap-1.5 text-text-base font-medium">
          <Download className="h-4 w-4" />
          <span className="text-ui-md">{t('ingestion.library.title')}</span>
        </div>
        <div className="flex-1" />
        <Select value={statusFilter} onValueChange={(v) => { if (v != null) setStatusFilter(v) }}>
          <SelectTrigger data-testid="ingestion-status-filter" className="w-[140px] h-7 text-ui-xs">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            {STATUS_OPTIONS.map((s) => (
              <SelectItem key={s} value={s} className="text-ui-xs">
                {s === 'all' ? 'All' : s.charAt(0).toUpperCase() + s.slice(1)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <div className="relative">
          <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-text-soft" />
          <Input
            data-testid="ingestion-search-input"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search jobs..."
            className="h-7 w-[180px] pl-7 text-ui-xs"
          />
        </div>
      </div>

      <div className="flex-1 overflow-auto">
        <Table>
          <TableHeader>
            <TableRow className="bg-bg-subtle hover:bg-bg-subtle">
              <TableHead className="text-ui-xs font-medium text-text-muted h-8 px-2">Status</TableHead>
              <TableHead className="text-ui-xs font-medium text-text-muted px-2">Source URL</TableHead>
              <TableHead className="text-ui-xs font-medium text-text-muted px-2">Target</TableHead>
              <TableHead className="text-ui-xs font-medium text-text-muted px-2 w-20">Rows</TableHead>
              <TableHead className="text-ui-xs font-medium text-text-muted px-2 w-40">Created</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {jobs.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-ui-sm text-text-soft py-8">
                  No ingestion jobs found
                </TableCell>
              </TableRow>
            ) : (
              jobs.map((job) => (
                <TableRow
                  key={job.id}
                  data-testid={`ingestion-library-row-${job.id}`}
                  className="hover:bg-interaction-hover cursor-pointer"
                  onDoubleClick={() => openJobTab(job)}
                >
                  <TableCell className="px-2 py-1.5">
                    <Badge variant="outline" className={`text-ui-xs px-1.5 py-0 ${statusColor(job.status)}`}>
                      {job.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="px-2 py-1.5 text-ui-xs truncate max-w-[300px]" title={job.sourceUrl}>
                    {job.sourceUrl}
                  </TableCell>
                  <TableCell className="px-2 py-1.5 text-ui-xs">
                    {job.targetSchema && job.targetTable ? `${job.targetSchema}.${job.targetTable}` : job.targetTable ?? '—'}
                  </TableCell>
                  <TableCell className="px-2 py-1.5 text-ui-xs text-text-muted">
                    {job.rowCount?.toLocaleString() ?? '—'}
                  </TableCell>
                  <TableCell className="px-2 py-1.5 text-ui-xs text-text-muted">
                    {new Date(job.createdAt).toLocaleString()}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {data && (
        <div className="px-3 py-1.5 border-t border-border-default text-ui-xs text-text-muted">
          {data.total} job{data.total !== 1 ? 's' : ''} total
        </div>
      )}
    </div>
  )
}
