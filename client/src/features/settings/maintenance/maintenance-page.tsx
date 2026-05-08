import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { toast } from 'sonner'
import { getStorageOverview, cleanupTrash, getOrphanedFiles } from '@/services/api/maintenance'
import { OrphanArchivesDrawer } from './orphan-archives-drawer'
import { useState } from 'react'
import { RefreshCw } from 'lucide-react'
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
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)

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
          <span className="text-base font-medium">{t('maintenance.storageOverview.workdir')}</span>
          <span className="ml-2 text-sm font-mono text-strong break-all">
            {d.workdir}
          </span>
        </div>
        <div className="text-base font-medium">
          {t('maintenance.storageOverview.totalSize')}: <span className="text-strong">{fmtBytes(d.totalBytes)}</span>
        </div>
      </div>

      <div className="mb-4 rounded-md border border-default bg-panel p-3">
        <ul className="space-y-1.5 text-sm">
          {Object.entries(d.breakdown).map(([key, item]) => (
            <li key={key} className="flex items-center justify-between text-sm">
              <div className="flex items-center gap-1.5 text-base font-medium">
                <span>├─</span>
                <span>{item.label}</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="font-mono text-sm">{fmtBytes(item.bytes)}</span>
                {key === 'workspaces' && orphanCount > 0 && (
                  <button
                    type="button"
                    className="text-status-info text-xs hover:underline"
                    onClick={() => setDrawerOpen(true)}
                  >
                    {t('maintenance.storageOverview.orphanedCount', { n: orphanCount })}
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      </div>

      <Button
        variant="destructive"
        className="mb-6"
        onClick={() => setConfirmOpen(true)}
        disabled={cleanup.isPending}
      >
        {t('maintenance.action.cleanupTrash')}
      </Button>

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
          <AlertDialogFooter className="border-t-0 bg-transparent pt-2">
            <AlertDialogCancel className="border-0 bg-transparent hover:bg-muted/50">
              {t('common.cancel')}
            </AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              className="border-0 bg-transparent"
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

      {drawerOpen && (
        <OrphanArchivesDrawer
          files={orphans.data ?? []}
          onClose={() => { setDrawerOpen(false); qc.invalidateQueries({ queryKey: ['maintenance'] }) }}
        />
      )}
    </div>
  )
}
