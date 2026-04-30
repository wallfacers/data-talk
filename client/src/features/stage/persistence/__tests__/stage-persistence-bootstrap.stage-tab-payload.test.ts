import { beforeEach, describe, expect, it, vi } from 'vitest'
import { coordinator } from '../stage-persistence-bootstrap'
import { useStageStore } from '@/stores/stage-store'

describe('stage-persistence-bootstrap - stage tab payload subscription', () => {
  beforeEach(() => {
    coordinator.phase = 'idle'
    useStageStore.setState({
      tabs: [],
      activeTabId: null,
      openTabIds: new Set<string>(),
      openTabIdsOrdered: [],
      open: false,
      maximized: false,
      sidebarCollapsed: false,
      sidebarSelection: null,
      resourceTreeExpanded: [],
      activeRailPanel: null,
      revealOrigin: null,
    })
    vi.spyOn(coordinator, 'scheduleContentWrite').mockImplementation(() => undefined)
  })

  it('persists artifact preview payload when a new persistent stage-local tab opens', () => {
    useStageStore.getState().openArtifactPreviewTab('sess-1', 'art-1', 'Revenue Chart')

    const tab = useStageStore.getState().tabs[0]
    expect(tab?.type).toBe('artifact_preview')

    expect(coordinator.scheduleContentWrite).toHaveBeenCalledWith(
      tab?.tabId,
      expect.objectContaining({
        payload: expect.objectContaining({
          artifactId: 'art-1',
          artifactTitle: 'Revenue Chart',
          sessionId: 'sess-1',
        }),
        contentText: 'Revenue Chart',
        expectedVersion: undefined,
      }),
    )
  })

  it('does not persist metadata-only artifact payload placeholders during hydration', () => {
    coordinator.phase = 'hydrating'

    useStageStore.getState().__hydrateAll([{
      tabId: 'artifact_preview_1',
      type: 'artifact_preview',
      title: 'Revenue Chart',
      originSessionId: 'sess-1',
      payload: {},
      payloadVersion: 7,
      createdAt: 1,
      lastTouchedAt: 1,
    }])

    expect(coordinator.scheduleContentWrite).not.toHaveBeenCalled()
  })
})
