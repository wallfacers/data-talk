import type { ReactNode } from 'react'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { StageTabBar } from './stage-tab-bar'

vi.mock('lucide-react', async () => {
  const actual = await vi.importActual<typeof import('lucide-react')>('lucide-react')
  const FileTextIcon = (props: any) => <svg data-testid="file-text-icon" {...props} />
  const NetworkIcon = (props: any) => <svg data-testid="network-icon" {...props} />
  const SparklesIcon = (props: any) => <svg data-testid="sparkles-icon" {...props} />

  return {
    ...actual,
    FileTextIcon,
    NetworkIcon,
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
  ContextMenuItem: ({
    children,
    onClick,
    disabled,
  }: { children?: ReactNode; onClick?: () => void; disabled?: boolean }) => (
    <button type="button" onClick={disabled ? undefined : onClick} disabled={disabled}>
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

const erDesignerTabs = [
  { tabId: 'designer', title: 'ER 图设计器', type: 'er_designer' as const },
]

function mockTabOverflow() {
  const scroller = document.querySelector('.overflow-x-auto') as HTMLDivElement | null
  if (!scroller) return
  Object.defineProperty(scroller, 'clientWidth', { configurable: true, value: 120 })
  Object.defineProperty(scroller, 'scrollWidth', { configurable: true, value: 360 })
  fireEvent(window, new Event('resize'))
}

describe('StageTabBar', () => {
  it('scrolls the tab strip horizontally when the active tab is offscreen-right', () => {
    // Avoid scrollIntoView: when the whole stage panel sits behind a
    // translateX(100%) transform (closed state), scrollIntoView walks up to
    // the document and drags the closed panel back into view. The component
    // must instead mutate scrollLeft on the tab strip itself.
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

    const { container, rerender } = render(<StageTabBar tabs={tabs} activeId="left" />)

    // Pin the layout: scroll viewport is 120px wide, the right tab starts at
    // 200px. The effect should advance scrollLeft to (offsetLeft + width - clientWidth).
    const scroller = container.querySelector('.overflow-x-auto') as HTMLDivElement
    Object.defineProperty(scroller, 'clientWidth', { configurable: true, value: 120 })
    const rightTab = scroller.querySelector('[data-tab-id="right"]') as HTMLElement
    Object.defineProperty(rightTab, 'offsetLeft', { configurable: true, value: 200 })
    Object.defineProperty(rightTab, 'offsetWidth', { configurable: true, value: 80 })

    rerender(<StageTabBar tabs={tabs} activeId="right" />)

    expect(scrollIntoView).not.toHaveBeenCalled()
    expect(scroller.scrollLeft).toBe(200 + 80 - 120)
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

  it('uses a pointer cursor on the active SQL tab instead of a text cursor', () => {
    render(<StageTabBar tabs={tabs} activeId="active" />)

    const activeTab = screen.getByText('Active').closest('[data-tab-id="active"]') as HTMLElement
    const activeButton = within(activeTab).getByRole('tab', { name: 'Active' })

    expect(activeButton.className).toContain('cursor-pointer')
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

  it('disables context actions when there is no sibling tab in that direction', () => {
    render(
      <StageTabBar
        tabs={[{ tabId: 'only', title: 'Only', type: 'query_editor' }]}
        activeId="only"
        onClose={() => {}}
        onCloseOthers={() => {}}
        onCloseAll={() => {}}
        onCloseLeft={() => {}}
        onCloseRight={() => {}}
      />
    )

    const onlyTab = screen.getByText('Only').closest('[data-tab-id="only"]') as HTMLElement
    const menu = onlyTab.nextElementSibling as HTMLElement

    expect(within(menu).getByText('关闭其他')).toBeDisabled()
    expect(within(menu).getByText('关闭左侧标签页')).toBeDisabled()
    expect(within(menu).getByText('关闭右侧标签页')).toBeDisabled()
  })

  it('uses a file icon for file_preview tabs instead of the sparkle fallback', () => {
    render(<StageTabBar tabs={filePreviewTabs} activeId="preview" />)

    const previewTab = screen.getByText('README.md').closest('[data-tab-id="preview"]') as HTMLElement

    expect(within(previewTab).getByTestId('file-text-icon')).toBeTruthy()
    expect(within(previewTab).queryByTestId('sparkles-icon')).toBeNull()
  })

  it('uses the ER network icon for er_designer tabs instead of the sparkle fallback', () => {
    render(<StageTabBar tabs={erDesignerTabs} activeId="designer" />)

    const designerTab = screen.getByText('ER 图设计器').closest('[data-tab-id="designer"]') as HTMLElement

    expect(within(designerTab).getByTestId('network-icon')).toBeTruthy()
    expect(within(designerTab).queryByTestId('sparkles-icon')).toBeNull()
  })

  it('Close X on a tab calls onClose with the tabId (parent will detachFromWorkset)', () => {
    const onClose = vi.fn()
    render(<StageTabBar tabs={[{ tabId: 'qe-1', title: 'one' }]} activeId="qe-1" onClose={onClose} />)
    const closeButtons = screen.getAllByRole('button', { name: /关闭/ })
    fireEvent.click(closeButtons[0])
    expect(onClose).toHaveBeenCalledWith('qe-1')
  })
})
