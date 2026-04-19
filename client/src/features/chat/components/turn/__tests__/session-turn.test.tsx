import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SessionTurn } from '../session-turn'
import { useChatPartsStore } from '@/stores/chat-parts-store'

function renderTurn(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

describe('SessionTurn · showThinking', () => {
  beforeEach(() => {
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
    })
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
})
