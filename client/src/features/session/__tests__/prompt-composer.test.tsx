import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { I18nContext } from '@/i18n/provider'
import { translateMessage } from '@/i18n/messages'
import { useConnectionStore } from '@/features/connection/store'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useSessionStore } from '@/stores/session-store'
import * as chooserStore from '@/features/session/data-source-picker/data-source-picker-store'
import * as sessionApi from '@/services/api/session'
import * as bangQueryApi from '@/services/api/bang-query-message'
import * as openDirectSqlQueryEditorTabApi from '@/features/stage/utils/open-direct-sql-query-editor-tab'
import * as sessionDataContextApi from '@/services/api/session-data-context'
import { PromptComposer } from '../prompt-composer'
import type { Mock } from 'vitest'

const channel = {
  sendMessage: vi.fn(),
  abort: vi.fn(),
  isStreaming: false,
}

let hasActiveModel = true

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
  useHasActiveModel: () => hasActiveModel,
}))

vi.mock('@/services/channel/use-channel', () => ({
  useChannel: () => channel,
}))

vi.mock('@/services/api/session')
vi.mock('@/services/api/bang-query-message')
vi.mock('@/services/api/session-data-context', () => ({
  getSessionDataContext: vi.fn(),
  setSessionDataContext: vi.fn(),
  resolveUseTarget: vi.fn(),
  validateSessionDataContext: vi.fn(),
}))
vi.mock('@/features/stage/utils/open-direct-sql-query-editor-tab')

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
    hasActiveModel = true
    channel.sendMessage.mockReset()
    channel.sendMessage.mockResolvedValue(true)
    channel.abort.mockReset()
    channel.isStreaming = false
    vi.mocked(sessionDataContextApi.getSessionDataContext).mockResolvedValue(null as any)
    vi.mocked(sessionDataContextApi.resolveUseTarget).mockResolvedValue({
      status: 'not_found',
      context: null,
      matchedTarget: null,
      candidates: [],
      suggestions: [],
      message: null,
    } as any)
    vi.mocked(sessionDataContextApi.setSessionDataContext).mockResolvedValue(null as any)
    vi.mocked(sessionDataContextApi.validateSessionDataContext).mockResolvedValue(null as any)
    document.body.innerHTML = '<div id="composer-slot"></div>'
    useConnectionStore.setState({ activeConnectionId: null, connections: [] })
    useSessionStore.setState({
      activeSessionId: null,
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      dataContextBySession: new Map(),
      pendingPrompt: null,
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
    } as any)
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
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

  it('opens a newly created AI chat session directly in split mode before the pending prompt resumes', async () => {
    const createSessionMock = sessionApi.createSession as unknown as Mock
    createSessionMock.mockResolvedValue({ id: 'sess-ai-first', hasEverSent: false } as any)

    useConnectionStore.setState({ activeConnectionId: 'conn-1', connections: [{ id: 'conn-1', name: 'Main' } as any] })
    useSessionStore.setState({
      activeSessionId: null,
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      dataContextBySession: new Map(),
      pendingPrompt: null,
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
    } as any)

    renderWithClient(<PromptComposer />)
    fireEvent.change(screen.getByPlaceholderText('用自然语言查询你的数据库...'), {
      target: { value: 'show me orders' },
    })
    fireEvent.click(document.querySelector('button[type="submit"]') as HTMLButtonElement)

    await waitFor(() => expect(createSessionMock).toHaveBeenCalledWith('conn-1', 'show me orders'))
    await waitFor(() => expect(useSessionStore.getState().activeSessionId).toBe('sess-ai-first'))

    expect(useSessionStore.getState().activeSessionId).toBe('sess-ai-first')
    expect(useSessionStore.getState().modeBySession.get('sess-ai-first')).toBe('SPLIT')
    expect(useSessionStore.getState().hasEverSentBySession.get('sess-ai-first')).toBe(true)
  })

  it('restores the textarea immediately when an active-session AI send fails', async () => {
    channel.sendMessage.mockResolvedValueOnce(false)

    useConnectionStore.setState({ activeConnectionId: 'conn-1', connections: [{ id: 'conn-1', name: 'Main' } as any] })
    useSessionStore.setState({
      activeSessionId: 'sess-1',
      modeBySession: new Map([['sess-1', 'SPLIT']]),
      hasEverSentBySession: new Map([['sess-1', true]]),
      dataContextBySession: new Map(),
      pendingPrompt: null,
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
      composerRestoreDraft: null,
    } as any)

    renderWithClient(<PromptComposer />)
    const textarea = screen.getByPlaceholderText('用自然语言查询你的数据库...') as HTMLTextAreaElement
    fireEvent.change(textarea, { target: { value: '你好' } })
    fireEvent.click(document.querySelector('button[type="submit"]') as HTMLButtonElement)

    await waitFor(() => expect(channel.sendMessage).toHaveBeenCalled())
    await waitFor(() => expect(textarea.value).toBe('你好'))
  })

  it('re-hydrates the textarea from a queued restore draft', async () => {
    useConnectionStore.setState({ activeConnectionId: 'conn-1', connections: [{ id: 'conn-1', name: 'Main' } as any] })
    useSessionStore.setState({
      activeSessionId: 'sess-restore',
      modeBySession: new Map([['sess-restore', 'SPLIT']]),
      hasEverSentBySession: new Map([['sess-restore', true]]),
      dataContextBySession: new Map(),
      pendingPrompt: null,
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
      composerRestoreDraft: { sessionId: 'sess-restore', text: '你好' },
    } as any)

    renderWithClient(<PromptComposer />)

    await waitFor(() => {
      expect((screen.getByPlaceholderText('用自然语言查询你的数据库...') as HTMLTextAreaElement).value).toBe('你好')
    })
    expect(useSessionStore.getState().composerRestoreDraft).toBeNull()
  })

  it('persists bang query text before opening the bang query tab', async () => {
    vi.spyOn(Date, 'now').mockReturnValue(1713650000000)
    const createBangQueryMessageMock = bangQueryApi.createBangQueryMessage as unknown as Mock
    const openDirectSqlQueryEditorTabMock = openDirectSqlQueryEditorTabApi.openDirectSqlQueryEditorTab as unknown as Mock
    createBangQueryMessageMock.mockResolvedValue({
      id: 'sqm-1',
      sessionId: 'sess-1',
      createdAt: 1713650000000,
      kind: 'bang_query_user',
    } as any)
    openDirectSqlQueryEditorTabMock.mockResolvedValue('tab-1')

    useConnectionStore.setState({ activeConnectionId: 'conn-1', connections: [{ id: 'conn-1', name: 'Main' } as any] })
    useSessionStore.setState({
      activeSessionId: 'sess-1',
      modeBySession: new Map(),
      hasEverSentBySession: new Map([['sess-1', true]]),
      dataContextBySession: new Map(),
      pendingPrompt: null,
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
    } as any)

    renderWithClient(<PromptComposer />)
    fireEvent.change(screen.getByPlaceholderText('用自然语言查询你的数据库...'), {
      target: { value: '!select 1' },
    })
    fireEvent.click(document.querySelector('button[type="submit"]') as HTMLButtonElement)

    await waitFor(() => expect(createBangQueryMessageMock).toHaveBeenCalled())
    expect(channel.sendMessage).not.toHaveBeenCalled()
    expect(createBangQueryMessageMock).toHaveBeenCalledWith('sess-1', '!select 1', 1713650000000)
    expect(openDirectSqlQueryEditorTabMock).toHaveBeenCalledWith({
      sessionId: 'sess-1',
      connectionId: 'conn-1',
      sql: 'select 1',
    })
    expect(useSessionStore.getState().hasEverSentBySession.get('sess-1')).toBe(true)
    expect(useSessionStore.getState().modeBySession.get('sess-1')).toBe('SPLIT')
    expect(useSessionStore.getState().activeSessionId).toBe('sess-1')
    expect(useChatPartsStore.getState().infoBySession.get('sess-1')?.get('sqm-1')).toMatchObject({
      id: 'sqm-1',
      role: 'user',
      sessionID: 'sess-1',
      time: { created: 1713650000000 },
    })
    expect(useChatPartsStore.getState().partsBySession.get('sess-1')?.get('sqm-1')?.[0]).toMatchObject({
      type: 'text',
      text: '!select 1',
      metadata: { displayKind: 'bang_query_user', queryMode: 'direct_sql' },
    })
    expect(useSessionStore.getState().pendingPrompt).toBeNull()
    expect(createBangQueryMessageMock.mock.invocationCallOrder[0]).toBeLessThan(
      openDirectSqlQueryEditorTabMock.mock.invocationCallOrder[0],
    )
  })

  it('creates a session before persisting a bang query when no active session exists', async () => {
    vi.spyOn(Date, 'now').mockReturnValue(1713650001234)
    const createSessionMock = sessionApi.createSession as unknown as Mock
    const createBangQueryMessageMock = bangQueryApi.createBangQueryMessage as unknown as Mock
    const openDirectSqlQueryEditorTabMock = openDirectSqlQueryEditorTabApi.openDirectSqlQueryEditorTab as unknown as Mock
    createSessionMock.mockResolvedValue({ id: 'sess-created', hasEverSent: false } as any)
    createBangQueryMessageMock.mockResolvedValue({
      id: 'sqm-2',
      sessionId: 'sess-created',
      createdAt: 1713650001234,
      kind: 'bang_query_user',
    } as any)
    openDirectSqlQueryEditorTabMock.mockResolvedValue('tab-2')

    useConnectionStore.setState({ activeConnectionId: 'conn-1', connections: [{ id: 'conn-1', name: 'Main' } as any] })
    useSessionStore.setState({
      activeSessionId: null,
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      dataContextBySession: new Map(),
      pendingPrompt: null,
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
    } as any)

    renderWithClient(<PromptComposer />)
    fireEvent.change(screen.getByPlaceholderText('用自然语言查询你的数据库...'), {
      target: { value: '!with cte as (select 1) select * from cte' },
    })
    fireEvent.click(document.querySelector('button[type="submit"]') as HTMLButtonElement)

    await waitFor(() => expect(createSessionMock).toHaveBeenCalled())
    expect(createSessionMock).toHaveBeenCalledWith('conn-1', '!with cte as (select 1) select * from cte')
    expect(createBangQueryMessageMock).toHaveBeenCalledWith(
      'sess-created',
      '!with cte as (select 1) select * from cte',
      1713650001234,
    )
    expect(openDirectSqlQueryEditorTabMock).toHaveBeenCalledWith({
      sessionId: 'sess-created',
      connectionId: 'conn-1',
      sql: 'with cte as (select 1) select * from cte',
    })
    expect(createSessionMock.mock.invocationCallOrder[0]).toBeLessThan(
      createBangQueryMessageMock.mock.invocationCallOrder[0],
    )
    expect(useSessionStore.getState().hasEverSentBySession.get('sess-created')).toBe(true)
    expect(useSessionStore.getState().modeBySession.get('sess-created')).toBe('SPLIT')
    expect(useSessionStore.getState().pendingPrompt).toBeNull()
    expect(useSessionStore.getState().pendingModelPrompt).toBe(false)
  })

  it('creates and persists a bang query session without requiring an active AI model', async () => {
    hasActiveModel = false
    vi.spyOn(Date, 'now').mockReturnValue(1713650005678)
    const createSessionMock = sessionApi.createSession as unknown as Mock
    const createBangQueryMessageMock = bangQueryApi.createBangQueryMessage as unknown as Mock
    const openDirectSqlQueryEditorTabMock = openDirectSqlQueryEditorTabApi.openDirectSqlQueryEditorTab as unknown as Mock
    createSessionMock.mockResolvedValue({ id: 'sess-no-model', hasEverSent: false } as any)
    createBangQueryMessageMock.mockResolvedValue({
      id: 'sqm-3',
      sessionId: 'sess-no-model',
      createdAt: 1713650005678,
      kind: 'bang_query_user',
    } as any)
    openDirectSqlQueryEditorTabMock.mockResolvedValue('tab-3')

    useConnectionStore.setState({ activeConnectionId: 'conn-1', connections: [{ id: 'conn-1', name: 'Main' } as any] })
    useSessionStore.setState({
      activeSessionId: null,
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      dataContextBySession: new Map(),
      pendingPrompt: null,
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
    } as any)

    renderWithClient(<PromptComposer />)
    fireEvent.change(screen.getByPlaceholderText('用自然语言查询你的数据库...'), {
      target: { value: '!select 1' },
    })
    fireEvent.click(document.querySelector('button[type="submit"]') as HTMLButtonElement)

    await waitFor(() => expect(createSessionMock).toHaveBeenCalled())
    expect(createSessionMock).toHaveBeenCalledWith('conn-1', '!select 1')
    expect(createBangQueryMessageMock).toHaveBeenCalledWith('sess-no-model', '!select 1', 1713650005678)
    expect(openDirectSqlQueryEditorTabMock).toHaveBeenCalledWith({
      sessionId: 'sess-no-model',
      connectionId: 'conn-1',
      sql: 'select 1',
    })
    expect(useSessionStore.getState().hasEverSentBySession.get('sess-no-model')).toBe(true)
    expect(useSessionStore.getState().modeBySession.get('sess-no-model')).toBe('SPLIT')
    expect(useSessionStore.getState().pendingPrompt).toBeNull()
    expect(useSessionStore.getState().pendingModelPrompt).toBe(false)
    expect(channel.sendMessage).not.toHaveBeenCalled()
  })

  it('deletes a newly created blank session when bang-query persistence fails', async () => {
    const createSessionMock = sessionApi.createSession as unknown as Mock
    const deleteSessionMock = sessionApi.deleteSession as unknown as Mock
    const createBangQueryMessageMock = bangQueryApi.createBangQueryMessage as unknown as Mock
    createSessionMock.mockResolvedValue({ id: 'sess-failed', hasEverSent: false } as any)
    deleteSessionMock.mockResolvedValue(undefined)
    createBangQueryMessageMock.mockRejectedValue(new Error('persist failed'))

    useConnectionStore.setState({ activeConnectionId: 'conn-1', connections: [{ id: 'conn-1', name: 'Main' } as any] })
    useSessionStore.setState({
      activeSessionId: null,
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      dataContextBySession: new Map(),
      pendingPrompt: null,
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
    } as any)

    renderWithClient(<PromptComposer />)
    fireEvent.change(screen.getByPlaceholderText('用自然语言查询你的数据库...'), {
      target: { value: '!select 1' },
    })
    fireEvent.click(document.querySelector('button[type="submit"]') as HTMLButtonElement)

    await waitFor(() => expect(createBangQueryMessageMock).toHaveBeenCalled())
    await waitFor(() => expect(deleteSessionMock).toHaveBeenCalledWith('sess-failed'))
    expect(useSessionStore.getState().activeSessionId).toBeNull()
    expect(useSessionStore.getState().hasEverSentBySession.get('sess-failed')).toBeUndefined()
    expect(openDirectSqlQueryEditorTabApi.openDirectSqlQueryEditorTab).not.toHaveBeenCalled()
  })

  it('shows direct query mode state for bang-query inputs without changing AI routing for other bang commands', async () => {
    useConnectionStore.setState({ activeConnectionId: 'conn-1', connections: [{ id: 'conn-1', name: 'Main' } as any] })
    useSessionStore.setState({
      activeSessionId: 'sess-1',
      modeBySession: new Map(),
      hasEverSentBySession: new Map([['sess-1', true]]),
      dataContextBySession: new Map(),
      pendingPrompt: null,
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
    } as any)

    renderWithClient(<PromptComposer />)
    const textarea = screen.getByPlaceholderText('用自然语言查询你的数据库...')
    fireEvent.change(textarea, { target: { value: '!select 1' } })

    expect(screen.getByText('直查模式')).toBeInTheDocument()
    expect(document.querySelector('[data-slot="input-group"]')).toHaveAttribute('data-bang-query-mode', 'true')

    fireEvent.change(textarea, { target: { value: '!help' } })
    fireEvent.click(document.querySelector('button[type="submit"]') as HTMLButtonElement)

    await waitFor(() => expect(channel.sendMessage).toHaveBeenCalled())
    expect(bangQueryApi.createBangQueryMessage).not.toHaveBeenCalled()
    expect(openDirectSqlQueryEditorTabApi.openDirectSqlQueryEditorTab).not.toHaveBeenCalled()
  })

  it('resolves !use commands through the session data context API', async () => {
    vi.mocked(sessionDataContextApi.resolveUseTarget).mockResolvedValueOnce({
      status: 'matched',
      context: {
        sessionId: 'sess-1',
        connectionId: 'conn-1',
        connectionNameSnapshot: 'Main',
        database: 'orders',
        schema: 'public',
        selectedLevel: 'schema',
        updatedAt: 1713650010000,
      },
      matchedTarget: { level: 'schema', label: 'public' },
      candidates: [],
      suggestions: [],
      message: null,
    } as any)
    vi.mocked(sessionDataContextApi.setSessionDataContext).mockResolvedValueOnce({
      sessionId: 'sess-1',
      connectionId: 'conn-1',
      connectionNameSnapshot: 'Main',
      database: 'orders',
      schema: 'public',
      selectedLevel: 'schema',
      updatedAt: 1713650010000,
    } as any)

    useConnectionStore.setState({ activeConnectionId: 'conn-1', connections: [{ id: 'conn-1', name: 'Main' } as any] })
    useSessionStore.setState({
      activeSessionId: 'sess-1',
      modeBySession: new Map(),
      hasEverSentBySession: new Map([['sess-1', true]]),
      dataContextBySession: new Map(),
      pendingPrompt: null,
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
    } as any)

    renderWithClient(<PromptComposer />)
    fireEvent.change(screen.getByPlaceholderText('用自然语言查询你的数据库...'), {
      target: { value: '! use public' },
    })
    fireEvent.click(document.querySelector('button[type="submit"]') as HTMLButtonElement)

    await waitFor(() => expect(sessionDataContextApi.resolveUseTarget).toHaveBeenCalledWith('sess-1', 'public'))
    expect(sessionDataContextApi.setSessionDataContext).toHaveBeenCalledWith('sess-1', {
      connectionId: 'conn-1',
      database: 'orders',
      schema: 'public',
      selectedLevel: 'schema',
    })
    expect(channel.sendMessage).not.toHaveBeenCalled()
    expect(openDirectSqlQueryEditorTabApi.openDirectSqlQueryEditorTab).not.toHaveBeenCalled()
  })
})
