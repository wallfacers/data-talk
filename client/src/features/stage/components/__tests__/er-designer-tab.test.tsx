import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useConnectionStore } from '@/features/connection/store'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { ErDesignerAdapter } from '../../adapters/ErDesignerAdapter'
import { ErDesignerTab } from '../er-designer-tab'

const connectionApiMocks = vi.hoisted(() => ({
  listConnections: vi.fn(),
  getConnectionTargets: vi.fn(),
}))

vi.mock('@/services/api/connection', () => connectionApiMocks)

vi.mock('@/features/stage/persistence/stage-persistence-bootstrap', () => ({
  coordinator: { ensureHydrated: vi.fn().mockResolvedValue(undefined) },
}))

vi.mock('../er-canvas/ErCanvas', () => ({
  ErCanvas: ({
    mode,
    payload,
    onPatch,
    onExec,
  }: {
    mode: string
    payload: { tables?: { name: string }[] }
    onPatch: (ops: Array<Record<string, unknown>>) => void
    onExec: (action: string, params?: unknown) => void
  }) => (
    <div data-testid="er-canvas" data-mode={mode}>
      <button
        type="button"
        onClick={() => onPatch([{ op: 'add', path: '/tables/-', value: { name: 'users', columns: [], indexes: [], uniques: [] } }])}
      >
        trigger-structural-patch
      </button>
      <button
        type="button"
        onClick={() => onExec('bind_target')}
      >
        trigger-bind-target
      </button>
      {payload.tables?.map((table) => <span key={table.name}>{table.name}</span>)}
    </div>
  ),
}))

describe('<ErDesignerTab>', () => {
  beforeEach(() => {
    useErTabsStore.setState({ inspectors: new Map(), designers: new Map() })
    useConnectionStore.setState({ activeConnectionId: null, connections: [] })
    connectionApiMocks.listConnections.mockReset()
    connectionApiMocks.getConnectionTargets.mockReset()
  })

  it('renders the designer canvas after hydration', () => {
    useErTabsStore.setState({
      designers: new Map([[
        'd-1',
        {
          kind: 'er_designer',
          dialect: 'mysql',
          targetConnectionId: null,
          targetDatabase: null,
          targetSchema: null,
          tables: [{ id: 't1', name: 'users', columns: [], indexes: [], uniques: [] }],
          relations: [],
          positions: {},
          collapsed: [],
          viewport: { x: 0, y: 0, zoom: 1 },
        },
      ]]),
    })

    render(<ErDesignerTab tabId="d-1" />)

    expect(screen.getByTestId('er-canvas')).toHaveAttribute('data-mode', 'designer')
    expect(screen.getByText('users')).toBeInTheDocument()
  })

  it('renders loading copy when payload is missing', () => {
    render(<ErDesignerTab tabId="missing" />)

    expect(screen.getByText(/Loading|加载/i)).toBeInTheDocument()
  })

  it('adds the current payload version to user-driven structural patches before delegating to the adapter', async () => {
    const patchSpy = vi.spyOn(ErDesignerAdapter.prototype, 'patch').mockResolvedValue({
      status: 'applied',
      message: 'ok',
      newVersion: 1,
      assignedIds: {},
    })
    useErTabsStore.setState({
      designers: new Map([[
        'd-1',
        {
          kind: 'er_designer',
          dialect: 'mysql',
          targetConnectionId: null,
          targetDatabase: null,
          targetSchema: null,
          tables: [],
          relations: [],
          positions: {},
          collapsed: [],
          viewport: { x: 0, y: 0, zoom: 1 },
          __v: 0,
        } as never,
      ]]),
    })

    render(<ErDesignerTab tabId="d-1" />)

    screen.getByRole('button', { name: 'trigger-structural-patch' }).click()

    expect(patchSpy).toHaveBeenCalledWith([
      expect.objectContaining({
        op: 'add',
        path: '/tables/-',
        baseVersion: 0,
      }),
    ])
  })

  it('opens bind-target flow and writes the selected connection context back to the designer payload', async () => {
    connectionApiMocks.listConnections.mockResolvedValue([
      {
        id: 'conn-1',
        name: 'Warehouse',
        kind: 'postgresql',
        host: 'localhost',
        port: 5432,
        databaseName: 'analytics',
        username: 'postgres',
        createdAt: 0,
        connectTimeout: 3000,
        lastTestStatus: null,
        lastTestAt: null,
      },
    ])
    connectionApiMocks.getConnectionTargets.mockResolvedValue({
      connectionId: 'conn-1',
      connectionName: 'Warehouse',
      databases: ['analytics'],
      schemas: ['public'],
    })
    useErTabsStore.setState({
      designers: new Map([[
        'd-1',
        {
          kind: 'er_designer',
          dialect: 'postgresql',
          targetConnectionId: null,
          targetDatabase: null,
          targetSchema: null,
          tables: [],
          relations: [],
          positions: {},
          collapsed: [],
          viewport: { x: 0, y: 0, zoom: 1 },
          __v: 0,
        } as never,
      ]]),
    })

    render(<ErDesignerTab tabId="d-1" />)

    fireEvent.click(screen.getByRole('button', { name: 'trigger-bind-target' }))

    expect(await screen.findByRole('heading', { name: /bind target|绑定目标/i })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /bind target|绑定目标/i }))

    await waitFor(() => {
      expect(useErTabsStore.getState().designers.get('d-1')).toMatchObject({
        targetConnectionId: 'conn-1',
        targetDatabase: 'analytics',
      })
    })
    expect(connectionApiMocks.getConnectionTargets).toHaveBeenCalledWith('conn-1')
  })

  it('renders human-readable bind-target labels instead of raw UUID and sentinel values', async () => {
    connectionApiMocks.listConnections.mockResolvedValue([
      {
        id: 'conn-1',
        name: 'Warehouse',
        kind: 'postgresql',
        host: 'localhost',
        port: 5432,
        databaseName: null,
        username: 'postgres',
        createdAt: 0,
        connectTimeout: 3000,
        lastTestStatus: null,
        lastTestAt: null,
      },
    ])
    connectionApiMocks.getConnectionTargets.mockResolvedValue({
      connectionId: 'conn-1',
      connectionName: 'Warehouse',
      databases: [],
      schemas: [],
    })
    useErTabsStore.setState({
      designers: new Map([[
        'd-1',
        {
          kind: 'er_designer',
          dialect: 'postgresql',
          targetConnectionId: 'conn-1',
          targetDatabase: null,
          targetSchema: null,
          tables: [],
          relations: [],
          positions: {},
          collapsed: [],
          viewport: { x: 0, y: 0, zoom: 1 },
          __v: 0,
        } as never,
      ]]),
    })

    render(<ErDesignerTab tabId="d-1" />)

    fireEvent.click(screen.getByRole('button', { name: 'trigger-bind-target' }))

    const connectionTrigger = await screen.findByRole('combobox', { name: /连接|Connection/i })
    const databaseTrigger = screen.getByRole('combobox', { name: /数据库|Database/i })
    const schemaTrigger = screen.getByRole('combobox', { name: /Schema/i })

    expect(connectionTrigger).toHaveTextContent('Warehouse')
    expect(connectionTrigger).not.toHaveTextContent('conn-1')
    expect(databaseTrigger).toHaveTextContent(/未设置|Not set/i)
    expect(databaseTrigger).not.toHaveTextContent('__empty__')
    expect(schemaTrigger).toHaveTextContent(/未设置|Not set/i)
    expect(schemaTrigger).not.toHaveTextContent('__empty__')
  })
})
