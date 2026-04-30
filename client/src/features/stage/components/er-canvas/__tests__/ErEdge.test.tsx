import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

const mockState = {
  edges: [
    {
      id: 'edge-a',
      source: 'users',
      target: 'orders',
      sourceHandle: 'c1-source',
      targetHandle: 'c2-target',
    },
    {
      id: 'edge-b',
      source: 'orders',
      target: 'users',
      sourceHandle: 'c2-source',
      targetHandle: 'c1-target',
    },
  ],
  nodeLookup: new Map([
    ['users', {
      internals: {
        positionAbsolute: { x: 0, y: 0 },
        handleBounds: {
          source: [{ id: 'c1-source', x: 100, y: 20, width: 10, height: 10 }],
          target: [{ id: 'c1-target', x: 0, y: 20, width: 10, height: 10 }],
        },
      },
      measured: { width: 100, height: 120 },
    }],
    ['orders', {
      internals: {
        positionAbsolute: { x: 220, y: 0 },
        handleBounds: {
          source: [{ id: 'c2-source', x: 100, y: 50, width: 10, height: 10 }],
          target: [{ id: 'c2-target', x: 0, y: 50, width: 10, height: 10 }],
        },
      },
      measured: { width: 100, height: 120 },
    }],
  ]),
  nodes: [
    { id: 'users', dragging: true },
    { id: 'orders', dragging: false },
  ],
}

vi.mock('@xyflow/react', () => ({
  EdgeLabelRenderer: ({ children }: { children?: React.ReactNode }) => <foreignObject>{children}</foreignObject>,
  Position: { Right: 'right', Left: 'left' },
  getSmoothStepPath: ({ sourceX, sourceY, targetX, targetY }: { sourceX: number; sourceY: number; targetX: number; targetY: number }) => [
    `M ${sourceX},${sourceY} L ${targetX},${targetY}`,
    (sourceX + targetX) / 2,
    (sourceY + targetY) / 2,
  ],
  useStore: (selector: (state: typeof mockState) => unknown) => selector(mockState),
}))

import { ErEdge } from '../ErEdge'

function renderEdge(children: React.ReactNode) {
  return render(<svg>{children}</svg>)
}

describe('<ErEdge>', () => {
  it('draws designer edges from node borders rather than detached handle centers', () => {
    const { container } = renderEdge(
      <ErEdge
        id="edge-b"
        source="orders"
        target="users"
        sourceX={225}
        sourceY={50}
        targetX={-5}
        targetY={20}
        sourcePosition={'right' as never}
        targetPosition={'left' as never}
        data={{ kind: 'fk', fromColumn: 'user_id', toColumn: 'id' }}
        selected={false}
      />,
    )

    expect(container.querySelector('path')?.getAttribute('d')).toBe('M 320,50 L 0,20')
  })

  it('lets designer edges update relation type and delete explicitly from the label', async () => {
    const onUpdateRelationType = vi.fn()
    const onDeleteRelation = vi.fn()

    renderEdge(
      <ErEdge
        id="edge-b"
        source="orders"
        target="users"
        sourceX={225}
        sourceY={50}
        targetX={-5}
        targetY={20}
        sourcePosition={'right' as never}
        targetPosition={'left' as never}
        data={{
          kind: 'fk',
          relationType: 'many_to_one',
          fromColumn: 'user_id',
          toColumn: 'id',
          mode: 'designer',
          onUpdateRelationType,
          onDeleteRelation,
        }}
        selected={false}
      />,
    )

    fireEvent.click(screen.getByRole('combobox', { name: /关系类型|Relation type/i }))
    const option = await screen.findByRole('option', { name: 'N:N' })
    fireEvent.mouseMove(option)
    fireEvent.pointerEnter(option, { pointerType: 'mouse' })
    fireEvent.click(option)
    fireEvent.click(screen.getByRole('button', { name: /删除关系|Delete relation/i }))

    expect(onUpdateRelationType).toHaveBeenCalledWith('many_to_many')
    expect(onDeleteRelation).toHaveBeenCalled()
  })

  it('renders the selected designer relation as the short label instead of the internal enum', () => {
    renderEdge(
      <ErEdge
        id="edge-b"
        source="orders"
        target="users"
        sourceX={225}
        sourceY={50}
        targetX={-5}
        targetY={20}
        sourcePosition={'right' as never}
        targetPosition={'left' as never}
        data={{
          kind: 'fk',
          relationType: 'one_to_one',
          fromColumn: 'user_id',
          toColumn: 'id',
          mode: 'designer',
          onUpdateRelationType: vi.fn(),
          onDeleteRelation: vi.fn(),
        }}
        selected={false}
      />,
    )

    const relationSelect = screen.getByRole('combobox', { name: /关系类型|Relation type/i })
    expect(relationSelect).toHaveTextContent('1:1')
    expect(relationSelect).not.toHaveTextContent('one_to_one')
  })

  it('skips crossing jump rendering while a node is being dragged', () => {
    const { container } = renderEdge(
      <ErEdge
        id="edge-b"
        source="orders"
        target="users"
        sourceX={220}
        sourceY={50}
        targetX={0}
        targetY={20}
        sourcePosition={'right' as never}
        targetPosition={'left' as never}
        data={{ kind: 'fk', fromColumn: 'user_id', toColumn: 'id' }}
        selected={false}
      />,
    )

    expect(container.querySelectorAll('circle')).toHaveLength(0)
  })
})
