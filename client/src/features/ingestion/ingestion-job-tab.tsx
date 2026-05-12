import { useCallback, useEffect, useState } from 'react'
import { useI18n } from '@/i18n/use-i18n'
import { useIngestionJobQuery } from './hooks/use-ingestion-job-query'
import { useIngestionJobsStore } from './stores/use-ingestion-jobs-store'
import { confirmIngestionJob, cancelIngestionJob } from './api/ingestion-api'
import { FetchingPhase } from './phases/fetching-phase'
import { MappingPhase } from './phases/mapping-phase'
import { WritingPhase } from './phases/writing-phase'
import { CompletedPhase } from './phases/completed-phase'
import { FailedPhase } from './phases/failed-phase'
import { Badge } from '@/components/ui/badge'
import type { StageTab } from '@/stores/stage-store'

interface IngestionJobTabProps {
  tab: StageTab
}

const PHASE_ORDER = ['fetching', 'fetched', 'mapped', 'confirmed', 'writing', 'completed', 'failed', 'cancelled'] as const

function phaseIndex(status: string): number {
  const idx = PHASE_ORDER.indexOf(status as typeof PHASE_ORDER[number])
  return idx >= 0 ? idx : 0
}

function statusColor(status: string): string {
  switch (status) {
    case 'completed': return 'bg-status-success/15 text-status-success border-status-success/30'
    case 'failed': case 'cancelled': return 'bg-status-danger/15 text-status-danger border-status-danger/30'
    case 'writing': return 'bg-accent-primarySurface text-accent-primary border-accent-primary/30'
    case 'fetching': return 'bg-status-info/15 text-status-info border-status-info/30'
    default: return 'bg-bg-subtle text-text-muted border-border-default'
  }
}

export function IngestionJobTab({ tab }: IngestionJobTabProps) {
  const { t } = useI18n()
  const payload = tab.payload as { id?: string } | null
  const jobId = payload?.id ?? ''
  const { data: job, isLoading } = useIngestionJobQuery(jobId)
  const editingMapping = useIngestionJobsStore((s) => s.editingMapping)
  const [, setConfirming] = useState(false)
  useEffect(() => {
    if (!job || !jobId) return
    if (editingMapping.has(jobId)) return
    if (job.status !== 'fetched' && job.status !== 'mapped') return
    // We need columns from the mapping — this would come from the infer schema action result
    // For now, the mapping editor only shows once the AI calls infer_ingestion_schema
    // and the result is stored. The mapping phase shows an empty editor until then.
  }, [job, jobId, editingMapping])

  const handleConfirm = useCallback(async () => {
    if (!jobId) return
    setConfirming(true)
    try {
      await confirmIngestionJob(jobId)
    } finally {
      setConfirming(false)
    }
  }, [jobId])

  const handleCancel = useCallback(async () => {
    if (!jobId) return
    await cancelIngestionJob(jobId)
  }, [jobId])

  if (!jobId) return null

  if (isLoading || !job) {
    return (
      <div className="flex items-center justify-center h-full text-ui-sm text-text-muted">
        Loading...
      </div>
    )
  }

  const currentPhase = phaseIndex(job.status)

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center gap-3 px-4 py-2 border-b border-border-default">
        <span className="text-ui-md font-medium text-text-strong truncate">
          {t('ingestion.job.title', { id: jobId.slice(0, 8) })}
        </span>
        <Badge variant="outline" className={`text-ui-xs px-1.5 py-0 ${statusColor(job.status)}`}>
          {job.status}
        </Badge>
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
                className={`h-2 w-2 rounded-full transition-colors ${
                  isDone
                    ? 'bg-status-success'
                    : isError
                      ? 'bg-status-danger'
                      : isCurrent
                        ? 'bg-accent-primary'
                        : 'bg-border-default'
                }`}
                title={phase}
              />
            )
          })}
        </div>
      </div>

      {/* Phase content */}
      <div className="flex-1 overflow-auto">
        {job.status === 'fetching' && <FetchingPhase job={job} />}
        {(job.status === 'fetched' || job.status === 'mapped') && (
          <MappingPhase job={job} onConfirm={handleConfirm} onCancel={handleCancel} />
        )}
        {job.status === 'confirmed' && <FetchingPhase job={job} />}
        {job.status === 'writing' && <WritingPhase job={job} />}
        {job.status === 'completed' && <CompletedPhase job={job} />}
        {(job.status === 'failed' || job.status === 'cancelled') && <FailedPhase job={job} />}
      </div>
    </div>
  )
}
