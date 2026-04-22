import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { translateMessage } from '@/i18n/messages'
import { SqlEditorToolbar } from './sql-editor-toolbar'

describe('SqlEditorToolbar', () => {
  const t = (key: Parameters<typeof translateMessage>[1], values?: Record<string, string | number>) =>
    translateMessage('zh-CN', key, values)
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
        contextChip={<button type="button">{t('stage.context.label.session')}</button>}
      />,
    )

    expect(screen.getByRole('button', { name: t('stage.toolbar.run') })).toBeTruthy()
    expect(screen.queryByRole('button', { name: t('stage.toolbar.cancel') })).toBeNull()
    expect(screen.getByRole('button', { name: t('stage.toolbar.format') })).toBeTruthy()
    expect(screen.queryByRole('button', { name: /Save/i })).toBeNull()
    expect(screen.queryByRole('button', { name: /More actions/i })).toBeNull()
    expect(screen.getByRole('button', { name: t('stage.context.label.session') })).toBeTruthy()
    expect(screen.getByRole('combobox', { name: t('stage.limit.aria') })).toHaveTextContent(t('stage.limit.rows', { count: 100 }))
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
        contextChip={<button type="button">{t('stage.context.label.override')}</button>}
      />,
    )

    expect(screen.getByRole('button', { name: t('stage.toolbar.run') })).toBeDisabled()
    expect(screen.getByRole('button', { name: t('stage.toolbar.cancel') })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.format') }))
    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.cancel') }))

    expect(onFormat).toHaveBeenCalledTimes(1)
    expect(onCancel).toHaveBeenCalledTimes(1)
  })
})
