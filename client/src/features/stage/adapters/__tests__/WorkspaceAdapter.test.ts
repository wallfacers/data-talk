import { describe, it, expect, beforeEach } from 'vitest'
import { WorkspaceAdapter } from '../WorkspaceAdapter'
import { useStageStore } from '@/stores/stage-store'

describe('WorkspaceAdapter', () => {
  beforeEach(() => {
    useStageStore.setState({
      workspaceTabs: [], tabsBySession: new Map(),
      activeWorkspaceTabId: null, activeTabIdBySession: new Map(),
    } as unknown as Record<string, unknown>)
  })

  it('exec open creates workspace tab', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const res = await adapter.exec('open', { type: 'bang_query', title: 'SELECT 1', connection_id: 'conn-1' })
    expect(res.success).toBe(true)
    const tabs = useStageStore.getState().workspaceTabs
    expect(tabs).toHaveLength(1)
    expect(tabs[0].type).toBe('bang_query')
    expect(tabs[0].connectionId).toBe('conn-1')
    expect(tabs[0].originSessionId).toBe('s1')
  })

  it('read state returns tabs + active', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    await adapter.exec('open', { type: 'bang_query', title: 'Q' })
    const state = adapter.read('state') as { tabs: unknown[]; activeTabId: string | null }
    expect(state.tabs.length).toBe(1)
  })

  it('exec close removes tab', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type: 'bang_query', title: 'Q' })
    const tabId = (opened.data as { tabId: string }).tabId
    const closed = await adapter.exec('close', { target: tabId })
    expect(closed.success).toBe(true)
    expect(useStageStore.getState().workspaceTabs).toHaveLength(0)
  })

  it('rejects open without type', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const res = await adapter.exec('open', {})
    expect(res.success).toBe(false)
  })
})
