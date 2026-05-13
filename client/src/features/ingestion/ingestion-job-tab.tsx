import { useCallback, useEffect, useState } from 'react'
import { useI18n } from '@/i18n/use-i18n'
import { useIngestionJobQuery } from './hooks/use-ingestion-job-query'
import { useIngestionJobsStore } from './stores/use-ingestion-jobs-store'
import { confirmIngestionJob, cancelIngestionJob, deleteIngestionJob, ingestionJobsKey, ingestionJobKey } from './api/ingestion-api'
import { toast } from 'sonner'
import { useQueryClient } from '@tanstack/react-query'
import { useStageStore } from '@/stores/stage-store'
import { coordinator } from '@/features/stage/persistence/stage-persistence-bootstrap'
import { FetchingPhase } from './phases/fetching-phase'
import { MappingPhase } from './phases/mapping-phase'
import { WritingPhase } from './phases/writing-phase'
import { CompletedPhase } from './phases/completed-phase'
import { FailedPhase } from './phases/failed-phase'
import { Badge } from '@/components/ui/badge'
import { Trash2, Loader2 } from 'lucide-react'
import type { StageTab } from '@/stores/stage-store'

interface IngestionJobTabProps {
  tab: StageTab
}

const PHASE_ORDER = ['fetching', 'fetched', 'mapped', 'confirmed', 'writing', 'completed', 'failed', 'cancelled'] as const

const DELETABLE_STATUSES = new Set(['completed', 'failed', 'cancelled', 'fetched', 'mapped', 'confirmed'])

function phaseIndex(status: string): number {
  const idx = PHASE_ORDER.indexOf(status as typeof PHASE_ORDER[number])
  return idx >= 0 ? idx : 0
}

function statusColor(status: string): string {
  switch (status) {
    case 'completed': return 'bg-status-success/15 text-status-success border-status-success/30'
    case 'failed': case 'cancelled': return 'bg-status-danger/15 text-status-danger border-status-danger/30'
    case 'writing': return 'bg-primary/10 text-primary border-primary/30'
    case 'fetching': return 'bg-status-info/15 text-status-info border-status-info/30'
    default: return 'bg-muted text-muted-foreground border-border'
  }
}

export function IngestionJobTab({ tab }: IngestionJobTabProps) {
  const { t } = useI18n()
  const payload = tab.payload as { id?: string } | null
  const jobId = payload?.id ?? ''
  const { data: job, isLoading } = useIngestionJobQuery(jobId)
  const hydrateFromJob = useIngestionJobsStore((s) => s.hydrateFromJob)
  const [confirming, setConfirming] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const detachFromWorkset = useStageStore((s) => s.detachFromWorkset)
  const queryClient = useQueryClient()
  useEffect(() => {
    void coordinator.ensureHydrated(tab.tabId)
  }, [tab.tabId])
  useEffect(() => {
    if (job) hydrateFromJob(job)
  }, [job, hydrateFromJob])

  const handleConfirm = useCallback(async () => {
    if (!jobId || confirming) return
    setConfirming(true)
    try {
      await confirmIngestionJob(jobId)
      await queryClient.invalidateQueries({ queryKey: ingestionJobKey(jobId) })
      await queryClient.invalidateQueries({ queryKey: ingestionJobsKey })
      toast.success(t('ingestion.toast.confirm.success'))
    } catch (e) {
      toast.error(t('ingestion.toast.confirm.failed'))
    } finally {
      setConfirming(false)
    }
  }, [jobId, confirming, queryClient, t])

  const handleCancel = useCallback(async () => {
    if (!jobId) return
    try {
      await cancelIngestionJob(jobId)
      await queryClient.invalidateQueries({ queryKey: ingestionJobKey(jobId) })
      toast.success(t('ingestion.toast.cancel.success'))
    } catch (e) {
      toast.error(t('ingestion.toast.cancel.failed'))
    }
  }, [jobId, queryClient, t])

  const handleDelete = useCallback(async () => {
    if (!jobId || deleting) return
    setDeleting(true)
    try {
      await deleteIngestionJob(jobId)
      await queryClient.invalidateQueries({ queryKey: ingestionJobsKey })
      detachFromWorkset(tab.tabId)
      toast.success(t('ingestion.toast.delete.success'))
    } catch (e) {
      toast.error(t('ingestion.toast.delete.failed'))
      setDeleting(false)
    }
  }, [jobId, deleting, queryClient, detachFromWorkset, tab.tabId, t])

  if (!jobId) {
    return (
      <div className="flex items-center justify-center h-full text-xs text-muted-foreground">
        {t('ingestion.job.loading')}
      </div>
    )
  }

  if (isLoading || !job) {
    return (
      <div className="flex items-center justify-center h-full text-xs text-muted-foreground">
        {t('ingestion.job.loading')}
      </div>
    )
  }

  const currentPhase = phaseIndex(job.status)
  const canDelete = DELETABLE_STATUSES.has(job.status)

  return (
    <div className="flex flex-col h-full" data-testid="ingestion-job-tab">
      {/* Header */}
      <div className="flex items-center gap-3 px-4 py-2 border-b border-border">
        <span className="text-sm font-medium truncate">
          {t('ingestion.job.title', { id: jobId.slice(0, 8) })}
        </span>
        <Badge variant="outline" className={`text-xs px-1.5 py-0 ${statusColor(job.status)}`}>
          {t(`ingestion.status.${job.status}` as any)}        </Badge>
        <div className="flex-1" />
        {/* Phase stepper dots */}
        <div className="flex items-center gap-1">
          {PHASE_ORDER.slice(0, 6).map((phase, i) => {
            const isDone = currentPhase > i
            const isCurrent = currentPhase === i
            const isError = (job.status === 'failed' || job.status === 'cancelled') && isCurrent
            return (
              <div
                key={phase}
                data-testid={`ingestion-stepper-dot-${phase}`}
                className={`size-2 rounded-full transition-colors ${
                  isDone
                    ? 'bg-status-success'
                    : isError
                      ? 'bg-status-danger'
                      : isCurrent
                        ? 'bg-primary'
                        : 'bg-border'
                }`}
                title={phase}
              />
            )
          })}
        </div>
        {canDelete && (
          <button
            data-testid="ingestion-job-delete-btn"
            onClick={() => void handleDelete()}
            disabled={deleting}
            className="flex items-center gap-1 rounded-md px-2 py-1 text-xs text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive disabled:opacity-50"
          >
            {deleting ? <Loader2 className="size-3.5 animate-spin" /> : <Trash2 className="size-3.5" />}
          </button>
        )}
      </div>

      {/* Phase content */}
      <div className="min-h-0 flex-1 overflow-auto">
        {job.status === 'fetching' && <div data-testid="ingestion-phase-fetching"><FetchingPhase job={job} /></div>}
        {(job.status === 'fetched' || job.status === 'mapped') && (
          <div data-testid="ingestion-phase-mapping">
            <MappingPhase job={job} onConfirm={handleConfirm} onCancel={handleCancel} confirming={confirming} />
          </div>
        )}
        {job.status === 'confirmed' && <div data-testid="ingestion-phase-fetching"><FetchingPhase job={job} /></div>}
        {job.status === 'writing' && <div data-testid="ingestion-phase-writing"><WritingPhase job={job} /></div>}
        {job.status === 'completed' && <div data-testid="ingestion-phase-completed"><CompletedPhase job={job} /></div>}
        {(job.status === 'failed' || job.status === 'cancelled') && <div data-testid="ingestion-phase-failed"><FailedPhase job={job} /></div>}
      </div>
    </div>
  )
}
