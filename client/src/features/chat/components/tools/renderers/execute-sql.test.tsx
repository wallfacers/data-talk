import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { translateMessage } from '@/i18n/messages'
import { ExecuteSql } from './execute-sql'
import type { ActionDescriptor } from '@/features/actions/registry'
import type { ToolPart } from '@/services/channel/types'

const mockOpenQueryEditor = vi.fn()
const mockOpenStage = vi.fn()

const t = (key: Parameters<typeof translateMessage>[1], values?: Record<string, string | number>) =>
  translateMessage('en-US', key, values)

vi.mock('@/stores/ui-settings-store', () => ({
  getCurrentLanguage: () => 'en-US',
}))

vi.mock('@/i18n/messages', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/i18n/messages')>()
  return {
    translateMessage: (_lang: string, key: string, values?: Record<string, unknown>) =>
      actual.translateMessage('en-US', key as never, values as Record<string, string | number>),
  }
})

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    language: 'en-US',
    setLanguage: vi.fn(),
    t: (key: string, values?: Record<string, string | number>) => t(key as never, values),
  }),
}))

vi.mock('@/stores/stage-store', () => ({
  useStageStore: {
    getState: () => ({
      openQueryEditor: mockOpenQueryEditor,
      openStage: mockOpenStage,
    }),
  },
}))

vi.mock('@/stores/session-store', () => ({
  useSessionStore: {
    getState: () => ({
      dataContextBySession: new Map([
        ['sess-1', {
          sessionId: 'sess-1',
          connectionId: 'conn-from-session',
          connectionNameSnapshot: 'Session DB',
          database: 'app',
          schema: 'public',
        }],
      ]),
    }),
  },
}))

vi.mock('@/features/connection/store', () => ({
  useConnectionStore: {
    getState: () => ({
      connections: [
        { id: 'conn-from-session', name: 'Session DB' },
        { id: 'conn-from-input', name: 'Input DB' },
      ],
    }),
  },
}))

const descriptor: ActionDescriptor = {
  id: 'datatalk_execute_sql',
  executor: 'CLIENT',
  description: 'Execute SQL',
  inputSchema: {},
  outputSchema: {},
  produces: [],
  sideEffects: [],
  requiresConnection: true,
  timeoutMs: 30000,
  category: 'query',
}

function buildPart(output: Record<string, unknown>, extra?: Partial<ToolPart>): ToolPart {
  return {
    id: 'part-1',
    type: 'tool',
    sessionID: 'sess-1',
    messageID: 'msg-1',
    tool: 'datatalk_execute_sql',
    state: {
      status: 'completed',
      input: { sql: 'DELETE FROM users WHERE id = 1' },
      output,
      metadata: {},
    },
    callID: 'call-abc',
    ...extra,
  }
}

describe('ExecuteSql renderer', () => {
  beforeEach(() => {
    mockOpenQueryEditor.mockClear()
    mockOpenStage.mockClear()
  })

  it('renders blocked_in_chat card with affected objects, SQL preview and CTA', () => {
    const part = buildPart({
      status: 'blocked_in_chat',
      risk: { level: 'L2', reason: 'INSERT statement', affectedObjects: ['public.users'] },
      sqlPreview: "INSERT INTO users (name) VALUES ('test')",
    })

    render(<ExecuteSql part={part} descriptor={descriptor} />)

    expect(screen.getByText(/INSERT INTO users/)).toBeTruthy()
    expect(screen.getByText('public.users')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Open in SQL Workbench' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Execute' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Cancel' })).toBeNull()
  })

  it('clicking Open in SQL Workbench opens an ai_open query editor with sqlPreview pre-loaded', () => {
    const part = buildPart({
      status: 'blocked_in_chat',
      risk: { level: 'L3', reason: 'DROP TABLE', affectedObjects: ['public.users'] },
      sqlPreview: 'DROP TABLE users',
    })

    render(<ExecuteSql part={part} descriptor={descriptor} />)
    fireEvent.click(screen.getByRole('button', { name: 'Open in SQL Workbench' }))

    expect(mockOpenQueryEditor).toHaveBeenCalledTimes(1)
    expect(mockOpenQueryEditor).toHaveBeenCalledWith(expect.objectContaining({
      sessionId: 'sess-1',
      scope: 'session',
      openMode: 'always_new',
      entryMode: 'ai_open',
      initialContent: 'DROP TABLE users',
      autoRun: false,
      connectionId: 'conn-from-session',
      database: 'app',
      schema: 'public',
    }))
    expect(mockOpenStage).toHaveBeenCalledWith('sess-1')
  })

  it('prefers connectionId from tool input over session context', () => {
    const part = buildPart({
      status: 'blocked_in_chat',
      risk: { level: 'L2', reason: 'UPDATE', affectedObjects: ['orders'] },
      sqlPreview: 'UPDATE orders SET status = 1 WHERE id = 1',
    }, {
      state: {
        status: 'completed',
        input: { sql: 'UPDATE orders SET status = 1 WHERE id = 1', connectionId: 'conn-from-input' },
        output: {
          status: 'blocked_in_chat',
          risk: { level: 'L2', reason: 'UPDATE', affectedObjects: ['orders'] },
          sqlPreview: 'UPDATE orders SET status = 1 WHERE id = 1',
        },
        metadata: {},
      },
    })

    render(<ExecuteSql part={part} descriptor={descriptor} />)
    fireEvent.click(screen.getByRole('button', { name: 'Open in SQL Workbench' }))

    expect(mockOpenQueryEditor).toHaveBeenCalledWith(expect.objectContaining({
      connectionId: 'conn-from-input',
      connectionName: 'Input DB',
    }))
  })

  it('renders the legacy artifact view for executed L1 output', () => {
    const part = buildPart({
      rowCount: 42,
      rows: [{ id: 1, name: 'Alice' }],
      columns: ['id', 'name'],
    })

    render(<ExecuteSql part={part} descriptor={descriptor} />)

    expect(screen.getByText('42 rows affected')).toBeTruthy()
  })
})
