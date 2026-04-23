import { beforeEach, describe, expect, it, vi } from 'vitest'
import { openDirectSqlQueryEditorTab } from '../open-direct-sql-query-editor-tab'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'

describe('openDirectSqlQueryEditorTab', () => {
  const openQueryEditorMock = vi.fn()
  const openStageMock = vi.fn()

  beforeEach(() => {
    openQueryEditorMock.mockReset()
    openStageMock.mockReset()
    useConnectionStore.setState({
      activeConnectionId: null,
      connections: [
        { id: 'conn-1', name: 'orders-prod' },
      ] as any,
    })
    useSessionStore.setState({
      activeSessionId: 'sess-1',
      dataContextBySession: new Map([['sess-1', {
        sessionId: 'sess-1',
        connectionId: 'conn-1',
        connectionNameSnapshot: 'orders-prod',
        database: 'session-db',
        schema: 'session-schema',
        selectedLevel: 'schema',
        updatedAt: 1,
      }]]),
    } as any)
    useStageStore.setState({
      openBySession: new Map(),
      autoOpenedSessions: new Set(),
      maximizedBySession: new Map(),
      revealOrigin: null,
      sidebarCollapsedBySession: new Map(),
      sidebarSelectionBySession: new Map(),
      resourceTreeExpandedBySession: new Map(),
      workspaceTabs: [],
      tabsBySession: new Map(),
      activeWorkspaceTabId: null,
      activeTabIdBySession: new Map(),
      openQueryEditor: openQueryEditorMock,
      openStage: openStageMock,
    } as any)
  })

  it('delegates direct SQL opening to openQueryEditor and opens the stage for the session', async () => {
    const baseTitle = translateMessage(getCurrentLanguage(), 'stage.toolRow.sql')
    openQueryEditorMock.mockReturnValue({ tabId: 'query-editor-1', created: true })

    const tabId = await openDirectSqlQueryEditorTab({
      sessionId: 'sess-1',
      connectionId: 'conn-1',
      sql: 'SELECT id, name FROM users',
    })

    expect(tabId).toBe('query-editor-1')
    expect(openQueryEditorMock).toHaveBeenCalledWith({
      sessionId: 'sess-1',
      scope: 'session',
      baseTitle,
      openMode: 'always_new',
      entryMode: 'direct_sql',
      initialContent: 'SELECT id, name FROM users',
      autoRun: true,
      connectionId: 'conn-1',
      connectionName: 'orders-prod',
      database: 'session-db',
      schema: 'session-schema',
    })
    expect(openStageMock).toHaveBeenCalledWith('sess-1')
  })

  it('delegates session context and explicit autoRun overrides into openQueryEditor', async () => {
    const baseTitle = translateMessage(getCurrentLanguage(), 'stage.toolRow.sql')
    useConnectionStore.setState({ connections: [] as any })
    openQueryEditorMock.mockReturnValue({ tabId: 'query-editor-2', created: true })

    const tabId = await openDirectSqlQueryEditorTab({
      sessionId: 'sess-1',
      connectionId: 'conn-1',
      sql: 'WITH cte AS (SELECT 1) SELECT * FROM cte',
      autoRun: false,
    })

    expect(tabId).toBe('query-editor-2')
    expect(openQueryEditorMock).toHaveBeenCalledWith({
      sessionId: 'sess-1',
      scope: 'session',
      baseTitle,
      openMode: 'always_new',
      entryMode: 'direct_sql',
      initialContent: 'WITH cte AS (SELECT 1) SELECT * FROM cte',
      autoRun: false,
      connectionId: 'conn-1',
      connectionName: 'orders-prod',
      database: 'session-db',
      schema: 'session-schema',
    })
  })

  it('throws when no connectionId is provided', async () => {
    await expect(openDirectSqlQueryEditorTab({
      sessionId: 'sess-1',
      connectionId: null,
      sql: 'SELECT 1',
    })).rejects.toThrow()
  })
})
