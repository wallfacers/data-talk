import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useSessionStore } from '@/stores/session-store'
import * as useChannelModule from '@/services/channel/use-channel'
import { usePendingPromptResume } from '../use-pending-prompt-resume'

vi.mock('@/services/channel/use-channel')
vi.mock('../use-has-active-model', () => ({
  useHasActiveModel: () => true,
}))

describe('usePendingPromptResume', () => {
  const sendMessage = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    sendMessage.mockResolvedValue(true)
    vi.mocked(useChannelModule.useChannel).mockReturnValue({
      sendMessage,
      abort: vi.fn(),
      isStreaming: false,
      isAborting: false,
      canAbort: false,
      client: null as any,
      retryPendingUser: vi.fn(),
      removePendingUser: vi.fn(),
    })
    useSessionStore.setState({
      activeSessionId: null,
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      dataContextBySession: new Map(),
      pendingPrompt: null,
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
      composerRestoreDraft: null,
    } as any)
  })

  it('stores a restore draft when queued send fails', async () => {
    sendMessage.mockResolvedValueOnce(false)
    useSessionStore.setState({
      activeSessionId: 'sess-1',
      pendingPrompt: '你好',
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
    } as any)

    renderHook(() => usePendingPromptResume())

    await waitFor(() => expect(sendMessage).toHaveBeenCalled())
    expect(useSessionStore.getState().pendingPrompt).toBeNull()
    expect(useSessionStore.getState().composerRestoreDraft).toEqual({
      sessionId: 'sess-1',
      text: '你好',
    })
  })
})
