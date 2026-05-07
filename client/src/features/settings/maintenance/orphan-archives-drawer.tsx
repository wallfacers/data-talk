import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { useI18n } from '@/i18n/use-i18n'
import { toast } from 'sonner'
import { reattachFile, type OrphanedFileDto } from '@/services/api/maintenance'
import { discardFile } from '@/services/api/file-artifacts'
import { useConnectionStore } from '@/features/connection/store'

interface Props {
  files: OrphanedFileDto[]
  onClose: () => void
}

export function OrphanArchivesDrawer({ files, onClose }: Props) {
  const { t } = useI18n()
  const qc = useQueryClient()
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
    setProcessing(false)
    qc.invalidateQueries({ queryKey: ['maintenance'] })
  }

  return (
    <div className="fixed inset-y-0 right-0 w-[480px] bg-canvas border-l border-subtle shadow-lg z-50 flex flex-col">
      <div className="flex items-center justify-between px-4 py-3 border-b border-subtle">
        <h3 className="text-sm font-medium text-strong">{t('maintenance.orphans.drawer.title', { n: files.length })}</h3>
        <Button variant="ghost" size="sm" onClick={onClose}>✕</Button>
      </div>

      <p className="px-4 py-2 text-xs text-muted">{t('maintenance.orphans.drawer.description')}</p>

      {connections.length === 0 && (
        <div className="mx-4 p-2 bg-status-infoSurface text-status-info border border-status-info rounded-md text-xs">
          {t('maintenance.orphans.drawer.bannerNoConnection')}
        </div>
      )}

      <div className="flex items-center gap-2 px-4 py-2 border-b border-subtle">
        <Button variant="ghost" size="sm" onClick={toggleAll}>{t('maintenance.orphans.drawer.selectAll')}</Button>
        {connections.length > 0 && (
          <>
            <select value={targetConn} onChange={e => setTargetConn(e.target.value)} className="text-xs border border-subtle rounded px-1 py-0.5 bg-panel">
              {connections.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
            <Button variant="ghost" size="sm" disabled={selected.size === 0 || processing} onClick={async () => {
              setProcessing(true)
              let ok = 0
              for (const id of selected) { const r = await doReattach(id, targetConn); if (r.ok) ok++ }
              toast(t('maintenance.orphans.toast.reattachPartial', { ok, fail: selected.size - ok }))
              setProcessing(false)
              qc.invalidateQueries({ queryKey: ['maintenance'] })
            }}>{t('maintenance.orphans.drawer.reattachBulk')}</Button>
          </>
        )}
        <Button variant="ghost" size="sm" disabled={selected.size === 0 || processing} onClick={batchDiscard}>
          {t('maintenance.orphans.drawer.discardBulk')}
        </Button>
      </div>

      <div className="flex-1 overflow-y-auto">
        {files.length > 200 && (
          <p className="px-4 py-1 text-xs text-status-warning">{t('maintenance.orphans.drawer.tooltipOver200')}</p>
        )}
        {files.map(f => (
          <div key={f.id} className="flex items-center gap-3 px-4 py-2 border-b border-subtle hover:bg-interaction-hover">
            <Checkbox checked={selected.has(f.id)} onCheckedChange={() => toggle(f.id)} />
            <div className="flex-1 min-w-0">
              <div className="text-sm text-strong truncate">{f.filename}</div>
              <div className="text-xs text-muted">{f.kind} · {(f.sizeBytes / 1024).toFixed(1)} KB</div>
              {f.orphanedFromConnection && (
                <div className="text-xs text-muted mt-0.5">{t('maintenance.orphans.drawer.originalConnection', { name: f.orphanedFromConnection })}</div>
              )}
            </div>
            {connections.length > 0 && (
              <Button variant="ghost" size="sm" disabled={processing} onClick={async () => { await doReattach(f.id, targetConn); qc.invalidateQueries({ queryKey: ['maintenance'] }) }}>
                {t('maintenance.orphans.drawer.reattach')}
              </Button>
            )}
            <Button variant="ghost" size="sm" disabled={processing} onClick={async () => { await doDiscard(f.id); qc.invalidateQueries({ queryKey: ['maintenance'] }) }}>
              {t('maintenance.orphans.drawer.discard')}
            </Button>
          </div>
        ))}
      </div>
    </div>
  )
}
