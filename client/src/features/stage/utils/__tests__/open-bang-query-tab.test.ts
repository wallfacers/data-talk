import { describe, it, expect, beforeEach, vi } from 'vitest'
import { openBangQueryTab } from '../open-bang-query-tab'
import { useStageStore } from '@/stores/stage-store'

vi.mock('@/services/api/query', () => ({
  executeQuery: vi.fn(async (input: { sql: string }) => ({
    columns: ['c'], rows: [{ c: input.sql.length }], durationMs: 5, rowCount: 1,
  })),
}))

describe('openBangQueryTab', () => {
  beforeEach(() => {
    useStageStore.setState({
      workspaceTabs: [],
      tabsBySession: new Map(),
      activeWorkspaceTabId: null,
      activeTabIdBySession: new Map(),
      openBySession: new Map(),
      autoOpenedSessions: new Set(),
    } as unknown as Record<string, unknown>)
  })

  it('creates a bang_query tab on success and opens stage', async () => {
    await openBangQueryTab({ sessionId: 's1', connectionId: 'c1', sql: 'SELECT 1' })
    const tabs = useStageStore.getState().workspaceTabs
    expect(tabs).toHaveLength(1)
    expect(tabs[0].type).toBe('bang_query')
    expect(tabs[0].connectionId).toBe('c1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
  })

  it('throws when no connectionId provided', async () => {
    await expect(openBangQueryTab({ sessionId: 's1', connectionId: null, sql: 'SELECT 1' })).rejects.toThrow()
  })
})
