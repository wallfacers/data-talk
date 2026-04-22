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
const connections = [
  { id: 'conn-1', name: 'Primary Connection', databaseName: 'db_main' },
  { id: 'conn-2', name: 'Analytics', databaseName: 'analytics' },
]

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
        connections={connections}
        databaseOptions={['db_main', 'analytics']}
        schemaOptions={['public', 'reporting']}
        onResetTabContext={onResetTabContext}
        onSetTabContext={onSetTabContext}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))
    fireEvent.click(screen.getByRole('button', { name: t('stage.context.action.pinCurrent') }))

    expect(onSetTabContext).toHaveBeenCalledWith(context)
    expect(onResetTabContext).not.toHaveBeenCalled()
  })

  it('can apply a manual override while in session mode', () => {
    render(
      <SqlContextChip
        context={context}
        mode="session"
        connections={connections}
        databaseOptions={['db_main', 'analytics']}
        schemaOptions={['public', 'reporting']}
        onResetTabContext={onResetTabContext}
        onSetTabContext={onSetTabContext}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))
    fireEvent.click(screen.getByRole('button', { name: t('stage.context.action.applyOverride') }))

    expect(onSetTabContext).toHaveBeenCalledWith(context)
    expect(onResetTabContext).not.toHaveBeenCalled()
  })

  it('shows the override badge and can return to the session context', () => {
    render(
      <SqlContextChip
        context={context}
        mode="override"
        connections={connections}
        databaseOptions={['db_main', 'analytics']}
        schemaOptions={['public', 'reporting']}
        onResetTabContext={onResetTabContext}
        onSetTabContext={onSetTabContext}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))
    fireEvent.click(screen.getByRole('button', { name: t('stage.context.action.useSession') }))

    expect(onResetTabContext).toHaveBeenCalledTimes(1)
    expect(onSetTabContext).not.toHaveBeenCalled()
  })

  it('resolves connection name from options when context only has id', () => {
    render(
      <SqlContextChip
        context={{
          connectionId: 'conn-2',
          connectionName: null,
          database: 'analytics',
          schema: null,
        }}
        mode="session"
        connections={connections}
        databaseOptions={['db_main', 'analytics']}
        schemaOptions={['public', 'reporting']}
        onResetTabContext={onResetTabContext}
        onSetTabContext={onSetTabContext}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))
    fireEvent.click(screen.getByRole('button', { name: t('stage.context.action.pinCurrent') }))

    expect(onSetTabContext).toHaveBeenCalledWith({
      connectionId: 'conn-2',
      connectionName: 'Analytics',
      database: 'analytics',
      schema: null,
    })
  })

  it('shows connection name instead of id in editable select trigger', () => {
    render(
      <SqlContextChip
        context={{
          connectionId: 'conn-1',
          connectionName: null,
          database: 'db_main',
          schema: null,
        }}
        mode="session"
        connections={connections}
        databaseOptions={['db_main', 'analytics']}
        schemaOptions={['public', 'reporting']}
        onResetTabContext={onResetTabContext}
        onSetTabContext={onSetTabContext}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))

    expect(screen.getByRole('combobox', { name: t('stage.context.field.connection') }).textContent).toContain('Primary Connection')
    expect(screen.getByRole('combobox', { name: t('stage.context.field.connection') }).textContent).not.toContain('conn-1')
  })
})
