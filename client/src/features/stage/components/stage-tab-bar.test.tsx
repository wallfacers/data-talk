import type { ReactNode } from 'react'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { StageTabBar } from './stage-tab-bar'

vi.mock('lucide-react', async () => {
  const actual = await vi.importActual<typeof import('lucide-react')>('lucide-react')
  const FileTextIcon = (props: any) => <svg data-testid="file-text-icon" {...props} />
  const SparklesIcon = (props: any) => <svg data-testid="sparkles-icon" {...props} />

  return {
    ...actual,
    FileTextIcon,
    SparklesIcon,
  }
})

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    t: (key: string) =>
      ({
        'stage.menu.close': '关闭',
        'stage.menu.closeOthers': '关闭其他',
        'stage.menu.closeAll': '关闭全部',
        'stage.menu.closeLeft': '关闭左侧标签页',
        'stage.menu.closeRight': '关闭右侧标签页',
      })[key] ?? key,
  }),
}))

vi.mock('@/components/ui/context-menu', () => ({
  ContextMenu: ({ children }: { children?: ReactNode }) => <>{children}</>,
  ContextMenuTrigger: ({ render, children }: { render?: ReactNode; children?: ReactNode }) => <>{render ?? children}</>,
  ContextMenuContent: ({ children }: { children?: ReactNode }) => <div data-testid="context-menu-content">{children}</div>,
  ContextMenuItem: ({ children, onSelect }: { children?: ReactNode; onSelect?: () => void }) => (
    <button type="button" onClick={onSelect}>
      {children}
    </button>
  ),
  ContextMenuSeparator: () => <span data-testid="context-menu-separator" />,
}))

const tabs = [
  { tabId: 'left', title: 'Left', type: 'query_editor' as const },
  { tabId: 'active', title: 'Active', type: 'query_editor' as const, dirty: true },
  { tabId: 'right', title: 'Right', type: 'er_canvas' as const },
]

const filePreviewTabs = [
  { tabId: 'preview', title: 'README.md', type: 'file_preview' as const },
]

describe('StageTabBar', () => {
  it('renders underline-only tabs with active state, dirty indicator, and hover-close affordance', () => {
    const onClose = vi.fn()

    const { container } = render(<StageTabBar tabs={tabs} activeId="active" onClose={onClose} />)

    expect(container.firstElementChild?.className).toContain('overflow-x-auto')

    const activeTab = screen.getByText('Active').closest('[data-tab-id="active"]') as HTMLElement
    const activeButton = within(activeTab).getByRole('tab', { name: 'Active' })
    expect(activeButton.getAttribute('data-state')).toBe('active')
    expect(activeButton.className).toContain('border-b-2')
    expect(activeButton.className).toContain('data-[state=active]:border-b-foreground')
    expect(within(activeTab).getByTestId('dirty-indicator')).toBeTruthy()

    const inactiveTab = screen.getByText('Left').closest('[data-tab-id="left"]') as HTMLElement
    const inactiveButton = within(inactiveTab).getByRole('tab', { name: 'Left' })
    expect(inactiveButton.getAttribute('data-state')).toBe('inactive')
    expect(inactiveButton.className).toContain('border-b-transparent')

    const inactiveClose = within(inactiveTab).getByLabelText('关闭')
    expect(inactiveClose.className).toContain('opacity-0')
    expect(inactiveClose.className).toContain('group-hover/tab:opacity-100')

    const activeClose = within(activeTab).getByLabelText('关闭')
    expect(activeClose.className).toContain('opacity-100')

    fireEvent.click(activeClose)
    expect(onClose).toHaveBeenCalledWith('active')
  })

  it('keeps the context menu actions wired to the active tab', () => {
    const onClose = vi.fn()
    const onCloseOthers = vi.fn()
    const onCloseAll = vi.fn()
    const onCloseLeft = vi.fn()
    const onCloseRight = vi.fn()

    render(
      <StageTabBar
        tabs={tabs}
        activeId="active"
        onClose={onClose}
        onCloseOthers={onCloseOthers}
        onCloseAll={onCloseAll}
        onCloseLeft={onCloseLeft}
        onCloseRight={onCloseRight}
      />
    )

    const activeTab = screen.getByText('Active').closest('[data-tab-id="active"]') as HTMLElement
    const menu = activeTab.nextElementSibling as HTMLElement

    fireEvent.click(within(menu).getByText('关闭其他'))
    expect(onCloseOthers).toHaveBeenCalledWith('active')

    fireEvent.click(within(menu).getByText('关闭全部'))
    expect(onCloseAll).toHaveBeenCalledWith()

    fireEvent.click(within(menu).getByText('关闭左侧标签页'))
    expect(onCloseLeft).toHaveBeenCalledWith('active')

    fireEvent.click(within(menu).getByText('关闭右侧标签页'))
    expect(onCloseRight).toHaveBeenCalledWith('active')
  })

  it('uses a file icon for file_preview tabs instead of the sparkle fallback', () => {
    render(<StageTabBar tabs={filePreviewTabs} activeId="preview" />)

    const previewTab = screen.getByText('README.md').closest('[data-tab-id="preview"]') as HTMLElement

    expect(within(previewTab).getByTestId('file-text-icon')).toBeTruthy()
    expect(within(previewTab).queryByTestId('sparkles-icon')).toBeNull()
  })
})
