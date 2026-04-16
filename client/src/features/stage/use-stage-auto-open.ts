import { useOntologyStore } from '@/stores/ontology-store'
import { useStageStore } from '@/stores/stage-store'

let subscribed = false
const lastSizeBySession = new Map<string, number>()

export function ensureStageAutoOpenSubscribed() {
  if (subscribed) return
  subscribed = true
  useOntologyStore.subscribe((state) => {
    for (const [sid, m] of state.artifactsBySession.entries()) {
      const prev = lastSizeBySession.get(sid) ?? 0
      const cur = m.size
      if (prev === 0 && cur > 0) {
        useStageStore.getState().notifyArtifactArrived(sid)
      }
      lastSizeBySession.set(sid, cur)
    }
  })
}

// 测试辅助：允许在 vi.resetModules 不可用的情形下重置内部状态
export function __resetStageAutoOpenForTest() {
  subscribed = false
  lastSizeBySession.clear()
}
