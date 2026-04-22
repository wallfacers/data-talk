import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { StageActivityRail } from './stage-activity-rail'
import { useStageStore } from '@/stores/stage-store'
import { useSqlWorkbenchStore } from '../../stores/sql-workbench-store'

const sessionTab = {
  tabId: 'session-q1',
  type: 'query_editor',
  title: 'Session SQL',
  scope: 'session' as const,
  originSessionId: 's-1',
  createdAt: 0,
  payload: {
    initialSql: 'select session',
    source: 'user' as const,
    connectionId: 'conn-session',
    connectionName: 'Session Connection',
    database: 'session_db',
    schema: 'session_schema',
  },
  connectionId: 'conn-session',
  connectionName: 'Session Connection',
  database: 'session_db',
  schema: 'session_schema',
}

const workspaceTab = {
  tabId: 'workspace-q1',
  type: 'query_editor',
  title: 'Workspace SQL',
  scope: 'workspace' as const,
  createdAt: 0,
  payload: {
    initialSql: 'select workspace',
    source: 'user' as const,
    connectionId: 'conn-workspace',
    connectionName: 'Workspace Connection',
    database: 'workspace_db',
    schema: 'workspace_schema',
  },
  connectionId: 'conn-workspace',
  connectionName: 'Workspace Connection',
  database: 'workspace_db',
  schema: 'workspace_schema',
}

describe('StageActivityRail', () => {
  beforeEach(() => {
    useStageStore.setState({
      openBySession: new Map(),
      autoOpenedSessions: new Set(),
      maximizedBySession: new Map(),
      revealOrigin: null,
      sidebarCollapsedBySession: new Map(),
      sidebarSelectionBySession: new Map(),
      resourceTreeExpandedBySession: new Map(),
      activeRailPanelBySession: new Map(),
      workspaceTabs: [workspaceTab],
      tabsBySession: new Map([['s-1', [sessionTab]]]),
      activeWorkspaceTabId: 'workspace-q1',
      activeTabIdBySession: new Map([['s-1', 'session-q1']]),
    })
    useSqlWorkbenchStore.setState({ tabsById: {} })
    useSqlWorkbenchStore.getState().ensureTab('session-q1', {
      sqlText: 'select session',
      source: 'user',
    })
    useSqlWorkbenchStore.getState().ensureTab('workspace-q1', {
      sqlText: 'select workspace',
      source: 'user',
    })
    useSqlWorkbenchStore.getState().appendHistoryEntry('workspace-q1', {
      id: 'h-1',
      at: 1,
      sql: 'select 1',
      status: 'ok',
    })
  })

  it('renders real panel content and preserves toggle behavior', () => {
    render(<StageActivityRail sessionId="s-1" />)

    fireEvent.click(screen.getByRole('button', { name: 'Schema' }))
    expect(screen.getByTestId('schema-panel')).toBeTruthy()
    expect(screen.getByText('Session Connection')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Schema' }))
    expect(screen.queryByTestId('schema-panel')).toBeNull()
  })

  it('falls back to the workspace active query editor tab for history actions', async () => {
    useStageStore.setState({
      activeTabIdBySession: new Map([['s-1', null]]),
      activeWorkspaceTabId: 'workspace-q1',
    })

    render(<StageActivityRail sessionId="s-1" />)

    fireEvent.click(screen.getByRole('button', { name: 'History' }))
    fireEvent.click(await screen.findByRole('button', { name: /select 1/i }))

    await waitFor(() => {
      expect(useSqlWorkbenchStore.getState().tabsById['workspace-q1']?.sqlText).toBe('select workspace\nselect 1')
    })
  })
})
