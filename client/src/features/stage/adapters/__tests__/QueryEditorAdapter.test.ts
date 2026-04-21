import { beforeEach, describe, expect, it } from 'vitest'
import { useStageStore, type StageTab } from '@/stores/stage-store'
import { QueryEditorAdapter } from '../QueryEditorAdapter'

function resetStageStore() {
  useStageStore.setState({
    workspaceTabs: [],
    tabsBySession: new Map(),
    activeWorkspaceTabId: null,
    activeTabIdBySession: new Map(),
  } as unknown as Record<string, unknown>)
}

function openTab(tab: StageTab) {
  useStageStore.getState().openTab(tab)
}

describe('QueryEditorAdapter', () => {
  beforeEach(() => {
    resetStageStore()
  })

  it('read state consumes normalized payload fields before tab-root fallbacks', () => {
    openTab({
      tabId: 'q1',
      type: 'query_editor',
      title: 'SQL',
      scope: 'session',
      originSessionId: 's1',
      connectionId: 'tab-conn',
      connectionName: 'Tab Warehouse',
      database: 'tab-db',
      schema: 'public',
      payload: {
        initialSql: 'select 42',
        source: 'ai',
        initialResult: {
          columns: ['n'],
          rows: [[42]],
          rowCount: 1,
          executionMs: 5,
          truncated: false,
        },
        connectionId: 'payload-conn',
        connectionName: 'Payload Warehouse',
        database: 'payload-db',
        contextNotice: 'Using reporting schema',
      },
      createdAt: 0,
    })

    const adapter = new QueryEditorAdapter('q1')

    expect(adapter.read('state')).toEqual({
      sql: 'select 42',
      source: 'ai',
      entryMode: 'ai_generated',
      connectionId: 'payload-conn',
      connectionName: 'Payload Warehouse',
      database: 'payload-db',
      schema: 'public',
      lastRun: {
        columns: ['n'],
        rowCount: 1,
        executionMs: 5,
        truncated: false,
      },
      contextNotice: 'Using reporting schema',
    })
  })

  it('exposes normalized payload context through adapter getters used by ui_list', () => {
    openTab({
      tabId: 'q2',
      type: 'query_editor',
      title: 'Normalized SQL',
      scope: 'session',
      originSessionId: 's1',
      payload: {
        initialSql: 'select 2',
        source: 'ai',
        connectionId: 'payload-conn',
        database: 'payload-db',
      },
      createdAt: 0,
    })

    const adapter = new QueryEditorAdapter('q2')

    expect(adapter.connectionId).toBe('payload-conn')
    expect(adapter.database).toBe('payload-db')
  })

  it('focus clears the current session-active tab when focusing a workspace query editor', async () => {
    openTab({
      tabId: 'ws-q1',
      type: 'query_editor',
      title: 'Workspace SQL',
      scope: 'workspace',
      payload: {},
      createdAt: 0,
    })
    openTab({
      tabId: 'session-q1',
      type: 'query_editor',
      title: 'Session SQL',
      scope: 'session',
      originSessionId: 's1',
      payload: {},
      createdAt: 0,
    })

    useStageStore.setState({
      activeWorkspaceTabId: null,
      activeTabIdBySession: new Map([['s1', 'session-q1']]),
    } as unknown as Record<string, unknown>)

    const adapter = new QueryEditorAdapter('ws-q1', () => 's1')
    const result = await adapter.exec('focus')

    expect(result).toEqual({ success: true })
    expect(useStageStore.getState().activeWorkspaceTabId).toBe('ws-q1')
    expect(useStageStore.getState().activeTabIdBySession.get('s1')).toBeNull()
  })

  it('focus delegates to the stage store', async () => {
    openTab({
      tabId: 'q1',
      type: 'query_editor',
      title: 'SQL',
      scope: 'session',
      originSessionId: 's1',
      payload: {},
      createdAt: 0,
    })

    useStageStore.setState({
      activeTabIdBySession: new Map([['s1', null]]),
    } as unknown as Record<string, unknown>)

    const adapter = new QueryEditorAdapter('q1')
    const result = await adapter.exec('focus')

    expect(result).toEqual({ success: true })
    expect(useStageStore.getState().activeTabIdBySession.get('s1')).toBe('q1')
  })

  it('close removes the query editor tab', async () => {
    openTab({
      tabId: 'q1',
      type: 'query_editor',
      title: 'SQL',
      scope: 'session',
      originSessionId: 's1',
      payload: {},
      createdAt: 0,
    })

    const adapter = new QueryEditorAdapter('q1')
    const result = await adapter.exec('close')

    expect(result).toEqual({ success: true })
    expect(useStageStore.getState().tabsBySession.get('s1')).toEqual([])
  })

  it('rejects unknown actions', async () => {
    openTab({
      tabId: 'q1',
      type: 'query_editor',
      title: 'SQL',
      scope: 'session',
      originSessionId: 's1',
      payload: {},
      createdAt: 0,
    })

    const adapter = new QueryEditorAdapter('q1')
    const result = await adapter.exec('rerun')

    expect(result.success).toBe(false)
    expect(result.error).toContain('Unknown action')
  })

  it('describes connectionName in schema and stays read-only for patch', () => {
    openTab({
      tabId: 'q3',
      type: 'query_editor',
      title: 'SQL',
      scope: 'session',
      originSessionId: 's1',
      payload: {
        initialSql: 'select 3',
        connectionName: 'Warehouse',
      },
      createdAt: 0,
    })

    const adapter = new QueryEditorAdapter('q3')

    expect(adapter.read('schema')).toEqual(expect.objectContaining({
      properties: expect.objectContaining({
        connectionName: { type: ['string', 'null'] },
      }),
    }))
    expect(adapter.patch([])).toEqual({
      status: 'error',
      message: 'query_editor is read-only; edit through the UI',
    })
  })
})
