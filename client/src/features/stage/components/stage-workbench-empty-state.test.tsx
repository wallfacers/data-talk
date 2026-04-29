import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { StageWorkbenchEmptyState } from './stage-workbench-empty-state'

describe('StageWorkbenchEmptyState', () => {
  it('renders structured instrument tiles for enabled and pending stage tools', () => {
    render(<StageWorkbenchEmptyState onOpenSqlEditor={vi.fn()} onOpenErDesigner={vi.fn()} />)

    const sqlTile = screen.getByRole('button', { name: /SQL 编辑器/ })
    const erTile = screen.getByRole('button', { name: /ER 图设计器/ })

    expect(sqlTile.className).toContain('rounded-lg')
    expect(sqlTile.className).toContain('hover:bg-interaction-hover')
    expect(erTile.className).toContain('rounded-lg')
    expect(erTile).not.toBeDisabled()
  })

  it('keeps the SQL and ER designer tiles interactive while pending tiles stay disabled', () => {
    const onOpenSqlEditor = vi.fn()
    const onOpenErDesigner = vi.fn()

    render(<StageWorkbenchEmptyState onOpenSqlEditor={onOpenSqlEditor} onOpenErDesigner={onOpenErDesigner} />)

    fireEvent.click(screen.getByRole('button', { name: /SQL 编辑器/ }))
    fireEvent.click(screen.getByRole('button', { name: /ER 图设计器/ }))

    expect(onOpenSqlEditor).toHaveBeenCalledTimes(1)
    expect(onOpenErDesigner).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: /报表/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Dashboard/ })).toBeDisabled()
  })

  it('disables the SQL tile when no open handler is provided', () => {
    render(<StageWorkbenchEmptyState />)

    expect(screen.getByRole('button', { name: /SQL 编辑器/ })).toBeDisabled()
  })
})
