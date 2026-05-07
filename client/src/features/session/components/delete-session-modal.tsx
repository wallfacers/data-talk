import { useState, useCallback, useRef, useEffect } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ArchiveIcon, Trash2Icon, AlertTriangleIcon } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { archiveFile, discardFile } from '@/services/api/file-artifacts'
import { deleteSession } from '@/services/api/session'

export interface DeleteSessionCandidate {
  id: string
  filename: string
  kind: string
  sizeBytes: number
  title: string | null
  summary: string | null
}

export interface DeleteSessionModalProps {
  sessionId: string
  connectionName: string
  candidates: DeleteSessionCandidate[]
  open: boolean
  onOpenChange: (open: boolean) => void
}

type CandidateState = 'pending' | 'archive' | 'discard' | 'in_progress' | 'done' | 'failed'

interface CandidateRow {
  candidate: DeleteSessionCandidate
  state: CandidateState
}

export function DeleteSessionModal({
  sessionId,
  connectionName,
  candidates,
  open,
  onOpenChange,
}: DeleteSessionModalProps) {
  const { t } = useI18n()
  const qc = useQueryClient()
  const didProcessRef = useRef(false)

  const [rows, setRows] = useState<CandidateRow[]>([])

  // Initialize rows when modal opens
  useEffect(() => {
    if (open && candidates.length > 0) {
      setRows(
        candidates.map((c) => ({ candidate: c, state: 'pending' as CandidateState }))
      )
      didProcessRef.current = false
    }
  }, [open, candidates])

  const allDecided = rows.length > 0 && rows.every((r) => r.state === 'archive' || r.state === 'discard')
  const anyInProgress = rows.some((r) => r.state === 'in_progress')
  const anyFailed = rows.some((r) => r.state === 'failed')
  const hasFailedIds = rows.filter((r) => r.state === 'failed').map((r) => r.candidate.id)

  const setCandidateState = useCallback((id: string, state: CandidateState) => {
    setRows((prev) =>
      prev.map((r) => (r.candidate.id === id ? { ...r, state } : r))
    )
  }, [])

  const handleAllArchive = useCallback(() => {
    setRows((prev) =>
      prev.map((r) => (r.state === 'pending' || r.state === 'discard' ? { ...r, state: 'archive' } : r))
    )
  }, [])

  const handleAllDiscard = useCallback(() => {
    setRows((prev) =>
      prev.map((r) => (r.state === 'pending' || r.state === 'archive' ? { ...r, state: 'discard' } : r))
    )
  }, [])

  // Process: archive/discard per candidate, then force delete session
  const processMut = useMutation({
    mutationFn: async () => {
      // Only process candidates that are archive/discard (not done or failed)
      const toProcess = rows.filter(
        (r) => r.state === 'archive' || r.state === 'discard' || r.state === 'failed'
      )

      for (const row of toProcess) {
        setCandidateState(row.candidate.id, 'in_progress')
        try {
          if (row.state === 'archive' || row.state === 'failed') {
            await archiveFile(sessionId, row.candidate.id)
          } else {
            await discardFile(row.candidate.id)
          }
          setCandidateState(row.candidate.id, 'done')
        } catch {
          setCandidateState(row.candidate.id, 'failed')
          throw new Error(t('files.deleteModal.processFailed'))
        }
      }

      // All succeeded — force delete session
      await deleteSession(sessionId, { force: true })
    },
    onSuccess: () => {
      didProcessRef.current = true
      qc.invalidateQueries({ queryKey: ['sessions'] })
      toast.success(t('common.deleted'))
      onOpenChange(false)
    },
    onError: () => {
      toast.error(t('files.deleteModal.processError'))
    },
  })

  const handleConfirm = useCallback(() => {
    if (!allDecided || anyInProgress) return
    processMut.mutate()
  }, [allDecided, anyInProgress, processMut])

  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      if (!nextOpen) {
        // Clear state on close
        setRows([])
        didProcessRef.current = false
      }
      onOpenChange(nextOpen)
    },
    [onOpenChange]
  )

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="w-[480px] bg-canvas p-6" showCloseButton={!processMut.isPending}>
        <DialogHeader>
          <DialogTitle className="text-lg">{t('files.deleteModal.title')}</DialogTitle>
          <DialogDescription>
            {t('files.deleteModal.description', { connectionName })}
          </DialogDescription>
        </DialogHeader>

        {/* Bulk actions */}
        <div className="flex gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={handleAllArchive}
            disabled={processMut.isPending}
            className="h-8 text-sm"
          >
            <ArchiveIcon className="mr-1 size-3.5" />
            {t('files.deleteModal.allArchive')}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleAllDiscard}
            disabled={processMut.isPending}
            className="h-8 text-sm"
          >
            <Trash2Icon className="mr-1 size-3.5" />
            {t('files.deleteModal.allDiscard')}
          </Button>
        </div>

        {/* Candidate list */}
        <div className="flex max-h-[320px] flex-col gap-1 overflow-y-auto">
          {rows.map((row) => (
            <CandidateRowView
              key={row.candidate.id}
              row={row}
              connectionName={connectionName}
              onArchive={() => setCandidateState(row.candidate.id, 'archive')}
              onDiscard={() => setCandidateState(row.candidate.id, 'discard')}
              disabled={processMut.isPending}
              t={t}
            />
          ))}
        </div>

        {/* Error indicator */}
        {anyFailed && !processMut.isPending && (
          <div className="flex items-center gap-2 rounded-md bg-status-dangerSurface p-2 text-sm text-status-danger">
            <AlertTriangleIcon className="size-4" />
            <span>{t('files.deleteModal.someFailed')}</span>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                hasFailedIds.forEach((id) => setCandidateState(id, 'archive'))
              }}
              className="h-6 text-xs"
            >
              {t('common.retry')}
            </Button>
          </div>
        )}

        <DialogFooter>
          <Button
            variant="ghost"
            onClick={() => handleOpenChange(false)}
            disabled={processMut.isPending}
            className="h-8 text-sm"
          >
            {t('files.deleteModal.cancel')}
          </Button>
          <Button
            onClick={handleConfirm}
            disabled={!allDecided || anyInProgress}
            className="h-8 text-sm bg-accent-primary text-inverse hover:bg-accent-primaryHover"
          >
            {processMut.isPending ? t('common.loading') : t('files.deleteModal.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function CandidateRowView({
  row,
  connectionName,
  onArchive,
  onDiscard,
  disabled,
  t,
}: {
  row: CandidateRow
  connectionName: string
  onArchive: () => void
  onDiscard: () => void
  disabled: boolean
  t: ReturnType<typeof useI18n>['t']
}) {
  const isArchive = row.state === 'archive'
  const isDiscard = row.state === 'discard'
  const isDone = row.state === 'done'
  const isFailed = row.state === 'failed'
  const isInProgress = row.state === 'in_progress'

  const sizeLabel = formatBytes(row.candidate.sizeBytes)

  return (
    <div
      className={cn(
        'flex items-center gap-2 rounded-md px-2 py-1.5 text-sm',
        isDone && 'opacity-50',
        isFailed && 'bg-status-dangerSurface'
      )}
    >
      <span className="flex-1 truncate text-base">
        {row.candidate.filename}
        {row.candidate.title ? ` — ${row.candidate.title}` : ''}
        <span className="ml-1 text-muted">({sizeLabel})</span>
      </span>

      {isDone && <span className="text-xs text-status-success">{t('common.done')}</span>}
      {isFailed && (
        <span className="text-xs text-status-danger">{t('common.failed')}</span>
      )}
      {isInProgress && <span className="text-xs text-muted">{t('common.loading')}</span>}

      {!isDone && !isInProgress && (
        <div className="flex gap-1">
          <button
            onClick={onArchive}
            disabled={disabled}
            className={cn(
              'h-6 rounded px-2 text-xs transition-colors',
              isArchive
                ? 'bg-accent-primarySurface text-accent-primary border border-accent-primary'
                : 'border border-subtle text-base hover:bg-hover hover:border-default',
              disabled && 'cursor-not-allowed text-disabled'
            )}
            aria-label={t('files.action.archive')}
          >
            {t('files.deleteModal.archiveTo', { connectionName })}
          </button>
          <button
            onClick={onDiscard}
            disabled={disabled}
            className={cn(
              'h-6 rounded px-2 text-xs transition-colors',
              isDiscard
                ? 'bg-status-dangerSurface text-status-danger border border-status-danger'
                : 'border border-subtle text-base hover:bg-hover hover:border-default',
              disabled && 'cursor-not-allowed text-disabled'
            )}
            aria-label={t('files.action.discard')}
          >
            {t('files.deleteModal.discard')}
          </button>
        </div>
      )}
    </div>
  )
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function cn(...classes: (string | false | undefined | null)[]) {
  return classes.filter(Boolean).join(' ')
}
