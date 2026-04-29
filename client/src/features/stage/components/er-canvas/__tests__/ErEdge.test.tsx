import { render } from '@testing-library/react'
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
    }],
    ['orders', {
      internals: {
        positionAbsolute: { x: 220, y: 0 },
        handleBounds: {
          source: [{ id: 'c2-source', x: 100, y: 50, width: 10, height: 10 }],
          target: [{ id: 'c2-target', x: 0, y: 50, width: 10, height: 10 }],
        },
      },
    }],
  ]),
  nodes: [
    { id: 'users', dragging: true },
    { id: 'orders', dragging: false },
  ],
}

vi.mock('@xyflow/react', () => ({
  EdgeLabelRenderer: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
  Position: { Right: 'right', Left: 'left' },
  getSmoothStepPath: ({ sourceX, sourceY, targetX, targetY }: { sourceX: number; sourceY: number; targetX: number; targetY: number }) => [
    `M ${sourceX},${sourceY} L ${targetX},${targetY}`,
    (sourceX + targetX) / 2,
    (sourceY + targetY) / 2,
  ],
  useStore: (selector: (state: typeof mockState) => unknown) => selector(mockState),
}))

import { ErEdge } from '../ErEdge'

describe('<ErEdge>', () => {
  it('skips crossing jump rendering while a node is being dragged', () => {
    const { container } = render(
      <svg>
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
        />
      </svg>,
    )

    expect(container.querySelectorAll('circle')).toHaveLength(0)
  })
})
