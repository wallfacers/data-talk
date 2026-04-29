import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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
  { id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' },
  { id: 'conn-2', name: 'Analytics', kind: 'mysql', databaseName: 'analytics' },
]
const connectionTargetsByConnectionId = {
  'conn-1': {
    databases: ['db_main', 'warehouse'],
    schemas: ['public', 'reporting'],
  },
  'conn-2': {
    databases: ['analytics'],
    schemas: [],
  },
}

async function chooseSelectOption(fieldLabel: string, optionLabel: string) {
  fireEvent.click(screen.getByRole('combobox', { name: fieldLabel }))
  const option = await screen.findByRole('option', { name: optionLabel })
  fireEvent.mouseMove(option)
  fireEvent.pointerEnter(option, { pointerType: 'mouse' })
  fireEvent.click(option)
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
        connections={connections}
        connectionTargetsByConnectionId={connectionTargetsByConnectionId}
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
        connectionTargetsByConnectionId={connectionTargetsByConnectionId}
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
        connectionTargetsByConnectionId={connectionTargetsByConnectionId}
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
        connectionTargetsByConnectionId={connectionTargetsByConnectionId}
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
        connectionTargetsByConnectionId={connectionTargetsByConnectionId}
        onResetTabContext={onResetTabContext}
        onSetTabContext={onSetTabContext}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))

    expect(screen.getByRole('combobox', { name: t('stage.context.field.connection') }).textContent).toContain('Primary Connection')
    expect(screen.getByRole('combobox', { name: t('stage.context.field.connection') }).textContent).not.toContain('conn-1')
  })

  it('echoes the current database and schema through editable select triggers', () => {
    render(
      <SqlContextChip
        context={context}
        mode="session"
        connections={connections}
        connectionTargetsByConnectionId={connectionTargetsByConnectionId}
        onResetTabContext={onResetTabContext}
        onSetTabContext={onSetTabContext}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))

    expect(screen.getByRole('combobox', { name: t('stage.context.field.database') })).toHaveTextContent('db_main')
    expect(screen.getByRole('combobox', { name: t('stage.context.field.schema') })).toHaveTextContent('public')
  })

  it('syncs session context updates into database and schema selects while open', () => {
    const { rerender } = render(
      <SqlContextChip
        context={null}
        mode="session"
        connections={connections}
        connectionTargetsByConnectionId={connectionTargetsByConnectionId}
        onResetTabContext={onResetTabContext}
        onSetTabContext={onSetTabContext}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))

    rerender(
      <SqlContextChip
        context={context}
        mode="session"
        connections={connections}
        connectionTargetsByConnectionId={connectionTargetsByConnectionId}
        onResetTabContext={onResetTabContext}
        onSetTabContext={onSetTabContext}
      />,
    )

    expect(screen.getByRole('combobox', { name: t('stage.context.field.database') })).toHaveTextContent('db_main')
    expect(screen.getByRole('combobox', { name: t('stage.context.field.schema') })).toHaveTextContent('public')
  })

  it('hides the schema field for connections whose kind does not use schemas', () => {
    render(
      <SqlContextChip
        context={{
          connectionId: 'conn-2',
          connectionName: 'Analytics',
          database: 'analytics',
          schema: null,
        }}
        mode="session"
        connections={connections}
        connectionTargetsByConnectionId={connectionTargetsByConnectionId}
        onResetTabContext={onResetTabContext}
        onSetTabContext={onSetTabContext}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))

    expect(screen.getByRole('combobox', { name: t('stage.context.field.database') })).toHaveTextContent('analytics')
    expect(screen.queryByRole('combobox', { name: t('stage.context.field.schema') })).toBeNull()
    expect(screen.queryByText(t('stage.context.field.schema'))).toBeNull()
  })

  it('keeps schema visible when an existing schema value must still be echoed', () => {
    render(
      <SqlContextChip
        context={{
          connectionId: 'conn-2',
          connectionName: 'Analytics',
          database: 'analytics',
          schema: 'legacy_schema',
        }}
        mode="session"
        connections={connections}
        connectionTargetsByConnectionId={connectionTargetsByConnectionId}
        onResetTabContext={onResetTabContext}
        onSetTabContext={onSetTabContext}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))

    expect(screen.getByRole('combobox', { name: t('stage.context.field.schema') })).toHaveTextContent('legacy_schema')
  })

  it('shows database options only for the selected connection target set', async () => {
    render(
      <SqlContextChip
        context={context}
        mode="session"
        connections={connections}
        connectionTargetsByConnectionId={connectionTargetsByConnectionId}
        onResetTabContext={onResetTabContext}
        onSetTabContext={onSetTabContext}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))
    fireEvent.click(screen.getByRole('combobox', { name: t('stage.context.field.database') }))

    expect(await screen.findByText('warehouse')).toBeTruthy()
    expect(screen.queryByText('analytics')).toBeNull()
  })

  it('switches database and schema options to the selected connection targets', async () => {
    const postgresConnections = [
      { id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' },
      { id: 'conn-2', name: 'Analytics', kind: 'postgres', databaseName: 'analytics' },
    ]
    const postgresTargets = {
      'conn-1': {
        databases: ['db_main', 'warehouse'],
        schemas: ['public', 'reporting'],
      },
      'conn-2': {
        databases: ['analytics', 'finance'],
        schemas: ['core', 'mart'],
      },
    }

    render(
      <SqlContextChip
        context={context}
        mode="session"
        connections={postgresConnections}
        connectionTargetsByConnectionId={postgresTargets}
        onResetTabContext={onResetTabContext}
        onSetTabContext={onSetTabContext}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))
    await chooseSelectOption(t('stage.context.field.connection'), 'Analytics')

    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: t('stage.context.field.connection') })).toHaveTextContent('Analytics')
    })
    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: t('stage.context.field.database') })).toHaveTextContent('analytics')
    })
    expect(screen.getByRole('combobox', { name: t('stage.context.field.schema') })).toHaveTextContent(
      t('stage.context.value.empty'),
    )

    fireEvent.click(screen.getByRole('combobox', { name: t('stage.context.field.database') }))
    await waitFor(() => expect(screen.getByRole('option', { name: 'finance' })).toBeTruthy())
    expect(screen.queryByRole('option', { name: 'warehouse' })).toBeNull()
    expect(screen.queryByRole('option', { name: 'db_main' })).toBeNull()

    fireEvent.click(screen.getByRole('combobox', { name: t('stage.context.field.schema') }))
    await waitFor(() => expect(screen.getByRole('option', { name: 'core' })).toBeTruthy())
    expect(screen.queryByRole('option', { name: 'public' })).toBeNull()
    expect(screen.queryByRole('option', { name: 'reporting' })).toBeNull()
  })

  it('applies the manually selected connection, database, and schema as the tab context override', async () => {
    const postgresConnections = [
      { id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' },
      { id: 'conn-2', name: 'Analytics', kind: 'postgres', databaseName: 'analytics' },
    ]
    const postgresTargets = {
      'conn-1': {
        databases: ['db_main', 'warehouse'],
        schemas: ['public', 'reporting'],
      },
      'conn-2': {
        databases: ['analytics', 'finance'],
        schemas: ['core', 'mart'],
      },
    }

    render(
      <SqlContextChip
        context={context}
        mode="session"
        connections={postgresConnections}
        connectionTargetsByConnectionId={postgresTargets}
        onResetTabContext={onResetTabContext}
        onSetTabContext={onSetTabContext}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: t('stage.context.tooltip.button') }))
    await chooseSelectOption(t('stage.context.field.connection'), 'Analytics')
    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: t('stage.context.field.connection') })).toHaveTextContent('Analytics')
    })
    await chooseSelectOption(t('stage.context.field.database'), 'finance')
    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: t('stage.context.field.database') })).toHaveTextContent('finance')
    })
    await chooseSelectOption(t('stage.context.field.schema'), 'mart')
    fireEvent.click(screen.getByRole('button', { name: t('stage.context.action.applyOverride') }))

    expect(onSetTabContext).toHaveBeenCalledWith({
      connectionId: 'conn-2',
      connectionName: 'Analytics',
      database: 'finance',
      schema: 'mart',
    })
  })
})
