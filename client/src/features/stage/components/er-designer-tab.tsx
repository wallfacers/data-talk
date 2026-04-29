import { useCallback, useEffect, useMemo } from 'react'
import type { ComponentType } from 'react'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import type { ErDesignerPayload, JsonPatchOp } from '@/features/stage/stores/er-tabs-payload-types'
import { useI18n } from '@/i18n/use-i18n'
import { ErDesignerAdapter } from '../adapters/ErDesignerAdapter'
import { coordinator } from '../persistence/stage-persistence-bootstrap'
import { ErCanvas } from './er-canvas/ErCanvas'

type ErDesignerCanvasProps = {
  tabId: string
  mode: 'designer'
  payload: ErDesignerPayload
  onPatch: (ops: JsonPatchOp[]) => void
  onExec: (action: string, params?: unknown) => void
}

const DesignerCanvas = ErCanvas as unknown as ComponentType<ErDesignerCanvasProps>

export function ErDesignerTab({ tabId }: { tabId: string }) {
  const { t } = useI18n()
  const payload = useErTabsStore((state) => state.designers.get(tabId) ?? null)

  useEffect(() => {
    void coordinator.ensureHydrated(tabId)
  }, [tabId])

  const adapter = useMemo(() => new ErDesignerAdapter(tabId, () => null), [tabId])

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
    <DesignerCanvas
      tabId={tabId}
      mode="designer"
      payload={payload}
      onPatch={onPatch}
      onExec={onExec}
    />
  )
}
