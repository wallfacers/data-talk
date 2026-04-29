import { render, screen } from '@testing-library/react'
import { beforeAll, describe, expect, it, vi } from 'vitest'
import {
  ErCanvas,
  buildDesignerConnectPatch,
  buildDesignerEdgeDeletePatches,
  buildDesignerNodeDeletePatches,
  designerToGraph,
} from '../ErCanvas'
import type { ErDesignerPayload, ErInspectorPayload } from '@/features/stage/stores/er-tabs-payload-types'

const samplePayload: ErInspectorPayload = {
  kind: 'er_inspector',
  connectionId: 'c1',
  selection: ['users'],
  neighborDepth: 0,
  layout: 'dagre-LR',
  tablesSnapshot: [
    {
      name: 'users',
      columns: [{ name: 'id', type: 'BIGINT', isPK: true, isFK: false, nullable: false }],
      fkOut: [],
    },
  ],
  positions: { users: { x: 0, y: 0 } },
  collapsed: [],
  virtualRelations: [],
  notes: {},
  viewport: { x: 0, y: 0, zoom: 1 },
}

const designerPayload: ErDesignerPayload = {
  kind: 'er_designer',
  dialect: 'mysql',
  tables: [
    {
      id: 't1',
      name: 'users',
      columns: [
        { id: 'c1', name: 'id', type: 'BIGINT', nullable: false, isPrimaryKey: true, isAutoIncrement: true },
      ],
      indexes: [],
      uniques: [],
    },
    {
      id: 't2',
      name: 'orders',
      columns: [
        { id: 'c2', name: 'user_id', type: 'BIGINT', nullable: false, isPrimaryKey: false, isAutoIncrement: false },
      ],
      indexes: [],
      uniques: [],
    },
  ],
  relations: [],
  positions: { t1: { x: 0, y: 0 }, t2: { x: 320, y: 0 } },
  collapsed: [],
  viewport: { x: 0, y: 0, zoom: 1 },
}

describe('<ErCanvas mode="inspector">', () => {
  beforeAll(() => {
    global.ResizeObserver = class ResizeObserver {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
  })

  it('renders the inspector toolbar and a node for each table', () => {
    render(<ErCanvas tabId="t-1" mode="inspector" payload={samplePayload} onPatch={vi.fn()} onExec={vi.fn()} />)

    expect(screen.getByRole('button', { name: /自动布局/ })).toBeInTheDocument()
    expect(screen.getByText('users')).toBeInTheDocument()
  })

  it('renders ErEmptyState when payload selection is empty', () => {
    render(
      <ErCanvas
        tabId="t-1"
        mode="inspector"
        payload={{ ...samplePayload, selection: [], tablesSnapshot: [] }}
        onPatch={vi.fn()}
        onExec={vi.fn()}
      />,
    )

    expect(screen.getByText(/尚未选择表/)).toBeInTheDocument()
  })
})

describe('<ErCanvas mode="designer">', () => {
  beforeAll(() => {
    global.ResizeObserver = class ResizeObserver {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
  })

  it('renders designer toolbar and designer nodes', () => {
    render(<ErCanvas tabId="d-1" mode="designer" payload={designerPayload} onPatch={vi.fn()} onExec={vi.fn()} />)

    expect(screen.getByRole('button', { name: /add table|添加表/i })).toBeInTheDocument()
    expect(screen.getByText('users')).toBeInTheDocument()
    expect(screen.getByText('orders')).toBeInTheDocument()
    expect(document.querySelector('.react-flow__background pattern circle')).toBeTruthy()
  })

  it('renders an explicit empty-state hint for a blank designer draft', () => {
    render(
      <ErCanvas
        tabId="d-empty"
        mode="designer"
        payload={{ ...designerPayload, tables: [], positions: {} }}
        onPatch={vi.fn()}
        onExec={vi.fn()}
      />,
    )

    expect(screen.getByText(/尚未添加表|No tables yet/i)).toBeInTheDocument()
  })

  it('maps comment_ref relations to virtual edges in the designer graph', () => {
    const graph = designerToGraph({
      ...designerPayload,
      relations: [{
        id: 'r_virtual',
        fromTableId: 't1',
        fromColumnId: 'c1',
        toTableId: 't2',
        toColumnId: 'c2',
        type: 'many_to_one',
        constraintMethod: 'comment_ref',
      }],
    })

    expect(graph.edges).toEqual([
      expect.objectContaining({
        id: 'r_virtual',
        data: expect.objectContaining({
          kind: 'virtual',
          relationType: 'many_to_one',
        }),
      }),
    ])
  })

  it('emits patches for designer toolbar, context menu, and deletion callbacks', () => {
    const onPatch = vi.fn()
    const onExec = vi.fn()

    render(<ErCanvas tabId="d-1" mode="designer" payload={designerPayload} onPatch={onPatch} onExec={onExec} />)

    screen.getByRole('button', { name: /add table|添加表/i }).click()
    screen.getByRole('button', { name: /bind target|绑定目标/i }).click()
    screen.getByRole('button', { name: /auto layout|自动布局/i }).click()

    expect(onPatch).toHaveBeenCalledWith([{ op: 'add', path: '/tables/-', value: expect.objectContaining({ name: 'new_table' }) }])
    expect(onExec).toHaveBeenCalledWith('bind_target')
    expect(onExec).toHaveBeenCalledWith('auto_layout')
  })

  it('builds designer relation and deletion patches for ReactFlow callbacks', () => {
    expect(buildDesignerConnectPatch({
      source: 't1',
      target: 't2',
      sourceHandle: 'c1-source',
      targetHandle: 'c2-target',
    })).toEqual([{
      op: 'add',
      path: '/relations/-',
      value: {
        fromTableId: 't1',
        fromColumnId: 'c1',
        toTableId: 't2',
        toColumnId: 'c2',
        type: 'many_to_one',
        constraintMethod: 'database_fk',
      },
    }])

    expect(buildDesignerNodeDeletePatches([{ id: 't1' }])).toEqual([
      { op: 'remove', path: '/tables[id=t1]' },
    ])
    expect(buildDesignerEdgeDeletePatches([{ id: 'fk:r1' }, { id: 'vr:r2' }, { id: 'r3' }])).toEqual([
      { op: 'remove', path: '/relations[id=r1]' },
      { op: 'remove', path: '/relations[id=r2]' },
      { op: 'remove', path: '/relations[id=r3]' },
    ])
  })
})
