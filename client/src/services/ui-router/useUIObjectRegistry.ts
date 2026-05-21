import { useEffect } from 'react'
import { uiRouter } from './UIRouter'
import type { UIObject } from './types'

export function useUIObjectRegistry(instance: UIObject | null) {
  useEffect(() => {
    if (!instance) return
    uiRouter.registerInstance(instance.objectId, instance)
    return () => uiRouter.unregisterInstance(instance.objectId)
  }, [instance])
}
