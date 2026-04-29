import { useCallback, useEffect, useMemo } from 'react'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import type { JsonPatchOp } from '@/features/stage/stores/er-tabs-payload-types'
import { useI18n } from '@/i18n/use-i18n'
import { ErInspectorAdapter } from '../adapters/ErInspectorAdapter'
import { coordinator } from '../persistence/stage-persistence-bootstrap'
import { ErCanvas } from './er-canvas/ErCanvas'

export function ErInspectorTab({ tabId }: { tabId: string }) {
  const { t } = useI18n()
  const payload = useErTabsStore((state) => state.inspectors.get(tabId) ?? null)

  useEffect(() => {
    void coordinator.ensureHydrated(tabId)
  }, [tabId])

  const adapter = useMemo(() => new ErInspectorAdapter(tabId, () => null), [tabId])

  const onPatch = useCallback((ops: JsonPatchOp[]) => {
    void adapter.patch(ops)
  }, [adapter])

  const onExec = useCallback((action: string, params?: unknown) => {
    void adapter.exec(action, params)
  }, [adapter])

  if (!payload) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-text-muted">
        {t('erCanvas.loading')}
      </div>
    )
  }

  return (
    <ErCanvas
      tabId={tabId}
      mode="inspector"
      payload={payload}
      onPatch={onPatch}
      onExec={onExec}
    />
  )
}
