import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ComponentProps } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AiAssistPanel, cleanupStageAiSessions } from './ai-assist-panel'
import { useSqlWorkbenchStore } from '../../stores/sql-workbench-store'
import * as sessionApi from '@/services/api/session'

const sendMessageMock = vi.fn()
const channelClientMock = {
  sendMessage: sendMessageMock,
  abort: vi.fn(),
  subscribe: vi.fn(),
  actionResult: vi.fn(),
}

vi.mock('@/services/channel/channel-client', () => ({
  ChannelClient: vi.fn().mockImplementation(() => channelClientMock),
}))

vi.mock('@/services/api/session', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/services/api/session')>()
  return {
    ...actual,
    createSession: vi.fn(),
    deleteSession: vi.fn(),
  }
})

function renderPanel(overrides: Partial<ComponentProps<typeof AiAssistPanel>> = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <AiAssistPanel
        tabId="tab-1"
        tabTitle="Orders SQL"
        connectionName="Orders DB"
        database="orders"
        schema="public"
        lastError="division by zero"
        lastRunSummary="last run failed in 42ms"
        {...overrides}
      />
    </QueryClientProvider>,
  )
}

describe('AiAssistPanel', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    await cleanupStageAiSessions(new Set())
    useSqlWorkbenchStore.setState({
      tabsById: {
        'tab-1': {
          sqlText: 'select * from users',
          source: 'user',
          executeStatus: 'idle',
          results: [],
          activeResultId: null,
          resolvedContext: null,
          contextNotice: null,
          risk: null,
          errorMessage: null,
          override: null,
          history: [],
          savedSqlText: 'select * from users',
          limit: 100,
          cursor: { line: 1, column: 1 },
        },
      },
    })
  })

  it('creates a tagged AI session once per tab, injects SQL context, and streams the assistant response', async () => {
    vi.mocked(sessionApi.createSession).mockResolvedValue({
      id: 'ai-tab-1',
      connectionId: null,
      title: '_stage-ai_Orders SQL',
      hasEverSent: false,
      createdAt: 1,
      updatedAt: 1,
      titleLocked: false,
      reusedEmpty: false,
    })

    sendMessageMock.mockImplementation(async (_parts: Array<{ text?: string }>, sink: (event: any) => void) => {
      sink({ id: 1, event: 'message.part.created', data: { part: { id: 'p1', type: 'text', text: 'Start', sessionID: 'ai-tab-1', messageID: 'm1' } } })
      sink({ id: 2, event: 'message.part.delta', data: { partId: 'p1', field: 'text', delta: ' and finish.' } })
    })

    renderPanel()

    fireEvent.click(screen.getByRole('button', { name: 'Explain' }))

    await waitFor(() => expect(sessionApi.createSession).toHaveBeenCalledWith(undefined, '_stage-ai_Orders SQL'))
    await waitFor(() => expect(sendMessageMock).toHaveBeenCalledTimes(1))
    const [parts] = sendMessageMock.mock.calls[0]
    expect(parts[0].text).toContain('Current SQL')
    expect(parts[0].text).toContain('select * from users')
    expect(parts[0].text).toContain('division by zero')
    expect(parts[0].text).toContain('last run failed in 42ms')
    expect(await screen.findByText(/Start and finish\./)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Optimize' }))
    await waitFor(() => expect(sendMessageMock).toHaveBeenCalledTimes(2))
    expect(sessionApi.createSession).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Fix error' })).toBeInTheDocument()
  })

  it('extracts fenced SQL blocks and offers insert / replace actions', async () => {
    vi.mocked(sessionApi.createSession).mockResolvedValue({
      id: 'ai-tab-1',
      connectionId: null,
      title: '_stage-ai_Orders SQL',
      hasEverSent: false,
      createdAt: 1,
      updatedAt: 1,
      titleLocked: false,
      reusedEmpty: false,
    })

    sendMessageMock.mockImplementation(async (_parts: Array<{ text?: string }>, sink: (event: any) => void) => {
      sink({
        id: 1,
        event: 'message.part.delta',
        data: {
          partId: 'p1',
          field: 'text',
          delta: 'Use this:\n```sql\nselect id, email from users;\n```',
        },
      })
    })

    renderPanel()

    fireEvent.click(screen.getByRole('button', { name: 'Explain' }))

    await waitFor(() => expect(screen.getByRole('button', { name: 'Insert at cursor' })).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Replace selection' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Insert at cursor' }))
    await waitFor(() => {
      expect(useSqlWorkbenchStore.getState().tabsById['tab-1']?.sqlText).toContain('select id, email from users;')
    })
  })

  it('hides the Fix error action when there is no last error', () => {
    renderPanel({ lastError: null })

    expect(screen.queryByRole('button', { name: 'Fix error' })).not.toBeInTheDocument()
  })

  it('can clean up removed tab sessions', async () => {
    vi.mocked(sessionApi.createSession).mockResolvedValue({
      id: 'ai-tab-1',
      connectionId: null,
      title: '_stage-ai_Orders SQL',
      hasEverSent: false,
      createdAt: 1,
      updatedAt: 1,
      titleLocked: false,
      reusedEmpty: false,
    })
    vi.mocked(sessionApi.deleteSession).mockResolvedValue(undefined)

    renderPanel()
    fireEvent.click(screen.getByRole('button', { name: 'Explain' }))

    await waitFor(() => expect(sessionApi.createSession).toHaveBeenCalled())

    await cleanupStageAiSessions(new Set())
    await waitFor(() => expect(sessionApi.deleteSession).toHaveBeenCalledWith('ai-tab-1'))
  })
})
