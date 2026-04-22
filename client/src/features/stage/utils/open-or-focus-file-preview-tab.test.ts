import { beforeEach, describe, expect, it } from 'vitest'
import { useStageStore } from '@/stores/stage-store'
import { openOrFocusFilePreviewTab } from './open-or-focus-file-preview-tab'

describe('openOrFocusFilePreviewTab', () => {
  beforeEach(() => {
    useStageStore.setState({
      openBySession: new Map(),
      autoOpenedSessions: new Set(),
      maximizedBySession: new Map(),
      revealOrigin: null,
      sidebarCollapsedBySession: new Map(),
      sidebarSelectionBySession: new Map(),
      resourceTreeExpandedBySession: new Map(),
      activeRailPanelBySession: new Map(),
      workspaceTabs: [],
      tabsBySession: new Map(),
      activeWorkspaceTabId: null,
      activeTabIdBySession: new Map(),
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

    const tabs = useStageStore.getState().tabsBySession.get('sess-1') ?? []
    expect(tabs).toHaveLength(1)
    expect(tabs[0]).toEqual(expect.objectContaining({
      type: 'file_preview',
      scope: 'session',
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
    expect(useStageStore.getState().activeTabIdBySession.get('sess-1')).toBe(result.tabId)
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
    expect(useStageStore.getState().tabsBySession.get('sess-1')).toHaveLength(1)
    expect(useStageStore.getState().activeTabIdBySession.get('sess-1')).toBe(first.tabId)
  })

  it('creates a distinct file preview tab for the same sourceKey in a different session', () => {
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
    expect(second.created).toBe(true)
    expect(second.tabId).not.toBe(first.tabId)
    expect(useStageStore.getState().tabsBySession.get('sess-1')).toHaveLength(1)
    expect(useStageStore.getState().tabsBySession.get('sess-2')).toHaveLength(1)
    expect(useStageStore.getState().activeTabIdBySession.get('sess-1')).toBe(first.tabId)
    expect(useStageStore.getState().activeTabIdBySession.get('sess-2')).toBe(second.tabId)
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
