import { describe, it, expect, beforeEach } from 'vitest'
import { useStageStore } from './stage-store'

describe('stage-store', () => {
  beforeEach(() => {
    useStageStore.setState({ openBySession: new Map(), autoOpenedSessions: new Set() })
  })

  it('openStage / closeStage 切换 openBySession', () => {
    const { openStage, closeStage } = useStageStore.getState()
    openStage('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
    closeStage('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('closeStage 隐式 markAutoOpened，禁止后续自弹', () => {
    const { closeStage, notifyArtifactArrived } = useStageStore.getState()
    closeStage('s1')
    expect(useStageStore.getState().autoOpenedSessions.has('s1')).toBe(true)
    notifyArtifactArrived('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('notifyArtifactArrived 首次自弹，幂等', () => {
    const { notifyArtifactArrived } = useStageStore.getState()
    notifyArtifactArrived('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
    expect(useStageStore.getState().autoOpenedSessions.has('s1')).toBe(true)
    notifyArtifactArrived('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
  })

  it('toggleStage 切换状态', () => {
    const { toggleStage } = useStageStore.getState()
    toggleStage('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
    toggleStage('s1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
  })

  it('syncCollapsed(true) 反向同步并标 autoOpened', () => {
    const { openStage, syncCollapsed } = useStageStore.getState()
    openStage('s1')
    syncCollapsed('s1', true)
    expect(useStageStore.getState().openBySession.get('s1')).toBe(false)
    expect(useStageStore.getState().autoOpenedSessions.has('s1')).toBe(true)
  })

  it('syncCollapsed(false) 反向同步且不动 autoOpened', () => {
    const { syncCollapsed } = useStageStore.getState()
    syncCollapsed('s1', false)
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
    expect(useStageStore.getState().autoOpenedSessions.has('s1')).toBe(false)
  })

  it('clear 清空指定 session', () => {
    const { openStage, clear } = useStageStore.getState()
    openStage('s1')
    openStage('s2')
    clear('s1')
    expect(useStageStore.getState().openBySession.has('s1')).toBe(false)
    expect(useStageStore.getState().openBySession.get('s2')).toBe(true)
  })
})
