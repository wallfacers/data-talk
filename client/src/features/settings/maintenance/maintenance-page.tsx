import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useI18n } from '@/i18n/use-i18n'
import { toast } from 'sonner'
import { getStorageOverview, cleanupTrash, getOrphanedFiles } from '@/services/api/maintenance'
import { OrphanArchivesDrawer } from './orphan-archives-drawer'
import { useState } from 'react'

function fmtBytes(n: number) {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

export function MaintenancePage() {
  const { t } = useI18n()
  const qc = useQueryClient()
  const [drawerOpen, setDrawerOpen] = useState(false)

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
      toast.success(t('maintenance.toast.cleanupTrashDone'))
      qc.invalidateQueries({ queryKey: ['maintenance'] })
    },
  })

  if (overview.isLoading) {
    return <div className="p-4 space-y-2"><Skeleton className="h-4 w-3/4" /><Skeleton className="h-4 w-1/2" /></div>
  }

  const d = overview.data
  if (!d) return null

  const orphanCount = orphans.data?.length ?? 0

  return (
    <div className="space-y-4 p-2">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-strong">{t('maintenance.tab.title')}</h3>
        <Button variant="ghost" size="sm" onClick={() => qc.invalidateQueries({ queryKey: ['maintenance'] })}>
          {t('maintenance.storageOverview.refresh')}
        </Button>
      </div>

      <div className="text-xs text-muted space-y-1">
        <div>{t('maintenance.storageOverview.workdir')}: <span className="text-base">{d.workdir}</span></div>
        <div>{t('maintenance.storageOverview.totalSize')}: {fmtBytes(d.totalBytes)}</div>
      </div>

      <ul className="space-y-1 text-sm">
        {Object.entries(d.breakdown).map(([key, item]) => (
          <li key={key} className="flex items-center gap-2 text-muted">
            <span className="text-base">├─ {item.label}</span>
            <span>{fmtBytes(item.bytes)}</span>
            {key === 'workspaces' && orphanCount > 0 && (
              <button
                type="button"
                className="text-status-info text-xs ml-2 hover:underline"
                onClick={() => setDrawerOpen(true)}
              >
                {t('maintenance.storageOverview.orphanedCount', { n: orphanCount })}
              </button>
            )}
          </li>
        ))}
      </ul>

      <div className="flex gap-2 mt-4">
        <Button variant="ghost" size="sm" onClick={() => cleanup.mutate()} disabled={cleanup.isPending}>
          {t('maintenance.action.cleanupTrash')}
        </Button>
      </div>

      {drawerOpen && (
        <OrphanArchivesDrawer
          files={orphans.data ?? []}
          onClose={() => { setDrawerOpen(false); qc.invalidateQueries({ queryKey: ['maintenance'] }) }}
        />
      )}
    </div>
  )
}
