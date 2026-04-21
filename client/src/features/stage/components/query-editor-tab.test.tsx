import { useState } from 'react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryEditorTab } from './query-editor-tab'
import type { StageTab } from '@/stores/stage-store'
import * as useSqlExecuteModule from '../hooks/use-sql-execute'
import * as useChannelModule from '@/services/channel/use-channel'
import { normalizeQueryEditorPayload } from '../utils/normalize-query-editor-payload'

type MockResolvedSessionContext = {
  sessionId: string
  connectionId: string
  connectionNameSnapshot: string
  database: string
  schema: string
  selectedLevel: string
  updatedAt: number
}

type SqlExecuteMockState = {
  status: 'idle' | 'running' | 'success' | 'risk_blocked' | 'error'
  result: any
  risk: any
  errorMessage: string | null
}

const hoisted = vi.hoisted(() => {
  const editorViews: Array<{
    doc: string
    destroySpy: ReturnType<typeof vi.fn>
    listener: ((update: { docChanged: boolean; state: { doc: { toString: () => string } } }) => void) | null
  }> = []

  return {
    execute: vi.fn(),
    reset: vi.fn(),
    sendMessage: vi.fn(),
    updateTabPayload: vi.fn(),
    sqlExecuteState: {
      status: 'idle',
      result: null,
      risk: null,
      errorMessage: null,
    } as SqlExecuteMockState,
    sqlExecuteSeed: {
      status: 'idle',
      result: null,
      risk: null,
      errorMessage: null,
    } as SqlExecuteMockState,
    editorViews,
    resetEditors: () => {
      editorViews.length = 0
    },
    getLatestEditorDoc: () => editorViews.at(-1)?.doc ?? '',
    emitEditorChange: (nextDoc: string) => {
      const editor = editorViews.at(-1)
      if (!editor) throw new Error('No mock editor instance available')
      editor.doc = nextDoc
      editor.listener?.({
        docChanged: true,
        state: {
          doc: {
            toString: () => nextDoc,
          },
        },
      })
    },
    connectionState: {
      activeConnectionId: 'c-fallback' as string | null,
      connections: [
        { id: 'c-tab', name: 'Tab Conn' },
        { id: 'c-session', name: 'Session Conn' },
        { id: 'c-fallback', name: 'Fallback Conn' },
      ],
    },
    sessionDataContextValue: {
      context: {
        sessionId: 's-1',
        connectionId: 'c-session',
        connectionNameSnapshot: 'Session Conn',
        database: 'session-db',
        schema: 'session-schema',
        selectedLevel: 'schema',
        updatedAt: 0,
      } as MockResolvedSessionContext | null,
      isLoading: false,
      error: null,
      refresh: vi.fn(),
      resolveUseTarget: vi.fn(),
      setSessionDataContext: vi.fn(),
      validateSessionDataContext: vi.fn(),
    },
  }
})

vi.mock('@codemirror/lang-sql', () => ({ sql: () => [] }))
vi.mock('codemirror', () => {
  class MockEditorView {
    static updateListener = {
      of(listener: (update: { docChanged: boolean; state: { doc: { toString: () => string } } }) => void) {
        return { __type: 'updateListener', listener }
      },
    }

    private readonly record: {
      doc: string
      destroySpy: ReturnType<typeof vi.fn>
      listener: ((update: { docChanged: boolean; state: { doc: { toString: () => string } } }) => void) | null
    }

    constructor({ parent, doc, extensions }: any) {
      this.record = {
        doc: doc ?? '',
        destroySpy: vi.fn(),
        listener: Array.isArray(extensions)
          ? (extensions.find((extension: any) => extension?.__type === 'updateListener')?.listener ?? null)
          : null,
      }
      hoisted.editorViews.push(this.record)
      if (parent) parent.textContent = 'editor'
    }

    get state() {
      return {
        doc: {
          toString: () => this.record.doc,
        },
      }
    }

    destroy() {
      this.record.destroySpy()
    }
  }

  return {
    basicSetup: [],
    EditorView: MockEditorView,
  }
})
vi.mock('@codemirror/view', () => ({ keymap: { of: () => [] }, Prec: { high: (x: any) => x } }))
vi.mock('@codemirror/state', () => ({ Prec: { high: (x: any) => x } }))

