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

  beforeEach(() => {
    onRun.mockReset()
    onCancel.mockReset()
    onFormat.mockReset()
  })

  it('shows only run and format on the left, with context controls on the right', () => {
    render(
      <SqlEditorToolbar
        canRun
        isRunning={false}
        onCancel={onCancel}
        onFormat={onFormat}
        onRun={onRun}
        contextControls={
          <>
            <span>固定 session 上下文</span>
            <span>连接</span>
            <span>数据库</span>
            <span>Schema</span>
            <span>分页限制</span>
          </>
        }
      />,
    )

    expect(screen.getByRole('button', { name: t('stage.toolbar.run') })).toBeTruthy()
    expect(screen.queryByRole('button', { name: t('stage.toolbar.cancel') })).toBeNull()
    expect(screen.getByRole('button', { name: t('stage.toolbar.format') })).toBeTruthy()
    expect(screen.queryByRole('button', { name: /Save/i })).toBeNull()
    expect(screen.queryByRole('button', { name: /More actions/i })).toBeNull()
    expect(screen.getByTestId('sql-editor-toolbar').textContent).toMatch(
      /固定 session 上下文.*连接.*数据库.*Schema.*分页限制/,
    )
  })

  it('shows cancel while running and forwards the primary actions', () => {
    render(
      <SqlEditorToolbar
        canRun={false}
        isRunning
        onCancel={onCancel}
        onFormat={onFormat}
        onRun={onRun}
        contextControls={<span>{t('stage.context.label.override')}</span>}
      />,
    )

    expect(screen.queryByRole('button', { name: t('stage.toolbar.run') })).toBeNull()
    expect(screen.getByRole('button', { name: t('stage.toolbar.cancel') })).toBeEnabled()

    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.format') }))
    fireEvent.click(screen.getByRole('button', { name: t('stage.toolbar.cancel') }))

    expect(onFormat).toHaveBeenCalledTimes(1)
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('renders Explain button when onExplain prop provided', () => {
    const onExplain = vi.fn()

    render(
      <SqlEditorToolbar
        canRun
        isRunning={false}
        onCancel={onCancel}
        onFormat={onFormat}
        onRun={onRun}
        canExplain
        onExplain={onExplain}
        contextControls={<span>{t('stage.context.label.session')}</span>}
      />,
    )

    expect(screen.getByRole('button', { name: t('stage.toolbar.explain') })).toBeEnabled()
  })

  it('Explain button is disabled when canExplain is false', () => {
    const onExplain = vi.fn()

    render(
      <SqlEditorToolbar
        canRun
        isRunning={false}
        onCancel={onCancel}
        onFormat={onFormat}
        onRun={onRun}
        canExplain={false}
        onExplain={onExplain}
        contextControls={<span>{t('stage.context.label.session')}</span>}
      />,
    )

    expect(screen.getByRole('button', { name: t('stage.toolbar.explain') })).toBeDisabled()
  })
})
