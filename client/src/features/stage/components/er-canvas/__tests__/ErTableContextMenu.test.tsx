import { fireEvent, render, screen } from '@testing-library/react'
import type { ComponentProps } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { ErTableContextMenu } from '../ErTableContextMenu'

describe('<ErTableContextMenu>', () => {
  function renderMenu(overrides: Partial<ComponentProps<typeof ErTableContextMenu>> = {}) {
    const props: ComponentProps<typeof ErTableContextMenu> = {
      x: 0,
      y: 0,
      tableId: 't1',
      onRename: vi.fn(),
      onAddColumn: vi.fn(),
      onDeleteTable: vi.fn(),
      onClose: vi.fn(),
      ...overrides,
    }

    return {
      ...render(<ErTableContextMenu {...props} />),
      props,
    }
  }

  it('renders three menu items with leading icons', () => {
    renderMenu()

    const items = screen.getAllByRole('menuitem')

    expect(items).toHaveLength(3)
    expect(items[0]).toHaveAccessibleName(/rename|重命名/i)
    expect(items[1]).toHaveAccessibleName(/add column|添加列/i)
    expect(items[2]).toHaveAccessibleName(/delete table|删除表/i)
    items.forEach((item) => {
      expect(item.firstElementChild?.tagName.toLowerCase()).toBe('svg')
    })
  })

  it('focuses the first item when opened', () => {
    renderMenu()

    expect(screen.getAllByRole('menuitem')[0]).toHaveFocus()
  })

  it('cycles focus through menu items with arrow keys', () => {
    renderMenu()

    const items = screen.getAllByRole('menuitem')

    fireEvent.keyDown(items[0], { key: 'ArrowDown' })
    expect(items[1]).toHaveFocus()

    fireEvent.keyDown(items[1], { key: 'ArrowDown' })
    expect(items[2]).toHaveFocus()

    fireEvent.keyDown(items[2], { key: 'ArrowDown' })
    expect(items[0]).toHaveFocus()

    fireEvent.keyDown(items[0], { key: 'ArrowUp' })
    expect(items[2]).toHaveFocus()
  })

  it('triggers the focused action with Enter and closes', () => {
    const { props } = renderMenu()

    const items = screen.getAllByRole('menuitem')
    fireEvent.keyDown(items[0], { key: 'ArrowDown' })
    fireEvent.keyDown(items[1], { key: 'Enter' })

    expect(props.onAddColumn).toHaveBeenCalledWith('t1')
    expect(props.onClose).toHaveBeenCalledTimes(1)
    expect(props.onRename).not.toHaveBeenCalled()
    expect(props.onDeleteTable).not.toHaveBeenCalled()
  })

  it('closes with Escape without invoking actions', () => {
    const { props } = renderMenu()

    fireEvent.keyDown(screen.getAllByRole('menuitem')[0], { key: 'Escape' })

    expect(props.onClose).toHaveBeenCalledTimes(1)
    expect(props.onRename).not.toHaveBeenCalled()
    expect(props.onAddColumn).not.toHaveBeenCalled()
    expect(props.onDeleteTable).not.toHaveBeenCalled()
  })

  it('marks the delete item as dangerous', () => {
    renderMenu()

    expect(screen.getByRole('menuitem', { name: /delete table|删除表/i })).toHaveAttribute(
      'data-er-menu-variant',
      'danger',
    )
  })

  it('closes on outside mousedown', () => {
    const onClose = vi.fn()
    render(
      <div>
        <ErTableContextMenu
          x={0}
          y={0}
          tableId="t1"
          onRename={vi.fn()}
          onAddColumn={vi.fn()}
          onDeleteTable={vi.fn()}
          onClose={onClose}
        />
        <button type="button">Outside</button>
      </div>,
    )

    fireEvent.mouseDown(screen.getByRole('button', { name: 'Outside' }))

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('emits action callbacks with tableId and closes the menu', () => {
    const onRename = vi.fn()
    const onAddColumn = vi.fn()
    const onDeleteTable = vi.fn()
    const onClose = vi.fn()

    const { rerender } = render(
      <ErTableContextMenu
        x={0}
        y={0}
        tableId="t1"
        onRename={onRename}
        onAddColumn={onAddColumn}
        onDeleteTable={onDeleteTable}
        onClose={onClose}
      />,
    )
    fireEvent.click(screen.getByRole('menuitem', { name: /rename|重命名/i }))
    expect(onRename).toHaveBeenCalledWith('t1')
    expect(onClose).toHaveBeenCalledTimes(1)

    rerender(
      <ErTableContextMenu
        x={0}
        y={0}
        tableId="t1"
        onRename={onRename}
        onAddColumn={onAddColumn}
        onDeleteTable={onDeleteTable}
        onClose={onClose}
      />,
    )
    fireEvent.click(screen.getByRole('menuitem', { name: /add column|添加列/i }))
    expect(onAddColumn).toHaveBeenCalledWith('t1')

    rerender(
      <ErTableContextMenu
        x={0}
        y={0}
        tableId="t1"
        onRename={onRename}
        onAddColumn={onAddColumn}
        onDeleteTable={onDeleteTable}
        onClose={onClose}
      />,
    )
    fireEvent.click(screen.getByRole('menuitem', { name: /delete table|删除表/i }))
    expect(onDeleteTable).toHaveBeenCalledWith('t1')
  })
})
