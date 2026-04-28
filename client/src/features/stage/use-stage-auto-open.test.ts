import { describe, it, expect, beforeEach } from 'vitest'
import { useOntologyStore } from '@/stores/ontology-store'
import { useStageStore } from '@/stores/stage-store'

describe('use-stage-auto-open', () => {
  beforeEach(async () => {
    useOntologyStore.setState({ artifactsBySession: new Map() })
    useStageStore.setState({ open: false, autoOpened: false } as never)
    const mod = await import('./use-stage-auto-open')
    mod.__resetStageAutoOpenForTest()
  })

  it('首个 artifact 到达 → stage 自动开', async () => {
    const { ensureStageAutoOpenSubscribed } = await import('./use-stage-auto-open')
    ensureStageAutoOpenSubscribed()

    useOntologyStore.getState().upsertArtifact('s1', {
      id: 'a1', version: 1, kind: 'table',
    })
    expect(useStageStore.getState().open).toBe(true)
    expect(useStageStore.getState().autoOpened).toBe(true)
  })

  it('closeStage 后再来 artifact → 再次自弹', async () => {
    const { ensureStageAutoOpenSubscribed } = await import('./use-stage-auto-open')
    ensureStageAutoOpenSubscribed()

    useOntologyStore.getState().upsertArtifact('s1', {
      id: 'a1', version: 1, kind: 'table',
    })
    expect(useStageStore.getState().open).toBe(true)

    useStageStore.getState().closeStage()
    expect(useStageStore.getState().open).toBe(false)
    expect(useStageStore.getState().autoOpened).toBe(false)

    useOntologyStore.getState().upsertArtifact('s1', {
      id: 'a2', version: 1, kind: 'chart',
    })
    expect(useStageStore.getState().open).toBe(true)
  })

  it('多 session 的 artifact 都能触发自动开', async () => {
    const { ensureStageAutoOpenSubscribed } = await import('./use-stage-auto-open')
    ensureStageAutoOpenSubscribed()

    useOntologyStore.getState().upsertArtifact('s1', {
      id: 'a1', version: 1, kind: 'table',
    })
    expect(useStageStore.getState().open).toBe(true)

    // After close, a different session's artifact re-triggers
    useStageStore.getState().closeStage()
    useOntologyStore.getState().upsertArtifact('s2', {
      id: 'b1', version: 1, kind: 'table',
    })
    expect(useStageStore.getState().open).toBe(true)
  })
})
