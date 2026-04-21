import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { StageToolRow } from './stage-tool-row'
import { useStageStore } from '@/stores/stage-store'
import { openOrFocusStageToolTab } from '@/features/stage/utils/open-or-focus-stage-tool-tab'

vi.mock('@/features/stage/utils/open-or-focus-stage-tool-tab', () => ({
  openOrFocusStageToolTab: vi.fn(),
}))

describe('StageToolRow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useStageStore.setState({
      workspaceTabs: [],
      tabsBySession: new Map(),
      activeWorkspaceTabId: null,
      activeTabIdBySession: new Map(),
    } as any)
  })

  it('opens the SQL editor through the shared helper', () => {
    vi.mocked(openOrFocusStageToolTab).mockReturnValue({ tabId: 'tab-1', created: true })
    useStageStore.setState({
      activeTabIdBySession: new Map([['sess-1', 'session-tab-1']]),
    } as any)

    render(<StageToolRow sessionId="sess-1" />)

    fireEvent.click(screen.getByRole('button', { name: 'SQL 编辑器' }))

    expect(openOrFocusStageToolTab).toHaveBeenCalledWith({
      getState: useStageStore.getState,
      sessionId: 'sess-1',
      target: {
        kind: 'global_tool',
        tool: 'sql',
        title: 'SQL 编辑器',
      },
    })
    expect(useStageStore.getState().activeTabIdBySession.get('sess-1')).toBeNull()
  })

  it('renders SQL as the only tool action', () => {
    render(<StageToolRow sessionId="sess-1" />)

    expect(screen.getByRole('button', { name: 'SQL 编辑器' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: /ER 图设计器/i })).toBeNull()
    expect(screen.queryByRole('button', { name: /报表/i })).toBeNull()
    expect(screen.queryByRole('button', { name: /Dashboard/i })).toBeNull()
  })
})
