import { useCallback } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { InfoIcon } from 'lucide-react'
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
import { deleteConnection } from '@/services/api/connection'

export interface ConnectionDeleteCounts {
  sessions: number
  candidates: number
  temporary: number
  archived: number
}

export interface DeleteConnectionModalProps {
  connectionId: string
  connectionName: string
  counts: ConnectionDeleteCounts
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function DeleteConnectionModal({
  connectionId,
  connectionName,
  counts,
  open,
  onOpenChange,
}: DeleteConnectionModalProps) {
  const { t } = useI18n()
  const qc = useQueryClient()

  const hasOrphans = counts.archived > 0
  const hasAny = counts.sessions > 0 || counts.candidates > 0 || counts.temporary > 0

  const deleteMut = useMutation({
    mutationFn: () => deleteConnection(connectionId, { force: true }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['connections'] })
      qc.invalidateQueries({ queryKey: ['session-data-context'] })
      toast.success(t('common.deleted'))
      onOpenChange(false)
    },
    onError: () => {
      toast.error(t('connections.deleteModal.deleteError'))
    },
  })

  const handleConfirm = useCallback(() => {
    if (deleteMut.isPending) return
    deleteMut.mutate()
  }, [deleteMut])

  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      onOpenChange(nextOpen)
    },
    [onOpenChange]
  )

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="w-[480px] bg-bg-panel p-6" showCloseButton={!deleteMut.isPending}>
        <DialogHeader>
          <DialogTitle className="text-lg">
            {t('connections.deleteModal.title', { connectionName })}
          </DialogTitle>
          <DialogDescription>
            {t('connections.deleteModal.description')}
          </DialogDescription>
        </DialogHeader>

        {/* Summary lines */}
        <div className="space-y-1 text-sm text-base">
          {hasAny && (
            <div>
              {t('connections.deleteModal.summarySessions', { count: counts.sessions })}
              {counts.candidates > 0 || counts.temporary > 0 ? (
                <span>
                  {counts.candidates > 0 && counts.temporary > 0
                    ? `、${counts.candidates} ${t('connections.deleteModal.summaryCandidates')}${counts.temporary > 0 ? `、${counts.temporary} ${t('connections.deleteModal.summaryTemporary')}` : ''}`
                    : counts.candidates > 0
                      ? `、${counts.candidates} ${t('connections.deleteModal.summaryCandidates')}`
                      : `、${counts.temporary} ${t('connections.deleteModal.summaryTemporary')}`}
                </span>
              ) : null}
            </div>
          )}
        </div>

        {/* Orphan banner — only shown when archived > 0 */}
        {hasOrphans && (
          <div className="flex items-start gap-2 rounded-md border border-status-info bg-status-infoSurface p-3 text-sm">
            <InfoIcon className="mt-0.5 size-4 shrink-0 text-status-info" />
            <span className="text-status-info">
              {t('connections.deleteModal.orphanBanner', { count: counts.archived })}
            </span>
          </div>
        )}

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => handleOpenChange(false)}
            disabled={deleteMut.isPending}
          >
            {t('connections.deleteModal.cancel')}
          </Button>
          <Button
            variant="destructive"
            onClick={handleConfirm}
            disabled={deleteMut.isPending}
          >
            {deleteMut.isPending ? t('common.loading') : t('connections.deleteModal.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
