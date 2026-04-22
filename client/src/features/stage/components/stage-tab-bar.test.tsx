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
        'stage.tabBar.moreTabs': '更多标签页',
        'stage.tabBar.startPage': '开始页',
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

function mockTabOverflow() {
  const scroller = document.querySelector('.overflow-x-auto') as HTMLDivElement | null
  if (!scroller) return
  Object.defineProperty(scroller, 'clientWidth', { configurable: true, value: 120 })
  Object.defineProperty(scroller, 'scrollWidth', { configurable: true, value: 360 })
  fireEvent(window, new Event('resize'))
}

describe('StageTabBar', () => {
  it('scrolls the active tab into view when active tab changes', () => {
    const requestAnimationFrameSpy = vi
      .spyOn(window, 'requestAnimationFrame')
      .mockImplementation((callback: FrameRequestCallback) => {
        callback(0)
        return 0
      })
    const scrollIntoView = vi.fn()
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      value: scrollIntoView,
    })

    const { rerender } = render(<StageTabBar tabs={tabs} activeId="left" />)
    rerender(<StageTabBar tabs={tabs} activeId="right" />)

    expect(scrollIntoView).toHaveBeenCalled()
    requestAnimationFrameSpy.mockRestore()
  })

  it('renders underline-only tabs with active state, dirty indicator, and hover-close affordance', () => {
    const onClose = vi.fn()

    const { container } = render(<StageTabBar tabs={tabs} activeId="active" onClose={onClose} />)

    expect(container.querySelector('.overflow-x-auto')).toBeTruthy()

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

  it('shows overflow actions and supports selecting/closing tabs plus opening start page', () => {
    const onSelect = vi.fn()
    const onClose = vi.fn()
    const onOpenStartPage = vi.fn()

    render(
      <StageTabBar
        tabs={tabs}
        activeId="active"
        onSelect={onSelect}
        onClose={onClose}
        onOpenStartPage={onOpenStartPage}
      />,
    )
    mockTabOverflow()

    fireEvent.click(screen.getByTestId('stage-tab-start-button'))
    expect(onOpenStartPage).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByTestId('stage-tab-overflow-trigger'))
    const menu = screen.getByTestId('stage-tab-overflow-menu')
    fireEvent.click(within(menu).getAllByLabelText('关闭')[0] as HTMLElement)
    expect(onClose).toHaveBeenCalledWith('left')

    fireEvent.click(within(menu).getByText('Right'))
    expect(onSelect).toHaveBeenCalledWith('right')
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
