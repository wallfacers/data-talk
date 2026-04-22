import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { translateMessage } from '@/i18n/messages'
import { SqlContextChip } from './sql-context-chip'

const context = {
  connectionId: 'conn-1',
  connectionName: 'Primary Connection',
  database: 'db_main',
  schema: 'public',
}

describe('SqlContextChip', () => {
  const t = (key: Parameters<typeof translateMessage>[1], values?: Record<string, string | number>) =>
    translateMessage('zh-CN', key, values)
  const onSetTabContext = vi.fn()
  const onResetTabContext = vi.fn()

  beforeEach(() => {
    onSetTabContext.mockReset()
    onResetTabContext.mockReset()
  })

  it('shows the inherited session badge and can pin the current context', () => {
    render(
      <SqlContextChip
        context={context}
        mode="session"
        onResetTabContext={onResetTabContext}
        onSetTabContext={onSetTabContext}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: new RegExp(t('stage.context.label.session')) }))
    fireEvent.click(screen.getByRole('menuitem', { name: t('stage.context.action.pinCurrent') }))

    expect(onSetTabContext).toHaveBeenCalledWith(context)
    expect(onResetTabContext).not.toHaveBeenCalled()
  })

  it('shows the override badge and can return to the session context', () => {
    render(
      <SqlContextChip
        context={context}
        mode="override"
        onResetTabContext={onResetTabContext}
        onSetTabContext={onSetTabContext}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: new RegExp(t('stage.context.label.override')) }))
    fireEvent.click(screen.getByRole('menuitem', { name: t('stage.context.action.useSession') }))

    expect(onResetTabContext).toHaveBeenCalledTimes(1)
    expect(onSetTabContext).not.toHaveBeenCalled()
  })
})
