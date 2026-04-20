import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { I18nContext } from '@/i18n/provider'
import { translateMessage } from '@/i18n/messages'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import * as chooserStore from '@/features/session/data-source-picker/data-source-picker-store'
import * as sessionApi from '@/services/api/session'
import { PromptComposer } from '../prompt-composer'

vi.mock('../model-picker/model-picker', () => ({
  ModelPicker: () => <div>model-picker</div>,
}))

vi.mock('../data-source-picker/data-source-picker', () => ({
  DataSourcePicker: () => <div>data-source-picker</div>,
}))

vi.mock('@/features/stage/components/stage-toggle-button', () => ({
  StageToggleButton: () => <div>stage-toggle</div>,
}))

vi.mock('../hooks/use-has-active-model', () => ({
  useHasActiveModel: () => true,
}))

vi.mock('@/services/channel/use-channel', () => ({
  useChannel: () => ({
    sendMessage: vi.fn(),
    abort: vi.fn(),
    isStreaming: false,
  }),
}))

vi.mock('@/services/api/session')

function renderWithClient(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <I18nContext.Provider
      value={{
        language: 'zh-CN',
        setLanguage: vi.fn(),
        t: (key, values) => translateMessage('zh-CN', key, values),
      }}
    >
      <QueryClientProvider client={qc}>{ui}</QueryClientProvider>
    </I18nContext.Provider>,
  )
}

describe('PromptComposer', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.body.innerHTML = '<div id="composer-slot"></div>'
    useConnectionStore.setState({ activeConnectionId: null, connections: [] })
    useSessionStore.setState({
      activeSessionId: null,
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      pendingPrompt: null,
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
    } as any)
  })

  it('requests connection chooser before creating session when no active connection', async () => {
    const requestPick = vi.spyOn(chooserStore.useDataSourcePickerStore.getState(), 'requestPick')
      .mockResolvedValue({ cancelled: true })

    renderWithClient(<PromptComposer />)
    fireEvent.change(screen.getByPlaceholderText('用自然语言查询你的数据库...'), {
      target: { value: 'show me orders' },
    })
    fireEvent.click(document.querySelector('button[type="submit"]') as HTMLButtonElement)

    await waitFor(() => {
      expect(requestPick).toHaveBeenCalled()
      expect(sessionApi.createSession).not.toHaveBeenCalled()
    })
  })
})
