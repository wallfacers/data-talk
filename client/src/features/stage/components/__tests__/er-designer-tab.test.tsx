import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { ErDesignerAdapter } from '../../adapters/ErDesignerAdapter'
import { ErDesignerTab } from '../er-designer-tab'

vi.mock('@/features/stage/persistence/stage-persistence-bootstrap', () => ({
  coordinator: { ensureHydrated: vi.fn().mockResolvedValue(undefined) },
}))

vi.mock('../er-canvas/ErCanvas', () => ({
  ErCanvas: ({
    mode,
    payload,
    onPatch,
  }: {
    mode: string
    payload: { tables?: { name: string }[] }
    onPatch: (ops: Array<Record<string, unknown>>) => void
  }) => (
    <div data-testid="er-canvas" data-mode={mode}>
      <button
        type="button"
        onClick={() => onPatch([{ op: 'add', path: '/tables/-', value: { name: 'users', columns: [], indexes: [], uniques: [] } }])}
      >
        trigger-structural-patch
      </button>
      {payload.tables?.map((table) => <span key={table.name}>{table.name}</span>)}
    </div>
  ),
}))

describe('<ErDesignerTab>', () => {
  beforeEach(() => {
    useErTabsStore.setState({ inspectors: new Map(), designers: new Map() })
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
})
