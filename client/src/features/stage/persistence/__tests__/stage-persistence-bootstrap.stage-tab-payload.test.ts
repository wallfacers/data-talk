import { beforeEach, describe, expect, it, vi } from 'vitest'
import { coordinator } from '../stage-persistence-bootstrap'
import { useStageStore } from '@/stores/stage-store'

describe('stage-persistence-bootstrap - stage tab payload subscription', () => {
  beforeEach(() => {
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
})
