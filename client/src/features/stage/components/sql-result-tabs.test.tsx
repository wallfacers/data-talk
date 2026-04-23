import type { ReactNode } from 'react'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { SqlResultTabs } from './sql-result-tabs'

vi.mock('@/i18n/use-i18n', () => ({
  useI18n: () => ({
    t: (key: string) =>
      ({
        'stage.menu.close': '关闭',
        'stage.menu.closeOthers': '关闭其他',
        'stage.menu.closeAll': '关闭全部',
        'stage.tabBar.moreTabs': '更多标签页',
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

const results = [
  {
    resultId: 'r1',
    kind: 'result_set' as const,
    title: 'Result 1',
    statementIndex: 0,
    statementText: 'select 1',
    columns: ['id'],
    rows: [[1]],
    rowCount: 1,
    executionMs: 9,
    truncated: false,
  },
  {
    resultId: 'r2',
    kind: 'error' as const,
    title: 'Error 2',
    statementIndex: 1,
    statementText: 'select * from nope',
    columns: [],
    rows: [],
    rowCount: 0,
    executionMs: 1,
    truncated: false,
    errorMessage: 'relation nope does not exist',
  },
]

function mockResultTabOverflow() {
  const scroller = document.querySelector('.overflow-x-auto') as HTMLDivElement | null
  if (!scroller) return
  Object.defineProperty(scroller, 'clientWidth', { configurable: true, value: 120 })
  Object.defineProperty(scroller, 'scrollWidth', { configurable: true, value: 360 })
  fireEvent(window, new Event('resize'))
}

describe('SqlResultTabs', () => {
  it('renders one tab button per result and marks the active one', () => {
    const { container } = render(
      <SqlResultTabs
        results={results}
        activeResultId="r1"
        onSelect={() => {}}
        onClose={() => {}}
        onCloseOthers={() => {}}
        onCloseAll={() => {}}
      />,
    )

    expect(screen.getByRole('tab', { name: 'Result 1' }).getAttribute('data-state')).toBe('active')
    expect(screen.getByRole('tab', { name: 'Error 2' }).getAttribute('data-state')).toBe('inactive')
    expect(container.querySelector('.overflow-x-auto')).toBeTruthy()
  })

  it('calls onSelect when a result tab is clicked', () => {
    const onSelect = vi.fn()
    render(
      <SqlResultTabs
        results={results}
        activeResultId="r1"
        onSelect={onSelect}
        onClose={() => {}}
        onCloseOthers={() => {}}
        onCloseAll={() => {}}
      />,
    )

    fireEvent.click(screen.getByRole('tab', { name: 'Error 2' }))

    expect(onSelect).toHaveBeenCalledWith('r2')
  })

  it('renders a destructive underline for the active error result', () => {
    render(
      <SqlResultTabs
        results={results}
        activeResultId="r2"
        onSelect={() => {}}
        onClose={() => {}}
        onCloseOthers={() => {}}
        onCloseAll={() => {}}
      />,
    )

    const errorTab = screen.getByRole('tab', { name: 'Error 2' })
    expect(errorTab.getAttribute('data-state')).toBe('active')
    expect(errorTab.className).toContain('data-[state=active]:border-b-destructive')
    expect(errorTab.className).toContain('text-destructive')
  })

  it('wires close actions and disables close others when there is only one sibling set', () => {
    const onClose = vi.fn()
    const onCloseOthers = vi.fn()
    const onCloseAll = vi.fn()

    const { rerender } = render(
      <SqlResultTabs
        results={results}
        activeResultId="r1"
        onSelect={() => {}}
        onClose={onClose}
        onCloseOthers={onCloseOthers}
        onCloseAll={onCloseAll}
      />,
    )

    const activeTab = screen.getByText('Result 1').closest('[data-result-id="r1"]') as HTMLElement
    fireEvent.click(within(activeTab).getByLabelText('关闭'))
    expect(onClose).toHaveBeenCalledWith('r1')

    const activeMenu = activeTab.nextElementSibling as HTMLElement
    fireEvent.click(within(activeMenu).getByText('关闭其他'))
    expect(onCloseOthers).toHaveBeenCalledWith('r1')

    fireEvent.click(within(activeMenu).getByText('关闭全部'))
    expect(onCloseAll).toHaveBeenCalledWith()

    rerender(
      <SqlResultTabs
        results={[results[0]]}
        activeResultId="r1"
        onSelect={() => {}}
        onClose={onClose}
        onCloseOthers={onCloseOthers}
        onCloseAll={onCloseAll}
      />,
    )

    const singleTab = screen.getByText('Result 1').closest('[data-result-id="r1"]') as HTMLElement
    const singleMenu = singleTab.nextElementSibling as HTMLElement
    expect(within(singleMenu).getByText('关闭其他')).toBeDisabled()
  })

  it('supports selecting and closing results from the overflow dropdown', () => {
    const onSelect = vi.fn()
    const onClose = vi.fn()

    render(
      <SqlResultTabs
        results={results}
        activeResultId="r1"
        onSelect={onSelect}
        onClose={onClose}
        onCloseOthers={() => {}}
        onCloseAll={() => {}}
      />,
    )
    mockResultTabOverflow()

    fireEvent.click(screen.getByTestId('sql-result-overflow-trigger'))
    const menu = screen.getByTestId('sql-result-overflow-menu')
    fireEvent.click(within(menu).getAllByLabelText('关闭')[0] as HTMLElement)
    expect(onClose).toHaveBeenCalledWith('r1')

    fireEvent.click(within(menu).getByText('Error 2'))
    expect(onSelect).toHaveBeenCalledWith('r2')
  })
})
