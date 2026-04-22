import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StageTabContent } from './stage-tab-content'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'

vi.mock('./sql-workbench-tab', () => ({
  SqlWorkbenchTab: () => <div data-testid="sql-workbench-tab">sql workbench tab</div>,
}))

vi.mock('./file-preview-tab', () => ({
  FilePreviewTab: () => <div data-testid="file-preview-tab">file preview tab</div>,
}))

vi.mock('@/components/ui/context-menu', () => ({
  ContextMenu: ({ children }: { children?: ReactNode }) => <>{children}</>,
  ContextMenuTrigger: ({ render, children }: { render?: ReactNode; children?: ReactNode }) => <>{render ?? children}</>,
  ContextMenuContent: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  ContextMenuItem: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  ContextMenuSeparator: () => null,
}))

describe('StageTabContent', () => {
  beforeEach(() => {
    useSessionStore.setState({ activeSessionId: 's1' })
    useStageStore.setState({
      workspaceTabs: [],
      tabsBySession: new Map(),
      activeWorkspaceTabId: null,
      activeTabIdBySession: new Map(),
    })
  })

  it('renders SqlWorkbenchTab when the active tab is query_editor', () => {
    useStageStore.setState({
      tabsBySession: new Map([
        ['s1', [
          {
            tabId: 'sql-1',
            type: 'query_editor',
            title: 'SQL',
            scope: 'session' as const,
            originSessionId: 's1',
            createdAt: 0,
            payload: {},
          },
        ]],
      ]),
      activeTabIdBySession: new Map([['s1', 'sql-1']]),
    })

    render(<StageTabContent />)

    expect(screen.getByTestId('sql-workbench-tab')).toBeTruthy()
  })

  it('renders FilePreviewTab when the active tab is file_preview', () => {
    useStageStore.setState({
      tabsBySession: new Map([
        ['s1', [
          {
            tabId: 'preview-1',
            type: 'file_preview',
            title: 'README.md',
            scope: 'session' as const,
            originSessionId: 's1',
            createdAt: 0,
            payload: { sourceKey: 'readme' },
          },
        ]],
      ]),
      activeTabIdBySession: new Map([['s1', 'preview-1']]),
    })

    render(<StageTabContent />)

    expect(screen.getByTestId('file-preview-tab')).toBeTruthy()
  })
})
