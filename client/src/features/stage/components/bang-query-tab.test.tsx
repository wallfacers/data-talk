import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { BangQueryTab } from './bang-query-tab'
import { useConnectionStore } from '@/features/connection/store'
import { useStageStore } from '@/stores/stage-store'

vi.mock('@/features/data-grid/components/data-grid', () => ({
  DataGrid: () => <div>data-grid</div>,
}))

describe('BangQueryTab', () => {
  beforeEach(() => {
    useConnectionStore.setState({ activeConnectionId: null, connections: [] })
    useStageStore.setState({
      workspaceTabs: [{
        tabId: 't1',
        type: 'bang_query',
        title: 'SELECT 1',
        scope: 'workspace',
        connectionId: 'c1',
        connectionName: 'orders-prod',
        database: 'orders',
        schema: 'public',
        payload: {
          sql: 'SELECT 1',
          rows: [{ c: 1 }],
          lastRun: { columns: ['c'], rowCount: 1, durationMs: 5, truncated: false },
        },
        createdAt: 1,
      }],
      tabsBySession: new Map(),
      activeWorkspaceTabId: 't1',
      activeTabIdBySession: new Map(),
    } as any)
  })

  it('renders source badge and switches active connection on continue action', () => {
    render(<BangQueryTab tabId="t1" />)
    expect(screen.getByText('orders-prod')).toBeInTheDocument()
    expect(screen.getByText('orders / public')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '用此数据源继续' }))
    expect(useConnectionStore.getState().activeConnectionId).toBe('c1')
  })
})