vi.mock('../hooks/use-sql-execute')
vi.mock('@/services/channel/use-channel')
vi.mock('@/features/connection/store', () => ({
  useConnectionStore: (sel: any) => sel(hoisted.connectionState),
}))
vi.mock('@/features/session/hooks/use-session-data-context', () => ({
  useSessionDataContext: () => hoisted.sessionDataContextValue,
}))
vi.mock('@/stores/stage-store', async () => {
  const actual = await vi.importActual<typeof import('@/stores/stage-store')>('@/stores/stage-store')
  return {
    ...actual,
    useStageStore: Object.assign(vi.fn(), {
      getState: () => ({ updateTabPayload: hoisted.updateTabPayload }),
    }),
  }
})

const mockTab: StageTab = {
  tabId: 'qe-1',
  type: 'query_editor',
  title: 'SQL 编辑器',
  scope: 'session',
  originSessionId: 's-1',
  createdAt: 0,
  connectionId: 'c-tab',
  connectionName: 'Tab Conn',
  database: 'tab-db',
  payload: { sql: 'SELECT 1', source: 'user' },
}

describe('QueryEditorTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    hoisted.resetEditors()
    hoisted.connectionState.activeConnectionId = 'c-fallback'
    hoisted.connectionState.connections = [
      { id: 'c-tab', name: 'Tab Conn' },
      { id: 'c-session', name: 'Session Conn' },
      { id: 'c-fallback', name: 'Fallback Conn' },
    ]
    hoisted.sessionDataContextValue.context = {
      sessionId: 's-1',
      connectionId: 'c-session',
      connectionNameSnapshot: 'Session Conn',
      database: 'session-db',
      schema: 'session-schema',
      selectedLevel: 'schema',
      updatedAt: 0,
    }
    hoisted.sqlExecuteState = {
      status: 'idle',
      result: null,
      risk: null,
      errorMessage: null,
    }
    hoisted.sqlExecuteSeed = {
      status: 'idle',
      result: null,
      risk: null,
      errorMessage: null,
    }
    vi.mocked(useSqlExecuteModule.useSqlExecute).mockImplementation(() => ({
      execute: hoisted.execute.mockResolvedValue(undefined as any),
      result: hoisted.sqlExecuteState.result,
      risk: hoisted.sqlExecuteState.risk,
      status: hoisted.sqlExecuteState.status,
      errorMessage: hoisted.sqlExecuteState.errorMessage,
      reset: hoisted.reset,
    }))
    vi.mocked(useChannelModule.useChannel).mockReturnValue({
      sendMessage: hoisted.sendMessage,
      abort: vi.fn(),
      isStreaming: false,
      client: null as any,
      retryPendingUser: vi.fn(),
      removePendingUser: vi.fn(),
    })
  })

  afterEach(() => {
    cleanup()
  })

  it('renders editor area and Run button', () => {
    render(<QueryEditorTab tab={mockTab} />)
    expect(screen.getByRole('button', { name: /运行 SQL/i })).toBeTruthy()
    expect(screen.getAllByText(/Tab Conn/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/tab-db/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/session-schema/).length).toBeGreaterThan(0)
  })

  it('calls execute on Run click', () => {
    render(<QueryEditorTab tab={mockTab} />)
    fireEvent.click(screen.getByRole('button', { name: /运行 SQL/i }))
    expect(hoisted.execute).toHaveBeenCalledWith(
      'SELECT 1',
      'c-tab',
      'user',
      { sessionId: 's-1', database: 'tab-db', schema: 'session-schema' },
    )
  })

  it('shows risk warning when risk_blocked', () => {
    hoisted.sqlExecuteState = {
      status: 'risk_blocked',
      result: null,
      risk: { riskLevel: 'HIGH', riskReason: 'bulk_delete' },
      errorMessage: null,
    }
    render(<QueryEditorTab tab={mockTab} />)
    expect(screen.getByText(/高风险操作/i)).toBeTruthy()
    expect(screen.getByText(/bulk_delete/i)).toBeTruthy()
  })

  it('sends message to AI on risk warning click', () => {
    hoisted.sqlExecuteState = {
      status: 'risk_blocked',
      result: null,
      risk: { riskLevel: 'HIGH', riskReason: 'bulk_delete' },
      errorMessage: null,
    }
    render(<QueryEditorTab tab={mockTab} />)
    fireEvent.click(screen.getByText(/发给 AI 审查/i))
    expect(hoisted.sendMessage).toHaveBeenCalledWith(
      expect.arrayContaining([expect.objectContaining({ type: 'text' })]),
    )
  })

  it('shows the direct query mode badge and embedded inspector panel', () => {
    render(<QueryEditorTab tab={{
      ...mockTab,
      payload: {
        entryMode: 'direct_sql',
        initialSql: 'select 1',
        source: 'user',
      },
    }} />)

    expect(screen.getByText('直查模式', { selector: 'span' })).toBeTruthy()
    expect(screen.getByText(/检查器/i)).toBeTruthy()
    expect(screen.getByText(/连接/i)).toBeTruthy()
  })

  it('renders a no-connection empty state instead of the run button', () => {
    hoisted.connectionState.activeConnectionId = null
    hoisted.sessionDataContextValue.context = null

    render(<QueryEditorTab tab={{
      ...mockTab,
      connectionId: undefined,
      connectionName: undefined,
      database: undefined,
      payload: {
        entryMode: 'manual',
        initialSql: 'select 1',
        source: 'user',
      },
    }} />)

    expect(screen.getByText(/选择一个数据源后即可运行 SQL/i)).toBeTruthy()
    expect(screen.queryByRole('button', { name: /运行 SQL/i })).toBeNull()
  })

  it('triggers autoRun once when a connection is available', async () => {
    render(<QueryEditorTab tab={{
      ...mockTab,
      payload: {
        entryMode: 'direct_sql',
        initialSql: 'select 42',
        source: 'user',
        autoRun: true,
      },
    }} />)

    await waitFor(() => {
      expect(hoisted.execute).toHaveBeenCalledWith(
        'select 42',
        'c-tab',
        'user',
        { sessionId: 's-1', database: 'tab-db', schema: 'session-schema' },
      )
    })
    expect(hoisted.execute).toHaveBeenCalledTimes(1)
  })

  it('renders successful query results in the embedded result panel', () => {
    hoisted.sqlExecuteState = {
      status: 'success',
      result: {
        columns: ['id'],
        rows: [[1]],
        rowCount: 1,
        executionMs: 7,
        truncated: false,
      },
      risk: null,
      errorMessage: null,
    }

    render(<QueryEditorTab tab={mockTab} />)

    expect(screen.getByText(/结果/i)).toBeTruthy()
    expect(screen.getAllByText('1 行 · 7ms')).toHaveLength(2)
    expect(screen.getByText('id')).toBeTruthy()
    expect(screen.getByText('1')).toBeTruthy()
  })

  it('renders null cells with the i18n-backed label instead of the raw null literal', () => {
    hoisted.sqlExecuteState = {
      status: 'success',
      result: {
        columns: ['maybe_null'],
        rows: [[null]],
        rowCount: 1,
        executionMs: 7,
        truncated: false,
      },
      risk: null,
      errorMessage: null,
    }

    render(<QueryEditorTab tab={mockTab} />)

    expect(screen.getByText('NULL')).toBeTruthy()
    expect(screen.queryByText(/^null$/)).toBeNull()
  })

  it('normalizes malformed normalized-looking payloads back into store state on mount', () => {
    render(<QueryEditorTab tab={{
      ...mockTab,
      payload: {
        entryMode: 'direct_sql',
        initialSql: 'select 1',
        source: 'user',
        autoRun: false,
        initialResult: {
          columns: ['n'],
          rows: 'not-an-array',
          rowCount: 1,
          executionMs: 3,
          truncated: false,
        },
        lastRun: {
          columns: ['n'],
          rowCount: '1',
          executionMs: 3,
          truncated: false,
        },
        contextNotice: null,
      },
    }} />)

    expect(hoisted.updateTabPayload).toHaveBeenCalledTimes(1)
    expect(hoisted.updateTabPayload).toHaveBeenCalledWith('qe-1', expect.any(Function))
  })

  it('mirrors editor changes back into updateTabPayload with the updated SQL', () => {
    render(<QueryEditorTab tab={{
      ...mockTab,
      payload: normalizeQueryEditorPayload({
        entryMode: 'manual',
        initialSql: 'select 1',
        source: 'user',
      }),
    }} />)

    hoisted.emitEditorChange('select 2')

    expect(hoisted.updateTabPayload).toHaveBeenCalledTimes(1)
    const [, updater] = hoisted.updateTabPayload.mock.calls[0]
    expect(updater({
      sql: 'select 1',
      source: 'user',
    })).toMatchObject({
      entryMode: 'manual',
      initialSql: 'select 2',
      source: 'user',
      autoRun: false,
    })
  })

  it('reinitializes editor and clears query state when switching tabs', () => {
    hoisted.connectionState.connections = [
      { id: 'c-tab-a', name: 'Tab A Conn' },
      { id: 'c-tab-b', name: 'Tab B Conn' },
    ]
    hoisted.connectionState.activeConnectionId = null
    hoisted.sessionDataContextValue.context = null
    hoisted.sqlExecuteSeed = {
      status: 'success',
      result: {
        columns: ['id'],
        rows: [[1]],
        rowCount: 1,
        executionMs: 7,
        truncated: false,
        resolvedContext: {
          connectionId: 'c-tab-a',
          connectionName: 'Resolved Conn',
          database: 'resolved-db',
          schema: 'resolved-schema',
        },
      },
      risk: null,
      errorMessage: null,
    }
    vi.mocked(useSqlExecuteModule.useSqlExecute).mockImplementation(() => {
      const [state] = useState(hoisted.sqlExecuteSeed)
      return {
        execute: hoisted.execute.mockResolvedValue(undefined as any),
        result: state.result,
        risk: state.risk,
        status: state.status,
        errorMessage: state.errorMessage,
        reset: hoisted.reset,
      }
    })

    const tabA: StageTab = {
      ...mockTab,
      tabId: 'qe-a',
      originSessionId: undefined,
      connectionId: 'c-tab-a',
      connectionName: 'Tab A Conn',
      database: 'db-a',
      schema: 'schema-a',
      payload: normalizeQueryEditorPayload({
        entryMode: 'manual',
        initialSql: 'select * from a',
        source: 'user',
      }),
    }
    const tabB: StageTab = {
      ...mockTab,
      tabId: 'qe-b',
      originSessionId: undefined,
      connectionId: 'c-tab-b',
      connectionName: 'Tab B Conn',
      database: 'db-b',
      schema: 'schema-b',
      payload: normalizeQueryEditorPayload({
        entryMode: 'manual',
        initialSql: 'select * from b',
        source: 'user',
      }),
    }

    const { rerender } = render(<QueryEditorTab tab={tabA} />)
    expect(screen.getAllByText('1 行 · 7ms')).toHaveLength(2)
    expect(screen.getAllByText('Resolved Conn')).toHaveLength(2)
    expect(screen.getByText('resolved-db / resolved-schema')).toBeTruthy()

    hoisted.emitEditorChange('select * from tab_a_edited')
    hoisted.sqlExecuteSeed = {
      status: 'idle',
      result: null,
      risk: null,
      errorMessage: null,
    }

    rerender(<QueryEditorTab tab={tabB} />)

    expect(hoisted.getLatestEditorDoc()).toBe('select * from b')
    expect(screen.queryAllByText('Resolved Conn')).toHaveLength(0)
    expect(screen.queryByText('resolved-db / resolved-schema')).toBeNull()
    expect(screen.queryAllByText('1 行 · 7ms')).toHaveLength(0)
    expect(screen.getAllByText('Tab B Conn').length).toBeGreaterThan(0)
  })
})
