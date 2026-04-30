import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SqlContextToolbarControls } from './sql-context-toolbar-controls'

const toastErrorMock = vi.hoisted(() => vi.fn())

vi.mock('sonner', () => ({
  toast: {
    error: toastErrorMock,
  },
}))

const context = {
  connectionId: 'conn-1',
  connectionName: 'Primary Connection',
  database: 'db_main',
  schema: 'public',
}

const connections = [
  { id: 'conn-1', name: 'Primary Connection', kind: 'postgres', databaseName: 'db_main' },
  { id: 'conn-2', name: 'Analytics', kind: 'mysql', databaseName: 'analytics' },
  { id: 'conn-3', name: 'Local SQLite', kind: 'sqlite', databaseName: '/tmp/app.db' },
]

const targets = {
  databases: ['db_main', 'warehouse'],
  schemas: ['public', 'reporting'],
}

async function chooseSelectOption(fieldLabel: string, optionLabel: string) {
  fireEvent.click(screen.getByRole('combobox', { name: fieldLabel }))
  const option = await screen.findByRole('option', { name: optionLabel })
  fireEvent.mouseMove(option)
  fireEvent.pointerEnter(option, { pointerType: 'mouse' })
  fireEvent.click(option)
}

function renderControls(overrides: Partial<Parameters<typeof SqlContextToolbarControls>[0]> = {}) {
  return render(
    <SqlContextToolbarControls
      useSessionContext
      context={context}
      connections={connections}
      targets={targets}
      limit={100}
      onUseSessionContextChange={vi.fn()}
      onConnectionChange={vi.fn()}
      onDatabaseChange={vi.fn()}
      onSchemaChange={vi.fn()}
      onLimitChange={vi.fn()}
      onOpenConnections={vi.fn().mockResolvedValue(undefined)}
      onOpenTargets={vi.fn().mockResolvedValue(undefined)}
      {...overrides}
    />,
  )
}

describe('SqlContextToolbarControls', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders visible labels in toolbar order', () => {
    renderControls()

    expect(screen.getByLabelText('固定 session 上下文')).toBeInTheDocument()
    expect(screen.getByLabelText('连接')).toBeInTheDocument()
    expect(screen.getByLabelText('数据库')).toBeInTheDocument()
    expect(screen.getByLabelText('Schema')).toBeInTheDocument()
    expect(screen.getByLabelText('分页限制')).toBeInTheDocument()
    expect(screen.getByTestId('sql-context-toolbar-controls').textContent).toMatch(
      /固定 session 上下文.*连接.*数据库.*Schema.*分页限制/,
    )
  })

  it('shows latest values but locks context selects while following session context', () => {
    renderControls()

    expect(screen.getByRole('combobox', { name: '连接' })).toHaveTextContent('Primary Connection')
    expect(screen.getByRole('combobox', { name: '数据库' })).toHaveTextContent('db_main')
    expect(screen.getByRole('combobox', { name: 'Schema' })).toHaveTextContent('public')
    expect(screen.getByRole('combobox', { name: '连接' })).toBeDisabled()
    expect(screen.getByRole('combobox', { name: '数据库' })).toBeDisabled()
    expect(screen.getByRole('combobox', { name: 'Schema' })).toBeDisabled()
    expect(screen.getByRole('combobox', { name: '分页限制' })).toBeEnabled()
  })

  it('enables context selects and emits manual changes when not following session context', async () => {
    const onUseSessionContextChange = vi.fn()
    const onConnectionChange = vi.fn()

    renderControls({
      useSessionContext: false,
      onUseSessionContextChange,
      onConnectionChange,
    })

    expect(screen.getByRole('combobox', { name: '连接' })).toBeEnabled()
    expect(screen.getByRole('combobox', { name: '数据库' })).toBeEnabled()
    expect(screen.getByRole('combobox', { name: 'Schema' })).toBeEnabled()

    fireEvent.click(screen.getByRole('switch', { name: '固定 session 上下文' }))
    expect(onUseSessionContextChange).toHaveBeenCalledWith(true)

    await chooseSelectOption('连接', 'Analytics')
    expect(onConnectionChange).toHaveBeenCalledWith('conn-2')
  })

  it('refreshes connections every time the connection dropdown opens', async () => {
    const onOpenConnections = vi.fn().mockResolvedValue(undefined)
    renderControls({
      useSessionContext: false,
      onOpenConnections,
    })
    const connectionSelect = screen.getByRole('combobox', { name: '连接' })

    fireEvent.click(connectionSelect)
    await waitFor(() => expect(onOpenConnections).toHaveBeenCalledTimes(1))
    fireEvent.keyDown(connectionSelect, { key: 'Escape' })
    fireEvent.click(connectionSelect)

    await waitFor(() => expect(onOpenConnections).toHaveBeenCalledTimes(2))
  })

  it('toasts target refresh failures without writing a manual database change', async () => {
    const onOpenTargets = vi.fn().mockRejectedValue(new Error('connection refused'))
    const onDatabaseChange = vi.fn()
    renderControls({
      useSessionContext: false,
      onOpenTargets,
      onDatabaseChange,
    })

    fireEvent.click(screen.getByRole('combobox', { name: '数据库' }))

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalledWith('数据库上下文刷新失败')
    })
    expect(onDatabaseChange).not.toHaveBeenCalled()
  })

  it('does not request target refresh when no connection is selected', async () => {
    const onOpenTargets = vi.fn().mockResolvedValue(undefined)
    renderControls({
      useSessionContext: false,
      context: null,
      targets: null,
      onOpenTargets,
    })

    fireEvent.click(screen.getByRole('combobox', { name: '数据库' }))

    expect(onOpenTargets).not.toHaveBeenCalled()
  })

  it('hides schema selection for MySQL connections because database is the selectable namespace', () => {
    renderControls({
      useSessionContext: false,
      context: {
        connectionId: 'conn-2',
        connectionName: 'Analytics',
        database: 'analytics',
        schema: null,
      },
      targets: {
        databases: ['analytics', 'warehouse'],
        schemas: ['ignored_mysql_schema'],
      },
    })

    expect(screen.getByRole('combobox', { name: '数据库' })).toHaveTextContent('analytics')
    expect(screen.queryByRole('combobox', { name: 'Schema' })).not.toBeInTheDocument()
  })

  it('hides schema selection for SQLite connections because context is file-scoped', () => {
    renderControls({
      useSessionContext: false,
      context: {
        connectionId: 'conn-3',
        connectionName: 'Local SQLite',
        database: '/tmp/app.db',
        schema: null,
      },
      targets: {
        databases: ['/tmp/app.db'],
        schemas: ['ignored_sqlite_schema'],
      },
    })

    expect(screen.getByRole('combobox', { name: '数据库' })).toHaveTextContent('/tmp/app.db')
    expect(screen.queryByRole('combobox', { name: 'Schema' })).not.toBeInTheDocument()
  })
})
