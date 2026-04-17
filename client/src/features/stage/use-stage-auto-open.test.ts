import { describe, it, expect, beforeEach } from 'vitest'
import { useOntologyStore } from '@/stores/ontology-store'
import { useStageStore } from '@/stores/stage-store'

describe('use-stage-auto-open', () => {
  beforeEach(async () => {
    useOntologyStore.setState({ artifactsBySession: new Map() })
    useStageStore.setState({ openBySession: new Map(), autoOpenedSessions: new Set() })
    const mod = await import('./use-stage-auto-open')
    mod.__resetStageAutoOpenForTest()
  })

  it('首个 artifact 到达 → stage 自动开', async () => {
    const { ensureStageAutoOpenSubscribed } = await import('./use-stage-auto-open')
    ensureStageAutoOpenSubscribed()

    useOntologyStore.getState().upsertArtifact('s1', {
      id: 'a1', version: 1, kind: 'table',
    })
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
  })

  it('用户先 close 后再来 artifact → 不再自弹', async () => {
    const { ensureStageAutoOpenSubscribed } = await import('./use-stage-auto-open')
    ensureStageAutoOpenSubscribed()

    useStageStore.getState().closeStage('s1')
    useOntologyStore.getState().upsertArtifact('s1', {
      id: 'a1', version: 1, kind: 'chart',
    })
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('多 session 互不影响', async () => {
    const { ensureStageAutoOpenSubscribed } = await import('./use-stage-auto-open')
    ensureStageAutoOpenSubscribed()

    useOntologyStore.getState().upsertArtifact('s1', {
      id: 'a1', version: 1, kind: 'table',
    })
    useOntologyStore.getState().upsertArtifact('s2', {
      id: 'b1', version: 1, kind: 'table',
    })
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
    expect(useStageStore.getState().openBySession.get('s2')).toBe(true)
  })
})
