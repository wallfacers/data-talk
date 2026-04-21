import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { QueryEditorTab } from './query-editor-tab'
import type { StageTab } from '@/stores/stage-store'
import * as useSqlExecuteModule from '../hooks/use-sql-execute'
import * as useChannelModule from '@/services/channel/use-channel'

vi.mock('@codemirror/lang-sql', () => ({ sql: () => [] }))
vi.mock('codemirror', () => ({
  basicSetup: [],
  EditorView: class {
    constructor({ parent }: any) { if (parent) parent.textContent = 'editor' }
    get state() { return { doc: { toString: () => 'SELECT 1' } } }
    destroy() {}
  },
}))
vi.mock('@codemirror/view', () => ({ keymap: { of: () => [] }, Prec: { high: (x: any) => x } }))
vi.mock('@codemirror/state', () => ({ Prec: { high: (x: any) => x } }))

vi.mock('../hooks/use-sql-execute')
vi.mock('@/services/channel/use-channel')
vi.mock('@/features/connection/store', () => ({
  useConnectionStore: (sel: any) => sel({
    activeConnectionId: 'c-fallback',
    connections: [
      { id: 'c-tab', name: 'Tab Conn' },
      { id: 'c-session', name: 'Session Conn' },
      { id: 'c-fallback', name: 'Fallback Conn' },
    ],
  }),
}))
vi.mock('@/features/session/hooks/use-session-data-context', () => ({
  useSessionDataContext: () => ({
    context: {
      sessionId: 's-1',
      connectionId: 'c-session',
      connectionNameSnapshot: 'Session Conn',
      database: 'session-db',
      schema: 'session-schema',
      selectedLevel: 'schema',
      updatedAt: 0,
    },
    isLoading: false,
    error: null,
    refresh: vi.fn(),
    resolveUseTarget: vi.fn(),
    setSessionDataContext: vi.fn(),
    validateSessionDataContext: vi.fn(),
  }),
}))

const mockTab: StageTab = {
  tabId: 'qe-1', type: 'query_editor', title: 'SQL 编辑器',
  scope: 'session', originSessionId: 's-1', createdAt: 0,
  connectionId: 'c-tab',
  connectionName: 'Tab Conn',
  database: 'tab-db',
  payload: { sql: 'SELECT 1', source: 'user' },
}

describe('QueryEditorTab', () => {
  const mockExecute = vi.fn()
  const mockReset = vi.fn()
  const mockSendMessage = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useSqlExecuteModule.useSqlExecute).mockReturnValue({
      execute: mockExecute.mockResolvedValue(undefined as any), result: null, risk: null,
      status: 'idle', errorMessage: null, reset: mockReset,
    })
    vi.mocked(useChannelModule.useChannel).mockReturnValue({
      sendMessage: mockSendMessage, abort: vi.fn(), isStreaming: false,
      client: null as any, retryPendingUser: vi.fn(), removePendingUser: vi.fn(),
    })
  })

  it('renders editor area and Run button', () => {
    render(<QueryEditorTab tab={mockTab} />)
    expect(screen.getByRole('button', { name: /Ctrl\+Enter 运行/i })).toBeTruthy()
    expect(screen.getByText(/Tab Conn/)).toBeTruthy()
    expect(screen.getByText(/tab-db/)).toBeTruthy()
    expect(screen.getByText(/session-schema/)).toBeTruthy()
  })

  it('calls execute on Run click', () => {
    render(<QueryEditorTab tab={mockTab} />)
    fireEvent.click(screen.getByRole('button', { name: /Ctrl\+Enter 运行/i }))
    expect(mockExecute).toHaveBeenCalledWith(
      'SELECT 1',
      'c-tab',
      'user',
      { sessionId: 's-1', database: 'tab-db', schema: 'session-schema' },
    )
  })

  it('shows risk warning when risk_blocked', () => {
    vi.mocked(useSqlExecuteModule.useSqlExecute).mockReturnValue({
      execute: mockExecute.mockResolvedValue(undefined as any), result: null,
      risk: { riskLevel: 'HIGH', riskReason: 'bulk_delete' },
      status: 'risk_blocked', errorMessage: null, reset: mockReset,
    })
    render(<QueryEditorTab tab={mockTab} />)
    expect(screen.getByText(/高风险操作/i)).toBeTruthy()
    expect(screen.getByText(/bulk_delete/i)).toBeTruthy()
  })

  it('sends message to AI on risk warning click', () => {
    vi.mocked(useSqlExecuteModule.useSqlExecute).mockReturnValue({
      execute: mockExecute.mockResolvedValue(undefined as any), result: null,
      risk: { riskLevel: 'HIGH', riskReason: 'bulk_delete' },
      status: 'risk_blocked', errorMessage: null, reset: mockReset,
    })
    render(<QueryEditorTab tab={mockTab} />)
    fireEvent.click(screen.getByText(/发给 AI 审查/i))
    expect(mockSendMessage).toHaveBeenCalledWith(
      expect.arrayContaining([expect.objectContaining({ type: 'text' })])
    )
  })
})
