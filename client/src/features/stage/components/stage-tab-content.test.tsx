import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StageTabContent } from './stage-tab-content'
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
    useStageStore.setState({
      tabs: [],
      openTabIds: new Set(),
      openTabIdsOrdered: [],
      activeTabId: null,
    } as never)
  })

  it('renders SqlWorkbenchTab when the active tab is query_editor', () => {
    useStageStore.setState({
      tabs: [{
        tabId: 'sql-1',
        type: 'query_editor',
        title: 'SQL',
        scope: 'session' as const,
        originSessionId: 's1',
        createdAt: 0,
        payload: {},
      }],
      activeTabId: 'sql-1',
      openTabIds: new Set(['sql-1']),
      openTabIdsOrdered: ['sql-1'],
    } as never)

    render(<StageTabContent />)

    expect(screen.getByTestId('sql-workbench-tab')).toBeTruthy()
  })

  it('renders FilePreviewTab when the active tab is file_preview', () => {
    useStageStore.setState({
      tabs: [{
        tabId: 'preview-1',
        type: 'file_preview',
        title: 'README.md',
        scope: 'session' as const,
        originSessionId: 's1',
        createdAt: 0,
        payload: { sourceKey: 'readme' },
      }],
      activeTabId: 'preview-1',
      openTabIds: new Set(['preview-1']),
      openTabIdsOrdered: ['preview-1'],
    } as never)

    render(<StageTabContent />)

    expect(screen.getByTestId('file-preview-tab')).toBeTruthy()
  })
})
