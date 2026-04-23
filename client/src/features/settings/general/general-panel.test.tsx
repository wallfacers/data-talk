import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { GeneralSettingsPanel } from './general-panel'
import * as sessionApi from '@/services/api/session'
import { useSessionStore } from '@/stores/session-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'
import { useStageStore } from '@/stores/stage-store'
import { useChannelStore } from '@/stores/channel-store'

const openBlankSessionMock = vi.fn(async () => {})

vi.mock('@/features/session/hooks/use-open-blank-session', () => ({
  useOpenBlankSession: () => openBlankSessionMock,
}))

function renderPanel() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={qc}>
      <GeneralSettingsPanel />
    </QueryClientProvider>,
  )
  return qc
}

function seedSessionResources() {
  useSessionStore.setState({
    activeSessionId: 's1',
    modeBySession: new Map([['s1', 'SPLIT']]),
    hasEverSentBySession: new Map([['s1', true]]),
    dataContextBySession: new Map([['s1', {
      sessionId: 's1',
      connectionId: 'c1',
      connectionNameSnapshot: 'Main',
      database: 'db1',
      schema: 'public',
      selectedLevel: 'schema',
      updatedAt: Date.now(),
    }]]),
    pendingPrompt: 'pending',
    composerRestoreDraft: { sessionId: 's1', text: 'draft' },
    pendingModelPrompt: true,
    pendingConnectionPrompt: true,
    pendingActionAfterConnectionPick: { kind: 'send' },
  })

  useChatPartsStore.setState((s) => ({
    partsBySession: new Map([['s1', new Map()]]),
    infoBySession: new Map([['s1', new Map()]]),
    partIndexBySession: new Map([['s1', new Map()]]),
    streamingBySession: new Set(['s1']),
    pendingDeltasBySession: new Map([['s1', new Map()]]),
    version: s.version + 1,
  }))
  useOntologyStore.setState({
    artifactsBySession: new Map([['s1', new Map([['a1', { id: 'a1', version: 1, kind: 'table' } as any]])]]),
  })
  useTimelineStore.setState({
    orderBySession: new Map([['s1', ['a1']]]),
    activeBySession: new Map([['s1', 'a1']]),
    manualBySession: new Map([['s1', false]]),
  })
  useStageStore.setState({
    openBySession: new Map([['s1', true]]),
    autoOpenedSessions: new Set(['s1']),
    maximizedBySession: new Map([['s1', true]]),
    sidebarCollapsedBySession: new Map([['s1', false]]),
    sidebarSelectionBySession: new Map([['s1', null]]),
    resourceTreeExpandedBySession: new Map([['s1', ['node']]]),
    activeRailPanelBySession: new Map([['s1', 'history']]),
    tabsBySession: new Map([['s1', []]]),
    activeTabIdBySession: new Map([['s1', 'tab-1']]),
  } as any)
  useChannelStore.setState({
    lastEventIdBySession: new Map([['s1', 42]]),
  })
}

describe('GeneralSettingsPanel clear all sessions', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    openBlankSessionMock.mockClear()
    seedSessionResources()
  })

  it('cancel does not call clear-all api', async () => {
    const clearAllSpy = vi.spyOn(sessionApi, 'clearAllSessions').mockResolvedValue(undefined)
    renderPanel()

    fireEvent.click(screen.getByRole('button', { name: '清空全部会话' }))
    fireEvent.click(await screen.findByRole('button', { name: '取消' }))

    expect(clearAllSpy).not.toHaveBeenCalled()
    expect(openBlankSessionMock).not.toHaveBeenCalled()
  })

  it('confirm clears all sessions and local session resources', async () => {
    const clearAllSpy = vi.spyOn(sessionApi, 'clearAllSessions').mockResolvedValue(undefined)
    renderPanel()

    fireEvent.click(screen.getByRole('button', { name: '清空全部会话' }))
    fireEvent.click(await screen.findByRole('button', { name: '确认清空' }))

    await waitFor(() => expect(clearAllSpy).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(openBlankSessionMock).toHaveBeenCalledTimes(1))

    expect(useSessionStore.getState().activeSessionId).toBeNull()
    expect(useSessionStore.getState().modeBySession.size).toBe(0)
    expect(useSessionStore.getState().hasEverSentBySession.size).toBe(0)
    expect(useSessionStore.getState().dataContextBySession.size).toBe(0)
    expect(useSessionStore.getState().pendingPrompt).toBeNull()
    expect(useSessionStore.getState().composerRestoreDraft).toBeNull()
    expect(useSessionStore.getState().pendingModelPrompt).toBe(false)
    expect(useSessionStore.getState().pendingConnectionPrompt).toBe(false)
    expect(useSessionStore.getState().pendingActionAfterConnectionPick).toBeNull()
    expect(useChatPartsStore.getState().partsBySession.size).toBe(0)
    expect(useChatPartsStore.getState().infoBySession.size).toBe(0)
    expect(useChatPartsStore.getState().partIndexBySession.size).toBe(0)
    expect(useChatPartsStore.getState().streamingBySession.size).toBe(0)
    expect(useOntologyStore.getState().artifactsBySession.size).toBe(0)
    expect(useTimelineStore.getState().orderBySession.size).toBe(0)
    expect(useTimelineStore.getState().activeBySession.size).toBe(0)
    expect(useTimelineStore.getState().manualBySession.size).toBe(0)
    expect(useStageStore.getState().openBySession.size).toBe(0)
    expect(useStageStore.getState().tabsBySession.size).toBe(0)
    expect(useStageStore.getState().activeTabIdBySession.size).toBe(0)
    expect(useChannelStore.getState().lastEventIdBySession.size).toBe(0)
  })
})
