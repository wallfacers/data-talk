import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SessionTurn } from '../session-turn'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { useUISettingsStore } from '@/stores/ui-settings-store'
import * as openDirectSqlQueryEditorTabApi from '@/features/stage/utils/open-direct-sql-query-editor-tab'

vi.mock('@/features/stage/utils/open-direct-sql-query-editor-tab', () => ({
  openDirectSqlQueryEditorTab: vi.fn(),
}))

function renderTurn(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const view = render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
  return {
    ...view,
    rerenderWithClient(nextUi: React.ReactElement) {
      view.rerender(<QueryClientProvider client={qc}>{nextUi}</QueryClientProvider>)
    },
  }
}

function estimateTurnHeight(turn: HTMLElement | null): number {
  if (!turn) return 0

  let height = 16

  if (turn.querySelector('.bg-primary')) {
    height += 56
  }

  const textParts = Array.from(turn.querySelectorAll('[data-component="text-part"]'))
  for (const textPart of textParts) {
    height += 48
    if (textPart.querySelector('.mt-1.flex.items-center.gap-2.text-xs.text-muted-foreground')) {
      height += 16
    }
  }

  if (turn.querySelector('[aria-label="思考中…"]')) {
    height += 36
  }

  if (turn.querySelector('.min-h-9') && textParts.length === 0 && !turn.querySelector('[aria-label="思考中…"]')) {
    height += 36
  }

  return height
}

