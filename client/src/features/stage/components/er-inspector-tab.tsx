import { useCallback, useEffect, useMemo } from 'react'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import type { JsonPatchOp } from '@/features/stage/stores/er-tabs-payload-types'
import { ErInspectorAdapter } from '../adapters/ErInspectorAdapter'
import { coordinator } from '../persistence/stage-persistence-bootstrap'
import { ErCanvas } from './er-canvas/ErCanvas'
import { TabContentLoader } from './tab-content-loader'

export function ErInspectorTab({ tabId }: { tabId: string }) {
  const payload = useErTabsStore((state) => state.inspectors.get(tabId) ?? null)

  useEffect(() => {
    void coordinator.ensureHydrated(tabId)
  }, [tabId])

  const adapter = useMemo(() => new ErInspectorAdapter(tabId, () => null), [tabId])

  const onPatch = useCallback((ops: JsonPatchOp[]) => {
    void adapter.patch(ops)
  }, [adapter])

  const onExec = useCallback((action: string, params?: unknown) => {
    adapter.exec(action, params).catch((error: unknown) => {
      console.error(`[ErInspectorTab] exec("${action}") failed:`, error)
    })
  }, [adapter])

  if (!payload) {
    return (
      <div data-er-tab-id={tabId} className="h-full">
        <TabContentLoader />
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
