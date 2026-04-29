import { render, screen } from '@testing-library/react'
import { beforeAll, describe, expect, it, vi } from 'vitest'
import { ErCanvas } from '../ErCanvas'
import type { ErInspectorPayload } from '@/features/stage/stores/er-tabs-payload-types'

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
