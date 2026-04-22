import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SqlEditorToolbar } from './sql-editor-toolbar'

describe('SqlEditorToolbar', () => {
  const onRun = vi.fn()
  const onCancel = vi.fn()
  const onFormat = vi.fn()
  const onLimitChange = vi.fn()

  beforeEach(() => {
    onRun.mockReset()
    onCancel.mockReset()
    onFormat.mockReset()
    onLimitChange.mockReset()
  })

  it('shows only run and format on the left, with session context and limit on the right', () => {
    render(
      <SqlEditorToolbar
        canRun
        isRunning={false}
        limit={100}
        onCancel={onCancel}
        onFormat={onFormat}
        onLimitChange={onLimitChange}
        onRun={onRun}
        contextChip={<button type="button">Session context</button>}
      />,
    )

    expect(screen.getByRole('button', { name: /Run/i })).toBeTruthy()
    expect(screen.queryByRole('button', { name: /Cancel/i })).toBeNull()
    expect(screen.getByRole('button', { name: /Format/i })).toBeTruthy()
    expect(screen.queryByRole('button', { name: /Save/i })).toBeNull()
    expect(screen.queryByRole('button', { name: /More actions/i })).toBeNull()
    expect(screen.getByRole('button', { name: /Session context/i })).toBeTruthy()
    expect(screen.getByRole('combobox', { name: /Execution limit/i })).toHaveTextContent('100 rows')
  })

  it('shows cancel while running and forwards the primary actions', () => {
    render(
      <SqlEditorToolbar
        canRun={false}
        isRunning
        limit={null}
        onCancel={onCancel}
        onFormat={onFormat}
        onLimitChange={onLimitChange}
        onRun={onRun}
        contextChip={<button type="button">Tab override</button>}
      />,
    )

    expect(screen.getByRole('button', { name: /Run/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Cancel/i })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: /Format/i }))
    fireEvent.click(screen.getByRole('button', { name: /Cancel/i }))

    expect(onFormat).toHaveBeenCalledTimes(1)
    expect(onCancel).toHaveBeenCalledTimes(1)
  })
})
