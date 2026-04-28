import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { translateMessage } from '@/i18n/messages'
import { StageActivityRail } from './stage-activity-rail'
import { useStageStore } from '@/stores/stage-store'
import { useSqlWorkbenchStore } from '../../stores/sql-workbench-store'

const sessionTab = {
  tabId: 'session-q1',
  type: 'query_editor',
  title: 'Session SQL',
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
  const t = (key: Parameters<typeof translateMessage>[1], values?: Record<string, string | number>) =>
    translateMessage('zh-CN', key, values)

  beforeEach(() => {
    useStageStore.setState({
      open: false,
      autoOpened: false,
      maximized: false,
      revealOrigin: null,
      sidebarCollapsed: false,
      sidebarSelection: null,
      resourceTreeExpanded: [],
      activeRailPanel: null,
      tabs: [workspaceTab, sessionTab],
      openTabIds: new Set(['workspace-q1', 'session-q1']),
      openTabIdsOrdered: ['workspace-q1', 'session-q1'],
      activeTabId: 'session-q1',
    } as never)
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
    render(<StageActivityRail />)

    fireEvent.click(screen.getByRole('button', { name: t('stage.activityRail.schema.title') }))
    expect(screen.getByTestId('schema-panel')).toBeTruthy()
    expect(screen.getByText('Session Connection')).toBeTruthy()
    expect(screen.getByRole('button', { name: t('stage.activityRail.closePanel') })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: t('stage.activityRail.schema.title') }))
    expect(screen.queryByTestId('schema-panel')).toBeNull()
  })

  it('falls back to the workspace active query editor tab for history actions', async () => {
    useStageStore.setState({
      activeTabId: 'workspace-q1',
    } as never)

    render(<StageActivityRail />)

    fireEvent.click(screen.getByRole('button', { name: t('stage.activityRail.history.title') }))
    fireEvent.click(await screen.findByRole('button', { name: /select 1/i }))

    await waitFor(() => {
      expect(useSqlWorkbenchStore.getState().tabsById['workspace-q1']?.sqlText).toBe('select workspace\nselect 1')
    })
  })
})