describe('SessionTurn · showThinking', () => {
  beforeEach(() => {
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
    })
    useConnectionStore.setState({ activeConnectionId: null, connections: [] })
    useSessionStore.setState({
      activeSessionId: 's1',
      dataContextBySession: new Map(),
    } as any)
    useUISettingsStore.setState({
      splitResizable: false,
      language: 'zh-CN',
      autoExpandReasoning: false,
    } as any)
    ;(openDirectSqlQueryEditorTabApi.openDirectSqlQueryEditorTab as unknown as Mock).mockReset()
  })

  it('shows "思考中…" when streaming and no assistant message has arrived yet', () => {
    useChatPartsStore.getState().setStreaming('s1', true)
    renderTurn(
      <SessionTurn
        sessionId="s1"
        userMessageId="u1"
        assistantMessageIds={[]}
        userInfo={{ id: 'u1', role: 'user', sessionID: 's1', time: { created: 1 } }}
        isLastTurn
      />,
    )
    expect(screen.getByLabelText('思考中…')).toBeInTheDocument()
  })

  it('keeps the placeholder chevron collapsed when auto expand reasoning is disabled', () => {
    useChatPartsStore.getState().setStreaming('s1', true)

    renderTurn(
      <SessionTurn
        sessionId="s1"
        userMessageId="u1"
        assistantMessageIds={[]}
        userInfo={{ id: 'u1', role: 'user', sessionID: 's1', time: { created: 1 } }}
        isLastTurn
      />,
    )

    const chevron = screen.getByLabelText('思考中…').previousElementSibling
    expect(chevron).not.toBeNull()
    expect(chevron?.classList.contains('rotate-90')).toBe(false)
  })

  it('rotates the placeholder chevron when auto expand reasoning is enabled', () => {
    useUISettingsStore.setState({ autoExpandReasoning: true } as any)
    useChatPartsStore.getState().setStreaming('s1', true)

    renderTurn(
      <SessionTurn
        sessionId="s1"
        userMessageId="u1"
        assistantMessageIds={[]}
        userInfo={{ id: 'u1', role: 'user', sessionID: 's1', time: { created: 1 } }}
        isLastTurn
      />,
    )

    const chevron = screen.getByLabelText('思考中…').previousElementSibling
    expect(chevron).not.toBeNull()
    expect(chevron?.classList.contains('rotate-90')).toBe(true)
  })

  it('does not show "思考中…" when not streaming and no assistant message', () => {
    renderTurn(
      <SessionTurn
        sessionId="s1"
        userMessageId="u1"
        assistantMessageIds={[]}
        userInfo={{ id: 'u1', role: 'user', sessionID: 's1', time: { created: 1 } }}
        isLastTurn
      />,
    )
    expect(screen.queryByLabelText('思考中…')).toBeNull()
  })

  it('hides "思考中…" once a visible text part arrives', () => {
    useChatPartsStore.getState().setStreaming('s1', true)
    useChatPartsStore.getState().upsertInfo('s1', {
      id: 'a1', role: 'assistant', sessionID: 's1', time: { created: 2 },
    })
    useChatPartsStore.getState().upsertPart('s1', {
      type: 'text', id: 'prt1', sessionID: 's1', messageID: 'a1', text: 'hi', metadata: {},
    } as any)
    renderTurn(
      <SessionTurn
        sessionId="s1"
        userMessageId="u1"
        assistantMessageIds={['a1']}
        userInfo={{ id: 'u1', role: 'user', sessionID: 's1', time: { created: 1 } }}
        isLastTurn
      />,
    )
    expect(screen.queryByLabelText('思考中…')).toBeNull()
  })

  it('does not show "思考中…" when the assistant message has an error', () => {
    useChatPartsStore.getState().setStreaming('s1', true)
    useChatPartsStore.getState().upsertInfo('s1', {
      id: 'a1', role: 'assistant', sessionID: 's1', time: { created: 2 },
      error: { name: 'ProviderAuthError', data: { message: 'Invalid token' } },
    })
    renderTurn(
      <SessionTurn
        sessionId="s1"
        userMessageId="u1"
        assistantMessageIds={['a1']}
        userInfo={{ id: 'u1', role: 'user', sessionID: 's1', time: { created: 1 } }}
        isLastTurn
      />,
    )
    expect(screen.queryByLabelText('思考中…')).toBeNull()
    expect(screen.getByText(/Invalid token/)).toBeInTheDocument()
  })

  it('renders bang-query user messages with a prefix marker and rerun action', () => {
    useChatPartsStore.getState().upsertInfo('s1', {
      id: 'u1',
      role: 'user',
      sessionID: 's1',
      time: { created: 1 },
    })
    useChatPartsStore.getState().upsertPart('s1', {
      type: 'text',
      id: 'p1',
      sessionID: 's1',
      messageID: 'u1',
      text: '!select 1',
      metadata: { displayKind: 'bang_query_user', queryMode: 'direct_sql' },
    } as any)

    renderTurn(
      <SessionTurn
        sessionId="s1"
        userMessageId="u1"
        assistantMessageIds={[]}
        userInfo={{ id: 'u1', role: 'user', sessionID: 's1', time: { created: 1 } }}
        isLastTurn
      />,
    )

    expect(screen.getByLabelText('SQL 直查消息')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '重跑' })).toBeInTheDocument()
    expect(screen.queryByText('SQL 直查')).toBeNull()
    expect(screen.getByText('!select 1')).toBeInTheDocument()
  })

  it('re-runs bang-query SQL from the bubble action button', async () => {
    const openDirectSqlQueryEditorTabMock = openDirectSqlQueryEditorTabApi.openDirectSqlQueryEditorTab as unknown as Mock
    openDirectSqlQueryEditorTabMock.mockResolvedValue('tab-rerun')
    useSessionStore.setState({
      dataContextBySession: new Map([['s1', {
        sessionId: 's1',
        connectionId: 'conn-1',
        connectionNameSnapshot: 'Main',
        database: 'orders',
        schema: 'public',
        selectedLevel: 'schema',
        updatedAt: 1713650010000,
      }]]),
    } as any)

    useChatPartsStore.getState().upsertInfo('s1', {
      id: 'u1',
      role: 'user',
      sessionID: 's1',
      time: { created: 1 },
    })
    useChatPartsStore.getState().upsertPart('s1', {
      type: 'text',
      id: 'p1',
      sessionID: 's1',
      messageID: 'u1',
      text: `! select '你好' as "name"`,
      metadata: { displayKind: 'bang_query_user', queryMode: 'direct_sql' },
    } as any)

    renderTurn(
      <SessionTurn
        sessionId="s1"
        userMessageId="u1"
        assistantMessageIds={[]}
        userInfo={{ id: 'u1', role: 'user', sessionID: 's1', time: { created: 1 } }}
        isLastTurn
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '重跑' }))

    await waitFor(() =>
      expect(openDirectSqlQueryEditorTabMock).toHaveBeenCalledWith({
        sessionId: 's1',
        connectionId: 'conn-1',
        sql: `select '你好' as "name"`,
        autoRun: true,
      }),
    )
  })

  it('opens WITH bang-query in editor without auto-run from bubble action button', async () => {
    const openDirectSqlQueryEditorTabMock = openDirectSqlQueryEditorTabApi.openDirectSqlQueryEditorTab as unknown as Mock
    openDirectSqlQueryEditorTabMock.mockResolvedValue('tab-rerun-with')
    useSessionStore.setState({
      dataContextBySession: new Map([['s1', {
        sessionId: 's1',
        connectionId: 'conn-1',
        connectionNameSnapshot: 'Main',
        database: 'orders',
        schema: 'public',
        selectedLevel: 'schema',
        updatedAt: 1713650010000,
      }]]),
    } as any)

    useChatPartsStore.getState().upsertInfo('s1', {
      id: 'u1',
      role: 'user',
      sessionID: 's1',
      time: { created: 1 },
    })
    useChatPartsStore.getState().upsertPart('s1', {
      type: 'text',
      id: 'p1',
      sessionID: 's1',
      messageID: 'u1',
      text: '!with cte as (select 1) select * from cte',
      metadata: { displayKind: 'bang_query_user', queryMode: 'direct_sql' },
    } as any)

    renderTurn(
      <SessionTurn
        sessionId="s1"
        userMessageId="u1"
        assistantMessageIds={[]}
        userInfo={{ id: 'u1', role: 'user', sessionID: 's1', time: { created: 1 } }}
        isLastTurn
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '重跑' }))

    await waitFor(() =>
      expect(openDirectSqlQueryEditorTabMock).toHaveBeenCalledWith({
        sessionId: 's1',
        connectionId: 'conn-1',
        sql: 'with cte as (select 1) select * from cte',
        autoRun: false,
      }),
    )
  })

  it('renders pending user bubble without slide-in animation to avoid jitter', () => {
    useChatPartsStore.getState().upsertInfo('s1', {
      id: 'u1',
      role: 'user',
      sessionID: 's1',
      time: { created: 1 },
      __pending: true,
    })
    useChatPartsStore.getState().upsertPart('s1', {
      type: 'text',
      id: 'p1',
      sessionID: 's1',
      messageID: 'u1',
      text: 'show me orders',
      metadata: {},
    } as any)

    renderTurn(
      <SessionTurn
        sessionId="s1"
        userMessageId="u1"
        assistantMessageIds={[]}
        userInfo={{ id: 'u1', role: 'user', sessionID: 's1', time: { created: 1 }, __pending: true }}
        isLastTurn
      />,
    )

    const shell = screen.getByText('show me orders').closest('[data-pending-user-motion="true"]')
    expect(shell).not.toBeNull()

    const bubble = screen.getByText('show me orders').closest('.bg-primary')
    expect(bubble).not.toBeNull()
    expect(bubble?.className).not.toContain('slide-in-from-bottom')
    expect(bubble?.className).not.toContain('animate-in')
    expect(bubble?.className).toContain('opacity-85')
  })

  it('reserves assistant space for a pending last turn before streaming flips on', () => {
    useChatPartsStore.getState().upsertInfo('s1', {
      id: 'u1',
      role: 'user',
      sessionID: 's1',
      time: { created: 1 },
      __pending: true,
    })
    useChatPartsStore.getState().upsertPart('s1', {
      type: 'text',
      id: 'p1',
      sessionID: 's1',
      messageID: 'u1',
      text: 'show me orders',
      metadata: {},
    } as any)

    const { container } = renderTurn(
      <SessionTurn
        sessionId="s1"
        userMessageId="u1"
        assistantMessageIds={[]}
        userInfo={{ id: 'u1', role: 'user', sessionID: 's1', time: { created: 1 }, __pending: true }}
        isLastTurn
      />,
    )

    const reserved = container.querySelector('[data-component="session-turn"] > .min-h-9')
    expect(reserved).not.toBeNull()
  })

  it('reserves user bubble meta space while the user message is still pending', () => {
    useChatPartsStore.getState().upsertInfo('s1', {
      id: 'u1',
      role: 'user',
      sessionID: 's1',
      time: { created: 1 },
      __pending: true,
    })
    useChatPartsStore.getState().upsertPart('s1', {
      type: 'text',
      id: 'p1',
      sessionID: 's1',
      messageID: 'u1',
      text: 'show me orders',
      metadata: {},
    } as any)

    renderTurn(
      <SessionTurn
        sessionId="s1"
        userMessageId="u1"
        assistantMessageIds={[]}
        userInfo={{ id: 'u1', role: 'user', sessionID: 's1', time: { created: 1 }, __pending: true }}
        isLastTurn
      />,
    )

    expect(screen.getByTestId('user-bubble-meta-placeholder')).toBeInTheDocument()
  })

  it('keeps the previous assistant turn height stable when a new pending user turn appends', () => {
    useChatPartsStore.getState().upsertInfo('s1', {
      id: 'u1',
      role: 'user',
      sessionID: 's1',
      time: { created: 1 },
    })
    useChatPartsStore.getState().upsertPart('s1', {
      type: 'text',
      id: 'u1p',
      sessionID: 's1',
      messageID: 'u1',
      text: 'first question',
      metadata: {},
    } as any)
    useChatPartsStore.getState().upsertInfo('s1', {
      id: 'a1',
      role: 'assistant',
      sessionID: 's1',
      modelID: 'deepseek-chat',
      time: { created: 2 },
    })
    useChatPartsStore.getState().upsertPart('s1', {
      type: 'text',
      id: 'a1p',
      sessionID: 's1',
      messageID: 'a1',
      text: 'first answer',
      metadata: {},
    } as any)

    const view = renderTurn(
      <SessionTurn
        sessionId="s1"
        userMessageId="u1"
        assistantMessageIds={['a1']}
        userInfo={{ id: 'u1', role: 'user', sessionID: 's1', time: { created: 1 } }}
        isLastTurn
      />,
    )

    const previousTurnBefore = screen.getByText('first question').closest('[data-component="session-turn"]') as HTMLElement | null
    const previousHeightBefore = estimateTurnHeight(previousTurnBefore)

    act(() => {
      useChatPartsStore.getState().upsertInfo('s1', {
        id: 'u2',
        role: 'user',
        sessionID: 's1',
        time: { created: 3 },
        __pending: true,
      })
      useChatPartsStore.getState().upsertPart('s1', {
        type: 'text',
        id: 'u2p',
        sessionID: 's1',
        messageID: 'u2',
        text: 'second question',
        metadata: {},
      } as any)
    })

    view.rerenderWithClient(
      <>
        <SessionTurn
          sessionId="s1"
          userMessageId="u1"
          assistantMessageIds={['a1']}
          userInfo={{ id: 'u1', role: 'user', sessionID: 's1', time: { created: 1 } }}
          isLastTurn={false}
        />
        <SessionTurn
          sessionId="s1"
          userMessageId="u2"
          assistantMessageIds={[]}
          userInfo={{ id: 'u2', role: 'user', sessionID: 's1', time: { created: 3 }, __pending: true }}
          isLastTurn
        />
      </>,
    )

    const previousTurnAfter = screen.getByText('first question').closest('[data-component="session-turn"]') as HTMLElement | null
    const previousHeightAfter = estimateTurnHeight(previousTurnAfter)

    expect(view.container.querySelectorAll('[data-pending-user-motion="true"]')).toHaveLength(1)
    expect(previousHeightAfter).toBe(previousHeightBefore)
  })
})
