import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ErTableContextMenu } from '../ErTableContextMenu'

describe('<ErTableContextMenu>', () => {
  it('renders rename, add column, and delete table actions', () => {
    render(
      <ErTableContextMenu
        x={0}
        y={0}
        tableId="t1"
        onRename={vi.fn()}
        onAddColumn={vi.fn()}
        onDeleteTable={vi.fn()}
        onClose={vi.fn()}
      />,
    )

    expect(screen.getByRole('menuitem', { name: /rename|重命名/i })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: /add column|添加列/i })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: /delete table|删除表/i })).toBeInTheDocument()
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
