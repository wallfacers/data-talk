import { useOntologyStore } from '@/stores/ontology-store'
import { useStageStore } from '@/stores/stage-store'

let subscribed = false
let prevTotalCount = 0

export function ensureStageAutoOpenSubscribed() {
  if (subscribed) return
  subscribed = true
  useOntologyStore.subscribe((state) => {
    let totalCount = 0
    for (const m of state.artifactsBySession.values()) {
      totalCount += m.size
    }
    if (totalCount > prevTotalCount) {
      useStageStore.getState().notifyArtifactArrived()
    }
    prevTotalCount = totalCount
  })
}

export function __resetStageAutoOpenForTest() {
  subscribed = false
  prevTotalCount = 0
}
