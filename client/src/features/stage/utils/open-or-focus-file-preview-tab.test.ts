import { beforeEach, describe, expect, it } from 'vitest'
import { useStageStore } from '@/stores/stage-store'
import { openOrFocusFilePreviewTab } from './open-or-focus-file-preview-tab'

describe('openOrFocusFilePreviewTab', () => {
  beforeEach(() => {
    useStageStore.setState({
      open: false,
      maximized: false,
      revealOrigin: null,
      sidebarCollapsed: false,
      sidebarSelection: null,
      resourceTreeExpanded: [],
      activeRailPanel: null,
      tabs: [],
      openTabIds: new Set(),
      openTabIdsOrdered: [],
      activeTabId: null,
    } as unknown as Record<string, unknown>)
  })

  it('creates a session-scoped file preview tab on first sync in the current session', () => {
    const result = openOrFocusFilePreviewTab({
      getState: useStageStore.getState,
      sessionId: 'sess-1',
      payload: {
        sourceKey: 'msg-1:part-1',
        filePath: '/tmp/report.md',
        filename: 'report.md',
        fileType: 'markdown',
        content: '# Report',
        truncated: false,
        language: 'markdown',
      },
    })

    expect(result).toEqual({ tabId: expect.any(String), created: true })

    const tabs = useStageStore.getState().tabs
    expect(tabs).toHaveLength(1)
    expect(tabs[0]).toEqual(expect.objectContaining({
      type: 'file_preview',
      originSessionId: 'sess-1',
      title: 'report.md',
      payload: expect.objectContaining({
        sourceKey: 'msg-1:part-1',
        filePath: '/tmp/report.md',
        filename: 'report.md',
        fileType: 'markdown',
        content: '# Report',
        truncated: false,
        language: 'markdown',
      }),
    }))
    expect(useStageStore.getState().activeTabId).toBe(result.tabId)
  })

  it('focuses the existing file preview tab when sourceKey matches again in the same session', () => {
    const first = openOrFocusFilePreviewTab({
      getState: useStageStore.getState,
      sessionId: 'sess-1',
      payload: {
        sourceKey: 'msg-1:part-1',
        filePath: '/tmp/report.md',
        filename: 'report.md',
        fileType: 'markdown',
        content: '# Report',
        truncated: false,
        language: 'markdown',
      },
    })

    const second = openOrFocusFilePreviewTab({
      getState: useStageStore.getState,
      sessionId: 'sess-1',
      payload: {
        sourceKey: 'msg-1:part-1',
        filePath: '/tmp/report.md',
        filename: 'report.md',
        fileType: 'markdown',
        content: '# Updated Report',
        truncated: true,
        language: 'markdown',
      },
    })

    expect(first.created).toBe(true)
    expect(second).toEqual({ tabId: first.tabId, created: false })
    expect(useStageStore.getState().tabs).toHaveLength(1)
    expect(useStageStore.getState().activeTabId).toBe(first.tabId)
  })

  it('reuses the existing file preview tab for the same sourceKey even from a different session', () => {
    const first = openOrFocusFilePreviewTab({
      getState: useStageStore.getState,
      sessionId: 'sess-1',
      payload: {
        sourceKey: 'msg-1:part-1',
        filePath: '/tmp/report.md',
        filename: 'report.md',
        fileType: 'markdown',
        content: '# Report',
        truncated: false,
        language: 'markdown',
      },
    })

    const second = openOrFocusFilePreviewTab({
      getState: useStageStore.getState,
      sessionId: 'sess-2',
      payload: {
        sourceKey: 'msg-1:part-1',
        filePath: '/tmp/report.md',
        filename: 'report.md',
        fileType: 'markdown',
        content: '# Report',
        truncated: false,
        language: 'markdown',
      },
    })

    expect(first.created).toBe(true)
    // In the global tabs model, same sourceKey reuses the existing tab regardless of sessionId
    expect(second).toEqual({ tabId: first.tabId, created: false })
    expect(useStageStore.getState().tabs).toHaveLength(1)
    expect(useStageStore.getState().activeTabId).toBe(first.tabId)
  })

  it('throws a clear error when sessionId is missing or blank', () => {
    expect(() =>
      openOrFocusFilePreviewTab({
        getState: useStageStore.getState,
        sessionId: null,
        payload: {
          sourceKey: 'msg-1:part-1',
          filePath: '/tmp/report.md',
          filename: 'report.md',
          fileType: 'markdown',
          content: '# Report',
          truncated: false,
          language: 'markdown',
        },
      })
    ).toThrow('sessionId is required to open a session file preview tab')

    expect(() =>
      openOrFocusFilePreviewTab({
        getState: useStageStore.getState,
        sessionId: '   ',
        payload: {
          sourceKey: 'msg-1:part-1',
          filePath: '/tmp/report.md',
          filename: 'report.md',
          fileType: 'markdown',
          content: '# Report',
          truncated: false,
          language: 'markdown',
        },
      })
    ).toThrow('sessionId is required to open a session file preview tab')
  })
})
